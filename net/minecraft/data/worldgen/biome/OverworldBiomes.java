package net.minecraft.data.worldgen.biome;

import javax.annotation.Nullable;
import net.minecraft.core.HolderGetter;
import net.minecraft.data.worldgen.BiomeDefaultFeatures;
import net.minecraft.data.worldgen.Carvers;
import net.minecraft.data.worldgen.placement.AquaticPlacements;
import net.minecraft.data.worldgen.placement.MiscOverworldPlacements;
import net.minecraft.data.worldgen.placement.VegetationPlacements;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class OverworldBiomes {
    protected static final int NORMAL_WATER_COLOR = 4159204;
    protected static final int NORMAL_WATER_FOG_COLOR = 329011;
    private static final int OVERWORLD_FOG_COLOR = 12638463;
    @Nullable
    private static final Music NORMAL_MUSIC = null;
    public static final int SWAMP_SKELETON_WEIGHT = 70;

    protected static int calculateSkyColor(float temperature) {
        float f = temperature / 3.0F;
        f = Mth.clamp(f, -1.0F, 1.0F);
        return Mth.hsvToRgb(0.62222224F - f * 0.05F, 0.5F + f * 0.1F, 1.0F);
    }

    private static Biome biome(
        boolean hasPercipitation,
        float temperature,
        float downfall,
        MobSpawnSettings.Builder mobSpawnSettings,
        BiomeGenerationSettings.Builder generationSettings,
        @Nullable Music backgroundMusic
    ) {
        return biome(hasPercipitation, temperature, downfall, 4159204, 329011, null, null, mobSpawnSettings, generationSettings, backgroundMusic);
    }

    private static Biome biome(
        boolean hasPrecipitation,
        float temperature,
        float downfall,
        int waterColor,
        int waterFogColor,
        @Nullable Integer grassColorOverride,
        @Nullable Integer foliageColorOverride,
        MobSpawnSettings.Builder mobSpawnSettings,
        BiomeGenerationSettings.Builder generationSettings,
        @Nullable Music backgroundMusic
    ) {
        BiomeSpecialEffects.Builder builder = new BiomeSpecialEffects.Builder()
            .waterColor(waterColor)
            .waterFogColor(waterFogColor)
            .fogColor(12638463)
            .skyColor(calculateSkyColor(temperature))
            .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
            .backgroundMusic(backgroundMusic);
        if (grassColorOverride != null) {
            builder.grassColorOverride(grassColorOverride);
        }

        if (foliageColorOverride != null) {
            builder.foliageColorOverride(foliageColorOverride);
        }

        return new Biome.BiomeBuilder()
            .hasPrecipitation(hasPrecipitation)
            .temperature(temperature)
            .downfall(downfall)
            .specialEffects(builder.build())
            .mobSpawnSettings(mobSpawnSettings.build())
            .generationSettings(generationSettings.build())
            .build();
    }

    private static void globalOverworldGeneration(BiomeGenerationSettings.Builder generationSettings) {
        BiomeDefaultFeatures.addDefaultCarversAndLakes(generationSettings);
        BiomeDefaultFeatures.addDefaultCrystalFormations(generationSettings);
        BiomeDefaultFeatures.addDefaultMonsterRoom(generationSettings);
        BiomeDefaultFeatures.addDefaultUndergroundVariety(generationSettings);
        BiomeDefaultFeatures.addDefaultSprings(generationSettings);
        BiomeDefaultFeatures.addSurfaceFreezing(generationSettings);
    }

    public static Biome oldGrowthTaiga(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isSpruce) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.farmAnimals(builder);
        builder.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 8, 4, 4));
        builder.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.RABBIT, 4, 2, 3));
        builder.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.FOX, 8, 2, 4));
        if (isSpruce) {
            BiomeDefaultFeatures.commonSpawns(builder);
        } else {
            BiomeDefaultFeatures.caveSpawns(builder);
            BiomeDefaultFeatures.monsters(builder, 100, 25, 100, false);
        }

        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addMossyStoneBlock(builder1);
        BiomeDefaultFeatures.addFerns(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        builder1.addFeature(
            GenerationStep.Decoration.VEGETAL_DECORATION,
            isSpruce ? VegetationPlacements.TREES_OLD_GROWTH_SPRUCE_TAIGA : VegetationPlacements.TREES_OLD_GROWTH_PINE_TAIGA
        );
        BiomeDefaultFeatures.addDefaultFlowers(builder1);
        BiomeDefaultFeatures.addGiantTaigaVegetation(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1);
        BiomeDefaultFeatures.addCommonBerryBushes(builder1);
        Music music = Musics.createGameMusic(SoundEvents.MUSIC_BIOME_OLD_GROWTH_TAIGA);
        return biome(true, isSpruce ? 0.25F : 0.3F, 0.8F, builder, builder1, music);
    }

    public static Biome sparseJungle(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.baseJungleSpawns(builder);
        builder.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 8, 2, 4));
        return baseJungle(placedFeatures, worldCarvers, 0.8F, false, true, false, builder, Musics.createGameMusic(SoundEvents.MUSIC_BIOME_SPARSE_JUNGLE));
    }

    public static Biome jungle(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.baseJungleSpawns(builder);
        builder.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.PARROT, 40, 1, 2))
            .addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.OCELOT, 2, 1, 3))
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.PANDA, 1, 1, 2));
        return baseJungle(placedFeatures, worldCarvers, 0.9F, false, false, true, builder, Musics.createGameMusic(SoundEvents.MUSIC_BIOME_JUNGLE));
    }

    public static Biome bambooJungle(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.baseJungleSpawns(builder);
        builder.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.PARROT, 40, 1, 2))
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.PANDA, 80, 1, 2))
            .addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.OCELOT, 2, 1, 1));
        return baseJungle(placedFeatures, worldCarvers, 0.9F, true, false, true, builder, Musics.createGameMusic(SoundEvents.MUSIC_BIOME_BAMBOO_JUNGLE));
    }

    private static Biome baseJungle(
        HolderGetter<PlacedFeature> placedFeatures,
        HolderGetter<ConfiguredWorldCarver<?>> worldCarvers,
        float downfall,
        boolean isBambooJungle,
        boolean isSparse,
        boolean addBamboo,
        MobSpawnSettings.Builder mobSpawnSettings,
        Music backgroudMusic
    ) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder);
        BiomeDefaultFeatures.addDefaultOres(builder);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder);
        if (isBambooJungle) {
            BiomeDefaultFeatures.addBambooVegetation(builder);
        } else {
            if (addBamboo) {
                BiomeDefaultFeatures.addLightBambooVegetation(builder);
            }

            if (isSparse) {
                BiomeDefaultFeatures.addSparseJungleTrees(builder);
            } else {
                BiomeDefaultFeatures.addJungleTrees(builder);
            }
        }

        BiomeDefaultFeatures.addWarmFlowers(builder);
        BiomeDefaultFeatures.addJungleGrass(builder);
        BiomeDefaultFeatures.addDefaultMushrooms(builder);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder);
        BiomeDefaultFeatures.addJungleVines(builder);
        if (isSparse) {
            BiomeDefaultFeatures.addSparseJungleMelons(builder);
        } else {
            BiomeDefaultFeatures.addJungleMelons(builder);
        }

        return biome(true, 0.95F, downfall, mobSpawnSettings, builder, backgroudMusic);
    }

    public static Biome windsweptHills(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isForest) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.farmAnimals(builder);
        builder.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.LLAMA, 5, 4, 6));
        BiomeDefaultFeatures.commonSpawns(builder);
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        if (isForest) {
            BiomeDefaultFeatures.addMountainForestTrees(builder1);
        } else {
            BiomeDefaultFeatures.addMountainTrees(builder1);
        }

        BiomeDefaultFeatures.addDefaultFlowers(builder1);
        BiomeDefaultFeatures.addDefaultGrass(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1);
        BiomeDefaultFeatures.addExtraEmeralds(builder1);
        BiomeDefaultFeatures.addInfestedStone(builder1);
        return biome(true, 0.2F, 0.3F, builder, builder1, NORMAL_MUSIC);
    }

    public static Biome desert(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.desertSpawns(builder);
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        BiomeDefaultFeatures.addFossilDecoration(builder1);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        BiomeDefaultFeatures.addDefaultFlowers(builder1);
        BiomeDefaultFeatures.addDefaultGrass(builder1);
        BiomeDefaultFeatures.addDesertVegetation(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addDesertExtraVegetation(builder1);
        BiomeDefaultFeatures.addDesertExtraDecoration(builder1);
        return biome(false, 2.0F, 0.0F, builder, builder1, Musics.createGameMusic(SoundEvents.MUSIC_BIOME_DESERT));
    }

    public static Biome plains(
        HolderGetter<PlacedFeature> placedFeatures,
        HolderGetter<ConfiguredWorldCarver<?>> worldCarvers,
        boolean isSunflowerPlains,
        boolean isCold,
        boolean isIceSpikes
    ) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        if (isCold) {
            builder.creatureGenerationProbability(0.07F);
            BiomeDefaultFeatures.snowySpawns(builder);
            if (isIceSpikes) {
                builder1.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, MiscOverworldPlacements.ICE_SPIKE);
                builder1.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, MiscOverworldPlacements.ICE_PATCH);
            }
        } else {
            BiomeDefaultFeatures.plainsSpawns(builder);
            BiomeDefaultFeatures.addPlainGrass(builder1);
            if (isSunflowerPlains) {
                builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.PATCH_SUNFLOWER);
            }
        }

        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        if (isCold) {
            BiomeDefaultFeatures.addSnowyTrees(builder1);
            BiomeDefaultFeatures.addDefaultFlowers(builder1);
            BiomeDefaultFeatures.addDefaultGrass(builder1);
        } else {
            BiomeDefaultFeatures.addPlainVegetation(builder1);
        }

        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        if (isSunflowerPlains) {
            builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.PATCH_SUGAR_CANE);
            builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.PATCH_PUMPKIN);
        } else {
            BiomeDefaultFeatures.addDefaultExtraVegetation(builder1);
        }

        float f = isCold ? 0.0F : 0.8F;
        return biome(true, f, isCold ? 0.5F : 0.4F, builder, builder1, NORMAL_MUSIC);
    }

    public static Biome mushroomFields(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.mooshroomSpawns(builder);
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        BiomeDefaultFeatures.addMushroomFieldVegetation(builder1);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1);
        return biome(true, 0.9F, 1.0F, builder, builder1, NORMAL_MUSIC);
    }

    public static Biome savanna(
        HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isShatteredSavanna, boolean isPlateau
    ) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder);
        if (!isShatteredSavanna) {
            BiomeDefaultFeatures.addSavannaGrass(builder);
        }

        BiomeDefaultFeatures.addDefaultOres(builder);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder);
        if (isShatteredSavanna) {
            BiomeDefaultFeatures.addShatteredSavannaTrees(builder);
            BiomeDefaultFeatures.addDefaultFlowers(builder);
            BiomeDefaultFeatures.addShatteredSavannaGrass(builder);
        } else {
            BiomeDefaultFeatures.addSavannaTrees(builder);
            BiomeDefaultFeatures.addWarmFlowers(builder);
            BiomeDefaultFeatures.addSavannaExtraGrass(builder);
        }

        BiomeDefaultFeatures.addDefaultMushrooms(builder);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder);
        MobSpawnSettings.Builder builder1 = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.farmAnimals(builder1);
        builder1.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.HORSE, 1, 2, 6))
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.DONKEY, 1, 1, 1))
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.ARMADILLO, 10, 2, 3));
        BiomeDefaultFeatures.commonSpawns(builder1);
        if (isPlateau) {
            builder1.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.LLAMA, 8, 4, 4));
            builder1.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 8, 4, 8));
        }

        return biome(false, 2.0F, 0.0F, builder1, builder, NORMAL_MUSIC);
    }

    public static Biome badlands(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean trees) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.commonSpawns(builder);
        builder.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.ARMADILLO, 6, 1, 2));
        builder.creatureGenerationProbability(0.03F);
        if (trees) {
            builder.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 2, 4, 8));
            builder.creatureGenerationProbability(0.04F);
        }

        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addExtraGold(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        if (trees) {
            BiomeDefaultFeatures.addBadlandsTrees(builder1);
        }

        BiomeDefaultFeatures.addBadlandGrass(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addBadlandExtraVegetation(builder1);
        return new Biome.BiomeBuilder()
            .hasPrecipitation(false)
            .temperature(2.0F)
            .downfall(0.0F)
            .specialEffects(
                new BiomeSpecialEffects.Builder()
                    .waterColor(4159204)
                    .waterFogColor(329011)
                    .fogColor(12638463)
                    .skyColor(calculateSkyColor(2.0F))
                    .foliageColorOverride(10387789)
                    .grassColorOverride(9470285)
                    .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
                    .backgroundMusic(Musics.createGameMusic(SoundEvents.MUSIC_BIOME_BADLANDS))
                    .build()
            )
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    private static Biome baseOcean(
        MobSpawnSettings.Builder mobSpawnSettings, int waterColor, int waterFogColor, BiomeGenerationSettings.Builder generationSettings
    ) {
        return biome(true, 0.5F, 0.5F, waterColor, waterFogColor, null, null, mobSpawnSettings, generationSettings, NORMAL_MUSIC);
    }

    private static BiomeGenerationSettings.Builder baseOceanGeneration(
        HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers
    ) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder);
        BiomeDefaultFeatures.addDefaultOres(builder);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder);
        BiomeDefaultFeatures.addWaterTrees(builder);
        BiomeDefaultFeatures.addDefaultFlowers(builder);
        BiomeDefaultFeatures.addDefaultGrass(builder);
        BiomeDefaultFeatures.addDefaultMushrooms(builder);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder);
        return builder;
    }

    public static Biome coldOcean(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isDeep) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.oceanSpawns(builder, 3, 4, 15);
        builder.addSpawn(MobCategory.WATER_AMBIENT, new MobSpawnSettings.SpawnerData(EntityType.SALMON, 15, 1, 5));
        BiomeGenerationSettings.Builder builder1 = baseOceanGeneration(placedFeatures, worldCarvers);
        builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, isDeep ? AquaticPlacements.SEAGRASS_DEEP_COLD : AquaticPlacements.SEAGRASS_COLD);
        BiomeDefaultFeatures.addColdOceanExtraVegetation(builder1);
        return baseOcean(builder, 4020182, 329011, builder1);
    }

    public static Biome ocean(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isDeep) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.oceanSpawns(builder, 1, 4, 10);
        builder.addSpawn(MobCategory.WATER_CREATURE, new MobSpawnSettings.SpawnerData(EntityType.DOLPHIN, 1, 1, 2));
        BiomeGenerationSettings.Builder builder1 = baseOceanGeneration(placedFeatures, worldCarvers);
        builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, isDeep ? AquaticPlacements.SEAGRASS_DEEP : AquaticPlacements.SEAGRASS_NORMAL);
        BiomeDefaultFeatures.addColdOceanExtraVegetation(builder1);
        return baseOcean(builder, 4159204, 329011, builder1);
    }

    public static Biome lukeWarmOcean(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isDeep) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        if (isDeep) {
            BiomeDefaultFeatures.oceanSpawns(builder, 8, 4, 8);
        } else {
            BiomeDefaultFeatures.oceanSpawns(builder, 10, 2, 15);
        }

        builder.addSpawn(MobCategory.WATER_AMBIENT, new MobSpawnSettings.SpawnerData(EntityType.PUFFERFISH, 5, 1, 3))
            .addSpawn(MobCategory.WATER_AMBIENT, new MobSpawnSettings.SpawnerData(EntityType.TROPICAL_FISH, 25, 8, 8))
            .addSpawn(MobCategory.WATER_CREATURE, new MobSpawnSettings.SpawnerData(EntityType.DOLPHIN, 2, 1, 2));
        BiomeGenerationSettings.Builder builder1 = baseOceanGeneration(placedFeatures, worldCarvers);
        builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, isDeep ? AquaticPlacements.SEAGRASS_DEEP_WARM : AquaticPlacements.SEAGRASS_WARM);
        BiomeDefaultFeatures.addLukeWarmKelp(builder1);
        return baseOcean(builder, 4566514, 267827, builder1);
    }

    public static Biome warmOcean(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder()
            .addSpawn(MobCategory.WATER_AMBIENT, new MobSpawnSettings.SpawnerData(EntityType.PUFFERFISH, 15, 1, 3));
        BiomeDefaultFeatures.warmOceanSpawns(builder, 10, 4);
        BiomeGenerationSettings.Builder builder1 = baseOceanGeneration(placedFeatures, worldCarvers)
            .addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, AquaticPlacements.WARM_OCEAN_VEGETATION)
            .addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, AquaticPlacements.SEAGRASS_WARM)
            .addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, AquaticPlacements.SEA_PICKLE);
        return baseOcean(builder, 4445678, 270131, builder1);
    }

    public static Biome frozenOcean(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isDeep) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder()
            .addSpawn(MobCategory.WATER_CREATURE, new MobSpawnSettings.SpawnerData(EntityType.SQUID, 1, 1, 4))
            .addSpawn(MobCategory.WATER_AMBIENT, new MobSpawnSettings.SpawnerData(EntityType.SALMON, 15, 1, 5))
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.POLAR_BEAR, 1, 1, 2));
        BiomeDefaultFeatures.commonSpawns(builder);
        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.DROWNED, 5, 1, 1));
        float f = isDeep ? 0.5F : 0.0F;
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        BiomeDefaultFeatures.addIcebergs(builder1);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addBlueIce(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        BiomeDefaultFeatures.addWaterTrees(builder1);
        BiomeDefaultFeatures.addDefaultFlowers(builder1);
        BiomeDefaultFeatures.addDefaultGrass(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1);
        return new Biome.BiomeBuilder()
            .hasPrecipitation(true)
            .temperature(f)
            .temperatureAdjustment(Biome.TemperatureModifier.FROZEN)
            .downfall(0.5F)
            .specialEffects(
                new BiomeSpecialEffects.Builder()
                    .waterColor(3750089)
                    .waterFogColor(329011)
                    .fogColor(12638463)
                    .skyColor(calculateSkyColor(f))
                    .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
                    .build()
            )
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    public static Biome forest(
        HolderGetter<PlacedFeature> placedFeatures,
        HolderGetter<ConfiguredWorldCarver<?>> worldCarvers,
        boolean isBirchForest,
        boolean tallBirchTrees,
        boolean isFlowerForest
    ) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder);
        Music music;
        if (isFlowerForest) {
            music = Musics.createGameMusic(SoundEvents.MUSIC_BIOME_FLOWER_FOREST);
            builder.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.FLOWER_FOREST_FLOWERS);
        } else {
            music = Musics.createGameMusic(SoundEvents.MUSIC_BIOME_FOREST);
            BiomeDefaultFeatures.addForestFlowers(builder);
        }

        BiomeDefaultFeatures.addDefaultOres(builder);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder);
        if (isFlowerForest) {
            builder.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.TREES_FLOWER_FOREST);
            builder.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.FLOWER_FLOWER_FOREST);
            BiomeDefaultFeatures.addDefaultGrass(builder);
        } else {
            if (isBirchForest) {
                if (tallBirchTrees) {
                    BiomeDefaultFeatures.addTallBirchTrees(builder);
                } else {
                    BiomeDefaultFeatures.addBirchTrees(builder);
                }
            } else {
                BiomeDefaultFeatures.addOtherBirchTrees(builder);
            }

            BiomeDefaultFeatures.addDefaultFlowers(builder);
            BiomeDefaultFeatures.addForestGrass(builder);
        }

        BiomeDefaultFeatures.addDefaultMushrooms(builder);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder);
        MobSpawnSettings.Builder builder1 = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.farmAnimals(builder1);
        BiomeDefaultFeatures.commonSpawns(builder1);
        if (isFlowerForest) {
            builder1.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.RABBIT, 4, 2, 3));
        } else if (!isBirchForest) {
            builder1.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 5, 4, 4));
        }

        float f = isBirchForest ? 0.6F : 0.7F;
        return biome(true, f, isBirchForest ? 0.6F : 0.8F, builder1, builder, music);
    }

    public static Biome taiga(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isCold) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.farmAnimals(builder);
        builder.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 8, 4, 4))
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.RABBIT, 4, 2, 3))
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.FOX, 8, 2, 4));
        BiomeDefaultFeatures.commonSpawns(builder);
        float f = isCold ? -0.5F : 0.25F;
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addFerns(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        BiomeDefaultFeatures.addTaigaTrees(builder1);
        BiomeDefaultFeatures.addDefaultFlowers(builder1);
        BiomeDefaultFeatures.addTaigaGrass(builder1);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1);
        if (isCold) {
            BiomeDefaultFeatures.addRareBerryBushes(builder1);
        } else {
            BiomeDefaultFeatures.addCommonBerryBushes(builder1);
        }

        return biome(true, f, isCold ? 0.4F : 0.8F, isCold ? 4020182 : 4159204, 329011, null, null, builder, builder1, NORMAL_MUSIC);
    }

    public static Biome darkForest(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isPaleGarden) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        if (!isPaleGarden) {
            BiomeDefaultFeatures.farmAnimals(builder);
        }

        BiomeDefaultFeatures.commonSpawns(builder);
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        builder1.addFeature(
            GenerationStep.Decoration.VEGETAL_DECORATION,
            isPaleGarden ? VegetationPlacements.PALE_GARDEN_VEGETATION : VegetationPlacements.DARK_FOREST_VEGETATION
        );
        if (!isPaleGarden) {
            BiomeDefaultFeatures.addForestFlowers(builder1);
        } else {
            builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.PALE_MOSS_PATCH);
            builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.PALE_GARDEN_FLOWERS);
        }

        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        if (!isPaleGarden) {
            BiomeDefaultFeatures.addDefaultFlowers(builder1);
        } else {
            builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.FLOWER_PALE_GARDEN);
        }

        BiomeDefaultFeatures.addForestGrass(builder1);
        if (!isPaleGarden) {
            BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        }

        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1);
        return new Biome.BiomeBuilder()
            .hasPrecipitation(true)
            .temperature(0.7F)
            .downfall(0.8F)
            .specialEffects(
                isPaleGarden
                    ? new BiomeSpecialEffects.Builder()
                        .waterColor(7768221)
                        .waterFogColor(5597568)
                        .fogColor(8484720)
                        .skyColor(12171705)
                        .grassColorOverride(7832178)
                        .foliageColorOverride(8883574)
                        .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
                        .silenceAllBackgroundMusic()
                        .build()
                    : new BiomeSpecialEffects.Builder()
                        .waterColor(4159204)
                        .waterFogColor(329011)
                        .fogColor(12638463)
                        .skyColor(calculateSkyColor(0.7F))
                        .grassColorModifier(BiomeSpecialEffects.GrassColorModifier.DARK_FOREST)
                        .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
                        .backgroundMusic(Musics.createGameMusic(SoundEvents.MUSIC_BIOME_FOREST))
                        .build()
            )
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    public static Biome swamp(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.farmAnimals(builder);
        BiomeDefaultFeatures.commonSpawns(builder, 70);
        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.SLIME, 1, 1, 1));
        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.BOGGED, 30, 4, 4));
        builder.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.FROG, 10, 2, 5));
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        BiomeDefaultFeatures.addFossilDecoration(builder1);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addSwampClayDisk(builder1);
        BiomeDefaultFeatures.addSwampVegetation(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addSwampExtraVegetation(builder1);
        builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, AquaticPlacements.SEAGRASS_SWAMP);
        Music music = Musics.createGameMusic(SoundEvents.MUSIC_BIOME_SWAMP);
        return new Biome.BiomeBuilder()
            .hasPrecipitation(true)
            .temperature(0.8F)
            .downfall(0.9F)
            .specialEffects(
                new BiomeSpecialEffects.Builder()
                    .waterColor(6388580)
                    .waterFogColor(2302743)
                    .fogColor(12638463)
                    .skyColor(calculateSkyColor(0.8F))
                    .foliageColorOverride(6975545)
                    .grassColorModifier(BiomeSpecialEffects.GrassColorModifier.SWAMP)
                    .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
                    .backgroundMusic(music)
                    .build()
            )
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    public static Biome mangroveSwamp(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.commonSpawns(builder, 70);
        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.SLIME, 1, 1, 1));
        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.BOGGED, 30, 4, 4));
        builder.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.FROG, 10, 2, 5));
        builder.addSpawn(MobCategory.WATER_AMBIENT, new MobSpawnSettings.SpawnerData(EntityType.TROPICAL_FISH, 25, 8, 8));
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        BiomeDefaultFeatures.addFossilDecoration(builder1);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addMangroveSwampDisks(builder1);
        BiomeDefaultFeatures.addMangroveSwampVegetation(builder1);
        builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, AquaticPlacements.SEAGRASS_SWAMP);
        Music music = Musics.createGameMusic(SoundEvents.MUSIC_BIOME_SWAMP);
        return new Biome.BiomeBuilder()
            .hasPrecipitation(true)
            .temperature(0.8F)
            .downfall(0.9F)
            .specialEffects(
                new BiomeSpecialEffects.Builder()
                    .waterColor(3832426)
                    .waterFogColor(5077600)
                    .fogColor(12638463)
                    .skyColor(calculateSkyColor(0.8F))
                    .foliageColorOverride(9285927)
                    .grassColorModifier(BiomeSpecialEffects.GrassColorModifier.SWAMP)
                    .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
                    .backgroundMusic(music)
                    .build()
            )
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    public static Biome river(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isCold) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder()
            .addSpawn(MobCategory.WATER_CREATURE, new MobSpawnSettings.SpawnerData(EntityType.SQUID, 2, 1, 4))
            .addSpawn(MobCategory.WATER_AMBIENT, new MobSpawnSettings.SpawnerData(EntityType.SALMON, 5, 1, 5));
        BiomeDefaultFeatures.commonSpawns(builder);
        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.DROWNED, isCold ? 1 : 100, 1, 1));
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        BiomeDefaultFeatures.addWaterTrees(builder1);
        BiomeDefaultFeatures.addDefaultFlowers(builder1);
        BiomeDefaultFeatures.addDefaultGrass(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1);
        if (!isCold) {
            builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, AquaticPlacements.SEAGRASS_RIVER);
        }

        float f = isCold ? 0.0F : 0.5F;
        return biome(true, f, 0.5F, isCold ? 3750089 : 4159204, 329011, null, null, builder, builder1, NORMAL_MUSIC);
    }

    public static Biome beach(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isCold, boolean isStony) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        boolean flag = !isStony && !isCold;
        if (flag) {
            builder.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.TURTLE, 5, 2, 5));
        }

        BiomeDefaultFeatures.commonSpawns(builder);
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        BiomeDefaultFeatures.addDefaultFlowers(builder1);
        BiomeDefaultFeatures.addDefaultGrass(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1);
        float f;
        if (isCold) {
            f = 0.05F;
        } else if (isStony) {
            f = 0.2F;
        } else {
            f = 0.8F;
        }

        return biome(true, f, flag ? 0.4F : 0.3F, isCold ? 4020182 : 4159204, 329011, null, null, builder, builder1, NORMAL_MUSIC);
    }

    public static Biome theVoid(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        builder.addFeature(GenerationStep.Decoration.TOP_LAYER_MODIFICATION, MiscOverworldPlacements.VOID_START_PLATFORM);
        return biome(false, 0.5F, 0.5F, new MobSpawnSettings.Builder(), builder, NORMAL_MUSIC);
    }

    public static Biome meadowOrCherryGrove(
        HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isCherryGrove
    ) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        MobSpawnSettings.Builder builder1 = new MobSpawnSettings.Builder();
        builder1.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(isCherryGrove ? EntityType.PIG : EntityType.DONKEY, 1, 1, 2))
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.RABBIT, 2, 2, 6))
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.SHEEP, 2, 2, 4));
        BiomeDefaultFeatures.commonSpawns(builder1);
        globalOverworldGeneration(builder);
        BiomeDefaultFeatures.addPlainGrass(builder);
        BiomeDefaultFeatures.addDefaultOres(builder);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder);
        if (isCherryGrove) {
            BiomeDefaultFeatures.addCherryGroveVegetation(builder);
        } else {
            BiomeDefaultFeatures.addMeadowVegetation(builder);
        }

        BiomeDefaultFeatures.addExtraEmeralds(builder);
        BiomeDefaultFeatures.addInfestedStone(builder);
        Music music = Musics.createGameMusic(isCherryGrove ? SoundEvents.MUSIC_BIOME_CHERRY_GROVE : SoundEvents.MUSIC_BIOME_MEADOW);
        return isCherryGrove
            ? biome(true, 0.5F, 0.8F, 6141935, 6141935, 11983713, 11983713, builder1, builder, music)
            : biome(true, 0.5F, 0.8F, 937679, 329011, null, null, builder1, builder, music);
    }

    public static Biome frozenPeaks(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        MobSpawnSettings.Builder builder1 = new MobSpawnSettings.Builder();
        builder1.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.GOAT, 5, 1, 3));
        BiomeDefaultFeatures.commonSpawns(builder1);
        globalOverworldGeneration(builder);
        BiomeDefaultFeatures.addFrozenSprings(builder);
        BiomeDefaultFeatures.addDefaultOres(builder);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder);
        BiomeDefaultFeatures.addExtraEmeralds(builder);
        BiomeDefaultFeatures.addInfestedStone(builder);
        Music music = Musics.createGameMusic(SoundEvents.MUSIC_BIOME_FROZEN_PEAKS);
        return biome(true, -0.7F, 0.9F, builder1, builder, music);
    }

    public static Biome jaggedPeaks(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        MobSpawnSettings.Builder builder1 = new MobSpawnSettings.Builder();
        builder1.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.GOAT, 5, 1, 3));
        BiomeDefaultFeatures.commonSpawns(builder1);
        globalOverworldGeneration(builder);
        BiomeDefaultFeatures.addFrozenSprings(builder);
        BiomeDefaultFeatures.addDefaultOres(builder);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder);
        BiomeDefaultFeatures.addExtraEmeralds(builder);
        BiomeDefaultFeatures.addInfestedStone(builder);
        Music music = Musics.createGameMusic(SoundEvents.MUSIC_BIOME_JAGGED_PEAKS);
        return biome(true, -0.7F, 0.9F, builder1, builder, music);
    }

    public static Biome stonyPeaks(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        MobSpawnSettings.Builder builder1 = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.commonSpawns(builder1);
        globalOverworldGeneration(builder);
        BiomeDefaultFeatures.addDefaultOres(builder);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder);
        BiomeDefaultFeatures.addExtraEmeralds(builder);
        BiomeDefaultFeatures.addInfestedStone(builder);
        Music music = Musics.createGameMusic(SoundEvents.MUSIC_BIOME_STONY_PEAKS);
        return biome(true, 1.0F, 0.3F, builder1, builder, music);
    }

    public static Biome snowySlopes(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        MobSpawnSettings.Builder builder1 = new MobSpawnSettings.Builder();
        builder1.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.RABBIT, 4, 2, 3))
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.GOAT, 5, 1, 3));
        BiomeDefaultFeatures.commonSpawns(builder1);
        globalOverworldGeneration(builder);
        BiomeDefaultFeatures.addFrozenSprings(builder);
        BiomeDefaultFeatures.addDefaultOres(builder);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder);
        BiomeDefaultFeatures.addExtraEmeralds(builder);
        BiomeDefaultFeatures.addInfestedStone(builder);
        Music music = Musics.createGameMusic(SoundEvents.MUSIC_BIOME_SNOWY_SLOPES);
        return biome(true, -0.3F, 0.9F, builder1, builder, music);
    }

    public static Biome grove(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        MobSpawnSettings.Builder builder1 = new MobSpawnSettings.Builder();
        builder1.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 1, 1, 1))
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.RABBIT, 8, 2, 3))
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.FOX, 4, 2, 4));
        BiomeDefaultFeatures.commonSpawns(builder1);
        globalOverworldGeneration(builder);
        BiomeDefaultFeatures.addFrozenSprings(builder);
        BiomeDefaultFeatures.addDefaultOres(builder);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder);
        BiomeDefaultFeatures.addGroveTrees(builder);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder);
        BiomeDefaultFeatures.addExtraEmeralds(builder);
        BiomeDefaultFeatures.addInfestedStone(builder);
        Music music = Musics.createGameMusic(SoundEvents.MUSIC_BIOME_GROVE);
        return biome(true, -0.2F, 0.8F, builder1, builder, music);
    }

    public static Biome lushCaves(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        builder.addSpawn(MobCategory.AXOLOTLS, new MobSpawnSettings.SpawnerData(EntityType.AXOLOTL, 10, 4, 6));
        builder.addSpawn(MobCategory.WATER_AMBIENT, new MobSpawnSettings.SpawnerData(EntityType.TROPICAL_FISH, 25, 8, 8));
        BiomeDefaultFeatures.commonSpawns(builder);
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addPlainGrass(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addLushCavesSpecialOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        BiomeDefaultFeatures.addLushCavesVegetationFeatures(builder1);
        Music music = Musics.createGameMusic(SoundEvents.MUSIC_BIOME_LUSH_CAVES);
        return biome(true, 0.5F, 0.5F, builder, builder1, music);
    }

    public static Biome dripstoneCaves(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.dripstoneCavesSpawns(builder);
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addPlainGrass(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1, true);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        BiomeDefaultFeatures.addPlainVegetation(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1);
        BiomeDefaultFeatures.addDripstone(builder1);
        Music music = Musics.createGameMusic(SoundEvents.MUSIC_BIOME_DRIPSTONE_CAVES);
        return biome(true, 0.8F, 0.4F, builder, builder1, music);
    }

    public static Biome deepDark(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        builder1.addCarver(Carvers.CAVE);
        builder1.addCarver(Carvers.CAVE_EXTRA_UNDERGROUND);
        builder1.addCarver(Carvers.CANYON);
        BiomeDefaultFeatures.addDefaultCrystalFormations(builder1);
        BiomeDefaultFeatures.addDefaultMonsterRoom(builder1);
        BiomeDefaultFeatures.addDefaultUndergroundVariety(builder1);
        BiomeDefaultFeatures.addSurfaceFreezing(builder1);
        BiomeDefaultFeatures.addPlainGrass(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        BiomeDefaultFeatures.addPlainVegetation(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1);
        BiomeDefaultFeatures.addSculk(builder1);
        Music music = Musics.createGameMusic(SoundEvents.MUSIC_BIOME_DEEP_DARK);
        return biome(true, 0.8F, 0.4F, builder, builder1, music);
    }
}
