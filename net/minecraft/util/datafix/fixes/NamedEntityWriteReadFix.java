package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import net.minecraft.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public abstract class NamedEntityWriteReadFix extends DataFix {
    private final String name;
    private final String entityName;
    private final TypeReference type;

    public NamedEntityWriteReadFix(Schema outputSchema, boolean changesType, String name, TypeReference type, String entityName) {
        super(outputSchema, changesType);
        this.name = name;
        this.type = type;
        this.entityName = entityName;
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(this.type);
        Type<?> choiceType = this.getInputSchema().getChoiceType(this.type, this.entityName);
        Type<?> type1 = this.getOutputSchema().getType(this.type);
        Type<?> choiceType1 = this.getOutputSchema().getChoiceType(this.type, this.entityName);
        OpticFinder<?> opticFinder = DSL.namedChoice(this.entityName, choiceType);
        Type<?> type2 = ExtraDataFixUtils.patchSubType(choiceType, type, type1);
        return this.fix(type, type1, opticFinder, choiceType1, type2);
    }

    private <S, T, A, B> TypeRewriteRule fix(Type<S> inputType, Type<T> outputType, OpticFinder<A> finder, Type<B> outputChoiceType, Type<?> newType) {
        return this.fixTypeEverywhere(this.name, inputType, outputType, dynamicOps -> object -> {
            Typed<S> typed = new Typed<>(inputType, dynamicOps, object);
            return (T)typed.update(finder, outputChoiceType, object1 -> {
                Typed<A> typed1 = new Typed<>((Type<A>)newType, dynamicOps, object1);
                return Util.<A, B>writeAndReadTypedOrThrow(typed1, outputChoiceType, this::fix).getValue();
            }).getValue();
        });
    }

    protected abstract <T> Dynamic<T> fix(Dynamic<T> tag);
}
