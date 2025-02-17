package net.minecraft.world.entity.monster;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class Spider extends Monster {
    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(Spider.class, EntityDataSerializers.BYTE);
    private static final float SPIDER_SPECIAL_EFFECT_CHANCE = 0.1F;

    public Spider(EntityType<? extends Spider> entityType, Level level) {
        super(entityType, level);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.spiderRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.spiderRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.spiderControllable;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.spiderMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.spiderScale);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.spiderTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.spiderAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Armadillo.class, 6.0F, 1.0, 1.2, livingEntity -> !((Armadillo)livingEntity).isScared()));
        this.goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(4, new Spider.SpiderAttackGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new Spider.SpiderTargetGoal<>(this, Player.class));
        this.targetSelector.addGoal(3, new Spider.SpiderTargetGoal<>(this, IronGolem.class));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS_ID, (byte)0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            this.setClimbing(this.horizontalCollision && (this.level().paperConfig().entities.behavior.allowSpiderWorldBorderClimbing || !(io.papermc.paper.FeatureHooks.isSpiderCollidingWithWorldBorder(this) && this.level().getWorldBorder().isInsideCloseToBorder(this, this.getBoundingBox())))); // Paper - Add config option for spider worldborder climbing (Inflate by +EPSILON as collision will just barely place us outside border)
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 16.0).add(Attributes.MOVEMENT_SPEED, 0.3F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SPIDER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.SPIDER_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.SPIDER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        this.playSound(SoundEvents.SPIDER_STEP, 0.15F, 1.0F);
    }

    @Override
    public boolean onClimbable() {
        return this.isClimbing();
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 motionMultiplier) {
        if (!state.is(Blocks.COBWEB)) {
            super.makeStuckInBlock(state, motionMultiplier);
        }
    }

    @Override
    public boolean canBeAffected(MobEffectInstance potioneffect) {
        return (!potioneffect.is(MobEffects.POISON) || !this.level().paperConfig().entities.mobEffects.spidersImmuneToPoisonEffect) && super.canBeAffected(potioneffect); // Paper - Add config for mobs immune to default effects
    }

    public boolean isClimbing() {
        return (this.entityData.get(DATA_FLAGS_ID) & 1) != 0;
    }

    public void setClimbing(boolean climbing) {
        byte b = this.entityData.get(DATA_FLAGS_ID);
        if (climbing) {
            b = (byte)(b | 1);
        } else {
            b = (byte)(b & -2);
        }

        this.entityData.set(DATA_FLAGS_ID, b);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        spawnGroupData = super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
        RandomSource random = level.getRandom();
        if (random.nextInt(100) == 0) {
            Skeleton skeleton = EntityType.SKELETON.create(this.level(), EntitySpawnReason.JOCKEY);
            if (skeleton != null) {
                skeleton.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                skeleton.finalizeSpawn(level, difficulty, spawnReason, null);
                skeleton.startRiding(this);
            }
        }

        if (spawnGroupData == null) {
            spawnGroupData = new Spider.SpiderEffectsGroupData();
            if (level.getDifficulty() == Difficulty.HARD && random.nextFloat() < 0.1F * difficulty.getSpecialMultiplier()) {
                ((Spider.SpiderEffectsGroupData)spawnGroupData).setRandomEffect(random);
            }
        }

        if (spawnGroupData instanceof Spider.SpiderEffectsGroupData spiderEffectsGroupData) {
            Holder<MobEffect> holder = spiderEffectsGroupData.effect;
            if (holder != null) {
                this.addEffect(new MobEffectInstance(holder, -1), null, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.SPIDER_SPAWN, level instanceof net.minecraft.server.level.ServerLevel); // CraftBukkit
            }
        }

        return spawnGroupData;
    }

    @Override
    public Vec3 getVehicleAttachmentPoint(Entity entity) {
        return entity.getBbWidth() <= this.getBbWidth() ? new Vec3(0.0, 0.3125 * this.getScale(), 0.0) : super.getVehicleAttachmentPoint(entity);
    }

    static class SpiderAttackGoal extends MeleeAttackGoal {
        public SpiderAttackGoal(Spider spider) {
            super(spider, 1.0, true);
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !this.mob.isVehicle();
        }

        @Override
        public boolean canContinueToUse() {
            float lightLevelDependentMagicValue = this.mob.getLightLevelDependentMagicValue();
            if (lightLevelDependentMagicValue >= 0.5F && this.mob.getRandom().nextInt(100) == 0) {
                this.mob.setTarget(null);
                return false;
            } else {
                return super.canContinueToUse();
            }
        }
    }

    public static class SpiderEffectsGroupData implements SpawnGroupData {
        @Nullable
        public Holder<MobEffect> effect;

        public void setRandomEffect(RandomSource random) {
            int randomInt = random.nextInt(5);
            if (randomInt <= 1) {
                this.effect = MobEffects.MOVEMENT_SPEED;
            } else if (randomInt <= 2) {
                this.effect = MobEffects.DAMAGE_BOOST;
            } else if (randomInt <= 3) {
                this.effect = MobEffects.REGENERATION;
            } else if (randomInt <= 4) {
                this.effect = MobEffects.INVISIBILITY;
            }
        }
    }

    static class SpiderTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
        public SpiderTargetGoal(Spider spider, Class<T> entityTypeToTarget) {
            super(spider, entityTypeToTarget, true);
        }

        @Override
        public boolean canUse() {
            float lightLevelDependentMagicValue = this.mob.getLightLevelDependentMagicValue();
            return !(lightLevelDependentMagicValue >= 0.5F) && super.canUse();
        }
    }
}
