package net.minecraft.world.level;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressStructure;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

// CraftBukkit start
import net.minecraft.world.level.storage.LevelData;
import org.bukkit.craftbukkit.util.CraftSpawnCategory;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
// CraftBukkit end

public final class NaturalSpawner {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MIN_SPAWN_DISTANCE = 24;
    public static final int SPAWN_DISTANCE_CHUNK = 8;
    public static final int SPAWN_DISTANCE_BLOCK = 128;
    static final int MAGIC_NUMBER = (int)Math.pow(17.0, 2.0);
    public static final MobCategory[] SPAWNING_CATEGORIES = Stream.of(MobCategory.values())
        .filter(category -> category != MobCategory.MISC)
        .toArray(MobCategory[]::new);

    private NaturalSpawner() {
    }

    public static NaturalSpawner.SpawnState createState(
        int spawnableChunkCount, Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkGetter, LocalMobCapCalculator calculator
    ) {
        // Paper start - Optional per player mob spawns
        return createState(spawnableChunkCount, entities, chunkGetter, calculator, false);
    }

    public static NaturalSpawner.SpawnState createState(
        int spawnableChunkCount, Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkGetter, LocalMobCapCalculator calculator, final boolean countMobs
    ) {
        // Paper end - Optional per player mob spawns
        PotentialCalculator potentialCalculator = new PotentialCalculator();
        Object2IntOpenHashMap<MobCategory> map = new Object2IntOpenHashMap<>();

        for (Entity entity : entities) {
            if (!(entity instanceof Mob mob && (mob.isPersistenceRequired() || mob.requiresCustomPersistence()))) {
                MobCategory category = entity.getType().getCategory();
                if (category != MobCategory.MISC) {
                    // Paper start - Only count natural spawns
                    if (!entity.level().paperConfig().entities.spawning.countAllMobsForSpawning &&
                        !(entity.spawnReason == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL ||
                            entity.spawnReason == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CHUNK_GEN)) {
                        continue;
                    }
                    // Paper end - Only count natural spawns
                    BlockPos blockPos = entity.blockPosition();
                    chunkGetter.query(ChunkPos.asLong(blockPos), chunk -> {
                        MobSpawnSettings.MobSpawnCost mobSpawnCost = getRoughBiome(blockPos, chunk).getMobSettings().getMobSpawnCost(entity.getType());
                        if (mobSpawnCost != null) {
                            potentialCalculator.addCharge(entity.blockPosition(), mobSpawnCost.charge());
                        }

                        if (calculator != null && entity instanceof Mob) { // Paper - Optional per player mob spawns
                            calculator.addMob(chunk.getPos(), category);
                        }

                        map.addTo(category, 1);
                        // Paper start - Optional per player mob spawns
                        if (countMobs) {
                            chunk.level.getChunkSource().chunkMap.updatePlayerMobTypeMap(entity);
                        }
                        // Paper end - Optional per player mob spawns
                    });
                }
            }
        }

        return new NaturalSpawner.SpawnState(spawnableChunkCount, map, potentialCalculator, calculator);
    }

    static Biome getRoughBiome(BlockPos pos, ChunkAccess chunk) {
        return chunk.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ())).value();
    }

    // CraftBukkit start - add server
    public static List<MobCategory> getFilteredSpawningCategories(
        NaturalSpawner.SpawnState spawnState, boolean spawnFriendlies, boolean spawnEnemies, boolean spawnPassives, ServerLevel level
    ) {
        LevelData worlddata = level.getLevelData(); // CraftBukkit - Other mob type spawn tick rate
        // CraftBukkit end
        List<MobCategory> list = new ArrayList<>(SPAWNING_CATEGORIES.length);

        for (MobCategory mobCategory : SPAWNING_CATEGORIES) {
            // CraftBukkit start - Use per-world spawn limits
            boolean spawnThisTick = true;
            int limit = mobCategory.getMaxInstancesPerChunk();
            SpawnCategory spawnCategory = CraftSpawnCategory.toBukkit(mobCategory);
            if (CraftSpawnCategory.isValidForLimits(spawnCategory)) {
                spawnThisTick = level.ticksPerSpawnCategory.getLong(spawnCategory) != 0 && worlddata.getGameTime() % level.ticksPerSpawnCategory.getLong(spawnCategory) == 0;
                limit = level.getWorld().getSpawnLimit(spawnCategory);
            }

            if (!spawnThisTick || limit == 0) {
                continue;
            }

            if ((spawnFriendlies || !mobCategory.isFriendly())
                && (spawnEnemies || mobCategory.isFriendly())
                && (spawnPassives || !mobCategory.isPersistent())
                && (level.paperConfig().entities.spawning.perPlayerMobSpawns || spawnState.canSpawnForCategoryGlobal(mobCategory, limit))) { // Paper - Optional per player mob spawns; remove global check, check later during the local one
                list.add(mobCategory);
                // CraftBukkit end
            }
        }

        return list;
    }

    public static void spawnForChunk(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState, List<MobCategory> categories) {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("spawner");

        for (MobCategory mobCategory : categories) {
            // Paper start - Optional per player mob spawns
            final boolean canSpawn;
            int maxSpawns = Integer.MAX_VALUE;
            if (level.paperConfig().entities.spawning.perPlayerMobSpawns) {
                // Copied from getFilteredSpawningCategories
                int limit = mobCategory.getMaxInstancesPerChunk();
                SpawnCategory spawnCategory = CraftSpawnCategory.toBukkit(mobCategory);
                if (CraftSpawnCategory.isValidForLimits(spawnCategory)) {
                    limit = level.getWorld().getSpawnLimit(spawnCategory);
                }

                // Apply per-player limit
                int minDiff = Integer.MAX_VALUE;
                final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.server.level.ServerPlayer> inRange =
                    level.moonrise$getNearbyPlayers().getPlayers(chunk.getPos(), ca.spottedleaf.moonrise.common.misc.NearbyPlayers.NearbyMapType.TICK_VIEW_DISTANCE);
                if (inRange != null) {
                    final net.minecraft.server.level.ServerPlayer[] backingSet = inRange.getRawDataUnchecked();
                    for (int k = 0, len = inRange.size(); k < len; k++) {
                        minDiff = Math.min(limit - level.getChunkSource().chunkMap.getMobCountNear(backingSet[k], mobCategory), minDiff);
                    }
                }

                maxSpawns = (minDiff == Integer.MAX_VALUE) ? 0 : minDiff;
                canSpawn = maxSpawns > 0;
            } else {
                canSpawn = spawnState.canSpawnForCategoryLocal(mobCategory, chunk.getPos());
            }
            if (canSpawn) {
                spawnCategoryForChunk(mobCategory, level, chunk, spawnState::canSpawn, spawnState::afterSpawn,
                    maxSpawns, level.paperConfig().entities.spawning.perPlayerMobSpawns ? level.getChunkSource().chunkMap::updatePlayerMobTypeMap : null);
                // Paper end - Optional per player mob spawns
            }
        }

        profilerFiller.pop();
    }

    // Paper start - Add mobcaps commands
    public static int globalLimitForCategory(final ServerLevel level, final MobCategory category, final int spawnableChunkCount) {
        final int categoryLimit = level.getWorld().getSpawnLimitUnsafe(CraftSpawnCategory.toBukkit(category));
        if (categoryLimit < 1) {
            return categoryLimit;
        }
        return categoryLimit * spawnableChunkCount / NaturalSpawner.MAGIC_NUMBER;
    }
    // Paper end - Add mobcaps commands

    public static void spawnCategoryForChunk(
        MobCategory category, ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnPredicate filter, NaturalSpawner.AfterSpawnCallback callback
    ) {
        // Paper start - Optional per player mob spawns
        spawnCategoryForChunk(category, level, chunk, filter, callback, Integer.MAX_VALUE, null);
    }
    public static void spawnCategoryForChunk(
        MobCategory category, ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnPredicate filter, NaturalSpawner.AfterSpawnCallback callback, final int maxSpawns, final Consumer<Entity> trackEntity
    ) {
        // Paper end - Optional per player mob spawns
        BlockPos randomPosWithin = getRandomPosWithin(level, chunk);
        if (randomPosWithin.getY() >= level.getMinY() + 1) {
            spawnCategoryForPosition(category, level, chunk, randomPosWithin, filter, callback, maxSpawns, trackEntity); // Paper - Optional per player mob spawns
        }
    }

    @VisibleForDebug
    public static void spawnCategoryForPosition(MobCategory category, ServerLevel level, BlockPos pos) {
        spawnCategoryForPosition(category, level, level.getChunk(pos), pos, (entityType, spawnPos, chunk) -> true, (mob, chunk) -> {});
    }

    public static void spawnCategoryForPosition(
        MobCategory category,
        ServerLevel level,
        ChunkAccess chunk,
        BlockPos pos,
        NaturalSpawner.SpawnPredicate filter,
        NaturalSpawner.AfterSpawnCallback callback
    ) {
        spawnCategoryForPosition(category, level, chunk, pos, filter, callback, Integer.MAX_VALUE, null);
    }
    public static void spawnCategoryForPosition(
        MobCategory category, ServerLevel level, ChunkAccess chunk, BlockPos pos, NaturalSpawner.SpawnPredicate filter, NaturalSpawner.AfterSpawnCallback callback, final int maxSpawns, final @Nullable Consumer<Entity> trackEntity
    ) {
        // Paper end - Optional per player mob spawns
        StructureManager structureManager = level.structureManager();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        int y = pos.getY();
        BlockState blockState = level.getBlockStateIfLoadedAndInBounds(pos); // Paper - don't load chunks for mob spawn
        if (blockState != null && !blockState.isRedstoneConductor(chunk, pos)) { // Paper - don't load chunks for mob spawn
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
            int i = 0;

            for (int i1 = 0; i1 < 3; i1++) {
                int x = pos.getX();
                int z = pos.getZ();
                int i2 = 6;
                MobSpawnSettings.SpawnerData spawnerData = null;
                SpawnGroupData spawnGroupData = null;
                int ceil = Mth.ceil(level.random.nextFloat() * 4.0F);
                int i3 = 0;

                for (int i4 = 0; i4 < ceil; i4++) {
                    x += level.random.nextInt(6) - level.random.nextInt(6);
                    z += level.random.nextInt(6) - level.random.nextInt(6);
                    mutableBlockPos.set(x, y, z);
                    double d = x + 0.5;
                    double d1 = z + 0.5;
                    Player nearestPlayer = level.getNearestPlayer(d, y, d1, -1.0, level.purpurConfig.mobSpawningIgnoreCreativePlayers); // Purpur - mob spawning option to ignore creative players
                    if (nearestPlayer != null) {
                        double d2 = nearestPlayer.distanceToSqr(d, y, d1);
                        if (level.isLoadedAndInBounds(mutableBlockPos) && isRightDistanceToPlayerAndSpawnPoint(level, chunk, mutableBlockPos, d2)) { // Paper - don't load chunks for mob spawn
                            if (spawnerData == null) {
                                Optional<MobSpawnSettings.SpawnerData> randomSpawnMobAt = getRandomSpawnMobAt(
                                    level, structureManager, generator, category, level.random, mutableBlockPos
                                );
                                if (randomSpawnMobAt.isEmpty()) {
                                    break;
                                }

                                spawnerData = randomSpawnMobAt.get();
                                ceil = spawnerData.minCount + level.random.nextInt(1 + spawnerData.maxCount - spawnerData.minCount);
                            }

                            // Paper start - PreCreatureSpawnEvent
                            PreSpawnStatus doSpawning = isValidSpawnPostitionForType(level, category, structureManager, generator, spawnerData, mutableBlockPos, d2);
                            // Paper start - per player mob count backoff
                            if (doSpawning == PreSpawnStatus.ABORT || doSpawning == PreSpawnStatus.CANCELLED) {
                                level.getChunkSource().chunkMap.updateFailurePlayerMobTypeMap(mutableBlockPos.getX() >> 4, mutableBlockPos.getZ() >> 4, category);
                            }
                            // Paper end - per player mob count backoff
                            if (doSpawning == PreSpawnStatus.ABORT) {
                                return;
                            }
                            if (doSpawning == PreSpawnStatus.SUCCESS && filter.test(spawnerData.type, mutableBlockPos, chunk)) {
                                // Paper end - PreCreatureSpawnEvent
                                Mob mobForSpawn = getMobForSpawn(level, spawnerData.type);
                                if (mobForSpawn == null) {
                                    return;
                                }

                                mobForSpawn.moveTo(d, y, d1, level.random.nextFloat() * 360.0F, 0.0F);
                                if (isValidPositionForMob(level, mobForSpawn, d2)) {
                                    spawnGroupData = mobForSpawn.finalizeSpawn(
                                        level, level.getCurrentDifficultyAt(mobForSpawn.blockPosition()), EntitySpawnReason.NATURAL, spawnGroupData
                                    );
                                    // CraftBukkit start
                                    // SPIGOT-7045: Give ocelot babies back their special spawn reason. Note: This is the only modification required as ocelots count as monsters which means they only spawn during normal chunk ticking and do not spawn during chunk generation as starter mobs.
                                    level.addFreshEntityWithPassengers(mobForSpawn, (mobForSpawn instanceof net.minecraft.world.entity.animal.Ocelot && !((org.bukkit.entity.Ageable) mobForSpawn.getBukkitEntity()).isAdult()) ? SpawnReason.OCELOT_BABY : SpawnReason.NATURAL);
                                    if (!mobForSpawn.isRemoved()) {
                                        ++i;
                                        ++i3;
                                        callback.run(mobForSpawn, chunk);
                                        // Paper start - Optional per player mob spawns
                                        if (trackEntity != null) {
                                            trackEntity.accept(mobForSpawn);
                                        }
                                        // Paper end - Optional per player mob spawns
                                    }
                                    // CraftBukkit end
                                    if (i >= mobForSpawn.getMaxSpawnClusterSize() || i >= maxSpawns) { // Paper - Optional per player mob spawns
                                        return;
                                    }

                                    if (mobForSpawn.isMaxGroupSizeReached(i3)) {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isRightDistanceToPlayerAndSpawnPoint(ServerLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos pos, double distance) {
        return !(distance <= 576.0)
            && !level.getSharedSpawnPos().closerToCenterThan(new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), 24.0)
            && (Objects.equals(new ChunkPos(pos), chunk.getPos()) || level.isNaturalSpawningAllowed(pos));
    }

    // Paper start - PreCreatureSpawnEvent
    private enum PreSpawnStatus {
        FAIL,
        SUCCESS,
        CANCELLED,
        ABORT
    }
    private static PreSpawnStatus isValidSpawnPostitionForType(
    // Paper end - PreCreatureSpawnEvent
        ServerLevel level,
        MobCategory category,
        StructureManager structureManager,
        ChunkGenerator generator,
        MobSpawnSettings.SpawnerData data,
        BlockPos.MutableBlockPos pos,
        double distance
    ) {
        EntityType<?> entityType = data.type;

        // Paper start - PreCreatureSpawnEvent
        com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent event = new com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent(
            io.papermc.paper.util.MCUtil.toLocation(level, pos),
            org.bukkit.craftbukkit.entity.CraftEntityType.minecraftToBukkit(entityType), SpawnReason.NATURAL
        );
        if (!event.callEvent()) {
            if (event.shouldAbortSpawn()) {
                return PreSpawnStatus.ABORT;
            }
            return PreSpawnStatus.CANCELLED;
        }
        final boolean success = entityType.getCategory() != MobCategory.MISC
            // Paper end - PreCreatureSpawnEvent
            && (
                entityType.canSpawnFarFromPlayer()
                    || !(distance > entityType.getCategory().getDespawnDistance() * entityType.getCategory().getDespawnDistance())
            )
            && entityType.canSummon()
            && canSpawnMobAt(level, structureManager, generator, category, data, pos)
            && SpawnPlacements.isSpawnPositionOk(entityType, level, pos)
            && SpawnPlacements.checkSpawnRules(entityType, level, EntitySpawnReason.NATURAL, pos, level.random)
            && level.noCollision(entityType.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
        return success ? PreSpawnStatus.SUCCESS : PreSpawnStatus.FAIL; // Paper - PreCreatureSpawnEvent
    }

    @Nullable
    private static Mob getMobForSpawn(ServerLevel level, EntityType<?> entityType) {
        try {
            Entity var3 = entityType.create(level, EntitySpawnReason.NATURAL);
            if (var3 instanceof Mob) {
                return (Mob)var3;
            }

            LOGGER.warn("Can't spawn entity of type: {}", BuiltInRegistries.ENTITY_TYPE.getKey(entityType));
        } catch (Exception var4) {
            LOGGER.warn("Failed to create mob", (Throwable)var4);
            com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(var4); // Paper - ServerExceptionEvent
        }

        return null;
    }

    private static boolean isValidPositionForMob(ServerLevel level, Mob mob, double distance) {
        return (
                !(distance > mob.getType().getCategory().getDespawnDistance() * mob.getType().getCategory().getDespawnDistance())
                    || !mob.removeWhenFarAway(distance)
            )
            && mob.checkSpawnRules(level, EntitySpawnReason.NATURAL)
            && mob.checkSpawnObstruction(level);
    }

    private static Optional<MobSpawnSettings.SpawnerData> getRandomSpawnMobAt(
        ServerLevel level, StructureManager structureManager, ChunkGenerator generator, MobCategory category, RandomSource random, BlockPos pos
    ) {
        Holder<Biome> biome = level.getBiome(pos);
        return category == MobCategory.WATER_AMBIENT && biome.is(BiomeTags.REDUCED_WATER_AMBIENT_SPAWNS) && random.nextFloat() < 0.98F
            ? Optional.empty()
            : mobsAt(level, structureManager, generator, category, pos, biome).getRandom(random);
    }

    private static boolean canSpawnMobAt(
        ServerLevel level, StructureManager structureManager, ChunkGenerator generator, MobCategory category, MobSpawnSettings.SpawnerData data, BlockPos pos
    ) {
        return mobsAt(level, structureManager, generator, category, pos, null).unwrap().contains(data);
    }

    private static WeightedRandomList<MobSpawnSettings.SpawnerData> mobsAt(
        ServerLevel level, StructureManager structureManager, ChunkGenerator generator, MobCategory category, BlockPos pos, @Nullable Holder<Biome> biome
    ) {
        return isInNetherFortressBounds(pos, level, category, structureManager)
            ? NetherFortressStructure.FORTRESS_ENEMIES
            : generator.getMobsAt(biome != null ? biome : level.getBiome(pos), structureManager, category, pos);
    }

    public static boolean isInNetherFortressBounds(BlockPos pos, ServerLevel level, MobCategory category, StructureManager structureManager) {
        if (category == MobCategory.MONSTER && level.getBlockState(pos.below()).is(Blocks.NETHER_BRICKS)) {
            Structure structure = structureManager.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BuiltinStructures.FORTRESS);
            return structure != null && structureManager.getStructureAt(pos, structure).isValid();
        } else {
            return false;
        }
    }

    private static BlockPos getRandomPosWithin(Level level, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        int i = pos.getMinBlockX() + level.random.nextInt(16);
        int i1 = pos.getMinBlockZ() + level.random.nextInt(16);
        int i2 = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, i, i1) + 1;
        int i3 = Mth.randomBetweenInclusive(level.random, level.getMinY(), i2);
        return new BlockPos(i, i3, i1);
    }

    public static boolean isValidEmptySpawnBlock(BlockGetter block, BlockPos pos, BlockState blockState, FluidState fluidState, EntityType<?> entityType) {
        return !blockState.isCollisionShapeFullBlock(block, pos)
            && !blockState.isSignalSource()
            && fluidState.isEmpty()
            && !blockState.is(BlockTags.PREVENT_MOB_SPAWNING_INSIDE)
            && !entityType.isBlockDangerous(blockState);
    }

    public static void spawnMobsForChunkGeneration(ServerLevelAccessor levelAccessor, Holder<Biome> biome, ChunkPos chunkPos, RandomSource random) {
        MobSpawnSettings mobSettings = biome.value().getMobSettings();
        WeightedRandomList<MobSpawnSettings.SpawnerData> mobs = mobSettings.getMobs(MobCategory.CREATURE);
        if (!mobs.isEmpty()) {
            int minBlockX = chunkPos.getMinBlockX();
            int minBlockZ = chunkPos.getMinBlockZ();

            while (random.nextFloat() < mobSettings.getCreatureProbability()) {
                Optional<MobSpawnSettings.SpawnerData> random1 = mobs.getRandom(random);
                if (!random1.isEmpty()) {
                    MobSpawnSettings.SpawnerData spawnerData = random1.get();
                    int i = spawnerData.minCount + random.nextInt(1 + spawnerData.maxCount - spawnerData.minCount);
                    SpawnGroupData spawnGroupData = null;
                    int i1 = minBlockX + random.nextInt(16);
                    int i2 = minBlockZ + random.nextInt(16);
                    int i3 = i1;
                    int i4 = i2;

                    for (int i5 = 0; i5 < i; i5++) {
                        boolean flag = false;

                        for (int i6 = 0; !flag && i6 < 4; i6++) {
                            BlockPos topNonCollidingPos = getTopNonCollidingPos(levelAccessor, spawnerData.type, i1, i2);
                            if (spawnerData.type.canSummon() && SpawnPlacements.isSpawnPositionOk(spawnerData.type, levelAccessor, topNonCollidingPos)) {
                                float width = spawnerData.type.getWidth();
                                double d = Mth.clamp((double)i1, (double)minBlockX + width, minBlockX + 16.0 - width);
                                double d1 = Mth.clamp((double)i2, (double)minBlockZ + width, minBlockZ + 16.0 - width);
                                if (!levelAccessor.noCollision(spawnerData.type.getSpawnAABB(d, topNonCollidingPos.getY(), d1))
                                    || !SpawnPlacements.checkSpawnRules(
                                        spawnerData.type,
                                        levelAccessor,
                                        EntitySpawnReason.CHUNK_GENERATION,
                                        BlockPos.containing(d, topNonCollidingPos.getY(), d1),
                                        levelAccessor.getRandom()
                                    )) {
                                    continue;
                                }

                                Entity entity;
                                try {
                                    entity = spawnerData.type.create(levelAccessor.getLevel(), EntitySpawnReason.NATURAL);
                                } catch (Exception var27) {
                                    LOGGER.warn("Failed to create mob", (Throwable)var27);
                                    com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(var27); // Paper - ServerExceptionEvent
                                    continue;
                                }

                                if (entity == null) {
                                    continue;
                                }

                                entity.moveTo(d, topNonCollidingPos.getY(), d1, random.nextFloat() * 360.0F, 0.0F);
                                if (entity instanceof Mob mob
                                    && mob.checkSpawnRules(levelAccessor, EntitySpawnReason.CHUNK_GENERATION)
                                    && mob.checkSpawnObstruction(levelAccessor)) {
                                    spawnGroupData = mob.finalizeSpawn(
                                        levelAccessor,
                                        levelAccessor.getCurrentDifficultyAt(mob.blockPosition()),
                                        EntitySpawnReason.CHUNK_GENERATION,
                                        spawnGroupData
                                    );
                                    levelAccessor.addFreshEntityWithPassengers(mob, SpawnReason.CHUNK_GEN); // CraftBukkit
                                    flag = true;
                                }
                            }

                            i1 += random.nextInt(5) - random.nextInt(5);

                            for (i2 += random.nextInt(5) - random.nextInt(5);
                                i1 < minBlockX || i1 >= minBlockX + 16 || i2 < minBlockZ || i2 >= minBlockZ + 16;
                                i2 = i4 + random.nextInt(5) - random.nextInt(5)
                            ) {
                                i1 = i3 + random.nextInt(5) - random.nextInt(5);
                            }
                        }
                    }
                }
            }
        }
    }

    private static BlockPos getTopNonCollidingPos(LevelReader level, EntityType<?> entityType, int x, int z) {
        int height = level.getHeight(SpawnPlacements.getHeightmapType(entityType), x, z);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(x, height, z);
        if (level.dimensionType().hasCeiling()) {
            do {
                mutableBlockPos.move(Direction.DOWN);
            } while (!level.getBlockState(mutableBlockPos).isAir());

            do {
                mutableBlockPos.move(Direction.DOWN);
            } while (level.getBlockState(mutableBlockPos).isAir() && mutableBlockPos.getY() > level.getMinY());
        }

        return SpawnPlacements.getPlacementType(entityType).adjustSpawnPosition(level, mutableBlockPos.immutable());
    }

    @FunctionalInterface
    public interface AfterSpawnCallback {
        void run(Mob mob, ChunkAccess chunk);
    }

    @FunctionalInterface
    public interface ChunkGetter {
        void query(long chunkPos, Consumer<LevelChunk> consumer);
    }

    @FunctionalInterface
    public interface SpawnPredicate {
        boolean test(EntityType<?> entityType, BlockPos pos, ChunkAccess chunk);
    }

    public static class SpawnState {
        private final int spawnableChunkCount;
        private final Object2IntOpenHashMap<MobCategory> mobCategoryCounts;
        private final PotentialCalculator spawnPotential;
        private final Object2IntMap<MobCategory> unmodifiableMobCategoryCounts;
        private final LocalMobCapCalculator localMobCapCalculator;
        @Nullable
        private BlockPos lastCheckedPos;
        @Nullable
        private EntityType<?> lastCheckedType;
        private double lastCharge;

        SpawnState(
            int spawnableChunkCount,
            Object2IntOpenHashMap<MobCategory> mobCategoryCounts,
            PotentialCalculator spawnPotential,
            LocalMobCapCalculator localMobCapCalculator
        ) {
            this.spawnableChunkCount = spawnableChunkCount;
            this.mobCategoryCounts = mobCategoryCounts;
            this.spawnPotential = spawnPotential;
            this.localMobCapCalculator = localMobCapCalculator;
            this.unmodifiableMobCategoryCounts = Object2IntMaps.unmodifiable(mobCategoryCounts);
        }

        private boolean canSpawn(EntityType<?> entityType, BlockPos pos, ChunkAccess chunk) {
            this.lastCheckedPos = pos;
            this.lastCheckedType = entityType;
            MobSpawnSettings.MobSpawnCost mobSpawnCost = NaturalSpawner.getRoughBiome(pos, chunk).getMobSettings().getMobSpawnCost(entityType);
            if (mobSpawnCost == null) {
                this.lastCharge = 0.0;
                return true;
            } else {
                double charge = mobSpawnCost.charge();
                this.lastCharge = charge;
                double potentialEnergyChange = this.spawnPotential.getPotentialEnergyChange(pos, charge);
                return potentialEnergyChange <= mobSpawnCost.energyBudget();
            }
        }

        private void afterSpawn(Mob mob, ChunkAccess chunk) {
            EntityType<?> type = mob.getType();
            BlockPos blockPos = mob.blockPosition();
            double d;
            if (blockPos.equals(this.lastCheckedPos) && type == this.lastCheckedType) {
                d = this.lastCharge;
            } else {
                MobSpawnSettings.MobSpawnCost mobSpawnCost = NaturalSpawner.getRoughBiome(blockPos, chunk).getMobSettings().getMobSpawnCost(type);
                if (mobSpawnCost != null) {
                    d = mobSpawnCost.charge();
                } else {
                    d = 0.0;
                }
            }

            this.spawnPotential.addCharge(blockPos, d);
            MobCategory category = type.getCategory();
            this.mobCategoryCounts.addTo(category, 1);
            if (this.localMobCapCalculator != null) this.localMobCapCalculator.addMob(new ChunkPos(blockPos), category); // Paper - Optional per player mob spawns
        }

        public int getSpawnableChunkCount() {
            return this.spawnableChunkCount;
        }

        public Object2IntMap<MobCategory> getMobCategoryCounts() {
            return this.unmodifiableMobCategoryCounts;
        }

        // CraftBukkit start
        boolean canSpawnForCategoryGlobal(MobCategory category, int limit) {
            int i = limit * this.spawnableChunkCount / NaturalSpawner.MAGIC_NUMBER;
            // CraftBukkit end
            return this.mobCategoryCounts.getInt(category) < i;
        }

        boolean canSpawnForCategoryLocal(MobCategory category, ChunkPos chunkPos) {
            return this.localMobCapCalculator.canSpawn(category, chunkPos);
        }
    }
}
