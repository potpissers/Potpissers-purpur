package net.minecraft.world.entity.animal.horse;

import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.SoundType;

public class Horse extends AbstractHorse implements VariantHolder<Variant> {
    private static final EntityDataAccessor<Integer> DATA_ID_TYPE_VARIANT = SynchedEntityData.defineId(Horse.class, EntityDataSerializers.INT);
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.HORSE
        .getDimensions()
        .withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, EntityType.HORSE.getHeight() + 0.125F, 0.0F))
        .scale(0.5F);

    public Horse(EntityType<? extends Horse> entityType, Level level) {
        super(entityType, level);
    }

    // Purpur start - Ridables
    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.horseRidableInWater;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public float generateMaxHealth(RandomSource random) {
        return (float) generateMaxHealth(this.level().purpurConfig.horseMaxHealthMin, this.level().purpurConfig.horseMaxHealthMax);
    }

    @Override
    public double generateJumpStrength(RandomSource random) {
        return generateJumpStrength(this.level().purpurConfig.horseJumpStrengthMin, this.level().purpurConfig.horseJumpStrengthMax);
    }

    @Override
    public double generateSpeed(RandomSource random) {
        return generateSpeed(this.level().purpurConfig.horseMovementSpeedMin, this.level().purpurConfig.horseMovementSpeedMax);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Make entity breeding times configurable
    @Override
    public int getPurpurBreedTime() {
        return this.level().purpurConfig.horseBreedingTicks;
    }
    // Purpur end - Make entity breeding times configurable

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.horseTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.horseAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    protected void randomizeAttributes(RandomSource random) {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(generateMaxHealth(random::nextInt));
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(generateSpeed(random::nextDouble));
        this.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(generateJumpStrength(random::nextDouble));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ID_TYPE_VARIANT, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Variant", this.getTypeVariant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setTypeVariant(compound.getInt("Variant"));
    }

    private void setTypeVariant(int typeVariant) {
        this.entityData.set(DATA_ID_TYPE_VARIANT, typeVariant);
    }

    private int getTypeVariant() {
        return this.entityData.get(DATA_ID_TYPE_VARIANT);
    }

    public void setVariantAndMarkings(Variant variant, Markings marking) {
        this.setTypeVariant(variant.getId() & 0xFF | marking.getId() << 8 & 0xFF00);
    }

    @Override
    public Variant getVariant() {
        return Variant.byId(this.getTypeVariant() & 0xFF);
    }

    @Override
    public void setVariant(Variant variant) {
        this.setTypeVariant(variant.getId() & 0xFF | this.getTypeVariant() & -256);
    }

    public Markings getMarkings() {
        return Markings.byId((this.getTypeVariant() & 0xFF00) >> 8);
    }

    @Override
    protected void playGallopSound(SoundType soundType) {
        super.playGallopSound(soundType);
        if (this.random.nextInt(10) == 0) {
            this.playSound(SoundEvents.HORSE_BREATHE, soundType.getVolume() * 0.6F, soundType.getPitch());
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.HORSE_AMBIENT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.HORSE_DEATH;
    }

    @Nullable
    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.HORSE_EAT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.HORSE_HURT;
    }

    @Override
    protected SoundEvent getAngrySound() {
        return SoundEvents.HORSE_ANGRY;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        boolean flag = !this.isBaby() && this.isTamed() && player.isSecondaryUseActive();
        if (!this.isVehicle() && !flag) {
            ItemStack itemInHand = player.getItemInHand(hand);
            if (!itemInHand.isEmpty()) {
                if (this.isFood(itemInHand)) {
                    return this.fedFood(player, itemInHand);
                }

                if (!this.isTamed()) {
                    this.makeMad();
                    return InteractionResult.SUCCESS;
                }
            }

            return super.mobInteract(player, hand);
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    public boolean canMate(Animal otherAnimal) {
        return otherAnimal != this
            && (otherAnimal instanceof Donkey || otherAnimal instanceof Horse)
            && this.canParent()
            && ((AbstractHorse)otherAnimal).canParent();
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        if (otherParent instanceof Donkey) {
            Mule mule = EntityType.MULE.create(level, EntitySpawnReason.BREEDING);
            if (mule != null) {
                this.setOffspringAttributes(otherParent, mule);
            }

            return mule;
        } else {
            Horse horse = (Horse)otherParent;
            Horse horse1 = EntityType.HORSE.create(level, EntitySpawnReason.BREEDING);
            if (horse1 != null) {
                int randomInt = this.random.nextInt(9);
                Variant variant;
                if (randomInt < 4) {
                    variant = this.getVariant();
                } else if (randomInt < 8) {
                    variant = horse.getVariant();
                } else {
                    variant = Util.getRandom(Variant.values(), this.random);
                }

                int randomInt1 = this.random.nextInt(5);
                Markings markings;
                if (randomInt1 < 2) {
                    markings = this.getMarkings();
                } else if (randomInt1 < 4) {
                    markings = horse.getMarkings();
                } else {
                    markings = Util.getRandom(Markings.values(), this.random);
                }

                horse1.setVariantAndMarkings(variant, markings);
                this.setOffspringAttributes(otherParent, horse1);
            }

            return horse1;
        }
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return true;
    }

    @Override
    protected void hurtArmor(DamageSource damageSource, float damageAmount) {
        this.doHurtEquipment(damageSource, damageAmount, new EquipmentSlot[]{EquipmentSlot.BODY});
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        RandomSource random = level.getRandom();
        Variant variant;
        if (spawnGroupData instanceof Horse.HorseGroupData) {
            variant = ((Horse.HorseGroupData)spawnGroupData).variant;
        } else {
            variant = Util.getRandom(Variant.values(), random);
            spawnGroupData = new Horse.HorseGroupData(variant);
        }

        this.setVariantAndMarkings(variant, Util.getRandom(Markings.values(), random));
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    public static class HorseGroupData extends AgeableMob.AgeableMobGroupData {
        public final Variant variant;

        public HorseGroupData(Variant variant) {
            super(true);
            this.variant = variant;
        }
    }
}
