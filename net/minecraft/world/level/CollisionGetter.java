package net.minecraft.world.level;

import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface CollisionGetter extends BlockGetter {
    WorldBorder getWorldBorder();

    @Nullable
    BlockGetter getChunkForCollisions(int chunkX, int chunkZ);

    default boolean isUnobstructed(@Nullable Entity entity, VoxelShape shape) {
        return true;
    }

    default boolean isUnobstructed(BlockState state, BlockPos pos, CollisionContext context) {
        VoxelShape collisionShape = state.getCollisionShape(this, pos, context);
        return collisionShape.isEmpty() || this.isUnobstructed(null, collisionShape.move(pos.getX(), pos.getY(), pos.getZ()));
    }

    default boolean isUnobstructed(Entity entity) {
        return this.isUnobstructed(entity, Shapes.create(entity.getBoundingBox()));
    }

    default boolean noCollision(AABB collisionBox) {
        return this.noCollision(null, collisionBox);
    }

    default boolean noCollision(Entity entity) {
        return this.noCollision(entity, entity.getBoundingBox());
    }

    default boolean noCollision(@Nullable Entity entity, AABB collisionBox) {
        return this.noCollision(entity, collisionBox, false);
    }

    default boolean noCollision(@Nullable Entity entity, AABB collisionBox, boolean checkLiquid) {
        for (VoxelShape voxelShape : checkLiquid ? this.getBlockAndLiquidCollisions(entity, collisionBox) : this.getBlockCollisions(entity, collisionBox)) {
            if (!voxelShape.isEmpty()) {
                return false;
            }
        }

        if (!this.getEntityCollisions(entity, collisionBox).isEmpty()) {
            return false;
        } else if (entity == null) {
            return true;
        } else {
            VoxelShape voxelShape1 = this.borderCollision(entity, collisionBox);
            return voxelShape1 == null || !Shapes.joinIsNotEmpty(voxelShape1, Shapes.create(collisionBox), BooleanOp.AND);
        }
    }

    default boolean noBlockCollision(@Nullable Entity entity, AABB boundingBox) {
        for (VoxelShape voxelShape : this.getBlockCollisions(entity, boundingBox)) {
            if (!voxelShape.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB collisionBox);

    default Iterable<VoxelShape> getCollisions(@Nullable Entity entity, AABB collisionBox) {
        List<VoxelShape> entityCollisions = this.getEntityCollisions(entity, collisionBox);
        Iterable<VoxelShape> blockCollisions = this.getBlockCollisions(entity, collisionBox);
        return entityCollisions.isEmpty() ? blockCollisions : Iterables.concat(entityCollisions, blockCollisions);
    }

    default Iterable<VoxelShape> getBlockCollisions(@Nullable Entity entity, AABB collisionBox) {
        return () -> new BlockCollisions<>(this, entity, collisionBox, false, (mutableBlockPos, voxelShape) -> voxelShape);
    }

    default Iterable<VoxelShape> getBlockAndLiquidCollisions(@Nullable Entity entity, AABB collisionBox) {
        return () -> new BlockCollisions<>(this, CollisionContext.of(entity, true), collisionBox, false, (mutableBlockPos, voxelShape) -> voxelShape);
    }

    @Nullable
    private VoxelShape borderCollision(Entity entity, AABB box) {
        WorldBorder worldBorder = this.getWorldBorder();
        return worldBorder.isInsideCloseToBorder(entity, box) ? worldBorder.getCollisionShape() : null;
    }

    default BlockHitResult clipIncludingBorder(ClipContext clipContext) {
        BlockHitResult blockHitResult = this.clip(clipContext);
        WorldBorder worldBorder = this.getWorldBorder();
        if (worldBorder.isWithinBounds(clipContext.getFrom()) && !worldBorder.isWithinBounds(blockHitResult.getLocation())) {
            Vec3 vec3 = blockHitResult.getLocation().subtract(clipContext.getFrom());
            Direction approximateNearest = Direction.getApproximateNearest(vec3.x, vec3.y, vec3.z);
            Vec3 vec31 = worldBorder.clampVec3ToBound(blockHitResult.getLocation());
            return new BlockHitResult(vec31, approximateNearest, BlockPos.containing(vec31), false, true);
        } else {
            return blockHitResult;
        }
    }

    default boolean collidesWithSuffocatingBlock(@Nullable Entity entity, AABB box) {
        BlockCollisions<VoxelShape> blockCollisions = new BlockCollisions<>(this, entity, box, true, (mutableBlockPos, voxelShape) -> voxelShape);

        while (blockCollisions.hasNext()) {
            if (!blockCollisions.next().isEmpty()) {
                return true;
            }
        }

        return false;
    }

    default Optional<BlockPos> findSupportingBlock(Entity entity, AABB box) {
        BlockPos blockPos = null;
        double d = Double.MAX_VALUE;
        BlockCollisions<BlockPos> blockCollisions = new BlockCollisions<>(this, entity, box, false, (mutableBlockPos, voxelShape) -> mutableBlockPos);

        while (blockCollisions.hasNext()) {
            BlockPos blockPos1 = blockCollisions.next();
            double d1 = blockPos1.distToCenterSqr(entity.position());
            if (d1 < d || d1 == d && (blockPos == null || blockPos.compareTo(blockPos1) < 0)) {
                blockPos = blockPos1.immutable();
                d = d1;
            }
        }

        return Optional.ofNullable(blockPos);
    }

    default Optional<Vec3> findFreePosition(@Nullable Entity entity, VoxelShape shape, Vec3 pos, double x, double y, double z) {
        if (shape.isEmpty()) {
            return Optional.empty();
        } else {
            AABB aabb = shape.bounds().inflate(x, y, z);
            VoxelShape voxelShape = StreamSupport.stream(this.getBlockCollisions(entity, aabb).spliterator(), false)
                .filter(voxelShape2 -> this.getWorldBorder() == null || this.getWorldBorder().isWithinBounds(voxelShape2.bounds()))
                .flatMap(voxelShape2 -> voxelShape2.toAabbs().stream())
                .map(box -> box.inflate(x / 2.0, y / 2.0, z / 2.0))
                .map(Shapes::create)
                .reduce(Shapes.empty(), Shapes::or);
            VoxelShape voxelShape1 = Shapes.join(shape, voxelShape, BooleanOp.ONLY_FIRST);
            return voxelShape1.closestPointTo(pos);
        }
    }
}
