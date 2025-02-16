package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

public class Drowned extends Zombie implements RangedAttackMob {
    public static final float NAUTILUS_SHELL_CHANCE = 0.03F;
    boolean searchingForLand;
    protected final WaterBoundPathNavigation waterNavigation;
    protected final GroundPathNavigation groundNavigation;

    public Drowned(EntityType<? extends Drowned> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new Drowned.DrownedMoveControl(this);
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.waterNavigation = new WaterBoundPathNavigation(this, level);
        this.groundNavigation = new GroundPathNavigation(this, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes().add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(1, new Drowned.DrownedGoToWaterGoal(this, 1.0));
        this.goalSelector.addGoal(2, new Drowned.DrownedTridentAttackGoal(this, 1.0, 40, 10.0F));
        this.goalSelector.addGoal(2, new Drowned.DrownedAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(5, new Drowned.DrownedGoToBeachGoal(this, 1.0));
        this.goalSelector.addGoal(6, new Drowned.DrownedSwimUpGoal(this, 1.0, this.level().getSeaLevel()));
        this.goalSelector.addGoal(7, new RandomStrollGoal(this, 1.0));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Drowned.class).setAlertOthers(ZombifiedPiglin.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (entity, level) -> this.okTarget(entity)));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Axolotl.class, true, false));
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Turtle.class, 10, true, false, Turtle.BABY_ON_LAND_SELECTOR));
    }

    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        spawnGroupData = super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
        if (this.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty() && level.getRandom().nextFloat() < 0.03F) {
            this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.NAUTILUS_SHELL));
            this.setGuaranteedDrop(EquipmentSlot.OFFHAND);
        }

        return spawnGroupData;
    }

    public static boolean checkDrownedSpawnRules(
        EntityType<Drowned> entityType, ServerLevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        if (!level.getFluidState(pos.below()).is(FluidTags.WATER) && !EntitySpawnReason.isSpawner(spawnReason)) {
            return false;
        } else {
            Holder<Biome> biome = level.getBiome(pos);
            boolean flag = level.getDifficulty() != Difficulty.PEACEFUL
                && (EntitySpawnReason.ignoresLightRequirements(spawnReason) || isDarkEnoughToSpawn(level, pos, random))
                && (EntitySpawnReason.isSpawner(spawnReason) || level.getFluidState(pos).is(FluidTags.WATER));
            if (!flag || !EntitySpawnReason.isSpawner(spawnReason) && spawnReason != EntitySpawnReason.REINFORCEMENT) {
                return biome.is(BiomeTags.MORE_FREQUENT_DROWNED_SPAWNS)
                    ? random.nextInt(15) == 0 && flag
                    : random.nextInt(40) == 0 && isDeepEnoughToSpawn(level, pos) && flag;
            } else {
                return true;
            }
        }
    }

    private static boolean isDeepEnoughToSpawn(LevelAccessor level, BlockPos pos) {
        return pos.getY() < level.getSeaLevel() - 5;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isInWater() ? SoundEvents.DROWNED_AMBIENT_WATER : SoundEvents.DROWNED_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return this.isInWater() ? SoundEvents.DROWNED_HURT_WATER : SoundEvents.DROWNED_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return this.isInWater() ? SoundEvents.DROWNED_DEATH_WATER : SoundEvents.DROWNED_DEATH;
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.DROWNED_STEP;
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.DROWNED_SWIM;
    }

    @Override
    protected boolean canSpawnInLiquids() {
        return true;
    }

    @Override
    protected ItemStack getSkull() {
        return ItemStack.EMPTY;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        if (random.nextFloat() > 0.9) {
            int randomInt = random.nextInt(16);
            if (randomInt < 10) {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
            } else {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
            }
        }
    }

    @Override
    protected boolean canReplaceCurrentItem(ItemStack newItem, ItemStack currentItem, EquipmentSlot slot) {
        return !currentItem.is(Items.NAUTILUS_SHELL) && super.canReplaceCurrentItem(newItem, currentItem, slot);
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    public boolean okTarget(@Nullable LivingEntity target) {
        return target != null && (!this.level().isDay() || target.isInWater());
    }

    @Override
    public boolean isPushedByFluid() {
        return !this.isSwimming();
    }

    boolean wantsToSwim() {
        if (this.searchingForLand) {
            return true;
        } else {
            LivingEntity target = this.getTarget();
            return target != null && target.isInWater();
        }
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isControlledByLocalInstance() && this.isUnderWater() && this.wantsToSwim()) {
            this.moveRelative(0.01F, travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    public void updateSwimming() {
        if (!this.level().isClientSide) {
            if (this.isEffectiveAi() && this.isUnderWater() && this.wantsToSwim()) {
                this.navigation = this.waterNavigation;
                this.setSwimming(true);
            } else {
                this.navigation = this.groundNavigation;
                this.setSwimming(false);
            }
        }
    }

    @Override
    public boolean isVisuallySwimming() {
        return this.isSwimming();
    }

    protected boolean closeToNextPos() {
        Path path = this.getNavigation().getPath();
        if (path != null) {
            BlockPos target = path.getTarget();
            if (target != null) {
                double d = this.distanceToSqr(target.getX(), target.getY(), target.getZ());
                if (d < 4.0) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        ItemStack mainHandItem = this.getMainHandItem();
        ItemStack itemStack = mainHandItem.is(Items.TRIDENT) ? mainHandItem : new ItemStack(Items.TRIDENT);
        ThrownTrident thrownTrident = new ThrownTrident(this.level(), this, itemStack);
        double d = target.getX() - this.getX();
        double d1 = target.getY(0.3333333333333333) - thrownTrident.getY();
        double d2 = target.getZ() - this.getZ();
        double squareRoot = Math.sqrt(d * d + d2 * d2);
        if (this.level() instanceof ServerLevel serverLevel) {
            Projectile.spawnProjectileUsingShoot(
                thrownTrident, serverLevel, itemStack, d, d1 + squareRoot * 0.2F, d2, 1.6F, 14 - this.level().getDifficulty().getId() * 4
            );
        }

        this.playSound(SoundEvents.DROWNED_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    @Override
    public TagKey<Item> getPreferredWeaponType() {
        return ItemTags.DROWNED_PREFERRED_WEAPONS;
    }

    public void setSearchingForLand(boolean searchingForLand) {
        this.searchingForLand = searchingForLand;
    }

    static class DrownedAttackGoal extends ZombieAttackGoal {
        private final Drowned drowned;

        public DrownedAttackGoal(Drowned drowned, double speedModifier, boolean followingTargetEvenIfNotSeen) {
            super(drowned, speedModifier, followingTargetEvenIfNotSeen);
            this.drowned = drowned;
        }

        @Override
        public boolean canUse() {
            return super.canUse() && this.drowned.okTarget(this.drowned.getTarget());
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && this.drowned.okTarget(this.drowned.getTarget());
        }
    }

    static class DrownedGoToBeachGoal extends MoveToBlockGoal {
        private final Drowned drowned;

        public DrownedGoToBeachGoal(Drowned drowned, double speedModifier) {
            super(drowned, speedModifier, 8, 2);
            this.drowned = drowned;
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !this.drowned.level().isDay() && this.drowned.isInWater() && this.drowned.getY() >= this.drowned.level().getSeaLevel() - 3;
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse();
        }

        @Override
        protected boolean isValidTarget(LevelReader level, BlockPos pos) {
            BlockPos blockPos = pos.above();
            return level.isEmptyBlock(blockPos) && level.isEmptyBlock(blockPos.above()) && level.getBlockState(pos).entityCanStandOn(level, pos, this.drowned);
        }

        @Override
        public void start() {
            this.drowned.setSearchingForLand(false);
            this.drowned.navigation = this.drowned.groundNavigation;
            super.start();
        }

        @Override
        public void stop() {
            super.stop();
        }
    }

    static class DrownedGoToWaterGoal extends Goal {
        private final PathfinderMob mob;
        private double wantedX;
        private double wantedY;
        private double wantedZ;
        private final double speedModifier;
        private final Level level;

        public DrownedGoToWaterGoal(PathfinderMob mob, double speedModifier) {
            this.mob = mob;
            this.speedModifier = speedModifier;
            this.level = mob.level();
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!this.level.isDay()) {
                return false;
            } else if (this.mob.isInWater()) {
                return false;
            } else {
                Vec3 waterPos = this.getWaterPos();
                if (waterPos == null) {
                    return false;
                } else {
                    this.wantedX = waterPos.x;
                    this.wantedY = waterPos.y;
                    this.wantedZ = waterPos.z;
                    return true;
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            return !this.mob.getNavigation().isDone();
        }

        @Override
        public void start() {
            this.mob.getNavigation().moveTo(this.wantedX, this.wantedY, this.wantedZ, this.speedModifier);
        }

        @Nullable
        private Vec3 getWaterPos() {
            RandomSource random = this.mob.getRandom();
            BlockPos blockPos = this.mob.blockPosition();

            for (int i = 0; i < 10; i++) {
                BlockPos blockPos1 = blockPos.offset(random.nextInt(20) - 10, 2 - random.nextInt(8), random.nextInt(20) - 10);
                if (this.level.getBlockState(blockPos1).is(Blocks.WATER)) {
                    return Vec3.atBottomCenterOf(blockPos1);
                }
            }

            return null;
        }
    }

    static class DrownedMoveControl extends MoveControl {
        private final Drowned drowned;

        public DrownedMoveControl(Drowned mob) {
            super(mob);
            this.drowned = mob;
        }

        @Override
        public void tick() {
            LivingEntity target = this.drowned.getTarget();
            if (this.drowned.wantsToSwim() && this.drowned.isInWater()) {
                if (target != null && target.getY() > this.drowned.getY() || this.drowned.searchingForLand) {
                    this.drowned.setDeltaMovement(this.drowned.getDeltaMovement().add(0.0, 0.002, 0.0));
                }

                if (this.operation != MoveControl.Operation.MOVE_TO || this.drowned.getNavigation().isDone()) {
                    this.drowned.setSpeed(0.0F);
                    return;
                }

                double d = this.wantedX - this.drowned.getX();
                double d1 = this.wantedY - this.drowned.getY();
                double d2 = this.wantedZ - this.drowned.getZ();
                double squareRoot = Math.sqrt(d * d + d1 * d1 + d2 * d2);
                d1 /= squareRoot;
                float f = (float)(Mth.atan2(d2, d) * 180.0F / (float)Math.PI) - 90.0F;
                this.drowned.setYRot(this.rotlerp(this.drowned.getYRot(), f, 90.0F));
                this.drowned.yBodyRot = this.drowned.getYRot();
                float f1 = (float)(this.speedModifier * this.drowned.getAttributeValue(Attributes.MOVEMENT_SPEED));
                float f2 = Mth.lerp(0.125F, this.drowned.getSpeed(), f1);
                this.drowned.setSpeed(f2);
                this.drowned.setDeltaMovement(this.drowned.getDeltaMovement().add(f2 * d * 0.005, f2 * d1 * 0.1, f2 * d2 * 0.005));
            } else {
                if (!this.drowned.onGround()) {
                    this.drowned.setDeltaMovement(this.drowned.getDeltaMovement().add(0.0, -0.008, 0.0));
                }

                super.tick();
            }
        }
    }

    static class DrownedSwimUpGoal extends Goal {
        private final Drowned drowned;
        private final double speedModifier;
        private final int seaLevel;
        private boolean stuck;

        public DrownedSwimUpGoal(Drowned drowned, double speedModifier, int seaLevel) {
            this.drowned = drowned;
            this.speedModifier = speedModifier;
            this.seaLevel = seaLevel;
        }

        @Override
        public boolean canUse() {
            return !this.drowned.level().isDay() && this.drowned.isInWater() && this.drowned.getY() < this.seaLevel - 2;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse() && !this.stuck;
        }

        @Override
        public void tick() {
            if (this.drowned.getY() < this.seaLevel - 1 && (this.drowned.getNavigation().isDone() || this.drowned.closeToNextPos())) {
                Vec3 posTowards = DefaultRandomPos.getPosTowards(
                    this.drowned, 4, 8, new Vec3(this.drowned.getX(), this.seaLevel - 1, this.drowned.getZ()), (float) (Math.PI / 2)
                );
                if (posTowards == null) {
                    this.stuck = true;
                    return;
                }

                this.drowned.getNavigation().moveTo(posTowards.x, posTowards.y, posTowards.z, this.speedModifier);
            }
        }

        @Override
        public void start() {
            this.drowned.setSearchingForLand(true);
            this.stuck = false;
        }

        @Override
        public void stop() {
            this.drowned.setSearchingForLand(false);
        }
    }

    static class DrownedTridentAttackGoal extends RangedAttackGoal {
        private final Drowned drowned;

        public DrownedTridentAttackGoal(RangedAttackMob rangedAttackMob, double speedModifier, int attackInterval, float attackRadius) {
            super(rangedAttackMob, speedModifier, attackInterval, attackRadius);
            this.drowned = (Drowned)rangedAttackMob;
        }

        @Override
        public boolean canUse() {
            return super.canUse() && this.drowned.getMainHandItem().is(Items.TRIDENT);
        }

        @Override
        public void start() {
            super.start();
            this.drowned.setAggressive(true);
            this.drowned.startUsingItem(InteractionHand.MAIN_HAND);
        }

        @Override
        public void stop() {
            super.stop();
            this.drowned.stopUsingItem();
            this.drowned.setAggressive(false);
        }
    }
}
