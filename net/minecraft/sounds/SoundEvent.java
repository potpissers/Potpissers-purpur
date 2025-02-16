package net.minecraft.sounds;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceLocation;

public record SoundEvent(ResourceLocation location, Optional<Float> fixedRange) {
    public static final Codec<SoundEvent> DIRECT_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("sound_id").forGetter(SoundEvent::location),
                Codec.FLOAT.lenientOptionalFieldOf("range").forGetter(SoundEvent::fixedRange)
            )
            .apply(instance, SoundEvent::create)
    );
    public static final Codec<Holder<SoundEvent>> CODEC = RegistryFileCodec.create(Registries.SOUND_EVENT, DIRECT_CODEC);
    public static final StreamCodec<ByteBuf, SoundEvent> DIRECT_STREAM_CODEC = StreamCodec.composite(
        ResourceLocation.STREAM_CODEC, SoundEvent::location, ByteBufCodecs.FLOAT.apply(ByteBufCodecs::optional), SoundEvent::fixedRange, SoundEvent::create
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<SoundEvent>> STREAM_CODEC = ByteBufCodecs.holder(
        Registries.SOUND_EVENT, DIRECT_STREAM_CODEC
    );

    private static SoundEvent create(ResourceLocation location, Optional<Float> range) {
        return range.<SoundEvent>map(r -> createFixedRangeEvent(location, r)).orElseGet(() -> createVariableRangeEvent(location));
    }

    public static SoundEvent createVariableRangeEvent(ResourceLocation location) {
        return new SoundEvent(location, Optional.empty());
    }

    public static SoundEvent createFixedRangeEvent(ResourceLocation location, float range) {
        return new SoundEvent(location, Optional.of(range));
    }

    public float getRange(float volume) {
        return this.fixedRange.orElse(volume > 1.0F ? 16.0F * volume : 16.0F);
    }
}
