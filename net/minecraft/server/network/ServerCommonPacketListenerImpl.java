package net.minecraft.server.network;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import org.slf4j.Logger;

public abstract class ServerCommonPacketListenerImpl implements ServerCommonPacketListener, org.bukkit.craftbukkit.entity.CraftPlayer.TransferCookieConnection { // CraftBukkit
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int LATENCY_CHECK_INTERVAL = 15000;
    private static final int CLOSED_LISTENER_TIMEOUT = 15000;
    private static final Component TIMEOUT_DISCONNECTION_MESSAGE = Component.translatable("disconnect.timeout");
    static final Component DISCONNECT_UNEXPECTED_QUERY = Component.translatable("multiplayer.disconnect.unexpected_query_response");
    protected final MinecraftServer server;
    public final Connection connection; // Paper
    private final boolean transferred;
    private long keepAliveTime = Util.getMillis(); // Paper
    private boolean keepAlivePending;
    private long keepAliveChallenge;
    private long closedListenerTime;
    private boolean closed = false;
    private it.unimi.dsi.fastutil.longs.LongList keepAlives = new it.unimi.dsi.fastutil.longs.LongArrayList(); // Purpur - Alternative Keepalive Handling
    private int latency;
    private volatile boolean suspendFlushingOnServerThread = false;
    // CraftBukkit start
    protected final net.minecraft.server.level.ServerPlayer player;
    protected final org.bukkit.craftbukkit.CraftServer cserver;
    public boolean processedDisconnect;
    // CraftBukkit end
    public final java.util.Map<java.util.UUID, net.kyori.adventure.resource.ResourcePackCallback> packCallbacks = new java.util.concurrent.ConcurrentHashMap<>(); // Paper - adventure resource pack callbacks
    private static final long KEEPALIVE_LIMIT = Long.getLong("paper.playerconnection.keepalive", 30) * 1000; // Paper - provide property to set keepalive limit
    protected static final net.minecraft.resources.ResourceLocation MINECRAFT_BRAND = net.minecraft.resources.ResourceLocation.withDefaultNamespace("brand"); // Paper - Brand support
    protected static final net.minecraft.resources.ResourceLocation PURPUR_CLIENT = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("purpur", "client"); // Purpur - Purpur client support

    public ServerCommonPacketListenerImpl(MinecraftServer server, Connection connection, CommonListenerCookie cookie, net.minecraft.server.level.ServerPlayer player) { // CraftBukkit
        this.server = server;
        this.connection = connection;
        this.keepAliveTime = Util.getMillis();
        this.latency = cookie.latency();
        this.transferred = cookie.transferred();
        // CraftBukkit start - add fields and methods
        this.player = player;
        this.player.transferCookieConnection = this;
        this.cserver = server.server;
    }

    public org.bukkit.craftbukkit.entity.CraftPlayer getCraftPlayer() {
        return this.player == null ? null : this.player.getBukkitEntity();
    }

    @Override
    public boolean isTransferred() {
        return this.transferred;
    }

    @Override
    public net.minecraft.network.ConnectionProtocol getProtocol() {
        return this.protocol();
    }

    @Override
    public void sendPacket(Packet<?> packet) {
        this.send(packet);
    }

    @Override
    public void kickPlayer(Component reason, org.bukkit.event.player.PlayerKickEvent.Cause cause) { // Paper - kick event causes
        this.disconnect(reason, cause); // Paper - kick event causes
    }
    // CraftBukkit end

    private void close() {
        if (!this.closed) {
            this.closedListenerTime = Util.getMillis();
            this.closed = true;
        }
    }

    @Override
    public void onDisconnect(DisconnectionDetails details) {
        // Paper start - Fix kick event leave message not being sent
        this.onDisconnect(details, null);
    }

    public void onDisconnect(DisconnectionDetails info, @Nullable net.kyori.adventure.text.Component quitMessage) {
        // Paper end - Fix kick event leave message not being sent
        if (this.isSingleplayerOwner()) {
            LOGGER.info("Stopping singleplayer server as player logged out");
            this.server.halt(false);
        }
    }

    @Override
    public void onPacketError(Packet packet, Exception exception) throws ReportedException {
        ServerCommonPacketListener.super.onPacketError(packet, exception);
        this.server.reportPacketHandlingException(exception, packet.type());
    }

    @Override
    public void handleKeepAlive(ServerboundKeepAlivePacket packet) {
        // Purpur start - Alternative Keepalive Handling
        if (org.purpurmc.purpur.PurpurConfig.useAlternateKeepAlive) {
            if (this.keepAlivePending && !keepAlives.isEmpty() && keepAlives.contains(packet.getId())) {
                int ping = (int) (Util.getMillis() - packet.getId());
                this.latency = (this.latency * 3 + ping) / 4;
                this.keepAlivePending = false;
                keepAlives.clear(); // we got a valid response, lets roll with it and forget the rest
            }
        } else
        // Purpur end - Alternative Keepalive Handling
        if (this.keepAlivePending && packet.getId() == this.keepAliveChallenge) {
            int i = (int)(Util.getMillis() - this.keepAliveTime);
            this.latency = (this.latency * 3 + i) / 4;
            this.keepAlivePending = false;
        } else if (!this.isSingleplayerOwner()) {
            this.disconnectAsync(TIMEOUT_DISCONNECTION_MESSAGE, org.bukkit.event.player.PlayerKickEvent.Cause.TIMEOUT); // Paper - add proper async disconnect
        }
    }

    @Override
    public void handlePong(ServerboundPongPacket packet) {
    }

    private static final net.minecraft.resources.ResourceLocation CUSTOM_REGISTER = net.minecraft.resources.ResourceLocation.withDefaultNamespace("register"); // CraftBukkit
    private static final net.minecraft.resources.ResourceLocation CUSTOM_UNREGISTER = net.minecraft.resources.ResourceLocation.withDefaultNamespace("unregister"); // CraftBukkit

    @Override
    public void handleCustomPayload(ServerboundCustomPayloadPacket packet) {
        // CraftBukkit start
        // Paper start - Brand support
        if (packet.payload() instanceof net.minecraft.network.protocol.common.custom.BrandPayload(String brand)) {
            this.player.clientBrandName = brand;
        }
        // Paper end - Brand support
        if (!(packet.payload() instanceof final net.minecraft.network.protocol.common.custom.DiscardedPayload discardedPayload)) {
            return;
        }
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        net.minecraft.resources.ResourceLocation identifier = packet.payload().type().id();
        io.netty.buffer.ByteBuf payload = discardedPayload.data();

        if (identifier.equals(ServerCommonPacketListenerImpl.CUSTOM_REGISTER)) {
            try {
                String channels = payload.toString(com.google.common.base.Charsets.UTF_8);
                for (String channel : channels.split("\0")) {
                    this.getCraftPlayer().addChannel(channel);
                }
            } catch (Exception ex) {
                ServerGamePacketListenerImpl.LOGGER.error("Couldn't register custom payload", ex);
                this.disconnect(Component.literal("Invalid payload REGISTER!"), org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_PAYLOAD); // Paper - kick event cause
            }
        // Purpur start - Purpur client support
        } else if (identifier.equals(PURPUR_CLIENT)) {
            try {
                player.purpurClient = true;
            } catch (Exception ignore) {
            }
        // Purpur end - Purpur client support
        } else if (identifier.equals(ServerCommonPacketListenerImpl.CUSTOM_UNREGISTER)) {
            try {
                String channels = payload.toString(com.google.common.base.Charsets.UTF_8);
                for (String channel : channels.split("\0")) {
                    this.getCraftPlayer().removeChannel(channel);
                }
            } catch (Exception ex) {
                ServerGamePacketListenerImpl.LOGGER.error("Couldn't unregister custom payload", ex);
                this.disconnect(Component.literal("Invalid payload UNREGISTER!"), org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_PAYLOAD); // Paper - kick event cause
            }
        } else {
            try {
                byte[] data = new byte[payload.readableBytes()];
                payload.readBytes(data);
                // Paper start - Brand support; Retain this incase upstream decides to 'break' the new mechanism in favour of backwards compat...
                if (identifier.equals(MINECRAFT_BRAND)) {
                    try {
                        this.player.clientBrandName = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.copiedBuffer(data)).readUtf(256);
                    } catch (StringIndexOutOfBoundsException ex) {
                        this.player.clientBrandName = "illegal";
                    }
                }
                // Paper end - Brand support
                this.cserver.getMessenger().dispatchIncomingMessage(this.player.getBukkitEntity(), identifier.toString(), data);
            } catch (Exception ex) {
                ServerGamePacketListenerImpl.LOGGER.error("Couldn't dispatch custom payload", ex);
                this.disconnect(Component.literal("Invalid custom payload!"), org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_PAYLOAD); // Paper - kick event cause
            }
        }
    }

    public final boolean isDisconnected() {
        return (!this.player.joining && !this.connection.isConnected()) || this.processedDisconnect; // Paper - Fix duplication bugs
    }
    // CraftBukkit end

    @Override
    public void handleResourcePackResponse(ServerboundResourcePackPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.server);
        if (packet.action() == ServerboundResourcePackPacket.Action.DECLINED && this.server.isResourcePackRequired()) {
            LOGGER.info("Disconnecting {} due to resource pack {} rejection", this.playerProfile().getName(), packet.id());
            this.disconnect(Component.translatable("multiplayer.requiredTexturePrompt.disconnect"), org.bukkit.event.player.PlayerKickEvent.Cause.RESOURCE_PACK_REJECTION); // Paper - kick event cause
        }
        // Paper start - adventure pack callbacks
        // call the callbacks before the previously-existing event so the event has final say
        final net.kyori.adventure.resource.ResourcePackCallback callback;
        if (packet.action().isTerminal()) {
            callback = this.packCallbacks.remove(packet.id());
        } else {
            callback = this.packCallbacks.get(packet.id());
        }
        if (callback != null) {
            callback.packEventReceived(packet.id(), net.kyori.adventure.resource.ResourcePackStatus.valueOf(packet.action().name()), this.getCraftPlayer());
        }
        // Paper end
        // Paper start - store last pack status
        org.bukkit.event.player.PlayerResourcePackStatusEvent.Status packStatus = org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.values()[packet.action().ordinal()];
        this.player.getBukkitEntity().resourcePackStatus = packStatus;
        this.cserver.getPluginManager().callEvent(new org.bukkit.event.player.PlayerResourcePackStatusEvent(this.getCraftPlayer(), packet.id(), packStatus)); // CraftBukkit
        // Paper end - store last pack status
    }

    @Override
    public void handleCookieResponse(ServerboundCookieResponsePacket packet) {
        // CraftBukkit start
        PacketUtils.ensureRunningOnSameThread(packet, this, this.server);
        if (this.player.getBukkitEntity().handleCookieResponse(packet)) {
            return;
        }
        // CraftBukkit end
        this.disconnect(DISCONNECT_UNEXPECTED_QUERY, org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_COOKIE); // Paper - kick event cause
    }

    protected void keepConnectionAlive() {
        Profiler.get().push("keepAlive");
        long millis = Util.getMillis();
        // Paper start - give clients a longer time to respond to pings as per pre 1.12.2 timings
        // This should effectively place the keepalive handling back to "as it was" before 1.12.2
        final long elapsedTime = millis - this.keepAliveTime;

        // Purpur start - Alternative Keepalive Handling
        if (org.purpurmc.purpur.PurpurConfig.useAlternateKeepAlive) {
            if (elapsedTime >= 1000L) { // 1 second
                if (this.keepAlivePending && !this.processedDisconnect && keepAlives.size() * 1000L >= KEEPALIVE_LIMIT) {
                    this.disconnect(ServerCommonPacketListenerImpl.TIMEOUT_DISCONNECTION_MESSAGE, org.bukkit.event.player.PlayerKickEvent.Cause.TIMEOUT);
                } else if (this.checkIfClosed(millis)) {
                    this.keepAlivePending = true;
                    this.keepAliveTime = millis; // hijack this field for 1 second intervals
                    this.keepAlives.add(millis); // currentTime is ID
                    this.send(new ClientboundKeepAlivePacket(millis));
                }
            }
        } else
        // Purpur end - Alternative Keepalive Handling

        if (!this.isSingleplayerOwner() && elapsedTime >= 15000L) { // use vanilla's 15000L between keep alive packets
            if (this.keepAlivePending) {
                if (!this.processedDisconnect && elapsedTime >= KEEPALIVE_LIMIT) { // check keepalive limit, don't fire if already disconnected
                    this.disconnect(TIMEOUT_DISCONNECTION_MESSAGE, org.bukkit.event.player.PlayerKickEvent.Cause.TIMEOUT); // Paper - kick event cause
                }
                // Paper end - give clients a longer time to respond to pings as per pre 1.12.2 timings
            } else if (this.checkIfClosed(millis)) {
                this.keepAlivePending = true;
                this.keepAliveTime = millis;
                this.keepAliveChallenge = millis;
                this.send(new ClientboundKeepAlivePacket(this.keepAliveChallenge));
            }
        }

        Profiler.get().pop();
    }

    private boolean checkIfClosed(long time) {
        if (this.closed) {
            if (time - this.closedListenerTime >= 15000L) {
                this.disconnect(TIMEOUT_DISCONNECTION_MESSAGE, org.bukkit.event.player.PlayerKickEvent.Cause.TIMEOUT); // Paper - kick event cause
            }

            return false;
        } else {
            return true;
        }
    }

    public void suspendFlushing() {
        this.suspendFlushingOnServerThread = true;
    }

    public void resumeFlushing() {
        this.suspendFlushingOnServerThread = false;
        this.connection.flushChannel();
    }

    public void send(Packet<?> packet) {
        this.send(packet, null);
    }

    public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
        // CraftBukkit start
        if (packet == null || this.processedDisconnect) { // Spigot
            return;
        } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket defaultSpawnPositionPacket) {
            this.player.compassTarget = org.bukkit.craftbukkit.util.CraftLocation.toBukkit(defaultSpawnPositionPacket.getPos(), this.getCraftPlayer().getWorld());
        }
        // CraftBukkit end
        if (packet.isTerminal()) {
            this.close();
        }

        boolean flag = !this.suspendFlushingOnServerThread || !this.server.isSameThread();

        try {
            this.connection.send(packet, listener, flag);
        } catch (Throwable var7) {
            CrashReport crashReport = CrashReport.forThrowable(var7, "Sending packet");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Packet being sent");
            crashReportCategory.setDetail("Packet class", () -> packet.getClass().getCanonicalName());
            throw new ReportedException(crashReport);
        }
    }

    // Paper start - adventure
    public void disconnect(final net.kyori.adventure.text.Component reason) {
        this.disconnect(reason, org.bukkit.event.player.PlayerKickEvent.Cause.UNKNOWN);
    }

    public void disconnect(final net.kyori.adventure.text.Component reason, org.bukkit.event.player.PlayerKickEvent.Cause cause) {
        this.disconnect(io.papermc.paper.adventure.PaperAdventure.asVanilla(reason), cause);
    }
    // Paper end - adventure

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper - kick event causes
    public void disconnect(Component reason) {
        // Paper start - kick event causes
        this.disconnect(reason, org.bukkit.event.player.PlayerKickEvent.Cause.UNKNOWN);
    }

    public void disconnect(final Component reason, org.bukkit.event.player.PlayerKickEvent.Cause cause) {
        this.disconnect(new DisconnectionDetails(reason), cause);
        // Paper end - kick event causes
    }

    public void disconnect(DisconnectionDetails disconnectionDetails, org.bukkit.event.player.PlayerKickEvent.Cause cause) { // Paper - kick event cause
        // CraftBukkit start - fire PlayerKickEvent
        if (this.processedDisconnect) {
            return;
        }
        if (!this.cserver.isPrimaryThread()) {
            org.bukkit.craftbukkit.util.Waitable waitable = new org.bukkit.craftbukkit.util.Waitable() {
                @Override
                protected Object evaluate() {
                    ServerCommonPacketListenerImpl.this.disconnect(disconnectionDetails, cause); // Paper - kick event causes
                    return null;
                }
            };

            this.server.processQueue.add(waitable);

            try {
                waitable.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.ExecutionException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        net.kyori.adventure.text.Component leaveMessage = net.kyori.adventure.text.Component.translatable("multiplayer.player.left", net.kyori.adventure.text.format.NamedTextColor.YELLOW, io.papermc.paper.configuration.GlobalConfiguration.get().messages.useDisplayNameInQuitMessage ? this.player.getBukkitEntity().displayName() : net.kyori.adventure.text.Component.text(this.player.getScoreboardName())); // Paper - Adventure

        org.bukkit.event.player.PlayerKickEvent event = new org.bukkit.event.player.PlayerKickEvent(this.player.getBukkitEntity(), io.papermc.paper.adventure.PaperAdventure.asAdventure(disconnectionDetails.reason()), leaveMessage, cause); // Paper - adventure & kick event causes

        if (this.cserver.getServer().isRunning()) {
            this.cserver.getPluginManager().callEvent(event);
        }

        if (event.isCancelled()) {
            // Do not kick the player
            return;
        }
        // Send the possibly modified leave message
        this.disconnect0(new DisconnectionDetails(io.papermc.paper.adventure.PaperAdventure.asVanilla(event.reason()), disconnectionDetails.report(), disconnectionDetails.bugReportLink()), event.leaveMessage()); // Paper - Adventure & use kick event leave message
    }

    private void disconnect0(DisconnectionDetails disconnectionDetails, @Nullable net.kyori.adventure.text.Component leaveMessage) { // Paper - use kick event leave message
        // CraftBukkit end
        this.player.quitReason = org.bukkit.event.player.PlayerQuitEvent.QuitReason.KICKED; // Paper - Add API for quit reason
        this.connection
            .send(
                new ClientboundDisconnectPacket(disconnectionDetails.reason()),
                PacketSendListener.thenRun(() -> this.connection.disconnect(disconnectionDetails))
            );
        this.onDisconnect(disconnectionDetails, leaveMessage); // CraftBukkit - fire quit instantly // Paper - use kick event leave message
        this.connection.setReadOnly();
        // CraftBukkit - Don't wait
        this.server.scheduleOnMain(this.connection::handleDisconnection); // Paper
    }

    // Paper start - add proper async disconnect
    public void disconnectAsync(net.kyori.adventure.text.Component reason, org.bukkit.event.player.PlayerKickEvent.Cause cause) {
        this.disconnectAsync(io.papermc.paper.adventure.PaperAdventure.asVanilla(reason), cause);
    }

    public void disconnectAsync(Component reason, org.bukkit.event.player.PlayerKickEvent.Cause cause) {
        this.disconnectAsync(new DisconnectionDetails(reason), cause);
    }

    public void disconnectAsync(DisconnectionDetails disconnectionInfo, org.bukkit.event.player.PlayerKickEvent.Cause cause) {
        if (this.cserver.isPrimaryThread()) {
            this.disconnect(disconnectionInfo, cause);
            return;
        }

        this.connection.setReadOnly();
        this.server.scheduleOnMain(() -> {
            ServerCommonPacketListenerImpl.this.disconnect(disconnectionInfo, cause);
            if (ServerCommonPacketListenerImpl.this.player.quitReason == null) {
                // cancelled
                ServerCommonPacketListenerImpl.this.connection.enableAutoRead();
            }
        });
    }
    // Paper end - add proper async disconnect

    protected boolean isSingleplayerOwner() {
        return this.server.isSingleplayerOwner(this.playerProfile());
    }

    protected abstract GameProfile playerProfile();

    @VisibleForDebug
    public GameProfile getOwner() {
        return this.playerProfile();
    }

    public int latency() {
        return this.latency;
    }

    protected CommonListenerCookie createCookie(ClientInformation clientInformation) {
        return new CommonListenerCookie(this.playerProfile(), this.latency, clientInformation, this.transferred);
    }
}
