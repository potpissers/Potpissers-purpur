package net.minecraft.world.level;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;

public abstract class Level implements LevelAccessor, AutoCloseable {
    public static final Codec<ResourceKey<Level>> RESOURCE_KEY_CODEC = ResourceKey.codec(Registries.DIMENSION);
    public static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
    public static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_nether"));
    public static final ResourceKey<Level> END = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_end"));
    public static final int MAX_LEVEL_SIZE = 30000000;
    public static final int LONG_PARTICLE_CLIP_RANGE = 512;
    public static final int SHORT_PARTICLE_CLIP_RANGE = 32;
    public static final int MAX_BRIGHTNESS = 15;
    public static final int TICKS_PER_DAY = 24000;
    public static final int MAX_ENTITY_SPAWN_Y = 20000000;
    public static final int MIN_ENTITY_SPAWN_Y = -20000000;
    protected final List<TickingBlockEntity> blockEntityTickers = Lists.newArrayList();
    protected final NeighborUpdater neighborUpdater;
    private final List<TickingBlockEntity> pendingBlockEntityTickers = Lists.newArrayList();
    private boolean tickingBlockEntities;
    public final Thread thread;
    private final boolean isDebug;
    private int skyDarken;
    protected int randValue = RandomSource.create().nextInt();
    protected final int addend = 1013904223;
    protected float oRainLevel;
    public float rainLevel;
    protected float oThunderLevel;
    public float thunderLevel;
    public final RandomSource random = RandomSource.create();
    @Deprecated
    private final RandomSource threadSafeRandom = RandomSource.createThreadSafe();
    private final Holder<DimensionType> dimensionTypeRegistration;
    public final WritableLevelData levelData;
    public final boolean isClientSide;
    private final WorldBorder worldBorder;
    private final BiomeManager biomeManager;
    private final ResourceKey<Level> dimension;
    private final RegistryAccess registryAccess;
    private final DamageSources damageSources;
    private long subTickCount;

    protected Level(
        WritableLevelData levelData,
        ResourceKey<Level> dimension,
        RegistryAccess registryAccess,
        Holder<DimensionType> dimensionTypeRegistration,
        boolean isClientSide,
        boolean isDebug,
        long biomeZoomSeed,
        int maxChainedNeighborUpdates
    ) {
        this.levelData = levelData;
        this.dimensionTypeRegistration = dimensionTypeRegistration;
        final DimensionType dimensionType = dimensionTypeRegistration.value();
        this.dimension = dimension;
        this.isClientSide = isClientSide;
        if (dimensionType.coordinateScale() != 1.0) {
            this.worldBorder = new WorldBorder() {
                @Override
                public double getCenterX() {
                    return super.getCenterX() / dimensionType.coordinateScale();
                }

                @Override
                public double getCenterZ() {
                    return super.getCenterZ() / dimensionType.coordinateScale();
                }
            };
        } else {
            this.worldBorder = new WorldBorder();
        }

        this.thread = Thread.currentThread();
        this.biomeManager = new BiomeManager(this, biomeZoomSeed);
        this.isDebug = isDebug;
        this.neighborUpdater = new CollectingNeighborUpdater(this, maxChainedNeighborUpdates);
        this.registryAccess = registryAccess;
        this.damageSources = new DamageSources(registryAccess);
    }

    @Override
    public boolean isClientSide() {
        return this.isClientSide;
    }

    @Nullable
    @Override
    public MinecraftServer getServer() {
        return null;
    }

    public boolean isInWorldBounds(BlockPos pos) {
        return !this.isOutsideBuildHeight(pos) && isInWorldBoundsHorizontal(pos);
    }

    public static boolean isInSpawnableBounds(BlockPos pos) {
        return !isOutsideSpawnableHeight(pos.getY()) && isInWorldBoundsHorizontal(pos);
    }

    private static boolean isInWorldBoundsHorizontal(BlockPos pos) {
        return pos.getX() >= -30000000 && pos.getZ() >= -30000000 && pos.getX() < 30000000 && pos.getZ() < 30000000;
    }

    private static boolean isOutsideSpawnableHeight(int y) {
        return y < -20000000 || y >= 20000000;
    }

    public LevelChunk getChunkAt(BlockPos pos) {
        return this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    @Override
    public LevelChunk getChunk(int chunkX, int chunkZ) {
        return (LevelChunk)this.getChunk(chunkX, chunkZ, ChunkStatus.FULL);
    }

    @Nullable
    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus chunkStatus, boolean requireChunk) {
        ChunkAccess chunk = this.getChunkSource().getChunk(x, z, chunkStatus, requireChunk);
        if (chunk == null && requireChunk) {
            throw new IllegalStateException("Should always be able to create a chunk!");
        } else {
            return chunk;
        }
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState newState, int flags) {
        return this.setBlock(pos, newState, flags, 512);
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        if (this.isOutsideBuildHeight(pos)) {
            return false;
        } else if (!this.isClientSide && this.isDebug()) {
            return false;
        } else {
            LevelChunk chunkAt = this.getChunkAt(pos);
            Block block = state.getBlock();
            BlockState blockState = chunkAt.setBlockState(pos, state, (flags & 64) != 0);
            if (blockState == null) {
                return false;
            } else {
                BlockState blockState1 = this.getBlockState(pos);
                if (blockState1 == state) {
                    if (blockState != blockState1) {
                        this.setBlocksDirty(pos, blockState, blockState1);
                    }

                    if ((flags & 2) != 0
                        && (!this.isClientSide || (flags & 4) == 0)
                        && (this.isClientSide || chunkAt.getFullStatus() != null && chunkAt.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING))) {
                        this.sendBlockUpdated(pos, blockState, state, flags);
                    }

                    if ((flags & 1) != 0) {
                        this.blockUpdated(pos, blockState.getBlock());
                        if (!this.isClientSide && state.hasAnalogOutputSignal()) {
                            this.updateNeighbourForOutputSignal(pos, block);
                        }
                    }

                    if ((flags & 16) == 0 && recursionLeft > 0) {
                        int i = flags & -34;
                        blockState.updateIndirectNeighbourShapes(this, pos, i, recursionLeft - 1);
                        state.updateNeighbourShapes(this, pos, i, recursionLeft - 1);
                        state.updateIndirectNeighbourShapes(this, pos, i, recursionLeft - 1);
                    }

                    this.onBlockStateChange(pos, blockState, blockState1);
                }

                return true;
            }
        }
    }

    public void onBlockStateChange(BlockPos pos, BlockState blockState, BlockState newState) {
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean isMoving) {
        FluidState fluidState = this.getFluidState(pos);
        return this.setBlock(pos, fluidState.createLegacyBlock(), 3 | (isMoving ? 64 : 0));
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean dropBlock, @Nullable Entity entity, int recursionLeft) {
        BlockState blockState = this.getBlockState(pos);
        if (blockState.isAir()) {
            return false;
        } else {
            FluidState fluidState = this.getFluidState(pos);
            if (!(blockState.getBlock() instanceof BaseFireBlock)) {
                this.levelEvent(2001, pos, Block.getId(blockState));
            }

            if (dropBlock) {
                BlockEntity blockEntity = blockState.hasBlockEntity() ? this.getBlockEntity(pos) : null;
                Block.dropResources(blockState, this, pos, blockEntity, entity, ItemStack.EMPTY);
            }

            boolean flag = this.setBlock(pos, fluidState.createLegacyBlock(), 3, recursionLeft);
            if (flag) {
                this.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(entity, blockState));
            }

            return flag;
        }
    }

    public void addDestroyBlockEffect(BlockPos pos, BlockState state) {
    }

    public boolean setBlockAndUpdate(BlockPos pos, BlockState state) {
        return this.setBlock(pos, state, 3);
    }

    public abstract void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags);

    public void setBlocksDirty(BlockPos blockPos, BlockState oldState, BlockState newState) {
    }

    public void updateNeighborsAt(BlockPos pos, Block block) {
    }

    public void updateNeighborsAt(BlockPos pos, Block block, @Nullable Orientation orientation) {
    }

    public void updateNeighborsAtExceptFromFacing(BlockPos pos, Block block, Direction facing, @Nullable Orientation orientation) {
    }

    public void neighborChanged(BlockPos pos, Block block, @Nullable Orientation orientation) {
    }

    public void neighborChanged(BlockState state, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
    }

    @Override
    public void neighborShapeChanged(Direction direction, BlockPos pos, BlockPos neighborPos, BlockState neighborState, int flags, int recursionLeft) {
        this.neighborUpdater.shapeUpdate(direction, neighborState, pos, neighborPos, flags, recursionLeft);
    }

    @Override
    public int getHeight(Heightmap.Types heightmapType, int x, int z) {
        int i;
        if (x >= -30000000 && z >= -30000000 && x < 30000000 && z < 30000000) {
            if (this.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                i = this.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)).getHeight(heightmapType, x & 15, z & 15) + 1;
            } else {
                i = this.getMinY();
            }
        } else {
            i = this.getSeaLevel() + 1;
        }

        return i;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.getChunkSource().getLightEngine();
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (this.isOutsideBuildHeight(pos)) {
            return Blocks.VOID_AIR.defaultBlockState();
        } else {
            LevelChunk chunk = this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
            return chunk.getBlockState(pos);
        }
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        if (this.isOutsideBuildHeight(pos)) {
            return Fluids.EMPTY.defaultFluidState();
        } else {
            LevelChunk chunkAt = this.getChunkAt(pos);
            return chunkAt.getFluidState(pos);
        }
    }

    public boolean isDay() {
        return !this.dimensionType().hasFixedTime() && this.skyDarken < 4;
    }

    public boolean isNight() {
        return !this.dimensionType().hasFixedTime() && !this.isDay();
    }

    public void playSound(@Nullable Entity entity, BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch) {
        this.playSound(entity instanceof Player player ? player : null, pos, sound, category, volume, pitch);
    }

    @Override
    public void playSound(@Nullable Player player, BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch) {
        this.playSound(player, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, category, volume, pitch);
    }

    public abstract void playSeededSound(
        @Nullable Player player, double x, double d, double y, Holder<SoundEvent> holder, SoundSource z, float f, float sound, long category
    );

    public void playSeededSound(
        @Nullable Player player, double x, double y, double z, SoundEvent sound, SoundSource category, float volume, float pitch, long seed
    ) {
        this.playSeededSound(player, x, y, z, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), category, volume, pitch, seed);
    }

    public abstract void playSeededSound(
        @Nullable Player player, Entity entity, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed
    );

    public void playSound(@Nullable Player player, double x, double y, double z, SoundEvent sound, SoundSource category) {
        this.playSound(player, x, y, z, sound, category, 1.0F, 1.0F);
    }

    public void playSound(@Nullable Player player, double x, double y, double z, SoundEvent sound, SoundSource category, float volume, float pitch) {
        this.playSeededSound(player, x, y, z, sound, category, volume, pitch, this.threadSafeRandom.nextLong());
    }

    public void playSound(@Nullable Player player, double x, double y, double z, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch) {
        this.playSeededSound(player, x, y, z, sound, category, volume, pitch, this.threadSafeRandom.nextLong());
    }

    public void playSound(@Nullable Player player, Entity entity, SoundEvent event, SoundSource category, float volume, float pitch) {
        this.playSeededSound(player, entity, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event), category, volume, pitch, this.threadSafeRandom.nextLong());
    }

    public void playLocalSound(BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch, boolean distanceDelay) {
        this.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, category, volume, pitch, distanceDelay);
    }

    public void playLocalSound(Entity entity, SoundEvent sound, SoundSource category, float volume, float pitch) {
    }

    public void playLocalSound(double x, double y, double z, SoundEvent sound, SoundSource category, float volume, float pitch, boolean distanceDelay) {
    }

    @Override
    public void addParticle(ParticleOptions particleData, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
    }

    public void addParticle(
        ParticleOptions particle, boolean overrideLimiter, boolean alwaysShow, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
    }

    public void addAlwaysVisibleParticle(ParticleOptions particle, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
    }

    public void addAlwaysVisibleParticle(
        ParticleOptions particle, boolean ignoreRange, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
    }

    public float getSunAngle(float partialTick) {
        float timeOfDay = this.getTimeOfDay(partialTick);
        return timeOfDay * (float) (Math.PI * 2);
    }

    public void addBlockEntityTicker(TickingBlockEntity ticker) {
        (this.tickingBlockEntities ? this.pendingBlockEntityTickers : this.blockEntityTickers).add(ticker);
    }

    protected void tickBlockEntities() {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("blockEntities");
        this.tickingBlockEntities = true;
        if (!this.pendingBlockEntityTickers.isEmpty()) {
            this.blockEntityTickers.addAll(this.pendingBlockEntityTickers);
            this.pendingBlockEntityTickers.clear();
        }

        Iterator<TickingBlockEntity> iterator = this.blockEntityTickers.iterator();
        boolean runsNormally = this.tickRateManager().runsNormally();

        while (iterator.hasNext()) {
            TickingBlockEntity tickingBlockEntity = iterator.next();
            if (tickingBlockEntity.isRemoved()) {
                iterator.remove();
            } else if (runsNormally && this.shouldTickBlocksAt(tickingBlockEntity.getPos())) {
                tickingBlockEntity.tick();
            }
        }

        this.tickingBlockEntities = false;
        profilerFiller.pop();
    }

    public <T extends Entity> void guardEntityTick(Consumer<T> consumerEntity, T entity) {
        try {
            consumerEntity.accept(entity);
        } catch (Throwable var6) {
            CrashReport crashReport = CrashReport.forThrowable(var6, "Ticking entity");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Entity being ticked");
            entity.fillCrashReportCategory(crashReportCategory);
            throw new ReportedException(crashReport);
        }
    }

    public boolean shouldTickDeath(Entity entity) {
        return true;
    }

    public boolean shouldTickBlocksAt(long chunkPos) {
        return true;
    }

    public boolean shouldTickBlocksAt(BlockPos pos) {
        return this.shouldTickBlocksAt(ChunkPos.asLong(pos));
    }

    public void explode(@Nullable Entity source, double x, double y, double z, float radius, Level.ExplosionInteraction explosionInteraction) {
        this.explode(
            source,
            Explosion.getDefaultDamageSource(this, source),
            null,
            x,
            y,
            z,
            radius,
            false,
            explosionInteraction,
            ParticleTypes.EXPLOSION,
            ParticleTypes.EXPLOSION_EMITTER,
            SoundEvents.GENERIC_EXPLODE
        );
    }

    public void explode(@Nullable Entity source, double x, double y, double z, float radius, boolean fire, Level.ExplosionInteraction explosionInteraction) {
        this.explode(
            source,
            Explosion.getDefaultDamageSource(this, source),
            null,
            x,
            y,
            z,
            radius,
            fire,
            explosionInteraction,
            ParticleTypes.EXPLOSION,
            ParticleTypes.EXPLOSION_EMITTER,
            SoundEvents.GENERIC_EXPLODE
        );
    }

    public void explode(
        @Nullable Entity source,
        @Nullable DamageSource damageSource,
        @Nullable ExplosionDamageCalculator damageCalculator,
        Vec3 pos,
        float radius,
        boolean fire,
        Level.ExplosionInteraction explosionInteraction
    ) {
        this.explode(
            source,
            damageSource,
            damageCalculator,
            pos.x(),
            pos.y(),
            pos.z(),
            radius,
            fire,
            explosionInteraction,
            ParticleTypes.EXPLOSION,
            ParticleTypes.EXPLOSION_EMITTER,
            SoundEvents.GENERIC_EXPLODE
        );
    }

    public void explode(
        @Nullable Entity source,
        @Nullable DamageSource damageSource,
        @Nullable ExplosionDamageCalculator damageCalculator,
        double x,
        double y,
        double z,
        float radius,
        boolean fire,
        Level.ExplosionInteraction explosionInteraction
    ) {
        this.explode(
            source,
            damageSource,
            damageCalculator,
            x,
            y,
            z,
            radius,
            fire,
            explosionInteraction,
            ParticleTypes.EXPLOSION,
            ParticleTypes.EXPLOSION_EMITTER,
            SoundEvents.GENERIC_EXPLODE
        );
    }

    public abstract void explode(
        @Nullable Entity source,
        @Nullable DamageSource damageSource,
        @Nullable ExplosionDamageCalculator damageCalculator,
        double x,
        double d,
        double y,
        float f,
        boolean z,
        Level.ExplosionInteraction explosionInteraction,
        ParticleOptions radius,
        ParticleOptions fire,
        Holder<SoundEvent> explosionInteraction1
    );

    public abstract String gatherChunkSourceStats();

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        if (this.isOutsideBuildHeight(pos)) {
            return null;
        } else {
            return !this.isClientSide && Thread.currentThread() != this.thread
                ? null
                : this.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
        }
    }

    public void setBlockEntity(BlockEntity blockEntity) {
        BlockPos blockPos = blockEntity.getBlockPos();
        if (!this.isOutsideBuildHeight(blockPos)) {
            this.getChunkAt(blockPos).addAndRegisterBlockEntity(blockEntity);
        }
    }

    public void removeBlockEntity(BlockPos pos) {
        if (!this.isOutsideBuildHeight(pos)) {
            this.getChunkAt(pos).removeBlockEntity(pos);
        }
    }

    public boolean isLoaded(BlockPos pos) {
        return !this.isOutsideBuildHeight(pos)
            && this.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    public boolean loadedAndEntityCanStandOnFace(BlockPos pos, Entity entity, Direction direction) {
        if (this.isOutsideBuildHeight(pos)) {
            return false;
        } else {
            ChunkAccess chunk = this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), ChunkStatus.FULL, false);
            return chunk != null && chunk.getBlockState(pos).entityCanStandOnFace(this, pos, entity, direction);
        }
    }

    public boolean loadedAndEntityCanStandOn(BlockPos pos, Entity entity) {
        return this.loadedAndEntityCanStandOnFace(pos, entity, Direction.UP);
    }

    public void updateSkyBrightness() {
        double d = 1.0 - this.getRainLevel(1.0F) * 5.0F / 16.0;
        double d1 = 1.0 - this.getThunderLevel(1.0F) * 5.0F / 16.0;
        double d2 = 0.5 + 2.0 * Mth.clamp((double)Mth.cos(this.getTimeOfDay(1.0F) * (float) (Math.PI * 2)), -0.25, 0.25);
        this.skyDarken = (int)((1.0 - d2 * d * d1) * 11.0);
    }

    public void setSpawnSettings(boolean spawnSettings) {
        this.getChunkSource().setSpawnSettings(spawnSettings);
    }

    public BlockPos getSharedSpawnPos() {
        BlockPos spawnPos = this.levelData.getSpawnPos();
        if (!this.getWorldBorder().isWithinBounds(spawnPos)) {
            spawnPos = this.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(this.getWorldBorder().getCenterX(), 0.0, this.getWorldBorder().getCenterZ())
            );
        }

        return spawnPos;
    }

    public float getSharedSpawnAngle() {
        return this.levelData.getSpawnAngle();
    }

    protected void prepareWeather() {
        if (this.levelData.isRaining()) {
            this.rainLevel = 1.0F;
            if (this.levelData.isThundering()) {
                this.thunderLevel = 1.0F;
            }
        }
    }

    @Override
    public void close() throws IOException {
        this.getChunkSource().close();
    }

    @Nullable
    @Override
    public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }

    @Override
    public List<Entity> getEntities(@Nullable Entity entity, AABB boundingBox, Predicate<? super Entity> predicate) {
        Profiler.get().incrementCounter("getEntities");
        List<Entity> list = Lists.newArrayList();
        this.getEntities().get(boundingBox, entity1 -> {
            if (entity1 != entity && predicate.test(entity1)) {
                list.add(entity1);
            }
        });

        for (EnderDragonPart enderDragonPart : this.dragonParts()) {
            if (enderDragonPart != entity
                && enderDragonPart.parentMob != entity
                && predicate.test(enderDragonPart)
                && boundingBox.intersects(enderDragonPart.getBoundingBox())) {
                list.add(enderDragonPart);
            }
        }

        return list;
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> entityTypeTest, AABB bounds, Predicate<? super T> predicate) {
        List<T> list = Lists.newArrayList();
        this.getEntities(entityTypeTest, bounds, predicate, list);
        return list;
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> entityTypeTest, AABB bounds, Predicate<? super T> predicate, List<? super T> output) {
        this.getEntities(entityTypeTest, bounds, predicate, output, Integer.MAX_VALUE);
    }

    public <T extends Entity> void getEntities(
        EntityTypeTest<Entity, T> entityTypeTest, AABB bounds, Predicate<? super T> predicate, List<? super T> output, int maxResults
    ) {
        Profiler.get().incrementCounter("getEntities");
        this.getEntities().get(entityTypeTest, bounds, entity -> {
            if (predicate.test(entity)) {
                output.add(entity);
                if (output.size() >= maxResults) {
                    return AbortableIterationConsumer.Continuation.ABORT;
                }
            }

            if (entity instanceof EnderDragon enderDragon) {
                for (EnderDragonPart enderDragonPart : enderDragon.getSubEntities()) {
                    T entity1 = entityTypeTest.tryCast(enderDragonPart);
                    if (entity1 != null && predicate.test(entity1)) {
                        output.add(entity1);
                        if (output.size() >= maxResults) {
                            return AbortableIterationConsumer.Continuation.ABORT;
                        }
                    }
                }
            }

            return AbortableIterationConsumer.Continuation.CONTINUE;
        });
    }

    @Nullable
    public abstract Entity getEntity(int id);

    public abstract Collection<EnderDragonPart> dragonParts();

    public void blockEntityChanged(BlockPos pos) {
        if (this.hasChunkAt(pos)) {
            this.getChunkAt(pos).markUnsaved();
        }
    }

    public void disconnect() {
    }

    public long getGameTime() {
        return this.levelData.getGameTime();
    }

    public long getDayTime() {
        return this.levelData.getDayTime();
    }

    public boolean mayInteract(Player player, BlockPos pos) {
        return true;
    }

    public void broadcastEntityEvent(Entity entity, byte state) {
    }

    public void broadcastDamageEvent(Entity entity, DamageSource damageSource) {
    }

    public void blockEvent(BlockPos pos, Block block, int eventID, int eventParam) {
        this.getBlockState(pos).triggerEvent(this, pos, eventID, eventParam);
    }

    @Override
    public LevelData getLevelData() {
        return this.levelData;
    }

    public abstract TickRateManager tickRateManager();

    public float getThunderLevel(float partialTick) {
        return Mth.lerp(partialTick, this.oThunderLevel, this.thunderLevel) * this.getRainLevel(partialTick);
    }

    public void setThunderLevel(float strength) {
        float f = Mth.clamp(strength, 0.0F, 1.0F);
        this.oThunderLevel = f;
        this.thunderLevel = f;
    }

    public float getRainLevel(float partialTick) {
        return Mth.lerp(partialTick, this.oRainLevel, this.rainLevel);
    }

    public void setRainLevel(float strength) {
        float f = Mth.clamp(strength, 0.0F, 1.0F);
        this.oRainLevel = f;
        this.rainLevel = f;
    }

    private boolean canHaveWeather() {
        return this.dimensionType().hasSkyLight() && !this.dimensionType().hasCeiling();
    }

    public boolean isThundering() {
        return this.canHaveWeather() && this.getThunderLevel(1.0F) > 0.9;
    }

    public boolean isRaining() {
        return this.canHaveWeather() && this.getRainLevel(1.0F) > 0.2;
    }

    public boolean isRainingAt(BlockPos pos) {
        if (!this.isRaining()) {
            return false;
        } else if (!this.canSeeSky(pos)) {
            return false;
        } else if (this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() > pos.getY()) {
            return false;
        } else {
            Biome biome = this.getBiome(pos).value();
            return biome.getPrecipitationAt(pos, this.getSeaLevel()) == Biome.Precipitation.RAIN;
        }
    }

    @Nullable
    public abstract MapItemSavedData getMapData(MapId mapId);

    public abstract void setMapData(MapId mapId, MapItemSavedData mapData);

    public abstract MapId getFreeMapId();

    public void globalLevelEvent(int id, BlockPos pos, int data) {
    }

    public CrashReportCategory fillReportDetails(CrashReport report) {
        CrashReportCategory crashReportCategory = report.addCategory("Affected level", 1);
        crashReportCategory.setDetail("All players", () -> this.players().size() + " total; " + this.players());
        crashReportCategory.setDetail("Chunk stats", this.getChunkSource()::gatherStats);
        crashReportCategory.setDetail("Level dimension", () -> this.dimension().location().toString());

        try {
            this.levelData.fillCrashReportCategory(crashReportCategory, this);
        } catch (Throwable var4) {
            crashReportCategory.setDetailError("Level Data Unobtainable", var4);
        }

        return crashReportCategory;
    }

    public abstract void destroyBlockProgress(int breakerId, BlockPos pos, int progress);

    public void createFireworks(double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, List<FireworkExplosion> explosions) {
    }

    public abstract Scoreboard getScoreboard();

    public void updateNeighbourForOutputSignal(BlockPos pos, Block block) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockPos = pos.relative(direction);
            if (this.hasChunkAt(blockPos)) {
                BlockState blockState = this.getBlockState(blockPos);
                if (blockState.is(Blocks.COMPARATOR)) {
                    this.neighborChanged(blockState, blockPos, block, null, false);
                } else if (blockState.isRedstoneConductor(this, blockPos)) {
                    blockPos = blockPos.relative(direction);
                    blockState = this.getBlockState(blockPos);
                    if (blockState.is(Blocks.COMPARATOR)) {
                        this.neighborChanged(blockState, blockPos, block, null, false);
                    }
                }
            }
        }
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        long l = 0L;
        float f = 0.0F;
        if (this.hasChunkAt(pos)) {
            f = this.getMoonBrightness();
            l = this.getChunkAt(pos).getInhabitedTime();
        }

        return new DifficultyInstance(this.getDifficulty(), this.getDayTime(), l, f);
    }

    @Override
    public int getSkyDarken() {
        return this.skyDarken;
    }

    public void setSkyFlashTime(int timeFlash) {
    }

    @Override
    public WorldBorder getWorldBorder() {
        return this.worldBorder;
    }

    public void sendPacketToServer(Packet<?> packet) {
        throw new UnsupportedOperationException("Can't send packets to server unless you're on the client.");
    }

    @Override
    public DimensionType dimensionType() {
        return this.dimensionTypeRegistration.value();
    }

    public Holder<DimensionType> dimensionTypeRegistration() {
        return this.dimensionTypeRegistration;
    }

    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    @Override
    public RandomSource getRandom() {
        return this.random;
    }

    @Override
    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> state) {
        return state.test(this.getBlockState(pos));
    }

    @Override
    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate) {
        return predicate.test(this.getFluidState(pos));
    }

    public abstract RecipeAccess recipeAccess();

    public BlockPos getBlockRandomPos(int x, int y, int z, int yMask) {
        this.randValue = this.randValue * 3 + 1013904223;
        int i = this.randValue >> 2;
        return new BlockPos(x + (i & 15), y + (i >> 16 & yMask), z + (i >> 8 & 15));
    }

    public boolean noSave() {
        return false;
    }

    @Override
    public BiomeManager getBiomeManager() {
        return this.biomeManager;
    }

    public final boolean isDebug() {
        return this.isDebug;
    }

    public abstract LevelEntityGetter<Entity> getEntities();

    @Override
    public long nextSubTickCount() {
        return this.subTickCount++;
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.registryAccess;
    }

    public DamageSources damageSources() {
        return this.damageSources;
    }

    public abstract PotionBrewing potionBrewing();

    public abstract FuelValues fuelValues();

    public static enum ExplosionInteraction implements StringRepresentable {
        NONE("none"),
        BLOCK("block"),
        MOB("mob"),
        TNT("tnt"),
        TRIGGER("trigger");

        public static final Codec<Level.ExplosionInteraction> CODEC = StringRepresentable.fromEnum(Level.ExplosionInteraction::values);
        private final String id;

        private ExplosionInteraction(final String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }
    }
}
