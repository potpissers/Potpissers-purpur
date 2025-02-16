package net.minecraft.server.packs.resources;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.Util;
import net.minecraft.util.Unit;

public class SimpleReloadInstance<S> implements ReloadInstance {
    private static final int PREPARATION_PROGRESS_WEIGHT = 2;
    private static final int EXTRA_RELOAD_PROGRESS_WEIGHT = 2;
    private static final int LISTENER_PROGRESS_WEIGHT = 1;
    protected final CompletableFuture<Unit> allPreparations = new CompletableFuture<>();
    protected CompletableFuture<List<S>> allDone;
    final Set<PreparableReloadListener> preparingListeners;
    private final int listenerCount;
    private int startedReloads;
    private int finishedReloads;
    private final AtomicInteger startedTaskCounter = new AtomicInteger();
    private final AtomicInteger doneTaskCounter = new AtomicInteger();

    public static SimpleReloadInstance<Void> of(
        ResourceManager resourceManager,
        List<PreparableReloadListener> listeners,
        Executor backgroundExecutor,
        Executor gameExecutor,
        CompletableFuture<Unit> alsoWaitedFor
    ) {
        return new SimpleReloadInstance<>(
            backgroundExecutor,
            gameExecutor,
            resourceManager,
            listeners,
            (barrier, manager, listener, backgroundExecutor1, gameExecutor1) -> listener.reload(barrier, manager, backgroundExecutor, gameExecutor1),
            alsoWaitedFor
        );
    }

    protected SimpleReloadInstance(
        Executor backgroundExecutor,
        final Executor gameExecutor,
        ResourceManager resourceManager,
        List<PreparableReloadListener> listeners,
        SimpleReloadInstance.StateFactory<S> stateFactory,
        CompletableFuture<Unit> alsoWaitedFor
    ) {
        this.listenerCount = listeners.size();
        this.startedTaskCounter.incrementAndGet();
        alsoWaitedFor.thenRun(this.doneTaskCounter::incrementAndGet);
        List<CompletableFuture<S>> list = Lists.newArrayList();
        CompletableFuture<?> completableFuture = alsoWaitedFor;
        this.preparingListeners = Sets.newHashSet(listeners);

        for (final PreparableReloadListener preparableReloadListener : listeners) {
            final CompletableFuture<?> completableFuture1 = completableFuture;
            CompletableFuture<S> completableFuture2 = stateFactory.create(
                new PreparableReloadListener.PreparationBarrier() {
                    @Override
                    public <T> CompletableFuture<T> wait(T backgroundResult) {
                        gameExecutor.execute(() -> {
                            SimpleReloadInstance.this.preparingListeners.remove(preparableReloadListener);
                            if (SimpleReloadInstance.this.preparingListeners.isEmpty()) {
                                SimpleReloadInstance.this.allPreparations.complete(Unit.INSTANCE);
                            }
                        });
                        return SimpleReloadInstance.this.allPreparations
                            .thenCombine((CompletionStage<? extends T>)completableFuture1, (unit, object) -> backgroundResult);
                    }
                },
                resourceManager,
                preparableReloadListener,
                task -> {
                    this.startedTaskCounter.incrementAndGet();
                    backgroundExecutor.execute(() -> {
                        task.run();
                        this.doneTaskCounter.incrementAndGet();
                    });
                },
                task -> {
                    this.startedReloads++;
                    gameExecutor.execute(() -> {
                        task.run();
                        this.finishedReloads++;
                    });
                }
            );
            list.add(completableFuture2);
            completableFuture = completableFuture2;
        }

        this.allDone = Util.sequenceFailFast(list);
    }

    @Override
    public CompletableFuture<?> done() {
        return this.allDone;
    }

    @Override
    public float getActualProgress() {
        int i = this.listenerCount - this.preparingListeners.size();
        float f = this.doneTaskCounter.get() * 2 + this.finishedReloads * 2 + i * 1;
        float f1 = this.startedTaskCounter.get() * 2 + this.startedReloads * 2 + this.listenerCount * 1;
        return f / f1;
    }

    public static ReloadInstance create(
        ResourceManager resourceManager,
        List<PreparableReloadListener> listeners,
        Executor backgroundExecutor,
        Executor gameExecutor,
        CompletableFuture<Unit> alsoWaitedFor,
        boolean profiled
    ) {
        return (ReloadInstance)(profiled
            ? new ProfiledReloadInstance(resourceManager, listeners, backgroundExecutor, gameExecutor, alsoWaitedFor)
            : of(resourceManager, listeners, backgroundExecutor, gameExecutor, alsoWaitedFor));
    }

    protected interface StateFactory<S> {
        CompletableFuture<S> create(
            PreparableReloadListener.PreparationBarrier preperationBarrier,
            ResourceManager resourceManager,
            PreparableReloadListener listener,
            Executor backgroundExecutor,
            Executor gameExecutor
        );
    }
}
