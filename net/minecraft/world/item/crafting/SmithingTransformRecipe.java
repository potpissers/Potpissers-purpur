package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;

public class SmithingTransformRecipe implements SmithingRecipe {
    final Optional<Ingredient> template;
    final Optional<Ingredient> base;
    final Optional<Ingredient> addition;
    final ItemStack result;
    @Nullable
    private PlacementInfo placementInfo;
    final boolean copyDataComponents; // Paper - Option to prevent data components copy

    public SmithingTransformRecipe(Optional<Ingredient> template, Optional<Ingredient> base, Optional<Ingredient> addition, ItemStack result) {
        // Paper start - Option to prevent data components copy
        this(template, base, addition, result, true);
    }
    public SmithingTransformRecipe(Optional<Ingredient> template, Optional<Ingredient> base, Optional<Ingredient> addition, ItemStack result, boolean copyDataComponents) {
        this.copyDataComponents = copyDataComponents;
        // Paper end - Option to prevent data components copy
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.result = result;
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
        ItemStack itemStack = input.base().transmuteCopy(this.result.getItem(), this.result.getCount());
        if (this.copyDataComponents) { // Paper - Option to prevent data components copy
        itemStack.applyComponents(this.result.getComponentsPatch());
        } // Paper - Option to prevent data components copy
        return itemStack;
    }

    @Override
    public Optional<Ingredient> templateIngredient() {
        return this.template;
    }

    @Override
    public Optional<Ingredient> baseIngredient() {
        return this.base;
    }

    @Override
    public Optional<Ingredient> additionIngredient() {
        return this.addition;
    }

    @Override
    public RecipeSerializer<SmithingTransformRecipe> getSerializer() {
        return RecipeSerializer.SMITHING_TRANSFORM;
    }

    @Override
    public PlacementInfo placementInfo() {
        if (this.placementInfo == null) {
            this.placementInfo = PlacementInfo.createFromOptionals(List.of(this.template, this.base, this.addition));
        }

        return this.placementInfo;
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(
            new SmithingRecipeDisplay(
                Ingredient.optionalIngredientToDisplay(this.template),
                Ingredient.optionalIngredientToDisplay(this.base),
                Ingredient.optionalIngredientToDisplay(this.addition),
                new SlotDisplay.ItemStackSlotDisplay(this.result),
                new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)
            )
        );
    }

    // CraftBukkit start
    @Override
    public org.bukkit.inventory.Recipe toBukkitRecipe(org.bukkit.NamespacedKey id) {
        org.bukkit.craftbukkit.inventory.CraftItemStack result = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(this.result);

        org.bukkit.craftbukkit.inventory.CraftSmithingTransformRecipe recipe = new org.bukkit.craftbukkit.inventory.CraftSmithingTransformRecipe(id, result, org.bukkit.craftbukkit.inventory.CraftRecipe.toBukkit(this.template), org.bukkit.craftbukkit.inventory.CraftRecipe.toBukkit(this.base), org.bukkit.craftbukkit.inventory.CraftRecipe.toBukkit(this.addition), this.copyDataComponents); // Paper - Option to prevent data components copy

        return recipe;
    }
    // CraftBukkit end

    public static class Serializer implements RecipeSerializer<SmithingTransformRecipe> {
        private static final MapCodec<SmithingTransformRecipe> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Ingredient.CODEC.optionalFieldOf("template").forGetter(recipe -> recipe.template),
                    Ingredient.CODEC.optionalFieldOf("base").forGetter(recipe -> recipe.base),
                    Ingredient.CODEC.optionalFieldOf("addition").forGetter(recipe -> recipe.addition),
                    ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
                )
                .apply(instance, SmithingTransformRecipe::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, SmithingTransformRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC,
            recipe -> recipe.template,
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC,
            recipe -> recipe.base,
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC,
            recipe -> recipe.addition,
            ItemStack.STREAM_CODEC,
            recipe -> recipe.result,
            SmithingTransformRecipe::new
        );

        @Override
        public MapCodec<SmithingTransformRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SmithingTransformRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
