package net.minecraft.world.entity.animal.horse;

import com.mojang.serialization.Codec;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LlamaFollowCaravanGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RunAroundLikeCrazyGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class Llama extends AbstractChestedHorse implements VariantHolder<Llama.Variant>, RangedAttackMob {
    private static final int MAX_STRENGTH = 5;
    private static final EntityDataAccessor<Integer> DATA_STRENGTH_ID = SynchedEntityData.defineId(Llama.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VARIANT_ID = SynchedEntityData.defineId(Llama.class, EntityDataSerializers.INT);
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.LLAMA
        .getDimensions()
        .withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, EntityType.LLAMA.getHeight() - 0.8125F, -0.3F))
        .scale(0.5F);
    boolean didSpit;
    @Nullable
    private Llama caravanHead;
    @Nullable
    public Llama caravanTail; // Paper
    public boolean shouldJoinCaravan = true; // Purpur - Llama API

    public Llama(EntityType<? extends Llama> entityType, Level level) {
        super(entityType, level);
        this.getNavigation().setRequiredPathLength(40.0F);
        this.maxDomestication = 30; // Paper - Missing entity API; configure max temper instead of a hardcoded value
        // Purpur start - Ridables
        this.moveControl = new org.purpurmc.purpur.controller.MoveControllerWASD(this) {
            @Override
            public void tick() {
                if (entity.getRider() != null && entity.isControllable() && isSaddled()) {
                    purpurTick(entity.getRider());
                } else {
                    vanillaTick();
                }
            }
        };
        this.lookControl = new org.purpurmc.purpur.controller.LookControllerWASD(this) {
            @Override
            public void tick() {
                if (entity.getRider() != null && entity.isControllable() && isSaddled()) {
                    purpurTick(entity.getRider());
                } else {
                    vanillaTick();
                }
            }
        };
        // Purpur end - Ridables
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.llamaRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.llamaRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.llamaControllable;
    }

    @Override
    public boolean isSaddled() {
        return super.isSaddled() || (isTamed());
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public float generateMaxHealth(RandomSource random) {
        return (float) generateMaxHealth(this.level().purpurConfig.llamaMaxHealthMin, this.level().purpurConfig.llamaMaxHealthMax);
    }

    @Override
    public double generateJumpStrength(RandomSource random) {
        return generateJumpStrength(this.level().purpurConfig.llamaJumpStrengthMin, this.level().purpurConfig.llamaJumpStrengthMax);
    }

    @Override
    public double generateSpeed(RandomSource random) {
        return generateSpeed(this.level().purpurConfig.llamaMovementSpeedMin, this.level().purpurConfig.llamaMovementSpeedMax);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Make entity breeding times configurable
    @Override
    public int getPurpurBreedTime() {
        return this.level().purpurConfig.llamaBreedingTicks;
    }
    // Purpur end - Make entity breeding times configurable

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.llamaTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    public boolean isTraderLlama() {
        return false;
    }

    // CraftBukkit start
    public void setStrengthPublic(int strength) {
        this.setStrength(strength);
    }
    // CraftBukkit end
    private void setStrength(int strength) {
        this.entityData.set(DATA_STRENGTH_ID, Math.max(1, Math.min(5, strength)));
    }

    private void setRandomStrength(RandomSource random) {
        int i = random.nextFloat() < 0.04F ? 5 : 3;
        this.setStrength(1 + random.nextInt(i));
    }

    public int getStrength() {
        return this.entityData.get(DATA_STRENGTH_ID);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Variant", this.getVariant().id);
        compound.putInt("Strength", this.getStrength());
        compound.putBoolean("Purpur.ShouldJoinCaravan", shouldJoinCaravan); // Purpur - Llama API
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        this.setStrength(compound.getInt("Strength"));
        super.readAdditionalSaveData(compound);
        this.setVariant(Llama.Variant.byId(compound.getInt("Variant")));
        if (compound.contains("Purpur.ShouldJoinCaravan")) this.shouldJoinCaravan = compound.getBoolean("Purpur.ShouldJoinCaravan"); // Purpur - Llama API
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.LlamaHasRider(this)); // Purpur - Ridables
        this.goalSelector.addGoal(1, new RunAroundLikeCrazyGoal(this, 1.2));
        this.goalSelector.addGoal(2, new LlamaFollowCaravanGoal(this, 2.1F));
        this.goalSelector.addGoal(3, new RangedAttackGoal(this, 1.25, 40, 20.0F));
        this.goalSelector.addGoal(3, new PanicGoal(this, 1.2));
        this.goalSelector.addGoal(4, new BreedGoal(this, 1.0));
        this.goalSelector.addGoal(5, new TemptGoal(this, 1.25, itemStack -> itemStack.is(ItemTags.LLAMA_TEMPT_ITEMS), false));
        this.goalSelector.addGoal(6, new FollowParentGoal(this, 1.0));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.LlamaHasRider(this)); // Purpur - Ridables
        this.targetSelector.addGoal(1, new Llama.LlamaHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new Llama.LlamaAttackWolfGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseChestedHorseAttributes();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STRENGTH_ID, 0);
        builder.define(DATA_VARIANT_ID, 0);
    }

    @Override
    public Llama.Variant getVariant() {
        return Llama.Variant.byId(this.entityData.get(DATA_VARIANT_ID));
    }

    @Override
    public void setVariant(Llama.Variant variant) {
        this.entityData.set(DATA_VARIANT_ID, variant.id);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.LLAMA_FOOD);
    }

    @Override
    protected boolean handleEating(Player player, ItemStack stack) {
        int i = 0;
        int i1 = 0;
        float f = 0.0F;
        boolean flag = false;
        if (stack.is(Items.WHEAT)) {
            i = 10;
            i1 = 3;
            f = 2.0F;
        } else if (stack.is(Blocks.HAY_BLOCK.asItem())) {
            i = 90;
            i1 = 6;
            f = 10.0F;
            if (this.isTamed() && this.getAge() == 0 && this.canFallInLove()) {
                flag = true;
                this.setInLove(player, stack.copy()); // Paper - Fix EntityBreedEvent copying
            }
        }

        if (this.getHealth() < this.getMaxHealth() && f > 0.0F) {
            this.heal(f, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.EATING); // Paper - Add missing regain reason
            flag = true;
        }

        if (this.isBaby() && i > 0) {
            this.level().addParticle(ParticleTypes.HAPPY_VILLAGER, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), 0.0, 0.0, 0.0);
            if (!this.level().isClientSide) {
                this.ageUp(i);
            }

            flag = true;
        }

        if (i1 > 0 && (flag || !this.isTamed()) && this.getTemper() < this.getMaxTemper()) {
            flag = true;
            if (!this.level().isClientSide) {
                this.modifyTemper(i1);
            }
        }

        if (flag && !this.isSilent()) {
            SoundEvent eatingSound = this.getEatingSound();
            if (eatingSound != null) {
                this.level()
                    .playSound(
                        null,
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        this.getEatingSound(),
                        this.getSoundSource(),
                        1.0F,
                        1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F
                    );
            }
        }

        return flag;
    }

    @Override
    public boolean isImmobile() {
        return this.isDeadOrDying() || this.isEating();
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        RandomSource random = level.getRandom();
        this.setRandomStrength(random);
        Llama.Variant variant;
        if (spawnGroupData instanceof Llama.LlamaGroupData) {
            variant = ((Llama.LlamaGroupData)spawnGroupData).variant;
        } else {
            variant = Util.getRandom(Llama.Variant.values(), random);
            spawnGroupData = new Llama.LlamaGroupData(variant);
        }

        this.setVariant(variant);
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    @Override
    protected boolean canPerformRearing() {
        return false;
    }

    @Override
    protected SoundEvent getAngrySound() {
        return SoundEvents.LLAMA_ANGRY;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.LLAMA_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.LLAMA_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.LLAMA_DEATH;
    }

    @Nullable
    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.LLAMA_EAT;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        this.playSound(SoundEvents.LLAMA_STEP, 0.15F, 1.0F);
    }

    @Override
    protected void playChestEquipsSound() {
        this.playSound(SoundEvents.LLAMA_CHEST, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
    }

    @Override
    public int getInventoryColumns() {
        return this.hasChest() ? this.getStrength() : 0;
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return true;
    }

    @Override
    public boolean isSaddleable() {
        return false;
    }

    @Override
    public int getMaxTemper() {
        return super.getMaxTemper(); // Paper - Missing entity API; delegate to parent
    }

    @Override
    public boolean canMate(Animal otherAnimal) {
        return otherAnimal != this && otherAnimal instanceof Llama && this.canParent() && ((Llama)otherAnimal).canParent();
    }

    @Nullable
    @Override
    public Llama getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        Llama llama = this.makeNewLlama();
        if (llama != null) {
            this.setOffspringAttributes(otherParent, llama);
            Llama llama1 = (Llama)otherParent;
            int i = this.random.nextInt(Math.max(this.getStrength(), llama1.getStrength())) + 1;
            if (this.random.nextFloat() < 0.03F) {
                i++;
            }

            llama.setStrength(i);
            llama.setVariant(this.random.nextBoolean() ? this.getVariant() : llama1.getVariant());
        }

        return llama;
    }

    @Nullable
    protected Llama makeNewLlama() {
        return EntityType.LLAMA.create(this.level(), EntitySpawnReason.BREEDING);
    }

    private void spit(LivingEntity target) {
        LlamaSpit llamaSpit = new LlamaSpit(this.level(), this);
        double d = target.getX() - this.getX();
        double d1 = target.getY(0.3333333333333333) - llamaSpit.getY();
        double d2 = target.getZ() - this.getZ();
        double d3 = Math.sqrt(d * d + d2 * d2) * 0.2F;
        if (this.level() instanceof ServerLevel serverLevel) {
            Projectile.spawnProjectileUsingShoot(llamaSpit, serverLevel, ItemStack.EMPTY, d, d1 + d3, d2, 1.5F, 10.0F);
        }

        if (!this.isSilent()) {
            this.level()
                .playSound(
                    null,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    SoundEvents.LLAMA_SPIT,
                    this.getSoundSource(),
                    1.0F,
                    1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F
                );
        }

        this.didSpit = true;
    }

    void setDidSpit(boolean didSpit) {
        this.didSpit = didSpit;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        int i = this.calculateFallDamage(fallDistance, multiplier);
        if (i <= 0) {
            return false;
        } else {
            if (fallDistance >= 6.0F) {
                this.hurt(source, i);
                if (this.isVehicle()) {
                    for (Entity entity : this.getIndirectPassengers()) {
                        entity.hurt(source, i);
                    }
                }
            }

            this.playBlockFallSound();
            return true;
        }
    }

    public void leaveCaravan() {
        if (this.caravanHead != null) {
            new org.purpurmc.purpur.event.entity.LlamaLeaveCaravanEvent((org.bukkit.entity.Llama) getBukkitEntity()).callEvent(); // Purpur - Llama API
            this.caravanHead.caravanTail = null;
        }

        this.caravanHead = null;
    }

    public void joinCaravan(Llama caravanHead) {
        if (!this.level().purpurConfig.llamaJoinCaravans || !shouldJoinCaravan || !new org.purpurmc.purpur.event.entity.LlamaJoinCaravanEvent((org.bukkit.entity.Llama) getBukkitEntity(), (org.bukkit.entity.Llama) caravanHead.getBukkitEntity()).callEvent()) return; // Purpur - Llama API // Purpur - Config to disable Llama caravans
        this.caravanHead = caravanHead;
        this.caravanHead.caravanTail = this;
    }

    public boolean hasCaravanTail() {
        return this.caravanTail != null;
    }

    public boolean inCaravan() {
        return this.caravanHead != null;
    }

    @Nullable
    public Llama getCaravanHead() {
        return this.caravanHead;
    }

    @Override
    protected double followLeashSpeed() {
        return 2.0;
    }

    @Override
    protected void followMommy(ServerLevel level) {
        if (!this.inCaravan() && this.isBaby()) {
            super.followMommy(level);
        }
    }

    @Override
    public boolean canEatGrass() {
        return false;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        this.spit(target);
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.75 * this.getEyeHeight(), this.getBbWidth() * 0.5);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
        return getDefaultPassengerAttachmentPoint(this, entity, dimensions.attachments());
    }

    static class LlamaAttackWolfGoal extends NearestAttackableTargetGoal<Wolf> {
        public LlamaAttackWolfGoal(Llama llama) {
            super(llama, Wolf.class, 16, false, true, (entity, level) -> !((Wolf)entity).isTame());
        }

        @Override
        protected double getFollowDistance() {
            return super.getFollowDistance() * 0.25;
        }
    }

    static class LlamaGroupData extends AgeableMob.AgeableMobGroupData {
        public final Llama.Variant variant;

        LlamaGroupData(Llama.Variant variant) {
            super(true);
            this.variant = variant;
        }
    }

    static class LlamaHurtByTargetGoal extends HurtByTargetGoal {
        public LlamaHurtByTargetGoal(Llama llama) {
            super(llama);
        }

        @Override
        public boolean canContinueToUse() {
            if (this.mob instanceof Llama llama && llama.didSpit) {
                llama.setDidSpit(false);
                return false;
            } else {
                return super.canContinueToUse();
            }
        }
    }

    public static enum Variant implements StringRepresentable {
        CREAMY(0, "creamy"),
        WHITE(1, "white"),
        BROWN(2, "brown"),
        GRAY(3, "gray");

        public static final Codec<Llama.Variant> CODEC = StringRepresentable.fromEnum(Llama.Variant::values);
        private static final IntFunction<Llama.Variant> BY_ID = ByIdMap.continuous(Llama.Variant::getId, values(), ByIdMap.OutOfBoundsStrategy.CLAMP);
        final int id;
        private final String name;

        private Variant(final int id, final String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return this.id;
        }

        public static Llama.Variant byId(int id) {
            return BY_ID.apply(id);
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
