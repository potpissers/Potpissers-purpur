package net.minecraft.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class DebugBuffer<T> {
    private final AtomicReferenceArray<T> data;
    private final AtomicInteger index;

    public DebugBuffer(int length) {
        this.data = new AtomicReferenceArray<>(length);
        this.index = new AtomicInteger(0);
    }

    public void push(T value) {
        int len = this.data.length();

        int i;
        int i1;
        do {
            i = this.index.get();
            i1 = (i + 1) % len;
        } while (!this.index.compareAndSet(i, i1));

        this.data.set(i1, value);
    }

    public List<T> dump() {
        int i = this.index.get();
        Builder<T> builder = ImmutableList.builder();

        for (int i1 = 0; i1 < this.data.length(); i1++) {
            int i2 = Math.floorMod(i - i1, this.data.length());
            T object = this.data.get(i2);
            if (object != null) {
                builder.add(object);
            }
        }

        return builder.build();
    }
}
