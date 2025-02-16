package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.item.equipment.trim.TrimPatterns;
import net.minecraft.world.level.Level;

public class SpawnArmorTrimsCommand {
    private static final List<ResourceKey<TrimPattern>> VANILLA_TRIM_PATTERNS = List.of(
        TrimPatterns.SENTRY,
        TrimPatterns.DUNE,
        TrimPatterns.COAST,
        TrimPatterns.WILD,
        TrimPatterns.WARD,
        TrimPatterns.EYE,
        TrimPatterns.VEX,
        TrimPatterns.TIDE,
        TrimPatterns.SNOUT,
        TrimPatterns.RIB,
        TrimPatterns.SPIRE,
        TrimPatterns.WAYFINDER,
        TrimPatterns.SHAPER,
        TrimPatterns.SILENCE,
        TrimPatterns.RAISER,
        TrimPatterns.HOST,
        TrimPatterns.FLOW,
        TrimPatterns.BOLT
    );
    private static final List<ResourceKey<TrimMaterial>> VANILLA_TRIM_MATERIALS = List.of(
        TrimMaterials.QUARTZ,
        TrimMaterials.IRON,
        TrimMaterials.NETHERITE,
        TrimMaterials.REDSTONE,
        TrimMaterials.COPPER,
        TrimMaterials.GOLD,
        TrimMaterials.EMERALD,
        TrimMaterials.DIAMOND,
        TrimMaterials.LAPIS,
        TrimMaterials.AMETHYST,
        TrimMaterials.RESIN
    );
    private static final ToIntFunction<ResourceKey<TrimPattern>> TRIM_PATTERN_ORDER = Util.createIndexLookup(VANILLA_TRIM_PATTERNS);
    private static final ToIntFunction<ResourceKey<TrimMaterial>> TRIM_MATERIAL_ORDER = Util.createIndexLookup(VANILLA_TRIM_MATERIALS);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("spawn_armor_trims")
                .requires(commandSourceStack -> commandSourceStack.hasPermission(2))
                .executes(commandContext -> spawnArmorTrims(commandContext.getSource(), commandContext.getSource().getPlayerOrException()))
        );
    }

    private static int spawnArmorTrims(CommandSourceStack source, Player player) {
        Level level = player.level();
        NonNullList<ArmorTrim> list = NonNullList.create();
        Registry<TrimPattern> registry = level.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN);
        Registry<TrimMaterial> registry1 = level.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL);
        HolderLookup<Item> holderLookup = level.holderLookup(Registries.ITEM);
        Map<ResourceKey<EquipmentAsset>, List<Item>> map = holderLookup.listElements().map(Holder.Reference::value).filter(item1 -> {
            Equippable equippable1 = item1.components().get(DataComponents.EQUIPPABLE);
            return equippable1 != null && equippable1.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR && equippable1.assetId().isPresent();
        }).collect(Collectors.groupingBy(item1 -> item1.components().get(DataComponents.EQUIPPABLE).assetId().get()));
        registry.stream()
            .sorted(Comparator.comparing(trimPattern -> TRIM_PATTERN_ORDER.applyAsInt(registry.getResourceKey(trimPattern).orElse(null))))
            .forEachOrdered(
                trimPattern -> registry1.stream()
                    .sorted(Comparator.comparing(trimMaterial -> TRIM_MATERIAL_ORDER.applyAsInt(registry1.getResourceKey(trimMaterial).orElse(null))))
                    .forEachOrdered(trimMaterial -> list.add(new ArmorTrim(registry1.wrapAsHolder(trimMaterial), registry.wrapAsHolder(trimPattern))))
            );
        BlockPos blockPos = player.blockPosition().relative(player.getDirection(), 5);
        int i = map.size() - 1;
        double d = 3.0;
        int i1 = 0;
        int i2 = 0;

        for (ArmorTrim armorTrim : list) {
            for (List<Item> list1 : map.values()) {
                double d1 = blockPos.getX() + 0.5 - i1 % registry1.size() * 3.0;
                double d2 = blockPos.getY() + 0.5 + i2 % i * 3.0;
                double d3 = blockPos.getZ() + 0.5 + i1 / registry1.size() * 10;
                ArmorStand armorStand = new ArmorStand(level, d1, d2, d3);
                armorStand.setYRot(180.0F);
                armorStand.setNoGravity(true);

                for (Item item : list1) {
                    Equippable equippable = Objects.requireNonNull(item.components().get(DataComponents.EQUIPPABLE));
                    ItemStack itemStack = new ItemStack(item);
                    itemStack.set(DataComponents.TRIM, armorTrim);
                    armorStand.setItemSlot(equippable.slot(), itemStack);
                    if (itemStack.is(Items.TURTLE_HELMET)) {
                        armorStand.setCustomName(
                            armorTrim.pattern()
                                .value()
                                .copyWithStyle(armorTrim.material())
                                .copy()
                                .append(" ")
                                .append(armorTrim.material().value().description())
                        );
                        armorStand.setCustomNameVisible(true);
                    } else {
                        armorStand.setInvisible(true);
                    }
                }

                level.addFreshEntity(armorStand);
                i2++;
            }

            i1++;
        }

        source.sendSuccess(() -> Component.literal("Armorstands with trimmed armor spawned around you"), true);
        return 1;
    }
}
