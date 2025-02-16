package net.minecraft.network.protocol.common.custom;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.DebugEntityNameGenerator;

public record BreezeDebugPayload(BreezeDebugPayload.BreezeInfo breezeInfo) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, BreezeDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(
        BreezeDebugPayload::write, BreezeDebugPayload::new
    );
    public static final CustomPacketPayload.Type<BreezeDebugPayload> TYPE = CustomPacketPayload.createType("debug/breeze");

    private BreezeDebugPayload(FriendlyByteBuf buffer) {
        this(new BreezeDebugPayload.BreezeInfo(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        this.breezeInfo.write(buffer);
    }

    @Override
    public CustomPacketPayload.Type<BreezeDebugPayload> type() {
        return TYPE;
    }

    public record BreezeInfo(UUID uuid, int id, Integer attackTarget, BlockPos jumpTarget) {
        public BreezeInfo(FriendlyByteBuf buffer) {
            this(buffer.readUUID(), buffer.readInt(), buffer.readNullable(FriendlyByteBuf::readInt), buffer.readNullable(BlockPos.STREAM_CODEC));
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeUUID(this.uuid);
            buffer.writeInt(this.id);
            buffer.writeNullable(this.attackTarget, FriendlyByteBuf::writeInt);
            buffer.writeNullable(this.jumpTarget, BlockPos.STREAM_CODEC);
        }

        public String generateName() {
            return DebugEntityNameGenerator.getEntityName(this.uuid);
        }

        @Override
        public String toString() {
            return this.generateName();
        }
    }
}
