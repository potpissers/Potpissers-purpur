package net.minecraft.world.effect;

import net.minecraft.world.entity.LivingEntity;

class WitherMobEffect extends MobEffect {
    protected WitherMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        entity.hurt(entity.damageSources().wither(), entity.level().purpurConfig.entityWitherDegenerationAmount); // Purpur
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int i = 40 >> amplifier;
        return i <= 0 || duration % i == 0;
    }
}
