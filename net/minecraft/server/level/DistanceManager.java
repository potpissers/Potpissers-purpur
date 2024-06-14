package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.util.SortedArraySet;
import net.minecraft.util.thread.TaskScheduler;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

public abstract class DistanceManager implements ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemDistanceManager, ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickDistanceManager { // Paper - rewrite chunk system // Paper - chunk tick iteration optimisation
    static final Logger LOGGER = LogUtils.getLogger();
    static final int PLAYER_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    private static final int INITIAL_TICKET_LIST_CAPACITY = 4;
    final Long2ObjectMap<ObjectSet<ServerPlayer>> playersPerChunk = new Long2ObjectOpenHashMap<>();
    // Paper - rewrite chunk system
    // Paper - chunk tick iteration optimisation
    // Paper - rewrite chunk system
    private long ticketTickCounter;
    // Paper - rewrite chunk system

    protected DistanceManager(Executor dispatcher, Executor mainThreadExecutor) {
        TaskScheduler<Runnable> taskScheduler = TaskScheduler.wrapExecutor("player ticket throttler", mainThreadExecutor);
        // Paper - rewrite chunk system
    }

    // Paper start - rewrite chunk system
    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager moonrise$getChunkHolderManager() {
        return ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.moonrise$getChunkMap().level).moonrise$getChunkTaskScheduler().chunkHolderManager;
    }
    // Paper end - rewrite chunk system
    // Paper start - chunk tick iteration optimisation
    private final ca.spottedleaf.moonrise.common.misc.PositionCountingAreaMap<ServerPlayer> spawnChunkTracker = new ca.spottedleaf.moonrise.common.misc.PositionCountingAreaMap<>();

    @Override
    public final void moonrise$addPlayer(final ServerPlayer player, final SectionPos pos) {
        this.spawnChunkTracker.add(player, pos.x(), pos.z(), ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickConstants.PLAYER_SPAWN_TRACK_RANGE);
    }

    @Override
    public final void moonrise$removePlayer(final ServerPlayer player, final SectionPos pos) {
        this.spawnChunkTracker.remove(player);
    }

    @Override
    public final void moonrise$updatePlayer(final ServerPlayer player,
                                            final SectionPos oldPos, final SectionPos newPos,
                                            final boolean oldIgnore, final boolean newIgnore) {
        if (newIgnore) {
            this.spawnChunkTracker.remove(player);
        } else {
            this.spawnChunkTracker.addOrUpdate(player, newPos.x(), newPos.z(), ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickConstants.PLAYER_SPAWN_TRACK_RANGE);
        }
    }
    // Paper end - chunk tick iteration optimisation

    protected void purgeStaleTickets() {
        this.moonrise$getChunkHolderManager().tick(); // Paper - rewrite chunk system
    }

    private static int getTicketLevelAt(SortedArraySet<Ticket<?>> tickets) {
        return !tickets.isEmpty() ? tickets.first().getTicketLevel() : ChunkLevel.MAX_LEVEL + 1;
    }

    protected abstract boolean isChunkToRemove(long chunkPos);

    @Nullable
    protected abstract ChunkHolder getChunk(long chunkPos);

    @Nullable
    protected abstract ChunkHolder updateChunkScheduling(long chunkPos, int i, @Nullable ChunkHolder newLevel, int holder);

    public boolean runAllUpdates(ChunkMap chunkMap) {
        return this.moonrise$getChunkHolderManager().processTicketUpdates(); // Paper - rewrite chunk system
    }

    void addTicket(long chunkPos, Ticket<?> ticket) {
        this.moonrise$getChunkHolderManager().addTicketAtLevel((TicketType)ticket.getType(), chunkPos, ticket.getTicketLevel(), ticket.key); // Paper - rewrite chunk system
    }

    void removeTicket(long chunkPos, Ticket<?> ticket) {
        this.moonrise$getChunkHolderManager().removeTicketAtLevel((TicketType)ticket.getType(), chunkPos, ticket.getTicketLevel(), ticket.key); // Paper - rewrite chunk system
    }

    public <T> void addTicket(TicketType<T> type, ChunkPos pos, int level, T value) {
        this.addTicket(pos.toLong(), new Ticket<>(type, level, value));
    }

    public <T> void removeTicket(TicketType<T> type, ChunkPos pos, int level, T value) {
        Ticket<T> ticket = new Ticket<>(type, level, value);
        this.removeTicket(pos.toLong(), ticket);
    }

    public <T> void addRegionTicket(TicketType<T> type, ChunkPos pos, int distance, T value) {
        this.moonrise$getChunkHolderManager().addTicketAtLevel(type, pos, ChunkLevel.byStatus(FullChunkStatus.FULL) - distance, value); // Paper - rewrite chunk system
    }

    public <T> void removeRegionTicket(TicketType<T> type, ChunkPos pos, int distance, T value) {
        this.moonrise$getChunkHolderManager().removeTicketAtLevel(type, pos, ChunkLevel.byStatus(FullChunkStatus.FULL) - distance, value); // Paper - rewrite chunk system
    }

    // Paper start
    public boolean addPluginRegionTicket(final ChunkPos pos, final org.bukkit.plugin.Plugin value) {
        return this.moonrise$getChunkHolderManager().addTicketAtLevel(TicketType.PLUGIN_TICKET, pos, ChunkLevel.byStatus(FullChunkStatus.FULL) - 2, value); // Paper - rewrite chunk system
    }

    public boolean removePluginRegionTicket(final ChunkPos pos, final org.bukkit.plugin.Plugin value) {
        return this.moonrise$getChunkHolderManager().removeTicketAtLevel(TicketType.PLUGIN_TICKET, pos, ChunkLevel.byStatus(FullChunkStatus.FULL) - 2, value); // Paper - rewrite chunk system
    }
    // Paper end

    private SortedArraySet<Ticket<?>> getTickets(long chunkPos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    protected void updateChunkForced(ChunkPos pos, boolean add) {
        // Paper start - rewrite chunk system
        if (add) {
            this.moonrise$getChunkHolderManager().addTicketAtLevel(TicketType.FORCED, pos, ChunkMap.FORCED_TICKET_LEVEL, pos);
        } else {
            this.moonrise$getChunkHolderManager().removeTicketAtLevel(TicketType.FORCED, pos, ChunkMap.FORCED_TICKET_LEVEL, pos);
        }
        // Paper end - rewrite chunk system
    }

    public void addPlayer(SectionPos sectionPos, ServerPlayer player) {
        ChunkPos chunkPos = sectionPos.chunk();
        long packedChunkPos = chunkPos.toLong();
        this.playersPerChunk.computeIfAbsent(packedChunkPos, l -> new ObjectOpenHashSet<>()).add(player);
        // Paper - chunk tick iteration optimisation
        // Paper - rewrite chunk system
    }

    public void removePlayer(SectionPos sectionPos, ServerPlayer player) {
        ChunkPos chunkPos = sectionPos.chunk();
        long packedChunkPos = chunkPos.toLong();
        ObjectSet<ServerPlayer> set = this.playersPerChunk.get(packedChunkPos);
        // Paper start - some state corruption happens here, don't crash, clean up gracefully
        if (set != null) set.remove(player);
        if (set == null || set.isEmpty()) {
        // Paper end - some state corruption happens here, don't crash, clean up gracefully
            this.playersPerChunk.remove(packedChunkPos);
            // Paper - chunk tick iteration optimisation
            // Paper - rewrite chunk system
        }
    }

    private int getPlayerTicketLevel() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public boolean inEntityTickingRange(long chunkPos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = this.moonrise$getChunkHolderManager().getChunkHolder(chunkPos);
        return chunkHolder != null && chunkHolder.isEntityTickingReady();
        // Paper end - rewrite chunk system
    }

    public boolean inBlockTickingRange(long chunkPos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = this.moonrise$getChunkHolderManager().getChunkHolder(chunkPos);
        return chunkHolder != null && chunkHolder.isTickingReady();
        // Paper end - rewrite chunk system
    }

    protected String getTicketDebugString(long chunkPos) {
        return this.moonrise$getChunkHolderManager().getTicketDebugString(chunkPos); // Paper - rewrite chunk system
    }

    protected void updatePlayerTickets(int viewDistance) {
        this.moonrise$getChunkMap().setServerViewDistance(viewDistance); // Paper - rewrite chunk system
    }

    public void updateSimulationDistance(int simulationDistance) {
        // Paper start - rewrite chunk system
        // note: vanilla does not clamp to 0, but we do simply because we need a min of 0
        final int clamped = net.minecraft.util.Mth.clamp(simulationDistance, 0, ca.spottedleaf.moonrise.common.util.MoonriseConstants.MAX_VIEW_DISTANCE);

        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.moonrise$getChunkMap().level).moonrise$getPlayerChunkLoader().setTickDistance(clamped);
        // Paper end - rewrite chunk system
    }

    public int getNaturalSpawnChunkCount() {
        return this.spawnChunkTracker.getTotalPositions(); // Paper - chunk tick iteration optimisation
    }

    public boolean hasPlayersNearby(long chunkPos) {
        return this.spawnChunkTracker.hasObjectsNear(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkX(chunkPos), ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkZ(chunkPos)); // Paper - chunk tick iteration optimisation
    }

    public LongIterator getSpawnCandidateChunks() {
        return this.spawnChunkTracker.getPositions().iterator(); // Paper - chunk tick iteration optimisation
    }

    public String getDebugStatus() {
        return "No DistanceManager stats available"; // Paper - rewrite chunk system
    }

    private void dumpTickets(String filename) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @VisibleForTesting
    TickingTracker tickingTracker() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public LongSet getTickingChunks() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public void removeTicketsOnClosing() {
        // Paper - rewrite chunk system
    }

    public boolean hasTickets() {
        throw new UnsupportedOperationException();  // Paper - rewrite chunk system
    }

    // CraftBukkit start
    public <T> void removeAllTicketsFor(TicketType<T> ticketType, int ticketLevel, T ticketIdentifier) {
        this.moonrise$getChunkHolderManager().removeAllTicketsFor(ticketType, ticketLevel, ticketIdentifier); // Paper - rewrite chunk system
    }
    // CraftBukkit end

/*  // Paper - rewrite chunk system
    class ChunkTicketTracker extends ChunkTracker {
        private static final int MAX_LEVEL = ChunkLevel.MAX_LEVEL + 1;

        public ChunkTicketTracker() {
            super(MAX_LEVEL + 1, 16, 256);
        }

        @Override
        protected int getLevelFromSource(long pos) {
            SortedArraySet<Ticket<?>> set = DistanceManager.this.tickets.get(pos);
            if (set == null) {
                return Integer.MAX_VALUE;
            } else {
                return set.isEmpty() ? Integer.MAX_VALUE : set.first().getTicketLevel();
            }
        }

        @Override
        protected int getLevel(long sectionPos) {
            if (!DistanceManager.this.isChunkToRemove(sectionPos)) {
                ChunkHolder chunk = DistanceManager.this.getChunk(sectionPos);
                if (chunk != null) {
                    return chunk.getTicketLevel();
                }
            }

            return MAX_LEVEL;
        }

        @Override
        protected void setLevel(long sectionPos, int level) {
            ChunkHolder chunk = DistanceManager.this.getChunk(sectionPos);
            int i = chunk == null ? MAX_LEVEL : chunk.getTicketLevel();
            if (i != level) {
                chunk = DistanceManager.this.updateChunkScheduling(sectionPos, level, chunk, i);
                if (chunk != null) {
                    DistanceManager.this.chunksToUpdateFutures.add(chunk);
                }
            }
        }

        public int runDistanceUpdates(int toUpdateCount) {
            return this.runUpdates(toUpdateCount);
        }
    }*/  // Paper - rewrite chunk system

    class FixedPlayerDistanceChunkTracker extends ChunkTracker {
        protected final Long2ByteMap chunks = new Long2ByteOpenHashMap();
        protected final int maxDistance;

        protected FixedPlayerDistanceChunkTracker(final int maxDistance) {
            super(maxDistance + 2, 16, 256);
            this.maxDistance = maxDistance;
            this.chunks.defaultReturnValue((byte)(maxDistance + 2));
        }

        @Override
        protected int getLevel(long sectionPos) {
            return this.chunks.get(sectionPos);
        }

        @Override
        protected void setLevel(long sectionPos, int level) {
            byte b;
            if (level > this.maxDistance) {
                b = this.chunks.remove(sectionPos);
            } else {
                b = this.chunks.put(sectionPos, (byte)level);
            }

            this.onLevelChange(sectionPos, b, level);
        }

        protected void onLevelChange(long chunkPos, int oldLevel, int newLevel) {
        }

        @Override
        protected int getLevelFromSource(long pos) {
            return this.havePlayer(pos) ? 0 : Integer.MAX_VALUE;
        }

        private boolean havePlayer(long chunkPos) {
            ObjectSet<ServerPlayer> set = DistanceManager.this.playersPerChunk.get(chunkPos);
            return set != null && !set.isEmpty();
        }

        public void runAllUpdates() {
            this.runUpdates(Integer.MAX_VALUE);
        }

        private void dumpChunks(String filename) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(new File(filename))) {
                for (it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry entry : this.chunks.long2ByteEntrySet()) {
                    ChunkPos chunkPos = new ChunkPos(entry.getLongKey());
                    String string = Byte.toString(entry.getByteValue());
                    fileOutputStream.write((chunkPos.x + "\t" + chunkPos.z + "\t" + string + "\n").getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException var9) {
                DistanceManager.LOGGER.error("Failed to dump chunks to {}", filename, var9);
            }
        }
    }

/*  // Paper - rewrite chunk system
    class PlayerTicketTracker extends DistanceManager.FixedPlayerDistanceChunkTracker {
        private int viewDistance;
        private final Long2IntMap queueLevels = Long2IntMaps.synchronize(new Long2IntOpenHashMap());
        private final LongSet toUpdate = new LongOpenHashSet();

        protected PlayerTicketTracker(final int i) {
            super(i);
            this.viewDistance = 0;
            this.queueLevels.defaultReturnValue(i + 2);
        }

        @Override
        protected void onLevelChange(long chunkPos, int oldLevel, int newLevel) {
            this.toUpdate.add(chunkPos);
        }

        public void updateViewDistance(int viewDistance) {
            for (it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry entry : this.chunks.long2ByteEntrySet()) {
                byte byteValue = entry.getByteValue();
                long longKey = entry.getLongKey();
                this.onLevelChange(longKey, byteValue, this.haveTicketFor(byteValue), byteValue <= viewDistance);
            }

            this.viewDistance = viewDistance;
        }

        private void onLevelChange(long chunkPos, int level, boolean hadTicket, boolean hasTicket) {
            if (hadTicket != hasTicket) {
                Ticket<?> ticket = new Ticket<>(TicketType.PLAYER, DistanceManager.PLAYER_TICKET_LEVEL, new ChunkPos(chunkPos));
                if (hasTicket) {
                    DistanceManager.this.ticketDispatcher.submit(() -> DistanceManager.this.mainThreadExecutor.execute(() -> {
                        if (this.haveTicketFor(this.getLevel(chunkPos))) {
                            DistanceManager.this.addTicket(chunkPos, ticket);
                            DistanceManager.this.ticketsToRelease.add(chunkPos);
                        } else {
                            DistanceManager.this.ticketDispatcher.release(chunkPos, () -> {}, false);
                        }
                    }), chunkPos, () -> level);
                } else {
                    DistanceManager.this.ticketDispatcher
                        .release(
                            chunkPos, () -> DistanceManager.this.mainThreadExecutor.execute(() -> DistanceManager.this.removeTicket(chunkPos, ticket)), true
                        );
                }
            }
        }

        @Override
        public void runAllUpdates() {
            super.runAllUpdates();
            if (!this.toUpdate.isEmpty()) {
                LongIterator longIterator = this.toUpdate.iterator();

                while (longIterator.hasNext()) {
                    long l = longIterator.nextLong();
                    int i = this.queueLevels.get(l);
                    int level = this.getLevel(l);
                    if (i != level) {
                        DistanceManager.this.ticketDispatcher.onLevelChange(new ChunkPos(l), () -> this.queueLevels.get(l), level, i1 -> {
                            if (i1 >= this.queueLevels.defaultReturnValue()) {
                                this.queueLevels.remove(l);
                            } else {
                                this.queueLevels.put(l, i1);
                            }
                        });
                        this.onLevelChange(l, level, this.haveTicketFor(i), this.haveTicketFor(level));
                    }
                }

                this.toUpdate.clear();
            }
        }

        private boolean haveTicketFor(int level) {
            return level <= this.viewDistance;
        }
    }*/  // Paper - rewrite chunk system
}
