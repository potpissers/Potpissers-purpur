package net.minecraft.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.mojang.brigadier.StringReader;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Field;
import net.minecraft.CharPredicate;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;

public class ParserUtils {
    private static final Field JSON_READER_POS = Util.make(() -> {
        try {
            Field declaredField = JsonReader.class.getDeclaredField("pos");
            declaredField.setAccessible(true);
            return declaredField;
        } catch (NoSuchFieldException var1) {
            throw new IllegalStateException("Couldn't get field 'pos' for JsonReader", var1);
        }
    });
    private static final Field JSON_READER_LINESTART = Util.make(() -> {
        try {
            Field declaredField = JsonReader.class.getDeclaredField("lineStart");
            declaredField.setAccessible(true);
            return declaredField;
        } catch (NoSuchFieldException var1) {
            throw new IllegalStateException("Couldn't get field 'lineStart' for JsonReader", var1);
        }
    });

    private static int getPos(JsonReader reader) {
        try {
            return JSON_READER_POS.getInt(reader) - JSON_READER_LINESTART.getInt(reader);
        } catch (IllegalAccessException var2) {
            throw new IllegalStateException("Couldn't read position of JsonReader", var2);
        }
    }

    public static <T> T parseJson(HolderLookup.Provider registries, StringReader reader, Codec<T> codec) {
        JsonReader jsonReader = new JsonReader(new java.io.StringReader(reader.getRemaining()));
        jsonReader.setLenient(false);

        Object var5;
        try {
            JsonElement jsonElement = Streams.parse(jsonReader);
            var5 = codec.parse(registries.createSerializationContext(JsonOps.INSTANCE), jsonElement).getOrThrow(JsonParseException::new);
        } catch (StackOverflowError var9) {
            throw new JsonParseException(var9);
        } finally {
            reader.setCursor(reader.getCursor() + getPos(jsonReader));
        }

        return (T)var5;
    }

    public static String readWhile(StringReader reader, CharPredicate predicate) {
        int cursor = reader.getCursor();

        while (reader.canRead() && predicate.test(reader.peek())) {
            reader.skip();
        }

        return reader.getString().substring(cursor, reader.getCursor());
    }
}
