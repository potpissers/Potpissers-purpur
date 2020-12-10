package net.minecraft.world.entity.monster;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

public class Giant extends Monster {
    public Giant(EntityType<? extends Giant> type, Level world) {
        super(type, world);
    }

    // Purpur start
    @Override
    public boolean isRidable() {
        return level().purpurConfig.giantRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.giantRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.giantControllable;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this));
        this.targetSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this));
    }

    @Override
    protected void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.giantMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.giantScale);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(this.level().purpurConfig.giantMovementSpeed);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(this.level().purpurConfig.giantAttackDamage);
    }

    // Purpur end

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 100.0).add(Attributes.MOVEMENT_SPEED, 0.5).add(Attributes.ATTACK_DAMAGE, 50.0);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader world) {
        return world.getPathfindingCostFromLightLevels(pos);
    }
}
