package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;

public class ContainerBlockEntityLockPredicateFix extends DataFix {
    public ContainerBlockEntityLockPredicateFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "ContainerBlockEntityLockPredicateFix",
            this.getInputSchema().findChoiceType(References.BLOCK_ENTITY),
            ContainerBlockEntityLockPredicateFix::fixBlockEntity
        );
    }

    private static Typed<?> fixBlockEntity(Typed<?> data) {
        return data.update(DSL.remainderFinder(), tag -> tag.renameAndFixField("Lock", "lock", LockComponentPredicateFix::fixLock));
    }
}
