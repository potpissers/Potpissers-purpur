package net.minecraft.server.level;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;

public interface ServerEntityGetter extends EntityGetter {
    ServerLevel getLevel();

    @Nullable
    default Player getNearestPlayer(TargetingConditions targetingConditions, LivingEntity source) {
        return this.getNearestEntity(this.players(), targetingConditions, source, source.getX(), source.getY(), source.getZ());
    }

    @Nullable
    default Player getNearestPlayer(TargetingConditions targetingConditions, LivingEntity source, double x, double y, double z) {
        return this.getNearestEntity(this.players(), targetingConditions, source, x, y, z);
    }

    @Nullable
    default Player getNearestPlayer(TargetingConditions targetingConditions, double x, double y, double z) {
        return this.getNearestEntity(this.players(), targetingConditions, null, x, y, z);
    }

    @Nullable
    default <T extends LivingEntity> T getNearestEntity(
        Class<? extends T> entityClass, TargetingConditions targetingConditions, @Nullable LivingEntity source, double x, double y, double z, AABB area
    ) {
        return this.getNearestEntity(this.getEntitiesOfClass(entityClass, area, entity -> true), targetingConditions, source, x, y, z);
    }

    @Nullable
    default <T extends LivingEntity> T getNearestEntity(
        List<? extends T> entities, TargetingConditions targetingConditions, @Nullable LivingEntity source, double x, double y, double z
    ) {
        double d = -1.0;
        T livingEntity = null;

        for (T livingEntity1 : entities) {
            if (targetingConditions.test(this.getLevel(), source, livingEntity1)) {
                double d1 = livingEntity1.distanceToSqr(x, y, z);
                if (d == -1.0 || d1 < d) {
                    d = d1;
                    livingEntity = livingEntity1;
                }
            }
        }

        return livingEntity;
    }

    default List<Player> getNearbyPlayers(TargetingConditions targetingConditions, LivingEntity source, AABB area) {
        List<Player> list = new ArrayList<>();

        for (Player player : this.players()) {
            if (area.contains(player.getX(), player.getY(), player.getZ()) && targetingConditions.test(this.getLevel(), source, player)) {
                list.add(player);
            }
        }

        return list;
    }

    default <T extends LivingEntity> List<T> getNearbyEntities(Class<T> entityClass, TargetingConditions targetingConditions, LivingEntity source, AABB area) {
        List<T> entitiesOfClass = this.getEntitiesOfClass(entityClass, area, entity -> true);
        List<T> list = new ArrayList<>();

        for (T livingEntity : entitiesOfClass) {
            if (targetingConditions.test(this.getLevel(), source, livingEntity)) {
                list.add(livingEntity);
            }
        }

        return list;
    }
}
