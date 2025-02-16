package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public class LeapAtTargetGoal extends Goal {
    private final Mob mob;
    private LivingEntity target;
    private final float yd;

    public LeapAtTargetGoal(Mob mob, float yd) {
        this.mob = mob;
        this.yd = yd;
        this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.mob.hasControllingPassenger()) {
            return false;
        } else {
            this.target = this.mob.getTarget();
            if (this.target == null) {
                return false;
            } else {
                double d = this.mob.distanceToSqr(this.target);
                return !(d < 4.0) && !(d > 16.0) && this.mob.onGround() && this.mob.getRandom().nextInt(reducedTickDelay(5)) == 0;
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.onGround();
    }

    @Override
    public void start() {
        Vec3 deltaMovement = this.mob.getDeltaMovement();
        Vec3 vec3 = new Vec3(this.target.getX() - this.mob.getX(), 0.0, this.target.getZ() - this.mob.getZ());
        if (vec3.lengthSqr() > 1.0E-7) {
            vec3 = vec3.normalize().scale(0.4).add(deltaMovement.scale(0.2));
        }

        this.mob.setDeltaMovement(vec3.x, this.yd, vec3.z);
    }
}
