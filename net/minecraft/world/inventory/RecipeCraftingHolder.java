package net.minecraft.world.inventory;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameRules;

public interface RecipeCraftingHolder {
    void setRecipeUsed(@Nullable RecipeHolder<?> recipe);

    @Nullable
    RecipeHolder<?> getRecipeUsed();

    default void awardUsedRecipes(Player player, List<ItemStack> items) {
        RecipeHolder<?> recipeUsed = this.getRecipeUsed();
        if (recipeUsed != null) {
            player.triggerRecipeCrafted(recipeUsed, items);
            if (!recipeUsed.value().isSpecial()) {
                player.awardRecipes(Collections.singleton(recipeUsed));
                this.setRecipeUsed(null);
            }
        }
    }

    default boolean setRecipeUsed(ServerPlayer player, RecipeHolder<?> recipe) {
        if (!recipe.value().isSpecial()
            && player.serverLevel().getGameRules().getBoolean(GameRules.RULE_LIMITED_CRAFTING)
            && !player.getRecipeBook().contains(recipe.id())) {
            return false;
        } else {
            this.setRecipeUsed(recipe);
            return true;
        }
    }
}
