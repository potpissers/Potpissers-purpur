package net.minecraft.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.tags.TagLoader;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.animal.WolfVariant;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.providers.EnchantmentProvider;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPreset;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.slf4j.Logger;

public class RegistryDataLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Comparator<ResourceKey<?>> ERROR_KEY_COMPARATOR = Comparator.<ResourceKey<?>, ResourceLocation>comparing(ResourceKey::registry).thenComparing(ResourceKey::location);
    private static final RegistrationInfo NETWORK_REGISTRATION_INFO = new RegistrationInfo(Optional.empty(), Lifecycle.experimental());
    private static final Function<Optional<KnownPack>, RegistrationInfo> REGISTRATION_INFO_CACHE = Util.memoize(optional -> {
        Lifecycle lifecycle = optional.map(KnownPack::isVanilla).map(_boolean -> Lifecycle.stable()).orElse(Lifecycle.experimental());
        return new RegistrationInfo(optional, lifecycle);
    });
    public static final List<RegistryDataLoader.RegistryData<?>> WORLDGEN_REGISTRIES = List.of(
        new RegistryDataLoader.RegistryData<>(Registries.DIMENSION_TYPE, DimensionType.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.BIOME, Biome.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.CHAT_TYPE, ChatType.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.CONFIGURED_CARVER, ConfiguredWorldCarver.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.CONFIGURED_FEATURE, ConfiguredFeature.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.PLACED_FEATURE, PlacedFeature.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.STRUCTURE, Structure.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.STRUCTURE_SET, StructureSet.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.PROCESSOR_LIST, StructureProcessorType.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.TEMPLATE_POOL, StructureTemplatePool.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.NOISE_SETTINGS, NoiseGeneratorSettings.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.NOISE, NormalNoise.NoiseParameters.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.DENSITY_FUNCTION, DensityFunction.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.WORLD_PRESET, WorldPreset.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.FLAT_LEVEL_GENERATOR_PRESET, FlatLevelGeneratorPreset.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.TRIM_PATTERN, TrimPattern.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.TRIM_MATERIAL, TrimMaterial.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.TRIAL_SPAWNER_CONFIG, TrialSpawnerConfig.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.WOLF_VARIANT, WolfVariant.DIRECT_CODEC, true),
        new RegistryDataLoader.RegistryData<>(Registries.PAINTING_VARIANT, PaintingVariant.DIRECT_CODEC, true),
        new RegistryDataLoader.RegistryData<>(Registries.DAMAGE_TYPE, DamageType.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, MultiNoiseBiomeSourceParameterList.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.BANNER_PATTERN, BannerPattern.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.ENCHANTMENT, Enchantment.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.ENCHANTMENT_PROVIDER, EnchantmentProvider.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.JUKEBOX_SONG, JukeboxSong.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.INSTRUMENT, Instrument.DIRECT_CODEC)
    );
    public static final List<RegistryDataLoader.RegistryData<?>> DIMENSION_REGISTRIES = List.of(
        new RegistryDataLoader.RegistryData<>(Registries.LEVEL_STEM, LevelStem.CODEC)
    );
    public static final List<RegistryDataLoader.RegistryData<?>> SYNCHRONIZED_REGISTRIES = List.of(
        new RegistryDataLoader.RegistryData<>(Registries.BIOME, Biome.NETWORK_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.CHAT_TYPE, ChatType.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.TRIM_PATTERN, TrimPattern.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.TRIM_MATERIAL, TrimMaterial.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.WOLF_VARIANT, WolfVariant.DIRECT_CODEC, true),
        new RegistryDataLoader.RegistryData<>(Registries.PAINTING_VARIANT, PaintingVariant.DIRECT_CODEC, true),
        new RegistryDataLoader.RegistryData<>(Registries.DIMENSION_TYPE, DimensionType.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.DAMAGE_TYPE, DamageType.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.BANNER_PATTERN, BannerPattern.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.ENCHANTMENT, Enchantment.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.JUKEBOX_SONG, JukeboxSong.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.INSTRUMENT, Instrument.DIRECT_CODEC)
    );

    public static RegistryAccess.Frozen load(
        ResourceManager resourceManager, List<HolderLookup.RegistryLookup<?>> registryLookups, List<RegistryDataLoader.RegistryData<?>> registryData
    ) {
        return load((loader, infoLookup) -> loader.loadFromResources(resourceManager, infoLookup), registryLookups, registryData);
    }

    public static RegistryAccess.Frozen load(
        Map<ResourceKey<? extends Registry<?>>, RegistryDataLoader.NetworkedRegistryData> elements,
        ResourceProvider resourceProvider,
        List<HolderLookup.RegistryLookup<?>> registryLookups,
        List<RegistryDataLoader.RegistryData<?>> registryData
    ) {
        return load((loader, infoLookup) -> loader.loadFromNetwork(elements, resourceProvider, infoLookup), registryLookups, registryData);
    }

    private static RegistryAccess.Frozen load(
        RegistryDataLoader.LoadingFunction loadingFunction,
        List<HolderLookup.RegistryLookup<?>> registryLookups,
        List<RegistryDataLoader.RegistryData<?>> registryData
    ) {
        Map<ResourceKey<?>, Exception> map = new HashMap<>();
        List<RegistryDataLoader.Loader<?>> list = registryData.stream()
            .map(registryData1 -> registryData1.create(Lifecycle.stable(), map))
            .collect(Collectors.toUnmodifiableList());
        RegistryOps.RegistryInfoLookup registryInfoLookup = createContext(registryLookups, list);
        list.forEach(loader -> loadingFunction.apply((RegistryDataLoader.Loader<?>)loader, registryInfoLookup));
        list.forEach(loader -> {
            Registry<?> registry = loader.registry();

            try {
                registry.freeze();
            } catch (Exception var4x) {
                map.put(registry.key(), var4x);
            }

            if (loader.data.requiredNonEmpty && registry.size() == 0) {
                map.put(registry.key(), new IllegalStateException("Registry must be non-empty"));
            }
        });
        if (!map.isEmpty()) {
            throw logErrors(map);
        } else {
            return new RegistryAccess.ImmutableRegistryAccess(list.stream().map(RegistryDataLoader.Loader::registry).toList()).freeze();
        }
    }

    private static RegistryOps.RegistryInfoLookup createContext(
        List<HolderLookup.RegistryLookup<?>> registryLookups, List<RegistryDataLoader.Loader<?>> loaders
    ) {
        final Map<ResourceKey<? extends Registry<?>>, RegistryOps.RegistryInfo<?>> map = new HashMap<>();
        registryLookups.forEach(registryLookup -> map.put(registryLookup.key(), createInfoForContextRegistry((HolderLookup.RegistryLookup<?>)registryLookup)));
        loaders.forEach(loader -> map.put(loader.registry.key(), createInfoForNewRegistry(loader.registry)));
        return new RegistryOps.RegistryInfoLookup() {
            @Override
            public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey) {
                return Optional.ofNullable((RegistryOps.RegistryInfo<T>)map.get(registryKey));
            }
        };
    }

    private static <T> RegistryOps.RegistryInfo<T> createInfoForNewRegistry(WritableRegistry<T> registry) {
        return new RegistryOps.RegistryInfo<>(registry, registry.createRegistrationLookup(), registry.registryLifecycle());
    }

    private static <T> RegistryOps.RegistryInfo<T> createInfoForContextRegistry(HolderLookup.RegistryLookup<T> registryLookup) {
        return new RegistryOps.RegistryInfo<>(registryLookup, registryLookup, registryLookup.registryLifecycle());
    }

    private static ReportedException logErrors(Map<ResourceKey<?>, Exception> errors) {
        printFullDetailsToLog(errors);
        return createReportWithBriefInfo(errors);
    }

    private static void printFullDetailsToLog(Map<ResourceKey<?>, Exception> errors) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        Map<ResourceLocation, Map<ResourceLocation, Exception>> map = errors.entrySet()
            .stream()
            .collect(Collectors.groupingBy(entry -> entry.getKey().registry(), Collectors.toMap(entry -> entry.getKey().location(), Entry::getValue)));
        map.entrySet().stream().sorted(Entry.comparingByKey()).forEach(entry -> {
            printWriter.printf("> Errors in registry %s:%n", entry.getKey());
            entry.getValue().entrySet().stream().sorted(Entry.comparingByKey()).forEach(entry1 -> {
                printWriter.printf(">> Errors in element %s:%n", entry1.getKey());
                entry1.getValue().printStackTrace(printWriter);
            });
        });
        printWriter.flush();
        LOGGER.error("Registry loading errors:\n{}", stringWriter);
    }

    private static ReportedException createReportWithBriefInfo(Map<ResourceKey<?>, Exception> errors) {
        CrashReport crashReport = CrashReport.forThrowable(new IllegalStateException("Failed to load registries due to errors"), "Registry Loading");
        CrashReportCategory crashReportCategory = crashReport.addCategory("Loading info");
        crashReportCategory.setDetail(
            "Errors",
            () -> {
                StringBuilder stringBuilder = new StringBuilder();
                errors.entrySet()
                    .stream()
                    .sorted(Entry.comparingByKey(ERROR_KEY_COMPARATOR))
                    .forEach(
                        entry -> stringBuilder.append("\n\t\t")
                            .append(entry.getKey().registry())
                            .append("/")
                            .append(entry.getKey().location())
                            .append(": ")
                            .append(entry.getValue().getMessage())
                    );
                return stringBuilder.toString();
            }
        );
        return new ReportedException(crashReport);
    }

    private static <E> void loadElementFromResource(
        WritableRegistry<E> registry,
        Decoder<E> codec,
        RegistryOps<JsonElement> ops,
        ResourceKey<E> resourceKey,
        Resource resource,
        RegistrationInfo registrationInfo
    ) throws IOException {
        try (Reader reader = resource.openAsReader()) {
            JsonElement jsonElement = JsonParser.parseReader(reader);
            DataResult<E> dataResult = codec.parse(ops, jsonElement);
            E orThrow = dataResult.getOrThrow();
            registry.register(resourceKey, orThrow, registrationInfo);
        }
    }

    static <E> void loadContentsFromManager(
        ResourceManager resourceManager,
        RegistryOps.RegistryInfoLookup registryInfoLookup,
        WritableRegistry<E> registry,
        Decoder<E> codec,
        Map<ResourceKey<?>, Exception> loadingErrors
    ) {
        FileToIdConverter fileToIdConverter = FileToIdConverter.registry(registry.key());
        RegistryOps<JsonElement> registryOps = RegistryOps.create(JsonOps.INSTANCE, registryInfoLookup);

        for (Entry<ResourceLocation, Resource> entry : fileToIdConverter.listMatchingResources(resourceManager).entrySet()) {
            ResourceLocation resourceLocation = entry.getKey();
            ResourceKey<E> resourceKey = ResourceKey.create(registry.key(), fileToIdConverter.fileToId(resourceLocation));
            Resource resource = entry.getValue();
            RegistrationInfo registrationInfo = REGISTRATION_INFO_CACHE.apply(resource.knownPackInfo());

            try {
                loadElementFromResource(registry, codec, registryOps, resourceKey, resource, registrationInfo);
            } catch (Exception var14) {
                loadingErrors.put(
                    resourceKey,
                    new IllegalStateException(String.format(Locale.ROOT, "Failed to parse %s from pack %s", resourceLocation, resource.sourcePackId()), var14)
                );
            }
        }

        TagLoader.loadTagsForRegistry(resourceManager, registry);
    }

    static <E> void loadContentsFromNetwork(
        Map<ResourceKey<? extends Registry<?>>, RegistryDataLoader.NetworkedRegistryData> elements,
        ResourceProvider resourceProvider,
        RegistryOps.RegistryInfoLookup registryInfoLookup,
        WritableRegistry<E> registry,
        Decoder<E> codec,
        Map<ResourceKey<?>, Exception> loadingErrors
    ) {
        RegistryDataLoader.NetworkedRegistryData networkedRegistryData = elements.get(registry.key());
        if (networkedRegistryData != null) {
            RegistryOps<Tag> registryOps = RegistryOps.create(NbtOps.INSTANCE, registryInfoLookup);
            RegistryOps<JsonElement> registryOps1 = RegistryOps.create(JsonOps.INSTANCE, registryInfoLookup);
            FileToIdConverter fileToIdConverter = FileToIdConverter.registry(registry.key());

            for (RegistrySynchronization.PackedRegistryEntry packedRegistryEntry : networkedRegistryData.elements) {
                ResourceKey<E> resourceKey = ResourceKey.create(registry.key(), packedRegistryEntry.id());
                Optional<Tag> optional = packedRegistryEntry.data();
                if (optional.isPresent()) {
                    try {
                        DataResult<E> dataResult = codec.parse(registryOps, optional.get());
                        E orThrow = dataResult.getOrThrow();
                        registry.register(resourceKey, orThrow, NETWORK_REGISTRATION_INFO);
                    } catch (Exception var16) {
                        loadingErrors.put(
                            resourceKey, new IllegalStateException(String.format(Locale.ROOT, "Failed to parse value %s from server", optional.get()), var16)
                        );
                    }
                } else {
                    ResourceLocation resourceLocation = fileToIdConverter.idToFile(packedRegistryEntry.id());

                    try {
                        Resource resourceOrThrow = resourceProvider.getResourceOrThrow(resourceLocation);
                        loadElementFromResource(registry, codec, registryOps1, resourceKey, resourceOrThrow, NETWORK_REGISTRATION_INFO);
                    } catch (Exception var17) {
                        loadingErrors.put(resourceKey, new IllegalStateException("Failed to parse local data", var17));
                    }
                }
            }

            TagLoader.loadTagsFromNetwork(networkedRegistryData.tags, registry);
        }
    }

    record Loader<T>(RegistryDataLoader.RegistryData<T> data, WritableRegistry<T> registry, Map<ResourceKey<?>, Exception> loadingErrors) {
        public void loadFromResources(ResourceManager resouceManager, RegistryOps.RegistryInfoLookup registryInfoLookup) {
            RegistryDataLoader.loadContentsFromManager(resouceManager, registryInfoLookup, this.registry, this.data.elementCodec, this.loadingErrors);
        }

        public void loadFromNetwork(
            Map<ResourceKey<? extends Registry<?>>, RegistryDataLoader.NetworkedRegistryData> elements,
            ResourceProvider resourceProvider,
            RegistryOps.RegistryInfoLookup registryInfoLookup
        ) {
            RegistryDataLoader.loadContentsFromNetwork(
                elements, resourceProvider, registryInfoLookup, this.registry, this.data.elementCodec, this.loadingErrors
            );
        }
    }

    @FunctionalInterface
    interface LoadingFunction {
        void apply(RegistryDataLoader.Loader<?> loader, RegistryOps.RegistryInfoLookup registryInfoLookup);
    }

    public record NetworkedRegistryData(List<RegistrySynchronization.PackedRegistryEntry> elements, TagNetworkSerialization.NetworkPayload tags) {
    }

    public record RegistryData<T>(ResourceKey<? extends Registry<T>> key, Codec<T> elementCodec, boolean requiredNonEmpty) {
        RegistryData(ResourceKey<? extends Registry<T>> key, Codec<T> elementCodec) {
            this(key, elementCodec, false);
        }

        RegistryDataLoader.Loader<T> create(Lifecycle registryLifecycle, Map<ResourceKey<?>, Exception> loadingErrors) {
            WritableRegistry<T> writableRegistry = new MappedRegistry<>(this.key, registryLifecycle);
            return new RegistryDataLoader.Loader<>(this, writableRegistry, loadingErrors);
        }

        public void runWithArguments(BiConsumer<ResourceKey<? extends Registry<T>>, Codec<T>> runner) {
            runner.accept(this.key, this.elementCodec);
        }
    }
}
