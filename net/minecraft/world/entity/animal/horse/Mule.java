package net.minecraft.world.entity.animal.horse;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class Mule extends AbstractChestedHorse {
    public Mule(EntityType<? extends Mule> entityType, Level level) {
        super(entityType, level);
    }

    // Purpur start - Ridables
    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.muleRidableInWater;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public float generateMaxHealth(net.minecraft.util.RandomSource random) {
        return (float) generateMaxHealth(this.level().purpurConfig.muleMaxHealthMin, this.level().purpurConfig.muleMaxHealthMax);
    }

    @Override
    public double generateJumpStrength(net.minecraft.util.RandomSource random) {
        return generateJumpStrength(this.level().purpurConfig.muleJumpStrengthMin, this.level().purpurConfig.muleJumpStrengthMax);
    }

    @Override
    public double generateSpeed(net.minecraft.util.RandomSource random) {
        return generateSpeed(this.level().purpurConfig.muleMovementSpeedMin, this.level().purpurConfig.muleMovementSpeedMax);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Make entity breeding times configurable
    @Override
    public int getPurpurBreedTime() {
        return this.level().purpurConfig.muleBreedingTicks;
    }
    // Purpur end - Make entity breeding times configurable

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.MULE_AMBIENT;
    }

    @Override
    protected SoundEvent getAngrySound() {
        return SoundEvents.MULE_ANGRY;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.MULE_DEATH;
    }

    @Nullable
    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.MULE_EAT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.MULE_HURT;
    }

    @Override
    protected void playJumpSound() {
        this.playSound(SoundEvents.MULE_JUMP, 0.4F, 1.0F);
    }

    @Override
    protected void playChestEquipsSound() {
        this.playSound(SoundEvents.MULE_CHEST, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return EntityType.MULE.create(level, EntitySpawnReason.BREEDING);
    }
}
