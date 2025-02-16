package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;

public class TellRawCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("tellraw")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("targets", EntityArgument.players())
                        .then(
                            Commands.argument("message", ComponentArgument.textComponent(context))
                                .executes(
                                    context1 -> {
                                        int i = 0;

                                        for (ServerPlayer serverPlayer : EntityArgument.getPlayers(context1, "targets")) {
                                            serverPlayer.sendSystemMessage(
                                                ComponentUtils.updateForEntity(
                                                    context1.getSource(), ComponentArgument.getComponent(context1, "message"), serverPlayer, 0
                                                ),
                                                false
                                            );
                                            i++;
                                        }

                                        return i;
                                    }
                                )
                        )
                )
        );
    }
}
