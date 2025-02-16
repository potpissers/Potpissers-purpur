package net.minecraft.world.inventory;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

class ArmorSlot extends Slot {
    private final LivingEntity owner;
    private final EquipmentSlot slot;
    @Nullable
    private final ResourceLocation emptyIcon;

    public ArmorSlot(Container container, LivingEntity owner, EquipmentSlot slot, int slotIndex, int x, int y, @Nullable ResourceLocation emptyIcon) {
        super(container, slotIndex, x, y);
        this.owner = owner;
        this.slot = slot;
        this.emptyIcon = emptyIcon;
    }

    @Override
    public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
        this.owner.onEquipItem(this.slot, oldStack, newStack);
        super.setByPlayer(newStack, oldStack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return this.slot == this.owner.getEquipmentSlotForItem(stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        ItemStack item = this.getItem();
        return (item.isEmpty() || player.isCreative() || !EnchantmentHelper.has(item, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE))
            && super.mayPickup(player);
    }

    @Nullable
    @Override
    public ResourceLocation getNoItemIcon() {
        return this.emptyIcon;
    }
}
