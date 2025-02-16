package net.minecraft.commands.arguments.item;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class FunctionArgument implements ArgumentType<FunctionArgument.Result> {
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "#foo");
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_TAG = new DynamicCommandExceptionType(
        tag -> Component.translatableEscape("arguments.function.tag.unknown", tag)
    );
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_FUNCTION = new DynamicCommandExceptionType(
        function -> Component.translatableEscape("arguments.function.unknown", function)
    );

    public static FunctionArgument functions() {
        return new FunctionArgument();
    }

    @Override
    public FunctionArgument.Result parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '#') {
            reader.skip();
            final ResourceLocation resourceLocation = ResourceLocation.read(reader);
            return new FunctionArgument.Result() {
                @Override
                public Collection<CommandFunction<CommandSourceStack>> create(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                    return FunctionArgument.getFunctionTag(context, resourceLocation);
                }

                @Override
                public Pair<ResourceLocation, Either<CommandFunction<CommandSourceStack>, Collection<CommandFunction<CommandSourceStack>>>> unwrap(
                    CommandContext<CommandSourceStack> context
                ) throws CommandSyntaxException {
                    return Pair.of(resourceLocation, Either.right(FunctionArgument.getFunctionTag(context, resourceLocation)));
                }

                @Override
                public Pair<ResourceLocation, Collection<CommandFunction<CommandSourceStack>>> unwrapToCollection(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                    return Pair.of(resourceLocation, FunctionArgument.getFunctionTag(context, resourceLocation));
                }
            };
        } else {
            final ResourceLocation resourceLocation = ResourceLocation.read(reader);
            return new FunctionArgument.Result() {
                @Override
                public Collection<CommandFunction<CommandSourceStack>> create(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                    return Collections.singleton(FunctionArgument.getFunction(context, resourceLocation));
                }

                @Override
                public Pair<ResourceLocation, Either<CommandFunction<CommandSourceStack>, Collection<CommandFunction<CommandSourceStack>>>> unwrap(
                    CommandContext<CommandSourceStack> context
                ) throws CommandSyntaxException {
                    return Pair.of(resourceLocation, Either.left(FunctionArgument.getFunction(context, resourceLocation)));
                }

                @Override
                public Pair<ResourceLocation, Collection<CommandFunction<CommandSourceStack>>> unwrapToCollection(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                    return Pair.of(resourceLocation, Collections.singleton(FunctionArgument.getFunction(context, resourceLocation)));
                }
            };
        }
    }

    static CommandFunction<CommandSourceStack> getFunction(CommandContext<CommandSourceStack> context, ResourceLocation id) throws CommandSyntaxException {
        return context.getSource().getServer().getFunctions().get(id).orElseThrow(() -> ERROR_UNKNOWN_FUNCTION.create(id.toString()));
    }

    static Collection<CommandFunction<CommandSourceStack>> getFunctionTag(CommandContext<CommandSourceStack> context, ResourceLocation id) throws CommandSyntaxException {
        Collection<CommandFunction<CommandSourceStack>> tag = context.getSource().getServer().getFunctions().getTag(id);
        if (tag == null) {
            throw ERROR_UNKNOWN_TAG.create(id.toString());
        } else {
            return tag;
        }
    }

    public static Collection<CommandFunction<CommandSourceStack>> getFunctions(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return context.getArgument(name, FunctionArgument.Result.class).create(context);
    }

    public static Pair<ResourceLocation, Either<CommandFunction<CommandSourceStack>, Collection<CommandFunction<CommandSourceStack>>>> getFunctionOrTag(
        CommandContext<CommandSourceStack> context, String name
    ) throws CommandSyntaxException {
        return context.getArgument(name, FunctionArgument.Result.class).unwrap(context);
    }

    public static Pair<ResourceLocation, Collection<CommandFunction<CommandSourceStack>>> getFunctionCollection(
        CommandContext<CommandSourceStack> context, String name
    ) throws CommandSyntaxException {
        return context.getArgument(name, FunctionArgument.Result.class).unwrapToCollection(context);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public interface Result {
        Collection<CommandFunction<CommandSourceStack>> create(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;

        Pair<ResourceLocation, Either<CommandFunction<CommandSourceStack>, Collection<CommandFunction<CommandSourceStack>>>> unwrap(
            CommandContext<CommandSourceStack> context
        ) throws CommandSyntaxException;

        Pair<ResourceLocation, Collection<CommandFunction<CommandSourceStack>>> unwrapToCollection(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }
}
