package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class HopperMenu extends AbstractContainerMenu {
    public static final int CONTAINER_SIZE = 5;
    private final Container hopper;
    // CraftBukkit start
    private org.bukkit.craftbukkit.inventory.CraftInventoryView bukkitEntity = null;
    private Inventory player;

    @Override
    public org.bukkit.craftbukkit.inventory.CraftInventoryView getBukkitView() {
        if (this.bukkitEntity != null) {
            return this.bukkitEntity;
        }

        org.bukkit.craftbukkit.inventory.CraftInventory inventory = new org.bukkit.craftbukkit.inventory.CraftInventory(this.hopper);
        this.bukkitEntity = new org.bukkit.craftbukkit.inventory.CraftInventoryView(this.player.player.getBukkitEntity(), inventory, this);
        return this.bukkitEntity;
    }
    // CraftBukkit end

    public HopperMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(5));
    }

    public HopperMenu(int containerId, Inventory playerInventory, Container container) {
        super(MenuType.HOPPER, containerId);
        this.hopper = container;
        this.player = playerInventory; // CraftBukkit - save player
        checkContainerSize(container, 5);
        container.startOpen(playerInventory.player);

        for (int i = 0; i < 5; i++) {
            this.addSlot(new Slot(container, i, 44 + i * 18, 20));
        }

        this.addStandardInventorySlots(playerInventory, 8, 51);
    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return this.hopper.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            itemStack = item.copy();
            if (index < this.hopper.getContainerSize()) {
                if (!this.moveItemStackTo(item, this.hopper.getContainerSize(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(item, 0, this.hopper.getContainerSize(), false)) {
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
        this.hopper.stopOpen(player);
    }
}
