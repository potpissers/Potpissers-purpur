package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.ClimbOnTopOfPowderSnowGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;

public class Silverfish extends Monster {
    @Nullable
    private Silverfish.SilverfishWakeUpFriendsGoal friendsGoal;

    public Silverfish(EntityType<? extends Silverfish> entityType, Level level) {
        super(entityType, level);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.silverfishRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.silverfishRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.silverfishControllable;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.silverfishMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.silverfishScale);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(this.level().purpurConfig.silverfishMovementSpeed);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(this.level().purpurConfig.silverfishAttackDamage);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.silverfishTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    @Override
    protected void registerGoals() {
        this.friendsGoal = new Silverfish.SilverfishWakeUpFriendsGoal(this);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.goalSelector.addGoal(1, new ClimbOnTopOfPowderSnowGoal(this, this.level()));
        this.goalSelector.addGoal(3, this.friendsGoal);
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(5, new Silverfish.SilverfishMergeWithStoneGoal(this));
        this.targetSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 8.0).add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.ATTACK_DAMAGE, 1.0);
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.EVENTS;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SILVERFISH_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.SILVERFISH_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.SILVERFISH_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        this.playSound(SoundEvents.SILVERFISH_STEP, 0.15F, 1.0F);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableTo(level, damageSource)) {
            return false;
        } else {
            if ((damageSource.getEntity() != null || damageSource.is(DamageTypeTags.ALWAYS_TRIGGERS_SILVERFISH)) && this.friendsGoal != null) {
                this.friendsGoal.notifyHurt();
            }

            return super.hurtServer(level, damageSource, amount);
        }
    }

    @Override
    public void tick() {
        this.yBodyRot = this.getYRot();
        super.tick();
    }

    @Override
    public void setYBodyRot(float offset) {
        this.setYRot(offset);
        super.setYBodyRot(offset);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return InfestedBlock.isCompatibleHostBlock(level.getBlockState(pos.below())) ? 10.0F : super.getWalkTargetValue(pos, level);
    }

    public static boolean checkSilverfishSpawnRules(
        EntityType<Silverfish> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        if (!checkAnyLightMonsterSpawnRules(entityType, level, spawnReason, pos, random)) {
            return false;
        } else if (EntitySpawnReason.isSpawner(spawnReason)) {
            return true;
        } else {
            Player nearestPlayer = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5.0, true);
            return !(nearestPlayer != null && !nearestPlayer.affectsSpawning) && nearestPlayer == null; // Paper - Affects Spawning API
        }
    }

    static class SilverfishMergeWithStoneGoal extends RandomStrollGoal {
        @Nullable
        private Direction selectedDirection;
        private boolean doMerge;

        public SilverfishMergeWithStoneGoal(Silverfish silverfish) {
            super(silverfish, 1.0, 10);
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.mob.getTarget() != null) {
                return false;
            } else if (!this.mob.getNavigation().isDone()) {
                return false;
            } else {
                RandomSource random = this.mob.getRandom();
                if (getServerLevel(this.mob).purpurConfig.silverfishBypassMobGriefing ^ getServerLevel(this.mob).getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && random.nextInt(reducedTickDelay(10)) == 0) { // Purpur - Add mobGriefing bypass to everything affected
                    this.selectedDirection = Direction.getRandom(random);
                    BlockPos blockPos = BlockPos.containing(this.mob.getX(), this.mob.getY() + 0.5, this.mob.getZ()).relative(this.selectedDirection);
                    BlockState blockState = this.mob.level().getBlockState(blockPos);
                    if (InfestedBlock.isCompatibleHostBlock(blockState)) {
                        this.doMerge = true;
                        return true;
                    }
                }

                this.doMerge = false;
                return super.canUse();
            }
        }

        @Override
        public boolean canContinueToUse() {
            return !this.doMerge && super.canContinueToUse();
        }

        @Override
        public void start() {
            if (!this.doMerge) {
                super.start();
            } else {
                LevelAccessor levelAccessor = this.mob.level();
                BlockPos blockPos = BlockPos.containing(this.mob.getX(), this.mob.getY() + 0.5, this.mob.getZ()).relative(this.selectedDirection);
                BlockState blockState = levelAccessor.getBlockState(blockPos);
                if (InfestedBlock.isCompatibleHostBlock(blockState)) {
                    // CraftBukkit start
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this.mob, blockPos, InfestedBlock.infestedStateByHost(blockState))) {
                        return;
                    }
                    // CraftBukkit end
                    levelAccessor.setBlock(blockPos, InfestedBlock.infestedStateByHost(blockState), 3);
                    this.mob.spawnAnim();
                    this.mob.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.ENTER_BLOCK); // CraftBukkit - add Bukkit remove cause
                }
            }
        }
    }

    static class SilverfishWakeUpFriendsGoal extends Goal {
        private final Silverfish silverfish;
        private int lookForFriends;

        public SilverfishWakeUpFriendsGoal(Silverfish silverfish) {
            this.silverfish = silverfish;
        }

        public void notifyHurt() {
            if (this.lookForFriends == 0) {
                this.lookForFriends = this.adjustedTickDelay(20);
            }
        }

        @Override
        public boolean canUse() {
            return this.lookForFriends > 0;
        }

        @Override
        public void tick() {
            this.lookForFriends--;
            if (this.lookForFriends <= 0) {
                Level level = this.silverfish.level();
                RandomSource random = this.silverfish.getRandom();
                BlockPos blockPos = this.silverfish.blockPosition();

                for (int i = 0; i <= 5 && i >= -5; i = (i <= 0 ? 1 : 0) - i) {
                    for (int i1 = 0; i1 <= 10 && i1 >= -10; i1 = (i1 <= 0 ? 1 : 0) - i1) {
                        for (int i2 = 0; i2 <= 10 && i2 >= -10; i2 = (i2 <= 0 ? 1 : 0) - i2) {
                            BlockPos blockPos1 = blockPos.offset(i1, i, i2);
                            BlockState blockState = level.getBlockState(blockPos1);
                            Block block = blockState.getBlock();
                            if (block instanceof InfestedBlock) {
                                // CraftBukkit start
                                BlockState afterState = getServerLevel(level).purpurConfig.silverfishBypassMobGriefing ^ getServerLevel(level).getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) ? blockState.getFluidState().createLegacyBlock() : ((InfestedBlock) block).hostStateByInfested(level.getBlockState(blockPos1)); // Paper - fix wrong block state // Purpur - Add mobGriefing bypass to everything affected
                                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this.silverfish, blockPos1, afterState)) { // Paper - fix wrong block state
                                    continue;
                                }
                                // CraftBukkit end
                                if (getServerLevel(level).getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                                    level.destroyBlock(blockPos1, true, this.silverfish);
                                } else {
                                    level.setBlock(blockPos1, ((InfestedBlock)block).hostStateByInfested(level.getBlockState(blockPos1)), 3);
                                }

                                if (random.nextBoolean()) {
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
