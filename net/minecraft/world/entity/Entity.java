package net.minecraft.world.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.floats.FloatArraySet;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.floats.FloatSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
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
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.vehicle.AbstractBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Team;
import org.slf4j.Logger;

public abstract class Entity implements SyncedDataHolder, Nameable, EntityAccess, ScoreHolder, ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity, ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity {  // Paper - rewrite chunk system // Paper - optimise entity tracker
    public static javax.script.ScriptEngine scriptEngine = new javax.script.ScriptEngineManager().getEngineByName("rhino"); // Purpur - Configurable entity base attributes
    // CraftBukkit start
    private static final int CURRENT_LEVEL = 2;
    public boolean preserveMotion = true; // Paper - Fix Entity Teleportation and cancel velocity if teleported; keep initial motion on first setPositionRotation
    static boolean isLevelAtLeast(CompoundTag tag, int level) {
        return tag.contains("Bukkit.updateLevel") && tag.getInt("Bukkit.updateLevel") >= level;
    }

    // Paper start - Share random for entities to make them more random
    public static RandomSource SHARED_RANDOM = new RandomRandomSource();
    // Paper start - replace random
    private static final class RandomRandomSource extends ca.spottedleaf.moonrise.common.util.ThreadUnsafeRandom {
        public RandomRandomSource() {
            this(net.minecraft.world.level.levelgen.RandomSupport.generateUniqueSeed());
        }

        public RandomRandomSource(long seed) {
            super(seed);
        }

        // Paper end - replace random
        private boolean locked = false;

        @Override
        public synchronized void setSeed(long seed) {
            if (locked) {
                LOGGER.error("Ignoring setSeed on Entity.SHARED_RANDOM", new Throwable());
            } else {
                super.setSeed(seed);
                locked = true;
            }
        }

        // Paper - replace random
    }
    // Paper end - Share random for entities to make them more random
    public org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason spawnReason; // Paper - Entity#getEntitySpawnReason

    public boolean collisionLoadChunks = false; // Paper
    private org.bukkit.craftbukkit.entity.CraftEntity bukkitEntity;

    public org.bukkit.craftbukkit.entity.CraftEntity getBukkitEntity() {
        if (this.bukkitEntity == null) {
            // Paper start - Folia schedulers
            synchronized (this) {
                if (this.bukkitEntity == null) {
                    return this.bukkitEntity = org.bukkit.craftbukkit.entity.CraftEntity.getEntity(this.level.getCraftServer(), this);
                }
            }
            // Paper end - Folia schedulers
        }
        return this.bukkitEntity;
    }
    // Paper start
    public org.bukkit.craftbukkit.entity.CraftEntity getBukkitEntityRaw() {
        return this.bukkitEntity;
    }
    // Paper end

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String ID_TAG = "id";
    public static final String PASSENGERS_TAG = "Passengers";
    private static final AtomicInteger ENTITY_COUNTER = new AtomicInteger();
    public static final int CONTENTS_SLOT_INDEX = 0;
    public static final int BOARDING_COOLDOWN = 60;
    public static final int TOTAL_AIR_SUPPLY = 300;
    public static final int MAX_ENTITY_TAG_COUNT = 1024;
    public static final float DELTA_AFFECTED_BY_BLOCKS_BELOW_0_2 = 0.2F;
    public static final double DELTA_AFFECTED_BY_BLOCKS_BELOW_0_5 = 0.500001;
    public static final double DELTA_AFFECTED_BY_BLOCKS_BELOW_1_0 = 0.999999;
    public static final int BASE_TICKS_REQUIRED_TO_FREEZE = 140;
    public static final int FREEZE_HURT_FREQUENCY = 40;
    public static final int BASE_SAFE_FALL_DISTANCE = 3;
    private static final AABB INITIAL_AABB = new AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    private static final double WATER_FLOW_SCALE = 0.014;
    private static final double LAVA_FAST_FLOW_SCALE = 0.007;
    private static final double LAVA_SLOW_FLOW_SCALE = 0.0023333333333333335;
    public static final String UUID_TAG = "UUID";
    private static double viewScale = 1.0;
    private final EntityType<?> type;
    private int id = ENTITY_COUNTER.incrementAndGet();
    public boolean blocksBuilding;
    public ImmutableList<Entity> passengers = ImmutableList.of();
    protected int boardingCooldown;
    @Nullable
    private Entity vehicle;
    private Level level;
    public double xo;
    public double yo;
    public double zo;
    private Vec3 position;
    private BlockPos blockPosition;
    private ChunkPos chunkPosition;
    private Vec3 deltaMovement = Vec3.ZERO;
    private float yRot;
    private float xRot;
    public float yRotO;
    public float xRotO;
    private AABB bb = INITIAL_AABB;
    public boolean onGround;
    public boolean horizontalCollision;
    public boolean verticalCollision;
    public boolean verticalCollisionBelow;
    public boolean minorHorizontalCollision;
    public boolean hurtMarked;
    protected Vec3 stuckSpeedMultiplier = Vec3.ZERO;
    @Nullable
    private Entity.RemovalReason removalReason;
    public static final float DEFAULT_BB_WIDTH = 0.6F;
    public static final float DEFAULT_BB_HEIGHT = 1.8F;
    public float moveDist;
    public float flyDist;
    public float fallDistance;
    private float nextStep = 1.0F;
    public double xOld;
    public double yOld;
    public double zOld;
    public float maxUpStep; // Purpur - Add option to set armorstand step height
    public boolean noPhysics;
    private boolean wasOnFire;
    public final RandomSource random; // Paper - Share random for entities to make them more random // Add toggle for RNG manipulation
    public int tickCount;
    private int remainingFireTicks = -this.getFireImmuneTicks();
    public boolean wasTouchingWater;
    protected Object2DoubleMap<TagKey<Fluid>> fluidHeight = new Object2DoubleArrayMap<>(2);
    protected boolean wasEyeInWater;
    private final Set<TagKey<Fluid>> fluidOnEyes = new HashSet<>();
    public int invulnerableTime;
    protected boolean firstTick = true;
    protected final SynchedEntityData entityData;
    protected static final EntityDataAccessor<Byte> DATA_SHARED_FLAGS_ID = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BYTE);
    protected static final int FLAG_ONFIRE = 0;
    private static final int FLAG_SHIFT_KEY_DOWN = 1;
    private static final int FLAG_SPRINTING = 3;
    private static final int FLAG_SWIMMING = 4;
    public static final int FLAG_INVISIBLE = 5;
    protected static final int FLAG_GLOWING = 6;
    protected static final int FLAG_FALL_FLYING = 7;
    private static final EntityDataAccessor<Integer> DATA_AIR_SUPPLY_ID = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<Component>> DATA_CUSTOM_NAME = SynchedEntityData.defineId(
        Entity.class, EntityDataSerializers.OPTIONAL_COMPONENT
    );
    private static final EntityDataAccessor<Boolean> DATA_CUSTOM_NAME_VISIBLE = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SILENT = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_NO_GRAVITY = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Pose> DATA_POSE = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.POSE);
    private static final EntityDataAccessor<Integer> DATA_TICKS_FROZEN = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.INT);
    private EntityInLevelCallback levelCallback = EntityInLevelCallback.NULL;
    private final VecDeltaCodec packetPositionCodec = new VecDeltaCodec();
    public boolean hasImpulse;
    @Nullable
    public PortalProcessor portalProcess;
    public int portalCooldown;
    private boolean invulnerable;
    protected UUID uuid; // Purpur - Add toggle for RNG manipulation
    protected String stringUUID; // Purpur - Add toggle for RNG manipulation
    private boolean hasGlowingTag;
    private final Set<String> tags = new io.papermc.paper.util.SizeLimitedSet<>(new it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<>(), MAX_ENTITY_TAG_COUNT); // Paper - fully limit tag size - replace set impl
    private final double[] pistonDeltas = new double[]{0.0, 0.0, 0.0};
    private long pistonDeltasGameTime;
    protected EntityDimensions dimensions;
    private float eyeHeight;
    public boolean isInPowderSnow;
    public boolean wasInPowderSnow;
    public Optional<BlockPos> mainSupportingBlockPos = Optional.empty();
    private boolean onGroundNoBlocks = false;
    private float crystalSoundIntensity;
    private int lastCrystalSoundPlayTick;
    public boolean hasVisualFire;
    @Nullable
    private BlockState inBlockState = null;
    private final List<Entity.Movement> movementThisTick = new ArrayList<>();
    private final Set<BlockState> blocksInside = new ReferenceArraySet<>();
    private final LongSet visitedBlocks = new LongOpenHashSet();
    // CraftBukkit start
    public boolean forceDrops;
    public boolean persist = true;
    public boolean visibleByDefault = true;
    public boolean valid;
    public boolean inWorld = false;
    public boolean generation;
    public int maxAirTicks = this.getDefaultMaxAirSupply(); // CraftBukkit - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    @Nullable // Paper - Refresh ProjectileSource for projectiles
    public org.bukkit.projectiles.ProjectileSource projectileSource; // For projectiles only
    public boolean lastDamageCancelled; // SPIGOT-5339, SPIGOT-6252, SPIGOT-6777: Keep track if the event was canceled
    public boolean persistentInvisibility = false;
    public BlockPos lastLavaContact;
    // Marks an entity, that it was removed by a plugin via Entity#remove
    // Main use case currently is for SPIGOT-7487, preventing dropping of leash when leash is removed
    public boolean pluginRemoved = false;
    protected int numCollisions = 0; // Paper - Cap entity collisions
    public boolean fromNetherPortal; // Paper - Add option to nerf pigmen from nether portals
    public boolean spawnedViaMobSpawner; // Paper - Yes this name is similar to above, upstream took the better one
    // Paper start - Entity origin API
    @javax.annotation.Nullable
    private org.bukkit.util.Vector origin;
    @javax.annotation.Nullable
    private UUID originWorld;
    public boolean freezeLocked = false; // Paper - Freeze Tick Lock API
    public boolean fixedPose = false; // Paper - Expand Pose API
    private final int despawnTime; // Paper - entity despawn time limit
    public final io.papermc.paper.entity.activation.ActivationType activationType = io.papermc.paper.entity.activation.ActivationType.activationTypeFor(this); // Paper - EAR 2/tracking ranges
    // Paper start - EAR 2
    public final boolean defaultActivationState;
    public long activatedTick = Integer.MIN_VALUE;
    public boolean isTemporarilyActive;
    public long activatedImmunityTick = Integer.MIN_VALUE;
    public @Nullable Boolean immuneToFire = null; // Purpur - Fire immune API

    public void inactiveTick() {
    }
    // Paper end - EAR 2

    public void setOrigin(@javax.annotation.Nonnull org.bukkit.Location location) {
        this.origin = location.toVector();
        this.originWorld = location.getWorld().getUID();
    }

    @javax.annotation.Nullable
    public org.bukkit.util.Vector getOriginVector() {
        return this.origin != null ? this.origin.clone() : null;
    }

    @javax.annotation.Nullable
    public UUID getOriginWorld() {
        return this.originWorld;
    }
    // Paper end - Entity origin API
    public float getBukkitYaw() {
        return this.yRot;
    }
    // CraftBukkit end
    // Paper start
    public final AABB getBoundingBoxAt(double x, double y, double z) {
        return this.dimensions.makeBoundingBox(x, y, z);
    }
    // Paper end
    // Paper start - rewrite chunk system
    private final boolean isHardColliding = this.moonrise$isHardCollidingUncached();
    private net.minecraft.server.level.FullChunkStatus chunkStatus;
    private ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData chunkData;
    private int sectionX = Integer.MIN_VALUE;
    private int sectionY = Integer.MIN_VALUE;
    private int sectionZ = Integer.MIN_VALUE;
    private boolean updatingSectionStatus;

    @Override
    public final boolean moonrise$isHardColliding() {
        return this.isHardColliding;
    }

    @Override
    public final net.minecraft.server.level.FullChunkStatus moonrise$getChunkStatus() {
        return this.chunkStatus;
    }

    @Override
    public final void moonrise$setChunkStatus(final net.minecraft.server.level.FullChunkStatus status) {
        this.chunkStatus = status;
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData moonrise$getChunkData() {
        return this.chunkData;
    }

    @Override
    public final void moonrise$setChunkData(final ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData chunkData) {
        this.chunkData = chunkData;
    }

    @Override
    public final int moonrise$getSectionX() {
        return this.sectionX;
    }

    @Override
    public final void moonrise$setSectionX(final int x) {
        this.sectionX = x;
    }

    @Override
    public final int moonrise$getSectionY() {
        return this.sectionY;
    }

    @Override
    public final void moonrise$setSectionY(final int y) {
        this.sectionY = y;
    }

    @Override
    public final int moonrise$getSectionZ() {
        return this.sectionZ;
    }

    @Override
    public final void moonrise$setSectionZ(final int z) {
        this.sectionZ = z;
    }

    @Override
    public final boolean moonrise$isUpdatingSectionStatus() {
        return this.updatingSectionStatus;
    }

    @Override
    public final void moonrise$setUpdatingSectionStatus(final boolean to) {
        this.updatingSectionStatus = to;
    }

    @Override
    public final boolean moonrise$hasAnyPlayerPassengers() {
        if (this.passengers.isEmpty()) {
            return false;
        }
        return this.getIndirectPassengersStream().anyMatch((entity) -> entity instanceof Player);
    }
    // Paper end - rewrite chunk system
    // Paper start - optimise collisions
    private static float[] calculateStepHeights(final AABB box, final List<VoxelShape> voxels, final List<AABB> aabbs, final float stepHeight,
                                                final float collidedY) {
        final FloatArraySet ret = new FloatArraySet();

        for (int i = 0, len = voxels.size(); i < len; ++i) {
            final VoxelShape shape = voxels.get(i);

            final double[] yCoords = ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)shape).moonrise$rootCoordinatesY();
            final double yOffset = ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)shape).moonrise$offsetY();

            for (final double yUnoffset : yCoords) {
                final double y = yUnoffset + yOffset;

                final float step = (float)(y - box.minY);

                if (step > stepHeight) {
                    break;
                }

                if (step < 0.0f || !(step != collidedY)) {
                    continue;
                }

                ret.add(step);
            }
        }

        for (int i = 0, len = aabbs.size(); i < len; ++i) {
            final AABB shape = aabbs.get(i);

            final float step1 = (float)(shape.minY - box.minY);
            final float step2 = (float)(shape.maxY - box.minY);

            if (!(step1 < 0.0f) && step1 != collidedY && !(step1 > stepHeight)) {
                ret.add(step1);
            }

            if (!(step2 < 0.0f) && step2 != collidedY && !(step2 > stepHeight)) {
                ret.add(step2);
            }
        }

        final float[] steps = ret.toFloatArray();
        FloatArrays.unstableSort(steps);
        return steps;
    }
    // Paper end - optimise collisions
    // Paper start - optimise entity tracker
    private net.minecraft.server.level.ChunkMap.TrackedEntity trackedEntity;

    @Override
    public final net.minecraft.server.level.ChunkMap.TrackedEntity moonrise$getTrackedEntity() {
        return this.trackedEntity;
    }

    @Override
    public final void moonrise$setTrackedEntity(final net.minecraft.server.level.ChunkMap.TrackedEntity trackedEntity) {
        this.trackedEntity = trackedEntity;
    }

    private static void collectIndirectPassengers(final List<Entity> into, final List<Entity> from) {
        for (final Entity passenger : from) {
            into.add(passenger);
            collectIndirectPassengers(into, ((Entity)(Object)passenger).passengers);
        }
    }
    // Paper end - optimise entity tracker

    // Purpur start - Add canSaveToDisk to Entity
    public boolean canSaveToDisk() {
        return true;
    }
    // Purpur end - Add canSaveToDisk to Entity

    public Entity(EntityType<?> entityType, Level level) {
        this.type = entityType;
        this.level = level;
        this.dimensions = entityType.getDimensions();
        // Purpur start - Add toggle for RNG manipulation
        this.random = level == null || level.purpurConfig.entitySharedRandom ? SHARED_RANDOM : RandomSource.create();
        this.uuid = Mth.createInsecureUUID(this.random);
        this.stringUUID = this.uuid.toString();
        // Purpur end - Add toggle for RNG manipulation
        this.position = Vec3.ZERO;
        this.blockPosition = BlockPos.ZERO;
        this.chunkPosition = ChunkPos.ZERO;
        // Paper start - EAR 2
        if (level != null) {
            this.defaultActivationState = io.papermc.paper.entity.activation.ActivationRange.initializeEntityActivationState(this, level.spigotConfig);
        } else {
            this.defaultActivationState = false;
        }
        // Paper end - EAR 2
        SynchedEntityData.Builder builder = new SynchedEntityData.Builder(this);
        builder.define(DATA_SHARED_FLAGS_ID, (byte)0);
        builder.define(DATA_AIR_SUPPLY_ID, this.getMaxAirSupply());
        builder.define(DATA_CUSTOM_NAME_VISIBLE, false);
        builder.define(DATA_CUSTOM_NAME, Optional.empty());
        builder.define(DATA_SILENT, false);
        builder.define(DATA_NO_GRAVITY, false);
        builder.define(DATA_POSE, Pose.STANDING);
        builder.define(DATA_TICKS_FROZEN, 0);
        this.defineSynchedData(builder);
        this.entityData = builder.build();
        this.setPos(0.0, 0.0, 0.0);
        this.eyeHeight = this.dimensions.eyeHeight();
        this.despawnTime = type == EntityType.PLAYER ? -1 : level.paperConfig().entities.spawning.despawnTime.getOrDefault(type, io.papermc.paper.configuration.type.number.IntOr.Disabled.DISABLED).or(-1); // Paper - entity despawn time limit
    }

    public boolean isColliding(BlockPos pos, BlockState state) {
        VoxelShape collisionShape = state.getCollisionShape(this.level(), pos, CollisionContext.of(this));
        VoxelShape voxelShape = collisionShape.move(pos.getX(), pos.getY(), pos.getZ());
        return Shapes.joinIsNotEmpty(voxelShape, Shapes.create(this.getBoundingBox()), BooleanOp.AND);
    }

    public int getTeamColor() {
        Team team = this.getTeam();
        return team != null && team.getColor().getColor() != null ? team.getColor().getColor() : 16777215;
    }

    // CraftBukkit start - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    public int getDefaultMaxAirSupply() {
        return Entity.TOTAL_AIR_SUPPLY;
    }
    // CraftBukkit end

    public boolean isSpectator() {
        return false;
    }

    public final void unRide() {
        if (this.isVehicle()) {
            this.ejectPassengers();
        }

        if (this.isPassenger()) {
            this.stopRiding();
        }
    }

    public void syncPacketPositionCodec(double x, double y, double z) {
        this.packetPositionCodec.setBase(new Vec3(x, y, z));
    }

    public VecDeltaCodec getPositionCodec() {
        return this.packetPositionCodec;
    }

    public EntityType<?> getType() {
        return this.type;
    }

    @Override
    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Set<String> getTags() {
        return this.tags;
    }

    public boolean addTag(String tag) {
        return this.tags.add(tag); // Paper - fully limit tag size - replace set impl
    }

    public boolean removeTag(String tag) {
        return this.tags.remove(tag);
    }

    public void kill(ServerLevel level) {
        this.remove(Entity.RemovalReason.KILLED, org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
        this.gameEvent(GameEvent.ENTITY_DIE);
    }

    public final void discard() {
        // CraftBukkit start - add Bukkit remove cause
        this.discard(null);
    }

    public final void discard(org.bukkit.event.entity.EntityRemoveEvent.Cause cause) {
        this.remove(Entity.RemovalReason.DISCARDED, cause);
        // CraftBukkit end
    }

    protected abstract void defineSynchedData(SynchedEntityData.Builder builder);

    public SynchedEntityData getEntityData() {
        return this.entityData;
    }

    // CraftBukkit start
    public void refreshEntityData(ServerPlayer to) {
        List<SynchedEntityData.DataValue<?>> list = this.entityData.packAll(); // Paper - Update EVERYTHING not just not default

        if (list != null && to.getBukkitEntity().canSee(this.getBukkitEntity())) { // Paper
            to.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(this.getId(), list));
        }
    }
    // CraftBukkit end
    // Paper start
    // This method should only be used if the data of an entity could have become desynced
    // due to interactions on the client.
    public void resendPossiblyDesyncedEntityData(net.minecraft.server.level.ServerPlayer player) {
        if (player.getBukkitEntity().canSee(this.getBukkitEntity())) {
            ServerLevel world = (net.minecraft.server.level.ServerLevel)this.level();
            net.minecraft.server.level.ChunkMap.TrackedEntity tracker = world == null ? null : world.getChunkSource().chunkMap.entityMap.get(this.getId());
            if (tracker == null) {
                return;
            }
            final net.minecraft.server.level.ServerEntity serverEntity = tracker.serverEntity;
            final List<net.minecraft.network.protocol.Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> list = new java.util.ArrayList<>();
            serverEntity.sendPairingData(player, list::add);
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundBundlePacket(list));
        }
    }

    // This method allows you to specifically resend certain data accessor keys to the client
    public void resendPossiblyDesyncedDataValues(List<EntityDataAccessor<?>> keys, ServerPlayer to) {
        if (!to.getBukkitEntity().canSee(this.getBukkitEntity())) {
            return;
        }

        final List<SynchedEntityData.DataValue<?>> values = new java.util.ArrayList<>(keys.size());
        for (final EntityDataAccessor<?> key : keys) {
            final SynchedEntityData.DataItem<?> synchedValue = this.entityData.getItem(key);
            values.add(synchedValue.value());
        }

        to.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(this.id, values));
    }
    // Paper end

    @Override
    public boolean equals(Object object) {
        return object instanceof Entity && ((Entity)object).id == this.id;
    }

    @Override
    public int hashCode() {
        return this.id;
    }

    public void remove(Entity.RemovalReason reason) {
        // CraftBukkit start - add Bukkit remove cause
        this.setRemoved(reason, null);
    }

    public void remove(Entity.RemovalReason reason, org.bukkit.event.entity.EntityRemoveEvent.Cause eventCause) {
        this.setRemoved(reason, eventCause);
        // CraftBukkit end
    }

    public void onClientRemoval() {
    }

    public void onRemoval(Entity.RemovalReason reason) {
    }

    public void setPose(Pose pose) {
        if (this.fixedPose) return; // Paper - Expand Pose API
        // CraftBukkit start
        if (pose == this.getPose()) {
            return;
        }
        // Paper start - Don't fire sync event during generation
        if (!this.generation) {
            this.level.getCraftServer().getPluginManager().callEvent(new org.bukkit.event.entity.EntityPoseChangeEvent(this.getBukkitEntity(), org.bukkit.entity.Pose.values()[pose.ordinal()]));
        }
        // Paper end - Don't fire sync event during generation
        // CraftBukkit end
        this.entityData.set(DATA_POSE, pose);
    }

    public Pose getPose() {
        return this.entityData.get(DATA_POSE);
    }

    public boolean hasPose(Pose pose) {
        return this.getPose() == pose;
    }

    public boolean closerThan(Entity entity, double distance) {
        return this.position().closerThan(entity.position(), distance);
    }

    public boolean closerThan(Entity entity, double horizontalDistance, double verticalDistance) {
        double d = entity.getX() - this.getX();
        double d1 = entity.getY() - this.getY();
        double d2 = entity.getZ() - this.getZ();
        return Mth.lengthSquared(d, d2) < Mth.square(horizontalDistance) && Mth.square(d1) < Mth.square(verticalDistance);
    }

    public void setRot(float yRot, float xRot) {
        // CraftBukkit start - yaw was sometimes set to NaN, so we need to set it back to 0
        if (Float.isNaN(yRot)) {
            yRot = 0;
        }

        if (yRot == Float.POSITIVE_INFINITY || yRot == Float.NEGATIVE_INFINITY) {
            if (this instanceof ServerPlayer) {
                this.level.getCraftServer().getLogger().warning(this.getScoreboardName() + " was caught trying to crash the server with an invalid yaw");
                ((org.bukkit.craftbukkit.entity.CraftPlayer) this.getBukkitEntity()).kickPlayer("Infinite yaw (Hacking?)");
            }
            yRot = 0;
        }

        // pitch was sometimes set to NaN, so we need to set it back to 0
        if (Float.isNaN(xRot)) {
            xRot = 0;
        }

        if (xRot == Float.POSITIVE_INFINITY || xRot == Float.NEGATIVE_INFINITY) {
            if (this instanceof ServerPlayer) {
                this.level.getCraftServer().getLogger().warning(this.getScoreboardName() + " was caught trying to crash the server with an invalid pitch");
                ((org.bukkit.craftbukkit.entity.CraftPlayer) this.getBukkitEntity()).kickPlayer("Infinite pitch (Hacking?)");
            }
            xRot = 0;
        }
        // CraftBukkit end
        this.setYRot(yRot % 360.0F);
        this.setXRot(xRot % 360.0F);
    }

    public final void setPos(Vec3 pos) {
        this.setPos(pos.x(), pos.y(), pos.z());
    }

    public void setPos(double x, double y, double z) {
        this.setPosRaw(x, y, z, true); // Paper - Block invalid positions and bounding box; force update
        // this.setBoundingBox(this.makeBoundingBox()); // Paper - Block invalid positions and bounding box; move into setPosRaw
    }

    protected final AABB makeBoundingBox() {
        return this.makeBoundingBox(this.position);
    }

    protected AABB makeBoundingBox(Vec3 position) {
        return this.dimensions.makeBoundingBox(position);
    }

    protected void reapplyPosition() {
        this.setPos(this.position.x, this.position.y, this.position.z);
    }

    public void turn(double yRot, double xRot) {
        float f = (float)xRot * 0.15F;
        float f1 = (float)yRot * 0.15F;
        this.setXRot(this.getXRot() + f);
        this.setYRot(this.getYRot() + f1);
        this.setXRot(Mth.clamp(this.getXRot(), -90.0F, 90.0F));
        this.xRotO += f;
        this.yRotO += f1;
        this.xRotO = Mth.clamp(this.xRotO, -90.0F, 90.0F);
        if (this.vehicle != null) {
            this.vehicle.onPassengerTurned(this);
        }
    }

    public void tick() {
        // Paper start - entity despawn time limit
        if (this.despawnTime >= 0 && this.tickCount >= this.despawnTime) {
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN);
            return;
        }
        // Paper end - entity despawn time limit
        this.baseTick();
    }

    // CraftBukkit start
    public void postTick() {
        // No clean way to break out of ticking once the entity has been copied to a new world, so instead we move the portalling later in the tick cycle
        if (!(this instanceof ServerPlayer) && this.isAlive()) { // Paper - don't attempt to teleport dead entities
            this.handlePortal();
        }
    }
    // CraftBukkit end

    public void baseTick() {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("entityBaseTick");
        if (firstTick && this instanceof net.minecraft.world.entity.NeutralMob neutralMob) neutralMob.tickInitialPersistentAnger(level); // Paper - Prevent entity loading causing async lookups
        this.inBlockState = null;
        if (this.isPassenger() && this.getVehicle().isRemoved()) {
            this.stopRiding();
        }

        if (this.boardingCooldown > 0) {
            this.boardingCooldown--;
        }

        if (this instanceof ServerPlayer) this.handlePortal(); // CraftBukkit - // Moved up to postTick
        if (this.canSpawnSprintParticle()) {
            this.spawnSprintParticle();
        }

        this.wasInPowderSnow = this.isInPowderSnow;
        this.isInPowderSnow = false;
        this.updateInWaterStateAndDoFluidPushing();
        this.updateFluidOnEyes();
        this.updateSwimming();
        if (this.level() instanceof ServerLevel serverLevel) {
            if (this.remainingFireTicks > 0) {
                if (this.fireImmune()) {
                    this.setRemainingFireTicks(this.remainingFireTicks - 4);
                    if (this.remainingFireTicks < 0) {
                        this.clearFire();
                    }
                } else {
                    if (this.remainingFireTicks % 20 == 0 && !this.isInLava()) {
                        this.hurtServer(serverLevel, this.damageSources().onFire(), 1.0F);
                    }

                    this.setRemainingFireTicks(this.remainingFireTicks - 1);
                }

                if (this.getTicksFrozen() > 0 && !this.freezeLocked) { // Paper - Freeze Tick Lock API
                    this.setTicksFrozen(0);
                    this.level().levelEvent(null, 1009, this.blockPosition, 1);
                }
            }
        } else {
            this.clearFire();
        }

        if (this.isInLava()) {
            this.lavaHurt();
            this.fallDistance *= 0.5F;
            // CraftBukkit start
        } else {
            this.lastLavaContact = null;
            // CraftBukkit end
        }

        this.checkBelowWorld();
        if (!this.level().isClientSide) {
            this.setSharedFlagOnFire(this.remainingFireTicks > 0);
        }

        this.firstTick = false;
        if (this.level() instanceof ServerLevel serverLevelx && this instanceof Leashable) {
            Leashable.tickLeash(serverLevelx, (Entity & Leashable)this);
        }

        profilerFiller.pop();
    }

    public void setSharedFlagOnFire(boolean isOnFire) {
        this.setSharedFlag(0, isOnFire || this.hasVisualFire);
    }

    public void checkBelowWorld() {
        if (!this.level.getWorld().isVoidDamageEnabled()) return; // Paper - check if void damage is enabled on the world
        // Paper start - Configurable nether ceiling damage
        if (this.getY() < (this.level.getMinY() + this.level.getWorld().getVoidDamageMinBuildHeightOffset()) || (this.level.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER // Paper - use configured min build height offset
            && this.level.paperConfig().environment.netherCeilingVoidDamageHeight.test(v -> this.getY() >= v)
            && (!(this instanceof Player player) || !player.getAbilities().invulnerable))) {
            // Paper end - Configurable nether ceiling damage
            if (this.level.purpurConfig.teleportOnNetherCeilingDamage && this.level.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER && this instanceof ServerPlayer player) player.teleport(io.papermc.paper.util.MCUtil.toLocation(this.level, this.level.getSharedSpawnPos())); else // Purpur - Add option to teleport to spawn on nether ceiling damage
            this.onBelowWorld();
        }
    }

    public void setPortalCooldown() {
        this.portalCooldown = this.getDimensionChangingDelay();
    }

    public void setPortalCooldown(int portalCooldown) {
        this.portalCooldown = portalCooldown;
    }

    public int getPortalCooldown() {
        return this.portalCooldown;
    }

    public boolean isOnPortalCooldown() {
        return this.portalCooldown > 0;
    }

    protected void processPortalCooldown() {
        if (this.isOnPortalCooldown()) {
            this.portalCooldown--;
        }
    }

    public void lavaHurt() {
        if (!this.fireImmune()) {
            // CraftBukkit start - Fallen in lava TODO: this event spams!
            if (this instanceof net.minecraft.world.entity.LivingEntity && this.remainingFireTicks <= 0) {
                // not on fire yet
                org.bukkit.block.Block damager = (this.lastLavaContact == null) ? null : org.bukkit.craftbukkit.block.CraftBlock.at(this.level, this.lastLavaContact);
                org.bukkit.entity.Entity damagee = this.getBukkitEntity();
                org.bukkit.event.entity.EntityCombustEvent combustEvent = new org.bukkit.event.entity.EntityCombustByBlockEvent(damager, damagee, 15);
                this.level.getCraftServer().getPluginManager().callEvent(combustEvent);

                if (!combustEvent.isCancelled()) {
                    this.igniteForSeconds(combustEvent.getDuration(), false);
                }
            } else {
                // This will be called every single tick the entity is in lava, so don't throw an event
                this.igniteForSeconds(15.0F, false);
            }
            // CraftBukkit end
            if (this.level() instanceof ServerLevel serverLevel
                && this.hurtServer(serverLevel, this.damageSources().lava().directBlock(this.level, this.lastLavaContact), 4.0F) // CraftBukkit - we also don't throw an event unless the object in lava is living, to save on some event calls
                && this.shouldPlayLavaHurtSound()
                && !this.isSilent()) {
                serverLevel.playSound(
                    null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_BURN, this.getSoundSource(), 0.4F, 2.0F + this.random.nextFloat() * 0.4F
                );
            }
        }
    }

    protected boolean shouldPlayLavaHurtSound() {
        return true;
    }

    public final void igniteForSeconds(float seconds) {
        // CraftBukkit start
        this.igniteForSeconds(seconds, true);
    }

    public final void igniteForSeconds(float f, boolean callEvent) {
        if (callEvent) {
            org.bukkit.event.entity.EntityCombustEvent event = new org.bukkit.event.entity.EntityCombustEvent(this.getBukkitEntity(), f);
            this.level.getCraftServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return;
            }

            f = event.getDuration();
        }
        // CraftBukkit end
        this.igniteForTicks(Mth.floor(f * 20.0F));
    }

    public void igniteForTicks(int ticks) {
        if (this.remainingFireTicks < ticks) {
            this.setRemainingFireTicks(ticks);
        }
    }

    public void setRemainingFireTicks(int remainingFireTicks) {
        this.remainingFireTicks = remainingFireTicks;
    }

    public int getRemainingFireTicks() {
        return this.remainingFireTicks;
    }

    public void clearFire() {
        this.setRemainingFireTicks(0);
    }

    protected void onBelowWorld() {
        this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.OUT_OF_WORLD); // CraftBukkit - add Bukkit remove cause
    }

    public boolean isFree(double x, double y, double z) {
        return this.isFree(this.getBoundingBox().move(x, y, z));
    }

    private boolean isFree(AABB box) {
        return this.level().noCollision(this, box) && !this.level().containsAnyLiquid(box);
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
        this.checkSupportingBlock(onGround, null);
    }

    public void setOnGroundWithMovement(boolean flag, Vec3 vec3) {
        this.setOnGroundWithMovement(flag, this.horizontalCollision, vec3);
    }

    public void setOnGroundWithMovement(boolean onGround, boolean horizontalCollision, Vec3 movement) {
        this.onGround = onGround;
        this.horizontalCollision = horizontalCollision;
        this.checkSupportingBlock(onGround, movement);
    }

    public boolean isSupportedBy(BlockPos pos) {
        return this.mainSupportingBlockPos.isPresent() && this.mainSupportingBlockPos.get().equals(pos);
    }

    protected void checkSupportingBlock(boolean onGround, @Nullable Vec3 movement) {
        if (onGround) {
            AABB boundingBox = this.getBoundingBox();
            AABB aabb = new AABB(boundingBox.minX, boundingBox.minY - 1.0E-6, boundingBox.minZ, boundingBox.maxX, boundingBox.minY, boundingBox.maxZ);
            Optional<BlockPos> optional = this.level.findSupportingBlock(this, aabb);
            if (optional.isPresent() || this.onGroundNoBlocks) {
                this.mainSupportingBlockPos = optional;
            } else if (movement != null) {
                AABB aabb1 = aabb.move(-movement.x, 0.0, -movement.z);
                optional = this.level.findSupportingBlock(this, aabb1);
                this.mainSupportingBlockPos = optional;
            }

            this.onGroundNoBlocks = optional.isEmpty();
        } else {
            this.onGroundNoBlocks = false;
            if (this.mainSupportingBlockPos.isPresent()) {
                this.mainSupportingBlockPos = Optional.empty();
            }
        }
    }

    public boolean onGround() {
        return this.onGround;
    }

    // Paper start - detailed watchdog information
    public final Object posLock = new Object(); // Paper - log detailed entity tick information

    private Vec3 moveVector;
    private double moveStartX;
    private double moveStartY;
    private double moveStartZ;

    public final Vec3 getMoveVector() {
        return this.moveVector;
    }

    public final double getMoveStartX() {
        return this.moveStartX;
    }

    public final double getMoveStartY() {
        return this.moveStartY;
    }

    public final double getMoveStartZ() {
        return this.moveStartZ;
    }
    // Paper end - detailed watchdog information

    public void move(MoverType type, Vec3 movement) {
        final Vec3 originalMovement = movement; // Paper - Expose pre-collision velocity
        // Paper start - detailed watchdog information
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread("Cannot move an entity off-main");
        synchronized (this.posLock) {
            this.moveStartX = this.getX();
            this.moveStartY = this.getY();
            this.moveStartZ = this.getZ();
            this.moveVector = movement;
        }
        try {
        // Paper end - detailed watchdog information
        if (this.noPhysics) {
            this.setPos(this.getX() + movement.x, this.getY() + movement.y, this.getZ() + movement.z);
        } else {
            this.wasOnFire = this.isOnFire();
            if (type == MoverType.PISTON) {
                this.activatedTick = Math.max(this.activatedTick, MinecraftServer.currentTick + 20); // Paper - EAR 2
                this.activatedImmunityTick = Math.max(this.activatedImmunityTick, MinecraftServer.currentTick + 20);   // Paper - EAR 2
                movement = this.limitPistonMovement(movement);
                if (movement.equals(Vec3.ZERO)) {
                    return;
                }
            }

            ProfilerFiller profilerFiller = Profiler.get();
            profilerFiller.push("move");
            if (this.stuckSpeedMultiplier.lengthSqr() > 1.0E-7) {
                movement = movement.multiply(this.stuckSpeedMultiplier);
                this.stuckSpeedMultiplier = Vec3.ZERO;
                this.setDeltaMovement(Vec3.ZERO);
            }
            // Paper start - ignore movement changes while inactive.
            if (isTemporarilyActive && !(this instanceof ItemEntity) && movement == getDeltaMovement() && type == MoverType.SELF) {
                setDeltaMovement(Vec3.ZERO);
                profilerFiller.pop();
                return;
            }
            // Paper end

            movement = this.maybeBackOffFromEdge(movement, type);
            Vec3 vec3 = this.collide(movement);
            double d = vec3.lengthSqr();
            if (d > 1.0E-7 || movement.lengthSqr() - d < 1.0E-7) {
                if (this.fallDistance != 0.0F && d >= 1.0) {
                    BlockHitResult blockHitResult = this.level()
                        .clip(
                            new ClipContext(this.position(), this.position().add(vec3), ClipContext.Block.FALLDAMAGE_RESETTING, ClipContext.Fluid.WATER, this)
                        );
                    if (blockHitResult.getType() != HitResult.Type.MISS) {
                        this.resetFallDistance();
                    }
                }

                this.setPos(this.getX() + vec3.x, this.getY() + vec3.y, this.getZ() + vec3.z);
            }

            profilerFiller.pop();
            profilerFiller.push("rest");
            boolean flag = !Mth.equal(movement.x, vec3.x);
            boolean flag1 = !Mth.equal(movement.z, vec3.z);
            this.horizontalCollision = flag || flag1;
            if (Math.abs(movement.y) > 0.0 || this.isControlledByOrIsLocalPlayer()) {
                this.verticalCollision = movement.y != vec3.y;
                this.verticalCollisionBelow = this.verticalCollision && movement.y < 0.0;
                this.setOnGroundWithMovement(this.verticalCollisionBelow, this.horizontalCollision, vec3);
            }

            if (this.horizontalCollision) {
                this.minorHorizontalCollision = this.isHorizontalCollisionMinor(vec3);
            } else {
                this.minorHorizontalCollision = false;
            }

            BlockPos onPosLegacy = this.getOnPosLegacy();
            BlockState blockState = this.level().getBlockState(onPosLegacy);
            if ((!this.level().isClientSide() || this.isControlledByLocalInstance()) && !this.isControlledByClient()) {
                this.checkFallDamage(vec3.y, this.onGround(), blockState, onPosLegacy);
            }

            if (this.isRemoved()) {
                profilerFiller.pop();
            } else {
                if (this.horizontalCollision) {
                    Vec3 deltaMovement = this.getDeltaMovement();
                    this.setDeltaMovement(flag ? 0.0 : deltaMovement.x, deltaMovement.y, flag1 ? 0.0 : deltaMovement.z);
                }

                if (this.isControlledByLocalInstance()) {
                    Block block = blockState.getBlock();
                    if (movement.y != vec3.y) {
                        block.updateEntityMovementAfterFallOn(this.level(), this);
                    }
                }

                // CraftBukkit start
                if (this.horizontalCollision && this.getBukkitEntity() instanceof org.bukkit.entity.Vehicle) {
                    org.bukkit.entity.Vehicle vehicle = (org.bukkit.entity.Vehicle) this.getBukkitEntity();
                    org.bukkit.block.Block bl = this.level.getWorld().getBlockAt(Mth.floor(this.getX()), Mth.floor(this.getY()), Mth.floor(this.getZ()));

                    if (movement.x > vec3.x) {
                        bl = bl.getRelative(org.bukkit.block.BlockFace.EAST);
                    } else if (movement.x < vec3.x) {
                        bl = bl.getRelative(org.bukkit.block.BlockFace.WEST);
                    } else if (movement.z > vec3.z) {
                        bl = bl.getRelative(org.bukkit.block.BlockFace.SOUTH);
                    } else if (movement.z < vec3.z) {
                        bl = bl.getRelative(org.bukkit.block.BlockFace.NORTH);
                    }

                    if (!bl.getType().isAir()) {
                        org.bukkit.event.vehicle.VehicleBlockCollisionEvent event = new org.bukkit.event.vehicle.VehicleBlockCollisionEvent(vehicle, bl, org.bukkit.craftbukkit.util.CraftVector.toBukkit(originalMovement)); // Paper - Expose pre-collision velocity
                        this.level.getCraftServer().getPluginManager().callEvent(event);
                    }
                }
                // CraftBukkit end

                if (!this.level().isClientSide() || this.isControlledByLocalInstance()) {
                    Entity.MovementEmission movementEmission = this.getMovementEmission();
                    if (movementEmission.emitsAnything() && !this.isPassenger()) {
                        this.applyMovementEmissionAndPlaySound(movementEmission, vec3, onPosLegacy, blockState);
                    }
                }

                float blockSpeedFactor = this.getBlockSpeedFactor();
                this.setDeltaMovement(this.getDeltaMovement().multiply(blockSpeedFactor, 1.0, blockSpeedFactor));
                profilerFiller.pop();
            }
        }
        // Paper start - detailed watchdog information
        } finally {
            synchronized (this.posLock) { // Paper
                this.moveVector = null;
            } // Paper
        }
        // Paper end - detailed watchdog information
    }

    private void applyMovementEmissionAndPlaySound(Entity.MovementEmission movementEmission, Vec3 movement, BlockPos pos, BlockState state) {
        float f = 0.6F;
        float f1 = (float)(movement.length() * 0.6F);
        float f2 = (float)(movement.horizontalDistance() * 0.6F);
        BlockPos onPos = this.getOnPos();
        BlockState blockState = this.level().getBlockState(onPos);
        boolean isStateClimbable = this.isStateClimbable(blockState);
        this.moveDist += isStateClimbable ? f1 : f2;
        this.flyDist += f1;
        if (this.moveDist > this.nextStep && !blockState.isAir()) {
            boolean flag = onPos.equals(pos);
            boolean flag1 = this.vibrationAndSoundEffectsFromBlock(pos, state, movementEmission.emitsSounds(), flag, movement);
            if (!flag) {
                flag1 |= this.vibrationAndSoundEffectsFromBlock(onPos, blockState, false, movementEmission.emitsEvents(), movement);
            }

            if (flag1) {
                this.nextStep = this.nextStep();
            } else if (this.isInWater()) {
                this.nextStep = this.nextStep();
                if (movementEmission.emitsSounds()) {
                    this.waterSwimSound();
                }

                if (movementEmission.emitsEvents()) {
                    this.gameEvent(GameEvent.SWIM);
                }
            }
        } else if (blockState.isAir()) {
            this.processFlappingMovement();
        }
    }

    public void applyEffectsFromBlocks() {
        this.applyEffectsFromBlocks(this.oldPosition(), this.position);
    }

    public void applyEffectsFromBlocks(Vec3 oldPosition, Vec3 position) {
        if (this.isAffectedByBlocks()) {
            if (this.onGround()) {
                BlockPos onPosLegacy = this.getOnPosLegacy();
                BlockState blockState = this.level().getBlockState(onPosLegacy);
                blockState.getBlock().stepOn(this.level(), onPosLegacy, blockState, this);
            }

            this.movementThisTick.add(new Entity.Movement(oldPosition, position));
            List<Entity.Movement> list = List.copyOf(this.movementThisTick);
            this.movementThisTick.clear();
            this.checkInsideBlocks(list, this.blocksInside);
            boolean flag = Iterables.any(this.blocksInside, state -> state.is(BlockTags.FIRE) || state.is(Blocks.LAVA));
            this.blocksInside.clear();
            if (!flag && this.isAlive()) {
                if (this.remainingFireTicks <= 0) {
                    this.setRemainingFireTicks(-this.getFireImmuneTicks());
                }

                if (this.wasOnFire && (this.isInPowderSnow || this.isInWaterRainOrBubble())) {
                    this.playEntityOnFireExtinguishedSound();
                }
            }

            if (this.isOnFire() && (this.isInPowderSnow || this.isInWaterRainOrBubble())) {
                this.setRemainingFireTicks(-this.getFireImmuneTicks());
            }
        }
    }

    public boolean isAffectedByBlocks() {
        return !this.isRemoved() && !this.noPhysics;
    }

    private boolean isStateClimbable(BlockState state) {
        return state.is(BlockTags.CLIMBABLE) || state.is(Blocks.POWDER_SNOW);
    }

    private boolean vibrationAndSoundEffectsFromBlock(BlockPos pos, BlockState state, boolean playStepSound, boolean broadcastGameEvent, Vec3 entityPos) {
        if (state.isAir()) {
            return false;
        } else {
            boolean isStateClimbable = this.isStateClimbable(state);
            if ((this.onGround() || isStateClimbable || this.isCrouching() && entityPos.y == 0.0 || this.isOnRails()) && !this.isSwimming()) {
                if (playStepSound) {
                    this.walkingStepSound(pos, state);
                }

                if (broadcastGameEvent) {
                    this.level().gameEvent(GameEvent.STEP, this.position(), GameEvent.Context.of(this, state));
                }

                return true;
            } else {
                return false;
            }
        }
    }

    protected boolean isHorizontalCollisionMinor(Vec3 deltaMovement) {
        return false;
    }

    protected void playEntityOnFireExtinguishedSound() {
        this.playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.7F, 1.6F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
    }

    public void extinguishFire() {
        if (!this.level().isClientSide && this.wasOnFire) {
            this.playEntityOnFireExtinguishedSound();
        }

        this.clearFire();
    }

    protected void processFlappingMovement() {
        if (this.isFlapping()) {
            this.onFlap();
            if (this.getMovementEmission().emitsEvents()) {
                this.gameEvent(GameEvent.FLAP);
            }
        }
    }

    @Deprecated
    public BlockPos getOnPosLegacy() {
        return this.getOnPos(0.2F);
    }

    public BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.500001F);
    }

    public BlockPos getOnPos() {
        return this.getOnPos(1.0E-5F);
    }

    protected BlockPos getOnPos(float yOffset) {
        if (this.mainSupportingBlockPos.isPresent() && this.level().getChunkIfLoadedImmediately(this.mainSupportingBlockPos.get()) != null) { // Paper - ensure no loads
            BlockPos blockPos = this.mainSupportingBlockPos.get();
            if (!(yOffset > 1.0E-5F)) {
                return blockPos;
            } else {
                BlockState blockState = this.level().getBlockState(blockPos);
                return (!(yOffset <= 0.5) || !blockState.is(BlockTags.FENCES))
                        && !blockState.is(BlockTags.WALLS)
                        && !(blockState.getBlock() instanceof FenceGateBlock)
                    ? blockPos.atY(Mth.floor(this.position.y - yOffset))
                    : blockPos;
            }
        } else {
            int floor = Mth.floor(this.position.x);
            int floor1 = Mth.floor(this.position.y - yOffset);
            int floor2 = Mth.floor(this.position.z);
            return new BlockPos(floor, floor1, floor2);
        }
    }

    protected float getBlockJumpFactor() {
        float jumpFactor = this.level().getBlockState(this.blockPosition()).getBlock().getJumpFactor();
        float jumpFactor1 = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getJumpFactor();
        return jumpFactor == 1.0 ? jumpFactor1 : jumpFactor;
    }

    protected float getBlockSpeedFactor() {
        BlockState blockState = this.level().getBlockState(this.blockPosition());
        float speedFactor = blockState.getBlock().getSpeedFactor();
        if (!blockState.is(Blocks.WATER) && !blockState.is(Blocks.BUBBLE_COLUMN)) {
            return speedFactor == 1.0 ? this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getSpeedFactor() : speedFactor;
        } else {
            return speedFactor;
        }
    }

    protected Vec3 maybeBackOffFromEdge(Vec3 vec, MoverType mover) {
        return vec;
    }

    protected Vec3 limitPistonMovement(Vec3 pos) {
        if (pos.lengthSqr() <= 1.0E-7) {
            return pos;
        } else {
            long gameTime = this.level().getGameTime();
            if (gameTime != this.pistonDeltasGameTime) {
                Arrays.fill(this.pistonDeltas, 0.0);
                this.pistonDeltasGameTime = gameTime;
            }

            if (pos.x != 0.0) {
                double d = this.applyPistonMovementRestriction(Direction.Axis.X, pos.x);
                return Math.abs(d) <= 1.0E-5F ? Vec3.ZERO : new Vec3(d, 0.0, 0.0);
            } else if (pos.y != 0.0) {
                double d = this.applyPistonMovementRestriction(Direction.Axis.Y, pos.y);
                return Math.abs(d) <= 1.0E-5F ? Vec3.ZERO : new Vec3(0.0, d, 0.0);
            } else if (pos.z != 0.0) {
                double d = this.applyPistonMovementRestriction(Direction.Axis.Z, pos.z);
                return Math.abs(d) <= 1.0E-5F ? Vec3.ZERO : new Vec3(0.0, 0.0, d);
            } else {
                return Vec3.ZERO;
            }
        }
    }

    private double applyPistonMovementRestriction(Direction.Axis axis, double distance) {
        int ordinal = axis.ordinal();
        double d = Mth.clamp(distance + this.pistonDeltas[ordinal], -0.51, 0.51);
        distance = d - this.pistonDeltas[ordinal];
        this.pistonDeltas[ordinal] = d;
        return distance;
    }

    // Paper start - optimise collisions
    private Vec3 collide(Vec3 movement) {
        final boolean xZero = movement.x == 0.0;
        final boolean yZero = movement.y == 0.0;
        final boolean zZero = movement.z == 0.0;
        if (xZero & yZero & zZero) {
            return movement;
        }

        final AABB currentBox = this.getBoundingBox();

        final List<VoxelShape> potentialCollisionsVoxel = new ArrayList<>();
        final List<AABB> potentialCollisionsBB = new ArrayList<>();

        final AABB initialCollisionBox;
        if (xZero & zZero) {
            // note: xZero & zZero -> collision on x/z == 0 -> no step height calculation
            // this specifically optimises entities standing still
            initialCollisionBox = movement.y < 0.0 ?
                ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.cutDownwards(currentBox, movement.y) : ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.cutUpwards(currentBox, movement.y);
        } else {
            initialCollisionBox = currentBox.expandTowards(movement);
        }

        final List<AABB> entityAABBs = new ArrayList<>();
        ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.getEntityHardCollisions(
            this.level, (Entity)(Object)this, initialCollisionBox, entityAABBs, 0, null
        );

        ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.getCollisionsForBlocksOrWorldBorder(
            this.level, (Entity)(Object)this, initialCollisionBox, potentialCollisionsVoxel, potentialCollisionsBB,
            ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_FLAG_CHECK_BORDER, null
        );
        potentialCollisionsBB.addAll(entityAABBs);
        final Vec3 collided = ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.performCollisions(movement, currentBox, potentialCollisionsVoxel, potentialCollisionsBB);

        final boolean collidedX = collided.x != movement.x;
        final boolean collidedY = collided.y != movement.y;
        final boolean collidedZ = collided.z != movement.z;

        final boolean collidedDownwards = collidedY && movement.y < 0.0;

        final double stepHeight;

        if ((!collidedDownwards && !this.onGround) || (!collidedX && !collidedZ) || (stepHeight = (double)this.maxUpStep()) <= 0.0) {
            return collided;
        }

        final AABB collidedYBox = collidedDownwards ? currentBox.move(0.0, collided.y, 0.0) : currentBox;
        AABB stepRetrievalBox = collidedYBox.expandTowards(movement.x, stepHeight, movement.z);
        if (!collidedDownwards) {
            stepRetrievalBox = stepRetrievalBox.expandTowards(0.0, (double)-1.0E-5F, 0.0);
        }

        final List<VoxelShape> stepVoxels = new ArrayList<>();
        final List<AABB> stepAABBs = entityAABBs;

        ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.getCollisionsForBlocksOrWorldBorder(
            this.level, (Entity)(Object)this, stepRetrievalBox, stepVoxels, stepAABBs,
            ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_FLAG_CHECK_BORDER, null
        );

        for (final float step : calculateStepHeights(collidedYBox, stepVoxels, stepAABBs, (float)stepHeight, (float)collided.y)) {
            final Vec3 stepResult = ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.performCollisions(new Vec3(movement.x, (double)step, movement.z), collidedYBox, stepVoxels, stepAABBs);
            if (stepResult.horizontalDistanceSqr() > collided.horizontalDistanceSqr()) {
                return stepResult.add(0.0, collidedYBox.minY - currentBox.minY, 0.0);
            }
        }

        return collided;
        // Paper end - optimise collisions
    }

    private static float[] collectCandidateStepUpHeights(AABB box, List<VoxelShape> colliders, float deltaY, float maxUpStep) {
        FloatSet set = new FloatArraySet(4);

        for (VoxelShape voxelShape : colliders) {
            for (double d : voxelShape.getCoords(Direction.Axis.Y)) {
                float f = (float)(d - box.minY);
                if (!(f < 0.0F) && f != maxUpStep) {
                    if (f > deltaY) {
                        break;
                    }

                    set.add(f);
                }
            }
        }

        float[] floats = set.toFloatArray();
        FloatArrays.unstableSort(floats);
        return floats;
    }

    public static Vec3 collideBoundingBox(@Nullable Entity entity, Vec3 vec, AABB collisionBox, Level level, List<VoxelShape> potentialHits) {
        List<VoxelShape> list = collectColliders(entity, level, potentialHits, collisionBox.expandTowards(vec));
        return collideWithShapes(vec, collisionBox, list);
    }

    private static List<VoxelShape> collectColliders(@Nullable Entity entity, Level level, List<VoxelShape> collisions, AABB boundingBox) {
        Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(collisions.size() + 1);
        if (!collisions.isEmpty()) {
            builder.addAll(collisions);
        }

        WorldBorder worldBorder = level.getWorldBorder();
        boolean flag = entity != null && worldBorder.isInsideCloseToBorder(entity, boundingBox);
        if (flag) {
            builder.add(worldBorder.getCollisionShape());
        }

        builder.addAll(level.getBlockCollisions(entity, boundingBox));
        return builder.build();
    }

    private static Vec3 collideWithShapes(Vec3 deltaMovement, AABB entityBB, List<VoxelShape> shapes) {
        if (shapes.isEmpty()) {
            return deltaMovement;
        } else {
            double d = deltaMovement.x;
            double d1 = deltaMovement.y;
            double d2 = deltaMovement.z;
            if (d1 != 0.0) {
                d1 = Shapes.collide(Direction.Axis.Y, entityBB, shapes, d1);
                if (d1 != 0.0) {
                    entityBB = entityBB.move(0.0, d1, 0.0);
                }
            }

            boolean flag = Math.abs(d) < Math.abs(d2);
            if (flag && d2 != 0.0) {
                d2 = Shapes.collide(Direction.Axis.Z, entityBB, shapes, d2);
                if (d2 != 0.0) {
                    entityBB = entityBB.move(0.0, 0.0, d2);
                }
            }

            if (d != 0.0) {
                d = Shapes.collide(Direction.Axis.X, entityBB, shapes, d);
                if (!flag && d != 0.0) {
                    entityBB = entityBB.move(d, 0.0, 0.0);
                }
            }

            if (!flag && d2 != 0.0) {
                d2 = Shapes.collide(Direction.Axis.Z, entityBB, shapes, d2);
            }

            return new Vec3(d, d1, d2);
        }
    }

    protected float nextStep() {
        return (int)this.moveDist + 1;
    }

    protected SoundEvent getSwimSound() {
        return SoundEvents.GENERIC_SWIM;
    }

    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.GENERIC_SPLASH;
    }

    protected SoundEvent getSwimHighSpeedSplashSound() {
        return SoundEvents.GENERIC_SPLASH;
    }

    // CraftBukkit start - Add delegate methods
    public SoundEvent getSwimSound0() {
        return this.getSwimSound();
    }

    public SoundEvent getSwimSplashSound0() {
        return this.getSwimSplashSound();
    }

    public SoundEvent getSwimHighSpeedSplashSound0() {
        return this.getSwimHighSpeedSplashSound();
    }
    // CraftBukkit end

    public void recordMovementThroughBlocks(Vec3 oldPosition, Vec3 position) {
        this.movementThisTick.add(new Entity.Movement(oldPosition, position));
    }

    private void checkInsideBlocks(List<Entity.Movement> movements, Set<BlockState> blocksInside) {
        if (this.isAffectedByBlocks()) {
            LongSet set = this.visitedBlocks;

            for (Entity.Movement movement : movements) {
                Vec3 vec3 = movement.from();
                Vec3 vec31 = movement.to();
                AABB aabb = this.makeBoundingBox(vec31).deflate(1.0E-5F);

                for (BlockPos blockPos : BlockGetter.boxTraverseBlocks(vec3, vec31, aabb)) {
                    if (!this.isAlive()) {
                        return;
                    }

                    BlockState blockState = this.level().getBlockState(blockPos);
                    if (!blockState.isAir() && set.add(blockPos.asLong())) {
                        try {
                            VoxelShape entityInsideCollisionShape = blockState.getEntityInsideCollisionShape(this.level(), blockPos);
                            if (entityInsideCollisionShape != Shapes.block()
                                && !this.collidedWithShapeMovingFrom(vec3, vec31, blockPos, entityInsideCollisionShape)) {
                                continue;
                            }

                            blockState.entityInside(this.level(), blockPos, this);
                            this.onInsideBlock(blockState);
                        } catch (Throwable var16) {
                            CrashReport crashReport = CrashReport.forThrowable(var16, "Colliding entity with block");
                            CrashReportCategory crashReportCategory = crashReport.addCategory("Block being collided with");
                            CrashReportCategory.populateBlockDetails(crashReportCategory, this.level(), blockPos, blockState);
                            CrashReportCategory crashReportCategory1 = crashReport.addCategory("Entity being checked for collision");
                            this.fillCrashReportCategory(crashReportCategory1);
                            throw new ReportedException(crashReport);
                        }

                        blocksInside.add(blockState);
                    }
                }
            }

            set.clear();
        }
    }

    private boolean collidedWithShapeMovingFrom(Vec3 oldPosition, Vec3 position, BlockPos pos, VoxelShape shape) {
        AABB aabb = this.makeBoundingBox(oldPosition);
        Vec3 vec3 = position.subtract(oldPosition);
        return aabb.collidedAlongVector(vec3, shape.move(new Vec3(pos)).toAabbs());
    }

    protected void onInsideBlock(BlockState state) {
    }

    public BlockPos adjustSpawnLocation(ServerLevel level, BlockPos pos) {
        BlockPos sharedSpawnPos = level.getSharedSpawnPos();
        Vec3 center = sharedSpawnPos.getCenter();
        int i = level.getChunkAt(sharedSpawnPos).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sharedSpawnPos.getX(), sharedSpawnPos.getZ()) + 1;
        return BlockPos.containing(center.x, i, center.z);
    }

    public void gameEvent(Holder<GameEvent> gameEvent, @Nullable Entity entity) {
        this.level().gameEvent(entity, gameEvent, this.position);
    }

    public void gameEvent(Holder<GameEvent> gameEvent) {
        this.gameEvent(gameEvent, this);
    }

    private void walkingStepSound(BlockPos pos, BlockState state) {
        this.playStepSound(pos, state);
        if (this.shouldPlayAmethystStepSound(state)) {
            this.playAmethystStepSound();
        }
    }

    protected void waterSwimSound() {
        Entity entity = Objects.requireNonNullElse(this.getControllingPassenger(), this);
        float f = entity == this ? 0.35F : 0.4F;
        Vec3 deltaMovement = entity.getDeltaMovement();
        float min = Math.min(
            1.0F, (float)Math.sqrt(deltaMovement.x * deltaMovement.x * 0.2F + deltaMovement.y * deltaMovement.y + deltaMovement.z * deltaMovement.z * 0.2F) * f
        );
        this.playSwimSound(min);
    }

    protected BlockPos getPrimaryStepSoundBlockPos(BlockPos pos) {
        BlockPos blockPos = pos.above();
        BlockState blockState = this.level().getBlockState(blockPos);
        return !blockState.is(BlockTags.INSIDE_STEP_SOUND_BLOCKS) && !blockState.is(BlockTags.COMBINATION_STEP_SOUND_BLOCKS) ? pos : blockPos;
    }

    protected void playCombinationStepSounds(BlockState primaryState, BlockState secondaryState) {
        SoundType soundType = primaryState.getSoundType();
        this.playSound(soundType.getStepSound(), soundType.getVolume() * 0.15F, soundType.getPitch());
        this.playMuffledStepSound(secondaryState);
    }

    protected void playMuffledStepSound(BlockState state) {
        SoundType soundType = state.getSoundType();
        this.playSound(soundType.getStepSound(), soundType.getVolume() * 0.05F, soundType.getPitch() * 0.8F);
    }

    protected void playStepSound(BlockPos pos, BlockState state) {
        SoundType soundType = state.getSoundType();
        this.playSound(soundType.getStepSound(), soundType.getVolume() * 0.15F, soundType.getPitch());
    }

    private boolean shouldPlayAmethystStepSound(BlockState state) {
        return state.is(BlockTags.CRYSTAL_SOUND_BLOCKS) && this.tickCount >= this.lastCrystalSoundPlayTick + 20;
    }

    private void playAmethystStepSound() {
        this.crystalSoundIntensity = this.crystalSoundIntensity * (float)Math.pow(0.997, this.tickCount - this.lastCrystalSoundPlayTick);
        this.crystalSoundIntensity = Math.min(1.0F, this.crystalSoundIntensity + 0.07F);
        float f = 0.5F + this.crystalSoundIntensity * this.random.nextFloat() * 1.2F;
        float f1 = 0.1F + this.crystalSoundIntensity * 1.2F;
        this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, f1, f);
        this.lastCrystalSoundPlayTick = this.tickCount;
    }

    protected void playSwimSound(float volume) {
        this.playSound(this.getSwimSound(), volume, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
    }

    protected void onFlap() {
    }

    protected boolean isFlapping() {
        return false;
    }

    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (!this.isSilent()) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, this.getSoundSource(), volume, pitch);
        }
    }

    public void playSound(SoundEvent sound) {
        if (!this.isSilent()) {
            this.playSound(sound, 1.0F, 1.0F);
        }
    }

    public boolean isSilent() {
        return this.entityData.get(DATA_SILENT);
    }

    public void setSilent(boolean isSilent) {
        this.entityData.set(DATA_SILENT, isSilent);
    }

    public boolean isNoGravity() {
        return this.entityData.get(DATA_NO_GRAVITY);
    }

    public void setNoGravity(boolean noGravity) {
        this.entityData.set(DATA_NO_GRAVITY, noGravity);
    }

    protected double getDefaultGravity() {
        return 0.0;
    }

    public final double getGravity() {
        return this.isNoGravity() ? 0.0 : this.getDefaultGravity();
    }

    protected void applyGravity() {
        double gravity = this.getGravity();
        if (gravity != 0.0) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -gravity, 0.0));
        }
    }

    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.ALL;
    }

    public boolean dampensVibrations() {
        return false;
    }

    public final void doCheckFallDamage(double x, double y, double z, boolean onGround) {
        if (!this.touchingUnloadedChunk()) {
            this.checkSupportingBlock(onGround, new Vec3(x, y, z));
            BlockPos onPosLegacy = this.getOnPosLegacy();
            BlockState blockState = this.level().getBlockState(onPosLegacy);
            this.checkFallDamage(y, onGround, blockState, onPosLegacy);
        }
    }

    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        if (onGround) {
            if (this.fallDistance > 0.0F) {
                state.getBlock().fallOn(this.level(), state, pos, this, this.fallDistance);
                this.level()
                    .gameEvent(
                        GameEvent.HIT_GROUND,
                        this.position,
                        GameEvent.Context.of(
                            this, this.mainSupportingBlockPos.<BlockState>map(supportingPos -> this.level().getBlockState(supportingPos)).orElse(state)
                        )
                    );
            }

            this.resetFallDistance();
        } else if (y < 0.0) {
            this.fallDistance -= (float)y;
        }
    }

    public boolean fireImmune() {
        return this.immuneToFire != null ? immuneToFire : this.getType().fireImmune(); // Purpur - add fire immune API
    }

    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (this.type.is(EntityTypeTags.FALL_DAMAGE_IMMUNE)) {
            return false;
        } else {
            if (this.isVehicle()) {
                for (Entity entity : this.getPassengers()) {
                    entity.causeFallDamage(fallDistance, multiplier, source);
                }
            }

            return false;
        }
    }

    public boolean isInWater() {
        return this.wasTouchingWater;
    }

    public boolean isInRain() {
        BlockPos blockPos = this.blockPosition();
        return this.level().isRainingAt(blockPos)
            || this.level().isRainingAt(BlockPos.containing(blockPos.getX(), this.getBoundingBox().maxY, blockPos.getZ()));
    }

    public boolean isInBubbleColumn() {
        return this.getInBlockState().is(Blocks.BUBBLE_COLUMN);
    }

    public boolean isInWaterOrRain() {
        return this.isInWater() || this.isInRain();
    }

    public boolean isInWaterRainOrBubble() {
        return this.isInWater() || this.isInRain() || this.isInBubbleColumn();
    }

    public boolean isInWaterOrBubble() {
        return this.isInWater() || this.isInBubbleColumn();
    }

    public boolean isInLiquid() {
        return this.isInWaterOrBubble() || this.isInLava();
    }

    public boolean isUnderWater() {
        return this.wasEyeInWater && this.isInWater();
    }

    public void updateSwimming() {
        if (this.isSwimming()) {
            this.setSwimming(this.isSprinting() && this.isInWater() && !this.isPassenger());
        } else {
            this.setSwimming(
                this.isSprinting() && this.isUnderWater() && !this.isPassenger() && this.level().getFluidState(this.blockPosition).is(FluidTags.WATER)
            );
        }
    }

    protected boolean updateInWaterStateAndDoFluidPushing() {
        this.fluidHeight.clear();
        this.updateInWaterStateAndDoWaterCurrentPushing();
        double d = this.level().dimensionType().ultraWarm() ? 0.007 : 0.0023333333333333335;
        boolean flag = this.updateFluidHeightAndDoFluidPushing(FluidTags.LAVA, d);
        return this.isInWater() || flag;
    }

    public void updateInWaterStateAndDoWaterCurrentPushing() { // Purpur - Movement options for armor stands - package-private -> public - TODO: use AT file
        if (this.getVehicle() instanceof AbstractBoat abstractBoat && !abstractBoat.isUnderWater()) {
            this.wasTouchingWater = false;
        } else if (this.updateFluidHeightAndDoFluidPushing(FluidTags.WATER, 0.014)) {
            if (!this.wasTouchingWater && !this.firstTick) {
                this.doWaterSplashEffect();
            }

            this.resetFallDistance();
            this.wasTouchingWater = true;
            this.clearFire();
        } else {
            this.wasTouchingWater = false;
        }
    }

    private void updateFluidOnEyes() {
        this.wasEyeInWater = this.isEyeInFluid(FluidTags.WATER);
        this.fluidOnEyes.clear();
        double eyeY = this.getEyeY();
        if (!(
            this.getVehicle() instanceof AbstractBoat abstractBoat
                && !abstractBoat.isUnderWater()
                && abstractBoat.getBoundingBox().maxY >= eyeY
                && abstractBoat.getBoundingBox().minY <= eyeY
        )) {
            BlockPos blockPos = BlockPos.containing(this.getX(), eyeY, this.getZ());
            FluidState fluidState = this.level().getFluidState(blockPos);
            double d = blockPos.getY() + fluidState.getHeight(this.level(), blockPos);
            if (d > eyeY) {
                fluidState.getTags().forEach(this.fluidOnEyes::add);
            }
        }
    }

    protected void doWaterSplashEffect() {
        Entity entity = Objects.requireNonNullElse(this.getControllingPassenger(), this);
        float f = entity == this ? 0.2F : 0.9F;
        Vec3 deltaMovement = entity.getDeltaMovement();
        float min = Math.min(
            1.0F, (float)Math.sqrt(deltaMovement.x * deltaMovement.x * 0.2F + deltaMovement.y * deltaMovement.y + deltaMovement.z * deltaMovement.z * 0.2F) * f
        );
        if (min < 0.25F) {
            this.playSound(this.getSwimSplashSound(), min, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        } else {
            this.playSound(this.getSwimHighSpeedSplashSound(), min, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        }

        float f1 = Mth.floor(this.getY());

        for (int i = 0; i < 1.0F + this.dimensions.width() * 20.0F; i++) {
            double d = (this.random.nextDouble() * 2.0 - 1.0) * this.dimensions.width();
            double d1 = (this.random.nextDouble() * 2.0 - 1.0) * this.dimensions.width();
            this.level()
                .addParticle(
                    ParticleTypes.BUBBLE,
                    this.getX() + d,
                    f1 + 1.0F,
                    this.getZ() + d1,
                    deltaMovement.x,
                    deltaMovement.y - this.random.nextDouble() * 0.2F,
                    deltaMovement.z
                );
        }

        for (int i = 0; i < 1.0F + this.dimensions.width() * 20.0F; i++) {
            double d = (this.random.nextDouble() * 2.0 - 1.0) * this.dimensions.width();
            double d1 = (this.random.nextDouble() * 2.0 - 1.0) * this.dimensions.width();
            this.level().addParticle(ParticleTypes.SPLASH, this.getX() + d, f1 + 1.0F, this.getZ() + d1, deltaMovement.x, deltaMovement.y, deltaMovement.z);
        }

        this.gameEvent(GameEvent.SPLASH);
    }

    @Deprecated
    protected BlockState getBlockStateOnLegacy() {
        return this.level().getBlockState(this.getOnPosLegacy());
    }

    public BlockState getBlockStateOn() {
        return this.level().getBlockState(this.getOnPos());
    }

    public boolean canSpawnSprintParticle() {
        return this.isSprinting() && !this.isInWater() && !this.isSpectator() && !this.isCrouching() && !this.isInLava() && this.isAlive();
    }

    protected void spawnSprintParticle() {
        BlockPos onPosLegacy = this.getOnPosLegacy();
        BlockState blockState = this.level().getBlockState(onPosLegacy);
        if (blockState.getRenderShape() != RenderShape.INVISIBLE) {
            Vec3 deltaMovement = this.getDeltaMovement();
            BlockPos blockPos = this.blockPosition();
            double d = this.getX() + (this.random.nextDouble() - 0.5) * this.dimensions.width();
            double d1 = this.getZ() + (this.random.nextDouble() - 0.5) * this.dimensions.width();
            if (blockPos.getX() != onPosLegacy.getX()) {
                d = Mth.clamp(d, (double)onPosLegacy.getX(), onPosLegacy.getX() + 1.0);
            }

            if (blockPos.getZ() != onPosLegacy.getZ()) {
                d1 = Mth.clamp(d1, (double)onPosLegacy.getZ(), onPosLegacy.getZ() + 1.0);
            }

            this.level()
                .addParticle(
                    new BlockParticleOption(ParticleTypes.BLOCK, blockState), d, this.getY() + 0.1, d1, deltaMovement.x * -4.0, 1.5, deltaMovement.z * -4.0
                );
        }
    }

    public boolean isEyeInFluid(TagKey<Fluid> fluidTag) {
        return this.fluidOnEyes.contains(fluidTag);
    }

    public boolean isInLava() {
        return !this.firstTick && this.fluidHeight.getDouble(FluidTags.LAVA) > 0.0;
    }

    public void moveRelative(float amount, Vec3 relative) {
        Vec3 inputVector = getInputVector(relative, amount, this.getYRot());
        this.setDeltaMovement(this.getDeltaMovement().add(inputVector));
    }

    protected static Vec3 getInputVector(Vec3 relative, float motionScaler, float facing) {
        double d = relative.lengthSqr();
        if (d < 1.0E-7) {
            return Vec3.ZERO;
        } else {
            Vec3 vec3 = (d > 1.0 ? relative.normalize() : relative).scale(motionScaler);
            float sin = Mth.sin(facing * (float) (Math.PI / 180.0));
            float cos = Mth.cos(facing * (float) (Math.PI / 180.0));
            return new Vec3(vec3.x * cos - vec3.z * sin, vec3.y, vec3.z * cos + vec3.x * sin);
        }
    }

    @Deprecated
    public float getLightLevelDependentMagicValue() {
        return this.level().hasChunkAt(this.getBlockX(), this.getBlockZ())
            ? this.level().getLightLevelDependentMagicValue(BlockPos.containing(this.getX(), this.getEyeY(), this.getZ()))
            : 0.0F;
    }

    public void absMoveTo(double x, double y, double z, float yRot, float xRot) {
        this.absMoveTo(x, y, z);
        this.absRotateTo(yRot, xRot);
    }

    public void absRotateTo(float yRot, float xRot) {
        this.setYRot(yRot % 360.0F);
        this.setXRot(Mth.clamp(xRot, -90.0F, 90.0F) % 360.0F);
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.setYHeadRot(yRot); // Paper - Update head rotation
    }

    public void absMoveTo(double x, double y, double z) {
        double d = Mth.clamp(x, -3.0E7, 3.0E7);
        double d1 = Mth.clamp(z, -3.0E7, 3.0E7);
        this.xo = d;
        this.yo = y;
        this.zo = d1;
        this.setPos(d, y, d1);
        if (this.valid) this.level.getChunk((int) Math.floor(this.getX()) >> 4, (int) Math.floor(this.getZ()) >> 4); // CraftBukkit
    }

    public void moveTo(Vec3 vec) {
        this.moveTo(vec.x, vec.y, vec.z);
    }

    public void moveTo(double x, double y, double z) {
        this.moveTo(x, y, z, this.getYRot(), this.getXRot());
    }

    public void moveTo(BlockPos pos, float yRot, float xRot) {
        this.moveTo(pos.getBottomCenter(), yRot, xRot);
    }

    public void moveTo(Vec3 pos, float yRot, float xRot) {
        this.moveTo(pos.x, pos.y, pos.z, yRot, xRot);
    }

    public void moveTo(double x, double y, double z, float yRot, float xRot) {
        // Paper start - Fix Entity Teleportation and cancel velocity if teleported
        if (!preserveMotion) {
            this.deltaMovement = Vec3.ZERO;
        } else {
            this.preserveMotion = false;
        }
        // Paper end - Fix Entity Teleportation and cancel velocity if teleported
        this.setPosRaw(x, y, z);
        this.setYRot(yRot);
        this.setXRot(xRot);
        this.setOldPosAndRot();
        this.reapplyPosition();
        this.setYHeadRot(yRot); // Paper - Update head rotation
    }

    public final void setOldPosAndRot() {
        this.setOldPos();
        this.setOldRot();
    }

    public final void setOldPosAndRot(Vec3 pos, float yRot, float xRot) {
        this.setOldPos(pos);
        this.setOldRot(yRot, xRot);
    }

    protected void setOldPos() {
        this.setOldPos(this.position);
    }

    public void setOldRot() {
        this.setOldRot(this.getYRot(), this.getXRot());
    }

    private void setOldPos(Vec3 pos) {
        this.xo = this.xOld = pos.x;
        this.yo = this.yOld = pos.y;
        this.zo = this.zOld = pos.z;
    }

    private void setOldRot(float yRot, float xRot) {
        this.yRotO = yRot;
        this.xRotO = xRot;
    }

    public final Vec3 oldPosition() {
        return new Vec3(this.xOld, this.yOld, this.zOld);
    }

    public float distanceTo(Entity entity) {
        float f = (float)(this.getX() - entity.getX());
        float f1 = (float)(this.getY() - entity.getY());
        float f2 = (float)(this.getZ() - entity.getZ());
        return Mth.sqrt(f * f + f1 * f1 + f2 * f2);
    }

    public double distanceToSqr(double x, double y, double z) {
        double d = this.getX() - x;
        double d1 = this.getY() - y;
        double d2 = this.getZ() - z;
        return d * d + d1 * d1 + d2 * d2;
    }

    public double distanceToSqr(Entity entity) {
        return this.distanceToSqr(entity.position());
    }

    public double distanceToSqr(Vec3 vec) {
        double d = this.getX() - vec.x;
        double d1 = this.getY() - vec.y;
        double d2 = this.getZ() - vec.z;
        return d * d + d1 * d1 + d2 * d2;
    }

    public void playerTouch(Player player) {
    }

    public void push(Entity entity) {
        if (!this.isPassengerOfSameVehicle(entity)) {
            if (!entity.noPhysics && !this.noPhysics) {
                if (this.level.paperConfig().collisions.onlyPlayersCollide && !(entity instanceof ServerPlayer || this instanceof ServerPlayer)) return; // Paper - Collision option for requiring a player participant
                double d = entity.getX() - this.getX();
                double d1 = entity.getZ() - this.getZ();
                double max = Mth.absMax(d, d1);
                if (max >= 0.01F) {
                    max = Math.sqrt(max);
                    d /= max;
                    d1 /= max;
                    double d2 = 1.0 / max;
                    if (d2 > 1.0) {
                        d2 = 1.0;
                    }

                    d *= d2;
                    d1 *= d2;
                    d *= 0.05F;
                    d1 *= 0.05F;
                    if (!this.isVehicle() && this.isPushable()) {
                        this.push(-d, 0.0, -d1);
                    }

                    if (!entity.isVehicle() && entity.isPushable()) {
                        entity.push(d, 0.0, d1);
                    }
                }
            }
        }
    }

    public void push(Vec3 vector) {
        this.push(vector.x, vector.y, vector.z);
    }

    public void push(double x, double y, double z) {
        // Paper start - Add EntityKnockbackByEntityEvent and EntityPushedByEntityAttackEvent
        this.push(x, y, z, null);
    }

    public void push(double x, double y, double z, @Nullable Entity pushingEntity) {
        org.bukkit.util.Vector delta = new org.bukkit.util.Vector(x, y, z);
        if (pushingEntity != null) {
            io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent event = new io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent(this.getBukkitEntity(), io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.PUSH, pushingEntity.getBukkitEntity(), delta);
            if (!event.callEvent()) {
                return;
            }
            delta = event.getKnockback();
        }
        this.setDeltaMovement(this.getDeltaMovement().add(delta.getX(), delta.getY(), delta.getZ()));
        // Paper end - Add EntityKnockbackByEntityEvent and EntityPushedByEntityAttackEvent
        this.hasImpulse = true;
    }

    protected void markHurt() {
        this.hurtMarked = true;
    }

    @Deprecated
    public final void hurt(DamageSource damageSource, float amount) {
        if (this.level instanceof ServerLevel serverLevel) {
            this.hurtServer(serverLevel, damageSource, amount);
        }
    }

    @Deprecated
    public final boolean hurtOrSimulate(DamageSource damageSource, float amount) {
        return this.level instanceof ServerLevel serverLevel ? this.hurtServer(serverLevel, damageSource, amount) : this.hurtClient(damageSource);
    }

    public abstract boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount);

    public boolean hurtClient(DamageSource damageSource) {
        return false;
    }

    public final Vec3 getViewVector(float partialTicks) {
        return this.calculateViewVector(this.getViewXRot(partialTicks), this.getViewYRot(partialTicks));
    }

    public Direction getNearestViewDirection() {
        return Direction.getApproximateNearest(this.getViewVector(1.0F));
    }

    public float getViewXRot(float partialTicks) {
        return this.getXRot(partialTicks);
    }

    public float getViewYRot(float partialTick) {
        return this.getYRot(partialTick);
    }

    public float getXRot(float partialTick) {
        return partialTick == 1.0F ? this.getXRot() : Mth.lerp(partialTick, this.xRotO, this.getXRot());
    }

    public float getYRot(float partialTick) {
        return partialTick == 1.0F ? this.getYRot() : Mth.rotLerp(partialTick, this.yRotO, this.getYRot());
    }

    public final Vec3 calculateViewVector(float xRot, float yRot) {
        float f = xRot * (float) (Math.PI / 180.0);
        float f1 = -yRot * (float) (Math.PI / 180.0);
        float cos = Mth.cos(f1);
        float sin = Mth.sin(f1);
        float cos1 = Mth.cos(f);
        float sin1 = Mth.sin(f);
        return new Vec3(sin * cos1, -sin1, cos * cos1);
    }

    public final Vec3 getUpVector(float partialTick) {
        return this.calculateUpVector(this.getViewXRot(partialTick), this.getViewYRot(partialTick));
    }

    protected final Vec3 calculateUpVector(float xRot, float yRot) {
        return this.calculateViewVector(xRot - 90.0F, yRot);
    }

    public final Vec3 getEyePosition() {
        return new Vec3(this.getX(), this.getEyeY(), this.getZ());
    }

    public final Vec3 getEyePosition(float partialTick) {
        double d = Mth.lerp((double)partialTick, this.xo, this.getX());
        double d1 = Mth.lerp((double)partialTick, this.yo, this.getY()) + this.getEyeHeight();
        double d2 = Mth.lerp((double)partialTick, this.zo, this.getZ());
        return new Vec3(d, d1, d2);
    }

    public Vec3 getLightProbePosition(float partialTicks) {
        return this.getEyePosition(partialTicks);
    }

    public final Vec3 getPosition(float partialTicks) {
        double d = Mth.lerp((double)partialTicks, this.xo, this.getX());
        double d1 = Mth.lerp((double)partialTicks, this.yo, this.getY());
        double d2 = Mth.lerp((double)partialTicks, this.zo, this.getZ());
        return new Vec3(d, d1, d2);
    }

    public HitResult pick(double hitDistance, float partialTicks, boolean hitFluids) {
        Vec3 eyePosition = this.getEyePosition(partialTicks);
        Vec3 viewVector = this.getViewVector(partialTicks);
        Vec3 vec3 = eyePosition.add(viewVector.x * hitDistance, viewVector.y * hitDistance, viewVector.z * hitDistance);
        return this.level()
            .clip(new ClipContext(eyePosition, vec3, ClipContext.Block.OUTLINE, hitFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, this));
    }

    public boolean canBeHitByProjectile() {
        return this.isAlive() && this.isPickable();
    }

    public boolean isPickable() {
        return false;
    }

    public boolean isPushable() {
        // Paper start - Climbing should not bypass cramming gamerule
        return isCollidable(false);
    }

    public boolean isCollidable(boolean ignoreClimbing) {
        // Paper end - Climbing should not bypass cramming gamerule
        return false;
    }

    // CraftBukkit start - collidable API
    public boolean canCollideWithBukkit(Entity entity) {
        return this.isPushable();
    }
    // CraftBukkit end

    public void awardKillScore(Entity entity, DamageSource damageSource) {
        if (entity instanceof ServerPlayer) {
            CriteriaTriggers.ENTITY_KILLED_PLAYER.trigger((ServerPlayer)entity, this, damageSource);
        }
    }

    public boolean shouldRender(double x, double y, double z) {
        double d = this.getX() - x;
        double d1 = this.getY() - y;
        double d2 = this.getZ() - z;
        double d3 = d * d + d1 * d1 + d2 * d2;
        return this.shouldRenderAtSqrDistance(d3);
    }

    public boolean shouldRenderAtSqrDistance(double distance) {
        double size = this.getBoundingBox().getSize();
        if (Double.isNaN(size)) {
            size = 1.0;
        }

        size *= 64.0 * viewScale;
        return distance < size * size;
    }

    public boolean saveAsPassenger(CompoundTag compound) {
        // CraftBukkit start - allow excluding certain data when saving
        // Paper start - Raw entity serialization API
        return this.saveAsPassenger(compound, true, false, false);
    }
    public boolean saveAsPassenger(CompoundTag compound, boolean includeAll, boolean includeNonSaveable, boolean forceSerialization) {
        // Paper end - Raw entity serialization API
        // CraftBukkit end
        if (this.removalReason != null && !this.removalReason.shouldSave() && !forceSerialization) { // Paper - Raw entity serialization API
            return false;
        } else {
            String encodeId = this.getEncodeId(includeNonSaveable); // Paper - Raw entity serialization API
            if ((!this.persist && !forceSerialization) || encodeId == null) { // CraftBukkit - persist flag // Paper - Raw entity serialization API
                return false;
            } else {
                compound.putString("id", encodeId);
                this.saveWithoutId(compound, includeAll, includeNonSaveable, forceSerialization); // CraftBukkit - pass on includeAll // Paper - Raw entity serialization API
                return true;
            }
        }
    }

    public boolean save(CompoundTag compound) {
        return !this.isPassenger() && this.saveAsPassenger(compound);
    }

    public CompoundTag saveWithoutId(CompoundTag compound) {
        // CraftBukkit start - allow excluding certain data when saving
        // Paper start - Raw entity serialization API
        return this.saveWithoutId(compound, true, false, false);
    }

    public CompoundTag saveWithoutId(CompoundTag compound, boolean includeAll, boolean includeNonSaveable, boolean forceSerialization) {
        // Paper end - Raw entity serialization API
        // CraftBukkit end
        try {
            // CraftBukkit start - selectively save position
            if (includeAll) {
                if (this.vehicle != null) {
                    compound.put("Pos", this.newDoubleList(this.vehicle.getX(), this.getY(), this.vehicle.getZ()));
                } else {
                    compound.put("Pos", this.newDoubleList(this.getX(), this.getY(), this.getZ()));
                }
             }
            // CraftBukkit end

            Vec3 deltaMovement = this.getDeltaMovement();
            compound.put("Motion", this.newDoubleList(deltaMovement.x, deltaMovement.y, deltaMovement.z));
            // CraftBukkit start - Checking for NaN pitch/yaw and resetting to zero
            // TODO: make sure this is the best way to address this.
            if (Float.isNaN(this.yRot)) {
                this.yRot = 0;
            }

            if (Float.isNaN(this.xRot)) {
                this.xRot = 0;
            }
            // CraftBukkit end
            compound.put("Rotation", this.newFloatList(this.getYRot(), this.getXRot()));
            compound.putFloat("FallDistance", this.fallDistance);
            compound.putShort("Fire", (short)this.remainingFireTicks);
            compound.putShort("Air", (short)this.getAirSupply());
            compound.putBoolean("OnGround", this.onGround());
            compound.putBoolean("Invulnerable", this.invulnerable);
            compound.putInt("PortalCooldown", this.portalCooldown);
            // CraftBukkit start - selectively save uuid and world
            if (includeAll) {
                compound.putUUID("UUID", this.getUUID());
                // PAIL: Check above UUID reads 1.8 properly, ie: UUIDMost / UUIDLeast
                compound.putLong("WorldUUIDLeast", this.level.getWorld().getUID().getLeastSignificantBits());
                compound.putLong("WorldUUIDMost", this.level.getWorld().getUID().getMostSignificantBits());
            }
            compound.putInt("Bukkit.updateLevel", Entity.CURRENT_LEVEL);
            if (!this.persist) {
                compound.putBoolean("Bukkit.persist", this.persist);
            }
            if (!this.visibleByDefault) {
                compound.putBoolean("Bukkit.visibleByDefault", this.visibleByDefault);
            }
            if (this.persistentInvisibility) {
                compound.putBoolean("Bukkit.invisible", this.persistentInvisibility);
            }
            // SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
            if (this.maxAirTicks != this.getDefaultMaxAirSupply()) {
                compound.putInt("Bukkit.MaxAirSupply", this.getMaxAirSupply());
            }
            compound.putInt("Spigot.ticksLived", this.tickCount);
            // CraftBukkit end
            Component customName = this.getCustomName();
            if (customName != null) {
                compound.putString("CustomName", Component.Serializer.toJson(customName, this.registryAccess()));
            }

            if (this.isCustomNameVisible()) {
                compound.putBoolean("CustomNameVisible", this.isCustomNameVisible());
            }

            if (this.isSilent()) {
                compound.putBoolean("Silent", this.isSilent());
            }

            if (this.isNoGravity()) {
                compound.putBoolean("NoGravity", this.isNoGravity());
            }

            if (this.hasGlowingTag) {
                compound.putBoolean("Glowing", true);
            }

            int ticksFrozen = this.getTicksFrozen();
            if (ticksFrozen > 0) {
                compound.putInt("TicksFrozen", this.getTicksFrozen());
            }

            if (this.hasVisualFire) {
                compound.putBoolean("HasVisualFire", this.hasVisualFire);
            }

            if (!this.tags.isEmpty()) {
                ListTag listTag = new ListTag();

                for (String string : this.tags) {
                    listTag.add(StringTag.valueOf(string));
                }

                compound.put("Tags", listTag);
            }

            this.addAdditionalSaveData(compound, includeAll); // CraftBukkit - pass on includeAll
            if (this.isVehicle()) {
                ListTag listTag = new ListTag();

                for (Entity entity : this.getPassengers()) {
                    CompoundTag compoundTag = new CompoundTag();
                    if (entity.saveAsPassenger(compoundTag, includeAll, includeNonSaveable, forceSerialization)) { // CraftBukkit - pass on includeAll // Paper - Raw entity serialization API
                        listTag.add(compoundTag);
                    }
                }

                if (!listTag.isEmpty()) {
                    compound.put("Passengers", listTag);
                }
            }

            // CraftBukkit start - stores eventually existing bukkit values
            if (this.bukkitEntity != null) {
                this.bukkitEntity.storeBukkitValues(compound);
            }
            // CraftBukkit end
            // Paper start
            if (this.origin != null) {
                UUID originWorld = this.originWorld != null ? this.originWorld : this.level != null ? this.level.getWorld().getUID() : null;
                if (originWorld != null) {
                    compound.putUUID("Paper.OriginWorld", originWorld);
                }
                compound.put("Paper.Origin", this.newDoubleList(this.origin.getX(), this.origin.getY(), this.origin.getZ()));
            }
            if (this.spawnReason != null) {
                compound.putString("Paper.SpawnReason", this.spawnReason.name());
            }
            // Save entity's from mob spawner status
            if (this.spawnedViaMobSpawner) {
                compound.putBoolean("Paper.FromMobSpawner", true);
            }
            if (this.fromNetherPortal) {
                compound.putBoolean("Paper.FromNetherPortal", true);
            }
            if (this.freezeLocked) {
                compound.putBoolean("Paper.FreezeLock", true);
            }
            // Paper end

            // Purpur start - Fire immune API
            if (immuneToFire != null) {
                compound.putBoolean("Purpur.FireImmune", immuneToFire);
            }
            // Purpur end - Fire immune API

            return compound;
        } catch (Throwable var9) {
            CrashReport crashReport = CrashReport.forThrowable(var9, "Saving entity NBT");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Entity being saved");
            this.fillCrashReportCategory(crashReportCategory);
            throw new ReportedException(crashReport);
        }
    }

    public void load(CompoundTag compound) {
        try {
            ListTag list = compound.getList("Pos", 6);
            ListTag list1 = compound.getList("Motion", 6);
            ListTag list2 = compound.getList("Rotation", 5);
            double _double = list1.getDouble(0);
            double _double1 = list1.getDouble(1);
            double _double2 = list1.getDouble(2);
            this.setDeltaMovement(
                Math.abs(_double) > 10.0 ? 0.0 : _double, Math.abs(_double1) > 10.0 ? 0.0 : _double1, Math.abs(_double2) > 10.0 ? 0.0 : _double2
            );
            this.hasImpulse = true;
            double d = 3.0000512E7;
            this.setPosRaw(
                Mth.clamp(list.getDouble(0), -3.0000512E7, 3.0000512E7),
                Mth.clamp(list.getDouble(1), -2.0E7, 2.0E7),
                Mth.clamp(list.getDouble(2), -3.0000512E7, 3.0000512E7)
            );
            this.setYRot(list2.getFloat(0));
            this.setXRot(list2.getFloat(1));
            this.setOldPosAndRot();
            this.setYHeadRot(this.getYRot());
            this.setYBodyRot(this.getYRot());
            this.fallDistance = compound.getFloat("FallDistance");
            this.remainingFireTicks = compound.getShort("Fire");
            if (compound.contains("Air")) {
                this.setAirSupply(compound.getShort("Air"));
            }

            this.onGround = compound.getBoolean("OnGround");
            this.invulnerable = compound.getBoolean("Invulnerable");
            this.portalCooldown = compound.getInt("PortalCooldown");
            if (compound.hasUUID("UUID")) {
                this.uuid = compound.getUUID("UUID");
                this.stringUUID = this.uuid.toString();
            }

            if (!Double.isFinite(this.getX()) || !Double.isFinite(this.getY()) || !Double.isFinite(this.getZ())) {
                throw new IllegalStateException("Entity has invalid position");
            } else if (Double.isFinite(this.getYRot()) && Double.isFinite(this.getXRot())) {
                this.reapplyPosition();
                this.setRot(this.getYRot(), this.getXRot());
                if (compound.contains("CustomName", 8)) {
                    String string = compound.getString("CustomName");

                    try {
                        this.setCustomName(Component.Serializer.fromJson(string, this.registryAccess()));
                    } catch (Exception var16) {
                        LOGGER.warn("Failed to parse entity custom name {}", string, var16);
                    }
                } else {
                    this.entityData.set(DATA_CUSTOM_NAME, Optional.empty());
                }

                this.setCustomNameVisible(compound.getBoolean("CustomNameVisible"));
                this.setSilent(compound.getBoolean("Silent"));
                this.setNoGravity(compound.getBoolean("NoGravity"));
                this.setGlowingTag(compound.getBoolean("Glowing"));
                this.setTicksFrozen(compound.getInt("TicksFrozen"));
                this.hasVisualFire = compound.getBoolean("HasVisualFire");
                if (compound.contains("Tags", 9)) {
                    this.tags.clear();
                    ListTag list3 = compound.getList("Tags", 8);
                    int min = Math.min(list3.size(), 1024);

                    for (int i = 0; i < min; i++) {
                        this.tags.add(list3.getString(i));
                    }
                }

                this.readAdditionalSaveData(compound);
                if (this.repositionEntityAfterLoad()) {
                    this.reapplyPosition();
                }
            } else {
                throw new IllegalStateException("Entity has invalid rotation");
            }
            // CraftBukkit start
            // Spigot start
            if (this instanceof net.minecraft.world.entity.LivingEntity) {
                this.tickCount = compound.getInt("Spigot.ticksLived");
            }
            // Spigot end
            this.persist = !compound.contains("Bukkit.persist") || compound.getBoolean("Bukkit.persist");
            this.visibleByDefault = !compound.contains("Bukkit.visibleByDefault") || compound.getBoolean("Bukkit.visibleByDefault");
            // SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
            if (compound.contains("Bukkit.MaxAirSupply")) {
                this.maxAirTicks = compound.getInt("Bukkit.MaxAirSupply");
            }
            // CraftBukkit end

            // CraftBukkit start
            // Paper - move world parsing/loading to PlayerList#placeNewPlayer
            this.getBukkitEntity().readBukkitValues(compound);
            if (compound.contains("Bukkit.invisible")) {
                boolean bukkitInvisible = compound.getBoolean("Bukkit.invisible");
                this.setInvisible(bukkitInvisible);
                this.persistentInvisibility = bukkitInvisible;
            }
            // CraftBukkit end

            // Paper start
            ListTag originTag = compound.getList("Paper.Origin", net.minecraft.nbt.Tag.TAG_DOUBLE);
            if (!originTag.isEmpty()) {
                UUID originWorld = null;
                if (compound.contains("Paper.OriginWorld")) {
                    originWorld = compound.getUUID("Paper.OriginWorld");
                } else if (this.level != null) {
                    originWorld = this.level.getWorld().getUID();
                }
                this.originWorld = originWorld;
                origin = new org.bukkit.util.Vector(originTag.getDouble(0), originTag.getDouble(1), originTag.getDouble(2));
            }

            spawnedViaMobSpawner = compound.getBoolean("Paper.FromMobSpawner"); // Restore entity's from mob spawner status
            fromNetherPortal = compound.getBoolean("Paper.FromNetherPortal");
            if (compound.contains("Paper.SpawnReason")) {
                String spawnReasonName = compound.getString("Paper.SpawnReason");
                try {
                    spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.valueOf(spawnReasonName);
                } catch (Exception ignored) {
                    LOGGER.error("Unknown SpawnReason " + spawnReasonName + " for " + this);
                }
            }
            if (spawnReason == null) {
                if (spawnedViaMobSpawner) {
                    spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER;
                } else if (this instanceof Mob && (this instanceof net.minecraft.world.entity.animal.Animal || this instanceof net.minecraft.world.entity.animal.AbstractFish) && !((Mob) this).removeWhenFarAway(0.0)) {
                    if (!compound.getBoolean("PersistenceRequired")) {
                        spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL;
                    }
                }
            }
            if (spawnReason == null) {
                spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT;
            }
            if (compound.contains("Paper.FreezeLock")) {
                freezeLocked = compound.getBoolean("Paper.FreezeLock");
            }
            // Paper end

            // Purpur start - Fire immune API
            if (compound.contains("Purpur.FireImmune")) {
                immuneToFire = compound.getBoolean("Purpur.FireImmune");
            }
            // Purpur end - Fire immune API

        } catch (Throwable var17) {
            CrashReport crashReport = CrashReport.forThrowable(var17, "Loading entity NBT");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Entity being loaded");
            this.fillCrashReportCategory(crashReportCategory);
            throw new ReportedException(crashReport);
        }
    }

    protected boolean repositionEntityAfterLoad() {
        return true;
    }

    @Nullable
    public final String getEncodeId() {
        // Paper start - Raw entity serialization API
        return getEncodeId(false);
    }
    public final @Nullable String getEncodeId(boolean includeNonSaveable) {
        // Paper end - Raw entity serialization API
        EntityType<?> type = this.getType();
        ResourceLocation key = EntityType.getKey(type);
        return (type.canSerialize() || includeNonSaveable) && key != null ? key.toString() : null; // Paper - Raw entity serialization API
    }

    // CraftBukkit start - allow excluding certain data when saving
    protected void addAdditionalSaveData(CompoundTag tag, boolean includeAll) {
        this.addAdditionalSaveData(tag);
    }
    // CraftBukkit end

    protected abstract void readAdditionalSaveData(CompoundTag tag);

    protected abstract void addAdditionalSaveData(CompoundTag tag);

    protected ListTag newDoubleList(double... numbers) {
        ListTag listTag = new ListTag();

        for (double d : numbers) {
            listTag.add(DoubleTag.valueOf(d));
        }

        return listTag;
    }

    protected ListTag newFloatList(float... numbers) {
        ListTag listTag = new ListTag();

        for (float f : numbers) {
            listTag.add(FloatTag.valueOf(f));
        }

        return listTag;
    }

    @Nullable
    public ItemEntity spawnAtLocation(ServerLevel level, ItemLike item) {
        return this.spawnAtLocation(level, item, 0);
    }

    @Nullable
    public ItemEntity spawnAtLocation(ServerLevel level, ItemLike item, int yOffset) {
        return this.spawnAtLocation(level, new ItemStack(item), (float)yOffset);
    }

    @Nullable
    public ItemEntity spawnAtLocation(ServerLevel level, ItemStack stack) {
        return this.spawnAtLocation(level, stack, 0.0F);
    }

    @Nullable
    public ItemEntity spawnAtLocation(ServerLevel level, ItemStack stack, float yOffset) {
        // Paper start - Restore vanilla drops behavior
        return this.spawnAtLocation(level, stack, yOffset, null);
    }
    public record DefaultDrop(Item item, org.bukkit.inventory.ItemStack stack, @Nullable java.util.function.Consumer<ItemStack> dropConsumer) {
        public DefaultDrop(final ItemStack stack, final java.util.function.Consumer<ItemStack> dropConsumer) {
            this(stack.getItem(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(stack), dropConsumer);
        }

        public void runConsumer(final java.util.function.Consumer<org.bukkit.inventory.ItemStack> fallback) {
            if (this.dropConsumer == null || org.bukkit.craftbukkit.inventory.CraftItemType.bukkitToMinecraft(this.stack.getType()) != this.item) {
                fallback.accept(this.stack);
            } else {
                this.dropConsumer.accept(org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(this.stack));
            }
        }
    }
    @Nullable
    public ItemEntity spawnAtLocation(ServerLevel level, ItemStack stack, float yOffset, @Nullable java.util.function.Consumer<? super ItemEntity> delayedAddConsumer) {
        // Paper end - Restore vanilla drops behavior
        if (stack.isEmpty()) {
            return null;
        } else {
            // CraftBukkit start - Capture drops for death event
            if (this instanceof net.minecraft.world.entity.LivingEntity && !this.forceDrops) {
                // Paper start - Restore vanilla drops behavior
                ((net.minecraft.world.entity.LivingEntity) this).drops.add(new net.minecraft.world.entity.Entity.DefaultDrop(stack, itemStack -> {
                    ItemEntity itemEntity = new ItemEntity(this.level, this.getX(), this.getY() + (double) yOffset, this.getZ(), itemStack); // stack is copied before consumer
                    itemEntity.setDefaultPickUpDelay();
                    this.level.addFreshEntity(itemEntity);
                    if (delayedAddConsumer != null) delayedAddConsumer.accept(itemEntity);
                }));
                // Paper end - Restore vanilla drops behavior
                return null;
            }
            // CraftBukkit end
            ItemEntity itemEntity = new ItemEntity(level, this.getX(), this.getY() + yOffset, this.getZ(), stack.copy()); // Paper - copy so we can destroy original
            stack.setCount(0); // Paper - destroy this item - if this ever leaks due to game bugs, ensure it doesn't dupe

            itemEntity.setDefaultPickUpDelay();
            itemEntity.setDefaultPickUpDelay(); // Paper - diff on change (in dropConsumer)
            // Paper start - Call EntityDropItemEvent
            return this.spawnAtLocation(level, itemEntity);
        }
    }
    @Nullable
    public ItemEntity spawnAtLocation(ServerLevel level, ItemEntity itemEntity) {
        {
            // Paper end - Call EntityDropItemEvent
            // CraftBukkit start
            org.bukkit.event.entity.EntityDropItemEvent event = new org.bukkit.event.entity.EntityDropItemEvent(this.getBukkitEntity(), (org.bukkit.entity.Item) itemEntity.getBukkitEntity());
            org.bukkit.Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return null;
            }
            // CraftBukkit end
            level.addFreshEntity(itemEntity);
            return itemEntity;
        }
    }

    public boolean isAlive() {
        return !this.isRemoved();
    }

    public boolean isInWall() {
        // Paper start - optimise collisions
        if (this.noPhysics) {
            return false;
        }

        final double reducedWith = (double)(this.dimensions.width() * 0.8F);
        final AABB boundingBox = AABB.ofSize(this.getEyePosition(), reducedWith, 1.0E-6D, reducedWith);
        final Level world = this.level;

        if (ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.isEmpty(boundingBox)) {
            return false;
        }

        final int minBlockX = Mth.floor(boundingBox.minX);
        final int minBlockY = Mth.floor(boundingBox.minY);
        final int minBlockZ = Mth.floor(boundingBox.minZ);

        final int maxBlockX = Mth.floor(boundingBox.maxX);
        final int maxBlockY = Mth.floor(boundingBox.maxY);
        final int maxBlockZ = Mth.floor(boundingBox.maxZ);

        final int minChunkX = minBlockX >> 4;
        final int minChunkY = minBlockY >> 4;
        final int minChunkZ = minBlockZ >> 4;

        final int maxChunkX = maxBlockX >> 4;
        final int maxChunkY = maxBlockY >> 4;
        final int maxChunkZ = maxBlockZ >> 4;

        final int minSection = ca.spottedleaf.moonrise.common.util.WorldUtil.getMinSection(world);
        final net.minecraft.world.level.chunk.ChunkSource chunkSource = world.getChunkSource();
        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int currChunkZ = minChunkZ; currChunkZ <= maxChunkZ; ++currChunkZ) {
            for (int currChunkX = minChunkX; currChunkX <= maxChunkX; ++currChunkX) {
                final net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunkSource.getChunk(currChunkX, currChunkZ, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true).getSections();

                for (int currChunkY = minChunkY; currChunkY <= maxChunkY; ++currChunkY) {
                    final int sectionIdx = currChunkY - minSection;
                    if (sectionIdx < 0 || sectionIdx >= sections.length) {
                        continue;
                    }
                    final net.minecraft.world.level.chunk.LevelChunkSection section = sections[sectionIdx];
                    if (section.hasOnlyAir()) {
                        // empty
                        continue;
                    }

                    final net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.world.level.block.state.BlockState> blocks = section.states;

                    final int minXIterate = currChunkX == minChunkX ? (minBlockX & 15) : 0;
                    final int maxXIterate = currChunkX == maxChunkX ? (maxBlockX & 15) : 15;
                    final int minZIterate = currChunkZ == minChunkZ ? (minBlockZ & 15) : 0;
                    final int maxZIterate = currChunkZ == maxChunkZ ? (maxBlockZ & 15) : 15;
                    final int minYIterate = currChunkY == minChunkY ? (minBlockY & 15) : 0;
                    final int maxYIterate = currChunkY == maxChunkY ? (maxBlockY & 15) : 15;

                    for (int currY = minYIterate; currY <= maxYIterate; ++currY) {
                        final int blockY = currY | (currChunkY << 4);
                        mutablePos.setY(blockY);
                        for (int currZ = minZIterate; currZ <= maxZIterate; ++currZ) {
                            final int blockZ = currZ | (currChunkZ << 4);
                            mutablePos.setZ(blockZ);
                            for (int currX = minXIterate; currX <= maxXIterate; ++currX) {
                                final int blockX = currX | (currChunkX << 4);
                                mutablePos.setX(blockX);

                                final BlockState blockState = blocks.get((currX) | (currZ << 4) | ((currY) << 8));

                                if (((ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState)blockState).moonrise$emptyCollisionShape()
                                    || !blockState.isSuffocating(world, mutablePos)) {
                                    continue;
                                }

                                // Yes, it does not use the Entity context stuff.
                                final VoxelShape collisionShape = blockState.getCollisionShape(world, mutablePos);

                                if (collisionShape.isEmpty()) {
                                    continue;
                                }

                                final AABB toCollide = boundingBox.move(-(double)blockX, -(double)blockY, -(double)blockZ);

                                final AABB singleAABB = ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)collisionShape).moonrise$getSingleAABBRepresentation();
                                if (singleAABB != null) {
                                    if (ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.voxelShapeIntersect(singleAABB, toCollide)) {
                                        return true;
                                    }
                                    continue;
                                }

                                if (ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.voxelShapeIntersectNoEmpty(collisionShape, toCollide)) {
                                    return true;
                                }
                                continue;
                            }
                        }
                    }
                }
            }
        }

        return false;
        // Paper end - optimise collisions
    }

    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.isAlive() && this instanceof Leashable leashable) {
            if (leashable.getLeashHolder() == player) {
                if (!this.level().isClientSide()) {
                    if (hand == InteractionHand.OFF_HAND && (level().purpurConfig.villagerCanBeLeashed || level().purpurConfig.wanderingTraderCanBeLeashed) && this instanceof net.minecraft.world.entity.npc.AbstractVillager) return InteractionResult.CONSUME; // Purpur - Allow leashing villagers
                    // CraftBukkit start - fire PlayerUnleashEntityEvent
                    // Paper start - Expand EntityUnleashEvent
                    org.bukkit.event.player.PlayerUnleashEntityEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerUnleashEntityEvent(this, player, hand, !player.hasInfiniteMaterials());
                    if (event.isCancelled()) {
                        // Paper end - Expand EntityUnleashEvent
                        ((ServerPlayer) player).connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket(this, leashable.getLeashHolder()));
                        return InteractionResult.PASS;
                    }
                    // CraftBukkit end
                    if (!event.isDropLeash()) { // Paper - Expand EntityUnleashEvent
                        leashable.removeLeash();
                    } else {
                        leashable.dropLeash();
                    }

                    this.gameEvent(GameEvent.ENTITY_INTERACT, player);
                }

                return InteractionResult.SUCCESS.withoutItem();
            }

            ItemStack itemInHand = player.getItemInHand(hand);
            if (itemInHand.is(Items.LEAD) && leashable.canHaveALeashAttachedToIt()) {
                if (!this.level().isClientSide()) {
                    // CraftBukkit start - fire PlayerLeashEntityEvent
                    if (org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerLeashEntityEvent(this, player, player, hand).isCancelled()) {
                        ((ServerPlayer) player).connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket(this, leashable.getLeashHolder()));
                        player.containerMenu.sendAllDataToRemote(); // Paper - Fix inventory desync
                        return InteractionResult.PASS;
                    }
                    // CraftBukkit end
                    leashable.setLeashedTo(player, true);
                }

                itemInHand.shrink(1);
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    public boolean canCollideWith(Entity entity) {
        return entity.canBeCollidedWith() && !this.isPassengerOfSameVehicle(entity);
    }

    public boolean canBeCollidedWith() {
        return false;
    }

    public void rideTick() {
        this.setDeltaMovement(Vec3.ZERO);
        this.tick();
        if (this.isPassenger()) {
            this.getVehicle().positionRider(this);
        }
    }

    public final void positionRider(Entity passenger) {
        if (this.hasPassenger(passenger)) {
            this.positionRider(passenger, Entity::setPos);
        }
    }

    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        Vec3 passengerRidingPosition = this.getPassengerRidingPosition(passenger);
        Vec3 vehicleAttachmentPoint = passenger.getVehicleAttachmentPoint(this);
        callback.accept(
            passenger,
            passengerRidingPosition.x - vehicleAttachmentPoint.x,
            passengerRidingPosition.y - vehicleAttachmentPoint.y,
            passengerRidingPosition.z - vehicleAttachmentPoint.z
        );
    }

    public void onPassengerTurned(Entity entityToUpdate) {
    }

    public Vec3 getVehicleAttachmentPoint(Entity entity) {
        return this.getAttachments().get(EntityAttachment.VEHICLE, 0, this.yRot);
    }

    public Vec3 getPassengerRidingPosition(Entity entity) {
        return this.position().add(this.getPassengerAttachmentPoint(entity, this.dimensions, 1.0F));
    }

    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
        return getDefaultPassengerAttachmentPoint(this, entity, dimensions.attachments());
    }

    protected static Vec3 getDefaultPassengerAttachmentPoint(Entity vehicle, Entity passenger, EntityAttachments attachments) {
        int index = vehicle.getPassengers().indexOf(passenger);
        return attachments.getClamped(EntityAttachment.PASSENGER, index, vehicle.yRot);
    }

    public boolean startRiding(Entity vehicle) {
        return this.startRiding(vehicle, false);
    }

    public boolean showVehicleHealth() {
        return this instanceof LivingEntity;
    }

    public boolean startRiding(Entity vehicle, boolean force) {
        if (vehicle == this.vehicle || vehicle.level != this.level) { // Paper - Ensure entity passenger world matches ridden entity (bad plugins)
            return false;
        } else if (!vehicle.couldAcceptPassenger()) {
            return false;
        } else if (!force && !this.level().isClientSide() && !vehicle.type.canSerialize()) { // SPIGOT-7947: Allow force riding all entities
            return false;
        } else {
            for (Entity entity = vehicle; entity.vehicle != null; entity = entity.vehicle) {
                if (entity.vehicle == this) {
                    return false;
                }
            }

            if (force || this.canRide(vehicle) && vehicle.canAddPassenger(this)) {
                // CraftBukkit start
                if (vehicle.getBukkitEntity() instanceof org.bukkit.entity.Vehicle && this.getBukkitEntity() instanceof org.bukkit.entity.LivingEntity) {
                    org.bukkit.event.vehicle.VehicleEnterEvent event = new org.bukkit.event.vehicle.VehicleEnterEvent((org.bukkit.entity.Vehicle) vehicle.getBukkitEntity(), this.getBukkitEntity());
                    // Suppress during worldgen
                    if (this.valid) {
                        org.bukkit.Bukkit.getPluginManager().callEvent(event);
                    }
                    if (event.isCancelled()) {
                        return false;
                    }
                }

                org.bukkit.event.entity.EntityMountEvent event = new org.bukkit.event.entity.EntityMountEvent(this.getBukkitEntity(), vehicle.getBukkitEntity());
                // Suppress during worldgen
                if (this.valid) {
                    org.bukkit.Bukkit.getPluginManager().callEvent(event);
                }
                if (event.isCancelled()) {
                    return false;
                }
                // CraftBukkit end
                if (this.isPassenger()) {
                    this.stopRiding();
                }

                this.setPose(Pose.STANDING);
                this.vehicle = vehicle;
                this.vehicle.addPassenger(this);
                vehicle.getIndirectPassengersStream()
                    .filter(entity1 -> entity1 instanceof ServerPlayer)
                    .forEach(entity1 -> CriteriaTriggers.START_RIDING_TRIGGER.trigger((ServerPlayer)entity1));
                return true;
            } else {
                return false;
            }
        }
    }

    protected boolean canRide(Entity vehicle) {
        return !this.isShiftKeyDown() && this.boardingCooldown <= 0;
    }

    public void ejectPassengers() {
        for (int i = this.passengers.size() - 1; i >= 0; i--) {
            this.passengers.get(i).stopRiding();
        }
    }

    public void removeVehicle() {
        // Paper start - Force entity dismount during teleportation
        this.removeVehicle(false);
    }
    public void removeVehicle(boolean suppressCancellation) {
        // Paper end - Force entity dismount during teleportation
        if (this.vehicle != null) {
            Entity entity = this.vehicle;
            this.vehicle = null;
            if (!entity.removePassenger(this, suppressCancellation)) this.vehicle = entity; // CraftBukkit // Paper - Force entity dismount during teleportation
        }
    }

    public void stopRiding() {
        // Paper start - Force entity dismount during teleportation
        this.stopRiding(false);
    }

    public void stopRiding(boolean suppressCancellation) {
        this.removeVehicle(suppressCancellation);
        // Paper end - Force entity dismount during teleportation
    }

    protected void addPassenger(Entity passenger) {
        if (passenger.getVehicle() != this) {
            throw new IllegalStateException("Use x.startRiding(y), not y.addPassenger(x)");
        } else {
            if (this.passengers.isEmpty()) {
                this.passengers = ImmutableList.of(passenger);
            } else {
                List<Entity> list = Lists.newArrayList(this.passengers);
                if (!this.level().isClientSide && passenger instanceof Player && !(this.getFirstPassenger() instanceof Player)) {
                    list.add(0, passenger);
                } else {
                    list.add(passenger);
                }

                this.passengers = ImmutableList.copyOf(list);
            }

            // Purpur start - Ridables
            if (isRidable() && this.passengers.get(0) == passenger && passenger instanceof Player player) {
                onMount(player);
                this.rider = player;
            }
            // Purpur end - Ridables

            this.gameEvent(GameEvent.ENTITY_MOUNT, passenger);
        }
    }

    // Paper start - Force entity dismount during teleportation
    protected boolean removePassenger(Entity passenger) {
        return removePassenger(passenger, false);
    }
    protected boolean removePassenger(Entity passenger, boolean suppressCancellation) { // CraftBukkit
        // Paper end - Force entity dismount during teleportation
        if (passenger.getVehicle() == this) {
            throw new IllegalStateException("Use x.stopRiding(y), not y.removePassenger(x)");
        } else {
            // CraftBukkit start
            org.bukkit.craftbukkit.entity.CraftEntity craft = (org.bukkit.craftbukkit.entity.CraftEntity) passenger.getBukkitEntity().getVehicle();
            Entity orig = craft == null ? null : craft.getHandle();
            if (this.getBukkitEntity() instanceof org.bukkit.entity.Vehicle && passenger.getBukkitEntity() instanceof org.bukkit.entity.LivingEntity) {
                org.bukkit.event.vehicle.VehicleExitEvent event = new org.bukkit.event.vehicle.VehicleExitEvent(
                        (org.bukkit.entity.Vehicle) this.getBukkitEntity(),
                        (org.bukkit.entity.LivingEntity) passenger.getBukkitEntity(), !suppressCancellation // Paper - Force entity dismount during teleportation
                );
                // Suppress during worldgen
                if (this.valid) {
                    org.bukkit.Bukkit.getPluginManager().callEvent(event);
                }
                org.bukkit.craftbukkit.entity.CraftEntity craftn = (org.bukkit.craftbukkit.entity.CraftEntity) passenger.getBukkitEntity().getVehicle();
                Entity n = craftn == null ? null : craftn.getHandle();
                if (event.isCancelled() || n != orig) {
                    return false;
                }
            }

            org.bukkit.event.entity.EntityDismountEvent event = new org.bukkit.event.entity.EntityDismountEvent(passenger.getBukkitEntity(), this.getBukkitEntity(), !suppressCancellation); // Paper - Force entity dismount during teleportation
            // Suppress during worldgen
            if (this.valid) {
                org.bukkit.Bukkit.getPluginManager().callEvent(event);
            }
            if (event.isCancelled()) {
                return false;
            }
            // CraftBukkit end

            // Purpur start - Ridables
            if (this.rider != null && this.passengers.get(0) == this.rider) {
                onDismount(this.rider);
                this.rider = null;
            }
            // Purpur end - Ridables

            if (this.passengers.size() == 1 && this.passengers.get(0) == passenger) {
                this.passengers = ImmutableList.of();
            } else {
                this.passengers = this.passengers.stream().filter(entity -> entity != passenger).collect(ImmutableList.toImmutableList());
            }

            passenger.boardingCooldown = 60;
            this.gameEvent(GameEvent.ENTITY_DISMOUNT, passenger);
        }
        return true; // CraftBukkit
    }

    protected boolean canAddPassenger(Entity passenger) {
        return this.passengers.isEmpty();
    }

    protected boolean couldAcceptPassenger() {
        return true;
    }

    public void cancelLerp() {
    }

    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.setPos(x, y, z);
        this.setRot(yRot, xRot);
    }

    public double lerpTargetX() {
        return this.getX();
    }

    public double lerpTargetY() {
        return this.getY();
    }

    public double lerpTargetZ() {
        return this.getZ();
    }

    public float lerpTargetXRot() {
        return this.getXRot();
    }

    public float lerpTargetYRot() {
        return this.getYRot();
    }

    public void lerpHeadTo(float yaw, int pitch) {
        this.setYHeadRot(yaw);
    }

    public float getPickRadius() {
        return 0.0F;
    }

    public Vec3 getLookAngle() {
        return this.calculateViewVector(this.getXRot(), this.getYRot());
    }

    public Vec3 getHandHoldingItemAngle(Item item) {
        if (!(this instanceof Player player)) {
            return Vec3.ZERO;
        } else {
            boolean flag = player.getOffhandItem().is(item) && !player.getMainHandItem().is(item);
            HumanoidArm humanoidArm = flag ? player.getMainArm().getOpposite() : player.getMainArm();
            return this.calculateViewVector(0.0F, this.getYRot() + (humanoidArm == HumanoidArm.RIGHT ? 80 : -80)).scale(0.5);
        }
    }

    public Vec2 getRotationVector() {
        return new Vec2(this.getXRot(), this.getYRot());
    }

    public Vec3 getForward() {
        return Vec3.directionFromRotation(this.getRotationVector());
    }

    public BlockPos portalPos = BlockPos.ZERO; // Purpur - Fix stuck in portals
    public void setAsInsidePortal(Portal portal, BlockPos pos) {
        if (this.isOnPortalCooldown()) {
            if (!(level().purpurConfig.playerFixStuckPortal && this instanceof Player && !pos.equals(this.portalPos))) // Purpur - Fix stuck in portals
            this.setPortalCooldown();
        } else if (this.level.purpurConfig.entitiesCanUsePortals || this instanceof ServerPlayer) { // Purpur - Entities can use portals
            if (this.portalProcess == null || !this.portalProcess.isSamePortal(portal)) {
                this.portalProcess = new PortalProcessor(portal, pos.immutable());
            } else if (!this.portalProcess.isInsidePortalThisTick()) {
                this.portalProcess.updateEntryPosition(pos.immutable());
                this.portalProcess.setAsInsidePortalThisTick(true);
                this.portalPos = BlockPos.ZERO; // Purpur - Fix stuck in portals
            }
        }
    }

    protected void handlePortal() {
        if (this.level() instanceof ServerLevel serverLevel) {
            this.processPortalCooldown();
            if (this.portalProcess != null) {
                if (this.portalProcess.processPortalTeleportation(serverLevel, this, this.canUsePortal(false))) {
                    ProfilerFiller profilerFiller = Profiler.get();
                    profilerFiller.push("portal");
                    this.setPortalCooldown();
                    TeleportTransition portalDestination = this.portalProcess.getPortalDestination(serverLevel, this);
                    if (portalDestination != null) {
                        ServerLevel level = portalDestination.newLevel();
                        if (this instanceof ServerPlayer // CraftBukkit - always call event for players
                            || (level != null && (level.dimension() == serverLevel.dimension() || this.canTeleport(serverLevel, level)))) { // CraftBukkit
                            this.teleport(portalDestination);
                        }
                    }

                    profilerFiller.pop();
                } else if (this.portalProcess.hasExpired()) {
                    this.portalProcess = null;
                }
            }
        }
    }

    public int getDimensionChangingDelay() {
        Entity firstPassenger = this.getFirstPassenger();
        return firstPassenger instanceof ServerPlayer ? firstPassenger.getDimensionChangingDelay() : 300;
    }

    public void lerpMotion(double x, double y, double z) {
        this.setDeltaMovement(x, y, z);
    }

    public void handleDamageEvent(DamageSource damageSource) {
    }

    public void handleEntityEvent(byte id) {
        switch (id) {
            case 53:
                HoneyBlock.showSlideParticles(this);
        }
    }

    public void animateHurt(float yaw) {
    }

    public boolean isOnFire() {
        boolean flag = this.level() != null && this.level().isClientSide;
        return !this.fireImmune() && (this.remainingFireTicks > 0 || flag && this.getSharedFlag(0));
    }

    public boolean isPassenger() {
        return this.getVehicle() != null;
    }

    public boolean isVehicle() {
        return !this.passengers.isEmpty();
    }

    public boolean dismountsUnderwater() {
        return this.getType().is(EntityTypeTags.DISMOUNTS_UNDERWATER);
    }

    public boolean canControlVehicle() {
        return !this.getType().is(EntityTypeTags.NON_CONTROLLING_RIDER);
    }

    public void setShiftKeyDown(boolean keyDown) {
        this.setSharedFlag(1, keyDown);
    }

    public boolean isShiftKeyDown() {
        return this.getSharedFlag(1);
    }

    public boolean isSteppingCarefully() {
        return this.isShiftKeyDown();
    }

    public boolean isSuppressingBounce() {
        return this.isShiftKeyDown();
    }

    public boolean isDiscrete() {
        return this.isShiftKeyDown();
    }

    public boolean isDescending() {
        return this.isShiftKeyDown();
    }

    public boolean isCrouching() {
        return this.hasPose(net.minecraft.world.entity.Pose.CROUCHING);
    }

    public boolean isSprinting() {
        return this.getSharedFlag(3);
    }

    public void setSprinting(boolean sprinting) {
        this.setSharedFlag(3, sprinting);
    }

    public boolean isSwimming() {
        return this.getSharedFlag(4);
    }

    public boolean isVisuallySwimming() {
        return this.hasPose(net.minecraft.world.entity.Pose.SWIMMING);
    }

    public boolean isVisuallyCrawling() {
        return this.isVisuallySwimming() && !this.isInWater();
    }

    public void setSwimming(boolean swimming) {
        // CraftBukkit start
        if (this.valid && this.isSwimming() != swimming && this instanceof net.minecraft.world.entity.LivingEntity) {
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callToggleSwimEvent((net.minecraft.world.entity.LivingEntity) this, swimming).isCancelled()) {
                return;
            }
        }
        // CraftBukkit end
        this.setSharedFlag(4, swimming);
    }

    public final boolean hasGlowingTag() {
        return this.hasGlowingTag;
    }

    public final void setGlowingTag(boolean hasGlowingTag) {
        this.hasGlowingTag = hasGlowingTag;
        this.setSharedFlag(6, this.isCurrentlyGlowing());
    }

    public boolean isCurrentlyGlowing() {
        return this.level().isClientSide() ? this.getSharedFlag(6) : this.hasGlowingTag;
    }

    public boolean isInvisible() {
        return this.getSharedFlag(5);
    }

    public boolean isInvisibleTo(Player player) {
        if (player.isSpectator()) {
            return false;
        } else {
            Team team = this.getTeam();
            return (team == null || player == null || player.getTeam() != team || !team.canSeeFriendlyInvisibles()) && this.isInvisible();
        }
    }

    public boolean isOnRails() {
        return false;
    }

    public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> listenerConsumer) {
    }

    @Nullable
    public PlayerTeam getTeam() {
        if (!this.level().paperConfig().scoreboards.allowNonPlayerEntitiesOnScoreboards && !(this instanceof Player)) { return null; } // Paper - Perf: Disable Scoreboards for non players by default
        return this.level().getScoreboard().getPlayersTeam(this.getScoreboardName());
    }

    public final boolean isAlliedTo(@Nullable Entity entity) {
        return entity != null && (this == entity || this.considersEntityAsAlly(entity) || entity.considersEntityAsAlly(this));
    }

    protected boolean considersEntityAsAlly(Entity entity) {
        return this.isAlliedTo(entity.getTeam());
    }

    public boolean isAlliedTo(@Nullable Team team) {
        return this.getTeam() != null && this.getTeam().isAlliedTo(team);
    }

    public void setInvisible(boolean invisible) {
        // CraftBukkit - start
        if (!this.persistentInvisibility) { // Prevent Minecraft from removing our invisibility flag
            this.setSharedFlag(5, invisible);
        }
        // CraftBukkit - end
    }

    public boolean getSharedFlag(int flag) {
        return (this.entityData.get(DATA_SHARED_FLAGS_ID) & 1 << flag) != 0;
    }

    public void setSharedFlag(int flag, boolean set) {
        byte b = this.entityData.get(DATA_SHARED_FLAGS_ID);
        if (set) {
            this.entityData.set(DATA_SHARED_FLAGS_ID, (byte)(b | 1 << flag));
        } else {
            this.entityData.set(DATA_SHARED_FLAGS_ID, (byte)(b & ~(1 << flag)));
        }
    }

    public int getMaxAirSupply() {
        return this.level == null? this.maxAirTicks : this.level().purpurConfig.drowningAirTicks; // CraftBukkit - SPIGOT-6907: re-implement LivingEntity#setMaximumAir() // Purpur - Drowning Settings
    }

    public int getAirSupply() {
        return this.entityData.get(DATA_AIR_SUPPLY_ID);
    }

    public void setAirSupply(int air) {
        // CraftBukkit start
        org.bukkit.event.entity.EntityAirChangeEvent event = new org.bukkit.event.entity.EntityAirChangeEvent(this.getBukkitEntity(), air);
        // Suppress during worldgen
        if (this.valid) {
            event.getEntity().getServer().getPluginManager().callEvent(event);
        }
        if (event.isCancelled() && this.getAirSupply() != air) {
            this.entityData.markDirty(Entity.DATA_AIR_SUPPLY_ID);
            return;
        }
        this.entityData.set(Entity.DATA_AIR_SUPPLY_ID, event.getAmount());
        // CraftBukkit end
    }

    public int getTicksFrozen() {
        return this.entityData.get(DATA_TICKS_FROZEN);
    }

    public void setTicksFrozen(int ticksFrozen) {
        this.entityData.set(DATA_TICKS_FROZEN, ticksFrozen);
    }

    public float getPercentFrozen() {
        int ticksRequiredToFreeze = this.getTicksRequiredToFreeze();
        return (float)Math.min(this.getTicksFrozen(), ticksRequiredToFreeze) / ticksRequiredToFreeze;
    }

    public boolean isFullyFrozen() {
        return this.getTicksFrozen() >= this.getTicksRequiredToFreeze();
    }

    public int getTicksRequiredToFreeze() {
        return 140;
    }

    public void thunderHit(ServerLevel level, LightningBolt lightning) {
        this.setRemainingFireTicks(this.remainingFireTicks + 1);
        // CraftBukkit start
        final org.bukkit.entity.Entity thisBukkitEntity = this.getBukkitEntity();
        final org.bukkit.entity.Entity stormBukkitEntity = lightning.getBukkitEntity();
        final org.bukkit.plugin.PluginManager pluginManager = org.bukkit.Bukkit.getPluginManager();
        // CraftBukkit end
        if (this.remainingFireTicks == 0) {
            // CraftBukkit start - Call a combust event when lightning strikes
            org.bukkit.event.entity.EntityCombustByEntityEvent entityCombustEvent = new org.bukkit.event.entity.EntityCombustByEntityEvent(stormBukkitEntity, thisBukkitEntity, 8.0F);
            pluginManager.callEvent(entityCombustEvent);
            if (!entityCombustEvent.isCancelled()) {
                this.igniteForSeconds(entityCombustEvent.getDuration(), false);
            // Paper start - fix EntityCombustEvent cancellation
            } else {
                this.setRemainingFireTicks(this.remainingFireTicks - 1);
            // Paper end - fix EntityCombustEvent cancellation
            }
            // CraftBukkit end
        }

        // CraftBukkit start
        if (thisBukkitEntity instanceof org.bukkit.entity.Hanging) {
            org.bukkit.event.hanging.HangingBreakByEntityEvent hangingEvent = new org.bukkit.event.hanging.HangingBreakByEntityEvent((org.bukkit.entity.Hanging) thisBukkitEntity, stormBukkitEntity);
            pluginManager.callEvent(hangingEvent);

            if (hangingEvent.isCancelled()) {
                return;
            }
        }

        if (this.fireImmune()) {
            return;
        }

        if (!this.hurtServer(level, this.damageSources().lightningBolt().customEventDamager(lightning), 5.0F)) { // Paper - fix DamageSource API
            return;
        }
        // CraftBukkit end
    }

    public void onAboveBubbleCol(boolean downwards) {
        Vec3 deltaMovement = this.getDeltaMovement();
        double max;
        if (downwards) {
            max = Math.max(-0.9, deltaMovement.y - 0.03);
        } else {
            max = Math.min(1.8, deltaMovement.y + 0.1);
        }

        this.setDeltaMovement(deltaMovement.x, max, deltaMovement.z);
    }

    public void onInsideBubbleColumn(boolean downwards) {
        Vec3 deltaMovement = this.getDeltaMovement();
        double max;
        if (downwards) {
            max = Math.max(-0.3, deltaMovement.y - 0.03);
        } else {
            max = Math.min(0.7, deltaMovement.y + 0.06);
        }

        this.setDeltaMovement(deltaMovement.x, max, deltaMovement.z);
        this.resetFallDistance();
    }

    public boolean killedEntity(ServerLevel level, LivingEntity entity) {
        return true;
    }

    public void checkSlowFallDistance() {
        if (this.getDeltaMovement().y() > -0.5 && this.fallDistance > 1.0F) {
            this.fallDistance = 1.0F;
        }
    }

    public void resetFallDistance() {
        this.fallDistance = 0.0F;
    }

    protected void moveTowardsClosestSpace(double x, double y, double z) {
        BlockPos blockPos = BlockPos.containing(x, y, z);
        Vec3 vec3 = new Vec3(x - blockPos.getX(), y - blockPos.getY(), z - blockPos.getZ());
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        Direction direction = Direction.UP;
        double d = Double.MAX_VALUE;

        for (Direction direction1 : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP}) {
            mutableBlockPos.setWithOffset(blockPos, direction1);
            if (!this.level().getBlockState(mutableBlockPos).isCollisionShapeFullBlock(this.level(), mutableBlockPos)) {
                double d1 = vec3.get(direction1.getAxis());
                double d2 = direction1.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 - d1 : d1;
                if (d2 < d) {
                    d = d2;
                    direction = direction1;
                }
            }
        }

        float f = this.random.nextFloat() * 0.2F + 0.1F;
        float f1 = direction.getAxisDirection().getStep();
        Vec3 vec31 = this.getDeltaMovement().scale(0.75);
        if (direction.getAxis() == Direction.Axis.X) {
            this.setDeltaMovement(f1 * f, vec31.y, vec31.z);
        } else if (direction.getAxis() == Direction.Axis.Y) {
            this.setDeltaMovement(vec31.x, f1 * f, vec31.z);
        } else if (direction.getAxis() == Direction.Axis.Z) {
            this.setDeltaMovement(vec31.x, vec31.y, f1 * f);
        }
    }

    public void makeStuckInBlock(BlockState state, Vec3 motionMultiplier) {
        this.resetFallDistance();
        this.stuckSpeedMultiplier = motionMultiplier;
    }

    private static Component removeAction(Component name) {
        MutableComponent mutableComponent = name.plainCopy().setStyle(name.getStyle().withClickEvent(null));

        for (Component component : name.getSiblings()) {
            mutableComponent.append(removeAction(component));
        }

        return mutableComponent;
    }

    @Override
    public Component getName() {
        Component customName = this.getCustomName();
        return customName != null ? removeAction(customName) : this.getTypeName();
    }

    protected Component getTypeName() {
        return this.type.getDescription();
    }

    public boolean is(Entity entity) {
        return this == entity;
    }

    public float getYHeadRot() {
        return 0.0F;
    }

    public void setYHeadRot(float yHeadRot) {
    }

    public void setYBodyRot(float yBodyRot) {
    }

    public boolean isAttackable() {
        return true;
    }

    public boolean skipAttackInteraction(Entity entity) {
        return false;
    }

    @Override
    public String toString() {
        String string = this.level() == null ? "~NULL~" : this.level().toString();
        return this.removalReason != null
            ? String.format(
                Locale.ROOT,
                "%s['%s'/%d, uuid='%s', l='%s', x=%.2f, y=%.2f, z=%.2f, cpos=%s, tl=%d, v=%b, removed=%s]", // Paper - add more info
                this.getClass().getSimpleName(),
                this.getName().getString(),
                this.id,
                this.uuid, // Paper - add more info
                string,
                this.getX(),
                this.getY(),
                this.getZ(),
                this.chunkPosition(), this.tickCount, this.valid, // Paper - add more info
                this.removalReason
            )
            : String.format(
                Locale.ROOT,
                "%s['%s'/%d, uuid='%s', l='%s', x=%.2f, y=%.2f, z=%.2f, cpos=%s, tl=%d, v=%b]", // Paper - add more info
                this.getClass().getSimpleName(),
                this.getName().getString(),
                this.id,
                this.uuid, // Paper - add more info
                string,
                this.getX(),
                this.getY(),
                this.getZ(),
                this.chunkPosition(), this.tickCount, this.valid // Paper - add more info
            );
    }

    public final boolean isInvulnerableToBase(DamageSource damageSource) {
        return this.isRemoved()
            || this.invulnerable && !damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !damageSource.isCreativePlayer()
            || damageSource.is(DamageTypeTags.IS_FIRE) && this.fireImmune()
            || damageSource.is(DamageTypeTags.IS_FALL) && this.getType().is(EntityTypeTags.FALL_DAMAGE_IMMUNE);
    }

    public boolean isInvulnerable() {
        return this.invulnerable;
    }

    public void setInvulnerable(boolean isInvulnerable) {
        this.invulnerable = isInvulnerable;
    }

    public void copyPosition(Entity entity) {
        this.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
    }

    public void restoreFrom(Entity entity) {
        // Paper start - Forward CraftEntity in teleport command
        org.bukkit.craftbukkit.entity.CraftEntity bukkitEntity = entity.bukkitEntity;
        if (bukkitEntity != null) {
            bukkitEntity.setHandle(this);
            this.bukkitEntity = bukkitEntity;
        }
        // Paper end - Forward CraftEntity in teleport command
        CompoundTag compoundTag = entity.saveWithoutId(new CompoundTag());
        compoundTag.remove("Dimension");
        this.load(compoundTag);
        this.portalCooldown = entity.portalCooldown;
        this.portalProcess = entity.portalProcess;
    }

    @Nullable
    public Entity teleport(TeleportTransition teleportTransition) {
        // Paper start - Fix item duplication and teleport issues
        if ((!this.isAlive() || !this.valid) && (teleportTransition.newLevel() != this.level)) {
            LOGGER.warn("Illegal Entity Teleport " + this + " to " + teleportTransition.newLevel() + ":" + teleportTransition.position(), new Throwable());
            return null;
        }
        // Paper end - Fix item duplication and teleport issues
        if (this.level() instanceof ServerLevel serverLevel && !this.isRemoved()) {
            // CraftBukkit start
            PositionMoveRotation absolutePosition = PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(this), PositionMoveRotation.of(teleportTransition), teleportTransition.relatives());
            Vec3 velocity = absolutePosition.deltaMovement(); // Paper
            org.bukkit.Location to = org.bukkit.craftbukkit.util.CraftLocation.toBukkit(absolutePosition.position(), teleportTransition.newLevel().getWorld(), absolutePosition.yRot(), absolutePosition.xRot());
            // Paper start - gateway-specific teleport event
            final org.bukkit.event.entity.EntityTeleportEvent teleEvent;
            if (this.portalProcess != null && this.portalProcess.isSamePortal(((net.minecraft.world.level.block.EndGatewayBlock) net.minecraft.world.level.block.Blocks.END_GATEWAY)) && this.level.getBlockEntity(this.portalProcess.getEntryPosition()) instanceof net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity theEndGatewayBlockEntity) {
                teleEvent = new com.destroystokyo.paper.event.entity.EntityTeleportEndGatewayEvent(this.getBukkitEntity(), this.getBukkitEntity().getLocation(), to, new org.bukkit.craftbukkit.block.CraftEndGateway(to.getWorld(), theEndGatewayBlockEntity));
                teleEvent.callEvent();
            } else {
                teleEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTeleportEvent(this, to);
            }
            // Paper end - gateway-specific teleport event
            if (teleEvent.isCancelled() || teleEvent.getTo() == null) {
                return null;
            }
            if (!to.equals(teleEvent.getTo())) {
                to = teleEvent.getTo();
                teleportTransition = new TeleportTransition(((org.bukkit.craftbukkit.CraftWorld) to.getWorld()).getHandle(), org.bukkit.craftbukkit.util.CraftLocation.toVec3D(to), Vec3.ZERO, to.getYaw(), to.getPitch(), teleportTransition.missingRespawnBlock(), teleportTransition.asPassenger(), Set.of(), teleportTransition.postTeleportTransition(), teleportTransition.cause());
                // Paper start - Call EntityPortalExitEvent
                velocity = Vec3.ZERO;
            }
            if (this.portalProcess != null) { // if in a portal
                org.bukkit.craftbukkit.entity.CraftEntity bukkitEntity = this.getBukkitEntity();
                org.bukkit.event.entity.EntityPortalExitEvent event = new org.bukkit.event.entity.EntityPortalExitEvent(
                    bukkitEntity,
                    bukkitEntity.getLocation(), to.clone(),
                    bukkitEntity.getVelocity(), org.bukkit.craftbukkit.util.CraftVector.toBukkit(velocity)
                );
                event.callEvent();

                // Only change the target if actually needed, since we reset relative flags
                if (!event.isCancelled() && event.getTo() != null && (!event.getTo().equals(event.getFrom()) || !event.getAfter().equals(event.getBefore()))) {
                    to = event.getTo().clone();
                    velocity = org.bukkit.craftbukkit.util.CraftVector.toNMS(event.getAfter());
                    teleportTransition = new TeleportTransition(((org.bukkit.craftbukkit.CraftWorld) to.getWorld()).getHandle(), org.bukkit.craftbukkit.util.CraftLocation.toVec3D(to), velocity, to.getYaw(), to.getPitch(), teleportTransition.missingRespawnBlock(), teleportTransition.asPassenger(), Set.of(), teleportTransition.postTeleportTransition(), teleportTransition.cause());
                }
            }
            if (this.isRemoved()) {
                return null;
            }
            // Paper end - Call EntityPortalExitEvent
            // CraftBukkit end
            ServerLevel level = teleportTransition.newLevel();
            boolean flag = level.dimension() != serverLevel.dimension();
            if (!teleportTransition.asPassenger()) {
                this.stopRiding();
            }

            return flag ? this.teleportCrossDimension(level, teleportTransition) : this.teleportSameDimension(serverLevel, teleportTransition);
        } else {
            return null;
        }
    }

    private Entity teleportSameDimension(ServerLevel level, TeleportTransition teleportTransition) {
        for (Entity entity : this.getPassengers()) {
            entity.teleport(this.calculatePassengerTransition(teleportTransition, entity));
        }

        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("teleportSameDimension");
        this.teleportSetPosition(PositionMoveRotation.of(teleportTransition), teleportTransition.relatives());
        if (!teleportTransition.asPassenger()) {
            this.sendTeleportTransitionToRidingPlayers(teleportTransition);
        }

        teleportTransition.postTeleportTransition().onTransition(this);
        profilerFiller.pop();
        return this;
    }

    private Entity teleportCrossDimension(ServerLevel level, TeleportTransition teleportTransition) {
        List<Entity> passengers = this.getPassengers();
        List<Entity> list = new ArrayList<>(passengers.size());
        this.ejectPassengers();

        for (Entity entity : passengers) {
            Entity entity1 = entity.teleport(this.calculatePassengerTransition(teleportTransition, entity));
            if (entity1 != null) {
                list.add(entity1);
            }
        }

        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("teleportCrossDimension");
        Entity entityx = this.getType().create(level, EntitySpawnReason.DIMENSION_TRAVEL);
        if (entityx == null) {
            profilerFiller.pop();
            return null;
        } else {
            // Paper start - Fix item duplication and teleport issues
            if (this instanceof Leashable leashable) {
                leashable.dropLeash(); // Paper drop lead
            }
            // Paper end - Fix item duplication and teleport issues
            entityx.restoreFrom(this);
            this.removeAfterChangingDimensions();
            // CraftBukkit start - Forward the CraftEntity to the new entity
            //this.getBukkitEntity().setHandle(entity);
            //entity.bukkitEntity = this.getBukkitEntity(); // Paper - forward CraftEntity in teleport command; moved to Entity#restoreFrom
            // CraftBukkit end
            entityx.teleportSetPosition(PositionMoveRotation.of(teleportTransition), teleportTransition.relatives());
            if (this.inWorld) level.addDuringTeleport(entityx); // CraftBukkit - Don't spawn the new entity if the current entity isn't spawned

            for (Entity entity2 : list) {
                entity2.startRiding(entityx, true);
            }

            level.resetEmptyTime();
            teleportTransition.postTeleportTransition().onTransition(entityx);
            profilerFiller.pop();
            return entityx;
        }
    }

    private TeleportTransition calculatePassengerTransition(TeleportTransition teleportTransition, Entity entity) {
        float f = teleportTransition.yRot() + (teleportTransition.relatives().contains(Relative.Y_ROT) ? 0.0F : entity.getYRot() - this.getYRot());
        float f1 = teleportTransition.xRot() + (teleportTransition.relatives().contains(Relative.X_ROT) ? 0.0F : entity.getXRot() - this.getXRot());
        Vec3 vec3 = entity.position().subtract(this.position());
        Vec3 vec31 = teleportTransition.position()
            .add(
                teleportTransition.relatives().contains(Relative.X) ? 0.0 : vec3.x(),
                teleportTransition.relatives().contains(Relative.Y) ? 0.0 : vec3.y(),
                teleportTransition.relatives().contains(Relative.Z) ? 0.0 : vec3.z()
            );
        return teleportTransition.withPosition(vec31).withRotation(f, f1).transitionAsPassenger();
    }

    private void sendTeleportTransitionToRidingPlayers(TeleportTransition teleportTransition) {
        Entity controllingPassenger = this.getControllingPassenger();

        for (Entity entity : this.getIndirectPassengers()) {
            if (entity instanceof ServerPlayer) {
                ServerPlayer serverPlayer = (ServerPlayer)entity;
                if (controllingPassenger != null && serverPlayer.getId() == controllingPassenger.getId()) {
                    serverPlayer.connection
                        .send(
                            ClientboundTeleportEntityPacket.teleport(
                                this.getId(), PositionMoveRotation.of(teleportTransition), teleportTransition.relatives(), this.onGround
                            )
                        );
                } else {
                    serverPlayer.connection
                        .send(ClientboundTeleportEntityPacket.teleport(this.getId(), PositionMoveRotation.of(this), Set.of(), this.onGround));
                }
            }
        }
    }

    public void teleportSetPosition(PositionMoveRotation positionMovementRotation, Set<Relative> relatives) {
        PositionMoveRotation positionMoveRotation = PositionMoveRotation.of(this);
        PositionMoveRotation positionMoveRotation1 = PositionMoveRotation.calculateAbsolute(positionMoveRotation, positionMovementRotation, relatives);
        this.setPosRaw(positionMoveRotation1.position().x, positionMoveRotation1.position().y, positionMoveRotation1.position().z);
        this.setYRot(positionMoveRotation1.yRot());
        this.setYHeadRot(positionMoveRotation1.yRot());
        this.setXRot(positionMoveRotation1.xRot());
        this.reapplyPosition();
        this.setOldPosAndRot();
        this.setDeltaMovement(positionMoveRotation1.deltaMovement());
        this.movementThisTick.clear();
    }

    public void forceSetRotation(float yRot, float xRot) {
        this.setYRot(yRot);
        this.setYHeadRot(yRot);
        this.setXRot(xRot);
        this.setOldRot();
    }

    public void placePortalTicket(BlockPos pos) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(pos), 3, pos);
        }
    }

    protected void removeAfterChangingDimensions() {
        this.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION, null); // CraftBukkit - add Bukkit remove cause
        if (this instanceof Leashable leashable && leashable.isLeashed()) { // Paper - only call if it is leashed
            // Paper start - Expand EntityUnleashEvent
            final org.bukkit.event.entity.EntityUnleashEvent event = new org.bukkit.event.entity.EntityUnleashEvent(this.getBukkitEntity(), org.bukkit.event.entity.EntityUnleashEvent.UnleashReason.UNKNOWN, false); // CraftBukkit
            event.callEvent();
            if (!event.isDropLeash()) {
                leashable.removeLeash();
            } else {
                leashable.dropLeash();
            }
            // Paper end - Expand EntityUnleashEvent
        }
    }

    public Vec3 getRelativePortalPosition(Direction.Axis axis, BlockUtil.FoundRectangle portal) {
        return PortalShape.getRelativePosition(portal, axis, this.position(), this.getDimensions(this.getPose()));
    }

    // CraftBukkit start
    public org.bukkit.craftbukkit.event.CraftPortalEvent callPortalEvent(Entity entity, org.bukkit.Location exit, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause, int searchRadius, int creationRadius) {
        org.bukkit.entity.Entity bukkitEntity = entity.getBukkitEntity();
        org.bukkit.Location enter = bukkitEntity.getLocation();

        // Paper start
        final org.bukkit.PortalType portalType = switch (cause) {
            case END_PORTAL -> org.bukkit.PortalType.ENDER;
            case NETHER_PORTAL -> org.bukkit.PortalType.NETHER;
            case END_GATEWAY -> org.bukkit.PortalType.END_GATEWAY; // not actually used yet
            default -> org.bukkit.PortalType.CUSTOM;
        };
        org.bukkit.event.entity.EntityPortalEvent event = new org.bukkit.event.entity.EntityPortalEvent(bukkitEntity, enter, exit, searchRadius, true, creationRadius, portalType);
        // Paper end
        event.getEntity().getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getTo() == null || event.getTo().getWorld() == null || !entity.isAlive()) {
            return null;
        }
        return new org.bukkit.craftbukkit.event.CraftPortalEvent(event);
    }
    // CraftBukkit end

    public boolean canUsePortal(boolean allowPassengers) {
        return (allowPassengers || !this.isPassenger()) && this.isAlive() && (this.level.purpurConfig.entitiesCanUsePortals || this instanceof ServerPlayer); // Purpur - Entities can use portals
    }

    public boolean canTeleport(Level fromLevel, Level toLevel) {
        if (!this.isAlive() || !this.valid) return false; // Paper - Fix item duplication and teleport issues
        if (fromLevel.dimension() == Level.END && toLevel.dimension() == Level.OVERWORLD) {
            for (Entity entity : this.getPassengers()) {
                if (entity instanceof ServerPlayer serverPlayer && !serverPlayer.seenCredits) {
                    return false;
                }
            }
        }

        return true;
    }

    public float getBlockExplosionResistance(
        Explosion explosion, BlockGetter level, BlockPos pos, BlockState blockState, FluidState fluidState, float explosionPower
    ) {
        return explosionPower;
    }

    public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState blockState, float explosionPower) {
        return true;
    }

    public int getMaxFallDistance() {
        return 3;
    }

    public boolean isIgnoringBlockTriggers() {
        return false;
    }

    public void fillCrashReportCategory(CrashReportCategory category) {
        category.setDetail("Entity Type", () -> EntityType.getKey(this.getType()) + " (" + this.getClass().getCanonicalName() + ")");
        category.setDetail("Entity ID", this.id);
        category.setDetail("Entity Name", () -> this.getName().getString());
        category.setDetail("Entity's Exact location", String.format(Locale.ROOT, "%.2f, %.2f, %.2f", this.getX(), this.getY(), this.getZ()));
        category.setDetail(
            "Entity's Block location", CrashReportCategory.formatLocation(this.level(), Mth.floor(this.getX()), Mth.floor(this.getY()), Mth.floor(this.getZ()))
        );
        Vec3 deltaMovement = this.getDeltaMovement();
        category.setDetail("Entity's Momentum", String.format(Locale.ROOT, "%.2f, %.2f, %.2f", deltaMovement.x, deltaMovement.y, deltaMovement.z));
        category.setDetail("Entity's Passengers", () -> this.getPassengers().toString());
        category.setDetail("Entity's Vehicle", () -> String.valueOf(this.getVehicle()));
    }

    public boolean displayFireAnimation() {
        return this.isOnFire() && !this.isSpectator();
    }

    public void setUUID(UUID uniqueId) {
        this.uuid = uniqueId;
        this.stringUUID = this.uuid.toString();
    }

    @Override
    public UUID getUUID() {
        return this.uuid;
    }

    public String getStringUUID() {
        return this.stringUUID;
    }

    @Override
    public String getScoreboardName() {
        return this.stringUUID;
    }

    public boolean isPushedByFluid() {
        return true;
    }

    public static double getViewScale() {
        return viewScale;
    }

    public static void setViewScale(double renderDistWeight) {
        viewScale = renderDistWeight;
    }

    @Override
    public Component getDisplayName() {
        return PlayerTeam.formatNameForTeam(this.getTeam(), this.getName())
            .withStyle(style -> style.withHoverEvent(this.createHoverEvent()).withInsertion(this.getStringUUID()));
    }

    public void setCustomName(@Nullable Component name) {
        this.entityData.set(DATA_CUSTOM_NAME, Optional.ofNullable(name));
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return this.entityData.get(DATA_CUSTOM_NAME).orElse(null);
    }

    @Override
    public boolean hasCustomName() {
        return this.entityData.get(DATA_CUSTOM_NAME).isPresent();
    }

    public void setCustomNameVisible(boolean alwaysRenderNameTag) {
        this.entityData.set(DATA_CUSTOM_NAME_VISIBLE, alwaysRenderNameTag);
    }

    public boolean isCustomNameVisible() {
        return this.entityData.get(DATA_CUSTOM_NAME_VISIBLE);
    }

    // CraftBukkit start
    public final boolean teleportTo(ServerLevel level, double x, double y, double z, Set<Relative> relativeMovements, float yaw, float pitch, boolean setCamera) {
        return this.teleportTo(level, x, y, z, relativeMovements, yaw, pitch, setCamera, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }
    public boolean teleportTo(ServerLevel level, double x, double y, double z, Set<Relative> relativeMovements, float yaw, float pitch, boolean setCamera, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        // CraftBukkit end
        float f = Mth.clamp(pitch, -90.0F, 90.0F);
        Entity entity = this.teleport(new TeleportTransition(level, new Vec3(x, y, z), Vec3.ZERO, yaw, f, relativeMovements, TeleportTransition.DO_NOTHING, cause)); // CraftBukkit
        return entity != null;
    }

    public void dismountTo(double x, double y, double z) {
        this.teleportTo(x, y, z);
    }

    public void teleportTo(double x, double y, double z) {
        if (this.level() instanceof ServerLevel) {
            this.moveTo(x, y, z, this.getYRot(), this.getXRot());
            this.teleportPassengers();
        }
    }

    public void teleportPassengers() {
        this.getSelfAndPassengers().forEach(entity -> {
            for (Entity entity1 : entity.passengers) {
                entity.positionRider(entity1, Entity::moveTo);
            }
        });
    }

    public void teleportRelative(double dx, double dy, double dz) {
        this.teleportTo(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
    }

    public boolean shouldShowName() {
        return this.isCustomNameVisible();
    }

    @Override
    public void onSyncedDataUpdated(List<SynchedEntityData.DataValue<?>> dataValues) {
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_POSE.equals(key)) {
            this.refreshDimensions();
        }
    }

    @Deprecated
    protected void fixupDimensions() {
        Pose pose = this.getPose();
        EntityDimensions dimensions = this.getDimensions(pose);
        this.dimensions = dimensions;
        this.eyeHeight = dimensions.eyeHeight();
    }

    public void refreshDimensions() {
        EntityDimensions entityDimensions = this.dimensions;
        Pose pose = this.getPose();
        EntityDimensions dimensions = this.getDimensions(pose);
        this.dimensions = dimensions;
        this.eyeHeight = dimensions.eyeHeight();
        this.reapplyPosition();
        boolean flag = dimensions.width() <= 4.0F && dimensions.height() <= 4.0F;
        if (!this.level.isClientSide
            && !this.firstTick
            && !this.noPhysics
            && flag
            && (dimensions.width() > entityDimensions.width() || dimensions.height() > entityDimensions.height())
            && !(this instanceof Player)) {
            this.fudgePositionAfterSizeChange(entityDimensions);
        }
    }

    public boolean fudgePositionAfterSizeChange(EntityDimensions dimensions) {
        EntityDimensions dimensions1 = this.getDimensions(this.getPose());
        Vec3 vec3 = this.position().add(0.0, dimensions.height() / 2.0, 0.0);
        double d = Math.max(0.0F, dimensions1.width() - dimensions.width()) + 1.0E-6;
        double d1 = Math.max(0.0F, dimensions1.height() - dimensions.height()) + 1.0E-6;
        VoxelShape voxelShape = Shapes.create(AABB.ofSize(vec3, d, d1, d));
        Optional<Vec3> optional = this.level.findFreePosition(this, voxelShape, vec3, dimensions1.width(), dimensions1.height(), dimensions1.width());
        if (optional.isPresent()) {
            this.setPos(optional.get().add(0.0, -dimensions1.height() / 2.0, 0.0));
            return true;
        } else {
            if (dimensions1.width() > dimensions.width() && dimensions1.height() > dimensions.height()) {
                VoxelShape voxelShape1 = Shapes.create(AABB.ofSize(vec3, d, 1.0E-6, d));
                Optional<Vec3> optional1 = this.level.findFreePosition(this, voxelShape1, vec3, dimensions1.width(), dimensions.height(), dimensions1.width());
                if (optional1.isPresent()) {
                    this.setPos(optional1.get().add(0.0, -dimensions.height() / 2.0 + 1.0E-6, 0.0));
                    return true;
                }
            }

            return false;
        }
    }

    public Direction getDirection() {
        return Direction.fromYRot(this.getYRot());
    }

    public Direction getMotionDirection() {
        return this.getDirection();
    }

    protected HoverEvent createHoverEvent() {
        return new HoverEvent(HoverEvent.Action.SHOW_ENTITY, new HoverEvent.EntityTooltipInfo(this.getType(), this.getUUID(), this.getName()));
    }

    public boolean broadcastToPlayer(ServerPlayer player) {
        return true;
    }

    @Override
    public final AABB getBoundingBox() {
        return this.bb;
    }

    public final void setBoundingBox(AABB bb) {
        // CraftBukkit start - block invalid bounding boxes
        double minX = bb.minX,
                minY = bb.minY,
                minZ = bb.minZ,
                maxX = bb.maxX,
                maxY = bb.maxY,
                maxZ = bb.maxZ;
        double len = bb.maxX - bb.minX;
        if (len < 0) maxX = minX;
        if (len > 64) maxX = minX + 64.0;

        len = bb.maxY - bb.minY;
        if (len < 0) maxY = minY;
        if (len > 64) maxY = minY + 64.0;

        len = bb.maxZ - bb.minZ;
        if (len < 0) maxZ = minZ;
        if (len > 64) maxZ = minZ + 64.0;
        this.bb = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        // CraftBukkit end
    }

    public final float getEyeHeight(Pose pose) {
        return this.getDimensions(pose).eyeHeight();
    }

    public final float getEyeHeight() {
        return this.eyeHeight;
    }

    public Vec3 getLeashOffset(float partialTick) {
        return this.getLeashOffset();
    }

    protected Vec3 getLeashOffset() {
        return new Vec3(0.0, this.getEyeHeight(), this.getBbWidth() * 0.4F);
    }

    public SlotAccess getSlot(int slot) {
        return SlotAccess.NULL;
    }

    public Level getCommandSenderWorld() {
        return this.level();
    }

    @Nullable
    public MinecraftServer getServer() {
        return this.level().getServer();
    }

    public InteractionResult interactAt(Player player, Vec3 vec, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    public boolean ignoreExplosion(Explosion explosion) {
        return false;
    }

    public void startSeenByPlayer(ServerPlayer serverPlayer) {
    }

    public void stopSeenByPlayer(ServerPlayer serverPlayer) {
        // Paper start - entity tracking events
        // Since this event cannot be cancelled, we should call it here to catch all "un-tracks"
        if (io.papermc.paper.event.player.PlayerUntrackEntityEvent.getHandlerList().getRegisteredListeners().length > 0) {
            new io.papermc.paper.event.player.PlayerUntrackEntityEvent(serverPlayer.getBukkitEntity(), this.getBukkitEntity()).callEvent();
        }
        // Paper end - entity tracking events
    }

    public float rotate(Rotation transformRotation) {
        float f = Mth.wrapDegrees(this.getYRot());
        switch (transformRotation) {
            case CLOCKWISE_180:
                return f + 180.0F;
            case COUNTERCLOCKWISE_90:
                return f + 270.0F;
            case CLOCKWISE_90:
                return f + 90.0F;
            default:
                return f;
        }
    }

    public float mirror(Mirror transformMirror) {
        float f = Mth.wrapDegrees(this.getYRot());
        switch (transformMirror) {
            case FRONT_BACK:
                return -f;
            case LEFT_RIGHT:
                return 180.0F - f;
            default:
                return f;
        }
    }

    public ProjectileDeflection deflection(Projectile projectile) {
        return this.getType().is(EntityTypeTags.DEFLECTS_PROJECTILES) ? ProjectileDeflection.REVERSE : ProjectileDeflection.NONE;
    }

    @Nullable
    public net.minecraft.world.entity.LivingEntity getControllingPassenger() {
        return null;
    }

    public final boolean hasControllingPassenger() {
        return this.getControllingPassenger() != null;
    }

    public final List<Entity> getPassengers() {
        return this.passengers;
    }

    @Nullable
    public Entity getFirstPassenger() {
        return this.passengers.isEmpty() ? null : this.passengers.get(0);
    }

    public boolean hasPassenger(Entity entity) {
        return this.passengers.contains(entity);
    }

    public boolean hasPassenger(Predicate<Entity> predicate) {
        for (Entity entity : this.passengers) {
            if (predicate.test(entity)) {
                return true;
            }
        }

        return false;
    }

    private Stream<Entity> getIndirectPassengersStream() {
        if (this.passengers.isEmpty()) { return Stream.of(); } // Paper - Optimize indirect passenger iteration
        return this.passengers.stream().flatMap(Entity::getSelfAndPassengers);
    }

    @Override
    public Stream<Entity> getSelfAndPassengers() {
        if (this.passengers.isEmpty()) { return Stream.of(this); } // Paper - Optimize indirect passenger iteration
        return Stream.concat(Stream.of(this), this.getIndirectPassengersStream());
    }

    @Override
    public Stream<Entity> getPassengersAndSelf() {
        if (this.passengers.isEmpty()) { return Stream.of(this); } // Paper - Optimize indirect passenger iteration
        return Stream.concat(this.passengers.stream().flatMap(Entity::getPassengersAndSelf), Stream.of(this));
    }

    public Iterable<Entity> getIndirectPassengers() {
        // Paper start - optimise entity tracker
        final List<Entity> ret = new ArrayList<>();

        if (this.passengers.isEmpty()) {
            return ret;
        }

        collectIndirectPassengers(ret, this.passengers);

        return ret;
        // Paper end - optimise entity tracker
    }

    public int countPlayerPassengers() {
        return (int)this.getIndirectPassengersStream().filter(entity -> entity instanceof Player).count();
    }

    public boolean hasExactlyOnePlayerPassenger() {
        if (this.passengers.isEmpty()) { return false; } // Paper - Optimize indirect passenger iteration
        return this.countPlayerPassengers() == 1;
    }

    public Entity getRootVehicle() {
        Entity entity = this;

        while (entity.isPassenger()) {
            entity = entity.getVehicle();
        }

        return entity;
    }

    public boolean isPassengerOfSameVehicle(Entity entity) {
        return this.getRootVehicle() == entity.getRootVehicle();
    }

    public boolean hasIndirectPassenger(Entity entity) {
        if (!entity.isPassenger()) {
            return false;
        } else {
            Entity vehicle = entity.getVehicle();
            return vehicle == this || this.hasIndirectPassenger(vehicle);
        }
    }

    public boolean isControlledByOrIsLocalPlayer() {
        return this instanceof Player player ? player.isLocalPlayer() : this.isControlledByLocalInstance();
    }

    public boolean isControlledByLocalInstance() {
        return this.getControllingPassenger() instanceof Player player ? player.isLocalPlayer() : this.isEffectiveAi();
    }

    public boolean isControlledByClient() {
        LivingEntity controllingPassenger = this.getControllingPassenger();
        return controllingPassenger != null && controllingPassenger.isControlledByClient();
    }

    public boolean isEffectiveAi() {
        return !this.level().isClientSide;
    }

    protected static Vec3 getCollisionHorizontalEscapeVector(double vehicleWidth, double passengerWidth, float yRot) {
        double d = (vehicleWidth + passengerWidth + 1.0E-5F) / 2.0;
        float f = -Mth.sin(yRot * (float) (Math.PI / 180.0));
        float cos = Mth.cos(yRot * (float) (Math.PI / 180.0));
        float max = Math.max(Math.abs(f), Math.abs(cos));
        return new Vec3(f * d / max, 0.0, cos * d / max);
    }

    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return new Vec3(this.getX(), this.getBoundingBox().maxY, this.getZ());
    }

    @Nullable
    public Entity getVehicle() {
        return this.vehicle;
    }

    @Nullable
    public Entity getControlledVehicle() {
        return this.vehicle != null && this.vehicle.getControllingPassenger() == this ? this.vehicle : null;
    }

    public PushReaction getPistonPushReaction() {
        return PushReaction.NORMAL;
    }

    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }

    public int getFireImmuneTicks() {
        return 1;
    }

    // CraftBukkit start
    private final CommandSource commandSource = new CommandSource() {

        @Override
        public void sendSystemMessage(Component message) {
        }

        @Override
        public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
            return Entity.this.getBukkitEntity();
        }

        @Override
        public boolean acceptsSuccess() {
            return ((ServerLevel) Entity.this.level()).getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_SENDCOMMANDFEEDBACK);
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return true;
        }
    };
    // CraftBukkit end

    public CommandSourceStack createCommandSourceStackForNameResolution(ServerLevel level) {
        return new CommandSourceStack(
            this.commandSource, this.position(), this.getRotationVector(), level, 0, this.getName().getString(), this.getDisplayName(), level.getServer(), this // CraftBukkit
        );
    }

    public void lookAt(EntityAnchorArgument.Anchor anchor, Vec3 target) {
        Vec3 vec3 = anchor.apply(this);
        double d = target.x - vec3.x;
        double d1 = target.y - vec3.y;
        double d2 = target.z - vec3.z;
        double squareRoot = Math.sqrt(d * d + d2 * d2);
        this.setXRot(Mth.wrapDegrees((float)(-(Mth.atan2(d1, squareRoot) * 180.0F / (float)Math.PI))));
        this.setYRot(Mth.wrapDegrees((float)(Mth.atan2(d2, d) * 180.0F / (float)Math.PI) - 90.0F));
        this.setYHeadRot(this.getYRot());
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
    }

    public float getPreciseBodyRotation(float partialTick) {
        return Mth.lerp(partialTick, this.yRotO, this.yRot);
    }

    // Purpur start - Stop squids floating on top of water
    public AABB getAxisForFluidCheck() {
        return this.getBoundingBox().deflate(0.001D);
    }
    // Purpur end - Stop squids floating on top of water

    // Paper start - optimise collisions
    public boolean updateFluidHeightAndDoFluidPushing(final TagKey<Fluid> fluid, final double flowScale) {
        if (this.touchingUnloadedChunk()) {
            return false;
        }

        final AABB boundingBox = this.getBoundingBox().deflate(1.0E-3);

        final Level world = this.level;
        final int minSection = ca.spottedleaf.moonrise.common.util.WorldUtil.getMinSection(world);

        final int minBlockX = Mth.floor(boundingBox.minX);
        final int minBlockY = Math.max((minSection << 4), Mth.floor(boundingBox.minY));
        final int minBlockZ = Mth.floor(boundingBox.minZ);

        // note: bounds are exclusive in Vanilla, so we subtract 1 - our loop expects bounds to be inclusive
        final int maxBlockX = Mth.ceil(boundingBox.maxX) - 1;
        final int maxBlockY = Math.min((ca.spottedleaf.moonrise.common.util.WorldUtil.getMaxSection(world) << 4) | 15, Mth.ceil(boundingBox.maxY) - 1);
        final int maxBlockZ = Mth.ceil(boundingBox.maxZ) - 1;

        final boolean isPushable = this.isPushedByFluid();
        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        Vec3 pushVector = Vec3.ZERO;
        double totalPushes = 0.0;
        double maxHeightDiff = 0.0;
        boolean inFluid = false;

        final int minChunkX = minBlockX >> 4;
        final int maxChunkX = maxBlockX >> 4;

        final int minChunkY = minBlockY >> 4;
        final int maxChunkY = maxBlockY >> 4;

        final int minChunkZ = minBlockZ >> 4;
        final int maxChunkZ = maxBlockZ >> 4;

        final net.minecraft.world.level.chunk.ChunkSource chunkSource = world.getChunkSource();

        for (int currChunkZ = minChunkZ; currChunkZ <= maxChunkZ; ++currChunkZ) {
            for (int currChunkX = minChunkX; currChunkX <= maxChunkX; ++currChunkX) {
                final net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunkSource.getChunk(currChunkX, currChunkZ, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false).getSections();

                // bound y
                for (int currChunkY = minChunkY; currChunkY <= maxChunkY; ++currChunkY) {
                    final int sectionIdx = currChunkY - minSection;
                    if (sectionIdx < 0 || sectionIdx >= sections.length) {
                        continue;
                    }
                    final net.minecraft.world.level.chunk.LevelChunkSection section = sections[sectionIdx];
                    if (section.hasOnlyAir()) {
                        // empty
                        continue;
                    }

                    final net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.world.level.block.state.BlockState> blocks = section.states;

                    final int minXIterate = currChunkX == minChunkX ? (minBlockX & 15) : 0;
                    final int maxXIterate = currChunkX == maxChunkX ? (maxBlockX & 15) : 15;
                    final int minZIterate = currChunkZ == minChunkZ ? (minBlockZ & 15) : 0;
                    final int maxZIterate = currChunkZ == maxChunkZ ? (maxBlockZ & 15) : 15;
                    final int minYIterate = currChunkY == minChunkY ? (minBlockY & 15) : 0;
                    final int maxYIterate = currChunkY == maxChunkY ? (maxBlockY & 15) : 15;

                    for (int currY = minYIterate; currY <= maxYIterate; ++currY) {
                        for (int currZ = minZIterate; currZ <= maxZIterate; ++currZ) {
                            for (int currX = minXIterate; currX <= maxXIterate; ++currX) {
                                final FluidState fluidState = blocks.get((currX) | (currZ << 4) | ((currY) << 8)).getFluidState();

                                if (fluidState.isEmpty() || !fluidState.is(fluid)) {
                                    continue;
                                }

                                mutablePos.set(currX | (currChunkX << 4), currY | (currChunkY << 4), currZ | (currChunkZ << 4));

                                final double height = (double)((float)mutablePos.getY() + fluidState.getHeight(world, mutablePos));
                                final double diff = height - boundingBox.minY;

                                if (diff < 0.0) {
                                    continue;
                                }

                                inFluid = true;
                                maxHeightDiff = Math.max(maxHeightDiff, diff);

                                if (!isPushable) {
                                    continue;
                                }

                                ++totalPushes;

                                final Vec3 flow = fluidState.getFlow(world, mutablePos);

                                if (diff < 0.4) {
                                    pushVector = pushVector.add(flow.scale(diff));
                                } else {
                                    pushVector = pushVector.add(flow);
                                }
                            }
                        }
                    }
                }
            }
        }

        this.fluidHeight.put(fluid, maxHeightDiff);

        if (pushVector.lengthSqr() == 0.0) {
            return inFluid;
        }

        // note: totalPushes != 0 as pushVector != 0
        pushVector = pushVector.scale(1.0 / totalPushes);
        final Vec3 currMovement = this.getDeltaMovement();

        if (!((Entity)(Object)this instanceof Player)) {
            pushVector = pushVector.normalize();
        }

        pushVector = pushVector.scale(flowScale);
        if (Math.abs(currMovement.x) < 0.003 && Math.abs(currMovement.z) < 0.003 && pushVector.length() < 0.0045000000000000005) {
            pushVector = pushVector.normalize().scale(0.0045000000000000005);
        }

        this.setDeltaMovement(currMovement.add(pushVector));

        // note: inFluid = true here as pushVector != 0
        return true;
    }
    // Paper end - optimise collisions

    public boolean touchingUnloadedChunk() {
        AABB aabb = this.getBoundingBox().inflate(1.0);
        int floor = Mth.floor(aabb.minX);
        int ceil = Mth.ceil(aabb.maxX);
        int floor1 = Mth.floor(aabb.minZ);
        int ceil1 = Mth.ceil(aabb.maxZ);
        return !this.level().hasChunksAt(floor, floor1, ceil, ceil1);
    }

    public double getFluidHeight(TagKey<Fluid> fluidTag) {
        return this.fluidHeight.getDouble(fluidTag);
    }

    public double getFluidJumpThreshold() {
        return this.getEyeHeight() < 0.4 ? 0.0 : 0.4;
    }

    public final float getBbWidth() {
        return this.dimensions.width();
    }

    public final float getBbHeight() {
        return this.dimensions.height();
    }

    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity);
    }

    public EntityDimensions getDimensions(Pose pose) {
        return this.type.getDimensions();
    }

    public final EntityAttachments getAttachments() {
        return this.dimensions.attachments();
    }

    public Vec3 position() {
        return this.position;
    }

    public Vec3 trackingPosition() {
        return this.position();
    }

    @Override
    public BlockPos blockPosition() {
        return this.blockPosition;
    }

    public BlockState getInBlockState() {
        if (this.inBlockState == null) {
            this.inBlockState = this.level().getBlockState(this.blockPosition());
        }

        return this.inBlockState;
    }

    public ChunkPos chunkPosition() {
        return this.chunkPosition;
    }

    public Vec3 getDeltaMovement() {
        return this.deltaMovement;
    }

    public void setDeltaMovement(Vec3 deltaMovement) {
        synchronized (this.posLock) { // Paper - detailed watchdog information
        this.deltaMovement = deltaMovement;
        } // Paper - detailed watchdog information
    }

    public void addDeltaMovement(Vec3 addend) {
        this.setDeltaMovement(this.getDeltaMovement().add(addend));
    }

    public void setDeltaMovement(double x, double y, double z) {
        this.setDeltaMovement(new Vec3(x, y, z));
    }

    public final int getBlockX() {
        return this.blockPosition.getX();
    }

    public final double getX() {
        return this.position.x;
    }

    public double getX(double scale) {
        return this.position.x + this.getBbWidth() * scale;
    }

    public double getRandomX(double scale) {
        return this.getX((2.0 * this.random.nextDouble() - 1.0) * scale);
    }

    public final int getBlockY() {
        return this.blockPosition.getY();
    }

    public final double getY() {
        return this.position.y;
    }

    public double getY(double scale) {
        return this.position.y + this.getBbHeight() * scale;
    }

    public double getRandomY() {
        return this.getY(this.random.nextDouble());
    }

    public double getEyeY() {
        return this.position.y + this.eyeHeight;
    }

    public final int getBlockZ() {
        return this.blockPosition.getZ();
    }

    public final double getZ() {
        return this.position.z;
    }

    public double getZ(double scale) {
        return this.position.z + this.getBbWidth() * scale;
    }

    public double getRandomZ(double scale) {
        return this.getZ((2.0 * this.random.nextDouble() - 1.0) * scale);
    }

    // Paper start - Block invalid positions and bounding box
    public static boolean checkPosition(Entity entity, double newX, double newY, double newZ) {
        if (Double.isFinite(newX) && Double.isFinite(newY) && Double.isFinite(newZ)) {
            return true;
        }

        String entityInfo;
        try {
            entityInfo = entity.toString();
        } catch (Exception ex) {
            entityInfo = "[Entity info unavailable] ";
        }
        LOGGER.error("New entity position is invalid! Tried to set invalid position ({},{},{}) for entity {} located at {}, entity info: {}", newX, newY, newZ, entity.getClass().getName(), entity.position, entityInfo, new Throwable());
        return false;
    }
    public final void setPosRaw(double x, double y, double z) {
        this.setPosRaw(x, y, z, false);
    }
    public final void setPosRaw(double x, double y, double z, boolean forceBoundingBoxUpdate) {
        // Paper start - rewrite chunk system
        if (this.updatingSectionStatus) {
            LOGGER.error(
                "Refusing to update position for entity " + this + " to position " + new Vec3(x, y, z)
                    + " since it is processing a section status update", new Throwable()
            );
            return;
        }
        // Paper end - rewrite chunk system
        if (!checkPosition(this, x, y, z)) {
            return;
        }
        // Paper end - Block invalid positions and bounding box
        // Paper start - Fix MC-4
        if (this instanceof ItemEntity) {
            if (io.papermc.paper.configuration.GlobalConfiguration.get().misc.fixEntityPositionDesync) {
                // encode/decode from ClientboundMoveEntityPacket
                x = Mth.lfloor(x * 4096.0) * (1 / 4096.0);
                y = Mth.lfloor(y * 4096.0) * (1 / 4096.0);
                z = Mth.lfloor(z * 4096.0) * (1 / 4096.0);
            }
        }
        // Paper end - Fix MC-4
        if (this.position.x != x || this.position.y != y || this.position.z != z) {
            synchronized (this.posLock) { // Paper - detailed watchdog information
            this.position = new Vec3(x, y, z);
            } // Paper - detailed watchdog information
            int floor = Mth.floor(x);
            int floor1 = Mth.floor(y);
            int floor2 = Mth.floor(z);
            if (floor != this.blockPosition.getX() || floor1 != this.blockPosition.getY() || floor2 != this.blockPosition.getZ()) {
                this.blockPosition = new BlockPos(floor, floor1, floor2);
                this.inBlockState = null;
                if (SectionPos.blockToSectionCoord(floor) != this.chunkPosition.x || SectionPos.blockToSectionCoord(floor2) != this.chunkPosition.z) {
                    this.chunkPosition = new ChunkPos(this.blockPosition);
                }
            }

            this.levelCallback.onMove();
        }
        // Paper start - Block invalid positions and bounding box; don't allow desync of pos and AABB
        // hanging has its own special logic
        if (!(this instanceof net.minecraft.world.entity.decoration.HangingEntity) && (forceBoundingBoxUpdate || this.position.x != x || this.position.y != y || this.position.z != z)) {
            this.setBoundingBox(this.makeBoundingBox());
        }
        // Paper end - Block invalid positions and bounding box
    }

    public void checkDespawn() {
    }

    public Vec3 getRopeHoldPosition(float partialTicks) {
        return this.getPosition(partialTicks).add(0.0, this.eyeHeight * 0.7, 0.0);
    }

    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        int id = packet.getId();
        double x = packet.getX();
        double y = packet.getY();
        double z = packet.getZ();
        this.syncPacketPositionCodec(x, y, z);
        this.moveTo(x, y, z, packet.getYRot(), packet.getXRot());
        this.setId(id);
        this.setUUID(packet.getUUID());
    }

    @Nullable
    public ItemStack getPickResult() {
        return null;
    }

    public void setIsInPowderSnow(boolean isInPowderSnow) {
        this.isInPowderSnow = isInPowderSnow;
    }

    public boolean canFreeze() {
        return !this.getType().is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES);
    }

    public boolean isFreezing() {
        return (this.isInPowderSnow || this.wasInPowderSnow) && this.canFreeze();
    }

    public float getYRot() {
        return this.yRot;
    }

    public float getVisualRotationYInDegrees() {
        return this.getYRot();
    }

    public void setYRot(float yRot) {
        if (!Float.isFinite(yRot)) {
            Util.logAndPauseIfInIde("Invalid entity rotation: " + yRot + ", discarding.");
        } else {
            this.yRot = yRot;
        }
    }

    public float getXRot() {
        return this.xRot;
    }

    public void setXRot(float xRot) {
        if (!Float.isFinite(xRot)) {
            Util.logAndPauseIfInIde("Invalid entity rotation: " + xRot + ", discarding.");
        } else {
            this.xRot = Math.clamp(xRot % 360.0F, -90.0F, 90.0F);
        }
    }

    public boolean canSprint() {
        return false;
    }

    public float maxUpStep() {
        return maxUpStep; // Purpur - Add option to set armorstand step height
    }

    public void onExplosionHit(@Nullable Entity entity) {
    }

    public final boolean isRemoved() {
        return this.removalReason != null;
    }

    @Nullable
    public Entity.RemovalReason getRemovalReason() {
        return this.removalReason;
    }

    @Override
    public final void setRemoved(Entity.RemovalReason removalReason) {
        // CraftBukkit start - add Bukkit remove cause
        this.setRemoved(removalReason, null);
    }

    @Override
    public final void setRemoved(Entity.RemovalReason removalReason, org.bukkit.event.entity.EntityRemoveEvent.Cause cause) {
        // Paper start - rewrite chunk system
        if (!((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel)this.level).moonrise$getEntityLookup().canRemoveEntity((Entity)(Object)this)) {
            LOGGER.warn("Entity " + this + " is currently prevented from being removed from the world since it is processing section status updates", new Throwable());
            return;
        }
        // Paper end - rewrite chunk system
        org.bukkit.craftbukkit.event.CraftEventFactory.callEntityRemoveEvent(this, cause);
        // CraftBukkit end
        final boolean alreadyRemoved = this.removalReason != null; // Paper - Folia schedulers
        if (this.removalReason == null) {
            this.removalReason = removalReason;
        }

        if (this.removalReason.shouldDestroy()) {
            this.stopRiding();
        }

        if (this.removalReason != Entity.RemovalReason.UNLOADED_TO_CHUNK) { this.getPassengers().forEach(Entity::stopRiding); } // Paper - rewrite chunk system
        this.levelCallback.onRemove(removalReason);
        this.onRemoval(removalReason);
        // Paper start - Folia schedulers
        if (!(this instanceof ServerPlayer) && removalReason != RemovalReason.CHANGED_DIMENSION && !alreadyRemoved) {
            // Players need to be special cased, because they are regularly removed from the world
            this.retireScheduler();
        }
        // Paper end - Folia schedulers
    }

    public void unsetRemoved() {
        this.removalReason = null;
    }

    // Paper start - Folia schedulers
    /**
     * Invoked only when the entity is truly removed from the server, never to be added to any world.
     */
    public final void retireScheduler() {
        // we need to force create the bukkit entity so that the scheduler can be retired...
        this.getBukkitEntity().taskScheduler.retire();
    }
    // Paper end - Folia schedulers

    @Override
    public void setLevelCallback(EntityInLevelCallback levelCallback) {
        this.levelCallback = levelCallback;
    }

    @Override
    public boolean shouldBeSaved() {
        return (this.removalReason == null || this.removalReason.shouldSave())
            && !this.isPassenger()
            && (!this.isVehicle() || !((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity)this).moonrise$hasAnyPlayerPassengers()); // Paper - rewrite chunk system
    }

    @Override
    public boolean isAlwaysTicking() {
        return false;
    }

    public boolean mayInteract(ServerLevel level, BlockPos pos) {
        return true;
    }

    public Level level() {
        return this.level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public DamageSources damageSources() {
        return this.level().damageSources();
    }

    public RegistryAccess registryAccess() {
        return this.level().registryAccess();
    }

    protected void lerpPositionAndRotationStep(int steps, double targetX, double targetY, double targetZ, double targetYRot, double targetXRot) {
        double d = 1.0 / steps;
        double d1 = Mth.lerp(d, this.getX(), targetX);
        double d2 = Mth.lerp(d, this.getY(), targetY);
        double d3 = Mth.lerp(d, this.getZ(), targetZ);
        float f = (float)Mth.rotLerp(d, (double)this.getYRot(), targetYRot);
        float f1 = (float)Mth.lerp(d, (double)this.getXRot(), targetXRot);
        this.setPos(d1, d2, d3);
        this.setRot(f, f1);
    }

    public RandomSource getRandom() {
        return this.random;
    }

    public Vec3 getKnownMovement() {
        return this.getControllingPassenger() instanceof Player player && this.isAlive() ? player.getKnownMovement() : this.getDeltaMovement();
    }

    @Nullable
    public ItemStack getWeaponItem() {
        return null;
    }

    public Optional<ResourceKey<LootTable>> getLootTable() {
        return this.type.getDefaultLootTable();
    }

    @FunctionalInterface
    public interface MoveFunction {
        void accept(Entity entity, double x, double d, double y);
    }

    record Movement(Vec3 from, Vec3 to) {
    }

    public static enum MovementEmission {
        NONE(false, false),
        SOUNDS(true, false),
        EVENTS(false, true),
        ALL(true, true);

        final boolean sounds;
        final boolean events;

        private MovementEmission(final boolean sounds, final boolean events) {
            this.sounds = sounds;
            this.events = events;
        }

        public boolean emitsAnything() {
            return this.events || this.sounds;
        }

        public boolean emitsEvents() {
            return this.events;
        }

        public boolean emitsSounds() {
            return this.sounds;
        }
    }

    public static enum RemovalReason {
        KILLED(true, false),
        DISCARDED(true, false),
        UNLOADED_TO_CHUNK(false, true),
        UNLOADED_WITH_PLAYER(false, false),
        CHANGED_DIMENSION(false, false);

        private final boolean destroy;
        private final boolean save;

        private RemovalReason(final boolean destroy, final boolean save) {
            this.destroy = destroy;
            this.save = save;
        }

        public boolean shouldDestroy() {
            return this.destroy;
        }

        public boolean shouldSave() {
            return this.save;
        }
    }

    // Paper start - Expose entity id counter
    public static int nextEntityId() {
        return ENTITY_COUNTER.incrementAndGet();
    }

    public boolean isTicking() {
        return ((ServerLevel) this.level).isPositionEntityTicking(this.blockPosition());
    }
    // Paper end - Expose entity id counter
    // Purpur start - Ridables
    @Nullable
    private Player rider = null;

    @Nullable
    public Player getRider() {
        return rider;
    }

    public boolean isRidable() {
        return false;
    }

    public boolean isControllable() {
        return true;
    }

    public void onMount(Player rider) {
        if (this instanceof Mob) {
            ((Mob) this).setTarget(null, null, false);
            ((Mob) this).getNavigation().stop();
        }
        rider.setJumping(false); // fixes jump on mount
    }

    public void onDismount(Player player) {
    }

    public boolean onSpacebar() {
        return false;
    }

    public boolean onClick(InteractionHand hand) {
        return false;
    }

    public boolean processClick(InteractionHand hand) {
        return false;
    }
    // Purpur end - Ridables
}
