package net.minecraft.data.loot;

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.RandomSequence;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.slf4j.Logger;

public class LootTableProvider implements DataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackOutput.PathProvider pathProvider;
    private final Set<ResourceKey<LootTable>> requiredTables;
    private final List<LootTableProvider.SubProviderEntry> subProviders;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public LootTableProvider(
        PackOutput output,
        Set<ResourceKey<LootTable>> requiredTables,
        List<LootTableProvider.SubProviderEntry> subProviders,
        CompletableFuture<HolderLookup.Provider> registries
    ) {
        this.pathProvider = output.createRegistryElementsPathProvider(Registries.LOOT_TABLE);
        this.subProviders = subProviders;
        this.requiredTables = requiredTables;
        this.registries = registries;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        return this.registries.thenCompose(provider -> this.run(output, provider));
    }

    private CompletableFuture<?> run(CachedOutput output, HolderLookup.Provider provider) {
        WritableRegistry<LootTable> writableRegistry = new MappedRegistry<>(Registries.LOOT_TABLE, Lifecycle.experimental());
        Map<RandomSupport.Seed128bit, ResourceLocation> map = new Object2ObjectOpenHashMap<>();
        this.subProviders.forEach(subProviderEntry -> subProviderEntry.provider().apply(provider).generate((resourceKey1, builder) -> {
            ResourceLocation resourceLocation = sequenceIdForLootTable(resourceKey1);
            ResourceLocation resourceLocation1 = map.put(RandomSequence.seedForKey(resourceLocation), resourceLocation);
            if (resourceLocation1 != null) {
                Util.logAndPauseIfInIde("Loot table random sequence seed collision on " + resourceLocation1 + " and " + resourceKey1.location());
            }

            builder.setRandomSequence(resourceLocation);
            LootTable lootTable = builder.setParamSet(subProviderEntry.paramSet).build();
            writableRegistry.register(resourceKey1, lootTable, RegistrationInfo.BUILT_IN);
        }));
        writableRegistry.freeze();
        ProblemReporter.Collector collector = new ProblemReporter.Collector();
        HolderGetter.Provider frozen = new RegistryAccess.ImmutableRegistryAccess(List.of(writableRegistry)).freeze();
        ValidationContext validationContext = new ValidationContext(collector, LootContextParamSets.ALL_PARAMS, frozen);

        for (ResourceKey<LootTable> resourceKey : Sets.difference(this.requiredTables, writableRegistry.registryKeySet())) {
            collector.report("Missing built-in table: " + resourceKey.location());
        }

        writableRegistry.listElements()
            .forEach(
                reference -> reference.value()
                    .validate(
                        validationContext.setContextKeySet(reference.value().getParamSet())
                            .enterElement("{" + reference.key().location() + "}", reference.key())
                    )
            );
        Multimap<String, String> multimap = collector.get();
        if (!multimap.isEmpty()) {
            multimap.forEach((string, string1) -> LOGGER.warn("Found validation problem in {}: {}", string, string1));
            throw new IllegalStateException("Failed to validate loot tables, see logs");
        } else {
            return CompletableFuture.allOf(writableRegistry.entrySet().stream().map(entry -> {
                ResourceKey<LootTable> resourceKey1 = entry.getKey();
                LootTable lootTable = entry.getValue();
                Path path = this.pathProvider.json(resourceKey1.location());
                return DataProvider.saveStable(output, provider, LootTable.DIRECT_CODEC, lootTable, path);
            }).toArray(CompletableFuture[]::new));
        }
    }

    private static ResourceLocation sequenceIdForLootTable(ResourceKey<LootTable> lootTable) {
        return lootTable.location();
    }

    @Override
    public final String getName() {
        return "Loot Tables";
    }

    public record SubProviderEntry(Function<HolderLookup.Provider, LootTableSubProvider> provider, ContextKeySet paramSet) {
    }
}
