package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class ShulkerBoxMenu extends AbstractContainerMenu {
    private static final int CONTAINER_SIZE = 27;
    private final Container container;
    // CraftBukkit start
    private org.bukkit.craftbukkit.inventory.CraftInventoryView bukkitEntity;
    private Inventory player;

    @Override
    public org.bukkit.craftbukkit.inventory.CraftInventoryView getBukkitView() {
        if (this.bukkitEntity != null) {
            return this.bukkitEntity;
        }

        this.bukkitEntity = new org.bukkit.craftbukkit.inventory.CraftInventoryView(this.player.player.getBukkitEntity(), new org.bukkit.craftbukkit.inventory.CraftInventory(this.container), this);
        return this.bukkitEntity;
    }

    @Override
    public void startOpen() {
        container.startOpen(player.player);
    }
    // CraftBukkit end

    public ShulkerBoxMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(27));
    }

    public ShulkerBoxMenu(int containerId, Inventory playerInventory, Container container) {
        super(MenuType.SHULKER_BOX, containerId);
        checkContainerSize(container, 27);
        this.container = container;
        this.player = playerInventory; // CraftBukkit - save player
        // container.startOpen(playerInventory.player); // Paper - don't startOpen until menu actually opens
        int i = 3;
        int i1 = 9;

        for (int i2 = 0; i2 < 3; i2++) {
            for (int i3 = 0; i3 < 9; i3++) {
                this.addSlot(new ShulkerBoxSlot(container, i3 + i2 * 9, 8 + i3 * 18, 18 + i2 * 18));
            }
        }

        this.addStandardInventorySlots(playerInventory, 8, 84);
    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            itemStack = item.copy();
            if (index < this.container.getContainerSize()) {
                if (!this.moveItemStackTo(item, this.container.getContainerSize(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(item, 0, this.container.getContainerSize(), false)) {
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
}
