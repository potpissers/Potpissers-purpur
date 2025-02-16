package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.AbstractDoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleList;

public class OffsetDoubleList extends AbstractDoubleList {
    private final DoubleList delegate;
    private final double offset;

    public OffsetDoubleList(DoubleList delegate, double offset) {
        this.delegate = delegate;
        this.offset = offset;
    }

    @Override
    public double getDouble(int value) {
        return this.delegate.getDouble(value) + this.offset;
    }

    @Override
    public int size() {
        return this.delegate.size();
    }
}
