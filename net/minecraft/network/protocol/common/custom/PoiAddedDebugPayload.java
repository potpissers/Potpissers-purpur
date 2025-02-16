package net.minecraft.network.protocol.common.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record PoiAddedDebugPayload(BlockPos pos, String poiType, int freeTicketCount) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, PoiAddedDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(
        PoiAddedDebugPayload::write, PoiAddedDebugPayload::new
    );
    public static final CustomPacketPayload.Type<PoiAddedDebugPayload> TYPE = CustomPacketPayload.createType("debug/poi_added");

    private PoiAddedDebugPayload(FriendlyByteBuf buffer) {
        this(buffer.readBlockPos(), buffer.readUtf(), buffer.readInt());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeUtf(this.poiType);
        buffer.writeInt(this.freeTicketCount);
    }

    @Override
    public CustomPacketPayload.Type<PoiAddedDebugPayload> type() {
        return TYPE;
    }
}
