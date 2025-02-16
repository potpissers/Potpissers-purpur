package net.minecraft.world.entity.ai.goal.target;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

public class OwnerHurtByTargetGoal extends TargetGoal {
    private final TamableAnimal tameAnimal;
    private LivingEntity ownerLastHurtBy;
    private int timestamp;

    public OwnerHurtByTargetGoal(TamableAnimal tameAnimal) {
        super(tameAnimal, false);
        this.tameAnimal = tameAnimal;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.tameAnimal.isTame() && !this.tameAnimal.isOrderedToSit()) {
            LivingEntity owner = this.tameAnimal.getOwner();
            if (owner == null) {
                return false;
            } else {
                this.ownerLastHurtBy = owner.getLastHurtByMob();
                int lastHurtByMobTimestamp = owner.getLastHurtByMobTimestamp();
                return lastHurtByMobTimestamp != this.timestamp
                    && this.canAttack(this.ownerLastHurtBy, TargetingConditions.DEFAULT)
                    && this.tameAnimal.wantsToAttack(this.ownerLastHurtBy, owner);
            }
        } else {
            return false;
        }
    }

    @Override
    public void start() {
        this.mob.setTarget(this.ownerLastHurtBy, org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_ATTACKED_OWNER, true); // CraftBukkit - reason
        LivingEntity owner = this.tameAnimal.getOwner();
        if (owner != null) {
            this.timestamp = owner.getLastHurtByMobTimestamp();
        }

        super.start();
    }
}
