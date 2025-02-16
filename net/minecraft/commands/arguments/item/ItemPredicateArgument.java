package net.minecraft.commands.arguments.item;

import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.advancements.critereon.ItemSubPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.parsing.packrat.commands.Grammar;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemPredicateArgument implements ArgumentType<ItemPredicateArgument.Result> {
    private static final Collection<String> EXAMPLES = Arrays.asList("stick", "minecraft:stick", "#stick", "#stick{foo:'bar'}");
    static final DynamicCommandExceptionType ERROR_UNKNOWN_ITEM = new DynamicCommandExceptionType(
        item -> Component.translatableEscape("argument.item.id.invalid", item)
    );
    static final DynamicCommandExceptionType ERROR_UNKNOWN_TAG = new DynamicCommandExceptionType(
        tag -> Component.translatableEscape("arguments.item.tag.unknown", tag)
    );
    static final DynamicCommandExceptionType ERROR_UNKNOWN_COMPONENT = new DynamicCommandExceptionType(
        component -> Component.translatableEscape("arguments.item.component.unknown", component)
    );
    static final Dynamic2CommandExceptionType ERROR_MALFORMED_COMPONENT = new Dynamic2CommandExceptionType(
        (component, value) -> Component.translatableEscape("arguments.item.component.malformed", component, value)
    );
    static final DynamicCommandExceptionType ERROR_UNKNOWN_PREDICATE = new DynamicCommandExceptionType(
        predicate -> Component.translatableEscape("arguments.item.predicate.unknown", predicate)
    );
    static final Dynamic2CommandExceptionType ERROR_MALFORMED_PREDICATE = new Dynamic2CommandExceptionType(
        (predicate, value) -> Component.translatableEscape("arguments.item.predicate.malformed", predicate, value)
    );
    private static final ResourceLocation COUNT_ID = ResourceLocation.withDefaultNamespace("count");
    static final Map<ResourceLocation, ItemPredicateArgument.ComponentWrapper> PSEUDO_COMPONENTS = Stream.of(
            new ItemPredicateArgument.ComponentWrapper(
                COUNT_ID, itemStack -> true, MinMaxBounds.Ints.CODEC.map(ints -> itemStack -> ints.matches(itemStack.getCount()))
            )
        )
        .collect(
            Collectors.toUnmodifiableMap(
                ItemPredicateArgument.ComponentWrapper::id, componentWrapper -> (ItemPredicateArgument.ComponentWrapper)componentWrapper
            )
        );
    static final Map<ResourceLocation, ItemPredicateArgument.PredicateWrapper> PSEUDO_PREDICATES = Stream.of(
            new ItemPredicateArgument.PredicateWrapper(COUNT_ID, MinMaxBounds.Ints.CODEC.map(ints -> itemStack -> ints.matches(itemStack.getCount())))
        )
        .collect(
            Collectors.toUnmodifiableMap(
                ItemPredicateArgument.PredicateWrapper::id, predicateWrapper -> (ItemPredicateArgument.PredicateWrapper)predicateWrapper
            )
        );
    private final Grammar<List<Predicate<ItemStack>>> grammarWithContext;

    public ItemPredicateArgument(CommandBuildContext context) {
        ItemPredicateArgument.Context context1 = new ItemPredicateArgument.Context(context);
        this.grammarWithContext = ComponentPredicateParser.createGrammar(context1);
    }

    public static ItemPredicateArgument itemPredicate(CommandBuildContext context) {
        return new ItemPredicateArgument(context);
    }

    @Override
    public ItemPredicateArgument.Result parse(StringReader reader) throws CommandSyntaxException {
        return Util.allOf(this.grammarWithContext.parseForCommands(reader))::test;
    }

    public static ItemPredicateArgument.Result getItemPredicate(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, ItemPredicateArgument.Result.class);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return this.grammarWithContext.parseForSuggestions(builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    record ComponentWrapper(ResourceLocation id, Predicate<ItemStack> presenceChecker, Decoder<? extends Predicate<ItemStack>> valueChecker) {
        public static <T> ItemPredicateArgument.ComponentWrapper create(ImmutableStringReader reader, ResourceLocation id, DataComponentType<T> componentType) throws CommandSyntaxException {
            Codec<T> codec = componentType.codec();
            if (codec == null) {
                throw ItemPredicateArgument.ERROR_UNKNOWN_COMPONENT.createWithContext(reader, id);
            } else {
                return new ItemPredicateArgument.ComponentWrapper(id, itemStack -> itemStack.has(componentType), codec.map(object -> itemStack -> {
                    T object1 = itemStack.get(componentType);
                    return Objects.equals(object, object1);
                }));
            }
        }

        public Predicate<ItemStack> decode(ImmutableStringReader reader, RegistryOps<Tag> ops, Tag value) throws CommandSyntaxException {
            DataResult<? extends Predicate<ItemStack>> dataResult = this.valueChecker.parse(ops, value);
            return (Predicate<ItemStack>)dataResult.getOrThrow(
                string -> ItemPredicateArgument.ERROR_MALFORMED_COMPONENT.createWithContext(reader, this.id.toString(), string)
            );
        }
    }

    static class Context
        implements ComponentPredicateParser.Context<Predicate<ItemStack>, ItemPredicateArgument.ComponentWrapper, ItemPredicateArgument.PredicateWrapper> {
        private final HolderLookup.RegistryLookup<Item> items;
        private final HolderLookup.RegistryLookup<DataComponentType<?>> components;
        private final HolderLookup.RegistryLookup<ItemSubPredicate.Type<?>> predicates;
        private final RegistryOps<Tag> registryOps;

        Context(HolderLookup.Provider registries) {
            this.items = registries.lookupOrThrow(Registries.ITEM);
            this.components = registries.lookupOrThrow(Registries.DATA_COMPONENT_TYPE);
            this.predicates = registries.lookupOrThrow(Registries.ITEM_SUB_PREDICATE_TYPE);
            this.registryOps = registries.createSerializationContext(NbtOps.INSTANCE);
        }

        @Override
        public Predicate<ItemStack> forElementType(ImmutableStringReader reader, ResourceLocation elementType) throws CommandSyntaxException {
            Holder.Reference<Item> reference = this.items
                .get(ResourceKey.create(Registries.ITEM, elementType))
                .orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_ITEM.createWithContext(reader, elementType));
            return itemStack -> itemStack.is(reference);
        }

        @Override
        public Predicate<ItemStack> forTagType(ImmutableStringReader reader, ResourceLocation tagType) throws CommandSyntaxException {
            HolderSet<Item> holderSet = this.items
                .get(TagKey.create(Registries.ITEM, tagType))
                .orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_TAG.createWithContext(reader, tagType));
            return itemStack -> itemStack.is(holderSet);
        }

        @Override
        public ItemPredicateArgument.ComponentWrapper lookupComponentType(ImmutableStringReader reader, ResourceLocation componentType) throws CommandSyntaxException {
            ItemPredicateArgument.ComponentWrapper componentWrapper = ItemPredicateArgument.PSEUDO_COMPONENTS.get(componentType);
            if (componentWrapper != null) {
                return componentWrapper;
            } else {
                DataComponentType<?> dataComponentType = this.components
                    .get(ResourceKey.create(Registries.DATA_COMPONENT_TYPE, componentType))
                    .map(Holder::value)
                    .orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_COMPONENT.createWithContext(reader, componentType));
                return ItemPredicateArgument.ComponentWrapper.create(reader, componentType, dataComponentType);
            }
        }

        @Override
        public Predicate<ItemStack> createComponentTest(ImmutableStringReader reader, ItemPredicateArgument.ComponentWrapper context, Tag value) throws CommandSyntaxException {
            return context.decode(reader, this.registryOps, value);
        }

        @Override
        public Predicate<ItemStack> createComponentTest(ImmutableStringReader reader, ItemPredicateArgument.ComponentWrapper context) {
            return context.presenceChecker;
        }

        @Override
        public ItemPredicateArgument.PredicateWrapper lookupPredicateType(ImmutableStringReader reader, ResourceLocation predicateType) throws CommandSyntaxException {
            ItemPredicateArgument.PredicateWrapper predicateWrapper = ItemPredicateArgument.PSEUDO_PREDICATES.get(predicateType);
            return predicateWrapper != null
                ? predicateWrapper
                : this.predicates
                    .get(ResourceKey.create(Registries.ITEM_SUB_PREDICATE_TYPE, predicateType))
                    .map(ItemPredicateArgument.PredicateWrapper::new)
                    .orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_PREDICATE.createWithContext(reader, predicateType));
        }

        @Override
        public Predicate<ItemStack> createPredicateTest(ImmutableStringReader reader, ItemPredicateArgument.PredicateWrapper predicate, Tag value) throws CommandSyntaxException {
            return predicate.decode(reader, this.registryOps, value);
        }

        @Override
        public Stream<ResourceLocation> listElementTypes() {
            return this.items.listElementIds().map(ResourceKey::location);
        }

        @Override
        public Stream<ResourceLocation> listTagTypes() {
            return this.items.listTagIds().map(TagKey::location);
        }

        @Override
        public Stream<ResourceLocation> listComponentTypes() {
            return Stream.concat(
                ItemPredicateArgument.PSEUDO_COMPONENTS.keySet().stream(),
                this.components.listElements().filter(reference -> !reference.value().isTransient()).map(reference -> reference.key().location())
            );
        }

        @Override
        public Stream<ResourceLocation> listPredicateTypes() {
            return Stream.concat(ItemPredicateArgument.PSEUDO_PREDICATES.keySet().stream(), this.predicates.listElementIds().map(ResourceKey::location));
        }

        @Override
        public Predicate<ItemStack> negate(Predicate<ItemStack> value) {
            return value.negate();
        }

        @Override
        public Predicate<ItemStack> anyOf(List<Predicate<ItemStack>> values) {
            return Util.anyOf(values);
        }
    }

    record PredicateWrapper(ResourceLocation id, Decoder<? extends Predicate<ItemStack>> type) {
        public PredicateWrapper(Holder.Reference<ItemSubPredicate.Type<?>> predicate) {
            this(predicate.key().location(), predicate.value().codec().map(itemSubPredicate -> itemSubPredicate::matches));
        }

        public Predicate<ItemStack> decode(ImmutableStringReader reader, RegistryOps<Tag> ops, Tag value) throws CommandSyntaxException {
            DataResult<? extends Predicate<ItemStack>> dataResult = this.type.parse(ops, value);
            return (Predicate<ItemStack>)dataResult.getOrThrow(
                string -> ItemPredicateArgument.ERROR_MALFORMED_PREDICATE.createWithContext(reader, this.id.toString(), string)
            );
        }
    }

    public interface Result extends Predicate<ItemStack> {
    }
}
