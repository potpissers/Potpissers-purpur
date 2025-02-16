package net.minecraft.world.item.equipment;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public record ArmorMaterial(
    int durability,
    Map<ArmorType, Integer> defense,
    int enchantmentValue,
    Holder<SoundEvent> equipSound,
    float toughness,
    float knockbackResistance,
    TagKey<Item> repairIngredient,
    ResourceKey<EquipmentAsset> assetId
) {
    public Item.Properties humanoidProperties(Item.Properties properties, ArmorType armorType) {
        return properties.durability(armorType.getDurability(this.durability))
            .attributes(this.createAttributes(armorType))
            .enchantable(this.enchantmentValue)
            .component(DataComponents.EQUIPPABLE, Equippable.builder(armorType.getSlot()).setEquipSound(this.equipSound).setAsset(this.assetId).build())
            .repairable(this.repairIngredient);
    }

    public Item.Properties animalProperties(Item.Properties properties, HolderSet<EntityType<?>> allowedEntities) {
        return properties.durability(ArmorType.BODY.getDurability(this.durability))
            .attributes(this.createAttributes(ArmorType.BODY))
            .repairable(this.repairIngredient)
            .component(
                DataComponents.EQUIPPABLE,
                Equippable.builder(EquipmentSlot.BODY).setEquipSound(this.equipSound).setAsset(this.assetId).setAllowedEntities(allowedEntities).build()
            );
    }

    public Item.Properties animalProperties(
        Item.Properties properties, Holder<SoundEvent> equipSound, boolean damageOnHurt, HolderSet<EntityType<?>> allowedEntities
    ) {
        if (damageOnHurt) {
            properties = properties.durability(ArmorType.BODY.getDurability(this.durability)).repairable(this.repairIngredient);
        }

        return properties.attributes(this.createAttributes(ArmorType.BODY))
            .component(
                DataComponents.EQUIPPABLE,
                Equippable.builder(EquipmentSlot.BODY)
                    .setEquipSound(equipSound)
                    .setAsset(this.assetId)
                    .setAllowedEntities(allowedEntities)
                    .setDamageOnHurt(damageOnHurt)
                    .build()
            );
    }

    private ItemAttributeModifiers createAttributes(ArmorType armorType) {
        int orDefault = this.defense.getOrDefault(armorType, 0);
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        EquipmentSlotGroup equipmentSlotGroup = EquipmentSlotGroup.bySlot(armorType.getSlot());
        ResourceLocation resourceLocation = ResourceLocation.withDefaultNamespace("armor." + armorType.getName());
        builder.add(Attributes.ARMOR, new AttributeModifier(resourceLocation, orDefault, AttributeModifier.Operation.ADD_VALUE), equipmentSlotGroup);
        builder.add(
            Attributes.ARMOR_TOUGHNESS, new AttributeModifier(resourceLocation, this.toughness, AttributeModifier.Operation.ADD_VALUE), equipmentSlotGroup
        );
        if (this.knockbackResistance > 0.0F) {
            builder.add(
                Attributes.KNOCKBACK_RESISTANCE,
                new AttributeModifier(resourceLocation, this.knockbackResistance, AttributeModifier.Operation.ADD_VALUE),
                equipmentSlotGroup
            );
        }

        return builder.build();
    }
}
