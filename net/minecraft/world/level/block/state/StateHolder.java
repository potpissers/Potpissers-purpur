package net.minecraft.world.level.block.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.properties.Property;

public abstract class StateHolder<O, S> {
    public static final String NAME_TAG = "Name";
    public static final String PROPERTIES_TAG = "Properties";
    public static final Function<Entry<Property<?>, Comparable<?>>, String> PROPERTY_ENTRY_TO_STRING_FUNCTION = new Function<Entry<Property<?>, Comparable<?>>, String>() {
        @Override
        public String apply(@Nullable Entry<Property<?>, Comparable<?>> propertyEntry) {
            if (propertyEntry == null) {
                return "<NULL>";
            } else {
                Property<?> property = propertyEntry.getKey();
                return property.getName() + "=" + this.getName(property, propertyEntry.getValue());
            }
        }

        private <T extends Comparable<T>> String getName(Property<T> property, Comparable<?> value) {
            return property.getName((T)value);
        }
    };
    protected final O owner;
    private final Reference2ObjectArrayMap<Property<?>, Comparable<?>> values;
    private Map<Property<?>, S[]> neighbours;
    protected final MapCodec<S> propertiesCodec;

    protected StateHolder(O owner, Reference2ObjectArrayMap<Property<?>, Comparable<?>> values, MapCodec<S> propertiesCodec) {
        this.owner = owner;
        this.values = values;
        this.propertiesCodec = propertiesCodec;
    }

    public <T extends Comparable<T>> S cycle(Property<T> property) {
        return this.setValue(property, findNextInCollection(property.getPossibleValues(), this.getValue(property)));
    }

    protected static <T> T findNextInCollection(List<T> possibleValues, T currentValue) {
        int i = possibleValues.indexOf(currentValue) + 1;
        return i == possibleValues.size() ? possibleValues.getFirst() : possibleValues.get(i);
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(this.owner);
        if (!this.getValues().isEmpty()) {
            stringBuilder.append('[');
            stringBuilder.append(this.getValues().entrySet().stream().map(PROPERTY_ENTRY_TO_STRING_FUNCTION).collect(Collectors.joining(",")));
            stringBuilder.append(']');
        }

        return stringBuilder.toString();
    }

    public Collection<Property<?>> getProperties() {
        return Collections.unmodifiableCollection(this.values.keySet());
    }

    public <T extends Comparable<T>> boolean hasProperty(Property<T> property) {
        return this.values.containsKey(property);
    }

    public <T extends Comparable<T>> T getValue(Property<T> property) {
        Comparable<?> comparable = this.values.get(property);
        if (comparable == null) {
            throw new IllegalArgumentException("Cannot get property " + property + " as it does not exist in " + this.owner);
        } else {
            return property.getValueClass().cast(comparable);
        }
    }

    public <T extends Comparable<T>> Optional<T> getOptionalValue(Property<T> property) {
        return Optional.ofNullable(this.getNullableValue(property));
    }

    public <T extends Comparable<T>> T getValueOrElse(Property<T> property, T defaultValue) {
        return Objects.requireNonNullElse(this.getNullableValue(property), defaultValue);
    }

    @Nullable
    public <T extends Comparable<T>> T getNullableValue(Property<T> property) {
        Comparable<?> comparable = this.values.get(property);
        return comparable == null ? null : property.getValueClass().cast(comparable);
    }

    public <T extends Comparable<T>, V extends T> S setValue(Property<T> property, V value) {
        Comparable<?> comparable = this.values.get(property);
        if (comparable == null) {
            throw new IllegalArgumentException("Cannot set property " + property + " as it does not exist in " + this.owner);
        } else {
            return this.setValueInternal(property, value, comparable);
        }
    }

    public <T extends Comparable<T>, V extends T> S trySetValue(Property<T> property, V value) {
        Comparable<?> comparable = this.values.get(property);
        return (S)(comparable == null ? this : this.setValueInternal(property, value, comparable));
    }

    private <T extends Comparable<T>, V extends T> S setValueInternal(Property<T> property, V value, Comparable<?> comparable) {
        if (comparable.equals(value)) {
            return (S)this;
        } else {
            int internalIndex = property.getInternalIndex((T)value);
            if (internalIndex < 0) {
                throw new IllegalArgumentException("Cannot set property " + property + " to " + value + " on " + this.owner + ", it is not an allowed value");
            } else {
                return (S)this.neighbours.get(property)[internalIndex];
            }
        }
    }

    public void populateNeighbours(Map<Map<Property<?>, Comparable<?>>, S> possibleStateMap) {
        if (this.neighbours != null) {
            throw new IllegalStateException();
        } else {
            Map<Property<?>, S[]> map = new Reference2ObjectArrayMap<>(this.values.size());

            for (Entry<Property<?>, Comparable<?>> entry : this.values.entrySet()) {
                Property<?> property = entry.getKey();
                map.put(
                    property,
                    (S[]) property.getPossibleValues().stream().map(comparable -> possibleStateMap.get(this.makeNeighbourValues(property, comparable))).toArray()
                );
            }

            this.neighbours = map;
        }
    }

    private Map<Property<?>, Comparable<?>> makeNeighbourValues(Property<?> property, Comparable<?> value) {
        Map<Property<?>, Comparable<?>> map = new Reference2ObjectArrayMap<>(this.values);
        map.put(property, value);
        return map;
    }

    public Map<Property<?>, Comparable<?>> getValues() {
        return this.values;
    }

    protected static <O, S extends StateHolder<O, S>> Codec<S> codec(Codec<O> propertyMap, Function<O, S> holderFunction) {
        return propertyMap.dispatch(
            "Name",
            stateHolder -> stateHolder.owner,
            object -> {
                S stateHolder = holderFunction.apply((O)object);
                return stateHolder.getValues().isEmpty()
                    ? MapCodec.unit(stateHolder)
                    : stateHolder.propertiesCodec.codec().lenientOptionalFieldOf("Properties").xmap(optional -> optional.orElse(stateHolder), Optional::of);
            }
        );
    }
}
