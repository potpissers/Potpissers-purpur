package net.minecraft.world.level.gameevent;

import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class DynamicGameEventListener<T extends GameEventListener> {
    private final T listener;
    @Nullable
    private SectionPos lastSection;

    public DynamicGameEventListener(T listener) {
        this.listener = listener;
    }

    public void add(ServerLevel level) {
        this.move(level);
    }

    public T getListener() {
        return this.listener;
    }

    public void remove(ServerLevel level) {
        ifChunkExists(level, this.lastSection, listenerRegistry -> listenerRegistry.unregister(this.listener));
    }

    public void move(ServerLevel level) {
        this.listener.getListenerSource().getPosition(level).map(SectionPos::of).ifPresent(sectionPos -> {
            if (this.lastSection == null || !this.lastSection.equals(sectionPos)) {
                ifChunkExists(level, this.lastSection, listenerRegistry -> listenerRegistry.unregister(this.listener));
                this.lastSection = sectionPos;
                ifChunkExists(level, this.lastSection, listenerRegistry -> listenerRegistry.register(this.listener));
            }
        });
    }

    private static void ifChunkExists(LevelReader level, @Nullable SectionPos sectionPos, Consumer<GameEventListenerRegistry> dispatcherConsumer) {
        if (sectionPos != null) {
            ChunkAccess chunk = level.getChunkIfLoadedImmediately(sectionPos.getX(), sectionPos.getZ()); // Paper - Perf: can cause sync loads while completing a chunk, resulting in deadlock
            if (chunk != null) {
                dispatcherConsumer.accept(chunk.getListenerRegistry(sectionPos.y()));
            }
        }
    }
}
