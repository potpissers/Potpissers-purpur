package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class AvoidEntityGoal<T extends LivingEntity> extends Goal {
    protected final PathfinderMob mob;
    private final double walkSpeedModifier;
    private final double sprintSpeedModifier;
    @Nullable
    protected T toAvoid;
    protected final float maxDist;
    @Nullable
    protected Path path;
    protected final PathNavigation pathNav;
    protected final Class<T> avoidClass;
    protected final Predicate<LivingEntity> avoidPredicate;
    protected final Predicate<LivingEntity> predicateOnAvoidEntity;
    private final TargetingConditions avoidEntityTargeting;

    public AvoidEntityGoal(PathfinderMob mob, Class<T> entityClassToAvoid, float maxDistance, double walkSpeedModifier, double sprintSpeedModifier) {
        this(mob, entityClassToAvoid, entity -> true, maxDistance, walkSpeedModifier, sprintSpeedModifier, EntitySelector.NO_CREATIVE_OR_SPECTATOR::test);
    }

    public AvoidEntityGoal(
        PathfinderMob mob,
        Class<T> entityClassToAvoid,
        Predicate<LivingEntity> avoidPredicate,
        float maxDistance,
        double walkSpeedModifier,
        double sprintSpeedModifier,
        Predicate<LivingEntity> predicateOnAvoidEntity
    ) {
        this.mob = mob;
        this.avoidClass = entityClassToAvoid;
        this.avoidPredicate = avoidPredicate;
        this.maxDist = maxDistance;
        this.walkSpeedModifier = walkSpeedModifier;
        this.sprintSpeedModifier = sprintSpeedModifier;
        this.predicateOnAvoidEntity = predicateOnAvoidEntity;
        this.pathNav = mob.getNavigation();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        this.avoidEntityTargeting = TargetingConditions.forCombat()
            .range(maxDistance)
            .selector((entity, level) -> predicateOnAvoidEntity.test(entity) && avoidPredicate.test(entity));
    }

    public AvoidEntityGoal(
        PathfinderMob mob,
        Class<T> entityClassToAvoid,
        float maxDistance,
        double walkSpeedModifier,
        double sprintSpeedModifier,
        Predicate<LivingEntity> predicateOnAvoidEntity
    ) {
        this(mob, entityClassToAvoid, livingEntity -> true, maxDistance, walkSpeedModifier, sprintSpeedModifier, predicateOnAvoidEntity);
    }

    @Override
    public boolean canUse() {
        this.toAvoid = getServerLevel(this.mob)
            .getNearestEntity(
                this.mob.level().getEntitiesOfClass(this.avoidClass, this.mob.getBoundingBox().inflate(this.maxDist, 3.0, this.maxDist), livingEntity -> true),
                this.avoidEntityTargeting,
                this.mob,
                this.mob.getX(),
                this.mob.getY(),
                this.mob.getZ()
            );
        if (this.toAvoid == null) {
            return false;
        } else {
            Vec3 posAway = DefaultRandomPos.getPosAway(this.mob, 16, 7, this.toAvoid.position());
            if (posAway == null) {
                return false;
            } else if (this.toAvoid.distanceToSqr(posAway.x, posAway.y, posAway.z) < this.toAvoid.distanceToSqr(this.mob)) {
                return false;
            } else {
                this.path = this.pathNav.createPath(posAway.x, posAway.y, posAway.z, 0);
                return this.path != null;
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return !this.pathNav.isDone();
    }

    @Override
    public void start() {
        this.pathNav.moveTo(this.path, this.walkSpeedModifier);
    }

    @Override
    public void stop() {
        this.toAvoid = null;
    }

    @Override
    public void tick() {
        if (this.mob.distanceToSqr(this.toAvoid) < 49.0) {
            this.mob.getNavigation().setSpeedModifier(this.sprintSpeedModifier);
        } else {
            this.mob.getNavigation().setSpeedModifier(this.walkSpeedModifier);
        }
    }
}
