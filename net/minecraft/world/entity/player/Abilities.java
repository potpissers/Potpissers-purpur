package net.minecraft.world.entity.player;

import net.minecraft.nbt.CompoundTag;

public class Abilities {
    public boolean invulnerable;
    public boolean flying;
    public boolean mayfly;
    public boolean instabuild;
    public boolean mayBuild = true;
    public float flyingSpeed = 0.05F;
    public float walkingSpeed = 0.1F;

    public void addSaveData(CompoundTag compound) {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putBoolean("invulnerable", this.invulnerable);
        compoundTag.putBoolean("flying", this.flying);
        compoundTag.putBoolean("mayfly", this.mayfly);
        compoundTag.putBoolean("instabuild", this.instabuild);
        compoundTag.putBoolean("mayBuild", this.mayBuild);
        compoundTag.putFloat("flySpeed", this.flyingSpeed);
        compoundTag.putFloat("walkSpeed", this.walkingSpeed);
        compound.put("abilities", compoundTag);
    }

    public void loadSaveData(CompoundTag compound) {
        if (compound.contains("abilities", 10)) {
            CompoundTag compound1 = compound.getCompound("abilities");
            this.invulnerable = compound1.getBoolean("invulnerable");
            this.flying = compound1.getBoolean("flying");
            this.mayfly = compound1.getBoolean("mayfly");
            this.instabuild = compound1.getBoolean("instabuild");
            if (compound1.contains("flySpeed", 99)) {
                this.flyingSpeed = compound1.getFloat("flySpeed");
                this.walkingSpeed = compound1.getFloat("walkSpeed");
            }

            if (compound1.contains("mayBuild", 1)) {
                this.mayBuild = compound1.getBoolean("mayBuild");
            }
        }
    }

    public float getFlyingSpeed() {
        return this.flyingSpeed;
    }

    public void setFlyingSpeed(float flyingSpeed) {
        this.flyingSpeed = flyingSpeed;
    }

    public float getWalkingSpeed() {
        return this.walkingSpeed;
    }

    public void setWalkingSpeed(float walkingSpeed) {
        this.walkingSpeed = walkingSpeed;
    }
}
