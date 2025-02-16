package net.minecraft.world.level.block;

import com.mojang.math.OctahedralGroup;
import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

public enum Rotation implements StringRepresentable {
    NONE("none", OctahedralGroup.IDENTITY),
    CLOCKWISE_90("clockwise_90", OctahedralGroup.ROT_90_Y_NEG),
    CLOCKWISE_180("180", OctahedralGroup.ROT_180_FACE_XZ),
    COUNTERCLOCKWISE_90("counterclockwise_90", OctahedralGroup.ROT_90_Y_POS);

    public static final Codec<Rotation> CODEC = StringRepresentable.fromEnum(Rotation::values);
    private final String id;
    private final OctahedralGroup rotation;

    private Rotation(final String id, final OctahedralGroup rotation) {
        this.id = id;
        this.rotation = rotation;
    }

    public Rotation getRotated(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> {
                switch (this) {
                    case NONE:
                        yield CLOCKWISE_90;
                    case CLOCKWISE_90:
                        yield CLOCKWISE_180;
                    case CLOCKWISE_180:
                        yield COUNTERCLOCKWISE_90;
                    case COUNTERCLOCKWISE_90:
                        yield NONE;
                    default:
                        throw new MatchException(null, null);
                }
            }
            case CLOCKWISE_180 -> {
                switch (this) {
                    case NONE:
                        yield CLOCKWISE_180;
                    case CLOCKWISE_90:
                        yield COUNTERCLOCKWISE_90;
                    case CLOCKWISE_180:
                        yield NONE;
                    case COUNTERCLOCKWISE_90:
                        yield CLOCKWISE_90;
                    default:
                        throw new MatchException(null, null);
                }
            }
            case COUNTERCLOCKWISE_90 -> {
                switch (this) {
                    case NONE:
                        yield COUNTERCLOCKWISE_90;
                    case CLOCKWISE_90:
                        yield NONE;
                    case CLOCKWISE_180:
                        yield CLOCKWISE_90;
                    case COUNTERCLOCKWISE_90:
                        yield CLOCKWISE_180;
                    default:
                        throw new MatchException(null, null);
                }
            }
            default -> this;
        };
    }

    public OctahedralGroup rotation() {
        return this.rotation;
    }

    public Direction rotate(Direction facing) {
        if (facing.getAxis() == Direction.Axis.Y) {
            return facing;
        } else {
            return switch (this) {
                case CLOCKWISE_90 -> facing.getClockWise();
                case CLOCKWISE_180 -> facing.getOpposite();
                case COUNTERCLOCKWISE_90 -> facing.getCounterClockWise();
                default -> facing;
            };
        }
    }

    public int rotate(int rotation, int positionCount) {
        return switch (this) {
            case CLOCKWISE_90 -> (rotation + positionCount / 4) % positionCount;
            case CLOCKWISE_180 -> (rotation + positionCount / 2) % positionCount;
            case COUNTERCLOCKWISE_90 -> (rotation + positionCount * 3 / 4) % positionCount;
            default -> rotation;
        };
    }

    public static Rotation getRandom(RandomSource random) {
        return Util.getRandom(values(), random);
    }

    public static List<Rotation> getShuffled(RandomSource random) {
        return Util.shuffledCopy(values(), random);
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }
}
