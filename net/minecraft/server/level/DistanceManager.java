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

public abstract class DistanceManager {
    static final Logger LOGGER = LogUtils.getLogger();
    static final int PLAYER_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    private static final int INITIAL_TICKET_LIST_CAPACITY = 4;
    final Long2ObjectMap<ObjectSet<ServerPlayer>> playersPerChunk = new Long2ObjectOpenHashMap<>();
    public final Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = new Long2ObjectOpenHashMap<>();
    private final DistanceManager.ChunkTicketTracker ticketTracker = new DistanceManager.ChunkTicketTracker();
    private final DistanceManager.FixedPlayerDistanceChunkTracker naturalSpawnChunkCounter = new DistanceManager.FixedPlayerDistanceChunkTracker(8);
    private final TickingTracker tickingTicketsTracker = new TickingTracker();
    private final DistanceManager.PlayerTicketTracker playerTicketManager = new DistanceManager.PlayerTicketTracker(32);
    final Set<ChunkHolder> chunksToUpdateFutures = new ReferenceOpenHashSet<>();
    final ThrottlingChunkTaskDispatcher ticketDispatcher;
    final LongSet ticketsToRelease = new LongOpenHashSet();
    final Executor mainThreadExecutor;
    private long ticketTickCounter;
    public int simulationDistance = 10;

    protected DistanceManager(Executor dispatcher, Executor mainThreadExecutor) {
        TaskScheduler<Runnable> taskScheduler = TaskScheduler.wrapExecutor("player ticket throttler", mainThreadExecutor);
        this.ticketDispatcher = new ThrottlingChunkTaskDispatcher(taskScheduler, dispatcher, 4);
        this.mainThreadExecutor = mainThreadExecutor;
    }

    protected void purgeStaleTickets() {
        this.ticketTickCounter++;
        ObjectIterator<Entry<SortedArraySet<Ticket<?>>>> objectIterator = this.tickets.long2ObjectEntrySet().fastIterator();

        while (objectIterator.hasNext()) {
            Entry<SortedArraySet<Ticket<?>>> entry = objectIterator.next();
            Iterator<Ticket<?>> iterator = entry.getValue().iterator();
            boolean flag = false;

            while (iterator.hasNext()) {
                Ticket<?> ticket = iterator.next();
                if (ticket.timedOut(this.ticketTickCounter)) {
                    iterator.remove();
                    flag = true;
                    this.tickingTicketsTracker.removeTicket(entry.getLongKey(), ticket);
                }
            }

            if (flag) {
                this.ticketTracker.update(entry.getLongKey(), getTicketLevelAt(entry.getValue()), false);
            }

            if (entry.getValue().isEmpty()) {
                objectIterator.remove();
            }
        }
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
        this.naturalSpawnChunkCounter.runAllUpdates();
        this.tickingTicketsTracker.runAllUpdates();
        this.playerTicketManager.runAllUpdates();
        int i = Integer.MAX_VALUE - this.ticketTracker.runDistanceUpdates(Integer.MAX_VALUE);
        boolean flag = i != 0;
        if (flag) {
        }

        if (!this.chunksToUpdateFutures.isEmpty()) {
            // CraftBukkit start - SPIGOT-7780: Call chunk unload events before updateHighestAllowedStatus
            for (final ChunkHolder chunkHolder : this.chunksToUpdateFutures) {
                chunkHolder.callEventIfUnloading(chunkMap);
            }
            // CraftBukkit end - SPIGOT-7780: Call chunk unload events before updateHighestAllowedStatus

            for (ChunkHolder chunkHolder : this.chunksToUpdateFutures) {
                chunkHolder.updateHighestAllowedStatus(chunkMap);
            }

            for (ChunkHolder chunkHolder : this.chunksToUpdateFutures) {
                chunkHolder.updateFutures(chunkMap, this.mainThreadExecutor);
            }

            this.chunksToUpdateFutures.clear();
            return true;
        } else {
            if (!this.ticketsToRelease.isEmpty()) {
                LongIterator longIterator = this.ticketsToRelease.iterator();

                while (longIterator.hasNext()) {
                    long l = longIterator.nextLong();
                    if (this.getTickets(l).stream().anyMatch(ticket -> ticket.getType() == TicketType.PLAYER)) {
                        ChunkHolder updatingChunkIfPresent = chunkMap.getUpdatingChunkIfPresent(l);
                        if (updatingChunkIfPresent == null) {
                            throw new IllegalStateException();
                        }

                        CompletableFuture<ChunkResult<LevelChunk>> entityTickingChunkFuture = updatingChunkIfPresent.getEntityTickingChunkFuture();
                        entityTickingChunkFuture.thenAccept(
                            chunkResult -> this.mainThreadExecutor.execute(() -> this.ticketDispatcher.release(l, () -> {}, false))
                        );
                    }
                }

                this.ticketsToRelease.clear();
            }

            return flag;
        }
    }

    void addTicket(long chunkPos, Ticket<?> ticket) {
        SortedArraySet<Ticket<?>> tickets = this.getTickets(chunkPos);
        int ticketLevelAt = getTicketLevelAt(tickets);
        Ticket<?> ticket1 = tickets.addOrGet(ticket);
        ticket1.setCreatedTick(this.ticketTickCounter);
        if (ticket.getTicketLevel() < ticketLevelAt) {
            this.ticketTracker.update(chunkPos, ticket.getTicketLevel(), true);
        }
    }

    void removeTicket(long chunkPos, Ticket<?> ticket) {
        SortedArraySet<Ticket<?>> tickets = this.getTickets(chunkPos);
        if (tickets.remove(ticket)) {
        }

        if (tickets.isEmpty()) {
            this.tickets.remove(chunkPos);
        }

        this.ticketTracker.update(chunkPos, getTicketLevelAt(tickets), false);
    }

    public <T> void addTicket(TicketType<T> type, ChunkPos pos, int level, T value) {
        this.addTicket(pos.toLong(), new Ticket<>(type, level, value));
    }

    public <T> void removeTicket(TicketType<T> type, ChunkPos pos, int level, T value) {
        Ticket<T> ticket = new Ticket<>(type, level, value);
        this.removeTicket(pos.toLong(), ticket);
    }

    public <T> void addRegionTicket(TicketType<T> type, ChunkPos pos, int distance, T value) {
        Ticket<T> ticket = new Ticket<>(type, ChunkLevel.byStatus(FullChunkStatus.FULL) - distance, value);
        long packedChunkPos = pos.toLong();
        this.addTicket(packedChunkPos, ticket); // Paper - diff on change above
        this.tickingTicketsTracker.addTicket(packedChunkPos, ticket);
    }

    public <T> void removeRegionTicket(TicketType<T> type, ChunkPos pos, int distance, T value) {
        Ticket<T> ticket = new Ticket<>(type, ChunkLevel.byStatus(FullChunkStatus.FULL) - distance, value);
        long packedChunkPos = pos.toLong();
        this.removeTicket(packedChunkPos, ticket); // Paper - diff on change above
        this.tickingTicketsTracker.removeTicket(packedChunkPos, ticket);
    }

    // Paper start
    public boolean addPluginRegionTicket(final ChunkPos pos, final org.bukkit.plugin.Plugin value) {
        Ticket<org.bukkit.plugin.Plugin> ticket = new Ticket<>(TicketType.PLUGIN_TICKET, ChunkLevel.byStatus(FullChunkStatus.FULL) - 2, value); // Copied from below and keep in-line with force loading, add at level 31
        final long packedChunkPos = pos.toLong();
        final Set<Ticket<?>> tickets = this.getTickets(packedChunkPos);
        if (tickets.contains(ticket)) {
            return false;
        }
        this.addTicket(packedChunkPos, ticket);
        this.tickingTicketsTracker.addTicket(packedChunkPos, ticket);
        return true;
    }

    public boolean removePluginRegionTicket(final ChunkPos pos, final org.bukkit.plugin.Plugin value) {
        Ticket<org.bukkit.plugin.Plugin> ticket = new Ticket<>(TicketType.PLUGIN_TICKET, ChunkLevel.byStatus(FullChunkStatus.FULL) - 2, value); // Copied from below and keep in-line with force loading, add at level 31
        final long packedChunkPos = pos.toLong();
        final Set<Ticket<?>> tickets = this.tickets.get(packedChunkPos); // Don't use getTickets, we don't want to create a new set
        if (tickets == null || !tickets.contains(ticket)) {
            return false;
        }
        this.removeTicket(packedChunkPos, ticket);
        this.tickingTicketsTracker.removeTicket(packedChunkPos, ticket);
        return true;
    }
    // Paper end

    private SortedArraySet<Ticket<?>> getTickets(long chunkPos) {
        return this.tickets.computeIfAbsent(chunkPos, l -> SortedArraySet.create(4));
    }

    protected void updateChunkForced(ChunkPos pos, boolean add) {
        Ticket<ChunkPos> ticket = new Ticket<>(TicketType.FORCED, ChunkMap.FORCED_TICKET_LEVEL, pos);
        long packedChunkPos = pos.toLong();
        if (add) {
            this.addTicket(packedChunkPos, ticket);
            this.tickingTicketsTracker.addTicket(packedChunkPos, ticket);
        } else {
            this.removeTicket(packedChunkPos, ticket);
            this.tickingTicketsTracker.removeTicket(packedChunkPos, ticket);
        }
    }

    public void addPlayer(SectionPos sectionPos, ServerPlayer player) {
        ChunkPos chunkPos = sectionPos.chunk();
        long packedChunkPos = chunkPos.toLong();
        this.playersPerChunk.computeIfAbsent(packedChunkPos, l -> new ObjectOpenHashSet<>()).add(player);
        this.naturalSpawnChunkCounter.update(packedChunkPos, 0, true);
        this.playerTicketManager.update(packedChunkPos, 0, true);
        this.tickingTicketsTracker.addTicket(TicketType.PLAYER, chunkPos, this.getPlayerTicketLevel(), chunkPos);
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
            this.naturalSpawnChunkCounter.update(packedChunkPos, Integer.MAX_VALUE, false);
            this.playerTicketManager.update(packedChunkPos, Integer.MAX_VALUE, false);
            this.tickingTicketsTracker.removeTicket(TicketType.PLAYER, chunkPos, this.getPlayerTicketLevel(), chunkPos);
        }
    }

    private int getPlayerTicketLevel() {
        return Math.max(0, ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING) - this.simulationDistance);
    }

    public boolean inEntityTickingRange(long chunkPos) {
        return ChunkLevel.isEntityTicking(this.tickingTicketsTracker.getLevel(chunkPos));
    }

    public boolean inBlockTickingRange(long chunkPos) {
        return ChunkLevel.isBlockTicking(this.tickingTicketsTracker.getLevel(chunkPos));
    }

    protected String getTicketDebugString(long chunkPos) {
        SortedArraySet<Ticket<?>> set = this.tickets.get(chunkPos);
        return set != null && !set.isEmpty() ? set.first().toString() : "no_ticket";
    }

    protected void updatePlayerTickets(int viewDistance) {
        this.playerTicketManager.updateViewDistance(viewDistance);
    }

    public void updateSimulationDistance(int simulationDistance) {
        if (simulationDistance != this.simulationDistance) {
            this.simulationDistance = simulationDistance;
            this.tickingTicketsTracker.replacePlayerTicketsLevel(this.getPlayerTicketLevel());
        }
    }

    public int getNaturalSpawnChunkCount() {
        this.naturalSpawnChunkCounter.runAllUpdates();
        return this.naturalSpawnChunkCounter.chunks.size();
    }

    public boolean hasPlayersNearby(long chunkPos) {
        this.naturalSpawnChunkCounter.runAllUpdates();
        return this.naturalSpawnChunkCounter.chunks.containsKey(chunkPos);
    }

    public LongIterator getSpawnCandidateChunks() {
        this.naturalSpawnChunkCounter.runAllUpdates();
        return this.naturalSpawnChunkCounter.chunks.keySet().iterator();
    }

    public String getDebugStatus() {
        return this.ticketDispatcher.getDebugStatus();
    }

    private void dumpTickets(String filename) {
        try (FileOutputStream fileOutputStream = new FileOutputStream(new File(filename))) {
            for (Entry<SortedArraySet<Ticket<?>>> entry : this.tickets.long2ObjectEntrySet()) {
                ChunkPos chunkPos = new ChunkPos(entry.getLongKey());

                for (Ticket<?> ticket : entry.getValue()) {
                    fileOutputStream.write(
                        (chunkPos.x + "\t" + chunkPos.z + "\t" + ticket.getType() + "\t" + ticket.getTicketLevel() + "\t\n").getBytes(StandardCharsets.UTF_8)
                    );
                }
            }
        } catch (IOException var10) {
            LOGGER.error("Failed to dump tickets to {}", filename, var10);
        }
    }

    @VisibleForTesting
    TickingTracker tickingTracker() {
        return this.tickingTicketsTracker;
    }

    public LongSet getTickingChunks() {
        return this.tickingTicketsTracker.getTickingChunks();
    }

    public void removeTicketsOnClosing() {
        ImmutableSet<TicketType<?>> set = ImmutableSet.of(TicketType.UNKNOWN, TicketType.POST_TELEPORT, TicketType.FUTURE_AWAIT); // Paper - add additional tickets to preserve
        ObjectIterator<Entry<SortedArraySet<Ticket<?>>>> objectIterator = this.tickets.long2ObjectEntrySet().fastIterator();

        while (objectIterator.hasNext()) {
            Entry<SortedArraySet<Ticket<?>>> entry = objectIterator.next();
            Iterator<Ticket<?>> iterator = entry.getValue().iterator();
            boolean flag = false;

            while (iterator.hasNext()) {
                Ticket<?> ticket = iterator.next();
                if (!set.contains(ticket.getType())) {
                    iterator.remove();
                    flag = true;
                    this.tickingTicketsTracker.removeTicket(entry.getLongKey(), ticket);
                }
            }

            if (flag) {
                this.ticketTracker.update(entry.getLongKey(), getTicketLevelAt(entry.getValue()), false);
            }

            if (entry.getValue().isEmpty()) {
                objectIterator.remove();
            }
        }
    }

    public boolean hasTickets() {
        return !this.tickets.isEmpty();
    }

    // CraftBukkit start
    public <T> void removeAllTicketsFor(TicketType<T> ticketType, int ticketLevel, T ticketIdentifier) {
        Ticket<T> target = new Ticket<>(ticketType, ticketLevel, ticketIdentifier);

        for (java.util.Iterator<Entry<SortedArraySet<Ticket<?>>>> iterator = this.tickets.long2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
            Entry<SortedArraySet<Ticket<?>>> entry = iterator.next();
            SortedArraySet<Ticket<?>> tickets = entry.getValue();
            if (tickets.remove(target)) {
                // copied from removeTicket
                this.ticketTracker.update(entry.getLongKey(), DistanceManager.getTicketLevelAt(tickets), false);

                // can't use entry after it's removed
                if (tickets.isEmpty()) {
                    iterator.remove();
                }
            }
        }
    }
    // CraftBukkit end

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
    }

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
    }
}
