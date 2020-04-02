package net.minecraft.world.level;

import com.google.common.collect.AbstractIterator;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockCollisions<T> extends AbstractIterator<T> {
    private final AABB box;
    private final CollisionContext context;
    private final Cursor3D cursor;
    private final BlockPos.MutableBlockPos pos;
    private final VoxelShape entityShape;
    private final CollisionGetter collisionGetter;
    private final boolean onlySuffocatingBlocks;
    @Nullable
    private BlockGetter cachedBlockGetter;
    private long cachedBlockGetterPos;
    private final BiFunction<BlockPos.MutableBlockPos, VoxelShape, T> resultProvider;

    public BlockCollisions(
        CollisionGetter collisionGetter,
        @Nullable Entity entity,
        AABB box,
        boolean onlySuffocatingBlocks,
        BiFunction<BlockPos.MutableBlockPos, VoxelShape, T> resultProvider
    ) {
        this(collisionGetter, entity == null ? CollisionContext.empty() : CollisionContext.of(entity), box, onlySuffocatingBlocks, resultProvider);
    }

    public BlockCollisions(
        CollisionGetter collisionGetter,
        CollisionContext context,
        AABB box,
        boolean onlySuffocatingBlocks,
        BiFunction<BlockPos.MutableBlockPos, VoxelShape, T> resultProvider
    ) {
        this.context = context;
        this.pos = new BlockPos.MutableBlockPos();
        this.entityShape = Shapes.create(box);
        this.collisionGetter = collisionGetter;
        this.box = box;
        this.onlySuffocatingBlocks = onlySuffocatingBlocks;
        this.resultProvider = resultProvider;
        int i = Mth.floor(box.minX - 1.0E-7) - 1;
        int i1 = Mth.floor(box.maxX + 1.0E-7) + 1;
        int i2 = Mth.floor(box.minY - 1.0E-7) - 1;
        int i3 = Mth.floor(box.maxY + 1.0E-7) + 1;
        int i4 = Mth.floor(box.minZ - 1.0E-7) - 1;
        int i5 = Mth.floor(box.maxZ + 1.0E-7) + 1;
        this.cursor = new Cursor3D(i, i2, i4, i1, i3, i5);
    }

    @Nullable
    private BlockGetter getChunk(int x, int z) {
        int sectionPosX = SectionPos.blockToSectionCoord(x);
        int sectionPosZ = SectionPos.blockToSectionCoord(z);
        long packedChunkPos = ChunkPos.asLong(sectionPosX, sectionPosZ);
        if (this.cachedBlockGetter != null && this.cachedBlockGetterPos == packedChunkPos) {
            return this.cachedBlockGetter;
        } else {
            BlockGetter chunkForCollisions = this.collisionGetter.getChunkForCollisions(sectionPosX, sectionPosZ);
            this.cachedBlockGetter = chunkForCollisions;
            this.cachedBlockGetterPos = packedChunkPos;
            return chunkForCollisions;
        }
    }

    @Override
    protected T computeNext() {
        while (this.cursor.advance()) {
            int i = this.cursor.nextX(); final int x = i; // Paper - OBFHELPER
            int i1 = this.cursor.nextY(); final int y = i1; // Paper - OBFHELPER
            int i2 = this.cursor.nextZ(); final int z = i2; // Paper - OBFHELPER
            int nextType = this.cursor.getNextType();
            if (nextType != 3) {
                // Paper start - ensure we don't load chunks
                // BlockGetter blockGetter = this.getChunk(i, k);
                if (true) {
                    @Nullable final Entity source = this.context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext entityContext ? entityContext.getEntity() : null;
                    final boolean far = source != null && io.papermc.paper.util.MCUtil.distanceSq(source.getX(), y, source.getZ(), x, y, z) > 14;
                    this.pos.set(x, y, z);
                    BlockState blockState;
                    if (this.collisionGetter instanceof net.minecraft.server.level.WorldGenRegion) {
                        BlockGetter blockGetter = this.getChunk(x, z);
                        if (blockGetter == null) {
                            continue;
                        }
                        blockState = blockGetter.getBlockState(this.pos);
                    } else if ((!far && source instanceof net.minecraft.server.level.ServerPlayer) || (source != null && source.collisionLoadChunks)) {
                        blockState = this.collisionGetter.getBlockState(this.pos);
                    } else {
                        blockState = this.collisionGetter.getBlockStateIfLoaded(this.pos);
                    }
                    if (blockState == null) {
                        if (!(source instanceof net.minecraft.server.level.ServerPlayer) || source.level().paperConfig().chunks.preventMovingIntoUnloadedChunks) {
                            return this.resultProvider.apply(new BlockPos.MutableBlockPos(x, y, z), Shapes.create(far ? source.getBoundingBox() : new AABB(new BlockPos(x, y, z))));
                        }
                        continue;
                    }
                    if (true // onlySuffocatingBlocks is only true on the client, so we don't care about it here
                    // Paper end - ensure we don't load chunks
                        && (nextType != 1 || blockState.hasLargeCollisionShape())
                        && (nextType != 2 || blockState.is(Blocks.MOVING_PISTON))) {
                        VoxelShape collisionShape = this.context.getCollisionShape(blockState, this.collisionGetter, this.pos);
                        if (collisionShape == Shapes.block()) {
                            if (this.box.intersects(i, i1, i2, i + 1.0, i1 + 1.0, i2 + 1.0)) {
                                return this.resultProvider.apply(this.pos, collisionShape.move(i, i1, i2));
                            }
                        } else {
                            VoxelShape voxelShape = collisionShape.move(i, i1, i2);
                            if (!voxelShape.isEmpty() && Shapes.joinIsNotEmpty(voxelShape, this.entityShape, BooleanOp.AND)) {
                                return this.resultProvider.apply(this.pos, voxelShape);
                            }
                        }
                    }
                }
            }
        }

        return this.endOfData();
    }
}
