package net.minecraft.world.entity.npc;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;

public class CatSpawner implements CustomSpawner {
    private static final int TICK_DELAY = 1200;
    private int nextTick;

    @Override
    public int tick(ServerLevel level, boolean spawnHostiles, boolean spawnPassives) {
        if (spawnPassives && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
            this.nextTick--;
            if (this.nextTick > 0) {
                return 0;
            } else {
                this.nextTick = 1200;
                Player randomPlayer = level.getRandomPlayer();
                if (randomPlayer == null) {
                    return 0;
                } else {
                    RandomSource randomSource = level.random;
                    int i = (8 + randomSource.nextInt(24)) * (randomSource.nextBoolean() ? -1 : 1);
                    int i1 = (8 + randomSource.nextInt(24)) * (randomSource.nextBoolean() ? -1 : 1);
                    BlockPos blockPos = randomPlayer.blockPosition().offset(i, 0, i1);
                    int i2 = 10;
                    if (!level.hasChunksAt(blockPos.getX() - 10, blockPos.getZ() - 10, blockPos.getX() + 10, blockPos.getZ() + 10)) {
                        return 0;
                    } else {
                        if (SpawnPlacements.isSpawnPositionOk(EntityType.CAT, level, blockPos)) {
                            if (level.isCloseToVillage(blockPos, 2)) {
                                return this.spawnInVillage(level, blockPos);
                            }

                            if (level.structureManager().getStructureWithPieceAt(blockPos, StructureTags.CATS_SPAWN_IN).isValid()) {
                                return this.spawnInHut(level, blockPos);
                            }
                        }

                        return 0;
                    }
                }
            }
        } else {
            return 0;
        }
    }

    private int spawnInVillage(ServerLevel serverLevel, BlockPos pos) {
        int i = 48;
        if (serverLevel.getPoiManager().getCountInRange(holder -> holder.is(PoiTypes.HOME), pos, 48, PoiManager.Occupancy.IS_OCCUPIED) > 4L) {
            List<Cat> entitiesOfClass = serverLevel.getEntitiesOfClass(Cat.class, new AABB(pos).inflate(48.0, 8.0, 48.0));
            if (entitiesOfClass.size() < 5) {
                return this.spawnCat(pos, serverLevel);
            }
        }

        return 0;
    }

    private int spawnInHut(ServerLevel serverLevel, BlockPos pos) {
        int i = 16;
        List<Cat> entitiesOfClass = serverLevel.getEntitiesOfClass(Cat.class, new AABB(pos).inflate(16.0, 8.0, 16.0));
        return entitiesOfClass.size() < 1 ? this.spawnCat(pos, serverLevel) : 0;
    }

    private int spawnCat(BlockPos pos, ServerLevel serverLevel) {
        Cat cat = EntityType.CAT.create(serverLevel, EntitySpawnReason.NATURAL);
        if (cat == null) {
            return 0;
        } else {
            cat.moveTo(pos, 0.0F, 0.0F); // Paper - move up - Fix MC-147659
            cat.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
            serverLevel.addFreshEntityWithPassengers(cat);
            return 1;
        }
    }
}
