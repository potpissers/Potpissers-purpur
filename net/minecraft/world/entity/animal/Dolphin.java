package net.minecraft.world.entity.animal;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreathAirGoal;
import net.minecraft.world.entity.ai.goal.DolphinJumpGoal;
import net.minecraft.world.entity.ai.goal.FollowBoatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.TryFindWaterGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;

public class Dolphin extends AgeableWaterCreature {
    private static final EntityDataAccessor<BlockPos> TREASURE_POS = SynchedEntityData.defineId(Dolphin.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> GOT_FISH = SynchedEntityData.defineId(Dolphin.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> MOISTNESS_LEVEL = SynchedEntityData.defineId(Dolphin.class, EntityDataSerializers.INT);
    static final TargetingConditions SWIM_WITH_PLAYER_TARGETING = TargetingConditions.forNonCombat().range(10.0).ignoreLineOfSight();
    public static final int TOTAL_AIR_SUPPLY = 4800;
    private static final int TOTAL_MOISTNESS_LEVEL = 2400;
    public static final Predicate<ItemEntity> ALLOWED_ITEMS = itemEntity -> !itemEntity.hasPickUpDelay() && itemEntity.isAlive() && itemEntity.isInWater();
    public static final float BABY_SCALE = 0.65F;
    private boolean isNaturallyAggressiveToPlayers; // Purpur - Dolphins naturally aggressive to players chance
    private int spitCooldown; // Purpur - Ridables

    public Dolphin(EntityType<? extends Dolphin> entityType, Level level) {
        super(entityType, level);
        // Purpur start - Ridables
        class DolphinMoveControl extends SmoothSwimmingMoveControl {
            private final org.purpurmc.purpur.controller.WaterMoveControllerWASD waterMoveControllerWASD;
            private final Dolphin dolphin;

            public DolphinMoveControl(Dolphin dolphin, int pitchChange, int yawChange, float speedInWater, float speedInAir, boolean buoyant) {
                super(dolphin, pitchChange, yawChange, speedInWater, speedInAir, buoyant);
                this.dolphin = dolphin;
                this.waterMoveControllerWASD = new org.purpurmc.purpur.controller.WaterMoveControllerWASD(dolphin);
            }

            @Override
            public void tick() {
                if (dolphin.getRider() != null && dolphin.isControllable()) {
                    purpurTick(dolphin.getRider());
                } else {
                    super.tick();
                }
            }

            public void purpurTick(Player rider) {
                if (dolphin.getAirSupply() < 150) {
                    // if drowning override player WASD controls to find air
                    super.tick();
                } else {
                    waterMoveControllerWASD.purpurTick(rider);
                    dolphin.setDeltaMovement(dolphin.getDeltaMovement().add(0.0D, 0.005D, 0.0D));
                }
            }
        };
        this.moveControl = new DolphinMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        // Purpur end - Ridables
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
        this.setCanPickUpLoot(true);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.dolphinRidable;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.dolphinControllable;
    }

    @Override
    public boolean onSpacebar() {
        if (spitCooldown == 0 && getRider() != null) {
            spitCooldown = level().purpurConfig.dolphinSpitCooldown;

            org.bukkit.craftbukkit.entity.CraftPlayer player = (org.bukkit.craftbukkit.entity.CraftPlayer) getRider().getBukkitEntity();
            if (!player.hasPermission("allow.special.dolphin")) {
                return false;
            }

            org.bukkit.Location loc = player.getEyeLocation();
            loc.setPitch(loc.getPitch() - 10);
            org.bukkit.util.Vector target = loc.getDirection().normalize().multiply(10).add(loc.toVector());

            org.purpurmc.purpur.entity.projectile.DolphinSpit spit = new org.purpurmc.purpur.entity.projectile.DolphinSpit(level(), this);
            spit.shoot(target.getX() - getX(), target.getY() - getY(), target.getZ() - getZ(), level().purpurConfig.dolphinSpitSpeed, 5.0F);

            level().addFreshEntity(spit);
            playSound(SoundEvents.DOLPHIN_ATTACK, 1.0F, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.2F);
            return true;
        }
        return false;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.dolphinMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.dolphinScale);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.dolphinTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.dolphinAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        this.setAirSupply(this.getMaxAirSupply());
        this.setXRot(0.0F);
        SpawnGroupData spawnGroupData1 = Objects.requireNonNullElseGet(spawnGroupData, () -> new AgeableMob.AgeableMobGroupData(0.1F));
        this.isNaturallyAggressiveToPlayers = level.getLevel().purpurConfig.dolphinNaturallyAggressiveToPlayersChance > 0.0D && random.nextDouble() <= level.getLevel().purpurConfig.dolphinNaturallyAggressiveToPlayersChance; // Purpur - Dolphins naturally aggressive to players chance
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData1);
    }

    @Nullable
    @Override
    public Dolphin getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return EntityType.DOLPHIN.create(level, EntitySpawnReason.BREEDING);
    }

    // CraftBukkit start - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    @Override
    public int getDefaultMaxAirSupply() {
        return TOTAL_AIR_SUPPLY;
    }
    // CraftBukkit end

    @Override
    public float getAgeScale() {
        return this.isBaby() ? 0.65F : 1.0F;
    }

    @Override
    protected void handleAirSupply(int airSupply) {
    }

    public void setTreasurePos(BlockPos pos) {
        this.entityData.set(TREASURE_POS, pos);
    }

    public BlockPos getTreasurePos() {
        return this.entityData.get(TREASURE_POS);
    }

    public boolean gotFish() {
        return this.entityData.get(GOT_FISH);
    }

    public void setGotFish(boolean gotFish) {
        this.entityData.set(GOT_FISH, gotFish);
    }

    public int getMoistnessLevel() {
        return this.entityData.get(MOISTNESS_LEVEL);
    }

    public void setMoisntessLevel(int moistnessLevel) {
        this.entityData.set(MOISTNESS_LEVEL, moistnessLevel);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TREASURE_POS, BlockPos.ZERO);
        builder.define(GOT_FISH, false);
        builder.define(MOISTNESS_LEVEL, 2400);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("TreasurePosX", this.getTreasurePos().getX());
        compound.putInt("TreasurePosY", this.getTreasurePos().getY());
        compound.putInt("TreasurePosZ", this.getTreasurePos().getZ());
        compound.putBoolean("GotFish", this.gotFish());
        compound.putInt("Moistness", this.getMoistnessLevel());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        int _int = compound.getInt("TreasurePosX");
        int _int1 = compound.getInt("TreasurePosY");
        int _int2 = compound.getInt("TreasurePosZ");
        this.setTreasurePos(new BlockPos(_int, _int1, _int2));
        super.readAdditionalSaveData(compound);
        this.setGotFish(compound.getBoolean("GotFish"));
        this.setMoisntessLevel(compound.getInt("Moistness"));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new BreathAirGoal(this));
        this.goalSelector.addGoal(0, new TryFindWaterGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2000000476837158D, true)); // Purpur - Dolphins naturally aggressive to players chance
        this.goalSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.goalSelector.addGoal(1, new Dolphin.DolphinSwimToTreasureGoal(this));
        this.goalSelector.addGoal(2, new Dolphin.DolphinSwimWithPlayerGoal(this, 4.0));
        this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0, 10));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(5, new DolphinJumpGoal(this, 10));
        //this.goalSelector.addGoal(6, new MeleeAttackGoal(this, 1.2F, true)); // Purpur - moved up - Dolphins naturally aggressive to players chance
        this.goalSelector.addGoal(8, new Dolphin.PlayWithItemsGoal());
        this.goalSelector.addGoal(8, new FollowBoatGoal(this));
        this.goalSelector.addGoal(9, new AvoidEntityGoal<>(this, Guardian.class, 8.0F, 1.0, 1.0));
        this.targetSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Guardian.class).setAlertOthers());
        this.targetSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (ignored, ignored2) -> isNaturallyAggressiveToPlayers)); // Purpur - Dolphins naturally aggressive to players chance
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 1.2F).add(Attributes.ATTACK_DAMAGE, 3.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    public void playAttackSound() {
        this.playSound(SoundEvents.DOLPHIN_ATTACK, 1.0F, 1.0F);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isBaby() && super.canAttack(target);
    }

    @Override
    public int getMaxAirSupply() {
        return this.maxAirTicks; // CraftBukkit - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    }

    @Override
    protected int increaseAirSupply(int currentAir) {
        return this.getMaxAirSupply();
    }

    @Override
    public int getMaxHeadXRot() {
        return 1;
    }

    @Override
    public int getMaxHeadYRot() {
        return 1;
    }

    @Override
    protected boolean canRide(Entity entity) {
        return boardingCooldown <= 0; // Purpur - make dolphin honor ride cooldown like all other non-boss mobs;
    }

    @Override
    protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND && this.canPickUpLoot();
    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity entity) {
        if (this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
            ItemStack item = entity.getItem();
            if (this.canHoldItem(item)) {
                // CraftBukkit start - call EntityPickupItemEvent
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPickupItemEvent(this, entity, 0, false).isCancelled()) return;
                item = entity.getItem(); // CraftBukkit- update ItemStack from event
                // CraftBukkit end
                this.onItemPickup(entity);
                this.setItemSlot(EquipmentSlot.MAINHAND, item);
                this.setGuaranteedDrop(EquipmentSlot.MAINHAND);
                this.take(entity, item.getCount());
                entity.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Purpur start - Ridables
        if (spitCooldown > 0) {
            spitCooldown--;
        }
        // Purpur end - Ridables
        if (this.isNoAi()) {
            this.setAirSupply(this.getMaxAirSupply());
        } else {
            if (this.isInWaterRainOrBubble()) {
                this.setMoisntessLevel(2400);
            } else {
                this.setMoisntessLevel(this.getMoistnessLevel() - 1);
                if (this.getMoistnessLevel() <= 0) {
                    this.hurt(this.damageSources().dryOut(), 1.0F);
                }

                if (this.onGround()) {
                    this.setDeltaMovement(
                        this.getDeltaMovement().add((this.random.nextFloat() * 2.0F - 1.0F) * 0.2F, 0.5, (this.random.nextFloat() * 2.0F - 1.0F) * 0.2F)
                    );
                    this.setYRot(this.random.nextFloat() * 360.0F);
                    this.setOnGround(false);
                    this.hasImpulse = true;
                }
            }

            if (this.level().isClientSide && this.isInWater() && this.getDeltaMovement().lengthSqr() > 0.03) {
                Vec3 viewVector = this.getViewVector(0.0F);
                float f = Mth.cos(this.getYRot() * (float) (Math.PI / 180.0)) * 0.3F;
                float f1 = Mth.sin(this.getYRot() * (float) (Math.PI / 180.0)) * 0.3F;
                float f2 = 1.2F - this.random.nextFloat() * 0.7F;

                for (int i = 0; i < 2; i++) {
                    this.level()
                        .addParticle(
                            ParticleTypes.DOLPHIN,
                            this.getX() - viewVector.x * f2 + f,
                            this.getY() - viewVector.y,
                            this.getZ() - viewVector.z * f2 + f1,
                            0.0,
                            0.0,
                            0.0
                        );
                    this.level()
                        .addParticle(
                            ParticleTypes.DOLPHIN,
                            this.getX() - viewVector.x * f2 - f,
                            this.getY() - viewVector.y,
                            this.getZ() - viewVector.z * f2 - f1,
                            0.0,
                            0.0,
                            0.0
                        );
                }
            }
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 38) {
            this.addParticlesAroundSelf(ParticleTypes.HAPPY_VILLAGER);
        } else {
            super.handleEntityEvent(id);
        }
    }

    private void addParticlesAroundSelf(ParticleOptions particleOption) {
        for (int i = 0; i < 7; i++) {
            double d = this.random.nextGaussian() * 0.01;
            double d1 = this.random.nextGaussian() * 0.01;
            double d2 = this.random.nextGaussian() * 0.01;
            this.level().addParticle(particleOption, this.getRandomX(1.0), this.getRandomY() + 0.2, this.getRandomZ(1.0), d, d1, d2);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (!itemInHand.isEmpty() && itemInHand.is(ItemTags.FISHES)) {
            if (!this.level().isClientSide) {
                this.playSound(SoundEvents.DOLPHIN_EAT, 1.0F, 1.0F);
            }

            if (this.isBaby()) {
                itemInHand.consume(1, player);
                this.ageUp(getSpeedUpSecondsWhenFeeding(-this.age), true);
            } else {
                this.setGotFish(true);
                itemInHand.consume(1, player);
            }

            return InteractionResult.SUCCESS;
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.DOLPHIN_HURT;
    }

    @Nullable
    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.DOLPHIN_DEATH;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return this.isInWater() ? SoundEvents.DOLPHIN_AMBIENT_WATER : SoundEvents.DOLPHIN_AMBIENT;
    }

    @Override
    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.DOLPHIN_SPLASH;
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.DOLPHIN_SWIM;
    }

    protected boolean closeToNextPos() {
        BlockPos targetPos = this.getNavigation().getTargetPos();
        return targetPos != null && targetPos.closerToCenterThan(this.position(), 12.0);
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isControlledByLocalInstance() && this.isInWater()) {
            this.moveRelative(this.getSpeed(), travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
            if (this.getTarget() == null) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.005, 0.0));
            }
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }

    static class DolphinSwimToTreasureGoal extends Goal {
        private final Dolphin dolphin;
        private boolean stuck;

        DolphinSwimToTreasureGoal(Dolphin dolphin) {
            this.dolphin = dolphin;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }

        @Override
        public boolean canUse() {
            if (this.dolphin.level().purpurConfig.dolphinDisableTreasureSearching) return false; // Purpur - Add option to disable dolphin treasure searching
            return this.dolphin.gotFish() && this.dolphin.getAirSupply() >= 100;
        }

        @Override
        public boolean canContinueToUse() {
            BlockPos treasurePos = this.dolphin.getTreasurePos();
            return !BlockPos.containing(treasurePos.getX(), this.dolphin.getY(), treasurePos.getZ()).closerToCenterThan(this.dolphin.position(), 4.0)
                && !this.stuck
                && this.dolphin.getAirSupply() >= 100;
        }

        @Override
        public void start() {
            if (this.dolphin.level() instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel)this.dolphin.level();
                this.stuck = false;
                this.dolphin.getNavigation().stop();
                BlockPos blockPos = this.dolphin.blockPosition();
                BlockPos blockPos1 = serverLevel.findNearestMapStructure(StructureTags.DOLPHIN_LOCATED, blockPos, 50, false);
                if (blockPos1 != null) {
                    this.dolphin.setTreasurePos(blockPos1);
                    serverLevel.broadcastEntityEvent(this.dolphin, (byte)38);
                } else {
                    this.stuck = true;
                }
            }
        }

        @Override
        public void stop() {
            BlockPos treasurePos = this.dolphin.getTreasurePos();
            if (BlockPos.containing(treasurePos.getX(), this.dolphin.getY(), treasurePos.getZ()).closerToCenterThan(this.dolphin.position(), 4.0) || this.stuck
                )
             {
                this.dolphin.setGotFish(false);
            }
        }

        @Override
        public void tick() {
            Level level = this.dolphin.level();
            if (this.dolphin.closeToNextPos() || this.dolphin.getNavigation().isDone()) {
                Vec3 vec3 = Vec3.atCenterOf(this.dolphin.getTreasurePos());
                Vec3 posTowards = DefaultRandomPos.getPosTowards(this.dolphin, 16, 1, vec3, (float) (Math.PI / 8));
                if (posTowards == null) {
                    posTowards = DefaultRandomPos.getPosTowards(this.dolphin, 8, 4, vec3, (float) (Math.PI / 2));
                }

                if (posTowards != null) {
                    BlockPos blockPos = BlockPos.containing(posTowards);
                    if (!level.getFluidState(blockPos).is(FluidTags.WATER) || !level.getBlockState(blockPos).isPathfindable(PathComputationType.WATER)) {
                        posTowards = DefaultRandomPos.getPosTowards(this.dolphin, 8, 5, vec3, (float) (Math.PI / 2));
                    }
                }

                if (posTowards == null) {
                    this.stuck = true;
                    return;
                }

                this.dolphin
                    .getLookControl()
                    .setLookAt(posTowards.x, posTowards.y, posTowards.z, this.dolphin.getMaxHeadYRot() + 20, this.dolphin.getMaxHeadXRot());
                this.dolphin.getNavigation().moveTo(posTowards.x, posTowards.y, posTowards.z, 1.3);
                if (level.random.nextInt(this.adjustedTickDelay(80)) == 0) {
                    level.broadcastEntityEvent(this.dolphin, (byte)38);
                }
            }
        }
    }

    static class DolphinSwimWithPlayerGoal extends Goal {
        private final Dolphin dolphin;
        private final double speedModifier;
        @Nullable
        private Player player;

        DolphinSwimWithPlayerGoal(Dolphin dolphin, double speedModifier) {
            this.dolphin = dolphin;
            this.speedModifier = speedModifier;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            this.player = getServerLevel(this.dolphin).getNearestPlayer(Dolphin.SWIM_WITH_PLAYER_TARGETING, this.dolphin);
            return this.player != null && this.player.isSwimming() && this.dolphin.getTarget() != this.player;
        }

        @Override
        public boolean canContinueToUse() {
            return this.player != null && this.player.isSwimming() && this.dolphin.distanceToSqr(this.player) < 256.0;
        }

        @Override
        public void start() {
            this.player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 100), this.dolphin, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.DOLPHIN); // CraftBukkit
        }

        @Override
        public void stop() {
            this.player = null;
            this.dolphin.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.dolphin.getLookControl().setLookAt(this.player, this.dolphin.getMaxHeadYRot() + 20, this.dolphin.getMaxHeadXRot());
            if (this.dolphin.distanceToSqr(this.player) < 6.25) {
                this.dolphin.getNavigation().stop();
            } else {
                this.dolphin.getNavigation().moveTo(this.player, this.speedModifier);
            }

            if (this.player.isSwimming() && this.player.level().random.nextInt(6) == 0) {
                this.player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 100), this.dolphin, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.DOLPHIN); // CraftBukkit
            }
        }
    }

    class PlayWithItemsGoal extends Goal {
        private int cooldown;

        @Override
        public boolean canUse() {
            if (this.cooldown > Dolphin.this.tickCount) {
                return false;
            } else {
                List<ItemEntity> entitiesOfClass = Dolphin.this.level()
                    .getEntitiesOfClass(ItemEntity.class, Dolphin.this.getBoundingBox().inflate(8.0, 8.0, 8.0), Dolphin.ALLOWED_ITEMS);
                return !entitiesOfClass.isEmpty() || !Dolphin.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
            }
        }

        @Override
        public void start() {
            List<ItemEntity> entitiesOfClass = Dolphin.this.level()
                .getEntitiesOfClass(ItemEntity.class, Dolphin.this.getBoundingBox().inflate(8.0, 8.0, 8.0), Dolphin.ALLOWED_ITEMS);
            if (!entitiesOfClass.isEmpty()) {
                Dolphin.this.getNavigation().moveTo(entitiesOfClass.get(0), 1.2F);
                Dolphin.this.playSound(SoundEvents.DOLPHIN_PLAY, 1.0F, 1.0F);
            }

            this.cooldown = 0;
        }

        @Override
        public void stop() {
            ItemStack itemBySlot = Dolphin.this.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!itemBySlot.isEmpty()) {
                this.drop(itemBySlot);
                Dolphin.this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                this.cooldown = Dolphin.this.tickCount + Dolphin.this.random.nextInt(100);
            }
        }

        @Override
        public void tick() {
            List<ItemEntity> entitiesOfClass = Dolphin.this.level()
                .getEntitiesOfClass(ItemEntity.class, Dolphin.this.getBoundingBox().inflate(8.0, 8.0, 8.0), Dolphin.ALLOWED_ITEMS);
            ItemStack itemBySlot = Dolphin.this.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!itemBySlot.isEmpty()) {
                this.drop(itemBySlot);
                Dolphin.this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            } else if (!entitiesOfClass.isEmpty()) {
                Dolphin.this.getNavigation().moveTo(entitiesOfClass.get(0), 1.2F);
            }
        }

        private void drop(ItemStack stack) {
            if (!stack.isEmpty()) {
                double d = Dolphin.this.getEyeY() - 0.3F;
                ItemEntity itemEntity = new ItemEntity(Dolphin.this.level(), Dolphin.this.getX(), d, Dolphin.this.getZ(), stack);
                itemEntity.setPickUpDelay(40);
                itemEntity.setThrower(Dolphin.this);
                float f = 0.3F;
                float f1 = Dolphin.this.random.nextFloat() * (float) (Math.PI * 2);
                float f2 = 0.02F * Dolphin.this.random.nextFloat();
                itemEntity.setDeltaMovement(
                    0.3F * -Mth.sin(Dolphin.this.getYRot() * (float) (Math.PI / 180.0)) * Mth.cos(Dolphin.this.getXRot() * (float) (Math.PI / 180.0))
                        + Mth.cos(f1) * f2,
                    0.3F * Mth.sin(Dolphin.this.getXRot() * (float) (Math.PI / 180.0)) * 1.5F,
                    0.3F * Mth.cos(Dolphin.this.getYRot() * (float) (Math.PI / 180.0)) * Mth.cos(Dolphin.this.getXRot() * (float) (Math.PI / 180.0))
                        + Mth.sin(f1) * f2
                );
                Dolphin.this.spawnAtLocation(getServerLevel(Dolphin.this), itemEntity); // Paper - Call EntityDropItemEvent
            }
        }
    }
}
