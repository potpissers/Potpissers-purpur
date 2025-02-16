package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class CopyNameFunction extends LootItemConditionalFunction {
    public static final MapCodec<CopyNameFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(CopyNameFunction.NameSource.CODEC.fieldOf("source").forGetter(copyNameFunction -> copyNameFunction.source))
            .apply(instance, CopyNameFunction::new)
    );
    private final CopyNameFunction.NameSource source;

    private CopyNameFunction(List<LootItemCondition> predicates, CopyNameFunction.NameSource source) {
        super(predicates);
        this.source = source;
    }

    @Override
    public LootItemFunctionType<CopyNameFunction> getType() {
        return LootItemFunctions.COPY_NAME;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(this.source.param);
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (context.getOptionalParameter(this.source.param) instanceof Nameable nameable) {
            stack.set(DataComponents.CUSTOM_NAME, nameable.getCustomName());
        }

        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> copyName(CopyNameFunction.NameSource source) {
        return simpleBuilder(conditions -> new CopyNameFunction(conditions, source));
    }

    public static enum NameSource implements StringRepresentable {
        THIS("this", LootContextParams.THIS_ENTITY),
        ATTACKING_ENTITY("attacking_entity", LootContextParams.ATTACKING_ENTITY),
        LAST_DAMAGE_PLAYER("last_damage_player", LootContextParams.LAST_DAMAGE_PLAYER),
        BLOCK_ENTITY("block_entity", LootContextParams.BLOCK_ENTITY);

        public static final Codec<CopyNameFunction.NameSource> CODEC = StringRepresentable.fromEnum(CopyNameFunction.NameSource::values);
        private final String name;
        final ContextKey<?> param;

        private NameSource(final String name, final ContextKey<?> param) {
            this.name = name;
            this.param = param;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
