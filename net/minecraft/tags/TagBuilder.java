package net.minecraft.tags;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public class TagBuilder {
    private final List<TagEntry> entries = new ArrayList<>();

    public static TagBuilder create() {
        return new TagBuilder();
    }

    public List<TagEntry> build() {
        return List.copyOf(this.entries);
    }

    public TagBuilder add(TagEntry entry) {
        this.entries.add(entry);
        return this;
    }

    public TagBuilder addElement(ResourceLocation elementLocation) {
        return this.add(TagEntry.element(elementLocation));
    }

    public TagBuilder addOptionalElement(ResourceLocation elementLocation) {
        return this.add(TagEntry.optionalElement(elementLocation));
    }

    public TagBuilder addTag(ResourceLocation tagLocation) {
        return this.add(TagEntry.tag(tagLocation));
    }

    public TagBuilder addOptionalTag(ResourceLocation tagLocation) {
        return this.add(TagEntry.optionalTag(tagLocation));
    }
}
