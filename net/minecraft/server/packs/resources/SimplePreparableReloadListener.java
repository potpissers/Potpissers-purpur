package net.minecraft.server.packs.resources;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

public abstract class SimplePreparableReloadListener<T> implements PreparableReloadListener {
    @Override
    public final CompletableFuture<Void> reload(
        PreparableReloadListener.PreparationBarrier barrier, ResourceManager manager, Executor backgroundExecutor, Executor gameExecutor
    ) {
        return CompletableFuture.<T>supplyAsync(() -> this.prepare(manager, Profiler.get()), backgroundExecutor)
            .thenCompose(barrier::wait)
            .thenAcceptAsync(object -> this.apply((T)object, manager, Profiler.get()), gameExecutor);
    }

    protected abstract T prepare(ResourceManager resourceManager, ProfilerFiller profiler);

    protected abstract void apply(T object, ResourceManager resourceManager, ProfilerFiller profiler);
}
