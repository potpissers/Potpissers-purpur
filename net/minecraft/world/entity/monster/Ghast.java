package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class Ghast extends FlyingMob implements Enemy {
    private static final EntityDataAccessor<Boolean> DATA_IS_CHARGING = SynchedEntityData.defineId(Ghast.class, EntityDataSerializers.BOOLEAN);
    private int explosionPower = 1;

    public Ghast(EntityType<? extends Ghast> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 5;
        this.moveControl = new Ghast.GhastMoveControl(this);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.ghastRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.ghastRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.ghastControllable;
    }

    @Override
    public double getMaxY() {
        return level().purpurConfig.ghastMaxY;
    }

    @Override
    public void travel(Vec3 vec3) {
        super.travel(vec3);
        if (getRider() != null && this.isControllable() && !onGround) {
            float speed = (float) getAttributeValue(Attributes.FLYING_SPEED);
            setSpeed(speed);
            Vec3 mot = getDeltaMovement();
            move(net.minecraft.world.entity.MoverType.SELF, mot.multiply(speed, 1.0, speed));
            setDeltaMovement(mot.scale(0.9D));
        }
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.ghastMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.ghastScale);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.ghastTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.ghastAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.goalSelector.addGoal(5, new Ghast.RandomFloatAroundGoal(this));
        this.goalSelector.addGoal(7, new Ghast.GhastLookGoal(this));
        this.goalSelector.addGoal(7, new Ghast.GhastShootFireballGoal(this));
        this.targetSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.targetSelector
            .addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (entity, level) -> Math.abs(entity.getY() - this.getY()) <= 4.0));
    }

    public boolean isCharging() {
        return this.entityData.get(DATA_IS_CHARGING);
    }

    public void setCharging(boolean charging) {
        this.entityData.set(DATA_IS_CHARGING, charging);
    }

    public int getExplosionPower() {
        return this.explosionPower;
    }

    // Paper start
    public void setExplosionPower(int explosionPower) {
        this.explosionPower = explosionPower;
    }
    // Paper end

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    private static boolean isReflectedFireball(DamageSource damageSource) {
        return damageSource.getDirectEntity() instanceof LargeFireball && damageSource.getEntity() instanceof Player;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource damageSource) {
        return this.isInvulnerable() && !damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
            || !isReflectedFireball(damageSource) && super.isInvulnerableTo(level, damageSource);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (isReflectedFireball(damageSource)) {
            super.hurtServer(level, damageSource, 1000.0F);
            return true;
        } else {
            return !this.isInvulnerableTo(level, damageSource) && super.hurtServer(level, damageSource, amount);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_IS_CHARGING, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.FOLLOW_RANGE, 100.0).add(Attributes.FLYING_SPEED, 0.6D); // Purpur - Ridables
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.GHAST_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.GHAST_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.GHAST_DEATH;
    }

    @Override
    public float getSoundVolume() {
        return 5.0F;
    }

    public static boolean checkGhastSpawnRules(
        EntityType<Ghast> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return level.getDifficulty() != Difficulty.PEACEFUL && random.nextInt(20) == 0 && checkMobSpawnRules(entityType, level, spawnReason, pos, random);
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 1;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putByte("ExplosionPower", (byte)this.explosionPower);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("ExplosionPower", 99)) {
            this.explosionPower = compound.getByte("ExplosionPower");
        }
    }

    static class GhastLookGoal extends Goal {
        private final Ghast ghast;

        public GhastLookGoal(Ghast ghast) {
            this.ghast = ghast;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.ghast.getTarget() == null) {
                Vec3 deltaMovement = this.ghast.getDeltaMovement();
                this.ghast.setYRot(-((float)Mth.atan2(deltaMovement.x, deltaMovement.z)) * (180.0F / (float)Math.PI));
                this.ghast.yBodyRot = this.ghast.getYRot();
            } else {
                LivingEntity target = this.ghast.getTarget();
                double d = 64.0;
                if (target.distanceToSqr(this.ghast) < 4096.0) {
                    double d1 = target.getX() - this.ghast.getX();
                    double d2 = target.getZ() - this.ghast.getZ();
                    this.ghast.setYRot(-((float)Mth.atan2(d1, d2)) * (180.0F / (float)Math.PI));
                    this.ghast.yBodyRot = this.ghast.getYRot();
                }
            }
        }
    }

    static class GhastMoveControl extends org.purpurmc.purpur.controller.FlyingMoveControllerWASD { // Purpur - Ridables
        private final Ghast ghast;
        private int floatDuration;

        public GhastMoveControl(Ghast mob) {
            super(mob);
            this.ghast = mob;
        }

        @Override
        public void vanillaTick() { // Purpur - Ridables
            if (this.operation == MoveControl.Operation.MOVE_TO) {
                if (this.floatDuration-- <= 0) {
                    this.floatDuration = this.floatDuration + this.ghast.getRandom().nextInt(5) + 2;
                    Vec3 vec3 = new Vec3(this.wantedX - this.ghast.getX(), this.wantedY - this.ghast.getY(), this.wantedZ - this.ghast.getZ());
                    double len = vec3.length();
                    vec3 = vec3.normalize();
                    if (this.canReach(vec3, Mth.ceil(len))) {
                        this.ghast.setDeltaMovement(this.ghast.getDeltaMovement().add(vec3.scale(0.1)));
                    } else {
                        this.operation = MoveControl.Operation.WAIT;
                    }
                }
            }
        }

        private boolean canReach(Vec3 pos, int length) {
            AABB boundingBox = this.ghast.getBoundingBox();

            for (int i = 1; i < length; i++) {
                boundingBox = boundingBox.move(pos);
                if (!this.ghast.level().noCollision(this.ghast, boundingBox)) {
                    return false;
                }
            }

            return true;
        }
    }

    static class GhastShootFireballGoal extends Goal {
        private final Ghast ghast;
        public int chargeTime;

        public GhastShootFireballGoal(Ghast ghast) {
            this.ghast = ghast;
        }

        @Override
        public boolean canUse() {
            return this.ghast.getTarget() != null;
        }

        @Override
        public void start() {
            this.chargeTime = 0;
        }

        @Override
        public void stop() {
            this.ghast.setCharging(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.ghast.getTarget();
            if (target != null) {
                double d = 64.0;
                if (target.distanceToSqr(this.ghast) < 4096.0 && this.ghast.hasLineOfSight(target)) {
                    Level level = this.ghast.level();
                    this.chargeTime++;
                    if (this.chargeTime == 10 && !this.ghast.isSilent()) {
                        level.levelEvent(null, 1015, this.ghast.blockPosition(), 0);
                    }

                    if (this.chargeTime == 20) {
                        double d1 = 4.0;
                        Vec3 viewVector = this.ghast.getViewVector(1.0F);
                        double d2 = target.getX() - (this.ghast.getX() + viewVector.x * 4.0);
                        double d3 = target.getY(0.5) - (0.5 + this.ghast.getY(0.5));
                        double d4 = target.getZ() - (this.ghast.getZ() + viewVector.z * 4.0);
                        Vec3 vec3 = new Vec3(d2, d3, d4);
                        if (!this.ghast.isSilent()) {
                            level.levelEvent(null, 1016, this.ghast.blockPosition(), 0);
                        }

                        LargeFireball largeFireball = new LargeFireball(level, this.ghast, vec3.normalize(), this.ghast.getExplosionPower());
                        largeFireball.bukkitYield = largeFireball.explosionPower = this.ghast.getExplosionPower(); // CraftBukkit - set bukkitYield when setting explosionPower
                        largeFireball.setPos(this.ghast.getX() + viewVector.x * 4.0, this.ghast.getY(0.5) + 0.5, largeFireball.getZ() + viewVector.z * 4.0);
                        level.addFreshEntity(largeFireball);
                        this.chargeTime = -40;
                    }
                } else if (this.chargeTime > 0) {
                    this.chargeTime--;
                }

                this.ghast.setCharging(this.chargeTime > 10);
            }
        }
    }

    static class RandomFloatAroundGoal extends Goal {
        private final Ghast ghast;

        public RandomFloatAroundGoal(Ghast ghast) {
            this.ghast = ghast;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            MoveControl moveControl = this.ghast.getMoveControl();
            if (!moveControl.hasWanted()) {
                return true;
            } else {
                double d = moveControl.getWantedX() - this.ghast.getX();
                double d1 = moveControl.getWantedY() - this.ghast.getY();
                double d2 = moveControl.getWantedZ() - this.ghast.getZ();
                double d3 = d * d + d1 * d1 + d2 * d2;
                return d3 < 1.0 || d3 > 3600.0;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            RandomSource random = this.ghast.getRandom();
            double d = this.ghast.getX() + (random.nextFloat() * 2.0F - 1.0F) * 16.0F;
            double d1 = this.ghast.getY() + (random.nextFloat() * 2.0F - 1.0F) * 16.0F;
            double d2 = this.ghast.getZ() + (random.nextFloat() * 2.0F - 1.0F) * 16.0F;
            this.ghast.getMoveControl().setWantedPosition(d, d1, d2, 1.0);
        }
    }
}
