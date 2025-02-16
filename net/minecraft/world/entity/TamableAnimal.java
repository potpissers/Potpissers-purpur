package net.minecraft.world.entity;

import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.scores.PlayerTeam;

public abstract class TamableAnimal extends Animal implements OwnableEntity {
    public static final int TELEPORT_WHEN_DISTANCE_IS_SQ = 144;
    private static final int MIN_HORIZONTAL_DISTANCE_FROM_TARGET_AFTER_TELEPORTING = 2;
    private static final int MAX_HORIZONTAL_DISTANCE_FROM_TARGET_AFTER_TELEPORTING = 3;
    private static final int MAX_VERTICAL_DISTANCE_FROM_TARGET_AFTER_TELEPORTING = 1;
    protected static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(TamableAnimal.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<Optional<UUID>> DATA_OWNERUUID_ID = SynchedEntityData.defineId(
        TamableAnimal.class, EntityDataSerializers.OPTIONAL_UUID
    );
    private boolean orderedToSit;

    protected TamableAnimal(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS_ID, (byte)0);
        builder.define(DATA_OWNERUUID_ID, Optional.empty());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (this.getOwnerUUID() != null) {
            compound.putUUID("Owner", this.getOwnerUUID());
        }

        compound.putBoolean("Sitting", this.orderedToSit);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        UUID uuid;
        if (compound.hasUUID("Owner")) {
            uuid = compound.getUUID("Owner");
        } else {
            String string = compound.getString("Owner");
            uuid = OldUsersConverter.convertMobOwnerIfNecessary(this.getServer(), string);
        }

        if (uuid != null) {
            try {
                this.setOwnerUUID(uuid);
                this.setTame(true, false);
            } catch (Throwable var4) {
                this.setTame(false, true);
            }
        } else {
            this.setOwnerUUID(null);
            this.setTame(false, true);
        }

        this.orderedToSit = compound.getBoolean("Sitting");
        this.setInSittingPose(this.orderedToSit);
    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }

    @Override
    public boolean handleLeashAtDistance(Entity leashHolder, float distance) {
        if (this.isInSittingPose()) {
            if (distance > 10.0F) {
                this.dropLeash();
            }

            return false;
        } else {
            return super.handleLeashAtDistance(leashHolder, distance);
        }
    }

    protected void spawnTamingParticles(boolean tamed) {
        ParticleOptions particleOptions = ParticleTypes.HEART;
        if (!tamed) {
            particleOptions = ParticleTypes.SMOKE;
        }

        for (int i = 0; i < 7; i++) {
            double d = this.random.nextGaussian() * 0.02;
            double d1 = this.random.nextGaussian() * 0.02;
            double d2 = this.random.nextGaussian() * 0.02;
            this.level().addParticle(particleOptions, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), d, d1, d2);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 7) {
            this.spawnTamingParticles(true);
        } else if (id == 6) {
            this.spawnTamingParticles(false);
        } else {
            super.handleEntityEvent(id);
        }
    }

    public boolean isTame() {
        return (this.entityData.get(DATA_FLAGS_ID) & 4) != 0;
    }

    public void setTame(boolean tame, boolean applyTamingSideEffects) {
        byte b = this.entityData.get(DATA_FLAGS_ID);
        if (tame) {
            this.entityData.set(DATA_FLAGS_ID, (byte)(b | 4));
        } else {
            this.entityData.set(DATA_FLAGS_ID, (byte)(b & -5));
        }

        if (applyTamingSideEffects) {
            this.applyTamingSideEffects();
        }
    }

    protected void applyTamingSideEffects() {
    }

    public boolean isInSittingPose() {
        return (this.entityData.get(DATA_FLAGS_ID) & 1) != 0;
    }

    public void setInSittingPose(boolean sitting) {
        byte b = this.entityData.get(DATA_FLAGS_ID);
        if (sitting) {
            this.entityData.set(DATA_FLAGS_ID, (byte)(b | 1));
        } else {
            this.entityData.set(DATA_FLAGS_ID, (byte)(b & -2));
        }
    }

    @Nullable
    @Override
    public UUID getOwnerUUID() {
        return this.entityData.get(DATA_OWNERUUID_ID).orElse(null);
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.entityData.set(DATA_OWNERUUID_ID, Optional.ofNullable(uuid));
    }

    public void tame(Player player) {
        this.setTame(true, true);
        this.setOwnerUUID(player.getUUID());
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.TAME_ANIMAL.trigger(serverPlayer, this);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isOwnedBy(target) && super.canAttack(target);
    }

    public boolean isOwnedBy(LivingEntity entity) {
        return entity == this.getOwner();
    }

    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return true;
    }

    @Override
    public PlayerTeam getTeam() {
        if (this.isTame()) {
            LivingEntity owner = this.getOwner();
            if (owner != null) {
                return owner.getTeam();
            }
        }

        return super.getTeam();
    }

    @Override
    protected boolean considersEntityAsAlly(Entity entity) {
        if (this.isTame()) {
            LivingEntity owner = this.getOwner();
            if (entity == owner) {
                return true;
            }

            if (owner != null) {
                return owner.considersEntityAsAlly(entity);
            }
        }

        return super.considersEntityAsAlly(entity);
    }

    @Override
    public void die(DamageSource cause) {
        if (this.level() instanceof ServerLevel serverLevel
            && serverLevel.getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES)
            && this.getOwner() instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(this.getCombatTracker().getDeathMessage());
        }

        super.die(cause);
    }

    public boolean isOrderedToSit() {
        return this.orderedToSit;
    }

    public void setOrderedToSit(boolean orderedToSit) {
        this.orderedToSit = orderedToSit;
    }

    public void tryToTeleportToOwner() {
        LivingEntity owner = this.getOwner();
        if (owner != null) {
            this.teleportToAroundBlockPos(owner.blockPosition());
        }
    }

    public boolean shouldTryTeleportToOwner() {
        LivingEntity owner = this.getOwner();
        return owner != null && this.distanceToSqr(this.getOwner()) >= 144.0;
    }

    private void teleportToAroundBlockPos(BlockPos pos) {
        for (int i = 0; i < 10; i++) {
            int randomInt = this.random.nextIntBetweenInclusive(-3, 3);
            int randomInt1 = this.random.nextIntBetweenInclusive(-3, 3);
            if (Math.abs(randomInt) >= 2 || Math.abs(randomInt1) >= 2) {
                int randomInt2 = this.random.nextIntBetweenInclusive(-1, 1);
                if (this.maybeTeleportTo(pos.getX() + randomInt, pos.getY() + randomInt2, pos.getZ() + randomInt1)) {
                    return;
                }
            }
        }
    }

    private boolean maybeTeleportTo(int x, int y, int z) {
        if (!this.canTeleportTo(new BlockPos(x, y, z))) {
            return false;
        } else {
            this.moveTo(x + 0.5, y, z + 0.5, this.getYRot(), this.getXRot());
            this.navigation.stop();
            return true;
        }
    }

    private boolean canTeleportTo(BlockPos pos) {
        PathType pathTypeStatic = WalkNodeEvaluator.getPathTypeStatic(this, pos);
        if (pathTypeStatic != PathType.WALKABLE) {
            return false;
        } else {
            BlockState blockState = this.level().getBlockState(pos.below());
            if (!this.canFlyToOwner() && blockState.getBlock() instanceof LeavesBlock) {
                return false;
            } else {
                BlockPos blockPos = pos.subtract(this.blockPosition());
                return this.level().noCollision(this, this.getBoundingBox().move(blockPos));
            }
        }
    }

    public final boolean unableToMoveToOwner() {
        return this.isOrderedToSit() || this.isPassenger() || this.mayBeLeashed() || this.getOwner() != null && this.getOwner().isSpectator();
    }

    protected boolean canFlyToOwner() {
        return false;
    }

    public class TamableAnimalPanicGoal extends PanicGoal {
        public TamableAnimalPanicGoal(final double speedModifier, final TagKey<DamageType> panicCausingDamageTypes) {
            super(TamableAnimal.this, speedModifier, panicCausingDamageTypes);
        }

        public TamableAnimalPanicGoal(final double speedModifier) {
            super(TamableAnimal.this, speedModifier);
        }

        @Override
        public void tick() {
            if (!TamableAnimal.this.unableToMoveToOwner() && TamableAnimal.this.shouldTryTeleportToOwner()) {
                TamableAnimal.this.tryToTeleportToOwner();
            }

            super.tick();
        }
    }
}
