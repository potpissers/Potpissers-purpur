package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class MapBannerBlockPosFormatFix extends DataFix {
    public MapBannerBlockPosFormatFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    private static <T> Dynamic<T> fixMapSavedData(Dynamic<T> data) {
        return data.update("banners", dynamic -> dynamic.createList(dynamic.asStream().map(dynamic1 -> dynamic1.update("Pos", ExtraDataFixUtils::fixBlockPos))));
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "MapBannerBlockPosFormatFix",
            this.getInputSchema().getType(References.SAVED_DATA_MAP_DATA),
            typed -> typed.update(DSL.remainderFinder(), dynamic -> dynamic.update("data", MapBannerBlockPosFormatFix::fixMapSavedData))
        );
    }
}
