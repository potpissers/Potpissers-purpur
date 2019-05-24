package net.minecraft.world.inventory;

import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;

public class PlayerEnderChestContainer extends SimpleContainer {
    @Nullable
    private EnderChestBlockEntity activeChest;
    // CraftBukkit start
    private final Player owner;

    public org.bukkit.inventory.InventoryHolder getBukkitOwner() {
        return this.owner.getBukkitEntity();
    }

    @Override
    public org.bukkit.Location getLocation() {
        return this.activeChest != null ? org.bukkit.craftbukkit.util.CraftLocation.toBukkit(this.activeChest.getBlockPos(), this.activeChest.getLevel().getWorld()) : null;
    }

    public PlayerEnderChestContainer(Player owner) {
        super(org.purpurmc.purpur.PurpurConfig.enderChestSixRows ? 54 : 27); // Purpur - Barrels and enderchests 6 rows
        this.owner = owner;
        // CraftBukkit end
    }

    // Purpur start - Barrels and enderchests 6 rows
    @Override
    public int getContainerSize() {
        return owner.sixRowEnderchestSlotCount < 0 ? super.getContainerSize() : owner.sixRowEnderchestSlotCount;
    }
    // Purpur end - Barrels and enderchests 6 rows

    public void setActiveChest(EnderChestBlockEntity enderChestBlockEntity) {
        this.activeChest = enderChestBlockEntity;
    }

    public boolean isActiveChest(EnderChestBlockEntity enderChest) {
        return this.activeChest == enderChest;
    }

    @Override
    public void fromTag(ListTag tag, HolderLookup.Provider levelRegistry) {
        for (int i = 0; i < this.getContainerSize(); i++) {
            this.setItem(i, ItemStack.EMPTY);
        }

        for (int i = 0; i < tag.size(); i++) {
            CompoundTag compound = tag.getCompound(i);
            int i1 = compound.getByte("Slot") & 255;
            if (i1 >= 0 && i1 < this.getContainerSize()) {
                this.setItem(i1, ItemStack.parse(levelRegistry, compound).orElse(ItemStack.EMPTY));
            }
        }
    }

    @Override
    public ListTag createTag(HolderLookup.Provider levelRegistry) {
        ListTag listTag = new ListTag();

        for (int i = 0; i < this.getContainerSize(); i++) {
            ItemStack item = this.getItem(i);
            if (!item.isEmpty()) {
                CompoundTag compoundTag = new CompoundTag();
                compoundTag.putByte("Slot", (byte)i);
                listTag.add(item.save(levelRegistry, compoundTag));
            }
        }

        return listTag;
    }

    @Override
    public boolean stillValid(Player player) {
        return (this.activeChest == null || this.activeChest.stillValid(player)) && super.stillValid(player);
    }

    @Override
    public void startOpen(Player player) {
        if (this.activeChest != null) {
            this.activeChest.startOpen(player);
        }

        super.startOpen(player);
    }

    @Override
    public void stopOpen(Player player) {
        if (this.activeChest != null) {
            this.activeChest.stopOpen(player);
        }

        super.stopOpen(player);
        this.activeChest = null;
    }
}
