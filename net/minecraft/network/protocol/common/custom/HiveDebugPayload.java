package net.minecraft.network.protocol.common.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record HiveDebugPayload(HiveDebugPayload.HiveInfo hiveInfo) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, HiveDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(HiveDebugPayload::write, HiveDebugPayload::new);
    public static final CustomPacketPayload.Type<HiveDebugPayload> TYPE = CustomPacketPayload.createType("debug/hive");

    private HiveDebugPayload(FriendlyByteBuf buffer) {
        this(new HiveDebugPayload.HiveInfo(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        this.hiveInfo.write(buffer);
    }

    @Override
    public CustomPacketPayload.Type<HiveDebugPayload> type() {
        return TYPE;
    }

    public record HiveInfo(BlockPos pos, String hiveType, int occupantCount, int honeyLevel, boolean sedated) {
        public HiveInfo(FriendlyByteBuf buffer) {
            this(buffer.readBlockPos(), buffer.readUtf(), buffer.readInt(), buffer.readInt(), buffer.readBoolean());
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(this.pos);
            buffer.writeUtf(this.hiveType);
            buffer.writeInt(this.occupantCount);
            buffer.writeInt(this.honeyLevel);
            buffer.writeBoolean(this.sedated);
        }
    }
}
