package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;

public class CountConfiguration implements FeatureConfiguration {
    public static final Codec<CountConfiguration> CODEC = IntProvider.codec(0, 256)
        .fieldOf("count")
        .xmap(CountConfiguration::new, CountConfiguration::count)
        .codec();
    private final IntProvider count;

    public CountConfiguration(int count) {
        this.count = ConstantInt.of(count);
    }

    public CountConfiguration(IntProvider count) {
        this.count = count;
    }

    public IntProvider count() {
        return this.count;
    }
}
