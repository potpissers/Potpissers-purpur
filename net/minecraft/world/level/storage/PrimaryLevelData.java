package net.minecraft.world.level.storage;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.CrashReportCategory;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.timers.TimerCallbacks;
import net.minecraft.world.level.timers.TimerQueue;
import org.slf4j.Logger;

public class PrimaryLevelData implements ServerLevelData, WorldData {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String LEVEL_NAME = "LevelName";
    protected static final String PLAYER = "Player";
    protected static final String WORLD_GEN_SETTINGS = "WorldGenSettings";
    public LevelSettings settings;
    private final WorldOptions worldOptions;
    private final PrimaryLevelData.SpecialWorldProperty specialWorldProperty;
    private final Lifecycle worldGenSettingsLifecycle;
    private BlockPos spawnPos;
    private float spawnAngle;
    private long gameTime;
    private long dayTime;
    @Nullable
    private final CompoundTag loadedPlayerTag;
    private final int version;
    private int clearWeatherTime;
    private boolean raining;
    private int rainTime;
    private boolean thundering;
    private int thunderTime;
    private boolean initialized;
    private boolean difficultyLocked;
    private WorldBorder.Settings worldBorder;
    private EndDragonFight.Data endDragonFightData;
    @Nullable
    private CompoundTag customBossEvents;
    private int wanderingTraderSpawnDelay;
    private int wanderingTraderSpawnChance;
    @Nullable
    private UUID wanderingTraderId;
    private final Set<String> knownServerBrands;
    private boolean wasModded;
    private final Set<String> removedFeatureFlags;
    private final TimerQueue<MinecraftServer> scheduledEvents;

    // CraftBukkit start - Add world and pdc
    public net.minecraft.core.Registry<net.minecraft.world.level.dimension.LevelStem> customDimensions;
    private net.minecraft.server.level.ServerLevel world;
    protected net.minecraft.nbt.Tag pdc;

    public void setWorld(net.minecraft.server.level.ServerLevel world) {
        if (this.world != null) {
            return;
        }
        this.world = world;
        world.getWorld().readBukkitValues(this.pdc);
        this.pdc = null;
    }
    // CraftBukkit end

    private PrimaryLevelData(
        @Nullable CompoundTag loadedPlayerTag,
        boolean wasModded,
        BlockPos spawnPos,
        float spawnAngle,
        long gameTime,
        long dayTime,
        int version,
        int clearWeatherTime,
        int rainTime,
        boolean raining,
        int thunderTime,
        boolean thundering,
        boolean initialized,
        boolean difficultyLocked,
        WorldBorder.Settings worldBorder,
        int wanderingTraderSpawnDelay,
        int wanderingTraderSpawnChance,
        @Nullable UUID wanderingTraderId,
        Set<String> knownServerBrands,
        Set<String> removedFeatureFlags,
        TimerQueue<MinecraftServer> scheduledEvents,
        @Nullable CompoundTag customBossEvents,
        EndDragonFight.Data endDragonFightData,
        LevelSettings settings,
        WorldOptions worldOptions,
        PrimaryLevelData.SpecialWorldProperty specialWorldProperty,
        Lifecycle worldGenSettingsLifecycle
    ) {
        this.wasModded = wasModded;
        this.spawnPos = spawnPos;
        this.spawnAngle = spawnAngle;
        this.gameTime = gameTime;
        this.dayTime = dayTime;
        this.version = version;
        this.clearWeatherTime = clearWeatherTime;
        this.rainTime = rainTime;
        this.raining = raining;
        this.thunderTime = thunderTime;
        this.thundering = thundering;
        this.initialized = initialized;
        this.difficultyLocked = difficultyLocked;
        this.worldBorder = worldBorder;
        this.wanderingTraderSpawnDelay = wanderingTraderSpawnDelay;
        this.wanderingTraderSpawnChance = wanderingTraderSpawnChance;
        this.wanderingTraderId = wanderingTraderId;
        this.knownServerBrands = knownServerBrands;
        this.removedFeatureFlags = removedFeatureFlags;
        this.loadedPlayerTag = loadedPlayerTag;
        this.scheduledEvents = scheduledEvents;
        this.customBossEvents = customBossEvents;
        this.endDragonFightData = endDragonFightData;
        this.settings = settings;
        this.worldOptions = worldOptions;
        this.specialWorldProperty = specialWorldProperty;
        this.worldGenSettingsLifecycle = worldGenSettingsLifecycle;
    }

    public PrimaryLevelData(
        LevelSettings settings, WorldOptions worldOptions, PrimaryLevelData.SpecialWorldProperty specialWorldProperty, Lifecycle worldGenSettingsLifecycle
    ) {
        this(
            null,
            false,
            BlockPos.ZERO,
            0.0F,
            0L,
            0L,
            19133,
            0,
            0,
            false,
            0,
            false,
            false,
            false,
            WorldBorder.DEFAULT_SETTINGS,
            0,
            0,
            null,
            Sets.newLinkedHashSet(),
            new HashSet<>(),
            new TimerQueue<>(TimerCallbacks.SERVER_CALLBACKS),
            null,
            EndDragonFight.Data.DEFAULT,
            settings.copy(),
            worldOptions,
            specialWorldProperty,
            worldGenSettingsLifecycle
        );
    }

    public static <T> PrimaryLevelData parse(
        Dynamic<T> tag,
        LevelSettings levelSettings,
        PrimaryLevelData.SpecialWorldProperty specialWorldProperty,
        WorldOptions worldOptions,
        Lifecycle worldGenSettingsLifecycle
    ) {
        long _long = tag.get("Time").asLong(0L);
        return new PrimaryLevelData(
            tag.get("Player").flatMap(CompoundTag.CODEC::parse).result().orElse(null),
            tag.get("WasModded").asBoolean(false),
            new BlockPos(tag.get("SpawnX").asInt(0), tag.get("SpawnY").asInt(0), tag.get("SpawnZ").asInt(0)),
            tag.get("SpawnAngle").asFloat(0.0F),
            _long,
            tag.get("DayTime").asLong(_long),
            LevelVersion.parse(tag).levelDataVersion(),
            tag.get("clearWeatherTime").asInt(0),
            tag.get("rainTime").asInt(0),
            tag.get("raining").asBoolean(false),
            tag.get("thunderTime").asInt(0),
            tag.get("thundering").asBoolean(false),
            tag.get("initialized").asBoolean(true),
            tag.get("DifficultyLocked").asBoolean(false),
            WorldBorder.Settings.read(tag, WorldBorder.DEFAULT_SETTINGS),
            tag.get("WanderingTraderSpawnDelay").asInt(0),
            tag.get("WanderingTraderSpawnChance").asInt(0),
            tag.get("WanderingTraderId").read(UUIDUtil.CODEC).result().orElse(null),
            tag.get("ServerBrands")
                .asStream()
                .flatMap(brandsDynamic -> brandsDynamic.asString().result().stream())
                .collect(Collectors.toCollection(Sets::newLinkedHashSet)),
            tag.get("removed_features")
                .asStream()
                .flatMap(removedFeaturesDynamic -> removedFeaturesDynamic.asString().result().stream())
                .collect(Collectors.toSet()),
            new TimerQueue<>(TimerCallbacks.SERVER_CALLBACKS, tag.get("ScheduledEvents").asStream()),
            (CompoundTag)tag.get("CustomBossEvents").orElseEmptyMap().getValue(),
            tag.get("DragonFight").read(EndDragonFight.Data.CODEC).resultOrPartial(LOGGER::error).orElse(EndDragonFight.Data.DEFAULT),
            levelSettings,
            worldOptions,
            specialWorldProperty,
            worldGenSettingsLifecycle
        );
    }

    @Override
    public CompoundTag createTag(RegistryAccess registries, @Nullable CompoundTag hostPlayerNBT) {
        if (hostPlayerNBT == null) {
            hostPlayerNBT = this.loadedPlayerTag;
        }

        CompoundTag compoundTag = new CompoundTag();
        this.setTagData(registries, compoundTag, hostPlayerNBT);
        return compoundTag;
    }

    private void setTagData(RegistryAccess registry, CompoundTag nbt, @Nullable CompoundTag playerNBT) {
        nbt.put("ServerBrands", stringCollectionToTag(this.knownServerBrands));
        nbt.putBoolean("WasModded", this.wasModded);
        if (!this.removedFeatureFlags.isEmpty()) {
            nbt.put("removed_features", stringCollectionToTag(this.removedFeatureFlags));
        }

        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("Name", SharedConstants.getCurrentVersion().getName());
        compoundTag.putInt("Id", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        compoundTag.putBoolean("Snapshot", !SharedConstants.getCurrentVersion().isStable());
        compoundTag.putString("Series", SharedConstants.getCurrentVersion().getDataVersion().getSeries());
        nbt.put("Version", compoundTag);
        NbtUtils.addCurrentDataVersion(nbt);
        DynamicOps<Tag> dynamicOps = registry.createSerializationContext(NbtOps.INSTANCE);
        WorldGenSettings.encode(dynamicOps, this.worldOptions, new net.minecraft.world.level.levelgen.WorldDimensions(this.customDimensions != null ? this.customDimensions : registry.lookupOrThrow(net.minecraft.core.registries.Registries.LEVEL_STEM))) // CraftBukkit
            .resultOrPartial(Util.prefix("WorldGenSettings: ", LOGGER::error))
            .ifPresent(worldOptionsTag -> nbt.put("WorldGenSettings", worldOptionsTag));
        nbt.putInt("GameType", this.settings.gameType().getId());
        nbt.putInt("SpawnX", this.spawnPos.getX());
        nbt.putInt("SpawnY", this.spawnPos.getY());
        nbt.putInt("SpawnZ", this.spawnPos.getZ());
        nbt.putFloat("SpawnAngle", this.spawnAngle);
        nbt.putLong("Time", this.gameTime);
        nbt.putLong("DayTime", this.dayTime);
        nbt.putLong("LastPlayed", Util.getEpochMillis());
        nbt.putString("LevelName", this.settings.levelName());
        nbt.putInt("version", 19133);
        nbt.putInt("clearWeatherTime", this.clearWeatherTime);
        nbt.putInt("rainTime", this.rainTime);
        nbt.putBoolean("raining", this.raining);
        nbt.putInt("thunderTime", this.thunderTime);
        nbt.putBoolean("thundering", this.thundering);
        nbt.putBoolean("hardcore", this.settings.hardcore());
        nbt.putBoolean("allowCommands", this.settings.allowCommands());
        nbt.putBoolean("initialized", this.initialized);
        this.worldBorder.write(nbt);
        nbt.putByte("Difficulty", (byte)this.settings.difficulty().getId());
        nbt.putBoolean("DifficultyLocked", this.difficultyLocked);
        nbt.put("GameRules", this.settings.gameRules().createTag());
        nbt.put("DragonFight", EndDragonFight.Data.CODEC.encodeStart(NbtOps.INSTANCE, this.endDragonFightData).getOrThrow());
        if (playerNBT != null) {
            nbt.put("Player", playerNBT);
        }

        WorldDataConfiguration.CODEC
            .encodeStart(NbtOps.INSTANCE, this.settings.getDataConfiguration())
            .ifSuccess(dataConfigurationTag -> nbt.merge((CompoundTag)dataConfigurationTag))
            .ifError(error -> LOGGER.warn("Failed to encode configuration {}", error.message()));
        if (this.customBossEvents != null) {
            nbt.put("CustomBossEvents", this.customBossEvents);
        }

        nbt.put("ScheduledEvents", this.scheduledEvents.store());
        nbt.putInt("WanderingTraderSpawnDelay", this.wanderingTraderSpawnDelay);
        nbt.putInt("WanderingTraderSpawnChance", this.wanderingTraderSpawnChance);
        if (this.wanderingTraderId != null) {
            nbt.putUUID("WanderingTraderId", this.wanderingTraderId);
        }
        nbt.putString("Bukkit.Version", org.bukkit.Bukkit.getName() + "/" + org.bukkit.Bukkit.getVersion() + "/" + org.bukkit.Bukkit.getBukkitVersion()); // CraftBukkit
        this.world.getWorld().storeBukkitValues(nbt); // CraftBukkit - add pdc
    }

    private static ListTag stringCollectionToTag(Set<String> stringCollection) {
        ListTag listTag = new ListTag();
        stringCollection.stream().map(StringTag::valueOf).forEach(listTag::add);
        return listTag;
    }

    @Override
    public BlockPos getSpawnPos() {
        return this.spawnPos;
    }

    @Override
    public float getSpawnAngle() {
        return this.spawnAngle;
    }

    @Override
    public long getGameTime() {
        return this.gameTime;
    }

    @Override
    public long getDayTime() {
        return this.dayTime;
    }

    @Nullable
    @Override
    public CompoundTag getLoadedPlayerTag() {
        return this.loadedPlayerTag;
    }

    @Override
    public void setGameTime(long time) {
        this.gameTime = time;
    }

    @Override
    public void setDayTime(long time) {
        this.dayTime = time;
    }

    @Override
    public void setSpawn(BlockPos spawnPoint, float angle) {
        this.spawnPos = spawnPoint.immutable();
        this.spawnAngle = angle;
    }

    @Override
    public String getLevelName() {
        return this.settings.levelName();
    }

    @Override
    public int getVersion() {
        return this.version;
    }

    @Override
    public int getClearWeatherTime() {
        return this.clearWeatherTime;
    }

    @Override
    public void setClearWeatherTime(int time) {
        this.clearWeatherTime = time;
    }

    @Override
    public boolean isThundering() {
        return this.thundering;
    }

    @Override
    public void setThundering(boolean thundering) {
        // Paper start - Add cause to Weather/ThunderChangeEvents
        this.setThundering(thundering, org.bukkit.event.weather.ThunderChangeEvent.Cause.UNKNOWN);
    }
    public void setThundering(boolean thundering, org.bukkit.event.weather.ThunderChangeEvent.Cause cause) {
        // Paper end - Add cause to Weather/ThunderChangeEvents
        // CraftBukkit start
        if (this.thundering == thundering) {
            return;
        }

        org.bukkit.World world = org.bukkit.Bukkit.getWorld(this.getLevelName());
        if (world != null) {
            org.bukkit.event.weather.ThunderChangeEvent thunder = new org.bukkit.event.weather.ThunderChangeEvent(world, thundering, cause); // Paper - Add cause to Weather/ThunderChangeEvents
            org.bukkit.Bukkit.getServer().getPluginManager().callEvent(thunder);
            if (thunder.isCancelled()) {
                return;
            }
        }
        // CraftBukkit end
        this.thundering = thundering;
    }

    @Override
    public int getThunderTime() {
        return this.thunderTime;
    }

    @Override
    public void setThunderTime(int time) {
        this.thunderTime = time;
    }

    @Override
    public boolean isRaining() {
        return this.raining;
    }

    @Override
    public void setRaining(boolean isRaining) {
        // Paper start - Add cause to Weather/ThunderChangeEvents
        this.setRaining(isRaining, org.bukkit.event.weather.WeatherChangeEvent.Cause.UNKNOWN);
    }

    public void setRaining(boolean isRaining, org.bukkit.event.weather.WeatherChangeEvent.Cause cause) {
        // Paper end - Add cause to Weather/ThunderChangeEvents
        // CraftBukkit start
        if (this.raining == isRaining) {
            return;
        }

        org.bukkit.World world = org.bukkit.Bukkit.getWorld(this.getLevelName());
        if (world != null) {
            org.bukkit.event.weather.WeatherChangeEvent weather = new org.bukkit.event.weather.WeatherChangeEvent(world, isRaining, cause); // Paper - Add cause to Weather/ThunderChangeEvents
            org.bukkit.Bukkit.getServer().getPluginManager().callEvent(weather);
            if (weather.isCancelled()) {
                return;
            }
        }
        // CraftBukkit end
        this.raining = isRaining;
    }

    @Override
    public int getRainTime() {
        return this.rainTime;
    }

    @Override
    public void setRainTime(int time) {
        this.rainTime = time;
    }

    @Override
    public GameType getGameType() {
        return this.settings.gameType();
    }

    @Override
    public void setGameType(GameType type) {
        this.settings = this.settings.withGameType(type);
    }

    @Override
    public boolean isHardcore() {
        return this.settings.hardcore();
    }

    @Override
    public boolean isAllowCommands() {
        return this.settings.allowCommands();
    }

    @Override
    public boolean isInitialized() {
        return this.initialized;
    }

    @Override
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    @Override
    public GameRules getGameRules() {
        return this.settings.gameRules();
    }

    @Override
    public WorldBorder.Settings getWorldBorder() {
        return this.worldBorder;
    }

    @Override
    public void setWorldBorder(WorldBorder.Settings serializer) {
        this.worldBorder = serializer;
    }

    @Override
    public Difficulty getDifficulty() {
        return this.settings.difficulty();
    }

    @Override
    public void setDifficulty(Difficulty difficulty) {
        this.settings = this.settings.withDifficulty(difficulty);
        // CraftBukkit start
        net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket packet = new net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket(this.getDifficulty(), this.isDifficultyLocked());
        for (net.minecraft.server.level.ServerPlayer player : (java.util.List<net.minecraft.server.level.ServerPlayer>) (java.util.List) this.world.players()) {
            player.connection.send(packet);
        }
        // CraftBukkit end
    }

    @Override
    public boolean isDifficultyLocked() {
        return this.difficultyLocked;
    }

    @Override
    public void setDifficultyLocked(boolean locked) {
        this.difficultyLocked = locked;
    }

    @Override
    public TimerQueue<MinecraftServer> getScheduledEvents() {
        return this.scheduledEvents;
    }

    @Override
    public void fillCrashReportCategory(CrashReportCategory crashReportCategory, LevelHeightAccessor level) {
        ServerLevelData.super.fillCrashReportCategory(crashReportCategory, level);
        WorldData.super.fillCrashReportCategory(crashReportCategory);
    }

    @Override
    public WorldOptions worldGenOptions() {
        return this.worldOptions;
    }

    @Override
    public boolean isFlatWorld() {
        return this.specialWorldProperty == PrimaryLevelData.SpecialWorldProperty.FLAT;
    }

    @Override
    public boolean isDebugWorld() {
        return this.specialWorldProperty == PrimaryLevelData.SpecialWorldProperty.DEBUG;
    }

    @Override
    public Lifecycle worldGenSettingsLifecycle() {
        return this.worldGenSettingsLifecycle;
    }

    @Override
    public EndDragonFight.Data endDragonFightData() {
        return this.endDragonFightData;
    }

    @Override
    public void setEndDragonFightData(EndDragonFight.Data endDragonFightData) {
        this.endDragonFightData = endDragonFightData;
    }

    @Override
    public WorldDataConfiguration getDataConfiguration() {
        return this.settings.getDataConfiguration();
    }

    @Override
    public void setDataConfiguration(WorldDataConfiguration dataConfiguration) {
        this.settings = this.settings.withDataConfiguration(dataConfiguration);
    }

    @Nullable
    @Override
    public CompoundTag getCustomBossEvents() {
        return this.customBossEvents;
    }

    @Override
    public void setCustomBossEvents(@Nullable CompoundTag nbt) {
        this.customBossEvents = nbt;
    }

    @Override
    public int getWanderingTraderSpawnDelay() {
        return this.wanderingTraderSpawnDelay;
    }

    @Override
    public void setWanderingTraderSpawnDelay(int delay) {
        this.wanderingTraderSpawnDelay = delay;
    }

    @Override
    public int getWanderingTraderSpawnChance() {
        return this.wanderingTraderSpawnChance;
    }

    @Override
    public void setWanderingTraderSpawnChance(int chance) {
        this.wanderingTraderSpawnChance = chance;
    }

    @Nullable
    @Override
    public UUID getWanderingTraderId() {
        return this.wanderingTraderId;
    }

    @Override
    public void setWanderingTraderId(UUID id) {
        this.wanderingTraderId = id;
    }

    @Override
    public void setModdedInfo(String name, boolean isModded) {
        this.knownServerBrands.add(name);
        this.wasModded |= isModded;
    }

    @Override
    public boolean wasModded() {
        return this.wasModded;
    }

    @Override
    public Set<String> getKnownServerBrands() {
        return ImmutableSet.copyOf(this.knownServerBrands);
    }

    @Override
    public Set<String> getRemovedFeatureFlags() {
        return Set.copyOf(this.removedFeatureFlags);
    }

    @Override
    public ServerLevelData overworldData() {
        return this;
    }

    @Override
    public LevelSettings getLevelSettings() {
        return this.settings.copy();
    }

    // CraftBukkit start - Check if the name stored in NBT is the correct one
    public void checkName(String name) {
        if (!this.settings.levelName.equals(name)) {
            this.settings.levelName = name;
        }
    }
    // CraftBukkit end

    @Deprecated
    public static enum SpecialWorldProperty {
        NONE,
        FLAT,
        DEBUG;
    }
}
