package net.minecraft.world.entity.ai.goal;

import javax.annotation.Nullable;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.HoverRandomPos;
import net.minecraft.world.phys.Vec3;

public class WaterAvoidingRandomFlyingGoal extends WaterAvoidingRandomStrollGoal {
    public WaterAvoidingRandomFlyingGoal(PathfinderMob mob, double speedModifier) {
        super(mob, speedModifier);
    }

    @Nullable
    @Override
    protected Vec3 getPosition() {
        Vec3 viewVector = this.mob.getViewVector(0.0F);
        int i = 8;
        Vec3 pos = HoverRandomPos.getPos(this.mob, 8, 7, viewVector.x, viewVector.z, (float) (Math.PI / 2), 3, 1);
        return pos != null ? pos : AirAndWaterRandomPos.getPos(this.mob, 8, 4, -2, viewVector.x, viewVector.z, (float) (Math.PI / 2));
    }
}
