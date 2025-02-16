package net.minecraft.resources;

import com.google.common.collect.MapMaker;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;

public class ResourceKey<T> {
    private static final ConcurrentMap<ResourceKey.InternKey, ResourceKey<?>> VALUES = new MapMaker().weakValues().makeMap();
    private final ResourceLocation registryName;
    private final ResourceLocation location;

    public static <T> Codec<ResourceKey<T>> codec(ResourceKey<? extends Registry<T>> registryKey) {
        return ResourceLocation.CODEC.xmap(path -> create(registryKey, path), ResourceKey::location);
    }

    public static <T> StreamCodec<ByteBuf, ResourceKey<T>> streamCodec(ResourceKey<? extends Registry<T>> registryKey) {
        return ResourceLocation.STREAM_CODEC.map(location -> create(registryKey, location), ResourceKey::location);
    }

    public static <T> ResourceKey<T> create(ResourceKey<? extends Registry<T>> registryKey, ResourceLocation location) {
        return create(registryKey.location, location);
    }

    public static <T> ResourceKey<Registry<T>> createRegistryKey(ResourceLocation location) {
        return create(Registries.ROOT_REGISTRY_NAME, location);
    }

    private static <T> ResourceKey<T> create(ResourceLocation registryName, ResourceLocation location) {
        return (ResourceKey<T>)VALUES.computeIfAbsent(new ResourceKey.InternKey(registryName, location), key -> new ResourceKey(key.registry, key.location));
    }

    private ResourceKey(ResourceLocation registryName, ResourceLocation location) {
        this.registryName = registryName;
        this.location = location;
    }

    @Override
    public String toString() {
        return "ResourceKey[" + this.registryName + " / " + this.location + "]";
    }

    public boolean isFor(ResourceKey<? extends Registry<?>> registryKey) {
        return this.registryName.equals(registryKey.location());
    }

    public <E> Optional<ResourceKey<E>> cast(ResourceKey<? extends Registry<E>> registryKey) {
        return this.isFor(registryKey) ? Optional.of((ResourceKey<E>)this) : Optional.empty();
    }

    public ResourceLocation location() {
        return this.location;
    }

    public ResourceLocation registry() {
        return this.registryName;
    }

    public ResourceKey<Registry<T>> registryKey() {
        return createRegistryKey(this.registryName);
    }

    record InternKey(ResourceLocation registry, ResourceLocation location) {
    }
}
