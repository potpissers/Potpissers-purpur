package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CactusBlock extends Block {
    public static final MapCodec<CactusBlock> CODEC = simpleCodec(CactusBlock::new);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_15;
    public static final int MAX_AGE = 15;
    protected static final int AABB_OFFSET = 1;
    protected static final VoxelShape COLLISION_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 15.0, 15.0);
    protected static final VoxelShape OUTLINE_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);

    @Override
    public MapCodec<CactusBlock> codec() {
        return CODEC;
    }

    protected CactusBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, Integer.valueOf(0)));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos blockPos = pos.above();
        if (level.isEmptyBlock(blockPos)) {
            int i = 1;

            while (level.getBlockState(pos.below(i)).is(this)) {
                i++;
            }

            if (i < 3) {
                int ageValue = state.getValue(AGE);
                if (ageValue == 15) {
                    level.setBlockAndUpdate(blockPos, this.defaultBlockState());
                    BlockState blockState = state.setValue(AGE, Integer.valueOf(0));
                    level.setBlock(pos, blockState, 4);
                    level.neighborChanged(blockState, blockPos, this, null, false);
                } else {
                    level.setBlock(pos, state.setValue(AGE, Integer.valueOf(ageValue + 1)), 4);
                }
            }
        }
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION_SHAPE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE_SHAPE;
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
        if (!state.canSurvive(level, pos)) {
            scheduledTickAccess.scheduleTick(pos, this, 1);
        }

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState blockState = level.getBlockState(pos.relative(direction));
            if (blockState.isSolid() || level.getFluidState(pos.relative(direction)).is(FluidTags.LAVA)) {
                return false;
            }
        }

        BlockState blockState1 = level.getBlockState(pos.below());
        return (blockState1.is(Blocks.CACTUS) || blockState1.is(BlockTags.SAND)) && !level.getBlockState(pos.above()).liquid();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        entity.hurt(level.damageSources().cactus(), 1.0F);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}
