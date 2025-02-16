package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class ChestMenu extends AbstractContainerMenu {
    private final Container container;
    private final int containerRows;

    private ChestMenu(MenuType<?> type, int containerId, Inventory playerInventory, int rows) {
        this(type, containerId, playerInventory, new SimpleContainer(9 * rows), rows);
    }

    public static ChestMenu oneRow(int containerId, Inventory playerInventory) {
        return new ChestMenu(MenuType.GENERIC_9x1, containerId, playerInventory, 1);
    }

    public static ChestMenu twoRows(int containerId, Inventory playerInventory) {
        return new ChestMenu(MenuType.GENERIC_9x2, containerId, playerInventory, 2);
    }

    public static ChestMenu threeRows(int containerId, Inventory playerInventory) {
        return new ChestMenu(MenuType.GENERIC_9x3, containerId, playerInventory, 3);
    }

    public static ChestMenu fourRows(int containerId, Inventory playerInventory) {
        return new ChestMenu(MenuType.GENERIC_9x4, containerId, playerInventory, 4);
    }

    public static ChestMenu fiveRows(int containerId, Inventory playerInventory) {
        return new ChestMenu(MenuType.GENERIC_9x5, containerId, playerInventory, 5);
    }

    public static ChestMenu sixRows(int containerId, Inventory playerInventory) {
        return new ChestMenu(MenuType.GENERIC_9x6, containerId, playerInventory, 6);
    }

    public static ChestMenu threeRows(int containerId, Inventory playerInventory, Container container) {
        return new ChestMenu(MenuType.GENERIC_9x3, containerId, playerInventory, container, 3);
    }

    public static ChestMenu sixRows(int containerId, Inventory playerInventory, Container container) {
        return new ChestMenu(MenuType.GENERIC_9x6, containerId, playerInventory, container, 6);
    }

    public ChestMenu(MenuType<?> type, int containerId, Inventory playerInventory, Container container, int rows) {
        super(type, containerId);
        checkContainerSize(container, rows * 9);
        this.container = container;
        this.containerRows = rows;
        container.startOpen(playerInventory.player);
        int i = 18;
        this.addChestGrid(container, 8, 18);
        int i1 = 18 + this.containerRows * 18 + 13;
        this.addStandardInventorySlots(playerInventory, 8, i1);
    }

    private void addChestGrid(Container container, int x, int y) {
        for (int i = 0; i < this.containerRows; i++) {
            for (int i1 = 0; i1 < 9; i1++) {
                this.addSlot(new Slot(container, i1 + i * 9, x + i1 * 18, y + i * 18));
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            itemStack = item.copy();
            if (index < this.containerRows * 9) {
                if (!this.moveItemStackTo(item, this.containerRows * 9, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(item, 0, this.containerRows * 9, false)) {
                return ItemStack.EMPTY;
            }

            if (item.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemStack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    public Container getContainer() {
        return this.container;
    }

    public int getRowCount() {
        return this.containerRows;
    }
}
