package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public abstract class Goal {
    private final EnumSet<Goal.Flag> flags = EnumSet.noneOf(Goal.Flag.class);

    public abstract boolean canUse();

    public boolean canContinueToUse() {
        return this.canUse();
    }

    public boolean isInterruptable() {
        return true;
    }

    public void start() {
    }

    public void stop() {
    }

    public boolean requiresUpdateEveryTick() {
        return false;
    }

    public void tick() {
    }

    public void setFlags(EnumSet<Goal.Flag> flagSet) {
        this.flags.clear();
        this.flags.addAll(flagSet);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    public EnumSet<Goal.Flag> getFlags() {
        return this.flags;
    }


    // Paper start - Mob Goal API
    public boolean hasFlag(final Goal.Flag flag) {
        return this.flags.contains(flag);
    }

    public void addFlag(final Goal.Flag flag) {
        this.flags.add(flag);
    }
    // Paper end - Mob Goal API

    protected int adjustedTickDelay(int adjustment) {
        return this.requiresUpdateEveryTick() ? adjustment : reducedTickDelay(adjustment);
    }

    protected static int reducedTickDelay(int reduction) {
        return Mth.positiveCeilDiv(reduction, 2);
    }

    protected static ServerLevel getServerLevel(Entity entity) {
        return (ServerLevel)entity.level();
    }

    protected static ServerLevel getServerLevel(Level level) {
        return (ServerLevel)level;
    }

    // Paper start - Mob goal api
    private com.destroystokyo.paper.entity.ai.PaperVanillaGoal<?> vanillaGoal;
    public <T extends org.bukkit.entity.Mob> com.destroystokyo.paper.entity.ai.Goal<T> asPaperVanillaGoal() {
        if (this.vanillaGoal == null) {
            this.vanillaGoal = new com.destroystokyo.paper.entity.ai.PaperVanillaGoal<>(this);
        }
        //noinspection unchecked
        return (com.destroystokyo.paper.entity.ai.Goal<T>) this.vanillaGoal;
    }
    // Paper end - Mob goal api

    public static enum Flag {
        UNKNOWN_BEHAVIOR, // Paper - add UNKNOWN_BEHAVIOR
        MOVE,
        LOOK,
        JUMP,
        TARGET;
    }
}
