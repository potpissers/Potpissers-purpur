package net.minecraft.world.level.levelgen.structure;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;

public class StructureCheck {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NO_STRUCTURE = -1;
    private final ChunkScanAccess storageAccess;
    private final RegistryAccess registryAccess;
    private final StructureTemplateManager structureTemplateManager;
    private final ResourceKey<Level> dimension;
    private final ChunkGenerator chunkGenerator;
    private final RandomState randomState;
    private final LevelHeightAccessor heightAccessor;
    private final BiomeSource biomeSource;
    private final long seed;
    private final DataFixer fixerUpper;
    private final Long2ObjectMap<Object2IntMap<Structure>> loadedChunks = new Long2ObjectOpenHashMap<>();
    private final Map<Structure, Long2BooleanMap> featureChecks = new HashMap<>();

    public StructureCheck(
        ChunkScanAccess storageAccess,
        RegistryAccess registryAccess,
        StructureTemplateManager structureTemplateManager,
        ResourceKey<Level> dimension,
        ChunkGenerator chunkGenerator,
        RandomState randomState,
        LevelHeightAccessor heightAccessor,
        BiomeSource biomeSource,
        long seed,
        DataFixer fixerUpper
    ) {
        this.storageAccess = storageAccess;
        this.registryAccess = registryAccess;
        this.structureTemplateManager = structureTemplateManager;
        this.dimension = dimension;
        this.chunkGenerator = chunkGenerator;
        this.randomState = randomState;
        this.heightAccessor = heightAccessor;
        this.biomeSource = biomeSource;
        this.seed = seed;
        this.fixerUpper = fixerUpper;
    }

    public StructureCheckResult checkStart(ChunkPos chunkPos, Structure structure, StructurePlacement placement, boolean skipKnownStructures) {
        long packedChunkPos = chunkPos.toLong();
        Object2IntMap<Structure> map = this.loadedChunks.get(packedChunkPos);
        if (map != null) {
            return this.checkStructureInfo(map, structure, skipKnownStructures);
        } else {
            StructureCheckResult structureCheckResult = this.tryLoadFromStorage(chunkPos, structure, skipKnownStructures, packedChunkPos);
            if (structureCheckResult != null) {
                return structureCheckResult;
            } else if (!placement.applyAdditionalChunkRestrictions(chunkPos.x, chunkPos.z, this.seed)) {
                return StructureCheckResult.START_NOT_PRESENT;
            } else {
                boolean flag = this.featureChecks
                    .computeIfAbsent(structure, structure1 -> new Long2BooleanOpenHashMap())
                    .computeIfAbsent(packedChunkPos, l -> this.canCreateStructure(chunkPos, structure));
                return !flag ? StructureCheckResult.START_NOT_PRESENT : StructureCheckResult.CHUNK_LOAD_NEEDED;
            }
        }
    }

    private boolean canCreateStructure(ChunkPos chunkPos, Structure structure) {
        return structure.findValidGenerationPoint(
                new Structure.GenerationContext(
                    this.registryAccess,
                    this.chunkGenerator,
                    this.biomeSource,
                    this.randomState,
                    this.structureTemplateManager,
                    this.seed,
                    chunkPos,
                    this.heightAccessor,
                    structure.biomes()::contains
                )
            )
            .isPresent();
    }

    @Nullable
    private StructureCheckResult tryLoadFromStorage(ChunkPos chunkPos, Structure structure, boolean skipKnownStructures, long packedChunkPos) {
        CollectFields collectFields = new CollectFields(
            new FieldSelector(IntTag.TYPE, "DataVersion"),
            new FieldSelector("Level", "Structures", CompoundTag.TYPE, "Starts"),
            new FieldSelector("structures", CompoundTag.TYPE, "starts")
        );

        try {
            this.storageAccess.scanChunk(chunkPos, collectFields).join();
        } catch (Exception var13) {
            LOGGER.warn("Failed to read chunk {}", chunkPos, var13);
            return StructureCheckResult.CHUNK_LOAD_NEEDED;
        }

        if (!(collectFields.getResult() instanceof CompoundTag compoundTag)) {
            return null;
        } else {
            int version = ChunkStorage.getVersion(compoundTag);
            if (version <= 1493) {
                return StructureCheckResult.CHUNK_LOAD_NEEDED;
            } else {
                ChunkStorage.injectDatafixingContext(compoundTag, this.dimension, this.chunkGenerator.getTypeNameForDataFixer());

                CompoundTag compoundTag1;
                try {
                    compoundTag1 = DataFixTypes.CHUNK.updateToCurrentVersion(this.fixerUpper, compoundTag, version);
                } catch (Exception var12) {
                    LOGGER.warn("Failed to partially datafix chunk {}", chunkPos, var12);
                    return StructureCheckResult.CHUNK_LOAD_NEEDED;
                }

                Object2IntMap<Structure> map = this.loadStructures(compoundTag1);
                if (map == null) {
                    return null;
                } else {
                    this.storeFullResults(packedChunkPos, map);
                    return this.checkStructureInfo(map, structure, skipKnownStructures);
                }
            }
        }
    }

    @Nullable
    private Object2IntMap<Structure> loadStructures(CompoundTag tag) {
        if (!tag.contains("structures", 10)) {
            return null;
        } else {
            CompoundTag compound = tag.getCompound("structures");
            if (!compound.contains("starts", 10)) {
                return null;
            } else {
                CompoundTag compound1 = compound.getCompound("starts");
                if (compound1.isEmpty()) {
                    return Object2IntMaps.emptyMap();
                } else {
                    Object2IntMap<Structure> map = new Object2IntOpenHashMap<>();
                    Registry<Structure> registry = this.registryAccess.lookupOrThrow(Registries.STRUCTURE);

                    for (String string : compound1.getAllKeys()) {
                        ResourceLocation resourceLocation = ResourceLocation.tryParse(string);
                        if (resourceLocation != null) {
                            Structure structure = registry.getValue(resourceLocation);
                            if (structure != null) {
                                CompoundTag compound2 = compound1.getCompound(string);
                                if (!compound2.isEmpty()) {
                                    String string1 = compound2.getString("id");
                                    if (!"INVALID".equals(string1)) {
                                        int _int = compound2.getInt("references");
                                        map.put(structure, _int);
                                    }
                                }
                            }
                        }
                    }

                    return map;
                }
            }
        }
    }

    private static Object2IntMap<Structure> deduplicateEmptyMap(Object2IntMap<Structure> map) {
        return map.isEmpty() ? Object2IntMaps.emptyMap() : map;
    }

    private StructureCheckResult checkStructureInfo(Object2IntMap<Structure> structureChunks, Structure structure, boolean skipKnownStructures) {
        int orDefault = structureChunks.getOrDefault(structure, -1);
        return orDefault == -1 || skipKnownStructures && orDefault != 0 ? StructureCheckResult.START_NOT_PRESENT : StructureCheckResult.START_PRESENT;
    }

    public void onStructureLoad(ChunkPos chunkPos, Map<Structure, StructureStart> chunkStarts) {
        long packedChunkPos = chunkPos.toLong();
        Object2IntMap<Structure> map = new Object2IntOpenHashMap<>();
        chunkStarts.forEach((structure, structureStart) -> {
            if (structureStart.isValid()) {
                map.put(structure, structureStart.getReferences());
            }
        });
        this.storeFullResults(packedChunkPos, map);
    }

    private void storeFullResults(long chunkPos, Object2IntMap<Structure> structureChunks) {
        this.loadedChunks.put(chunkPos, deduplicateEmptyMap(structureChunks));
        this.featureChecks.values().forEach(map -> map.remove(chunkPos));
    }

    public void incrementReference(ChunkPos pos, Structure structure) {
        this.loadedChunks.compute(pos.toLong(), (_long, map) -> {
            if (map == null || map.isEmpty()) {
                map = new Object2IntOpenHashMap<>();
            }

            map.computeInt(structure, (structure1, integer) -> integer == null ? 1 : integer + 1);
            return map;
        });
    }
}
