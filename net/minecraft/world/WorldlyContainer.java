package net.minecraft.world;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

public interface WorldlyContainer extends Container {
    int[] getSlotsForFace(Direction side);

    boolean canPlaceItemThroughFace(int index, ItemStack itemStack, @Nullable Direction direction);

    boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction);
}
