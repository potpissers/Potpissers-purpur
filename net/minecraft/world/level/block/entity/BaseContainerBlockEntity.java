package net.minecraft.world.level.block.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.LockCode;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.state.BlockState;

public abstract class BaseContainerBlockEntity extends BlockEntity implements Container, MenuProvider, Nameable {
    public LockCode lockKey = LockCode.NO_LOCK;
    @Nullable
    public Component name;

    protected BaseContainerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.lockKey = LockCode.fromTag(tag, registries);
        if (tag.contains("CustomName", 8)) {
            this.name = parseCustomNameSafe(tag.getString("CustomName"), registries);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.lockKey.addToTag(tag, registries);
        if (this.name != null) {
            tag.putString("CustomName", Component.Serializer.toJson(this.name, registries));
        }
    }

    @Override
    public Component getName() {
        return this.name != null ? this.name : this.getDefaultName();
    }

    @Override
    public Component getDisplayName() {
        return this.getName();
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return this.name;
    }

    protected abstract Component getDefaultName();

    public boolean canOpen(Player player) {
        return canUnlock(player, this.lockKey, this.getDisplayName(), this); // Paper - Add BlockLockCheckEvent
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper - Add BlockLockCheckEvent
    public static boolean canUnlock(Player player, LockCode code, Component displayName) {
        // Paper start - Add BlockLockCheckEvent
        return canUnlock(player, code, displayName, null);
    }
    public static boolean canUnlock(Player player, LockCode code, Component displayName, @Nullable BlockEntity blockEntity) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && blockEntity != null && blockEntity.getLevel() != null && blockEntity.getLevel().getBlockEntity(blockEntity.getBlockPos()) == blockEntity) {
            final org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(blockEntity.getLevel(), blockEntity.getBlockPos());
            net.kyori.adventure.text.Component lockedMessage = net.kyori.adventure.text.Component.translatable("container.isLocked", io.papermc.paper.adventure.PaperAdventure.asAdventure(displayName));
            net.kyori.adventure.sound.Sound lockedSound = net.kyori.adventure.sound.Sound.sound(org.bukkit.Sound.BLOCK_CHEST_LOCKED, net.kyori.adventure.sound.Sound.Source.BLOCK, 1.0F, 1.0F);
            final io.papermc.paper.event.block.BlockLockCheckEvent event = new io.papermc.paper.event.block.BlockLockCheckEvent(block, serverPlayer.getBukkitEntity(), lockedMessage, lockedSound);
            event.callEvent();
            if (event.getResult() == org.bukkit.event.Event.Result.ALLOW) {
                return true;
            } else if (event.getResult() == org.bukkit.event.Event.Result.DENY || (!player.isSpectator() && !code.unlocksWith(event.isUsingCustomKeyItemStack() ? org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getKeyItem()) : player.getMainHandItem()))) {
                if (event.getLockedMessage() != null) {
                    event.getPlayer().sendActionBar(event.getLockedMessage());
                }
                if (event.getLockedSound() != null) {
                    event.getPlayer().playSound(event.getLockedSound());
                }
                return false;
            } else {
                return true;
            }
        } else { // logic below is replaced by logic above
        // Paper end - Add BlockLockCheckEvent
        if (!player.isSpectator() && !code.unlocksWith(player.getMainHandItem())) {
            player.displayClientMessage(Component.translatable("container.isLocked", displayName), true); // Paper - diff on change
            player.playNotifySound(SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.0F);
            return false;
        } else {
            return true;
        }
        } // Paper - Add BlockLockCheckEvent
    }

    protected abstract NonNullList<ItemStack> getItems();

    protected abstract void setItems(NonNullList<ItemStack> items);

    @Override
    public boolean isEmpty() {
        for (ItemStack itemStack : this.getItems()) {
            if (!itemStack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.getItems().get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack itemStack = ContainerHelper.removeItem(this.getItems(), slot, amount);
        if (!itemStack.isEmpty()) {
            this.setChanged();
        }

        return itemStack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.getItems(), slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.getItems().set(slot, stack);
        stack.limitSize(this.getMaxStackSize(stack));
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.getItems().clear();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return this.canOpen(player) ? this.createMenu(containerId, playerInventory) : null;
    }

    protected abstract AbstractContainerMenu createMenu(int containerId, Inventory inventory);

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        this.name = componentInput.get(DataComponents.CUSTOM_NAME);
        this.lockKey = componentInput.getOrDefault(DataComponents.LOCK, LockCode.NO_LOCK);
        componentInput.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(this.getItems());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CUSTOM_NAME, this.name);
        if (!this.lockKey.equals(LockCode.NO_LOCK)) {
            components.set(DataComponents.LOCK, this.lockKey);
        }

        components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("CustomName");
        tag.remove("lock");
        tag.remove("Items");
    }

    // CraftBukkit start
    @Override
    public org.bukkit.Location getLocation() {
        if (this.level == null) return null;
        return new org.bukkit.Location(this.level.getWorld(), this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ());
    }
    // CraftBukkit end
}
