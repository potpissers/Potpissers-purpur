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
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.level.Level;

public class EggItem extends Item implements ProjectileItem {
    public static float PROJECTILE_SHOOT_POWER = 1.5F;

    public EggItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel) {
            // CraftBukkit start
            // Paper start - PlayerLaunchProjectileEvent
            final Projectile.Delayed<ThrownEgg> thrownEgg = Projectile.spawnProjectileFromRotationDelayed(ThrownEgg::new, serverLevel, itemInHand, player, 0.0F, EggItem.PROJECTILE_SHOOT_POWER, (float) serverLevel.purpurConfig.eggProjectileOffset); // Purpur - Projectile offset config
            com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent event = new com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent((org.bukkit.entity.Player) player.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemInHand), (org.bukkit.entity.Projectile) thrownEgg.projectile().getBukkitEntity());
            if (event.callEvent() && thrownEgg.attemptSpawn()) {
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
                    SoundEvents.EGG_THROW,
                    SoundSource.PLAYERS,
                    0.5F,
                    0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F)
                );
                player.awardStat(Stats.ITEM_USED.get(this));
            } else {
                // Paper end - PlayerLaunchProjectileEvent
                player.containerMenu.sendAllDataToRemote();
                return InteractionResult.FAIL;
            }
            // CraftBukkit end
        }
        // Paper - PlayerLaunchProjectileEvent - moved up
        return InteractionResult.SUCCESS;
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        return new ThrownEgg(level, pos.x(), pos.y(), pos.z(), stack);
    }
}
