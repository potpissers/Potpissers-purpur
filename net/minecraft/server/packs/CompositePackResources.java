package net.minecraft.server.packs;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;

public class CompositePackResources implements PackResources {
    private final PackResources primaryPackResources;
    private final List<PackResources> packResourcesStack;

    public CompositePackResources(PackResources primaryPackResources, List<PackResources> packResourcesStack) {
        this.primaryPackResources = primaryPackResources;
        List<PackResources> list = new ArrayList<>(packResourcesStack.size() + 1);
        list.addAll(Lists.reverse(packResourcesStack));
        list.add(primaryPackResources);
        this.packResourcesStack = List.copyOf(list);
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        return this.primaryPackResources.getRootResource(elements);
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
        for (PackResources packResources : this.packResourcesStack) {
            IoSupplier<InputStream> resource = packResources.getResource(packType, location);
            if (resource != null) {
                return resource;
            }
        }

        return null;
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, PackResources.ResourceOutput resourceOutput) {
        Map<ResourceLocation, IoSupplier<InputStream>> map = new HashMap<>();

        for (PackResources packResources : this.packResourcesStack) {
            packResources.listResources(packType, namespace, path, map::putIfAbsent);
        }

        map.forEach(resourceOutput);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> set = new HashSet<>();

        for (PackResources packResources : this.packResourcesStack) {
            set.addAll(packResources.getNamespaces(type));
        }

        return set;
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionType<T> type) throws IOException {
        return this.primaryPackResources.getMetadataSection(type);
    }

    @Override
    public PackLocationInfo location() {
        return this.primaryPackResources.location();
    }

    @Override
    public void close() {
        this.packResourcesStack.forEach(PackResources::close);
    }
}
