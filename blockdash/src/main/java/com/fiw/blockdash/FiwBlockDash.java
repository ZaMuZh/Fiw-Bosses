package com.fiw.blockdash;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FiwBlockDash implements ModInitializer {
    public static final String MOD_ID = "fiw_blockdash";
    private static final Map<String, DashArena> ARENAS = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(FiwBlockDash::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(CommandManager.literal("blockdash")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("create")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id").toLowerCase();
                            ARENAS.put(id, new DashArena(id));
                            ctx.getSource().sendFeedback(() -> Text.literal("BlockDash arena created: " + id), false);
                            return 1;
                        })))
                .then(CommandManager.literal("setstart")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .executes(ctx -> {
                            DashArena a = get(ctx.getSource(), StringArgumentType.getString(ctx, "id"));
                            a.setStart(ctx.getSource().getWorld(), ctx.getSource().getPosition());
                            ctx.getSource().sendFeedback(() -> Text.literal("Start set at your position."), false);
                            return 1;
                        })))
                .then(CommandManager.literal("setend")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .executes(ctx -> {
                            DashArena a = get(ctx.getSource(), StringArgumentType.getString(ctx, "id"));
                            a.setEnd(ctx.getSource().getWorld(), ctx.getSource().getPosition());
                            ctx.getSource().sendFeedback(() -> Text.literal("Obstacle spawn/end set at your position."), false);
                            return 1;
                        })))
                .then(CommandManager.literal("width")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .then(CommandManager.argument("blocks", IntegerArgumentType.integer(3, 64))
                            .executes(ctx -> {
                                get(ctx.getSource(), StringArgumentType.getString(ctx, "id")).width = IntegerArgumentType.getInteger(ctx, "blocks");
                                ctx.getSource().sendFeedback(() -> Text.literal("Arena width updated."), false);
                                return 1;
                            }))))
                .then(CommandManager.literal("duration")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .then(CommandManager.argument("seconds", IntegerArgumentType.integer(10, 3600))
                            .executes(ctx -> {
                                get(ctx.getSource(), StringArgumentType.getString(ctx, "id")).durationSeconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                ctx.getSource().sendFeedback(() -> Text.literal("Duration updated."), false);
                                return 1;
                            }))))
                .then(CommandManager.literal("interval")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .then(CommandManager.argument("ticks", IntegerArgumentType.integer(10, 400))
                            .executes(ctx -> {
                                get(ctx.getSource(), StringArgumentType.getString(ctx, "id")).spawnInterval = IntegerArgumentType.getInteger(ctx, "ticks");
                                ctx.getSource().sendFeedback(() -> Text.literal("Spawn interval updated."), false);
                                return 1;
                            }))))
                .then(CommandManager.literal("speed")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .then(CommandManager.argument("blocksPerSecond", DoubleArgumentType.doubleArg(0.1, 10.0))
                            .executes(ctx -> {
                                get(ctx.getSource(), StringArgumentType.getString(ctx, "id")).startSpeed = DoubleArgumentType.getDouble(ctx, "blocksPerSecond");
                                ctx.getSource().sendFeedback(() -> Text.literal("Starting speed updated."), false);
                                return 1;
                            }))))
                .then(CommandManager.literal("maxspeed")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .then(CommandManager.argument("blocksPerSecond", DoubleArgumentType.doubleArg(0.1, 15.0))
                            .executes(ctx -> {
                                get(ctx.getSource(), StringArgumentType.getString(ctx, "id")).maxSpeed = DoubleArgumentType.getDouble(ctx, "blocksPerSecond");
                                ctx.getSource().sendFeedback(() -> Text.literal("Maximum speed updated."), false);
                                return 1;
                            }))))
                .then(CommandManager.literal("push")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .then(CommandManager.argument("strength", DoubleArgumentType.doubleArg(0.1, 3.0))
                            .executes(ctx -> {
                                get(ctx.getSource(), StringArgumentType.getString(ctx, "id")).pushStrength = DoubleArgumentType.getDouble(ctx, "strength");
                                ctx.getSource().sendFeedback(() -> Text.literal("Push strength updated."), false);
                                return 1;
                            }))))
                .then(CommandManager.literal("start")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .executes(ctx -> {
                            DashArena a = get(ctx.getSource(), StringArgumentType.getString(ctx, "id"));
                            a.start(ctx.getSource().getServer());
                            ctx.getSource().sendFeedback(() -> Text.literal("BlockDash started: " + a.id), false);
                            return 1;
                        })))
                .then(CommandManager.literal("stop")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .executes(ctx -> {
                            get(ctx.getSource(), StringArgumentType.getString(ctx, "id")).stop();
                            ctx.getSource().sendFeedback(() -> Text.literal("BlockDash stopped."), false);
                            return 1;
                        })))
                .then(CommandManager.literal("list")
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(() -> Text.literal("Arenas: " + String.join(", ", ARENAS.keySet())), false);
                        return 1;
                    }))
            )
        );
    }

    private static DashArena get(ServerCommandSource source, String id) {
        DashArena arena = ARENAS.get(id.toLowerCase());
        if (arena == null) throw new IllegalArgumentException("Unknown BlockDash arena: " + id);
        return arena;
    }

    private static void tick(MinecraftServer server) {
        for (DashArena arena : ARENAS.values()) arena.tick(server);
    }
}
