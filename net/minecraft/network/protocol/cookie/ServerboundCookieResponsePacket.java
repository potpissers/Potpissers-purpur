package net.minecraft.network.protocol.cookie;

import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.common.ClientboundStoreCookiePacket;
import net.minecraft.resources.ResourceLocation;

public record ServerboundCookieResponsePacket(ResourceLocation key, @Nullable byte[] payload) implements Packet<ServerCookiePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundCookieResponsePacket> STREAM_CODEC = Packet.codec(
        ServerboundCookieResponsePacket::write, ServerboundCookieResponsePacket::new
    );

    private ServerboundCookieResponsePacket(FriendlyByteBuf buffer) {
        this(buffer.readResourceLocation(), buffer.readNullable(ClientboundStoreCookiePacket.PAYLOAD_STREAM_CODEC));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(this.key);
        buffer.writeNullable(this.payload, ClientboundStoreCookiePacket.PAYLOAD_STREAM_CODEC);
    }

    @Override
    public PacketType<ServerboundCookieResponsePacket> type() {
        return CookiePacketTypes.SERVERBOUND_COOKIE_RESPONSE;
    }

    @Override
    public void handle(ServerCookiePacketListener handler) {
        handler.handleCookieResponse(this);
    }
}
