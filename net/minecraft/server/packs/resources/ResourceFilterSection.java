package net.minecraft.server.packs.resources;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.util.ResourceLocationPattern;

public class ResourceFilterSection {
    private static final Codec<ResourceFilterSection> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                Codec.list(ResourceLocationPattern.CODEC).fieldOf("block").forGetter(resourceFilterSection -> resourceFilterSection.blockList)
            )
            .apply(instance, ResourceFilterSection::new)
    );
    public static final MetadataSectionType<ResourceFilterSection> TYPE = new MetadataSectionType<>("filter", CODEC);
    private final List<ResourceLocationPattern> blockList;

    public ResourceFilterSection(List<ResourceLocationPattern> blockList) {
        this.blockList = List.copyOf(blockList);
    }

    public boolean isNamespaceFiltered(String namespace) {
        return this.blockList.stream().anyMatch(pattern -> pattern.namespacePredicate().test(namespace));
    }

    public boolean isPathFiltered(String path) {
        return this.blockList.stream().anyMatch(pattern -> pattern.pathPredicate().test(path));
    }
}
