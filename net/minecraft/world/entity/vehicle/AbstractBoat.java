package net.minecraft.world.entity.vehicle;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class AbstractBoat extends VehicleEntity implements Leashable {
    private static final EntityDataAccessor<Boolean> DATA_ID_PADDLE_LEFT = SynchedEntityData.defineId(AbstractBoat.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ID_PADDLE_RIGHT = SynchedEntityData.defineId(AbstractBoat.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ID_BUBBLE_TIME = SynchedEntityData.defineId(AbstractBoat.class, EntityDataSerializers.INT);
    public static final int PADDLE_LEFT = 0;
    public static final int PADDLE_RIGHT = 1;
    private static final int TIME_TO_EJECT = 60;
    private static final float PADDLE_SPEED = (float) (Math.PI / 8);
    public static final double PADDLE_SOUND_TIME = (float) (Math.PI / 4);
    public static final int BUBBLE_TIME = 60;
    private final float[] paddlePositions = new float[2];
    private float invFriction;
    private float outOfControlTicks;
    private float deltaRotation;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;
    private boolean inputLeft;
    private boolean inputRight;
    private boolean inputUp;
    private boolean inputDown;
    private double waterLevel;
    private float landFriction;
    private AbstractBoat.Status status;
    private AbstractBoat.Status oldStatus;
    private double lastYd;
    private boolean isAboveBubbleColumn;
    private boolean bubbleColumnDirectionIsDown;
    private float bubbleMultiplier;
    private float bubbleAngle;
    private float bubbleAngleO;
    @Nullable
    private Leashable.LeashData leashData;
    private final Supplier<Item> dropItem;

    public AbstractBoat(EntityType<? extends AbstractBoat> entityType, Level level, Supplier<Item> dropItem) {
        super(entityType, level);
        this.dropItem = dropItem;
        this.blocksBuilding = true;
    }

    public void setInitialPos(double x, double y, double z) {
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.EVENTS;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ID_PADDLE_LEFT, false);
        builder.define(DATA_ID_PADDLE_RIGHT, false);
        builder.define(DATA_ID_BUBBLE_TIME, 0);
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return canVehicleCollide(this, entity);
    }

    public static boolean canVehicleCollide(Entity first, Entity second) {
        return (second.canBeCollidedWith() || second.isPushable()) && !first.isPassengerOfSameVehicle(second);
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public Vec3 getRelativePortalPosition(Direction.Axis axis, BlockUtil.FoundRectangle portal) {
        return LivingEntity.resetForwardDirectionOfRelativePortalPosition(super.getRelativePortalPosition(axis, portal));
    }

    protected abstract double rideHeight(EntityDimensions dimensions);

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
        float singlePassengerXOffset = this.getSinglePassengerXOffset();
        if (this.getPassengers().size() > 1) {
            int index = this.getPassengers().indexOf(entity);
            if (index == 0) {
                singlePassengerXOffset = 0.2F;
            } else {
                singlePassengerXOffset = -0.6F;
            }

            if (entity instanceof Animal) {
                singlePassengerXOffset += 0.2F;
            }
        }

        return new Vec3(0.0, this.rideHeight(dimensions), singlePassengerXOffset).yRot(-this.getYRot() * (float) (Math.PI / 180.0));
    }

    @Override
    public void onAboveBubbleCol(boolean downwards) {
        if (!this.level().isClientSide) {
            this.isAboveBubbleColumn = true;
            this.bubbleColumnDirectionIsDown = downwards;
            if (this.getBubbleTime() == 0) {
                this.setBubbleTime(60);
            }
        }

        this.level()
            .addParticle(ParticleTypes.SPLASH, this.getX() + this.random.nextFloat(), this.getY() + 0.7, this.getZ() + this.random.nextFloat(), 0.0, 0.0, 0.0);
        if (this.random.nextInt(20) == 0) {
            this.level()
                .playLocalSound(
                    this.getX(), this.getY(), this.getZ(), this.getSwimSplashSound(), this.getSoundSource(), 1.0F, 0.8F + 0.4F * this.random.nextFloat(), false
                );
            this.gameEvent(GameEvent.SPLASH, this.getControllingPassenger());
        }
    }

    @Override
    public void push(Entity entity) {
        if (entity instanceof AbstractBoat) {
            if (entity.getBoundingBox().minY < this.getBoundingBox().maxY) {
                super.push(entity);
            }
        } else if (entity.getBoundingBox().minY <= this.getBoundingBox().minY) {
            super.push(entity);
        }
    }

    @Override
    public void animateHurt(float yaw) {
        this.setHurtDir(-this.getHurtDir());
        this.setHurtTime(10);
        this.setDamage(this.getDamage() * 11.0F);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public void cancelLerp() {
        this.lerpSteps = 0;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = steps;
    }

    @Override
    public double lerpTargetX() {
        return this.lerpSteps > 0 ? this.lerpX : this.getX();
    }

    @Override
    public double lerpTargetY() {
        return this.lerpSteps > 0 ? this.lerpY : this.getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.lerpSteps > 0 ? this.lerpZ : this.getZ();
    }

    @Override
    public float lerpTargetXRot() {
        return this.lerpSteps > 0 ? (float)this.lerpXRot : this.getXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return this.lerpSteps > 0 ? (float)this.lerpYRot : this.getYRot();
    }

    @Override
    public Direction getMotionDirection() {
        return this.getDirection().getClockWise();
    }

    @Override
    public void tick() {
        this.oldStatus = this.status;
        this.status = this.getStatus();
        if (this.status != AbstractBoat.Status.UNDER_WATER && this.status != AbstractBoat.Status.UNDER_FLOWING_WATER) {
            this.outOfControlTicks = 0.0F;
        } else {
            this.outOfControlTicks++;
        }

        if (!this.level().isClientSide && this.outOfControlTicks >= 60.0F) {
            this.ejectPassengers();
        }

        if (this.getHurtTime() > 0) {
            this.setHurtTime(this.getHurtTime() - 1);
        }

        if (this.getDamage() > 0.0F) {
            this.setDamage(this.getDamage() - 1.0F);
        }

        super.tick();
        this.tickLerp();
        if (this.isControlledByLocalInstance()) {
            if (!(this.getFirstPassenger() instanceof Player)) {
                this.setPaddleState(false, false);
            }

            this.floatBoat();
            if (this.level().isClientSide) {
                this.controlBoat();
                this.level().sendPacketToServer(new ServerboundPaddleBoatPacket(this.getPaddleState(0), this.getPaddleState(1)));
            }

            this.move(MoverType.SELF, this.getDeltaMovement());
        } else {
            this.setDeltaMovement(Vec3.ZERO);
        }

        this.applyEffectsFromBlocks();
        this.applyEffectsFromBlocks();
        this.tickBubbleColumn();

        for (int i = 0; i <= 1; i++) {
            if (this.getPaddleState(i)) {
                if (!this.isSilent()
                    && this.paddlePositions[i] % (float) (Math.PI * 2) <= (float) (Math.PI / 4)
                    && (this.paddlePositions[i] + (float) (Math.PI / 8)) % (float) (Math.PI * 2) >= (float) (Math.PI / 4)) {
                    SoundEvent paddleSound = this.getPaddleSound();
                    if (paddleSound != null) {
                        Vec3 viewVector = this.getViewVector(1.0F);
                        double d = i == 1 ? -viewVector.z : viewVector.z;
                        double d1 = i == 1 ? viewVector.x : -viewVector.x;
                        this.level()
                            .playSound(
                                null,
                                this.getX() + d,
                                this.getY(),
                                this.getZ() + d1,
                                paddleSound,
                                this.getSoundSource(),
                                1.0F,
                                0.8F + 0.4F * this.random.nextFloat()
                            );
                    }
                }

                this.paddlePositions[i] = this.paddlePositions[i] + (float) (Math.PI / 8);
            } else {
                this.paddlePositions[i] = 0.0F;
            }
        }

        List<Entity> entities = this.level().getEntities(this, this.getBoundingBox().inflate(0.2F, -0.01F, 0.2F), EntitySelector.pushableBy(this));
        if (!entities.isEmpty()) {
            boolean flag = !this.level().isClientSide && !(this.getControllingPassenger() instanceof Player);

            for (Entity entity : entities) {
                if (!entity.hasPassenger(this)) {
                    if (flag
                        && this.getPassengers().size() < this.getMaxPassengers()
                        && !entity.isPassenger()
                        && this.hasEnoughSpaceFor(entity)
                        && entity instanceof LivingEntity
                        && !(entity instanceof WaterAnimal)
                        && !(entity instanceof Player)
                        && !(entity instanceof Creaking)) {
                        entity.startRiding(this);
                    } else {
                        this.push(entity);
                    }
                }
            }
        }
    }

    private void tickBubbleColumn() {
        if (this.level().isClientSide) {
            int bubbleTime = this.getBubbleTime();
            if (bubbleTime > 0) {
                this.bubbleMultiplier += 0.05F;
            } else {
                this.bubbleMultiplier -= 0.1F;
            }

            this.bubbleMultiplier = Mth.clamp(this.bubbleMultiplier, 0.0F, 1.0F);
            this.bubbleAngleO = this.bubbleAngle;
            this.bubbleAngle = 10.0F * (float)Math.sin(0.5F * (float)this.level().getGameTime()) * this.bubbleMultiplier;
        } else {
            if (!this.isAboveBubbleColumn) {
                this.setBubbleTime(0);
            }

            int bubbleTime = this.getBubbleTime();
            if (bubbleTime > 0) {
                this.setBubbleTime(--bubbleTime);
                int i = 60 - bubbleTime - 1;
                if (i > 0 && bubbleTime == 0) {
                    this.setBubbleTime(0);
                    Vec3 deltaMovement = this.getDeltaMovement();
                    if (this.bubbleColumnDirectionIsDown) {
                        this.setDeltaMovement(deltaMovement.add(0.0, -0.7, 0.0));
                        this.ejectPassengers();
                    } else {
                        this.setDeltaMovement(deltaMovement.x, this.hasPassenger(entity -> entity instanceof Player) ? 2.7 : 0.6, deltaMovement.z);
                    }
                }

                this.isAboveBubbleColumn = false;
            }
        }
    }

    @Nullable
    protected SoundEvent getPaddleSound() {
        switch (this.getStatus()) {
            case IN_WATER:
            case UNDER_WATER:
            case UNDER_FLOWING_WATER:
                return SoundEvents.BOAT_PADDLE_WATER;
            case ON_LAND:
                return SoundEvents.BOAT_PADDLE_LAND;
            case IN_AIR:
            default:
                return null;
        }
    }

    private void tickLerp() {
        if (this.lerpSteps > 0) {
            this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot, this.lerpXRot);
            this.lerpSteps--;
        }
    }

    public void setPaddleState(boolean left, boolean right) {
        this.entityData.set(DATA_ID_PADDLE_LEFT, left);
        this.entityData.set(DATA_ID_PADDLE_RIGHT, right);
    }

    public float getRowingTime(int side, float partialTick) {
        return this.getPaddleState(side) ? Mth.clampedLerp(this.paddlePositions[side] - (float) (Math.PI / 8), this.paddlePositions[side], partialTick) : 0.0F;
    }

    @Nullable
    @Override
    public Leashable.LeashData getLeashData() {
        return this.leashData;
    }

    @Override
    public void setLeashData(@Nullable Leashable.LeashData leashData) {
        this.leashData = leashData;
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.88F * this.getEyeHeight(), this.getBbWidth() * 0.64F);
    }

    @Override
    public void elasticRangeLeashBehaviour(Entity leashHolder, float distance) {
        Vec3 vec3 = leashHolder.position().subtract(this.position()).normalize().scale(distance - 6.0);
        Vec3 deltaMovement = this.getDeltaMovement();
        boolean flag = deltaMovement.dot(vec3) > 0.0;
        this.setDeltaMovement(deltaMovement.add(vec3.scale(flag ? 0.15F : 0.2F)));
    }

    private AbstractBoat.Status getStatus() {
        AbstractBoat.Status status = this.isUnderwater();
        if (status != null) {
            this.waterLevel = this.getBoundingBox().maxY;
            return status;
        } else if (this.checkInWater()) {
            return AbstractBoat.Status.IN_WATER;
        } else {
            float groundFriction = this.getGroundFriction();
            if (groundFriction > 0.0F) {
                this.landFriction = groundFriction;
                return AbstractBoat.Status.ON_LAND;
            } else {
                return AbstractBoat.Status.IN_AIR;
            }
        }
    }

    public float getWaterLevelAbove() {
        AABB boundingBox = this.getBoundingBox();
        int floor = Mth.floor(boundingBox.minX);
        int ceil = Mth.ceil(boundingBox.maxX);
        int floor1 = Mth.floor(boundingBox.maxY);
        int ceil1 = Mth.ceil(boundingBox.maxY - this.lastYd);
        int floor2 = Mth.floor(boundingBox.minZ);
        int ceil2 = Mth.ceil(boundingBox.maxZ);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        label39:
        for (int i = floor1; i < ceil1; i++) {
            float f = 0.0F;

            for (int i1 = floor; i1 < ceil; i1++) {
                for (int i2 = floor2; i2 < ceil2; i2++) {
                    mutableBlockPos.set(i1, i, i2);
                    FluidState fluidState = this.level().getFluidState(mutableBlockPos);
                    if (fluidState.is(FluidTags.WATER)) {
                        f = Math.max(f, fluidState.getHeight(this.level(), mutableBlockPos));
                    }

                    if (f >= 1.0F) {
                        continue label39;
                    }
                }
            }

            if (f < 1.0F) {
                return mutableBlockPos.getY() + f;
            }
        }

        return ceil1 + 1;
    }

    public float getGroundFriction() {
        AABB boundingBox = this.getBoundingBox();
        AABB aabb = new AABB(boundingBox.minX, boundingBox.minY - 0.001, boundingBox.minZ, boundingBox.maxX, boundingBox.minY, boundingBox.maxZ);
        int i = Mth.floor(aabb.minX) - 1;
        int i1 = Mth.ceil(aabb.maxX) + 1;
        int i2 = Mth.floor(aabb.minY) - 1;
        int i3 = Mth.ceil(aabb.maxY) + 1;
        int i4 = Mth.floor(aabb.minZ) - 1;
        int i5 = Mth.ceil(aabb.maxZ) + 1;
        VoxelShape voxelShape = Shapes.create(aabb);
        float f = 0.0F;
        int i6 = 0;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i7 = i; i7 < i1; i7++) {
            for (int i8 = i4; i8 < i5; i8++) {
                int i9 = (i7 != i && i7 != i1 - 1 ? 0 : 1) + (i8 != i4 && i8 != i5 - 1 ? 0 : 1);
                if (i9 != 2) {
                    for (int i10 = i2; i10 < i3; i10++) {
                        if (i9 <= 0 || i10 != i2 && i10 != i3 - 1) {
                            mutableBlockPos.set(i7, i10, i8);
                            BlockState blockState = this.level().getBlockState(mutableBlockPos);
                            if (!(blockState.getBlock() instanceof WaterlilyBlock)
                                && Shapes.joinIsNotEmpty(
                                    blockState.getCollisionShape(this.level(), mutableBlockPos).move(i7, i10, i8), voxelShape, BooleanOp.AND
                                )) {
                                f += blockState.getBlock().getFriction();
                                i6++;
                            }
                        }
                    }
                }
            }
        }

        return f / i6;
    }

    private boolean checkInWater() {
        AABB boundingBox = this.getBoundingBox();
        int floor = Mth.floor(boundingBox.minX);
        int ceil = Mth.ceil(boundingBox.maxX);
        int floor1 = Mth.floor(boundingBox.minY);
        int ceil1 = Mth.ceil(boundingBox.minY + 0.001);
        int floor2 = Mth.floor(boundingBox.minZ);
        int ceil2 = Mth.ceil(boundingBox.maxZ);
        boolean flag = false;
        this.waterLevel = -Double.MAX_VALUE;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i = floor; i < ceil; i++) {
            for (int i1 = floor1; i1 < ceil1; i1++) {
                for (int i2 = floor2; i2 < ceil2; i2++) {
                    mutableBlockPos.set(i, i1, i2);
                    FluidState fluidState = this.level().getFluidState(mutableBlockPos);
                    if (fluidState.is(FluidTags.WATER)) {
                        float f = i1 + fluidState.getHeight(this.level(), mutableBlockPos);
                        this.waterLevel = Math.max((double)f, this.waterLevel);
                        flag |= boundingBox.minY < f;
                    }
                }
            }
        }

        return flag;
    }

    @Nullable
    private AbstractBoat.Status isUnderwater() {
        AABB boundingBox = this.getBoundingBox();
        double d = boundingBox.maxY + 0.001;
        int floor = Mth.floor(boundingBox.minX);
        int ceil = Mth.ceil(boundingBox.maxX);
        int floor1 = Mth.floor(boundingBox.maxY);
        int ceil1 = Mth.ceil(d);
        int floor2 = Mth.floor(boundingBox.minZ);
        int ceil2 = Mth.ceil(boundingBox.maxZ);
        boolean flag = false;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i = floor; i < ceil; i++) {
            for (int i1 = floor1; i1 < ceil1; i1++) {
                for (int i2 = floor2; i2 < ceil2; i2++) {
                    mutableBlockPos.set(i, i1, i2);
                    FluidState fluidState = this.level().getFluidState(mutableBlockPos);
                    if (fluidState.is(FluidTags.WATER) && d < mutableBlockPos.getY() + fluidState.getHeight(this.level(), mutableBlockPos)) {
                        if (!fluidState.isSource()) {
                            return AbstractBoat.Status.UNDER_FLOWING_WATER;
                        }

                        flag = true;
                    }
                }
            }
        }

        return flag ? AbstractBoat.Status.UNDER_WATER : null;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04;
    }

    private void floatBoat() {
        double d = -this.getGravity();
        double d1 = 0.0;
        this.invFriction = 0.05F;
        if (this.oldStatus == AbstractBoat.Status.IN_AIR && this.status != AbstractBoat.Status.IN_AIR && this.status != AbstractBoat.Status.ON_LAND) {
            this.waterLevel = this.getY(1.0);
            double d2 = this.getWaterLevelAbove() - this.getBbHeight() + 0.101;
            if (this.level().noCollision(this, this.getBoundingBox().move(0.0, d2 - this.getY(), 0.0))) {
                this.setPos(this.getX(), d2, this.getZ());
                this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.0, 1.0));
                this.lastYd = 0.0;
            }

            this.status = AbstractBoat.Status.IN_WATER;
        } else {
            if (this.status == AbstractBoat.Status.IN_WATER) {
                d1 = (this.waterLevel - this.getY()) / this.getBbHeight();
                this.invFriction = 0.9F;
            } else if (this.status == AbstractBoat.Status.UNDER_FLOWING_WATER) {
                d = -7.0E-4;
                this.invFriction = 0.9F;
            } else if (this.status == AbstractBoat.Status.UNDER_WATER) {
                d1 = 0.01F;
                this.invFriction = 0.45F;
            } else if (this.status == AbstractBoat.Status.IN_AIR) {
                this.invFriction = 0.9F;
            } else if (this.status == AbstractBoat.Status.ON_LAND) {
                this.invFriction = this.landFriction;
                if (this.getControllingPassenger() instanceof Player) {
                    this.landFriction /= 2.0F;
                }
            }

            Vec3 deltaMovement = this.getDeltaMovement();
            this.setDeltaMovement(deltaMovement.x * this.invFriction, deltaMovement.y + d, deltaMovement.z * this.invFriction);
            this.deltaRotation = this.deltaRotation * this.invFriction;
            if (d1 > 0.0) {
                Vec3 deltaMovement1 = this.getDeltaMovement();
                this.setDeltaMovement(deltaMovement1.x, (deltaMovement1.y + d1 * (this.getDefaultGravity() / 0.65)) * 0.75, deltaMovement1.z);
            }
        }
    }

    private void controlBoat() {
        if (this.isVehicle()) {
            float f = 0.0F;
            if (this.inputLeft) {
                this.deltaRotation--;
            }

            if (this.inputRight) {
                this.deltaRotation++;
            }

            if (this.inputRight != this.inputLeft && !this.inputUp && !this.inputDown) {
                f += 0.005F;
            }

            this.setYRot(this.getYRot() + this.deltaRotation);
            if (this.inputUp) {
                f += 0.04F;
            }

            if (this.inputDown) {
                f -= 0.005F;
            }

            this.setDeltaMovement(
                this.getDeltaMovement()
                    .add(Mth.sin(-this.getYRot() * (float) (Math.PI / 180.0)) * f, 0.0, Mth.cos(this.getYRot() * (float) (Math.PI / 180.0)) * f)
            );
            this.setPaddleState(this.inputRight && !this.inputLeft || this.inputUp, this.inputLeft && !this.inputRight || this.inputUp);
        }
    }

    protected float getSinglePassengerXOffset() {
        return 0.0F;
    }

    public boolean hasEnoughSpaceFor(Entity entity) {
        return entity.getBbWidth() < this.getBbWidth();
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        super.positionRider(passenger, callback);
        if (!passenger.getType().is(EntityTypeTags.CAN_TURN_IN_BOATS)) {
            passenger.setYRot(passenger.getYRot() + this.deltaRotation);
            passenger.setYHeadRot(passenger.getYHeadRot() + this.deltaRotation);
            this.clampRotation(passenger);
            if (passenger instanceof Animal && this.getPassengers().size() == this.getMaxPassengers()) {
                int i = passenger.getId() % 2 == 0 ? 90 : 270;
                passenger.setYBodyRot(((Animal)passenger).yBodyRot + i);
                passenger.setYHeadRot(passenger.getYHeadRot() + i);
            }
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 collisionHorizontalEscapeVector = getCollisionHorizontalEscapeVector(
            this.getBbWidth() * Mth.SQRT_OF_TWO, passenger.getBbWidth(), passenger.getYRot()
        );
        double d = this.getX() + collisionHorizontalEscapeVector.x;
        double d1 = this.getZ() + collisionHorizontalEscapeVector.z;
        BlockPos blockPos = BlockPos.containing(d, this.getBoundingBox().maxY, d1);
        BlockPos blockPos1 = blockPos.below();
        if (!this.level().isWaterAt(blockPos1)) {
            List<Vec3> list = Lists.newArrayList();
            double blockFloorHeight = this.level().getBlockFloorHeight(blockPos);
            if (DismountHelper.isBlockFloorValid(blockFloorHeight)) {
                list.add(new Vec3(d, blockPos.getY() + blockFloorHeight, d1));
            }

            double blockFloorHeight1 = this.level().getBlockFloorHeight(blockPos1);
            if (DismountHelper.isBlockFloorValid(blockFloorHeight1)) {
                list.add(new Vec3(d, blockPos1.getY() + blockFloorHeight1, d1));
            }

            for (Pose pose : passenger.getDismountPoses()) {
                for (Vec3 vec3 : list) {
                    if (DismountHelper.canDismountTo(this.level(), vec3, passenger, pose)) {
                        passenger.setPose(pose);
                        return vec3;
                    }
                }
            }
        }

        return super.getDismountLocationForPassenger(passenger);
    }

    protected void clampRotation(Entity entity) {
        entity.setYBodyRot(this.getYRot());
        float f = Mth.wrapDegrees(entity.getYRot() - this.getYRot());
        float f1 = Mth.clamp(f, -105.0F, 105.0F);
        entity.yRotO += f1 - f;
        entity.setYRot(entity.getYRot() + f1 - f);
        entity.setYHeadRot(entity.getYRot());
    }

    @Override
    public void onPassengerTurned(Entity entityToUpdate) {
        this.clampRotation(entityToUpdate);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        this.writeLeashData(tag, this.leashData);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.readLeashData(tag);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        InteractionResult interactionResult = super.interact(player, hand);
        if (interactionResult != InteractionResult.PASS) {
            return interactionResult;
        } else {
            return (InteractionResult)(player.isSecondaryUseActive()
                    || !(this.outOfControlTicks < 60.0F)
                    || !this.level().isClientSide && !player.startRiding(this)
                ? InteractionResult.PASS
                : InteractionResult.SUCCESS);
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide && reason.shouldDestroy() && this.isLeashed()) {
            this.dropLeash();
        }

        super.remove(reason);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        this.lastYd = this.getDeltaMovement().y;
        if (!this.isPassenger()) {
            if (onGround) {
                this.resetFallDistance();
            } else if (!this.level().getFluidState(this.blockPosition().below()).is(FluidTags.WATER) && y < 0.0) {
                this.fallDistance -= (float)y;
            }
        }
    }

    public boolean getPaddleState(int side) {
        return this.entityData.get(side == 0 ? DATA_ID_PADDLE_LEFT : DATA_ID_PADDLE_RIGHT) && this.getControllingPassenger() != null;
    }

    private void setBubbleTime(int bubbleTime) {
        this.entityData.set(DATA_ID_BUBBLE_TIME, bubbleTime);
    }

    private int getBubbleTime() {
        return this.entityData.get(DATA_ID_BUBBLE_TIME);
    }

    public float getBubbleAngle(float partialTick) {
        return Mth.lerp(partialTick, this.bubbleAngleO, this.bubbleAngle);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < this.getMaxPassengers() && !this.isEyeInFluid(FluidTags.WATER);
    }

    protected int getMaxPassengers() {
        return 2;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof LivingEntity livingEntity ? livingEntity : super.getControllingPassenger();
    }

    public void setInput(boolean left, boolean right, boolean up, boolean down) {
        this.inputLeft = left;
        this.inputRight = right;
        this.inputUp = up;
        this.inputDown = down;
    }

    @Override
    public boolean isUnderWater() {
        return this.status == AbstractBoat.Status.UNDER_WATER || this.status == AbstractBoat.Status.UNDER_FLOWING_WATER;
    }

    @Override
    protected final Item getDropItem() {
        return this.dropItem.get();
    }

    @Override
    public final ItemStack getPickResult() {
        return new ItemStack(this.dropItem.get());
    }

    public static enum Status {
        IN_WATER,
        UNDER_WATER,
        UNDER_FLOWING_WATER,
        ON_LAND,
        IN_AIR;
    }
}
