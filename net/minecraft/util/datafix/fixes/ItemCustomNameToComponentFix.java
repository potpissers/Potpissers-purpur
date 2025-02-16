package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import net.minecraft.util.datafix.ComponentDataFixUtils;

public class ItemCustomNameToComponentFix extends DataFix {
    public ItemCustomNameToComponentFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    private Dynamic<?> fixTag(Dynamic<?> tag) {
        Optional<? extends Dynamic<?>> optional = tag.get("display").result();
        if (optional.isPresent()) {
            Dynamic<?> dynamic = (Dynamic<?>)optional.get();
            Optional<String> optional1 = dynamic.get("Name").asString().result();
            if (optional1.isPresent()) {
                dynamic = dynamic.set("Name", ComponentDataFixUtils.createPlainTextComponent(dynamic.getOps(), optional1.get()));
            }

            return tag.set("display", dynamic);
        } else {
            return tag;
        }
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticFinder = type.findField("tag");
        return this.fixTypeEverywhereTyped(
            "ItemCustomNameToComponentFix", type, typed -> typed.updateTyped(opticFinder, typed1 -> typed1.update(DSL.remainderFinder(), this::fixTag))
        );
    }
}
