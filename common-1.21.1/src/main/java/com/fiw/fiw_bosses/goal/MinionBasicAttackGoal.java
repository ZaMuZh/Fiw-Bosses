package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

public final class MinionBasicAttackGoal extends Goal {
    private final BossEntity minion;
    private final int cooldown;
    private int cooldownTicks;

    public MinionBasicAttackGoal(BossEntity minion, int cooldown) {
        this.minion = minion;
        this.cooldown = Math.max(10, cooldown);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() {
        LivingEntity target = minion.getTarget();
        return target != null && target.isAlive() && minion.canAbilityHit(target);
    }

    @Override public boolean canContinueToUse() {
        LivingEntity target = minion.getTarget();
        return target != null && target.isAlive() && minion.canAbilityHit(target);
    }

    @Override public void tick() {
        LivingEntity target = minion.getTarget();
        if (target == null || !target.isAlive()) return;
        minion.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (minion.distanceToSqr(target) > 3.2 * 3.2) {
            minion.getNavigation().moveTo(target, 1.18);
            return;
        }
        minion.getNavigation().stop();
        if (cooldownTicks > 0) cooldownTicks--;
        if (cooldownTicks <= 0) {
            minion.swing(InteractionHand.MAIN_HAND);
            minion.playAttackAnimation(1);
            float damage = (float) minion.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            minion.dealAbilityDamage(target, minion.damageSources().mobAttack(minion), damage);
            Vec3 away = target.position().subtract(minion.position()).normalize();
            target.push(away.x * 0.28, 0.18, away.z * 0.28);
            target.hurtMarked = true;
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
