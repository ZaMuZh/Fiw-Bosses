package com.fiw.fiw_bosses.client.renderer;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

final class DisguiseRenderHelper {
    private DisguiseRenderHelper() {}

    private record CachedDisguise(String id, Entity entity) {}

    private static final Map<BossEntity, CachedDisguise> CACHE = new WeakHashMap<>();

    static boolean render(
            BossEntity source,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            EntityRenderDispatcher dispatcher
    ) {
        String disguiseId = source.getDisguiseEntity();

        if (disguiseId == null || disguiseId.isBlank()) {
            disguiseId = ClientDisguiseManager.getDisguise(source.getId());
        }

        if (disguiseId == null || disguiseId.isBlank()) {
            CACHE.remove(source);
            return false;
        }

        CachedDisguise cached = CACHE.get(source);
        Entity fake;

        if (cached != null && cached.id().equals(disguiseId)) {
            fake = cached.entity();
            if (fake == null) return false;
        } else {
            fake = createFake(source, disguiseId);
            if (fake == null) {
                CACHE.put(source, new CachedDisguise(disguiseId, null));
                return false;
            }
            CACHE.put(source, new CachedDisguise(disguiseId, fake));
        }

        syncFake(source, fake);
        dispatcher.render(fake, 0.0, 0.0, 0.0, entityYaw, partialTick, poseStack, buffer, packedLight);
        return true;
    }

    /**
     * Builds an unspawned Cobblemon PokemonEntity through reflection so FIW Bosses
     * can render Cobblemon species without a hard compile-time dependency on Cobblemon.
     */
    private static Entity createCobblemonDisguise(BossEntity source, String propertiesText) {
        try {
            Class<?> propertiesClass = Class.forName(
                    "com.cobblemon.mod.common.api.pokemon.PokemonProperties"
            );

            Object companion = null;
            for (Field field : propertiesClass.getFields()) {
                if (field.getName().equals("Companion") && Modifier.isStatic(field.getModifiers())) {
                    companion = field.get(null);
                    break;
                }
            }
            if (companion == null) {
                for (Field field : propertiesClass.getDeclaredFields()) {
                    if (field.getName().equals("Companion") && Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true);
                        companion = field.get(null);
                        break;
                    }
                }
            }
            if (companion == null) {
                System.err.println("[FIW Bosses] No se encontró PokemonProperties.Companion.");
                return null;
            }

            Method parseMethod = null;
            for (Method method : companion.getClass().getMethods()) {
                if (method.getName().equals("parse")
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == String.class) {
                    parseMethod = method;
                    break;
                }
            }
            if (parseMethod == null) {
                System.err.println("[FIW Bosses] No se encontró Companion.parse(String).");
                return null;
            }

            Object properties = parseMethod.invoke(companion, propertiesText);
            if (properties == null) {
                System.err.println("[FIW Bosses] No se pudieron interpretar las propiedades Cobblemon: " + propertiesText);
                return null;
            }

            Object pokemon = null;
            for (Method method : propertiesClass.getMethods()) {
                if (!method.getName().equals("create") || method.getParameterCount() != 0) continue;
                pokemon = method.invoke(properties);
                if (pokemon != null) break;
            }
            if (pokemon == null) {
                System.err.println("[FIW Bosses] No se pudo crear el Pokémon desde sus propiedades.");
                return null;
            }

            Class<?> pokemonEntityClass = Class.forName(
                    "com.cobblemon.mod.common.entity.pokemon.PokemonEntity"
            );
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE
                    .getOptional(ResourceLocation.parse("cobblemon:pokemon"))
                    .orElse(null);
            if (type == null) {
                System.err.println("[FIW Bosses] No se encontró cobblemon:pokemon.");
                return null;
            }

            Object level = source.level();
            for (var constructor : pokemonEntityClass.getConstructors()) {
                Class<?>[] params = constructor.getParameterTypes();
                if (params.length != 3) continue;

                Object[] args = new Object[3];
                boolean levelFound = false;
                boolean pokemonFound = false;
                boolean typeFound = false;
                boolean compatible = true;

                for (int i = 0; i < params.length; i++) {
                    if (!levelFound && params[i].isInstance(level)) {
                        args[i] = level;
                        levelFound = true;
                    } else if (!pokemonFound && params[i].isInstance(pokemon)) {
                        args[i] = pokemon;
                        pokemonFound = true;
                    } else if (!typeFound && params[i].isInstance(type)) {
                        args[i] = type;
                        typeFound = true;
                    } else {
                        compatible = false;
                        break;
                    }
                }

                if (compatible && levelFound && pokemonFound && typeFound) {
                    return (Entity) constructor.newInstance(args);
                }
            }

            System.err.println("[FIW Bosses] No se encontró un constructor compatible para PokemonEntity.");
        } catch (Throwable e) {
            System.err.println("[FIW Bosses] Error creando disfraz Cobblemon:");
            e.printStackTrace();
        }
        return null;
    }

    private static Entity createFake(BossEntity source, String disguiseId) {
        if (disguiseId.startsWith("cobblemon:pokemon:")) {
            String pokemonProperties = disguiseId.substring("cobblemon:pokemon:".length());
            if (!pokemonProperties.contains("level=")) pokemonProperties += " level=50";
            if (!pokemonProperties.startsWith("species=")) pokemonProperties = "species=" + pokemonProperties;
            return createCobblemonDisguise(source, pokemonProperties);
        }

        ResourceLocation id = ResourceLocation.tryParse(disguiseId);
        if (id == null || FiwBossesCore.MOD_ID.equals(id.getNamespace())) return null;

        Optional<EntityType<?>> typeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (typeOpt.isEmpty() || typeOpt.get() == source.getType()) return null;
        return typeOpt.get().create(source.level());
    }

    private static void syncFake(BossEntity source, Entity fake) {
        fake.setPos(source.getX(), source.getY(), source.getZ());
        fake.setYRot(source.getYRot());
        fake.setXRot(source.getXRot());
        fake.setYHeadRot(source.getYHeadRot());
        fake.tickCount = source.tickCount;
        fake.setCustomName(source.getCustomName());
        fake.setCustomNameVisible(source.isCustomNameVisible());
        fake.setInvisible(source.isInvisible());

        if (fake instanceof LivingEntity living) {
            var scale = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
            if (scale != null) {
                scale.setBaseValue(source.getAttributeValue(
                        net.minecraft.world.entity.ai.attributes.Attributes.SCALE
                ));
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
    }

    private static void copyAnimation(BossEntity source, LivingEntity fake) {
        float position = source.walkAnimation.position();
        float speed = source.walkAnimation.speed();
        float speedOld = source.walkAnimation.speed(0.0f);

        fake.walkAnimation.setSpeed(position - speed);
        fake.walkAnimation.update(0.0f, 0.0f);
        fake.walkAnimation.setSpeed(speedOld);
        fake.walkAnimation.update(speed, 1.0f);

        fake.attackAnim = source.attackAnim;
        fake.oAttackAnim = source.oAttackAnim;
        fake.swinging = source.swinging;
        fake.swingingArm = source.swingingArm;
        fake.swingTime = source.swingTime;
    }
}
