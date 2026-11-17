package com.fiw.fiw_bosses.client.renderer;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

final class DisguiseRenderHelper {
    private DisguiseRenderHelper() {}

    static boolean render(BossEntity source, float entityYaw, float partialTick, PoseStack poseStack,
                          MultiBufferSource buffer, int packedLight, EntityRenderDispatcher dispatcher) {
        Entity fake = createFake(source);
        if (fake == null) return false;

        // Match the Fabric renderer: eased idle motion plus distinct attack wind-ups,
        // impact poses and recovery. This keeps Cobblemon disguises animated on NeoForge too.
        float time = source.tickCount + partialTick;
        float idleSway = (float) Math.sin(time * 0.075f) * 1.15f;
        float bob = (float) Math.sin(time * 0.11f) * 0.018f;
        float attackLean = 0.0f;
        float attackTwist = 0.0f;
        int attackTicks = source.getAttackAnimationTicks();
        int attackStyle = source.getAttackAnimation();
        if (attackTicks > 0) {
            float duration = attackStyle == 6 ? 50.0f
                    : ((attackStyle == 2 || attackStyle == 3) ? 34.0f : 28.0f);
            float progress = 1.0f - attackTicks / duration;
            float anticipation = smoothstep(0.0f, 0.36f, progress);
            float strike = smoothstep(0.36f, 0.55f, progress);
            float recovery = smoothstep(0.55f, 1.0f, progress);
            switch (attackStyle) {
                case 2 -> {
                    attackLean = -11.0f * anticipation + 25.0f * strike - 14.0f * recovery;
                    attackTwist = (float) Math.sin(progress * Math.PI) * 3.0f;
                    bob -= (float) Math.sin(progress * Math.PI) * 0.075f;
                }
                case 3 -> {
                    attackLean = -14.0f * anticipation + 30.0f * strike - 16.0f * recovery;
                    attackTwist = (float) Math.sin(progress * Math.PI) * 2.0f;
                    bob -= (float) Math.sin(progress * Math.PI) * 0.095f;
                }
                case 4 -> {
                    attackLean = 5.0f * anticipation - 2.0f * strike - 3.0f * recovery;
                    attackTwist = (float) Math.sin(progress * Math.PI * 2.0f) * 4.0f;
                    bob += (float) Math.sin(progress * Math.PI) * 0.045f;
                }
                case 5 -> {
                    attackLean = 12.0f * anticipation + 8.0f * strike - 20.0f * recovery;
                    attackTwist = (float) Math.sin(progress * Math.PI) * 6.0f;
                }
                case 6 -> {
                    float pulse = (float) Math.sin(progress * Math.PI);
                    attackLean = -10.0f * anticipation + 7.0f * strike - 4.0f * recovery;
                    attackTwist = (float) Math.sin(progress * Math.PI * 2.0f) * 3.0f;
                    bob += pulse * 0.16f;
                }
                default -> {
                    attackLean = -6.0f * anticipation + 14.0f * strike - 8.0f * recovery;
                    attackTwist = (float) Math.sin(progress * Math.PI) * 9.0f;
                    bob += (float) Math.sin(progress * Math.PI) * 0.025f;
                }
            }
        }

        poseStack.pushPose();
        poseStack.translate(0.0, bob, 0.0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(idleSway + attackTwist));
        poseStack.mulPose(Axis.XP.rotationDegrees(attackLean));
        try {
            dispatcher.render(fake, 0.0, 0.0, 0.0, entityYaw, partialTick, poseStack, buffer, packedLight);
        } finally {
            poseStack.popPose();
        }
        return true;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = net.minecraft.util.Mth.clamp((value - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static Entity createFake(BossEntity source) {
        String disguiseId = source.getDisguiseEntity();
        if (disguiseId == null || disguiseId.isBlank()) {
            disguiseId = ClientDisguiseManager.getDisguise(source.getId());
        }
        if (disguiseId == null || disguiseId.isBlank()) return null;

        ResourceLocation id = ResourceLocation.tryParse(disguiseId);
        if (id == null) return null;
        if (FiwBossesCore.MOD_ID.equals(id.getNamespace())) return null;

        Optional<EntityType<?>> typeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (typeOpt.isEmpty() || typeOpt.get() == source.getType()) return null;

        Entity fake = typeOpt.get().create(source.level());
        if (fake == null) return null;

        fake.moveTo(source.getX(), source.getY(), source.getZ(), source.getYRot(), source.getXRot());
        fake.tickCount = source.tickCount;
        fake.setCustomName(source.getCustomName());
        fake.setCustomNameVisible(source.isCustomNameVisible());
        fake.setInvisible(source.isInvisible());

        if (fake instanceof LivingEntity living) {
            // The disguise is drawn from this throwaway mob, so it needs the boss's `scale`.
            var scale = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
            if (scale != null) {
                scale.setBaseValue(source.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SCALE));
            }
            living.yBodyRot = source.yBodyRot;
            living.yBodyRotO = source.yBodyRotO;
            living.yHeadRot = source.yHeadRot;
            living.yHeadRotO = source.yHeadRotO;
            living.hurtTime = source.hurtTime;
            living.deathTime = source.deathTime;
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                living.setItemSlot(slot, source.getItemBySlot(slot));
            }
            copyAnimation(source, living);
        }

        return fake;
    }

    /**
     * The throwaway entity never walks or swings, so without this a disguised boss glides with
     * frozen limbs. WalkAnimationState only exposes setSpeed/update, so replay the boss's state:
     * update(f, g) sets speedOld = speed, speed += (f - speed) * g, position += speed.
     */
    private static void copyAnimation(BossEntity source, LivingEntity fake) {
        float position = source.walkAnimation.position();
        float speed = source.walkAnimation.speed();
        float speedOld = source.walkAnimation.speed(0.0f);
        fake.walkAnimation.setSpeed(position - speed);
        fake.walkAnimation.update(0.0f, 0.0f);   // position = position - speed
        fake.walkAnimation.setSpeed(speedOld);
        fake.walkAnimation.update(speed, 1.0f);  // speedOld, speed and position now match
        fake.attackAnim = source.attackAnim;
        fake.oAttackAnim = source.oAttackAnim;
        fake.swinging = source.swinging;
        fake.swingingArm = source.swingingArm;
        fake.swingTime = source.swingTime;
    }
}
