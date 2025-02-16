package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class DefaultGameModeCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("defaultgamemode")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("gamemode", GameModeArgument.gameMode())
                        .executes(context -> setMode(context.getSource(), GameModeArgument.getGameMode(context, "gamemode")))
                )
        );
    }

    private static int setMode(CommandSourceStack commandSource, GameType gamemode) {
        int i = 0;
        MinecraftServer server = commandSource.getServer();
        server.setDefaultGameType(gamemode);
        GameType forcedGameType = server.getForcedGameType();
        if (forcedGameType != null) {
            for (ServerPlayer serverPlayer : server.getPlayerList().getPlayers()) {
                if (serverPlayer.setGameMode(forcedGameType)) {
                    i++;
                }
            }
        }

        commandSource.sendSuccess(() -> Component.translatable("commands.defaultgamemode.success", gamemode.getLongDisplayName()), true);
        return i;
    }
}
