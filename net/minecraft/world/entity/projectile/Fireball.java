package net.minecraft.world.entity.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public abstract class Fireball extends AbstractHurtingProjectile implements ItemSupplier {
    private static final float MIN_CAMERA_DISTANCE_SQUARED = 12.25F;
    private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK = SynchedEntityData.defineId(Fireball.class, EntityDataSerializers.ITEM_STACK);

    public Fireball(EntityType<? extends Fireball> entityType, Level level) {
        super(entityType, level);
    }

    public Fireball(EntityType<? extends Fireball> entityType, double x, double y, double z, Vec3 movement, Level level) {
        super(entityType, x, y, z, movement, level);
    }

    public Fireball(EntityType<? extends Fireball> entityType, LivingEntity owner, Vec3 movement, Level level) {
        super(entityType, owner, movement, level);
    }

    public void setItem(ItemStack stack) {
        if (stack.isEmpty()) {
            this.getEntityData().set(DATA_ITEM_STACK, this.getDefaultItem());
        } else {
            this.getEntityData().set(DATA_ITEM_STACK, stack.copyWithCount(1));
        }
    }

    @Override
    protected void playEntityOnFireExtinguishedSound() {
    }

    @Override
    public ItemStack getItem() {
        return this.getEntityData().get(DATA_ITEM_STACK);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM_STACK, this.getDefaultItem());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.put("Item", this.getItem().save(this.registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("Item", 10)) {
            // CraftBukkit start - SPIGOT-5474 probably came from bugged earlier versions
            final ItemStack itemStack = ItemStack.parse(this.registryAccess(), compound.getCompound("Item")).orElse(this.getDefaultItem());
            if (!itemStack.isEmpty()) {
                this.setItem(itemStack);
            }
            // CraftBukkit end
        } else {
            this.setItem(this.getDefaultItem());
        }
    }

    private ItemStack getDefaultItem() {
        return new ItemStack(Items.FIRE_CHARGE);
    }

    @Override
    public SlotAccess getSlot(int slot) {
        return slot == 0 ? SlotAccess.of(this::getItem, this::setItem) : super.getSlot(slot);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return (this.tickCount >= 2 || !(distance < 12.25)) && super.shouldRenderAtSqrDistance(distance);
    }
}
