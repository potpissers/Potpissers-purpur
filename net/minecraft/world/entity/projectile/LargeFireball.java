package net.minecraft.world.entity.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class LargeFireball extends Fireball {
    public int explosionPower = 1;

    public LargeFireball(EntityType<? extends LargeFireball> entityType, Level level) {
        super(entityType, level);
    }

    public LargeFireball(Level level, LivingEntity owner, Vec3 movement, int explosionPower) {
        super(EntityType.FIREBALL, owner, movement, level);
        this.explosionPower = explosionPower;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level() instanceof ServerLevel serverLevel) {
            boolean _boolean = serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
            this.level().explode(this, this.getX(), this.getY(), this.getZ(), this.explosionPower, _boolean, Level.ExplosionInteraction.MOB);
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level() instanceof ServerLevel serverLevel) {
            Entity var6 = result.getEntity();
            Entity owner = this.getOwner();
            DamageSource damageSource = this.damageSources().fireball(this, owner);
            var6.hurtServer(serverLevel, damageSource, 6.0F);
            EnchantmentHelper.doPostAttackEffects(serverLevel, var6, damageSource);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putByte("ExplosionPower", (byte)this.explosionPower);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("ExplosionPower", 99)) {
            this.explosionPower = compound.getByte("ExplosionPower");
        }
    }
}
