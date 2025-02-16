package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.ComponentDataFixUtils;

public class ItemWrittenBookPagesStrictJsonFix extends DataFix {
    public ItemWrittenBookPagesStrictJsonFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    public Dynamic<?> fixTag(Dynamic<?> tag) {
        return tag.update(
            "pages",
            dynamic -> DataFixUtils.orElse(
                dynamic.asStreamOpt().map(stream -> stream.map(ComponentDataFixUtils::rewriteFromLenient)).map(tag::createList).result(), tag.emptyList()
            )
        );
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticFinder = type.findField("tag");
        return this.fixTypeEverywhereTyped(
            "ItemWrittenBookPagesStrictJsonFix", type, typed -> typed.updateTyped(opticFinder, typed1 -> typed1.update(DSL.remainderFinder(), this::fixTag))
        );
    }
}
