package net.minecraft.world.entity.projectile;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class Snowball extends ThrowableItemProjectile {

    public Snowball(EntityType<? extends Snowball> type, Level world) {
        super(type, world);
    }

    public Snowball(Level world, LivingEntity owner) {
        super(EntityType.SNOWBALL, owner, world);
    }

    public Snowball(Level world, double x, double y, double z) {
        super(EntityType.SNOWBALL, x, y, z, world);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SNOWBALL;
    }

    private ParticleOptions getParticle() {
        ItemStack itemstack = this.getItem();

        return (ParticleOptions) (!itemstack.isEmpty() && !itemstack.is(this.getDefaultItem()) ? new ItemParticleOption(ParticleTypes.ITEM, itemstack) : ParticleTypes.ITEM_SNOWBALL);
    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 3) {
            ParticleOptions particleparam = this.getParticle();

            for (int i = 0; i < 8; ++i) {
                this.level().addParticle(particleparam, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
            }
        }

    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        Entity entity = entityHitResult.getEntity();
        int i = entity.level().purpurConfig.snowballDamage >= 0 ? entity.level().purpurConfig.snowballDamage : entity instanceof Blaze ? 3 : 0; // Purpur

        entity.hurt(this.damageSources().thrown(this, this.getOwner()), (float) i);
    }

    // Purpur start - borrowed and modified code from ThrownPotion#onHitBlock and ThrownPotion#dowseFire
    @Override
    protected void onHitBlock(net.minecraft.world.phys.BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);

        if (!this.level().isClientSide) {
            net.minecraft.core.BlockPos blockposition = blockHitResult.getBlockPos();
            net.minecraft.core.BlockPos blockposition1 = blockposition.relative(blockHitResult.getDirection());

            net.minecraft.world.level.block.state.BlockState iblockdata = this.level().getBlockState(blockposition);

            if (this.level().purpurConfig.snowballExtinguishesFire && this.level().getBlockState(blockposition1).is(net.minecraft.world.level.block.Blocks.FIRE)) {
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this, blockposition1, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())) {
                    this.level().removeBlock(blockposition1, false);
                }
            } else if (this.level().purpurConfig.snowballExtinguishesCandles && net.minecraft.world.level.block.AbstractCandleBlock.isLit(iblockdata)) {
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this, blockposition, iblockdata.setValue(net.minecraft.world.level.block.AbstractCandleBlock.LIT, false))) {
                    net.minecraft.world.level.block.AbstractCandleBlock.extinguish(null, iblockdata, this.level(), blockposition);
                }
            } else if (this.level().purpurConfig.snowballExtinguishesCampfires && net.minecraft.world.level.block.CampfireBlock.isLitCampfire(iblockdata)) {
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this, blockposition, iblockdata.setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false))) {
                    this.level().levelEvent(null, 1009, blockposition, 0);
                    net.minecraft.world.level.block.CampfireBlock.dowse(this.getOwner(), this.level(), blockposition, iblockdata);
                    this.level().setBlockAndUpdate(blockposition, iblockdata.setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
                }
            }
        }
    }
    // Purpur end

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide) {
            this.level().broadcastEntityEvent(this, (byte) 3);
            this.discard(EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }

    }
}
