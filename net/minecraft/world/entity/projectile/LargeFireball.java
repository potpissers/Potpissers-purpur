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
        this.isIncendiary = (level instanceof ServerLevel serverLevel) && serverLevel.purpurConfig.fireballsBypassMobGriefing ^ serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING); // CraftBukkit // Purpur - Add mobGriefing bypass to everything affected
    }

    public LargeFireball(Level level, LivingEntity owner, Vec3 movement, int explosionPower) {
        super(EntityType.FIREBALL, owner, movement, level);
        this.explosionPower = explosionPower;
        this.isIncendiary = (level instanceof ServerLevel serverLevel) && serverLevel.purpurConfig.fireballsBypassMobGriefing ^ serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING); // CraftBukkit // Purpur - Add mobGriefing bypass to everything affected
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level() instanceof ServerLevel serverLevel) {
            boolean _boolean = serverLevel.purpurConfig.fireballsBypassMobGriefing ^ serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING); // Purpur - Add mobGriefing bypass to everything affected
            // CraftBukkit start - fire ExplosionPrimeEvent
            org.bukkit.event.entity.ExplosionPrimeEvent event = new org.bukkit.event.entity.ExplosionPrimeEvent((org.bukkit.entity.Explosive) this.getBukkitEntity());
            this.level().getCraftServer().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                // give 'this' instead of (Entity) null so we know what causes the damage
                this.level().explode(this, this.getX(), this.getY(), this.getZ(), event.getRadius(), event.getFire(), Level.ExplosionInteraction.MOB);
            }
            // CraftBukkit end
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
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
            // CraftBukkit - set bukkitYield when setting explosionpower
            this.bukkitYield = this.explosionPower = compound.getByte("ExplosionPower");
        }
    }
}
