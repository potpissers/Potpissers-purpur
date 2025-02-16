package net.minecraft.world.entity.ai.util;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

public class DefaultRandomPos {
    @Nullable
    public static Vec3 getPos(PathfinderMob mob, int radius, int verticalDistance) {
        boolean flag = GoalUtils.mobRestricted(mob, radius);
        return RandomPos.generateRandomPos(mob, () -> {
            BlockPos blockPos = RandomPos.generateRandomDirection(mob.getRandom(), radius, verticalDistance);
            return generateRandomPosTowardDirection(mob, radius, flag, blockPos);
        });
    }

    @Nullable
    public static Vec3 getPosTowards(PathfinderMob mob, int radius, int yRange, Vec3 vectorPosition, double amplifier) {
        Vec3 vec3 = vectorPosition.subtract(mob.getX(), mob.getY(), mob.getZ());
        boolean flag = GoalUtils.mobRestricted(mob, radius);
        return RandomPos.generateRandomPos(mob, () -> {
            BlockPos blockPos = RandomPos.generateRandomDirectionWithinRadians(mob.getRandom(), radius, yRange, 0, vec3.x, vec3.z, amplifier);
            return blockPos == null ? null : generateRandomPosTowardDirection(mob, radius, flag, blockPos);
        });
    }

    @Nullable
    public static Vec3 getPosAway(PathfinderMob mob, int radius, int yRange, Vec3 vectorPosition) {
        Vec3 vec3 = mob.position().subtract(vectorPosition);
        boolean flag = GoalUtils.mobRestricted(mob, radius);
        return RandomPos.generateRandomPos(mob, () -> {
            BlockPos blockPos = RandomPos.generateRandomDirectionWithinRadians(mob.getRandom(), radius, yRange, 0, vec3.x, vec3.z, (float) (Math.PI / 2));
            return blockPos == null ? null : generateRandomPosTowardDirection(mob, radius, flag, blockPos);
        });
    }

    @Nullable
    private static BlockPos generateRandomPosTowardDirection(PathfinderMob mob, int radius, boolean shortCircuit, BlockPos pos) {
        BlockPos blockPos = RandomPos.generateRandomPosTowardDirection(mob, radius, mob.getRandom(), pos);
        return !GoalUtils.isOutsideLimits(blockPos, mob)
                && !GoalUtils.isRestricted(shortCircuit, mob, blockPos)
                && !GoalUtils.isNotStable(mob.getNavigation(), blockPos)
                && !GoalUtils.hasMalus(mob, blockPos)
            ? blockPos
            : null;
    }
}
