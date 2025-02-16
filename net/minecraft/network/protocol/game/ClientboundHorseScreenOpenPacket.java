package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundHorseScreenOpenPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundHorseScreenOpenPacket> STREAM_CODEC = Packet.codec(
        ClientboundHorseScreenOpenPacket::write, ClientboundHorseScreenOpenPacket::new
    );
    private final int containerId;
    private final int inventoryColumns;
    private final int entityId;

    public ClientboundHorseScreenOpenPacket(int containerId, int size, int entityId) {
        this.containerId = containerId;
        this.inventoryColumns = size;
        this.entityId = entityId;
    }

    private ClientboundHorseScreenOpenPacket(FriendlyByteBuf buffer) {
        this.containerId = buffer.readContainerId();
        this.inventoryColumns = buffer.readVarInt();
        this.entityId = buffer.readInt();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeContainerId(this.containerId);
        buffer.writeVarInt(this.inventoryColumns);
        buffer.writeInt(this.entityId);
    }

    @Override
    public PacketType<ClientboundHorseScreenOpenPacket> type() {
        return GamePacketTypes.CLIENTBOUND_HORSE_SCREEN_OPEN;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleHorseScreenOpen(this);
    }

    public int getContainerId() {
        return this.containerId;
    }

    public int getInventoryColumns() {
        return this.inventoryColumns;
    }

    public int getEntityId() {
        return this.entityId;
    }
}
