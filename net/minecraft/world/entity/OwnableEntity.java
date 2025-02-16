package net.minecraft.world.entity;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.level.EntityGetter;

public interface OwnableEntity {
    @Nullable
    UUID getOwnerUUID();

    EntityGetter level();

    @Nullable
    default LivingEntity getOwner() {
        UUID ownerUuid = this.getOwnerUUID();
        return ownerUuid == null ? null : this.level().getPlayerByUUID(ownerUuid);
    }
}
