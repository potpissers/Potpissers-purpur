package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameRules;

public class GameRuleCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext commandBuildContext) {
        final LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder = Commands.literal("gamerule").requires(source -> source.hasPermission(2));
        new GameRules(commandBuildContext.enabledFeatures())
            .visitGameRuleTypes(
                new GameRules.GameRuleTypeVisitor() {
                    @Override
                    public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                        LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder1 = Commands.literal(key.getId());
                        literalArgumentBuilder.then(
                            literalArgumentBuilder1.executes(context -> GameRuleCommand.queryRule(context.getSource(), key))
                                .then(type.createArgument("value").executes(context -> GameRuleCommand.setRule(context, key)))
                        );
                    }
                }
            );
        dispatcher.register(literalArgumentBuilder);
    }

    static <T extends GameRules.Value<T>> int setRule(CommandContext<CommandSourceStack> source, GameRules.Key<T> gameRule) {
        CommandSourceStack commandSourceStack = source.getSource();
        T rule = commandSourceStack.getLevel().getGameRules().getRule(gameRule); // CraftBukkit
        rule.setFromArgument(source, "value", gameRule); // Paper - Add WorldGameRuleChangeEvent
        commandSourceStack.sendSuccess(() -> Component.translatable("commands.gamerule.set", gameRule.getId(), rule.toString()), true);
        return rule.getCommandResult();
    }

    static <T extends GameRules.Value<T>> int queryRule(CommandSourceStack source, GameRules.Key<T> gameRule) {
        T rule = source.getLevel().getGameRules().getRule(gameRule); // CraftBukkit
        source.sendSuccess(() -> Component.translatable("commands.gamerule.query", gameRule.getId(), rule.toString()), false);
        return rule.getCommandResult();
    }
}
