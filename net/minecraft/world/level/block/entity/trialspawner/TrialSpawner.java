package net.minecraft.world.level.block.entity.trialspawner;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class TrialSpawner {
    public static final String NORMAL_CONFIG_TAG_NAME = "normal_config";
    public static final String OMINOUS_CONFIG_TAG_NAME = "ominous_config";
    public static final int DETECT_PLAYER_SPAWN_BUFFER = 40;
    private static final int DEFAULT_TARGET_COOLDOWN_LENGTH = 36000;
    private static final int DEFAULT_PLAYER_SCAN_RANGE = 14;
    private static final int MAX_MOB_TRACKING_DISTANCE = 47;
    private static final int MAX_MOB_TRACKING_DISTANCE_SQR = Mth.square(47);
    private static final float SPAWNING_AMBIENT_SOUND_CHANCE = 0.02F;
    private Holder<TrialSpawnerConfig> normalConfig;
    private Holder<TrialSpawnerConfig> ominousConfig;
    private final TrialSpawnerData data;
    private final int requiredPlayerRange;
    private final int targetCooldownLength;
    private final TrialSpawner.StateAccessor stateAccessor;
    private PlayerDetector playerDetector;
    private final PlayerDetector.EntitySelector entitySelector;
    private boolean overridePeacefulAndMobSpawnRule;
    private boolean isOminous;

    public Codec<TrialSpawner> codec() {
        return RecordCodecBuilder.create(
            instance -> instance.group(
                    TrialSpawnerConfig.CODEC
                        .optionalFieldOf("normal_config", Holder.direct(TrialSpawnerConfig.DEFAULT))
                        .forGetter(trialSpawner -> trialSpawner.normalConfig),
                    TrialSpawnerConfig.CODEC
                        .optionalFieldOf("ominous_config", Holder.direct(TrialSpawnerConfig.DEFAULT))
                        .forGetter(trialSpawner -> trialSpawner.ominousConfig),
                    TrialSpawnerData.MAP_CODEC.forGetter(TrialSpawner::getData),
                    Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("target_cooldown_length", 36000).forGetter(TrialSpawner::getTargetCooldownLength),
                    Codec.intRange(1, 128).optionalFieldOf("required_player_range", 14).forGetter(TrialSpawner::getRequiredPlayerRange)
                )
                .apply(
                    instance,
                    (holder, holder1, trialSpawnerData, integer, integer1) -> new TrialSpawner(
                        holder, holder1, trialSpawnerData, integer, integer1, this.stateAccessor, this.playerDetector, this.entitySelector
                    )
                )
        );
    }

    public TrialSpawner(TrialSpawner.StateAccessor stateAccessor, PlayerDetector playerDetector, PlayerDetector.EntitySelector entitySelector) {
        this(
            Holder.direct(TrialSpawnerConfig.DEFAULT),
            Holder.direct(TrialSpawnerConfig.DEFAULT),
            new TrialSpawnerData(),
            36000,
            14,
            stateAccessor,
            playerDetector,
            entitySelector
        );
    }

    public TrialSpawner(
        Holder<TrialSpawnerConfig> normalConfig,
        Holder<TrialSpawnerConfig> ominousConfig,
        TrialSpawnerData data,
        int targetCooldownLength,
        int requiredPlayerRange,
        TrialSpawner.StateAccessor stateAccessor,
        PlayerDetector playerDetector,
        PlayerDetector.EntitySelector entitySelector
    ) {
        this.normalConfig = normalConfig;
        this.ominousConfig = ominousConfig;
        this.data = data;
        this.targetCooldownLength = targetCooldownLength;
        this.requiredPlayerRange = requiredPlayerRange;
        this.stateAccessor = stateAccessor;
        this.playerDetector = playerDetector;
        this.entitySelector = entitySelector;
    }

    public TrialSpawnerConfig getConfig() {
        return this.isOminous ? this.getOminousConfig() : this.getNormalConfig();
    }

    @VisibleForTesting
    public TrialSpawnerConfig getNormalConfig() {
        return this.normalConfig.value();
    }

    @VisibleForTesting
    public TrialSpawnerConfig getOminousConfig() {
        return this.ominousConfig.value();
    }

    public void applyOminous(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, level.getBlockState(pos).setValue(TrialSpawnerBlock.OMINOUS, Boolean.valueOf(true)), 3);
        level.levelEvent(3020, pos, 1);
        this.isOminous = true;
        this.data.resetAfterBecomingOminous(this, level);
    }

    public void removeOminous(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, level.getBlockState(pos).setValue(TrialSpawnerBlock.OMINOUS, Boolean.valueOf(false)), 3);
        this.isOminous = false;
    }

    public boolean isOminous() {
        return this.isOminous;
    }

    public TrialSpawnerData getData() {
        return this.data;
    }

    public int getTargetCooldownLength() {
        return this.targetCooldownLength;
    }

    public int getRequiredPlayerRange() {
        return this.requiredPlayerRange;
    }

    public TrialSpawnerState getState() {
        return this.stateAccessor.getState();
    }

    public void setState(Level level, TrialSpawnerState state) {
        this.stateAccessor.setState(level, state);
    }

    public void markUpdated() {
        this.stateAccessor.markUpdated();
    }

    public PlayerDetector getPlayerDetector() {
        return this.playerDetector;
    }

    public PlayerDetector.EntitySelector getEntitySelector() {
        return this.entitySelector;
    }

    public boolean canSpawnInLevel(ServerLevel level) {
        return this.overridePeacefulAndMobSpawnRule
            || level.getDifficulty() != Difficulty.PEACEFUL && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
    }

    public Optional<UUID> spawnMob(ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        SpawnData nextSpawnData = this.data.getOrCreateNextSpawnData(this, level.getRandom());
        CompoundTag compoundTag = nextSpawnData.entityToSpawn();
        ListTag list = compoundTag.getList("Pos", 6);
        Optional<EntityType<?>> optional = EntityType.by(compoundTag);
        if (optional.isEmpty()) {
            return Optional.empty();
        } else {
            int size = list.size();
            double d = size >= 1 ? list.getDouble(0) : pos.getX() + (random.nextDouble() - random.nextDouble()) * this.getConfig().spawnRange() + 0.5;
            double d1 = size >= 2 ? list.getDouble(1) : pos.getY() + random.nextInt(3) - 1;
            double d2 = size >= 3 ? list.getDouble(2) : pos.getZ() + (random.nextDouble() - random.nextDouble()) * this.getConfig().spawnRange() + 0.5;
            if (!level.noCollision(optional.get().getSpawnAABB(d, d1, d2))) {
                return Optional.empty();
            } else {
                Vec3 vec3 = new Vec3(d, d1, d2);
                if (!inLineOfSight(level, pos.getCenter(), vec3)) {
                    return Optional.empty();
                } else {
                    BlockPos blockPos = BlockPos.containing(vec3);
                    if (!SpawnPlacements.checkSpawnRules(optional.get(), level, EntitySpawnReason.TRIAL_SPAWNER, blockPos, level.getRandom())) {
                        return Optional.empty();
                    } else {
                        if (nextSpawnData.getCustomSpawnRules().isPresent()) {
                            SpawnData.CustomSpawnRules customSpawnRules = nextSpawnData.getCustomSpawnRules().get();
                            if (!customSpawnRules.isValidPosition(blockPos, level)) {
                                return Optional.empty();
                            }
                        }

                        Entity entity = EntityType.loadEntityRecursive(compoundTag, level, EntitySpawnReason.TRIAL_SPAWNER, entity1 -> {
                            entity1.moveTo(d, d1, d2, random.nextFloat() * 360.0F, 0.0F);
                            return entity1;
                        });
                        if (entity == null) {
                            return Optional.empty();
                        } else {
                            if (entity instanceof Mob mob) {
                                if (!mob.checkSpawnObstruction(level)) {
                                    return Optional.empty();
                                }

                                boolean flag = nextSpawnData.getEntityToSpawn().size() == 1 && nextSpawnData.getEntityToSpawn().contains("id", 8);
                                if (flag) {
                                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.TRIAL_SPAWNER, null);
                                }

                                mob.setPersistenceRequired();
                                nextSpawnData.getEquipment().ifPresent(mob::equip);
                            }

                            if (!level.tryAddFreshEntityWithPassengers(entity)) {
                                return Optional.empty();
                            } else {
                                TrialSpawner.FlameParticle flameParticle = this.isOminous
                                    ? TrialSpawner.FlameParticle.OMINOUS
                                    : TrialSpawner.FlameParticle.NORMAL;
                                level.levelEvent(3011, pos, flameParticle.encode());
                                level.levelEvent(3012, blockPos, flameParticle.encode());
                                level.gameEvent(entity, GameEvent.ENTITY_PLACE, blockPos);
                                return Optional.of(entity.getUUID());
                            }
                        }
                    }
                }
            }
        }
    }

    public void ejectReward(ServerLevel level, BlockPos pos, ResourceKey<LootTable> lootTable) {
        LootTable lootTable1 = level.getServer().reloadableRegistries().getLootTable(lootTable);
        LootParams lootParams = new LootParams.Builder(level).create(LootContextParamSets.EMPTY);
        ObjectArrayList<ItemStack> randomItems = lootTable1.getRandomItems(lootParams);
        if (!randomItems.isEmpty()) {
            for (ItemStack itemStack : randomItems) {
                DefaultDispenseItemBehavior.spawnItem(level, itemStack, 2, Direction.UP, Vec3.atBottomCenterOf(pos).relative(Direction.UP, 1.2));
            }

            level.levelEvent(3014, pos, 0);
        }
    }

    public void tickClient(Level level, BlockPos pos, boolean isOminous) {
        TrialSpawnerState state = this.getState();
        state.emitParticles(level, pos, isOminous);
        if (state.hasSpinningMob()) {
            double d = Math.max(0L, this.data.nextMobSpawnsAt - level.getGameTime());
            this.data.oSpin = this.data.spin;
            this.data.spin = (this.data.spin + state.spinningMobSpeed() / (d + 200.0)) % 360.0;
        }

        if (state.isCapableOfSpawning()) {
            RandomSource random = level.getRandom();
            if (random.nextFloat() <= 0.02F) {
                SoundEvent soundEvent = isOminous ? SoundEvents.TRIAL_SPAWNER_AMBIENT_OMINOUS : SoundEvents.TRIAL_SPAWNER_AMBIENT;
                level.playLocalSound(pos, soundEvent, SoundSource.BLOCKS, random.nextFloat() * 0.25F + 0.75F, random.nextFloat() + 0.5F, false);
            }
        }
    }

    public void tickServer(ServerLevel level, BlockPos pos, boolean isOminous) {
        this.isOminous = isOminous;
        TrialSpawnerState state = this.getState();
        if (this.data.currentMobs.removeIf(uuid -> shouldMobBeUntracked(level, pos, uuid))) {
            this.data.nextMobSpawnsAt = level.getGameTime() + this.getConfig().ticksBetweenSpawn();
        }

        TrialSpawnerState trialSpawnerState = state.tickAndGetNext(pos, this, level);
        if (trialSpawnerState != state) {
            this.setState(level, trialSpawnerState);
        }
    }

    private static boolean shouldMobBeUntracked(ServerLevel level, BlockPos pos, UUID uuid) {
        Entity entity = level.getEntity(uuid);
        return entity == null
            || !entity.isAlive()
            || !entity.level().dimension().equals(level.dimension())
            || entity.blockPosition().distSqr(pos) > MAX_MOB_TRACKING_DISTANCE_SQR;
    }

    private static boolean inLineOfSight(Level level, Vec3 spawnerPos, Vec3 mobPos) {
        BlockHitResult blockHitResult = level.clip(
            new ClipContext(mobPos, spawnerPos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty())
        );
        return blockHitResult.getBlockPos().equals(BlockPos.containing(spawnerPos)) || blockHitResult.getType() == HitResult.Type.MISS;
    }

    public static void addSpawnParticles(Level level, BlockPos pos, RandomSource random, SimpleParticleType particleType) {
        for (int i = 0; i < 20; i++) {
            double d = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 2.0;
            double d1 = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 2.0;
            double d2 = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 2.0;
            level.addParticle(ParticleTypes.SMOKE, d, d1, d2, 0.0, 0.0, 0.0);
            level.addParticle(particleType, d, d1, d2, 0.0, 0.0, 0.0);
        }
    }

    public static void addBecomeOminousParticles(Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 20; i++) {
            double d = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 2.0;
            double d1 = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 2.0;
            double d2 = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 2.0;
            double d3 = random.nextGaussian() * 0.02;
            double d4 = random.nextGaussian() * 0.02;
            double d5 = random.nextGaussian() * 0.02;
            level.addParticle(ParticleTypes.TRIAL_OMEN, d, d1, d2, d3, d4, d5);
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, d, d1, d2, d3, d4, d5);
        }
    }

    public static void addDetectPlayerParticles(Level level, BlockPos pos, RandomSource random, int type, ParticleOptions particle) {
        for (int i = 0; i < 30 + Math.min(type, 10) * 5; i++) {
            double d = (2.0F * random.nextFloat() - 1.0F) * 0.65;
            double d1 = (2.0F * random.nextFloat() - 1.0F) * 0.65;
            double d2 = pos.getX() + 0.5 + d;
            double d3 = pos.getY() + 0.1 + random.nextFloat() * 0.8;
            double d4 = pos.getZ() + 0.5 + d1;
            level.addParticle(particle, d2, d3, d4, 0.0, 0.0, 0.0);
        }
    }

    public static void addEjectItemParticles(Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 20; i++) {
            double d = pos.getX() + 0.4 + random.nextDouble() * 0.2;
            double d1 = pos.getY() + 0.4 + random.nextDouble() * 0.2;
            double d2 = pos.getZ() + 0.4 + random.nextDouble() * 0.2;
            double d3 = random.nextGaussian() * 0.02;
            double d4 = random.nextGaussian() * 0.02;
            double d5 = random.nextGaussian() * 0.02;
            level.addParticle(ParticleTypes.SMALL_FLAME, d, d1, d2, d3, d4, d5 * 0.25);
            level.addParticle(ParticleTypes.SMOKE, d, d1, d2, d3, d4, d5);
        }
    }

    public void overrideEntityToSpawn(EntityType<?> entityType, Level level) {
        this.data.reset();
        this.normalConfig = Holder.direct(this.normalConfig.value().withSpawning(entityType));
        this.ominousConfig = Holder.direct(this.ominousConfig.value().withSpawning(entityType));
        this.setState(level, TrialSpawnerState.INACTIVE);
    }

    @Deprecated(
        forRemoval = true
    )
    @VisibleForTesting
    public void setPlayerDetector(PlayerDetector playerDetector) {
        this.playerDetector = playerDetector;
    }

    @Deprecated(
        forRemoval = true
    )
    @VisibleForTesting
    public void overridePeacefulAndMobSpawnRule() {
        this.overridePeacefulAndMobSpawnRule = true;
    }

    public static enum FlameParticle {
        NORMAL(ParticleTypes.FLAME),
        OMINOUS(ParticleTypes.SOUL_FIRE_FLAME);

        public final SimpleParticleType particleType;

        private FlameParticle(final SimpleParticleType particleType) {
            this.particleType = particleType;
        }

        public static TrialSpawner.FlameParticle decode(int id) {
            TrialSpawner.FlameParticle[] flameParticles = values();
            return id <= flameParticles.length && id >= 0 ? flameParticles[id] : NORMAL;
        }

        public int encode() {
            return this.ordinal();
        }
    }

    public interface StateAccessor {
        void setState(Level level, TrialSpawnerState state);

        TrialSpawnerState getState();

        void markUpdated();
    }
}
