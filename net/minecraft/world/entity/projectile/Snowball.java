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

public class Snowball extends ThrowableItemProjectile {
    public Snowball(EntityType<? extends Snowball> entityType, Level level) {
        super(entityType, level);
    }

    public Snowball(Level level, LivingEntity owner, ItemStack item) {
        super(EntityType.SNOWBALL, owner, level, item);
    }

    public Snowball(Level level, double x, double y, double z, ItemStack item) {
        super(EntityType.SNOWBALL, x, y, z, level, item);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SNOWBALL;
    }

    private ParticleOptions getParticle() {
        ItemStack item = this.getItem();
        return (ParticleOptions)(item.isEmpty() ? ParticleTypes.ITEM_SNOWBALL : new ItemParticleOption(ParticleTypes.ITEM, item));
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3) {
            ParticleOptions particle = this.getParticle();

            for (int i = 0; i < 8; i++) {
                this.level().addParticle(particle, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity entity = result.getEntity();
        int i = entity.level().purpurConfig.snowballDamage >= 0 ? entity.level().purpurConfig.snowballDamage : entity instanceof Blaze ? 3 : 0; // Purpur - Add configurable snowball damage
        entity.hurt(this.damageSources().thrown(this, this.getOwner()), i);
    }

    // Purpur start - options to extinguish fire blocks with snowballs - borrowed and modified code from ThrownPotion#onHitBlock and ThrownPotion#dowseFire
    @Override
    protected void onHitBlock(net.minecraft.world.phys.BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);

        if (!this.level().isClientSide) {
            net.minecraft.core.BlockPos pos = blockHitResult.getBlockPos();
            net.minecraft.core.BlockPos relativePos = pos.relative(blockHitResult.getDirection());

            net.minecraft.world.level.block.state.BlockState blockState = this.level().getBlockState(pos);

            if (this.level().purpurConfig.snowballExtinguishesFire && this.level().getBlockState(relativePos).is(net.minecraft.world.level.block.Blocks.FIRE)) {
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this, relativePos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())) {
                    this.level().removeBlock(relativePos, false);
                }
            } else if (this.level().purpurConfig.snowballExtinguishesCandles && net.minecraft.world.level.block.AbstractCandleBlock.isLit(blockState)) {
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this, pos, blockState.setValue(net.minecraft.world.level.block.AbstractCandleBlock.LIT, false))) {
                    net.minecraft.world.level.block.AbstractCandleBlock.extinguish(null, blockState, this.level(), pos);
                }
            } else if (this.level().purpurConfig.snowballExtinguishesCampfires && net.minecraft.world.level.block.CampfireBlock.isLitCampfire(blockState)) {
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this, pos, blockState.setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false))) {
                    this.level().levelEvent(null, 1009, pos, 0);
                    net.minecraft.world.level.block.CampfireBlock.dowse(this.getOwner(), this.level(), pos, blockState);
                    this.level().setBlockAndUpdate(pos, blockState.setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
                }
            }
        }
    }
    // Purpur end - options to extinguish fire blocks with snowballs

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            this.level().broadcastEntityEvent(this, (byte)3);
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }
    }
}
