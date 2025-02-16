package net.minecraft.world.phys.shapes;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

public class MinecartCollisionContext extends EntityCollisionContext {
    @Nullable
    private BlockPos ingoreBelow;
    @Nullable
    private BlockPos slopeIgnore;

    protected MinecartCollisionContext(AbstractMinecart minecart, boolean canStandOnFluid) {
        super(minecart, canStandOnFluid);
        this.setupContext(minecart);
    }

    private void setupContext(AbstractMinecart minecart) {
        BlockPos currentBlockPosOrRailBelow = minecart.getCurrentBlockPosOrRailBelow();
        BlockState blockState = minecart.level().getBlockState(currentBlockPosOrRailBelow);
        boolean isRail = BaseRailBlock.isRail(blockState);
        if (isRail) {
            this.ingoreBelow = currentBlockPosOrRailBelow.below();
            RailShape railShape = blockState.getValue(((BaseRailBlock)blockState.getBlock()).getShapeProperty());
            if (railShape.isSlope()) {
                this.slopeIgnore = switch (railShape) {
                    case ASCENDING_EAST -> currentBlockPosOrRailBelow.east();
                    case ASCENDING_WEST -> currentBlockPosOrRailBelow.west();
                    case ASCENDING_NORTH -> currentBlockPosOrRailBelow.north();
                    case ASCENDING_SOUTH -> currentBlockPosOrRailBelow.south();
                    default -> null;
                };
            }
        }
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, CollisionGetter collisionGetter, BlockPos pos) {
        return !pos.equals(this.ingoreBelow) && !pos.equals(this.slopeIgnore) ? super.getCollisionShape(state, collisionGetter, pos) : Shapes.empty();
    }
}
