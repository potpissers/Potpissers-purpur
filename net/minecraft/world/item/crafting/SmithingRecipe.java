package net.minecraft.world.item.crafting;

import java.util.Optional;
import net.minecraft.world.level.Level;

public interface SmithingRecipe extends Recipe<SmithingRecipeInput> {
    @Override
    default RecipeType<SmithingRecipe> getType() {
        return RecipeType.SMITHING;
    }

    @Override
    RecipeSerializer<? extends SmithingRecipe> getSerializer();

    @Override
    default boolean matches(SmithingRecipeInput input, Level level) {
        return Ingredient.testOptionalIngredient(this.templateIngredient(), input.template())
            && Ingredient.testOptionalIngredient(this.baseIngredient(), input.base())
            && Ingredient.testOptionalIngredient(this.additionIngredient(), input.addition());
    }

    Optional<Ingredient> templateIngredient();

    Optional<Ingredient> baseIngredient();

    Optional<Ingredient> additionIngredient();

    @Override
    default RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.SMITHING;
    }
}
