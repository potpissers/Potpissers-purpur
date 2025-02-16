package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BambooSaplingBlock extends Block implements BonemealableBlock {
    public static final MapCodec<BambooSaplingBlock> CODEC = simpleCodec(BambooSaplingBlock::new);
    protected static final float SAPLING_AABB_OFFSET = 4.0F;
    protected static final VoxelShape SAPLING_SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 12.0, 12.0);

    @Override
    public MapCodec<BambooSaplingBlock> codec() {
        return CODEC;
    }

    public BambooSaplingBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Vec3 offset = state.getOffset(pos);
        return SAPLING_SHAPE.move(offset.x, offset.y, offset.z);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < (level.spigotConfig.bambooModifier / (100.0f * 3)) && level.isEmptyBlock(pos.above()) && level.getRawBrightness(pos.above(), 0) >= 9) { // Spigot - SPIGOT-7159: Better modifier resolution
            this.growBamboo(level, pos);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(BlockTags.BAMBOO_PLANTABLE_ON);
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
            return Blocks.AIR.defaultBlockState();
        } else {
            return direction == Direction.UP && neighborState.is(Blocks.BAMBOO)
                ? Blocks.BAMBOO.defaultBlockState()
                : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(Items.BAMBOO);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return level.getBlockState(pos.above()).isAir();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        this.growBamboo(level, pos);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return player.getMainHandItem().getItem() instanceof SwordItem ? 1.0F : super.getDestroyProgress(state, player, level, pos);
    }

    protected void growBamboo(Level level, BlockPos state) {
        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, state, state.above(), Blocks.BAMBOO.defaultBlockState().setValue(BambooStalkBlock.LEAVES, BambooLeaves.SMALL), 3); // CraftBukkit - BlockSpreadEvent
    }
}
