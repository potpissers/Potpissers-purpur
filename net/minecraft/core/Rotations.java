package net.minecraft.core;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public class Rotations {
    public static final StreamCodec<ByteBuf, Rotations> STREAM_CODEC = new StreamCodec<ByteBuf, Rotations>() {
        @Override
        public Rotations decode(ByteBuf buffer) {
            return new Rotations(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        }

        @Override
        public void encode(ByteBuf buffer, Rotations value) {
            buffer.writeFloat(value.x);
            buffer.writeFloat(value.y);
            buffer.writeFloat(value.z);
        }
    };
    protected final float x;
    protected final float y;
    protected final float z;

    public Rotations(float x, float y, float z) {
        this.x = !Float.isInfinite(x) && !Float.isNaN(x) ? x % 360.0F : 0.0F;
        this.y = !Float.isInfinite(y) && !Float.isNaN(y) ? y % 360.0F : 0.0F;
        this.z = !Float.isInfinite(z) && !Float.isNaN(z) ? z % 360.0F : 0.0F;
    }

    public Rotations(ListTag tag) {
        this(tag.getFloat(0), tag.getFloat(1), tag.getFloat(2));
    }

    // Paper start - faster alternative constructor
    private Rotations(float x, float y, float z, Void dummy_var) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Rotations createWithoutValidityChecks(float x, float y, float z) {
        return new Rotations(x, y, z, null);
    }
    // Paper end - faster alternative constructor

    public ListTag save() {
        ListTag listTag = new ListTag();
        listTag.add(FloatTag.valueOf(this.x));
        listTag.add(FloatTag.valueOf(this.y));
        listTag.add(FloatTag.valueOf(this.z));
        return listTag;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Rotations rotations && this.x == rotations.x && this.y == rotations.y && this.z == rotations.z;
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public float getZ() {
        return this.z;
    }

    public float getWrappedX() {
        return Mth.wrapDegrees(this.x);
    }

    public float getWrappedY() {
        return Mth.wrapDegrees(this.y);
    }

    public float getWrappedZ() {
        return Mth.wrapDegrees(this.z);
    }
}
