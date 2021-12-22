package net.minecraft.world.entity.monster;

import com.google.common.annotations.VisibleForTesting;
import java.util.EnumSet;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.ConversionType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

public class Slime extends Mob implements Enemy {
    private static final EntityDataAccessor<Integer> ID_SIZE = SynchedEntityData.defineId(Slime.class, EntityDataSerializers.INT);
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 127;
    public static final int MAX_NATURAL_SIZE = 4;
    public float targetSquish;
    public float squish;
    public float oSquish;
    private boolean wasOnGround;
    private boolean canWander = true; // Paper - Slime pathfinder events
    protected boolean actualJump; // Purpur - Ridables

    public Slime(EntityType<? extends Slime> entityType, Level level) {
        super(entityType, level);
        this.fixupDimensions();
        this.moveControl = new Slime.SlimeMoveControl(this);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.slimeRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.slimeRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.slimeControllable;
    }

    @Override
    public float getJumpPower() {
        float height = super.getJumpPower();
        return getRider() != null && this.isControllable() && actualJump ? height * 1.5F : height;
    }

    @Override
    public boolean onSpacebar() {
        if (onGround && getRider() != null && this.isControllable()) {
            actualJump = true;
            if (getRider().getForwardMot() == 0 || getRider().getStrafeMot() == 0) {
                jumpFromGround(); // jump() here if not moving
            }
        }
        return true; // do not jump() in wasd controller, let vanilla controller handle
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    protected String getMaxHealthEquation() {
        return level().purpurConfig.slimeMaxHealth;
    }

    protected String getAttackDamageEquation() {
        return level().purpurConfig.slimeAttackDamage;
    }

    protected java.util.Map<Integer, Double> getMaxHealthCache() {
        return level().purpurConfig.slimeMaxHealthCache;
    }

    protected java.util.Map<Integer, Double> getAttackDamageCache() {
        return level().purpurConfig.slimeAttackDamageCache;
    }

    protected double getFromCache(java.util.function.Supplier<String> equation, java.util.function.Supplier<java.util.Map<Integer, Double>> cache, java.util.function.Supplier<Double> defaultValue) {
        int size = getSize();
        Double value = cache.get().get(size);
        if (value == null) {
            try {
                value = ((Number) scriptEngine.eval("let size = " + size + "; " + equation.get())).doubleValue();
            } catch (javax.script.ScriptException e) {
                e.printStackTrace();
                value = defaultValue.get();
            }
            cache.get().put(size, value);
        }
        return value;
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.slimeTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.slimeAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.goalSelector.addGoal(1, new Slime.SlimeFloatGoal(this));
        this.goalSelector.addGoal(2, new Slime.SlimeAttackGoal(this));
        this.goalSelector.addGoal(3, new Slime.SlimeRandomDirectionGoal(this));
        this.goalSelector.addGoal(5, new Slime.SlimeKeepOnJumpingGoal(this));
        this.targetSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.targetSelector
            .addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (entity, level) -> Math.abs(entity.getY() - this.getY()) <= 4.0));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ID_SIZE, 1);
    }

    @VisibleForTesting
    public void setSize(int size, boolean resetHealth) {
        int i = Mth.clamp(size, 1, 127);
        this.entityData.set(ID_SIZE, i);
        this.reapplyPosition();
        this.refreshDimensions();
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(getFromCache(this::getMaxHealthEquation, this::getMaxHealthCache, () -> (double) (size * size))); // Purpur - Configurable entity base attributes
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.2F + 0.1F * i);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(getFromCache(this::getAttackDamageEquation, this::getAttackDamageCache, () -> (double) i)); // Purpur - Configurable entity base attributes
        if (resetHealth) {
            this.setHealth(this.getMaxHealth());
        }

        this.xpReward = i;
    }

    public int getSize() {
        return this.entityData.get(ID_SIZE);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Size", this.getSize() - 1);
        compound.putBoolean("wasOnGround", this.wasOnGround);
        compound.putBoolean("Paper.canWander", this.canWander); // Paper
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        this.setSize(compound.getInt("Size") + 1, false);
        super.readAdditionalSaveData(compound);
        this.wasOnGround = compound.getBoolean("wasOnGround");
        // Paper start
        if (compound.contains("Paper.canWander")) {
            this.canWander = compound.getBoolean("Paper.canWander");
        }
        // Paper end
    }

    public boolean isTiny() {
        return this.getSize() <= 1;
    }

    protected ParticleOptions getParticleType() {
        return ParticleTypes.ITEM_SLIME;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return this.getSize() > 0;
    }

    @Override
    public void tick() {
        this.oSquish = this.squish;
        this.squish = this.squish + (this.targetSquish - this.squish) * 0.5F;
        super.tick();
        if (this.onGround() && !this.wasOnGround) {
            float f = this.getDimensions(this.getPose()).width() * 2.0F;
            float f1 = f / 2.0F;

            for (int i = 0; i < f * 16.0F; i++) {
                float f2 = this.random.nextFloat() * (float) (Math.PI * 2);
                float f3 = this.random.nextFloat() * 0.5F + 0.5F;
                float f4 = Mth.sin(f2) * f1 * f3;
                float f5 = Mth.cos(f2) * f1 * f3;
                this.level().addParticle(this.getParticleType(), this.getX() + f4, this.getY(), this.getZ() + f5, 0.0, 0.0, 0.0);
            }

            this.playSound(this.getSquishSound(), this.getSoundVolume(), ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) / 0.8F);
            this.targetSquish = -0.5F;
        } else if (!this.onGround() && this.wasOnGround) {
            this.targetSquish = 1.0F;
        }

        this.wasOnGround = this.onGround();
        this.decreaseSquish();
    }

    protected void decreaseSquish() {
        this.targetSquish *= 0.6F;
    }

    protected int getJumpDelay() {
        return this.random.nextInt(20) + 10;
    }

    @Override
    public void refreshDimensions() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        super.refreshDimensions();
        this.setPos(x, y, z);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (ID_SIZE.equals(key)) {
            this.refreshDimensions();
            this.setYRot(this.yHeadRot);
            this.yBodyRot = this.yHeadRot;
            if (this.isInWater() && this.random.nextInt(20) == 0) {
                this.doWaterSplashEffect();
            }
        }

        super.onSyncedDataUpdated(key);
    }

    @Override
    public EntityType<? extends Slime> getType() {
        return (EntityType<? extends Slime>)super.getType();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        // CraftBukkit start - add Bukkit remove cause
        this.remove(reason, null);
    }

    @Override
    public void remove(Entity.RemovalReason reason, org.bukkit.event.entity.EntityRemoveEvent.Cause eventCause) {
        // CraftBukkit end
        int size = this.getSize();
        if (!this.level().isClientSide && size > 1 && this.isDeadOrDying()) {
            float width = this.getDimensions(this.getPose()).width();
            float f = width / 2.0F;
            int i = size / 2;
            int i1 = 2 + this.random.nextInt(3);
            PlayerTeam team = this.getTeam();
            // CraftBukkit start
            org.bukkit.event.entity.SlimeSplitEvent event = new org.bukkit.event.entity.SlimeSplitEvent((org.bukkit.entity.Slime) this.getBukkitEntity(), i1);
            this.level().getCraftServer().getPluginManager().callEvent(event);

            if (!event.isCancelled() && event.getCount() > 0) {
                i1 = event.getCount();
            } else {
                super.remove(reason, eventCause); // CraftBukkit - add Bukkit remove cause
                return;
            }
            java.util.List<net.minecraft.world.entity.LivingEntity> slimes = new java.util.ArrayList<>(i1);
            // CraftBukkit end

            for (int i2 = 0; i2 < i1; i2++) {
                float f1 = (i2 % 2 - 0.5F) * f;
                float f2 = (i2 / 2 - 0.5F) * f;
                Slime converted = this.convertTo(this.getType(), new ConversionParams(ConversionType.SPLIT_ON_DEATH, false, false, team), EntitySpawnReason.TRIGGERED, (mob) -> { // CraftBukkit
                    mob.aware = this.aware; // Paper - Fix nerfed slime when splitting
                    mob.setSize(i, true);
                    mob.moveTo(this.getX() + f1, this.getY() + 0.5, this.getZ() + f2, this.random.nextFloat() * 360.0F, 0.0F);
                // CraftBukkit start
                }, null, null);
                if (converted != null) {
                    slimes.add(converted);
                }
                // CraftBukkit end
            }
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTransformEvent(this, slimes, org.bukkit.event.entity.EntityTransformEvent.TransformReason.SPLIT).isCancelled()) {
                super.remove(reason, eventCause); // CraftBukkit - add Bukkit remove cause
                return;
            }
            for (LivingEntity living : slimes) {
                this.level().addFreshEntity(living, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SLIME_SPLIT); // CraftBukkit - SpawnReason
            }
            // CraftBukkit end
        }

        super.remove(reason, eventCause); // CraftBukkit - add Bukkit remove cause
    }

    @Override
    public void push(Entity entity) {
        super.push(entity);
        if (entity instanceof IronGolem && this.isDealsDamage()) {
            this.dealDamage((LivingEntity)entity);
        }
    }

    @Override
    public void playerTouch(Player entity) {
        if (this.isDealsDamage()) {
            this.dealDamage(entity);
        }
    }

    protected void dealDamage(LivingEntity livingEntity) {
        if (this.level() instanceof ServerLevel serverLevel
            && this.isAlive()
            && this.isWithinMeleeAttackRange(livingEntity)
            && this.hasLineOfSight(livingEntity)) {
            DamageSource damageSource = this.damageSources().mobAttack(this);
            if (livingEntity.hurtServer(serverLevel, damageSource, this.getAttackDamage())) {
                this.playSound(SoundEvents.SLIME_ATTACK, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                EnchantmentHelper.doPostAttackEffects(serverLevel, livingEntity, damageSource);
            }
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
        return new Vec3(0.0, dimensions.height() - 0.015625 * this.getSize() * partialTick, 0.0);
    }

    protected boolean isDealsDamage() {
        return !this.isTiny() && this.isEffectiveAi();
    }

    protected float getAttackDamage() {
        return (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return this.isTiny() ? SoundEvents.SLIME_HURT_SMALL : SoundEvents.SLIME_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return this.isTiny() ? SoundEvents.SLIME_DEATH_SMALL : SoundEvents.SLIME_DEATH;
    }

    protected SoundEvent getSquishSound() {
        return this.isTiny() ? SoundEvents.SLIME_SQUISH_SMALL : SoundEvents.SLIME_SQUISH;
    }

    public static boolean checkSlimeSpawnRules(
        EntityType<Slime> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        if (level.getDifficulty() != Difficulty.PEACEFUL) {
            if (EntitySpawnReason.isSpawner(spawnReason)) {
                return checkMobSpawnRules(entityType, level, spawnReason, pos, random);
            }

            // Paper start - Replace rules for Height in Swamp Biome
            final double maxHeightSwamp = level.getMinecraftWorld().paperConfig().entities.spawning.slimeSpawnHeight.surfaceBiome.maximum;
            final double minHeightSwamp = level.getMinecraftWorld().paperConfig().entities.spawning.slimeSpawnHeight.surfaceBiome.minimum;
            // Paper end
            if (level.getBiome(pos).is(BiomeTags.ALLOWS_SURFACE_SLIME_SPAWNS)
                && pos.getY() > minHeightSwamp // Paper - Replace rules for Height in Swamp Biome
                && pos.getY() < maxHeightSwamp // Paper - Replace rules for Height in Swamp Biome
                && random.nextFloat() < 0.5F
                && random.nextFloat() < level.getMoonBrightness()
                && level.getMaxLocalRawBrightness(pos) <= random.nextInt(8)) {
                return checkMobSpawnRules(entityType, level, spawnReason, pos, random);
            }

            if (!(level instanceof WorldGenLevel)) {
                return false;
            }

            ChunkPos chunkPos = new ChunkPos(pos);
            boolean flag = level.getMinecraftWorld().paperConfig().entities.spawning.allChunksAreSlimeChunks || WorldgenRandom.seedSlimeChunk(chunkPos.x, chunkPos.z, ((WorldGenLevel) level).getSeed(), level.getMinecraftWorld().spigotConfig.slimeSeed).nextInt(10) == 0; // Spigot // Paper
                // Paper start - Replace rules for Height in Slime Chunks
                final double maxHeightSlimeChunk = level.getMinecraftWorld().paperConfig().entities.spawning.slimeSpawnHeight.slimeChunk.maximum;
                if (random.nextInt(10) == 0 && flag && pos.getY() < maxHeightSlimeChunk) {
                // Paper end - Replace rules for Height in Slime Chunks
                return checkMobSpawnRules(entityType, level, spawnReason, pos, random);
            }
        }

        return false;
    }

    @Override
    public float getSoundVolume() {
        return 0.4F * this.getSize();
    }

    @Override
    public int getMaxHeadXRot() {
        return 0;
    }

    protected boolean doPlayJumpSound() {
        return this.getSize() > 0;
    }

    @Override
    public void jumpFromGround() {
        Vec3 deltaMovement = this.getDeltaMovement();
        this.setDeltaMovement(deltaMovement.x, this.getJumpPower(), deltaMovement.z);
        this.hasImpulse = true;
        this.actualJump = false; // Purpur - Ridables
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        RandomSource random = level.getRandom();
        int randomInt = random.nextInt(3);
        if (randomInt < 2 && random.nextFloat() < 0.5F * difficulty.getSpecialMultiplier()) {
            randomInt++;
        }

        int i = 1 << randomInt;
        this.setSize(i, true);
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    float getSoundPitch() {
        float f = this.isTiny() ? 1.4F : 0.8F;
        return ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) * f;
    }

    protected SoundEvent getJumpSound() {
        return this.isTiny() ? SoundEvents.SLIME_JUMP_SMALL : SoundEvents.SLIME_JUMP;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return super.getDefaultDimensions(pose).scale(this.getSize());
    }

    // Paper start - Slime pathfinder events
    public boolean canWander() {
        return this.canWander;
    }

    public void setWander(boolean canWander) {
        this.canWander = canWander;
    }
    // Paper end - Slime pathfinder events

    static class SlimeAttackGoal extends Goal {
        private final Slime slime;
        private int growTiredTimer;

        public SlimeAttackGoal(Slime slime) {
            this.slime = slime;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.slime.getTarget();

            // Paper start - Slime pathfinder events
            if (target == null || !target.isAlive()) {
                return false;
            }
            if (!this.slime.canAttack(target)) {
                return false;
            }
            return this.slime.getMoveControl() instanceof Slime.SlimeMoveControl && this.slime.canWander && new com.destroystokyo.paper.event.entity.SlimeTargetLivingEntityEvent((org.bukkit.entity.Slime) this.slime.getBukkitEntity(), (org.bukkit.entity.LivingEntity) target.getBukkitEntity()).callEvent();
            // Paper end - Slime pathfinder events
        }

        @Override
        public void start() {
            this.growTiredTimer = reducedTickDelay(300);
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = this.slime.getTarget();

            // Paper start - Slime pathfinder events
            if (target == null || !target.isAlive()) {
                return false;
            }
            if (!this.slime.canAttack(target)) {
                return false;
            }
            return --this.growTiredTimer > 0 && this.slime.canWander && new com.destroystokyo.paper.event.entity.SlimeTargetLivingEntityEvent((org.bukkit.entity.Slime) this.slime.getBukkitEntity(), (org.bukkit.entity.LivingEntity) target.getBukkitEntity()).callEvent();
            // Paper end - Slime pathfinder events
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.slime.getTarget();
            if (target != null) {
                this.slime.lookAt(target, 10.0F, 10.0F);
            }

            if (this.slime.getMoveControl() instanceof Slime.SlimeMoveControl slimeMoveControl) {
                slimeMoveControl.setDirection(this.slime.getYRot(), this.slime.isDealsDamage());
            }
        }

        // Paper start - Slime pathfinder events; clear timer and target when goal resets
        public void stop() {
            this.growTiredTimer = 0;
            this.slime.setTarget(null);
        }
        // Paper end - Slime pathfinder events
    }

    static class SlimeFloatGoal extends Goal {
        private final Slime slime;

        public SlimeFloatGoal(Slime slime) {
            this.slime = slime;
            this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
            slime.getNavigation().setCanFloat(true);
        }

        @Override
        public boolean canUse() {
            return (this.slime.isInWater() || this.slime.isInLava()) && this.slime.getMoveControl() instanceof Slime.SlimeMoveControl && this.slime.canWander && new com.destroystokyo.paper.event.entity.SlimeSwimEvent((org.bukkit.entity.Slime) this.slime.getBukkitEntity()).callEvent(); // Paper - Slime pathfinder events
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.slime.getRandom().nextFloat() < 0.8F) {
                this.slime.getJumpControl().jump();
            }

            if (this.slime.getMoveControl() instanceof Slime.SlimeMoveControl slimeMoveControl) {
                slimeMoveControl.setWantedMovement(1.2);
            }
        }
    }

    static class SlimeKeepOnJumpingGoal extends Goal {
        private final Slime slime;

        public SlimeKeepOnJumpingGoal(Slime slime) {
            this.slime = slime;
            this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !this.slime.isPassenger() && this.slime.canWander && new com.destroystokyo.paper.event.entity.SlimeWanderEvent((org.bukkit.entity.Slime) this.slime.getBukkitEntity()).callEvent(); // Paper - Slime pathfinder events
        }

        @Override
        public void tick() {
            if (this.slime.getMoveControl() instanceof Slime.SlimeMoveControl slimeMoveControl) {
                slimeMoveControl.setWantedMovement(1.0);
            }
        }
    }

    static class SlimeMoveControl extends org.purpurmc.purpur.controller.MoveControllerWASD { // Purpur - Ridables
        private float yRot;
        private int jumpDelay;
        private final Slime slime;
        private boolean isAggressive;

        public SlimeMoveControl(Slime mob) {
            super(mob);
            this.slime = mob;
            this.yRot = 180.0F * mob.getYRot() / (float) Math.PI;
        }

        public void setDirection(float yRot, boolean aggressive) {
            this.yRot = yRot;
            this.isAggressive = aggressive;
        }

        public void setWantedMovement(double speed) {
            this.setSpeedModifier(speed); // Purpur - Ridables
            this.operation = MoveControl.Operation.MOVE_TO;
        }

        @Override
        public void tick() {
            // Purpur start - Ridables
            if (slime.getRider() != null && slime.isControllable()) {
                purpurTick(slime.getRider());
                if (slime.getForwardMot() != 0 || slime.getStrafeMot() != 0) {
                    if (jumpDelay > 10) {
                        jumpDelay = 6;
                    }
                } else {
                    jumpDelay = 20;
                }
            } else {
            // Purpur end - Ridables
            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), this.yRot, 90.0F));
            this.mob.yHeadRot = this.mob.getYRot();
            this.mob.yBodyRot = this.mob.getYRot();
            } if ((slime.getRider() == null || !slime.isControllable()) && this.operation != MoveControl.Operation.MOVE_TO) { // Purpur - Ridables
                this.mob.setZza(0.0F);
            } else {
                this.operation = MoveControl.Operation.WAIT;
                if (this.mob.onGround()) {
                    this.mob.setSpeed((float)(this.getSpeedModifier() * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * (slime.getRider() != null && slime.isControllable() && (slime.getRider().getForwardMot() != 0 || slime.getRider().getStrafeMot() != 0) ? 2.0D : 1.0D))); // Purpur - Ridables
                    if (this.jumpDelay-- <= 0) {
                        this.jumpDelay = this.slime.getJumpDelay();
                        if (this.isAggressive) {
                            this.jumpDelay /= 3;
                        }

                        this.slime.getJumpControl().jump();
                        if (this.slime.doPlayJumpSound()) {
                            this.slime.playSound(this.slime.getJumpSound(), this.slime.getSoundVolume(), this.slime.getSoundPitch());
                        }
                    } else {
                        this.slime.xxa = 0.0F;
                        this.slime.zza = 0.0F;
                        this.mob.setSpeed(0.0F);
                    }
                } else {
                    this.mob.setSpeed((float)(this.getSpeedModifier() * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * (slime.getRider() != null && slime.isControllable() && (slime.getRider().getForwardMot() != 0 || slime.getRider().getStrafeMot() != 0) ? 2.0D : 1.0D))); // Purpur - Ridables
                }
            }
        }
    }

    static class SlimeRandomDirectionGoal extends Goal {
        private final Slime slime;
        private float chosenDegrees;
        private int nextRandomizeTime;

        public SlimeRandomDirectionGoal(Slime slime) {
            this.slime = slime;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.slime.getTarget() == null && this.slime.canWander // Paper - Slime pathfinder events
                && (this.slime.onGround() || this.slime.isInWater() || this.slime.isInLava() || this.slime.hasEffect(MobEffects.LEVITATION))
                && this.slime.getMoveControl() instanceof Slime.SlimeMoveControl;
        }

        @Override
        public void tick() {
            if (--this.nextRandomizeTime <= 0) {
                this.nextRandomizeTime = this.adjustedTickDelay(40 + this.slime.getRandom().nextInt(60));
                this.chosenDegrees = this.slime.getRandom().nextInt(360);
                // Paper start - Slime pathfinder events
                com.destroystokyo.paper.event.entity.SlimeChangeDirectionEvent event = new com.destroystokyo.paper.event.entity.SlimeChangeDirectionEvent((org.bukkit.entity.Slime) this.slime.getBukkitEntity(), this.chosenDegrees);
                if (!this.slime.canWander || !event.callEvent()) return;
                this.chosenDegrees = event.getNewYaw();
                // Paper end - Slime pathfinder events
            }

            if (this.slime.getMoveControl() instanceof Slime.SlimeMoveControl slimeMoveControl) {
                slimeMoveControl.setDirection(this.chosenDegrees, false);
            }
        }
    }
}
