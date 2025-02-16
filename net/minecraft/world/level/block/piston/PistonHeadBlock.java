package net.minecraft.world.level.block.piston;

import com.mojang.serialization.MapCodec;
import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PistonHeadBlock extends DirectionalBlock {
    public static final MapCodec<PistonHeadBlock> CODEC = simpleCodec(PistonHeadBlock::new);
    public static final EnumProperty<PistonType> TYPE = BlockStateProperties.PISTON_TYPE;
    public static final BooleanProperty SHORT = BlockStateProperties.SHORT;
    public static final float PLATFORM = 4.0F;
    protected static final VoxelShape EAST_AABB = Block.box(12.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    protected static final VoxelShape WEST_AABB = Block.box(0.0, 0.0, 0.0, 4.0, 16.0, 16.0);
    protected static final VoxelShape SOUTH_AABB = Block.box(0.0, 0.0, 12.0, 16.0, 16.0, 16.0);
    protected static final VoxelShape NORTH_AABB = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 4.0);
    protected static final VoxelShape UP_AABB = Block.box(0.0, 12.0, 0.0, 16.0, 16.0, 16.0);
    protected static final VoxelShape DOWN_AABB = Block.box(0.0, 0.0, 0.0, 16.0, 4.0, 16.0);
    protected static final float AABB_OFFSET = 2.0F;
    protected static final float EDGE_MIN = 6.0F;
    protected static final float EDGE_MAX = 10.0F;
    protected static final VoxelShape UP_ARM_AABB = Block.box(6.0, -4.0, 6.0, 10.0, 12.0, 10.0);
    protected static final VoxelShape DOWN_ARM_AABB = Block.box(6.0, 4.0, 6.0, 10.0, 20.0, 10.0);
    protected static final VoxelShape SOUTH_ARM_AABB = Block.box(6.0, 6.0, -4.0, 10.0, 10.0, 12.0);
    protected static final VoxelShape NORTH_ARM_AABB = Block.box(6.0, 6.0, 4.0, 10.0, 10.0, 20.0);
    protected static final VoxelShape EAST_ARM_AABB = Block.box(-4.0, 6.0, 6.0, 12.0, 10.0, 10.0);
    protected static final VoxelShape WEST_ARM_AABB = Block.box(4.0, 6.0, 6.0, 20.0, 10.0, 10.0);
    protected static final VoxelShape SHORT_UP_ARM_AABB = Block.box(6.0, 0.0, 6.0, 10.0, 12.0, 10.0);
    protected static final VoxelShape SHORT_DOWN_ARM_AABB = Block.box(6.0, 4.0, 6.0, 10.0, 16.0, 10.0);
    protected static final VoxelShape SHORT_SOUTH_ARM_AABB = Block.box(6.0, 6.0, 0.0, 10.0, 10.0, 12.0);
    protected static final VoxelShape SHORT_NORTH_ARM_AABB = Block.box(6.0, 6.0, 4.0, 10.0, 10.0, 16.0);
    protected static final VoxelShape SHORT_EAST_ARM_AABB = Block.box(0.0, 6.0, 6.0, 12.0, 10.0, 10.0);
    protected static final VoxelShape SHORT_WEST_ARM_AABB = Block.box(4.0, 6.0, 6.0, 16.0, 10.0, 10.0);
    private static final VoxelShape[] SHAPES_SHORT = makeShapes(true);
    private static final VoxelShape[] SHAPES_LONG = makeShapes(false);

    @Override
    protected MapCodec<PistonHeadBlock> codec() {
        return CODEC;
    }

    private static VoxelShape[] makeShapes(boolean extended) {
        return Arrays.stream(Direction.values()).map(dir -> calculateShape(dir, extended)).toArray(VoxelShape[]::new);
    }

    private static VoxelShape calculateShape(Direction direction, boolean shortArm) {
        switch (direction) {
            case DOWN:
            default:
                return Shapes.or(DOWN_AABB, shortArm ? SHORT_DOWN_ARM_AABB : DOWN_ARM_AABB);
            case UP:
                return Shapes.or(UP_AABB, shortArm ? SHORT_UP_ARM_AABB : UP_ARM_AABB);
            case NORTH:
                return Shapes.or(NORTH_AABB, shortArm ? SHORT_NORTH_ARM_AABB : NORTH_ARM_AABB);
            case SOUTH:
                return Shapes.or(SOUTH_AABB, shortArm ? SHORT_SOUTH_ARM_AABB : SOUTH_ARM_AABB);
            case WEST:
                return Shapes.or(WEST_AABB, shortArm ? SHORT_WEST_ARM_AABB : WEST_ARM_AABB);
            case EAST:
                return Shapes.or(EAST_AABB, shortArm ? SHORT_EAST_ARM_AABB : EAST_ARM_AABB);
        }
    }

    public PistonHeadBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TYPE, PistonType.DEFAULT).setValue(SHORT, Boolean.valueOf(false))
        );
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(SHORT) ? SHAPES_SHORT : SHAPES_LONG)[state.getValue(FACING).ordinal()];
    }

    private boolean isFittingBase(BlockState baseState, BlockState extendedState) {
        Block block = baseState.getValue(TYPE) == PistonType.DEFAULT ? Blocks.PISTON : Blocks.STICKY_PISTON;
        return extendedState.is(block) && extendedState.getValue(PistonBaseBlock.EXTENDED) && extendedState.getValue(FACING) == baseState.getValue(FACING);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.getAbilities().instabuild) {
            BlockPos blockPos = pos.relative(state.getValue(FACING).getOpposite());
            if (this.isFittingBase(state, level.getBlockState(blockPos))) {
                level.destroyBlock(blockPos, false);
            }
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, isMoving);
            BlockPos blockPos = pos.relative(state.getValue(FACING).getOpposite());
            if (this.isFittingBase(state, level.getBlockState(blockPos))) {
                level.destroyBlock(blockPos, true);
            }
        }
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
        return direction.getOpposite() == state.getValue(FACING) && !state.canSurvive(level, pos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos.relative(state.getValue(FACING).getOpposite()));
        return this.isFittingBase(state, blockState) || blockState.is(Blocks.MOVING_PISTON) && blockState.getValue(FACING) == state.getValue(FACING);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (state.canSurvive(level, pos)) {
            level.neighborChanged(
                pos.relative(state.getValue(FACING).getOpposite()),
                neighborBlock,
                ExperimentalRedstoneUtils.withFront(orientation, state.getValue(FACING).getOpposite())
            );
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(state.getValue(TYPE) == PistonType.STICKY ? Blocks.STICKY_PISTON : Blocks.PISTON);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TYPE, SHORT);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}
