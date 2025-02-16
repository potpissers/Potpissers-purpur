package net.minecraft.network.protocol.game;

import java.util.BitSet;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

public class ClientboundLevelChunkWithLightPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundLevelChunkWithLightPacket> STREAM_CODEC = Packet.codec(
        ClientboundLevelChunkWithLightPacket::write, ClientboundLevelChunkWithLightPacket::new
    );
    private final int x;
    private final int z;
    private final ClientboundLevelChunkPacketData chunkData;
    private final ClientboundLightUpdatePacketData lightData;
    // Paper start - Anti-Xray
    public void setReady(final boolean ready) {
        // Empty hook, updated by feature patch
    }
    // Paper end - Anti-Xray

    public ClientboundLevelChunkWithLightPacket(LevelChunk chunk, LevelLightEngine lightEngine, @Nullable BitSet skyLight, @Nullable BitSet blockLight) {
        ChunkPos pos = chunk.getPos();
        this.x = pos.x;
        this.z = pos.z;
        this.chunkData = new ClientboundLevelChunkPacketData(chunk);
        this.lightData = new ClientboundLightUpdatePacketData(pos, lightEngine, skyLight, blockLight);
    }

    private ClientboundLevelChunkWithLightPacket(RegistryFriendlyByteBuf buffer) {
        this.x = buffer.readInt();
        this.z = buffer.readInt();
        this.chunkData = new ClientboundLevelChunkPacketData(buffer, this.x, this.z);
        this.lightData = new ClientboundLightUpdatePacketData(buffer, this.x, this.z);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(this.x);
        buffer.writeInt(this.z);
        this.chunkData.write(buffer);
        this.lightData.write(buffer);
    }

    @Override
    public PacketType<ClientboundLevelChunkWithLightPacket> type() {
        return GamePacketTypes.CLIENTBOUND_LEVEL_CHUNK_WITH_LIGHT;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleLevelChunkWithLight(this);
    }

    public int getX() {
        return this.x;
    }

    public int getZ() {
        return this.z;
    }

    public ClientboundLevelChunkPacketData getChunkData() {
        return this.chunkData;
    }

    public ClientboundLightUpdatePacketData getLightData() {
        return this.lightData;
    }
}
