package net.minecraft.util.random;

import java.util.List;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.util.RandomSource;

public class WeightedRandom {
    private WeightedRandom() {
    }

    public static int getTotalWeight(List<? extends WeightedEntry> entries) {
        long l = 0L;

        for (WeightedEntry weightedEntry : entries) {
            l += weightedEntry.getWeight().asInt();
        }

        if (l > 2147483647L) {
            throw new IllegalArgumentException("Sum of weights must be <= 2147483647");
        } else {
            return (int)l;
        }
    }

    public static <T extends WeightedEntry> Optional<T> getRandomItem(RandomSource random, List<T> entries, int totalWeight) {
        if (totalWeight < 0) {
            throw (IllegalArgumentException)Util.pauseInIde(new IllegalArgumentException("Negative total weight in getRandomItem"));
        } else if (totalWeight == 0) {
            return Optional.empty();
        } else {
            int randomInt = random.nextInt(totalWeight);
            return getWeightedItem(entries, randomInt);
        }
    }

    public static <T extends WeightedEntry> Optional<T> getWeightedItem(List<T> entries, int weightedIndex) {
        for (T weightedEntry : entries) {
            weightedIndex -= weightedEntry.getWeight().asInt();
            if (weightedIndex < 0) {
                return Optional.of(weightedEntry);
            }
        }

        return Optional.empty();
    }

    public static <T extends WeightedEntry> Optional<T> getRandomItem(RandomSource random, List<T> entries) {
        return getRandomItem(random, entries, getTotalWeight(entries));
    }
}
