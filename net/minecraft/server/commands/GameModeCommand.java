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
        // Purpur start - Gamemode extra permissions
        if (org.purpurmc.purpur.PurpurConfig.commandGamemodeRequiresPermission) {
            String gamemode = gameType.getName();
            CommandSourceStack sender = source.getSource();
            if (!sender.testPermission(2, "minecraft.command.gamemode." + gamemode)) {
                return 0;
            }
            if (sender.getEntity() instanceof ServerPlayer player && (players.size() > 1 || !players.contains(player)) && !sender.testPermission(2, "minecraft.command.gamemode." + gamemode + ".other")) {
                return 0;
            }
        }
        // Purpur end - Gamemode extra permissions
        int i = 0;

        for (ServerPlayer serverPlayer : players) {
            // Paper start - Expand PlayerGameModeChangeEvent
            org.bukkit.event.player.PlayerGameModeChangeEvent event = serverPlayer.setGameMode(gameType, org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.COMMAND, net.kyori.adventure.text.Component.empty());
            if (event != null && !event.isCancelled()) {
                logGamemodeChange(source.getSource(), serverPlayer, gameType);
                i++;
            } else if (event != null && event.cancelMessage() != null) {
                source.getSource().sendSuccess(() -> io.papermc.paper.adventure.PaperAdventure.asVanilla(event.cancelMessage()), true);
                // Paper end - Expand PlayerGameModeChangeEvent
            }
        }

        return i;
    }
}
