package net.minecraft.world.level.storage.loot.functions;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContextUser;

public interface LootItemFunction extends LootContextUser, BiFunction<ItemStack, LootContext, ItemStack> {
    LootItemFunctionType<? extends LootItemFunction> getType();

    static Consumer<ItemStack> decorate(
        BiFunction<ItemStack, LootContext, ItemStack> stackModification, Consumer<ItemStack> originalConsumer, LootContext lootContext
    ) {
        return itemStack -> originalConsumer.accept(stackModification.apply(itemStack, lootContext));
    }

    public interface Builder {
        LootItemFunction build();
    }
}
