package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.Comparator;

public final class MinionBasicAttackGoal extends Goal {
    private static final double ACQUIRE_RANGE = 20.0;
    private static final double ATTACK_RANGE = 3.2;
    // Minecraft health is measured in half-hearts: 3 HP = 1.5 hearts.
    // Magic damage bypasses ordinary armor points, so full diamond does not
    // reduce the requested per-hit damage.
    private static final float SNOVER_HIT_DAMAGE = 3.0F;

    private final BossEntity minion;
    private final int cooldown;
    private int cooldownTicks;

    public MinionBasicAttackGoal(BossEntity minion, int cooldown) {
        this.minion = minion;
        this.cooldown = Math.max(10, cooldown);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private LivingEntity findTarget() {
        // Snover are defensive: they only retaliate against the living player
        // who most recently damaged this individual minion. They never scan
        // for or initiate combat against nearby players on their own.
        LivingEntity attacker = minion.getLastHurtByMob();
        if (attacker instanceof Player player && player.isAlive()
                && !player.isSpectator() && !player.isCreative()
                && minion.canAbilityHit(player)) {
            if (minion.getTarget() != player) minion.setTarget(player);
            return player;
        }

        // Clear stale/passive targets so a Snover does not keep attacking
        // someone after its retaliation target is no longer valid.
        if (minion.getTarget() != null) minion.setTarget(null);
        return null;
    }

    @Override public boolean canUse() {
        return findTarget() != null;
    }

    @Override public boolean canContinueToUse() {
        LivingEntity target = findTarget();
        return target != null && target.isAlive();
    }

    @Override public void tick() {
        LivingEntity target = findTarget();
        if (target == null || !target.isAlive()) return;

        minion.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (minion.distanceToSqr(target) > ATTACK_RANGE * ATTACK_RANGE) {
            minion.getNavigation().moveTo(target, 1.18);
            return;
        }

        minion.getNavigation().stop();
        if (cooldownTicks > 0) cooldownTicks--;
        if (cooldownTicks <= 0 && minion.hasLineOfSight(target)
                && minion.canAbilityHit(target)) {
            minion.swing(InteractionHand.MAIN_HAND);
            minion.playAttackAnimation(1);

            // Fixed 3 HP (1.5 hearts) per successful strike, even through full
            // diamond armor. The ordinary mobAttack source was armor-reduced.
            target.hurt(minion.damageSources().magic(), SNOVER_HIT_DAMAGE);

            if (minion.level() instanceof ServerLevel level) {
                level.playSound(null, minion.getX(), minion.getY(), minion.getZ(),
                        SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 0.8F, 0.75F);
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        target.getX(), target.getY() + target.getBbHeight() * 0.55, target.getZ(),
                        4, 0.18, 0.18, 0.18, 0.05);
                level.sendParticles(ParticleTypes.SNOWFLAKE,
                        target.getX(), target.getY() + 0.8, target.getZ(),
                        7, 0.22, 0.25, 0.22, 0.04);
            }
            cooldownTicks = cooldown;
        }
    }

    @Override public void stop() { minion.getNavigation().stop(); }
}
