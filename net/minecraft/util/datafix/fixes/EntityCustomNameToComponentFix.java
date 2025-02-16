package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.util.datafix.ComponentDataFixUtils;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class EntityCustomNameToComponentFix extends DataFix {
    public EntityCustomNameToComponentFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    public TypeRewriteRule makeRule() {
        OpticFinder<String> opticFinder = DSL.fieldFinder("id", NamespacedSchema.namespacedString());
        return this.fixTypeEverywhereTyped(
            "EntityCustomNameToComponentFix", this.getInputSchema().getType(References.ENTITY), typed -> typed.update(DSL.remainderFinder(), dynamic -> {
                Optional<String> optional = typed.getOptional(opticFinder);
                return optional.isPresent() && Objects.equals(optional.get(), "minecraft:commandblock_minecart") ? dynamic : fixTagCustomName(dynamic);
            })
        );
    }

    public static Dynamic<?> fixTagCustomName(Dynamic<?> tag) {
        String string = tag.get("CustomName").asString("");
        return string.isEmpty() ? tag.remove("CustomName") : tag.set("CustomName", ComponentDataFixUtils.createPlainTextComponent(tag.getOps(), string));
    }
}
