package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.TaggedChoice.TaggedChoiceType;
import com.mojang.datafixers.util.Pair;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public abstract class SimplestEntityRenameFix extends DataFix {
    private final String name;

    public SimplestEntityRenameFix(String name, Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
        this.name = name;
    }

    @Override
    public TypeRewriteRule makeRule() {
        TaggedChoiceType<String> taggedChoiceType = (TaggedChoiceType<String>)this.getInputSchema().findChoiceType(References.ENTITY);
        TaggedChoiceType<String> taggedChoiceType1 = (TaggedChoiceType<String>)this.getOutputSchema().findChoiceType(References.ENTITY);
        Type<Pair<String, String>> type = DSL.named(References.ENTITY_NAME.typeName(), NamespacedSchema.namespacedString());
        if (!Objects.equals(this.getOutputSchema().getType(References.ENTITY_NAME), type)) {
            throw new IllegalStateException("Entity name type is not what was expected.");
        } else {
            return TypeRewriteRule.seq(this.fixTypeEverywhere(this.name, taggedChoiceType, taggedChoiceType1, dynamicOps -> pair -> pair.mapFirst(string -> {
                String string1 = this.rename(string);
                Type<?> type1 = taggedChoiceType.types().get(string);
                Type<?> type2 = taggedChoiceType1.types().get(string1);
                if (!type2.equals(type1, true, true)) {
                    throw new IllegalStateException(String.format(Locale.ROOT, "Dynamic type check failed: %s not equal to %s", type2, type1));
                } else {
                    return string1;
                }
            })), this.fixTypeEverywhere(this.name + " for entity name", type, dynamicOps -> pair -> pair.mapSecond(this::rename)));
        }
    }

    protected abstract String rename(String name);
}
