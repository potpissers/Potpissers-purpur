package net.minecraft.gametest.framework;

import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;

public class GameTestBatchFactory {
    private static final int MAX_TESTS_PER_BATCH = 50;

    public static Collection<GameTestBatch> fromTestFunction(Collection<TestFunction> testFunctions, ServerLevel level) {
        Map<String, List<TestFunction>> map = testFunctions.stream().collect(Collectors.groupingBy(TestFunction::batchName));
        return map.entrySet()
            .stream()
            .flatMap(
                entry -> {
                    String string = entry.getKey();
                    List<TestFunction> list = entry.getValue();
                    return Streams.mapWithIndex(
                        Lists.partition(list, 50).stream(),
                        (list1, l) -> toGameTestBatch(list1.stream().map(testFunction -> toGameTestInfo(testFunction, 0, level)).toList(), string, l)
                    );
                }
            )
            .toList();
    }

    public static GameTestInfo toGameTestInfo(TestFunction testFunction, int rotationSteps, ServerLevel level) {
        return new GameTestInfo(testFunction, StructureUtils.getRotationForRotationSteps(rotationSteps), level, RetryOptions.noRetries());
    }

    public static GameTestRunner.GameTestBatcher fromGameTestInfo() {
        return fromGameTestInfo(50);
    }

    public static GameTestRunner.GameTestBatcher fromGameTestInfo(int maxTests) {
        return infos -> {
            Map<String, List<GameTestInfo>> map = infos.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(gameTestInfo -> gameTestInfo.getTestFunction().batchName()));
            return map.entrySet().stream().flatMap(entry -> {
                String string = entry.getKey();
                List<GameTestInfo> list = entry.getValue();
                return Streams.mapWithIndex(Lists.partition(list, maxTests).stream(), (list1, l) -> toGameTestBatch(List.copyOf(list1), string, l));
            }).toList();
        };
    }

    public static GameTestBatch toGameTestBatch(Collection<GameTestInfo> gameTestInfos, String functionName, long index) {
        Consumer<ServerLevel> beforeBatchFunction = GameTestRegistry.getBeforeBatchFunction(functionName);
        Consumer<ServerLevel> afterBatchFunction = GameTestRegistry.getAfterBatchFunction(functionName);
        return new GameTestBatch(functionName + ":" + index, gameTestInfos, beforeBatchFunction, afterBatchFunction);
    }
}
