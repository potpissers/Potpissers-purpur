package net.minecraft.server.packs.resources;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface PreparableReloadListener {
    CompletableFuture<Void> reload(
        PreparableReloadListener.PreparationBarrier barrier, ResourceManager manager, Executor backgroundExecutor, Executor gameExecutor
    );

    default String getName() {
        return this.getClass().getSimpleName();
    }

    public interface PreparationBarrier {
        <T> CompletableFuture<T> wait(T backgroundResult);
    }
}
