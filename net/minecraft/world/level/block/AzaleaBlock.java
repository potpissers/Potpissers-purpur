package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AzaleaBlock extends BushBlock implements BonemealableBlock {
    public static final MapCodec<AzaleaBlock> CODEC = simpleCodec(AzaleaBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0.0, 8.0, 0.0, 16.0, 16.0, 16.0), Block.box(6.0, 0.0, 6.0, 10.0, 8.0, 10.0));

    @Override
    public MapCodec<AzaleaBlock> codec() {
        return CODEC;
    }

    protected AzaleaBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.CLAY) || super.mayPlaceOn(state, level, pos);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return level.getFluidState(pos.above()).isEmpty();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return level.random.nextFloat() < 0.45;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        // Purpur start - Chance for azalea blocks to grow into trees naturally
        growTree(level, random, pos, state);
    }

    @Override
    public void randomTick(net.minecraft.world.level.block.state.BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        double chance = state.getBlock() == Blocks.FLOWERING_AZALEA ? world.purpurConfig.floweringAzaleaGrowthChance : world.purpurConfig.azaleaGrowthChance;
        if (chance > 0.0D && world.getMaxLocalRawBrightness(pos.above()) > 9 && random.nextDouble() < chance) {
            growTree(world, random, pos, state);
        }
    }

    private void growTree(ServerLevel level, RandomSource random, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        // Purpur end - Chance for azalea blocks to grow into trees naturally
        TreeGrower.AZALEA.growTree(level, level.getChunkSource().getGenerator(), pos, state, random);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}
