package net.minecraft.util.thread;

import com.mojang.logging.LogUtils;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;

public class NamedThreadFactory implements ThreadFactory {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;

    public NamedThreadFactory(String namePrefix) {
        SecurityManager securityManager = System.getSecurityManager();
        this.group = securityManager != null ? securityManager.getThreadGroup() : Thread.currentThread().getThreadGroup();
        this.namePrefix = namePrefix + "-";
    }

    @Override
    public Thread newThread(Runnable task) {
        Thread thread = new Thread(this.group, task, this.namePrefix + this.threadNumber.getAndIncrement(), 0L);
        thread.setUncaughtExceptionHandler((thread1, exception) -> {
            LOGGER.error("Caught exception in thread {} from {}", thread1, task);
            LOGGER.error("", exception);
        });
        if (thread.getPriority() != 5) {
            thread.setPriority(5);
        }

        return thread;
    }
}
