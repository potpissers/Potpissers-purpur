package net.minecraft.world.level;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

public interface LevelWriter {
    boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft);

    default boolean setBlock(BlockPos pos, BlockState newState, int flags) {
        return this.setBlock(pos, newState, flags, 512);
    }

    boolean removeBlock(BlockPos pos, boolean isMoving);

    default boolean destroyBlock(BlockPos pos, boolean dropBlock) {
        return this.destroyBlock(pos, dropBlock, null);
    }

    default boolean destroyBlock(BlockPos pos, boolean dropBlock, @Nullable Entity entity) {
        return this.destroyBlock(pos, dropBlock, entity, 512);
    }

    boolean destroyBlock(BlockPos pos, boolean dropBlock, @Nullable Entity entity, int recursionLeft);

    default boolean addFreshEntity(Entity entity) {
        return false;
    }

    // CraftBukkit start
    default boolean addFreshEntity(Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        return false;
    }
    // CraftBukkit end
}
