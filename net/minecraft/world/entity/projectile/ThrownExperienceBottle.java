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
            // CraftBukkit - moved to after event
            int i = 3 + this.level().random.nextInt(5) + this.level().random.nextInt(5);
            // CraftBukkit start
            org.bukkit.event.entity.ExpBottleEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callExpBottleEvent(this, result, i);
            i = event.getExperience();
            if (event.getShowEffect()) {
                this.level().levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_SPELL_POTION_SPLASH, this.blockPosition(), net.minecraft.world.item.alchemy.PotionContents.BASE_POTION_COLOR);
            }
            // CraftBukkit end
            ExperienceOrb.award((ServerLevel)this.level(), this.position(), i, org.bukkit.entity.ExperienceOrb.SpawnReason.EXP_BOTTLE, this.getOwner(), this); // Paper
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }
    }
}
