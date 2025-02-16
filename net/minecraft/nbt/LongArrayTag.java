package net.minecraft.nbt;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;

public class LongArrayTag extends CollectionTag<LongTag> {
    private static final int SELF_SIZE_IN_BYTES = 24;
    public static final TagType<LongArrayTag> TYPE = new TagType.VariableSize<LongArrayTag>() {
        @Override
        public LongArrayTag load(DataInput input, NbtAccounter accounter) throws IOException {
            return new LongArrayTag(readAccounted(input, accounter));
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
            return visitor.visit(readAccounted(input, accounter));
        }

        private static long[] readAccounted(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(24L);
            int _int = input.readInt();
            accounter.accountBytes(8L, _int);
            long[] longs = new long[_int];

            for (int i = 0; i < _int; i++) {
                longs[i] = input.readLong();
            }

            return longs;
        }

        @Override
        public void skip(DataInput input, NbtAccounter accounter) throws IOException {
            input.skipBytes(input.readInt() * 8);
        }

        @Override
        public String getName() {
            return "LONG[]";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Long_Array";
        }
    };
    private long[] data;

    public LongArrayTag(long[] data) {
        this.data = data;
    }

    public LongArrayTag(LongSet dataSet) {
        this.data = dataSet.toLongArray();
    }

    public LongArrayTag(List<Long> data) {
        this(toArray(data));
    }

    private static long[] toArray(List<Long> dataList) {
        long[] longs = new long[dataList.size()];

        for (int i = 0; i < dataList.size(); i++) {
            Long _long = dataList.get(i);
            longs[i] = _long == null ? 0L : _long;
        }

        return longs;
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeInt(this.data.length);

        for (long l : this.data) {
            output.writeLong(l);
        }
    }

    @Override
    public int sizeInBytes() {
        return 24 + 8 * this.data.length;
    }

    @Override
    public byte getId() {
        return 12;
    }

    @Override
    public TagType<LongArrayTag> getType() {
        return TYPE;
    }

    @Override
    public String toString() {
        return this.getAsString();
    }

    @Override
    public LongArrayTag copy() {
        long[] longs = new long[this.data.length];
        System.arraycopy(this.data, 0, longs, 0, this.data.length);
        return new LongArrayTag(longs);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof LongArrayTag && Arrays.equals(this.data, ((LongArrayTag)other).data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.data);
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitLongArray(this);
    }

    public long[] getAsLongArray() {
        return this.data;
    }

    @Override
    public int size() {
        return this.data.length;
    }

    @Override
    public LongTag get(int index) {
        return LongTag.valueOf(this.data[index]);
    }

    @Override
    public LongTag set(int index, LongTag tag) {
        long l = this.data[index];
        this.data[index] = tag.getAsLong();
        return LongTag.valueOf(l);
    }

    @Override
    public void add(int index, LongTag tag) {
        this.data = ArrayUtils.add(this.data, index, tag.getAsLong());
    }

    @Override
    public boolean setTag(int index, Tag nbt) {
        if (nbt instanceof NumericTag) {
            this.data[index] = ((NumericTag)nbt).getAsLong();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addTag(int index, Tag nbt) {
        if (nbt instanceof NumericTag) {
            this.data = ArrayUtils.add(this.data, index, ((NumericTag)nbt).getAsLong());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public LongTag remove(int index) {
        long l = this.data[index];
        this.data = ArrayUtils.remove(this.data, index);
        return LongTag.valueOf(l);
    }

    @Override
    public byte getElementType() {
        return 4;
    }

    @Override
    public void clear() {
        this.data = new long[0];
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        return visitor.visit(this.data);
    }
}
