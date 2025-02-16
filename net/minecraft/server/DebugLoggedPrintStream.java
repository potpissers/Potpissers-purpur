package net.minecraft.server;

import com.mojang.logging.LogUtils;
import java.io.OutputStream;
import org.slf4j.Logger;

public class DebugLoggedPrintStream extends LoggedPrintStream {
    private static final Logger LOGGER = LogUtils.getLogger();

    public DebugLoggedPrintStream(String name, OutputStream out) {
        super(name, out);
    }

    @Override
    protected void logLine(String string) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StackTraceElement stackTraceElement = stackTrace[Math.min(3, stackTrace.length)];
        LOGGER.info("[{}]@.({}:{}): {}", this.name, stackTraceElement.getFileName(), stackTraceElement.getLineNumber(), string);
    }
}
