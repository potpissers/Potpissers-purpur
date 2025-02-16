package net.minecraft.world.level.levelgen.structure.pools.alias;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

record Random(ResourceKey<StructureTemplatePool> alias, SimpleWeightedRandomList<ResourceKey<StructureTemplatePool>> targets) implements PoolAliasBinding {
    static MapCodec<Random> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                ResourceKey.codec(Registries.TEMPLATE_POOL).fieldOf("alias").forGetter(Random::alias),
                SimpleWeightedRandomList.wrappedCodec(ResourceKey.codec(Registries.TEMPLATE_POOL)).fieldOf("targets").forGetter(Random::targets)
            )
            .apply(instance, Random::new)
    );

    @Override
    public void forEachResolved(RandomSource random, BiConsumer<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> stucturePoolKey) {
        this.targets.getRandom(random).ifPresent(wrapper -> stucturePoolKey.accept(this.alias, wrapper.data()));
    }

    @Override
    public Stream<ResourceKey<StructureTemplatePool>> allTargets() {
        return this.targets.unwrap().stream().map(WeightedEntry.Wrapper::data);
    }

    @Override
    public MapCodec<Random> codec() {
        return CODEC;
    }
}
