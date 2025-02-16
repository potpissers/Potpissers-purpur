package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.RangeArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomSequences;

public class RandomCommand {
    private static final SimpleCommandExceptionType ERROR_RANGE_TOO_LARGE = new SimpleCommandExceptionType(
        Component.translatable("commands.random.error.range_too_large")
    );
    private static final SimpleCommandExceptionType ERROR_RANGE_TOO_SMALL = new SimpleCommandExceptionType(
        Component.translatable("commands.random.error.range_too_small")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("random")
                .then(drawRandomValueTree("value", false))
                .then(drawRandomValueTree("roll", true))
                .then(
                    Commands.literal("reset")
                        .requires(commandSourceStack -> commandSourceStack.hasPermission(2))
                        .then(
                            Commands.literal("*")
                                .executes(commandContext -> resetAllSequences(commandContext.getSource()))
                                .then(
                                    Commands.argument("seed", IntegerArgumentType.integer())
                                        .executes(
                                            commandContext -> resetAllSequencesAndSetNewDefaults(
                                                commandContext.getSource(), IntegerArgumentType.getInteger(commandContext, "seed"), true, true
                                            )
                                        )
                                        .then(
                                            Commands.argument("includeWorldSeed", BoolArgumentType.bool())
                                                .executes(
                                                    commandContext -> resetAllSequencesAndSetNewDefaults(
                                                        commandContext.getSource(),
                                                        IntegerArgumentType.getInteger(commandContext, "seed"),
                                                        BoolArgumentType.getBool(commandContext, "includeWorldSeed"),
                                                        true
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("includeSequenceId", BoolArgumentType.bool())
                                                        .executes(
                                                            commandContext -> resetAllSequencesAndSetNewDefaults(
                                                                commandContext.getSource(),
                                                                IntegerArgumentType.getInteger(commandContext, "seed"),
                                                                BoolArgumentType.getBool(commandContext, "includeWorldSeed"),
                                                                BoolArgumentType.getBool(commandContext, "includeSequenceId")
                                                            )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.argument("sequence", ResourceLocationArgument.id())
                                .suggests(RandomCommand::suggestRandomSequence)
                                .executes(
                                    commandContext -> resetSequence(commandContext.getSource(), ResourceLocationArgument.getId(commandContext, "sequence"))
                                )
                                .then(
                                    Commands.argument("seed", IntegerArgumentType.integer())
                                        .executes(
                                            commandContext -> resetSequence(
                                                commandContext.getSource(),
                                                ResourceLocationArgument.getId(commandContext, "sequence"),
                                                IntegerArgumentType.getInteger(commandContext, "seed"),
                                                true,
                                                true
                                            )
                                        )
                                        .then(
                                            Commands.argument("includeWorldSeed", BoolArgumentType.bool())
                                                .executes(
                                                    commandContext -> resetSequence(
                                                        commandContext.getSource(),
                                                        ResourceLocationArgument.getId(commandContext, "sequence"),
                                                        IntegerArgumentType.getInteger(commandContext, "seed"),
                                                        BoolArgumentType.getBool(commandContext, "includeWorldSeed"),
                                                        true
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("includeSequenceId", BoolArgumentType.bool())
                                                        .executes(
                                                            commandContext -> resetSequence(
                                                                commandContext.getSource(),
                                                                ResourceLocationArgument.getId(commandContext, "sequence"),
                                                                IntegerArgumentType.getInteger(commandContext, "seed"),
                                                                BoolArgumentType.getBool(commandContext, "includeWorldSeed"),
                                                                BoolArgumentType.getBool(commandContext, "includeSequenceId")
                                                            )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> drawRandomValueTree(String subcommand, boolean displayResult) {
        return Commands.literal(subcommand)
            .then(
                Commands.argument("range", RangeArgument.intRange())
                    .executes(
                        commandContext -> randomSample(commandContext.getSource(), RangeArgument.Ints.getRange(commandContext, "range"), null, displayResult)
                    )
                    .then(
                        Commands.argument("sequence", ResourceLocationArgument.id())
                            .suggests(RandomCommand::suggestRandomSequence)
                            .requires(commandSourceStack -> commandSourceStack.hasPermission(2))
                            .executes(
                                commandContext -> randomSample(
                                    commandContext.getSource(),
                                    RangeArgument.Ints.getRange(commandContext, "range"),
                                    ResourceLocationArgument.getId(commandContext, "sequence"),
                                    displayResult
                                )
                            )
                    )
            );
    }

    private static CompletableFuture<Suggestions> suggestRandomSequence(CommandContext<CommandSourceStack> context, SuggestionsBuilder suggestionsBuilder) {
        List<String> list = Lists.newArrayList();
        context.getSource().getLevel().getRandomSequences().forAllSequences((resourceLocation, randomSequence) -> list.add(resourceLocation.toString()));
        return SharedSuggestionProvider.suggest(list, suggestionsBuilder);
    }

    private static int randomSample(CommandSourceStack source, MinMaxBounds.Ints range, @Nullable ResourceLocation sequence, boolean displayResult) throws CommandSyntaxException {
        RandomSource randomSequence;
        if (sequence != null) {
            randomSequence = source.getLevel().getRandomSequence(sequence);
        } else {
            randomSequence = source.getLevel().getRandom();
        }

        int i = range.min().orElse(Integer.MIN_VALUE);
        int i1 = range.max().orElse(Integer.MAX_VALUE);
        long l = (long)i1 - i;
        if (l == 0L) {
            throw ERROR_RANGE_TOO_SMALL.create();
        } else if (l >= 2147483647L) {
            throw ERROR_RANGE_TOO_LARGE.create();
        } else {
            int i2 = Mth.randomBetweenInclusive(randomSequence, i, i1);
            if (displayResult) {
                source.getServer()
                    .getPlayerList()
                    .broadcastSystemMessage(Component.translatable("commands.random.roll", source.getDisplayName(), i2, i, i1), false);
            } else {
                source.sendSuccess(() -> Component.translatable("commands.random.sample.success", i2), false);
            }

            return i2;
        }
    }

    private static int resetSequence(CommandSourceStack source, ResourceLocation sequence) throws CommandSyntaxException {
        source.getLevel().getRandomSequences().reset(sequence);
        source.sendSuccess(() -> Component.translatable("commands.random.reset.success", Component.translationArg(sequence)), false);
        return 1;
    }

    private static int resetSequence(CommandSourceStack source, ResourceLocation sequence, int seed, boolean includeWorldSeed, boolean includeSequenceId) throws CommandSyntaxException {
        source.getLevel().getRandomSequences().reset(sequence, seed, includeWorldSeed, includeSequenceId);
        source.sendSuccess(() -> Component.translatable("commands.random.reset.success", Component.translationArg(sequence)), false);
        return 1;
    }

    private static int resetAllSequences(CommandSourceStack source) {
        int i = source.getLevel().getRandomSequences().clear();
        source.sendSuccess(() -> Component.translatable("commands.random.reset.all.success", i), false);
        return i;
    }

    private static int resetAllSequencesAndSetNewDefaults(CommandSourceStack source, int seed, boolean includeWorldSeed, boolean includeSequenceId) {
        RandomSequences randomSequences = source.getLevel().getRandomSequences();
        randomSequences.setSeedDefaults(seed, includeWorldSeed, includeSequenceId);
        int i = randomSequences.clear();
        source.sendSuccess(() -> Component.translatable("commands.random.reset.all.success", i), false);
        return i;
    }
}
