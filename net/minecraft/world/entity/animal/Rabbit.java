package net.minecraft.world.entity.animal;

import com.mojang.serialization.Codec;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.ClimbOnTopOfPowderSnowGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarrotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class Rabbit extends Animal implements VariantHolder<Rabbit.Variant> {
    public static final double STROLL_SPEED_MOD = 0.6;
    public static final double BREED_SPEED_MOD = 0.8;
    public static final double FOLLOW_SPEED_MOD = 1.0;
    public static final double FLEE_SPEED_MOD = 2.2;
    public static final double ATTACK_SPEED_MOD = 1.4;
    private static final EntityDataAccessor<Integer> DATA_TYPE_ID = SynchedEntityData.defineId(Rabbit.class, EntityDataSerializers.INT);
    private static final ResourceLocation KILLER_BUNNY = ResourceLocation.withDefaultNamespace("killer_bunny");
    private static final int DEFAULT_ATTACK_POWER = 3;
    private static final int EVIL_ATTACK_POWER_INCREMENT = 5;
    private static final ResourceLocation EVIL_ATTACK_POWER_MODIFIER = ResourceLocation.withDefaultNamespace("evil");
    private static final int EVIL_ARMOR_VALUE = 8;
    private static final int MORE_CARROTS_DELAY = 40;
    private int jumpTicks;
    private int jumpDuration;
    private boolean wasOnGround;
    private int jumpDelayTicks;
    public int moreCarrotTicks;
    private boolean actualJump; // Purpur - Ridables

    public Rabbit(EntityType<? extends Rabbit> entityType, Level level) {
        super(entityType, level);
        this.jumpControl = new Rabbit.RabbitJumpControl(this);
        this.moveControl = new Rabbit.RabbitMoveControl(this);
        //this.setSpeedModifier(0.0); // CraftBukkit
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.rabbitRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.rabbitRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.rabbitControllable;
    }

    @Override
    public boolean onSpacebar() {
        if (onGround) {
            actualJump = true;
            jumpFromGround();
            actualJump = false;
        }
        return true;
    }

    private void handleJumping() {
        if (onGround) {
            RabbitJumpControl jumpController = (RabbitJumpControl) jumpControl;
            if (!wasOnGround) {
                setJumping(false);
                jumpController.setCanJump(false);
            }
            if (!jumpController.wantJump()) {
                if (moveControl.hasWanted()) {
                    startJumping();
                }
            } else if (!jumpController.canJump()) {
                jumpController.setCanJump(true);
            }
        }
        wasOnGround = onGround;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.rabbitMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.rabbitScale);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Make entity breeding times configurable
    @Override
    public int getPurpurBreedTime() {
        return this.level().purpurConfig.rabbitBreedingTicks;
    }
    // Purpur end - Make entity breeding times configurable

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.rabbitTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.rabbitAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    public void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.goalSelector.addGoal(1, new ClimbOnTopOfPowderSnowGoal(this, this.level()));
        this.goalSelector.addGoal(1, new Rabbit.RabbitPanicGoal(this, 2.2));
        this.goalSelector.addGoal(2, new BreedGoal(this, 0.8));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.0, itemStack -> itemStack.is(ItemTags.RABBIT_FOOD), false));
        this.goalSelector.addGoal(4, new Rabbit.RabbitAvoidEntityGoal<>(this, Player.class, 8.0F, 2.2, 2.2));
        this.goalSelector.addGoal(4, new Rabbit.RabbitAvoidEntityGoal<>(this, Wolf.class, 10.0F, 2.2, 2.2));
        this.goalSelector.addGoal(4, new Rabbit.RabbitAvoidEntityGoal<>(this, Monster.class, 4.0F, 2.2, 2.2));
        this.goalSelector.addGoal(5, new Rabbit.RaidGardenGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 10.0F));
    }

    @Override
    protected float getJumpPower() {
        // Purpur start - Ridables
        if (getRider() != null && this.isControllable()) {
            if (getForwardMot() < 0) {
                setSpeed(getForwardMot() * 2F);
            }
            return actualJump ? 0.5F : 0.3F;
        }
        // Purpur end - Ridables
        float f = 0.3F;
        if (this.moveControl.getSpeedModifier() <= 0.6) {
            f = 0.2F;
        }

        Path path = this.navigation.getPath();
        if (path != null && !path.isDone()) {
            Vec3 nextEntityPos = path.getNextEntityPos(this);
            if (nextEntityPos.y > this.getY() + 0.5) {
                f = 0.5F;
            }
        }

        if (this.horizontalCollision || this.jumping && this.moveControl.getWantedY() > this.getY() + 0.5) {
            f = 0.5F;
        }

        return super.getJumpPower(f / 0.42F);
    }

    @Override
    public void jumpFromGround() {
        super.jumpFromGround();
        double speedModifier = this.moveControl.getSpeedModifier();
        if (speedModifier > 0.0) {
            double d = this.getDeltaMovement().horizontalDistanceSqr();
            if (d < 0.01) {
                this.moveRelative(0.1F, new Vec3(0.0, 0.0, 1.0));
            }
        }

        if (!this.level().isClientSide) {
            this.level().broadcastEntityEvent(this, (byte)1);
        }
    }

    public float getJumpCompletion(float partialTick) {
        return this.jumpDuration == 0 ? 0.0F : (this.jumpTicks + partialTick) / this.jumpDuration;
    }

    public void setSpeedModifier(double speedModifier) {
        this.getNavigation().setSpeedModifier(speedModifier);
        this.moveControl.setWantedPosition(this.moveControl.getWantedX(), this.moveControl.getWantedY(), this.moveControl.getWantedZ(), speedModifier);
    }

    @Override
    public void setJumping(boolean jumping) {
        super.setJumping(jumping);
        if (jumping) {
            this.playSound(this.getJumpSound(), this.getSoundVolume(), ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) * 0.8F);
        }
    }

    public void startJumping() {
        this.setJumping(true);
        this.jumpDuration = 10;
        this.jumpTicks = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TYPE_ID, Rabbit.Variant.BROWN.id);
    }

    @Override
    public void customServerAiStep(ServerLevel level) {
        // Purpur start - Ridables
        if (getRider() != null && this.isControllable()) {
            handleJumping();
            return;
        }
        // Purpur end - Ridables
        if (this.jumpDelayTicks > 0) {
            this.jumpDelayTicks--;
        }

        if (this.moreCarrotTicks > 0) {
            this.moreCarrotTicks = this.moreCarrotTicks - this.random.nextInt(3);
            if (this.moreCarrotTicks < 0) {
                this.moreCarrotTicks = 0;
            }
        }

        if (this.onGround()) {
            if (!this.wasOnGround) {
                this.setJumping(false);
                this.checkLandingDelay();
            }

            if (this.getVariant() == Rabbit.Variant.EVIL && this.jumpDelayTicks == 0) {
                LivingEntity target = this.getTarget();
                if (target != null && this.distanceToSqr(target) < 16.0) {
                    this.facePoint(target.getX(), target.getZ());
                    this.moveControl.setWantedPosition(target.getX(), target.getY(), target.getZ(), this.moveControl.getSpeedModifier());
                    this.startJumping();
                    this.wasOnGround = true;
                }
            }

            Rabbit.RabbitJumpControl rabbitJumpControl = (Rabbit.RabbitJumpControl)this.jumpControl;
            if (!rabbitJumpControl.wantJump()) {
                if (this.moveControl.hasWanted() && this.jumpDelayTicks == 0) {
                    Path path = this.navigation.getPath();
                    Vec3 vec3 = new Vec3(this.moveControl.getWantedX(), this.moveControl.getWantedY(), this.moveControl.getWantedZ());
                    if (path != null && !path.isDone()) {
                        vec3 = path.getNextEntityPos(this);
                    }

                    this.facePoint(vec3.x, vec3.z);
                    this.startJumping();
                }
            } else if (!rabbitJumpControl.canJump()) {
                this.enableJumpControl();
            }
        }

        this.wasOnGround = this.onGround();
    }

    @Override
    public boolean canSpawnSprintParticle() {
        return false;
    }

    private void facePoint(double x, double z) {
        this.setYRot((float)(Mth.atan2(z - this.getZ(), x - this.getX()) * 180.0F / (float)Math.PI) - 90.0F);
    }

    private void enableJumpControl() {
        ((Rabbit.RabbitJumpControl)this.jumpControl).setCanJump(true);
    }

    private void disableJumpControl() {
        ((Rabbit.RabbitJumpControl)this.jumpControl).setCanJump(false);
    }

    private void setLandingDelay() {
        if (this.moveControl.getSpeedModifier() < 2.2) {
            this.jumpDelayTicks = 10;
        } else {
            this.jumpDelayTicks = 1;
        }
    }

    private void checkLandingDelay() {
        this.setLandingDelay();
        this.disableJumpControl();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.jumpTicks != this.jumpDuration) {
            this.jumpTicks++;
        } else if (this.jumpDuration != 0) {
            this.jumpTicks = 0;
            this.jumpDuration = 0;
            this.setJumping(false);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 3.0).add(Attributes.MOVEMENT_SPEED, 0.3F).add(Attributes.ATTACK_DAMAGE, 3.0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("RabbitType", this.getVariant().id);
        compound.putInt("MoreCarrotTicks", this.moreCarrotTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setVariant(Rabbit.Variant.byId(compound.getInt("RabbitType")));
        this.moreCarrotTicks = compound.getInt("MoreCarrotTicks");
    }

    protected SoundEvent getJumpSound() {
        return SoundEvents.RABBIT_JUMP;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RABBIT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.RABBIT_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.RABBIT_DEATH;
    }

    @Override
    public void playAttackSound() {
        if (this.getVariant() == Rabbit.Variant.EVIL) {
            this.playSound(SoundEvents.RABBIT_ATTACK, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
        }
    }

    @Override
    public SoundSource getSoundSource() {
        return this.getVariant() == Rabbit.Variant.EVIL ? SoundSource.HOSTILE : SoundSource.NEUTRAL;
    }

    @Nullable
    @Override
    public Rabbit getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        Rabbit rabbit = EntityType.RABBIT.create(level, EntitySpawnReason.BREEDING);
        if (rabbit != null) {
            Rabbit.Variant randomRabbitVariant = getRandomRabbitVariant(level, this.blockPosition());
            if (this.random.nextInt(20) != 0) {
                if (otherParent instanceof Rabbit rabbit1 && this.random.nextBoolean()) {
                    randomRabbitVariant = rabbit1.getVariant();
                } else {
                    randomRabbitVariant = this.getVariant();
                }
            }

            rabbit.setVariant(randomRabbitVariant);
        }

        return rabbit;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.RABBIT_FOOD);
    }

    @Override
    public Rabbit.Variant getVariant() {
        return Rabbit.Variant.byId(this.entityData.get(DATA_TYPE_ID));
    }

    @Override
    public void setVariant(Rabbit.Variant variant) {
        if (variant == Rabbit.Variant.EVIL) {
            this.getAttribute(Attributes.ARMOR).setBaseValue(8.0);
            this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.4, true));
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Wolf.class, true));
            this.getAttribute(Attributes.ATTACK_DAMAGE)
                .addOrUpdateTransientModifier(new AttributeModifier(EVIL_ATTACK_POWER_MODIFIER, 5.0, AttributeModifier.Operation.ADD_VALUE));
            if (!this.hasCustomName()) {
                this.setCustomName(Component.translatable(Util.makeDescriptionId("entity", KILLER_BUNNY)));
            }
        } else {
            this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(EVIL_ATTACK_POWER_MODIFIER);
        }

        this.entityData.set(DATA_TYPE_ID, variant.id);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        Rabbit.Variant randomRabbitVariant = getRandomRabbitVariant(level, this.blockPosition());
        if (spawnGroupData instanceof Rabbit.RabbitGroupData) {
            randomRabbitVariant = ((Rabbit.RabbitGroupData)spawnGroupData).variant;
        } else {
            spawnGroupData = new Rabbit.RabbitGroupData(randomRabbitVariant);
        }

        this.setVariant(randomRabbitVariant);

        // Purpur start - Special mobs naturally spawn
        if (randomRabbitVariant != Variant.EVIL && level.getLevel().purpurConfig.rabbitNaturalToast > 0D && random.nextDouble() <= level.getLevel().purpurConfig.rabbitNaturalToast) {
            setCustomName(Component.translatable("Toast"));
        }
        // Purpur end - Special mobs naturally spawn

        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    private static Rabbit.Variant getRandomRabbitVariant(LevelAccessor level, BlockPos pos) {
        // Purpur start - Special mobs naturally spawn
        Level world = level.getMinecraftWorld();
        if (world.purpurConfig.rabbitNaturalKiller > 0D && world.getRandom().nextDouble() <= world.purpurConfig.rabbitNaturalKiller) {
            return Rabbit.Variant.EVIL;
        }
        // Purpur end - Special mobs naturally spawn
        Holder<Biome> biome = level.getBiome(pos);
        int randomInt = level.getRandom().nextInt(100);
        if (biome.is(BiomeTags.SPAWNS_WHITE_RABBITS)) {
            return randomInt < 80 ? Rabbit.Variant.WHITE : Rabbit.Variant.WHITE_SPLOTCHED;
        } else if (biome.is(BiomeTags.SPAWNS_GOLD_RABBITS)) {
            return Rabbit.Variant.GOLD;
        } else {
            return randomInt < 50 ? Rabbit.Variant.BROWN : (randomInt < 90 ? Rabbit.Variant.SALT : Rabbit.Variant.BLACK);
        }
    }

    public static boolean checkRabbitSpawnRules(
        EntityType<Rabbit> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return level.getBlockState(pos.below()).is(BlockTags.RABBITS_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }

    boolean wantsMoreFood() {
        return this.moreCarrotTicks <= 0;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 1) {
            this.spawnSprintParticle();
            this.jumpDuration = 10;
            this.jumpTicks = 0;
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.6F * this.getEyeHeight(), this.getBbWidth() * 0.4F);
    }

    static class RabbitAvoidEntityGoal<T extends LivingEntity> extends AvoidEntityGoal<T> {
        private final Rabbit rabbit;

        public RabbitAvoidEntityGoal(Rabbit rabbit, Class<T> entityClassToAvoid, float maxDist, double walkSpeedModifier, double sprintSpeedModifier) {
            super(rabbit, entityClassToAvoid, maxDist, walkSpeedModifier, sprintSpeedModifier);
            this.rabbit = rabbit;
        }

        @Override
        public boolean canUse() {
            return this.rabbit.getVariant() != Rabbit.Variant.EVIL && super.canUse();
        }
    }

    public static class RabbitGroupData extends AgeableMob.AgeableMobGroupData {
        public final Rabbit.Variant variant;

        public RabbitGroupData(Rabbit.Variant variant) {
            super(1.0F);
            this.variant = variant;
        }
    }

    public static class RabbitJumpControl extends JumpControl {
        private final Rabbit rabbit;
        private boolean canJump;

        public RabbitJumpControl(Rabbit mob) {
            super(mob);
            this.rabbit = mob;
        }

        public boolean wantJump() {
            return this.jump;
        }

        public boolean canJump() {
            return this.canJump;
        }

        public void setCanJump(boolean canJump) {
            this.canJump = canJump;
        }

        @Override
        public void tick() {
            if (this.jump) {
                this.rabbit.startJumping();
                this.jump = false;
            }
        }
    }

    static class RabbitMoveControl extends org.purpurmc.purpur.controller.MoveControllerWASD { // Purpur - Ridables
        private final Rabbit rabbit;
        private double nextJumpSpeed;

        public RabbitMoveControl(Rabbit mob) {
            super(mob);
            this.rabbit = mob;
        }

        @Override
        public void vanillaTick() { // Purpur - Ridables
            if (this.rabbit.onGround() && !this.rabbit.jumping && !((Rabbit.RabbitJumpControl)this.rabbit.jumpControl).wantJump()) {
                this.rabbit.setSpeedModifier(0.0);
            } else if (this.hasWanted() || this.operation == MoveControl.Operation.JUMPING) {
                this.rabbit.setSpeedModifier(this.nextJumpSpeed);
            }

            super.vanillaTick(); // Purpur - Ridables
        }

        @Override
        public void setWantedPosition(double x, double y, double z, double speed) {
            if (this.rabbit.isInWater()) {
                speed = 1.5;
            }

            super.setWantedPosition(x, y, z, speed);
            if (speed > 0.0) {
                this.nextJumpSpeed = speed;
            }
        }
    }

    static class RabbitPanicGoal extends PanicGoal {
        private final Rabbit rabbit;

        public RabbitPanicGoal(Rabbit rabbit, double speedModifier) {
            super(rabbit, speedModifier);
            this.rabbit = rabbit;
        }

        @Override
        public void tick() {
            super.tick();
            this.rabbit.setSpeedModifier(this.speedModifier);
        }
    }

    static class RaidGardenGoal extends MoveToBlockGoal {
        private final Rabbit rabbit;
        private boolean wantsToRaid;
        private boolean canRaid;

        public RaidGardenGoal(Rabbit rabbit) {
            super(rabbit, 0.7F, 16);
            this.rabbit = rabbit;
        }

        @Override
        public boolean canUse() {
            if (this.nextStartTick <= 0) {
                if (!getServerLevel(this.rabbit).purpurConfig.rabbitBypassMobGriefing == !getServerLevel(this.rabbit).getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) { // Purpur - Add mobGriefing bypass to everything affected
                    return false;
                }

                this.canRaid = false;
                this.wantsToRaid = this.rabbit.wantsMoreFood();
            }

            return super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canRaid && super.canContinueToUse();
        }

        @Override
        public void tick() {
            super.tick();
            this.rabbit
                .getLookControl()
                .setLookAt(this.blockPos.getX() + 0.5, this.blockPos.getY() + 1, this.blockPos.getZ() + 0.5, 10.0F, this.rabbit.getMaxHeadXRot());
            if (this.isReachedTarget()) {
                Level level = this.rabbit.level();
                BlockPos blockPos = this.blockPos.above();
                BlockState blockState = level.getBlockState(blockPos);
                Block block = blockState.getBlock();
                if (this.canRaid && block instanceof CarrotBlock) {
                    int ageValue = blockState.getValue(CarrotBlock.AGE);
                    if (ageValue == 0) {
                        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this.rabbit, blockPos, blockState.getFluidState().createLegacyBlock())) return; // CraftBukkit // Paper - fix wrong block state
                        level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 2);
                        level.destroyBlock(blockPos, true, this.rabbit);
                    } else {
                        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this.rabbit, blockPos, blockState.setValue(CarrotBlock.AGE, ageValue - 1))) return; // CraftBukkit // Paper - fix wrong block state
                        level.setBlock(blockPos, blockState.setValue(CarrotBlock.AGE, Integer.valueOf(ageValue - 1)), 2);
                        level.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(this.rabbit));
                        level.levelEvent(2001, blockPos, Block.getId(blockState));
                    }

                    this.rabbit.moreCarrotTicks = 40;
                }

                this.canRaid = false;
                this.nextStartTick = 10;
            }
        }

        @Override
        protected boolean isValidTarget(LevelReader level, BlockPos pos) {
            BlockState blockState = level.getBlockState(pos);
            if (blockState.is(Blocks.FARMLAND) && this.wantsToRaid && !this.canRaid) {
                blockState = level.getBlockState(pos.above());
                if (blockState.getBlock() instanceof CarrotBlock && ((CarrotBlock)blockState.getBlock()).isMaxAge(blockState)) {
                    this.canRaid = true;
                    return true;
                }
            }

            return false;
        }
    }

    public static enum Variant implements StringRepresentable {
        BROWN(0, "brown"),
        WHITE(1, "white"),
        BLACK(2, "black"),
        WHITE_SPLOTCHED(3, "white_splotched"),
        GOLD(4, "gold"),
        SALT(5, "salt"),
        EVIL(99, "evil");

        private static final IntFunction<Rabbit.Variant> BY_ID = ByIdMap.sparse(Rabbit.Variant::id, values(), BROWN);
        public static final Codec<Rabbit.Variant> CODEC = StringRepresentable.fromEnum(Rabbit.Variant::values);
        final int id;
        private final String name;

        private Variant(final int id, final String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public int id() {
            return this.id;
        }

        public static Rabbit.Variant byId(int id) {
            return BY_ID.apply(id);
        }
    }
}
