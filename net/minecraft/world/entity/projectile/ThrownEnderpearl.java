package net.minecraft.world.entity.projectile;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class ThrownEnderpearl extends ThrowableItemProjectile {
    private long ticketTimer = 0L;

    public ThrownEnderpearl(EntityType<? extends ThrownEnderpearl> entityType, Level level) {
        super(entityType, level);
    }

    public ThrownEnderpearl(Level level, LivingEntity owner, ItemStack item) {
        super(EntityType.ENDER_PEARL, owner, level, item);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.ENDER_PEARL;
    }

    @Override
    protected void setOwnerThroughUUID(UUID uuid) {
        this.deregisterFromCurrentOwner();
        super.setOwnerThroughUUID(uuid);
        this.registerToCurrentOwner();
    }

    @Override
    public void setOwner(@Nullable Entity owner) {
        this.deregisterFromCurrentOwner();
        super.setOwner(owner);
        this.registerToCurrentOwner();
    }

    private void deregisterFromCurrentOwner() {
        if (this.getOwner() instanceof ServerPlayer serverPlayer) {
            serverPlayer.deregisterEnderPearl(this);
        }
    }

    private void registerToCurrentOwner() {
        if (this.getOwner() instanceof ServerPlayer serverPlayer) {
            serverPlayer.registerEnderPearl(this);
        }
    }

    @Nullable
    @Override
    protected Entity findOwner(UUID entityUuid) {
        if (this.level() instanceof ServerLevel serverLevel) {
            Entity entity = super.findOwner(entityUuid);
            if (entity != null) {
                return entity;
            } else {
                for (ServerLevel serverLevel1 : serverLevel.getServer().getAllLevels()) {
                    if (serverLevel1 != serverLevel) {
                        entity = serverLevel1.getEntity(entityUuid);
                        if (entity != null) {
                            return entity;
                        }
                    }
                }

                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        result.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);

        for (int i = 0; i < 32; i++) {
            this.level()
                .addParticle(
                    ParticleTypes.PORTAL,
                    this.getX(),
                    this.getY() + this.random.nextDouble() * 2.0,
                    this.getZ(),
                    this.random.nextGaussian(),
                    0.0,
                    this.random.nextGaussian()
                );
        }

        if (this.level() instanceof ServerLevel serverLevel && !this.isRemoved()) {
            Entity owner = this.getOwner();
            if (owner != null && isAllowedToTeleportOwner(owner, serverLevel)) {
                if (owner.isPassenger()) {
                    owner.unRide();
                }

                Vec3 vec3 = this.oldPosition();
                if (owner instanceof ServerPlayer serverPlayer) {
                    if (serverPlayer.connection.isAcceptingMessages()) {
                        if (this.random.nextFloat() < 0.05F && serverLevel.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                            Endermite endermite = EntityType.ENDERMITE.create(serverLevel, EntitySpawnReason.TRIGGERED);
                            if (endermite != null) {
                                endermite.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), owner.getXRot());
                                serverLevel.addFreshEntity(endermite);
                            }
                        }

                        if (this.isOnPortalCooldown()) {
                            owner.setPortalCooldown();
                        }

                        ServerPlayer serverPlayer1 = serverPlayer.teleport(
                            new TeleportTransition(
                                serverLevel, vec3, Vec3.ZERO, 0.0F, 0.0F, Relative.union(Relative.ROTATION, Relative.DELTA), TeleportTransition.DO_NOTHING
                            )
                        );
                        if (serverPlayer1 != null) {
                            serverPlayer1.resetFallDistance();
                            serverPlayer1.resetCurrentImpulseContext();
                            serverPlayer1.hurtServer(serverPlayer.serverLevel(), this.damageSources().enderPearl(), 5.0F);
                        }

                        this.playSound(serverLevel, vec3);
                    }
                } else {
                    Entity entity = owner.teleport(
                        new TeleportTransition(serverLevel, vec3, owner.getDeltaMovement(), owner.getYRot(), owner.getXRot(), TeleportTransition.DO_NOTHING)
                    );
                    if (entity != null) {
                        entity.resetFallDistance();
                    }

                    this.playSound(serverLevel, vec3);
                }

                this.discard();
            } else {
                this.discard();
            }
        }
    }

    private static boolean isAllowedToTeleportOwner(Entity entity, Level level) {
        if (entity.level().dimension() == level.dimension()) {
            return !(entity instanceof LivingEntity livingEntity) ? entity.isAlive() : livingEntity.isAlive() && !livingEntity.isSleeping();
        } else {
            return entity.canUsePortal(true);
        }
    }

    @Override
    public void tick() {
        int sectionPosX = SectionPos.blockToSectionCoord(this.position().x());
        int sectionPosZ = SectionPos.blockToSectionCoord(this.position().z());
        Entity owner = this.getOwner();
        if (owner instanceof ServerPlayer serverPlayer
            && !owner.isAlive()
            && serverPlayer.serverLevel().getGameRules().getBoolean(GameRules.RULE_ENDER_PEARLS_VANISH_ON_DEATH)) {
            this.discard();
        } else {
            super.tick();
        }

        if (this.isAlive()) {
            BlockPos blockPos = BlockPos.containing(this.position());
            if ((
                    --this.ticketTimer <= 0L
                        || sectionPosX != SectionPos.blockToSectionCoord(blockPos.getX())
                        || sectionPosZ != SectionPos.blockToSectionCoord(blockPos.getZ())
                )
                && owner instanceof ServerPlayer serverPlayer1) {
                this.ticketTimer = serverPlayer1.registerAndUpdateEnderPearlTicket(this);
            }
        }
    }

    private void playSound(Level level, Vec3 pos) {
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS);
    }

    @Nullable
    @Override
    public Entity teleport(TeleportTransition teleportTransition) {
        Entity entity = super.teleport(teleportTransition);
        if (entity != null) {
            entity.placePortalTicket(BlockPos.containing(entity.position()));
        }

        return entity;
    }

    @Override
    public boolean canTeleport(Level fromLevel, Level toLevel) {
        return fromLevel.dimension() == Level.END && toLevel.dimension() == Level.OVERWORLD && this.getOwner() instanceof ServerPlayer serverPlayer
            ? super.canTeleport(fromLevel, toLevel) && serverPlayer.seenCredits
            : super.canTeleport(fromLevel, toLevel);
    }

    @Override
    protected void onInsideBlock(BlockState state) {
        super.onInsideBlock(state);
        if (state.is(Blocks.END_GATEWAY) && this.getOwner() instanceof ServerPlayer serverPlayer) {
            serverPlayer.onInsideBlock(state);
        }
    }

    @Override
    public void onRemoval(Entity.RemovalReason reason) {
        if (reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            this.deregisterFromCurrentOwner();
        }

        super.onRemoval(reason);
    }
}
