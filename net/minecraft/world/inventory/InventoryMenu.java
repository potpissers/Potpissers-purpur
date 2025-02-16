package net.minecraft.world.inventory;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class InventoryMenu extends AbstractCraftingMenu {
    public static final int CONTAINER_ID = 0;
    public static final int RESULT_SLOT = 0;
    private static final int CRAFTING_GRID_WIDTH = 2;
    private static final int CRAFTING_GRID_HEIGHT = 2;
    public static final int CRAFT_SLOT_START = 1;
    public static final int CRAFT_SLOT_COUNT = 4;
    public static final int CRAFT_SLOT_END = 5;
    public static final int ARMOR_SLOT_START = 5;
    public static final int ARMOR_SLOT_COUNT = 4;
    public static final int ARMOR_SLOT_END = 9;
    public static final int INV_SLOT_START = 9;
    public static final int INV_SLOT_END = 36;
    public static final int USE_ROW_SLOT_START = 36;
    public static final int USE_ROW_SLOT_END = 45;
    public static final int SHIELD_SLOT = 45;
    public static final ResourceLocation EMPTY_ARMOR_SLOT_HELMET = ResourceLocation.withDefaultNamespace("container/slot/helmet");
    public static final ResourceLocation EMPTY_ARMOR_SLOT_CHESTPLATE = ResourceLocation.withDefaultNamespace("container/slot/chestplate");
    public static final ResourceLocation EMPTY_ARMOR_SLOT_LEGGINGS = ResourceLocation.withDefaultNamespace("container/slot/leggings");
    public static final ResourceLocation EMPTY_ARMOR_SLOT_BOOTS = ResourceLocation.withDefaultNamespace("container/slot/boots");
    public static final ResourceLocation EMPTY_ARMOR_SLOT_SHIELD = ResourceLocation.withDefaultNamespace("container/slot/shield");
    private static final Map<EquipmentSlot, ResourceLocation> TEXTURE_EMPTY_SLOTS = Map.of(
        EquipmentSlot.FEET,
        EMPTY_ARMOR_SLOT_BOOTS,
        EquipmentSlot.LEGS,
        EMPTY_ARMOR_SLOT_LEGGINGS,
        EquipmentSlot.CHEST,
        EMPTY_ARMOR_SLOT_CHESTPLATE,
        EquipmentSlot.HEAD,
        EMPTY_ARMOR_SLOT_HELMET
    );
    private static final EquipmentSlot[] SLOT_IDS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    public final boolean active;
    private final Player owner;

    public InventoryMenu(Inventory playerInventory, boolean active, final Player owner) {
        super(null, 0, 2, 2);
        this.active = active;
        this.owner = owner;
        this.addResultSlot(owner, 154, 28);
        this.addCraftingGridSlots(98, 18);

        for (int i = 0; i < 4; i++) {
            EquipmentSlot equipmentSlot = SLOT_IDS[i];
            ResourceLocation resourceLocation = TEXTURE_EMPTY_SLOTS.get(equipmentSlot);
            this.addSlot(new ArmorSlot(playerInventory, owner, equipmentSlot, 39 - i, 8, 8 + i * 18, resourceLocation));
        }

        this.addStandardInventorySlots(playerInventory, 8, 84);
        this.addSlot(new Slot(playerInventory, 40, 77, 62) {
            @Override
            public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
                owner.onEquipItem(EquipmentSlot.OFFHAND, oldStack, newStack);
                super.setByPlayer(newStack, oldStack);
            }

            @Override
            public ResourceLocation getNoItemIcon() {
                return InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
            }
        });
    }

    public static boolean isHotbarSlot(int index) {
        return index >= 36 && index < 45 || index == 45;
    }

    @Override
    public void slotsChanged(Container inventory) {
        if (this.owner.level() instanceof ServerLevel serverLevel) {
            CraftingMenu.slotChangedCraftingGrid(this, serverLevel, this.owner, this.craftSlots, this.resultSlots, null);
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.resultSlots.clearContent();
        if (!player.level().isClientSide) {
            this.clearContainer(player, this.craftSlots);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack item = slot.getItem();
            itemStack = item.copy();
            EquipmentSlot equipmentSlotForItem = player.getEquipmentSlotForItem(itemStack);
            if (index == 0) {
                if (!this.moveItemStackTo(item, 9, 45, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(item, itemStack);
            } else if (index >= 1 && index < 5) {
                if (!this.moveItemStackTo(item, 9, 45, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= 5 && index < 9) {
                if (!this.moveItemStackTo(item, 9, 45, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (equipmentSlotForItem.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && !this.slots.get(8 - equipmentSlotForItem.getIndex()).hasItem()) {
                int i = 8 - equipmentSlotForItem.getIndex();
                if (!this.moveItemStackTo(item, i, i + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (equipmentSlotForItem == EquipmentSlot.OFFHAND && !this.slots.get(45).hasItem()) {
                if (!this.moveItemStackTo(item, 45, 46, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= 9 && index < 36) {
                if (!this.moveItemStackTo(item, 36, 45, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= 36 && index < 45) {
                if (!this.moveItemStackTo(item, 9, 36, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(item, 9, 45, false)) {
                return ItemStack.EMPTY;
            }

            if (item.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY, itemStack);
            } else {
                slot.setChanged();
            }

            if (item.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, item);
            if (index == 0) {
                player.drop(item, false);
            }
        }

        return itemStack;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public Slot getResultSlot() {
        return this.slots.get(0);
    }

    @Override
    public List<Slot> getInputGridSlots() {
        return this.slots.subList(1, 5);
    }

    public CraftingContainer getCraftSlots() {
        return this.craftSlots;
    }

    @Override
    public RecipeBookType getRecipeBookType() {
        return RecipeBookType.CRAFTING;
    }

    @Override
    protected Player owner() {
        return this.owner;
    }
}
