package net.minecraft.network.protocol.game;

import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

public class ClientboundLevelChunkPacketData {
    private static final int TWO_MEGABYTES = 2097152;
    private final CompoundTag heightmaps;
    private final byte[] buffer;
    private final List<ClientboundLevelChunkPacketData.BlockEntityInfo> blockEntitiesData;

    public ClientboundLevelChunkPacketData(LevelChunk levelChunk) {
        this.heightmaps = new CompoundTag();

        for (Entry<Heightmap.Types, Heightmap> entry : levelChunk.getHeightmaps()) {
            if (entry.getKey().sendToClient()) {
                this.heightmaps.put(entry.getKey().getSerializationKey(), new LongArrayTag(entry.getValue().getRawData()));
            }
        }

        this.buffer = new byte[calculateChunkSize(levelChunk)];
        extractChunkData(new FriendlyByteBuf(this.getWriteBuffer()), levelChunk);
        this.blockEntitiesData = Lists.newArrayList();

        for (Entry<BlockPos, BlockEntity> entryx : levelChunk.getBlockEntities().entrySet()) {
            this.blockEntitiesData.add(ClientboundLevelChunkPacketData.BlockEntityInfo.create(entryx.getValue()));
        }
    }

    public ClientboundLevelChunkPacketData(RegistryFriendlyByteBuf buffer, int x, int z) {
        this.heightmaps = buffer.readNbt();
        if (this.heightmaps == null) {
            throw new RuntimeException("Can't read heightmap in packet for [" + x + ", " + z + "]");
        } else {
            int varInt = buffer.readVarInt();
            if (varInt > 2097152) { // Paper - diff on change - if this changes, update PacketEncoder
                throw new RuntimeException("Chunk Packet trying to allocate too much memory on read.");
            } else {
                this.buffer = new byte[varInt];
                buffer.readBytes(this.buffer);
                this.blockEntitiesData = ClientboundLevelChunkPacketData.BlockEntityInfo.LIST_STREAM_CODEC.decode(buffer);
            }
        }
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeNbt(this.heightmaps);
        buffer.writeVarInt(this.buffer.length);
        buffer.writeBytes(this.buffer);
        ClientboundLevelChunkPacketData.BlockEntityInfo.LIST_STREAM_CODEC.encode(buffer, this.blockEntitiesData);
    }

    private static int calculateChunkSize(LevelChunk chunk) {
        int i = 0;

        for (LevelChunkSection levelChunkSection : chunk.getSections()) {
            i += levelChunkSection.getSerializedSize();
        }

        return i;
    }

    private ByteBuf getWriteBuffer() {
        ByteBuf byteBuf = Unpooled.wrappedBuffer(this.buffer);
        byteBuf.writerIndex(0);
        return byteBuf;
    }

    public static void extractChunkData(FriendlyByteBuf buffer, LevelChunk chunk) {
        for (LevelChunkSection levelChunkSection : chunk.getSections()) {
            levelChunkSection.write(buffer);
        }
    }

    public Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> getBlockEntitiesTagsConsumer(int chunkX, int chunkZ) {
        return blockEntityTagOutput -> this.getBlockEntitiesTags(blockEntityTagOutput, chunkX, chunkZ);
    }

    private void getBlockEntitiesTags(ClientboundLevelChunkPacketData.BlockEntityTagOutput output, int chunkX, int chunkZ) {
        int i = 16 * chunkX;
        int i1 = 16 * chunkZ;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (ClientboundLevelChunkPacketData.BlockEntityInfo blockEntityInfo : this.blockEntitiesData) {
            int i2 = i + SectionPos.sectionRelative(blockEntityInfo.packedXZ >> 4);
            int i3 = i1 + SectionPos.sectionRelative(blockEntityInfo.packedXZ);
            mutableBlockPos.set(i2, blockEntityInfo.y, i3);
            output.accept(mutableBlockPos, blockEntityInfo.type, blockEntityInfo.tag);
        }
    }

    public FriendlyByteBuf getReadBuffer() {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(this.buffer));
    }

    public CompoundTag getHeightmaps() {
        return this.heightmaps;
    }

    static class BlockEntityInfo {
        public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundLevelChunkPacketData.BlockEntityInfo> STREAM_CODEC = StreamCodec.ofMember(
            ClientboundLevelChunkPacketData.BlockEntityInfo::write, ClientboundLevelChunkPacketData.BlockEntityInfo::new
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, List<ClientboundLevelChunkPacketData.BlockEntityInfo>> LIST_STREAM_CODEC = STREAM_CODEC.apply(
            ByteBufCodecs.list()
        );
        final int packedXZ;
        final int y;
        final BlockEntityType<?> type;
        @Nullable
        final CompoundTag tag;

        private BlockEntityInfo(int packedXZ, int y, BlockEntityType<?> type, @Nullable CompoundTag tag) {
            this.packedXZ = packedXZ;
            this.y = y;
            this.type = type;
            this.tag = tag;
        }

        private BlockEntityInfo(RegistryFriendlyByteBuf buffer) {
            this.packedXZ = buffer.readByte();
            this.y = buffer.readShort();
            this.type = ByteBufCodecs.registry(Registries.BLOCK_ENTITY_TYPE).decode(buffer);
            this.tag = buffer.readNbt();
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeByte(this.packedXZ);
            buffer.writeShort(this.y);
            ByteBufCodecs.registry(Registries.BLOCK_ENTITY_TYPE).encode(buffer, this.type);
            buffer.writeNbt(this.tag);
        }

        static ClientboundLevelChunkPacketData.BlockEntityInfo create(BlockEntity blockEntity) {
            CompoundTag updateTag = blockEntity.getUpdateTag(blockEntity.getLevel().registryAccess());
            BlockPos blockPos = blockEntity.getBlockPos();
            int i = SectionPos.sectionRelative(blockPos.getX()) << 4 | SectionPos.sectionRelative(blockPos.getZ());
            blockEntity.sanitizeSentNbt(updateTag); // Paper - Sanitize sent data
            return new ClientboundLevelChunkPacketData.BlockEntityInfo(i, blockPos.getY(), blockEntity.getType(), updateTag.isEmpty() ? null : updateTag);
        }
    }

    @FunctionalInterface
    public interface BlockEntityTagOutput {
        void accept(BlockPos pos, BlockEntityType<?> type, @Nullable CompoundTag nbt);
    }
}
