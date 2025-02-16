package net.minecraft.world.entity.vehicle;

import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public interface ContainerEntity extends Container, MenuProvider {
    Vec3 position();

    AABB getBoundingBox();

    @Nullable
    ResourceKey<LootTable> getContainerLootTable();

    void setContainerLootTable(@Nullable ResourceKey<LootTable> lootTable);

    long getContainerLootTableSeed();

    void setContainerLootTableSeed(long lootTableSeed);

    NonNullList<ItemStack> getItemStacks();

    void clearItemStacks();

    Level level();

    boolean isRemoved();

    @Override
    default boolean isEmpty() {
        return this.isChestVehicleEmpty();
    }

    default void addChestVehicleSaveData(CompoundTag tag, HolderLookup.Provider levelRegistry) {
        if (this.getContainerLootTable() != null) {
            tag.putString("LootTable", this.getContainerLootTable().location().toString());
            if (this.getContainerLootTableSeed() != 0L) {
                tag.putLong("LootTableSeed", this.getContainerLootTableSeed());
            }
        } else {
            ContainerHelper.saveAllItems(tag, this.getItemStacks(), levelRegistry);
        }
    }

    default void readChestVehicleSaveData(CompoundTag tag, HolderLookup.Provider levelRegistry) {
        this.clearItemStacks();
        if (tag.contains("LootTable", 8)) {
            this.setContainerLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(tag.getString("LootTable"))));
            this.setContainerLootTableSeed(tag.getLong("LootTableSeed"));
        } else {
            ContainerHelper.loadAllItems(tag, this.getItemStacks(), levelRegistry);
        }
    }

    default void chestVehicleDestroyed(DamageSource damageSource, ServerLevel level, Entity entity) {
        if (level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            Containers.dropContents(level, entity, this);
            Entity directEntity = damageSource.getDirectEntity();
            if (directEntity != null && directEntity.getType() == EntityType.PLAYER) {
                PiglinAi.angerNearbyPiglins(level, (Player)directEntity, true);
            }
        }
    }

    default InteractionResult interactWithContainerVehicle(Player player) {
        player.openMenu(this);
        return InteractionResult.SUCCESS;
    }

    default void unpackChestVehicleLootTable(@Nullable Player player) {
        MinecraftServer server = this.level().getServer();
        if (this.getContainerLootTable() != null && server != null) {
            LootTable lootTable = server.reloadableRegistries().getLootTable(this.getContainerLootTable());
            if (player != null) {
                CriteriaTriggers.GENERATE_LOOT.trigger((ServerPlayer)player, this.getContainerLootTable());
            }

            this.setContainerLootTable(null);
            LootParams.Builder builder = new LootParams.Builder((ServerLevel)this.level()).withParameter(LootContextParams.ORIGIN, this.position());
            if (player != null) {
                builder.withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player);
            }

            lootTable.fill(this, builder.create(LootContextParamSets.CHEST), this.getContainerLootTableSeed());
        }
    }

    default void clearChestVehicleContent() {
        this.unpackChestVehicleLootTable(null);
        this.getItemStacks().clear();
    }

    default boolean isChestVehicleEmpty() {
        for (ItemStack itemStack : this.getItemStacks()) {
            if (!itemStack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    default ItemStack removeChestVehicleItemNoUpdate(int slot) {
        this.unpackChestVehicleLootTable(null);
        ItemStack itemStack = this.getItemStacks().get(slot);
        if (itemStack.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            this.getItemStacks().set(slot, ItemStack.EMPTY);
            return itemStack;
        }
    }

    default ItemStack getChestVehicleItem(int slot) {
        this.unpackChestVehicleLootTable(null);
        return this.getItemStacks().get(slot);
    }

    default ItemStack removeChestVehicleItem(int slot, int amount) {
        this.unpackChestVehicleLootTable(null);
        return ContainerHelper.removeItem(this.getItemStacks(), slot, amount);
    }

    default void setChestVehicleItem(int slot, ItemStack stack) {
        this.unpackChestVehicleLootTable(null);
        this.getItemStacks().set(slot, stack);
        stack.limitSize(this.getMaxStackSize(stack));
    }

    default SlotAccess getChestVehicleSlot(final int index) {
        return index >= 0 && index < this.getContainerSize() ? new SlotAccess() {
            @Override
            public ItemStack get() {
                return ContainerEntity.this.getChestVehicleItem(index);
            }

            @Override
            public boolean set(ItemStack carried) {
                ContainerEntity.this.setChestVehicleItem(index, carried);
                return true;
            }
        } : SlotAccess.NULL;
    }

    default boolean isChestVehicleStillValid(Player player) {
        return !this.isRemoved() && player.canInteractWithEntity(this.getBoundingBox(), 4.0);
    }
}
