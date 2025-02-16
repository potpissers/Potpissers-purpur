package net.minecraft.world.level.block.entity;

import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.state.BlockState;

public class BrewingStandBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    private static final int INGREDIENT_SLOT = 3;
    private static final int FUEL_SLOT = 4;
    private static final int[] SLOTS_FOR_UP = new int[]{3};
    private static final int[] SLOTS_FOR_DOWN = new int[]{0, 1, 2, 3};
    private static final int[] SLOTS_FOR_SIDES = new int[]{0, 1, 2, 4};
    public static final int FUEL_USES = 20;
    public static final int DATA_BREW_TIME = 0;
    public static final int DATA_FUEL_USES = 1;
    public static final int NUM_DATA_VALUES = 2;
    private NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);
    public int brewTime;
    public int recipeBrewTime = 400; // Paper - Add recipeBrewTime
    private boolean[] lastPotionCount;
    private Item ingredient;
    public int fuel;
    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> BrewingStandBlockEntity.this.brewTime;
                case 1 -> BrewingStandBlockEntity.this.fuel;
                case 2 -> BrewingStandBlockEntity.this.recipeBrewTime; // Paper - Add recipeBrewTime
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0:
                    BrewingStandBlockEntity.this.brewTime = value;
                    break;
                case 1:
                    BrewingStandBlockEntity.this.fuel = value;
                // Paper start - Add recipeBrewTime
                    break;
                case 2:
                    BrewingStandBlockEntity.this.recipeBrewTime = value;
                    break;
                // Paper end - Add recipeBrewTime
            }
        }

        @Override
        public int getCount() {
            return 3; // Paper - Add recipeBrewTime
        }
    };
    // CraftBukkit start - add fields and methods
    public java.util.List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = MAX_STACK;

    public void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.add(player);
    }

    public void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.remove(player);
    }

    public java.util.List<org.bukkit.entity.HumanEntity> getViewers() {
        return this.transaction;
    }

    public java.util.List<net.minecraft.world.item.ItemStack> getContents() {
        return this.items;
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack;
    }

    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }
    // CraftBukkit end

    public BrewingStandBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.BREWING_STAND, pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.brewing");
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BrewingStandBlockEntity blockEntity) {
        ItemStack itemStack = blockEntity.items.get(4);
        if (blockEntity.fuel <= 0 && itemStack.is(ItemTags.BREWING_FUEL)) {
            // CraftBukkit start
            org.bukkit.event.inventory.BrewingStandFuelEvent event = new org.bukkit.event.inventory.BrewingStandFuelEvent(
                org.bukkit.craftbukkit.block.CraftBlock.at(level, pos),
                org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack),
                20
            );
            if (!event.callEvent()) {
                return;
            }

            blockEntity.fuel = event.getFuelPower();
            if (blockEntity.fuel > 0 && event.isConsuming()) {
                itemStack.shrink(1);
            }
            // CraftBukkit end
            setChanged(level, pos, state);
        }

        boolean isBrewable = isBrewable(level.potionBrewing(), blockEntity.items);
        boolean flag = blockEntity.brewTime > 0;
        ItemStack itemStack1 = blockEntity.items.get(3);
        if (flag) {
            blockEntity.brewTime--;
            boolean flag1 = blockEntity.brewTime == 0;
            if (flag1 && isBrewable) {
                doBrew(level, pos, blockEntity.items, blockEntity); // CraftBukkit
            } else if (!isBrewable || !itemStack1.is(blockEntity.ingredient)) {
                blockEntity.brewTime = 0;
            }

            setChanged(level, pos, state);
        } else if (isBrewable && blockEntity.fuel > 0) {
            blockEntity.fuel--;
            // CraftBukkit start
            org.bukkit.event.block.BrewingStartEvent event = new org.bukkit.event.block.BrewingStartEvent(
                org.bukkit.craftbukkit.block.CraftBlock.at(level, pos),
                org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack1), 400);
            event.callEvent();
            blockEntity.recipeBrewTime = event.getRecipeBrewTime(); // Paper - use recipe brew time from event
            blockEntity.brewTime = event.getBrewingTime(); // 400 -> event.getTotalBrewTime() // Paper - use brewing time from event
            // CraftBukkit end
            blockEntity.ingredient = itemStack1.getItem();
            setChanged(level, pos, state);
        }

        boolean[] potionBits = blockEntity.getPotionBits();
        if (!Arrays.equals(potionBits, blockEntity.lastPotionCount)) {
            blockEntity.lastPotionCount = potionBits;
            BlockState blockState = state;
            if (!(state.getBlock() instanceof BrewingStandBlock)) {
                return;
            }

            for (int i = 0; i < BrewingStandBlock.HAS_BOTTLE.length; i++) {
                blockState = blockState.setValue(BrewingStandBlock.HAS_BOTTLE[i], Boolean.valueOf(potionBits[i]));
            }

            level.setBlock(pos, blockState, 2);
        }
    }

    private boolean[] getPotionBits() {
        boolean[] flags = new boolean[3];

        for (int i = 0; i < 3; i++) {
            if (!this.items.get(i).isEmpty()) {
                flags[i] = true;
            }
        }

        return flags;
    }

    private static boolean isBrewable(PotionBrewing potionBrewing, NonNullList<ItemStack> items) {
        ItemStack itemStack = items.get(3);
        if (itemStack.isEmpty()) {
            return false;
        } else if (!potionBrewing.isIngredient(itemStack)) {
            return false;
        } else {
            for (int i = 0; i < 3; i++) {
                ItemStack itemStack1 = items.get(i);
                if (!itemStack1.isEmpty() && potionBrewing.hasMix(itemStack1, itemStack)) {
                    return true;
                }
            }

            return false;
        }
    }

    private static void doBrew(Level level, BlockPos pos, NonNullList<ItemStack> items, BrewingStandBlockEntity brewingStandBlockEntity) { // CraftBukkit
        ItemStack itemStack = items.get(3);
        PotionBrewing potionBrewing = level.potionBrewing();

        // CraftBukkit start
        org.bukkit.inventory.InventoryHolder owner = brewingStandBlockEntity.getOwner();
        java.util.List<org.bukkit.inventory.ItemStack> brewResults = new java.util.ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            brewResults.add(i, org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(potionBrewing.mix(itemStack, items.get(i))));
        }

        if (owner != null) {
            org.bukkit.event.inventory.BrewEvent event = new org.bukkit.event.inventory.BrewEvent(
                org.bukkit.craftbukkit.block.CraftBlock.at(level, pos),
                (org.bukkit.inventory.BrewerInventory) owner.getInventory(),
                brewResults,
                brewingStandBlockEntity.fuel
            );
            if (!event.callEvent()) {
                return;
            }

            for (int i = 0; i < 3; i++) {
                if (i < brewResults.size()) {
                    items.set(i, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(brewResults.get(i)));
                } else {
                    items.set(i, ItemStack.EMPTY);
                }
            }
        }
        // CraftBukkit end

        itemStack.shrink(1);
        ItemStack craftingRemainder = itemStack.getItem().getCraftingRemainder();
        if (!craftingRemainder.isEmpty()) {
            if (itemStack.isEmpty()) {
                itemStack = craftingRemainder;
            } else {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), craftingRemainder);
            }
        }

        items.set(3, itemStack);
        level.levelEvent(1035, pos, 0);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items, registries);
        this.brewTime = tag.getShort("BrewTime");
        if (this.brewTime > 0) {
            this.ingredient = this.items.get(3).getItem();
        }

        this.fuel = tag.getByte("Fuel");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putShort("BrewTime", (short)this.brewTime);
        ContainerHelper.saveAllItems(tag, this.items, registries);
        tag.putByte("Fuel", (byte)this.fuel);
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        PotionBrewing potionBrewing = this.level != null ? this.level.potionBrewing() : PotionBrewing.EMPTY; // Paper - move up
        if (index == 3) {
            return potionBrewing.isIngredient(stack);
        } else {
            return index == 4
                ? stack.is(ItemTags.BREWING_FUEL)
                : (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION) || stack.is(Items.GLASS_BOTTLE) || potionBrewing.isCustomInput(stack)) // Paper - Custom Potion Mixes
                    && this.getItem(index).isEmpty();
        }
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.UP) {
            return SLOTS_FOR_UP;
        } else {
            return side == Direction.DOWN ? SLOTS_FOR_DOWN : SLOTS_FOR_SIDES;
        }
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack itemStack, @Nullable Direction direction) {
        return this.canPlaceItem(index, itemStack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return index != 3 || stack.is(Items.GLASS_BOTTLE);
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory player) {
        return new BrewingStandMenu(id, player, this, this.dataAccess);
    }
}
