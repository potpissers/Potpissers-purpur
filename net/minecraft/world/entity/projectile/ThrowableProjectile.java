package net.minecraft.world.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public abstract class ThrowableProjectile extends Projectile {
    private static final float MIN_CAMERA_DISTANCE_SQUARED = 12.25F;

    protected ThrowableProjectile(EntityType<? extends ThrowableProjectile> entityType, Level level) {
        super(entityType, level);
    }

    protected ThrowableProjectile(EntityType<? extends ThrowableProjectile> entityType, double x, double y, double z, Level level) {
        this(entityType, level);
        this.setPos(x, y, z);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        if (this.tickCount < 2 && distance < 12.25) {
            return false;
        } else {
            double d = this.getBoundingBox().getSize() * 4.0;
            if (Double.isNaN(d)) {
                d = 4.0;
            }

            d *= 64.0;
            return distance < d * d;
        }
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return true;
    }

    @Override
    public void tick() {
        this.handleFirstTickBubbleColumn();
        this.applyGravity();
        this.applyInertia();
        HitResult hitResultOnMoveVector = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        Vec3 location;
        if (hitResultOnMoveVector.getType() != HitResult.Type.MISS) {
            location = hitResultOnMoveVector.getLocation();
        } else {
            location = this.position().add(this.getDeltaMovement());
        }

        this.setPos(location);
        this.updateRotation();
        this.applyEffectsFromBlocks();
        super.tick();
        if (hitResultOnMoveVector.getType() != HitResult.Type.MISS && this.isAlive()) {
            this.hitTargetOrDeflectSelf(hitResultOnMoveVector);
        }
    }

    private void applyInertia() {
        Vec3 deltaMovement = this.getDeltaMovement();
        Vec3 vec3 = this.position();
        float f1;
        if (this.isInWater()) {
            for (int i = 0; i < 4; i++) {
                float f = 0.25F;
                this.level()
                    .addParticle(
                        ParticleTypes.BUBBLE,
                        vec3.x - deltaMovement.x * 0.25,
                        vec3.y - deltaMovement.y * 0.25,
                        vec3.z - deltaMovement.z * 0.25,
                        deltaMovement.x,
                        deltaMovement.y,
                        deltaMovement.z
                    );
            }

            f1 = 0.8F;
        } else {
            f1 = 0.99F;
        }

        this.setDeltaMovement(deltaMovement.scale(f1));
    }

    private void handleFirstTickBubbleColumn() {
        if (this.firstTick) {
            for (BlockPos blockPos : BlockPos.betweenClosed(this.getBoundingBox())) {
                BlockState blockState = this.level().getBlockState(blockPos);
                if (blockState.is(Blocks.BUBBLE_COLUMN)) {
                    blockState.entityInside(this.level(), blockPos, this);
                }
            }
        }
    }

    @Override
    protected double getDefaultGravity() {
        return 0.03;
    }
}
