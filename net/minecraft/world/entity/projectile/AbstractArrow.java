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
import net.minecraft.world.entity.item.ItemEntity;
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
    public int life; // Paper - private -> public
    private double baseDamage = 2.0;
    public SoundEvent soundEvent = this.getDefaultHitGroundSoundEvent(); // Paper - private -> public
    @Nullable
    private IntOpenHashSet piercingIgnoreEntityIds;
    @Nullable
    private List<Entity> piercedAndKilledEntities;
    public ItemStack pickupItemStack = this.getDefaultPickupItem(); // Paper - private -> public
    @Nullable
    public ItemStack firedFromWeapon = null; // Paper - private -> public
    public net.minecraft.world.item.enchantment.ItemEnchantments actualEnchantments = net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY; // Purpur - Add an option to fix MC-3304 projectile looting

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
        // CraftBukkit start - handle the owner before the rest of things
        this(entityType, x, y, z, level, pickupItemStack, firedFromWeapon, null);
    }
    protected AbstractArrow(EntityType<? extends AbstractArrow> entityType, double x, double y, double z, Level level, ItemStack pickupItemStack, @Nullable ItemStack firedFromWeapon, @Nullable LivingEntity ownerEntity) {
        this(entityType, level);
        this.setOwner(ownerEntity);
        // CraftBukkit end
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
        this(entityType, owner.getX(), owner.getEyeY() - 0.1F, owner.getZ(), level, pickupItemStack, firedFromWeapon, owner); // CraftBukkit
        // this.setOwner(owner); // SPIGOT-7744 - Moved to the above constructor
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
            if (this.tickCount > 200) this.tickDespawn(); // Paper - tick despawnCounter regardless after 10 seconds
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
                    this.preHitTargetOrDeflectSelf(hitResult); // CraftBukkit - projectile hit event
                    this.hasImpulse = true;
                }
                break;
            } else if (this.isAlive() && !this.noPhysics) {
                ProjectileDeflection projectileDeflection = this.preHitTargetOrDeflectSelf(entityHitResult); // CraftBukkit - projectile hit event
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

    // Paper start - Fix cancelling ProjectileHitEvent for piercing arrows
    @Override
    public ProjectileDeflection preHitTargetOrDeflectSelf(HitResult hitResult) {
        if (hitResult instanceof EntityHitResult entityHitResult && this.hitCancelled && this.getPierceLevel() > 0) {
            if (this.piercingIgnoreEntityIds == null) {
                this.piercingIgnoreEntityIds = new IntOpenHashSet(5);
            }
            this.piercingIgnoreEntityIds.add(entityHitResult.getEntity().getId());
        }
        return super.preHitTargetOrDeflectSelf(hitResult);
    }
    // Paper end - Fix cancelling ProjectileHitEvent for piercing arrows

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    private boolean shouldFall() {
        return this.isInGround() && this.level().noCollision(new AABB(this.position(), this.position()).inflate(0.06)); // Paper - getAttachedBlocks api; diff on change
    }

    private void startFalling() {
        this.setInGround(false);
        Vec3 deltaMovement = this.getDeltaMovement();
        this.setDeltaMovement(deltaMovement.multiply(this.random.nextFloat() * 0.2F, this.random.nextFloat() * 0.2F, this.random.nextFloat() * 0.2F));
        if (this.level().purpurConfig.arrowMovementResetsDespawnCounter) this.life = 0; // Purpur - Arrows should not reset despawn counter
    }

    public boolean isInGround() { // Paper - protected -> public
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
        if (this.life >= (this.pickup == Pickup.CREATIVE_ONLY ? this.level().paperConfig().entities.spawning.creativeArrowDespawnRate.value() : (this.pickup == Pickup.DISALLOWED ? this.level().paperConfig().entities.spawning.nonPlayerArrowDespawnRate.value() : ((this instanceof ThrownTrident) ? this.level().spigotConfig.tridentDespawnRate : this.level().spigotConfig.arrowDespawnRate)))) { // Spigot // Paper - Configurable non-player arrow despawn rate; TODO: Extract this to init?
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
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
    public void push(double x, double y, double z, @Nullable Entity pushingEntity) { // Paper - add push source entity param
        if (!this.isInGround()) {
            super.push(x, y, z, pushingEntity); // Paper - add push source entity param
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
                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
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

        if (this.isCritArrow()) damageSource = damageSource.critical(); // Paper - add critical damage API
        boolean flag = entity.getType() == EntityType.ENDERMAN;
        int remainingFireTicks = entity.getRemainingFireTicks();
        if (this.isOnFire() && !flag) {
            // CraftBukkit start
            org.bukkit.event.entity.EntityCombustByEntityEvent combustEvent = new org.bukkit.event.entity.EntityCombustByEntityEvent(this.getBukkitEntity(), entity.getBukkitEntity(), 5.0F);
            org.bukkit.Bukkit.getPluginManager().callEvent(combustEvent);
            if (!combustEvent.isCancelled()) {
                entity.igniteForSeconds(combustEvent.getDuration(), false);
            }
            // CraftBukkit end
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
                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
            }
        } else {
            entity.setRemainingFireTicks(remainingFireTicks);
            this.deflect(ProjectileDeflection.REVERSE, entity, this.getOwner(), false);
            this.setDeltaMovement(this.getDeltaMovement().scale(0.2));
            if (this.level() instanceof ServerLevel serverLevel2 && this.getDeltaMovement().lengthSqr() < 1.0E-7) {
                if (this.pickup == AbstractArrow.Pickup.ALLOWED) {
                    this.spawnAtLocation(serverLevel2, this.getPickupItem(), 0.1F);
                }

                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
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
                entity.push(vec3.x, 0.1, vec3.z, this); // Paper - Add EntityKnockbackByEntityEvent and EntityPushedByEntityAttackEvent
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

    // Purpur start - Add an option to fix MC-3304 projectile looting
    public void setActualEnchantments(net.minecraft.world.item.enchantment.ItemEnchantments actualEnchantments) {
        this.actualEnchantments = actualEnchantments;
    }
    // Purpur end - Add an option to fix MC-3304 projectile looting

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
                .getOptional(ResourceLocation.tryParse(compound.getString("SoundEvent"))) // Paper - Validate resource location
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
        // Paper start - Fix PickupStatus getting reset
        this.setOwner(entity, true);
    }

    public void setOwner(@Nullable Entity entity, boolean resetPickup) {
        // Paper end - Fix PickupStatus getting reset
        super.setOwner(entity);
        if (!resetPickup) return; // Paper - Fix PickupStatus getting reset

        this.pickup = switch (entity) {
            case Player player when this.pickup == AbstractArrow.Pickup.DISALLOWED -> AbstractArrow.Pickup.ALLOWED;
            case OminousItemSpawner ominousItemSpawner -> AbstractArrow.Pickup.DISALLOWED;
            case null, default -> this.pickup;
        };
    }

    @Override
    public void playerTouch(Player entity) {
        if (!this.level().isClientSide && (this.isInGround() || this.isNoPhysics()) && this.shakeTime <= 0) {
            // CraftBukkit start
            ItemStack itemstack = this.getPickupItem();
            if (this.pickup == Pickup.ALLOWED && !itemstack.isEmpty() && entity.getInventory().canHold(itemstack) > 0) {
                ItemEntity item = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), itemstack);
                org.bukkit.event.player.PlayerPickupArrowEvent event = new org.bukkit.event.player.PlayerPickupArrowEvent((org.bukkit.entity.Player) entity.getBukkitEntity(), (org.bukkit.entity.Item) item.getBukkitEntity(), (org.bukkit.entity.AbstractArrow) this.getBukkitEntity());
                // event.setCancelled(!entityhuman.canPickUpLoot); TODO
                this.level().getCraftServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return;
                }
                itemstack = item.getItem();
            }

            if ((this.pickup == AbstractArrow.Pickup.ALLOWED && entity.getInventory().add(itemstack)) || (this.pickup == AbstractArrow.Pickup.CREATIVE_ONLY && entity.getAbilities().instabuild)) {
                // CraftBukkit end
                entity.take(this, 1);
                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
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

    public ItemStack getPickupItem() { // Paper - protected -> public
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

    public void setPierceLevel(byte pierceLevel) { // Paper - private -> public
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

    // Paper start
    public void setPickupItemStackPublic(final ItemStack pickupItemStack) {
        this.setPickupItemStack(pickupItemStack);
    }
    // Paper end

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
