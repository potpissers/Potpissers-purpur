package net.minecraft.world.entity.monster;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

// Purpur start
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
// Purpur end

public class Skeleton extends AbstractSkeleton {

    private static final int TOTAL_CONVERSION_TIME = 300;
    public static final EntityDataAccessor<Boolean> DATA_STRAY_CONVERSION_ID = SynchedEntityData.defineId(Skeleton.class, EntityDataSerializers.BOOLEAN);
    public static final String CONVERSION_TAG = "StrayConversionTime";
    public int inPowderSnowTime;
    public int conversionTime;

    public Skeleton(EntityType<? extends Skeleton> type, Level world) {
        super(type, world);
    }

    // Purpur start
    @Override
    public boolean isRidable() {
        return level().purpurConfig.skeletonRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.skeletonRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.skeletonControllable;
    }
    // Purpur end

    @Override
    public void initAttributes() {
        this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.skeletonMaxHealth);
    }

    // Purpur start
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.skeletonTakeDamageFromWater;
    }
    // Purpur end

    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.skeletonAlwaysDropExp;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(Skeleton.DATA_STRAY_CONVERSION_ID, false);
    }

    public boolean isFreezeConverting() {
        return (Boolean) this.getEntityData().get(Skeleton.DATA_STRAY_CONVERSION_ID);
    }

    public void setFreezeConverting(boolean converting) {
        this.entityData.set(Skeleton.DATA_STRAY_CONVERSION_ID, converting);
    }

    @Override
    public boolean isShaking() {
        return this.isFreezeConverting();
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.isAlive() && !this.isNoAi()) {
            if (this.isInPowderSnow) {
                if (this.isFreezeConverting()) {
                    --this.conversionTime;
                    if (this.conversionTime < 0) {
                        this.doFreezeConversion();
                    }
                } else {
                    ++this.inPowderSnowTime;
                    if (this.inPowderSnowTime >= 140) {
                        this.startFreezeConversion(300);
                    }
                }
            } else {
                this.inPowderSnowTime = -1;
                this.setFreezeConverting(false);
            }
        }

        super.tick();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("StrayConversionTime", this.isFreezeConverting() ? this.conversionTime : -1);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("StrayConversionTime", 99) && nbt.getInt("StrayConversionTime") > -1) {
            this.startFreezeConversion(nbt.getInt("StrayConversionTime"));
        }

    }

    public void startFreezeConversion(int time) {
        this.conversionTime = time;
        this.setFreezeConverting(true);
    }

    protected void doFreezeConversion() {
        Stray stray = this.convertTo(EntityType.STRAY, true, org.bukkit.event.entity.EntityTransformEvent.TransformReason.FROZEN, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.FROZEN); // CraftBukkit - add spawn and transform reasons // Paper - Fix issues with mob conversion
        if (!this.isSilent()) {
            this.level().levelEvent((Player) null, 1048, this.blockPosition(), 0);
        }
        // Paper start - Fix issues with mob conversion; reset conversion time to prevent event spam
        if (stray == null) {
            this.conversionTime = 300;
        }
        // Paper end - Fix issues with mob conversion

    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }

    @Override
    SoundEvent getStepSound() {
        return SoundEvents.SKELETON_STEP;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel world, DamageSource source, boolean causedByPlayer) {
        super.dropCustomDeathLoot(world, source, causedByPlayer);
        Entity entity = source.getEntity();

        if (entity instanceof Creeper entitycreeper) {
            if (entitycreeper.canDropMobsSkull()) {
                entitycreeper.increaseDroppedSkulls();
                this.spawnAtLocation((ItemLike) Items.SKELETON_SKULL);
            }
        }

    }

    // Purpur start
    private int witherRosesFed = 0;

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level().purpurConfig.skeletonFeedWitherRoses > 0 && this.getType() != EntityType.WITHER_SKELETON && stack.getItem() == Blocks.WITHER_ROSE.asItem()) {
            return this.feedWitherRose(player, stack);
        }

        return super.mobInteract(player, hand);
    }

    private InteractionResult feedWitherRose(Player player, ItemStack stack) {
        if (++witherRosesFed < level().purpurConfig.skeletonFeedWitherRoses) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return InteractionResult.CONSUME;
        }

        WitherSkeleton skeleton = EntityType.WITHER_SKELETON.create(level());
        if (skeleton == null) {
            return InteractionResult.PASS;
        }

        skeleton.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
        skeleton.setHealth(this.getHealth());
        skeleton.setAggressive(this.isAggressive());
        skeleton.copyPosition(this);
        skeleton.setYBodyRot(this.yBodyRot);
        skeleton.setYHeadRot(this.getYHeadRot());
        skeleton.yRotO = this.yRotO;
        skeleton.xRotO = this.xRotO;

        if (this.hasCustomName()) {
            skeleton.setCustomName(this.getCustomName());
        }

        if (CraftEventFactory.callEntityTransformEvent(this, skeleton, org.bukkit.event.entity.EntityTransformEvent.TransformReason.INFECTION).isCancelled()) {
            return InteractionResult.PASS;
        }

        this.level().addFreshEntity(skeleton);
        this.remove(RemovalReason.DISCARDED, org.bukkit.event.entity.EntityRemoveEvent.Cause.DISCARD);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        for (int i = 0; i < 15; ++i) {
            ((ServerLevel) level()).sendParticles(((ServerLevel) level()).players(), null, ParticleTypes.HAPPY_VILLAGER,
                    getX() + random.nextFloat(), getY() + (random.nextFloat() * 2), getZ() + random.nextFloat(), 1,
                    random.nextGaussian() * 0.05D, random.nextGaussian() * 0.05D, random.nextGaussian() * 0.05D, 0, true);
        }
        return InteractionResult.SUCCESS;
    }
    // Purpur end
}
