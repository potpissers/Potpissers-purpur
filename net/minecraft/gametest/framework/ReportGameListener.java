package net.minecraft.gametest.framework;

import com.google.common.base.MoreObjects;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.apache.commons.lang3.exception.ExceptionUtils;

class ReportGameListener implements GameTestListener {
    private int attempts = 0;
    private int successes = 0;

    public ReportGameListener() {
    }

    @Override
    public void testStructureLoaded(GameTestInfo testInfo) {
        spawnBeacon(testInfo, Blocks.LIGHT_GRAY_STAINED_GLASS);
        this.attempts++;
    }

    private void handleRetry(GameTestInfo testInfo, GameTestRunner runner, boolean passed) {
        RetryOptions retryOptions = testInfo.retryOptions();
        String string = String.format("[Run: %4d, Ok: %4d, Fail: %4d", this.attempts, this.successes, this.attempts - this.successes);
        if (!retryOptions.unlimitedTries()) {
            string = string + String.format(", Left: %4d", retryOptions.numberOfTries() - this.attempts);
        }

        string = string + "]";
        String string1 = testInfo.getTestName() + " " + (passed ? "passed" : "failed") + "! " + testInfo.getRunTime() + "ms";
        String string2 = String.format("%-53s%s", string, string1);
        if (passed) {
            reportPassed(testInfo, string2);
        } else {
            say(testInfo.getLevel(), ChatFormatting.RED, string2);
        }

        if (retryOptions.hasTriesLeft(this.attempts, this.successes)) {
            runner.rerunTest(testInfo);
        }
    }

    @Override
    public void testPassed(GameTestInfo test, GameTestRunner runner) {
        this.successes++;
        if (test.retryOptions().hasRetries()) {
            this.handleRetry(test, runner, true);
        } else if (!test.isFlaky()) {
            reportPassed(test, test.getTestName() + " passed! (" + test.getRunTime() + "ms)");
        } else {
            if (this.successes >= test.requiredSuccesses()) {
                reportPassed(test, test + " passed " + this.successes + " times of " + this.attempts + " attempts.");
            } else {
                say(test.getLevel(), ChatFormatting.GREEN, "Flaky test " + test + " succeeded, attempt: " + this.attempts + " successes: " + this.successes);
                runner.rerunTest(test);
            }
        }
    }

    @Override
    public void testFailed(GameTestInfo test, GameTestRunner runner) {
        if (!test.isFlaky()) {
            reportFailure(test, test.getError());
            if (test.retryOptions().hasRetries()) {
                this.handleRetry(test, runner, false);
            }
        } else {
            TestFunction testFunction = test.getTestFunction();
            String string = "Flaky test " + test + " failed, attempt: " + this.attempts + "/" + testFunction.maxAttempts();
            if (testFunction.requiredSuccesses() > 1) {
                string = string + ", successes: " + this.successes + " (" + testFunction.requiredSuccesses() + " required)";
            }

            say(test.getLevel(), ChatFormatting.YELLOW, string);
            if (test.maxAttempts() - this.attempts + this.successes >= test.requiredSuccesses()) {
                runner.rerunTest(test);
            } else {
                reportFailure(test, new ExhaustedAttemptsException(this.attempts, this.successes, test));
            }
        }
    }

    @Override
    public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
        newTest.addListener(this);
    }

    public static void reportPassed(GameTestInfo testInfo, String message) {
        updateBeaconGlass(testInfo, Blocks.LIME_STAINED_GLASS);
        visualizePassedTest(testInfo, message);
    }

    private static void visualizePassedTest(GameTestInfo testInfo, String message) {
        say(testInfo.getLevel(), ChatFormatting.GREEN, message);
        GlobalTestReporter.onTestSuccess(testInfo);
    }

    protected static void reportFailure(GameTestInfo testInfo, Throwable error) {
        updateBeaconGlass(testInfo, testInfo.isRequired() ? Blocks.RED_STAINED_GLASS : Blocks.ORANGE_STAINED_GLASS);
        spawnLectern(testInfo, Util.describeError(error));
        visualizeFailedTest(testInfo, error);
    }

    protected static void visualizeFailedTest(GameTestInfo testInfo, Throwable error) {
        String string = error.getMessage() + (error.getCause() == null ? "" : " cause: " + Util.describeError(error.getCause()));
        String string1 = (testInfo.isRequired() ? "" : "(optional) ") + testInfo.getTestName() + " failed! " + string;
        say(testInfo.getLevel(), testInfo.isRequired() ? ChatFormatting.RED : ChatFormatting.YELLOW, string1);
        Throwable throwable = MoreObjects.firstNonNull(ExceptionUtils.getRootCause(error), error);
        if (throwable instanceof GameTestAssertPosException gameTestAssertPosException) {
            showRedBox(testInfo.getLevel(), gameTestAssertPosException.getAbsolutePos(), gameTestAssertPosException.getMessageToShowAtBlock());
        }

        GlobalTestReporter.onTestFailed(testInfo);
    }

    protected static void spawnBeacon(GameTestInfo testInfo, Block block) {
        ServerLevel level = testInfo.getLevel();
        BlockPos beaconPos = getBeaconPos(testInfo);
        level.setBlockAndUpdate(beaconPos, Blocks.BEACON.defaultBlockState().rotate(testInfo.getRotation()));
        updateBeaconGlass(testInfo, block);

        for (int i = -1; i <= 1; i++) {
            for (int i1 = -1; i1 <= 1; i1++) {
                BlockPos blockPos = beaconPos.offset(i, -1, i1);
                level.setBlockAndUpdate(blockPos, Blocks.IRON_BLOCK.defaultBlockState());
            }
        }
    }

    private static BlockPos getBeaconPos(GameTestInfo testInfo) {
        BlockPos structureBlockPos = testInfo.getStructureBlockPos();
        BlockPos blockPos = new BlockPos(-1, -2, -1);
        return StructureTemplate.transform(structureBlockPos.offset(blockPos), Mirror.NONE, testInfo.getRotation(), structureBlockPos);
    }

    private static void updateBeaconGlass(GameTestInfo testInfo, Block newBlock) {
        ServerLevel level = testInfo.getLevel();
        BlockPos beaconPos = getBeaconPos(testInfo);
        if (level.getBlockState(beaconPos).is(Blocks.BEACON)) {
            BlockPos blockPos = beaconPos.offset(0, 1, 0);
            level.setBlockAndUpdate(blockPos, newBlock.defaultBlockState());
        }
    }

    private static void spawnLectern(GameTestInfo testInfo, String message) {
        ServerLevel level = testInfo.getLevel();
        BlockPos structureBlockPos = testInfo.getStructureBlockPos();
        BlockPos blockPos = new BlockPos(-1, 0, -1);
        BlockPos blockPos1 = StructureTemplate.transform(structureBlockPos.offset(blockPos), Mirror.NONE, testInfo.getRotation(), structureBlockPos);
        level.setBlockAndUpdate(blockPos1, Blocks.LECTERN.defaultBlockState().rotate(testInfo.getRotation()));
        BlockState blockState = level.getBlockState(blockPos1);
        ItemStack itemStack = createBook(testInfo.getTestName(), testInfo.isRequired(), message);
        LecternBlock.tryPlaceBook(null, level, blockPos1, blockState, itemStack);
    }

    private static ItemStack createBook(String testName, boolean required, String message) {
        StringBuffer stringBuffer = new StringBuffer();
        Arrays.stream(testName.split("\\.")).forEach(subName -> stringBuffer.append(subName).append('\n'));
        if (!required) {
            stringBuffer.append("(optional)\n");
        }

        stringBuffer.append("-------------------\n");
        ItemStack itemStack = new ItemStack(Items.WRITABLE_BOOK);
        itemStack.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(List.of(Filterable.passThrough(stringBuffer + message))));
        return itemStack;
    }

    protected static void say(ServerLevel serverLevel, ChatFormatting formatting, String message) {
        serverLevel.getPlayers(serverPlayer -> true).forEach(serverPlayer -> serverPlayer.sendSystemMessage(Component.literal(message).withStyle(formatting)));
    }

    private static void showRedBox(ServerLevel serverLevel, BlockPos pos, String displayMessage) {
        DebugPackets.sendGameTestAddMarker(serverLevel, pos, displayMessage, -2130771968, Integer.MAX_VALUE);
    }
}
