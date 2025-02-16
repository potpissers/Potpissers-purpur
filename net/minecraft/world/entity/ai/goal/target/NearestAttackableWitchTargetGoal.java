package net.minecraft.world.entity.ai.goal.target;

import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.raid.Raider;

public class NearestAttackableWitchTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
    private boolean canAttack = true;

    public NearestAttackableWitchTargetGoal(
        Raider raider, Class<T> targetType, int interval, boolean mustSee, boolean mustReach, @Nullable TargetingConditions.Selector selector
    ) {
        super(raider, targetType, interval, mustSee, mustReach, selector);
    }

    public void setCanAttack(boolean canAttack) {
        this.canAttack = canAttack;
    }

    @Override
    public boolean canUse() {
        return this.canAttack && super.canUse();
    }
}
