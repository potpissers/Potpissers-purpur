package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class OminousBannerRenameFix extends ItemStackTagFix {
    public OminousBannerRenameFix(Schema schema) {
        super(schema, "OminousBannerRenameFix", string -> string.equals("minecraft:white_banner"));
    }

    @Override
    protected <T> Dynamic<T> fixItemStackTag(Dynamic<T> itemStackTag) {
        Optional<? extends Dynamic<?>> optional = itemStackTag.get("display").result();
        if (optional.isPresent()) {
            Dynamic<?> dynamic = (Dynamic<?>)optional.get();
            Optional<String> optional1 = dynamic.get("Name").asString().result();
            if (optional1.isPresent()) {
                String string = optional1.get();
                string = string.replace("\"translate\":\"block.minecraft.illager_banner\"", "\"translate\":\"block.minecraft.ominous_banner\"");
                dynamic = dynamic.set("Name", dynamic.createString(string));
            }

            return itemStackTag.set("display", dynamic);
        } else {
            return itemStackTag;
        }
    }
}
