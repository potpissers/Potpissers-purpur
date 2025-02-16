package net.minecraft.world;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

public class ContainerHelper {
    public static final String TAG_ITEMS = "Items";

    public static ItemStack removeItem(List<ItemStack> stacks, int index, int amount) {
        return index >= 0 && index < stacks.size() && !stacks.get(index).isEmpty() && amount > 0 ? stacks.get(index).split(amount) : ItemStack.EMPTY;
    }

    public static ItemStack takeItem(List<ItemStack> stacks, int index) {
        return index >= 0 && index < stacks.size() ? stacks.set(index, ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    public static CompoundTag saveAllItems(CompoundTag tag, NonNullList<ItemStack> items, HolderLookup.Provider levelRegistry) {
        return saveAllItems(tag, items, true, levelRegistry);
    }

    public static CompoundTag saveAllItems(CompoundTag tag, NonNullList<ItemStack> items, boolean alwaysPutTag, HolderLookup.Provider levelRegistry) {
        ListTag listTag = new ListTag();

        for (int i = 0; i < items.size(); i++) {
            ItemStack itemStack = items.get(i);
            if (!itemStack.isEmpty()) {
                CompoundTag compoundTag = new CompoundTag();
                compoundTag.putByte("Slot", (byte)i);
                listTag.add(itemStack.save(levelRegistry, compoundTag));
            }
        }

        if (!listTag.isEmpty() || alwaysPutTag) {
            tag.put("Items", listTag);
        }

        return tag;
    }

    public static void loadAllItems(CompoundTag tag, NonNullList<ItemStack> items, HolderLookup.Provider levelRegistry) {
        ListTag list = tag.getList("Items", 10);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag compound = list.getCompound(i);
            int i1 = compound.getByte("Slot") & 255;
            if (i1 >= 0 && i1 < items.size()) {
                items.set(i1, ItemStack.parse(levelRegistry, compound).orElse(ItemStack.EMPTY));
            }
        }
    }

    public static int clearOrCountMatchingItems(Container container, Predicate<ItemStack> itemPredicate, int maxItems, boolean simulate) {
        int i = 0;

        for (int i1 = 0; i1 < container.getContainerSize(); i1++) {
            ItemStack item = container.getItem(i1);
            int i2 = clearOrCountMatchingItems(item, itemPredicate, maxItems - i, simulate);
            if (i2 > 0 && !simulate && item.isEmpty()) {
                container.setItem(i1, ItemStack.EMPTY);
            }

            i += i2;
        }

        return i;
    }

    public static int clearOrCountMatchingItems(ItemStack stack, Predicate<ItemStack> itemPredicate, int maxItems, boolean simulate) {
        if (stack.isEmpty() || !itemPredicate.test(stack)) {
            return 0;
        } else if (simulate) {
            return stack.getCount();
        } else {
            int i = maxItems < 0 ? stack.getCount() : Math.min(maxItems, stack.getCount());
            stack.shrink(i);
            return i;
        }
    }
}
