package net.minecraft.resources;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import java.lang.reflect.Type;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.ResourceLocationException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.GsonHelper;

public final class ResourceLocation implements Comparable<ResourceLocation> {
    public static final Codec<ResourceLocation> CODEC = Codec.STRING
        .<ResourceLocation>comapFlatMap(ResourceLocation::read, ResourceLocation::toString)
        .stable();
    public static final StreamCodec<ByteBuf, ResourceLocation> STREAM_CODEC = ByteBufCodecs.STRING_UTF8
        .map(ResourceLocation::parse, ResourceLocation::toString);
    public static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(Component.translatable("argument.id.invalid"));
    public static final char NAMESPACE_SEPARATOR = ':';
    public static final String DEFAULT_NAMESPACE = "minecraft";
    public static final String REALMS_NAMESPACE = "realms";
    private final String namespace;
    private final String path;

    private ResourceLocation(String namespace, String path) {
        assert isValidNamespace(namespace);

        assert isValidPath(path);

        this.namespace = namespace;
        this.path = path;
    }

    private static ResourceLocation createUntrusted(String namespace, String path) {
        return new ResourceLocation(assertValidNamespace(namespace, path), assertValidPath(namespace, path));
    }

    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        return createUntrusted(namespace, path);
    }

    public static ResourceLocation parse(String location) {
        return bySeparator(location, ':');
    }

    public static ResourceLocation withDefaultNamespace(String location) {
        return new ResourceLocation("minecraft", assertValidPath("minecraft", location));
    }

    @Nullable
    public static ResourceLocation tryParse(String location) {
        return tryBySeparator(location, ':');
    }

    @Nullable
    public static ResourceLocation tryBuild(String namespace, String path) {
        return isValidNamespace(namespace) && isValidPath(path) ? new ResourceLocation(namespace, path) : null;
    }

    public static ResourceLocation bySeparator(String location, char seperator) {
        int index = location.indexOf(seperator);
        if (index >= 0) {
            String sub = location.substring(index + 1);
            if (index != 0) {
                String sub1 = location.substring(0, index);
                return createUntrusted(sub1, sub);
            } else {
                return withDefaultNamespace(sub);
            }
        } else {
            return withDefaultNamespace(location);
        }
    }

    @Nullable
    public static ResourceLocation tryBySeparator(String location, char seperator) {
        int index = location.indexOf(seperator);
        if (index >= 0) {
            String sub = location.substring(index + 1);
            if (!isValidPath(sub)) {
                return null;
            } else if (index != 0) {
                String sub1 = location.substring(0, index);
                return isValidNamespace(sub1) ? new ResourceLocation(sub1, sub) : null;
            } else {
                return new ResourceLocation("minecraft", sub);
            }
        } else {
            return isValidPath(location) ? new ResourceLocation("minecraft", location) : null;
        }
    }

    public static DataResult<ResourceLocation> read(String location) {
        try {
            return DataResult.success(parse(location));
        } catch (ResourceLocationException var2) {
            return DataResult.error(() -> "Not a valid resource location: " + location + " " + var2.getMessage());
        }
    }

    public String getPath() {
        return this.path;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public ResourceLocation withPath(String path) {
        return new ResourceLocation(this.namespace, assertValidPath(this.namespace, path));
    }

    public ResourceLocation withPath(UnaryOperator<String> pathOperator) {
        return this.withPath(pathOperator.apply(this.path));
    }

    public ResourceLocation withPrefix(String pathPrefix) {
        return this.withPath(pathPrefix + this.path);
    }

    public ResourceLocation withSuffix(String pathSuffix) {
        return this.withPath(this.path + pathSuffix);
    }

    @Override
    public String toString() {
        return this.namespace + ":" + this.path;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof ResourceLocation resourceLocation
                && this.namespace.equals(resourceLocation.namespace)
                && this.path.equals(resourceLocation.path);
    }

    @Override
    public int hashCode() {
        return 31 * this.namespace.hashCode() + this.path.hashCode();
    }

    @Override
    public int compareTo(ResourceLocation other) {
        int i = this.path.compareTo(other.path);
        if (i == 0) {
            i = this.namespace.compareTo(other.namespace);
        }

        return i;
    }

    public String toDebugFileName() {
        return this.toString().replace('/', '_').replace(':', '_');
    }

    public String toLanguageKey() {
        return this.namespace + "." + this.path;
    }

    public String toShortLanguageKey() {
        return this.namespace.equals("minecraft") ? this.path : this.toLanguageKey();
    }

    public String toLanguageKey(String type) {
        return type + "." + this.toLanguageKey();
    }

    public String toLanguageKey(String type, String key) {
        return type + "." + this.toLanguageKey() + "." + key;
    }

    private static String readGreedy(StringReader reader) {
        int cursor = reader.getCursor();

        while (reader.canRead() && isAllowedInResourceLocation(reader.peek())) {
            reader.skip();
        }

        return reader.getString().substring(cursor, reader.getCursor());
    }

    public static ResourceLocation read(StringReader reader) throws CommandSyntaxException {
        int cursor = reader.getCursor();
        String greedy = readGreedy(reader);

        try {
            return parse(greedy);
        } catch (ResourceLocationException var4) {
            reader.setCursor(cursor);
            throw ERROR_INVALID.createWithContext(reader);
        }
    }

    public static ResourceLocation readNonEmpty(StringReader reader) throws CommandSyntaxException {
        int cursor = reader.getCursor();
        String greedy = readGreedy(reader);
        if (greedy.isEmpty()) {
            throw ERROR_INVALID.createWithContext(reader);
        } else {
            try {
                return parse(greedy);
            } catch (ResourceLocationException var4) {
                reader.setCursor(cursor);
                throw ERROR_INVALID.createWithContext(reader);
            }
        }
    }

    public static boolean isAllowedInResourceLocation(char character) {
        return character >= '0' && character <= '9'
            || character >= 'a' && character <= 'z'
            || character == '_'
            || character == ':'
            || character == '/'
            || character == '.'
            || character == '-';
    }

    public static boolean isValidPath(String path) {
        for (int i = 0; i < path.length(); i++) {
            if (!validPathChar(path.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    public static boolean isValidNamespace(String namespace) {
        for (int i = 0; i < namespace.length(); i++) {
            if (!validNamespaceChar(namespace.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static String assertValidNamespace(String namespace, String path) {
        if (!isValidNamespace(namespace)) {
            throw new ResourceLocationException("Non [a-z0-9_.-] character in namespace of location: " + namespace + ":" + path);
        } else {
            return namespace;
        }
    }

    public static boolean validPathChar(char pathChar) {
        return pathChar == '_'
            || pathChar == '-'
            || pathChar >= 'a' && pathChar <= 'z'
            || pathChar >= '0' && pathChar <= '9'
            || pathChar == '/'
            || pathChar == '.';
    }

    private static boolean validNamespaceChar(char namespaceChar) {
        return namespaceChar == '_'
            || namespaceChar == '-'
            || namespaceChar >= 'a' && namespaceChar <= 'z'
            || namespaceChar >= '0' && namespaceChar <= '9'
            || namespaceChar == '.';
    }

    private static String assertValidPath(String namespace, String path) {
        if (!isValidPath(path)) {
            throw new ResourceLocationException("Non [a-z0-9/._-] character in path of location: " + namespace + ":" + path);
        } else {
            return path;
        }
    }

    public static class Serializer implements JsonDeserializer<ResourceLocation>, JsonSerializer<ResourceLocation> {
        @Override
        public ResourceLocation deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return ResourceLocation.parse(GsonHelper.convertToString(json, "location"));
        }

        @Override
        public JsonElement serialize(ResourceLocation src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }
    }
}
