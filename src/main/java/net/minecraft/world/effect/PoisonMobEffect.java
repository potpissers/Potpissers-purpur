package net.minecraft.world.effect;

import net.minecraft.world.entity.LivingEntity;

class PoisonMobEffect extends MobEffect {

    protected PoisonMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.getHealth() > entity.level().purpurConfig.entityMinimalHealthPoison) { // Purpur
            entity.hurt(entity.damageSources().poison(), entity.level().purpurConfig.entityPoisonDegenerationAmount);  // CraftBukkit - DamageSource.MAGIC -> CraftEventFactory.POISON // Purpur
        }

        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int k = 25 >> amplifier;

        return k > 0 ? duration % k == 0 : true;
    }
}
