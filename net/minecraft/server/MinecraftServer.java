package net.minecraft.server;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.datafixers.DataFixer;
import com.mojang.jtracy.DiscontinuousFrame;
import com.mojang.jtracy.TracyClient;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.Proxy;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.FileUtil;
import net.minecraft.ReportType;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.Util;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.obfuscate.DontObfuscate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.DemoMode;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;
import net.minecraft.util.ModCheck;
import net.minecraft.util.Mth;
import net.minecraft.util.NativeModuleLister;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.debugchart.RemoteDebugSampleType;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.util.debugchart.TpsDebugDimensions;
import net.minecraft.util.profiling.EmptyProfileResults;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.ResultField;
import net.minecraft.util.profiling.SingleTickProfiler;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.util.profiling.metrics.profiling.ActiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.InactiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.MetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.ServerMetricsSamplersProvider;
import net.minecraft.util.profiling.metrics.storage.MetricsPersister;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.CommandStorage;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public abstract class MinecraftServer extends ReentrantBlockableEventLoop<TickTask> implements ServerInfo, ChunkIOErrorReporter, CommandSource, ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer { // Paper - rewrite chunk system
    private static MinecraftServer SERVER; // Paper
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final net.kyori.adventure.text.logger.slf4j.ComponentLogger COMPONENT_LOGGER = net.kyori.adventure.text.logger.slf4j.ComponentLogger.logger(LOGGER.getName()); // Paper
    public static final String VANILLA_BRAND = "vanilla";
    private static final float AVERAGE_TICK_TIME_SMOOTHING = 0.8F;
    private static final int TICK_STATS_SPAN = 100;
    private static final long OVERLOADED_THRESHOLD_NANOS = 30L * TimeUtil.NANOSECONDS_PER_SECOND / 20L; // CraftBukkit
    private static final int OVERLOADED_TICKS_THRESHOLD = 20;
    private static final long OVERLOADED_WARNING_INTERVAL_NANOS = 10L * TimeUtil.NANOSECONDS_PER_SECOND;
    private static final int OVERLOADED_TICKS_WARNING_INTERVAL = 100;
    private static final long STATUS_EXPIRE_TIME_NANOS = 5L * TimeUtil.NANOSECONDS_PER_SECOND;
    private static final long PREPARE_LEVELS_DEFAULT_DELAY_NANOS = 10L * TimeUtil.NANOSECONDS_PER_MILLISECOND;
    private static final int MAX_STATUS_PLAYER_SAMPLE = 12;
    private static final int SPAWN_POSITION_SEARCH_RADIUS = 5;
    private static final int AUTOSAVE_INTERVAL = 6000;
    private static final int MIMINUM_AUTOSAVE_TICKS = 100;
    private static final int MAX_TICK_LATENCY = 3;
    public static final int ABSOLUTE_MAX_WORLD_SIZE = 29999984;
    public static final LevelSettings DEMO_SETTINGS = new LevelSettings(
        "Demo World", GameType.SURVIVAL, false, Difficulty.NORMAL, false, new GameRules(FeatureFlags.DEFAULT_FLAGS), WorldDataConfiguration.DEFAULT
    );
    public static final GameProfile ANONYMOUS_PLAYER_PROFILE = new GameProfile(Util.NIL_UUID, "Anonymous Player");
    public LevelStorageSource.LevelStorageAccess storageSource;
    public final PlayerDataStorage playerDataStorage;
    private final List<Runnable> tickables = Lists.newArrayList();
    private MetricsRecorder metricsRecorder = InactiveMetricsRecorder.INSTANCE;
    private Consumer<ProfileResults> onMetricsRecordingStopped = results -> this.stopRecordingMetrics();
    private Consumer<Path> onMetricsRecordingFinished = path -> {};
    private boolean willStartRecordingMetrics;
    @Nullable
    private MinecraftServer.TimeProfiler debugCommandProfiler;
    private boolean debugCommandProfilerDelayStart;
    private ServerConnectionListener connection;
    public final ChunkProgressListenerFactory progressListenerFactory;
    @Nullable
    private ServerStatus status;
    @Nullable
    private ServerStatus.Favicon statusIcon;
    private final RandomSource random = RandomSource.create();
    public final DataFixer fixerUpper;
    private String localIp;
    private int port = -1;
    private final LayeredRegistryAccess<RegistryLayer> registries;
    private Map<ResourceKey<Level>, ServerLevel> levels = Maps.newLinkedHashMap();
    private PlayerList playerList;
    private volatile boolean running = true;
    private volatile boolean isRestarting = false; // Paper - flag to signify we're attempting to restart
    private boolean stopped;
    private int tickCount;
    private int ticksUntilAutosave = 6000;
    protected final Proxy proxy;
    private boolean onlineMode;
    private boolean preventProxyConnections;
    private boolean pvp;
    private boolean allowFlight;
    private net.kyori.adventure.text.Component motd; // Paper - Adventure
    private int playerIdleTimeout;
    private final long[] tickTimesNanos = new long[100];
    private long aggregatedTickTimesNanos = 0L;
    // Paper start - Add tick times API and /mspt command
    public final TickTimes tickTimes5s = new TickTimes(100);
    public final TickTimes tickTimes10s = new TickTimes(200);
    public final TickTimes tickTimes60s = new TickTimes(1200);
    // Paper end - Add tick times API and /mspt command
    @Nullable
    private KeyPair keyPair;
    @Nullable
    private GameProfile singleplayerProfile;
    private boolean isDemo;
    private volatile boolean isReady;
    private long lastOverloadWarningNanos;
    protected final Services services;
    private long lastServerStatus;
    public final Thread serverThread;
    private long lastTickNanos = Util.getNanos();
    private long taskExecutionStartNanos = Util.getNanos();
    private long idleTimeNanos;
    private long nextTickTimeNanos = Util.getNanos();
    private boolean waitingForNextTick = false;
    private long delayedTasksMaxNextTickTimeNanos;
    private boolean mayHaveDelayedTasks;
    private final PackRepository packRepository;
    private final ServerScoreboard scoreboard = new ServerScoreboard(this);
    @Nullable
    private CommandStorage commandStorage;
    private final CustomBossEvents customBossEvents = new CustomBossEvents();
    private final ServerFunctionManager functionManager;
    private boolean enforceWhitelist;
    private float smoothedTickTimeMillis;
    public final Executor executor;
    @Nullable
    private String serverId;
    public MinecraftServer.ReloadableResources resources;
    private final StructureTemplateManager structureTemplateManager;
    private final ServerTickRateManager tickRateManager;
    protected WorldData worldData;
    public PotionBrewing potionBrewing;
    private FuelValues fuelValues;
    private int emptyTicks;
    private volatile boolean isSaving;
    private static final AtomicReference<RuntimeException> fatalException = new AtomicReference<>();
    private final SuppressedExceptionCollector suppressedExceptions = new SuppressedExceptionCollector();
    private final DiscontinuousFrame tickFrame;

    // CraftBukkit start
    public final WorldLoader.DataLoadContext worldLoader;
    public org.bukkit.craftbukkit.CraftServer server;
    public joptsimple.OptionSet options;
    public org.bukkit.command.ConsoleCommandSender console;
    public static int currentTick; // Paper - improve tick loop
    public static final long startTimeMillis = System.currentTimeMillis(); // Purpur - Add uptime command
    public java.util.Queue<Runnable> processQueue = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
    public int autosavePeriod;
    // Paper - don't store the vanilla dispatcher
    public boolean forceTicks;
    // CraftBukkit end
    // Spigot start
    public static final int TPS = 20;
    public static final int TICK_TIME = 1000000000 / MinecraftServer.TPS;
    private static final int SAMPLE_INTERVAL = 20; // Paper - improve server tick loop
    @Deprecated(forRemoval = true) // Paper
    public final double[] recentTps = new double[4]; // Purpur - Add 5 second tps average in /tps
    // Spigot end
    public volatile boolean hasFullyShutdown; // Paper - Improved watchdog support
    public volatile boolean abnormalExit; // Paper - Improved watchdog support
    public volatile Thread shutdownThread; // Paper - Improved watchdog support
    public final io.papermc.paper.configuration.PaperConfigurations paperConfigurations; // Paper - add paper configuration files
    public boolean isIteratingOverLevels = false; // Paper - Throw exception on world create while being ticked
    private final Set<String> pluginsBlockingSleep = new java.util.HashSet<>(); // Paper - API to allow/disallow tick sleeping
    public boolean lagging = false; // Purpur - Lagging threshold
    public static final long SERVER_INIT = System.nanoTime(); // Paper - Lag compensation
    protected boolean upnp = false; // Purpur - UPnP Port Forwarding

    public static <S extends MinecraftServer> S spin(Function<Thread, S> threadFunction) {
        ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.init(); // Paper - rewrite data converter system
        AtomicReference<S> atomicReference = new AtomicReference<>();
        Thread thread = new ca.spottedleaf.moonrise.common.util.TickThread(() -> atomicReference.get().runServer(), "Server thread");
        thread.setUncaughtExceptionHandler((thread1, exception) -> LOGGER.error("Uncaught exception in server thread", exception));
        thread.setPriority(Thread.NORM_PRIORITY+2); // Paper - Perf: Boost priority
        if (Runtime.getRuntime().availableProcessors() > 4) {
            thread.setPriority(8);
        }

        S minecraftServer = (S)threadFunction.apply(thread);
        atomicReference.set(minecraftServer);
        thread.start();
        return minecraftServer;
    }

    // Paper start - rewrite chunk system
    private volatile Throwable chunkSystemCrash;

    @Override
    public final void moonrise$setChunkSystemCrash(final Throwable throwable) {
        this.chunkSystemCrash = throwable;
    }

    private static final long CHUNK_TASK_QUEUE_BACKOFF_MIN_TIME = 25L * 1000L; // 25us
    private static final long MAX_CHUNK_EXEC_TIME = 1000L; // 1us
    private static final long TASK_EXECUTION_FAILURE_BACKOFF = 5L * 1000L; // 5us

    private long lastMidTickExecute;
    private long lastMidTickExecuteFailure;

    private boolean tickMidTickTasks() {
        // give all worlds a fair chance at by targeting them all.
        // if we execute too many tasks, that's fine - we have logic to correctly handle overuse of allocated time.
        boolean executed = false;
        for (final ServerLevel world : this.getAllLevels()) {
            long currTime = System.nanoTime();
            if (currTime - ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)world).moonrise$getLastMidTickFailure() <= TASK_EXECUTION_FAILURE_BACKOFF) {
                continue;
            }
            if (!world.getChunkSource().pollTask()) {
                // we need to back off if this fails
                ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)world).moonrise$setLastMidTickFailure(currTime);
            } else {
                executed = true;
            }
        }

        return executed;
    }

    @Override
    public final void moonrise$executeMidTickTasks() {
        final long startTime = System.nanoTime();
        if ((startTime - this.lastMidTickExecute) <= CHUNK_TASK_QUEUE_BACKOFF_MIN_TIME || (startTime - this.lastMidTickExecuteFailure) <= TASK_EXECUTION_FAILURE_BACKOFF) {
            // it's shown to be bad to constantly hit the queue (chunk loads slow to a crawl), even if no tasks are executed.
            // so, backoff to prevent this
            return;
        }

        for (;;) {
            final boolean moreTasks = this.tickMidTickTasks();
            final long currTime = System.nanoTime();
            final long diff = currTime - startTime;

            if (!moreTasks || diff >= MAX_CHUNK_EXEC_TIME) {
                if (!moreTasks) {
                    this.lastMidTickExecuteFailure = currTime;
                }

                // note: negative values reduce the time
                long overuse = diff - MAX_CHUNK_EXEC_TIME;
                if (overuse >= (10L * 1000L * 1000L)) { // 10ms
                    // make sure something like a GC or dumb plugin doesn't screw us over...
                    overuse = 10L * 1000L * 1000L; // 10ms
                }

                final double overuseCount = (double)overuse/(double)MAX_CHUNK_EXEC_TIME;
                final long extraSleep = (long)Math.round(overuseCount*CHUNK_TASK_QUEUE_BACKOFF_MIN_TIME);

                this.lastMidTickExecute = currTime + extraSleep;
                return;
            }
        }
    }
    // Paper end - rewrite chunk system

    public MinecraftServer(
        // CraftBukkit start
        joptsimple.OptionSet options,
        WorldLoader.DataLoadContext worldLoader,
        // CraftBukkit end
        Thread serverThread,
        LevelStorageSource.LevelStorageAccess storageSource,
        PackRepository packRepository,
        WorldStem worldStem,
        Proxy proxy,
        DataFixer fixerUpper,
        Services services,
        ChunkProgressListenerFactory progressListenerFactory
    ) {
        super("Server");
        SERVER = this; // Paper - better singleton
        this.registries = worldStem.registries();
        this.worldData = worldStem.worldData();
        if (false && !this.registries.compositeAccess().lookupOrThrow(Registries.LEVEL_STEM).containsKey(LevelStem.OVERWORLD)) { // CraftBukkit - initialised later
            throw new IllegalStateException("Missing Overworld dimension data");
        } else {
            this.proxy = proxy;
            this.packRepository = packRepository;
            this.resources = new MinecraftServer.ReloadableResources(worldStem.resourceManager(), worldStem.dataPackResources());
            this.services = services;
            if (services.profileCache() != null) {
                services.profileCache().setExecutor(this);
            }

            // this.connection = new ServerConnectionListener(this); // Spigot
            this.tickRateManager = new ServerTickRateManager(this);
            this.progressListenerFactory = progressListenerFactory;
            this.storageSource = storageSource;
            this.playerDataStorage = storageSource.createPlayerStorage();
            this.fixerUpper = fixerUpper;
            this.functionManager = new ServerFunctionManager(this, this.resources.managers.getFunctionLibrary());
            HolderGetter<Block> holderGetter = this.registries
                .compositeAccess()
                .lookupOrThrow(Registries.BLOCK)
                .filterFeatures(this.worldData.enabledFeatures());
            this.structureTemplateManager = new StructureTemplateManager(worldStem.resourceManager(), storageSource, fixerUpper, holderGetter);
            this.serverThread = serverThread;
            this.executor = Util.backgroundExecutor();
            this.potionBrewing = PotionBrewing.bootstrap(this.worldData.enabledFeatures());
            this.resources.managers.getRecipeManager().finalizeRecipeLoading(this.worldData.enabledFeatures());
            this.fuelValues = FuelValues.vanillaBurnTimes(this.registries.compositeAccess(), this.worldData.enabledFeatures());
            this.tickFrame = TracyClient.createDiscontinuousFrame("Server Tick");
        }
        // CraftBukkit start
        this.options = options;
        this.worldLoader = worldLoader;
        // Paper start - Handled by TerminalConsoleAppender
        // Try to see if we're actually running in a terminal, disable jline if not
        /*
        if (System.console() == null && System.getProperty("jline.terminal") == null) {
            System.setProperty("jline.terminal", "jline.UnsupportedTerminal");
            Main.useJline = false;
        }

        try {
            this.reader = new ConsoleReader(System.in, System.out);
            this.reader.setExpandEvents(false); // Avoid parsing exceptions for uncommonly used event designators
        } catch (Throwable e) {
            try {
                // Try again with jline disabled for Windows users without C++ 2008 Redistributable
                System.setProperty("jline.terminal", "jline.UnsupportedTerminal");
                System.setProperty("user.language", "en");
                Main.useJline = false;
                this.reader = new ConsoleReader(System.in, System.out);
                this.reader.setExpandEvents(false);
            } catch (IOException ex) {
                MinecraftServer.LOGGER.warn((String) null, ex);
            }
        }
        */
        // Paper end
        io.papermc.paper.util.LogManagerShutdownThread.unhook(); // Paper - Improved watchdog support
        Runtime.getRuntime().addShutdownHook(new org.bukkit.craftbukkit.util.ServerShutdownThread(this));
        // CraftBukkit end
        this.paperConfigurations = services.paperConfigurations(); // Paper - add paper configuration files
    }

    private void readScoreboard(DimensionDataStorage dataStorage) {
        dataStorage.computeIfAbsent(this.getScoreboard().dataFactory(), "scoreboard");
    }

    protected abstract boolean initServer() throws IOException;

    protected void loadLevel(String levelId) { // CraftBukkit
        if (!JvmProfiler.INSTANCE.isRunning()) {
        }

        boolean flag = false;
        ProfiledDuration profiledDuration = JvmProfiler.INSTANCE.onWorldLoadedStarted();
        this.loadWorld0(levelId); // CraftBukkit
        if (profiledDuration != null) {
            profiledDuration.finish(true);
        }

        if (flag) {
            try {
                JvmProfiler.INSTANCE.stop();
            } catch (Throwable var5) {
                LOGGER.warn("Failed to stop JFR profiling", var5);
            }
        }
    }

    protected void forceDifficulty() {
    }

    // CraftBukkit start
    private void loadWorld0(String levelId) {
        // Mostly modelled off of net.minecraft.server.Main
        LevelStorageSource.LevelStorageAccess levelStorageAccess = this.storageSource;
        RegistryAccess.Frozen registryAccess = this.registries.compositeAccess();
        Registry<LevelStem> levelStemRegistry = registryAccess.lookupOrThrow(Registries.LEVEL_STEM);
        for (LevelStem levelStem : levelStemRegistry) {
            ResourceKey<LevelStem> levelStemKey = levelStemRegistry.getResourceKey(levelStem).get();
            ServerLevel serverLevel;
            int dimension = 0;

            if (levelStemKey == LevelStem.NETHER) {
                if (this.server.getAllowNether()) {
                    dimension = -1;
                } else {
                    continue;
                }
            } else if (levelStemKey == LevelStem.END) {
                if (this.server.getAllowEnd()) {
                    dimension = 1;
                } else {
                    continue;
                }
            } else if (levelStemKey != LevelStem.OVERWORLD) {
                dimension = -999;
            }

            // Migration of old CB world folders...
            String worldType = (dimension == -999) ? levelStemKey.location().getNamespace() + "_" + levelStemKey.location().getPath() : org.bukkit.World.Environment.getEnvironment(dimension).toString().toLowerCase(Locale.ROOT);
            String name = (levelStemKey == LevelStem.OVERWORLD) ? levelId : levelId + "_" + worldType;
            if (dimension != 0) {
                java.io.File newWorld = LevelStorageSource.getStorageFolder(new java.io.File(name).toPath(), levelStemKey).toFile();
                java.io.File oldWorld = LevelStorageSource.getStorageFolder(new java.io.File(levelId).toPath(), levelStemKey).toFile();
                java.io.File oldLevelDat = new java.io.File(new java.io.File(levelId), "level.dat"); // The data folders exist on first run as they are created in the PersistentCollection constructor above, but the level.dat won't

                if (!newWorld.isDirectory() && oldWorld.isDirectory() && oldLevelDat.isFile()) {
                    MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder required ----");
                    MinecraftServer.LOGGER.info("Unfortunately due to the way that Minecraft implemented multiworld support in 1.6, Bukkit requires that you move your " + worldType + " folder to a new location in order to operate correctly.");
                    MinecraftServer.LOGGER.info("We will move this folder for you, but it will mean that you need to move it back should you wish to stop using Bukkit in the future.");
                    MinecraftServer.LOGGER.info("Attempting to move " + oldWorld + " to " + newWorld + "...");

                    if (newWorld.exists()) {
                        MinecraftServer.LOGGER.warn("A file or folder already exists at " + newWorld + "!");
                        MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder failed ----");
                    } else if (newWorld.getParentFile().mkdirs()) {
                        if (oldWorld.renameTo(newWorld)) {
                            MinecraftServer.LOGGER.info("Success! To restore " + worldType + " in the future, simply move " + newWorld + " to " + oldWorld);
                            // Migrate world data too.
                            try {
                                com.google.common.io.Files.copy(oldLevelDat, new java.io.File(new java.io.File(name), "level.dat"));
                                org.apache.commons.io.FileUtils.copyDirectory(new java.io.File(new java.io.File(levelId), "data"), new java.io.File(new java.io.File(name), "data"));
                            } catch (IOException exception) {
                                MinecraftServer.LOGGER.warn("Unable to migrate world data.");
                            }
                            MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder complete ----");
                        } else {
                            MinecraftServer.LOGGER.warn("Could not move folder " + oldWorld + " to " + newWorld + "!");
                            MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder failed ----");
                        }
                    } else {
                        MinecraftServer.LOGGER.warn("Could not create path for " + newWorld + "!");
                        MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder failed ----");
                    }
                }

                try {
                    levelStorageAccess = LevelStorageSource.createDefault(this.server.getWorldContainer().toPath()).validateAndCreateAccess(name, levelStemKey);
                } catch (IOException | net.minecraft.world.level.validation.ContentValidationException ex) {
                    throw new RuntimeException(ex);
                }
            }

            com.mojang.serialization.Dynamic<?> dataTag;
            if (levelStorageAccess.hasWorldData()) {
                net.minecraft.world.level.storage.LevelSummary summary;
                try {
                    dataTag = levelStorageAccess.getDataTag();
                    summary = levelStorageAccess.getSummary(dataTag);
                } catch (net.minecraft.nbt.NbtException | net.minecraft.nbt.ReportedNbtException | IOException e) {
                    LevelStorageSource.LevelDirectory levelDirectory = levelStorageAccess.getLevelDirectory();
                    MinecraftServer.LOGGER.warn("Failed to load world data from {}", levelDirectory.dataFile(), e);
                    MinecraftServer.LOGGER.info("Attempting to use fallback");

                    try {
                        dataTag = levelStorageAccess.getDataTagFallback();
                        summary = levelStorageAccess.getSummary(dataTag);
                    } catch (net.minecraft.nbt.NbtException | net.minecraft.nbt.ReportedNbtException | IOException e1) {
                        MinecraftServer.LOGGER.error("Failed to load world data from {}", levelDirectory.oldDataFile(), e1);
                        MinecraftServer.LOGGER.error(
                            "Failed to load world data from {} and {}. World files may be corrupted. Shutting down.",
                            levelDirectory.dataFile(),
                            levelDirectory.oldDataFile()
                        );
                        return;
                    }

                    levelStorageAccess.restoreLevelDataFromOld();
                }

                if (summary.requiresManualConversion()) {
                    MinecraftServer.LOGGER.info("This world must be opened in an older version (like 1.6.4) to be safely converted");
                    return;
                }

                if (!summary.isCompatible()) {
                    MinecraftServer.LOGGER.info("This world was created by an incompatible version.");
                    return;
                }
            } else {
                dataTag = null;
            }

            org.bukkit.generator.ChunkGenerator chunkGenerator = this.server.getGenerator(name);
            org.bukkit.generator.BiomeProvider biomeProvider = this.server.getBiomeProvider(name);

            net.minecraft.world.level.storage.PrimaryLevelData primaryLevelData;
            WorldLoader.DataLoadContext context = this.worldLoader;
            Registry<LevelStem> contextLevelStemRegistry = context.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM);
            if (dataTag != null) {
                net.minecraft.world.level.storage.LevelDataAndDimensions levelDataAndDimensions = LevelStorageSource.getLevelDataAndDimensions(
                    dataTag, context.dataConfiguration(), contextLevelStemRegistry, context.datapackWorldgen()
                );
                primaryLevelData = (net.minecraft.world.level.storage.PrimaryLevelData) levelDataAndDimensions.worldData();
            } else {
                LevelSettings levelSettings;
                WorldOptions worldOptions;
                net.minecraft.world.level.levelgen.WorldDimensions worldDimensions;
                if (this.isDemo()) {
                    levelSettings = MinecraftServer.DEMO_SETTINGS;
                    worldOptions = WorldOptions.DEMO_OPTIONS;
                    worldDimensions = net.minecraft.world.level.levelgen.presets.WorldPresets.createNormalWorldDimensions(context.datapackWorldgen());
                } else {
                    net.minecraft.server.dedicated.DedicatedServerProperties properties = ((net.minecraft.server.dedicated.DedicatedServer) this).getProperties();
                    levelSettings = new LevelSettings(
                        properties.levelName,
                        properties.gamemode,
                        properties.hardcore,
                        properties.difficulty,
                        false,
                        new GameRules(context.dataConfiguration().enabledFeatures()),
                        context.dataConfiguration()
                    );
                    worldOptions = this.options.has("bonusChest") ? properties.worldOptions.withBonusChest(true) : properties.worldOptions; // CraftBukkit
                    worldDimensions = properties.createDimensions(context.datapackWorldgen());
                }

                net.minecraft.world.level.levelgen.WorldDimensions.Complete complete = worldDimensions.bake(contextLevelStemRegistry);
                com.mojang.serialization.Lifecycle lifecycle = complete.lifecycle().add(context.datapackWorldgen().allRegistriesLifecycle());

                primaryLevelData = new net.minecraft.world.level.storage.PrimaryLevelData(levelSettings, worldOptions, complete.specialWorldProperty(), lifecycle);
            }

            primaryLevelData.checkName(name); // CraftBukkit - Migration did not rewrite the level.dat; This forces 1.8 to take the last loaded world as respawn (in this case the end)
            if (this.options.has("forceUpgrade")) {
                net.minecraft.server.Main.forceUpgrade(levelStorageAccess, net.minecraft.util.datafix.DataFixers.getDataFixer(), this.options.has("eraseCache"), () -> true, registryAccess, this.options.has("recreateRegionFiles"));
            }

            // Now modelled off the createLevels method
            net.minecraft.world.level.storage.PrimaryLevelData serverLevelData = primaryLevelData;
            boolean isDebugWorld = primaryLevelData.isDebugWorld();
            WorldOptions worldOptions = primaryLevelData.worldGenOptions();
            long seed = worldOptions.seed();
            long l = BiomeManager.obfuscateSeed(seed);
            List<CustomSpawner> list = ImmutableList.of(
                new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(serverLevelData)
            );
            LevelStem customStem = levelStemRegistry.getValue(levelStemKey);

            org.bukkit.generator.WorldInfo worldInfo = new org.bukkit.craftbukkit.generator.CraftWorldInfo(serverLevelData, levelStorageAccess, org.bukkit.World.Environment.getEnvironment(dimension), customStem.type().value(), customStem.generator(), this.registryAccess()); // Paper - Expose vanilla BiomeProvider from WorldInfo
            if (biomeProvider == null && chunkGenerator != null) {
                biomeProvider = chunkGenerator.getDefaultBiomeProvider(worldInfo);
            }

            ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, levelStemKey.location());

            if (levelStemKey == LevelStem.OVERWORLD) {
                this.worldData = primaryLevelData;
                this.worldData.setGameType(((net.minecraft.server.dedicated.DedicatedServer) this).getProperties().gamemode); // From DedicatedServer.init

                ChunkProgressListener listener = this.progressListenerFactory.create(this.worldData.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS));

                serverLevel = new ServerLevel(
                    this, this.executor, levelStorageAccess, serverLevelData, dimensionKey, customStem, listener, isDebugWorld, l, list, true, null,
                    org.bukkit.World.Environment.getEnvironment(dimension), chunkGenerator, biomeProvider
                );
                DimensionDataStorage dataStorage = serverLevel.getDataStorage();
                this.readScoreboard(dataStorage);
                this.commandStorage = new CommandStorage(dataStorage);
                this.server.scoreboardManager = new org.bukkit.craftbukkit.scoreboard.CraftScoreboardManager(this, serverLevel.getScoreboard());
            } else {
                ChunkProgressListener listener = this.progressListenerFactory.create(this.worldData.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS));
                // Paper start - option to use the dimension_type to check if spawners should be added. I imagine mojang will add some datapack-y way of managing this in the future.
                final List<CustomSpawner> spawners;
                if (io.papermc.paper.configuration.GlobalConfiguration.get().misc.useDimensionTypeForCustomSpawners && this.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE).getResourceKey(customStem.type().value()).orElseThrow() == net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD) {
                    spawners = list;
                } else {
                    spawners = Collections.emptyList();
                }
                serverLevel = new ServerLevel(
                    this, this.executor, levelStorageAccess, serverLevelData, dimensionKey, customStem, listener, isDebugWorld, l, spawners, true, this.overworld().getRandomSequences(),
                    org.bukkit.World.Environment.getEnvironment(dimension), chunkGenerator, biomeProvider
                );
                // Paper end - option to use the dimension_type to check if spawners should be added
            }

            // Back to the createLevels method without crazy modifications
            primaryLevelData.setModdedInfo(this.getServerModName(), this.getModdedStatus().shouldReportAsModified());
            this.addLevel(serverLevel); // Paper - Put world into worldlist before initing the world; move up
            this.initWorld(serverLevel, primaryLevelData, this.worldData, worldOptions);

            // Paper - Put world into worldlist before initing the world; move up
            this.getPlayerList().addWorldborderListener(serverLevel);

            if (primaryLevelData.getCustomBossEvents() != null) {
                this.getCustomBossEvents().load(primaryLevelData.getCustomBossEvents(), this.registryAccess());
            }
        }
        this.forceDifficulty();
        for (ServerLevel serverLevel : this.getAllLevels()) {
            this.prepareLevels(serverLevel.getChunkSource().chunkMap.progressListener, serverLevel);
            // Paper - rewrite chunk system
            this.server.getPluginManager().callEvent(new org.bukkit.event.world.WorldLoadEvent(serverLevel.getWorld()));
        }

        // Paper start - Configurable player collision; Handle collideRule team for player collision toggle
        final ServerScoreboard scoreboard = this.getScoreboard();
        final java.util.Collection<String> toRemove = scoreboard.getPlayerTeams().stream().filter(team -> team.getName().startsWith("collideRule_")).map(net.minecraft.world.scores.PlayerTeam::getName).collect(java.util.stream.Collectors.toList());
        for (String teamName : toRemove) {
            scoreboard.removePlayerTeam(scoreboard.getPlayerTeam(teamName)); // Clean up after ourselves
        }

        if (!io.papermc.paper.configuration.GlobalConfiguration.get().collisions.enablePlayerCollisions) {
            this.getPlayerList().collideRuleTeamName = org.apache.commons.lang3.StringUtils.left("collideRule_" + java.util.concurrent.ThreadLocalRandom.current().nextInt(), 16);
            net.minecraft.world.scores.PlayerTeam collideTeam = scoreboard.addPlayerTeam(this.getPlayerList().collideRuleTeamName);
            collideTeam.setSeeFriendlyInvisibles(false); // Because we want to mimic them not being on a team at all
        }
        // Paper end - Configurable player collision

        this.server.enablePlugins(org.bukkit.plugin.PluginLoadOrder.POSTWORLD);
        this.server.spark.registerCommandBeforePlugins(this.server); // Paper - spark
        this.server.spark.enableAfterPlugins(this.server); // Paper - spark
        if (io.papermc.paper.plugin.PluginInitializerManager.instance().pluginRemapper != null) io.papermc.paper.plugin.PluginInitializerManager.instance().pluginRemapper.pluginsEnabled(); // Paper - Remap plugins
        io.papermc.paper.command.brigadier.PaperCommands.INSTANCE.setValid(); // Paper - reset invalid state for event fire below
        io.papermc.paper.plugin.lifecycle.event.LifecycleEventRunner.INSTANCE.callReloadableRegistrarEvent(io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS, io.papermc.paper.command.brigadier.PaperCommands.INSTANCE, org.bukkit.plugin.Plugin.class, io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause.INITIAL); // Paper - call commands event for regular plugins
        ((org.bukkit.craftbukkit.help.SimpleHelpMap) this.server.getHelpMap()).initializeCommands();
        this.server.getPluginManager().callEvent(new org.bukkit.event.server.ServerLoadEvent(org.bukkit.event.server.ServerLoadEvent.LoadType.STARTUP));
        this.connection.acceptConnections();
    }

    public void initWorld(ServerLevel serverLevel, ServerLevelData serverLevelData, WorldData saveData, WorldOptions worldOptions) {
        boolean isDebugWorld = saveData.isDebugWorld();
        if (serverLevel.generator != null) {
            serverLevel.getWorld().getPopulators().addAll(serverLevel.generator.getDefaultPopulators(serverLevel.getWorld()));
        }
        // CraftBukkit start
        WorldBorder worldborder = serverLevel.getWorldBorder();
        worldborder.applySettings(serverLevelData.getWorldBorder()); // CraftBukkit - move up so that WorldBorder is set during WorldInitEvent
        this.server.getPluginManager().callEvent(new org.bukkit.event.world.WorldInitEvent(serverLevel.getWorld())); // CraftBukkit - SPIGOT-5569: Call WorldInitEvent before any chunks are generated

        if (!serverLevelData.isInitialized()) {
            try {
                setInitialSpawn(serverLevel, serverLevelData, worldOptions.generateBonusChest(), isDebugWorld);
                serverLevelData.setInitialized(true);
                if (isDebugWorld) {
                    this.setupDebugLevel(this.worldData);
                }
            } catch (Throwable var23) {
                CrashReport crashReport = CrashReport.forThrowable(var23, "Exception initializing level");

                try {
                    serverLevel.fillReportDetails(crashReport);
                } catch (Throwable var22) {
                }

                throw new ReportedException(crashReport);
            }

            serverLevelData.setInitialized(true);
        }
    }
    // CraftBukkit end

    private static void setInitialSpawn(ServerLevel level, ServerLevelData levelData, boolean generateBonusChest, boolean debug) {
        if (debug) {
            levelData.setSpawn(BlockPos.ZERO.above(80), 0.0F);
        } else {
            ServerChunkCache chunkSource = level.getChunkSource();
            // CraftBukkit start
            if (level.generator != null) {
                java.util.Random rand = new java.util.Random(level.getSeed());
                org.bukkit.Location spawn = level.generator.getFixedSpawnLocation(level.getWorld(), rand);

                if (spawn != null) {
                    if (spawn.getWorld() != level.getWorld()) {
                        throw new IllegalStateException("Cannot set spawn point for " + levelData.getLevelName() + " to be in another world (" + spawn.getWorld().getName() + ")");
                    } else {
                        levelData.setSpawn(new BlockPos(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ()), spawn.getYaw());
                        return;
                    }
                }
            }
            // CraftBukkit end
            ChunkPos chunkPos = new ChunkPos(chunkSource.randomState().sampler().findSpawnPosition()); // Paper - Only attempt to find spawn position if there isn't a fixed spawn position set
            int spawnHeight = chunkSource.getGenerator().getSpawnHeight(level);
            if (spawnHeight < level.getMinY()) {
                BlockPos worldPosition = chunkPos.getWorldPosition();
                spawnHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldPosition.getX() + 8, worldPosition.getZ() + 8);
            }

            levelData.setSpawn(chunkPos.getWorldPosition().offset(8, spawnHeight, 8), 0.0F);
            int i = 0;
            int i1 = 0;
            int i2 = 0;
            int i3 = -1;

            for (int i4 = 0; i4 < Mth.square(11); i4++) {
                if (i >= -5 && i <= 5 && i1 >= -5 && i1 <= 5) {
                    BlockPos spawnPosInChunk = PlayerRespawnLogic.getSpawnPosInChunk(level, new ChunkPos(chunkPos.x + i, chunkPos.z + i1));
                    if (spawnPosInChunk != null) {
                        levelData.setSpawn(spawnPosInChunk, 0.0F);
                        break;
                    }
                }

                if (i == i1 || i < 0 && i == -i1 || i > 0 && i == 1 - i1) {
                    int i5 = i2;
                    i2 = -i3;
                    i3 = i5;
                }

                i += i2;
                i1 += i3;
            }

            if (generateBonusChest) {
                level.registryAccess()
                    .lookup(Registries.CONFIGURED_FEATURE)
                    .flatMap(registry -> registry.get(MiscOverworldFeatures.BONUS_CHEST))
                    .ifPresent(holder -> holder.value().place(level, chunkSource.getGenerator(), level.random, levelData.getSpawnPos()));
            }
        }
    }

    private void setupDebugLevel(WorldData worldData) {
        worldData.setDifficulty(Difficulty.PEACEFUL);
        worldData.setDifficultyLocked(true);
        ServerLevelData serverLevelData = worldData.overworldData();
        serverLevelData.setRaining(false);
        serverLevelData.setThundering(false);
        serverLevelData.setClearWeatherTime(1000000000);
        serverLevelData.setDayTime(6000L);
        serverLevelData.setGameType(GameType.SPECTATOR);
    }

    // CraftBukkit start
    public void prepareLevels(ChunkProgressListener listener, ServerLevel serverLevel) {
        this.forceTicks = true;
        // CraftBukkit end
        LOGGER.info("Preparing start region for dimension {}", serverLevel.dimension().location());
        BlockPos sharedSpawnPos = serverLevel.getSharedSpawnPos();
        listener.updateSpawnPos(new ChunkPos(sharedSpawnPos));
        ServerChunkCache chunkSource = serverLevel.getChunkSource();
        this.nextTickTimeNanos = Util.getNanos();
        serverLevel.setDefaultSpawnPos(sharedSpawnPos, serverLevel.getSharedSpawnAngle());
        int _int = serverLevel.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS); // CraftBukkit - per-world
        int i = _int > 0 ? Mth.square(ChunkProgressListener.calculateDiameter(_int)) : 0;

        while (chunkSource.getTickingGenerated() < i) {
            // CraftBukkit start
            // this.nextTickTimeNanos = Util.getNanos() + PREPARE_LEVELS_DEFAULT_DELAY_NANOS;
            this.executeModerately();
        }

        // this.nextTickTimeNanos = Util.getNanos() + PREPARE_LEVELS_DEFAULT_DELAY_NANOS;
        this.executeModerately();

        if (true) {
            ServerLevel serverLevel1 = serverLevel;
            // CraftBukkit end
            ForcedChunksSavedData forcedChunksSavedData = serverLevel1.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");
            if (forcedChunksSavedData != null) {
                LongIterator longIterator = forcedChunksSavedData.getChunks().iterator();

                while (longIterator.hasNext()) {
                    long l = longIterator.nextLong();
                    ChunkPos chunkPos = new ChunkPos(l);
                    serverLevel1.getChunkSource().updateChunkForced(chunkPos, true);
                }
            }
        }

        // CraftBukkit start
        // this.nextTickTimeNanos = SystemUtils.getNanos() + MinecraftServer.PREPARE_LEVELS_DEFAULT_DELAY_NANOS;
        this.executeModerately();
        // CraftBukkit end
        listener.stop();
        // CraftBukkit start
        // this.updateMobSpawningFlags();
        serverLevel.setSpawnSettings(serverLevel.serverLevelData.getDifficulty() != Difficulty.PEACEFUL && ((net.minecraft.server.dedicated.DedicatedServer) this).settings.getProperties().spawnMonsters); // Paper - per level difficulty (from setDifficulty(ServerLevel, Difficulty, boolean))

        this.forceTicks = false;
        // CraftBukkit end
    }

    public GameType getDefaultGameType() {
        return this.worldData.getGameType();
    }

    public boolean isHardcore() {
        return this.worldData.isHardcore();
    }

    public abstract int getOperatorUserPermissionLevel();

    public abstract int getFunctionCompilationLevel();

    public abstract boolean shouldRconBroadcast();

    public boolean saveAllChunks(boolean suppressLog, boolean flush, boolean forced) {
        // Paper start - add close param
        return this.saveAllChunks(suppressLog, flush, forced, false);
    }
    public boolean saveAllChunks(boolean suppressLog, boolean flush, boolean forced, boolean close) {
        // Paper end - add close param
        boolean flag = false;

        for (ServerLevel serverLevel : this.getAllLevels()) {
            if (!suppressLog) {
                LOGGER.info("Saving chunks for level '{}'/{}", serverLevel, serverLevel.dimension().location());
            }

            serverLevel.save(null, flush, serverLevel.noSave && !forced, close); // Paper - add close param
            flag = true;
        }

        /* // CraftBukkit start - moved to WorldServer.save
        ServerLevel serverLevel1 = this.overworld();
        ServerLevelData serverLevelData = this.worldData.overworldData();
        serverLevelData.setWorldBorder(serverLevel1.getWorldBorder().createSettings());
        this.worldData.setCustomBossEvents(this.getCustomBossEvents().save(this.registryAccess()));
        this.storageSource.saveDataTag(this.registryAccess(), this.worldData, this.getPlayerList().getSingleplayerData());
         */
        // CraftBukkit end
        if (flush) {
            for (ServerLevel serverLevel2 : this.getAllLevels()) {
                LOGGER.info("ThreadedAnvilChunkStorage ({}): All chunks are saved", serverLevel2.getChunkSource().chunkMap.getStorageName());
            }

            LOGGER.info("ThreadedAnvilChunkStorage: All dimensions are saved");
        }

        return flag;
    }

    public boolean saveEverything(boolean suppressLog, boolean flush, boolean forced) {
        boolean var4;
        try {
            this.isSaving = true;
            this.getPlayerList().saveAll(); // Paper - Incremental chunk and player saving; diff on change
            var4 = this.saveAllChunks(suppressLog, flush, forced);
        } finally {
            this.isSaving = false;
        }

        return var4;
    }

    @Override
    public void close() {
        this.stopServer();
    }

    // CraftBukkit start
    private boolean hasStopped = false;
    private boolean hasLoggedStop = false; // Paper - Debugging
    private final Object stopLock = new Object();
    public final boolean hasStopped() {
        synchronized (this.stopLock) {
            return this.hasStopped;
        }
    }
    // CraftBukkit end

    public void stopServer() {
        // CraftBukkit start - prevent double stopping on multiple threads
        synchronized(this.stopLock) {
            if (this.hasStopped) return;
            this.hasStopped = true;
        }
        if (!hasLoggedStop && isDebugging()) io.papermc.paper.util.TraceUtil.dumpTraceForThread("Server stopped"); // Paper - Debugging
        shutdownThread = Thread.currentThread(); // Paper - Improved watchdog support
        org.spigotmc.WatchdogThread.doStop(); // Paper - Improved watchdog support
        // CraftBukkit end
        if (this.metricsRecorder.isRecording()) {
            this.cancelRecordingMetrics();
        }

        LOGGER.info("Stopping server");
        Commands.COMMAND_SENDING_POOL.shutdownNow(); // Paper - Perf: Async command map building; Shutdown and don't bother finishing
        // Purpur start - UPnP Port Forwarding
        if (upnp) {
            if (dev.omega24.upnp4j.UPnP4J.close(this.getPort(), dev.omega24.upnp4j.util.Protocol.TCP)) {
                LOGGER.info("[UPnP] Port {} closed", this.getPort());
            } else {
                LOGGER.error("[UPnP] Failed to close port {}", this.getPort());
            }
        }
        // Purpur end - UPnP Port Forwarding
        // CraftBukkit start
        if (this.server != null) {
            this.server.spark.disable(); // Paper - spark
            this.server.disablePlugins();
            this.server.waitForAsyncTasksShutdown(); // Paper - Wait for Async Tasks during shutdown
        }
        // CraftBukkit end
        if (io.papermc.paper.plugin.PluginInitializerManager.instance().pluginRemapper != null) io.papermc.paper.plugin.PluginInitializerManager.instance().pluginRemapper.shutdown(); // Paper - Plugin remapping
        this.getConnection().stop();
        this.isSaving = true;
        if (this.playerList != null) {
            LOGGER.info("Saving players");
            this.playerList.saveAll();
            this.playerList.removeAll(this.isRestarting); // Paper
            try { Thread.sleep(100); } catch (InterruptedException ex) {} // CraftBukkit - SPIGOT-625 - give server at least a chance to send packets
        }

        LOGGER.info("Saving worlds");

        for (ServerLevel serverLevel : this.getAllLevels()) {
            if (serverLevel != null) {
                serverLevel.noSave = false;
            }
        }

        while (false && this.levels.values().stream().anyMatch(level -> level.getChunkSource().chunkMap.hasWork())) { // Paper - rewrite chunk system
            this.nextTickTimeNanos = Util.getNanos() + TimeUtil.NANOSECONDS_PER_MILLISECOND;

            for (ServerLevel serverLevelx : this.getAllLevels()) {
                serverLevelx.getChunkSource().removeTicketsOnClosing();
                serverLevelx.getChunkSource().tick(() -> true, false);
            }

            this.waitUntilNextTick();
        }

        this.saveAllChunks(false, true, false, true); // Paper - rewrite chunk system

        this.isSaving = false;
        this.resources.close();

        try {
            this.storageSource.close();
        } catch (IOException var4) {
            LOGGER.error("Failed to unlock level {}", this.storageSource.getLevelId(), var4);
        }
        // Spigot start
        io.papermc.paper.util.MCUtil.ASYNC_EXECUTOR.shutdown(); // Paper
        try {
            io.papermc.paper.util.MCUtil.ASYNC_EXECUTOR.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS); // Paper
        } catch (java.lang.InterruptedException ignored) {} // Paper
        if (org.spigotmc.SpigotConfig.saveUserCacheOnStopOnly) {
            LOGGER.info("Saving usercache.json");
            this.getProfileCache().save(false); // Paper - Perf: Async GameProfileCache saving
        }
        // Spigot end
        // Paper start - rewrite chunk system
        LOGGER.info("Waiting for I/O tasks to complete...");
        ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.flush((MinecraftServer)(Object)this);
        LOGGER.info("All I/O tasks to complete");
        if ((Object)this instanceof net.minecraft.server.dedicated.DedicatedServer) {
            ca.spottedleaf.moonrise.common.util.MoonriseCommon.haltExecutors();
        }
        // Paper end - rewrite chunk system
        // Paper start - Improved watchdog support - move final shutdown items here
        Util.shutdownExecutors();
        try {
            net.minecrell.terminalconsole.TerminalConsoleAppender.close(); // Paper - Use TerminalConsoleAppender
        } catch (final Exception ignored) {
        }
        io.papermc.paper.log.CustomLogManager.forceReset(); // Paper - Reset loggers after shutdown
        this.onServerExit();
        // Paper end - Improved watchdog support - move final shutdown items here
    }

    public String getLocalIp() {
        return this.localIp;
    }

    public void setLocalIp(String localIp) {
        this.localIp = localIp;
    }

    public boolean isRunning() {
        return this.running;
    }

    public void halt(boolean waitForServer) {
        // Paper start - allow passing of the intent to restart
        this.safeShutdown(waitForServer, false);
    }
    public void safeShutdown(boolean waitForServer, boolean isRestarting) {
        org.purpurmc.purpur.task.BossBarTask.stopAll(); // Purpur - Implement TPSBar
        org.purpurmc.purpur.task.BeehiveTask.instance().unregister(); // Purpur - Give bee counts in beehives to Purpur clients
        this.isRestarting = isRestarting;
        this.hasLoggedStop = true; // Paper - Debugging
        if (isDebugging()) io.papermc.paper.util.TraceUtil.dumpTraceForThread("Server stopped"); // Paper - Debugging
        // Paper end
        this.running = false;
        if (waitForServer) {
            try {
                this.serverThread.join();
            } catch (InterruptedException var3) {
                LOGGER.error("Error while shutting down", (Throwable)var3);
            }
        }
    }

    // Paper start - Further improve server tick loop
    private static final long SEC_IN_NANO = 1000000000;
    private static final long MAX_CATCHUP_BUFFER = TICK_TIME * TPS * 60L;
    private long lastTick = 0;
    private long catchupTime = 0;
    public final RollingAverage tps5s = new RollingAverage(5); // Purpur - Add 5 second tps average in /tps
    public final RollingAverage tps1 = new RollingAverage(60);
    public final RollingAverage tps5 = new RollingAverage(60 * 5);
    public final RollingAverage tps15 = new RollingAverage(60 * 15);

    public static class RollingAverage {
        private final int size;
        private long time;
        private java.math.BigDecimal total;
        private int index = 0;
        private final java.math.BigDecimal[] samples;
        private final long[] times;

        RollingAverage(int size) {
            this.size = size;
            this.time = size * SEC_IN_NANO;
            this.total = dec(TPS).multiply(dec(SEC_IN_NANO)).multiply(dec(size));
            this.samples = new java.math.BigDecimal[size];
            this.times = new long[size];
            for (int i = 0; i < size; i++) {
                this.samples[i] = dec(TPS);
                this.times[i] = SEC_IN_NANO;
            }
        }

        private static java.math.BigDecimal dec(long t) {
            return new java.math.BigDecimal(t);
        }
        public void add(java.math.BigDecimal x, long t) {
            time -= times[index];
            total = total.subtract(samples[index].multiply(dec(times[index])));
            samples[index] = x;
            times[index] = t;
            time += t;
            total = total.add(x.multiply(dec(t)));
            if (++index == size) {
                index = 0;
            }
        }

        public double getAverage() {
            return total.divide(dec(time), 30, java.math.RoundingMode.HALF_UP).doubleValue();
        }
    }
    private static final java.math.BigDecimal TPS_BASE = new java.math.BigDecimal(1E9).multiply(new java.math.BigDecimal(SAMPLE_INTERVAL));
    // Paper end

    protected void runServer() {
        try {
            if (!this.initServer()) {
                throw new IllegalStateException("Failed to initialize server");
            }

            this.nextTickTimeNanos = Util.getNanos();
            this.statusIcon = this.loadStatusIcon().orElse(null);
            this.status = this.buildServerStatus();

            this.server.spark.enableBeforePlugins(); // Paper - spark
            // Spigot start
            // Paper start
            LOGGER.info("Running delayed init tasks");
            this.server.getScheduler().mainThreadHeartbeat(); // run all 1 tick delay tasks during init,
            // this is going to be the first thing the tick process does anyways, so move done and run it after
            // everything is init before watchdog tick.
            // anything at 3+ won't be caught here but also will trip watchdog....
            // tasks are default scheduled at -1 + delay, and first tick will tick at 1
            final long actualDoneTimeMs = System.currentTimeMillis() - org.bukkit.craftbukkit.Main.BOOT_TIME.toEpochMilli(); // Paper - Improve startup message
            LOGGER.info("Done ({})! For help, type \"help\"", String.format(java.util.Locale.ROOT, "%.3fs", actualDoneTimeMs / 1000.00D)); // Paper - Improve startup message
            org.spigotmc.WatchdogThread.tick();
            // Paper end
            org.spigotmc.WatchdogThread.hasStarted = true; // Paper
            Arrays.fill(this.recentTps, 20);
            // Paper start - further improve server tick loop
            long tickSection = Util.getNanos();
            long currentTime;
            // Paper end - further improve server tick loop
            // Paper start - Add onboarding message for initial server start
            if (io.papermc.paper.configuration.GlobalConfiguration.isFirstStart) {
                LOGGER.info("*************************************************************************************");
                LOGGER.info("This is the first time you're starting this server.");
                LOGGER.info("It's recommended you read our 'Getting Started' documentation for guidance.");
                LOGGER.info("View this and more helpful information here: https://docs.papermc.io/paper/next-steps");
                LOGGER.info("*************************************************************************************");
            }
            // Paper end - Add onboarding message for initial server start

            // Purpur start - config for startup commands
            if (!Boolean.getBoolean("Purpur.IReallyDontWantStartupCommands") && !org.purpurmc.purpur.PurpurConfig.startupCommands.isEmpty()) {
                LOGGER.info("Purpur: Running startup commands specified in purpur.yml.");
                for (final String startupCommand : org.purpurmc.purpur.PurpurConfig.startupCommands) {
                    LOGGER.info("Purpur: Running the following command: \"{}\"", startupCommand);
                    ((net.minecraft.server.dedicated.DedicatedServer) this).handleConsoleInput(startupCommand, this.createCommandSourceStack());
                }
            }
            // Purpur end - config for startup commands

            while (this.running) {
                long l;
                if (!this.isPaused() && this.tickRateManager.isSprinting() && this.tickRateManager.checkShouldSprintThisTick()) {
                    l = 0L;
                    this.nextTickTimeNanos = Util.getNanos();
                    this.lastOverloadWarningNanos = this.nextTickTimeNanos;
                } else {
                    l = this.tickRateManager.nanosecondsPerTick();
                    long l1 = Util.getNanos() - this.nextTickTimeNanos;
                    if (l1 > OVERLOADED_THRESHOLD_NANOS + 20L * l
                        && this.nextTickTimeNanos - this.lastOverloadWarningNanos >= OVERLOADED_WARNING_INTERVAL_NANOS + 100L * l) {
                        long l2 = l1 / l;
                        if (this.server.getWarnOnOverload()) // CraftBukkit
                        LOGGER.warn("Can't keep up! Is the server overloaded? Running {}ms or {} ticks behind", l1 / TimeUtil.NANOSECONDS_PER_MILLISECOND, l2);
                        this.nextTickTimeNanos += l2 * l;
                        this.lastOverloadWarningNanos = this.nextTickTimeNanos;
                    }
                }
                // Spigot start
                // Paper start - further improve server tick loop
                currentTime = Util.getNanos();
                if (++MinecraftServer.currentTick % MinecraftServer.SAMPLE_INTERVAL == 0) {
                    final long diff = currentTime - tickSection;
                    final java.math.BigDecimal currentTps = TPS_BASE.divide(new java.math.BigDecimal(diff), 30, java.math.RoundingMode.HALF_UP);
                    tps5s.add(currentTps, diff); // Purpur - Add 5 second tps average in /tps
                    tps1.add(currentTps, diff);
                    tps5.add(currentTps, diff);
                    tps15.add(currentTps, diff);

                    // Backwards compat with bad plugins
                    // Purpur start - Add 5 second tps average in /tps
                    this.recentTps[0] = tps5s.getAverage();
                    this.recentTps[1] = tps1.getAverage();
                    this.recentTps[2] = tps5.getAverage();
                    this.recentTps[3] = tps15.getAverage();
                    // Purpur end - Add 5 second tps average in /tps
                    lagging = recentTps[0] < org.purpurmc.purpur.PurpurConfig.laggingThreshold; // Purpur - Lagging threshold
                    tickSection = currentTime;
                }
                // Paper end - further improve server tick loop
                // Spigot end

                boolean flag = l == 0L;
                if (this.debugCommandProfilerDelayStart) {
                    this.debugCommandProfilerDelayStart = false;
                    this.debugCommandProfiler = new MinecraftServer.TimeProfiler(Util.getNanos(), this.tickCount);
                }

                //MinecraftServer.currentTick = (int) (System.currentTimeMillis() / 50); // CraftBukkit // Paper - don't overwrite current tick time
                lastTick = currentTime;
                this.nextTickTimeNanos += l;

                try (Profiler.Scope scope = Profiler.use(this.createProfiler())) {
                    ProfilerFiller profilerFiller = Profiler.get();
                    profilerFiller.push("tick");
                    this.tickFrame.start();
                    this.tickServer(flag ? () -> false : this::haveTime);
                    // Paper start - rewrite chunk system
                    final Throwable crash = this.chunkSystemCrash;
                    if (crash != null) {
                        this.chunkSystemCrash = null;
                        throw new RuntimeException("Chunk system crash propagated to tick()", crash);
                    }
                    // Paper end - rewrite chunk system
                    this.tickFrame.end();
                    profilerFiller.popPush("nextTickWait");
                    this.mayHaveDelayedTasks = true;
                    this.delayedTasksMaxNextTickTimeNanos = Math.max(Util.getNanos() + l, this.nextTickTimeNanos);
                    // Purpur start - Configurable TPS Catchup
                    if (!org.purpurmc.purpur.PurpurConfig.tpsCatchup /*|| !gg.pufferfish.pufferfish.PufferfishConfig.tpsCatchup*/) { // Purpur - Configurable TPS Catchup
                        this.nextTickTimeNanos = currentTime + l;
                        this.delayedTasksMaxNextTickTimeNanos = nextTickTimeNanos;
                    }
                    // Purpur end - Configurable TPS Catchup
                    this.startMeasuringTaskExecutionTime();
                    this.waitUntilNextTick();
                    this.finishMeasuringTaskExecutionTime();
                    if (flag) {
                        this.tickRateManager.endTickWork();
                    }

                    profilerFiller.pop();
                    this.logFullTickTime();
                } finally {
                    this.endMetricsRecordingTick();
                }

                this.isReady = true;
                JvmProfiler.INSTANCE.onServerTick(this.smoothedTickTimeMillis);
            }
        } catch (Throwable var69) {
            LOGGER.error("Encountered an unexpected exception", var69);
            CrashReport crashReport = constructOrExtractCrashReport(var69);
            this.fillSystemReport(crashReport.getSystemReport());
            Path path = this.getServerDirectory().resolve("crash-reports").resolve("crash-" + Util.getFilenameFormattedDateTime() + "-server.txt");
            if (crashReport.saveToFile(path, ReportType.CRASH)) {
                LOGGER.error("This crash report has been saved to: {}", path.toAbsolutePath());
            } else {
                LOGGER.error("We were unable to save this crash report to disk.");
            }

            this.onServerCrash(crashReport);
        } finally {
            try {
                this.stopped = true;
                this.stopServer();
            } catch (Throwable var64) {
                LOGGER.error("Exception stopping the server", var64);
            } finally {
                if (this.services.profileCache() != null) {
                    this.services.profileCache().clearExecutor();
                }

                //this.onServerExit(); // Paper - Improved watchdog support; moved into stop
            }
        }
    }

    private void logFullTickTime() {
        long nanos = Util.getNanos();
        if (this.isTickTimeLoggingEnabled()) {
            this.getTickTimeLogger().logSample(nanos - this.lastTickNanos);
        }

        this.lastTickNanos = nanos;
    }

    private void startMeasuringTaskExecutionTime() {
        if (this.isTickTimeLoggingEnabled()) {
            this.taskExecutionStartNanos = Util.getNanos();
            this.idleTimeNanos = 0L;
        }
    }

    private void finishMeasuringTaskExecutionTime() {
        if (this.isTickTimeLoggingEnabled()) {
            SampleLogger tickTimeLogger = this.getTickTimeLogger();
            tickTimeLogger.logPartialSample(Util.getNanos() - this.taskExecutionStartNanos - this.idleTimeNanos, TpsDebugDimensions.SCHEDULED_TASKS.ordinal());
            tickTimeLogger.logPartialSample(this.idleTimeNanos, TpsDebugDimensions.IDLE.ordinal());
        }
    }

    private static CrashReport constructOrExtractCrashReport(Throwable cause) {
        ReportedException reportedException = null;

        for (Throwable throwable = cause; throwable != null; throwable = throwable.getCause()) {
            if (throwable instanceof ReportedException reportedException1) {
                reportedException = reportedException1;
            }
        }

        CrashReport report;
        if (reportedException != null) {
            report = reportedException.getReport();
            if (reportedException != cause) {
                report.addCategory("Wrapped in").setDetailError("Wrapping exception", cause);
            }
        } else {
            report = new CrashReport("Exception in server tick loop", cause);
        }

        return report;
    }

    private boolean haveTime() {
        // CraftBukkit start
        return this.forceTicks || this.runningTask() || Util.getNanos() < (this.mayHaveDelayedTasks ? this.delayedTasksMaxNextTickTimeNanos : this.nextTickTimeNanos);
    }

    private void executeModerately() {
        this.runAllTasks();
        java.util.concurrent.locks.LockSupport.parkNanos("executing tasks", 1000L);
        // CraftBukkit end
    }

    public static boolean throwIfFatalException() {
        RuntimeException runtimeException = fatalException.get();
        if (runtimeException != null) {
            throw runtimeException;
        } else {
            return true;
        }
    }

    public static void setFatalException(RuntimeException fatalException) {
        MinecraftServer.fatalException.compareAndSet(null, fatalException);
    }

    @Override
    public void managedBlock(BooleanSupplier isDone) {
        super.managedBlock(() -> throwIfFatalException() && isDone.getAsBoolean());
    }

    protected void waitUntilNextTick() {
        this.runAllTasks();
        this.waitingForNextTick = true;

        try {
            this.managedBlock(() -> !this.haveTime());
        } finally {
            this.waitingForNextTick = false;
        }
    }

    @Override
    public void waitForTasks() {
        boolean isTickTimeLoggingEnabled = this.isTickTimeLoggingEnabled();
        long l = isTickTimeLoggingEnabled ? Util.getNanos() : 0L;
        long l1 = this.waitingForNextTick ? this.nextTickTimeNanos - Util.getNanos() : 100000L;
        LockSupport.parkNanos("waiting for tasks", l1);
        if (isTickTimeLoggingEnabled) {
            this.idleTimeNanos = this.idleTimeNanos + (Util.getNanos() - l);
        }
    }

    @Override
    public TickTask wrapRunnable(Runnable runnable) {
        // Paper start - anything that does try to post to main during watchdog crash, run on watchdog
        if (this.hasStopped && Thread.currentThread().equals(shutdownThread)) {
            runnable.run();
            runnable = () -> {};
        }
        // Paper end
        return new TickTask(this.tickCount, runnable);
    }

    @Override
    protected boolean shouldRun(TickTask runnable) {
        return runnable.getTick() + 3 < this.tickCount || this.haveTime();
    }

    @Override
    public boolean pollTask() {
        boolean flag = this.pollTaskInternal();
        this.mayHaveDelayedTasks = flag;
        return flag;
    }

    private boolean pollTaskInternal() {
        if (super.pollTask()) {
            this.moonrise$executeMidTickTasks(); // Paper - rewrite chunk system
            return true;
        } else {
            boolean ret = false; // Paper - force execution of all worlds, do not just bias the first
            if (this.tickRateManager.isSprinting() || this.haveTime()) {
                for (ServerLevel serverLevel : this.getAllLevels()) {
                    if (serverLevel.getChunkSource().pollTask()) {
                        ret = true; // Paper - force execution of all worlds, do not just bias the first
                    }
                }
            }

            return ret; // Paper - force execution of all worlds, do not just bias the first
        }
    }

    @Override
    public void doRunTask(TickTask task) {
        Profiler.get().incrementCounter("runTask");
        super.doRunTask(task);
    }

    private Optional<ServerStatus.Favicon> loadStatusIcon() {
        Optional<Path> optional = Optional.of(this.getFile("server-icon.png"))
            .filter(path -> Files.isRegularFile(path))
            .or(() -> this.storageSource.getIconFile().filter(path -> Files.isRegularFile(path)));
        return optional.flatMap(path -> {
            try {
                BufferedImage bufferedImage = ImageIO.read(path.toFile());
                Preconditions.checkState(bufferedImage.getWidth() == 64, "Must be 64 pixels wide");
                Preconditions.checkState(bufferedImage.getHeight() == 64, "Must be 64 pixels high");
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ImageIO.write(bufferedImage, "PNG", byteArrayOutputStream);
                return Optional.of(new ServerStatus.Favicon(byteArrayOutputStream.toByteArray()));
            } catch (Exception var3) {
                LOGGER.error("Couldn't load server icon", (Throwable)var3);
                return Optional.empty();
            }
        });
    }

    public Optional<Path> getWorldScreenshotFile() {
        return this.storageSource.getIconFile();
    }

    public Path getServerDirectory() {
        return Path.of("");
    }

    public void onServerCrash(CrashReport report) {
    }

    public void onServerExit() {
    }

    public boolean isPaused() {
        return false;
    }

    public void tickServer(BooleanSupplier hasTimeLeft) {
        org.spigotmc.WatchdogThread.tick(); // Spigot
        long nanos = Util.getNanos();
        int i = this.pauseWhileEmptySeconds() * 20;
        this.removeDisabledPluginsBlockingSleep(); // Paper - API to allow/disallow tick sleeping
        if (i > 0) {
            if (this.playerList.getPlayerCount() == 0 && !this.tickRateManager.isSprinting() && this.pluginsBlockingSleep.isEmpty()) { // Paper - API to allow/disallow tick sleeping
                this.emptyTicks++;
            } else {
                this.emptyTicks = 0;
            }

            if (this.emptyTicks >= i) {
                this.server.spark.tickStart(); // Paper - spark
                if (this.emptyTicks == i) {
                    LOGGER.info("Server empty for {} seconds, pausing", this.pauseWhileEmptySeconds());
                    this.autoSave();
                }

                this.server.getScheduler().mainThreadHeartbeat(); // CraftBukkit
                // Paper start - avoid issues with certain tasks not processing during sleep
                Runnable task;
                while ((task = this.processQueue.poll()) != null) {
                    task.run();
                }
                for (final ServerLevel level : this.levels.values()) {
                    // process unloads
                    level.getChunkSource().tick(() -> true, false);
                }
                // Paper end - avoid issues with certain tasks not processing during sleep
                this.server.spark.executeMainThreadTasks(); // Paper - spark
                this.tickConnection();
                this.server.spark.tickEnd(((double)(System.nanoTime() - lastTick) / 1000000D)); // Paper - spark
                return;
            }
        }

        this.server.spark.tickStart(); // Paper - spark
        new com.destroystokyo.paper.event.server.ServerTickStartEvent(this.tickCount+1).callEvent(); // Paper - Server Tick Events
        this.tickCount++;
        this.tickRateManager.tick();
        this.tickChildren(hasTimeLeft);
        if (nanos - this.lastServerStatus >= STATUS_EXPIRE_TIME_NANOS) {
            this.lastServerStatus = nanos;
            this.status = this.buildServerStatus();
        }

        this.ticksUntilAutosave--;
        // Paper start - Incremental chunk and player saving
        final ProfilerFiller profiler = Profiler.get();
        int playerSaveInterval = io.papermc.paper.configuration.GlobalConfiguration.get().playerAutoSave.rate;
        if (playerSaveInterval < 0) {
            playerSaveInterval = autosavePeriod;
        }
        profiler.push("save");
        final boolean fullSave = autosavePeriod > 0 && this.tickCount % autosavePeriod == 0;
        try {
            this.isSaving = true;
            if (playerSaveInterval > 0) {
                this.playerList.saveAll(playerSaveInterval);
            }
            for (final ServerLevel level : this.getAllLevels()) {
                if (level.paperConfig().chunks.autoSaveInterval.value() > 0) {
                    level.saveIncrementally(fullSave);
                }
            }
        } finally {
            this.isSaving = false;
        }
        profiler.pop();
        // Paper end - Incremental chunk and player saving

        ProfilerFiller profilerFiller = Profiler.get();
        this.runAllTasks(); // Paper - move runAllTasks() into full server tick (previously for timings)
        this.server.spark.executeMainThreadTasks(); // Paper - spark
        // Paper start - Server Tick Events
        long endTime = System.nanoTime();
        long remaining = (TICK_TIME - (endTime - lastTick)) - catchupTime;
        new com.destroystokyo.paper.event.server.ServerTickEndEvent(this.tickCount, ((double)(endTime - lastTick) / 1000000D), remaining).callEvent();
        // Paper end - Server Tick Events
        this.server.spark.tickEnd(((double)(endTime - lastTick) / 1000000D)); // Paper - spark
        profilerFiller.push("tallying");
        long l = Util.getNanos() - nanos;
        int i1 = this.tickCount % 100;
        this.aggregatedTickTimesNanos = this.aggregatedTickTimesNanos - this.tickTimesNanos[i1];
        this.aggregatedTickTimesNanos += l;
        this.tickTimesNanos[i1] = l;
        this.smoothedTickTimeMillis = this.smoothedTickTimeMillis * 0.8F + (float)l / (float)TimeUtil.NANOSECONDS_PER_MILLISECOND * 0.19999999F;
        // Paper start - Add tick times API and /mspt command
        this.tickTimes5s.add(this.tickCount, l);
        this.tickTimes10s.add(this.tickCount, l);
        this.tickTimes60s.add(this.tickCount, l);
        // Paper end - Add tick times API and /mspt command
        this.logTickMethodTime(nanos);
        profilerFiller.pop();
    }

    private void autoSave() {
        this.ticksUntilAutosave = this.autosavePeriod; // CraftBukkit
        LOGGER.debug("Autosave started");
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("save");
        this.saveEverything(true, false, false);
        profilerFiller.pop();
        LOGGER.debug("Autosave finished");
    }

    private void logTickMethodTime(long startTime) {
        if (this.isTickTimeLoggingEnabled()) {
            this.getTickTimeLogger().logPartialSample(Util.getNanos() - startTime, TpsDebugDimensions.TICK_SERVER_METHOD.ordinal());
        }
    }

    private int computeNextAutosaveInterval() {
        float f;
        if (this.tickRateManager.isSprinting()) {
            long l = this.getAverageTickTimeNanos() + 1L;
            f = (float)TimeUtil.NANOSECONDS_PER_SECOND / (float)l;
        } else {
            f = this.tickRateManager.tickrate();
        }

        int i = 300;
        return Math.max(100, (int)(f * 300.0F));
    }

    public void onTickRateChanged() {
        int i = this.computeNextAutosaveInterval();
        if (i < this.ticksUntilAutosave) {
            this.ticksUntilAutosave = i;
        }
    }

    protected abstract SampleLogger getTickTimeLogger();

    public abstract boolean isTickTimeLoggingEnabled();

    private ServerStatus buildServerStatus() {
        ServerStatus.Players players = this.buildPlayerStatus();
        return new ServerStatus(
            io.papermc.paper.adventure.PaperAdventure.asVanilla(this.motd), // Paper - Adventure
            Optional.of(players),
            Optional.of(ServerStatus.Version.current()),
            Optional.ofNullable(this.statusIcon),
            this.enforceSecureProfile()
        );
    }

    private ServerStatus.Players buildPlayerStatus() {
        List<ServerPlayer> players = this.playerList.getPlayers();
        int maxPlayers = this.getMaxPlayers();
        if (this.hidesOnlinePlayers()) {
            return new ServerStatus.Players(maxPlayers, players.size(), List.of());
        } else {
            int min = Math.min(players.size(), org.spigotmc.SpigotConfig.playerSample); // Paper - PaperServerListPingEvent
            ObjectArrayList<GameProfile> list = new ObjectArrayList<>(min);
            int randomInt = Mth.nextInt(this.random, 0, players.size() - min);

            for (int i = 0; i < min; i++) {
                ServerPlayer serverPlayer = players.get(randomInt + i);
                list.add(serverPlayer.allowsListing() ? serverPlayer.getGameProfile() : ANONYMOUS_PLAYER_PROFILE);
            }

            Util.shuffle(list, this.random);
            return new ServerStatus.Players(maxPlayers, players.size(), list);
        }
    }

    protected void tickChildren(BooleanSupplier hasTimeLeft) {
        ProfilerFiller profilerFiller = Profiler.get();
        this.getPlayerList().getPlayers().forEach(serverPlayer1 -> serverPlayer1.connection.suspendFlushing());
        this.server.getScheduler().mainThreadHeartbeat(); // CraftBukkit
        // Paper start - Folia scheduler API
        ((io.papermc.paper.threadedregions.scheduler.FoliaGlobalRegionScheduler) org.bukkit.Bukkit.getGlobalRegionScheduler()).tick();
        getAllLevels().forEach(level -> {
            for (final net.minecraft.world.entity.Entity entity : level.getEntities().getAll()) {
                if (entity.isRemoved()) {
                    continue;
                }
                final org.bukkit.craftbukkit.entity.CraftEntity bukkit = entity.getBukkitEntityRaw();
                if (bukkit != null) {
                    bukkit.taskScheduler.executeTick();
                }
            }
        });
        // Paper end - Folia scheduler API
        io.papermc.paper.adventure.providers.ClickCallbackProviderImpl.CALLBACK_MANAGER.handleQueue(this.tickCount); // Paper
        profilerFiller.push("commandFunctions");
        this.getFunctions().tick();
        profilerFiller.popPush("levels");

        // CraftBukkit start
        // Run tasks that are waiting on processing
        while (!this.processQueue.isEmpty()) {
            this.processQueue.remove().run();
        }

        // Send time updates to everyone, it will get the right time from the world the player is in.
        // Paper start - Perf: Optimize time updates
        for (final ServerLevel level : this.getAllLevels()) {
            final boolean doDaylight = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
            final long dayTime = level.getDayTime();
            long worldTime = level.getGameTime();
            final ClientboundSetTimePacket worldPacket = new ClientboundSetTimePacket(worldTime, dayTime, doDaylight);
            for (Player entityhuman : level.players()) {
                if (!(entityhuman instanceof ServerPlayer) || (!level.isForceTime() && (tickCount + entityhuman.getId()) % 20 != 0)) { // Purpur - Configurable daylight cycle
                    continue;
                }
                ServerPlayer entityplayer = (ServerPlayer) entityhuman;
                long playerTime = entityplayer.getPlayerTime();
                boolean relativeTime = entityplayer.relativeTime;
                ClientboundSetTimePacket packet = ((relativeTime || !doDaylight) && playerTime == dayTime) ? worldPacket :
                    new ClientboundSetTimePacket(worldTime, playerTime, relativeTime && doDaylight);
                entityplayer.connection.send(packet); // Add support for per player time
                // Paper end - Perf: Optimize time updates
            }
        }

        this.isIteratingOverLevels = true; // Paper - Throw exception on world create while being ticked
        for (ServerLevel serverLevel : this.getAllLevels()) {
            serverLevel.hasPhysicsEvent = org.bukkit.event.block.BlockPhysicsEvent.getHandlerList().getRegisteredListeners().length > 0; // Paper - BlockPhysicsEvent
            serverLevel.hasEntityMoveEvent = io.papermc.paper.event.entity.EntityMoveEvent.getHandlerList().getRegisteredListeners().length > 0; // Paper - Add EntityMoveEvent
            serverLevel.updateLagCompensationTick(); // Paper - lag compensation
            net.minecraft.world.level.block.entity.HopperBlockEntity.skipHopperEvents = serverLevel.paperConfig().hopper.disableMoveEvent || org.bukkit.event.inventory.InventoryMoveItemEvent.getHandlerList().getRegisteredListeners().length == 0; // Paper - Perf: Optimize Hoppers
            profilerFiller.push(() -> serverLevel + " " + serverLevel.dimension().location());
            /* Drop global time updates
            if (this.tickCount % 20 == 0) {
                profilerFiller.push("timeSync");
                this.synchronizeTime(serverLevel);
                profilerFiller.pop();
            }
            // CraftBukkit end */

            profilerFiller.push("tick");

            try {
                serverLevel.tick(hasTimeLeft);
            } catch (Throwable var7) {
                CrashReport crashReport = CrashReport.forThrowable(var7, "Exception ticking world");
                serverLevel.fillReportDetails(crashReport);
                throw new ReportedException(crashReport);
            }

            profilerFiller.pop();
            profilerFiller.pop();
            serverLevel.explosionDensityCache.clear(); // Paper - Optimize explosions
        }
        this.isIteratingOverLevels = false; // Paper - Throw exception on world create while being ticked

        profilerFiller.popPush("connection");
        this.tickConnection();
        profilerFiller.popPush("players");
        this.playerList.tick();
        if (SharedConstants.IS_RUNNING_IN_IDE && this.tickRateManager.runsNormally()) {
            GameTestTicker.SINGLETON.tick();
        }

        profilerFiller.popPush("server gui refresh");

        for (int i = 0; i < this.tickables.size(); i++) {
            this.tickables.get(i).run();
        }

        profilerFiller.popPush("send chunks");

        for (ServerPlayer serverPlayer : this.playerList.getPlayers()) {
            serverPlayer.connection.chunkSender.sendNextChunks(serverPlayer);
            serverPlayer.connection.resumeFlushing();
        }

        profilerFiller.pop();
    }

    public void tickConnection() {
        this.getConnection().tick();
    }

    private void synchronizeTime(ServerLevel level) {
        this.playerList
            .broadcastAll(
                new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)),
                level.dimension()
            );
    }

    public void forceTimeSynchronization() {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("timeSync");

        for (ServerLevel serverLevel : this.getAllLevels()) {
            this.synchronizeTime(serverLevel);
        }

        profilerFiller.pop();
    }

    public boolean isLevelEnabled(Level level) {
        return true;
    }

    public void addTickable(Runnable tickable) {
        this.tickables.add(tickable);
    }

    protected void setId(String serverId) {
        this.serverId = serverId;
    }

    public boolean isShutdown() {
        return !this.serverThread.isAlive();
    }

    public Path getFile(String path) {
        return this.getServerDirectory().resolve(path);
    }

    public final ServerLevel overworld() {
        return this.levels.get(Level.OVERWORLD);
    }

    @Nullable
    public ServerLevel getLevel(ResourceKey<Level> dimension) {
        return this.levels.get(dimension);
    }

    // CraftBukkit start
    public void addLevel(ServerLevel level) {
        Map<ResourceKey<Level>, ServerLevel> oldLevels = this.levels;
        Map<ResourceKey<Level>, ServerLevel> newLevels = Maps.newLinkedHashMap(oldLevels);
        newLevels.put(level.dimension(), level);
        this.levels = Collections.unmodifiableMap(newLevels);
    }

    public void removeLevel(ServerLevel level) {
        Map<ResourceKey<Level>, ServerLevel> oldLevels = this.levels;
        Map<ResourceKey<Level>, ServerLevel> newLevels = Maps.newLinkedHashMap(oldLevels);
        newLevels.remove(level.dimension());
        this.levels = Collections.unmodifiableMap(newLevels);
    }
    // CraftBukkit end

    public Set<ResourceKey<Level>> levelKeys() {
        return this.levels.keySet();
    }

    public Iterable<ServerLevel> getAllLevels() {
        return this.levels.values();
    }

    @Override
    public String getServerVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    @Override
    public int getPlayerCount() {
        return this.playerList.getPlayerCount();
    }

    @Override
    public int getMaxPlayers() {
        return this.playerList.getMaxPlayers();
    }

    public String[] getPlayerNames() {
        return this.playerList.getPlayerNamesArray();
    }

    @DontObfuscate
    public String getServerModName() {
        return org.purpurmc.purpur.PurpurConfig.serverModName; // Paper // Purpur - Configurable server mod name
    }

    public SystemReport fillSystemReport(SystemReport systemReport) {
        systemReport.setDetail("Server Running", () -> Boolean.toString(this.running));
        if (this.playerList != null) {
            systemReport.setDetail(
                "Player Count", () -> this.playerList.getPlayerCount() + " / " + this.playerList.getMaxPlayers() + "; " + this.playerList.getPlayers()
            );
        }

        systemReport.setDetail("Active Data Packs", () -> PackRepository.displayPackList(this.packRepository.getSelectedPacks()));
        systemReport.setDetail("Available Data Packs", () -> PackRepository.displayPackList(this.packRepository.getAvailablePacks()));
        systemReport.setDetail(
            "Enabled Feature Flags",
            () -> FeatureFlags.REGISTRY.toNames(this.worldData.enabledFeatures()).stream().map(ResourceLocation::toString).collect(Collectors.joining(", "))
        );
        systemReport.setDetail("World Generation", () -> this.worldData.worldGenSettingsLifecycle().toString());
        systemReport.setDetail("World Seed", () -> String.valueOf(this.worldData.worldGenOptions().seed()));
        systemReport.setDetail("Suppressed Exceptions", this.suppressedExceptions::dump);
        if (this.serverId != null) {
            systemReport.setDetail("Server Id", () -> this.serverId);
        }

        return this.fillServerSystemReport(systemReport);
    }

    public abstract SystemReport fillServerSystemReport(SystemReport report);

    public ModCheck getModdedStatus() {
        return ModCheck.identify("vanilla", this::getServerModName, "Server", MinecraftServer.class);
    }

    @Override
    public void sendSystemMessage(Component component) {
        LOGGER.info(io.papermc.paper.adventure.PaperAdventure.ANSI_SERIALIZER.serialize(io.papermc.paper.adventure.PaperAdventure.asAdventure(component))); // Paper - Log message with colors
    }

    public KeyPair getKeyPair() {
        return this.keyPair;
    }

    public int getPort() {
        return this.port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Nullable
    public GameProfile getSingleplayerProfile() {
        return this.singleplayerProfile;
    }

    public void setSingleplayerProfile(@Nullable GameProfile singleplayerProfile) {
        this.singleplayerProfile = singleplayerProfile;
    }

    public boolean isSingleplayer() {
        return this.singleplayerProfile != null;
    }

    protected void initializeKeyPair() {
        LOGGER.info("Generating keypair");

        try {
            this.keyPair = Crypt.generateKeyPair();
        } catch (CryptException var2) {
            throw new IllegalStateException("Failed to generate key pair", var2);
        }
    }

    // Paper start - per level difficulty
    public void setDifficulty(ServerLevel level, Difficulty difficulty, boolean forceUpdate) {
        net.minecraft.world.level.storage.PrimaryLevelData worldData = (net.minecraft.world.level.storage.PrimaryLevelData) level.serverLevelData;
        if (forceUpdate || !worldData.isDifficultyLocked()) {
            worldData.setDifficulty(worldData.isHardcore() ? Difficulty.HARD : difficulty);
            level.setSpawnSettings(worldData.getDifficulty() != Difficulty.PEACEFUL && ((net.minecraft.server.dedicated.DedicatedServer) this).settings.getProperties().spawnMonsters);
            // this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
            // Paper end - per level difficulty
        }
    }

    public int getScaledTrackingDistance(int trackingDistance) {
        return trackingDistance;
    }

    private void updateMobSpawningFlags() {
        for (ServerLevel serverLevel : this.getAllLevels()) {
            serverLevel.setSpawnSettings(serverLevel.serverLevelData.getDifficulty() != Difficulty.PEACEFUL && ((net.minecraft.server.dedicated.DedicatedServer) this).settings.getProperties().spawnMonsters); // Paper - per level difficulty (from setDifficulty(ServerLevel, Difficulty, boolean))
        }
    }

    public void setDifficultyLocked(boolean locked) {
        this.worldData.setDifficultyLocked(locked);
        this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
    }

    private void sendDifficultyUpdate(ServerPlayer player) {
        LevelData levelData = player.level().getLevelData();
        player.connection.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
    }

    public boolean isSpawningMonsters() {
        return this.worldData.getDifficulty() != Difficulty.PEACEFUL;
    }

    public boolean isDemo() {
        return this.isDemo;
    }

    public void setDemo(boolean demo) {
        this.isDemo = demo;
    }

    public Optional<MinecraftServer.ServerResourcePackInfo> getServerResourcePack() {
        return Optional.empty();
    }

    public boolean isResourcePackRequired() {
        return this.getServerResourcePack().filter(MinecraftServer.ServerResourcePackInfo::isRequired).isPresent();
    }

    public abstract boolean isDedicatedServer();

    public abstract int getRateLimitPacketsPerSecond();

    public boolean usesAuthentication() {
        return this.onlineMode;
    }

    public void setUsesAuthentication(boolean online) {
        this.onlineMode = online;
    }

    public boolean getPreventProxyConnections() {
        return this.preventProxyConnections;
    }

    public void setPreventProxyConnections(boolean preventProxyConnections) {
        this.preventProxyConnections = preventProxyConnections;
    }

    public abstract boolean isEpollEnabled();

    public boolean isPvpAllowed() {
        return this.pvp;
    }

    public void setPvpAllowed(boolean allowPvp) {
        this.pvp = allowPvp;
    }

    public boolean isFlightAllowed() {
        return this.allowFlight;
    }

    public void setFlightAllowed(boolean allow) {
        this.allowFlight = allow;
    }

    public abstract boolean isCommandBlockEnabled();

    @Override
    public String getMotd() {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(this.motd); // Paper - Adventure
    }

    public void setMotd(String motd) {
        // Paper start - Adventure
        this.motd = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserializeOr(motd, net.kyori.adventure.text.Component.empty());
    }

    public net.kyori.adventure.text.Component motd() {
        return this.motd;
    }

    public void motd(net.kyori.adventure.text.Component motd) {
        // Paper end - Adventure
        this.motd = motd;
    }

    public boolean isStopped() {
        return this.stopped;
    }

    public PlayerList getPlayerList() {
        return this.playerList;
    }

    public void setPlayerList(PlayerList list) {
        this.playerList = list;
    }

    public abstract boolean isPublished();

    public void setDefaultGameType(GameType gameMode) {
        this.worldData.setGameType(gameMode);
    }

    public ServerConnectionListener getConnection() {
        return this.connection == null ? this.connection = new ServerConnectionListener(this) : this.connection; // Spigot
    }

    public boolean isReady() {
        return this.isReady;
    }

    public boolean hasGui() {
        return false;
    }

    public boolean publishServer(@Nullable GameType gameMode, boolean commands, int port) {
        return false;
    }

    public int getTickCount() {
        return this.tickCount;
    }

    public int getSpawnProtectionRadius() {
        return 16;
    }

    public boolean isUnderSpawnProtection(ServerLevel level, BlockPos pos, Player player) {
        return false;
    }

    public boolean repliesToStatus() {
        return true;
    }

    public boolean hidesOnlinePlayers() {
        return false;
    }

    public Proxy getProxy() {
        return this.proxy;
    }

    public int getPlayerIdleTimeout() {
        return this.playerIdleTimeout;
    }

    public void setPlayerIdleTimeout(int idleTimeout) {
        this.playerIdleTimeout = idleTimeout;
    }

    public MinecraftSessionService getSessionService() {
        return this.services.sessionService();
    }

    @Nullable
    public SignatureValidator getProfileKeySignatureValidator() {
        return this.services.profileKeySignatureValidator();
    }

    public GameProfileRepository getProfileRepository() {
        return this.services.profileRepository();
    }

    @Nullable
    public GameProfileCache getProfileCache() {
        return this.services.profileCache();
    }

    @Nullable
    public ServerStatus getStatus() {
        return this.status;
    }

    public void invalidateStatus() {
        this.lastServerStatus = 0L;
    }

    public int getAbsoluteMaxWorldSize() {
        return 29999984;
    }

    @Override
    public boolean scheduleExecutables() {
        return super.scheduleExecutables() && !this.isStopped();
    }

    @Override
    public void executeIfPossible(Runnable task) {
        if (this.isStopped()) {
            throw new io.papermc.paper.util.ServerStopRejectedExecutionException("Server already shutting down"); // Paper - do not prematurely disconnect players on stop
        } else {
            super.executeIfPossible(task);
        }
    }

    @Override
    public Thread getRunningThread() {
        return this.serverThread;
    }

    public int getCompressionThreshold() {
        return 256;
    }

    public boolean enforceSecureProfile() {
        return false;
    }

    public long getNextTickTime() {
        return this.nextTickTimeNanos;
    }

    public DataFixer getFixerUpper() {
        return this.fixerUpper;
    }

    public int getSpawnRadius(@Nullable ServerLevel level) {
        return level != null ? level.getGameRules().getInt(GameRules.RULE_SPAWN_RADIUS) : 10;
    }

    public ServerAdvancementManager getAdvancements() {
        return this.resources.managers.getAdvancements();
    }

    public ServerFunctionManager getFunctions() {
        return this.functionManager;
    }

    // Paper start - Add ServerResourcesReloadedEvent
    @Deprecated @io.papermc.paper.annotation.DoNotUse
    public CompletableFuture<Void> reloadResources(Collection<String> selectedIds) {
        return this.reloadResources(selectedIds, io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause.PLUGIN);
    }
    public CompletableFuture<Void> reloadResources(Collection<String> selectedIds, io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause cause) {
        // Paper end - Add ServerResourcesReloadedEvent
        CompletableFuture<Void> completableFuture = CompletableFuture.<ImmutableList>supplyAsync(
                () -> selectedIds.stream().map(this.packRepository::getPack).filter(Objects::nonNull).map(Pack::open).collect(ImmutableList.toImmutableList()),
                this
            )
            .thenCompose(
                list -> {
                    CloseableResourceManager closeableResourceManager = new MultiPackResourceManager(PackType.SERVER_DATA, list);
                    List<Registry.PendingTags<?>> list1 = TagLoader.loadTagsForExistingRegistries(closeableResourceManager, this.registries.compositeAccess(), io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause.RELOAD); // Paper - tag lifecycle - add cause
                    return ReloadableServerResources.loadResources(
                            closeableResourceManager,
                            this.registries,
                            list1,
                            this.worldData.enabledFeatures(),
                            this.isDedicatedServer() ? Commands.CommandSelection.DEDICATED : Commands.CommandSelection.INTEGRATED,
                            this.getFunctionCompilationLevel(),
                            this.executor,
                            this
                        )
                        .whenComplete((reloadableServerResources, throwable) -> {
                            if (throwable != null) {
                                closeableResourceManager.close();
                            }
                        })
                        .thenApply(reloadableServerResources -> new MinecraftServer.ReloadableResources(closeableResourceManager, reloadableServerResources));
                }
            )
            .thenAcceptAsync(
                reloadableResources -> {
                    io.papermc.paper.command.brigadier.PaperBrigadier.moveBukkitCommands(this.resources.managers().getCommands(), reloadableResources.managers().commands); // Paper
                    this.resources.close();
                    this.resources = reloadableResources;
                    this.packRepository.setSelected(selectedIds, false); // Paper - add pendingReload flag to determine required pack loading - false as this is *after* a reload (see above)
                    WorldDataConfiguration worldDataConfiguration = new WorldDataConfiguration(
                        getSelectedPacks(this.packRepository, true), this.worldData.enabledFeatures()
                    );
                    this.worldData.setDataConfiguration(worldDataConfiguration);
                    this.resources.managers.updateStaticRegistryTags();
                    this.resources.managers.getRecipeManager().finalizeRecipeLoading(this.worldData.enabledFeatures());
                    this.potionBrewing = this.potionBrewing.reload(this.worldData.enabledFeatures()); // Paper - Custom Potion Mixes
                    if (Thread.currentThread() != this.serverThread) return; // Paper
                    // Paper start - we don't need to save everything, just advancements
                    // this.getPlayerList().saveAll();
                    for (final ServerPlayer player : this.getPlayerList().getPlayers()) {
                        player.getAdvancements().save();
                    }
                    // Paper end - we don't need to save everything, just advancements
                    this.getPlayerList().reloadResources();
                    this.functionManager.replaceLibrary(this.resources.managers.getFunctionLibrary());
                    this.structureTemplateManager.onResourceManagerReload(this.resources.resourceManager);
                    this.fuelValues = FuelValues.vanillaBurnTimes(this.registries.compositeAccess(), this.worldData.enabledFeatures());
                    org.bukkit.craftbukkit.block.data.CraftBlockData.reloadCache(); // Paper - cache block data strings; they can be defined by datapacks so refresh it here
                    // Paper start - brigadier command API
                    io.papermc.paper.command.brigadier.PaperCommands.INSTANCE.setValid(); // reset invalid state for event fire below
                    io.papermc.paper.plugin.lifecycle.event.LifecycleEventRunner.INSTANCE.callReloadableRegistrarEvent(io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS, io.papermc.paper.command.brigadier.PaperCommands.INSTANCE, org.bukkit.plugin.Plugin.class, io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause.RELOAD); // call commands event for regular plugins
                    final org.bukkit.craftbukkit.help.SimpleHelpMap helpMap = (org.bukkit.craftbukkit.help.SimpleHelpMap) this.server.getHelpMap();
                    helpMap.clear();
                    helpMap.initializeGeneralTopics();
                    helpMap.initializeCommands();
                    this.server.syncCommands(); // Refresh commands after event
                    // Paper end
                    new io.papermc.paper.event.server.ServerResourcesReloadedEvent(cause).callEvent(); // Paper - Add ServerResourcesReloadedEvent; fire after everything has been reloaded
                },
                this
            );
        if (this.isSameThread()) {
            this.managedBlock(completableFuture::isDone);
        }

        return completableFuture;
    }

    public static WorldDataConfiguration configurePackRepository(
        PackRepository packRepository, WorldDataConfiguration initialDataConfig, boolean initMode, boolean safeMode
    ) {
        DataPackConfig dataPackConfig = initialDataConfig.dataPacks();
        FeatureFlagSet featureFlagSet = initMode ? FeatureFlagSet.of() : initialDataConfig.enabledFeatures();
        FeatureFlagSet featureFlagSet1 = initMode ? FeatureFlags.REGISTRY.allFlags() : initialDataConfig.enabledFeatures();
        packRepository.reload(true); // Paper - will load resource packs
        if (safeMode) {
            return configureRepositoryWithSelection(packRepository, List.of("vanilla"), featureFlagSet, false);
        } else {
            Set<String> set = Sets.newLinkedHashSet();

            for (String string : dataPackConfig.getEnabled()) {
                if (packRepository.isAvailable(string)) {
                    set.add(string);
                } else {
                    LOGGER.warn("Missing data pack {}", string);
                }
            }

            for (Pack pack : packRepository.getAvailablePacks()) {
                String id = pack.getId();
                if (!dataPackConfig.getDisabled().contains(id)) {
                    FeatureFlagSet requestedFeatures = pack.getRequestedFeatures();
                    boolean flag = set.contains(id);
                    if (!flag && pack.getPackSource().shouldAddAutomatically()) {
                        if (requestedFeatures.isSubsetOf(featureFlagSet1)) {
                            LOGGER.info("Found new data pack {}, loading it automatically", id);
                            set.add(id);
                        } else {
                            LOGGER.info(
                                "Found new data pack {}, but can't load it due to missing features {}",
                                id,
                                FeatureFlags.printMissingFlags(featureFlagSet1, requestedFeatures)
                            );
                        }
                    }

                    if (flag && !requestedFeatures.isSubsetOf(featureFlagSet1)) {
                        LOGGER.warn(
                            "Pack {} requires features {} that are not enabled for this world, disabling pack.",
                            id,
                            FeatureFlags.printMissingFlags(featureFlagSet1, requestedFeatures)
                        );
                        set.remove(id);
                    }
                }
            }

            if (set.isEmpty()) {
                LOGGER.info("No datapacks selected, forcing vanilla");
                set.add("vanilla");
            }

            return configureRepositoryWithSelection(packRepository, set, featureFlagSet, true);
        }
    }

    private static WorldDataConfiguration configureRepositoryWithSelection(
        PackRepository packRepository, Collection<String> selectedPacks, FeatureFlagSet enabledFeatures, boolean safeMode
    ) {
        packRepository.setSelected(selectedPacks, true); // Paper - add pendingReload flag to determine required pack loading - before the initial server load
        enableForcedFeaturePacks(packRepository, enabledFeatures);
        DataPackConfig selectedPacks1 = getSelectedPacks(packRepository, safeMode);
        FeatureFlagSet featureFlagSet = packRepository.getRequestedFeatureFlags().join(enabledFeatures);
        return new WorldDataConfiguration(selectedPacks1, featureFlagSet);
    }

    private static void enableForcedFeaturePacks(PackRepository packRepository, FeatureFlagSet enabledFeatures) {
        FeatureFlagSet requestedFeatureFlags = packRepository.getRequestedFeatureFlags();
        FeatureFlagSet featureFlagSet = enabledFeatures.subtract(requestedFeatureFlags);
        if (!featureFlagSet.isEmpty()) {
            Set<String> set = new ObjectArraySet<>(packRepository.getSelectedIds());

            for (Pack pack : packRepository.getAvailablePacks()) {
                if (featureFlagSet.isEmpty()) {
                    break;
                }

                if (pack.getPackSource() == PackSource.FEATURE) {
                    String id = pack.getId();
                    FeatureFlagSet requestedFeatures = pack.getRequestedFeatures();
                    if (!requestedFeatures.isEmpty() && requestedFeatures.intersects(featureFlagSet) && requestedFeatures.isSubsetOf(enabledFeatures)) {
                        if (!set.add(id)) {
                            throw new IllegalStateException("Tried to force '" + id + "', but it was already enabled");
                        }

                        LOGGER.info("Found feature pack ('{}') for requested feature, forcing to enabled", id);
                        featureFlagSet = featureFlagSet.subtract(requestedFeatures);
                    }
                }
            }

            packRepository.setSelected(set, true); // Paper - add pendingReload flag to determine required pack loading - before the initial server start
        }
    }

    private static DataPackConfig getSelectedPacks(PackRepository packRepository, boolean safeMode) {
        Collection<String> selectedIds = packRepository.getSelectedIds();
        List<String> list = ImmutableList.copyOf(selectedIds);
        List<String> list1 = safeMode ? packRepository.getAvailableIds().stream().filter(packId -> !selectedIds.contains(packId)).toList() : List.of();
        return new DataPackConfig(list, list1);
    }

    public void kickUnlistedPlayers(CommandSourceStack commandSource) {
        if (this.isEnforceWhitelist()) {
            PlayerList playerList = commandSource.getServer().getPlayerList();
            if (!playerList.isUsingWhitelist()) return; // Paper - whitelist not enabled
            UserWhiteList whiteList = playerList.getWhiteList();

            for (ServerPlayer serverPlayer : Lists.newArrayList(playerList.getPlayers())) {
                if (!whiteList.isWhiteListed(serverPlayer.getGameProfile()) && !this.getPlayerList().isOp(serverPlayer.getGameProfile())) { // Paper - Fix kicking ops when whitelist is reloaded (MC-171420)
                    serverPlayer.connection.disconnect(net.kyori.adventure.text.Component.text(org.spigotmc.SpigotConfig.whitelistMessage), org.bukkit.event.player.PlayerKickEvent.Cause.WHITELIST); // Paper - use configurable message & kick event cause
                }
            }
        }
    }

    public PackRepository getPackRepository() {
        return this.packRepository;
    }

    public Commands getCommands() {
        return this.resources.managers.getCommands();
    }

    public CommandSourceStack createCommandSourceStack() {
        ServerLevel serverLevel = this.overworld();
        return new CommandSourceStack(
            this,
            serverLevel == null ? Vec3.ZERO : Vec3.atLowerCornerOf(serverLevel.getSharedSpawnPos()),
            Vec2.ZERO,
            serverLevel,
            4,
            "Server",
            Component.literal("Server"),
            this,
            null
        );
    }

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public abstract boolean shouldInformAdmins();

    public RecipeManager getRecipeManager() {
        return this.resources.managers.getRecipeManager();
    }

    public ServerScoreboard getScoreboard() {
        return this.scoreboard;
    }

    public CommandStorage getCommandStorage() {
        if (this.commandStorage == null) {
            throw new NullPointerException("Called before server init");
        } else {
            return this.commandStorage;
        }
    }

    public GameRules getGameRules() {
        return this.overworld().getGameRules();
    }

    public CustomBossEvents getCustomBossEvents() {
        return this.customBossEvents;
    }

    public boolean isEnforceWhitelist() {
        return this.enforceWhitelist;
    }

    public void setEnforceWhitelist(boolean whitelistEnabled) {
        this.enforceWhitelist = whitelistEnabled;
    }

    public float getCurrentSmoothedTickTime() {
        return this.smoothedTickTimeMillis;
    }

    public ServerTickRateManager tickRateManager() {
        return this.tickRateManager;
    }

    public long getAverageTickTimeNanos() {
        return this.aggregatedTickTimesNanos / Math.min(100, Math.max(this.tickCount, 1));
    }

    public long[] getTickTimesNanos() {
        return this.tickTimesNanos;
    }

    public int getProfilePermissions(GameProfile profile) {
        if (this.getPlayerList().isOp(profile)) {
            ServerOpListEntry serverOpListEntry = this.getPlayerList().getOps().get(profile);
            if (serverOpListEntry != null) {
                return serverOpListEntry.getLevel();
            } else if (this.isSingleplayerOwner(profile)) {
                return 4;
            } else if (this.isSingleplayer()) {
                return this.getPlayerList().isAllowCommandsForAllPlayers() ? 4 : 0;
            } else {
                return this.getOperatorUserPermissionLevel();
            }
        } else {
            return 0;
        }
    }

    public abstract boolean isSingleplayerOwner(GameProfile profile);

    public void dumpServerProperties(Path path) throws IOException {
    }

    private void saveDebugReport(Path path) {
        Path path1 = path.resolve("levels");

        try {
            for (Entry<ResourceKey<Level>, ServerLevel> entry : this.levels.entrySet()) {
                ResourceLocation resourceLocation = entry.getKey().location();
                Path path2 = path1.resolve(resourceLocation.getNamespace()).resolve(resourceLocation.getPath());
                Files.createDirectories(path2);
                entry.getValue().saveDebugReport(path2);
            }

            this.dumpGameRules(path.resolve("gamerules.txt"));
            this.dumpClasspath(path.resolve("classpath.txt"));
            this.dumpMiscStats(path.resolve("stats.txt"));
            this.dumpThreads(path.resolve("threads.txt"));
            this.dumpServerProperties(path.resolve("server.properties.txt"));
            this.dumpNativeModules(path.resolve("modules.txt"));
        } catch (IOException var7) {
            LOGGER.warn("Failed to save debug report", (Throwable)var7);
        }
    }

    private void dumpMiscStats(Path path) throws IOException {
        try (Writer bufferedWriter = Files.newBufferedWriter(path)) {
            bufferedWriter.write(String.format(Locale.ROOT, "pending_tasks: %d\n", this.getPendingTasksCount()));
            bufferedWriter.write(String.format(Locale.ROOT, "average_tick_time: %f\n", this.getCurrentSmoothedTickTime()));
            bufferedWriter.write(String.format(Locale.ROOT, "tick_times: %s\n", Arrays.toString(this.tickTimesNanos)));
            bufferedWriter.write(String.format(Locale.ROOT, "queue: %s\n", Util.backgroundExecutor()));
        }
    }

    private void dumpGameRules(Path path) throws IOException {
        try (Writer bufferedWriter = Files.newBufferedWriter(path)) {
            final List<String> list = Lists.newArrayList();
            final GameRules gameRules = this.getGameRules();
            gameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
                @Override
                public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                    list.add(String.format(Locale.ROOT, "%s=%s\n", key.getId(), gameRules.getRule(key)));
                }
            });

            for (String string : list) {
                bufferedWriter.write(string);
            }
        }
    }

    private void dumpClasspath(Path path) throws IOException {
        try (Writer bufferedWriter = Files.newBufferedWriter(path)) {
            String property = System.getProperty("java.class.path");
            String property1 = System.getProperty("path.separator");

            for (String string : Splitter.on(property1).split(property)) {
                bufferedWriter.write(string);
                bufferedWriter.write("\n");
            }
        }
    }

    private void dumpThreads(Path path) throws IOException {
        ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = threadMxBean.dumpAllThreads(true, true);
        Arrays.sort(threadInfos, Comparator.comparing(ThreadInfo::getThreadName));

        try (Writer bufferedWriter = Files.newBufferedWriter(path)) {
            for (ThreadInfo threadInfo : threadInfos) {
                bufferedWriter.write(threadInfo.toString());
                bufferedWriter.write(10);
            }
        }
    }

    private void dumpNativeModules(Path path) throws IOException {
        try (Writer bufferedWriter = Files.newBufferedWriter(path)) {
            List<NativeModuleLister.NativeModuleInfo> list;
            try {
                list = Lists.newArrayList(NativeModuleLister.listModules());
            } catch (Throwable var7) {
                LOGGER.warn("Failed to list native modules", var7);
                return;
            }

            list.sort(Comparator.comparing(nativeModuleInfo1 -> nativeModuleInfo1.name));

            for (NativeModuleLister.NativeModuleInfo nativeModuleInfo : list) {
                bufferedWriter.write(nativeModuleInfo.toString());
                bufferedWriter.write(10);
            }
        }
    }

    // Paper start - rewrite chunk system
    @Override
    public boolean isSameThread() {
        return ca.spottedleaf.moonrise.common.util.TickThread.isTickThread();
    }
    // Paper end - rewrite chunk system

    // CraftBukkit start
    public boolean isDebugging() {
        return false;
    }

    public static MinecraftServer getServer() {
        return SERVER; // Paper
    }

    @Deprecated
    public static RegistryAccess getDefaultRegistryAccess() {
        return org.bukkit.craftbukkit.CraftRegistry.getMinecraftRegistry();
    }
    // CraftBukkit end

    private ProfilerFiller createProfiler() {
        if (this.willStartRecordingMetrics) {
            this.metricsRecorder = ActiveMetricsRecorder.createStarted(
                new ServerMetricsSamplersProvider(Util.timeSource, this.isDedicatedServer()),
                Util.timeSource,
                Util.ioPool(),
                new MetricsPersister("server"),
                this.onMetricsRecordingStopped,
                path -> {
                    this.executeBlocking(() -> this.saveDebugReport(path.resolve("server")));
                    this.onMetricsRecordingFinished.accept(path);
                }
            );
            this.willStartRecordingMetrics = false;
        }

        this.metricsRecorder.startTick();
        return SingleTickProfiler.decorateFiller(this.metricsRecorder.getProfiler(), SingleTickProfiler.createTickProfiler("Server"));
    }

    public void endMetricsRecordingTick() {
        this.metricsRecorder.endTick();
    }

    public boolean isRecordingMetrics() {
        return this.metricsRecorder.isRecording();
    }

    public void startRecordingMetrics(Consumer<ProfileResults> output, Consumer<Path> onMetricsRecordingFinished) {
        this.onMetricsRecordingStopped = profileResults -> {
            this.stopRecordingMetrics();
            output.accept(profileResults);
        };
        this.onMetricsRecordingFinished = onMetricsRecordingFinished;
        this.willStartRecordingMetrics = true;
    }

    public void stopRecordingMetrics() {
        this.metricsRecorder = InactiveMetricsRecorder.INSTANCE;
    }

    public void finishRecordingMetrics() {
        this.metricsRecorder.end();
    }

    public void cancelRecordingMetrics() {
        this.metricsRecorder.cancel();
    }

    public Path getWorldPath(LevelResource levelResource) {
        return this.storageSource.getLevelPath(levelResource);
    }

    public boolean forceSynchronousWrites() {
        return true;
    }

    public StructureTemplateManager getStructureManager() {
        return this.structureTemplateManager;
    }

    public WorldData getWorldData() {
        return this.worldData;
    }

    public RegistryAccess.Frozen registryAccess() {
        return this.registries.compositeAccess();
    }

    public LayeredRegistryAccess<RegistryLayer> registries() {
        return this.registries;
    }

    public ReloadableServerRegistries.Holder reloadableRegistries() {
        return this.resources.managers.fullRegistries();
    }

    public TextFilter createTextFilterForPlayer(ServerPlayer player) {
        return TextFilter.DUMMY;
    }

    public ServerPlayerGameMode createGameModeForPlayer(ServerPlayer player) {
        return (ServerPlayerGameMode)(this.isDemo() ? new DemoMode(player) : new ServerPlayerGameMode(player));
    }

    @Nullable
    public GameType getForcedGameType() {
        return null;
    }

    public ResourceManager getResourceManager() {
        return this.resources.resourceManager;
    }

    public boolean isCurrentlySaving() {
        return this.isSaving;
    }

    public boolean isTimeProfilerRunning() {
        return this.debugCommandProfilerDelayStart || this.debugCommandProfiler != null;
    }

    public void startTimeProfiler() {
        this.debugCommandProfilerDelayStart = true;
    }

    public ProfileResults stopTimeProfiler() {
        if (this.debugCommandProfiler == null) {
            return EmptyProfileResults.EMPTY;
        } else {
            ProfileResults profileResults = this.debugCommandProfiler.stop(Util.getNanos(), this.tickCount);
            this.debugCommandProfiler = null;
            return profileResults;
        }
    }

    public int getMaxChainedNeighborUpdates() {
        return 1000000;
    }

    public void logChatMessage(Component content, ChatType.Bound boundChatType, @Nullable String header) {
        // Paper start
        net.kyori.adventure.text.Component string = io.papermc.paper.adventure.PaperAdventure.asAdventure(boundChatType.decorate(content));
        if (header != null) {
            COMPONENT_LOGGER.info("[{}] {}", header, string);
        } else {
            COMPONENT_LOGGER.info("{}", string);
            // Paper end
        }
    }

    public final java.util.concurrent.ExecutorService chatExecutor = java.util.concurrent.Executors.newCachedThreadPool(
        new com.google.common.util.concurrent.ThreadFactoryBuilder().setDaemon(true).setNameFormat("Async Chat Thread - #%d").setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(net.minecraft.server.MinecraftServer.LOGGER)).build()); // Paper
    public final ChatDecorator improvedChatDecorator = new io.papermc.paper.adventure.ImprovedChatDecorator(this); // Paper - adventure

    public ChatDecorator getChatDecorator() {
        return this.improvedChatDecorator; // Paper - support async chat decoration events
    }

    public boolean logIPs() {
        return true;
    }

    public void subscribeToDebugSample(ServerPlayer player, RemoteDebugSampleType sampleType) {
    }

    public boolean acceptsTransfers() {
        return false;
    }

    private void storeChunkIoError(CrashReport crashReport, ChunkPos chunkPos, RegionStorageInfo regionStorageInfo) {
        Util.ioPool().execute(() -> {
            try {
                Path file = this.getFile("debug");
                FileUtil.createDirectoriesSafe(file);
                String string = FileUtil.sanitizeName(regionStorageInfo.level());
                Path path = file.resolve("chunk-" + string + "-" + Util.getFilenameFormattedDateTime() + "-server.txt");
                FileStore fileStore = Files.getFileStore(file);
                long usableSpace = fileStore.getUsableSpace();
                if (usableSpace < 8192L) {
                    LOGGER.warn("Not storing chunk IO report due to low space on drive {}", fileStore.name());
                    return;
                }

                CrashReportCategory crashReportCategory = crashReport.addCategory("Chunk Info");
                crashReportCategory.setDetail("Level", regionStorageInfo::level);
                crashReportCategory.setDetail("Dimension", () -> regionStorageInfo.dimension().location().toString());
                crashReportCategory.setDetail("Storage", regionStorageInfo::type);
                crashReportCategory.setDetail("Position", chunkPos::toString);
                crashReport.saveToFile(path, ReportType.CHUNK_IO_ERROR);
                LOGGER.info("Saved details to {}", crashReport.getSaveFile());
            } catch (Exception var11) {
                LOGGER.warn("Failed to store chunk IO exception", (Throwable)var11);
            }
        });
    }

    @Override
    public void reportChunkLoadFailure(Throwable throwable, RegionStorageInfo regionStorageInfo, ChunkPos chunkPos) {
        LOGGER.error("Failed to load chunk {},{}", chunkPos.x, chunkPos.z, throwable);
        this.suppressedExceptions.addEntry("chunk/load", throwable);
        this.storeChunkIoError(CrashReport.forThrowable(throwable, "Chunk load failure"), chunkPos, regionStorageInfo);
    }

    @Override
    public void reportChunkSaveFailure(Throwable throwable, RegionStorageInfo regionStorageInfo, ChunkPos chunkPos) {
        LOGGER.error("Failed to save chunk {},{}", chunkPos.x, chunkPos.z, throwable);
        this.suppressedExceptions.addEntry("chunk/save", throwable);
        this.storeChunkIoError(CrashReport.forThrowable(throwable, "Chunk save failure"), chunkPos, regionStorageInfo);
    }

    public void reportPacketHandlingException(Throwable throwable, PacketType<?> packetType) {
        this.suppressedExceptions.addEntry("packet/" + packetType.toString(), throwable);
    }

    public PotionBrewing potionBrewing() {
        return this.potionBrewing;
    }

    public FuelValues fuelValues() {
        return this.fuelValues;
    }

    public ServerLinks serverLinks() {
        return ServerLinks.EMPTY;
    }

    protected int pauseWhileEmptySeconds() {
        return 0;
    }

    public record ReloadableResources(CloseableResourceManager resourceManager, ReloadableServerResources managers) implements AutoCloseable {
        @Override
        public void close() {
            this.resourceManager.close();
        }
    }

    public record ServerResourcePackInfo(UUID id, String url, String hash, boolean isRequired, @Nullable Component prompt) {
    }

    static class TimeProfiler {
        final long startNanos;
        final int startTick;

        TimeProfiler(long startNanos, int startTick) {
            this.startNanos = startNanos;
            this.startTick = startTick;
        }

        ProfileResults stop(final long endTimeNano, final int endTimeTicks) {
            return new ProfileResults() {
                @Override
                public List<ResultField> getTimes(String sectionPath) {
                    return Collections.emptyList();
                }

                @Override
                public boolean saveResults(Path path) {
                    return false;
                }

                @Override
                public long getStartTimeNano() {
                    return TimeProfiler.this.startNanos;
                }

                @Override
                public int getStartTimeTicks() {
                    return TimeProfiler.this.startTick;
                }

                @Override
                public long getEndTimeNano() {
                    return endTimeNano;
                }

                @Override
                public int getEndTimeTicks() {
                    return endTimeTicks;
                }

                @Override
                public String getProfilerResults() {
                    return "";
                }
            };
        }
    }

    // Paper start - Add tick times API and /mspt command
    public static class TickTimes {
        private final long[] times;

        public TickTimes(int length) {
            times = new long[length];
        }

        void add(int index, long time) {
            times[index % times.length] = time;
        }

        public long[] getTimes() {
            return times.clone();
        }

        public double getAverage() {
            long total = 0L;
            for (long value : times) {
                total += value;
            }
            return ((double) total / (double) times.length) * 1.0E-6D;
        }
    }
    // Paper end - Add tick times API and /mspt command

    // Paper start - API to check if the server is sleeping
    public boolean isTickPaused() {
        return this.emptyTicks > 0 && this.emptyTicks >= this.pauseWhileEmptySeconds() * 20;
    }

    public void addPluginAllowingSleep(final String pluginName, final boolean value) {
        if (!value) {
            this.pluginsBlockingSleep.add(pluginName);
        } else {
            this.pluginsBlockingSleep.remove(pluginName);
        }
    }

    private void removeDisabledPluginsBlockingSleep() {
        if (this.pluginsBlockingSleep.isEmpty()) {
            return;
        }
        this.pluginsBlockingSleep.removeIf(plugin -> (
            !io.papermc.paper.plugin.manager.PaperPluginManagerImpl.getInstance().isPluginEnabled(plugin)
        ));
    }
    // Paper end - API to check if the server is sleeping
}
