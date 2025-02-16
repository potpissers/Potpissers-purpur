package net.minecraft.core;

import com.google.common.collect.Lists;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.Validate;

public class NonNullList<E> extends AbstractList<E> {
    private final List<E> list;
    @Nullable
    private final E defaultValue;

    public static <E> NonNullList<E> create() {
        return new NonNullList<>(Lists.newArrayList(), null);
    }

    public static <E> NonNullList<E> createWithCapacity(int initialCapacity) {
        return new NonNullList<>(Lists.newArrayListWithCapacity(initialCapacity), null);
    }

    public static <E> NonNullList<E> withSize(int size, E defaultValue) {
        Validate.notNull(defaultValue);
        Object[] objects = new Object[size];
        Arrays.fill(objects, defaultValue);
        return new NonNullList<>(Arrays.asList((E[])objects), defaultValue);
    }

    @SafeVarargs
    public static <E> NonNullList<E> of(E defaultValue, E... elements) {
        return new NonNullList<>(Arrays.asList(elements), defaultValue);
    }

    protected NonNullList(List<E> list, @Nullable E defaultValue) {
        this.list = list;
        this.defaultValue = defaultValue;
    }

    @Nonnull
    @Override
    public E get(int index) {
        return this.list.get(index);
    }

    @Override
    public E set(int index, E value) {
        Validate.notNull(value);
        return this.list.set(index, value);
    }

    @Override
    public void add(int index, E value) {
        Validate.notNull(value);
        this.list.add(index, value);
    }

    @Override
    public E remove(int index) {
        return this.list.remove(index);
    }

    @Override
    public int size() {
        return this.list.size();
    }

    @Override
    public void clear() {
        if (this.defaultValue == null) {
            super.clear();
        } else {
            for (int i = 0; i < this.size(); i++) {
                this.set(i, this.defaultValue);
            }
        }
    }
}
