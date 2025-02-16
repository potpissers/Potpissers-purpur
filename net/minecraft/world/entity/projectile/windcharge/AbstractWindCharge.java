package net.minecraft.world.entity.projectile.windcharge;

import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public abstract class AbstractWindCharge extends AbstractHurtingProjectile implements ItemSupplier {
    public static final ExplosionDamageCalculator EXPLOSION_DAMAGE_CALCULATOR = new SimpleExplosionDamageCalculator(
        true, false, Optional.empty(), BuiltInRegistries.BLOCK.get(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity())
    );
    public static final double JUMP_SCALE = 0.25;

    public AbstractWindCharge(EntityType<? extends AbstractWindCharge> entityType, Level level) {
        super(entityType, level);
        this.accelerationPower = 0.0;
    }

    public AbstractWindCharge(EntityType<? extends AbstractWindCharge> entityType, Level level, Entity owner, double x, double y, double z) {
        super(entityType, x, y, z, level);
        this.setOwner(owner);
        this.accelerationPower = 0.0;
    }

    AbstractWindCharge(EntityType<? extends AbstractWindCharge> entityType, double x, double y, double z, Vec3 movement, Level level) {
        super(entityType, x, y, z, movement, level);
        this.accelerationPower = 0.0;
    }

    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        float f = this.getType().getDimensions().width() / 2.0F;
        float height = this.getType().getDimensions().height();
        float f1 = 0.15F;
        return new AABB(position.x - f, position.y - 0.15F, position.z - f, position.x + f, position.y - 0.15F + height, position.z + f);
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return !(entity instanceof AbstractWindCharge) && super.canCollideWith(entity);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return !(target instanceof AbstractWindCharge) && target.getType() != EntityType.END_CRYSTAL && super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level() instanceof ServerLevel serverLevel) {
            LivingEntity livingEntity1 = this.getOwner() instanceof LivingEntity livingEntity ? livingEntity : null;
            Entity entity = result.getEntity();
            if (livingEntity1 != null) {
                livingEntity1.setLastHurtMob(entity);
            }

            DamageSource damageSource = this.damageSources().windCharge(this, livingEntity1);
            if (entity.hurtServer(serverLevel, damageSource, 1.0F) && entity instanceof LivingEntity livingEntity2) {
                EnchantmentHelper.doPostAttackEffects(serverLevel, livingEntity2, damageSource);
            }

            this.explode(this.position());
        }
    }

    @Override
    public void push(double x, double y, double z) {
    }

    public abstract void explode(Vec3 pos);

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            Vec3i unitVec3i = result.getDirection().getUnitVec3i();
            Vec3 vec3 = Vec3.atLowerCornerOf(unitVec3i).multiply(0.25, 0.25, 0.25);
            Vec3 vec31 = result.getLocation().add(vec3);
            this.explode(vec31);
            this.discard();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            this.discard();
        }
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    public ItemStack getItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected float getInertia() {
        return 1.0F;
    }

    @Override
    protected float getLiquidInertia() {
        return this.getInertia();
    }

    @Nullable
    @Override
    protected ParticleOptions getTrailParticle() {
        return null;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.getBlockY() > this.level().getMaxY() + 30) {
            this.explode(this.position());
            this.discard();
        } else {
            super.tick();
        }
    }
}
