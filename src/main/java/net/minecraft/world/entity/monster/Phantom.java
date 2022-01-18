package net.minecraft.world.entity.monster;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class Phantom extends FlyingMob implements Enemy {

    public static final float FLAP_DEGREES_PER_TICK = 7.448451F;
    public static final int TICKS_PER_FLAP = Mth.ceil(24.166098F);
    private static final EntityDataAccessor<Integer> ID_SIZE = SynchedEntityData.defineId(Phantom.class, EntityDataSerializers.INT);
    Vec3 moveTargetPoint;
    public BlockPos anchorPoint;
    Phantom.AttackPhase attackPhase;
    private static final net.minecraft.world.item.crafting.Ingredient TORCH = net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.TORCH, net.minecraft.world.item.Items.SOUL_TORCH); // Purpur
    Vec3 crystalPosition; // Purpur

    public Phantom(EntityType<? extends Phantom> type, Level world) {
        super(type, world);
        this.moveTargetPoint = Vec3.ZERO;
        this.anchorPoint = BlockPos.ZERO;
        this.attackPhase = Phantom.AttackPhase.CIRCLE;
        this.xpReward = 5;
        this.moveControl = new Phantom.PhantomMoveControl(this);
        this.lookControl = new Phantom.PhantomLookControl(this, this);
        this.setShouldBurnInDay(true); // Purpur - API for any mob to burn daylight
    }

    // Purpur start
    @Override
    public boolean isRidable() {
        return level().purpurConfig.phantomRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.phantomRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.phantomControllable;
    }

    @Override
    public double getMaxY() {
        return level().purpurConfig.phantomMaxY;
    }

    @Override
    public void travel(Vec3 vec3) {
        super.travel(vec3);
        if (getRider() != null && this.isControllable() && !onGround) {
            float speed = (float) getAttributeValue(Attributes.FLYING_SPEED);
            setSpeed(speed);
            Vec3 mot = getDeltaMovement();
            move(net.minecraft.world.entity.MoverType.SELF, mot.multiply(speed, speed, speed));
            setDeltaMovement(mot.scale(0.9D));
        }
    }

    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.FLYING_SPEED, 3.0D);
    }

    @Override
    public boolean onSpacebar() {
        if (getRider() != null && getRider().getBukkitEntity().hasPermission("allow.special.phantom")) {
            shoot();
        }
        return false;
    }

    public boolean shoot() {
        org.bukkit.Location loc = ((org.bukkit.entity.LivingEntity) getBukkitEntity()).getEyeLocation();
        loc.setPitch(-loc.getPitch());
        org.bukkit.util.Vector target = loc.getDirection().normalize().multiply(100).add(loc.toVector());

        org.purpurmc.purpur.entity.PhantomFlames flames = new org.purpurmc.purpur.entity.PhantomFlames(level(), this);
        flames.canGrief = level().purpurConfig.phantomAllowGriefing;
        flames.shoot(target.getX() - getX(), target.getY() - getY(), target.getZ() - getZ(), 1.0F, 5.0F);
        level().addFreshEntity(flames);
        return true;
    }

    @Override
    protected void dropFromLootTable(DamageSource damageSource, boolean causedByPlayer) {
        boolean dropped = false;
        if (lastHurtByPlayer == null && damageSource.getEntity() instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal) {
            if (random.nextInt(5) < 1) {
                dropped = spawnAtLocation(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PHANTOM_MEMBRANE)) != null;
            }
        }
        if (!dropped) {
            super.dropFromLootTable(damageSource, causedByPlayer);
        }
    }

    public boolean isCirclingCrystal() {
        return crystalPosition != null;
    }
    // Purpur end

    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.phantomTakeDamageFromWater;
    }

    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.phantomAlwaysDropExp;
    }

    @Override
    public boolean isFlapping() {
        return (this.getUniqueFlapTickOffset() + this.tickCount) % Phantom.TICKS_PER_FLAP == 0;
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new Phantom.PhantomBodyRotationControl(this);
    }

    @Override
    protected void registerGoals() {
        // Purpur start
        this.goalSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this));
        if (level().purpurConfig.phantomOrbitCrystalRadius > 0) {
            this.goalSelector.addGoal(1, new FindCrystalGoal(this));
            this.goalSelector.addGoal(2, new OrbitCrystalGoal(this));
        }
        this.goalSelector.addGoal(3, new Phantom.PhantomAttackStrategyGoal());
        this.goalSelector.addGoal(4, new Phantom.PhantomSweepAttackGoal());
        this.goalSelector.addGoal(5, new Phantom.PhantomCircleAroundAnchorGoal());
        this.targetSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this));
        // Purpur end
        this.targetSelector.addGoal(1, new Phantom.PhantomAttackPlayerTargetGoal());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(Phantom.ID_SIZE, 0);
    }

    public void setPhantomSize(int size) {
        this.entityData.set(Phantom.ID_SIZE, Mth.clamp(size, 0, 64));
    }

    private void updatePhantomSizeInfo() {
        this.refreshDimensions();
        // Purpur start
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(getFromCache(() -> this.level().purpurConfig.phantomMaxHealth, () -> this.level().purpurConfig.phantomMaxHealthCache, () -> 20.0D));
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(getFromCache(() -> this.level().purpurConfig.phantomAttackDamage, () -> this.level().purpurConfig.phantomAttackDamageCache, () -> (double) 6 + this.getPhantomSize()));
        // Purpur end
    }

    public int getPhantomSize() {
        return (Integer) this.entityData.get(Phantom.ID_SIZE);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if (Phantom.ID_SIZE.equals(data)) {
            this.updatePhantomSizeInfo();
        }

        super.onSyncedDataUpdated(data);
    }

    public int getUniqueFlapTickOffset() {
        return this.getId() * 3;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    private double getFromCache(java.util.function.Supplier<String> equation, java.util.function.Supplier<java.util.Map<Integer, Double>> cache, java.util.function.Supplier<Double> defaultValue) {
        int size = getPhantomSize();
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

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            float f = Mth.cos((float) (this.getUniqueFlapTickOffset() + this.tickCount) * 7.448451F * 0.017453292F + 3.1415927F);
            float f1 = Mth.cos((float) (this.getUniqueFlapTickOffset() + this.tickCount + 1) * 7.448451F * 0.017453292F + 3.1415927F);

            if (f > 0.0F && f1 <= 0.0F) {
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.PHANTOM_FLAP, this.getSoundSource(), 0.95F + this.random.nextFloat() * 0.05F, 0.95F + this.random.nextFloat() * 0.05F, false);
            }

            float f2 = this.getBbWidth() * 1.48F;
            float f3 = Mth.cos(this.getYRot() * 0.017453292F) * f2;
            float f4 = Mth.sin(this.getYRot() * 0.017453292F) * f2;
            float f5 = (0.3F + f * 0.45F) * this.getBbHeight() * 2.5F;

            this.level().addParticle(ParticleTypes.MYCELIUM, this.getX() + (double) f3, this.getY() + (double) f5, this.getZ() + (double) f4, 0.0D, 0.0D, 0.0D);
            this.level().addParticle(ParticleTypes.MYCELIUM, this.getX() - (double) f3, this.getY() + (double) f5, this.getZ() - (double) f4, 0.0D, 0.0D, 0.0D);
        }

        if (level().purpurConfig.phantomFlamesOnSwoop && attackPhase == AttackPhase.SWOOP) shoot(); // Purpur
    }

    @Override
    public void aiStep() {
        // Purpur - implemented in LivingEntity; moved down to shouldBurnInDay() - API for any mob to burn daylight
        super.aiStep();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData) {
        this.anchorPoint = this.blockPosition().above(5);
        // Purpur start
        int min = world.getLevel().purpurConfig.phantomMinSize;
        int max = world.getLevel().purpurConfig.phantomMaxSize;
        this.setPhantomSize(min == max ? min : world.getRandom().nextInt(max + 1 - min) + min);
        // Purpur end
        return super.finalizeSpawn(world, difficulty, spawnReason, entityData);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("AX")) {
            this.anchorPoint = new BlockPos(nbt.getInt("AX"), nbt.getInt("AY"), nbt.getInt("AZ"));
        }

        this.setPhantomSize(nbt.getInt("Size"));
        // Paper start
        if (nbt.hasUUID("Paper.SpawningEntity")) {
            this.spawningEntity = nbt.getUUID("Paper.SpawningEntity");
        }
        if (false && nbt.contains("Paper.ShouldBurnInDay")) { // Purpur - implemented in LivingEntity - API for any mob to burn daylight
            this.shouldBurnInDay = nbt.getBoolean("Paper.ShouldBurnInDay");
        }
        // Paper end
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("AX", this.anchorPoint.getX());
        nbt.putInt("AY", this.anchorPoint.getY());
        nbt.putInt("AZ", this.anchorPoint.getZ());
        nbt.putInt("Size", this.getPhantomSize());
        // Paper start
        if (this.spawningEntity != null) {
            nbt.putUUID("Paper.SpawningEntity", this.spawningEntity);
        }
        //nbt.putBoolean("Paper.ShouldBurnInDay", shouldBurnInDay); // Purpur - implemented in LivingEntity - API for any mob to burn daylight
        // Paper end
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    public float getSoundVolume() {
        return 1.0F;
    }

    @Override
    public boolean canAttackType(EntityType<?> type) {
        return true;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        int i = this.getPhantomSize();
        EntityDimensions entitysize = super.getDefaultDimensions(pose);

        return entitysize.scale(1.0F + 0.15F * (float) i);
    }

    // Paper start
    @Nullable
    java.util.UUID spawningEntity;

    @Nullable
    public java.util.UUID getSpawningEntity() {
        return this.spawningEntity;
    }
    public void setSpawningEntity(java.util.UUID entity) { this.spawningEntity = entity; }
    //private boolean shouldBurnInDay = true; // Purpur - moved to LivingEntity; keep methods for ABI compatibility - API for any mob to burn daylight
    // Purpur start - API for any mob to burn daylight
    public boolean shouldBurnInDay() {
        boolean burnFromDaylight = this.shouldBurnInDay && this.level().purpurConfig.phantomBurnInDaylight;
        boolean burnFromLightSource = this.level().purpurConfig.phantomBurnInLight > 0 && this.level().getMaxLocalRawBrightness(blockPosition()) >= this.level().purpurConfig.phantomBurnInLight;
        return burnFromDaylight || burnFromLightSource;
    }
    // Purpur end - API for any mob to burn daylight
    public void setShouldBurnInDay(boolean shouldBurnInDay) { this.shouldBurnInDay = shouldBurnInDay; }
    // Paper end

    private static enum AttackPhase {

        CIRCLE, SWOOP;

        private AttackPhase() {}
    }

    // Purpur start
    class FindCrystalGoal extends Goal {
        private final Phantom phantom;
        private net.minecraft.world.entity.boss.enderdragon.EndCrystal crystal;
        private Comparator<net.minecraft.world.entity.boss.enderdragon.EndCrystal> comparator;

        FindCrystalGoal(Phantom phantom) {
            this.phantom = phantom;
            this.comparator = Comparator.comparingDouble(phantom::distanceToSqr);
            this.setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            double range = maxTargetRange();
            List<net.minecraft.world.entity.boss.enderdragon.EndCrystal> crystals = level().getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EndCrystal.class, phantom.getBoundingBox().inflate(range));
            if (crystals.isEmpty()) {
                return false;
            }
            crystals.sort(comparator);
            crystal = crystals.get(0);
            if (phantom.distanceToSqr(crystal) > range * range) {
                crystal = null;
                return false;
            }
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            if (crystal == null || !crystal.isAlive()) {
                return false;
            }
            double range = maxTargetRange();
            return phantom.distanceToSqr(crystal) <= (range * range) * 2;
        }

        @Override
        public void start() {
            phantom.crystalPosition = new Vec3(crystal.getX(), crystal.getY() + (phantom.random.nextInt(10) + 10), crystal.getZ());
        }

        @Override
        public void stop() {
            crystal = null;
            phantom.crystalPosition = null;
            super.stop();
        }

        private double maxTargetRange() {
            return phantom.level().purpurConfig.phantomOrbitCrystalRadius;
        }
    }

    class OrbitCrystalGoal extends Goal {
        private final Phantom phantom;
        private float offset;
        private float radius;
        private float verticalChange;
        private float direction;

        OrbitCrystalGoal(Phantom phantom) {
            this.phantom = phantom;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return phantom.isCirclingCrystal();
        }

        @Override
        public void start() {
            this.radius = 5.0F + phantom.random.nextFloat() * 10.0F;
            this.verticalChange = -4.0F + phantom.random.nextFloat() * 9.0F;
            this.direction = phantom.random.nextBoolean() ? 1.0F : -1.0F;
            updateOffset();
        }

        @Override
        public void tick() {
            if (phantom.random.nextInt(350) == 0) {
                this.verticalChange = -4.0F + phantom.random.nextFloat() * 9.0F;
            }
            if (phantom.random.nextInt(250) == 0) {
                ++this.radius;
                if (this.radius > 15.0F) {
                    this.radius = 5.0F;
                    this.direction = -this.direction;
                }
            }
            if (phantom.random.nextInt(450) == 0) {
                this.offset = phantom.random.nextFloat() * 2.0F * 3.1415927F;
                updateOffset();
            }
            if (phantom.moveTargetPoint.distanceToSqr(phantom.getX(), phantom.getY(), phantom.getZ()) < 4.0D) {
                updateOffset();
            }
            if (phantom.moveTargetPoint.y < phantom.getY() && !phantom.level().isEmptyBlock(new BlockPos(phantom).below(1))) {
                this.verticalChange = Math.max(1.0F, this.verticalChange);
                updateOffset();
            }
            if (phantom.moveTargetPoint.y > phantom.getY() && !phantom.level().isEmptyBlock(new BlockPos(phantom).above(1))) {
                this.verticalChange = Math.min(-1.0F, this.verticalChange);
                updateOffset();
            }
        }

        private void updateOffset() {
            this.offset += this.direction * 15.0F * 0.017453292F;
            phantom.moveTargetPoint = phantom.crystalPosition.add(
                    this.radius * Mth.cos(this.offset),
                    -4.0F + this.verticalChange,
                    this.radius * Mth.sin(this.offset));
        }
    }
    // Purpur end

    private class PhantomMoveControl extends org.purpurmc.purpur.controller.FlyingMoveControllerWASD { // Purpur

        private float speed = 0.1F;

        public PhantomMoveControl(final Mob entity) {
            super(entity);
        }

        // Purpur start
        public void purpurTick(Player rider) {
            if (!Phantom.this.onGround) {
                // phantom is always in motion when flying
                // TODO - FIX THIS
                // rider.setForward(1.0F);
            }
            super.purpurTick(rider);
        }
        // Purpur end

        @Override
        public void vanillaTick() { // Purpur
            if (Phantom.this.horizontalCollision) {
                Phantom.this.setYRot(Phantom.this.getYRot() + 180.0F);
                this.speed = 0.1F;
            }

            double d0 = Phantom.this.moveTargetPoint.x - Phantom.this.getX();
            double d1 = Phantom.this.moveTargetPoint.y - Phantom.this.getY();
            double d2 = Phantom.this.moveTargetPoint.z - Phantom.this.getZ();
            double d3 = Math.sqrt(d0 * d0 + d2 * d2);

            if (Math.abs(d3) > 9.999999747378752E-6D) {
                double d4 = 1.0D - Math.abs(d1 * 0.699999988079071D) / d3;

                d0 *= d4;
                d2 *= d4;
                d3 = Math.sqrt(d0 * d0 + d2 * d2);
                double d5 = Math.sqrt(d0 * d0 + d2 * d2 + d1 * d1);
                float f = Phantom.this.getYRot();
                float f1 = (float) Mth.atan2(d2, d0);
                float f2 = Mth.wrapDegrees(Phantom.this.getYRot() + 90.0F);
                float f3 = Mth.wrapDegrees(f1 * 57.295776F);

                Phantom.this.setYRot(Mth.approachDegrees(f2, f3, 4.0F) - 90.0F);
                Phantom.this.yBodyRot = Phantom.this.getYRot();
                if (Mth.degreesDifferenceAbs(f, Phantom.this.getYRot()) < 3.0F) {
                    this.speed = Mth.approach(this.speed, 1.8F, 0.005F * (1.8F / this.speed));
                } else {
                    this.speed = Mth.approach(this.speed, 0.2F, 0.025F);
                }

                float f4 = (float) (-(Mth.atan2(-d1, d3) * 57.2957763671875D));

                Phantom.this.setXRot(f4);
                float f5 = Phantom.this.getYRot() + 90.0F;
                double d6 = (double) (this.speed * Mth.cos(f5 * 0.017453292F)) * Math.abs(d0 / d5);
                double d7 = (double) (this.speed * Mth.sin(f5 * 0.017453292F)) * Math.abs(d2 / d5);
                double d8 = (double) (this.speed * Mth.sin(f4 * 0.017453292F)) * Math.abs(d1 / d5);
                Vec3 vec3d = Phantom.this.getDeltaMovement();

                Phantom.this.setDeltaMovement(vec3d.add((new Vec3(d6, d8, d7)).subtract(vec3d).scale(0.2D)));
            }

        }
    }

    private class PhantomLookControl extends org.purpurmc.purpur.controller.LookControllerWASD { // Purpur

        public PhantomLookControl(final Phantom entity, final Mob phantom) {
            super(phantom);
        }

        // Purpur start
        public void purpurTick(Player rider) {
            setYawPitch(rider.getYRot(), -rider.xRotO * 0.75F);
        }
        // Purpur end

        @Override
        public void vanillaTick() {} // Purpur
    }

    private class PhantomBodyRotationControl extends BodyRotationControl {

        public PhantomBodyRotationControl(final Mob entity) {
            super(entity);
        }

        @Override
        public void clientTick() {
            Phantom.this.yHeadRot = Phantom.this.yBodyRot;
            Phantom.this.yBodyRot = Phantom.this.getYRot();
        }
    }

    private class PhantomAttackStrategyGoal extends Goal {

        private int nextSweepTick;

        PhantomAttackStrategyGoal() {}

        @Override
        public boolean canUse() {
            LivingEntity entityliving = Phantom.this.getTarget();

            return entityliving != null ? Phantom.this.canAttack(entityliving, TargetingConditions.DEFAULT) : false;
        }

        @Override
        public void start() {
            this.nextSweepTick = this.adjustedTickDelay(10);
            Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
            this.setAnchorAboveTarget();
        }

        @Override
        public void stop() {
            Phantom.this.anchorPoint = Phantom.this.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, Phantom.this.anchorPoint).above(10 + Phantom.this.random.nextInt(20));
        }

        @Override
        public void tick() {
            if (Phantom.this.attackPhase == Phantom.AttackPhase.CIRCLE) {
                --this.nextSweepTick;
                if (this.nextSweepTick <= 0) {
                    Phantom.this.attackPhase = Phantom.AttackPhase.SWOOP;
                    this.setAnchorAboveTarget();
                    this.nextSweepTick = this.adjustedTickDelay((8 + Phantom.this.random.nextInt(4)) * 20);
                    Phantom.this.playSound(SoundEvents.PHANTOM_SWOOP, 10.0F, 0.95F + Phantom.this.random.nextFloat() * 0.1F);
                }
            }

        }

        private void setAnchorAboveTarget() {
            Phantom.this.anchorPoint = Phantom.this.getTarget().blockPosition().above(20 + Phantom.this.random.nextInt(20));
            if (Phantom.this.anchorPoint.getY() < Phantom.this.level().getSeaLevel()) {
                Phantom.this.anchorPoint = new BlockPos(Phantom.this.anchorPoint.getX(), Phantom.this.level().getSeaLevel() + 1, Phantom.this.anchorPoint.getZ());
            }

        }
    }

    private class PhantomSweepAttackGoal extends Phantom.PhantomMoveTargetGoal {

        private static final int CAT_SEARCH_TICK_DELAY = 20;
        private boolean isScaredOfCat;
        private int catSearchTick;

        PhantomSweepAttackGoal() {
            super();
        }

        @Override
        public boolean canUse() {
            return Phantom.this.getTarget() != null && Phantom.this.attackPhase == Phantom.AttackPhase.SWOOP;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity entityliving = Phantom.this.getTarget();

            if (entityliving == null) {
                return false;
            } else if (!entityliving.isAlive()) {
                return false;
            // Purpur start
            } else if (level().purpurConfig.phantomBurnInLight > 0 && level().getLightEmission(new BlockPos(Phantom.this)) >= level().purpurConfig.phantomBurnInLight) {
                return false;
            } else if (level().purpurConfig.phantomIgnorePlayersWithTorch && (TORCH.test(entityliving.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)) || TORCH.test(entityliving.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND)))) {
                return false;
            // Purpur end
            } else {
                if (entityliving instanceof Player) {
                    Player entityhuman = (Player) entityliving;

                    if (entityliving.isSpectator() || entityhuman.isCreative()) {
                        return false;
                    }
                }

                if (!this.canUse()) {
                    return false;
                } else {
                    if (Phantom.this.tickCount > this.catSearchTick) {
                        this.catSearchTick = Phantom.this.tickCount + 20;
                        List<Cat> list = Phantom.this.level().getEntitiesOfClass(Cat.class, Phantom.this.getBoundingBox().inflate(16.0D), EntitySelector.ENTITY_STILL_ALIVE);
                        Iterator iterator = list.iterator();

                        while (iterator.hasNext()) {
                            Cat entitycat = (Cat) iterator.next();

                            entitycat.hiss();
                        }

                        this.isScaredOfCat = !list.isEmpty();
                    }

                    return !this.isScaredOfCat;
                }
            }
        }

        @Override
        public void start() {}

        @Override
        public void stop() {
            Phantom.this.setTarget((LivingEntity) null);
            Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
        }

        @Override
        public void tick() {
            LivingEntity entityliving = Phantom.this.getTarget();

            if (entityliving != null) {
                Phantom.this.moveTargetPoint = new Vec3(entityliving.getX(), entityliving.getY(0.5D), entityliving.getZ());
                if (Phantom.this.getBoundingBox().inflate(0.20000000298023224D).intersects(entityliving.getBoundingBox())) {
                    Phantom.this.doHurtTarget(entityliving);
                    Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
                    if (!Phantom.this.isSilent()) {
                        Phantom.this.level().levelEvent(1039, Phantom.this.blockPosition(), 0);
                    }
                } else if (Phantom.this.horizontalCollision || Phantom.this.hurtTime > 0) {
                    Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
                }

            }
        }
    }

    private class PhantomCircleAroundAnchorGoal extends Phantom.PhantomMoveTargetGoal {

        private float angle;
        private float distance;
        private float height;
        private float clockwise;

        PhantomCircleAroundAnchorGoal() {
            super();
        }

        @Override
        public boolean canUse() {
            return Phantom.this.getTarget() == null || Phantom.this.attackPhase == Phantom.AttackPhase.CIRCLE;
        }

        @Override
        public void start() {
            this.distance = 5.0F + Phantom.this.random.nextFloat() * 10.0F;
            this.height = -4.0F + Phantom.this.random.nextFloat() * 9.0F;
            this.clockwise = Phantom.this.random.nextBoolean() ? 1.0F : -1.0F;
            this.selectNext();
        }

        @Override
        public void tick() {
            if (Phantom.this.random.nextInt(this.adjustedTickDelay(350)) == 0) {
                this.height = -4.0F + Phantom.this.random.nextFloat() * 9.0F;
            }

            if (Phantom.this.random.nextInt(this.adjustedTickDelay(250)) == 0) {
                ++this.distance;
                if (this.distance > 15.0F) {
                    this.distance = 5.0F;
                    this.clockwise = -this.clockwise;
                }
            }

            if (Phantom.this.random.nextInt(this.adjustedTickDelay(450)) == 0) {
                this.angle = Phantom.this.random.nextFloat() * 2.0F * 3.1415927F;
                this.selectNext();
            }

            if (this.touchingTarget()) {
                this.selectNext();
            }

            if (Phantom.this.moveTargetPoint.y < Phantom.this.getY() && !Phantom.this.level().isEmptyBlock(Phantom.this.blockPosition().below(1))) {
                this.height = Math.max(1.0F, this.height);
                this.selectNext();
            }

            if (Phantom.this.moveTargetPoint.y > Phantom.this.getY() && !Phantom.this.level().isEmptyBlock(Phantom.this.blockPosition().above(1))) {
                this.height = Math.min(-1.0F, this.height);
                this.selectNext();
            }

        }

        private void selectNext() {
            if (BlockPos.ZERO.equals(Phantom.this.anchorPoint)) {
                Phantom.this.anchorPoint = Phantom.this.blockPosition();
            }

            this.angle += this.clockwise * 15.0F * 0.017453292F;
            Phantom.this.moveTargetPoint = Vec3.atLowerCornerOf(Phantom.this.anchorPoint).add((double) (this.distance * Mth.cos(this.angle)), (double) (-4.0F + this.height), (double) (this.distance * Mth.sin(this.angle)));
        }
    }

    private class PhantomAttackPlayerTargetGoal extends Goal {

        private final TargetingConditions attackTargeting = TargetingConditions.forCombat().range(64.0D);
        private int nextScanTick = reducedTickDelay(20);

        PhantomAttackPlayerTargetGoal() {}

        @Override
        public boolean canUse() {
            if (this.nextScanTick > 0) {
                --this.nextScanTick;
                return false;
            } else {
                this.nextScanTick = reducedTickDelay(60);
                List<Player> list = Phantom.this.level().getNearbyPlayers(this.attackTargeting, Phantom.this, Phantom.this.getBoundingBox().inflate(16.0D, 64.0D, 16.0D));

                if (level().purpurConfig.phantomIgnorePlayersWithTorch) list.removeIf(human -> TORCH.test(human.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)) || TORCH.test(human.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND)));// Purpur
                if (!list.isEmpty()) {
                    list.sort(Comparator.comparing((Entity e) -> { return e.getY(); }).reversed()); // CraftBukkit - decompile error
                    Iterator iterator = list.iterator();

                    while (iterator.hasNext()) {
                        Player entityhuman = (Player) iterator.next();

                        if (Phantom.this.canAttack(entityhuman, TargetingConditions.DEFAULT)) {
                            if (!level().paperConfig().entities.behavior.phantomsOnlyAttackInsomniacs || EntitySelector.IS_INSOMNIAC.test(entityhuman)) // Paper - Add phantom creative and insomniac controls
                            Phantom.this.setTarget(entityhuman, org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER, true); // CraftBukkit - reason
                            return true;
                        }
                    }
                }

                return false;
            }
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity entityliving = Phantom.this.getTarget();

            return entityliving != null ? Phantom.this.canAttack(entityliving, TargetingConditions.DEFAULT) : false;
        }
    }

    private abstract class PhantomMoveTargetGoal extends Goal {

        public PhantomMoveTargetGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        protected boolean touchingTarget() {
            return Phantom.this.moveTargetPoint.distanceToSqr(Phantom.this.getX(), Phantom.this.getY(), Phantom.this.getZ()) < 4.0D;
        }
    }
}
