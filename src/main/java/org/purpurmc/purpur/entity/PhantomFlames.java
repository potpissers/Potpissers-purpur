package org.purpurmc.purpur.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class PhantomFlames extends LlamaSpit {
    public Phantom phantom;
    public int ticksLived;
    public boolean canGrief = false;

    public PhantomFlames(EntityType<? extends LlamaSpit> type, Level world) {
        super(type, world);
    }

    public PhantomFlames(Level world, Phantom phantom) {
        this(EntityType.LLAMA_SPIT, world);
        setOwner(phantom.getRider() != null ? phantom.getRider() : phantom);
        this.phantom = phantom;
        this.setPos(
                phantom.getX() - (double) (phantom.getBbWidth() + 1.0F) * 0.5D * (double) Mth.sin(phantom.yBodyRot * 0.017453292F),
                phantom.getEyeY() - 0.10000000149011612D,
                phantom.getZ() + (double) (phantom.getBbWidth() + 1.0F) * 0.5D * (double) Mth.cos(phantom.yBodyRot * 0.017453292F));
    }

    // Purpur start
    @Override
    public boolean canSaveToDisk() {
        return false;
    }
    // Purpur end

    public void tick() {
        super_tick();

        Vec3 mot = this.getDeltaMovement();
        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);

        this.preHitTargetOrDeflectSelf(hitResult);

        double x = this.getX() + mot.x;
        double y = this.getY() + mot.y;
        double z = this.getZ() + mot.z;

        this.updateRotation();

        Vec3 motDouble = mot.scale(2.0);
        for (int i = 0; i < 5; i++) {
            ((ServerLevel) level()).sendParticles(null, ParticleTypes.FLAME,
                    getX() + random.nextFloat() / 2 - 0.25F,
                    getY() + random.nextFloat() / 2 - 0.25F,
                    getZ() + random.nextFloat() / 2 - 0.25F,
                    0, motDouble.x(), motDouble.y(), motDouble.z(), 0.1D, true);
        }

        if (++ticksLived > 20) {
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DISCARD);
        } else if (this.level().getBlockStates(this.getBoundingBox()).noneMatch(BlockBehaviour.BlockStateBase::isAir)) {
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DISCARD);
        } else if (this.isInWaterOrBubble()) {
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DISCARD);
        } else {
            this.setDeltaMovement(mot.scale(0.99D));
            if (!this.isNoGravity()) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.06D, 0.0D));
            }

            this.setPos(x, y, z);
        }
    }

    @Override
    public void shoot(double x, double y, double z, float speed, float inaccuracy) {
        setDeltaMovement(new Vec3(x, y, z).normalize().add(
                random.nextGaussian() * (double) 0.0075F * (double) inaccuracy,
                random.nextGaussian() * (double) 0.0075F * (double) inaccuracy,
                random.nextGaussian() * (double) 0.0075F * (double) inaccuracy)
                .scale(speed));
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        Entity shooter = this.getOwner();
        if (shooter instanceof LivingEntity) {
            Entity target = entityHitResult.getEntity();
            if (canGrief || (target instanceof LivingEntity && !(target instanceof ArmorStand))) {
                boolean hurt = target.hurt(target.damageSources().mobProjectile(this, (LivingEntity) shooter), level().purpurConfig.phantomFlameDamage);
                if (hurt && level().purpurConfig.phantomFlameFireTime > 0) {
                    target.igniteForSeconds(level().purpurConfig.phantomFlameFireTime);
                }
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        if (this.hitCancelled) {
            return;
        }
        if (this.canGrief) {
            BlockState state = this.level().getBlockState(blockHitResult.getBlockPos());
            state.onProjectileHit(this.level(), state, blockHitResult, this);
        }
        this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DISCARD);
    }
}
