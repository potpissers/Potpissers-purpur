package org.bukkit.craftbukkit.projectiles;

import com.google.common.base.Preconditions;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LingeringPotion;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.TippedArrow;
import org.bukkit.entity.WitherSkull;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionType;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.util.Vector;

public class CraftBlockProjectileSource implements BlockProjectileSource {
    private final DispenserBlockEntity dispenserBlock;

    public CraftBlockProjectileSource(DispenserBlockEntity dispenserBlock) {
        this.dispenserBlock = dispenserBlock;
    }

    @Override
    public Block getBlock() {
        return this.dispenserBlock.getLevel().getWorld().getBlockAt(this.dispenserBlock.getBlockPos().getX(), this.dispenserBlock.getBlockPos().getY(), this.dispenserBlock.getBlockPos().getZ());
    }

    @Override
    public <T extends Projectile> T launchProjectile(Class<? extends T> projectile) {
        return this.launchProjectile(projectile, null);
    }

    @Override
    public <T extends Projectile> T launchProjectile(Class<? extends T> projectile, Vector velocity) {
        // Paper start - launchProjectile consumer
        return this.launchProjectile(projectile, velocity, null);
    }

    @Override
    public <T extends Projectile> T launchProjectile(Class<? extends T> projectile, Vector velocity, java.util.function.Consumer<? super T> function) {
        // Paper end - launchProjectile consumer
        Preconditions.checkArgument(this.getBlock().getType() == Material.DISPENSER, "Block is no longer dispenser");

        // Paper start - rewrite whole method to match ProjectileDispenseBehavior
        net.minecraft.world.item.Item item = null;
        if (Snowball.class.isAssignableFrom(projectile)) {
            item = net.minecraft.world.item.Items.SNOWBALL;
        } else if (Egg.class.isAssignableFrom(projectile)) {
            item = net.minecraft.world.item.Items.EGG;
        } else if (ThrownExpBottle.class.isAssignableFrom(projectile)) {
            item = net.minecraft.world.item.Items.EXPERIENCE_BOTTLE;
        } else if (ThrownPotion.class.isAssignableFrom(projectile)) {
            if (LingeringPotion.class.isAssignableFrom(projectile)) {
                item = net.minecraft.world.item.Items.LINGERING_POTION;
            } else {
                item = net.minecraft.world.item.Items.SPLASH_POTION;
            }
        } else if (AbstractArrow.class.isAssignableFrom(projectile)) {
            if (SpectralArrow.class.isAssignableFrom(projectile)) {
                item = net.minecraft.world.item.Items.SPECTRAL_ARROW;
            } else {
                item = net.minecraft.world.item.Items.ARROW;
            }
        } else if (org.bukkit.entity.WindCharge.class.isAssignableFrom(projectile)) {
            item = net.minecraft.world.item.Items.WIND_CHARGE;
        } else if (org.bukkit.entity.Firework.class.isAssignableFrom(projectile)) {
            item = net.minecraft.world.item.Items.FIREWORK_ROCKET;
        } else if (SmallFireball.class.isAssignableFrom(projectile)) {
            item = net.minecraft.world.item.Items.FIRE_CHARGE;
        }

        if (!(item instanceof net.minecraft.world.item.ProjectileItem projectileItem)) {
            throw new IllegalArgumentException("Projectile '%s' is not supported".formatted(projectile.getSimpleName()));
        }

        net.minecraft.world.item.ProjectileItem.DispenseConfig config = projectileItem.createDispenseConfig();
        net.minecraft.world.level.block.state.BlockState state = this.dispenserBlock.getBlockState();
        net.minecraft.world.level.Level world = this.dispenserBlock.getLevel();
        BlockSource pointer = new BlockSource((ServerLevel) world, this.dispenserBlock.getBlockPos(), state, this.dispenserBlock); // copied from DispenseBlock#dispenseFrom
        Direction facing = state.getValue(DispenserBlock.FACING);
        Position pos = config.positionFunction().getDispensePosition(pointer, facing);

        net.minecraft.world.entity.projectile.Projectile launch = projectileItem.asProjectile(world, pos, new net.minecraft.world.item.ItemStack(item), facing);
        // some projectile are not shoot and doesn't rely on the config for power/uncertainty
        projectileItem.shoot(launch, facing.getStepX(), facing.getStepY(), facing.getStepZ(), config.power(), config.uncertainty());
        launch.projectileSource = this;
        // Paper end

        if (velocity != null) {
            ((T) launch.getBukkitEntity()).setVelocity(velocity);
        }
        // Paper start
        if (function != null) {
            function.accept((T) launch.getBukkitEntity());
        }
        // Paper end

        world.addFreshEntity(launch);
        return (T) launch.getBukkitEntity();
    }
}
