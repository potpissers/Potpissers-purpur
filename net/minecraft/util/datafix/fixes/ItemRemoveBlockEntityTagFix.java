package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class ItemRemoveBlockEntityTagFix extends DataFix {
    private final Set<String> items;

    public ItemRemoveBlockEntityTagFix(Schema outputSchema, boolean changesType, Set<String> items) {
        super(outputSchema, changesType);
        this.items = items;
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<Pair<String, String>> opticFinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        OpticFinder<?> opticFinder1 = type.findField("tag");
        OpticFinder<?> opticFinder2 = opticFinder1.type().findField("BlockEntityTag");
        return this.fixTypeEverywhereTyped("ItemRemoveBlockEntityTagFix", type, typed -> {
            Optional<Pair<String, String>> optional = typed.getOptional(opticFinder);
            if (optional.isPresent() && this.items.contains(optional.get().getSecond())) {
                Optional<? extends Typed<?>> optionalTyped = typed.getOptionalTyped(opticFinder1);
                if (optionalTyped.isPresent()) {
                    Typed<?> typed1 = (Typed<?>)optionalTyped.get();
                    Optional<? extends Typed<?>> optionalTyped1 = typed1.getOptionalTyped(opticFinder2);
                    if (optionalTyped1.isPresent()) {
                        Optional<? extends Dynamic<?>> optional1 = typed1.write().result();
                        Dynamic<?> dynamic = (Dynamic<?>)(optional1.isPresent() ? optional1.get() : typed1.get(DSL.remainderFinder()));
                        Dynamic<?> dynamic1 = dynamic.remove("BlockEntityTag");
                        Optional<? extends Pair<? extends Typed<?>, ?>> optional2 = opticFinder1.type().readTyped(dynamic1).result();
                        if (optional2.isEmpty()) {
                            return typed;
                        }

                        return typed.set(opticFinder1, (Typed<?>)optional2.get().getFirst());
                    }
                }
            }

            return typed;
        });
    }
}
