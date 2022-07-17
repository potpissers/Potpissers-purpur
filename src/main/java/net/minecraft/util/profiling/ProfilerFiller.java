package net.minecraft.util.profiling;

import java.util.function.Supplier;
import net.minecraft.util.profiling.metrics.MetricCategory;

public interface ProfilerFiller {
    String ROOT = "root";

    @io.papermc.paper.annotation.DoNotUse // Purpur
    void startTick();

    @io.papermc.paper.annotation.DoNotUse // Purpur
    void endTick();

    @io.papermc.paper.annotation.DoNotUse // Purpur
    void push(String location);

    @io.papermc.paper.annotation.DoNotUse // Purpur
    void push(Supplier<String> locationGetter);

    @io.papermc.paper.annotation.DoNotUse // Purpur
    void pop();

    @io.papermc.paper.annotation.DoNotUse // Purpur
    void popPush(String location);

    @io.papermc.paper.annotation.DoNotUse // Purpur
    void popPush(Supplier<String> locationGetter);

    @io.papermc.paper.annotation.DoNotUse // Purpur
    void markForCharting(MetricCategory type);

    @io.papermc.paper.annotation.DoNotUse // Purpur
    default void incrementCounter(String marker) {
        //this.incrementCounter(marker, 1); // Purpur
    }

    @io.papermc.paper.annotation.DoNotUse // Purpur
    void incrementCounter(String marker, int num);

    @io.papermc.paper.annotation.DoNotUse // Purpur
    default void incrementCounter(Supplier<String> markerGetter) {
        //this.incrementCounter(markerGetter, 1); // Purpur
    }

    @io.papermc.paper.annotation.DoNotUse // Purpur
    void incrementCounter(Supplier<String> markerGetter, int num);

    static ProfilerFiller tee(ProfilerFiller a, ProfilerFiller b) {
        if (a == InactiveProfiler.INSTANCE) {
            return b;
        } else {
            return b == InactiveProfiler.INSTANCE ? a : new ProfilerFiller() {
                @Override
                public void startTick() {
                    //a.startTick(); // Purpur
                    //b.startTick(); // Purpur
                }

                @Override
                public void endTick() {
                    //a.endTick(); // Purpur
                    //b.endTick(); // Purpur
                }

                @Override
                public void push(String location) {
                    //a.push(location); // Purpur
                    //b.push(location); // Purpur
                }

                @Override
                public void push(Supplier<String> locationGetter) {
                    //a.push(locationGetter); // Purpur
                    //b.push(locationGetter); // Purpur
                }

                @Override
                public void markForCharting(MetricCategory type) {
                    //a.markForCharting(type); // Purpur
                    //b.markForCharting(type); // Purpur
                }

                @Override
                public void pop() {
                    //a.pop(); // Purpur
                    //b.pop(); // Purpur
                }

                @Override
                public void popPush(String location) {
                    //a.popPush(location); // Purpur
                    //b.popPush(location); // Purpur
                }

                @Override
                public void popPush(Supplier<String> locationGetter) {
                    //a.popPush(locationGetter); // Purpur
                    //b.popPush(locationGetter); // Purpur
                }

                @Override
                public void incrementCounter(String marker, int num) {
                    //a.incrementCounter(marker, num); // Purpur
                    //b.incrementCounter(marker, num); // Purpur
                }

                @Override
                public void incrementCounter(Supplier<String> markerGetter, int num) {
                    //a.incrementCounter(markerGetter, num); // Purpur
                    //b.incrementCounter(markerGetter, num); // Purpur
                }
            };
        }
    }
}
