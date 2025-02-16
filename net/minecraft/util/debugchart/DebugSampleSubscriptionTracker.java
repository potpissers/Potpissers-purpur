package net.minecraft.util.debugchart;

import com.google.common.collect.Maps;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import net.minecraft.Util;
import net.minecraft.network.protocol.game.ClientboundDebugSamplePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

public class DebugSampleSubscriptionTracker {
    public static final int STOP_SENDING_AFTER_TICKS = 200;
    public static final int STOP_SENDING_AFTER_MS = 10000;
    private final PlayerList playerList;
    private final EnumMap<RemoteDebugSampleType, Map<ServerPlayer, DebugSampleSubscriptionTracker.SubscriptionStartedAt>> subscriptions;
    private final Queue<DebugSampleSubscriptionTracker.SubscriptionRequest> subscriptionRequestQueue = new LinkedList<>();

    public DebugSampleSubscriptionTracker(PlayerList playerList) {
        this.playerList = playerList;
        this.subscriptions = new EnumMap<>(RemoteDebugSampleType.class);

        for (RemoteDebugSampleType remoteDebugSampleType : RemoteDebugSampleType.values()) {
            this.subscriptions.put(remoteDebugSampleType, Maps.newHashMap());
        }
    }

    public boolean shouldLogSamples(RemoteDebugSampleType sampleType) {
        return !this.subscriptions.get(sampleType).isEmpty();
    }

    public void broadcast(ClientboundDebugSamplePacket packet) {
        for (ServerPlayer serverPlayer : this.subscriptions.get(packet.debugSampleType()).keySet()) {
            serverPlayer.connection.send(packet);
        }
    }

    public void subscribe(ServerPlayer player, RemoteDebugSampleType sampleType) {
        if (this.playerList.isOp(player.getGameProfile())) {
            this.subscriptionRequestQueue.add(new DebugSampleSubscriptionTracker.SubscriptionRequest(player, sampleType));
        }
    }

    public void tick(int tick) {
        long millis = Util.getMillis();
        this.handleSubscriptions(millis, tick);
        this.handleUnsubscriptions(millis, tick);
    }

    private void handleSubscriptions(long millis, int tick) {
        for (DebugSampleSubscriptionTracker.SubscriptionRequest subscriptionRequest : this.subscriptionRequestQueue) {
            this.subscriptions
                .get(subscriptionRequest.sampleType())
                .put(subscriptionRequest.player(), new DebugSampleSubscriptionTracker.SubscriptionStartedAt(millis, tick));
        }
    }

    private void handleUnsubscriptions(long millis, int tick) {
        for (Map<ServerPlayer, DebugSampleSubscriptionTracker.SubscriptionStartedAt> map : this.subscriptions.values()) {
            map.entrySet().removeIf(entry -> {
                boolean flag = !this.playerList.isOp(entry.getKey().getGameProfile());
                DebugSampleSubscriptionTracker.SubscriptionStartedAt subscriptionStartedAt = entry.getValue();
                return flag || tick > subscriptionStartedAt.tick() + 200 && millis > subscriptionStartedAt.millis() + 10000L;
            });
        }
    }

    record SubscriptionRequest(ServerPlayer player, RemoteDebugSampleType sampleType) {
    }

    record SubscriptionStartedAt(long millis, int tick) {
    }
}
