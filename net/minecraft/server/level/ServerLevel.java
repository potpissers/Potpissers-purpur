package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportType;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Mth;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.portal.PortalForcer;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapIndex;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.LevelTicks;
import org.slf4j.Logger;

public class ServerLevel extends Level implements ServerEntityGetter, WorldGenLevel, ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel, ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevelReader, ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickServerLevel { // Paper - rewrite chunk system // Paper - chunk tick iteration
    public static final BlockPos END_SPAWN_POINT = new BlockPos(100, 50, 0);
    public static final IntProvider RAIN_DELAY = UniformInt.of(12000, 180000);
    public static final IntProvider RAIN_DURATION = UniformInt.of(12000, 24000);
    private static final IntProvider THUNDER_DELAY = UniformInt.of(12000, 180000);
    public static final IntProvider THUNDER_DURATION = UniformInt.of(3600, 15600);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int EMPTY_TIME_NO_TICK = 300;
    private static final int MAX_SCHEDULED_TICKS_PER_TICK = 65536;
    final List<ServerPlayer> players = Lists.newArrayList();
    public final ServerChunkCache chunkSource;
    private final MinecraftServer server;
    public final net.minecraft.world.level.storage.PrimaryLevelData serverLevelData; // CraftBukkit - type
    private int lastSpawnChunkRadius;
    final EntityTickList entityTickList = new EntityTickList();
    // Paper - rewrite chunk system
    private final GameEventDispatcher gameEventDispatcher;
    public boolean noSave;
    private final SleepStatus sleepStatus;
    private int emptyTime;
    private final PortalForcer portalForcer;
    private final LevelTicks<Block> blockTicks = new LevelTicks<>(this::isPositionTickingWithEntitiesLoaded);
    private final LevelTicks<Fluid> fluidTicks = new LevelTicks<>(this::isPositionTickingWithEntitiesLoaded);
    private final PathTypeCache pathTypesByPosCache = new PathTypeCache();
    final Set<Mob> navigatingMobs = new ObjectOpenHashSet<>();
    volatile boolean isUpdatingNavigations;
    protected final Raids raids;
    private final ObjectLinkedOpenHashSet<BlockEventData> blockEvents = new ObjectLinkedOpenHashSet<>();
    private final List<BlockEventData> blockEventsToReschedule = new ArrayList<>(64);
    private boolean handlingTick;
    private final List<CustomSpawner> customSpawners;
    @Nullable
    private EndDragonFight dragonFight;
    final Int2ObjectMap<EnderDragonPart> dragonParts = new Int2ObjectOpenHashMap<>();
    private final StructureManager structureManager;
    private final StructureCheck structureCheck;
    private final boolean tickTime;
    private double preciseTime; // Purpur - Configurable daylight cycle
    private boolean forceTime; // Purpur - Configurable daylight cycle
    private final RandomSequences randomSequences;

    // CraftBukkit start
    public final LevelStorageSource.LevelStorageAccess levelStorageAccess;
    public final UUID uuid;
    public boolean hasPhysicsEvent = true; // Paper - BlockPhysicsEvent
    public boolean hasEntityMoveEvent; // Paper - Add EntityMoveEvent
    private final alternate.current.wire.WireHandler wireHandler = new alternate.current.wire.WireHandler(this); // Paper - optimize redstone (Alternate Current)

    public LevelChunk getChunkIfLoaded(int x, int z) {
        return this.chunkSource.getChunkAtIfLoadedImmediately(x, z); // Paper - Use getChunkIfLoadedImmediately
    }

    @Override
    public ResourceKey<LevelStem> getTypeKey() {
        return this.levelStorageAccess.dimensionType;
    }

    // Paper start
    public final boolean areChunksLoadedForMove(AABB axisalignedbb) {
        // copied code from collision methods, so that we can guarantee that they wont load chunks (we don't override
        // ICollisionAccess methods for VoxelShapes)
        // be more strict too, add a block (dumb plugins in move events?)
        int minBlockX = Mth.floor(axisalignedbb.minX - 1.0E-7D) - 3;
        int maxBlockX = Mth.floor(axisalignedbb.maxX + 1.0E-7D) + 3;

        int minBlockZ = Mth.floor(axisalignedbb.minZ - 1.0E-7D) - 3;
        int maxBlockZ = Mth.floor(axisalignedbb.maxZ + 1.0E-7D) + 3;

        int minChunkX = minBlockX >> 4;
        int maxChunkX = maxBlockX >> 4;

        int minChunkZ = minBlockZ >> 4;
        int maxChunkZ = maxBlockZ >> 4;

        ServerChunkCache chunkProvider = this.getChunkSource();

        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                if (chunkProvider.getChunkAtIfLoadedImmediately(cx, cz) == null) {
                    return false;
                }
            }
        }

        return true;
    }

    public final void loadChunksForMoveAsync(AABB axisalignedbb, ca.spottedleaf.concurrentutil.util.Priority priority,
                                             java.util.function.Consumer<List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        // Paper - rewrite chunk system
        int minBlockX = Mth.floor(axisalignedbb.minX - 1.0E-7D) - 3;
        int minBlockZ = Mth.floor(axisalignedbb.minZ - 1.0E-7D) - 3;

        int maxBlockX = Mth.floor(axisalignedbb.maxX + 1.0E-7D) + 3;
        int maxBlockZ = Mth.floor(axisalignedbb.maxZ + 1.0E-7D) + 3;

        int minChunkX = minBlockX >> 4;
        int minChunkZ = minBlockZ >> 4;

        int maxChunkX = maxBlockX >> 4;
        int maxChunkZ = maxBlockZ >> 4;

        this.loadChunks(minChunkX, minChunkZ, maxChunkX, maxChunkZ, priority, onLoad);
    }

    public final void loadChunks(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ,
                                 ca.spottedleaf.concurrentutil.util.Priority priority,
                                 java.util.function.Consumer<List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        this.moonrise$loadChunksAsync(minChunkX, maxChunkX, minChunkZ, maxChunkZ, priority, onLoad); // Paper - rewrite chunk system
    }
    // Paper end

    // Paper start - optimise getPlayerByUUID
    @Nullable
    @Override
    public Player getPlayerByUUID(UUID uuid) {
        final Player player = this.getServer().getPlayerList().getPlayer(uuid);
        return player != null && player.level() == this ? player : null;
    }
    // Paper end - optimise getPlayerByUUID
    // Paper start - rewrite chunk system
    private final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.ViewDistanceHolder viewDistanceHolder = new ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.ViewDistanceHolder();
    private final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader chunkLoader = new ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader((ServerLevel)(Object)this);
    private final ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.EntityDataController entityDataController;
    private final ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.PoiDataController poiDataController;
    private final ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.ChunkDataController chunkDataController;
    private final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler chunkTaskScheduler;
    private long lastMidTickFailure;
    private long tickedBlocksOrFluids;
    private final ca.spottedleaf.moonrise.common.misc.NearbyPlayers nearbyPlayers = new ca.spottedleaf.moonrise.common.misc.NearbyPlayers((ServerLevel)(Object)this);
    private static final ServerChunkCache.ChunkAndHolder[] EMPTY_CHUNK_AND_HOLDERS = new ServerChunkCache.ChunkAndHolder[0];
    private final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.server.level.ServerChunkCache.ChunkAndHolder> loadedChunks = new ca.spottedleaf.moonrise.common.list.ReferenceList<>(EMPTY_CHUNK_AND_HOLDERS);
    private final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.server.level.ServerChunkCache.ChunkAndHolder> tickingChunks = new ca.spottedleaf.moonrise.common.list.ReferenceList<>(EMPTY_CHUNK_AND_HOLDERS);
    private final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.server.level.ServerChunkCache.ChunkAndHolder> entityTickingChunks = new ca.spottedleaf.moonrise.common.list.ReferenceList<>(EMPTY_CHUNK_AND_HOLDERS);

    @Override
    public final LevelChunk moonrise$getFullChunkIfLoaded(final int chunkX, final int chunkZ) {
        return this.chunkSource.getChunkNow(chunkX, chunkZ);
    }

    @Override
    public final ChunkAccess moonrise$getAnyChunkIfLoaded(final int chunkX, final int chunkZ) {
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder newChunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkX, chunkZ));
        if (newChunkHolder == null) {
            return null;
        }
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder.ChunkCompletion lastCompletion = newChunkHolder.getLastChunkCompletion();
        return lastCompletion == null ? null : lastCompletion.chunk();
    }

    @Override
    public final ChunkAccess moonrise$getSpecificChunkIfLoaded(final int chunkX, final int chunkZ, final net.minecraft.world.level.chunk.status.ChunkStatus leastStatus) {
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder newChunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        if (newChunkHolder == null) {
            return null;
        }
        return newChunkHolder.getChunkIfPresentUnchecked(leastStatus);
    }

    @Override
    public final void moonrise$midTickTasks() {
        ((ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer)this.server).moonrise$executeMidTickTasks();
    }

    @Override
    public final ChunkAccess moonrise$syncLoadNonFull(final int chunkX, final int chunkZ, final net.minecraft.world.level.chunk.status.ChunkStatus status) {
        return this.moonrise$getChunkTaskScheduler().syncLoadNonFull(chunkX, chunkZ, status);
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler moonrise$getChunkTaskScheduler() {
        return this.chunkTaskScheduler;
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController  moonrise$getChunkDataController() {
        return this.chunkDataController;
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController moonrise$getPoiChunkDataController() {
        return this.poiDataController;
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController moonrise$getEntityChunkDataController() {
        return this.entityDataController;
    }

    @Override
    public final int moonrise$getRegionChunkShift() {
        return io.papermc.paper.threadedregions.TickRegions.getRegionChunkShift();
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader moonrise$getPlayerChunkLoader() {
        return this.chunkLoader;
    }

    @Override
    public final void moonrise$loadChunksAsync(final BlockPos pos, final int radiusBlocks,
                                               final ca.spottedleaf.concurrentutil.util.Priority priority,
                                               final java.util.function.Consumer<java.util.List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        this.moonrise$loadChunksAsync(
            (pos.getX() - radiusBlocks) >> 4,
            (pos.getX() + radiusBlocks) >> 4,
            (pos.getZ() - radiusBlocks) >> 4,
            (pos.getZ() + radiusBlocks) >> 4,
            priority, onLoad
        );
    }

    @Override
    public final void moonrise$loadChunksAsync(final BlockPos pos, final int radiusBlocks,
                                               final net.minecraft.world.level.chunk.status.ChunkStatus chunkStatus, final ca.spottedleaf.concurrentutil.util.Priority priority,
                                               final java.util.function.Consumer<java.util.List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        this.moonrise$loadChunksAsync(
            (pos.getX() - radiusBlocks) >> 4,
            (pos.getX() + radiusBlocks) >> 4,
            (pos.getZ() - radiusBlocks) >> 4,
            (pos.getZ() + radiusBlocks) >> 4,
            chunkStatus, priority, onLoad
        );
    }

    @Override
    public final void moonrise$loadChunksAsync(final int minChunkX, final int maxChunkX, final int minChunkZ, final int maxChunkZ,
                                               final ca.spottedleaf.concurrentutil.util.Priority priority,
                                               final java.util.function.Consumer<java.util.List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        this.moonrise$loadChunksAsync(minChunkX, maxChunkX, minChunkZ, maxChunkZ, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, priority, onLoad);
    }

    @Override
    public final void moonrise$loadChunksAsync(final int minChunkX, final int maxChunkX, final int minChunkZ, final int maxChunkZ,
                                               final net.minecraft.world.level.chunk.status.ChunkStatus chunkStatus, final ca.spottedleaf.concurrentutil.util.Priority priority,
                                               final java.util.function.Consumer<java.util.List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler chunkTaskScheduler = this.moonrise$getChunkTaskScheduler();
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager chunkHolderManager = chunkTaskScheduler.chunkHolderManager;

        final int requiredChunks = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        final java.util.concurrent.atomic.AtomicInteger loadedChunks = new java.util.concurrent.atomic.AtomicInteger();
        final Long holderIdentifier = ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.getNextChunkLoadId();
        final int ticketLevel = ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.getTicketLevel(chunkStatus);

        final List<ChunkAccess> ret = new ArrayList<>(requiredChunks);

        final java.util.function.Consumer<net.minecraft.world.level.chunk.ChunkAccess> consumer = (final ChunkAccess chunk) -> {
            if (chunk != null) {
                synchronized (ret) {
                    ret.add(chunk);
                }
                chunkHolderManager.addTicketAtLevel(ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.CHUNK_LOAD, chunk.getPos(), ticketLevel, holderIdentifier);
            }
            if (loadedChunks.incrementAndGet() == requiredChunks) {
                try {
                    onLoad.accept(java.util.Collections.unmodifiableList(ret));
                } finally {
                    for (int i = 0, len = ret.size(); i < len; ++i) {
                        final ChunkPos chunkPos = ret.get(i).getPos();

                        chunkHolderManager.removeTicketAtLevel(ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.CHUNK_LOAD, chunkPos, ticketLevel, holderIdentifier);
                    }
                }
            }
        };

        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                ca.spottedleaf.moonrise.common.PlatformHooks.get().scheduleChunkLoad(
                    this, cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true, priority, consumer
                );
            }
        }
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.ViewDistanceHolder moonrise$getViewDistanceHolder() {
        return this.viewDistanceHolder;
    }

    @Override
    public final long moonrise$getLastMidTickFailure() {
        return this.lastMidTickFailure;
    }

    @Override
    public final void moonrise$setLastMidTickFailure(final long time) {
        this.lastMidTickFailure = time;
    }

    @Override
    public final ca.spottedleaf.moonrise.common.misc.NearbyPlayers moonrise$getNearbyPlayers() {
        return this.nearbyPlayers;
    }

    @Override
    public final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.server.level.ServerChunkCache.ChunkAndHolder> moonrise$getLoadedChunks() {
        return this.loadedChunks;
    }

    @Override
    public final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.server.level.ServerChunkCache.ChunkAndHolder> moonrise$getTickingChunks() {
        return this.tickingChunks;
    }

    @Override
    public final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.server.level.ServerChunkCache.ChunkAndHolder> moonrise$getEntityTickingChunks() {
        return this.entityTickingChunks;
    }

    @Override
    public final boolean moonrise$areChunksLoaded(final int fromX, final int fromZ, final int toX, final int toZ) {
        final ServerChunkCache chunkSource = this.chunkSource;

        for (int currZ = fromZ; currZ <= toZ; ++currZ) {
            for (int currX = fromX; currX <= toX; ++currX) {
                if (!chunkSource.hasChunk(currX, currZ)) {
                    return false;
                }
            }
        }

        return true;
    }
    // Paper end - rewrite chunk system
    // Paper start - chunk tick iteration
    private static final ServerChunkCache.ChunkAndHolder[] EMPTY_PLAYER_CHUNK_HOLDERS = new ServerChunkCache.ChunkAndHolder[0];
    private final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.server.level.ServerChunkCache.ChunkAndHolder> playerTickingChunks = new ca.spottedleaf.moonrise.common.list.ReferenceList<>(EMPTY_PLAYER_CHUNK_HOLDERS);
    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap playerTickingRequests = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();

    @Override
    public final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.server.level.ServerChunkCache.ChunkAndHolder> moonrise$getPlayerTickingChunks() {
        return this.playerTickingChunks;
    }

    @Override
    public final void moonrise$markChunkForPlayerTicking(final LevelChunk chunk) {
        final ChunkPos pos = chunk.getPos();
        if (!this.playerTickingRequests.containsKey(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(pos))) {
            return;
        }

        this.playerTickingChunks.add(((ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemLevelChunk)chunk).moonrise$getChunkAndHolder());
    }

    @Override
    public final void moonrise$removeChunkForPlayerTicking(final LevelChunk chunk) {
        this.playerTickingChunks.remove(((ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemLevelChunk)chunk).moonrise$getChunkAndHolder());
    }

    @Override
    public final void moonrise$addPlayerTickingRequest(final int chunkX, final int chunkZ) {
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread((ServerLevel)(Object)this, chunkX, chunkZ, "Cannot add ticking request async");

        final long chunkKey = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkX, chunkZ);

        if (this.playerTickingRequests.addTo(chunkKey, 1) != 0) {
            // already added
            return;
        }

        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)(ServerLevel)(Object)this).moonrise$getChunkTaskScheduler()
            .chunkHolderManager.getChunkHolder(chunkKey);

        if (chunkHolder == null || !chunkHolder.isTickingReady()) {
            return;
        }

        this.playerTickingChunks.add(
            ((ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemLevelChunk)(LevelChunk)chunkHolder.getCurrentChunk()).moonrise$getChunkAndHolder()
        );
    }

    @Override
    public final void moonrise$removePlayerTickingRequest(final int chunkX, final int chunkZ) {
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread((ServerLevel)(Object)this, chunkX, chunkZ, "Cannot remove ticking request async");

        final long chunkKey = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkX, chunkZ);
        final int val = this.playerTickingRequests.addTo(chunkKey, -1);

        if (val <= 0) {
            throw new IllegalStateException("Negative counter");
        }

        if (val != 1) {
            // still has at least one request
            return;
        }

        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)(ServerLevel)(Object)this).moonrise$getChunkTaskScheduler()
            .chunkHolderManager.getChunkHolder(chunkKey);

        if (chunkHolder == null || !chunkHolder.isTickingReady()) {
            return;
        }

        this.playerTickingChunks.remove(
            ((ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemLevelChunk)(LevelChunk)chunkHolder.getCurrentChunk()).moonrise$getChunkAndHolder()
        );
    }
    // Paper end - chunk tick iteration

    public ServerLevel(
        MinecraftServer server,
        Executor dispatcher,
        LevelStorageSource.LevelStorageAccess levelStorageAccess,
        net.minecraft.world.level.storage.PrimaryLevelData serverLevelData, // CraftBukkit
        ResourceKey<Level> dimension,
        LevelStem levelStem,
        ChunkProgressListener progressListener,
        boolean isDebug,
        long biomeZoomSeed,
        List<CustomSpawner> customSpawners,
        boolean tickTime,
        @Nullable RandomSequences randomSequences,
        org.bukkit.World.Environment env, // CraftBukkit
        org.bukkit.generator.ChunkGenerator gen, // CraftBukkit
        org.bukkit.generator.BiomeProvider biomeProvider // CraftBukkit
    ) {
        // CraftBukkit start
        super(serverLevelData, dimension, server.registryAccess(), levelStem.type(), false, isDebug, biomeZoomSeed, server.getMaxChainedNeighborUpdates(), gen, biomeProvider, env, spigotConfig -> server.paperConfigurations.createWorldConfig(io.papermc.paper.configuration.PaperConfigurations.createWorldContextMap(levelStorageAccess.levelDirectory.path(), serverLevelData.getLevelName(), dimension.location(), spigotConfig, server.registryAccess(), serverLevelData.getGameRules())), dispatcher); // Paper - create paper world configs; Async-Anti-Xray: Pass executor
        this.pvpMode = server.isPvpAllowed();
        this.levelStorageAccess = levelStorageAccess;
        this.uuid = org.bukkit.craftbukkit.util.WorldUUID.getUUID(levelStorageAccess.levelDirectory.path().toFile());
        // CraftBukkit end
        this.tickTime = tickTime;
        this.server = server;
        // Purpur start - Allow toggling special MobSpawners per world
        this.customSpawners = new ArrayList<>();
        if (purpurConfig.phantomSpawning) {
            this.customSpawners.add(new net.minecraft.world.level.levelgen.PhantomSpawner());
        }
        if (purpurConfig.patrolSpawning) {
            this.customSpawners.add(new net.minecraft.world.level.levelgen.PatrolSpawner());
        }
        if (purpurConfig.catSpawning) {
            this.customSpawners.add(new net.minecraft.world.entity.npc.CatSpawner());
        }
        if (purpurConfig.villageSiegeSpawning) {
            this.customSpawners.add(new net.minecraft.world.entity.ai.village.VillageSiege());
        }
        if (purpurConfig.villagerTraderSpawning) {
            this.customSpawners.add(new net.minecraft.world.entity.npc.WanderingTraderSpawner(serverLevelData));
        }
        // Purpur end - Allow toggling special MobSpawners per world
        this.serverLevelData = serverLevelData;
        ChunkGenerator chunkGenerator = levelStem.generator();
        // CraftBukkit start
        this.serverLevelData.setWorld(this);

        if (biomeProvider != null) {
            net.minecraft.world.level.biome.BiomeSource worldChunkManager = new org.bukkit.craftbukkit.generator.CustomWorldChunkManager(this.getWorld(), biomeProvider, this.server.registryAccess().lookupOrThrow(Registries.BIOME), chunkGenerator.getBiomeSource()); // Paper - add vanillaBiomeProvider
            if (chunkGenerator instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator cga) {
                chunkGenerator = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(worldChunkManager, cga.settings);
            } else if (chunkGenerator instanceof net.minecraft.world.level.levelgen.FlatLevelSource cpf) {
                chunkGenerator = new net.minecraft.world.level.levelgen.FlatLevelSource(cpf.settings(), worldChunkManager);
            }
        }

        if (gen != null) {
            chunkGenerator = new org.bukkit.craftbukkit.generator.CustomChunkGenerator(this, chunkGenerator, gen);
        }
        // CraftBukkit end
        boolean flag = server.forceSynchronousWrites();
        DataFixer fixerUpper = server.getFixerUpper();
        // Paper - rewrite chunk system
        this.chunkSource = new ServerChunkCache(
            this,
            levelStorageAccess,
            fixerUpper,
            server.getStructureManager(),
            dispatcher,
            chunkGenerator,
            this.spigotConfig.viewDistance, // Spigot
            this.spigotConfig.simulationDistance, // Spigot
            flag,
            progressListener,
            null, // Paper - rewrite chunk system
            () -> server.overworld().getDataStorage()
        );
        this.chunkSource.getGeneratorState().ensureStructuresGenerated();
        this.portalForcer = new PortalForcer(this);
        this.updateSkyBrightness();
        this.prepareWeather();
        this.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
        this.raids = this.getDataStorage().computeIfAbsent(Raids.factory(this), Raids.getFileId(this.dimensionTypeRegistration()));
        if (!server.isSingleplayer()) {
            serverLevelData.setGameType(server.getDefaultGameType());
        }

        long seed = server.getWorldData().worldGenOptions().seed();
        this.structureCheck = new StructureCheck(
            this.chunkSource.chunkScanner(),
            this.registryAccess(),
            server.getStructureManager(),
            getTypeKey(), // Paper - Fix missing CB diff
            chunkGenerator,
            this.chunkSource.randomState(),
            this,
            chunkGenerator.getBiomeSource(),
            seed,
            fixerUpper
        );
        this.structureManager = new StructureManager(this, this.serverLevelData.worldGenOptions(), this.structureCheck); // CraftBukkit
        if (this.dimension() == Level.END && this.dimensionTypeRegistration().is(BuiltinDimensionTypes.END) || env == org.bukkit.World.Environment.THE_END) { // CraftBukkit - Allow to create EnderDragonBattle in default and custom END
            this.dragonFight = new EndDragonFight(this, this.serverLevelData.worldGenOptions().seed(), this.serverLevelData.endDragonFightData()); // CraftBukkit
        } else {
            this.dragonFight = null;
        }

        this.sleepStatus = new SleepStatus();
        this.gameEventDispatcher = new GameEventDispatcher(this);
        this.randomSequences = Objects.requireNonNullElseGet(
            randomSequences, () -> this.getDataStorage().computeIfAbsent(RandomSequences.factory(seed), "random_sequences")
        );
        // Paper start - rewrite chunk system
        this.moonrise$setEntityLookup(new ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup((ServerLevel)(Object)this, ((ServerLevel)(Object)this).new EntityCallbacks()));
        this.chunkTaskScheduler = new ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler((ServerLevel)(Object)this);
        this.entityDataController = new ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.EntityDataController(
            new ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.EntityDataController.EntityRegionFileStorage(
                new RegionStorageInfo(levelStorageAccess.getLevelId(), dimension, "entities"),
                levelStorageAccess.getDimensionPath(dimension).resolve("entities"),
                server.forceSynchronousWrites()
            ),
            this.chunkTaskScheduler
        );
        this.poiDataController = new ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.PoiDataController((ServerLevel)(Object)this, this.chunkTaskScheduler);
        this.chunkDataController = new ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.ChunkDataController((ServerLevel)(Object)this, this.chunkTaskScheduler);
        // Paper end - rewrite chunk system
        this.getCraftServer().addWorld(this.getWorld()); // CraftBukkit
        this.preciseTime = this.serverLevelData.getDayTime(); // Purpur - Configurable daylight cycle
    }

    // Paper start
    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return this.getChunkSource().getChunkAtIfLoadedImmediately(chunkX, chunkZ) != null;
    }
    // Paper end

    @Deprecated
    @VisibleForTesting
    public void setDragonFight(@Nullable EndDragonFight dragonFight) {
        this.dragonFight = dragonFight;
    }

    public void setWeatherParameters(int clearTime, int weatherTime, boolean isRaining, boolean isThundering) {
        this.serverLevelData.setClearWeatherTime(clearTime);
        this.serverLevelData.setRainTime(weatherTime);
        this.serverLevelData.setThunderTime(weatherTime);
        this.serverLevelData.setRaining(isRaining, org.bukkit.event.weather.WeatherChangeEvent.Cause.COMMAND); // Paper - Add cause to Weather/ThunderChangeEvents
        this.serverLevelData.setThundering(isThundering, org.bukkit.event.weather.ThunderChangeEvent.Cause.COMMAND); // Paper - Add cause to Weather/ThunderChangeEvents
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return this.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(x, y, z, this.getChunkSource().randomState().sampler());
    }

    public StructureManager structureManager() {
        return this.structureManager;
    }

    public void tick(BooleanSupplier hasTimeLeft) {
        ProfilerFiller profilerFiller = Profiler.get();
        this.handlingTick = true;
        TickRateManager tickRateManager = this.tickRateManager();
        boolean runsNormally = tickRateManager.runsNormally();
        if (runsNormally) {
            profilerFiller.push("world border");
            this.getWorldBorder().tick();
            profilerFiller.popPush("weather");
            this.advanceWeatherCycle();
            profilerFiller.pop();
        }

        int _int = this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        if (this.purpurConfig.playersSkipNight && this.sleepStatus.areEnoughSleeping(_int) && this.sleepStatus.areEnoughDeepSleeping(_int, this.players)) { // Purpur - Config for skipping night
            // Paper start - create time skip event - move up calculations
            final long newDayTime = this.levelData.getDayTime() + 24000L;
            org.bukkit.event.world.TimeSkipEvent event = new org.bukkit.event.world.TimeSkipEvent(
                this.getWorld(),
                org.bukkit.event.world.TimeSkipEvent.SkipReason.NIGHT_SKIP,
                (newDayTime - newDayTime % 24000L) - this.getDayTime()
            );
            // Paper end - create time skip event - move up calculations
            if (this.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                // Paper start - call time skip event if gamerule is enabled
                // long l = this.levelData.getDayTime() + 24000L; // Paper - diff on change to above - newDayTime
                // this.setDayTime(l - l % 24000L); // Paper - diff on change to above - event param
                if (event.callEvent()) {
                    this.setDayTime(this.getDayTime() + event.getSkipAmount());
                }
                // Paper end - call time skip event if gamerule is enabled
            }

            if (!event.isCancelled()) this.wakeUpAllPlayers(); // Paper - only wake up players if time skip event is not cancelled
            if (this.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE) && this.isRaining()) {
                this.resetWeatherCycle();
            }
        }

        this.updateSkyBrightness();
        if (runsNormally) {
            this.tickTime();
        }

        profilerFiller.push("tickPending");
        if (!this.isDebug() && runsNormally) {
            long l = this.getGameTime();
            profilerFiller.push("blockTicks");
            this.blockTicks.tick(l, paperConfig().environment.maxBlockTicks, this::tickBlock); // Paper - configurable max block ticks
            profilerFiller.popPush("fluidTicks");
            this.fluidTicks.tick(l, paperConfig().environment.maxFluidTicks, this::tickFluid); // Paper - configurable max fluid ticks
            profilerFiller.pop();
        }

        profilerFiller.popPush("raid");
        if (runsNormally) {
            this.raids.tick();
        }

        profilerFiller.popPush("chunkSource");
        this.getChunkSource().tick(hasTimeLeft, true);
        profilerFiller.popPush("blockEvents");
        if (runsNormally) {
            this.runBlockEvents();
        }

        this.handlingTick = false;
        profilerFiller.pop();
        boolean flag = !paperConfig().unsupportedSettings.disableWorldTickingWhenEmpty || !this.players.isEmpty() || !this.getForcedChunks().isEmpty(); // CraftBukkit - this prevents entity cleanup, other issues on servers with no players // Paper - restore this
        if (flag) {
            this.resetEmptyTime();
        }

        if (flag || this.emptyTime++ < 300) {
            profilerFiller.push("entities");
            if (this.dragonFight != null && runsNormally) {
                profilerFiller.push("dragonFight");
                this.dragonFight.tick();
                profilerFiller.pop();
            }

            io.papermc.paper.entity.activation.ActivationRange.activateEntities(this); // Paper - EAR
            this.entityTickList
                .forEach(
                    entity -> {
                        if (!entity.isRemoved()) {
                            if (!tickRateManager.isEntityFrozen(entity)) {
                                profilerFiller.push("checkDespawn");
                                entity.checkDespawn();
                                profilerFiller.pop();
                                if (true) { // Paper - rewrite chunk system
                                    Entity vehicle = entity.getVehicle();
                                    if (vehicle != null) {
                                        if (!vehicle.isRemoved() && vehicle.hasPassenger(entity)) {
                                            return;
                                        }

                                        entity.stopRiding();
                                    }

                                    profilerFiller.push("tick");
                                    this.guardEntityTick(this::tickNonPassenger, entity);
                                    profilerFiller.pop();
                                }
                            }
                        }
                    }
                );
            profilerFiller.pop();
            this.tickBlockEntities();
        }

        profilerFiller.push("entityManagement");
        // Paper - rewrite chunk system
        profilerFiller.pop();
    }

    @Override
    public boolean shouldTickBlocksAt(long chunkPos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder holder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkPos);
        return holder != null && holder.isTickingReady();
        // Paper end - rewrite chunk system
    }

    protected void tickTime() {
        if (this.tickTime) {
            long l = this.levelData.getGameTime() + 1L;
            this.serverLevelData.setGameTime(l);
            Profiler.get().push("scheduledFunctions");
            this.serverLevelData.getScheduledEvents().tick(this.server, l);
            Profiler.get().pop();
            if (this.serverLevelData.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                // Purpur start - Configurable daylight cycle
                int incrementTicks = isDay() ? this.purpurConfig.daytimeTicks : this.purpurConfig.nighttimeTicks;
                if (incrementTicks != 12000) {
                    this.preciseTime += 12000 / (double) incrementTicks;
                    this.setDayTime(this.preciseTime);
                } else
                // Purpur end - Configurable daylight cycle
                this.setDayTime(this.levelData.getDayTime() + 1L);
            }
        }
    }

    public void setDayTime(long time) {
        this.serverLevelData.setDayTime(time);
        // Purpur start - Configurable daylight cycle
        this.preciseTime = time;
        this.forceTime = false;
    }
    public void setDayTime(double i) {
        this.serverLevelData.setDayTime((long) i);
        this.forceTime = true;
        // Purpur end - Configurable daylight cycle
    }

    // Purpur start - Configurable daylight cycle
    public boolean isForceTime() {
        return this.forceTime;
    }
    // Purpur end - Configurable daylight cycle

    public void tickCustomSpawners(boolean spawnEnemies, boolean spawnFriendlies) {
        for (CustomSpawner customSpawner : this.customSpawners) {
            customSpawner.tick(this, spawnEnemies, spawnFriendlies);
        }
    }

    private void wakeUpAllPlayers() {
        this.sleepStatus.removeAllSleepers();
        this.players.stream().filter(LivingEntity::isSleeping).collect(Collectors.toList()).forEach(player -> player.stopSleepInBed(false, false));
    }

    // Paper start - optimise random ticking
    private final ca.spottedleaf.moonrise.common.util.SimpleThreadUnsafeRandom simpleRandom = new ca.spottedleaf.moonrise.common.util.SimpleThreadUnsafeRandom(net.minecraft.world.level.levelgen.RandomSupport.generateUniqueSeed());

    private void optimiseRandomTick(final LevelChunk chunk, final int tickSpeed) {
        final LevelChunkSection[] sections = chunk.getSections();
        final int minSection = ca.spottedleaf.moonrise.common.util.WorldUtil.getMinSection((ServerLevel)(Object)this);
        final ca.spottedleaf.moonrise.common.util.SimpleThreadUnsafeRandom simpleRandom = this.simpleRandom;
        final boolean doubleTickFluids = !ca.spottedleaf.moonrise.common.PlatformHooks.get().configFixMC224294();

        final ChunkPos cpos = chunk.getPos();
        final int offsetX = cpos.x << 4;
        final int offsetZ = cpos.z << 4;

        for (int sectionIndex = 0, sectionsLen = sections.length; sectionIndex < sectionsLen; sectionIndex++) {
            final int offsetY = (sectionIndex + minSection) << 4;
            final LevelChunkSection section = sections[sectionIndex];
            final net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.world.level.block.state.BlockState> states = section.states;
            if (!section.isRandomlyTickingBlocks()) {
                continue;
            }

            final ca.spottedleaf.moonrise.common.list.ShortList tickList = ((ca.spottedleaf.moonrise.patches.block_counting.BlockCountingChunkSection)section).moonrise$getTickingBlockList();

            for (int i = 0; i < tickSpeed; ++i) {
                final int tickingBlocks = tickList.size();
                final int index = simpleRandom.nextInt() & ((16 * 16 * 16) - 1);

                if (index >= tickingBlocks) {
                    // most of the time we fall here
                    continue;
                }

                final int location = (int)tickList.getRaw(index) & 0xFFFF;
                final BlockState state = states.get(location);

                // do not use a mutable pos, as some random tick implementations store the input without calling immutable()!
                final BlockPos pos = new BlockPos((location & 15) | offsetX, ((location >>> (4 + 4)) & 15) | offsetY, ((location >>> 4) & 15) | offsetZ);

                state.randomTick((ServerLevel)(Object)this, pos, simpleRandom);
                if (doubleTickFluids) {
                    final FluidState fluidState = state.getFluidState();
                    if (fluidState.isRandomlyTicking()) {
                        fluidState.randomTick((ServerLevel)(Object)this, pos, simpleRandom);
                    }
                }
            }
        }

        return;
    }
    // Paper end - optimise random ticking

    public void tickChunk(LevelChunk chunk, int randomTickSpeed) {
        final ca.spottedleaf.moonrise.common.util.SimpleThreadUnsafeRandom simpleRandom = this.simpleRandom; // Paper - optimise random ticking
        ChunkPos pos = chunk.getPos();
        boolean isRaining = this.isRaining();
        int minBlockX = pos.getMinBlockX();
        int minBlockZ = pos.getMinBlockZ();
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("thunder");
        if (!this.paperConfig().environment.disableThunder && isRaining && this.isThundering() && this.spigotConfig.thunderChance > 0 && simpleRandom.nextInt(this.spigotConfig.thunderChance) == 0) { // Spigot // Paper - Option to disable thunder // Paper - optimise random ticking
            BlockPos blockPos = this.findLightningTargetAround(this.getBlockRandomPos(minBlockX, 0, minBlockZ, 15));
            if (this.isRainingAt(blockPos)) {
                DifficultyInstance currentDifficultyAt = this.getCurrentDifficultyAt(blockPos);
                boolean flag = this.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                    && this.random.nextDouble() < currentDifficultyAt.getEffectiveDifficulty() * this.paperConfig().entities.spawning.skeletonHorseThunderSpawnChance.or(0.01) // Paper - Configurable spawn chances for skeleton horses
                    && !this.getBlockState(blockPos.below()).is(Blocks.LIGHTNING_ROD);
                if (flag) {
                    // Purpur start - Special mobs naturally spawn
                    net.minecraft.world.entity.animal.horse.AbstractHorse entityhorseskeleton;
                    if (purpurConfig.zombieHorseSpawnChance > 0D && random.nextDouble() <= purpurConfig.zombieHorseSpawnChance) {
                        entityhorseskeleton = EntityType.ZOMBIE_HORSE.create(this, EntitySpawnReason.EVENT);
                    } else {
                        entityhorseskeleton = EntityType.SKELETON_HORSE.create(this, EntitySpawnReason.EVENT);
                        if (entityhorseskeleton != null) ((SkeletonHorse) entityhorseskeleton).setTrap(true);
                    }
                    // Purpur end - Special mobs naturally spawn
                    SkeletonHorse skeletonHorse = EntityType.SKELETON_HORSE.create(this, EntitySpawnReason.EVENT);
                    if (skeletonHorse != null) {
                        //skeletonHorse.setTrap(true); // Purpur - Special mobs naturally spawn - moved up
                        skeletonHorse.setAge(0);
                        skeletonHorse.setPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                        this.addFreshEntity(skeletonHorse, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.LIGHTNING); // CraftBukkit
                    }
                }

                LightningBolt lightningBolt = EntityType.LIGHTNING_BOLT.create(this, EntitySpawnReason.EVENT);
                if (lightningBolt != null) {
                    lightningBolt.moveTo(Vec3.atBottomCenterOf(blockPos));
                    lightningBolt.setVisualOnly(flag);
                    this.strikeLightning(lightningBolt, org.bukkit.event.weather.LightningStrikeEvent.Cause.WEATHER); // CraftBukkit
                }
            }
        }

        profilerFiller.popPush("iceandsnow");

        if (!this.paperConfig().environment.disableIceAndSnow) { // Paper - Option to disable ice and snow
        for (int i = 0; i < randomTickSpeed; i++) {
            if (simpleRandom.nextInt(48) == 0) {  // Paper - optimise random ticking
                this.tickPrecipitation(this.getBlockRandomPos(minBlockX, 0, minBlockZ, 15));
            }
        }
        } // Paper - Option to disable ice and snow

        profilerFiller.popPush("tickBlocks");
        if (randomTickSpeed > 0) {
            this.optimiseRandomTick(chunk, randomTickSpeed); // Paper - optimise random ticking
        }

        profilerFiller.pop();
    }

    @VisibleForTesting
    public void tickPrecipitation(BlockPos blockPos) {
        BlockPos heightmapPos = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockPos);
        BlockPos blockPos1 = heightmapPos.below();
        Biome biome = this.getBiome(heightmapPos).value();
        if (biome.shouldFreeze(this, blockPos1)) {
            org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this, blockPos1, Blocks.ICE.defaultBlockState(), null); // CraftBukkit
        }

        if (this.isRaining()) {
            int _int = this.getGameRules().getInt(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT);
            if (_int > 0 && biome.shouldSnow(this, heightmapPos)) {
                BlockState blockState = this.getBlockState(heightmapPos);
                if (blockState.is(Blocks.SNOW)) {
                    int layersValue = blockState.getValue(SnowLayerBlock.LAYERS);
                    if (layersValue < Math.min(_int, 8)) {
                        BlockState blockState1 = blockState.setValue(SnowLayerBlock.LAYERS, Integer.valueOf(layersValue + 1));
                        Block.pushEntitiesUp(blockState, blockState1, this, heightmapPos);
                        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this, heightmapPos, blockState1, null); // CraftBukkit
                    }
                } else {
                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this, heightmapPos, Blocks.SNOW.defaultBlockState(), null); // CraftBukkit
                }
            }

            Biome.Precipitation precipitationAt = biome.getPrecipitationAt(blockPos1, this.getSeaLevel());
            if (precipitationAt != Biome.Precipitation.NONE) {
                BlockState blockState2 = this.getBlockState(blockPos1);
                blockState2.getBlock().handlePrecipitation(blockState2, this, blockPos1, precipitationAt);
            }
        }
    }

    public Optional<BlockPos> findLightningRod(BlockPos pos) {
        Optional<BlockPos> optional = this.getPoiManager()
            .findClosest(
                pointOfInterestType -> pointOfInterestType.is(PoiTypes.LIGHTNING_ROD),
                blockPos -> blockPos.getY() == this.getHeight(Heightmap.Types.WORLD_SURFACE, blockPos.getX(), blockPos.getZ()) - 1,
                pos,
                org.purpurmc.purpur.PurpurConfig.lightningRodRange, // Purpur - Make lightning rod range configurable
                PoiManager.Occupancy.ANY
            );
        return optional.map(blockPos -> blockPos.above(1));
    }

    protected BlockPos findLightningTargetAround(BlockPos pos) {
        // Paper start - Add methods to find targets for lightning strikes
        return this.findLightningTargetAround(pos, false);
    }
    public BlockPos findLightningTargetAround(BlockPos pos, boolean returnNullWhenNoTarget) {
        // Paper end - Add methods to find targets for lightning strikes
        BlockPos heightmapPos = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        Optional<BlockPos> optional = this.findLightningRod(heightmapPos);
        if (optional.isPresent()) {
            return optional.get();
        } else {
            AABB aabb = AABB.encapsulatingFullBlocks(heightmapPos, heightmapPos.atY(this.getMaxY() + 1)).inflate(3.0);
            List<LivingEntity> entitiesOfClass = this.getEntitiesOfClass(
                LivingEntity.class, aabb, entity -> entity != null && entity.isAlive() && this.canSeeSky(entity.blockPosition()) && !entity.isSpectator() // Paper - Fix lightning being able to hit spectators (MC-262422)
            );
            if (!entitiesOfClass.isEmpty()) {
                return entitiesOfClass.get(this.random.nextInt(entitiesOfClass.size())).blockPosition();
            } else {
                if (returnNullWhenNoTarget) return null; // Paper - Add methods to find targets for lightning strikes
                if (heightmapPos.getY() == this.getMinY() - 1) {
                    heightmapPos = heightmapPos.above(2);
                }

                return heightmapPos;
            }
        }
    }

    public boolean isHandlingTick() {
        return this.handlingTick;
    }

    public boolean canSleepThroughNights() {
        return this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE) <= 100;
    }

    private void announceSleepStatus() {
        if (this.canSleepThroughNights()) {
            if (!this.getServer().isSingleplayer() || this.getServer().isPublished()) {
                int _int = this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
                Component component;
                if (this.sleepStatus.areEnoughSleeping(_int)) {
                    // Purpur start - Customizable sleeping actionbar messages
                    if (org.purpurmc.purpur.PurpurConfig.sleepSkippingNight.isBlank()) {
                        return;
                    }
                    if (!org.purpurmc.purpur.PurpurConfig.sleepSkippingNight.equalsIgnoreCase("default")) {
                        component = io.papermc.paper.adventure.PaperAdventure.asVanilla(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(org.purpurmc.purpur.PurpurConfig.sleepSkippingNight));
                    } else
                    // Purpur end - Customizable sleeping actionbar messages
                    component = Component.translatable("sleep.skipping_night");
                } else {
                    // Purpur start - Customizable sleeping actionbar messages
                    if (org.purpurmc.purpur.PurpurConfig.sleepingPlayersPercent.isBlank()) {
                        return;
                    }
                    if (!org.purpurmc.purpur.PurpurConfig.sleepingPlayersPercent.equalsIgnoreCase("default")) {
                        component = io.papermc.paper.adventure.PaperAdventure.asVanilla(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(org.purpurmc.purpur.PurpurConfig.sleepingPlayersPercent,
                            net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed("count", Integer.toString(this.sleepStatus.amountSleeping())),
                            net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed("total", Integer.toString(this.sleepStatus.sleepersNeeded(_int)))));
                    } else
                    // Purpur end - Customizable sleeping actionbar messages
                    component = Component.translatable("sleep.players_sleeping", this.sleepStatus.amountSleeping(), this.sleepStatus.sleepersNeeded(_int));
                }

                for (ServerPlayer serverPlayer : this.players) {
                    serverPlayer.displayClientMessage(component, true);
                }
            }
        }
    }

    public void updateSleepingPlayerList() {
        if (!this.players.isEmpty() && this.sleepStatus.update(this.players)) {
            this.announceSleepStatus();
        }
    }

    @Override
    public ServerScoreboard getScoreboard() {
        return this.server.getScoreboard();
    }

    private void advanceWeatherCycle() {
        boolean isRaining = this.isRaining();
        if (this.dimensionType().hasSkyLight()) {
            if (this.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) {
                int clearWeatherTime = this.serverLevelData.getClearWeatherTime();
                int thunderTime = this.serverLevelData.getThunderTime();
                int rainTime = this.serverLevelData.getRainTime();
                boolean isThundering = this.levelData.isThundering();
                boolean isRaining1 = this.levelData.isRaining();
                if (clearWeatherTime > 0) {
                    clearWeatherTime--;
                    thunderTime = isThundering ? 0 : 1;
                    rainTime = isRaining1 ? 0 : 1;
                    isThundering = false;
                    isRaining1 = false;
                } else {
                    if (thunderTime > 0) {
                        if (--thunderTime == 0) {
                            isThundering = !isThundering;
                        }
                    } else if (isThundering) {
                        thunderTime = THUNDER_DURATION.sample(this.random);
                    } else {
                        thunderTime = THUNDER_DELAY.sample(this.random);
                    }

                    if (rainTime > 0) {
                        if (--rainTime == 0) {
                            isRaining1 = !isRaining1;
                        }
                    } else if (isRaining1) {
                        rainTime = RAIN_DURATION.sample(this.random);
                    } else {
                        rainTime = RAIN_DELAY.sample(this.random);
                    }
                }

                this.serverLevelData.setThunderTime(thunderTime);
                this.serverLevelData.setRainTime(rainTime);
                this.serverLevelData.setClearWeatherTime(clearWeatherTime);
                this.serverLevelData.setThundering(isThundering, org.bukkit.event.weather.ThunderChangeEvent.Cause.NATURAL); // Paper - Add cause to Weather/ThunderChangeEvents
                this.serverLevelData.setRaining(isRaining1, org.bukkit.event.weather.WeatherChangeEvent.Cause.NATURAL); // Paper - Add cause to Weather/ThunderChangeEvents
            }

            this.oThunderLevel = this.thunderLevel;
            if (this.levelData.isThundering()) {
                this.thunderLevel += 0.01F;
            } else {
                this.thunderLevel -= 0.01F;
            }

            this.thunderLevel = Mth.clamp(this.thunderLevel, 0.0F, 1.0F);
            this.oRainLevel = this.rainLevel;
            if (this.levelData.isRaining()) {
                this.rainLevel += 0.01F;
            } else {
                this.rainLevel -= 0.01F;
            }

            this.rainLevel = Mth.clamp(this.rainLevel, 0.0F, 1.0F);
        }

        /* CraftBukkit start
        if (this.oRainLevel != this.rainLevel) {
            this.server
                .getPlayerList()
                .broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, this.rainLevel), this.dimension());
        }

        if (this.oThunderLevel != this.thunderLevel) {
            this.server
                .getPlayerList()
                .broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, this.thunderLevel), this.dimension());
        }

        if (isRaining != this.isRaining()) {
            if (isRaining) {
                this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0.0F));
            } else {
                this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0F));
            }

            this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, this.rainLevel));
            this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, this.thunderLevel));
        }
        */
        for (ServerPlayer player : this.players) {
            if (player.level() == this) {
                player.tickWeather();
            }
        }

        if (isRaining != this.isRaining()) {
            // Only send weather packets to those affected
            for (ServerPlayer player : this.players) {
                if (player.level() == this) {
                    player.setPlayerWeather((!isRaining ? org.bukkit.WeatherType.DOWNFALL : org.bukkit.WeatherType.CLEAR), false);
                }
            }
        }
        for (ServerPlayer player : this.players) {
            if (player.level() == this) {
                player.updateWeather(this.oRainLevel, this.rainLevel, this.oThunderLevel, this.thunderLevel);
            }
        }
        // CraftBukkit end
    }

    @VisibleForTesting
    public void resetWeatherCycle() {
        // CraftBukkit start
        if (this.purpurConfig.rainStopsAfterSleep) // Purpur - Option for if rain and thunder should stop on sleep
        this.serverLevelData.setRaining(false, org.bukkit.event.weather.WeatherChangeEvent.Cause.SLEEP); // Paper - Add cause to Weather/ThunderChangeEvents
        // If we stop due to everyone sleeping we should reset the weather duration to some other random value.
        // Not that everyone ever manages to get the whole server to sleep at the same time....
        if (!this.serverLevelData.isRaining()) {
            this.serverLevelData.setRainTime(0);
        }
        // CraftBukkit end
        if (this.purpurConfig.thunderStopsAfterSleep) // Purpur - Option for if rain and thunder should stop on sleep
        this.serverLevelData.setThundering(false, org.bukkit.event.weather.ThunderChangeEvent.Cause.SLEEP); // Paper - Add cause to Weather/ThunderChangeEvents
        // CraftBukkit start
        // If we stop due to everyone sleeping we should reset the weather duration to some other random value.
        // Not that everyone ever manages to get the whole server to sleep at the same time....
        if (!this.serverLevelData.isThundering()) {
            this.serverLevelData.setThunderTime(0);
        }
        // CraftBukkit end
    }

    public void resetEmptyTime() {
        this.emptyTime = 0;
    }

    private void tickFluid(BlockPos pos, Fluid fluid) {
        BlockState blockState = this.getBlockState(pos);
        FluidState fluidState = blockState.getFluidState();
        if (fluidState.is(fluid)) {
            fluidState.tick(this, pos, blockState);
        }
        // Paper start - rewrite chunk system
        if ((++this.tickedBlocksOrFluids & 7L) != 0L) {
            ((ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer)this.server).moonrise$executeMidTickTasks();
        }
        // Paper end - rewrite chunk system

    }

    private void tickBlock(BlockPos pos, Block block) {
        BlockState blockState = this.getBlockState(pos);
        if (blockState.is(block)) {
            blockState.tick(this, pos, this.random);
        }
        // Paper start - rewrite chunk system
        if ((++this.tickedBlocksOrFluids & 7L) != 0L) {
            ((ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer)this.server).moonrise$executeMidTickTasks();
        }
        // Paper end - rewrite chunk system

    }

    // Paper start - log detailed entity tick information
    // TODO replace with varhandle
    static final java.util.concurrent.atomic.AtomicReference<Entity> currentlyTickingEntity = new java.util.concurrent.atomic.AtomicReference<>();

    public static List<Entity> getCurrentlyTickingEntities() {
        Entity ticking = currentlyTickingEntity.get();
        List<Entity> ret = java.util.Arrays.asList(ticking == null ? new Entity[0] : new Entity[] { ticking });

        return ret;
    }
    // Paper end - log detailed entity tick information

    public void tickNonPassenger(Entity entity) {
        // Paper start - log detailed entity tick information
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread("Cannot tick an entity off-main");
        try {
            if (currentlyTickingEntity.get() == null) {
                currentlyTickingEntity.lazySet(entity);
            }
            // Paper end - log detailed entity tick information
        entity.setOldPosAndRot();
        ProfilerFiller profilerFiller = Profiler.get();
        entity.tickCount++;
        profilerFiller.push(() -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        profilerFiller.incrementCounter("tickNonPassenger");
        final boolean isActive = io.papermc.paper.entity.activation.ActivationRange.checkIfActive(entity); // Paper - EAR 2
        if (isActive) { // Paper - EAR 2
        entity.tick();
        entity.postTick(); // CraftBukkit
        } else {entity.inactiveTick();} // Paper - EAR 2
        profilerFiller.pop();

        for (Entity entity1 : entity.getPassengers()) {
            this.tickPassenger(entity, entity1, isActive); // Paper - EAR 2
        }
        // Paper start - log detailed entity tick information
        } finally {
            if (currentlyTickingEntity.get() == entity) {
                currentlyTickingEntity.lazySet(null);
            }
        }
        // Paper end - log detailed entity tick information
    }

    private void tickPassenger(Entity ridingEntity, Entity passengerEntity, final boolean isActive) { // Paper - EAR 2
        if (passengerEntity.isRemoved() || passengerEntity.getVehicle() != ridingEntity) {
            passengerEntity.stopRiding();
        } else if (passengerEntity instanceof Player || this.entityTickList.contains(passengerEntity)) {
            passengerEntity.setOldPosAndRot();
            passengerEntity.tickCount++;
            ProfilerFiller profilerFiller = Profiler.get();
            profilerFiller.push(() -> BuiltInRegistries.ENTITY_TYPE.getKey(passengerEntity.getType()).toString());
            profilerFiller.incrementCounter("tickPassenger");
            // Paper start - EAR 2
            if (isActive) {
            passengerEntity.rideTick();
            passengerEntity.postTick(); // CraftBukkit
            } else {
                passengerEntity.setDeltaMovement(Vec3.ZERO);
                passengerEntity.inactiveTick();
                // copied from inside of if (isPassenger()) of passengerTick, but that ifPassenger is unnecessary
                ridingEntity.positionRider(passengerEntity);
            }
            // Paper end - EAR 2
            profilerFiller.pop();

            for (Entity entity : passengerEntity.getPassengers()) {
                this.tickPassenger(passengerEntity, entity, isActive); // Paper - EAR 2
            }
        }
    }

    @Override
    public boolean mayInteract(Player player, BlockPos pos) {
        return !this.server.isUnderSpawnProtection(this, pos, player) && this.getWorldBorder().isWithinBounds(pos);
    }

    // Paper start - Incremental chunk and player saving
    public void saveIncrementally(boolean doFull) {
        if (doFull) {
            org.bukkit.Bukkit.getPluginManager().callEvent(new org.bukkit.event.world.WorldSaveEvent(this.getWorld()));
        }

        if (doFull) {
            this.saveLevelData(true);
        }
        // chunk autosave is already called by the ChunkSystem during unload processing (ChunkMap#processUnloads)
        // Copied from save()
        // CraftBukkit start - moved from MinecraftServer.saveChunks
        if (doFull) { // Paper
            ServerLevel serverLevel1 = this;
            this.serverLevelData.setWorldBorder(serverLevel1.getWorldBorder().createSettings());
            this.serverLevelData.setCustomBossEvents(this.server.getCustomBossEvents().save(this.registryAccess()));
            this.levelStorageAccess.saveDataTag(this.server.registryAccess(), this.serverLevelData, this.server.getPlayerList().getSingleplayerData());
        }
        // CraftBukkit end
    }
    // Paper end - Incremental chunk and player saving

    public void save(@Nullable ProgressListener progress, boolean flush, boolean skipSave) {
        // Paper start - add close param
        this.save(progress, flush, skipSave, false);
    }
    public void save(@Nullable ProgressListener progress, boolean flush, boolean skipSave, boolean close) {
        // Paper end - add close param
        ServerChunkCache chunkSource = this.getChunkSource();
        if (!skipSave) {
            org.bukkit.Bukkit.getPluginManager().callEvent(new org.bukkit.event.world.WorldSaveEvent(this.getWorld())); // CraftBukkit
            if (progress != null) {
                progress.progressStartNoAbort(Component.translatable("menu.savingLevel"));
            }

            this.saveLevelData(flush);
            if (progress != null) {
                progress.progressStage(Component.translatable("menu.savingChunks"));
            }

            if (!close) { chunkSource.save(flush); } // Paper - add close param
            // Paper - rewrite chunk system
        }
        // Paper start - add close param
        if (close) {
            try {
                chunkSource.close(!skipSave);
            } catch (IOException never) {
                throw new RuntimeException(never);
            }
        }
        // Paper end - add close param

        // CraftBukkit start - moved from MinecraftServer.saveChunks
        ServerLevel worldserver1 = this;

        this.serverLevelData.setWorldBorder(worldserver1.getWorldBorder().createSettings());
        this.serverLevelData.setCustomBossEvents(this.server.getCustomBossEvents().save(this.registryAccess()));
        this.levelStorageAccess.saveDataTag(this.server.registryAccess(), this.serverLevelData, this.server.getPlayerList().getSingleplayerData());
        // CraftBukkit end
    }

    private void saveLevelData(boolean join) {
        if (this.dragonFight != null) {
            this.serverLevelData.setEndDragonFightData(this.dragonFight.saveData()); // CraftBukkit
        }

        DimensionDataStorage dataStorage = this.getChunkSource().getDataStorage();
        if (join) {
            dataStorage.saveAndJoin();
        } else {
            dataStorage.scheduleSave();
        }
    }

    public <T extends Entity> List<? extends T> getEntities(EntityTypeTest<Entity, T> typeTest, Predicate<? super T> predicate) {
        List<T> list = Lists.newArrayList();
        this.getEntities(typeTest, predicate, list);
        return list;
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> typeTest, Predicate<? super T> predicate, List<? super T> output) {
        this.getEntities(typeTest, predicate, output, Integer.MAX_VALUE);
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> typeTest, Predicate<? super T> predicate, List<? super T> output, int maxResults) {
        this.getEntities().get(typeTest, entity -> {
            if (predicate.test(entity)) {
                output.add(entity);
                if (output.size() >= maxResults) {
                    return AbortableIterationConsumer.Continuation.ABORT;
                }
            }

            return AbortableIterationConsumer.Continuation.CONTINUE;
        });
    }

    public List<? extends EnderDragon> getDragons() {
        return this.getEntities(EntityType.ENDER_DRAGON, LivingEntity::isAlive);
    }

    public List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> predicate) {
        return this.getPlayers(predicate, Integer.MAX_VALUE);
    }

    public List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> predicate, int maxResults) {
        List<ServerPlayer> list = Lists.newArrayList();

        for (ServerPlayer serverPlayer : this.players) {
            if (predicate.test(serverPlayer)) {
                list.add(serverPlayer);
                if (list.size() >= maxResults) {
                    return list;
                }
            }
        }

        return list;
    }

    @Nullable
    public ServerPlayer getRandomPlayer() {
        List<ServerPlayer> players = this.getPlayers(LivingEntity::isAlive);
        return players.isEmpty() ? null : players.get(this.random.nextInt(players.size()));
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        // CraftBukkit start
        return this.addFreshEntity(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    @Override
    public boolean addFreshEntity(Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        return this.addEntity(entity, reason);
        // CraftBukkit end
    }

    public boolean addWithUUID(Entity entity) {
        // CraftBukkit start
        return this.addWithUUID(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    public boolean addWithUUID(Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        return this.addEntity(entity, reason);
        // CraftBukkit end
    }

    public void addDuringTeleport(Entity entity) {
        // CraftBukkit start
        // SPIGOT-6415: Don't call spawn event for entities which travel trough worlds,
        // since it is only an implementation detail, that a new entity is created when
        // they are traveling between worlds.
        this.addDuringTeleport(entity, null);
    }

    public void addDuringTeleport(Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        // CraftBukkit end
        if (entity instanceof ServerPlayer serverPlayer) {
            this.addPlayer(serverPlayer);
        } else {
            this.addEntity(entity, reason); // CraftBukkit
        }
    }

    public void addNewPlayer(ServerPlayer player) {
        this.addPlayer(player);
    }

    public void addRespawnedPlayer(ServerPlayer player) {
        this.addPlayer(player);
    }

    private void addPlayer(ServerPlayer player) {
        Entity entity = this.getEntities().get(player.getUUID());
        if (entity != null) {
            LOGGER.warn("Force-added player with duplicate UUID {}", player.getUUID());
            entity.unRide();
            this.removePlayerImmediately((ServerPlayer)entity, Entity.RemovalReason.DISCARDED);
        }

        this.moonrise$getEntityLookup().addNewEntity(player); // Paper - rewrite chunk system
    }

    // CraftBukkit start
    private boolean addEntity(Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason spawnReason) {
        org.spigotmc.AsyncCatcher.catchOp("entity add"); // Spigot
        entity.generation = false; // Paper - Don't fire sync event during generation; Reset flag if it was added during a ServerLevel generation process
        // Paper start - extra debug info
        if (entity.valid) {
            MinecraftServer.LOGGER.error("Attempted Double World add on {}", entity, new Throwable());
            return true;
        }
        // Paper end - extra debug info
        if (entity.spawnReason == null) entity.spawnReason = spawnReason; // Paper - Entity#getEntitySpawnReason
        if (entity.isRemoved()) {
            // LOGGER.warn("Tried to add entity {} but it was marked as removed already", EntityType.getKey(entity.getType())); // CraftBukkit - remove warning
            return false;
        } else {
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity && itemEntity.getItem().isEmpty()) return false; // Paper - Prevent empty items from being added
            // Paper start - capture all item additions to the world
            if (captureDrops != null && entity instanceof net.minecraft.world.entity.item.ItemEntity) {
                captureDrops.add((net.minecraft.world.entity.item.ItemEntity) entity);
                return true;
            }
            // Paper end - capture all item additions to the world
            // SPIGOT-6415: Don't call spawn event when reason is null. For example when an entity teleports to a new world.
            if (spawnReason != null && !org.bukkit.craftbukkit.event.CraftEventFactory.doEntityAddEventCalling(this, entity, spawnReason)) {
                return false;
            }
            // CraftBukkit end

            return this.moonrise$getEntityLookup().addNewEntity(entity); // Paper - rewrite chunk system
        }
    }

    public boolean tryAddFreshEntityWithPassengers(Entity entity) {
        // CraftBukkit start
        return this.tryAddFreshEntityWithPassengers(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    public boolean tryAddFreshEntityWithPassengers(Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        // CraftBukkit end
        if (entity.getSelfAndPassengers().map(Entity::getUUID).anyMatch(this.moonrise$getEntityLookup()::hasEntity)) { // Paper - rewrite chunk system
            return false;
        } else {
            this.addFreshEntityWithPassengers(entity, reason); // CraftBukkit
            return true;
        }
    }

    public void unload(LevelChunk chunk) {
        // Spigot start
        for (net.minecraft.world.level.block.entity.BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof net.minecraft.world.Container) {
                // Paper start - this area looks like it can load chunks, change the behavior
                // chests for example can apply physics to the world
                // so instead we just change the active container and call the event
                for (org.bukkit.entity.HumanEntity human : Lists.newArrayList(((net.minecraft.world.Container) blockEntity).getViewers())) {
                    ((org.bukkit.craftbukkit.entity.CraftHumanEntity) human).getHandle().closeUnloadedInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason.UNLOADED); // Paper - Inventory close reason
                }
                // Paper end - this area looks like it can load chunks, change the behavior
            }
        }
        // Spigot end
        chunk.clearAllBlockEntities();
        chunk.unregisterTickContainerFromLevel(this);
    }

    public void removePlayerImmediately(ServerPlayer player, Entity.RemovalReason reason) {
        player.remove(reason, null); // CraftBukkit - add Bukkit remove cause
    }

    // CraftBukkit start
    public boolean strikeLightning(Entity entitylightning) {
        return this.strikeLightning(entitylightning, org.bukkit.event.weather.LightningStrikeEvent.Cause.UNKNOWN);
    }

    public boolean strikeLightning(Entity entitylightning, org.bukkit.event.weather.LightningStrikeEvent.Cause cause) {
        org.bukkit.event.weather.LightningStrikeEvent lightning = org.bukkit.craftbukkit.event.CraftEventFactory.callLightningStrikeEvent((org.bukkit.entity.LightningStrike) entitylightning.getBukkitEntity(), cause);

        if (lightning.isCancelled()) {
            return false;
        }

        return this.addFreshEntity(entitylightning);
    }
    // CraftBukkit end

    @Override
    public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {
        // CraftBukkit start
        Player breakerPlayer = null;
        Entity entity = this.getEntity(breakerId);
        if (entity instanceof Player) breakerPlayer = (Player) entity;
        // CraftBukkit end

        // Paper start - Add BlockBreakProgressUpdateEvent
        // If a plugin is using this method to send destroy packets for a client-side only entity id, no block progress occurred on the server.
        // Hence, do not call the event.
        if (entity != null) {
            float progressFloat = Mth.clamp(progress, 0, 10) / 10.0f;
            org.bukkit.craftbukkit.block.CraftBlock bukkitBlock = org.bukkit.craftbukkit.block.CraftBlock.at(this, pos);
            new io.papermc.paper.event.block.BlockBreakProgressUpdateEvent(bukkitBlock, progressFloat, entity.getBukkitEntity())
                .callEvent();
        }
        // Paper end - Add BlockBreakProgressUpdateEvent
        for (ServerPlayer serverPlayer : this.server.getPlayerList().getPlayers()) {
            if (serverPlayer != null && serverPlayer.level() == this && serverPlayer.getId() != breakerId) {
                double d = pos.getX() - serverPlayer.getX();
                double d1 = pos.getY() - serverPlayer.getY();
                double d2 = pos.getZ() - serverPlayer.getZ();
                // CraftBukkit start
                if (breakerPlayer != null && !serverPlayer.getBukkitEntity().canSee(breakerPlayer.getBukkitEntity())) {
                    continue;
                }
                // CraftBukkit end
                if (d * d + d1 * d1 + d2 * d2 < 1024.0) {
                    serverPlayer.connection.send(new ClientboundBlockDestructionPacket(breakerId, pos, progress));
                }
            }
        }
    }

    @Override
    public void playSeededSound(
        @Nullable Player player, double x, double y, double z, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed
    ) {
        this.server
            .getPlayerList()
            .broadcast(
                player, x, y, z, sound.value().getRange(volume), this.dimension(), new ClientboundSoundPacket(sound, category, x, y, z, volume, pitch, seed)
            );
    }

    @Override
    public void playSeededSound(@Nullable Player player, Entity entity, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed) {
        this.server
            .getPlayerList()
            .broadcast(
                player,
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                sound.value().getRange(volume),
                this.dimension(),
                new ClientboundSoundEntityPacket(sound, category, entity, volume, pitch, seed)
            );
    }

    @Override
    public void globalLevelEvent(int id, BlockPos pos, int data) {
        if (this.getGameRules().getBoolean(GameRules.RULE_GLOBAL_SOUND_EVENTS)) {
            this.server.getPlayerList().getPlayers().forEach(player -> {
                Vec3 vec31;
                if (player.level() == this) {
                    Vec3 vec3 = Vec3.atCenterOf(pos);
                    if (player.distanceToSqr(vec3) < Mth.square(32)) {
                        vec31 = vec3;
                    } else {
                        Vec3 vec32 = vec3.subtract(player.position()).normalize();
                        vec31 = player.position().add(vec32.scale(32.0));
                    }
                } else {
                    vec31 = player.position();
                }

                player.connection.send(new ClientboundLevelEventPacket(id, BlockPos.containing(vec31), data, true));
            });
        } else {
            this.levelEvent(null, id, pos, data);
        }
    }

    @Override
    public void levelEvent(@Nullable Player player, int type, BlockPos pos, int data) {
        this.server
            .getPlayerList()
            .broadcast(player, pos.getX(), pos.getY(), pos.getZ(), 64.0, this.dimension(), new ClientboundLevelEventPacket(type, pos, data, false)); // Paper - diff on change (the 64.0 distance is used as defaults for sound ranges in spigot config for ender dragon, end portal and wither)
    }

    public int getLogicalHeight() {
        return this.dimensionType().logicalHeight();
    }

    @Override
    public void gameEvent(Holder<GameEvent> gameEvent, Vec3 pos, GameEvent.Context context) {
        // Paper start - Prevent GameEvents being fired from unloaded chunks
        if (this.getChunkIfLoadedImmediately((Mth.floor(pos.x) >> 4), (Mth.floor(pos.z) >> 4)) == null) {
            return;
        }
        // Paper end - Prevent GameEvents being fired from unloaded chunks
        this.gameEventDispatcher.post(gameEvent, pos, context);
    }

    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        if (this.isUpdatingNavigations) {
            String string = "recursive call to sendBlockUpdated";
            Util.logAndPauseIfInIde("recursive call to sendBlockUpdated", new IllegalStateException("recursive call to sendBlockUpdated"));
        }

        this.getChunkSource().blockChanged(pos);
        this.pathTypesByPosCache.invalidate(pos);
        if (this.paperConfig().misc.updatePathfindingOnBlockUpdate) { // Paper - option to disable pathfinding updates
        VoxelShape collisionShape = oldState.getCollisionShape(this, pos);
        VoxelShape collisionShape1 = newState.getCollisionShape(this, pos);
        if (Shapes.joinIsNotEmpty(collisionShape, collisionShape1, BooleanOp.NOT_SAME)) {
            List<PathNavigation> list = new ObjectArrayList<>();

            try { // Paper - catch CME see below why
            for (Mob mob : this.navigatingMobs) {
                PathNavigation navigation = mob.getNavigation();
                if (navigation.shouldRecomputePath(pos)) {
                    list.add(navigation);
                }
            }
            // Paper start - catch CME see below why
            } catch (final java.util.ConcurrentModificationException concurrentModificationException) {
                // This can happen because the pathfinder update below may trigger a chunk load, which in turn may cause more navigators to register
                // In this case we just run the update again across all the iterators as the chunk will then be loaded
                // As this is a relative edge case it is much faster than copying navigators (on either read or write)
                this.sendBlockUpdated(pos, oldState, newState, flags);
                return;
            }
            // Paper end - catch CME see below why

            try {
                this.isUpdatingNavigations = true;

                for (PathNavigation pathNavigation : list) {
                    pathNavigation.recomputePath();
                }
            } finally {
                this.isUpdatingNavigations = false;
            }
        }
        } // Paper - option to disable pathfinding updates
    }

    @Override
    public void updateNeighborsAt(BlockPos pos, Block block) {
        if (captureBlockStates) { return; } // Paper - Cancel all physics during placement
        this.updateNeighborsAt(pos, block, ExperimentalRedstoneUtils.initialOrientation(this, null, null));
    }

    @Override
    public void updateNeighborsAt(BlockPos pos, Block block, @Nullable Orientation orientation) {
        if (captureBlockStates) { return; } // Paper - Cancel all physics during placement
        this.neighborUpdater.updateNeighborsAtExceptFromFacing(pos, block, null, orientation);
    }

    @Override
    public void updateNeighborsAtExceptFromFacing(BlockPos pos, Block block, Direction facing, @Nullable Orientation orientation) {
        this.neighborUpdater.updateNeighborsAtExceptFromFacing(pos, block, facing, orientation);
    }

    @Override
    public void neighborChanged(BlockPos pos, Block block, @Nullable Orientation orientation) {
        this.neighborUpdater.neighborChanged(pos, block, orientation);
    }

    @Override
    public void neighborChanged(BlockState state, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        this.neighborUpdater.neighborChanged(state, pos, block, orientation, movedByPiston);
    }

    @Override
    public void broadcastEntityEvent(Entity entity, byte state) {
        this.getChunkSource().broadcastAndSend(entity, new ClientboundEntityEventPacket(entity, state));
    }

    @Override
    public void broadcastDamageEvent(Entity entity, DamageSource damageSource) {
        this.getChunkSource().broadcastAndSend(entity, new ClientboundDamageEventPacket(entity, damageSource));
    }

    @Override
    public ServerChunkCache getChunkSource() {
        return this.chunkSource;
    }

    @Override
    public void explode(
        @Nullable Entity source,
        @Nullable DamageSource damageSource,
        @Nullable ExplosionDamageCalculator damageCalculator,
        double x,
        double y,
        double z,
        float radius,
        boolean fire,
        Level.ExplosionInteraction explosionInteraction,
        ParticleOptions smallExplosionParticles,
        ParticleOptions largeExplosionParticles,
        Holder<SoundEvent> explosionSound
    ) {
        // CraftBukkit start
        this.explode0(source, damageSource, damageCalculator, x, y, z, radius, fire, explosionInteraction, smallExplosionParticles, largeExplosionParticles, explosionSound);
    }

    public ServerExplosion explode0(
        @Nullable Entity source,
        @Nullable DamageSource damageSource,
        @Nullable ExplosionDamageCalculator damageCalculator,
        double x,
        double y,
        double z,
        float radius,
        boolean fire,
        Level.ExplosionInteraction explosionInteraction,
        ParticleOptions smallExplosionParticles,
        ParticleOptions largeExplosionParticles,
        Holder<SoundEvent> explosionSound
    ) {
        return this.explode0(source, damageSource, damageCalculator, x, y, z, radius, fire, explosionInteraction, smallExplosionParticles, largeExplosionParticles, explosionSound, null);
    }
    public ServerExplosion explode0(
        @Nullable Entity source,
        @Nullable DamageSource damageSource,
        @Nullable ExplosionDamageCalculator damageCalculator,
        double x,
        double y,
        double z,
        float radius,
        boolean fire,
        Level.ExplosionInteraction explosionInteraction,
        ParticleOptions smallExplosionParticles,
        ParticleOptions largeExplosionParticles,
        Holder<SoundEvent> explosionSound,
        @Nullable java.util.function.Consumer<ServerExplosion> configurator
    ) {
        // CraftBukkit end
        Explosion.BlockInteraction blockInteraction = switch (explosionInteraction) {
            case NONE -> Explosion.BlockInteraction.KEEP;
            case BLOCK -> this.getDestroyType(GameRules.RULE_BLOCK_EXPLOSION_DROP_DECAY);
            case MOB -> this.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                ? this.getDestroyType(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY)
                : Explosion.BlockInteraction.KEEP;
            case TNT -> this.getDestroyType(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY);
            case TRIGGER -> Explosion.BlockInteraction.TRIGGER_BLOCK;
            case STANDARD -> Explosion.BlockInteraction.DESTROY; // CraftBukkit - handle custom explosion type
        };
        Vec3 vec3 = new Vec3(x, y, z);
        ServerExplosion serverExplosion = new ServerExplosion(this, source, damageSource, damageCalculator, vec3, radius, fire, blockInteraction);
        if (configurator != null) configurator.accept(serverExplosion);// Paper - Allow explosions to damage source
        serverExplosion.explode();
        // CraftBukkit start
        if (serverExplosion.wasCanceled) {
            return serverExplosion;
        }
        // CraftBukkit end
        ParticleOptions particleOptions = serverExplosion.isSmall() ? smallExplosionParticles : largeExplosionParticles;

        for (ServerPlayer serverPlayer : this.players) {
            if (serverPlayer.distanceToSqr(vec3) < 4096.0) {
                Optional<Vec3> optional = Optional.ofNullable(serverExplosion.getHitPlayers().get(serverPlayer));
                serverPlayer.connection.send(new ClientboundExplodePacket(vec3, optional, particleOptions, explosionSound));
            }
        }

        return serverExplosion; // CraftBukkit
    }

    private Explosion.BlockInteraction getDestroyType(GameRules.Key<GameRules.BooleanValue> decayGameRule) {
        return this.getGameRules().getBoolean(decayGameRule) ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.DESTROY;
    }

    @Override
    public void blockEvent(BlockPos pos, Block block, int eventID, int eventParam) {
        this.blockEvents.add(new BlockEventData(pos, block, eventID, eventParam));
    }

    private void runBlockEvents() {
        this.blockEventsToReschedule.clear();

        while (!this.blockEvents.isEmpty()) {
            BlockEventData blockEventData = this.blockEvents.removeFirst();
            if (this.shouldTickBlocksAt(blockEventData.pos())) {
                if (this.doBlockEvent(blockEventData)) {
                    this.server
                        .getPlayerList()
                        .broadcast(
                            null,
                            blockEventData.pos().getX(),
                            blockEventData.pos().getY(),
                            blockEventData.pos().getZ(),
                            64.0,
                            this.dimension(),
                            new ClientboundBlockEventPacket(blockEventData.pos(), blockEventData.block(), blockEventData.paramA(), blockEventData.paramB())
                        );
                }
            } else {
                this.blockEventsToReschedule.add(blockEventData);
            }
        }

        this.blockEvents.addAll(this.blockEventsToReschedule);
    }

    private boolean doBlockEvent(BlockEventData event) {
        BlockState blockState = this.getBlockState(event.pos());
        return blockState.is(event.block()) && blockState.triggerEvent(this, event.pos(), event.paramA(), event.paramB());
    }

    @Override
    public LevelTicks<Block> getBlockTicks() {
        return this.blockTicks;
    }

    @Override
    public LevelTicks<Fluid> getFluidTicks() {
        return this.fluidTicks;
    }

    @Nonnull
    @Override
    public MinecraftServer getServer() {
        return this.server;
    }

    public PortalForcer getPortalForcer() {
        return this.portalForcer;
    }

    public StructureTemplateManager getStructureManager() {
        return this.server.getStructureManager();
    }

    public <T extends ParticleOptions> int sendParticles(
        T type, double posX, double posY, double posZ, int particleCount, double xOffset, double yOffset, double zOffset, double speed
    ) {
        return this.sendParticlesSource(null, type, false, false, posX, posY, posZ, particleCount, xOffset, yOffset, zOffset, speed); // CraftBukkit - visibility api support
    }

    public <T extends ParticleOptions> int sendParticles(
        T type,
        boolean overrideLimiter,
        boolean alwaysShow,
        double posX,
        double posY,
        double posZ,
        int particleCount,
        double xOffset,
        double yOffset,
        double zOffset,
        double speed
    ) {
    // CraftBukkit start - visibility api support
        return this.sendParticlesSource(null, type, overrideLimiter, alwaysShow, posX, posY, posZ, particleCount, xOffset, yOffset, zOffset, speed);
    }
    public <T extends ParticleOptions> int sendParticlesSource(
        @javax.annotation.Nullable ServerPlayer sender,
        T type,
        boolean overrideLimiter,
        boolean alwaysShow,
        double posX,
        double posY,
        double posZ,
        int particleCount,
        double xOffset,
        double yOffset,
        double zOffset,
        double speed
    ) {
        return sendParticlesSource(this.players, sender, type, overrideLimiter, alwaysShow, posX, posY, posZ, particleCount, xOffset, yOffset, zOffset, speed);
    }
    public <T extends ParticleOptions> int sendParticlesSource(
        List<ServerPlayer> receivers,
        @javax.annotation.Nullable ServerPlayer sender,
        T type,
        boolean overrideLimiter,
        boolean alwaysShow,
        double posX,
        double posY,
        double posZ,
        int particleCount,
        double xOffset,
        double yOffset,
        double zOffset,
        double speed
    ) {
    // CraftBukkit end - visibility api support
        ClientboundLevelParticlesPacket clientboundLevelParticlesPacket = new ClientboundLevelParticlesPacket(
            type, overrideLimiter, alwaysShow, posX, posY, posZ, (float)xOffset, (float)yOffset, (float)zOffset, (float)speed, particleCount
        );
        int i = 0;

        for (int i1 = 0; i1 < receivers.size(); i1++) { // Paper - particle API
            ServerPlayer serverPlayer = receivers.get(i1); // Paper - particle API
            if (sender != null && !serverPlayer.getBukkitEntity().canSee(sender.getBukkitEntity())) continue; // CraftBukkit
            if (this.sendParticles(serverPlayer, overrideLimiter, posX, posY, posZ, clientboundLevelParticlesPacket)) {
                i++;
            }
        }

        return i;
    }

    public <T extends ParticleOptions> boolean sendParticles(
        ServerPlayer player,
        T particle,
        boolean overrideLimiter,
        boolean alwaysShow,
        double posX,
        double posY,
        double posZ,
        int count,
        double xDist,
        double yDist,
        double zDist,
        double maxSpeed
    ) {
        Packet<?> packet = new ClientboundLevelParticlesPacket(
            particle, overrideLimiter, alwaysShow, posX, posY, posZ, (float)xDist, (float)yDist, (float)zDist, (float)maxSpeed, count
        );
        return this.sendParticles(player, overrideLimiter, posX, posY, posZ, packet);
    }

    private boolean sendParticles(ServerPlayer player, boolean longDistance, double posX, double posY, double posZ, Packet<?> packet) {
        if (player.level() != this) {
            return false;
        } else {
            BlockPos blockPos = player.blockPosition();
            if (blockPos.closerToCenterThan(new Vec3(posX, posY, posZ), longDistance ? 512.0 : 32.0)) {
                player.connection.send(packet);
                return true;
            } else {
                return false;
            }
        }
    }

    @Nullable
    @Override
    public Entity getEntity(int id) {
        return this.getEntities().get(id);
    }

    @Nullable
    public Entity getEntity(UUID uniqueId) {
        return this.getEntities().get(uniqueId);
    }

    @Deprecated
    @Nullable
    public Entity getEntityOrPart(int id) {
        Entity entity = this.getEntities().get(id);
        return entity != null ? entity : this.dragonParts.get(id);
    }

    @Override
    public Collection<EnderDragonPart> dragonParts() {
        return this.dragonParts.values();
    }

    @Nullable
    public BlockPos findNearestMapStructure(TagKey<Structure> structureTag, BlockPos pos, int radius, boolean skipExistingChunks) {
        if (!this.serverLevelData.worldGenOptions().generateStructures()) { // CraftBukkit
            return null;
        } else {
            Optional<HolderSet.Named<Structure>> optional = this.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(structureTag);
            if (optional.isEmpty()) {
                return null;
            } else {
                Pair<BlockPos, Holder<Structure>> pair = this.getChunkSource()
                    .getGenerator()
                    .findNearestMapStructure(this, optional.get(), pos, radius, skipExistingChunks);
                return pair != null ? pair.getFirst() : null;
            }
        }
    }

    @Nullable
    public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(
        Predicate<Holder<Biome>> biomePredicate, BlockPos pos, int radius, int horizontalStep, int verticalStep
    ) {
        return this.getChunkSource()
            .getGenerator()
            .getBiomeSource()
            .findClosestBiome3d(pos, radius, horizontalStep, verticalStep, biomePredicate, this.getChunkSource().randomState().sampler(), this);
    }

    @Override
    public RecipeManager recipeAccess() {
        return this.server.getRecipeManager();
    }

    @Override
    public TickRateManager tickRateManager() {
        return this.server.tickRateManager();
    }

    @Override
    public boolean noSave() {
        return this.noSave;
    }

    public DimensionDataStorage getDataStorage() {
        return this.getChunkSource().getDataStorage();
    }

    @Nullable
    @Override
    public MapItemSavedData getMapData(MapId mapId) {
        // Paper start - Call missing map initialize event and set id
        final DimensionDataStorage storage = this.getServer().overworld().getDataStorage();

        final Optional<net.minecraft.world.level.saveddata.SavedData> cacheEntry = storage.cache.get(mapId.key());
        if (cacheEntry == null) { // Cache did not contain, try to load and may init
            final MapItemSavedData mapData = storage.get(MapItemSavedData.factory(), mapId.key()); // get populates the cache
            if (mapData != null) { // map was read, init it and return
                mapData.id = mapId;
                new org.bukkit.event.server.MapInitializeEvent(mapData.mapView).callEvent();
                return mapData;
            }

            return null; // Map does not exist, reading failed.
        }

        // Cache entry exists, update it with the id ref and return.
        if (cacheEntry.orElse(null) instanceof final MapItemSavedData mapItemSavedData) {
            mapItemSavedData.id = mapId;
            return mapItemSavedData;
        }

        return null;
        // Paper end - Call missing map initialize event and set id
    }

    @Override
    public void setMapData(MapId mapId, MapItemSavedData mapData) {
        // CraftBukkit start
        mapData.id = mapId;
        org.bukkit.event.server.MapInitializeEvent event = new org.bukkit.event.server.MapInitializeEvent(mapData.mapView);
        event.callEvent();
        // CraftBukkit end
        this.getServer().overworld().getDataStorage().set(mapId.key(), mapData);
    }

    @Override
    public MapId getFreeMapId() {
        return this.getServer().overworld().getDataStorage().computeIfAbsent(MapIndex.factory(), "idcounts").getFreeAuxValueForMap();
    }

    public void setDefaultSpawnPos(BlockPos pos, float angle) {
        BlockPos spawnPos = this.levelData.getSpawnPos();
        float spawnAngle = this.levelData.getSpawnAngle();
        if (!spawnPos.equals(pos) || spawnAngle != angle) {
            org.bukkit.Location prevSpawnLoc = this.getWorld().getSpawnLocation(); // Paper - Call SpawnChangeEvent
            this.levelData.setSpawn(pos, angle);
            new org.bukkit.event.world.SpawnChangeEvent(this.getWorld(), prevSpawnLoc).callEvent(); // Paper - Call SpawnChangeEvent
            this.getServer().getPlayerList().broadcastAll(new ClientboundSetDefaultSpawnPositionPacket(pos, angle));
        }

        if (this.lastSpawnChunkRadius > 1) {
            // Paper start - allow disabling gamerule limits
            for (ChunkPos chunkPos : io.papermc.paper.util.MCUtil.getSpiralOutChunks(spawnPos, this.lastSpawnChunkRadius - 2)) {
                this.getChunkSource().removeTicketAtLevel(TicketType.START, chunkPos, net.minecraft.server.level.ChunkLevel.ENTITY_TICKING_LEVEL, Unit.INSTANCE);
            }
            // Paper end - allow disabling gamerule limits
        }

        int i = this.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS) + 1;
        if (i > 1) {
            // Paper start - allow disabling gamerule limits
            for (ChunkPos chunkPos : io.papermc.paper.util.MCUtil.getSpiralOutChunks(pos, i - 2)) {
                this.getChunkSource().addTicketAtLevel(TicketType.START, chunkPos, net.minecraft.server.level.ChunkLevel.ENTITY_TICKING_LEVEL, Unit.INSTANCE);
            }
            // Paper end - allow disabling gamerule limits
        }

        this.lastSpawnChunkRadius = i;
    }

    public LongSet getForcedChunks() {
        ForcedChunksSavedData forcedChunksSavedData = this.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");
        return (LongSet)(forcedChunksSavedData != null ? LongSets.unmodifiable(forcedChunksSavedData.getChunks()) : LongSets.EMPTY_SET);
    }

    public boolean setChunkForced(int chunkX, int chunkZ, boolean add) {
        ForcedChunksSavedData forcedChunksSavedData = this.getDataStorage().computeIfAbsent(ForcedChunksSavedData.factory(), "chunks");
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        long packedChunkPos = chunkPos.toLong();
        boolean flag;
        if (add) {
            flag = forcedChunksSavedData.getChunks().add(packedChunkPos);
            if (flag) {
                this.getChunk(chunkX, chunkZ);
            }
        } else {
            flag = forcedChunksSavedData.getChunks().remove(packedChunkPos);
        }

        forcedChunksSavedData.setDirty(flag);
        if (flag) {
            this.getChunkSource().updateChunkForced(chunkPos, add);
        }

        return flag;
    }

    @Override
    public List<ServerPlayer> players() {
        return this.players;
    }

    @Override
    public void onBlockStateChange(BlockPos pos, BlockState blockState, BlockState newState) {
        Optional<Holder<PoiType>> optional = PoiTypes.forState(blockState);
        Optional<Holder<PoiType>> optional1 = PoiTypes.forState(newState);
        if (!Objects.equals(optional, optional1)) {
            BlockPos blockPos = pos.immutable();
            optional.ifPresent(poiType -> this.getServer().execute(() -> {
                this.getPoiManager().remove(blockPos);
                DebugPackets.sendPoiRemovedPacket(this, blockPos);
            }));
            optional1.ifPresent(poiType -> this.getServer().execute(() -> {
                // Paper start - Remove stale POIs
                if (optional.isEmpty() && this.getPoiManager().exists(blockPos, ignored -> true)) {
                    this.getPoiManager().remove(blockPos);
                }
                // Paper end - Remove stale POIs
                this.getPoiManager().add(blockPos, (Holder<PoiType>)poiType);
                DebugPackets.sendPoiAddedPacket(this, blockPos);
            }));
        }
    }

    public PoiManager getPoiManager() {
        return this.getChunkSource().getPoiManager();
    }

    public boolean isVillage(BlockPos pos) {
        return this.isCloseToVillage(pos, 1);
    }

    public boolean isVillage(SectionPos pos) {
        return this.isVillage(pos.center());
    }

    public boolean isCloseToVillage(BlockPos pos, int sections) {
        return sections <= 6 && this.sectionsToVillage(SectionPos.of(pos)) <= sections;
    }

    public int sectionsToVillage(SectionPos pos) {
        return this.getPoiManager().sectionsToVillage(pos);
    }

    public Raids getRaids() {
        return this.raids;
    }

    @Nullable
    public Raid getRaidAt(BlockPos pos) {
        return this.raids.getNearbyRaid(pos, 9216);
    }

    public boolean isRaided(BlockPos pos) {
        return this.getRaidAt(pos) != null;
    }

    public void onReputationEvent(ReputationEventType type, Entity target, ReputationEventHandler host) {
        host.onReputationEventFrom(type, target);
    }

    public void saveDebugReport(Path path) throws IOException {
        ChunkMap chunkMap = this.getChunkSource().chunkMap;

        try (Writer bufferedWriter = Files.newBufferedWriter(path.resolve("stats.txt"))) {
            bufferedWriter.write(String.format(Locale.ROOT, "spawning_chunks: %d\n", chunkMap.getDistanceManager().getNaturalSpawnChunkCount()));
            NaturalSpawner.SpawnState lastSpawnState = this.getChunkSource().getLastSpawnState();
            if (lastSpawnState != null) {
                for (Entry<MobCategory> entry : lastSpawnState.getMobCategoryCounts().object2IntEntrySet()) {
                    bufferedWriter.write(String.format(Locale.ROOT, "spawn_count.%s: %d\n", entry.getKey().getName(), entry.getIntValue()));
                }
            }

            bufferedWriter.write(String.format(Locale.ROOT, "entities: %s\n", this.moonrise$getEntityLookup().getDebugInfo()));  // Paper - rewrite chunk system
            bufferedWriter.write(String.format(Locale.ROOT, "block_entity_tickers: %d\n", this.blockEntityTickers.size()));
            bufferedWriter.write(String.format(Locale.ROOT, "block_ticks: %d\n", this.getBlockTicks().count()));
            bufferedWriter.write(String.format(Locale.ROOT, "fluid_ticks: %d\n", this.getFluidTicks().count()));
            bufferedWriter.write("distance_manager: " + chunkMap.getDistanceManager().getDebugStatus() + "\n");
            bufferedWriter.write(String.format(Locale.ROOT, "pending_tasks: %d\n", this.getChunkSource().getPendingTasksCount()));
        }

        CrashReport crashReport = new CrashReport("Level dump", new Exception("dummy"));
        this.fillReportDetails(crashReport);

        try (Writer bufferedWriter1 = Files.newBufferedWriter(path.resolve("example_crash.txt"))) {
            bufferedWriter1.write(crashReport.getFriendlyReport(ReportType.TEST));
        }

        Path path1 = path.resolve("chunks.csv");

        try (Writer bufferedWriter2 = Files.newBufferedWriter(path1)) {
            //chunkMap.dumpChunks(bufferedWriter2); // Paper - rewrite chunk system
        }

        Path path2 = path.resolve("entity_chunks.csv");

        try (Writer bufferedWriter3 = Files.newBufferedWriter(path2)) {
            //this.entityManager.dumpSections(bufferedWriter3); // Paper - rewrite chunk system
        }

        Path path3 = path.resolve("entities.csv");

        try (Writer bufferedWriter4 = Files.newBufferedWriter(path3)) {
            dumpEntities(bufferedWriter4, this.getEntities().getAll());
        }

        Path path4 = path.resolve("block_entities.csv");

        try (Writer bufferedWriter5 = Files.newBufferedWriter(path4)) {
            this.dumpBlockEntityTickers(bufferedWriter5);
        }
    }

    private static void dumpEntities(Writer writer, Iterable<Entity> entities) throws IOException {
        CsvOutput csvOutput = CsvOutput.builder()
            .addColumn("x")
            .addColumn("y")
            .addColumn("z")
            .addColumn("uuid")
            .addColumn("type")
            .addColumn("alive")
            .addColumn("display_name")
            .addColumn("custom_name")
            .build(writer);

        for (Entity entity : entities) {
            Component customName = entity.getCustomName();
            Component displayName = entity.getDisplayName();
            csvOutput.writeRow(
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                entity.getUUID(),
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                entity.isAlive(),
                displayName.getString(),
                customName != null ? customName.getString() : null
            );
        }
    }

    private void dumpBlockEntityTickers(Writer output) throws IOException {
        CsvOutput csvOutput = CsvOutput.builder().addColumn("x").addColumn("y").addColumn("z").addColumn("type").build(output);

        for (TickingBlockEntity tickingBlockEntity : this.blockEntityTickers) {
            BlockPos pos = tickingBlockEntity.getPos();
            csvOutput.writeRow(pos.getX(), pos.getY(), pos.getZ(), tickingBlockEntity.getType());
        }
    }

    @VisibleForTesting
    public void clearBlockEvents(BoundingBox boundingBox) {
        this.blockEvents.removeIf(blockEventData -> boundingBox.isInside(blockEventData.pos()));
    }

    @Override
    public void blockUpdated(BlockPos pos, Block block) {
        if (!this.isDebug()) {
            // CraftBukkit start
            if (this.populating) {
                return;
            }
            // CraftBukkit end
            this.updateNeighborsAt(pos, block);
        }
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return 1.0F;
    }

    public Iterable<Entity> getAllEntities() {
        return this.getEntities().getAll();
    }

    @Override
    public String toString() {
        return "ServerLevel[" + this.serverLevelData.getLevelName() + "]";
    }

    public boolean isFlat() {
        return this.serverLevelData.isFlatWorld(); // CraftBukkit
    }

    @Override
    public long getSeed() {
        return this.serverLevelData.worldGenOptions().seed(); // CraftBukkit
    }

    @Nullable
    public EndDragonFight getDragonFight() {
        return this.dragonFight;
    }

    @Override
    public ServerLevel getLevel() {
        return this;
    }

    @VisibleForTesting
    public String getWatchdogStats() {
        return String.format(
            Locale.ROOT,
            "players: %s, entities: %s [%s], block_entities: %d [%s], block_ticks: %d, fluid_ticks: %d, chunk_source: %s",
            this.players.size(),
            this.moonrise$getEntityLookup().getDebugInfo(), // Paper - rewrite chunk system
            getTypeCount(this.moonrise$getEntityLookup().getAll(), entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()), // Paper - rewrite chunk system
            this.blockEntityTickers.size(),
            getTypeCount(this.blockEntityTickers, TickingBlockEntity::getType),
            this.getBlockTicks().count(),
            this.getFluidTicks().count(),
            this.gatherChunkSourceStats()
        );
    }

    private static <T> String getTypeCount(Iterable<T> objects, Function<T, String> typeGetter) {
        try {
            Object2IntOpenHashMap<String> map = new Object2IntOpenHashMap<>();

            for (T object : objects) {
                String string = typeGetter.apply(object);
                map.addTo(string, 1);
            }

            return map.object2IntEntrySet()
                .stream()
                .sorted(Comparator.<Entry<String>, Integer>comparing(Entry::getIntValue).reversed())
                .limit(5L)
                .map(entry -> entry.getKey() + ":" + entry.getIntValue())
                .collect(Collectors.joining(","));
        } catch (Exception var6) {
            return "";
        }
    }

    @Override
    public LevelEntityGetter<Entity> getEntities() {
        org.spigotmc.AsyncCatcher.catchOp("Chunk getEntities call"); // Spigot
        return this.moonrise$getEntityLookup(); // Paper - rewrite chunk system
    }

    public void addLegacyChunkEntities(Stream<Entity> entities) {
        // Paper start - add chunkpos param
        this.addLegacyChunkEntities(entities, null);
    }
    public void addLegacyChunkEntities(Stream<Entity> entities, ChunkPos chunkPos) {
        // Paper end - add chunkpos param
        this.moonrise$getEntityLookup().addLegacyChunkEntities(entities.toList(), chunkPos); // Paper - rewrite chunk system
    }

    public void addWorldGenChunkEntities(Stream<Entity> entities) {
        // Paper start - add chunkpos param
        this.addWorldGenChunkEntities(entities, null);
    }
    public void addWorldGenChunkEntities(Stream<Entity> entities, ChunkPos chunkPos) {
        // Paper end - add chunkpos param
        this.moonrise$getEntityLookup().addWorldGenChunkEntities(entities.toList(), chunkPos); // Paper - rewrite chunk system
    }

    public void startTickingChunk(LevelChunk chunk) {
        chunk.unpackTicks(this.getLevelData().getGameTime());
    }

    public void onStructureStartsAvailable(ChunkAccess chunk) {
        this.server.execute(() -> this.structureCheck.onStructureLoad(chunk.getPos(), chunk.getAllStarts()));
    }

    public PathTypeCache getPathTypeCache() {
        return this.pathTypesByPosCache;
    }

    @Override
    public void close() throws IOException {
        super.close();
        // Paper - rewrite chunk system
    }

    @Override
    public String gatherChunkSourceStats() {
        return "Chunks[S] W: " + this.chunkSource.gatherStats() + " E: " + this.moonrise$getEntityLookup().getDebugInfo(); // Paper - rewrite chunk system
    }

    public boolean areEntitiesLoaded(long chunkPos) {
        return this.moonrise$getAnyChunkIfLoaded(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkX(chunkPos), ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkZ(chunkPos)) != null; // Paper - rewrite chunk system
    }

    private boolean isPositionTickingWithEntitiesLoaded(long chunkPos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkPos);
        // isTicking implies the chunk is loaded, and the chunk is loaded now implies the entities are loaded
        return chunkHolder != null && chunkHolder.isTickingReady();
        // Paper end - rewrite chunk system
    }

    public boolean isPositionEntityTicking(BlockPos pos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(pos));
        return chunkHolder != null && chunkHolder.isEntityTickingReady();
        // Paper end - rewrite chunk system
    }

    public boolean isNaturalSpawningAllowed(BlockPos pos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(pos));
        return chunkHolder != null && chunkHolder.isEntityTickingReady();
        // Paper end - rewrite chunk system
    }

    public boolean isNaturalSpawningAllowed(ChunkPos chunkPos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkPos));
        return chunkHolder != null && chunkHolder.isEntityTickingReady();
        // Paper end - rewrite chunk system
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.server.getWorldData().enabledFeatures();
    }

    @Override
    public PotionBrewing potionBrewing() {
        return this.server.potionBrewing();
    }

    @Override
    public FuelValues fuelValues() {
        return this.server.fuelValues();
    }

    public RandomSource getRandomSequence(ResourceLocation location) {
        return this.randomSequences.get(location);
    }

    public RandomSequences getRandomSequences() {
        return this.randomSequences;
    }

    public GameRules getGameRules() {
        return this.serverLevelData.getGameRules();
    }

    // Paper start - respect global sound events gamerule
    public List<net.minecraft.server.level.ServerPlayer> getPlayersForGlobalSoundGamerule() {
        return this.getGameRules().getBoolean(GameRules.RULE_GLOBAL_SOUND_EVENTS) ? ((ServerLevel) this).getServer().getPlayerList().players : ((ServerLevel) this).players();
    }

    public double getGlobalSoundRangeSquared(java.util.function.Function<org.spigotmc.SpigotWorldConfig, Integer> rangeFunction) {
        final double range = rangeFunction.apply(this.spigotConfig);
        return range <= 0 ? 64.0 * 64.0 : range * range; // 64 is taken from default in ServerLevel#levelEvent
    }
    // Paper end - respect global sound events gamerule
    // Paper start - notify observers even if grow failed
    public void checkCapturedTreeStateForObserverNotify(final BlockPos pos, final org.bukkit.craftbukkit.block.CraftBlockState craftBlockState) {
        // notify observers if the block state is the same and the Y level equals the original y level (for mega trees)
        // blocks at the same Y level with the same state can be assumed to be saplings which trigger observers regardless of if the
        // tree grew or not
        if (craftBlockState.getPosition().getY() == pos.getY() && this.getBlockState(craftBlockState.getPosition()) == craftBlockState.getHandle()) {
            this.notifyAndUpdatePhysics(craftBlockState.getPosition(), null, craftBlockState.getHandle(), craftBlockState.getHandle(), craftBlockState.getHandle(), craftBlockState.getFlag(), 512);
        }
    }
    // Paper end - notify observers even if grow failed

    @Override
    public CrashReportCategory fillReportDetails(CrashReport report) {
        CrashReportCategory crashReportCategory = super.fillReportDetails(report);
        crashReportCategory.setDetail("Loaded entity count", () -> String.valueOf(this.moonrise$getEntityLookup().getEntityCount())); // Paper - rewrite chunk system
        return crashReportCategory;
    }

    @Override
    public int getSeaLevel() {
        return this.chunkSource.getGenerator().getSeaLevel();
    }

    // Paper start - optimize redstone (Alternate Current)
    @Override
    public alternate.current.wire.WireHandler getWireHandler() {
        return wireHandler;
    }
    // Paper end - optimize redstone (Alternate Current)

    final class EntityCallbacks implements LevelCallback<Entity> {
        @Override
        public void onCreated(Entity entity) {
        }

        @Override
        public void onDestroyed(Entity entity) {
            ServerLevel.this.getScoreboard().entityRemoved(entity);
        }

        @Override
        public void onTickingStart(Entity entity) {
            if (entity instanceof net.minecraft.world.entity.Marker && !paperConfig().entities.markers.tick) return; // Paper - Configurable marker ticking
            ServerLevel.this.entityTickList.add(entity);
        }

        @Override
        public void onTickingEnd(Entity entity) {
            ServerLevel.this.entityTickList.remove(entity);
            // Paper start - Reset pearls when they stop being ticked
            if (ServerLevel.this.paperConfig().fixes.disableUnloadedChunkEnderpearlExploit && ServerLevel.this.paperConfig().misc.legacyEnderPearlBehavior && entity instanceof net.minecraft.world.entity.projectile.ThrownEnderpearl pearl) {
                pearl.cachedOwner = null;
                pearl.ownerUUID = null;
            }
            // Paper end - Reset pearls when they stop being ticked
        }

        @Override
        public void onTrackingStart(Entity entity) {
            org.spigotmc.AsyncCatcher.catchOp("entity register"); // Spigot
            // ServerLevel.this.getChunkSource().addEntity(entity); // Paper - ignore and warn about illegal addEntity calls instead of crashing server; moved down below valid=true
            if (entity instanceof ServerPlayer serverPlayer) {
                ServerLevel.this.players.add(serverPlayer);
                ServerLevel.this.updateSleepingPlayerList();
            }

            if (entity instanceof Mob mob) {
                if (false && ServerLevel.this.isUpdatingNavigations) { // Paper - Remove unnecessary onTrackingStart during navigation warning
                    String string = "onTrackingStart called during navigation iteration";
                    Util.logAndPauseIfInIde(
                        "onTrackingStart called during navigation iteration", new IllegalStateException("onTrackingStart called during navigation iteration")
                    );
                }

                ServerLevel.this.navigatingMobs.add(mob);
            }

            if (entity instanceof EnderDragon enderDragon) {
                for (EnderDragonPart enderDragonPart : enderDragon.getSubEntities()) {
                    ServerLevel.this.dragonParts.put(enderDragonPart.getId(), enderDragonPart);
                }
            }

            entity.updateDynamicGameEventListener(DynamicGameEventListener::add);
            entity.inWorld = true; // CraftBukkit - Mark entity as in world
            entity.valid = true; // CraftBukkit
            ServerLevel.this.getChunkSource().addEntity(entity); // Paper - ignore and warn about illegal addEntity calls instead of crashing server
            // Paper start - Entity origin API
            if (entity.getOriginVector() == null) {
                entity.setOrigin(entity.getBukkitEntity().getLocation());
            }
            // Default to current world if unknown, gross assumption but entities rarely change world
            if (entity.getOriginWorld() == null) {
                entity.setOrigin(entity.getOriginVector().toLocation(getWorld()));
            }
            // Paper end - Entity origin API
            new com.destroystokyo.paper.event.entity.EntityAddToWorldEvent(entity.getBukkitEntity(), ServerLevel.this.getWorld()).callEvent(); // Paper - fire while valid
        }

        @Override
        public void onTrackingEnd(Entity entity) {
            org.spigotmc.AsyncCatcher.catchOp("entity unregister"); // Spigot
            // Spigot start // TODO I don't think this is needed anymore
            if (entity instanceof Player player) {
                for (final ServerLevel level : ServerLevel.this.getServer().getAllLevels()) {
                    for (final Optional<net.minecraft.world.level.saveddata.SavedData> savedData : level.getDataStorage().cache.values()) {
                        if (savedData.isEmpty() || !(savedData.get() instanceof MapItemSavedData map)) {
                            continue;
                        }

                        map.carriedByPlayers.remove(player);
                        if (map.carriedBy.removeIf(holdingPlayer -> holdingPlayer.player == player)) {
                            map.decorations.remove(player.getName().getString());
                        }
                    }
                }
            }
            // Spigot end
            // Spigot start
            if (entity.getBukkitEntity() instanceof org.bukkit.inventory.InventoryHolder && (!(entity instanceof ServerPlayer) || entity.getRemovalReason() != Entity.RemovalReason.KILLED)) { // SPIGOT-6876: closeInventory clears death message
                // Paper start - Fix merchant inventory not closing on entity removal
                if (!entity.level().purpurConfig.playerVoidTrading && entity.getBukkitEntity() instanceof org.bukkit.inventory.Merchant merchant && merchant.getTrader() != null) { // Purpur - Allow void trading
                    merchant.getTrader().closeInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason.UNLOADED);
                }
                // Paper end - Fix merchant inventory not closing on entity removal
                for (org.bukkit.entity.HumanEntity h : Lists.newArrayList(((org.bukkit.inventory.InventoryHolder) entity.getBukkitEntity()).getInventory().getViewers())) {
                    h.closeInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason.UNLOADED); // Paper - Inventory close reason
                }
            }
            // Spigot end
            ServerLevel.this.getChunkSource().removeEntity(entity);
            if (entity instanceof ServerPlayer serverPlayer) {
                ServerLevel.this.players.remove(serverPlayer);
                ServerLevel.this.updateSleepingPlayerList();
            }

            if (entity instanceof Mob mob) {
                if (false && ServerLevel.this.isUpdatingNavigations) { // Paper - Remove unnecessary onTrackingStart during navigation warning
                    String string = "onTrackingStart called during navigation iteration";
                    Util.logAndPauseIfInIde(
                        "onTrackingStart called during navigation iteration", new IllegalStateException("onTrackingStart called during navigation iteration")
                    );
                }

                ServerLevel.this.navigatingMobs.remove(mob);
            }

            if (entity instanceof EnderDragon enderDragon) {
                for (EnderDragonPart enderDragonPart : enderDragon.getSubEntities()) {
                    ServerLevel.this.dragonParts.remove(enderDragonPart.getId());
                }
            }

            entity.updateDynamicGameEventListener(DynamicGameEventListener::remove);
            // CraftBukkit start
            entity.valid = false;
            if (!(entity instanceof ServerPlayer)) {
                for (ServerPlayer player : ServerLevel.this.server.getPlayerList().players) { // Paper - call onEntityRemove for all online players
                    player.getBukkitEntity().onEntityRemove(entity);
                }
            }
            // CraftBukkit end
            new com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent(entity.getBukkitEntity(), ServerLevel.this.getWorld()).callEvent(); // Paper - fire while valid
        }

        @Override
        public void onSectionChange(Entity entity) {
            entity.updateDynamicGameEventListener(DynamicGameEventListener::move);
        }
    }

    // Paper start - check global player list where appropriate
    @Override
    @Nullable
    public Player getGlobalPlayerByUUID(UUID uuid) {
        return this.server.getPlayerList().getPlayer(uuid);
    }
    // Paper end - check global player list where appropriate

    // Paper start - lag compensation
    private long lagCompensationTick = MinecraftServer.SERVER_INIT;

    public long getLagCompensationTick() {
        return this.lagCompensationTick;
    }

    public void updateLagCompensationTick() {
        this.lagCompensationTick = (System.nanoTime() - MinecraftServer.SERVER_INIT) / (java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(50L));
    }
    // Paper end - lag compensation
}
