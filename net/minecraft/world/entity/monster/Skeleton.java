package net.minecraft.world.entity.monster;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class Skeleton extends AbstractSkeleton {
    private static final int TOTAL_CONVERSION_TIME = 300;
    public static final EntityDataAccessor<Boolean> DATA_STRAY_CONVERSION_ID = SynchedEntityData.defineId(Skeleton.class, EntityDataSerializers.BOOLEAN);
    public static final String CONVERSION_TAG = "StrayConversionTime";
    public int inPowderSnowTime;
    public int conversionTime;

    public Skeleton(EntityType<? extends Skeleton> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STRAY_CONVERSION_ID, false);
    }

    public boolean isFreezeConverting() {
        return this.getEntityData().get(DATA_STRAY_CONVERSION_ID);
    }

    public void setFreezeConverting(boolean isFrozen) {
        this.entityData.set(DATA_STRAY_CONVERSION_ID, isFrozen);
    }

    @Override
    public boolean isShaking() {
        return this.isFreezeConverting();
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.isAlive() && !this.isNoAi()) {
            if (this.isInPowderSnow) {
                if (this.isFreezeConverting()) {
                    this.conversionTime--;
                    if (this.conversionTime < 0) {
                        this.doFreezeConversion();
                    }
                } else {
                    this.inPowderSnowTime++;
                    if (this.inPowderSnowTime >= 140) {
                        this.startFreezeConversion(300);
                    }
                }
            } else {
                this.inPowderSnowTime = -1;
                this.setFreezeConverting(false);
            }
        }

        super.tick();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("StrayConversionTime", this.isFreezeConverting() ? this.conversionTime : -1);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("StrayConversionTime", 99) && compound.getInt("StrayConversionTime") > -1) {
            this.startFreezeConversion(compound.getInt("StrayConversionTime"));
        }
    }

    @VisibleForTesting
    public void startFreezeConversion(int conversionTime) {
        this.conversionTime = conversionTime;
        this.setFreezeConverting(true);
    }

    protected void doFreezeConversion() {
        final Stray stray = this.convertTo(EntityType.STRAY, ConversionParams.single(this, true, true), mob -> { // Paper - Fix issues with mob conversion; reset conversion time to prevent event spam
            if (!this.isSilent()) {
                this.level().levelEvent(null, 1048, this.blockPosition(), 0);
            }
        // Paper start - add spawn and transform reasons
        }, org.bukkit.event.entity.EntityTransformEvent.TransformReason.FROZEN, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.FROZEN);
        if (stray == null) {
            // Reset conversion time to prevent event spam
            this.conversionTime = 300;
        }
        // Paper end - add spawn and transform reasons
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }

    @Override
    SoundEvent getStepSound() {
        return SoundEvents.SKELETON_STEP;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        if (damageSource.getEntity() instanceof Creeper creeper && creeper.canDropMobsSkull()) {
            creeper.increaseDroppedSkulls();
            this.spawnAtLocation(level, Items.SKELETON_SKULL);
        }
    }
}
