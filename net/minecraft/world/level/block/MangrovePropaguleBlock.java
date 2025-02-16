package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MangrovePropaguleBlock extends SaplingBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<MangrovePropaguleBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(TreeGrower.CODEC.fieldOf("tree").forGetter(mangrovePropaguleBlock -> mangrovePropaguleBlock.treeGrower), propertiesCodec())
            .apply(instance, MangrovePropaguleBlock::new)
    );
    public static final IntegerProperty AGE = BlockStateProperties.AGE_4;
    public static final int MAX_AGE = 4;
    private static final VoxelShape[] SHAPE_PER_AGE = new VoxelShape[]{
        Block.box(7.0, 13.0, 7.0, 9.0, 16.0, 9.0),
        Block.box(7.0, 10.0, 7.0, 9.0, 16.0, 9.0),
        Block.box(7.0, 7.0, 7.0, 9.0, 16.0, 9.0),
        Block.box(7.0, 3.0, 7.0, 9.0, 16.0, 9.0),
        Block.box(7.0, 0.0, 7.0, 9.0, 16.0, 9.0)
    };
    private static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty HANGING = BlockStateProperties.HANGING;

    @Override
    public MapCodec<MangrovePropaguleBlock> codec() {
        return CODEC;
    }

    public MangrovePropaguleBlock(TreeGrower treeGrower, BlockBehaviour.Properties properties) {
        super(treeGrower, properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(STAGE, Integer.valueOf(0))
                .setValue(AGE, Integer.valueOf(0))
                .setValue(WATERLOGGED, Boolean.valueOf(false))
                .setValue(HANGING, Boolean.valueOf(false))
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE).add(AGE).add(WATERLOGGED).add(HANGING);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return super.mayPlaceOn(state, level, pos) || state.is(Blocks.CLAY);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        boolean flag = fluidState.getType() == Fluids.WATER;
        return super.getStateForPlacement(context).setValue(WATERLOGGED, Boolean.valueOf(flag)).setValue(AGE, Integer.valueOf(4));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Vec3 offset = state.getOffset(pos);
        VoxelShape voxelShape;
        if (!state.getValue(HANGING)) {
            voxelShape = SHAPE_PER_AGE[4];
        } else {
            voxelShape = SHAPE_PER_AGE[state.getValue(AGE)];
        }

        return voxelShape.move(offset.x, offset.y, offset.z);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return isHanging(state) ? level.getBlockState(pos.above()).is(Blocks.MANGROVE_LEAVES) : super.canSurvive(state, level, pos);
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

        return direction == Direction.UP && !state.canSurvive(level, pos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!isHanging(state)) {
            if (random.nextInt(7) == 0) {
                this.advanceTree(level, pos, state, random);
            }
        } else {
            if (!isFullyGrown(state)) {
                level.setBlock(pos, state.cycle(AGE), 2);
            }
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return !isHanging(state) || !isFullyGrown(state);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return isHanging(state) ? !isFullyGrown(state) : super.isBonemealSuccess(level, random, pos, state);
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        if (isHanging(state) && !isFullyGrown(state)) {
            level.setBlock(pos, state.cycle(AGE), 2);
        } else {
            super.performBonemeal(level, random, pos, state);
        }
    }

    private static boolean isHanging(BlockState state) {
        return state.getValue(HANGING);
    }

    private static boolean isFullyGrown(BlockState state) {
        return state.getValue(AGE) == 4;
    }

    public static BlockState createNewHangingPropagule() {
        return createNewHangingPropagule(0);
    }

    public static BlockState createNewHangingPropagule(int age) {
        return Blocks.MANGROVE_PROPAGULE.defaultBlockState().setValue(HANGING, Boolean.valueOf(true)).setValue(AGE, Integer.valueOf(age));
    }
}
