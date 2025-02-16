package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;

public class SeedCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, boolean notIntegratedServer) {
        dispatcher.register(Commands.literal("seed").requires(source -> !notIntegratedServer || source.hasPermission(2)).executes(context -> {
            long seed = context.getSource().getLevel().getSeed();
            Component component = ComponentUtils.copyOnClickText(String.valueOf(seed));
            context.getSource().sendSuccess(() -> Component.translatable("commands.seed.success", component), false);
            return (int)seed;
        }));
    }
}
