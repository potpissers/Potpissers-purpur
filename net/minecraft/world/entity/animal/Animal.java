package net.minecraft.world.entity.animal;

import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;

public abstract class Animal extends AgeableMob {
    protected static final int PARENT_AGE_AFTER_BREEDING = 6000;
    public int inLove;
    @Nullable
    public UUID loveCause;
    public ItemStack breedItem; // CraftBukkit - Add breedItem variable
    public abstract int getPurpurBreedTime(); // Purpur - Make entity breeding times configurable

    protected Animal(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 16.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
    }

    public static AttributeSupplier.Builder createAnimalAttributes() {
        return Mob.createMobAttributes().add(Attributes.TEMPT_RANGE, 10.0);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (this.getAge() != 0) {
            this.inLove = 0;
        }

        super.customServerAiStep(level);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.getAge() != 0) {
            this.inLove = 0;
        }

        if (this.inLove > 0) {
            this.inLove--;
            if (this.inLove % 10 == 0) {
                double d = this.random.nextGaussian() * 0.02;
                double d1 = this.random.nextGaussian() * 0.02;
                double d2 = this.random.nextGaussian() * 0.02;
                this.level().addParticle(ParticleTypes.HEART, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), d, d1, d2);
            }
        }
    }

    @Override
    // CraftBukkit start - void -> boolean
    public boolean actuallyHurt(ServerLevel level, DamageSource damageSource, float amount, org.bukkit.event.entity.EntityDamageEvent event) {
        boolean damageResult = super.actuallyHurt(level, damageSource, amount, event);
        if (!damageResult) return false;
        this.resetLove();
        return true;
        // CraftBukkit end
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK) ? 10.0F : level.getPathfindingCostFromLightLevels(pos);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("InLove", this.inLove);
        if (this.loveCause != null) {
            compound.putUUID("LoveCause", this.loveCause);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.inLove = compound.getInt("InLove");
        this.loveCause = compound.hasUUID("LoveCause") ? compound.getUUID("LoveCause") : null;
    }

    public static boolean checkAnimalSpawnRules(
        EntityType<? extends Animal> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        boolean flag = EntitySpawnReason.ignoresLightRequirements(spawnReason) || isBrightEnoughToSpawn(level, pos);
        return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON) && flag;
    }

    protected static boolean isBrightEnoughToSpawn(BlockAndTintGetter level, BlockPos pos) {
        return level.getRawBrightness(pos, 0) > 8;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return 1 + this.random.nextInt(3);
    }

    public abstract boolean isFood(ItemStack stack);

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (this.isFood(itemInHand)) {
            int age = this.getAge();
            if (!this.level().isClientSide && age == 0 && this.canFallInLove() && (this.level().purpurConfig.animalBreedingCooldownSeconds <= 0 || !this.level().hasBreedingCooldown(player.getUUID(), this.getClass()))) { // Purpur - Add adjustable breeding cooldown to config
                final ItemStack breedCopy = itemInHand.copy(); // Paper - Fix EntityBreedEvent copying
                this.usePlayerItem(player, hand, itemInHand);
                this.setInLove(player, breedCopy); // Paper - Fix EntityBreedEvent copying
                this.playEatingSound();
                return InteractionResult.SUCCESS_SERVER;
            }

            if (this.isBaby()) {
                this.usePlayerItem(player, hand, itemInHand);
                this.ageUp(getSpeedUpSecondsWhenFeeding(-age), true);
                this.playEatingSound();
                return InteractionResult.SUCCESS;
            }

            if (this.level().isClientSide) {
                return InteractionResult.CONSUME;
            }
        }

        return super.mobInteract(player, hand);
    }

    protected void playEatingSound() {
    }

    protected void usePlayerItem(Player player, InteractionHand hand, ItemStack stack) {
        int count = stack.getCount();
        UseRemainder useRemainder = stack.get(DataComponents.USE_REMAINDER);
        stack.consume(1, player);
        if (useRemainder != null) {
            ItemStack itemStack = useRemainder.convertIntoRemainder(stack, count, player.hasInfiniteMaterials(), player::handleExtraItemsCreatedOnUse);
            player.setItemInHand(hand, itemStack);
        }
    }

    public boolean canFallInLove() {
        return this.inLove <= 0;
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper - Fix EntityBreedEvent copying
    public void setInLove(@Nullable Player player) {
        // Paper start - Fix EntityBreedEvent copying
        this.setInLove(player, null);
    }
    public void setInLove(@Nullable Player player, @Nullable ItemStack breedItemCopy) {
        if (breedItemCopy != null) this.breedItem = breedItemCopy;
        // Paper end - Fix EntityBreedEvent copying
        // CraftBukkit start
        org.bukkit.event.entity.EntityEnterLoveModeEvent entityEnterLoveModeEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityEnterLoveModeEvent(player, this, 600);
        if (entityEnterLoveModeEvent.isCancelled()) {
            this.breedItem = null; // Paper - Fix EntityBreedEvent copying; clear if cancelled
            return;
        }
        this.inLove = entityEnterLoveModeEvent.getTicksInLove();
        // CraftBukkit end
        if (player != null) {
            this.loveCause = player.getUUID();
        }
        // Paper - Fix EntityBreedEvent copying; set breed item in better place

        this.level().broadcastEntityEvent(this, (byte)18);
    }

    public void setInLoveTime(int inLove) {
        this.inLove = inLove;
    }

    public int getInLoveTime() {
        return this.inLove;
    }

    @Nullable
    public ServerPlayer getLoveCause() {
        if (this.loveCause == null) {
            return null;
        } else {
            Player playerByUuid = this.level().getPlayerByUUID(this.loveCause);
            return playerByUuid instanceof ServerPlayer ? (ServerPlayer)playerByUuid : null;
        }
    }

    public boolean isInLove() {
        return this.inLove > 0;
    }

    public void resetLove() {
        this.inLove = 0;
    }

    public boolean canMate(Animal otherAnimal) {
        return otherAnimal != this && otherAnimal.getClass() == this.getClass() && this.isInLove() && otherAnimal.isInLove();
    }

    public void spawnChildFromBreeding(ServerLevel level, Animal mate) {
        AgeableMob breedOffspring = this.getBreedOffspring(level, mate);
        if (breedOffspring != null) {
            //breedOffspring.setBaby(true); // Purpur - Add adjustable breeding cooldown to config - moved down
            //breedOffspring.moveTo(this.getX(), this.getY(), this.getZ(), 0.0F, 0.0F); // Purpur - Add adjustable breeding cooldown to config - moved down
            // CraftBukkit start - call EntityBreedEvent
            ServerPlayer breeder = Optional.ofNullable(this.getLoveCause()).or(() -> Optional.ofNullable(mate.getLoveCause())).orElse(null);
            // Purpur start - Add adjustable breeding cooldown to config
            if (breeder != null && level.purpurConfig.animalBreedingCooldownSeconds > 0) {
                if (level.hasBreedingCooldown(breeder.getUUID(), this.getClass())) {
                    return;
                }
                level.addBreedingCooldown(breeder.getUUID(), this.getClass());
            }
            breedOffspring.setBaby(true);
            breedOffspring.moveTo(this.getX(), this.getY(), this.getZ(), 0.0F, 0.0F);
            // Purpur end - Add adjustable breeding cooldown to config
            int experience = this.getRandom().nextInt(7) + 1;
            org.bukkit.event.entity.EntityBreedEvent entityBreedEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityBreedEvent(breedOffspring, this, mate, breeder, this.breedItem, experience);
            if (entityBreedEvent.isCancelled()) {
                this.resetLove();
                mate.resetLove();
                return;
            }
            experience = entityBreedEvent.getExperience();
            this.finalizeSpawnChildFromBreeding(level, mate, breedOffspring, experience);
            level.addFreshEntityWithPassengers(breedOffspring, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BREEDING);
            // CraftBukkit end - call EntityBreedEvent
        }
    }

    public void finalizeSpawnChildFromBreeding(ServerLevel level, Animal animal, @Nullable AgeableMob baby) {
        // CraftBukkit start - call EntityBreedEvent
        this.finalizeSpawnChildFromBreeding(level, animal, baby, this.getRandom().nextInt(7) + 1);
    }

    public void finalizeSpawnChildFromBreeding(ServerLevel level, Animal animal, @Nullable AgeableMob baby, int experience) {
        // CraftBukkit end - call EntityBreedEvent
        // Paper start - call EntityBreedEvent
        ServerPlayer player = this.getLoveCause();
        if (player == null) player = animal.getLoveCause();
        if (player != null) {
            // Paper end - call EntityBreedEvent
            player.awardStat(Stats.ANIMALS_BRED);
            CriteriaTriggers.BRED_ANIMALS.trigger(player, this, animal, baby);
        } // Paper - call EntityBreedEvent
        // Purpur start - Make entity breeding times configurable
        this.setAge(this.getPurpurBreedTime());
        animal.setAge(animal.getPurpurBreedTime());
        // Purpur end - Make entity breeding times configurable
        this.resetLove();
        animal.resetLove();
        level.broadcastEntityEvent(this, (byte)18);
        if (experience > 0 && level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) { // Paper - call EntityBreedEvent
            level.addFreshEntity(new ExperienceOrb(level, this.getX(), this.getY(), this.getZ(), experience, org.bukkit.entity.ExperienceOrb.SpawnReason.BREED, player, baby)); // Paper - call EntityBreedEvent, add spawn context
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 18) {
            for (int i = 0; i < 7; i++) {
                double d = this.random.nextGaussian() * 0.02;
                double d1 = this.random.nextGaussian() * 0.02;
                double d2 = this.random.nextGaussian() * 0.02;
                this.level().addParticle(ParticleTypes.HEART, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), d, d1, d2);
            }
        } else {
            super.handleEntityEvent(id);
        }
    }
}
