package net.minecraft.world.level.block.entity.trialspawner;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OminousItemSpawner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public enum TrialSpawnerState implements StringRepresentable {
    INACTIVE("inactive", 0, TrialSpawnerState.ParticleEmission.NONE, -1.0, false),
    WAITING_FOR_PLAYERS("waiting_for_players", 4, TrialSpawnerState.ParticleEmission.SMALL_FLAMES, 200.0, true),
    ACTIVE("active", 8, TrialSpawnerState.ParticleEmission.FLAMES_AND_SMOKE, 1000.0, true),
    WAITING_FOR_REWARD_EJECTION("waiting_for_reward_ejection", 8, TrialSpawnerState.ParticleEmission.SMALL_FLAMES, -1.0, false),
    EJECTING_REWARD("ejecting_reward", 8, TrialSpawnerState.ParticleEmission.SMALL_FLAMES, -1.0, false),
    COOLDOWN("cooldown", 0, TrialSpawnerState.ParticleEmission.SMOKE_INSIDE_AND_TOP_FACE, -1.0, false);

    private static final float DELAY_BEFORE_EJECT_AFTER_KILLING_LAST_MOB = 40.0F;
    private static final int TIME_BETWEEN_EACH_EJECTION = Mth.floor(30.0F);
    private final String name;
    private final int lightLevel;
    private final double spinningMobSpeed;
    private final TrialSpawnerState.ParticleEmission particleEmission;
    private final boolean isCapableOfSpawning;

    private TrialSpawnerState(
        final String name,
        final int lightLevel,
        final TrialSpawnerState.ParticleEmission particleEmission,
        final double spinningMobSpeed,
        final boolean isCapableOfSpawning
    ) {
        this.name = name;
        this.lightLevel = lightLevel;
        this.particleEmission = particleEmission;
        this.spinningMobSpeed = spinningMobSpeed;
        this.isCapableOfSpawning = isCapableOfSpawning;
    }

    TrialSpawnerState tickAndGetNext(BlockPos pos, TrialSpawner spawner, ServerLevel level) {
        TrialSpawnerData data = spawner.getData();
        TrialSpawnerConfig config = spawner.getConfig();

        return switch (this) {
            case INACTIVE -> data.getOrCreateDisplayEntity(spawner, level, WAITING_FOR_PLAYERS) == null ? this : WAITING_FOR_PLAYERS;
            case WAITING_FOR_PLAYERS -> {
                if (!spawner.canSpawnInLevel(level)) {
                    data.resetStatistics();
                    yield this;
                } else if (!data.hasMobToSpawn(spawner, level.random)) {
                    yield INACTIVE;
                } else {
                    data.tryDetectPlayers(level, pos, spawner);
                    yield data.detectedPlayers.isEmpty() ? this : ACTIVE;
                }
            }
            case ACTIVE -> {
                if (!spawner.canSpawnInLevel(level)) {
                    data.resetStatistics();
                    yield WAITING_FOR_PLAYERS;
                } else if (!data.hasMobToSpawn(spawner, level.random)) {
                    yield INACTIVE;
                } else {
                    int i = data.countAdditionalPlayers(pos);
                    data.tryDetectPlayers(level, pos, spawner);
                    if (spawner.isOminous()) {
                        this.spawnOminousOminousItemSpawner(level, pos, spawner);
                    }

                    if (data.hasFinishedSpawningAllMobs(config, i)) {
                        if (data.haveAllCurrentMobsDied()) {
                            data.cooldownEndsAt = level.getGameTime() + spawner.getTargetCooldownLength();
                            data.totalMobsSpawned = 0;
                            data.nextMobSpawnsAt = 0L;
                            yield WAITING_FOR_REWARD_EJECTION;
                        }
                    } else if (data.isReadyToSpawnNextMob(level, config, i)) {
                        spawner.spawnMob(level, pos).ifPresent(uuid -> {
                            data.currentMobs.add(uuid);
                            data.totalMobsSpawned++;
                            data.nextMobSpawnsAt = level.getGameTime() + config.ticksBetweenSpawn();
                            config.spawnPotentialsDefinition().getRandom(level.getRandom()).ifPresent(wrapper -> {
                                data.nextSpawnData = Optional.of(wrapper.data());
                                spawner.markUpdated();
                            });
                        });
                    }

                    yield this;
                }
            }
            case WAITING_FOR_REWARD_EJECTION -> {
                if (data.isReadyToOpenShutter(level, 40.0F, spawner.getTargetCooldownLength())) {
                    level.playSound(null, pos, SoundEvents.TRIAL_SPAWNER_OPEN_SHUTTER, SoundSource.BLOCKS);
                    yield EJECTING_REWARD;
                } else {
                    yield this;
                }
            }
            case EJECTING_REWARD -> {
                if (!data.isReadyToEjectItems(level, TIME_BETWEEN_EACH_EJECTION, spawner.getTargetCooldownLength())) {
                    yield this;
                } else if (data.detectedPlayers.isEmpty()) {
                    level.playSound(null, pos, SoundEvents.TRIAL_SPAWNER_CLOSE_SHUTTER, SoundSource.BLOCKS);
                    data.ejectingLootTable = Optional.empty();
                    yield COOLDOWN;
                } else {
                    if (data.ejectingLootTable.isEmpty()) {
                        data.ejectingLootTable = config.lootTablesToEject().getRandomValue(level.getRandom());
                    }

                    data.ejectingLootTable.ifPresent(resourceKey -> spawner.ejectReward(level, pos, (ResourceKey<LootTable>)resourceKey));
                    data.detectedPlayers.remove(data.detectedPlayers.iterator().next());
                    yield this;
                }
            }
            case COOLDOWN -> {
                data.tryDetectPlayers(level, pos, spawner);
                if (!data.detectedPlayers.isEmpty()) {
                    data.totalMobsSpawned = 0;
                    data.nextMobSpawnsAt = 0L;
                    yield ACTIVE;
                } else if (data.isCooldownFinished(level)) {
                    spawner.removeOminous(level, pos);
                    data.reset();
                    yield WAITING_FOR_PLAYERS;
                } else {
                    yield this;
                }
            }
        };
    }

    private void spawnOminousOminousItemSpawner(ServerLevel level, BlockPos pos, TrialSpawner spawner) {
        TrialSpawnerData data = spawner.getData();
        TrialSpawnerConfig config = spawner.getConfig();
        ItemStack itemStack = data.getDispensingItems(level, config, pos).getRandomValue(level.random).orElse(ItemStack.EMPTY);
        if (!itemStack.isEmpty()) {
            if (this.timeToSpawnItemSpawner(level, data)) {
                calculatePositionToSpawnSpawner(level, pos, spawner, data).ifPresent(vec3 -> {
                    OminousItemSpawner ominousItemSpawner = OminousItemSpawner.create(level, itemStack);
                    ominousItemSpawner.moveTo(vec3);
                    level.addFreshEntity(ominousItemSpawner);
                    float f = (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.2F + 1.0F;
                    level.playSound(null, BlockPos.containing(vec3), SoundEvents.TRIAL_SPAWNER_SPAWN_ITEM_BEGIN, SoundSource.BLOCKS, 1.0F, f);
                    data.cooldownEndsAt = level.getGameTime() + spawner.getOminousConfig().ticksBetweenItemSpawners();
                });
            }
        }
    }

    private static Optional<Vec3> calculatePositionToSpawnSpawner(ServerLevel level, BlockPos pos, TrialSpawner spawner, TrialSpawnerData spawnerData) {
        List<Player> list = spawnerData.detectedPlayers
            .stream()
            .map(level::getPlayerByUUID)
            .filter(Objects::nonNull)
            .filter(
                player -> !player.isCreative()
                    && !player.isSpectator()
                    && player.isAlive()
                    && player.distanceToSqr(pos.getCenter()) <= Mth.square(spawner.getRequiredPlayerRange())
            )
            .toList();
        if (list.isEmpty()) {
            return Optional.empty();
        } else {
            Entity entity = selectEntityToSpawnItemAbove(list, spawnerData.currentMobs, spawner, pos, level);
            return entity == null ? Optional.empty() : calculatePositionAbove(entity, level);
        }
    }

    private static Optional<Vec3> calculatePositionAbove(Entity entity, ServerLevel level) {
        Vec3 vec3 = entity.position();
        Vec3 vec31 = vec3.relative(Direction.UP, entity.getBbHeight() + 2.0F + level.random.nextInt(4));
        BlockHitResult blockHitResult = level.clip(new ClipContext(vec3, vec31, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 vec32 = blockHitResult.getBlockPos().getCenter().relative(Direction.DOWN, 1.0);
        BlockPos blockPos = BlockPos.containing(vec32);
        return !level.getBlockState(blockPos).getCollisionShape(level, blockPos).isEmpty() ? Optional.empty() : Optional.of(vec32);
    }

    @Nullable
    private static Entity selectEntityToSpawnItemAbove(List<Player> player, Set<UUID> currentMobs, TrialSpawner spawner, BlockPos pos, ServerLevel level) {
        Stream<Entity> stream = currentMobs.stream()
            .map(level::getEntity)
            .filter(Objects::nonNull)
            .filter(entity -> entity.isAlive() && entity.distanceToSqr(pos.getCenter()) <= Mth.square(spawner.getRequiredPlayerRange()));
        List<? extends Entity> list = level.random.nextBoolean() ? stream.toList() : player;
        if (list.isEmpty()) {
            return null;
        } else {
            return list.size() == 1 ? list.getFirst() : Util.getRandom(list, level.random);
        }
    }

    private boolean timeToSpawnItemSpawner(ServerLevel level, TrialSpawnerData spawnerData) {
        return level.getGameTime() >= spawnerData.cooldownEndsAt;
    }

    public int lightLevel() {
        return this.lightLevel;
    }

    public double spinningMobSpeed() {
        return this.spinningMobSpeed;
    }

    public boolean hasSpinningMob() {
        return this.spinningMobSpeed >= 0.0;
    }

    public boolean isCapableOfSpawning() {
        return this.isCapableOfSpawning;
    }

    public void emitParticles(Level level, BlockPos pos, boolean isOminous) {
        this.particleEmission.emit(level, level.getRandom(), pos, isOminous);
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    static class LightLevel {
        private static final int UNLIT = 0;
        private static final int HALF_LIT = 4;
        private static final int LIT = 8;

        private LightLevel() {
        }
    }

    interface ParticleEmission {
        TrialSpawnerState.ParticleEmission NONE = (level, random, pos, isOminous) -> {};
        TrialSpawnerState.ParticleEmission SMALL_FLAMES = (level, random, pos, isOminous) -> {
            if (random.nextInt(2) == 0) {
                Vec3 vec3 = pos.getCenter().offsetRandom(random, 0.9F);
                addParticle(isOminous ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMALL_FLAME, vec3, level);
            }
        };
        TrialSpawnerState.ParticleEmission FLAMES_AND_SMOKE = (level, random, pos, isOminous) -> {
            Vec3 vec3 = pos.getCenter().offsetRandom(random, 1.0F);
            addParticle(ParticleTypes.SMOKE, vec3, level);
            addParticle(isOminous ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, vec3, level);
        };
        TrialSpawnerState.ParticleEmission SMOKE_INSIDE_AND_TOP_FACE = (level, random, pos, isOminous) -> {
            Vec3 vec3 = pos.getCenter().offsetRandom(random, 0.9F);
            if (random.nextInt(3) == 0) {
                addParticle(ParticleTypes.SMOKE, vec3, level);
            }

            if (level.getGameTime() % 20L == 0L) {
                Vec3 vec31 = pos.getCenter().add(0.0, 0.5, 0.0);
                int i = level.getRandom().nextInt(4) + 20;

                for (int i1 = 0; i1 < i; i1++) {
                    addParticle(ParticleTypes.SMOKE, vec31, level);
                }
            }
        };

        private static void addParticle(SimpleParticleType particleType, Vec3 pos, Level level) {
            level.addParticle(particleType, pos.x(), pos.y(), pos.z(), 0.0, 0.0, 0.0);
        }

        void emit(Level level, RandomSource random, BlockPos pos, boolean isOminous);
    }

    static class SpinningMob {
        private static final double NONE = -1.0;
        private static final double SLOW = 200.0;
        private static final double FAST = 1000.0;

        private SpinningMob() {
        }
    }
}
