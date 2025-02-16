package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Objects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class LocalCoordinates implements Coordinates {
    public static final char PREFIX_LOCAL_COORDINATE = '^';
    private final double left;
    private final double up;
    private final double forwards;

    public LocalCoordinates(double left, double up, double forwards) {
        this.left = left;
        this.up = up;
        this.forwards = forwards;
    }

    @Override
    public Vec3 getPosition(CommandSourceStack source) {
        Vec2 rotation = source.getRotation();
        Vec3 vec3 = source.getAnchor().apply(source);
        float cos = Mth.cos((rotation.y + 90.0F) * (float) (Math.PI / 180.0));
        float sin = Mth.sin((rotation.y + 90.0F) * (float) (Math.PI / 180.0));
        float cos1 = Mth.cos(-rotation.x * (float) (Math.PI / 180.0));
        float sin1 = Mth.sin(-rotation.x * (float) (Math.PI / 180.0));
        float cos2 = Mth.cos((-rotation.x + 90.0F) * (float) (Math.PI / 180.0));
        float sin2 = Mth.sin((-rotation.x + 90.0F) * (float) (Math.PI / 180.0));
        Vec3 vec31 = new Vec3(cos * cos1, sin1, sin * cos1);
        Vec3 vec32 = new Vec3(cos * cos2, sin2, sin * cos2);
        Vec3 vec33 = vec31.cross(vec32).scale(-1.0);
        double d = vec31.x * this.forwards + vec32.x * this.up + vec33.x * this.left;
        double d1 = vec31.y * this.forwards + vec32.y * this.up + vec33.y * this.left;
        double d2 = vec31.z * this.forwards + vec32.z * this.up + vec33.z * this.left;
        return new Vec3(vec3.x + d, vec3.y + d1, vec3.z + d2);
    }

    @Override
    public Vec2 getRotation(CommandSourceStack source) {
        return Vec2.ZERO;
    }

    @Override
    public boolean isXRelative() {
        return true;
    }

    @Override
    public boolean isYRelative() {
        return true;
    }

    @Override
    public boolean isZRelative() {
        return true;
    }

    public static LocalCoordinates parse(StringReader reader) throws CommandSyntaxException {
        int cursor = reader.getCursor();
        double _double = readDouble(reader, cursor);
        if (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
            double _double1 = readDouble(reader, cursor);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                double _double2 = readDouble(reader, cursor);
                return new LocalCoordinates(_double, _double1, _double2);
            } else {
                reader.setCursor(cursor);
                throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
            }
        } else {
            reader.setCursor(cursor);
            throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
        }
    }

    private static double readDouble(StringReader reader, int start) throws CommandSyntaxException {
        if (!reader.canRead()) {
            throw WorldCoordinate.ERROR_EXPECTED_DOUBLE.createWithContext(reader);
        } else if (reader.peek() != '^') {
            reader.setCursor(start);
            throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(reader);
        } else {
            reader.skip();
            return reader.canRead() && reader.peek() != ' ' ? reader.readDouble() : 0.0;
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof LocalCoordinates localCoordinates
                && this.left == localCoordinates.left
                && this.up == localCoordinates.up
                && this.forwards == localCoordinates.forwards;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.left, this.up, this.forwards);
    }
}
