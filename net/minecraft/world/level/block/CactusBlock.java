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

public class CactusBlock extends Block implements BonemealableBlock { // Purpur - bonemealable cactus
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

            if (i < level.paperConfig().maxGrowthHeight.cactus) { // Paper - Configurable cactus/bamboo/reed growth height
                int ageValue = state.getValue(AGE);

                int modifier = level.spigotConfig.cactusModifier; // Spigot - SPIGOT-7159: Better modifier resolution
                if (ageValue >= 15 || (modifier != 100 && random.nextFloat() < (modifier / (100.0f * 16)))) { // Spigot - SPIGOT-7159: Better modifier
                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, blockPos, this.defaultBlockState()); // CraftBukkit
                    BlockState blockState = state.setValue(AGE, Integer.valueOf(0));
                    level.setBlock(pos, blockState, 4);
                    level.neighborChanged(blockState, blockPos, this, null, false);
                } else if (modifier == 100 || random.nextFloat() < (modifier / (100.0f * 16))) { // Spigot - SPIGOT-7159: Better modifier resolution
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
            if ((level.getWorldBorder().world.purpurConfig.cactusBreaksFromSolidNeighbors && blockState.isSolid()) || level.getFluidState(pos.relative(direction)).is(FluidTags.LAVA)) { // Purpur - Cactus breaks from solid neighbors config
                return false;
            }
        }

        BlockState blockState1 = level.getBlockState(pos.below());
        return (blockState1.is(Blocks.CACTUS) || blockState1.is(BlockTags.SAND)) && !level.getBlockState(pos.above()).liquid();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        entity.hurt(level.damageSources().cactus().directBlock(level, pos), 1.0F); // CraftBukkit
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    // Purpur start - bonemealable cactus
    @Override
    public boolean isValidBonemealTarget(final LevelReader world, final BlockPos pos, final BlockState state) {
        if (!((Level) world).purpurConfig.cactusAffectedByBonemeal || !world.isEmptyBlock(pos.above())) return false;

        int cactusHeight = 0;
        while (world.getBlockState(pos.below(cactusHeight)).is(this)) {
            cactusHeight++;
        }

        return cactusHeight < ((Level) world).paperConfig().maxGrowthHeight.cactus;
    }

    @Override
    public boolean isBonemealSuccess(Level world, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel world, RandomSource random, BlockPos pos, BlockState state) {
        int cactusHeight = 0;
        while (world.getBlockState(pos.below(cactusHeight)).is(this)) {
            cactusHeight++;
        }
        for (int i = 0; i <= world.paperConfig().maxGrowthHeight.cactus - cactusHeight; i++) {
            world.setBlockAndUpdate(pos.above(i), state.setValue(CactusBlock.AGE, 0));
        }
    }
    // Purpur end - bonemealable cactus
}
