package net.minecraft.gametest.framework;

import com.mojang.brigadier.Message;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class TestClassNameArgument implements ArgumentType<String> {
    private static final Collection<String> EXAMPLES = Arrays.asList("techtests", "mobtests");

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        String unquotedString = reader.readUnquotedString();
        if (GameTestRegistry.isTestClass(unquotedString)) {
            return unquotedString;
        } else {
            Message message = Component.literal("No such test class: " + unquotedString);
            throw new CommandSyntaxException(new SimpleCommandExceptionType(message), message);
        }
    }

    public static TestClassNameArgument testClassName() {
        return new TestClassNameArgument();
    }

    public static String getTestClassName(CommandContext<CommandSourceStack> context, String argument) {
        return context.getArgument(argument, String.class);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(GameTestRegistry.getAllTestClassNames().stream(), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
