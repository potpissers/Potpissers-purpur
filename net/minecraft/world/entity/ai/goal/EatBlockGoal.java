package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;

public class EatBlockGoal extends Goal {
    private static final int EAT_ANIMATION_TICKS = 40;
    private static final Predicate<BlockState> IS_TALL_GRASS = BlockStatePredicate.forBlock(Blocks.SHORT_GRASS);
    private final Mob mob;
    private final Level level;
    private int eatAnimationTick;

    public EatBlockGoal(Mob mob) {
        this.mob = mob;
        this.level = mob.level();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        // Paper start - Fix MC-210802
        if (!((net.minecraft.server.level.ServerLevel) this.level).chunkSource.chunkMap.anyPlayerCloseEnoughForSpawning(this.mob.chunkPosition())) {
            return false;
        }
        // Paper end
        if (this.mob.getRandom().nextInt(this.mob.isBaby() ? 50 : 1000) != 0) {
            return false;
        } else {
            BlockPos blockPos = this.mob.blockPosition();
            return IS_TALL_GRASS.test(this.level.getBlockState(blockPos)) || this.level.getBlockState(blockPos.below()).is(Blocks.GRASS_BLOCK);
        }
    }

    @Override
    public void start() {
        this.eatAnimationTick = this.adjustedTickDelay(40);
        this.level.broadcastEntityEvent(this.mob, (byte)10);
        this.mob.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.eatAnimationTick = 0;
    }

    @Override
    public boolean canContinueToUse() {
        return this.eatAnimationTick > 0;
    }

    public int getEatAnimationTick() {
        return this.eatAnimationTick;
    }

    @Override
    public void tick() {
        this.eatAnimationTick = Math.max(0, this.eatAnimationTick - 1);
        if (this.eatAnimationTick == this.adjustedTickDelay(4)) {
            BlockPos blockPos = this.mob.blockPosition();
            final BlockState blockState = this.level.getBlockState(blockPos); // Paper - fix wrong block state
            if (IS_TALL_GRASS.test(blockState)) { // Paper - fix wrong block state
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this.mob, blockPos, blockState.getFluidState().createLegacyBlock(), !getServerLevel(this.level).purpurConfig.sheepBypassMobGriefing == !getServerLevel(this.level).getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))) { // CraftBukkit // Paper - fix wrong block state // Purpur - Add mobGriefing bypass to everything affected
                    this.level.destroyBlock(blockPos, false);
                }

                this.mob.ate();
            } else {
                BlockPos blockPos1 = blockPos.below();
                if (this.level.getBlockState(blockPos1).is(Blocks.GRASS_BLOCK)) {
                    if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this.mob, blockPos1, Blocks.DIRT.defaultBlockState(), !getServerLevel(this.level).purpurConfig.sheepBypassMobGriefing == !getServerLevel(this.level).getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))) { // CraftBukkit // Paper - Fix wrong block state // Purpur - Add mobGriefing bypass to everything affected
                        this.level.levelEvent(2001, blockPos1, Block.getId(Blocks.GRASS_BLOCK.defaultBlockState()));
                        this.level.setBlock(blockPos1, Blocks.DIRT.defaultBlockState(), 2);
                    }

                    this.mob.ate();
                }
            }
        }
    }
}
