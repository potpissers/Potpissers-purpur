package net.minecraft.server.packs.resources;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.util.GsonHelper;

public interface ResourceMetadata {
    ResourceMetadata EMPTY = new ResourceMetadata() {
        @Override
        public <T> Optional<T> getSection(MetadataSectionType<T> type) {
            return Optional.empty();
        }
    };
    IoSupplier<ResourceMetadata> EMPTY_SUPPLIER = () -> EMPTY;

    static ResourceMetadata fromJsonStream(InputStream stream) throws IOException {
        ResourceMetadata var3;
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            final JsonObject jsonObject = GsonHelper.parse(bufferedReader);
            var3 = new ResourceMetadata() {
                @Override
                public <T> Optional<T> getSection(MetadataSectionType<T> type) {
                    String string = type.name();
                    if (jsonObject.has(string)) {
                        T orThrow = type.codec().parse(JsonOps.INSTANCE, jsonObject.get(string)).getOrThrow(JsonParseException::new);
                        return Optional.of(orThrow);
                    } else {
                        return Optional.empty();
                    }
                }
            };
        }

        return var3;
    }

    <T> Optional<T> getSection(MetadataSectionType<T> type);

    default ResourceMetadata copySections(Collection<MetadataSectionType<?>> serializers) {
        ResourceMetadata.Builder builder = new ResourceMetadata.Builder();

        for (MetadataSectionType<?> metadataSectionType : serializers) {
            this.copySection(builder, metadataSectionType);
        }

        return builder.build();
    }

    private <T> void copySection(ResourceMetadata.Builder builder, MetadataSectionType<T> type) {
        this.getSection(type).ifPresent(object -> builder.put(type, (T)object));
    }

    public static class Builder {
        private final ImmutableMap.Builder<MetadataSectionType<?>, Object> map = ImmutableMap.builder();

        public <T> ResourceMetadata.Builder put(MetadataSectionType<T> type, T value) {
            this.map.put(type, value);
            return this;
        }

        public ResourceMetadata build() {
            final ImmutableMap<MetadataSectionType<?>, Object> map = this.map.build();
            return map.isEmpty() ? ResourceMetadata.EMPTY : new ResourceMetadata() {
                @Override
                public <T> Optional<T> getSection(MetadataSectionType<T> type) {
                    return Optional.ofNullable((T)map.get(type));
                }
            };
        }
    }
}
