package net.minecraft.gametest.framework;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

public class GameTestRunner {
    public static final int DEFAULT_TESTS_PER_ROW = 8;
    private static final Logger LOGGER = LogUtils.getLogger();
    final ServerLevel level;
    private final GameTestTicker testTicker;
    private final List<GameTestInfo> allTestInfos;
    private ImmutableList<GameTestBatch> batches;
    final List<GameTestBatchListener> batchListeners = Lists.newArrayList();
    private final List<GameTestInfo> scheduledForRerun = Lists.newArrayList();
    private final GameTestRunner.GameTestBatcher testBatcher;
    private boolean stopped = true;
    @Nullable
    GameTestBatch currentBatch;
    private final GameTestRunner.StructureSpawner existingStructureSpawner;
    private final GameTestRunner.StructureSpawner newStructureSpawner;
    final boolean haltOnError;

    protected GameTestRunner(
        GameTestRunner.GameTestBatcher testBatcher,
        Collection<GameTestBatch> batches,
        ServerLevel level,
        GameTestTicker testTicker,
        GameTestRunner.StructureSpawner existingStructureSpawner,
        GameTestRunner.StructureSpawner newStructureSpawner,
        boolean haltOnError
    ) {
        this.level = level;
        this.testTicker = testTicker;
        this.testBatcher = testBatcher;
        this.existingStructureSpawner = existingStructureSpawner;
        this.newStructureSpawner = newStructureSpawner;
        this.batches = ImmutableList.copyOf(batches);
        this.haltOnError = haltOnError;
        this.allTestInfos = this.batches.stream().flatMap(gameTestBatch -> gameTestBatch.gameTestInfos().stream()).collect(Util.toMutableList());
        testTicker.setRunner(this);
        this.allTestInfos.forEach(gameTestInfo -> gameTestInfo.addListener(new ReportGameListener()));
    }

    public List<GameTestInfo> getTestInfos() {
        return this.allTestInfos;
    }

    public void start() {
        this.stopped = false;
        this.runBatch(0);
    }

    public void stop() {
        this.stopped = true;
        if (this.currentBatch != null) {
            this.currentBatch.afterBatchFunction().accept(this.level);
        }
    }

    public void rerunTest(GameTestInfo test) {
        GameTestInfo gameTestInfo = test.copyReset();
        test.getListeners().forEach(gameTestListener -> gameTestListener.testAddedForRerun(test, gameTestInfo, this));
        this.allTestInfos.add(gameTestInfo);
        this.scheduledForRerun.add(gameTestInfo);
        if (this.stopped) {
            this.runScheduledRerunTests();
        }
    }

    void runBatch(final int index) {
        if (index >= this.batches.size()) {
            this.runScheduledRerunTests();
        } else {
            this.currentBatch = this.batches.get(index);
            this.existingStructureSpawner.onBatchStart(this.level);
            this.newStructureSpawner.onBatchStart(this.level);
            Collection<GameTestInfo> collection = this.createStructuresForBatch(this.currentBatch.gameTestInfos());
            String string = this.currentBatch.name();
            LOGGER.info("Running test batch '{}' ({} tests)...", string, collection.size());
            this.currentBatch.beforeBatchFunction().accept(this.level);
            this.batchListeners.forEach(gameTestBatchListener -> gameTestBatchListener.testBatchStarting(this.currentBatch));
            final MultipleTestTracker multipleTestTracker = new MultipleTestTracker();
            collection.forEach(multipleTestTracker::addTestToTrack);
            multipleTestTracker.addListener(
                new GameTestListener() {
                    private void testCompleted() {
                        if (multipleTestTracker.isDone()) {
                            GameTestRunner.this.currentBatch.afterBatchFunction().accept(GameTestRunner.this.level);
                            GameTestRunner.this.batchListeners
                                .forEach(gameTestBatchListener -> gameTestBatchListener.testBatchFinished(GameTestRunner.this.currentBatch));
                            LongSet set = new LongArraySet(GameTestRunner.this.level.getForcedChunks());
                            set.forEach(l -> GameTestRunner.this.level.setChunkForced(ChunkPos.getX(l), ChunkPos.getZ(l), false));
                            GameTestRunner.this.runBatch(index + 1);
                        }
                    }

                    @Override
                    public void testStructureLoaded(GameTestInfo testInfo) {
                    }

                    @Override
                    public void testPassed(GameTestInfo test, GameTestRunner runner) {
                        this.testCompleted();
                    }

                    @Override
                    public void testFailed(GameTestInfo test, GameTestRunner runner) {
                        if (GameTestRunner.this.haltOnError) {
                            GameTestRunner.this.currentBatch.afterBatchFunction().accept(GameTestRunner.this.level);
                            LongSet set = new LongArraySet(GameTestRunner.this.level.getForcedChunks());
                            set.forEach(l -> GameTestRunner.this.level.setChunkForced(ChunkPos.getX(l), ChunkPos.getZ(l), false));
                            GameTestTicker.SINGLETON.clear();
                        } else {
                            this.testCompleted();
                        }
                    }

                    @Override
                    public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
                    }
                }
            );
            collection.forEach(this.testTicker::add);
        }
    }

    private void runScheduledRerunTests() {
        if (!this.scheduledForRerun.isEmpty()) {
            LOGGER.info(
                "Starting re-run of tests: {}",
                this.scheduledForRerun.stream().map(gameTestInfo -> gameTestInfo.getTestFunction().testName()).collect(Collectors.joining(", "))
            );
            this.batches = ImmutableList.copyOf(this.testBatcher.batch(this.scheduledForRerun));
            this.scheduledForRerun.clear();
            this.stopped = false;
            this.runBatch(0);
        } else {
            this.batches = ImmutableList.of();
            this.stopped = true;
        }
    }

    public void addListener(GameTestBatchListener listener) {
        this.batchListeners.add(listener);
    }

    private Collection<GameTestInfo> createStructuresForBatch(Collection<GameTestInfo> batch) {
        return batch.stream().map(this::spawn).flatMap(Optional::stream).toList();
    }

    private Optional<GameTestInfo> spawn(GameTestInfo test) {
        return test.getStructureBlockPos() == null ? this.newStructureSpawner.spawnStructure(test) : this.existingStructureSpawner.spawnStructure(test);
    }

    public static void clearMarkers(ServerLevel serverLevel) {
        DebugPackets.sendGameTestClearPacket(serverLevel);
    }

    public static class Builder {
        private final ServerLevel level;
        private final GameTestTicker testTicker = GameTestTicker.SINGLETON;
        private GameTestRunner.GameTestBatcher batcher = GameTestBatchFactory.fromGameTestInfo();
        private GameTestRunner.StructureSpawner existingStructureSpawner = GameTestRunner.StructureSpawner.IN_PLACE;
        private GameTestRunner.StructureSpawner newStructureSpawner = GameTestRunner.StructureSpawner.NOT_SET;
        private final Collection<GameTestBatch> batches;
        private boolean haltOnError = false;

        private Builder(Collection<GameTestBatch> batches, ServerLevel level) {
            this.batches = batches;
            this.level = level;
        }

        public static GameTestRunner.Builder fromBatches(Collection<GameTestBatch> batches, ServerLevel level) {
            return new GameTestRunner.Builder(batches, level);
        }

        public static GameTestRunner.Builder fromInfo(Collection<GameTestInfo> infos, ServerLevel level) {
            return fromBatches(GameTestBatchFactory.fromGameTestInfo().batch(infos), level);
        }

        public GameTestRunner.Builder haltOnError(boolean haltOnError) {
            this.haltOnError = haltOnError;
            return this;
        }

        public GameTestRunner.Builder newStructureSpawner(GameTestRunner.StructureSpawner newStructureSpawner) {
            this.newStructureSpawner = newStructureSpawner;
            return this;
        }

        public GameTestRunner.Builder existingStructureSpawner(StructureGridSpawner existingStructureSpawner) {
            this.existingStructureSpawner = existingStructureSpawner;
            return this;
        }

        public GameTestRunner.Builder batcher(GameTestRunner.GameTestBatcher batcher) {
            this.batcher = batcher;
            return this;
        }

        public GameTestRunner build() {
            return new GameTestRunner(
                this.batcher, this.batches, this.level, this.testTicker, this.existingStructureSpawner, this.newStructureSpawner, this.haltOnError
            );
        }
    }

    public interface GameTestBatcher {
        Collection<GameTestBatch> batch(Collection<GameTestInfo> infos);
    }

    public interface StructureSpawner {
        GameTestRunner.StructureSpawner IN_PLACE = gameTestInfo -> Optional.of(gameTestInfo.prepareTestStructure().placeStructure().startExecution(1));
        GameTestRunner.StructureSpawner NOT_SET = gameTestInfo -> Optional.empty();

        Optional<GameTestInfo> spawnStructure(GameTestInfo gameTestInfo);

        default void onBatchStart(ServerLevel level) {
        }
    }
}
