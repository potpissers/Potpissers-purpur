package net.minecraft.server.packs.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public abstract class SimpleJsonResourceReloadListener<T> extends SimplePreparableReloadListener<Map<ResourceLocation, T>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final DynamicOps<JsonElement> ops;
    private final Codec<T> codec;
    private final FileToIdConverter lister;

    protected SimpleJsonResourceReloadListener(HolderLookup.Provider provider, Codec<T> codec, ResourceKey<? extends Registry<T>> registryKey) {
        this(provider.createSerializationContext(JsonOps.INSTANCE), codec, FileToIdConverter.registry(registryKey));
    }

    protected SimpleJsonResourceReloadListener(Codec<T> codec, FileToIdConverter lister) {
        this(JsonOps.INSTANCE, codec, lister);
    }

    private SimpleJsonResourceReloadListener(DynamicOps<JsonElement> ops, Codec<T> codec, FileToIdConverter lister) {
        this.ops = ops;
        this.codec = codec;
        this.lister = lister;
    }

    @Override
    protected Map<ResourceLocation, T> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, T> map = new HashMap<>();
        scanDirectory(resourceManager, this.lister, this.ops, this.codec, map);
        return map;
    }

    public static <T> void scanDirectory(
        ResourceManager resourceManager,
        ResourceKey<? extends Registry<T>> registryKey,
        DynamicOps<JsonElement> ops,
        Codec<T> codec,
        Map<ResourceLocation, T> output
    ) {
        scanDirectory(resourceManager, FileToIdConverter.registry(registryKey), ops, codec, output);
    }

    public static <T> void scanDirectory(
        ResourceManager resourceManager, FileToIdConverter lister, DynamicOps<JsonElement> ops, Codec<T> codec, Map<ResourceLocation, T> output
    ) {
        for (Entry<ResourceLocation, Resource> entry : lister.listMatchingResources(resourceManager).entrySet()) {
            ResourceLocation resourceLocation = entry.getKey();
            ResourceLocation resourceLocation1 = lister.fileToId(resourceLocation);

            try (Reader reader = entry.getValue().openAsReader()) {
                codec.parse(ops, JsonParser.parseReader(reader)).ifSuccess(object -> {
                    if (output.putIfAbsent(resourceLocation1, (T)object) != null) {
                        throw new IllegalStateException("Duplicate data file ignored with ID " + resourceLocation1);
                    }
                }).ifError(error -> LOGGER.error("Couldn't parse data file '{}' from '{}': {}", resourceLocation1, resourceLocation, error));
            } catch (IllegalArgumentException | IOException | JsonParseException var14) {
                LOGGER.error("Couldn't parse data file '{}' from '{}'", resourceLocation1, resourceLocation, var14);
            }
        }
    }
}
