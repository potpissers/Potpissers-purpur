package net.minecraft.util;

import it.unimi.dsi.fastutil.floats.Float2FloatFunction;
import java.util.function.Function;

public interface ToFloatFunction<C> {
    ToFloatFunction<Float> IDENTITY = createUnlimited(f -> f);

    float apply(C object);

    float minValue();

    float maxValue();

    static ToFloatFunction<Float> createUnlimited(final Float2FloatFunction wrapped) {
        return new ToFloatFunction<Float>() {
            @Override
            public float apply(Float object) {
                return wrapped.apply(object);
            }

            @Override
            public float minValue() {
                return Float.NEGATIVE_INFINITY;
            }

            @Override
            public float maxValue() {
                return Float.POSITIVE_INFINITY;
            }
        };
    }

    default <C2> ToFloatFunction<C2> comap(final Function<C2, C> converter) {
        final ToFloatFunction<C> toFloatFunction = this;
        return new ToFloatFunction<C2>() {
            @Override
            public float apply(C2 object) {
                return toFloatFunction.apply(converter.apply(object));
            }

            @Override
            public float minValue() {
                return toFloatFunction.minValue();
            }

            @Override
            public float maxValue() {
                return toFloatFunction.maxValue();
            }
        };
    }
}
