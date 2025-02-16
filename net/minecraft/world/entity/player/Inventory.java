package net.minecraft.world.entity.player;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class Inventory implements Container, Nameable {
    public static final int POP_TIME_DURATION = 5;
    public static final int INVENTORY_SIZE = 36;
    public static final int SELECTION_SIZE = 9;
    public static final int SLOT_OFFHAND = 40;
    public static final int NOT_FOUND_INDEX = -1;
    public final NonNullList<ItemStack> items = NonNullList.withSize(36, ItemStack.EMPTY);
    public final NonNullList<ItemStack> armor = NonNullList.withSize(4, ItemStack.EMPTY);
    public final NonNullList<ItemStack> offhand = NonNullList.withSize(1, ItemStack.EMPTY);
    private final List<NonNullList<ItemStack>> compartments = ImmutableList.of(this.items, this.armor, this.offhand);
    public int selected;
    public final Player player;
    private int timesChanged;

    public Inventory(Player player) {
        this.player = player;
    }

    public ItemStack getSelected() {
        return isHotbarSlot(this.selected) ? this.items.get(this.selected) : ItemStack.EMPTY;
    }

    public static int getSelectionSize() {
        return 9;
    }

    private boolean hasRemainingSpaceForItem(ItemStack destination, ItemStack origin) {
        return !destination.isEmpty()
            && ItemStack.isSameItemSameComponents(destination, origin)
            && destination.isStackable()
            && destination.getCount() < this.getMaxStackSize(destination);
    }

    public int getFreeSlot() {
        for (int i = 0; i < this.items.size(); i++) {
            if (this.items.get(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    public void addAndPickItem(ItemStack stack) {
        this.selected = this.getSuitableHotbarSlot();
        if (!this.items.get(this.selected).isEmpty()) {
            int freeSlot = this.getFreeSlot();
            if (freeSlot != -1) {
                this.items.set(freeSlot, this.items.get(this.selected));
            }
        }

        this.items.set(this.selected, stack);
    }

    public void pickSlot(int index) {
        this.selected = this.getSuitableHotbarSlot();
        ItemStack itemStack = this.items.get(this.selected);
        this.items.set(this.selected, this.items.get(index));
        this.items.set(index, itemStack);
    }

    public static boolean isHotbarSlot(int index) {
        return index >= 0 && index < 9;
    }

    public int findSlotMatchingItem(ItemStack stack) {
        for (int i = 0; i < this.items.size(); i++) {
            if (!this.items.get(i).isEmpty() && ItemStack.isSameItemSameComponents(stack, this.items.get(i))) {
                return i;
            }
        }

        return -1;
    }

    public static boolean isUsableForCrafting(ItemStack stack) {
        return !stack.isDamaged() && !stack.isEnchanted() && !stack.has(DataComponents.CUSTOM_NAME);
    }

    public int findSlotMatchingCraftingIngredient(Holder<Item> item, ItemStack stack) {
        for (int i = 0; i < this.items.size(); i++) {
            ItemStack itemStack = this.items.get(i);
            if (!itemStack.isEmpty()
                && itemStack.is(item)
                && isUsableForCrafting(itemStack)
                && (stack.isEmpty() || ItemStack.isSameItemSameComponents(stack, itemStack))) {
                return i;
            }
        }

        return -1;
    }

    public int getSuitableHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            int i1 = (this.selected + i) % 9;
            if (this.items.get(i1).isEmpty()) {
                return i1;
            }
        }

        for (int ix = 0; ix < 9; ix++) {
            int i1 = (this.selected + ix) % 9;
            if (!this.items.get(i1).isEnchanted()) {
                return i1;
            }
        }

        return this.selected;
    }

    public void setSelectedHotbarSlot(int selectedHotbarSlot) {
        this.selected = selectedHotbarSlot;
    }

    public int clearOrCountMatchingItems(Predicate<ItemStack> stackPredicate, int maxCount, Container inventory) {
        int i = 0;
        boolean flag = maxCount == 0;
        i += ContainerHelper.clearOrCountMatchingItems(this, stackPredicate, maxCount - i, flag);
        i += ContainerHelper.clearOrCountMatchingItems(inventory, stackPredicate, maxCount - i, flag);
        ItemStack carried = this.player.containerMenu.getCarried();
        i += ContainerHelper.clearOrCountMatchingItems(carried, stackPredicate, maxCount - i, flag);
        if (carried.isEmpty()) {
            this.player.containerMenu.setCarried(ItemStack.EMPTY);
        }

        return i;
    }

    private int addResource(ItemStack stack) {
        int slotWithRemainingSpace = this.getSlotWithRemainingSpace(stack);
        if (slotWithRemainingSpace == -1) {
            slotWithRemainingSpace = this.getFreeSlot();
        }

        return slotWithRemainingSpace == -1 ? stack.getCount() : this.addResource(slotWithRemainingSpace, stack);
    }

    private int addResource(int slot, ItemStack stack) {
        int count = stack.getCount();
        ItemStack item = this.getItem(slot);
        if (item.isEmpty()) {
            item = stack.copyWithCount(0);
            this.setItem(slot, item);
        }

        int i = this.getMaxStackSize(item) - item.getCount();
        int min = Math.min(count, i);
        if (min == 0) {
            return count;
        } else {
            count -= min;
            item.grow(min);
            item.setPopTime(5);
            return count;
        }
    }

    public int getSlotWithRemainingSpace(ItemStack stack) {
        if (this.hasRemainingSpaceForItem(this.getItem(this.selected), stack)) {
            return this.selected;
        } else if (this.hasRemainingSpaceForItem(this.getItem(40), stack)) {
            return 40;
        } else {
            for (int i = 0; i < this.items.size(); i++) {
                if (this.hasRemainingSpaceForItem(this.items.get(i), stack)) {
                    return i;
                }
            }

            return -1;
        }
    }

    public void tick() {
        for (NonNullList<ItemStack> list : this.compartments) {
            for (int i = 0; i < list.size(); i++) {
                if (!list.get(i).isEmpty()) {
                    list.get(i).inventoryTick(this.player.level(), this.player, i, this.selected == i);
                }
            }
        }
    }

    public boolean add(ItemStack stack) {
        return this.add(-1, stack);
    }

    public boolean add(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        } else {
            try {
                if (stack.isDamaged()) {
                    if (slot == -1) {
                        slot = this.getFreeSlot();
                    }

                    if (slot >= 0) {
                        this.items.set(slot, stack.copyAndClear());
                        this.items.get(slot).setPopTime(5);
                        return true;
                    } else if (this.player.hasInfiniteMaterials()) {
                        stack.setCount(0);
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    int count;
                    do {
                        count = stack.getCount();
                        if (slot == -1) {
                            stack.setCount(this.addResource(stack));
                        } else {
                            stack.setCount(this.addResource(slot, stack));
                        }
                    } while (!stack.isEmpty() && stack.getCount() < count);

                    if (stack.getCount() == count && this.player.hasInfiniteMaterials()) {
                        stack.setCount(0);
                        return true;
                    } else {
                        return stack.getCount() < count;
                    }
                }
            } catch (Throwable var6) {
                CrashReport crashReport = CrashReport.forThrowable(var6, "Adding item to inventory");
                CrashReportCategory crashReportCategory = crashReport.addCategory("Item being added");
                crashReportCategory.setDetail("Item ID", Item.getId(stack.getItem()));
                crashReportCategory.setDetail("Item data", stack.getDamageValue());
                crashReportCategory.setDetail("Item name", () -> stack.getHoverName().getString());
                throw new ReportedException(crashReport);
            }
        }
    }

    public void placeItemBackInInventory(ItemStack stack) {
        this.placeItemBackInInventory(stack, true);
    }

    public void placeItemBackInInventory(ItemStack stack, boolean sendPacket) {
        while (!stack.isEmpty()) {
            int slotWithRemainingSpace = this.getSlotWithRemainingSpace(stack);
            if (slotWithRemainingSpace == -1) {
                slotWithRemainingSpace = this.getFreeSlot();
            }

            if (slotWithRemainingSpace == -1) {
                this.player.drop(stack, false);
                break;
            }

            int i = stack.getMaxStackSize() - this.getItem(slotWithRemainingSpace).getCount();
            if (this.add(slotWithRemainingSpace, stack.split(i)) && sendPacket && this.player instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(this.createInventoryUpdatePacket(slotWithRemainingSpace));
            }
        }
    }

    public ClientboundSetPlayerInventoryPacket createInventoryUpdatePacket(int slot) {
        return new ClientboundSetPlayerInventoryPacket(slot, this.getItem(slot).copy());
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        List<ItemStack> list = null;

        for (NonNullList<ItemStack> list1 : this.compartments) {
            if (index < list1.size()) {
                list = list1;
                break;
            }

            index -= list1.size();
        }

        return list != null && !list.get(index).isEmpty() ? ContainerHelper.removeItem(list, index, count) : ItemStack.EMPTY;
    }

    public void removeItem(ItemStack stack) {
        for (NonNullList<ItemStack> list : this.compartments) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) == stack) {
                    list.set(i, ItemStack.EMPTY);
                    break;
                }
            }
        }
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        NonNullList<ItemStack> list = null;

        for (NonNullList<ItemStack> list1 : this.compartments) {
            if (index < list1.size()) {
                list = list1;
                break;
            }

            index -= list1.size();
        }

        if (list != null && !list.get(index).isEmpty()) {
            ItemStack itemStack = list.get(index);
            list.set(index, ItemStack.EMPTY);
            return itemStack;
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        NonNullList<ItemStack> list = null;

        for (NonNullList<ItemStack> list1 : this.compartments) {
            if (index < list1.size()) {
                list = list1;
                break;
            }

            index -= list1.size();
        }

        if (list != null) {
            list.set(index, stack);
        }
    }

    public float getDestroySpeed(BlockState state) {
        return this.items.get(this.selected).getDestroySpeed(state);
    }

    public ListTag save(ListTag listTag) {
        for (int i = 0; i < this.items.size(); i++) {
            if (!this.items.get(i).isEmpty()) {
                CompoundTag compoundTag = new CompoundTag();
                compoundTag.putByte("Slot", (byte)i);
                listTag.add(this.items.get(i).save(this.player.registryAccess(), compoundTag));
            }
        }

        for (int ix = 0; ix < this.armor.size(); ix++) {
            if (!this.armor.get(ix).isEmpty()) {
                CompoundTag compoundTag = new CompoundTag();
                compoundTag.putByte("Slot", (byte)(ix + 100));
                listTag.add(this.armor.get(ix).save(this.player.registryAccess(), compoundTag));
            }
        }

        for (int ixx = 0; ixx < this.offhand.size(); ixx++) {
            if (!this.offhand.get(ixx).isEmpty()) {
                CompoundTag compoundTag = new CompoundTag();
                compoundTag.putByte("Slot", (byte)(ixx + 150));
                listTag.add(this.offhand.get(ixx).save(this.player.registryAccess(), compoundTag));
            }
        }

        return listTag;
    }

    public void load(ListTag listTag) {
        this.items.clear();
        this.armor.clear();
        this.offhand.clear();

        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag compound = listTag.getCompound(i);
            int i1 = compound.getByte("Slot") & 255;
            ItemStack itemStack = ItemStack.parse(this.player.registryAccess(), compound).orElse(ItemStack.EMPTY);
            if (i1 >= 0 && i1 < this.items.size()) {
                this.items.set(i1, itemStack);
            } else if (i1 >= 100 && i1 < this.armor.size() + 100) {
                this.armor.set(i1 - 100, itemStack);
            } else if (i1 >= 150 && i1 < this.offhand.size() + 150) {
                this.offhand.set(i1 - 150, itemStack);
            }
        }
    }

    @Override
    public int getContainerSize() {
        return this.items.size() + this.armor.size() + this.offhand.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemStack : this.items) {
            if (!itemStack.isEmpty()) {
                return false;
            }
        }

        for (ItemStack itemStackx : this.armor) {
            if (!itemStackx.isEmpty()) {
                return false;
            }
        }

        for (ItemStack itemStackxx : this.offhand) {
            if (!itemStackxx.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        List<ItemStack> list = null;

        for (NonNullList<ItemStack> list1 : this.compartments) {
            if (index < list1.size()) {
                list = list1;
                break;
            }

            index -= list1.size();
        }

        return list == null ? ItemStack.EMPTY : list.get(index);
    }

    @Override
    public Component getName() {
        return Component.translatable("container.inventory");
    }

    public ItemStack getArmor(int slot) {
        return this.armor.get(slot);
    }

    public void dropAll() {
        for (List<ItemStack> list : this.compartments) {
            for (int i = 0; i < list.size(); i++) {
                ItemStack itemStack = list.get(i);
                if (!itemStack.isEmpty()) {
                    this.player.drop(itemStack, true, false);
                    list.set(i, ItemStack.EMPTY);
                }
            }
        }
    }

    @Override
    public void setChanged() {
        this.timesChanged++;
    }

    public int getTimesChanged() {
        return this.timesChanged;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.canInteractWithEntity(this.player, 4.0);
    }

    public boolean contains(ItemStack stack) {
        for (List<ItemStack> list : this.compartments) {
            for (ItemStack itemStack : list) {
                if (!itemStack.isEmpty() && ItemStack.isSameItemSameComponents(itemStack, stack)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean contains(TagKey<Item> tag) {
        for (List<ItemStack> list : this.compartments) {
            for (ItemStack itemStack : list) {
                if (!itemStack.isEmpty() && itemStack.is(tag)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean contains(Predicate<ItemStack> predicate) {
        for (List<ItemStack> list : this.compartments) {
            for (ItemStack itemStack : list) {
                if (predicate.test(itemStack)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void replaceWith(Inventory playerInventory) {
        for (int i = 0; i < this.getContainerSize(); i++) {
            this.setItem(i, playerInventory.getItem(i));
        }

        this.selected = playerInventory.selected;
    }

    @Override
    public void clearContent() {
        for (List<ItemStack> list : this.compartments) {
            list.clear();
        }
    }

    public void fillStackedContents(StackedItemContents contents) {
        for (ItemStack itemStack : this.items) {
            contents.accountSimpleStack(itemStack);
        }
    }

    public ItemStack removeFromSelected(boolean removeStack) {
        ItemStack selected = this.getSelected();
        return selected.isEmpty() ? ItemStack.EMPTY : this.removeItem(this.selected, removeStack ? selected.getCount() : 1);
    }
}
