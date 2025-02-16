package net.minecraft.data.tags;

import com.google.common.collect.Maps;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;

public abstract class TagsProvider<T> implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;
    private final CompletableFuture<HolderLookup.Provider> lookupProvider;
    private final CompletableFuture<Void> contentsDone = new CompletableFuture<>();
    private final CompletableFuture<TagsProvider.TagLookup<T>> parentProvider;
    protected final ResourceKey<? extends Registry<T>> registryKey;
    private final Map<ResourceLocation, TagBuilder> builders = Maps.newLinkedHashMap();

    protected TagsProvider(PackOutput output, ResourceKey<? extends Registry<T>> registryKey, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        this(output, registryKey, lookupProvider, CompletableFuture.completedFuture(TagsProvider.TagLookup.empty()));
    }

    protected TagsProvider(
        PackOutput output,
        ResourceKey<? extends Registry<T>> registryKey,
        CompletableFuture<HolderLookup.Provider> lookupProvider,
        CompletableFuture<TagsProvider.TagLookup<T>> parentProvider
    ) {
        this.pathProvider = output.createRegistryTagsPathProvider(registryKey);
        this.registryKey = registryKey;
        this.parentProvider = parentProvider;
        this.lookupProvider = lookupProvider;
    }

    @Override
    public final String getName() {
        return "Tags for " + this.registryKey.location();
    }

    protected abstract void addTags(HolderLookup.Provider provider);

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        record CombinedData<T>(HolderLookup.Provider contents, TagsProvider.TagLookup<T> parent) {
        }

        return this.createContentsProvider()
            .thenApply(provider -> {
                this.contentsDone.complete(null);
                return (HolderLookup.Provider)provider;
            })
            .thenCombineAsync(
                this.parentProvider, (provider, tagLookup) -> new CombinedData<>(provider, (TagsProvider.TagLookup<T>)tagLookup), Util.backgroundExecutor()
            )
            .thenCompose(
                combinedData -> {
                    HolderLookup.RegistryLookup<T> registryLookup = combinedData.contents.lookupOrThrow(this.registryKey);
                    Predicate<ResourceLocation> predicate = resourceLocation -> registryLookup.get(ResourceKey.create(this.registryKey, resourceLocation))
                        .isPresent();
                    Predicate<ResourceLocation> predicate1 = resourceLocation -> this.builders.containsKey(resourceLocation)
                        || combinedData.parent.contains(TagKey.create(this.registryKey, resourceLocation));
                    return CompletableFuture.allOf(
                        this.builders
                            .entrySet()
                            .stream()
                            .map(
                                entry -> {
                                    ResourceLocation resourceLocation = entry.getKey();
                                    TagBuilder tagBuilder = entry.getValue();
                                    List<TagEntry> list = tagBuilder.build();
                                    List<TagEntry> list1 = list.stream().filter(tagEntry -> !tagEntry.verifyIfPresent(predicate, predicate1)).toList();
                                    if (!list1.isEmpty()) {
                                        throw new IllegalArgumentException(
                                            String.format(
                                                Locale.ROOT,
                                                "Couldn't define tag %s as it is missing following references: %s",
                                                resourceLocation,
                                                list1.stream().map(Objects::toString).collect(Collectors.joining(","))
                                            )
                                        );
                                    } else {
                                        Path path = this.pathProvider.json(resourceLocation);
                                        return DataProvider.saveStable(output, combinedData.contents, TagFile.CODEC, new TagFile(list, false), path);
                                    }
                                }
                            )
                            .toArray(CompletableFuture[]::new)
                    );
                }
            );
    }

    protected TagsProvider.TagAppender<T> tag(TagKey<T> tag) {
        TagBuilder rawBuilder = this.getOrCreateRawBuilder(tag);
        return new TagsProvider.TagAppender<>(rawBuilder);
    }

    protected TagBuilder getOrCreateRawBuilder(TagKey<T> tag) {
        return this.builders.computeIfAbsent(tag.location(), resourceLocation -> TagBuilder.create());
    }

    public CompletableFuture<TagsProvider.TagLookup<T>> contentsGetter() {
        return this.contentsDone.thenApply(_void -> tagKey -> Optional.ofNullable(this.builders.get(tagKey.location())));
    }

    protected CompletableFuture<HolderLookup.Provider> createContentsProvider() {
        return this.lookupProvider.thenApply(provider -> {
            this.builders.clear();
            this.addTags(provider);
            return (HolderLookup.Provider)provider;
        });
    }

    protected static class TagAppender<T> {
        private final TagBuilder builder;

        protected TagAppender(TagBuilder builder) {
            this.builder = builder;
        }

        public final TagsProvider.TagAppender<T> add(ResourceKey<T> key) {
            this.builder.addElement(key.location());
            return this;
        }

        @SafeVarargs
        public final TagsProvider.TagAppender<T> add(ResourceKey<T>... keys) {
            for (ResourceKey<T> resourceKey : keys) {
                this.builder.addElement(resourceKey.location());
            }

            return this;
        }

        public final TagsProvider.TagAppender<T> addAll(List<ResourceKey<T>> keys) {
            for (ResourceKey<T> resourceKey : keys) {
                this.builder.addElement(resourceKey.location());
            }

            return this;
        }

        public TagsProvider.TagAppender<T> addOptional(ResourceLocation location) {
            this.builder.addOptionalElement(location);
            return this;
        }

        public TagsProvider.TagAppender<T> addTag(TagKey<T> tag) {
            this.builder.addTag(tag.location());
            return this;
        }

        public TagsProvider.TagAppender<T> addOptionalTag(ResourceLocation location) {
            this.builder.addOptionalTag(location);
            return this;
        }
    }

    @FunctionalInterface
    public interface TagLookup<T> extends Function<TagKey<T>, Optional<TagBuilder>> {
        static <T> TagsProvider.TagLookup<T> empty() {
            return tagKey -> Optional.empty();
        }

        default boolean contains(TagKey<T> key) {
            return this.apply(key).isPresent();
        }
    }
}
