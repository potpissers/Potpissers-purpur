package net.minecraft.world.entity;

import com.mojang.datafixers.util.Either;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;

public interface Leashable {
    String LEASH_TAG = "leash";
    double LEASH_TOO_FAR_DIST = 10.0;
    double LEASH_ELASTIC_DIST = 6.0;

    @Nullable
    Leashable.LeashData getLeashData();

    void setLeashData(@Nullable Leashable.LeashData leashData);

    default boolean isLeashed() {
        return this.getLeashData() != null && this.getLeashData().leashHolder != null;
    }

    default boolean mayBeLeashed() {
        return this.getLeashData() != null;
    }

    default boolean canHaveALeashAttachedToIt() {
        return this.canBeLeashed() && !this.isLeashed();
    }

    default boolean canBeLeashed() {
        return true;
    }

    default void setDelayedLeashHolderId(int delayedLeashHolderId) {
        this.setLeashData(new Leashable.LeashData(delayedLeashHolderId));
        dropLeash((Entity & Leashable)this, false, false);
    }

    default void readLeashData(CompoundTag tag) {
        Leashable.LeashData leashDataInternal = readLeashDataInternal(tag);
        if (this.getLeashData() != null && leashDataInternal == null) {
            this.removeLeash();
        }

        this.setLeashData(leashDataInternal);
    }

    @Nullable
    private static Leashable.LeashData readLeashDataInternal(CompoundTag tag) {
        if (tag.contains("leash", 10)) {
            // Paper start
            final CompoundTag leashTag = tag.getCompound("leash");
            if (!leashTag.hasUUID("UUID")) {
                return null;
            }
            return new Leashable.LeashData(Either.left(leashTag.getUUID("UUID")));
            // Paper end
        } else {
            if (tag.contains("leash", 11)) {
                Either<UUID, BlockPos> either = NbtUtils.readBlockPos(tag, "leash").<Either<UUID, BlockPos>>map(Either::right).orElse(null);
                if (either != null) {
                    return new Leashable.LeashData(either);
                }
            }

            return null;
        }
    }

    default void writeLeashData(CompoundTag tag, @Nullable Leashable.LeashData leashData) {
        if (leashData != null) {
            Either<UUID, BlockPos> either = leashData.delayedLeashInfo;
            // CraftBukkit start - SPIGOT-7487: Don't save (and possible drop) leash, when the holder was removed by a plugin
            if (leashData.leashHolder != null && leashData.leashHolder.pluginRemoved) {
                return;
            }
            // CraftBukkit end
            if (leashData.leashHolder instanceof LeashFenceKnotEntity leashFenceKnotEntity) {
                either = Either.right(leashFenceKnotEntity.getPos());
            } else if (leashData.leashHolder != null) {
                either = Either.left(leashData.leashHolder.getUUID());
            }

            if (either != null) {
                tag.put("leash", either.map(uuid -> {
                    CompoundTag compoundTag = new CompoundTag();
                    compoundTag.putUUID("UUID", uuid);
                    return compoundTag;
                }, NbtUtils::writeBlockPos));
            }
        }
    }

    private static <E extends Entity & Leashable> void restoreLeashFromSave(E entity, Leashable.LeashData leashData) {
        if (leashData.delayedLeashInfo != null && entity.level() instanceof ServerLevel serverLevel) {
            Optional<UUID> optional = leashData.delayedLeashInfo.left();
            Optional<BlockPos> optional1 = leashData.delayedLeashInfo.right();
            if (optional.isPresent()) {
                Entity entity1 = serverLevel.getEntity(optional.get());
                if (entity1 != null) {
                    setLeashedTo(entity, entity1, true);
                    return;
                }
            } else if (optional1.isPresent()) {
                setLeashedTo(entity, LeashFenceKnotEntity.getOrCreateKnot(serverLevel, optional1.get()), true);
                return;
            }

            if (entity.tickCount > 100) {
                entity.forceDrops = true; // CraftBukkit
                entity.spawnAtLocation(serverLevel, Items.LEAD);
                entity.forceDrops = false; // CraftBukkit
                entity.setLeashData(null);
            }
        }
    }

    default void dropLeash() {
        dropLeash((Entity & Leashable)this, true, true);
    }

    default void removeLeash() {
        dropLeash((Entity & Leashable)this, true, false);
    }

    default void onLeashRemoved() {
    }

    private static <E extends Entity & Leashable> void dropLeash(E entity, boolean broadcastPacket, boolean dropItem) {
        Leashable.LeashData leashData = entity.getLeashData();
        if (leashData != null && leashData.leashHolder != null) {
            entity.setLeashData(null);
            entity.onLeashRemoved();
            if (entity.level() instanceof ServerLevel serverLevel) {
                if (dropItem) {
                    entity.forceDrops = true; // CraftBukkit
                    entity.spawnAtLocation(serverLevel, Items.LEAD);
                    entity.forceDrops = false; // CraftBukkit
                }

                if (broadcastPacket) {
                    serverLevel.getChunkSource().broadcast(entity, new ClientboundSetEntityLinkPacket(entity, null));
                }
            }
        }
    }

    static <E extends Entity & Leashable> void tickLeash(ServerLevel level, E entity) {
        Leashable.LeashData leashData = entity.getLeashData();
        if (leashData != null && leashData.delayedLeashInfo != null) {
            restoreLeashFromSave(entity, leashData);
        }

        if (leashData != null && leashData.leashHolder != null) {
            if (!entity.isAlive() || !leashData.leashHolder.isAlive()) {
                // Paper start - Expand EntityUnleashEvent
                final org.bukkit.event.entity.EntityUnleashEvent event = new org.bukkit.event.entity.EntityUnleashEvent(
                    entity.getBukkitEntity(),
                    !entity.isAlive() ? org.bukkit.event.entity.EntityUnleashEvent.UnleashReason.PLAYER_UNLEASH : org.bukkit.event.entity.EntityUnleashEvent.UnleashReason.HOLDER_GONE,
                    level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS) && !entity.pluginRemoved
                );
                event.callEvent();
                if (event.isDropLeash()) { // CraftBukkit - SPIGOT-7487: Don't drop leash, when the holder was removed by a plugin
                    // Paper end - Expand EntityUnleashEvent
                    entity.dropLeash();
                } else {
                    entity.removeLeash();
                }
            }

            Entity leashHolder = entity.getLeashHolder();
            if (leashHolder != null && leashHolder.level() == entity.level()) {
                float f = entity.distanceTo(leashHolder);
                if (!entity.handleLeashAtDistance(leashHolder, f)) {
                    return;
                }

                if (f > entity.level().paperConfig().misc.maxLeashDistance.or(LEASH_TOO_FAR_DIST)) { // Paper - Configurable max leash distance
                    entity.leashTooFarBehaviour();
                } else if (f > 6.0) {
                    entity.elasticRangeLeashBehaviour(leashHolder, f);
                    entity.checkSlowFallDistance();
                } else {
                    entity.closeRangeLeashBehaviour(leashHolder);
                }
            }
        }
    }

    default boolean handleLeashAtDistance(Entity leashHolder, float distance) {
        return true;
    }

    default void leashTooFarBehaviour() {
        // CraftBukkit start
        boolean dropLeash = true; // Paper
        if (this instanceof Entity entity) {
            // Paper start - Expand EntityUnleashEvent
            final org.bukkit.event.entity.EntityUnleashEvent event = new org.bukkit.event.entity.EntityUnleashEvent(entity.getBukkitEntity(), org.bukkit.event.entity.EntityUnleashEvent.UnleashReason.DISTANCE, true);
            if (!event.callEvent()) return;
            dropLeash = event.isDropLeash();
        }
        // CraftBukkit end
        if (dropLeash) {
            this.dropLeash();
        } else {
            this.removeLeash();
        }
        // Paper end - Expand EntityUnleashEvent
    }

    default void closeRangeLeashBehaviour(Entity entity) {
    }

    default void elasticRangeLeashBehaviour(Entity leashHolder, float distance) {
        legacyElasticRangeLeashBehaviour((Entity & Leashable)this, leashHolder, distance);
    }

    private static <E extends Entity & Leashable> void legacyElasticRangeLeashBehaviour(E entity, Entity leashHolder, float distance) {
        double d = (leashHolder.getX() - entity.getX()) / distance;
        double d1 = (leashHolder.getY() - entity.getY()) / distance;
        double d2 = (leashHolder.getZ() - entity.getZ()) / distance;
        entity.setDeltaMovement(
            entity.getDeltaMovement().add(Math.copySign(d * d * 0.4, d), Math.copySign(d1 * d1 * 0.4, d1), Math.copySign(d2 * d2 * 0.4, d2))
        );
    }

    default void setLeashedTo(Entity leashHolder, boolean broadcastPacket) {
        setLeashedTo((Entity & Leashable)this, leashHolder, broadcastPacket);
    }

    private static <E extends Entity & Leashable> void setLeashedTo(E entity, Entity leashHolder, boolean broadcastPacket) {
        Leashable.LeashData leashData = entity.getLeashData();
        if (leashData == null) {
            leashData = new Leashable.LeashData(leashHolder);
            entity.setLeashData(leashData);
        } else {
            leashData.setLeashHolder(leashHolder);
        }

        if (broadcastPacket && entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().broadcast(entity, new ClientboundSetEntityLinkPacket(entity, leashHolder));
        }

        if (entity.isPassenger()) {
            entity.stopRiding();
        }
    }

    @Nullable
    default Entity getLeashHolder() {
        return getLeashHolder((Entity & Leashable)this);
    }

    @Nullable
    private static <E extends Entity & Leashable> Entity getLeashHolder(E entity) {
        Leashable.LeashData leashData = entity.getLeashData();
        if (leashData == null) {
            return null;
        } else {
            if (leashData.delayedLeashHolderId != 0 && entity.level().isClientSide) {
                Entity var3 = entity.level().getEntity(leashData.delayedLeashHolderId);
                if (var3 instanceof Entity) {
                    leashData.setLeashHolder(var3);
                }
            }

            return leashData.leashHolder;
        }
    }

    public static final class LeashData {
        int delayedLeashHolderId;
        @Nullable
        public Entity leashHolder;
        @Nullable
        public Either<UUID, BlockPos> delayedLeashInfo;

        LeashData(Either<UUID, BlockPos> delayedLeashInfo) {
            this.delayedLeashInfo = delayedLeashInfo;
        }

        LeashData(Entity leashHolder) {
            this.leashHolder = leashHolder;
        }

        LeashData(int delayedLeashInfoId) {
            this.delayedLeashHolderId = delayedLeashInfoId;
        }

        public void setLeashHolder(Entity leashHolder) {
            this.leashHolder = leashHolder;
            this.delayedLeashInfo = null;
            this.delayedLeashHolderId = 0;
        }
    }
}
