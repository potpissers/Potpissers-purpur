package net.minecraft.server;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.TreeNodePosition;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public class ServerAdvancementManager extends SimpleJsonResourceReloadListener<Advancement> {
    private static final Logger LOGGER = LogUtils.getLogger();
    public Map<ResourceLocation, AdvancementHolder> advancements = new java.util.HashMap<>(); // CraftBukkit - SPIGOT-7734: mutable
    private AdvancementTree tree = new AdvancementTree();
    private final HolderLookup.Provider registries;

    public ServerAdvancementManager(HolderLookup.Provider registries) {
        super(registries, Advancement.CODEC, Registries.ADVANCEMENT);
        this.registries = registries;
    }

    @Override
    protected void apply(Map<ResourceLocation, Advancement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        Builder<ResourceLocation, AdvancementHolder> builder = ImmutableMap.builder();
        object.forEach((resourceLocation, advancement) -> {
            // Spigot start
            if (org.spigotmc.SpigotConfig.disabledAdvancements != null && (org.spigotmc.SpigotConfig.disabledAdvancements.contains("*") || org.spigotmc.SpigotConfig.disabledAdvancements.contains(resourceLocation.toString()) || org.spigotmc.SpigotConfig.disabledAdvancements.contains(resourceLocation.getNamespace()))) {
                return;
            }
            // Spigot end
            this.validate(resourceLocation, advancement);
            builder.put(resourceLocation, new AdvancementHolder(resourceLocation, advancement));
        });
        this.advancements = new java.util.HashMap<>(builder.buildOrThrow()); // CraftBukkit - SPIGOT-7734: mutable
        AdvancementTree advancementTree = new AdvancementTree();
        advancementTree.addAll(this.advancements.values());
        LOGGER.info("Loaded {} advancements", advancementTree.nodes().size()); // Paper - Improve logging and errors; moved from AdvancementTree#addAll

        for (AdvancementNode advancementNode : advancementTree.roots()) {
            if (advancementNode.holder().value().display().isPresent()) {
                TreeNodePosition.run(advancementNode);
            }
        }

        this.tree = advancementTree;
    }

    private void validate(ResourceLocation location, Advancement advancement) {
        ProblemReporter.Collector collector = new ProblemReporter.Collector();
        advancement.validate(collector, this.registries);
        collector.getReport().ifPresent(string -> LOGGER.warn("Found validation problems in advancement {}: \n{}", location, string));
    }

    @Nullable
    public AdvancementHolder get(ResourceLocation location) {
        return this.advancements.get(location);
    }

    public AdvancementTree tree() {
        return this.tree;
    }

    public Collection<AdvancementHolder> getAllAdvancements() {
        return this.advancements.values();
    }
}
