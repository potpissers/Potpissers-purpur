package net.minecraft.server.level.progress;

import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class ProcessorChunkProgressListener implements ChunkProgressListener {
    private final ChunkProgressListener delegate;
    private final ConsecutiveExecutor consecutiveExecutor;
    private boolean started;

    private ProcessorChunkProgressListener(ChunkProgressListener delegate, Executor dispatcher) {
        this.delegate = delegate;
        this.consecutiveExecutor = new ConsecutiveExecutor(dispatcher, "progressListener");
    }

    public static ProcessorChunkProgressListener createStarted(ChunkProgressListener delegate, Executor dispatcher) {
        ProcessorChunkProgressListener processorChunkProgressListener = new ProcessorChunkProgressListener(delegate, dispatcher);
        processorChunkProgressListener.start();
        return processorChunkProgressListener;
    }

    @Override
    public void updateSpawnPos(ChunkPos center) {
        this.consecutiveExecutor.schedule(() -> this.delegate.updateSpawnPos(center));
    }

    @Override
    public void onStatusChange(ChunkPos chunkPos, @Nullable ChunkStatus chunkStatus) {
        if (this.started) {
            this.consecutiveExecutor.schedule(() -> this.delegate.onStatusChange(chunkPos, chunkStatus));
        }
    }

    @Override
    public void start() {
        this.started = true;
        this.consecutiveExecutor.schedule(this.delegate::start);
    }

    @Override
    public void stop() {
        this.started = false;
        this.consecutiveExecutor.schedule(this.delegate::stop);
    }
}
