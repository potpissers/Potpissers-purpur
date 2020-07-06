package net.minecraft.server.network;

import com.google.common.collect.Lists;
import com.google.common.primitives.Floats;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.LastSeenMessagesValidator;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MessageSignatureCache;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.chat.SignableCommand;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.chat.SignedMessageChain;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundChatAckPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;
import net.minecraft.network.protocol.game.ServerboundDebugSampleSubscriptionPacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket;
import net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FutureChain;
import net.minecraft.util.Mth;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.StringUtil;
import net.minecraft.util.TickThrottler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.vehicle.AbstractBoat;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

// CraftBukkit start
import io.papermc.paper.adventure.ChatProcessor; // Paper
import io.papermc.paper.adventure.PaperAdventure; // Paper
import com.mojang.datafixers.util.Pair;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.CraftInput;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftItemType;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.craftbukkit.util.LazyPlayerSet;
import org.bukkit.craftbukkit.util.Waitable;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent.RespawnReason;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.SmithingInventory;
// CraftBukkit end

public class ServerGamePacketListenerImpl
    extends ServerCommonPacketListenerImpl
    implements ServerGamePacketListener,
    ServerPlayerConnection,
    TickablePacketListener {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int NO_BLOCK_UPDATES_TO_ACK = -1;
    private static final int TRACKED_MESSAGE_DISCONNECT_THRESHOLD = 4096;
    private static final int MAXIMUM_FLYING_TICKS = 80;
    private static final Component CHAT_VALIDATION_FAILED = Component.translatable("multiplayer.disconnect.chat_validation_failed");
    private static final Component INVALID_COMMAND_SIGNATURE = Component.translatable("chat.disabled.invalid_command_signature").withStyle(ChatFormatting.RED);
    private static final int MAX_COMMAND_SUGGESTIONS = 1000;
    public ServerPlayer player;
    public final PlayerChunkSender chunkSender;
    private int tickCount;
    private int ackBlockChangesUpTo = -1;
    private final TickThrottler chatSpamThrottler = new TickThrottler(20, 200);
    private final TickThrottler tabSpamThrottler = new TickThrottler(io.papermc.paper.configuration.GlobalConfiguration.get().spamLimiter.tabSpamIncrement, io.papermc.paper.configuration.GlobalConfiguration.get().spamLimiter.tabSpamLimit); // Paper - configurable tab spam limits
    private final TickThrottler dropSpamThrottler = new TickThrottler(20, 1480);
    private final TickThrottler recipeSpamPackets = new TickThrottler(io.papermc.paper.configuration.GlobalConfiguration.get().spamLimiter.recipeSpamIncrement, io.papermc.paper.configuration.GlobalConfiguration.get().spamLimiter.recipeSpamLimit);
    private double firstGoodX;
    private double firstGoodY;
    private double firstGoodZ;
    private double lastGoodX;
    private double lastGoodY;
    private double lastGoodZ;
    @Nullable
    private Entity lastVehicle;
    private double vehicleFirstGoodX;
    private double vehicleFirstGoodY;
    private double vehicleFirstGoodZ;
    private double vehicleLastGoodX;
    private double vehicleLastGoodY;
    private double vehicleLastGoodZ;
    @Nullable
    private Vec3 awaitingPositionFromClient;
    private int awaitingTeleport;
    private int awaitingTeleportTime;
    private boolean clientIsFloating;
    private int aboveGroundTickCount;
    private boolean clientVehicleIsFloating;
    private int aboveGroundVehicleTickCount;
    private int receivedMovePacketCount;
    private int knownMovePacketCount;
    private boolean receivedMovementThisTick;
    // CraftBukkit start - add fields
    private int lastTick = MinecraftServer.currentTick;
    private int allowedPlayerTicks = 1;
    private int lastDropTick = MinecraftServer.currentTick;
    private int lastBookTick  = MinecraftServer.currentTick;
    private int dropCount = 0;

    private boolean hasMoved = false;
    private double lastPosX = Double.MAX_VALUE;
    private double lastPosY = Double.MAX_VALUE;
    private double lastPosZ = Double.MAX_VALUE;
    private float lastPitch = Float.MAX_VALUE;
    private float lastYaw = Float.MAX_VALUE;
    private boolean justTeleported = false;
    // CraftBukkit end
    @Nullable
    private RemoteChatSession chatSession;
    private boolean hasLoggedExpiry = false; // Paper - Prevent causing expired keys from impacting new joins
    private SignedMessageChain.Decoder signedMessageDecoder;
    private final LastSeenMessagesValidator lastSeenMessages = new LastSeenMessagesValidator(20);
    private final MessageSignatureCache messageSignatureCache = MessageSignatureCache.createDefault();
    private final FutureChain chatMessageChain;
    private boolean waitingForSwitchToConfig;
    private static final int MAX_SIGN_LINE_LENGTH = Integer.getInteger("Paper.maxSignLength", 80); // Paper - Limit client sign length

    public ServerGamePacketListenerImpl(MinecraftServer server, Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
        super(server, connection, cookie, player); // CraftBukkit
        this.chunkSender = new PlayerChunkSender(connection.isMemoryConnection());
        this.player = player;
        player.connection = this;
        player.getTextFilter().join();
        this.signedMessageDecoder = SignedMessageChain.Decoder.unsigned(player.getUUID(), server::enforceSecureProfile);
        this.chatMessageChain = new FutureChain(server.chatExecutor); // CraftBukkit - async chat
    }

    // Purpur start - AFK API
    private final com.google.common.cache.LoadingCache<org.bukkit.craftbukkit.entity.CraftPlayer, Boolean> kickPermissionCache = com.google.common.cache.CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(1, java.util.concurrent.TimeUnit.MINUTES)
        .build(
            new com.google.common.cache.CacheLoader<>() {
                @Override
                public Boolean load(org.bukkit.craftbukkit.entity.CraftPlayer player) {
                    return player.hasPermission("purpur.bypassIdleKick");
                }
            }
        );
    // Purpur end - AFK API

    @Override
    public void tick() {
        if (this.ackBlockChangesUpTo > -1) {
            this.send(new ClientboundBlockChangedAckPacket(this.ackBlockChangesUpTo));
            this.ackBlockChangesUpTo = -1;
        }

        this.resetPosition();
        this.player.xo = this.player.getX();
        this.player.yo = this.player.getY();
        this.player.zo = this.player.getZ();
        this.player.doTick();
        this.player.absMoveTo(this.firstGoodX, this.firstGoodY, this.firstGoodZ, this.player.getYRot(), this.player.getXRot());
        this.tickCount++;
        this.knownMovePacketCount = this.receivedMovePacketCount;
        if (this.clientIsFloating && !this.player.isSleeping() && !this.player.isPassenger() && !this.player.isDeadOrDying()) {
            if (++this.aboveGroundTickCount > this.getMaximumFlyingTicks(this.player)) {
                LOGGER.warn("{} was kicked for floating too long!", this.player.getName().getString());
                this.disconnect(io.papermc.paper.configuration.GlobalConfiguration.get().messages.kick.flyingPlayer, org.bukkit.event.player.PlayerKickEvent.Cause.FLYING_PLAYER); // Paper - use configurable kick message & kick event cause
                return;
            }
        } else {
            this.clientIsFloating = false;
            this.aboveGroundTickCount = 0;
        }

        this.lastVehicle = this.player.getRootVehicle();
        if (this.lastVehicle != this.player && this.lastVehicle.getControllingPassenger() == this.player) {
            this.vehicleFirstGoodX = this.lastVehicle.getX();
            this.vehicleFirstGoodY = this.lastVehicle.getY();
            this.vehicleFirstGoodZ = this.lastVehicle.getZ();
            this.vehicleLastGoodX = this.lastVehicle.getX();
            this.vehicleLastGoodY = this.lastVehicle.getY();
            this.vehicleLastGoodZ = this.lastVehicle.getZ();
            if (this.clientVehicleIsFloating && this.lastVehicle.getControllingPassenger() == this.player) {
                if (++this.aboveGroundVehicleTickCount > this.getMaximumFlyingTicks(this.lastVehicle)) {
                    LOGGER.warn("{} was kicked for floating a vehicle too long!", this.player.getName().getString());
                    this.disconnect(io.papermc.paper.configuration.GlobalConfiguration.get().messages.kick.flyingVehicle, org.bukkit.event.player.PlayerKickEvent.Cause.FLYING_VEHICLE); // Paper - use configurable kick message & kick event cause
                    return;
                }
            } else {
                this.clientVehicleIsFloating = false;
                this.aboveGroundVehicleTickCount = 0;
            }
        } else {
            this.lastVehicle = null;
            this.clientVehicleIsFloating = false;
            this.aboveGroundVehicleTickCount = 0;
        }

        this.keepConnectionAlive();
        this.chatSpamThrottler.tick();
        this.dropSpamThrottler.tick();
        this.tabSpamThrottler.tick(); // Paper - configurable tab spam limits
        this.recipeSpamPackets.tick(); // Paper - auto recipe limit
        if (this.player.getLastActionTime() > 0L
            && this.server.getPlayerIdleTimeout() > 0
            && Util.getMillis() - this.player.getLastActionTime() > this.server.getPlayerIdleTimeout() * 1000L * 60L && !this.player.wonGame) { // Paper - Prevent AFK kick while watching end credits
            // Purpur start - AFK API
            this.player.setAfk(true);
            if (!this.player.level().purpurConfig.idleTimeoutKick || (!Boolean.parseBoolean(System.getenv("PURPUR_FORCE_IDLE_KICK")) && kickPermissionCache.getUnchecked(this.player.getBukkitEntity()))) {
                return;
            }
            // Purpur end - AFK API
            this.player.resetLastActionTime(); // CraftBukkit - SPIGOT-854
            this.disconnect(Component.translatable("multiplayer.disconnect.idling"), org.bukkit.event.player.PlayerKickEvent.Cause.IDLING); // Paper - kick event cause
        }
        // Paper start - Prevent causing expired keys from impacting new joins
        if (!this.hasLoggedExpiry && this.chatSession != null && this.chatSession.profilePublicKey().data().hasExpired()) {
            LOGGER.info("Player profile key for {} has expired!", this.player.getName().getString());
            this.hasLoggedExpiry = true;
        }
        // Paper end - Prevent causing expired keys from impacting new joins
    }

    private int getMaximumFlyingTicks(Entity entity) {
        double gravity = entity.getGravity();
        if (gravity < 1.0E-5F) {
            return Integer.MAX_VALUE;
        } else {
            double d = 0.08 / gravity;
            return Mth.ceil(80.0 * Math.max(d, 1.0));
        }
    }

    public void resetPosition() {
        this.firstGoodX = this.player.getX();
        this.firstGoodY = this.player.getY();
        this.firstGoodZ = this.player.getZ();
        this.lastGoodX = this.player.getX();
        this.lastGoodY = this.player.getY();
        this.lastGoodZ = this.player.getZ();
    }

    @Override
    public boolean isAcceptingMessages() {
        return this.connection.isConnected() && !this.waitingForSwitchToConfig;
    }

    @Override
    public boolean shouldHandleMessage(Packet<?> packet) {
        return super.shouldHandleMessage(packet)
            || this.waitingForSwitchToConfig && this.connection.isConnected() && packet instanceof ServerboundConfigurationAcknowledgedPacket;
    }

    @Override
    protected GameProfile playerProfile() {
        return this.player.getGameProfile();
    }

    private <T, R> CompletableFuture<R> filterTextPacket(T message, BiFunction<TextFilter, T, CompletableFuture<R>> processor) {
        return processor.apply(this.player.getTextFilter(), message).thenApply(result -> {
            if (!this.isAcceptingMessages()) {
                LOGGER.debug("Ignoring packet due to disconnection");
                throw new CancellationException("disconnected");
            } else {
                return (R)result;
            }
        });
    }

    private CompletableFuture<FilteredText> filterTextPacket(String text) {
        return this.filterTextPacket(text, TextFilter::processStreamMessage);
    }

    private CompletableFuture<List<FilteredText>> filterTextPacket(List<String> texts) {
        return this.filterTextPacket(texts, TextFilter::processMessageBundle);
    }

    @Override
    public void handlePlayerInput(ServerboundPlayerInputPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        // CraftBukkit start
        if (!packet.input().equals(this.player.getLastClientInput())) {
            PlayerInputEvent event = new PlayerInputEvent(this.player.getBukkitEntity(), new CraftInput(packet.input()));
            this.cserver.getPluginManager().callEvent(event);
        }
        // CraftBukkit end
        this.player.setLastClientInput(packet.input());
    }

    private static boolean containsInvalidValues(double x, double y, double z, float yRot, float xRot) {
        return Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) || !Floats.isFinite(xRot) || !Floats.isFinite(yRot);
    }

    private static double clampHorizontal(double value) {
        return Mth.clamp(value, -3.0E7, 3.0E7);
    }

    private static double clampVertical(double value) {
        return Mth.clamp(value, -2.0E7, 2.0E7);
    }

    @Override
    public void handleMoveVehicle(ServerboundMoveVehiclePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (containsInvalidValues(packet.position().x(), packet.position().y(), packet.position().z(), packet.yRot(), packet.xRot())) {
            this.disconnect(Component.translatable("multiplayer.disconnect.invalid_vehicle_movement"), org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_VEHICLE_MOVEMENT); // Paper - kick event cause
        } else if (!this.updateAwaitingTeleport() && this.player.hasClientLoaded()) {
            Entity rootVehicle = this.player.getRootVehicle();
            // Paper start - Don't allow vehicle movement from players while teleporting
            if (this.awaitingPositionFromClient != null || this.player.isImmobile() || rootVehicle.isRemoved()) {
                return;
            }
            // Paper end - Don't allow vehicle movement from players while teleporting
            if (rootVehicle != this.player && rootVehicle.getControllingPassenger() == this.player && rootVehicle == this.lastVehicle) {
                ServerLevel serverLevel = this.player.serverLevel();
                // CraftBukkit - store current player position
                double prevX = this.player.getX();
                double prevY = this.player.getY();
                double prevZ = this.player.getZ();
                float prevYaw = this.player.getYRot();
                float prevPitch = this.player.getXRot();
                // CraftBukkit end
                double x = rootVehicle.getX();
                double y = rootVehicle.getY();
                double z = rootVehicle.getZ();
                double d = clampHorizontal(packet.position().x()); final double toX = d; // Paper - OBFHELPER
                double d1 = clampVertical(packet.position().y()); final double toY = d1; // Paper - OBFHELPER
                double d2 = clampHorizontal(packet.position().z()); final double toZ = d2; // Paper - OBFHELPER
                float f = Mth.wrapDegrees(packet.yRot());
                float f1 = Mth.wrapDegrees(packet.xRot());
                double d3 = d - this.vehicleFirstGoodX;
                double d4 = d1 - this.vehicleFirstGoodY;
                double d5 = d2 - this.vehicleFirstGoodZ;
                double d6 = rootVehicle.getDeltaMovement().lengthSqr();
                double d7 = d3 * d3 + d4 * d4 + d5 * d5;
                // Paper start - fix large move vectors killing the server
                double currDeltaX = toX - x;
                double currDeltaY = toY - y;
                double currDeltaZ = toZ - z;
                d7 = Math.max(d7, (currDeltaX * currDeltaX + currDeltaY * currDeltaY + currDeltaZ * currDeltaZ) - 1);
                double otherFieldX = toX - this.vehicleLastGoodX;
                double otherFieldY = toY - this.vehicleLastGoodY;
                double otherFieldZ = toZ - this.vehicleLastGoodZ;
                d7 = Math.max(d7, (otherFieldX * otherFieldX + otherFieldY * otherFieldY + otherFieldZ * otherFieldZ) - 1);
                // Paper end - fix large move vectors killing the server

                // CraftBukkit start - handle custom speeds and skipped ticks
                this.allowedPlayerTicks += (System.currentTimeMillis() / 50) - this.lastTick;
                this.allowedPlayerTicks = Math.max(this.allowedPlayerTicks, 1);
                this.lastTick = (int) (System.currentTimeMillis() / 50);

                ++this.receivedMovePacketCount;
                int i = this.receivedMovePacketCount - this.knownMovePacketCount;
                if (i > Math.max(this.allowedPlayerTicks, 5)) {
                    ServerGamePacketListenerImpl.LOGGER.debug(this.player.getScoreboardName() + " is sending move packets too frequently (" + i + " packets since last tick)");
                    i = 1;
                }

                if (d7 > 0) {
                    this.allowedPlayerTicks -= 1;
                } else {
                    this.allowedPlayerTicks = 20;
                }
                double speed;
                if (this.player.getAbilities().flying) {
                    speed = this.player.getAbilities().flyingSpeed * 20f;
                } else {
                    speed = this.player.getAbilities().walkingSpeed * 10f;
                }
                speed *= 2f; // TODO: Get the speed of the vehicle instead of the player

                // Paper start - Prevent moving into unloaded chunks
                if (this.player.level().paperConfig().chunks.preventMovingIntoUnloadedChunks && (
                    !serverLevel.areChunksLoadedForMove(this.player.getBoundingBox().expandTowards(new Vec3(toX, toY, toZ).subtract(this.player.position()))) ||
                        !serverLevel.areChunksLoadedForMove(rootVehicle.getBoundingBox().expandTowards(new Vec3(toX, toY, toZ).subtract(rootVehicle.position())))
                )) {
                    this.connection.send(ClientboundMoveVehiclePacket.fromEntity(rootVehicle));
                    return;
                }
                // Paper end - Prevent moving into unloaded chunks
                if (d7 - d6 > Math.max(100.0, Math.pow((double) (org.spigotmc.SpigotConfig.movedTooQuicklyMultiplier * (float) i * speed), 2)) && !this.isSingleplayerOwner()) {
                    // CraftBukkit end
                    LOGGER.warn(
                        "{} (vehicle of {}) moved too quickly! {},{},{}", rootVehicle.getName().getString(), this.player.getName().getString(), d3, d4, d5
                    );
                    this.send(ClientboundMoveVehiclePacket.fromEntity(rootVehicle));
                    return;
                }

                AABB oldBox = rootVehicle.getBoundingBox(); // Paper - copy from player movement packet
                d3 = d - this.vehicleLastGoodX; // Paper - diff on change, used for checking large move vectors above
                d4 = d1 - this.vehicleLastGoodY; // Paper - diff on change, used for checking large move vectors above
                d5 = d2 - this.vehicleLastGoodZ; // Paper - diff on change, used for checking large move vectors above
                boolean flag1 = rootVehicle.verticalCollisionBelow;
                if (rootVehicle instanceof LivingEntity livingEntity && livingEntity.onClimbable()) {
                    livingEntity.resetFallDistance();
                }

                rootVehicle.move(MoverType.PLAYER, new Vec3(d3, d4, d5));
                boolean didCollide = toX != rootVehicle.getX() || toY != rootVehicle.getY() || toZ != rootVehicle.getZ(); // Paper - needed here as the difference in Y can be reset - also note: this is only a guess at whether collisions took place, floating point errors can make this true when it shouldn't be...
                double verticalDelta = d4; // Paper - Decompile fix, was named d11 previously, is now gone in the source
                d3 = d - rootVehicle.getX();
                d4 = d1 - rootVehicle.getY();
                if (d4 > -0.5 || d4 < 0.5) {
                    d4 = 0.0;
                }

                d5 = d2 - rootVehicle.getZ();
                d7 = d3 * d3 + d4 * d4 + d5 * d5;
                boolean flag2 = false;
                if (d7 > org.spigotmc.SpigotConfig.movedWronglyThreshold) { // Spigot
                    flag2 = true; // Paper - diff on change, this should be moved wrongly
                    LOGGER.warn("{} (vehicle of {}) moved wrongly! {}", rootVehicle.getName().getString(), this.player.getName().getString(), Math.sqrt(d7));
                }

                rootVehicle.absMoveTo(d, d1, d2, f, f1);
                this.player.absMoveTo(d, d1, d2, this.player.getYRot(), this.player.getXRot()); // CraftBukkit
                // Paper start - optimise out extra getCubes
                boolean teleportBack = flag2; // violating this is always a fail
                if (!teleportBack) {
                    // note: only call after setLocation, or else getBoundingBox is wrong
                    AABB newBox = rootVehicle.getBoundingBox();
                    if (didCollide || !oldBox.equals(newBox)) {
                        teleportBack = this.hasNewCollision(serverLevel, rootVehicle, oldBox, newBox);
                    } // else: no collision at all detected, why do we care?
                }
                if (teleportBack) { // Paper end - optimise out extra getCubes
                    rootVehicle.absMoveTo(x, y, z, f, f1);
                    this.player.absMoveTo(x, y, z, this.player.getYRot(), this.player.getXRot()); // CraftBukkit
                    this.send(ClientboundMoveVehiclePacket.fromEntity(rootVehicle));
                    return;
                }

                // CraftBukkit start - fire PlayerMoveEvent
                org.bukkit.entity.Player player = this.getCraftPlayer();
                if (!this.hasMoved) {
                    this.lastPosX = prevX;
                    this.lastPosY = prevY;
                    this.lastPosZ = prevZ;
                    this.lastYaw = prevYaw;
                    this.lastPitch = prevPitch;
                    this.hasMoved = true;
                }
                Location from = new Location(player.getWorld(), this.lastPosX, this.lastPosY, this.lastPosZ, this.lastYaw, this.lastPitch); // Get the Players previous Event location.
                Location to = CraftLocation.toBukkit(packet.position(), player.getWorld(), packet.yRot(), packet.xRot());

                // Prevent 40 event-calls for less than a single pixel of movement >.>
                double delta = Math.pow(this.lastPosX - to.getX(), 2) + Math.pow(this.lastPosY - to.getY(), 2) + Math.pow(this.lastPosZ - to.getZ(), 2);
                float deltaAngle = Math.abs(this.lastYaw - to.getYaw()) + Math.abs(this.lastPitch - to.getPitch());

                if ((delta > 1f / 256 || deltaAngle > 10f) && !this.player.isImmobile()) {
                    this.lastPosX = to.getX();
                    this.lastPosY = to.getY();
                    this.lastPosZ = to.getZ();
                    this.lastYaw = to.getYaw();
                    this.lastPitch = to.getPitch();

                    if (!to.getWorld().getUID().equals(from.getWorld().getUID()) || to.getBlockX() != from.getBlockX() || to.getBlockY() != from.getBlockY() || to.getBlockZ() != from.getBlockZ() || to.getYaw() != from.getYaw() || to.getPitch() != from.getPitch()) this.player.resetLastActionTime(); // Purpur - AFK API

                    Location oldTo = to.clone();
                    PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
                    this.cserver.getPluginManager().callEvent(event);

                    // If the event is cancelled we move the player back to their old location.
                    if (event.isCancelled()) {
                        this.teleport(from);
                        return;
                    }

                    // If a Plugin has changed the To destination then we teleport the Player
                    // there to avoid any 'Moved wrongly' or 'Moved too quickly' errors.
                    // We only do this if the Event was not cancelled.
                    if (!oldTo.equals(event.getTo()) && !event.isCancelled()) {
                        this.player.getBukkitEntity().teleport(event.getTo(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                        return;
                    }

                    // Check to see if the Players Location has some how changed during the call of the event.
                    // This can happen due to a plugin teleporting the player instead of using .setTo()
                    if (!from.equals(this.getCraftPlayer().getLocation()) && this.justTeleported) {
                        this.justTeleported = false;
                        return;
                    }
                }
                // CraftBukkit end

                this.player.serverLevel().getChunkSource().move(this.player);
                if (!rootVehicle.isSpectator() && rootVehicle.isAffectedByBlocks()) rootVehicle.recordMovementThroughBlocks(new Vec3(x, y, z), rootVehicle.position());
                Vec3 vec3 = new Vec3(rootVehicle.getX() - x, rootVehicle.getY() - y, rootVehicle.getZ() - z);
                this.handlePlayerKnownMovement(vec3);
                rootVehicle.setOnGroundWithMovement(packet.onGround(), vec3);
                rootVehicle.doCheckFallDamage(vec3.x, vec3.y, vec3.z, packet.onGround());
                this.player.checkMovementStatistics(vec3.x, vec3.y, vec3.z);
                this.clientVehicleIsFloating = verticalDelta >= -0.03125 // Paper - Decompile fix
                    && !flag1
                    && !this.server.isFlightAllowed()
                    && !rootVehicle.isNoGravity()
                    && this.noBlocksAround(rootVehicle);
                this.vehicleLastGoodX = rootVehicle.getX();
                this.vehicleLastGoodY = rootVehicle.getY();
                this.vehicleLastGoodZ = rootVehicle.getZ();
            }
        }
    }

    private boolean noBlocksAround(Entity entity) {
        // Paper start - stop using streams, this is already a known fixed problem in Entity#move
        AABB box = entity.getBoundingBox().inflate(0.0625).expandTowards(0.0, -0.55, 0.0);
        int minX = Mth.floor(box.minX);
        int minY = Mth.floor(box.minY);
        int minZ = Mth.floor(box.minZ);
        int maxX = Mth.floor(box.maxX);
        int maxY = Mth.floor(box.maxY);
        int maxZ = Mth.floor(box.maxZ);

        Level level = entity.level();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= maxY; ++y) {
            for (int z = minZ; z <= maxZ; ++z) {
                for (int x = minX; x <= maxX; ++x) {
                    pos.set(x, y, z);
                    BlockState blockState = level.getBlockStateIfLoaded(pos);
                    if (blockState != null && !blockState.isAir()) {
                        return false;
                    }
                }
            }
        }

        return true;
        // Paper end - stop using streams, this is already a known fixed problem in Entity#move
    }

    @Override
    public void handleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (packet.getId() == this.awaitingTeleport) {
            if (this.awaitingPositionFromClient == null) {
                ServerGamePacketListenerImpl.LOGGER.warn("Disconnected on accept teleport packet. Was not expecting position data from client at this time"); // Purpur - Add more logger output for invalid movement kicks
                this.disconnect(Component.translatable("multiplayer.disconnect.invalid_player_movement"), org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_PLAYER_MOVEMENT); // Paper - kick event cause
                return;
            }

            this.player
                .moveTo( // Paper - Fix Entity Teleportation and cancel velocity if teleported
                    this.awaitingPositionFromClient.x,
                    this.awaitingPositionFromClient.y,
                    this.awaitingPositionFromClient.z,
                    this.player.getYRot(),
                    this.player.getXRot()
                );
            this.lastGoodX = this.awaitingPositionFromClient.x;
            this.lastGoodY = this.awaitingPositionFromClient.y;
            this.lastGoodZ = this.awaitingPositionFromClient.z;
            this.player.hasChangedDimension();
            this.awaitingPositionFromClient = null;
            this.player.serverLevel().getChunkSource().move(this.player); // CraftBukkit
        }
    }

    @Override
    public void handleAcceptPlayerLoad(ServerboundPlayerLoadedPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        // Paper start - PlayerLoadedWorldEvent
        if (this.player.hasClientLoaded()) {
            return;
        }
        final io.papermc.paper.event.player.PlayerClientLoadedWorldEvent event = new io.papermc.paper.event.player.PlayerClientLoadedWorldEvent(this.player.getBukkitEntity(), false);
        event.callEvent();
        // Paper end - PlayerLoadedWorldEvent
        this.player.setClientLoaded(true);
    }

    @Override
    public void handleRecipeBookSeenRecipePacket(ServerboundRecipeBookSeenRecipePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        RecipeManager.ServerDisplayInfo recipeFromDisplay = this.server.getRecipeManager().getRecipeFromDisplay(packet.recipe());
        if (recipeFromDisplay != null) {
            this.player.getRecipeBook().removeHighlight(recipeFromDisplay.parent().id());
        }
    }

    @Override
    public void handleBundleItemSelectedPacket(ServerboundSelectBundleItemPacket packet) {
        this.player.containerMenu.setSelectedBundleItemIndex(packet.slotId(), packet.selectedItemIndex());
    }

    @Override
    public void handleRecipeBookChangeSettingsPacket(ServerboundRecipeBookChangeSettingsPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        CraftEventFactory.callRecipeBookSettingsEvent(this.player, packet.getBookType(), packet.isOpen(), packet.isFiltering()); // CraftBukkit
        this.player.getRecipeBook().setBookSetting(packet.getBookType(), packet.isOpen(), packet.isFiltering());
    }

    @Override
    public void handleSeenAdvancements(ServerboundSeenAdvancementsPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (packet.getAction() == ServerboundSeenAdvancementsPacket.Action.OPENED_TAB) {
            ResourceLocation resourceLocation = Objects.requireNonNull(packet.getTab());
            AdvancementHolder advancementHolder = this.server.getAdvancements().get(resourceLocation);
            if (advancementHolder != null) {
                this.player.getAdvancements().setSelectedTab(advancementHolder);
            }
        }
    }

    // Paper start - AsyncTabCompleteEvent
    private static final java.util.concurrent.ExecutorService TAB_COMPLETE_EXECUTOR = java.util.concurrent.Executors.newFixedThreadPool(4,
        new com.google.common.util.concurrent.ThreadFactoryBuilder().setDaemon(true).setNameFormat("Async Tab Complete Thread - #%d").setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(MinecraftServer.LOGGER)).build());
    // Paper end - AsyncTabCompleteEvent

    @Override
    public void handleCustomCommandSuggestions(ServerboundCommandSuggestionPacket packet) {
        // PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel()); // Paper - AsyncTabCompleteEvent; run this async
        // CraftBukkit start
        if (!this.tabSpamThrottler.isIncrementAndUnderThreshold() && !this.server.getPlayerList().isOp(this.player.getGameProfile()) && !this.server.isSingleplayerOwner(this.player.getGameProfile())) { // Paper - configurable tab spam limits
            this.disconnectAsync(Component.translatable("disconnect.spam"), org.bukkit.event.player.PlayerKickEvent.Cause.SPAM); // Paper - Kick event cause // Paper - add proper async disconnect
            return;
        }
        // CraftBukkit end
        // Paper start - Don't suggest if tab-complete is disabled
        if (org.spigotmc.SpigotConfig.tabComplete < 0) {
            return;
        }
        // Paper end - Don't suggest if tab-complete is disabled
        // Paper start
        final int index;
        if (packet.getCommand().length() > 64 && ((index = packet.getCommand().indexOf(' ')) == -1 || index >= 64)) {
            this.disconnectAsync(Component.translatable("disconnect.spam"), org.bukkit.event.player.PlayerKickEvent.Cause.SPAM); // Paper - add proper async disconnect
            return;
        }
        // Paper end
        // Paper start - AsyncTabCompleteEvent
        TAB_COMPLETE_EXECUTOR.execute(() -> this.handleCustomCommandSuggestions0(packet));
    }

    private void handleCustomCommandSuggestions0(final ServerboundCommandSuggestionPacket packet) {
        // Paper end - AsyncTabCompleteEvent
        StringReader stringReader = new StringReader(packet.getCommand());
        if (stringReader.canRead() && stringReader.peek() == '/') {
            stringReader.skip();
        }

        // Paper start - AsyncTabCompleteEvent
        final com.destroystokyo.paper.event.server.AsyncTabCompleteEvent event = new com.destroystokyo.paper.event.server.AsyncTabCompleteEvent(this.getCraftPlayer(), packet.getCommand(), true, null);
        event.callEvent();
        final List<com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion> completions = event.isCancelled() ? com.google.common.collect.ImmutableList.of() : event.completions();
        // If the event isn't handled, we can assume that we have no completions, and so we'll ask the server
        if (!event.isHandled()) {
            if (event.isCancelled()) {
                return;
            }

            // This needs to be on main
            this.server.scheduleOnMain(() -> this.sendServerSuggestions(packet, stringReader));
        } else if (!completions.isEmpty()) {
            final com.mojang.brigadier.suggestion.SuggestionsBuilder builder0 = new com.mojang.brigadier.suggestion.SuggestionsBuilder(packet.getCommand(), stringReader.getTotalLength());
            final com.mojang.brigadier.suggestion.SuggestionsBuilder builder = builder0.createOffset(builder0.getInput().lastIndexOf(' ') + 1);
            for (final com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion completion : completions) {
                final Integer intSuggestion = com.google.common.primitives.Ints.tryParse(completion.suggestion());
                if (intSuggestion != null) {
                    builder.suggest(intSuggestion, PaperAdventure.asVanilla(completion.tooltip()));
                } else {
                    builder.suggest(completion.suggestion(), PaperAdventure.asVanilla(completion.tooltip()));
                }
            }
            // Paper start - Brigadier API
            com.mojang.brigadier.suggestion.Suggestions suggestions = builder.buildFuture().join();
            com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent suggestEvent = new com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent(this.getCraftPlayer(), suggestions, packet.getCommand());
            suggestEvent.setCancelled(suggestions.isEmpty());
            if (suggestEvent.callEvent()) {
                this.connection.send(new ClientboundCommandSuggestionsPacket(packet.getId(), limitTo(suggestEvent.getSuggestions(), ServerGamePacketListenerImpl.MAX_COMMAND_SUGGESTIONS)));
            }
            // Paper end - Brigadier API
        }
    }
    // Paper start - brig API
    private static Suggestions limitTo(final Suggestions suggestions, final int size) {
        return suggestions.getList().size() <= size ? suggestions : new Suggestions(suggestions.getRange(), suggestions.getList().subList(0, size));
    }
    // Paper end - brig API

    private void sendServerSuggestions(final ServerboundCommandSuggestionPacket packet, final StringReader stringReader) {
        // Paper end - AsyncTabCompleteEvent
        ParseResults<CommandSourceStack> parseResults = this.server.getCommands().getDispatcher().parse(stringReader, this.player.createCommandSourceStack());
        // Paper start - Handle non-recoverable exceptions
        if (!parseResults.getExceptions().isEmpty()
            && parseResults.getExceptions().values().stream().anyMatch(e -> e instanceof io.papermc.paper.brigadier.TagParseCommandSyntaxException)) {
            this.disconnect(Component.translatable("disconnect.spam"), org.bukkit.event.player.PlayerKickEvent.Cause.SPAM);
            return;
        }
        // Paper end - Handle non-recoverable exceptions
        this.server
            .getCommands()
            .getDispatcher()
            .getCompletionSuggestions(parseResults)
            .thenAccept(
                suggestions -> {
                    // Paper start - Don't tab-complete namespaced commands if send-namespaced is false
                    if (!org.spigotmc.SpigotConfig.sendNamespaced && suggestions.getRange().getStart() <= 1) {
                        suggestions.getList().removeIf(suggestion -> suggestion.getText().contains(":"));
                    }
                    // Paper end - Don't tab-complete namespaced commands if send-namespaced is false
                    // Paper start - Brigadier API
                    com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent suggestEvent = new com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent(this.getCraftPlayer(), suggestions, packet.getCommand());
                    suggestEvent.setCancelled(suggestions.isEmpty());
                    if (suggestEvent.callEvent()) {
                        this.send(new ClientboundCommandSuggestionsPacket(packet.getId(), limitTo(suggestEvent.getSuggestions(), ServerGamePacketListenerImpl.MAX_COMMAND_SUGGESTIONS)));
                    }
                    // Paper end - Brigadier API
                }
            );
    }

    @Override
    public void handleSetCommandBlock(ServerboundSetCommandBlockPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (!this.server.isCommandBlockEnabled()) {
            this.player.sendSystemMessage(Component.translatable("advMode.notEnabled"));
        } else if (!this.player.canUseGameMasterBlocks() && (!this.player.isCreative() || !this.player.getBukkitEntity().hasPermission("minecraft.commandblock"))) { // Paper - command block permission
            this.player.sendSystemMessage(Component.translatable("advMode.notAllowed"));
        } else {
            BaseCommandBlock baseCommandBlock = null;
            CommandBlockEntity commandBlockEntity = null;
            BlockPos pos = packet.getPos();
            BlockEntity blockEntity = this.player.level().getBlockEntity(pos);
            if (blockEntity instanceof CommandBlockEntity) {
                commandBlockEntity = (CommandBlockEntity)blockEntity;
                baseCommandBlock = commandBlockEntity.getCommandBlock();
            }

            String command = packet.getCommand();
            boolean isTrackOutput = packet.isTrackOutput();
            if (baseCommandBlock != null) {
                CommandBlockEntity.Mode mode = commandBlockEntity.getMode();
                BlockState blockState = this.player.level().getBlockState(pos);
                Direction direction = blockState.getValue(CommandBlock.FACING);

                BlockState blockState1 = switch (packet.getMode()) {
                    case SEQUENCE -> Blocks.CHAIN_COMMAND_BLOCK.defaultBlockState();
                    case AUTO -> Blocks.REPEATING_COMMAND_BLOCK.defaultBlockState();
                    default -> Blocks.COMMAND_BLOCK.defaultBlockState();
                };
                BlockState blockState2 = blockState1.setValue(CommandBlock.FACING, direction)
                    .setValue(CommandBlock.CONDITIONAL, Boolean.valueOf(packet.isConditional()));
                if (blockState2 != blockState) {
                    this.player.level().setBlock(pos, blockState2, 2);
                    blockEntity.setBlockState(blockState2);
                    this.player.level().getChunkAt(pos).setBlockEntity(blockEntity);
                }

                baseCommandBlock.setCommand(command);
                baseCommandBlock.setTrackOutput(isTrackOutput);
                if (!isTrackOutput) {
                    baseCommandBlock.setLastOutput(null);
                }

                commandBlockEntity.setAutomatic(packet.isAutomatic());
                if (mode != packet.getMode()) {
                    commandBlockEntity.onModeSwitch();
                }

                baseCommandBlock.onUpdated();
                if (!StringUtil.isNullOrEmpty(command)) {
                    this.player.sendSystemMessage(Component.translatable("advMode.setCommand.success", command));
                }
            }
        }
    }

    @Override
    public void handleSetCommandMinecart(ServerboundSetCommandMinecartPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (!this.server.isCommandBlockEnabled()) {
            this.player.sendSystemMessage(Component.translatable("advMode.notEnabled"));
        } else if (!this.player.canUseGameMasterBlocks() && (!this.player.isCreative() || !this.player.getBukkitEntity().hasPermission("minecraft.commandblock"))) { // Paper - command block permission
            this.player.sendSystemMessage(Component.translatable("advMode.notAllowed"));
        } else {
            BaseCommandBlock commandBlock = packet.getCommandBlock(this.player.level());
            if (commandBlock != null) {
                commandBlock.setCommand(packet.getCommand());
                commandBlock.setTrackOutput(packet.isTrackOutput());
                if (!packet.isTrackOutput()) {
                    commandBlock.setLastOutput(null);
                }

                commandBlock.onUpdated();
                this.player.sendSystemMessage(Component.translatable("advMode.setCommand.success", packet.getCommand()));
            }
        }
    }

    @Override
    public void handlePickItemFromBlock(ServerboundPickItemFromBlockPacket packet) {
        ServerLevel serverLevel = this.player.serverLevel();
        PacketUtils.ensureRunningOnSameThread(packet, this, serverLevel);
        BlockPos blockPos = packet.pos();
        if (this.player.canInteractWithBlock(blockPos, 1.0)) {
            if (serverLevel.isLoaded(blockPos)) {
                BlockState blockState = serverLevel.getBlockState(blockPos);
                boolean flag = this.player.hasInfiniteMaterials() && packet.includeData();
                ItemStack cloneItemStack = blockState.getCloneItemStack(serverLevel, blockPos, flag);
                if (!cloneItemStack.isEmpty()) {
                    if (flag && this.player.getBukkitEntity().hasPermission("minecraft.nbt.copy")) { // Spigot
                        addBlockDataToItem(blockState, serverLevel, blockPos, cloneItemStack);
                    }

                    this.tryPickItem(cloneItemStack);
                }
            }
        }
    }

    private static void addBlockDataToItem(BlockState state, ServerLevel level, BlockPos pos, ItemStack stack) {
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        if (blockEntity != null) {
            CompoundTag compoundTag = blockEntity.saveCustomOnly(level.registryAccess());
            blockEntity.removeComponentsFromTag(compoundTag);
            BlockItem.setBlockEntityData(stack, blockEntity.getType(), compoundTag);
            stack.applyComponents(blockEntity.collectComponents());
        }
    }

    @Override
    public void handlePickItemFromEntity(ServerboundPickItemFromEntityPacket packet) {
        ServerLevel serverLevel = this.player.serverLevel();
        PacketUtils.ensureRunningOnSameThread(packet, this, serverLevel);
        Entity entity = serverLevel.getEntity(packet.id());
        if (entity != null && this.player.canInteractWithEntity(entity, 3.0)) {
            ItemStack pickResult = entity.getPickResult();
            if (pickResult != null && !pickResult.isEmpty()) {
                this.tryPickItem(pickResult);
            }
        }
    }

    private void tryPickItem(ItemStack stack) {
        if (stack.isItemEnabled(this.player.level().enabledFeatures())) {
            Inventory inventory = this.player.getInventory();
            int i = inventory.findSlotMatchingItem(stack);
            // Paper start - Add PlayerPickItemEvent
            final int sourceSlot = i;
            final int targetSlot = Inventory.isHotbarSlot(sourceSlot) ? sourceSlot : inventory.getSuitableHotbarSlot();
            final org.bukkit.entity.Player bukkitPlayer = this.player.getBukkitEntity();
            final io.papermc.paper.event.player.PlayerPickItemEvent event = new io.papermc.paper.event.player.PlayerPickItemEvent(bukkitPlayer, targetSlot, sourceSlot);
            if (!event.callEvent()) {
                return;
            }
            i = event.getSourceSlot();
            // Paper end - Add PlayerPickItemEvent
            if (i != -1) {
                if (Inventory.isHotbarSlot(i) && Inventory.isHotbarSlot(event.getTargetSlot())) { // Paper - Add PlayerPickItemEvent
                    inventory.selected = event.getTargetSlot(); // Paper - Add PlayerPickItemEvent
                } else {
                    inventory.pickSlot(i, event.getTargetSlot()); // Paper - Add PlayerPickItemEvent
                }
            } else if (this.player.hasInfiniteMaterials()) {
                inventory.addAndPickItem(stack, event.getTargetSlot()); // Paper - Add PlayerPickItemEvent
            }

            this.player.connection.send(new ClientboundSetHeldSlotPacket(inventory.selected));
            this.player.inventoryMenu.broadcastChanges();
        }
    }

    @Override
    public void handleRenameItem(ServerboundRenameItemPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.containerMenu instanceof AnvilMenu anvilMenu) {
            if (!anvilMenu.stillValid(this.player)) {
                LOGGER.debug("Player {} interacted with invalid menu {}", this.player, anvilMenu);
                return;
            }

            anvilMenu.setItemName(packet.getName());
        }
    }

    @Override
    public void handleSetBeaconPacket(ServerboundSetBeaconPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.containerMenu instanceof BeaconMenu beaconMenu) {
            if (!this.player.containerMenu.stillValid(this.player)) {
                LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
                return;
            }

            beaconMenu.updateEffects(packet.primary(), packet.secondary());
        }
    }

    @Override
    public void handleSetStructureBlock(ServerboundSetStructureBlockPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPos pos = packet.getPos();
            BlockState blockState = this.player.level().getBlockState(pos);
            if (this.player.level().getBlockEntity(pos) instanceof StructureBlockEntity structureBlockEntity) {
                structureBlockEntity.setMode(packet.getMode());
                structureBlockEntity.setStructureName(packet.getName());
                structureBlockEntity.setStructurePos(packet.getOffset());
                structureBlockEntity.setStructureSize(packet.getSize());
                structureBlockEntity.setMirror(packet.getMirror());
                structureBlockEntity.setRotation(packet.getRotation());
                structureBlockEntity.setMetaData(packet.getData());
                structureBlockEntity.setIgnoreEntities(packet.isIgnoreEntities());
                structureBlockEntity.setShowAir(packet.isShowAir());
                structureBlockEntity.setShowBoundingBox(packet.isShowBoundingBox());
                structureBlockEntity.setIntegrity(packet.getIntegrity());
                structureBlockEntity.setSeed(packet.getSeed());
                if (structureBlockEntity.hasStructureName()) {
                    String structureName = structureBlockEntity.getStructureName();
                    if (packet.getUpdateType() == StructureBlockEntity.UpdateType.SAVE_AREA) {
                        if (structureBlockEntity.saveStructure()) {
                            this.player.displayClientMessage(Component.translatable("structure_block.save_success", structureName), false);
                        } else {
                            this.player.displayClientMessage(Component.translatable("structure_block.save_failure", structureName), false);
                        }
                    } else if (packet.getUpdateType() == StructureBlockEntity.UpdateType.LOAD_AREA) {
                        if (!structureBlockEntity.isStructureLoadable()) {
                            this.player.displayClientMessage(Component.translatable("structure_block.load_not_found", structureName), false);
                        } else if (structureBlockEntity.placeStructureIfSameSize(this.player.serverLevel())) {
                            this.player.displayClientMessage(Component.translatable("structure_block.load_success", structureName), false);
                        } else {
                            this.player.displayClientMessage(Component.translatable("structure_block.load_prepare", structureName), false);
                        }
                    } else if (packet.getUpdateType() == StructureBlockEntity.UpdateType.SCAN_AREA) {
                        if (structureBlockEntity.detectSize()) {
                            this.player.displayClientMessage(Component.translatable("structure_block.size_success", structureName), false);
                        } else {
                            this.player.displayClientMessage(Component.translatable("structure_block.size_failure"), false);
                        }
                    }
                } else {
                    this.player.displayClientMessage(Component.translatable("structure_block.invalid_structure_name", packet.getName()), false);
                }

                structureBlockEntity.setChanged();
                this.player.level().sendBlockUpdated(pos, blockState, blockState, 3);
            }
        }
    }

    @Override
    public void handleSetJigsawBlock(ServerboundSetJigsawBlockPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPos pos = packet.getPos();
            BlockState blockState = this.player.level().getBlockState(pos);
            if (this.player.level().getBlockEntity(pos) instanceof JigsawBlockEntity jigsawBlockEntity) {
                jigsawBlockEntity.setName(packet.getName());
                jigsawBlockEntity.setTarget(packet.getTarget());
                jigsawBlockEntity.setPool(ResourceKey.create(Registries.TEMPLATE_POOL, packet.getPool()));
                jigsawBlockEntity.setFinalState(packet.getFinalState());
                jigsawBlockEntity.setJoint(packet.getJoint());
                jigsawBlockEntity.setPlacementPriority(packet.getPlacementPriority());
                jigsawBlockEntity.setSelectionPriority(packet.getSelectionPriority());
                jigsawBlockEntity.setChanged();
                this.player.level().sendBlockUpdated(pos, blockState, blockState, 3);
            }
        }
    }

    @Override
    public void handleJigsawGenerate(ServerboundJigsawGeneratePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPos pos = packet.getPos();
            if (this.player.level().getBlockEntity(pos) instanceof JigsawBlockEntity jigsawBlockEntity) {
                jigsawBlockEntity.generate(this.player.serverLevel(), packet.levels(), packet.keepJigsaws());
            }
        }
    }

    @Override
    public void handleSelectTrade(ServerboundSelectTradePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        int item = packet.getItem();
        if (this.player.containerMenu instanceof MerchantMenu merchantMenu) {
            // CraftBukkit start
            final org.bukkit.event.inventory.TradeSelectEvent tradeSelectEvent = CraftEventFactory.callTradeSelectEvent(this.player, item, merchantMenu);
            if (tradeSelectEvent.isCancelled()) {
                this.player.containerMenu.sendAllDataToRemote();
                return;
            }
            // CraftBukkit end
            if (!merchantMenu.stillValid(this.player)) {
                LOGGER.debug("Player {} interacted with invalid menu {}", this.player, merchantMenu);
                return;
            }

            merchantMenu.setSelectionHint(item);
            merchantMenu.tryMoveItems(item);
        }
    }

    @Override
    public void handleEditBook(ServerboundEditBookPacket packet) {
        // Paper start - Book size limits
        final io.papermc.paper.configuration.type.number.IntOr.Disabled pageMax = io.papermc.paper.configuration.GlobalConfiguration.get().itemValidation.bookSize.pageMax;
        if (!this.cserver.isPrimaryThread() && pageMax.enabled()) {
            final List<String> pageList = packet.pages();
            long byteTotal = 0;
            final int maxBookPageSize = pageMax.intValue();
            final double multiplier = Math.clamp(io.papermc.paper.configuration.GlobalConfiguration.get().itemValidation.bookSize.totalMultiplier, 0.3D, 1D);
            long byteAllowed = maxBookPageSize;
            // Purpur start - PlayerBookTooLargeEvent
            int slot = packet.slot();
            ItemStack itemstack = Inventory.isHotbarSlot(slot) || slot == Inventory.SLOT_OFFHAND ? this.player.getInventory().getItem(slot) : ItemStack.EMPTY;
            // Purpur end - PlayerBookTooLargeEvent
            for (final String page : pageList) {
                final int byteLength = page.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                byteTotal += byteLength;
                final int length = page.length();
                int multiByteCharacters = 0;
                if (byteLength != length) {
                    // Count the number of multi byte characters
                    for (final char c : page.toCharArray()) {
                        if (c > 127) {
                            multiByteCharacters++;
                        }
                    }
                }

                // Allow pages with fewer characters to consume less of the allowed byte quota
                byteAllowed += maxBookPageSize * Math.clamp((double) length / 255D, 0.1D, 1) * multiplier;

                if (multiByteCharacters > 1) {
                    // Penalize multibyte characters
                    byteAllowed -= multiByteCharacters;
                }
            }

            if (byteTotal > byteAllowed) {
                ServerGamePacketListenerImpl.LOGGER.warn("{} tried to send too large of a book. Book size: {} - Allowed: {} - Pages: {}", this.player.getScoreboardName(), byteTotal, byteAllowed, pageList.size());
                org.purpurmc.purpur.event.player.PlayerBookTooLargeEvent event = new org.purpurmc.purpur.event.player.PlayerBookTooLargeEvent(player.getBukkitEntity(), itemstack.asBukkitCopy()); if (event.shouldKickPlayer()) // Purpur - PlayerBookTooLargeEvent
                this.disconnectAsync(Component.literal("Book too large!"), org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION); // Paper - kick event cause // Paper - add proper async disconnect
                return;
            }
        }
        // Paper end - Book size limits
        // CraftBukkit start
        if (this.lastBookTick + 20 > MinecraftServer.currentTick) {
            this.disconnectAsync(Component.literal("Book edited too quickly!"), org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION); // Paper - kick event cause // Paper - add proper async disconnect
            return;
        }
        this.lastBookTick = MinecraftServer.currentTick;
        // CraftBukkit end
        int slot = packet.slot();
        if (Inventory.isHotbarSlot(slot) || slot == 40) {
            List<String> list = Lists.newArrayList();
            Optional<String> optional = packet.title();
            optional.ifPresent(list::add);
            list.addAll(packet.pages());
            // Purpur start - Allow color codes in books
            boolean hasEditPerm = getCraftPlayer().hasPermission("purpur.book.color.edit");
            boolean hasSignPerm = hasEditPerm || getCraftPlayer().hasPermission("purpur.book.color.sign");
            // Purpur end - Allow color codes in books
            Consumer<List<FilteredText>> consumer = optional.isPresent()
                ? texts -> this.signBook(texts.get(0), texts.subList(1, texts.size()), slot, hasSignPerm) // Purpur - Allow color codes in books
                : texts -> this.updateBookContents(texts, slot, hasEditPerm); // Purpur - Allow color codes in books
            this.filterTextPacket(list).thenAcceptAsync(consumer, this.server);
        }
    }

    private void updateBookContents(List<FilteredText> pages, int index) {
    // Purpur start - Allow color codes in books
        updateBookContents(pages, index, false);
    }
    private void updateBookContents(List<FilteredText> pages, int index, boolean hasPerm) {
    // Purpur end - Allow color codes in books
        // CraftBukkit start
        ItemStack handItem = this.player.getInventory().getItem(index);
        ItemStack item = handItem.copy();
        // CraftBukkit end
        if (item.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
            List<Filterable<String>> list = pages.stream().map(filteredText -> filterableFromOutgoing(filteredText).map(s -> color(s, hasPerm))).toList(); // Purpur - Allow color codes in books
            item.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(list));
            this.player.getInventory().setItem(index, CraftEventFactory.handleEditBookEvent(this.player, index, handItem, item)); // CraftBukkit // Paper - Don't ignore result (see other callsite for handleEditBookEvent)
        }
    }

    private void signBook(FilteredText title, List<FilteredText> pages, int index) {
        // Purpur start - Allow color codes in books
        signBook(title, pages, index, false);
    }
    private void signBook(FilteredText title, List<FilteredText> pages, int index, boolean hasPerm) {
        // Purpur end - Allow color codes in books
        ItemStack item = this.player.getInventory().getItem(index);
        if (item.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
            ItemStack itemStack = item.transmuteCopy(Items.WRITTEN_BOOK);
            itemStack.remove(DataComponents.WRITABLE_BOOK_CONTENT);
            List<Filterable<Component>> list = pages.stream().map((filteredText) -> this.filterableFromOutgoing(filteredText).map(s -> hexColor(s, hasPerm))).toList(); // Purpur - Allow color codes in books
            itemStack.set(
                DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(this.filterableFromOutgoing(title), this.player.getName().getString(), 0, list, true)
            );
            CraftEventFactory.handleEditBookEvent(this.player, index, item, itemStack); // CraftBukkit
            this.player.getInventory().setItem(index, item); // CraftBukkit - event factory updates the hand book
        }
    }

    private Filterable<String> filterableFromOutgoing(FilteredText filteredText) {
        return this.player.isTextFilteringEnabled() ? Filterable.passThrough(filteredText.filteredOrEmpty()) : Filterable.from(filteredText);
    }

    // Purpur start - Allow color codes in books
    private Component hexColor(String str, boolean hasPerm) {
        return hasPerm ? PaperAdventure.asVanilla(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(str)) : Component.literal(str);
    }

    private String color(String str, boolean hasPerm) {
        return hasPerm ? org.bukkit.ChatColor.color(str, false) : str;
    }
    // Purpur end - Allow color codes in books

    @Override
    public void handleEntityTagQuery(ServerboundEntityTagQueryPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.hasPermissions(2)) {
            Entity entity = this.player.level().getEntity(packet.getEntityId());
            if (entity != null) {
                CompoundTag compoundTag = entity.saveWithoutId(new CompoundTag());
                this.player.connection.send(new ClientboundTagQueryPacket(packet.getTransactionId(), compoundTag));
            }
        }
    }

    @Override
    public void handleContainerSlotStateChanged(ServerboundContainerSlotStateChangedPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (!this.player.isSpectator() && packet.containerId() == this.player.containerMenu.containerId) {
            if (this.player.containerMenu instanceof CrafterMenu crafterMenu && crafterMenu.getContainer() instanceof CrafterBlockEntity crafterBlockEntity) {
                crafterBlockEntity.setSlotState(packet.slotId(), packet.newState());
            }
        }
    }

    @Override
    public void handleBlockEntityTagQuery(ServerboundBlockEntityTagQueryPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.hasPermissions(2)) {
            BlockEntity blockEntity = this.player.level().getBlockEntity(packet.getPos());
            CompoundTag compoundTag = blockEntity != null ? blockEntity.saveWithoutMetadata(this.player.registryAccess()) : null;
            this.player.connection.send(new ClientboundTagQueryPacket(packet.getTransactionId(), compoundTag));
        }
    }

    @Override
    public void handleMovePlayer(ServerboundMovePlayerPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        // Purpur start - Add more logger output for invalid movement kicks
        boolean invalidX = Double.isNaN(packet.getX(0.0));
        boolean invalidY = Double.isNaN(packet.getY(0.0));
        boolean invalidZ = Double.isNaN(packet.getZ(0.0));
        boolean invalidYaw = !Floats.isFinite(packet.getYRot(0.0F));
        boolean invalidPitch = !Floats.isFinite(packet.getXRot(0.0F));
        if (invalidX || invalidY || invalidZ || invalidYaw || invalidPitch) {
            ServerGamePacketListenerImpl.LOGGER.warn(String.format("Disconnected on move player packet. Invalid data: x=%b, y=%b, z=%b, yaw=%b, pitch=%b", invalidX, invalidY, invalidZ, invalidYaw, invalidPitch));
        // Purpur end - Add more logger output for invalid movement kicks
            this.disconnect(Component.translatable("multiplayer.disconnect.invalid_player_movement"), org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_PLAYER_MOVEMENT); // Paper - kick event cause
        } else {
            ServerLevel serverLevel = this.player.serverLevel();
            if (!this.player.wonGame && !this.player.isImmobile()) { // CraftBukkit
                if (this.tickCount == 0) {
                    this.resetPosition();
                }

                if (!this.updateAwaitingTeleport() && this.player.hasClientLoaded()) {
                    double d = clampHorizontal(packet.getX(this.player.getX())); final double toX = d; // Paper - OBFHELPER
                    double d1 = clampVertical(packet.getY(this.player.getY())); final double toY = d1; // Paper - OBFHELPER
                    double d2 = clampHorizontal(packet.getZ(this.player.getZ())); final double toZ = d2; // Paper - OBFHELPER
                    float f = Mth.wrapDegrees(packet.getYRot(this.player.getYRot())); final float toYaw = f; // Paper - OBFHELPER
                    float f1 = Mth.wrapDegrees(packet.getXRot(this.player.getXRot())); final float toPitch = f1; // Paper - OBFHELPER
                    if (this.player.isPassenger()) {
                        this.player.absMoveTo(this.player.getX(), this.player.getY(), this.player.getZ(), f, f1);
                        this.player.serverLevel().getChunkSource().move(this.player);
                        this.allowedPlayerTicks = 20; // CraftBukkit
                    } else {
                        // CraftBukkit - Make sure the move is valid but then reset it for plugins to modify
                        double prevX = this.player.getX();
                        double prevY = this.player.getY();
                        double prevZ = this.player.getZ();
                        float prevYaw = this.player.getYRot();
                        float prevPitch = this.player.getXRot();
                        // CraftBukkit end
                        double x = this.player.getX();
                        double y = this.player.getY();
                        double z = this.player.getZ();
                        double d3 = d - this.firstGoodX;
                        double d4 = d1 - this.firstGoodY;
                        double d5 = d2 - this.firstGoodZ;
                        double d6 = this.player.getDeltaMovement().lengthSqr();
                        double d7 = d3 * d3 + d4 * d4 + d5 * d5;
                        // Paper start - fix large move vectors killing the server
                        double currDeltaX = toX - prevX;
                        double currDeltaY = toY - prevY;
                        double currDeltaZ = toZ - prevZ;
                        d7 = Math.max(d7, (currDeltaX * currDeltaX + currDeltaY * currDeltaY + currDeltaZ * currDeltaZ) - 1);
                        double otherFieldX = d - this.lastGoodX;
                        double otherFieldY = d1 - this.lastGoodY;
                        double otherFieldZ = d2 - this.lastGoodZ;
                        d7 = Math.max(d7, (otherFieldX * otherFieldX + otherFieldY * otherFieldY + otherFieldZ * otherFieldZ) - 1);
                        // Paper end - fix large move vectors killing the server
                        if (this.player.isSleeping()) {
                            if (d7 > 1.0) {
                                this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), f, f1);
                            }
                        } else {
                            boolean isFallFlying = this.player.isFallFlying();
                            if (serverLevel.tickRateManager().runsNormally()) {
                                this.receivedMovePacketCount++;
                                int i = this.receivedMovePacketCount - this.knownMovePacketCount;

                                // CraftBukkit start - handle custom speeds and skipped ticks
                                this.allowedPlayerTicks += (System.currentTimeMillis() / 50) - this.lastTick;
                                this.allowedPlayerTicks = Math.max(this.allowedPlayerTicks, 1);
                                this.lastTick = (int) (System.currentTimeMillis() / 50);

                                if (i > Math.max(this.allowedPlayerTicks, 5)) {
                                    LOGGER.debug("{} is sending move packets too frequently ({} packets since last tick)", this.player.getName().getString(), i);
                                    i = 1;
                                }

                                if (packet.hasRot || d7 > 0) {
                                    this.allowedPlayerTicks -= 1;
                                } else {
                                    this.allowedPlayerTicks = 20;
                                }
                                double speed;
                                if (this.player.getAbilities().flying) {
                                    speed = this.player.getAbilities().flyingSpeed * 20f;
                                } else {
                                    speed = this.player.getAbilities().walkingSpeed * 10f;
                                }
                                // Paper start - Prevent moving into unloaded chunks
                                if (this.player.level().paperConfig().chunks.preventMovingIntoUnloadedChunks && (this.player.getX() != toX || this.player.getZ() != toZ) && !serverLevel.areChunksLoadedForMove(this.player.getBoundingBox().expandTowards(new Vec3(toX, toY, toZ).subtract(this.player.position())))) {
                                    // Paper start - Add fail move event
                                    io.papermc.paper.event.player.PlayerFailMoveEvent event = fireFailMove(io.papermc.paper.event.player.PlayerFailMoveEvent.FailReason.MOVED_INTO_UNLOADED_CHUNK,
                                        toX, toY, toZ, toYaw, toPitch, false);
                                    if (!event.isAllowed()) {
                                        this.internalTeleport(PositionMoveRotation.of(this.player), Collections.emptySet());
                                        return;
                                    }
                                    // Paper end - Add fail move event
                                }
                                // Paper end - Prevent moving into unloaded chunks

                                if (this.shouldCheckPlayerMovement(isFallFlying)) {
                                    float f2 = isFallFlying ? 300.0F : 100.0F;
                                    if (d7 - d6 > Math.max(f2, Mth.square(org.spigotmc.SpigotConfig.movedTooQuicklyMultiplier * (float) i * speed))) {
                                        // CraftBukkit end
                                        // Paper start - Add fail move event
                                        io.papermc.paper.event.player.PlayerFailMoveEvent event = fireFailMove(io.papermc.paper.event.player.PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY,
                                            toX, toY, toZ, toYaw, toPitch, true);
                                        if (!event.isAllowed()) {
                                            if (event.getLogWarning()) {
                                                LOGGER.warn("{} moved too quickly! {},{},{}", this.player.getName().getString(), d3, d4, d5);
                                            }
                                            this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), this.player.getYRot(), this.player.getXRot());
                                            return;
                                        }
                                        // Paper end - Add fail move event
                                    }
                                }
                            }

                            AABB boundingBox = this.player.getBoundingBox(); // Paper - diff on change, should be old AABB
                            d3 = d - this.lastGoodX; // Paper - diff on change, used for checking large move vectors above
                            d4 = d1 - this.lastGoodY; // Paper - diff on change, used for checking large move vectors above
                            d5 = d2 - this.lastGoodZ; // Paper - diff on change, used for checking large move vectors above
                            boolean flag = d4 > 0.0;
                            if (this.player.onGround() && !packet.isOnGround() && flag) {
                                // Paper start - Add PlayerJumpEvent
                                org.bukkit.entity.Player player = this.getCraftPlayer();
                                Location from = new Location(player.getWorld(), this.lastPosX, this.lastPosY, this.lastPosZ, this.lastYaw, this.lastPitch); // Get the Players previous Event location.
                                Location to = player.getLocation().clone(); // Start off the To location as the Players current location.

                                // If the packet contains movement information then we update the To location with the correct XYZ.
                                if (packet.hasPos) {
                                    to.setX(packet.x);
                                    to.setY(packet.y);
                                    to.setZ(packet.z);
                                }

                                // If the packet contains look information then we update the To location with the correct Yaw & Pitch.
                                if (packet.hasRot) {
                                    to.setYaw(packet.yRot);
                                    to.setPitch(packet.xRot);
                                }

                                com.destroystokyo.paper.event.player.PlayerJumpEvent event = new com.destroystokyo.paper.event.player.PlayerJumpEvent(player, from, to);

                                if (event.callEvent()) {
                                    this.player.jumpFromGround();
                                } else {
                                    from = event.getFrom();
                                    this.internalTeleport(new PositionMoveRotation(org.bukkit.craftbukkit.util.CraftLocation.toVec3D(from), Vec3.ZERO, from.getYaw(), from.getPitch()), Collections.emptySet());
                                    return;
                                }
                                // Paper end - Add PlayerJumpEvent
                            }

                            boolean flag1 = this.player.verticalCollisionBelow;
                            this.player.move(MoverType.PLAYER, new Vec3(d3, d4, d5));
                            this.player.onGround = packet.isOnGround(); // CraftBukkit - SPIGOT-5810, SPIGOT-5835, SPIGOT-6828: reset by this.player.move
                            boolean didCollide = toX != this.player.getX() || toY != this.player.getY() || toZ != this.player.getZ(); // Paper - needed here as the difference in Y can be reset - also note: this is only a guess at whether collisions took place, floating point errors can make this true when it shouldn't be...
                            // Paper start - prevent position desync
                            if (this.awaitingPositionFromClient != null) {
                                return; // ... thanks Mojang for letting move calls teleport across dimensions.
                            }
                            // Paper end - prevent position desync
                            double verticalDelta = d4; // Paper - Decompile fix, was named d11 previously, is now gone in the source
                            d3 = d - this.player.getX();
                            d4 = d1 - this.player.getY();
                            if (d4 > -0.5 || d4 < 0.5) {
                                d4 = 0.0;
                            }

                            d5 = d2 - this.player.getZ();
                            d7 = d3 * d3 + d4 * d4 + d5 * d5;
                            boolean movedWrongly = false; // Paper - Add fail move event; rename
                            if (!this.player.isChangingDimension()
                                && d7 > org.spigotmc.SpigotConfig.movedWronglyThreshold // Spigot
                                && !this.player.isSleeping()
                                && !this.player.gameMode.isCreative()
                                && this.player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                                // Paper start - Add fail move event
                                io.papermc.paper.event.player.PlayerFailMoveEvent event = fireFailMove(io.papermc.paper.event.player.PlayerFailMoveEvent.FailReason.MOVED_WRONGLY,
                                    toX, toY, toZ, toYaw, toPitch, true);
                                if (!event.isAllowed()) {
                                    movedWrongly = true;
                                    if (event.getLogWarning())
                                    // Paper end
                                LOGGER.warn("{} moved wrongly!, ({})", this.player.getName().getString(), verticalDelta); // Purpur - AFK API
                                } // Paper
                            }

                            // Paper start - Add fail move event
                            // Paper start - optimise out extra getCubes
                            boolean teleportBack = !this.player.noPhysics && !this.player.isSleeping() && movedWrongly;
                            this.player.absMoveTo(d, d1, d2, f, f1); // prevent desync by tping to the set position, dropped for unknown reasons by mojang
                            if (!this.player.noPhysics && !this.player.isSleeping() && !teleportBack) {
                                AABB newBox = this.player.getBoundingBox();
                                if (didCollide || !boundingBox.equals(newBox)) {
                                    // note: only call after setLocation, or else getBoundingBox is wrong
                                    teleportBack = this.hasNewCollision(serverLevel, this.player, boundingBox, newBox);
                                } // else: no collision at all detected, why do we care?
                            }
                            // Paper end - optimise out extra getCubes
                            if (teleportBack) {
                                io.papermc.paper.event.player.PlayerFailMoveEvent event = fireFailMove(io.papermc.paper.event.player.PlayerFailMoveEvent.FailReason.CLIPPED_INTO_BLOCK,
                                    toX, toY, toZ, toYaw, toPitch, false);
                                if (event.isAllowed()) {
                                    teleportBack = false;
                                }
                            }
                            if (!teleportBack) {
                                // Paper end - Add fail move event
                                // CraftBukkit start - fire PlayerMoveEvent
                                // Reset to old location first
                                this.player.absMoveTo(prevX, prevY, prevZ, prevYaw, prevPitch);

                                org.bukkit.entity.Player player = this.getCraftPlayer();
                                if (!this.hasMoved) {
                                    this.lastPosX = prevX;
                                    this.lastPosY = prevY;
                                    this.lastPosZ = prevZ;
                                    this.lastYaw = prevYaw;
                                    this.lastPitch = prevPitch;
                                    this.hasMoved = true;
                                }

                                Location from = new Location(player.getWorld(), this.lastPosX, this.lastPosY, this.lastPosZ, this.lastYaw, this.lastPitch); // Get the Players previous Event location.
                                Location to = player.getLocation().clone(); // Start off the To location as the Players current location.

                                // If the packet contains movement information then we update the To location with the correct XYZ.
                                if (packet.hasPos) {
                                    to.setX(packet.x);
                                    to.setY(packet.y);
                                    to.setZ(packet.z);
                                }

                                // If the packet contains look information then we update the To location with the correct Yaw & Pitch.
                                if (packet.hasRot) {
                                    to.setYaw(packet.yRot);
                                    to.setPitch(packet.xRot);
                                }

                                // Prevent 40 event-calls for less than a single pixel of movement >.>
                                double delta = Math.pow(this.lastPosX - to.getX(), 2) + Math.pow(this.lastPosY - to.getY(), 2) + Math.pow(this.lastPosZ - to.getZ(), 2);
                                float deltaAngle = Math.abs(this.lastYaw - to.getYaw()) + Math.abs(this.lastPitch - to.getPitch());

                                if ((delta > 1f / 256 || deltaAngle > 10f) && !this.player.isImmobile()) {
                                    this.lastPosX = to.getX();
                                    this.lastPosY = to.getY();
                                    this.lastPosZ = to.getZ();
                                    this.lastYaw = to.getYaw();
                                    this.lastPitch = to.getPitch();

                                    if (!to.getWorld().getUID().equals(from.getWorld().getUID()) || to.getBlockX() != from.getBlockX() || to.getBlockY() != from.getBlockY() || to.getBlockZ() != from.getBlockZ() || to.getYaw() != from.getYaw() || to.getPitch() != from.getPitch()) this.player.resetLastActionTime(); // Purpur - AFK API

                                    Location oldTo = to.clone();
                                    PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
                                    this.cserver.getPluginManager().callEvent(event);

                                    // If the event is cancelled we move the player back to their old location.
                                    if (event.isCancelled()) {
                                        this.teleport(from);
                                        return;
                                    }

                                    // If a Plugin has changed the To destination then we teleport the Player
                                    // there to avoid any 'Moved wrongly' or 'Moved too quickly' errors.
                                    // We only do this if the Event was not cancelled.
                                    if (!oldTo.equals(event.getTo()) && !event.isCancelled()) {
                                        this.player.getBukkitEntity().teleport(event.getTo(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                                        return;
                                    }

                                    // Check to see if the Players Location has some how changed during the call of the event.
                                    // This can happen due to a plugin teleporting the player instead of using .setTo()
                                    if (!from.equals(this.getCraftPlayer().getLocation()) && this.justTeleported) {
                                        this.justTeleported = false;
                                        return;
                                    }
                                }
                                // CraftBukkit end
                                this.player.absMoveTo(d, d1, d2, f, f1);
                                boolean isAutoSpinAttack = this.player.isAutoSpinAttack();
                                this.clientIsFloating = verticalDelta >= -0.03125 // Paper - Decompile fix
                                    && !flag1
                                    && this.player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                                    && !this.server.isFlightAllowed()
                                    && !this.player.getAbilities().mayfly
                                    && !this.player.hasEffect(MobEffects.LEVITATION)
                                    && !isFallFlying
                                    && !isAutoSpinAttack
                                    && this.noBlocksAround(this.player);
                                this.player.serverLevel().getChunkSource().move(this.player);
                                Vec3 vec3 = new Vec3(this.player.getX() - x, this.player.getY() - y, this.player.getZ() - z);
                                this.player.setOnGroundWithMovement(packet.isOnGround(), packet.horizontalCollision(), vec3);
                                this.player.doCheckFallDamage(vec3.x, vec3.y, vec3.z, packet.isOnGround());
                                if (!this.player.isSpectator() && this.player.isAffectedByBlocks()) this.player.recordMovementThroughBlocks(new Vec3(x, y, z), this.player.position());
                                this.handlePlayerKnownMovement(vec3);
                                if (flag) {
                                    this.player.resetFallDistance();
                                }

                                if (packet.isOnGround()
                                    || this.player.hasLandedInLiquid()
                                    || this.player.onClimbable()
                                    || this.player.isSpectator()
                                    || isFallFlying
                                    || isAutoSpinAttack) {
                                    this.player.tryResetCurrentImpulseContext();
                                }

                                // Purpur start - Dont run with scissors!
                                if (this.player.serverLevel().purpurConfig.dontRunWithScissors && this.player.isSprinting() && !(this.player.serverLevel().purpurConfig.ignoreScissorsInWater && this.player.isInWater()) && !(this.player.serverLevel().purpurConfig.ignoreScissorsInLava && this.player.isInLava()) && (isScissors(this.player.getItemInHand(InteractionHand.MAIN_HAND)) || isScissors(this.player.getItemInHand(InteractionHand.OFF_HAND))) && (int) (Math.random() * 10) == 0) {
                                    this.player.hurtServer(this.player.serverLevel(), this.player.damageSources().scissors(), (float) this.player.serverLevel().purpurConfig.scissorsRunningDamage);
                                    if (!org.purpurmc.purpur.PurpurConfig.dontRunWithScissors.isBlank()) this.player.sendActionBarMessage(org.purpurmc.purpur.PurpurConfig.dontRunWithScissors);
                                }
                                // Purpur end - Dont run with scissors!

                                this.player.checkMovementStatistics(this.player.getX() - x, this.player.getY() - y, this.player.getZ() - z);
                                this.lastGoodX = this.player.getX();
                                this.lastGoodY = this.player.getY();
                                this.lastGoodZ = this.player.getZ();
                            } else {
                                this.internalTeleport(x, y, z, f, f1); // CraftBukkit - SPIGOT-1807: Don't call teleport event, when the client thinks the player is falling, because the chunks are not loaded on the client yet.
                                this.player.doCheckFallDamage(this.player.getX() - x, this.player.getY() - y, this.player.getZ() - z, packet.isOnGround());
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean shouldCheckPlayerMovement(boolean isElytraMovement) {
        if (this.isSingleplayerOwner()) {
            return false;
        } else if (this.player.isChangingDimension()) {
            return false;
        } else {
            GameRules gameRules = this.player.serverLevel().getGameRules();
            return !gameRules.getBoolean(GameRules.RULE_DISABLE_PLAYER_MOVEMENT_CHECK)
                && (!isElytraMovement || !gameRules.getBoolean(GameRules.RULE_DISABLE_ELYTRA_MOVEMENT_CHECK));
        }
    }

    private boolean updateAwaitingTeleport() {
        if (this.awaitingPositionFromClient != null) {
            if (false && this.tickCount - this.awaitingTeleportTime > 20) { // Paper - this will greatly screw with clients with > 1000ms RTT
                this.awaitingTeleportTime = this.tickCount;
                this.teleport(
                    this.awaitingPositionFromClient.x,
                    this.awaitingPositionFromClient.y,
                    this.awaitingPositionFromClient.z,
                    this.player.getYRot(),
                    this.player.getXRot()
                );
            }
            this.allowedPlayerTicks = 20; // CraftBukkit

            return true;
        } else {
            this.awaitingTeleportTime = this.tickCount;
            return false;
        }
    }

    // Purpur start - Dont run with scissors!
    public boolean isScissors(ItemStack stack) {
        if (!stack.is(Items.SHEARS)) return false;

        ResourceLocation itemModelReference = stack.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
        if (itemModelReference != null && itemModelReference.equals(this.player.serverLevel().purpurConfig.dontRunWithScissorsItemModelReference)) return true;

        return stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, net.minecraft.world.item.component.CustomModelData.EMPTY).equals(net.minecraft.world.item.component.CustomModelData.EMPTY);
    }
    // Purpur end - Dont run with scissors!

    // Paper start - optimise out extra getCubes
    private boolean hasNewCollision(final ServerLevel level, final Entity entity, final AABB oldBox, final AABB newBox) {
        final List<AABB> collisionsBB = new java.util.ArrayList<>();
        final List<VoxelShape> collisionsVoxel = new java.util.ArrayList<>();
        ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.getCollisions(
            level, entity, newBox, collisionsVoxel, collisionsBB,
            ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_FLAG_COLLIDE_WITH_UNLOADED_CHUNKS | ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_FLAG_CHECK_BORDER,
            null, null
        );

        for (int i = 0, len = collisionsBB.size(); i < len; ++i) {
            final AABB box = collisionsBB.get(i);
            if (!ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.voxelShapeIntersect(box, oldBox)) {
                return true;
            }
        }

        for (int i = 0, len = collisionsVoxel.size(); i < len; ++i) {
            final VoxelShape voxel = collisionsVoxel.get(i);
            if (!ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.voxelShapeIntersectNoEmpty(voxel, oldBox)) {
                return true;
            }
        }

        return false;
    }
    // Paper end - optimise out extra getCubes
    private boolean isPlayerCollidingWithAnythingNew(LevelReader level, AABB box, double x, double y, double z) {
        AABB aabb = this.player.getBoundingBox().move(x - this.player.getX(), y - this.player.getY(), z - this.player.getZ());
        Iterable<VoxelShape> collisions = level.getCollisions(this.player, aabb.deflate(1.0E-5F));
        VoxelShape voxelShape = Shapes.create(box.deflate(1.0E-5F));

        for (VoxelShape voxelShape1 : collisions) {
            if (!Shapes.joinIsNotEmpty(voxelShape1, voxelShape, BooleanOp.AND)) {
                return true;
            }
        }

        return false;
    }

    public void teleport(double x, double y, double z, float yaw, float pitch) {
        // CraftBukkit start - Delegate to teleport(Location)
        this.teleport(x, y, z, yaw, pitch, PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }

    public boolean teleport(double d0, double d1, double d2, float f, float f1, PlayerTeleportEvent.TeleportCause cause) {
        return this.teleport(new PositionMoveRotation(new Vec3(d0, d1, d2), Vec3.ZERO, f, f1), Collections.emptySet(), cause);
        // CraftBukkit end
    }

    public void teleport(PositionMoveRotation posMoveRotation, Set<Relative> relatives) {
        // CraftBukkit start
        this.teleport(posMoveRotation, relatives, PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }

    public boolean teleport(PositionMoveRotation posMoveRotation, Set<Relative> relatives, PlayerTeleportEvent.TeleportCause cause) { // CraftBukkit - Return event status
        org.bukkit.entity.Player player = this.getCraftPlayer();
        Location from = player.getLocation();
        PositionMoveRotation absolutePosition = PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(this.player), posMoveRotation, relatives);
        Location to = CraftLocation.toBukkit(absolutePosition.position(), this.getCraftPlayer().getWorld(), absolutePosition.yRot(), absolutePosition.xRot());
        // SPIGOT-5171: Triggered on join
        if (from.equals(to)) {
            this.internalTeleport(posMoveRotation, relatives);
            return true; // CraftBukkit - Return event status
        }

        // Paper start - Teleport API
        final Set<io.papermc.paper.entity.TeleportFlag.Relative> relativeFlags = java.util.EnumSet.noneOf(io.papermc.paper.entity.TeleportFlag.Relative.class);
        for (final Relative relativeArgument : relatives) {
            final io.papermc.paper.entity.TeleportFlag.Relative flag = org.bukkit.craftbukkit.entity.CraftPlayer.deltaRelativeToAPI(relativeArgument);
            if (flag != null) relativeFlags.add(flag);
        }
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, from.clone(), to.clone(), cause, java.util.Set.copyOf(relativeFlags));
        // Paper end - Teleport API
        this.cserver.getPluginManager().callEvent(event);

        if (event.isCancelled() || !to.equals(event.getTo())) {
            // set = Collections.emptySet(); // Can't relative teleport // Paper - Teleport API; Now you can!
            to = event.isCancelled() ? event.getFrom() : event.getTo();
            posMoveRotation = new PositionMoveRotation(CraftLocation.toVec3D(to), Vec3.ZERO, to.getYaw(), to.getPitch());
        }

        this.internalTeleport(posMoveRotation, relatives);
        return !event.isCancelled(); // CraftBukkit - Return event status
    }

    public void teleport(Location dest) {
        this.internalTeleport(dest.getX(), dest.getY(), dest.getZ(), dest.getYaw(), dest.getPitch());
    }

    private void internalTeleport(double d0, double d1, double d2, float f, float f1) {
        this.internalTeleport(new PositionMoveRotation(new Vec3(d0, d1, d2), Vec3.ZERO, f, f1), Collections.emptySet());
    }

    public void internalTeleport(PositionMoveRotation posMoveRotation, Set<Relative> relatives) {
        org.spigotmc.AsyncCatcher.catchOp("teleport"); // Paper
        // Paper start - Prevent teleporting dead entities
        if (this.player.isRemoved()) {
            LOGGER.info("Attempt to teleport removed player {} restricted", player.getScoreboardName());
            if (this.server.isDebugging()) io.papermc.paper.util.TraceUtil.dumpTraceForThread("Attempt to teleport removed player");
            return;
        }
        // Paper end - Prevent teleporting dead entities
        if (Float.isNaN(posMoveRotation.yRot())) {
            posMoveRotation = new PositionMoveRotation(posMoveRotation.position(), posMoveRotation.deltaMovement(), 0, posMoveRotation.xRot());
        }
        if (Float.isNaN(posMoveRotation.xRot())) {
            posMoveRotation = new PositionMoveRotation(posMoveRotation.position(), posMoveRotation.deltaMovement(), posMoveRotation.yRot(), 0);
        }

        this.justTeleported = true;
        // CraftBukkit end
        this.awaitingTeleportTime = this.tickCount;
        if (++this.awaitingTeleport == Integer.MAX_VALUE) {
            this.awaitingTeleport = 0;
        }

        this.player.teleportSetPosition(posMoveRotation, relatives);
        this.awaitingPositionFromClient = this.player.position();
        // CraftBukkit start - update last location
        this.lastPosX = this.awaitingPositionFromClient.x;
        this.lastPosY = this.awaitingPositionFromClient.y;
        this.lastPosZ = this.awaitingPositionFromClient.z;
        this.lastYaw = this.player.getYRot();
        this.lastPitch = this.player.getXRot();
        // CraftBukkit end
        this.player.connection.send(ClientboundPlayerPositionPacket.of(this.awaitingTeleport, posMoveRotation, relatives));
    }

    @Override
    public void handlePlayerAction(ServerboundPlayerActionPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (this.player.hasClientLoaded()) {
            BlockPos pos = packet.getPos();
            this.player.resetLastActionTime();
            ServerboundPlayerActionPacket.Action action = packet.getAction();
            switch (action) {
                case SWAP_ITEM_WITH_OFFHAND:
                    if (!this.player.isSpectator()) {
                        ItemStack itemInHand = this.player.getItemInHand(InteractionHand.OFF_HAND);
                        // CraftBukkit start - inspiration taken from DispenserRegistry (See SpigotCraft#394)
                        CraftItemStack mainHand = CraftItemStack.asCraftMirror(itemInHand);
                        CraftItemStack offHand = CraftItemStack.asCraftMirror(this.player.getItemInHand(InteractionHand.MAIN_HAND));
                        PlayerSwapHandItemsEvent swapItemsEvent = new PlayerSwapHandItemsEvent(this.getCraftPlayer(), mainHand.clone(), offHand.clone());
                        this.cserver.getPluginManager().callEvent(swapItemsEvent);
                        if (swapItemsEvent.isCancelled()) {
                            return;
                        }
                        if (swapItemsEvent.getOffHandItem().equals(offHand)) {
                            this.player.setItemInHand(InteractionHand.OFF_HAND, this.player.getItemInHand(InteractionHand.MAIN_HAND));
                        } else {
                            this.player.setItemInHand(InteractionHand.OFF_HAND, CraftItemStack.asNMSCopy(swapItemsEvent.getOffHandItem()));
                        }
                        if (swapItemsEvent.getMainHandItem().equals(mainHand)) {
                            this.player.setItemInHand(InteractionHand.MAIN_HAND, itemInHand);
                        } else {
                            this.player.setItemInHand(InteractionHand.MAIN_HAND, CraftItemStack.asNMSCopy(swapItemsEvent.getMainHandItem()));
                        }
                        // CraftBukkit end
                        this.player.stopUsingItem();
                    }

                    return;
                case DROP_ITEM:
                    if (!this.player.isSpectator()) {
                        // limit how quickly items can be dropped
                        // If the ticks aren't the same then the count starts from 0 and we update the lastDropTick.
                        if (this.lastDropTick != MinecraftServer.currentTick) {
                            this.dropCount = 0;
                            this.lastDropTick = MinecraftServer.currentTick;
                        } else {
                            // Else we increment the drop count and check the amount.
                            this.dropCount++;
                            if (this.dropCount >= 20) {
                                ServerGamePacketListenerImpl.LOGGER.warn(this.player.getScoreboardName() + " dropped their items too quickly!");
                                this.disconnect(Component.literal("You dropped your items too quickly (Hacking?)"), org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION); // Paper - kick event cause
                                return;
                            }
                        }
                        // CraftBukkit end
                        this.player.drop(false);
                    }

                    return;
                case DROP_ALL_ITEMS:
                    if (!this.player.isSpectator()) {
                        this.player.drop(true);
                    }

                    return;
                case RELEASE_USE_ITEM:
                    this.player.releaseUsingItem();
                    return;
                case START_DESTROY_BLOCK:
                case ABORT_DESTROY_BLOCK:
                case STOP_DESTROY_BLOCK:
                    // Paper start - Don't allow digging into unloaded chunks
                    if (this.player.level().getChunkIfLoadedImmediately(pos.getX() >> 4, pos.getZ() >> 4) == null) {
                        this.player.connection.ackBlockChangesUpTo(packet.getSequence());
                        return;
                    }
                    // Paper end - Don't allow digging into unloaded chunks
                    // Paper start - Send block entities after destroy prediction
                    this.player.gameMode.capturedBlockEntity = false;
                    this.player.gameMode.captureSentBlockEntities = true;
                    // Paper end - Send block entities after destroy prediction
                    this.player.gameMode.handleBlockBreakAction(pos, action, packet.getDirection(), this.player.level().getMaxY(), packet.getSequence());
                    this.player.connection.ackBlockChangesUpTo(packet.getSequence());
                    // Paper start - Send block entities after destroy prediction
                    this.player.gameMode.captureSentBlockEntities = false;
                    // If a block entity was modified speedup the block change ack to avoid the block entity
                    // being overridden.
                    if (this.player.gameMode.capturedBlockEntity) {
                        // manually tick
                        this.send(new ClientboundBlockChangedAckPacket(this.ackBlockChangesUpTo));
                        this.player.connection.ackBlockChangesUpTo = -1;

                        this.player.gameMode.capturedBlockEntity = false;
                        BlockEntity blockEntity = this.player.level().getBlockEntity(pos);
                        if (blockEntity != null) {
                            this.player.connection.send(blockEntity.getUpdatePacket());
                        }
                    }
                    // Paper end - Send block entities after destroy prediction
                    return;
                default:
                    throw new IllegalArgumentException("Invalid player action");
            }
        }
    }

    private static boolean wasBlockPlacementAttempt(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        } else {
            Item item = stack.getItem();
            return (item instanceof BlockItem || item instanceof BucketItem) && !player.getCooldowns().isOnCooldown(stack);
        }
    }

    // Spigot start - limit place/interactions
    private int limitedPackets;
    private long lastLimitedPacket = -1;
    private static int getSpamThreshold() { return io.papermc.paper.configuration.GlobalConfiguration.get().spamLimiter.incomingPacketThreshold; } // Paper - Configurable threshold

    private boolean checkLimit(long timestamp) {
        if (this.lastLimitedPacket != -1 && timestamp - this.lastLimitedPacket < getSpamThreshold() && this.limitedPackets++ >= 8) { // Paper - Configurable threshold; raise packet limit to 8
            return false;
        }

        if (this.lastLimitedPacket == -1 || timestamp - this.lastLimitedPacket >= getSpamThreshold()) { // Paper - Configurable threshold
            this.lastLimitedPacket = timestamp;
            this.limitedPackets = 0;
            return true;
        }

        return true;
    }
    // Spigot end - limit place/interactions

    @Override
    public void handleUseItemOn(ServerboundUseItemOnPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (!this.checkLimit(packet.timestamp)) return; // Spigot - check limit
        if (this.player.hasClientLoaded()) {
            this.player.connection.ackBlockChangesUpTo(packet.getSequence());
            ServerLevel serverLevel = this.player.serverLevel();
            InteractionHand hand = packet.getHand();
            ItemStack itemInHand = this.player.getItemInHand(hand);
            if (itemInHand.isItemEnabled(serverLevel.enabledFeatures())) {
                BlockHitResult hitResult = packet.getHitResult();
                Vec3 location = hitResult.getLocation();
                // Paper start - improve distance check
                if (!Double.isFinite(location.x()) || !Double.isFinite(location.y()) || !Double.isFinite(location.z())) {
                    return;
                }
                // Paper end - improve distance check
                BlockPos blockPos = hitResult.getBlockPos();
                if (this.player.canInteractWithBlock(blockPos, 1.0)) {
                    Vec3 vec3 = location.subtract(Vec3.atCenterOf(blockPos));
                    double d = 1.0000001;
                    if (Math.abs(vec3.x()) < 1.0000001 && Math.abs(vec3.y()) < 1.0000001 && Math.abs(vec3.z()) < 1.0000001) {
                        Direction direction = hitResult.getDirection();
                        this.player.resetLastActionTime();
                        int maxY = this.player.level().getMaxY();
                        if (blockPos.getY() <= maxY) {
                            if (this.awaitingPositionFromClient == null && (serverLevel.mayInteract(this.player, blockPos) || (serverLevel.paperConfig().spawn.allowUsingSignsInsideSpawnProtection && serverLevel.getBlockState(blockPos).getBlock() instanceof net.minecraft.world.level.block.SignBlock))) { // Paper - Allow using signs inside spawn protection
                                this.player.stopUsingItem(); // CraftBukkit - SPIGOT-4706
                                InteractionResult interactionResult = this.player.gameMode.useItemOn(this.player, serverLevel, itemInHand, hand, hitResult);
                                if (interactionResult.consumesAction()) {
                                    CriteriaTriggers.ANY_BLOCK_USE.trigger(this.player, hitResult.getBlockPos(), itemInHand.copy());
                                }

                                if (direction == Direction.UP
                                    && !interactionResult.consumesAction()
                                    && blockPos.getY() >= maxY
                                    && wasBlockPlacementAttempt(this.player, itemInHand)) {
                                    Component component = Component.translatable("build.tooHigh", maxY).withStyle(ChatFormatting.RED);
                                    this.player.sendSystemMessage(component, true);
                                } else if (interactionResult instanceof InteractionResult.Success success
                                    && success.swingSource() == InteractionResult.SwingSource.SERVER && !this.player.gameMode.interactResult) { // Paper - Call interact event
                                    this.player.swing(hand, true);
                                }
                            } else { this.player.containerMenu.sendAllDataToRemote(); } // Paper - Fix inventory desync; MC-99075
                        } else {
                            Component component1 = Component.translatable("build.tooHigh", maxY).withStyle(ChatFormatting.RED);
                            this.player.sendSystemMessage(component1, true);
                        }

                        this.player.connection.send(new ClientboundBlockUpdatePacket(serverLevel, blockPos));
                        this.player.connection.send(new ClientboundBlockUpdatePacket(serverLevel, blockPos.relative(direction)));
                    } else {
                        LOGGER.warn(
                            "Rejecting UseItemOnPacket from {}: Location {} too far away from hit block {}.",
                            this.player.getGameProfile().getName(),
                            location,
                            blockPos
                        );
                    }
                }
            }
        }
    }

    @Override
    public void handleUseItem(ServerboundUseItemPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (!this.checkLimit(packet.timestamp)) return; // Spigot - check limit
        if (this.player.hasClientLoaded()) {
            this.ackBlockChangesUpTo(packet.getSequence());
            ServerLevel serverLevel = this.player.serverLevel();
            InteractionHand hand = packet.getHand();
            ItemStack itemInHand = this.player.getItemInHand(hand);
            this.player.resetLastActionTime();
            if (!itemInHand.isEmpty() && itemInHand.isItemEnabled(serverLevel.enabledFeatures())) {
                float f = Mth.wrapDegrees(packet.getYRot());
                float f1 = Mth.wrapDegrees(packet.getXRot());
                if (f1 != this.player.getXRot() || f != this.player.getYRot()) {
                    this.player.absRotateTo(f, f1);
                }

                // CraftBukkit start
                // Raytrace to look for 'rogue armswings'
                double x = this.player.getX();
                double eyeY = this.player.getEyeY();
                double z = this.player.getZ();
                Vec3 from = new Vec3(x, eyeY, z);

                float f3 = Mth.cos(-f * 0.017453292F - 3.1415927F);
                float f4 = Mth.sin(-f * 0.017453292F - 3.1415927F);
                float f5 = -Mth.cos(-f1 * 0.017453292F);
                float f6 = Mth.sin(-f1 * 0.017453292F);
                float f7 = f4 * f5;
                float f8 = f3 * f5;
                double d3 = this.player.blockInteractionRange();
                Vec3 to = from.add((double) f7 * d3, (double) f6 * d3, (double) f8 * d3);
                BlockHitResult hitResult = this.player.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this.player));

                boolean cancelled;
                if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
                    if (this.player.gameMode.shiftClickMended(itemInHand)) return; // Purpur - Shift right click to use exp for mending
                    org.bukkit.event.player.PlayerInteractEvent event = CraftEventFactory.callPlayerInteractEvent(this.player, Action.RIGHT_CLICK_AIR, itemInHand, hand);
                    cancelled = event.useItemInHand() == Event.Result.DENY;
                } else {
                    if (this.player.gameMode.firedInteract && this.player.gameMode.interactPosition.equals(hitResult.getBlockPos()) && this.player.gameMode.interactHand == hand && ItemStack.isSameItemSameComponents(this.player.gameMode.interactItemStack, itemInHand)) {
                        cancelled = this.player.gameMode.interactResult;
                    } else {
                        org.bukkit.event.player.PlayerInteractEvent event = CraftEventFactory.callPlayerInteractEvent(this.player, Action.RIGHT_CLICK_BLOCK, hitResult.getBlockPos(), hitResult.getDirection(), itemInHand, true, hand, hitResult.getLocation());
                        cancelled = event.useItemInHand() == Event.Result.DENY;
                    }
                    this.player.gameMode.firedInteract = false;
                }

                if (cancelled) {
                    this.player.resyncUsingItem(this.player); // Paper - Properly cancel usable items
                    this.player.containerMenu.sendAllDataToRemote(); // SPIGOT-2524
                    return;
                }
                itemInHand = this.player.getItemInHand(hand); // Update in case it was changed in the event
                if (itemInHand.isEmpty()) {
                    return;
                }
                // CraftBukkit end

                if (this.player.gameMode.useItem(this.player, serverLevel, itemInHand, hand) instanceof InteractionResult.Success success
                    && success.swingSource() == InteractionResult.SwingSource.SERVER) {
                    this.player.swing(hand, true);
                }
            }
        }
    }

    @Override
    public void handleTeleportToEntityPacket(ServerboundTeleportToEntityPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.isSpectator()) {
            for (ServerLevel serverLevel : this.server.getAllLevels()) {
                Entity entity = packet.getEntity(serverLevel);
                if (entity != null) {
                    this.player.teleportTo(serverLevel, entity.getX(), entity.getY(), entity.getZ(), Set.of(), entity.getYRot(), entity.getXRot(), true, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.SPECTATE); // CraftBukkit
                    return;
                }
            }
        }
    }

    @Override
    public void handlePaddleBoat(ServerboundPaddleBoatPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.getControlledVehicle() instanceof AbstractBoat abstractBoat) {
            abstractBoat.setPaddleState(packet.getLeft(), packet.getRight());
        }
    }

    @Override
    public void onDisconnect(DisconnectionDetails details) {
        // Paper start - Fix kick event leave message not being sent
        this.onDisconnect(details, null);
    }

    @Override
    public void onDisconnect(DisconnectionDetails details, @Nullable net.kyori.adventure.text.Component quitMessage) {
        // Paper end - Fix kick event leave message not being sent
        // CraftBukkit start - Rarely it would send a disconnect line twice
        if (this.processedDisconnect) {
            return;
        } else {
            this.processedDisconnect = true;
        }
        // CraftBukkit end
        LOGGER.info("{} lost connection: {}", this.player.getName().getString(), details.reason().getString());
        this.removePlayerFromWorld(quitMessage); // Paper - Fix kick event leave message not being sent
        super.onDisconnect(details, quitMessage); // Paper - Fix kick event leave message not being sent
    }

    private void removePlayerFromWorld() {
        // Paper start - Fix kick event leave message not being sent
        this.removePlayerFromWorld(null);
    }

    private void removePlayerFromWorld(@Nullable net.kyori.adventure.text.Component quitMessage) {
        // Paper end - Fix kick event leave message not being sent
        this.chatMessageChain.close();
        // CraftBukkit start - Replace vanilla quit message handling with our own.
        /*
        this.server.invalidateStatus();
        this.server
            .getPlayerList()
            .broadcastSystemMessage(Component.translatable("multiplayer.player.left", this.player.getDisplayName()).withStyle(ChatFormatting.YELLOW), false);
         */
        this.player.disconnect();
        // Paper start - Adventure
        quitMessage = quitMessage == null ? this.server.getPlayerList().remove(this.player) : this.server.getPlayerList().remove(this.player, quitMessage); // Paper - pass in quitMessage to fix kick message not being used
        if ((quitMessage != null) && !quitMessage.equals(net.kyori.adventure.text.Component.empty())) {
            this.server.getPlayerList().broadcastSystemMessage(PaperAdventure.asVanilla(quitMessage), false);
            // Paper end - Adventure
        }
        // CraftBukkit end
        this.player.getTextFilter().leave();
    }

    public void ackBlockChangesUpTo(int sequence) {
        if (sequence < 0) {
            this.disconnect(Component.literal("Expected packet sequence nr >= 0"), org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION); // Paper - Treat sequence violations like they should be
            throw new IllegalArgumentException("Expected packet sequence nr >= 0");
        } else {
            this.ackBlockChangesUpTo = Math.max(sequence, this.ackBlockChangesUpTo);
        }
    }

    @Override
    public void handleSetCarriedItem(ServerboundSetCarriedItemPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (packet.getSlot() >= 0 && packet.getSlot() < Inventory.getSelectionSize()) {
            if (packet.getSlot() == this.player.getInventory().selected) { return; } // Paper - don't fire itemheldevent when there wasn't a slot change
            PlayerItemHeldEvent event = new PlayerItemHeldEvent(this.getCraftPlayer(), this.player.getInventory().selected, packet.getSlot());
            this.cserver.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                this.send(new ClientboundSetHeldSlotPacket(this.player.getInventory().selected));
                this.player.resetLastActionTime();
                return;
            }
            // CraftBukkit end
            if (this.player.getInventory().selected != packet.getSlot() && this.player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
                this.player.stopUsingItem();
            }

            this.player.getInventory().selected = packet.getSlot();
            this.player.resetLastActionTime();
        } else {
            LOGGER.warn("{} tried to set an invalid carried item", this.player.getName().getString());
            this.disconnect(Component.literal("Invalid hotbar selection (Hacking?)"), org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION); // CraftBukkit // Paper - kick event cause
        }
    }

    @Override
    public void handleChat(ServerboundChatPacket packet) {
        // CraftBukkit start - async chat
        // SPIGOT-3638
        if (this.server.isStopped()) {
            return;
        }
        // CraftBukkit end
        Optional<LastSeenMessages> optional = this.unpackAndApplyLastSeen(packet.lastSeenMessages());
        if (!optional.isEmpty()) {
            this.tryHandleChat(packet.message(), () -> {
                PlayerChatMessage signedMessage;
                try {
                    signedMessage = this.getSignedMessage(packet, optional.get());
                } catch (SignedMessageChain.DecodeException var6) {
                    this.handleMessageDecodeFailure(var6);
                    return;
                }

                CompletableFuture<FilteredText> completableFuture = this.filterTextPacket(signedMessage.signedContent()).thenApplyAsync(Function.identity(), this.server.chatExecutor); // CraftBukkit - async chat
                CompletableFuture<Component> componentFuture = this.server.getChatDecorator().decorate(this.player, null, signedMessage.decoratedContent()); // Paper - Adventure

                this.chatMessageChain.append(CompletableFuture.allOf(completableFuture, componentFuture), (filteredtext) -> { // Paper - Adventure
                    PlayerChatMessage playerChatMessage = signedMessage.withUnsignedContent(componentFuture.join()).filter(completableFuture.join().mask()); // Paper - Adventure
                    this.broadcastChatMessage(playerChatMessage);
                });
            }, false); // CraftBukkit - async chat
        }
    }

    @Override
    public void handleChatCommand(ServerboundChatCommandPacket packet) {
        this.tryHandleChat(packet.command(), () -> {
            // CraftBukkit start - SPIGOT-7346: Prevent disconnected players from executing commands
            if (this.player.hasDisconnected()) {
                return;
            }
            // CraftBukkit end
            this.performUnsignedChatCommand(packet.command());
            this.detectRateSpam("/" + packet.command()); // Spigot
        }, true); // CraftBukkit - sync commands
    }

    private void performUnsignedChatCommand(String command) {
        // CraftBukkit start
        String prefixedCommand = "/" + command;
        if (org.spigotmc.SpigotConfig.logCommands) { // Paper - Add missing SpigotConfig logCommands check
            LOGGER.info("{} issued server command: {}", this.player.getScoreboardName(), prefixedCommand);
        }

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(this.getCraftPlayer(), prefixedCommand, new LazyPlayerSet(this.server));
        this.cserver.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }
        command = event.getMessage().substring(1);
        // CraftBukkit end
        ParseResults<CommandSourceStack> parseResults = this.parseCommand(command);
        if (this.server.enforceSecureProfile() && SignableCommand.hasSignableArguments(parseResults)) {
            LOGGER.error(
                "Received unsigned command packet from {}, but the command requires signable arguments: {}", this.player.getGameProfile().getName(), command
            );
            this.player.sendSystemMessage(INVALID_COMMAND_SIGNATURE);
        } else {
            this.server.getCommands().performCommand(parseResults, command);
        }
    }

    @Override
    public void handleSignedChatCommand(ServerboundChatCommandSignedPacket packet) {
        Optional<LastSeenMessages> optional = this.unpackAndApplyLastSeen(packet.lastSeenMessages());
        if (!optional.isEmpty()) {
            this.tryHandleChat(packet.command(), () -> {
                // CraftBukkit start - SPIGOT-7346: Prevent disconnected players from executing commands
                if (this.player.hasDisconnected()) {
                    return;
                }
                // CraftBukkit end
                this.performSignedChatCommand(packet, optional.get());
                this.detectRateSpam("/" + packet.command()); // Spigot
            }, true); // CraftBukkit - sync commands
        }
    }

    private void performSignedChatCommand(ServerboundChatCommandSignedPacket packet, LastSeenMessages lastSeenMessages) {
        // CraftBukkit start
        String command = "/" + packet.command();
        if (org.spigotmc.SpigotConfig.logCommands) { // Paper - Add missing SpigotConfig logCommands check
            LOGGER.info("{} issued server command: {}", this.player.getScoreboardName(), command);
        } // Paper - Add missing SpigotConfig logCommands check

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(this.getCraftPlayer(), command, new LazyPlayerSet(this.server));
        this.cserver.getPluginManager().callEvent(event);
        command = event.getMessage().substring(1);

        ParseResults<CommandSourceStack> parseResults = this.parseCommand(packet.command());

        Map<String, PlayerChatMessage> map;
        try {
            // Paper - Always parse the original command to add to the chat chain
            map = this.collectSignedArguments(packet, SignableCommand.of(parseResults), lastSeenMessages);
        } catch (SignedMessageChain.DecodeException var6) {
            this.handleMessageDecodeFailure(var6);
            return;
        }

        // Paper start - Fix cancellation and message changing
        if (event.isCancelled()) {
            // Only now are we actually good to return
            return;
        }

        // Remove signed parts if the command was changed
        if (!command.equals(packet.command())) {
            parseResults = this.parseCommand(command);
            map = Collections.emptyMap();
        }
        // Paper end - Fix cancellation and message changing

        CommandSigningContext commandSigningContext = new CommandSigningContext.SignedArguments(map);
        parseResults = Commands.mapSource(parseResults, source -> source.withSigningContext(commandSigningContext, this.chatMessageChain));
        this.server.getCommands().performCommand(parseResults, command); // CraftBukkit
    }

    private void handleMessageDecodeFailure(SignedMessageChain.DecodeException exception) {
        LOGGER.warn("Failed to update secure chat state for {}: '{}'", this.player.getGameProfile().getName(), exception.getComponent().getString());
        this.player.sendSystemMessage(exception.getComponent().copy().withStyle(ChatFormatting.RED));
    }

    private <S> Map<String, PlayerChatMessage> collectSignedArguments(
        ServerboundChatCommandSignedPacket packet, SignableCommand<S> command, LastSeenMessages lastSeenMessages
    ) throws SignedMessageChain.DecodeException {
        List<ArgumentSignatures.Entry> list = packet.argumentSignatures().entries();
        List<SignableCommand.Argument<S>> list1 = command.arguments();
        if (list.isEmpty()) {
            return this.collectUnsignedArguments(list1);
        } else {
            Map<String, PlayerChatMessage> map = new Object2ObjectOpenHashMap<>();

            for (ArgumentSignatures.Entry entry : list) {
                SignableCommand.Argument<S> argument = command.getArgument(entry.name());
                if (argument == null) {
                    this.signedMessageDecoder.setChainBroken();
                    throw createSignedArgumentMismatchException(packet.command(), list, list1);
                }

                SignedMessageBody signedMessageBody = new SignedMessageBody(argument.value(), packet.timeStamp(), packet.salt(), lastSeenMessages);
                map.put(argument.name(), this.signedMessageDecoder.unpack(entry.signature(), signedMessageBody));
            }

            for (SignableCommand.Argument<S> argument1 : list1) {
                if (!map.containsKey(argument1.name())) {
                    throw createSignedArgumentMismatchException(packet.command(), list, list1);
                }
            }

            return map;
        }
    }

    private <S> Map<String, PlayerChatMessage> collectUnsignedArguments(List<SignableCommand.Argument<S>> arguments) throws SignedMessageChain.DecodeException {
        Map<String, PlayerChatMessage> map = new HashMap<>();

        for (SignableCommand.Argument<S> argument : arguments) {
            SignedMessageBody signedMessageBody = SignedMessageBody.unsigned(argument.value());
            map.put(argument.name(), this.signedMessageDecoder.unpack(null, signedMessageBody));
        }

        return map;
    }

    private static <S> SignedMessageChain.DecodeException createSignedArgumentMismatchException(
        String command, List<ArgumentSignatures.Entry> signedArguments, List<SignableCommand.Argument<S>> unsignedArguments
    ) {
        String string = signedArguments.stream().map(ArgumentSignatures.Entry::name).collect(Collectors.joining(", "));
        String string1 = unsignedArguments.stream().map(SignableCommand.Argument::name).collect(Collectors.joining(", "));
        LOGGER.error("Signed command mismatch between server and client ('{}'): got [{}] from client, but expected [{}]", command, string, string1);
        return new SignedMessageChain.DecodeException(INVALID_COMMAND_SIGNATURE);
    }

    private ParseResults<CommandSourceStack> parseCommand(String command) {
        CommandDispatcher<CommandSourceStack> dispatcher = this.server.getCommands().getDispatcher();
        return dispatcher.parse(command, this.player.createCommandSourceStack());
    }

    private void tryHandleChat(String message, Runnable handler, boolean sync) { // CraftBukkit
        if (isChatMessageIllegal(message)) {
            this.disconnectAsync(Component.translatable("multiplayer.disconnect.illegal_characters"), org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_CHARACTERS); // Paper - add proper async disconnect
        } else if (this.player.isRemoved() || this.player.getChatVisibility() == ChatVisiblity.HIDDEN) { // CraftBukkit - dead men tell no tales
            this.send(new ClientboundSystemChatPacket(Component.translatable("chat.disabled.options").withStyle(ChatFormatting.RED), false));
        } else {
            this.player.resetLastActionTime();
            // CraftBukkit start
            if (sync) {
                this.server.execute(handler);
            } else {
                handler.run();
            }
            // CraftBukkit end
        }
    }

    private Optional<LastSeenMessages> unpackAndApplyLastSeen(LastSeenMessages.Update update) {
        synchronized (this.lastSeenMessages) {
            Optional<LastSeenMessages> optional = this.lastSeenMessages.applyUpdate(update);
            if (optional.isEmpty()) {
                LOGGER.warn("Failed to validate message acknowledgements from {}", this.player.getName().getString());
                this.disconnectAsync(CHAT_VALIDATION_FAILED, org.bukkit.event.player.PlayerKickEvent.Cause.CHAT_VALIDATION_FAILED); // Paper - kick event causes & add proper async disconnect
            }

            return optional;
        }
    }

    public static boolean isChatMessageIllegal(String message) {
        for (int i = 0; i < message.length(); i++) {
            if (!StringUtil.isAllowedChatCharacter(message.charAt(i))) {
                return true;
            }
        }

        return false;
    }

    // CraftBukkit start - add method
    public void chat(String msg, PlayerChatMessage original, boolean async) {
        if (msg.isEmpty() || this.player.getChatVisibility() == ChatVisiblity.HIDDEN) {
            return;
        }
        OutgoingChatMessage outgoing = OutgoingChatMessage.create(original);

        if (false && !async && msg.startsWith("/")) { // Paper - Don't handle commands in chat logic
            this.handleCommand(msg);
        } else if (this.player.getChatVisibility() == ChatVisiblity.SYSTEM) {
            // Do nothing, this is coming from a plugin
            // Paper start
        } else if (true) {
            if (!async && !org.bukkit.Bukkit.isPrimaryThread()) {
                org.spigotmc.AsyncCatcher.catchOp("Asynchronous player chat is not allowed here");
            }
            final ChatProcessor cp = new ChatProcessor(this.server, this.player, original, async);
            cp.process();
            // Paper end
        } else if (false) { // Paper
            org.bukkit.entity.Player player = this.getCraftPlayer();
            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(async, player, msg, new LazyPlayerSet(this.server));
            String originalFormat = event.getFormat(), originalMessage = event.getMessage();
            this.cserver.getPluginManager().callEvent(event);

            if (PlayerChatEvent.getHandlerList().getRegisteredListeners().length != 0) {
                // Evil plugins still listening to deprecated event
                final PlayerChatEvent queueEvent = new PlayerChatEvent(player, event.getMessage(), event.getFormat(), event.getRecipients());
                queueEvent.setCancelled(event.isCancelled());
                Waitable<Object> waitable = new Waitable<>() {
                    @Override
                    protected Object evaluate() {
                        org.bukkit.Bukkit.getPluginManager().callEvent(queueEvent);

                        if (queueEvent.isCancelled()) {
                            return null;
                        }

                        String message = String.format(queueEvent.getFormat(), queueEvent.getPlayer().getDisplayName(), queueEvent.getMessage());
                        if (((LazyPlayerSet) queueEvent.getRecipients()).isLazy()) {
                            if (!org.spigotmc.SpigotConfig.bungee && originalFormat.equals(queueEvent.getFormat()) && originalMessage.equals(queueEvent.getMessage()) && queueEvent.getPlayer().getName().equalsIgnoreCase(queueEvent.getPlayer().getDisplayName())) { // Spigot
                                ServerGamePacketListenerImpl.this.server.getPlayerList().broadcastChatMessage(original, ServerGamePacketListenerImpl.this.player, ChatType.bind(ChatType.CHAT, (Entity) ServerGamePacketListenerImpl.this.player));
                                return null;
                            }

                            for (ServerPlayer recipient : ServerGamePacketListenerImpl.this.server.getPlayerList().players) {
                                recipient.getBukkitEntity().sendMessage(ServerGamePacketListenerImpl.this.player.getUUID(), message);
                            }
                        } else {
                            for (org.bukkit.entity.Player recipient : queueEvent.getRecipients()) {
                                recipient.sendMessage(ServerGamePacketListenerImpl.this.player.getUUID(), message);
                            }
                        }
                        ServerGamePacketListenerImpl.this.server.console.sendMessage(message);

                        return null;
                    }};
                if (async) {
                    this.server.processQueue.add(waitable);
                } else {
                    waitable.run();
                }
                try {
                    waitable.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // This is proper habit for java. If we aren't handling it, pass it on!
                } catch (ExecutionException e) {
                    throw new RuntimeException("Exception processing chat event", e.getCause());
                }
            } else {
                if (event.isCancelled()) {
                    return;
                }

                msg = String.format(event.getFormat(), event.getPlayer().getDisplayName(), event.getMessage());
                if (((LazyPlayerSet) event.getRecipients()).isLazy()) {
                    if (!org.spigotmc.SpigotConfig.bungee && originalFormat.equals(event.getFormat()) && originalMessage.equals(event.getMessage()) && event.getPlayer().getName().equalsIgnoreCase(event.getPlayer().getDisplayName())) { // Spigot
                        ServerGamePacketListenerImpl.this.server.getPlayerList().broadcastChatMessage(original, ServerGamePacketListenerImpl.this.player, ChatType.bind(ChatType.CHAT, ServerGamePacketListenerImpl.this.player));
                        return;
                    }

                    for (ServerPlayer recipient : this.server.getPlayerList().players) {
                        recipient.getBukkitEntity().sendMessage(ServerGamePacketListenerImpl.this.player.getUUID(), msg);
                    }
                } else {
                    for (org.bukkit.entity.Player recipient : event.getRecipients()) {
                        recipient.sendMessage(ServerGamePacketListenerImpl.this.player.getUUID(), msg);
                    }
                }
                this.server.console.sendMessage(msg);
            }
        }
    }

    @Deprecated // Paper
    public void handleCommand(String s) { // Paper - private -> public
        // Paper start - Remove all this old duplicated logic
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        /*
        It should be noted that this represents the "legacy" command execution path.
        Api can call commands even if there is no additional context provided.
        This method should ONLY be used if you need to execute a command WITHOUT
        an actual player's input.
        */
        this.performUnsignedChatCommand(s);
        // Paper end
    }
    // CraftBukkit end

    private PlayerChatMessage getSignedMessage(ServerboundChatPacket packet, LastSeenMessages lastSeenMessages) throws SignedMessageChain.DecodeException {
        SignedMessageBody signedMessageBody = new SignedMessageBody(packet.message(), packet.timeStamp(), packet.salt(), lastSeenMessages);
        return this.signedMessageDecoder.unpack(packet.signature(), signedMessageBody);
    }

    private void broadcastChatMessage(PlayerChatMessage message) {
        // CraftBukkit start
        String rawMessage = message.signedContent();
        if (rawMessage.isEmpty()) {
            LOGGER.warn("{} tried to send an empty message", this.player.getScoreboardName());
        } else if (this.getCraftPlayer().isConversing()) {
            final String conversationInput = rawMessage;
            this.server.processQueue.add(() -> ServerGamePacketListenerImpl.this.getCraftPlayer().acceptConversationInput(conversationInput));
        } else if (this.player.getChatVisibility() == ChatVisiblity.SYSTEM) { // Re-add "Command Only" flag check
            this.send(new ClientboundSystemChatPacket(Component.translatable("chat.cannotSend").withStyle(ChatFormatting.RED), false));
        } else {
            this.chat(rawMessage, message, true);
        }
        // this.server.getPlayerList().broadcastChatMessage(message, this.player, ChatType.bind(ChatType.CHAT, this.player));
        // CraftBukkit end
        this.detectRateSpam(rawMessage); // Spigot
    }

    // Spigot start - spam exclusions
    private void detectRateSpam(String message) {
        // CraftBukkit start - replaced with thread safe throttle
        for (String exclude : org.spigotmc.SpigotConfig.spamExclusions) {
            if (exclude != null && message.startsWith(exclude)) {
                return;
            }
        }
        // Spigot end
        // this.chatSpamThrottler.increment();
        if (!this.chatSpamThrottler.isIncrementAndUnderThreshold()
            // CraftBukkit end
            && !this.server.getPlayerList().isOp(this.player.getGameProfile())
            && !this.server.isSingleplayerOwner(this.player.getGameProfile())) {
            this.disconnectAsync(Component.translatable("disconnect.spam"), org.bukkit.event.player.PlayerKickEvent.Cause.SPAM); // Paper - kick event cause & add proper async disconnect
        }
    }

    @Override
    public void handleChatAck(ServerboundChatAckPacket packet) {
        synchronized (this.lastSeenMessages) {
            if (!this.lastSeenMessages.applyOffset(packet.offset())) {
                LOGGER.warn("Failed to validate message acknowledgements from {}", this.player.getName().getString());
                this.disconnectAsync(ServerGamePacketListenerImpl.CHAT_VALIDATION_FAILED, org.bukkit.event.player.PlayerKickEvent.Cause.CHAT_VALIDATION_FAILED); // Paper - kick event causes & add proper async disconnect
            }
        }
    }

    @Override
    public void handleAnimate(ServerboundSwingPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        // CraftBukkit start - Raytrace to look for 'rogue armswings'
        float f1 = this.player.getXRot();
        float f2 = this.player.getYRot();
        double d0 = this.player.getX();
        double d1 = this.player.getY() + (double) this.player.getEyeHeight();
        double d2 = this.player.getZ();
        Location origin = new Location(this.player.level().getWorld(), d0, d1, d2, f2, f1);

        double d3 = Math.max(this.player.blockInteractionRange(), this.player.entityInteractionRange());
        // SPIGOT-5607: Only call interact event if no block or entity is being clicked. Use bukkit ray trace method, because it handles blocks and entities at the same time
        // SPIGOT-7429: Make sure to call PlayerInteractEvent for spectators and non-pickable entities
        org.bukkit.util.RayTraceResult result = this.player.level().getWorld().rayTrace(origin, origin.getDirection(), d3, org.bukkit.FluidCollisionMode.NEVER, false, 0.0, entity -> { // Paper - Call interact event; change raySize from 0.1 to 0.0
            Entity handle = ((CraftEntity) entity).getHandle();
            return entity != this.player.getBukkitEntity() && this.player.getBukkitEntity().canSee(entity) && !handle.isSpectator() && handle.isPickable() && !handle.isPassengerOfSameVehicle(this.player);
        });
        if (result == null) {
            CraftEventFactory.callPlayerInteractEvent(this.player, Action.LEFT_CLICK_AIR, this.player.getInventory().getSelected(), InteractionHand.MAIN_HAND);
        } else { // Paper start - Call interact event
            GameType gameType = this.player.gameMode.getGameModeForPlayer();
            if (gameType == GameType.ADVENTURE && result.getHitBlock() != null) {
                CraftEventFactory.callPlayerInteractEvent(this.player, Action.LEFT_CLICK_BLOCK, ((org.bukkit.craftbukkit.block.CraftBlock) result.getHitBlock()).getPosition(), org.bukkit.craftbukkit.block.CraftBlock.blockFaceToNotch(result.getHitBlockFace()), this.player.getInventory().getSelected(), InteractionHand.MAIN_HAND);
            } else if (gameType != GameType.CREATIVE && result.getHitEntity() != null && origin.toVector().distanceSquared(result.getHitPosition()) > this.player.entityInteractionRange() * this.player.entityInteractionRange()) {
                CraftEventFactory.callPlayerInteractEvent(this.player, Action.LEFT_CLICK_AIR, this.player.getInventory().getSelected(), InteractionHand.MAIN_HAND);
            }
        } // Paper end - Call interact event

        // Arm swing animation
        io.papermc.paper.event.player.PlayerArmSwingEvent event = new io.papermc.paper.event.player.PlayerArmSwingEvent(this.getCraftPlayer(), org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(packet.getHand())); // Paper - Add PlayerArmSwingEvent
        this.cserver.getPluginManager().callEvent(event);

        if (event.isCancelled()) return;
        // CraftBukkit end
        this.player.swing(packet.getHand());
    }

    @Override
    public void handlePlayerCommand(ServerboundPlayerCommandPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.hasClientLoaded()) {
            // CraftBukkit start
            if (this.player.isRemoved()) return;
            switch (packet.getAction()) {
                case PRESS_SHIFT_KEY:
                case RELEASE_SHIFT_KEY: {
                    PlayerToggleSneakEvent event = new PlayerToggleSneakEvent(this.getCraftPlayer(), packet.getAction() == ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY);
                    this.cserver.getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return;
                    }
                    break;
                }
                case START_SPRINTING:
                case STOP_SPRINTING: {
                    PlayerToggleSprintEvent event = new PlayerToggleSprintEvent(this.getCraftPlayer(), packet.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING);
                    this.cserver.getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return;
                    }
                    break;
                }
            }
            // CraftBukkit end

            this.player.resetLastActionTime();
            switch (packet.getAction()) {
                case PRESS_SHIFT_KEY:
                    this.player.setShiftKeyDown(true);
                    // Paper start - Add option to make parrots stay
                    if (this.player.level().paperConfig().entities.behavior.parrotsAreUnaffectedByPlayerMovement) {
                        this.player.removeEntitiesOnShoulder();
                    }
                    // Paper end - Add option to make parrots stay
                    break;
                case RELEASE_SHIFT_KEY:
                    this.player.setShiftKeyDown(false);
                    break;
                case START_SPRINTING:
                    this.player.setSprinting(true);
                    break;
                case STOP_SPRINTING:
                    this.player.setSprinting(false);
                    break;
                case STOP_SLEEPING:
                    if (this.player.isSleeping()) {
                        this.player.stopSleepInBed(false, true);
                        this.awaitingPositionFromClient = this.player.position();
                    }
                    break;
                case START_RIDING_JUMP:
                    if (this.player.getControlledVehicle() instanceof PlayerRideableJumping playerRideableJumping) {
                        int data = packet.getData();
                        if (playerRideableJumping.canJump() && data > 0) {
                            playerRideableJumping.handleStartJump(data);
                        }
                    }
                    break;
                case STOP_RIDING_JUMP:
                    if (this.player.getControlledVehicle() instanceof PlayerRideableJumping playerRideableJumping) {
                        playerRideableJumping.handleStopJump();
                    }
                    break;
                case OPEN_INVENTORY:
                    if (this.player.getVehicle() instanceof HasCustomInventoryScreen hasCustomInventoryScreen) {
                        hasCustomInventoryScreen.openCustomInventoryScreen(this.player);
                    }
                    break;
                case START_FALL_FLYING:
                    if (!this.player.tryToStartFallFlying()) {
                        this.player.stopFallFlying();
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Invalid client command!");
            }
        }
    }

    public void addPendingMessage(PlayerChatMessage message) {
        MessageSignature messageSignature = message.signature();
        if (messageSignature != null) {
            this.messageSignatureCache.push(message.signedBody(), message.signature());
            int i;
            synchronized (this.lastSeenMessages) {
                this.lastSeenMessages.addPending(messageSignature);
                i = this.lastSeenMessages.trackedMessagesCount();
            }

            if (i > 4096) {
                this.disconnectAsync(Component.translatable("multiplayer.disconnect.too_many_pending_chats"), org.bukkit.event.player.PlayerKickEvent.Cause.TOO_MANY_PENDING_CHATS); // Paper - kick event cause & add proper async disconnect
            }
        }
    }

    public void sendPlayerChatMessage(PlayerChatMessage chatMessage, ChatType.Bound boundType) {
        // CraftBukkit start - SPIGOT-7262: if hidden we have to send as disguised message. Query whether we should send at all (but changing this may not be expected).
        if (!this.getCraftPlayer().canSeePlayer(chatMessage.link().sender())) {
            this.sendDisguisedChatMessage(chatMessage.decoratedContent(), boundType);
            return;
        }
        // CraftBukkit end
        // Paper start - Ensure that client receives chat packets in the same order that we add into the message signature cache
        synchronized (this.messageSignatureCache) {
        this.send(
            new ClientboundPlayerChatPacket(
                chatMessage.link().sender(),
                chatMessage.link().index(),
                chatMessage.signature(),
                chatMessage.signedBody().pack(this.messageSignatureCache),
                chatMessage.unsignedContent(),
                chatMessage.filterMask(),
                boundType
            )
        );
        this.addPendingMessage(chatMessage);
        }
        // Paper end - Ensure that client receives chat packets in the same order that we add into the message signature cache
    }

    public void sendDisguisedChatMessage(Component message, ChatType.Bound boundType) {
        this.send(new ClientboundDisguisedChatPacket(message, boundType));
    }

    public SocketAddress getRemoteAddress() {
        return this.connection.getRemoteAddress();
    }

    // Spigot start
    public SocketAddress getRawAddress() {
        // Paper start - Unix domain socket support; this can be nullable in the case of a Unix domain socket, so if it is, fake something
        if (this.connection.channel.remoteAddress() == null) {
            return new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0);
        }
        // Paper end - Unix domain socket support
        return this.connection.channel.remoteAddress();
    }
    // Spigot end

    public void switchToConfig() {
        this.waitingForSwitchToConfig = true;
        this.removePlayerFromWorld();
        this.send(ClientboundStartConfigurationPacket.INSTANCE);
        this.connection.setupOutboundProtocol(ConfigurationProtocols.CLIENTBOUND);
    }

    @Override
    public void handlePingRequest(ServerboundPingRequestPacket packet) {
        this.connection.send(new ClientboundPongResponsePacket(packet.getTime()));
    }

    @Override
    public void handleInteract(ServerboundInteractPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (this.player.hasClientLoaded()) {
            final ServerLevel serverLevel = this.player.serverLevel();
            final Entity target = packet.getTarget(serverLevel);
            // Spigot start
            if (target == this.player && !this.player.isSpectator()) {
                this.disconnect(Component.literal("Cannot interact with self!"), org.bukkit.event.player.PlayerKickEvent.Cause.SELF_INTERACTION); // Paper - kick event cause
                return;
            }
            // Spigot end
            this.player.resetLastActionTime();
            this.player.setShiftKeyDown(packet.isUsingSecondaryAction());
            if (target != null) {
                if (!serverLevel.getWorldBorder().isWithinBounds(target.blockPosition())) {
                    return;
                }

                AABB boundingBox = target.getBoundingBox();
                if (this.player.canInteractWithEntity(boundingBox, io.papermc.paper.configuration.GlobalConfiguration.get().misc.clientInteractionLeniencyDistance.or(3.0))) { // Paper - configurable lenience value for interact range
                    if (target instanceof net.minecraft.world.entity.Mob mob) mob.ticksSinceLastInteraction = 0; // Purpur - Entity lifespan
                    packet.dispatch(
                        new ServerboundInteractPacket.Handler() {
                            private void performInteraction(InteractionHand hand, ServerGamePacketListenerImpl.EntityInteraction entityInteraction, PlayerInteractEntityEvent event) { // CraftBukkit
                                ItemStack itemInHand = ServerGamePacketListenerImpl.this.player.getItemInHand(hand);
                                if (itemInHand.isItemEnabled(serverLevel.enabledFeatures())) {
                                    ItemStack itemStack = itemInHand.copy();
                                    // CraftBukkit start
                                    boolean triggerLeashUpdate = itemInHand != null && itemInHand.getItem() == Items.LEAD && target instanceof net.minecraft.world.entity.Mob;
                                    Item origItem = ServerGamePacketListenerImpl.this.player.getInventory().getSelected() == null ? null : ServerGamePacketListenerImpl.this.player.getInventory().getSelected().getItem();

                                    ServerGamePacketListenerImpl.this.cserver.getPluginManager().callEvent(event);

                                    player.processClick(hand); // Purpur - Ridables

                                    // Entity in bucket - SPIGOT-4048 and SPIGOT-6859a
                                    if ((target instanceof Bucketable && target instanceof LivingEntity && origItem != null && origItem.asItem() == Items.WATER_BUCKET) && (event.isCancelled() || ServerGamePacketListenerImpl.this.player.getInventory().getSelected() == null || ServerGamePacketListenerImpl.this.player.getInventory().getSelected().getItem() != origItem)) {
                                        target.resendPossiblyDesyncedEntityData(ServerGamePacketListenerImpl.this.player); // Paper - The entire mob gets deleted, so resend it
                                        ServerGamePacketListenerImpl.this.player.containerMenu.sendAllDataToRemote();
                                    }

                                    if (triggerLeashUpdate && (event.isCancelled() || ServerGamePacketListenerImpl.this.player.getInventory().getSelected() == null || ServerGamePacketListenerImpl.this.player.getInventory().getSelected().getItem() != origItem)) {
                                        // Refresh the current leash state
                                        ServerGamePacketListenerImpl.this.send(new net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket(target, ((net.minecraft.world.entity.Mob) target).getLeashHolder()));
                                    }

                                    if (event.isCancelled() || ServerGamePacketListenerImpl.this.player.getInventory().getSelected() == null || ServerGamePacketListenerImpl.this.player.getInventory().getSelected().getItem() != origItem) {
                                        // Refresh the current entity metadata
                                        target.refreshEntityData(ServerGamePacketListenerImpl.this.player);
                                        // SPIGOT-7136 - Allays
                                        if (target instanceof Allay || target instanceof net.minecraft.world.entity.animal.horse.AbstractHorse) { // Paper - Fix horse armor desync
                                            ServerGamePacketListenerImpl.this.send(new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(target.getId(), Arrays.stream(net.minecraft.world.entity.EquipmentSlot.values()).map((slot) -> Pair.of(slot, ((LivingEntity) target).getItemBySlot(slot).copy())).collect(Collectors.toList()), true)); // Paper - sanitize
                                        }

                                        ServerGamePacketListenerImpl.this.player.containerMenu.sendAllDataToRemote(); // Paper - fix slot desync - always refresh player inventory
                                    }

                                    if (event.isCancelled()) {
                                        return;
                                    }
                                    // CraftBukkit end
                                    InteractionResult result = entityInteraction.run(ServerGamePacketListenerImpl.this.player, target, hand);

                                    // CraftBukkit start
                                    if (!itemInHand.isEmpty() && itemInHand.getCount() <= -1) {
                                        ServerGamePacketListenerImpl.this.player.containerMenu.sendAllDataToRemote();
                                    }
                                    // CraftBukkit end

                                    if (result instanceof InteractionResult.Success success // CraftBukkit
                                    ) {
                                        ItemStack itemStack1 = success.wasItemInteraction() ? itemStack : ItemStack.EMPTY;
                                        CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY.trigger(ServerGamePacketListenerImpl.this.player, itemStack1, target);
                                        if (success.swingSource() == InteractionResult.SwingSource.SERVER) {
                                            ServerGamePacketListenerImpl.this.player.swing(hand, true);
                                        }
                                    }
                                }
                            }

                            @Override
                            public void onInteraction(InteractionHand hand) {
                                this.performInteraction(hand, Player::interactOn, new PlayerInteractEntityEvent(ServerGamePacketListenerImpl.this.getCraftPlayer(), target.getBukkitEntity(), org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(hand))); // CraftBukkit
                            }

                            @Override
                            public void onInteraction(InteractionHand hand, Vec3 interactionLocation) {
                                this.performInteraction(
                                    hand, (player, entity, interactionHand) -> entity.interactAt(player, interactionLocation, interactionHand), new PlayerInteractAtEntityEvent(ServerGamePacketListenerImpl.this.getCraftPlayer(), target.getBukkitEntity(), org.bukkit.craftbukkit.util.CraftVector.toBukkit(interactionLocation), org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(hand)) // CraftBukkit
                                );
                            }

                            @Override
                            public void onAttack() {
                                if (!(target instanceof ItemEntity)
                                    && !(target instanceof ExperienceOrb)
                                    && (target != ServerGamePacketListenerImpl.this.player || ServerGamePacketListenerImpl.this.player.isSpectator()) // CraftBukkit
                                    && !(target instanceof AbstractArrow abstractArrow && !abstractArrow.isAttackable())) {
                                    ItemStack itemInHand = ServerGamePacketListenerImpl.this.player.getItemInHand(InteractionHand.MAIN_HAND);
                                    if (itemInHand.isItemEnabled(serverLevel.enabledFeatures())) {
                                        ServerGamePacketListenerImpl.this.player.attack(target);
                                        // CraftBukkit start
                                        if (!itemInHand.isEmpty() && itemInHand.getCount() <= -1) {
                                            ServerGamePacketListenerImpl.this.player.containerMenu.sendAllDataToRemote();
                                        }
                                        // CraftBukkit end
                                    }
                                } else {
                                    ServerGamePacketListenerImpl.this.disconnect(Component.translatable("multiplayer.disconnect.invalid_entity_attacked"), org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_ENTITY_ATTACKED); // Paper - add cause
                                    ServerGamePacketListenerImpl.LOGGER
                                        .warn("Player {} tried to attack an invalid entity", ServerGamePacketListenerImpl.this.player.getName().getString());
                                }
                            }
                        }
                    );
                }
            }
            // Paper start - PlayerUseUnknownEntityEvent
            else {
                packet.dispatch(new net.minecraft.network.protocol.game.ServerboundInteractPacket.Handler() {
                    @Override
                    public void onInteraction(net.minecraft.world.InteractionHand hand) {
                        CraftEventFactory.callPlayerUseUnknownEntityEvent(ServerGamePacketListenerImpl.this.player, packet, hand, null);
                    }

                    @Override
                    public void onInteraction(net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.Vec3 pos) {
                        CraftEventFactory.callPlayerUseUnknownEntityEvent(ServerGamePacketListenerImpl.this.player, packet, hand, pos);
                    }

                    @Override
                    public void onAttack() {
                        CraftEventFactory.callPlayerUseUnknownEntityEvent(ServerGamePacketListenerImpl.this.player, packet, net.minecraft.world.InteractionHand.MAIN_HAND, null);
                    }
                });
            }
            // Paper end - PlayerUseUnknownEntityEvent
        }
    }

    @Override
    public void handleClientCommand(ServerboundClientCommandPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        this.player.resetLastActionTime();
        ServerboundClientCommandPacket.Action action = packet.getAction();
        switch (action) {
            case PERFORM_RESPAWN:
                if (this.player.wonGame) {
                    this.player.wonGame = false;
                    this.player = this.server.getPlayerList().respawn(this.player, true, Entity.RemovalReason.CHANGED_DIMENSION, RespawnReason.END_PORTAL); // CraftBukkit
                    this.resetPosition();
                    CriteriaTriggers.CHANGED_DIMENSION.trigger(this.player, Level.END, Level.OVERWORLD);
                } else {
                    if (this.player.getHealth() > 0.0F) {
                        return;
                    }

                    this.player = this.server.getPlayerList().respawn(this.player, false, Entity.RemovalReason.KILLED, RespawnReason.DEATH); // CraftBukkit
                    this.resetPosition();
                    if (this.server.isHardcore()) {
                        this.player.setGameMode(GameType.SPECTATOR, org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.HARDCORE_DEATH, null); // Paper - Expand PlayerGameModeChangeEvent
                        this.player.serverLevel().getGameRules().getRule(GameRules.RULE_SPECTATORSGENERATECHUNKS).set(false, this.player.serverLevel()); // CraftBukkit - per-world
                    }
                }
                break;
            case REQUEST_STATS:
                this.player.getStats().sendStats(this.player);
        }
    }

    @Override
    public void handleContainerClose(ServerboundContainerClosePacket packet) {
        // Paper start - Inventory close reason
        this.handleContainerClose(packet, org.bukkit.event.inventory.InventoryCloseEvent.Reason.PLAYER);
    }

    public void handleContainerClose(ServerboundContainerClosePacket packet, org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) {
        // Paper end - Inventory close reason
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());

        if (this.player.isImmobile()) return; // CraftBukkit
        CraftEventFactory.handleInventoryCloseEvent(this.player, reason); // CraftBukkit // Paper

        this.player.doCloseContainer();
    }

    @Override
    public void handleContainerClick(ServerboundContainerClickPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        if (this.player.containerMenu.containerId == packet.getContainerId() && this.player.containerMenu.stillValid(this.player)) { // CraftBukkit
            boolean cancelled = this.player.isSpectator(); // CraftBukkit - see below if
            if (false/*this.player.isSpectator()*/) { // CraftBukkit
                this.player.containerMenu.sendAllDataToRemote();
            } else if (!this.player.containerMenu.stillValid(this.player)) {
                LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
            } else {
                int slotNum = packet.getSlotNum();
                if (!this.player.containerMenu.isValidSlotIndex(slotNum)) {
                    LOGGER.debug(
                        "Player {} clicked invalid slot index: {}, available slots: {}", this.player.getName(), slotNum, this.player.containerMenu.slots.size()
                    );
                } else {
                    boolean flag = packet.getStateId() != this.player.containerMenu.getStateId();
                    this.player.containerMenu.suppressRemoteUpdates();
                    // CraftBukkit start - Call InventoryClickEvent
                    if (slotNum < -1 && slotNum != AbstractContainerMenu.SLOT_CLICKED_OUTSIDE) {
                        return;
                    }

                    InventoryView inventory = this.player.containerMenu.getBukkitView();
                    SlotType type = inventory.getSlotType(slotNum);

                    InventoryClickEvent event;
                    ClickType click = ClickType.UNKNOWN;
                    InventoryAction action = InventoryAction.UNKNOWN;

                    switch (packet.getClickType()) {
                        case PICKUP:
                            if (packet.getButtonNum() == 0) {
                                click = ClickType.LEFT;
                            } else if (packet.getButtonNum() == 1) {
                                click = ClickType.RIGHT;
                            }
                            if (packet.getButtonNum() == 0 || packet.getButtonNum() == 1) {
                                action = InventoryAction.NOTHING; // Don't want to repeat ourselves
                                if (slotNum == AbstractContainerMenu.SLOT_CLICKED_OUTSIDE) {
                                    if (!this.player.containerMenu.getCarried().isEmpty()) {
                                        action = packet.getButtonNum() == 0 ? InventoryAction.DROP_ALL_CURSOR : InventoryAction.DROP_ONE_CURSOR;
                                    }
                                } else if (slotNum < 0)  {
                                    action = InventoryAction.NOTHING;
                                } else {
                                    Slot slot = this.player.containerMenu.getSlot(slotNum);
                                    if (slot != null) {
                                        ItemStack clickedItem = slot.getItem();
                                        ItemStack cursor = this.player.containerMenu.getCarried();
                                        if (clickedItem.isEmpty()) {
                                            if (!cursor.isEmpty()) {
                                                if (cursor.getItem() instanceof net.minecraft.world.item.BundleItem && packet.getButtonNum() != 0) {
                                                    action = cursor.get(DataComponents.BUNDLE_CONTENTS).isEmpty() ? InventoryAction.NOTHING : InventoryAction.PLACE_FROM_BUNDLE;
                                                } else {
                                                    action = packet.getButtonNum() == 0 ? InventoryAction.PLACE_ALL : InventoryAction.PLACE_ONE;
                                                }
                                            }
                                        } else if (slot.mayPickup(this.player)) {
                                            if (cursor.isEmpty()) {
                                                if (slot.getItem().getItem() instanceof net.minecraft.world.item.BundleItem && packet.getButtonNum() != 0) {
                                                    action = slot.getItem().get(DataComponents.BUNDLE_CONTENTS).isEmpty() ? InventoryAction.NOTHING : InventoryAction.PICKUP_FROM_BUNDLE;
                                                } else {
                                                    action = packet.getButtonNum() == 0 ? InventoryAction.PICKUP_ALL : InventoryAction.PICKUP_HALF;
                                                }
                                            } else if (slot.mayPlace(cursor)) {
                                                if (ItemStack.isSameItemSameComponents(clickedItem, cursor)) {
                                                    int toPlace = packet.getButtonNum() == 0 ? cursor.getCount() : 1;
                                                    toPlace = Math.min(toPlace, clickedItem.getMaxStackSize() - clickedItem.getCount());
                                                    toPlace = Math.min(toPlace, slot.container.getMaxStackSize() - clickedItem.getCount());
                                                    if (toPlace == 1) {
                                                        action = InventoryAction.PLACE_ONE;
                                                    } else if (toPlace == cursor.getCount()) {
                                                        action = InventoryAction.PLACE_ALL;
                                                    } else if (toPlace < 0) {
                                                        action = toPlace != -1 ? InventoryAction.PICKUP_SOME : InventoryAction.PICKUP_ONE; // this happens with oversized stacks
                                                    } else if (toPlace != 0) {
                                                        action = InventoryAction.PLACE_SOME;
                                                    }
                                                } else if (cursor.getCount() <= slot.getMaxStackSize()) {
                                                    if (cursor.getItem() instanceof net.minecraft.world.item.BundleItem && packet.getButtonNum() == 0) {
                                                        int toPickup = cursor.get(DataComponents.BUNDLE_CONTENTS).getMaxAmountToAdd(slot.getItem());
                                                        if (toPickup >= slot.getItem().getCount()) {
                                                            action = InventoryAction.PICKUP_ALL_INTO_BUNDLE;
                                                        } else if (toPickup == 0) {
                                                            action = InventoryAction.NOTHING;
                                                        } else {
                                                            action = InventoryAction.PICKUP_SOME_INTO_BUNDLE;
                                                        }
                                                    } else if (slot.getItem().getItem() instanceof net.minecraft.world.item.BundleItem && packet.getButtonNum() == 0) {
                                                        int toPickup = slot.getItem().get(DataComponents.BUNDLE_CONTENTS).getMaxAmountToAdd(cursor);
                                                        if (toPickup >= cursor.getCount()) {
                                                            action = InventoryAction.PLACE_ALL_INTO_BUNDLE;
                                                        } else if (toPickup == 0) {
                                                            action = InventoryAction.NOTHING;
                                                        } else {
                                                            action = InventoryAction.PLACE_SOME_INTO_BUNDLE;
                                                        }
                                                    } else {
                                                        action = InventoryAction.SWAP_WITH_CURSOR;
                                                    }
                                                }
                                            } else if (ItemStack.isSameItemSameComponents(cursor, clickedItem)) {
                                                if (clickedItem.getCount() >= 0) {
                                                    if (clickedItem.getCount() + cursor.getCount() <= cursor.getMaxStackSize()) {
                                                        // As of 1.5, this is result slots only
                                                        action = InventoryAction.PICKUP_ALL;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            break;
                        // TODO check on updates
                        case QUICK_MOVE:
                            if (packet.getButtonNum() == 0) {
                                click = ClickType.SHIFT_LEFT;
                            } else if (packet.getButtonNum() == 1) {
                                click = ClickType.SHIFT_RIGHT;
                            }
                            if (packet.getButtonNum() == 0 || packet.getButtonNum() == 1) {
                                if (slotNum < 0) {
                                    action = InventoryAction.NOTHING;
                                } else {
                                    Slot slot = this.player.containerMenu.getSlot(slotNum);
                                    if (slot != null && slot.mayPickup(this.player) && slot.hasItem()) {
                                        action = InventoryAction.MOVE_TO_OTHER_INVENTORY;
                                    } else {
                                        action = InventoryAction.NOTHING;
                                    }
                                }
                            }
                            break;
                            case SWAP:
                                if ((packet.getButtonNum() >= 0 && packet.getButtonNum() < 9) || packet.getButtonNum() == Inventory.SLOT_OFFHAND) {
                                    // Paper start - Add slot sanity checks to container clicks
                                    if (slotNum < 0) {
                                        action = InventoryAction.NOTHING;
                                        break;
                                    }
                                    // Paper end - Add slot sanity checks to container clicks
                                    click = (packet.getButtonNum() == Inventory.SLOT_OFFHAND) ? ClickType.SWAP_OFFHAND : ClickType.NUMBER_KEY;
                                    Slot clickedSlot = this.player.containerMenu.getSlot(slotNum);
                                    if (clickedSlot.mayPickup(this.player)) {
                                        ItemStack hotbar = this.player.getInventory().getItem(packet.getButtonNum());
                                        if ((!hotbar.isEmpty() && clickedSlot.mayPlace(hotbar)) || (hotbar.isEmpty() && clickedSlot.hasItem())) { // Paper - modernify this logic (no such thing as a "hotbar move and readd"
                                            action = InventoryAction.HOTBAR_SWAP;
                                        } else {
                                            action = InventoryAction.NOTHING;
                                        }
                                    } else {
                                        action = InventoryAction.NOTHING;
                                    }
                                }
                                break;
                            case CLONE:
                                if (packet.getButtonNum() == 2) {
                                    click = ClickType.MIDDLE;
                                    if (slotNum < 0) {
                                        action = InventoryAction.NOTHING;
                                    } else {
                                        Slot slot = this.player.containerMenu.getSlot(slotNum);
                                        if (slot != null && slot.hasItem() && this.player.getAbilities().instabuild && this.player.containerMenu.getCarried().isEmpty()) {
                                            action = InventoryAction.CLONE_STACK;
                                        } else {
                                            action = InventoryAction.NOTHING;
                                        }
                                    }
                                } else {
                                    click = ClickType.UNKNOWN;
                                    action = InventoryAction.UNKNOWN;
                                }
                                break;
                            case THROW:
                                if (slotNum >= 0) {
                                    if (packet.getButtonNum() == 0) {
                                        click = ClickType.DROP;
                                        Slot slot = this.player.containerMenu.getSlot(slotNum);
                                        if (slot != null && slot.hasItem() && slot.mayPickup(this.player) && !slot.getItem().isEmpty() && slot.getItem().getItem() != Items.AIR) {
                                            action = InventoryAction.DROP_ONE_SLOT;
                                        } else {
                                            action = InventoryAction.NOTHING;
                                        }
                                    } else if (packet.getButtonNum() == 1) {
                                        click = ClickType.CONTROL_DROP;
                                        Slot slot = this.player.containerMenu.getSlot(slotNum);
                                        if (slot != null && slot.hasItem() && slot.mayPickup(this.player) && !slot.getItem().isEmpty() && slot.getItem().getItem() != Items.AIR) {
                                            action = InventoryAction.DROP_ALL_SLOT;
                                        } else {
                                            action = InventoryAction.NOTHING;
                                        }
                                    }
                                } else {
                                    // Sane default (because this happens when they are holding nothing. Don't ask why.)
                                    click = ClickType.LEFT;
                                    if (packet.getButtonNum() == 1) {
                                        click = ClickType.RIGHT;
                                    }
                                    action = InventoryAction.NOTHING;
                                }
                                break;
                            case QUICK_CRAFT:
                                // Paper start - Fix CraftBukkit drag system
                                AbstractContainerMenu containerMenu = this.player.containerMenu;
                                int currentStatus = this.player.containerMenu.quickcraftStatus;
                                int newStatus = AbstractContainerMenu.getQuickcraftHeader(packet.getButtonNum());
                                if ((currentStatus != 1 || newStatus != 2 && currentStatus != newStatus)) {
                                } else if (containerMenu.getCarried().isEmpty()) {
                                } else if (newStatus == 0) {
                                } else if (newStatus == 1) {
                                } else if (newStatus == 2) {
                                    if (!this.player.containerMenu.quickcraftSlots.isEmpty()) {
                                        if (this.player.containerMenu.quickcraftSlots.size() == 1) {
                                            int index = containerMenu.quickcraftSlots.iterator().next().index;
                                            containerMenu.resetQuickCraft();
                                            this.handleContainerClick(new ServerboundContainerClickPacket(packet.getContainerId(), packet.getStateId(), index, containerMenu.quickcraftType, net.minecraft.world.inventory.ClickType.PICKUP, packet.getCarriedItem(), packet.getChangedSlots()));
                                            return;
                                        }
                                    }
                                }
                                // Paper end - Fix CraftBukkit drag system
                                this.player.containerMenu.clicked(slotNum, packet.getButtonNum(), packet.getClickType(), this.player);
                                break;
                            case PICKUP_ALL:
                                click = ClickType.DOUBLE_CLICK;
                                action = InventoryAction.NOTHING;
                                if (slotNum >= 0 && !this.player.containerMenu.getCarried().isEmpty()) {
                                    ItemStack cursor = this.player.containerMenu.getCarried();
                                    action = InventoryAction.NOTHING;
                                    // Quick check for if we have any of the item
                                    if (inventory.getTopInventory().contains(CraftItemType.minecraftToBukkit(cursor.getItem())) || inventory.getBottomInventory().contains(CraftItemType.minecraftToBukkit(cursor.getItem()))) {
                                        action = InventoryAction.COLLECT_TO_CURSOR;
                                    }
                                }
                                break;
                            default:
                                break;
                    }

                    if (packet.getClickType() != net.minecraft.world.inventory.ClickType.QUICK_CRAFT) {
                        if (click == ClickType.NUMBER_KEY) {
                            event = new InventoryClickEvent(inventory, type, slotNum, click, action, packet.getButtonNum());
                        } else {
                            event = new InventoryClickEvent(inventory, type, slotNum, click, action);
                        }

                        org.bukkit.inventory.Inventory top = inventory.getTopInventory();
                        if (slotNum == 0 && top instanceof CraftingInventory) {
                            org.bukkit.inventory.Recipe recipe = ((CraftingInventory) top).getRecipe();
                            if (recipe != null) {
                                if (click == ClickType.NUMBER_KEY) {
                                    event = new CraftItemEvent(recipe, inventory, type, slotNum, click, action, packet.getButtonNum());
                                } else {
                                    event = new CraftItemEvent(recipe, inventory, type, slotNum, click, action);
                                }
                            }
                        }

                        if (slotNum == 3 && top instanceof SmithingInventory) {
                            org.bukkit.inventory.ItemStack result = ((SmithingInventory) top).getResult();
                            if (result != null) {
                                if (click == ClickType.NUMBER_KEY) {
                                    event = new SmithItemEvent(inventory, type, slotNum, click, action, packet.getButtonNum());
                                } else {
                                    event = new SmithItemEvent(inventory, type, slotNum, click, action);
                                }
                            }
                        }

                        // Paper start - cartography item event
                        if (slotNum == net.minecraft.world.inventory.CartographyTableMenu.RESULT_SLOT && top instanceof org.bukkit.inventory.CartographyInventory cartographyInventory) {
                            org.bukkit.inventory.ItemStack result = cartographyInventory.getResult();
                            if (result != null && !result.isEmpty()) {
                                if (click == ClickType.NUMBER_KEY) {
                                    event = new io.papermc.paper.event.player.CartographyItemEvent(inventory, type, slotNum, click, action, packet.getButtonNum());
                                } else {
                                    event = new io.papermc.paper.event.player.CartographyItemEvent(inventory, type, slotNum, click, action);
                                }
                            }
                        }
                        // Paper end - cartography item event

                        event.setCancelled(cancelled);
                        AbstractContainerMenu oldContainer = this.player.containerMenu; // SPIGOT-1224
                        this.cserver.getPluginManager().callEvent(event);
                        if (this.player.containerMenu != oldContainer) {
                            return;
                        }

                        switch (event.getResult()) {
                            case ALLOW:
                            case DEFAULT:
                                this.player.containerMenu.clicked(slotNum, packet.getButtonNum(), packet.getClickType(), this.player);
                                break;
                            case DENY:
                                /* Needs enum constructor in InventoryAction
                                if (action.modifiesOtherSlots()) {

                                } else {
                                    if (action.modifiesCursor()) {
                                        this.player.playerConnection.sendPacket(new Packet103SetSlot(-1, -1, this.player.inventory.getCarried()));
                                    }
                                    if (action.modifiesClicked()) {
                                        this.player.playerConnection.sendPacket(new Packet103SetSlot(this.player.activeContainer.windowId, packet102windowclick.slot, this.player.activeContainer.getSlot(packet102windowclick.slot).getItem()));
                                    }
                                }*/
                                switch (action) {
                                    // Modified other slots
                                    case PICKUP_ALL:
                                    case MOVE_TO_OTHER_INVENTORY:
                                    case HOTBAR_MOVE_AND_READD:
                                    case HOTBAR_SWAP:
                                    case COLLECT_TO_CURSOR:
                                    case UNKNOWN:
                                        this.player.containerMenu.sendAllDataToRemote();
                                        break;
                                    // Modified cursor and clicked
                                    case PICKUP_SOME:
                                    case PICKUP_HALF:
                                    case PICKUP_ONE:
                                    case PLACE_ALL:
                                    case PLACE_SOME:
                                    case PLACE_ONE:
                                    case SWAP_WITH_CURSOR:
                                        this.player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket(this.player.containerMenu.getCarried().copy())); // Paper - correctly set cursor
                                        this.player.connection.send(new ClientboundContainerSetSlotPacket(this.player.containerMenu.containerId, this.player.inventoryMenu.incrementStateId(), slotNum, this.player.containerMenu.getSlot(slotNum).getItem()));
                                        break;
                                    // Modified clicked only
                                    case DROP_ALL_SLOT:
                                    case DROP_ONE_SLOT:
                                        this.player.connection.send(new ClientboundContainerSetSlotPacket(this.player.containerMenu.containerId, this.player.inventoryMenu.incrementStateId(), slotNum, this.player.containerMenu.getSlot(slotNum).getItem()));
                                        break;
                                    // Modified cursor only
                                    case DROP_ALL_CURSOR:
                                    case DROP_ONE_CURSOR:
                                    case CLONE_STACK:
                                        this.player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket(this.player.containerMenu.getCarried().copy())); // Paper - correctly set cursor
                                        break;
                                    // Nothing
                                    case NOTHING:
                                        break;
                                }
                        }

                        if (event instanceof CraftItemEvent || event instanceof SmithItemEvent) {
                            // Need to update the inventory on crafting to
                            // correctly support custom recipes
                            this.player.containerMenu.sendAllDataToRemote();
                        }
                    }
                   // CraftBukkit end

                    for (Entry<ItemStack> entry : Int2ObjectMaps.fastIterable(packet.getChangedSlots())) {
                        this.player.containerMenu.setRemoteSlotNoCopy(entry.getIntKey(), entry.getValue());
                    }

                    this.player.containerMenu.setRemoteCarried(packet.getCarriedItem());
                    this.player.containerMenu.resumeRemoteUpdates();
                    if (flag) {
                        this.player.containerMenu.broadcastFullState();
                    } else {
                        this.player.containerMenu.broadcastChanges();
                    }
                }
            }
        }
    }

    @Override
    public void handlePlaceRecipe(ServerboundPlaceRecipePacket packet) {
        // Paper start - auto recipe limit
        if (!org.bukkit.Bukkit.isPrimaryThread()) {
            if (!this.recipeSpamPackets.isIncrementAndUnderThreshold()) {
                this.disconnectAsync(net.minecraft.network.chat.Component.translatable("disconnect.spam"), org.bukkit.event.player.PlayerKickEvent.Cause.SPAM); // Paper - kick event cause // Paper - add proper async disconnect
                return;
            }
        }
        // Paper end - auto recipe limit
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        this.player.resetLastActionTime();
        if (!this.player.isSpectator() && this.player.containerMenu.containerId == packet.containerId()) {
            if (!this.player.containerMenu.stillValid(this.player)) {
                LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
            } else {
                RecipeManager.ServerDisplayInfo recipeFromDisplay = this.server.getRecipeManager().getRecipeFromDisplay(packet.recipe());
                if (recipeFromDisplay != null) {
                    RecipeHolder<?> recipeHolder = recipeFromDisplay.parent();
                    if (this.player.getRecipeBook().contains(recipeHolder.id())) {
                        if (this.player.containerMenu instanceof RecipeBookMenu recipeBookMenu) {
                            if (recipeHolder.value().placementInfo().isImpossibleToPlace()) {
                                LOGGER.debug("Player {} tried to place impossible recipe {}", this.player, recipeHolder.id().location());
                                return;
                            }

                            // Paper start - Add PlayerRecipeBookClickEvent
                            NamespacedKey recipeName = org.bukkit.craftbukkit.util.CraftNamespacedKey.fromMinecraft(recipeHolder.id().location());
                            boolean makeAll = packet.useMaxItems();
                            com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent paperEvent = new com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent(
                                this.player.getBukkitEntity(), recipeName, makeAll
                            );
                            if (!paperEvent.callEvent()) {
                                return;
                            }
                            recipeName = paperEvent.getRecipe();
                            makeAll = paperEvent.isMakeAll();
                            if (org.bukkit.event.player.PlayerRecipeBookClickEvent.getHandlerList().getRegisteredListeners().length > 0) {
                                // Paper end - Add PlayerRecipeBookClickEvent
                                // CraftBukkit start - implement PlayerRecipeBookClickEvent
                                org.bukkit.inventory.Recipe recipe = this.cserver.getRecipe(recipeName); // Paper - Add PlayerRecipeBookClickEvent - forward to legacy event
                                if (recipe == null) {
                                    return;
                                }
                                // Paper start - Add PlayerRecipeBookClickEvent - forward to legacy event
                                org.bukkit.event.player.PlayerRecipeBookClickEvent event = CraftEventFactory.callRecipeBookClickEvent(this.player, recipe, makeAll);
                                recipeName = ((org.bukkit.Keyed) event.getRecipe()).getKey();
                                makeAll = event.isShiftClick();
                            }
                            if (!(this.player.containerMenu instanceof RecipeBookMenu)) {
                                return;
                            }
                            // Paper end - Add PlayerRecipeBookClickEvent - forward to legacy event

                            // Cast to keyed should be safe as the recipe will never be a MerchantRecipe.
                            recipeHolder = this.server.getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, org.bukkit.craftbukkit.util.CraftNamespacedKey.toMinecraft(recipeName))).orElse(null); // Paper - Add PlayerRecipeBookClickEvent - forward to legacy event
                            if (recipeHolder == null) {
                                return;
                            }

                            RecipeBookMenu.PostPlaceAction postPlaceAction = recipeBookMenu.handlePlacement(
                                makeAll, this.player.isCreative(), recipeHolder, this.player.serverLevel(), this.player.getInventory()
                            );
                            // CraftBukkit end
                            if (postPlaceAction == RecipeBookMenu.PostPlaceAction.PLACE_GHOST_RECIPE) {
                                this.player
                                    .connection
                                    .send(new ClientboundPlaceGhostRecipePacket(this.player.containerMenu.containerId, recipeFromDisplay.display().display()));
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void handleContainerButtonClick(ServerboundContainerButtonClickPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        if (this.player.containerMenu.containerId == packet.containerId() && !this.player.isSpectator()) {
            if (!this.player.containerMenu.stillValid(this.player)) {
                LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
            } else {
                boolean flag = this.player.containerMenu.clickMenuButton(this.player, packet.buttonId());
                if (flag) {
                    this.player.containerMenu.broadcastChanges();
                }
            }
        }
    }

    @Override
    public void handleSetCreativeModeSlot(ServerboundSetCreativeModeSlotPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.gameMode.isCreative()) {
            boolean flag = packet.slotNum() < 0;
            ItemStack itemStack = packet.itemStack();
            if (!itemStack.isItemEnabled(this.player.level().enabledFeatures())) {
                return;
            }

            boolean flag1 = packet.slotNum() >= 1 && packet.slotNum() <= 45;
            boolean flag2 = itemStack.isEmpty() || itemStack.getCount() <= itemStack.getMaxStackSize();
            if (flag || (flag1 && !ItemStack.matches(this.player.inventoryMenu.getSlot(packet.slotNum()).getItem(), packet.itemStack()))) { // Insist on valid slot
                // CraftBukkit start - Call click event
                InventoryView inventory = this.player.inventoryMenu.getBukkitView();
                org.bukkit.inventory.ItemStack item = CraftItemStack.asBukkitCopy(packet.itemStack());

                SlotType type = SlotType.QUICKBAR;
                if (flag) {
                    type = SlotType.OUTSIDE;
                } else if (packet.slotNum() < 36) {
                    if (packet.slotNum() >= 5 && packet.slotNum() < 9) {
                        type = SlotType.ARMOR;
                    } else {
                        type = SlotType.CONTAINER;
                    }
                }
                InventoryCreativeEvent event = new InventoryCreativeEvent(inventory, type, flag ? AbstractContainerMenu.SLOT_CLICKED_OUTSIDE : packet.slotNum(), item);
                this.cserver.getPluginManager().callEvent(event);

                itemStack = CraftItemStack.asNMSCopy(event.getCursor());

                switch (event.getResult()) {
                    case ALLOW:
                        // Plugin cleared the id / stacksize checks
                        flag2 = true;
                        break;
                    case DEFAULT:
                        break;
                    case DENY:
                        // Reset the slot
                        if (packet.slotNum() >= 0) {
                            this.player.connection.send(new ClientboundContainerSetSlotPacket(this.player.inventoryMenu.containerId, this.player.inventoryMenu.incrementStateId(), packet.slotNum(), this.player.inventoryMenu.getSlot(packet.slotNum()).getItem()));
                            this.player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket(ItemStack.EMPTY.copy())); // Paper - correctly set cursor
                        }
                        return;
                }
            }
            // CraftBukkit end
            if (flag1 && flag2) {
                this.player.inventoryMenu.getSlot(packet.slotNum()).setByPlayer(itemStack);
                this.player.inventoryMenu.setRemoteSlot(packet.slotNum(), itemStack);
                this.player.inventoryMenu.broadcastChanges();
            } else if (flag && flag2) {
                if (this.dropSpamThrottler.isUnderThreshold()) {
                    this.dropSpamThrottler.increment();
                    this.player.drop(itemStack, true);
                } else {
                    LOGGER.warn("Player {} was dropping items too fast in creative mode, ignoring.", this.player.getName().getString());
                }
            }
        }
    }

    @Override
    public void handleSignUpdate(ServerboundSignUpdatePacket packet) {
        // Paper start - Limit client sign length
        String[] lines = packet.getLines();
        for (int i = 0; i < lines.length; ++i) {
            if (MAX_SIGN_LINE_LENGTH > 0 && lines[i].length() > MAX_SIGN_LINE_LENGTH) {
                // This handles multibyte characters as 1
                int offset = lines[i].codePoints().limit(MAX_SIGN_LINE_LENGTH).map(Character::charCount).sum();
                if (offset < lines[i].length()) {
                    lines[i] = lines[i].substring(0, offset); // this will break any filtering, but filtering is NYI as of 1.17
                }
            }
        }
        List<String> list = Stream.of(lines).map(ChatFormatting::stripFormatting).collect(Collectors.toList());
        // Paper end - Limit client sign length
        this.filterTextPacket(list).thenAcceptAsync(list1 -> this.updateSignText(packet, (List<FilteredText>)list1), this.server);
    }

    private void updateSignText(ServerboundSignUpdatePacket packet, List<FilteredText> filteredText) {
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        ServerLevel serverLevel = this.player.serverLevel();
        BlockPos pos = packet.getPos();
        if (serverLevel.hasChunkAt(pos)) {
            if (!(serverLevel.getBlockEntity(pos) instanceof SignBlockEntity signBlockEntity)) {
                return;
            }

            signBlockEntity.updateSignText(this.player, packet.isFrontText(), filteredText);
        }
    }

    @Override
    public void handlePlayerAbilities(ServerboundPlayerAbilitiesPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        // CraftBukkit start
        if (this.player.getAbilities().mayfly && this.player.getAbilities().flying != packet.isFlying()) {
            PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(this.player.getBukkitEntity(), packet.isFlying());
            this.cserver.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                this.player.getAbilities().flying = packet.isFlying(); // Actually set the player's flying status
            } else {
                this.player.onUpdateAbilities(); // Tell the player their ability was reverted
            }
        }
        // CraftBukkit end
    }

    @Override
    public void handleClientInformation(ServerboundClientInformationPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        // Paper start - do not accept invalid information
        if (packet.information().viewDistance() < 0) {
            LOGGER.warn("Disconnecting {} for invalid view distance: {}", this.player.getScoreboardName(), packet.information().viewDistance());
            this.disconnect(Component.literal("Invalid client settings"), org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION);
            return;
        }
        // Paper end - do not accept invalid information
        boolean isModelPartShown = this.player.isModelPartShown(PlayerModelPart.HAT);
        this.player.updateOptions(packet.information());
        this.connection.channel.attr(io.papermc.paper.adventure.PaperAdventure.LOCALE_ATTRIBUTE).set(net.kyori.adventure.translation.Translator.parseLocale(packet.information().language())); // Paper
        if (this.player.isModelPartShown(PlayerModelPart.HAT) != isModelPartShown) {
            this.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT, this.player));
        }
    }

    @Override
    public void handleChangeDifficulty(ServerboundChangeDifficultyPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.hasPermissions(2) || this.isSingleplayerOwner()) {
            // this.server.setDifficulty(packet.getDifficulty(), false); // Paper - per level difficulty; don't allow clients to change this
        }
    }

    @Override
    public void handleLockDifficulty(ServerboundLockDifficultyPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.hasPermissions(2) || this.isSingleplayerOwner()) {
            this.server.setDifficultyLocked(packet.isLocked());
        }
    }

    @Override
    public void handleChatSessionUpdate(ServerboundChatSessionUpdatePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        RemoteChatSession.Data data = packet.chatSession();
        ProfilePublicKey.Data data1 = this.chatSession != null ? this.chatSession.profilePublicKey().data() : null;
        ProfilePublicKey.Data data2 = data.profilePublicKey();
        if (!Objects.equals(data1, data2)) {
            if (data1 != null && data2.expiresAt().isBefore(data1.expiresAt())) {
                this.disconnect(ProfilePublicKey.EXPIRED_PROFILE_PUBLIC_KEY, org.bukkit.event.player.PlayerKickEvent.Cause.EXPIRED_PROFILE_PUBLIC_KEY); // Paper - kick event causes
            } else {
                try {
                    SignatureValidator profileKeySignatureValidator = this.server.getProfileKeySignatureValidator();
                    if (profileKeySignatureValidator == null) {
                        LOGGER.warn("Ignoring chat session from {} due to missing Services public key", this.player.getGameProfile().getName());
                        return;
                    }

                    this.resetPlayerChatState(data.validate(this.player.getGameProfile(), profileKeySignatureValidator));
                } catch (ProfilePublicKey.ValidationException var6) {
                    // LOGGER.error("Failed to validate profile key: {}", var6.getMessage()); // Paper - Improve logging and errors
                    this.disconnect(var6.getComponent(), var6.kickCause); // Paper - kick event causes
                }
            }
        }
    }

    @Override
    public void handleConfigurationAcknowledged(ServerboundConfigurationAcknowledgedPacket packet) {
        if (!this.waitingForSwitchToConfig) {
            throw new IllegalStateException("Client acknowledged config, but none was requested");
        } else {
            this.connection
                .setupInboundProtocol(
                    ConfigurationProtocols.SERVERBOUND,
                    new ServerConfigurationPacketListenerImpl(this.server, this.connection, this.createCookie(this.player.clientInformation()), this.player) // CraftBukkit
                );
        }
    }

    @Override
    public void handleChunkBatchReceived(ServerboundChunkBatchReceivedPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        this.chunkSender.onChunkBatchReceivedByClient(packet.desiredChunksPerTick());
    }

    @Override
    public void handleDebugSampleSubscription(ServerboundDebugSampleSubscriptionPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        this.server.subscribeToDebugSample(this.player, packet.sampleType());
    }

    private void resetPlayerChatState(RemoteChatSession chatSession) {
        this.chatSession = chatSession;
        this.hasLoggedExpiry = false; // Paper - Prevent causing expired keys from impacting new joins
        this.signedMessageDecoder = chatSession.createMessageDecoder(this.player.getUUID());
        this.chatMessageChain
            .append(
                () -> {
                    this.player.setChatSession(chatSession);
                    this.server
                        .getPlayerList()
                        .broadcastAll(
                            new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT), List.of(this.player)), this.player // Paper - Use single player info update packet on join
                        );
                }
            );
    }

    // CraftBukkit start - handled in super
    // @Override
    // public void handleCustomPayload(ServerboundCustomPayloadPacket packet) {
    // }
    // CraftBukkit end

    @Override
    public void handleClientTickEnd(ServerboundClientTickEndPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (!this.receivedMovementThisTick) {
            this.player.setKnownMovement(Vec3.ZERO);
        }

        this.receivedMovementThisTick = false;
    }

    private void handlePlayerKnownMovement(Vec3 movement) {
        if (movement.lengthSqr() > 1.0E-5F) {
            this.player.resetLastActionTime();
        }

        this.player.setKnownMovement(movement);
        this.receivedMovementThisTick = true;
    }

    @Override
    public ServerPlayer getPlayer() {
        return this.player;
    }

    @FunctionalInterface
    interface EntityInteraction {
        InteractionResult run(ServerPlayer player, Entity entity, InteractionHand hand);
    }

    // Paper start - Add fail move event
    private io.papermc.paper.event.player.PlayerFailMoveEvent fireFailMove(io.papermc.paper.event.player.PlayerFailMoveEvent.FailReason failReason,
                                                                           double toX, double toY, double toZ, float toYaw, float toPitch, boolean logWarning) {
        org.bukkit.entity.Player player = this.getCraftPlayer();
        Location from = new Location(player.getWorld(), this.lastPosX, this.lastPosY, this.lastPosZ, this.lastYaw, this.lastPitch);
        Location to = new Location(player.getWorld(), toX, toY, toZ, toYaw, toPitch);
        io.papermc.paper.event.player.PlayerFailMoveEvent event = new io.papermc.paper.event.player.PlayerFailMoveEvent(player, failReason,
            false, logWarning, from, to);
        event.callEvent();
        return event;
    }
    // Paper end - Add fail move event
}
