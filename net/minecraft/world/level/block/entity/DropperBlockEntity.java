package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

public class DropperBlockEntity extends DispenserBlockEntity {
    public DropperBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.DROPPER, pos, blockState);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.dropper");
    }
}
