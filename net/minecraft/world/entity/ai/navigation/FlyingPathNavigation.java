package net.minecraft.world.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

public class FlyingPathNavigation extends PathNavigation {
    public FlyingPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new FlyNodeEvaluator();
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected boolean canMoveDirectly(Vec3 posVec31, Vec3 posVec32) {
        return isClearForMovementBetween(this.mob, posVec31, posVec32, true);
    }

    @Override
    protected boolean canUpdatePath() {
        return this.canFloat() && this.mob.isInLiquid() || !this.mob.isPassenger();
    }

    @Override
    protected Vec3 getTempMobPos() {
        return this.mob.position();
    }

    @Override
    public Path createPath(Entity entity, int i) {
        return this.createPath(entity.blockPosition(), i);
    }

    @Override
    public void tick() {
        this.tick++;
        if (this.hasDelayedRecomputation) {
            this.recomputePath();
        }

        if (!this.isDone()) {
            if (this.canUpdatePath()) {
                this.followThePath();
            } else if (this.path != null && !this.path.isDone()) {
                Vec3 nextEntityPos = this.path.getNextEntityPos(this.mob);
                if (this.mob.getBlockX() == Mth.floor(nextEntityPos.x)
                    && this.mob.getBlockY() == Mth.floor(nextEntityPos.y)
                    && this.mob.getBlockZ() == Mth.floor(nextEntityPos.z)) {
                    this.path.advance();
                }
            }

            DebugPackets.sendPathFindingPacket(this.level, this.mob, this.path, this.maxDistanceToWaypoint);
            if (!this.isDone()) {
                Vec3 nextEntityPos = this.path.getNextEntityPos(this.mob);
                this.mob.getMoveControl().setWantedPosition(nextEntityPos.x, nextEntityPos.y, nextEntityPos.z, this.speedModifier);
            }
        }
    }

    public void setCanOpenDoors(boolean canOpenDoors) {
        this.nodeEvaluator.setCanOpenDoors(canOpenDoors);
    }

    @Override
    public boolean isStableDestination(BlockPos pos) {
        return this.level.getBlockState(pos).entityCanStandOn(this.level, pos, this.mob);
    }
}
