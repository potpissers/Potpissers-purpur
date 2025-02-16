package net.minecraft.world.entity.ai.util;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

public class GoalUtils {
    public static boolean hasGroundPathNavigation(Mob mob) {
        return mob.getNavigation() instanceof GroundPathNavigation;
    }

    public static boolean mobRestricted(PathfinderMob mob, int radius) {
        return mob.hasRestriction() && mob.getRestrictCenter().closerToCenterThan(mob.position(), mob.getRestrictRadius() + radius + 1.0);
    }

    public static boolean isOutsideLimits(BlockPos pos, PathfinderMob mob) {
        return mob.level().isOutsideBuildHeight(pos.getY());
    }

    public static boolean isRestricted(boolean shortCircuit, PathfinderMob mob, BlockPos pos) {
        return shortCircuit && !mob.isWithinRestriction(pos);
    }

    public static boolean isNotStable(PathNavigation navigation, BlockPos pos) {
        return !navigation.isStableDestination(pos);
    }

    public static boolean isWater(PathfinderMob mob, BlockPos pos) {
        return mob.level().getFluidState(pos).is(FluidTags.WATER);
    }

    public static boolean hasMalus(PathfinderMob mob, BlockPos pos) {
        return mob.getPathfindingMalus(WalkNodeEvaluator.getPathTypeStatic(mob, pos)) != 0.0F;
    }

    public static boolean isSolid(PathfinderMob mob, BlockPos pos) {
        return mob.level().getBlockState(pos).isSolid();
    }
}
