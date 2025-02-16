package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;

public class GameModeCommand {
    public static final int PERMISSION_LEVEL = 2;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("gamemode")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("gamemode", GameModeArgument.gameMode())
                        .executes(
                            context -> setMode(
                                context, Collections.singleton(context.getSource().getPlayerOrException()), GameModeArgument.getGameMode(context, "gamemode")
                            )
                        )
                        .then(
                            Commands.argument("target", EntityArgument.players())
                                .executes(
                                    context -> setMode(context, EntityArgument.getPlayers(context, "target"), GameModeArgument.getGameMode(context, "gamemode"))
                                )
                        )
                )
        );
    }

    private static void logGamemodeChange(CommandSourceStack source, ServerPlayer player, GameType gameType) {
        Component component = Component.translatable("gameMode." + gameType.getName());
        if (source.getEntity() == player) {
            source.sendSuccess(() -> Component.translatable("commands.gamemode.success.self", component), true);
        } else {
            if (source.getLevel().getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK)) {
                player.sendSystemMessage(Component.translatable("gameMode.changed", component));
            }

            source.sendSuccess(() -> Component.translatable("commands.gamemode.success.other", player.getDisplayName(), component), true);
        }
    }

    private static int setMode(CommandContext<CommandSourceStack> source, Collection<ServerPlayer> players, GameType gameType) {
        int i = 0;

        for (ServerPlayer serverPlayer : players) {
            if (serverPlayer.setGameMode(gameType)) {
                logGamemodeChange(source.getSource(), serverPlayer, gameType);
                i++;
            }
        }

        return i;
    }
}
