package net.minecraft.world.level.portal;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

public record TeleportTransition(
    ServerLevel newLevel,
    Vec3 position,
    Vec3 deltaMovement,
    float yRot,
    float xRot,
    boolean missingRespawnBlock,
    boolean asPassenger,
    Set<Relative> relatives,
    TeleportTransition.PostTeleportTransition postTeleportTransition
    , org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause // CraftBukkit
) {
    public static final TeleportTransition.PostTeleportTransition DO_NOTHING = entity -> {};
    public static final TeleportTransition.PostTeleportTransition PLAY_PORTAL_SOUND = TeleportTransition::playPortalSound;
    public static final TeleportTransition.PostTeleportTransition PLACE_PORTAL_TICKET = TeleportTransition::placePortalTicket;

    // CraftBukkit start
    public TeleportTransition(ServerLevel newLevel, Vec3 position, Vec3 deltaMovement, float yRot, float xRot, boolean missingRespawnBlock, boolean asPassenger, Set<Relative> relatives, TeleportTransition.PostTeleportTransition postTeleportTransition) {
        this(newLevel, position, deltaMovement, yRot, xRot, missingRespawnBlock, asPassenger, relatives, postTeleportTransition, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }

    public TeleportTransition(org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        this(null, Vec3.ZERO, Vec3.ZERO, 0.0F, 0.0F, false, false, Set.of(), DO_NOTHING, cause);
    }
    // CraftBukkit end

    public TeleportTransition(
        ServerLevel newLevel, Vec3 position, Vec3 deltaMovement, float yRot, float xRot, TeleportTransition.PostTeleportTransition postTeleportTransition
    ) {
        // CraftBukkit start
        this(newLevel, position, deltaMovement, yRot, xRot, postTeleportTransition, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }

    public TeleportTransition(
        ServerLevel newLevel, Vec3 position, Vec3 deltaMovement, float yRot, float xRot, TeleportTransition.PostTeleportTransition postTeleportTransition, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause
    ) {
        this(newLevel, position, deltaMovement, yRot, xRot, Set.of(), postTeleportTransition, cause);
        // CraftBukkit end
    }

    public TeleportTransition(
        ServerLevel newLevel,
        Vec3 position,
        Vec3 deltaMovement,
        float yRot,
        float xRot,
        Set<Relative> relatives,
        TeleportTransition.PostTeleportTransition postTeleportTransition
    ) {
        // CraftBukkit start
        this(newLevel, position, deltaMovement, yRot, xRot, relatives, postTeleportTransition, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }
    public TeleportTransition(
        ServerLevel newLevel,
        Vec3 position,
        Vec3 deltaMovement,
        float yRot,
        float xRot,
        Set<Relative> relatives,
        TeleportTransition.PostTeleportTransition postTeleportTransition,
        org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause
    ) {
        this(newLevel, position, deltaMovement, yRot, xRot, false, false, relatives, postTeleportTransition, cause);
        // CraftBukkit end
    }

    public TeleportTransition(ServerLevel level, Entity entity, TeleportTransition.PostTeleportTransition postTeleportTransition) {
        // CraftBukkit start
        this(level, entity, postTeleportTransition, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }
    public TeleportTransition(ServerLevel level, Entity entity, TeleportTransition.PostTeleportTransition postTeleportTransition, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        this(level, findAdjustedSharedSpawnPos(level, entity), Vec3.ZERO, level.getSharedSpawnAngle(), 0.0F, false, false, Set.of(), postTeleportTransition, cause); // Paper - MC-200092 - fix first spawn pos yaw being ignored
        // CraftBukkit end
    }

    private static void playPortalSound(Entity entity) {
        if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundLevelEventPacket(1032, BlockPos.ZERO, 0, false));
        }
    }

    private static void placePortalTicket(Entity entity) {
        entity.placePortalTicket(BlockPos.containing(entity.position()));
    }

    public static TeleportTransition missingRespawnBlock(ServerLevel level, Entity entity, TeleportTransition.PostTeleportTransition postTeleportTransition) {
        return new TeleportTransition(level, findAdjustedSharedSpawnPos(level, entity), Vec3.ZERO, level.getSharedSpawnAngle(), 0.0F, true, false, Set.of(), postTeleportTransition); // Paper - MC-200092 - fix spawn pos yaw being ignored
    }

    private static Vec3 findAdjustedSharedSpawnPos(ServerLevel level, Entity entity) {
        return entity.adjustSpawnLocation(level, level.getSharedSpawnPos()).getBottomCenter();
    }

    public TeleportTransition withRotation(float yRot, float xRot) {
        return new TeleportTransition(
            this.newLevel(),
            this.position(),
            this.deltaMovement(),
            yRot,
            xRot,
            this.missingRespawnBlock(),
            this.asPassenger(),
            this.relatives(),
            this.postTeleportTransition()
        );
    }

    public TeleportTransition withPosition(Vec3 position) {
        return new TeleportTransition(
            this.newLevel(),
            position,
            this.deltaMovement(),
            this.yRot(),
            this.xRot(),
            this.missingRespawnBlock(),
            this.asPassenger(),
            this.relatives(),
            this.postTeleportTransition()
        );
    }

    public TeleportTransition transitionAsPassenger() {
        return new TeleportTransition(
            this.newLevel(),
            this.position(),
            this.deltaMovement(),
            this.yRot(),
            this.xRot(),
            this.missingRespawnBlock(),
            true,
            this.relatives(),
            this.postTeleportTransition()
        );
    }

    @FunctionalInterface
    public interface PostTeleportTransition {
        void onTransition(Entity entity);

        default TeleportTransition.PostTeleportTransition then(TeleportTransition.PostTeleportTransition postTeleportTransition) {
            return entity -> {
                this.onTransition(entity);
                postTeleportTransition.onTransition(entity);
            };
        }
    }
}
