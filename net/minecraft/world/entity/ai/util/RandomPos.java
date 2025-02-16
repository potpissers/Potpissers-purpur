package net.minecraft.world.entity.ai.util;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

public class RandomPos {
    private static final int RANDOM_POS_ATTEMPTS = 10;

    public static BlockPos generateRandomDirection(RandomSource random, int horizontalDistance, int verticalDistance) {
        int i = random.nextInt(2 * horizontalDistance + 1) - horizontalDistance;
        int i1 = random.nextInt(2 * verticalDistance + 1) - verticalDistance;
        int i2 = random.nextInt(2 * horizontalDistance + 1) - horizontalDistance;
        return new BlockPos(i, i1, i2);
    }

    @Nullable
    public static BlockPos generateRandomDirectionWithinRadians(
        RandomSource random, int maxHorizontalDifference, int yRange, int y, double x, double z, double maxAngleDelta
    ) {
        double d = Mth.atan2(z, x) - (float) (Math.PI / 2);
        double d1 = d + (2.0F * random.nextFloat() - 1.0F) * maxAngleDelta;
        double d2 = Math.sqrt(random.nextDouble()) * Mth.SQRT_OF_TWO * maxHorizontalDifference;
        double d3 = -d2 * Math.sin(d1);
        double d4 = d2 * Math.cos(d1);
        if (!(Math.abs(d3) > maxHorizontalDifference) && !(Math.abs(d4) > maxHorizontalDifference)) {
            int i = random.nextInt(2 * yRange + 1) - yRange + y;
            return BlockPos.containing(d3, i, d4);
        } else {
            return null;
        }
    }

    @VisibleForTesting
    public static BlockPos moveUpOutOfSolid(BlockPos pos, int maxY, Predicate<BlockPos> posPredicate) {
        if (!posPredicate.test(pos)) {
            return pos;
        } else {
            BlockPos.MutableBlockPos mutableBlockPos = pos.mutable().move(Direction.UP);

            while (mutableBlockPos.getY() <= maxY && posPredicate.test(mutableBlockPos)) {
                mutableBlockPos.move(Direction.UP);
            }

            return mutableBlockPos.immutable();
        }
    }

    @VisibleForTesting
    public static BlockPos moveUpToAboveSolid(BlockPos pos, int aboveSolidAmount, int maxY, Predicate<BlockPos> posPredicate) {
        if (aboveSolidAmount < 0) {
            throw new IllegalArgumentException("aboveSolidAmount was " + aboveSolidAmount + ", expected >= 0");
        } else if (!posPredicate.test(pos)) {
            return pos;
        } else {
            BlockPos.MutableBlockPos mutableBlockPos = pos.mutable().move(Direction.UP);

            while (mutableBlockPos.getY() <= maxY && posPredicate.test(mutableBlockPos)) {
                mutableBlockPos.move(Direction.UP);
            }

            int y = mutableBlockPos.getY();

            while (mutableBlockPos.getY() <= maxY && mutableBlockPos.getY() - y < aboveSolidAmount) {
                mutableBlockPos.move(Direction.UP);
                if (posPredicate.test(mutableBlockPos)) {
                    mutableBlockPos.move(Direction.DOWN);
                    break;
                }
            }

            return mutableBlockPos.immutable();
        }
    }

    @Nullable
    public static Vec3 generateRandomPos(PathfinderMob mob, Supplier<BlockPos> posSupplier) {
        return generateRandomPos(posSupplier, mob::getWalkTargetValue);
    }

    @Nullable
    public static Vec3 generateRandomPos(Supplier<BlockPos> posSupplier, ToDoubleFunction<BlockPos> toDoubleFunction) {
        double d = Double.NEGATIVE_INFINITY;
        BlockPos blockPos = null;

        for (int i = 0; i < 10; i++) {
            BlockPos blockPos1 = posSupplier.get();
            if (blockPos1 != null) {
                double d1 = toDoubleFunction.applyAsDouble(blockPos1);
                if (d1 > d) {
                    d = d1;
                    blockPos = blockPos1;
                }
            }
        }

        return blockPos != null ? Vec3.atBottomCenterOf(blockPos) : null;
    }

    public static BlockPos generateRandomPosTowardDirection(PathfinderMob mob, int range, RandomSource random, BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        if (mob.hasRestriction() && range > 1) {
            BlockPos restrictCenter = mob.getRestrictCenter();
            if (mob.getX() > restrictCenter.getX()) {
                x -= random.nextInt(range / 2);
            } else {
                x += random.nextInt(range / 2);
            }

            if (mob.getZ() > restrictCenter.getZ()) {
                z -= random.nextInt(range / 2);
            } else {
                z += random.nextInt(range / 2);
            }
        }

        return BlockPos.containing(x + mob.getX(), pos.getY() + mob.getY(), z + mob.getZ());
    }
}
