package net.minecraft.world.inventory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class HorseInventoryMenu extends AbstractContainerMenu {
    static final ResourceLocation SADDLE_SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/slot/saddle");
    private static final ResourceLocation LLAMA_ARMOR_SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/slot/llama_armor");
    private static final ResourceLocation ARMOR_SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/slot/horse_armor");
    private final Container horseContainer;
    private final Container armorContainer;
    private final AbstractHorse horse;
    public static final int SLOT_BODY_ARMOR = 1;
    private static final int SLOT_HORSE_INVENTORY_START = 2;

    public HorseInventoryMenu(int containerId, Inventory inventory, Container horseContainer, final AbstractHorse horse, int columns) {
        super(null, containerId);
        this.horseContainer = horseContainer;
        this.armorContainer = horse.getBodyArmorAccess();
        this.horse = horse;
        horseContainer.startOpen(inventory.player);
        this.addSlot(new Slot(horseContainer, 0, 8, 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.SADDLE) && !this.hasItem() && horse.isSaddleable();
            }

            @Override
            public boolean isActive() {
                return horse.isSaddleable();
            }

            @Override
            public ResourceLocation getNoItemIcon() {
                return HorseInventoryMenu.SADDLE_SLOT_SPRITE;
            }
        });
        ResourceLocation resourceLocation = horse instanceof Llama ? LLAMA_ARMOR_SLOT_SPRITE : ARMOR_SLOT_SPRITE;
        this.addSlot(new ArmorSlot(this.armorContainer, horse, EquipmentSlot.BODY, 0, 8, 36, resourceLocation) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return horse.isEquippableInSlot(stack, EquipmentSlot.BODY);
            }

            @Override
            public boolean isActive() {
                return horse.canUseSlot(EquipmentSlot.BODY);
            }
        });
        if (columns > 0) {
            for (int i = 0; i < 3; i++) {
                for (int i1 = 0; i1 < columns; i1++) {
                    this.addSlot(new Slot(horseContainer, 1 + i1 + i * columns, 80 + i1 * 18, 18 + i * 18));
                }
            }
        }

        this.addStandardInventorySlots(inventory, 8, 84);
    }

    @Override
    public boolean stillValid(Player player) {
        return !this.horse.hasInventoryChanged(this.horseContainer)
            && this.horseContainer.stillValid(player)
            && this.armorContainer.stillValid(player)
            && this.horse.isAlive()
            && player.canInteractWithEntity(this.horse, 4.0);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            itemStack = item.copy();
            int i = this.horseContainer.getContainerSize() + 1;
            if (index < i) {
                if (!this.moveItemStackTo(item, i, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getSlot(1).mayPlace(item) && !this.getSlot(1).hasItem()) {
                if (!this.moveItemStackTo(item, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getSlot(0).mayPlace(item)) {
                if (!this.moveItemStackTo(item, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (i <= 1 || !this.moveItemStackTo(item, 2, i, false)) {
                int i2 = i + 27;
                int i4 = i2 + 9;
                if (index >= i2 && index < i4) {
                    if (!this.moveItemStackTo(item, i, i2, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index >= i && index < i2) {
                    if (!this.moveItemStackTo(item, i2, i4, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(item, i2, i2, false)) {
                    return ItemStack.EMPTY;
                }

                return ItemStack.EMPTY;
            }

            if (item.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemStack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.horseContainer.stopOpen(player);
    }
}
