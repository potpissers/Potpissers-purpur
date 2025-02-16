package net.minecraft.world.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

public class GroundPathNavigation extends PathNavigation {
    private boolean avoidSun;

    public GroundPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new WalkNodeEvaluator();
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected boolean canUpdatePath() {
        return this.mob.onGround() || this.mob.isInLiquid() || this.mob.isPassenger();
    }

    @Override
    protected Vec3 getTempMobPos() {
        return new Vec3(this.mob.getX(), this.getSurfaceY(), this.mob.getZ());
    }

    @Override
    public Path createPath(BlockPos pos, int accuracy) {
        LevelChunk chunkNow = this.level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        if (chunkNow == null) {
            return null;
        } else {
            if (chunkNow.getBlockState(pos).isAir()) {
                BlockPos.MutableBlockPos mutableBlockPos = pos.mutable().move(Direction.DOWN);

                while (mutableBlockPos.getY() > this.level.getMinY() && chunkNow.getBlockState(mutableBlockPos).isAir()) {
                    mutableBlockPos.move(Direction.DOWN);
                }

                if (mutableBlockPos.getY() > this.level.getMinY()) {
                    return super.createPath(mutableBlockPos.above(), accuracy);
                }

                mutableBlockPos.setY(pos.getY() + 1);

                while (mutableBlockPos.getY() <= this.level.getMaxY() && chunkNow.getBlockState(mutableBlockPos).isAir()) {
                    mutableBlockPos.move(Direction.UP);
                }

                pos = mutableBlockPos;
            }

            if (!chunkNow.getBlockState(pos).isSolid()) {
                return super.createPath(pos, accuracy);
            } else {
                BlockPos.MutableBlockPos mutableBlockPos = pos.mutable().move(Direction.UP);

                while (mutableBlockPos.getY() <= this.level.getMaxY() && chunkNow.getBlockState(mutableBlockPos).isSolid()) {
                    mutableBlockPos.move(Direction.UP);
                }

                return super.createPath(mutableBlockPos.immutable(), accuracy);
            }
        }
    }

    @Override
    public Path createPath(Entity entity, int i) {
        return this.createPath(entity.blockPosition(), i);
    }

    private int getSurfaceY() {
        if (this.mob.isInWater() && this.canFloat()) {
            int blockY = this.mob.getBlockY();
            BlockState blockState = this.level.getBlockState(BlockPos.containing(this.mob.getX(), blockY, this.mob.getZ()));
            int i = 0;

            while (blockState.is(Blocks.WATER)) {
                blockState = this.level.getBlockState(BlockPos.containing(this.mob.getX(), ++blockY, this.mob.getZ()));
                if (++i > 16) {
                    return this.mob.getBlockY();
                }
            }

            return blockY;
        } else {
            return Mth.floor(this.mob.getY() + 0.5);
        }
    }

    @Override
    protected void trimPath() {
        super.trimPath();
        if (this.avoidSun) {
            if (this.level.canSeeSky(BlockPos.containing(this.mob.getX(), this.mob.getY() + 0.5, this.mob.getZ()))) {
                return;
            }

            for (int i = 0; i < this.path.getNodeCount(); i++) {
                Node node = this.path.getNode(i);
                if (this.level.canSeeSky(new BlockPos(node.x, node.y, node.z))) {
                    this.path.truncateNodes(i);
                    return;
                }
            }
        }
    }

    protected boolean hasValidPathType(PathType pathType) {
        return pathType != PathType.WATER && pathType != PathType.LAVA && pathType != PathType.OPEN;
    }

    public void setCanOpenDoors(boolean canOpenDoors) {
        this.nodeEvaluator.setCanOpenDoors(canOpenDoors);
    }

    public void setAvoidSun(boolean avoidSun) {
        this.avoidSun = avoidSun;
    }

    public void setCanWalkOverFences(boolean canWalkOverFences) {
        this.nodeEvaluator.setCanWalkOverFences(canWalkOverFences);
    }
}
