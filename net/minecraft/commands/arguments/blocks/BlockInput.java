package net.minecraft.commands.arguments.blocks;

import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.properties.Property;

public class BlockInput implements Predicate<BlockInWorld> {
    private final BlockState state;
    private final Set<Property<?>> properties;
    @Nullable
    private final CompoundTag tag;

    public BlockInput(BlockState state, Set<Property<?>> properties, @Nullable CompoundTag tag) {
        this.state = state;
        this.properties = properties;
        this.tag = tag;
    }

    public BlockState getState() {
        return this.state;
    }

    public Set<Property<?>> getDefinedProperties() {
        return this.properties;
    }

    @Override
    public boolean test(BlockInWorld block) {
        BlockState state = block.getState();
        if (!state.is(this.state.getBlock())) {
            return false;
        } else {
            for (Property<?> property : this.properties) {
                if (state.getValue(property) != this.state.getValue(property)) {
                    return false;
                }
            }

            if (this.tag == null) {
                return true;
            } else {
                BlockEntity entity = block.getEntity();
                return entity != null && NbtUtils.compareNbt(this.tag, entity.saveWithFullMetadata(block.getLevel().registryAccess()), true);
            }
        }
    }

    public boolean test(ServerLevel level, BlockPos pos) {
        return this.test(new BlockInWorld(level, pos, false));
    }

    public boolean place(ServerLevel level, BlockPos pos, int flags) {
        BlockState blockState = Block.updateFromNeighbourShapes(this.state, level, pos);
        if (blockState.isAir()) {
            blockState = this.state;
        }

        blockState = this.overwriteWithDefinedProperties(blockState);
        if (!level.setBlock(pos, blockState, flags)) {
            return false;
        } else {
            if (this.tag != null) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    blockEntity.loadWithComponents(this.tag, level.registryAccess());
                }
            }

            return true;
        }
    }

    private BlockState overwriteWithDefinedProperties(BlockState state) {
        if (state == this.state) {
            return state;
        } else {
            for (Property<?> property : this.properties) {
                state = copyProperty(state, this.state, property);
            }

            return state;
        }
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState source, BlockState target, Property<T> property) {
        return source.setValue(property, target.getValue(property));
    }
}
