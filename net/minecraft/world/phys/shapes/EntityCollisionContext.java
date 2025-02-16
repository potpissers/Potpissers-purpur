package net.minecraft.world.phys.shapes;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class EntityCollisionContext implements CollisionContext {
    protected static final CollisionContext EMPTY = new EntityCollisionContext(false, -Double.MAX_VALUE, ItemStack.EMPTY, fluidState -> false, null) {
        @Override
        public boolean isAbove(VoxelShape shape, BlockPos pos, boolean canAscend) {
            return canAscend;
        }
    };
    private final boolean descending;
    private final double entityBottom;
    private final ItemStack heldItem;
    private final Predicate<FluidState> canStandOnFluid;
    @Nullable
    private final Entity entity;

    protected EntityCollisionContext(
        boolean descending, double entityBottom, ItemStack heldItem, Predicate<FluidState> canStandOnFluid, @Nullable Entity entity
    ) {
        this.descending = descending;
        this.entityBottom = entityBottom;
        this.heldItem = heldItem;
        this.canStandOnFluid = canStandOnFluid;
        this.entity = entity;
    }

    @Deprecated
    protected EntityCollisionContext(Entity entity, boolean canStandOnFluid) {
        this(
            entity.isDescending(),
            entity.getY(),
            entity instanceof LivingEntity ? ((LivingEntity)entity).getMainHandItem() : ItemStack.EMPTY,
            canStandOnFluid ? fluidState -> true : (entity instanceof LivingEntity ? ((LivingEntity)entity)::canStandOnFluid : fluidState -> false),
            entity
        );
    }

    @Override
    public boolean isHoldingItem(Item item) {
        return this.heldItem.is(item);
    }

    @Override
    public boolean canStandOnFluid(FluidState fluid1, FluidState fluid2) {
        return this.canStandOnFluid.test(fluid2) && !fluid1.getType().isSame(fluid2.getType());
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, CollisionGetter collisionGetter, BlockPos pos) {
        return state.getCollisionShape(collisionGetter, pos, this);
    }

    @Override
    public boolean isDescending() {
        return this.descending;
    }

    @Override
    public boolean isAbove(VoxelShape shape, BlockPos pos, boolean canAscend) {
        return this.entityBottom > pos.getY() + shape.max(Direction.Axis.Y) - 1.0E-5F;
    }

    @Nullable
    public Entity getEntity() {
        return this.entity;
    }
}
