package net.minecraft.world.level.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.random.SimpleWeightedRandomList;

public class BiomeSpecialEffects {
    public static final Codec<BiomeSpecialEffects> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                Codec.INT.fieldOf("fog_color").forGetter(effects -> effects.fogColor),
                Codec.INT.fieldOf("water_color").forGetter(effects -> effects.waterColor),
                Codec.INT.fieldOf("water_fog_color").forGetter(effects -> effects.waterFogColor),
                Codec.INT.fieldOf("sky_color").forGetter(effects -> effects.skyColor),
                Codec.INT.optionalFieldOf("foliage_color").forGetter(effects -> effects.foliageColorOverride),
                Codec.INT.optionalFieldOf("grass_color").forGetter(effects -> effects.grassColorOverride),
                BiomeSpecialEffects.GrassColorModifier.CODEC
                    .optionalFieldOf("grass_color_modifier", BiomeSpecialEffects.GrassColorModifier.NONE)
                    .forGetter(effects -> effects.grassColorModifier),
                AmbientParticleSettings.CODEC.optionalFieldOf("particle").forGetter(effects -> effects.ambientParticleSettings),
                SoundEvent.CODEC.optionalFieldOf("ambient_sound").forGetter(effects -> effects.ambientLoopSoundEvent),
                AmbientMoodSettings.CODEC.optionalFieldOf("mood_sound").forGetter(effects -> effects.ambientMoodSettings),
                AmbientAdditionsSettings.CODEC.optionalFieldOf("additions_sound").forGetter(effects -> effects.ambientAdditionsSettings),
                SimpleWeightedRandomList.wrappedCodecAllowingEmpty(Music.CODEC).optionalFieldOf("music").forGetter(effects -> effects.backgroundMusic),
                Codec.FLOAT.fieldOf("music_volume").orElse(1.0F).forGetter(effects -> effects.backgroundMusicVolume)
            )
            .apply(instance, BiomeSpecialEffects::new)
    );
    private final int fogColor;
    private final int waterColor;
    private final int waterFogColor;
    private final int skyColor;
    private final Optional<Integer> foliageColorOverride;
    private final Optional<Integer> grassColorOverride;
    private final BiomeSpecialEffects.GrassColorModifier grassColorModifier;
    private final Optional<AmbientParticleSettings> ambientParticleSettings;
    private final Optional<Holder<SoundEvent>> ambientLoopSoundEvent;
    private final Optional<AmbientMoodSettings> ambientMoodSettings;
    private final Optional<AmbientAdditionsSettings> ambientAdditionsSettings;
    private final Optional<SimpleWeightedRandomList<Music>> backgroundMusic;
    private final float backgroundMusicVolume;

    BiomeSpecialEffects(
        int fogColor,
        int waterColor,
        int waterFogColor,
        int skyColor,
        Optional<Integer> foliageColorOverride,
        Optional<Integer> grassColorOverride,
        BiomeSpecialEffects.GrassColorModifier grassColorModifier,
        Optional<AmbientParticleSettings> ambientParticleSettings,
        Optional<Holder<SoundEvent>> ambientLoopSoundEvent,
        Optional<AmbientMoodSettings> ambientMoodSettings,
        Optional<AmbientAdditionsSettings> ambientAdditionsSettings,
        Optional<SimpleWeightedRandomList<Music>> backgroundMusic,
        float backgroundMusicVolume
    ) {
        this.fogColor = fogColor;
        this.waterColor = waterColor;
        this.waterFogColor = waterFogColor;
        this.skyColor = skyColor;
        this.foliageColorOverride = foliageColorOverride;
        this.grassColorOverride = grassColorOverride;
        this.grassColorModifier = grassColorModifier;
        this.ambientParticleSettings = ambientParticleSettings;
        this.ambientLoopSoundEvent = ambientLoopSoundEvent;
        this.ambientMoodSettings = ambientMoodSettings;
        this.ambientAdditionsSettings = ambientAdditionsSettings;
        this.backgroundMusic = backgroundMusic;
        this.backgroundMusicVolume = backgroundMusicVolume;
    }

    public int getFogColor() {
        return this.fogColor;
    }

    public int getWaterColor() {
        return this.waterColor;
    }

    public int getWaterFogColor() {
        return this.waterFogColor;
    }

    public int getSkyColor() {
        return this.skyColor;
    }

    public Optional<Integer> getFoliageColorOverride() {
        return this.foliageColorOverride;
    }

    public Optional<Integer> getGrassColorOverride() {
        return this.grassColorOverride;
    }

    public BiomeSpecialEffects.GrassColorModifier getGrassColorModifier() {
        return this.grassColorModifier;
    }

    public Optional<AmbientParticleSettings> getAmbientParticleSettings() {
        return this.ambientParticleSettings;
    }

    public Optional<Holder<SoundEvent>> getAmbientLoopSoundEvent() {
        return this.ambientLoopSoundEvent;
    }

    public Optional<AmbientMoodSettings> getAmbientMoodSettings() {
        return this.ambientMoodSettings;
    }

    public Optional<AmbientAdditionsSettings> getAmbientAdditionsSettings() {
        return this.ambientAdditionsSettings;
    }

    public Optional<SimpleWeightedRandomList<Music>> getBackgroundMusic() {
        return this.backgroundMusic;
    }

    public float getBackgroundMusicVolume() {
        return this.backgroundMusicVolume;
    }

    public static class Builder {
        private OptionalInt fogColor = OptionalInt.empty();
        private OptionalInt waterColor = OptionalInt.empty();
        private OptionalInt waterFogColor = OptionalInt.empty();
        private OptionalInt skyColor = OptionalInt.empty();
        private Optional<Integer> foliageColorOverride = Optional.empty();
        private Optional<Integer> grassColorOverride = Optional.empty();
        private BiomeSpecialEffects.GrassColorModifier grassColorModifier = BiomeSpecialEffects.GrassColorModifier.NONE;
        private Optional<AmbientParticleSettings> ambientParticle = Optional.empty();
        private Optional<Holder<SoundEvent>> ambientLoopSoundEvent = Optional.empty();
        private Optional<AmbientMoodSettings> ambientMoodSettings = Optional.empty();
        private Optional<AmbientAdditionsSettings> ambientAdditionsSettings = Optional.empty();
        private Optional<SimpleWeightedRandomList<Music>> backgroundMusic = Optional.empty();
        private float backgroundMusicVolume = 1.0F;

        public BiomeSpecialEffects.Builder fogColor(int fogColor) {
            this.fogColor = OptionalInt.of(fogColor);
            return this;
        }

        public BiomeSpecialEffects.Builder waterColor(int waterColor) {
            this.waterColor = OptionalInt.of(waterColor);
            return this;
        }

        public BiomeSpecialEffects.Builder waterFogColor(int waterFogColor) {
            this.waterFogColor = OptionalInt.of(waterFogColor);
            return this;
        }

        public BiomeSpecialEffects.Builder skyColor(int skyColor) {
            this.skyColor = OptionalInt.of(skyColor);
            return this;
        }

        public BiomeSpecialEffects.Builder foliageColorOverride(int foliageColorOverride) {
            this.foliageColorOverride = Optional.of(foliageColorOverride);
            return this;
        }

        public BiomeSpecialEffects.Builder grassColorOverride(int grassColorOverride) {
            this.grassColorOverride = Optional.of(grassColorOverride);
            return this;
        }

        public BiomeSpecialEffects.Builder grassColorModifier(BiomeSpecialEffects.GrassColorModifier grassColorModifier) {
            this.grassColorModifier = grassColorModifier;
            return this;
        }

        public BiomeSpecialEffects.Builder ambientParticle(AmbientParticleSettings ambientParticle) {
            this.ambientParticle = Optional.of(ambientParticle);
            return this;
        }

        public BiomeSpecialEffects.Builder ambientLoopSound(Holder<SoundEvent> ambientLoopSoundEvent) {
            this.ambientLoopSoundEvent = Optional.of(ambientLoopSoundEvent);
            return this;
        }

        public BiomeSpecialEffects.Builder ambientMoodSound(AmbientMoodSettings ambientMoodSettings) {
            this.ambientMoodSettings = Optional.of(ambientMoodSettings);
            return this;
        }

        public BiomeSpecialEffects.Builder ambientAdditionsSound(AmbientAdditionsSettings ambientAdditionsSettings) {
            this.ambientAdditionsSettings = Optional.of(ambientAdditionsSettings);
            return this;
        }

        public BiomeSpecialEffects.Builder backgroundMusic(@Nullable Music backgroundMusic) {
            if (backgroundMusic == null) {
                this.backgroundMusic = Optional.empty();
                return this;
            } else {
                this.backgroundMusic = Optional.of(SimpleWeightedRandomList.single(backgroundMusic));
                return this;
            }
        }

        public BiomeSpecialEffects.Builder silenceAllBackgroundMusic() {
            return this.backgroundMusic(SimpleWeightedRandomList.empty()).backgroundMusicVolume(0.0F);
        }

        public BiomeSpecialEffects.Builder backgroundMusic(SimpleWeightedRandomList<Music> backgroundMusic) {
            this.backgroundMusic = Optional.of(backgroundMusic);
            return this;
        }

        public BiomeSpecialEffects.Builder backgroundMusicVolume(float backgroundMusicVolume) {
            this.backgroundMusicVolume = backgroundMusicVolume;
            return this;
        }

        public BiomeSpecialEffects build() {
            return new BiomeSpecialEffects(
                this.fogColor.orElseThrow(() -> new IllegalStateException("Missing 'fog' color.")),
                this.waterColor.orElseThrow(() -> new IllegalStateException("Missing 'water' color.")),
                this.waterFogColor.orElseThrow(() -> new IllegalStateException("Missing 'water fog' color.")),
                this.skyColor.orElseThrow(() -> new IllegalStateException("Missing 'sky' color.")),
                this.foliageColorOverride,
                this.grassColorOverride,
                this.grassColorModifier,
                this.ambientParticle,
                this.ambientLoopSoundEvent,
                this.ambientMoodSettings,
                this.ambientAdditionsSettings,
                this.backgroundMusic,
                this.backgroundMusicVolume
            );
        }
    }

    public static enum GrassColorModifier implements StringRepresentable {
        NONE("none") {
            @Override
            public int modifyColor(double x, double z, int grassColor) {
                return grassColor;
            }
        },
        DARK_FOREST("dark_forest") {
            @Override
            public int modifyColor(double x, double z, int grassColor) {
                return (grassColor & 16711422) + 2634762 >> 1;
            }
        },
        SWAMP("swamp") {
            @Override
            public int modifyColor(double x, double z, int grassColor) {
                double value = Biome.BIOME_INFO_NOISE.getValue(x * 0.0225, z * 0.0225, false);
                return value < -0.1 ? 5011004 : 6975545;
            }
        };

        private final String name;
        public static final Codec<BiomeSpecialEffects.GrassColorModifier> CODEC = StringRepresentable.fromEnum(BiomeSpecialEffects.GrassColorModifier::values);

        public abstract int modifyColor(double x, double d, int z);

        GrassColorModifier(final String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
