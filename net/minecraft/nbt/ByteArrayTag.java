package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;

public class ByteArrayTag extends CollectionTag<ByteTag> {
    private static final int SELF_SIZE_IN_BYTES = 24;
    public static final TagType<ByteArrayTag> TYPE = new TagType.VariableSize<ByteArrayTag>() {
        @Override
        public ByteArrayTag load(DataInput input, NbtAccounter accounter) throws IOException {
            return new ByteArrayTag(readAccounted(input, accounter));
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
            return visitor.visit(readAccounted(input, accounter));
        }

        private static byte[] readAccounted(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(24L);
            int _int = input.readInt();
            com.google.common.base.Preconditions.checkArgument(_int < 1 << 24); // Spigot
            accounter.accountBytes(1L, _int);
            byte[] bytes = new byte[_int];
            input.readFully(bytes);
            return bytes;
        }

        @Override
        public void skip(DataInput input, NbtAccounter accounter) throws IOException {
            input.skipBytes(input.readInt() * 1);
        }

        @Override
        public String getName() {
            return "BYTE[]";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Byte_Array";
        }
    };
    private byte[] data;

    public ByteArrayTag(byte[] data) {
        this.data = data;
    }

    public ByteArrayTag(List<Byte> data) {
        this(toArray(data));
    }

    private static byte[] toArray(List<Byte> dataList) {
        byte[] bytes = new byte[dataList.size()];

        for (int i = 0; i < dataList.size(); i++) {
            Byte _byte = dataList.get(i);
            bytes[i] = _byte == null ? 0 : _byte;
        }

        return bytes;
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeInt(this.data.length);
        output.write(this.data);
    }

    @Override
    public int sizeInBytes() {
        return 24 + 1 * this.data.length;
    }

    @Override
    public byte getId() {
        return 7;
    }

    @Override
    public TagType<ByteArrayTag> getType() {
        return TYPE;
    }

    @Override
    public String toString() {
        return this.getAsString();
    }

    @Override
    public Tag copy() {
        byte[] bytes = new byte[this.data.length];
        System.arraycopy(this.data, 0, bytes, 0, this.data.length);
        return new ByteArrayTag(bytes);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ByteArrayTag && Arrays.equals(this.data, ((ByteArrayTag)other).data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.data);
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitByteArray(this);
    }

    public byte[] getAsByteArray() {
        return this.data;
    }

    @Override
    public int size() {
        return this.data.length;
    }

    @Override
    public ByteTag get(int index) {
        return ByteTag.valueOf(this.data[index]);
    }

    @Override
    public ByteTag set(int index, ByteTag tag) {
        byte b = this.data[index];
        this.data[index] = tag.getAsByte();
        return ByteTag.valueOf(b);
    }

    @Override
    public void add(int index, ByteTag tag) {
        this.data = ArrayUtils.add(this.data, index, tag.getAsByte());
    }

    @Override
    public boolean setTag(int index, Tag nbt) {
        if (nbt instanceof NumericTag) {
            this.data[index] = ((NumericTag)nbt).getAsByte();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addTag(int index, Tag nbt) {
        if (nbt instanceof NumericTag) {
            this.data = ArrayUtils.add(this.data, index, ((NumericTag)nbt).getAsByte());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public ByteTag remove(int index) {
        byte b = this.data[index];
        this.data = ArrayUtils.remove(this.data, index);
        return ByteTag.valueOf(b);
    }

    @Override
    public byte getElementType() {
        return 1;
    }

    @Override
    public void clear() {
        this.data = new byte[0];
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        return visitor.visit(this.data);
    }
}
