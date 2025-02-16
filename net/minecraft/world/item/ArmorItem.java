package net.minecraft.world.item;

import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;

public class ArmorItem extends Item {
    public ArmorItem(ArmorMaterial properties, ArmorType armorType, Item.Properties properties1) {
        super(properties.humanoidProperties(properties1, armorType));
    }
}
