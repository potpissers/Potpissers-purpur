package net.minecraft.world.entity;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Scoreboard;

public enum ConversionType {
    SINGLE(true) {
        @Override
        void convert(Mob oldMob, Mob newMob, ConversionParams conversionParams) {
            Entity firstPassenger = oldMob.getFirstPassenger();
            newMob.copyPosition(oldMob);
            newMob.setDeltaMovement(oldMob.getDeltaMovement());
            if (firstPassenger != null) {
                firstPassenger.stopRiding();
                firstPassenger.boardingCooldown = 0;

                for (Entity entity : newMob.getPassengers()) {
                    entity.stopRiding();
                    entity.remove(Entity.RemovalReason.DISCARDED);
                }

                firstPassenger.startRiding(newMob);
            }

            Entity vehicle = oldMob.getVehicle();
            if (vehicle != null) {
                oldMob.stopRiding();
                newMob.startRiding(vehicle);
            }

            if (conversionParams.keepEquipment()) {
                for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
                    ItemStack itemBySlot = oldMob.getItemBySlot(equipmentSlot);
                    if (!itemBySlot.isEmpty()) {
                        newMob.setItemSlot(equipmentSlot, itemBySlot.copyAndClear());
                        newMob.setDropChance(equipmentSlot, oldMob.getEquipmentDropChance(equipmentSlot));
                    }
                }
            }

            newMob.fallDistance = oldMob.fallDistance;
            newMob.setSharedFlag(7, oldMob.isFallFlying());
            newMob.lastHurtByPlayerTime = oldMob.lastHurtByPlayerTime;
            newMob.hurtTime = oldMob.hurtTime;
            newMob.yBodyRot = oldMob.yBodyRot;
            newMob.setOnGround(oldMob.onGround());
            oldMob.getSleepingPos().ifPresent(newMob::setSleepingPos);
            Entity entity = oldMob.getLeashHolder();
            if (entity != null) {
                newMob.setLeashedTo(entity, true);
            }

            this.convertCommon(oldMob, newMob, conversionParams);
        }
    },
    SPLIT_ON_DEATH(false) {
        @Override
        void convert(Mob oldMob, Mob newMob, ConversionParams conversionParams) {
            Entity firstPassenger = oldMob.getFirstPassenger();
            if (firstPassenger != null) {
                firstPassenger.stopRiding();
            }

            Entity leashHolder = oldMob.getLeashHolder();
            if (leashHolder != null) {
                oldMob.dropLeash();
            }

            this.convertCommon(oldMob, newMob, conversionParams);
        }
    };

    private final boolean discardAfterConversion;

    ConversionType(final boolean discardAfterConversion) {
        this.discardAfterConversion = discardAfterConversion;
    }

    public boolean shouldDiscardAfterConversion() {
        return this.discardAfterConversion;
    }

    abstract void convert(Mob oldMob, Mob newMob, ConversionParams conversionParams);

    void convertCommon(Mob oldMob, Mob newMob, ConversionParams conversionParams) {
        newMob.setAbsorptionAmount(oldMob.getAbsorptionAmount());

        for (MobEffectInstance mobEffectInstance : oldMob.getActiveEffects()) {
            newMob.addEffect(new MobEffectInstance(mobEffectInstance));
        }

        if (oldMob.isBaby()) {
            newMob.setBaby(true);
        }

        if (oldMob instanceof AgeableMob ageableMob && newMob instanceof AgeableMob ageableMob1) {
            ageableMob1.setAge(ageableMob.getAge());
            ageableMob1.forcedAge = ageableMob.forcedAge;
            ageableMob1.forcedAgeTimer = ageableMob.forcedAgeTimer;
        }

        Brain<?> brain = oldMob.getBrain();
        Brain<?> brain1 = newMob.getBrain();
        if (brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED) && brain.hasMemoryValue(MemoryModuleType.ANGRY_AT)) {
            brain1.setMemory(MemoryModuleType.ANGRY_AT, brain.getMemory(MemoryModuleType.ANGRY_AT));
        }

        if (conversionParams.preserveCanPickUpLoot()) {
            newMob.setCanPickUpLoot(oldMob.canPickUpLoot());
        }

        newMob.setLeftHanded(oldMob.isLeftHanded());
        newMob.setNoAi(oldMob.isNoAi());
        if (oldMob.isPersistenceRequired()) {
            newMob.setPersistenceRequired();
        }

        if (oldMob.hasCustomName()) {
            newMob.setCustomName(oldMob.getCustomName());
            newMob.setCustomNameVisible(oldMob.isCustomNameVisible());
        }

        newMob.setSharedFlagOnFire(oldMob.isOnFire());
        newMob.setInvulnerable(oldMob.isInvulnerable());
        newMob.setNoGravity(oldMob.isNoGravity());
        newMob.setPortalCooldown(oldMob.getPortalCooldown());
        newMob.setSilent(oldMob.isSilent());
        oldMob.getTags().forEach(newMob::addTag);
        if (conversionParams.team() != null) {
            Scoreboard scoreboard = newMob.level().getScoreboard();
            scoreboard.addPlayerToTeam(newMob.getStringUUID(), conversionParams.team());
            if (oldMob.getTeam() != null && oldMob.getTeam() == conversionParams.team()) {
                scoreboard.removePlayerFromTeam(oldMob.getStringUUID(), oldMob.getTeam());
            }
        }

        if (oldMob instanceof Zombie zombie && zombie.canBreakDoors() && newMob instanceof Zombie zombie1) {
            zombie1.setCanBreakDoors(true);
        }
    }
}
