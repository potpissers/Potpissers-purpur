package net.minecraft.world.level.saveddata.maps;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

public class MapFrame {
    private final BlockPos pos;
    private final int rotation;
    private final int entityId;

    public MapFrame(BlockPos pos, int rotation, int entityId) {
        this.pos = pos;
        this.rotation = rotation;
        this.entityId = entityId;
    }

    @Nullable
    public static MapFrame load(CompoundTag compoundTag) {
        Optional<BlockPos> blockPos = NbtUtils.readBlockPos(compoundTag, "pos");
        if (blockPos.isEmpty()) {
            return null;
        } else {
            int _int = compoundTag.getInt("rotation");
            int _int1 = compoundTag.getInt("entity_id");
            return new MapFrame(blockPos.get(), _int, _int1);
        }
    }

    public CompoundTag save() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.put("pos", NbtUtils.writeBlockPos(this.pos));
        compoundTag.putInt("rotation", this.rotation);
        compoundTag.putInt("entity_id", this.entityId);
        return compoundTag;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int getRotation() {
        return this.rotation;
    }

    public int getEntityId() {
        return this.entityId;
    }

    public String getId() {
        return frameId(this.pos);
    }

    public static String frameId(BlockPos pos) {
        return "frame-" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
