package net.minecraft.world.item.equipment.trim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

public record ArmorTrim(Holder<TrimMaterial> material, Holder<TrimPattern> pattern, boolean showInTooltip) implements TooltipProvider {
    public static final Codec<ArmorTrim> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                TrimMaterial.CODEC.fieldOf("material").forGetter(ArmorTrim::material),
                TrimPattern.CODEC.fieldOf("pattern").forGetter(ArmorTrim::pattern),
                Codec.BOOL.optionalFieldOf("show_in_tooltip", Boolean.valueOf(true)).forGetter(trim -> trim.showInTooltip)
            )
            .apply(instance, ArmorTrim::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorTrim> STREAM_CODEC = StreamCodec.composite(
        TrimMaterial.STREAM_CODEC,
        ArmorTrim::material,
        TrimPattern.STREAM_CODEC,
        ArmorTrim::pattern,
        ByteBufCodecs.BOOL,
        armorTrim -> armorTrim.showInTooltip,
        ArmorTrim::new
    );
    private static final Component UPGRADE_TITLE = Component.translatable(
            Util.makeDescriptionId("item", ResourceLocation.withDefaultNamespace("smithing_template.upgrade"))
        )
        .withStyle(ChatFormatting.GRAY);

    public ArmorTrim(Holder<TrimMaterial> material, Holder<TrimPattern> pattern) {
        this(material, pattern, true);
    }

    public boolean hasPatternAndMaterial(Holder<TrimPattern> pattern, Holder<TrimMaterial> material) {
        return pattern.equals(this.pattern) && material.equals(this.material);
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag tooltipFlag) {
        if (this.showInTooltip) {
            tooltipAdder.accept(UPGRADE_TITLE);
            tooltipAdder.accept(CommonComponents.space().append(this.pattern.value().copyWithStyle(this.material)));
            tooltipAdder.accept(CommonComponents.space().append(this.material.value().description()));
        }
    }

    public ArmorTrim withTooltip(boolean showInTooltip) {
        return new ArmorTrim(this.material, this.pattern, showInTooltip);
    }
}
