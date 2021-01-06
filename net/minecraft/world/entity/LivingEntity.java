package net.minecraft.world.entity;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;

// CraftBukkit start
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedList;
import java.util.UUID;
import net.minecraft.world.item.component.Consumable;
import org.bukkit.Location;
import org.bukkit.craftbukkit.attribute.CraftAttributeMap;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.entity.ArrowBodyCountChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.entity.EntityKnockbackEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
// CraftBukkit end

public abstract class LivingEntity extends Entity implements Attackable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_ACTIVE_EFFECTS = "active_effects";
    private static final ResourceLocation SPEED_MODIFIER_POWDER_SNOW_ID = ResourceLocation.withDefaultNamespace("powder_snow");
    private static final ResourceLocation SPRINTING_MODIFIER_ID = ResourceLocation.withDefaultNamespace("sprinting");
    private static final AttributeModifier SPEED_MODIFIER_SPRINTING = new AttributeModifier(
        SPRINTING_MODIFIER_ID, 0.3F, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    );
    public static final int HAND_SLOTS = 2;
    public static final int ARMOR_SLOTS = 4;
    public static final int EQUIPMENT_SLOT_OFFSET = 98;
    public static final int ARMOR_SLOT_OFFSET = 100;
    public static final int BODY_ARMOR_OFFSET = 105;
    public static final int SWING_DURATION = 6;
    public static final int PLAYER_HURT_EXPERIENCE_TIME = 100;
    private static final int DAMAGE_SOURCE_TIMEOUT = 40;
    public static final double MIN_MOVEMENT_DISTANCE = 0.003;
    public static final double DEFAULT_BASE_GRAVITY = 0.08;
    public static final int DEATH_DURATION = 20;
    private static final int TICKS_PER_ELYTRA_FREE_FALL_EVENT = 10;
    private static final int FREE_FALL_EVENTS_PER_ELYTRA_BREAK = 2;
    public static final float BASE_JUMP_POWER = 0.42F;
    private static final double MAX_LINE_OF_SIGHT_TEST_RANGE = 128.0;
    protected static final int LIVING_ENTITY_FLAG_IS_USING = 1;
    protected static final int LIVING_ENTITY_FLAG_OFF_HAND = 2;
    public static final int LIVING_ENTITY_FLAG_SPIN_ATTACK = 4;
    protected static final EntityDataAccessor<Byte> DATA_LIVING_ENTITY_FLAGS = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.BYTE);
    public static final EntityDataAccessor<Float> DATA_HEALTH_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<List<ParticleOptions>> DATA_EFFECT_PARTICLES = SynchedEntityData.defineId(
        LivingEntity.class, EntityDataSerializers.PARTICLES
    );
    private static final EntityDataAccessor<Boolean> DATA_EFFECT_AMBIENCE_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> DATA_ARROW_COUNT_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STINGER_COUNT_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> SLEEPING_POS_ID = SynchedEntityData.defineId(
        LivingEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS
    );
    private static final int PARTICLE_FREQUENCY_WHEN_INVISIBLE = 15;
    protected static final EntityDimensions SLEEPING_DIMENSIONS = EntityDimensions.fixed(0.2F, 0.2F).withEyeHeight(0.2F);
    public static final float EXTRA_RENDER_CULLING_SIZE_WITH_BIG_HAT = 0.5F;
    public static final float DEFAULT_BABY_SCALE = 0.5F;
    public static final String ATTRIBUTES_FIELD = "attributes";
    public static final Predicate<LivingEntity> PLAYER_NOT_WEARING_DISGUISE_ITEM = entity -> {
        if (entity instanceof Player player) {
            ItemStack itemBySlot = player.getItemBySlot(EquipmentSlot.HEAD);
            return !itemBySlot.is(ItemTags.GAZE_DISGUISE_EQUIPMENT);
        } else {
            return true;
        }
    };
    private final AttributeMap attributes;
    public CombatTracker combatTracker = new CombatTracker(this);
    public final Map<Holder<MobEffect>, MobEffectInstance> activeEffects = Maps.newHashMap();
    private final NonNullList<ItemStack> lastHandItemStacks = NonNullList.withSize(2, ItemStack.EMPTY);
    private final NonNullList<ItemStack> lastArmorItemStacks = NonNullList.withSize(4, ItemStack.EMPTY);
    private ItemStack lastBodyItemStack = ItemStack.EMPTY;
    public boolean swinging;
    private boolean discardFriction = false;
    public InteractionHand swingingArm;
    public int swingTime;
    public int removeArrowTime;
    public int removeStingerTime;
    public int hurtTime;
    public int hurtDuration;
    public int deathTime;
    public float oAttackAnim;
    public float attackAnim;
    protected int attackStrengthTicker;
    public final WalkAnimationState walkAnimation = new WalkAnimationState();
    public int invulnerableDuration = 20;
    public final float timeOffs;
    public final float rotA;
    public float yBodyRot;
    public float yBodyRotO;
    public float yHeadRot;
    public float yHeadRotO;
    public final ElytraAnimationState elytraAnimationState = new ElytraAnimationState(this);
    @Nullable
    public Player lastHurtByPlayer;
    public int lastHurtByPlayerTime;
    protected boolean dead;
    protected int noActionTime;
    protected float oRun;
    protected float run;
    protected float animStep;
    protected float animStepO;
    protected float rotOffs;
    public float lastHurt;
    public boolean jumping;
    public float xxa; public float getStrafeMot() { return xxa; } public void setStrafeMot(float strafe) { xxa = strafe; } // Purpur - OBFHELPER
    public float yya; public float getVerticalMot() { return yya; } public void setVerticalMot(float vertical) { yya = vertical; } // Purpur - OBFHELPER
    public float zza; public float getForwardMot() { return zza; } public void setForwardMot(float forward) { zza = forward; } // Purpur - OBFHELPER
    protected int lerpSteps;
    protected double lerpX;
    protected double lerpY;
    protected double lerpZ;
    protected double lerpYRot;
    protected double lerpXRot;
    protected double lerpYHeadRot;
    protected int lerpHeadSteps;
    public boolean effectsDirty = true;
    @Nullable
    public LivingEntity lastHurtByMob;
    public int lastHurtByMobTimestamp;
    @Nullable
    private LivingEntity lastHurtMob;
    private int lastHurtMobTimestamp;
    private float speed;
    private int noJumpDelay;
    private float absorptionAmount;
    protected ItemStack useItem = ItemStack.EMPTY;
    public int useItemRemaining;
    protected int fallFlyTicks;
    private BlockPos lastPos;
    private Optional<BlockPos> lastClimbablePos = Optional.empty();
    @Nullable
    private DamageSource lastDamageSource;
    private long lastDamageStamp;
    protected int autoSpinAttackTicks;
    protected float autoSpinAttackDmg;
    @Nullable
    protected ItemStack autoSpinAttackItemStack;
    private float swimAmount;
    private float swimAmountO;
    protected Brain<?> brain;
    protected boolean skipDropExperience;
    private final EnumMap<EquipmentSlot, Reference2ObjectMap<Enchantment, Set<EnchantmentLocationBasedEffect>>> activeLocationDependentEnchantments = new EnumMap<>(
        EquipmentSlot.class
    );
    protected float appliedScale = 1.0F;
    // CraftBukkit start
    public int expToDrop;
    public ArrayList<DefaultDrop> drops = new ArrayList<>(); // Paper - Restore vanilla drops behavior
    public final org.bukkit.craftbukkit.attribute.CraftAttributeMap craftAttributes;
    public boolean collides = true;
    public Set<UUID> collidableExemptions = new HashSet<>();
    public boolean bukkitPickUpLoot;
    public org.bukkit.craftbukkit.entity.CraftLivingEntity getBukkitLivingEntity() { return (org.bukkit.craftbukkit.entity.CraftLivingEntity) super.getBukkitEntity(); } // Paper
    public boolean silentDeath = false; // Paper - mark entity as dying silently for cancellable death event
    public net.kyori.adventure.util.TriState frictionState = net.kyori.adventure.util.TriState.NOT_SET; // Paper - Friction API

    @Override
    public float getBukkitYaw() {
        return this.getYHeadRot();
    }
    // CraftBukkit end

    protected LivingEntity(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
        this.attributes = new AttributeMap(DefaultAttributes.getSupplier(entityType), this); // Purpur - Ridables
        this.initAttributes(); // Purpur - Configurable entity base attributes
        this.craftAttributes = new CraftAttributeMap(this.attributes); // CraftBukkit
        // CraftBukkit - this.setHealth(this.getMaxHealth()) inlined and simplified to skip the instanceof check for Player, as getBukkitEntity() is not initialized in constructor
        this.entityData.set(LivingEntity.DATA_HEALTH_ID, this.getMaxHealth());
        this.blocksBuilding = true;
        this.rotA = (float)((Math.random() + 1.0) * 0.01F);
        this.reapplyPosition();
        this.timeOffs = (float)Math.random() * 12398.0F;
        this.setYRot((float)(Math.random() * (float) (Math.PI * 2)));
        this.yHeadRot = this.getYRot();
        NbtOps nbtOps = NbtOps.INSTANCE;
        this.brain = this.makeBrain(new Dynamic<>(nbtOps, nbtOps.createMap(ImmutableMap.of(nbtOps.createString("memories"), nbtOps.emptyMap()))));
    }

    protected void initAttributes() {}// Purpur - Configurable entity base attributes

    public Brain<?> getBrain() {
        return this.brain;
    }

    protected Brain.Provider<?> brainProvider() {
        return Brain.provider(ImmutableList.of(), ImmutableList.of());
    }

    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return this.brainProvider().makeBrain(dynamic);
    }

    @Override
    public void kill(ServerLevel level) {
        this.hurtServer(level, this.damageSources().genericKill(), Float.MAX_VALUE);
    }

    public boolean canAttackType(EntityType<?> entityType) {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LIVING_ENTITY_FLAGS, (byte)0);
        builder.define(DATA_EFFECT_PARTICLES, List.of());
        builder.define(DATA_EFFECT_AMBIENCE_ID, false);
        builder.define(DATA_ARROW_COUNT_ID, 0);
        builder.define(DATA_STINGER_COUNT_ID, 0);
        builder.define(DATA_HEALTH_ID, 1.0F);
        builder.define(SLEEPING_POS_ID, Optional.empty());
    }

    public static AttributeSupplier.Builder createLivingAttributes() {
        return AttributeSupplier.builder()
            .add(Attributes.MAX_HEALTH)
            .add(Attributes.KNOCKBACK_RESISTANCE)
            .add(Attributes.MOVEMENT_SPEED)
            .add(Attributes.ARMOR)
            .add(Attributes.ARMOR_TOUGHNESS)
            .add(Attributes.MAX_ABSORPTION)
            .add(Attributes.STEP_HEIGHT)
            .add(Attributes.SCALE)
            .add(Attributes.GRAVITY)
            .add(Attributes.SAFE_FALL_DISTANCE)
            .add(Attributes.FALL_DAMAGE_MULTIPLIER)
            .add(Attributes.JUMP_STRENGTH)
            .add(Attributes.OXYGEN_BONUS)
            .add(Attributes.BURNING_TIME)
            .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)
            .add(Attributes.WATER_MOVEMENT_EFFICIENCY)
            .add(Attributes.MOVEMENT_EFFICIENCY)
            .add(Attributes.ATTACK_KNOCKBACK);
    }
    public boolean shouldSendAttribute(Attribute attribute) { return true; } // Purpur - Ridables

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        if (!this.isInWater()) {
            this.updateInWaterStateAndDoWaterCurrentPushing();
        }

        if (this.level() instanceof ServerLevel serverLevel && onGround && this.fallDistance > 0.0F) {
            this.onChangedBlock(serverLevel, pos);
            double attributeValue = this.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
            if (this.fallDistance > attributeValue && !state.isAir()) {
                double x = this.getX();
                double y1 = this.getY();
                double z = this.getZ();
                BlockPos blockPos = this.blockPosition();
                if (pos.getX() != blockPos.getX() || pos.getZ() != blockPos.getZ()) {
                    double d = x - pos.getX() - 0.5;
                    double d1 = z - pos.getZ() - 0.5;
                    double max = Math.max(Math.abs(d), Math.abs(d1));
                    x = pos.getX() + 0.5 + d / max * 0.5;
                    z = pos.getZ() + 0.5 + d1 / max * 0.5;
                }

                float f = Mth.ceil(this.fallDistance - attributeValue);
                double min = Math.min((double)(0.2F + f / 15.0F), 2.5);
                int i = (int)(150.0 * min);
                // CraftBukkit start - visiblity api
                if (this instanceof ServerPlayer) {
                    serverLevel.sendParticlesSource((ServerPlayer) this, new BlockParticleOption(ParticleTypes.BLOCK, state), false, false, x, y1, z, i, 0.0, 0.0, 0.0, 0.15F);
                } else {
                    serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y1, z, i, 0.0, 0.0, 0.0, 0.15F);
                }
                // CraftBukkit end
            }
        }

        super.checkFallDamage(y, onGround, state, pos);
        if (onGround) {
            this.lastClimbablePos = Optional.empty();
        }
    }

    public final boolean canBreatheUnderwater() {
        return this.getType().is(EntityTypeTags.CAN_BREATHE_UNDER_WATER);
    }

    public float getSwimAmount(float partialTicks) {
        return Mth.lerp(partialTicks, this.swimAmountO, this.swimAmount);
    }

    public boolean hasLandedInLiquid() {
        return this.getDeltaMovement().y() < 1.0E-5F && this.isInLiquid();
    }

    @Override
    public void baseTick() {
        this.oAttackAnim = this.attackAnim;
        if (this.firstTick) {
            this.getSleepingPos().ifPresent(this::setPosToBed);
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            EnchantmentHelper.tickEffects(serverLevel, this);
        }

        super.baseTick();
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("livingEntityBaseTick");
        if (this.fireImmune() || this.level().isClientSide) {
            this.clearFire();
        }

        if (this.isAlive()) {
            boolean flag = this instanceof Player;
            if (this.level() instanceof ServerLevel serverLevel1) {
                if (this.isInWall()) {
                    this.hurtServer(serverLevel1, this.damageSources().inWall(), 1.0F);
                } else if (flag && !this.level().getWorldBorder().isWithinBounds(this.getBoundingBox())) {
                    double d = this.level().getWorldBorder().getDistanceToBorder(this) + this.level().getWorldBorder().getDamageSafeZone();
                    if (d < 0.0) {
                        double damagePerBlock = this.level().getWorldBorder().getDamagePerBlock();
                        if (damagePerBlock > 0.0) {
                            // Purpur start - Add option to teleport to spawn if outside world border
                            if (this.level().purpurConfig.teleportIfOutsideBorder && this instanceof ServerPlayer serverPlayer) {
                                serverPlayer.teleport(io.papermc.paper.util.MCUtil.toLocation(this.level(), this.level().getSharedSpawnPos()));
                                return;
                            }
                            // Purpur end - Add option to teleport to spawn if outside world border
                            this.hurtServer(serverLevel1, this.damageSources().outOfBorder(), Math.max(1, Mth.floor(-d * damagePerBlock)));
                        }
                    }
                }
            }

            if (this.isEyeInFluid(FluidTags.WATER)
                && !this.level().getBlockState(BlockPos.containing(this.getX(), this.getEyeY(), this.getZ())).is(Blocks.BUBBLE_COLUMN)) {
                boolean flag1 = !this.canBreatheUnderwater()
                    && !MobEffectUtil.hasWaterBreathing(this)
                    && (!flag || !((Player)this).getAbilities().invulnerable);
                if (flag1) {
                    this.setAirSupply(this.decreaseAirSupply(this.getAirSupply()));
                    if (this.getAirSupply() == -this.level().purpurConfig.drowningDamageInterval) { // Purpur - Drowning Settings
                        this.setAirSupply(0);
                        Vec3 deltaMovement = this.getDeltaMovement();

                        for (int i = 0; i < 8; i++) {
                            double damagePerBlock = this.random.nextDouble() - this.random.nextDouble();
                            double d1 = this.random.nextDouble() - this.random.nextDouble();
                            double d2 = this.random.nextDouble() - this.random.nextDouble();
                            this.level()
                                .addParticle(
                                    ParticleTypes.BUBBLE,
                                    this.getX() + damagePerBlock,
                                    this.getY() + d1,
                                    this.getZ() + d2,
                                    deltaMovement.x,
                                    deltaMovement.y,
                                    deltaMovement.z
                                );
                        }

                        this.hurt(this.damageSources().drown(), (float) this.level().purpurConfig.damageFromDrowning); // Purpur - Drowning Settings
                    }
                } else if (this.getAirSupply() < this.getMaxAirSupply()) {
                    this.setAirSupply(this.increaseAirSupply(this.getAirSupply()));
                }

                if (!this.level().isClientSide && this.isPassenger() && this.getVehicle() != null && this.getVehicle().dismountsUnderwater()) {
                    this.stopRiding();
                }
            } else if (this.getAirSupply() < this.getMaxAirSupply()) {
                this.setAirSupply(this.increaseAirSupply(this.getAirSupply()));
            }

            if (this.level() instanceof ServerLevel serverLevel1x) {
                BlockPos blockPos = this.blockPosition();
                if (!Objects.equal(this.lastPos, blockPos)) {
                    this.lastPos = blockPos;
                    this.onChangedBlock(serverLevel1x, blockPos);
                }
            }
        }

        if (this.isAlive() && (this.isInWaterRainOrBubble() || this.isInPowderSnow)) {
            this.extinguishFire();
        }

        if (this.hurtTime > 0) {
            this.hurtTime--;
        }

        if (this.invulnerableTime > 0 && !(this instanceof ServerPlayer)) {
            this.invulnerableTime--;
        }

        if (this.isDeadOrDying() && this.level().shouldTickDeath(this)) {
            this.tickDeath();
        }

        if (this.lastHurtByPlayerTime > 0) {
            this.lastHurtByPlayerTime--;
        } else {
            this.lastHurtByPlayer = null;
        }

        if (this.lastHurtMob != null && !this.lastHurtMob.isAlive()) {
            this.lastHurtMob = null;
        }

        if (this.lastHurtByMob != null) {
            if (!this.lastHurtByMob.isAlive()) {
                this.setLastHurtByMob(null);
            } else if (this.tickCount - this.lastHurtByMobTimestamp > 100) {
                this.setLastHurtByMob(null);
            }
        }

        this.tickEffects();
        this.animStepO = this.animStep;
        this.yBodyRotO = this.yBodyRot;
        this.yHeadRotO = this.yHeadRot;
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        profilerFiller.pop();
    }

    @Override
    protected float getBlockSpeedFactor() {
        return Mth.lerp((float)this.getAttributeValue(Attributes.MOVEMENT_EFFICIENCY), super.getBlockSpeedFactor(), 1.0F);
    }

    protected void removeFrost() {
        AttributeInstance attribute = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) {
            if (attribute.getModifier(SPEED_MODIFIER_POWDER_SNOW_ID) != null) {
                attribute.removeModifier(SPEED_MODIFIER_POWDER_SNOW_ID);
            }
        }
    }

    protected void tryAddFrost() {
        if (!this.getBlockStateOnLegacy().isAir()) {
            int ticksFrozen = this.getTicksFrozen();
            if (ticksFrozen > 0) {
                AttributeInstance attribute = this.getAttribute(Attributes.MOVEMENT_SPEED);
                if (attribute == null) {
                    return;
                }

                float f = -0.05F * this.getPercentFrozen();
                attribute.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_POWDER_SNOW_ID, f, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    protected void onChangedBlock(ServerLevel level, BlockPos pos) {
        EnchantmentHelper.runLocationChangedEffects(level, this);
    }

    public boolean isBaby() {
        return false;
    }

    public float getAgeScale() {
        return this.isBaby() ? 0.5F : 1.0F;
    }

    public final float getScale() {
        AttributeMap attributes = this.getAttributes();
        return attributes == null ? 1.0F : this.sanitizeScale((float)attributes.getValue(Attributes.SCALE));
    }

    protected float sanitizeScale(float scale) {
        return scale;
    }

    protected boolean isAffectedByFluids() {
        return true;
    }

    protected void tickDeath() {
        this.deathTime++;
        if (this.deathTime >= 20 && !this.level().isClientSide() && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte)60);
            this.remove(Entity.RemovalReason.KILLED, EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
        }
    }

    public boolean shouldDropExperience() {
        return !this.isBaby();
    }

    protected boolean shouldDropLoot() {
        return !this.isBaby();
    }

    protected int decreaseAirSupply(int currentAir) {
        AttributeInstance attribute = this.getAttribute(Attributes.OXYGEN_BONUS);
        double value;
        if (attribute != null) {
            value = attribute.getValue();
        } else {
            value = 0.0;
        }

        return value > 0.0 && this.random.nextDouble() >= 1.0 / (value + 1.0) ? currentAir : currentAir - 1;
    }

    protected int increaseAirSupply(int currentAir) {
        return Math.min(currentAir + 4, this.getMaxAirSupply());
    }

    public final int getExperienceReward(ServerLevel level, @Nullable Entity killer) {
        return EnchantmentHelper.processMobExperience(level, killer, this, this.getBaseExperienceReward(level));
    }

    protected int getBaseExperienceReward(ServerLevel level) {
        return 0;
    }

    protected boolean isAlwaysExperienceDropper() {
        return false;
    }

    @Nullable
    public LivingEntity getLastHurtByMob() {
        return this.lastHurtByMob;
    }

    @Override
    public LivingEntity getLastAttacker() {
        return this.getLastHurtByMob();
    }

    public int getLastHurtByMobTimestamp() {
        return this.lastHurtByMobTimestamp;
    }

    public void setLastHurtByPlayer(@Nullable Player player) {
        this.lastHurtByPlayer = player;
        this.lastHurtByPlayerTime = this.tickCount;
    }

    public void setLastHurtByMob(@Nullable LivingEntity livingEntity) {
        this.lastHurtByMob = livingEntity;
        this.lastHurtByMobTimestamp = this.tickCount;
    }

    @Nullable
    public LivingEntity getLastHurtMob() {
        return this.lastHurtMob;
    }

    public int getLastHurtMobTimestamp() {
        return this.lastHurtMobTimestamp;
    }

    public void setLastHurtMob(Entity entity) {
        if (entity instanceof LivingEntity) {
            this.lastHurtMob = (LivingEntity)entity;
        } else {
            this.lastHurtMob = null;
        }

        this.lastHurtMobTimestamp = this.tickCount;
    }

    public int getNoActionTime() {
        return this.noActionTime;
    }

    public void setNoActionTime(int idleTime) {
        this.noActionTime = idleTime;
    }

    public boolean shouldDiscardFriction() {
        return !this.frictionState.toBooleanOrElse(!this.discardFriction); // Paper - Friction API
    }

    public void setDiscardFriction(boolean discardFriction) {
        this.discardFriction = discardFriction;
    }

    protected boolean doesEmitEquipEvent(EquipmentSlot slot) {
        return true;
    }

    public void onEquipItem(EquipmentSlot slot, ItemStack oldItem, ItemStack newItem) {
        // CraftBukkit start
        this.onEquipItem(slot, oldItem, newItem, false);
    }
    public void onEquipItem(EquipmentSlot slot, ItemStack oldItem, ItemStack newItem, boolean silent) {
        // CraftBukkit end
        if (!this.level().isClientSide() && !this.isSpectator()) {
            boolean flag = newItem.isEmpty() && oldItem.isEmpty();
            if (!flag && !ItemStack.isSameItemSameComponents(oldItem, newItem) && !this.firstTick) {
                Equippable equippable = newItem.get(DataComponents.EQUIPPABLE);
                if (!this.isSilent() && equippable != null && slot == equippable.slot() && !silent) { // CraftBukkit
                    this.level()
                        .playSeededSound(
                            null, this.getX(), this.getY(), this.getZ(), equippable.equipSound(), this.getSoundSource(), 1.0F, 1.0F, this.random.nextLong()
                        );
                }

                if (this.doesEmitEquipEvent(slot)) {
                    this.gameEvent(equippable != null ? GameEvent.EQUIP : GameEvent.UNEQUIP);
                }
            }
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        // CraftBukkit start - add Bukkit remove cause
        this.remove(reason, null);
    }

    @Override
    public void remove(Entity.RemovalReason reason, EntityRemoveEvent.Cause eventCause) {
        // CraftBukkit end
        if ((reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) && this.level() instanceof ServerLevel serverLevel) {
            this.triggerOnDeathMobEffects(serverLevel, reason);
        }

        super.remove(reason, eventCause); // CraftBukkit
        this.brain.clearMemories();
    }

    protected void triggerOnDeathMobEffects(ServerLevel level, Entity.RemovalReason removalReason) {
        for (MobEffectInstance mobEffectInstance : this.getActiveEffects()) {
            mobEffectInstance.onMobRemoved(level, this, removalReason);
        }

        this.removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.DEATH); // CraftBukkit
        this.activeEffects.clear();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        // Paper start - Friction API
        if (this.frictionState != net.kyori.adventure.util.TriState.NOT_SET) {
            compound.putString("Paper.FrictionState", this.frictionState.toString());
        }
        // Paper end - Friction API
        compound.putFloat("Health", this.getHealth());
        compound.putShort("HurtTime", (short)this.hurtTime);
        compound.putInt("HurtByTimestamp", this.lastHurtByMobTimestamp);
        compound.putShort("DeathTime", (short)this.deathTime);
        compound.putFloat("AbsorptionAmount", this.getAbsorptionAmount());
        compound.put("attributes", this.getAttributes().save());
        if (!this.activeEffects.isEmpty()) {
            ListTag listTag = new ListTag();

            for (MobEffectInstance mobEffectInstance : this.activeEffects.values()) {
                listTag.add(mobEffectInstance.save());
            }

            compound.put("active_effects", listTag);
        }

        compound.putBoolean("FallFlying", this.isFallFlying());
        this.getSleepingPos().ifPresent(pos -> {
            compound.putInt("SleepingX", pos.getX());
            compound.putInt("SleepingY", pos.getY());
            compound.putInt("SleepingZ", pos.getZ());
        });
        DataResult<Tag> dataResult = this.brain.serializeStart(NbtOps.INSTANCE);
        dataResult.resultOrPartial(LOGGER::error).ifPresent(brain -> compound.put("Brain", brain));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        // Paper start - Check for NaN
        float absorptionAmount = compound.getFloat("AbsorptionAmount");
        if (Float.isNaN(absorptionAmount)) {
            absorptionAmount = 0;
        }
        this.internalSetAbsorptionAmount(absorptionAmount);
        // Paper end - Check for NaN
        // Paper start - Friction API
        if (compound.contains("Paper.FrictionState")) {
            String frictionState = compound.getString("Paper.FrictionState");
            try {
                this.frictionState = net.kyori.adventure.util.TriState.valueOf(frictionState);
            } catch (Exception ignored) {
                LOGGER.error("Unknown friction state " + frictionState + " for " + this);
            }
        }
        // Paper end - Friction API
        if (compound.contains("attributes", 9) && this.level() != null && !this.level().isClientSide) {
            this.getAttributes().load(compound.getList("attributes", 10));
        }

        if (compound.contains("active_effects", 9)) {
            ListTag list = compound.getList("active_effects", 10);

            for (int i = 0; i < list.size(); i++) {
                CompoundTag compound1 = list.getCompound(i);
                MobEffectInstance mobEffectInstance = MobEffectInstance.load(compound1);
                if (mobEffectInstance != null) {
                    this.activeEffects.put(mobEffectInstance.getEffect(), mobEffectInstance);
                }
            }
        }

        // CraftBukkit start
        if (compound.contains("Bukkit.MaxHealth")) {
            Tag maxHealthTag = compound.get("Bukkit.MaxHealth");
            if (maxHealthTag.getId() == 5) {
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(((FloatTag) maxHealthTag).getAsDouble());
            } else if (maxHealthTag.getId() == 3) {
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(((IntTag) maxHealthTag).getAsDouble());
            }
        }
        // CraftBukkit end
        if (compound.contains("Health", 99)) {
            this.setHealth(compound.getFloat("Health"));
        }

        this.hurtTime = compound.getShort("HurtTime");
        this.deathTime = compound.getShort("DeathTime");
        this.lastHurtByMobTimestamp = compound.getInt("HurtByTimestamp");
        if (compound.contains("Team", 8)) {
            String string = compound.getString("Team");
            Scoreboard scoreboard = this.level().getScoreboard();
            PlayerTeam playerTeam = scoreboard.getPlayerTeam(string);
            if (!this.level().paperConfig().scoreboards.allowNonPlayerEntitiesOnScoreboards && !(this instanceof net.minecraft.world.entity.player.Player)) { playerTeam = null; } // Paper - Perf: Disable Scoreboards for non players by default
            boolean flag = playerTeam != null && scoreboard.addPlayerToTeam(this.getStringUUID(), playerTeam);
            if (!flag) {
                LOGGER.warn("Unable to add mob to team \"{}\" (that team probably doesn't exist)", string);
            }
        }

        if (compound.getBoolean("FallFlying")) {
            this.setSharedFlag(7, true);
        }

        if (compound.contains("SleepingX", 99) && compound.contains("SleepingY", 99) && compound.contains("SleepingZ", 99)) {
            BlockPos blockPos = new BlockPos(compound.getInt("SleepingX"), compound.getInt("SleepingY"), compound.getInt("SleepingZ"));
            if (this.position().distanceToSqr(blockPos.getX(), blockPos.getY(), blockPos.getZ()) < 16 * 16) { // Paper - The sleeping pos will always also set the actual pos, so a desync suggests something is wrong
            this.setSleepingPos(blockPos);
            this.entityData.set(DATA_POSE, Pose.SLEEPING);
            if (!this.firstTick) {
                this.setPosToBed(blockPos);
            }
            } // Paper - The sleeping pos will always also set the actual pos, so a desync suggests something is wrong
        }

        if (compound.contains("Brain", 10)) {
            this.brain = this.makeBrain(new Dynamic<>(NbtOps.INSTANCE, compound.get("Brain")));
        }
    }

    // CraftBukkit start
    private boolean isTickingEffects = false;
    private List<ProcessableEffect> effectsToProcess = Lists.newArrayList();

    private static class ProcessableEffect {

        private Holder<MobEffect> type;
        private MobEffectInstance effect;
        private final EntityPotionEffectEvent.Cause cause;

        private ProcessableEffect(MobEffectInstance effect, EntityPotionEffectEvent.Cause cause) {
            this.effect = effect;
            this.cause = cause;
        }

        private ProcessableEffect(Holder<MobEffect> type, EntityPotionEffectEvent.Cause cause) {
            this.type = type;
            this.cause = cause;
        }
    }
    // CraftBukkit end

    protected void tickEffects() {
        Iterator<Holder<MobEffect>> iterator = this.activeEffects.keySet().iterator();

        this.isTickingEffects = true; // CraftBukkit
        try {
            while (iterator.hasNext()) {
                Holder<MobEffect> holder = iterator.next();
                MobEffectInstance mobEffectInstance = this.activeEffects.get(holder);
                if (!mobEffectInstance.tick(this, () -> this.onEffectUpdated(mobEffectInstance, true, null))) {
                    if (!this.level().isClientSide) {
                        // CraftBukkit start
                        EntityPotionEffectEvent event = CraftEventFactory.callEntityPotionEffectChangeEvent(this, mobEffectInstance, null, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.EXPIRATION);
                        if (event.isCancelled()) {
                            continue;
                        }
                        // CraftBukkit end
                        iterator.remove();
                        this.onEffectsRemoved(List.of(mobEffectInstance));
                    }
                } else if (mobEffectInstance.getDuration() % 600 == 0) {
                    this.onEffectUpdated(mobEffectInstance, false, null);
                }
            }
        } catch (ConcurrentModificationException var6) {
        }

        // CraftBukkit start
        this.isTickingEffects = false;
        for (ProcessableEffect effect : this.effectsToProcess) {
            if (effect.effect != null) {
                this.addEffect(effect.effect, effect.cause);
            } else {
                this.removeEffect(effect.type, effect.cause);
            }
        }
        this.effectsToProcess.clear();
        // CraftBukkit end
        if (this.effectsDirty) {
            if (!this.level().isClientSide) {
                this.updateInvisibilityStatus();
                this.updateGlowingStatus();
            }

            this.effectsDirty = false;
        }

        List<ParticleOptions> list = this.entityData.get(DATA_EFFECT_PARTICLES);
        if (!list.isEmpty()) {
            boolean flag = this.entityData.get(DATA_EFFECT_AMBIENCE_ID);
            int i = this.isInvisible() ? 15 : 4;
            int i1 = flag ? 5 : 1;
            if (this.random.nextInt(i * i1) == 0) {
                this.level().addParticle(Util.getRandom(list, this.random), this.getRandomX(0.5), this.getRandomY(), this.getRandomZ(0.5), 1.0, 1.0, 1.0);
            }
        }
    }

    protected void updateInvisibilityStatus() {
        if (this.activeEffects.isEmpty()) {
            this.removeEffectParticles();
            this.setInvisible(false);
        } else {
            this.setInvisible(this.hasEffect(MobEffects.INVISIBILITY));
            this.updateSynchronizedMobEffectParticles();
        }
    }

    private void updateSynchronizedMobEffectParticles() {
        List<ParticleOptions> list = this.activeEffects
            .values()
            .stream()
            .filter(MobEffectInstance::isVisible)
            .map(MobEffectInstance::getParticleOptions)
            .toList();
        this.entityData.set(DATA_EFFECT_PARTICLES, list);
        this.entityData.set(DATA_EFFECT_AMBIENCE_ID, areAllEffectsAmbient(this.activeEffects.values()));
    }

    private void updateGlowingStatus() {
        boolean isCurrentlyGlowing = this.isCurrentlyGlowing();
        if (this.getSharedFlag(6) != isCurrentlyGlowing) {
            this.setSharedFlag(6, isCurrentlyGlowing);
        }
    }

    public double getVisibilityPercent(@Nullable Entity lookingEntity) {
        double d = 1.0;
        if (this.isDiscrete()) {
            d *= 0.8;
        }

        if (this.isInvisible()) {
            float armorCoverPercentage = this.getArmorCoverPercentage();
            if (armorCoverPercentage < 0.1F) {
                armorCoverPercentage = 0.1F;
            }

            d *= 0.7 * armorCoverPercentage;
        }

        if (lookingEntity != null) {
            ItemStack itemBySlot = this.getItemBySlot(EquipmentSlot.HEAD);
            EntityType<?> type = lookingEntity.getType();
            // Purpur start - Mob head visibility percent
            if (type == EntityType.SKELETON && itemBySlot.is(Items.SKELETON_SKULL)) {
                d *= lookingEntity.level().purpurConfig.skeletonHeadVisibilityPercent;
            }
            else if (type == EntityType.ZOMBIE && itemBySlot.is(Items.ZOMBIE_HEAD)) {
                d *= lookingEntity.level().purpurConfig.zombieHeadVisibilityPercent;
            }
            else if ((type == EntityType.PIGLIN || type == EntityType.PIGLIN_BRUTE) && itemBySlot.is(Items.PIGLIN_HEAD)) {
                d *= lookingEntity.level().purpurConfig.piglinHeadVisibilityPercent;
            }
            else if (type == EntityType.CREEPER && itemBySlot.is(Items.CREEPER_HEAD)) {
                d *= lookingEntity.level().purpurConfig.creeperHeadVisibilityPercent;
            }
            // Purpur end - Mob head visibility percent
        }

        // Purpur start - Configurable mob blindness
        if (lookingEntity instanceof LivingEntity entityliving) {
            if (entityliving.hasEffect(MobEffects.BLINDNESS)) {
                int amplifier = entityliving.getEffect(MobEffects.BLINDNESS).getAmplifier();
                for (int i = 0; i < amplifier; i++) {
                    d *= this.level().purpurConfig.mobsBlindnessMultiplier;
                }
            }
        }
        // Purpur end - Configurable mob blindness

        return d;
    }

    public boolean canAttack(LivingEntity target) {
        return (!(target instanceof Player) || this.level().getDifficulty() != Difficulty.PEACEFUL) && target.canBeSeenAsEnemy();
    }

    public boolean canBeSeenAsEnemy() {
        return !this.isInvulnerable() && this.canBeSeenByAnyone();
    }

    public boolean canBeSeenByAnyone() {
        return !this.isSpectator() && this.isAlive();
    }

    public static boolean areAllEffectsAmbient(Collection<MobEffectInstance> potionEffects) {
        for (MobEffectInstance mobEffectInstance : potionEffects) {
            if (mobEffectInstance.isVisible() && !mobEffectInstance.isAmbient()) {
                return false;
            }
        }

        return true;
    }

    protected void removeEffectParticles() {
        this.entityData.set(DATA_EFFECT_PARTICLES, List.of());
    }

    public boolean removeAllEffects() {
        // CraftBukkit start
        return this.removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    }
    public boolean removeAllEffects(EntityPotionEffectEvent.Cause cause) {
        // CraftBukkit end
        if (this.level().isClientSide) {
            return false;
        } else if (this.activeEffects.isEmpty()) {
            return false;
        } else {
            // CraftBukkit start
            List<MobEffectInstance> toRemove = new LinkedList<>();
            Iterator<MobEffectInstance> iterator = this.activeEffects.values().iterator();
            while (iterator.hasNext()) {
                MobEffectInstance effect = iterator.next();
                if (cause == EntityPotionEffectEvent.Cause.MILK && !this.level().purpurConfig.milkClearsBeneficialEffects && effect.getEffect().value().isBeneficial()) continue; // Purpur - Milk Keeps Beneficial Effects
                EntityPotionEffectEvent event = CraftEventFactory.callEntityPotionEffectChangeEvent(this, effect, null, cause, EntityPotionEffectEvent.Action.CLEARED);
                if (event.isCancelled()) {
                    continue;
                }

                iterator.remove();
                toRemove.add(effect);
            }

            this.onEffectsRemoved(toRemove);
            return !toRemove.isEmpty();
            // CraftBukkit end
        }
    }

    public Collection<MobEffectInstance> getActiveEffects() {
        return this.activeEffects.values();
    }

    public Map<Holder<MobEffect>, MobEffectInstance> getActiveEffectsMap() {
        return this.activeEffects;
    }

    public boolean hasEffect(Holder<MobEffect> effect) {
        return this.activeEffects.containsKey(effect);
    }

    @Nullable
    public MobEffectInstance getEffect(Holder<MobEffect> effect) {
        return this.activeEffects.get(effect);
    }

    public final boolean addEffect(MobEffectInstance effectInstance) {
        return this.addEffect(effectInstance, (Entity) null); // CraftBukkit
    }

    // CraftBukkit start
    public boolean addEffect(MobEffectInstance effectInstance, EntityPotionEffectEvent.Cause cause) {
        return this.addEffect(effectInstance, (Entity) null, cause);
    }

    public boolean addEffect(MobEffectInstance effectInstance, @Nullable Entity entity) {
        return this.addEffect(effectInstance, entity, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    public boolean addEffect(MobEffectInstance effectInstance, @Nullable Entity entity, EntityPotionEffectEvent.Cause cause) {
        // Paper start - Don't fire sync event during generation
        return this.addEffect(effectInstance, entity, cause, true);
    }
    public boolean addEffect(MobEffectInstance effectInstance, @Nullable Entity entity, EntityPotionEffectEvent.Cause cause, boolean fireEvent) {
        // Paper end - Don't fire sync event during generation
        // org.spigotmc.AsyncCatcher.catchOp("effect add"); // Spigot // Paper - move to API
        if (this.isTickingEffects) {
            this.effectsToProcess.add(new ProcessableEffect(effectInstance, cause));
            return true;
        }
        // CraftBukkit end
        if (!this.canBeAffected(effectInstance)) {
            return false;
        } else {
            MobEffectInstance mobEffectInstance = this.activeEffects.get(effectInstance.getEffect());
            boolean flag = false;
            // CraftBukkit start
            boolean override = false;
            if (mobEffectInstance != null) {
                override = new MobEffectInstance(mobEffectInstance).update(effectInstance);
            }

            if (fireEvent) { // Paper - Don't fire sync event during generation
            EntityPotionEffectEvent event = CraftEventFactory.callEntityPotionEffectChangeEvent(this, mobEffectInstance, effectInstance, cause, override);
            override = event.isOverride(); // Paper - Don't fire sync event during generation
            if (event.isCancelled()) {
                return false;
            }
            } // Paper - Don't fire sync event during generation
            // CraftBukkit end
            if (mobEffectInstance == null) {
                this.activeEffects.put(effectInstance.getEffect(), effectInstance);
                this.onEffectAdded(effectInstance, entity);
                flag = true;
                effectInstance.onEffectAdded(this);
                // CraftBukkit start
            } else if (override) { // Paper - Don't fire sync event during generation
                mobEffectInstance.update(effectInstance);
                this.onEffectUpdated(mobEffectInstance, true, entity);
                flag = true;
            }

            effectInstance.onEffectStarted(this);
            return flag;
        }
    }

    public boolean canBeAffected(MobEffectInstance effectInstance) {
        if (this.getType().is(EntityTypeTags.IMMUNE_TO_INFESTED)) {
            return !effectInstance.is(MobEffects.INFESTED);
        } else {
            return this.getType().is(EntityTypeTags.IMMUNE_TO_OOZING)
                ? !effectInstance.is(MobEffects.OOZING)
                : !this.getType().is(EntityTypeTags.IGNORES_POISON_AND_REGEN)
                    || !effectInstance.is(MobEffects.REGENERATION) && !effectInstance.is(MobEffects.POISON);
        }
    }

    public void forceAddEffect(MobEffectInstance instance, @Nullable Entity entity) {
        if (this.canBeAffected(instance)) {
            MobEffectInstance mobEffectInstance = this.activeEffects.put(instance.getEffect(), instance);
            if (mobEffectInstance == null) {
                this.onEffectAdded(instance, entity);
            } else {
                instance.copyBlendState(mobEffectInstance);
                this.onEffectUpdated(instance, true, entity);
            }
        }
    }

    public boolean isInvertedHealAndHarm() {
        return this.getType().is(EntityTypeTags.INVERTED_HEALING_AND_HARM);
    }

    @Nullable
    public MobEffectInstance removeEffectNoUpdate(Holder<MobEffect> effect) {
        // CraftBukkit start
        return this.removeEffectNoUpdate(effect, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    @Nullable
    public MobEffectInstance removeEffectNoUpdate(Holder<MobEffect> effect, EntityPotionEffectEvent.Cause cause) {
        if (this.isTickingEffects) {
            this.effectsToProcess.add(new ProcessableEffect(effect, cause));
            return null;
        }

        MobEffectInstance effectInstance = this.activeEffects.get(effect);
        if (effectInstance == null) {
            return null;
        }

        EntityPotionEffectEvent event = CraftEventFactory.callEntityPotionEffectChangeEvent(this, effectInstance, null, cause);
        if (event.isCancelled()) {
            return null;
        }

        return this.activeEffects.remove(effect);
    }

    public boolean removeEffect(Holder<MobEffect> effect) {
        return this.removeEffect(effect, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    public boolean removeEffect(Holder<MobEffect> effect, EntityPotionEffectEvent.Cause cause) {
        MobEffectInstance mobEffectInstance = this.removeEffectNoUpdate(effect, cause);
        // CraftBukkit end
        if (mobEffectInstance != null) {
            this.onEffectsRemoved(List.of(mobEffectInstance));
            return true;
        } else {
            return false;
        }
    }

    protected void onEffectAdded(MobEffectInstance effectInstance, @Nullable Entity entity) {
        this.effectsDirty = true;
        if (!this.level().isClientSide) {
            effectInstance.getEffect().value().addAttributeModifiers(this.getAttributes(), effectInstance.getAmplifier());
            this.sendEffectToPassengers(effectInstance);
        }
    }

    public void sendEffectToPassengers(MobEffectInstance effectInstance) {
        for (Entity entity : this.getPassengers()) {
            if (entity instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), effectInstance, false));
            }
        }
    }

    protected void onEffectUpdated(MobEffectInstance effectInstance, boolean forced, @Nullable Entity entity) {
        this.effectsDirty = true;
        if (forced && !this.level().isClientSide) {
            MobEffect mobEffect = effectInstance.getEffect().value();
            mobEffect.removeAttributeModifiers(this.getAttributes());
            mobEffect.addAttributeModifiers(this.getAttributes(), effectInstance.getAmplifier());
            this.refreshDirtyAttributes();
        }

        if (!this.level().isClientSide) {
            this.sendEffectToPassengers(effectInstance);
        }
    }

    protected void onEffectsRemoved(Collection<MobEffectInstance> effects) {
        this.effectsDirty = true;
        if (!this.level().isClientSide) {
            for (MobEffectInstance mobEffectInstance : effects) {
                mobEffectInstance.getEffect().value().removeAttributeModifiers(this.getAttributes());

                for (Entity entity : this.getPassengers()) {
                    if (entity instanceof ServerPlayer serverPlayer) {
                        serverPlayer.connection.send(new ClientboundRemoveMobEffectPacket(this.getId(), mobEffectInstance.getEffect()));
                    }
                }
            }

            this.refreshDirtyAttributes();
        }
    }

    private void refreshDirtyAttributes() {
        Set<AttributeInstance> attributesToUpdate = this.getAttributes().getAttributesToUpdate();

        for (AttributeInstance attributeInstance : attributesToUpdate) {
            this.onAttributeUpdated(attributeInstance.getAttribute());
        }

        attributesToUpdate.clear();
    }

    protected void onAttributeUpdated(Holder<Attribute> attribute) {
        if (attribute.is(Attributes.MAX_HEALTH)) {
            float maxHealth = this.getMaxHealth();
            if (this.getHealth() > maxHealth) {
                this.setHealth(maxHealth);
            }
        } else if (attribute.is(Attributes.MAX_ABSORPTION)) {
            float maxHealth = this.getMaxAbsorption();
            if (this.getAbsorptionAmount() > maxHealth) {
                this.setAbsorptionAmount(maxHealth);
            }
        }
    }

    public void heal(float healAmount) {
        // CraftBukkit start - Delegate so we can handle providing a reason for health being regained
        this.heal(healAmount, EntityRegainHealthEvent.RegainReason.CUSTOM);
    }

    public void heal(float healAmount, EntityRegainHealthEvent.RegainReason regainReason) {
        // Paper start - Forward
        this.heal(healAmount, regainReason, false);
    }

    public void heal(float healAmount, EntityRegainHealthEvent.RegainReason regainReason, boolean isFastRegen) {
        // Paper end - Forward
        float health = this.getHealth();
        if (health > 0.0F) {
            EntityRegainHealthEvent event = new EntityRegainHealthEvent(this.getBukkitEntity(), healAmount, regainReason, isFastRegen); // Paper
            // Suppress during worldgen
            if (this.valid) {
                this.level().getCraftServer().getPluginManager().callEvent(event);
            }

            if (!event.isCancelled()) {
                this.setHealth((float) (this.getHealth() + event.getAmount()));
            }
            // CraftBukkit end
        }
    }

    public float getHealth() {
        // CraftBukkit start - Use unscaled health
        if (this instanceof ServerPlayer serverPlayer) {
            return (float) serverPlayer.getBukkitEntity().getHealth();
        }
        // CraftBukkit end
        return this.entityData.get(DATA_HEALTH_ID);
    }

    public void setHealth(float health) {
        // Paper start - Check for NaN
        if (Float.isNaN(health)) { health = getMaxHealth(); if (this.valid) {
            System.err.println("[NAN-HEALTH] " + getScoreboardName() + " had NaN health set");
        } } // Paper end - Check for NaN
        // CraftBukkit start - Handle scaled health
        if (this instanceof ServerPlayer) {
            org.bukkit.craftbukkit.entity.CraftPlayer player = ((ServerPlayer) this).getBukkitEntity();
            // Squeeze
            if (health < 0.0F) {
                player.setRealHealth(0.0D);
            } else if (health > player.getMaxHealth()) {
                player.setRealHealth(player.getMaxHealth());
            } else {
                player.setRealHealth(health);
            }

            player.updateScaledHealth(false);
            return;
        }
        // CraftBukkit end
        this.entityData.set(DATA_HEALTH_ID, Mth.clamp(health, 0.0F, this.getMaxHealth()));
    }

    public boolean isDeadOrDying() {
        return this.getHealth() <= 0.0F;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableTo(level, damageSource)) {
            return false;
        } else if (this.isRemoved() || this.dead || this.getHealth() <= 0.0F) { // CraftBukkit - Don't allow entities that got set to dead/killed elsewhere to get damaged and die
            return false;
        } else if (damageSource.is(DamageTypeTags.IS_FIRE) && this.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return false;
        } else {
            if (this.isSleeping()) {
                this.stopSleeping();
            }

            // Purpur start - One Punch Man!
            if (damageSource.getEntity() instanceof net.minecraft.world.entity.player.Player player && damageSource.getEntity().level().purpurConfig.creativeOnePunch && !damageSource.is(DamageTypeTags.IS_PROJECTILE)) {
                if (player.isCreative()) {
                    org.apache.commons.lang3.mutable.MutableDouble attackDamage = new org.apache.commons.lang3.mutable.MutableDouble();
                    player.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, (attributeHolder, attributeModifier) -> {
                        if (attributeModifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                            attackDamage.addAndGet(attributeModifier.amount());
                        }
                    });

                    if (attackDamage.doubleValue() == 0.0D) {
                        // One punch!
                        amount = 9999F;
                    }
                }
            }
            // Purpur end - One Punch Man!

            this.noActionTime = 0;
            if (amount < 0.0F) {
                amount = 0.0F;
            }

            float f = amount; final float originalAmount = amount; // Paper - revert to vanilla #hurt - OBFHELPER
            boolean flag = amount > 0.0F && this.isDamageSourceBlocked(damageSource); // Copied from below;
            float f1 = 0.0F;
            // CraftBukkit - Moved into handleEntityDamage(DamageSource, float) for get f and actuallyHurt(DamageSource, float, EntityDamageEvent) for handle damage
            if (false && amount > 0.0F && this.isDamageSourceBlocked(damageSource)) {
                this.hurtCurrentlyUsedShield(amount);
                f1 = amount;
                amount = 0.0F;
                if (!damageSource.is(DamageTypeTags.IS_PROJECTILE) && damageSource.getDirectEntity() instanceof LivingEntity livingEntity && livingEntity.distanceToSqr(this) <= (200.0D * 200.0D)) { // Paper - Check distance in entity interactions
                    this.blockUsingShield(livingEntity);
                }

                flag = true;
            }

            // CraftBukkit - Moved into handleEntityDamage(DamageSource, float) for get f
            if (false && damageSource.is(DamageTypeTags.IS_FREEZING) && this.getType().is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES)) {
                amount *= 5.0F;
            }

            // CraftBukkit - Moved into handleEntityDamage(DamageSource, float) for get f and actuallyHurt(DamageSource, float, EntityDamageEvent) for handle damage
            if (false && damageSource.is(DamageTypeTags.DAMAGES_HELMET) && !this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                this.hurtHelmet(damageSource, amount);
                amount *= 0.75F;
            }

            EntityDamageEvent event; // CraftBukkit // Paper - move this into the actual invuln check....
            this.walkAnimation.setSpeed(1.5F);
            if (Float.isNaN(amount) || Float.isInfinite(amount)) {
                amount = Float.MAX_VALUE;
            }

            boolean flag1 = true;
            if (this.invulnerableTime > (float) this.invulnerableDuration / 2.0F && !damageSource.is(DamageTypeTags.BYPASSES_COOLDOWN)) { // CraftBukkit - restore use of maxNoDamageTicks
                if (amount <= this.lastHurt) {
                    return false;
                }

                // Paper start - only call damage event when actuallyHurt will be called - move call logic down
                event = this.handleEntityDamage(damageSource, amount, this.lastHurt); // Paper - fix invulnerability reduction in EntityDamageEvent - pass lastDamage reduction
                amount = computeAmountFromEntityDamageEvent(event);
                // Paper end - only call damage event when actuallyHurt will be called - move call logic down

                // CraftBukkit start
                if (!this.actuallyHurt(level, damageSource, (float) event.getFinalDamage(), event)) { // Paper - fix invulnerability reduction in EntityDamageEvent - no longer subtract lastHurt, that is part of the damage event calc now
                    return false;
                }
                if (this instanceof ServerPlayer && event.getDamage() == 0 && originalAmount == 0) return false; // Paper - revert to vanilla damage - players are not affected by damage that is 0 - skip damage if the vanilla damage is 0 and was not modified by plugins in the event.
                // CraftBukkit end
                this.lastHurt = amount;
                flag1 = false;
            } else {
                // Paper start - only call damage event when actuallyHurt will be called - move call logic down
                event = this.handleEntityDamage(damageSource, amount, 0); // Paper - fix invulnerability reduction in EntityDamageEvent - pass lastDamage reduction (none in this branch)
                amount = computeAmountFromEntityDamageEvent(event);
                // Paper end - only call damage event when actuallyHurt will be called - move call logic down
                // CraftBukkit start
                if (!this.actuallyHurt(level, damageSource, (float) event.getFinalDamage(), event)) {
                    return false;
                }
                if (this instanceof ServerPlayer && event.getDamage() == 0 && originalAmount == 0) return false; // Paper - revert to vanilla damage - players are not affected by damage that is 0 - skip damage if the vanilla damage is 0 and was not modified by plugins in the event.
                this.lastHurt = amount;
                this.invulnerableTime = this.invulnerableDuration; // CraftBukkit - restore use of maxNoDamageTicks
                // this.actuallyHurt(level, damageSource, amount);
                // CraftBukkit end
                this.hurtDuration = 10;
                this.hurtTime = this.hurtDuration;
            }

            this.resolveMobResponsibleForDamage(damageSource);
            this.resolvePlayerResponsibleForDamage(damageSource);
            if (flag1) {
                if (flag) {
                    level.broadcastEntityEvent(this, (byte)29);
                } else {
                    level.broadcastDamageEvent(this, damageSource);
                }

                if (!damageSource.is(DamageTypeTags.NO_IMPACT) && !flag) { // CraftBukkit - Prevent marking hurt if the damage is blocked
                    this.markHurt();
                }

                if (!damageSource.is(DamageTypeTags.NO_KNOCKBACK)) {
                    double d = 0.0;
                    double d1 = 0.0;
                    if (damageSource.getDirectEntity() instanceof Projectile projectile) {
                        DoubleDoubleImmutablePair doubleDoubleImmutablePair = projectile.calculateHorizontalHurtKnockbackDirection(this, damageSource);
                        d = -doubleDoubleImmutablePair.leftDouble();
                        d1 = -doubleDoubleImmutablePair.rightDouble();
                    } else if (damageSource.getSourcePosition() != null) {
                        d = damageSource.getSourcePosition().x() - this.getX();
                        d1 = damageSource.getSourcePosition().z() - this.getZ();
                    }
                    // Paper start - Check distance in entity interactions; see for loop in knockback method
                    if (Math.abs(d) > 200) {
                        d = Math.random() - Math.random();
                    }
                    if (Math.abs(d1) > 200) {
                        d1 = Math.random() - Math.random();
                    }
                    // Paper end - Check distance in entity interactions

                    this.knockback(0.4F, d, d1, damageSource.getDirectEntity(), damageSource.getDirectEntity() == null ? io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.DAMAGE : io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.ENTITY_ATTACK); // CraftBukkit // Paper - knockback events
                    if (!flag) {
                        this.indicateDamage(d, d1);
                    }
                }
            }

            if (this.isDeadOrDying()) {
                if (!this.checkTotemDeathProtection(damageSource)) {
                    // Paper start - moved into CraftEventFactory event caller for cancellable death event
                    this.silentDeath = !flag1; // mark entity as dying silently
                    // Paper end

                    this.die(damageSource);
                    this.silentDeath = false; // Paper - cancellable death event - reset to default
                }
            } else if (flag1) {
                this.playHurtSound(damageSource);
            }

            boolean flag2 = !flag; // CraftBukkit - Ensure to return false if damage is blocked
            if (flag2) {
                this.lastDamageSource = damageSource;
                this.lastDamageStamp = this.level().getGameTime();

                for (MobEffectInstance mobEffectInstance : this.getActiveEffects()) {
                    mobEffectInstance.onMobHurt(level, this, damageSource, amount);
                }
            }

            if (this instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.ENTITY_HURT_PLAYER.trigger(serverPlayer, damageSource, f, amount, flag);
                if (f1 > 0.0F && f1 < 3.4028235E37F) {
                    serverPlayer.awardStat(Stats.DAMAGE_BLOCKED_BY_SHIELD, Math.round(f1 * 10.0F));
                }
            }

            if (damageSource.getEntity() instanceof ServerPlayer serverPlayerx) {
                CriteriaTriggers.PLAYER_HURT_ENTITY.trigger(serverPlayerx, this, damageSource, f, amount, flag);
            }

            return flag2;
        }
    }

    protected void resolveMobResponsibleForDamage(DamageSource damageSource) {
        if (damageSource.getEntity() instanceof LivingEntity livingEntity
            && !damageSource.is(DamageTypeTags.NO_ANGER)
            && (!damageSource.is(DamageTypes.WIND_CHARGE) || !this.getType().is(EntityTypeTags.NO_ANGER_FROM_WIND_CHARGE))) {
            this.setLastHurtByMob(livingEntity);
        }
    }

    @Nullable
    protected Player resolvePlayerResponsibleForDamage(DamageSource damageSource) {
        Entity entity = damageSource.getEntity();
        if (entity instanceof Player player) {
            this.lastHurtByPlayerTime = this.level().purpurConfig.mobLastHurtByPlayerTime; // Purpur - Config for mob last hurt by player time
            this.lastHurtByPlayer = player;
            return player;
        } else if (entity instanceof Wolf wolf && wolf.isTame()) {
            this.lastHurtByPlayerTime = this.level().purpurConfig.mobLastHurtByPlayerTime; // Purpur - Config for mob last hurt by player time
            if (wolf.getOwner() instanceof Player player1) {
                this.lastHurtByPlayer = player1;
            } else {
                this.lastHurtByPlayer = null;
            }

            return this.lastHurtByPlayer;
        } else {
            return null;
        }
    }

    // Paper start - only call damage event when actuallyHurt will be called - move out amount computation logic
    private float computeAmountFromEntityDamageEvent(final EntityDamageEvent event) {
        // Taken from hurt()'s craftbukkit diff.
        float amount = 0;
        amount += (float) event.getDamage(DamageModifier.BASE);
        amount += (float) event.getDamage(DamageModifier.BLOCKING);
        amount += (float) event.getDamage(DamageModifier.FREEZING);
        amount += (float) event.getDamage(DamageModifier.HARD_HAT);
        return amount;
    }
    // Paper end - only call damage event when actuallyHurt will be called - move out amount computation logic

    protected void blockUsingShield(LivingEntity attacker) {
        attacker.blockedByShield(this);
    }

    protected void blockedByShield(LivingEntity defender) {
        defender.knockback(0.5, defender.getX() - this.getX(), defender.getZ() - this.getZ(), this, io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.SHIELD_BLOCK); // CraftBukkit // Paper - fix attacker & knockback events
    }

    private boolean checkTotemDeathProtection(DamageSource damageSource) {
        if (damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        } else {
            ItemStack itemStack = null;
            DeathProtection deathProtection = null;

            // CraftBukkit start
            InteractionHand hand = null;
            ItemStack itemInHand = ItemStack.EMPTY;
            for (InteractionHand interactionHand : InteractionHand.values()) {
                itemInHand = this.getItemInHand(interactionHand);
                deathProtection = itemInHand.get(DataComponents.DEATH_PROTECTION);
                if (deathProtection != null) {
                    hand = interactionHand; // CraftBukkit
                    itemStack = itemInHand.copy();
                    // itemInHand.shrink(1); // CraftBukkit
                    break;
                }
            }

            // Purpur start - Totems work in inventory
            if (level().purpurConfig.totemOfUndyingWorksInInventory && this instanceof ServerPlayer player && (itemStack == null || itemStack.getItem() != Items.TOTEM_OF_UNDYING) && player.getBukkitEntity().hasPermission("purpur.inventory_totem")) {
                for (ItemStack item : player.getInventory().items) {
                    if (item.getItem() == Items.TOTEM_OF_UNDYING) {
                        itemInHand = item;
                        itemStack = item.copy();
                        break;
                    }
                }
            }
            // Purpur end - Totems work in inventory

            final org.bukkit.inventory.EquipmentSlot handSlot = (hand != null) ? org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(hand) : null;
            final EntityResurrectEvent event = new EntityResurrectEvent((org.bukkit.entity.LivingEntity) this.getBukkitEntity(), handSlot);
            event.setCancelled(itemStack == null);
            this.level().getCraftServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                // Set death protection to null as the event was cancelled. Prevent any attempt at ressurection.
                deathProtection = null;
            } else {
                if (!itemInHand.isEmpty() && itemStack != null) { // Paper - only reduce item if actual totem was found
                    itemInHand.shrink(1);
                }
                // Paper start - fix NPE when pre-cancelled EntityResurrectEvent is uncancelled
                // restore the previous behavior in that case by defaulting to vanillas totem of undying efect
                if (deathProtection == null) {
                    deathProtection = DeathProtection.TOTEM_OF_UNDYING;
                }
                // Paper end - fix NPE when pre-cancelled EntityResurrectEvent is uncancelled
                if (itemStack != null && this instanceof final ServerPlayer serverPlayer) {
            // CraftBukkit end
                    serverPlayer.awardStat(Stats.ITEM_USED.get(itemStack.getItem()));
                    CriteriaTriggers.USED_TOTEM.trigger(serverPlayer, itemStack);
                    this.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
                }

                this.setHealth(1.0F);
                deathProtection.applyEffects(itemStack, this);
                this.level().broadcastEntityEvent(this, (byte)35);
            }

            return deathProtection != null;
        }
    }

    @Nullable
    public DamageSource getLastDamageSource() {
        if (this.level().getGameTime() - this.lastDamageStamp > 40L) {
            this.lastDamageSource = null;
        }

        return this.lastDamageSource;
    }

    protected void playHurtSound(DamageSource source) {
        this.makeSound(this.getHurtSound(source));
    }

    public void makeSound(@Nullable SoundEvent sound) {
        if (sound != null) {
            this.playSound(sound, this.getSoundVolume(), this.getVoicePitch());
        }
    }

    public boolean isDamageSourceBlocked(DamageSource damageSource) {
        Entity directEntity = damageSource.getDirectEntity();
        boolean flag = false;
        if (directEntity instanceof AbstractArrow abstractArrow && abstractArrow.getPierceLevel() > 0) {
            flag = true;
        }

        ItemStack itemBlockingWith = this.getItemBlockingWith();
        if (!damageSource.is(DamageTypeTags.BYPASSES_SHIELD) && itemBlockingWith != null && itemBlockingWith.getItem() instanceof ShieldItem && !flag) {
            Vec3 sourcePosition = damageSource.getSourcePosition();
            if (sourcePosition != null) {
                Vec3 vec3 = this.calculateViewVector(0.0F, this.getYHeadRot());
                Vec3 vec31 = sourcePosition.vectorTo(this.position());
                vec31 = new Vec3(vec31.x, 0.0, vec31.z).normalize();
                return vec31.dot(vec3) < 0.0;
            }
        }

        return false;
    }

    private void breakItem(ItemStack stack) {
        if (!stack.isEmpty()) {
            if (!this.isSilent()) {
                this.level()
                    .playLocalSound(
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        stack.getBreakingSound(),
                        this.getSoundSource(),
                        0.8F,
                        0.8F + this.level().random.nextFloat() * 0.4F,
                        false
                    );
            }

            this.spawnItemParticles(stack, 5);
        }
    }

    public void die(DamageSource damageSource) {
        if (!this.isRemoved() && !this.dead) {
            Entity entity = damageSource.getEntity();
            LivingEntity killCredit = this.getKillCredit();
            /* // Paper - move down to make death event cancellable - this is the awardKillScore below
            if (killCredit != null) {
                killCredit.awardKillScore(this, damageSource);
            }

            if (this.isSleeping()) {
                this.stopSleeping();
            }

            if (!this.level().isClientSide && this.hasCustomName()) {
                if (org.spigotmc.SpigotConfig.logNamedDeaths) LivingEntity.LOGGER.info("Named entity {} died: {}", this, this.getCombatTracker().getDeathMessage().getString()); // Spigot
            }
            */ // Paper - move down to make death event cancellable - this is the awardKillScore below

            this.dead = true;
            // Paper - moved into if below
            if (this.level() instanceof ServerLevel serverLevel) {
                // Paper - move below into if for onKill
                // Paper start
                org.bukkit.event.entity.EntityDeathEvent deathEvent = this.dropAllDeathLoot(serverLevel, damageSource);
                if (deathEvent == null || !deathEvent.isCancelled()) {
                    //if (entityliving != null) { // Paper - Fix item duplication and teleport issues; moved to be run earlier in #dropAllDeathLoot before destroying the drop items in CraftEventFactory#callEntityDeathEvent
                    //    entityliving.awardKillScore(this, damageSource);
                    //}
                    // Paper start - clear equipment if event is not cancelled
                    if (this instanceof Mob) {
                        for (EquipmentSlot slot : this.clearedEquipmentSlots) {
                            this.setItemSlot(slot, ItemStack.EMPTY);
                        }
                        this.clearedEquipmentSlots.clear();
                    }
                    // Paper end

                    if (this.isSleeping()) {
                        this.stopSleeping();
                    }

                    if (!this.level().isClientSide && this.hasCustomName()) {
                        if (org.spigotmc.SpigotConfig.logNamedDeaths) LivingEntity.LOGGER.info("Named entity {} died: {}", this, this.getCombatTracker().getDeathMessage().getString()); // Spigot
                    }

                    this.getCombatTracker().recheckStatus();
                    if (entity != null) {
                        entity.killedEntity((ServerLevel) this.level(), this);
                    }
                    this.gameEvent(GameEvent.ENTITY_DIE);
                } else {
                    this.dead = false;
                    this.setHealth((float) deathEvent.getReviveHealth());
                }
                // Paper end
                    this.createWitherRose(killCredit);
                }

            // Paper start
            if (this.dead) { // Paper
                this.level().broadcastEntityEvent(this, (byte)3);

            this.setPose(Pose.DYING);
            }
            // Paper end
        }
    }

    protected void createWitherRose(@Nullable LivingEntity entitySource) {
        if (this.level() instanceof ServerLevel serverLevel) {
            boolean var6 = false;
            if (this.dead && entitySource instanceof WitherBoss) { // Paper
                if (serverLevel.purpurConfig.witherBypassMobGriefing ^ serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) { // Purpur - Add mobGriefing bypass to everything affected
                    BlockPos blockPos = this.blockPosition();
                    BlockState blockState = Blocks.WITHER_ROSE.defaultBlockState();
                    if (this.level().getBlockState(blockPos).isAir() && blockState.canSurvive(this.level(), blockPos)) {
                        var6 = org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this.level(), blockPos, blockState, 3, this); // CraftBukkit - call EntityBlockFormEvent for Wither Rose
                    }
                }

                if (!var6) {
                    ItemEntity itemEntity = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), new ItemStack(Items.WITHER_ROSE));
                    // CraftBukkit start
                    org.bukkit.event.entity.EntityDropItemEvent event = new org.bukkit.event.entity.EntityDropItemEvent(this.getBukkitEntity(), (org.bukkit.entity.Item) itemEntity.getBukkitEntity());
                    CraftEventFactory.callEvent(event);
                    if (event.isCancelled()) {
                        return;
                    }
                    // CraftBukkit end
                    this.level().addFreshEntity(itemEntity);
                }
            }
        }
    }

    // Paper start
    protected boolean clearEquipmentSlots = true;
    protected Set<EquipmentSlot> clearedEquipmentSlots = new java.util.HashSet<>();
    protected org.bukkit.event.entity.EntityDeathEvent dropAllDeathLoot(ServerLevel level, DamageSource damageSource) {
        // Paper end
        boolean flag = this.lastHurtByPlayerTime > 0;
        this.dropEquipment(level); // CraftBukkit - from below
        if (this.shouldDropLoot() && level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            if (!(damageSource.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING) && level().purpurConfig.disableDropsOnCrammingDeath)) { // Purpur - Disable loot drops on death by cramming
            this.dropFromLootTable(level, damageSource, flag);
            // Paper start
            final boolean prev = this.clearEquipmentSlots;
            this.clearEquipmentSlots = false;
            this.clearedEquipmentSlots.clear();
            // Paper end
            this.dropCustomDeathLoot(level, damageSource, flag);
            this.clearEquipmentSlots = prev; // Paper
            } // Purpur - Disable loot drops on death by cramming
        }

        // CraftBukkit start - Call death event // Paper start - call advancement triggers with correct entity equipment
        org.bukkit.event.entity.EntityDeathEvent deathEvent = CraftEventFactory.callEntityDeathEvent(this, damageSource, this.drops, () -> {
            final LivingEntity entityliving = this.getKillCredit();
            if (entityliving != null) {
                entityliving.awardKillScore(this, damageSource);
            }
        }); // Paper end
        this.postDeathDropItems(deathEvent); // Paper
        this.drops = new ArrayList<>();
        // this.dropEquipment(level); // CraftBukkit - moved up
        // CraftBukkit end
        this.dropExperience(level, damageSource.getEntity());
        return deathEvent; // Paper
    }

    protected void dropEquipment(ServerLevel level) {
    }
    protected void postDeathDropItems(org.bukkit.event.entity.EntityDeathEvent event) {} // Paper - method for post death logic that cannot be ran before the event is potentially cancelled

    public int getExpReward(ServerLevel level, @Nullable Entity entity) { // CraftBukkit
        if (!this.wasExperienceConsumed()
            && (
                this.isAlwaysExperienceDropper()
                    || this.lastHurtByPlayerTime > 0 && this.shouldDropExperience() && level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)
            )) {
            return this.getExperienceReward(level, entity); // CraftBukkit
        }
        return 0; // CraftBukkit
    }

    protected void dropExperience(ServerLevel level, @Nullable Entity entity) {
        // CraftBukkit start - Update getExpReward() above if the removed if() changes!
        if (!(this instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon)) { // CraftBukkit - SPIGOT-2420: Special case ender dragon will drop the xp over time
            ExperienceOrb.award(level, this.position(), this.expToDrop, this instanceof ServerPlayer ? org.bukkit.entity.ExperienceOrb.SpawnReason.PLAYER_DEATH : org.bukkit.entity.ExperienceOrb.SpawnReason.ENTITY_DEATH, entity, this); // Paper
            this.expToDrop = 0;
        }
        // CraftBukkit end
    }

    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
    }

    public long getLootTableSeed() {
        return 0L;
    }

    protected float getKnockback(Entity attacker, DamageSource damageSource) {
        float f = (float)this.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        return this.level() instanceof ServerLevel serverLevel
            ? EnchantmentHelper.modifyKnockback(serverLevel, this.getWeaponItem(), attacker, damageSource, f)
            : f;
    }

    protected void dropFromLootTable(ServerLevel level, DamageSource damageSource, boolean playerKill) {
        Optional<ResourceKey<LootTable>> lootTable = this.getLootTable();
        if (!lootTable.isEmpty()) {
            LootTable lootTable1 = level.getServer().reloadableRegistries().getLootTable(lootTable.get());
            LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, this)
                .withParameter(LootContextParams.ORIGIN, this.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource)
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, damageSource.getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, damageSource.getDirectEntity());
            if (playerKill && this.lastHurtByPlayer != null) {
                builder = builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, this.lastHurtByPlayer).withLuck(this.lastHurtByPlayer.getLuck());
            }

            LootParams lootParams = builder.create(LootContextParamSets.ENTITY);
            lootTable1.getRandomItems(lootParams, this.getLootTableSeed(), stack -> this.spawnAtLocation(level, stack));
        }
    }

    public boolean dropFromGiftLootTable(ServerLevel level, ResourceKey<LootTable> lootTable, BiConsumer<ServerLevel, ItemStack> dropConsumer) {
        return this.dropFromLootTable(
            level,
            lootTable,
            builder -> builder.withParameter(LootContextParams.ORIGIN, this.position())
                .withParameter(LootContextParams.THIS_ENTITY, this)
                .create(LootContextParamSets.GIFT),
            dropConsumer
        );
    }

    protected void dropFromShearingLootTable(
        ServerLevel level, ResourceKey<LootTable> lootTable, ItemStack shears, BiConsumer<ServerLevel, ItemStack> dropConsumer
    ) {
        this.dropFromLootTable(
            level,
            lootTable,
            builder -> builder.withParameter(LootContextParams.ORIGIN, this.position())
                .withParameter(LootContextParams.THIS_ENTITY, this)
                .withParameter(LootContextParams.TOOL, shears)
                .create(LootContextParamSets.SHEARING),
            dropConsumer
        );
    }

    protected boolean dropFromLootTable(
        ServerLevel level,
        ResourceKey<LootTable> lootTable,
        Function<LootParams.Builder, LootParams> paramsBuilder,
        BiConsumer<ServerLevel, ItemStack> dropConsumer
    ) {
        LootTable lootTable1 = level.getServer().reloadableRegistries().getLootTable(lootTable);
        LootParams lootParams = paramsBuilder.apply(new LootParams.Builder(level));
        List<ItemStack> randomItems = lootTable1.getRandomItems(lootParams);
        if (!randomItems.isEmpty()) {
            randomItems.forEach(stack -> dropConsumer.accept(level, stack));
            return true;
        } else {
            return false;
        }
    }

    public void knockback(double strength, double x, double z) {
        // CraftBukkit start - EntityKnockbackEvent
        this.knockback(strength, x, z, null, io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.UNKNOWN); // Paper - knockback events
    }

    public void knockback(double strength, double x, double z, @Nullable Entity attacker, io.papermc.paper.event.entity.EntityKnockbackEvent.Cause eventCause) { // Paper - knockback events
        strength *= 1.0 - this.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (true || !(strength <= 0.0)) { // CraftBukkit - Call event even when force is 0
            // this.hasImpulse = true; // CraftBukkit - Move down
            Vec3 deltaMovement = this.getDeltaMovement();

            while (x * x + z * z < 1.0E-5F) {
                x = (Math.random() - Math.random()) * 0.01;
                z = (Math.random() - Math.random()) * 0.01;
            }

            Vec3 vec3 = new Vec3(x, 0.0, z).normalize().scale(strength);
            // Paper start - knockback events
            Vec3 finalVelocity = new Vec3(
                deltaMovement.x / 2.0 - vec3.x,
                this.onGround() ? Math.min(0.4, deltaMovement.y / 2.0 + strength) : deltaMovement.y,
                deltaMovement.z / 2.0 - vec3.z
            );
            Vec3 diff = finalVelocity.subtract(deltaMovement);
            io.papermc.paper.event.entity.EntityKnockbackEvent event = CraftEventFactory.callEntityKnockbackEvent((org.bukkit.craftbukkit.entity.CraftLivingEntity) this.getBukkitEntity(), attacker, attacker, eventCause, strength, diff);
            // Paper end - knockback events
            if (event.isCancelled()) {
                return;
            }

            this.hasImpulse = true;
            this.setDeltaMovement(deltaMovement.add(event.getKnockback().getX(), event.getKnockback().getY(), event.getKnockback().getZ())); // Paper - knockback events
            // CraftBukkit end
        }
    }

    public void indicateDamage(double xDistance, double zDistance) {
    }

    @Nullable
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.GENERIC_HURT;
    }

    @Nullable
    public SoundEvent getDeathSound() {
        return SoundEvents.GENERIC_DEATH;
    }

    private SoundEvent getFallDamageSound(int height) {
        return height > 4 ? this.getFallSounds().big() : this.getFallSounds().small();
    }

    public void skipDropExperience() {
        this.skipDropExperience = true;
    }

    public boolean wasExperienceConsumed() {
        return this.skipDropExperience;
    }

    public float getHurtDir() {
        return 0.0F;
    }

    protected AABB getHitbox() {
        AABB boundingBox = this.getBoundingBox();
        Entity vehicle = this.getVehicle();
        if (vehicle != null) {
            Vec3 passengerRidingPosition = vehicle.getPassengerRidingPosition(this);
            return boundingBox.setMinY(Math.max(passengerRidingPosition.y, boundingBox.minY));
        } else {
            return boundingBox;
        }
    }

    public Map<Enchantment, Set<EnchantmentLocationBasedEffect>> activeLocationDependentEnchantments(EquipmentSlot slot) {
        return this.activeLocationDependentEnchantments.computeIfAbsent(slot, equipmentSlot -> new Reference2ObjectArrayMap<>());
    }

    public boolean canBeNameTagged() {
        return true;
    }

    public LivingEntity.Fallsounds getFallSounds() {
        return new LivingEntity.Fallsounds(SoundEvents.GENERIC_SMALL_FALL, SoundEvents.GENERIC_BIG_FALL);
    }

    // CraftBukkit start - Add delegate methods
    public SoundEvent getHurtSound0(DamageSource damagesource) {
        return this.getHurtSound(damagesource);
    }

    public SoundEvent getDeathSound0() {
        return this.getDeathSound();
    }

    public SoundEvent getFallDamageSound0(int fallHeight) {
        return this.getFallDamageSound(fallHeight);
    }
    // CraftBukkit end

    public Optional<BlockPos> getLastClimbablePos() {
        return this.lastClimbablePos;
    }

    public boolean onClimbable() {
        if (this.isSpectator()) {
            return false;
        } else {
            BlockPos blockPos = this.blockPosition();
            BlockState inBlockState = this.getInBlockState();
            if (inBlockState.is(BlockTags.CLIMBABLE)) {
                this.lastClimbablePos = Optional.of(blockPos);
                return true;
            } else if (inBlockState.getBlock() instanceof TrapDoorBlock && this.trapdoorUsableAsLadder(blockPos, inBlockState)) {
                this.lastClimbablePos = Optional.of(blockPos);
                return true;
            } else {
                return false;
            }
        }
    }

    private boolean trapdoorUsableAsLadder(BlockPos pos, BlockState state) {
        if (!state.getValue(TrapDoorBlock.OPEN)) {
            return false;
        } else {
            BlockState blockState = this.level().getBlockState(pos.below());
            return blockState.is(Blocks.LADDER) && blockState.getValue(LadderBlock.FACING) == state.getValue(TrapDoorBlock.FACING);
        }
    }

    @Override
    public boolean isAlive() {
        return !this.isRemoved() && this.getHealth() > 0.0F && !this.dead; // Paper - Check this.dead
    }

    public boolean isLookingAtMe(LivingEntity entity, double tolerance, boolean scaleByDistance, boolean visual, double... yValues) {
        Vec3 vec3 = entity.getViewVector(1.0F).normalize();

        for (double d : yValues) {
            Vec3 vec31 = new Vec3(this.getX() - entity.getX(), d - entity.getEyeY(), this.getZ() - entity.getZ());
            double len = vec31.length();
            vec31 = vec31.normalize();
            double d1 = vec3.dot(vec31);
            if (d1 > 1.0 - tolerance / (scaleByDistance ? len : 1.0)
                && entity.hasLineOfSight(this, visual ? ClipContext.Block.VISUAL : ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, d)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int getMaxFallDistance() {
        return this.getComfortableFallDistance(0.0F);
    }

    protected final int getComfortableFallDistance(float health) {
        return Mth.floor(health + 3.0F);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        boolean flag = super.causeFallDamage(fallDistance, multiplier, source);
        int i = this.calculateFallDamage(fallDistance, multiplier);
        if (i > 0) {
            // CraftBukkit start
            if (!this.hurtServer((ServerLevel) this.level(), source, (float) i)) {
                return true;
            }
            // CraftBukkit end
            this.playSound(this.getFallDamageSound(i), 1.0F, 1.0F);
            this.playBlockFallSound();
            // this.hurt(source, i); // CraftBukkit - moved up
            return true;
        } else {
            return flag;
        }
    }

    protected int calculateFallDamage(float fallDistance, float damageMultiplier) {
        if (this.getType().is(EntityTypeTags.FALL_DAMAGE_IMMUNE)) {
            return 0;
        } else {
            float f = (float)this.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
            float f1 = fallDistance - f;
            return Mth.ceil(f1 * damageMultiplier * this.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER));
        }
    }

    protected void playBlockFallSound() {
        if (!this.isSilent()) {
            int floor = Mth.floor(this.getX());
            int floor1 = Mth.floor(this.getY() - 0.2F);
            int floor2 = Mth.floor(this.getZ());
            BlockState blockState = this.level().getBlockState(new BlockPos(floor, floor1, floor2));
            if (!blockState.isAir()) {
                SoundType soundType = blockState.getSoundType();
                this.playSound(soundType.getFallSound(), soundType.getVolume() * 0.5F, soundType.getPitch() * 0.75F);
            }
        }
    }

    @Override
    public void animateHurt(float yaw) {
        this.hurtDuration = 10;
        this.hurtTime = this.hurtDuration;
    }

    public int getArmorValue() {
        return Mth.floor(this.getAttributeValue(Attributes.ARMOR));
    }

    protected void hurtArmor(DamageSource damageSource, float damageAmount) {
    }

    protected void hurtHelmet(DamageSource damageSource, float damageAmount) {
    }

    protected void hurtCurrentlyUsedShield(float damageAmount) {
    }

    protected void doHurtEquipment(DamageSource damageSource, float damageAmount, EquipmentSlot... slots) {
        if (!(damageAmount <= 0.0F)) {
            int i = (int)Math.max(1.0F, damageAmount / 4.0F);

            for (EquipmentSlot equipmentSlot : slots) {
                ItemStack itemBySlot = this.getItemBySlot(equipmentSlot);
                Equippable equippable = itemBySlot.get(DataComponents.EQUIPPABLE);
                if (equippable != null && equippable.damageOnHurt() && itemBySlot.isDamageableItem() && itemBySlot.canBeHurtBy(damageSource)) {
                    itemBySlot.hurtAndBreak(i, this, equipmentSlot);
                }
            }
        }
    }

    protected float getDamageAfterArmorAbsorb(DamageSource damageSource, float damageAmount) {
        if (!damageSource.is(DamageTypeTags.BYPASSES_ARMOR)) {
            // this.hurtArmor(damageSource, damageAmount); // CraftBukkit - actuallyHurt(DamageSource, float, EntityDamageEvent) for damage handling
            damageAmount = CombatRules.getDamageAfterAbsorb(
                this, damageAmount, damageSource, this.getArmorValue(), (float)this.getAttributeValue(Attributes.ARMOR_TOUGHNESS)
            );
        }

        return damageAmount;
    }

    protected float getDamageAfterMagicAbsorb(DamageSource damageSource, float damageAmount) {
        if (damageSource.is(DamageTypeTags.BYPASSES_EFFECTS)) {
            return damageAmount;
        } else {
            // CraftBukkit - Moved to handleEntityDamage(DamageSource, float)
            if (false && this.hasEffect(MobEffects.DAMAGE_RESISTANCE) && !damageSource.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
                int i = (this.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1) * 5;
                int i1 = 25 - i;
                float f = damageAmount * i1;
                float f1 = damageAmount;
                damageAmount = Math.max(f / 25.0F, 0.0F);
                float f2 = f1 - damageAmount;
                if (f2 > 0.0F && f2 < 3.4028235E37F) {
                    if (this instanceof ServerPlayer) {
                        ((ServerPlayer)this).awardStat(Stats.DAMAGE_RESISTED, Math.round(f2 * 10.0F));
                    } else if (damageSource.getEntity() instanceof ServerPlayer) {
                        ((ServerPlayer)damageSource.getEntity()).awardStat(Stats.DAMAGE_DEALT_RESISTED, Math.round(f2 * 10.0F));
                    }
                }
            }

            if (damageAmount <= 0.0F) {
                return 0.0F;
            } else if (damageSource.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
                return damageAmount;
            } else {
                float damageProtection;
                if (this.level() instanceof ServerLevel serverLevel) {
                    damageProtection = EnchantmentHelper.getDamageProtection(serverLevel, this, damageSource);
                } else {
                    damageProtection = 0.0F;
                }

                if (damageProtection > 0.0F) {
                    damageAmount = CombatRules.getDamageAfterMagicAbsorb(damageAmount, damageProtection);
                }

                return damageAmount;
            }
        }
    }

    // CraftBukkit start
    private EntityDamageEvent handleEntityDamage(final DamageSource damagesource, float amount, final float invulnerabilityRelatedLastDamage) { // Paper - fix invulnerability reduction in EntityDamageEvent
        float originalDamage = amount;
        // Paper start - fix invulnerability reduction in EntityDamageEvent
        final com.google.common.base.Function<Double, Double> invulnerabilityReductionEquation = d -> {
            if (invulnerabilityRelatedLastDamage == 0) return 0D; // no last damage, no reduction
            // last damage existed, this means the reduction *technically* is (new damage - last damage).
            // If the event damage was changed to something less than invul damage, hard lock it at 0.
            if (d < invulnerabilityRelatedLastDamage) return 0D;
            return (double) -invulnerabilityRelatedLastDamage;
        };
        final float originalInvulnerabilityReduction = invulnerabilityReductionEquation.apply((double) amount).floatValue();
        amount += originalInvulnerabilityReduction;
        // Paper end - fix invulnerability reduction in EntityDamageEvent

        com.google.common.base.Function<Double, Double> freezing = new com.google.common.base.Function<Double, Double>() {
            @Override
            public Double apply(Double f) {
                if (damagesource.is(DamageTypeTags.IS_FREEZING) && LivingEntity.this.getType().is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES)) {
                    return -(f - (f * 5.0F));
                }
                return -0.0;
            }
        };
        float freezingModifier = freezing.apply((double) amount).floatValue();
        amount += freezingModifier;

        com.google.common.base.Function<Double, Double> hardHat = new com.google.common.base.Function<Double, Double>() {
            @Override
            public Double apply(Double f) {
                if (damagesource.is(DamageTypeTags.DAMAGES_HELMET) && !LivingEntity.this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                    return -(f - (f * 0.75F));
                }
                return -0.0;
            }
        };
        float hardHatModifier = hardHat.apply((double) amount).floatValue();
        amount += hardHatModifier;

        com.google.common.base.Function<Double, Double> blocking = new com.google.common.base.Function<Double, Double>() {
            @Override
            public Double apply(Double f) {
                return -((LivingEntity.this.isDamageSourceBlocked(damagesource)) ? f : 0.0);
            }
        };
        float blockingModifier = blocking.apply((double) amount).floatValue();
        amount += blockingModifier;

        com.google.common.base.Function<Double, Double> armor = new com.google.common.base.Function<Double, Double>() {
            @Override
            public Double apply(Double f) {
                return -(f - LivingEntity.this.getDamageAfterArmorAbsorb(damagesource, f.floatValue()));
            }
        };
        float armorModifier = armor.apply((double) amount).floatValue();
        amount += armorModifier;

        com.google.common.base.Function<Double, Double> resistance = new com.google.common.base.Function<Double, Double>() {
            @Override
            public Double apply(Double f) {
                if (!damagesource.is(DamageTypeTags.BYPASSES_EFFECTS) && LivingEntity.this.hasEffect(MobEffects.DAMAGE_RESISTANCE) && !damagesource.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
                    int i = (LivingEntity.this.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1) * 5;
                    int j = 25 - i;
                    float f1 = f.floatValue() * (float) j;

                    return -(f - Math.max(f1 / 25.0F, 0.0F));
                }
                return -0.0;
            }
        };
        float resistanceModifier = resistance.apply((double) amount).floatValue();
        amount += resistanceModifier;

        com.google.common.base.Function<Double, Double> magic = new com.google.common.base.Function<Double, Double>() {
            @Override
            public Double apply(Double f) {
                return -(f - LivingEntity.this.getDamageAfterMagicAbsorb(damagesource, f.floatValue()));
            }
        };
        float magicModifier = magic.apply((double) amount).floatValue();
        amount += magicModifier;

        com.google.common.base.Function<Double, Double> absorption = new com.google.common.base.Function<Double, Double>() {
            @Override
            public Double apply(Double f) {
                return -(Math.max(f - Math.max(f - LivingEntity.this.getAbsorptionAmount(), 0.0F), 0.0F));
            }
        };
        float absorptionModifier = absorption.apply((double) amount).floatValue();

        // Paper start - fix invulnerability reduction in EntityDamageEvent
        return CraftEventFactory.handleLivingEntityDamageEvent(this, damagesource, originalDamage, freezingModifier, hardHatModifier, blockingModifier, armorModifier, resistanceModifier, magicModifier, absorptionModifier, freezing, hardHat, blocking, armor, resistance, magic, absorption, (damageModifierDoubleMap, damageModifierFunctionMap) -> {
            damageModifierFunctionMap.put(DamageModifier.INVULNERABILITY_REDUCTION, invulnerabilityReductionEquation);
            damageModifierDoubleMap.put(DamageModifier.INVULNERABILITY_REDUCTION, (double) originalInvulnerabilityReduction);
        });
        // Paper end - fix invulnerability reduction in EntityDamageEvent
    }

    protected boolean actuallyHurt(ServerLevel level, final DamageSource damageSource, float amount, final EntityDamageEvent event) { // void -> boolean, add final
        if (!this.isInvulnerableTo(level, damageSource)) {
            if (event.isCancelled()) {
                return false;
            }

            if (damageSource.getEntity() instanceof net.minecraft.world.entity.player.Player) {
                // Paper start - PlayerAttackEntityCooldownResetEvent
                //((net.minecraft.world.entity.player.Player) damagesource.getEntity()).resetAttackStrengthTicker(); // Moved from EntityHuman in order to make the cooldown reset get called after the damage event is fired
                if (damageSource.getEntity() instanceof ServerPlayer) {
                    ServerPlayer player = (ServerPlayer) damageSource.getEntity();
                    if (new com.destroystokyo.paper.event.player.PlayerAttackEntityCooldownResetEvent(player.getBukkitEntity(), this.getBukkitEntity(), player.getAttackStrengthScale(0F)).callEvent()) {
                        player.resetAttackStrengthTicker();
                    }
                } else {
                    ((net.minecraft.world.entity.player.Player) damageSource.getEntity()).resetAttackStrengthTicker();
                }
                // Paper end - PlayerAttackEntityCooldownResetEvent
            }

            // Resistance
            if (event.getDamage(DamageModifier.RESISTANCE) < 0) {
                float f3 = (float) -event.getDamage(DamageModifier.RESISTANCE);
                if (f3 > 0.0F && f3 < 3.4028235E37F) {
                    if (this instanceof ServerPlayer) {
                        ((ServerPlayer) this).awardStat(Stats.DAMAGE_RESISTED, Math.round(f3 * 10.0F));
                    } else if (damageSource.getEntity() instanceof ServerPlayer) {
                        ((ServerPlayer) damageSource.getEntity()).awardStat(Stats.DAMAGE_DEALT_RESISTED, Math.round(f3 * 10.0F));
                    }
                }
            }

            // Apply damage to helmet
            if (damageSource.is(DamageTypeTags.DAMAGES_HELMET) && !this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                this.hurtHelmet(damageSource, amount);
            }

            // Apply damage to armor
            if (!damageSource.is(DamageTypeTags.BYPASSES_ARMOR)) {
                float armorDamage = (float) (event.getDamage() + event.getDamage(DamageModifier.BLOCKING) + event.getDamage(DamageModifier.HARD_HAT));
                this.hurtArmor(damageSource, armorDamage);
            }

            // Apply blocking code // PAIL: steal from above
            if (event.getDamage(DamageModifier.BLOCKING) < 0) {
                this.hurtCurrentlyUsedShield((float) -event.getDamage(DamageModifier.BLOCKING));
                Entity entity = damageSource.getDirectEntity();

                if (!damageSource.is(DamageTypeTags.IS_PROJECTILE) && entity instanceof LivingEntity && entity.distanceToSqr(this) <= (200.0D * 200.0D)) { // Paper - Fix shield disable inconsistency & Check distance in entity interactions
                    this.blockUsingShield((LivingEntity) entity);
                }
            }

            boolean human = this instanceof net.minecraft.world.entity.player.Player;
            float originalDamage = (float) event.getDamage();
            float absorptionModifier = (float) -event.getDamage(DamageModifier.ABSORPTION);
            this.setAbsorptionAmount(Math.max(this.getAbsorptionAmount() - absorptionModifier, 0.0F));
            float f1 = absorptionModifier;

            if (f1 > 0.0F && f1 < 3.4028235E37F && this instanceof Player player) {
                player.awardStat(Stats.DAMAGE_ABSORBED, Math.round(f1 * 10.0F));
            }
            // CraftBukkit end
            if (f1 > 0.0F && f1 < 3.4028235E37F && damageSource.getEntity() instanceof ServerPlayer serverPlayer) {
                serverPlayer.awardStat(Stats.DAMAGE_DEALT_ABSORBED, Math.round(f1 * 10.0F));
            }

            // CraftBukkit start
            if (amount > 0 || !human) {
                if (human) {
                    // PAIL: Be sure to drag all this code from the EntityHuman subclass each update.
                    ((net.minecraft.world.entity.player.Player) this).causeFoodExhaustion(damageSource.getFoodExhaustion(), org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.DAMAGED); // CraftBukkit - EntityExhaustionEvent
                    if (amount < 3.4028235E37F) {
                        ((net.minecraft.world.entity.player.Player) this).awardStat(Stats.DAMAGE_TAKEN, Math.round(amount * 10.0F));
                    }
                }
                // CraftBukkit end
                this.getCombatTracker().recordDamage(damageSource, amount);
                this.setHealth(this.getHealth() - amount);
                // CraftBukkit start
                if (!human) {
                    this.setAbsorptionAmount(this.getAbsorptionAmount() - amount);
                }
                this.gameEvent(GameEvent.ENTITY_DAMAGE);
                return true;
            } else {
                // Duplicate triggers if blocking
                if (event.getDamage(DamageModifier.BLOCKING) < 0) {
                    if (this instanceof ServerPlayer) {
                        CriteriaTriggers.ENTITY_HURT_PLAYER.trigger((ServerPlayer) this, damageSource, originalDamage, amount, true); // Paper - fix taken/dealt param order
                        f1 = (float) -event.getDamage(DamageModifier.BLOCKING);
                        if (f1 > 0.0F && f1 < 3.4028235E37F) {
                            ((ServerPlayer) this).awardStat(Stats.DAMAGE_BLOCKED_BY_SHIELD, Math.round(originalDamage * 10.0F));
                        }
                    }

                    if (damageSource.getEntity() instanceof ServerPlayer) {
                        CriteriaTriggers.PLAYER_HURT_ENTITY.trigger((ServerPlayer) damageSource.getEntity(), this, damageSource, originalDamage, amount, true); // Paper - fix taken/dealt param order
                    }

                    return !io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.skipVanillaDamageTickWhenShieldBlocked; // Paper - this should always return true, however expose an unsupported setting to flip this to false to enable "shield stunning".
                } else {
                    return true; // Paper - return false ONLY if event was cancelled
                }
                // CraftBukkit end
            }
        }
        return true; // CraftBukkit // Paper - return false ONLY if event was cancelled
    }

    public CombatTracker getCombatTracker() {
        return this.combatTracker;
    }

    @Nullable
    public LivingEntity getKillCredit() {
        if (this.lastHurtByPlayer != null) {
            return this.lastHurtByPlayer;
        } else {
            return this.lastHurtByMob != null ? this.lastHurtByMob : null;
        }
    }

    public final float getMaxHealth() {
        return (float)this.getAttributeValue(Attributes.MAX_HEALTH);
    }

    public final float getMaxAbsorption() {
        return (float)this.getAttributeValue(Attributes.MAX_ABSORPTION);
    }

    public final int getArrowCount() {
        return this.entityData.get(DATA_ARROW_COUNT_ID);
    }

    public final void setArrowCount(int count) {
        // CraftBukkit start
        this.setArrowCount(count, false);
    }

    public final void setArrowCount(int count, boolean reset) {
        ArrowBodyCountChangeEvent event = CraftEventFactory.callArrowBodyCountChangeEvent(this, this.getArrowCount(), count, reset);
        if (event.isCancelled()) {
            return;
        }
        this.entityData.set(DATA_ARROW_COUNT_ID, event.getNewAmount());
        // CraftBukkit end
    }

    public final int getStingerCount() {
        return this.entityData.get(DATA_STINGER_COUNT_ID);
    }

    public final void setStingerCount(int stingerCount) {
        this.entityData.set(DATA_STINGER_COUNT_ID, stingerCount);
    }

    private int getCurrentSwingDuration() {
        if (MobEffectUtil.hasDigSpeed(this)) {
            return 6 - (1 + MobEffectUtil.getDigSpeedAmplification(this));
        } else {
            return this.hasEffect(MobEffects.DIG_SLOWDOWN) ? 6 + (1 + this.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) * 2 : 6;
        }
    }

    public void swing(InteractionHand hand) {
        this.swing(hand, false);
    }

    public void swing(InteractionHand hand, boolean updateSelf) {
        if (!this.swinging || this.swingTime >= this.getCurrentSwingDuration() / 2 || this.swingTime < 0) {
            this.swingTime = -1;
            this.swinging = true;
            this.swingingArm = hand;
            if (this.level() instanceof ServerLevel) {
                ClientboundAnimatePacket clientboundAnimatePacket = new ClientboundAnimatePacket(this, hand == InteractionHand.MAIN_HAND ? 0 : 3);
                ServerChunkCache chunkSource = ((ServerLevel)this.level()).getChunkSource();
                if (updateSelf) {
                    chunkSource.broadcastAndSend(this, clientboundAnimatePacket);
                } else {
                    chunkSource.broadcast(this, clientboundAnimatePacket);
                }
            }
        }
    }

    @Override
    public void handleDamageEvent(DamageSource damageSource) {
        this.walkAnimation.setSpeed(1.5F);
        this.invulnerableTime = 20;
        this.hurtDuration = 10;
        this.hurtTime = this.hurtDuration;
        SoundEvent hurtSound = this.getHurtSound(damageSource);
        if (hurtSound != null) {
            this.playSound(hurtSound, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
        }

        this.lastDamageSource = damageSource;
        this.lastDamageStamp = this.level().getGameTime();
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case 3:
                SoundEvent deathSound = this.getDeathSound();
                if (deathSound != null) {
                    this.playSound(deathSound, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                }

                if (!(this instanceof Player)) {
                    this.setHealth(0.0F);
                    this.die(this.damageSources().generic());
                }
                break;
            case 29:
                this.playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F + this.level().random.nextFloat() * 0.4F);
                break;
            case 30:
                this.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + this.level().random.nextFloat() * 0.4F);
                break;
            case 46:
                int i = 128;

                for (int i1 = 0; i1 < 128; i1++) {
                    double d = i1 / 127.0;
                    float f = (this.random.nextFloat() - 0.5F) * 0.2F;
                    float f1 = (this.random.nextFloat() - 0.5F) * 0.2F;
                    float f2 = (this.random.nextFloat() - 0.5F) * 0.2F;
                    double d1 = Mth.lerp(d, this.xo, this.getX()) + (this.random.nextDouble() - 0.5) * this.getBbWidth() * 2.0;
                    double d2 = Mth.lerp(d, this.yo, this.getY()) + this.random.nextDouble() * this.getBbHeight();
                    double d3 = Mth.lerp(d, this.zo, this.getZ()) + (this.random.nextDouble() - 0.5) * this.getBbWidth() * 2.0;
                    this.level().addParticle(ParticleTypes.PORTAL, d1, d2, d3, f, f1, f2);
                }
                break;
            case 47:
                this.breakItem(this.getItemBySlot(EquipmentSlot.MAINHAND));
                break;
            case 48:
                this.breakItem(this.getItemBySlot(EquipmentSlot.OFFHAND));
                break;
            case 49:
                this.breakItem(this.getItemBySlot(EquipmentSlot.HEAD));
                break;
            case 50:
                this.breakItem(this.getItemBySlot(EquipmentSlot.CHEST));
                break;
            case 51:
                this.breakItem(this.getItemBySlot(EquipmentSlot.LEGS));
                break;
            case 52:
                this.breakItem(this.getItemBySlot(EquipmentSlot.FEET));
                break;
            case 54:
                HoneyBlock.showJumpParticles(this);
                break;
            case 55:
                this.swapHandItems();
                break;
            case 60:
                this.makePoofParticles();
                break;
            case 65:
                this.breakItem(this.getItemBySlot(EquipmentSlot.BODY));
                break;
            default:
                super.handleEntityEvent(id);
        }
    }

    public void makePoofParticles() {
        for (int i = 0; i < 20; i++) {
            double d = this.random.nextGaussian() * 0.02;
            double d1 = this.random.nextGaussian() * 0.02;
            double d2 = this.random.nextGaussian() * 0.02;
            double d3 = 10.0;
            this.level()
                .addParticle(ParticleTypes.POOF, this.getRandomX(1.0) - d * 10.0, this.getRandomY() - d1 * 10.0, this.getRandomZ(1.0) - d2 * 10.0, d, d1, d2);
        }
    }

    private void swapHandItems() {
        ItemStack itemBySlot = this.getItemBySlot(EquipmentSlot.OFFHAND);
        this.setItemSlot(EquipmentSlot.OFFHAND, this.getItemBySlot(EquipmentSlot.MAINHAND));
        this.setItemSlot(EquipmentSlot.MAINHAND, itemBySlot);
    }

    @Override
    protected void onBelowWorld() {
        this.hurt(this.damageSources().fellOutOfWorld(), this.level().getWorld().getVoidDamageAmount()); // Paper - use configured void damage amount
    }

    protected void updateSwingTime() {
        int currentSwingDuration = this.getCurrentSwingDuration();
        if (this.swinging) {
            this.swingTime++;
            if (this.swingTime >= currentSwingDuration) {
                this.swingTime = 0;
                this.swinging = false;
            }
        } else {
            this.swingTime = 0;
        }

        this.attackAnim = (float)this.swingTime / currentSwingDuration;
    }

    @Nullable
    public AttributeInstance getAttribute(Holder<Attribute> attribute) {
        return this.getAttributes().getInstance(attribute);
    }

    public double getAttributeValue(Holder<Attribute> attribute) {
        return this.getAttributes().getValue(attribute);
    }

    public double getAttributeBaseValue(Holder<Attribute> attribute) {
        return this.getAttributes().getBaseValue(attribute);
    }

    public AttributeMap getAttributes() {
        return this.attributes;
    }

    public ItemStack getMainHandItem() {
        return this.getItemBySlot(EquipmentSlot.MAINHAND);
    }

    public ItemStack getOffhandItem() {
        return this.getItemBySlot(EquipmentSlot.OFFHAND);
    }

    public ItemStack getItemHeldByArm(HumanoidArm arm) {
        return this.getMainArm() == arm ? this.getMainHandItem() : this.getOffhandItem();
    }

    @Nonnull
    @Override
    public ItemStack getWeaponItem() {
        return this.getMainHandItem();
    }

    public boolean isHolding(Item item) {
        return this.isHolding(stack -> stack.is(item));
    }

    public boolean isHolding(Predicate<ItemStack> predicate) {
        return predicate.test(this.getMainHandItem()) || predicate.test(this.getOffhandItem());
    }

    public ItemStack getItemInHand(InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            return this.getItemBySlot(EquipmentSlot.MAINHAND);
        } else if (hand == InteractionHand.OFF_HAND) {
            return this.getItemBySlot(EquipmentSlot.OFFHAND);
        } else {
            throw new IllegalArgumentException("Invalid hand " + hand);
        }
    }

    public void setItemInHand(InteractionHand hand, ItemStack stack) {
        if (hand == InteractionHand.MAIN_HAND) {
            this.setItemSlot(EquipmentSlot.MAINHAND, stack);
        } else {
            if (hand != InteractionHand.OFF_HAND) {
                throw new IllegalArgumentException("Invalid hand " + hand);
            }

            this.setItemSlot(EquipmentSlot.OFFHAND, stack);
        }
    }

    public boolean hasItemInSlot(EquipmentSlot slot) {
        return !this.getItemBySlot(slot).isEmpty();
    }

    public boolean canUseSlot(EquipmentSlot slot) {
        return false;
    }

    public abstract Iterable<ItemStack> getArmorSlots();

    public abstract ItemStack getItemBySlot(EquipmentSlot slot);

    // CraftBukkit start
    public void setItemSlot(EquipmentSlot enumitemslot, ItemStack itemstack, boolean silent) {
        this.setItemSlot(enumitemslot, itemstack);
    }
    // CraftBukkit end

    public abstract void setItemSlot(EquipmentSlot slot, ItemStack stack);

    public Iterable<ItemStack> getHandSlots() {
        return List.of();
    }

    public Iterable<ItemStack> getArmorAndBodyArmorSlots() {
        return this.getArmorSlots();
    }

    public Iterable<ItemStack> getAllSlots() {
        return Iterables.concat(this.getHandSlots(), this.getArmorAndBodyArmorSlots());
    }

    protected void verifyEquippedItem(ItemStack stack) {
        stack.getItem().verifyComponentsAfterLoad(stack);
    }

    public float getArmorCoverPercentage() {
        Iterable<ItemStack> armorSlots = this.getArmorSlots();
        int i = 0;
        int i1 = 0;

        for (ItemStack itemStack : armorSlots) {
            if (!itemStack.isEmpty()) {
                i1++;
            }

            i++;
        }

        return i > 0 ? (float)i1 / i : 0.0F;
    }

    @Override
    public void setSprinting(boolean sprinting) {
        super.setSprinting(sprinting);
        AttributeInstance attribute = this.getAttribute(Attributes.MOVEMENT_SPEED);
        attribute.removeModifier(SPEED_MODIFIER_SPRINTING.id());
        if (sprinting) {
            attribute.addTransientModifier(SPEED_MODIFIER_SPRINTING);
        }
    }

    public float getSoundVolume() {
        return 1.0F;
    }

    public float getVoicePitch() {
        return this.isBaby()
            ? (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.5F
            : (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F;
    }

    protected boolean isImmobile() {
        return this.isDeadOrDying();
    }

    @Override
    public void push(Entity entity) {
        if (!this.isSleeping()) {
            super.push(entity);
        }
    }

    private void dismountVehicle(Entity vehicle) {
        Vec3 vec3;
        if (this.isRemoved()) {
            vec3 = this.position();
        } else if (!vehicle.isRemoved() && !this.level().getBlockState(vehicle.blockPosition()).is(BlockTags.PORTALS)) {
            vec3 = vehicle.getDismountLocationForPassenger(this);
        } else {
            double max = Math.max(this.getY(), vehicle.getY());
            vec3 = new Vec3(this.getX(), max, this.getZ());
            boolean flag = this.getBbWidth() <= 4.0F && this.getBbHeight() <= 4.0F;
            if (flag) {
                double d = this.getBbHeight() / 2.0;
                Vec3 vec31 = vec3.add(0.0, d, 0.0);
                VoxelShape voxelShape = Shapes.create(AABB.ofSize(vec31, this.getBbWidth(), this.getBbHeight(), this.getBbWidth()));
                vec3 = this.level()
                    .findFreePosition(this, voxelShape, vec31, this.getBbWidth(), this.getBbHeight(), this.getBbWidth())
                    .map(vec32 -> vec32.add(0.0, -d, 0.0))
                    .orElse(vec3);
            }
        }

        this.dismountTo(vec3.x, vec3.y, vec3.z);
    }

    @Override
    public boolean shouldShowName() {
        return this.isCustomNameVisible();
    }

    protected float getJumpPower() {
        return this.getJumpPower(1.0F);
    }

    protected float getJumpPower(float multiplier) {
        return (float)this.getAttributeValue(Attributes.JUMP_STRENGTH) * multiplier * this.getBlockJumpFactor() + this.getJumpBoostPower();
    }

    public float getJumpBoostPower() {
        return this.hasEffect(MobEffects.JUMP) ? 0.1F * (this.getEffect(MobEffects.JUMP).getAmplifier() + 1.0F) : 0.0F;
    }

    protected long lastJumpTime = 0L; // Paper - Prevent excessive velocity through repeated crits
    @VisibleForTesting
    public void jumpFromGround() {
        float jumpPower = this.getJumpPower();
        if (!(jumpPower <= 1.0E-5F)) {
            Vec3 deltaMovement = this.getDeltaMovement();
            // Paper start - Prevent excessive velocity through repeated crits
            long time = System.nanoTime();
            boolean canCrit = true;
            if (this instanceof net.minecraft.world.entity.player.Player) {
                canCrit = false;
                if (time - this.lastJumpTime > (long)(0.250e9)) {
                    this.lastJumpTime = time;
                    canCrit = true;
                }
            }
            // Paper end - Prevent excessive velocity through repeated crits
            this.setDeltaMovement(deltaMovement.x, Math.max((double)jumpPower, deltaMovement.y), deltaMovement.z);
            if (this.isSprinting()) {
                float f = this.getYRot() * (float) (Math.PI / 180.0);
                if (canCrit) // Paper - Prevent excessive velocity through repeated crits
                this.addDeltaMovement(new Vec3(-Mth.sin(f) * 0.2, 0.0, Mth.cos(f) * 0.2));
            }

            this.hasImpulse = true;
        }
    }

    protected void goDownInWater() {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.04F, 0.0));
    }

    protected void jumpInLiquid(TagKey<Fluid> fluidTag) {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.04F, 0.0));
    }

    protected float getWaterSlowDown() {
        return 0.8F;
    }

    public boolean canStandOnFluid(FluidState fluidState) {
        return false;
    }

    @Override
    protected double getDefaultGravity() {
        return this.getAttributeValue(Attributes.GRAVITY);
    }

    protected double getEffectiveGravity() {
        boolean flag = this.getDeltaMovement().y <= 0.0;
        return flag && this.hasEffect(MobEffects.SLOW_FALLING) ? Math.min(this.getGravity(), 0.01) : this.getGravity();
    }

    public void travel(Vec3 travelVector) {
        if (this.isControlledByLocalInstance()) {
            FluidState fluidState = this.level().getFluidState(this.blockPosition());
            if ((this.isInWater() || this.isInLava()) && this.isAffectedByFluids() && !this.canStandOnFluid(fluidState)) {
                this.travelInFluid(travelVector);
            } else if (this.isFallFlying()) {
                this.travelFallFlying();
            } else {
                this.travelInAir(travelVector);
            }
        }
    }

    private void travelInAir(Vec3 travelVector) {
        BlockPos blockPosBelowThatAffectsMyMovement = this.getBlockPosBelowThatAffectsMyMovement();
        float f = this.onGround() ? this.level().getBlockState(blockPosBelowThatAffectsMyMovement).getBlock().getFriction() : 1.0F;
        float f1 = f * 0.91F;
        Vec3 vec3 = this.handleRelativeFrictionAndCalculateMovement(travelVector, f);
        double d = vec3.y;
        MobEffectInstance effect = this.getEffect(MobEffects.LEVITATION);
        if (effect != null) {
            d += (0.05 * (effect.getAmplifier() + 1) - vec3.y) * 0.2;
        } else if (!this.level().isClientSide || this.level().hasChunkAt(blockPosBelowThatAffectsMyMovement)) {
            d -= this.getEffectiveGravity();
        } else if (this.getY() > this.level().getMinY()) {
            d = -0.1;
        } else {
            d = 0.0;
        }

        if (this.shouldDiscardFriction()) {
            this.setDeltaMovement(vec3.x, d, vec3.z);
        } else {
            float f2 = this instanceof FlyingAnimal ? f1 : 0.98F;
            this.setDeltaMovement(vec3.x * f1, d * f2, vec3.z * f1);
        }
    }

    private void travelInFluid(Vec3 travelVector) {
        boolean flag = this.getDeltaMovement().y <= 0.0;
        double y = this.getY();
        double effectiveGravity = this.getEffectiveGravity();
        if (this.isInWater()) {
            float f = this.isSprinting() ? 0.9F : this.getWaterSlowDown();
            float f1 = 0.02F;
            float f2 = (float)this.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY);
            if (!this.onGround()) {
                f2 *= 0.5F;
            }

            if (f2 > 0.0F) {
                f += (0.54600006F - f) * f2;
                f1 += (this.getSpeed() - f1) * f2;
            }

            if (this.hasEffect(MobEffects.DOLPHINS_GRACE)) {
                f = 0.96F;
            }

            this.moveRelative(f1, travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            Vec3 deltaMovement = this.getDeltaMovement();
            if (this.horizontalCollision && this.onClimbable()) {
                deltaMovement = new Vec3(deltaMovement.x, 0.2, deltaMovement.z);
            }

            deltaMovement = deltaMovement.multiply(f, 0.8F, f);
            this.setDeltaMovement(this.getFluidFallingAdjustedMovement(effectiveGravity, flag, deltaMovement));
        } else {
            this.moveRelative(0.02F, travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            if (this.getFluidHeight(FluidTags.LAVA) <= this.getFluidJumpThreshold()) {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.5, 0.8F, 0.5));
                Vec3 fluidFallingAdjustedMovement = this.getFluidFallingAdjustedMovement(effectiveGravity, flag, this.getDeltaMovement());
                this.setDeltaMovement(fluidFallingAdjustedMovement);
            } else {
                this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
            }

            if (effectiveGravity != 0.0) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0, -effectiveGravity / 4.0, 0.0));
            }
        }

        Vec3 fluidFallingAdjustedMovement = this.getDeltaMovement();
        if (this.horizontalCollision
            && this.isFree(fluidFallingAdjustedMovement.x, fluidFallingAdjustedMovement.y + 0.6F - this.getY() + y, fluidFallingAdjustedMovement.z)) {
            this.setDeltaMovement(fluidFallingAdjustedMovement.x, 0.3F, fluidFallingAdjustedMovement.z);
        }
    }

    private void travelFallFlying() {
        Vec3 deltaMovement = this.getDeltaMovement();
        double d = deltaMovement.horizontalDistance();
        this.setDeltaMovement(this.updateFallFlyingMovement(deltaMovement));
        this.move(MoverType.SELF, this.getDeltaMovement());
        if (!this.level().isClientSide) {
            double d1 = this.getDeltaMovement().horizontalDistance();
            this.handleFallFlyingCollisions(d, d1);
        }
    }

    private Vec3 updateFallFlyingMovement(Vec3 deltaMovement) {
        Vec3 lookAngle = this.getLookAngle();
        float f = this.getXRot() * (float) (Math.PI / 180.0);
        double squareRoot = Math.sqrt(lookAngle.x * lookAngle.x + lookAngle.z * lookAngle.z);
        double d = deltaMovement.horizontalDistance();
        double effectiveGravity = this.getEffectiveGravity();
        double squared = Mth.square(Math.cos(f));
        deltaMovement = deltaMovement.add(0.0, effectiveGravity * (-1.0 + squared * 0.75), 0.0);
        if (deltaMovement.y < 0.0 && squareRoot > 0.0) {
            double d1 = deltaMovement.y * -0.1 * squared;
            deltaMovement = deltaMovement.add(lookAngle.x * d1 / squareRoot, d1, lookAngle.z * d1 / squareRoot);
        }

        if (f < 0.0F && squareRoot > 0.0) {
            double d1 = d * -Mth.sin(f) * 0.04;
            deltaMovement = deltaMovement.add(-lookAngle.x * d1 / squareRoot, d1 * 3.2, -lookAngle.z * d1 / squareRoot);
        }

        if (squareRoot > 0.0) {
            deltaMovement = deltaMovement.add(
                (lookAngle.x / squareRoot * d - deltaMovement.x) * 0.1, 0.0, (lookAngle.z / squareRoot * d - deltaMovement.z) * 0.1
            );
        }

        return deltaMovement.multiply(0.99F, 0.98F, 0.99F);
    }

    private void handleFallFlyingCollisions(double oldSpeed, double newSpeed) {
        if (this.horizontalCollision) {
            double d = oldSpeed - newSpeed;
            float f = (float)(d * 10.0 - 3.0);
            if (f > 0.0F) {
                this.playSound(this.getFallDamageSound((int)f), 1.0F, 1.0F);
                if (level().purpurConfig.elytraKineticDamage) // Purpur - Toggle for kinetic damage
                this.hurt(this.damageSources().flyIntoWall(), f);
            }
        }
    }

    private void travelRidden(Player player, Vec3 travelVector) {
        Vec3 riddenInput = this.getRiddenInput(player, travelVector);
        this.tickRidden(player, riddenInput);
        if (this.isControlledByLocalInstance()) {
            this.setSpeed(this.getRiddenSpeed(player));
            this.travel(riddenInput);
        } else {
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    protected void tickRidden(Player player, Vec3 travelVector) {
    }

    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        return travelVector;
    }

    protected float getRiddenSpeed(Player player) {
        return this.getSpeed();
    }

    public void calculateEntityAnimation(boolean includeHeight) {
        float f = (float)Mth.length(this.getX() - this.xo, includeHeight ? this.getY() - this.yo : 0.0, this.getZ() - this.zo);
        if (!this.isPassenger() && this.isAlive()) {
            this.updateWalkAnimation(f);
        } else {
            this.walkAnimation.stop();
        }
    }

    protected void updateWalkAnimation(float partialTick) {
        float min = Math.min(partialTick * 4.0F, 1.0F);
        this.walkAnimation.update(min, 0.4F, this.isBaby() ? 3.0F : 1.0F);
    }

    private Vec3 handleRelativeFrictionAndCalculateMovement(Vec3 deltaMovement, float friction) {
        this.moveRelative(this.getFrictionInfluencedSpeed(friction), deltaMovement);
        this.setDeltaMovement(this.handleOnClimbable(this.getDeltaMovement()));
        this.move(MoverType.SELF, this.getDeltaMovement());
        Vec3 deltaMovement1 = this.getDeltaMovement();
        if ((this.horizontalCollision || this.jumping)
            && (this.onClimbable() || this.getInBlockState().is(Blocks.POWDER_SNOW) && PowderSnowBlock.canEntityWalkOnPowderSnow(this))) {
            deltaMovement1 = new Vec3(deltaMovement1.x, 0.2, deltaMovement1.z);
        }

        return deltaMovement1;
    }

    public Vec3 getFluidFallingAdjustedMovement(double gravity, boolean isFalling, Vec3 deltaMovement) {
        if (gravity != 0.0 && !this.isSprinting()) {
            double d;
            if (isFalling && Math.abs(deltaMovement.y - 0.005) >= 0.003 && Math.abs(deltaMovement.y - gravity / 16.0) < 0.003) {
                d = -0.003;
            } else {
                d = deltaMovement.y - gravity / 16.0;
            }

            return new Vec3(deltaMovement.x, d, deltaMovement.z);
        } else {
            return deltaMovement;
        }
    }

    private Vec3 handleOnClimbable(Vec3 deltaMovement) {
        if (this.onClimbable()) {
            this.resetFallDistance();
            float f = 0.15F;
            double d = Mth.clamp(deltaMovement.x, -0.15F, 0.15F);
            double d1 = Mth.clamp(deltaMovement.z, -0.15F, 0.15F);
            double max = Math.max(deltaMovement.y, -0.15F);
            if (max < 0.0 && !this.getInBlockState().is(Blocks.SCAFFOLDING) && this.isSuppressingSlidingDownLadder() && this instanceof Player) {
                max = 0.0;
            }

            deltaMovement = new Vec3(d, max, d1);
        }

        return deltaMovement;
    }

    private float getFrictionInfluencedSpeed(float friction) {
        return this.onGround() ? this.getSpeed() * (0.21600002F / (friction * friction * friction)) : this.getFlyingSpeed();
    }

    protected float getFlyingSpeed() {
        return this.getControllingPassenger() instanceof net.minecraft.world.entity.player.Player ? this.getSpeed() * 0.1F : 0.02F;
    }

    public float getSpeed() {
        return this.speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public boolean doHurtTarget(ServerLevel level, Entity source) {
        this.setLastHurtMob(source);
        return false;
    }

    // Paper start - EAR 2
    @Override
    public void inactiveTick() {
        super.inactiveTick();
        ++this.noActionTime; // Above all the floats
    }
    // Paper end - EAR 2

    @Override
    public void tick() {
        super.tick();
        this.updatingUsingItem();
        this.updateSwimAmount();
        if (!this.level().isClientSide) {
            int arrowCount = this.getArrowCount();
            if (arrowCount > 0) {
                if (this.removeArrowTime <= 0) {
                    this.removeArrowTime = 20 * (30 - arrowCount);
                }

                this.removeArrowTime--;
                if (this.removeArrowTime <= 0) {
                    this.setArrowCount(arrowCount - 1);
                }
            }

            int stingerCount = this.getStingerCount();
            if (stingerCount > 0) {
                if (this.removeStingerTime <= 0) {
                    this.removeStingerTime = 20 * (30 - stingerCount);
                }

                this.removeStingerTime--;
                if (this.removeStingerTime <= 0) {
                    this.setStingerCount(stingerCount - 1);
                }
            }

            this.detectEquipmentUpdatesPublic(); // CraftBukkit
            if (this.tickCount % 20 == 0) {
                this.getCombatTracker().recheckStatus();
            }

            if (this.isSleeping() && !this.checkBedExists()) {
                this.stopSleeping();
            }
        }

        if (!this.isRemoved()) {
            this.aiStep();
        }

        double d = this.getX() - this.xo;
        double d1 = this.getZ() - this.zo;
        float f = (float)(d * d + d1 * d1);
        float f1 = this.yBodyRot;
        float f2 = 0.0F;
        this.oRun = this.run;
        float f3 = 0.0F;
        if (f > 0.0025000002F) {
            f3 = 1.0F;
            f2 = (float)Math.sqrt(f) * 3.0F;
            float f4 = (float)Mth.atan2(d1, d) * (180.0F / (float)Math.PI) - 90.0F;
            float abs = Mth.abs(Mth.wrapDegrees(this.getYRot()) - f4);
            if (95.0F < abs && abs < 265.0F) {
                f1 = f4 - 180.0F;
            } else {
                f1 = f4;
            }
        }

        if (this.attackAnim > 0.0F) {
            f1 = this.getYRot();
        }

        if (!this.onGround()) {
            f3 = 0.0F;
        }

        this.run = this.run + (f3 - this.run) * 0.3F;
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("headTurn");
        f2 = this.tickHeadTurn(f1, f2);
        profilerFiller.pop();
        profilerFiller.push("rangeChecks");

        // Paper start - stop large pitch and yaw changes from crashing the server
        this.yRotO += Math.round((this.getYRot() - this.yRotO) / 360.0F) * 360.0F;

        this.yBodyRotO += Math.round((this.yBodyRot - this.yBodyRotO) / 360.0F) * 360.0F;

        this.xRotO += Math.round((this.getXRot() - this.xRotO) / 360.0F) * 360.0F;

        this.yHeadRotO += Math.round((this.yHeadRot - this.yHeadRotO) / 360.0F) * 360.0F;

        profilerFiller.pop();
        this.animStep += f2;
        if (this.isFallFlying()) {
            this.fallFlyTicks++;
        } else {
            this.fallFlyTicks = 0;
        }

        if (this.isSleeping()) {
            this.setXRot(0.0F);
        }

        this.refreshDirtyAttributes();
        float abs = this.getScale();
        if (abs != this.appliedScale) {
            this.appliedScale = abs;
            this.refreshDimensions();
        }

        this.elytraAnimationState.tick();
    }

    public void detectEquipmentUpdatesPublic() { // CraftBukkit
        Map<EquipmentSlot, ItemStack> map = this.collectEquipmentChanges();
        if (map != null) {
            this.handleHandSwap(map);
            if (!map.isEmpty()) {
                this.handleEquipmentChanges(map);
            }
        }
    }

    @Nullable
    private Map<EquipmentSlot, ItemStack> collectEquipmentChanges() {
        Map<EquipmentSlot, ItemStack> map = null;

        for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
            ItemStack itemStack = switch (equipmentSlot.getType()) {
                case HAND -> this.getLastHandItem(equipmentSlot);
                case HUMANOID_ARMOR -> this.getLastArmorItem(equipmentSlot);
                case ANIMAL_ARMOR -> this.lastBodyItemStack;
            };
            ItemStack itemBySlot = this.getItemBySlot(equipmentSlot);
            if (this.equipmentHasChanged(itemStack, itemBySlot)) {
                // Paper start - PlayerArmorChangeEvent
                if (this instanceof ServerPlayer && equipmentSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                    final org.bukkit.inventory.ItemStack oldItem = CraftItemStack.asBukkitCopy(itemStack);
                    final org.bukkit.inventory.ItemStack newItem = CraftItemStack.asBukkitCopy(itemBySlot);
                    new com.destroystokyo.paper.event.player.PlayerArmorChangeEvent((org.bukkit.entity.Player) this.getBukkitEntity(), com.destroystokyo.paper.event.player.PlayerArmorChangeEvent.SlotType.valueOf(equipmentSlot.name()), oldItem, newItem).callEvent();
                }
                // Paper end - PlayerArmorChangeEvent
                if (map == null) {
                    map = Maps.newEnumMap(EquipmentSlot.class);
                }

                map.put(equipmentSlot, itemBySlot);
                AttributeMap attributes = this.getAttributes();
                if (!itemStack.isEmpty()) {
                    this.stopLocationBasedEffects(itemStack, equipmentSlot, attributes);
                }
            }
        }

        if (map != null) {
            for (Entry<EquipmentSlot, ItemStack> entry : map.entrySet()) {
                EquipmentSlot equipmentSlot1 = entry.getKey();
                ItemStack itemBySlot = entry.getValue();
                if (!itemBySlot.isEmpty() && !itemBySlot.isBroken()) {
                    itemBySlot.forEachModifier(equipmentSlot1, (attribute, modifier) -> {
                        AttributeInstance instance = this.attributes.getInstance(attribute);
                        if (instance != null) {
                            instance.removeModifier(modifier.id());
                            instance.addTransientModifier(modifier);
                        }
                    });
                    if (this.level() instanceof ServerLevel serverLevel) {
                        EnchantmentHelper.runLocationChangedEffects(serverLevel, itemBySlot, this, equipmentSlot1);
                    }
                }
            }
        }

        return map;
    }

    public boolean equipmentHasChanged(ItemStack oldItem, ItemStack newItem) {
        return !ItemStack.matches(newItem, oldItem);
    }

    private void handleHandSwap(Map<EquipmentSlot, ItemStack> hands) {
        ItemStack itemStack = hands.get(EquipmentSlot.MAINHAND);
        ItemStack itemStack1 = hands.get(EquipmentSlot.OFFHAND);
        if (itemStack != null
            && itemStack1 != null
            && ItemStack.matches(itemStack, this.getLastHandItem(EquipmentSlot.OFFHAND))
            && ItemStack.matches(itemStack1, this.getLastHandItem(EquipmentSlot.MAINHAND))) {
            ((ServerLevel)this.level()).getChunkSource().broadcast(this, new ClientboundEntityEventPacket(this, (byte)55));
            hands.remove(EquipmentSlot.MAINHAND);
            hands.remove(EquipmentSlot.OFFHAND);
            this.setLastHandItem(EquipmentSlot.MAINHAND, itemStack.copy());
            this.setLastHandItem(EquipmentSlot.OFFHAND, itemStack1.copy());
        }
    }

    private void handleEquipmentChanges(Map<EquipmentSlot, ItemStack> equipments) {
        List<Pair<EquipmentSlot, ItemStack>> list = Lists.newArrayListWithCapacity(equipments.size());
        equipments.forEach((slot, stack) -> {
            ItemStack itemStack = stack.copy();
            list.add(Pair.of(slot, itemStack));
            switch (slot.getType()) {
                case HAND:
                    this.setLastHandItem(slot, itemStack);
                    break;
                case HUMANOID_ARMOR:
                    this.setLastArmorItem(slot, itemStack);
                    break;
                case ANIMAL_ARMOR:
                    this.lastBodyItemStack = itemStack;
            }
        });
        ((ServerLevel)this.level()).getChunkSource().broadcast(this, new ClientboundSetEquipmentPacket(this.getId(), list, true)); // Paper - data sanitization
    }

    private ItemStack getLastArmorItem(EquipmentSlot slot) {
        return this.lastArmorItemStacks.get(slot.getIndex());
    }

    private void setLastArmorItem(EquipmentSlot slot, ItemStack stack) {
        this.lastArmorItemStacks.set(slot.getIndex(), stack);
    }

    private ItemStack getLastHandItem(EquipmentSlot slot) {
        return this.lastHandItemStacks.get(slot.getIndex());
    }

    private void setLastHandItem(EquipmentSlot slot, ItemStack stack) {
        this.lastHandItemStacks.set(slot.getIndex(), stack);
    }

    protected float tickHeadTurn(float yRot, float animStep) {
        float f = Mth.wrapDegrees(yRot - this.yBodyRot);
        this.yBodyRot += f * 0.3F;
        float f1 = Mth.wrapDegrees(this.getYRot() - this.yBodyRot);
        float maxHeadRotationRelativeToBody = this.getMaxHeadRotationRelativeToBody();
        if (Math.abs(f1) > maxHeadRotationRelativeToBody) {
            this.yBodyRot = this.yBodyRot + (f1 - Mth.sign(f1) * maxHeadRotationRelativeToBody);
        }

        boolean flag = f1 < -90.0F || f1 >= 90.0F;
        if (flag) {
            animStep *= -1.0F;
        }

        return animStep;
    }

    protected float getMaxHeadRotationRelativeToBody() {
        return 50.0F;
    }

    public void aiStep() {
        if (this.noJumpDelay > 0) {
            this.noJumpDelay--;
        }

        if (this.lerpSteps > 0) {
            this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot, this.lerpXRot);
            this.lerpSteps--;
        } else if (!this.isEffectiveAi()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
        }

        if (this.lerpHeadSteps > 0) {
            this.lerpHeadRotationStep(this.lerpHeadSteps, this.lerpYHeadRot);
            this.lerpHeadSteps--;
        }

        Vec3 deltaMovement = this.getDeltaMovement();
        double d = deltaMovement.x;
        double d1 = deltaMovement.y;
        double d2 = deltaMovement.z;
        if (Math.abs(deltaMovement.x) < 0.003) {
            d = 0.0;
        }

        if (Math.abs(deltaMovement.y) < 0.003) {
            d1 = 0.0;
        }

        if (Math.abs(deltaMovement.z) < 0.003) {
            d2 = 0.0;
        }

        this.setDeltaMovement(d, d1, d2);
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("ai");
        if (this.isImmobile()) {
            this.jumping = false;
            this.xxa = 0.0F;
            this.zza = 0.0F;
        } else if (this.isEffectiveAi()) {
            profilerFiller.push("newAi");
            this.serverAiStep();
            profilerFiller.pop();
        }

        profilerFiller.pop();
        profilerFiller.push("jump");
        if (this.jumping && this.isAffectedByFluids()) {
            double fluidHeight;
            if (this.isInLava()) {
                fluidHeight = this.getFluidHeight(FluidTags.LAVA);
            } else {
                fluidHeight = this.getFluidHeight(FluidTags.WATER);
            }

            boolean flag = this.isInWater() && fluidHeight > 0.0;
            double fluidJumpThreshold = this.getFluidJumpThreshold();
            if (!flag || this.onGround() && !(fluidHeight > fluidJumpThreshold)) {
                if (!this.isInLava() || this.onGround() && !(fluidHeight > fluidJumpThreshold)) {
                    if ((this.onGround() || flag && fluidHeight <= fluidJumpThreshold) && this.noJumpDelay == 0) {
                        if (new com.destroystokyo.paper.event.entity.EntityJumpEvent(getBukkitLivingEntity()).callEvent()) { // Paper - Entity Jump API
                        this.jumpFromGround();
                        this.noJumpDelay = 10;
                        } else { this.setJumping(false); } // Paper - Entity Jump API; setJumping(false) stops a potential loop
                    }
                } else {
                    this.jumpInLiquid(FluidTags.LAVA);
                }
            } else {
                this.jumpInLiquid(FluidTags.WATER);
            }
        } else {
            this.noJumpDelay = 0;
        }

        profilerFiller.pop();
        profilerFiller.push("travel");
        this.xxa *= 0.98F;
        this.zza *= 0.98F;
        if (this.isFallFlying()) {
            this.updateFallFlying();
        }

        AABB boundingBox = this.getBoundingBox();
        Vec3 vec3 = new Vec3(this.xxa, this.yya, this.zza);
        if (this.hasEffect(MobEffects.SLOW_FALLING) || this.hasEffect(MobEffects.LEVITATION)) {
            this.resetFallDistance();
        }

        if (this.getControllingPassenger() instanceof Player player && this.isAlive()) {
            this.travelRidden(player, vec3);
        } else {
            this.travel(vec3);
        }

        if (!this.level().isClientSide() || this.isControlledByLocalInstance()) {
            this.applyEffectsFromBlocks();
        }

        this.calculateEntityAnimation(this instanceof FlyingAnimal);
        profilerFiller.pop();
        profilerFiller.push("freezing");
        if (!this.level().isClientSide && !this.isDeadOrDying() && !this.freezeLocked) { // Paper - Freeze Tick Lock API
            int ticksFrozen = this.getTicksFrozen();
            if (this.isInPowderSnow && this.canFreeze()) {
                this.setTicksFrozen(Math.min(this.getTicksRequiredToFreeze(), ticksFrozen + 1));
            } else {
                this.setTicksFrozen(Math.max(0, ticksFrozen - 2));
            }
        }

        this.removeFrost();
        this.tryAddFrost();
        if (this.level() instanceof ServerLevel serverLevel && this.tickCount % 40 == 0 && this.isFullyFrozen() && this.canFreeze()) {
            this.hurtServer(serverLevel, this.damageSources().freeze(), 1.0F);
        }

        profilerFiller.pop();
        profilerFiller.push("push");
        if (this.autoSpinAttackTicks > 0) {
            this.autoSpinAttackTicks--;
            this.checkAutoSpinAttack(boundingBox, this.getBoundingBox());
        }

        this.pushEntities();
        profilerFiller.pop();
        // Paper start - Add EntityMoveEvent
        // Purpur start - Ridables
        if (this.xo != this.getX() || this.yo != this.getY() || this.zo != this.getZ() || this.yRotO != this.getYRot() || this.xRotO != this.getXRot()) {
            if (((ServerLevel) this.level()).hasEntityMoveEvent && !(this instanceof Player)) {
                // Purpur end - Ridables
                Location from = new Location(this.level().getWorld(), this.xo, this.yo, this.zo, this.yRotO, this.xRotO);
                Location to = new Location(this.level().getWorld(), this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
                io.papermc.paper.event.entity.EntityMoveEvent event = new io.papermc.paper.event.entity.EntityMoveEvent(this.getBukkitLivingEntity(), from, to.clone());
                if (!event.callEvent()) {
                    this.absMoveTo(from.getX(), from.getY(), from.getZ(), from.getYaw(), from.getPitch());
                } else if (!to.equals(event.getTo())) {
                    this.absMoveTo(event.getTo().getX(), event.getTo().getY(), event.getTo().getZ(), event.getTo().getYaw(), event.getTo().getPitch());
                }
            }
            // Purpur start - Ridables
            if (getRider() != null) {
                getRider().resetLastActionTime();
                if (((ServerLevel) level()).hasRidableMoveEvent && this instanceof Mob) {
                    Location from = new Location(level().getWorld(), xo, yo, zo, this.yRotO, this.xRotO);
                    Location to = new Location(level().getWorld(), getX(), getY(), getZ(), this.getYRot(), this.getXRot());
                    org.purpurmc.purpur.event.entity.RidableMoveEvent event = new org.purpurmc.purpur.event.entity.RidableMoveEvent((org.bukkit.entity.Mob) getBukkitLivingEntity(), (org.bukkit.entity.Player) getRider().getBukkitEntity(), from, to.clone());
                    if (!event.callEvent()) {
                        absMoveTo(from.getX(), from.getY(), from.getZ(), from.getYaw(), from.getPitch());
                    } else if (!to.equals(event.getTo())) {
                        absMoveTo(to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch());
                    }
                }
            }
            // Purpur end - Ridables
        }
        // Paper end - Add EntityMoveEvent
        if (this.level() instanceof ServerLevel serverLevel && this.isSensitiveToWater() && this.isInWaterRainOrBubble()) {
            this.hurtServer(serverLevel, this.damageSources().drown(), 1.0F);
        }
    }

    public boolean isSensitiveToWater() {
        return false;
    }

    protected void updateFallFlying() {
        this.checkSlowFallDistance();
        if (!this.level().isClientSide) {
            if (!this.canGlide()) {
                if (this.getSharedFlag(7) != false && !CraftEventFactory.callToggleGlideEvent(this, false).isCancelled()) // CraftBukkit
                this.setSharedFlag(7, false);
                return;
            }

            int i = this.fallFlyTicks + 1;
            if (i % 10 == 0) {
                int i1 = i / 10;
                if (i1 % 2 == 0) {
                    List<EquipmentSlot> list = EquipmentSlot.VALUES.stream().filter(slot -> canGlideUsing(this.getItemBySlot(slot), slot)).toList();
                    EquipmentSlot equipmentSlot = Util.getRandom(list, this.random);

                    // Purpur start - Implement elytra settings
                    int damage = level().purpurConfig.elytraDamagePerSecond;
                    if (level().purpurConfig.elytraDamageMultiplyBySpeed > 0) {
                        double speed = getDeltaMovement().lengthSqr();
                        if (speed > level().purpurConfig.elytraDamageMultiplyBySpeed) {
                            damage *= (int) speed;
                        }
                    }

                    this.getItemBySlot(equipmentSlot).hurtAndBreak(damage, this, equipmentSlot);
                    // Purpur end - Implement elytra settings
                }

                this.gameEvent(GameEvent.ELYTRA_GLIDE);
            }
        }
    }

    public boolean canGlide() {
        if (!this.onGround() && !this.isPassenger() && !this.hasEffect(MobEffects.LEVITATION)) {
            for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
                if (canGlideUsing(this.getItemBySlot(equipmentSlot), equipmentSlot)) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    protected void serverAiStep() {
    }

    protected void pushEntities() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            this.level().getEntities(EntityTypeTest.forClass(Player.class), this.getBoundingBox(), EntitySelector.pushableBy(this)).forEach(this::doPush);
        } else {
            // Paper start - don't run getEntities if we're not going to use its result
            if (!this.isPushable()) {
                return;
            }

            net.minecraft.world.scores.Team team = this.getTeam();
            if (team != null && team.getCollisionRule() == net.minecraft.world.scores.Team.CollisionRule.NEVER) {
                return;
            }

            int _int = serverLevel.getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);
            if (_int <= 0 && this.level().paperConfig().collisions.maxEntityCollisions <= 0) {
                return;
            }
            // Paper end - don't run getEntities if we're not going to use its result
            List<Entity> entities = this.level().getEntities(this, this.getBoundingBox(), EntitySelector.pushable(this, this.level().paperConfig().collisions.fixClimbingBypassingCrammingRule)); // Paper - Climbing should not bypass cramming gamerule
            if (!entities.isEmpty()) {
                // Paper - don't run getEntities if we're not going to use its result; moved up
                if (_int > 0 && entities.size() > _int - 1 && this.random.nextInt(4) == 0) {
                    int i = 0;

                    for (Entity entity : entities) {
                        if (!entity.isPassenger()) {
                            i++;
                        }
                    }

                    if (i > _int - 1) {
                        this.hurtServer(serverLevel, this.damageSources().cramming(), 6.0F);
                    }
                }

                // Paper start - Cap entity collisions
                this.numCollisions = Math.max(0, this.numCollisions - this.level().paperConfig().collisions.maxEntityCollisions);
                for (Entity entity1 : entities) {
                    if (this.numCollisions >= this.level().paperConfig().collisions.maxEntityCollisions) {
                        break;
                    }

                    entity1.numCollisions++;
                    this.numCollisions++;
                    // Paper end - Cap entity collisions
                    this.doPush(entity1);
                }
            }
        }
    }

    protected void checkAutoSpinAttack(AABB boundingBoxBeforeSpin, AABB boundingBoxAfterSpin) {
        AABB aabb = boundingBoxBeforeSpin.minmax(boundingBoxAfterSpin);
        List<Entity> entities = this.level().getEntities(this, aabb);
        if (!entities.isEmpty()) {
            for (Entity entity : entities) {
                if (entity instanceof LivingEntity) {
                    this.doAutoAttackOnTouch((LivingEntity)entity);
                    this.autoSpinAttackTicks = 0;
                    this.setDeltaMovement(this.getDeltaMovement().scale(-0.2));
                    break;
                }
            }
        } else if (this.horizontalCollision) {
            this.autoSpinAttackTicks = 0;
        }

        if (!this.level().isClientSide && this.autoSpinAttackTicks <= 0) {
            this.setLivingEntityFlag(4, false);
            this.autoSpinAttackDmg = 0.0F;
            this.autoSpinAttackItemStack = null;
        }
    }

    protected void doPush(Entity entity) {
        entity.push(this);
    }

    protected void doAutoAttackOnTouch(LivingEntity target) {
    }

    public boolean isAutoSpinAttack() {
        return (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 4) != 0;
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
        if (vehicle != null && vehicle != this.getVehicle() && !this.level().isClientSide && vehicle.valid) { // Paper - don't process on world gen
            this.dismountVehicle(vehicle);
        }
    }

    @Override
    public void rideTick() {
        super.rideTick();
        this.oRun = this.run;
        this.run = 0.0F;
        this.resetFallDistance();
    }

    @Override
    public void cancelLerp() {
        this.lerpSteps = 0;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = steps;
    }

    @Override
    public double lerpTargetX() {
        return this.lerpSteps > 0 ? this.lerpX : this.getX();
    }

    @Override
    public double lerpTargetY() {
        return this.lerpSteps > 0 ? this.lerpY : this.getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.lerpSteps > 0 ? this.lerpZ : this.getZ();
    }

    @Override
    public float lerpTargetXRot() {
        return this.lerpSteps > 0 ? (float)this.lerpXRot : this.getXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return this.lerpSteps > 0 ? (float)this.lerpYRot : this.getYRot();
    }

    @Override
    public void lerpHeadTo(float yaw, int pitch) {
        this.lerpYHeadRot = yaw;
        this.lerpHeadSteps = pitch;
    }

    public void setJumping(boolean jumping) {
        this.jumping = jumping;
    }

    public void onItemPickup(ItemEntity itemEntity) {
        Entity owner = itemEntity.thrower != null ? this.level().getGlobalPlayerByUUID(itemEntity.thrower) : null; // Paper - check global player list where appropriate
        if (owner instanceof ServerPlayer) {
            CriteriaTriggers.THROWN_ITEM_PICKED_UP_BY_ENTITY.trigger((ServerPlayer)owner, itemEntity.getItem(), this);
        }
    }

    public void take(Entity entity, int amount) {
        if (!entity.isRemoved()
            && !this.level().isClientSide
            && (entity instanceof ItemEntity || entity instanceof AbstractArrow || entity instanceof ExperienceOrb)) {
            ((ServerLevel)this.level()).getChunkSource().broadcastAndSend(this, new ClientboundTakeItemEntityPacket(entity.getId(), this.getId(), amount)); // Paper - broadcast with collector as source
        }
    }

    public boolean hasLineOfSight(Entity entity) {
        return this.hasLineOfSight(entity, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity.getEyeY());
    }

    public boolean hasLineOfSight(Entity entity, ClipContext.Block block, ClipContext.Fluid fluid, double y) {
        if (entity.level() != this.level()) {
            return false;
        } else {
            Vec3 vec3 = new Vec3(this.getX(), this.getEyeY(), this.getZ());
            Vec3 vec31 = new Vec3(entity.getX(), y, entity.getZ());
            // Paper - diff on change - used in CraftLivingEntity#hasLineOfSight(Location) and CraftWorld#lineOfSightExists
            return !(vec31.distanceToSqr(vec3) > 128.0D * 128.0D) && this.level().clip(new ClipContext(vec3, vec31, block, fluid, this)).getType() == HitResult.Type.MISS; // Paper - Perf: Use distance squared
        }
    }

    @Override
    public float getViewYRot(float partialTicks) {
        return partialTicks == 1.0F ? this.yHeadRot : Mth.rotLerp(partialTicks, this.yHeadRotO, this.yHeadRot);
    }

    public float getAttackAnim(float partialTick) {
        float f = this.attackAnim - this.oAttackAnim;
        if (f < 0.0F) {
            f++;
        }

        return this.oAttackAnim + f * partialTick;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved() && this.collides; // CraftBukkit
    }

    // Paper start - Climbing should not bypass cramming gamerule
    @Override
    public boolean isPushable() {
        return this.isCollidable(this.level().paperConfig().collisions.fixClimbingBypassingCrammingRule);
    }

    @Override
    public boolean isCollidable(boolean ignoreClimbing) {
        return this.isAlive() && !this.isSpectator() && (ignoreClimbing || !this.onClimbable()) && this.collides; // CraftBukkit
        // Paper end - Climbing should not bypass cramming gamerule
    }

    // CraftBukkit start - collidable API
    @Override
    public boolean canCollideWithBukkit(Entity entity) {
        return this.isPushable() && this.collides != this.collidableExemptions.contains(entity.getUUID());
    }
    // CraftBukkit end

    @Override
    public float getYHeadRot() {
        return this.yHeadRot;
    }

    @Override
    public void setYHeadRot(float rotation) {
        this.yHeadRot = rotation;
    }

    @Override
    public void setYBodyRot(float offset) {
        this.yBodyRot = offset;
    }

    @Override
    public Vec3 getRelativePortalPosition(Direction.Axis axis, BlockUtil.FoundRectangle portal) {
        return resetForwardDirectionOfRelativePortalPosition(super.getRelativePortalPosition(axis, portal));
    }

    public static Vec3 resetForwardDirectionOfRelativePortalPosition(Vec3 relativePortalPosition) {
        return new Vec3(relativePortalPosition.x, relativePortalPosition.y, 0.0);
    }

    public float getAbsorptionAmount() {
        return this.absorptionAmount;
    }

    public final void setAbsorptionAmount(float absorptionAmount) {
        this.internalSetAbsorptionAmount(!Float.isNaN(absorptionAmount) ? Mth.clamp(absorptionAmount, 0.0F, this.getMaxAbsorption()) : 0.0F); // Paper - Check for NaN
    }

    protected void internalSetAbsorptionAmount(float absorptionAmount) {
        this.absorptionAmount = absorptionAmount;
    }

    public void onEnterCombat() {
    }

    public void onLeaveCombat() {
    }

    protected void updateEffectVisibility() {
        this.effectsDirty = true;
    }

    public abstract HumanoidArm getMainArm();

    public boolean isUsingItem() {
        return (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 1) > 0;
    }

    public InteractionHand getUsedItemHand() {
        return (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 2) > 0 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    // Paper start - Properly cancel usable items
    public void resyncUsingItem(ServerPlayer serverPlayer) {
        this.resendPossiblyDesyncedDataValues(java.util.List.of(DATA_LIVING_ENTITY_FLAGS), serverPlayer);
    }
    // Paper end - Properly cancel usable items
    // Paper start - lag compensate eating
    protected long eatStartTime;
    protected int totalEatTimeTicks;
    // Paper end - lag compensate eating
    private void updatingUsingItem() {
        if (this.isUsingItem()) {
            if (ItemStack.isSameItem(this.getItemInHand(this.getUsedItemHand()), this.useItem)) {
                this.useItem = this.getItemInHand(this.getUsedItemHand());
                this.updateUsingItem(this.useItem);
            } else {
                this.stopUsingItem();
            }
        }
    }

    protected void updateUsingItem(ItemStack usingItem) {
        usingItem.onUseTick(this.level(), this, this.getUseItemRemainingTicks());
        // Paper start - lag compensate eating
        // we add 1 to the expected time to avoid lag compensating when we should not
        final boolean shouldLagCompensate = this.useItem.has(DataComponents.FOOD) && this.eatStartTime != -1 && (System.nanoTime() - this.eatStartTime) > ((1L + this.totalEatTimeTicks) * 50L * (1000L * 1000L));
        if ((--this.useItemRemaining == 0 || shouldLagCompensate) && !this.level().isClientSide && !usingItem.useOnRelease()) {
            this.useItemRemaining = 0;
            // Paper end - lag compensate eating
            this.completeUsingItem();
        }
    }

    private void updateSwimAmount() {
        this.swimAmountO = this.swimAmount;
        if (this.isVisuallySwimming()) {
            this.swimAmount = Math.min(1.0F, this.swimAmount + 0.09F);
        } else {
            this.swimAmount = Math.max(0.0F, this.swimAmount - 0.09F);
        }
    }

    public void setLivingEntityFlag(int key, boolean value) {
        int i = this.entityData.get(DATA_LIVING_ENTITY_FLAGS);
        if (value) {
            i |= key;
        } else {
            i &= ~key;
        }

        this.entityData.set(DATA_LIVING_ENTITY_FLAGS, (byte)i);
    }

    public void startUsingItem(InteractionHand hand) {
        // Paper start - Prevent consuming the wrong itemstack
        this.startUsingItem(hand, false);
    }
    public void startUsingItem(InteractionHand hand, boolean forceUpdate) {
        // Paper end - Prevent consuming the wrong itemstack
        ItemStack itemInHand = this.getItemInHand(hand);
        if (!itemInHand.isEmpty() && !this.isUsingItem() || forceUpdate) { // Paper - Prevent consuming the wrong itemstack
            this.useItem = itemInHand;
            // Paper start - lag compensate eating
            this.useItemRemaining = this.totalEatTimeTicks = itemInHand.getUseDuration(this);
            this.eatStartTime = System.nanoTime();
            // Paper end - lag compensate eating
            if (!this.level().isClientSide) {
                this.setLivingEntityFlag(1, true);
                this.setLivingEntityFlag(2, hand == InteractionHand.OFF_HAND);
                this.gameEvent(GameEvent.ITEM_INTERACT_START);
            }
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SLEEPING_POS_ID.equals(key)) {
            if (this.level().isClientSide) {
                this.getSleepingPos().ifPresent(this::setPosToBed);
            }
        } else if (DATA_LIVING_ENTITY_FLAGS.equals(key) && this.level().isClientSide) {
            if (this.isUsingItem() && this.useItem.isEmpty()) {
                this.useItem = this.getItemInHand(this.getUsedItemHand());
                if (!this.useItem.isEmpty()) {
                    this.useItemRemaining = this.useItem.getUseDuration(this);
                }
            } else if (!this.isUsingItem() && !this.useItem.isEmpty()) {
                this.useItem = ItemStack.EMPTY;
                // Paper start - lag compensate eating
                this.useItemRemaining = this.totalEatTimeTicks = 0;
                this.eatStartTime = -1L;
                // Paper end - lag compensate eating
            }
        }
    }

    @Override
    public void lookAt(EntityAnchorArgument.Anchor anchor, Vec3 target) {
        super.lookAt(anchor, target);
        this.yHeadRotO = this.yHeadRot;
        this.yBodyRot = this.yHeadRot;
        this.yBodyRotO = this.yBodyRot;
    }

    @Override
    public float getPreciseBodyRotation(float partialTick) {
        return Mth.lerp(partialTick, this.yBodyRotO, this.yBodyRot);
    }

    public void spawnItemParticles(ItemStack stack, int amount) {
        for (int i = 0; i < amount; i++) {
            Vec3 vec3 = new Vec3((this.random.nextFloat() - 0.5) * 0.1, Math.random() * 0.1 + 0.1, 0.0);
            vec3 = vec3.xRot(-this.getXRot() * (float) (Math.PI / 180.0));
            vec3 = vec3.yRot(-this.getYRot() * (float) (Math.PI / 180.0));
            double d = -this.random.nextFloat() * 0.6 - 0.3;
            Vec3 vec31 = new Vec3((this.random.nextFloat() - 0.5) * 0.3, d, 0.6);
            vec31 = vec31.xRot(-this.getXRot() * (float) (Math.PI / 180.0));
            vec31 = vec31.yRot(-this.getYRot() * (float) (Math.PI / 180.0));
            vec31 = vec31.add(this.getX(), this.getEyeY(), this.getZ());
            this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, stack), vec31.x, vec31.y, vec31.z, vec3.x, vec3.y + 0.05, vec3.z);
        }
    }

    public void completeUsingItem() {
        if (!this.level().isClientSide || this.isUsingItem()) {
            InteractionHand usedItemHand = this.getUsedItemHand();
            if (!this.useItem.equals(this.getItemInHand(usedItemHand))) {
                this.releaseUsingItem();
            } else {
                if (!this.useItem.isEmpty() && this.isUsingItem()) {
                    this.startUsingItem(this.getUsedItemHand(), true); // Paper - Prevent consuming the wrong itemstack
                    // CraftBukkit start - fire PlayerItemConsumeEvent
                    ItemStack itemStack;
                    PlayerItemConsumeEvent event = null; // Paper
                    if (this instanceof ServerPlayer serverPlayer) {
                        org.bukkit.inventory.ItemStack craftItem = CraftItemStack.asBukkitCopy(this.useItem);
                        org.bukkit.inventory.EquipmentSlot hand = org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(usedItemHand);
                        event = new PlayerItemConsumeEvent((org.bukkit.entity.Player) this.getBukkitEntity(), craftItem, hand); // Paper
                        this.level().getCraftServer().getPluginManager().callEvent(event);

                        if (event.isCancelled()) {
                            // Update client
                            Consumable consumable = this.useItem.get(DataComponents.CONSUMABLE);
                            if (consumable != null) {
                                consumable.cancelUsingItem(serverPlayer, this.useItem);
                            }
                            serverPlayer.getBukkitEntity().updateInventory();
                            serverPlayer.getBukkitEntity().updateScaledHealth();
                            this.stopUsingItem(); // Paper - event is using an item, clear active item to reset its use
                            return;
                        }

                        itemStack = (craftItem.equals(event.getItem())) ? this.useItem.finishUsingItem(this.level(), this) : CraftItemStack.asNMSCopy(event.getItem()).finishUsingItem(this.level(), this);
                    } else {
                        itemStack = this.useItem.finishUsingItem(this.level(), this);
                    }
                    // Paper start - save the default replacement item and change it if necessary
                    final ItemStack defaultReplacement = itemStack;
                    if (event != null && event.getReplacement() != null) {
                        itemStack = CraftItemStack.asNMSCopy(event.getReplacement());
                    }
                    // Paper end
                    // CraftBukkit end
                    if (itemStack != this.useItem) {
                        this.setItemInHand(usedItemHand, itemStack);
                    }

                    this.stopUsingItem();
                    // Paper start
                    if (this instanceof ServerPlayer) {
                        ((ServerPlayer) this).getBukkitEntity().updateInventory();
                    }
                    // Paper end
                }
            }
        }
    }

    public void handleExtraItemsCreatedOnUse(ItemStack stack) {
    }

    public ItemStack getUseItem() {
        return this.useItem;
    }

    public int getUseItemRemainingTicks() {
        return this.useItemRemaining;
    }

    public int getTicksUsingItem() {
        return this.isUsingItem() ? this.useItem.getUseDuration(this) - this.getUseItemRemainingTicks() : 0;
    }

    public void releaseUsingItem() {
        if (!this.useItem.isEmpty()) {
            if (this instanceof ServerPlayer) new io.papermc.paper.event.player.PlayerStopUsingItemEvent((org.bukkit.entity.Player) getBukkitEntity(), useItem.asBukkitMirror(), getTicksUsingItem()).callEvent(); // Paper - Add PlayerStopUsingItemEvent
            this.useItem.releaseUsing(this.level(), this, this.getUseItemRemainingTicks());
            if (this.useItem.useOnRelease()) {
                this.updatingUsingItem();
            }
        }

        this.stopUsingItem();
    }

    public void stopUsingItem() {
        if (!this.level().isClientSide) {
            boolean isUsingItem = this.isUsingItem();
            this.setLivingEntityFlag(1, false);
            if (isUsingItem) {
                this.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
            }
        }

        this.useItem = ItemStack.EMPTY;
        // Paper start - lag compensate eating
        this.useItemRemaining = this.totalEatTimeTicks = 0;
        this.eatStartTime = -1L;
        // Paper end - lag compensate eating
    }

    public boolean isBlocking() {
        return this.getItemBlockingWith() != null;
    }

    @Nullable
    public ItemStack getItemBlockingWith() {
        if (this.isUsingItem() && !this.useItem.isEmpty()) {
            Item item = this.useItem.getItem();
            if (item.getUseAnimation(this.useItem) != ItemUseAnimation.BLOCK) {
                return null;
            } else {
                return item.getUseDuration(this.useItem, this) - this.useItemRemaining < this.getShieldBlockingDelay() ? null : this.useItem; // Paper - Make shield blocking delay configurable
            }
        } else {
            return null;
        }
    }

    // Paper start - Make shield blocking delay configurable
    public HitResult getRayTrace(int maxDistance, ClipContext.Fluid fluidCollisionOption) {
        if (maxDistance < 1 || maxDistance > 120) {
            throw new IllegalArgumentException("maxDistance must be between 1-120");
        }

        Vec3 start = new Vec3(getX(), getY() + getEyeHeight(), getZ());
        org.bukkit.util.Vector dir = getBukkitEntity().getLocation().getDirection().multiply(maxDistance);
        Vec3 end = new Vec3(start.x + dir.getX(), start.y + dir.getY(), start.z + dir.getZ());
        ClipContext raytrace = new ClipContext(start, end, ClipContext.Block.OUTLINE, fluidCollisionOption, this);

        return this.level().clip(raytrace);
    }

    public @Nullable net.minecraft.world.phys.EntityHitResult getTargetEntity(int maxDistance) {
        if (maxDistance < 1 || maxDistance > 120) {
            throw new IllegalArgumentException("maxDistance must be between 1-120");
        }

        Vec3 start = this.getEyePosition(1.0F);
        Vec3 direction = this.getLookAngle();
        Vec3 end = start.add(direction.x * maxDistance, direction.y * maxDistance, direction.z * maxDistance);

        List<Entity> entityList = this.level().getEntities(this, getBoundingBox().expandTowards(direction.x * maxDistance, direction.y * maxDistance, direction.z * maxDistance).inflate(1.0D, 1.0D, 1.0D), EntitySelector.NO_SPECTATORS.and(Entity::isPickable));

        double distance = 0.0D;
        net.minecraft.world.phys.EntityHitResult result = null;

        for (Entity entity : entityList) {
            final double inflationAmount = (double) entity.getPickRadius();
            AABB aabb = entity.getBoundingBox().inflate(inflationAmount, inflationAmount, inflationAmount);
            Optional<Vec3> rayTraceResult = aabb.clip(start, end);

            if (rayTraceResult.isPresent()) {
                Vec3 rayTrace = rayTraceResult.get();
                double distanceTo = start.distanceToSqr(rayTrace);
                if (distanceTo < distance || distance == 0.0D) {
                    result = new net.minecraft.world.phys.EntityHitResult(entity, rayTrace);
                    distance = distanceTo;
                }
            }
        }

        return result;
    }

    public int shieldBlockingDelay = this.level().paperConfig().misc.shieldBlockingDelay;

    public int getShieldBlockingDelay() {
        return shieldBlockingDelay;
    }

    public void setShieldBlockingDelay(int shieldBlockingDelay) {
        this.shieldBlockingDelay = shieldBlockingDelay;
    }
    // Paper end - Make shield blocking delay configurable

    public boolean isSuppressingSlidingDownLadder() {
        return this.isShiftKeyDown();
    }

    public boolean isFallFlying() {
        return this.getSharedFlag(7);
    }

    @Override
    public boolean isVisuallySwimming() {
        return super.isVisuallySwimming() || !this.isFallFlying() && this.hasPose(Pose.FALL_FLYING);
    }

    public int getFallFlyingTicks() {
        return this.fallFlyTicks;
    }

    public boolean randomTeleport(double x, double y, double z, boolean broadcastTeleport) {
        // CraftBukkit start
        return this.randomTeleport(x, y, z, broadcastTeleport, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN).orElse(false);
    }

    public Optional<Boolean> randomTeleport(double x, double y, double z, boolean broadcastTeleport, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        // CraftBukkit end
        double x1 = this.getX();
        double y1 = this.getY();
        double z1 = this.getZ();
        double d = y;
        boolean flag = false;
        BlockPos blockPos = BlockPos.containing(x, y, z);
        Level level = this.level();
        if (level.hasChunkAt(blockPos)) {
            boolean flag1 = false;

            while (!flag1 && blockPos.getY() > level.getMinY()) {
                BlockPos blockPos1 = blockPos.below();
                BlockState blockState = level.getBlockState(blockPos1);
                if (blockState.blocksMotion()) {
                    flag1 = true;
                } else {
                    d--;
                    blockPos = blockPos1;
                }
            }

            if (flag1) {
                // CraftBukkit start - Teleport event
                // first set position, to check if the place to teleport is valid
                this.setPos(x, d, z);
                if (level.noCollision(this) && !level.containsAnyLiquid(this.getBoundingBox())) {
                    flag = true;
                }
                // now revert and call event if the teleport place is valid
                this.setPos(x1, y1, z1);

                if (flag) {
                    if (!(this instanceof ServerPlayer)) {
                        EntityTeleportEvent teleport = new EntityTeleportEvent(this.getBukkitEntity(), new Location(this.level().getWorld(), x1, y1, z1), new Location(this.level().getWorld(), x, d, z));
                        this.level().getCraftServer().getPluginManager().callEvent(teleport);
                        if (!teleport.isCancelled() && teleport.getTo() != null) { // Paper
                            Location to = teleport.getTo();
                            this.teleportTo(to.getX(), to.getY(), to.getZ());
                        } else {
                            return Optional.empty();
                        }
                    } else {
                        // player teleport event is called in the underlining code
                        if (!((ServerPlayer) this).connection.teleport(x, d, z, this.getYRot(), this.getXRot(), cause)) {
                            return Optional.empty();
                        }
                    }
                }
                // CraftBukkit end
            }
        }

        if (!flag) {
            // this.teleportTo(x1, y1, z1); // CraftBukkit - already set the location back
            return Optional.of(false); // CraftBukkit
        } else {
            if (broadcastTeleport) {
                level.broadcastEntityEvent(this, (byte)46);
            }

            if (this instanceof PathfinderMob pathfinderMob) {
                pathfinderMob.getNavigation().stop();
            }

            return Optional.of(true); // CraftBukkit
        }
    }

    public boolean isAffectedByPotions() {
        return !this.isDeadOrDying();
    }

    public boolean attackable() {
        return true;
    }

    public void setRecordPlayingNearby(BlockPos jukebox, boolean partyParrot) {
    }

    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public final EntityDimensions getDimensions(Pose pose) {
        return pose == Pose.SLEEPING ? SLEEPING_DIMENSIONS : this.getDefaultDimensions(pose).scale(this.getScale());
    }

    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return this.getType().getDimensions().scale(this.getAgeScale());
    }

    public ImmutableList<Pose> getDismountPoses() {
        return ImmutableList.of(Pose.STANDING);
    }

    public AABB getLocalBoundsForPose(Pose pose) {
        EntityDimensions dimensions = this.getDimensions(pose);
        return new AABB(-dimensions.width() / 2.0F, 0.0, -dimensions.width() / 2.0F, dimensions.width() / 2.0F, dimensions.height(), dimensions.width() / 2.0F);
    }

    protected boolean wouldNotSuffocateAtTargetPose(Pose pose) {
        AABB aabb = this.getDimensions(pose).makeBoundingBox(this.position());
        return this.level().noBlockCollision(this, aabb);
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return super.canUsePortal(allowPassengers) && !this.isSleeping();
    }

    public Optional<BlockPos> getSleepingPos() {
        return this.entityData.get(SLEEPING_POS_ID);
    }

    public void setSleepingPos(BlockPos pos) {
        this.entityData.set(SLEEPING_POS_ID, Optional.of(pos));
    }

    public void clearSleepingPos() {
        this.entityData.set(SLEEPING_POS_ID, Optional.empty());
    }

    public boolean isSleeping() {
        return this.getSleepingPos().isPresent();
    }

    public void startSleeping(BlockPos pos) {
        if (this.isPassenger()) {
            this.stopRiding();
        }

        BlockState blockState = this.level().getBlockState(pos);
        if (blockState.getBlock() instanceof BedBlock) {
            this.level().setBlock(pos, blockState.setValue(BedBlock.OCCUPIED, Boolean.valueOf(true)), 3);
        }

        this.setPose(Pose.SLEEPING);
        this.setPosToBed(pos);
        this.setSleepingPos(pos);
        this.setDeltaMovement(Vec3.ZERO);
        this.hasImpulse = true;
    }

    private void setPosToBed(BlockPos pos) {
        this.setPos(pos.getX() + 0.5, pos.getY() + 0.6875, pos.getZ() + 0.5);
    }

    private boolean checkBedExists() {
        return this.getSleepingPos().map(pos -> this.level().getBlockState(pos).getBlock() instanceof BedBlock).orElse(false);
    }

    public void stopSleeping() {
        this.getSleepingPos().filter(this.level()::hasChunkAt).ifPresent(blockPos -> {
            BlockState blockState = this.level().getBlockState(blockPos);
            if (blockState.getBlock() instanceof BedBlock) {
                Direction direction = blockState.getValue(BedBlock.FACING);
                this.level().setBlock(blockPos, blockState.setValue(BedBlock.OCCUPIED, Boolean.valueOf(false)), 3);
                Vec3 vec31 = BedBlock.findStandUpPosition(this.getType(), this.level(), blockPos, direction, this.getYRot()).orElseGet(() -> {
                    BlockPos blockPos1 = blockPos.above();
                    return new Vec3(blockPos1.getX() + 0.5, blockPos1.getY() + 0.1, blockPos1.getZ() + 0.5);
                });
                Vec3 vec32 = Vec3.atBottomCenterOf(blockPos).subtract(vec31).normalize();
                float f = (float)Mth.wrapDegrees(Mth.atan2(vec32.z, vec32.x) * 180.0F / (float)Math.PI - 90.0);
                this.setPos(vec31.x, vec31.y, vec31.z);
                this.setYRot(f);
                this.setXRot(0.0F);
            }
        });
        Vec3 vec3 = this.position();
        this.setPose(Pose.STANDING);
        this.setPos(vec3.x, vec3.y, vec3.z);
        this.clearSleepingPos();
    }

    @Nullable
    public Direction getBedOrientation() {
        BlockPos blockPos = this.getSleepingPos().orElse(null);
        return blockPos != null ? BedBlock.getBedOrientation(this.level(), blockPos) : null;
    }

    @Override
    public boolean isInWall() {
        return !this.isSleeping() && super.isInWall();
    }

    public ItemStack getProjectile(ItemStack weaponStack) {
        return ItemStack.EMPTY;
    }

    public static byte entityEventForEquipmentBreak(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> 47;
            case OFFHAND -> 48;
            case HEAD -> 49;
            case CHEST -> 50;
            case FEET -> 52;
            case LEGS -> 51;
            case BODY -> 65;
        };
    }

    public void onEquippedItemBroken(Item item, EquipmentSlot slot) {
        this.level().broadcastEntityEvent(this, entityEventForEquipmentBreak(slot));
        this.stopLocationBasedEffects(this.getItemBySlot(slot), slot, this.attributes);
    }

    private void stopLocationBasedEffects(ItemStack stack, EquipmentSlot slot, AttributeMap attributeMap) {
        stack.forEachModifier(slot, (attribute, modifier) -> {
            AttributeInstance instance = attributeMap.getInstance(attribute);
            if (instance != null) {
                instance.removeModifier(modifier);
            }
        });
        EnchantmentHelper.stopLocationBasedEffects(stack, this, slot);
    }

    public static EquipmentSlot getSlotForHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }

    public final boolean canEquipWithDispenser(ItemStack stack) {
        if (this.isAlive() && !this.isSpectator()) {
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            if (equippable != null && equippable.dispensable()) {
                EquipmentSlot equipmentSlot = equippable.slot();
                return this.canUseSlot(equipmentSlot)
                    && equippable.canBeEquippedBy(this.getType())
                    && this.getItemBySlot(equipmentSlot).isEmpty()
                    && this.canDispenserEquipIntoSlot(equipmentSlot);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
        return true;
    }

    public final EquipmentSlot getEquipmentSlotForItem(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && this.canUseSlot(equippable.slot()) ? equippable.slot() : EquipmentSlot.MAINHAND;
    }

    public final boolean isEquippableInSlot(ItemStack stack, EquipmentSlot slot) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable == null
            ? slot == EquipmentSlot.MAINHAND && this.canUseSlot(EquipmentSlot.MAINHAND)
            : slot == equippable.slot() && this.canUseSlot(equippable.slot()) && equippable.canBeEquippedBy(this.getType());
    }

    // Purpur start - Dispenser curse of binding protection
    public @Nullable EquipmentSlot getEquipmentSlotForDispenserItem(ItemStack itemstack) {
        return EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.BINDING_CURSE, itemstack) > 0 ? null : this.getEquipmentSlotForItem(itemstack);
    }
    // Purpur end - Dispenser curse of binding protection

    private static SlotAccess createEquipmentSlotAccess(LivingEntity entity, EquipmentSlot slot) {
        return slot != EquipmentSlot.HEAD && slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND
            ? SlotAccess.forEquipmentSlot(entity, slot, stack -> stack.isEmpty() || entity.getEquipmentSlotForItem(stack) == slot)
            : SlotAccess.forEquipmentSlot(entity, slot);
    }

    @Nullable
    private static EquipmentSlot getEquipmentSlot(int index) {
        if (index == 100 + EquipmentSlot.HEAD.getIndex()) {
            return EquipmentSlot.HEAD;
        } else if (index == 100 + EquipmentSlot.CHEST.getIndex()) {
            return EquipmentSlot.CHEST;
        } else if (index == 100 + EquipmentSlot.LEGS.getIndex()) {
            return EquipmentSlot.LEGS;
        } else if (index == 100 + EquipmentSlot.FEET.getIndex()) {
            return EquipmentSlot.FEET;
        } else if (index == 98) {
            return EquipmentSlot.MAINHAND;
        } else if (index == 99) {
            return EquipmentSlot.OFFHAND;
        } else {
            return index == 105 ? EquipmentSlot.BODY : null;
        }
    }

    @Override
    public SlotAccess getSlot(int slot) {
        EquipmentSlot equipmentSlot = getEquipmentSlot(slot);
        return equipmentSlot != null ? createEquipmentSlotAccess(this, equipmentSlot) : super.getSlot(slot);
    }

    @Override
    public boolean canFreeze() {
        if (this.isSpectator()) {
            return false;
        } else {
            boolean flag = !this.getItemBySlot(EquipmentSlot.HEAD).is(ItemTags.FREEZE_IMMUNE_WEARABLES)
                && !this.getItemBySlot(EquipmentSlot.CHEST).is(ItemTags.FREEZE_IMMUNE_WEARABLES)
                && !this.getItemBySlot(EquipmentSlot.LEGS).is(ItemTags.FREEZE_IMMUNE_WEARABLES)
                && !this.getItemBySlot(EquipmentSlot.FEET).is(ItemTags.FREEZE_IMMUNE_WEARABLES)
                && !this.getItemBySlot(EquipmentSlot.BODY).is(ItemTags.FREEZE_IMMUNE_WEARABLES);
            return flag && super.canFreeze();
        }
    }

    @Override
    public boolean isCurrentlyGlowing() {
        return !this.level().isClientSide() && this.hasEffect(MobEffects.GLOWING) || super.isCurrentlyGlowing();
    }

    @Override
    public float getVisualRotationYInDegrees() {
        return this.yBodyRot;
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        double x = packet.getX();
        double y = packet.getY();
        double z = packet.getZ();
        float yRot = packet.getYRot();
        float xRot = packet.getXRot();
        this.syncPacketPositionCodec(x, y, z);
        this.yBodyRot = packet.getYHeadRot();
        this.yHeadRot = packet.getYHeadRot();
        this.yBodyRotO = this.yBodyRot;
        this.yHeadRotO = this.yHeadRot;
        this.setId(packet.getId());
        this.setUUID(packet.getUUID());
        this.absMoveTo(x, y, z, yRot, xRot);
        this.setDeltaMovement(packet.getXa(), packet.getYa(), packet.getZa());
    }

    public boolean canDisableShield() {
        return this.getWeaponItem().getItem() instanceof AxeItem;
    }

    @Override
    public float maxUpStep() {
        float f = (float)this.getAttributeValue(Attributes.STEP_HEIGHT);
        return this.getControllingPassenger() instanceof Player ? Math.max(f, 1.0F) : f;
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity entity) {
        return this.position().add(this.getPassengerAttachmentPoint(entity, this.getDimensions(this.getPose()), this.getScale() * this.getAgeScale()));
    }

    protected void lerpHeadRotationStep(int lerpHeadSteps, double lerpYHeadRot) {
        this.yHeadRot = (float)Mth.rotLerp(1.0 / lerpHeadSteps, (double)this.yHeadRot, lerpYHeadRot);
    }

    @Override
    public void igniteForTicks(int ticks) {
        super.igniteForTicks(Mth.ceil(ticks * this.getAttributeValue(Attributes.BURNING_TIME)));
    }

    public boolean hasInfiniteMaterials() {
        return false;
    }

    public boolean isInvulnerableTo(ServerLevel level, DamageSource damageSource) {
        return this.isInvulnerableToBase(damageSource) || EnchantmentHelper.isImmuneToDamage(level, this, damageSource);
    }

    public static boolean canGlideUsing(ItemStack stack, EquipmentSlot slot) {
        if (!stack.has(DataComponents.GLIDER)) {
            return false;
        } else {
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            return equippable != null && slot == equippable.slot() && !stack.nextDamageWillBreak();
        }
    }

    @VisibleForTesting
    public int getLastHurtByPlayerTime() {
        return this.lastHurtByPlayerTime;
    }

    public record Fallsounds(SoundEvent small, SoundEvent big) {
    }
}
