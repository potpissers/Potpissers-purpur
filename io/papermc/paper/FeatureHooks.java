package io.papermc.paper;

import io.papermc.paper.command.PaperSubcommand;
import io.papermc.paper.command.subcommands.ChunkDebugCommand;
import io.papermc.paper.command.subcommands.FixLightCommand;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.bukkit.Chunk;
import org.bukkit.World;

public final class FeatureHooks {

    public static void initChunkTaskScheduler(final boolean useParallelGen) {
        ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.init(useParallelGen); // Paper - Chunk system
    }

    public static void registerPaperCommands(final Map<Set<String>, PaperSubcommand> commands) {
        commands.put(Set.of("fixlight"), new FixLightCommand()); // Paper - rewrite chunk system
        commands.put(Set.of("debug", "chunkinfo", "holderinfo"), new ChunkDebugCommand());  // Paper - rewrite chunk system
    }

    public static LevelChunkSection createSection(final Registry<Biome> biomeRegistry, final Level level, final ChunkPos chunkPos, final int chunkSection) {
        return new LevelChunkSection(biomeRegistry, level, chunkPos, chunkSection); // Paper - Anti-Xray - Add parameters
    }

    public static void sendChunkRefreshPackets(final List<ServerPlayer> playersInRange, final LevelChunk chunk) {
        // Paper start - Anti-Xray
        final Map<Object, ClientboundLevelChunkWithLightPacket> refreshPackets = new HashMap<>();
        for (final ServerPlayer player : playersInRange) {
            if (player.connection == null) continue;

            final Boolean shouldModify = chunk.getLevel().chunkPacketBlockController.shouldModify(player, chunk);
            player.connection.send(refreshPackets.computeIfAbsent(shouldModify, s -> { // Use connection to prevent creating firing event
                return new ClientboundLevelChunkWithLightPacket(chunk, chunk.level.getLightEngine(), null, null, (Boolean) s);
            }));
        }
        // Paper end - Anti-Xray
    }

    public static PalettedContainer<BlockState> emptyPalettedBlockContainer() {
        return new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES, null); // Paper - Anti-Xray - Add preset block states
    }

    public static Set<Long> getSentChunkKeys(final ServerPlayer player) {
        return LongSets.unmodifiable(player.moonrise$getChunkLoader().getSentChunksRaw().clone()); // Paper - rewrite chunk system
    }

    public static Set<Chunk> getSentChunks(final ServerPlayer player) {
        // Paper start - rewrite chunk system
        final LongOpenHashSet rawChunkKeys = player.moonrise$getChunkLoader().getSentChunksRaw();
        final ObjectSet<org.bukkit.Chunk> chunks = new ObjectOpenHashSet<>(rawChunkKeys.size());
        final World world = player.serverLevel().getWorld();
        final LongIterator iter = player.moonrise$getChunkLoader().getSentChunksRaw().longIterator();
        while (iter.hasNext()) {
            chunks.add(world.getChunkAt(iter.nextLong(), false));
        }
        // Paper end - rewrite chunk system
        return ObjectSets.unmodifiable(chunks);
    }

    public static boolean isChunkSent(final ServerPlayer player, final long chunkKey) {
        return player.getChunkTrackingView().contains(new ChunkPos(chunkKey));
    }

    public static boolean isSpiderCollidingWithWorldBorder(final Spider spider) {
        return ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.isCollidingWithBorder(spider.level().getWorldBorder(), spider.getBoundingBox().inflate(ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON)); // Paper - rewrite collision system
    }

    public static void dumpAllChunkLoadInfo(net.minecraft.server.MinecraftServer server, boolean isLongTimeout) {
        ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.dumpAllChunkLoadInfo(server, isLongTimeout); // Paper - rewrite chunk system
    }

    private static void dumpEntity(final Entity entity) {
    }

    public static org.bukkit.entity.Entity[] getChunkEntities(net.minecraft.server.level.ServerLevel world, int chunkX, int chunkZ) {
        return world.getChunkEntities(chunkX, chunkZ); // Paper - rewrite chunk system
    }

    public static java.util.Collection<org.bukkit.plugin.Plugin> getPluginChunkTickets(net.minecraft.server.level.ServerLevel world,
                                                                                       int x, int z) {
        return world.moonrise$getChunkTaskScheduler().chunkHolderManager.getPluginChunkTickets(x, z); // Paper - rewrite chunk system
    }

    public static Map<org.bukkit.plugin.Plugin, java.util.Collection<org.bukkit.Chunk>> getPluginChunkTickets(net.minecraft.server.level.ServerLevel world) {
        Map<org.bukkit.plugin.Plugin, com.google.common.collect.ImmutableList.Builder<Chunk>> ret = new HashMap<>();
        net.minecraft.server.level.DistanceManager chunkDistanceManager = world.getChunkSource().chunkMap.distanceManager;

        for (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<net.minecraft.util.SortedArraySet<net.minecraft.server.level.Ticket<?>>> chunkTickets : chunkDistanceManager.moonrise$getChunkHolderManager().getTicketsCopy().long2ObjectEntrySet()) { // Paper - rewrite chunk system
            long chunkKey = chunkTickets.getLongKey();
            net.minecraft.util.SortedArraySet<net.minecraft.server.level.Ticket<?>> tickets = chunkTickets.getValue();

            org.bukkit.Chunk chunk = null;
            for (net.minecraft.server.level.Ticket<?> ticket : tickets) {
                if (ticket.getType() != net.minecraft.server.level.TicketType.PLUGIN_TICKET) {
                    continue;
                }

                if (chunk == null) {
                    chunk = world.getWorld().getChunkAt(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
                }

                ret.computeIfAbsent((org.bukkit.plugin.Plugin) ticket.key, (key) -> com.google.common.collect.ImmutableList.builder()).add(chunk);
            }
        }

        return ret.entrySet().stream().collect(com.google.common.collect.ImmutableMap.toImmutableMap(Map.Entry::getKey, (entry) -> entry.getValue().build()));
    }

    public static int getViewDistance(net.minecraft.server.level.ServerLevel world) {
        return world.moonrise$getPlayerChunkLoader().getAPIViewDistance(); // Paper - rewrite chunk system
    }

    public static int getSimulationDistance(net.minecraft.server.level.ServerLevel world) {
        return world.moonrise$getPlayerChunkLoader().getAPITickDistance(); // Paper - rewrite chunk system
    }

    public static int getSendViewDistance(net.minecraft.server.level.ServerLevel world) {
        return world.moonrise$getPlayerChunkLoader().getAPISendViewDistance(); // Paper - rewrite chunk system
    }

    public static void setViewDistance(net.minecraft.server.level.ServerLevel world, int distance) {
        if (distance < 2 || distance > 32) {
            throw new IllegalArgumentException("View distance " + distance + " is out of range of [2, 32]");
        }
        world.chunkSource.chunkMap.setServerViewDistance(distance);
    }

    public static void setSimulationDistance(net.minecraft.server.level.ServerLevel world, int distance) {
        if (distance < 2 || distance > 32) {
            throw new IllegalArgumentException("Simulation distance " + distance + " is out of range of [2, 32]");
        }
        world.chunkSource.chunkMap.distanceManager.updateSimulationDistance(distance);
    }

    public static void setSendViewDistance(net.minecraft.server.level.ServerLevel world, int distance) {
        world.chunkSource.setSendViewDistance(distance); // Paper - rewrite chunk system
    }

    public static void tickEntityManager(net.minecraft.server.level.ServerLevel world) {
        // Paper - rewrite chunk system
    }

    public static void closeEntityManager(net.minecraft.server.level.ServerLevel world, boolean save) {
        // Paper - rewrite chunk system
    }

    public static java.util.concurrent.Executor getWorldgenExecutor() {
        return Runnable::run; // Paper - rewrite chunk system
    }

    public static void setViewDistance(ServerPlayer player, int distance) {
        ((ca.spottedleaf.moonrise.patches.chunk_system.player.ChunkSystemServerPlayer)player).moonrise$getViewDistanceHolder().setLoadViewDistance(distance == -1 ? distance : distance + 1); // Paper - rewrite chunk system
    }

    public static void setSimulationDistance(ServerPlayer player, int distance) {
        ((ca.spottedleaf.moonrise.patches.chunk_system.player.ChunkSystemServerPlayer)player).moonrise$getViewDistanceHolder().setTickViewDistance(distance); // Paper - rewrite chunk system
    }

    public static void setSendViewDistance(ServerPlayer player, int distance) {
        ((ca.spottedleaf.moonrise.patches.chunk_system.player.ChunkSystemServerPlayer)player).moonrise$getViewDistanceHolder().setSendViewDistance(distance); // Paper - rewrite chunk system
    }

}