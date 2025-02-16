package net.minecraft.world.level.levelgen.structure.pools.alias;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

record RandomGroup(SimpleWeightedRandomList<List<PoolAliasBinding>> groups) implements PoolAliasBinding {
    static MapCodec<RandomGroup> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(SimpleWeightedRandomList.wrappedCodec(Codec.list(PoolAliasBinding.CODEC)).fieldOf("groups").forGetter(RandomGroup::groups))
            .apply(instance, RandomGroup::new)
    );

    @Override
    public void forEachResolved(RandomSource random, BiConsumer<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> stucturePoolKey) {
        this.groups
            .getRandom(random)
            .ifPresent(wrapper -> wrapper.data().forEach(poolAliasBinding -> poolAliasBinding.forEachResolved(random, stucturePoolKey)));
    }

    @Override
    public Stream<ResourceKey<StructureTemplatePool>> allTargets() {
        return this.groups.unwrap().stream().flatMap(wrapper -> wrapper.data().stream()).flatMap(PoolAliasBinding::allTargets);
    }

    @Override
    public MapCodec<RandomGroup> codec() {
        return CODEC;
    }
}
