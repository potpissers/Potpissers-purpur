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
    // Paper start - Ability to control player's insomnia and phantoms
    public static Predicate<Player> IS_INSOMNIAC = (player) -> {
        net.minecraft.server.level.ServerPlayer serverPlayer = (net.minecraft.server.level.ServerPlayer) player;
        int playerInsomniaTicks = serverPlayer.level().paperConfig().entities.behavior.playerInsomniaStartTicks;

        if (playerInsomniaTicks <= 0) {
            return false;
        }

        return net.minecraft.util.Mth.clamp(serverPlayer.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.TIME_SINCE_REST)), 1, Integer.MAX_VALUE) >= playerInsomniaTicks;
    };
    // Paper end - Ability to control player's insomnia and phantoms
    public static Predicate<Player> notAfk = (player) -> !player.isAfk(); // Purpur - AFK API

    // Paper start - Affects Spawning API
    public static final Predicate<Entity> PLAYER_AFFECTS_SPAWNING = (entity) -> {
        return !entity.isSpectator() && entity.isAlive() && entity instanceof Player player && player.affectsSpawning;
    };
    // Paper end - Affects Spawning API

    private EntitySelector() {
    }

    public static Predicate<Entity> withinDistance(double x, double y, double z, double range) {
        double d = range * range;
        return entity -> entity != null && entity.distanceToSqr(x, y, z) <= d;
    }

    public static Predicate<Entity> pushableBy(Entity entity) {
        // Paper start - Climbing should not bypass cramming gamerule
        return pushable(entity, false);
    }
    public static Predicate<Entity> pushable(Entity entity, boolean ignoreClimbing) {
        // Paper end - Climbing should not bypass cramming gamerule
        Team team = entity.getTeam();
        Team.CollisionRule collisionRule = team == null ? Team.CollisionRule.ALWAYS : team.getCollisionRule();
        return (Predicate<Entity>)(collisionRule == Team.CollisionRule.NEVER
            ? Predicates.alwaysFalse()
            : NO_SPECTATORS.and(
            pushedEntity -> {
                if (!pushedEntity.isCollidable(ignoreClimbing) || !pushedEntity.canCollideWithBukkit(entity) || !entity.canCollideWithBukkit(pushedEntity)) { // CraftBukkit - collidable API // Paper - Climbing should not bypass cramming gamerule
                    return false;
                } else if (!entity.level().isClientSide || pushedEntity instanceof Player && ((Player)pushedEntity).isLocalPlayer()) {
                    Team team1 = pushedEntity.getTeam();
                    Team.CollisionRule collisionRule1 = team1 == null ? Team.CollisionRule.ALWAYS : team1.getCollisionRule();
                    if (collisionRule1 == Team.CollisionRule.NEVER || (pushedEntity instanceof Player && !io.papermc.paper.configuration.GlobalConfiguration.get().collisions.enablePlayerCollisions)) { // Paper - Configurable player collision
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
