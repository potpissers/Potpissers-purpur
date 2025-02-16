package net.minecraft.gametest.framework;

import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import org.slf4j.Logger;

public class LogTestReporter implements TestReporter {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onTestFailed(GameTestInfo testInfo) {
        String string = testInfo.getTestOrigin().toShortString();
        if (testInfo.isRequired()) {
            LOGGER.error("{} failed at {}! {}", testInfo.getTestName(), string, Util.describeError(testInfo.getError()));
        } else {
            LOGGER.warn("(optional) {} failed at {}. {}", testInfo.getTestName(), string, Util.describeError(testInfo.getError()));
        }
    }

    @Override
    public void onTestSuccess(GameTestInfo testInfo) {
    }
}
