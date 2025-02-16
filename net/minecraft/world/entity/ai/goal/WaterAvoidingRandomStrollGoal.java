package net.minecraft.world.entity.ai.goal;

import javax.annotation.Nullable;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

public class WaterAvoidingRandomStrollGoal extends RandomStrollGoal {
    public static final float PROBABILITY = 0.001F;
    protected final float probability;

    public WaterAvoidingRandomStrollGoal(PathfinderMob mob, double speedModifier) {
        this(mob, speedModifier, 0.001F);
    }

    public WaterAvoidingRandomStrollGoal(PathfinderMob mob, double speedModifier, float probability) {
        super(mob, speedModifier);
        this.probability = probability;
    }

    @Nullable
    @Override
    protected Vec3 getPosition() {
        if (this.mob.isInWaterOrBubble()) {
            Vec3 pos = LandRandomPos.getPos(this.mob, 15, 7);
            return pos == null ? super.getPosition() : pos;
        } else {
            return this.mob.getRandom().nextFloat() >= this.probability ? LandRandomPos.getPos(this.mob, 10, 7) : super.getPosition();
        }
    }
}
