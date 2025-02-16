package net.minecraft.world.level.levelgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class PhantomSpawner implements CustomSpawner {
    private int nextTick;

    @Override
    public int tick(ServerLevel level, boolean spawnEnemies, boolean spawnFriendlies) {
        if (!spawnEnemies) {
            return 0;
        } else if (!level.getGameRules().getBoolean(GameRules.RULE_DOINSOMNIA)) {
            return 0;
        } else {
            RandomSource randomSource = level.random;
            this.nextTick--;
            if (this.nextTick > 0) {
                return 0;
            } else {
                this.nextTick = this.nextTick + (60 + randomSource.nextInt(60)) * 20;
                if (level.getSkyDarken() < 5 && level.dimensionType().hasSkyLight()) {
                    return 0;
                } else {
                    int i = 0;

                    for (ServerPlayer serverPlayer : level.players()) {
                        if (!serverPlayer.isSpectator()) {
                            BlockPos blockPos = serverPlayer.blockPosition();
                            if (!level.dimensionType().hasSkyLight() || blockPos.getY() >= level.getSeaLevel() && level.canSeeSky(blockPos)) {
                                DifficultyInstance currentDifficultyAt = level.getCurrentDifficultyAt(blockPos);
                                if (currentDifficultyAt.isHarderThan(randomSource.nextFloat() * 3.0F)) {
                                    ServerStatsCounter stats = serverPlayer.getStats();
                                    int i1 = Mth.clamp(stats.getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)), 1, Integer.MAX_VALUE);
                                    int i2 = 24000;
                                    if (randomSource.nextInt(i1) >= 72000) {
                                        BlockPos blockPos1 = blockPos.above(20 + randomSource.nextInt(15))
                                            .east(-10 + randomSource.nextInt(21))
                                            .south(-10 + randomSource.nextInt(21));
                                        BlockState blockState = level.getBlockState(blockPos1);
                                        FluidState fluidState = level.getFluidState(blockPos1);
                                        if (NaturalSpawner.isValidEmptySpawnBlock(level, blockPos1, blockState, fluidState, EntityType.PHANTOM)) {
                                            SpawnGroupData spawnGroupData = null;
                                            int i3 = 1 + randomSource.nextInt(currentDifficultyAt.getDifficulty().getId() + 1);

                                            for (int i4 = 0; i4 < i3; i4++) {
                                                Phantom phantom = EntityType.PHANTOM.create(level, EntitySpawnReason.NATURAL);
                                                if (phantom != null) {
                                                    phantom.moveTo(blockPos1, 0.0F, 0.0F);
                                                    spawnGroupData = phantom.finalizeSpawn(
                                                        level, currentDifficultyAt, EntitySpawnReason.NATURAL, spawnGroupData
                                                    );
                                                    level.addFreshEntityWithPassengers(phantom);
                                                    i++;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    return i;
                }
            }
        }
    }
}
