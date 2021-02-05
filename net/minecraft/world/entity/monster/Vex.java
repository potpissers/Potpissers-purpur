package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

public class Vex extends Monster implements TraceableEntity {
    public static final float FLAP_DEGREES_PER_TICK = 45.836624F;
    public static final int TICKS_PER_FLAP = Mth.ceil((float) (Math.PI * 5.0 / 4.0));
    protected static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(Vex.class, EntityDataSerializers.BYTE);
    private static final int FLAG_IS_CHARGING = 1;
    @Nullable
    Mob owner;
    @Nullable
    private BlockPos boundOrigin;
    public boolean hasLimitedLife;
    public int limitedLifeTicks;

    public Vex(EntityType<? extends Vex> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new Vex.VexMoveControl(this);
        this.xpReward = 3;
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.vexRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.vexRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.vexControllable;
    }

    @Override
    public double getMaxY() {
        return level().purpurConfig.vexMaxY;
    }

    @Override
    public void travel(Vec3 vec3) {
        super.travel(vec3);
        if (getRider() != null && this.isControllable()) {
            float speed;
            if (onGround) {
                speed = (float) getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.1F;
            } else {
                speed = (float) getAttributeValue(Attributes.FLYING_SPEED);
            }
            setSpeed(speed);
            Vec3 mot = getDeltaMovement();
            move(net.minecraft.world.entity.MoverType.SELF, mot.multiply(speed, 1.0, speed));
            setDeltaMovement(mot.scale(0.9D));
        }
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false; //  no fall damage please
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.vexMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.vexScale);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.vexTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    @Override
    public boolean isFlapping() {
        return this.tickCount % TICKS_PER_FLAP == 0;
    }

    @Override
    public boolean isAffectedByBlocks() {
        return !this.isRemoved();
    }

    @Override
    public void tick() {
        this.noPhysics = getRider() == null || !this.isControllable(); // Purpur - Ridables
        super.tick();
        this.noPhysics = false;
        this.setNoGravity(true);
        if (this.hasLimitedLife && --this.limitedLifeTicks <= 0) {
            this.limitedLifeTicks = 20;
            this.hurt(this.damageSources().starve(), 1.0F);
        }
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.goalSelector.addGoal(4, new Vex.VexChargeAttackGoal());
        this.goalSelector.addGoal(8, new Vex.VexRandomMoveGoal());
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
        this.targetSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
        this.targetSelector.addGoal(2, new Vex.VexCopyOwnerTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 14.0).add(Attributes.ATTACK_DAMAGE, 4.0).add(Attributes.FLYING_SPEED, 0.6D); // Purpur;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS_ID, (byte)0);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("BoundX")) {
            this.boundOrigin = new BlockPos(compound.getInt("BoundX"), compound.getInt("BoundY"), compound.getInt("BoundZ"));
        }

        if (compound.contains("LifeTicks")) {
            this.setLimitedLife(compound.getInt("LifeTicks"));
        }
    }

    @Override
    public void restoreFrom(Entity entity) {
        super.restoreFrom(entity);
        if (entity instanceof Vex vex) {
            this.owner = vex.getOwner();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (this.boundOrigin != null) {
            compound.putInt("BoundX", this.boundOrigin.getX());
            compound.putInt("BoundY", this.boundOrigin.getY());
            compound.putInt("BoundZ", this.boundOrigin.getZ());
        }

        if (this.hasLimitedLife) {
            compound.putInt("LifeTicks", this.limitedLifeTicks);
        }
    }

    @Nullable
    @Override
    public Mob getOwner() {
        return this.owner;
    }

    @Nullable
    public BlockPos getBoundOrigin() {
        return this.boundOrigin;
    }

    public void setBoundOrigin(@Nullable BlockPos boundOrigin) {
        this.boundOrigin = boundOrigin;
    }

    private boolean getVexFlag(int mask) {
        int i = this.entityData.get(DATA_FLAGS_ID);
        return (i & mask) != 0;
    }

    private void setVexFlag(int mask, boolean value) {
        int i = this.entityData.get(DATA_FLAGS_ID);
        if (value) {
            i |= mask;
        } else {
            i &= ~mask;
        }

        this.entityData.set(DATA_FLAGS_ID, (byte)(i & 0xFF));
    }

    public boolean isCharging() {
        return this.getVexFlag(1);
    }

    public void setIsCharging(boolean charging) {
        this.setVexFlag(1, charging);
    }

    public void setOwner(Mob owner) {
        this.owner = owner;
    }

    public void setLimitedLife(int limitedLifeTicks) {
        this.hasLimitedLife = true;
        this.limitedLifeTicks = limitedLifeTicks;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VEX_AMBIENT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.VEX_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.VEX_HURT;
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        RandomSource random = level.getRandom();
        this.populateDefaultEquipmentSlots(random, difficulty);
        this.populateDefaultEquipmentEnchantments(level, random, difficulty);
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    class VexChargeAttackGoal extends Goal {
        public VexChargeAttackGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = Vex.this.getTarget();
            return target != null
                && target.isAlive()
                && !Vex.this.getMoveControl().hasWanted()
                && Vex.this.random.nextInt(reducedTickDelay(7)) == 0
                && Vex.this.distanceToSqr(target) > 4.0;
        }

        @Override
        public boolean canContinueToUse() {
            return Vex.this.getMoveControl().hasWanted() && Vex.this.isCharging() && Vex.this.getTarget() != null && Vex.this.getTarget().isAlive();
        }

        @Override
        public void start() {
            LivingEntity target = Vex.this.getTarget();
            if (target != null) {
                Vec3 eyePosition = target.getEyePosition();
                Vex.this.moveControl.setWantedPosition(eyePosition.x, eyePosition.y, eyePosition.z, 1.0);
            }

            Vex.this.setIsCharging(true);
            Vex.this.playSound(SoundEvents.VEX_CHARGE, 1.0F, 1.0F);
        }

        @Override
        public void stop() {
            Vex.this.setIsCharging(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = Vex.this.getTarget();
            if (target != null) {
                if (Vex.this.getBoundingBox().intersects(target.getBoundingBox())) {
                    Vex.this.doHurtTarget(getServerLevel(Vex.this.level()), target);
                    Vex.this.setIsCharging(false);
                } else {
                    double d = Vex.this.distanceToSqr(target);
                    if (d < 9.0) {
                        Vec3 eyePosition = target.getEyePosition();
                        Vex.this.moveControl.setWantedPosition(eyePosition.x, eyePosition.y, eyePosition.z, 1.0);
                    }
                }
            }
        }
    }

    class VexCopyOwnerTargetGoal extends TargetGoal {
        private final TargetingConditions copyOwnerTargeting = TargetingConditions.forNonCombat().ignoreLineOfSight().ignoreInvisibilityTesting();

        public VexCopyOwnerTargetGoal(final PathfinderMob mob) {
            super(mob, false);
        }

        @Override
        public boolean canUse() {
            return Vex.this.owner != null && Vex.this.owner.getTarget() != null && this.canAttack(Vex.this.owner.getTarget(), this.copyOwnerTargeting);
        }

        @Override
        public void start() {
            Vex.this.setTarget(Vex.this.owner.getTarget(), org.bukkit.event.entity.EntityTargetEvent.TargetReason.OWNER_ATTACKED_TARGET, true); // CraftBukkit
            super.start();
        }
    }

    class VexMoveControl extends org.purpurmc.purpur.controller.FlyingMoveControllerWASD { // Purpur - Ridables
        public VexMoveControl(final Vex mob) {
            super(mob);
        }

        @Override
        public void vanillaTick() { // Purpur - Ridables
            if (this.operation == MoveControl.Operation.MOVE_TO) {
                Vec3 vec3 = new Vec3(this.wantedX - Vex.this.getX(), this.wantedY - Vex.this.getY(), this.wantedZ - Vex.this.getZ());
                double len = vec3.length();
                if (len < Vex.this.getBoundingBox().getSize()) {
                    this.operation = MoveControl.Operation.WAIT;
                    Vex.this.setDeltaMovement(Vex.this.getDeltaMovement().scale(0.5));
                } else {
                    Vex.this.setDeltaMovement(Vex.this.getDeltaMovement().add(vec3.scale(this.getSpeedModifier() * 0.05 / len))); // Purpur - Ridables
                    if (Vex.this.getTarget() == null) {
                        Vec3 deltaMovement = Vex.this.getDeltaMovement();
                        Vex.this.setYRot(-((float)Mth.atan2(deltaMovement.x, deltaMovement.z)) * (180.0F / (float)Math.PI));
                        Vex.this.yBodyRot = Vex.this.getYRot();
                    } else {
                        double d = Vex.this.getTarget().getX() - Vex.this.getX();
                        double d1 = Vex.this.getTarget().getZ() - Vex.this.getZ();
                        Vex.this.setYRot(-((float)Mth.atan2(d, d1)) * (180.0F / (float)Math.PI));
                        Vex.this.yBodyRot = Vex.this.getYRot();
                    }
                }
            }
        }
    }

    class VexRandomMoveGoal extends Goal {
        public VexRandomMoveGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !Vex.this.getMoveControl().hasWanted() && Vex.this.random.nextInt(reducedTickDelay(7)) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void tick() {
            BlockPos boundOrigin = Vex.this.getBoundOrigin();
            if (boundOrigin == null) {
                boundOrigin = Vex.this.blockPosition();
            }

            for (int i = 0; i < 3; i++) {
                BlockPos blockPos = boundOrigin.offset(Vex.this.random.nextInt(15) - 7, Vex.this.random.nextInt(11) - 5, Vex.this.random.nextInt(15) - 7);
                // Paper start - Don't load chunks
                final net.minecraft.world.level.block.state.BlockState blockState = Vex.this.level().getBlockStateIfLoaded(blockPos);
                if (blockState != null && blockState.isAir()) {
                    // Paper end - Don't load chunks
                    Vex.this.moveControl.setWantedPosition(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5, 0.25);
                    if (Vex.this.getTarget() == null) {
                        Vex.this.getLookControl().setLookAt(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5, 180.0F, 20.0F);
                    }
                    break;
                }
            }
        }
    }
}
