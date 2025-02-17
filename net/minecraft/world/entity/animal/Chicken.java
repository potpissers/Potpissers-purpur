package net.minecraft.world.entity.animal;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;

public class Chicken extends Animal {
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.CHICKEN.getDimensions().scale(0.5F).withEyeHeight(0.2975F);
    public float flap;
    public float flapSpeed;
    public float oFlapSpeed;
    public float oFlap;
    public float flapping = 1.0F;
    private float nextFlap = 1.0F;
    public int eggTime = this.random.nextInt(6000) + 6000;
    public boolean isChickenJockey;

    public Chicken(EntityType<? extends Chicken> entityType, Level level) {
        super(entityType, level);
        this.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.chickenRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.chickenRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.chickenControllable;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.chickenMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.chickenScale);
        // Purpur start - Chickens can retaliate
        if (level().purpurConfig.chickenRetaliate) {
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2.0D);
        }
        // Purpur end - Chickens can retaliate
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Make entity breeding times configurable
    @Override
    public int getPurpurBreedTime() {
        return this.level().purpurConfig.chickenBreedingTicks;
    }
    // Purpur end - Make entity breeding times configurable

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        //this.goalSelector.addGoal(1, new PanicGoal(this, 1.4)); // Purpur - Chickens can retaliate - moved down
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.0, itemStack -> itemStack.is(ItemTags.CHICKEN_FOOD), false));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        // Purpur start - Chickens can retaliate
        if (level().purpurConfig.chickenRetaliate) {
            this.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.0D, false));
            this.targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(this));
        } else {
            this.goalSelector.addGoal(1, new PanicGoal(this, 1.4D));
        }
        // Purpur end - Chickens can retaliate
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 4.0).add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.ATTACK_DAMAGE, 0.0D); // Purpur - Chickens can retaliate
    }

    @Override
    public void aiStep() {
        super.aiStep();
        this.oFlap = this.flap;
        this.oFlapSpeed = this.flapSpeed;
        this.flapSpeed = this.flapSpeed + (this.onGround() ? -1.0F : 4.0F) * 0.3F;
        this.flapSpeed = Mth.clamp(this.flapSpeed, 0.0F, 1.0F);
        if (!this.onGround() && this.flapping < 1.0F) {
            this.flapping = 1.0F;
        }

        this.flapping *= 0.9F;
        Vec3 deltaMovement = this.getDeltaMovement();
        if (!this.onGround() && deltaMovement.y < 0.0) {
            this.setDeltaMovement(deltaMovement.multiply(1.0, 0.6, 1.0));
        }

        this.flap = this.flap + this.flapping * 2.0F;
        if (this.level() instanceof ServerLevel serverLevel && this.isAlive() && !this.isBaby() && !this.isChickenJockey() && --this.eggTime <= 0) {
            this.forceDrops = true; // CraftBukkit
            if (this.dropFromGiftLootTable(serverLevel, BuiltInLootTables.CHICKEN_LAY, this::spawnAtLocation)) {
                this.playSound(SoundEvents.CHICKEN_EGG, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                this.gameEvent(GameEvent.ENTITY_PLACE);
            }
            this.forceDrops = false; // CraftBukkit

            this.eggTime = this.random.nextInt(6000) + 6000;
        }
    }

    @Override
    protected boolean isFlapping() {
        return this.flyDist > this.nextFlap;
    }

    @Override
    protected void onFlap() {
        this.nextFlap = this.flyDist + this.flapSpeed / 2.0F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.CHICKEN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.CHICKEN_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.CHICKEN_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        this.playSound(SoundEvents.CHICKEN_STEP, 0.15F, 1.0F);
    }

    @Nullable
    @Override
    public Chicken getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return EntityType.CHICKEN.create(level, EntitySpawnReason.BREEDING);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.CHICKEN_FOOD);
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return this.isChickenJockey() ? 10 : super.getBaseExperienceReward(level);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.isChickenJockey = compound.getBoolean("IsChickenJockey");
        if (compound.contains("EggLayTime")) {
            this.eggTime = compound.getInt("EggLayTime");
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("IsChickenJockey", this.isChickenJockey);
        compound.putInt("EggLayTime", this.eggTime);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return this.isChickenJockey();
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        super.positionRider(passenger, callback);
        if (passenger instanceof LivingEntity) {
            ((LivingEntity)passenger).yBodyRot = this.yBodyRot;
        }
    }

    public boolean isChickenJockey() {
        return this.isChickenJockey;
    }

    public void setChickenJockey(boolean isChickenJockey) {
        this.isChickenJockey = isChickenJockey;
    }
}
