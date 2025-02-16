package net.minecraft.nbt.visitors;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagType;

public class CollectToTag implements StreamTagVisitor {
    private String lastId = "";
    @Nullable
    private Tag rootTag;
    private final Deque<Consumer<Tag>> consumerStack = new ArrayDeque<>();

    @Nullable
    public Tag getResult() {
        return this.rootTag;
    }

    protected int depth() {
        return this.consumerStack.size();
    }

    private void appendEntry(Tag tag) {
        this.consumerStack.getLast().accept(tag);
    }

    @Override
    public StreamTagVisitor.ValueResult visitEnd() {
        this.appendEntry(EndTag.INSTANCE);
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(String entry) {
        this.appendEntry(StringTag.valueOf(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(byte entry) {
        this.appendEntry(ByteTag.valueOf(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(short entry) {
        this.appendEntry(ShortTag.valueOf(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(int entry) {
        this.appendEntry(IntTag.valueOf(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(long entry) {
        this.appendEntry(LongTag.valueOf(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(float entry) {
        this.appendEntry(FloatTag.valueOf(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(double entry) {
        this.appendEntry(DoubleTag.valueOf(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(byte[] entry) {
        this.appendEntry(new ByteArrayTag(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(int[] entry) {
        this.appendEntry(new IntArrayTag(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visit(long[] entry) {
        this.appendEntry(new LongArrayTag(entry));
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visitList(TagType<?> type, int size) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.EntryResult visitElement(TagType<?> type, int size) {
        this.enterContainerIfNeeded(type);
        return StreamTagVisitor.EntryResult.ENTER;
    }

    @Override
    public StreamTagVisitor.EntryResult visitEntry(TagType<?> type) {
        return StreamTagVisitor.EntryResult.ENTER;
    }

    @Override
    public StreamTagVisitor.EntryResult visitEntry(TagType<?> type, String id) {
        this.lastId = id;
        this.enterContainerIfNeeded(type);
        return StreamTagVisitor.EntryResult.ENTER;
    }

    private void enterContainerIfNeeded(TagType<?> type) {
        if (type == ListTag.TYPE) {
            ListTag listTag = new ListTag();
            this.appendEntry(listTag);
            this.consumerStack.addLast(listTag::add);
        } else if (type == CompoundTag.TYPE) {
            CompoundTag compoundTag = new CompoundTag();
            this.appendEntry(compoundTag);
            this.consumerStack.addLast(tag -> compoundTag.put(this.lastId, tag));
        }
    }

    @Override
    public StreamTagVisitor.ValueResult visitContainerEnd() {
        this.consumerStack.removeLast();
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    public StreamTagVisitor.ValueResult visitRootEntry(TagType<?> type) {
        if (type == ListTag.TYPE) {
            ListTag listTag = new ListTag();
            this.rootTag = listTag;
            this.consumerStack.addLast(listTag::add);
        } else if (type == CompoundTag.TYPE) {
            CompoundTag compoundTag = new CompoundTag();
            this.rootTag = compoundTag;
            this.consumerStack.addLast(tag -> compoundTag.put(this.lastId, tag));
        } else {
            this.consumerStack.addLast(tag -> this.rootTag = tag);
        }

        return StreamTagVisitor.ValueResult.CONTINUE;
    }
}
