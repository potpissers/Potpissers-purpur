package net.minecraft.world.level.entity;

import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

public interface EntityAccess {
    int getId();

    UUID getUUID();

    BlockPos blockPosition();

    AABB getBoundingBox();

    void setLevelCallback(EntityInLevelCallback levelCallback);

    Stream<? extends EntityAccess> getSelfAndPassengers();

    Stream<? extends EntityAccess> getPassengersAndSelf();

    void setRemoved(Entity.RemovalReason removalReason);

    // CraftBukkit start - add Bukkit remove cause
    default void setRemoved(Entity.RemovalReason removalReason, org.bukkit.event.entity.EntityRemoveEvent.Cause eventCause) {
        this.setRemoved(removalReason);
    }
    // CraftBukkit end

    boolean shouldBeSaved();

    boolean isAlwaysTicking();
}
