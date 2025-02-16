package net.minecraft.world.entity.ai.behavior;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

public class AnimalPanic<E extends PathfinderMob> extends Behavior<E> {
    private static final int PANIC_MIN_DURATION = 100;
    private static final int PANIC_MAX_DURATION = 120;
    private static final int PANIC_DISTANCE_HORIZONTAL = 5;
    private static final int PANIC_DISTANCE_VERTICAL = 4;
    private final float speedMultiplier;
    private final Function<PathfinderMob, TagKey<DamageType>> panicCausingDamageTypes;

    public AnimalPanic(float speedMultiplier) {
        this(speedMultiplier, pathfinderMob -> DamageTypeTags.PANIC_CAUSES);
    }

    public AnimalPanic(float speedMultiplier, Function<PathfinderMob, TagKey<DamageType>> panicCausingDamageTypes) {
        super(Map.of(MemoryModuleType.IS_PANICKING, MemoryStatus.REGISTERED, MemoryModuleType.HURT_BY, MemoryStatus.REGISTERED), 100, 120);
        this.speedMultiplier = speedMultiplier;
        this.panicCausingDamageTypes = panicCausingDamageTypes;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, E owner) {
        return owner.getBrain()
                .getMemory(MemoryModuleType.HURT_BY)
                .map(damageSource -> damageSource.is(this.panicCausingDamageTypes.apply(owner)))
                .orElse(false)
            || owner.getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return true;
    }

    @Override
    protected void start(ServerLevel level, E entity, long gameTime) {
        entity.getBrain().setMemory(MemoryModuleType.IS_PANICKING, true);
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        Brain<?> brain = entity.getBrain();
        brain.eraseMemory(MemoryModuleType.IS_PANICKING);
    }

    @Override
    protected void tick(ServerLevel level, E owner, long gameTime) {
        if (owner.getNavigation().isDone()) {
            Vec3 panicPos = this.getPanicPos(owner, level);
            if (panicPos != null) {
                owner.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(panicPos, this.speedMultiplier, 0));
            }
        }
    }

    @Nullable
    private Vec3 getPanicPos(E pathfinder, ServerLevel level) {
        if (pathfinder.isOnFire()) {
            Optional<Vec3> optional = this.lookForWater(level, pathfinder).map(Vec3::atBottomCenterOf);
            if (optional.isPresent()) {
                return optional.get();
            }
        }

        return LandRandomPos.getPos(pathfinder, 5, 4);
    }

    private Optional<BlockPos> lookForWater(BlockGetter level, Entity entity) {
        BlockPos blockPos = entity.blockPosition();
        if (!level.getBlockState(blockPos).getCollisionShape(level, blockPos).isEmpty()) {
            return Optional.empty();
        } else {
            Predicate<BlockPos> predicate;
            if (Mth.ceil(entity.getBbWidth()) == 2) {
                predicate = blockPos1 -> BlockPos.squareOutSouthEast(blockPos1).allMatch(blockPos2 -> level.getFluidState(blockPos2).is(FluidTags.WATER));
            } else {
                predicate = blockPos1 -> level.getFluidState(blockPos1).is(FluidTags.WATER);
            }

            return BlockPos.findClosestMatch(blockPos, 5, 1, predicate);
        }
    }
}
