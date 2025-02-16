package net.minecraft.world.item;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.equipment.ArmorMaterial;

public class AnimalArmorItem extends Item {
    private final AnimalArmorItem.BodyType bodyType;

    public AnimalArmorItem(ArmorMaterial properties, AnimalArmorItem.BodyType bodyType, Item.Properties properties1) {
        super(properties.animalProperties(properties1, bodyType.allowedEntities));
        this.bodyType = bodyType;
    }

    public AnimalArmorItem(
        ArmorMaterial properties, AnimalArmorItem.BodyType bodyType, Holder<SoundEvent> equipSound, boolean damageOnHurt, Item.Properties properties1
    ) {
        super(properties.animalProperties(properties1, equipSound, damageOnHurt, bodyType.allowedEntities));
        this.bodyType = bodyType;
    }

    @Override
    public SoundEvent getBreakingSound() {
        return this.bodyType.breakingSound;
    }

    public static enum BodyType {
        EQUESTRIAN(SoundEvents.ITEM_BREAK, EntityType.HORSE),
        CANINE(SoundEvents.WOLF_ARMOR_BREAK, EntityType.WOLF);

        final SoundEvent breakingSound;
        final HolderSet<EntityType<?>> allowedEntities;

        private BodyType(final SoundEvent breakingSound, final EntityType<?>... allowedEntities) {
            this.breakingSound = breakingSound;
            this.allowedEntities = HolderSet.direct(EntityType::builtInRegistryHolder, allowedEntities);
        }
    }
}
