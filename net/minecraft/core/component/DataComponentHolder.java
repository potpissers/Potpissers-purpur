package net.minecraft.core.component;

import java.util.stream.Stream;
import javax.annotation.Nullable;

public interface DataComponentHolder {
    DataComponentMap getComponents();

    @Nullable
    default <T> T get(DataComponentType<? extends T> component) {
        return this.getComponents().get(component);
    }

    default <T> Stream<T> getAllOfType(Class<? extends T> type) {
        return this.getComponents().stream().map(TypedDataComponent::value).filter(object -> type.isAssignableFrom(object.getClass())).map(object -> (T)object);
    }

    default <T> T getOrDefault(DataComponentType<? extends T> component, T defaultValue) {
        return this.getComponents().getOrDefault(component, defaultValue);
    }

    default boolean has(DataComponentType<?> component) {
        return this.getComponents().has(component);
    }
}
