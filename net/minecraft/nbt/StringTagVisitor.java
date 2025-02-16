package net.minecraft.nbt;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class StringTagVisitor implements TagVisitor {
    private static final Pattern SIMPLE_VALUE = Pattern.compile("[A-Za-z0-9._+-]+");
    private final StringBuilder builder = new StringBuilder();

    public String visit(Tag tag) {
        tag.accept(this);
        return this.builder.toString();
    }

    @Override
    public void visitString(StringTag tag) {
        this.builder.append(StringTag.quoteAndEscape(tag.getAsString()));
    }

    @Override
    public void visitByte(ByteTag tag) {
        this.builder.append(tag.getAsNumber()).append('b');
    }

    @Override
    public void visitShort(ShortTag tag) {
        this.builder.append(tag.getAsNumber()).append('s');
    }

    @Override
    public void visitInt(IntTag tag) {
        this.builder.append(tag.getAsNumber());
    }

    @Override
    public void visitLong(LongTag tag) {
        this.builder.append(tag.getAsNumber()).append('L');
    }

    @Override
    public void visitFloat(FloatTag tag) {
        this.builder.append(tag.getAsFloat()).append('f');
    }

    @Override
    public void visitDouble(DoubleTag tag) {
        this.builder.append(tag.getAsDouble()).append('d');
    }

    @Override
    public void visitByteArray(ByteArrayTag tag) {
        this.builder.append("[B;");
        byte[] asByteArray = tag.getAsByteArray();

        for (int i = 0; i < asByteArray.length; i++) {
            if (i != 0) {
                this.builder.append(',');
            }

            this.builder.append(asByteArray[i]).append('B');
        }

        this.builder.append(']');
    }

    @Override
    public void visitIntArray(IntArrayTag tag) {
        this.builder.append("[I;");
        int[] asIntArray = tag.getAsIntArray();

        for (int i = 0; i < asIntArray.length; i++) {
            if (i != 0) {
                this.builder.append(',');
            }

            this.builder.append(asIntArray[i]);
        }

        this.builder.append(']');
    }

    @Override
    public void visitLongArray(LongArrayTag tag) {
        this.builder.append("[L;");
        long[] asLongArray = tag.getAsLongArray();

        for (int i = 0; i < asLongArray.length; i++) {
            if (i != 0) {
                this.builder.append(',');
            }

            this.builder.append(asLongArray[i]).append('L');
        }

        this.builder.append(']');
    }

    @Override
    public void visitList(ListTag tag) {
        this.builder.append('[');

        for (int i = 0; i < tag.size(); i++) {
            if (i != 0) {
                this.builder.append(',');
            }

            this.builder.append(new StringTagVisitor().visit(tag.get(i)));
        }

        this.builder.append(']');
    }

    @Override
    public void visitCompound(CompoundTag tag) {
        this.builder.append('{');
        List<String> list = Lists.newArrayList(tag.getAllKeys());
        Collections.sort(list);

        for (String string : list) {
            if (this.builder.length() != 1) {
                this.builder.append(',');
            }

            this.builder.append(handleEscape(string)).append(':').append(new StringTagVisitor().visit(tag.get(string)));
        }

        this.builder.append('}');
    }

    protected static String handleEscape(String text) {
        return SIMPLE_VALUE.matcher(text).matches() ? text : StringTag.quoteAndEscape(text);
    }

    @Override
    public void visitEnd(EndTag tag) {
        this.builder.append("END");
    }
}
