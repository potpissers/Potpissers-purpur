package net.minecraft.world.level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface EntityGetter {
    List<Entity> getEntities(@Nullable Entity entity, AABB area, Predicate<? super Entity> predicate);

    <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> entityTypeTest, AABB bounds, Predicate<? super T> predicate);

    default <T extends Entity> List<T> getEntitiesOfClass(Class<T> entityClass, AABB area, Predicate<? super T> filter) {
        return this.getEntities(EntityTypeTest.forClass(entityClass), area, filter);
    }

    List<? extends Player> players();

    default List<Entity> getEntities(@Nullable Entity entity, AABB area) {
        return this.getEntities(entity, area, EntitySelector.NO_SPECTATORS);
    }

    default boolean isUnobstructed(@Nullable Entity entity, VoxelShape shape) {
        if (shape.isEmpty()) {
            return true;
        } else {
            for (Entity entity1 : this.getEntities(entity, shape.bounds())) {
                if (!entity1.isRemoved()
                    && entity1.blocksBuilding
                    && (entity == null || !entity1.isPassengerOfSameVehicle(entity))
                    && Shapes.joinIsNotEmpty(shape, Shapes.create(entity1.getBoundingBox()), BooleanOp.AND)) {
                    return false;
                }
            }

            return true;
        }
    }

    default <T extends Entity> List<T> getEntitiesOfClass(Class<T> entityClass, AABB area) {
        return this.getEntitiesOfClass(entityClass, area, EntitySelector.NO_SPECTATORS);
    }

    default List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB collisionBox) {
        if (collisionBox.getSize() < 1.0E-7) {
            return List.of();
        } else {
            Predicate<Entity> predicate = entity == null ? EntitySelector.CAN_BE_COLLIDED_WITH : EntitySelector.NO_SPECTATORS.and(entity::canCollideWith);
            List<Entity> entities = this.getEntities(entity, collisionBox.inflate(1.0E-7), predicate);
            if (entities.isEmpty()) {
                return List.of();
            } else {
                Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(entities.size());

                for (Entity entity1 : entities) {
                    builder.add(Shapes.create(entity1.getBoundingBox()));
                }

                return builder.build();
            }
        }
    }

    // Paper start - Affects Spawning API
    default @Nullable Player findNearbyPlayer(Entity entity, double maxDistance, @Nullable Predicate<Entity> predicate) {
        return this.getNearestPlayer(entity.getX(), entity.getY(), entity.getZ(), maxDistance, predicate);
    }
    // Paper end - Affects Spawning API

    @Nullable
    default Player getNearestPlayer(double x, double y, double z, double distance, @Nullable Predicate<Entity> predicate) {
        double d = -1.0;
        Player player = null;

        for (Player player1 : this.players()) {
            if (predicate == null || predicate.test(player1)) {
                double d1 = player1.distanceToSqr(x, y, z);
                if ((distance < 0.0 || d1 < distance * distance) && (d == -1.0 || d1 < d)) {
                    d = d1;
                    player = player1;
                }
            }
        }

        return player;
    }

    // Paper start
    default List<org.bukkit.entity.HumanEntity> findNearbyBukkitPlayers(double x, double y, double z, double radius, boolean notSpectator) {
        return findNearbyBukkitPlayers(x, y, z, radius, notSpectator ? EntitySelector.NO_SPECTATORS : net.minecraft.world.entity.EntitySelector.NO_CREATIVE_OR_SPECTATOR);
    }

    default List<org.bukkit.entity.HumanEntity> findNearbyBukkitPlayers(double x, double y, double z, double radius, @Nullable Predicate<Entity> predicate) {
        com.google.common.collect.ImmutableList.Builder<org.bukkit.entity.HumanEntity> builder = com.google.common.collect.ImmutableList.builder();

        for (Player human : this.players()) {
            if (predicate == null || predicate.test(human)) {
                double distanceSquared = human.distanceToSqr(x, y, z);

                if (radius < 0.0D || distanceSquared < radius * radius) {
                    builder.add(human.getBukkitEntity());
                }
            }
        }

        return builder.build();
    }
    // Paper end

    @Nullable
    default Player getNearestPlayer(Entity entity, double distance) {
        return this.getNearestPlayer(entity.getX(), entity.getY(), entity.getZ(), distance, false);
    }

    @Nullable
    default Player getNearestPlayer(double x, double y, double z, double distance, boolean creativePlayers) {
        Predicate<Entity> predicate = creativePlayers ? EntitySelector.NO_CREATIVE_OR_SPECTATOR : EntitySelector.NO_SPECTATORS;
        return this.getNearestPlayer(x, y, z, distance, predicate);
    }

    // Paper start - Affects Spawning API
    default boolean hasNearbyAlivePlayerThatAffectsSpawning(double x, double y, double z, double range) {
        for (Player player : this.players()) {
            if (EntitySelector.PLAYER_AFFECTS_SPAWNING.test(player)) { // combines NO_SPECTATORS and LIVING_ENTITY_STILL_ALIVE with an "affects spawning" check
                double distanceSqr = player.distanceToSqr(x, y, z);
                if (range < 0.0D || distanceSqr < range * range) {
                    return true;
                }
            }
        }
        return false;
    }
    // Paper end - Affects Spawning API

    default boolean hasNearbyAlivePlayer(double x, double y, double z, double distance) {
        for (Player player : this.players()) {
            if (EntitySelector.NO_SPECTATORS.test(player) && EntitySelector.LIVING_ENTITY_STILL_ALIVE.test(player)) {
                double d = player.distanceToSqr(x, y, z);
                if (distance < 0.0 || d < distance * distance) {
                    return true;
                }
            }
        }

        return false;
    }

    @Nullable
    default Player getPlayerByUUID(UUID uniqueId) {
        for (int i = 0; i < this.players().size(); i++) {
            Player player = this.players().get(i);
            if (uniqueId.equals(player.getUUID())) {
                return player;
            }
        }

        return null;
    }

    // Paper start - check global player list where appropriate
    @Nullable
    default Player getGlobalPlayerByUUID(UUID uuid) {
        return this.getPlayerByUUID(uuid);
    }
    // Paper end - check global player list where appropriate
}
