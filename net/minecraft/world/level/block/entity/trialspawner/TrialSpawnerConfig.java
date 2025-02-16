package net.minecraft.world.level.block.entity.trialspawner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

public record TrialSpawnerConfig(
    int spawnRange,
    float totalMobs,
    float simultaneousMobs,
    float totalMobsAddedPerPlayer,
    float simultaneousMobsAddedPerPlayer,
    int ticksBetweenSpawn,
    SimpleWeightedRandomList<SpawnData> spawnPotentialsDefinition,
    SimpleWeightedRandomList<ResourceKey<LootTable>> lootTablesToEject,
    ResourceKey<LootTable> itemsToDropWhenOminous
) {
    public static final TrialSpawnerConfig DEFAULT = builder().build();
    public static final Codec<TrialSpawnerConfig> DIRECT_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                Codec.intRange(1, 128).optionalFieldOf("spawn_range", DEFAULT.spawnRange).forGetter(TrialSpawnerConfig::spawnRange),
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("total_mobs", DEFAULT.totalMobs).forGetter(TrialSpawnerConfig::totalMobs),
                Codec.floatRange(0.0F, Float.MAX_VALUE)
                    .optionalFieldOf("simultaneous_mobs", DEFAULT.simultaneousMobs)
                    .forGetter(TrialSpawnerConfig::simultaneousMobs),
                Codec.floatRange(0.0F, Float.MAX_VALUE)
                    .optionalFieldOf("total_mobs_added_per_player", DEFAULT.totalMobsAddedPerPlayer)
                    .forGetter(TrialSpawnerConfig::totalMobsAddedPerPlayer),
                Codec.floatRange(0.0F, Float.MAX_VALUE)
                    .optionalFieldOf("simultaneous_mobs_added_per_player", DEFAULT.simultaneousMobsAddedPerPlayer)
                    .forGetter(TrialSpawnerConfig::simultaneousMobsAddedPerPlayer),
                Codec.intRange(0, Integer.MAX_VALUE)
                    .optionalFieldOf("ticks_between_spawn", DEFAULT.ticksBetweenSpawn)
                    .forGetter(TrialSpawnerConfig::ticksBetweenSpawn),
                SpawnData.LIST_CODEC
                    .optionalFieldOf("spawn_potentials", SimpleWeightedRandomList.empty())
                    .forGetter(TrialSpawnerConfig::spawnPotentialsDefinition),
                SimpleWeightedRandomList.wrappedCodecAllowingEmpty(ResourceKey.codec(Registries.LOOT_TABLE))
                    .optionalFieldOf("loot_tables_to_eject", DEFAULT.lootTablesToEject)
                    .forGetter(TrialSpawnerConfig::lootTablesToEject),
                ResourceKey.codec(Registries.LOOT_TABLE)
                    .optionalFieldOf("items_to_drop_when_ominous", DEFAULT.itemsToDropWhenOminous)
                    .forGetter(TrialSpawnerConfig::itemsToDropWhenOminous)
            )
            .apply(instance, TrialSpawnerConfig::new)
    );
    public static final Codec<Holder<TrialSpawnerConfig>> CODEC = RegistryFileCodec.create(Registries.TRIAL_SPAWNER_CONFIG, DIRECT_CODEC);

    public int calculateTargetTotalMobs(int players) {
        return (int)Math.floor(this.totalMobs + this.totalMobsAddedPerPlayer * players);
    }

    public int calculateTargetSimultaneousMobs(int players) {
        return (int)Math.floor(this.simultaneousMobs + this.simultaneousMobsAddedPerPlayer * players);
    }

    public long ticksBetweenItemSpawners() {
        return 160L;
    }

    public static TrialSpawnerConfig.Builder builder() {
        return new TrialSpawnerConfig.Builder();
    }

    public TrialSpawnerConfig withSpawning(EntityType<?> entityType) {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString());
        SpawnData spawnData = new SpawnData(compoundTag, Optional.empty(), Optional.empty());
        return new TrialSpawnerConfig(
            this.spawnRange,
            this.totalMobs,
            this.simultaneousMobs,
            this.totalMobsAddedPerPlayer,
            this.simultaneousMobsAddedPerPlayer,
            this.ticksBetweenSpawn,
            SimpleWeightedRandomList.single(spawnData),
            this.lootTablesToEject,
            this.itemsToDropWhenOminous
        );
    }

    public static class Builder {
        private int spawnRange = 4;
        private float totalMobs = 6.0F;
        private float simultaneousMobs = 2.0F;
        private float totalMobsAddedPerPlayer = 2.0F;
        private float simultaneousMobsAddedPerPlayer = 1.0F;
        private int ticksBetweenSpawn = 40;
        private SimpleWeightedRandomList<SpawnData> spawnPotentialsDefinition = SimpleWeightedRandomList.empty();
        private SimpleWeightedRandomList<ResourceKey<LootTable>> lootTablesToEject = SimpleWeightedRandomList.<ResourceKey<LootTable>>builder()
            .add(BuiltInLootTables.SPAWNER_TRIAL_CHAMBER_CONSUMABLES)
            .add(BuiltInLootTables.SPAWNER_TRIAL_CHAMBER_KEY)
            .build();
        private ResourceKey<LootTable> itemsToDropWhenOminous = BuiltInLootTables.SPAWNER_TRIAL_ITEMS_TO_DROP_WHEN_OMINOUS;

        public TrialSpawnerConfig.Builder spawnRange(int spawnRange) {
            this.spawnRange = spawnRange;
            return this;
        }

        public TrialSpawnerConfig.Builder totalMobs(float totalMobs) {
            this.totalMobs = totalMobs;
            return this;
        }

        public TrialSpawnerConfig.Builder simultaneousMobs(float simultaneousMobs) {
            this.simultaneousMobs = simultaneousMobs;
            return this;
        }

        public TrialSpawnerConfig.Builder totalMobsAddedPerPlayer(float totalMobsAddedPerPlayer) {
            this.totalMobsAddedPerPlayer = totalMobsAddedPerPlayer;
            return this;
        }

        public TrialSpawnerConfig.Builder simultaneousMobsAddedPerPlayer(float simultaneousMobsAddedPerPlayer) {
            this.simultaneousMobsAddedPerPlayer = simultaneousMobsAddedPerPlayer;
            return this;
        }

        public TrialSpawnerConfig.Builder ticksBetweenSpawn(int ticksBetweenSpawn) {
            this.ticksBetweenSpawn = ticksBetweenSpawn;
            return this;
        }

        public TrialSpawnerConfig.Builder spawnPotentialsDefinition(SimpleWeightedRandomList<SpawnData> spawnPotentialsDefinition) {
            this.spawnPotentialsDefinition = spawnPotentialsDefinition;
            return this;
        }

        public TrialSpawnerConfig.Builder lootTablesToEject(SimpleWeightedRandomList<ResourceKey<LootTable>> lootTablesToEject) {
            this.lootTablesToEject = lootTablesToEject;
            return this;
        }

        public TrialSpawnerConfig.Builder itemsToDropWhenOminous(ResourceKey<LootTable> itemsToDropWhenOminous) {
            this.itemsToDropWhenOminous = itemsToDropWhenOminous;
            return this;
        }

        public TrialSpawnerConfig build() {
            return new TrialSpawnerConfig(
                this.spawnRange,
                this.totalMobs,
                this.simultaneousMobs,
                this.totalMobsAddedPerPlayer,
                this.simultaneousMobsAddedPerPlayer,
                this.ticksBetweenSpawn,
                this.spawnPotentialsDefinition,
                this.lootTablesToEject,
                this.itemsToDropWhenOminous
            );
        }
    }
}
