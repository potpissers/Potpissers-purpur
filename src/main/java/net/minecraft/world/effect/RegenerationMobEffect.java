package net.minecraft.world.effect;

import net.minecraft.world.entity.LivingEntity;

class RegenerationMobEffect extends MobEffect {

    protected RegenerationMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(entity.level().purpurConfig.entityHealthRegenAmount, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.MAGIC_REGEN); // CraftBukkit // Purpur
        }

        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int k = 50 >> amplifier;

        return k > 0 ? duration % k == 0 : true;
    }
}
