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

public class ServerPlayer extends Player {
    private static final Logger LOGGER = LogUtils.getLogger();
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
    private int lastSentExp = -99999999;
    private ChatVisiblity chatVisibility = ChatVisiblity.FULL;
    private ParticleStatus particleStatus = ParticleStatus.ALL;
    private boolean canChatColor = true;
    private long lastActionTime = Util.getMillis();
    @Nullable
    private Entity camera;
    private boolean isChangingDimension;
    public boolean seenCredits;
    private final ServerRecipeBook recipeBook;
    @Nullable
    private Vec3 levitationStartPos;
    private int levitationStartTime;
    private boolean disconnected;
    private int requestedViewDistance = 2;
    private String language = "en_us";
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
    private WardenSpawnTracker wardenSpawnTracker = new WardenSpawnTracker(0, 0, 0);
    @Nullable
    private BlockPos raidOmenPosition;
    private Vec3 lastKnownClientMovement = Vec3.ZERO;
    private Input lastClientInput = Input.EMPTY;
    private final Set<ThrownEnderpearl> enderPearls = new HashSet<>();
    private final ContainerSynchronizer containerSynchronizer = new ContainerSynchronizer() {
        @Override
        public void sendInitialData(AbstractContainerMenu container, NonNullList<ItemStack> items, ItemStack carriedItem, int[] initialData) {
            ServerPlayer.this.connection
                .send(new ClientboundContainerSetContentPacket(container.containerId, container.incrementStateId(), items, carriedItem));

            for (int i = 0; i < initialData.length; i++) {
                this.broadcastDataValue(container, i, initialData[i]);
            }
        }

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
    };
    private int containerCounter;
    public boolean wonGame;

    public ServerPlayer(MinecraftServer server, ServerLevel level, GameProfile gameProfile, ClientInformation clientInformation) {
        super(level, level.getSharedSpawnPos(), level.getSharedSpawnAngle(), gameProfile);
        this.textFilter = server.createTextFilterForPlayer(this);
        this.gameMode = server.createGameModeForPlayer(this);
        this.recipeBook = new ServerRecipeBook((recipe, output) -> server.getRecipeManager().listDisplaysForRecipe(recipe, output));
        this.server = server;
        this.stats = server.getPlayerList().getPlayerStats(this);
        this.advancements = server.getPlayerList().getPlayerAdvancements(this);
        this.moveTo(this.adjustSpawnLocation(level, level.getSharedSpawnPos()).getBottomCenter(), 0.0F, 0.0F);
        this.updateOptions(clientInformation);
        this.object = null;
    }

    @Override
    public BlockPos adjustSpawnLocation(ServerLevel level, BlockPos pos) {
        AABB aabb = this.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO);
        BlockPos blockPos = pos;
        if (level.dimensionType().hasSkyLight() && level.getServer().getWorldData().getGameType() != GameType.ADVENTURE) {
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

        if (this.isSleeping()) {
            this.stopSleeping();
        }

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

        compound.putBoolean("spawn_extra_particles_on_fall", this.spawnExtraParticlesOnFall);
        if (this.raidOmenPosition != null) {
            BlockPos.CODEC
                .encodeStart(NbtOps.INSTANCE, this.raidOmenPosition)
                .resultOrPartial(LOGGER::error)
                .ifPresent(raidOmenPosition -> compound.put("raid_omen_position", raidOmenPosition));
        }

        this.saveEnderPearls(compound);
    }

    private void saveParentVehicle(CompoundTag tag) {
        Entity rootVehicle = this.getRootVehicle();
        Entity vehicle = this.getVehicle();
        if (vehicle != null && rootVehicle != this && rootVehicle.hasExactlyOnePlayerPassenger()) {
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
                compound.getCompound("Entity"), serverLevel, EntitySpawnReason.LOAD, entity2 -> !serverLevel.addWithUUID(entity2) ? null : entity2
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
                entity.discard();

                for (Entity entity1x : entity.getIndirectPassengers()) {
                    entity1x.discard();
                }
            }
        }
    }

    private void saveEnderPearls(CompoundTag tag) {
        if (!this.enderPearls.isEmpty()) {
            ListTag listTag = new ListTag();

            for (ThrownEnderpearl thrownEnderpearl : this.enderPearls) {
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

    private void initMenu(AbstractContainerMenu menu) {
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
        this.tickClientLoadTimeout();
        this.gameMode.tick();
        this.wardenSpawnTracker.tick();
        if (this.invulnerableTime > 0) {
            this.invulnerableTime--;
        }

        this.containerMenu.broadcastChanges();
        if (!this.containerMenu.stillValid(this)) {
            this.closeContainer();
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
            if (!this.isSpectator() || !this.touchingUnloadedChunk()) {
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
                this.connection.send(new ClientboundSetHealthPacket(this.getHealth(), this.foodData.getFoodLevel(), this.foodData.getSaturationLevel()));
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
                    this.heal(1.0F);
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
        this.getScoreboard().forAllObjectives(criteria, this, scoreAccess -> scoreAccess.set(points));
    }

    @Override
    public void die(DamageSource cause) {
        this.gameEvent(GameEvent.ENTITY_DIE);
        boolean _boolean = this.serverLevel().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES);
        if (_boolean) {
            Component deathMessage = this.getCombatTracker().getDeathMessage();
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

        if (!this.isSpectator()) {
            this.dropAllDeathLoot(this.serverLevel(), cause);
        }

        this.getScoreboard().forAllObjectives(ObjectiveCriteria.DEATH_COUNT, this, ScoreAccess::increment);
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
            this.getScoreboard().forAllObjectives(ObjectiveCriteria.KILL_COUNT_ALL, this, ScoreAccess::increment);
            if (entity instanceof Player) {
                this.awardStat(Stats.PLAYER_KILLS);
                this.getScoreboard().forAllObjectives(ObjectiveCriteria.KILL_COUNT_PLAYERS, this, ScoreAccess::increment);
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
                this.getScoreboard().forAllObjectives(crtieria[id], scoreHolder, ScoreAccess::increment);
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableTo(level, damageSource)) {
            return false;
        } else {
            Entity entity = damageSource.getEntity();
            return !(entity instanceof Player player && !this.canHarmPlayer(player))
                && !(entity instanceof AbstractArrow abstractArrow && abstractArrow.getOwner() instanceof Player player1 && !this.canHarmPlayer(player1))
                && super.hurtServer(level, damageSource, amount);
        }
    }

    @Override
    public boolean canHarmPlayer(Player other) {
        return this.isPvpAllowed() && super.canHarmPlayer(other);
    }

    private boolean isPvpAllowed() {
        return this.server.isPvpAllowed();
    }

    public TeleportTransition findRespawnPositionAndUseSpawnBlock(boolean useCharge, TeleportTransition.PostTeleportTransition postTeleportTransition) {
        BlockPos respawnPosition = this.getRespawnPosition();
        float respawnAngle = this.getRespawnAngle();
        boolean isRespawnForced = this.isRespawnForced();
        ServerLevel level = this.server.getLevel(this.getRespawnDimension());
        if (level != null && respawnPosition != null) {
            Optional<ServerPlayer.RespawnPosAngle> optional = findRespawnAndUseSpawnBlock(level, respawnPosition, respawnAngle, isRespawnForced, useCharge);
            if (optional.isPresent()) {
                ServerPlayer.RespawnPosAngle respawnPosAngle = optional.get();
                return new TeleportTransition(level, respawnPosAngle.position(), Vec3.ZERO, respawnPosAngle.yaw(), 0.0F, postTeleportTransition);
            } else {
                return TeleportTransition.missingRespawnBlock(this.server.overworld(), this, postTeleportTransition);
            }
        } else {
            return new TeleportTransition(this.server.overworld(), this, postTeleportTransition);
        }
    }

    private static Optional<ServerPlayer.RespawnPosAngle> findRespawnAndUseSpawnBlock(
        ServerLevel level, BlockPos pos, float angle, boolean forced, boolean useCharge
    ) {
        BlockState blockState = level.getBlockState(pos);
        Block block = blockState.getBlock();
        if (block instanceof RespawnAnchorBlock && (forced || blockState.getValue(RespawnAnchorBlock.CHARGE) > 0) && RespawnAnchorBlock.canSetSpawn(level)) {
            Optional<Vec3> optional = RespawnAnchorBlock.findStandUpPosition(EntityType.PLAYER, level, pos);
            if (!forced && useCharge && optional.isPresent()) {
                level.setBlock(pos, blockState.setValue(RespawnAnchorBlock.CHARGE, Integer.valueOf(blockState.getValue(RespawnAnchorBlock.CHARGE) - 1)), 3);
            }

            return optional.map(vec3 -> ServerPlayer.RespawnPosAngle.of(vec3, pos));
        } else if (block instanceof BedBlock && BedBlock.canSetSpawn(level)) {
            return BedBlock.findStandUpPosition(EntityType.PLAYER, level, pos, blockState.getValue(BedBlock.FACING), angle)
                .map(vec3 -> ServerPlayer.RespawnPosAngle.of(vec3, pos));
        } else if (!forced) {
            return Optional.empty();
        } else {
            boolean isPossibleToRespawnInThis = block.isPossibleToRespawnInThis(blockState);
            BlockState blockState1 = level.getBlockState(pos.above());
            boolean isPossibleToRespawnInThis1 = blockState1.getBlock().isPossibleToRespawnInThis(blockState1);
            return isPossibleToRespawnInThis && isPossibleToRespawnInThis1
                ? Optional.of(new ServerPlayer.RespawnPosAngle(new Vec3(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5), angle))
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
        if (this.isRemoved()) {
            return null;
        } else {
            if (teleportTransition.missingRespawnBlock()) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
            }

            ServerLevel level = teleportTransition.newLevel();
            ServerLevel serverLevel = this.serverLevel();
            ResourceKey<Level> resourceKey = serverLevel.dimension();
            if (!teleportTransition.asPassenger()) {
                this.stopRiding();
            }

            if (level.dimension() == resourceKey) {
                this.connection.teleport(PositionMoveRotation.of(teleportTransition), teleportTransition.relatives());
                this.connection.resetPosition();
                teleportTransition.postTeleportTransition().onTransition(this);
                return this;
            } else {
                this.isChangingDimension = true;
                LevelData levelData = level.getLevelData();
                this.connection.send(new ClientboundRespawnPacket(this.createCommonSpawnInfo(level), (byte)3));
                this.connection.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
                PlayerList playerList = this.server.getPlayerList();
                playerList.sendPlayerPermissionLevel(this);
                serverLevel.removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
                this.unsetRemoved();
                ProfilerFiller profilerFiller = Profiler.get();
                profilerFiller.push("moving");
                if (resourceKey == Level.OVERWORLD && level.dimension() == Level.NETHER) {
                    this.enteredNetherPosition = this.position();
                }

                profilerFiller.pop();
                profilerFiller.push("placing");
                this.setServerLevel(level);
                this.connection.teleport(PositionMoveRotation.of(teleportTransition), teleportTransition.relatives());
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
                return this;
            }
        }
    }

    @Override
    public void forceSetRotation(float yRot, float xRot) {
        this.connection.send(new ClientboundPlayerRotationPacket(yRot, xRot));
    }

    private void triggerDimensionChangeTriggers(ServerLevel level) {
        ResourceKey<Level> resourceKey = level.dimension();
        ResourceKey<Level> resourceKey1 = this.level().dimension();
        CriteriaTriggers.CHANGED_DIMENSION.trigger(this, resourceKey, resourceKey1);
        if (resourceKey == Level.NETHER && resourceKey1 == Level.OVERWORLD && this.enteredNetherPosition != null) {
            CriteriaTriggers.NETHER_TRAVEL.trigger(this, this.enteredNetherPosition);
        }

        if (resourceKey1 != Level.NETHER) {
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

    @Override
    public Either<Player.BedSleepingProblem, Unit> startSleepInBed(BlockPos at) {
        Direction direction = this.level().getBlockState(at).getValue(HorizontalDirectionalBlock.FACING);
        if (this.isSleeping() || !this.isAlive()) {
            return Either.left(Player.BedSleepingProblem.OTHER_PROBLEM);
        } else if (!this.level().dimensionType().natural()) {
            return Either.left(Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
        } else if (!this.bedInRange(at, direction)) {
            return Either.left(Player.BedSleepingProblem.TOO_FAR_AWAY);
        } else if (this.bedBlocked(at, direction)) {
            return Either.left(Player.BedSleepingProblem.OBSTRUCTED);
        } else {
            this.setRespawnPosition(this.level().dimension(), at, this.getYRot(), false, true);
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
                    if (!entitiesOfClass.isEmpty()) {
                        return Either.left(Player.BedSleepingProblem.NOT_SAFE);
                    }
                }

                Either<Player.BedSleepingProblem, Unit> either = super.startSleepInBed(at).ifRight(unit -> {
                    this.awardStat(Stats.SLEEP_IN_BED);
                    CriteriaTriggers.SLEPT_IN_BED.trigger(this);
                });
                if (!this.serverLevel().canSleepThroughNights()) {
                    this.displayClientMessage(Component.translatable("sleep.not_possible"), true);
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
        if (this.isSleeping()) {
            this.serverLevel().getChunkSource().broadcastAndSend(this, new ClientboundAnimatePacket(this, 2));
        }

        super.stopSleepInBed(wakeImmediately, updateLevelForSleepingPlayers);
        if (this.connection != null) {
            this.connection.teleport(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
        }
    }

    @Override
    public void dismountTo(double x, double y, double z) {
        this.removeVehicle();
        this.setPos(x, y, z);
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource damageSource) {
        return super.isInvulnerableTo(level, damageSource)
            || this.isChangingDimension() && !damageSource.is(DamageTypes.ENDER_PEARL)
            || !this.hasClientLoaded();
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
        this.connection.send(new ClientboundBlockUpdatePacket(this.level(), signEntity.getBlockPos()));
        this.connection.send(new ClientboundOpenSignEditorPacket(signEntity.getBlockPos(), isFrontText));
    }

    private void nextContainerCounter() {
        this.containerCounter = this.containerCounter % 100 + 1;
    }

    @Override
    public OptionalInt openMenu(@Nullable MenuProvider menu) {
        if (menu == null) {
            return OptionalInt.empty();
        } else {
            if (this.containerMenu != this.inventoryMenu) {
                this.closeContainer();
            }

            this.nextContainerCounter();
            AbstractContainerMenu abstractContainerMenu = menu.createMenu(this.containerCounter, this.getInventory(), this);
            if (abstractContainerMenu == null) {
                if (this.isSpectator()) {
                    this.displayClientMessage(Component.translatable("container.spectatorCantOpen").withStyle(ChatFormatting.RED), true);
                }

                return OptionalInt.empty();
            } else {
                this.connection
                    .send(new ClientboundOpenScreenPacket(abstractContainerMenu.containerId, abstractContainerMenu.getType(), menu.getDisplayName()));
                this.initMenu(abstractContainerMenu);
                this.containerMenu = abstractContainerMenu;
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
        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer();
        }

        this.nextContainerCounter();
        int inventoryColumns = horse.getInventoryColumns();
        this.connection.send(new ClientboundHorseScreenOpenPacket(this.containerCounter, inventoryColumns, horse.getId()));
        this.containerMenu = new HorseInventoryMenu(this.containerCounter, this.getInventory(), inventory, horse, inventoryColumns);
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
        this.connection.send(new ClientboundContainerClosePacket(this.containerMenu.containerId));
        this.doCloseContainer();
    }

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
                    this.causeFoodExhaustion(0.01F * rounded * 0.01F);
                }
            } else if (this.isEyeInFluid(FluidTags.WATER)) {
                int rounded = Math.round((float)Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F);
                if (rounded > 0) {
                    this.awardStat(Stats.WALK_UNDER_WATER_ONE_CM, rounded);
                    this.causeFoodExhaustion(0.01F * rounded * 0.01F);
                }
            } else if (this.isInWater()) {
                int rounded = Math.round((float)Math.sqrt(dx * dx + dz * dz) * 100.0F);
                if (rounded > 0) {
                    this.awardStat(Stats.WALK_ON_WATER_ONE_CM, rounded);
                    this.causeFoodExhaustion(0.01F * rounded * 0.01F);
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
                        this.causeFoodExhaustion(0.1F * rounded * 0.01F);
                    } else if (this.isCrouching()) {
                        this.awardStat(Stats.CROUCH_ONE_CM, rounded);
                        this.causeFoodExhaustion(0.0F * rounded * 0.01F);
                    } else {
                        this.awardStat(Stats.WALK_ONE_CM, rounded);
                        this.causeFoodExhaustion(0.0F * rounded * 0.01F);
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
        this.getScoreboard().forAllObjectives(stat, this, score -> score.add(amount));
    }

    @Override
    public void resetStat(Stat<?> stat) {
        this.stats.setValue(this, stat, 0);
        this.getScoreboard().forAllObjectives(stat, this, ScoreAccess::reset);
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
            this.causeFoodExhaustion(0.2F);
        } else {
            this.causeFoodExhaustion(0.05F);
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
        if (this.isSleeping()) {
            this.stopSleepInBed(true, false);
        }
    }

    public boolean hasDisconnected() {
        return this.disconnected;
    }

    public void resetSentInfo() {
        this.lastSentHealth = -1.0E8F;
    }

    @Override
    public void displayClientMessage(Component chatComponent, boolean actionBar) {
        this.sendSystemMessage(chatComponent, actionBar);
    }

    @Override
    protected void completeUsingItem() {
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
            this.getAttributes().assignPermanentModifiers(that.getAttributes());
            this.setHealth(that.getHealth());
            this.foodData = that.foodData;

            for (MobEffectInstance mobEffectInstance : that.getActiveEffects()) {
                this.addEffect(new MobEffectInstance(mobEffectInstance));
            }

            this.getInventory().replaceWith(that.getInventory());
            this.experienceLevel = that.experienceLevel;
            this.totalExperience = that.totalExperience;
            this.experienceProgress = that.experienceProgress;
            this.setScore(that.getScore());
            this.portalProcess = that.portalProcess;
        } else {
            this.getAttributes().assignBaseValues(that.getAttributes());
            this.setHealth(this.getMaxHealth());
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
        this.recipeBook.copyOverData(that.recipeBook);
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
    public boolean teleportTo(ServerLevel level, double x, double y, double z, Set<Relative> relativeMovements, float yaw, float pitch, boolean setCamera) {
        if (this.isSleeping()) {
            this.stopSleepInBed(true, true);
        }

        if (setCamera) {
            this.setCamera(this);
        }

        boolean flag = super.teleportTo(level, x, y, z, relativeMovements, yaw, pitch, setCamera);
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
        boolean isSpectator = this.isSpectator();
        if (!this.gameMode.changeGameModeForPlayer(gameMode)) {
            return false;
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
            return true;
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
        if (this.acceptsChatMessages()) {
            message.sendToPlayer(this, filtered, boundType);
        }
    }

    public String getIpAddress() {
        return this.connection.getRemoteAddress() instanceof InetSocketAddress inetSocketAddress
            ? InetAddresses.toAddrString(inetSocketAddress.getAddress())
            : "<unknown>";
    }

    public void updateOptions(ClientInformation clientInformation) {
        this.language = clientInformation.language();
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
    }

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
            if (this.camera.level() instanceof ServerLevel serverLevel) {
                this.teleportTo(serverLevel, this.camera.getX(), this.camera.getY(), this.camera.getZ(), Set.of(), this.getYRot(), this.getXRot(), false);
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
        return null;
    }

    public int getTabListOrder() {
        return 0;
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

    public void setRespawnPosition(ResourceKey<Level> dimension, @Nullable BlockPos position, float angle, boolean forced, boolean sendMessage) {
        if (position != null) {
            boolean flag = position.equals(this.respawnPosition) && dimension.equals(this.respawnDimension);
            if (sendMessage && !flag) {
                this.sendSystemMessage(Component.translatable("block.minecraft.set_spawn"));
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
    public ItemEntity drop(ItemStack droppedItem, boolean dropAround, boolean traceItem) {
        ItemEntity itemEntity = this.createItemStackToDrop(droppedItem, dropAround, traceItem);
        if (itemEntity == null) {
            return null;
        } else {
            this.level().addFreshEntity(itemEntity);
            ItemStack item = itemEntity.getItem();
            if (traceItem) {
                if (!item.isEmpty()) {
                    this.awardStat(Stats.ITEM_DROPPED.get(item.getItem()), droppedItem.getCount());
                }

                this.awardStat(Stats.DROP);
            }

            return itemEntity;
        }
    }

    @Nullable
    private ItemEntity createItemStackToDrop(ItemStack droppedItem, boolean dropAround, boolean includeThrowerName) {
        if (droppedItem.isEmpty()) {
            return null;
        } else {
            double d = this.getEyeY() - 0.3F;
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
        Entity vehicle = this.getVehicle();
        super.stopRiding();
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
        level.getChunkSource().addRegionTicket(TicketType.ENDER_PEARL, pos, 2, pos);
        return TicketType.ENDER_PEARL.timeout();
    }

    record RespawnPosAngle(Vec3 position, float yaw) {
        public static ServerPlayer.RespawnPosAngle of(Vec3 position, BlockPos towardsPos) {
            return new ServerPlayer.RespawnPosAngle(position, calculateLookAtYaw(position, towardsPos));
        }

        private static float calculateLookAtYaw(Vec3 position, BlockPos towardsPos) {
            Vec3 vec3 = Vec3.atBottomCenterOf(towardsPos).subtract(position).normalize();
            return (float)Mth.wrapDegrees(Mth.atan2(vec3.z, vec3.x) * 180.0F / (float)Math.PI - 90.0);
        }
    }
}
