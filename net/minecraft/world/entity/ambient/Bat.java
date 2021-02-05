package net.minecraft.world.entity.ambient;

import java.time.LocalDate;
import java.time.temporal.ChronoField;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class Bat extends AmbientCreature {
    public static final float FLAP_LENGTH_SECONDS = 0.5F;
    public static final float TICKS_PER_FLAP = 10.0F;
    private static final EntityDataAccessor<Byte> DATA_ID_FLAGS = SynchedEntityData.defineId(Bat.class, EntityDataSerializers.BYTE);
    private static final int FLAG_RESTING = 1;
    private static final TargetingConditions BAT_RESTING_TARGETING = TargetingConditions.forNonCombat().range(4.0);
    public final AnimationState flyAnimationState = new AnimationState();
    public final AnimationState restAnimationState = new AnimationState();
    @Nullable
    public BlockPos targetPosition;

    public Bat(EntityType<? extends Bat> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new org.purpurmc.purpur.controller.FlyingWithSpacebarMoveControllerWASD(this, 0.075F); // Purpur - Ridables
        if (!level.isClientSide) {
            this.setResting(true);
        }
    }

    // Purpur start - Ridables
    @Override
    public boolean shouldSendAttribute(net.minecraft.world.entity.ai.attributes.Attribute attribute) { return attribute != Attributes.FLYING_SPEED.value(); } // Fixes log spam on clients

    @Override
    public boolean isRidable() {
        return level().purpurConfig.batRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.batRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.batControllable;
    }

    @Override
    public double getMaxY() {
        return level().purpurConfig.batMaxY;
    }

    @Override
    public void onMount(net.minecraft.world.entity.player.Player rider) {
        super.onMount(rider);
        if (isResting()) {
            setResting(false);
            level().levelEvent(null, 1025, new BlockPos(this).above(), 0);
        }
    }

    @Override
    public void travel(Vec3 vec3) {
        super.travel(vec3);
        if (getRider() != null && this.isControllable() && !onGround) {
            float speed = (float) getAttributeValue(Attributes.FLYING_SPEED) * 2;
            setSpeed(speed);
            Vec3 mot = getDeltaMovement();
            move(net.minecraft.world.entity.MoverType.SELF, mot.multiply(speed, 0.25, speed));
            setDeltaMovement(mot.scale(0.9D));
        }
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.batMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.batScale);
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(this.level().purpurConfig.batFollowRange);
        this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(this.level().purpurConfig.batKnockbackResistance);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(this.level().purpurConfig.batMovementSpeed);
        this.getAttribute(Attributes.FLYING_SPEED).setBaseValue(this.level().purpurConfig.batFlyingSpeed);
        this.getAttribute(Attributes.ARMOR).setBaseValue(this.level().purpurConfig.batArmor);
        this.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(this.level().purpurConfig.batArmorToughness);
        this.getAttribute(Attributes.ATTACK_KNOCKBACK).setBaseValue(this.level().purpurConfig.batAttackKnockback);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.batTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    @Override
    public boolean isFlapping() {
        return !this.isResting() && this.tickCount % 10.0F == 0.0F;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ID_FLAGS, (byte)0);
    }

    @Override
    public float getSoundVolume() {
        return 0.1F;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.95F;
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound() {
        return this.isResting() && this.random.nextInt(4) != 0 ? null : SoundEvents.BAT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.BAT_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.BAT_DEATH;
    }

    @Override
    public boolean isCollidable(boolean ignoreClimbing) { // Paper - Climbing should not bypass cramming gamerule
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    protected void pushEntities() {
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.FLYING_SPEED, 0.6D); // Purpur - Ridables
    }

    public boolean isResting() {
        return (this.entityData.get(DATA_ID_FLAGS) & 1) != 0;
    }

    public void setResting(boolean isResting) {
        byte b = this.entityData.get(DATA_ID_FLAGS);
        if (isResting) {
            this.entityData.set(DATA_ID_FLAGS, (byte)(b | 1));
        } else {
            this.entityData.set(DATA_ID_FLAGS, (byte)(b & -2));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isResting()) {
            this.setDeltaMovement(Vec3.ZERO);
            this.setPosRaw(this.getX(), Mth.floor(this.getY()) + 1.0 - this.getBbHeight(), this.getZ());
        } else {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.6, 1.0));
        }

        this.setupAnimationStates();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        // Purpur start - Ridables
        if (getRider() != null && this.isControllable()) {
            Vec3 mot = getDeltaMovement();
            setDeltaMovement(mot.x(), mot.y() + (getVerticalMot() > 0 ? 0.07D : 0.0D), mot.z());
            return;
        }
        // Purpur end - Ridables

        super.customServerAiStep(level);
        BlockPos blockPos = this.blockPosition();
        BlockPos blockPos1 = blockPos.above();
        if (this.isResting()) {
            boolean isSilent = this.isSilent();
            if (level.getBlockState(blockPos1).isRedstoneConductor(level, blockPos)) {
                if (this.random.nextInt(200) == 0) {
                    this.yHeadRot = this.random.nextInt(360);
                }

                if (level.getNearestPlayer(BAT_RESTING_TARGETING, this) != null && org.bukkit.craftbukkit.event.CraftEventFactory.handleBatToggleSleepEvent(this, true)) { // CraftBukkit - Call BatToggleSleepEvent
                    this.setResting(false);
                    if (!isSilent) {
                        level.levelEvent(null, 1025, blockPos, 0);
                    }
                }
            } else if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBatToggleSleepEvent(this, true)) { // CraftBukkit - Call BatToggleSleepEvent
                this.setResting(false);
                if (!isSilent) {
                    level.levelEvent(null, 1025, blockPos, 0);
                }
            }
        } else {
            if (this.targetPosition != null && (!level.isEmptyBlock(this.targetPosition) || this.targetPosition.getY() <= level.getMinY())) {
                this.targetPosition = null;
            }

            if (this.targetPosition == null || this.random.nextInt(30) == 0 || this.targetPosition.closerToCenterThan(this.position(), 2.0)) {
                this.targetPosition = BlockPos.containing(
                    this.getX() + this.random.nextInt(7) - this.random.nextInt(7),
                    this.getY() + this.random.nextInt(6) - 2.0,
                    this.getZ() + this.random.nextInt(7) - this.random.nextInt(7)
                );
            }

            double d = this.targetPosition.getX() + 0.5 - this.getX();
            double d1 = this.targetPosition.getY() + 0.1 - this.getY();
            double d2 = this.targetPosition.getZ() + 0.5 - this.getZ();
            Vec3 deltaMovement = this.getDeltaMovement();
            Vec3 vec3 = deltaMovement.add(
                (Math.signum(d) * 0.5 - deltaMovement.x) * 0.1F,
                (Math.signum(d1) * 0.7F - deltaMovement.y) * 0.1F,
                (Math.signum(d2) * 0.5 - deltaMovement.z) * 0.1F
            );
            this.setDeltaMovement(vec3);
            float f = (float)(Mth.atan2(vec3.z, vec3.x) * 180.0F / (float)Math.PI) - 90.0F;
            float f1 = Mth.wrapDegrees(f - this.getYRot());
            this.zza = 0.5F;
            this.setYRot(this.getYRot() + f1);
            if (this.random.nextInt(100) == 0 && level.getBlockState(blockPos1).isRedstoneConductor(level, blockPos1) && org.bukkit.craftbukkit.event.CraftEventFactory.handleBatToggleSleepEvent(this, false)) { // CraftBukkit - Call BatToggleSleepEvent
                this.setResting(true);
            }
        }
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.EVENTS;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableTo(level, damageSource)) {
            return false;
        } else {
            if (this.isResting() && org.bukkit.craftbukkit.event.CraftEventFactory.handleBatToggleSleepEvent(this, true)) { // CraftBukkit - Call BatToggleSleepEvent
                this.setResting(false);
            }

            return super.hurtServer(level, damageSource, amount);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.entityData.set(DATA_ID_FLAGS, compound.getByte("BatFlags"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putByte("BatFlags", this.entityData.get(DATA_ID_FLAGS));
    }

    public static boolean checkBatSpawnRules(
        EntityType<Bat> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource randomSource
    ) {
        if (pos.getY() >= level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, pos).getY()) {
            return false;
        } else {
            int maxLocalRawBrightness = level.getMaxLocalRawBrightness(pos);
            int i = 4;
            if (Bat.isHalloweenSeason(level.getMinecraftWorld())) { // Purpur - Halloween options and optimizations
                i = 7;
            } else if (randomSource.nextBoolean()) {
                return false;
            }

            return maxLocalRawBrightness <= randomSource.nextInt(i)
                && level.getBlockState(pos.below()).is(BlockTags.BATS_SPAWNABLE_ON)
                && checkMobSpawnRules(entityType, level, spawnReason, pos, randomSource);
        }
    }

    // Pufferfish start - only check for spooky season once an hour
    //private static boolean isSpookySeason = false;
    //private static final int ONE_HOUR = 20 * 60 * 60;
    //private static int lastSpookyCheck = -ONE_HOUR;
    public static boolean isHalloweenSeason(Level level) { return level.purpurConfig.forceHalloweenSeason || isHalloween(); } // Purpur - Halloween options and optimizations
    private static boolean isHalloween() {
        LocalDate localDate = LocalDate.now();
        int i = localDate.get(ChronoField.DAY_OF_MONTH);
        int i1 = localDate.get(ChronoField.MONTH_OF_YEAR);
        return i1 == 10 && i >= 20 || i1 == 11 && i <= 3;
    }

    private void setupAnimationStates() {
        if (this.isResting()) {
            this.flyAnimationState.stop();
            this.restAnimationState.startIfStopped(this.tickCount);
        } else {
            this.restAnimationState.stop();
            this.flyAnimationState.startIfStopped(this.tickCount);
        }
    }
}
