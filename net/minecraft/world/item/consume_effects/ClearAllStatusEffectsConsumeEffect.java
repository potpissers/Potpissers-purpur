package net.minecraft.world.item.consume_effects;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public record ClearAllStatusEffectsConsumeEffect() implements ConsumeEffect {
    public static final ClearAllStatusEffectsConsumeEffect INSTANCE = new ClearAllStatusEffectsConsumeEffect();
    public static final MapCodec<ClearAllStatusEffectsConsumeEffect> CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, ClearAllStatusEffectsConsumeEffect> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public ConsumeEffect.Type<ClearAllStatusEffectsConsumeEffect> getType() {
        return ConsumeEffect.Type.CLEAR_ALL_EFFECTS;
    }

    @Override
    // CraftBukkit start
    public boolean apply(Level level, ItemStack stack, LivingEntity entity, org.bukkit.event.entity.EntityPotionEffectEvent.Cause cause) {
        // Purpur start - Option to toggle milk curing bad omen
        net.minecraft.world.effect.MobEffectInstance badOmen = entity.getEffect(net.minecraft.world.effect.MobEffects.BAD_OMEN);
        if (!level.purpurConfig.milkCuresBadOmen && stack.is(net.minecraft.world.item.Items.MILK_BUCKET) && badOmen != null) {
            return entity.removeAllEffects(cause) && entity.addEffect(badOmen);
        }
        // Purpur end - Option to toggle milk curing bad omen
        return entity.removeAllEffects(cause);
        // CraftBukkit end
    }
}
