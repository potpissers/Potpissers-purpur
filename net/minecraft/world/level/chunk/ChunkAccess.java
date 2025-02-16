package net.minecraft.world.level.chunk;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.TickContainerAccess;
import org.slf4j.Logger;

public abstract class ChunkAccess implements BiomeManager.NoiseBiomeSource, LightChunk, StructureAccess {
    public static final int NO_FILLED_SECTION = -1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LongSet EMPTY_REFERENCE_SET = new LongOpenHashSet();
    protected final ShortList[] postProcessing;
    private volatile boolean unsaved;
    private volatile boolean isLightCorrect;
    protected final ChunkPos chunkPos; public final long coordinateKey; public final int locX; public final int locZ; // Paper - cache coordinate key
    private long inhabitedTime;
    @Nullable
    @Deprecated
    private BiomeGenerationSettings carverBiomeSettings;
    @Nullable
    protected NoiseChunk noiseChunk;
    protected final UpgradeData upgradeData;
    @Nullable
    protected BlendingData blendingData;
    public final Map<Heightmap.Types, Heightmap> heightmaps = Maps.newEnumMap(Heightmap.Types.class);
    protected ChunkSkyLightSources skyLightSources;
    private final Map<Structure, StructureStart> structureStarts = Maps.newHashMap();
    private final Map<Structure, LongSet> structuresRefences = Maps.newHashMap();
    protected final Map<BlockPos, CompoundTag> pendingBlockEntities = Maps.newHashMap();
    public final Map<BlockPos, BlockEntity> blockEntities = new Object2ObjectOpenHashMap<>();
    protected final LevelHeightAccessor levelHeightAccessor;
    protected final LevelChunkSection[] sections;
    // CraftBukkit start - SPIGOT-6814: move to IChunkAccess to account for 1.17 to 1.18 chunk upgrading.
    private static final org.bukkit.craftbukkit.persistence.CraftPersistentDataTypeRegistry DATA_TYPE_REGISTRY = new org.bukkit.craftbukkit.persistence.CraftPersistentDataTypeRegistry();
    public org.bukkit.craftbukkit.persistence.DirtyCraftPersistentDataContainer persistentDataContainer = new org.bukkit.craftbukkit.persistence.DirtyCraftPersistentDataContainer(ChunkAccess.DATA_TYPE_REGISTRY);
    // CraftBukkit end
    public final Registry<Biome> biomeRegistry; // CraftBukkit

    public ChunkAccess(
        ChunkPos chunkPos,
        UpgradeData upgradeData,
        LevelHeightAccessor levelHeightAccessor,
        Registry<Biome> biomeRegistry,
        long inhabitedTime,
        @Nullable LevelChunkSection[] sections,
        @Nullable BlendingData blendingData
    ) {
        this.locX = chunkPos.x; this.locZ = chunkPos.z; // Paper - reduce need for field lookups
        this.chunkPos = chunkPos; this.coordinateKey = ChunkPos.asLong(locX, locZ); // Paper - cache long key
        this.upgradeData = upgradeData;
        this.levelHeightAccessor = levelHeightAccessor;
        this.sections = new LevelChunkSection[levelHeightAccessor.getSectionsCount()];
        this.inhabitedTime = inhabitedTime;
        this.postProcessing = new ShortList[levelHeightAccessor.getSectionsCount()];
        this.blendingData = blendingData;
        this.skyLightSources = new ChunkSkyLightSources(levelHeightAccessor);
        if (sections != null) {
            if (this.sections.length == sections.length) {
                System.arraycopy(sections, 0, this.sections, 0, this.sections.length);
            } else {
                LOGGER.warn("Could not set level chunk sections, array length is {} instead of {}", sections.length, this.sections.length);
            }
        }

        this.replaceMissingSections(biomeRegistry, this.sections); // Paper - Anti-Xray - make it a non-static method
        this.biomeRegistry = biomeRegistry; // CraftBukkit
    }

    private void replaceMissingSections(Registry<Biome> biomeRegistry, LevelChunkSection[] sections) { // Paper - Anti-Xray - make it a non-static method
        for (int i = 0; i < sections.length; i++) {
            if (sections[i] == null) {
                sections[i] = new LevelChunkSection(biomeRegistry, this.levelHeightAccessor instanceof net.minecraft.world.level.Level ? (net.minecraft.world.level.Level) this.levelHeightAccessor : null, this.chunkPos, this.levelHeightAccessor.getSectionYFromSectionIndex(i)); // Paper - Anti-Xray - Add parameters
            }
        }
    }

    public GameEventListenerRegistry getListenerRegistry(int sectionY) {
        return GameEventListenerRegistry.NOOP;
    }

    public abstract BlockState getBlockState(final int x, final int y, final int z); // Paper
    @Nullable
    public abstract BlockState setBlockState(BlockPos pos, BlockState state, boolean isMoving);

    public abstract void setBlockEntity(BlockEntity blockEntity);

    public abstract void addEntity(Entity entity);

    public int getHighestFilledSectionIndex() {
        LevelChunkSection[] sections = this.getSections();

        for (int i = sections.length - 1; i >= 0; i--) {
            LevelChunkSection levelChunkSection = sections[i];
            if (!levelChunkSection.hasOnlyAir()) {
                return i;
            }
        }

        return -1;
    }

    @Deprecated(
        forRemoval = true
    )
    public int getHighestSectionPosition() {
        int highestFilledSectionIndex = this.getHighestFilledSectionIndex();
        return highestFilledSectionIndex == -1 ? this.getMinY() : SectionPos.sectionToBlockCoord(this.getSectionYFromSectionIndex(highestFilledSectionIndex));
    }

    public Set<BlockPos> getBlockEntitiesPos() {
        Set<BlockPos> set = Sets.newHashSet(this.pendingBlockEntities.keySet());
        set.addAll(this.blockEntities.keySet());
        return set;
    }

    public LevelChunkSection[] getSections() {
        return this.sections;
    }

    public LevelChunkSection getSection(int index) {
        return this.getSections()[index];
    }

    public Collection<Entry<Heightmap.Types, Heightmap>> getHeightmaps() {
        return Collections.unmodifiableSet(this.heightmaps.entrySet());
    }

    public void setHeightmap(Heightmap.Types type, long[] data) {
        this.getOrCreateHeightmapUnprimed(type).setRawData(this, type, data);
    }

    public Heightmap getOrCreateHeightmapUnprimed(Heightmap.Types type) {
        return this.heightmaps.computeIfAbsent(type, absentType -> new Heightmap(this, absentType));
    }

    public boolean hasPrimedHeightmap(Heightmap.Types type) {
        return this.heightmaps.get(type) != null;
    }

    public int getHeight(Heightmap.Types type, int x, int z) {
        Heightmap heightmap = this.heightmaps.get(type);
        if (heightmap == null) {
            if (SharedConstants.IS_RUNNING_IN_IDE && this instanceof LevelChunk) {
                LOGGER.error("Unprimed heightmap: " + type + " " + x + " " + z);
            }

            Heightmap.primeHeightmaps(this, EnumSet.of(type));
            heightmap = this.heightmaps.get(type);
        }

        return heightmap.getFirstAvailable(x & 15, z & 15) - 1;
    }

    public ChunkPos getPos() {
        return this.chunkPos;
    }

    @Nullable
    @Override
    public StructureStart getStartForStructure(Structure structure) {
        return this.structureStarts.get(structure);
    }

    @Override
    public void setStartForStructure(Structure structure, StructureStart structureStart) {
        this.structureStarts.put(structure, structureStart);
        this.markUnsaved();
    }

    public Map<Structure, StructureStart> getAllStarts() {
        return Collections.unmodifiableMap(this.structureStarts);
    }

    public void setAllStarts(Map<Structure, StructureStart> structureStarts) {
        this.structureStarts.clear();
        this.structureStarts.putAll(structureStarts);
        this.markUnsaved();
    }

    @Override
    public LongSet getReferencesForStructure(Structure structure) {
        return this.structuresRefences.getOrDefault(structure, EMPTY_REFERENCE_SET);
    }

    @Override
    public void addReferenceForStructure(Structure structure, long reference) {
        this.structuresRefences.computeIfAbsent(structure, key -> new LongOpenHashSet()).add(reference);
        this.markUnsaved();
    }

    @Override
    public Map<Structure, LongSet> getAllReferences() {
        return Collections.unmodifiableMap(this.structuresRefences);
    }

    @Override
    public void setAllReferences(Map<Structure, LongSet> structureReferencesMap) {
        this.structuresRefences.clear();
        this.structuresRefences.putAll(structureReferencesMap);
        this.markUnsaved();
    }

    public boolean isYSpaceEmpty(int startY, int endY) {
        if (startY < this.getMinY()) {
            startY = this.getMinY();
        }

        if (endY > this.getMaxY()) {
            endY = this.getMaxY();
        }

        for (int i = startY; i <= endY; i += 16) {
            if (!this.getSection(this.getSectionIndex(i)).hasOnlyAir()) {
                return false;
            }
        }

        return true;
    }

    public boolean isSectionEmpty(int y) {
        return this.getSection(this.getSectionIndexFromSectionY(y)).hasOnlyAir();
    }

    public void markUnsaved() {
        this.unsaved = true;
    }

    public boolean tryMarkSaved() {
        if (this.unsaved) {
            this.unsaved = false;
            this.persistentDataContainer.dirty(false); // CraftBukkit - SPIGOT-6814: chunk was saved, pdc is no longer dirty
            return true;
        } else {
            return false;
        }
    }

    public boolean isUnsaved() {
        return this.unsaved || this.persistentDataContainer.dirty(); // CraftBukkit - SPIGOT-6814: chunk is unsaved if pdc was mutated
    }

    public abstract ChunkStatus getPersistedStatus();

    public ChunkStatus getHighestGeneratedStatus() {
        ChunkStatus persistedStatus = this.getPersistedStatus();
        BelowZeroRetrogen belowZeroRetrogen = this.getBelowZeroRetrogen();
        if (belowZeroRetrogen != null) {
            ChunkStatus chunkStatus = belowZeroRetrogen.targetStatus();
            return ChunkStatus.max(chunkStatus, persistedStatus);
        } else {
            return persistedStatus;
        }
    }

    public abstract void removeBlockEntity(BlockPos pos);

    public void markPosForPostprocessing(BlockPos pos) {
        LOGGER.warn("Trying to mark a block for PostProcessing @ {}, but this operation is not supported.", pos);
    }

    public ShortList[] getPostProcessing() {
        return this.postProcessing;
    }

    public void addPackedPostProcess(ShortList offsets, int index) {
        getOrCreateOffsetList(this.getPostProcessing(), index).addAll(offsets);
    }

    public void setBlockEntityNbt(CompoundTag tag) {
        BlockPos posFromTag = BlockEntity.getPosFromTag(tag);
        if (!this.blockEntities.containsKey(posFromTag)) {
            this.pendingBlockEntities.put(posFromTag, tag);
        }
    }

    @Nullable
    public CompoundTag getBlockEntityNbt(BlockPos pos) {
        return this.pendingBlockEntities.get(pos);
    }

    @Nullable
    public abstract CompoundTag getBlockEntityNbtForSaving(BlockPos pos, HolderLookup.Provider registries);

    @Override
    public final void findBlockLightSources(BiConsumer<BlockPos, BlockState> output) {
        this.findBlocks(state -> state.getLightEmission() != 0, output);
    }

    public void findBlocks(Predicate<BlockState> predicate, BiConsumer<BlockPos, BlockState> output) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int sectionY = this.getMinSectionY(); sectionY <= this.getMaxSectionY(); sectionY++) {
            LevelChunkSection section = this.getSection(this.getSectionIndexFromSectionY(sectionY));
            if (section.maybeHas(predicate)) {
                BlockPos blockPos = SectionPos.of(this.chunkPos, sectionY).origin();

                for (int i = 0; i < 16; i++) {
                    for (int i1 = 0; i1 < 16; i1++) {
                        for (int i2 = 0; i2 < 16; i2++) {
                            BlockState blockState = section.getBlockState(i2, i, i1);
                            if (predicate.test(blockState)) {
                                output.accept(mutableBlockPos.setWithOffset(blockPos, i2, i, i1), blockState);
                            }
                        }
                    }
                }
            }
        }
    }

    public abstract TickContainerAccess<Block> getBlockTicks();

    public abstract TickContainerAccess<Fluid> getFluidTicks();

    public boolean canBeSerialized() {
        return true;
    }

    public abstract ChunkAccess.PackedTicks getTicksForSerialization(long gametime);

    public UpgradeData getUpgradeData() {
        return this.upgradeData;
    }

    public boolean isOldNoiseGeneration() {
        return this.blendingData != null;
    }

    @Nullable
    public BlendingData getBlendingData() {
        return this.blendingData;
    }

    public long getInhabitedTime() {
        return this.inhabitedTime;
    }

    public void incrementInhabitedTime(long amount) {
        this.inhabitedTime += amount;
    }

    public void setInhabitedTime(long inhabitedTime) {
        this.inhabitedTime = inhabitedTime;
    }

    public static ShortList getOrCreateOffsetList(ShortList[] packedPositions, int index) {
        if (packedPositions[index] == null) {
            packedPositions[index] = new ShortArrayList();
        }

        return packedPositions[index];
    }

    public boolean isLightCorrect() {
        return this.isLightCorrect;
    }

    public void setLightCorrect(boolean lightCorrect) {
        this.isLightCorrect = lightCorrect;
        this.markUnsaved();
    }

    @Override
    public int getMinY() {
        return this.levelHeightAccessor.getMinY();
    }

    @Override
    public int getHeight() {
        return this.levelHeightAccessor.getHeight();
    }

    public NoiseChunk getOrCreateNoiseChunk(Function<ChunkAccess, NoiseChunk> noiseChunkCreator) {
        if (this.noiseChunk == null) {
            this.noiseChunk = noiseChunkCreator.apply(this);
        }

        return this.noiseChunk;
    }

    @Deprecated
    public BiomeGenerationSettings carverBiome(Supplier<BiomeGenerationSettings> caverBiomeSettingsSupplier) {
        if (this.carverBiomeSettings == null) {
            this.carverBiomeSettings = caverBiomeSettingsSupplier.get();
        }

        return this.carverBiomeSettings;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        try {
            int quartPosMinY = QuartPos.fromBlock(this.getMinY());
            int i = quartPosMinY + QuartPos.fromBlock(this.getHeight()) - 1;
            int i1 = Mth.clamp(y, quartPosMinY, i);
            int sectionIndex = this.getSectionIndex(QuartPos.toBlock(i1));
            return this.sections[sectionIndex].getNoiseBiome(x & 3, i1 & 3, z & 3);
        } catch (Throwable var8) {
            CrashReport crashReport = CrashReport.forThrowable(var8, "Getting biome");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Biome being got");
            crashReportCategory.setDetail("Location", () -> CrashReportCategory.formatLocation(this, x, y, z));
            throw new ReportedException(crashReport);
        }
    }
    // CraftBukkit start
    public void setBiome(int x, int y, int z, Holder<Biome> biome) {
        try {
            int minY = QuartPos.fromBlock(this.getMinY());
            int maxY = minY + QuartPos.fromBlock(this.getHeight()) - 1;
            int clampedY = Mth.clamp(y, minY, maxY);
            int sectionIndex = this.getSectionIndex(QuartPos.toBlock(clampedY));
            this.sections[sectionIndex].setBiome(x & 3, clampedY & 3, z & 3, biome);
        } catch (Throwable throwable) {
            CrashReport report = CrashReport.forThrowable(throwable, "Setting biome");
            CrashReportCategory reportCategory = report.addCategory("Biome being set");
            reportCategory.setDetail("Location", () -> CrashReportCategory.formatLocation(this, x, y, z));
            throw new ReportedException(report);
        }
    }
    // CraftBukkit end

    public void fillBiomesFromNoise(BiomeResolver resolver, Climate.Sampler sampler) {
        ChunkPos pos = this.getPos();
        int quartPosMinX = QuartPos.fromBlock(pos.getMinBlockX());
        int quartPosMinZ = QuartPos.fromBlock(pos.getMinBlockZ());
        LevelHeightAccessor heightAccessorForGeneration = this.getHeightAccessorForGeneration();

        for (int sectionY = heightAccessorForGeneration.getMinSectionY(); sectionY <= heightAccessorForGeneration.getMaxSectionY(); sectionY++) {
            LevelChunkSection section = this.getSection(this.getSectionIndexFromSectionY(sectionY));
            int quartPosY = QuartPos.fromSection(sectionY);
            section.fillBiomesFromNoise(resolver, sampler, quartPosMinX, quartPosY, quartPosMinZ);
        }
    }

    public boolean hasAnyStructureReferences() {
        return !this.getAllReferences().isEmpty();
    }

    @Nullable
    public BelowZeroRetrogen getBelowZeroRetrogen() {
        return null;
    }

    public boolean isUpgrading() {
        return this.getBelowZeroRetrogen() != null;
    }

    public LevelHeightAccessor getHeightAccessorForGeneration() {
        return this;
    }

    public void initializeLightSources() {
        this.skyLightSources.fillFrom(this);
    }

    @Override
    public ChunkSkyLightSources getSkyLightSources() {
        return this.skyLightSources;
    }

    public record PackedTicks(List<SavedTick<Block>> blocks, List<SavedTick<Fluid>> fluids) {
    }
}
