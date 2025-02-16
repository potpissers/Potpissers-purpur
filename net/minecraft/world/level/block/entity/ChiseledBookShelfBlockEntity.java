package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import org.slf4j.Logger;

public class ChiseledBookShelfBlockEntity extends BlockEntity implements Container {
    public static final int MAX_BOOKS_IN_STORAGE = 6;
    private static final Logger LOGGER = LogUtils.getLogger();
    private final NonNullList<ItemStack> items = NonNullList.withSize(6, ItemStack.EMPTY);
    public int lastInteractedSlot = -1;

    public ChiseledBookShelfBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.CHISELED_BOOKSHELF, pos, state);
    }

    private void updateState(int slot) {
        if (slot >= 0 && slot < 6) {
            this.lastInteractedSlot = slot;
            BlockState blockState = this.getBlockState();

            for (int i = 0; i < ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.size(); i++) {
                boolean flag = !this.getItem(i).isEmpty();
                BooleanProperty booleanProperty = ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(i);
                blockState = blockState.setValue(booleanProperty, Boolean.valueOf(flag));
            }

            Objects.requireNonNull(this.level).setBlock(this.worldPosition, blockState, 3);
            this.level.gameEvent(GameEvent.BLOCK_CHANGE, this.worldPosition, GameEvent.Context.of(blockState));
        } else {
            LOGGER.error("Expected slot 0-5, got {}", slot);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items.clear();
        ContainerHelper.loadAllItems(tag, this.items, registries);
        this.lastInteractedSlot = tag.getInt("last_interacted_slot");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, true, registries);
        tag.putInt("last_interacted_slot", this.lastInteractedSlot);
    }

    public int count() {
        return (int)this.items.stream().filter(Predicate.not(ItemStack::isEmpty)).count();
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    @Override
    public int getContainerSize() {
        return 6;
    }

    @Override
    public boolean isEmpty() {
        return this.items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack itemStack = Objects.requireNonNullElse(this.items.get(slot), ItemStack.EMPTY);
        this.items.set(slot, ItemStack.EMPTY);
        if (!itemStack.isEmpty()) {
            this.updateState(slot);
        }

        return itemStack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return this.removeItem(slot, 1);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (stack.is(ItemTags.BOOKSHELF_BOOKS)) {
            this.items.set(slot, stack);
            this.updateState(slot);
        } else if (stack.isEmpty()) {
            this.removeItem(slot, 1);
        }
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return target.hasAnyMatching(
            itemStack -> itemStack.isEmpty()
                || ItemStack.isSameItemSameComponents(stack, itemStack) && itemStack.getCount() + stack.getCount() <= target.getMaxStackSize(itemStack)
        );
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.is(ItemTags.BOOKSHELF_BOOKS) && this.getItem(slot).isEmpty() && stack.getCount() == this.getMaxStackSize();
    }

    public int getLastInteractedSlot() {
        return this.lastInteractedSlot;
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        componentInput.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(this.items);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.items));
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("Items");
    }
}
