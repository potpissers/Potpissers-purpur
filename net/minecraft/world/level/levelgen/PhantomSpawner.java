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
            // Paper start - Ability to control player's insomnia and phantoms
            if (level.paperConfig().entities.behavior.phantomsSpawnAttemptMaxSeconds <= 0) {
                return 0;
            }
            // Paper end - Ability to control player's insomnia and phantoms
            RandomSource randomSource = level.random;
            this.nextTick--;
            if (this.nextTick > 0) {
                return 0;
            } else {
                // Paper start - Ability to control player's insomnia and phantoms
                int spawnAttemptMinSeconds = level.paperConfig().entities.behavior.phantomsSpawnAttemptMinSeconds;
                int spawnAttemptMaxSeconds = level.paperConfig().entities.behavior.phantomsSpawnAttemptMaxSeconds;
                this.nextTick += (spawnAttemptMinSeconds + randomSource.nextInt(spawnAttemptMaxSeconds - spawnAttemptMinSeconds + 1)) * 20;
                // Paper end - Ability to control player's insomnia and phantoms
                if (level.getSkyDarken() < level.purpurConfig.phantomSpawnMinSkyDarkness && level.dimensionType().hasSkyLight()) { // Purpur - Add phantom spawning options
                    return 0;
                } else {
                    int i = 0;

                    for (ServerPlayer serverPlayer : level.players()) {
                        if (!serverPlayer.isSpectator() && (!level.paperConfig().entities.behavior.phantomsDoNotSpawnOnCreativePlayers || !serverPlayer.isCreative())) { // Paper - Add phantom creative and insomniac controls
                            BlockPos blockPos = serverPlayer.blockPosition();
                            if (!level.dimensionType().hasSkyLight() || (!level.purpurConfig.phantomSpawnOnlyAboveSeaLevel || blockPos.getY() >= level.getSeaLevel()) && (!level.purpurConfig.phantomSpawnOnlyWithVisibleSky || level.canSeeSky(blockPos))) { // Purpur - Add phantom spawning options
                                DifficultyInstance currentDifficultyAt = level.getCurrentDifficultyAt(blockPos);
                                if (currentDifficultyAt.isHarderThan(randomSource.nextFloat() * (float) level.purpurConfig.phantomSpawnLocalDifficultyChance)) { // Purpur - Add phantom spawning options
                                    ServerStatsCounter stats = serverPlayer.getStats();
                                    int i1 = Mth.clamp(stats.getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)), 1, Integer.MAX_VALUE);
                                    int i2 = 24000;
                                    if (randomSource.nextInt(i1) >= level.paperConfig().entities.behavior.playerInsomniaStartTicks) { // Paper - Ability to control player's insomnia and phantoms
                                        BlockPos blockPos1 = blockPos.above(20 + randomSource.nextInt(15))
                                            .east(-10 + randomSource.nextInt(21))
                                            .south(-10 + randomSource.nextInt(21));
                                        BlockState blockState = level.getBlockState(blockPos1);
                                        FluidState fluidState = level.getFluidState(blockPos1);
                                        if (NaturalSpawner.isValidEmptySpawnBlock(level, blockPos1, blockState, fluidState, EntityType.PHANTOM)) {
                                            SpawnGroupData spawnGroupData = null;
                                            int i3 = level.purpurConfig.phantomSpawnMinPerAttempt + level.random.nextInt((level.purpurConfig.phantomSpawnMaxPerAttempt < 0 ? currentDifficultyAt.getDifficulty().getId() : level.purpurConfig.phantomSpawnMaxPerAttempt - level.purpurConfig.phantomSpawnMinPerAttempt) + 1); // Purpur - Add phantom spawning options

                                            for (int i4 = 0; i4 < i3; i4++) {
                                                // Paper start - PhantomPreSpawnEvent
                                                com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent event = new com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent(io.papermc.paper.util.MCUtil.toLocation(level, blockPos1), serverPlayer.getBukkitEntity(), org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL);
                                                if (!event.callEvent()) {
                                                    if (event.shouldAbortSpawn()) {
                                                        break;
                                                    }
                                                    continue;
                                                }
                                                // Paper end - PhantomPreSpawnEvent
                                                Phantom phantom = EntityType.PHANTOM.create(level, EntitySpawnReason.NATURAL);
                                                if (phantom != null) {
                                                    phantom.spawningEntity = serverPlayer.getUUID(); // Paper - PhantomPreSpawnEvent
                                                    phantom.moveTo(blockPos1, 0.0F, 0.0F);
                                                    spawnGroupData = phantom.finalizeSpawn(
                                                        level, currentDifficultyAt, EntitySpawnReason.NATURAL, spawnGroupData
                                                    );
                                                    level.addFreshEntityWithPassengers(phantom, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL); // CraftBukkit
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
