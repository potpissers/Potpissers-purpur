package net.minecraft.world.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

class HungerMobEffect extends MobEffect {
    protected HungerMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity instanceof Player player) {
            player.causeFoodExhaustion(entity.level().purpurConfig.humanHungerExhaustionAmount * (amplifier + 1), org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.HUNGER_EFFECT); // CraftBukkit - EntityExhaustionEvent // Purpur - Config MobEffect by world
        }

        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }
}
