package net.minecraft.world.entity.monster.hoglin;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Dynamic;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class Hoglin extends Animal implements Enemy, HoglinBase {
    private static final EntityDataAccessor<Boolean> DATA_IMMUNE_TO_ZOMBIFICATION = SynchedEntityData.defineId(Hoglin.class, EntityDataSerializers.BOOLEAN);
    private static final int MAX_HEALTH = 40;
    private static final float MOVEMENT_SPEED_WHEN_FIGHTING = 0.3F;
    private static final int ATTACK_KNOCKBACK = 1;
    private static final float KNOCKBACK_RESISTANCE = 0.6F;
    private static final int ATTACK_DAMAGE = 6;
    private static final float BABY_ATTACK_DAMAGE = 0.5F;
    public static final int CONVERSION_TIME = 300;
    private int attackAnimationRemainingTicks;
    public int timeInOverworld;
    public boolean cannotBeHunted;
    protected static final ImmutableList<? extends SensorType<? extends Sensor<? super Hoglin>>> SENSOR_TYPES = ImmutableList.of(
        SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.NEAREST_ADULT, SensorType.HOGLIN_SPECIFIC_SENSOR
    );
    protected static final ImmutableList<? extends MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
        MemoryModuleType.BREED_TARGET,
        MemoryModuleType.NEAREST_LIVING_ENTITIES,
        MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
        MemoryModuleType.NEAREST_VISIBLE_PLAYER,
        MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
        MemoryModuleType.LOOK_TARGET,
        MemoryModuleType.WALK_TARGET,
        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
        MemoryModuleType.PATH,
        MemoryModuleType.ATTACK_TARGET,
        MemoryModuleType.ATTACK_COOLING_DOWN,
        MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLIN,
        MemoryModuleType.AVOID_TARGET,
        MemoryModuleType.VISIBLE_ADULT_PIGLIN_COUNT,
        MemoryModuleType.VISIBLE_ADULT_HOGLIN_COUNT,
        MemoryModuleType.NEAREST_VISIBLE_ADULT_HOGLINS,
        MemoryModuleType.NEAREST_VISIBLE_ADULT,
        MemoryModuleType.NEAREST_REPELLENT,
        MemoryModuleType.PACIFIED,
        MemoryModuleType.IS_PANICKING
    );

    public Hoglin(EntityType<? extends Hoglin> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 5;
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.hoglinRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.hoglinRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.hoglinControllable;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.hoglinMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.hoglinScale);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Make entity breeding times configurable
    @Override
    public int getPurpurBreedTime() {
        return this.level().purpurConfig.hoglinBreedingTicks;
    }
    // Purpur end - Make entity breeding times configurable

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.hoglinTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    @VisibleForTesting
    public void setTimeInOverworld(int timeInOverworld) {
        this.timeInOverworld = timeInOverworld;
    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 40.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.6F)
            .add(Attributes.ATTACK_KNOCKBACK, 1.0)
            .add(Attributes.ATTACK_DAMAGE, 6.0);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity source) {
        if (source instanceof LivingEntity livingEntity) {
            this.attackAnimationRemainingTicks = 10;
            this.level().broadcastEntityEvent(this, (byte)4);
            this.makeSound(SoundEvents.HOGLIN_ATTACK);
            HoglinAi.onHitTarget(this, livingEntity);
            return HoglinBase.hurtAndThrowTarget(level, this, livingEntity);
        } else {
            return false;
        }
    }

    @Override
    protected void blockedByShield(LivingEntity entity) {
        if (this.isAdult()) {
            HoglinBase.throwTarget(this, entity);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        boolean flag = super.hurtServer(level, damageSource, amount);
        if (flag && damageSource.getEntity() instanceof LivingEntity livingEntity) {
            HoglinAi.wasHurtBy(level, this, livingEntity);
        }

        return flag;
    }

    @Override
    protected Brain.Provider<Hoglin> brainProvider() {
        return Brain.provider(MEMORY_TYPES, SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return HoglinAi.makeBrain(this.brainProvider().makeBrain(dynamic));
    }

    @Override
    public Brain<Hoglin> getBrain() {
        return (Brain<Hoglin>)super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("hoglinBrain");
        //if ((getRider() == null || !this.isControllable()) && this.behaviorTick++ % this.activatedPriority == 0) // Pufferfish // Purpur - only use brain if no rider
        this.getBrain().tick(level, this);
        profilerFiller.pop();
        HoglinAi.updateActivity(this);
        if (this.isConverting()) {
            this.timeInOverworld++;
            if (this.timeInOverworld > 300) {
                this.makeSound(SoundEvents.HOGLIN_CONVERTED_TO_ZOMBIFIED);
                this.finishConversion();
            }
        } else {
            this.timeInOverworld = 0;
        }
    }

    @Override
    public void aiStep() {
        if (this.attackAnimationRemainingTicks > 0) {
            this.attackAnimationRemainingTicks--;
        }

        super.aiStep();
    }

    @Override
    protected void ageBoundaryReached() {
        if (this.isBaby()) {
            this.xpReward = 3;
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.5);
        } else {
            this.xpReward = 5;
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6.0);
        }
    }

    public static boolean checkHoglinSpawnRules(
        EntityType<Hoglin> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return !level.getBlockState(pos.below()).is(Blocks.NETHER_WART_BLOCK);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        if (level.getRandom().nextFloat() < 0.2F) {
            this.setBaby(true);
        }

        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return !this.isPersistenceRequired();
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        if (HoglinAi.isPosNearNearestRepellent(this, pos)) {
            return -1.0F;
        } else {
            return level.getBlockState(pos.below()).is(Blocks.CRIMSON_NYLIUM) ? 10.0F : 0.0F;
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        InteractionResult interactionResult = super.mobInteract(player, hand);
        if (interactionResult.consumesAction()) {
            this.setPersistenceRequired();
        }

        return interactionResult;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) {
            this.attackAnimationRemainingTicks = 10;
            this.makeSound(SoundEvents.HOGLIN_ATTACK);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public int getAttackAnimationRemainingTicks() {
        return this.attackAnimationRemainingTicks;
    }

    @Override
    public boolean shouldDropExperience() {
        return true;
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return this.xpReward;
    }

    private void finishConversion() {
        final Entity converted = this.convertTo(EntityType.ZOGLIN, ConversionParams.single(this, true, false),  mob -> {mob.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));}, org.bukkit.event.entity.EntityTransformEvent.TransformReason.PIGLIN_ZOMBIFIED, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.PIGLIN_ZOMBIFIED); // CraftBukkit - add spawn and transform reasons
        // Paper start - Fix issues with mob conversion; reset to prevent event spam
        if (converted == null) {
            this.timeInOverworld = 0;
        }
        // Paper end - Fix issues with mob conversion
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.HOGLIN_FOOD);
    }

    public boolean isAdult() {
        return !this.isBaby();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_IMMUNE_TO_ZOMBIFICATION, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (this.isImmuneToZombification()) {
            compound.putBoolean("IsImmuneToZombification", true);
        }

        compound.putInt("TimeInOverworld", this.timeInOverworld);
        if (this.cannotBeHunted) {
            compound.putBoolean("CannotBeHunted", true);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setImmuneToZombification(compound.getBoolean("IsImmuneToZombification"));
        this.timeInOverworld = compound.getInt("TimeInOverworld");
        this.setCannotBeHunted(compound.getBoolean("CannotBeHunted"));
    }

    public void setImmuneToZombification(boolean immuneToZombification) {
        this.getEntityData().set(DATA_IMMUNE_TO_ZOMBIFICATION, immuneToZombification);
    }

    public boolean isImmuneToZombification() {
        return this.getEntityData().get(DATA_IMMUNE_TO_ZOMBIFICATION);
    }

    public boolean isConverting() {
        return !this.level().dimensionType().piglinSafe() && !this.isImmuneToZombification() && !this.isNoAi();
    }

    private void setCannotBeHunted(boolean cannotBeHunted) {
        this.cannotBeHunted = cannotBeHunted;
    }

    public boolean canBeHunted() {
        return this.isAdult() && !this.cannotBeHunted;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        Hoglin hoglin = EntityType.HOGLIN.create(level, EntitySpawnReason.BREEDING);
        if (hoglin != null) {
            hoglin.setPersistenceRequired();
        }

        return hoglin;
    }

    @Override
    public boolean canFallInLove() {
        return !HoglinAi.isPacified(this) && super.canFallInLove();
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.level().isClientSide ? null : HoglinAi.getSoundForCurrentActivity(this).orElse(null);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.HOGLIN_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.HOGLIN_DEATH;
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.HOSTILE_SWIM;
    }

    @Override
    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.HOSTILE_SPLASH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        this.playSound(SoundEvents.HOGLIN_STEP, 0.15F, 1.0F);
    }

    @Override
    protected void sendDebugPackets() {
        super.sendDebugPackets();
        DebugPackets.sendEntityBrain(this);
    }

    @Nullable
    @Override
    public LivingEntity getTarget() {
        return this.getTargetFromBrain();
    }
}
