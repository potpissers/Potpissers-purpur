package net.minecraft.world.phys.shapes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public interface CollisionContext {
    static CollisionContext empty() {
        return EntityCollisionContext.EMPTY;
    }

    static CollisionContext of(Entity entity) {
        return (CollisionContext)(switch (entity) {
            case AbstractMinecart abstractMinecart -> AbstractMinecart.useExperimentalMovement(abstractMinecart.level())
                ? new MinecartCollisionContext(abstractMinecart, false)
                : new EntityCollisionContext(entity, false);
            default -> new EntityCollisionContext(entity, false);
        });
    }

    static CollisionContext of(Entity entity, boolean canStandOnFluid) {
        return new EntityCollisionContext(entity, canStandOnFluid);
    }

    boolean isDescending();

    boolean isAbove(VoxelShape shape, BlockPos pos, boolean canAscend);

    boolean isHoldingItem(Item item);

    boolean canStandOnFluid(FluidState fluid1, FluidState fluid2);

    VoxelShape getCollisionShape(BlockState state, CollisionGetter collisionGetter, BlockPos pos);
}
