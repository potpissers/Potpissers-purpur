package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.providers.EnchantmentProvider;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

public class Vindicator extends AbstractIllager {
    private static final String TAG_JOHNNY = "Johnny";
    public static final Predicate<Difficulty> DOOR_BREAKING_PREDICATE = difficulty -> difficulty == Difficulty.NORMAL || difficulty == Difficulty.HARD;
    public boolean isJohnny;

    public Vindicator(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.vindicatorRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.vindicatorRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.vindicatorControllable;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.vindicatorMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.vindicatorScale);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.vindicatorTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.vindicatorAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Creaking.class, 8.0F, 1.0, 1.2));
        this.goalSelector.addGoal(2, new Vindicator.VindicatorBreakDoorGoal(this));
        this.goalSelector.addGoal(3, new AbstractIllager.RaiderOpenDoorGoal(this));
        this.goalSelector.addGoal(4, new Raider.HoldGroundAttackGoal(this, 10.0F));
        this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0, false));
        this.targetSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
        this.targetSelector.addGoal(4, new Vindicator.VindicatorJohnnyAttackGoal(this));
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (!this.isNoAi() && GoalUtils.hasGroundPathNavigation(this)) {
            boolean isRaided = level.isRaided(this.blockPosition());
            ((GroundPathNavigation)this.getNavigation()).setCanOpenDoors(isRaided);
        }

        super.customServerAiStep(level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MOVEMENT_SPEED, 0.35F)
            .add(Attributes.FOLLOW_RANGE, 12.0)
            .add(Attributes.MAX_HEALTH, 24.0)
            .add(Attributes.ATTACK_DAMAGE, 5.0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (this.isJohnny) {
            compound.putBoolean("Johnny", true);
        }
    }

    @Override
    public AbstractIllager.IllagerArmPose getArmPose() {
        if (this.isAggressive()) {
            return AbstractIllager.IllagerArmPose.ATTACKING;
        } else {
            return this.isCelebrating() ? AbstractIllager.IllagerArmPose.CELEBRATING : AbstractIllager.IllagerArmPose.CROSSED;
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("Johnny", 99)) {
            this.isJohnny = compound.getBoolean("Johnny");
        }
    }

    @Override
    public SoundEvent getCelebrateSound() {
        return SoundEvents.VINDICATOR_CELEBRATE;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        SpawnGroupData spawnGroupData1 = super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
        ((GroundPathNavigation)this.getNavigation()).setCanOpenDoors(true);
        RandomSource random = level.getRandom();
        this.populateDefaultEquipmentSlots(random, difficulty);
        this.populateDefaultEquipmentEnchantments(level, random, difficulty);
        // Purpur start - Special mobs naturally spawn
        if (level().purpurConfig.vindicatorJohnnySpawnChance > 0D && random.nextDouble() <= level().purpurConfig.vindicatorJohnnySpawnChance) {
            setCustomName(Component.translatable("Johnny"));
        }
        // Purpur end - Special mobs naturally spawn
        return spawnGroupData1;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        if (this.getCurrentRaid() == null) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        }
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        if (!this.isJohnny && name != null && name.getString().equals("Johnny")) {
            this.isJohnny = true;
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VINDICATOR_AMBIENT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.VINDICATOR_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.VINDICATOR_HURT;
    }

    @Override
    public void applyRaidBuffs(ServerLevel level, int wave, boolean unused) {
        ItemStack itemStack = new ItemStack(Items.IRON_AXE);
        Raid currentRaid = this.getCurrentRaid();
        boolean flag = this.random.nextFloat() <= currentRaid.getEnchantOdds();
        if (flag) {
            ResourceKey<EnchantmentProvider> resourceKey = wave > currentRaid.getNumGroups(Difficulty.NORMAL)
                ? VanillaEnchantmentProviders.RAID_VINDICATOR_POST_WAVE_5
                : VanillaEnchantmentProviders.RAID_VINDICATOR;
            EnchantmentHelper.enchantItemFromProvider(
                itemStack, level.registryAccess(), resourceKey, level.getCurrentDifficultyAt(this.blockPosition()), this.random
            );
        }

        this.setItemSlot(EquipmentSlot.MAINHAND, itemStack);
    }

    static class VindicatorBreakDoorGoal extends BreakDoorGoal {
        public VindicatorBreakDoorGoal(Mob mob) {
            super(mob, 6, com.google.common.base.Predicates.in(mob.level().paperConfig().entities.behavior.doorBreakingDifficulty.getOrDefault(mob.getType(), mob.level().paperConfig().entities.behavior.doorBreakingDifficulty.get(EntityType.VINDICATOR)))); // Paper - Configurable door breaking difficulty
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canContinueToUse() {
            Vindicator vindicator = (Vindicator)this.mob;
            return vindicator.hasActiveRaid() && super.canContinueToUse();
        }

        @Override
        public boolean canUse() {
            Vindicator vindicator = (Vindicator)this.mob;
            return vindicator.hasActiveRaid() && vindicator.random.nextInt(reducedTickDelay(10)) == 0 && super.canUse();
        }

        @Override
        public void start() {
            super.start();
            this.mob.setNoActionTime(0);
        }
    }

    static class VindicatorJohnnyAttackGoal extends NearestAttackableTargetGoal<LivingEntity> {
        public VindicatorJohnnyAttackGoal(Vindicator vindicator) {
            super(vindicator, LivingEntity.class, 0, true, true, (entity, level) -> entity.attackable());
        }

        @Override
        public boolean canUse() {
            return ((Vindicator)this.mob).isJohnny && super.canUse();
        }

        @Override
        public void start() {
            super.start();
            this.mob.setNoActionTime(0);
        }
    }
}
