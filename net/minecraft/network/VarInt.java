package net.minecraft.network;

import io.netty.buffer.ByteBuf;

public class VarInt {
    private static final int MAX_VARINT_SIZE = 5;
    private static final int DATA_BITS_MASK = 127;
    private static final int CONTINUATION_BIT_MASK = 128;
    private static final int DATA_BITS_PER_BYTE = 7;

    public static int getByteSize(int data) {
    // Paper start - Optimize VarInts
        return VARINT_EXACT_BYTE_LENGTHS[Integer.numberOfLeadingZeros(data)];
    }
    private static final int[] VARINT_EXACT_BYTE_LENGTHS = new int[33];
    static {
        for (int i = 0; i <= 32; ++i) {
            VARINT_EXACT_BYTE_LENGTHS[i] = (int) Math.ceil((31d - (i - 1)) / 7d);
        }
        VARINT_EXACT_BYTE_LENGTHS[32] = 1; // Special case for the number 0.
    }
    public static int getByteSizeOld(int data) {
    // Paper end - Optimize VarInts
        for (int i = 1; i < 5; i++) {
            if ((data & -1 << i * 7) == 0) {
                return i;
            }
        }

        return 5;
    }

    public static boolean hasContinuationBit(byte data) {
        return (data & 128) == 128;
    }

    public static int read(ByteBuf buffer) {
        int i = 0;
        int i1 = 0;

        byte _byte;
        do {
            _byte = buffer.readByte();
            i |= (_byte & 127) << i1++ * 7;
            if (i1 > 5) {
                throw new RuntimeException("VarInt too big");
            }
        } while (hasContinuationBit(_byte));

        return i;
    }

    public static ByteBuf write(ByteBuf buffer, int value) {
     // Paper start - Optimize VarInts
        // Peel the one and two byte count cases explicitly as they are the most common VarInt sizes
        // that the proxy will write, to improve inlining.
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buffer.writeByte(value);
        } else if ((value & (0xFFFFFFFF << 14)) == 0) {
            int w = (value & 0x7F | 0x80) << 8 | (value >>> 7);
            buffer.writeShort(w);
        } else {
            writeOld(buffer, value);
        }
        return buffer;
    }
    public static ByteBuf writeOld(ByteBuf buffer, int value) {
    // Paper end - Optimize VarInts
        while ((value & -128) != 0) {
            buffer.writeByte(value & 127 | 128);
            value >>>= 7;
        }

        buffer.writeByte(value);
        return buffer;
    }
}
