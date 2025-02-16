package net.minecraft.data.tags;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public abstract class ItemTagsProvider extends IntrinsicHolderTagsProvider<Item> {
    private final CompletableFuture<TagsProvider.TagLookup<Block>> blockTags;
    private final Map<TagKey<Block>, TagKey<Item>> tagsToCopy = new HashMap<>();

    public ItemTagsProvider(
        PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, CompletableFuture<TagsProvider.TagLookup<Block>> blockTags
    ) {
        super(output, Registries.ITEM, lookupProvider, item -> item.builtInRegistryHolder().key());
        this.blockTags = blockTags;
    }

    public ItemTagsProvider(
        PackOutput output,
        CompletableFuture<HolderLookup.Provider> lookupProvider,
        CompletableFuture<TagsProvider.TagLookup<Item>> parentProvider,
        CompletableFuture<TagsProvider.TagLookup<Block>> blockTags
    ) {
        super(output, Registries.ITEM, lookupProvider, parentProvider, item -> item.builtInRegistryHolder().key());
        this.blockTags = blockTags;
    }

    protected void copy(TagKey<Block> blockTag, TagKey<Item> itemTag) {
        this.tagsToCopy.put(blockTag, itemTag);
    }

    @Override
    protected CompletableFuture<HolderLookup.Provider> createContentsProvider() {
        return super.createContentsProvider().thenCombine(this.blockTags, (provider, tagLookup) -> {
            this.tagsToCopy.forEach((tagKey, tagKey1) -> {
                TagBuilder rawBuilder = this.getOrCreateRawBuilder((TagKey<Item>)tagKey1);
                Optional<TagBuilder> optional = tagLookup.apply((TagKey<? super TagKey<Block>>)tagKey);
                optional.orElseThrow(() -> new IllegalStateException("Missing block tag " + tagKey1.location())).build().forEach(rawBuilder::add);
            });
            return (HolderLookup.Provider)provider;
        });
    }
}
