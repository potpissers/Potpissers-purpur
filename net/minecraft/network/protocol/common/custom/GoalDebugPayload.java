package net.minecraft.network.protocol.common.custom;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record GoalDebugPayload(int entityId, BlockPos pos, List<GoalDebugPayload.DebugGoal> goals) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, GoalDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(GoalDebugPayload::write, GoalDebugPayload::new);
    public static final CustomPacketPayload.Type<GoalDebugPayload> TYPE = CustomPacketPayload.createType("debug/goal_selector");

    private GoalDebugPayload(FriendlyByteBuf buffer) {
        this(buffer.readInt(), buffer.readBlockPos(), buffer.readList(GoalDebugPayload.DebugGoal::new));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeInt(this.entityId);
        buffer.writeBlockPos(this.pos);
        buffer.writeCollection(this.goals, (buffer1, value) -> value.write(buffer1));
    }

    @Override
    public CustomPacketPayload.Type<GoalDebugPayload> type() {
        return TYPE;
    }

    public record DebugGoal(int priority, boolean isRunning, String name) {
        public DebugGoal(FriendlyByteBuf buffer) {
            this(buffer.readInt(), buffer.readBoolean(), buffer.readUtf(255));
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeInt(this.priority);
            buffer.writeBoolean(this.isRunning);
            buffer.writeUtf(this.name);
        }
    }
}
