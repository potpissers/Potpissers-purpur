package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

public class AdultSensor extends Sensor<AgeableMob> {
    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.NEAREST_VISIBLE_ADULT, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
    }

    @Override
    protected void doTick(ServerLevel level, AgeableMob entity) {
        entity.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).ifPresent(entities -> this.setNearestVisibleAdult(entity, entities));
    }

    private void setNearestVisibleAdult(AgeableMob mob, NearestVisibleLivingEntities nearbyEntities) {
        Optional<AgeableMob> optional = nearbyEntities.findClosest(entity -> entity.getType() == mob.getType() && !entity.isBaby()).map(AgeableMob.class::cast);
        mob.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT, optional);
    }
}
