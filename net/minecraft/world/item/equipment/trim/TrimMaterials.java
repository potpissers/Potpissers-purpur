package net.minecraft.world.item.equipment.trim;

import java.util.Map;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

public class TrimMaterials {
    public static final ResourceKey<TrimMaterial> QUARTZ = registryKey("quartz");
    public static final ResourceKey<TrimMaterial> IRON = registryKey("iron");
    public static final ResourceKey<TrimMaterial> NETHERITE = registryKey("netherite");
    public static final ResourceKey<TrimMaterial> REDSTONE = registryKey("redstone");
    public static final ResourceKey<TrimMaterial> COPPER = registryKey("copper");
    public static final ResourceKey<TrimMaterial> GOLD = registryKey("gold");
    public static final ResourceKey<TrimMaterial> EMERALD = registryKey("emerald");
    public static final ResourceKey<TrimMaterial> DIAMOND = registryKey("diamond");
    public static final ResourceKey<TrimMaterial> LAPIS = registryKey("lapis");
    public static final ResourceKey<TrimMaterial> AMETHYST = registryKey("amethyst");
    public static final ResourceKey<TrimMaterial> RESIN = registryKey("resin");

    public static void bootstrap(BootstrapContext<TrimMaterial> context) {
        register(context, QUARTZ, Items.QUARTZ, Style.EMPTY.withColor(14931140));
        register(context, IRON, Items.IRON_INGOT, Style.EMPTY.withColor(15527148), Map.of(EquipmentAssets.IRON, "iron_darker"));
        register(context, NETHERITE, Items.NETHERITE_INGOT, Style.EMPTY.withColor(6445145), Map.of(EquipmentAssets.NETHERITE, "netherite_darker"));
        register(context, REDSTONE, Items.REDSTONE, Style.EMPTY.withColor(9901575));
        register(context, COPPER, Items.COPPER_INGOT, Style.EMPTY.withColor(11823181));
        register(context, GOLD, Items.GOLD_INGOT, Style.EMPTY.withColor(14594349), Map.of(EquipmentAssets.GOLD, "gold_darker"));
        register(context, EMERALD, Items.EMERALD, Style.EMPTY.withColor(1155126));
        register(context, DIAMOND, Items.DIAMOND, Style.EMPTY.withColor(7269586), Map.of(EquipmentAssets.DIAMOND, "diamond_darker"));
        register(context, LAPIS, Items.LAPIS_LAZULI, Style.EMPTY.withColor(4288151));
        register(context, AMETHYST, Items.AMETHYST_SHARD, Style.EMPTY.withColor(10116294));
        register(context, RESIN, Items.RESIN_BRICK, Style.EMPTY.withColor(16545810));
    }

    public static Optional<Holder.Reference<TrimMaterial>> getFromIngredient(HolderLookup.Provider registries, ItemStack ingredient) {
        return registries.lookupOrThrow(Registries.TRIM_MATERIAL).listElements().filter(reference -> ingredient.is(reference.value().ingredient())).findFirst();
    }

    private static void register(BootstrapContext<TrimMaterial> context, ResourceKey<TrimMaterial> materialKey, Item ingredient, Style style) {
        register(context, materialKey, ingredient, style, Map.of());
    }

    private static void register(
        BootstrapContext<TrimMaterial> context,
        ResourceKey<TrimMaterial> materialKey,
        Item ingredient,
        Style style,
        Map<ResourceKey<EquipmentAsset>, String> overrideArmorMaterials
    ) {
        TrimMaterial trimMaterial = TrimMaterial.create(
            materialKey.location().getPath(),
            ingredient,
            Component.translatable(Util.makeDescriptionId("trim_material", materialKey.location())).withStyle(style),
            overrideArmorMaterials
        );
        context.register(materialKey, trimMaterial);
    }

    private static ResourceKey<TrimMaterial> registryKey(String name) {
        return ResourceKey.create(Registries.TRIM_MATERIAL, ResourceLocation.withDefaultNamespace(name));
    }
}
