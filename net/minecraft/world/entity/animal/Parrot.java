package net.minecraft.world.entity.animal;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Difficulty;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowMobGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LandOnOwnersShoulderGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

public class Parrot extends ShoulderRidingEntity implements VariantHolder<Parrot.Variant>, FlyingAnimal {
    private static final EntityDataAccessor<Integer> DATA_VARIANT_ID = SynchedEntityData.defineId(Parrot.class, EntityDataSerializers.INT);
    private static final Predicate<Mob> NOT_PARROT_PREDICATE = new Predicate<Mob>() {
        @Override
        public boolean test(@Nullable Mob mob) {
            return mob != null && Parrot.MOB_SOUND_MAP.containsKey(mob.getType());
        }
    };
    static final Map<EntityType<?>, SoundEvent> MOB_SOUND_MAP = Util.make(Maps.newHashMap(), map -> {
        map.put(EntityType.BLAZE, SoundEvents.PARROT_IMITATE_BLAZE);
        map.put(EntityType.BOGGED, SoundEvents.PARROT_IMITATE_BOGGED);
        map.put(EntityType.BREEZE, SoundEvents.PARROT_IMITATE_BREEZE);
        map.put(EntityType.CAVE_SPIDER, SoundEvents.PARROT_IMITATE_SPIDER);
        map.put(EntityType.CREAKING, SoundEvents.PARROT_IMITATE_CREAKING);
        map.put(EntityType.CREEPER, SoundEvents.PARROT_IMITATE_CREEPER);
        map.put(EntityType.DROWNED, SoundEvents.PARROT_IMITATE_DROWNED);
        map.put(EntityType.ELDER_GUARDIAN, SoundEvents.PARROT_IMITATE_ELDER_GUARDIAN);
        map.put(EntityType.ENDER_DRAGON, SoundEvents.PARROT_IMITATE_ENDER_DRAGON);
        map.put(EntityType.ENDERMITE, SoundEvents.PARROT_IMITATE_ENDERMITE);
        map.put(EntityType.EVOKER, SoundEvents.PARROT_IMITATE_EVOKER);
        map.put(EntityType.GHAST, SoundEvents.PARROT_IMITATE_GHAST);
        map.put(EntityType.GUARDIAN, SoundEvents.PARROT_IMITATE_GUARDIAN);
        map.put(EntityType.HOGLIN, SoundEvents.PARROT_IMITATE_HOGLIN);
        map.put(EntityType.HUSK, SoundEvents.PARROT_IMITATE_HUSK);
        map.put(EntityType.ILLUSIONER, SoundEvents.PARROT_IMITATE_ILLUSIONER);
        map.put(EntityType.MAGMA_CUBE, SoundEvents.PARROT_IMITATE_MAGMA_CUBE);
        map.put(EntityType.PHANTOM, SoundEvents.PARROT_IMITATE_PHANTOM);
        map.put(EntityType.PIGLIN, SoundEvents.PARROT_IMITATE_PIGLIN);
        map.put(EntityType.PIGLIN_BRUTE, SoundEvents.PARROT_IMITATE_PIGLIN_BRUTE);
        map.put(EntityType.PILLAGER, SoundEvents.PARROT_IMITATE_PILLAGER);
        map.put(EntityType.RAVAGER, SoundEvents.PARROT_IMITATE_RAVAGER);
        map.put(EntityType.SHULKER, SoundEvents.PARROT_IMITATE_SHULKER);
        map.put(EntityType.SILVERFISH, SoundEvents.PARROT_IMITATE_SILVERFISH);
        map.put(EntityType.SKELETON, SoundEvents.PARROT_IMITATE_SKELETON);
        map.put(EntityType.SLIME, SoundEvents.PARROT_IMITATE_SLIME);
        map.put(EntityType.SPIDER, SoundEvents.PARROT_IMITATE_SPIDER);
        map.put(EntityType.STRAY, SoundEvents.PARROT_IMITATE_STRAY);
        map.put(EntityType.VEX, SoundEvents.PARROT_IMITATE_VEX);
        map.put(EntityType.VINDICATOR, SoundEvents.PARROT_IMITATE_VINDICATOR);
        map.put(EntityType.WARDEN, SoundEvents.PARROT_IMITATE_WARDEN);
        map.put(EntityType.WITCH, SoundEvents.PARROT_IMITATE_WITCH);
        map.put(EntityType.WITHER, SoundEvents.PARROT_IMITATE_WITHER);
        map.put(EntityType.WITHER_SKELETON, SoundEvents.PARROT_IMITATE_WITHER_SKELETON);
        map.put(EntityType.ZOGLIN, SoundEvents.PARROT_IMITATE_ZOGLIN);
        map.put(EntityType.ZOMBIE, SoundEvents.PARROT_IMITATE_ZOMBIE);
        map.put(EntityType.ZOMBIE_VILLAGER, SoundEvents.PARROT_IMITATE_ZOMBIE_VILLAGER);
    });
    public float flap;
    public float flapSpeed;
    public float oFlapSpeed;
    public float oFlap;
    private float flapping = 1.0F;
    private float nextFlap = 1.0F;
    private boolean partyParrot;
    @Nullable
    private BlockPos jukebox;

    public Parrot(EntityType<? extends Parrot> entityType, Level level) {
        super(entityType, level);
        // Purpur start - Ridables
        final org.purpurmc.purpur.controller.FlyingWithSpacebarMoveControllerWASD flyingController = new org.purpurmc.purpur.controller.FlyingWithSpacebarMoveControllerWASD(this, 0.3F);
        class ParrotMoveControl extends FlyingMoveControl {
            public ParrotMoveControl(Mob entity, int maxPitchChange, boolean noGravity) {
                super(entity, maxPitchChange, noGravity);
            }

            @Override
            public void tick() {
                if (mob.getRider() != null && mob.isControllable()) {
                    flyingController.purpurTick(mob.getRider());
                } else {
                    super.tick();
                }
            }

            @Override
            public boolean hasWanted() {
                return mob.getRider() != null && mob.isControllable() ? getForwardMot() != 0 || getStrafeMot() != 0 : super.hasWanted();
            }
        }
        this.moveControl = new ParrotMoveControl(this, 10, false);
        // Purpur end - Ridables
        this.setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.COCOA, -1.0F);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.parrotRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.parrotRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.parrotControllable;
    }

    @Override
    public double getMaxY() {
        return level().purpurConfig.parrotMaxY;
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
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.parrotMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.parrotScale);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Make entity breeding times configurable
    @Override
    public int getPurpurBreedTime() {
        return 6000;
    }
    // Purpur end - Make entity breeding times configurable

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.parrotTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        this.setVariant(Util.getRandom(Parrot.Variant.values(), level.getRandom()));
        if (spawnGroupData == null) {
            spawnGroupData = new AgeableMob.AgeableMobGroupData(false);
        }

        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    protected void registerGoals() {
        //this.goalSelector.addGoal(0, new TamableAnimal.TamableAnimalPanicGoal(1.25)); // Purpur - move down
        this.goalSelector.addGoal(0, new FloatGoal(this));
        if (this.level().purpurConfig.parrotBreedable) this.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.BreedGoal(this, 1.0D)); // Purpur - Breedable parrots
        this.goalSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.goalSelector.addGoal(1, new TamableAnimal.TamableAnimalPanicGoal(1.25D)); // Purpur - Ridables
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.0, 5.0F, 1.0F));
        this.goalSelector.addGoal(2, new Parrot.ParrotWanderGoal(this, 1.0));
        this.goalSelector.addGoal(3, new LandOnOwnersShoulderGoal(this));
        this.goalSelector.addGoal(3, new FollowMobGoal(this, 1.0, 3.0F, 7.0F));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 6.0)
            .add(Attributes.FLYING_SPEED, 0.4F)
            .add(Attributes.MOVEMENT_SPEED, 0.2F)
            .add(Attributes.ATTACK_DAMAGE, 3.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation flyingPathNavigation = new FlyingPathNavigation(this, level);
        flyingPathNavigation.setCanOpenDoors(false);
        flyingPathNavigation.setCanFloat(true);
        return flyingPathNavigation;
    }

    @Override
    public void aiStep() {
        if (this.jukebox == null || !this.jukebox.closerToCenterThan(this.position(), 3.46) || !this.level().getBlockState(this.jukebox).is(Blocks.JUKEBOX)) {
            this.partyParrot = false;
            this.jukebox = null;
        }

        if (this.level().random.nextInt(400) == 0) {
            imitateNearbyMobs(this.level(), this);
        }

        super.aiStep();
        this.calculateFlapping();
    }

    @Override
    public void setRecordPlayingNearby(BlockPos pos, boolean isPartying) {
        this.jukebox = pos;
        this.partyParrot = isPartying;
    }

    public boolean isPartyParrot() {
        return this.partyParrot;
    }

    private void calculateFlapping() {
        this.oFlap = this.flap;
        this.oFlapSpeed = this.flapSpeed;
        this.flapSpeed = this.flapSpeed + (!this.onGround() && !this.isPassenger() ? 4 : -1) * 0.3F;
        this.flapSpeed = Mth.clamp(this.flapSpeed, 0.0F, 1.0F);
        if (!this.onGround() && this.flapping < 1.0F) {
            this.flapping = 1.0F;
        }

        this.flapping *= 0.9F;
        Vec3 deltaMovement = this.getDeltaMovement();
        if (!this.onGround() && deltaMovement.y < 0.0) {
            this.setDeltaMovement(deltaMovement.multiply(1.0, 0.6, 1.0));
        }

        this.flap = this.flap + this.flapping * 2.0F;
    }

    public static boolean imitateNearbyMobs(Level level, Entity parrot) {
        if (parrot.isAlive() && !parrot.isSilent() && level.random.nextInt(2) == 0) {
            List<Mob> entitiesOfClass = level.getEntitiesOfClass(Mob.class, parrot.getBoundingBox().inflate(20.0), NOT_PARROT_PREDICATE);
            if (!entitiesOfClass.isEmpty()) {
                Mob mob = entitiesOfClass.get(level.random.nextInt(entitiesOfClass.size()));
                if (!mob.isSilent()) {
                    SoundEvent imitatedSound = getImitatedSound(mob.getType());
                    level.playSound(null, parrot.getX(), parrot.getY(), parrot.getZ(), imitatedSound, parrot.getSoundSource(), 0.7F, getPitch(level.random));
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (!this.isTame() && itemInHand.is(ItemTags.PARROT_FOOD)) {
            this.usePlayerItem(player, hand, itemInHand);
            if (!this.isSilent()) {
                this.level()
                    .playSound(
                        null,
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        SoundEvents.PARROT_EAT,
                        this.getSoundSource(),
                        1.0F,
                        1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F
                    );
            }

            if (!this.level().isClientSide) {
                if (this.level().purpurConfig.alwaysTameInCreative && player.hasInfiniteMaterials() || (this.random.nextInt(10) == 0 && !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTameEvent(this, player).isCancelled())) { // CraftBukkit // Purpur - Config to always tame in Creative
                    this.tame(player);
                    this.level().broadcastEntityEvent(this, (byte)7);
                } else {
                    this.level().broadcastEntityEvent(this, (byte)6);
                }
            }

            if (this.level().purpurConfig.parrotBreedable) return super.mobInteract(player, hand); // Purpur - Breedable parrots
            return InteractionResult.SUCCESS;
        } else if (!itemInHand.is(ItemTags.PARROT_POISONOUS_FOOD)) {
            if (!this.isFlying() && this.isTame() && this.isOwnedBy(player)) {
                if (!this.level().isClientSide) {
                    this.setOrderedToSit(!this.isOrderedToSit());
                }

                return InteractionResult.SUCCESS;
            } else {
                return super.mobInteract(player, hand);
            }
        } else {
            this.usePlayerItem(player, hand, itemInHand);
            this.addEffect(new MobEffectInstance(MobEffects.POISON, 900), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.FOOD); // CraftBukkit
            if (player.isCreative() || !this.isInvulnerable()) {
                this.hurt(this.damageSources().playerAttack(player), Float.MAX_VALUE);
            }

            return InteractionResult.SUCCESS;
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return this.level().purpurConfig.parrotBreedable && stack.is(ItemTags.PARROT_FOOD); // Purpur - Breedable parrots
    }

    public static boolean checkParrotSpawnRules(
        EntityType<Parrot> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return level.getBlockState(pos.below()).is(BlockTags.PARROTS_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean canMate(Animal otherAnimal) {
        return super.canMate(otherAnimal); // Purpur - Breedable parrots
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return level.purpurConfig.parrotBreedable ? EntityType.PARROT.create(level, EntitySpawnReason.BREEDING) : null; // Purpur - Breedable parrots
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound() {
        return getAmbient(this.level(), this.level().random);
    }

    public static SoundEvent getAmbient(Level level, RandomSource random) {
        if (level.getDifficulty() != Difficulty.PEACEFUL && random.nextInt(1000) == 0) {
            List<EntityType<?>> list = Lists.newArrayList(MOB_SOUND_MAP.keySet());
            return getImitatedSound(list.get(random.nextInt(list.size())));
        } else {
            return SoundEvents.PARROT_AMBIENT;
        }
    }

    private static SoundEvent getImitatedSound(EntityType<?> type) {
        return MOB_SOUND_MAP.getOrDefault(type, SoundEvents.PARROT_AMBIENT);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.PARROT_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.PARROT_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        this.playSound(SoundEvents.PARROT_STEP, 0.15F, 1.0F);
    }

    @Override
    protected boolean isFlapping() {
        return this.flyDist > this.nextFlap;
    }

    @Override
    protected void onFlap() {
        this.playSound(SoundEvents.PARROT_FLY, 0.15F, 1.0F);
        this.nextFlap = this.flyDist + this.flapSpeed / 2.0F;
    }

    @Override
    public float getVoicePitch() {
        return getPitch(this.random);
    }

    public static float getPitch(RandomSource random) {
        return (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }

    @Override
    public boolean isCollidable(boolean ignoreClimbing) { // Paper - Climbing should not bypass cramming gamerule
        return super.isCollidable(ignoreClimbing); // CraftBukkit - collidable API // Paper - Climbing should not bypass cramming gamerule
    }

    @Override
    protected void doPush(Entity entity) {
        if (!(entity instanceof Player)) {
            super.doPush(entity);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableTo(level, damageSource)) {
            return false;
        } else {
            // CraftBukkit start
            if (!super.hurtServer(level, damageSource, amount)) {
                return false;
            }
            this.setOrderedToSit(false);
            return true;
            // CraftBukkit
        }
    }

    @Override
    public Parrot.Variant getVariant() {
        return Parrot.Variant.byId(this.entityData.get(DATA_VARIANT_ID));
    }

    @Override
    public void setVariant(Parrot.Variant variant) {
        this.entityData.set(DATA_VARIANT_ID, variant.id);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT_ID, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Variant", this.getVariant().id);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setVariant(Parrot.Variant.byId(compound.getInt("Variant")));
    }

    @Override
    public boolean isFlying() {
        return !this.onGround();
    }

    @Override
    protected boolean canFlyToOwner() {
        return true;
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.5F * this.getEyeHeight(), this.getBbWidth() * 0.4F);
    }

    static class ParrotWanderGoal extends WaterAvoidingRandomFlyingGoal {
        public ParrotWanderGoal(PathfinderMob mob, double speedModifier) {
            super(mob, speedModifier);
        }

        @Nullable
        @Override
        protected Vec3 getPosition() {
            Vec3 vec3 = null;
            if (this.mob.isInWater()) {
                vec3 = LandRandomPos.getPos(this.mob, 15, 15);
            }

            if (this.mob.getRandom().nextFloat() >= this.probability) {
                vec3 = this.getTreePos();
            }

            return vec3 == null ? super.getPosition() : vec3;
        }

        @Nullable
        private Vec3 getTreePos() {
            BlockPos blockPos = this.mob.blockPosition();
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos mutableBlockPos1 = new BlockPos.MutableBlockPos();

            for (BlockPos blockPos1 : BlockPos.betweenClosed(
                Mth.floor(this.mob.getX() - 3.0),
                Mth.floor(this.mob.getY() - 6.0),
                Mth.floor(this.mob.getZ() - 3.0),
                Mth.floor(this.mob.getX() + 3.0),
                Mth.floor(this.mob.getY() + 6.0),
                Mth.floor(this.mob.getZ() + 3.0)
            )) {
                if (!blockPos.equals(blockPos1)) {
                    BlockState blockState = this.mob.level().getBlockState(mutableBlockPos1.setWithOffset(blockPos1, Direction.DOWN));
                    boolean flag = blockState.getBlock() instanceof LeavesBlock || blockState.is(BlockTags.LOGS);
                    if (flag
                        && this.mob.level().isEmptyBlock(blockPos1)
                        && this.mob.level().isEmptyBlock(mutableBlockPos.setWithOffset(blockPos1, Direction.UP))) {
                        return Vec3.atBottomCenterOf(blockPos1);
                    }
                }
            }

            return null;
        }
    }

    public static enum Variant implements StringRepresentable {
        RED_BLUE(0, "red_blue"),
        BLUE(1, "blue"),
        GREEN(2, "green"),
        YELLOW_BLUE(3, "yellow_blue"),
        GRAY(4, "gray");

        public static final Codec<Parrot.Variant> CODEC = StringRepresentable.fromEnum(Parrot.Variant::values);
        private static final IntFunction<Parrot.Variant> BY_ID = ByIdMap.continuous(Parrot.Variant::getId, values(), ByIdMap.OutOfBoundsStrategy.CLAMP);
        final int id;
        private final String name;

        private Variant(final int id, final String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return this.id;
        }

        public static Parrot.Variant byId(int id) {
            return BY_ID.apply(id);
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
