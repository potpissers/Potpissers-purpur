package net.minecraft.advancements.critereon;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;

public record WrappedMinMaxBounds(@Nullable Float min, @Nullable Float max) {
    public static final WrappedMinMaxBounds ANY = new WrappedMinMaxBounds(null, null);
    public static final SimpleCommandExceptionType ERROR_INTS_ONLY = new SimpleCommandExceptionType(Component.translatable("argument.range.ints"));

    public static WrappedMinMaxBounds exactly(float value) {
        return new WrappedMinMaxBounds(value, value);
    }

    public static WrappedMinMaxBounds between(float min, float max) {
        return new WrappedMinMaxBounds(min, max);
    }

    public static WrappedMinMaxBounds atLeast(float min) {
        return new WrappedMinMaxBounds(min, null);
    }

    public static WrappedMinMaxBounds atMost(float max) {
        return new WrappedMinMaxBounds(null, max);
    }

    public boolean matches(float value) {
        return (this.min == null || this.max == null || !(this.min > this.max) || !(this.min > value) || !(this.max < value))
            && (this.min == null || !(this.min > value))
            && (this.max == null || !(this.max < value));
    }

    public boolean matchesSqr(double value) {
        return (this.min == null || this.max == null || !(this.min > this.max) || !(this.min * this.min > value) || !(this.max * this.max < value))
            && (this.min == null || !(this.min * this.min > value))
            && (this.max == null || !(this.max * this.max < value));
    }

    public JsonElement serializeToJson() {
        if (this == ANY) {
            return JsonNull.INSTANCE;
        } else if (this.min != null && this.max != null && this.min.equals(this.max)) {
            return new JsonPrimitive(this.min);
        } else {
            JsonObject jsonObject = new JsonObject();
            if (this.min != null) {
                jsonObject.addProperty("min", this.min);
            }

            if (this.max != null) {
                jsonObject.addProperty("max", this.min);
            }

            return jsonObject;
        }
    }

    public static WrappedMinMaxBounds fromJson(@Nullable JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return ANY;
        } else if (GsonHelper.isNumberValue(json)) {
            float f = GsonHelper.convertToFloat(json, "value");
            return new WrappedMinMaxBounds(f, f);
        } else {
            JsonObject jsonObject = GsonHelper.convertToJsonObject(json, "value");
            Float _float = jsonObject.has("min") ? GsonHelper.getAsFloat(jsonObject, "min") : null;
            Float _float1 = jsonObject.has("max") ? GsonHelper.getAsFloat(jsonObject, "max") : null;
            return new WrappedMinMaxBounds(_float, _float1);
        }
    }

    public static WrappedMinMaxBounds fromReader(StringReader reader, boolean isFloatingPoint) throws CommandSyntaxException {
        return fromReader(reader, isFloatingPoint, value -> value);
    }

    public static WrappedMinMaxBounds fromReader(StringReader reader, boolean isFloatingPoint, Function<Float, Float> valueFactory) throws CommandSyntaxException {
        if (!reader.canRead()) {
            throw MinMaxBounds.ERROR_EMPTY.createWithContext(reader);
        } else {
            int cursor = reader.getCursor();
            Float _float = optionallyFormat(readNumber(reader, isFloatingPoint), valueFactory);
            Float _float1;
            if (reader.canRead(2) && reader.peek() == '.' && reader.peek(1) == '.') {
                reader.skip();
                reader.skip();
                _float1 = optionallyFormat(readNumber(reader, isFloatingPoint), valueFactory);
                if (_float == null && _float1 == null) {
                    reader.setCursor(cursor);
                    throw MinMaxBounds.ERROR_EMPTY.createWithContext(reader);
                }
            } else {
                if (!isFloatingPoint && reader.canRead() && reader.peek() == '.') {
                    reader.setCursor(cursor);
                    throw ERROR_INTS_ONLY.createWithContext(reader);
                }

                _float1 = _float;
            }

            if (_float == null && _float1 == null) {
                reader.setCursor(cursor);
                throw MinMaxBounds.ERROR_EMPTY.createWithContext(reader);
            } else {
                return new WrappedMinMaxBounds(_float, _float1);
            }
        }
    }

    @Nullable
    private static Float readNumber(StringReader reader, boolean isFloatingPoint) throws CommandSyntaxException {
        int cursor = reader.getCursor();

        while (reader.canRead() && isAllowedNumber(reader, isFloatingPoint)) {
            reader.skip();
        }

        String sub = reader.getString().substring(cursor, reader.getCursor());
        if (sub.isEmpty()) {
            return null;
        } else {
            try {
                return Float.parseFloat(sub);
            } catch (NumberFormatException var5) {
                if (isFloatingPoint) {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidDouble().createWithContext(reader, sub);
                } else {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidInt().createWithContext(reader, sub);
                }
            }
        }
    }

    private static boolean isAllowedNumber(StringReader reader, boolean isFloatingPoint) {
        char c = reader.peek();
        return c >= '0' && c <= '9' || c == '-' || isFloatingPoint && c == '.' && (!reader.canRead(2) || reader.peek(1) != '.');
    }

    @Nullable
    private static Float optionallyFormat(@Nullable Float value, Function<Float, Float> valueFactory) {
        return value == null ? null : valueFactory.apply(value);
    }
}
