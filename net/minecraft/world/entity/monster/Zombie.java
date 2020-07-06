package net.minecraft.world.entity.monster;

import com.google.common.annotations.VisibleForTesting;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class Zombie extends Monster {
    private static final ResourceLocation SPEED_MODIFIER_BABY_ID = ResourceLocation.withDefaultNamespace("baby");
    private final AttributeModifier babyModifier = new AttributeModifier(Zombie.SPEED_MODIFIER_BABY_ID, this.level().paperConfig().entities.behavior.babyZombieMovementModifier, AttributeModifier.Operation.ADD_MULTIPLIED_BASE); // Paper - Make baby speed configurable
    private static final ResourceLocation REINFORCEMENT_CALLER_CHARGE_ID = ResourceLocation.withDefaultNamespace("reinforcement_caller_charge");
    private static final AttributeModifier ZOMBIE_REINFORCEMENT_CALLEE_CHARGE = new AttributeModifier(
        ResourceLocation.withDefaultNamespace("reinforcement_callee_charge"), -0.05F, AttributeModifier.Operation.ADD_VALUE
    );
    private static final ResourceLocation LEADER_ZOMBIE_BONUS_ID = ResourceLocation.withDefaultNamespace("leader_zombie_bonus");
    private static final ResourceLocation ZOMBIE_RANDOM_SPAWN_BONUS_ID = ResourceLocation.withDefaultNamespace("zombie_random_spawn_bonus");
    private static final EntityDataAccessor<Boolean> DATA_BABY_ID = SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_SPECIAL_TYPE_ID = SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> DATA_DROWNED_CONVERSION_ID = SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.BOOLEAN);
    public static final float ZOMBIE_LEADER_CHANCE = 0.05F;
    public static final int REINFORCEMENT_ATTEMPTS = 50;
    public static final int REINFORCEMENT_RANGE_MAX = 40;
    public static final int REINFORCEMENT_RANGE_MIN = 7;
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.ZOMBIE.getDimensions().scale(0.5F).withEyeHeight(0.93F);
    private static final float BREAK_DOOR_CHANCE = 0.1F;
    public static final Predicate<Difficulty> DOOR_BREAKING_PREDICATE = difficulty -> difficulty == Difficulty.HARD;
    private final BreakDoorGoal breakDoorGoal; // Paper - move down
    private boolean canBreakDoors;
    private int inWaterTime;
    public int conversionTime;
    private boolean shouldBurnInDay = true; // Paper - Add more Zombie API

    public Zombie(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
        this.breakDoorGoal = new BreakDoorGoal(this, com.google.common.base.Predicates.in(level.paperConfig().entities.behavior.doorBreakingDifficulty.getOrDefault(entityType, level.paperConfig().entities.behavior.doorBreakingDifficulty.get(EntityType.ZOMBIE)))); // Paper - Configurable door breaking difficulty
    }

    public Zombie(Level level) {
        this(EntityType.ZOMBIE, level);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.zombieRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.zombieRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.zombieControllable;
    }
    // Purpur end - Ridables

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        if (this.level().paperConfig().entities.behavior.zombiesTargetTurtleEggs) this.goalSelector.addGoal(4, new Zombie.ZombieAttackTurtleEggGoal(this, 1.0, 3)); // Paper - Add zombie targets turtle egg config
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.addBehaviourGoals();
    }

    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(2, new ZombieAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(6, new MoveThroughVillageGoal(this, 1.0, true, 4, this::canBreakDoors));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(ZombifiedPiglin.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        // Purpur start - Add option to disable zombie aggressiveness towards villagers
        if (this.level().spigotConfig.zombieAggressiveTowardsVillager) this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false) { // Spigot
            @Override
            public boolean canUse() {
                return (level().purpurConfig.zombieAggressiveTowardsVillagerWhenLagging || !level().getServer().server.isLagging()) && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return (level().purpurConfig.zombieAggressiveTowardsVillagerWhenLagging || !level().getServer().server.isLagging()) && super.canContinueToUse();
            }
        });
        // Purpur end - Add option to disable zombie aggressiveness towards villagers
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Turtle.class, 10, true, false, Turtle.BABY_ON_LAND_SELECTOR));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.FOLLOW_RANGE, 35.0)
            .add(Attributes.MOVEMENT_SPEED, 0.23F)
            .add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.ARMOR, 2.0)
            .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BABY_ID, false);
        builder.define(DATA_SPECIAL_TYPE_ID, 0);
        builder.define(DATA_DROWNED_CONVERSION_ID, false);
    }

    public boolean isUnderWaterConverting() {
        return this.getEntityData().get(DATA_DROWNED_CONVERSION_ID);
    }

    public boolean canBreakDoors() {
        return this.canBreakDoors;
    }

    public void setCanBreakDoors(boolean canBreakDoors) {
        if (GoalUtils.hasGroundPathNavigation(this)) {
            if (this.canBreakDoors != canBreakDoors) {
                this.canBreakDoors = canBreakDoors;
                ((GroundPathNavigation)this.getNavigation()).setCanOpenDoors(canBreakDoors);
                if (canBreakDoors) {
                    this.goalSelector.addGoal(1, this.breakDoorGoal);
                } else {
                    this.goalSelector.removeGoal(this.breakDoorGoal);
                }
            }
        } else if (this.canBreakDoors) {
            this.goalSelector.removeGoal(this.breakDoorGoal);
            this.canBreakDoors = false;
        }
    }

    @Override
    public boolean isBaby() {
        return this.getEntityData().get(DATA_BABY_ID);
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        final int previousReward = this.xpReward; // Paper - store previous value to reset after calculating XP reward
        if (this.isBaby()) {
            this.xpReward = (int)(this.xpReward * 2.5);
        }

        // Paper start - store previous value to reset after calculating XP reward
        int reward = super.getBaseExperienceReward(level);
        this.xpReward = previousReward;
        return reward;
        // Paper end - store previous value to reset after calculating XP reward
    }

    @Override
    public void setBaby(boolean childZombie) {
        this.getEntityData().set(DATA_BABY_ID, childZombie);
        if (this.level() != null && !this.level().isClientSide) {
            AttributeInstance attribute = this.getAttribute(Attributes.MOVEMENT_SPEED);
            attribute.removeModifier(this.babyModifier.id()); // Paper - Make baby speed configurable
            if (childZombie) {
                attribute.addTransientModifier(this.babyModifier); // Paper - Make baby speed configurable
            }
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_BABY_ID.equals(key)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(key);
    }

    protected boolean convertsInWater() {
        return true;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.isAlive() && !this.isNoAi()) {
            if (this.isUnderWaterConverting()) {
                this.conversionTime--;
                if (this.conversionTime < 0) {
                    this.doUnderWaterConversion();
                }
            } else if (this.convertsInWater()) {
                if (this.isEyeInFluid(FluidTags.WATER)) {
                    this.inWaterTime++;
                    if (this.inWaterTime >= 600) {
                        this.startUnderWaterConversion(300);
                    }
                } else {
                    this.inWaterTime = -1;
                }
            }
        }

        super.tick();
    }

    @Override
    public void aiStep() {
        if (this.isAlive()) {
            boolean flag = this.isSunSensitive() && this.isSunBurnTick();
            if (flag) {
                ItemStack itemBySlot = this.getItemBySlot(EquipmentSlot.HEAD);
                if (!itemBySlot.isEmpty()) {
                    if (itemBySlot.isDamageableItem()) {
                        Item item = itemBySlot.getItem();
                        itemBySlot.setDamageValue(itemBySlot.getDamageValue() + this.random.nextInt(2));
                        if (itemBySlot.getDamageValue() >= itemBySlot.getMaxDamage()) {
                            this.onEquippedItemBroken(item, EquipmentSlot.HEAD);
                            this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        }
                    }

                    flag = false;
                }

                if (flag) {
                    this.igniteForSeconds(8.0F);
                }
            }
        }

        super.aiStep();
    }

    // Paper start - Add more Zombie API
    public void stopDrowning() {
        this.conversionTime = -1;
        this.getEntityData().set(Zombie.DATA_DROWNED_CONVERSION_ID, false);
    }
    // Paper end - Add more Zombie API
    public void startUnderWaterConversion(int conversionTime) {
        // this.lastTick = MinecraftServer.currentTick; // CraftBukkit // Paper - remove anti tick skipping measures / wall tim
        this.conversionTime = conversionTime;
        this.getEntityData().set(DATA_DROWNED_CONVERSION_ID, true);
    }

    protected void doUnderWaterConversion() {
        this.convertToZombieType(EntityType.DROWNED);
        if (!this.isSilent()) {
            this.level().levelEvent(null, 1040, this.blockPosition(), 0);
        }
    }

    protected void convertToZombieType(EntityType<? extends Zombie> entityType) {
        Zombie converted = this.convertTo( // CraftBukkit
            entityType,
            ConversionParams.single(this, true, true),
            // CraftBukkit start
            zombie -> { zombie.handleAttributes(zombie.level().getCurrentDifficultyAt(zombie.blockPosition()).getSpecialMultiplier()); },
            org.bukkit.event.entity.EntityTransformEvent.TransformReason.DROWNED, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DROWNED);
        if (converted == null) {
            ((org.bukkit.entity.Zombie) this.getBukkitEntity()).setConversionTime(-1); // CraftBukkit - SPIGOT-5208: End conversion to stop event spam
        }
        // CraftBukkit end
    }

    @VisibleForTesting
    public boolean convertVillagerToZombieVillager(ServerLevel level, Villager villager) {
        // CraftBukkit start
        return Zombie.convertVillagerToZombieVillager(level, villager, this.blockPosition(), this.isSilent(), org.bukkit.event.entity.EntityTransformEvent.TransformReason.INFECTION, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.INFECTION) != null;
    }

    public static ZombieVillager convertVillagerToZombieVillager(ServerLevel level, Villager villager, net.minecraft.core.BlockPos blockPosition, boolean silent, org.bukkit.event.entity.EntityTransformEvent.TransformReason transformReason, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason creatureSpawnReason) {
        // CraftBukkit end
        ZombieVillager zombieVillager = villager.convertTo(EntityType.ZOMBIE_VILLAGER, ConversionParams.single(villager, true, true), mob -> {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.CONVERSION, new Zombie.ZombieGroupData(false, true));
            mob.setVillagerData(villager.getVillagerData());
            mob.setGossips(villager.getGossips().store(NbtOps.INSTANCE));
            mob.setTradeOffers(villager.getOffers().copy());
            mob.setVillagerXp(villager.getVillagerXp());
            // CraftBukkit start
            if (!silent) {
                level.levelEvent(null, 1026, blockPosition, 0);
            }
        }, transformReason, creatureSpawnReason);
        return zombieVillager;
        // CraftBukkit end
    }

    public boolean isSunSensitive() {
        return this.shouldBurnInDay; // Paper - Add more Zombie API
    }
    // Paper start - Add more Zombie API
    public void setShouldBurnInDay(boolean shouldBurnInDay) {
        this.shouldBurnInDay = shouldBurnInDay;
    }
    // Paper end - Add more Zombie API

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (!super.hurtServer(level, damageSource, amount)) {
            return false;
        } else {
            LivingEntity target = this.getTarget();
            if (target == null && damageSource.getEntity() instanceof LivingEntity) {
                target = (LivingEntity)damageSource.getEntity();
            }

            if (target != null
                && level.getDifficulty() == Difficulty.HARD
                && this.random.nextFloat() < this.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE)
                && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                int floor = Mth.floor(this.getX());
                int floor1 = Mth.floor(this.getY());
                int floor2 = Mth.floor(this.getZ());
                EntityType<? extends Zombie> type = this.getType();
                Zombie zombie = type.create(level, EntitySpawnReason.REINFORCEMENT);
                if (zombie == null) {
                    return true;
                }

                for (int i = 0; i < 50; i++) {
                    int i1 = floor + Mth.nextInt(this.random, 7, 40) * Mth.nextInt(this.random, -1, 1);
                    int i2 = floor1 + Mth.nextInt(this.random, 7, 40) * Mth.nextInt(this.random, -1, 1);
                    int i3 = floor2 + Mth.nextInt(this.random, 7, 40) * Mth.nextInt(this.random, -1, 1);
                    BlockPos blockPos = new BlockPos(i1, i2, i3);
                    if (SpawnPlacements.isSpawnPositionOk(type, level, blockPos)
                        && SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.REINFORCEMENT, blockPos, level.random)) {
                        zombie.setPos(i1, i2, i3);
                        if (!level.hasNearbyAlivePlayerThatAffectsSpawning(i1, i2, i3, 7.0) // Paper - affects spawning api
                            && level.isUnobstructed(zombie)
                            && level.noCollision(zombie)
                            && (zombie.canSpawnInLiquids() || !level.containsAnyLiquid(zombie.getBoundingBox()))) {
                            zombie.setTarget(target, org.bukkit.event.entity.EntityTargetEvent.TargetReason.REINFORCEMENT_TARGET, true); // CraftBukkit
                            zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(zombie.blockPosition()), EntitySpawnReason.REINFORCEMENT, null);
                            level.addFreshEntityWithPassengers(zombie, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.REINFORCEMENTS); // CraftBukkit
                            AttributeInstance attribute = this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
                            AttributeModifier modifier = attribute.getModifier(REINFORCEMENT_CALLER_CHARGE_ID);
                            double d = modifier != null ? modifier.amount() : 0.0;
                            attribute.removeModifier(REINFORCEMENT_CALLER_CHARGE_ID);
                            attribute.addPermanentModifier(
                                new AttributeModifier(REINFORCEMENT_CALLER_CHARGE_ID, d - 0.05, AttributeModifier.Operation.ADD_VALUE)
                            );
                            zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).addPermanentModifier(ZOMBIE_REINFORCEMENT_CALLEE_CHARGE);
                            break;
                        }
                    }
                }
            }

            return true;
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity source) {
        boolean flag = super.doHurtTarget(level, source);
        if (flag) {
            float effectiveDifficulty = this.level().getCurrentDifficultyAt(this.blockPosition()).getEffectiveDifficulty();
            if (this.getMainHandItem().isEmpty() && this.isOnFire() && this.random.nextFloat() < effectiveDifficulty * 0.3F) {
                // CraftBukkit start
                org.bukkit.event.entity.EntityCombustByEntityEvent event = new org.bukkit.event.entity.EntityCombustByEntityEvent(this.getBukkitEntity(), source.getBukkitEntity(), (float) (2 * (int)effectiveDifficulty));
                this.level().getCraftServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    source.igniteForSeconds(event.getDuration(), false);
                }
                // CraftBukkit end
            }
        }

        return flag;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ZOMBIE_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_DEATH;
    }

    protected SoundEvent getStepSound() {
        return SoundEvents.ZOMBIE_STEP;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        this.playSound(this.getStepSound(), 0.15F, 1.0F);
    }

    @Override
    public EntityType<? extends Zombie> getType() {
        return (EntityType<? extends Zombie>)super.getType();
    }

    protected boolean canSpawnInLiquids() {
        return false;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        super.populateDefaultEquipmentSlots(random, difficulty);
        if (random.nextFloat() < (this.level().getDifficulty() == Difficulty.HARD ? 0.05F : 0.01F)) {
            int randomInt = random.nextInt(3);
            if (randomInt == 0) {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            } else {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("IsBaby", this.isBaby());
        compound.putBoolean("CanBreakDoors", this.canBreakDoors());
        compound.putInt("InWaterTime", this.isInWater() ? this.inWaterTime : -1);
        compound.putInt("DrownedConversionTime", this.isUnderWaterConverting() ? this.conversionTime : -1);
        compound.putBoolean("Paper.ShouldBurnInDay", this.shouldBurnInDay); // Paper - Add more Zombie API
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setBaby(compound.getBoolean("IsBaby"));
        this.setCanBreakDoors(compound.getBoolean("CanBreakDoors"));
        this.inWaterTime = compound.getInt("InWaterTime");
        if (compound.contains("DrownedConversionTime", 99) && compound.getInt("DrownedConversionTime") > -1) {
            this.startUnderWaterConversion(compound.getInt("DrownedConversionTime"));
        }
        // Paper start - Add more Zombie API
        if (compound.contains("Paper.ShouldBurnInDay")) {
            this.shouldBurnInDay = compound.getBoolean("Paper.ShouldBurnInDay");
        }
        // Paper end - Add more Zombie API
    }

    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity entity) {
        boolean flag = super.killedEntity(level, entity);
        final double fallbackChance = level.getDifficulty() == Difficulty.HARD ? 100 : level.getDifficulty() == Difficulty.NORMAL ? 50 : 0; // Paper - Configurable chance of villager zombie infection - moved up from belows if
        if (this.random.nextDouble() * 100 < level.paperConfig().entities.behavior.zombieVillagerInfectionChance.or(fallbackChance) && entity instanceof Villager villager) { // Paper - Configurable chance of villager zombie infection
            if (false && level.getDifficulty() != Difficulty.HARD && this.random.nextBoolean()) { // Paper - Configurable chance of villager zombie infection - moved to "fallbackChance"
                return flag;
            }

            if (this.convertVillagerToZombieVillager(level, villager)) {
                flag = false;
            }
        }

        return flag;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    @Override
    public boolean canHoldItem(ItemStack stack) {
        return (!stack.is(Items.EGG) || !this.isBaby() || !this.isPassenger()) && super.canHoldItem(stack);
    }

    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
        return !stack.is(Items.GLOW_INK_SAC) && super.wantsToPickUp(level, stack);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        RandomSource random = level.getRandom();
        spawnGroupData = super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
        float specialMultiplier = difficulty.getSpecialMultiplier();
        if (spawnReason != EntitySpawnReason.CONVERSION) {
            this.setCanPickUpLoot(this.level().paperConfig().entities.behavior.mobsCanAlwaysPickUpLoot.zombies || random.nextFloat() < 0.55F * specialMultiplier); // Paper - Add world settings for mobs picking up loot
        }

        if (spawnGroupData == null) {
            spawnGroupData = new Zombie.ZombieGroupData(getSpawnAsBabyOdds(random), true);
        }

        if (spawnGroupData instanceof Zombie.ZombieGroupData zombieGroupData) {
            if (zombieGroupData.isBaby) {
                this.setBaby(true);
                if (zombieGroupData.canSpawnJockey) {
                    if (random.nextFloat() < 0.05) {
                        List<Chicken> entitiesOfClass = level.getEntitiesOfClass(
                            Chicken.class, this.getBoundingBox().inflate(5.0, 3.0, 5.0), EntitySelector.ENTITY_NOT_BEING_RIDDEN
                        );
                        if (!entitiesOfClass.isEmpty()) {
                            Chicken chicken = entitiesOfClass.get(0);
                            chicken.setChickenJockey(true);
                            this.startRiding(chicken);
                        }
                    } else if (random.nextFloat() < 0.05) {
                        Chicken chicken1 = EntityType.CHICKEN.create(this.level(), EntitySpawnReason.JOCKEY);
                        if (chicken1 != null) {
                            chicken1.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                            chicken1.finalizeSpawn(level, difficulty, EntitySpawnReason.JOCKEY, null);
                            chicken1.setChickenJockey(true);
                            this.startRiding(chicken1);
                            level.addFreshEntity(chicken1, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.MOUNT); // CraftBukkit
                        }
                    }
                }
            }

            this.setCanBreakDoors(random.nextFloat() < specialMultiplier * 0.1F);
            if (spawnReason != EntitySpawnReason.CONVERSION) {
                this.populateDefaultEquipmentSlots(random, difficulty);
                this.populateDefaultEquipmentEnchantments(level, random, difficulty);
            }
        }

        if (this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            if (net.minecraft.world.entity.ambient.Bat.isHalloweenSeason(level.getMinecraftWorld()) && this.random.nextFloat() < this.level().purpurConfig.chanceHeadHalloweenOnEntity) { // Purpur - Halloween options and optimizations
                this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(random.nextFloat() < 0.1F ? Blocks.JACK_O_LANTERN : Blocks.CARVED_PUMPKIN));
                this.armorDropChances[EquipmentSlot.HEAD.getIndex()] = 0.0F;
            }
        }

        this.handleAttributes(specialMultiplier);
        return spawnGroupData;
    }

    @VisibleForTesting
    public void setInWaterTime(int inWaterTime) {
        this.inWaterTime = inWaterTime;
    }

    @VisibleForTesting
    public void setConversionTime(int conversionTime) {
        this.conversionTime = conversionTime;
    }

    public static boolean getSpawnAsBabyOdds(RandomSource random) {
        return random.nextFloat() < 0.05F;
    }

    protected void handleAttributes(float difficulty) {
        this.randomizeReinforcementsChance();
        this.getAttribute(Attributes.KNOCKBACK_RESISTANCE)
            .addOrReplacePermanentModifier(
                new AttributeModifier(RANDOM_SPAWN_BONUS_ID, this.random.nextDouble() * 0.05F, AttributeModifier.Operation.ADD_VALUE)
            );
        double d = this.random.nextDouble() * 1.5 * difficulty;
        if (d > 1.0) {
            this.getAttribute(Attributes.FOLLOW_RANGE)
                .addOrReplacePermanentModifier(new AttributeModifier(ZOMBIE_RANDOM_SPAWN_BONUS_ID, d, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        if (this.random.nextFloat() < difficulty * 0.05F) {
            this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE)
                .addOrReplacePermanentModifier(
                    new AttributeModifier(LEADER_ZOMBIE_BONUS_ID, this.random.nextDouble() * 0.25 + 0.5, AttributeModifier.Operation.ADD_VALUE)
                );
            this.getAttribute(Attributes.MAX_HEALTH)
                .addOrReplacePermanentModifier(
                    new AttributeModifier(LEADER_ZOMBIE_BONUS_ID, this.random.nextDouble() * 3.0 + 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                );
            this.setCanBreakDoors(true);
        }
    }

    protected void randomizeReinforcementsChance() {
        this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(this.random.nextDouble() * 0.1F);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        if (damageSource.getEntity() instanceof Creeper creeper && creeper.canDropMobsSkull()) {
            ItemStack skull = this.getSkull();
            if (!skull.isEmpty()) {
                creeper.increaseDroppedSkulls();
                this.spawnAtLocation(level, skull);
            }
        }
    }

    protected ItemStack getSkull() {
        return new ItemStack(Items.ZOMBIE_HEAD);
    }

    class ZombieAttackTurtleEggGoal extends RemoveBlockGoal {
        ZombieAttackTurtleEggGoal(final PathfinderMob mob, final double speedModifier, final int verticalSearchRange) {
            super(Blocks.TURTLE_EGG, mob, speedModifier, verticalSearchRange);
        }

        @Override
        public void playDestroyProgressSound(LevelAccessor level, BlockPos pos) {
            level.playSound(null, pos, SoundEvents.ZOMBIE_DESTROY_EGG, SoundSource.HOSTILE, 0.5F, 0.9F + Zombie.this.random.nextFloat() * 0.2F);
        }

        @Override
        public void playBreakSound(Level level, BlockPos pos) {
            level.playSound(null, pos, SoundEvents.TURTLE_EGG_BREAK, SoundSource.BLOCKS, 0.7F, 0.9F + level.random.nextFloat() * 0.2F);
        }

        @Override
        public double acceptedDistance() {
            return 1.14;
        }
    }

    public static class ZombieGroupData implements SpawnGroupData {
        public final boolean isBaby;
        public final boolean canSpawnJockey;

        public ZombieGroupData(boolean isBaby, boolean canSpawnJockey) {
            this.isBaby = isBaby;
            this.canSpawnJockey = canSpawnJockey;
        }
    }
}
