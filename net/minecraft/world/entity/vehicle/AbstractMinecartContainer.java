package net.minecraft.world.entity.vehicle;

import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

public abstract class AbstractMinecartContainer extends AbstractMinecart implements ContainerEntity {
    private NonNullList<ItemStack> itemStacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY); // CraftBukkit - SPIGOT-3513
    @Nullable
    public ResourceKey<LootTable> lootTable;
    public long lootTableSeed;
    private final com.destroystokyo.paper.loottable.PaperLootableInventoryData lootableData = new com.destroystokyo.paper.loottable.PaperLootableInventoryData(); // Paper - LootTable API

    protected AbstractMinecartContainer(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void destroy(ServerLevel level, DamageSource damageSource) {
        super.destroy(level, damageSource);
        this.chestVehicleDestroyed(damageSource, level, this);
    }

    @Override
    public ItemStack getItem(int index) {
        return this.getChestVehicleItem(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        return this.removeChestVehicleItem(index, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return this.removeChestVehicleItemNoUpdate(index);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        this.setChestVehicleItem(index, stack);
    }

    @Override
    public SlotAccess getSlot(int slot) {
        return this.getChestVehicleSlot(slot);
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return this.isChestVehicleStillValid(player);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        // CraftBukkit start - add Bukkit remove cause
        this.remove(reason, null);
    }

    @Override
    public void remove(Entity.RemovalReason reason, org.bukkit.event.entity.EntityRemoveEvent.Cause cause) {
        // CraftBukkit end  - add Bukkit remove cause
        if (!this.level().isClientSide && reason.shouldDestroy()) {
            Containers.dropContents(this.level(), this, this);
        }

        super.remove(reason, cause); // CraftBukkit - add Bukkit remove cause
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        this.addChestVehicleSaveData(compound, this.registryAccess());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.readChestVehicleSaveData(compound, this.registryAccess());
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return this.interactWithContainerVehicle(player);
    }

    @Override
    protected Vec3 applyNaturalSlowdown(Vec3 speed) {
        float f = 0.98F;
        if (this.lootTable == null) {
            int i = 15 - AbstractContainerMenu.getRedstoneSignalFromContainer(this);
            f += i * 0.001F;
        }

        if (this.isInWater()) {
            f *= 0.95F;
        }

        return speed.multiply(f, 0.0, f);
    }

    @Override
    public void clearContent() {
        this.clearChestVehicleContent();
    }

    public void setLootTable(ResourceKey<LootTable> lootTable, long seed) {
        this.lootTable = lootTable;
        this.lootTableSeed = seed;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (this.lootTable != null && player.isSpectator()) {
            return null;
        } else {
            this.unpackChestVehicleLootTable(playerInventory.player);
            return this.createMenu(containerId, playerInventory);
        }
    }

    protected abstract AbstractContainerMenu createMenu(int containerId, Inventory playerInventory);

    @Nullable
    @Override
    public ResourceKey<LootTable> getContainerLootTable() {
        return this.lootTable;
    }

    @Override
    public void setContainerLootTable(@Nullable ResourceKey<LootTable> lootTable) {
        this.lootTable = lootTable;
    }

    @Override
    public long getContainerLootTableSeed() {
        return this.lootTableSeed;
    }

    @Override
    public void setContainerLootTableSeed(long lootTableSeed) {
        this.lootTableSeed = lootTableSeed;
    }

    @Override
    public NonNullList<ItemStack> getItemStacks() {
        return this.itemStacks;
    }

    @Override
    public void clearItemStacks() {
        this.itemStacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
    }

    // Paper start - LootTable API
    @Override
    public com.destroystokyo.paper.loottable.PaperLootableInventoryData lootableData() {
        return this.lootableData;
    }
    // Paper end - LootTable API

    // CraftBukkit start
    public java.util.List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = MAX_STACK;

    public java.util.List<net.minecraft.world.item.ItemStack> getContents() {
        return this.itemStacks;
    }

    public void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.add(player);
    }

    public void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.remove(player);
    }

    public java.util.List<org.bukkit.entity.HumanEntity> getViewers() {
        return this.transaction;
    }

    public org.bukkit.inventory.InventoryHolder getOwner() {
        return this.getBukkitEntity() instanceof final org.bukkit.inventory.InventoryHolder inventoryHolder ? inventoryHolder : null;
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack;
    }

    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }

    @Override
    public org.bukkit.Location getLocation() {
        return this.getBukkitEntity().getLocation();
    }
    // CraftBukkit end
}
