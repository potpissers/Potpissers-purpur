package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class OptionsMenuBlurrinessFix extends DataFix {
    public OptionsMenuBlurrinessFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "OptionsMenuBlurrinessFix",
            this.getInputSchema().getType(References.OPTIONS),
            typed -> typed.update(
                DSL.remainderFinder(),
                dynamic -> dynamic.update("menuBackgroundBlurriness", dynamic1 -> dynamic1.createInt(this.convertToIntRange(dynamic1.asString("0.5"))))
            )
        );
    }

    private int convertToIntRange(String value) {
        try {
            return Math.round(Float.parseFloat(value) * 10.0F);
        } catch (NumberFormatException var3) {
            return 5;
        }
    }
}
