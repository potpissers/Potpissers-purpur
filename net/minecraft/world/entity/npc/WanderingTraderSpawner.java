package net.minecraft.world.entity.npc;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ServerLevelData;

public class WanderingTraderSpawner implements CustomSpawner {
    private static final int DEFAULT_TICK_DELAY = 1200;
    public static final int DEFAULT_SPAWN_DELAY = 24000;
    private static final int MIN_SPAWN_CHANCE = 25;
    private static final int MAX_SPAWN_CHANCE = 75;
    private static final int SPAWN_CHANCE_INCREASE = 25;
    private static final int SPAWN_ONE_IN_X_CHANCE = 10;
    private static final int NUMBER_OF_SPAWN_ATTEMPTS = 10;
    private final RandomSource random = RandomSource.create();
    private final ServerLevelData serverLevelData;
    private int tickDelay;
    private int spawnDelay;
    private int spawnChance;

    public WanderingTraderSpawner(ServerLevelData serverLevelData) {
        this.serverLevelData = serverLevelData;
        // Paper start - Add Wandering Trader spawn rate config options
        this.tickDelay = Integer.MIN_VALUE;
        // this.spawnDelay = serverLevelData.getWanderingTraderSpawnDelay();
        // this.spawnChance = serverLevelData.getWanderingTraderSpawnChance();
        // if (this.spawnDelay == 0 && this.spawnChance == 0) {
        //     this.spawnDelay = 24000;
        //     serverLevelData.setWanderingTraderSpawnDelay(this.spawnDelay);
        //     this.spawnChance = 25;
        //     serverLevelData.setWanderingTraderSpawnChance(this.spawnChance);
        // }
        // Paper end - Add Wandering Trader spawn rate config options
    }

    @Override
    public int tick(ServerLevel level, boolean spawnHostiles, boolean spawnPassives) {
        // Paper start - Add Wandering Trader spawn rate config options
        if (this.tickDelay == Integer.MIN_VALUE) {
            this.tickDelay = level.paperConfig().entities.spawning.wanderingTrader.spawnMinuteLength;
            this.spawnDelay = level.paperConfig().entities.spawning.wanderingTrader.spawnDayLength;
            this.spawnChance = level.paperConfig().entities.spawning.wanderingTrader.spawnChanceMin;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_DO_TRADER_SPAWNING)) {
            return 0;
        } else if (--this.tickDelay - 1 > 0) {
            this.tickDelay = this.tickDelay - 1;
            return 0;
        } else {
            this.tickDelay = level.paperConfig().entities.spawning.wanderingTrader.spawnMinuteLength;
            this.spawnDelay = this.spawnDelay - level.paperConfig().entities.spawning.wanderingTrader.spawnMinuteLength;
            //this.serverLevelData.setWanderingTraderSpawnDelay(this.spawnDelay); // Paper - We don't need to save this value to disk if it gets set back to a hardcoded value anyways
            if (this.spawnDelay > 0) {
                return 0;
            } else {
                this.spawnDelay = level.paperConfig().entities.spawning.wanderingTrader.spawnDayLength;
                if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                    return 0;
                } else {
                    int i = this.spawnChance;
                    this.spawnChance = Mth.clamp(this.spawnChance + level.paperConfig().entities.spawning.wanderingTrader.spawnChanceFailureIncrement, level.paperConfig().entities.spawning.wanderingTrader.spawnChanceMin, level.paperConfig().entities.spawning.wanderingTrader.spawnChanceMax);
                    //this.serverLevelData.setWanderingTraderSpawnChance(this.spawnChance); // Paper - We don't need to save this value to disk if it gets set back to a hardcoded value anyways
                    if (this.random.nextInt(100) > i) {
                        return 0;
                    } else if (this.spawn(level)) {
                        this.spawnChance = level.paperConfig().entities.spawning.wanderingTrader.spawnChanceMin;
                        // Paper end - Add Wandering Trader spawn rate config options
                        return 1;
                    } else {
                        return 0;
                    }
                }
            }
        }
    }

    private boolean spawn(ServerLevel serverLevel) {
        Player randomPlayer = serverLevel.getRandomPlayer();
        if (randomPlayer == null) {
            return true;
        } else if (this.random.nextInt(10) != 0) {
            return false;
        } else {
            BlockPos blockPos = randomPlayer.blockPosition();
            int i = 48;
            PoiManager poiManager = serverLevel.getPoiManager();
            Optional<BlockPos> optional = poiManager.find(holder -> holder.is(PoiTypes.MEETING), blockPos3 -> true, blockPos, 48, PoiManager.Occupancy.ANY);
            BlockPos blockPos1 = optional.orElse(blockPos);
            BlockPos blockPos2 = this.findSpawnPositionNear(serverLevel, blockPos1, 48);
            if (blockPos2 != null && this.hasEnoughSpace(serverLevel, blockPos2)) {
                if (serverLevel.getBiome(blockPos2).is(BiomeTags.WITHOUT_WANDERING_TRADER_SPAWNS)) {
                    return false;
                }

                WanderingTrader wanderingTrader = EntityType.WANDERING_TRADER.spawn(serverLevel, trader -> trader.setDespawnDelay(48000), blockPos2, EntitySpawnReason.EVENT, false, false, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL); // CraftBukkit // Paper - set despawnTimer before spawn events called
                if (wanderingTrader != null) {
                    for (int i1 = 0; i1 < 2; i1++) {
                        this.tryToSpawnLlamaFor(serverLevel, wanderingTrader, 4);
                    }

                    this.serverLevelData.setWanderingTraderId(wanderingTrader.getUUID());
                    // wanderingTrader.setDespawnDelay(48000); // Paper - moved above, modifiable by plugins on CreatureSpawnEvent
                    wanderingTrader.setWanderTarget(blockPos1);
                    wanderingTrader.restrictTo(blockPos1, 16);
                    return true;
                }
            }

            return false;
        }
    }

    private void tryToSpawnLlamaFor(ServerLevel serverLevel, WanderingTrader trader, int maxDistance) {
        BlockPos blockPos = this.findSpawnPositionNear(serverLevel, trader.blockPosition(), maxDistance);
        if (blockPos != null) {
            TraderLlama traderLlama = EntityType.TRADER_LLAMA.spawn(serverLevel, blockPos, EntitySpawnReason.EVENT, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL); // CraftBukkit
            if (traderLlama != null) {
                traderLlama.setLeashedTo(trader, true);
            }
        }
    }

    @Nullable
    private BlockPos findSpawnPositionNear(LevelReader level, BlockPos pos, int maxDistance) {
        BlockPos blockPos = null;
        SpawnPlacementType placementType = SpawnPlacements.getPlacementType(EntityType.WANDERING_TRADER);

        for (int i = 0; i < 10; i++) {
            int i1 = pos.getX() + this.random.nextInt(maxDistance * 2) - maxDistance;
            int i2 = pos.getZ() + this.random.nextInt(maxDistance * 2) - maxDistance;
            int height = level.getHeight(Heightmap.Types.WORLD_SURFACE, i1, i2);
            // Purpur start - Allow toggling special MobSpawners per world - allow traders to spawn below nether roof
            BlockPos.MutableBlockPos blockPos1 = new BlockPos.MutableBlockPos(i1, height, i2);
            if (level.dimensionType().hasCeiling()) {
                do {
                    blockPos1.relative(net.minecraft.core.Direction.DOWN);
                } while (!level.getBlockState(blockPos1).isAir());
                do {
                    blockPos1.relative(net.minecraft.core.Direction.DOWN);
                } while (level.getBlockState(blockPos1).isAir() && blockPos1.getY() > 0);
            }
            // Purpur end - Allow toggling special MobSpawners per world
            if (placementType.isSpawnPositionOk(level, blockPos1, EntityType.WANDERING_TRADER)) {
                blockPos = blockPos1;
                break;
            }
        }

        return blockPos;
    }

    private boolean hasEnoughSpace(BlockGetter level, BlockPos pos) {
        for (BlockPos blockPos : BlockPos.betweenClosed(pos, pos.offset(1, 2, 1))) {
            if (!level.getBlockState(blockPos).getCollisionShape(level, blockPos).isEmpty()) {
                return false;
            }
        }

        return true;
    }
}
