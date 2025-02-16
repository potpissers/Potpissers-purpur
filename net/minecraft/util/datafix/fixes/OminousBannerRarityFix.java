package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.TaggedChoice.TaggedChoiceType;
import com.mojang.datafixers.util.Pair;
import net.minecraft.util.datafix.ComponentDataFixUtils;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class OminousBannerRarityFix extends DataFix {
    public OminousBannerRarityFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.BLOCK_ENTITY);
        Type<?> type1 = this.getInputSchema().getType(References.ITEM_STACK);
        TaggedChoiceType<?> taggedChoiceType = this.getInputSchema().findChoiceType(References.BLOCK_ENTITY);
        OpticFinder<Pair<String, String>> opticFinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        OpticFinder<?> opticFinder1 = type.findField("components");
        OpticFinder<?> opticFinder2 = type1.findField("components");
        return TypeRewriteRule.seq(this.fixTypeEverywhereTyped("Ominous Banner block entity common rarity to uncommon rarity fix", type, typed -> {
            Object first = typed.get(taggedChoiceType.finder()).getFirst();
            return first.equals("minecraft:banner") ? this.fix(typed, opticFinder1) : typed;
        }), this.fixTypeEverywhereTyped("Ominous Banner item stack common rarity to uncommon rarity fix", type1, typed -> {
            String string = typed.getOptional(opticFinder).map(Pair::getSecond).orElse("");
            return string.equals("minecraft:white_banner") ? this.fix(typed, opticFinder2) : typed;
        }));
    }

    private Typed<?> fix(Typed<?> data, OpticFinder<?> componentField) {
        return data.updateTyped(
            componentField,
            typed -> typed.update(
                DSL.remainderFinder(),
                dynamic -> {
                    boolean isPresent = dynamic.get("minecraft:item_name")
                        .asString()
                        .result()
                        .flatMap(ComponentDataFixUtils::extractTranslationString)
                        .filter(string -> string.equals("block.minecraft.ominous_banner"))
                        .isPresent();
                    return isPresent
                        ? dynamic.set("minecraft:rarity", dynamic.createString("uncommon"))
                            .set("minecraft:item_name", ComponentDataFixUtils.createTranslatableComponent(dynamic.getOps(), "block.minecraft.ominous_banner"))
                        : dynamic;
                }
            )
        );
    }
}
