package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;

public abstract class BushBlock extends Block {
    protected BushBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected abstract MapCodec<? extends BushBlock> codec();

    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND);
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
       // CraftBukkit start
       if (!state.canSurvive(level, pos)) {
           // Suppress during worldgen
           if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel && serverLevel.hasPhysicsEvent) || !org.bukkit.craftbukkit.event.CraftEventFactory.callBlockPhysicsEvent(serverLevel, pos).isCancelled()) { // Paper
               return Blocks.AIR.defaultBlockState();
           }
       }
       return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
       // CraftBukkit end
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockPos = pos.below();
        return this.mayPlaceOn(level.getBlockState(blockPos), level, blockPos);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return state.getFluidState().isEmpty();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return pathComputationType == PathComputationType.AIR && !this.hasCollision || super.isPathfindable(state, pathComputationType);
    }

    // Purpur start - Ability for hoe to replant crops
    public void playerDestroyAndReplant(net.minecraft.world.level.Level world, net.minecraft.world.entity.player.Player player, BlockPos pos, BlockState state, @javax.annotation.Nullable net.minecraft.world.level.block.entity.BlockEntity blockEntity, net.minecraft.world.item.ItemStack itemInHand, net.minecraft.world.level.ItemLike itemToReplant) {
        player.awardStat(net.minecraft.stats.Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.BLOCK_MINED);
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
    // Purpur end - Ability for hoe to replant crops
}
