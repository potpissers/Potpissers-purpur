package net.minecraft.world.level.levelgen.structure;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class LegacyStructureDataHandler {
    private static final Map<String, String> CURRENT_TO_LEGACY_MAP = Util.make(Maps.newHashMap(), map -> {
        map.put("Village", "Village");
        map.put("Mineshaft", "Mineshaft");
        map.put("Mansion", "Mansion");
        map.put("Igloo", "Temple");
        map.put("Desert_Pyramid", "Temple");
        map.put("Jungle_Pyramid", "Temple");
        map.put("Swamp_Hut", "Temple");
        map.put("Stronghold", "Stronghold");
        map.put("Monument", "Monument");
        map.put("Fortress", "Fortress");
        map.put("EndCity", "EndCity");
    });
    private static final Map<String, String> LEGACY_TO_CURRENT_MAP = Util.make(Maps.newHashMap(), map -> {
        map.put("Iglu", "Igloo");
        map.put("TeDP", "Desert_Pyramid");
        map.put("TeJP", "Jungle_Pyramid");
        map.put("TeSH", "Swamp_Hut");
    });
    private static final Set<String> OLD_STRUCTURE_REGISTRY_KEYS = Set.of(
        "pillager_outpost",
        "mineshaft",
        "mansion",
        "jungle_pyramid",
        "desert_pyramid",
        "igloo",
        "ruined_portal",
        "shipwreck",
        "swamp_hut",
        "stronghold",
        "monument",
        "ocean_ruin",
        "fortress",
        "endcity",
        "buried_treasure",
        "village",
        "nether_fossil",
        "bastion_remnant"
    );
    private final boolean hasLegacyData;
    private final Map<String, Long2ObjectMap<CompoundTag>> dataMap = Maps.newHashMap();
    private final Map<String, StructureFeatureIndexSavedData> indexMap = Maps.newHashMap();
    private final List<String> legacyKeys;
    private final List<String> currentKeys;

    public LegacyStructureDataHandler(@Nullable DimensionDataStorage storage, List<String> legacyKeys, List<String> currentKeys) {
        this.legacyKeys = legacyKeys;
        this.currentKeys = currentKeys;
        this.populateCaches(storage);
        boolean flag = false;

        for (String string : this.currentKeys) {
            flag |= this.dataMap.get(string) != null;
        }

        this.hasLegacyData = flag;
    }

    public void removeIndex(long packedChunkPos) {
        for (String string : this.legacyKeys) {
            StructureFeatureIndexSavedData structureFeatureIndexSavedData = this.indexMap.get(string);
            if (structureFeatureIndexSavedData != null && structureFeatureIndexSavedData.hasUnhandledIndex(packedChunkPos)) {
                structureFeatureIndexSavedData.removeIndex(packedChunkPos);
            }
        }
    }

    public CompoundTag updateFromLegacy(CompoundTag tag) {
        CompoundTag compound = tag.getCompound("Level");
        ChunkPos chunkPos = new ChunkPos(compound.getInt("xPos"), compound.getInt("zPos"));
        if (this.isUnhandledStructureStart(chunkPos.x, chunkPos.z)) {
            tag = this.updateStructureStart(tag, chunkPos);
        }

        CompoundTag compound1 = compound.getCompound("Structures");
        CompoundTag compound2 = compound1.getCompound("References");

        for (String string : this.currentKeys) {
            boolean flag = OLD_STRUCTURE_REGISTRY_KEYS.contains(string.toLowerCase(Locale.ROOT));
            if (!compound2.contains(string, 12) && flag) {
                int i = 8;
                LongList list = new LongArrayList();

                for (int i1 = chunkPos.x - 8; i1 <= chunkPos.x + 8; i1++) {
                    for (int i2 = chunkPos.z - 8; i2 <= chunkPos.z + 8; i2++) {
                        if (this.hasLegacyStart(i1, i2, string)) {
                            list.add(ChunkPos.asLong(i1, i2));
                        }
                    }
                }

                compound2.putLongArray(string, list);
            }
        }

        compound1.put("References", compound2);
        compound.put("Structures", compound1);
        tag.put("Level", compound);
        return tag;
    }

    private boolean hasLegacyStart(int chunkX, int chunkZ, String key) {
        return this.hasLegacyData
            && this.dataMap.get(key) != null
            && this.indexMap.get(CURRENT_TO_LEGACY_MAP.get(key)).hasStartIndex(ChunkPos.asLong(chunkX, chunkZ));
    }

    private boolean isUnhandledStructureStart(int chunkX, int chunkZ) {
        if (!this.hasLegacyData) {
            return false;
        } else {
            for (String string : this.currentKeys) {
                if (this.dataMap.get(string) != null && this.indexMap.get(CURRENT_TO_LEGACY_MAP.get(string)).hasUnhandledIndex(ChunkPos.asLong(chunkX, chunkZ))
                    )
                 {
                    return true;
                }
            }

            return false;
        }
    }

    private CompoundTag updateStructureStart(CompoundTag tag, ChunkPos chunkPos) {
        CompoundTag compound = tag.getCompound("Level");
        CompoundTag compound1 = compound.getCompound("Structures");
        CompoundTag compound2 = compound1.getCompound("Starts");

        for (String string : this.currentKeys) {
            Long2ObjectMap<CompoundTag> map = this.dataMap.get(string);
            if (map != null) {
                long packedChunkPos = chunkPos.toLong();
                if (this.indexMap.get(CURRENT_TO_LEGACY_MAP.get(string)).hasUnhandledIndex(packedChunkPos)) {
                    CompoundTag compoundTag = map.get(packedChunkPos);
                    if (compoundTag != null) {
                        compound2.put(string, compoundTag);
                    }
                }
            }
        }

        compound1.put("Starts", compound2);
        compound.put("Structures", compound1);
        tag.put("Level", compound);
        return tag;
    }

    private void populateCaches(@Nullable DimensionDataStorage storage) {
        if (storage != null) {
            for (String string : this.legacyKeys) {
                CompoundTag compoundTag = new CompoundTag();

                try {
                    compoundTag = storage.readTagFromDisk(string, DataFixTypes.SAVED_DATA_STRUCTURE_FEATURE_INDICES, 1493)
                        .getCompound("data")
                        .getCompound("Features");
                    if (compoundTag.isEmpty()) {
                        continue;
                    }
                } catch (IOException var13) {
                }

                for (String string1 : compoundTag.getAllKeys()) {
                    CompoundTag compound = compoundTag.getCompound(string1);
                    long packedChunkPos = ChunkPos.asLong(compound.getInt("ChunkX"), compound.getInt("ChunkZ"));
                    ListTag list = compound.getList("Children", 10);
                    if (!list.isEmpty()) {
                        String string2 = list.getCompound(0).getString("id");
                        String string3 = LEGACY_TO_CURRENT_MAP.get(string2);
                        if (string3 != null) {
                            compound.putString("id", string3);
                        }
                    }

                    String string2 = compound.getString("id");
                    this.dataMap.computeIfAbsent(string2, string6 -> new Long2ObjectOpenHashMap<>()).put(packedChunkPos, compound);
                }

                String string4 = string + "_index";
                StructureFeatureIndexSavedData structureFeatureIndexSavedData = storage.computeIfAbsent(StructureFeatureIndexSavedData.factory(), string4);
                if (structureFeatureIndexSavedData.getAll().isEmpty()) {
                    StructureFeatureIndexSavedData structureFeatureIndexSavedData1 = new StructureFeatureIndexSavedData();
                    this.indexMap.put(string, structureFeatureIndexSavedData1);

                    for (String string5 : compoundTag.getAllKeys()) {
                        CompoundTag compound1 = compoundTag.getCompound(string5);
                        structureFeatureIndexSavedData1.addIndex(ChunkPos.asLong(compound1.getInt("ChunkX"), compound1.getInt("ChunkZ")));
                    }
                } else {
                    this.indexMap.put(string, structureFeatureIndexSavedData);
                }
            }
        }
    }

    public static LegacyStructureDataHandler getLegacyStructureHandler(ResourceKey<net.minecraft.world.level.dimension.LevelStem> level, @Nullable DimensionDataStorage storage) { // CraftBukkit
        if (level == net.minecraft.world.level.dimension.LevelStem.OVERWORLD) { // CraftBukkit
            return new LegacyStructureDataHandler(
                storage,
                ImmutableList.of("Monument", "Stronghold", "Village", "Mineshaft", "Temple", "Mansion"),
                ImmutableList.of("Village", "Mineshaft", "Mansion", "Igloo", "Desert_Pyramid", "Jungle_Pyramid", "Swamp_Hut", "Stronghold", "Monument")
            );
        } else if (level == net.minecraft.world.level.dimension.LevelStem.NETHER) { // CraftBukkit
            List<String> list = ImmutableList.of("Fortress");
            return new LegacyStructureDataHandler(storage, list, list);
        } else if (level == net.minecraft.world.level.dimension.LevelStem.END) { // CraftBukkit
            List<String> list = ImmutableList.of("EndCity");
            return new LegacyStructureDataHandler(storage, list, list);
        } else {
            throw new RuntimeException(String.format(Locale.ROOT, "Unknown dimension type : %s", level));
        }
    }
}
