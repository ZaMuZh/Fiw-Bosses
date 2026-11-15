package com.fiw.blockdash;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.text.Text;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class DashArena {
    final String id;
    int width = 9;
    int durationSeconds = 90;
    int spawnInterval = 50;
    double startSpeed = 2.0;
    double maxSpeed = 5.0;
    double pushStrength = 0.9;

    private ServerWorld world;
    private Vec3d start;
    private Vec3d end;
    private Vec3d travel;
    private Vec3d side;
    private final Set<UUID> players = new HashSet<>();
    private final List<Wall> walls = new ArrayList<>();
    private int elapsedTicks;
    private int spawnTicks;
    private boolean running;

    DashArena(String id) { this.id = id; }

    void setStart(ServerWorld world, Vec3d pos) {
        this.world = world;
        this.start = new Vec3d(Math.floor(pos.x) + 0.5, Math.floor(pos.y), Math.floor(pos.z) + 0.5);
    }

    void setEnd(ServerWorld world, Vec3d pos) {
        this.world = world;
        this.end = new Vec3d(Math.floor(pos.x) + 0.5, Math.floor(pos.y), Math.floor(pos.z) + 0.5);
    }

    void start(MinecraftServer server) {
        if (start == null || end == null || world == null) throw new IllegalStateException("Set both start and end first.");
        if (start.distanceTo(end) < 8) throw new IllegalStateException("Start and end must be at least 8 blocks apart.");
        stop();
        travel = start.subtract(end).normalize();
        side = new Vec3d(-travel.z, 0, travel.x).normalize();
        players.clear();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (insideLane(player.getPos())) players.add(player.getUuid());
        }
        if (players.isEmpty()) throw new IllegalStateException("No players are inside this arena lane.");
        elapsedTicks = 0;
        spawnTicks = 0;
        running = true;
        world.getPlayers().forEach(p -> {
            if (players.contains(p.getUuid())) p.sendMessage(Text.literal("§b§lBLOCK DASH §f¡Sobrevive a los muros!"), false);
        });
    }

    void stop() {
        running = false;
        for (Wall wall : walls) wall.remove();
        walls.clear();
        players.clear();
    }

    void tick(MinecraftServer server) {
        if (!running || world == null) return;
        elapsedTicks++;
        double progress = Math.min(1.0, elapsedTicks / (double)(durationSeconds * 20));
        double speed = startSpeed + (maxSpeed - startSpeed) * progress;
        if (++spawnTicks >= spawnInterval) {
            spawnTicks = 0;
            spawnWall();
        }
        Vec3d delta = travel.multiply(speed / 20.0);
        for (Wall wall : List.copyOf(walls)) {
            wall.move(delta, players, world);
            wall.collide(players, world, travel, pushStrength);
            if (wall.position.subtract(start).dotProduct(travel) >= 0) {
                wall.remove();
                walls.remove(wall);
            }
        }
        for (UUID uuid : Set.copyOf(players)) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
            if (player == null || player.getY() < Math.min(start.y, end.y) - 8) {
                players.remove(uuid);
                if (player != null) player.sendMessage(Text.literal("§c¡Eliminado de BlockDash!"), false);
            }
        }
        if (players.isEmpty()) finish(false);
        else if (elapsedTicks >= durationSeconds * 20) finish(true);
    }

    private boolean insideLane(Vec3d pos) {
        Vec3d fromStart = pos.subtract(start);
        Vec3d line = end.subtract(start);
        double lengthSq = line.lengthSquared();
        double t = Math.max(0, Math.min(1, fromStart.dotProduct(line) / lengthSq));
        Vec3d nearest = start.add(line.multiply(t));
        return pos.distanceTo(nearest) <= width / 2.0;
    }

    private void spawnWall() {
        Random random = world.getRandom();
        List<BlockDisplayEntity> blocks = new ArrayList<>();
        List<BlockDisplayEntity> platforms = new ArrayList<>();
        List<BlockDisplayEntity> hazards = new ArrayList<>();
        Vec3d base = end.subtract(side.multiply(width / 2.0));
        int pattern = random.nextInt(9);

        switch (pattern) {
            case 0 -> { // Side opening: choose left or right.
                int gapStart = random.nextBoolean() ? 0 : Math.max(0, width - 3);
                addPlane(blocks, hazards, base, 0, 3, gapStart, 3, -1);
            }
            case 1 -> { // Full-height central opening: pass without crouching.
                int gapStart = Math.max(0, width / 2 - 1);
                addPlane(blocks, hazards, base, 0, 3, gapStart, 3, -1);
            }
            case 2 -> { // Low hurdle: jump over the whole row.
                addPlane(blocks, hazards, base, 0, 1, -1, 0, -1);
            }
            case 3 -> { // Two offset gates make a zigzag.
                int firstGap = random.nextInt(Math.max(1, width - 2));
                int secondGap = (firstGap + Math.max(2, width / 2)) % Math.max(1, width - 2);
                addPlane(blocks, hazards, base, 0, 3, firstGap, 3, -1);
                addPlane(blocks, hazards, base, 2, 3, secondGap, 3, -1);
            }
            case 4 -> { // A safe climbable staircase parkour; no blocking wall.
                int center = Math.max(0, width / 2);
                int[] depths = {-2, -1, 0, 1, 2};
                int[] heights = {0, 1, 2, 1, 0};
                for (int i = 0; i < depths.length; i++) {
                    Vec3d p = base.add(side.multiply(center)).add(travel.multiply(depths[i])).add(0, heights[i], 0);
                    addDisplay(platforms, p, Blocks.BLUE_ICE.getDefaultState());
                }
            }
            case 5 -> { // Alternating low hurdles: jump left/right.
                for (int i = 0; i < 3; i++) {
                    int lane = (i % 2 == 0) ? 0 : Math.max(0, width - 3);
                    addPlane(blocks, hazards, base, i * 2, 1, lane, 3, -1);
                }
            }
            case 6 -> { // Two full-height openings; both are passable standing.
                int left = Math.max(0, width / 4 - 1);
                int right = Math.min(width - 2, (width * 3) / 4 - 1);
                addPlane(blocks, hazards, base, 0, 3, left, 2, -1);
                addPlane(blocks, hazards, base, 1, 3, right, 2, -1);
            }
            case 7 -> { // Stepping-stone parkour with varied heights and lanes.
                int[] lanes = {0, Math.max(0, width / 3), Math.max(0, (width * 2) / 3), Math.max(0, width - 1)};
                int[] heights = {0, 1, 2, 1};
                for (int i = 0; i < lanes.length; i++) {
                    Vec3d p = base.add(side.multiply(lanes[i])).add(travel.multiply(i * 2 - 2)).add(0, heights[i], 0);
                    addDisplay(platforms, p, i % 2 == 0 ? Blocks.BLUE_ICE.getDefaultState() : Blocks.PACKED_ICE.getDefaultState());
                }
            }
            default -> { // Longer zig-zag parkour bridge, alternating height and side.
                for (int i = 0; i < 6; i++) {
                    int lane = (i % 2 == 0) ? Math.max(0, width / 5) : Math.max(0, width - 2 - width / 5);
                    int height = (i % 3 == 1) ? 2 : (i % 3 == 2 ? 1 : 0);
                    Vec3d p = base.add(side.multiply(lane)).add(travel.multiply(i - 2.5)).add(0, height, 0);
                    addDisplay(platforms, p, i % 2 == 0 ? Blocks.BLUE_ICE.getDefaultState() : Blocks.PACKED_ICE.getDefaultState());
                }
            }
        }

        if (!blocks.isEmpty() || !platforms.isEmpty()) walls.add(new Wall(blocks, platforms, hazards, end));
    }

    /**
     * Builds a wall plane. gapStart/gapWidth describe a full-height opening.
     * If tunnelTopRow is 2, the opening is covered by a roof, forming an arch.
     */
    private void addPlane(List<BlockDisplayEntity> blocks, List<BlockDisplayEntity> hazards, Vec3d base, int depth, int height,
                          int gapStart, int gapWidth, int tunnelTopRow) {
        for (int x = 0; x < width; x++) {
            boolean inGap = gapStart >= 0 && x >= gapStart && x < gapStart + gapWidth;
            for (int y = 0; y < height; y++) {
                if (inGap && (tunnelTopRow < 0 || y < tunnelTopRow)) continue;
                Vec3d p = base.add(side.multiply(x)).add(travel.multiply(depth)).add(0, y, 0);
                addDisplay(blocks, p, Blocks.PACKED_ICE.getDefaultState());
                hazards.add(blocks.get(blocks.size() - 1));
            }
        }
    }

    private void addDisplay(List<BlockDisplayEntity> target, Vec3d pos, BlockState state) {
        BlockDisplayEntity display = (BlockDisplayEntity) EntityType.BLOCK_DISPLAY.create(world);
        if (display == null) return;
        NbtCompound displayData = new NbtCompound();
        displayData.put(BlockDisplayEntity.BLOCK_STATE_NBT_KEY, NbtHelper.fromBlockState(state));
        display.readNbt(displayData);
        display.setNoGravity(true);
        display.setInvulnerable(true);
        // Display entities interpolate their teleports client-side instead of visibly snapping.
        display.setTeleportDuration(2);
        display.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
        world.spawnEntity(display);
        target.add(display);
    }

    private void finish(boolean survived) {
        running = false;
        for (Wall wall : walls) wall.remove();
        walls.clear();
        for (UUID uuid : players) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
            if (player == null) continue;
            if (survived) {
                player.sendMessage(Text.literal("§a§l¡FELICIDADES POR SUPERAR EL DESAFÍO!"), false);
                world.spawnParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 1, player.getZ(), 45, 0.6, 0.8, 0.6, 0.08);
                player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            } else {
                player.sendMessage(Text.literal("§c§lFIN DE BLOCK DASH §fNo quedan supervivientes."), false);
            }
        }
        players.clear();
    }

    private static final class Wall {
        private final List<BlockDisplayEntity> blocks;
        private final List<BlockDisplayEntity> platforms;
        private final List<BlockDisplayEntity> hazards;
        private Vec3d position;
        private final Set<UUID> alreadyPushed = new HashSet<>();

        Wall(List<BlockDisplayEntity> blocks, List<BlockDisplayEntity> platforms, List<BlockDisplayEntity> hazards, Vec3d position) {
            this.blocks = blocks;
            this.platforms = platforms;
            this.hazards = hazards;
            this.position = position;
        }

        void move(Vec3d delta, Set<UUID> players, ServerWorld world) {
            // Carry players standing on a moving parkour step.
            List<ServerPlayerEntity> riding = new ArrayList<>();
            for (UUID uuid : players) {
                ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
                if (player == null || player.getVelocity().y > 0.05) continue;
                Box playerBox = player.getBoundingBox();
                for (BlockDisplayEntity platform : platforms) {
                    double top = platform.getY() + 1.0;
                    boolean horizontal = playerBox.maxX > platform.getX() && playerBox.minX < platform.getX() + 1.0
                        && playerBox.maxZ > platform.getZ() && playerBox.minZ < platform.getZ() + 1.0;
                    if (horizontal && player.getY() >= top - 0.18 && player.getY() <= top + 0.25) {
                        riding.add(player);
                        break;
                    }
                }
            }
            position = position.add(delta);
            for (BlockDisplayEntity block : blocks) {
                block.setPosition(block.getX() + delta.x, block.getY() + delta.y, block.getZ() + delta.z);
            }
            for (BlockDisplayEntity platform : platforms) {
                platform.setPosition(platform.getX() + delta.x, platform.getY() + delta.y, platform.getZ() + delta.z);
            }
            for (ServerPlayerEntity player : riding) {
                player.setPosition(player.getX() + delta.x, player.getY() + delta.y, player.getZ() + delta.z);
                Vec3d velocity = player.getVelocity();
                player.setVelocity(velocity.x, 0, velocity.z);
                player.setOnGround(true);
                player.velocityModified = true;
            }

            // Block displays have no vanilla collision shape. Give parkour steps
            // a small, safe server-side landing surface so players can jump on them.
            for (UUID uuid : players) {
                ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
                if (player == null || player.getVelocity().y > 0.05) continue;
                Box playerBox = player.getBoundingBox();
                for (BlockDisplayEntity platform : platforms) {
                    double top = platform.getY() + 1.0;
                    boolean horizontal = playerBox.maxX > platform.getX() + 0.05
                        && playerBox.minX < platform.getX() + 0.95
                        && playerBox.maxZ > platform.getZ() + 0.05
                        && playerBox.minZ < platform.getZ() + 0.95;
                    double feet = player.getY();
                    if (horizontal && feet <= top + 0.18 && feet >= top - 0.65) {
                        player.setPosition(player.getX(), top, player.getZ());
                        Vec3d velocity = player.getVelocity();
                        player.setVelocity(velocity.x, 0, velocity.z);
                        player.setOnGround(true);
                        player.velocityModified = true;
                        break;
                    }
                }
            }
        }

        void collide(Set<UUID> players, ServerWorld world, Vec3d direction, double push) {
            for (UUID uuid : players) {
                ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
                if (player == null) continue;
                Box box = player.getBoundingBox();
                boolean hit = false;
                for (BlockDisplayEntity block : hazards) {
                    Box obstacle = new Box(block.getX(), block.getY(), block.getZ(),
                        block.getX() + 1, block.getY() + 1, block.getZ() + 1);
                    if (box.intersects(obstacle)) { hit = true; break; }
                }
                if (hit && alreadyPushed.add(uuid)) {
                    player.addVelocity(direction.x * push, 0.22, direction.z * push);
                    player.velocityModified = true;
                }
            }
        }

        void remove() {
            for (Entity entity : blocks) entity.discard();
            for (Entity entity : platforms) entity.discard();
        }
    }
}
