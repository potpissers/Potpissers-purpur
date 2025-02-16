package net.minecraft.commands.execution;

import javax.annotation.Nullable;
import net.minecraft.commands.ExecutionCommandSource;

public interface ExecutionControl<T> {
    void queueNext(EntryAction<T> entry);

    void tracer(@Nullable TraceCallbacks tracer);

    @Nullable
    TraceCallbacks tracer();

    Frame currentFrame();

    static <T extends ExecutionCommandSource<T>> ExecutionControl<T> create(final ExecutionContext<T> executionContext, final Frame frame) {
        return new ExecutionControl<T>() {
            @Override
            public void queueNext(EntryAction<T> entry) {
                executionContext.queueNext(new CommandQueueEntry<>(frame, entry));
            }

            @Override
            public void tracer(@Nullable TraceCallbacks tracer) {
                executionContext.tracer(tracer);
            }

            @Nullable
            @Override
            public TraceCallbacks tracer() {
                return executionContext.tracer();
            }

            @Override
            public Frame currentFrame() {
                return frame;
            }
        };
    }
}
