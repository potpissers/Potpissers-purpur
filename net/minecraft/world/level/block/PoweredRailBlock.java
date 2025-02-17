package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;

public class PoweredRailBlock extends BaseRailBlock {
    public static final MapCodec<PoweredRailBlock> CODEC = simpleCodec(PoweredRailBlock::new);
    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    @Override
    public MapCodec<PoweredRailBlock> codec() {
        return CODEC;
    }

    protected PoweredRailBlock(BlockBehaviour.Properties properties) {
        super(true, properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(SHAPE, RailShape.NORTH_SOUTH)
                .setValue(POWERED, Boolean.valueOf(false))
                .setValue(WATERLOGGED, Boolean.valueOf(false))
        );
    }

    protected boolean findPoweredRailSignal(Level level, BlockPos pos, BlockState state, boolean searchForward, int recursionCount) {
        if (recursionCount >= level.purpurConfig.railActivationRange) { // Purpur - Config for powered rail activation distance
            return false;
        } else {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            boolean flag = true;
            RailShape railShape = state.getValue(SHAPE);
            switch (railShape) {
                case NORTH_SOUTH:
                    if (searchForward) {
                        z++;
                    } else {
                        z--;
                    }
                    break;
                case EAST_WEST:
                    if (searchForward) {
                        x--;
                    } else {
                        x++;
                    }
                    break;
                case ASCENDING_EAST:
                    if (searchForward) {
                        x--;
                    } else {
                        x++;
                        y++;
                        flag = false;
                    }

                    railShape = RailShape.EAST_WEST;
                    break;
                case ASCENDING_WEST:
                    if (searchForward) {
                        x--;
                        y++;
                        flag = false;
                    } else {
                        x++;
                    }

                    railShape = RailShape.EAST_WEST;
                    break;
                case ASCENDING_NORTH:
                    if (searchForward) {
                        z++;
                    } else {
                        z--;
                        y++;
                        flag = false;
                    }

                    railShape = RailShape.NORTH_SOUTH;
                    break;
                case ASCENDING_SOUTH:
                    if (searchForward) {
                        z++;
                        y++;
                        flag = false;
                    } else {
                        z--;
                    }

                    railShape = RailShape.NORTH_SOUTH;
            }

            return this.isSameRailWithPower(level, new BlockPos(x, y, z), searchForward, recursionCount, railShape)
                || flag && this.isSameRailWithPower(level, new BlockPos(x, y - 1, z), searchForward, recursionCount, railShape);
        }
    }

    protected boolean isSameRailWithPower(Level level, BlockPos state, boolean searchForward, int recursionCount, RailShape shape) {
        BlockState blockState = level.getBlockState(state);
        if (!blockState.is(this)) {
            return false;
        } else {
            RailShape railShape = blockState.getValue(SHAPE);
            return (
                    shape != RailShape.EAST_WEST
                        || railShape != RailShape.NORTH_SOUTH && railShape != RailShape.ASCENDING_NORTH && railShape != RailShape.ASCENDING_SOUTH
                )
                && (
                    shape != RailShape.NORTH_SOUTH
                        || railShape != RailShape.EAST_WEST && railShape != RailShape.ASCENDING_EAST && railShape != RailShape.ASCENDING_WEST
                )
                && blockState.getValue(POWERED)
                && (level.hasNeighborSignal(state) || this.findPoweredRailSignal(level, state, blockState, searchForward, recursionCount + 1));
        }
    }

    @Override
    protected void updateState(BlockState state, Level level, BlockPos pos, Block block) {
        boolean poweredValue = state.getValue(POWERED);
        boolean flag = level.hasNeighborSignal(pos)
            || this.findPoweredRailSignal(level, pos, state, true, 0)
            || this.findPoweredRailSignal(level, pos, state, false, 0);
        if (flag != poweredValue) {
            // CraftBukkit start
            int power = flag ? 15 : 0;
            int newPower = org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(level, pos, power, 15 - power).getNewCurrent();
            if (newPower == power) {
                return;
            }
            // CraftBukkit end
            level.setBlock(pos, state.setValue(POWERED, Boolean.valueOf(flag)), 3);
            level.updateNeighborsAt(pos.below(), this);
            if (state.getValue(SHAPE).isSlope()) {
                level.updateNeighborsAt(pos.above(), this);
            }
        }
    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return SHAPE;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        switch (rot) {
            case CLOCKWISE_180:
                switch ((RailShape)state.getValue(SHAPE)) {
                    case ASCENDING_EAST:
                        return state.setValue(SHAPE, RailShape.ASCENDING_WEST);
                    case ASCENDING_WEST:
                        return state.setValue(SHAPE, RailShape.ASCENDING_EAST);
                    case ASCENDING_NORTH:
                        return state.setValue(SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_SOUTH:
                        return state.setValue(SHAPE, RailShape.ASCENDING_NORTH);
                    case SOUTH_EAST:
                        return state.setValue(SHAPE, RailShape.NORTH_WEST);
                    case SOUTH_WEST:
                        return state.setValue(SHAPE, RailShape.NORTH_EAST);
                    case NORTH_WEST:
                        return state.setValue(SHAPE, RailShape.SOUTH_EAST);
                    case NORTH_EAST:
                        return state.setValue(SHAPE, RailShape.SOUTH_WEST);
                }
            case COUNTERCLOCKWISE_90:
                switch ((RailShape)state.getValue(SHAPE)) {
                    case NORTH_SOUTH:
                        return state.setValue(SHAPE, RailShape.EAST_WEST);
                    case EAST_WEST:
                        return state.setValue(SHAPE, RailShape.NORTH_SOUTH);
                    case ASCENDING_EAST:
                        return state.setValue(SHAPE, RailShape.ASCENDING_NORTH);
                    case ASCENDING_WEST:
                        return state.setValue(SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_NORTH:
                        return state.setValue(SHAPE, RailShape.ASCENDING_WEST);
                    case ASCENDING_SOUTH:
                        return state.setValue(SHAPE, RailShape.ASCENDING_EAST);
                    case SOUTH_EAST:
                        return state.setValue(SHAPE, RailShape.NORTH_EAST);
                    case SOUTH_WEST:
                        return state.setValue(SHAPE, RailShape.SOUTH_EAST);
                    case NORTH_WEST:
                        return state.setValue(SHAPE, RailShape.SOUTH_WEST);
                    case NORTH_EAST:
                        return state.setValue(SHAPE, RailShape.NORTH_WEST);
                }
            case CLOCKWISE_90:
                switch ((RailShape)state.getValue(SHAPE)) {
                    case NORTH_SOUTH:
                        return state.setValue(SHAPE, RailShape.EAST_WEST);
                    case EAST_WEST:
                        return state.setValue(SHAPE, RailShape.NORTH_SOUTH);
                    case ASCENDING_EAST:
                        return state.setValue(SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_WEST:
                        return state.setValue(SHAPE, RailShape.ASCENDING_NORTH);
                    case ASCENDING_NORTH:
                        return state.setValue(SHAPE, RailShape.ASCENDING_EAST);
                    case ASCENDING_SOUTH:
                        return state.setValue(SHAPE, RailShape.ASCENDING_WEST);
                    case SOUTH_EAST:
                        return state.setValue(SHAPE, RailShape.SOUTH_WEST);
                    case SOUTH_WEST:
                        return state.setValue(SHAPE, RailShape.NORTH_WEST);
                    case NORTH_WEST:
                        return state.setValue(SHAPE, RailShape.NORTH_EAST);
                    case NORTH_EAST:
                        return state.setValue(SHAPE, RailShape.SOUTH_EAST);
                }
            default:
                return state;
        }
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        RailShape railShape = state.getValue(SHAPE);
        switch (mirror) {
            case LEFT_RIGHT:
                switch (railShape) {
                    case ASCENDING_NORTH:
                        return state.setValue(SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_SOUTH:
                        return state.setValue(SHAPE, RailShape.ASCENDING_NORTH);
                    case SOUTH_EAST:
                        return state.setValue(SHAPE, RailShape.NORTH_EAST);
                    case SOUTH_WEST:
                        return state.setValue(SHAPE, RailShape.NORTH_WEST);
                    case NORTH_WEST:
                        return state.setValue(SHAPE, RailShape.SOUTH_WEST);
                    case NORTH_EAST:
                        return state.setValue(SHAPE, RailShape.SOUTH_EAST);
                    default:
                        return super.mirror(state, mirror);
                }
            case FRONT_BACK:
                switch (railShape) {
                    case ASCENDING_EAST:
                        return state.setValue(SHAPE, RailShape.ASCENDING_WEST);
                    case ASCENDING_WEST:
                        return state.setValue(SHAPE, RailShape.ASCENDING_EAST);
                    case ASCENDING_NORTH:
                    case ASCENDING_SOUTH:
                    default:
                        break;
                    case SOUTH_EAST:
                        return state.setValue(SHAPE, RailShape.SOUTH_WEST);
                    case SOUTH_WEST:
                        return state.setValue(SHAPE, RailShape.SOUTH_EAST);
                    case NORTH_WEST:
                        return state.setValue(SHAPE, RailShape.NORTH_EAST);
                    case NORTH_EAST:
                        return state.setValue(SHAPE, RailShape.NORTH_WEST);
                }
        }

        return super.mirror(state, mirror);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHAPE, POWERED, WATERLOGGED);
    }
}
