package net.minecraft.world.item;

import net.minecraft.tags.BlockTags;

public class PickaxeItem extends DiggerItem {
    public PickaxeItem(ToolMaterial material, float attackDamage, float attackSpeed, Item.Properties properties) {
        super(material, BlockTags.MINEABLE_WITH_PICKAXE, attackDamage, attackSpeed, properties);
    }
}
