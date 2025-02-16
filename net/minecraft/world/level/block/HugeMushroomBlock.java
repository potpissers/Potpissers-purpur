package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class HugeMushroomBlock extends Block {
    public static final MapCodec<HugeMushroomBlock> CODEC = simpleCodec(HugeMushroomBlock::new);
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    public static final BooleanProperty UP = PipeBlock.UP;
    public static final BooleanProperty DOWN = PipeBlock.DOWN;
    private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION;

    @Override
    public MapCodec<HugeMushroomBlock> codec() {
        return CODEC;
    }

    public HugeMushroomBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(NORTH, Boolean.valueOf(true))
                .setValue(EAST, Boolean.valueOf(true))
                .setValue(SOUTH, Boolean.valueOf(true))
                .setValue(WEST, Boolean.valueOf(true))
                .setValue(UP, Boolean.valueOf(true))
                .setValue(DOWN, Boolean.valueOf(true))
        );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        return this.defaultBlockState()
            .setValue(DOWN, Boolean.valueOf(!level.getBlockState(clickedPos.below()).is(this)))
            .setValue(UP, Boolean.valueOf(!level.getBlockState(clickedPos.above()).is(this)))
            .setValue(NORTH, Boolean.valueOf(!level.getBlockState(clickedPos.north()).is(this)))
            .setValue(EAST, Boolean.valueOf(!level.getBlockState(clickedPos.east()).is(this)))
            .setValue(SOUTH, Boolean.valueOf(!level.getBlockState(clickedPos.south()).is(this)))
            .setValue(WEST, Boolean.valueOf(!level.getBlockState(clickedPos.west()).is(this)));
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random
    ) {
        return neighborState.is(this)
            ? state.setValue(PROPERTY_BY_DIRECTION.get(direction), Boolean.valueOf(false))
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(rot.rotate(Direction.NORTH)), state.getValue(NORTH))
            .setValue(PROPERTY_BY_DIRECTION.get(rot.rotate(Direction.SOUTH)), state.getValue(SOUTH))
            .setValue(PROPERTY_BY_DIRECTION.get(rot.rotate(Direction.EAST)), state.getValue(EAST))
            .setValue(PROPERTY_BY_DIRECTION.get(rot.rotate(Direction.WEST)), state.getValue(WEST))
            .setValue(PROPERTY_BY_DIRECTION.get(rot.rotate(Direction.UP)), state.getValue(UP))
            .setValue(PROPERTY_BY_DIRECTION.get(rot.rotate(Direction.DOWN)), state.getValue(DOWN));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(mirror.mirror(Direction.NORTH)), state.getValue(NORTH))
            .setValue(PROPERTY_BY_DIRECTION.get(mirror.mirror(Direction.SOUTH)), state.getValue(SOUTH))
            .setValue(PROPERTY_BY_DIRECTION.get(mirror.mirror(Direction.EAST)), state.getValue(EAST))
            .setValue(PROPERTY_BY_DIRECTION.get(mirror.mirror(Direction.WEST)), state.getValue(WEST))
            .setValue(PROPERTY_BY_DIRECTION.get(mirror.mirror(Direction.UP)), state.getValue(UP))
            .setValue(PROPERTY_BY_DIRECTION.get(mirror.mirror(Direction.DOWN)), state.getValue(DOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UP, DOWN, NORTH, EAST, SOUTH, WEST);
    }
}
