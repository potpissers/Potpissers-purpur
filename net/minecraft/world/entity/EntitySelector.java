package net.minecraft.world.entity;

import com.google.common.base.Predicates;
import java.util.function.Predicate;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;

public final class EntitySelector {
    public static final Predicate<Entity> ENTITY_STILL_ALIVE = Entity::isAlive;
    public static final Predicate<Entity> LIVING_ENTITY_STILL_ALIVE = entity -> entity.isAlive() && entity instanceof LivingEntity;
    public static final Predicate<Entity> ENTITY_NOT_BEING_RIDDEN = entity -> entity.isAlive() && !entity.isVehicle() && !entity.isPassenger();
    public static final Predicate<Entity> CONTAINER_ENTITY_SELECTOR = entity -> entity instanceof Container && entity.isAlive();
    public static final Predicate<Entity> NO_CREATIVE_OR_SPECTATOR = entity -> !(entity instanceof Player)
        || !entity.isSpectator() && !((Player)entity).isCreative();
    public static final Predicate<Entity> NO_SPECTATORS = entity -> !entity.isSpectator();
    public static final Predicate<Entity> CAN_BE_COLLIDED_WITH = NO_SPECTATORS.and(Entity::canBeCollidedWith);
    public static final Predicate<Entity> CAN_BE_PICKED = NO_SPECTATORS.and(Entity::isPickable);

    private EntitySelector() {
    }

    public static Predicate<Entity> withinDistance(double x, double y, double z, double range) {
        double d = range * range;
        return entity -> entity != null && entity.distanceToSqr(x, y, z) <= d;
    }

    public static Predicate<Entity> pushableBy(Entity entity) {
        Team team = entity.getTeam();
        Team.CollisionRule collisionRule = team == null ? Team.CollisionRule.ALWAYS : team.getCollisionRule();
        return (Predicate<Entity>)(collisionRule == Team.CollisionRule.NEVER
            ? Predicates.alwaysFalse()
            : NO_SPECTATORS.and(
                pushedEntity -> {
                    if (!pushedEntity.isPushable()) {
                        return false;
                    } else if (!entity.level().isClientSide || pushedEntity instanceof Player && ((Player)pushedEntity).isLocalPlayer()) {
                        Team team1 = pushedEntity.getTeam();
                        Team.CollisionRule collisionRule1 = team1 == null ? Team.CollisionRule.ALWAYS : team1.getCollisionRule();
                        if (collisionRule1 == Team.CollisionRule.NEVER) {
                            return false;
                        } else {
                            boolean flag = team != null && team.isAlliedTo(team1);
                            return (collisionRule != Team.CollisionRule.PUSH_OWN_TEAM && collisionRule1 != Team.CollisionRule.PUSH_OWN_TEAM || !flag)
                                && (collisionRule != Team.CollisionRule.PUSH_OTHER_TEAMS && collisionRule1 != Team.CollisionRule.PUSH_OTHER_TEAMS || flag);
                        }
                    } else {
                        return false;
                    }
                }
            ));
    }

    public static Predicate<Entity> notRiding(Entity entity) {
        return passenger -> {
            while (passenger.isPassenger()) {
                passenger = passenger.getVehicle();
                if (passenger == entity) {
                    return false;
                }
            }

            return true;
        };
    }
}
