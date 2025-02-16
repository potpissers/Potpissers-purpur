package net.minecraft.server.level;

import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public class PlayerRespawnLogic {
    @Nullable
    protected static BlockPos getOverworldRespawnPos(ServerLevel level, int x, int z) {
        boolean hasCeiling = level.dimensionType().hasCeiling();
        LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        int i = hasCeiling ? level.getChunkSource().getGenerator().getSpawnHeight(level) : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15);
        if (i < level.getMinY()) {
            return null;
        } else {
            int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
            if (height <= i && height > chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15)) {
                return null;
            } else {
                BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

                for (int i1 = i + 1; i1 >= level.getMinY(); i1--) {
                    mutableBlockPos.set(x, i1, z);
                    BlockState blockState = level.getBlockState(mutableBlockPos);
                    if (!blockState.getFluidState().isEmpty()) {
                        break;
                    }

                    if (Block.isFaceFull(blockState.getCollisionShape(level, mutableBlockPos), Direction.UP)) {
                        return mutableBlockPos.above().immutable();
                    }
                }

                return null;
            }
        }
    }

    @Nullable
    public static BlockPos getSpawnPosInChunk(ServerLevel level, ChunkPos chunkPos) {
        if (SharedConstants.debugVoidTerrain(chunkPos)) {
            return null;
        } else {
            for (int blockX = chunkPos.getMinBlockX(); blockX <= chunkPos.getMaxBlockX(); blockX++) {
                for (int blockZ = chunkPos.getMinBlockZ(); blockZ <= chunkPos.getMaxBlockZ(); blockZ++) {
                    BlockPos overworldRespawnPos = getOverworldRespawnPos(level, blockX, blockZ);
                    if (overworldRespawnPos != null) {
                        return overworldRespawnPos;
                    }
                }
            }

            return null;
        }
    }
}
