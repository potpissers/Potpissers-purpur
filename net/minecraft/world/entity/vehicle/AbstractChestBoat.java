package net.minecraft.world.entity.vehicle;

import java.util.function.Supplier;
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
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootTable;

public abstract class AbstractChestBoat extends AbstractBoat implements HasCustomInventoryScreen, ContainerEntity {
    private static final int CONTAINER_SIZE = 27;
    private NonNullList<ItemStack> itemStacks = NonNullList.withSize(27, ItemStack.EMPTY);
    @Nullable
    private ResourceKey<LootTable> lootTable;
    private long lootTableSeed;

    public AbstractChestBoat(EntityType<? extends AbstractChestBoat> entityType, Level level, Supplier<Item> dropItem) {
        super(entityType, level, dropItem);
    }

    @Override
    protected float getSinglePassengerXOffset() {
        return 0.15F;
    }

    @Override
    protected int getMaxPassengers() {
        return 1;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.addChestVehicleSaveData(tag, this.registryAccess());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.readChestVehicleSaveData(tag, this.registryAccess());
    }

    @Override
    public void destroy(ServerLevel level, DamageSource damageSource) {
        this.destroy(level, this.getDropItem());
        this.chestVehicleDestroyed(damageSource, level, this);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide && reason.shouldDestroy()) {
            Containers.dropContents(this.level(), this, this);
        }

        super.remove(reason);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!player.isSecondaryUseActive()) {
            InteractionResult interactionResult = super.interact(player, hand);
            if (interactionResult != InteractionResult.PASS) {
                return interactionResult;
            }
        }

        if (this.canAddPassenger(player) && !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        } else {
            InteractionResult interactionResult = this.interactWithContainerVehicle(player);
            if (interactionResult.consumesAction() && player.level() instanceof ServerLevel serverLevel) {
                this.gameEvent(GameEvent.CONTAINER_OPEN, player);
                PiglinAi.angerNearbyPiglins(serverLevel, player, true);
            }

            return interactionResult;
        }
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        player.openMenu(this);
        if (player.level() instanceof ServerLevel serverLevel) {
            this.gameEvent(GameEvent.CONTAINER_OPEN, player);
            PiglinAi.angerNearbyPiglins(serverLevel, player, true);
        }
    }

    @Override
    public void clearContent() {
        this.clearChestVehicleContent();
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.getChestVehicleItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return this.removeChestVehicleItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return this.removeChestVehicleItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.setChestVehicleItem(slot, stack);
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

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (this.lootTable != null && player.isSpectator()) {
            return null;
        } else {
            this.unpackLootTable(playerInventory.player);
            return ChestMenu.threeRows(containerId, playerInventory, this);
        }
    }

    public void unpackLootTable(@Nullable Player player) {
        this.unpackChestVehicleLootTable(player);
    }

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

    @Override
    public void stopOpen(Player player) {
        this.level().gameEvent(GameEvent.CONTAINER_CLOSE, this.position(), GameEvent.Context.of(player));
    }
}
