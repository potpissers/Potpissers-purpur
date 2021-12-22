package net.minecraft.world.entity.monster;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;

public class Stray extends AbstractSkeleton {
    public Stray(EntityType<? extends Stray> entityType, Level level) {
        super(entityType, level);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.strayRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.strayRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.strayControllable;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.strayMaxHealth);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.strayTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.strayAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    public static boolean checkStraySpawnRules(
        EntityType<Stray> entityType, ServerLevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        BlockPos blockPos = pos;

        do {
            blockPos = blockPos.above();
        } while (level.getBlockState(blockPos).is(Blocks.POWDER_SNOW));

        return checkMonsterSpawnRules(entityType, level, spawnReason, pos, random)
            && (EntitySpawnReason.isSpawner(spawnReason) || level.canSeeSky(blockPos.below()));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.STRAY_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.STRAY_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.STRAY_DEATH;
    }

    @Override
    SoundEvent getStepSound() {
        return SoundEvents.STRAY_STEP;
    }

    @Override
    protected AbstractArrow getArrow(ItemStack arrow, float velocity, @Nullable ItemStack weapon) {
        AbstractArrow abstractArrow = super.getArrow(arrow, velocity, weapon);
        if (abstractArrow instanceof Arrow) {
            ((Arrow)abstractArrow).addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600));
        }

        return abstractArrow;
    }
}
