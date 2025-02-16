package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BigDripleafStemBlock extends HorizontalDirectionalBlock implements BonemealableBlock, SimpleWaterloggedBlock {
    public static final MapCodec<BigDripleafStemBlock> CODEC = simpleCodec(BigDripleafStemBlock::new);
    private static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final int STEM_WIDTH = 6;
    protected static final VoxelShape NORTH_SHAPE = Block.box(5.0, 0.0, 9.0, 11.0, 16.0, 15.0);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(5.0, 0.0, 1.0, 11.0, 16.0, 7.0);
    protected static final VoxelShape EAST_SHAPE = Block.box(1.0, 0.0, 5.0, 7.0, 16.0, 11.0);
    protected static final VoxelShape WEST_SHAPE = Block.box(9.0, 0.0, 5.0, 15.0, 16.0, 11.0);

    @Override
    public MapCodec<BigDripleafStemBlock> codec() {
        return CODEC;
    }

    protected BigDripleafStemBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, Boolean.valueOf(false)).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        switch ((Direction)state.getValue(FACING)) {
            case SOUTH:
                return SOUTH_SHAPE;
            case NORTH:
            default:
                return NORTH_SHAPE;
            case WEST:
                return WEST_SHAPE;
            case EAST:
                return EAST_SHAPE;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED, FACING);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockPos = pos.below();
        BlockState blockState = level.getBlockState(blockPos);
        BlockState blockState1 = level.getBlockState(pos.above());
        return (blockState.is(this) || blockState.is(BlockTags.BIG_DRIPLEAF_PLACEABLE)) && (blockState1.is(this) || blockState1.is(Blocks.BIG_DRIPLEAF));
    }

    protected static boolean place(LevelAccessor level, BlockPos pos, FluidState fluidState, Direction direction) {
        BlockState blockState = Blocks.BIG_DRIPLEAF_STEM
            .defaultBlockState()
            .setValue(WATERLOGGED, Boolean.valueOf(fluidState.isSourceOfType(Fluids.WATER)))
            .setValue(FACING, direction);
        return level.setBlock(pos, blockState, 3);
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
        if ((direction == Direction.DOWN || direction == Direction.UP) && !state.canSurvive(level, pos)) {
            scheduledTickAccess.scheduleTick(pos, this, 1);
        }

        if (state.getValue(WATERLOGGED)) {
            scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        Optional<BlockPos> topConnectedBlock = BlockUtil.getTopConnectedBlock(level, pos, state.getBlock(), Direction.UP, Blocks.BIG_DRIPLEAF);
        if (topConnectedBlock.isEmpty()) {
            return false;
        } else {
            BlockPos blockPos = topConnectedBlock.get().above();
            BlockState blockState = level.getBlockState(blockPos);
            return BigDripleafBlock.canPlaceAt(level, blockPos, blockState);
        }
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        Optional<BlockPos> topConnectedBlock = BlockUtil.getTopConnectedBlock(level, pos, state.getBlock(), Direction.UP, Blocks.BIG_DRIPLEAF);
        if (!topConnectedBlock.isEmpty()) {
            BlockPos blockPos = topConnectedBlock.get();
            BlockPos blockPos1 = blockPos.above();
            Direction direction = state.getValue(FACING);
            place(level, blockPos, level.getFluidState(blockPos), direction);
            BigDripleafBlock.place(level, blockPos1, level.getFluidState(blockPos1), direction);
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(Blocks.BIG_DRIPLEAF);
    }
}
