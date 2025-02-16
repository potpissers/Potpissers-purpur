package net.minecraft.world.item;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.level.Level;

public class SpectralArrowItem extends ArrowItem {
    public SpectralArrowItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
        return new SpectralArrow(level, shooter, ammo.copyWithCount(1), weapon);
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        SpectralArrow spectralArrow = new SpectralArrow(level, pos.x(), pos.y(), pos.z(), stack.copyWithCount(1), null);
        spectralArrow.pickup = AbstractArrow.Pickup.ALLOWED;
        return spectralArrow;
    }
}
