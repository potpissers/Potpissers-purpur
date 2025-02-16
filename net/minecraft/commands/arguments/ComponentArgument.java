package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Arrays;
import java.util.Collection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.ParserUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

public class ComponentArgument implements ArgumentType<Component> {
    private static final Collection<String> EXAMPLES = Arrays.asList("\"hello world\"", "\"\"", "\"{\"text\":\"hello world\"}", "[\"\"]");
    public static final DynamicCommandExceptionType ERROR_INVALID_JSON = new DynamicCommandExceptionType(
        component -> Component.translatableEscape("argument.component.invalid", component)
    );
    private final HolderLookup.Provider registries;

    private ComponentArgument(HolderLookup.Provider registries) {
        this.registries = registries;
    }

    public static Component getComponent(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, Component.class);
    }

    public static ComponentArgument textComponent(CommandBuildContext context) {
        return new ComponentArgument(context);
    }

    @Override
    public Component parse(StringReader reader) throws CommandSyntaxException {
        try {
            return ParserUtils.parseJson(this.registries, reader, ComponentSerialization.CODEC);
        } catch (Exception var4) {
            String string = var4.getCause() != null ? var4.getCause().getMessage() : var4.getMessage();
            throw ERROR_INVALID_JSON.createWithContext(reader, string);
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
