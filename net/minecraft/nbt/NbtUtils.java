package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import org.slf4j.Logger;

public final class NbtUtils {
    private static final Comparator<ListTag> YXZ_LISTTAG_INT_COMPARATOR = Comparator.<ListTag>comparingInt(listTag -> listTag.getInt(1))
        .thenComparingInt(listTag -> listTag.getInt(0))
        .thenComparingInt(listTag -> listTag.getInt(2));
    private static final Comparator<ListTag> YXZ_LISTTAG_DOUBLE_COMPARATOR = Comparator.<ListTag>comparingDouble(listTag -> listTag.getDouble(1))
        .thenComparingDouble(listTag -> listTag.getDouble(0))
        .thenComparingDouble(listTag -> listTag.getDouble(2));
    public static final String SNBT_DATA_TAG = "data";
    private static final char PROPERTIES_START = '{';
    private static final char PROPERTIES_END = '}';
    private static final String ELEMENT_SEPARATOR = ",";
    private static final char KEY_VALUE_SEPARATOR = ':';
    private static final Splitter COMMA_SPLITTER = Splitter.on(",");
    private static final Splitter COLON_SPLITTER = Splitter.on(':').limit(2);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int INDENT = 2;
    private static final int NOT_FOUND = -1;

    private NbtUtils() {
    }

    @VisibleForTesting
    public static boolean compareNbt(@Nullable Tag tag, @Nullable Tag other, boolean compareListTag) {
        if (tag == other) {
            return true;
        } else if (tag == null) {
            return true;
        } else if (other == null) {
            return false;
        } else if (!tag.getClass().equals(other.getClass())) {
            return false;
        } else if (tag instanceof CompoundTag compoundTag) {
            CompoundTag compoundTag1 = (CompoundTag)other;
            if (compoundTag1.size() < compoundTag.size()) {
                return false;
            } else {
                for (String string : compoundTag.getAllKeys()) {
                    Tag tag1 = compoundTag.get(string);
                    if (!compareNbt(tag1, compoundTag1.get(string), compareListTag)) {
                        return false;
                    }
                }

                return true;
            }
        } else if (tag instanceof ListTag listTag && compareListTag) {
            ListTag listTag1 = (ListTag)other;
            if (listTag.isEmpty()) {
                return listTag1.isEmpty();
            } else if (listTag1.size() < listTag.size()) {
                return false;
            } else {
                for (Tag tag2 : listTag) {
                    boolean flag = false;

                    for (Tag tag3 : listTag1) {
                        if (compareNbt(tag2, tag3, compareListTag)) {
                            flag = true;
                            break;
                        }
                    }

                    if (!flag) {
                        return false;
                    }
                }

                return true;
            }
        } else {
            return tag.equals(other);
        }
    }

    public static IntArrayTag createUUID(UUID uuid) {
        return new IntArrayTag(UUIDUtil.uuidToIntArray(uuid));
    }

    public static UUID loadUUID(Tag tag) {
        if (tag.getType() != IntArrayTag.TYPE) {
            throw new IllegalArgumentException("Expected UUID-Tag to be of type " + IntArrayTag.TYPE.getName() + ", but found " + tag.getType().getName() + ".");
        } else {
            int[] asIntArray = ((IntArrayTag)tag).getAsIntArray();
            if (asIntArray.length != 4) {
                throw new IllegalArgumentException("Expected UUID-Array to be of length 4, but found " + asIntArray.length + ".");
            } else {
                return UUIDUtil.uuidFromIntArray(asIntArray);
            }
        }
    }

    public static Optional<BlockPos> readBlockPos(CompoundTag tag, String key) {
        int[] intArray = tag.getIntArray(key);
        return intArray.length == 3 ? Optional.of(new BlockPos(intArray[0], intArray[1], intArray[2])) : Optional.empty();
    }

    public static Tag writeBlockPos(BlockPos pos) {
        return new IntArrayTag(new int[]{pos.getX(), pos.getY(), pos.getZ()});
    }

    public static BlockState readBlockState(HolderGetter<Block> blockGetter, CompoundTag tag) {
        if (!tag.contains("Name", 8)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            ResourceLocation resourceLocation = ResourceLocation.parse(tag.getString("Name"));
            Optional<? extends Holder<Block>> optional = blockGetter.get(ResourceKey.create(Registries.BLOCK, resourceLocation));
            if (optional.isEmpty()) {
                return Blocks.AIR.defaultBlockState();
            } else {
                Block block = optional.get().value();
                BlockState blockState = block.defaultBlockState();
                if (tag.contains("Properties", 10)) {
                    CompoundTag compound = tag.getCompound("Properties");
                    StateDefinition<Block, BlockState> stateDefinition = block.getStateDefinition();

                    for (String string : compound.getAllKeys()) {
                        Property<?> property = stateDefinition.getProperty(string);
                        if (property != null) {
                            blockState = setValueHelper(blockState, property, string, compound, tag);
                        }
                    }
                }

                return blockState;
            }
        }
    }

    private static <S extends StateHolder<?, S>, T extends Comparable<T>> S setValueHelper(
        S stateHolder, Property<T> property, String propertyName, CompoundTag propertiesTag, CompoundTag blockStateTag
    ) {
        Optional<T> value = property.getValue(propertiesTag.getString(propertyName));
        if (value.isPresent()) {
            return stateHolder.setValue(property, value.get());
        } else {
            LOGGER.warn("Unable to read property: {} with value: {} for blockstate: {}", propertyName, propertiesTag.getString(propertyName), blockStateTag);
            return stateHolder;
        }
    }

    public static CompoundTag writeBlockState(BlockState state) {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        Map<Property<?>, Comparable<?>> values = state.getValues();
        if (!values.isEmpty()) {
            CompoundTag compoundTag1 = new CompoundTag();

            for (Entry<Property<?>, Comparable<?>> entry : values.entrySet()) {
                Property<?> property = entry.getKey();
                compoundTag1.putString(property.getName(), getName(property, entry.getValue()));
            }

            compoundTag.put("Properties", compoundTag1);
        }

        return compoundTag;
    }

    public static CompoundTag writeFluidState(FluidState state) {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("Name", BuiltInRegistries.FLUID.getKey(state.getType()).toString());
        Map<Property<?>, Comparable<?>> values = state.getValues();
        if (!values.isEmpty()) {
            CompoundTag compoundTag1 = new CompoundTag();

            for (Entry<Property<?>, Comparable<?>> entry : values.entrySet()) {
                Property<?> property = entry.getKey();
                compoundTag1.putString(property.getName(), getName(property, entry.getValue()));
            }

            compoundTag.put("Properties", compoundTag1);
        }

        return compoundTag;
    }

    private static <T extends Comparable<T>> String getName(Property<T> property, Comparable<?> value) {
        return property.getName((T)value);
    }

    public static String prettyPrint(Tag tag) {
        return prettyPrint(tag, false);
    }

    public static String prettyPrint(Tag tag, boolean prettyPrintArray) {
        return prettyPrint(new StringBuilder(), tag, 0, prettyPrintArray).toString();
    }

    public static StringBuilder prettyPrint(StringBuilder stringBuilder, Tag tag, int indentLevel, boolean prettyPrintArray) {
        switch (tag.getId()) {
            case 0:
                break;
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 8:
                stringBuilder.append(tag);
                break;
            case 7:
                ByteArrayTag byteArrayTag = (ByteArrayTag)tag;
                byte[] asByteArray = byteArrayTag.getAsByteArray();
                int ix = asByteArray.length;
                indent(indentLevel, stringBuilder).append("byte[").append(ix).append("] {\n");
                if (prettyPrintArray) {
                    indent(indentLevel + 1, stringBuilder);

                    for (int i1 = 0; i1 < asByteArray.length; i1++) {
                        if (i1 != 0) {
                            stringBuilder.append(',');
                        }

                        if (i1 % 16 == 0 && i1 / 16 > 0) {
                            stringBuilder.append('\n');
                            if (i1 < asByteArray.length) {
                                indent(indentLevel + 1, stringBuilder);
                            }
                        } else if (i1 != 0) {
                            stringBuilder.append(' ');
                        }

                        stringBuilder.append(String.format(Locale.ROOT, "0x%02X", asByteArray[i1] & 255));
                    }
                } else {
                    indent(indentLevel + 1, stringBuilder).append(" // Skipped, supply withBinaryBlobs true");
                }

                stringBuilder.append('\n');
                indent(indentLevel, stringBuilder).append('}');
                break;
            case 9:
                ListTag listTag = (ListTag)tag;
                int size = listTag.size();
                int i = listTag.getElementType();
                String string = i == 0 ? "undefined" : TagTypes.getType(i).getPrettyName();
                indent(indentLevel, stringBuilder).append("list<").append(string).append(">[").append(size).append("] [");
                if (size != 0) {
                    stringBuilder.append('\n');
                }

                for (int i2 = 0; i2 < size; i2++) {
                    if (i2 != 0) {
                        stringBuilder.append(",\n");
                    }

                    indent(indentLevel + 1, stringBuilder);
                    prettyPrint(stringBuilder, listTag.get(i2), indentLevel + 1, prettyPrintArray);
                }

                if (size != 0) {
                    stringBuilder.append('\n');
                }

                indent(indentLevel, stringBuilder).append(']');
                break;
            case 10:
                CompoundTag compoundTag = (CompoundTag)tag;
                List<String> list = Lists.newArrayList(compoundTag.getAllKeys());
                Collections.sort(list);
                indent(indentLevel, stringBuilder).append('{');
                if (stringBuilder.length() - stringBuilder.lastIndexOf("\n") > 2 * (indentLevel + 1)) {
                    stringBuilder.append('\n');
                    indent(indentLevel + 1, stringBuilder);
                }

                ix = list.stream().mapToInt(String::length).max().orElse(0);
                String stringx = Strings.repeat(" ", ix);

                for (int i2 = 0; i2 < list.size(); i2++) {
                    if (i2 != 0) {
                        stringBuilder.append(",\n");
                    }

                    String string1 = list.get(i2);
                    indent(indentLevel + 1, stringBuilder)
                        .append('"')
                        .append(string1)
                        .append('"')
                        .append(stringx, 0, stringx.length() - string1.length())
                        .append(": ");
                    prettyPrint(stringBuilder, compoundTag.get(string1), indentLevel + 1, prettyPrintArray);
                }

                if (!list.isEmpty()) {
                    stringBuilder.append('\n');
                }

                indent(indentLevel, stringBuilder).append('}');
                break;
            case 11:
                IntArrayTag intArrayTag = (IntArrayTag)tag;
                int[] asIntArray = intArrayTag.getAsIntArray();
                ix = 0;

                for (int i3 : asIntArray) {
                    ix = Math.max(ix, String.format(Locale.ROOT, "%X", i3).length());
                }

                int i1 = asIntArray.length;
                indent(indentLevel, stringBuilder).append("int[").append(i1).append("] {\n");
                if (prettyPrintArray) {
                    indent(indentLevel + 1, stringBuilder);

                    for (int i2 = 0; i2 < asIntArray.length; i2++) {
                        if (i2 != 0) {
                            stringBuilder.append(',');
                        }

                        if (i2 % 16 == 0 && i2 / 16 > 0) {
                            stringBuilder.append('\n');
                            if (i2 < asIntArray.length) {
                                indent(indentLevel + 1, stringBuilder);
                            }
                        } else if (i2 != 0) {
                            stringBuilder.append(' ');
                        }

                        stringBuilder.append(String.format(Locale.ROOT, "0x%0" + ix + "X", asIntArray[i2]));
                    }
                } else {
                    indent(indentLevel + 1, stringBuilder).append(" // Skipped, supply withBinaryBlobs true");
                }

                stringBuilder.append('\n');
                indent(indentLevel, stringBuilder).append('}');
                break;
            case 12:
                LongArrayTag longArrayTag = (LongArrayTag)tag;
                long[] asLongArray = longArrayTag.getAsLongArray();
                long l = 0L;

                for (long l1 : asLongArray) {
                    l = Math.max(l, (long)String.format(Locale.ROOT, "%X", l1).length());
                }

                long l2 = asLongArray.length;
                indent(indentLevel, stringBuilder).append("long[").append(l2).append("] {\n");
                if (prettyPrintArray) {
                    indent(indentLevel + 1, stringBuilder);

                    for (int i3 = 0; i3 < asLongArray.length; i3++) {
                        if (i3 != 0) {
                            stringBuilder.append(',');
                        }

                        if (i3 % 16 == 0 && i3 / 16 > 0) {
                            stringBuilder.append('\n');
                            if (i3 < asLongArray.length) {
                                indent(indentLevel + 1, stringBuilder);
                            }
                        } else if (i3 != 0) {
                            stringBuilder.append(' ');
                        }

                        stringBuilder.append(String.format(Locale.ROOT, "0x%0" + l + "X", asLongArray[i3]));
                    }
                } else {
                    indent(indentLevel + 1, stringBuilder).append(" // Skipped, supply withBinaryBlobs true");
                }

                stringBuilder.append('\n');
                indent(indentLevel, stringBuilder).append('}');
                break;
            default:
                stringBuilder.append("<UNKNOWN :(>");
        }

        return stringBuilder;
    }

    private static StringBuilder indent(int indentLevel, StringBuilder stringBuilder) {
        int i = stringBuilder.lastIndexOf("\n") + 1;
        int i1 = stringBuilder.length() - i;

        for (int i2 = 0; i2 < 2 * indentLevel - i1; i2++) {
            stringBuilder.append(' ');
        }

        return stringBuilder;
    }

    public static Component toPrettyComponent(Tag tag) {
        return new TextComponentTagVisitor("").visit(tag);
    }

    public static String structureToSnbt(CompoundTag tag) {
        return new SnbtPrinterTagVisitor().visit(packStructureTemplate(tag));
    }

    public static CompoundTag snbtToStructure(String text) throws CommandSyntaxException {
        return unpackStructureTemplate(TagParser.parseTag(text));
    }

    @VisibleForTesting
    static CompoundTag packStructureTemplate(CompoundTag tag) {
        boolean flag = tag.contains("palettes", 9);
        ListTag list;
        if (flag) {
            list = tag.getList("palettes", 9).getList(0);
        } else {
            list = tag.getList("palette", 10);
        }

        ListTag listTag = list.stream()
            .map(CompoundTag.class::cast)
            .map(NbtUtils::packBlockState)
            .map(StringTag::valueOf)
            .collect(Collectors.toCollection(ListTag::new));
        tag.put("palette", listTag);
        if (flag) {
            ListTag listTag1 = new ListTag();
            ListTag list1 = tag.getList("palettes", 9);
            list1.stream().map(ListTag.class::cast).forEach(paletteTag -> {
                CompoundTag compoundTag = new CompoundTag();

                for (int i = 0; i < paletteTag.size(); i++) {
                    compoundTag.putString(listTag.getString(i), packBlockState(paletteTag.getCompound(i)));
                }

                listTag1.add(compoundTag);
            });
            tag.put("palettes", listTag1);
        }

        if (tag.contains("entities", 9)) {
            ListTag listTag1 = tag.getList("entities", 10);
            ListTag list1 = listTag1.stream()
                .map(CompoundTag.class::cast)
                .sorted(Comparator.comparing(entityTag -> entityTag.getList("pos", 6), YXZ_LISTTAG_DOUBLE_COMPARATOR))
                .collect(Collectors.toCollection(ListTag::new));
            tag.put("entities", list1);
        }

        ListTag listTag1 = tag.getList("blocks", 10)
            .stream()
            .map(CompoundTag.class::cast)
            .sorted(Comparator.comparing(blockTag -> blockTag.getList("pos", 3), YXZ_LISTTAG_INT_COMPARATOR))
            .peek(blockTag -> blockTag.putString("state", listTag.getString(blockTag.getInt("state"))))
            .collect(Collectors.toCollection(ListTag::new));
        tag.put("data", listTag1);
        tag.remove("blocks");
        return tag;
    }

    @VisibleForTesting
    static CompoundTag unpackStructureTemplate(CompoundTag tag) {
        ListTag list = tag.getList("palette", 8);
        Map<String, Tag> map = list.stream()
            .map(StringTag.class::cast)
            .map(StringTag::getAsString)
            .collect(ImmutableMap.toImmutableMap(Function.identity(), NbtUtils::unpackBlockState));
        if (tag.contains("palettes", 9)) {
            tag.put(
                "palettes",
                tag.getList("palettes", 10)
                    .stream()
                    .map(CompoundTag.class::cast)
                    .map(
                        paletteTag -> map.keySet()
                            .stream()
                            .map(paletteTag::getString)
                            .map(NbtUtils::unpackBlockState)
                            .collect(Collectors.toCollection(ListTag::new))
                    )
                    .collect(Collectors.toCollection(ListTag::new))
            );
            tag.remove("palette");
        } else {
            tag.put("palette", map.values().stream().collect(Collectors.toCollection(ListTag::new)));
        }

        if (tag.contains("data", 9)) {
            Object2IntMap<String> map1 = new Object2IntOpenHashMap<>();
            map1.defaultReturnValue(-1);

            for (int i = 0; i < list.size(); i++) {
                map1.put(list.getString(i), i);
            }

            ListTag list1 = tag.getList("data", 10);

            for (int i1 = 0; i1 < list1.size(); i1++) {
                CompoundTag compound = list1.getCompound(i1);
                String string = compound.getString("state");
                int _int = map1.getInt(string);
                if (_int == -1) {
                    throw new IllegalStateException("Entry " + string + " missing from palette");
                }

                compound.putInt("state", _int);
            }

            tag.put("blocks", list1);
            tag.remove("data");
        }

        return tag;
    }

    @VisibleForTesting
    static String packBlockState(CompoundTag tag) {
        StringBuilder stringBuilder = new StringBuilder(tag.getString("Name"));
        if (tag.contains("Properties", 10)) {
            CompoundTag compound = tag.getCompound("Properties");
            String string = compound.getAllKeys()
                .stream()
                .sorted()
                .map(propertyKey -> propertyKey + ":" + compound.get(propertyKey).getAsString())
                .collect(Collectors.joining(","));
            stringBuilder.append('{').append(string).append('}');
        }

        return stringBuilder.toString();
    }

    @VisibleForTesting
    static CompoundTag unpackBlockState(String blockStateText) {
        CompoundTag compoundTag = new CompoundTag();
        int index = blockStateText.indexOf(123);
        String sub;
        if (index >= 0) {
            sub = blockStateText.substring(0, index);
            CompoundTag compoundTag1 = new CompoundTag();
            if (index + 2 <= blockStateText.length()) {
                String sub1 = blockStateText.substring(index + 1, blockStateText.indexOf(125, index));
                COMMA_SPLITTER.split(sub1).forEach(stateMetadata -> {
                    List<String> parts = COLON_SPLITTER.splitToList(stateMetadata);
                    if (parts.size() == 2) {
                        compoundTag1.putString(parts.get(0), parts.get(1));
                    } else {
                        LOGGER.error("Something went wrong parsing: '{}' -- incorrect gamedata!", blockStateText);
                    }
                });
                compoundTag.put("Properties", compoundTag1);
            }
        } else {
            sub = blockStateText;
        }

        compoundTag.putString("Name", sub);
        return compoundTag;
    }

    public static CompoundTag addCurrentDataVersion(CompoundTag tag) {
        int version = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        return addDataVersion(tag, version);
    }

    public static CompoundTag addDataVersion(CompoundTag tag, int dataVersion) {
        tag.putInt("DataVersion", dataVersion);
        return tag;
    }

    public static int getDataVersion(CompoundTag tag, int defaultValue) {
        return tag.contains("DataVersion", 99) ? tag.getInt("DataVersion") : defaultValue;
    }
}
