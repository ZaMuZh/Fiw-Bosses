package com.fiw.blockdash;

import net.minecraft.block.Blocks;
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
    int width =  nine();
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

    private static int nine() { return 9; }

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
        Vec3d direction = start.subtract(end).normalize();
        travel = direction;
        side = new Vec3d(-direction.z, 0, direction.x).normalize();
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
        for (Wall wall : List.copyOf(walls)) {
            wall.move(travel.multiply(speed / 20.0));
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
                continue;
            }
        }
        if (players.isEmpty()) {
            finish(false);
        } else if (elapsedTicks >= durationSeconds * 20) {
            finish(true);
        }
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
        int gapWidth = 2 + random.nextInt(2);
        int gapCenter = random.nextInt(width);
        List<BlockDisplayEntity> blocks = new ArrayList<>();
        Vec3d base = end.subtract(side.multiply(width / 2.0));
        for (int x = 0; x < width; x++) {
            boolean gap = x >= gapCenter - gapWidth / 2 && x < gapCenter - gapWidth / 2 + gapWidth;
            if (gap) continue;
            for (int y = 0; y < 3; y++) {
                Vec3d p = base.add(side.multiply(x)).add(0, y, 0);
                BlockDisplayEntity display = (BlockDisplayEntity) EntityType.BLOCK_DISPLAY.create(world);
                if (display == null) continue;
                display.setBlockState(Blocks.PACKED_ICE.getDefaultState());
                display.setNoGravity(true);
                display.setInvulnerable(true);
                display.refreshPositionAndAngles(p.x, p.y, p.z, 0, 0);
                world.spawnEntity(display);
                blocks.add(display);
            }
        }
        if (!blocks.isEmpty()) walls.add(new Wall(blocks, end));
    }

    private void finish(boolean survived) {
        running = false;
        for (Wall wall : walls) wall.remove();
        walls.clear();
        for (UUID uuid : players) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
            if (player == null) continue;
            if (survived) {
                player.sendMessage(Text.literal("§a§l¡VICTORIA! §fHas sobrevivido a BlockDash."), false);
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
        private Vec3d position;
        Wall(List<BlockDisplayEntity> blocks, Vec3d position) { this.blocks = blocks; this.position = position; }
        void move(Vec3d delta) {
            position = position.add(delta);
            for (BlockDisplayEntity block : blocks) block.setPosition(block.getX() + delta.x, block.getY() + delta.y, block.getZ() + delta.z);
        }
        void collide(Set<UUID> players, ServerWorld world, Vec3d direction, double push) {
            for (UUID uuid : players) {
                ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
                if (player == null) continue;
                Box box = player.getBoundingBox();
                boolean hit = false;
                for (BlockDisplayEntity block : blocks) {
                    Box obstacle = new Box(block.getX(), block.getY(), block.getZ(), block.getX() + 1, block.getY() + 1, block.getZ() + 1);
                    if (box.intersects(obstacle)) { hit = true; break; }
                }
                if (hit) {
                    player.addVelocity(direction.x * push, 0.22, direction.z * push);
                    player.velocityModified = true;
                }
            }
        }
        void remove() { for (Entity entity : blocks) entity.discard(); }
    }
}
