package net.minecraft.util.profiling.jfr.stats;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.util.profiling.jfr.Percentiles;

public record TimedStatSummary<T extends TimedStat>(
    T fastest, T slowest, @Nullable T secondSlowest, int count, Map<Integer, Double> percentilesNanos, Duration totalDuration
) {
    public static <T extends TimedStat> TimedStatSummary<T> summary(List<T> stats) {
        if (stats.isEmpty()) {
            throw new IllegalArgumentException("No values");
        } else {
            List<T> list = stats.stream().sorted(Comparator.comparing(TimedStat::duration)).toList();
            Duration duration = list.stream().map(TimedStat::duration).reduce(Duration::plus).orElse(Duration.ZERO);
            T timedStat = (T)list.get(0);
            T timedStat1 = (T)list.get(list.size() - 1);
            T timedStat2 = list.size() > 1 ? list.get(list.size() - 2) : null;
            int size = list.size();
            Map<Integer, Double> map = Percentiles.evaluate(list.stream().mapToLong(timedStat3 -> timedStat3.duration().toNanos()).toArray());
            return new TimedStatSummary<>(timedStat, timedStat1, timedStat2, size, map, duration);
        }
    }
}
