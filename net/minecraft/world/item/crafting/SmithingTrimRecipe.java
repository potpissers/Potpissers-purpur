package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.item.equipment.trim.TrimPatterns;

public class SmithingTrimRecipe implements SmithingRecipe {
    final Optional<Ingredient> template;
    final Optional<Ingredient> base;
    final Optional<Ingredient> addition;
    @Nullable
    private PlacementInfo placementInfo;
    final boolean copyDataComponents; // Paper - Option to prevent data components copy

    public SmithingTrimRecipe(Optional<Ingredient> template, Optional<Ingredient> base, Optional<Ingredient> addition) {
        // Paper start - Option to prevent data components copy
        this(template, base, addition, true);
    }
    public SmithingTrimRecipe(Optional<Ingredient> template, Optional<Ingredient> base, Optional<Ingredient> addition, boolean copyDataComponents) {
        this.copyDataComponents = copyDataComponents;
        // Paper end - Option to prevent data components copy
        this.template = template;
        this.base = base;
        this.addition = addition;
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
        return applyTrim(registries, input.base(), input.addition(), input.template(), this.copyDataComponents); // Paper - Option to prevent data components copy
    }

    public static ItemStack applyTrim(HolderLookup.Provider registries, ItemStack base, ItemStack addition, ItemStack template) {
        // Paper start - Option to prevent data components copy
        return applyTrim(registries, base, addition, template, true);
    }
    public static ItemStack applyTrim(HolderLookup.Provider registries, ItemStack base, ItemStack addition, ItemStack template, boolean copyDataComponents) {
        // Paper end - Option to prevent data components copy
        Optional<Holder.Reference<TrimMaterial>> fromIngredient = TrimMaterials.getFromIngredient(registries, addition);
        Optional<Holder.Reference<TrimPattern>> fromTemplate = TrimPatterns.getFromTemplate(registries, template);
        if (fromIngredient.isPresent() && fromTemplate.isPresent()) {
            ArmorTrim armorTrim = base.get(DataComponents.TRIM);
            if (armorTrim != null && armorTrim.hasPatternAndMaterial(fromTemplate.get(), fromIngredient.get())) {
                return ItemStack.EMPTY;
            } else {
                ItemStack itemStack = copyDataComponents ? base.copyWithCount(1) : new ItemStack(base.getItem(), 1); // Paper - Option to prevent data components copy
                itemStack.set(DataComponents.TRIM, new ArmorTrim(fromIngredient.get(), fromTemplate.get()));
                return itemStack;
            }
        } else {
            return ItemStack.EMPTY;
        }
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
    public RecipeSerializer<SmithingTrimRecipe> getSerializer() {
        return RecipeSerializer.SMITHING_TRIM;
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
        SlotDisplay slotDisplay = Ingredient.optionalIngredientToDisplay(this.base);
        SlotDisplay slotDisplay1 = Ingredient.optionalIngredientToDisplay(this.addition);
        SlotDisplay slotDisplay2 = Ingredient.optionalIngredientToDisplay(this.template);
        return List.of(
            new SmithingRecipeDisplay(
                slotDisplay2,
                slotDisplay,
                slotDisplay1,
                new SlotDisplay.SmithingTrimDemoSlotDisplay(slotDisplay, slotDisplay1, slotDisplay2),
                new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)
            )
        );
    }

    // CraftBukkit start
    @Override
    public org.bukkit.inventory.Recipe toBukkitRecipe(org.bukkit.NamespacedKey id) {
        return new org.bukkit.craftbukkit.inventory.CraftSmithingTrimRecipe(id, org.bukkit.craftbukkit.inventory.CraftRecipe.toBukkit(this.template), org.bukkit.craftbukkit.inventory.CraftRecipe.toBukkit(this.base), org.bukkit.craftbukkit.inventory.CraftRecipe.toBukkit(this.addition), this.copyDataComponents); // Paper - Option to prevent data components copy
    }
    // CraftBukkit end

    public static class Serializer implements RecipeSerializer<SmithingTrimRecipe> {
        private static final MapCodec<SmithingTrimRecipe> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Ingredient.CODEC.optionalFieldOf("template").forGetter(recipe -> recipe.template),
                    Ingredient.CODEC.optionalFieldOf("base").forGetter(recipe -> recipe.base),
                    Ingredient.CODEC.optionalFieldOf("addition").forGetter(recipe -> recipe.addition)
                )
                .apply(instance, SmithingTrimRecipe::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, SmithingTrimRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC,
            recipe -> recipe.template,
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC,
            recipe -> recipe.base,
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC,
            recipe -> recipe.addition,
            SmithingTrimRecipe::new
        );

        @Override
        public MapCodec<SmithingTrimRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SmithingTrimRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
