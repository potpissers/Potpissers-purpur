package net.minecraft.world.level.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

public class AmbientAdditionsSettings {
    public static final Codec<AmbientAdditionsSettings> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                SoundEvent.CODEC.fieldOf("sound").forGetter(settings -> settings.soundEvent),
                Codec.DOUBLE.fieldOf("tick_chance").forGetter(settings -> settings.tickChance)
            )
            .apply(instance, AmbientAdditionsSettings::new)
    );
    private final Holder<SoundEvent> soundEvent;
    private final double tickChance;

    public AmbientAdditionsSettings(Holder<SoundEvent> soundEvent, double tickChance) {
        this.soundEvent = soundEvent;
        this.tickChance = tickChance;
    }

    public Holder<SoundEvent> getSoundEvent() {
        return this.soundEvent;
    }

    public double getTickChance() {
        return this.tickChance;
    }
}
