package net.minecraft.world.item.consume_effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public interface ConsumeEffect {
    Codec<ConsumeEffect> CODEC = BuiltInRegistries.CONSUME_EFFECT_TYPE.byNameCodec().dispatch(ConsumeEffect::getType, ConsumeEffect.Type::codec);
    StreamCodec<RegistryFriendlyByteBuf, ConsumeEffect> STREAM_CODEC = ByteBufCodecs.registry(Registries.CONSUME_EFFECT_TYPE)
        .dispatch(ConsumeEffect::getType, ConsumeEffect.Type::streamCodec);

    ConsumeEffect.Type<? extends ConsumeEffect> getType();

    // CraftBukkit start
    default boolean apply(Level level, ItemStack stack, LivingEntity entity) {
        return this.apply(level, stack, entity, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    default boolean apply(Level level, ItemStack stack, LivingEntity entity, org.bukkit.event.entity.EntityPotionEffectEvent.Cause cause) {
        return this.apply(level, stack, entity);
    }
    // CraftBukkit end

    public record Type<T extends ConsumeEffect>(MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec) {
        public static final ConsumeEffect.Type<ApplyStatusEffectsConsumeEffect> APPLY_EFFECTS = register(
            "apply_effects", ApplyStatusEffectsConsumeEffect.CODEC, ApplyStatusEffectsConsumeEffect.STREAM_CODEC
        );
        public static final ConsumeEffect.Type<RemoveStatusEffectsConsumeEffect> REMOVE_EFFECTS = register(
            "remove_effects", RemoveStatusEffectsConsumeEffect.CODEC, RemoveStatusEffectsConsumeEffect.STREAM_CODEC
        );
        public static final ConsumeEffect.Type<ClearAllStatusEffectsConsumeEffect> CLEAR_ALL_EFFECTS = register(
            "clear_all_effects", ClearAllStatusEffectsConsumeEffect.CODEC, ClearAllStatusEffectsConsumeEffect.STREAM_CODEC
        );
        public static final ConsumeEffect.Type<TeleportRandomlyConsumeEffect> TELEPORT_RANDOMLY = register(
            "teleport_randomly", TeleportRandomlyConsumeEffect.CODEC, TeleportRandomlyConsumeEffect.STREAM_CODEC
        );
        public static final ConsumeEffect.Type<PlaySoundConsumeEffect> PLAY_SOUND = register(
            "play_sound", PlaySoundConsumeEffect.CODEC, PlaySoundConsumeEffect.STREAM_CODEC
        );

        private static <T extends ConsumeEffect> ConsumeEffect.Type<T> register(
            String name, MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec
        ) {
            return Registry.register(BuiltInRegistries.CONSUME_EFFECT_TYPE, name, new ConsumeEffect.Type<>(codec, streamCodec));
        }
    }
}
