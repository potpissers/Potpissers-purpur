package net.minecraft.world.entity.projectile;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OminousItemSpawner;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class AbstractArrow extends Projectile {
    private static final double ARROW_BASE_DAMAGE = 2.0;
    private static final int SHAKE_TIME = 7;
    private static final float WATER_INERTIA = 0.6F;
    private static final float INERTIA = 0.99F;
    private static final EntityDataAccessor<Byte> ID_FLAGS = SynchedEntityData.defineId(AbstractArrow.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> PIERCE_LEVEL = SynchedEntityData.defineId(AbstractArrow.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> IN_GROUND = SynchedEntityData.defineId(AbstractArrow.class, EntityDataSerializers.BOOLEAN);
    private static final int FLAG_CRIT = 1;
    private static final int FLAG_NOPHYSICS = 2;
    @Nullable
    private BlockState lastState;
    protected int inGroundTime;
    public AbstractArrow.Pickup pickup = AbstractArrow.Pickup.DISALLOWED;
    public int shakeTime;
    private int life;
    private double baseDamage = 2.0;
    private SoundEvent soundEvent = this.getDefaultHitGroundSoundEvent();
    @Nullable
    private IntOpenHashSet piercingIgnoreEntityIds;
    @Nullable
    private List<Entity> piercedAndKilledEntities;
    private ItemStack pickupItemStack = this.getDefaultPickupItem();
    @Nullable
    private ItemStack firedFromWeapon = null;

    protected AbstractArrow(EntityType<? extends AbstractArrow> entityType, Level level) {
        super(entityType, level);
    }

    protected AbstractArrow(
        EntityType<? extends AbstractArrow> entityType,
        double x,
        double y,
        double z,
        Level level,
        ItemStack pickupItemStack,
        @Nullable ItemStack firedFromWeapon
    ) {
        this(entityType, level);
        this.pickupItemStack = pickupItemStack.copy();
        this.setCustomName(pickupItemStack.get(DataComponents.CUSTOM_NAME));
        Unit unit = pickupItemStack.remove(DataComponents.INTANGIBLE_PROJECTILE);
        if (unit != null) {
            this.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        }

        this.setPos(x, y, z);
        if (firedFromWeapon != null && level instanceof ServerLevel serverLevel) {
            if (firedFromWeapon.isEmpty()) {
                throw new IllegalArgumentException("Invalid weapon firing an arrow");
            }

            this.firedFromWeapon = firedFromWeapon.copy();
            int piercingCount = EnchantmentHelper.getPiercingCount(serverLevel, firedFromWeapon, this.pickupItemStack);
            if (piercingCount > 0) {
                this.setPierceLevel((byte)piercingCount);
            }
        }
    }

    protected AbstractArrow(
        EntityType<? extends AbstractArrow> entityType, LivingEntity owner, Level level, ItemStack pickupItemStack, @Nullable ItemStack firedFromWeapon
    ) {
        this(entityType, owner.getX(), owner.getEyeY() - 0.1F, owner.getZ(), level, pickupItemStack, firedFromWeapon);
        this.setOwner(owner);
    }

    public void setSoundEvent(SoundEvent soundEvent) {
        this.soundEvent = soundEvent;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d = this.getBoundingBox().getSize() * 10.0;
        if (Double.isNaN(d)) {
            d = 1.0;
        }

        d *= 64.0 * getViewScale();
        return distance < d * d;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ID_FLAGS, (byte)0);
        builder.define(PIERCE_LEVEL, (byte)0);
        builder.define(IN_GROUND, false);
    }

    @Override
    public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        super.shoot(x, y, z, velocity, inaccuracy);
        this.life = 0;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.setPos(x, y, z);
        this.setRot(yRot, xRot);
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        super.lerpMotion(x, y, z);
        this.life = 0;
        if (this.isInGround() && Mth.lengthSquared(x, y, z) > 0.0) {
            this.setInGround(false);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!this.firstTick && this.shakeTime <= 0 && key.equals(IN_GROUND) && this.isInGround()) {
            this.shakeTime = 7;
        }
    }

    @Override
    public void tick() {
        boolean flag = !this.isNoPhysics();
        Vec3 deltaMovement = this.getDeltaMovement();
        BlockPos blockPos = this.blockPosition();
        BlockState blockState = this.level().getBlockState(blockPos);
        if (!blockState.isAir() && flag) {
            VoxelShape collisionShape = blockState.getCollisionShape(this.level(), blockPos);
            if (!collisionShape.isEmpty()) {
                Vec3 vec3 = this.position();

                for (AABB aabb : collisionShape.toAabbs()) {
                    if (aabb.move(blockPos).contains(vec3)) {
                        this.setInGround(true);
                        break;
                    }
                }
            }
        }

        if (this.shakeTime > 0) {
            this.shakeTime--;
        }

        if (this.isInWaterOrRain() || blockState.is(Blocks.POWDER_SNOW)) {
            this.clearFire();
        }

        if (this.isInGround() && flag) {
            if (!this.level().isClientSide()) {
                if (this.lastState != blockState && this.shouldFall()) {
                    this.startFalling();
                } else {
                    this.tickDespawn();
                }
            }

            this.inGroundTime++;
            if (this.isAlive()) {
                this.applyEffectsFromBlocks();
            }
        } else {
            this.inGroundTime = 0;
            Vec3 vec31 = this.position();
            if (this.isInWater()) {
                this.applyInertia(this.getWaterInertia());
                this.addBubbleParticles(vec31);
            }

            if (this.isCritArrow()) {
                for (int i = 0; i < 4; i++) {
                    this.level()
                        .addParticle(
                            ParticleTypes.CRIT,
                            vec31.x + deltaMovement.x * i / 4.0,
                            vec31.y + deltaMovement.y * i / 4.0,
                            vec31.z + deltaMovement.z * i / 4.0,
                            -deltaMovement.x,
                            -deltaMovement.y + 0.2,
                            -deltaMovement.z
                        );
                }
            }

            float f;
            if (!flag) {
                f = (float)(Mth.atan2(-deltaMovement.x, -deltaMovement.z) * 180.0F / (float)Math.PI);
            } else {
                f = (float)(Mth.atan2(deltaMovement.x, deltaMovement.z) * 180.0F / (float)Math.PI);
            }

            float f1 = (float)(Mth.atan2(deltaMovement.y, deltaMovement.horizontalDistance()) * 180.0F / (float)Math.PI);
            this.setXRot(lerpRotation(this.getXRot(), f1));
            this.setYRot(lerpRotation(this.getYRot(), f));
            if (flag) {
                BlockHitResult blockHitResult = this.level()
                    .clipIncludingBorder(new ClipContext(vec31, vec31.add(deltaMovement), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                this.stepMoveAndHit(blockHitResult);
            } else {
                this.setPos(vec31.add(deltaMovement));
                this.applyEffectsFromBlocks();
            }

            if (!this.isInWater()) {
                this.applyInertia(0.99F);
            }

            if (flag && !this.isInGround()) {
                this.applyGravity();
            }

            super.tick();
        }
    }

    private void stepMoveAndHit(BlockHitResult hitResult) {
        while (this.isAlive()) {
            Vec3 vec3 = this.position();
            EntityHitResult entityHitResult = this.findHitEntity(vec3, hitResult.getLocation());
            Vec3 location = Objects.requireNonNullElse(entityHitResult, hitResult).getLocation();
            this.setPos(location);
            this.applyEffectsFromBlocks(vec3, location);
            if (this.portalProcess != null && this.portalProcess.isInsidePortalThisTick()) {
                this.handlePortal();
            }

            if (entityHitResult == null) {
                if (this.isAlive() && hitResult.getType() != HitResult.Type.MISS) {
                    this.hitTargetOrDeflectSelf(hitResult);
                    this.hasImpulse = true;
                }
                break;
            } else if (this.isAlive() && !this.noPhysics) {
                ProjectileDeflection projectileDeflection = this.hitTargetOrDeflectSelf(entityHitResult);
                this.hasImpulse = true;
                if (this.getPierceLevel() > 0 && projectileDeflection == ProjectileDeflection.NONE) {
                    continue;
                }
                break;
            }
        }
    }

    private void applyInertia(float intertia) {
        Vec3 deltaMovement = this.getDeltaMovement();
        this.setDeltaMovement(deltaMovement.scale(intertia));
    }

    private void addBubbleParticles(Vec3 pos) {
        Vec3 deltaMovement = this.getDeltaMovement();

        for (int i = 0; i < 4; i++) {
            float f = 0.25F;
            this.level()
                .addParticle(
                    ParticleTypes.BUBBLE,
                    pos.x - deltaMovement.x * 0.25,
                    pos.y - deltaMovement.y * 0.25,
                    pos.z - deltaMovement.z * 0.25,
                    deltaMovement.x,
                    deltaMovement.y,
                    deltaMovement.z
                );
        }
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    private boolean shouldFall() {
        return this.isInGround() && this.level().noCollision(new AABB(this.position(), this.position()).inflate(0.06));
    }

    private void startFalling() {
        this.setInGround(false);
        Vec3 deltaMovement = this.getDeltaMovement();
        this.setDeltaMovement(deltaMovement.multiply(this.random.nextFloat() * 0.2F, this.random.nextFloat() * 0.2F, this.random.nextFloat() * 0.2F));
        this.life = 0;
    }

    protected boolean isInGround() {
        return this.entityData.get(IN_GROUND);
    }

    protected void setInGround(boolean inGround) {
        this.entityData.set(IN_GROUND, inGround);
    }

    @Override
    public void move(MoverType type, Vec3 pos) {
        super.move(type, pos);
        if (type != MoverType.SELF && this.shouldFall()) {
            this.startFalling();
        }
    }

    protected void tickDespawn() {
        this.life++;
        if (this.life >= 1200) {
            this.discard();
        }
    }

    private void resetPiercedEntities() {
        if (this.piercedAndKilledEntities != null) {
            this.piercedAndKilledEntities.clear();
        }

        if (this.piercingIgnoreEntityIds != null) {
            this.piercingIgnoreEntityIds.clear();
        }
    }

    @Override
    protected void onItemBreak(Item item) {
        this.firedFromWeapon = null;
    }

    @Override
    public void onInsideBubbleColumn(boolean downwards) {
        if (!this.isInGround()) {
            super.onInsideBubbleColumn(downwards);
        }
    }

    @Override
    public void push(double x, double y, double z) {
        if (!this.isInGround()) {
            super.push(x, y, z);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity entity = result.getEntity();
        float f = (float)this.getDeltaMovement().length();
        double d = this.baseDamage;
        Entity owner = this.getOwner();
        DamageSource damageSource = this.damageSources().arrow(this, (Entity)(owner != null ? owner : this));
        if (this.getWeaponItem() != null && this.level() instanceof ServerLevel serverLevel) {
            d = EnchantmentHelper.modifyDamage(serverLevel, this.getWeaponItem(), entity, damageSource, (float)d);
        }

        int ceil = Mth.ceil(Mth.clamp(f * d, 0.0, 2.147483647E9));
        if (this.getPierceLevel() > 0) {
            if (this.piercingIgnoreEntityIds == null) {
                this.piercingIgnoreEntityIds = new IntOpenHashSet(5);
            }

            if (this.piercedAndKilledEntities == null) {
                this.piercedAndKilledEntities = Lists.newArrayListWithCapacity(5);
            }

            if (this.piercingIgnoreEntityIds.size() >= this.getPierceLevel() + 1) {
                this.discard();
                return;
            }

            this.piercingIgnoreEntityIds.add(entity.getId());
        }

        if (this.isCritArrow()) {
            long l = this.random.nextInt(ceil / 2 + 2);
            ceil = (int)Math.min(l + ceil, 2147483647L);
        }

        if (owner instanceof LivingEntity livingEntity) {
            livingEntity.setLastHurtMob(entity);
        }

        boolean flag = entity.getType() == EntityType.ENDERMAN;
        int remainingFireTicks = entity.getRemainingFireTicks();
        if (this.isOnFire() && !flag) {
            entity.igniteForSeconds(5.0F);
        }

        if (entity.hurtOrSimulate(damageSource, ceil)) {
            if (flag) {
                return;
            }

            if (entity instanceof LivingEntity livingEntity1) {
                if (!this.level().isClientSide && this.getPierceLevel() <= 0) {
                    livingEntity1.setArrowCount(livingEntity1.getArrowCount() + 1);
                }

                this.doKnockback(livingEntity1, damageSource);
                if (this.level() instanceof ServerLevel serverLevel1) {
                    EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel1, livingEntity1, damageSource, this.getWeaponItem());
                }

                this.doPostHurtEffects(livingEntity1);
                if (livingEntity1 != owner && livingEntity1 instanceof Player && owner instanceof ServerPlayer && !this.isSilent()) {
                    ((ServerPlayer)owner).connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.ARROW_HIT_PLAYER, 0.0F));
                }

                if (!entity.isAlive() && this.piercedAndKilledEntities != null) {
                    this.piercedAndKilledEntities.add(livingEntity1);
                }

                if (!this.level().isClientSide && owner instanceof ServerPlayer serverPlayer) {
                    if (this.piercedAndKilledEntities != null) {
                        CriteriaTriggers.KILLED_BY_ARROW.trigger(serverPlayer, this.piercedAndKilledEntities, this.firedFromWeapon);
                    } else if (!entity.isAlive()) {
                        CriteriaTriggers.KILLED_BY_ARROW.trigger(serverPlayer, List.of(entity), this.firedFromWeapon);
                    }
                }
            }

            this.playSound(this.soundEvent, 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
            if (this.getPierceLevel() <= 0) {
                this.discard();
            }
        } else {
            entity.setRemainingFireTicks(remainingFireTicks);
            this.deflect(ProjectileDeflection.REVERSE, entity, this.getOwner(), false);
            this.setDeltaMovement(this.getDeltaMovement().scale(0.2));
            if (this.level() instanceof ServerLevel serverLevel2 && this.getDeltaMovement().lengthSqr() < 1.0E-7) {
                if (this.pickup == AbstractArrow.Pickup.ALLOWED) {
                    this.spawnAtLocation(serverLevel2, this.getPickupItem(), 0.1F);
                }

                this.discard();
            }
        }
    }

    protected void doKnockback(LivingEntity entity, DamageSource damageSource) {
        double d = this.firedFromWeapon != null && this.level() instanceof ServerLevel serverLevel
            ? EnchantmentHelper.modifyKnockback(serverLevel, this.firedFromWeapon, entity, damageSource, 0.0F)
            : 0.0F;
        if (d > 0.0) {
            double max = Math.max(0.0, 1.0 - entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            Vec3 vec3 = this.getDeltaMovement().multiply(1.0, 0.0, 1.0).normalize().scale(d * 0.6 * max);
            if (vec3.lengthSqr() > 0.0) {
                entity.push(vec3.x, 0.1, vec3.z);
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        this.lastState = this.level().getBlockState(result.getBlockPos());
        super.onHitBlock(result);
        ItemStack weaponItem = this.getWeaponItem();
        if (this.level() instanceof ServerLevel serverLevel && weaponItem != null) {
            this.hitBlockEnchantmentEffects(serverLevel, result, weaponItem);
        }

        Vec3 deltaMovement = this.getDeltaMovement();
        Vec3 vec3 = new Vec3(Math.signum(deltaMovement.x), Math.signum(deltaMovement.y), Math.signum(deltaMovement.z));
        Vec3 vec31 = vec3.scale(0.05F);
        this.setPos(this.position().subtract(vec31));
        this.setDeltaMovement(Vec3.ZERO);
        this.playSound(this.getHitGroundSoundEvent(), 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
        this.setInGround(true);
        this.shakeTime = 7;
        this.setCritArrow(false);
        this.setPierceLevel((byte)0);
        this.setSoundEvent(SoundEvents.ARROW_HIT);
        this.resetPiercedEntities();
    }

    protected void hitBlockEnchantmentEffects(ServerLevel level, BlockHitResult hitResult, ItemStack stack) {
        Vec3 vec3 = hitResult.getBlockPos().clampLocationWithin(hitResult.getLocation());
        EnchantmentHelper.onHitBlock(
            level,
            stack,
            this.getOwner() instanceof LivingEntity livingEntity ? livingEntity : null,
            this,
            null,
            vec3,
            level.getBlockState(hitResult.getBlockPos()),
            item -> this.firedFromWeapon = null
        );
    }

    @Override
    public ItemStack getWeaponItem() {
        return this.firedFromWeapon;
    }

    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.ARROW_HIT;
    }

    protected final SoundEvent getHitGroundSoundEvent() {
        return this.soundEvent;
    }

    protected void doPostHurtEffects(LivingEntity target) {
    }

    @Nullable
    protected EntityHitResult findHitEntity(Vec3 startVec, Vec3 endVec) {
        return ProjectileUtil.getEntityHitResult(
            this.level(), this, startVec, endVec, this.getBoundingBox().expandTowards(this.getDeltaMovement()).inflate(1.0), this::canHitEntity
        );
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return (!(target instanceof Player) || !(this.getOwner() instanceof Player player && !player.canHarmPlayer((Player)target)))
            && super.canHitEntity(target)
            && (this.piercingIgnoreEntityIds == null || !this.piercingIgnoreEntityIds.contains(target.getId()));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putShort("life", (short)this.life);
        if (this.lastState != null) {
            compound.put("inBlockState", NbtUtils.writeBlockState(this.lastState));
        }

        compound.putByte("shake", (byte)this.shakeTime);
        compound.putBoolean("inGround", this.isInGround());
        compound.putByte("pickup", (byte)this.pickup.ordinal());
        compound.putDouble("damage", this.baseDamage);
        compound.putBoolean("crit", this.isCritArrow());
        compound.putByte("PierceLevel", this.getPierceLevel());
        compound.putString("SoundEvent", BuiltInRegistries.SOUND_EVENT.getKey(this.soundEvent).toString());
        compound.put("item", this.pickupItemStack.save(this.registryAccess()));
        if (this.firedFromWeapon != null) {
            compound.put("weapon", this.firedFromWeapon.save(this.registryAccess(), new CompoundTag()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.life = compound.getShort("life");
        if (compound.contains("inBlockState", 10)) {
            this.lastState = NbtUtils.readBlockState(this.level().holderLookup(Registries.BLOCK), compound.getCompound("inBlockState"));
        }

        this.shakeTime = compound.getByte("shake") & 255;
        this.setInGround(compound.getBoolean("inGround"));
        if (compound.contains("damage", 99)) {
            this.baseDamage = compound.getDouble("damage");
        }

        this.pickup = AbstractArrow.Pickup.byOrdinal(compound.getByte("pickup"));
        this.setCritArrow(compound.getBoolean("crit"));
        this.setPierceLevel(compound.getByte("PierceLevel"));
        if (compound.contains("SoundEvent", 8)) {
            this.soundEvent = BuiltInRegistries.SOUND_EVENT
                .getOptional(ResourceLocation.parse(compound.getString("SoundEvent")))
                .orElse(this.getDefaultHitGroundSoundEvent());
        }

        if (compound.contains("item", 10)) {
            this.setPickupItemStack(ItemStack.parse(this.registryAccess(), compound.getCompound("item")).orElse(this.getDefaultPickupItem()));
        } else {
            this.setPickupItemStack(this.getDefaultPickupItem());
        }

        if (compound.contains("weapon", 10)) {
            this.firedFromWeapon = ItemStack.parse(this.registryAccess(), compound.getCompound("weapon")).orElse(null);
        } else {
            this.firedFromWeapon = null;
        }
    }

    @Override
    public void setOwner(@Nullable Entity entity) {
        super.setOwner(entity);

        this.pickup = switch (entity) {
            case Player player when this.pickup == AbstractArrow.Pickup.DISALLOWED -> AbstractArrow.Pickup.ALLOWED;
            case OminousItemSpawner ominousItemSpawner -> AbstractArrow.Pickup.DISALLOWED;
            case null, default -> this.pickup;
        };
    }

    @Override
    public void playerTouch(Player entity) {
        if (!this.level().isClientSide && (this.isInGround() || this.isNoPhysics()) && this.shakeTime <= 0) {
            if (this.tryPickup(entity)) {
                entity.take(this, 1);
                this.discard();
            }
        }
    }

    protected boolean tryPickup(Player player) {
        return switch (this.pickup) {
            case DISALLOWED -> false;
            case ALLOWED -> player.getInventory().add(this.getPickupItem());
            case CREATIVE_ONLY -> player.hasInfiniteMaterials();
        };
    }

    protected ItemStack getPickupItem() {
        return this.pickupItemStack.copy();
    }

    protected abstract ItemStack getDefaultPickupItem();

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    public ItemStack getPickupItemStackOrigin() {
        return this.pickupItemStack;
    }

    public void setBaseDamage(double baseDamage) {
        this.baseDamage = baseDamage;
    }

    public double getBaseDamage() {
        return this.baseDamage;
    }

    @Override
    public boolean isAttackable() {
        return this.getType().is(EntityTypeTags.REDIRECTABLE_PROJECTILE);
    }

    public void setCritArrow(boolean critArrow) {
        this.setFlag(1, critArrow);
    }

    private void setPierceLevel(byte pierceLevel) {
        this.entityData.set(PIERCE_LEVEL, pierceLevel);
    }

    private void setFlag(int id, boolean value) {
        byte b = this.entityData.get(ID_FLAGS);
        if (value) {
            this.entityData.set(ID_FLAGS, (byte)(b | id));
        } else {
            this.entityData.set(ID_FLAGS, (byte)(b & ~id));
        }
    }

    protected void setPickupItemStack(ItemStack pickupItemStack) {
        if (!pickupItemStack.isEmpty()) {
            this.pickupItemStack = pickupItemStack;
        } else {
            this.pickupItemStack = this.getDefaultPickupItem();
        }
    }

    public boolean isCritArrow() {
        byte b = this.entityData.get(ID_FLAGS);
        return (b & 1) != 0;
    }

    public byte getPierceLevel() {
        return this.entityData.get(PIERCE_LEVEL);
    }

    public void setBaseDamageFromMob(float velocity) {
        this.setBaseDamage(velocity * 2.0F + this.random.triangle(this.level().getDifficulty().getId() * 0.11, 0.57425));
    }

    protected float getWaterInertia() {
        return 0.6F;
    }

    public void setNoPhysics(boolean noPhysics) {
        this.noPhysics = noPhysics;
        this.setFlag(2, noPhysics);
    }

    public boolean isNoPhysics() {
        return !this.level().isClientSide ? this.noPhysics : (this.entityData.get(ID_FLAGS) & 2) != 0;
    }

    @Override
    public boolean isPickable() {
        return super.isPickable() && !this.isInGround();
    }

    @Override
    public SlotAccess getSlot(int slot) {
        return slot == 0 ? SlotAccess.of(this::getPickupItemStackOrigin, this::setPickupItemStack) : super.getSlot(slot);
    }

    @Override
    protected boolean shouldBounceOnWorldBorder() {
        return true;
    }

    public static enum Pickup {
        DISALLOWED,
        ALLOWED,
        CREATIVE_ONLY;

        public static AbstractArrow.Pickup byOrdinal(int ordinal) {
            if (ordinal < 0 || ordinal > values().length) {
                ordinal = 0;
            }

            return values()[ordinal];
        }
    }
}
