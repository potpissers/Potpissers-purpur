package net.minecraft.data.info;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

public class RegistryDumpReport implements DataProvider {
    private final PackOutput output;

    public RegistryDumpReport(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        JsonObject jsonObject = new JsonObject();
        BuiltInRegistries.REGISTRY
            .listElements()
            .forEach(holder -> jsonObject.add(holder.key().location().toString(), dumpRegistry((Registry<?>)holder.value())));
        Path path = this.output.getOutputFolder(PackOutput.Target.REPORTS).resolve("registries.json");
        return DataProvider.saveStable(output, jsonObject, path);
    }

    private static <T> JsonElement dumpRegistry(Registry<T> registry) {
        JsonObject jsonObject = new JsonObject();
        if (registry instanceof DefaultedRegistry) {
            ResourceLocation defaultKey = ((DefaultedRegistry)registry).getDefaultKey();
            jsonObject.addProperty("default", defaultKey.toString());
        }

        int id = BuiltInRegistries.REGISTRY.getId(registry);
        jsonObject.addProperty("protocol_id", id);
        JsonObject jsonObject1 = new JsonObject();
        registry.listElements().forEach(holder -> {
            T object = holder.value();
            int id1 = registry.getId(object);
            JsonObject jsonObject2 = new JsonObject();
            jsonObject2.addProperty("protocol_id", id1);
            jsonObject1.add(holder.key().location().toString(), jsonObject2);
        });
        jsonObject.add("entries", jsonObject1);
        return jsonObject;
    }

    @Override
    public final String getName() {
        return "Registry Dump";
    }
}
