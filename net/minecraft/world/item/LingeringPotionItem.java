package net.minecraft.world.item;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;

public class LingeringPotionItem extends ThrowablePotionItem {
    public LingeringPotionItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        PotionContents potionContents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        potionContents.addPotionTooltip(tooltipComponents::add, 0.25F, context.tickRate());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // Paper start - PlayerLaunchProjectileEvent
        final InteractionResult wrapper = super.use(level, player, hand);
        if (wrapper instanceof InteractionResult.Fail) return wrapper;
        // Paper end - PlayerLaunchProjectileEvent
        level.playSound(
            null,
            player.getX(),
            player.getY(),
            player.getZ(),
            SoundEvents.LINGERING_POTION_THROW,
            SoundSource.NEUTRAL,
            0.5F,
            0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F)
        );
        return wrapper; // Paper - PlayerLaunchProjectileEvent
    }
}
