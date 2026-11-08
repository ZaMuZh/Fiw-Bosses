package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

/**
 * A marked icicle bombardment. Players see the impact circles first, then actual
 * packed/blue-ice falling-block entities descend from above and shatter on impact.
 */
public class IcicleRainGoal extends Goal {

    private final BossEntity boss;
    private final double radius;
    private final int height;
    private final int icicleCount;
    private final float damage;
    private final int warnTicks;
    private final int rainTicks;
    private final int cooldown;

    private final List<ImpactPoint> impacts = new ArrayList<>();
    private final List<ActiveIcicle> falling = new ArrayList<>();
    private int cooldownTimer;
    private int age;
    private int spawned;
    private boolean active;

    public IcicleRainGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss = boss;
        this.radius = params.has("radius") ? params.get("radius").getAsDouble() : 10.0;
        this.height = params.has("height") ? params.get("height").getAsInt() : 20;
        this.icicleCount = params.has("icicleCount") ? params.get("icicleCount").getAsInt() : 18;
        this.damage = params.has("damage") ? params.get("damage").getAsFloat() : 8.0f;
        this.warnTicks = params.has("warnTicks") ? params.get("warnTicks").getAsInt() : 30;
        this.rainTicks = params.has("rainTicks") ? params.get("rainTicks").getAsInt() : 25;
        this.cooldown = cooldownTicks;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) {
            cooldownTimer--;
            return false;
        }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive() && boss.distanceTo(target) <= radius + 16.0;
    }

    @Override
    public boolean canContinueToUse() {
        return active;
    }

    @Override
    public void start() {
        boss.playAttackAnimation(4);
        age = 0;
        spawned = 0;
        active = true;
        impacts.clear();
        falling.clear();

        LivingEntity target = boss.getTarget();
        if (target == null || !target.isAlive()) {
            active = false;
            return;
        }

        ServerLevel level = (ServerLevel) boss.level();
        double cx = target.getX();
        double cz = target.getZ();
        for (int i = 0; i < icicleCount; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(level.getRandom().nextDouble()) * radius;
            double x = cx + Math.cos(angle) * distance;
            double z = cz + Math.sin(angle) * distance;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(x, target.getY(), z).getX(),
                    BlockPos.containing(x, target.getY(), z).getZ());
            impacts.add(new ImpactPoint(x, z, ground));
        }

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.6f, 0.55f);
    }

    @Override
    public void tick() {
        if (!active || boss.level().isClientSide) return;
        ServerLevel level = (ServerLevel) boss.level();
        age++;

        if (age <= warnTicks) {
            renderWarnings(level);
            if (age == Math.max(1, warnTicks - 8)) {
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 1.5f, 0.55f);
            }
            return;
        }

        int elapsedRain = age - warnTicks;
        int shouldHaveSpawned = Math.min(icicleCount,
                (int) Math.ceil((double) elapsedRain * icicleCount / Math.max(1, rainTicks)));
        while (spawned < shouldHaveSpawned) {
            spawnIcicle(level, impacts.get(spawned));
            spawned++;
        }

        tickFalling(level);
        if (spawned >= icicleCount && falling.isEmpty() && elapsedRain >= rainTicks) {
            active = false;
        }
    }

    private void renderWarnings(ServerLevel level) {
        if (age % 3 != 0) return;
        for (ImpactPoint point : impacts) {
            double pulse = 0.95 + Math.sin(age * 0.55) * 0.18;
            // Eight clear points keep the warning readable without flooding the
            // server with hundreds of individual particle packets in the final phase.
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * 2.0 * i / 8.0 + age * 0.035;
                double x = point.x + Math.cos(angle) * pulse;
                double z = point.z + Math.sin(angle) * pulse;
                level.sendParticles(ParticleTypes.END_ROD,
                        x, point.groundY + 0.08, z, 1, 0, 0, 0, 0);
            }
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    point.x, point.groundY + 0.12, point.z,
                    1, 0.18, 0.02, 0.18, 0.01);
        }
    }

    private void spawnIcicle(ServerLevel level, ImpactPoint point) {
        int x = net.minecraft.util.Mth.floor(point.x);
        int z = net.minecraft.util.Mth.floor(point.z);
        BlockPos spawnPos = new BlockPos(x, point.groundY + Math.max(8, height), z);
        // Never replace an arena/build block to create a projectile, and keep the
        // temporary entity inside the dimension's build limits.
        if (spawnPos.getY() < level.getMinBuildHeight()
                || spawnPos.getY() >= level.getMaxBuildHeight()
                || !level.isEmptyBlock(spawnPos)) {
            return;
        }
        BlockState state = level.getRandom().nextBoolean()
                ? Blocks.PACKED_ICE.defaultBlockState()
                : Blocks.BLUE_ICE.defaultBlockState();

        BlockPos landingPos = BlockPos.containing(point.x, point.groundY + 1, point.z);
        if (!level.isEmptyBlock(landingPos)) {
            // A blocked impact lane cancels this icicle; never deal instant damage
            // through a ceiling or solid arena block.
            return;
        }

        BlockState previous = level.getBlockState(spawnPos);
        level.setBlock(spawnPos, state, 3);
        FallingBlockEntity entity = FallingBlockEntity.fall(level, spawnPos, state);
        level.setBlock(spawnPos, previous, 3);
        if (entity.isRemoved()) return;
        entity.dropItem = false;
        entity.disableDrop(); // Destroy the falling block on landing instead of placing arena ice.
        entity.setDeltaMovement(0.0, -0.85, 0.0);
        falling.add(new ActiveIcicle(entity, point));
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                point.x, point.groundY + height, point.z,
                7, 0.18, 0.25, 0.18, 0.02);
    }

    private void tickFalling(ServerLevel level) {
        Iterator<ActiveIcicle> iterator = falling.iterator();
        while (iterator.hasNext()) {
            ActiveIcicle icicle = iterator.next();
            FallingBlockEntity entity = icicle.entity;
            // A discarded projectile must never trigger a delayed impact, even if
            // its last position happened to be below the impact threshold.
            if (entity.isRemoved()) {
                iterator.remove();
                continue;
            }
            if (entity.getY() <= icicle.impact.groundY + 1.1) {
                shatter(level, icicle.impact);
                entity.discard();
                iterator.remove();
                continue;
            }

            // A roof or a newly placed block can stop a falling icicle above its
            // marked target. Cancel it quietly so the attack cannot hang forever
            // and never deal damage through the obstruction.
            if (entity.getY() >= icicle.lastY - 0.01) {
                icicle.stalledTicks++;
            } else {
                icicle.lastY = entity.getY();
                icicle.stalledTicks = 0;
            }
            if (icicle.stalledTicks >= 8) {
                entity.discard();
                iterator.remove();
            }
        }
    }

    private void shatter(ServerLevel level, ImpactPoint point) {
        BlockParticleOption iceShards = new BlockParticleOption(
                ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState());
        level.sendParticles(iceShards,
                point.x, point.groundY + 0.35, point.z,
                22, 0.75, 0.22, 0.75, 0.16);
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                point.x, point.groundY + 0.5, point.z,
                18, 0.65, 0.3, 0.65, 0.12);
        level.playSound(null, point.x, point.groundY, point.z,
                SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 0.85f, 1.35f);

        AABB hitBox = new AABB(
                point.x - 1.65, point.groundY - 0.5, point.z - 1.65,
                point.x + 1.65, point.groundY + 2.5, point.z + 1.65);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, hitBox,
                e -> e.isAlive() && boss.canAbilityHit(e))) {
            boss.dealAbilityDamage(victim, boss.damageSources().mobAttack(boss), damage);
            Vec3 away = victim.position().subtract(point.x, point.groundY, point.z).normalize();
            victim.push(away.x * 0.55, 0.28, away.z * 0.55);
            victim.hurtMarked = true;
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active = false;
        // The projectiles are temporary attack entities. Remove airborne ones on
        // interruption, but never clear blocks at their landing coordinates: those
        // coordinates may have been modified by players during the warning/volley.
        if (!boss.level().isClientSide) {
            for (ActiveIcicle icicle : falling) {
                if (!icicle.entity.isRemoved()) icicle.entity.discard();
            }
        }
        falling.clear();
        impacts.clear();
        spawned = 0;
        age = 0;
    }

    private record ImpactPoint(double x, double z, int groundY) {}

    private static final class ActiveIcicle {
        private final FallingBlockEntity entity;
        private final ImpactPoint impact;
        private double lastY;
        private int stalledTicks;

        private ActiveIcicle(FallingBlockEntity entity, ImpactPoint impact) {
            this.entity = entity;
            this.impact = impact;
            this.lastY = entity.getY();
        }
    }
}
