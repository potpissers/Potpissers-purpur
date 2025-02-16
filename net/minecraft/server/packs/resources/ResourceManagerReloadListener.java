package net.minecraft.server.packs.resources;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

public interface ResourceManagerReloadListener extends PreparableReloadListener {
    @Override
    default CompletableFuture<Void> reload(
        PreparableReloadListener.PreparationBarrier barrier, ResourceManager manager, Executor backgroundExecutor, Executor gameExecutor
    ) {
        return barrier.wait(Unit.INSTANCE).thenRunAsync(() -> {
            ProfilerFiller profilerFiller = Profiler.get();
            profilerFiller.push("listener");
            this.onResourceManagerReload(manager);
            profilerFiller.pop();
        }, gameExecutor);
    }

    void onResourceManagerReload(ResourceManager resourceManager);
}
