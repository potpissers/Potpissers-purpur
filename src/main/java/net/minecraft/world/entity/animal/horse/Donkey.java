package net.minecraft.world.entity.animal.horse;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;

public class Donkey extends AbstractChestedHorse {
    public Donkey(EntityType<? extends Donkey> type, Level world) {
        super(type, world);
    }

    // Purpur start
    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.donkeyRidableInWater;
    }
    // Purpur end

    @Override
    public float generateMaxHealth(net.minecraft.util.RandomSource random) {
        return (float) generateMaxHealth(this.level().purpurConfig.donkeyMaxHealthMin, this.level().purpurConfig.donkeyMaxHealthMax);
    }

    @Override
    public double generateJumpStrength(net.minecraft.util.RandomSource random) {
        return generateJumpStrength(this.level().purpurConfig.donkeyJumpStrengthMin, this.level().purpurConfig.donkeyJumpStrengthMax);
    }

    @Override
    public double generateSpeed(net.minecraft.util.RandomSource random) {
        return generateSpeed(this.level().purpurConfig.donkeyMovementSpeedMin, this.level().purpurConfig.donkeyMovementSpeedMax);
    }

    @Override
    public int getPurpurBreedTime() {
        return this.level().purpurConfig.donkeyBreedingTicks;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.DONKEY_AMBIENT;
    }

    @Override
    protected SoundEvent getAngrySound() {
        return SoundEvents.DONKEY_ANGRY;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.DONKEY_DEATH;
    }

    @Nullable
    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.DONKEY_EAT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.DONKEY_HURT;
    }

    @Override
    public boolean canMate(Animal other) {
        return other != this && (other instanceof Donkey || other instanceof Horse) && this.canParent() && ((AbstractHorse)other).canParent();
    }

    @Override
    protected void playJumpSound() {
        this.playSound(SoundEvents.DONKEY_JUMP, 0.4F, 1.0F);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel world, AgeableMob entity) {
        EntityType<? extends AbstractHorse> entityType = entity instanceof Horse ? EntityType.MULE : EntityType.DONKEY;
        AbstractHorse abstractHorse = entityType.create(world);
        if (abstractHorse != null) {
            this.setOffspringAttributes(entity, abstractHorse);
        }

        return abstractHorse;
    }
}
