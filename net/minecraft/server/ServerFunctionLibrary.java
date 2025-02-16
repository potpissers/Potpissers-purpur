package net.minecraft.server;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class ServerFunctionLibrary implements PreparableReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceKey<Registry<CommandFunction<CommandSourceStack>>> TYPE_KEY = ResourceKey.createRegistryKey(
        ResourceLocation.withDefaultNamespace("function")
    );
    private static final FileToIdConverter LISTER = new FileToIdConverter(Registries.elementsDirPath(TYPE_KEY), ".mcfunction");
    private volatile Map<ResourceLocation, CommandFunction<CommandSourceStack>> functions = ImmutableMap.of();
    private final TagLoader<CommandFunction<CommandSourceStack>> tagsLoader = new TagLoader<>(
        (id, required) -> this.getFunction(id), Registries.tagsDirPath(TYPE_KEY)
    );
    private volatile Map<ResourceLocation, List<CommandFunction<CommandSourceStack>>> tags = Map.of();
    private final int functionCompilationLevel;
    private final CommandDispatcher<CommandSourceStack> dispatcher;

    public Optional<CommandFunction<CommandSourceStack>> getFunction(ResourceLocation location) {
        return Optional.ofNullable(this.functions.get(location));
    }

    public Map<ResourceLocation, CommandFunction<CommandSourceStack>> getFunctions() {
        return this.functions;
    }

    public List<CommandFunction<CommandSourceStack>> getTag(ResourceLocation location) {
        return this.tags.getOrDefault(location, List.of());
    }

    public Iterable<ResourceLocation> getAvailableTags() {
        return this.tags.keySet();
    }

    public ServerFunctionLibrary(int functionCompilationLevel, CommandDispatcher<CommandSourceStack> dispatcher) {
        this.functionCompilationLevel = functionCompilationLevel;
        this.dispatcher = dispatcher;
    }

    @Override
    public CompletableFuture<Void> reload(
        PreparableReloadListener.PreparationBarrier barrier, ResourceManager manager, Executor backgroundExecutor, Executor gameExecutor
    ) {
        CompletableFuture<Map<ResourceLocation, List<TagLoader.EntryWithSource>>> completableFuture = CompletableFuture.supplyAsync(
            () -> this.tagsLoader.load(manager), backgroundExecutor
        );
        CompletableFuture<Map<ResourceLocation, CompletableFuture<CommandFunction<CommandSourceStack>>>> completableFuture1 = CompletableFuture.<Map<ResourceLocation, Resource>>supplyAsync(
                () -> LISTER.listMatchingResources(manager), backgroundExecutor
            )
            .thenCompose(
                map -> {
                    Map<ResourceLocation, CompletableFuture<CommandFunction<CommandSourceStack>>> map1 = Maps.newHashMap();
                    CommandSourceStack commandSourceStack = new CommandSourceStack(
                        CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, this.functionCompilationLevel, "", CommonComponents.EMPTY, null, null
                    );

                    for (Entry<ResourceLocation, Resource> entry : map.entrySet()) {
                        ResourceLocation resourceLocation = entry.getKey();
                        ResourceLocation resourceLocation1 = LISTER.fileToId(resourceLocation);
                        map1.put(resourceLocation1, CompletableFuture.supplyAsync(() -> {
                            List<String> lines = readLines(entry.getValue());
                            return CommandFunction.fromLines(resourceLocation1, this.dispatcher, commandSourceStack, lines);
                        }, backgroundExecutor));
                    }

                    CompletableFuture<?>[] completableFutures = map1.values().toArray(new CompletableFuture[0]);
                    return CompletableFuture.allOf(completableFutures).handle((_void, throwable) -> map1);
                }
            );
        return completableFuture.thenCombine(completableFuture1, Pair::of)
            .thenCompose(barrier::wait)
            .thenAcceptAsync(
                pair -> {
                    Map<ResourceLocation, CompletableFuture<CommandFunction<CommandSourceStack>>> map = (Map<ResourceLocation, CompletableFuture<CommandFunction<CommandSourceStack>>>)pair.getSecond();
                    Builder<ResourceLocation, CommandFunction<CommandSourceStack>> builder = ImmutableMap.builder();
                    map.forEach((resourceLocation, completableFuture2) -> completableFuture2.handle((commandFunction, throwable) -> {
                        if (throwable != null) {
                            LOGGER.error("Failed to load function {}", resourceLocation, throwable);
                        } else {
                            builder.put(resourceLocation, commandFunction);
                        }

                        return null;
                    }).join());
                    this.functions = builder.build();
                    this.tags = this.tagsLoader.build((Map<ResourceLocation, List<TagLoader.EntryWithSource>>)pair.getFirst());
                },
                gameExecutor
            );
    }

    private static List<String> readLines(Resource resource) {
        try {
            List var2;
            try (BufferedReader bufferedReader = resource.openAsReader()) {
                var2 = bufferedReader.lines().toList();
            }

            return var2;
        } catch (IOException var6) {
            throw new CompletionException(var6);
        }
    }
}
