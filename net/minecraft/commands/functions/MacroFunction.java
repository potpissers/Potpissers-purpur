package net.minecraft.commands.functions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class MacroFunction<T extends ExecutionCommandSource<T>> implements CommandFunction<T> {
    private static final DecimalFormat DECIMAL_FORMAT = Util.make(new DecimalFormat("#"), format -> {
        format.setMaximumFractionDigits(15);
        format.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
    });
    private static final int MAX_CACHE_ENTRIES = 8;
    private final List<String> parameters;
    private final Object2ObjectLinkedOpenHashMap<List<String>, InstantiatedFunction<T>> cache = new Object2ObjectLinkedOpenHashMap<>(8, 0.25F);
    private final ResourceLocation id;
    private final List<MacroFunction.Entry<T>> entries;

    public MacroFunction(ResourceLocation id, List<MacroFunction.Entry<T>> entries, List<String> parameters) {
        this.id = id;
        this.entries = entries;
        this.parameters = parameters;
    }

    @Override
    public ResourceLocation id() {
        return this.id;
    }

    @Override
    public InstantiatedFunction<T> instantiate(@Nullable CompoundTag arguments, CommandDispatcher<T> dispatcher) throws FunctionInstantiationException {
        if (arguments == null) {
            throw new FunctionInstantiationException(Component.translatable("commands.function.error.missing_arguments", Component.translationArg(this.id())));
        } else {
            List<String> list = new ArrayList<>(this.parameters.size());

            for (String string : this.parameters) {
                Tag tag = arguments.get(string);
                if (tag == null) {
                    throw new FunctionInstantiationException(
                        Component.translatable("commands.function.error.missing_argument", Component.translationArg(this.id()), string)
                    );
                }

                list.add(stringify(tag));
            }

            InstantiatedFunction<T> instantiatedFunction = this.cache.getAndMoveToLast(list);
            if (instantiatedFunction != null) {
                return instantiatedFunction;
            } else {
                if (this.cache.size() >= 8) {
                    this.cache.removeFirst();
                }

                InstantiatedFunction<T> instantiatedFunction1 = this.substituteAndParse(this.parameters, list, dispatcher);
                this.cache.put(list, instantiatedFunction1);
                return instantiatedFunction1;
            }
        }
    }

    private static String stringify(Tag tag) {
        if (tag instanceof FloatTag floatTag) {
            return DECIMAL_FORMAT.format(floatTag.getAsFloat());
        } else if (tag instanceof DoubleTag doubleTag) {
            return DECIMAL_FORMAT.format(doubleTag.getAsDouble());
        } else if (tag instanceof ByteTag byteTag) {
            return String.valueOf(byteTag.getAsByte());
        } else if (tag instanceof ShortTag shortTag) {
            return String.valueOf(shortTag.getAsShort());
        } else {
            return tag instanceof LongTag longTag ? String.valueOf(longTag.getAsLong()) : tag.getAsString();
        }
    }

    private static void lookupValues(List<String> arguments, IntList parameters, List<String> output) {
        output.clear();
        parameters.forEach(i -> output.add(arguments.get(i)));
    }

    private InstantiatedFunction<T> substituteAndParse(List<String> argumentNames, List<String> argumentValues, CommandDispatcher<T> dispatcher) throws FunctionInstantiationException {
        List<UnboundEntryAction<T>> list = new ArrayList<>(this.entries.size());
        List<String> list1 = new ArrayList<>(argumentValues.size());

        for (MacroFunction.Entry<T> entry : this.entries) {
            lookupValues(argumentValues, entry.parameters(), list1);
            list.add(entry.instantiate(list1, dispatcher, this.id));
        }

        return new PlainTextFunction<>(this.id().withPath(string -> string + "/" + argumentNames.hashCode()), list);
    }

    interface Entry<T> {
        IntList parameters();

        UnboundEntryAction<T> instantiate(List<String> arguments, CommandDispatcher<T> dispatcher, ResourceLocation function) throws FunctionInstantiationException;
    }

    static class MacroEntry<T extends ExecutionCommandSource<T>> implements MacroFunction.Entry<T> {
        private final StringTemplate template;
        private final IntList parameters;
        private final T compilationContext;

        public MacroEntry(StringTemplate template, IntList parameters, T compilationContext) {
            this.template = template;
            this.parameters = parameters;
            this.compilationContext = compilationContext;
        }

        @Override
        public IntList parameters() {
            return this.parameters;
        }

        @Override
        public UnboundEntryAction<T> instantiate(List<String> arguments, CommandDispatcher<T> dispatcher, ResourceLocation function) throws FunctionInstantiationException {
            String string = this.template.substitute(arguments);

            try {
                return CommandFunction.parseCommand(dispatcher, this.compilationContext, new StringReader(string));
            } catch (CommandSyntaxException var6) {
                throw new FunctionInstantiationException(
                    Component.translatable("commands.function.error.parse", Component.translationArg(function), string, var6.getMessage())
                );
            }
        }
    }

    static class PlainTextEntry<T> implements MacroFunction.Entry<T> {
        private final UnboundEntryAction<T> compiledAction;

        public PlainTextEntry(UnboundEntryAction<T> compiledAction) {
            this.compiledAction = compiledAction;
        }

        @Override
        public IntList parameters() {
            return IntLists.emptyList();
        }

        @Override
        public UnboundEntryAction<T> instantiate(List<String> arguments, CommandDispatcher<T> dispatcher, ResourceLocation function) {
            return this.compiledAction;
        }
    }
}
