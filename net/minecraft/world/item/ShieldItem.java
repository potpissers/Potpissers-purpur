package net.minecraft.world.item;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class ShieldItem extends Item {
    public static final int EFFECTIVE_BLOCK_DELAY = 5;
    public static final float MINIMUM_DURABILITY_DAMAGE = 3.0F;

    public ShieldItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        DyeColor dyeColor = stack.get(DataComponents.BASE_COLOR);
        return (Component)(dyeColor != null ? Component.translatable(this.descriptionId + "." + dyeColor.getName()) : super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        BannerItem.appendHoverTextFromBannerBlockEntityTag(stack, tooltipComponents);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }
}
