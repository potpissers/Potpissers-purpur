package net.minecraft.network.protocol.common.custom;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.DebugEntityNameGenerator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public record BeeDebugPayload(BeeDebugPayload.BeeInfo beeInfo) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, BeeDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(BeeDebugPayload::write, BeeDebugPayload::new);
    public static final CustomPacketPayload.Type<BeeDebugPayload> TYPE = CustomPacketPayload.createType("debug/bee");

    private BeeDebugPayload(FriendlyByteBuf buffer) {
        this(new BeeDebugPayload.BeeInfo(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        this.beeInfo.write(buffer);
    }

    @Override
    public CustomPacketPayload.Type<BeeDebugPayload> type() {
        return TYPE;
    }

    public record BeeInfo(
        UUID uuid,
        int id,
        Vec3 pos,
        @Nullable Path path,
        @Nullable BlockPos hivePos,
        @Nullable BlockPos flowerPos,
        int travelTicks,
        Set<String> goals,
        List<BlockPos> blacklistedHives
    ) {
        public BeeInfo(FriendlyByteBuf buffer) {
            this(
                buffer.readUUID(),
                buffer.readInt(),
                buffer.readVec3(),
                buffer.readNullable(Path::createFromStream),
                buffer.readNullable(BlockPos.STREAM_CODEC),
                buffer.readNullable(BlockPos.STREAM_CODEC),
                buffer.readInt(),
                buffer.readCollection(HashSet::new, FriendlyByteBuf::readUtf),
                buffer.readList(BlockPos.STREAM_CODEC)
            );
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeUUID(this.uuid);
            buffer.writeInt(this.id);
            buffer.writeVec3(this.pos);
            buffer.writeNullable(this.path, (buffer1, value) -> value.writeToStream(buffer1));
            buffer.writeNullable(this.hivePos, BlockPos.STREAM_CODEC);
            buffer.writeNullable(this.flowerPos, BlockPos.STREAM_CODEC);
            buffer.writeInt(this.travelTicks);
            buffer.writeCollection(this.goals, FriendlyByteBuf::writeUtf);
            buffer.writeCollection(this.blacklistedHives, BlockPos.STREAM_CODEC);
        }

        public boolean hasHive(BlockPos pos) {
            return Objects.equals(pos, this.hivePos);
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
