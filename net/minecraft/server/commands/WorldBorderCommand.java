package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec2Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec2;

public class WorldBorderCommand {
    private static final SimpleCommandExceptionType ERROR_SAME_CENTER = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.center.failed")
    );
    private static final SimpleCommandExceptionType ERROR_SAME_SIZE = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.set.failed.nochange")
    );
    private static final SimpleCommandExceptionType ERROR_TOO_SMALL = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.set.failed.small")
    );
    private static final SimpleCommandExceptionType ERROR_TOO_BIG = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.set.failed.big", 5.999997E7F)
    );
    private static final SimpleCommandExceptionType ERROR_TOO_FAR_OUT = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.set.failed.far", 2.9999984E7)
    );
    private static final SimpleCommandExceptionType ERROR_SAME_WARNING_TIME = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.warning.time.failed")
    );
    private static final SimpleCommandExceptionType ERROR_SAME_WARNING_DISTANCE = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.warning.distance.failed")
    );
    private static final SimpleCommandExceptionType ERROR_SAME_DAMAGE_BUFFER = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.damage.buffer.failed")
    );
    private static final SimpleCommandExceptionType ERROR_SAME_DAMAGE_AMOUNT = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.damage.amount.failed")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("worldborder")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("distance", DoubleArgumentType.doubleArg(-5.999997E7F, 5.999997E7F))
                                .executes(
                                    context -> setSize(
                                        context.getSource(),
                                        context.getSource().getLevel().getWorldBorder().getSize() + DoubleArgumentType.getDouble(context, "distance"),
                                        0L
                                    )
                                )
                                .then(
                                    Commands.argument("time", IntegerArgumentType.integer(0))
                                        .executes(
                                            context -> setSize(
                                                context.getSource(),
                                                context.getSource().getLevel().getWorldBorder().getSize() + DoubleArgumentType.getDouble(context, "distance"),
                                                context.getSource().getLevel().getWorldBorder().getLerpRemainingTime()
                                                    + IntegerArgumentType.getInteger(context, "time") * 1000L
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("set")
                        .then(
                            Commands.argument("distance", DoubleArgumentType.doubleArg(-5.999997E7F, 5.999997E7F))
                                .executes(context -> setSize(context.getSource(), DoubleArgumentType.getDouble(context, "distance"), 0L))
                                .then(
                                    Commands.argument("time", IntegerArgumentType.integer(0))
                                        .executes(
                                            context -> setSize(
                                                context.getSource(),
                                                DoubleArgumentType.getDouble(context, "distance"),
                                                IntegerArgumentType.getInteger(context, "time") * 1000L
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("center")
                        .then(
                            Commands.argument("pos", Vec2Argument.vec2())
                                .executes(context -> setCenter(context.getSource(), Vec2Argument.getVec2(context, "pos")))
                        )
                )
                .then(
                    Commands.literal("damage")
                        .then(
                            Commands.literal("amount")
                                .then(
                                    Commands.argument("damagePerBlock", FloatArgumentType.floatArg(0.0F))
                                        .executes(context -> setDamageAmount(context.getSource(), FloatArgumentType.getFloat(context, "damagePerBlock")))
                                )
                        )
                        .then(
                            Commands.literal("buffer")
                                .then(
                                    Commands.argument("distance", FloatArgumentType.floatArg(0.0F))
                                        .executes(context -> setDamageBuffer(context.getSource(), FloatArgumentType.getFloat(context, "distance")))
                                )
                        )
                )
                .then(Commands.literal("get").executes(context -> getSize(context.getSource())))
                .then(
                    Commands.literal("warning")
                        .then(
                            Commands.literal("distance")
                                .then(
                                    Commands.argument("distance", IntegerArgumentType.integer(0))
                                        .executes(context -> setWarningDistance(context.getSource(), IntegerArgumentType.getInteger(context, "distance")))
                                )
                        )
                        .then(
                            Commands.literal("time")
                                .then(
                                    Commands.argument("time", IntegerArgumentType.integer(0))
                                        .executes(context -> setWarningTime(context.getSource(), IntegerArgumentType.getInteger(context, "time")))
                                )
                        )
                )
        );
    }

    private static int setDamageBuffer(CommandSourceStack source, float distance) throws CommandSyntaxException {
        WorldBorder worldBorder = source.getServer().overworld().getWorldBorder();
        if (worldBorder.getDamageSafeZone() == distance) {
            throw ERROR_SAME_DAMAGE_BUFFER.create();
        } else {
            worldBorder.setDamageSafeZone(distance);
            source.sendSuccess(() -> Component.translatable("commands.worldborder.damage.buffer.success", String.format(Locale.ROOT, "%.2f", distance)), true);
            return (int)distance;
        }
    }

    private static int setDamageAmount(CommandSourceStack source, float damagePerBlock) throws CommandSyntaxException {
        WorldBorder worldBorder = source.getServer().overworld().getWorldBorder();
        if (worldBorder.getDamagePerBlock() == damagePerBlock) {
            throw ERROR_SAME_DAMAGE_AMOUNT.create();
        } else {
            worldBorder.setDamagePerBlock(damagePerBlock);
            source.sendSuccess(
                () -> Component.translatable("commands.worldborder.damage.amount.success", String.format(Locale.ROOT, "%.2f", damagePerBlock)), true
            );
            return (int)damagePerBlock;
        }
    }

    private static int setWarningTime(CommandSourceStack source, int time) throws CommandSyntaxException {
        WorldBorder worldBorder = source.getServer().overworld().getWorldBorder();
        if (worldBorder.getWarningTime() == time) {
            throw ERROR_SAME_WARNING_TIME.create();
        } else {
            worldBorder.setWarningTime(time);
            source.sendSuccess(() -> Component.translatable("commands.worldborder.warning.time.success", time), true);
            return time;
        }
    }

    private static int setWarningDistance(CommandSourceStack source, int distance) throws CommandSyntaxException {
        WorldBorder worldBorder = source.getServer().overworld().getWorldBorder();
        if (worldBorder.getWarningBlocks() == distance) {
            throw ERROR_SAME_WARNING_DISTANCE.create();
        } else {
            worldBorder.setWarningBlocks(distance);
            source.sendSuccess(() -> Component.translatable("commands.worldborder.warning.distance.success", distance), true);
            return distance;
        }
    }

    private static int getSize(CommandSourceStack source) {
        double size = source.getServer().overworld().getWorldBorder().getSize();
        source.sendSuccess(() -> Component.translatable("commands.worldborder.get", String.format(Locale.ROOT, "%.0f", size)), false);
        return Mth.floor(size + 0.5);
    }

    private static int setCenter(CommandSourceStack source, Vec2 pos) throws CommandSyntaxException {
        WorldBorder worldBorder = source.getServer().overworld().getWorldBorder();
        if (worldBorder.getCenterX() == pos.x && worldBorder.getCenterZ() == pos.y) {
            throw ERROR_SAME_CENTER.create();
        } else if (!(Math.abs(pos.x) > 2.9999984E7) && !(Math.abs(pos.y) > 2.9999984E7)) {
            worldBorder.setCenter(pos.x, pos.y);
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.worldborder.center.success", String.format(Locale.ROOT, "%.2f", pos.x), String.format(Locale.ROOT, "%.2f", pos.y)
                ),
                true
            );
            return 0;
        } else {
            throw ERROR_TOO_FAR_OUT.create();
        }
    }

    private static int setSize(CommandSourceStack source, double newSize, long time) throws CommandSyntaxException {
        WorldBorder worldBorder = source.getServer().overworld().getWorldBorder();
        double size = worldBorder.getSize();
        if (size == newSize) {
            throw ERROR_SAME_SIZE.create();
        } else if (newSize < 1.0) {
            throw ERROR_TOO_SMALL.create();
        } else if (newSize > 5.999997E7F) {
            throw ERROR_TOO_BIG.create();
        } else {
            if (time > 0L) {
                worldBorder.lerpSizeBetween(size, newSize, time);
                if (newSize > size) {
                    source.sendSuccess(
                        () -> Component.translatable("commands.worldborder.set.grow", String.format(Locale.ROOT, "%.1f", newSize), Long.toString(time / 1000L)),
                        true
                    );
                } else {
                    source.sendSuccess(
                        () -> Component.translatable(
                            "commands.worldborder.set.shrink", String.format(Locale.ROOT, "%.1f", newSize), Long.toString(time / 1000L)
                        ),
                        true
                    );
                }
            } else {
                worldBorder.setSize(newSize);
                source.sendSuccess(() -> Component.translatable("commands.worldborder.set.immediate", String.format(Locale.ROOT, "%.1f", newSize)), true);
            }

            return (int)(newSize - size);
        }
    }
}
