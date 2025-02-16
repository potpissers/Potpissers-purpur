package net.minecraft.world.level.redstone;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;

public class ExperimentalRedstoneUtils {
    @Nullable
    public static Orientation initialOrientation(Level level, @Nullable Direction front, @Nullable Direction up) {
        if (level.enabledFeatures().contains(FeatureFlags.REDSTONE_EXPERIMENTS)) {
            Orientation orientation = Orientation.random(level.random).withSideBias(Orientation.SideBias.LEFT);
            if (up != null) {
                orientation = orientation.withUp(up);
            }

            if (front != null) {
                orientation = orientation.withFront(front);
            }
            // Paper start - Optimize redstone (Alternate Current) - use default front instead of random
            else if (level.paperConfig().misc.redstoneImplementation == io.papermc.paper.configuration.WorldConfiguration.Misc.RedstoneImplementation.ALTERNATE_CURRENT) {
                orientation = orientation.withFront(Direction.WEST);
            }
            // Paper end - Optimize redstone (Alternate Current)

            return orientation;
        } else {
            return null;
        }
    }

    @Nullable
    public static Orientation withFront(@Nullable Orientation orientation, Direction direction) {
        return orientation == null ? null : orientation.withFront(direction);
    }
}
