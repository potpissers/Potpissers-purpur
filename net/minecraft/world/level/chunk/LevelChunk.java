package net.minecraft.world.level.chunk;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.TickContainerAccess;
import org.slf4j.Logger;

public class LevelChunk extends ChunkAccess implements ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemLevelChunk, ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk, ca.spottedleaf.moonrise.patches.getblock.GetBlockChunk { // Paper - rewrite chunk system // Paper - get block chunk optimisation
    static final Logger LOGGER = LogUtils.getLogger();
    private static final TickingBlockEntity NULL_TICKER = new TickingBlockEntity() {
        @Override
        public void tick() {
        }

        @Override
        public boolean isRemoved() {
            return true;
        }

        @Override
        public BlockPos getPos() {
            return BlockPos.ZERO;
        }

        @Override
        public String getType() {
            return "<null>";
        }
    };
    private final Map<BlockPos, LevelChunk.RebindableTickingBlockEntityWrapper> tickersInLevel = Maps.newHashMap();
    public boolean loaded;
    public final ServerLevel level; // CraftBukkit - type
    @Nullable
    private Supplier<FullChunkStatus> fullStatus;
    @Nullable
    private LevelChunk.PostLoadProcessor postLoad;
    private final Int2ObjectMap<GameEventListenerRegistry> gameEventListenerRegistrySections;
    private final LevelChunkTicks<Block> blockTicks;
    private final LevelChunkTicks<Fluid> fluidTicks;
    private LevelChunk.UnsavedListener unsavedListener = chunkPos -> {};
    // CraftBukkit start
    public boolean mustNotSave;
    public boolean needsDecoration;
    // CraftBukkit end

    // Paper start
    boolean loadedTicketLevel;
    // Paper end
    // Paper start - rewrite chunk system
    private boolean postProcessingDone;
    private net.minecraft.server.level.ServerChunkCache.ChunkAndHolder chunkAndHolder;

    @Override
    public final boolean moonrise$isPostProcessingDone() {
        return this.postProcessingDone;
    }

    @Override
    public final net.minecraft.server.level.ServerChunkCache.ChunkAndHolder moonrise$getChunkAndHolder() {
        return this.chunkAndHolder;
    }

    @Override
    public final void moonrise$setChunkAndHolder(final net.minecraft.server.level.ServerChunkCache.ChunkAndHolder holder) {
        this.chunkAndHolder = holder;
    }
    // Paper end - rewrite chunk system
    // Paper start - get block chunk optimisation
    private static final BlockState AIR_BLOCKSTATE = Blocks.AIR.defaultBlockState();
    private static final FluidState AIR_FLUIDSTATE = Fluids.EMPTY.defaultFluidState();
    private static final BlockState VOID_AIR_BLOCKSTATE = Blocks.VOID_AIR.defaultBlockState();
    private final int minSection;
    private final int maxSection;
    private final boolean debug;
    private final BlockState defaultBlockState;

    @Override
    public final BlockState moonrise$getBlock(final int x, final int y, final int z) {
        return this.getBlockStateFinal(x, y, z);
    }
    // Paper end - get block chunk optimisation

    public LevelChunk(Level level, ChunkPos pos) {
        this(level, pos, UpgradeData.EMPTY, new LevelChunkTicks<>(), new LevelChunkTicks<>(), 0L, null, null, null);
    }

    public LevelChunk(
        Level level,
        ChunkPos pos,
        UpgradeData data,
        LevelChunkTicks<Block> blockTicks,
        LevelChunkTicks<Fluid> fluidTicks,
        long inhabitedTime,
        @Nullable LevelChunkSection[] sections,
        @Nullable LevelChunk.PostLoadProcessor postLoad,
        @Nullable BlendingData blendingData
    ) {
        super(pos, data, level, net.minecraft.server.MinecraftServer.getServer().registryAccess().lookupOrThrow(Registries.BIOME), inhabitedTime, sections, blendingData); // Paper - Anti-Xray - The world isn't ready yet, use server singleton for registry
        this.level = (ServerLevel) level; // CraftBukkit - type
        this.gameEventListenerRegistrySections = new Int2ObjectOpenHashMap<>();

        for (Heightmap.Types types : Heightmap.Types.values()) {
            if (ChunkStatus.FULL.heightmapsAfter().contains(types)) {
                this.heightmaps.put(types, new Heightmap(this, types));
            }
        }

        this.postLoad = postLoad;
        this.blockTicks = blockTicks;
        this.fluidTicks = fluidTicks;
        // Paper start - get block chunk optimisation
        this.minSection = ca.spottedleaf.moonrise.common.util.WorldUtil.getMinSection(level);
        this.maxSection = ca.spottedleaf.moonrise.common.util.WorldUtil.getMaxSection(level);

        final boolean empty = ((Object)this instanceof EmptyLevelChunk);
        this.debug = !empty && this.level.isDebug();
        this.defaultBlockState = empty ? VOID_AIR_BLOCKSTATE : AIR_BLOCKSTATE;
        // Paper end - get block chunk optimisation
    }

    public LevelChunk(ServerLevel level, ProtoChunk chunk, @Nullable LevelChunk.PostLoadProcessor postLoad) {
        this(
            level,
            chunk.getPos(),
            chunk.getUpgradeData(),
            chunk.unpackBlockTicks(),
            chunk.unpackFluidTicks(),
            chunk.getInhabitedTime(),
            chunk.getSections(),
            postLoad,
            chunk.getBlendingData()
        );
        if (!Collections.disjoint(chunk.pendingBlockEntities.keySet(), chunk.blockEntities.keySet())) {
            LOGGER.error("Chunk at {} contains duplicated block entities", chunk.getPos());
        }

        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            this.setBlockEntity(blockEntity);
        }

        this.pendingBlockEntities.putAll(chunk.getBlockEntityNbts());

        for (int i = 0; i < chunk.getPostProcessing().length; i++) {
            this.postProcessing[i] = chunk.getPostProcessing()[i];
        }

        this.setAllStarts(chunk.getAllStarts());
        this.setAllReferences(chunk.getAllReferences());

        for (Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            if (ChunkStatus.FULL.heightmapsAfter().contains(entry.getKey())) {
                this.setHeightmap(entry.getKey(), entry.getValue().getRawData());
            }
        }

        // Paper - rewrite chunk system
        this.setLightCorrect(chunk.isLightCorrect());
        this.markUnsaved();
        this.needsDecoration = true; // CraftBukkit
        // CraftBukkit start
        this.persistentDataContainer = chunk.persistentDataContainer; // SPIGOT-6814: copy PDC to account for 1.17 to 1.18 chunk upgrading.
        // CraftBukkit end
        // Paper start - rewrite chunk system
        this.starlight$setBlockNibbles(((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)chunk).starlight$getBlockNibbles());
        this.starlight$setSkyNibbles(((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)chunk).starlight$getSkyNibbles());
        this.starlight$setSkyEmptinessMap(((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)chunk).starlight$getSkyEmptinessMap());
        this.starlight$setBlockEmptinessMap(((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)chunk).starlight$getBlockEmptinessMap());
        // Paper end - rewrite chunk system
    }

    public void setUnsavedListener(LevelChunk.UnsavedListener unsavedListener) {
        this.unsavedListener = unsavedListener;
        if (this.isUnsaved()) {
            unsavedListener.setUnsaved(this.chunkPos);
        }
    }
    // Paper start
    @Override
    public long getInhabitedTime() {
        return this.level.paperConfig().chunks.fixedChunkInhabitedTime < 0 ? super.getInhabitedTime() : this.level.paperConfig().chunks.fixedChunkInhabitedTime;
    }
    // Paper end

    @Override
    public void markUnsaved() {
        boolean isUnsaved = this.isUnsaved();
        super.markUnsaved();
        if (!isUnsaved) {
            this.unsavedListener.setUnsaved(this.chunkPos);
        }
    }

    @Override
    public TickContainerAccess<Block> getBlockTicks() {
        return this.blockTicks;
    }

    @Override
    public TickContainerAccess<Fluid> getFluidTicks() {
        return this.fluidTicks;
    }

    @Override
    public ChunkAccess.PackedTicks getTicksForSerialization(long gametime) {
        return new ChunkAccess.PackedTicks(this.blockTicks.pack(gametime), this.fluidTicks.pack(gametime));
    }

    @Override
    public GameEventListenerRegistry getListenerRegistry(int sectionY) {
        return this.level instanceof ServerLevel serverLevel
            ? this.gameEventListenerRegistrySections
                .computeIfAbsent(sectionY, i -> new EuclideanGameEventListenerRegistry(serverLevel, sectionY, this::removeGameEventListenerRegistry))
            : super.getListenerRegistry(sectionY);
    }

    // Paper start - Perf: Reduce instructions and provide final method
    public BlockState getBlockState(final int x, final int y, final int z) {
        return this.getBlockStateFinal(x, y, z);
    }
    public BlockState getBlockStateFinal(final int x, final int y, final int z) {
        // Copied and modified from below
        final int sectionIndex = this.getSectionIndex(y);
        if (sectionIndex < 0 || sectionIndex >= this.sections.length
            || this.sections[sectionIndex].nonEmptyBlockCount == 0) {
            return Blocks.AIR.defaultBlockState();
        }
        return this.sections[sectionIndex].states.get((y & 15) << 8 | (z & 15) << 4 | x & 15);
    }
    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (true) {
            return this.getBlockStateFinal(pos.getX(), pos.getY(), pos.getZ());
        }
        // Paper end - Perf: Reduce instructions and provide final method
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (this.level.isDebug()) {
            BlockState blockState = null;
            if (y == 60) {
                blockState = Blocks.BARRIER.defaultBlockState();
            }

            if (y == 70) {
                blockState = DebugLevelSource.getBlockStateFor(x, z);
            }

            return blockState == null ? Blocks.AIR.defaultBlockState() : blockState;
        } else {
            try {
                int sectionIndex = this.getSectionIndex(y);
                if (sectionIndex >= 0 && sectionIndex < this.sections.length) {
                    LevelChunkSection levelChunkSection = this.sections[sectionIndex];
                    if (!levelChunkSection.hasOnlyAir()) {
                        return levelChunkSection.getBlockState(x & 15, y & 15, z & 15);
                    }
                }

                return Blocks.AIR.defaultBlockState();
            } catch (Throwable var8) {
                CrashReport crashReport = CrashReport.forThrowable(var8, "Getting block state");
                CrashReportCategory crashReportCategory = crashReport.addCategory("Block being got");
                crashReportCategory.setDetail("Location", () -> CrashReportCategory.formatLocation(this, x, y, z));
                throw new ReportedException(crashReport);
            }
        }
    }

    // Paper start - If loaded util
    @Override
    public final FluidState getFluidIfLoaded(BlockPos blockposition) {
        return this.getFluidState(blockposition);
    }

    @Override
    public final BlockState getBlockStateIfLoaded(BlockPos blockposition) {
        return this.getBlockState(blockposition);
    }
    // Paper end

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.getFluidState(pos.getX(), pos.getY(), pos.getZ());
    }

    public FluidState getFluidState(int x, int y, int z) {
        // try { // Paper start - Perf: Optimise Chunk#getFluid
            int sectionIndex = this.getSectionIndex(y);
            if (sectionIndex >= 0 && sectionIndex < this.sections.length) {
                LevelChunkSection levelChunkSection = this.sections[sectionIndex];
                if (!levelChunkSection.hasOnlyAir()) {
                    return levelChunkSection.states.get((y & 15) << 8 | (z & 15) << 4 | x & 15).getFluidState(); // Paper - Perf: Optimise Chunk#getFluid
                }
            }

            return Fluids.EMPTY.defaultFluidState();
        /* // Paper - Perf: Optimise Chunk#getFluid
        } catch (Throwable var7) {
            CrashReport crashReport = CrashReport.forThrowable(var7, "Getting fluid state");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Block being got");
            crashReportCategory.setDetail("Location", () -> CrashReportCategory.formatLocation(this, x, y, z));
            throw new ReportedException(crashReport);
        }
        */ // Paper - Perf: Optimise Chunk#getFluid
    }

    @Nullable
    @Override
    public BlockState setBlockState(BlockPos pos, BlockState state, boolean isMoving) {
// CraftBukkit start
        return this.setBlockState(pos, state, isMoving, true);
    }

    @Nullable
    public BlockState setBlockState(BlockPos pos, BlockState state, boolean isMoving, boolean doPlace) {
        // CraftBukkit end
        int y = pos.getY();
        LevelChunkSection section = this.getSection(this.getSectionIndex(y));
        boolean hasOnlyAir = section.hasOnlyAir();
        if (hasOnlyAir && state.isAir()) {
            return null;
        } else {
            int i = pos.getX() & 15;
            int i1 = y & 15;
            int i2 = pos.getZ() & 15;
            BlockState blockState = section.setBlockState(i, i1, i2, state);
            if (blockState == state) {
                return null;
            } else {
                Block block = state.getBlock();
                this.heightmaps.get(Heightmap.Types.MOTION_BLOCKING).update(i, y, i2, state);
                this.heightmaps.get(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES).update(i, y, i2, state);
                this.heightmaps.get(Heightmap.Types.OCEAN_FLOOR).update(i, y, i2, state);
                this.heightmaps.get(Heightmap.Types.WORLD_SURFACE).update(i, y, i2, state);
                boolean hasOnlyAir1 = section.hasOnlyAir();
                if (hasOnlyAir != hasOnlyAir1) {
                    this.level.getChunkSource().getLightEngine().updateSectionStatus(pos, hasOnlyAir1);
                    this.level.getChunkSource().onSectionEmptinessChanged(this.chunkPos.x, SectionPos.blockToSectionCoord(y), this.chunkPos.z, hasOnlyAir1);
                }

                if (LightEngine.hasDifferentLightProperties(blockState, state)) {
                    ProfilerFiller profilerFiller = Profiler.get();
                    profilerFiller.push("updateSkyLightSources");
                    // Paper - rewrite chunk system
                    profilerFiller.popPush("queueCheckLight");
                    this.level.getChunkSource().getLightEngine().checkBlock(pos);
                    profilerFiller.pop();
                }

                boolean hasBlockEntity = blockState.hasBlockEntity();
                if (!this.level.isClientSide && !this.level.isBlockPlaceCancelled) { // Paper - prevent calling cleanup logic when undoing a block place upon a cancelled BlockPlaceEvent
                    blockState.onRemove(this.level, pos, state, isMoving);
                } else if (!blockState.is(block) && hasBlockEntity) {
                    this.removeBlockEntity(pos);
                }

                if (!section.getBlockState(i, i1, i2).is(block)) {
                    return null;
                } else {
                    if (!this.level.isClientSide && doPlace && (!this.level.captureBlockStates || block instanceof net.minecraft.world.level.block.BaseEntityBlock)) { // CraftBukkit - Don't place while processing the BlockPlaceEvent, unless it's a BlockContainer. Prevents blocks such as TNT from activating when cancelled.
                        state.onPlace(this.level, pos, blockState, isMoving);
                    }

                    if (state.hasBlockEntity()) {
                        BlockEntity blockEntity = this.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
                        if (blockEntity != null && !blockEntity.isValidBlockState(state)) {
                            LOGGER.warn(
                                "Found mismatched block entity @ {}: type = {}, state = {}",
                                pos,
                                blockEntity.getType().builtInRegistryHolder().key().location(),
                                state
                            );
                            this.removeBlockEntity(pos);
                            blockEntity = null;
                        }

                        if (blockEntity == null) {
                            blockEntity = ((EntityBlock)block).newBlockEntity(pos, state);
                            if (blockEntity != null) {
                                this.addAndRegisterBlockEntity(blockEntity);
                            }
                        } else {
                            blockEntity.setBlockState(state);
                            this.updateBlockEntityTicker(blockEntity);
                        }
                    }

                    this.markUnsaved();
                    return blockState;
                }
            }
        }
    }

    @Deprecated
    @Override
    public void addEntity(Entity entity) {
    }

    @Nullable
    private BlockEntity createBlockEntity(BlockPos pos) {
        BlockState blockState = this.getBlockState(pos);
        return !blockState.hasBlockEntity() ? null : ((EntityBlock)blockState.getBlock()).newBlockEntity(pos, blockState);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return this.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
    }

    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos, LevelChunk.EntityCreationType creationType) {
        // CraftBukkit start
        BlockEntity blockEntity = this.level.capturedTileEntities.get(pos);
        if (blockEntity == null) {
            blockEntity = this.blockEntities.get(pos);
        }
        // CraftBukkit end
        if (blockEntity == null) {
            CompoundTag compoundTag = this.pendingBlockEntities.remove(pos);
            if (compoundTag != null) {
                BlockEntity blockEntity1 = this.promotePendingBlockEntity(pos, compoundTag);
                if (blockEntity1 != null) {
                    return blockEntity1;
                }
            }
        }

        if (blockEntity == null) {
            if (creationType == LevelChunk.EntityCreationType.IMMEDIATE) {
                blockEntity = this.createBlockEntity(pos);
                if (blockEntity != null) {
                    this.addAndRegisterBlockEntity(blockEntity);
                }
            }
        } else if (blockEntity.isRemoved()) {
            this.blockEntities.remove(pos);
            return null;
        }

        return blockEntity;
    }

    public void addAndRegisterBlockEntity(BlockEntity blockEntity) {
        this.setBlockEntity(blockEntity);
        if (this.isInLevel()) {
            if (this.level instanceof ServerLevel serverLevel) {
                this.addGameEventListener(blockEntity, serverLevel);
            }

            this.updateBlockEntityTicker(blockEntity);
        }
    }

    private boolean isInLevel() {
        return this.loaded || this.level.isClientSide();
    }

    boolean isTicking(BlockPos pos) {
        return this.level.getWorldBorder().isWithinBounds(pos)
            && (
                !(this.level instanceof ServerLevel serverLevel)
                    || this.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING) && serverLevel.areEntitiesLoaded(ChunkPos.asLong(pos))
            );
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {
        BlockPos blockPos = blockEntity.getBlockPos();
        BlockState blockState = this.getBlockState(blockPos);
        if (!blockState.hasBlockEntity()) {
            // Paper start - ServerExceptionEvent
            com.destroystokyo.paper.exception.ServerInternalException e = new com.destroystokyo.paper.exception.ServerInternalException(
                "Trying to set block entity %s at position %s, but state %s does not allow it".formatted(blockEntity, blockPos, blockState)
            );
            e.printStackTrace();
            com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(e);
            // Paper end - ServerExceptionEvent
        } else {
            BlockState blockState1 = blockEntity.getBlockState();
            if (blockState != blockState1) {
                if (!blockEntity.getType().isValid(blockState)) {
                    LOGGER.warn("Trying to set block entity {} at position {}, but state {} does not allow it", blockEntity, blockPos, blockState);
                    return;
                }

                if (blockState.getBlock() != blockState1.getBlock()) {
                    LOGGER.warn("Block state mismatch on block entity {} in position {}, {} != {}, updating", blockEntity, blockPos, blockState, blockState1);
                }

                blockEntity.setBlockState(blockState);
            }

            blockEntity.setLevel(this.level);
            blockEntity.clearRemoved();
            BlockEntity blockEntity1 = this.blockEntities.put(blockPos.immutable(), blockEntity);
            if (blockEntity1 != null && blockEntity1 != blockEntity) {
                blockEntity1.setRemoved();
            }
        }
    }

    @Nullable
    @Override
    public CompoundTag getBlockEntityNbtForSaving(BlockPos pos, HolderLookup.Provider registries) {
        BlockEntity blockEntity = this.getBlockEntity(pos);
        if (blockEntity != null && !blockEntity.isRemoved()) {
            CompoundTag compoundTag = blockEntity.saveWithFullMetadata(this.level.registryAccess());
            compoundTag.putBoolean("keepPacked", false);
            return compoundTag;
        } else {
            CompoundTag compoundTag = this.pendingBlockEntities.get(pos);
            if (compoundTag != null) {
                compoundTag = compoundTag.copy();
                compoundTag.putBoolean("keepPacked", true);
            }

            return compoundTag;
        }
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
        if (this.isInLevel()) {
            BlockEntity blockEntity = this.blockEntities.remove(pos);
            // CraftBukkit start - SPIGOT-5561: Also remove from pending map
            if (!this.pendingBlockEntities.isEmpty()) {
                this.pendingBlockEntities.remove(pos);
            }
            // CraftBukkit end
            if (blockEntity != null) {
                if (this.level instanceof ServerLevel serverLevel) {
                    this.removeGameEventListener(blockEntity, serverLevel);
                }

                blockEntity.setRemoved();
            }
        }

        this.removeBlockEntityTicker(pos);
    }

    private <T extends BlockEntity> void removeGameEventListener(T blockEntity, ServerLevel level) {
        Block block = blockEntity.getBlockState().getBlock();
        if (block instanceof EntityBlock) {
            GameEventListener listener = ((EntityBlock)block).getListener(level, blockEntity);
            if (listener != null) {
                int sectionPosY = SectionPos.blockToSectionCoord(blockEntity.getBlockPos().getY());
                GameEventListenerRegistry listenerRegistry = this.getListenerRegistry(sectionPosY);
                listenerRegistry.unregister(listener);
            }
        }
    }

    private void removeGameEventListenerRegistry(int sectionY) {
        this.gameEventListenerRegistrySections.remove(sectionY);
    }

    private void removeBlockEntityTicker(BlockPos pos) {
        LevelChunk.RebindableTickingBlockEntityWrapper rebindableTickingBlockEntityWrapper = this.tickersInLevel.remove(pos);
        if (rebindableTickingBlockEntityWrapper != null) {
            rebindableTickingBlockEntityWrapper.rebind(NULL_TICKER);
        }
    }

    public void runPostLoad() {
        if (this.postLoad != null) {
            this.postLoad.run(this);
            this.postLoad = null;
        }
    }

    // CraftBukkit start
    public void loadCallback() {
        if (this.loadedTicketLevel) { LOGGER.error("Double calling chunk load!", new Throwable()); } // Paper
        // Paper start
        this.loadedTicketLevel = true;
        // Paper end
        org.bukkit.Server server = this.level.getCraftServer();
        // Paper - rewrite chunk system
        if (server != null) {
            /*
             * If it's a new world, the first few chunks are generated inside
             * the World constructor. We can't reliably alter that, so we have
             * no way of creating a CraftWorld/CraftServer at that point.
             */
            org.bukkit.Chunk bukkitChunk = new org.bukkit.craftbukkit.CraftChunk(this);
            server.getPluginManager().callEvent(new org.bukkit.event.world.ChunkLoadEvent(bukkitChunk, this.needsDecoration));
            org.bukkit.craftbukkit.event.CraftEventFactory.callEntitiesLoadEvent(this.level, this.chunkPos, ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(this.locX, this.locZ).getEntityChunk().getAllEntities()); // Paper - rewrite chunk system

            if (this.needsDecoration) {
                this.needsDecoration = false;
                java.util.Random random = new java.util.Random();
                random.setSeed(this.level.getSeed());
                long xRand = random.nextLong() / 2L * 2L + 1L;
                long zRand = random.nextLong() / 2L * 2L + 1L;
                random.setSeed((long) this.chunkPos.x * xRand + (long) this.chunkPos.z * zRand ^ this.level.getSeed());

                org.bukkit.World world = this.level.getWorld();
                if (world != null) {
                    this.level.populating = true;
                    try {
                        for (org.bukkit.generator.BlockPopulator populator : world.getPopulators()) {
                            populator.populate(world, random, bukkitChunk);
                        }
                    } finally {
                        this.level.populating = false;
                    }
                }
                server.getPluginManager().callEvent(new org.bukkit.event.world.ChunkPopulateEvent(bukkitChunk));
            }
        }
    }

    public void unloadCallback() {
        if (!this.loadedTicketLevel) { LOGGER.error("Double calling chunk unload!", new Throwable()); } // Paper
        org.bukkit.Server server = this.level.getCraftServer();
        org.bukkit.craftbukkit.event.CraftEventFactory.callEntitiesUnloadEvent(this.level, this.chunkPos, ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(this.locX, this.locZ).getEntityChunk().getAllEntities()); // Paper - rewrite chunk system
        org.bukkit.Chunk bukkitChunk = new org.bukkit.craftbukkit.CraftChunk(this);
        org.bukkit.event.world.ChunkUnloadEvent unloadEvent = new org.bukkit.event.world.ChunkUnloadEvent(bukkitChunk, true); // Paper - rewrite chunk system - force save to true so that mustNotSave is correctly set below
        server.getPluginManager().callEvent(unloadEvent);
        // note: saving can be prevented, but not forced if no saving is actually required
        this.mustNotSave = !unloadEvent.isSaveChunk();
        // Paper - rewrite chunk system
        // Paper start
        this.loadedTicketLevel = false;
        // Paper end
    }

    @Override
    public boolean isUnsaved() {
        // Paper start - rewrite chunk system
        final long gameTime = this.level.getGameTime();
        if (((ca.spottedleaf.moonrise.patches.chunk_system.ticks.ChunkSystemLevelChunkTicks)this.blockTicks).moonrise$isDirty(gameTime)
            || ((ca.spottedleaf.moonrise.patches.chunk_system.ticks.ChunkSystemLevelChunkTicks)this.fluidTicks).moonrise$isDirty(gameTime)) {
            return true;
        }

        return super.isUnsaved();
        // Paper end - rewrite chunk system
    }

    // Paper start - rewrite chunk system
    @Override
    public boolean tryMarkSaved() {
        if (!this.isUnsaved()) {
            return false;
        }
        ((ca.spottedleaf.moonrise.patches.chunk_system.ticks.ChunkSystemLevelChunkTicks)this.blockTicks).moonrise$clearDirty();
        ((ca.spottedleaf.moonrise.patches.chunk_system.ticks.ChunkSystemLevelChunkTicks)this.fluidTicks).moonrise$clearDirty();

        super.tryMarkSaved();

        return true;
    }
    // Paper end - rewrite chunk system
    // CraftBukkit end

    public boolean isEmpty() {
        return false;
    }

    public void replaceWithPacketData(FriendlyByteBuf buffer, CompoundTag tag, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> outputTagConsumer) {
        this.clearAllBlockEntities();

        for (LevelChunkSection levelChunkSection : this.sections) {
            levelChunkSection.read(buffer);
        }

        for (Heightmap.Types types : Heightmap.Types.values()) {
            String serializationKey = types.getSerializationKey();
            if (tag.contains(serializationKey, 12)) {
                this.setHeightmap(types, tag.getLongArray(serializationKey));
            }
        }

        this.initializeLightSources();
        outputTagConsumer.accept((pos, blockEntityType, blockEntityTag) -> {
            BlockEntity blockEntity = this.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
            if (blockEntity != null && blockEntityTag != null && blockEntity.getType() == blockEntityType) {
                blockEntity.loadWithComponents(blockEntityTag, this.level.registryAccess());
            }
        });
    }

    public void replaceBiomes(FriendlyByteBuf buffer) {
        for (LevelChunkSection levelChunkSection : this.sections) {
            levelChunkSection.readBiomes(buffer);
        }
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public Level getLevel() {
        return this.level;
    }

    public Map<BlockPos, BlockEntity> getBlockEntities() {
        return this.blockEntities;
    }

    public void postProcessGeneration(ServerLevel level) {
        ChunkPos pos = this.getPos();

        for (int i = 0; i < this.postProcessing.length; i++) {
            if (this.postProcessing[i] != null) {
                for (Short _short : this.postProcessing[i]) {
                    BlockPos blockPos = ProtoChunk.unpackOffsetCoordinates(_short, this.getSectionYFromSectionIndex(i), pos);
                    BlockState blockState = this.getBlockState(blockPos);
                    FluidState fluidState = blockState.getFluidState();
                    if (!fluidState.isEmpty()) {
                        fluidState.tick(level, blockPos, blockState);
                    }

                    if (!(blockState.getBlock() instanceof LiquidBlock)) {
                        BlockState blockState1 = Block.updateFromNeighbourShapes(blockState, level, blockPos);
                        if (blockState1 != blockState) {
                            level.setBlock(blockPos, blockState1, 20);
                        }
                    }
                }

                this.postProcessing[i].clear();
            }
        }

        for (BlockPos blockPos1 : ImmutableList.copyOf(this.pendingBlockEntities.keySet())) {
            this.getBlockEntity(blockPos1);
        }

        this.pendingBlockEntities.clear();
        this.upgradeData.upgrade(this);
        this.postProcessingDone = true; // Paper - rewrite chunk system
    }

    @Nullable
    private BlockEntity promotePendingBlockEntity(BlockPos pos, CompoundTag tag) {
        BlockState blockState = this.getBlockState(pos);
        BlockEntity blockEntity;
        if ("DUMMY".equals(tag.getString("id"))) {
            if (blockState.hasBlockEntity()) {
                blockEntity = ((EntityBlock)blockState.getBlock()).newBlockEntity(pos, blockState);
            } else {
                blockEntity = null;
                LOGGER.warn("Tried to load a DUMMY block entity @ {} but found not block entity block {} at location", pos, blockState);
            }
        } else {
            blockEntity = BlockEntity.loadStatic(pos, blockState, tag, this.level.registryAccess());
        }

        if (blockEntity != null) {
            blockEntity.setLevel(this.level);
            this.addAndRegisterBlockEntity(blockEntity);
        } else {
            LOGGER.warn("Tried to load a block entity for block {} but failed at location {}", blockState, pos);
        }

        return blockEntity;
    }

    public void unpackTicks(long pos) {
        this.blockTicks.unpack(pos);
        this.fluidTicks.unpack(pos);
    }

    public void registerTickContainerInLevel(ServerLevel level) {
        level.getBlockTicks().addContainer(this.chunkPos, this.blockTicks);
        level.getFluidTicks().addContainer(this.chunkPos, this.fluidTicks);
    }

    public void unregisterTickContainerFromLevel(ServerLevel level) {
        level.getBlockTicks().removeContainer(this.chunkPos);
        level.getFluidTicks().removeContainer(this.chunkPos);
    }

    @Override
    public ChunkStatus getPersistedStatus() {
        return ChunkStatus.FULL;
    }

    public FullChunkStatus getFullStatus() {
        return this.fullStatus == null ? FullChunkStatus.FULL : this.fullStatus.get();
    }

    public void setFullStatus(Supplier<FullChunkStatus> fullStatus) {
        this.fullStatus = fullStatus;
    }

    public void clearAllBlockEntities() {
        this.blockEntities.values().forEach(BlockEntity::setRemoved);
        this.blockEntities.clear();
        this.tickersInLevel.values().forEach(ticker -> ticker.rebind(NULL_TICKER));
        this.tickersInLevel.clear();
    }

    public void registerAllBlockEntitiesAfterLevelLoad() {
        this.blockEntities.values().forEach(blockEntity -> {
            if (this.level instanceof ServerLevel serverLevel) {
                this.addGameEventListener(blockEntity, serverLevel);
            }

            this.updateBlockEntityTicker(blockEntity);
        });
    }

    private <T extends BlockEntity> void addGameEventListener(T blockEntity, ServerLevel level) {
        Block block = blockEntity.getBlockState().getBlock();
        if (block instanceof EntityBlock) {
            GameEventListener listener = ((EntityBlock)block).getListener(level, blockEntity);
            if (listener != null) {
                this.getListenerRegistry(SectionPos.blockToSectionCoord(blockEntity.getBlockPos().getY())).register(listener);
            }
        }
    }

    private <T extends BlockEntity> void updateBlockEntityTicker(T blockEntity) {
        BlockState blockState = blockEntity.getBlockState();
        BlockEntityTicker<T> ticker = blockState.getTicker(this.level, (BlockEntityType<T>)blockEntity.getType());
        if (ticker == null) {
            this.removeBlockEntityTicker(blockEntity.getBlockPos());
        } else {
            this.tickersInLevel
                .compute(
                    blockEntity.getBlockPos(),
                    (pos, ticker1) -> {
                        TickingBlockEntity tickingBlockEntity = this.createTicker(blockEntity, ticker);
                        if (ticker1 != null) {
                            ticker1.rebind(tickingBlockEntity);
                            return (LevelChunk.RebindableTickingBlockEntityWrapper)ticker1;
                        } else if (this.isInLevel()) {
                            LevelChunk.RebindableTickingBlockEntityWrapper rebindableTickingBlockEntityWrapper = new LevelChunk.RebindableTickingBlockEntityWrapper(
                                tickingBlockEntity
                            );
                            this.level.addBlockEntityTicker(rebindableTickingBlockEntityWrapper);
                            return rebindableTickingBlockEntityWrapper;
                        } else {
                            return null;
                        }
                    }
                );
        }
    }

    private <T extends BlockEntity> TickingBlockEntity createTicker(T blockEntity, BlockEntityTicker<T> ticker) {
        return new LevelChunk.BoundTickingBlockEntity<>(blockEntity, ticker);
    }

    class BoundTickingBlockEntity<T extends BlockEntity> implements TickingBlockEntity {
        private final T blockEntity;
        private final BlockEntityTicker<T> ticker;
        private boolean loggedInvalidBlockState;

        BoundTickingBlockEntity(final T blockEntity, final BlockEntityTicker<T> ticker) {
            this.blockEntity = blockEntity;
            this.ticker = ticker;
        }

        @Override
        public void tick() {
            if (!this.blockEntity.isRemoved() && this.blockEntity.hasLevel()) {
                BlockPos blockPos = this.blockEntity.getBlockPos();
                if (LevelChunk.this.isTicking(blockPos)) {
                    try {
                        ProfilerFiller profilerFiller = Profiler.get();
                        profilerFiller.push(this::getType);
                        BlockState blockState = LevelChunk.this.getBlockState(blockPos);
                        if (this.blockEntity.getType().isValid(blockState)) {
                            this.ticker.tick(LevelChunk.this.level, this.blockEntity.getBlockPos(), blockState, this.blockEntity);
                            this.loggedInvalidBlockState = false;
                        // Paper start - Remove the Block Entity if it's invalid
                        } else {
                            LevelChunk.this.removeBlockEntity(this.getPos());
                            if (!this.loggedInvalidBlockState) {
                                this.loggedInvalidBlockState = true;
                                LevelChunk.LOGGER.warn("Block entity {} @ {} state {} invalid for ticking:", LogUtils.defer(this::getType), LogUtils.defer(this::getPos), blockState);
                            }
                            // Paper end - Remove the Block Entity if it's invalid
                        }

                        profilerFiller.pop();
                    } catch (Throwable var5) {
                        // Paper start - Prevent block entity and entity crashes
                        final String msg = String.format("BlockEntity threw exception at %s:%s,%s,%s", LevelChunk.this.getLevel().getWorld().getName(), this.getPos().getX(), this.getPos().getY(), this.getPos().getZ());
                        net.minecraft.server.MinecraftServer.LOGGER.error(msg, var5);
                        net.minecraft.world.level.chunk.LevelChunk.this.level.getCraftServer().getPluginManager().callEvent(new com.destroystokyo.paper.event.server.ServerExceptionEvent(new com.destroystokyo.paper.exception.ServerInternalException(msg, var5))); // Paper - ServerExceptionEvent
                        LevelChunk.this.removeBlockEntity(this.getPos());
                        // Paper end - Prevent block entity and entity crashes
                    }
                }
            }
        }

        @Override
        public boolean isRemoved() {
            return this.blockEntity.isRemoved();
        }

        @Override
        public BlockPos getPos() {
            return this.blockEntity.getBlockPos();
        }

        @Override
        public String getType() {
            return BlockEntityType.getKey(this.blockEntity.getType()).toString();
        }

        @Override
        public String toString() {
            return "Level ticker for " + this.getType() + "@" + this.getPos();
        }
    }

    public static enum EntityCreationType {
        IMMEDIATE,
        QUEUED,
        CHECK;
    }

    @FunctionalInterface
    public interface PostLoadProcessor {
        void run(LevelChunk chunk);
    }

    static class RebindableTickingBlockEntityWrapper implements TickingBlockEntity {
        private TickingBlockEntity ticker;

        RebindableTickingBlockEntityWrapper(TickingBlockEntity ticker) {
            this.ticker = ticker;
        }

        void rebind(TickingBlockEntity ticker) {
            this.ticker = ticker;
        }

        @Override
        public void tick() {
            this.ticker.tick();
        }

        @Override
        public boolean isRemoved() {
            return this.ticker.isRemoved();
        }

        @Override
        public BlockPos getPos() {
            return this.ticker.getPos();
        }

        @Override
        public String getType() {
            return this.ticker.getType();
        }

        @Override
        public String toString() {
            return this.ticker + " <wrapped>";
        }
    }

    @FunctionalInterface
    public interface UnsavedListener {
        void setUnsaved(ChunkPos chunkPos);
    }
}
