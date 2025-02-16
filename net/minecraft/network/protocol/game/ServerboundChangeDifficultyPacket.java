package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.Difficulty;

public class ServerboundChangeDifficultyPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundChangeDifficultyPacket> STREAM_CODEC = Packet.codec(
        ServerboundChangeDifficultyPacket::write, ServerboundChangeDifficultyPacket::new
    );
    private final Difficulty difficulty;

    public ServerboundChangeDifficultyPacket(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    private ServerboundChangeDifficultyPacket(FriendlyByteBuf buffer) {
        this.difficulty = Difficulty.byId(buffer.readUnsignedByte());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeByte(this.difficulty.getId());
    }

    @Override
    public PacketType<ServerboundChangeDifficultyPacket> type() {
        return GamePacketTypes.SERVERBOUND_CHANGE_DIFFICULTY;
    }

    @Override
    public void handle(ServerGamePacketListener handler) {
        handler.handleChangeDifficulty(this);
    }

    public Difficulty getDifficulty() {
        return this.difficulty;
    }
}
