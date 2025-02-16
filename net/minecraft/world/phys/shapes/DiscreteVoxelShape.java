package net.minecraft.world.phys.shapes;

import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;

public abstract class DiscreteVoxelShape {
    private static final Direction.Axis[] AXIS_VALUES = Direction.Axis.values();
    protected final int xSize;
    protected final int ySize;
    protected final int zSize;

    protected DiscreteVoxelShape(int xSize, int ySize, int zSize) {
        if (xSize >= 0 && ySize >= 0 && zSize >= 0) {
            this.xSize = xSize;
            this.ySize = ySize;
            this.zSize = zSize;
        } else {
            throw new IllegalArgumentException("Need all positive sizes: x: " + xSize + ", y: " + ySize + ", z: " + zSize);
        }
    }

    public boolean isFullWide(AxisCycle axis, int x, int y, int z) {
        return this.isFullWide(axis.cycle(x, y, z, Direction.Axis.X), axis.cycle(x, y, z, Direction.Axis.Y), axis.cycle(x, y, z, Direction.Axis.Z));
    }

    public boolean isFullWide(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < this.xSize && y < this.ySize && z < this.zSize && this.isFull(x, y, z);
    }

    public boolean isFull(AxisCycle rotation, int x, int y, int z) {
        return this.isFull(rotation.cycle(x, y, z, Direction.Axis.X), rotation.cycle(x, y, z, Direction.Axis.Y), rotation.cycle(x, y, z, Direction.Axis.Z));
    }

    public abstract boolean isFull(int x, int y, int z);

    public abstract void fill(int x, int y, int z);

    public boolean isEmpty() {
        for (Direction.Axis axis : AXIS_VALUES) {
            if (this.firstFull(axis) >= this.lastFull(axis)) {
                return true;
            }
        }

        return false;
    }

    public abstract int firstFull(Direction.Axis axis);

    public abstract int lastFull(Direction.Axis axis);

    public int firstFull(Direction.Axis axis, int y, int z) {
        int size = this.getSize(axis);
        if (y >= 0 && z >= 0) {
            Direction.Axis axis1 = AxisCycle.FORWARD.cycle(axis);
            Direction.Axis axis2 = AxisCycle.BACKWARD.cycle(axis);
            if (y < this.getSize(axis1) && z < this.getSize(axis2)) {
                AxisCycle axisCycle = AxisCycle.between(Direction.Axis.X, axis);

                for (int i = 0; i < size; i++) {
                    if (this.isFull(axisCycle, i, y, z)) {
                        return i;
                    }
                }

                return size;
            } else {
                return size;
            }
        } else {
            return size;
        }
    }

    public int lastFull(Direction.Axis axis, int y, int z) {
        if (y >= 0 && z >= 0) {
            Direction.Axis axis1 = AxisCycle.FORWARD.cycle(axis);
            Direction.Axis axis2 = AxisCycle.BACKWARD.cycle(axis);
            if (y < this.getSize(axis1) && z < this.getSize(axis2)) {
                int size = this.getSize(axis);
                AxisCycle axisCycle = AxisCycle.between(Direction.Axis.X, axis);

                for (int i = size - 1; i >= 0; i--) {
                    if (this.isFull(axisCycle, i, y, z)) {
                        return i + 1;
                    }
                }

                return 0;
            } else {
                return 0;
            }
        } else {
            return 0;
        }
    }

    public int getSize(Direction.Axis axis) {
        return axis.choose(this.xSize, this.ySize, this.zSize);
    }

    public int getXSize() {
        return this.getSize(Direction.Axis.X);
    }

    public int getYSize() {
        return this.getSize(Direction.Axis.Y);
    }

    public int getZSize() {
        return this.getSize(Direction.Axis.Z);
    }

    public void forAllEdges(DiscreteVoxelShape.IntLineConsumer consumer, boolean combine) {
        this.forAllAxisEdges(consumer, AxisCycle.NONE, combine);
        this.forAllAxisEdges(consumer, AxisCycle.FORWARD, combine);
        this.forAllAxisEdges(consumer, AxisCycle.BACKWARD, combine);
    }

    private void forAllAxisEdges(DiscreteVoxelShape.IntLineConsumer lineConsumer, AxisCycle axis, boolean combine) {
        AxisCycle axisCycle = axis.inverse();
        int size = this.getSize(axisCycle.cycle(Direction.Axis.X));
        int size1 = this.getSize(axisCycle.cycle(Direction.Axis.Y));
        int size2 = this.getSize(axisCycle.cycle(Direction.Axis.Z));

        for (int i = 0; i <= size; i++) {
            for (int i1 = 0; i1 <= size1; i1++) {
                int i2 = -1;

                for (int i3 = 0; i3 <= size2; i3++) {
                    int i4 = 0;
                    int i5 = 0;

                    for (int i6 = 0; i6 <= 1; i6++) {
                        for (int i7 = 0; i7 <= 1; i7++) {
                            if (this.isFullWide(axisCycle, i + i6 - 1, i1 + i7 - 1, i3)) {
                                i4++;
                                i5 ^= i6 ^ i7;
                            }
                        }
                    }

                    if (i4 == 1 || i4 == 3 || i4 == 2 && (i5 & 1) == 0) {
                        if (combine) {
                            if (i2 == -1) {
                                i2 = i3;
                            }
                        } else {
                            lineConsumer.consume(
                                axisCycle.cycle(i, i1, i3, Direction.Axis.X),
                                axisCycle.cycle(i, i1, i3, Direction.Axis.Y),
                                axisCycle.cycle(i, i1, i3, Direction.Axis.Z),
                                axisCycle.cycle(i, i1, i3 + 1, Direction.Axis.X),
                                axisCycle.cycle(i, i1, i3 + 1, Direction.Axis.Y),
                                axisCycle.cycle(i, i1, i3 + 1, Direction.Axis.Z)
                            );
                        }
                    } else if (i2 != -1) {
                        lineConsumer.consume(
                            axisCycle.cycle(i, i1, i2, Direction.Axis.X),
                            axisCycle.cycle(i, i1, i2, Direction.Axis.Y),
                            axisCycle.cycle(i, i1, i2, Direction.Axis.Z),
                            axisCycle.cycle(i, i1, i3, Direction.Axis.X),
                            axisCycle.cycle(i, i1, i3, Direction.Axis.Y),
                            axisCycle.cycle(i, i1, i3, Direction.Axis.Z)
                        );
                        i2 = -1;
                    }
                }
            }
        }
    }

    public void forAllBoxes(DiscreteVoxelShape.IntLineConsumer consumer, boolean combine) {
        BitSetDiscreteVoxelShape.forAllBoxes(this, consumer, combine);
    }

    public void forAllFaces(DiscreteVoxelShape.IntFaceConsumer faceConsumer) {
        this.forAllAxisFaces(faceConsumer, AxisCycle.NONE);
        this.forAllAxisFaces(faceConsumer, AxisCycle.FORWARD);
        this.forAllAxisFaces(faceConsumer, AxisCycle.BACKWARD);
    }

    private void forAllAxisFaces(DiscreteVoxelShape.IntFaceConsumer faceConsumer, AxisCycle axisRotation) {
        AxisCycle axisCycle = axisRotation.inverse();
        Direction.Axis axis = axisCycle.cycle(Direction.Axis.Z);
        int size = this.getSize(axisCycle.cycle(Direction.Axis.X));
        int size1 = this.getSize(axisCycle.cycle(Direction.Axis.Y));
        int size2 = this.getSize(axis);
        Direction direction = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
        Direction direction1 = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);

        for (int i = 0; i < size; i++) {
            for (int i1 = 0; i1 < size1; i1++) {
                boolean flag = false;

                for (int i2 = 0; i2 <= size2; i2++) {
                    boolean flag1 = i2 != size2 && this.isFull(axisCycle, i, i1, i2);
                    if (!flag && flag1) {
                        faceConsumer.consume(
                            direction,
                            axisCycle.cycle(i, i1, i2, Direction.Axis.X),
                            axisCycle.cycle(i, i1, i2, Direction.Axis.Y),
                            axisCycle.cycle(i, i1, i2, Direction.Axis.Z)
                        );
                    }

                    if (flag && !flag1) {
                        faceConsumer.consume(
                            direction1,
                            axisCycle.cycle(i, i1, i2 - 1, Direction.Axis.X),
                            axisCycle.cycle(i, i1, i2 - 1, Direction.Axis.Y),
                            axisCycle.cycle(i, i1, i2 - 1, Direction.Axis.Z)
                        );
                    }

                    flag = flag1;
                }
            }
        }
    }

    public interface IntFaceConsumer {
        void consume(Direction direction, int x, int y, int z);
    }

    public interface IntLineConsumer {
        void consume(int x1, int y1, int z1, int x2, int y2, int z2);
    }
}
