package net.minecraft.data.worldgen.features;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.data.worldgen.placement.TreePlacements;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.valueproviders.BiasedToBottomInt;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.WeightedPlacedFeature;
import net.minecraft.world.level.levelgen.feature.configurations.BlockColumnConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.ProbabilityFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomBooleanFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleRandomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.VegetationPatchConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.DualNoiseProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseThresholdProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.WeightedStateProvider;
import net.minecraft.world.level.levelgen.placement.BlockPredicateFilter;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.material.Fluids;

public class VegetationFeatures {
    public static final ResourceKey<ConfiguredFeature<?, ?>> BAMBOO_NO_PODZOL = FeatureUtils.createKey("bamboo_no_podzol");
    public static final ResourceKey<ConfiguredFeature<?, ?>> BAMBOO_SOME_PODZOL = FeatureUtils.createKey("bamboo_some_podzol");
    public static final ResourceKey<ConfiguredFeature<?, ?>> VINES = FeatureUtils.createKey("vines");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PATCH_BROWN_MUSHROOM = FeatureUtils.createKey("patch_brown_mushroom");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PATCH_RED_MUSHROOM = FeatureUtils.createKey("patch_red_mushroom");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PATCH_SUNFLOWER = FeatureUtils.createKey("patch_sunflower");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PATCH_PUMPKIN = FeatureUtils.createKey("patch_pumpkin");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PATCH_BERRY_BUSH = FeatureUtils.createKey("patch_berry_bush");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PATCH_TAIGA_GRASS = FeatureUtils.createKey("patch_taiga_grass");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PATCH_GRASS = FeatureUtils.createKey("patch_grass");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PATCH_GRASS_JUNGLE = FeatureUtils.createKey("patch_grass_jungle");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SINGLE_PIECE_OF_GRASS = FeatureUtils.createKey("single_piece_of_grass");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PATCH_DEAD_BUSH = FeatureUtils.createKey("patch_dead_bush");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PATCH_MELON = FeatureUtils.createKey("patch_melon");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PATCH_WATERLILY = FeatureUtils.createKey("patch_waterlily");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PATCH_TALL_GRASS = FeatureUtils.createKey("patch_tall_grass");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PATCH_LARGE_FERN = FeatureUtils.createKey("patch_large_fern");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PATCH_CACTUS = FeatureUtils.createKey("patch_cactus");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PATCH_SUGAR_CANE = FeatureUtils.createKey("patch_sugar_cane");
    public static final ResourceKey<ConfiguredFeature<?, ?>> FLOWER_DEFAULT = FeatureUtils.createKey("flower_default");
    public static final ResourceKey<ConfiguredFeature<?, ?>> FLOWER_FLOWER_FOREST = FeatureUtils.createKey("flower_flower_forest");
    public static final ResourceKey<ConfiguredFeature<?, ?>> FLOWER_SWAMP = FeatureUtils.createKey("flower_swamp");
    public static final ResourceKey<ConfiguredFeature<?, ?>> FLOWER_PLAIN = FeatureUtils.createKey("flower_plain");
    public static final ResourceKey<ConfiguredFeature<?, ?>> FLOWER_MEADOW = FeatureUtils.createKey("flower_meadow");
    public static final ResourceKey<ConfiguredFeature<?, ?>> FLOWER_CHERRY = FeatureUtils.createKey("flower_cherry");
    public static final ResourceKey<ConfiguredFeature<?, ?>> FLOWER_PALE_GARDEN = FeatureUtils.createKey("flower_pale_garden");
    public static final ResourceKey<ConfiguredFeature<?, ?>> FOREST_FLOWERS = FeatureUtils.createKey("forest_flowers");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PALE_FOREST_FLOWERS = FeatureUtils.createKey("pale_forest_flowers");
    public static final ResourceKey<ConfiguredFeature<?, ?>> DARK_FOREST_VEGETATION = FeatureUtils.createKey("dark_forest_vegetation");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PALE_GARDEN_VEGETATION = FeatureUtils.createKey("pale_garden_vegetation");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PALE_MOSS_VEGETATION = FeatureUtils.createKey("pale_moss_vegetation");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PALE_MOSS_PATCH = FeatureUtils.createKey("pale_moss_patch");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PALE_MOSS_PATCH_BONEMEAL = FeatureUtils.createKey("pale_moss_patch_bonemeal");
    public static final ResourceKey<ConfiguredFeature<?, ?>> TREES_FLOWER_FOREST = FeatureUtils.createKey("trees_flower_forest");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MEADOW_TREES = FeatureUtils.createKey("meadow_trees");
    public static final ResourceKey<ConfiguredFeature<?, ?>> TREES_TAIGA = FeatureUtils.createKey("trees_taiga");
    public static final ResourceKey<ConfiguredFeature<?, ?>> TREES_GROVE = FeatureUtils.createKey("trees_grove");
    public static final ResourceKey<ConfiguredFeature<?, ?>> TREES_SAVANNA = FeatureUtils.createKey("trees_savanna");
    public static final ResourceKey<ConfiguredFeature<?, ?>> BIRCH_TALL = FeatureUtils.createKey("birch_tall");
    public static final ResourceKey<ConfiguredFeature<?, ?>> TREES_WINDSWEPT_HILLS = FeatureUtils.createKey("trees_windswept_hills");
    public static final ResourceKey<ConfiguredFeature<?, ?>> TREES_WATER = FeatureUtils.createKey("trees_water");
    public static final ResourceKey<ConfiguredFeature<?, ?>> TREES_BIRCH_AND_OAK = FeatureUtils.createKey("trees_birch_and_oak");
    public static final ResourceKey<ConfiguredFeature<?, ?>> TREES_PLAINS = FeatureUtils.createKey("trees_plains");
    public static final ResourceKey<ConfiguredFeature<?, ?>> TREES_SPARSE_JUNGLE = FeatureUtils.createKey("trees_sparse_jungle");
    public static final ResourceKey<ConfiguredFeature<?, ?>> TREES_OLD_GROWTH_SPRUCE_TAIGA = FeatureUtils.createKey("trees_old_growth_spruce_taiga");
    public static final ResourceKey<ConfiguredFeature<?, ?>> TREES_OLD_GROWTH_PINE_TAIGA = FeatureUtils.createKey("trees_old_growth_pine_taiga");
    public static final ResourceKey<ConfiguredFeature<?, ?>> TREES_JUNGLE = FeatureUtils.createKey("trees_jungle");
    public static final ResourceKey<ConfiguredFeature<?, ?>> BAMBOO_VEGETATION = FeatureUtils.createKey("bamboo_vegetation");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MUSHROOM_ISLAND_VEGETATION = FeatureUtils.createKey("mushroom_island_vegetation");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MANGROVE_VEGETATION = FeatureUtils.createKey("mangrove_vegetation");

    private static RandomPatchConfiguration grassPatch(BlockStateProvider stateProvider, int tries) {
        return FeatureUtils.simpleRandomPatchConfiguration(
            tries, PlacementUtils.onlyWhenEmpty(Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(stateProvider))
        );
    }

    public static void bootstrap(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        HolderGetter<ConfiguredFeature<?, ?>> holderGetter = context.lookup(Registries.CONFIGURED_FEATURE);
        Holder<ConfiguredFeature<?, ?>> orThrow = holderGetter.getOrThrow(TreeFeatures.HUGE_BROWN_MUSHROOM);
        Holder<ConfiguredFeature<?, ?>> orThrow1 = holderGetter.getOrThrow(TreeFeatures.HUGE_RED_MUSHROOM);
        Holder<ConfiguredFeature<?, ?>> orThrow2 = holderGetter.getOrThrow(TreeFeatures.FANCY_OAK_BEES_005);
        Holder<ConfiguredFeature<?, ?>> orThrow3 = holderGetter.getOrThrow(TreeFeatures.OAK_BEES_005);
        Holder<ConfiguredFeature<?, ?>> orThrow4 = holderGetter.getOrThrow(PATCH_GRASS_JUNGLE);
        HolderGetter<PlacedFeature> holderGetter1 = context.lookup(Registries.PLACED_FEATURE);
        Holder<PlacedFeature> orThrow5 = holderGetter1.getOrThrow(TreePlacements.DARK_OAK_CHECKED);
        Holder<PlacedFeature> orThrow6 = holderGetter1.getOrThrow(TreePlacements.PALE_OAK_CHECKED);
        Holder<PlacedFeature> orThrow7 = holderGetter1.getOrThrow(TreePlacements.PALE_OAK_CREAKING_CHECKED);
        Holder<PlacedFeature> orThrow8 = holderGetter1.getOrThrow(TreePlacements.BIRCH_CHECKED);
        Holder<PlacedFeature> orThrow9 = holderGetter1.getOrThrow(TreePlacements.FANCY_OAK_CHECKED);
        Holder<PlacedFeature> orThrow10 = holderGetter1.getOrThrow(TreePlacements.BIRCH_BEES_002);
        Holder<PlacedFeature> orThrow11 = holderGetter1.getOrThrow(TreePlacements.FANCY_OAK_BEES_002);
        Holder<PlacedFeature> orThrow12 = holderGetter1.getOrThrow(TreePlacements.FANCY_OAK_BEES);
        Holder<PlacedFeature> orThrow13 = holderGetter1.getOrThrow(TreePlacements.PINE_CHECKED);
        Holder<PlacedFeature> orThrow14 = holderGetter1.getOrThrow(TreePlacements.SPRUCE_CHECKED);
        Holder<PlacedFeature> orThrow15 = holderGetter1.getOrThrow(TreePlacements.PINE_ON_SNOW);
        Holder<PlacedFeature> orThrow16 = holderGetter1.getOrThrow(TreePlacements.ACACIA_CHECKED);
        Holder<PlacedFeature> orThrow17 = holderGetter1.getOrThrow(TreePlacements.SUPER_BIRCH_BEES_0002);
        Holder<PlacedFeature> orThrow18 = holderGetter1.getOrThrow(TreePlacements.BIRCH_BEES_0002_PLACED);
        Holder<PlacedFeature> orThrow19 = holderGetter1.getOrThrow(TreePlacements.FANCY_OAK_BEES_0002);
        Holder<PlacedFeature> orThrow20 = holderGetter1.getOrThrow(TreePlacements.JUNGLE_BUSH);
        Holder<PlacedFeature> orThrow21 = holderGetter1.getOrThrow(TreePlacements.MEGA_SPRUCE_CHECKED);
        Holder<PlacedFeature> orThrow22 = holderGetter1.getOrThrow(TreePlacements.MEGA_PINE_CHECKED);
        Holder<PlacedFeature> orThrow23 = holderGetter1.getOrThrow(TreePlacements.MEGA_JUNGLE_TREE_CHECKED);
        Holder<PlacedFeature> orThrow24 = holderGetter1.getOrThrow(TreePlacements.TALL_MANGROVE_CHECKED);
        Holder<PlacedFeature> orThrow25 = holderGetter1.getOrThrow(TreePlacements.OAK_CHECKED);
        Holder<PlacedFeature> orThrow26 = holderGetter1.getOrThrow(TreePlacements.OAK_BEES_002);
        Holder<PlacedFeature> orThrow27 = holderGetter1.getOrThrow(TreePlacements.SUPER_BIRCH_BEES);
        Holder<PlacedFeature> orThrow28 = holderGetter1.getOrThrow(TreePlacements.SPRUCE_ON_SNOW);
        Holder<PlacedFeature> orThrow29 = holderGetter1.getOrThrow(TreePlacements.OAK_BEES_0002);
        Holder<PlacedFeature> orThrow30 = holderGetter1.getOrThrow(TreePlacements.JUNGLE_TREE_CHECKED);
        Holder<PlacedFeature> orThrow31 = holderGetter1.getOrThrow(TreePlacements.MANGROVE_CHECKED);
        FeatureUtils.register(context, BAMBOO_NO_PODZOL, Feature.BAMBOO, new ProbabilityFeatureConfiguration(0.0F));
        FeatureUtils.register(context, BAMBOO_SOME_PODZOL, Feature.BAMBOO, new ProbabilityFeatureConfiguration(0.2F));
        FeatureUtils.register(context, VINES, Feature.VINES);
        FeatureUtils.register(
            context,
            PATCH_BROWN_MUSHROOM,
            Feature.RANDOM_PATCH,
            FeatureUtils.simplePatchConfiguration(Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(BlockStateProvider.simple(Blocks.BROWN_MUSHROOM)))
        );
        FeatureUtils.register(
            context,
            PATCH_RED_MUSHROOM,
            Feature.RANDOM_PATCH,
            FeatureUtils.simplePatchConfiguration(Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(BlockStateProvider.simple(Blocks.RED_MUSHROOM)))
        );
        FeatureUtils.register(
            context,
            PATCH_SUNFLOWER,
            Feature.RANDOM_PATCH,
            FeatureUtils.simplePatchConfiguration(Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(BlockStateProvider.simple(Blocks.SUNFLOWER)))
        );
        FeatureUtils.register(
            context,
            PATCH_PUMPKIN,
            Feature.RANDOM_PATCH,
            FeatureUtils.simplePatchConfiguration(
                Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(BlockStateProvider.simple(Blocks.PUMPKIN)), List.of(Blocks.GRASS_BLOCK)
            )
        );
        FeatureUtils.register(
            context,
            PATCH_BERRY_BUSH,
            Feature.RANDOM_PATCH,
            FeatureUtils.simplePatchConfiguration(
                Feature.SIMPLE_BLOCK,
                new SimpleBlockConfiguration(
                    BlockStateProvider.simple(Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, Integer.valueOf(3)))
                ),
                List.of(Blocks.GRASS_BLOCK)
            )
        );
        FeatureUtils.register(
            context,
            PATCH_TAIGA_GRASS,
            Feature.RANDOM_PATCH,
            grassPatch(
                new WeightedStateProvider(
                    SimpleWeightedRandomList.<BlockState>builder().add(Blocks.SHORT_GRASS.defaultBlockState(), 1).add(Blocks.FERN.defaultBlockState(), 4)
                ),
                32
            )
        );
        FeatureUtils.register(context, PATCH_GRASS, Feature.RANDOM_PATCH, grassPatch(BlockStateProvider.simple(Blocks.SHORT_GRASS), 32));
        FeatureUtils.register(
            context,
            PATCH_GRASS_JUNGLE,
            Feature.RANDOM_PATCH,
            new RandomPatchConfiguration(
                32,
                7,
                3,
                PlacementUtils.filtered(
                    Feature.SIMPLE_BLOCK,
                    new SimpleBlockConfiguration(
                        new WeightedStateProvider(
                            SimpleWeightedRandomList.<BlockState>builder()
                                .add(Blocks.SHORT_GRASS.defaultBlockState(), 3)
                                .add(Blocks.FERN.defaultBlockState(), 1)
                        )
                    ),
                    BlockPredicate.allOf(
                        BlockPredicate.ONLY_IN_AIR_PREDICATE, BlockPredicate.not(BlockPredicate.matchesBlocks(Direction.DOWN.getUnitVec3i(), Blocks.PODZOL))
                    )
                )
            )
        );
        FeatureUtils.register(
            context,
            SINGLE_PIECE_OF_GRASS,
            Feature.SIMPLE_BLOCK,
            new SimpleBlockConfiguration(BlockStateProvider.simple(Blocks.SHORT_GRASS.defaultBlockState()))
        );
        FeatureUtils.register(context, PATCH_DEAD_BUSH, Feature.RANDOM_PATCH, grassPatch(BlockStateProvider.simple(Blocks.DEAD_BUSH), 4));
        FeatureUtils.register(
            context,
            PATCH_MELON,
            Feature.RANDOM_PATCH,
            new RandomPatchConfiguration(
                64,
                7,
                3,
                PlacementUtils.filtered(
                    Feature.SIMPLE_BLOCK,
                    new SimpleBlockConfiguration(BlockStateProvider.simple(Blocks.MELON)),
                    BlockPredicate.allOf(
                        BlockPredicate.replaceable(), BlockPredicate.noFluid(), BlockPredicate.matchesBlocks(Direction.DOWN.getUnitVec3i(), Blocks.GRASS_BLOCK)
                    )
                )
            )
        );
        FeatureUtils.register(
            context,
            PATCH_WATERLILY,
            Feature.RANDOM_PATCH,
            new RandomPatchConfiguration(
                10, 7, 3, PlacementUtils.onlyWhenEmpty(Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(BlockStateProvider.simple(Blocks.LILY_PAD)))
            )
        );
        FeatureUtils.register(
            context,
            PATCH_TALL_GRASS,
            Feature.RANDOM_PATCH,
            FeatureUtils.simplePatchConfiguration(Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(BlockStateProvider.simple(Blocks.TALL_GRASS)))
        );
        FeatureUtils.register(
            context,
            PATCH_LARGE_FERN,
            Feature.RANDOM_PATCH,
            FeatureUtils.simplePatchConfiguration(Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(BlockStateProvider.simple(Blocks.LARGE_FERN)))
        );
        FeatureUtils.register(
            context,
            PATCH_CACTUS,
            Feature.RANDOM_PATCH,
            FeatureUtils.simpleRandomPatchConfiguration(
                10,
                PlacementUtils.inlinePlaced(
                    Feature.BLOCK_COLUMN,
                    BlockColumnConfiguration.simple(BiasedToBottomInt.of(1, 3), BlockStateProvider.simple(Blocks.CACTUS)),
                    BlockPredicateFilter.forPredicate(
                        BlockPredicate.allOf(
                            BlockPredicate.ONLY_IN_AIR_PREDICATE, BlockPredicate.wouldSurvive(Blocks.CACTUS.defaultBlockState(), BlockPos.ZERO)
                        )
                    )
                )
            )
        );
        FeatureUtils.register(
            context,
            PATCH_SUGAR_CANE,
            Feature.RANDOM_PATCH,
            new RandomPatchConfiguration(
                20,
                4,
                0,
                PlacementUtils.inlinePlaced(
                    Feature.BLOCK_COLUMN,
                    BlockColumnConfiguration.simple(BiasedToBottomInt.of(2, 4), BlockStateProvider.simple(Blocks.SUGAR_CANE)),
                    BlockPredicateFilter.forPredicate(
                        BlockPredicate.allOf(
                            BlockPredicate.ONLY_IN_AIR_PREDICATE,
                            BlockPredicate.wouldSurvive(Blocks.SUGAR_CANE.defaultBlockState(), BlockPos.ZERO),
                            BlockPredicate.anyOf(
                                BlockPredicate.matchesFluids(new BlockPos(1, -1, 0), Fluids.WATER, Fluids.FLOWING_WATER),
                                BlockPredicate.matchesFluids(new BlockPos(-1, -1, 0), Fluids.WATER, Fluids.FLOWING_WATER),
                                BlockPredicate.matchesFluids(new BlockPos(0, -1, 1), Fluids.WATER, Fluids.FLOWING_WATER),
                                BlockPredicate.matchesFluids(new BlockPos(0, -1, -1), Fluids.WATER, Fluids.FLOWING_WATER)
                            )
                        )
                    )
                )
            )
        );
        FeatureUtils.register(
            context,
            FLOWER_DEFAULT,
            Feature.FLOWER,
            grassPatch(
                new WeightedStateProvider(
                    SimpleWeightedRandomList.<BlockState>builder().add(Blocks.POPPY.defaultBlockState(), 2).add(Blocks.DANDELION.defaultBlockState(), 1)
                ),
                64
            )
        );
        FeatureUtils.register(
            context,
            FLOWER_FLOWER_FOREST,
            Feature.FLOWER,
            new RandomPatchConfiguration(
                96,
                6,
                2,
                PlacementUtils.onlyWhenEmpty(
                    Feature.SIMPLE_BLOCK,
                    new SimpleBlockConfiguration(
                        new NoiseProvider(
                            2345L,
                            new NormalNoise.NoiseParameters(0, 1.0),
                            0.020833334F,
                            List.of(
                                Blocks.DANDELION.defaultBlockState(),
                                Blocks.POPPY.defaultBlockState(),
                                Blocks.ALLIUM.defaultBlockState(),
                                Blocks.AZURE_BLUET.defaultBlockState(),
                                Blocks.RED_TULIP.defaultBlockState(),
                                Blocks.ORANGE_TULIP.defaultBlockState(),
                                Blocks.WHITE_TULIP.defaultBlockState(),
                                Blocks.PINK_TULIP.defaultBlockState(),
                                Blocks.OXEYE_DAISY.defaultBlockState(),
                                Blocks.CORNFLOWER.defaultBlockState(),
                                Blocks.LILY_OF_THE_VALLEY.defaultBlockState()
                            )
                        )
                    )
                )
            )
        );
        FeatureUtils.register(
            context,
            FLOWER_SWAMP,
            Feature.FLOWER,
            new RandomPatchConfiguration(
                64, 6, 2, PlacementUtils.onlyWhenEmpty(Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(BlockStateProvider.simple(Blocks.BLUE_ORCHID)))
            )
        );
        FeatureUtils.register(
            context,
            FLOWER_PLAIN,
            Feature.FLOWER,
            new RandomPatchConfiguration(
                64,
                6,
                2,
                PlacementUtils.onlyWhenEmpty(
                    Feature.SIMPLE_BLOCK,
                    new SimpleBlockConfiguration(
                        new NoiseThresholdProvider(
                            2345L,
                            new NormalNoise.NoiseParameters(0, 1.0),
                            0.005F,
                            -0.8F,
                            0.33333334F,
                            Blocks.DANDELION.defaultBlockState(),
                            List.of(
                                Blocks.ORANGE_TULIP.defaultBlockState(),
                                Blocks.RED_TULIP.defaultBlockState(),
                                Blocks.PINK_TULIP.defaultBlockState(),
                                Blocks.WHITE_TULIP.defaultBlockState()
                            ),
                            List.of(
                                Blocks.POPPY.defaultBlockState(),
                                Blocks.AZURE_BLUET.defaultBlockState(),
                                Blocks.OXEYE_DAISY.defaultBlockState(),
                                Blocks.CORNFLOWER.defaultBlockState()
                            )
                        )
                    )
                )
            )
        );
        FeatureUtils.register(
            context,
            FLOWER_MEADOW,
            Feature.FLOWER,
            new RandomPatchConfiguration(
                96,
                6,
                2,
                PlacementUtils.onlyWhenEmpty(
                    Feature.SIMPLE_BLOCK,
                    new SimpleBlockConfiguration(
                        new DualNoiseProvider(
                            new InclusiveRange<>(1, 3),
                            new NormalNoise.NoiseParameters(-10, 1.0),
                            1.0F,
                            2345L,
                            new NormalNoise.NoiseParameters(-3, 1.0),
                            1.0F,
                            List.of(
                                Blocks.TALL_GRASS.defaultBlockState(),
                                Blocks.ALLIUM.defaultBlockState(),
                                Blocks.POPPY.defaultBlockState(),
                                Blocks.AZURE_BLUET.defaultBlockState(),
                                Blocks.DANDELION.defaultBlockState(),
                                Blocks.CORNFLOWER.defaultBlockState(),
                                Blocks.OXEYE_DAISY.defaultBlockState(),
                                Blocks.SHORT_GRASS.defaultBlockState()
                            )
                        )
                    )
                )
            )
        );
        SimpleWeightedRandomList.Builder<BlockState> builder = SimpleWeightedRandomList.builder();

        for (int i = 1; i <= 4; i++) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                builder.add(
                    Blocks.PINK_PETALS.defaultBlockState().setValue(PinkPetalsBlock.AMOUNT, Integer.valueOf(i)).setValue(PinkPetalsBlock.FACING, direction), 1
                );
            }
        }

        FeatureUtils.register(
            context,
            FLOWER_CHERRY,
            Feature.FLOWER,
            new RandomPatchConfiguration(
                96, 6, 2, PlacementUtils.onlyWhenEmpty(Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(new WeightedStateProvider(builder)))
            )
        );
        FeatureUtils.register(
            context,
            FLOWER_PALE_GARDEN,
            Feature.FLOWER,
            new RandomPatchConfiguration(
                1,
                0,
                0,
                PlacementUtils.onlyWhenEmpty(Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(BlockStateProvider.simple(Blocks.CLOSED_EYEBLOSSOM), true))
            )
        );
        FeatureUtils.register(
            context,
            FOREST_FLOWERS,
            Feature.SIMPLE_RANDOM_SELECTOR,
            new SimpleRandomFeatureConfiguration(
                HolderSet.direct(
                    PlacementUtils.inlinePlaced(
                        Feature.RANDOM_PATCH,
                        FeatureUtils.simplePatchConfiguration(Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(BlockStateProvider.simple(Blocks.LILAC)))
                    ),
                    PlacementUtils.inlinePlaced(
                        Feature.RANDOM_PATCH,
                        FeatureUtils.simplePatchConfiguration(Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(BlockStateProvider.simple(Blocks.ROSE_BUSH)))
                    ),
                    PlacementUtils.inlinePlaced(
                        Feature.RANDOM_PATCH,
                        FeatureUtils.simplePatchConfiguration(Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(BlockStateProvider.simple(Blocks.PEONY)))
                    ),
                    PlacementUtils.inlinePlaced(
                        Feature.NO_BONEMEAL_FLOWER,
                        FeatureUtils.simplePatchConfiguration(
                            Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(BlockStateProvider.simple(Blocks.LILY_OF_THE_VALLEY))
                        )
                    )
                )
            )
        );
        FeatureUtils.register(
            context,
            PALE_FOREST_FLOWERS,
            Feature.RANDOM_PATCH,
            FeatureUtils.simplePatchConfiguration(Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(BlockStateProvider.simple(Blocks.CLOSED_EYEBLOSSOM), true))
        );
        FeatureUtils.register(
            context,
            DARK_FOREST_VEGETATION,
            Feature.RANDOM_SELECTOR,
            new RandomFeatureConfiguration(
                List.of(
                    new WeightedPlacedFeature(PlacementUtils.inlinePlaced(orThrow), 0.025F),
                    new WeightedPlacedFeature(PlacementUtils.inlinePlaced(orThrow1), 0.05F),
                    new WeightedPlacedFeature(orThrow5, 0.6666667F),
                    new WeightedPlacedFeature(orThrow8, 0.2F),
                    new WeightedPlacedFeature(orThrow9, 0.1F)
                ),
                orThrow25
            )
        );
        FeatureUtils.register(
            context,
            PALE_GARDEN_VEGETATION,
            Feature.RANDOM_SELECTOR,
            new RandomFeatureConfiguration(List.of(new WeightedPlacedFeature(orThrow7, 0.1F), new WeightedPlacedFeature(orThrow6, 0.9F)), orThrow6)
        );
        FeatureUtils.register(
            context,
            PALE_MOSS_VEGETATION,
            Feature.SIMPLE_BLOCK,
            new SimpleBlockConfiguration(
                new WeightedStateProvider(
                    SimpleWeightedRandomList.<BlockState>builder()
                        .add(Blocks.PALE_MOSS_CARPET.defaultBlockState(), 25)
                        .add(Blocks.SHORT_GRASS.defaultBlockState(), 25)
                        .add(Blocks.TALL_GRASS.defaultBlockState(), 10)
                )
            )
        );
        FeatureUtils.register(
            context,
            PALE_MOSS_PATCH,
            Feature.VEGETATION_PATCH,
            new VegetationPatchConfiguration(
                BlockTags.MOSS_REPLACEABLE,
                BlockStateProvider.simple(Blocks.PALE_MOSS_BLOCK),
                PlacementUtils.inlinePlaced(holderGetter.getOrThrow(PALE_MOSS_VEGETATION)),
                CaveSurface.FLOOR,
                ConstantInt.of(1),
                0.0F,
                5,
                0.3F,
                UniformInt.of(2, 4),
                0.75F
            )
        );
        FeatureUtils.register(
            context,
            PALE_MOSS_PATCH_BONEMEAL,
            Feature.VEGETATION_PATCH,
            new VegetationPatchConfiguration(
                BlockTags.MOSS_REPLACEABLE,
                BlockStateProvider.simple(Blocks.PALE_MOSS_BLOCK),
                PlacementUtils.inlinePlaced(holderGetter.getOrThrow(PALE_MOSS_VEGETATION)),
                CaveSurface.FLOOR,
                ConstantInt.of(1),
                0.0F,
                5,
                0.6F,
                UniformInt.of(1, 2),
                0.75F
            )
        );
        FeatureUtils.register(
            context,
            TREES_FLOWER_FOREST,
            Feature.RANDOM_SELECTOR,
            new RandomFeatureConfiguration(List.of(new WeightedPlacedFeature(orThrow10, 0.2F), new WeightedPlacedFeature(orThrow11, 0.1F)), orThrow26)
        );
        FeatureUtils.register(
            context, MEADOW_TREES, Feature.RANDOM_SELECTOR, new RandomFeatureConfiguration(List.of(new WeightedPlacedFeature(orThrow12, 0.5F)), orThrow27)
        );
        FeatureUtils.register(
            context,
            TREES_TAIGA,
            Feature.RANDOM_SELECTOR,
            new RandomFeatureConfiguration(List.of(new WeightedPlacedFeature(orThrow13, 0.33333334F)), orThrow14)
        );
        FeatureUtils.register(
            context,
            TREES_GROVE,
            Feature.RANDOM_SELECTOR,
            new RandomFeatureConfiguration(List.of(new WeightedPlacedFeature(orThrow15, 0.33333334F)), orThrow28)
        );
        FeatureUtils.register(
            context, TREES_SAVANNA, Feature.RANDOM_SELECTOR, new RandomFeatureConfiguration(List.of(new WeightedPlacedFeature(orThrow16, 0.8F)), orThrow25)
        );
        FeatureUtils.register(
            context, BIRCH_TALL, Feature.RANDOM_SELECTOR, new RandomFeatureConfiguration(List.of(new WeightedPlacedFeature(orThrow17, 0.5F)), orThrow18)
        );
        FeatureUtils.register(
            context,
            TREES_WINDSWEPT_HILLS,
            Feature.RANDOM_SELECTOR,
            new RandomFeatureConfiguration(List.of(new WeightedPlacedFeature(orThrow14, 0.666F), new WeightedPlacedFeature(orThrow9, 0.1F)), orThrow25)
        );
        FeatureUtils.register(
            context, TREES_WATER, Feature.RANDOM_SELECTOR, new RandomFeatureConfiguration(List.of(new WeightedPlacedFeature(orThrow9, 0.1F)), orThrow25)
        );
        FeatureUtils.register(
            context,
            TREES_BIRCH_AND_OAK,
            Feature.RANDOM_SELECTOR,
            new RandomFeatureConfiguration(List.of(new WeightedPlacedFeature(orThrow18, 0.2F), new WeightedPlacedFeature(orThrow19, 0.1F)), orThrow29)
        );
        FeatureUtils.register(
            context,
            TREES_PLAINS,
            Feature.RANDOM_SELECTOR,
            new RandomFeatureConfiguration(
                List.of(new WeightedPlacedFeature(PlacementUtils.inlinePlaced(orThrow2), 0.33333334F)), PlacementUtils.inlinePlaced(orThrow3)
            )
        );
        FeatureUtils.register(
            context,
            TREES_SPARSE_JUNGLE,
            Feature.RANDOM_SELECTOR,
            new RandomFeatureConfiguration(List.of(new WeightedPlacedFeature(orThrow9, 0.1F), new WeightedPlacedFeature(orThrow20, 0.5F)), orThrow30)
        );
        FeatureUtils.register(
            context,
            TREES_OLD_GROWTH_SPRUCE_TAIGA,
            Feature.RANDOM_SELECTOR,
            new RandomFeatureConfiguration(
                List.of(new WeightedPlacedFeature(orThrow21, 0.33333334F), new WeightedPlacedFeature(orThrow13, 0.33333334F)), orThrow14
            )
        );
        FeatureUtils.register(
            context,
            TREES_OLD_GROWTH_PINE_TAIGA,
            Feature.RANDOM_SELECTOR,
            new RandomFeatureConfiguration(
                List.of(
                    new WeightedPlacedFeature(orThrow21, 0.025641026F),
                    new WeightedPlacedFeature(orThrow22, 0.30769232F),
                    new WeightedPlacedFeature(orThrow13, 0.33333334F)
                ),
                orThrow14
            )
        );
        FeatureUtils.register(
            context,
            TREES_JUNGLE,
            Feature.RANDOM_SELECTOR,
            new RandomFeatureConfiguration(
                List.of(
                    new WeightedPlacedFeature(orThrow9, 0.1F), new WeightedPlacedFeature(orThrow20, 0.5F), new WeightedPlacedFeature(orThrow23, 0.33333334F)
                ),
                orThrow30
            )
        );
        FeatureUtils.register(
            context,
            BAMBOO_VEGETATION,
            Feature.RANDOM_SELECTOR,
            new RandomFeatureConfiguration(
                List.of(new WeightedPlacedFeature(orThrow9, 0.05F), new WeightedPlacedFeature(orThrow20, 0.15F), new WeightedPlacedFeature(orThrow23, 0.7F)),
                PlacementUtils.inlinePlaced(orThrow4)
            )
        );
        FeatureUtils.register(
            context,
            MUSHROOM_ISLAND_VEGETATION,
            Feature.RANDOM_BOOLEAN_SELECTOR,
            new RandomBooleanFeatureConfiguration(PlacementUtils.inlinePlaced(orThrow1), PlacementUtils.inlinePlaced(orThrow))
        );
        FeatureUtils.register(
            context,
            MANGROVE_VEGETATION,
            Feature.RANDOM_SELECTOR,
            new RandomFeatureConfiguration(List.of(new WeightedPlacedFeature(orThrow24, 0.85F)), orThrow31)
        );
    }
}
