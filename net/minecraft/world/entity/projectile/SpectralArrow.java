package net.minecraft.world.entity.projectile;

import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class SpectralArrow extends AbstractArrow {
    public int duration = 200;

    public SpectralArrow(EntityType<? extends SpectralArrow> entityType, Level level) {
        super(entityType, level);
    }

    public SpectralArrow(Level level, LivingEntity owner, ItemStack pickupItemStack, @Nullable ItemStack firedFromWeapon) {
        super(EntityType.SPECTRAL_ARROW, owner, level, pickupItemStack, firedFromWeapon);
    }

    public SpectralArrow(Level level, double x, double y, double z, ItemStack pickupItemStack, @Nullable ItemStack firedFromWeapon) {
        super(EntityType.SPECTRAL_ARROW, x, y, z, level, pickupItemStack, firedFromWeapon);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide && !this.isInGround()) {
            this.level().addParticle(ParticleTypes.INSTANT_EFFECT, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected void doPostHurtEffects(LivingEntity living) {
        super.doPostHurtEffects(living);
        MobEffectInstance mobEffectInstance = new MobEffectInstance(MobEffects.GLOWING, this.duration, 0);
        living.addEffect(mobEffectInstance, this.getEffectSource(), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ARROW); // CraftBukkit
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("Duration")) {
            this.duration = compound.getInt("Duration");
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Duration", this.duration);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.SPECTRAL_ARROW);
    }
}
