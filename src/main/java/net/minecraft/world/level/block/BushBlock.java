package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;

public abstract class BushBlock extends Block {

    protected BushBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    protected abstract MapCodec<? extends BushBlock> codec();

    protected boolean mayPlaceOn(BlockState floor, BlockGetter world, BlockPos pos) {
        return floor.is(BlockTags.DIRT) || floor.is(Blocks.FARMLAND);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        // CraftBukkit start
        if (!state.canSurvive(world, pos)) {
            if (!(world instanceof net.minecraft.server.level.ServerLevel && ((net.minecraft.server.level.ServerLevel) world).hasPhysicsEvent) || !org.bukkit.craftbukkit.event.CraftEventFactory.callBlockPhysicsEvent(world, pos).isCancelled()) { // Paper
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
        // CraftBukkit end
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        BlockPos blockposition1 = pos.below();

        return this.mayPlaceOn(world.getBlockState(blockposition1), world, blockposition1);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) {
        return state.getFluidState().isEmpty();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type == PathComputationType.AIR && !this.hasCollision ? true : super.isPathfindable(state, type);
    }

    // Purpur start
    public void playerDestroyAndReplant(net.minecraft.world.level.Level world, net.minecraft.world.entity.player.Player player, BlockPos pos, BlockState state, @javax.annotation.Nullable net.minecraft.world.level.block.entity.BlockEntity blockEntity, net.minecraft.world.item.ItemStack itemInHand, net.minecraft.world.level.ItemLike itemToReplant) {
        player.awardStat(net.minecraft.stats.Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.025F, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.BLOCK_MINED);//0.005F, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.BLOCK_MINED);
        java.util.List<net.minecraft.world.item.ItemStack> dropList = Block.getDrops(state, (net.minecraft.server.level.ServerLevel) world, pos, blockEntity, player, itemInHand);

        boolean planted = false;
        for (net.minecraft.world.item.ItemStack itemToDrop : dropList) {
            if (!planted && itemToDrop.getItem() == itemToReplant) {
                world.setBlock(pos, defaultBlockState(), 3);
                itemToDrop.setCount(itemToDrop.getCount() - 1);
                planted = true;
            }
            Block.popResource(world, pos, itemToDrop);
        }

        state.spawnAfterBreak((net.minecraft.server.level.ServerLevel) world, pos, itemInHand, true);
    }
    // Purpur end
}
