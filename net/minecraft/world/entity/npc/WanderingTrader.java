package net.minecraft.world.entity.npc;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.InteractGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.UseItemGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;

// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.inventory.CraftMerchantRecipe;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
// CraftBukkit end

public class WanderingTrader extends net.minecraft.world.entity.npc.AbstractVillager implements Consumable.OverrideConsumeSound {
    private static final int NUMBER_OF_TRADE_OFFERS = 5;
    @Nullable
    private BlockPos wanderTarget;
    private int despawnDelay;
    // Paper start - Add more WanderingTrader API
    public boolean canDrinkPotion = true;
    public boolean canDrinkMilk = true;
    // Paper end - Add more WanderingTrader API

    public WanderingTrader(EntityType<? extends WanderingTrader> entityType, Level level) {
        super(entityType, level);
    }

    // Purpur start - Allow leashing villagers
    @Override
    public boolean canBeLeashed() {
        return level().purpurConfig.wanderingTraderCanBeLeashed;
    }
    // Purpur end - Allow leashing villagers

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector
            .addGoal(
                0,
                new UseItemGoal<>(
                    this,
                    PotionContents.createItemStack(Items.POTION, Potions.INVISIBILITY),
                    SoundEvents.WANDERING_TRADER_DISAPPEARED,
                    wanderingTrader -> this.canDrinkPotion && this.level().isNight() && !wanderingTrader.isInvisible() // Paper - Add more WanderingTrader API
                )
            );
        this.goalSelector
            .addGoal(
                0,
                new UseItemGoal<>(
                    this,
                    new ItemStack(Items.MILK_BUCKET),
                    SoundEvents.WANDERING_TRADER_REAPPEARED,
                    wanderingTrader -> level().purpurConfig.milkClearsBeneficialEffects && this.canDrinkMilk && this.level().isDay() && wanderingTrader.isInvisible() // Paper - Add more WanderingTrader API // // Purpur - Milk Keeps Beneficial Effects
                )
            );
        this.goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Zombie.class, 8.0F, 0.5, 0.5));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Evoker.class, 12.0F, 0.5, 0.5));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Vindicator.class, 8.0F, 0.5, 0.5));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Vex.class, 8.0F, 0.5, 0.5));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Pillager.class, 15.0F, 0.5, 0.5));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Illusioner.class, 12.0F, 0.5, 0.5));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Zoglin.class, 10.0F, 0.5, 0.5));
        this.goalSelector.addGoal(1, new PanicGoal(this, 0.5));
        this.goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        this.goalSelector.addGoal(2, new WanderingTrader.WanderToPositionGoal(this, 2.0, 0.35));
        this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.35));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.35));
        this.goalSelector.addGoal(9, new InteractGoal(this, Player.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null;
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (!itemInHand.is(Items.VILLAGER_SPAWN_EGG) && this.isAlive() && !this.isTrading() && !this.isBaby()) {
            if (hand == InteractionHand.MAIN_HAND) {
                player.awardStat(Stats.TALKED_TO_VILLAGER);
            }

            if (!this.level().isClientSide) {
                if (this.getOffers().isEmpty()) {
                    return InteractionResult.CONSUME;
                }

                if (this.level().purpurConfig.wanderingTraderAllowTrading) { // Purpur - Add config for villager trading
                this.setTradingPlayer(player);
                this.openTradingScreen(player, this.getDisplayName(), 1);
                } // Purpur - Add config for villager trading
            }

            return InteractionResult.SUCCESS;
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    protected void updateTrades() {
        if (this.level().enabledFeatures().contains(FeatureFlags.TRADE_REBALANCE)) {
            this.experimentalUpdateTrades();
        } else {
            VillagerTrades.ItemListing[] itemListings = VillagerTrades.WANDERING_TRADER_TRADES.get(1);
            VillagerTrades.ItemListing[] itemListings1 = VillagerTrades.WANDERING_TRADER_TRADES.get(2);
            if (itemListings != null && itemListings1 != null) {
                MerchantOffers offers = this.getOffers();
                this.addOffersFromItemListings(offers, itemListings, 5);
                int randomInt = this.random.nextInt(itemListings1.length);
                VillagerTrades.ItemListing itemListing = itemListings1[randomInt];
                MerchantOffer offer = itemListing.getOffer(this, this.random);
                if (offer != null) {
                    // CraftBukkit start
                    VillagerAcquireTradeEvent event = new VillagerAcquireTradeEvent((AbstractVillager) this.getBukkitEntity(), offer.asBukkit());
                    // Suppress during worldgen
                    if (this.valid) {
                        Bukkit.getPluginManager().callEvent(event);
                    }
                    if (!event.isCancelled()) {
                        offers.add(CraftMerchantRecipe.fromBukkit(event.getRecipe()).toMinecraft());
                    }
                    // CraftBukkit end
                }
            }
        }
    }

    private void experimentalUpdateTrades() {
        MerchantOffers offers = this.getOffers();

        for (Pair<VillagerTrades.ItemListing[], Integer> pair : VillagerTrades.EXPERIMENTAL_WANDERING_TRADER_TRADES) {
            VillagerTrades.ItemListing[] itemListings = pair.getLeft();
            this.addOffersFromItemListings(offers, itemListings, pair.getRight());
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("DespawnDelay", this.despawnDelay);
        if (this.wanderTarget != null) {
            compound.put("wander_target", NbtUtils.writeBlockPos(this.wanderTarget));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("DespawnDelay", 99)) {
            this.despawnDelay = compound.getInt("DespawnDelay");
        }

        NbtUtils.readBlockPos(compound, "wander_target").ifPresent(blockPos -> this.wanderTarget = blockPos);
        this.setAge(Math.max(0, this.getAge()));
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        if (offer.shouldRewardExp()) {
            int i = 3 + this.random.nextInt(4);
            this.level().addFreshEntity(new ExperienceOrb(this.level(), this.getX(), this.getY() + 0.5, this.getZ(), i, org.bukkit.entity.ExperienceOrb.SpawnReason.VILLAGER_TRADE, this.getTradingPlayer(), this)); // Paper
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isTrading() ? SoundEvents.WANDERING_TRADER_TRADE : SoundEvents.WANDERING_TRADER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.WANDERING_TRADER_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.WANDERING_TRADER_DEATH;
    }

    @Override
    public SoundEvent getConsumeSound(ItemStack stack) {
        return stack.is(Items.MILK_BUCKET) ? SoundEvents.WANDERING_TRADER_DRINK_MILK : SoundEvents.WANDERING_TRADER_DRINK_POTION;
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean getYesSound) {
        return getYesSound ? SoundEvents.WANDERING_TRADER_YES : SoundEvents.WANDERING_TRADER_NO;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.WANDERING_TRADER_YES;
    }

    public void setDespawnDelay(int despawnDelay) {
        this.despawnDelay = despawnDelay;
    }

    public int getDespawnDelay() {
        return this.despawnDelay;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide) {
            this.maybeDespawn();
        }
    }

    private void maybeDespawn() {
        if (this.despawnDelay > 0 && !this.isTrading() && --this.despawnDelay == 0) {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        }
    }

    public void setWanderTarget(@Nullable BlockPos wanderTarget) {
        this.wanderTarget = wanderTarget;
    }

    @Nullable
    public BlockPos getWanderTarget() {
        return this.wanderTarget;
    }

    class WanderToPositionGoal extends Goal {
        final WanderingTrader trader;
        final double stopDistance;
        final double speedModifier;

        WanderToPositionGoal(final WanderingTrader trader, final double stopDistance, final double speedModifier) {
            this.trader = trader;
            this.stopDistance = stopDistance;
            this.speedModifier = speedModifier;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public void stop() {
            this.trader.setWanderTarget(null);
            WanderingTrader.this.navigation.stop();
        }

        @Override
        public boolean canUse() {
            BlockPos wanderTarget = this.trader.getWanderTarget();
            return wanderTarget != null && this.isTooFarAway(wanderTarget, this.stopDistance);
        }

        @Override
        public void tick() {
            BlockPos wanderTarget = this.trader.getWanderTarget();
            if (wanderTarget != null && WanderingTrader.this.navigation.isDone()) {
                if (this.isTooFarAway(wanderTarget, 10.0)) {
                    Vec3 vec3 = new Vec3(
                            wanderTarget.getX() - this.trader.getX(), wanderTarget.getY() - this.trader.getY(), wanderTarget.getZ() - this.trader.getZ()
                        )
                        .normalize();
                    Vec3 vec31 = vec3.scale(10.0).add(this.trader.getX(), this.trader.getY(), this.trader.getZ());
                    WanderingTrader.this.navigation.moveTo(vec31.x, vec31.y, vec31.z, this.speedModifier);
                } else {
                    WanderingTrader.this.navigation.moveTo(wanderTarget.getX(), wanderTarget.getY(), wanderTarget.getZ(), this.speedModifier);
                }
            }
        }

        private boolean isTooFarAway(BlockPos pos, double distance) {
            return !pos.closerToCenterThan(this.trader.position(), distance);
        }
    }
}
