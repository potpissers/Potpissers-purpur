package net.minecraft.world.item;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

public class WrittenBookItem extends Item {
    public WrittenBookItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        WrittenBookContent writtenBookContent = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (writtenBookContent != null) {
            if (!StringUtil.isBlank(writtenBookContent.author())) {
                tooltipComponents.add(Component.translatable("book.byAuthor", writtenBookContent.author()).withStyle(ChatFormatting.GRAY));
            }

            tooltipComponents.add(Component.translatable("book.generation." + writtenBookContent.generation()).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        player.openItemGui(itemInHand, hand);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS;
    }

    public static boolean resolveBookComponents(ItemStack bookStack, CommandSourceStack resolvingSource, @Nullable Player resolvingPlayer) {
        WrittenBookContent writtenBookContent = bookStack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (io.papermc.paper.configuration.GlobalConfiguration.get().itemValidation.resolveSelectorsInBooks && writtenBookContent != null && !writtenBookContent.resolved()) { // Paper - Disable component selector resolving in books by default
            WrittenBookContent writtenBookContent1 = writtenBookContent.resolve(resolvingSource, resolvingPlayer);
            if (writtenBookContent1 != null) {
                bookStack.set(DataComponents.WRITTEN_BOOK_CONTENT, writtenBookContent1);
                return true;
            }

            bookStack.set(DataComponents.WRITTEN_BOOK_CONTENT, writtenBookContent.markResolved());
        }

        return false;
    }
}
