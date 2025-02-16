package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;

public class BreathAirGoal extends Goal {
    private final PathfinderMob mob;

    public BreathAirGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.mob.getAirSupply() < 140;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public void start() {
        this.findAirPosition();
    }

    private void findAirPosition() {
        Iterable<BlockPos> iterable = BlockPos.betweenClosed(
            Mth.floor(this.mob.getX() - 1.0),
            this.mob.getBlockY(),
            Mth.floor(this.mob.getZ() - 1.0),
            Mth.floor(this.mob.getX() + 1.0),
            Mth.floor(this.mob.getY() + 8.0),
            Mth.floor(this.mob.getZ() + 1.0)
        );
        BlockPos blockPos = null;

        for (BlockPos blockPos1 : iterable) {
            if (this.givesAir(this.mob.level(), blockPos1)) {
                blockPos = blockPos1;
                break;
            }
        }

        if (blockPos == null) {
            blockPos = BlockPos.containing(this.mob.getX(), this.mob.getY() + 8.0, this.mob.getZ());
        }

        this.mob.getNavigation().moveTo(blockPos.getX(), blockPos.getY() + 1, blockPos.getZ(), 1.0);
    }

    @Override
    public void tick() {
        this.findAirPosition();
        this.mob.moveRelative(0.02F, new Vec3(this.mob.xxa, this.mob.yya, this.mob.zza));
        this.mob.move(MoverType.SELF, this.mob.getDeltaMovement());
    }

    private boolean givesAir(LevelReader level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        return (level.getFluidState(pos).isEmpty() || blockState.is(Blocks.BUBBLE_COLUMN)) && blockState.isPathfindable(PathComputationType.LAND);
    }
}
