package net.minecraft.world.level;

import com.mojang.logging.LogUtils;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public abstract class BaseSpawner {
    public static final String SPAWN_DATA_TAG = "SpawnData";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int EVENT_SPAWN = 1;
    public int spawnDelay = 20;
    public SimpleWeightedRandomList<SpawnData> spawnPotentials = SimpleWeightedRandomList.empty();
    @Nullable
    public SpawnData nextSpawnData;
    private double spin;
    private double oSpin;
    public int minSpawnDelay = 200;
    public int maxSpawnDelay = 800;
    public int spawnCount = 4;
    @Nullable
    private Entity displayEntity;
    public int maxNearbyEntities = 6;
    public int requiredPlayerRange = 16;
    public int spawnRange = 4;

    public void setEntityId(EntityType<?> type, @Nullable Level level, RandomSource random, BlockPos pos) {
        this.getOrCreateNextSpawnData(level, random, pos).getEntityToSpawn().putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
    }

    public boolean isNearPlayer(Level level, BlockPos pos) {
        return level.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, this.requiredPlayerRange);
    }

    public void clientTick(Level level, BlockPos pos) {
        if (!this.isNearPlayer(level, pos)) {
            this.oSpin = this.spin;
        } else if (this.displayEntity != null) {
            RandomSource random = level.getRandom();
            double d = pos.getX() + random.nextDouble();
            double d1 = pos.getY() + random.nextDouble();
            double d2 = pos.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.SMOKE, d, d1, d2, 0.0, 0.0, 0.0);
            level.addParticle(ParticleTypes.FLAME, d, d1, d2, 0.0, 0.0, 0.0);
            if (this.spawnDelay > 0) {
                this.spawnDelay--;
            }

            this.oSpin = this.spin;
            this.spin = (this.spin + 1000.0F / (this.spawnDelay + 200.0F)) % 360.0;
        }
    }

    public void serverTick(ServerLevel serverLevel, BlockPos pos) {
        if (this.isNearPlayer(serverLevel, pos)) {
            if (this.spawnDelay == -1) {
                this.delay(serverLevel, pos);
            }

            if (this.spawnDelay > 0) {
                this.spawnDelay--;
            } else {
                boolean flag = false;
                RandomSource random = serverLevel.getRandom();
                SpawnData nextSpawnData = this.getOrCreateNextSpawnData(serverLevel, random, pos);

                for (int i = 0; i < this.spawnCount; i++) {
                    CompoundTag entityToSpawn = nextSpawnData.getEntityToSpawn();
                    Optional<EntityType<?>> optional = EntityType.by(entityToSpawn);
                    if (optional.isEmpty()) {
                        this.delay(serverLevel, pos);
                        return;
                    }

                    ListTag list = entityToSpawn.getList("Pos", 6);
                    int size = list.size();
                    double d = size >= 1 ? list.getDouble(0) : pos.getX() + (random.nextDouble() - random.nextDouble()) * this.spawnRange + 0.5;
                    double d1 = size >= 2 ? list.getDouble(1) : pos.getY() + random.nextInt(3) - 1;
                    double d2 = size >= 3 ? list.getDouble(2) : pos.getZ() + (random.nextDouble() - random.nextDouble()) * this.spawnRange + 0.5;
                    if (serverLevel.noCollision(optional.get().getSpawnAABB(d, d1, d2))) {
                        BlockPos blockPos = BlockPos.containing(d, d1, d2);
                        if (nextSpawnData.getCustomSpawnRules().isPresent()) {
                            if (!optional.get().getCategory().isFriendly() && serverLevel.getDifficulty() == Difficulty.PEACEFUL) {
                                continue;
                            }

                            SpawnData.CustomSpawnRules customSpawnRules = nextSpawnData.getCustomSpawnRules().get();
                            if (!customSpawnRules.isValidPosition(blockPos, serverLevel)) {
                                continue;
                            }
                        } else if (!SpawnPlacements.checkSpawnRules(optional.get(), serverLevel, EntitySpawnReason.SPAWNER, blockPos, serverLevel.getRandom())) {
                            continue;
                        }

                        Entity entity = EntityType.loadEntityRecursive(entityToSpawn, serverLevel, EntitySpawnReason.SPAWNER, entity1 -> {
                            entity1.moveTo(d, d1, d2, entity1.getYRot(), entity1.getXRot());
                            return entity1;
                        });
                        if (entity == null) {
                            this.delay(serverLevel, pos);
                            return;
                        }

                        int size1 = serverLevel.getEntities(
                                EntityTypeTest.forExactClass(entity.getClass()),
                                new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1).inflate(this.spawnRange),
                                EntitySelector.NO_SPECTATORS
                            )
                            .size();
                        if (size1 >= this.maxNearbyEntities) {
                            this.delay(serverLevel, pos);
                            return;
                        }

                        entity.moveTo(entity.getX(), entity.getY(), entity.getZ(), random.nextFloat() * 360.0F, 0.0F);
                        if (entity instanceof Mob mob) {
                            if (nextSpawnData.getCustomSpawnRules().isEmpty() && !mob.checkSpawnRules(serverLevel, EntitySpawnReason.SPAWNER)
                                || !mob.checkSpawnObstruction(serverLevel)) {
                                continue;
                            }

                            boolean flag1 = nextSpawnData.getEntityToSpawn().size() == 1 && nextSpawnData.getEntityToSpawn().contains("id", 8);
                            if (flag1) {
                                ((Mob)entity)
                                    .finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(entity.blockPosition()), EntitySpawnReason.SPAWNER, null);
                            }

                            nextSpawnData.getEquipment().ifPresent(mob::equip);
                        }

                        if (!serverLevel.tryAddFreshEntityWithPassengers(entity)) {
                            this.delay(serverLevel, pos);
                            return;
                        }

                        serverLevel.levelEvent(2004, pos, 0);
                        serverLevel.gameEvent(entity, GameEvent.ENTITY_PLACE, blockPos);
                        if (entity instanceof Mob) {
                            ((Mob)entity).spawnAnim();
                        }

                        flag = true;
                    }
                }

                if (flag) {
                    this.delay(serverLevel, pos);
                }
            }
        }
    }

    public void delay(Level level, BlockPos pos) {
        RandomSource randomSource = level.random;
        if (this.maxSpawnDelay <= this.minSpawnDelay) {
            this.spawnDelay = this.minSpawnDelay;
        } else {
            this.spawnDelay = this.minSpawnDelay + randomSource.nextInt(this.maxSpawnDelay - this.minSpawnDelay);
        }

        this.spawnPotentials.getRandom(randomSource).ifPresent(data -> this.setNextSpawnData(level, pos, data.data()));
        this.broadcastEvent(level, pos, 1);
    }

    public void load(@Nullable Level level, BlockPos pos, CompoundTag tag) {
        this.spawnDelay = tag.getShort("Delay");
        boolean flag = tag.contains("SpawnData", 10);
        if (flag) {
            SpawnData spawnData = SpawnData.CODEC
                .parse(NbtOps.INSTANCE, tag.getCompound("SpawnData"))
                .resultOrPartial(data -> LOGGER.warn("Invalid SpawnData: {}", data))
                .orElseGet(SpawnData::new);
            this.setNextSpawnData(level, pos, spawnData);
        }

        boolean flag1 = tag.contains("SpawnPotentials", 9);
        if (flag1) {
            ListTag list = tag.getList("SpawnPotentials", 10);
            this.spawnPotentials = SpawnData.LIST_CODEC
                .parse(NbtOps.INSTANCE, list)
                .resultOrPartial(potentials -> LOGGER.warn("Invalid SpawnPotentials list: {}", potentials))
                .orElseGet(SimpleWeightedRandomList::empty);
        } else {
            this.spawnPotentials = SimpleWeightedRandomList.single(this.nextSpawnData != null ? this.nextSpawnData : new SpawnData());
        }

        if (tag.contains("MinSpawnDelay", 99)) {
            this.minSpawnDelay = tag.getShort("MinSpawnDelay");
            this.maxSpawnDelay = tag.getShort("MaxSpawnDelay");
            this.spawnCount = tag.getShort("SpawnCount");
        }

        if (tag.contains("MaxNearbyEntities", 99)) {
            this.maxNearbyEntities = tag.getShort("MaxNearbyEntities");
            this.requiredPlayerRange = tag.getShort("RequiredPlayerRange");
        }

        if (tag.contains("SpawnRange", 99)) {
            this.spawnRange = tag.getShort("SpawnRange");
        }

        this.displayEntity = null;
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putShort("Delay", (short)this.spawnDelay);
        tag.putShort("MinSpawnDelay", (short)this.minSpawnDelay);
        tag.putShort("MaxSpawnDelay", (short)this.maxSpawnDelay);
        tag.putShort("SpawnCount", (short)this.spawnCount);
        tag.putShort("MaxNearbyEntities", (short)this.maxNearbyEntities);
        tag.putShort("RequiredPlayerRange", (short)this.requiredPlayerRange);
        tag.putShort("SpawnRange", (short)this.spawnRange);
        if (this.nextSpawnData != null) {
            tag.put(
                "SpawnData",
                SpawnData.CODEC
                    .encodeStart(NbtOps.INSTANCE, this.nextSpawnData)
                    .getOrThrow(string -> new IllegalStateException("Invalid SpawnData: " + string))
            );
        }

        tag.put("SpawnPotentials", SpawnData.LIST_CODEC.encodeStart(NbtOps.INSTANCE, this.spawnPotentials).getOrThrow());
        return tag;
    }

    @Nullable
    public Entity getOrCreateDisplayEntity(Level level, BlockPos pos) {
        if (this.displayEntity == null) {
            CompoundTag entityToSpawn = this.getOrCreateNextSpawnData(level, level.getRandom(), pos).getEntityToSpawn();
            if (!entityToSpawn.contains("id", 8)) {
                return null;
            }

            this.displayEntity = EntityType.loadEntityRecursive(entityToSpawn, level, EntitySpawnReason.SPAWNER, Function.identity());
            if (entityToSpawn.size() == 1 && this.displayEntity instanceof Mob) {
            }
        }

        return this.displayEntity;
    }

    public boolean onEventTriggered(Level level, int id) {
        if (id == 1) {
            if (level.isClientSide) {
                this.spawnDelay = this.minSpawnDelay;
            }

            return true;
        } else {
            return false;
        }
    }

    public void setNextSpawnData(@Nullable Level level, BlockPos pos, SpawnData nextSpawnData) {
        this.nextSpawnData = nextSpawnData;
    }

    private SpawnData getOrCreateNextSpawnData(@Nullable Level level, RandomSource random, BlockPos pos) {
        if (this.nextSpawnData != null) {
            return this.nextSpawnData;
        } else {
            this.setNextSpawnData(level, pos, this.spawnPotentials.getRandom(random).map(WeightedEntry.Wrapper::data).orElseGet(SpawnData::new));
            return this.nextSpawnData;
        }
    }

    public abstract void broadcastEvent(Level level, BlockPos pos, int eventId);

    public double getSpin() {
        return this.spin;
    }

    public double getoSpin() {
        return this.oSpin;
    }
}
