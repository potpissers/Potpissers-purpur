package net.minecraft.world;

import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface Container extends Clearable {
    float DEFAULT_DISTANCE_BUFFER = 4.0F;

    int getContainerSize();

    boolean isEmpty();

    ItemStack getItem(int slot);

    ItemStack removeItem(int slot, int amount);

    ItemStack removeItemNoUpdate(int slot);

    void setItem(int slot, ItemStack stack);

    int getMaxStackSize(); // CraftBukkit

    default int getMaxStackSize(ItemStack stack) {
        return Math.min(this.getMaxStackSize(), stack.getMaxStackSize());
    }

    void setChanged();

    boolean stillValid(Player player);

    default void startOpen(Player player) {
    }

    default void stopOpen(Player player) {
    }

    default boolean canPlaceItem(int slot, ItemStack stack) {
        return true;
    }

    default boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return true;
    }

    default int countItem(Item item) {
        int i = 0;

        for (int i1 = 0; i1 < this.getContainerSize(); i1++) {
            ItemStack item1 = this.getItem(i1);
            if (item1.getItem().equals(item)) {
                i += item1.getCount();
            }
        }

        return i;
    }

    default boolean hasAnyOf(Set<Item> set) {
        return this.hasAnyMatching(item -> !item.isEmpty() && set.contains(item.getItem()));
    }

    default boolean hasAnyMatching(Predicate<ItemStack> predicate) {
        for (int i = 0; i < this.getContainerSize(); i++) {
            ItemStack item = this.getItem(i);
            if (predicate.test(item)) {
                return true;
            }
        }

        return false;
    }

    static boolean stillValidBlockEntity(BlockEntity blockEntity, Player player) {
        return stillValidBlockEntity(blockEntity, player, 4.0F);
    }

    static boolean stillValidBlockEntity(BlockEntity blockEntity, Player player, float distance) {
        Level level = blockEntity.getLevel();
        BlockPos blockPos = blockEntity.getBlockPos();
        return level != null && level.getBlockEntity(blockPos) == blockEntity && player.canInteractWithBlock(blockPos, distance);
    }

    // CraftBukkit start
    java.util.List<ItemStack> getContents();

    void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity player);

    void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity player);

    java.util.List<org.bukkit.entity.HumanEntity> getViewers();

    org.bukkit.inventory.@org.jetbrains.annotations.Nullable InventoryHolder getOwner();

    void setMaxStackSize(int size);

    org.bukkit.Location getLocation();

    int MAX_STACK = Item.ABSOLUTE_MAX_STACK_SIZE;
    // CraftBukkit end
}
