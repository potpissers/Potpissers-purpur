package net.minecraft.world.entity.ai.util;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

public class HoverRandomPos {
    @Nullable
    public static Vec3 getPos(PathfinderMob mob, int radius, int yRange, double x, double z, float amplifier, int maxSwimUp, int minSwimUp) {
        boolean flag = GoalUtils.mobRestricted(mob, radius);
        return RandomPos.generateRandomPos(
            mob,
            () -> {
                BlockPos blockPos = RandomPos.generateRandomDirectionWithinRadians(mob.getRandom(), radius, yRange, 0, x, z, amplifier);
                if (blockPos == null) {
                    return null;
                } else {
                    BlockPos blockPos1 = LandRandomPos.generateRandomPosTowardDirection(mob, radius, flag, blockPos);
                    if (blockPos1 == null) {
                        return null;
                    } else {
                        blockPos1 = RandomPos.moveUpToAboveSolid(
                            blockPos1,
                            mob.getRandom().nextInt(maxSwimUp - minSwimUp + 1) + minSwimUp,
                            mob.level().getMaxY(),
                            pos -> GoalUtils.isSolid(mob, pos)
                        );
                        return !GoalUtils.isWater(mob, blockPos1) && !GoalUtils.hasMalus(mob, blockPos1) ? blockPos1 : null;
                    }
                }
            }
        );
    }
}
