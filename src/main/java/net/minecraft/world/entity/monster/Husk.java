package net.minecraft.world.entity.monster;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

public class Husk extends Zombie {

    public Husk(EntityType<? extends Husk> type, Level world) {
        super(type, world);
        this.setShouldBurnInDay(false); // Purpur - API for any mob to burn daylight
    }

    // Purpur start
    @Override
    public boolean isRidable() {
        return level().purpurConfig.huskRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.huskRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.huskControllable;
    }
    // Purpur end

    @Override
    public void initAttributes() {
        this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.huskMaxHealth);
    }

    @Override
    protected void randomizeReinforcementsChance() {
        this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(this.random.nextDouble() * this.level().purpurConfig.huskSpawnReinforcements);
    }

    @Override
    public boolean jockeyOnlyBaby() {
        return level().purpurConfig.huskJockeyOnlyBaby;
    }

    @Override
    public double jockeyChance() {
        return level().purpurConfig.huskJockeyChance;
    }

    @Override
    public boolean jockeyTryExistingChickens() {
        return level().purpurConfig.huskJockeyTryExistingChickens;
    }

    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.huskTakeDamageFromWater;
    }

    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.huskAlwaysDropExp;
    }

    public static boolean checkHuskSpawnRules(EntityType<Husk> type, ServerLevelAccessor world, MobSpawnType spawnReason, BlockPos pos, RandomSource random) {
        return checkMonsterSpawnRules(type, world, spawnReason, pos, random) && (MobSpawnType.isSpawner(spawnReason) || world.canSeeSky(pos));
    }

    @Override
    public boolean isSunSensitive() {
        return this.shouldBurnInDay; // Purpur - moved to LivingEntity; keep methods for ABI compatibility - API for any mob to burn daylight
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.HUSK_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.HUSK_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.HUSK_DEATH;
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.HUSK_STEP;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean flag = super.doHurtTarget(target);

        if (flag && this.getMainHandItem().isEmpty() && target instanceof LivingEntity) {
            float f = this.level().getCurrentDifficultyAt(this.blockPosition()).getEffectiveDifficulty();

            ((LivingEntity) target).addEffect(new MobEffectInstance(MobEffects.HUNGER, 140 * (int) f), this, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
        }

        return flag;
    }

    @Override
    protected boolean convertsInWater() {
        return true;
    }

    @Override
    protected void doUnderWaterConversion() {
        this.convertToZombieType(EntityType.ZOMBIE);
        if (!this.isSilent()) {
            this.level().levelEvent((Player) null, 1041, this.blockPosition(), 0);
        }

    }

    @Override
    protected ItemStack getSkull() {
        return ItemStack.EMPTY;
    }
}
