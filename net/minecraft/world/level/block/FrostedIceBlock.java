package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;

public class FrostedIceBlock extends IceBlock {
    public static final MapCodec<FrostedIceBlock> CODEC = simpleCodec(FrostedIceBlock::new);
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    private static final int NEIGHBORS_TO_AGE = 4;
    private static final int NEIGHBORS_TO_MELT = 2;

    @Override
    public MapCodec<FrostedIceBlock> codec() {
        return CODEC;
    }

    public FrostedIceBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, Integer.valueOf(0)));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        level.scheduleTick(pos, this, Mth.nextInt(level.getRandom(), 60, 120));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if ((random.nextInt(3) == 0 || this.fewerNeigboursThan(level, pos, 4))
            && level.getMaxLocalRawBrightness(pos) > 11 - state.getValue(AGE) - state.getLightBlock()
            && this.slightlyMelt(state, level, pos)) {
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (Direction direction : Direction.values()) {
                mutableBlockPos.setWithOffset(pos, direction);
                BlockState blockState = level.getBlockState(mutableBlockPos);
                if (blockState.is(this) && !this.slightlyMelt(blockState, level, mutableBlockPos)) {
                    level.scheduleTick(mutableBlockPos, this, Mth.nextInt(random, 20, 40));
                }
            }
        } else {
            level.scheduleTick(pos, this, Mth.nextInt(random, 20, 40));
        }
    }

    private boolean slightlyMelt(BlockState state, Level level, BlockPos pos) {
        int ageValue = state.getValue(AGE);
        if (ageValue < 3) {
            level.setBlock(pos, state.setValue(AGE, Integer.valueOf(ageValue + 1)), 2);
            return false;
        } else {
            this.melt(state, level, pos);
            return true;
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (neighborBlock.defaultBlockState().is(this) && this.fewerNeigboursThan(level, pos, 2)) {
            this.melt(state, level, pos);
        }

        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
    }

    private boolean fewerNeigboursThan(BlockGetter level, BlockPos pos, int neighborsRequired) {
        int i = 0;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.values()) {
            mutableBlockPos.setWithOffset(pos, direction);
            if (level.getBlockState(mutableBlockPos).is(this)) {
                if (++i >= neighborsRequired) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }
}
