package net.minecraft.world.level.biome;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.random.Weight;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.slf4j.Logger;

public class MobSpawnSettings {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float DEFAULT_CREATURE_SPAWN_PROBABILITY = 0.1F;
    public static final WeightedRandomList<MobSpawnSettings.SpawnerData> EMPTY_MOB_LIST = WeightedRandomList.create();
    public static final MobSpawnSettings EMPTY = new MobSpawnSettings.Builder().build();
    public static final MapCodec<MobSpawnSettings> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.floatRange(0.0F, 0.9999999F)
                    .optionalFieldOf("creature_spawn_probability", 0.1F)
                    .forGetter(settings -> settings.creatureGenerationProbability),
                Codec.simpleMap(
                        MobCategory.CODEC,
                        WeightedRandomList.codec(MobSpawnSettings.SpawnerData.CODEC).promotePartial(Util.prefix("Spawn data: ", LOGGER::error)),
                        StringRepresentable.keys(MobCategory.values())
                    )
                    .fieldOf("spawners")
                    .forGetter(settings -> settings.spawners),
                Codec.simpleMap(BuiltInRegistries.ENTITY_TYPE.byNameCodec(), MobSpawnSettings.MobSpawnCost.CODEC, BuiltInRegistries.ENTITY_TYPE)
                    .fieldOf("spawn_costs")
                    .forGetter(settings -> settings.mobSpawnCosts)
            )
            .apply(instance, MobSpawnSettings::new)
    );
    private final float creatureGenerationProbability;
    private final Map<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> spawners;
    private final Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> mobSpawnCosts;

    MobSpawnSettings(
        float creatureGenerationProbability,
        Map<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> spawners,
        Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> mobSpawnCosts
    ) {
        this.creatureGenerationProbability = creatureGenerationProbability;
        this.spawners = ImmutableMap.copyOf(spawners);
        this.mobSpawnCosts = ImmutableMap.copyOf(mobSpawnCosts);
    }

    public WeightedRandomList<MobSpawnSettings.SpawnerData> getMobs(MobCategory category) {
        return this.spawners.getOrDefault(category, EMPTY_MOB_LIST);
    }

    @Nullable
    public MobSpawnSettings.MobSpawnCost getMobSpawnCost(EntityType<?> entityType) {
        return this.mobSpawnCosts.get(entityType);
    }

    public float getCreatureProbability() {
        return this.creatureGenerationProbability;
    }

    public static class Builder {
        // Paper start - Perf: keep track of data in a pair set to give O(1) contains calls - we have to hook removals incase plugins mess with it
        public static class MobList extends java.util.ArrayList<MobSpawnSettings.SpawnerData> {
            java.util.Set<MobSpawnSettings.SpawnerData> biomes = new java.util.HashSet<>();

            @Override
            public boolean contains(Object o) {
                return biomes.contains(o);
            }

            @Override
            public boolean add(MobSpawnSettings.SpawnerData BiomeSettingsMobs) {
                biomes.add(BiomeSettingsMobs);
                return super.add(BiomeSettingsMobs);
            }

            @Override
            public MobSpawnSettings.SpawnerData remove(int index) {
                MobSpawnSettings.SpawnerData removed = super.remove(index);
                if (removed != null) {
                    biomes.remove(removed);
                }
                return removed;
            }

            @Override
            public void clear() {
                biomes.clear();
                super.clear();
            }
        }
        // use toImmutableEnumMap collector
        private final Map<MobCategory, List<MobSpawnSettings.SpawnerData>> spawners = Stream.of(MobCategory.values())
            .collect(Maps.toImmutableEnumMap(mobCategory -> (MobCategory)mobCategory, mobCategory -> new MobList())); // Use MobList instead of ArrayList
        // Paper end - Perf: keep track of data in a pair set to give O(1) contains calls
        private final Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> mobSpawnCosts = Maps.newLinkedHashMap();
        private float creatureGenerationProbability = 0.1F;

        public MobSpawnSettings.Builder addSpawn(MobCategory classification, MobSpawnSettings.SpawnerData spawner) {
            this.spawners.get(classification).add(spawner);
            return this;
        }

        public MobSpawnSettings.Builder addMobCharge(EntityType<?> entityType, double charge, double energyBudget) {
            this.mobSpawnCosts.put(entityType, new MobSpawnSettings.MobSpawnCost(energyBudget, charge));
            return this;
        }

        public MobSpawnSettings.Builder creatureGenerationProbability(float probability) {
            this.creatureGenerationProbability = probability;
            return this;
        }

        public MobSpawnSettings build() {
            return new MobSpawnSettings(
                this.creatureGenerationProbability,
                this.spawners.entrySet().stream().collect(ImmutableMap.toImmutableMap(Entry::getKey, entry -> WeightedRandomList.create(entry.getValue()))),
                ImmutableMap.copyOf(this.mobSpawnCosts)
            );
        }
    }

    public record MobSpawnCost(double energyBudget, double charge) {
        public static final Codec<MobSpawnSettings.MobSpawnCost> CODEC = RecordCodecBuilder.create(
            codec -> codec.group(
                    Codec.DOUBLE.fieldOf("energy_budget").forGetter(cost -> cost.energyBudget), Codec.DOUBLE.fieldOf("charge").forGetter(cost -> cost.charge)
                )
                .apply(codec, MobSpawnSettings.MobSpawnCost::new)
        );
    }

    public static class SpawnerData extends WeightedEntry.IntrusiveBase {
        public static final Codec<MobSpawnSettings.SpawnerData> CODEC = RecordCodecBuilder.<MobSpawnSettings.SpawnerData>create(
                codec -> codec.group(
                        BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("type").forGetter(data -> data.type),
                        Weight.CODEC.fieldOf("weight").forGetter(WeightedEntry.IntrusiveBase::getWeight),
                        ExtraCodecs.POSITIVE_INT.fieldOf("minCount").forGetter(data -> data.minCount),
                        ExtraCodecs.POSITIVE_INT.fieldOf("maxCount").forGetter(data -> data.maxCount)
                    )
                    .apply(codec, MobSpawnSettings.SpawnerData::new)
            )
            .validate(
                spawnerData -> spawnerData.minCount > spawnerData.maxCount
                    ? DataResult.error(() -> "minCount needs to be smaller or equal to maxCount")
                    : DataResult.success(spawnerData)
            );
        public final EntityType<?> type;
        public final int minCount;
        public final int maxCount;

        public SpawnerData(EntityType<?> type, int weight, int minCount, int maxCount) {
            this(type, Weight.of(weight), minCount, maxCount);
        }

        public SpawnerData(EntityType<?> type, Weight weight, int minCount, int maxCount) {
            super(weight);
            this.type = type.getCategory() == MobCategory.MISC ? EntityType.PIG : type;
            this.minCount = minCount;
            this.maxCount = maxCount;
        }

        @Override
        public String toString() {
            return EntityType.getKey(this.type) + "*(" + this.minCount + "-" + this.maxCount + "):" + this.getWeight();
        }
    }
}
