package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class DispenserBlockEntity extends RandomizableContainerBlockEntity {
    public static final int CONTAINER_SIZE = 9;
    private NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);

    // CraftBukkit start - add fields and methods
    public java.util.List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = MAX_STACK;

    public java.util.List<net.minecraft.world.item.ItemStack> getContents() {
        return this.items;
    }

    public void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.add(player);
    }

    public void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.remove(player);
    }

    public java.util.List<org.bukkit.entity.HumanEntity> getViewers() {
        return this.transaction;
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack;
    }

    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }
    // CraftBukkit end

    protected DispenserBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public DispenserBlockEntity(BlockPos pos, BlockState blockState) {
        this(BlockEntityType.DISPENSER, pos, blockState);
    }

    @Override
    public int getContainerSize() {
        return 9;
    }

    public int getRandomSlot(RandomSource random) {
        this.unpackLootTable(null);
        int i = -1;
        int i1 = 1;

        for (int i2 = 0; i2 < this.items.size(); i2++) {
            if (!this.items.get(i2).isEmpty() && random.nextInt(i1++) == 0) {
                i = i2;
            }
        }

        return i;
    }

    public ItemStack insertItem(ItemStack stack) {
        int maxStackSize = this.getMaxStackSize(stack);

        for (int i = 0; i < this.items.size(); i++) {
            ItemStack itemStack = this.items.get(i);
            if (itemStack.isEmpty() || ItemStack.isSameItemSameComponents(stack, itemStack)) {
                int min = Math.min(stack.getCount(), maxStackSize - itemStack.getCount());
                if (min > 0) {
                    if (itemStack.isEmpty()) {
                        this.setItem(i, stack.split(min));
                    } else {
                        stack.shrink(min);
                        itemStack.grow(min);
                    }
                }

                if (stack.isEmpty()) {
                    break;
                }
            }
        }

        return stack;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.dispenser");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, this.items, registries);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, this.items, registries);
        }
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory player) {
        return new DispenserMenu(id, player, this);
    }
}
