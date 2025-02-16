package net.minecraft.world.entity.projectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public class ThrownExperienceBottle extends ThrowableItemProjectile {
    public ThrownExperienceBottle(EntityType<? extends ThrownExperienceBottle> entityType, Level level) {
        super(entityType, level);
    }

    public ThrownExperienceBottle(Level level, LivingEntity owner, ItemStack item) {
        super(EntityType.EXPERIENCE_BOTTLE, owner, level, item);
    }

    public ThrownExperienceBottle(Level level, double x, double y, double z, ItemStack item) {
        super(EntityType.EXPERIENCE_BOTTLE, x, y, z, level, item);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.EXPERIENCE_BOTTLE;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.07;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level() instanceof ServerLevel) {
            this.level().levelEvent(2002, this.blockPosition(), -13083194);
            int i = 3 + this.level().random.nextInt(5) + this.level().random.nextInt(5);
            ExperienceOrb.award((ServerLevel)this.level(), this.position(), i);
            this.discard();
        }
    }
}
