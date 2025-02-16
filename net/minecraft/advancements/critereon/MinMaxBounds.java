package net.minecraft.advancements.critereon;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

public interface MinMaxBounds<T extends Number> {
    SimpleCommandExceptionType ERROR_EMPTY = new SimpleCommandExceptionType(Component.translatable("argument.range.empty"));
    SimpleCommandExceptionType ERROR_SWAPPED = new SimpleCommandExceptionType(Component.translatable("argument.range.swapped"));

    Optional<T> min();

    Optional<T> max();

    default boolean isAny() {
        return this.min().isEmpty() && this.max().isEmpty();
    }

    default Optional<T> unwrapPoint() {
        Optional<T> optional = this.min();
        Optional<T> optional1 = this.max();
        return optional.equals(optional1) ? optional : Optional.empty();
    }

    static <T extends Number, R extends MinMaxBounds<T>> Codec<R> createCodec(Codec<T> codec, MinMaxBounds.BoundsFactory<T, R> boundsFactory) {
        Codec<R> codec1 = RecordCodecBuilder.create(
            instance -> instance.group(codec.optionalFieldOf("min").forGetter(MinMaxBounds::min), codec.optionalFieldOf("max").forGetter(MinMaxBounds::max))
                .apply(instance, boundsFactory::create)
        );
        return Codec.either(codec1, codec)
            .xmap(either -> either.map(min -> (R)min, max -> boundsFactory.create(Optional.of((T)max), Optional.of((T)max))), bounds -> {
                Optional<T> optional = bounds.unwrapPoint();
                return optional.isPresent() ? Either.right(optional.get()) : Either.left((R)bounds);
            });
    }

    static <T extends Number, R extends MinMaxBounds<T>> R fromReader(
        StringReader reader,
        MinMaxBounds.BoundsFromReaderFactory<T, R> boundedFactory,
        Function<String, T> valueFactory,
        Supplier<DynamicCommandExceptionType> commandExceptionSupplier,
        Function<T, T> formatter
    ) throws CommandSyntaxException {
        if (!reader.canRead()) {
            throw ERROR_EMPTY.createWithContext(reader);
        } else {
            int cursor = reader.getCursor();

            try {
                Optional<T> optional = readNumber(reader, valueFactory, commandExceptionSupplier).map(formatter);
                Optional<T> optional1;
                if (reader.canRead(2) && reader.peek() == '.' && reader.peek(1) == '.') {
                    reader.skip();
                    reader.skip();
                    optional1 = readNumber(reader, valueFactory, commandExceptionSupplier).map(formatter);
                    if (optional.isEmpty() && optional1.isEmpty()) {
                        throw ERROR_EMPTY.createWithContext(reader);
                    }
                } else {
                    optional1 = optional;
                }

                if (optional.isEmpty() && optional1.isEmpty()) {
                    throw ERROR_EMPTY.createWithContext(reader);
                } else {
                    return boundedFactory.create(reader, optional, optional1);
                }
            } catch (CommandSyntaxException var8) {
                reader.setCursor(cursor);
                throw new CommandSyntaxException(var8.getType(), var8.getRawMessage(), var8.getInput(), cursor);
            }
        }
    }

    private static <T extends Number> Optional<T> readNumber(
        StringReader reader, Function<String, T> stringToValueFunction, Supplier<DynamicCommandExceptionType> commandExceptionSupplier
    ) throws CommandSyntaxException {
        int cursor = reader.getCursor();

        while (reader.canRead() && isAllowedInputChat(reader)) {
            reader.skip();
        }

        String sub = reader.getString().substring(cursor, reader.getCursor());
        if (sub.isEmpty()) {
            return Optional.empty();
        } else {
            try {
                return Optional.of(stringToValueFunction.apply(sub));
            } catch (NumberFormatException var6) {
                throw commandExceptionSupplier.get().createWithContext(reader, sub);
            }
        }
    }

    private static boolean isAllowedInputChat(StringReader reader) {
        char c = reader.peek();
        return c >= '0' && c <= '9' || c == '-' || c == '.' && (!reader.canRead(2) || reader.peek(1) != '.');
    }

    @FunctionalInterface
    public interface BoundsFactory<T extends Number, R extends MinMaxBounds<T>> {
        R create(Optional<T> min, Optional<T> max);
    }

    @FunctionalInterface
    public interface BoundsFromReaderFactory<T extends Number, R extends MinMaxBounds<T>> {
        R create(StringReader reader, Optional<T> min, Optional<T> max) throws CommandSyntaxException;
    }

    public record Doubles(@Override Optional<Double> min, @Override Optional<Double> max, Optional<Double> minSq, Optional<Double> maxSq)
        implements MinMaxBounds<Double> {
        public static final MinMaxBounds.Doubles ANY = new MinMaxBounds.Doubles(Optional.empty(), Optional.empty());
        public static final Codec<MinMaxBounds.Doubles> CODEC = MinMaxBounds.<Double, MinMaxBounds.Doubles>createCodec(Codec.DOUBLE, MinMaxBounds.Doubles::new);

        private Doubles(Optional<Double> min, Optional<Double> max) {
            this(min, max, squareOpt(min), squareOpt(max));
        }

        private static MinMaxBounds.Doubles create(StringReader reader, Optional<Double> min, Optional<Double> max) throws CommandSyntaxException {
            if (min.isPresent() && max.isPresent() && min.get() > max.get()) {
                throw ERROR_SWAPPED.createWithContext(reader);
            } else {
                return new MinMaxBounds.Doubles(min, max);
            }
        }

        private static Optional<Double> squareOpt(Optional<Double> value) {
            return value.map(val -> val * val);
        }

        public static MinMaxBounds.Doubles exactly(double value) {
            return new MinMaxBounds.Doubles(Optional.of(value), Optional.of(value));
        }

        public static MinMaxBounds.Doubles between(double min, double max) {
            return new MinMaxBounds.Doubles(Optional.of(min), Optional.of(max));
        }

        public static MinMaxBounds.Doubles atLeast(double min) {
            return new MinMaxBounds.Doubles(Optional.of(min), Optional.empty());
        }

        public static MinMaxBounds.Doubles atMost(double max) {
            return new MinMaxBounds.Doubles(Optional.empty(), Optional.of(max));
        }

        public boolean matches(double value) {
            return (!this.min.isPresent() || !(this.min.get() > value)) && (this.max.isEmpty() || !(this.max.get() < value));
        }

        public boolean matchesSqr(double value) {
            return (!this.minSq.isPresent() || !(this.minSq.get() > value)) && (this.maxSq.isEmpty() || !(this.maxSq.get() < value));
        }

        public static MinMaxBounds.Doubles fromReader(StringReader reader) throws CommandSyntaxException {
            return fromReader(reader, _double -> _double);
        }

        public static MinMaxBounds.Doubles fromReader(StringReader reader, Function<Double, Double> formatter) throws CommandSyntaxException {
            return MinMaxBounds.fromReader(
                reader, MinMaxBounds.Doubles::create, Double::parseDouble, CommandSyntaxException.BUILT_IN_EXCEPTIONS::readerInvalidDouble, formatter
            );
        }
    }

    public record Ints(@Override Optional<Integer> min, @Override Optional<Integer> max, Optional<Long> minSq, Optional<Long> maxSq)
        implements MinMaxBounds<Integer> {
        public static final MinMaxBounds.Ints ANY = new MinMaxBounds.Ints(Optional.empty(), Optional.empty());
        public static final Codec<MinMaxBounds.Ints> CODEC = MinMaxBounds.<Integer, MinMaxBounds.Ints>createCodec(Codec.INT, MinMaxBounds.Ints::new);

        private Ints(Optional<Integer> min, Optional<Integer> max) {
            this(min, max, min.map(integer -> integer.longValue() * integer.longValue()), squareOpt(max));
        }

        private static MinMaxBounds.Ints create(StringReader reader, Optional<Integer> min, Optional<Integer> max) throws CommandSyntaxException {
            if (min.isPresent() && max.isPresent() && min.get() > max.get()) {
                throw ERROR_SWAPPED.createWithContext(reader);
            } else {
                return new MinMaxBounds.Ints(min, max);
            }
        }

        private static Optional<Long> squareOpt(Optional<Integer> value) {
            return value.map(integer -> integer.longValue() * integer.longValue());
        }

        public static MinMaxBounds.Ints exactly(int value) {
            return new MinMaxBounds.Ints(Optional.of(value), Optional.of(value));
        }

        public static MinMaxBounds.Ints between(int min, int max) {
            return new MinMaxBounds.Ints(Optional.of(min), Optional.of(max));
        }

        public static MinMaxBounds.Ints atLeast(int min) {
            return new MinMaxBounds.Ints(Optional.of(min), Optional.empty());
        }

        public static MinMaxBounds.Ints atMost(int max) {
            return new MinMaxBounds.Ints(Optional.empty(), Optional.of(max));
        }

        public boolean matches(int value) {
            return (!this.min.isPresent() || this.min.get() <= value) && (this.max.isEmpty() || this.max.get() >= value);
        }

        public boolean matchesSqr(long value) {
            return (!this.minSq.isPresent() || this.minSq.get() <= value) && (this.maxSq.isEmpty() || this.maxSq.get() >= value);
        }

        public static MinMaxBounds.Ints fromReader(StringReader reader) throws CommandSyntaxException {
            return fromReader(reader, integer -> integer);
        }

        public static MinMaxBounds.Ints fromReader(StringReader reader, Function<Integer, Integer> valueFunction) throws CommandSyntaxException {
            return MinMaxBounds.fromReader(
                reader, MinMaxBounds.Ints::create, Integer::parseInt, CommandSyntaxException.BUILT_IN_EXCEPTIONS::readerInvalidInt, valueFunction
            );
        }
    }
}
