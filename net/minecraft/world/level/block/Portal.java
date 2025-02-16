package net.minecraft.world.level.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;

public interface Portal {
    default int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return 0;
    }

    @Nullable
    TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos);

    default Portal.Transition getLocalTransition() {
        return Portal.Transition.NONE;
    }

    public static enum Transition {
        CONFUSION,
        NONE;
    }
}
