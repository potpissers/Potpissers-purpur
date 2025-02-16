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
        if (serverLevel.getServer().getWorldData().worldGenOptions().generateStructures()) {
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
                wrapped = new LevelChunk(serverLevel, protoChunk, chunk1 -> postLoadProtoChunk(serverLevel, protoChunk.getEntities()));
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

    private static void postLoadProtoChunk(ServerLevel level, List<CompoundTag> entityTags) {
        if (!entityTags.isEmpty()) {
            level.addWorldGenChunkEntities(EntityType.loadEntitiesRecursive(entityTags, level, EntitySpawnReason.LOAD));
        }
    }
}
