package net.minecraft.world.entity.ai.behavior;

import java.util.function.Function;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class BabyFollowAdult {
    public static OneShot<AgeableMob> create(UniformInt followRange, float speedModifier) {
        return create(followRange, entity -> speedModifier);
    }

    public static OneShot<AgeableMob> create(UniformInt followRange, Function<LivingEntity, Float> speedModifier) {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.present(MemoryModuleType.NEAREST_VISIBLE_ADULT),
                    instance.registered(MemoryModuleType.LOOK_TARGET),
                    instance.absent(MemoryModuleType.WALK_TARGET)
                )
                .apply(instance, (nearestVisibleAdult, lookTarget, walkTarget) -> (level, mob, gameTime) -> {
                    if (!mob.isBaby()) {
                        return false;
                    } else {
                        AgeableMob ageableMob = instance.get(nearestVisibleAdult);
                        if (mob.closerThan(ageableMob, followRange.getMaxValue() + 1) && !mob.closerThan(ageableMob, followRange.getMinValue())) {
                            WalkTarget walkTarget1 = new WalkTarget(
                                new EntityTracker(ageableMob, false), speedModifier.apply(mob), followRange.getMinValue() - 1
                            );
                            lookTarget.set(new EntityTracker(ageableMob, true));
                            walkTarget.set(walkTarget1);
                            return true;
                        } else {
                            return false;
                        }
                    }
                })
        );
    }
}
