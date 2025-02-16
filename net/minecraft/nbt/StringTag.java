package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

public class StringTag implements Tag {
    private static final int SELF_SIZE_IN_BYTES = 36;
    public static final TagType<StringTag> TYPE = new TagType.VariableSize<StringTag>() {
        @Override
        public StringTag load(DataInput input, NbtAccounter accounter) throws IOException {
            return StringTag.valueOf(readAccounted(input, accounter));
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
            return visitor.visit(readAccounted(input, accounter));
        }

        private static String readAccounted(DataInput input, NbtAccounter nbtAccounter) throws IOException {
            nbtAccounter.accountBytes(36L);
            String utf = input.readUTF();
            nbtAccounter.accountBytes(2L, utf.length());
            return utf;
        }

        @Override
        public void skip(DataInput input, NbtAccounter accounter) throws IOException {
            StringTag.skipString(input);
        }

        @Override
        public String getName() {
            return "STRING";
        }

        @Override
        public String getPrettyName() {
            return "TAG_String";
        }

        @Override
        public boolean isValue() {
            return true;
        }
    };
    private static final StringTag EMPTY = new StringTag("");
    private static final char DOUBLE_QUOTE = '"';
    private static final char SINGLE_QUOTE = '\'';
    private static final char ESCAPE = '\\';
    private static final char NOT_SET = '\u0000';
    private final String data;

    public static void skipString(DataInput input) throws IOException {
        input.skipBytes(input.readUnsignedShort());
    }

    private StringTag(String data) {
        Objects.requireNonNull(data, "Null string not allowed");
        this.data = data;
    }

    public static StringTag valueOf(String data) {
        return data.isEmpty() ? EMPTY : new StringTag(data);
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeUTF(this.data);
    }

    @Override
    public int sizeInBytes() {
        return 36 + 2 * this.data.length();
    }

    @Override
    public byte getId() {
        return 8;
    }

    @Override
    public TagType<StringTag> getType() {
        return TYPE;
    }

    @Override
    public String toString() {
        return Tag.super.getAsString();
    }

    @Override
    public StringTag copy() {
        return this;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof StringTag && Objects.equals(this.data, ((StringTag)other).data);
    }

    @Override
    public int hashCode() {
        return this.data.hashCode();
    }

    @Override
    public String getAsString() {
        return this.data;
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitString(this);
    }

    public static String quoteAndEscape(String text) {
        StringBuilder stringBuilder = new StringBuilder(" ");
        char c = 0;

        for (int i = 0; i < text.length(); i++) {
            char c1 = text.charAt(i);
            if (c1 == '\\') {
                stringBuilder.append('\\');
            } else if (c1 == '"' || c1 == '\'') {
                if (c == 0) {
                    c = (char)(c1 == '"' ? 39 : 34);
                }

                if (c == c1) {
                    stringBuilder.append('\\');
                }
            }

            stringBuilder.append(c1);
        }

        if (c == 0) {
            c = '"';
        }

        stringBuilder.setCharAt(0, c);
        stringBuilder.append(c);
        return stringBuilder.toString();
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        return visitor.visit(this.data);
    }
}
