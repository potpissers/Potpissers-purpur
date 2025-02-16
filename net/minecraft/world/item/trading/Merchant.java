package net.minecraft.world.item.trading;

import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;

public interface Merchant {
    void setTradingPlayer(@Nullable Player tradingPlayer);

    @Nullable
    Player getTradingPlayer();

    MerchantOffers getOffers();

    void overrideOffers(MerchantOffers offers);

    void notifyTrade(MerchantOffer offer);

    void notifyTradeUpdated(ItemStack stack);

    int getVillagerXp();

    void overrideXp(int xp);

    boolean showProgressBar();

    SoundEvent getNotifyTradeSound();

    default boolean canRestock() {
        return false;
    }

    default void openTradingScreen(Player player, Component displayName, int level) {
        OptionalInt optionalInt = player.openMenu(
            new SimpleMenuProvider((containerId, inventory, player1) -> new MerchantMenu(containerId, inventory, this), displayName)
        );
        if (optionalInt.isPresent()) {
            MerchantOffers offers = this.getOffers();
            if (!offers.isEmpty()) {
                player.sendMerchantOffers(optionalInt.getAsInt(), offers, level, this.getVillagerXp(), this.showProgressBar(), this.canRestock());
            }
        }
    }

    boolean isClientSide();

    boolean stillValid(Player player);
}
