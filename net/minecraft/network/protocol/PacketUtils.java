package net.minecraft.network.protocol;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.network.PacketListener;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.BlockableEventLoop;
import org.slf4j.Logger;

public class PacketUtils {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static <T extends PacketListener> void ensureRunningOnSameThread(Packet<T> packet, T processor, ServerLevel level) throws RunningOnDifferentThreadException {
        ensureRunningOnSameThread(packet, processor, level.getServer());
    }

    public static <T extends PacketListener> void ensureRunningOnSameThread(Packet<T> packet, T processor, BlockableEventLoop<?> executor) throws RunningOnDifferentThreadException {
        if (!executor.isSameThread()) {
            executor.executeIfPossible(() -> {
                packetProcessing.push(processor); // Paper - detailed watchdog information
                try { // Paper - detailed watchdog information
                if (processor instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl serverCommonPacketListener && serverCommonPacketListener.processedDisconnect) return; // Paper - Don't handle sync packets for kicked players
                if (processor.shouldHandleMessage(packet)) {
                    try {
                        packet.handle(processor);
                    } catch (Exception var4) {
                        if (var4 instanceof ReportedException reportedException && reportedException.getCause() instanceof OutOfMemoryError) {
                            throw makeReportedException(var4, packet, processor);
                        }

                        processor.onPacketError(packet, var4);
                    }
                } else {
                    LOGGER.debug("Ignoring packet due to disconnection: {}", packet);
                }
                // Paper start - detailed watchdog information
                } finally {
                    totalMainThreadPacketsProcessed.getAndIncrement();
                    packetProcessing.pop();
                }
                // Paper end - detailed watchdog information
            });
            throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
        }
    }

    public static <T extends PacketListener> ReportedException makeReportedException(Exception exception, Packet<T> packet, T packetListener) {
        if (exception instanceof ReportedException reportedException) {
            fillCrashReport(reportedException.getReport(), packetListener, packet);
            return reportedException;
        } else {
            CrashReport crashReport = CrashReport.forThrowable(exception, "Main thread packet handler");
            fillCrashReport(crashReport, packetListener, packet);
            return new ReportedException(crashReport);
        }
    }

    public static <T extends PacketListener> void fillCrashReport(CrashReport crashReport, T packetListener, @Nullable Packet<T> packet) {
        if (packet != null) {
            CrashReportCategory crashReportCategory = crashReport.addCategory("Incoming Packet");
            crashReportCategory.setDetail("Type", () -> packet.type().toString());
            crashReportCategory.setDetail("Is Terminal", () -> Boolean.toString(packet.isTerminal()));
            crashReportCategory.setDetail("Is Skippable", () -> Boolean.toString(packet.isSkippable()));
        }

        packetListener.fillCrashReport(crashReport);
    }

    // Paper start - detailed watchdog information
    public static final java.util.concurrent.ConcurrentLinkedDeque<PacketListener> packetProcessing = new java.util.concurrent.ConcurrentLinkedDeque<>();
    static final java.util.concurrent.atomic.AtomicLong totalMainThreadPacketsProcessed = new java.util.concurrent.atomic.AtomicLong();

    public static long getTotalProcessedPackets() {
        return totalMainThreadPacketsProcessed.get();
    }

    public static java.util.List<PacketListener> getCurrentPacketProcessors() {
        java.util.List<PacketListener> listeners = new java.util.ArrayList<>(4);
        for (PacketListener listener : packetProcessing) {
            listeners.add(listener);
        }

        return listeners;
    }
    // Paper end - detailed watchdog information
}
