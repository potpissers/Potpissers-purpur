package net.minecraft.world.level.block;

import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface CaveVines {
    VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);
    BooleanProperty BERRIES = BlockStateProperties.BERRIES;

    static InteractionResult use(@Nullable Entity entity, BlockState state, Level level, BlockPos pos) {
        if (state.getValue(BERRIES)) {
            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, pos, (BlockState) state.setValue(CaveVines.BERRIES, false))) {
                return InteractionResult.SUCCESS;
            }

            if (entity instanceof net.minecraft.world.entity.player.Player player) {
                org.bukkit.event.player.PlayerHarvestBlockEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerHarvestBlockEvent(level, pos, player, net.minecraft.world.InteractionHand.MAIN_HAND, java.util.Collections.singletonList(new ItemStack(Items.GLOW_BERRIES, 1)));
                if (event.isCancelled()) {
                    return InteractionResult.SUCCESS; // We need to return a success either way, because making it PASS or FAIL will result in a bug where cancelling while harvesting w/ block in hand places block
                }
                for (org.bukkit.inventory.ItemStack itemStack : event.getItemsHarvested()) {
                    Block.popResource(level, pos, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(itemStack));
                }
            } else {
                Block.popResource(level, pos, new ItemStack(Items.GLOW_BERRIES, 1));
            }
            // CraftBukkit end
            float f = Mth.randomBetween(level.random, 0.8F, 1.2F);
            level.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, f);
            BlockState blockState = state.setValue(BERRIES, Boolean.valueOf(false));
            level.setBlock(pos, blockState, 2);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(entity, blockState));
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    static boolean hasGlowBerries(BlockState state) {
        return state.hasProperty(BERRIES) && state.getValue(BERRIES);
    }

    static ToIntFunction<BlockState> emission(int berries) {
        return state -> state.getValue(BlockStateProperties.BERRIES) ? berries : 0;
    }
}
