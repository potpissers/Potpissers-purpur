package net.minecraft.world.item;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SignItem extends StandingAndWallBlockItem {
    public SignItem(Block standingBlock, Block wallBlock, Item.Properties properties) {
        super(standingBlock, wallBlock, Direction.DOWN, properties);
    }

    public SignItem(Item.Properties properties, Block standingBlock, Block wallBlock, Direction attachmentDirection) {
        super(standingBlock, wallBlock, attachmentDirection, properties);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state) {
        boolean flag = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (!level.isClientSide
            && !flag
            && player != null
            && level.getBlockEntity(pos) instanceof SignBlockEntity signBlockEntity
            && level.getBlockState(pos).getBlock() instanceof SignBlock signBlock) {
            signBlock.openTextEdit(player, signBlockEntity, true);
        }

        return flag;
    }
}
