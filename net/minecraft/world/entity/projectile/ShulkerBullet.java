package net.minecraft.world.entity.projectile;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class ShulkerBullet extends Projectile {
    private static final double SPEED = 0.15;
    @Nullable
    private Entity finalTarget;
    @Nullable
    public Direction currentMoveDirection;
    public int flightSteps;
    public double targetDeltaX;
    public double targetDeltaY;
    public double targetDeltaZ;
    @Nullable
    private UUID targetId;

    public ShulkerBullet(EntityType<? extends ShulkerBullet> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    public ShulkerBullet(Level level, LivingEntity shooter, Entity finalTarget, Direction.Axis axis) {
        this(EntityType.SHULKER_BULLET, level);
        this.setOwner(shooter);
        Vec3 center = shooter.getBoundingBox().getCenter();
        this.moveTo(center.x, center.y, center.z, this.getYRot(), this.getXRot());
        this.finalTarget = finalTarget;
        this.currentMoveDirection = Direction.UP;
        this.selectNextMoveDirection(axis);
        this.projectileSource = shooter.getBukkitLivingEntity(); // CraftBukkit
    }

    // CraftBukkit start
    @Nullable
    public Entity getTarget() {
        return this.finalTarget;
    }

    public void setTarget(Entity finalTarget) {
        this.finalTarget = finalTarget;
        this.currentMoveDirection = Direction.UP;
        this.selectNextMoveDirection(Direction.Axis.X);
    }
    // CraftBukkit end

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (this.finalTarget != null) {
            compound.putUUID("Target", this.finalTarget.getUUID());
        }

        if (this.currentMoveDirection != null) {
            compound.putInt("Dir", this.currentMoveDirection.get3DDataValue());
        }

        compound.putInt("Steps", this.flightSteps);
        compound.putDouble("TXD", this.targetDeltaX);
        compound.putDouble("TYD", this.targetDeltaY);
        compound.putDouble("TZD", this.targetDeltaZ);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.flightSteps = compound.getInt("Steps");
        this.targetDeltaX = compound.getDouble("TXD");
        this.targetDeltaY = compound.getDouble("TYD");
        this.targetDeltaZ = compound.getDouble("TZD");
        if (compound.contains("Dir", 99)) {
            this.currentMoveDirection = Direction.from3DDataValue(compound.getInt("Dir"));
        }

        if (compound.hasUUID("Target")) {
            this.targetId = compound.getUUID("Target");
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Nullable
    private Direction getMoveDirection() {
        return this.currentMoveDirection;
    }

    private void setMoveDirection(@Nullable Direction direction) {
        this.currentMoveDirection = direction;
    }

    private void selectNextMoveDirection(@Nullable Direction.Axis axis) {
        double d = 0.5;
        BlockPos blockPos;
        if (this.finalTarget == null) {
            blockPos = this.blockPosition().below();
        } else {
            d = this.finalTarget.getBbHeight() * 0.5;
            blockPos = BlockPos.containing(this.finalTarget.getX(), this.finalTarget.getY() + d, this.finalTarget.getZ());
        }

        double d1 = blockPos.getX() + 0.5;
        double d2 = blockPos.getY() + d;
        double d3 = blockPos.getZ() + 0.5;
        Direction direction = null;
        if (!blockPos.closerToCenterThan(this.position(), 2.0)) {
            BlockPos blockPos1 = this.blockPosition();
            List<Direction> list = Lists.newArrayList();
            if (axis != Direction.Axis.X) {
                if (blockPos1.getX() < blockPos.getX() && this.level().isEmptyBlock(blockPos1.east())) {
                    list.add(Direction.EAST);
                } else if (blockPos1.getX() > blockPos.getX() && this.level().isEmptyBlock(blockPos1.west())) {
                    list.add(Direction.WEST);
                }
            }

            if (axis != Direction.Axis.Y) {
                if (blockPos1.getY() < blockPos.getY() && this.level().isEmptyBlock(blockPos1.above())) {
                    list.add(Direction.UP);
                } else if (blockPos1.getY() > blockPos.getY() && this.level().isEmptyBlock(blockPos1.below())) {
                    list.add(Direction.DOWN);
                }
            }

            if (axis != Direction.Axis.Z) {
                if (blockPos1.getZ() < blockPos.getZ() && this.level().isEmptyBlock(blockPos1.south())) {
                    list.add(Direction.SOUTH);
                } else if (blockPos1.getZ() > blockPos.getZ() && this.level().isEmptyBlock(blockPos1.north())) {
                    list.add(Direction.NORTH);
                }
            }

            direction = Direction.getRandom(this.random);
            if (list.isEmpty()) {
                for (int i = 5; !this.level().isEmptyBlock(blockPos1.relative(direction)) && i > 0; i--) {
                    direction = Direction.getRandom(this.random);
                }
            } else {
                direction = list.get(this.random.nextInt(list.size()));
            }

            d1 = this.getX() + direction.getStepX();
            d2 = this.getY() + direction.getStepY();
            d3 = this.getZ() + direction.getStepZ();
        }

        this.setMoveDirection(direction);
        double d4 = d1 - this.getX();
        double d5 = d2 - this.getY();
        double d6 = d3 - this.getZ();
        double squareRoot = Math.sqrt(d4 * d4 + d5 * d5 + d6 * d6);
        if (squareRoot == 0.0) {
            this.targetDeltaX = 0.0;
            this.targetDeltaY = 0.0;
            this.targetDeltaZ = 0.0;
        } else {
            this.targetDeltaX = d4 / squareRoot * 0.15;
            this.targetDeltaY = d5 / squareRoot * 0.15;
            this.targetDeltaZ = d6 / squareRoot * 0.15;
        }

        this.hasImpulse = true;
        this.flightSteps = 10 + this.random.nextInt(5) * 10;
    }

    @Override
    public void checkDespawn() {
        if (this.level().getDifficulty() == Difficulty.PEACEFUL) {
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        }
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04;
    }

    @Override
    public void tick() {
        super.tick();
        HitResult hitResult = null;
        if (!this.level().isClientSide) {
            if (this.finalTarget == null && this.targetId != null) {
                this.finalTarget = ((ServerLevel)this.level()).getEntity(this.targetId);
                if (this.finalTarget == null) {
                    this.targetId = null;
                }
            }

            if (this.finalTarget == null || !this.finalTarget.isAlive() || this.finalTarget instanceof Player && this.finalTarget.isSpectator()) {
                this.applyGravity();
            } else {
                this.targetDeltaX = Mth.clamp(this.targetDeltaX * 1.025, -1.0, 1.0);
                this.targetDeltaY = Mth.clamp(this.targetDeltaY * 1.025, -1.0, 1.0);
                this.targetDeltaZ = Mth.clamp(this.targetDeltaZ * 1.025, -1.0, 1.0);
                Vec3 deltaMovement = this.getDeltaMovement();
                this.setDeltaMovement(
                    deltaMovement.add(
                        (this.targetDeltaX - deltaMovement.x) * 0.2, (this.targetDeltaY - deltaMovement.y) * 0.2, (this.targetDeltaZ - deltaMovement.z) * 0.2
                    )
                );
            }

            hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        }

        Vec3 deltaMovement = this.getDeltaMovement();
        this.setPos(this.position().add(deltaMovement));
        this.applyEffectsFromBlocks();
        if (this.portalProcess != null && this.portalProcess.isInsidePortalThisTick()) {
            this.handlePortal();
        }

        if (hitResult != null && this.isAlive() && hitResult.getType() != HitResult.Type.MISS) {
            this.preHitTargetOrDeflectSelf(hitResult); // CraftBukkit - projectile hit event
        }

        ProjectileUtil.rotateTowardsMovement(this, 0.5F);
        if (this.level().isClientSide) {
            this.level()
                .addParticle(
                    ParticleTypes.END_ROD, this.getX() - deltaMovement.x, this.getY() - deltaMovement.y + 0.15, this.getZ() - deltaMovement.z, 0.0, 0.0, 0.0
                );
        } else if (this.finalTarget != null && !this.finalTarget.isRemoved()) {
            if (this.flightSteps > 0) {
                this.flightSteps--;
                if (this.flightSteps == 0) {
                    this.selectNextMoveDirection(this.currentMoveDirection == null ? null : this.currentMoveDirection.getAxis());
                }
            }

            if (this.currentMoveDirection != null) {
                BlockPos blockPos = this.blockPosition();
                Direction.Axis axis = this.currentMoveDirection.getAxis();
                if (this.level().loadedAndEntityCanStandOn(blockPos.relative(this.currentMoveDirection), this)) {
                    this.selectNextMoveDirection(axis);
                } else {
                    BlockPos blockPos1 = this.finalTarget.blockPosition();
                    if (axis == Direction.Axis.X && blockPos.getX() == blockPos1.getX()
                        || axis == Direction.Axis.Z && blockPos.getZ() == blockPos1.getZ()
                        || axis == Direction.Axis.Y && blockPos.getY() == blockPos1.getY()) {
                        this.selectNextMoveDirection(axis);
                    }
                }
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !target.noPhysics;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 16384.0;
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity entity = result.getEntity();
        Entity owner = this.getOwner();
        LivingEntity livingEntity = owner instanceof LivingEntity ? (LivingEntity)owner : null;
        DamageSource damageSource = this.damageSources().mobProjectile(this, livingEntity);
        boolean flag = entity.hurtOrSimulate(damageSource, 4.0F);
        if (flag) {
            if (this.level() instanceof ServerLevel serverLevel) {
                EnchantmentHelper.doPostAttackEffects(serverLevel, entity, damageSource);
            }

            if (entity instanceof LivingEntity livingEntity1) {
                livingEntity1.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 200), MoreObjects.firstNonNull(owner, this), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        ((ServerLevel)this.level()).sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
        this.playSound(SoundEvents.SHULKER_BULLET_HIT, 1.0F, 1.0F);
    }

    private void destroy() {
        // CraftBukkit start - add Bukkit remove cause
        this.destroy(null);
    }

    private void destroy(org.bukkit.event.entity.EntityRemoveEvent.Cause cause) {
        this.discard(cause);
        // CraftBukkit end
        this.level().gameEvent(GameEvent.ENTITY_DAMAGE, this.position(), GameEvent.Context.of(this));
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        this.destroy(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurtClient(DamageSource damageSource) {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        // CraftBukkit start
        if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, damageSource, amount, false)) {
            return false;
        }
        // CraftBukkit end
        this.playSound(SoundEvents.SHULKER_BULLET_HURT, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 15, 0.2, 0.2, 0.2, 0.0);
        this.destroy(org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
        return true;
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        double xa = packet.getXa();
        double ya = packet.getYa();
        double za = packet.getZa();
        this.setDeltaMovement(xa, ya, za);
    }
}
