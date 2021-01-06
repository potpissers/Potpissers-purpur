package net.minecraft.world.entity.projectile;

import com.google.common.base.MoreObjects;
import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public abstract class Projectile extends Entity implements TraceableEntity {
    @Nullable
    public UUID ownerUUID;
    @Nullable
    public Entity cachedOwner;
    public boolean leftOwner;
    public boolean hasBeenShot;
    @Nullable
    private Entity lastDeflectedBy;
    protected boolean hitCancelled = false; // CraftBukkit

    Projectile(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
    }

    public void setOwner(@Nullable Entity owner) {
        if (owner != null) {
            this.ownerUUID = owner.getUUID();
            this.cachedOwner = owner;
        }
        // Paper start - Refresh ProjectileSource for projectiles
        else {
            this.ownerUUID = null;
            this.cachedOwner = null;
            this.projectileSource = null;
        }
        // Paper end - Refresh ProjectileSource for projectiles
        this.refreshProjectileSource(false); // Paper
    }

    // Paper start - Refresh ProjectileSource for projectiles
    public void refreshProjectileSource(boolean fillCache) {
        if (fillCache) {
            this.getOwner();
        }
        if (this.cachedOwner != null && !this.cachedOwner.isRemoved() && this.projectileSource == null && this.cachedOwner.getBukkitEntity() instanceof org.bukkit.projectiles.ProjectileSource projSource) {
            this.projectileSource = projSource;
        }
    }
    // Paper end - Refresh ProjectileSource for projectiles

    @Nullable
    @Override
    public Entity getOwner() {
        if (this.cachedOwner != null && !this.cachedOwner.isRemoved()) {
            this.refreshProjectileSource(false); // Paper - Refresh ProjectileSource for projectiles
            return this.cachedOwner;
        } else if (this.ownerUUID != null) {
            this.cachedOwner = this.findOwner(this.ownerUUID);
            this.refreshProjectileSource(false); // Paper - Refresh ProjectileSource for projectiles
            return this.cachedOwner;
        } else {
            return null;
        }
    }

    @Nullable
    protected Entity findOwner(UUID entityUuid) {
        return this.level() instanceof ServerLevel serverLevel ? serverLevel.getEntity(entityUuid) : null;
    }

    public Entity getEffectSource() {
        return MoreObjects.firstNonNull(this.getOwner(), this);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        if (this.ownerUUID != null) {
            compound.putUUID("Owner", this.ownerUUID);
        }

        if (this.leftOwner) {
            compound.putBoolean("LeftOwner", true);
        }

        compound.putBoolean("HasBeenShot", this.hasBeenShot);
    }

    protected boolean ownedBy(Entity entity) {
        return entity.getUUID().equals(this.ownerUUID);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.hasUUID("Owner")) {
            this.setOwnerThroughUUID(compound.getUUID("Owner"));
            if (this instanceof ThrownEnderpearl && this.level().paperConfig().fixes.disableUnloadedChunkEnderpearlExploit && this.level().paperConfig().misc.legacyEnderPearlBehavior) { this.ownerUUID = null; } // Paper - Reset pearls when they stop being ticked; Don't store shooter name for pearls to block enderpearl travel exploit
        }

        this.leftOwner = compound.getBoolean("LeftOwner");
        this.hasBeenShot = compound.getBoolean("HasBeenShot");
    }

    protected void setOwnerThroughUUID(UUID uuid) {
        if (this.ownerUUID != uuid) {
            this.ownerUUID = uuid;
            this.cachedOwner = this.findOwner(uuid);
        }
    }

    @Override
    public void restoreFrom(Entity entity) {
        super.restoreFrom(entity);
        if (entity instanceof Projectile projectile) {
            this.ownerUUID = projectile.ownerUUID;
            this.cachedOwner = projectile.cachedOwner;
        }
    }

    @Override
    public void tick() {
        if (!this.hasBeenShot) {
            this.gameEvent(GameEvent.PROJECTILE_SHOOT, this.getOwner());
            this.hasBeenShot = true;
        }

        if (!this.leftOwner) {
            this.leftOwner = this.checkLeftOwner();
        }

        super.tick();
    }

    private boolean checkLeftOwner() {
        Entity owner = this.getOwner();
        if (owner != null) {
            AABB aabb = this.getBoundingBox().expandTowards(this.getDeltaMovement()).inflate(1.0);
            return owner.getRootVehicle()
                .getSelfAndPassengers()
                .filter(EntitySelector.CAN_BE_PICKED)
                .noneMatch(entity -> aabb.intersects(entity.getBoundingBox()));
        } else {
            return true;
        }
    }

    public Vec3 getMovementToShoot(double x, double y, double z, float velocity, float inaccuracy) {
        return new Vec3(x, y, z)
            .normalize()
            .add(
                this.random.triangle(0.0, 0.0172275 * inaccuracy),
                this.random.triangle(0.0, 0.0172275 * inaccuracy),
                this.random.triangle(0.0, 0.0172275 * inaccuracy)
            )
            .scale(velocity);
    }

    public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        Vec3 movementToShoot = this.getMovementToShoot(x, y, z, velocity, inaccuracy);
        this.setDeltaMovement(movementToShoot);
        this.hasImpulse = true;
        double d = movementToShoot.horizontalDistance();
        this.setYRot((float)(Mth.atan2(movementToShoot.x, movementToShoot.z) * 180.0F / (float)Math.PI));
        this.setXRot((float)(Mth.atan2(movementToShoot.y, d) * 180.0F / (float)Math.PI));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public void shootFromRotation(Entity shooter, float x, float y, float z, float velocity, float inaccuracy) {
        float f = -Mth.sin(y * (float) (Math.PI / 180.0)) * Mth.cos(x * (float) (Math.PI / 180.0));
        float f1 = -Mth.sin((x + z) * (float) (Math.PI / 180.0));
        float f2 = Mth.cos(y * (float) (Math.PI / 180.0)) * Mth.cos(x * (float) (Math.PI / 180.0));
        this.shoot(f, f1, f2, velocity, inaccuracy);
        Vec3 knownMovement = shooter.getKnownMovement();
        // Paper start - allow disabling relative velocity
        if (Double.isNaN(knownMovement.x) || Double.isNaN(knownMovement.y) || Double.isNaN(knownMovement.z)) {
            knownMovement = new Vec3(0, 0, 0);
        }
        if (!shooter.level().paperConfig().misc.disableRelativeProjectileVelocity) {
        this.setDeltaMovement(this.getDeltaMovement().add(knownMovement.x, shooter.onGround() ? 0.0 : knownMovement.y, knownMovement.z));
        }
        // Paper end - allow disabling relative velocity
    }

    public static <T extends Projectile> T spawnProjectileFromRotation(
        Projectile.ProjectileFactory<T> factory, ServerLevel level, ItemStack spawnedFrom, LivingEntity owner, float z, float velocity, float innaccuracy
    ) {
        // Paper start - PlayerLaunchProjectileEvent
        return spawnProjectileFromRotationDelayed(factory, level, spawnedFrom, owner, z, velocity, innaccuracy).spawn();
    }
    public static <T extends Projectile> Delayed<T> spawnProjectileFromRotationDelayed(Projectile.ProjectileFactory<T> factory, ServerLevel level, ItemStack spawnedFrom, LivingEntity owner, float z, float velocity, float innaccuracy) {
        return spawnProjectileDelayed(
        // Paper end - PlayerLaunchProjectileEvent
            factory.create(level, owner, spawnedFrom),
            level,
            spawnedFrom,
            projectlie -> projectlie.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), z, velocity, innaccuracy)
        );
    }

    public static <T extends Projectile> T spawnProjectileUsingShoot(
        Projectile.ProjectileFactory<T> factory,
        ServerLevel level,
        ItemStack spawnedFrom,
        LivingEntity owner,
        double x,
        double y,
        double z,
        float velocity,
        float inaccuracy
    ) {
        return spawnProjectile(factory.create(level, owner, spawnedFrom), level, spawnedFrom, projectile -> projectile.shoot(x, y, z, velocity, inaccuracy));
    }

    public static <T extends Projectile> T spawnProjectileUsingShoot(
        T projectile, ServerLevel level, ItemStack spawnedFrom, double x, double y, double z, float velocity, float inaccuracy
    ) {
    // Paper start - fixes and addition to spawn reason API
        return spawnProjectileUsingShootDelayed(projectile, level, spawnedFrom, x, y, z, velocity, inaccuracy).spawn();
    }
    public static <T extends Projectile> Delayed<T> spawnProjectileUsingShootDelayed(T projectile, ServerLevel level, ItemStack spawnedFrom, double x, double y, double z, float velocity, float inaccuracy) {
        return spawnProjectileDelayed(projectile, level, spawnedFrom, projectile1 -> projectile.shoot(x, y, z, velocity, inaccuracy));
    // Paper end - fixes and addition to spawn reason API
    }

    public static <T extends Projectile> T spawnProjectile(T projectile, ServerLevel level, ItemStack spawnedFrom) {
        return spawnProjectile(projectile, level, spawnedFrom, projectile1 -> {});
    }

    public static <T extends Projectile> T spawnProjectile(T projectile, ServerLevel level, ItemStack stack, Consumer<T> adapter) {
        // Paper start - delayed projectile spawning
        return spawnProjectileDelayed(projectile, level, stack, adapter).spawn();
    }
    public static <T extends Projectile> Delayed<T> spawnProjectileDelayed(T projectile, ServerLevel level, ItemStack stack, Consumer<T> adapter) {
        // Paper end - delayed projectile spawning
        adapter.accept(projectile);
        return new Delayed<>(projectile, level, stack); // Paper - delayed projectile spawning
    }

    // Paper start - delayed projectile spawning
    public record Delayed<T extends Projectile>(
        T projectile,
        ServerLevel world,
        ItemStack projectileStack
    ) {
        // Taken from net.minecraft.world.entity.projectile.Projectile.spawnProjectile(T, net.minecraft.server.level.ServerLevel, net.minecraft.world.item.ItemStack, java.util.function.Consumer<T>)
        public boolean attemptSpawn() {
            if (!this.world.addFreshEntity(this.projectile)) return false;
            this.projectile.applyOnProjectileSpawned(this.world, this.projectileStack);
            return true;
        }

        public T spawn() {
            this.attemptSpawn();
            return this.projectile();
        }

        public boolean attemptSpawn(final org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
            if (!this.world.addFreshEntity(this.projectile, reason)) return false;
            this.projectile.applyOnProjectileSpawned(this.world, this.projectileStack);
            return true;
        }

        public T spawn(final org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
            this.attemptSpawn(reason);
            return this.projectile();
        }
    }
    // Paper end - delayed projectile spawning

    public void applyOnProjectileSpawned(ServerLevel level, ItemStack spawnedFrom) {
        EnchantmentHelper.onProjectileSpawned(level, spawnedFrom, this, item -> {});
        if (this instanceof AbstractArrow abstractArrow) {
            ItemStack weaponItem = abstractArrow.getWeaponItem();
            if (weaponItem != null && !weaponItem.isEmpty() && !spawnedFrom.getItem().equals(weaponItem.getItem())) {
                EnchantmentHelper.onProjectileSpawned(level, weaponItem, this, abstractArrow::onItemBreak);
            }
        }
    }

    // CraftBukkit start - call projectile hit event
    public ProjectileDeflection preHitTargetOrDeflectSelf(HitResult hitResult) { // Paper - protected -> public
        org.bukkit.event.entity.ProjectileHitEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callProjectileHitEvent(this, hitResult);
        this.hitCancelled = event != null && event.isCancelled();
        if (hitResult.getType() == HitResult.Type.BLOCK || !this.hitCancelled) {
            return this.hitTargetOrDeflectSelf(hitResult);
        }
        return ProjectileDeflection.NONE;
    }
    // CraftBukkit end

    protected ProjectileDeflection hitTargetOrDeflectSelf(HitResult hitResult) {
        if (hitResult.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHitResult = (EntityHitResult)hitResult;
            Entity entity = entityHitResult.getEntity();
            ProjectileDeflection projectileDeflection = entity.deflection(this);
            if (projectileDeflection != ProjectileDeflection.NONE) {
                if (entity != this.lastDeflectedBy && this.deflect(projectileDeflection, entity, this.getOwner(), false)) {
                    this.lastDeflectedBy = entity;
                }

                return projectileDeflection;
            }
        } else if (this.shouldBounceOnWorldBorder() && hitResult instanceof BlockHitResult blockHitResult && blockHitResult.isWorldBorderHit()) {
            ProjectileDeflection projectileDeflection1 = ProjectileDeflection.REVERSE;
            if (this.deflect(projectileDeflection1, null, this.getOwner(), false)) {
                this.setDeltaMovement(this.getDeltaMovement().scale(0.2));
                return projectileDeflection1;
            }
        }

        this.onHit(hitResult);
        return ProjectileDeflection.NONE;
    }

    protected boolean shouldBounceOnWorldBorder() {
        return false;
    }

    public boolean deflect(ProjectileDeflection deflection, @Nullable Entity entity, @Nullable Entity owner, boolean deflectedByPlayer) {
        deflection.deflect(this, entity, this.random);
        if (!this.level().isClientSide) {
            // Paper start - Fix PickupStatus getting reset
            if (this instanceof AbstractArrow arrow) {
                arrow.setOwner(owner, false);
            } else {
                this.setOwner(owner);
            }
            // Paper end - Fix PickupStatus getting reset
            this.onDeflection(entity, deflectedByPlayer);
        }

        return true;
    }

    protected void onDeflection(@Nullable Entity entity, boolean deflectedByPlayer) {
    }

    protected void onItemBreak(Item item) {
    }

    protected void onHit(HitResult result) {
        HitResult.Type type = result.getType();
        if (type == HitResult.Type.ENTITY) {
            EntityHitResult entityHitResult = (EntityHitResult)result;
            Entity entity = entityHitResult.getEntity();
            if (entity.getType().is(EntityTypeTags.REDIRECTABLE_PROJECTILE) && entity instanceof Projectile projectile) {
                projectile.deflect(ProjectileDeflection.AIM_DEFLECT, this.getOwner(), this.getOwner(), true);
            }

            this.onHitEntity(entityHitResult);
            this.level().gameEvent(GameEvent.PROJECTILE_LAND, result.getLocation(), GameEvent.Context.of(this, null));
        } else if (type == HitResult.Type.BLOCK) {
            BlockHitResult blockHitResult = (BlockHitResult)result;
            this.onHitBlock(blockHitResult);
            BlockPos blockPos = blockHitResult.getBlockPos();
            this.level().gameEvent(GameEvent.PROJECTILE_LAND, blockPos, GameEvent.Context.of(this, this.level().getBlockState(blockPos)));
        }
    }

    protected void onHitEntity(EntityHitResult result) {
    }

    protected void onHitBlock(BlockHitResult result) {
        // CraftBukkit start - cancellable hit event
        if (this.hitCancelled) {
            return;
        }
        // CraftBukkit end
        BlockState blockState = this.level().getBlockState(result.getBlockPos());
        blockState.onProjectileHit(this.level(), blockState, result, this);
    }

    // Paper start
    public boolean canHitEntityPublic(final Entity target) {
        return this.canHitEntity(target);
    }
    // Paper end

    protected boolean canHitEntity(Entity target) {
        if (!target.canBeHitByProjectile()) {
            return false;
        } else {
            Entity owner = this.getOwner();
            // Paper start - Cancel hit for vanished players
            if (owner instanceof net.minecraft.server.level.ServerPlayer && target instanceof net.minecraft.server.level.ServerPlayer) {
                org.bukkit.entity.Player collided = (org.bukkit.entity.Player) target.getBukkitEntity();
                org.bukkit.entity.Player shooter = (org.bukkit.entity.Player) owner.getBukkitEntity();
                if (!shooter.canSee(collided)) {
                    return false;
                }
            }
            // Paper end - Cancel hit for vanished players
            return owner == null || this.leftOwner || !owner.isPassengerOfSameVehicle(target);
        }
    }

    protected void updateRotation() {
        Vec3 deltaMovement = this.getDeltaMovement();
        double d = deltaMovement.horizontalDistance();
        this.setXRot(lerpRotation(this.xRotO, (float)(Mth.atan2(deltaMovement.y, d) * 180.0F / (float)Math.PI)));
        this.setYRot(lerpRotation(this.yRotO, (float)(Mth.atan2(deltaMovement.x, deltaMovement.z) * 180.0F / (float)Math.PI)));
    }

    protected static float lerpRotation(float currentRotation, float targetRotation) {
        currentRotation += Math.round((targetRotation - currentRotation) / 360.0F) * 360.0F; // Paper - stop large look changes from crashing the server

        return Mth.lerp(0.2F, currentRotation, targetRotation);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        Entity owner = this.getOwner();
        return new ClientboundAddEntityPacket(this, entity, owner == null ? 0 : owner.getId());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        Vec3 vec3 = new Vec3(packet.getXa(), packet.getYa(), packet.getZa());
        this.setDeltaMovement(vec3);
        Entity entity = this.level().getEntity(packet.getData());
        if (entity != null) {
            this.setOwner(entity);
        }
    }

    @Override
    public boolean mayInteract(ServerLevel level, BlockPos pos) {
        Entity owner = this.getOwner();
        return owner instanceof Player ? owner.mayInteract(level, pos) : owner == null || level.purpurConfig.projectilesBypassMobGriefing ^ level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING); // Purpur - Add mobGriefing bypass to everything affected
    }

    public boolean mayBreak(ServerLevel level) {
        return this.getType().is(EntityTypeTags.IMPACT_PROJECTILES) && level.getGameRules().getBoolean(GameRules.RULE_PROJECTILESCANBREAKBLOCKS);
    }

    @Override
    public boolean isPickable() {
        return this.getType().is(EntityTypeTags.REDIRECTABLE_PROJECTILE);
    }

    @Override
    public float getPickRadius() {
        return this.isPickable() ? 1.0F : 0.0F;
    }

    public DoubleDoubleImmutablePair calculateHorizontalHurtKnockbackDirection(LivingEntity entity, DamageSource damageSource) {
        double d = this.getDeltaMovement().x;
        double d1 = this.getDeltaMovement().z;
        return DoubleDoubleImmutablePair.of(d, d1);
    }

    @Override
    public int getDimensionChangingDelay() {
        return 2;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (!this.isInvulnerableToBase(damageSource)) {
            this.markHurt();
        }

        return false;
    }

    @FunctionalInterface
    public interface ProjectileFactory<T extends Projectile> {
        T create(ServerLevel level, LivingEntity owner, ItemStack spawnedFrom);
    }
}
