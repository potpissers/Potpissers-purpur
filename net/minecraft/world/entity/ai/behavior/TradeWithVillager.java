package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TradeWithVillager extends Behavior<Villager> {
    private Set<Item> trades = ImmutableSet.of();

    public TradeWithVillager() {
        super(
            ImmutableMap.of(
                MemoryModuleType.INTERACTION_TARGET, MemoryStatus.VALUE_PRESENT, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT
            )
        );
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        return BehaviorUtils.targetIsValid(owner.getBrain(), MemoryModuleType.INTERACTION_TARGET, EntityType.VILLAGER);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager entity, long gameTime) {
        return this.checkExtraStartConditions(level, entity);
    }

    @Override
    protected void start(ServerLevel level, Villager entity, long gameTime) {
        Villager villager = (Villager)entity.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).get();
        BehaviorUtils.lockGazeAndWalkToEachOther(entity, villager, 0.5F, 2);
        this.trades = figureOutWhatIAmWillingToTrade(entity, villager);
    }

    @Override
    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        Villager villager = (Villager)owner.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).get();
        if (!(owner.distanceToSqr(villager) > 5.0)) {
            BehaviorUtils.lockGazeAndWalkToEachOther(owner, villager, 0.5F, 2);
            owner.gossip(level, villager, gameTime);
            if (owner.hasExcessFood() && (owner.getVillagerData().getProfession() == VillagerProfession.FARMER || villager.wantsMoreFood())) {
                throwHalfStack(owner, Villager.FOOD_POINTS.keySet(), villager);
            }

            if (villager.getVillagerData().getProfession() == VillagerProfession.FARMER
                && owner.getInventory().countItem(Items.WHEAT) > Items.WHEAT.getDefaultMaxStackSize() / 2) {
                throwHalfStack(owner, ImmutableSet.of(Items.WHEAT), villager);
            }

            // Purpur start - Option for Villager Clerics to farm Nether Wart
            if (level.purpurConfig.villagerClericsFarmWarts && level.purpurConfig.villagerClericFarmersThrowWarts && owner.getVillagerData().getProfession() == VillagerProfession.CLERIC && owner.getInventory().countItem(Items.NETHER_WART) > Items.NETHER_WART.getDefaultMaxStackSize() / 2) {
                throwHalfStack(owner, ImmutableSet.of(Items.NETHER_WART), villager);
            }
            // Purpur end - Option for Villager Clerics to farm Nether Wart

            if (!this.trades.isEmpty() && owner.getInventory().hasAnyOf(this.trades)) {
                throwHalfStack(owner, this.trades, villager);
            }
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager entity, long gameTime) {
        entity.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
    }

    private static Set<Item> figureOutWhatIAmWillingToTrade(Villager villager, Villager other) {
        ImmutableSet<Item> set = other.getVillagerData().getProfession().requestedItems();
        ImmutableSet<Item> set1 = villager.getVillagerData().getProfession().requestedItems();
        return set.stream().filter(item -> !set1.contains(item)).collect(Collectors.toSet());
    }

    private static void throwHalfStack(Villager villager, Set<Item> stack, LivingEntity entity) {
        SimpleContainer inventory = villager.getInventory();
        ItemStack itemStack = ItemStack.EMPTY;
        int i = 0;

        while (i < inventory.getContainerSize()) {
            ItemStack item;
            Item item1;
            int i1;
            label28: {
                item = inventory.getItem(i);
                if (!item.isEmpty()) {
                    item1 = item.getItem();
                    if (stack.contains(item1)) {
                        if (item.getCount() > item.getMaxStackSize() / 2) {
                            i1 = item.getCount() / 2;
                            break label28;
                        }

                        if (item.getCount() > 24) {
                            i1 = item.getCount() - 24;
                            break label28;
                        }
                    }
                }

                i++;
                continue;
            }

            item.shrink(i1);
            itemStack = new ItemStack(item1, i1);
            break;
        }

        if (!itemStack.isEmpty()) {
            BehaviorUtils.throwItem(villager, itemStack, entity.position());
        }
    }
}
