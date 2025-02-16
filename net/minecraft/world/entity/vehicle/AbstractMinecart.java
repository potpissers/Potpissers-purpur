package net.minecraft.world.entity.vehicle;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public abstract class AbstractMinecart extends VehicleEntity {
    private static final Vec3 LOWERED_PASSENGER_ATTACHMENT = new Vec3(0.0, 0.0, 0.0);
    private static final EntityDataAccessor<Integer> DATA_ID_DISPLAY_BLOCK = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ID_DISPLAY_OFFSET = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ID_CUSTOM_DISPLAY = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.BOOLEAN);
    private static final ImmutableMap<Pose, ImmutableList<Integer>> POSE_DISMOUNT_HEIGHTS = ImmutableMap.of(
        Pose.STANDING, ImmutableList.of(0, 1, -1), Pose.CROUCHING, ImmutableList.of(0, 1, -1), Pose.SWIMMING, ImmutableList.of(0, 1)
    );
    protected static final float WATER_SLOWDOWN_FACTOR = 0.95F;
    private boolean onRails;
    private boolean flipped;
    private final MinecartBehavior behavior;
    private static final Map<RailShape, Pair<Vec3i, Vec3i>> EXITS = Util.make(Maps.newEnumMap(RailShape.class), map -> {
        Vec3i unitVec3i = Direction.WEST.getUnitVec3i();
        Vec3i unitVec3i1 = Direction.EAST.getUnitVec3i();
        Vec3i unitVec3i2 = Direction.NORTH.getUnitVec3i();
        Vec3i unitVec3i3 = Direction.SOUTH.getUnitVec3i();
        Vec3i vec3i = unitVec3i.below();
        Vec3i vec3i1 = unitVec3i1.below();
        Vec3i vec3i2 = unitVec3i2.below();
        Vec3i vec3i3 = unitVec3i3.below();
        map.put(RailShape.NORTH_SOUTH, Pair.of(unitVec3i2, unitVec3i3));
        map.put(RailShape.EAST_WEST, Pair.of(unitVec3i, unitVec3i1));
        map.put(RailShape.ASCENDING_EAST, Pair.of(vec3i, unitVec3i1));
        map.put(RailShape.ASCENDING_WEST, Pair.of(unitVec3i, vec3i1));
        map.put(RailShape.ASCENDING_NORTH, Pair.of(unitVec3i2, vec3i3));
        map.put(RailShape.ASCENDING_SOUTH, Pair.of(vec3i2, unitVec3i3));
        map.put(RailShape.SOUTH_EAST, Pair.of(unitVec3i3, unitVec3i1));
        map.put(RailShape.SOUTH_WEST, Pair.of(unitVec3i3, unitVec3i));
        map.put(RailShape.NORTH_WEST, Pair.of(unitVec3i2, unitVec3i));
        map.put(RailShape.NORTH_EAST, Pair.of(unitVec3i2, unitVec3i1));
    });

    protected AbstractMinecart(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
        if (useExperimentalMovement(level)) {
            this.behavior = new NewMinecartBehavior(this);
        } else {
            this.behavior = new OldMinecartBehavior(this);
        }
    }

    protected AbstractMinecart(EntityType<?> entityType, Level level, double x, double y, double z) {
        this(entityType, level);
        this.setInitialPos(x, y, z);
    }

    public void setInitialPos(double x, double y, double z) {
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
    }

    @Nullable
    public static <T extends AbstractMinecart> T createMinecart(
        Level level, double x, double y, double z, EntityType<T> type, EntitySpawnReason spawnReason, ItemStack spawnedFrom, @Nullable Player player
    ) {
        T abstractMinecart = (T)type.create(level, spawnReason);
        if (abstractMinecart != null) {
            abstractMinecart.setInitialPos(x, y, z);
            EntityType.createDefaultStackConfig(level, spawnedFrom, player).accept(abstractMinecart);
            if (abstractMinecart.getBehavior() instanceof NewMinecartBehavior newMinecartBehavior) {
                BlockPos currentBlockPosOrRailBelow = abstractMinecart.getCurrentBlockPosOrRailBelow();
                BlockState blockState = level.getBlockState(currentBlockPosOrRailBelow);
                newMinecartBehavior.adjustToRails(currentBlockPosOrRailBelow, blockState, true);
            }
        }

        return abstractMinecart;
    }

    public MinecartBehavior getBehavior() {
        return this.behavior;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.EVENTS;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ID_DISPLAY_BLOCK, Block.getId(Blocks.AIR.defaultBlockState()));
        builder.define(DATA_ID_DISPLAY_OFFSET, 6);
        builder.define(DATA_ID_CUSTOM_DISPLAY, false);
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return AbstractBoat.canVehicleCollide(this, entity);
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public Vec3 getRelativePortalPosition(Direction.Axis axis, BlockUtil.FoundRectangle portal) {
        return LivingEntity.resetForwardDirectionOfRelativePortalPosition(super.getRelativePortalPosition(axis, portal));
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
        boolean flag = entity instanceof Villager || entity instanceof WanderingTrader;
        return flag ? LOWERED_PASSENGER_ATTACHMENT : super.getPassengerAttachmentPoint(entity, dimensions, partialTick);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity livingEntity) {
        Direction motionDirection = this.getMotionDirection();
        if (motionDirection.getAxis() == Direction.Axis.Y) {
            return super.getDismountLocationForPassenger(livingEntity);
        } else {
            int[][] ints = DismountHelper.offsetsForDirection(motionDirection);
            BlockPos blockPos = this.blockPosition();
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
            ImmutableList<Pose> dismountPoses = livingEntity.getDismountPoses();

            for (Pose pose : dismountPoses) {
                EntityDimensions dimensions = livingEntity.getDimensions(pose);
                float f = Math.min(dimensions.width(), 1.0F) / 2.0F;

                for (int i : POSE_DISMOUNT_HEIGHTS.get(pose)) {
                    for (int[] ints1 : ints) {
                        mutableBlockPos.set(blockPos.getX() + ints1[0], blockPos.getY() + i, blockPos.getZ() + ints1[1]);
                        double blockFloorHeight = this.level()
                            .getBlockFloorHeight(
                                DismountHelper.nonClimbableShape(this.level(), mutableBlockPos),
                                () -> DismountHelper.nonClimbableShape(this.level(), mutableBlockPos.below())
                            );
                        if (DismountHelper.isBlockFloorValid(blockFloorHeight)) {
                            AABB aabb = new AABB(-f, 0.0, -f, f, dimensions.height(), f);
                            Vec3 vec3 = Vec3.upFromBottomCenterOf(mutableBlockPos, blockFloorHeight);
                            if (DismountHelper.canDismountTo(this.level(), livingEntity, aabb.move(vec3))) {
                                livingEntity.setPose(pose);
                                return vec3;
                            }
                        }
                    }
                }
            }

            double d = this.getBoundingBox().maxY;
            mutableBlockPos.set((double)blockPos.getX(), d, (double)blockPos.getZ());

            for (Pose pose1 : dismountPoses) {
                double d1 = livingEntity.getDimensions(pose1).height();
                int ceil = Mth.ceil(d - mutableBlockPos.getY() + d1);
                double d2 = DismountHelper.findCeilingFrom(mutableBlockPos, ceil, pos -> this.level().getBlockState(pos).getCollisionShape(this.level(), pos));
                if (d + d1 <= d2) {
                    livingEntity.setPose(pose1);
                    break;
                }
            }

            return super.getDismountLocationForPassenger(livingEntity);
        }
    }

    @Override
    protected float getBlockSpeedFactor() {
        BlockState blockState = this.level().getBlockState(this.blockPosition());
        return blockState.is(BlockTags.RAILS) ? 1.0F : super.getBlockSpeedFactor();
    }

    @Override
    public void animateHurt(float yaw) {
        this.setHurtDir(-this.getHurtDir());
        this.setHurtTime(10);
        this.setDamage(this.getDamage() + this.getDamage() * 10.0F);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    public static Pair<Vec3i, Vec3i> exits(RailShape shape) {
        return EXITS.get(shape);
    }

    @Override
    public Direction getMotionDirection() {
        return this.behavior.getMotionDirection();
    }

    @Override
    protected double getDefaultGravity() {
        return this.isInWater() ? 0.005 : 0.04;
    }

    @Override
    public void tick() {
        if (this.getHurtTime() > 0) {
            this.setHurtTime(this.getHurtTime() - 1);
        }

        if (this.getDamage() > 0.0F) {
            this.setDamage(this.getDamage() - 1.0F);
        }

        this.checkBelowWorld();
        this.handlePortal();
        this.behavior.tick();
        this.updateInWaterStateAndDoFluidPushing();
        if (this.isInLava()) {
            this.lavaHurt();
            this.fallDistance *= 0.5F;
        }

        this.firstTick = false;
    }

    public boolean isFirstTick() {
        return this.firstTick;
    }

    public BlockPos getCurrentBlockPosOrRailBelow() {
        int floor = Mth.floor(this.getX());
        int floor1 = Mth.floor(this.getY());
        int floor2 = Mth.floor(this.getZ());
        if (useExperimentalMovement(this.level())) {
            double d = this.getY() - 0.1 - 1.0E-5F;
            if (this.level().getBlockState(BlockPos.containing(floor, d, floor2)).is(BlockTags.RAILS)) {
                floor1 = Mth.floor(d);
            }
        } else if (this.level().getBlockState(new BlockPos(floor, floor1 - 1, floor2)).is(BlockTags.RAILS)) {
            floor1--;
        }

        return new BlockPos(floor, floor1, floor2);
    }

    protected double getMaxSpeed(ServerLevel level) {
        return this.behavior.getMaxSpeed(level);
    }

    public void activateMinecart(int x, int y, int z, boolean powered) {
    }

    @Override
    public void lerpPositionAndRotationStep(int steps, double targetX, double targetY, double targetZ, double targetYRot, double targetXRot) {
        super.lerpPositionAndRotationStep(steps, targetX, targetY, targetZ, targetYRot, targetXRot);
    }

    @Override
    public void applyGravity() {
        super.applyGravity();
    }

    @Override
    public void reapplyPosition() {
        super.reapplyPosition();
    }

    @Override
    public boolean updateInWaterStateAndDoFluidPushing() {
        return super.updateInWaterStateAndDoFluidPushing();
    }

    @Override
    public Vec3 getKnownMovement() {
        return this.behavior.getKnownMovement(super.getKnownMovement());
    }

    @Override
    public void cancelLerp() {
        this.behavior.cancelLerp();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.behavior.lerpTo(x, y, z, yRot, xRot, steps);
    }

    @Override
    public double lerpTargetX() {
        return this.behavior.lerpTargetX();
    }

    @Override
    public double lerpTargetY() {
        return this.behavior.lerpTargetY();
    }

    @Override
    public double lerpTargetZ() {
        return this.behavior.lerpTargetZ();
    }

    @Override
    public float lerpTargetXRot() {
        return this.behavior.lerpTargetXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return this.behavior.lerpTargetYRot();
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        this.behavior.lerpMotion(x, y, z);
    }

    protected void moveAlongTrack(ServerLevel level) {
        this.behavior.moveAlongTrack(level);
    }

    protected void comeOffTrack(ServerLevel level) {
        double maxSpeed = this.getMaxSpeed(level);
        Vec3 deltaMovement = this.getDeltaMovement();
        this.setDeltaMovement(Mth.clamp(deltaMovement.x, -maxSpeed, maxSpeed), deltaMovement.y, Mth.clamp(deltaMovement.z, -maxSpeed, maxSpeed));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        if (!this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.95));
        }
    }

    protected double makeStepAlongTrack(BlockPos pos, RailShape railShape, double speed) {
        return this.behavior.stepAlongTrack(pos, railShape, speed);
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        if (useExperimentalMovement(this.level())) {
            Vec3 vec3 = this.position().add(movement);
            super.move(type, movement);
            boolean flag = this.behavior.pushAndPickupEntities();
            if (flag) {
                super.move(type, vec3.subtract(this.position()));
            }

            if (type.equals(MoverType.PISTON)) {
                this.onRails = false;
            }
        } else {
            super.move(type, movement);
            this.applyEffectsFromBlocks();
        }
    }

    @Override
    public void applyEffectsFromBlocks() {
        if (!useExperimentalMovement(this.level())) {
            this.applyEffectsFromBlocks(this.position(), this.position());
        } else {
            super.applyEffectsFromBlocks();
        }
    }

    @Override
    public boolean isOnRails() {
        return this.onRails;
    }

    public void setOnRails(boolean onRails) {
        this.onRails = onRails;
    }

    public boolean isFlipped() {
        return this.flipped;
    }

    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
    }

    public Vec3 getRedstoneDirection(BlockPos pos) {
        BlockState blockState = this.level().getBlockState(pos);
        if (blockState.is(Blocks.POWERED_RAIL) && blockState.getValue(PoweredRailBlock.POWERED)) {
            RailShape railShape = blockState.getValue(((BaseRailBlock)blockState.getBlock()).getShapeProperty());
            if (railShape == RailShape.EAST_WEST) {
                if (this.isRedstoneConductor(pos.west())) {
                    return new Vec3(1.0, 0.0, 0.0);
                }

                if (this.isRedstoneConductor(pos.east())) {
                    return new Vec3(-1.0, 0.0, 0.0);
                }
            } else if (railShape == RailShape.NORTH_SOUTH) {
                if (this.isRedstoneConductor(pos.north())) {
                    return new Vec3(0.0, 0.0, 1.0);
                }

                if (this.isRedstoneConductor(pos.south())) {
                    return new Vec3(0.0, 0.0, -1.0);
                }
            }

            return Vec3.ZERO;
        } else {
            return Vec3.ZERO;
        }
    }

    public boolean isRedstoneConductor(BlockPos pos) {
        return this.level().getBlockState(pos).isRedstoneConductor(this.level(), pos);
    }

    protected Vec3 applyNaturalSlowdown(Vec3 speed) {
        double slowdownFactor = this.behavior.getSlowdownFactor();
        Vec3 vec3 = speed.multiply(slowdownFactor, 0.0, slowdownFactor);
        if (this.isInWater()) {
            vec3 = vec3.scale(0.95F);
        }

        return vec3;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.getBoolean("CustomDisplayTile")) {
            this.setDisplayBlockState(NbtUtils.readBlockState(this.level().holderLookup(Registries.BLOCK), compound.getCompound("DisplayState")));
            this.setDisplayOffset(compound.getInt("DisplayOffset"));
        }

        this.flipped = compound.getBoolean("FlippedRotation");
        this.firstTick = compound.getBoolean("HasTicked");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        if (this.hasCustomDisplay()) {
            compound.putBoolean("CustomDisplayTile", true);
            compound.put("DisplayState", NbtUtils.writeBlockState(this.getDisplayBlockState()));
            compound.putInt("DisplayOffset", this.getDisplayOffset());
        }

        compound.putBoolean("FlippedRotation", this.flipped);
        compound.putBoolean("HasTicked", this.firstTick);
    }

    @Override
    public void push(Entity entity) {
        if (!this.level().isClientSide) {
            if (!entity.noPhysics && !this.noPhysics) {
                if (!this.hasPassenger(entity)) {
                    double d = entity.getX() - this.getX();
                    double d1 = entity.getZ() - this.getZ();
                    double d2 = d * d + d1 * d1;
                    if (d2 >= 1.0E-4F) {
                        d2 = Math.sqrt(d2);
                        d /= d2;
                        d1 /= d2;
                        double d3 = 1.0 / d2;
                        if (d3 > 1.0) {
                            d3 = 1.0;
                        }

                        d *= d3;
                        d1 *= d3;
                        d *= 0.1F;
                        d1 *= 0.1F;
                        d *= 0.5;
                        d1 *= 0.5;
                        if (entity instanceof AbstractMinecart abstractMinecart) {
                            this.pushOtherMinecart(abstractMinecart, d, d1);
                        } else {
                            this.push(-d, 0.0, -d1);
                            entity.push(d / 4.0, 0.0, d1 / 4.0);
                        }
                    }
                }
            }
        }
    }

    private void pushOtherMinecart(AbstractMinecart otherMinecart, double deltaX, double deltaZ) {
        double d;
        double d1;
        if (useExperimentalMovement(this.level())) {
            d = this.getDeltaMovement().x;
            d1 = this.getDeltaMovement().z;
        } else {
            d = otherMinecart.getX() - this.getX();
            d1 = otherMinecart.getZ() - this.getZ();
        }

        Vec3 vec3 = new Vec3(d, 0.0, d1).normalize();
        Vec3 vec31 = new Vec3(Mth.cos(this.getYRot() * (float) (Math.PI / 180.0)), 0.0, Mth.sin(this.getYRot() * (float) (Math.PI / 180.0))).normalize();
        double abs = Math.abs(vec3.dot(vec31));
        if (!(abs < 0.8F) || useExperimentalMovement(this.level())) {
            Vec3 deltaMovement = this.getDeltaMovement();
            Vec3 deltaMovement1 = otherMinecart.getDeltaMovement();
            if (otherMinecart.isFurnace() && !this.isFurnace()) {
                this.setDeltaMovement(deltaMovement.multiply(0.2, 1.0, 0.2));
                this.push(deltaMovement1.x - deltaX, 0.0, deltaMovement1.z - deltaZ);
                otherMinecart.setDeltaMovement(deltaMovement1.multiply(0.95, 1.0, 0.95));
            } else if (!otherMinecart.isFurnace() && this.isFurnace()) {
                otherMinecart.setDeltaMovement(deltaMovement1.multiply(0.2, 1.0, 0.2));
                otherMinecart.push(deltaMovement.x + deltaX, 0.0, deltaMovement.z + deltaZ);
                this.setDeltaMovement(deltaMovement.multiply(0.95, 1.0, 0.95));
            } else {
                double d2 = (deltaMovement1.x + deltaMovement.x) / 2.0;
                double d3 = (deltaMovement1.z + deltaMovement.z) / 2.0;
                this.setDeltaMovement(deltaMovement.multiply(0.2, 1.0, 0.2));
                this.push(d2 - deltaX, 0.0, d3 - deltaZ);
                otherMinecart.setDeltaMovement(deltaMovement1.multiply(0.2, 1.0, 0.2));
                otherMinecart.push(d2 + deltaX, 0.0, d3 + deltaZ);
            }
        }
    }

    public BlockState getDisplayBlockState() {
        return !this.hasCustomDisplay() ? this.getDefaultDisplayBlockState() : Block.stateById(this.getEntityData().get(DATA_ID_DISPLAY_BLOCK));
    }

    public BlockState getDefaultDisplayBlockState() {
        return Blocks.AIR.defaultBlockState();
    }

    public int getDisplayOffset() {
        return !this.hasCustomDisplay() ? this.getDefaultDisplayOffset() : this.getEntityData().get(DATA_ID_DISPLAY_OFFSET);
    }

    public int getDefaultDisplayOffset() {
        return 6;
    }

    public void setDisplayBlockState(BlockState displayState) {
        this.getEntityData().set(DATA_ID_DISPLAY_BLOCK, Block.getId(displayState));
        this.setCustomDisplay(true);
    }

    public void setDisplayOffset(int displayOffset) {
        this.getEntityData().set(DATA_ID_DISPLAY_OFFSET, displayOffset);
        this.setCustomDisplay(true);
    }

    public boolean hasCustomDisplay() {
        return this.getEntityData().get(DATA_ID_CUSTOM_DISPLAY);
    }

    public void setCustomDisplay(boolean customDisplay) {
        this.getEntityData().set(DATA_ID_CUSTOM_DISPLAY, customDisplay);
    }

    public static boolean useExperimentalMovement(Level level) {
        return level.enabledFeatures().contains(FeatureFlags.MINECART_IMPROVEMENTS);
    }

    @Override
    public abstract ItemStack getPickResult();

    public boolean isRideable() {
        return false;
    }

    public boolean isFurnace() {
        return false;
    }
}
