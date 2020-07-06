package net.minecraft.world.entity.player;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.math.IntMath;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.slf4j.Logger;

public abstract class Player extends LivingEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final HumanoidArm DEFAULT_MAIN_HAND = HumanoidArm.RIGHT;
    public static final int DEFAULT_MODEL_CUSTOMIZATION = 0;
    public static final int MAX_HEALTH = 20;
    public static final int SLEEP_DURATION = 100;
    public static final int WAKE_UP_DURATION = 10;
    public static final int ENDER_SLOT_OFFSET = 200;
    public static final int HELD_ITEM_SLOT = 499;
    public static final int CRAFTING_SLOT_OFFSET = 500;
    public static final float DEFAULT_BLOCK_INTERACTION_RANGE = 4.5F;
    public static final float DEFAULT_ENTITY_INTERACTION_RANGE = 3.0F;
    public static final float CROUCH_BB_HEIGHT = 1.5F;
    public static final float SWIMMING_BB_WIDTH = 0.6F;
    public static final float SWIMMING_BB_HEIGHT = 0.6F;
    public static final float DEFAULT_EYE_HEIGHT = 1.62F;
    private static final int CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME_TICKS = 40;
    public static final Vec3 DEFAULT_VEHICLE_ATTACHMENT = new Vec3(0.0, 0.6, 0.0);
    public static final EntityDimensions STANDING_DIMENSIONS = EntityDimensions.scalable(0.6F, 1.8F)
        .withEyeHeight(1.62F)
        .withAttachments(EntityAttachments.builder().attach(EntityAttachment.VEHICLE, DEFAULT_VEHICLE_ATTACHMENT));
    private static final Map<Pose, EntityDimensions> POSES = ImmutableMap.<Pose, EntityDimensions>builder()
        .put(Pose.STANDING, STANDING_DIMENSIONS)
        .put(Pose.SLEEPING, SLEEPING_DIMENSIONS)
        .put(Pose.FALL_FLYING, EntityDimensions.scalable(0.6F, 0.6F).withEyeHeight(0.4F))
        .put(Pose.SWIMMING, EntityDimensions.scalable(0.6F, 0.6F).withEyeHeight(0.4F))
        .put(Pose.SPIN_ATTACK, EntityDimensions.scalable(0.6F, 0.6F).withEyeHeight(0.4F))
        .put(
            Pose.CROUCHING,
            EntityDimensions.scalable(0.6F, 1.5F)
                .withEyeHeight(1.27F)
                .withAttachments(EntityAttachments.builder().attach(EntityAttachment.VEHICLE, DEFAULT_VEHICLE_ATTACHMENT))
        )
        .put(Pose.DYING, EntityDimensions.fixed(0.2F, 0.2F).withEyeHeight(1.62F))
        .build();
    private static final EntityDataAccessor<Float> DATA_PLAYER_ABSORPTION_ID = SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_SCORE_ID = SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Byte> DATA_PLAYER_MODE_CUSTOMISATION = SynchedEntityData.defineId(Player.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<Byte> DATA_PLAYER_MAIN_HAND = SynchedEntityData.defineId(Player.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<CompoundTag> DATA_SHOULDER_LEFT = SynchedEntityData.defineId(Player.class, EntityDataSerializers.COMPOUND_TAG);
    protected static final EntityDataAccessor<CompoundTag> DATA_SHOULDER_RIGHT = SynchedEntityData.defineId(Player.class, EntityDataSerializers.COMPOUND_TAG);
    public static final int CLIENT_LOADED_TIMEOUT_TIME = 60;
    private long timeEntitySatOnShoulder;
    final Inventory inventory = new Inventory(this);
    protected PlayerEnderChestContainer enderChestInventory = new PlayerEnderChestContainer(this); // CraftBukkit - add "this" to constructor
    public final InventoryMenu inventoryMenu;
    public AbstractContainerMenu containerMenu;
    protected FoodData foodData = new FoodData();
    protected int jumpTriggerTime;
    private boolean clientLoaded = false;
    protected int clientLoadedTimeoutTimer = 60;
    public float oBob;
    public float bob;
    public int takeXpDelay;
    public double xCloakO;
    public double yCloakO;
    public double zCloakO;
    public double xCloak;
    public double yCloak;
    public double zCloak;
    public int sleepCounter;
    protected boolean wasUnderwater;
    private final Abilities abilities = new Abilities();
    public int experienceLevel;
    public int totalExperience;
    public float experienceProgress;
    public int enchantmentSeed;
    protected final float defaultFlySpeed = 0.02F;
    private int lastLevelUpTime;
    public GameProfile gameProfile;
    private boolean reducedDebugInfo;
    private ItemStack lastItemInMainHand = ItemStack.EMPTY;
    private final ItemCooldowns cooldowns = this.createItemCooldowns();
    private Optional<GlobalPos> lastDeathLocation = Optional.empty();
    @Nullable
    public FishingHook fishing;
    public float hurtDir; // Paper - protected -> public
    @Nullable
    public Vec3 currentImpulseImpactPos;
    @Nullable
    public Entity currentExplosionCause;
    private boolean ignoreFallDamageFromCurrentImpulse;
    private int currentImpulseContextResetGraceTime;
    public boolean affectsSpawning = true; // Paper - Affects Spawning API
    public net.kyori.adventure.util.TriState flyingFallDamage = net.kyori.adventure.util.TriState.NOT_SET; // Paper - flying fall damage
    public int burpDelay = 0; // Purpur - Burp delay
    public boolean canPortalInstant = false; // Purpur - Add portal permission bypass

    // CraftBukkit start
    public boolean fauxSleeping;
    public int oldLevel = -1;

    // Purpur start - AFK API
    public abstract void setAfk(boolean afk);

    public boolean isAfk() {
        return false;
    }
    // Purpur end - AFK API
    @Override
    public org.bukkit.craftbukkit.entity.CraftHumanEntity getBukkitEntity() {
        return (org.bukkit.craftbukkit.entity.CraftHumanEntity) super.getBukkitEntity();
    }
    // CraftBukkit end

    // Purpur start - Ridables
    public abstract void resetLastActionTime();

    @Override
    public boolean processClick(InteractionHand hand) {
        Entity vehicle = getRootVehicle();
        if (vehicle != null && vehicle.getRider() == this) {
            return vehicle.onClick(hand);
        }
        return false;
    }
    // Purpur end - Ridables

    public Player(Level level, BlockPos pos, float yRot, GameProfile gameProfile) {
        super(EntityType.PLAYER, level);
        this.setUUID(gameProfile.getId());
        this.gameProfile = gameProfile;
        this.inventoryMenu = new InventoryMenu(this.inventory, !level.isClientSide, this);
        this.containerMenu = this.inventoryMenu;
        this.moveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, yRot, 0.0F);
        this.rotOffs = 180.0F;
    }

    public boolean blockActionRestricted(Level level, BlockPos pos, GameType gameMode) {
        if (!gameMode.isBlockPlacingRestricted()) {
            return false;
        } else if (gameMode == GameType.SPECTATOR) {
            return true;
        } else if (this.mayBuild()) {
            return false;
        } else {
            ItemStack mainHandItem = this.getMainHandItem();
            return mainHandItem.isEmpty() || !mainHandItem.canBreakBlockInAdventureMode(new BlockInWorld(level, pos, false));
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
            .add(Attributes.ATTACK_DAMAGE, 1.0)
            .add(Attributes.MOVEMENT_SPEED, 0.1F)
            .add(Attributes.ATTACK_SPEED)
            .add(Attributes.LUCK)
            .add(Attributes.BLOCK_INTERACTION_RANGE, 4.5)
            .add(Attributes.ENTITY_INTERACTION_RANGE, 3.0)
            .add(Attributes.BLOCK_BREAK_SPEED)
            .add(Attributes.SUBMERGED_MINING_SPEED)
            .add(Attributes.SNEAKING_SPEED)
            .add(Attributes.MINING_EFFICIENCY)
            .add(Attributes.SWEEPING_DAMAGE_RATIO);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PLAYER_ABSORPTION_ID, 0.0F);
        builder.define(DATA_SCORE_ID, 0);
        builder.define(DATA_PLAYER_MODE_CUSTOMISATION, (byte)0);
        builder.define(DATA_PLAYER_MAIN_HAND, (byte)DEFAULT_MAIN_HAND.getId());
        builder.define(DATA_SHOULDER_LEFT, new CompoundTag());
        builder.define(DATA_SHOULDER_RIGHT, new CompoundTag());
    }

    @Override
    public void tick() {
        // Purpur start - Burp delay
        if (this.burpDelay > 0 && --this.burpDelay == 0) {
            this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.0F, this.level().random.nextFloat() * 0.1F + 0.9F);
        }
        // Purpur end - Burp delay

        this.noPhysics = this.isSpectator();
        if (this.isSpectator() || this.isPassenger()) {
            this.setOnGround(false);
        }

        if (this.takeXpDelay > 0) {
            this.takeXpDelay--;
        }

        if (this.isSleeping()) {
            this.sleepCounter++;
            // Paper start - Add PlayerDeepSleepEvent
            if (this.sleepCounter == SLEEP_DURATION) {
                if (!new io.papermc.paper.event.player.PlayerDeepSleepEvent((org.bukkit.entity.Player) getBukkitEntity()).callEvent()) {
                    this.sleepCounter = Integer.MIN_VALUE;
                }
            }
            // Paper end - Add PlayerDeepSleepEvent
            if (this.sleepCounter > 100) {
                this.sleepCounter = 100;
            }

            if (!this.level().isClientSide && this.level().isDay()) {
                this.stopSleepInBed(false, true);
            }
        } else if (this.sleepCounter > 0) {
            this.sleepCounter++;
            if (this.sleepCounter >= 110) {
                this.sleepCounter = 0;
            }
        }

        this.updateIsUnderwater();
        super.tick();
        if (!this.level().isClientSide && this.containerMenu != null && !this.containerMenu.stillValid(this)) {
            this.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.CANT_USE); // Paper - Inventory close reason
            this.containerMenu = this.inventoryMenu;
        }

        this.moveCloak();
        if (this instanceof ServerPlayer serverPlayer) {
            this.foodData.tick(serverPlayer);
            this.awardStat(Stats.PLAY_TIME);
            this.awardStat(Stats.TOTAL_WORLD_TIME);
            if (this.isAlive()) {
                this.awardStat(Stats.TIME_SINCE_DEATH);
            }

            if (this.isDiscrete()) {
                this.awardStat(Stats.CROUCH_TIME);
            }

            if (!this.isSleeping()) {
                this.awardStat(Stats.TIME_SINCE_REST);
            }
        }

        int i = 29999999;
        double d = Mth.clamp(this.getX(), -2.9999999E7, 2.9999999E7);
        double d1 = Mth.clamp(this.getZ(), -2.9999999E7, 2.9999999E7);
        if (d != this.getX() || d1 != this.getZ()) {
            this.setPos(d, this.getY(), d1);
        }

        this.attackStrengthTicker++;
        ItemStack mainHandItem = this.getMainHandItem();
        if (!ItemStack.matches(this.lastItemInMainHand, mainHandItem)) {
            if (!ItemStack.isSameItem(this.lastItemInMainHand, mainHandItem)) {
                this.resetAttackStrengthTicker();
            }

            this.lastItemInMainHand = mainHandItem.copy();
        }

        if (!this.isEyeInFluid(FluidTags.WATER) && this.isEquipped(Items.TURTLE_HELMET)) {
            this.turtleHelmetTick();
        }

        // Purpur start - Full netherite armor grants fire resistance
        if (this.level().purpurConfig.playerNetheriteFireResistanceDuration > 0 && this.level().getGameTime() % 20 == 0) {
            if (this.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET)
                && this.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE)
                && this.getItemBySlot(EquipmentSlot.LEGS).is(Items.NETHERITE_LEGGINGS)
                && this.getItemBySlot(EquipmentSlot.FEET).is(Items.NETHERITE_BOOTS)) {
                this.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, this.level().purpurConfig.playerNetheriteFireResistanceDuration, this.level().purpurConfig.playerNetheriteFireResistanceAmplifier, this.level().purpurConfig.playerNetheriteFireResistanceAmbient, this.level().purpurConfig.playerNetheriteFireResistanceShowParticles, this.level().purpurConfig.playerNetheriteFireResistanceShowIcon), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.NETHERITE_ARMOR);
            }
        }
        // Purpur end - Full netherite armor grants fire resistance

        this.cooldowns.tick();
        this.updatePlayerPose();
        if (this.currentImpulseContextResetGraceTime > 0) {
            this.currentImpulseContextResetGraceTime--;
        }
    }

    @Override
    protected float getMaxHeadRotationRelativeToBody() {
        return this.isBlocking() ? 15.0F : super.getMaxHeadRotationRelativeToBody();
    }

    public boolean isSecondaryUseActive() {
        return this.isShiftKeyDown();
    }

    protected boolean wantsToStopRiding() {
        return this.isShiftKeyDown();
    }

    protected boolean isStayingOnGroundSurface() {
        return this.isShiftKeyDown();
    }

    protected boolean updateIsUnderwater() {
        this.wasUnderwater = this.isEyeInFluid(FluidTags.WATER);
        return this.wasUnderwater;
    }

    @Override
    public void onAboveBubbleCol(boolean downwards) {
        if (!this.getAbilities().flying) {
            super.onAboveBubbleCol(downwards);
        }
    }

    @Override
    public void onInsideBubbleColumn(boolean downwards) {
        if (!this.getAbilities().flying) {
            super.onInsideBubbleColumn(downwards);
        }
    }

    private void turtleHelmetTick() {
        this.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200, 0, false, false, true), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.TURTLE_HELMET); // CraftBukkit
    }

    private boolean isEquipped(Item item) {
        for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
            ItemStack itemBySlot = this.getItemBySlot(equipmentSlot);
            Equippable equippable = itemBySlot.get(DataComponents.EQUIPPABLE);
            if (itemBySlot.is(item) && equippable != null && equippable.slot() == equipmentSlot) {
                return true;
            }
        }

        return false;
    }

    protected ItemCooldowns createItemCooldowns() {
        return new ItemCooldowns();
    }

    private void moveCloak() {
        this.xCloakO = this.xCloak;
        this.yCloakO = this.yCloak;
        this.zCloakO = this.zCloak;
        double d = this.getX() - this.xCloak;
        double d1 = this.getY() - this.yCloak;
        double d2 = this.getZ() - this.zCloak;
        double d3 = 10.0;
        if (d > 10.0) {
            this.xCloak = this.getX();
            this.xCloakO = this.xCloak;
        }

        if (d2 > 10.0) {
            this.zCloak = this.getZ();
            this.zCloakO = this.zCloak;
        }

        if (d1 > 10.0) {
            this.yCloak = this.getY();
            this.yCloakO = this.yCloak;
        }

        if (d < -10.0) {
            this.xCloak = this.getX();
            this.xCloakO = this.xCloak;
        }

        if (d2 < -10.0) {
            this.zCloak = this.getZ();
            this.zCloakO = this.zCloak;
        }

        if (d1 < -10.0) {
            this.yCloak = this.getY();
            this.yCloakO = this.yCloak;
        }

        this.xCloak += d * 0.25;
        this.zCloak += d2 * 0.25;
        this.yCloak += d1 * 0.25;
    }

    protected void updatePlayerPose() {
        if (this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.SWIMMING)) {
            Pose pose;
            if (this.isFallFlying()) {
                pose = Pose.FALL_FLYING;
            } else if (this.isSleeping()) {
                pose = Pose.SLEEPING;
            } else if (this.isSwimming()) {
                pose = Pose.SWIMMING;
            } else if (this.isAutoSpinAttack()) {
                pose = Pose.SPIN_ATTACK;
            } else if (this.isShiftKeyDown() && !this.abilities.flying) {
                pose = Pose.CROUCHING;
            } else {
                pose = Pose.STANDING;
            }

            Pose pose1;
            if (this.isSpectator() || this.isPassenger() || this.canPlayerFitWithinBlocksAndEntitiesWhen(pose)) {
                pose1 = pose;
            } else if (this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.CROUCHING)) {
                pose1 = Pose.CROUCHING;
            } else {
                pose1 = Pose.SWIMMING;
            }

            this.setPose(pose1);
        }
    }

    protected boolean canPlayerFitWithinBlocksAndEntitiesWhen(Pose pose) {
        return this.level().noCollision(this, this.getDimensions(pose).makeBoundingBox(this.position()).deflate(1.0E-7));
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.PLAYER_SWIM;
    }

    @Override
    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.PLAYER_SPLASH;
    }

    @Override
    protected SoundEvent getSwimHighSpeedSplashSound() {
        return SoundEvents.PLAYER_SPLASH_HIGH_SPEED;
    }

    @Override
    public int getDimensionChangingDelay() {
        return 10;
    }

    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        this.level().playSound(this, this.getX(), this.getY(), this.getZ(), sound, this.getSoundSource(), volume, pitch);
    }

    public void playNotifySound(SoundEvent sound, SoundSource source, float volume, float pitch) {
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.PLAYERS;
    }

    @Override
    public int getFireImmuneTicks() {
        return 20;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 9) {
            this.completeUsingItem();
        } else if (id == 23) {
            this.reducedDebugInfo = false;
        } else if (id == 22) {
            this.reducedDebugInfo = true;
        } else {
            super.handleEntityEvent(id);
        }
    }

    // Paper start - Inventory close reason; unused code, but to keep signatures aligned
    public void closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) {
        this.closeContainer();
        this.containerMenu = this.inventoryMenu;
    }
    // Paper end - Inventory close reason
    // Paper start - special close for unloaded inventory
    public void closeUnloadedInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) {
        this.containerMenu = this.inventoryMenu;
    }
    // Paper end - special close for unloaded inventory

    public void closeContainer() {
        this.containerMenu = this.inventoryMenu;
    }

    protected void doCloseContainer() {
    }

    @Override
    public void rideTick() {
        if (!this.level().isClientSide && this.wantsToStopRiding() && this.isPassenger()) {
            this.stopRiding();
            // CraftBukkit start - SPIGOT-7316: no longer passenger, dismount and return
            if (!this.isPassenger()) {
                this.setShiftKeyDown(false);
                return;
            }
        }
        {
            // CraftBukkit end
            super.rideTick();
            this.oBob = this.bob;
            this.bob = 0.0F;
        }
    }

    @Override
    protected void serverAiStep() {
        super.serverAiStep();
        this.updateSwingTime();
        this.yHeadRot = this.getYRot();
    }

    @Override
    public void aiStep() {
        if (this.jumpTriggerTime > 0) {
            this.jumpTriggerTime--;
        }

        this.tickRegeneration();
        this.inventory.tick();
        this.oBob = this.bob;
        if (this.abilities.flying && !this.isPassenger()) {
            this.resetFallDistance();
        }

        super.aiStep();
        this.setSpeed((float)this.getAttributeValue(Attributes.MOVEMENT_SPEED));
        float f;
        if (this.onGround() && !this.isDeadOrDying() && !this.isSwimming()) {
            f = Math.min(0.1F, (float)this.getDeltaMovement().horizontalDistance());
        } else {
            f = 0.0F;
        }

        this.bob = this.bob + (f - this.bob) * 0.4F;
        if (this.getHealth() > 0.0F && !this.isSpectator()) {
            AABB aabb;
            if (this.isPassenger() && !this.getVehicle().isRemoved()) {
                aabb = this.getBoundingBox().minmax(this.getVehicle().getBoundingBox()).inflate(1.0, 0.0, 1.0);
            } else {
                aabb = this.getBoundingBox().inflate(1.0, 0.5, 1.0);
            }

            List<Entity> entities = this.level().getEntities(this, aabb);
            List<Entity> list = Lists.newArrayList();

            for (Entity entity : entities) {
                if (entity.getType() == EntityType.EXPERIENCE_ORB && entity.level().purpurConfig.playerExpPickupDelay >= 0) { // Purpur - Configurable player pickup exp delay
                    list.add(entity);
                } else if (!entity.isRemoved()) {
                    this.touch(entity);
                }
            }

            if (!list.isEmpty()) {
                this.touch(Util.getRandom(list, this.random));
            }
        }

        this.playShoulderEntityAmbientSound(this.getShoulderEntityLeft());
        this.playShoulderEntityAmbientSound(this.getShoulderEntityRight());
        if (!this.level().isClientSide && (this.fallDistance > 0.5F || this.isInWater()) || this.abilities.flying || this.isSleeping() || this.isInPowderSnow) {
            if (!this.level().paperConfig().entities.behavior.parrotsAreUnaffectedByPlayerMovement) // Paper - Add option to make parrots stay
            this.removeEntitiesOnShoulder();
        }
    }

    protected void tickRegeneration() {
    }

    private void playShoulderEntityAmbientSound(@Nullable CompoundTag entityCompound) {
        if (entityCompound != null && (!entityCompound.contains("Silent") || !entityCompound.getBoolean("Silent")) && this.level().random.nextInt(200) == 0) {
            String string = entityCompound.getString("id");
            EntityType.byString(string)
                .filter(entityType -> entityType == EntityType.PARROT)
                .ifPresent(
                    entityType -> {
                        if (!Parrot.imitateNearbyMobs(this.level(), this)) {
                            this.level()
                                .playSound(
                                    null,
                                    this.getX(),
                                    this.getY(),
                                    this.getZ(),
                                    Parrot.getAmbient(this.level(), this.level().random),
                                    this.getSoundSource(),
                                    1.0F,
                                    Parrot.getPitch(this.level().random)
                                );
                        }
                    }
                );
        }
    }

    private void touch(Entity entity) {
        entity.playerTouch(this);
    }

    public int getScore() {
        return this.entityData.get(DATA_SCORE_ID);
    }

    public void setScore(int score) {
        this.entityData.set(DATA_SCORE_ID, score);
    }

    public void increaseScore(int score) {
        int score1 = this.getScore();
        this.entityData.set(DATA_SCORE_ID, score1 + score);
    }

    public void startAutoSpinAttack(int ticks, float damage, ItemStack itemStack) {
        this.autoSpinAttackTicks = ticks;
        this.autoSpinAttackDmg = damage;
        this.autoSpinAttackItemStack = itemStack;
        if (!this.level().isClientSide) {
            this.removeEntitiesOnShoulder();
            this.setLivingEntityFlag(4, true);
        }
    }

    @Nonnull
    @Override
    public ItemStack getWeaponItem() {
        return this.isAutoSpinAttack() && this.autoSpinAttackItemStack != null ? this.autoSpinAttackItemStack : super.getWeaponItem();
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        this.reapplyPosition();
        if (!this.isSpectator() && this.level() instanceof ServerLevel serverLevel) {
            this.dropAllDeathLoot(serverLevel, cause);
        }

        if (cause != null) {
            this.setDeltaMovement(
                -Mth.cos((this.getHurtDir() + this.getYRot()) * (float) (Math.PI / 180.0)) * 0.1F,
                0.1F,
                -Mth.sin((this.getHurtDir() + this.getYRot()) * (float) (Math.PI / 180.0)) * 0.1F
            );
        } else {
            this.setDeltaMovement(0.0, 0.1, 0.0);
        }

        this.awardStat(Stats.DEATHS);
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        this.clearFire();
        this.setSharedFlagOnFire(false);
        this.setLastDeathLocation(Optional.of(GlobalPos.of(this.level().dimension(), this.blockPosition())));
    }

    @Override
    protected void dropEquipment(ServerLevel level) {
        super.dropEquipment(level);
        if (!level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            this.destroyVanishingCursedItems();
            this.inventory.dropAll();
        }
    }

    protected void destroyVanishingCursedItems() {
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack item = this.inventory.getItem(i);
            if (!item.isEmpty() && EnchantmentHelper.has(item, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                this.inventory.removeItemNoUpdate(i);
            }
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return damageSource.type().effects().sound();
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    public void handleCreativeModeItemDrop(ItemStack stack) {
    }

    @Nullable
    public ItemEntity drop(ItemStack itemStack, boolean includeThrowerName) {
        return this.drop(itemStack, false, includeThrowerName);
    }

    @Nullable
    public ItemEntity drop(ItemStack droppedItem, boolean dropAround, boolean includeThrowerName) {
        // CraftBukkit start - SPIGOT-2942: Add boolean to call event
        return this.drop(droppedItem, dropAround, includeThrowerName, true, null);
    }

    @Nullable
    public ItemEntity drop(ItemStack droppedItem, boolean dropAround, boolean includeThrowerName, boolean callEvent) {
        return this.drop(droppedItem, dropAround, includeThrowerName, callEvent, null);
    }

    @Nullable
    public ItemEntity drop(ItemStack droppedItem, boolean dropAround, boolean includeThrowerName, boolean callEvent, @Nullable java.util.function.Consumer<org.bukkit.entity.Item> entityOperation) {
        // CraftBukkit end
        if (!droppedItem.isEmpty() && this.level().isClientSide) {
            this.swing(InteractionHand.MAIN_HAND);
        }

        return null;
    }

    public float getDestroySpeed(BlockState state) {
        float destroySpeed = this.inventory.getDestroySpeed(state);
        if (destroySpeed > 1.0F) {
            destroySpeed += (float)this.getAttributeValue(Attributes.MINING_EFFICIENCY);
        }

        if (MobEffectUtil.hasDigSpeed(this)) {
            destroySpeed *= 1.0F + (MobEffectUtil.getDigSpeedAmplification(this) + 1) * 0.2F;
        }

        if (this.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            float f = switch (this.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 8.1E-4F;
            };
            destroySpeed *= f;
        }

        destroySpeed *= (float)this.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
        if (this.isEyeInFluid(FluidTags.WATER)) {
            destroySpeed *= (float)this.getAttribute(Attributes.SUBMERGED_MINING_SPEED).getValue();
        }

        if (!this.onGround()) {
            destroySpeed /= 5.0F;
        }

        return destroySpeed;
    }

    public boolean hasCorrectToolForDrops(BlockState state) {
        return !state.requiresCorrectToolForDrops() || this.inventory.getSelected().isCorrectToolForDrops(state);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setUUID(this.gameProfile.getId());
        ListTag list = compound.getList("Inventory", 10);
        this.inventory.load(list);
        this.inventory.selected = compound.getInt("SelectedItemSlot");
        this.sleepCounter = compound.getShort("SleepTimer");
        this.experienceProgress = compound.getFloat("XpP");
        this.experienceLevel = compound.getInt("XpLevel");
        this.totalExperience = compound.getInt("XpTotal");
        this.enchantmentSeed = compound.getInt("XpSeed");
        if (this.enchantmentSeed == 0) {
            this.enchantmentSeed = this.random.nextInt();
        }

        this.setScore(compound.getInt("Score"));
        this.foodData.readAdditionalSaveData(compound);
        this.abilities.loadSaveData(compound);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(this.abilities.getWalkingSpeed());
        if (compound.contains("EnderItems", 9)) {
            this.enderChestInventory.fromTag(compound.getList("EnderItems", 10), this.registryAccess());
        }

        if (compound.contains("ShoulderEntityLeft", 10)) {
            this.setShoulderEntityLeft(compound.getCompound("ShoulderEntityLeft"));
        }

        if (compound.contains("ShoulderEntityRight", 10)) {
            this.setShoulderEntityRight(compound.getCompound("ShoulderEntityRight"));
        }

        if (compound.contains("LastDeathLocation", 10)) {
            this.setLastDeathLocation(GlobalPos.CODEC.parse(NbtOps.INSTANCE, compound.get("LastDeathLocation")).resultOrPartial(LOGGER::error));
        }

        if (compound.contains("current_explosion_impact_pos", 9)) {
            Vec3.CODEC
                .parse(NbtOps.INSTANCE, compound.get("current_explosion_impact_pos"))
                .resultOrPartial(LOGGER::error)
                .ifPresent(currentImpulseImpactPos -> this.currentImpulseImpactPos = currentImpulseImpactPos);
        }

        this.ignoreFallDamageFromCurrentImpulse = compound.getBoolean("ignore_fall_damage_from_current_explosion");
        this.currentImpulseContextResetGraceTime = compound.getInt("current_impulse_context_reset_grace_time");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        NbtUtils.addCurrentDataVersion(compound);
        compound.put("Inventory", this.inventory.save(new ListTag()));
        compound.putInt("SelectedItemSlot", this.inventory.selected);
        compound.putShort("SleepTimer", (short)this.sleepCounter);
        compound.putFloat("XpP", this.experienceProgress);
        compound.putInt("XpLevel", this.experienceLevel);
        compound.putInt("XpTotal", this.totalExperience);
        compound.putInt("XpSeed", this.enchantmentSeed);
        compound.putInt("Score", this.getScore());
        this.foodData.addAdditionalSaveData(compound);
        this.abilities.addSaveData(compound);
        compound.put("EnderItems", this.enderChestInventory.createTag(this.registryAccess()));
        if (!this.getShoulderEntityLeft().isEmpty()) {
            compound.put("ShoulderEntityLeft", this.getShoulderEntityLeft());
        }

        if (!this.getShoulderEntityRight().isEmpty()) {
            compound.put("ShoulderEntityRight", this.getShoulderEntityRight());
        }

        this.getLastDeathLocation()
            .flatMap(pos -> GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, pos).resultOrPartial(LOGGER::error))
            .ifPresent(tag -> compound.put("LastDeathLocation", tag));
        if (this.currentImpulseImpactPos != null) {
            compound.put("current_explosion_impact_pos", Vec3.CODEC.encodeStart(NbtOps.INSTANCE, this.currentImpulseImpactPos).getOrThrow());
        }

        compound.putBoolean("ignore_fall_damage_from_current_explosion", this.ignoreFallDamageFromCurrentImpulse);
        compound.putInt("current_impulse_context_reset_grace_time", this.currentImpulseContextResetGraceTime);
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource damageSource) {
        if (super.isInvulnerableTo(level, damageSource)) {
            return true;
        } else if (damageSource.is(DamageTypeTags.IS_DROWNING)) {
            return !level.getGameRules().getBoolean(GameRules.RULE_DROWNING_DAMAGE);
        } else if (damageSource.is(DamageTypeTags.IS_FALL)) {
            return !level.getGameRules().getBoolean(GameRules.RULE_FALL_DAMAGE);
        } else {
            return damageSource.is(DamageTypeTags.IS_FIRE)
                ? !level.getGameRules().getBoolean(GameRules.RULE_FIRE_DAMAGE)
                : damageSource.is(DamageTypeTags.IS_FREEZING) && !level.getGameRules().getBoolean(GameRules.RULE_FREEZE_DAMAGE);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableTo(level, damageSource)) {
            return false;
        } else if (this.abilities.invulnerable && !damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        } else {
            this.noActionTime = 0;
            if (this.isDeadOrDying()) {
                return false;
            } else {
                // this.removeEntitiesOnShoulder(); // CraftBukkit - moved down
                if (damageSource.scalesWithDifficulty()) {
                    if (level.getDifficulty() == Difficulty.PEACEFUL) {
                        return false; // CraftBukkit - f = 0.0f -> return false
                    }

                    if (level.getDifficulty() == Difficulty.EASY) {
                        amount = Math.min(amount / 2.0F + 1.0F, amount);
                    }

                    if (level.getDifficulty() == Difficulty.HARD) {
                        amount = amount * 3.0F / 2.0F;
                    }
                }

                // return amount != 0.0F && super.hurtServer(level, damageSource, amount);
                // CraftBukkit start - Don't filter out 0 damage
                boolean damaged = super.hurtServer(level, damageSource, amount);
                if (damaged) {
                    this.removeEntitiesOnShoulder();
                }
                return damaged;
                // CraftBukkit end
            }
        }
    }

    @Override
    protected void blockUsingShield(LivingEntity entity) {
        super.blockUsingShield(entity);
        ItemStack itemBlockingWith = this.getItemBlockingWith();
        if (entity.canDisableShield() && itemBlockingWith != null) {
            this.disableShield(itemBlockingWith, entity); // Paper - Add PlayerShieldDisableEvent
        }
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !this.getAbilities().invulnerable && super.canBeSeenAsEnemy();
    }

    public boolean canHarmPlayer(Player other) {
        // CraftBukkit start - Change to check OTHER player's scoreboard team according to API
        // To summarize this method's logic, it's "Can parameter hurt this"
        org.bukkit.scoreboard.Team team;
        if (other instanceof ServerPlayer) {
            ServerPlayer thatPlayer = (ServerPlayer) other;
            team = thatPlayer.getBukkitEntity().getScoreboard().getPlayerTeam(thatPlayer.getBukkitEntity());
            if (team == null || team.allowFriendlyFire()) {
                return true;
            }
        } else {
            // This should never be called, but is implemented anyway
            org.bukkit.OfflinePlayer thisPlayer = other.level().getCraftServer().getOfflinePlayer(other.getScoreboardName());
            team = other.level().getCraftServer().getScoreboardManager().getMainScoreboard().getPlayerTeam(thisPlayer);
            if (team == null || team.allowFriendlyFire()) {
                return true;
            }
        }

        if (this instanceof ServerPlayer) {
            return !team.hasPlayer(((ServerPlayer) this).getBukkitEntity());
        }
        return !team.hasPlayer(this.level().getCraftServer().getOfflinePlayer(this.getScoreboardName()));
        // CraftBukkit end
    }

    @Override
    protected void hurtArmor(DamageSource damageSource, float damage) {
        this.doHurtEquipment(damageSource, damage, new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD});
    }

    @Override
    protected void hurtHelmet(DamageSource damageSource, float damageAmount) {
        this.doHurtEquipment(damageSource, damageAmount, new EquipmentSlot[]{EquipmentSlot.HEAD});
    }

    @Override
    protected void hurtCurrentlyUsedShield(float damage) {
        if (this.useItem.is(Items.SHIELD)) {
            if (!this.level().isClientSide) {
                this.awardStat(Stats.ITEM_USED.get(this.useItem.getItem()));
            }

            if (damage >= 3.0F) {
                int i = 1 + Mth.floor(damage);
                InteractionHand usedItemHand = this.getUsedItemHand();
                this.useItem.hurtAndBreak(i, this, getSlotForHand(usedItemHand));
                if (this.useItem.isEmpty()) {
                    if (usedItemHand == InteractionHand.MAIN_HAND) {
                        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    } else {
                        this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                    }

                    this.useItem = ItemStack.EMPTY;
                    this.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + this.level().random.nextFloat() * 0.4F);
                }
            }
        }
    }

    @Override
    // CraftBukkit start
    protected boolean actuallyHurt(ServerLevel level, DamageSource damageSource, float amount, org.bukkit.event.entity.EntityDamageEvent event) { // void -> boolean
        if (true) {
            return super.actuallyHurt(level, damageSource, amount, event);
        }
        // CraftBukkit end
        if (!this.isInvulnerableTo(level, damageSource)) {
            amount = this.getDamageAfterArmorAbsorb(damageSource, amount);
            amount = this.getDamageAfterMagicAbsorb(damageSource, amount);
            float var8 = Math.max(amount - this.getAbsorptionAmount(), 0.0F);
            this.setAbsorptionAmount(this.getAbsorptionAmount() - (amount - var8));
            float f1 = amount - var8;
            if (f1 > 0.0F && f1 < 3.4028235E37F) {
                this.awardStat(Stats.DAMAGE_ABSORBED, Math.round(f1 * 10.0F));
            }

            if (var8 != 0.0F) {
                this.causeFoodExhaustion(damageSource.getFoodExhaustion(), org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.DAMAGED); // CraftBukkit - EntityExhaustionEvent
                this.getCombatTracker().recordDamage(damageSource, var8);
                this.setHealth(this.getHealth() - var8);
                if (var8 < 3.4028235E37F) {
                    this.awardStat(Stats.DAMAGE_TAKEN, Math.round(var8 * 10.0F));
                }

                this.gameEvent(GameEvent.ENTITY_DAMAGE);
            }
        }
        return false; // CraftBukkit
    }

    public boolean isTextFilteringEnabled() {
        return false;
    }

    public void openTextEdit(SignBlockEntity signEntity, boolean isFrontText) {
    }

    public void openMinecartCommandBlock(BaseCommandBlock commandEntity) {
    }

    public void openCommandBlock(CommandBlockEntity commandBlockEntity) {
    }

    public void openStructureBlock(StructureBlockEntity structureEntity) {
    }

    public void openJigsawBlock(JigsawBlockEntity jigsawBlockEntity) {
    }

    public void openHorseInventory(AbstractHorse horse, Container inventory) {
    }

    public OptionalInt openMenu(@Nullable MenuProvider menu) {
        return OptionalInt.empty();
    }

    public void sendMerchantOffers(int containerId, MerchantOffers offers, int villagerLevel, int villagerXp, boolean showProgress, boolean canRestock) {
    }

    public void openItemGui(ItemStack stack, InteractionHand hand) {
    }

    public InteractionResult interactOn(Entity entityToInteractOn, InteractionHand hand) {
        if (this.isSpectator()) {
            if (entityToInteractOn instanceof MenuProvider) {
                this.openMenu((MenuProvider)entityToInteractOn);
            }

            return InteractionResult.PASS;
        } else {
            ItemStack itemInHand = this.getItemInHand(hand);
            ItemStack itemStack = itemInHand.copy();
            InteractionResult interactionResult = entityToInteractOn.interact(this, hand);
            if (interactionResult.consumesAction()) {
                if (this.abilities.instabuild && itemInHand == this.getItemInHand(hand) && itemInHand.getCount() < itemStack.getCount()) {
                    itemInHand.setCount(itemStack.getCount());
                }

                return interactionResult;
            } else {
                if (!itemInHand.isEmpty() && entityToInteractOn instanceof LivingEntity) {
                    if (this.abilities.instabuild) {
                        itemInHand = itemStack;
                    }

                    InteractionResult interactionResult1 = itemInHand.interactLivingEntity(this, (LivingEntity)entityToInteractOn, hand);
                    if (interactionResult1.consumesAction()) {
                        this.level().gameEvent(GameEvent.ENTITY_INTERACT, entityToInteractOn.position(), GameEvent.Context.of(this));
                        if (itemInHand.isEmpty() && !this.abilities.instabuild) {
                            this.setItemInHand(hand, ItemStack.EMPTY);
                        }

                        return interactionResult1;
                    }
                }

                return InteractionResult.PASS;
            }
        }
    }

    @Override
    public void removeVehicle() {
        // Paper start - Force entity dismount during teleportation
        this.removeVehicle(false);
    }
    @Override
    public void removeVehicle(boolean suppressCancellation) {
        super.removeVehicle(suppressCancellation);
        // Paper end - Force entity dismount during teleportation
        this.boardingCooldown = 0;
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || this.isSleeping() || this.isRemoved() || !valid; // Paper - player's who are dead or not in a world shouldn't move...
    }

    @Override
    public boolean isAffectedByFluids() {
        return !this.abilities.flying;
    }

    @Override
    protected Vec3 maybeBackOffFromEdge(Vec3 vec, MoverType mover) {
        float f = this.maxUpStep();
        if (!this.abilities.flying
            && !(vec.y > 0.0)
            && (mover == MoverType.SELF || mover == MoverType.PLAYER)
            && this.isStayingOnGroundSurface()
            && this.isAboveGround(f)) {
            double d = vec.x;
            double d1 = vec.z;
            double d2 = 0.05;
            double d3 = Math.signum(d) * 0.05;

            double d4;
            for (d4 = Math.signum(d1) * 0.05; d != 0.0 && this.canFallAtLeast(d, 0.0, f); d -= d3) {
                if (Math.abs(d) <= 0.05) {
                    d = 0.0;
                    break;
                }
            }

            while (d1 != 0.0 && this.canFallAtLeast(0.0, d1, f)) {
                if (Math.abs(d1) <= 0.05) {
                    d1 = 0.0;
                    break;
                }

                d1 -= d4;
            }

            while (d != 0.0 && d1 != 0.0 && this.canFallAtLeast(d, d1, f)) {
                if (Math.abs(d) <= 0.05) {
                    d = 0.0;
                } else {
                    d -= d3;
                }

                if (Math.abs(d1) <= 0.05) {
                    d1 = 0.0;
                } else {
                    d1 -= d4;
                }
            }

            return new Vec3(d, vec.y, d1);
        } else {
            return vec;
        }
    }

    private boolean isAboveGround(float maxUpStep) {
        return this.onGround() || this.fallDistance < maxUpStep && !this.canFallAtLeast(0.0, 0.0, maxUpStep - this.fallDistance);
    }

    private boolean canFallAtLeast(double x, double z, float distance) {
        AABB boundingBox = this.getBoundingBox();
        return this.level()
            .noCollision(
                this,
                new AABB(
                    boundingBox.minX + x,
                    boundingBox.minY - distance - 1.0E-5F,
                    boundingBox.minZ + z,
                    boundingBox.maxX + x,
                    boundingBox.minY,
                    boundingBox.maxZ + z
                )
            );
    }

    public void attack(Entity target) {
        // Paper start - PlayerAttackEntityEvent
        boolean willAttack = target.isAttackable() && !target.skipAttackInteraction(this); // Vanilla logic
        io.papermc.paper.event.player.PrePlayerAttackEntityEvent playerAttackEntityEvent = new io.papermc.paper.event.player.PrePlayerAttackEntityEvent(
            (org.bukkit.entity.Player) this.getBukkitEntity(),
            target.getBukkitEntity(),
            willAttack
        );

        if (playerAttackEntityEvent.callEvent() && willAttack) { // Logic moved to willAttack local variable.
            {
        // Paper end - PlayerAttackEntityEvent
                float f = this.isAutoSpinAttack() ? this.autoSpinAttackDmg : (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
                ItemStack weaponItem = this.getWeaponItem();
                DamageSource damageSource = Optional.ofNullable(weaponItem.getItem().getDamageSource(this)).orElse(this.damageSources().playerAttack(this));
                float f1 = this.getEnchantedDamage(target, f, damageSource) - f;
                float attackStrengthScale = this.getAttackStrengthScale(0.5F);
                f *= 0.2F + attackStrengthScale * attackStrengthScale * 0.8F;
                f1 *= attackStrengthScale;
                // this.resetAttackStrengthTicker(); // CraftBukkit - Moved to EntityLiving to reset the cooldown after the damage is dealt
                if (target.getType().is(EntityTypeTags.REDIRECTABLE_PROJECTILE)
                    && target instanceof Projectile projectile) {
                        // CraftBukkit start
                        if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(target, damageSource, f1, false)) {
                            return;
                        }
                        if (projectile.deflect(ProjectileDeflection.AIM_DEFLECT, this, this, true)) {
                            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, this.getSoundSource());
                            return;
                        }
                }
                {
                    // CraftBukkit end
                    if (f > 0.0F || f1 > 0.0F) {
                        boolean flag = attackStrengthScale > 0.9F;
                        boolean flag1;
                        if (this.isSprinting() && flag) {
                            this.sendSoundEffect(this, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, this.getSoundSource(), 1.0F, 1.0F); // Paper - send while respecting visibility
                            flag1 = true;
                        } else {
                            flag1 = false;
                        }

                        f += weaponItem.getItem().getAttackDamageBonus(target, f, damageSource);
                        boolean flag2 = flag
                            && this.fallDistance > 0.0F
                            && !this.onGround()
                            && !this.onClimbable()
                            && !this.isInWater()
                            && !this.hasEffect(MobEffects.BLINDNESS)
                            && !this.isPassenger()
                            && target instanceof LivingEntity
                            && !this.isSprinting();
                        flag2 = flag2 && !this.level().paperConfig().entities.behavior.disablePlayerCrits; // Paper - Toggleable player crits
                        if (flag2) {
                            damageSource = damageSource.critical(true); // Paper start - critical damage API
                            f *= this.level().purpurConfig.playerCriticalDamageMultiplier; // Purpur - Add config change multiplier critical damage value
                        }

                        float f2 = f + f1;
                        boolean flag3 = false;
                        if (flag && !flag2 && !flag1 && this.onGround()) {
                            double d = this.getKnownMovement().horizontalDistanceSqr();
                            double d1 = this.getSpeed() * 2.5;
                            if (d < Mth.square(d1) && this.getItemInHand(InteractionHand.MAIN_HAND).is(ItemTags.SWORDS)) {
                                flag3 = true;
                            }
                        }

                        float f3 = 0.0F;
                        if (target instanceof LivingEntity livingEntity) {
                            f3 = livingEntity.getHealth();
                        }

                        Vec3 deltaMovement = target.getDeltaMovement();
                        boolean flag4 = target.hurtOrSimulate(damageSource, f2);
                        if (flag4) {
                            float f4 = this.getKnockback(target, damageSource) + (flag1 ? 1.0F : 0.0F);
                            if (f4 > 0.0F) {
                                if (target instanceof LivingEntity livingEntity1) {
                                    livingEntity1.knockback(
                                        f4 * 0.5F, Mth.sin(this.getYRot() * (float) (Math.PI / 180.0)), -Mth.cos(this.getYRot() * (float) (Math.PI / 180.0))
                                        , this, io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.ENTITY_ATTACK // Paper - knockback events
                                    );
                                } else {
                                    target.push(
                                        -Mth.sin(this.getYRot() * (float) (Math.PI / 180.0)) * f4 * 0.5F,
                                        0.1,
                                        Mth.cos(this.getYRot() * (float) (Math.PI / 180.0)) * f4 * 0.5F
                                        , this // Paper - Add EntityKnockbackByEntityEvent and EntityPushedByEntityAttackEvent
                                    );
                                }

                                this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, 1.0, 0.6));
                                // Paper start - Configurable sprint interruption on attack
                                if (!this.level().paperConfig().misc.disableSprintInterruptionOnAttack) {
                                this.setSprinting(false);
                                }
                                // Paper end - Configurable sprint interruption on attack
                            }

                            if (flag3) {
                                float f5 = 1.0F + (float)this.getAttributeValue(Attributes.SWEEPING_DAMAGE_RATIO) * f;

                                for (LivingEntity livingEntity2 : this.level()
                                    .getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(1.0, 0.25, 1.0))) {
                                    if (livingEntity2 != this
                                        && livingEntity2 != target
                                        && !this.isAlliedTo(livingEntity2)
                                        && (!(livingEntity2 instanceof ArmorStand) || !((ArmorStand)livingEntity2).isMarker())
                                        && this.distanceToSqr(livingEntity2) < 9.0) {
                                        float f6 = this.getEnchantedDamage(livingEntity2, f5, damageSource) * attackStrengthScale;
                                        // CraftBukkit start - Only apply knockback if the damage hits
                                        if (!livingEntity2.hurtServer((ServerLevel) this.level(), this.damageSources().playerAttack(this).sweep().critical(flag2), f6)) { // Paper - add critical damage API
                                            continue;
                                        }
                                        // CraftBukkit end
                                        livingEntity2.knockback(
                                            0.4F, Mth.sin(this.getYRot() * (float) (Math.PI / 180.0)), -Mth.cos(this.getYRot() * (float) (Math.PI / 180.0))
                                            , this, io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.SWEEP_ATTACK // CraftBukkit // Paper - knockback events
                                        );
                                        // CraftBukkit - moved up
                                        if (this.level() instanceof ServerLevel serverLevel) {
                                            EnchantmentHelper.doPostAttackEffects(serverLevel, livingEntity2, damageSource);
                                        }
                                    }
                                }

                                this.sendSoundEffect(this, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, this.getSoundSource(), 1.0F, 1.0F); // Paper - send while respecting visibility
                                this.sweepAttack();
                            }

                            if (target instanceof ServerPlayer && target.hurtMarked) {
                                // CraftBukkit start - Add Velocity Event
                                boolean cancelled = false;
                                org.bukkit.entity.Player player = (org.bukkit.entity.Player) target.getBukkitEntity();
                                org.bukkit.util.Vector velocity = org.bukkit.craftbukkit.util.CraftVector.toBukkit(deltaMovement);

                                org.bukkit.event.player.PlayerVelocityEvent event = new org.bukkit.event.player.PlayerVelocityEvent(player, velocity.clone());
                                this.level().getCraftServer().getPluginManager().callEvent(event);

                                if (event.isCancelled()) {
                                    cancelled = true;
                                } else if (!velocity.equals(event.getVelocity())) {
                                    player.setVelocity(event.getVelocity());
                                }

                                if (!cancelled) {
                                ((ServerPlayer)target).connection.send(new ClientboundSetEntityMotionPacket(target));
                                target.hurtMarked = false;
                                target.setDeltaMovement(deltaMovement);
                                }
                                // CraftBukkit end
                            }

                            if (flag2) {
                                this.sendSoundEffect(this, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, this.getSoundSource(), 1.0F, 1.0F); // Paper - send while respecting visibility
                                this.crit(target);
                            }

                            if (!flag2 && !flag3) {
                                if (flag) {
                                    this.sendSoundEffect(
                                            this, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, this.getSoundSource(), 1.0F, 1.0F // Paper - send while respecting visibility
                                        );
                                } else {
                                    this.sendSoundEffect(
                                            this, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, this.getSoundSource(), 1.0F, 1.0F // Paper - send while respecting visibility
                                        );
                                }
                            }

                            if (f1 > 0.0F) {
                                this.magicCrit(target);
                            }

                            this.setLastHurtMob(target);
                            Entity entity = target;
                            if (target instanceof EnderDragonPart) {
                                entity = ((EnderDragonPart)target).parentMob;
                            }

                            boolean flag5 = false;
                            if (this.level() instanceof ServerLevel serverLevel1) {
                                if (entity instanceof LivingEntity livingEntity2x) {
                                    flag5 = weaponItem.hurtEnemy(livingEntity2x, this);
                                }

                                EnchantmentHelper.doPostAttackEffects(serverLevel1, target, damageSource);
                            }

                            if (!this.level().isClientSide && !weaponItem.isEmpty() && entity instanceof LivingEntity) {
                                if (flag5) {
                                    weaponItem.postHurtEnemy((LivingEntity)entity, this);
                                }

                                if (weaponItem.isEmpty()) {
                                    if (weaponItem == this.getMainHandItem()) {
                                        this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                                    } else {
                                        this.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                                    }
                                }
                            }

                            if (target instanceof LivingEntity) {
                                float f7 = f3 - ((LivingEntity)target).getHealth();
                                this.awardStat(Stats.DAMAGE_DEALT, Math.round(f7 * 10.0F));
                                if (this.level() instanceof ServerLevel && f7 > 2.0F) {
                                    int i = (int)(f7 * 0.5);
                                    ((ServerLevel)this.level())
                                        .sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.5), target.getZ(), i, 0.1, 0.0, 0.1, 0.2);
                                }
                            }

                            this.causeFoodExhaustion(this.level().spigotConfig.combatExhaustion, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.ATTACK); // CraftBukkit - EntityExhaustionEvent // Spigot - Change to use configurable value
                        } else {
                            this.sendSoundEffect(this, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, this.getSoundSource(), 1.0F, 1.0F); // Paper - send while respecting visibility
                            // CraftBukkit start - resync on cancelled event
                            if (this instanceof ServerPlayer) {
                                ((ServerPlayer) this).getBukkitEntity().updateInventory();
                            }
                            // CraftBukkit end
                        }
                    }
                }
            }
        }
    }

    protected float getEnchantedDamage(Entity entity, float damage, DamageSource damageSource) {
        return damage;
    }

    @Override
    protected void doAutoAttackOnTouch(LivingEntity target) {
        this.attack(target);
    }

    @io.papermc.paper.annotation.DoNotUse @Deprecated // Paper - Add PlayerShieldDisableEvent
    public void disableShield(ItemStack stack) {
        // Paper start - Add PlayerShieldDisableEvent
        this.disableShield(stack, null);
    }
    public void disableShield(ItemStack stack, @Nullable LivingEntity attacker) {
        final org.bukkit.entity.Entity finalAttacker = attacker != null ? attacker.getBukkitEntity() : null;
        if (finalAttacker != null) {
            final io.papermc.paper.event.player.PlayerShieldDisableEvent shieldDisableEvent = new io.papermc.paper.event.player.PlayerShieldDisableEvent((org.bukkit.entity.Player) getBukkitEntity(), finalAttacker, 100);
            if (!shieldDisableEvent.callEvent()) return;
            this.getCooldowns().addCooldown(stack, shieldDisableEvent.getCooldown());
        } else {
            this.getCooldowns().addCooldown(stack, 100);
        }
        // Paper end - Add PlayerShieldDisableEvent
        this.stopUsingItem();
        this.level().broadcastEntityEvent(this, (byte)30);
    }

    public void crit(Entity entityHit) {
    }

    public void magicCrit(Entity entityHit) {
    }

    public void sweepAttack() {
        double d = -Mth.sin(this.getYRot() * (float) (Math.PI / 180.0));
        double d1 = Mth.cos(this.getYRot() * (float) (Math.PI / 180.0));
        if (this.level() instanceof ServerLevel) {
            ((ServerLevel)this.level()).sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX() + d, this.getY(0.5), this.getZ() + d1, 0, d, 0.0, d1, 0.0);
        }
    }

    public void respawn() {
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        // CraftBukkit start - add Bukkit remove cause
        this.remove(reason, null);
    }

    @Override
    public void remove(Entity.RemovalReason reason, org.bukkit.event.entity.EntityRemoveEvent.Cause eventCause) {
        super.remove(reason, eventCause);
        // CraftBukkit end
        this.inventoryMenu.removed(this);
        if (this.containerMenu != null && this.hasContainerOpen()) {
            this.doCloseContainer();
        }
    }

    public boolean isLocalPlayer() {
        return false;
    }

    public GameProfile getGameProfile() {
        return this.gameProfile;
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    public Abilities getAbilities() {
        return this.abilities;
    }

    @Override
    public boolean hasInfiniteMaterials() {
        return this.abilities.instabuild;
    }

    public void updateTutorialInventoryAction(ItemStack carried, ItemStack clicked, ClickAction action) {
    }

    public boolean hasContainerOpen() {
        return this.containerMenu != this.inventoryMenu;
    }

    public boolean canDropItems() {
        return true;
    }

    public Either<Player.BedSleepingProblem, Unit> startSleepInBed(BlockPos bedPos) {
        // CraftBukkit start
        return this.startSleepInBed(bedPos, false);
    }

    public Either<Player.BedSleepingProblem, Unit> startSleepInBed(BlockPos bedPos, boolean force) {
        // CraftBukkit end
        this.startSleeping(bedPos);
        this.sleepCounter = 0;
        return Either.right(Unit.INSTANCE);
    }

    public void stopSleepInBed(boolean wakeImmediately, boolean updateLevelForSleepingPlayers) {
        super.stopSleeping();
        if (this.level() instanceof ServerLevel && updateLevelForSleepingPlayers) {
            ((ServerLevel)this.level()).updateSleepingPlayerList();
        }

        this.sleepCounter = wakeImmediately ? 0 : 100;
    }

    @Override
    public void stopSleeping() {
        this.stopSleepInBed(true, true);
    }

    public boolean isSleepingLongEnough() {
        return this.isSleeping() && this.sleepCounter >= 100;
    }

    public int getSleepTimer() {
        return this.sleepCounter;
    }

    public void displayClientMessage(Component chatComponent, boolean actionBar) {
    }

    public void awardStat(ResourceLocation statKey) {
        this.awardStat(Stats.CUSTOM.get(statKey));
    }

    public void awardStat(ResourceLocation stat, int increment) {
        this.awardStat(Stats.CUSTOM.get(stat), increment);
    }

    public void awardStat(Stat<?> stat) {
        this.awardStat(stat, 1);
    }

    public void awardStat(Stat<?> stat, int increment) {
    }

    public void resetStat(Stat<?> stat) {
    }

    public int awardRecipes(Collection<RecipeHolder<?>> recipes) {
        return 0;
    }

    public void triggerRecipeCrafted(RecipeHolder<?> recipe, List<ItemStack> items) {
    }

    public void awardRecipesByKey(List<ResourceKey<Recipe<?>>> recipes) {
    }

    public int resetRecipes(Collection<RecipeHolder<?>> recipes) {
        return 0;
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isPassenger()) {
            super.travel(travelVector);
        } else {
            if (this.isSwimming()) {
                double d = this.getLookAngle().y;
                double d1 = d < -0.2 ? 0.085 : 0.06;
                if (d <= 0.0 || this.jumping || !this.level().getFluidState(BlockPos.containing(this.getX(), this.getY() + 1.0 - 0.1, this.getZ())).isEmpty()) {
                    Vec3 deltaMovement = this.getDeltaMovement();
                    this.setDeltaMovement(deltaMovement.add(0.0, (d - deltaMovement.y) * d1, 0.0));
                }
            }

            if (this.getAbilities().flying) {
                double d = this.getDeltaMovement().y;
                super.travel(travelVector);
                this.setDeltaMovement(this.getDeltaMovement().with(Direction.Axis.Y, d * 0.6));
            } else {
                super.travel(travelVector);
            }
        }
    }

    @Override
    public boolean canGlide() {
        return !this.abilities.flying && super.canGlide();
    }

    @Override
    public void updateSwimming() {
        if (this.abilities.flying) {
            this.setSwimming(false);
        } else {
            super.updateSwimming();
        }
    }

    protected boolean freeAt(BlockPos pos) {
        return !this.level().getBlockState(pos).isSuffocating(this.level(), pos);
    }

    @Override
    public float getSpeed() {
        return (float)this.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (this.abilities.mayfly && !this.flyingFallDamage.toBooleanOrElse(false)) { // Paper - flying fall damage
            return false;
        } else {
            if (fallDistance >= 2.0F) {
                this.awardStat(Stats.FALL_ONE_CM, (int)Math.round(fallDistance * 100.0));
            }

            boolean flag = this.currentImpulseImpactPos != null && this.ignoreFallDamageFromCurrentImpulse;
            float min;
            if (flag) {
                min = Math.min(fallDistance, (float)(this.currentImpulseImpactPos.y - this.getY()));
                boolean flag1 = min <= 0.0F;
                if (flag1) {
                    this.resetCurrentImpulseContext();
                } else {
                    this.tryResetCurrentImpulseContext();
                }
            } else {
                min = fallDistance;
            }

            if (min > 0.0F && super.causeFallDamage(min, multiplier, source)) {
                this.resetCurrentImpulseContext();
                return true;
            } else {
                return false;
            }
        }
    }

    public boolean tryToStartFallFlying() {
        if (!this.isFallFlying() && this.canGlide() && !this.isInWater()) {
            this.startFallFlying();
            return true;
        } else {
            return false;
        }
    }

    public void startFallFlying() {
        // CraftBukkit start
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callToggleGlideEvent(this, true).isCancelled()) {
            this.setSharedFlag(7, true);
        } else {
            // SPIGOT-5542: must toggle like below
            this.setSharedFlag(7, true);
            this.setSharedFlag(7, false);
        }
        // CraftBukkit end
    }

    public void stopFallFlying() {
        // CraftBukkit start
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callToggleGlideEvent(this, false).isCancelled()) {
        this.setSharedFlag(7, true);
        this.setSharedFlag(7, false);
        }
        // CraftBukkit end
    }

    @Override
    protected void doWaterSplashEffect() {
        if (!this.isSpectator()) {
            super.doWaterSplashEffect();
        }
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (this.isInWater()) {
            this.waterSwimSound();
            this.playMuffledStepSound(state);
        } else {
            BlockPos primaryStepSoundBlockPos = this.getPrimaryStepSoundBlockPos(pos);
            if (!pos.equals(primaryStepSoundBlockPos)) {
                BlockState blockState = this.level().getBlockState(primaryStepSoundBlockPos);
                if (blockState.is(BlockTags.COMBINATION_STEP_SOUND_BLOCKS)) {
                    this.playCombinationStepSounds(blockState, state);
                } else {
                    super.playStepSound(primaryStepSoundBlockPos, blockState);
                }
            } else {
                super.playStepSound(pos, state);
            }
        }
    }

    @Override
    public LivingEntity.Fallsounds getFallSounds() {
        return new LivingEntity.Fallsounds(SoundEvents.PLAYER_SMALL_FALL, SoundEvents.PLAYER_BIG_FALL);
    }

    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity entity) {
        this.awardStat(Stats.ENTITY_KILLED.get(entity.getType()));
        return true;
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 motionMultiplier) {
        if (!this.abilities.flying) {
            super.makeStuckInBlock(state, motionMultiplier);
        }

        this.tryResetCurrentImpulseContext();
    }

    public void giveExperiencePoints(int xpPoints) {
        this.increaseScore(xpPoints);
        this.experienceProgress = this.experienceProgress + (float)xpPoints / this.getXpNeededForNextLevel();
        this.totalExperience = Mth.clamp(this.totalExperience + xpPoints, 0, Integer.MAX_VALUE);

        while (this.experienceProgress < 0.0F) {
            float f = this.experienceProgress * this.getXpNeededForNextLevel();
            if (this.experienceLevel > 0) {
                this.giveExperienceLevels(-1);
                this.experienceProgress = 1.0F + f / this.getXpNeededForNextLevel();
            } else {
                this.giveExperienceLevels(-1);
                this.experienceProgress = 0.0F;
            }
        }

        while (this.experienceProgress >= 1.0F) {
            this.experienceProgress = (this.experienceProgress - 1.0F) * this.getXpNeededForNextLevel();
            this.giveExperienceLevels(1);
            this.experienceProgress = this.experienceProgress / this.getXpNeededForNextLevel();
        }
    }

    public int getEnchantmentSeed() {
        return this.enchantmentSeed;
    }

    public void onEnchantmentPerformed(ItemStack enchantedItem, int levelCost) {
        this.experienceLevel -= levelCost;
        if (this.experienceLevel < 0) {
            this.experienceLevel = 0;
            this.experienceProgress = 0.0F;
            this.totalExperience = 0;
        }

        this.enchantmentSeed = this.random.nextInt();
    }

    public void giveExperienceLevels(int levels) {
        this.experienceLevel = IntMath.saturatedAdd(this.experienceLevel, levels);
        if (this.experienceLevel < 0) {
            this.experienceLevel = 0;
            this.experienceProgress = 0.0F;
            this.totalExperience = 0;
        }

        if (levels > 0 && this.experienceLevel % 5 == 0 && this.lastLevelUpTime < this.tickCount - 100.0F) {
            float f = this.experienceLevel > 30 ? 1.0F : this.experienceLevel / 30.0F;
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_LEVELUP, this.getSoundSource(), f * 0.75F, 1.0F);
            this.lastLevelUpTime = this.tickCount;
        }
    }

    public int getXpNeededForNextLevel() {
        if (this.experienceLevel >= 30) {
            return 112 + (this.experienceLevel - 30) * 9;
        } else { // Paper - diff on change; calculateTotalExperiencePoints
            return this.experienceLevel >= 15 ? 37 + (this.experienceLevel - 15) * 5 : 7 + this.experienceLevel * 2;
        }
    }

    // Paper start - send while respecting visibility
    private static void sendSoundEffect(Player fromEntity, double x, double y, double z, SoundEvent soundEffect, SoundSource soundCategory, float volume, float pitch) {
        fromEntity.level().playSound(fromEntity, x, y, z, soundEffect, soundCategory, volume, pitch); // This will not send the effect to the entity itself
        if (fromEntity instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(soundEffect), soundCategory, x, y, z, volume, pitch, fromEntity.random.nextLong()));
        }
    }
    // Paper end - send while respecting visibility

    public void causeFoodExhaustion(float exhaustion) {
        // CraftBukkit start
        this.causeFoodExhaustion(exhaustion, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.UNKNOWN);
    }

    public void causeFoodExhaustion(float exhaustion, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason reason) {
        // CraftBukkit end
        if (!this.abilities.invulnerable) {
            if (!this.level().isClientSide) {
                // CraftBukkit start
                org.bukkit.event.entity.EntityExhaustionEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerExhaustionEvent(this, reason, exhaustion);
                if (!event.isCancelled()) {
                    this.foodData.addExhaustion(event.getExhaustion());
                }
                // CraftBukkit end
            }
        }
    }

    public Optional<WardenSpawnTracker> getWardenSpawnTracker() {
        return Optional.empty();
    }

    public FoodData getFoodData() {
        return this.foodData;
    }

    public boolean canEat(boolean canAlwaysEat) {
        return this.abilities.invulnerable || canAlwaysEat || this.foodData.needsFood();
    }

    public boolean isHurt() {
        return this.getHealth() > 0.0F && this.getHealth() < this.getMaxHealth();
    }

    public boolean mayBuild() {
        return this.abilities.mayBuild;
    }

    public boolean mayUseItemAt(BlockPos pos, Direction facing, ItemStack stack) {
        if (this.abilities.mayBuild) {
            return true;
        } else {
            BlockPos blockPos = pos.relative(facing.getOpposite());
            BlockInWorld blockInWorld = new BlockInWorld(this.level(), blockPos, false);
            return stack.canPlaceOnBlockInAdventureMode(blockInWorld);
        }
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        // Purpur start - Add player death exp control options
        if (!level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) && !this.isSpectator()) {
            int toDrop;
            try {
                toDrop = Math.round(((Number) scriptEngine.eval("let expLevel = " + experienceLevel + "; " +
                    "let expTotal = " + totalExperience + "; " +
                    "let exp = " + experienceProgress + "; " +
                    level().purpurConfig.playerDeathExpDropEquation)).floatValue());
            } catch (javax.script.ScriptException e) {
                e.printStackTrace();
                toDrop = experienceLevel * 7;
            }
            return Math.min(toDrop, level().purpurConfig.playerDeathExpDropMax);
        } else {
            return 0;
        }
        // Purpur end - Add player death exp control options
    }

    @Override
    protected boolean isAlwaysExperienceDropper() {
        return true;
    }

    @Override
    public boolean shouldShowName() {
        return true;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return this.abilities.flying || this.onGround() && this.isDiscrete() ? Entity.MovementEmission.NONE : Entity.MovementEmission.ALL;
    }

    public void onUpdateAbilities() {
    }

    @Override
    public Component getName() {
        return Component.literal(this.gameProfile.getName());
    }

    public PlayerEnderChestContainer getEnderChestInventory() {
        return this.enderChestInventory;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot1) {
        if (slot1 == EquipmentSlot.MAINHAND) {
            return this.inventory.getSelected();
        } else if (slot1 == EquipmentSlot.OFFHAND) {
            return this.inventory.offhand.getFirst();
        } else {
            return slot1.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? this.inventory.armor.get(slot1.getIndex()) : ItemStack.EMPTY;
        }
    }

    @Override
    protected boolean doesEmitEquipEvent(EquipmentSlot slot) {
        return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        // CraftBukkit start
        this.setItemSlot(slot, stack, false);
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack, boolean silent) {
        // CraftBukkit end
        this.verifyEquippedItem(stack);
        if (slot == EquipmentSlot.MAINHAND) {
            this.onEquipItem(slot, this.inventory.items.set(this.inventory.selected, stack), stack, silent); // CraftBukkit
        } else if (slot == EquipmentSlot.OFFHAND) {
            this.onEquipItem(slot, this.inventory.offhand.set(0, stack), stack, silent); // CraftBukkit
        } else if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            this.onEquipItem(slot, this.inventory.armor.set(slot.getIndex(), stack), stack, silent); // CraftBukkit
        }
    }

    public boolean addItem(ItemStack stack) {
        return this.inventory.add(stack);
    }

    @Override
    public Iterable<ItemStack> getHandSlots() {
        return Lists.newArrayList(this.getMainHandItem(), this.getOffhandItem());
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return this.inventory.armor;
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return slot != EquipmentSlot.BODY;
    }

    // Purpur start - Player ridable in water option
    @Override
    public boolean dismountsUnderwater() {
        return !level().purpurConfig.playerRidableInWater;
    }
    // Purpur end - Player ridable in water option

    public boolean setEntityOnShoulder(CompoundTag entityCompound) {
        if (this.isPassenger() || !this.onGround() || this.isInWater() || this.isInPowderSnow) {
            return false;
        } else if (this.getShoulderEntityLeft().isEmpty()) {
            this.setShoulderEntityLeft(entityCompound);
            this.timeEntitySatOnShoulder = this.level().getGameTime();
            return true;
        } else if (this.getShoulderEntityRight().isEmpty()) {
            this.setShoulderEntityRight(entityCompound);
            this.timeEntitySatOnShoulder = this.level().getGameTime();
            return true;
        } else {
            return false;
        }
    }

    public void removeEntitiesOnShoulder() {
        if (this.timeEntitySatOnShoulder + 20L < this.level().getGameTime()) {
            // CraftBukkit start
            if (this.respawnEntityOnShoulder(this.getShoulderEntityLeft())) {
                this.setShoulderEntityLeft(new CompoundTag());
            }
            if (this.respawnEntityOnShoulder(this.getShoulderEntityRight())) {
                this.setShoulderEntityRight(new CompoundTag());
            }
            // CraftBukkit end
        }
    }

    // Paper start - release entity api
    public Entity releaseLeftShoulderEntity() {
        Entity entity = this.respawnEntityOnShoulder0(this.getShoulderEntityLeft());
        if (entity != null) {
            this.setShoulderEntityLeft(new CompoundTag());
        }
        return entity;
    }

    public Entity releaseRightShoulderEntity() {
        Entity entity = this.respawnEntityOnShoulder0(this.getShoulderEntityRight());
        if (entity != null) {
            this.setShoulderEntityRight(new CompoundTag());
        }
        return entity;
    }
    // Paper end - release entity api

    private boolean respawnEntityOnShoulder(CompoundTag entityCompound) { // CraftBukkit void->boolean
    // Paper start - release entity api - return entity - overload
        return this.respawnEntityOnShoulder0(entityCompound) != null;
    }
    @Nullable
    private Entity respawnEntityOnShoulder0(CompoundTag entityCompound) { // CraftBukkit void->boolean
    // Paper end - release entity api - return entity - overload
        if (!this.level().isClientSide && !entityCompound.isEmpty()) {
            return EntityType.create(entityCompound, this.level(), EntitySpawnReason.LOAD).map((entity) -> { // CraftBukkit
                if (entity instanceof TamableAnimal) {
                    ((TamableAnimal)entity).setOwnerUUID(this.uuid);
                }

                entity.setPos(this.getX(), this.getY() + 0.7F, this.getZ());
                return ((ServerLevel)this.level()).addWithUUID(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SHOULDER_ENTITY) ? entity : null; // CraftBukkit // Paper start - release entity api - return entity
            }).orElse(null); // CraftBukkit // Paper end - release entity api - return entity
        }
        return null; // Paper - return null
    }

    @Override
    public abstract boolean isSpectator();

    @Override
    public boolean canBeHitByProjectile() {
        return !this.isSpectator() && super.canBeHitByProjectile();
    }

    @Override
    public boolean isSwimming() {
        return !this.abilities.flying && !this.isSpectator() && super.isSwimming();
    }

    public abstract boolean isCreative();

    @Override
    public boolean isPushedByFluid() {
        return !this.abilities.flying;
    }

    public Scoreboard getScoreboard() {
        return this.level().getScoreboard();
    }

    @Override
    public Component getDisplayName() {
        MutableComponent mutableComponent = PlayerTeam.formatNameForTeam(this.getTeam(), this.getName());
        return this.decorateDisplayNameComponent(mutableComponent);
    }

    private MutableComponent decorateDisplayNameComponent(MutableComponent displayName) {
        String name = this.getGameProfile().getName();
        return displayName.withStyle(
            style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell " + name + " "))
                .withHoverEvent(this.createHoverEvent())
                .withInsertion(name)
        );
    }

    @Override
    public String getScoreboardName() {
        return this.getGameProfile().getName();
    }

    @Override
    protected void internalSetAbsorptionAmount(float absorptionAmount) {
        this.getEntityData().set(DATA_PLAYER_ABSORPTION_ID, absorptionAmount);
    }

    @Override
    public float getAbsorptionAmount() {
        return this.getEntityData().get(DATA_PLAYER_ABSORPTION_ID);
    }

    public boolean isModelPartShown(PlayerModelPart part) {
        return (this.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION) & part.getMask()) == part.getMask();
    }

    @Override
    public SlotAccess getSlot(int slot) {
        if (slot == 499) {
            return new SlotAccess() {
                @Override
                public ItemStack get() {
                    return Player.this.containerMenu.getCarried();
                }

                @Override
                public boolean set(ItemStack carried) {
                    Player.this.containerMenu.setCarried(carried);
                    return true;
                }
            };
        } else {
            final int i = slot - 500;
            if (i >= 0 && i < 4) {
                return new SlotAccess() {
                    @Override
                    public ItemStack get() {
                        return Player.this.inventoryMenu.getCraftSlots().getItem(i);
                    }

                    @Override
                    public boolean set(ItemStack carried) {
                        Player.this.inventoryMenu.getCraftSlots().setItem(i, carried);
                        Player.this.inventoryMenu.slotsChanged(Player.this.inventory);
                        return true;
                    }
                };
            } else if (slot >= 0 && slot < this.inventory.items.size()) {
                return SlotAccess.forContainer(this.inventory, slot);
            } else {
                int i1 = slot - 200;
                return i1 >= 0 && i1 < this.enderChestInventory.getContainerSize()
                    ? SlotAccess.forContainer(this.enderChestInventory, i1)
                    : super.getSlot(slot);
            }
        }
    }

    public boolean isReducedDebugInfo() {
        return this.reducedDebugInfo;
    }

    public void setReducedDebugInfo(boolean reducedDebugInfo) {
        this.reducedDebugInfo = reducedDebugInfo;
    }

    @Override
    public void setRemainingFireTicks(int ticks) {
        super.setRemainingFireTicks(this.abilities.invulnerable ? Math.min(ticks, 1) : ticks);
    }

    @Override
    public HumanoidArm getMainArm() {
        return this.entityData.get(DATA_PLAYER_MAIN_HAND) == 0 ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }

    public void setMainArm(HumanoidArm hand) {
        this.entityData.set(DATA_PLAYER_MAIN_HAND, (byte)(hand == HumanoidArm.LEFT ? 0 : 1));
    }

    public CompoundTag getShoulderEntityLeft() {
        return this.entityData.get(DATA_SHOULDER_LEFT);
    }

    public void setShoulderEntityLeft(CompoundTag entityCompound) {
        this.entityData.set(DATA_SHOULDER_LEFT, entityCompound);
    }

    public CompoundTag getShoulderEntityRight() {
        return this.entityData.get(DATA_SHOULDER_RIGHT);
    }

    public void setShoulderEntityRight(CompoundTag entityCompound) {
        this.entityData.set(DATA_SHOULDER_RIGHT, entityCompound);
    }

    public float getCurrentItemAttackStrengthDelay() {
        return (float)(1.0 / this.getAttributeValue(Attributes.ATTACK_SPEED) * 20.0);
    }

    public float getAttackStrengthScale(float adjustTicks) {
        return Mth.clamp((this.attackStrengthTicker + adjustTicks) / this.getCurrentItemAttackStrengthDelay(), 0.0F, 1.0F);
    }

    public void resetAttackStrengthTicker() {
        this.attackStrengthTicker = 0;
    }

    public ItemCooldowns getCooldowns() {
        return this.cooldowns;
    }

    @Override
    protected float getBlockSpeedFactor() {
        return !this.abilities.flying && !this.isFallFlying() ? super.getBlockSpeedFactor() : 1.0F;
    }

    public float getLuck() {
        return (float)this.getAttributeValue(Attributes.LUCK);
    }

    public boolean canUseGameMasterBlocks() {
        return this.abilities.instabuild && this.getPermissionLevel() >= 2;
    }

    public int getPermissionLevel() {
        return 0;
    }

    public boolean hasPermissions(int permissionLevel) {
        return this.getPermissionLevel() >= permissionLevel;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return POSES.getOrDefault(pose, STANDING_DIMENSIONS);
    }

    @Override
    public ImmutableList<Pose> getDismountPoses() {
        return ImmutableList.of(Pose.STANDING, Pose.CROUCHING, Pose.SWIMMING);
    }

    // Paper start - PlayerReadyArrowEvent
    protected boolean tryReadyArrow(ItemStack bow, ItemStack itemstack) {
        return !(this instanceof ServerPlayer) ||
                new com.destroystokyo.paper.event.player.PlayerReadyArrowEvent(
                    ((ServerPlayer) this).getBukkitEntity(),
                    org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(bow),
                    org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemstack)
                ).callEvent();
    }
    // Paper end - PlayerReadyArrowEvent

    @Override
    public ItemStack getProjectile(ItemStack shootable) {
        if (!(shootable.getItem() instanceof ProjectileWeaponItem)) {
            return ItemStack.EMPTY;
        } else {
            Predicate<ItemStack> supportedHeldProjectiles = ((ProjectileWeaponItem)shootable.getItem()).getSupportedHeldProjectiles().and(item -> this.tryReadyArrow(shootable, item)); // Paper - PlayerReadyArrowEvent
            ItemStack heldProjectile = ProjectileWeaponItem.getHeldProjectile(this, supportedHeldProjectiles);
            if (!heldProjectile.isEmpty()) {
                return heldProjectile;
            } else {
                supportedHeldProjectiles = ((ProjectileWeaponItem)shootable.getItem()).getAllSupportedProjectiles().and(item -> this.tryReadyArrow(shootable, item)); // Paper - PlayerReadyArrowEvent

                for (int i = 0; i < this.inventory.getContainerSize(); i++) {
                    ItemStack item = this.inventory.getItem(i);
                    if (supportedHeldProjectiles.test(item)) {
                        return item;
                    }
                }

                return this.abilities.instabuild ? new ItemStack(Items.ARROW) : ItemStack.EMPTY;
            }
        }
    }

    @Override
    public Vec3 getRopeHoldPosition(float partialTicks) {
        double d = 0.22 * (this.getMainArm() == HumanoidArm.RIGHT ? -1.0 : 1.0);
        float f = Mth.lerp(partialTicks * 0.5F, this.getXRot(), this.xRotO) * (float) (Math.PI / 180.0);
        float f1 = Mth.lerp(partialTicks, this.yBodyRotO, this.yBodyRot) * (float) (Math.PI / 180.0);
        if (this.isFallFlying() || this.isAutoSpinAttack()) {
            Vec3 viewVector = this.getViewVector(partialTicks);
            Vec3 deltaMovement = this.getDeltaMovement();
            double d1 = deltaMovement.horizontalDistanceSqr();
            double d2 = viewVector.horizontalDistanceSqr();
            float f2;
            if (d1 > 0.0 && d2 > 0.0) {
                double d3 = (deltaMovement.x * viewVector.x + deltaMovement.z * viewVector.z) / Math.sqrt(d1 * d2);
                double d4 = deltaMovement.x * viewVector.z - deltaMovement.z * viewVector.x;
                f2 = (float)(Math.signum(d4) * Math.acos(d3));
            } else {
                f2 = 0.0F;
            }

            return this.getPosition(partialTicks).add(new Vec3(d, -0.11, 0.85).zRot(-f2).xRot(-f).yRot(-f1));
        } else if (this.isVisuallySwimming()) {
            return this.getPosition(partialTicks).add(new Vec3(d, 0.2, -0.15).xRot(-f).yRot(-f1));
        } else {
            double d5 = this.getBoundingBox().getYsize() - 1.0;
            double d1 = this.isCrouching() ? -0.2 : 0.07;
            return this.getPosition(partialTicks).add(new Vec3(d, d5, d1).yRot(-f1));
        }
    }

    @Override
    public boolean isAlwaysTicking() {
        return true;
    }

    public boolean isScoping() {
        return this.isUsingItem() && this.getUseItem().is(Items.SPYGLASS);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    public Optional<GlobalPos> getLastDeathLocation() {
        return this.lastDeathLocation;
    }

    public void setLastDeathLocation(Optional<GlobalPos> lastDeathLocation) {
        this.lastDeathLocation = lastDeathLocation;
    }

    @Override
    public float getHurtDir() {
        return this.hurtDir;
    }

    @Override
    public void animateHurt(float yaw) {
        super.animateHurt(yaw);
        this.hurtDir = yaw;
    }

    @Override
    public boolean canSprint() {
        return true;
    }

    @Override
    protected float getFlyingSpeed() {
        if (this.abilities.flying && !this.isPassenger()) {
            return this.isSprinting() ? this.abilities.getFlyingSpeed() * 2.0F : this.abilities.getFlyingSpeed();
        } else {
            return this.isSprinting() ? 0.025999999F : 0.02F;
        }
    }

    public boolean hasClientLoaded() {
        return this.clientLoaded; // Paper - Add PlayerLoadedWorldEvent
    }

    public void tickClientLoadTimeout() {
        if (!this.clientLoaded) {
            this.clientLoadedTimeoutTimer--;
            // Paper start - Add PlayerLoadedWorldEvent
            if (this.clientLoadedTimeoutTimer <= 0) {
                this.clientLoaded = true;

                final io.papermc.paper.event.player.PlayerClientLoadedWorldEvent event = new io.papermc.paper.event.player.PlayerClientLoadedWorldEvent((org.bukkit.craftbukkit.entity.CraftPlayer) getBukkitEntity(), true);
                event.callEvent();
            }
            // Paper end - Add PlayerLoadedWorldEvent
        }
    }

    public void setClientLoaded(boolean clientLoaded) {
        this.clientLoaded = clientLoaded;
        if (!this.clientLoaded) {
            this.clientLoadedTimeoutTimer = 60;
        }
    }

    public double blockInteractionRange() {
        return this.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
    }

    public double entityInteractionRange() {
        return this.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
    }

    public boolean canInteractWithEntity(Entity entity, double distance) {
        return !entity.isRemoved() && this.canInteractWithEntity(entity.getBoundingBox(), distance);
    }

    public boolean canInteractWithEntity(AABB boundingBox, double distance) {
        double d = this.entityInteractionRange() + distance;
        return boundingBox.distanceToSqr(this.getEyePosition()) < d * d;
    }

    public boolean canInteractWithBlock(BlockPos pos, double distance) {
        double d = this.blockInteractionRange() + distance;
        return new AABB(pos).distanceToSqr(this.getEyePosition()) < d * d;
    }

    public void setIgnoreFallDamageFromCurrentImpulse(boolean ignoreFallDamageFromCurrentImpulse) {
        this.ignoreFallDamageFromCurrentImpulse = ignoreFallDamageFromCurrentImpulse;
        if (ignoreFallDamageFromCurrentImpulse) {
            this.currentImpulseContextResetGraceTime = 40;
        } else {
            this.currentImpulseContextResetGraceTime = 0;
        }
    }

    public boolean isIgnoringFallDamageFromCurrentImpulse() {
        return this.ignoreFallDamageFromCurrentImpulse;
    }

    public void tryResetCurrentImpulseContext() {
        if (this.currentImpulseContextResetGraceTime == 0) {
            this.resetCurrentImpulseContext();
        }
    }

    public void resetCurrentImpulseContext() {
        this.currentImpulseContextResetGraceTime = 0;
        this.currentExplosionCause = null;
        this.currentImpulseImpactPos = null;
        this.ignoreFallDamageFromCurrentImpulse = false;
    }

    public boolean shouldRotateWithMinecart() {
        return false;
    }

    @Override
    public boolean isControlledByClient() {
        return true;
    }

    @Override
    public boolean onClimbable() {
        return !this.abilities.flying && super.onClimbable();
    }

    public static enum BedSleepingProblem {
        NOT_POSSIBLE_HERE,
        NOT_POSSIBLE_NOW(Component.translatable("block.minecraft.bed.no_sleep")),
        TOO_FAR_AWAY(Component.translatable("block.minecraft.bed.too_far_away")),
        OBSTRUCTED(Component.translatable("block.minecraft.bed.obstructed")),
        OTHER_PROBLEM,
        NOT_SAFE(Component.translatable("block.minecraft.bed.not_safe"));

        @Nullable
        private final Component message;

        private BedSleepingProblem() {
            this.message = null;
        }

        private BedSleepingProblem(final Component message) {
            this.message = message;
        }

        @Nullable
        public Component getMessage() {
            return this.message;
        }
    }
}
