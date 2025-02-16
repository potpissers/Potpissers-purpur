package net.minecraft.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Keyable;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;

public interface StringRepresentable {
    int PRE_BUILT_MAP_THRESHOLD = 16;

    String getSerializedName();

    static <E extends Enum<E> & StringRepresentable> StringRepresentable.EnumCodec<E> fromEnum(Supplier<E[]> elementsSupplier) {
        return fromEnumWithMapping(elementsSupplier, string -> string);
    }

    static <E extends Enum<E> & StringRepresentable> StringRepresentable.EnumCodec<E> fromEnumWithMapping(
        Supplier<E[]> enumValues, Function<String, String> keyFunction
    ) {
        E[] enums = (E[])enumValues.get();
        Function<String, E> function = createNameLookup(enums, keyFunction);
        return new StringRepresentable.EnumCodec<>(enums, function);
    }

    static <T extends StringRepresentable> Codec<T> fromValues(Supplier<T[]> valuesSupplier) {
        T[] stringRepresentables = (T[])valuesSupplier.get();
        Function<String, T> function = createNameLookup(stringRepresentables, string -> string);
        ToIntFunction<T> toIntFunction = Util.createIndexLookup(Arrays.asList(stringRepresentables));
        return new StringRepresentable.StringRepresentableCodec<>(stringRepresentables, function, toIntFunction);
    }

    static <T extends StringRepresentable> Function<String, T> createNameLookup(T[] values, Function<String, String> keyFunction) {
        if (values.length > 16) {
            Map<String, T> map = Arrays.<StringRepresentable>stream(values)
                .collect(
                    Collectors.toMap(
                        stringRepresentable -> keyFunction.apply(stringRepresentable.getSerializedName()), stringRepresentable -> (T)stringRepresentable
                    )
                );
            return string -> string == null ? null : map.get(string);
        } else {
            return string -> {
                for (T stringRepresentable : values) {
                    if (keyFunction.apply(stringRepresentable.getSerializedName()).equals(string)) {
                        return stringRepresentable;
                    }
                }

                return null;
            };
        }
    }

    static Keyable keys(final StringRepresentable[] serializables) {
        return new Keyable() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Arrays.stream(serializables).map(StringRepresentable::getSerializedName).map(ops::createString);
            }
        };
    }

    @Deprecated
    public static class EnumCodec<E extends Enum<E> & StringRepresentable> extends StringRepresentable.StringRepresentableCodec<E> {
        private final Function<String, E> resolver;

        public EnumCodec(E[] values, Function<String, E> resolver) {
            super(values, resolver, object -> object.ordinal());
            this.resolver = resolver;
        }

        @Nullable
        public E byName(@Nullable String name) {
            return this.resolver.apply(name);
        }

        public E byName(@Nullable String name, E defaultValue) {
            return Objects.requireNonNullElse(this.byName(name), defaultValue);
        }

        public E byName(@Nullable String name, Supplier<? extends E> defaultValue) {
            return Objects.requireNonNullElseGet(this.byName(name), defaultValue);
        }
    }

    public static class StringRepresentableCodec<S extends StringRepresentable> implements Codec<S> {
        private final Codec<S> codec;

        public StringRepresentableCodec(S[] values, Function<String, S> nameLookup, ToIntFunction<S> indexLookup) {
            this.codec = ExtraCodecs.orCompressed(
                Codec.stringResolver(StringRepresentable::getSerializedName, nameLookup),
                ExtraCodecs.idResolverCodec(indexLookup, i -> i >= 0 && i < values.length ? values[i] : null, -1)
            );
        }

        @Override
        public <T> DataResult<Pair<S, T>> decode(DynamicOps<T> ops, T value) {
            return this.codec.decode(ops, value);
        }

        @Override
        public <T> DataResult<T> encode(S input, DynamicOps<T> ops, T prefix) {
            return this.codec.encode(input, ops, prefix);
        }
    }
}
