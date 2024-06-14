package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;

public class ServerChunkCache extends ChunkSource implements ca.spottedleaf.moonrise.patches.chunk_system.world.ChunkSystemServerChunkCache { // Paper - rewrite chunk system
    private static final Logger LOGGER = LogUtils.getLogger();
    private final DistanceManager distanceManager;
    private final ServerLevel level;
    public final Thread mainThread;
    final ThreadedLevelLightEngine lightEngine;
    public final ServerChunkCache.MainThreadExecutor mainThreadProcessor;
    public final ChunkMap chunkMap;
    private final DimensionDataStorage dataStorage;
    private long lastInhabitedUpdate;
    public boolean spawnEnemies = true;
    public boolean spawnFriendlies = true;
    private static final int CACHE_SIZE = 4;
    private final long[] lastChunkPos = new long[4];
    private final ChunkStatus[] lastChunkStatus = new ChunkStatus[4];
    private final ChunkAccess[] lastChunk = new ChunkAccess[4];
    private final List<LevelChunk> tickingChunks = new ArrayList<>();
    private final Set<ChunkHolder> chunkHoldersToBroadcast = new ReferenceOpenHashSet<>();
    @Nullable
    @VisibleForDebug
    private NaturalSpawner.SpawnState lastSpawnState;
    // Paper start
    private final ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable<net.minecraft.world.level.chunk.LevelChunk> fullChunks = new ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable<>();
    public int getFullChunksCount() {
        return this.fullChunks.size();
    }
    long chunkFutureAwaitCounter;
    // Paper end
    // Paper start - rewrite chunk system

    @Override
    public final void moonrise$setFullChunk(final int chunkX, final int chunkZ, final LevelChunk chunk) {
        final long key = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkX, chunkZ);
        if (chunk == null) {
            this.fullChunks.remove(key);
        } else {
            this.fullChunks.put(key, chunk);
        }
    }

    @Override
    public final LevelChunk moonrise$getFullChunkIfLoaded(final int chunkX, final int chunkZ) {
        return this.fullChunks.get(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkX, chunkZ));
    }

    private ChunkAccess syncLoad(final int chunkX, final int chunkZ, final ChunkStatus toStatus) {
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler chunkTaskScheduler = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler();
        final CompletableFuture<ChunkAccess> completable = new CompletableFuture<>();
        chunkTaskScheduler.scheduleChunkLoad(
            chunkX, chunkZ, toStatus, true, ca.spottedleaf.concurrentutil.util.Priority.BLOCKING,
            completable::complete
        );

        if (!completable.isDone() && chunkTaskScheduler.hasShutdown()) {
            throw new IllegalStateException(
                "Chunk system has shut down, cannot process chunk requests in world '" + ca.spottedleaf.moonrise.common.util.WorldUtil.getWorldName(this.level) + "' at "
                    + "(" + chunkX + "," + chunkZ + ") status: " + toStatus
            );
        }

        if (ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this.level, chunkX, chunkZ)) {
            ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.pushChunkWait(this.level, chunkX, chunkZ);
            this.mainThreadProcessor.managedBlock(completable::isDone);
            ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.popChunkWait();
        }

        final ChunkAccess ret = completable.join();
        if (ret == null) {
            throw new IllegalStateException("Chunk not loaded when requested");
        }

        return ret;
    }

    private ChunkAccess getChunkFallback(final int chunkX, final int chunkZ, final ChunkStatus toStatus,
                                         final boolean load) {
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler chunkTaskScheduler = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler();
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager chunkHolderManager = chunkTaskScheduler.chunkHolderManager;

        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder currentChunk = chunkHolderManager.getChunkHolder(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkX, chunkZ));

        final ChunkAccess ifPresent = currentChunk == null ? null : currentChunk.getChunkIfPresent(toStatus);

        if (ifPresent != null && (toStatus != ChunkStatus.FULL || currentChunk.isFullChunkReady())) {
            return ifPresent;
        }

        final ca.spottedleaf.moonrise.common.PlatformHooks platformHooks = ca.spottedleaf.moonrise.common.PlatformHooks.get();

        if (platformHooks.hasCurrentlyLoadingChunk() && currentChunk != null) {
            final ChunkAccess loading = platformHooks.getCurrentlyLoadingChunk(currentChunk.vanillaChunkHolder);
            if (loading != null && ca.spottedleaf.moonrise.common.util.TickThread.isTickThread()) {
                return loading;
            }
        }

        return load ? this.syncLoad(chunkX, chunkZ, toStatus) : null;
    }
    // Paper end - rewrite chunk system
    // Paper start - chunk tick iteration optimisations
    private final ca.spottedleaf.moonrise.common.util.SimpleThreadUnsafeRandom shuffleRandom = new ca.spottedleaf.moonrise.common.util.SimpleThreadUnsafeRandom(0L);
    private boolean isChunkNearPlayer(final ChunkMap chunkMap, final ChunkPos chunkPos, final LevelChunk levelChunk) {
        final ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData chunkData = ((ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkHolder)((ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemLevelChunk)levelChunk).moonrise$getChunkAndHolder().holder())
            .moonrise$getRealChunkHolder().holderData;
        final ca.spottedleaf.moonrise.common.misc.NearbyPlayers.TrackedChunk nearbyPlayers = chunkData.nearbyPlayers;
        if (nearbyPlayers == null) {
            return false;
        }

        final ca.spottedleaf.moonrise.common.list.ReferenceList<ServerPlayer> players = nearbyPlayers.getPlayers(ca.spottedleaf.moonrise.common.misc.NearbyPlayers.NearbyMapType.SPAWN_RANGE);

        if (players == null) {
            return false;
        }

        final ServerPlayer[] raw = players.getRawDataUnchecked();
        final int len = players.size();

        java.util.Objects.checkFromIndexSize(0, len, raw.length);
        for (int i = 0; i < len; ++i) {
            if (chunkMap.playerIsCloseEnoughForSpawning(raw[i], chunkPos, 16384.0D)) { // Spigot (reducedRange = false)
                return true;
            }
        }

        return false;
    }
    // Paper end - chunk tick iteration optimisations


    public ServerChunkCache(
        ServerLevel level,
        LevelStorageSource.LevelStorageAccess levelStorageAccess,
        DataFixer fixerUpper,
        StructureTemplateManager structureManager,
        Executor dispatcher,
        ChunkGenerator generator,
        int viewDistance,
        int simulationDistance,
        boolean sync,
        ChunkProgressListener progressListener,
        ChunkStatusUpdateListener chunkStatusListener,
        Supplier<DimensionDataStorage> overworldDataStorage
    ) {
        this.level = level;
        this.mainThreadProcessor = new ServerChunkCache.MainThreadExecutor(level);
        this.mainThread = Thread.currentThread();
        Path path = levelStorageAccess.getDimensionPath(level.dimension()).resolve("data");

        try {
            FileUtil.createDirectoriesSafe(path);
        } catch (IOException var15) {
            LOGGER.error("Failed to create dimension data storage directory", (Throwable)var15);
        }

        this.dataStorage = new DimensionDataStorage(path, fixerUpper, level.registryAccess());
        this.chunkMap = new ChunkMap(
            level,
            levelStorageAccess,
            fixerUpper,
            structureManager,
            dispatcher,
            this.mainThreadProcessor,
            this,
            generator,
            progressListener,
            chunkStatusListener,
            overworldDataStorage,
            viewDistance,
            sync
        );
        this.lightEngine = this.chunkMap.getLightEngine();
        this.distanceManager = this.chunkMap.getDistanceManager();
        this.distanceManager.updateSimulationDistance(simulationDistance);
        this.clearCache();
    }

    // CraftBukkit start - properly implement isChunkLoaded
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        ChunkHolder chunk = this.chunkMap.getUpdatingChunkIfPresent(ChunkPos.asLong(chunkX, chunkZ));
        if (chunk == null) {
            return false;
        }
        return chunk.getFullChunkNow() != null;
    }
    // CraftBukkit end
    // Paper start
    // Paper - rewrite chunk system

    @Nullable
    public ChunkAccess getChunkAtImmediately(int x, int z) {
        ChunkHolder holder = this.chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
        if (holder == null) {
            return null;
        }

        return holder.getLatestChunk();
    }

    public <T> void addTicketAtLevel(TicketType<T> ticketType, ChunkPos chunkPos, int ticketLevel, T identifier) {
        this.distanceManager.addTicket(ticketType, chunkPos, ticketLevel, identifier);
    }

    public <T> void removeTicketAtLevel(TicketType<T> ticketType, ChunkPos chunkPos, int ticketLevel, T identifier) {
        this.distanceManager.removeTicket(ticketType, chunkPos, ticketLevel, identifier);
    }

    // "real" get chunk if loaded
    // Note: Partially copied from the getChunkAt method below
    @Nullable
    public LevelChunk getChunkAtIfCachedImmediately(int x, int z) {
        long k = ChunkPos.asLong(x, z);

        // Note: Bypass cache since we need to check ticket level, and to make this MT-Safe

        ChunkHolder playerChunk = this.getVisibleChunkIfPresent(k);
        if (playerChunk == null) {
            return null;
        }

        return playerChunk.getFullChunkNowUnchecked();
    }

    @Nullable
    public LevelChunk getChunkAtIfLoadedImmediately(int x, int z) {
        return this.fullChunks.get(ChunkPos.asLong(x, z));
    }
    // Paper end

    @Override
    public ThreadedLevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    @Nullable
    private ChunkHolder getVisibleChunkIfPresent(long chunkPos) {
        return this.chunkMap.getVisibleChunkIfPresent(chunkPos);
    }

    public int getTickingGenerated() {
        return this.chunkMap.getTickingGenerated();
    }

    private void storeInCache(long chunkPos, @Nullable ChunkAccess chunk, ChunkStatus chunkStatus) {
        for (int i = 3; i > 0; i--) {
            this.lastChunkPos[i] = this.lastChunkPos[i - 1];
            this.lastChunkStatus[i] = this.lastChunkStatus[i - 1];
            this.lastChunk[i] = this.lastChunk[i - 1];
        }

        this.lastChunkPos[0] = chunkPos;
        this.lastChunkStatus[0] = chunkStatus;
        this.lastChunk[0] = chunk;
    }

    @Nullable
    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus chunkStatus, boolean requireChunk) {
        // Paper start - rewrite chunk system
        if (chunkStatus == ChunkStatus.FULL) {
            final LevelChunk ret = this.fullChunks.get(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(x, z));

            if (ret != null) {
                return ret;
            }

            return requireChunk ? this.getChunkFallback(x, z, chunkStatus, requireChunk) : null;
        }

        return this.getChunkFallback(x, z, chunkStatus, requireChunk);
        // Paper end - rewrite chunk system
    }

    @Nullable
    @Override
    public LevelChunk getChunkNow(int chunkX, int chunkZ) {
        // Paper start - rewrite chunk system
        final LevelChunk ret = this.fullChunks.get(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkX, chunkZ));
        if (!ca.spottedleaf.moonrise.common.PlatformHooks.get().hasCurrentlyLoadingChunk()) {
            return ret;
        }

        if (ret != null || !ca.spottedleaf.moonrise.common.util.TickThread.isTickThread()) {
            return ret;
        }

        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder holder = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler()
            .chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        if (holder == null) {
            return ret;
        }

        return ca.spottedleaf.moonrise.common.PlatformHooks.get().getCurrentlyLoadingChunk(holder.vanillaChunkHolder);
        // Paper end - rewrite chunk system
    }

    private void clearCache() {
        Arrays.fill(this.lastChunkPos, ChunkPos.INVALID_CHUNK_POS);
        Arrays.fill(this.lastChunkStatus, null);
        Arrays.fill(this.lastChunk, null);
    }

    public CompletableFuture<ChunkResult<ChunkAccess>> getChunkFuture(int x, int z, ChunkStatus chunkStatus, boolean requireChunk) {
        boolean flag = Thread.currentThread() == this.mainThread;
        CompletableFuture<ChunkResult<ChunkAccess>> chunkFutureMainThread;
        if (flag) {
            chunkFutureMainThread = this.getChunkFutureMainThread(x, z, chunkStatus, requireChunk);
            this.mainThreadProcessor.managedBlock(chunkFutureMainThread::isDone);
        } else {
            chunkFutureMainThread = CompletableFuture.<CompletableFuture<ChunkResult<ChunkAccess>>>supplyAsync(
                    () -> this.getChunkFutureMainThread(x, z, chunkStatus, requireChunk), this.mainThreadProcessor
                )
                .thenCompose(future -> (CompletionStage<ChunkResult<ChunkAccess>>)future);
        }

        return chunkFutureMainThread;
    }

    private CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(int x, int z, ChunkStatus chunkStatus, boolean requireChunk) {
        // Paper start - rewrite chunk system
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(this.level, x, z, "Scheduling chunk load off-main");

        final int minLevel = ChunkLevel.byStatus(chunkStatus);
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(x, z);

        final boolean needsFullScheduling = chunkStatus == ChunkStatus.FULL && (chunkHolder == null || !chunkHolder.getChunkStatus().isOrAfter(FullChunkStatus.FULL));

        if ((chunkHolder == null || chunkHolder.getTicketLevel() > minLevel || needsFullScheduling) && !requireChunk) {
            return ChunkHolder.UNLOADED_CHUNK_FUTURE;
        }

        final ChunkAccess ifPresent = chunkHolder == null ? null : chunkHolder.getChunkIfPresent(chunkStatus);
        if (needsFullScheduling || ifPresent == null) {
            // schedule
            final CompletableFuture<ChunkResult<ChunkAccess>> ret = new CompletableFuture<>();
            final Consumer<ChunkAccess> complete = (ChunkAccess chunk) -> {
                if (chunk == null) {
                    ret.complete(ChunkHolder.UNLOADED_CHUNK);
                } else {
                    ret.complete(ChunkResult.of(chunk));
                }
            };

            ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().scheduleChunkLoad(
                x, z, chunkStatus, true,
                ca.spottedleaf.concurrentutil.util.Priority.HIGHER,
                complete
            );

            return ret;
        } else {
            // can return now
            return CompletableFuture.completedFuture(ChunkResult.of(ifPresent));
        }
        // Paper end - rewrite chunk system
    }

    @Override
    public boolean hasChunk(int x, int z) {
        return this.getChunkNow(x, z) != null; // Paper - rewrite chunk system
    }

    @Nullable
    @Override
    public LightChunk getChunkForLighting(int chunkX, int chunkZ) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder newChunkHolder = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        if (newChunkHolder == null) {
            return null;
        }
        return newChunkHolder.getChunkIfPresentUnchecked(ChunkStatus.INITIALIZE_LIGHT.getParent());
        // Paper end - rewrite chunk system
    }

    @Override
    public Level getLevel() {
        return this.level;
    }

    public boolean pollTask() {
        return this.mainThreadProcessor.pollTask();
    }

    public boolean runDistanceManagerUpdates() { // Paper - public
        return ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.processTicketUpdates(); // Paper - rewrite chunk system
    }

    public boolean isPositionTicking(long chunkPos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder newChunkHolder = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkPos);
        return newChunkHolder != null && newChunkHolder.isTickingReady();
        // Paper end - rewrite chunk system
    }

    public void save(boolean flush) {
        // Paper - rewrite chunk system
        this.chunkMap.saveAllChunks(flush);
    }

    @Override
    public void close() throws IOException {
        // CraftBukkit start
        this.close(true);
    }

    public void close(boolean save) throws IOException {
        // CraftBukkit end
        // Paper - rewrite chunk system
        this.dataStorage.close();
        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.close(save, true); // Paper - rewrite chunk system
    }

    // CraftBukkit start - modelled on below
    public void purgeUnload() {
        if (true) return; // Paper - rewrite chunk system
        ProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("purge");
        this.distanceManager.purgeStaleTickets();
        this.runDistanceManagerUpdates();
        gameprofilerfiller.popPush("unload");
        this.chunkMap.tick(() -> true);
        gameprofilerfiller.pop();
        this.clearCache();
    }
    // CraftBukkit end

    @Override
    public void tick(BooleanSupplier hasTimeLeft, boolean tickChunks) {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("purge");
        if (this.level.tickRateManager().runsNormally() || !tickChunks || this.level.spigotConfig.unloadFrozenChunks) { // Spigot
            this.distanceManager.purgeStaleTickets();
        }

        this.runDistanceManagerUpdates();
        profilerFiller.popPush("chunks");
        if (tickChunks) {
            ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getPlayerChunkLoader().tick(); // Paper - rewrite chunk system
            this.tickChunks();
            this.chunkMap.tick();
        }

        profilerFiller.popPush("unload");
        this.chunkMap.tick(hasTimeLeft);
        profilerFiller.pop();
        this.clearCache();
    }

    private void tickChunks() {
        long gameTime = this.level.getGameTime();
        long l = gameTime - this.lastInhabitedUpdate;
        this.lastInhabitedUpdate = gameTime;
        if (!this.level.isDebug()) {
            ProfilerFiller profilerFiller = Profiler.get();
            profilerFiller.push("pollingChunks");
            if (this.level.tickRateManager().runsNormally()) {
                List<LevelChunk> list = this.tickingChunks;

                try {
                    profilerFiller.push("filteringTickingChunks");
                    this.collectTickingChunks(list);
                    profilerFiller.popPush("shuffleChunks");
                    // Paper start - chunk tick iteration optimisation
                    this.shuffleRandom.setSeed(this.level.random.nextLong());
                    Util.shuffle(list, this.shuffleRandom);
                    // Paper end - chunk tick iteration optimisation
                    this.tickChunks(profilerFiller, l, list);
                    profilerFiller.pop();
                } finally {
                    list.clear();
                }
            }

            this.broadcastChangedChunks(profilerFiller);
            profilerFiller.pop();
        }
    }

    private void broadcastChangedChunks(ProfilerFiller profiler) {
        profiler.push("broadcast");

        for (ChunkHolder chunkHolder : this.chunkHoldersToBroadcast) {
            LevelChunk tickingChunk = chunkHolder.getChunkToSend(); // Paper - rewrite chunk system
            if (tickingChunk != null) {
                chunkHolder.broadcastChanges(tickingChunk);
            }
        }

        this.chunkHoldersToBroadcast.clear();
        profiler.pop();
    }

    private void collectTickingChunks(List<LevelChunk> output) {
        // Paper start - chunk tick iteration optimisation
        final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.server.level.ServerChunkCache.ChunkAndHolder> tickingChunks =
            ((ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickServerLevel)this.level).moonrise$getPlayerTickingChunks();

        final ServerChunkCache.ChunkAndHolder[] raw = tickingChunks.getRawDataUnchecked();
        final int size = tickingChunks.size();

        final ChunkMap chunkMap = this.chunkMap;

        for (int i = 0; i < size; ++i) {
            final ServerChunkCache.ChunkAndHolder chunkAndHolder = raw[i];
            final LevelChunk levelChunk = chunkAndHolder.chunk();

            if (!this.isChunkNearPlayer(chunkMap, levelChunk.getPos(), levelChunk)) {
                continue;
            }

            output.add(levelChunk);
        }
        // Paper end - chunk tick iteration optimisation
    }

    private void tickChunks(ProfilerFiller profiler, long timeInhabited, List<LevelChunk> chunks) {
        profiler.popPush("naturalSpawnCount");
        int naturalSpawnChunkCount = this.distanceManager.getNaturalSpawnChunkCount();
        NaturalSpawner.SpawnState spawnState = NaturalSpawner.createState(
            naturalSpawnChunkCount, this.level.getAllEntities(), this::getFullChunk, new LocalMobCapCalculator(this.chunkMap)
        );
        this.lastSpawnState = spawnState;
        profiler.popPush("spawnAndTick");
        boolean _boolean = this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) && !this.level.players().isEmpty(); // CraftBukkit
        int _int = this.level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        List<MobCategory> filteredSpawningCategories;
        if (_boolean && (this.spawnEnemies || this.spawnFriendlies)) {
            // Paper start - PlayerNaturallySpawnCreaturesEvent
            for (ServerPlayer entityPlayer : this.level.players()) {
                int chunkRange = Math.min(level.spigotConfig.mobSpawnRange, entityPlayer.getBukkitEntity().getViewDistance());
                chunkRange = Math.min(chunkRange, 8);
                entityPlayer.playerNaturallySpawnedEvent = new com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent(entityPlayer.getBukkitEntity(), (byte) chunkRange);
                entityPlayer.playerNaturallySpawnedEvent.callEvent();
            }
            // Paper end - PlayerNaturallySpawnCreaturesEvent
            boolean flag = this.level.ticksPerSpawnCategory.getLong(org.bukkit.entity.SpawnCategory.ANIMAL) != 0L && this.level.getLevelData().getGameTime() % this.level.ticksPerSpawnCategory.getLong(org.bukkit.entity.SpawnCategory.ANIMAL) == 0L; // CraftBukkit
            filteredSpawningCategories = NaturalSpawner.getFilteredSpawningCategories(spawnState, this.spawnFriendlies, this.spawnEnemies, flag, this.level); // CraftBukkit
        } else {
            filteredSpawningCategories = List.of();
        }

        for (LevelChunk levelChunk : chunks) {
            ChunkPos pos = levelChunk.getPos();
            levelChunk.incrementInhabitedTime(timeInhabited);
            if (!filteredSpawningCategories.isEmpty() && this.level.getWorldBorder().isWithinBounds(pos) && this.chunkMap.anyPlayerCloseEnoughForSpawning(pos, true)) { // Spigot
                NaturalSpawner.spawnForChunk(this.level, levelChunk, spawnState, filteredSpawningCategories);
            }

            if (true) { // Paper - rewrite chunk system
                this.level.tickChunk(levelChunk, _int);
            }
        }

        profiler.popPush("customSpawners");
        if (_boolean) {
            this.level.tickCustomSpawners(this.spawnEnemies, this.spawnFriendlies);
        }
    }

    private void getFullChunk(long chunkPos, Consumer<LevelChunk> fullChunkGetter) {
        // Paper start - rewrite chunk system
        // note: bypass currentlyLoaded from getChunkNow
        final LevelChunk fullChunk = this.fullChunks.get(chunkPos);
        if (fullChunk != null) {
            fullChunkGetter.accept(fullChunk);
        }
        // Paper end - rewrite chunk system
    }

    @Override
    public String gatherStats() {
        return Integer.toString(this.getLoadedChunksCount());
    }

    @VisibleForTesting
    public int getPendingTasksCount() {
        return this.mainThreadProcessor.getPendingTasksCount();
    }

    public ChunkGenerator getGenerator() {
        return this.chunkMap.generator();
    }

    public ChunkGeneratorStructureState getGeneratorState() {
        return this.chunkMap.generatorState();
    }

    public RandomState randomState() {
        return this.chunkMap.randomState();
    }

    @Override
    public int getLoadedChunksCount() {
        return this.chunkMap.size();
    }

    public void blockChanged(BlockPos pos) {
        int sectionPosX = SectionPos.blockToSectionCoord(pos.getX());
        int sectionPosZ = SectionPos.blockToSectionCoord(pos.getZ());
        ChunkHolder visibleChunkIfPresent = this.getVisibleChunkIfPresent(ChunkPos.asLong(sectionPosX, sectionPosZ));
        if (visibleChunkIfPresent != null && visibleChunkIfPresent.blockChanged(pos)) {
            this.chunkHoldersToBroadcast.add(visibleChunkIfPresent);
        }
    }

    @Override
    public void onLightUpdate(LightLayer type, SectionPos pos) {
        this.mainThreadProcessor.execute(() -> {
            ChunkHolder visibleChunkIfPresent = this.getVisibleChunkIfPresent(pos.chunk().toLong());
            if (visibleChunkIfPresent != null && visibleChunkIfPresent.sectionLightChanged(type, pos.y())) {
                this.chunkHoldersToBroadcast.add(visibleChunkIfPresent);
            }
        });
    }

    public <T> void addRegionTicket(TicketType<T> type, ChunkPos pos, int distance, T value) {
        this.distanceManager.addRegionTicket(type, pos, distance, value);
    }

    public <T> void removeRegionTicket(TicketType<T> type, ChunkPos pos, int distance, T value) {
        this.distanceManager.removeRegionTicket(type, pos, distance, value);
    }

    @Override
    public void updateChunkForced(ChunkPos pos, boolean add) {
        this.distanceManager.updateChunkForced(pos, add);
    }

    public void move(ServerPlayer player) {
        if (!player.isRemoved()) {
            this.chunkMap.move(player);
        }
    }

    public void removeEntity(Entity entity) {
        this.chunkMap.removeEntity(entity);
    }

    public void addEntity(Entity entity) {
        this.chunkMap.addEntity(entity);
    }

    public void broadcastAndSend(Entity entity, Packet<?> packet) {
        this.chunkMap.broadcastAndSend(entity, packet);
    }

    public void broadcast(Entity entity, Packet<?> packet) {
        this.chunkMap.broadcast(entity, packet);
    }

    public void setViewDistance(int viewDistance) {
        this.chunkMap.setServerViewDistance(viewDistance);
    }

    // Paper start - rewrite chunk system
    public void setSendViewDistance(int viewDistance) {
        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getPlayerChunkLoader().setSendDistance(viewDistance);
    }
    // Paper end - rewrite chunk system

    public void setSimulationDistance(int simulationDistance) {
        this.distanceManager.updateSimulationDistance(simulationDistance);
    }

    @Override
    public void setSpawnSettings(boolean spawnSettings) {
        // CraftBukkit start
        this.setSpawnSettings(spawnSettings, this.spawnFriendlies);
    }
    public void setSpawnSettings(boolean spawnSettings, boolean spawnFriendlies) {
        this.spawnEnemies = spawnSettings;
        this.spawnFriendlies = spawnFriendlies;
        // CraftBukkit end
    }

    public String getChunkDebugData(ChunkPos chunkPos) {
        return this.chunkMap.getChunkDebugData(chunkPos);
    }

    public DimensionDataStorage getDataStorage() {
        return this.dataStorage;
    }

    public PoiManager getPoiManager() {
        return this.chunkMap.getPoiManager();
    }

    public ChunkScanAccess chunkScanner() {
        return this.chunkMap.chunkScanner();
    }

    @Nullable
    @VisibleForDebug
    public NaturalSpawner.SpawnState getLastSpawnState() {
        return this.lastSpawnState;
    }

    public void removeTicketsOnClosing() {
        this.distanceManager.removeTicketsOnClosing();
    }

    public void onChunkReadyToSend(ChunkHolder chunkHolder) {
        if (chunkHolder.hasChangesToBroadcast()) {
            this.chunkHoldersToBroadcast.add(chunkHolder);
        }
    }

    public record ChunkAndHolder(LevelChunk chunk, ChunkHolder holder) { // Paper - public
    }

    public final class MainThreadExecutor extends BlockableEventLoop<Runnable> {
        MainThreadExecutor(final Level level) {
            super("Chunk source main thread executor for " + level.dimension().location());
        }

        @Override
        public void managedBlock(BooleanSupplier isDone) {
            super.managedBlock(() -> MinecraftServer.throwIfFatalException() && isDone.getAsBoolean());
        }

        @Override
        public Runnable wrapRunnable(Runnable runnable) {
            return runnable;
        }

        @Override
        protected boolean shouldRun(Runnable runnable) {
            return true;
        }

        @Override
        protected boolean scheduleExecutables() {
            return true;
        }

        @Override
        protected Thread getRunningThread() {
            return ServerChunkCache.this.mainThread;
        }

        @Override
        protected void doRunTask(Runnable task) {
            Profiler.get().incrementCounter("runTask");
            super.doRunTask(task);
        }

        @Override
        public boolean pollTask() {
            // Paper start - rewrite chunk system
            final ServerChunkCache serverChunkCache = ServerChunkCache.this;
            if (serverChunkCache.runDistanceManagerUpdates()) {
                return true;
            } else {
                return super.pollTask() | ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)serverChunkCache.level).moonrise$getChunkTaskScheduler().executeMainThreadTask();
            }
            // Paper end - rewrite chunk system
        }
    }
}
