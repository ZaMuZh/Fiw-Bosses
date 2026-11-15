package com.fiw.blockdash;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
        List<PlatformTile> platforms = new ArrayList<>();
        List<BlockDisplayEntity> hazards = new ArrayList<>();
        Vec3d base = end.subtract(side.multiply((width - 1) / 2.0));
        int pattern = random.nextInt(12);
        int paletteIndex = random.nextInt(16);
        BlockState primary = palette(paletteIndex % 8);
        BlockState accent = palette((paletteIndex + 3) % 8);
        BlockState detail = palette((paletteIndex + 5) % 8);

        // Every wall pattern has a guaranteed standing-height route at least 3 blocks wide.
        // Parkour patterns use real world blocks, never BlockDisplay collision fakes.
        int safeGap = random.nextInt(Math.max(1, width - 2));
        switch (pattern) {
            case 0 -> addStyledWall(blocks, hazards, base, primary, accent, detail, 3, safeGap, 3, 0);
            case 1 -> addStyledWall(blocks, hazards, base, primary, accent, detail, 4, safeGap, 3, 1);
            case 2 -> addStyledWall(blocks, hazards, base, accent, primary, detail, 3, safeGap, 3, 2);
            case 3 -> addStyledWall(blocks, hazards, base, accent, detail, primary, 4, safeGap, 3, 3);
            case 4 -> addStyledWall(blocks, hazards, base, primary, detail, accent, 3, safeGap, 3, 4);
            case 5 -> addStyledWall(blocks, hazards, base, detail, primary, accent, 4, safeGap, 3, 5);
            case 6 -> addStyledWall(blocks, hazards, base, primary, accent, detail, 3, safeGap, 3, 6);
            case 7 -> addStyledWall(blocks, hazards, base, accent, primary, detail, 4, safeGap, 3, 7);
            case 8 -> addLowJumpWall(blocks, hazards, base, primary, accent, detail);
            case 9 -> addParkour(platforms, base, primary, accent, 0);
            case 10 -> addParkour(platforms, base, accent, detail, 1);
            default -> addParkour(platforms, base, detail, primary, 2);
        }

        if (!blocks.isEmpty() || !platforms.isEmpty()) walls.add(new Wall(blocks, platforms, hazards, end, world));
    }

    /**
     * Builds a wall plane. gapStart/gapWidth describe a full-height opening.
     * If tunnelTopRow is 2, the opening is covered by a roof, forming an arch.
     */
    private BlockState palette(int index) {
        return switch (index) {
            case 0 -> Blocks.RED_CONCRETE.getDefaultState();
            case 1 -> Blocks.ORANGE_CONCRETE.getDefaultState();
            case 2 -> Blocks.YELLOW_CONCRETE.getDefaultState();
            case 3 -> Blocks.LIME_CONCRETE.getDefaultState();
            case 4 -> Blocks.CYAN_CONCRETE.getDefaultState();
            case 5 -> Blocks.BLUE_CONCRETE.getDefaultState();
            case 6 -> Blocks.PURPLE_CONCRETE.getDefaultState();
            default -> Blocks.MAGENTA_CONCRETE.getDefaultState();
        };
    }

    private void addParkour(List<PlatformTile> platforms, Vec3d base, BlockState a, BlockState b, int style) {
        // Broad, walkable 3x2 pads. Heights never exceed one block between jumps.
        int mid = Math.max(0, width / 2 - 1);
        int left = 0;
        int right = Math.max(0, width - 3);
        int[] lanes = style == 0 ? new int[]{left, mid, right, mid, left}
            : style == 1 ? new int[]{mid, left, mid, right, mid}
            : new int[]{left, left, mid, right, right, mid};
        int[] heights = style == 2 ? new int[]{0, 1, 1, 0, 1, 0} : new int[]{0, 1, 0, 1, 0};
        for (int i = 0; i < lanes.length; i++) {
            int h = heights[i % heights.length];
            Vec3d p = base.add(side.multiply(lanes[i])).add(travel.multiply(i * 2 - (lanes.length - 1))).add(0, h, 0);
            for (int x = 0; x < 3; x++) for (int z = 0; z < 2; z++) {
                Vec3d cell = p.add(side.multiply(x)).add(travel.multiply(z));
                platforms.add(new PlatformTile(cell, (i + x + z) % 2 == 0 ? a : b));
            }
        }
    }

    private void addStyledWall(List<BlockDisplayEntity> blocks, List<BlockDisplayEntity> hazards,
                               Vec3d base, BlockState primary, BlockState accent, BlockState detail,
                               int height, int gapStart, int gapWidth, int style) {
        // A continuous, clearly readable wall face with a guaranteed 3-wide route.
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (x >= gapStart && x < gapStart + gapWidth) continue;
                boolean border = x == 0 || x == width - 1 || y == 0 || y == height - 1;
                boolean stripe = switch (style % 4) {
                    case 0 -> x % 3 == 0;
                    case 1 -> y == 1 || y == height - 2;
                    case 2 -> (x + y) % 4 == 0;
                    default -> x % 2 == 0 && y % 2 == 0;
                };
                BlockState face = border ? accent : stripe ? detail : primary;
                Vec3d pos = base.add(side.multiply(x)).add(0, y, 0);
                addDisplay(blocks, pos, face);
                hazards.add(blocks.get(blocks.size() - 1));
            }
        }
        // Bold top marker and colored pillars frame the opening without narrowing it.
        for (int x = 0; x < width; x++) {
            if (x >= gapStart && x < gapStart + gapWidth) continue;
            if ((x + style) % 3 == 0) {
                Vec3d pos = base.add(side.multiply(x)).add(travel.multiply(1)).add(0, height, 0);
                addDisplay(blocks, pos, detail);
                hazards.add(blocks.get(blocks.size() - 1));
            }
        }
    }

    private void addLowJumpWall(List<BlockDisplayEntity> blocks, List<BlockDisplayEntity> hazards,
                                Vec3d base, BlockState primary, BlockState accent, BlockState detail) {
        // Low hurdles occupy only one block of height and are spaced for clean jumps.
        for (int row = 0; row < 3; row++) {
            int lane = row == 1 ? Math.max(0, width - 3) : 0;
            for (int x = 0; x < 3; x++) {
                Vec3d pos = base.add(side.multiply(lane + x)).add(travel.multiply(row * 2)).add(0, 0, 0);
                addDisplay(blocks, pos, (row + x) % 2 == 0 ? accent : primary);
                hazards.add(blocks.get(blocks.size() - 1));
            }
        }
        // Decorative color marker floats above, not in the jump path.
        for (int x = 0; x < width; x += 2) {
            Vec3d pos = base.add(side.multiply(x)).add(travel.multiply(1)).add(0, 2, 0);
            addDisplay(blocks, pos, detail);
        }
    }

    private record PlatformTile(Vec3d position, BlockState state) {}

    private void addPlane(List<BlockDisplayEntity> blocks, List<BlockDisplayEntity> hazards, Vec3d base, int depth, int height,
                          int gapStart, int gapWidth, int tunnelTopRow, BlockState primary, BlockState accent) {
        for (int x = 0; x < width; x++) {
            boolean inGap = gapStart >= 0 && x >= gapStart && x < gapStart + gapWidth;
            for (int y = 0; y < height; y++) {
                if (inGap && (tunnelTopRow < 0 || y < tunnelTopRow)) continue;
                Vec3d p = base.add(side.multiply(x)).add(travel.multiply(depth)).add(0, y, 0);
                boolean frame = y == 0 || y == height - 1 || x == 0 || x == width - 1;
                boolean accentStripe = y == 1 && x % 4 == 0;
                BlockState face = (frame || accentStripe) ? accent : primary;
                addDisplay(blocks, p, face);
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
        private final List<PlatformTile> platforms;
        private final List<BlockDisplayEntity> hazards;
        private final Map<BlockPos, BlockState> originalBlocks = new HashMap<>();
        private Set<BlockPos> activePlatformBlocks = new HashSet<>();
        private Vec3d position;
        private final Set<UUID> alreadyPushed = new HashSet<>();
        private final ServerWorld world;

        Wall(List<BlockDisplayEntity> blocks, List<PlatformTile> platforms, List<BlockDisplayEntity> hazards,
             Vec3d position, ServerWorld world) {
            this.blocks = blocks;
            this.platforms = platforms;
            this.hazards = hazards;
            this.position = position;
            this.initialPosition = position;
            this.world = world;
            updatePhysicalPlatforms();
        }

        void move(Vec3d delta, Set<UUID> players, ServerWorld world) {
            position = position.add(delta);
            for (BlockDisplayEntity block : blocks) {
                block.setPosition(block.getX() + delta.x, block.getY() + delta.y, block.getZ() + delta.z);
            }
            updatePhysicalPlatforms();
        }

        private void updatePhysicalPlatforms() {
            Set<BlockPos> next = new HashSet<>();
            Map<BlockPos, BlockState> desired = new HashMap<>();
            for (PlatformTile tile : platforms) {
                Vec3d p = tile.position.add(position.subtract(initialPosition));
                BlockPos bp = BlockPos.ofFloored(p.x, p.y, p.z);
                next.add(bp);
                desired.put(bp, tile.state);
            }
            // Remember every original block before the first temporary placement.
            for (BlockPos pos : next) originalBlocks.putIfAbsent(pos.toImmutable(), world.getBlockState(pos));
            for (BlockPos old : activePlatformBlocks) {
                if (!next.contains(old)) {
                    BlockState original = originalBlocks.get(old);
                    if (original != null) world.setBlockState(old, original, Block.NOTIFY_ALL);
                }
            }
            for (Map.Entry<BlockPos, BlockState> entry : desired.entrySet()) {
                BlockPos pos = entry.getKey();
                if (!world.getBlockState(pos).equals(entry.getValue())) {
                    if (!originalBlocks.containsKey(pos)) originalBlocks.put(pos.toImmutable(), world.getBlockState(pos));
                    world.setBlockState(pos, entry.getValue(), Block.NOTIFY_ALL);
                }
            }
            activePlatformBlocks = next;
        }

        private Vec3d initialPosition = Vec3d.ZERO;

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
            for (BlockPos pos : originalBlocks.keySet()) {
                BlockState original = originalBlocks.get(pos);
                if (original != null) world.setBlockState(pos, original, Block.NOTIFY_ALL);
            }
            originalBlocks.clear();
            activePlatformBlocks.clear();
        }
    }
}
