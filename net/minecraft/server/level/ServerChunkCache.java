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

public class ServerChunkCache extends ChunkSource {
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
    public void addLoadedChunk(LevelChunk chunk) {
        this.fullChunks.put(chunk.coordinateKey, chunk);
    }

    public void removeLoadedChunk(LevelChunk chunk) {
        this.fullChunks.remove(chunk.coordinateKey);
    }

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
        if (Thread.currentThread() != this.mainThread) {
            return CompletableFuture.<ChunkAccess>supplyAsync(() -> this.getChunk(x, z, chunkStatus, requireChunk), this.mainThreadProcessor).join();
        } else {
            ProfilerFiller profilerFiller = Profiler.get();
            profilerFiller.incrementCounter("getChunk");
            long packedChunkPos = ChunkPos.asLong(x, z);

            for (int i = 0; i < 4; i++) {
                if (packedChunkPos == this.lastChunkPos[i] && chunkStatus == this.lastChunkStatus[i]) {
                    ChunkAccess chunkAccess = this.lastChunk[i];
                    if (chunkAccess != null) { // CraftBukkit - the chunk can become accessible in the meantime TODO for non-null chunks it might also make sense to check that the chunk's state hasn't changed in the meantime
                        return chunkAccess;
                    }
                }
            }

            profilerFiller.incrementCounter("getChunkCacheMiss");
            CompletableFuture<ChunkResult<ChunkAccess>> chunkFutureMainThread = this.getChunkFutureMainThread(x, z, chunkStatus, requireChunk);
            this.mainThreadProcessor.managedBlock(chunkFutureMainThread::isDone);
            // com.destroystokyo.paper.io.SyncLoadFinder.logSyncLoad(this.level, x, z); // Paper - Add debug for sync chunk loads
            ChunkResult<ChunkAccess> chunkResult = chunkFutureMainThread.join();
            ChunkAccess chunkAccess1 = chunkResult.orElse(null);
            if (chunkAccess1 == null && requireChunk) {
                throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("Chunk not there when requested: " + chunkResult.getError()));
            } else {
                this.storeInCache(packedChunkPos, chunkAccess1, chunkStatus);
                return chunkAccess1;
            }
        }
    }

    @Nullable
    @Override
    public LevelChunk getChunkNow(int chunkX, int chunkZ) {
        if (Thread.currentThread() != this.mainThread) {
            return null;
        } else {
            Profiler.get().incrementCounter("getChunkNow");
            long packedChunkPos = ChunkPos.asLong(chunkX, chunkZ);

            for (int i = 0; i < 4; i++) {
                if (packedChunkPos == this.lastChunkPos[i] && this.lastChunkStatus[i] == ChunkStatus.FULL) {
                    ChunkAccess chunkAccess = this.lastChunk[i];
                    return chunkAccess instanceof LevelChunk ? (LevelChunk)chunkAccess : null;
                }
            }

            ChunkHolder visibleChunkIfPresent = this.getVisibleChunkIfPresent(packedChunkPos);
            if (visibleChunkIfPresent == null) {
                return null;
            } else {
                ChunkAccess chunkAccess = visibleChunkIfPresent.getChunkIfPresent(ChunkStatus.FULL);
                if (chunkAccess != null) {
                    this.storeInCache(packedChunkPos, chunkAccess, ChunkStatus.FULL);
                    if (chunkAccess instanceof LevelChunk) {
                        return (LevelChunk)chunkAccess;
                    }
                }

                return null;
            }
        }
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
        ChunkPos chunkPos = new ChunkPos(x, z);
        long packedChunkPos = chunkPos.toLong();
        int i = ChunkLevel.byStatus(chunkStatus);
        ChunkHolder visibleChunkIfPresent = this.getVisibleChunkIfPresent(packedChunkPos);
        // CraftBukkit start - don't add new ticket for currently unloading chunk
        boolean currentlyUnloading = false;
        if (visibleChunkIfPresent != null) {
            FullChunkStatus oldChunkState = ChunkLevel.fullStatus(visibleChunkIfPresent.oldTicketLevel);
            FullChunkStatus currentChunkState = ChunkLevel.fullStatus(visibleChunkIfPresent.getTicketLevel());
            currentlyUnloading = (oldChunkState.isOrAfter(FullChunkStatus.FULL) && !currentChunkState.isOrAfter(FullChunkStatus.FULL));
        }
        if (requireChunk && !currentlyUnloading) {
        // CraftBukkit end
            this.distanceManager.addTicket(TicketType.UNKNOWN, chunkPos, i, chunkPos);
            if (this.chunkAbsent(visibleChunkIfPresent, i)) {
                ProfilerFiller profilerFiller = Profiler.get();
                profilerFiller.push("chunkLoad");
                this.runDistanceManagerUpdates();
                visibleChunkIfPresent = this.getVisibleChunkIfPresent(packedChunkPos);
                profilerFiller.pop();
                if (this.chunkAbsent(visibleChunkIfPresent, i)) {
                    throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("No chunk holder after ticket has been added"));
                }
            }
        }

        return this.chunkAbsent(visibleChunkIfPresent, i)
            ? GenerationChunkHolder.UNLOADED_CHUNK_FUTURE
            : visibleChunkIfPresent.scheduleChunkGenerationTask(chunkStatus, this.chunkMap);
    }

    private boolean chunkAbsent(@Nullable ChunkHolder chunkHolder, int status) {
        return chunkHolder == null || chunkHolder.oldTicketLevel > status; // CraftBukkit using oldTicketLevel for isLoaded checks
    }

    @Override
    public boolean hasChunk(int x, int z) {
        ChunkHolder visibleChunkIfPresent = this.getVisibleChunkIfPresent(new ChunkPos(x, z).toLong());
        int i = ChunkLevel.byStatus(ChunkStatus.FULL);
        return !this.chunkAbsent(visibleChunkIfPresent, i);
    }

    @Nullable
    @Override
    public LightChunk getChunkForLighting(int chunkX, int chunkZ) {
        long packedChunkPos = ChunkPos.asLong(chunkX, chunkZ);
        ChunkHolder visibleChunkIfPresent = this.getVisibleChunkIfPresent(packedChunkPos);
        return visibleChunkIfPresent == null ? null : visibleChunkIfPresent.getChunkIfPresentUnchecked(ChunkStatus.INITIALIZE_LIGHT.getParent());
    }

    @Override
    public Level getLevel() {
        return this.level;
    }

    public boolean pollTask() {
        return this.mainThreadProcessor.pollTask();
    }

    public boolean runDistanceManagerUpdates() { // Paper - public
        boolean flag = this.distanceManager.runAllUpdates(this.chunkMap);
        boolean flag1 = this.chunkMap.promoteChunkMap();
        this.chunkMap.runGenerationTasks();
        if (!flag && !flag1) {
            return false;
        } else {
            this.clearCache();
            return true;
        }
    }

    public boolean isPositionTicking(long chunkPos) {
        if (!this.level.shouldTickBlocksAt(chunkPos)) {
            return false;
        } else {
            ChunkHolder visibleChunkIfPresent = this.getVisibleChunkIfPresent(chunkPos);
            return visibleChunkIfPresent != null && visibleChunkIfPresent.getTickingChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).isSuccess();
        }
    }

    public void save(boolean flush) {
        this.runDistanceManagerUpdates();
        this.chunkMap.saveAllChunks(flush);
    }

    @Override
    public void close() throws IOException {
        // CraftBukkit start
        this.close(true);
    }

    public void close(boolean save) throws IOException {
        if (save) {
            this.save(true);
        }
        // CraftBukkit end
        this.dataStorage.close();
        this.lightEngine.close();
        this.chunkMap.close();
    }

    // CraftBukkit start - modelled on below
    public void purgeUnload() {
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
                    Util.shuffle(list, this.level.random);
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
            LevelChunk tickingChunk = chunkHolder.getTickingChunk();
            if (tickingChunk != null) {
                chunkHolder.broadcastChanges(tickingChunk);
            }
        }

        this.chunkHoldersToBroadcast.clear();
        profiler.pop();
    }

    private void collectTickingChunks(List<LevelChunk> output) {
        this.chunkMap.forEachSpawnCandidateChunk(chunk -> {
            LevelChunk tickingChunk = chunk.getTickingChunk();
            if (tickingChunk != null && this.level.isNaturalSpawningAllowed(chunk.getPos())) {
                output.add(tickingChunk);
            }
        });
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

            if (this.level.shouldTickBlocksAt(pos.toLong())) {
                this.level.tickChunk(levelChunk, _int);
            }
        }

        profiler.popPush("customSpawners");
        if (_boolean) {
            this.level.tickCustomSpawners(this.spawnEnemies, this.spawnFriendlies);
        }
    }

    private void getFullChunk(long chunkPos, Consumer<LevelChunk> fullChunkGetter) {
        ChunkHolder visibleChunkIfPresent = this.getVisibleChunkIfPresent(chunkPos);
        if (visibleChunkIfPresent != null) {
            visibleChunkIfPresent.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).ifSuccess(fullChunkGetter);
        }
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

    record ChunkAndHolder(LevelChunk chunk, ChunkHolder holder) {
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
            try { // CraftBukkit - process pending Chunk loadCallback() and unloadCallback() after each run task
            if (ServerChunkCache.this.runDistanceManagerUpdates()) {
                return true;
            } else {
                ServerChunkCache.this.lightEngine.tryScheduleUpdate();
                return super.pollTask();
            }
            // CraftBukkit start - process pending Chunk loadCallback() and unloadCallback() after each run task
            } finally {
                ServerChunkCache.this.chunkMap.callbackExecutor.run();
            }
            // CraftBukkit end - process pending Chunk loadCallback() and unloadCallback() after each run task
        }
    }
}
