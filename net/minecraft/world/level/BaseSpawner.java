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
    private int tickDelay = 0; // Paper - Configurable mob spawner tick rate

    public void setEntityId(EntityType<?> type, @Nullable Level level, RandomSource random, BlockPos pos) {
        this.getOrCreateNextSpawnData(level, random, pos).getEntityToSpawn().putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        this.spawnPotentials = SimpleWeightedRandomList.empty(); // CraftBukkit - SPIGOT-3496, MC-92282
    }

    public boolean isNearPlayer(Level level, BlockPos pos) {
        if (level.purpurConfig.spawnerDeactivateByRedstone && level.hasNeighborSignal(pos)) return false; // Purpur - Redstone deactivates spawners
        return level.hasNearbyAlivePlayerThatAffectsSpawning(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, this.requiredPlayerRange); // Paper - Affects Spawning API
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
        if (spawnCount <= 0 || maxNearbyEntities <= 0) return; // Paper - Ignore impossible spawn tick
        // Paper start - Configurable mob spawner tick rate
        if (spawnDelay > 0 && --tickDelay > 0) return;
        tickDelay = serverLevel.paperConfig().tickRates.mobSpawner;
        if (tickDelay == -1) { return; } // If disabled
        // Paper end - Configurable mob spawner tick rate
        if (this.isNearPlayer(serverLevel, pos)) {
            if (this.spawnDelay < -tickDelay) { // Paper - Configurable mob spawner tick rate
                this.delay(serverLevel, pos);
            }

            if (this.spawnDelay > 0) {
                this.spawnDelay -= tickDelay; // Paper - Configurable mob spawner tick rate
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

                        // Paper start - PreCreatureSpawnEvent
                        com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent event = new com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent(
                            io.papermc.paper.util.MCUtil.toLocation(serverLevel, d, d1, d2),
                            org.bukkit.craftbukkit.entity.CraftEntityType.minecraftToBukkit(optional.get()),
                            io.papermc.paper.util.MCUtil.toLocation(serverLevel, pos)
                        );
                        if (!event.callEvent()) {
                            flag = true;
                            if (event.shouldAbortSpawn()) {
                                break;
                            }
                            continue;
                        }
                        // Paper end - PreCreatureSpawnEvent

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

                        entity.preserveMotion = true; // Paper - Fix Entity Teleportation and cancel velocity if teleported; preserve entity motion from tag
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
                            // Spigot start
                            if (mob.level().spigotConfig.nerfSpawnerMobs) {
                                mob.aware = false;
                            }
                            // Spigot end
                        }

                        entity.spawnedViaMobSpawner = true; // Paper
                        entity.spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER; // Paper - Entity#getEntitySpawnReason
                        flag = true; // Paper
                        // CraftBukkit start
                        if (org.bukkit.craftbukkit.event.CraftEventFactory.callSpawnerSpawnEvent(entity, pos).isCancelled()) {
                            continue;
                        }
                        if (!serverLevel.tryAddFreshEntityWithPassengers(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER)) {
                            // CraftBukkit end
                            this.delay(serverLevel, pos);
                            return;
                        }

                        serverLevel.levelEvent(2004, pos, 0);
                        serverLevel.gameEvent(entity, GameEvent.ENTITY_PLACE, blockPos);
                        if (entity instanceof Mob) {
                            ((Mob)entity).spawnAnim();
                        }

                        //flag = true; // Paper - moved up above cancellable event
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
        // Paper start - use larger int if set
        if (tag.contains("Paper.Delay")) {
            this.spawnDelay = tag.getInt("Paper.Delay");
        } else {
        this.spawnDelay = tag.getShort("Delay");
        }
        // Paper end
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

        // Paper start - use ints if set
        if (tag.contains("Paper.MinSpawnDelay", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
            this.minSpawnDelay = tag.getInt("Paper.MinSpawnDelay");
            this.maxSpawnDelay = tag.getInt("Paper.MaxSpawnDelay");
            this.spawnCount = tag.getShort("SpawnCount");
        } else // Paper end
        if (tag.contains("MinSpawnDelay", 99)) {
            this.minSpawnDelay = tag.getInt("MinSpawnDelay"); // Paper - short -> int
            this.maxSpawnDelay = tag.getInt("MaxSpawnDelay"); // Paper - short -> int
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
        // Paper start
        if (spawnDelay > Short.MAX_VALUE) {
            tag.putInt("Paper.Delay", this.spawnDelay);
        }
        tag.putShort("Delay", (short) Math.min(Short.MAX_VALUE, this.spawnDelay));

        if (minSpawnDelay > Short.MAX_VALUE || maxSpawnDelay > Short.MAX_VALUE) {
            tag.putInt("Paper.MinSpawnDelay", this.minSpawnDelay);
            tag.putInt("Paper.MaxSpawnDelay", this.maxSpawnDelay);
        }

        tag.putShort("MinSpawnDelay", (short) Math.min(Short.MAX_VALUE, this.minSpawnDelay));
        tag.putShort("MaxSpawnDelay", (short) Math.min(Short.MAX_VALUE, this.maxSpawnDelay));
        // Paper end
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
