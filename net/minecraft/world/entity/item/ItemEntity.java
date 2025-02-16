package net.minecraft.world.entity.item;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

public class ItemEntity extends Entity implements TraceableEntity {
    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(ItemEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final float FLOAT_HEIGHT = 0.1F;
    public static final float EYE_HEIGHT = 0.2125F;
    private static final int LIFETIME = 6000;
    private static final int INFINITE_PICKUP_DELAY = 32767;
    private static final int INFINITE_LIFETIME = -32768;
    public int age;
    public int pickupDelay;
    public int health = 5;
    @Nullable
    public UUID thrower;
    @Nullable
    private Entity cachedThrower;
    @Nullable
    public UUID target;
    public final float bobOffs;

    public ItemEntity(EntityType<? extends ItemEntity> entityType, Level level) {
        super(entityType, level);
        this.bobOffs = this.random.nextFloat() * (float) Math.PI * 2.0F;
        this.setYRot(this.random.nextFloat() * 360.0F);
    }

    public ItemEntity(Level level, double posX, double posY, double posZ, ItemStack itemStack) {
        this(level, posX, posY, posZ, itemStack, level.random.nextDouble() * 0.2 - 0.1, 0.2, level.random.nextDouble() * 0.2 - 0.1);
    }

    public ItemEntity(Level level, double posX, double posY, double posZ, ItemStack itemStack, double deltaX, double deltaY, double deltaZ) {
        this(EntityType.ITEM, level);
        this.setPos(posX, posY, posZ);
        this.setDeltaMovement(deltaX, deltaY, deltaZ);
        this.setItem(itemStack);
    }

    private ItemEntity(ItemEntity other) {
        super(other.getType(), other.level());
        this.setItem(other.getItem().copy());
        this.copyPosition(other);
        this.age = other.age;
        this.bobOffs = other.bobOffs;
    }

    @Override
    public boolean dampensVibrations() {
        return this.getItem().is(ItemTags.DAMPENS_VIBRATIONS);
    }

    @Nullable
    @Override
    public Entity getOwner() {
        if (this.cachedThrower != null && !this.cachedThrower.isRemoved()) {
            return this.cachedThrower;
        } else if (this.thrower != null && this.level() instanceof ServerLevel serverLevel) {
            this.cachedThrower = serverLevel.getEntity(this.thrower);
            return this.cachedThrower;
        } else {
            return null;
        }
    }

    @Override
    public void restoreFrom(Entity entity) {
        super.restoreFrom(entity);
        if (entity instanceof ItemEntity itemEntity) {
            this.cachedThrower = itemEntity.cachedThrower;
        }
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM, ItemStack.EMPTY);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04;
    }

    @Override
    public void tick() {
        if (this.getItem().isEmpty()) {
            this.discard();
        } else {
            super.tick();
            if (this.pickupDelay > 0 && this.pickupDelay != 32767) {
                this.pickupDelay--;
            }

            this.xo = this.getX();
            this.yo = this.getY();
            this.zo = this.getZ();
            Vec3 deltaMovement = this.getDeltaMovement();
            if (this.isInWater() && this.getFluidHeight(FluidTags.WATER) > 0.1F) {
                this.setUnderwaterMovement();
            } else if (this.isInLava() && this.getFluidHeight(FluidTags.LAVA) > 0.1F) {
                this.setUnderLavaMovement();
            } else {
                this.applyGravity();
            }

            if (this.level().isClientSide) {
                this.noPhysics = false;
            } else {
                this.noPhysics = !this.level().noCollision(this, this.getBoundingBox().deflate(1.0E-7));
                if (this.noPhysics) {
                    this.moveTowardsClosestSpace(this.getX(), (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0, this.getZ());
                }
            }

            if (!this.onGround() || this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-5F || (this.tickCount + this.getId()) % 4 == 0) {
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.applyEffectsFromBlocks();
                float f = 0.98F;
                if (this.onGround()) {
                    f = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.98F;
                }

                this.setDeltaMovement(this.getDeltaMovement().multiply(f, 0.98, f));
                if (this.onGround()) {
                    Vec3 deltaMovement1 = this.getDeltaMovement();
                    if (deltaMovement1.y < 0.0) {
                        this.setDeltaMovement(deltaMovement1.multiply(1.0, -0.5, 1.0));
                    }
                }
            }

            boolean flag = Mth.floor(this.xo) != Mth.floor(this.getX())
                || Mth.floor(this.yo) != Mth.floor(this.getY())
                || Mth.floor(this.zo) != Mth.floor(this.getZ());
            int i = flag ? 2 : 40;
            if (this.tickCount % i == 0 && !this.level().isClientSide && this.isMergable()) {
                this.mergeWithNeighbours();
            }

            if (this.age != -32768) {
                this.age++;
            }

            this.hasImpulse = this.hasImpulse | this.updateInWaterStateAndDoFluidPushing();
            if (!this.level().isClientSide) {
                double d = this.getDeltaMovement().subtract(deltaMovement).lengthSqr();
                if (d > 0.01) {
                    this.hasImpulse = true;
                }
            }

            if (!this.level().isClientSide && this.age >= 6000) {
                this.discard();
            }
        }
    }

    @Override
    public BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.999999F);
    }

    private void setUnderwaterMovement() {
        this.setFluidMovement(0.99F);
    }

    private void setUnderLavaMovement() {
        this.setFluidMovement(0.95F);
    }

    private void setFluidMovement(double multiplier) {
        Vec3 deltaMovement = this.getDeltaMovement();
        this.setDeltaMovement(deltaMovement.x * multiplier, deltaMovement.y + (deltaMovement.y < 0.06F ? 5.0E-4F : 0.0F), deltaMovement.z * multiplier);
    }

    private void mergeWithNeighbours() {
        if (this.isMergable()) {
            for (ItemEntity itemEntity : this.level()
                .getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(0.5, 0.0, 0.5), neighbour -> neighbour != this && neighbour.isMergable())) {
                if (itemEntity.isMergable()) {
                    this.tryToMerge(itemEntity);
                    if (this.isRemoved()) {
                        break;
                    }
                }
            }
        }
    }

    private boolean isMergable() {
        ItemStack item = this.getItem();
        return this.isAlive() && this.pickupDelay != 32767 && this.age != -32768 && this.age < 6000 && item.getCount() < item.getMaxStackSize();
    }

    private void tryToMerge(ItemEntity itemEntity) {
        ItemStack item = this.getItem();
        ItemStack item1 = itemEntity.getItem();
        if (Objects.equals(this.target, itemEntity.target) && areMergable(item, item1)) {
            if (item1.getCount() < item.getCount()) {
                merge(this, item, itemEntity, item1);
            } else {
                merge(itemEntity, item1, this, item);
            }
        }
    }

    public static boolean areMergable(ItemStack destinationStack, ItemStack originStack) {
        return originStack.getCount() + destinationStack.getCount() <= originStack.getMaxStackSize()
            && ItemStack.isSameItemSameComponents(destinationStack, originStack);
    }

    public static ItemStack merge(ItemStack destinationStack, ItemStack originStack, int amount) {
        int min = Math.min(Math.min(destinationStack.getMaxStackSize(), amount) - destinationStack.getCount(), originStack.getCount());
        ItemStack itemStack = destinationStack.copyWithCount(destinationStack.getCount() + min);
        originStack.shrink(min);
        return itemStack;
    }

    private static void merge(ItemEntity destinationEntity, ItemStack destinationStack, ItemStack originStack) {
        ItemStack itemStack = merge(destinationStack, originStack, 64);
        destinationEntity.setItem(itemStack);
    }

    private static void merge(ItemEntity destinationEntity, ItemStack destinationStack, ItemEntity originEntity, ItemStack originStack) {
        merge(destinationEntity, destinationStack, originStack);
        destinationEntity.pickupDelay = Math.max(destinationEntity.pickupDelay, originEntity.pickupDelay);
        destinationEntity.age = Math.min(destinationEntity.age, originEntity.age);
        if (originStack.isEmpty()) {
            originEntity.discard();
        }
    }

    @Override
    public boolean fireImmune() {
        return !this.getItem().canBeHurtBy(this.damageSources().inFire()) || super.fireImmune();
    }

    @Override
    protected boolean shouldPlayLavaHurtSound() {
        return this.health <= 0 || this.tickCount % 10 == 0;
    }

    @Override
    public final boolean hurtClient(DamageSource damageSource) {
        return !this.isInvulnerableToBase(damageSource) && this.getItem().canBeHurtBy(damageSource);
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableToBase(damageSource)) {
            return false;
        } else if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && damageSource.getEntity() instanceof Mob) {
            return false;
        } else if (!this.getItem().canBeHurtBy(damageSource)) {
            return false;
        } else {
            this.markHurt();
            this.health = (int)(this.health - amount);
            this.gameEvent(GameEvent.ENTITY_DAMAGE, damageSource.getEntity());
            if (this.health <= 0) {
                this.getItem().onDestroyed(this);
                this.discard();
            }

            return true;
        }
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return !explosion.shouldAffectBlocklikeEntities() || super.ignoreExplosion(explosion);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        compound.putShort("Health", (short)this.health);
        compound.putShort("Age", (short)this.age);
        compound.putShort("PickupDelay", (short)this.pickupDelay);
        if (this.thrower != null) {
            compound.putUUID("Thrower", this.thrower);
        }

        if (this.target != null) {
            compound.putUUID("Owner", this.target);
        }

        if (!this.getItem().isEmpty()) {
            compound.put("Item", this.getItem().save(this.registryAccess()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        this.health = compound.getShort("Health");
        this.age = compound.getShort("Age");
        if (compound.contains("PickupDelay")) {
            this.pickupDelay = compound.getShort("PickupDelay");
        }

        if (compound.hasUUID("Owner")) {
            this.target = compound.getUUID("Owner");
        }

        if (compound.hasUUID("Thrower")) {
            this.thrower = compound.getUUID("Thrower");
            this.cachedThrower = null;
        }

        if (compound.contains("Item", 10)) {
            CompoundTag compound1 = compound.getCompound("Item");
            this.setItem(ItemStack.parse(this.registryAccess(), compound1).orElse(ItemStack.EMPTY));
        } else {
            this.setItem(ItemStack.EMPTY);
        }

        if (this.getItem().isEmpty()) {
            this.discard();
        }
    }

    @Override
    public void playerTouch(Player entity) {
        if (!this.level().isClientSide) {
            ItemStack item = this.getItem();
            Item item1 = item.getItem();
            int count = item.getCount();
            if (this.pickupDelay == 0 && (this.target == null || this.target.equals(entity.getUUID())) && entity.getInventory().add(item)) {
                entity.take(this, count);
                if (item.isEmpty()) {
                    this.discard();
                    item.setCount(count);
                }

                entity.awardStat(Stats.ITEM_PICKED_UP.get(item1), count);
                entity.onItemPickup(this);
            }
        }
    }

    @Override
    public Component getName() {
        Component customName = this.getCustomName();
        return customName != null ? customName : this.getItem().getItemName();
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Nullable
    @Override
    public Entity teleport(TeleportTransition teleportTransition) {
        Entity entity = super.teleport(teleportTransition);
        if (!this.level().isClientSide && entity instanceof ItemEntity itemEntity) {
            itemEntity.mergeWithNeighbours();
        }

        return entity;
    }

    public ItemStack getItem() {
        return this.getEntityData().get(DATA_ITEM);
    }

    public void setItem(ItemStack stack) {
        this.getEntityData().set(DATA_ITEM, stack);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ITEM.equals(key)) {
            this.getItem().setEntityRepresentation(this);
        }
    }

    public void setTarget(@Nullable UUID target) {
        this.target = target;
    }

    public void setThrower(Entity thrower) {
        this.thrower = thrower.getUUID();
        this.cachedThrower = thrower;
    }

    public int getAge() {
        return this.age;
    }

    public void setDefaultPickUpDelay() {
        this.pickupDelay = 10;
    }

    public void setNoPickUpDelay() {
        this.pickupDelay = 0;
    }

    public void setNeverPickUp() {
        this.pickupDelay = 32767;
    }

    public void setPickUpDelay(int pickupDelay) {
        this.pickupDelay = pickupDelay;
    }

    public boolean hasPickUpDelay() {
        return this.pickupDelay > 0;
    }

    public void setUnlimitedLifetime() {
        this.age = -32768;
    }

    public void setExtendedLifetime() {
        this.age = -6000;
    }

    public void makeFakeItem() {
        this.setNeverPickUp();
        this.age = 5999;
    }

    public static float getSpin(float age, float bobOffset) {
        return age / 20.0F + bobOffset;
    }

    public ItemEntity copy() {
        return new ItemEntity(this);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.AMBIENT;
    }

    @Override
    public float getVisualRotationYInDegrees() {
        return 180.0F - getSpin(this.getAge() + 0.5F, this.bobOffs) / (float) (Math.PI * 2) * 360.0F;
    }

    @Override
    public SlotAccess getSlot(int slot) {
        return slot == 0 ? SlotAccess.of(this::getItem, this::setItem) : super.getSlot(slot);
    }
}
