package net.minecraft.data.worldgen;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

public class BastionHousingUnitsPools {
    public static void bootstrap(BootstrapContext<StructureTemplatePool> context) {
        HolderGetter<StructureProcessorList> holderGetter = context.lookup(Registries.PROCESSOR_LIST);
        Holder<StructureProcessorList> orThrow = holderGetter.getOrThrow(ProcessorLists.HOUSING);
        HolderGetter<StructureTemplatePool> holderGetter1 = context.lookup(Registries.TEMPLATE_POOL);
        Holder<StructureTemplatePool> orThrow1 = holderGetter1.getOrThrow(Pools.EMPTY);
        Pools.register(
            context,
            "bastion/units/center_pieces",
            new StructureTemplatePool(
                orThrow1,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/units/center_pieces/center_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/units/center_pieces/center_1", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/units/center_pieces/center_2", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/units/pathways",
            new StructureTemplatePool(
                orThrow1,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/units/pathways/pathway_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/units/pathways/pathway_wall_0", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/units/walls/wall_bases",
            new StructureTemplatePool(
                orThrow1,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/units/walls/wall_base", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/units/walls/connected_wall", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/units/stages/stage_0",
            new StructureTemplatePool(
                orThrow1,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/units/stages/stage_0_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/units/stages/stage_0_1", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/units/stages/stage_0_2", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/units/stages/stage_0_3", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/units/stages/stage_1",
            new StructureTemplatePool(
                orThrow1,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/units/stages/stage_1_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/units/stages/stage_1_1", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/units/stages/stage_1_2", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/units/stages/stage_1_3", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/units/stages/rot/stage_1",
            new StructureTemplatePool(
                orThrow1,
                ImmutableList.of(Pair.of(StructurePoolElement.single("bastion/units/stages/rot/stage_1_0", orThrow), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/units/stages/stage_2",
            new StructureTemplatePool(
                orThrow1,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/units/stages/stage_2_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/units/stages/stage_2_1", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/units/stages/stage_3",
            new StructureTemplatePool(
                orThrow1,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/units/stages/stage_3_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/units/stages/stage_3_1", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/units/stages/stage_3_2", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/units/stages/stage_3_3", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/units/fillers/stage_0",
            new StructureTemplatePool(
                orThrow1,
                ImmutableList.of(Pair.of(StructurePoolElement.single("bastion/units/fillers/stage_0", orThrow), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/units/edges",
            new StructureTemplatePool(
                orThrow1,
                ImmutableList.of(Pair.of(StructurePoolElement.single("bastion/units/edges/edge_0", orThrow), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/units/wall_units",
            new StructureTemplatePool(
                orThrow1,
                ImmutableList.of(Pair.of(StructurePoolElement.single("bastion/units/wall_units/unit_0", orThrow), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/units/edge_wall_units",
            new StructureTemplatePool(
                orThrow1,
                ImmutableList.of(Pair.of(StructurePoolElement.single("bastion/units/wall_units/edge_0_large", orThrow), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/units/ramparts",
            new StructureTemplatePool(
                orThrow1,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/units/ramparts/ramparts_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/units/ramparts/ramparts_1", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/units/ramparts/ramparts_2", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/units/large_ramparts",
            new StructureTemplatePool(
                orThrow1,
                ImmutableList.of(Pair.of(StructurePoolElement.single("bastion/units/ramparts/ramparts_0", orThrow), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/units/rampart_plates",
            new StructureTemplatePool(
                orThrow1,
                ImmutableList.of(Pair.of(StructurePoolElement.single("bastion/units/rampart_plates/plate_0", orThrow), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
    }
}
