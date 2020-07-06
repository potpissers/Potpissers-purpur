package net.minecraft.server.level;

import com.google.common.net.InetAddresses;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundHorseScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.vehicle.AbstractBoat;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.ServerItemCooldowns;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.slf4j.Logger;

public class ServerPlayer extends Player implements ca.spottedleaf.moonrise.patches.chunk_system.player.ChunkSystemServerPlayer { // Paper - rewrite chunk system
    private static final Logger LOGGER = LogUtils.getLogger();
    public long lastSave = MinecraftServer.currentTick; // Paper - Incremental chunk and player saving
    private static final int NEUTRAL_MOB_DEATH_NOTIFICATION_RADII_XZ = 32;
    private static final int NEUTRAL_MOB_DEATH_NOTIFICATION_RADII_Y = 10;
    private static final int FLY_STAT_RECORDING_SPEED = 25;
    public static final double BLOCK_INTERACTION_DISTANCE_VERIFICATION_BUFFER = 1.0;
    public static final double ENTITY_INTERACTION_DISTANCE_VERIFICATION_BUFFER = 3.0;
    public static final int ENDER_PEARL_TICKET_RADIUS = 2;
    public static final String ENDER_PEARLS_TAG = "ender_pearls";
    public static final String ENDER_PEARL_DIMENSION_TAG = "ender_pearl_dimension";
    private static final AttributeModifier CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER = new AttributeModifier(
        ResourceLocation.withDefaultNamespace("creative_mode_block_range"), 0.5, AttributeModifier.Operation.ADD_VALUE
    );
    private static final AttributeModifier CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER = new AttributeModifier(
        ResourceLocation.withDefaultNamespace("creative_mode_entity_range"), 2.0, AttributeModifier.Operation.ADD_VALUE
    );
    public ServerGamePacketListenerImpl connection;
    public final MinecraftServer server;
    public final ServerPlayerGameMode gameMode;
    private final PlayerAdvancements advancements;
    private final ServerStatsCounter stats;
    private float lastRecordedHealthAndAbsorption = Float.MIN_VALUE;
    private int lastRecordedFoodLevel = Integer.MIN_VALUE;
    private int lastRecordedAirLevel = Integer.MIN_VALUE;
    private int lastRecordedArmor = Integer.MIN_VALUE;
    private int lastRecordedLevel = Integer.MIN_VALUE;
    private int lastRecordedExperience = Integer.MIN_VALUE;
    private float lastSentHealth = -1.0E8F;
    private int lastSentFood = -99999999;
    private boolean lastFoodSaturationZero = true;
    public int lastSentExp = -99999999;
    private ChatVisiblity chatVisibility = ChatVisiblity.FULL;
    public ParticleStatus particleStatus = ParticleStatus.ALL;
    private boolean canChatColor = true;
    private long lastActionTime = Util.getMillis();
    @Nullable
    private Entity camera;
    public boolean isChangingDimension;
    public boolean seenCredits;
    private final ServerRecipeBook recipeBook;
    @Nullable
    private Vec3 levitationStartPos;
    private int levitationStartTime;
    private boolean disconnected;
    private int requestedViewDistance = 2;
    public String language = null; // Paper - default to null
    public java.util.Locale adventure$locale = java.util.Locale.US; // Paper
    @Nullable
    private Vec3 startingToFallPosition;
    @Nullable
    private Vec3 enteredNetherPosition;
    @Nullable
    private Vec3 enteredLavaOnVehiclePosition;
    private SectionPos lastSectionPos = SectionPos.of(0, 0, 0);
    private ChunkTrackingView chunkTrackingView = ChunkTrackingView.EMPTY;
    private ResourceKey<Level> respawnDimension = Level.OVERWORLD;
    @Nullable
    private BlockPos respawnPosition;
    private boolean respawnForced;
    private float respawnAngle;
    private final TextFilter textFilter;
    private boolean textFilteringEnabled;
    private boolean allowsListing;
    private boolean spawnExtraParticlesOnFall;
    public WardenSpawnTracker wardenSpawnTracker = new WardenSpawnTracker(0, 0, 0);
    @Nullable
    private BlockPos raidOmenPosition;
    private Vec3 lastKnownClientMovement = Vec3.ZERO;
    private Input lastClientInput = Input.EMPTY;
    private final Set<ThrownEnderpearl> enderPearls = new HashSet<>();
    public final ContainerSynchronizer containerSynchronizer = new ContainerSynchronizer() {
        @Override
        public void sendInitialData(AbstractContainerMenu container, NonNullList<ItemStack> items, ItemStack carriedItem, int[] initialData) {
            ServerPlayer.this.connection
                .send(new ClientboundContainerSetContentPacket(container.containerId, container.incrementStateId(), items, carriedItem));

            for (int i = 0; i < initialData.length; i++) {
                this.broadcastDataValue(container, i, initialData[i]);
            }
        }

        // Paper start - Sync offhand slot in menus
        @Override
        public void sendOffHandSlotChange() {
            ServerPlayer.this.connection.send(new ClientboundContainerSetSlotPacket(ServerPlayer.this.inventoryMenu.containerId, ServerPlayer.this.inventoryMenu.incrementStateId(), net.minecraft.world.inventory.InventoryMenu.SHIELD_SLOT, ServerPlayer.this.inventoryMenu.getSlot(net.minecraft.world.inventory.InventoryMenu.SHIELD_SLOT).getItem().copy()));
        }
        // Paper end - Sync offhand slot in menus

        @Override
        public void sendSlotChange(AbstractContainerMenu container, int slot, ItemStack itemStack) {
            ServerPlayer.this.connection.send(new ClientboundContainerSetSlotPacket(container.containerId, container.incrementStateId(), slot, itemStack));
        }

        @Override
        public void sendCarriedChange(AbstractContainerMenu containerMenu, ItemStack stack) {
            ServerPlayer.this.connection.send(new ClientboundSetCursorItemPacket(stack.copy()));
        }

        @Override
        public void sendDataChange(AbstractContainerMenu container, int id, int value) {
            this.broadcastDataValue(container, id, value);
        }

        private void broadcastDataValue(AbstractContainerMenu container, int id, int value) {
            ServerPlayer.this.connection.send(new ClientboundContainerSetDataPacket(container.containerId, id, value));
        }
    };
    private final ContainerListener containerListener = new ContainerListener() {
        @Override
        public void slotChanged(AbstractContainerMenu containerToSend, int dataSlotIndex, ItemStack stack) {
            Slot slot = containerToSend.getSlot(dataSlotIndex);
            if (!(slot instanceof ResultSlot)) {
                if (slot.container == ServerPlayer.this.getInventory()) {
                    CriteriaTriggers.INVENTORY_CHANGED.trigger(ServerPlayer.this, ServerPlayer.this.getInventory(), stack);
                }
            }
        }

        // Paper start - Add PlayerInventorySlotChangeEvent
        @Override
        public void slotChanged(AbstractContainerMenu containerToSend, int dataSlotIndex, ItemStack oldStack, ItemStack stack) {
            // See slotChanged above
            Slot slot = containerToSend.getSlot(dataSlotIndex);
            if (!(slot instanceof ResultSlot)) {
                if (slot.container == ServerPlayer.this.getInventory()) {
                    if (io.papermc.paper.event.player.PlayerInventorySlotChangeEvent.getHandlerList().getRegisteredListeners().length == 0) {
                        CriteriaTriggers.INVENTORY_CHANGED.trigger(ServerPlayer.this, ServerPlayer.this.getInventory(), stack);
                        return;
                    }
                    io.papermc.paper.event.player.PlayerInventorySlotChangeEvent event = new io.papermc.paper.event.player.PlayerInventorySlotChangeEvent(
                        ServerPlayer.this.getBukkitEntity(),
                        dataSlotIndex,
                        org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(oldStack),
                        org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(stack)
                    );
                    event.callEvent();
                    if (event.shouldTriggerAdvancements()) {
                        CriteriaTriggers.INVENTORY_CHANGED.trigger(ServerPlayer.this, ServerPlayer.this.getInventory(), stack);
                    }
                }
            }
        }
        // Paper end - Add PlayerInventorySlotChangeEvent

        @Override
        public void dataChanged(AbstractContainerMenu containerMenu, int dataSlotIndex, int value) {
        }
    };
    @Nullable
    private RemoteChatSession chatSession;
    @Nullable
    public final Object object;
    private final CommandSource commandSource = new CommandSource() {
        @Override
        public boolean acceptsSuccess() {
            return ServerPlayer.this.serverLevel().getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK);
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return true;
        }

        @Override
        public void sendSystemMessage(Component component) {
            ServerPlayer.this.sendSystemMessage(component);
        }

        // CraftBukkit start
        @Override
        public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
            return ServerPlayer.this.getBukkitEntity();
        }
        // CraftBukkit end
    };
    private int containerCounter;
    public boolean wonGame;
    private int containerUpdateDelay; // Paper - Configurable container update tick rate
    public long loginTime; // Paper - Replace OfflinePlayer#getLastPlayed
    public int patrolSpawnDelay; // Paper - Pillager patrol spawn settings and per player options
    // Paper start - cancellable death event
    public boolean queueHealthUpdatePacket;
    public net.minecraft.network.protocol.game.ClientboundSetHealthPacket queuedHealthUpdatePacket;
    // Paper end - cancellable death event
    // Paper start - Optional per player mob spawns
    public static final int MOBCATEGORY_TOTAL_ENUMS = net.minecraft.world.entity.MobCategory.values().length;
    public final int[] mobCounts = new int[MOBCATEGORY_TOTAL_ENUMS];
    // Paper end - Optional per player mob spawns
    public final int[] mobBackoffCounts = new int[MOBCATEGORY_TOTAL_ENUMS]; // Paper - per player mob count backoff
    // CraftBukkit start
    public org.bukkit.craftbukkit.entity.CraftPlayer.TransferCookieConnection transferCookieConnection;
    public String displayName;
    public net.kyori.adventure.text.Component adventure$displayName; // Paper
    public Component listName;
    public int listOrder = 0;
    public org.bukkit.Location compassTarget;
    public int newExp = 0;
    public int newLevel = 0;
    public int newTotalExp = 0;
    public boolean keepLevel = false;
    public double maxHealthCache;
    public boolean joining = true;
    public boolean sentListPacket = false;
    public boolean supressTrackerForLogin = false; // Paper - Fire PlayerJoinEvent when Player is actually ready
    // CraftBukkit end
    public boolean isRealPlayer; // Paper
    public com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent playerNaturallySpawnedEvent; // Paper - PlayerNaturallySpawnCreaturesEvent
    public @Nullable String clientBrandName = null; // Paper - Brand support
    public org.bukkit.event.player.PlayerQuitEvent.QuitReason quitReason = null; // Paper - Add API for quit reason; there are a lot of changes to do if we change all methods leading to the event
    public boolean purpurClient = false; // Purpur - Purpur client support
    private boolean tpsBar = false; // Purpur - Implement TPSBar
    private boolean compassBar = false; // Purpur - Add compass command
    private boolean ramBar = false; // Purpur - Implement rambar commands

    // Paper start - rewrite chunk system
    private ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.PlayerChunkLoaderData chunkLoader;
    private final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.ViewDistanceHolder viewDistanceHolder = new ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.ViewDistanceHolder();

    @Override
    public final boolean moonrise$isRealPlayer() {
        return this.isRealPlayer;
    }

    @Override
    public final void moonrise$setRealPlayer(final boolean real) {
        this.isRealPlayer = real;
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.PlayerChunkLoaderData moonrise$getChunkLoader() {
        return this.chunkLoader;
    }

    @Override
    public final void moonrise$setChunkLoader(final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.PlayerChunkLoaderData loader) {
        this.chunkLoader = loader;
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.ViewDistanceHolder moonrise$getViewDistanceHolder() {
        return this.viewDistanceHolder;
    }
    // Paper end - rewrite chunk system

    public ServerPlayer(MinecraftServer server, ServerLevel level, GameProfile gameProfile, ClientInformation clientInformation) {
        super(level, level.getSharedSpawnPos(), level.getSharedSpawnAngle(), gameProfile);
        this.textFilter = server.createTextFilterForPlayer(this);
        this.gameMode = server.createGameModeForPlayer(this);
        this.recipeBook = new ServerRecipeBook((recipe, output) -> server.getRecipeManager().listDisplaysForRecipe(recipe, output));
        this.server = server;
        this.stats = server.getPlayerList().getPlayerStats(this);
        this.advancements = server.getPlayerList().getPlayerAdvancements(this);
        // this.moveTo(this.adjustSpawnLocation(level, level.getSharedSpawnPos()).getBottomCenter(), 0.0F, 0.0F); // Paper - Don't move existing players to world spawn
        this.updateOptionsNoEvents(clientInformation); // Paper - don't call options events on login
        this.object = null;

        // CraftBukkit start
        this.displayName = this.getScoreboardName();
        this.adventure$displayName = net.kyori.adventure.text.Component.text(this.getScoreboardName()); // Paper
        this.bukkitPickUpLoot = true;
        this.maxHealthCache = this.getMaxHealth();
    }

    @Override
    public BlockPos adjustSpawnLocation(ServerLevel level, BlockPos pos) {
        AABB aabb = this.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO);
        BlockPos blockPos = pos;
        if (level.dimensionType().hasSkyLight() && level.serverLevelData.getGameType() != GameType.ADVENTURE) { // CraftBukkit
            int max = Math.max(0, this.server.getSpawnRadius(level));
            int floor = Mth.floor(level.getWorldBorder().getDistanceToBorder(pos.getX(), pos.getZ()));
            if (floor < max) {
                max = floor;
            }

            if (floor <= 1) {
                max = 1;
            }

            long l = max * 2 + 1;
            long l1 = l * l;
            int i = l1 > 2147483647L ? Integer.MAX_VALUE : (int)l1;
            int coprime = this.getCoprime(i);
            int randomInt = RandomSource.create().nextInt(i);

            for (int i1 = 0; i1 < i; i1++) {
                int i2 = (randomInt + coprime * i1) % i;
                int i3 = i2 % (max * 2 + 1);
                int i4 = i2 / (max * 2 + 1);
                int i5 = pos.getX() + i3 - max;
                int i6 = pos.getZ() + i4 - max;

                try {
                    blockPos = PlayerRespawnLogic.getOverworldRespawnPos(level, i5, i6);
                    if (blockPos != null && this.noCollisionNoLiquid(level, aabb.move(blockPos.getBottomCenter()))) {
                        return blockPos;
                    }
                } catch (Exception var25) {
                    int i7 = i1;
                    int i8 = max;
                    CrashReport crashReport = CrashReport.forThrowable(var25, "Searching for spawn");
                    CrashReportCategory crashReportCategory = crashReport.addCategory("Spawn Lookup");
                    crashReportCategory.setDetail("Origin", pos::toString);
                    crashReportCategory.setDetail("Radius", () -> Integer.toString(i8));
                    crashReportCategory.setDetail("Candidate", () -> "[" + i5 + "," + i6 + "]");
                    crashReportCategory.setDetail("Progress", () -> i7 + " out of " + i);
                    throw new ReportedException(crashReport);
                }
            }

            blockPos = pos;
        }

        while (!this.noCollisionNoLiquid(level, aabb.move(blockPos.getBottomCenter())) && blockPos.getY() < level.getMaxY()) {
            blockPos = blockPos.above();
        }

        while (this.noCollisionNoLiquid(level, aabb.move(blockPos.below().getBottomCenter())) && blockPos.getY() > level.getMinY() + 1) {
            blockPos = blockPos.below();
        }

        return blockPos;
    }

    private boolean noCollisionNoLiquid(ServerLevel level, AABB collisionBox) {
        return level.noCollision(this, collisionBox, true);
    }

    private int getCoprime(int spawnArea) {
        return spawnArea <= 16 ? spawnArea - 1 : 17;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("warden_spawn_tracker", 10)) {
            WardenSpawnTracker.CODEC
                .parse(new Dynamic<>(NbtOps.INSTANCE, compound.get("warden_spawn_tracker")))
                .resultOrPartial(LOGGER::error)
                .ifPresent(wardenSpawnTracker -> this.wardenSpawnTracker = wardenSpawnTracker);
        }

        if (compound.contains("enteredNetherPosition", 10)) {
            CompoundTag compound1 = compound.getCompound("enteredNetherPosition");
            this.enteredNetherPosition = new Vec3(compound1.getDouble("x"), compound1.getDouble("y"), compound1.getDouble("z"));
        }

        this.seenCredits = compound.getBoolean("seenCredits");
        if (compound.contains("recipeBook", 10)) {
            this.recipeBook.fromNbt(compound.getCompound("recipeBook"), key -> this.server.getRecipeManager().byKey(key).isPresent());
        }
        this.getBukkitEntity().readExtraData(compound); // CraftBukkit

        if (this.isSleeping()) {
            this.stopSleeping();
        }

        // CraftBukkit start
        String spawnWorld = compound.getString("SpawnWorld");
        org.bukkit.craftbukkit.CraftWorld oldWorld = (org.bukkit.craftbukkit.CraftWorld) org.bukkit.Bukkit.getWorld(spawnWorld);
        if (oldWorld != null) {
            this.respawnDimension = oldWorld.getHandle().dimension();
        }
        // CraftBukkit end

        if (compound.contains("SpawnX", 99) && compound.contains("SpawnY", 99) && compound.contains("SpawnZ", 99)) {
            this.respawnPosition = new BlockPos(compound.getInt("SpawnX"), compound.getInt("SpawnY"), compound.getInt("SpawnZ"));
            this.respawnForced = compound.getBoolean("SpawnForced");
            this.respawnAngle = compound.getFloat("SpawnAngle");
            if (compound.contains("SpawnDimension")) {
                this.respawnDimension = Level.RESOURCE_KEY_CODEC
                    .parse(NbtOps.INSTANCE, compound.get("SpawnDimension"))
                    .resultOrPartial(LOGGER::error)
                    .orElse(Level.OVERWORLD);
            }
        }

        this.spawnExtraParticlesOnFall = compound.getBoolean("spawn_extra_particles_on_fall");
        Tag tag = compound.get("raid_omen_position");
        if (tag != null) {
            BlockPos.CODEC.parse(NbtOps.INSTANCE, tag).resultOrPartial(LOGGER::error).ifPresent(pos -> this.raidOmenPosition = pos);
        }

        if (compound.contains("Purpur.TPSBar")) { this.tpsBar = compound.getBoolean("Purpur.TPSBar"); } // Purpur - Implement TPSBar
        if (compound.contains("Purpur.CompassBar")) { this.compassBar = compound.getBoolean("Purpur.CompassBar"); } // Purpur - Add compass command
        if (compound.contains("Purpur.RamBar")) { this.ramBar = compound.getBoolean("Purpur.RamBar"); } // Purpur - Implement rambar command
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        WardenSpawnTracker.CODEC
            .encodeStart(NbtOps.INSTANCE, this.wardenSpawnTracker)
            .resultOrPartial(LOGGER::error)
            .ifPresent(wardenSpawnTracker -> compound.put("warden_spawn_tracker", wardenSpawnTracker));
        this.storeGameTypes(compound);
        compound.putBoolean("seenCredits", this.seenCredits);
        if (this.enteredNetherPosition != null) {
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.putDouble("x", this.enteredNetherPosition.x);
            compoundTag.putDouble("y", this.enteredNetherPosition.y);
            compoundTag.putDouble("z", this.enteredNetherPosition.z);
            compound.put("enteredNetherPosition", compoundTag);
        }

        this.saveParentVehicle(compound);
        compound.put("recipeBook", this.recipeBook.toNbt());
        compound.putString("Dimension", this.level().dimension().location().toString());
        if (this.respawnPosition != null) {
            compound.putInt("SpawnX", this.respawnPosition.getX());
            compound.putInt("SpawnY", this.respawnPosition.getY());
            compound.putInt("SpawnZ", this.respawnPosition.getZ());
            compound.putBoolean("SpawnForced", this.respawnForced);
            compound.putFloat("SpawnAngle", this.respawnAngle);
            ResourceLocation.CODEC
                .encodeStart(NbtOps.INSTANCE, this.respawnDimension.location())
                .resultOrPartial(LOGGER::error)
                .ifPresent(spawnDimension -> compound.put("SpawnDimension", spawnDimension));
        }
        this.getBukkitEntity().setExtraData(compound); // CraftBukkit

        compound.putBoolean("spawn_extra_particles_on_fall", this.spawnExtraParticlesOnFall);
        if (this.raidOmenPosition != null) {
            BlockPos.CODEC
                .encodeStart(NbtOps.INSTANCE, this.raidOmenPosition)
                .resultOrPartial(LOGGER::error)
                .ifPresent(raidOmenPosition -> compound.put("raid_omen_position", raidOmenPosition));
        }

        this.saveEnderPearls(compound);
        compound.putBoolean("Purpur.TPSBar", this.tpsBar); // Purpur - Implement TPSBar
        compound.putBoolean("Purpur.CompassBar", this.compassBar); // Purpur - Add compass command
        compound.putBoolean("Purpur.RamBar", this.ramBar); // Purpur - Add rambar command
    }

    private void saveParentVehicle(CompoundTag tag) {
        Entity rootVehicle = this.getRootVehicle();
        Entity vehicle = this.getVehicle();
        // CraftBukkit start - handle non-persistent vehicles
        boolean persistVehicle = true;
        if (vehicle != null) {
            for (Entity topVehicle = vehicle; topVehicle != null; topVehicle = topVehicle.getVehicle()) {
                if (!topVehicle.persist) {
                    persistVehicle = false;
                    break;
                }
            }
        }
        if (persistVehicle && vehicle != null && rootVehicle != this && rootVehicle.hasExactlyOnePlayerPassenger() && !rootVehicle.isRemoved()) { // Paper - Ensure valid vehicle status
            // CraftBukkit end
            CompoundTag compoundTag = new CompoundTag();
            CompoundTag compoundTag1 = new CompoundTag();
            rootVehicle.save(compoundTag1);
            compoundTag.putUUID("Attach", vehicle.getUUID());
            compoundTag.put("Entity", compoundTag1);
            tag.put("RootVehicle", compoundTag);
        }
    }

    public void loadAndSpawnParentVehicle(Optional<CompoundTag> tag) {
        if (tag.isPresent() && tag.get().contains("RootVehicle", 10) && this.level() instanceof ServerLevel serverLevel) {
            CompoundTag compound = tag.get().getCompound("RootVehicle");
            Entity entity = EntityType.loadEntityRecursive(
                compound.getCompound("Entity"), serverLevel, EntitySpawnReason.LOAD, entity2 -> !serverLevel.addWithUUID(entity2, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.MOUNT) ? null : entity2 // Paper - Entity#getEntitySpawnReason
            );
            if (entity == null) {
                return;
            }

            UUID uuid;
            if (compound.hasUUID("Attach")) {
                uuid = compound.getUUID("Attach");
            } else {
                uuid = null;
            }

            if (entity.getUUID().equals(uuid)) {
                this.startRiding(entity, true);
            } else {
                for (Entity entity1 : entity.getIndirectPassengers()) {
                    if (entity1.getUUID().equals(uuid)) {
                        this.startRiding(entity1, true);
                        break;
                    }
                }
            }

            if (!this.isPassenger()) {
                LOGGER.warn("Couldn't reattach entity to player");
                entity.discard(null); // CraftBukkit - add Bukkit remove cause

                for (Entity entity1x : entity.getIndirectPassengers()) {
                    entity1x.discard(null); // CraftBukkit - add Bukkit remove cause
                }
            }
        }
    }

    private void saveEnderPearls(CompoundTag tag) {
        if (!this.enderPearls.isEmpty()) {
            ListTag listTag = new ListTag();

            for (ThrownEnderpearl thrownEnderpearl : this.enderPearls) {
                if (thrownEnderpearl.level().paperConfig().misc.legacyEnderPearlBehavior) continue; // Paper - Allow using old ender pearl behavior
                if (thrownEnderpearl.isRemoved()) {
                    LOGGER.warn("Trying to save removed ender pearl, skipping");
                } else {
                    CompoundTag compoundTag = new CompoundTag();
                    thrownEnderpearl.save(compoundTag);
                    ResourceLocation.CODEC
                        .encodeStart(NbtOps.INSTANCE, thrownEnderpearl.level().dimension().location())
                        .resultOrPartial(LOGGER::error)
                        .ifPresent(enderPearlDimension -> compoundTag.put("ender_pearl_dimension", enderPearlDimension));
                    listTag.add(compoundTag);
                }
            }

            tag.put("ender_pearls", listTag);
        }
    }

    public void loadAndSpawnEnderpearls(Optional<CompoundTag> tag) {
        if (tag.isPresent() && tag.get().contains("ender_pearls", 9) && tag.get().get("ender_pearls") instanceof ListTag listTag) {
            listTag.forEach(
                enderPearlTag -> {
                    if (enderPearlTag instanceof CompoundTag compoundTag && compoundTag.contains("ender_pearl_dimension")) {
                        Optional<ResourceKey<Level>> optional = Level.RESOURCE_KEY_CODEC
                            .parse(NbtOps.INSTANCE, compoundTag.get("ender_pearl_dimension"))
                            .resultOrPartial(LOGGER::error);
                        if (optional.isEmpty()) {
                            LOGGER.warn("No dimension defined for ender pearl, skipping");
                            return;
                        }

                        ServerLevel level = this.level().getServer().getLevel(optional.get());
                        if (level != null) {
                            Entity entity = EntityType.loadEntityRecursive(
                                compoundTag, level, EntitySpawnReason.LOAD, entity1 -> !level.addWithUUID(entity1) ? null : entity1
                            );
                            if (entity != null) {
                                placeEnderPearlTicket(level, entity.chunkPosition());
                            } else {
                                LOGGER.warn("Failed to spawn player ender pearl in level ({}), skipping", optional.get());
                            }
                        } else {
                            LOGGER.warn("Trying to load ender pearl without level ({}) being loaded, skipping", optional.get());
                        }
                    }
                }
            );
        }
    }

    // CraftBukkit start
    public void spawnIn(final ServerLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("level can't be null");
        }
        this.setLevel(level);
        this.gameMode.setLevel(level);
    }
    // CraftBukkit end

    public void setExperiencePoints(int experiencePoints) {
        float f = this.getXpNeededForNextLevel();
        float f1 = (f - 1.0F) / f;
        this.experienceProgress = Mth.clamp(experiencePoints / f, 0.0F, f1);
        this.lastSentExp = -1;
    }

    public void setExperienceLevels(int level) {
        this.experienceLevel = level;
        this.lastSentExp = -1;
    }

    @Override
    public void giveExperienceLevels(int levels) {
        super.giveExperienceLevels(levels);
        this.lastSentExp = -1;
    }

    @Override
    public void onEnchantmentPerformed(ItemStack enchantedItem, int cost) {
        super.onEnchantmentPerformed(enchantedItem, cost);
        this.lastSentExp = -1;
    }

    public void initMenu(AbstractContainerMenu menu) {
        menu.addSlotListener(this.containerListener);
        menu.setSynchronizer(this.containerSynchronizer);
    }

    public void initInventoryMenu() {
        this.initMenu(this.inventoryMenu);
    }

    @Override
    public void onEnterCombat() {
        super.onEnterCombat();
        this.connection.send(ClientboundPlayerCombatEnterPacket.INSTANCE);
    }

    @Override
    public void onLeaveCombat() {
        super.onLeaveCombat();
        this.connection.send(new ClientboundPlayerCombatEndPacket(this.getCombatTracker()));
    }

    @Override
    public void onInsideBlock(BlockState state) {
        CriteriaTriggers.ENTER_BLOCK.trigger(this, state);
    }

    @Override
    protected ItemCooldowns createItemCooldowns() {
        return new ServerItemCooldowns(this);
    }

    @Override
    public void tick() {
        // CraftBukkit start
        if (this.joining) {
            this.joining = false;
        }
        // CraftBukkit end
        this.tickClientLoadTimeout();
        this.gameMode.tick();
        this.wardenSpawnTracker.tick();
        if (this.invulnerableTime > 0) {
            this.invulnerableTime--;
        }

        // Paper start - Configurable container update tick rate
        if (--this.containerUpdateDelay <= 0) {
            this.containerMenu.broadcastChanges();
            this.containerUpdateDelay = this.level().paperConfig().tickRates.containerUpdate;
        }
        // Paper end - Configurable container update tick rate
        if (this.containerMenu != this.inventoryMenu && (this.isImmobile() || !this.containerMenu.stillValid(this))) { // Paper - Prevent opening inventories when frozen
            this.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.CANT_USE); // Paper - Inventory close reason
            this.containerMenu = this.inventoryMenu;
        }

        Entity camera = this.getCamera();
        if (camera != this) {
            if (camera.isAlive()) {
                this.absMoveTo(camera.getX(), camera.getY(), camera.getZ(), camera.getYRot(), camera.getXRot());
                this.serverLevel().getChunkSource().move(this);
                if (this.wantsToStopRiding()) {
                    this.setCamera(this);
                }
            } else {
                this.setCamera(this);
            }
        }

        CriteriaTriggers.TICK.trigger(this);
        if (this.levitationStartPos != null) {
            CriteriaTriggers.LEVITATION.trigger(this, this.levitationStartPos, this.tickCount - this.levitationStartTime);
        }

        this.trackStartFallingPosition();
        this.trackEnteredOrExitedLavaOnVehicle();
        this.updatePlayerAttributes();
        this.advancements.flushDirty(this);

        // Purpur start - Ridables
        if (this.level().purpurConfig.useNightVisionWhenRiding && this.getVehicle() != null && this.getVehicle().getRider() == this && this.level().getGameTime() % 100 == 0) { // 5 seconds
            MobEffectInstance nightVision = this.getEffect(MobEffects.NIGHT_VISION);
            if (nightVision == null || nightVision.getDuration() <= 300) { // 15 seconds
                this.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0)); // 20 seconds
            }
        }
        // Purpur end - Ridables
    }

    private void updatePlayerAttributes() {
        AttributeInstance attribute = this.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (attribute != null) {
            if (this.isCreative()) {
                attribute.addOrUpdateTransientModifier(CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER);
            } else {
                attribute.removeModifier(CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER);
            }
        }

        AttributeInstance attribute1 = this.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (attribute1 != null) {
            if (this.isCreative()) {
                attribute1.addOrUpdateTransientModifier(CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER);
            } else {
                attribute1.removeModifier(CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER);
            }
        }
    }

    public void doTick() {
        try {
            if (valid && !this.isSpectator() || !this.touchingUnloadedChunk()) { // Paper - don't tick dead players that are not in the world currently (pending respawn)
                super.tick();
            }

            for (int i = 0; i < this.getInventory().getContainerSize(); i++) {
                ItemStack item = this.getInventory().getItem(i);
                if (!item.isEmpty()) {
                    this.synchronizeSpecialItemUpdates(item);
                }
            }

            if (this.getHealth() != this.lastSentHealth
                || this.lastSentFood != this.foodData.getFoodLevel()
                || this.foodData.getSaturationLevel() == 0.0F != this.lastFoodSaturationZero) {
                this.connection.send(new ClientboundSetHealthPacket(this.getBukkitEntity().getScaledHealth(), this.foodData.getFoodLevel(), this.foodData.getSaturationLevel())); // CraftBukkit
                this.lastSentHealth = this.getHealth();
                this.lastSentFood = this.foodData.getFoodLevel();
                this.lastFoodSaturationZero = this.foodData.getSaturationLevel() == 0.0F;
            }

            if (this.getHealth() + this.getAbsorptionAmount() != this.lastRecordedHealthAndAbsorption) {
                this.lastRecordedHealthAndAbsorption = this.getHealth() + this.getAbsorptionAmount();
                this.updateScoreForCriteria(ObjectiveCriteria.HEALTH, Mth.ceil(this.lastRecordedHealthAndAbsorption));
            }

            if (this.foodData.getFoodLevel() != this.lastRecordedFoodLevel) {
                this.lastRecordedFoodLevel = this.foodData.getFoodLevel();
                this.updateScoreForCriteria(ObjectiveCriteria.FOOD, Mth.ceil((float)this.lastRecordedFoodLevel));
            }

            if (this.getAirSupply() != this.lastRecordedAirLevel) {
                this.lastRecordedAirLevel = this.getAirSupply();
                this.updateScoreForCriteria(ObjectiveCriteria.AIR, Mth.ceil((float)this.lastRecordedAirLevel));
            }

            if (this.getArmorValue() != this.lastRecordedArmor) {
                this.lastRecordedArmor = this.getArmorValue();
                this.updateScoreForCriteria(ObjectiveCriteria.ARMOR, Mth.ceil((float)this.lastRecordedArmor));
            }

            if (this.totalExperience != this.lastRecordedExperience) {
                this.lastRecordedExperience = this.totalExperience;
                this.updateScoreForCriteria(ObjectiveCriteria.EXPERIENCE, Mth.ceil((float)this.lastRecordedExperience));
            }

            // CraftBukkit start - Force max health updates
            if (this.maxHealthCache != this.getMaxHealth()) {
                this.getBukkitEntity().updateScaledHealth();
            }
            // CraftBukkit end

            if (this.experienceLevel != this.lastRecordedLevel) {
                this.lastRecordedLevel = this.experienceLevel;
                this.updateScoreForCriteria(ObjectiveCriteria.LEVEL, Mth.ceil((float)this.lastRecordedLevel));
            }

            if (this.totalExperience != this.lastSentExp) {
                this.lastSentExp = this.totalExperience;
                this.connection.send(new ClientboundSetExperiencePacket(this.experienceProgress, this.totalExperience, this.experienceLevel));
            }

            if (this.tickCount % 20 == 0) {
                CriteriaTriggers.LOCATION.trigger(this);
            }

            // CraftBukkit start - initialize oldLevel, fire PlayerLevelChangeEvent, and tick client-sided world border
            if (this.oldLevel == -1) {
                this.oldLevel = this.experienceLevel;
            }

            if (this.oldLevel != this.experienceLevel) {
                org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerLevelChangeEvent(this.getBukkitEntity(), this.oldLevel, this.experienceLevel);
                this.oldLevel = this.experienceLevel;
            }

            if (this.getBukkitEntity().hasClientWorldBorder()) {
                ((org.bukkit.craftbukkit.CraftWorldBorder) this.getBukkitEntity().getWorldBorder()).getHandle().tick();
            }
            // CraftBukkit end
        } catch (Throwable var4) {
            CrashReport crashReport = CrashReport.forThrowable(var4, "Ticking player");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Player being ticked");
            this.fillCrashReportCategory(crashReportCategory);
            throw new ReportedException(crashReport);
        }
    }

    private void synchronizeSpecialItemUpdates(ItemStack stack) {
        MapId mapId = stack.get(DataComponents.MAP_ID);
        MapItemSavedData savedData = MapItem.getSavedData(mapId, this.level());
        if (savedData != null) {
            Packet<?> updatePacket = savedData.getUpdatePacket(mapId, this);
            if (updatePacket != null) {
                this.connection.send(updatePacket);
            }
        }
    }

    @Override
    protected void tickRegeneration() {
        if (this.level().getDifficulty() == Difficulty.PEACEFUL && this.serverLevel().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) {
            if (this.tickCount % 20 == 0) {
                if (this.getHealth() < this.getMaxHealth()) {
                    this.heal(1.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.REGEN); // CraftBukkit - added regain reason of "REGEN" for filtering purposes.
                }

                float saturationLevel = this.foodData.getSaturationLevel();
                if (saturationLevel < 20.0F) {
                    this.foodData.setSaturation(saturationLevel + 1.0F);
                }
            }

            if (this.tickCount % 10 == 0 && this.foodData.needsFood()) {
                this.foodData.setFoodLevel(this.foodData.getFoodLevel() + 1);
            }
        }
    }

    @Override
    public void resetFallDistance() {
        if (this.getHealth() > 0.0F && this.startingToFallPosition != null) {
            CriteriaTriggers.FALL_FROM_HEIGHT.trigger(this, this.startingToFallPosition);
        }

        this.startingToFallPosition = null;
        super.resetFallDistance();
    }

    public void trackStartFallingPosition() {
        if (this.fallDistance > 0.0F && this.startingToFallPosition == null) {
            this.startingToFallPosition = this.position();
            if (this.currentImpulseImpactPos != null && this.currentImpulseImpactPos.y <= this.startingToFallPosition.y) {
                CriteriaTriggers.FALL_AFTER_EXPLOSION.trigger(this, this.currentImpulseImpactPos, this.currentExplosionCause);
            }
        }
    }

    public void trackEnteredOrExitedLavaOnVehicle() {
        if (this.getVehicle() != null && this.getVehicle().isInLava()) {
            if (this.enteredLavaOnVehiclePosition == null) {
                this.enteredLavaOnVehiclePosition = this.position();
            } else {
                CriteriaTriggers.RIDE_ENTITY_IN_LAVA_TRIGGER.trigger(this, this.enteredLavaOnVehiclePosition);
            }
        }

        if (this.enteredLavaOnVehiclePosition != null && (this.getVehicle() == null || !this.getVehicle().isInLava())) {
            this.enteredLavaOnVehiclePosition = null;
        }
    }

    private void updateScoreForCriteria(ObjectiveCriteria criteria, int points) {
        this.level().getCraftServer().getScoreboardManager().forAllObjectives(criteria, this, scoreAccess -> scoreAccess.set(points)); // CraftBukkit - Use our scores instead
    }

    // Paper start - PlayerDeathEvent#getItemsToKeep
    private static void processKeep(org.bukkit.event.entity.PlayerDeathEvent event, NonNullList<ItemStack> inv) {
        List<org.bukkit.inventory.ItemStack> itemsToKeep = event.getItemsToKeep();
        if (inv == null) {
            // remainder of items left in toKeep - plugin added stuff on death that wasn't in the initial loot?
            if (!itemsToKeep.isEmpty()) {
                for (org.bukkit.inventory.ItemStack itemStack : itemsToKeep) {
                    event.getEntity().getInventory().addItem(itemStack);
                }
            }

            return;
        }

        for (int i = 0; i < inv.size(); ++i) {
            ItemStack item = inv.get(i);
            if (EnchantmentHelper.has(item, net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP) || itemsToKeep.isEmpty() || item.isEmpty()) {
                inv.set(i, ItemStack.EMPTY);
                continue;
            }

            final org.bukkit.inventory.ItemStack bukkitStack = item.getBukkitStack();
            boolean keep = false;
            final java.util.Iterator<org.bukkit.inventory.ItemStack> iterator = itemsToKeep.iterator();
            while (iterator.hasNext()) {
                final org.bukkit.inventory.ItemStack itemStack = iterator.next();
                if (bukkitStack.equals(itemStack)) {
                    iterator.remove();
                    keep = true;
                    break;
                }
            }

            if (!keep) {
                inv.set(i, ItemStack.EMPTY);
            }
        }
    }
    // Paper end - PlayerDeathEvent#getItemsToKeep

    @Override
    public void die(DamageSource cause) {
        // this.gameEvent(GameEvent.ENTITY_DIE); // Paper - move below event cancellation check
        boolean _boolean = this.serverLevel().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES); final boolean showDeathMessage = _boolean; // Paper - OBFHELPER
        // CraftBukkit start - fire PlayerDeathEvent
        if (this.isRemoved()) {
            return;
        }
        List<DefaultDrop> loot = new java.util.ArrayList<>(this.getInventory().getContainerSize()); // Paper - Restore vanilla drops behavior
        boolean keepInventory = this.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || this.isSpectator();
        if (!keepInventory) {
            for (ItemStack item : this.getInventory().getContents()) {
                if (!item.isEmpty() && !EnchantmentHelper.has(item, net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                    loot.add(new DefaultDrop(item, stack -> this.drop(stack, true, false, false))); // Paper - Restore vanilla drops behavior; drop function taken from Inventory#dropAll (don't fire drop event)
                }
            }
        }
        if (this.shouldDropLoot() && this.serverLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) { // Paper - fix player loottables running when mob loot gamerule is false
            // SPIGOT-5071: manually add player loot tables (SPIGOT-5195 - ignores keepInventory rule)
            this.dropFromLootTable(this.serverLevel(), cause, this.lastHurtByPlayerTime > 0);
            // Paper - Restore vanilla drops behaviour; custom death loot is a noop on server player, remove.
            loot.addAll(this.drops);
            this.drops.clear(); // SPIGOT-5188: make sure to clear
        } // Paper - fix player loottables running when mob loot gamerule is false

        Component defaultMessage = this.getCombatTracker().getDeathMessage();

        String deathmessage = defaultMessage.getString();
        this.keepLevel = keepInventory; // SPIGOT-2222: pre-set keepLevel
        org.bukkit.event.entity.PlayerDeathEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerDeathEvent(this, cause, loot, io.papermc.paper.adventure.PaperAdventure.asAdventure(defaultMessage), keepInventory); // Paper - Adventure
        // Paper start - cancellable death event
        if (event.isCancelled()) {
            // make compatible with plugins that might have already set the health in an event listener
            if (this.getHealth() <= 0) {
                this.setHealth((float) event.getReviveHealth());
            }
            return;
        }
        this.gameEvent(GameEvent.ENTITY_DIE); // moved from the top of this method
        // Paper end

        // SPIGOT-943 - only call if they have an inventory open
        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.DEATH); // Paper - Inventory close reason
        }

        net.kyori.adventure.text.Component apiDeathMessage = event.deathMessage() != null ? event.deathMessage() : net.kyori.adventure.text.Component.empty(); // Paper - Adventure

        if (apiDeathMessage != null && apiDeathMessage != net.kyori.adventure.text.Component.empty() && showDeathMessage) { // Paper - Adventure // TODO: allow plugins to override?
            Component deathMessage = io.papermc.paper.adventure.PaperAdventure.asVanilla(apiDeathMessage); // Paper - Adventure

            this.connection
                .send(
                    new ClientboundPlayerCombatKillPacket(this.getId(), deathMessage),
                    PacketSendListener.exceptionallySend(
                        () -> {
                            int i = 256;
                            String string = deathMessage.getString(256);
                            Component component = Component.translatable(
                                "death.attack.message_too_long", Component.literal(string).withStyle(ChatFormatting.YELLOW)
                            );
                            Component component1 = Component.translatable("death.attack.even_more_magic", this.getDisplayName())
                                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, component)));
                            return new ClientboundPlayerCombatKillPacket(this.getId(), component1);
                        }
                    )
                );
            Team team = this.getTeam();
            if (org.purpurmc.purpur.PurpurConfig.deathMessageOnlyBroadcastToAffectedPlayer) this.sendSystemMessage(deathMessage); else // Purpur - Configurable broadcast settings
            if (team == null || team.getDeathMessageVisibility() == Team.Visibility.ALWAYS) {
                this.server.getPlayerList().broadcastSystemMessage(deathMessage, false);
            } else if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OTHER_TEAMS) {
                this.server.getPlayerList().broadcastSystemToTeam(this, deathMessage);
            } else if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OWN_TEAM) {
                this.server.getPlayerList().broadcastSystemToAllExceptTeam(this, deathMessage);
            }
        } else {
            this.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), CommonComponents.EMPTY));
        }

        this.removeEntitiesOnShoulder();
        if (this.serverLevel().getGameRules().getBoolean(GameRules.RULE_FORGIVE_DEAD_PLAYERS)) {
            this.tellNeutralMobsThatIDied();
        }

        // SPIGOT-5478 must be called manually now
        if (event.shouldDropExperience()) this.dropExperience(this.serverLevel(), cause.getEntity()); // Paper - tie to event
        // we clean the player's inventory after the EntityDeathEvent is called so plugins can get the exact state of the inventory.
        if (!event.getKeepInventory()) {
            // Paper start - PlayerDeathEvent#getItemsToKeep
            for (NonNullList<ItemStack> inv : this.getInventory().compartments) {
                processKeep(event, inv);
            }
            processKeep(event, null);
            // Paper end - PlayerDeathEvent#getItemsToKeep
        }

        this.setCamera(this); // Remove spectated target
        // CraftBukkit end

        this.level().getCraftServer().getScoreboardManager().forAllObjectives(ObjectiveCriteria.DEATH_COUNT, this, ScoreAccess::increment); // CraftBukkit - Get our scores instead
        LivingEntity killCredit = this.getKillCredit();
        if (killCredit != null) {
            this.awardStat(Stats.ENTITY_KILLED_BY.get(killCredit.getType()));
            killCredit.awardKillScore(this, cause);
            this.createWitherRose(killCredit);
        }

        this.level().broadcastEntityEvent(this, (byte)3);
        this.awardStat(Stats.DEATHS);
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        this.clearFire();
        this.setTicksFrozen(0);
        this.setSharedFlagOnFire(false);
        this.getCombatTracker().recheckStatus();
        this.setLastDeathLocation(Optional.of(GlobalPos.of(this.level().dimension(), this.blockPosition())));
        this.setClientLoaded(false);
    }

    private void tellNeutralMobsThatIDied() {
        AABB aabb = new AABB(this.blockPosition()).inflate(32.0, 10.0, 32.0);
        this.level()
            .getEntitiesOfClass(Mob.class, aabb, EntitySelector.NO_SPECTATORS)
            .stream()
            .filter(mob -> mob instanceof NeutralMob)
            .forEach(mob -> ((NeutralMob)mob).playerDied(this.serverLevel(), this));
    }

    @Override
    public void awardKillScore(Entity entity, DamageSource damageSource) {
        if (entity != this) {
            super.awardKillScore(entity, damageSource);
            this.level().getCraftServer().getScoreboardManager().forAllObjectives(ObjectiveCriteria.KILL_COUNT_ALL, this, ScoreAccess::increment); // CraftBukkit - Get our scores instead
            if (entity instanceof Player) {
                this.awardStat(Stats.PLAYER_KILLS);
                this.level().getCraftServer().getScoreboardManager().forAllObjectives(ObjectiveCriteria.KILL_COUNT_PLAYERS, this, ScoreAccess::increment); // CraftBukkit - Get our scores instead
            } else {
                this.awardStat(Stats.MOB_KILLS);
            }

            this.handleTeamKill(this, entity, ObjectiveCriteria.TEAM_KILL);
            this.handleTeamKill(entity, this, ObjectiveCriteria.KILLED_BY_TEAM);
            CriteriaTriggers.PLAYER_KILLED_ENTITY.trigger(this, entity, damageSource);
        }
    }

    private void handleTeamKill(ScoreHolder scoreHolder, ScoreHolder teamMember, ObjectiveCriteria[] crtieria) {
        PlayerTeam playersTeam = this.getScoreboard().getPlayersTeam(teamMember.getScoreboardName());
        if (playersTeam != null) {
            int id = playersTeam.getColor().getId();
            if (id >= 0 && id < crtieria.length) {
                this.level().getCraftServer().getScoreboardManager().forAllObjectives(crtieria[id], scoreHolder, ScoreAccess::increment); // CraftBukkit - Get our scores instead
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableTo(level, damageSource)) {
            return false;
        } else {
            // Purpur start - Add boat fall damage config
            if (damageSource.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
                if (getRootVehicle() instanceof net.minecraft.world.entity.vehicle.Boat && !level().purpurConfig.boatsDoFallDamage) {
                    return false;
                }
            }
            // Purpur end - Add boat fall damage config
            Entity entity = damageSource.getEntity();
            if (!( // Paper - split the if statement. If below statement is false, hurtServer would not have been evaluated. Return false.
             !(entity instanceof Player player && !this.canHarmPlayer(player))
                && !(entity instanceof AbstractArrow abstractArrow && abstractArrow.getOwner() instanceof Player player1 && !this.canHarmPlayer(player1))
            )) return false; // Paper - split the if statement. If below statement is false, hurtServer would not have been evaluated. Return false.
            // Paper start - cancellable death events
            this.queueHealthUpdatePacket = true;
            boolean damaged = super.hurtServer(level, damageSource, amount);
            this.queueHealthUpdatePacket = false;
            if (this.queuedHealthUpdatePacket != null) {
                this.connection.send(this.queuedHealthUpdatePacket);
                this.queuedHealthUpdatePacket = null;
            }
            return damaged;
            // Paper end - cancellable death events
        }
    }

    @Override
    public boolean canHarmPlayer(Player other) {
        return this.isPvpAllowed() && super.canHarmPlayer(other);
    }

    private boolean isPvpAllowed() {
        return this.level().pvpMode; // CraftBukkit - this.server.isPvpAllowed() -> this.world.pvpMode
    }

    // CraftBukkit start
    public TeleportTransition findRespawnPositionAndUseSpawnBlock(boolean useCharge, TeleportTransition.PostTeleportTransition postTeleportTransition, org.bukkit.event.player.PlayerRespawnEvent.RespawnReason respawnReason) {
        TeleportTransition teleportTransition;
        boolean isBedSpawn = false;
        boolean isAnchorSpawn = false;
        // CraftBukkit end
        BlockPos respawnPosition = this.getRespawnPosition();
        float respawnAngle = this.getRespawnAngle();
        boolean isRespawnForced = this.isRespawnForced();
        ServerLevel level = this.server.getLevel(this.getRespawnDimension());
        if (level != null && respawnPosition != null) {
            Optional<ServerPlayer.RespawnPosAngle> optional = findRespawnAndUseSpawnBlock(level, respawnPosition, respawnAngle, isRespawnForced, useCharge);
            if (optional.isPresent()) {
                ServerPlayer.RespawnPosAngle respawnPosAngle = optional.get();
                // CraftBukkit start
                isBedSpawn = respawnPosAngle.isBedSpawn();
                isAnchorSpawn = respawnPosAngle.isAnchorSpawn();
                teleportTransition = new TeleportTransition(level, respawnPosAngle.position(), Vec3.ZERO, respawnPosAngle.yaw(), 0.0F, postTeleportTransition);
                // CraftBukkit end
            } else {
                teleportTransition = TeleportTransition.missingRespawnBlock(this.server.overworld(), this, postTeleportTransition); // CraftBukkit
            }
        } else {
            teleportTransition = new TeleportTransition(this.server.overworld(), this, postTeleportTransition); // CraftBukkit
        }
        // CraftBukkit start
        if (respawnReason == null) {
            return teleportTransition;
        }

        org.bukkit.entity.Player respawnPlayer = this.getBukkitEntity();
        org.bukkit.Location location = org.bukkit.craftbukkit.util.CraftLocation.toBukkit(
            teleportTransition.position(),
            teleportTransition.newLevel().getWorld(),
            teleportTransition.yRot(),
            teleportTransition.xRot()
        );

        // Paper start - respawn flags
        com.google.common.collect.ImmutableSet.Builder<org.bukkit.event.player.PlayerRespawnEvent.RespawnFlag> builder = com.google.common.collect.ImmutableSet.builder();
        if (respawnReason == org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.END_PORTAL) {
            builder.add(org.bukkit.event.player.PlayerRespawnEvent.RespawnFlag.END_PORTAL);
        }
        org.bukkit.event.player.PlayerRespawnEvent respawnEvent = new org.bukkit.event.player.PlayerRespawnEvent(
            respawnPlayer,
            location,
            isBedSpawn,
            isAnchorSpawn,
            respawnReason,
            builder
        );
        // Paper end - respawn flags
        this.level().getCraftServer().getPluginManager().callEvent(respawnEvent);
        // Spigot start
        if (this.connection.isDisconnected()) {
            return null;
        }
        // Spigot end

        location = respawnEvent.getRespawnLocation();

        return new TeleportTransition(
            ((org.bukkit.craftbukkit.CraftWorld) location.getWorld()).getHandle(),
            org.bukkit.craftbukkit.util.CraftLocation.toVec3D(location),
            teleportTransition.deltaMovement(),
            location.getYaw(),
            location.getPitch(),
            teleportTransition.missingRespawnBlock(),
            teleportTransition.asPassenger(),
            teleportTransition.relatives(),
            teleportTransition.postTeleportTransition(),
            teleportTransition.cause()
        );
        // CraftBukkit end
    }

    public static Optional<ServerPlayer.RespawnPosAngle> findRespawnAndUseSpawnBlock(
        ServerLevel level, BlockPos pos, float angle, boolean forced, boolean useCharge
    ) {
        BlockState blockState = level.getBlockState(pos);
        Block block = blockState.getBlock();
        if (block instanceof RespawnAnchorBlock && (forced || blockState.getValue(RespawnAnchorBlock.CHARGE) > 0) && RespawnAnchorBlock.canSetSpawn(level)) {
            Optional<Vec3> optional = RespawnAnchorBlock.findStandUpPosition(EntityType.PLAYER, level, pos);
            if (!forced && useCharge && optional.isPresent()) {
                level.setBlock(pos, blockState.setValue(RespawnAnchorBlock.CHARGE, Integer.valueOf(blockState.getValue(RespawnAnchorBlock.CHARGE) - 1)), 3);
            }

            return optional.map(vec3 -> ServerPlayer.RespawnPosAngle.of(vec3, pos, false, true)); // CraftBukkit
        } else if (block instanceof BedBlock && BedBlock.canSetSpawn(level)) {
            return BedBlock.findStandUpPosition(EntityType.PLAYER, level, pos, blockState.getValue(BedBlock.FACING), angle)
                .map(vec3 -> ServerPlayer.RespawnPosAngle.of(vec3, pos, true, false)); // CraftBukkit
        } else if (!forced) {
            return Optional.empty();
        } else {
            boolean isPossibleToRespawnInThis = block.isPossibleToRespawnInThis(blockState);
            BlockState blockState1 = level.getBlockState(pos.above());
            boolean isPossibleToRespawnInThis1 = blockState1.getBlock().isPossibleToRespawnInThis(blockState1);
            return isPossibleToRespawnInThis && isPossibleToRespawnInThis1
                ? Optional.of(new ServerPlayer.RespawnPosAngle(new Vec3(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5), angle, false, false)) // CraftBukkit
                : Optional.empty();
        }
    }

    public void showEndCredits() {
        this.unRide();
        this.serverLevel().removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
        if (!this.wonGame) {
            this.wonGame = true;
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, 0.0F));
            this.seenCredits = true;
        }
    }

    @Nullable
    @Override
    public ServerPlayer teleport(TeleportTransition teleportTransition) {
        if (this.isSleeping()) return null; // CraftBukkit - SPIGOT-3154
        if (this.isRemoved()) {
            return null;
        } else {
            if (teleportTransition.missingRespawnBlock()) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
            }

            ServerLevel level = teleportTransition.newLevel();
            ServerLevel serverLevel = this.serverLevel();
            // CraftBukkit start
            ResourceKey<net.minecraft.world.level.dimension.LevelStem> resourceKey = serverLevel.getTypeKey();

            org.bukkit.Location enter = this.getBukkitEntity().getLocation();
            PositionMoveRotation absolutePosition = PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(this), PositionMoveRotation.of(teleportTransition), teleportTransition.relatives());
            org.bukkit.Location exit = /* (worldserver == null) ? null : // Paper - always non-null */org.bukkit.craftbukkit.util.CraftLocation.toBukkit(absolutePosition.position(), level.getWorld(), absolutePosition.yRot(), absolutePosition.xRot());
            org.bukkit.event.player.PlayerTeleportEvent tpEvent = new org.bukkit.event.player.PlayerTeleportEvent(this.getBukkitEntity(), enter, exit, teleportTransition.cause());
            // Paper start - gateway-specific teleport event
            if (this.portalProcess != null && this.portalProcess.isSamePortal(((net.minecraft.world.level.block.EndGatewayBlock) net.minecraft.world.level.block.Blocks.END_GATEWAY)) && this.serverLevel().getBlockEntity(this.portalProcess.getEntryPosition()) instanceof net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity theEndGatewayBlockEntity) {
                tpEvent = new com.destroystokyo.paper.event.player.PlayerTeleportEndGatewayEvent(this.getBukkitEntity(), enter, exit, new org.bukkit.craftbukkit.block.CraftEndGateway(this.serverLevel().getWorld(), theEndGatewayBlockEntity));
            }
            // Paper end - gateway-specific teleport event
            org.bukkit.Bukkit.getServer().getPluginManager().callEvent(tpEvent);
            org.bukkit.Location newExit = tpEvent.getTo();
            if (tpEvent.isCancelled() || newExit == null) {
                return null;
            }
            if (!newExit.equals(exit)) {
                level = ((org.bukkit.craftbukkit.CraftWorld) newExit.getWorld()).getHandle();
                teleportTransition = new TeleportTransition(
                    level,
                    org.bukkit.craftbukkit.util.CraftLocation.toVec3D(newExit),
                    Vec3.ZERO,
                    newExit.getYaw(),
                    newExit.getPitch(),
                    teleportTransition.missingRespawnBlock(),
                    teleportTransition.asPassenger(),
                    Set.of(),
                    teleportTransition.postTeleportTransition(),
                    teleportTransition.cause());
            }
            // CraftBukkit end
            if (!teleportTransition.asPassenger()) {
                this.stopRiding();
            }

            // CraftBukkit start
            if (level != null && level.dimension() == serverLevel.dimension()) {
                this.connection.internalTeleport(PositionMoveRotation.of(teleportTransition), teleportTransition.relatives());
                // CraftBukkit end
                this.connection.resetPosition();
                teleportTransition.postTeleportTransition().onTransition(this);
                return this;
            } else {
                // CraftBukkit start
                /*
                this.isChangingDimension = true;
                LevelData levelData = level.getLevelData();
                this.connection.send(new ClientboundRespawnPacket(this.createCommonSpawnInfo(level), (byte)3));
                this.connection.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
                PlayerList playerList = this.server.getPlayerList();
                playerList.sendPlayerPermissionLevel(this);
                serverLevel.removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
                this.unsetRemoved();
                */
                // CraftBukkit end
                ProfilerFiller profilerFiller = Profiler.get();
                profilerFiller.push("moving");
                if (level != null && resourceKey == net.minecraft.world.level.dimension.LevelStem.OVERWORLD && level.getTypeKey() == net.minecraft.world.level.dimension.LevelStem.NETHER) { // CraftBukkit - empty to fall through to null to event
                    this.enteredNetherPosition = this.position();
                }

                profilerFiller.pop();
                profilerFiller.push("placing");
                // CraftBukkit start
                this.isChangingDimension = true; // CraftBukkit - Set teleport invulnerability only if player changing worlds
                LevelData worlddata = level.getLevelData();

                this.connection.send(new ClientboundRespawnPacket(this.createCommonSpawnInfo(level), (byte) 3));
                this.connection.send(new ClientboundChangeDifficultyPacket(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
                PlayerList playerList = this.server.getPlayerList();

                playerList.sendPlayerPermissionLevel(this);
                serverLevel.removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
                this.unsetRemoved();
                // CraftBukkit end
                this.portalPos = io.papermc.paper.util.MCUtil.toBlockPosition(exit); // Purpur - Fix stuck in portals
                this.setServerLevel(level);
                this.connection.internalTeleport(PositionMoveRotation.of(teleportTransition), teleportTransition.relatives()); // CraftBukkit - use internal teleport without event
                this.connection.resetPosition();
                level.addDuringTeleport(this);
                profilerFiller.pop();
                this.triggerDimensionChangeTriggers(serverLevel);
                this.stopUsingItem();
                this.connection.send(new ClientboundPlayerAbilitiesPacket(this.getAbilities()));
                playerList.sendLevelInfo(this, level);
                playerList.sendAllPlayerInfo(this);
                playerList.sendActivePlayerEffects(this);
                teleportTransition.postTeleportTransition().onTransition(this);
                this.lastSentExp = -1;
                this.lastSentHealth = -1.0F;
                this.lastSentFood = -1;


                // CraftBukkit start
                org.bukkit.event.player.PlayerChangedWorldEvent changeEvent = new org.bukkit.event.player.PlayerChangedWorldEvent(this.getBukkitEntity(), serverLevel.getWorld());
                this.level().getCraftServer().getPluginManager().callEvent(changeEvent);
                // CraftBukkit end
                // Paper start - Reset shield blocking on dimension change
                if (this.isBlocking()) {
                    this.stopUsingItem();
                }
                // Paper end - Reset shield blocking on dimension change
                return this;
            }
        }
    }

    // CraftBukkit start
    @Override
    public org.bukkit.craftbukkit.event.CraftPortalEvent callPortalEvent(
        Entity entity,
        org.bukkit.Location exit,
        org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause,
        int searchRadius,
        int creationRadius
    ) {
        org.bukkit.Location enter = this.getBukkitEntity().getLocation();
        org.bukkit.event.player.PlayerPortalEvent event = new org.bukkit.event.player.PlayerPortalEvent(this.getBukkitEntity(), enter, exit, cause, searchRadius, true, creationRadius);
        org.bukkit.Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getTo() == null || event.getTo().getWorld() == null) {
            return null;
        }
        return new org.bukkit.craftbukkit.event.CraftPortalEvent(event);
    }
    // CraftBukkit end

    @Override
    public void forceSetRotation(float yRot, float xRot) {
        this.connection.send(new ClientboundPlayerRotationPacket(yRot, xRot));
    }

    public void triggerDimensionChangeTriggers(ServerLevel level) {
        ResourceKey<Level> resourceKey = level.dimension();
        ResourceKey<Level> resourceKey1 = this.level().dimension();
        // CraftBukkit start
        ResourceKey<Level> maindimensionkey = org.bukkit.craftbukkit.util.CraftDimensionUtil.getMainDimensionKey(level);
        ResourceKey<Level> maindimensionkey1 = org.bukkit.craftbukkit.util.CraftDimensionUtil.getMainDimensionKey(this.level());
        // Paper start - Add option for strict advancement dimension checks
        if (io.papermc.paper.configuration.GlobalConfiguration.get().misc.strictAdvancementDimensionCheck) {
            maindimensionkey = resourceKey;
            maindimensionkey1 = resourceKey1;
        }
        // Paper end - Add option for strict advancement dimension checks
        CriteriaTriggers.CHANGED_DIMENSION.trigger(this, maindimensionkey, maindimensionkey1);
        if (maindimensionkey != resourceKey || maindimensionkey1 != resourceKey1) {
            CriteriaTriggers.CHANGED_DIMENSION.trigger(this, resourceKey, resourceKey1);
        }

        if (maindimensionkey == Level.NETHER && maindimensionkey1 == Level.OVERWORLD && this.enteredNetherPosition != null) {
            // CraftBukkit end
            CriteriaTriggers.NETHER_TRAVEL.trigger(this, this.enteredNetherPosition);
        }

        if (maindimensionkey1 != Level.NETHER) { // CraftBukkit
            this.enteredNetherPosition = null;
        }
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return player.isSpectator() ? this.getCamera() == this : !this.isSpectator() && super.broadcastToPlayer(player);
    }

    @Override
    public void take(Entity entity, int quantity) {
        super.take(entity, quantity);
        this.containerMenu.broadcastChanges();
    }

    // CraftBukkit start - moved bed result checks from below into separate method
    private Either<Player.BedSleepingProblem, Unit> getBedResult(BlockPos at, Direction direction) {
        if (this.isSleeping() || !this.isAlive()) {
            return Either.left(Player.BedSleepingProblem.OTHER_PROBLEM);
        } else if (!this.level().dimensionType().natural() && !this.level().dimensionType().bedWorks()) { // CraftBukkit - moved bed result checks from below into separate method
            return Either.left(Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
        } else if (!this.bedInRange(at, direction)) {
            return Either.left(Player.BedSleepingProblem.TOO_FAR_AWAY);
        } else if (this.bedBlocked(at, direction)) {
            return Either.left(Player.BedSleepingProblem.OBSTRUCTED);
        } else {
            this.setRespawnPosition(this.level().dimension(), at, this.getYRot(), false, true, com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.BED); // Paper - Add PlayerSetSpawnEvent
            if (this.level().isDay()) {
                return Either.left(Player.BedSleepingProblem.NOT_POSSIBLE_NOW);
            } else {
                if (!this.isCreative()) {
                    double d = 8.0;
                    double d1 = 5.0;
                    Vec3 vec3 = Vec3.atBottomCenterOf(at);
                    List<Monster> entitiesOfClass = this.level()
                        .getEntitiesOfClass(
                            Monster.class,
                            new AABB(vec3.x() - 8.0, vec3.y() - 5.0, vec3.z() - 8.0, vec3.x() + 8.0, vec3.y() + 5.0, vec3.z() + 8.0),
                            monster -> monster.isPreventingPlayerRest(this.serverLevel(), this)
                        );
                    if (!this.level().purpurConfig.playerSleepNearMonsters && !entitiesOfClass.isEmpty()) { // Purpur - Config to ignore nearby mobs when sleeping
                        return Either.left(Player.BedSleepingProblem.NOT_SAFE);
                    }
                }

    // CraftBukkit start
                return Either.right(Unit.INSTANCE);
            }
        }
    }

    @Override
    public Either<net.minecraft.world.entity.player.Player.BedSleepingProblem, Unit> startSleepInBed(BlockPos at, boolean force) {
        Direction enumdirection = (Direction) this.level().getBlockState(at).getValue(HorizontalDirectionalBlock.FACING);
        Either<net.minecraft.world.entity.player.Player.BedSleepingProblem, Unit> bedResult = this.getBedResult(at, enumdirection);

        if (bedResult.left().orElse(null) == net.minecraft.world.entity.player.Player.BedSleepingProblem.OTHER_PROBLEM) {
            return bedResult; // return immediately if the result is not bypassable by plugins
        }

        if (force) {
            bedResult = Either.right(Unit.INSTANCE);
        }

        bedResult = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerBedEnterEvent(this, at, bedResult);
        if (bedResult.left().isPresent()) {
            return bedResult;
        }

        {
            {
                Either<net.minecraft.world.entity.player.Player.BedSleepingProblem, Unit> either = super.startSleepInBed(at, force).ifRight(unit -> {
                    // CraftBukkit end
                    this.awardStat(Stats.SLEEP_IN_BED);
                    CriteriaTriggers.SLEPT_IN_BED.trigger(this);
                });
                if (!this.serverLevel().canSleepThroughNights()) {
                    // Purpur start - Customizable sleeping actionbar messages
                    Component clientMessage;
                    if (org.purpurmc.purpur.PurpurConfig.sleepNotPossible.isBlank()) {
                        clientMessage = null;
                    } else if (!org.purpurmc.purpur.PurpurConfig.sleepNotPossible.equalsIgnoreCase("default")) {
                        clientMessage = io.papermc.paper.adventure.PaperAdventure.asVanilla(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(org.purpurmc.purpur.PurpurConfig.sleepNotPossible));
                    } else {
                        clientMessage = Component.translatable("sleep.not_possible");
                    }
                    if (clientMessage != null) {
                        this.displayClientMessage(clientMessage, true);
                    }
                    // Purpur end - Customizable sleeping actionbar messages
                }

                ((ServerLevel)this.level()).updateSleepingPlayerList();
                return either;
            }
        }
    }

    @Override
    public void startSleeping(BlockPos pos) {
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        super.startSleeping(pos);
    }

    private boolean bedInRange(BlockPos pos, Direction direction) {
        return this.isReachableBedBlock(pos) || this.isReachableBedBlock(pos.relative(direction.getOpposite()));
    }

    private boolean isReachableBedBlock(BlockPos pos) {
        Vec3 vec3 = Vec3.atBottomCenterOf(pos);
        return Math.abs(this.getX() - vec3.x()) <= 3.0 && Math.abs(this.getY() - vec3.y()) <= 2.0 && Math.abs(this.getZ() - vec3.z()) <= 3.0;
    }

    private boolean bedBlocked(BlockPos pos, Direction direction) {
        BlockPos blockPos = pos.above();
        return !this.freeAt(blockPos) || !this.freeAt(blockPos.relative(direction.getOpposite()));
    }

    @Override
    public void stopSleepInBed(boolean wakeImmediately, boolean updateLevelForSleepingPlayers) {
        if (!this.isSleeping()) return; // CraftBukkit - Can't leave bed if not in one!
        // CraftBukkit start - fire PlayerBedLeaveEvent
        org.bukkit.craftbukkit.entity.CraftPlayer player = this.getBukkitEntity();
        BlockPos bedPosition = this.getSleepingPos().orElse(null);

        org.bukkit.block.Block bed;
        if (bedPosition != null) {
            bed = this.level().getWorld().getBlockAt(bedPosition.getX(), bedPosition.getY(), bedPosition.getZ());
        } else {
            bed = this.level().getWorld().getBlockAt(player.getLocation());
        }

        org.bukkit.event.player.PlayerBedLeaveEvent event = new org.bukkit.event.player.PlayerBedLeaveEvent(player, bed, true);
        this.level().getCraftServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        // CraftBukkit end
        if (this.isSleeping()) {
            this.serverLevel().getChunkSource().broadcastAndSend(this, new ClientboundAnimatePacket(this, 2));
        }

        super.stopSleepInBed(wakeImmediately, updateLevelForSleepingPlayers);
        if (this.connection != null) {
            this.connection.teleport(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.EXIT_BED); // CraftBukkit
        }
    }

    @Override
    public void dismountTo(double x, double y, double z) {
        this.removeVehicle();
        this.setPos(x, y, z);
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource damageSource) {
        return (super.isInvulnerableTo(level, damageSource) // Paper - disable player cramming;
            || this.isChangingDimension() && !damageSource.is(DamageTypes.ENDER_PEARL)
            || !this.hasClientLoaded()) || (!this.level().paperConfig().collisions.allowPlayerCrammingDamage && damageSource.is(DamageTypes.CRAMMING)); // Paper - disable player cramming;
    }

    @Override
    protected void onChangedBlock(ServerLevel level, BlockPos pos) {
        if (!this.isSpectator()) {
            super.onChangedBlock(level, pos);
        }
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        if (this.spawnExtraParticlesOnFall && onGround && this.fallDistance > 0.0F) {
            Vec3 vec3 = pos.getCenter().add(0.0, 0.5, 0.0);
            int i = (int)Mth.clamp(50.0F * this.fallDistance, 0.0F, 200.0F);
            this.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), vec3.x, vec3.y, vec3.z, i, 0.3F, 0.3F, 0.3F, 0.15F);
            this.spawnExtraParticlesOnFall = false;
        }

        super.checkFallDamage(y, onGround, state, pos);
    }

    @Override
    public void onExplosionHit(@Nullable Entity entity) {
        super.onExplosionHit(entity);
        this.currentImpulseImpactPos = this.position();
        this.currentExplosionCause = entity;
        this.setIgnoreFallDamageFromCurrentImpulse(entity != null && entity.getType() == EntityType.WIND_CHARGE);
    }

    @Override
    protected void pushEntities() {
        if (this.level().tickRateManager().runsNormally()) {
            super.pushEntities();
        }
    }

    @Override
    public void openTextEdit(SignBlockEntity signEntity, boolean isFrontText) {
        if (level().purpurConfig.signAllowColors) this.connection.send(signEntity.getTranslatedUpdatePacket(textFilteringEnabled, isFrontText)); // Purpur - Signs allow color codes
        this.connection.send(new ClientboundBlockUpdatePacket(this.level(), signEntity.getBlockPos()));
        this.connection.send(new ClientboundOpenSignEditorPacket(signEntity.getBlockPos(), isFrontText));
    }

    public int nextContainerCounter() { // CraftBukkit - void -> int
        this.containerCounter = this.containerCounter % 100 + 1;
        return this.containerCounter; // CraftBukkit
    }

    @Override
    public OptionalInt openMenu(@Nullable MenuProvider menu) {
        if (menu == null) {
            return OptionalInt.empty();
        } else {
            // CraftBukkit start - SPIGOT-6552: Handle inventory closing in CraftEventFactory#callInventoryOpenEvent(...)
            /*
            if (this.containerMenu != this.inventoryMenu) {
                this.closeContainer();
            }
            */
            // CraftBukkit end

            this.nextContainerCounter();
            AbstractContainerMenu abstractContainerMenu = menu.createMenu(this.containerCounter, this.getInventory(), this);
            Component title = null; // Paper - Add titleOverride to InventoryOpenEvent
            // CraftBukkit start - Inventory open hook
            if (abstractContainerMenu != null) {
                abstractContainerMenu.setTitle(menu.getDisplayName());

                boolean cancelled = false;
                // Paper start - Add titleOverride to InventoryOpenEvent
                final com.mojang.datafixers.util.Pair<net.kyori.adventure.text.Component, AbstractContainerMenu> result = org.bukkit.craftbukkit.event.CraftEventFactory.callInventoryOpenEventWithTitle(this, abstractContainerMenu, cancelled);
                abstractContainerMenu = result.getSecond();
                title = io.papermc.paper.adventure.PaperAdventure.asVanilla(result.getFirst());
                // Paper end - Add titleOverride to InventoryOpenEvent
                if (abstractContainerMenu == null && !cancelled) { // Let pre-cancelled events fall through
                    // SPIGOT-5263 - close chest if cancelled
                    if (menu instanceof Container) {
                        ((Container) menu).stopOpen(this);
                    } else if (menu instanceof net.minecraft.world.level.block.ChestBlock.DoubleInventory doubleInventory) {
                        // SPIGOT-5355 - double chests too :(
                        doubleInventory.container.stopOpen(this);
                        // Paper start - Fix InventoryOpenEvent cancellation
                    } else if (!this.enderChestInventory.isActiveChest(null)) {
                        this.enderChestInventory.stopOpen(this);
                        // Paper end - Fix InventoryOpenEvent cancellation
                    }
                    return OptionalInt.empty();
                }
            }
            // CraftBukkit end
            if (abstractContainerMenu == null) {
                if (this.isSpectator()) {
                    this.displayClientMessage(Component.translatable("container.spectatorCantOpen").withStyle(ChatFormatting.RED), true);
                }

                return OptionalInt.empty();
            } else {
                // CraftBukkit start
                this.containerMenu = abstractContainerMenu; // Moved up
                if (!this.isImmobile())
                this.connection
                    .send(new net.minecraft.network.protocol.game.ClientboundOpenScreenPacket(abstractContainerMenu.containerId, abstractContainerMenu.getType(), java.util.Objects.requireNonNullElseGet(title, abstractContainerMenu::getTitle))); // Paper - Add titleOverride to InventoryOpenEven
                // CraftBukkit end
                this.initMenu(abstractContainerMenu);
                // CraftBukkit - moved up
                return OptionalInt.of(this.containerCounter);
            }
        }
    }

    @Override
    public void sendMerchantOffers(int containerId, MerchantOffers offers, int level, int xp, boolean flag, boolean flag1) {
        this.connection.send(new ClientboundMerchantOffersPacket(containerId, offers, level, xp, flag, flag1));
    }

    @Override
    public void openHorseInventory(AbstractHorse horse, Container inventory) {
        // CraftBukkit start - Inventory open hook
        this.nextContainerCounter(); // Moved up from below
        AbstractContainerMenu container = new HorseInventoryMenu(this.containerCounter, this.getInventory(), inventory, horse, horse.getInventoryColumns());
        container.setTitle(horse.getDisplayName());
        container = org.bukkit.craftbukkit.event.CraftEventFactory.callInventoryOpenEvent(this, container);

        if (container == null) {
            inventory.stopOpen(this);
            return;
        }
        // CraftBukkit end
        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.OPEN_NEW); // Paper - Inventory close reason
        }

        // this.nextContainerCounter(); // CraftBukkit - moved up
        int inventoryColumns = horse.getInventoryColumns();
        this.connection.send(new ClientboundHorseScreenOpenPacket(this.containerCounter, inventoryColumns, horse.getId()));
        this.containerMenu = container; // CraftBukkit
        this.initMenu(this.containerMenu);
    }

    @Override
    public void openItemGui(ItemStack stack, InteractionHand hand) {
        if (stack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
            if (WrittenBookItem.resolveBookComponents(stack, this.createCommandSourceStack(), this)) {
                this.containerMenu.broadcastChanges();
            }

            this.connection.send(new ClientboundOpenBookPacket(hand));
        }
    }

    @Override
    public void openCommandBlock(CommandBlockEntity commandBlock) {
        this.connection.send(ClientboundBlockEntityDataPacket.create(commandBlock, BlockEntity::saveCustomOnly));
    }

    @Override
    public void closeContainer() {
        // Paper start - Inventory close reason
        this.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.UNKNOWN);
    }
    @Override
    public void closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) {
        org.bukkit.craftbukkit.event.CraftEventFactory.handleInventoryCloseEvent(this, reason); // CraftBukkit
        // Paper end - Inventory close reason
        this.connection.send(new ClientboundContainerClosePacket(this.containerMenu.containerId));
        this.doCloseContainer();
    }
    // Paper start - special close for unloaded inventory
    @Override
    public void closeUnloadedInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) {
        // copied from above
        org.bukkit.craftbukkit.event.CraftEventFactory.handleInventoryCloseEvent(this, reason); // CraftBukkit
        // Paper end
        // copied from below
        this.connection.send(new ClientboundContainerClosePacket(this.containerMenu.containerId));
        this.containerMenu = this.inventoryMenu;
        // do not run close logic
    }
    // Paper end - special close for unloaded inventory

    @Override
    public void doCloseContainer() {
        this.containerMenu.removed(this);
        this.inventoryMenu.transferState(this.containerMenu);
        this.containerMenu = this.inventoryMenu;
    }

    @Override
    public void rideTick() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        super.rideTick();
        this.checkRidingStatistics(this.getX() - x, this.getY() - y, this.getZ() - z);
    }

    public void checkMovementStatistics(double dx, double dy, double dz) {
        if (!this.isPassenger() && !didNotMove(dx, dy, dz)) {
            if (this.isSwimming()) {
                int rounded = Math.round((float)Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F);
                if (rounded > 0) {
                    this.awardStat(Stats.SWIM_ONE_CM, rounded);
                    this.causeFoodExhaustion(this.level().spigotConfig.swimMultiplier * (float) rounded * 0.01F, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.SWIM); // CraftBukkit - EntityExhaustionEvent // Spigot
                }
            } else if (this.isEyeInFluid(FluidTags.WATER)) {
                int rounded = Math.round((float)Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F);
                if (rounded > 0) {
                    this.awardStat(Stats.WALK_UNDER_WATER_ONE_CM, rounded);
                    this.causeFoodExhaustion(this.level().spigotConfig.swimMultiplier * (float) rounded * 0.01F, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.WALK_UNDERWATER); // CraftBukkit - EntityExhaustionEvent // Spigot
                }
            } else if (this.isInWater()) {
                int rounded = Math.round((float)Math.sqrt(dx * dx + dz * dz) * 100.0F);
                if (rounded > 0) {
                    this.awardStat(Stats.WALK_ON_WATER_ONE_CM, rounded);
                    this.causeFoodExhaustion(this.level().spigotConfig.swimMultiplier * (float) rounded * 0.01F, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.WALK_ON_WATER); // CraftBukkit - EntityExhaustionEvent // Spigot
                }
            } else if (this.onClimbable()) {
                if (dy > 0.0) {
                    this.awardStat(Stats.CLIMB_ONE_CM, (int)Math.round(dy * 100.0));
                }
            } else if (this.onGround()) {
                int rounded = Math.round((float)Math.sqrt(dx * dx + dz * dz) * 100.0F);
                if (rounded > 0) {
                    if (this.isSprinting()) {
                        this.awardStat(Stats.SPRINT_ONE_CM, rounded);
                        this.causeFoodExhaustion(this.level().spigotConfig.sprintMultiplier * (float) rounded * 0.01F, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.SPRINT); // CraftBukkit - EntityExhaustionEvent // Spigot
                    } else if (this.isCrouching()) {
                        this.awardStat(Stats.CROUCH_ONE_CM, rounded);
                        this.causeFoodExhaustion(this.level().spigotConfig.otherMultiplier * (float) rounded * 0.01F, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.CROUCH); // CraftBukkit - EntityExhaustionEvent // Spigot
                    } else {
                        this.awardStat(Stats.WALK_ONE_CM, rounded);
                        this.causeFoodExhaustion(this.level().spigotConfig.otherMultiplier * (float) rounded * 0.01F, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.WALK); // CraftBukkit - EntityExhaustionEvent // Spigot
                    }
                }
            } else if (this.isFallFlying()) {
                int rounded = Math.round((float)Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F);
                this.awardStat(Stats.AVIATE_ONE_CM, rounded);
            } else {
                int rounded = Math.round((float)Math.sqrt(dx * dx + dz * dz) * 100.0F);
                if (rounded > 25) {
                    this.awardStat(Stats.FLY_ONE_CM, rounded);
                }
            }
        }
    }

    private void checkRidingStatistics(double dx, double dy, double dz) {
        if (this.isPassenger() && !didNotMove(dx, dy, dz)) {
            int rounded = Math.round((float)Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F);
            Entity vehicle = this.getVehicle();
            if (vehicle instanceof AbstractMinecart) {
                this.awardStat(Stats.MINECART_ONE_CM, rounded);
            } else if (vehicle instanceof AbstractBoat) {
                this.awardStat(Stats.BOAT_ONE_CM, rounded);
            } else if (vehicle instanceof Pig) {
                this.awardStat(Stats.PIG_ONE_CM, rounded);
            } else if (vehicle instanceof AbstractHorse) {
                this.awardStat(Stats.HORSE_ONE_CM, rounded);
            } else if (vehicle instanceof Strider) {
                this.awardStat(Stats.STRIDER_ONE_CM, rounded);
            }
        }
    }

    private static boolean didNotMove(double dx, double dy, double dz) {
        return dx == 0.0 && dy == 0.0 && dz == 0.0;
    }

    @Override
    public void awardStat(Stat<?> stat, int amount) {
        this.stats.increment(this, stat, amount);
        this.level().getCraftServer().getScoreboardManager().forAllObjectives(stat, this, score -> score.add(amount)); // CraftBukkit - Get our scores instead
    }

    @Override
    public void resetStat(Stat<?> stat) {
        this.stats.setValue(this, stat, 0);
        this.level().getCraftServer().getScoreboardManager().forAllObjectives(stat, this, ScoreAccess::reset); // CraftBukkit - Get our scores instead
    }

    @Override
    public int awardRecipes(Collection<RecipeHolder<?>> recipes) {
        return this.recipeBook.addRecipes(recipes, this);
    }

    @Override
    public void triggerRecipeCrafted(RecipeHolder<?> recipe, List<ItemStack> items) {
        CriteriaTriggers.RECIPE_CRAFTED.trigger(this, recipe.id(), items);
    }

    @Override
    public void awardRecipesByKey(List<ResourceKey<Recipe<?>>> recipes) {
        List<RecipeHolder<?>> list = recipes.stream()
            .flatMap(resourceKey -> this.server.getRecipeManager().byKey((ResourceKey<Recipe<?>>)resourceKey).stream())
            .collect(Collectors.toList());
        this.awardRecipes(list);
    }

    @Override
    public int resetRecipes(Collection<RecipeHolder<?>> recipes) {
        return this.recipeBook.removeRecipes(recipes, this);
    }

    @Override
    public void jumpFromGround() {
        super.jumpFromGround();
        this.awardStat(Stats.JUMP);
        if (this.isSprinting()) {
            this.causeFoodExhaustion(this.level().spigotConfig.jumpSprintExhaustion, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.JUMP_SPRINT); // CraftBukkit - EntityExhaustionEvent // Spigot - Change to use configurable value
        } else {
            this.causeFoodExhaustion(this.level().spigotConfig.jumpWalkExhaustion, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.JUMP); // CraftBukkit - EntityExhaustionEvent // Spigot - Change to use configurable value
        }
    }

    @Override
    public void giveExperiencePoints(int xpPoints) {
        super.giveExperiencePoints(xpPoints);
        this.lastSentExp = -1;
    }

    public void disconnect() {
        this.disconnected = true;
        this.ejectPassengers();

        // Paper start - Workaround vehicle not tracking the passenger disconnection dismount
        if (this.isPassenger() && this.getVehicle() instanceof ServerPlayer) {
            this.stopRiding();
        }
        // Paper end - Workaround vehicle not tracking the passenger disconnection dismount

        if (this.isSleeping()) {
            this.stopSleepInBed(true, false);
        }
    }

    public boolean hasDisconnected() {
        return this.disconnected;
    }

    public void resetSentInfo() {
        this.lastSentHealth = -1.0E8F;
        this.lastSentExp = -1; // CraftBukkit - Added to reset
    }

    // Purpur start - Component related conveniences
    public void sendActionBarMessage(@Nullable String message) {
        if (message != null && !message.isEmpty()) {
            sendActionBarMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(message));
        }
    }

    public void sendActionBarMessage(@Nullable net.kyori.adventure.text.Component message) {
        if (message != null) {
            sendActionBarMessage(io.papermc.paper.adventure.PaperAdventure.asVanilla(message));
        }
    }

    public void sendActionBarMessage(@Nullable Component message) {
        if (message != null) {
            displayClientMessage(message, true);
        }
    }
    // Purpur end - Component related conveniences

    @Override
    public void displayClientMessage(Component chatComponent, boolean actionBar) {
        this.sendSystemMessage(chatComponent, actionBar);
    }

    @Override
    public void completeUsingItem() {
        if (!this.useItem.isEmpty() && this.isUsingItem()) {
            this.connection.send(new ClientboundEntityEventPacket(this, (byte)9));
            super.completeUsingItem();
        }
    }

    @Override
    public void lookAt(EntityAnchorArgument.Anchor anchor, Vec3 target) {
        super.lookAt(anchor, target);
        this.connection.send(new ClientboundPlayerLookAtPacket(anchor, target.x, target.y, target.z));
    }

    public void lookAt(EntityAnchorArgument.Anchor fromAnchor, Entity entity, EntityAnchorArgument.Anchor toAnchor) {
        Vec3 vec3 = toAnchor.apply(entity);
        super.lookAt(fromAnchor, vec3);
        this.connection.send(new ClientboundPlayerLookAtPacket(fromAnchor, entity, toAnchor));
    }

    public void restoreFrom(ServerPlayer that, boolean keepEverything) {
        this.wardenSpawnTracker = that.wardenSpawnTracker;
        this.chatSession = that.chatSession;
        this.gameMode.setGameModeForPlayer(that.gameMode.getGameModeForPlayer(), that.gameMode.getPreviousGameModeForPlayer());
        this.onUpdateAbilities();
        if (keepEverything) {
            this.getAttributes().assignBaseValues(that.getAttributes());
            // this.getAttributes().assignPermanentModifiers(that.getAttributes()); // CraftBukkit
            this.setHealth(that.getHealth());
            this.foodData = that.foodData;

            for (MobEffectInstance mobEffectInstance : that.getActiveEffects()) {
                // this.addEffect(new MobEffectInstance(mobEffectInstance)); // CraftBukkit
            }

            this.getInventory().replaceWith(that.getInventory());
            this.experienceLevel = that.experienceLevel;
            this.totalExperience = that.totalExperience;
            this.experienceProgress = that.experienceProgress;
            this.setScore(that.getScore());
            this.portalProcess = that.portalProcess;
        } else {
            this.getAttributes().assignBaseValues(that.getAttributes());
            // this.setHealth(this.getMaxHealth()); // CraftBukkit
            if (this.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || that.isSpectator()) {
                this.getInventory().replaceWith(that.getInventory());
                this.experienceLevel = that.experienceLevel;
                this.totalExperience = that.totalExperience;
                this.experienceProgress = that.experienceProgress;
                this.setScore(that.getScore());
            }
        }

        this.enchantmentSeed = that.enchantmentSeed;
        this.enderChestInventory = that.enderChestInventory;
        this.getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, that.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION));
        this.lastSentExp = -1;
        this.lastSentHealth = -1.0F;
        this.lastSentFood = -1;
        // this.recipeBook.copyOverData(that.recipeBook); // CraftBukkit
        this.seenCredits = that.seenCredits;
        this.enteredNetherPosition = that.enteredNetherPosition;
        this.chunkTrackingView = that.chunkTrackingView;
        this.setShoulderEntityLeft(that.getShoulderEntityLeft());
        this.setShoulderEntityRight(that.getShoulderEntityRight());
        this.setLastDeathLocation(that.getLastDeathLocation());
    }

    @Override
    protected void onEffectAdded(MobEffectInstance effectInstance, @Nullable Entity entity) {
        super.onEffectAdded(effectInstance, entity);
        this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), effectInstance, true));
        if (effectInstance.is(MobEffects.LEVITATION)) {
            this.levitationStartTime = this.tickCount;
            this.levitationStartPos = this.position();
        }

        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, entity);
    }

    @Override
    protected void onEffectUpdated(MobEffectInstance effectInstance, boolean forced, @Nullable Entity entity) {
        super.onEffectUpdated(effectInstance, forced, entity);
        this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), effectInstance, false));
        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, entity);
    }

    @Override
    protected void onEffectsRemoved(Collection<MobEffectInstance> effects) {
        super.onEffectsRemoved(effects);

        for (MobEffectInstance mobEffectInstance : effects) {
            this.connection.send(new ClientboundRemoveMobEffectPacket(this.getId(), mobEffectInstance.getEffect()));
            if (mobEffectInstance.is(MobEffects.LEVITATION)) {
                this.levitationStartPos = null;
            }
        }

        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, null);
    }

    @Override
    public void teleportTo(double x, double y, double z) {
        this.connection.teleport(new PositionMoveRotation(new Vec3(x, y, z), Vec3.ZERO, 0.0F, 0.0F), Relative.union(Relative.DELTA, Relative.ROTATION));
    }

    @Override
    public void teleportRelative(double dx, double dy, double dz) {
        this.connection.teleport(new PositionMoveRotation(new Vec3(dx, dy, dz), Vec3.ZERO, 0.0F, 0.0F), Relative.ALL);
    }

    @Override
    public boolean teleportTo(ServerLevel level, double x, double y, double z, Set<Relative> relativeMovements, float yaw, float pitch, boolean setCamera, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) { // CraftBukkit
        if (this.isSleeping()) {
            this.stopSleepInBed(true, true);
        }

        if (setCamera) {
            this.setCamera(this);
        }

        boolean flag = super.teleportTo(level, x, y, z, relativeMovements, yaw, pitch, setCamera, cause); // CraftBukkit
        if (flag) {
            this.setYHeadRot(relativeMovements.contains(Relative.Y_ROT) ? this.getYHeadRot() + yaw : yaw);
        }

        return flag;
    }

    @Override
    public void moveTo(double x, double y, double z) {
        super.moveTo(x, y, z);
        this.connection.resetPosition();
    }

    @Override
    public void crit(Entity entityHit) {
        this.serverLevel().getChunkSource().broadcastAndSend(this, new ClientboundAnimatePacket(entityHit, 4));
    }

    @Override
    public void magicCrit(Entity entityHit) {
        this.serverLevel().getChunkSource().broadcastAndSend(this, new ClientboundAnimatePacket(entityHit, 5));
    }

    @Override
    public void onUpdateAbilities() {
        if (this.connection != null) {
            this.connection.send(new ClientboundPlayerAbilitiesPacket(this.getAbilities()));
            this.updateInvisibilityStatus();
        }
    }

    public ServerLevel serverLevel() {
        return (ServerLevel)this.level();
    }

    public boolean setGameMode(GameType gameMode) {
        // Paper start - Expand PlayerGameModeChangeEvent
        org.bukkit.event.player.PlayerGameModeChangeEvent event = this.setGameMode(gameMode, org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.UNKNOWN, null);
        return event == null ? false : event.isCancelled();
    }
    @Nullable
    public org.bukkit.event.player.PlayerGameModeChangeEvent setGameMode(GameType gameMode, org.bukkit.event.player.PlayerGameModeChangeEvent.Cause cause, @Nullable net.kyori.adventure.text.Component message) {
        boolean isSpectator = this.isSpectator();
        org.bukkit.event.player.PlayerGameModeChangeEvent event = this.gameMode.changeGameModeForPlayer(gameMode, cause, message);
        if (event == null || event.isCancelled()) {
            return null;
        // Paper end - Expand PlayerGameModeChangeEvent
        } else {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, gameMode.getId()));
            if (gameMode == GameType.SPECTATOR) {
                this.removeEntitiesOnShoulder();
                this.stopRiding();
                EnchantmentHelper.stopLocationBasedEffects(this);
            } else {
                this.setCamera(this);
                if (isSpectator) {
                    EnchantmentHelper.runLocationChangedEffects(this.serverLevel(), this);
                }
            }

            this.onUpdateAbilities();
            this.updateEffectVisibility();
            return event; // Paper - Expand PlayerGameModeChangeEvent
        }
    }

    @Override
    public boolean isSpectator() {
        return this.gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
    }

    @Override
    public boolean isCreative() {
        return this.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
    }

    public CommandSource commandSource() {
        return this.commandSource;
    }

    public CommandSourceStack createCommandSourceStack() {
        return new CommandSourceStack(
            this.commandSource(),
            this.position(),
            this.getRotationVector(),
            this.serverLevel(),
            this.getPermissionLevel(),
            this.getName().getString(),
            this.getDisplayName(),
            this.server,
            this
        );
    }

    // Purpur start - Component related conveniences
    public void sendMiniMessage(@Nullable String message) {
        if (message != null && !message.isEmpty()) {
            this.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(message));
        }
    }

    public void sendMessage(@Nullable net.kyori.adventure.text.Component message) {
        if (message != null) {
            this.sendSystemMessage(io.papermc.paper.adventure.PaperAdventure.asVanilla(message));
        }
    }
    // Purpur end - Component related conveniences

    public void sendSystemMessage(Component mesage) {
        this.sendSystemMessage(mesage, false);
    }

    public void sendSystemMessage(Component message, boolean overlay) {
        if (this.acceptsSystemMessages(overlay)) {
            this.connection
                .send(
                    new ClientboundSystemChatPacket(message, overlay),
                    PacketSendListener.exceptionallySend(
                        () -> {
                            if (this.acceptsSystemMessages(false)) {
                                int i = 256;
                                String string = message.getString(256);
                                Component component = Component.literal(string).withStyle(ChatFormatting.YELLOW);
                                return new ClientboundSystemChatPacket(
                                    Component.translatable("multiplayer.message_not_delivered", component).withStyle(ChatFormatting.RED), false
                                );
                            } else {
                                return null;
                            }
                        }
                    )
                );
        }
    }

    public void sendChatMessage(OutgoingChatMessage message, boolean filtered, ChatType.Bound boundType) {
        // Paper start
        this.sendChatMessage(message, filtered, boundType, null);
    }
    public void sendChatMessage(OutgoingChatMessage message, boolean filtered, ChatType.Bound boundType, @Nullable Component unsigned) {
        // Paper end
        if (this.acceptsChatMessages()) {
            message.sendToPlayer(this, filtered, boundType, unsigned); // Paper
        }
    }

    public String getIpAddress() {
        return this.connection.getRemoteAddress() instanceof InetSocketAddress inetSocketAddress
            ? InetAddresses.toAddrString(inetSocketAddress.getAddress())
            : "<unknown>";
    }

    public void updateOptions(ClientInformation clientInformation) {
        // Paper start - settings event
        new com.destroystokyo.paper.event.player.PlayerClientOptionsChangeEvent(this.getBukkitEntity(), Util.make(new java.util.IdentityHashMap<>(), map -> {
            map.put(com.destroystokyo.paper.ClientOption.LOCALE, clientInformation.language());
            map.put(com.destroystokyo.paper.ClientOption.VIEW_DISTANCE, clientInformation.viewDistance());
            map.put(com.destroystokyo.paper.ClientOption.CHAT_VISIBILITY, com.destroystokyo.paper.ClientOption.ChatVisibility.valueOf(clientInformation.chatVisibility().name()));
            map.put(com.destroystokyo.paper.ClientOption.CHAT_COLORS_ENABLED, clientInformation.chatColors());
            map.put(com.destroystokyo.paper.ClientOption.SKIN_PARTS, new com.destroystokyo.paper.PaperSkinParts(clientInformation.modelCustomisation()));
            map.put(com.destroystokyo.paper.ClientOption.MAIN_HAND, clientInformation.mainHand() == HumanoidArm.LEFT ? org.bukkit.inventory.MainHand.LEFT : org.bukkit.inventory.MainHand.RIGHT);
            map.put(com.destroystokyo.paper.ClientOption.TEXT_FILTERING_ENABLED, clientInformation.textFilteringEnabled());
            map.put(com.destroystokyo.paper.ClientOption.ALLOW_SERVER_LISTINGS, clientInformation.allowsListing());
            map.put(com.destroystokyo.paper.ClientOption.PARTICLE_VISIBILITY, com.destroystokyo.paper.ClientOption.ParticleVisibility.valueOf(clientInformation.particleStatus().name()));
        })).callEvent();
        // Paper end - settings event
        // CraftBukkit start
        if (this.getMainArm() != clientInformation.mainHand()) {
            org.bukkit.event.player.PlayerChangedMainHandEvent event = new org.bukkit.event.player.PlayerChangedMainHandEvent(
                this.getBukkitEntity(),
                clientInformation.mainHand() == HumanoidArm.LEFT ? org.bukkit.inventory.MainHand.LEFT : org.bukkit.inventory.MainHand.RIGHT
            );
            this.server.server.getPluginManager().callEvent(event);
        }
        if (this.language == null || !this.language.equals(clientInformation.language())) { // Paper
            org.bukkit.event.player.PlayerLocaleChangeEvent event = new org.bukkit.event.player.PlayerLocaleChangeEvent(
                this.getBukkitEntity(),
                clientInformation.language()
            );
            this.server.server.getPluginManager().callEvent(event);
        }
        // CraftBukkit end
        // Paper start - don't call options events on login
        this.updateOptionsNoEvents(clientInformation);
    }
    public void updateOptionsNoEvents(ClientInformation clientInformation) {
        // Paper end
        this.language = clientInformation.language();
        this.adventure$locale = java.util.Objects.requireNonNullElse(net.kyori.adventure.translation.Translator.parseLocale(this.language), java.util.Locale.US); // Paper
        this.requestedViewDistance = clientInformation.viewDistance();
        this.chatVisibility = clientInformation.chatVisibility();
        this.canChatColor = clientInformation.chatColors();
        this.textFilteringEnabled = clientInformation.textFilteringEnabled();
        this.allowsListing = clientInformation.allowsListing();
        this.particleStatus = clientInformation.particleStatus();
        this.getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte)clientInformation.modelCustomisation());
        this.getEntityData().set(DATA_PLAYER_MAIN_HAND, (byte)clientInformation.mainHand().getId());
    }

    public ClientInformation clientInformation() {
        int i = this.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION);
        HumanoidArm humanoidArm = HumanoidArm.BY_ID.apply(this.getEntityData().get(DATA_PLAYER_MAIN_HAND));
        return new ClientInformation(
            this.language,
            this.requestedViewDistance,
            this.chatVisibility,
            this.canChatColor,
            i,
            humanoidArm,
            this.textFilteringEnabled,
            this.allowsListing,
            this.particleStatus
        );
    }

    public boolean canChatInColor() {
        return this.canChatColor;
    }

    public ChatVisiblity getChatVisibility() {
        return this.chatVisibility;
    }

    private boolean acceptsSystemMessages(boolean overlay) {
        return this.chatVisibility != ChatVisiblity.HIDDEN || overlay;
    }

    private boolean acceptsChatMessages() {
        return this.chatVisibility == ChatVisiblity.FULL;
    }

    public int requestedViewDistance() {
        return this.requestedViewDistance;
    }

    public void sendServerStatus(ServerStatus serverStatus) {
        this.connection.send(new ClientboundServerDataPacket(serverStatus.description(), serverStatus.favicon().map(ServerStatus.Favicon::iconBytes)));
    }

    @Override
    public int getPermissionLevel() {
        return this.server.getProfilePermissions(this.getGameProfile());
    }

    public void resetLastActionTime() {
        this.lastActionTime = Util.getMillis();
        this.setAfk(false); // Purpur - AFK API
    }

    // Purpur start - AFK API
    private boolean isAfk = false;

    @Override
    public void setAfk(boolean afk) {
        if (this.isAfk == afk) {
            return;
        }

        String msg = afk ? org.purpurmc.purpur.PurpurConfig.afkBroadcastAway : org.purpurmc.purpur.PurpurConfig.afkBroadcastBack;

        org.purpurmc.purpur.event.PlayerAFKEvent event = new org.purpurmc.purpur.event.PlayerAFKEvent(this.getBukkitEntity(), afk, this.level().purpurConfig.idleTimeoutKick, msg, !org.bukkit.Bukkit.isPrimaryThread());
        if (!event.callEvent() || event.shouldKick()) {
            return;
        }

        this.isAfk = afk;

        if (!afk) {
            resetLastActionTime();
        }

        msg = event.getBroadcastMsg();
        if (msg != null && !msg.isEmpty()) {
            String playerName = this.getGameProfile().getName();
            if (org.purpurmc.purpur.PurpurConfig.afkBroadcastUseDisplayName) {
                net.kyori.adventure.text.Component playerDisplayNameComponent = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(this.getBukkitEntity().getDisplayName());
                playerName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(playerDisplayNameComponent);
            }
            server.getPlayerList().broadcastMiniMessage(String.format(msg, playerName), false);
        }

        if (this.level().purpurConfig.idleTimeoutUpdateTabList) {
            String scoreboardName = getScoreboardName();
            String playerListName = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(getBukkitEntity().playerListName());
            String[] split = playerListName.split(scoreboardName);
            String prefix = (split.length > 0 ? split[0] : "").replace(org.purpurmc.purpur.PurpurConfig.afkTabListPrefix, "");
            String suffix = (split.length > 1 ? split[1] : "").replace(org.purpurmc.purpur.PurpurConfig.afkTabListSuffix, "");
            if (afk) {
                getBukkitEntity().setPlayerListName(org.purpurmc.purpur.PurpurConfig.afkTabListPrefix + prefix + scoreboardName + suffix + org.purpurmc.purpur.PurpurConfig.afkTabListSuffix, true);
            } else {
                getBukkitEntity().setPlayerListName(prefix + scoreboardName + suffix, true);
            }
        }

        ((ServerLevel) this.level()).updateSleepingPlayerList();
    }

    @Override
    public boolean isAfk() {
        return this.isAfk;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !this.isAfk() && super.canBeCollidedWith();
    }
    // Purpur end - AFK API

    public ServerStatsCounter getStats() {
        return this.stats;
    }

    public ServerRecipeBook getRecipeBook() {
        return this.recipeBook;
    }

    @Override
    protected void updateInvisibilityStatus() {
        if (this.isSpectator()) {
            this.removeEffectParticles();
            this.setInvisible(true);
        } else {
            super.updateInvisibilityStatus();
        }
    }

    public Entity getCamera() {
        return (Entity)(this.camera == null ? this : this.camera);
    }

    public void setCamera(@Nullable Entity entityToSpectate) {
        Entity camera = this.getCamera();
        this.camera = (Entity)(entityToSpectate == null ? this : entityToSpectate);
        if (camera != this.camera) {
            // Paper start - Add PlayerStartSpectatingEntityEvent and PlayerStopSpectatingEntity
            if (this.camera == this) {
                com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent playerStopSpectatingEntityEvent = new com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent(this.getBukkitEntity(), camera.getBukkitEntity());
                if (!playerStopSpectatingEntityEvent.callEvent()) {
                    this.camera = camera; // rollback camera entity again
                    return;
                }
            } else {
                com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent playerStartSpectatingEntityEvent = new com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent(this.getBukkitEntity(), camera.getBukkitEntity(), entityToSpectate.getBukkitEntity());
                if (!playerStartSpectatingEntityEvent.callEvent()) {
                    this.camera = camera; // rollback camera entity again
                    return;
                }
            }
            // Paper end - Add PlayerStartSpectatingEntityEvent and PlayerStopSpectatingEntity
            if (this.camera.level() instanceof ServerLevel serverLevel) {
                this.teleportTo(serverLevel, this.camera.getX(), this.camera.getY(), this.camera.getZ(), Set.of(), this.getYRot(), this.getXRot(), false, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.SPECTATE); // CraftBukkit
            }

            if (entityToSpectate != null) {
                this.serverLevel().getChunkSource().move(this);
            }

            this.connection.send(new ClientboundSetCameraPacket(this.camera));
            this.connection.resetPosition();
        }
    }

    @Override
    protected void processPortalCooldown() {
        if (!this.isChangingDimension) {
            super.processPortalCooldown();
        }
    }

    @Override
    public void attack(Entity targetEntity) {
        if (this.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            this.setCamera(targetEntity);
        } else {
            super.attack(targetEntity);
        }
    }

    public long getLastActionTime() {
        return this.lastActionTime;
    }

    @Nullable
    public Component getTabListDisplayName() {
        return this.listName; // CraftBukkit
    }

    public int getTabListOrder() {
        return this.listOrder; // CraftBukkit
    }

    @Override
    public void swing(InteractionHand hand) {
        super.swing(hand);
        this.resetAttackStrengthTicker();
    }

    public boolean isChangingDimension() {
        return this.isChangingDimension;
    }

    public void hasChangedDimension() {
        this.isChangingDimension = false;
    }

    public PlayerAdvancements getAdvancements() {
        return this.advancements;
    }

    @Nullable
    public BlockPos getRespawnPosition() {
        return this.respawnPosition;
    }

    public float getRespawnAngle() {
        return this.respawnAngle;
    }

    public ResourceKey<Level> getRespawnDimension() {
        return this.respawnDimension;
    }

    public boolean isRespawnForced() {
        return this.respawnForced;
    }

    public void copyRespawnPosition(ServerPlayer player) {
        this.setRespawnPosition(player.getRespawnDimension(), player.getRespawnPosition(), player.getRespawnAngle(), player.isRespawnForced(), false);
    }

    @Deprecated // Paper - Add PlayerSetSpawnEvent
    public void setRespawnPosition(ResourceKey<Level> dimension, @Nullable BlockPos position, float angle, boolean forced, boolean sendMessage) {
        // Paper start - Add PlayerSetSpawnEvent
        this.setRespawnPosition(dimension, position, angle, forced, sendMessage, com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.UNKNOWN);
    }
    @Deprecated
    public boolean setRespawnPosition(ResourceKey<Level> dimension, @Nullable BlockPos position, float angle, boolean forced, boolean sendMessage, org.bukkit.event.player.PlayerSpawnChangeEvent.Cause cause) {
        return this.setRespawnPosition(dimension, position, angle, forced, sendMessage, cause == org.bukkit.event.player.PlayerSpawnChangeEvent.Cause.RESET ?
            com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN : com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.valueOf(cause.name()));
    }
    public boolean setRespawnPosition(ResourceKey<Level> dimension, @Nullable BlockPos position, float angle, boolean forced, boolean sendMessage, com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause cause) {
        org.bukkit.Location spawnLoc = null;
        boolean willNotify = false;
        if (position != null) {
            boolean flag = position.equals(this.respawnPosition) && dimension.equals(this.respawnDimension);
            spawnLoc = io.papermc.paper.util.MCUtil.toLocation(this.getServer().getLevel(dimension), position);
            spawnLoc.setYaw(angle);
            willNotify = sendMessage && !flag;
        }
        org.bukkit.event.player.PlayerSpawnChangeEvent dumbEvent = new org.bukkit.event.player.PlayerSpawnChangeEvent(
            this.getBukkitEntity(),
            spawnLoc,
            forced,
            cause == com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN
                ? org.bukkit.event.player.PlayerSpawnChangeEvent.Cause.RESET
                : org.bukkit.event.player.PlayerSpawnChangeEvent.Cause.valueOf(cause.name())
        );
        dumbEvent.callEvent();

        com.destroystokyo.paper.event.player.PlayerSetSpawnEvent event = new com.destroystokyo.paper.event.player.PlayerSetSpawnEvent(
            this.getBukkitEntity(),
            cause,
            dumbEvent.getNewSpawn(),
            dumbEvent.isForced(),
            willNotify,
            willNotify ? net.kyori.adventure.text.Component.translatable("block.minecraft.set_spawn") : null
        );
        event.setCancelled(dumbEvent.isCancelled());
        if (!event.callEvent()) {
            return false;
        }
        if (event.getLocation() != null) {
            dimension = event.getLocation().getWorld() != null ? ((org.bukkit.craftbukkit.CraftWorld) event.getLocation().getWorld()).getHandle().dimension() : dimension;
            position = io.papermc.paper.util.MCUtil.toBlockPosition(event.getLocation());
            angle = event.getLocation().getYaw();
            forced = event.isForced();
            // Paper end - Add PlayerSetSpawnEvent

            if (event.willNotifyPlayer() && event.getNotification() != null) { // Paper - Add PlayerSetSpawnEvent
                this.sendSystemMessage(io.papermc.paper.adventure.PaperAdventure.asVanilla(event.getNotification())); // Paper - Add PlayerSetSpawnEvent
            }

            this.respawnPosition = position;
            this.respawnDimension = dimension;
            this.respawnAngle = angle;
            this.respawnForced = forced;
        } else {
            this.respawnPosition = null;
            this.respawnDimension = Level.OVERWORLD;
            this.respawnAngle = 0.0F;
            this.respawnForced = false;
        }

        return true; // Paper - Add PlayerSetSpawnEvent
    }

    public SectionPos getLastSectionPos() {
        return this.lastSectionPos;
    }

    public void setLastSectionPos(SectionPos sectionPos) {
        this.lastSectionPos = sectionPos;
    }

    public ChunkTrackingView getChunkTrackingView() {
        return this.chunkTrackingView;
    }

    public void setChunkTrackingView(ChunkTrackingView chunkTrackingView) {
        this.chunkTrackingView = chunkTrackingView;
    }

    @Override
    public void playNotifySound(SoundEvent sound, SoundSource source, float volume, float pitch) {
        this.connection
            .send(
                new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), source, this.getX(), this.getY(), this.getZ(), volume, pitch, this.random.nextLong()
                )
            );
    }

    @Override
    public ItemEntity drop(ItemStack droppedItem, boolean dropAround, boolean traceItem, boolean callEvent, @Nullable java.util.function.Consumer<org.bukkit.entity.Item> entityOperation) { // Paper start - Extend HumanEntity#dropItem API
        ItemEntity itemEntity = this.createItemStackToDrop(droppedItem, dropAround, traceItem);
        if (itemEntity == null) {
            return null;
        } else {
            // CraftBukkit start - fire PlayerDropItemEvent
            if (entityOperation != null) entityOperation.accept((org.bukkit.entity.Item) itemEntity.getBukkitEntity());
            if (callEvent) {
                org.bukkit.entity.Player player = this.getBukkitEntity();
                org.bukkit.entity.Item drop = (org.bukkit.entity.Item) itemEntity.getBukkitEntity();

                org.bukkit.event.player.PlayerDropItemEvent event = new org.bukkit.event.player.PlayerDropItemEvent(player, drop);
                this.level().getCraftServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    org.bukkit.inventory.ItemStack cur = player.getInventory().getItemInHand();
                    if (traceItem && (cur == null || cur.getAmount() == 0)) {
                        // The complete stack was dropped
                        player.getInventory().setItemInHand(drop.getItemStack());
                    } else if (traceItem && cur.isSimilar(drop.getItemStack()) && cur.getAmount() < cur.getMaxStackSize() && drop.getItemStack().getAmount() == 1) {
                        // Only one item is dropped
                        cur.setAmount(cur.getAmount() + 1);
                        player.getInventory().setItemInHand(cur);
                    } else {
                        // Fallback
                        player.getInventory().addItem(drop.getItemStack());
                    }
                    return null;
                }
            }
            // CraftBukkit end
            this.level().addFreshEntity(itemEntity);
            ItemStack item = itemEntity.getItem();
            if (traceItem) {
                if (!item.isEmpty()) {
                    this.awardStat(Stats.ITEM_DROPPED.get(item.getItem()), item.getCount()); // Paper - Fix PlayerDropItemEvent using wrong item
                }

                this.awardStat(Stats.DROP);
            }

            // Paper start - remove player from map on drop
            if (item.getItem() == net.minecraft.world.item.Items.FILLED_MAP) {
                final MapItemSavedData mapData = MapItem.getSavedData(item, this.level());
                if (mapData != null) {
                    mapData.tickCarriedBy(this, item);
                }
            }
            // Paper end - remove player from map on drop
            return itemEntity;
        }
    }

    @Nullable
    private ItemEntity createItemStackToDrop(ItemStack droppedItem, boolean dropAround, boolean includeThrowerName) {
        if (droppedItem.isEmpty()) {
            return null;
        } else {
            double d = this.getEyeY() - 0.3F;
            // Paper start
            ItemStack tmp = droppedItem.copy();
            droppedItem.setCount(0);
            droppedItem = tmp;
            // Paper end
            ItemEntity itemEntity = new ItemEntity(this.level(), this.getX(), d, this.getZ(), droppedItem);
            itemEntity.setPickUpDelay(40);
            if (includeThrowerName) {
                itemEntity.setThrower(this);
            }

            if (dropAround) {
                float f = this.random.nextFloat() * 0.5F;
                float f1 = this.random.nextFloat() * (float) (Math.PI * 2);
                itemEntity.setDeltaMovement(-Mth.sin(f1) * f, 0.2F, Mth.cos(f1) * f);
            } else {
                float f = 0.3F;
                float f1 = Mth.sin(this.getXRot() * (float) (Math.PI / 180.0));
                float cos = Mth.cos(this.getXRot() * (float) (Math.PI / 180.0));
                float sin = Mth.sin(this.getYRot() * (float) (Math.PI / 180.0));
                float cos1 = Mth.cos(this.getYRot() * (float) (Math.PI / 180.0));
                float f2 = this.random.nextFloat() * (float) (Math.PI * 2);
                float f3 = 0.02F * this.random.nextFloat();
                itemEntity.setDeltaMovement(
                    -sin * cos * 0.3F + Math.cos(f2) * f3,
                    -f1 * 0.3F + 0.1F + (this.random.nextFloat() - this.random.nextFloat()) * 0.1F,
                    cos1 * cos * 0.3F + Math.sin(f2) * f3
                );
            }

            return itemEntity;
        }
    }

    public TextFilter getTextFilter() {
        return this.textFilter;
    }

    public void setServerLevel(ServerLevel level) {
        this.setLevel(level);
        this.gameMode.setLevel(level);
    }

    @Nullable
    private static GameType readPlayerMode(@Nullable CompoundTag tag, String key) {
        return tag != null && tag.contains(key, 99) ? GameType.byId(tag.getInt(key)) : null;
    }

    private GameType calculateGameModeForNewPlayer(@Nullable GameType gameType) {
        GameType forcedGameType = this.server.getForcedGameType();
        if (forcedGameType != null) {
            return forcedGameType;
        } else {
            return gameType != null ? gameType : this.server.getDefaultGameType();
        }
    }

    public void loadGameTypes(@Nullable CompoundTag tag) {
        // Paper start - Expand PlayerGameModeChangeEvent
        if (this.server.getForcedGameType() != null && this.server.getForcedGameType() != ServerPlayer.readPlayerMode(tag, "playerGameType")) {
            if (new org.bukkit.event.player.PlayerGameModeChangeEvent(this.getBukkitEntity(), org.bukkit.GameMode.getByValue(this.server.getDefaultGameType().getId()), org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.DEFAULT_GAMEMODE, null).callEvent()) {
                this.gameMode.setGameModeForPlayer(this.server.getForcedGameType(), GameType.DEFAULT_MODE);
            } else {
                this.gameMode.setGameModeForPlayer(ServerPlayer.readPlayerMode(tag,"playerGameType"), ServerPlayer.readPlayerMode(tag, "previousPlayerGameType"));
            }
            return;
        }
        // Paper end - Expand PlayerGameModeChangeEvent
        this.gameMode
            .setGameModeForPlayer(this.calculateGameModeForNewPlayer(readPlayerMode(tag, "playerGameType")), readPlayerMode(tag, "previousPlayerGameType"));
    }

    private void storeGameTypes(CompoundTag tag) {
        tag.putInt("playerGameType", this.gameMode.getGameModeForPlayer().getId());
        GameType previousGameModeForPlayer = this.gameMode.getPreviousGameModeForPlayer();
        if (previousGameModeForPlayer != null) {
            tag.putInt("previousPlayerGameType", previousGameModeForPlayer.getId());
        }
    }

    @Override
    public boolean isTextFilteringEnabled() {
        return this.textFilteringEnabled;
    }

    public boolean shouldFilterMessageTo(ServerPlayer player) {
        return player != this && (this.textFilteringEnabled || player.textFilteringEnabled);
    }

    @Override
    public boolean mayInteract(ServerLevel level, BlockPos pos) {
        return super.mayInteract(level, pos) && level.mayInteract(this, pos);
    }

    @Override
    protected void updateUsingItem(ItemStack usingItem) {
        CriteriaTriggers.USING_ITEM.trigger(this, usingItem);
        super.updateUsingItem(usingItem);
    }

    public boolean drop(boolean dropStack) {
        Inventory inventory = this.getInventory();
        ItemStack itemStack = inventory.removeFromSelected(dropStack);
        this.containerMenu.findSlot(inventory, inventory.selected).ifPresent(i -> this.containerMenu.setRemoteSlot(i, inventory.getSelected()));
        return this.drop(itemStack, false, true) != null;
    }

    @Override
    public void handleExtraItemsCreatedOnUse(ItemStack stack) {
        if (!this.getInventory().add(stack)) {
            this.drop(stack, false);
        }
    }

    public boolean allowsListing() {
        return this.allowsListing;
    }

    @Override
    public Optional<WardenSpawnTracker> getWardenSpawnTracker() {
        return Optional.of(this.wardenSpawnTracker);
    }

    public void setSpawnExtraParticlesOnFall(boolean spawnExtraParticlesOnFall) {
        this.spawnExtraParticlesOnFall = spawnExtraParticlesOnFall;
    }

    @Override
    public void onItemPickup(ItemEntity itemEntity) {
        super.onItemPickup(itemEntity);
        Entity owner = itemEntity.getOwner();
        if (owner != null) {
            CriteriaTriggers.THROWN_ITEM_PICKED_UP_BY_PLAYER.trigger(this, itemEntity.getItem(), owner);
        }
    }

    public void setChatSession(RemoteChatSession chatSession) {
        this.chatSession = chatSession;
    }

    @Nullable
    public RemoteChatSession getChatSession() {
        return this.chatSession != null && this.chatSession.hasExpired() ? null : this.chatSession;
    }

    @Override
    public void indicateDamage(double xDistance, double zDistance) {
        this.hurtDir = (float)(Mth.atan2(zDistance, xDistance) * 180.0F / (float)Math.PI - this.getYRot());
        this.connection.send(new ClientboundHurtAnimationPacket(this));
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        if (super.startRiding(vehicle, force)) {
            vehicle.positionRider(this);
            this.connection.teleport(new PositionMoveRotation(this.position(), Vec3.ZERO, 0.0F, 0.0F), Relative.ROTATION);
            if (vehicle instanceof LivingEntity livingEntity) {
                this.server.getPlayerList().sendActiveEffects(livingEntity, this.connection);
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void stopRiding() {
        // Paper start - Force entity dismount during teleportation
        this.stopRiding(false);
    }
    @Override
    public void stopRiding(boolean suppressCancellation) {
        // Paper end - Force entity dismount during teleportation
        Entity vehicle = this.getVehicle();
        super.stopRiding(suppressCancellation); // Paper - Force entity dismount during teleportation
        if (vehicle instanceof LivingEntity livingEntity) {
            for (MobEffectInstance mobEffectInstance : livingEntity.getActiveEffects()) {
                this.connection.send(new ClientboundRemoveMobEffectPacket(vehicle.getId(), mobEffectInstance.getEffect()));
            }
        }
    }

    public CommonPlayerSpawnInfo createCommonSpawnInfo(ServerLevel level) {
        return new CommonPlayerSpawnInfo(
            level.dimensionTypeRegistration(),
            level.dimension(),
            BiomeManager.obfuscateSeed(level.getSeed()),
            this.gameMode.getGameModeForPlayer(),
            this.gameMode.getPreviousGameModeForPlayer(),
            level.isDebug(),
            level.isFlat(),
            this.getLastDeathLocation(),
            this.getPortalCooldown(),
            level.getSeaLevel()
        );
    }

    public void setRaidOmenPosition(BlockPos raidOmenPosition) {
        this.raidOmenPosition = raidOmenPosition;
    }

    public void clearRaidOmenPosition() {
        this.raidOmenPosition = null;
    }

    @Nullable
    public BlockPos getRaidOmenPosition() {
        return this.raidOmenPosition;
    }

    @Override
    public Vec3 getKnownMovement() {
        Entity vehicle = this.getVehicle();
        return vehicle != null && vehicle.getControllingPassenger() != this ? vehicle.getKnownMovement() : this.lastKnownClientMovement;
    }

    public void setKnownMovement(Vec3 knownMovement) {
        this.lastKnownClientMovement = knownMovement;
    }

    @Override
    protected float getEnchantedDamage(Entity entity, float damage, DamageSource damageSource) {
        return EnchantmentHelper.modifyDamage(this.serverLevel(), this.getWeaponItem(), entity, damageSource, damage);
    }

    @Override
    public void onEquippedItemBroken(Item item, EquipmentSlot slot) {
        super.onEquippedItemBroken(item, slot);
        this.awardStat(Stats.ITEM_BROKEN.get(item));
    }

    public Input getLastClientInput() {
        return this.lastClientInput;
    }

    public void setLastClientInput(Input lastClientInput) {
        this.lastClientInput = lastClientInput;
    }

    public Vec3 getLastClientMoveIntent() {
        float f = this.lastClientInput.left() == this.lastClientInput.right() ? 0.0F : (this.lastClientInput.left() ? 1.0F : -1.0F);
        float f1 = this.lastClientInput.forward() == this.lastClientInput.backward() ? 0.0F : (this.lastClientInput.forward() ? 1.0F : -1.0F);
        return getInputVector(new Vec3(f, 0.0, f1), 1.0F, this.getYRot());
    }

    public void registerEnderPearl(ThrownEnderpearl enderPearl) {
        this.enderPearls.add(enderPearl);
    }

    public void deregisterEnderPearl(ThrownEnderpearl enderPearl) {
        this.enderPearls.remove(enderPearl);
    }

    public Set<ThrownEnderpearl> getEnderPearls() {
        return this.enderPearls;
    }

    public long registerAndUpdateEnderPearlTicket(ThrownEnderpearl enderPearl) {
        if (enderPearl.level() instanceof ServerLevel serverLevel) {
            ChunkPos chunkPos = enderPearl.chunkPosition();
            this.registerEnderPearl(enderPearl);
            serverLevel.resetEmptyTime();
            return placeEnderPearlTicket(serverLevel, chunkPos) - 1L;
        } else {
            return 0L;
        }
    }

    public static long placeEnderPearlTicket(ServerLevel level, ChunkPos pos) {
        if (!level.paperConfig().misc.legacyEnderPearlBehavior) level.getChunkSource().addRegionTicket(TicketType.ENDER_PEARL, pos, 2, pos); // Paper - Allow using old ender pearl behavior
        return TicketType.ENDER_PEARL.timeout();
    }

    // CraftBukkit start
    public record RespawnPosAngle(Vec3 position, float yaw, boolean isBedSpawn, boolean isAnchorSpawn) {
        public static ServerPlayer.RespawnPosAngle of(Vec3 position, BlockPos towardsPos, boolean isBedSpawn, boolean isAnchorSpawn) {
            return new ServerPlayer.RespawnPosAngle(position, calculateLookAtYaw(position, towardsPos), isBedSpawn, isAnchorSpawn);
    // CraftBukkit end
        }

        private static float calculateLookAtYaw(Vec3 position, BlockPos towardsPos) {
            Vec3 vec3 = Vec3.atBottomCenterOf(towardsPos).subtract(position).normalize();
            return (float)Mth.wrapDegrees(Mth.atan2(vec3.z, vec3.x) * 180.0F / (float)Math.PI - 90.0);
        }
    }

    // CraftBukkit start - Add per-player time and weather.
    public long timeOffset = 0;
    public boolean relativeTime = true;

    public long getPlayerTime() {
        if (this.relativeTime) {
            // Adds timeOffset to the current server time.
            return this.level().getDayTime() + this.timeOffset;
        } else {
            // Adds timeOffset to the beginning of this day.
            return this.level().getDayTime() - (this.level().getDayTime() % 24000) + this.timeOffset;
        }
    }

    public org.bukkit.WeatherType weather = null;

    public org.bukkit.WeatherType getPlayerWeather() {
        return this.weather;
    }

    public void setPlayerWeather(org.bukkit.WeatherType type, boolean plugin) {
        if (!plugin && this.weather != null) {
            return;
        }

        if (plugin) {
            this.weather = type;
        }

        if (type == org.bukkit.WeatherType.DOWNFALL) {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0));
        } else {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0));
        }
    }

    private float pluginRainPosition;
    private float pluginRainPositionPrevious;

    public void updateWeather(float oldRain, float newRain, float oldThunder, float newThunder) {
        if (this.weather == null) {
            // Vanilla
            if (oldRain != newRain) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, newRain));
            }
        } else {
            // Plugin
            if (this.pluginRainPositionPrevious != this.pluginRainPosition) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, this.pluginRainPosition));
            }
        }

        if (oldThunder != newThunder) {
            if (this.weather == org.bukkit.WeatherType.DOWNFALL || this.weather == null) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, newThunder));
            } else {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0));
            }
        }
    }

    public void tickWeather() {
        if (this.weather == null) return;

        this.pluginRainPositionPrevious = this.pluginRainPosition;
        if (this.weather == org.bukkit.WeatherType.DOWNFALL) {
            this.pluginRainPosition += 0.01;
        } else {
            this.pluginRainPosition -= 0.01;
        }

        this.pluginRainPosition = Mth.clamp(this.pluginRainPosition, 0.0F, 1.0F);
    }

    public void resetPlayerWeather() {
        this.weather = null;
        this.setPlayerWeather(this.level().getLevelData().isRaining() ? org.bukkit.WeatherType.DOWNFALL : org.bukkit.WeatherType.CLEAR, false);
    }

    @Override
    public String toString() {
        return super.toString() + "(" + this.getScoreboardName() + " at " + this.getX() + "," + this.getY() + "," + this.getZ() + ")";
    }

    // SPIGOT-1903, MC-98153
    public void forceSetPositionRotation(double x, double y, double z, float yaw, float pitch) {
        this.moveTo(x, y, z, yaw, pitch);
        this.connection.resetPosition();
    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() || (this.connection != null && this.connection.isDisconnected()); // Paper - Fix duplication bugs
    }

    @Override
    public net.minecraft.world.scores.Scoreboard getScoreboard() {
        return this.getBukkitEntity().getScoreboard().getHandle();
    }

    public void reset() {
        float exp = 0;

        if (this.keepLevel) { // CraftBukkit - SPIGOT-6687: Only use keepLevel (was pre-set with RULE_KEEPINVENTORY value in PlayerDeathEvent)
            exp = this.experienceProgress;
            this.newTotalExp = this.totalExperience;
            this.newLevel = this.experienceLevel;
        }

        this.setHealth(this.getMaxHealth());
        this.stopUsingItem(); // CraftBukkit - SPIGOT-6682: Clear active item on reset
        this.setAirSupply(this.getMaxAirSupply()); // Paper - Reset players airTicks on respawn
        this.setRemainingFireTicks(0);
        this.fallDistance = 0;
        this.foodData = new net.minecraft.world.food.FoodData();
        this.experienceLevel = this.newLevel;
        this.totalExperience = this.newTotalExp;
        this.experienceProgress = 0;
        this.deathTime = 0;
        this.setArrowCount(0, true); // CraftBukkit - ArrowBodyCountChangeEvent
        this.removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.DEATH);
        this.effectsDirty = true;
        this.containerMenu = this.inventoryMenu;
        this.lastHurtByPlayer = null;
        this.lastHurtByMob = null;
        this.combatTracker = new net.minecraft.world.damagesource.CombatTracker(this);
        this.lastSentExp = -1;
        if (this.keepLevel) { // CraftBukkit - SPIGOT-6687: Only use keepLevel (was pre-set with RULE_KEEPINVENTORY value in PlayerDeathEvent)
            this.experienceProgress = exp;
        } else {
            this.giveExperiencePoints(this.newExp);
        }
        this.keepLevel = false;
        this.setDeltaMovement(0, 0, 0); // CraftBukkit - SPIGOT-6948: Reset velocity on death
        this.skipDropExperience = false; // CraftBukkit - SPIGOT-7462: Reset experience drop skip, so that further deaths drop xp
    }

    @Override
    public org.bukkit.craftbukkit.entity.CraftPlayer getBukkitEntity() {
        return (org.bukkit.craftbukkit.entity.CraftPlayer) super.getBukkitEntity();
    }
    // CraftBukkit end

    // Purpur start - Add option to teleport to spawn if outside world border
    public void teleport(org.bukkit.Location to) {
        this.ejectPassengers();
        this.stopRiding(true);

        if (this.isSleeping()) {
            this.stopSleepInBed(true, false);
        }

        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.TELEPORT);
        }

        ServerLevel toLevel = ((org.bukkit.craftbukkit.CraftWorld) to.getWorld()).getHandle();
        if (this.level() == toLevel) {
            this.connection.teleport(to);
        } else {
            this.server.getPlayerList().respawn(this, true, RemovalReason.KILLED, org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.DEATH, to);
        }
    }
    // Purpur end - Add option to teleport to spawn if outside world border

    // Purpur start - Implement TPSBar
    public boolean tpsBar() {
        return this.tpsBar;
    }

    public void tpsBar(boolean tpsBar) {
        this.tpsBar = tpsBar;
    }
    // Purpur end - Implement TPSBar

    // Purpur start - Add compass command
    public boolean compassBar() {
        return this.compassBar;
    }

    public void compassBar(boolean compassBar) {
        this.compassBar = compassBar;
    }
    // Purpur end - Add compass command

    // Purpur start - Add rambar command
    public boolean ramBar() {
        return this.ramBar;
    }

    public void ramBar(boolean ramBar) {
        this.ramBar = ramBar;
    }
    // Purpur end - Add rambar command
}
