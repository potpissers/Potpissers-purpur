package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ServerboundContainerSlotStateChangedPacket(int slotId, int containerId, boolean newState) implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundContainerSlotStateChangedPacket> STREAM_CODEC = Packet.codec(
        ServerboundContainerSlotStateChangedPacket::write, ServerboundContainerSlotStateChangedPacket::new
    );

    private ServerboundContainerSlotStateChangedPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readContainerId(), buffer.readBoolean());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.slotId);
        buffer.writeContainerId(this.containerId);
        buffer.writeBoolean(this.newState);
    }

    @Override
    public PacketType<ServerboundContainerSlotStateChangedPacket> type() {
        return GamePacketTypes.SERVERBOUND_CONTAINER_SLOT_STATE_CHANGED;
    }

    @Override
    public void handle(ServerGamePacketListener handler) {
        handler.handleContainerSlotStateChanged(this);
    }
}
