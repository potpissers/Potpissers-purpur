package net.minecraft.gametest.framework;

import java.util.function.Consumer;
import net.minecraft.world.level.block.Rotation;

public record TestFunction(
    String batchName,
    String testName,
    String structureName,
    Rotation rotation,
    int maxTicks,
    long setupTicks,
    boolean required,
    boolean manualOnly,
    int maxAttempts,
    int requiredSuccesses,
    boolean skyAccess,
    Consumer<GameTestHelper> function
) {
    public TestFunction(
        String batchName, String testName, String structureName, int maxTicks, long setupTicks, boolean required, Consumer<GameTestHelper> function
    ) {
        this(batchName, testName, structureName, Rotation.NONE, maxTicks, setupTicks, required, false, 1, 1, false, function);
    }

    public TestFunction(
        String batchName,
        String testName,
        String structureName,
        Rotation rotation,
        int maxTicks,
        long setupTicks,
        boolean required,
        Consumer<GameTestHelper> function
    ) {
        this(batchName, testName, structureName, rotation, maxTicks, setupTicks, required, false, 1, 1, false, function);
    }

    public void run(GameTestHelper gameTestHelper) {
        this.function.accept(gameTestHelper);
    }

    @Override
    public String toString() {
        return this.testName;
    }

    public boolean isFlaky() {
        return this.maxAttempts > 1;
    }
}
