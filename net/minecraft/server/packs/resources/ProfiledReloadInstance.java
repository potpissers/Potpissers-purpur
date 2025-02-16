package net.minecraft.server.packs.resources;

import com.google.common.base.Stopwatch;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.Util;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public class ProfiledReloadInstance extends SimpleReloadInstance<ProfiledReloadInstance.State> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Stopwatch total = Stopwatch.createUnstarted();

    public ProfiledReloadInstance(
        ResourceManager resourceManager,
        List<PreparableReloadListener> listeners,
        Executor backgroundExecutor,
        Executor gameExecutor,
        CompletableFuture<Unit> alsoWaitedFor
    ) {
        super(
            backgroundExecutor,
            gameExecutor,
            resourceManager,
            listeners,
            (barrier, manager, preparableReloadListener, executor, executor1) -> {
                AtomicLong atomicLong = new AtomicLong();
                AtomicLong atomicLong1 = new AtomicLong();
                CompletableFuture<Void> completableFuture = preparableReloadListener.reload(
                    barrier,
                    manager,
                    profiledExecutor(executor, atomicLong, preparableReloadListener.getName()),
                    profiledExecutor(executor1, atomicLong1, preparableReloadListener.getName())
                );
                return completableFuture.thenApplyAsync(_void -> {
                    LOGGER.debug("Finished reloading {}", preparableReloadListener.getName());
                    return new ProfiledReloadInstance.State(preparableReloadListener.getName(), atomicLong, atomicLong1);
                }, gameExecutor);
            },
            alsoWaitedFor
        );
        this.total.start();
        this.allDone = this.allDone.thenApplyAsync(this::finish, gameExecutor);
    }

    private static Executor profiledExecutor(Executor executor, AtomicLong timeTaken, String name) {
        return runnable -> executor.execute(() -> {
            ProfilerFiller profilerFiller = Profiler.get();
            profilerFiller.push(name);
            long nanos = Util.getNanos();
            runnable.run();
            timeTaken.addAndGet(Util.getNanos() - nanos);
            profilerFiller.pop();
        });
    }

    private List<ProfiledReloadInstance.State> finish(List<ProfiledReloadInstance.State> datapoints) {
        this.total.stop();
        long l = 0L;
        LOGGER.info("Resource reload finished after {} ms", this.total.elapsed(TimeUnit.MILLISECONDS));

        for (ProfiledReloadInstance.State state : datapoints) {
            long l1 = TimeUnit.NANOSECONDS.toMillis(state.preparationNanos.get());
            long l2 = TimeUnit.NANOSECONDS.toMillis(state.reloadNanos.get());
            long l3 = l1 + l2;
            String string = state.name;
            LOGGER.info("{} took approximately {} ms ({} ms preparing, {} ms applying)", string, l3, l1, l2);
            l += l2;
        }

        LOGGER.info("Total blocking time: {} ms", l);
        return datapoints;
    }

    public record State(String name, AtomicLong preparationNanos, AtomicLong reloadNanos) {
    }
}
