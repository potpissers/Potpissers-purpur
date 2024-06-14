package net.minecraft.world.level.chunk.status;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.Blender;

public class ChunkStatusTasks {
    private static boolean isLighted(ChunkAccess chunk) {
        return chunk.getPersistedStatus().isOrAfter(ChunkStatus.LIGHT) && chunk.isLightCorrect();
    }

    static CompletableFuture<ChunkAccess> passThrough(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateStructureStarts(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        ServerLevel serverLevel = worldGenContext.level();
        if (serverLevel.serverLevelData.worldGenOptions().generateStructures()) { // CraftBukkit
            worldGenContext.generator()
                .createStructures(
                    serverLevel.registryAccess(),
                    serverLevel.getChunkSource().getGeneratorState(),
                    serverLevel.structureManager(),
                    chunk,
                    worldGenContext.structureManager(),
                    serverLevel.dimension()
                );
        }

        serverLevel.onStructureStartsAvailable(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> loadStructureStarts(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        worldGenContext.level().onStructureStartsAvailable(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateStructureReferences(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        ServerLevel serverLevel = worldGenContext.level();
        WorldGenRegion worldGenRegion = new WorldGenRegion(serverLevel, cache, step, chunk);
        worldGenContext.generator().createReferences(worldGenRegion, serverLevel.structureManager().forWorldGenRegion(worldGenRegion), chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateBiomes(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        ServerLevel serverLevel = worldGenContext.level();
        WorldGenRegion worldGenRegion = new WorldGenRegion(serverLevel, cache, step, chunk);
        return worldGenContext.generator()
            .createBiomes(
                serverLevel.getChunkSource().randomState(), Blender.of(worldGenRegion), serverLevel.structureManager().forWorldGenRegion(worldGenRegion), chunk
            );
    }

    static CompletableFuture<ChunkAccess> generateNoise(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        ServerLevel serverLevel = worldGenContext.level();
        WorldGenRegion worldGenRegion = new WorldGenRegion(serverLevel, cache, step, chunk);
        return worldGenContext.generator()
            .fillFromNoise(
                Blender.of(worldGenRegion), serverLevel.getChunkSource().randomState(), serverLevel.structureManager().forWorldGenRegion(worldGenRegion), chunk
            )
            .thenApply(chunkAccess -> {
                if (chunkAccess instanceof ProtoChunk protoChunk) {
                    BelowZeroRetrogen belowZeroRetrogen = protoChunk.getBelowZeroRetrogen();
                    if (belowZeroRetrogen != null) {
                        BelowZeroRetrogen.replaceOldBedrock(protoChunk);
                        if (belowZeroRetrogen.hasBedrockHoles()) {
                            belowZeroRetrogen.applyBedrockMask(protoChunk);
                        }
                    }
                }

                return (ChunkAccess)chunkAccess;
            });
    }

    static CompletableFuture<ChunkAccess> generateSurface(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        ServerLevel serverLevel = worldGenContext.level();
        WorldGenRegion worldGenRegion = new WorldGenRegion(serverLevel, cache, step, chunk);
        worldGenContext.generator()
            .buildSurface(worldGenRegion, serverLevel.structureManager().forWorldGenRegion(worldGenRegion), serverLevel.getChunkSource().randomState(), chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateCarvers(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        ServerLevel serverLevel = worldGenContext.level();
        WorldGenRegion worldGenRegion = new WorldGenRegion(serverLevel, cache, step, chunk);
        if (chunk instanceof ProtoChunk protoChunk) {
            Blender.addAroundOldChunksCarvingMaskFilter(worldGenRegion, protoChunk);
        }

        worldGenContext.generator()
            .applyCarvers(
                worldGenRegion,
                serverLevel.getSeed(),
                serverLevel.getChunkSource().randomState(),
                serverLevel.getBiomeManager(),
                serverLevel.structureManager().forWorldGenRegion(worldGenRegion),
                chunk
            );
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateFeatures(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        ServerLevel serverLevel = worldGenContext.level();
        Heightmap.primeHeightmaps(
            chunk,
            EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE)
        );
        WorldGenRegion worldGenRegion = new WorldGenRegion(serverLevel, cache, step, chunk);
        worldGenContext.generator().applyBiomeDecoration(worldGenRegion, chunk, serverLevel.structureManager().forWorldGenRegion(worldGenRegion));
        Blender.generateBorderTicks(worldGenRegion, chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> initializeLight(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        ThreadedLevelLightEngine threadedLevelLightEngine = worldGenContext.lightEngine();
        chunk.initializeLightSources();
        ((ProtoChunk)chunk).setLightEngine(threadedLevelLightEngine);
        boolean isLighted = isLighted(chunk);
        return threadedLevelLightEngine.initializeLight(chunk, isLighted);
    }

    static CompletableFuture<ChunkAccess> light(WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk) {
        boolean isLighted = isLighted(chunk);
        return worldGenContext.lightEngine().lightChunk(chunk, isLighted);
    }

    static CompletableFuture<ChunkAccess> generateSpawn(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        if (!chunk.isUpgrading()) {
            worldGenContext.generator().spawnOriginalMobs(new WorldGenRegion(worldGenContext.level(), cache, step, chunk));
        }

        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> full(WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        GenerationChunkHolder generationChunkHolder = cache.get(pos.x, pos.z);
        return CompletableFuture.supplyAsync(() -> {
            ProtoChunk protoChunk = (ProtoChunk)chunk;
            ServerLevel serverLevel = worldGenContext.level();
            LevelChunk wrapped;
            if (protoChunk instanceof ImposterProtoChunk imposterProtoChunk) {
                wrapped = imposterProtoChunk.getWrapped();
            } else {
                wrapped = new LevelChunk(serverLevel, protoChunk, chunk1 -> postLoadProtoChunk(serverLevel, protoChunk.getEntities(), protoChunk.getPos())); // Paper - rewrite chunk system
                generationChunkHolder.replaceProtoChunk(new ImposterProtoChunk(wrapped, false));
            }

            wrapped.setFullStatus(generationChunkHolder::getFullStatus);
            wrapped.runPostLoad();
            wrapped.setLoaded(true);
            wrapped.registerAllBlockEntitiesAfterLevelLoad();
            wrapped.registerTickContainerInLevel(serverLevel);
            wrapped.setUnsavedListener(worldGenContext.unsavedListener());
            return wrapped;
        }, worldGenContext.mainThreadExecutor());
    }

    public static void postLoadProtoChunk(ServerLevel level, List<CompoundTag> entityTags, ChunkPos pos) { // Paper - public // Paper - rewrite chunk system - add ChunkPos param
        if (!entityTags.isEmpty()) {
            // CraftBukkit start - these are spawned serialized (DefinedStructure) and we don't call an add event below at the moment due to ordering complexities
            level.addWorldGenChunkEntities(EntityType.loadEntitiesRecursive(entityTags, level, EntitySpawnReason.LOAD).filter((entity) -> {
                boolean needsRemoval = false;
                net.minecraft.server.dedicated.DedicatedServer server = level.getCraftServer().getServer();
                if (!level.getChunkSource().spawnFriendlies && (entity instanceof net.minecraft.world.entity.animal.Animal || entity instanceof net.minecraft.world.entity.animal.WaterAnimal)) {
                    entity.discard(null); // CraftBukkit - add Bukkit remove cause
                    needsRemoval = true;
                }
                checkDupeUUID(level, entity); // Paper - duplicate uuid resolving
                return !needsRemoval;
            }), pos); // Paper - rewrite chunk system
            // CraftBukkit end
        }
    }

    // Paper start - duplicate uuid resolving
    // rets true if to prevent the entity from being added
    public static boolean checkDupeUUID(ServerLevel level, net.minecraft.world.entity.Entity entity) {
        io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.DuplicateUUID.DuplicateUUIDMode mode = level.paperConfig().entities.spawning.duplicateUuid.mode;
        if (mode != io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.DuplicateUUID.DuplicateUUIDMode.WARN
            && mode != io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.DuplicateUUID.DuplicateUUIDMode.DELETE
            && mode != io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.DuplicateUUID.DuplicateUUIDMode.SAFE_REGEN) {
            return false;
        }
        net.minecraft.world.entity.Entity other = level.getEntity(entity.getUUID());

        if (other == null || other == entity) {
            return false;
        }

        if (mode == io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.DuplicateUUID.DuplicateUUIDMode.SAFE_REGEN && other != null && !other.isRemoved()
            && java.util.Objects.equals(other.getEncodeId(), entity.getEncodeId())
            && entity.getBukkitEntity().getLocation().distance(other.getBukkitEntity().getLocation()) < level.paperConfig().entities.spawning.duplicateUuid.safeRegenDeleteRange
        ) {
            entity.discard(null);
            return true;
        }
        if (!other.isRemoved()) {
            switch (mode) {
                case SAFE_REGEN: {
                    entity.setUUID(java.util.UUID.randomUUID());
                    break;
                }
                case DELETE: {
                    entity.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DISCARD);
                    return true;
                }
                default:
                    break;
            }
        }
        return false;
    }
    // Paper end - duplicate uuid resolving
}
