package net.minecraft;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Contract;

public enum ChatFormatting implements StringRepresentable {
    BLACK("BLACK", '0', 0, 0),
    DARK_BLUE("DARK_BLUE", '1', 1, 170),
    DARK_GREEN("DARK_GREEN", '2', 2, 43520),
    DARK_AQUA("DARK_AQUA", '3', 3, 43690),
    DARK_RED("DARK_RED", '4', 4, 11141120),
    DARK_PURPLE("DARK_PURPLE", '5', 5, 11141290),
    GOLD("GOLD", '6', 6, 16755200),
    GRAY("GRAY", '7', 7, 11184810),
    DARK_GRAY("DARK_GRAY", '8', 8, 5592405),
    BLUE("BLUE", '9', 9, 5592575),
    GREEN("GREEN", 'a', 10, 5635925),
    AQUA("AQUA", 'b', 11, 5636095),
    RED("RED", 'c', 12, 16733525),
    LIGHT_PURPLE("LIGHT_PURPLE", 'd', 13, 16733695),
    YELLOW("YELLOW", 'e', 14, 16777045),
    WHITE("WHITE", 'f', 15, 16777215),
    OBFUSCATED("OBFUSCATED", 'k', true),
    BOLD("BOLD", 'l', true),
    STRIKETHROUGH("STRIKETHROUGH", 'm', true),
    UNDERLINE("UNDERLINE", 'n', true),
    ITALIC("ITALIC", 'o', true),
    RESET("RESET", 'r', -1, null);

    public static final Codec<ChatFormatting> CODEC = StringRepresentable.fromEnum(ChatFormatting::values);
    public static final char PREFIX_CODE = '§';
    private static final Map<String, ChatFormatting> FORMATTING_BY_NAME = Arrays.stream(values())
        .collect(Collectors.toMap(formatting -> cleanName(formatting.name), chatFormatter -> (ChatFormatting)chatFormatter));
    private static final Pattern STRIP_FORMATTING_PATTERN = Pattern.compile("(?i)§[0-9A-FK-OR]");
    private final String name;
    private final char code;
    private final boolean isFormat;
    private final String toString;
    private final int id;
    @Nullable
    private final Integer color;

    private static String cleanName(String string) {
        return string.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private ChatFormatting(final String name, final char code, final int id, @Nullable final Integer color) {
        this(name, code, false, id, color);
    }

    private ChatFormatting(final String name, final char code, final boolean isFormat) {
        this(name, code, isFormat, -1, null);
    }

    private ChatFormatting(final String name, final char code, final boolean isFormat, final int id, @Nullable final Integer color) {
        this.name = name;
        this.code = code;
        this.isFormat = isFormat;
        this.id = id;
        this.color = color;
        this.toString = "§" + code;
    }

    public char getChar() {
        return this.code;
    }

    public int getId() {
        return this.id;
    }

    public boolean isFormat() {
        return this.isFormat;
    }

    public boolean isColor() {
        return !this.isFormat && this != RESET;
    }

    @Nullable
    public Integer getColor() {
        return this.color;
    }

    public String getName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return this.toString;
    }

    @Nullable
    @Contract("!null->!null;_->_")
    public static String stripFormatting(@Nullable String text) {
        return text == null ? null : STRIP_FORMATTING_PATTERN.matcher(text).replaceAll("");
    }

    @Nullable
    public static ChatFormatting getByName(@Nullable String friendlyName) {
        return friendlyName == null ? null : FORMATTING_BY_NAME.get(cleanName(friendlyName));
    }

    @Nullable
    public static ChatFormatting getById(int index) {
        if (index < 0) {
            return RESET;
        } else {
            for (ChatFormatting chatFormatting : values()) {
                if (chatFormatting.getId() == index) {
                    return chatFormatting;
                }
            }

            return null;
        }
    }

    @Nullable
    public static ChatFormatting getByCode(char formattingCode) {
        char c = Character.toLowerCase(formattingCode);

        for (ChatFormatting chatFormatting : values()) {
            if (chatFormatting.code == c) {
                return chatFormatting;
            }
        }

        return null;
    }

    public static Collection<String> getNames(boolean getColor, boolean getFancyStyling) {
        List<String> list = Lists.newArrayList();

        for (ChatFormatting chatFormatting : values()) {
            if ((!chatFormatting.isColor() || getColor) && (!chatFormatting.isFormat() || getFancyStyling)) {
                list.add(chatFormatting.getName());
            }
        }

        return list;
    }

    @Override
    public String getSerializedName() {
        return this.getName();
    }
}
