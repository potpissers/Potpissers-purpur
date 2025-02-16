package net.minecraft.world.inventory;

import net.minecraft.world.item.ItemStack;

public interface ContainerListener {
    void slotChanged(AbstractContainerMenu containerToSend, int dataSlotIndex, ItemStack stack);

    void dataChanged(AbstractContainerMenu containerMenu, int dataSlotIndex, int value);

    // Paper start - Add PlayerInventorySlotChangeEvent
    default void slotChanged(AbstractContainerMenu containerToSend, int dataSlotIndex, ItemStack oldStack, ItemStack stack) {
        slotChanged(containerToSend, dataSlotIndex, stack);
    }
    // Paper end - Add PlayerInventorySlotChangeEvent
}
