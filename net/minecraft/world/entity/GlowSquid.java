package net.minecraft.world.entity;

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
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;

public class GlowSquid extends Squid {
    private static final EntityDataAccessor<Integer> DATA_DARK_TICKS_REMAINING = SynchedEntityData.defineId(GlowSquid.class, EntityDataSerializers.INT);

    public GlowSquid(EntityType<? extends GlowSquid> entityType, Level level) {
        super(entityType, level);
    }

    // Purpur start - Flying squids! Oh my!
    @Override
    public boolean canFly() {
        return this.level().purpurConfig.glowSquidsCanFly;
    }
    // Purpur end - Flying squids! Oh my!

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.glowSquidRidable;
    }


    @Override
    public boolean isControllable() {
        return level().purpurConfig.glowSquidControllable;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.glowSquidMaxHealth);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.glowSquidTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.glowSquidAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    protected ParticleOptions getInkParticle() {
        return ParticleTypes.GLOW_SQUID_INK;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DARK_TICKS_REMAINING, 0);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return EntityType.GLOW_SQUID.create(level, EntitySpawnReason.BREEDING);
    }

    @Override
    protected SoundEvent getSquirtSound() {
        return SoundEvents.GLOW_SQUID_SQUIRT;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.GLOW_SQUID_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.GLOW_SQUID_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.GLOW_SQUID_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("DarkTicksRemaining", this.getDarkTicksRemaining());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setDarkTicks(compound.getInt("DarkTicksRemaining"));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        int darkTicksRemaining = this.getDarkTicksRemaining();
        if (darkTicksRemaining > 0) {
            this.setDarkTicks(darkTicksRemaining - 1);
        }

        this.level().addParticle(ParticleTypes.GLOW, this.getRandomX(0.6), this.getRandomY(), this.getRandomZ(0.6), 0.0, 0.0, 0.0);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        boolean flag = super.hurtServer(level, damageSource, amount);
        if (flag) {
            this.setDarkTicks(100);
        }

        return flag;
    }

    public void setDarkTicks(int darkTicks) {
        this.entityData.set(DATA_DARK_TICKS_REMAINING, darkTicks);
    }

    public int getDarkTicksRemaining() {
        return this.entityData.get(DATA_DARK_TICKS_REMAINING);
    }

    public static boolean checkGlowSquidSpawnRules(
        EntityType<? extends LivingEntity> entityType, ServerLevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return pos.getY() <= level.getSeaLevel() - 33 && level.getRawBrightness(pos, 0) == 0 && level.getBlockState(pos).is(Blocks.WATER);
    }
}
