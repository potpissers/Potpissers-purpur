package net.minecraft.data.worldgen;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBindings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

public class TrialChambersStructurePools {
    public static final ResourceKey<StructureTemplatePool> START = Pools.createKey("trial_chambers/chamber/end");
    public static final ResourceKey<StructureTemplatePool> HALLWAY_FALLBACK = Pools.createKey("trial_chambers/hallway/fallback");
    public static final List<PoolAliasBinding> ALIAS_BINDINGS = ImmutableList.<PoolAliasBinding>builder()
        .add(
            PoolAliasBinding.randomGroup(
                SimpleWeightedRandomList.<List<PoolAliasBinding>>builder()
                    .add(
                        List.of(
                            PoolAliasBinding.direct(spawner("contents/ranged"), spawner("ranged/skeleton")),
                            PoolAliasBinding.direct(spawner("contents/slow_ranged"), spawner("slow_ranged/skeleton"))
                        )
                    )
                    .add(
                        List.of(
                            PoolAliasBinding.direct(spawner("contents/ranged"), spawner("ranged/stray")),
                            PoolAliasBinding.direct(spawner("contents/slow_ranged"), spawner("slow_ranged/stray"))
                        )
                    )
                    .add(
                        List.of(
                            PoolAliasBinding.direct(spawner("contents/ranged"), spawner("ranged/poison_skeleton")),
                            PoolAliasBinding.direct(spawner("contents/slow_ranged"), spawner("slow_ranged/poison_skeleton"))
                        )
                    )
                    .build()
            )
        )
        .add(
            PoolAliasBinding.random(
                spawner("contents/melee"),
                SimpleWeightedRandomList.<String>builder().add(spawner("melee/zombie")).add(spawner("melee/husk")).add(spawner("melee/spider")).build()
            )
        )
        .add(
            PoolAliasBinding.random(
                spawner("contents/small_melee"),
                SimpleWeightedRandomList.<String>builder()
                    .add(spawner("small_melee/slime"))
                    .add(spawner("small_melee/cave_spider"))
                    .add(spawner("small_melee/silverfish"))
                    .add(spawner("small_melee/baby_zombie"))
                    .build()
            )
        )
        .build();

    public static String spawner(String name) {
        return "trial_chambers/spawner/" + name;
    }

    public static void bootstrap(BootstrapContext<StructureTemplatePool> context) {
        HolderGetter<StructureTemplatePool> holderGetter = context.lookup(Registries.TEMPLATE_POOL);
        Holder<StructureTemplatePool> orThrow = holderGetter.getOrThrow(Pools.EMPTY);
        Holder<StructureTemplatePool> orThrow1 = holderGetter.getOrThrow(HALLWAY_FALLBACK);
        HolderGetter<StructureProcessorList> holderGetter1 = context.lookup(Registries.PROCESSOR_LIST);
        Holder<StructureProcessorList> orThrow2 = holderGetter1.getOrThrow(ProcessorLists.TRIAL_CHAMBERS_COPPER_BULB_DEGRADATION);
        context.register(
            START,
            new StructureTemplatePool(
                orThrow,
                List.of(
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/end_1", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/end_2", orThrow2), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/chamber/entrance_cap",
            new StructureTemplatePool(
                orThrow,
                List.of(Pair.of(StructurePoolElement.single("trial_chambers/chamber/entrance_cap", orThrow2), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/chambers/end",
            new StructureTemplatePool(
                orThrow1,
                List.of(
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/chamber_1", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/eruption", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/slanted", orThrow2), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/corridor",
            new StructureTemplatePool(
                orThrow,
                List.of(
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/second_plate"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/intersection/intersection_1", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/intersection/intersection_2", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/intersection/intersection_3", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/first_plate"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/atrium_1", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/entrance_1", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/entrance_2", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/entrance_3", orThrow2), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/chamber/addon",
            new StructureTemplatePool(
                orThrow,
                List.of(
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/addon/full_stacked_walkway"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/addon/full_stacked_walkway_2"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/addon/full_corner_column"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/addon/grate_bridge"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/addon/hanging_platform"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/addon/short_grate_platform"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/addon/short_platform"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/addon/lower_staircase_down"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/addon/walkway_with_bridge_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/addon/c1_breeze"), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/chamber/assembly",
            new StructureTemplatePool(
                orThrow,
                List.of(
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/full_column"), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/cover_1"), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/cover_2"), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/cover_3"), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/cover_4"), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/cover_5"), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/cover_6"), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/cover_7"), 5),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/platform_1"), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/spawner_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/hanging_1"), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/hanging_2"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/hanging_3"), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/hanging_4"), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/hanging_5"), 4),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/left_staircase_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/left_staircase_2"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/left_staircase_3"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/right_staircase_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/right_staircase_2"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly/right_staircase_3"), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/chamber/eruption",
            new StructureTemplatePool(
                orThrow,
                List.of(
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/eruption/center_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/eruption/breeze_slice_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/eruption/slice_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/eruption/slice_2"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/eruption/slice_3"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/eruption/quadrant_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/eruption/quadrant_2"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/eruption/quadrant_3"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/eruption/quadrant_4"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/eruption/quadrant_5"), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/chamber/slanted",
            new StructureTemplatePool(
                orThrow,
                List.of(
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/slanted/center"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/slanted/hallway_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/slanted/hallway_2"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/slanted/hallway_3"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/slanted/quadrant_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/slanted/quadrant_2"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/slanted/quadrant_3"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/slanted/quadrant_4"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/slanted/ramp_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/slanted/ramp_2"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/slanted/ramp_3"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/slanted/ramp_4"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/slanted/ominous_upper_arm_1"), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/chamber/pedestal",
            new StructureTemplatePool(
                orThrow,
                List.of(
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/pedestal/center_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/pedestal/slice_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/pedestal/slice_2"), 3),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/pedestal/slice_3"), 3),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/pedestal/slice_4"), 3),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/pedestal/slice_5"), 3),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/pedestal/ominous_slice_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/pedestal/quadrant_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/pedestal/quadrant_2"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/pedestal/quadrant_3"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/slanted/quadrant_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/slanted/quadrant_2"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/slanted/quadrant_3"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/slanted/quadrant_4"), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/corridor/slices",
            new StructureTemplatePool(
                orThrow,
                List.of(
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/straight_1", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/straight_2", orThrow2), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/straight_3", orThrow2), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/straight_4", orThrow2), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/straight_5", orThrow2), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/straight_6", orThrow2), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/straight_7", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/straight_8", orThrow2), 2)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        context.register(
            HALLWAY_FALLBACK,
            new StructureTemplatePool(
                orThrow,
                List.of(
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/rubble"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/rubble_chamber"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/rubble_thin"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/rubble_chamber_thin"), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/hallway",
            new StructureTemplatePool(
                orThrow1,
                List.of(
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/corridor_connector_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/upper_hallway_connector", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/lower_hallway_connector", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/rubble"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/chamber_1", orThrow2), 150),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/chamber_2", orThrow2), 150),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/chamber_4", orThrow2), 150),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/chamber_8", orThrow2), 150),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/assembly", orThrow2), 150),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/eruption", orThrow2), 150),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/slanted", orThrow2), 150),
                    Pair.of(StructurePoolElement.single("trial_chambers/chamber/pedestal", orThrow2), 150),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/rubble_chamber", orThrow2), 10),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/rubble_chamber_thin", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/cache_1", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/left_corner", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/right_corner", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/corner_staircase", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/corner_staircase_down", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/long_straight_staircase", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/long_straight_staircase_down", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/straight", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/straight_staircase", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/straight_staircase_down", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/trapped_staircase", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/encounter_1", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/encounter_2", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/encounter_3", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/encounter_4", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/hallway/encounter_5", orThrow2), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/corridors/addon/lower",
            new StructureTemplatePool(
                orThrow,
                List.of(
                    Pair.of(StructurePoolElement.empty(), 8),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/addon/staircase"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/addon/wall"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/addon/ladder_to_middle"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/addon/arrow_dispenser"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/addon/bridge_lower"), 2)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/corridors/addon/middle",
            new StructureTemplatePool(
                orThrow,
                List.of(
                    Pair.of(StructurePoolElement.empty(), 8),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/addon/open_walkway"), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/addon/walled_walkway"), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/corridors/addon/middle_upper",
            new StructureTemplatePool(
                orThrow,
                List.of(
                    Pair.of(StructurePoolElement.empty(), 6),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/addon/open_walkway_upper"), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/addon/chandelier_upper"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/addon/decoration_upper"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/addon/head_upper"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/addon/reward_upper"), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/atrium",
            new StructureTemplatePool(
                orThrow,
                List.of(
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/atrium/bogged_relief"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/atrium/breeze_relief"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/atrium/spiral_relief"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/atrium/spider_relief"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/atrium/grand_staircase_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/atrium/grand_staircase_2"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/atrium/grand_staircase_3"), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/decor",
            new StructureTemplatePool(
                orThrow,
                List.of(
                    Pair.of(StructurePoolElement.empty(), 22),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/empty_pot"), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/dead_bush_pot"), 2),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/undecorated_pot"), 10),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/flow_pot"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/guster_pot"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/scrape_pot"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/candle_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/candle_2"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/candle_3"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/candle_4"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/barrel"), 2)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/decor/disposal",
            new StructureTemplatePool(
                orThrow, List.of(Pair.of(StructurePoolElement.single("trial_chambers/decor/disposal"), 1)), StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/decor/bed",
            new StructureTemplatePool(
                orThrow,
                List.of(
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/white_bed"), 3),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/light_gray_bed"), 3),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/gray_bed"), 3),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/black_bed"), 3),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/brown_bed"), 3),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/red_bed"), 3),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/orange_bed"), 3),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/yellow_bed"), 3),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/lime_bed"), 3),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/green_bed"), 3),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/cyan_bed"), 3),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/light_blue_bed"), 3),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/blue_bed"), 3),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/purple_bed"), 3),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/magenta_bed"), 3),
                    Pair.of(StructurePoolElement.single("trial_chambers/decor/pink_bed"), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/entrance",
            new StructureTemplatePool(
                orThrow,
                List.of(
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/addon/display_1"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/addon/display_2"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/corridor/addon/display_3"), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/decor/chamber",
            new StructureTemplatePool(
                orThrow,
                List.of(Pair.of(StructurePoolElement.empty(), 4), Pair.of(StructurePoolElement.single("trial_chambers/decor/undecorated_pot"), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/reward/all",
            new StructureTemplatePool(
                orThrow, List.of(Pair.of(StructurePoolElement.single("trial_chambers/reward/vault"), 1)), StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/reward/ominous_vault",
            new StructureTemplatePool(
                orThrow, List.of(Pair.of(StructurePoolElement.single("trial_chambers/reward/ominous_vault"), 1)), StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/reward/contents/default",
            new StructureTemplatePool(
                orThrow, List.of(Pair.of(StructurePoolElement.single("trial_chambers/reward/vault"), 1)), StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/chests/supply",
            new StructureTemplatePool(
                orThrow, List.of(Pair.of(StructurePoolElement.single("trial_chambers/chests/connectors/supply"), 1)), StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/chests/contents/supply",
            new StructureTemplatePool(
                orThrow, List.of(Pair.of(StructurePoolElement.single("trial_chambers/chests/supply"), 1)), StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/spawner/ranged",
            new StructureTemplatePool(
                orThrow, List.of(Pair.of(StructurePoolElement.single("trial_chambers/spawner/connectors/ranged"), 1)), StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/spawner/slow_ranged",
            new StructureTemplatePool(
                orThrow,
                List.of(Pair.of(StructurePoolElement.single("trial_chambers/spawner/connectors/slow_ranged"), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/spawner/melee",
            new StructureTemplatePool(
                orThrow, List.of(Pair.of(StructurePoolElement.single("trial_chambers/spawner/connectors/melee"), 1)), StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/spawner/small_melee",
            new StructureTemplatePool(
                orThrow,
                List.of(Pair.of(StructurePoolElement.single("trial_chambers/spawner/connectors/small_melee"), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/spawner/breeze",
            new StructureTemplatePool(
                orThrow, List.of(Pair.of(StructurePoolElement.single("trial_chambers/spawner/connectors/breeze"), 1)), StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/spawner/all",
            new StructureTemplatePool(
                orThrow,
                List.of(
                    Pair.of(StructurePoolElement.single("trial_chambers/spawner/connectors/ranged"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/spawner/connectors/melee"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/spawner/connectors/small_melee"), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/spawner/contents/breeze",
            new StructureTemplatePool(
                orThrow, List.of(Pair.of(StructurePoolElement.single("trial_chambers/spawner/breeze/breeze"), 1)), StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "trial_chambers/dispensers/chamber",
            new StructureTemplatePool(
                orThrow,
                List.of(
                    Pair.of(StructurePoolElement.empty(), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/dispensers/chamber"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/dispensers/wall_dispenser"), 1),
                    Pair.of(StructurePoolElement.single("trial_chambers/dispensers/floor_dispenser"), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        PoolAliasBindings.registerTargetsAsPools(context, orThrow, ALIAS_BINDINGS);
    }
}
