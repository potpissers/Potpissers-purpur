package net.minecraft.world.entity.ai.behavior;

import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;

public class ResetProfession {
    public static BehaviorControl<Villager> create() {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.absent(MemoryModuleType.JOB_SITE))
                .apply(
                    instance,
                    jobSite -> (level, villager, gameTime) -> {
                        VillagerData villagerData = villager.getVillagerData();
                        if (villagerData.getProfession() != VillagerProfession.NONE
                            && villagerData.getProfession() != VillagerProfession.NITWIT
                            && villager.getVillagerXp() == 0
                            && villagerData.getLevel() <= 1) {
                            // CraftBukkit start
                            org.bukkit.event.entity.VillagerCareerChangeEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callVillagerCareerChangeEvent(villager, org.bukkit.craftbukkit.entity.CraftVillager.CraftProfession.minecraftToBukkit(VillagerProfession.NONE), org.bukkit.event.entity.VillagerCareerChangeEvent.ChangeReason.LOSING_JOB);
                            if (event.isCancelled()) {
                                return false;
                            }

                            villager.setVillagerData(villager.getVillagerData().setProfession(org.bukkit.craftbukkit.entity.CraftVillager.CraftProfession.bukkitToMinecraft(event.getProfession())));
                            // CraftBukkit end
                            villager.refreshBrain(level);
                            return true;
                        } else {
                            return false;
                        }
                    }
                )
        );
    }
}
