package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.debugchart.RemoteDebugSampleType;

public record ServerboundDebugSampleSubscriptionPacket(RemoteDebugSampleType sampleType) implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundDebugSampleSubscriptionPacket> STREAM_CODEC = Packet.codec(
        ServerboundDebugSampleSubscriptionPacket::write, ServerboundDebugSampleSubscriptionPacket::new
    );

    private ServerboundDebugSampleSubscriptionPacket(FriendlyByteBuf buffer) {
        this(buffer.readEnum(RemoteDebugSampleType.class));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.sampleType);
    }

    @Override
    public PacketType<ServerboundDebugSampleSubscriptionPacket> type() {
        return GamePacketTypes.SERVERBOUND_DEBUG_SAMPLE_SUBSCRIPTION;
    }

    @Override
    public void handle(ServerGamePacketListener handler) {
        handler.handleDebugSampleSubscription(this);
    }
}
