package net.minecraft.world.entity.vehicle;

import java.util.function.Supplier;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class ChestBoat extends AbstractChestBoat {
    public ChestBoat(EntityType<? extends ChestBoat> entityType, Level level, Supplier<Item> dropItem) {
        super(entityType, level, dropItem);
    }

    @Override
    protected double rideHeight(EntityDimensions dimensions) {
        return dimensions.height() / 3.0F;
    }
}
