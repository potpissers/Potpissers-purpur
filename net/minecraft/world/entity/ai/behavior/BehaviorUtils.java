package net.minecraft.world.entity.ai.behavior;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;

public class BehaviorUtils {
    private BehaviorUtils() {
    }

    public static void lockGazeAndWalkToEachOther(LivingEntity firstEntity, LivingEntity secondEntity, float speed, int distance) {
        lookAtEachOther(firstEntity, secondEntity);
        setWalkAndLookTargetMemoriesToEachOther(firstEntity, secondEntity, speed, distance);
    }

    public static boolean entityIsVisible(Brain<?> brain, LivingEntity target) {
        Optional<NearestVisibleLivingEntities> memory = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        return memory.isPresent() && memory.get().contains(target);
    }

    public static boolean targetIsValid(Brain<?> brains, MemoryModuleType<? extends LivingEntity> memorymodule, EntityType<?> entityType) {
        return targetIsValid(brains, memorymodule, entity -> entity.getType() == entityType);
    }

    private static boolean targetIsValid(Brain<?> brain, MemoryModuleType<? extends LivingEntity> memoryType, Predicate<LivingEntity> livingPredicate) {
        return brain.getMemory(memoryType).filter(livingPredicate).filter(LivingEntity::isAlive).filter(entity -> entityIsVisible(brain, entity)).isPresent();
    }

    private static void lookAtEachOther(LivingEntity firstEntity, LivingEntity secondEntity) {
        lookAtEntity(firstEntity, secondEntity);
        lookAtEntity(secondEntity, firstEntity);
    }

    public static void lookAtEntity(LivingEntity entity, LivingEntity target) {
        entity.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
    }

    private static void setWalkAndLookTargetMemoriesToEachOther(LivingEntity firstEntity, LivingEntity secondEntity, float speed, int distance) {
        setWalkAndLookTargetMemories(firstEntity, secondEntity, speed, distance);
        setWalkAndLookTargetMemories(secondEntity, firstEntity, speed, distance);
    }

    public static void setWalkAndLookTargetMemories(LivingEntity livingEntity, Entity target, float speed, int distance) {
        setWalkAndLookTargetMemories(livingEntity, new EntityTracker(target, true), speed, distance);
    }

    public static void setWalkAndLookTargetMemories(LivingEntity livingEntity, BlockPos pos, float speed, int distance) {
        setWalkAndLookTargetMemories(livingEntity, new BlockPosTracker(pos), speed, distance);
    }

    public static void setWalkAndLookTargetMemories(LivingEntity entity, PositionTracker positionTracker, float speedModifier, int closeEnoughDist) {
        WalkTarget walkTarget = new WalkTarget(positionTracker, speedModifier, closeEnoughDist);
        entity.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, positionTracker);
        entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET, walkTarget);
    }

    public static void throwItem(LivingEntity livingEntity, ItemStack stack, Vec3 offset) {
        Vec3 vec3 = new Vec3(0.3F, 0.3F, 0.3F);
        throwItem(livingEntity, stack, offset, vec3, 0.3F);
    }

    public static void throwItem(LivingEntity entity, ItemStack stack, Vec3 offset, Vec3 speedMultiplier, float yOffset) {
        double d = entity.getEyeY() - yOffset;
        ItemEntity itemEntity = new ItemEntity(entity.level(), entity.getX(), d, entity.getZ(), stack);
        itemEntity.setThrower(entity);
        Vec3 vec3 = offset.subtract(entity.position());
        vec3 = vec3.normalize().multiply(speedMultiplier.x, speedMultiplier.y, speedMultiplier.z);
        itemEntity.setDeltaMovement(vec3);
        itemEntity.setDefaultPickUpDelay();
        entity.level().addFreshEntity(itemEntity);
    }

    public static SectionPos findSectionClosestToVillage(ServerLevel serverLevel, SectionPos sectionPos, int radius) {
        int i = serverLevel.sectionsToVillage(sectionPos);
        return SectionPos.cube(sectionPos, radius)
            .filter(pos -> serverLevel.sectionsToVillage(pos) < i)
            .min(Comparator.comparingInt(serverLevel::sectionsToVillage))
            .orElse(sectionPos);
    }

    public static boolean isWithinAttackRange(Mob mob, LivingEntity target, int cooldown) {
        if (mob.getMainHandItem().getItem() instanceof ProjectileWeaponItem projectileWeaponItem && mob.canFireProjectileWeapon(projectileWeaponItem)) {
            int i = projectileWeaponItem.getDefaultProjectileRange() - cooldown;
            return mob.closerThan(target, i);
        } else {
            return mob.isWithinMeleeAttackRange(target);
        }
    }

    public static boolean isOtherTargetMuchFurtherAwayThanCurrentAttackTarget(LivingEntity livingEntity, LivingEntity target, double distance) {
        Optional<LivingEntity> memory = livingEntity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
        if (memory.isEmpty()) {
            return false;
        } else {
            double d = livingEntity.distanceToSqr(memory.get().position());
            double d1 = livingEntity.distanceToSqr(target.position());
            return d1 > d + distance * distance;
        }
    }

    public static boolean canSee(LivingEntity livingEntity, LivingEntity target) {
        Brain<?> brain = livingEntity.getBrain();
        return brain.hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
            && brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get().contains(target);
    }

    public static LivingEntity getNearestTarget(LivingEntity centerEntity, Optional<LivingEntity> optionalEntity, LivingEntity livingEntity) {
        return optionalEntity.isEmpty() ? livingEntity : getTargetNearestMe(centerEntity, optionalEntity.get(), livingEntity);
    }

    public static LivingEntity getTargetNearestMe(LivingEntity centerEntity, LivingEntity livingEntity1, LivingEntity livingEntity2) {
        Vec3 vec3 = livingEntity1.position();
        Vec3 vec31 = livingEntity2.position();
        return centerEntity.distanceToSqr(vec3) < centerEntity.distanceToSqr(vec31) ? livingEntity1 : livingEntity2;
    }

    public static Optional<LivingEntity> getLivingEntityFromUUIDMemory(LivingEntity livingEntity, MemoryModuleType<UUID> targetMemory) {
        Optional<UUID> memory = livingEntity.getBrain().getMemory(targetMemory);
        return memory.<Entity>map(identifier -> ((ServerLevel)livingEntity.level()).getEntity(identifier))
            .map(entity -> entity instanceof LivingEntity livingEntity1 ? livingEntity1 : null);
    }

    @Nullable
    public static Vec3 getRandomSwimmablePos(PathfinderMob pathfinder, int radius, int verticalDistance) {
        Vec3 pos = DefaultRandomPos.getPos(pathfinder, radius, verticalDistance);
        int i = 0;

        while (pos != null && !pathfinder.level().getBlockState(BlockPos.containing(pos)).isPathfindable(PathComputationType.WATER) && i++ < 10) {
            pos = DefaultRandomPos.getPos(pathfinder, radius, verticalDistance);
        }

        return pos;
    }

    public static boolean isBreeding(LivingEntity entity) {
        return entity.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET);
    }
}
