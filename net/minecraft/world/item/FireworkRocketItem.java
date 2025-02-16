package net.minecraft.world.item;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class FireworkRocketItem extends Item implements ProjectileItem {
    public static final byte[] CRAFTABLE_DURATIONS = new byte[]{1, 2, 3};
    public static final double ROCKET_PLACEMENT_OFFSET = 0.15;

    public FireworkRocketItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level instanceof ServerLevel serverLevel) {
            ItemStack itemInHand = context.getItemInHand();
            Vec3 clickLocation = context.getClickLocation();
            Direction clickedFace = context.getClickedFace();
            Projectile.spawnProjectile(
                new FireworkRocketEntity(
                    level,
                    context.getPlayer(),
                    clickLocation.x + clickedFace.getStepX() * 0.15,
                    clickLocation.y + clickedFace.getStepY() * 0.15,
                    clickLocation.z + clickedFace.getStepZ() * 0.15,
                    itemInHand
                ),
                serverLevel,
                itemInHand
            );
            itemInHand.shrink(1);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isFallFlying()) {
            ItemStack itemInHand = player.getItemInHand(hand);
            if (level instanceof ServerLevel serverLevel) {
                Projectile.spawnProjectile(new FireworkRocketEntity(level, itemInHand, player), serverLevel, itemInHand);
                itemInHand.consume(1, player);
                player.awardStat(Stats.ITEM_USED.get(this));
            }

            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
        if (fireworks != null) {
            fireworks.addToTooltip(context, tooltipComponents::add, tooltipFlag);
        }
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        return new FireworkRocketEntity(level, stack.copyWithCount(1), pos.x(), pos.y(), pos.z(), true);
    }

    @Override
    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder()
            .positionFunction(FireworkRocketItem::getEntityJustOutsideOfBlockPos)
            .uncertainty(1.0F)
            .power(0.5F)
            .overrideDispenseEvent(1004)
            .build();
    }

    private static Vec3 getEntityJustOutsideOfBlockPos(BlockSource blockSource, Direction direction) {
        return blockSource.center()
            .add(direction.getStepX() * 0.5000099999997474, direction.getStepY() * 0.5000099999997474, direction.getStepZ() * 0.5000099999997474);
    }
}
