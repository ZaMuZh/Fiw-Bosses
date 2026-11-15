package com.fiw.blockdash;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FiwBlockDash implements ModInitializer {
    public static final String MOD_ID = "fiw_blockdash";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Map<String, DashArena> ARENAS = new ConcurrentHashMap<>();
    private static final SimpleCommandExceptionType UNKNOWN_ARENA =
        new SimpleCommandExceptionType(Text.literal("Esa arena no existe. Usa /blockdash list."));
    private static final SimpleCommandExceptionType NOT_CONFIGURED =
        new SimpleCommandExceptionType(Text.literal("Configura primero el inicio y el final de la arena."));
    private static final SimpleCommandExceptionType START_FAILED =
        new SimpleCommandExceptionType(Text.literal("No se pudo iniciar. Comprueba que haya jugadores dentro del recorrido y que inicio/final estén separados al menos 8 bloques."));

    @Override
    public void onInitialize() {
        LOGGER.info("[Fiw BlockDash] Mod cargado. Registrando /blockdash");
        ServerTickEvents.END_SERVER_TICK.register(FiwBlockDash::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = CommandManager.literal("blockdash")
                .then(CommandManager.literal("create")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .requires(ServerCommandSource::isExecutedByPlayer)
                        .executes(ctx -> {
                            String id = normalize(StringArgumentType.getString(ctx, "id"));
                            ARENAS.put(id, new DashArena(id));
                            ctx.getSource().sendFeedback(() -> Text.literal("Arena BlockDash creada: " + id), false);
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(CommandManager.literal("setstart")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .requires(ServerCommandSource::isExecutedByPlayer)
                        .executes(ctx -> {
                            DashArena arena = get(StringArgumentType.getString(ctx, "id"));
                            arena.setStart(ctx.getSource().getWorld(), ctx.getSource().getPosition());
                            ctx.getSource().sendFeedback(() -> Text.literal("Inicio guardado para " + arena.id), false);
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(CommandManager.literal("setend")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .requires(ServerCommandSource::isExecutedByPlayer)
                        .executes(ctx -> {
                            DashArena arena = get(StringArgumentType.getString(ctx, "id"));
                            arena.setEnd(ctx.getSource().getWorld(), ctx.getSource().getPosition());
                            ctx.getSource().sendFeedback(() -> Text.literal("Final guardado para " + arena.id), false);
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(CommandManager.literal("width")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .then(CommandManager.argument("blocks", IntegerArgumentType.integer(3, 64))
                            .executes(ctx -> {
                                get(StringArgumentType.getString(ctx, "id")).width = IntegerArgumentType.getInteger(ctx, "blocks");
                                ctx.getSource().sendFeedback(() -> Text.literal("Anchura actualizada."), false);
                                return Command.SINGLE_SUCCESS;
                            }))))
                .then(CommandManager.literal("duration")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .then(CommandManager.argument("seconds", IntegerArgumentType.integer(10, 3600))
                            .executes(ctx -> {
                                get(StringArgumentType.getString(ctx, "id")).durationSeconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                ctx.getSource().sendFeedback(() -> Text.literal("Duración actualizada."), false);
                                return Command.SINGLE_SUCCESS;
                            }))))
                .then(CommandManager.literal("interval")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .then(CommandManager.argument("ticks", IntegerArgumentType.integer(10, 400))
                            .executes(ctx -> {
                                get(StringArgumentType.getString(ctx, "id")).spawnInterval = IntegerArgumentType.getInteger(ctx, "ticks");
                                ctx.getSource().sendFeedback(() -> Text.literal("Intervalo actualizado."), false);
                                return Command.SINGLE_SUCCESS;
                            }))))
                .then(CommandManager.literal("speed")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .then(CommandManager.argument("blocksPerSecond", DoubleArgumentType.doubleArg(0.1, 10.0))
                            .executes(ctx -> {
                                get(StringArgumentType.getString(ctx, "id")).startSpeed = DoubleArgumentType.getDouble(ctx, "blocksPerSecond");
                                ctx.getSource().sendFeedback(() -> Text.literal("Velocidad inicial actualizada."), false);
                                return Command.SINGLE_SUCCESS;
                            }))))
                .then(CommandManager.literal("maxspeed")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .then(CommandManager.argument("blocksPerSecond", DoubleArgumentType.doubleArg(0.1, 15.0))
                            .executes(ctx -> {
                                get(StringArgumentType.getString(ctx, "id")).maxSpeed = DoubleArgumentType.getDouble(ctx, "blocksPerSecond");
                                ctx.getSource().sendFeedback(() -> Text.literal("Velocidad máxima actualizada."), false);
                                return Command.SINGLE_SUCCESS;
                            }))))
                .then(CommandManager.literal("push")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .then(CommandManager.argument("strength", DoubleArgumentType.doubleArg(0.1, 3.0))
                            .executes(ctx -> {
                                get(StringArgumentType.getString(ctx, "id")).pushStrength = DoubleArgumentType.getDouble(ctx, "strength");
                                ctx.getSource().sendFeedback(() -> Text.literal("Fuerza de empuje actualizada."), false);
                                return Command.SINGLE_SUCCESS;
                            }))))
                .then(CommandManager.literal("start")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .executes(ctx -> {
                            DashArena arena = get(StringArgumentType.getString(ctx, "id"));
                            try {
                                arena.start(ctx.getSource().getServer());
                            } catch (IllegalStateException ex) {
                                throw START_FAILED.create();
                            }
                            ctx.getSource().sendFeedback(() -> Text.literal("BlockDash iniciado: " + arena.id), false);
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(CommandManager.literal("stop")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .executes(ctx -> {
                            get(StringArgumentType.getString(ctx, "id")).stop();
                            ctx.getSource().sendFeedback(() -> Text.literal("BlockDash detenido."), false);
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(CommandManager.literal("list")
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(() -> Text.literal("Arenas BlockDash: " +
                            (ARENAS.isEmpty() ? "ninguna. Crea una con /blockdash create <id>" : String.join(", ", ARENAS.keySet()))), false);
                        return Command.SINGLE_SUCCESS;
                    }));
            dispatcher.register(root);
            dispatcher.register(CommandManager.literal("fiwblockdash").redirect(root));
            LOGGER.info("[Fiw BlockDash] Comandos /blockdash y /fiwblockdash registrados");
        });
    }

    private static String normalize(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    private static DashArena get(String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        DashArena arena = ARENAS.get(normalize(id));
        if (arena == null) throw UNKNOWN_ARENA.create();
        return arena;
    }

    private static void tick(MinecraftServer server) {
        for (DashArena arena : ARENAS.values()) arena.tick(server);
    }
}
