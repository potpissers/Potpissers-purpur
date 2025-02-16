package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

public class WorldCoordinate {
    private static final char PREFIX_RELATIVE = '~';
    public static final SimpleCommandExceptionType ERROR_EXPECTED_DOUBLE = new SimpleCommandExceptionType(Component.translatable("argument.pos.missing.double"));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_INT = new SimpleCommandExceptionType(Component.translatable("argument.pos.missing.int"));
    private final boolean relative;
    private final double value;

    public WorldCoordinate(boolean relative, double value) {
        this.relative = relative;
        this.value = value;
    }

    public double get(double coord) {
        return this.relative ? this.value + coord : this.value;
    }

    public static WorldCoordinate parseDouble(StringReader reader, boolean centerCorrect) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '^') {
            throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(reader);
        } else if (!reader.canRead()) {
            throw ERROR_EXPECTED_DOUBLE.createWithContext(reader);
        } else {
            boolean isRelative = isRelative(reader);
            int cursor = reader.getCursor();
            double d = reader.canRead() && reader.peek() != ' ' ? reader.readDouble() : 0.0;
            String sub = reader.getString().substring(cursor, reader.getCursor());
            if (isRelative && sub.isEmpty()) {
                return new WorldCoordinate(true, 0.0);
            } else {
                if (!sub.contains(".") && !isRelative && centerCorrect) {
                    d += 0.5;
                }

                return new WorldCoordinate(isRelative, d);
            }
        }
    }

    public static WorldCoordinate parseInt(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '^') {
            throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(reader);
        } else if (!reader.canRead()) {
            throw ERROR_EXPECTED_INT.createWithContext(reader);
        } else {
            boolean isRelative = isRelative(reader);
            double d;
            if (reader.canRead() && reader.peek() != ' ') {
                d = isRelative ? reader.readDouble() : reader.readInt();
            } else {
                d = 0.0;
            }

            return new WorldCoordinate(isRelative, d);
        }
    }

    public static boolean isRelative(StringReader reader) {
        boolean flag;
        if (reader.peek() == '~') {
            flag = true;
            reader.skip();
        } else {
            flag = false;
        }

        return flag;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof WorldCoordinate worldCoordinate
                && this.relative == worldCoordinate.relative
                && Double.compare(worldCoordinate.value, this.value) == 0;
    }

    @Override
    public int hashCode() {
        int i = this.relative ? 1 : 0;
        long l = Double.doubleToLongBits(this.value);
        return 31 * i + (int)(l ^ l >>> 32);
    }

    public boolean isRelative() {
        return this.relative;
    }
}
