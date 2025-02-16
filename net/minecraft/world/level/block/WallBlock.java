package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WallBlock extends Block implements SimpleWaterloggedBlock {
    public static final MapCodec<WallBlock> CODEC = simpleCodec(WallBlock::new);
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final EnumProperty<WallSide> EAST_WALL = BlockStateProperties.EAST_WALL;
    public static final EnumProperty<WallSide> NORTH_WALL = BlockStateProperties.NORTH_WALL;
    public static final EnumProperty<WallSide> SOUTH_WALL = BlockStateProperties.SOUTH_WALL;
    public static final EnumProperty<WallSide> WEST_WALL = BlockStateProperties.WEST_WALL;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private final Map<BlockState, VoxelShape> shapeByIndex;
    private final Map<BlockState, VoxelShape> collisionShapeByIndex;
    private static final int WALL_WIDTH = 3;
    private static final int WALL_HEIGHT = 14;
    private static final int POST_WIDTH = 4;
    private static final int POST_COVER_WIDTH = 1;
    private static final int WALL_COVER_START = 7;
    private static final int WALL_COVER_END = 9;
    private static final VoxelShape POST_TEST = Block.box(7.0, 0.0, 7.0, 9.0, 16.0, 9.0);
    private static final VoxelShape NORTH_TEST = Block.box(7.0, 0.0, 0.0, 9.0, 16.0, 9.0);
    private static final VoxelShape SOUTH_TEST = Block.box(7.0, 0.0, 7.0, 9.0, 16.0, 16.0);
    private static final VoxelShape WEST_TEST = Block.box(0.0, 0.0, 7.0, 9.0, 16.0, 9.0);
    private static final VoxelShape EAST_TEST = Block.box(7.0, 0.0, 7.0, 16.0, 16.0, 9.0);

    @Override
    public MapCodec<WallBlock> codec() {
        return CODEC;
    }

    public WallBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(UP, Boolean.valueOf(true))
                .setValue(NORTH_WALL, WallSide.NONE)
                .setValue(EAST_WALL, WallSide.NONE)
                .setValue(SOUTH_WALL, WallSide.NONE)
                .setValue(WEST_WALL, WallSide.NONE)
                .setValue(WATERLOGGED, Boolean.valueOf(false))
        );
        this.shapeByIndex = this.makeShapes(4.0F, 3.0F, 16.0F, 0.0F, 14.0F, 16.0F);
        this.collisionShapeByIndex = this.makeShapes(4.0F, 3.0F, 24.0F, 0.0F, 24.0F, 24.0F);
    }

    private static VoxelShape applyWallShape(VoxelShape baseShape, WallSide height, VoxelShape lowShape, VoxelShape tallShape) {
        if (height == WallSide.TALL) {
            return Shapes.or(baseShape, tallShape);
        } else {
            return height == WallSide.LOW ? Shapes.or(baseShape, lowShape) : baseShape;
        }
    }

    private Map<BlockState, VoxelShape> makeShapes(float width, float depth, float wallPostHeight, float wallMinY, float wallLowHeight, float wallTallHeight) {
        float f = 8.0F - width;
        float f1 = 8.0F + width;
        float f2 = 8.0F - depth;
        float f3 = 8.0F + depth;
        VoxelShape voxelShape = Block.box(f, 0.0, f, f1, wallPostHeight, f1);
        VoxelShape voxelShape1 = Block.box(f2, wallMinY, 0.0, f3, wallLowHeight, f3);
        VoxelShape voxelShape2 = Block.box(f2, wallMinY, f2, f3, wallLowHeight, 16.0);
        VoxelShape voxelShape3 = Block.box(0.0, wallMinY, f2, f3, wallLowHeight, f3);
        VoxelShape voxelShape4 = Block.box(f2, wallMinY, f2, 16.0, wallLowHeight, f3);
        VoxelShape voxelShape5 = Block.box(f2, wallMinY, 0.0, f3, wallTallHeight, f3);
        VoxelShape voxelShape6 = Block.box(f2, wallMinY, f2, f3, wallTallHeight, 16.0);
        VoxelShape voxelShape7 = Block.box(0.0, wallMinY, f2, f3, wallTallHeight, f3);
        VoxelShape voxelShape8 = Block.box(f2, wallMinY, f2, 16.0, wallTallHeight, f3);
        Builder<BlockState, VoxelShape> builder = ImmutableMap.builder();

        for (Boolean _boolean : UP.getPossibleValues()) {
            for (WallSide wallSide : EAST_WALL.getPossibleValues()) {
                for (WallSide wallSide1 : NORTH_WALL.getPossibleValues()) {
                    for (WallSide wallSide2 : WEST_WALL.getPossibleValues()) {
                        for (WallSide wallSide3 : SOUTH_WALL.getPossibleValues()) {
                            VoxelShape voxelShape9 = Shapes.empty();
                            voxelShape9 = applyWallShape(voxelShape9, wallSide, voxelShape4, voxelShape8);
                            voxelShape9 = applyWallShape(voxelShape9, wallSide2, voxelShape3, voxelShape7);
                            voxelShape9 = applyWallShape(voxelShape9, wallSide1, voxelShape1, voxelShape5);
                            voxelShape9 = applyWallShape(voxelShape9, wallSide3, voxelShape2, voxelShape6);
                            if (_boolean) {
                                voxelShape9 = Shapes.or(voxelShape9, voxelShape);
                            }

                            BlockState blockState = this.defaultBlockState()
                                .setValue(UP, _boolean)
                                .setValue(EAST_WALL, wallSide)
                                .setValue(WEST_WALL, wallSide2)
                                .setValue(NORTH_WALL, wallSide1)
                                .setValue(SOUTH_WALL, wallSide3);
                            builder.put(blockState.setValue(WATERLOGGED, Boolean.valueOf(false)), voxelShape9);
                            builder.put(blockState.setValue(WATERLOGGED, Boolean.valueOf(true)), voxelShape9);
                        }
                    }
                }
            }
        }

        return builder.build();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapeByIndex.get(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.collisionShapeByIndex.get(state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    private boolean connectsTo(BlockState state, boolean sideSolid, Direction direction) {
        Block block = state.getBlock();
        boolean flag = block instanceof FenceGateBlock && FenceGateBlock.connectsToDirection(state, direction);
        return state.is(BlockTags.WALLS) || !isExceptionForConnection(state) && sideSolid || block instanceof IronBarsBlock || flag;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        BlockPos blockPos = clickedPos.north();
        BlockPos blockPos1 = clickedPos.east();
        BlockPos blockPos2 = clickedPos.south();
        BlockPos blockPos3 = clickedPos.west();
        BlockPos blockPos4 = clickedPos.above();
        BlockState blockState = level.getBlockState(blockPos);
        BlockState blockState1 = level.getBlockState(blockPos1);
        BlockState blockState2 = level.getBlockState(blockPos2);
        BlockState blockState3 = level.getBlockState(blockPos3);
        BlockState blockState4 = level.getBlockState(blockPos4);
        boolean flag = this.connectsTo(blockState, blockState.isFaceSturdy(level, blockPos, Direction.SOUTH), Direction.SOUTH);
        boolean flag1 = this.connectsTo(blockState1, blockState1.isFaceSturdy(level, blockPos1, Direction.WEST), Direction.WEST);
        boolean flag2 = this.connectsTo(blockState2, blockState2.isFaceSturdy(level, blockPos2, Direction.NORTH), Direction.NORTH);
        boolean flag3 = this.connectsTo(blockState3, blockState3.isFaceSturdy(level, blockPos3, Direction.EAST), Direction.EAST);
        BlockState blockState5 = this.defaultBlockState().setValue(WATERLOGGED, Boolean.valueOf(fluidState.getType() == Fluids.WATER));
        return this.updateShape(level, blockState5, blockPos4, blockState4, flag, flag1, flag2, flag3);
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
        if (state.getValue(WATERLOGGED)) {
            scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        if (direction == Direction.DOWN) {
            return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        } else {
            return direction == Direction.UP
                ? this.topUpdate(level, state, neighborPos, neighborState)
                : this.sideUpdate(level, pos, state, neighborPos, neighborState, direction);
        }
    }

    private static boolean isConnected(BlockState state, Property<WallSide> heightProperty) {
        return state.getValue(heightProperty) != WallSide.NONE;
    }

    private static boolean isCovered(VoxelShape firstShape, VoxelShape secondShape) {
        return !Shapes.joinIsNotEmpty(secondShape, firstShape, BooleanOp.ONLY_FIRST);
    }

    private BlockState topUpdate(LevelReader level, BlockState state, BlockPos pos, BlockState secondState) {
        boolean isConnected = isConnected(state, NORTH_WALL);
        boolean isConnected1 = isConnected(state, EAST_WALL);
        boolean isConnected2 = isConnected(state, SOUTH_WALL);
        boolean isConnected3 = isConnected(state, WEST_WALL);
        return this.updateShape(level, state, pos, secondState, isConnected, isConnected1, isConnected2, isConnected3);
    }

    private BlockState sideUpdate(LevelReader level, BlockPos firstPos, BlockState firstState, BlockPos secondPos, BlockState secondState, Direction dir) {
        Direction opposite = dir.getOpposite();
        boolean flag = dir == Direction.NORTH
            ? this.connectsTo(secondState, secondState.isFaceSturdy(level, secondPos, opposite), opposite)
            : isConnected(firstState, NORTH_WALL);
        boolean flag1 = dir == Direction.EAST
            ? this.connectsTo(secondState, secondState.isFaceSturdy(level, secondPos, opposite), opposite)
            : isConnected(firstState, EAST_WALL);
        boolean flag2 = dir == Direction.SOUTH
            ? this.connectsTo(secondState, secondState.isFaceSturdy(level, secondPos, opposite), opposite)
            : isConnected(firstState, SOUTH_WALL);
        boolean flag3 = dir == Direction.WEST
            ? this.connectsTo(secondState, secondState.isFaceSturdy(level, secondPos, opposite), opposite)
            : isConnected(firstState, WEST_WALL);
        BlockPos blockPos = firstPos.above();
        BlockState blockState = level.getBlockState(blockPos);
        return this.updateShape(level, firstState, blockPos, blockState, flag, flag1, flag2, flag3);
    }

    private BlockState updateShape(
        LevelReader level,
        BlockState state,
        BlockPos pos,
        BlockState neighbour,
        boolean northConnection,
        boolean eastConnection,
        boolean southConnection,
        boolean westConnection
    ) {
        VoxelShape faceShape = neighbour.getCollisionShape(level, pos).getFaceShape(Direction.DOWN);
        BlockState blockState = this.updateSides(state, northConnection, eastConnection, southConnection, westConnection, faceShape);
        return blockState.setValue(UP, Boolean.valueOf(this.shouldRaisePost(blockState, neighbour, faceShape)));
    }

    private boolean shouldRaisePost(BlockState state, BlockState neighbour, VoxelShape shape) {
        boolean flag = neighbour.getBlock() instanceof WallBlock && neighbour.getValue(UP);
        if (flag) {
            return true;
        } else {
            WallSide wallSide = state.getValue(NORTH_WALL);
            WallSide wallSide1 = state.getValue(SOUTH_WALL);
            WallSide wallSide2 = state.getValue(EAST_WALL);
            WallSide wallSide3 = state.getValue(WEST_WALL);
            boolean flag1 = wallSide1 == WallSide.NONE;
            boolean flag2 = wallSide3 == WallSide.NONE;
            boolean flag3 = wallSide2 == WallSide.NONE;
            boolean flag4 = wallSide == WallSide.NONE;
            boolean flag5 = flag4 && flag1 && flag2 && flag3 || flag4 != flag1 || flag2 != flag3;
            if (flag5) {
                return true;
            } else {
                boolean flag6 = wallSide == WallSide.TALL && wallSide1 == WallSide.TALL || wallSide2 == WallSide.TALL && wallSide3 == WallSide.TALL;
                return !flag6 && (neighbour.is(BlockTags.WALL_POST_OVERRIDE) || isCovered(shape, POST_TEST));
            }
        }
    }

    private BlockState updateSides(
        BlockState state, boolean northConnection, boolean eastConnection, boolean southConnection, boolean westConnection, VoxelShape wallShape
    ) {
        return state.setValue(NORTH_WALL, this.makeWallState(northConnection, wallShape, NORTH_TEST))
            .setValue(EAST_WALL, this.makeWallState(eastConnection, wallShape, EAST_TEST))
            .setValue(SOUTH_WALL, this.makeWallState(southConnection, wallShape, SOUTH_TEST))
            .setValue(WEST_WALL, this.makeWallState(westConnection, wallShape, WEST_TEST));
    }

    private WallSide makeWallState(boolean allowConnection, VoxelShape shape, VoxelShape neighbourShape) {
        if (allowConnection) {
            return isCovered(shape, neighbourShape) ? WallSide.TALL : WallSide.LOW;
        } else {
            return WallSide.NONE;
        }
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return !state.getValue(WATERLOGGED);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UP, NORTH_WALL, EAST_WALL, WEST_WALL, SOUTH_WALL, WATERLOGGED);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_180:
                return state.setValue(NORTH_WALL, state.getValue(SOUTH_WALL))
                    .setValue(EAST_WALL, state.getValue(WEST_WALL))
                    .setValue(SOUTH_WALL, state.getValue(NORTH_WALL))
                    .setValue(WEST_WALL, state.getValue(EAST_WALL));
            case COUNTERCLOCKWISE_90:
                return state.setValue(NORTH_WALL, state.getValue(EAST_WALL))
                    .setValue(EAST_WALL, state.getValue(SOUTH_WALL))
                    .setValue(SOUTH_WALL, state.getValue(WEST_WALL))
                    .setValue(WEST_WALL, state.getValue(NORTH_WALL));
            case CLOCKWISE_90:
                return state.setValue(NORTH_WALL, state.getValue(WEST_WALL))
                    .setValue(EAST_WALL, state.getValue(NORTH_WALL))
                    .setValue(SOUTH_WALL, state.getValue(EAST_WALL))
                    .setValue(WEST_WALL, state.getValue(SOUTH_WALL));
            default:
                return state;
        }
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        switch (mirror) {
            case LEFT_RIGHT:
                return state.setValue(NORTH_WALL, state.getValue(SOUTH_WALL)).setValue(SOUTH_WALL, state.getValue(NORTH_WALL));
            case FRONT_BACK:
                return state.setValue(EAST_WALL, state.getValue(WEST_WALL)).setValue(WEST_WALL, state.getValue(EAST_WALL));
            default:
                return super.mirror(state, mirror);
        }
    }
}
