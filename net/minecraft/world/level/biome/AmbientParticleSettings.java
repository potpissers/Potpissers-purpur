package net.minecraft.world.level.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

public class AmbientParticleSettings {
    public static final Codec<AmbientParticleSettings> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ParticleTypes.CODEC.fieldOf("options").forGetter(settings -> settings.options),
                Codec.FLOAT.fieldOf("probability").forGetter(settings -> settings.probability)
            )
            .apply(instance, AmbientParticleSettings::new)
    );
    private final ParticleOptions options;
    private final float probability;

    public AmbientParticleSettings(ParticleOptions options, float probability) {
        this.options = options;
        this.probability = probability;
    }

    public ParticleOptions getOptions() {
        return this.options;
    }

    public boolean canSpawn(RandomSource random) {
        return random.nextFloat() <= this.probability;
    }
}
