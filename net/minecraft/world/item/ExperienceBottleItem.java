package net.minecraft.world.item;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.level.Level;

public class ExperienceBottleItem extends Item implements ProjectileItem {
    public ExperienceBottleItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        // Paper - PlayerLaunchProjectileEvent - moved down
        if (level instanceof ServerLevel serverLevel) {
            // Paper start - PlayerLaunchProjectileEvent
            final Projectile.Delayed<ThrownExperienceBottle> thrownExperienceBottle = Projectile.spawnProjectileFromRotationDelayed(ThrownExperienceBottle::new, serverLevel, itemInHand, player, -20.0F, 0.7F, 1.0F);
            com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent event = new com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent((org.bukkit.entity.Player) player.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemInHand), (org.bukkit.entity.Projectile) thrownExperienceBottle.projectile().getBukkitEntity());
            if (event.callEvent() && thrownExperienceBottle.attemptSpawn()) {
                if (event.shouldConsume()) {
                    itemInHand.consume(1, player);
                } else {
                    player.containerMenu.sendAllDataToRemote();
                }

                level.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.EXPERIENCE_BOTTLE_THROW,
                    SoundSource.NEUTRAL,
                    0.5F,
                    0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F)
                );
            } else {
                player.containerMenu.sendAllDataToRemote();
                return InteractionResult.FAIL;
            }
            // Paper end - PlayerLaunchProjectileEvent
        }

        // Paper - PlayerLaunchProjectileEvent - moved up
        return InteractionResult.SUCCESS;
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        return new ThrownExperienceBottle(level, pos.x(), pos.y(), pos.z(), stack);
    }

    @Override
    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder()
            .uncertainty(ProjectileItem.DispenseConfig.DEFAULT.uncertainty() * 0.5F)
            .power(ProjectileItem.DispenseConfig.DEFAULT.power() * 1.25F)
            .build();
    }
}
