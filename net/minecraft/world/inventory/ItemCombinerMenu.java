package net.minecraft.world.inventory;

import javax.annotation.Nullable;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public abstract class ItemCombinerMenu extends AbstractContainerMenu {
    private static final int INVENTORY_SLOTS_PER_ROW = 9;
    private static final int INVENTORY_ROWS = 3;
    private static final int INPUT_SLOT_START = 0;
    protected final ContainerLevelAccess access;
    protected final Player player;
    protected final Container inputSlots;
    protected final ResultContainer resultSlots; // Paper - Add missing InventoryHolders; delay field init
    private final int resultSlotIndex;

    protected boolean mayPickup(Player player, boolean hasStack) {
        return true;
    }

    protected abstract void onTake(Player player, ItemStack stack);

    protected abstract boolean isValidBlock(BlockState state);

    public ItemCombinerMenu(
        @Nullable MenuType<?> menuType, int containerId, Inventory inventory, ContainerLevelAccess access, ItemCombinerMenuSlotDefinition slotDefinition
    ) {
        super(menuType, containerId);
        this.access = access;
        // Paper start - Add missing InventoryHolders; delay field init
        this.resultSlots = new ResultContainer(this.createBlockHolder(this.access)) {
            @Override
            public void setChanged() {
                ItemCombinerMenu.this.slotsChanged(this);
            }
        };
        // Paper end - Add missing InventoryHolders; delay field init
        this.player = inventory.player;
        this.inputSlots = this.createContainer(slotDefinition.getNumOfInputSlots());
        this.resultSlotIndex = slotDefinition.getResultSlotIndex();
        this.createInputSlots(slotDefinition);
        this.createResultSlot(slotDefinition);
        this.addStandardInventorySlots(inventory, 8, 84);
    }

    private void createInputSlots(ItemCombinerMenuSlotDefinition slotDefinition) {
        for (final ItemCombinerMenuSlotDefinition.SlotDefinition slotDefinition1 : slotDefinition.getSlots()) {
            this.addSlot(new Slot(this.inputSlots, slotDefinition1.slotIndex(), slotDefinition1.x(), slotDefinition1.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return slotDefinition1.mayPlace().test(stack);
                }
            });
        }
    }

    private void createResultSlot(ItemCombinerMenuSlotDefinition slotDefinition) {
        this.addSlot(
            new Slot(this.resultSlots, slotDefinition.getResultSlot().slotIndex(), slotDefinition.getResultSlot().x(), slotDefinition.getResultSlot().y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return ItemCombinerMenu.this.mayPickup(player, this.hasItem());
                }

                @Override
                public void onTake(Player player, ItemStack stack) {
                    ItemCombinerMenu.this.onTake(player, stack);
                }
            }
        );
    }

    public abstract void createResult();

    private SimpleContainer createContainer(int size) {
        return new SimpleContainer(this.createBlockHolder(this.access), size) {
            @Override
            public void setChanged() {
                super.setChanged();
                ItemCombinerMenu.this.slotsChanged(this);
            }
        };
    }

    @Override
    public void slotsChanged(Container inventory) {
        super.slotsChanged(inventory);
        if (inventory == this.inputSlots) {
            this.createResult();
            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareResultEvent(this, this instanceof SmithingMenu ? 3 : 2); // Paper - Add PrepareResultEvent
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, blockPos) -> this.clearContainer(player, this.inputSlots));
    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return this.access
            .evaluate((level, blockPos) -> !this.isValidBlock(level.getBlockState(blockPos)) ? false : player.canInteractWithBlock(blockPos, 4.0), true);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            itemStack = item.copy();
            int inventorySlotStart = this.getInventorySlotStart();
            int useRowEnd = this.getUseRowEnd();
            if (index == this.getResultSlot()) {
                if (!this.moveItemStackTo(item, inventorySlotStart, useRowEnd, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(item, itemStack);
            } else if (index >= 0 && index < this.getResultSlot()) {
                if (!this.moveItemStackTo(item, inventorySlotStart, useRowEnd, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.canMoveIntoInputSlots(item) && index >= this.getInventorySlotStart() && index < this.getUseRowEnd()) {
                if (!this.moveItemStackTo(item, 0, this.getResultSlot(), false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= this.getInventorySlotStart() && index < this.getInventorySlotEnd()) {
                if (!this.moveItemStackTo(item, this.getUseRowStart(), this.getUseRowEnd(), false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= this.getUseRowStart()
                && index < this.getUseRowEnd()
                && !this.moveItemStackTo(item, this.getInventorySlotStart(), this.getInventorySlotEnd(), false)) {
                return ItemStack.EMPTY;
            }

            if (item.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (item.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }

            this.activeQuickItem = itemStack; // Purpur - Anvil API
            slot.onTake(player, item);
            this.activeQuickItem = null; // Purpur - Anvil API
        }

        return itemStack;
    }

    protected boolean canMoveIntoInputSlots(ItemStack stack) {
        return true;
    }

    public int getResultSlot() {
        return this.resultSlotIndex;
    }

    private int getInventorySlotStart() {
        return this.getResultSlot() + 1;
    }

    private int getInventorySlotEnd() {
        return this.getInventorySlotStart() + 27;
    }

    private int getUseRowStart() {
        return this.getInventorySlotEnd();
    }

    private int getUseRowEnd() {
        return this.getUseRowStart() + 9;
    }
}
