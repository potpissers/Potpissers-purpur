package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SugarCaneBlock extends Block implements BonemealableBlock { // Purpur - bonemealable sugarcane
    public static final MapCodec<SugarCaneBlock> CODEC = simpleCodec(SugarCaneBlock::new);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_15;
    protected static final float AABB_OFFSET = 6.0F;
    protected static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

    @Override
    public MapCodec<SugarCaneBlock> codec() {
        return CODEC;
    }

    protected SugarCaneBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, Integer.valueOf(0)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.isEmptyBlock(pos.above())) {
            int i = 1;

            while (level.getBlockState(pos.below(i)).is(this)) {
                i++;
            }

            if (i < level.paperConfig().maxGrowthHeight.reeds) { // Paper - Configurable cactus/bamboo/reed growth height
                int ageValue = state.getValue(AGE);
                int modifier = level.spigotConfig.caneModifier; // Spigot - SPIGOT-7159: Better modifier resolution
                if (ageValue >= 15 || (modifier != 100 && random.nextFloat() < (modifier / (100.0f * 16)))) { // Spigot - SPIGOT-7159: Better modifier resolution
                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, pos.above(), this.defaultBlockState()); // CraftBukkit
                    level.setBlock(pos, state.setValue(AGE, Integer.valueOf(0)), 4);
                } else if (modifier == 100 || random.nextFloat() < (modifier / (100.0f * 16))) { // Spigot - SPIGOT-7159: Better modifier resolution
                    level.setBlock(pos, state.setValue(AGE, Integer.valueOf(ageValue + 1)), 4);
                }
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
        if (!state.canSurvive(level, pos)) {
            scheduledTickAccess.scheduleTick(pos, this, 1);
        }

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos.below());
        if (blockState.is(this)) {
            return true;
        } else {
            if (blockState.is(BlockTags.DIRT) || blockState.is(BlockTags.SAND)) {
                BlockPos blockPos = pos.below();

                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockState blockState1 = level.getBlockState(blockPos.relative(direction));
                    FluidState fluidState = level.getFluidState(blockPos.relative(direction));
                    if (fluidState.is(FluidTags.WATER) || blockState1.is(Blocks.FROSTED_ICE)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    // Purpur start - bonemealable sugarcane
    @Override
    public boolean isValidBonemealTarget(final LevelReader world, final BlockPos pos, final BlockState state) {
        if (!((net.minecraft.world.level.Level) world).purpurConfig.sugarCanAffectedByBonemeal || !world.isEmptyBlock(pos.above())) return false;

        int reedHeight = 0;
        while (world.getBlockState(pos.below(reedHeight)).is(this)) {
            reedHeight++;
        }

        return reedHeight < ((net.minecraft.world.level.Level) world).paperConfig().maxGrowthHeight.reeds;
    }

    @Override
    public boolean isBonemealSuccess(net.minecraft.world.level.Level world, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel world, RandomSource random, BlockPos pos, BlockState state) {
        int reedHeight = 0;
        while (world.getBlockState(pos.below(reedHeight)).is(this)) {
            reedHeight++;
        }
        for (int i = 0; i <= world.paperConfig().maxGrowthHeight.reeds - reedHeight; i++) {
            world.setBlockAndUpdate(pos.above(i), state.setValue(SugarCaneBlock.AGE, 0));
        }
    }
    // Purpur end - bonemealable sugarcane
}
