package net.minecraft.world.entity.npc;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public interface InventoryCarrier {
    String TAG_INVENTORY = "Inventory";

    SimpleContainer getInventory();

    static void pickUpItem(ServerLevel level, Mob mob, InventoryCarrier carrier, ItemEntity itemEntity) {
        ItemStack item = itemEntity.getItem();
        if (mob.wantsToPickUp(level, item)) {
            SimpleContainer inventory = carrier.getInventory();
            boolean canAddItem = inventory.canAddItem(item);
            if (!canAddItem) {
                return;
            }

            mob.onItemPickup(itemEntity);
            int count = item.getCount();
            ItemStack itemStack = inventory.addItem(item);
            mob.take(itemEntity, count - itemStack.getCount());
            if (itemStack.isEmpty()) {
                itemEntity.discard();
            } else {
                item.setCount(itemStack.getCount());
            }
        }
    }

    default void readInventoryFromTag(CompoundTag tag, HolderLookup.Provider levelRegistry) {
        if (tag.contains("Inventory", 9)) {
            this.getInventory().fromTag(tag.getList("Inventory", 10), levelRegistry);
        }
    }

    default void writeInventoryToTag(CompoundTag tag, HolderLookup.Provider levelRegistry) {
        tag.put("Inventory", this.getInventory().createTag(levelRegistry));
    }
}
