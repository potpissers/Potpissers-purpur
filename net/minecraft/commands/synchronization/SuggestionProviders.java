package net.minecraft.commands.synchronization;

import com.google.common.collect.Maps;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public class SuggestionProviders {
    private static final Map<ResourceLocation, SuggestionProvider<SharedSuggestionProvider>> PROVIDERS_BY_NAME = Maps.newHashMap();
    private static final ResourceLocation DEFAULT_NAME = ResourceLocation.withDefaultNamespace("ask_server");
    public static final SuggestionProvider<SharedSuggestionProvider> ASK_SERVER = register(
        DEFAULT_NAME, (context, builder) -> context.getSource().customSuggestion(context)
    );
    public static final SuggestionProvider<CommandSourceStack> AVAILABLE_SOUNDS = register(
        ResourceLocation.withDefaultNamespace("available_sounds"),
        (context, builder) -> SharedSuggestionProvider.suggestResource(context.getSource().getAvailableSounds(), builder)
    );
    public static final SuggestionProvider<CommandSourceStack> SUMMONABLE_ENTITIES = register(
        ResourceLocation.withDefaultNamespace("summonable_entities"),
        (commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggestResource(
            BuiltInRegistries.ENTITY_TYPE
                .stream()
                .filter(entityType -> entityType.isEnabled(commandContext.getSource().enabledFeatures()) && entityType.canSummon()),
            suggestionsBuilder,
            EntityType::getKey,
            entityType -> Component.translatable(Util.makeDescriptionId("entity", EntityType.getKey(entityType)))
        )
    );

    public static <S extends SharedSuggestionProvider> SuggestionProvider<S> register(
        ResourceLocation name, SuggestionProvider<SharedSuggestionProvider> provider
    ) {
        if (PROVIDERS_BY_NAME.containsKey(name)) {
            throw new IllegalArgumentException("A command suggestion provider is already registered with the name " + name);
        } else {
            PROVIDERS_BY_NAME.put(name, provider);
            return (SuggestionProvider<S>) new Wrapper(name, provider);
        }
    }

    public static SuggestionProvider<SharedSuggestionProvider> getProvider(ResourceLocation name) {
        return PROVIDERS_BY_NAME.getOrDefault(name, ASK_SERVER);
    }

    public static ResourceLocation getName(SuggestionProvider<SharedSuggestionProvider> provider) {
        return provider instanceof SuggestionProviders.Wrapper ? ((SuggestionProviders.Wrapper)provider).name : DEFAULT_NAME;
    }

    public static SuggestionProvider<SharedSuggestionProvider> safelySwap(SuggestionProvider<SharedSuggestionProvider> provider) {
        return provider instanceof SuggestionProviders.Wrapper ? provider : ASK_SERVER;
    }

    protected static class Wrapper implements SuggestionProvider<SharedSuggestionProvider> {
        private final SuggestionProvider<SharedSuggestionProvider> delegate;
        final ResourceLocation name;

        public Wrapper(ResourceLocation name, SuggestionProvider<SharedSuggestionProvider> delegate) {
            this.delegate = delegate;
            this.name = name;
        }

        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<SharedSuggestionProvider> context, SuggestionsBuilder builder) throws CommandSyntaxException {
            return this.delegate.getSuggestions(context, builder);
        }
    }
}
