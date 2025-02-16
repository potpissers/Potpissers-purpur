package net.minecraft.server.dedicated;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.DefaultUncaughtExceptionHandlerWithName;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.ConsoleInput;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerInterface;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.gui.MinecraftServerGui;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.network.ServerTextFilter;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.server.rcon.RconConsoleSource;
import net.minecraft.server.rcon.thread.QueryThreadGs4;
import net.minecraft.server.rcon.thread.RconThread;
import net.minecraft.util.Mth;
import net.minecraft.util.debugchart.DebugSampleSubscriptionTracker;
import net.minecraft.util.debugchart.RemoteDebugSampleType;
import net.minecraft.util.debugchart.RemoteSampleLogger;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.util.debugchart.TpsDebugDimensions;
import net.minecraft.util.monitoring.jmx.MinecraftServerStatistics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;

public class DedicatedServer extends MinecraftServer implements ServerInterface {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int CONVERSION_RETRY_DELAY_MS = 5000;
    private static final int CONVERSION_RETRIES = 2;
    private final List<ConsoleInput> consoleInput = Collections.synchronizedList(Lists.newArrayList());
    @Nullable
    private QueryThreadGs4 queryThreadGs4;
    private final RconConsoleSource rconConsoleSource;
    @Nullable
    private RconThread rconThread;
    private final DedicatedServerSettings settings;
    @Nullable
    private MinecraftServerGui gui;
    @Nullable
    private final ServerTextFilter serverTextFilter;
    @Nullable
    private RemoteSampleLogger tickTimeLogger;
    @Nullable
    private DebugSampleSubscriptionTracker debugSampleSubscriptionTracker;
    private final ServerLinks serverLinks;

    public DedicatedServer(
        Thread serverThread,
        LevelStorageSource.LevelStorageAccess storageSource,
        PackRepository packRepository,
        WorldStem worldStem,
        DedicatedServerSettings settings,
        DataFixer fixerUpper,
        Services services,
        ChunkProgressListenerFactory progressListenerFactory
    ) {
        super(serverThread, storageSource, packRepository, worldStem, Proxy.NO_PROXY, fixerUpper, services, progressListenerFactory);
        this.settings = settings;
        this.rconConsoleSource = new RconConsoleSource(this);
        this.serverTextFilter = ServerTextFilter.createFromConfig(settings.getProperties());
        this.serverLinks = createServerLinks(settings);
    }

    @Override
    public boolean initServer() throws IOException {
        Thread thread = new Thread("Server console handler") {
            @Override
            public void run() {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

                String string1;
                try {
                    while (!DedicatedServer.this.isStopped() && DedicatedServer.this.isRunning() && (string1 = bufferedReader.readLine()) != null) {
                        DedicatedServer.this.handleConsoleInput(string1, DedicatedServer.this.createCommandSourceStack());
                    }
                } catch (IOException var4) {
                    DedicatedServer.LOGGER.error("Exception handling console input", (Throwable)var4);
                }
            }
        };
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
        thread.start();
        LOGGER.info("Starting minecraft server version {}", SharedConstants.getCurrentVersion().getName());
        if (Runtime.getRuntime().maxMemory() / 1024L / 1024L < 512L) {
            LOGGER.warn("To start the server with more ram, launch it as \"java -Xmx1024M -Xms1024M -jar minecraft_server.jar\"");
        }

        LOGGER.info("Loading properties");
        DedicatedServerProperties properties = this.settings.getProperties();
        if (this.isSingleplayer()) {
            this.setLocalIp("127.0.0.1");
        } else {
            this.setUsesAuthentication(properties.onlineMode);
            this.setPreventProxyConnections(properties.preventProxyConnections);
            this.setLocalIp(properties.serverIp);
        }

        this.setPvpAllowed(properties.pvp);
        this.setFlightAllowed(properties.allowFlight);
        this.setMotd(properties.motd);
        super.setPlayerIdleTimeout(properties.playerIdleTimeout.get());
        this.setEnforceWhitelist(properties.enforceWhitelist);
        this.worldData.setGameType(properties.gamemode);
        LOGGER.info("Default game type: {}", properties.gamemode);
        InetAddress inetAddress = null;
        if (!this.getLocalIp().isEmpty()) {
            inetAddress = InetAddress.getByName(this.getLocalIp());
        }

        if (this.getPort() < 0) {
            this.setPort(properties.serverPort);
        }

        this.initializeKeyPair();
        LOGGER.info("Starting Minecraft server on {}:{}", this.getLocalIp().isEmpty() ? "*" : this.getLocalIp(), this.getPort());

        try {
            this.getConnection().startTcpServerListener(inetAddress, this.getPort());
        } catch (IOException var10) {
            LOGGER.warn("**** FAILED TO BIND TO PORT!");
            LOGGER.warn("The exception was: {}", var10.toString());
            LOGGER.warn("Perhaps a server is already running on that port?");
            return false;
        }

        if (!this.usesAuthentication()) {
            LOGGER.warn("**** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!");
            LOGGER.warn("The server will make no attempt to authenticate usernames. Beware.");
            LOGGER.warn(
                "While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose."
            );
            LOGGER.warn("To change this, set \"online-mode\" to \"true\" in the server.properties file.");
        }

        if (this.convertOldUsers()) {
            this.getProfileCache().save();
        }

        if (!OldUsersConverter.serverReadyAfterUserconversion(this)) {
            return false;
        } else {
            this.setPlayerList(new DedicatedPlayerList(this, this.registries(), this.playerDataStorage));
            this.debugSampleSubscriptionTracker = new DebugSampleSubscriptionTracker(this.getPlayerList());
            this.tickTimeLogger = new RemoteSampleLogger(
                TpsDebugDimensions.values().length, this.debugSampleSubscriptionTracker, RemoteDebugSampleType.TICK_TIME
            );
            long nanos = Util.getNanos();
            SkullBlockEntity.setup(this.services, this);
            GameProfileCache.setUsesAuthentication(this.usesAuthentication());
            LOGGER.info("Preparing level \"{}\"", this.getLevelIdName());
            this.loadLevel();
            long l = Util.getNanos() - nanos;
            String string = String.format(Locale.ROOT, "%.3fs", l / 1.0E9);
            LOGGER.info("Done ({})! For help, type \"help\"", string);
            if (properties.announcePlayerAchievements != null) {
                this.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(properties.announcePlayerAchievements, this);
            }

            if (properties.enableQuery) {
                LOGGER.info("Starting GS4 status listener");
                this.queryThreadGs4 = QueryThreadGs4.create(this);
            }

            if (properties.enableRcon) {
                LOGGER.info("Starting remote control listener");
                this.rconThread = RconThread.create(this);
            }

            if (this.getMaxTickLength() > 0L) {
                Thread thread1 = new Thread(new ServerWatchdog(this));
                thread1.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandlerWithName(LOGGER));
                thread1.setName("Server Watchdog");
                thread1.setDaemon(true);
                thread1.start();
            }

            if (properties.enableJmxMonitoring) {
                MinecraftServerStatistics.registerJmxMonitoring(this);
                LOGGER.info("JMX monitoring enabled");
            }

            return true;
        }
    }

    @Override
    public boolean isSpawningMonsters() {
        return this.settings.getProperties().spawnMonsters && super.isSpawningMonsters();
    }

    @Override
    public DedicatedServerProperties getProperties() {
        return this.settings.getProperties();
    }

    @Override
    public void forceDifficulty() {
        this.setDifficulty(this.getProperties().difficulty, true);
    }

    @Override
    public SystemReport fillServerSystemReport(SystemReport report) {
        report.setDetail("Is Modded", () -> this.getModdedStatus().fullDescription());
        report.setDetail("Type", () -> "Dedicated Server (map_server.txt)");
        return report;
    }

    @Override
    public void dumpServerProperties(Path path) throws IOException {
        DedicatedServerProperties properties = this.getProperties();

        try (Writer bufferedWriter = Files.newBufferedWriter(path)) {
            bufferedWriter.write(String.format(Locale.ROOT, "sync-chunk-writes=%s%n", properties.syncChunkWrites));
            bufferedWriter.write(String.format(Locale.ROOT, "gamemode=%s%n", properties.gamemode));
            bufferedWriter.write(String.format(Locale.ROOT, "spawn-monsters=%s%n", properties.spawnMonsters));
            bufferedWriter.write(String.format(Locale.ROOT, "entity-broadcast-range-percentage=%d%n", properties.entityBroadcastRangePercentage));
            bufferedWriter.write(String.format(Locale.ROOT, "max-world-size=%d%n", properties.maxWorldSize));
            bufferedWriter.write(String.format(Locale.ROOT, "view-distance=%d%n", properties.viewDistance));
            bufferedWriter.write(String.format(Locale.ROOT, "simulation-distance=%d%n", properties.simulationDistance));
            bufferedWriter.write(String.format(Locale.ROOT, "generate-structures=%s%n", properties.worldOptions.generateStructures()));
            bufferedWriter.write(String.format(Locale.ROOT, "use-native=%s%n", properties.useNativeTransport));
            bufferedWriter.write(String.format(Locale.ROOT, "rate-limit=%d%n", properties.rateLimitPacketsPerSecond));
        }
    }

    @Override
    public void onServerExit() {
        if (this.serverTextFilter != null) {
            this.serverTextFilter.close();
        }

        if (this.gui != null) {
            this.gui.close();
        }

        if (this.rconThread != null) {
            this.rconThread.stop();
        }

        if (this.queryThreadGs4 != null) {
            this.queryThreadGs4.stop();
        }
    }

    @Override
    public void tickConnection() {
        super.tickConnection();
        this.handleConsoleInputs();
    }

    @Override
    public boolean isLevelEnabled(Level level) {
        return level.dimension() != Level.NETHER || this.getProperties().allowNether;
    }

    public void handleConsoleInput(String msg, CommandSourceStack source) {
        this.consoleInput.add(new ConsoleInput(msg, source));
    }

    public void handleConsoleInputs() {
        while (!this.consoleInput.isEmpty()) {
            ConsoleInput consoleInput = this.consoleInput.remove(0);
            this.getCommands().performPrefixedCommand(consoleInput.source, consoleInput.msg);
        }
    }

    @Override
    public boolean isDedicatedServer() {
        return true;
    }

    @Override
    public int getRateLimitPacketsPerSecond() {
        return this.getProperties().rateLimitPacketsPerSecond;
    }

    @Override
    public boolean isEpollEnabled() {
        return this.getProperties().useNativeTransport;
    }

    @Override
    public DedicatedPlayerList getPlayerList() {
        return (DedicatedPlayerList)super.getPlayerList();
    }

    @Override
    public boolean isPublished() {
        return true;
    }

    @Override
    public String getServerIp() {
        return this.getLocalIp();
    }

    @Override
    public int getServerPort() {
        return this.getPort();
    }

    @Override
    public String getServerName() {
        return this.getMotd();
    }

    public void showGui() {
        if (this.gui == null) {
            this.gui = MinecraftServerGui.showFrameFor(this);
        }
    }

    @Override
    public boolean hasGui() {
        return this.gui != null;
    }

    @Override
    public boolean isCommandBlockEnabled() {
        return this.getProperties().enableCommandBlock;
    }

    @Override
    public int getSpawnProtectionRadius() {
        return this.getProperties().spawnProtection;
    }

    @Override
    public boolean isUnderSpawnProtection(ServerLevel level, BlockPos pos, Player player) {
        if (level.dimension() != Level.OVERWORLD) {
            return false;
        } else if (this.getPlayerList().getOps().isEmpty()) {
            return false;
        } else if (this.getPlayerList().isOp(player.getGameProfile())) {
            return false;
        } else if (this.getSpawnProtectionRadius() <= 0) {
            return false;
        } else {
            BlockPos sharedSpawnPos = level.getSharedSpawnPos();
            int abs = Mth.abs(pos.getX() - sharedSpawnPos.getX());
            int abs1 = Mth.abs(pos.getZ() - sharedSpawnPos.getZ());
            int max = Math.max(abs, abs1);
            return max <= this.getSpawnProtectionRadius();
        }
    }

    @Override
    public boolean repliesToStatus() {
        return this.getProperties().enableStatus;
    }

    @Override
    public boolean hidesOnlinePlayers() {
        return this.getProperties().hideOnlinePlayers;
    }

    @Override
    public int getOperatorUserPermissionLevel() {
        return this.getProperties().opPermissionLevel;
    }

    @Override
    public int getFunctionCompilationLevel() {
        return this.getProperties().functionPermissionLevel;
    }

    @Override
    public void setPlayerIdleTimeout(int idleTimeout) {
        super.setPlayerIdleTimeout(idleTimeout);
        this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.playerIdleTimeout.update(this.registryAccess(), idleTimeout));
    }

    @Override
    public boolean shouldRconBroadcast() {
        return this.getProperties().broadcastRconToOps;
    }

    @Override
    public boolean shouldInformAdmins() {
        return this.getProperties().broadcastConsoleToOps;
    }

    @Override
    public int getAbsoluteMaxWorldSize() {
        return this.getProperties().maxWorldSize;
    }

    @Override
    public int getCompressionThreshold() {
        return this.getProperties().networkCompressionThreshold;
    }

    @Override
    public boolean enforceSecureProfile() {
        DedicatedServerProperties properties = this.getProperties();
        return properties.enforceSecureProfile && properties.onlineMode && this.services.canValidateProfileKeys();
    }

    @Override
    public boolean logIPs() {
        return this.getProperties().logIPs;
    }

    protected boolean convertOldUsers() {
        boolean flag = false;

        for (int i = 0; !flag && i <= 2; i++) {
            if (i > 0) {
                LOGGER.warn("Encountered a problem while converting the user banlist, retrying in a few seconds");
                this.waitForRetry();
            }

            flag = OldUsersConverter.convertUserBanlist(this);
        }

        boolean flag1 = false;

        for (int var7 = 0; !flag1 && var7 <= 2; var7++) {
            if (var7 > 0) {
                LOGGER.warn("Encountered a problem while converting the ip banlist, retrying in a few seconds");
                this.waitForRetry();
            }

            flag1 = OldUsersConverter.convertIpBanlist(this);
        }

        boolean flag2 = false;

        for (int var8 = 0; !flag2 && var8 <= 2; var8++) {
            if (var8 > 0) {
                LOGGER.warn("Encountered a problem while converting the op list, retrying in a few seconds");
                this.waitForRetry();
            }

            flag2 = OldUsersConverter.convertOpsList(this);
        }

        boolean flag3 = false;

        for (int var9 = 0; !flag3 && var9 <= 2; var9++) {
            if (var9 > 0) {
                LOGGER.warn("Encountered a problem while converting the whitelist, retrying in a few seconds");
                this.waitForRetry();
            }

            flag3 = OldUsersConverter.convertWhiteList(this);
        }

        boolean flag4 = false;

        for (int var10 = 0; !flag4 && var10 <= 2; var10++) {
            if (var10 > 0) {
                LOGGER.warn("Encountered a problem while converting the player save files, retrying in a few seconds");
                this.waitForRetry();
            }

            flag4 = OldUsersConverter.convertPlayers(this);
        }

        return flag || flag1 || flag2 || flag3 || flag4;
    }

    private void waitForRetry() {
        try {
            Thread.sleep(5000L);
        } catch (InterruptedException var2) {
        }
    }

    public long getMaxTickLength() {
        return this.getProperties().maxTickTime;
    }

    @Override
    public int getMaxChainedNeighborUpdates() {
        return this.getProperties().maxChainedNeighborUpdates;
    }

    @Override
    public String getPluginNames() {
        return "";
    }

    @Override
    public String runCommand(String command) {
        this.rconConsoleSource.prepareForCommand();
        this.executeBlocking(() -> this.getCommands().performPrefixedCommand(this.rconConsoleSource.createCommandSourceStack(), command));
        return this.rconConsoleSource.getCommandResponse();
    }

    public void storeUsingWhiteList(boolean isStoreUsingWhiteList) {
        this.settings.update(properties -> properties.whiteList.update(this.registryAccess(), isStoreUsingWhiteList));
    }

    @Override
    public void stopServer() {
        super.stopServer();
        Util.shutdownExecutors();
        SkullBlockEntity.clear();
    }

    @Override
    public boolean isSingleplayerOwner(GameProfile profile) {
        return false;
    }

    @Override
    public int getScaledTrackingDistance(int trackingDistance) {
        return this.getProperties().entityBroadcastRangePercentage * trackingDistance / 100;
    }

    @Override
    public String getLevelIdName() {
        return this.storageSource.getLevelId();
    }

    @Override
    public boolean forceSynchronousWrites() {
        return this.settings.getProperties().syncChunkWrites;
    }

    @Override
    public TextFilter createTextFilterForPlayer(ServerPlayer player) {
        return this.serverTextFilter != null ? this.serverTextFilter.createContext(player.getGameProfile()) : TextFilter.DUMMY;
    }

    @Nullable
    @Override
    public GameType getForcedGameType() {
        return this.settings.getProperties().forceGameMode ? this.worldData.getGameType() : null;
    }

    @Override
    public Optional<MinecraftServer.ServerResourcePackInfo> getServerResourcePack() {
        return this.settings.getProperties().serverResourcePackInfo;
    }

    @Override
    public void endMetricsRecordingTick() {
        super.endMetricsRecordingTick();
        this.debugSampleSubscriptionTracker.tick(this.getTickCount());
    }

    @Override
    public SampleLogger getTickTimeLogger() {
        return this.tickTimeLogger;
    }

    @Override
    public boolean isTickTimeLoggingEnabled() {
        return this.debugSampleSubscriptionTracker.shouldLogSamples(RemoteDebugSampleType.TICK_TIME);
    }

    @Override
    public void subscribeToDebugSample(ServerPlayer player, RemoteDebugSampleType sampleType) {
        this.debugSampleSubscriptionTracker.subscribe(player, sampleType);
    }

    @Override
    public boolean acceptsTransfers() {
        return this.settings.getProperties().acceptsTransfers;
    }

    @Override
    public ServerLinks serverLinks() {
        return this.serverLinks;
    }

    @Override
    public int pauseWhileEmptySeconds() {
        return this.settings.getProperties().pauseWhenEmptySeconds;
    }

    private static ServerLinks createServerLinks(DedicatedServerSettings settings) {
        Optional<URI> optional = parseBugReportLink(settings.getProperties());
        return optional.<ServerLinks>map(uri -> new ServerLinks(List.of(ServerLinks.KnownLinkType.BUG_REPORT.create(uri)))).orElse(ServerLinks.EMPTY);
    }

    private static Optional<URI> parseBugReportLink(DedicatedServerProperties properties) {
        String string = properties.bugReportLink;
        if (string.isEmpty()) {
            return Optional.empty();
        } else {
            try {
                return Optional.of(Util.parseAndValidateUntrustedUri(string));
            } catch (Exception var3) {
                LOGGER.warn("Failed to parse bug link {}", string, var3);
                return Optional.empty();
            }
        }
    }
}
