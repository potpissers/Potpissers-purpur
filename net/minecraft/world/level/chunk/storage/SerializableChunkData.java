package net.minecraft.world.level.chunk.storage;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.Optionull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ShortTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTicks;
import net.minecraft.world.ticks.SavedTick;
import org.slf4j.Logger;

public record SerializableChunkData(
    Registry<Biome> biomeRegistry,
    ChunkPos chunkPos,
    int minSectionY,
    long lastUpdateTime,
    long inhabitedTime,
    ChunkStatus chunkStatus,
    @Nullable BlendingData.Packed blendingData,
    @Nullable BelowZeroRetrogen belowZeroRetrogen,
    UpgradeData upgradeData,
    @Nullable long[] carvingMask,
    Map<Heightmap.Types, long[]> heightmaps,
    ChunkAccess.PackedTicks packedTicks,
    ShortList[] postProcessingSections,
    boolean lightCorrect,
    List<SerializableChunkData.SectionData> sectionData,
    List<CompoundTag> entities,
    List<CompoundTag> blockEntities,
    CompoundTag structureData
) {
    public static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codecRW(
        Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState()
    );
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_UPGRADE_DATA = "UpgradeData";
    private static final String BLOCK_TICKS_TAG = "block_ticks";
    private static final String FLUID_TICKS_TAG = "fluid_ticks";
    public static final String X_POS_TAG = "xPos";
    public static final String Z_POS_TAG = "zPos";
    public static final String HEIGHTMAPS_TAG = "Heightmaps";
    public static final String IS_LIGHT_ON_TAG = "isLightOn";
    public static final String SECTIONS_TAG = "sections";
    public static final String BLOCK_LIGHT_TAG = "BlockLight";
    public static final String SKY_LIGHT_TAG = "SkyLight";

    @Nullable
    public static SerializableChunkData parse(LevelHeightAccessor levelHeightAccessor, RegistryAccess registries, CompoundTag tag) {
        if (!tag.contains("Status", 8)) {
            return null;
        } else {
            ChunkPos chunkPos = new ChunkPos(tag.getInt("xPos"), tag.getInt("zPos"));
            long _long = tag.getLong("LastUpdate");
            long _long1 = tag.getLong("InhabitedTime");
            ChunkStatus chunkStatus = ChunkStatus.byName(tag.getString("Status"));
            UpgradeData upgradeData = tag.contains("UpgradeData", 10)
                ? new UpgradeData(tag.getCompound("UpgradeData"), levelHeightAccessor)
                : UpgradeData.EMPTY;
            boolean _boolean = tag.getBoolean("isLightOn");
            BlendingData.Packed packed;
            if (tag.contains("blending_data", 10)) {
                packed = BlendingData.Packed.CODEC.parse(NbtOps.INSTANCE, tag.getCompound("blending_data")).resultOrPartial(LOGGER::error).orElse(null);
            } else {
                packed = null;
            }

            BelowZeroRetrogen belowZeroRetrogen;
            if (tag.contains("below_zero_retrogen", 10)) {
                belowZeroRetrogen = BelowZeroRetrogen.CODEC
                    .parse(NbtOps.INSTANCE, tag.getCompound("below_zero_retrogen"))
                    .resultOrPartial(LOGGER::error)
                    .orElse(null);
            } else {
                belowZeroRetrogen = null;
            }

            long[] longArray;
            if (tag.contains("carving_mask", 12)) {
                longArray = tag.getLongArray("carving_mask");
            } else {
                longArray = null;
            }

            CompoundTag compound = tag.getCompound("Heightmaps");
            Map<Heightmap.Types, long[]> map = new EnumMap<>(Heightmap.Types.class);

            for (Heightmap.Types types : chunkStatus.heightmapsAfter()) {
                String serializationKey = types.getSerializationKey();
                if (compound.contains(serializationKey, 12)) {
                    map.put(types, compound.getLongArray(serializationKey));
                }
            }

            List<SavedTick<Block>> list = SavedTick.loadTickList(
                tag.getList("block_ticks", 10), string -> BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(string)), chunkPos
            );
            List<SavedTick<Fluid>> list1 = SavedTick.loadTickList(
                tag.getList("fluid_ticks", 10), string -> BuiltInRegistries.FLUID.getOptional(ResourceLocation.tryParse(string)), chunkPos
            );
            ChunkAccess.PackedTicks packedTicks = new ChunkAccess.PackedTicks(list, list1);
            ListTag list2 = tag.getList("PostProcessing", 9);
            ShortList[] lists = new ShortList[list2.size()];

            for (int i = 0; i < list2.size(); i++) {
                ListTag list3 = list2.getList(i);
                ShortList list4 = new ShortArrayList(list3.size());

                for (int i1 = 0; i1 < list3.size(); i1++) {
                    list4.add(list3.getShort(i1));
                }

                lists[i] = list4;
            }

            List<CompoundTag> list5 = Lists.transform(tag.getList("entities", 10), tag1 -> (CompoundTag)tag1);
            List<CompoundTag> list6 = Lists.transform(tag.getList("block_entities", 10), tag1 -> (CompoundTag)tag1);
            CompoundTag compound1 = tag.getCompound("structures");
            ListTag list7 = tag.getList("sections", 10);
            List<SerializableChunkData.SectionData> list8 = new ArrayList<>(list7.size());
            Registry<Biome> registry = registries.lookupOrThrow(Registries.BIOME);
            Codec<PalettedContainerRO<Holder<Biome>>> codec = makeBiomeCodec(registry);

            for (int i2 = 0; i2 < list7.size(); i2++) {
                CompoundTag compound2 = list7.getCompound(i2);
                int _byte = compound2.getByte("Y");
                LevelChunkSection levelChunkSection;
                if (_byte >= levelHeightAccessor.getMinSectionY() && _byte <= levelHeightAccessor.getMaxSectionY()) {
                    PalettedContainer<BlockState> palettedContainer;
                    if (compound2.contains("block_states", 10)) {
                        palettedContainer = BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, compound2.getCompound("block_states"))
                            .promotePartial(string -> logErrors(chunkPos, _byte, string))
                            .getOrThrow(SerializableChunkData.ChunkReadException::new);
                    } else {
                        palettedContainer = new PalettedContainer<>(
                            Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES
                        );
                    }

                    PalettedContainerRO<Holder<Biome>> palettedContainerRo;
                    if (compound2.contains("biomes", 10)) {
                        palettedContainerRo = codec.parse(NbtOps.INSTANCE, compound2.getCompound("biomes"))
                            .promotePartial(string -> logErrors(chunkPos, _byte, string))
                            .getOrThrow(SerializableChunkData.ChunkReadException::new);
                    } else {
                        palettedContainerRo = new PalettedContainer<>(
                            registry.asHolderIdMap(), registry.getOrThrow(Biomes.PLAINS), PalettedContainer.Strategy.SECTION_BIOMES
                        );
                    }

                    levelChunkSection = new LevelChunkSection(palettedContainer, palettedContainerRo);
                } else {
                    levelChunkSection = null;
                }

                DataLayer dataLayer = compound2.contains("BlockLight", 7) ? new DataLayer(compound2.getByteArray("BlockLight")) : null;
                DataLayer dataLayer1 = compound2.contains("SkyLight", 7) ? new DataLayer(compound2.getByteArray("SkyLight")) : null;
                list8.add(new SerializableChunkData.SectionData(_byte, levelChunkSection, dataLayer, dataLayer1));
            }

            return new SerializableChunkData(
                registry,
                chunkPos,
                levelHeightAccessor.getMinSectionY(),
                _long,
                _long1,
                chunkStatus,
                packed,
                belowZeroRetrogen,
                upgradeData,
                longArray,
                map,
                packedTicks,
                lists,
                _boolean,
                list8,
                list5,
                list6,
                compound1
            );
        }
    }

    public ProtoChunk read(ServerLevel level, PoiManager poiManager, RegionStorageInfo regionStorageInfo, ChunkPos pos) {
        if (!Objects.equals(pos, this.chunkPos)) {
            LOGGER.error("Chunk file at {} is in the wrong location; relocating. (Expected {}, got {})", pos, pos, this.chunkPos);
            level.getServer().reportMisplacedChunk(this.chunkPos, pos, regionStorageInfo);
        }

        int sectionsCount = level.getSectionsCount();
        LevelChunkSection[] levelChunkSections = new LevelChunkSection[sectionsCount];
        boolean hasSkyLight = level.dimensionType().hasSkyLight();
        ChunkSource chunkSource = level.getChunkSource();
        LevelLightEngine lightEngine = chunkSource.getLightEngine();
        Registry<Biome> registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        boolean flag = false;

        for (SerializableChunkData.SectionData sectionData : this.sectionData) {
            SectionPos sectionPos = SectionPos.of(pos, sectionData.y);
            if (sectionData.chunkSection != null) {
                levelChunkSections[level.getSectionIndexFromSectionY(sectionData.y)] = sectionData.chunkSection;
                poiManager.checkConsistencyWithBlocks(sectionPos, sectionData.chunkSection);
            }

            boolean flag1 = sectionData.blockLight != null;
            boolean flag2 = hasSkyLight && sectionData.skyLight != null;
            if (flag1 || flag2) {
                if (!flag) {
                    lightEngine.retainData(pos, true);
                    flag = true;
                }

                if (flag1) {
                    lightEngine.queueSectionData(LightLayer.BLOCK, sectionPos, sectionData.blockLight);
                }

                if (flag2) {
                    lightEngine.queueSectionData(LightLayer.SKY, sectionPos, sectionData.skyLight);
                }
            }
        }

        ChunkType chunkType = this.chunkStatus.getChunkType();
        ChunkAccess chunkAccess;
        if (chunkType == ChunkType.LEVELCHUNK) {
            LevelChunkTicks<Block> levelChunkTicks = new LevelChunkTicks<>(this.packedTicks.blocks());
            LevelChunkTicks<Fluid> levelChunkTicks1 = new LevelChunkTicks<>(this.packedTicks.fluids());
            chunkAccess = new LevelChunk(
                level.getLevel(),
                pos,
                this.upgradeData,
                levelChunkTicks,
                levelChunkTicks1,
                this.inhabitedTime,
                levelChunkSections,
                postLoadChunk(level, this.entities, this.blockEntities),
                BlendingData.unpack(this.blendingData)
            );
        } else {
            ProtoChunkTicks<Block> protoChunkTicks = ProtoChunkTicks.load(this.packedTicks.blocks());
            ProtoChunkTicks<Fluid> protoChunkTicks1 = ProtoChunkTicks.load(this.packedTicks.fluids());
            ProtoChunk protoChunk = new ProtoChunk(
                pos, this.upgradeData, levelChunkSections, protoChunkTicks, protoChunkTicks1, level, registry, BlendingData.unpack(this.blendingData)
            );
            chunkAccess = protoChunk;
            protoChunk.setInhabitedTime(this.inhabitedTime);
            if (this.belowZeroRetrogen != null) {
                protoChunk.setBelowZeroRetrogen(this.belowZeroRetrogen);
            }

            protoChunk.setPersistedStatus(this.chunkStatus);
            if (this.chunkStatus.isOrAfter(ChunkStatus.INITIALIZE_LIGHT)) {
                protoChunk.setLightEngine(lightEngine);
            }
        }

        chunkAccess.setLightCorrect(this.lightCorrect);
        EnumSet<Heightmap.Types> set = EnumSet.noneOf(Heightmap.Types.class);

        for (Heightmap.Types types : chunkAccess.getPersistedStatus().heightmapsAfter()) {
            long[] longs = this.heightmaps.get(types);
            if (longs != null) {
                chunkAccess.setHeightmap(types, longs);
            } else {
                set.add(types);
            }
        }

        Heightmap.primeHeightmaps(chunkAccess, set);
        chunkAccess.setAllStarts(unpackStructureStart(StructurePieceSerializationContext.fromLevel(level), this.structureData, level.getSeed()));
        chunkAccess.setAllReferences(unpackStructureReferences(level.registryAccess(), pos, this.structureData));

        for (int i = 0; i < this.postProcessingSections.length; i++) {
            chunkAccess.addPackedPostProcess(this.postProcessingSections[i], i);
        }

        if (chunkType == ChunkType.LEVELCHUNK) {
            return new ImposterProtoChunk((LevelChunk)chunkAccess, false);
        } else {
            ProtoChunk protoChunk1 = (ProtoChunk)chunkAccess;

            for (CompoundTag compoundTag : this.entities) {
                protoChunk1.addEntity(compoundTag);
            }

            for (CompoundTag compoundTag : this.blockEntities) {
                protoChunk1.setBlockEntityNbt(compoundTag);
            }

            if (this.carvingMask != null) {
                protoChunk1.setCarvingMask(new CarvingMask(this.carvingMask, chunkAccess.getMinY()));
            }

            return protoChunk1;
        }
    }

    private static void logErrors(ChunkPos chunkPos, int sectionY, String error) {
        LOGGER.error("Recoverable errors when loading section [{}, {}, {}]: {}", chunkPos.x, sectionY, chunkPos.z, error);
    }

    private static Codec<PalettedContainerRO<Holder<Biome>>> makeBiomeCodec(Registry<Biome> biomeRegistry) {
        return PalettedContainer.codecRO(
            biomeRegistry.asHolderIdMap(),
            biomeRegistry.holderByNameCodec(),
            PalettedContainer.Strategy.SECTION_BIOMES,
            biomeRegistry.getOrThrow(Biomes.PLAINS)
        );
    }

    public static SerializableChunkData copyOf(ServerLevel level, ChunkAccess chunk) {
        if (!chunk.canBeSerialized()) {
            throw new IllegalArgumentException("Chunk can't be serialized: " + chunk);
        } else {
            ChunkPos pos = chunk.getPos();
            List<SerializableChunkData.SectionData> list = new ArrayList<>();
            LevelChunkSection[] sections = chunk.getSections();
            LevelLightEngine lightEngine = level.getChunkSource().getLightEngine();

            for (int lightSection = lightEngine.getMinLightSection(); lightSection < lightEngine.getMaxLightSection(); lightSection++) {
                int sectionIndexFromSectionY = chunk.getSectionIndexFromSectionY(lightSection);
                boolean flag = sectionIndexFromSectionY >= 0 && sectionIndexFromSectionY < sections.length;
                DataLayer dataLayerData = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(pos, lightSection));
                DataLayer dataLayerData1 = lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(pos, lightSection));
                DataLayer dataLayer = dataLayerData != null && !dataLayerData.isEmpty() ? dataLayerData.copy() : null;
                DataLayer dataLayer1 = dataLayerData1 != null && !dataLayerData1.isEmpty() ? dataLayerData1.copy() : null;
                if (flag || dataLayer != null || dataLayer1 != null) {
                    LevelChunkSection levelChunkSection = flag ? sections[sectionIndexFromSectionY].copy() : null;
                    list.add(new SerializableChunkData.SectionData(lightSection, levelChunkSection, dataLayer, dataLayer1));
                }
            }

            List<CompoundTag> list1 = new ArrayList<>(chunk.getBlockEntitiesPos().size());

            for (BlockPos blockPos : chunk.getBlockEntitiesPos()) {
                CompoundTag blockEntityNbtForSaving = chunk.getBlockEntityNbtForSaving(blockPos, level.registryAccess());
                if (blockEntityNbtForSaving != null) {
                    list1.add(blockEntityNbtForSaving);
                }
            }

            List<CompoundTag> list2 = new ArrayList<>();
            long[] longs = null;
            if (chunk.getPersistedStatus().getChunkType() == ChunkType.PROTOCHUNK) {
                ProtoChunk protoChunk = (ProtoChunk)chunk;
                list2.addAll(protoChunk.getEntities());
                CarvingMask carvingMask = protoChunk.getCarvingMask();
                if (carvingMask != null) {
                    longs = carvingMask.toArray();
                }
            }

            Map<Heightmap.Types, long[]> map = new EnumMap<>(Heightmap.Types.class);

            for (Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
                if (chunk.getPersistedStatus().heightmapsAfter().contains(entry.getKey())) {
                    long[] rawData = entry.getValue().getRawData();
                    map.put(entry.getKey(), (long[])rawData.clone());
                }
            }

            ChunkAccess.PackedTicks ticksForSerialization = chunk.getTicksForSerialization(level.getGameTime());
            ShortList[] lists = Arrays.stream(chunk.getPostProcessing())
                .map(list3 -> list3 != null ? new ShortArrayList(list3) : null)
                .toArray(ShortList[]::new);
            CompoundTag compoundTag = packStructureData(
                StructurePieceSerializationContext.fromLevel(level), pos, chunk.getAllStarts(), chunk.getAllReferences()
            );
            return new SerializableChunkData(
                level.registryAccess().lookupOrThrow(Registries.BIOME),
                pos,
                chunk.getMinSectionY(),
                level.getGameTime(),
                chunk.getInhabitedTime(),
                chunk.getPersistedStatus(),
                Optionull.map(chunk.getBlendingData(), BlendingData::pack),
                chunk.getBelowZeroRetrogen(),
                chunk.getUpgradeData().copy(),
                longs,
                map,
                ticksForSerialization,
                lists,
                chunk.isLightCorrect(),
                list,
                list2,
                list1,
                compoundTag
            );
        }
    }

    public CompoundTag write() {
        CompoundTag compoundTag = NbtUtils.addCurrentDataVersion(new CompoundTag());
        compoundTag.putInt("xPos", this.chunkPos.x);
        compoundTag.putInt("yPos", this.minSectionY);
        compoundTag.putInt("zPos", this.chunkPos.z);
        compoundTag.putLong("LastUpdate", this.lastUpdateTime);
        compoundTag.putLong("InhabitedTime", this.inhabitedTime);
        compoundTag.putString("Status", BuiltInRegistries.CHUNK_STATUS.getKey(this.chunkStatus).toString());
        if (this.blendingData != null) {
            BlendingData.Packed.CODEC
                .encodeStart(NbtOps.INSTANCE, this.blendingData)
                .resultOrPartial(LOGGER::error)
                .ifPresent(tag -> compoundTag.put("blending_data", tag));
        }

        if (this.belowZeroRetrogen != null) {
            BelowZeroRetrogen.CODEC
                .encodeStart(NbtOps.INSTANCE, this.belowZeroRetrogen)
                .resultOrPartial(LOGGER::error)
                .ifPresent(tag -> compoundTag.put("below_zero_retrogen", tag));
        }

        if (!this.upgradeData.isEmpty()) {
            compoundTag.put("UpgradeData", this.upgradeData.write());
        }

        ListTag listTag = new ListTag();
        Codec<PalettedContainerRO<Holder<Biome>>> codec = makeBiomeCodec(this.biomeRegistry);

        for (SerializableChunkData.SectionData sectionData : this.sectionData) {
            CompoundTag compoundTag1 = new CompoundTag();
            LevelChunkSection levelChunkSection = sectionData.chunkSection;
            if (levelChunkSection != null) {
                compoundTag1.put("block_states", BLOCK_STATE_CODEC.encodeStart(NbtOps.INSTANCE, levelChunkSection.getStates()).getOrThrow());
                compoundTag1.put("biomes", codec.encodeStart(NbtOps.INSTANCE, levelChunkSection.getBiomes()).getOrThrow());
            }

            if (sectionData.blockLight != null) {
                compoundTag1.putByteArray("BlockLight", sectionData.blockLight.getData());
            }

            if (sectionData.skyLight != null) {
                compoundTag1.putByteArray("SkyLight", sectionData.skyLight.getData());
            }

            if (!compoundTag1.isEmpty()) {
                compoundTag1.putByte("Y", (byte)sectionData.y);
                listTag.add(compoundTag1);
            }
        }

        compoundTag.put("sections", listTag);
        if (this.lightCorrect) {
            compoundTag.putBoolean("isLightOn", true);
        }

        ListTag listTag1 = new ListTag();
        listTag1.addAll(this.blockEntities);
        compoundTag.put("block_entities", listTag1);
        if (this.chunkStatus.getChunkType() == ChunkType.PROTOCHUNK) {
            ListTag listTag2 = new ListTag();
            listTag2.addAll(this.entities);
            compoundTag.put("entities", listTag2);
            if (this.carvingMask != null) {
                compoundTag.putLongArray("carving_mask", this.carvingMask);
            }
        }

        saveTicks(compoundTag, this.packedTicks);
        compoundTag.put("PostProcessing", packOffsets(this.postProcessingSections));
        CompoundTag compoundTag2 = new CompoundTag();
        this.heightmaps.forEach((types, longs) -> compoundTag2.put(types.getSerializationKey(), new LongArrayTag(longs)));
        compoundTag.put("Heightmaps", compoundTag2);
        compoundTag.put("structures", this.structureData);
        return compoundTag;
    }

    private static void saveTicks(CompoundTag tag, ChunkAccess.PackedTicks ticks) {
        ListTag listTag = new ListTag();

        for (SavedTick<Block> savedTick : ticks.blocks()) {
            listTag.add(savedTick.save(block -> BuiltInRegistries.BLOCK.getKey(block).toString()));
        }

        tag.put("block_ticks", listTag);
        ListTag listTag1 = new ListTag();

        for (SavedTick<Fluid> savedTick1 : ticks.fluids()) {
            listTag1.add(savedTick1.save(fluid -> BuiltInRegistries.FLUID.getKey(fluid).toString()));
        }

        tag.put("fluid_ticks", listTag1);
    }

    public static ChunkType getChunkTypeFromTag(@Nullable CompoundTag tag) {
        return tag != null ? ChunkStatus.byName(tag.getString("Status")).getChunkType() : ChunkType.PROTOCHUNK;
    }

    @Nullable
    private static LevelChunk.PostLoadProcessor postLoadChunk(ServerLevel level, List<CompoundTag> entities, List<CompoundTag> blockEntities) {
        return entities.isEmpty() && blockEntities.isEmpty() ? null : chunk -> {
            if (!entities.isEmpty()) {
                level.addLegacyChunkEntities(EntityType.loadEntitiesRecursive(entities, level, EntitySpawnReason.LOAD));
            }

            for (CompoundTag compoundTag : blockEntities) {
                boolean _boolean = compoundTag.getBoolean("keepPacked");
                if (_boolean) {
                    chunk.setBlockEntityNbt(compoundTag);
                } else {
                    BlockPos posFromTag = BlockEntity.getPosFromTag(compoundTag);
                    BlockEntity blockEntity = BlockEntity.loadStatic(posFromTag, chunk.getBlockState(posFromTag), compoundTag, level.registryAccess());
                    if (blockEntity != null) {
                        chunk.setBlockEntity(blockEntity);
                    }
                }
            }
        };
    }

    private static CompoundTag packStructureData(
        StructurePieceSerializationContext context, ChunkPos pos, Map<Structure, StructureStart> structureStarts, Map<Structure, LongSet> references
    ) {
        CompoundTag compoundTag = new CompoundTag();
        CompoundTag compoundTag1 = new CompoundTag();
        Registry<Structure> registry = context.registryAccess().lookupOrThrow(Registries.STRUCTURE);

        for (Entry<Structure, StructureStart> entry : structureStarts.entrySet()) {
            ResourceLocation key = registry.getKey(entry.getKey());
            compoundTag1.put(key.toString(), entry.getValue().createTag(context, pos));
        }

        compoundTag.put("starts", compoundTag1);
        CompoundTag compoundTag2 = new CompoundTag();

        for (Entry<Structure, LongSet> entry1 : references.entrySet()) {
            if (!entry1.getValue().isEmpty()) {
                ResourceLocation key1 = registry.getKey(entry1.getKey());
                compoundTag2.put(key1.toString(), new LongArrayTag(entry1.getValue()));
            }
        }

        compoundTag.put("References", compoundTag2);
        return compoundTag;
    }

    private static Map<Structure, StructureStart> unpackStructureStart(StructurePieceSerializationContext context, CompoundTag tag, long seed) {
        Map<Structure, StructureStart> map = Maps.newHashMap();
        Registry<Structure> registry = context.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        CompoundTag compound = tag.getCompound("starts");

        for (String string : compound.getAllKeys()) {
            ResourceLocation resourceLocation = ResourceLocation.tryParse(string);
            Structure structure = registry.getValue(resourceLocation);
            if (structure == null) {
                LOGGER.error("Unknown structure start: {}", resourceLocation);
            } else {
                StructureStart structureStart = StructureStart.loadStaticStart(context, compound.getCompound(string), seed);
                if (structureStart != null) {
                    map.put(structure, structureStart);
                }
            }
        }

        return map;
    }

    private static Map<Structure, LongSet> unpackStructureReferences(RegistryAccess registries, ChunkPos pos, CompoundTag tag) {
        Map<Structure, LongSet> map = Maps.newHashMap();
        Registry<Structure> registry = registries.lookupOrThrow(Registries.STRUCTURE);
        CompoundTag compound = tag.getCompound("References");

        for (String string : compound.getAllKeys()) {
            ResourceLocation resourceLocation = ResourceLocation.tryParse(string);
            Structure structure = registry.getValue(resourceLocation);
            if (structure == null) {
                LOGGER.warn("Found reference to unknown structure '{}' in chunk {}, discarding", resourceLocation, pos);
            } else {
                long[] longArray = compound.getLongArray(string);
                if (longArray.length != 0) {
                    map.put(structure, new LongOpenHashSet(Arrays.stream(longArray).filter(l -> {
                        ChunkPos chunkPos = new ChunkPos(l);
                        if (chunkPos.getChessboardDistance(pos) > 8) {
                            LOGGER.warn("Found invalid structure reference [ {} @ {} ] for chunk {}.", resourceLocation, chunkPos, pos);
                            return false;
                        } else {
                            return true;
                        }
                    }).toArray()));
                }
            }
        }

        return map;
    }

    private static ListTag packOffsets(ShortList[] offsets) {
        ListTag listTag = new ListTag();

        for (ShortList list : offsets) {
            ListTag listTag1 = new ListTag();
            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    listTag1.add(ShortTag.valueOf(list.getShort(i)));
                }
            }

            listTag.add(listTag1);
        }

        return listTag;
    }

    public static class ChunkReadException extends NbtException {
        public ChunkReadException(String message) {
            super(message);
        }
    }

    public record SectionData(int y, @Nullable LevelChunkSection chunkSection, @Nullable DataLayer blockLight, @Nullable DataLayer skyLight) {
    }
}
