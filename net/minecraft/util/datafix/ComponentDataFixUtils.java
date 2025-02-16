package net.minecraft.util.datafix;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Optional;
import net.minecraft.util.GsonHelper;

public class ComponentDataFixUtils {
    private static final String EMPTY_CONTENTS = createTextComponentJson("");

    public static <T> Dynamic<T> createPlainTextComponent(DynamicOps<T> ops, String text) {
        String string = createTextComponentJson(text);
        return new Dynamic<>(ops, ops.createString(string));
    }

    public static <T> Dynamic<T> createEmptyComponent(DynamicOps<T> ops) {
        return new Dynamic<>(ops, ops.createString(EMPTY_CONTENTS));
    }

    private static String createTextComponentJson(String text) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("text", text);
        return GsonHelper.toStableString(jsonObject);
    }

    public static <T> Dynamic<T> createTranslatableComponent(DynamicOps<T> ops, String translationKey) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("translate", translationKey);
        return new Dynamic<>(ops, ops.createString(GsonHelper.toStableString(jsonObject)));
    }

    public static <T> Dynamic<T> wrapLiteralStringAsComponent(Dynamic<T> dynamic) {
        return DataFixUtils.orElse(dynamic.asString().map(string -> createPlainTextComponent(dynamic.getOps(), string)).result(), dynamic);
    }

    public static Dynamic<?> rewriteFromLenient(Dynamic<?> dynamic) {
        Optional<String> optional = dynamic.asString().result();
        if (optional.isEmpty()) {
            return dynamic;
        } else {
            String string = optional.get();
            if (!string.isEmpty() && !string.equals("null")) {
                char c = string.charAt(0);
                char c1 = string.charAt(string.length() - 1);
                if (c == '"' && c1 == '"' || c == '{' && c1 == '}' || c == '[' && c1 == ']') {
                    try {
                        JsonElement jsonElement = JsonParser.parseString(string);
                        if (jsonElement.isJsonPrimitive()) {
                            return createPlainTextComponent(dynamic.getOps(), jsonElement.getAsString());
                        }

                        return dynamic.createString(GsonHelper.toStableString(jsonElement));
                    } catch (JsonParseException var6) {
                    }
                }

                return createPlainTextComponent(dynamic.getOps(), string);
            } else {
                return createEmptyComponent(dynamic.getOps());
            }
        }
    }

    public static Optional<String> extractTranslationString(String data) {
        try {
            JsonElement jsonElement = JsonParser.parseString(data);
            if (jsonElement.isJsonObject()) {
                JsonObject asJsonObject = jsonElement.getAsJsonObject();
                JsonElement jsonElement1 = asJsonObject.get("translate");
                if (jsonElement1 != null && jsonElement1.isJsonPrimitive()) {
                    return Optional.of(jsonElement1.getAsString());
                }
            }
        } catch (JsonParseException var4) {
        }

        return Optional.empty();
    }
}
