package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public record EnchantmentPredicate(Optional<HolderSet<Enchantment>> enchantments, MinMaxBounds.Ints level) {
    public static final Codec<EnchantmentPredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                RegistryCodecs.homogeneousList(Registries.ENCHANTMENT).optionalFieldOf("enchantments").forGetter(EnchantmentPredicate::enchantments),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("levels", MinMaxBounds.Ints.ANY).forGetter(EnchantmentPredicate::level)
            )
            .apply(instance, EnchantmentPredicate::new)
    );

    public EnchantmentPredicate(Holder<Enchantment> enchantment, MinMaxBounds.Ints level) {
        this(Optional.of(HolderSet.direct(enchantment)), level);
    }

    public EnchantmentPredicate(HolderSet<Enchantment> enchantments, MinMaxBounds.Ints level) {
        this(Optional.of(enchantments), level);
    }

    public boolean containedIn(ItemEnchantments enchantments) {
        if (this.enchantments.isPresent()) {
            for (Holder<Enchantment> holder : this.enchantments.get()) {
                if (this.matchesEnchantment(enchantments, holder)) {
                    return true;
                }
            }

            return false;
        } else if (this.level != MinMaxBounds.Ints.ANY) {
            for (Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
                if (this.level.matches(entry.getIntValue())) {
                    return true;
                }
            }

            return false;
        } else {
            return !enchantments.isEmpty();
        }
    }

    private boolean matchesEnchantment(ItemEnchantments itemEnchantments, Holder<Enchantment> enchantment) {
        int level = itemEnchantments.getLevel(enchantment);
        return level != 0 && (this.level == MinMaxBounds.Ints.ANY || this.level.matches(level));
    }
}
