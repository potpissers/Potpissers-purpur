package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.util.datafix.ComponentDataFixUtils;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class ItemStackCustomNameToOverrideComponentFix extends DataFix {
    private static final Set<String> MAP_NAMES = Set.of(
        "filled_map.buried_treasure",
        "filled_map.explorer_jungle",
        "filled_map.explorer_swamp",
        "filled_map.mansion",
        "filled_map.monument",
        "filled_map.trial_chambers",
        "filled_map.village_desert",
        "filled_map.village_plains",
        "filled_map.village_savanna",
        "filled_map.village_snowy",
        "filled_map.village_taiga"
    );

    public ItemStackCustomNameToOverrideComponentFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    public final TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<Pair<String, String>> opticFinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        OpticFinder<?> opticFinder1 = type.findField("components");
        return this.fixTypeEverywhereTyped(
            "ItemStack custom_name to item_name component fix",
            type,
            typed -> {
                Optional<Pair<String, String>> optional = typed.getOptional(opticFinder);
                Optional<String> optional1 = optional.map(Pair::getSecond);
                if (optional1.filter(string -> string.equals("minecraft:white_banner")).isPresent()) {
                    return typed.updateTyped(opticFinder1, typed1 -> typed1.update(DSL.remainderFinder(), ItemStackCustomNameToOverrideComponentFix::fixBanner));
                } else {
                    return optional1.filter(string -> string.equals("minecraft:filled_map")).isPresent()
                        ? typed.updateTyped(opticFinder1, typed1 -> typed1.update(DSL.remainderFinder(), ItemStackCustomNameToOverrideComponentFix::fixMap))
                        : typed;
                }
            }
        );
    }

    private static <T> Dynamic<T> fixMap(Dynamic<T> data) {
        return fixCustomName(data, MAP_NAMES::contains);
    }

    private static <T> Dynamic<T> fixBanner(Dynamic<T> data) {
        return fixCustomName(data, string -> string.equals("block.minecraft.ominous_banner"));
    }

    private static <T> Dynamic<T> fixCustomName(Dynamic<T> data, Predicate<String> shouldFix) {
        OptionalDynamic<T> optionalDynamic = data.get("minecraft:custom_name");
        Optional<String> optional = optionalDynamic.asString().result().flatMap(ComponentDataFixUtils::extractTranslationString).filter(shouldFix);
        return optional.isPresent() ? data.renameField("minecraft:custom_name", "minecraft:item_name") : data;
    }
}
