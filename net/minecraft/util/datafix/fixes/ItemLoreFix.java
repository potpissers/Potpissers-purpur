package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.stream.Stream;
import net.minecraft.util.datafix.ComponentDataFixUtils;

public class ItemLoreFix extends DataFix {
    public ItemLoreFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticFinder = type.findField("tag");
        return this.fixTypeEverywhereTyped(
            "Item Lore componentize",
            type,
            typed -> typed.updateTyped(
                opticFinder,
                typed1 -> typed1.update(
                    DSL.remainderFinder(),
                    dynamic -> dynamic.update(
                        "display",
                        dynamic1 -> dynamic1.update(
                            "Lore",
                            dynamic2 -> DataFixUtils.orElse(dynamic2.asStreamOpt().map(ItemLoreFix::fixLoreList).map(dynamic2::createList).result(), dynamic2)
                        )
                    )
                )
            )
        );
    }

    private static <T> Stream<Dynamic<T>> fixLoreList(Stream<Dynamic<T>> loreListTags) {
        return loreListTags.map(ComponentDataFixUtils::wrapLiteralStringAsComponent);
    }
}
