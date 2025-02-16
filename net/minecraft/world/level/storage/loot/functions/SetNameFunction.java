package net.minecraft.world.level.storage.loot.functions;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.slf4j.Logger;

public class SetNameFunction extends LootItemConditionalFunction {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<SetNameFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(
                instance.group(
                    ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(setNameFunction -> setNameFunction.name),
                    LootContext.EntityTarget.CODEC.optionalFieldOf("entity").forGetter(setNameFunction -> setNameFunction.resolutionContext),
                    SetNameFunction.Target.CODEC
                        .optionalFieldOf("target", SetNameFunction.Target.CUSTOM_NAME)
                        .forGetter(setNameFunction -> setNameFunction.target)
                )
            )
            .apply(instance, SetNameFunction::new)
    );
    private final Optional<Component> name;
    private final Optional<LootContext.EntityTarget> resolutionContext;
    private final SetNameFunction.Target target;

    private SetNameFunction(
        List<LootItemCondition> predicates, Optional<Component> name, Optional<LootContext.EntityTarget> resolutionContext, SetNameFunction.Target target
    ) {
        super(predicates);
        this.name = name;
        this.resolutionContext = resolutionContext;
        this.target = target;
    }

    @Override
    public LootItemFunctionType<SetNameFunction> getType() {
        return LootItemFunctions.SET_NAME;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return this.resolutionContext.<Set<ContextKey<?>>>map(entityTarget -> Set.of(entityTarget.getParam())).orElse(Set.of());
    }

    public static UnaryOperator<Component> createResolver(LootContext lootContext, @Nullable LootContext.EntityTarget resolutionContext) {
        if (resolutionContext != null) {
            Entity entity = lootContext.getOptionalParameter(resolutionContext.getParam());
            if (entity != null) {
                CommandSourceStack commandSourceStack = entity.createCommandSourceStackForNameResolution(lootContext.getLevel()).withPermission(2);
                return component -> {
                    try {
                        return ComponentUtils.updateForEntity(commandSourceStack, component, entity, 0);
                    } catch (CommandSyntaxException var4) {
                        LOGGER.warn("Failed to resolve text component", (Throwable)var4);
                        return component;
                    }
                };
            }
        }

        return component -> component;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        this.name.ifPresent(component -> stack.set(this.target.component(), createResolver(context, this.resolutionContext.orElse(null)).apply(component)));
        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> setName(Component name, SetNameFunction.Target target) {
        return simpleBuilder(list -> new SetNameFunction(list, Optional.of(name), Optional.empty(), target));
    }

    public static LootItemConditionalFunction.Builder<?> setName(Component name, SetNameFunction.Target target, LootContext.EntityTarget resolutionContext) {
        return simpleBuilder(list -> new SetNameFunction(list, Optional.of(name), Optional.of(resolutionContext), target));
    }

    public static enum Target implements StringRepresentable {
        CUSTOM_NAME("custom_name"),
        ITEM_NAME("item_name");

        public static final Codec<SetNameFunction.Target> CODEC = StringRepresentable.fromEnum(SetNameFunction.Target::values);
        private final String name;

        private Target(final String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public DataComponentType<Component> component() {
            return switch (this) {
                case CUSTOM_NAME -> DataComponents.CUSTOM_NAME;
                case ITEM_NAME -> DataComponents.ITEM_NAME;
            };
        }
    }
}
