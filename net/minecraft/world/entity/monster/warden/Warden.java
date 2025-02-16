package net.minecraft.world.entity.monster.warden;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import java.util.Collections;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.GameEventTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.warden.SonicBoom;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;

public class Warden extends Monster implements VibrationSystem {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int VIBRATION_COOLDOWN_TICKS = 40;
    private static final int TIME_TO_USE_MELEE_UNTIL_SONIC_BOOM = 200;
    private static final int MAX_HEALTH = 500;
    private static final float MOVEMENT_SPEED_WHEN_FIGHTING = 0.3F;
    private static final float KNOCKBACK_RESISTANCE = 1.0F;
    private static final float ATTACK_KNOCKBACK = 1.5F;
    private static final int ATTACK_DAMAGE = 30;
    private static final int FOLLOW_RANGE = 24;
    private static final EntityDataAccessor<Integer> CLIENT_ANGER_LEVEL = SynchedEntityData.defineId(Warden.class, EntityDataSerializers.INT);
    private static final int DARKNESS_DISPLAY_LIMIT = 200;
    private static final int DARKNESS_DURATION = 260;
    private static final int DARKNESS_RADIUS = 20;
    private static final int DARKNESS_INTERVAL = 120;
    private static final int ANGERMANAGEMENT_TICK_DELAY = 20;
    private static final int DEFAULT_ANGER = 35;
    private static final int PROJECTILE_ANGER = 10;
    private static final int ON_HURT_ANGER_BOOST = 20;
    private static final int RECENT_PROJECTILE_TICK_THRESHOLD = 100;
    private static final int TOUCH_COOLDOWN_TICKS = 20;
    private static final int DIGGING_PARTICLES_AMOUNT = 30;
    private static final float DIGGING_PARTICLES_DURATION = 4.5F;
    private static final float DIGGING_PARTICLES_OFFSET = 0.7F;
    private static final int PROJECTILE_ANGER_DISTANCE = 30;
    private int tendrilAnimation;
    private int tendrilAnimationO;
    private int heartAnimation;
    private int heartAnimationO;
    public AnimationState roarAnimationState = new AnimationState();
    public AnimationState sniffAnimationState = new AnimationState();
    public AnimationState emergeAnimationState = new AnimationState();
    public AnimationState diggingAnimationState = new AnimationState();
    public AnimationState attackAnimationState = new AnimationState();
    public AnimationState sonicBoomAnimationState = new AnimationState();
    private final DynamicGameEventListener<VibrationSystem.Listener> dynamicGameEventListener;
    private final VibrationSystem.User vibrationUser;
    private VibrationSystem.Data vibrationData;
    AngerManagement angerManagement = new AngerManagement(this::canTargetEntity, Collections.emptyList());

    public Warden(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.vibrationUser = new Warden.VibrationUser();
        this.vibrationData = new VibrationSystem.Data();
        this.dynamicGameEventListener = new DynamicGameEventListener<>(new VibrationSystem.Listener(this));
        this.xpReward = 5;
        this.getNavigation().setCanFloat(true);
        this.setPathfindingMalus(PathType.UNPASSABLE_RAIL, 0.0F);
        this.setPathfindingMalus(PathType.DAMAGE_OTHER, 8.0F);
        this.setPathfindingMalus(PathType.POWDER_SNOW, 8.0F);
        this.setPathfindingMalus(PathType.LAVA, 8.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity, this.hasPose(Pose.EMERGING) ? 1 : 0);
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        if (packet.getData() == 1) {
            this.setPose(Pose.EMERGING);
        }
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return super.checkSpawnObstruction(level) && level.noCollision(this, this.getType().getDimensions().makeBoundingBox(this.position()));
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0F;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource damageSource) {
        return this.isDiggingOrEmerging() && !damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(level, damageSource);
    }

    boolean isDiggingOrEmerging() {
        return this.hasPose(Pose.DIGGING) || this.hasPose(Pose.EMERGING);
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean canDisableShield() {
        return true;
    }

    @Override
    protected float nextStep() {
        return this.moveDist + 0.55F;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 500.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.ATTACK_KNOCKBACK, 1.5)
            .add(Attributes.ATTACK_DAMAGE, 30.0)
            .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    public boolean dampensVibrations() {
        return true;
    }

    @Override
    public float getSoundVolume() {
        return 4.0F;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return !this.hasPose(Pose.ROARING) && !this.isDiggingOrEmerging() ? this.getAngerLevel().getAmbientSound() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.WARDEN_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.WARDEN_STEP, 10.0F, 1.0F);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity source) {
        level.broadcastEntityEvent(this, (byte)4);
        this.playSound(SoundEvents.WARDEN_ATTACK_IMPACT, 10.0F, this.getVoicePitch());
        SonicBoom.setCooldown(this, 40);
        return super.doHurtTarget(level, source);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CLIENT_ANGER_LEVEL, 0);
    }

    public int getClientAngerLevel() {
        return this.entityData.get(CLIENT_ANGER_LEVEL);
    }

    private void syncClientAngerLevel() {
        this.entityData.set(CLIENT_ANGER_LEVEL, this.getActiveAnger());
    }

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel serverLevel) {
            VibrationSystem.Ticker.tick(serverLevel, this.vibrationData, this.vibrationUser);
            if (this.isPersistenceRequired() || this.requiresCustomPersistence()) {
                WardenAi.setDigCooldown(this);
            }
        }

        super.tick();
        if (this.level().isClientSide()) {
            if (this.tickCount % this.getHeartBeatDelay() == 0) {
                this.heartAnimation = 10;
                if (!this.isSilent()) {
                    this.level()
                        .playLocalSound(
                            this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_HEARTBEAT, this.getSoundSource(), 5.0F, this.getVoicePitch(), false
                        );
                }
            }

            this.tendrilAnimationO = this.tendrilAnimation;
            if (this.tendrilAnimation > 0) {
                this.tendrilAnimation--;
            }

            this.heartAnimationO = this.heartAnimation;
            if (this.heartAnimation > 0) {
                this.heartAnimation--;
            }

            switch (this.getPose()) {
                case EMERGING:
                    this.clientDiggingParticles(this.emergeAnimationState);
                    break;
                case DIGGING:
                    this.clientDiggingParticles(this.diggingAnimationState);
            }
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("wardenBrain");
        this.getBrain().tick(level, this);
        profilerFiller.pop();
        super.customServerAiStep(level);
        if ((this.tickCount + this.getId()) % 120 == 0) {
            applyDarknessAround(level, this.position(), this, 20);
        }

        if (this.tickCount % 20 == 0) {
            this.angerManagement.tick(level, this::canTargetEntity);
            this.syncClientAngerLevel();
        }

        WardenAi.updateActivity(this);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) {
            this.roarAnimationState.stop();
            this.attackAnimationState.start(this.tickCount);
        } else if (id == 61) {
            this.tendrilAnimation = 10;
        } else if (id == 62) {
            this.sonicBoomAnimationState.start(this.tickCount);
        } else {
            super.handleEntityEvent(id);
        }
    }

    private int getHeartBeatDelay() {
        float f = (float)this.getClientAngerLevel() / AngerLevel.ANGRY.getMinimumAnger();
        return 40 - Mth.floor(Mth.clamp(f, 0.0F, 1.0F) * 30.0F);
    }

    public float getTendrilAnimation(float partialTick) {
        return Mth.lerp(partialTick, (float)this.tendrilAnimationO, (float)this.tendrilAnimation) / 10.0F;
    }

    public float getHeartAnimation(float partialTick) {
        return Mth.lerp(partialTick, (float)this.heartAnimationO, (float)this.heartAnimation) / 10.0F;
    }

    private void clientDiggingParticles(AnimationState animationState) {
        if ((float)animationState.getTimeInMillis(this.tickCount) < 4500.0F) {
            RandomSource random = this.getRandom();
            BlockState blockStateOn = this.getBlockStateOn();
            if (blockStateOn.getRenderShape() != RenderShape.INVISIBLE) {
                for (int i = 0; i < 30; i++) {
                    double d = this.getX() + Mth.randomBetween(random, -0.7F, 0.7F);
                    double y = this.getY();
                    double d1 = this.getZ() + Mth.randomBetween(random, -0.7F, 0.7F);
                    this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, blockStateOn), d, y, d1, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_POSE.equals(key)) {
            switch (this.getPose()) {
                case EMERGING:
                    this.emergeAnimationState.start(this.tickCount);
                    break;
                case DIGGING:
                    this.diggingAnimationState.start(this.tickCount);
                    break;
                case ROARING:
                    this.roarAnimationState.start(this.tickCount);
                    break;
                case SNIFFING:
                    this.sniffAnimationState.start(this.tickCount);
            }
        }

        super.onSyncedDataUpdated(key);
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return this.isDiggingOrEmerging();
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return WardenAi.makeBrain(this, dynamic);
    }

    @Override
    public Brain<Warden> getBrain() {
        return (Brain<Warden>)super.getBrain();
    }

    @Override
    protected void sendDebugPackets() {
        super.sendDebugPackets();
        DebugPackets.sendEntityBrain(this);
    }

    @Override
    public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> listenerConsumer) {
        if (this.level() instanceof ServerLevel serverLevel) {
            listenerConsumer.accept(this.dynamicGameEventListener, serverLevel);
        }
    }

    @Contract("null->false")
    public boolean canTargetEntity(@Nullable Entity entity) {
        return entity instanceof LivingEntity livingEntity
            && this.level() == entity.level()
            && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity)
            && !this.isAlliedTo(entity)
            && livingEntity.getType() != EntityType.ARMOR_STAND
            && livingEntity.getType() != EntityType.WARDEN
            && !livingEntity.isInvulnerable()
            && !livingEntity.isDeadOrDying()
            && this.level().getWorldBorder().isWithinBounds(livingEntity.getBoundingBox());
    }

    public static void applyDarknessAround(ServerLevel level, Vec3 pos, @Nullable Entity source, int radius) {
        MobEffectInstance mobEffectInstance = new MobEffectInstance(MobEffects.DARKNESS, 260, 0, false, false);
        MobEffectUtil.addEffectToPlayersAround(level, source, pos, radius, mobEffectInstance, 200, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.WARDEN); // CraftBukkit - Add EntityPotionEffectEvent.Cause
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        RegistryOps<Tag> registryOps = this.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        AngerManagement.codec(this::canTargetEntity)
            .encodeStart(registryOps, this.angerManagement)
            .resultOrPartial(string -> LOGGER.error("Failed to encode anger state for Warden: '{}'", string))
            .ifPresent(tag -> compound.put("anger", tag));
        VibrationSystem.Data.CODEC
            .encodeStart(registryOps, this.vibrationData)
            .resultOrPartial(string -> LOGGER.error("Failed to encode vibration listener for Warden: '{}'", string))
            .ifPresent(tag -> compound.put("listener", tag));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        RegistryOps<Tag> registryOps = this.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        if (compound.contains("anger")) {
            AngerManagement.codec(this::canTargetEntity)
                .parse(registryOps, compound.get("anger"))
                .resultOrPartial(string -> LOGGER.error("Failed to parse anger state for Warden: '{}'", string))
                .ifPresent(angerManagement -> this.angerManagement = angerManagement);
            this.syncClientAngerLevel();
        }

        if (compound.contains("listener", 10)) {
            VibrationSystem.Data.CODEC
                .parse(registryOps, compound.getCompound("listener"))
                .resultOrPartial(string -> LOGGER.error("Failed to parse vibration listener for Warden: '{}'", string))
                .ifPresent(data -> this.vibrationData = data);
        }
    }

    private void playListeningSound() {
        if (!this.hasPose(Pose.ROARING)) {
            this.playSound(this.getAngerLevel().getListeningSound(), 10.0F, this.getVoicePitch());
        }
    }

    public AngerLevel getAngerLevel() {
        return AngerLevel.byAnger(this.getActiveAnger());
    }

    private int getActiveAnger() {
        return this.angerManagement.getActiveAnger(this.getTarget());
    }

    public void clearAnger(Entity entity) {
        this.angerManagement.clearAnger(entity);
    }

    public void increaseAngerAt(@Nullable Entity entity) {
        this.increaseAngerAt(entity, 35, true);
    }

    @VisibleForTesting
    public void increaseAngerAt(@Nullable Entity entity, int offset, boolean playListeningSound) {
        if (!this.isNoAi() && this.canTargetEntity(entity)) {
            // Paper start - Add WardenAngerChangeEvent
            int activeAnger = this.angerManagement.getActiveAnger(entity);
            io.papermc.paper.event.entity.WardenAngerChangeEvent event = new io.papermc.paper.event.entity.WardenAngerChangeEvent((org.bukkit.entity.Warden) this.getBukkitEntity(), entity.getBukkitEntity(), activeAnger, Math.min(150, activeAnger + offset));
            this.level().getCraftServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
            offset = event.getNewAnger() - activeAnger;
            // Paper end - Add WardenAngerChangeEvent
            WardenAi.setDigCooldown(this);
            boolean flag = !(this.getTarget() instanceof Player);
            int i = this.angerManagement.increaseAnger(entity, offset);
            if (entity instanceof Player && flag && AngerLevel.byAnger(i).isAngry()) {
                this.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }

            if (playListeningSound) {
                this.playListeningSound();
            }
        }
    }

    public Optional<LivingEntity> getEntityAngryAt() {
        return this.getAngerLevel().isAngry() ? this.angerManagement.getActiveEntity() : Optional.empty();
    }

    @Nullable
    @Override
    public LivingEntity getTarget() {
        return this.getTargetFromBrain();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        this.getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, 1200L);
        if (spawnReason == EntitySpawnReason.TRIGGERED) {
            this.setPose(Pose.EMERGING);
            this.getBrain().setMemoryWithExpiry(MemoryModuleType.IS_EMERGING, Unit.INSTANCE, WardenAi.EMERGE_DURATION);
            this.playSound(SoundEvents.WARDEN_AGITATED, 5.0F, 1.0F);
        }

        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        boolean flag = super.hurtServer(level, damageSource, amount);
        if (!this.isNoAi() && !this.isDiggingOrEmerging()) {
            Entity entity = damageSource.getEntity();
            this.increaseAngerAt(entity, AngerLevel.ANGRY.getMinimumAnger() + 20, false);
            if (this.brain.getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty()
                && entity instanceof LivingEntity livingEntity
                && (damageSource.isDirect() || this.closerThan(livingEntity, 5.0))) {
                this.setAttackTarget(livingEntity);
            }
        }

        return flag;
    }

    public void setAttackTarget(LivingEntity attackTarget) {
        this.getBrain().eraseMemory(MemoryModuleType.ROAR_TARGET);
        this.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attackTarget);
        this.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        SonicBoom.setCooldown(this, 200);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions entityDimensions = super.getDefaultDimensions(pose);
        return this.isDiggingOrEmerging() ? EntityDimensions.fixed(entityDimensions.width(), 1.0F) : entityDimensions;
    }

    @Override
    public boolean isPushable() {
        return !this.isDiggingOrEmerging() && super.isPushable();
    }

    @Override
    protected void doPush(Entity entity) {
        if (!this.isNoAi() && !this.getBrain().hasMemoryValue(MemoryModuleType.TOUCH_COOLDOWN)) {
            this.getBrain().setMemoryWithExpiry(MemoryModuleType.TOUCH_COOLDOWN, Unit.INSTANCE, 20L);
            this.increaseAngerAt(entity);
            WardenAi.setDisturbanceLocation(this, entity.blockPosition());
        }

        super.doPush(entity);
    }

    @VisibleForTesting
    public AngerManagement getAngerManagement() {
        return this.angerManagement;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new GroundPathNavigation(this, level) {
            @Override
            protected PathFinder createPathFinder(int maxVisitedNodes) {
                this.nodeEvaluator = new WalkNodeEvaluator();
                return new PathFinder(this.nodeEvaluator, maxVisitedNodes) {
                    @Override
                    protected float distance(Node first, Node second) {
                        return first.distanceToXZ(second);
                    }
                };
            }
        };
    }

    @Override
    public VibrationSystem.Data getVibrationData() {
        return this.vibrationData;
    }

    @Override
    public VibrationSystem.User getVibrationUser() {
        return this.vibrationUser;
    }

    class VibrationUser implements VibrationSystem.User {
        private static final int GAME_EVENT_LISTENER_RANGE = 16;
        private final PositionSource positionSource = new EntityPositionSource(Warden.this, Warden.this.getEyeHeight());

        @Override
        public int getListenerRadius() {
            return 16;
        }

        @Override
        public PositionSource getPositionSource() {
            return this.positionSource;
        }

        @Override
        public TagKey<GameEvent> getListenableEvents() {
            return GameEventTags.WARDEN_CAN_LISTEN;
        }

        @Override
        public boolean canTriggerAvoidVibration() {
            return true;
        }

        @Override
        public boolean canReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent, GameEvent.Context context) {
            return !Warden.this.isNoAi()
                && !Warden.this.isDeadOrDying()
                && !Warden.this.getBrain().hasMemoryValue(MemoryModuleType.VIBRATION_COOLDOWN)
                && !Warden.this.isDiggingOrEmerging()
                && level.getWorldBorder().isWithinBounds(pos)
                && !(context.sourceEntity() instanceof LivingEntity livingEntity && !Warden.this.canTargetEntity(livingEntity));
        }

        @Override
        public void onReceiveVibration(
            ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent, @Nullable Entity entity, @Nullable Entity playerEntity, float distance
        ) {
            if (!Warden.this.isDeadOrDying()) {
                Warden.this.brain.setMemoryWithExpiry(MemoryModuleType.VIBRATION_COOLDOWN, Unit.INSTANCE, 40L);
                level.broadcastEntityEvent(Warden.this, (byte)61);
                Warden.this.playSound(SoundEvents.WARDEN_TENDRIL_CLICKS, 5.0F, Warden.this.getVoicePitch());
                BlockPos blockPos = pos;
                if (playerEntity != null) {
                    if (Warden.this.closerThan(playerEntity, 30.0)) {
                        if (Warden.this.getBrain().hasMemoryValue(MemoryModuleType.RECENT_PROJECTILE)) {
                            if (Warden.this.canTargetEntity(playerEntity)) {
                                blockPos = playerEntity.blockPosition();
                            }

                            Warden.this.increaseAngerAt(playerEntity);
                        } else {
                            Warden.this.increaseAngerAt(playerEntity, 10, true);
                        }
                    }

                    Warden.this.getBrain().setMemoryWithExpiry(MemoryModuleType.RECENT_PROJECTILE, Unit.INSTANCE, 100L);
                } else {
                    Warden.this.increaseAngerAt(entity);
                }

                if (!Warden.this.getAngerLevel().isAngry()) {
                    Optional<LivingEntity> activeEntity = Warden.this.angerManagement.getActiveEntity();
                    if (playerEntity != null || activeEntity.isEmpty() || activeEntity.get() == entity) {
                        WardenAi.setDisturbanceLocation(Warden.this, blockPos);
                    }
                }
            }
        }
    }
}
