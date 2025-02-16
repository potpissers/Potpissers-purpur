package net.minecraft.world.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class ItemBasedSteering {
    private static final int MIN_BOOST_TIME = 140;
    private static final int MAX_BOOST_TIME = 700;
    private final SynchedEntityData entityData;
    private final EntityDataAccessor<Integer> boostTimeAccessor;
    private final EntityDataAccessor<Boolean> hasSaddleAccessor;
    public boolean boosting;
    public int boostTime;

    public ItemBasedSteering(SynchedEntityData entityData, EntityDataAccessor<Integer> boostTimeAccessor, EntityDataAccessor<Boolean> hasSaddleAccessor) {
        this.entityData = entityData;
        this.boostTimeAccessor = boostTimeAccessor;
        this.hasSaddleAccessor = hasSaddleAccessor;
    }

    public void onSynced() {
        this.boosting = true;
        this.boostTime = 0;
    }

    public boolean boost(RandomSource random) {
        if (this.boosting) {
            return false;
        } else {
            this.boosting = true;
            this.boostTime = 0;
            this.entityData.set(this.boostTimeAccessor, random.nextInt(841) + 140);
            return true;
        }
    }

    public void tickBoost() {
        if (this.boosting && this.boostTime++ > this.boostTimeTotal()) {
            this.boosting = false;
        }
    }

    public float boostFactor() {
        return this.boosting ? 1.0F + 1.15F * Mth.sin((float)this.boostTime / this.boostTimeTotal() * (float) Math.PI) : 1.0F;
    }

    public int boostTimeTotal() {
        return this.entityData.get(this.boostTimeAccessor);
    }

    public void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putBoolean("Saddle", this.hasSaddle());
    }

    public void readAdditionalSaveData(CompoundTag nbt) {
        this.setSaddle(nbt.getBoolean("Saddle"));
    }

    public void setSaddle(boolean saddled) {
        this.entityData.set(this.hasSaddleAccessor, saddled);
    }

    public boolean hasSaddle() {
        return this.entityData.get(this.hasSaddleAccessor);
    }
}
