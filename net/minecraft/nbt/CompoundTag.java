package net.minecraft.nbt;

import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;

public class CompoundTag implements Tag {
    public static final Codec<CompoundTag> CODEC = Codec.PASSTHROUGH
        .comapFlatMap(
            compoundTag -> {
                Tag tag = compoundTag.convert(NbtOps.INSTANCE).getValue();
                return tag instanceof CompoundTag compoundTag1
                    ? DataResult.success(compoundTag1 == compoundTag.getValue() ? compoundTag1.copy() : compoundTag1)
                    : DataResult.error(() -> "Not a compound tag: " + tag);
            },
            compoundTag -> new Dynamic<>(NbtOps.INSTANCE, compoundTag.copy())
        );
    private static final int SELF_SIZE_IN_BYTES = 48;
    private static final int MAP_ENTRY_SIZE_IN_BYTES = 32;
    public static final TagType<CompoundTag> TYPE = new TagType.VariableSize<CompoundTag>() {
        @Override
        public CompoundTag load(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            CompoundTag var3;
            try {
                var3 = loadCompound(input, accounter);
            } finally {
                accounter.popDepth();
            }

            return var3;
        }

        private static CompoundTag loadCompound(DataInput input, NbtAccounter nbtAccounter) throws IOException {
            nbtAccounter.accountBytes(48L);
            it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<String, Tag> map = new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>(8, 0.8f); // Paper - Reduce memory footprint of CompoundTag

            byte b;
            while ((b = input.readByte()) != 0) {
                String string = readString(input, nbtAccounter);
                Tag namedTagData = CompoundTag.readNamedTagData(TagTypes.getType(b), string, input, nbtAccounter);
                if (map.put(string, namedTagData) == null) {
                    nbtAccounter.accountBytes(36L);
                }
            }

            return new CompoundTag(map);
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            StreamTagVisitor.ValueResult var4;
            try {
                var4 = parseCompound(input, visitor, accounter);
            } finally {
                accounter.popDepth();
            }

            return var4;
        }

        private static StreamTagVisitor.ValueResult parseCompound(DataInput input, StreamTagVisitor visitor, NbtAccounter nbtAccounter) throws IOException {
            nbtAccounter.accountBytes(48L);

            byte b;
            label35:
            while ((b = input.readByte()) != 0) {
                TagType<?> type = TagTypes.getType(b);
                switch (visitor.visitEntry(type)) {
                    case HALT:
                        return StreamTagVisitor.ValueResult.HALT;
                    case BREAK:
                        StringTag.skipString(input);
                        type.skip(input, nbtAccounter);
                        break label35;
                    case SKIP:
                        StringTag.skipString(input);
                        type.skip(input, nbtAccounter);
                        break;
                    default:
                        String string = readString(input, nbtAccounter);
                        switch (visitor.visitEntry(type, string)) {
                            case HALT:
                                return StreamTagVisitor.ValueResult.HALT;
                            case BREAK:
                                type.skip(input, nbtAccounter);
                                break label35;
                            case SKIP:
                                type.skip(input, nbtAccounter);
                                break;
                            default:
                                nbtAccounter.accountBytes(36L);
                                switch (type.parse(input, visitor, nbtAccounter)) {
                                    case HALT:
                                        return StreamTagVisitor.ValueResult.HALT;
                                    case BREAK:
                                }
                        }
                }
            }

            if (b != 0) {
                while ((b = input.readByte()) != 0) {
                    StringTag.skipString(input);
                    TagTypes.getType(b).skip(input, nbtAccounter);
                }
            }

            return visitor.visitContainerEnd();
        }

        private static String readString(DataInput input, NbtAccounter accounter) throws IOException {
            String utf = input.readUTF();
            accounter.accountBytes(28L);
            accounter.accountBytes(2L, utf.length());
            return utf;
        }

        @Override
        public void skip(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            byte b;
            try {
                while ((b = input.readByte()) != 0) {
                    StringTag.skipString(input);
                    TagTypes.getType(b).skip(input, accounter);
                }
            } finally {
                accounter.popDepth();
            }
        }

        @Override
        public String getName() {
            return "COMPOUND";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Compound";
        }
    };
    private final Map<String, Tag> tags;

    protected CompoundTag(Map<String, Tag> tags) {
        this.tags = tags;
    }

    public CompoundTag() {
        this(new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>(8, 0.8f)); // Paper - Reduce memory footprint of CompoundTag
    }

    @Override
    public void write(DataOutput output) throws IOException {
        for (String string : this.tags.keySet()) {
            Tag tag = this.tags.get(string);
            writeNamedTag(string, tag, output);
        }

        output.writeByte(0);
    }

    @Override
    public int sizeInBytes() {
        int i = 48;

        for (Entry<String, Tag> entry : this.tags.entrySet()) {
            i += 28 + 2 * entry.getKey().length();
            i += 36;
            i += entry.getValue().sizeInBytes();
        }

        return i;
    }

    public Set<String> getAllKeys() {
        return this.tags.keySet();
    }

    @Override
    public byte getId() {
        return 10;
    }

    @Override
    public TagType<CompoundTag> getType() {
        return TYPE;
    }

    public int size() {
        return this.tags.size();
    }

    @Nullable
    public Tag put(String key, Tag value) {
        return this.tags.put(key, value);
    }

    public void putByte(String key, byte value) {
        this.tags.put(key, ByteTag.valueOf(value));
    }

    public void putShort(String key, short value) {
        this.tags.put(key, ShortTag.valueOf(value));
    }

    public void putInt(String key, int value) {
        this.tags.put(key, IntTag.valueOf(value));
    }

    public void putLong(String key, long value) {
        this.tags.put(key, LongTag.valueOf(value));
    }

    public void putUUID(String key, UUID value) {
        // Paper start - Support old UUID format
        if (this.contains(key + "Most", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC) && this.contains(key + "Least", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
            this.tags.remove(key + "Most");
            this.tags.remove(key + "Least");
        }
        // Paper end - Support old UUID format
        this.tags.put(key, NbtUtils.createUUID(value));
    }


    /**
     * You must use {@link #hasUUID(String)} before or else it <b>will</b> throw an NPE.
     */
    public UUID getUUID(String key) {
        // Paper start - Support old UUID format
        if (!contains(key, 11) && this.contains(key + "Most", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC) && this.contains(key + "Least", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
            return new UUID(this.getLong(key + "Most"), this.getLong(key + "Least"));
        }
        // Paper end - Support old UUID format
        return NbtUtils.loadUUID(this.get(key));
    }

    public boolean hasUUID(String key) {
        // Paper start - Support old UUID format
        if (this.contains(key + "Most", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC) && this.contains(key + "Least", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
            return true;
        }
        // Paper end - Support old UUID format
        Tag tag = this.get(key);
        return tag != null && tag.getType() == IntArrayTag.TYPE && ((IntArrayTag)tag).getAsIntArray().length == 4;
    }

    public void putFloat(String key, float value) {
        this.tags.put(key, FloatTag.valueOf(value));
    }

    public void putDouble(String key, double value) {
        this.tags.put(key, DoubleTag.valueOf(value));
    }

    public void putString(String key, String value) {
        this.tags.put(key, StringTag.valueOf(value));
    }

    public void putByteArray(String key, byte[] value) {
        this.tags.put(key, new ByteArrayTag(value));
    }

    public void putByteArray(String key, List<Byte> value) {
        this.tags.put(key, new ByteArrayTag(value));
    }

    public void putIntArray(String key, int[] value) {
        this.tags.put(key, new IntArrayTag(value));
    }

    public void putIntArray(String key, List<Integer> value) {
        this.tags.put(key, new IntArrayTag(value));
    }

    public void putLongArray(String key, long[] value) {
        this.tags.put(key, new LongArrayTag(value));
    }

    public void putLongArray(String key, List<Long> value) {
        this.tags.put(key, new LongArrayTag(value));
    }

    public void putBoolean(String key, boolean value) {
        this.tags.put(key, ByteTag.valueOf(value));
    }

    @Nullable
    public Tag get(String key) {
        return this.tags.get(key);
    }

    public byte getTagType(String key) {
        Tag tag = this.tags.get(key);
        return tag == null ? 0 : tag.getId();
    }

    public boolean contains(String key) {
        return this.tags.containsKey(key);
    }

    public boolean contains(String key, int tagType) {
        int tagType1 = this.getTagType(key);
        return tagType1 == tagType || tagType == 99 && (tagType1 == 1 || tagType1 == 2 || tagType1 == 3 || tagType1 == 4 || tagType1 == 5 || tagType1 == 6);
    }

    public byte getByte(String key) {
        try {
            if (this.contains(key, 99)) {
                return ((NumericTag)this.tags.get(key)).getAsByte();
            }
        } catch (ClassCastException var3) {
        }

        return 0;
    }

    public short getShort(String key) {
        try {
            if (this.contains(key, 99)) {
                return ((NumericTag)this.tags.get(key)).getAsShort();
            }
        } catch (ClassCastException var3) {
        }

        return 0;
    }

    public int getInt(String key) {
        try {
            if (this.contains(key, 99)) {
                return ((NumericTag)this.tags.get(key)).getAsInt();
            }
        } catch (ClassCastException var3) {
        }

        return 0;
    }

    public long getLong(String key) {
        try {
            if (this.contains(key, 99)) {
                return ((NumericTag)this.tags.get(key)).getAsLong();
            }
        } catch (ClassCastException var3) {
        }

        return 0L;
    }

    public float getFloat(String key) {
        try {
            if (this.contains(key, 99)) {
                return ((NumericTag)this.tags.get(key)).getAsFloat();
            }
        } catch (ClassCastException var3) {
        }

        return 0.0F;
    }

    public double getDouble(String key) {
        try {
            if (this.contains(key, 99)) {
                return ((NumericTag)this.tags.get(key)).getAsDouble();
            }
        } catch (ClassCastException var3) {
        }

        return 0.0;
    }

    public String getString(String key) {
        try {
            if (this.contains(key, 8)) {
                return this.tags.get(key).getAsString();
            }
        } catch (ClassCastException var3) {
        }

        return "";
    }

    public byte[] getByteArray(String key) {
        try {
            if (this.contains(key, 7)) {
                return ((ByteArrayTag)this.tags.get(key)).getAsByteArray();
            }
        } catch (ClassCastException var3) {
            throw new ReportedException(this.createReport(key, ByteArrayTag.TYPE, var3));
        }

        return new byte[0];
    }

    public int[] getIntArray(String key) {
        try {
            if (this.contains(key, 11)) {
                return ((IntArrayTag)this.tags.get(key)).getAsIntArray();
            }
        } catch (ClassCastException var3) {
            throw new ReportedException(this.createReport(key, IntArrayTag.TYPE, var3));
        }

        return new int[0];
    }

    public long[] getLongArray(String key) {
        try {
            if (this.contains(key, 12)) {
                return ((LongArrayTag)this.tags.get(key)).getAsLongArray();
            }
        } catch (ClassCastException var3) {
            throw new ReportedException(this.createReport(key, LongArrayTag.TYPE, var3));
        }

        return new long[0];
    }

    public CompoundTag getCompound(String key) {
        try {
            if (this.contains(key, 10)) {
                return (CompoundTag)this.tags.get(key);
            }
        } catch (ClassCastException var3) {
            throw new ReportedException(this.createReport(key, TYPE, var3));
        }

        return new CompoundTag();
    }

    public ListTag getList(String key, int tagType) {
        try {
            if (this.getTagType(key) == 9) {
                ListTag listTag = (ListTag)this.tags.get(key);
                if (!listTag.isEmpty() && listTag.getElementType() != tagType) {
                    return new ListTag();
                }

                return listTag;
            }
        } catch (ClassCastException var4) {
            throw new ReportedException(this.createReport(key, ListTag.TYPE, var4));
        }

        return new ListTag();
    }

    public boolean getBoolean(String key) {
        return this.getByte(key) != 0;
    }

    public void remove(String key) {
        this.tags.remove(key);
    }

    @Override
    public String toString() {
        return this.getAsString();
    }

    public boolean isEmpty() {
        return this.tags.isEmpty();
    }

    private CrashReport createReport(String tagName, TagType<?> type, ClassCastException exception) {
        CrashReport crashReport = CrashReport.forThrowable(exception, "Reading NBT data");
        CrashReportCategory crashReportCategory = crashReport.addCategory("Corrupt NBT tag", 1);
        crashReportCategory.setDetail("Tag type found", () -> this.tags.get(tagName).getType().getName());
        crashReportCategory.setDetail("Tag type expected", type::getName);
        crashReportCategory.setDetail("Tag name", tagName);
        return crashReport;
    }

    protected CompoundTag shallowCopy() {
        return new CompoundTag(new HashMap<>(this.tags));
    }

    @Override
    public CompoundTag copy() {
        // Paper start - Reduce memory footprint of CompoundTag
        it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<String, Tag> ret = new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>(this.tags.size(), 0.8f);
        java.util.Iterator<java.util.Map.Entry<String, Tag>> iterator = (this.tags instanceof it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap) ? ((it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap)this.tags).object2ObjectEntrySet().fastIterator() : this.tags.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Tag> entry = iterator.next();
            ret.put(entry.getKey(), entry.getValue().copy());
        }

        return new CompoundTag(ret);
        // Paper end - Reduce memory footprint of CompoundTag
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof CompoundTag && Objects.equals(this.tags, ((CompoundTag)other).tags);
    }

    @Override
    public int hashCode() {
        return this.tags.hashCode();
    }

    private static void writeNamedTag(String name, Tag tag, DataOutput output) throws IOException {
        output.writeByte(tag.getId());
        if (tag.getId() != 0) {
            output.writeUTF(name);
            tag.write(output);
        }
    }

    static Tag readNamedTagData(TagType<?> type, String name, DataInput input, NbtAccounter accounter) {
        try {
            return type.load(input, accounter);
        } catch (IOException var7) {
            CrashReport crashReport = CrashReport.forThrowable(var7, "Loading NBT data");
            CrashReportCategory crashReportCategory = crashReport.addCategory("NBT Tag");
            crashReportCategory.setDetail("Tag name", name);
            crashReportCategory.setDetail("Tag type", type.getName());
            throw new ReportedNbtException(crashReport);
        }
    }

    public CompoundTag merge(CompoundTag other) {
        for (String string : other.tags.keySet()) {
            Tag tag = other.tags.get(string);
            if (tag.getId() == 10) {
                if (this.contains(string, 10)) {
                    CompoundTag compound = this.getCompound(string);
                    compound.merge((CompoundTag)tag);
                } else {
                    this.put(string, tag.copy());
                }
            } else {
                this.put(string, tag.copy());
            }
        }

        return this;
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitCompound(this);
    }

    protected Set<Entry<String, Tag>> entrySet() {
        return this.tags.entrySet();
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        for (Entry<String, Tag> entry : this.tags.entrySet()) {
            Tag tag = entry.getValue();
            TagType<?> type = tag.getType();
            StreamTagVisitor.EntryResult entryResult = visitor.visitEntry(type);
            switch (entryResult) {
                case HALT:
                    return StreamTagVisitor.ValueResult.HALT;
                case BREAK:
                    return visitor.visitContainerEnd();
                case SKIP:
                    break;
                default:
                    entryResult = visitor.visitEntry(type, entry.getKey());
                    switch (entryResult) {
                        case HALT:
                            return StreamTagVisitor.ValueResult.HALT;
                        case BREAK:
                            return visitor.visitContainerEnd();
                        case SKIP:
                            break;
                        default:
                            StreamTagVisitor.ValueResult valueResult = tag.accept(visitor);
                            switch (valueResult) {
                                case HALT:
                                    return StreamTagVisitor.ValueResult.HALT;
                                case BREAK:
                                    return visitor.visitContainerEnd();
                            }
                    }
            }
        }

        return visitor.visitContainerEnd();
    }
}
