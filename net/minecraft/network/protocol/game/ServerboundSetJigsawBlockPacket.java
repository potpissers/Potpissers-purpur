package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;

public class ServerboundSetJigsawBlockPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundSetJigsawBlockPacket> STREAM_CODEC = Packet.codec(
        ServerboundSetJigsawBlockPacket::write, ServerboundSetJigsawBlockPacket::new
    );
    private final BlockPos pos;
    private final ResourceLocation name;
    private final ResourceLocation target;
    private final ResourceLocation pool;
    private final String finalState;
    private final JigsawBlockEntity.JointType joint;
    private final int selectionPriority;
    private final int placementPriority;

    public ServerboundSetJigsawBlockPacket(
        BlockPos pos,
        ResourceLocation name,
        ResourceLocation target,
        ResourceLocation pool,
        String finalState,
        JigsawBlockEntity.JointType joint,
        int selectionPriority,
        int placementPriority
    ) {
        this.pos = pos;
        this.name = name;
        this.target = target;
        this.pool = pool;
        this.finalState = finalState;
        this.joint = joint;
        this.selectionPriority = selectionPriority;
        this.placementPriority = placementPriority;
    }

    private ServerboundSetJigsawBlockPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.name = buffer.readResourceLocation();
        this.target = buffer.readResourceLocation();
        this.pool = buffer.readResourceLocation();
        this.finalState = buffer.readUtf();
        this.joint = JigsawBlockEntity.JointType.CODEC.byName(buffer.readUtf(), JigsawBlockEntity.JointType.ALIGNED);
        this.selectionPriority = buffer.readVarInt();
        this.placementPriority = buffer.readVarInt();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeResourceLocation(this.name);
        buffer.writeResourceLocation(this.target);
        buffer.writeResourceLocation(this.pool);
        buffer.writeUtf(this.finalState);
        buffer.writeUtf(this.joint.getSerializedName());
        buffer.writeVarInt(this.selectionPriority);
        buffer.writeVarInt(this.placementPriority);
    }

    @Override
    public PacketType<ServerboundSetJigsawBlockPacket> type() {
        return GamePacketTypes.SERVERBOUND_SET_JIGSAW_BLOCK;
    }

    @Override
    public void handle(ServerGamePacketListener handler) {
        handler.handleSetJigsawBlock(this);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public ResourceLocation getName() {
        return this.name;
    }

    public ResourceLocation getTarget() {
        return this.target;
    }

    public ResourceLocation getPool() {
        return this.pool;
    }

    public String getFinalState() {
        return this.finalState;
    }

    public JigsawBlockEntity.JointType getJoint() {
        return this.joint;
    }

    public int getSelectionPriority() {
        return this.selectionPriority;
    }

    public int getPlacementPriority() {
        return this.placementPriority;
    }
}
