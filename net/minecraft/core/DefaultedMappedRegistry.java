package net.minecraft.core;

import com.mojang.serialization.Lifecycle;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

public class DefaultedMappedRegistry<T> extends MappedRegistry<T> implements DefaultedRegistry<T> {
    private final ResourceLocation defaultKey;
    private Holder.Reference<T> defaultValue;

    public DefaultedMappedRegistry(String defaultKey, ResourceKey<? extends Registry<T>> key, Lifecycle registryLifecycle, boolean hasIntrusiveHolders) {
        super(key, registryLifecycle, hasIntrusiveHolders);
        this.defaultKey = ResourceLocation.parse(defaultKey);
    }

    @Override
    public Holder.Reference<T> register(ResourceKey<T> key, T value, RegistrationInfo registrationInfo) {
        Holder.Reference<T> reference = super.register(key, value, registrationInfo);
        if (this.defaultKey.equals(key.location())) {
            this.defaultValue = reference;
        }

        return reference;
    }

    @Override
    public int getId(@Nullable T value) {
        int i = super.getId(value);
        return i == -1 ? super.getId(this.defaultValue.value()) : i;
    }

    @Nonnull
    @Override
    public ResourceLocation getKey(T value) {
        ResourceLocation resourceLocation = super.getKey(value);
        return resourceLocation == null ? this.defaultKey : resourceLocation;
    }

    @Nonnull
    @Override
    public T getValue(@Nullable ResourceLocation key) {
        T object = super.getValue(key);
        return object == null ? this.defaultValue.value() : object;
    }

    @Override
    public Optional<T> getOptional(@Nullable ResourceLocation name) {
        return Optional.ofNullable(super.getValue(name));
    }

    @Override
    public Optional<Holder.Reference<T>> getAny() {
        return Optional.ofNullable(this.defaultValue);
    }

    @Nonnull
    @Override
    public T byId(int id) {
        T object = super.byId(id);
        return object == null ? this.defaultValue.value() : object;
    }

    @Override
    public Optional<Holder.Reference<T>> getRandom(RandomSource random) {
        return super.getRandom(random).or(() -> Optional.of(this.defaultValue));
    }

    @Override
    public ResourceLocation getDefaultKey() {
        return this.defaultKey;
    }
}
