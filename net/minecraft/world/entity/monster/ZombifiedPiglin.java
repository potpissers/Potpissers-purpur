package net.minecraft.world.entity.monster;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;

public class ZombifiedPiglin extends Zombie implements NeutralMob {
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.ZOMBIFIED_PIGLIN.getDimensions().scale(0.5F).withEyeHeight(0.97F);
    private static final ResourceLocation SPEED_MODIFIER_ATTACKING_ID = ResourceLocation.withDefaultNamespace("attacking");
    private static final AttributeModifier SPEED_MODIFIER_ATTACKING = new AttributeModifier(
        SPEED_MODIFIER_ATTACKING_ID, 0.05, AttributeModifier.Operation.ADD_VALUE
    );
    private static final UniformInt FIRST_ANGER_SOUND_DELAY = TimeUtil.rangeOfSeconds(0, 1);
    private int playFirstAngerSoundIn;
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private int remainingPersistentAngerTime;
    @Nullable
    private UUID persistentAngerTarget;
    private static final int ALERT_RANGE_Y = 10;
    private static final UniformInt ALERT_INTERVAL = TimeUtil.rangeOfSeconds(4, 6);
    private int ticksUntilNextAlert;
    private HurtByTargetGoal pathfinderGoalHurtByTarget; // Paper - fix PigZombieAngerEvent cancellation

    public ZombifiedPiglin(EntityType<? extends ZombifiedPiglin> entityType, Level level) {
        super(entityType, level);
        this.setPathfindingMalus(PathType.LAVA, 8.0F);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.zombifiedPiglinRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.zombifiedPiglinRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.zombifiedPiglinControllable;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.zombifiedPiglinMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.zombifiedPiglinScale);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Configurable jockey options
    @Override
    public boolean jockeyOnlyBaby() {
        return level().purpurConfig.zombifiedPiglinJockeyOnlyBaby;
    }

    @Override
    public double jockeyChance() {
        return level().purpurConfig.zombifiedPiglinJockeyChance;
    }

    @Override
    public boolean jockeyTryExistingChickens() {
        return level().purpurConfig.zombifiedPiglinJockeyTryExistingChickens;
    }
    // Purpur end - Configurable jockey options

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.zombifiedPiglinTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.zombifiedPiglinAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    public void setPersistentAngerTarget(@Nullable UUID target) {
        this.persistentAngerTarget = target;
    }

    @Override
    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(2, new ZombieAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.targetSelector.addGoal(1, this.pathfinderGoalHurtByTarget = (new HurtByTargetGoal(this)).setAlertOthers()); // Paper - fix PigZombieAngerEvent cancellation
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(3, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
            .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0)
            .add(Attributes.MOVEMENT_SPEED, 0.23F)
            .add(Attributes.ATTACK_DAMAGE, 5.0);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        AttributeInstance attribute = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (this.isAngry()) {
            if (!this.isBaby() && !attribute.hasModifier(SPEED_MODIFIER_ATTACKING_ID)) {
                attribute.addTransientModifier(SPEED_MODIFIER_ATTACKING);
            }

            this.maybePlayFirstAngerSound();
        } else if (attribute.hasModifier(SPEED_MODIFIER_ATTACKING_ID)) {
            attribute.removeModifier(SPEED_MODIFIER_ATTACKING_ID);
        }

        this.updatePersistentAnger(level, true);
        if (this.getTarget() != null) {
            this.maybeAlertOthers();
        }

        if (this.isAngry() && this.level().purpurConfig.zombifiedPiglinCountAsPlayerKillWhenAngry) { // Purpur - Toggle for Zombified Piglin death always counting as player kill when angry
            this.lastHurtByPlayerTime = this.tickCount;
        }

        super.customServerAiStep(level);
    }

    private void maybePlayFirstAngerSound() {
        if (this.playFirstAngerSoundIn > 0) {
            this.playFirstAngerSoundIn--;
            if (this.playFirstAngerSoundIn == 0) {
                this.playAngerSound();
            }
        }
    }

    private void maybeAlertOthers() {
        if (this.ticksUntilNextAlert > 0) {
            this.ticksUntilNextAlert--;
        } else {
            if (this.getSensing().hasLineOfSight(this.getTarget())) {
                this.alertOthers();
            }

            this.ticksUntilNextAlert = ALERT_INTERVAL.sample(this.random);
        }
    }

    private void alertOthers() {
        double attributeValue = this.getAttributeValue(Attributes.FOLLOW_RANGE);
        AABB aabb = AABB.unitCubeFromLowerCorner(this.position()).inflate(attributeValue, 10.0, attributeValue);
        this.level()
            .getEntitiesOfClass(ZombifiedPiglin.class, aabb, EntitySelector.NO_SPECTATORS)
            .stream()
            .filter(zombifiedPiglin -> zombifiedPiglin != this)
            .filter(zombifiedPiglin -> zombifiedPiglin.getTarget() == null)
            .filter(zombifiedPiglin -> !zombifiedPiglin.isAlliedTo(this.getTarget()))
            .forEach(zombifiedPiglin -> zombifiedPiglin.setTarget(this.getTarget(), org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_ATTACKED_NEARBY_ENTITY, true)); // CraftBukkit
    }

    private void playAngerSound() {
        this.playSound(SoundEvents.ZOMBIFIED_PIGLIN_ANGRY, this.getSoundVolume() * 2.0F, this.getVoicePitch() * 1.8F);
    }

    @Override
    public boolean setTarget(@Nullable LivingEntity livingEntity, org.bukkit.event.entity.EntityTargetEvent.TargetReason reason, boolean fireEvent) { // CraftBukkit - signature
        if (this.getTarget() == null && livingEntity != null) {
            this.playFirstAngerSoundIn = FIRST_ANGER_SOUND_DELAY.sample(this.random);
            this.ticksUntilNextAlert = ALERT_INTERVAL.sample(this.random);
        }

        if (livingEntity instanceof Player && this.level().purpurConfig.zombifiedPiglinCountAsPlayerKillWhenAngry) { // Purpur - Toggle for Zombified Piglin death always counting as player kill when angry
            this.setLastHurtByPlayer((Player)livingEntity);
        }

        return super.setTarget(livingEntity, reason, fireEvent); // CraftBukkit
    }

    @Override
    public void startPersistentAngerTimer() {
        // CraftBukkit start
        net.minecraft.world.entity.Entity entity = ((ServerLevel) this.level()).getEntity(this.getPersistentAngerTarget());
        org.bukkit.event.entity.PigZombieAngerEvent event = new org.bukkit.event.entity.PigZombieAngerEvent((org.bukkit.entity.PigZombie) this.getBukkitEntity(), (entity == null) ? null : entity.getBukkitEntity(), ZombifiedPiglin.PERSISTENT_ANGER_TIME.sample(this.random));
        this.level().getCraftServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            this.setPersistentAngerTarget(null);
            this.pathfinderGoalHurtByTarget.stop(); // Paper - fix PigZombieAngerEvent cancellation
            return;
        }
        this.setRemainingPersistentAngerTime(event.getNewAnger());
        // CraftBukkit end
    }

    public static boolean checkZombifiedPiglinSpawnRules(
        EntityType<ZombifiedPiglin> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return level.getDifficulty() != Difficulty.PEACEFUL && !level.getBlockState(pos.below()).is(Blocks.NETHER_WART_BLOCK);
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this) && !level.containsAnyLiquid(this.getBoundingBox());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        this.addPersistentAngerSaveData(compound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.readPersistentAngerSaveData(this.level(), compound);
    }

    @Override
    public void setRemainingPersistentAngerTime(int time) {
        this.remainingPersistentAngerTime = time;
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return this.remainingPersistentAngerTime;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isAngry() ? SoundEvents.ZOMBIFIED_PIGLIN_ANGRY : SoundEvents.ZOMBIFIED_PIGLIN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ZOMBIFIED_PIGLIN_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIFIED_PIGLIN_DEATH;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
    }

    @Override
    protected ItemStack getSkull() {
        return ItemStack.EMPTY;
    }

    @Override
    protected void randomizeReinforcementsChance() {
        this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(this.random.nextDouble() * this.level().purpurConfig.zombifiedPiglinSpawnReinforcements); // Purpur - Configurable entity base attributes
    }

    @Nullable
    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public boolean isPreventingPlayerRest(ServerLevel level, Player player) {
        return this.isAngryAt(player, level);
    }

    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
        return this.canHoldItem(stack);
    }
}
