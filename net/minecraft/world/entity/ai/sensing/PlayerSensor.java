package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;

public class PlayerSensor extends Sensor<LivingEntity> {
    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.NEAREST_PLAYERS, MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER);
    }

    @Override
    protected void doTick(ServerLevel level, LivingEntity entity) {
        List<Player> list = level.players()
            .stream()
            .filter(EntitySelector.NO_SPECTATORS)
            .filter(serverPlayer -> entity.closerThan(serverPlayer, this.getFollowDistance(entity)))
            .sorted(Comparator.comparingDouble(entity::distanceToSqr))
            .collect(Collectors.toList());
        Brain<?> brain = entity.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_PLAYERS, list);
        List<Player> list1 = list.stream().filter(player -> isEntityTargetable(level, entity, player)).collect(Collectors.toList());
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER, list1.isEmpty() ? null : list1.get(0));
        Optional<Player> optional = list1.stream().filter(player -> isEntityAttackable(level, entity, player)).findFirst();
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, optional);
    }

    protected double getFollowDistance(LivingEntity entity) {
        return entity.getAttributeValue(Attributes.FOLLOW_RANGE);
    }
}
