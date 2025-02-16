package net.minecraft.world.level.block.entity;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;

public class CrafterBlockEntity extends RandomizableContainerBlockEntity implements CraftingContainer {
    public static final int CONTAINER_WIDTH = 3;
    public static final int CONTAINER_HEIGHT = 3;
    public static final int CONTAINER_SIZE = 9;
    public static final int SLOT_DISABLED = 1;
    public static final int SLOT_ENABLED = 0;
    public static final int DATA_TRIGGERED = 9;
    public static final int NUM_DATA = 10;
    private NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
    public int craftingTicksRemaining = 0;
    protected final ContainerData containerData = new ContainerData() {
        private final int[] slotStates = new int[9];
        private int triggered = 0;

        @Override
        public int get(int index) {
            return index == 9 ? this.triggered : this.slotStates[index];
        }

        @Override
        public void set(int index, int value) {
            if (index == 9) {
                this.triggered = value;
            } else {
                this.slotStates[index] = value;
            }
        }

        @Override
        public int getCount() {
            return 10;
        }
    };

    public CrafterBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.CRAFTER, pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.crafter");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new CrafterMenu(containerId, inventory, this, this.containerData);
    }

    public void setSlotState(int slot, boolean state) {
        if (this.slotCanBeDisabled(slot)) {
            this.containerData.set(slot, state ? 0 : 1);
            this.setChanged();
        }
    }

    public boolean isSlotDisabled(int slot) {
        return slot >= 0 && slot < 9 && this.containerData.get(slot) == 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (this.containerData.get(slot) == 1) {
            return false;
        } else {
            ItemStack itemStack = this.items.get(slot);
            int count = itemStack.getCount();
            return count < itemStack.getMaxStackSize() && (itemStack.isEmpty() || !this.smallerStackExist(count, itemStack, slot));
        }
    }

    private boolean smallerStackExist(int currentSize, ItemStack stack, int slot) {
        for (int i = slot + 1; i < 9; i++) {
            if (!this.isSlotDisabled(i)) {
                ItemStack item = this.getItem(i);
                if (item.isEmpty() || item.getCount() < currentSize && ItemStack.isSameItemSameComponents(item, stack)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.craftingTicksRemaining = tag.getInt("crafting_ticks_remaining");
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, this.items, registries);
        }

        int[] intArray = tag.getIntArray("disabled_slots");

        for (int i = 0; i < 9; i++) {
            this.containerData.set(i, 0);
        }

        for (int i1 : intArray) {
            if (this.slotCanBeDisabled(i1)) {
                this.containerData.set(i1, 1);
            }
        }

        this.containerData.set(9, tag.getInt("triggered"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("crafting_ticks_remaining", this.craftingTicksRemaining);
        if (!this.trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, this.items, registries);
        }

        this.addDisabledSlots(tag);
        this.addTriggered(tag);
    }

    @Override
    public int getContainerSize() {
        return 9;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemStack : this.items) {
            if (!itemStack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (this.isSlotDisabled(slot)) {
            this.setSlotState(slot, true);
        }

        super.setItem(slot, stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getWidth() {
        return 3;
    }

    @Override
    public int getHeight() {
        return 3;
    }

    @Override
    public void fillStackedContents(StackedItemContents stackedContents) {
        for (ItemStack itemStack : this.items) {
            stackedContents.accountSimpleStack(itemStack);
        }
    }

    private void addDisabledSlots(CompoundTag tag) {
        IntList list = new IntArrayList();

        for (int i = 0; i < 9; i++) {
            if (this.isSlotDisabled(i)) {
                list.add(i);
            }
        }

        tag.putIntArray("disabled_slots", list);
    }

    private void addTriggered(CompoundTag tag) {
        tag.putInt("triggered", this.containerData.get(9));
    }

    public void setTriggered(boolean triggered) {
        this.containerData.set(9, triggered ? 1 : 0);
    }

    @VisibleForTesting
    public boolean isTriggered() {
        return this.containerData.get(9) == 1;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrafterBlockEntity crafter) {
        int i = crafter.craftingTicksRemaining - 1;
        if (i >= 0) {
            crafter.craftingTicksRemaining = i;
            if (i == 0) {
                level.setBlock(pos, state.setValue(CrafterBlock.CRAFTING, Boolean.valueOf(false)), 3);
            }
        }
    }

    public void setCraftingTicksRemaining(int craftingTicksRemaining) {
        this.craftingTicksRemaining = craftingTicksRemaining;
    }

    public int getRedstoneSignal() {
        int i = 0;

        for (int i1 = 0; i1 < this.getContainerSize(); i1++) {
            ItemStack item = this.getItem(i1);
            if (!item.isEmpty() || this.isSlotDisabled(i1)) {
                i++;
            }
        }

        return i;
    }

    private boolean slotCanBeDisabled(int slot) {
        return slot > -1 && slot < 9 && this.items.get(slot).isEmpty();
    }
}
