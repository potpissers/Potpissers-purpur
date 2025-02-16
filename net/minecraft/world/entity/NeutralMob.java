package net.minecraft.world.entity;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

public interface NeutralMob {
    String TAG_ANGER_TIME = "AngerTime";
    String TAG_ANGRY_AT = "AngryAt";

    int getRemainingPersistentAngerTime();

    void setRemainingPersistentAngerTime(int remainingPersistentAngerTime);

    @Nullable
    UUID getPersistentAngerTarget();

    void setPersistentAngerTarget(@Nullable UUID persistentAngerTarget);

    void startPersistentAngerTimer();

    default void addPersistentAngerSaveData(CompoundTag nbt) {
        nbt.putInt("AngerTime", this.getRemainingPersistentAngerTime());
        if (this.getPersistentAngerTarget() != null) {
            nbt.putUUID("AngryAt", this.getPersistentAngerTarget());
        }
    }

    default void readPersistentAngerSaveData(Level level, CompoundTag tag) {
        this.setRemainingPersistentAngerTime(tag.getInt("AngerTime"));
        if (level instanceof ServerLevel) {
            if (!tag.hasUUID("AngryAt")) {
                this.setPersistentAngerTarget(null);
            } else {
                UUID uuid = tag.getUUID("AngryAt");
                this.setPersistentAngerTarget(uuid);
                Entity entity = ((ServerLevel)level).getEntity(uuid);
                if (entity != null) {
                    if (entity instanceof Mob mob) {
                        this.setTarget(mob);
                        this.setLastHurtByMob(mob);
                    }

                    if (entity instanceof Player player) {
                        this.setTarget(player);
                        this.setLastHurtByPlayer(player);
                    }
                }
            }
        }
    }

    default void updatePersistentAnger(ServerLevel serverLevel, boolean updateAnger) {
        LivingEntity target = this.getTarget();
        UUID persistentAngerTarget = this.getPersistentAngerTarget();
        if ((target == null || target.isDeadOrDying()) && persistentAngerTarget != null && serverLevel.getEntity(persistentAngerTarget) instanceof Mob) {
            this.stopBeingAngry();
        } else {
            if (target != null && !Objects.equals(persistentAngerTarget, target.getUUID())) {
                this.setPersistentAngerTarget(target.getUUID());
                this.startPersistentAngerTimer();
            }

            if (this.getRemainingPersistentAngerTime() > 0 && (target == null || target.getType() != EntityType.PLAYER || !updateAnger)) {
                this.setRemainingPersistentAngerTime(this.getRemainingPersistentAngerTime() - 1);
                if (this.getRemainingPersistentAngerTime() == 0) {
                    this.stopBeingAngry();
                }
            }
        }
    }

    default boolean isAngryAt(LivingEntity entity, ServerLevel level) {
        return this.canAttack(entity)
            && (entity.getType() == EntityType.PLAYER && this.isAngryAtAllPlayers(level) || entity.getUUID().equals(this.getPersistentAngerTarget()));
    }

    default boolean isAngryAtAllPlayers(ServerLevel level) {
        return level.getGameRules().getBoolean(GameRules.RULE_UNIVERSAL_ANGER) && this.isAngry() && this.getPersistentAngerTarget() == null;
    }

    default boolean isAngry() {
        return this.getRemainingPersistentAngerTime() > 0;
    }

    default void playerDied(ServerLevel level, Player player) {
        if (level.getGameRules().getBoolean(GameRules.RULE_FORGIVE_DEAD_PLAYERS)) {
            if (player.getUUID().equals(this.getPersistentAngerTarget())) {
                this.stopBeingAngry();
            }
        }
    }

    default void forgetCurrentTargetAndRefreshUniversalAnger() {
        this.stopBeingAngry();
        this.startPersistentAngerTimer();
    }

    default void stopBeingAngry() {
        this.setLastHurtByMob(null);
        this.setPersistentAngerTarget(null);
        this.setTarget(null);
        this.setRemainingPersistentAngerTime(0);
    }

    @Nullable
    LivingEntity getLastHurtByMob();

    void setLastHurtByMob(@Nullable LivingEntity livingEntity);

    void setLastHurtByPlayer(@Nullable Player player);

    void setTarget(@Nullable LivingEntity livingEntity);

    boolean canAttack(LivingEntity entity);

    @Nullable
    LivingEntity getTarget();
}
