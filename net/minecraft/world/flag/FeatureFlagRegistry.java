package net.minecraft.world.flag;

import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class FeatureFlagRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final FeatureFlagUniverse universe;
    private final Map<ResourceLocation, FeatureFlag> names;
    private final FeatureFlagSet allFlags;

    FeatureFlagRegistry(FeatureFlagUniverse universe, FeatureFlagSet allFlags, Map<ResourceLocation, FeatureFlag> names) {
        this.universe = universe;
        this.names = names;
        this.allFlags = allFlags;
    }

    public boolean isSubset(FeatureFlagSet set) {
        return set.isSubsetOf(this.allFlags);
    }

    public FeatureFlagSet allFlags() {
        return this.allFlags;
    }

    public FeatureFlagSet fromNames(Iterable<ResourceLocation> names) {
        return this.fromNames(names, errorName -> LOGGER.warn("Unknown feature flag: {}", errorName));
    }

    public FeatureFlagSet subset(FeatureFlag... flags) {
        return FeatureFlagSet.create(this.universe, Arrays.asList(flags));
    }

    public FeatureFlagSet fromNames(Iterable<ResourceLocation> names, Consumer<ResourceLocation> onError) {
        Set<FeatureFlag> set = Sets.newIdentityHashSet();

        for (ResourceLocation resourceLocation : names) {
            FeatureFlag featureFlag = this.names.get(resourceLocation);
            if (featureFlag == null) {
                onError.accept(resourceLocation);
            } else {
                set.add(featureFlag);
            }
        }

        return FeatureFlagSet.create(this.universe, set);
    }

    public Set<ResourceLocation> toNames(FeatureFlagSet set) {
        Set<ResourceLocation> set1 = new HashSet<>();
        this.names.forEach((name, flag) -> {
            if (set.contains(flag)) {
                set1.add(name);
            }
        });
        return set1;
    }

    public Codec<FeatureFlagSet> codec() {
        return ResourceLocation.CODEC.listOf().comapFlatMap(list -> {
            Set<ResourceLocation> set = new HashSet<>();
            FeatureFlagSet featureFlagSet = this.fromNames(list, set::add);
            return !set.isEmpty() ? DataResult.error(() -> "Unknown feature ids: " + set, featureFlagSet) : DataResult.success(featureFlagSet);
        }, featureFlagSet -> List.copyOf(this.toNames(featureFlagSet)));
    }

    public static class Builder {
        private final FeatureFlagUniverse universe;
        private int id;
        private final Map<ResourceLocation, FeatureFlag> flags = new LinkedHashMap<>();

        public Builder(String id) {
            this.universe = new FeatureFlagUniverse(id);
        }

        public FeatureFlag createVanilla(String id) {
            return this.create(ResourceLocation.withDefaultNamespace(id));
        }

        public FeatureFlag create(ResourceLocation location) {
            if (this.id >= 64) {
                throw new IllegalStateException("Too many feature flags");
            } else {
                FeatureFlag featureFlag = new FeatureFlag(this.universe, this.id++);
                FeatureFlag featureFlag1 = this.flags.put(location, featureFlag);
                if (featureFlag1 != null) {
                    throw new IllegalStateException("Duplicate feature flag " + location);
                } else {
                    return featureFlag;
                }
            }
        }

        public FeatureFlagRegistry build() {
            FeatureFlagSet featureFlagSet = FeatureFlagSet.create(this.universe, this.flags.values());
            return new FeatureFlagRegistry(this.universe, featureFlagSet, Map.copyOf(this.flags));
        }
    }
}
