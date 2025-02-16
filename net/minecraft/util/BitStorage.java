package net.minecraft.util;

import java.util.function.IntConsumer;

public interface BitStorage extends ca.spottedleaf.moonrise.patches.block_counting.BlockCountingBitStorage { // Paper - block counting
    int getAndSet(int index, int value);

    void set(int index, int value);

    int get(int index);

    long[] getRaw();

    int getSize();

    int getBits();

    void getAll(IntConsumer consumer);

    void unpack(int[] array);

    BitStorage copy();

    // Paper start - block counting
    // provide default impl in case mods implement this...
    @Override
    public default it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<it.unimi.dsi.fastutil.shorts.ShortArrayList> moonrise$countEntries() {
        final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<it.unimi.dsi.fastutil.shorts.ShortArrayList> ret = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();

        final int size = this.getSize();
        for (int index = 0; index < size; ++index) {
            final int paletteIdx = this.get(index);
            ret.computeIfAbsent(paletteIdx, (final int key) -> {
                return new it.unimi.dsi.fastutil.shorts.ShortArrayList();
            }).add((short)index);
        }

        return ret;
    }
    // Paper end - block counting
}
