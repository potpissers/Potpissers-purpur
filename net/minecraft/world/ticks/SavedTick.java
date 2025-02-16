package net.minecraft.world.ticks;

import it.unimi.dsi.fastutil.Hash.Strategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;

public record SavedTick<T>(T type, BlockPos pos, int delay, TickPriority priority) {
    private static final String TAG_ID = "i";
    private static final String TAG_X = "x";
    private static final String TAG_Y = "y";
    private static final String TAG_Z = "z";
    private static final String TAG_DELAY = "t";
    private static final String TAG_PRIORITY = "p";
    public static final Strategy<SavedTick<?>> UNIQUE_TICK_HASH = new Strategy<SavedTick<?>>() {
        @Override
        public int hashCode(SavedTick<?> savedTick) {
            return 31 * savedTick.pos().hashCode() + savedTick.type().hashCode();
        }

        @Override
        public boolean equals(@Nullable SavedTick<?> first, @Nullable SavedTick<?> second) {
            return first == second || first != null && second != null && first.type() == second.type() && first.pos().equals(second.pos());
        }
    };

    public static <T> List<SavedTick<T>> loadTickList(ListTag tickList, Function<String, Optional<T>> idParser, ChunkPos chunkPos) {
        List<SavedTick<T>> list = new ArrayList<>(tickList.size());
        long packedChunkPos = chunkPos.toLong();

        for (int i = 0; i < tickList.size(); i++) {
            CompoundTag compound = tickList.getCompound(i);
            loadTick(compound, idParser).ifPresent(savedTick -> {
                if (ChunkPos.asLong(savedTick.pos()) == packedChunkPos) {
                    list.add((SavedTick<T>)savedTick);
                }
            });
        }

        return list;
    }

    public static <T> Optional<SavedTick<T>> loadTick(CompoundTag tag, Function<String, Optional<T>> idParser) {
        return idParser.apply(tag.getString("i")).map(object -> {
            BlockPos blockPos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            return new SavedTick<>((T)object, blockPos, tag.getInt("t"), TickPriority.byValue(tag.getInt("p")));
        });
    }

    private static CompoundTag saveTick(String id, BlockPos pos, int delay, TickPriority priority) {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("i", id);
        compoundTag.putInt("x", pos.getX());
        compoundTag.putInt("y", pos.getY());
        compoundTag.putInt("z", pos.getZ());
        compoundTag.putInt("t", delay);
        compoundTag.putInt("p", priority.getValue());
        return compoundTag;
    }

    public CompoundTag save(Function<T, String> idGetter) {
        return saveTick(idGetter.apply(this.type), this.pos, this.delay, this.priority);
    }

    public ScheduledTick<T> unpack(long gameTime, long subTickOrder) {
        return new ScheduledTick<>(this.type, this.pos, gameTime + this.delay, this.priority, subTickOrder);
    }

    public static <T> SavedTick<T> probe(T type, BlockPos pos) {
        return new SavedTick<>(type, pos, 0, TickPriority.NORMAL);
    }
}
