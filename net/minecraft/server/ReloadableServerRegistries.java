package net.minecraft.server;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.slf4j.Logger;

public class ReloadableServerRegistries {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final RegistrationInfo DEFAULT_REGISTRATION_INFO = new RegistrationInfo(Optional.empty(), Lifecycle.experimental());

    public static CompletableFuture<ReloadableServerRegistries.LoadResult> reload(
        LayeredRegistryAccess<RegistryLayer> registryAccess,
        List<Registry.PendingTags<?>> postponedTags,
        ResourceManager resourceManager,
        Executor backgroundExecutor
    ) {
        List<HolderLookup.RegistryLookup<?>> list = TagLoader.buildUpdatedLookups(registryAccess.getAccessForLoading(RegistryLayer.RELOADABLE), postponedTags);
        HolderLookup.Provider provider = HolderLookup.Provider.create(list.stream());
        RegistryOps<JsonElement> registryOps = provider.createSerializationContext(JsonOps.INSTANCE);
        List<CompletableFuture<WritableRegistry<?>>> list1 = LootDataType.values()
            .map(lootDataType -> scheduleRegistryLoad((LootDataType<?>)lootDataType, registryOps, resourceManager, backgroundExecutor))
            .toList();
        CompletableFuture<List<WritableRegistry<?>>> completableFuture = Util.sequence(list1);
        return completableFuture.thenApplyAsync(
            list2 -> createAndValidateFullContext(registryAccess, provider, (List<WritableRegistry<?>>)list2), backgroundExecutor
        );
    }

    private static <T> CompletableFuture<WritableRegistry<?>> scheduleRegistryLoad(
        LootDataType<T> lootDataType, RegistryOps<JsonElement> ops, ResourceManager resourceManager, Executor backgroundExecutor
    ) {
        return CompletableFuture.supplyAsync(
            () -> {
                WritableRegistry<T> writableRegistry = new MappedRegistry<>(lootDataType.registryKey(), Lifecycle.experimental());
                Map<ResourceLocation, T> map = new HashMap<>();
                SimpleJsonResourceReloadListener.scanDirectory(resourceManager, lootDataType.registryKey(), ops, lootDataType.codec(), map);
                map.forEach(
                    (resourceLocation, object) -> writableRegistry.register(
                        ResourceKey.create(lootDataType.registryKey(), resourceLocation), (T)object, DEFAULT_REGISTRATION_INFO
                    )
                );
                TagLoader.loadTagsForRegistry(resourceManager, writableRegistry);
                return writableRegistry;
            },
            backgroundExecutor
        );
    }

    private static ReloadableServerRegistries.LoadResult createAndValidateFullContext(
        LayeredRegistryAccess<RegistryLayer> registryAccess, HolderLookup.Provider provider, List<WritableRegistry<?>> registries
    ) {
        LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess = createUpdatedRegistries(registryAccess, registries);
        HolderLookup.Provider provider1 = concatenateLookups(provider, layeredRegistryAccess.getLayer(RegistryLayer.RELOADABLE));
        validateLootRegistries(provider1);
        return new ReloadableServerRegistries.LoadResult(layeredRegistryAccess, provider1);
    }

    private static HolderLookup.Provider concatenateLookups(HolderLookup.Provider lookup1, HolderLookup.Provider lookup2) {
        return HolderLookup.Provider.create(Stream.concat(lookup1.listRegistries(), lookup2.listRegistries()));
    }

    private static void validateLootRegistries(HolderLookup.Provider registries) {
        ProblemReporter.Collector collector = new ProblemReporter.Collector();
        ValidationContext validationContext = new ValidationContext(collector, LootContextParamSets.ALL_PARAMS, registries);
        LootDataType.values().forEach(lootDataType -> validateRegistry(validationContext, (LootDataType<?>)lootDataType, registries));
        collector.get().forEach((string, string1) -> LOGGER.warn("Found loot table element validation problem in {}: {}", string, string1));
    }

    private static LayeredRegistryAccess<RegistryLayer> createUpdatedRegistries(
        LayeredRegistryAccess<RegistryLayer> registryAccess, List<WritableRegistry<?>> registries
    ) {
        return registryAccess.replaceFrom(RegistryLayer.RELOADABLE, new RegistryAccess.ImmutableRegistryAccess(registries).freeze());
    }

    private static <T> void validateRegistry(ValidationContext context, LootDataType<T> lootDataType, HolderLookup.Provider registries) {
        HolderLookup<T> holderLookup = registries.lookupOrThrow(lootDataType.registryKey());
        holderLookup.listElements().forEach(reference -> lootDataType.runValidation(context, reference.key(), reference.value()));
    }

    public static class Holder {
        private final HolderLookup.Provider registries;

        public Holder(HolderLookup.Provider registries) {
            this.registries = registries;
        }

        public HolderGetter.Provider lookup() {
            return this.registries;
        }

        public Collection<ResourceLocation> getKeys(ResourceKey<? extends Registry<?>> registryKey) {
            return this.registries.lookupOrThrow(registryKey).listElementIds().map(ResourceKey::location).toList();
        }

        public LootTable getLootTable(ResourceKey<LootTable> lootTableKey) {
            return this.registries
                .lookup(Registries.LOOT_TABLE)
                .flatMap(registryLookup -> registryLookup.get(lootTableKey))
                .map(net.minecraft.core.Holder::value)
                .orElse(LootTable.EMPTY);
        }
    }

    public record LoadResult(LayeredRegistryAccess<RegistryLayer> layers, HolderLookup.Provider lookupWithUpdatedTags) {
    }
}
