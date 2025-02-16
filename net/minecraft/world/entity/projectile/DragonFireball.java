package net.minecraft.world.entity.projectile;

import java.util.List;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class DragonFireball extends AbstractHurtingProjectile {
    public static final float SPLASH_RANGE = 4.0F;

    public DragonFireball(EntityType<? extends DragonFireball> entityType, Level level) {
        super(entityType, level);
    }

    public DragonFireball(Level level, LivingEntity owner, Vec3 movement) {
        super(EntityType.DRAGON_FIREBALL, owner, movement, level);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (result.getType() != HitResult.Type.ENTITY || !this.ownedBy(((EntityHitResult)result).getEntity())) {
            if (!this.level().isClientSide) {
                List<LivingEntity> entitiesOfClass = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(4.0, 2.0, 4.0));
                AreaEffectCloud areaEffectCloud = new AreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
                Entity owner = this.getOwner();
                if (owner instanceof LivingEntity) {
                    areaEffectCloud.setOwner((LivingEntity)owner);
                }

                areaEffectCloud.setParticle(ParticleTypes.DRAGON_BREATH);
                areaEffectCloud.setRadius(3.0F);
                areaEffectCloud.setDuration(600);
                areaEffectCloud.setRadiusPerTick((7.0F - areaEffectCloud.getRadius()) / areaEffectCloud.getDuration());
                areaEffectCloud.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 1));
                if (!entitiesOfClass.isEmpty()) {
                    for (LivingEntity livingEntity : entitiesOfClass) {
                        double d = this.distanceToSqr(livingEntity);
                        if (d < 16.0) {
                            areaEffectCloud.setPos(livingEntity.getX(), livingEntity.getY(), livingEntity.getZ());
                            break;
                        }
                    }
                }

                this.level().levelEvent(2006, this.blockPosition(), this.isSilent() ? -1 : 1);
                this.level().addFreshEntity(areaEffectCloud);
                this.discard();
            }
        }
    }

    @Override
    protected ParticleOptions getTrailParticle() {
        return ParticleTypes.DRAGON_BREATH;
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }
}
