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
    private final java.util.Queue<ConsoleInput> serverCommandQueue = new java.util.concurrent.ConcurrentLinkedQueue<>(); // Paper - Perf: use a proper queue
    @Nullable
    private QueryThreadGs4 queryThreadGs4;
    // private final RconConsoleSource rconConsoleSource; // CraftBukkit - remove field
    @Nullable
    private RconThread rconThread;
    public DedicatedServerSettings settings;
    @Nullable
    private MinecraftServerGui gui;
    @Nullable
    private final ServerTextFilter serverTextFilter;
    @Nullable
    private RemoteSampleLogger tickTimeLogger;
    @Nullable
    private DebugSampleSubscriptionTracker debugSampleSubscriptionTracker;
    public ServerLinks serverLinks;

    public DedicatedServer(
        joptsimple.OptionSet options, net.minecraft.server.WorldLoader.DataLoadContext worldLoader, // CraftBukkit - Signature changed
        Thread serverThread,
        LevelStorageSource.LevelStorageAccess storageSource,
        PackRepository packRepository,
        WorldStem worldStem,
        DedicatedServerSettings settings,
        DataFixer fixerUpper,
        Services services,
        ChunkProgressListenerFactory progressListenerFactory
    ) {
        super(options, worldLoader, serverThread, storageSource, packRepository, worldStem, Proxy.NO_PROXY, fixerUpper, services, progressListenerFactory); // CraftBukkit - Signature changed
        this.settings = settings;
        //this.rconConsoleSource = new RconConsoleSource(this); // CraftBukkit - remove field
        this.serverTextFilter = ServerTextFilter.createFromConfig(settings.getProperties());
        this.serverLinks = createServerLinks(settings);
    }

    @Override
    public boolean initServer() throws IOException {
        Thread thread = new Thread("Server console handler") {
            @Override
            public void run() {
                // CraftBukkit start
                if (!org.bukkit.craftbukkit.Main.useConsole) return;
                // Paper start - Use TerminalConsoleAppender
                if (DedicatedServer.this.gui == null || System.console() != null) // Purpur - GUI Improvements - has no GUI or has console (did not double-click)
                new com.destroystokyo.paper.console.PaperConsole(DedicatedServer.this).start();
                /*
                jline.console.ConsoleReader bufferedreader = DedicatedServer.this.reader;
                // MC-33041, SPIGOT-5538: if System.in is not valid due to javaw, then return
                try {
                    System.in.available();
                } catch (IOException ex) {
                    return;
                }
                // CraftBukkit end
                String string1;
                try {
                    // CraftBukkit start - JLine disabling compatibility
                    while (!DedicatedServer.this.isStopped() && DedicatedServer.this.isRunning()) {
                        if (org.bukkit.craftbukkit.Main.useJline) {
                            string1 = bufferedreader.readLine(">", null);
                        } else {
                            string1 = bufferedreader.readLine();
                        }

                        // SPIGOT-5220: Throttle if EOF (ctrl^d) or stdin is /dev/null
                        if (string1 == null) {
                            try {
                                Thread.sleep(50L);
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }
                            continue;
                        }
                        if (string1.trim().length() > 0) { // Trim to filter lines which are just spaces
                            DedicatedServer.this.issueCommand(s, DedicatedServer.this.getServerCommandListener());
                        }
                        // CraftBukkit end
                    }
                } catch (IOException var4) {
                    DedicatedServer.LOGGER.error("Exception handling console input", (Throwable)var4);
                }
                */
                // Paper end
            }
        };
        // CraftBukkit start - TODO: handle command-line logging arguments
        java.util.logging.Logger global = java.util.logging.Logger.getLogger("");
        global.setUseParentHandlers(false);
        for (java.util.logging.Handler handler : global.getHandlers()) {
            global.removeHandler(handler);
        }
        global.addHandler(new org.bukkit.craftbukkit.util.ForwardLogHandler());

        // Paper start - Not needed with TerminalConsoleAppender
        final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getRootLogger();
        /*
        final org.apache.logging.log4j.core.Logger logger = ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger());
        for (org.apache.logging.log4j.core.Appender appender : logger.getAppenders().values()) {
            if (appender instanceof org.apache.logging.log4j.core.appender.ConsoleAppender) {
                logger.removeAppender(appender);
            }
        }

        TerminalConsoleWriterThread writerThread = new TerminalConsoleWriterThread(System.out, this.reader);
        this.reader.setCompletionHandler(new TerminalCompletionHandler(writerThread, this.reader.getCompletionHandler()));
        writerThread.start();
        */
        // Paper end - Not needed with TerminalConsoleAppender

        System.setOut(org.apache.logging.log4j.io.IoBuilder.forLogger(logger).setLevel(org.apache.logging.log4j.Level.INFO).buildPrintStream());
        System.setErr(org.apache.logging.log4j.io.IoBuilder.forLogger(logger).setLevel(org.apache.logging.log4j.Level.WARN).buildPrintStream());
        // CraftBukkit end
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
        // thread.start(); // Paper - Enhance console tab completions for brigadier commands; moved down
        LOGGER.info("Starting minecraft server version {}", SharedConstants.getCurrentVersion().getName());
        if (Runtime.getRuntime().maxMemory() / 1024L / 1024L < 512L) {
            LOGGER.warn("To start the server with more ram, launch it as \"java -Xmx1024M -Xms1024M -jar minecraft_server.jar\"");
        }

        // Paper start - detect running as root
        if (io.papermc.paper.util.ServerEnvironment.userIsRootOrAdmin()) {
            LOGGER.warn("****************************");
            LOGGER.warn("YOU ARE RUNNING THIS SERVER AS AN ADMINISTRATIVE OR ROOT USER. THIS IS NOT ADVISED.");
            LOGGER.warn("YOU ARE OPENING YOURSELF UP TO POTENTIAL RISKS WHEN DOING THIS.");
            LOGGER.warn("FOR MORE INFORMATION, SEE https://madelinemiller.dev/blog/root-minecraft-server/");
            LOGGER.warn("****************************");
        }
        // Paper end - detect running as root

        LOGGER.info("Loading properties");
        DedicatedServerProperties properties = this.settings.getProperties();
        if (this.isSingleplayer()) {
            this.setLocalIp("127.0.0.1");
        } else {
            this.setUsesAuthentication(properties.onlineMode);
            this.setPreventProxyConnections(properties.preventProxyConnections);
            this.setLocalIp(properties.serverIp);
        }

        // Spigot start
        this.setPlayerList(new DedicatedPlayerList(this, this.registries(), this.playerDataStorage));
        org.spigotmc.SpigotConfig.init((java.io.File) this.options.valueOf("spigot-settings"));
        org.spigotmc.SpigotConfig.registerCommands();
        // Spigot end
        io.papermc.paper.util.ObfHelper.INSTANCE.getClass(); // Paper - load mappings for stacktrace deobf and etc.
        // Purpur start - Configurable void damage height and damage
        try {
            org.purpurmc.purpur.PurpurConfig.init((java.io.File) options.valueOf("purpur-settings"));
        } catch (Exception e) {
            DedicatedServer.LOGGER.error("Unable to load server configuration", e);
            return false;
        }
        org.purpurmc.purpur.PurpurConfig.registerCommands();
        // Purpur end - Configurable void damage height and damage
        // Paper start - initialize global and world-defaults configuration
        this.paperConfigurations.initializeGlobalConfiguration(this.registryAccess());
        this.paperConfigurations.initializeWorldDefaultsConfiguration(this.registryAccess());
        // Paper end - initialize global and world-defaults configuration
        this.server.spark.enableEarlyIfRequested(); // Paper - spark
        // Paper start - fix converting txt to json file; convert old users earlier after PlayerList creation but before file load/save
        if (this.convertOldUsers()) {
            this.getProfileCache().save(false); // Paper
        }
        this.getPlayerList().loadAndSaveFiles(); // Must be after convertNames
        // Paper end - fix converting txt to json file
        org.spigotmc.WatchdogThread.doStart(org.spigotmc.SpigotConfig.timeoutTime, org.spigotmc.SpigotConfig.restartOnCrash); // Paper - start watchdog thread
        thread.start(); // Paper - Enhance console tab completions for brigadier commands; start console thread after MinecraftServer.console & PaperConfig are initialized
        io.papermc.paper.command.PaperCommands.registerCommands(this); // Paper - setup /paper command
        this.server.spark.registerCommandBeforePlugins(this.server); // Paper - spark
        com.destroystokyo.paper.Metrics.PaperMetrics.startMetrics(); // Paper - start metrics
        /*// Purpur start - Purpur config files // Purpur - Configurable void damage height and damage
        try {
            org.purpurmc.purpur.PurpurConfig.init((java.io.File) options.valueOf("purpur-settings"));
        } catch (Exception e) {
            DedicatedServer.LOGGER.error("Unable to load server configuration", e);
            return false;
        }
        org.purpurmc.purpur.PurpurConfig.registerCommands();
        */// Purpur end - Purpur config files // Purpur - Configurable void damage height and damage
        com.destroystokyo.paper.VersionHistoryManager.INSTANCE.getClass(); // Paper - load version history now

        this.setPvpAllowed(properties.pvp);
        this.setFlightAllowed(properties.allowFlight);
        this.setMotd(properties.motd);
        super.setPlayerIdleTimeout(properties.playerIdleTimeout.get());
        this.setEnforceWhitelist(properties.enforceWhitelist);
        // this.worldData.setGameType(properties.gamemode); // CraftBukkit - moved to world loading
        LOGGER.info("Default game type: {}", properties.gamemode);
        // Paper start - Unix domain socket support
        java.net.SocketAddress bindAddress;
        if (this.getLocalIp().startsWith("unix:")) {
            if (!io.netty.channel.epoll.Epoll.isAvailable()) {
                LOGGER.error("**** INVALID CONFIGURATION!");
                LOGGER.error("You are trying to use a Unix domain socket but you're not on a supported OS.");
                return false;
            } else if (!io.papermc.paper.configuration.GlobalConfiguration.get().proxies.velocity.enabled && !org.spigotmc.SpigotConfig.bungee) {
                LOGGER.error("**** INVALID CONFIGURATION!");
                LOGGER.error("Unix domain sockets require IPs to be forwarded from a proxy.");
                return false;
            }
            bindAddress = new io.netty.channel.unix.DomainSocketAddress(this.getLocalIp().substring("unix:".length()));
        } else {
        InetAddress inetAddress = null;
        if (!this.getLocalIp().isEmpty()) {
            inetAddress = InetAddress.getByName(this.getLocalIp());
        }

        if (this.getPort() < 0) {
            this.setPort(properties.serverPort);
        }
        bindAddress = new java.net.InetSocketAddress(inetAddress, this.getPort());
        }
        // Paper end - Unix domain socket support

        this.initializeKeyPair();
        LOGGER.info("Starting Minecraft server on {}:{}", this.getLocalIp().isEmpty() ? "*" : this.getLocalIp(), this.getPort());

        try {
            this.getConnection().startTcpServerListener(bindAddress); // Paper - Unix domain socket support
        } catch (IOException var10) {
            LOGGER.warn("**** FAILED TO BIND TO PORT!");
            LOGGER.warn("The exception was: {}", var10.toString());
            LOGGER.warn("Perhaps a server is already running on that port?");
            if (true) throw new IllegalStateException("Failed to bind to port", var10); // Paper - Propagate failed to bind to port error
            return false;
        }
        // Purpur start - UPnP Port Forwarding
        if (org.purpurmc.purpur.PurpurConfig.useUPnP) {
            LOGGER.info("[UPnP] Attempting to start UPnP port forwarding service...");
            if (dev.omega24.upnp4j.UPnP4J.isUPnPAvailable()) {
                if (dev.omega24.upnp4j.UPnP4J.isOpen(this.getPort(), dev.omega24.upnp4j.util.Protocol.TCP)) {
                    this.upnp = false;
                    LOGGER.info("[UPnP] Port {} is already open", this.getPort());
                } else if (dev.omega24.upnp4j.UPnP4J.open(this.getPort(), dev.omega24.upnp4j.util.Protocol.TCP)) {
                    this.upnp = true;
                    LOGGER.info("[UPnP] Successfully opened port {}", this.getPort());
                } else {
                    this.upnp = false;
                    LOGGER.info("[UPnP] Failed to open port {}", this.getPort());
                }

                if (upnp) {
                    LOGGER.info("[UPnP] {}:{}", dev.omega24.upnp4j.UPnP4J.getExternalIP(), this.getPort());
                }
            } else {
                this.upnp = false;
                LOGGER.error("[UPnP] Service is unavailable");
            }
        }
        // Purpur end - UPnP Port Forwarding

        // CraftBukkit start
        // this.setPlayerList(new DedicatedPlayerList(this, this.registries(), this.playerDataStorage)); // Spigot - moved up
        this.server.loadPlugins();
        this.server.enablePlugins(org.bukkit.plugin.PluginLoadOrder.STARTUP);
        // CraftBukkit end

        // Paper start - Add Velocity IP Forwarding Support
        boolean usingProxy = org.spigotmc.SpigotConfig.bungee || io.papermc.paper.configuration.GlobalConfiguration.get().proxies.velocity.enabled;
        String proxyFlavor = (io.papermc.paper.configuration.GlobalConfiguration.get().proxies.velocity.enabled) ? "Velocity" : "BungeeCord";
        String proxyLink = (io.papermc.paper.configuration.GlobalConfiguration.get().proxies.velocity.enabled) ? "https://docs.papermc.io/velocity/security" : "http://www.spigotmc.org/wiki/firewall-guide/";
        // Paper end - Add Velocity IP Forwarding Support
        if (!this.usesAuthentication()) {
            LOGGER.warn("**** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!");
            LOGGER.warn("The server will make no attempt to authenticate usernames. Beware.");
            // Spigot start
            // Paper start - Add Velocity IP Forwarding Support
            if (usingProxy) {
                LOGGER.warn("Whilst this makes it possible to use {}, unless access to your server is properly restricted, it also opens up the ability for hackers to connect with any username they choose.", proxyFlavor);
                LOGGER.warn("Please see {} for further information.", proxyLink);
                // Paper end - Add Velocity IP Forwarding Support
            } else {
                LOGGER.warn("While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.");
            }
            // Spigot end
            LOGGER.warn("To change this, set \"online-mode\" to \"true\" in the server.properties file.");
        }

        // CraftBukkit start
        /*
        if (this.convertOldUsers()) {
            this.getProfileCache().save();
        }
        */
        // CraftBukkit end

        if (!OldUsersConverter.serverReadyAfterUserconversion(this)) {
            return false;
        } else {
            // this.setPlayerList(new DedicatedPlayerList(this, this.registries(), this.playerDataStorage)); // CraftBukkit - moved up
            this.debugSampleSubscriptionTracker = new DebugSampleSubscriptionTracker(this.getPlayerList());
            this.tickTimeLogger = new RemoteSampleLogger(
                TpsDebugDimensions.values().length, this.debugSampleSubscriptionTracker, RemoteDebugSampleType.TICK_TIME
            );
            long nanos = Util.getNanos();
            SkullBlockEntity.setup(this.services, this);
            GameProfileCache.setUsesAuthentication(this.usesAuthentication());
            LOGGER.info("Preparing level \"{}\"", this.getLevelIdName());
            this.loadLevel(this.storageSource.getLevelId()); // CraftBukkit
            long l = Util.getNanos() - nanos;
            String string = String.format(Locale.ROOT, "%.3fs", l / 1.0E9);
            LOGGER.info("Done preparing level \"{}\" ({})", this.getLevelIdName(), string); // Paper - Improve startup message, add total time
            if (properties.announcePlayerAchievements != null) {
                this.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(properties.announcePlayerAchievements, this.overworld()); // CraftBukkit - per-world
            }

            if (properties.enableQuery) {
                LOGGER.info("Starting GS4 status listener");
                this.queryThreadGs4 = QueryThreadGs4.create(this);
            }

            if (properties.enableRcon) {
                LOGGER.info("Starting remote control listener");
                this.rconThread = RconThread.create(this);
            }

            if (false && this.getMaxTickLength() > 0L) { // Spigot - disable
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

            org.purpurmc.purpur.task.BossBarTask.startAll(); // Purpur - Implement TPSBar
            if (org.purpurmc.purpur.PurpurConfig.beeCountPayload) org.purpurmc.purpur.task.BeehiveTask.instance().register(); // Purpur - Give bee counts in beehives to Purpur clients
            return true;
        }
    }

    // Paper start
    public java.io.File getPluginsFolder() {
        return (java.io.File) this.options.valueOf("plugins");
    }
    // Paper end

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
        // this.setDifficulty(this.getProperties().difficulty, true); // Paper - per level difficulty; Don't overwrite level.dat's difficulty, keep current
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
            this.rconThread.stopNonBlocking(); // Paper - don't wait for remote connections
        }

        if (this.queryThreadGs4 != null) {
            // this.remoteStatusListener.stop(); // Paper - don't wait for remote connections
        }

        this.hasFullyShutdown = true; // Paper - Improved watchdog support
        System.exit(this.abnormalExit ? 70 : 0); // CraftBukkit // Paper - Improved watchdog support
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

    private static final java.util.concurrent.atomic.AtomicInteger ASYNC_DEBUG_CHUNKS_COUNT = new java.util.concurrent.atomic.AtomicInteger(); // Paper - rewrite chunk system

    public void handleConsoleInput(String msg, CommandSourceStack source) {
        // Paper start - rewrite chunk system
        if (msg.equalsIgnoreCase("paper debug chunks --async")) {
            LOGGER.info("Scheduling async debug chunks");
            Runnable run = () -> {
                LOGGER.info("Async debug chunks executing");
                ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.dumpAllChunkLoadInfo(this, false);
                org.bukkit.command.CommandSender sender = MinecraftServer.getServer().console;
                java.io.File file = ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.getChunkDebugFile();
                sender.sendMessage(net.kyori.adventure.text.Component.text("Writing chunk information dump to " + file, net.kyori.adventure.text.format.NamedTextColor.GREEN));
                try {
                    ca.spottedleaf.moonrise.common.util.JsonUtil.writeJson(ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.debugAllWorlds(this), file);
                    sender.sendMessage(net.kyori.adventure.text.Component.text("Successfully written chunk information!", net.kyori.adventure.text.format.NamedTextColor.GREEN));
                } catch (Throwable thr) {
                    MinecraftServer.LOGGER.warn("Failed to dump chunk information to file " + file.toString(), thr);
                    sender.sendMessage(net.kyori.adventure.text.Component.text("Failed to dump chunk information, see console", net.kyori.adventure.text.format.NamedTextColor.RED));
                }
            };
            Thread t = new Thread(run);
            t.setName("Async debug thread #" + ASYNC_DEBUG_CHUNKS_COUNT.getAndIncrement());
            t.setDaemon(true);
            t.start();
            return;
        }
        // Paper end - rewrite chunk system
        this.serverCommandQueue.add(new ConsoleInput(msg, source)); // Paper - Perf: use proper queue
    }

    public void handleConsoleInputs() {
        // Paper start - Perf: use proper queue
        ConsoleInput servercommand;
        while ((servercommand = this.serverCommandQueue.poll()) != null) {
            // Paper end - Perf: use proper queue
            // CraftBukkit start - ServerCommand for preprocessing
            org.bukkit.event.server.ServerCommandEvent event = new org.bukkit.event.server.ServerCommandEvent(this.console, servercommand.msg);
            this.server.getPluginManager().callEvent(event);
            if (event.isCancelled()) continue;
            servercommand = new ConsoleInput(event.getCommand(), servercommand.source);

            // this.getCommands().performPrefixedCommand(servercommand.source, servercommand.msg); // Called in dispatchServerCommand
            this.server.dispatchServerCommand(this.console, servercommand);
            // CraftBukkit end
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
        // Paper start - Add setting for proxy online mode status
        return properties.enforceSecureProfile
            && io.papermc.paper.configuration.GlobalConfiguration.get().proxies.isProxyOnlineMode()
            && this.services.canValidateProfileKeys();
        // Paper end - Add setting for proxy online mode status
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
        // CraftBukkit start - Whole method
        StringBuilder result = new StringBuilder();
        org.bukkit.plugin.Plugin[] plugins = this.server.getPluginManager().getPlugins();

        result.append(this.server.getName());
        result.append(" on Bukkit ");
        result.append(this.server.getBukkitVersion());

        if (plugins.length > 0 && this.server.getQueryPlugins()) {
            result.append(": ");

            for (int i = 0; i < plugins.length; i++) {
                if (i > 0) {
                    result.append("; ");
                }

                result.append(plugins[i].getDescription().getName());
                result.append(" ");
                result.append(plugins[i].getDescription().getVersion().replaceAll(";", ","));
            }
        }

        return result.toString();
        // CraftBukkit end
    }

    @Override
    public String runCommand(String command) {
        // CraftBukkit start - fire RemoteServerCommandEvent
        throw new UnsupportedOperationException("Not supported - remote source required.");
    }

    public String runCommand(RconConsoleSource rconConsoleSource, String s) {
        rconConsoleSource.prepareForCommand();
        this.executeBlocking(() -> {
            CommandSourceStack wrapper = rconConsoleSource.createCommandSourceStack();
            org.bukkit.event.server.RemoteServerCommandEvent event = new org.bukkit.event.server.RemoteServerCommandEvent(rconConsoleSource.getBukkitSender(wrapper), s);
            this.server.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
            ConsoleInput serverCommand = new ConsoleInput(event.getCommand(), wrapper);
            this.server.dispatchServerCommand(event.getSender(), serverCommand);
        });
        return rconConsoleSource.getCommandResponse();
        // CraftBukkit end
    }

    public void storeUsingWhiteList(boolean isStoreUsingWhiteList) {
        this.settings.update(properties -> properties.whiteList.update(this.registryAccess(), isStoreUsingWhiteList));
    }

    @Override
    public void stopServer() {
        super.stopServer();
        //Util.shutdownExecutors(); // Paper - Improved watchdog support; moved into super
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

    // CraftBukkit start
    public boolean isDebugging() {
        return this.getProperties().debug;
    }

    @Override
    public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
        return this.console;
    }
    // CraftBukkit end
}
