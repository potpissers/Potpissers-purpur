package net.minecraft.world.level;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface BlockGetter extends LevelHeightAccessor {
    int MAX_BLOCK_ITERATIONS_ALONG_TRAVEL = 16;

    @Nullable
    BlockEntity getBlockEntity(BlockPos pos);

    default <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> blockEntityType) {
        BlockEntity blockEntity = this.getBlockEntity(pos);
        return blockEntity != null && blockEntity.getType() == blockEntityType ? Optional.of((T)blockEntity) : Optional.empty();
    }

    BlockState getBlockState(BlockPos pos);

    FluidState getFluidState(BlockPos pos);

    default int getLightEmission(BlockPos pos) {
        return this.getBlockState(pos).getLightEmission();
    }

    default Stream<BlockState> getBlockStates(AABB area) {
        return BlockPos.betweenClosedStream(area).map(this::getBlockState);
    }

    default BlockHitResult isBlockInLine(ClipBlockStateContext context) {
        return traverseBlocks(
            context.getFrom(),
            context.getTo(),
            context,
            (traverseContext, traversePos) -> {
                BlockState blockState = this.getBlockState(traversePos);
                Vec3 vec3 = traverseContext.getFrom().subtract(traverseContext.getTo());
                return traverseContext.isTargetBlock().test(blockState)
                    ? new BlockHitResult(
                        traverseContext.getTo(), Direction.getApproximateNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(traverseContext.getTo()), false
                    )
                    : null;
            },
            failContext -> {
                Vec3 vec3 = failContext.getFrom().subtract(failContext.getTo());
                return BlockHitResult.miss(
                    failContext.getTo(), Direction.getApproximateNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(failContext.getTo())
                );
            }
        );
    }

    default BlockHitResult clip(ClipContext context) {
        return traverseBlocks(context.getFrom(), context.getTo(), context, (traverseContext, traversePos) -> {
            BlockState blockState = this.getBlockState(traversePos);
            FluidState fluidState = this.getFluidState(traversePos);
            Vec3 from = traverseContext.getFrom();
            Vec3 to = traverseContext.getTo();
            VoxelShape blockShape = traverseContext.getBlockShape(blockState, this, traversePos);
            BlockHitResult blockHitResult = this.clipWithInteractionOverride(from, to, traversePos, blockShape, blockState);
            VoxelShape fluidShape = traverseContext.getFluidShape(fluidState, this, traversePos);
            BlockHitResult blockHitResult1 = fluidShape.clip(from, to, traversePos);
            double d = blockHitResult == null ? Double.MAX_VALUE : traverseContext.getFrom().distanceToSqr(blockHitResult.getLocation());
            double d1 = blockHitResult1 == null ? Double.MAX_VALUE : traverseContext.getFrom().distanceToSqr(blockHitResult1.getLocation());
            return d <= d1 ? blockHitResult : blockHitResult1;
        }, failContext -> {
            Vec3 vec3 = failContext.getFrom().subtract(failContext.getTo());
            return BlockHitResult.miss(failContext.getTo(), Direction.getApproximateNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(failContext.getTo()));
        });
    }

    @Nullable
    default BlockHitResult clipWithInteractionOverride(Vec3 startVec, Vec3 endVec, BlockPos pos, VoxelShape shape, BlockState state) {
        BlockHitResult blockHitResult = shape.clip(startVec, endVec, pos);
        if (blockHitResult != null) {
            BlockHitResult blockHitResult1 = state.getInteractionShape(this, pos).clip(startVec, endVec, pos);
            if (blockHitResult1 != null
                && blockHitResult1.getLocation().subtract(startVec).lengthSqr() < blockHitResult.getLocation().subtract(startVec).lengthSqr()) {
                return blockHitResult.withDirection(blockHitResult1.getDirection());
            }
        }

        return blockHitResult;
    }

    default double getBlockFloorHeight(VoxelShape shape, Supplier<VoxelShape> belowShapeSupplier) {
        if (!shape.isEmpty()) {
            return shape.max(Direction.Axis.Y);
        } else {
            double d = belowShapeSupplier.get().max(Direction.Axis.Y);
            return d >= 1.0 ? d - 1.0 : Double.NEGATIVE_INFINITY;
        }
    }

    default double getBlockFloorHeight(BlockPos pos) {
        return this.getBlockFloorHeight(this.getBlockState(pos).getCollisionShape(this, pos), () -> {
            BlockPos blockPos = pos.below();
            return this.getBlockState(blockPos).getCollisionShape(this, blockPos);
        });
    }

    static <T, C> T traverseBlocks(Vec3 from, Vec3 to, C context, BiFunction<C, BlockPos, T> tester, Function<C, T> onFail) {
        if (from.equals(to)) {
            return onFail.apply(context);
        } else {
            double d = Mth.lerp(-1.0E-7, to.x, from.x);
            double d1 = Mth.lerp(-1.0E-7, to.y, from.y);
            double d2 = Mth.lerp(-1.0E-7, to.z, from.z);
            double d3 = Mth.lerp(-1.0E-7, from.x, to.x);
            double d4 = Mth.lerp(-1.0E-7, from.y, to.y);
            double d5 = Mth.lerp(-1.0E-7, from.z, to.z);
            int floor = Mth.floor(d3);
            int floor1 = Mth.floor(d4);
            int floor2 = Mth.floor(d5);
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(floor, floor1, floor2);
            T object = tester.apply(context, mutableBlockPos);
            if (object != null) {
                return object;
            } else {
                double d6 = d - d3;
                double d7 = d1 - d4;
                double d8 = d2 - d5;
                int i = Mth.sign(d6);
                int i1 = Mth.sign(d7);
                int i2 = Mth.sign(d8);
                double d9 = i == 0 ? Double.MAX_VALUE : i / d6;
                double d10 = i1 == 0 ? Double.MAX_VALUE : i1 / d7;
                double d11 = i2 == 0 ? Double.MAX_VALUE : i2 / d8;
                double d12 = d9 * (i > 0 ? 1.0 - Mth.frac(d3) : Mth.frac(d3));
                double d13 = d10 * (i1 > 0 ? 1.0 - Mth.frac(d4) : Mth.frac(d4));
                double d14 = d11 * (i2 > 0 ? 1.0 - Mth.frac(d5) : Mth.frac(d5));

                while (d12 <= 1.0 || d13 <= 1.0 || d14 <= 1.0) {
                    if (d12 < d13) {
                        if (d12 < d14) {
                            floor += i;
                            d12 += d9;
                        } else {
                            floor2 += i2;
                            d14 += d11;
                        }
                    } else if (d13 < d14) {
                        floor1 += i1;
                        d13 += d10;
                    } else {
                        floor2 += i2;
                        d14 += d11;
                    }

                    T object1 = tester.apply(context, mutableBlockPos.set(floor, floor1, floor2));
                    if (object1 != null) {
                        return object1;
                    }
                }

                return onFail.apply(context);
            }
        }
    }

    static Iterable<BlockPos> boxTraverseBlocks(Vec3 oldPosition, Vec3 position, AABB boundingBox) {
        Vec3 vec3 = position.subtract(oldPosition);
        Iterable<BlockPos> iterable = BlockPos.betweenClosed(boundingBox);
        if (vec3.lengthSqr() < Mth.square(0.99999F)) {
            return iterable;
        } else {
            Set<BlockPos> set = new ObjectLinkedOpenHashSet<>();
            Vec3 minPosition = boundingBox.getMinPosition();
            Vec3 vec31 = minPosition.subtract(vec3);
            addCollisionsAlongTravel(set, vec31, minPosition, boundingBox);

            for (BlockPos blockPos : iterable) {
                set.add(blockPos.immutable());
            }

            return set;
        }
    }

    private static void addCollisionsAlongTravel(Set<BlockPos> output, Vec3 start, Vec3 end, AABB boundingBox) {
        Vec3 vec3 = end.subtract(start);
        int floor = Mth.floor(start.x);
        int floor1 = Mth.floor(start.y);
        int floor2 = Mth.floor(start.z);
        int i = Mth.sign(vec3.x);
        int i1 = Mth.sign(vec3.y);
        int i2 = Mth.sign(vec3.z);
        double d = i == 0 ? Double.MAX_VALUE : i / vec3.x;
        double d1 = i1 == 0 ? Double.MAX_VALUE : i1 / vec3.y;
        double d2 = i2 == 0 ? Double.MAX_VALUE : i2 / vec3.z;
        double d3 = d * (i > 0 ? 1.0 - Mth.frac(start.x) : Mth.frac(start.x));
        double d4 = d1 * (i1 > 0 ? 1.0 - Mth.frac(start.y) : Mth.frac(start.y));
        double d5 = d2 * (i2 > 0 ? 1.0 - Mth.frac(start.z) : Mth.frac(start.z));
        int i3 = 0;

        while (d3 <= 1.0 || d4 <= 1.0 || d5 <= 1.0) {
            if (d3 < d4) {
                if (d3 < d5) {
                    floor += i;
                    d3 += d;
                } else {
                    floor2 += i2;
                    d5 += d2;
                }
            } else if (d4 < d5) {
                floor1 += i1;
                d4 += d1;
            } else {
                floor2 += i2;
                d5 += d2;
            }

            if (i3++ > 16) {
                break;
            }

            Optional<Vec3> optional = AABB.clip(floor, floor1, floor2, floor + 1, floor1 + 1, floor2 + 1, start, end);
            if (!optional.isEmpty()) {
                Vec3 vec31 = optional.get();
                double d6 = Mth.clamp(vec31.x, floor + 1.0E-5F, floor + 1.0 - 1.0E-5F);
                double d7 = Mth.clamp(vec31.y, floor1 + 1.0E-5F, floor1 + 1.0 - 1.0E-5F);
                double d8 = Mth.clamp(vec31.z, floor2 + 1.0E-5F, floor2 + 1.0 - 1.0E-5F);
                int floor3 = Mth.floor(d6 + boundingBox.getXsize());
                int floor4 = Mth.floor(d7 + boundingBox.getYsize());
                int floor5 = Mth.floor(d8 + boundingBox.getZsize());

                for (int i4 = floor; i4 <= floor3; i4++) {
                    for (int i5 = floor1; i5 <= floor4; i5++) {
                        for (int i6 = floor2; i6 <= floor5; i6++) {
                            output.add(new BlockPos(i4, i5, i6));
                        }
                    }
                }
            }
        }
    }
}
