package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import java.io.File;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.FileUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.slf4j.Logger;

public abstract class PlayerList {
    public static final File USERBANLIST_FILE = new File("banned-players.json");
    public static final File IPBANLIST_FILE = new File("banned-ips.json");
    public static final File OPLIST_FILE = new File("ops.json");
    public static final File WHITELIST_FILE = new File("whitelist.json");
    public static final Component CHAT_FILTERED_FULL = Component.translatable("chat.filtered_full");
    public static final Component DUPLICATE_LOGIN_DISCONNECT_MESSAGE = Component.translatable("multiplayer.disconnect.duplicate_login");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SEND_PLAYER_INFO_INTERVAL = 600;
    private static final SimpleDateFormat BAN_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
    private final MinecraftServer server;
    public final List<ServerPlayer> players = new java.util.concurrent.CopyOnWriteArrayList(); // CraftBukkit - ArrayList -> CopyOnWriteArrayList: Iterator safety
    private final Map<UUID, ServerPlayer> playersByUUID = Maps.newHashMap();
    private final UserBanList bans = new UserBanList(USERBANLIST_FILE);
    private final IpBanList ipBans = new IpBanList(IPBANLIST_FILE);
    private final ServerOpList ops = new ServerOpList(OPLIST_FILE);
    private final UserWhiteList whitelist = new UserWhiteList(WHITELIST_FILE);
    // CraftBukkit start
    // private final Map<UUID, ServerStatsCounter> stats = Maps.newHashMap();
    // private final Map<UUID, PlayerAdvancements> advancements = Maps.newHashMap();
    // CraftBukkit end
    public final PlayerDataStorage playerIo;
    private boolean doWhiteList;
    private final LayeredRegistryAccess<RegistryLayer> registries;
    public int maxPlayers;
    private int viewDistance;
    private int simulationDistance;
    private boolean allowCommandsForAllPlayers;
    private static final boolean ALLOW_LOGOUTIVATOR = false;
    private int sendAllPlayerInfoIn;

    // CraftBukkit start
    private org.bukkit.craftbukkit.CraftServer cserver;
    private final Map<String,ServerPlayer> playersByName = new java.util.HashMap<>();
    public @Nullable String collideRuleTeamName; // Paper - Configurable player collision

    public PlayerList(MinecraftServer server, LayeredRegistryAccess<RegistryLayer> registries, PlayerDataStorage playerIo, int maxPlayers) {
        this.cserver = server.server = new org.bukkit.craftbukkit.CraftServer((net.minecraft.server.dedicated.DedicatedServer) server, this);
        server.console = new com.destroystokyo.paper.console.TerminalConsoleCommandSender(); // Paper
        // CraftBukkit end
        this.server = server;
        this.registries = registries;
        this.maxPlayers = maxPlayers;
        this.playerIo = playerIo;
    }

    abstract public void loadAndSaveFiles(); // Paper - fix converting txt to json file; moved from DedicatedPlayerList constructor

    public void placeNewPlayer(Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
        player.isRealPlayer = true; // Paper
        player.loginTime = System.currentTimeMillis(); // Paper - Replace OfflinePlayer#getLastPlayed
        GameProfile gameProfile = player.getGameProfile();
        GameProfileCache profileCache = this.server.getProfileCache();
        String string;
        if (profileCache != null) {
            Optional<GameProfile> optional = profileCache.get(gameProfile.getId());
            string = optional.map(GameProfile::getName).orElse(gameProfile.getName());
            profileCache.add(gameProfile);
        } else {
            string = gameProfile.getName();
        }

        Optional<CompoundTag> optional = this.load(player);
        // CraftBukkit start - Better rename detection
        if (optional.isPresent()) {
            CompoundTag nbttagcompound = optional.get();
            if (nbttagcompound.contains("bukkit")) {
                CompoundTag bukkit = nbttagcompound.getCompound("bukkit");
                string = bukkit.contains("lastKnownName", 8) ? bukkit.getString("lastKnownName") : string;
            }
        }
        // CraftBukkit end
        // Paper start - move logic in Entity to here, to use bukkit supplied world UUID & reset to main world spawn if no valid world is found
        ResourceKey<Level> resourceKey = null; // Paper
        boolean[] invalidPlayerWorld = {false};
        bukkitData: if (optional.isPresent()) {
            // The main way for bukkit worlds to store the world is the world UUID despite mojang adding custom worlds
            final org.bukkit.World bWorld;
            if (optional.get().contains("WorldUUIDMost") && optional.get().contains("WorldUUIDLeast")) {
                bWorld = org.bukkit.Bukkit.getServer().getWorld(new UUID(optional.get().getLong("WorldUUIDMost"), optional.get().getLong("WorldUUIDLeast")));
            } else if (optional.get().contains("world", net.minecraft.nbt.Tag.TAG_STRING)) { // Paper - legacy bukkit world name
                bWorld = org.bukkit.Bukkit.getServer().getWorld(optional.get().getString("world"));
            } else {
                break bukkitData; // if neither of the bukkit data points exist, proceed to the vanilla migration section
            }
            if (bWorld != null) {
                resourceKey = ((org.bukkit.craftbukkit.CraftWorld) bWorld).getHandle().dimension();
            } else {
                resourceKey = Level.OVERWORLD;
                invalidPlayerWorld[0] = true;
            }
        }
        if (resourceKey == null) { // only run the vanilla logic if we haven't found a world from the bukkit data
        // Below is the vanilla way of getting the dimension, this is for migration from vanilla servers
        resourceKey = optional.<ResourceKey<Level>>flatMap(
                compoundTag -> {
                    com.mojang.serialization.DataResult<ResourceKey<Level>> dataResult = DimensionType.parseLegacy(new Dynamic<>(NbtOps.INSTANCE, compoundTag.get("Dimension")));
                    final Optional<ResourceKey<Level>> result = dataResult.resultOrPartial(LOGGER::error);
                    invalidPlayerWorld[0] = result.isEmpty(); // reset to main world spawn if no valid world is found
                    return result;
                }
            )
            .orElse(Level.OVERWORLD); // revert to vanilla default main world, this isn't an "invalid world" since no player data existed
        }
        // Paper end
        ServerLevel level = this.server.getLevel(resourceKey);
        ServerLevel serverLevel;
        if (level == null) {
            LOGGER.warn("Unknown respawn dimension {}, defaulting to overworld", resourceKey);
            serverLevel = this.server.overworld();
            invalidPlayerWorld[0] = true; // Paper - reset to main world if no world with parsed value is found
        } else {
            serverLevel = level;
        }

        // Paper start - Entity#getEntitySpawnReason
        if (optional.isEmpty()) {
            player.spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT; // set Player SpawnReason to DEFAULT on first login
            // Paper start - reset to main world spawn if first spawn or invalid world
        }
        if (optional.isEmpty() || invalidPlayerWorld[0]) {
            // Paper end - reset to main world spawn if first spawn or invalid world
            player.moveTo(player.adjustSpawnLocation(serverLevel, serverLevel.getSharedSpawnPos()).getBottomCenter(), serverLevel.getSharedSpawnAngle(), 0.0F); // Paper - MC-200092 - fix first spawn pos yaw being ignored
        }
        // Paper end - Entity#getEntitySpawnReason
        player.setServerLevel(serverLevel);
        String loggableAddress = connection.getLoggableAddress(this.server.logIPs());
        // Spigot start - spawn location event
        org.bukkit.entity.Player spawnPlayer = player.getBukkitEntity();
        org.spigotmc.event.player.PlayerSpawnLocationEvent ev = new org.spigotmc.event.player.PlayerSpawnLocationEvent(spawnPlayer, spawnPlayer.getLocation());
        this.cserver.getPluginManager().callEvent(ev);

        org.bukkit.Location loc = ev.getSpawnLocation();
        serverLevel = ((org.bukkit.craftbukkit.CraftWorld) loc.getWorld()).getHandle();

        player.spawnIn(serverLevel);
        // Paper start - set raw so we aren't fully joined to the world (not added to chunk or world)
        player.setPosRaw(loc.getX(), loc.getY(), loc.getZ());
        player.setRot(loc.getYaw(), loc.getPitch());
        // Paper end - set raw so we aren't fully joined to the world
        // Spigot end
        // LOGGER.info( // CraftBukkit - Moved message to after join
        //     "{}[{}] logged in with entity id {} at ({}, {}, {})",
        //     player.getName().getString(),
        //     loggableAddress,
        //     player.getId(),
        //     player.getX(),
        //     player.getY(),
        //     player.getZ()
        // );
        LevelData levelData = serverLevel.getLevelData();
        player.loadGameTypes(optional.orElse(null));
        ServerGamePacketListenerImpl serverGamePacketListenerImpl = new ServerGamePacketListenerImpl(this.server, connection, player, cookie);
        connection.setupInboundProtocol(
            GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(this.server.registryAccess())), serverGamePacketListenerImpl
        );
        GameRules gameRules = serverLevel.getGameRules();
        boolean _boolean = gameRules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
        boolean _boolean1 = gameRules.getBoolean(GameRules.RULE_REDUCEDDEBUGINFO);
        boolean _boolean2 = gameRules.getBoolean(GameRules.RULE_LIMITED_CRAFTING);
        serverGamePacketListenerImpl.send(
            new ClientboundLoginPacket(
                player.getId(),
                levelData.isHardcore(),
                this.server.levelKeys(),
                this.getMaxPlayers(),
                serverLevel.spigotConfig.viewDistance,// Spigot - view distance
                serverLevel.spigotConfig.simulationDistance,
                _boolean1,
                !_boolean,
                _boolean2,
                player.createCommonSpawnInfo(serverLevel),
                this.server.enforceSecureProfile()
            )
        );
        player.getBukkitEntity().sendSupportedChannels(); // CraftBukkit
        serverGamePacketListenerImpl.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
        serverGamePacketListenerImpl.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
        serverGamePacketListenerImpl.send(new ClientboundSetHeldSlotPacket(player.getInventory().selected));
        RecipeManager recipeManager = this.server.getRecipeManager();
        serverGamePacketListenerImpl.send(
            new ClientboundUpdateRecipesPacket(recipeManager.getSynchronizedItemProperties(), recipeManager.getSynchronizedStonecutterRecipes())
        );
        this.sendPlayerPermissionLevel(player);
        player.getStats().markAllDirty();
        player.getRecipeBook().sendInitialRecipeBook(player);
        this.updateEntireScoreboard(serverLevel.getScoreboard(), player);
        this.server.invalidateStatus();
        MutableComponent mutableComponent;
        if (player.getGameProfile().getName().equalsIgnoreCase(string)) {
            mutableComponent = Component.translatable("multiplayer.player.joined", player.getDisplayName());
        } else {
            mutableComponent = Component.translatable("multiplayer.player.joined.renamed", player.getDisplayName(), string);
        }

        // CraftBukkit start
        mutableComponent.withStyle(ChatFormatting.YELLOW);
        Component joinMessage = mutableComponent; // Paper - Adventure
        serverGamePacketListenerImpl.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        ServerStatus status = this.server.getStatus();
        if (status != null && !cookie.transferred()) {
            player.sendServerStatus(status);
        }

        // player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(this.players)); // CraftBukkit - replaced with loop below
        this.players.add(player);
        this.playersByName.put(player.getScoreboardName().toLowerCase(java.util.Locale.ROOT), player); // Spigot
        this.playersByUUID.put(player.getUUID(), player);
        // this.broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player))); // CraftBukkit - replaced with loop below
        // Paper start - Fire PlayerJoinEvent when Player is actually ready; correctly register player BEFORE PlayerJoinEvent, so the entity is valid and doesn't require tick delay hacks
        player.supressTrackerForLogin = true;
        serverLevel.addNewPlayer(player);
        this.server.getCustomBossEvents().onPlayerConnect(player); // see commented out section below serverLevel.addPlayerJoin(player);
        // Paper end - Fire PlayerJoinEvent when Player is actually ready
        player.loadAndSpawnEnderpearls(optional);
        player.loadAndSpawnParentVehicle(optional);
        // CraftBukkit start
        org.bukkit.craftbukkit.entity.CraftPlayer bukkitPlayer = player.getBukkitEntity();

        // Ensure that player inventory is populated with its viewer
        player.containerMenu.transferTo(player.containerMenu, bukkitPlayer);

        org.bukkit.event.player.PlayerJoinEvent playerJoinEvent = new org.bukkit.event.player.PlayerJoinEvent(bukkitPlayer, io.papermc.paper.adventure.PaperAdventure.asAdventure(mutableComponent)); // Paper - Adventure
        this.cserver.getPluginManager().callEvent(playerJoinEvent);

        if (!player.connection.isAcceptingMessages()) {
            return;
        }

        final net.kyori.adventure.text.Component jm = playerJoinEvent.joinMessage();

        if (jm != null && !jm.equals(net.kyori.adventure.text.Component.empty())) { // Paper - Adventure
            joinMessage = io.papermc.paper.adventure.PaperAdventure.asVanilla(jm); // Paper - Adventure
            this.server.getPlayerList().broadcastSystemMessage(joinMessage, false); // Paper - Adventure
        }
        // CraftBukkit end

        // CraftBukkit start - sendAll above replaced with this loop
        ClientboundPlayerInfoUpdatePacket packet = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)); // Paper - Add Listing API for Player

        final List<ServerPlayer> onlinePlayers = Lists.newArrayListWithExpectedSize(this.players.size() - 1); // Paper - Use single player info update packet on join
        for (int i = 0; i < this.players.size(); ++i) {
            ServerPlayer entityplayer1 = (ServerPlayer) this.players.get(i);

            if (entityplayer1.getBukkitEntity().canSee(bukkitPlayer)) {
                // Paper start - Add Listing API for Player
                if (entityplayer1.getBukkitEntity().isListed(bukkitPlayer)) {
                // Paper end - Add Listing API for Player
                entityplayer1.connection.send(packet);
                // Paper start - Add Listing API for Player
                } else {
                    entityplayer1.connection.send(ClientboundPlayerInfoUpdatePacket.createSinglePlayerInitializing(player, false));
                }
                // Paper end - Add Listing API for Player
            }

            if (entityplayer1 == player || !bukkitPlayer.canSee(entityplayer1.getBukkitEntity())) { // Paper - Use single player info update packet on join; Don't include joining player
                continue;
            }

            onlinePlayers.add(entityplayer1); // Paper - Use single player info update packet on join
        }
        // Paper start - Use single player info update packet on join
        if (!onlinePlayers.isEmpty()) {
            player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(onlinePlayers, player)); // Paper - Add Listing API for Player
        }
        // Paper end - Use single player info update packet on join
        player.sentListPacket = true;
        player.supressTrackerForLogin = false; // Paper - Fire PlayerJoinEvent when Player is actually ready
        ((ServerLevel)player.level()).getChunkSource().chunkMap.addEntity(player); // Paper - Fire PlayerJoinEvent when Player is actually ready; track entity now
        // CraftBukkit end

        //player.refreshEntityData(player); // CraftBukkit - BungeeCord#2321, send complete data to self on spawn // Paper - THIS IS NOT NEEDED ANYMORE

        this.sendLevelInfo(player, serverLevel);

        // CraftBukkit start - Only add if the player wasn't moved in the event
        if (player.level() == serverLevel && !serverLevel.players().contains(player)) {
            serverLevel.addNewPlayer(player);
            this.server.getCustomBossEvents().onPlayerConnect(player);
        }

        serverLevel = player.serverLevel(); // CraftBukkit - Update in case join event changed it
        // CraftBukkit end
        this.sendActivePlayerEffects(player);
        // Paper - move loading pearls / parent vehicle up
        player.initInventoryMenu();
        // CraftBukkit - Moved from above, added world
        // Paper start - Configurable player collision; Add to collideRule team if needed
        final net.minecraft.world.scores.Scoreboard scoreboard = this.getServer().getLevel(Level.OVERWORLD).getScoreboard();
        final PlayerTeam collideRuleTeam = scoreboard.getPlayerTeam(this.collideRuleTeamName);
        if (this.collideRuleTeamName != null && collideRuleTeam != null && player.getTeam() == null) {
            scoreboard.addPlayerToTeam(player.getScoreboardName(), collideRuleTeam);
        }
        // Paper end - Configurable player collision
        org.purpurmc.purpur.task.BossBarTask.addToAll(player); // Purpur - Implement TPSBar
        PlayerList.LOGGER.info("{}[{}] logged in with entity id {} at ([{}]{}, {}, {})", player.getName().getString(), loggableAddress, player.getId(), serverLevel.serverLevelData.getLevelName(), player.getX(), player.getY(), player.getZ());
        // Paper start - Send empty chunk, so players aren't stuck in the world loading screen with our chunk system not sending chunks when dead
        if (player.isDeadOrDying()) {
            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> plains = serverLevel.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                    .getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(
                    new net.minecraft.world.level.chunk.EmptyLevelChunk(serverLevel, player.chunkPosition(), plains),
                    serverLevel.getLightEngine(), (java.util.BitSet)null, (java.util.BitSet) null, true) // Paper - Anti-Xray
            );
        }
        // Paper end - Send empty chunk
    }

    public void updateEntireScoreboard(ServerScoreboard scoreboard, ServerPlayer player) {
        Set<Objective> set = Sets.newHashSet();

        for (PlayerTeam playerTeam : scoreboard.getPlayerTeams()) {
            player.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(playerTeam, true));
        }

        for (DisplaySlot displaySlot : DisplaySlot.values()) {
            Objective displayObjective = scoreboard.getDisplayObjective(displaySlot);
            if (displayObjective != null && !set.contains(displayObjective)) {
                for (Packet<?> packet : scoreboard.getStartTrackingPackets(displayObjective)) {
                    player.connection.send(packet);
                }

                set.add(displayObjective);
            }
        }
    }

    public void addWorldborderListener(ServerLevel level) {
        if (this.playerIo != null) return; // CraftBukkit
        level.getWorldBorder().addListener(new BorderChangeListener() {
            @Override
            public void onBorderSizeSet(WorldBorder border, double size) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderSizePacket(border), border.world); // CraftBukkit
            }

            @Override
            public void onBorderSizeLerping(WorldBorder border, double oldSize, double newSize, long time) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderLerpSizePacket(border), border.world); // CraftBukkit
            }

            @Override
            public void onBorderCenterSet(WorldBorder border, double x, double z) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderCenterPacket(border), border.world); // CraftBukkit
            }

            @Override
            public void onBorderSetWarningTime(WorldBorder border, int warningTime) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderWarningDelayPacket(border), border.world); // CraftBukkit
            }

            @Override
            public void onBorderSetWarningBlocks(WorldBorder border, int warningBlocks) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderWarningDistancePacket(border), border.world); // CraftBukkit
            }

            @Override
            public void onBorderSetDamagePerBlock(WorldBorder border, double damagePerBlock) {
            }

            @Override
            public void onBorderSetDamageSafeZOne(WorldBorder border, double damageSafeZone) {
            }
        });
    }

    public Optional<CompoundTag> load(ServerPlayer player) {
        CompoundTag loadedPlayerTag = this.server.getWorldData().getLoadedPlayerTag();
        Optional<CompoundTag> optional;
        if (this.server.isSingleplayerOwner(player.getGameProfile()) && loadedPlayerTag != null) {
            optional = Optional.of(loadedPlayerTag);
            player.load(loadedPlayerTag);
            LOGGER.debug("loading single player");
        } else {
            optional = this.playerIo.load(player);
        }

        return optional;
    }

    protected void save(ServerPlayer player) {
        if (!player.getBukkitEntity().isPersistent()) return; // CraftBukkit
        player.lastSave = MinecraftServer.currentTick; // Paper - Incremental chunk and player saving
        this.playerIo.save(player);
        ServerStatsCounter serverStatsCounter = player.getStats(); // CraftBukkit
        if (serverStatsCounter != null) {
            serverStatsCounter.save();
        }

        PlayerAdvancements playerAdvancements = player.getAdvancements(); // CraftBukkit
        if (playerAdvancements != null) {
            playerAdvancements.save();
        }
    }

    public net.kyori.adventure.text.Component remove(ServerPlayer player) { // CraftBukkit - return string // Paper - return Component
        // Paper start - Fix kick event leave message not being sent
        return this.remove(player, net.kyori.adventure.text.Component.translatable("multiplayer.player.left", net.kyori.adventure.text.format.NamedTextColor.YELLOW, io.papermc.paper.configuration.GlobalConfiguration.get().messages.useDisplayNameInQuitMessage ? player.getBukkitEntity().displayName() : io.papermc.paper.adventure.PaperAdventure.asAdventure(player.getDisplayName())));
    }
    public net.kyori.adventure.text.Component remove(ServerPlayer player, net.kyori.adventure.text.Component leaveMessage) {
        // Paper end - Fix kick event leave message not being sent
        org.purpurmc.purpur.task.BossBarTask.removeFromAll(player.getBukkitEntity()); // Purpur - Implement TPSBar
        ServerLevel serverLevel = player.serverLevel();
        player.awardStat(Stats.LEAVE_GAME);
        // CraftBukkit start - Quitting must be before we do final save of data, in case plugins need to modify it
        // See SPIGOT-5799, SPIGOT-6145
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.DISCONNECT); // Paper - Inventory close reason
        }

        org.bukkit.event.player.PlayerQuitEvent playerQuitEvent = new org.bukkit.event.player.PlayerQuitEvent(player.getBukkitEntity(), leaveMessage, player.quitReason); // Paper - Adventure & Add API for quit reason
        this.cserver.getPluginManager().callEvent(playerQuitEvent);
        player.getBukkitEntity().disconnect(playerQuitEvent.getQuitMessage());

        if (this.server.isSameThread()) player.doTick(); // SPIGOT-924 // Paper - Improved watchdog support; don't tick during emergency shutdowns
        // CraftBukkit end

        // Paper start - Configurable player collision; Remove from collideRule team if needed
        if (this.collideRuleTeamName != null) {
            final net.minecraft.world.scores.Scoreboard scoreBoard = this.server.getLevel(Level.OVERWORLD).getScoreboard();
            final PlayerTeam team = scoreBoard.getPlayersTeam(this.collideRuleTeamName);
            if (player.getTeam() == team && team != null) {
                scoreBoard.removePlayerFromTeam(player.getScoreboardName(), team);
            }
        }
        // Paper end - Configurable player collision

        // Paper - Drop carried item when player has disconnected
        if (!player.containerMenu.getCarried().isEmpty()) {
            net.minecraft.world.item.ItemStack carried = player.containerMenu.getCarried();
            player.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
            player.drop(carried, false);
        }
        // Paper end - Drop carried item when player has disconnected
        this.save(player);
        if (player.isPassenger()) {
            Entity rootVehicle = player.getRootVehicle();
            if (rootVehicle.hasExactlyOnePlayerPassenger()) {
                LOGGER.debug("Removing player mount");
                player.stopRiding();
                rootVehicle.getPassengersAndSelf().forEach(entity -> {
                    // Paper start - Fix villager boat exploit
                    if (entity instanceof net.minecraft.world.entity.npc.AbstractVillager villager) {
                        final net.minecraft.world.entity.player.Player human = villager.getTradingPlayer();
                        if (human != null) {
                            villager.setTradingPlayer(null);
                        }
                    }
                    // Paper end - Fix villager boat exploit
                    entity.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER, org.bukkit.event.entity.EntityRemoveEvent.Cause.PLAYER_QUIT); // CraftBukkit - add Bukkit remove cause
                });
            }
        }

        player.unRide();

        for (ThrownEnderpearl thrownEnderpearl : player.getEnderPearls()) {
            // Paper start - Allow using old ender pearl behavior
            if (!thrownEnderpearl.level().paperConfig().misc.legacyEnderPearlBehavior) {
                thrownEnderpearl.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER, org.bukkit.event.entity.EntityRemoveEvent.Cause.PLAYER_QUIT); // CraftBukkit - add Bukkit remove cause
            } else {
                thrownEnderpearl.cachedOwner = null;
            }
            // Paper end - Allow using old ender pearl behavior
        }

        serverLevel.removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        player.retireScheduler(); // Paper - Folia schedulers
        player.getAdvancements().stopListening();
        this.players.remove(player);
        this.playersByName.remove(player.getScoreboardName().toLowerCase(java.util.Locale.ROOT)); // Spigot
        this.server.getCustomBossEvents().onPlayerDisconnect(player);
        UUID uuid = player.getUUID();
        ServerPlayer serverPlayer = this.playersByUUID.get(uuid);
        if (serverPlayer == player) {
            this.playersByUUID.remove(uuid);
            // CraftBukkit start
            // this.stats.remove(uuid);
            // this.advancements.remove(uuid);
            // CraftBukkit end
        }

        // CraftBukkit start
        // this.broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
        ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID()));
        for (int i = 0; i < this.players.size(); i++) {
            ServerPlayer otherPlayer = (ServerPlayer) this.players.get(i);

            if (otherPlayer.getBukkitEntity().canSee(player.getBukkitEntity())) {
                otherPlayer.connection.send(packet);
            } else {
                otherPlayer.getBukkitEntity().onEntityRemove(player);
            }
        }
        // This removes the scoreboard (and player reference) for the specific player in the manager
        this.cserver.getScoreboardManager().removePlayer(player.getBukkitEntity());
        // CraftBukkit end
        return playerQuitEvent.quitMessage(); // Paper - Adventure
    }

    // CraftBukkit start - Whole method, SocketAddress to LoginListener, added hostname to signature, return EntityPlayer
    public ServerPlayer canPlayerLogin(net.minecraft.server.network.ServerLoginPacketListenerImpl loginlistener, GameProfile gameProfile) {
        // if (this.bans.isBanned(gameProfile)) {
        //     UserBanListEntry userBanListEntry = this.bans.get(gameProfile);
        // Moved from processLogin
        UUID uuid = gameProfile.getId();
        List<ServerPlayer> list = Lists.newArrayList();

        ServerPlayer entityplayer;

        for (int i = 0; i < this.players.size(); ++i) {
            entityplayer = (ServerPlayer) this.players.get(i);
            if (entityplayer.getUUID().equals(uuid) || (io.papermc.paper.configuration.GlobalConfiguration.get().proxies.isProxyOnlineMode() && entityplayer.getGameProfile().getName().equalsIgnoreCase(gameProfile.getName()))) { // Paper - validate usernames
                list.add(entityplayer);
            }
        }

        java.util.Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            entityplayer = (ServerPlayer) iterator.next();
            this.save(entityplayer); // CraftBukkit - Force the player's inventory to be saved
            entityplayer.connection.disconnect(Component.translatable("multiplayer.disconnect.duplicate_login"), org.bukkit.event.player.PlayerKickEvent.Cause.DUPLICATE_LOGIN); // Paper - kick event cause
        }

        // Instead of kicking then returning, we need to store the kick reason
        // in the event, check with plugins to see if it's ok, and THEN kick
        // depending on the outcome.
        SocketAddress socketAddress = loginlistener.connection.getRemoteAddress();

        ServerPlayer entity = new ServerPlayer(this.server, this.server.getLevel(Level.OVERWORLD), gameProfile, ClientInformation.createDefault());
        entity.transferCookieConnection = loginlistener;
        org.bukkit.entity.Player player = entity.getBukkitEntity();
        org.bukkit.event.player.PlayerLoginEvent event = new org.bukkit.event.player.PlayerLoginEvent(player, loginlistener.connection.hostname, ((java.net.InetSocketAddress) socketAddress).getAddress(), ((java.net.InetSocketAddress) loginlistener.connection.channel.remoteAddress()).getAddress());

        // Paper start - Fix MC-158900
        UserBanListEntry userBanListEntry;
        if (this.bans.isBanned(gameProfile) && (userBanListEntry = this.bans.get(gameProfile)) != null) {
            // Paper end - Fix MC-158900
            MutableComponent mutableComponent = Component.translatable("multiplayer.disconnect.banned.reason", userBanListEntry.getReason());
            if (userBanListEntry.getExpires() != null) {
                mutableComponent.append(
                    Component.translatable("multiplayer.disconnect.banned.expiration", BAN_DATE_FORMAT.format(userBanListEntry.getExpires()))
                );
            }

            // return mutableComponent;
            event.disallow(org.bukkit.event.player.PlayerLoginEvent.Result.KICK_BANNED, io.papermc.paper.adventure.PaperAdventure.asAdventure(mutableComponent)); // Paper - Adventure
        } else if (!this.isWhiteListed(gameProfile, event)) { // Paper - ProfileWhitelistVerifyEvent
            // return Component.translatable("multiplayer.disconnect.not_whitelisted");
            //event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(org.spigotmc.SpigotConfig.whitelistMessage)); // Spigot // Paper - Adventure - moved to isWhitelisted
        } else if (this.ipBans.isBanned(socketAddress) && getIpBans().get(socketAddress) != null && !this.getIpBans().get(socketAddress).hasExpired()) { // Paper - fix NPE with temp ip bans
            IpBanListEntry ipBanListEntry = this.ipBans.get(socketAddress);
            MutableComponent mutableComponent = Component.translatable("multiplayer.disconnect.banned_ip.reason", ipBanListEntry.getReason());
            if (ipBanListEntry.getExpires() != null) {
                mutableComponent.append(
                    Component.translatable("multiplayer.disconnect.banned_ip.expiration", BAN_DATE_FORMAT.format(ipBanListEntry.getExpires()))
                );
            }

            // return mutableComponent;
            event.disallow(org.bukkit.event.player.PlayerLoginEvent.Result.KICK_BANNED, io.papermc.paper.adventure.PaperAdventure.asAdventure(mutableComponent)); // Paper - Adventure
        } else {
            // return this.players.size() >= this.maxPlayers && !this.canBypassPlayerLimit(gameProfile)
            //     ? Component.translatable("multiplayer.disconnect.server_full")
            //     : null;
            if (this.players.size() >= this.maxPlayers && !(player.hasPermission("purpur.joinfullserver") || this.canBypassPlayerLimit(gameProfile))) { // Purpur - Allow player join full server by permission
                event.disallow(org.bukkit.event.player.PlayerLoginEvent.Result.KICK_FULL, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(org.spigotmc.SpigotConfig.serverFullMessage)); // Spigot // Paper - Adventure
            }
        }
        this.cserver.getPluginManager().callEvent(event);
        if (event.getResult() != org.bukkit.event.player.PlayerLoginEvent.Result.ALLOWED) {
            loginlistener.disconnect(io.papermc.paper.adventure.PaperAdventure.asVanilla(event.kickMessage())); // Paper - Adventure
            return null;
        }
        return entity;
    }

    // CraftBukkit start - added EntityPlayer
    public ServerPlayer getPlayerForLogin(GameProfile gameProfile, ClientInformation clientInformation, ServerPlayer player) {
        player.updateOptions(clientInformation);
        return player;
        // CraftBukkit end
    }

    public boolean disconnectAllPlayersWithProfile(GameProfile gameProfile, ServerPlayer player) { // CraftBukkit - added ServerPlayer
        // CraftBukkit start - Moved up
        // UUID id = gameProfile.getId();
        // Set<ServerPlayer> set = Sets.newIdentityHashSet();
        //
        // for (ServerPlayer serverPlayer : this.players) {
        //     if (serverPlayer.getUUID().equals(id)) {
        //         set.add(serverPlayer);
        //     }
        // }
        //
        // ServerPlayer serverPlayer1 = this.playersByUUID.get(gameProfile.getId());
        // if (serverPlayer1 != null) {
        //     set.add(serverPlayer1);
        // }
        //
        // for (ServerPlayer serverPlayer2 : set) {
        //     serverPlayer2.connection.disconnect(DUPLICATE_LOGIN_DISCONNECT_MESSAGE);
        // }
        //
        // return !set.isEmpty();
        return player == null;
        // CraftBukkit end
    }

    // CraftBukkit start
    public ServerPlayer respawn(ServerPlayer player, boolean keepInventory, Entity.RemovalReason reason, org.bukkit.event.player.PlayerRespawnEvent.RespawnReason eventReason) {
        return this.respawn(player, keepInventory, reason, eventReason, null);
    }
    public ServerPlayer respawn(ServerPlayer player, boolean keepInventory, Entity.RemovalReason reason, org.bukkit.event.player.PlayerRespawnEvent.RespawnReason eventReason, org.bukkit.Location location) {
        player.stopRiding(); // CraftBukkit
        this.players.remove(player);
        this.playersByName.remove(player.getScoreboardName().toLowerCase(java.util.Locale.ROOT)); // Spigot
        player.serverLevel().removePlayerImmediately(player, reason);
        // TeleportTransition teleportTransition = player.findRespawnPositionAndUseSpawnBlock(!keepInventory, TeleportTransition.DO_NOTHING);
        // ServerLevel level = teleportTransition.newLevel();
        // ServerPlayer serverPlayer = new ServerPlayer(this.server, level, player.getGameProfile(), player.clientInformation());
        ServerPlayer serverPlayer = player;
        Level fromWorld = player.level();
        player.wonGame = false;
        // CraftBukkit end
        serverPlayer.connection = player.connection;
        serverPlayer.restoreFrom(player, keepInventory);
        serverPlayer.setId(player.getId());
        serverPlayer.setMainArm(player.getMainArm());
        // CraftBukkit - not required, just copies old location into reused entity
        // if (!teleportTransition.missingRespawnBlock()) {
        //     serverPlayer.copyRespawnPosition(player);
        // }

        for (String string : player.getTags()) {
            serverPlayer.addTag(string);
        }
        // Paper start - Add PlayerPostRespawnEvent
        boolean isBedSpawn = false;
        boolean isRespawn = false;
        // Paper end - Add PlayerPostRespawnEvent

        // CraftBukkit start - fire PlayerRespawnEvent
        TeleportTransition teleportTransition;
        if (location == null) {
            teleportTransition = player.findRespawnPositionAndUseSpawnBlock(!keepInventory, TeleportTransition.DO_NOTHING, eventReason);

            if (!keepInventory) player.reset(); // SPIGOT-4785
           // Paper start - Add PlayerPostRespawnEvent
           if (teleportTransition == null) return player; // Early exit, mirrors belows early return for disconnected players in respawn event
           isRespawn = true;
           location = org.bukkit.craftbukkit.util.CraftLocation.toBukkit(teleportTransition.position(), teleportTransition.newLevel().getWorld(), teleportTransition.yRot(), teleportTransition.xRot());
           // Paper end - Add PlayerPostRespawnEvent
        } else {
            teleportTransition = new TeleportTransition(((org.bukkit.craftbukkit.CraftWorld) location.getWorld()).getHandle(), org.bukkit.craftbukkit.util.CraftLocation.toVec3D(location), Vec3.ZERO, location.getYaw(), location.getPitch(), TeleportTransition.DO_NOTHING);
        }
        // Spigot start
        if (teleportTransition == null) { // Paper - Add PlayerPostRespawnEvent - diff on change - spigot early returns if respawn pos is null, that is how they handle disconnected player in respawn event
            return player;
        }
        // Spigot end
        ServerLevel level = teleportTransition.newLevel();
        serverPlayer.spawnIn(level);
        serverPlayer.unsetRemoved();
        serverPlayer.setShiftKeyDown(false);
        Vec3 vec3 = teleportTransition.position();
        serverPlayer.forceSetPositionRotation(vec3.x, vec3.y, vec3.z, teleportTransition.yRot(), teleportTransition.xRot());
        level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.POST_TELEPORT, new net.minecraft.world.level.ChunkPos(net.minecraft.util.Mth.floor(vec3.x()) >> 4, net.minecraft.util.Mth.floor(vec3.z()) >> 4), 1, player.getId()); // Paper - post teleport ticket type
        // CraftBukkit end
        if (teleportTransition.missingRespawnBlock()) {
            serverPlayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
            serverPlayer.setRespawnPosition(null, null, 0f, false, false, com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN); // CraftBukkit - SPIGOT-5988: Clear respawn location when obstructed // Paper - PlayerSetSpawnEvent
        }

        byte b = (byte)(keepInventory ? 1 : 0);
        ServerLevel serverLevel = serverPlayer.serverLevel();
        LevelData levelData = serverLevel.getLevelData();
        serverPlayer.connection.send(new ClientboundRespawnPacket(serverPlayer.createCommonSpawnInfo(serverLevel), b));
        // serverPlayer.connection.teleport(serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), serverPlayer.getYRot(), serverPlayer.getXRot());
        serverPlayer.connection.send(new ClientboundSetChunkCacheRadiusPacket(serverLevel.spigotConfig.viewDistance)); // Spigot
        serverPlayer.connection.send(new ClientboundSetSimulationDistancePacket(serverLevel.spigotConfig.simulationDistance)); // Spigot
        serverPlayer.connection.teleport(org.bukkit.craftbukkit.util.CraftLocation.toBukkit(serverPlayer.position(), serverLevel.getWorld(), serverPlayer.getYRot(), serverPlayer.getXRot())); // CraftBukkit
        serverPlayer.connection.send(new ClientboundSetDefaultSpawnPositionPacket(level.getSharedSpawnPos(), level.getSharedSpawnAngle()));
        serverPlayer.connection.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
        serverPlayer.connection
            .send(new ClientboundSetExperiencePacket(serverPlayer.experienceProgress, serverPlayer.totalExperience, serverPlayer.experienceLevel));
        this.sendActivePlayerEffects(serverPlayer);
        this.sendLevelInfo(serverPlayer, level);
        this.sendPlayerPermissionLevel(serverPlayer);
        if (!serverPlayer.connection.isDisconnected()) {
            level.addRespawnedPlayer(serverPlayer);
            this.players.add(serverPlayer);
            this.playersByName.put(serverPlayer.getScoreboardName().toLowerCase(java.util.Locale.ROOT), serverPlayer); // Spigot
            this.playersByUUID.put(serverPlayer.getUUID(), serverPlayer);
        }
        // serverPlayer.initInventoryMenu();
        serverPlayer.setHealth(serverPlayer.getHealth());
        BlockPos respawnPosition = serverPlayer.getRespawnPosition();
        ServerLevel level1 = this.server.getLevel(serverPlayer.getRespawnDimension());
        if (!keepInventory && respawnPosition != null && level1 != null) {
            BlockState blockState = level1.getBlockState(respawnPosition);
            if (blockState.is(Blocks.RESPAWN_ANCHOR)) {
                serverPlayer.connection
                    .send(
                        new ClientboundSoundPacket(
                            SoundEvents.RESPAWN_ANCHOR_DEPLETE,
                            SoundSource.BLOCKS,
                            respawnPosition.getX(),
                            respawnPosition.getY(),
                            respawnPosition.getZ(),
                            1.0F,
                            1.0F,
                            level.getRandom().nextLong()
                        )
                    );
            }
            // Paper start - Add PlayerPostRespawnEvent
            if (blockState.is(net.minecraft.tags.BlockTags.BEDS) && !teleportTransition.missingRespawnBlock()) {
                isBedSpawn = true;
            }
            // Paper end - Add PlayerPostRespawnEvent
        }
        // Added from changeDimension
        this.sendAllPlayerInfo(player); // Update health, etc...
        player.onUpdateAbilities();
        for (MobEffectInstance mobEffect : player.getActiveEffects()) {
            player.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), mobEffect, false)); // blend = false
        }

        // Fire advancement trigger
        player.triggerDimensionChangeTriggers(level);

        // Don't fire on respawn
        if (fromWorld != level) {
            org.bukkit.event.player.PlayerChangedWorldEvent event = new org.bukkit.event.player.PlayerChangedWorldEvent(player.getBukkitEntity(), fromWorld.getWorld());
            this.server.server.getPluginManager().callEvent(event);
        }

        // Save player file again if they were disconnected
        if (player.connection.isDisconnected()) {
            this.save(player);
        }

        // Paper start - Add PlayerPostRespawnEvent
        if (isRespawn) {
            cserver.getPluginManager().callEvent(new com.destroystokyo.paper.event.player.PlayerPostRespawnEvent(player.getBukkitEntity(), location, isBedSpawn));
        }
        // Paper end - Add PlayerPostRespawnEvent

        // CraftBukkit end

        return serverPlayer;
    }

    public void sendActivePlayerEffects(ServerPlayer player) {
        this.sendActiveEffects(player, player.connection);
    }

    public void sendActiveEffects(LivingEntity entity, ServerGamePacketListenerImpl connection) {
        // Paper start - collect packets
        this.sendActiveEffects(entity, connection::send);
    }
    public void sendActiveEffects(LivingEntity entity, java.util.function.Consumer<Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> packetConsumer) {
        // Paper end - collect packets
        for (MobEffectInstance mobEffectInstance : entity.getActiveEffects()) {
            packetConsumer.accept(new ClientboundUpdateMobEffectPacket(entity.getId(), mobEffectInstance, false)); // Paper - collect packets
        }
    }

    public void sendPlayerPermissionLevel(ServerPlayer player) {
    // Paper start - avoid recalculating permissions if possible
        this.sendPlayerPermissionLevel(player, true);
    }

    public void sendPlayerPermissionLevel(ServerPlayer player, boolean recalculatePermissions) {
    // Paper end - avoid recalculating permissions if possible
        GameProfile gameProfile = player.getGameProfile();
        int profilePermissions = this.server.getProfilePermissions(gameProfile);
        this.sendPlayerPermissionLevel(player, profilePermissions, recalculatePermissions); // Paper - avoid recalculating permissions if possible
    }

    public void tick() {
        if (++this.sendAllPlayerInfoIn > 600) {
            // CraftBukkit start
            for (int i = 0; i < this.players.size(); ++i) {
                final ServerPlayer target = this.players.get(i);

                target.connection.send(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY), com.google.common.collect.Collections2.filter(this.players, t -> target.getBukkitEntity().canSee(t.getBukkitEntity()))));
            }
            // CraftBukkit end
            this.sendAllPlayerInfoIn = 0;
        }
    }

    // CraftBukkit start - add a world/entity limited version
    public void broadcastAll(Packet packet, net.minecraft.world.entity.player.Player entityhuman) {
        for (int i = 0; i < this.players.size(); ++i) {
            ServerPlayer entityplayer =  this.players.get(i);
            if (entityhuman != null && !entityplayer.getBukkitEntity().canSee(entityhuman.getBukkitEntity())) {
                continue;
            }
            ((ServerPlayer) this.players.get(i)).connection.send(packet);
        }
    }

    public void broadcastAll(Packet packet, Level world) {
        for (int i = 0; i < world.players().size(); ++i) {
            ((ServerPlayer) world.players().get(i)).connection.send(packet);
        }

    }
    // CraftBukkit end

    public void broadcastAll(Packet<?> packet) {
        for (ServerPlayer serverPlayer : this.players) {
            serverPlayer.connection.send(packet);
        }
    }

    // Purpur start - Component related conveniences
    public void broadcastMiniMessage(@Nullable String message, boolean overlay) {
        if (message != null && !message.isEmpty()) {
            this.broadcastMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(message), overlay);
        }
    }

    public void broadcastMessage(@Nullable net.kyori.adventure.text.Component message, boolean overlay) {
        if (message != null) {
            this.broadcastSystemMessage(io.papermc.paper.adventure.PaperAdventure.asVanilla(message), overlay);
        }
    }
    // Purpur end - Component related conveniences

    public void broadcastAll(Packet<?> packet, ResourceKey<Level> dimension) {
        for (ServerPlayer serverPlayer : this.players) {
            if (serverPlayer.level().dimension() == dimension) {
                serverPlayer.connection.send(packet);
            }
        }
    }

    public void broadcastSystemToTeam(Player player, Component message) {
        Team team = player.getTeam();
        if (team != null) {
            for (String string : team.getPlayers()) {
                ServerPlayer playerByName = this.getPlayerByName(string);
                if (playerByName != null && playerByName != player) {
                    playerByName.sendSystemMessage(message);
                }
            }
        }
    }

    public void broadcastSystemToAllExceptTeam(Player player, Component message) {
        Team team = player.getTeam();
        if (team == null) {
            this.broadcastSystemMessage(message, false);
        } else {
            for (int i = 0; i < this.players.size(); i++) {
                ServerPlayer serverPlayer = this.players.get(i);
                if (serverPlayer.getTeam() != team) {
                    serverPlayer.sendSystemMessage(message);
                }
            }
        }
    }

    public String[] getPlayerNamesArray() {
        String[] strings = new String[this.players.size()];

        for (int i = 0; i < this.players.size(); i++) {
            strings[i] = this.players.get(i).getGameProfile().getName();
        }

        return strings;
    }

    public UserBanList getBans() {
        return this.bans;
    }

    public IpBanList getIpBans() {
        return this.ipBans;
    }

    public void op(GameProfile profile) {
        this.ops.add(new ServerOpListEntry(profile, this.server.getOperatorUserPermissionLevel(), this.ops.canBypassPlayerLimit(profile)));
        ServerPlayer player = this.getPlayer(profile.getId());
        if (player != null) {
            this.sendPlayerPermissionLevel(player);
        }
    }

    public void deop(GameProfile profile) {
        this.ops.remove(profile);
        ServerPlayer player = this.getPlayer(profile.getId());
        if (player != null) {
            this.sendPlayerPermissionLevel(player);
        }
    }

    private void sendPlayerPermissionLevel(ServerPlayer player, int permLevel) {
        // Paper start - Add sendOpLevel API
        this.sendPlayerPermissionLevel(player, permLevel, true);
    }
    public void sendPlayerPermissionLevel(ServerPlayer player, int permLevel, boolean recalculatePermissions) {
        // Paper end - Add sendOpLevel API
        if (player.connection != null) {
            byte b;
            if (permLevel <= 0) {
                b = 24;
            } else if (permLevel >= 4) {
                b = 28;
            } else {
                b = (byte)(24 + permLevel);
            }
            if (b < 28 && player.getBukkitEntity().hasPermission("purpur.debug.f3n")) b = 28; // Purpur - Add permission for F3+N debug

            player.connection.send(new ClientboundEntityEventPacket(player, b));
        }

        if (recalculatePermissions) { // Paper - Add sendOpLevel API
        player.getBukkitEntity().recalculatePermissions(); // CraftBukkit
        this.server.getCommands().sendCommands(player);
        } // Paper - Add sendOpLevel API
    }

    public boolean isWhiteListed(GameProfile profile) {
        // Paper start - ProfileWhitelistVerifyEvent
        return this.isWhiteListed(profile, null);
    }
    public boolean isWhiteListed(GameProfile gameprofile, @Nullable org.bukkit.event.player.PlayerLoginEvent loginEvent) {
        boolean isOp = this.ops.contains(gameprofile);
        boolean isWhitelisted = !this.doWhiteList || isOp || this.whitelist.contains(gameprofile);
        final com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent event;

        final net.kyori.adventure.text.Component configuredMessage = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(org.spigotmc.SpigotConfig.whitelistMessage);
        event = new com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent(com.destroystokyo.paper.profile.CraftPlayerProfile.asBukkitMirror(gameprofile), this.doWhiteList, isWhitelisted, isOp, configuredMessage);
        event.callEvent();
        if (!event.isWhitelisted()) {
            if (loginEvent != null) {
                loginEvent.disallow(org.bukkit.event.player.PlayerLoginEvent.Result.KICK_WHITELIST, event.kickMessage() == null ? configuredMessage : event.kickMessage());
            }
            return false;
        }
        return true;
        // Paper end - ProfileWhitelistVerifyEvent
    }

    public boolean isOp(GameProfile profile) {
        return this.ops.contains(profile)
            || this.server.isSingleplayerOwner(profile) && this.server.getWorldData().isAllowCommands()
            || this.allowCommandsForAllPlayers;
    }

    @Nullable
    public ServerPlayer getPlayerByName(String username) {
        return this.playersByName.get(username.toLowerCase(java.util.Locale.ROOT)); // Spigot
    }

    public void broadcast(@Nullable Player except, double x, double y, double z, double radius, ResourceKey<Level> dimension, Packet<?> packet) {
        for (int i = 0; i < this.players.size(); i++) {
            ServerPlayer serverPlayer = this.players.get(i);
            // CraftBukkit start - Test if player receiving packet can see the source of the packet
            if (except != null && !serverPlayer.getBukkitEntity().canSee(except.getBukkitEntity())) {
               continue;
            }
            // CraftBukkit end
            if (serverPlayer != except && serverPlayer.level().dimension() == dimension) {
                double d = x - serverPlayer.getX();
                double d1 = y - serverPlayer.getY();
                double d2 = z - serverPlayer.getZ();
                if (d * d + d1 * d1 + d2 * d2 < radius * radius) {
                    serverPlayer.connection.send(packet);
                }
            }
        }
    }

    public void saveAll() {
        // Paper start - Incremental chunk and player saving
        this.saveAll(-1);
    }

    public void saveAll(final int interval) {
        io.papermc.paper.util.MCUtil.ensureMain("Save Players" , () -> { // Paper - Ensure main
        int numSaved = 0;
        final long now = MinecraftServer.currentTick;
        for (int i = 0; i < this.players.size(); i++) {
            final ServerPlayer player = this.players.get(i);
            if (interval == -1 || now - player.lastSave >= interval) {
                this.save(player);
                if (interval != -1 && ++numSaved >= io.papermc.paper.configuration.GlobalConfiguration.get().playerAutoSave.maxPerTick()) {
                    break;
                }
            }
            // Paper end - Incremental chunk and player saving
        }
        return null; }); // Paper - ensure main
    }

    public UserWhiteList getWhiteList() {
        return this.whitelist;
    }

    public String[] getWhiteListNames() {
        return this.whitelist.getUserList();
    }

    public ServerOpList getOps() {
        return this.ops;
    }

    public String[] getOpNames() {
        return this.ops.getUserList();
    }

    public void reloadWhiteList() {
    }

    public void sendLevelInfo(ServerPlayer player, ServerLevel level) {
        WorldBorder worldBorder = player.level().getWorldBorder(); // CraftBukkit
        player.connection.send(new ClientboundInitializeBorderPacket(worldBorder));
        player.connection.send(new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)));
        player.connection.send(new ClientboundSetDefaultSpawnPositionPacket(level.getSharedSpawnPos(), level.getSharedSpawnAngle()));
        if (level.isRaining()) {
            // CraftBukkit start - handle player weather
            // player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0F));
            // player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level.getRainLevel(1.0F)));
            // player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.getThunderLevel(1.0F)));
            player.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL, false);
            player.updateWeather(-level.rainLevel, level.rainLevel, -level.thunderLevel, level.thunderLevel);
            // CraftBukkit end
        }

        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
        this.server.tickRateManager().updateJoiningPlayer(player);
    }

    public void sendAllPlayerInfo(ServerPlayer player) {
        player.inventoryMenu.sendAllDataToRemote();
        // entityplayer.resetSentInfo();
        player.getBukkitEntity().updateScaledHealth(); // CraftBukkit - Update scaled health on respawn and worldchange
        player.refreshEntityData(player); // CraftBukkit - SPIGOT-7218: sync metadata
        player.connection.send(new ClientboundSetHeldSlotPacket(player.getInventory().selected));
        // CraftBukkit start - from GameRules
        int i = player.serverLevel().getGameRules().getBoolean(GameRules.RULE_REDUCEDDEBUGINFO) ? 22 : 23;
        player.connection.send(new ClientboundEntityEventPacket(player, (byte) i));
        float immediateRespawn = player.serverLevel().getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN) ? 1.0F: 0.0F;
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.IMMEDIATE_RESPAWN, immediateRespawn));
        // CraftBukkit end
    }

    public int getPlayerCount() {
        return this.players.size();
    }

    public int getMaxPlayers() {
        return this.maxPlayers;
    }

    public boolean isUsingWhitelist() {
        return this.doWhiteList;
    }

    public void setUsingWhiteList(boolean whitelistEnabled) {
        new com.destroystokyo.paper.event.server.WhitelistToggleEvent(whitelistEnabled).callEvent(); // Paper - WhitelistToggleEvent
        this.doWhiteList = whitelistEnabled;
    }

    public List<ServerPlayer> getPlayersWithAddress(String address) {
        List<ServerPlayer> list = Lists.newArrayList();

        for (ServerPlayer serverPlayer : this.players) {
            if (serverPlayer.getIpAddress().equals(address)) {
                list.add(serverPlayer);
            }
        }

        return list;
    }

    public int getViewDistance() {
        return this.viewDistance;
    }

    public int getSimulationDistance() {
        return this.simulationDistance;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    @Nullable
    public CompoundTag getSingleplayerData() {
        return null;
    }

    public void setAllowCommandsForAllPlayers(boolean allowCommandsForAllPlayers) {
        this.allowCommandsForAllPlayers = allowCommandsForAllPlayers;
    }

    public void removeAll() {
        // Paper start - Extract method to allow for restarting flag
        this.removeAll(false);
    }

    public void removeAll(boolean isRestarting) {
        // Paper end
        // CraftBukkit start - disconnect safely
        for (ServerPlayer player : this.players) {
            if (isRestarting) player.connection.disconnect(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(org.spigotmc.SpigotConfig.restartMessage), org.bukkit.event.player.PlayerKickEvent.Cause.UNKNOWN); else // Paper - kick event cause (cause is never used here)
            player.connection.disconnect(java.util.Objects.requireNonNullElseGet(this.server.server.shutdownMessage(), net.kyori.adventure.text.Component::empty)); // CraftBukkit - add custom shutdown message // Paper - Adventure
        }
        // CraftBukkit end

        // Paper start - Configurable player collision; Remove collideRule team if it exists
        if (this.collideRuleTeamName != null) {
            final net.minecraft.world.scores.Scoreboard scoreboard = this.getServer().getLevel(Level.OVERWORLD).getScoreboard();
            final PlayerTeam team = scoreboard.getPlayersTeam(this.collideRuleTeamName);
            if (team != null) scoreboard.removePlayerTeam(team);
        }
        // Paper end - Configurable player collision
    }

    // CraftBukkit start
    public void broadcastMessage(Component[] iChatBaseComponents) {
        for (Component component : iChatBaseComponents) {
            this.broadcastSystemMessage(component, false);
        }
    }
    // CraftBukkit end

    public void broadcastSystemMessage(Component message, boolean bypassHiddenChat) {
        this.broadcastSystemMessage(message, serverPlayer -> message, bypassHiddenChat);
    }

    public void broadcastSystemMessage(Component serverMessage, Function<ServerPlayer, Component> playerMessageFactory, boolean bypassHiddenChat) {
        this.server.sendSystemMessage(serverMessage);

        for (ServerPlayer serverPlayer : this.players) {
            Component component = playerMessageFactory.apply(serverPlayer);
            if (component != null) {
                serverPlayer.sendSystemMessage(component, bypassHiddenChat);
            }
        }
    }

    public void broadcastChatMessage(PlayerChatMessage message, CommandSourceStack sender, ChatType.Bound boundChatType) {
        this.broadcastChatMessage(message, sender::shouldFilterMessageTo, sender.getPlayer(), boundChatType);
    }

    public void broadcastChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound boundChatType) {
        // Paper start
        this.broadcastChatMessage(message, sender, boundChatType, null);
    }
    public void broadcastChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound boundChatType, @Nullable Function<net.kyori.adventure.audience.Audience, Component> unsignedFunction) {
        // Paper end
        this.broadcastChatMessage(message, sender::shouldFilterMessageTo, sender, boundChatType, unsignedFunction); // Paper
    }

    private void broadcastChatMessage(
        PlayerChatMessage message, Predicate<ServerPlayer> shouldFilterMessageTo, @Nullable ServerPlayer sender, ChatType.Bound boundChatType
    ) {
        // Paper start
        this.broadcastChatMessage(message, shouldFilterMessageTo, sender, boundChatType, null);
    }
    public void broadcastChatMessage(PlayerChatMessage message, Predicate<ServerPlayer> shouldFilterMessageTo, @Nullable ServerPlayer sender, ChatType.Bound boundChatType, @Nullable Function<net.kyori.adventure.audience.Audience, Component> unsignedFunction) {
        // Paper end
        boolean flag = this.verifyChatTrusted(message);
        this.server.logChatMessage((unsignedFunction == null ? message.decoratedContent() : unsignedFunction.apply(this.server.console)), boundChatType, flag ? null : "Not Secure"); // Paper
        OutgoingChatMessage outgoingChatMessage = OutgoingChatMessage.create(message);
        boolean flag1 = false;

        Packet<?> disguised = sender != null && unsignedFunction == null ? new net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket(outgoingChatMessage.content(), boundChatType) : null; // Paper - don't send player chat packets from vanished players
        for (ServerPlayer serverPlayer : this.players) {
            boolean flag2 = shouldFilterMessageTo.test(serverPlayer);
            // Paper start - don't send player chat packets from vanished players
            if (sender != null && !serverPlayer.getBukkitEntity().canSee(sender.getBukkitEntity())) {
                serverPlayer.connection.send(unsignedFunction != null
                    ? new net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket(unsignedFunction.apply(serverPlayer.getBukkitEntity()), boundChatType)
                    : disguised);
                continue;
            }
            serverPlayer.sendChatMessage(outgoingChatMessage, flag2, boundChatType, unsignedFunction == null ? null : unsignedFunction.apply(serverPlayer.getBukkitEntity()));
            // Paper end
            flag1 |= flag2 && message.isFullyFiltered();
        }

        if (flag1 && sender != null) {
            sender.sendSystemMessage(CHAT_FILTERED_FULL);
        }
    }

    public boolean verifyChatTrusted(PlayerChatMessage message) { // Paper - private -> public
        return message.hasSignature() && !message.hasExpiredServer(Instant.now());
    }

    // CraftBukkit start
    public ServerStatsCounter getPlayerStats(ServerPlayer player) {
        ServerStatsCounter serverstatisticmanager = player.getStats();
        return serverstatisticmanager == null ? this.getPlayerStats(player.getUUID(), player.getGameProfile().getName()) : serverstatisticmanager; // Paper - use username and not display name
    }

    public ServerStatsCounter getPlayerStats(UUID uuid, String displayName) {
        ServerPlayer player = this.getPlayer(uuid);
        ServerStatsCounter serverStatsCounter = player == null ? null : player.getStats();
        // CraftBukkit end
        if (serverStatsCounter == null) {
            File file = this.server.getWorldPath(LevelResource.PLAYER_STATS_DIR).toFile();
            File file1 = new File(file, uuid + ".json");
            if (!file1.exists()) {
                File file2 = new File(file, displayName + ".json"); // CraftBukkit
                Path path = file2.toPath();
                if (FileUtil.isPathNormalized(path) && FileUtil.isPathPortable(path) && path.startsWith(file.getPath()) && file2.isFile()) {
                    file2.renameTo(file1);
                }
            }

            serverStatsCounter = new ServerStatsCounter(this.server, file1);
            // this.stats.put(uuid, serverStatsCounter); // CraftBukkit
        }

        return serverStatsCounter;
    }

    public PlayerAdvancements getPlayerAdvancements(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerAdvancements playerAdvancements = player.getAdvancements(); // CraftBukkit
        if (playerAdvancements == null) {
            Path path = this.server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(uuid + ".json");
            playerAdvancements = new PlayerAdvancements(this.server.getFixerUpper(), this, this.server.getAdvancements(), path, player);
            // this.advancements.put(uuid, playerAdvancements); // CraftBukkit
        }

        playerAdvancements.setPlayer(player);
        return playerAdvancements;
    }

    public void setViewDistance(int viewDistance) {
        this.viewDistance = viewDistance;
        //this.broadcastAll(new ClientboundSetChunkCacheRadiusPacket(viewDistance)); // Paper - rewrite chunk system

        for (ServerLevel serverLevel : this.server.getAllLevels()) {
            if (serverLevel != null) {
                serverLevel.getChunkSource().setViewDistance(viewDistance);
            }
        }
    }

    public void setSimulationDistance(int simulationDistance) {
        this.simulationDistance = simulationDistance;
        //this.broadcastAll(new ClientboundSetSimulationDistancePacket(simulationDistance));  // Paper - rewrite chunk system

        for (ServerLevel serverLevel : this.server.getAllLevels()) {
            if (serverLevel != null) {
                serverLevel.getChunkSource().setSimulationDistance(simulationDistance);
            }
        }
    }

    public List<ServerPlayer> getPlayers() {
        return this.players;
    }

    @Nullable
    public ServerPlayer getPlayer(UUID playerUUID) {
        return this.playersByUUID.get(playerUUID);
    }

    public boolean canBypassPlayerLimit(GameProfile profile) {
        return false;
    }

    public void reloadResources() {
        // Paper start - API for updating recipes on clients
        this.reloadAdvancementData();
        this.reloadTagData();
        this.reloadRecipes();
    }
    public void reloadAdvancementData() {
        // Paper end - API for updating recipes on clients
        // CraftBukkit start
        // for (PlayerAdvancements playerAdvancements : this.advancements.values()) {
        //     playerAdvancements.reload(this.server.getAdvancements());
        // }
        for (ServerPlayer player : this.players) {
            player.getAdvancements().reload(this.server.getAdvancements());
            player.getAdvancements().flushDirty(player); // CraftBukkit - trigger immediate flush of advancements
        }
        // CraftBukkit end

        // Paper start - API for updating recipes on clients
    }
    public void reloadTagData() {
        this.broadcastAll(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(this.registries)));
        // CraftBukkit start
        // this.reloadRecipes(); // Paper - do not reload recipes just because tag data was reloaded
        // Paper end - API for updating recipes on clients
    }

    public void reloadRecipes() {
        // CraftBukkit end
        RecipeManager recipeManager = this.server.getRecipeManager();
        ClientboundUpdateRecipesPacket clientboundUpdateRecipesPacket = new ClientboundUpdateRecipesPacket(
            recipeManager.getSynchronizedItemProperties(), recipeManager.getSynchronizedStonecutterRecipes()
        );

        for (ServerPlayer serverPlayer : this.players) {
            serverPlayer.connection.send(clientboundUpdateRecipesPacket);
            serverPlayer.getRecipeBook().sendInitialRecipeBook(serverPlayer);
        }
    }

    public boolean isAllowCommandsForAllPlayers() {
        return this.allowCommandsForAllPlayers;
    }
}
