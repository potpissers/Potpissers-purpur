package net.minecraft.world.level.storage.loot.entries;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.Products.P4;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public abstract class LootPoolSingletonContainer extends LootPoolEntryContainer {
    public static final int DEFAULT_WEIGHT = 1;
    public static final int DEFAULT_QUALITY = 0;
    protected final int weight;
    protected final int quality;
    protected final List<LootItemFunction> functions;
    final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;
    private final LootPoolEntry entry = new LootPoolSingletonContainer.EntryBase() {
        @Override
        public void createItemStack(Consumer<ItemStack> stackConsumer, LootContext lootContext) {
            LootPoolSingletonContainer.this.createItemStack(
                LootItemFunction.decorate(LootPoolSingletonContainer.this.compositeFunction, stackConsumer, lootContext), lootContext
            );
        }
    };
    // Paper start - Configurable LootPool luck formula
    private Float lastLuck;
    private int lastWeight;
    // Paper end - Configurable LootPool luck formula

    protected LootPoolSingletonContainer(int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(conditions);
        this.weight = weight;
        this.quality = quality;
        this.functions = functions;
        this.compositeFunction = LootItemFunctions.compose(functions);
    }

    protected static <T extends LootPoolSingletonContainer> P4<Mu<T>, Integer, Integer, List<LootItemCondition>, List<LootItemFunction>> singletonFields(
        Instance<T> instance
    ) {
        return instance.group(
                Codec.INT.optionalFieldOf("weight", Integer.valueOf(1)).forGetter(container -> container.weight),
                Codec.INT.optionalFieldOf("quality", Integer.valueOf(0)).forGetter(lootPoolSingletonContainer -> lootPoolSingletonContainer.quality)
            )
            .and(commonFields(instance).t1())
            .and(LootItemFunctions.ROOT_CODEC.listOf().optionalFieldOf("functions", List.of()).forGetter(container -> container.functions));
    }

    @Override
    public void validate(ValidationContext validationContext) {
        super.validate(validationContext);

        for (int i = 0; i < this.functions.size(); i++) {
            this.functions.get(i).validate(validationContext.forChild(".functions[" + i + "]"));
        }
    }

    protected abstract void createItemStack(Consumer<ItemStack> stackConsumer, LootContext lootContext);

    @Override
    public boolean expand(LootContext lootContext, Consumer<LootPoolEntry> entryConsumer) {
        if (this.canRun(lootContext)) {
            entryConsumer.accept(this.entry);
            return true;
        } else {
            return false;
        }
    }

    public static LootPoolSingletonContainer.Builder<?> simpleBuilder(LootPoolSingletonContainer.EntryConstructor entryBuilder) {
        return new LootPoolSingletonContainer.DummyBuilder(entryBuilder);
    }

    public abstract static class Builder<T extends LootPoolSingletonContainer.Builder<T>>
        extends LootPoolEntryContainer.Builder<T>
        implements FunctionUserBuilder<T> {
        protected int weight = 1;
        protected int quality = 0;
        private final ImmutableList.Builder<LootItemFunction> functions = ImmutableList.builder();

        @Override
        public T apply(LootItemFunction.Builder functionBuilder) {
            this.functions.add(functionBuilder.build());
            return this.getThis();
        }

        protected List<LootItemFunction> getFunctions() {
            return this.functions.build();
        }

        public T setWeight(int weight) {
            this.weight = weight;
            return this.getThis();
        }

        public T setQuality(int quality) {
            this.quality = quality;
            return this.getThis();
        }
    }

    static class DummyBuilder extends LootPoolSingletonContainer.Builder<LootPoolSingletonContainer.DummyBuilder> {
        private final LootPoolSingletonContainer.EntryConstructor constructor;

        public DummyBuilder(LootPoolSingletonContainer.EntryConstructor constructor) {
            this.constructor = constructor;
        }

        @Override
        protected LootPoolSingletonContainer.DummyBuilder getThis() {
            return this;
        }

        @Override
        public LootPoolEntryContainer build() {
            return this.constructor.build(this.weight, this.quality, this.getConditions(), this.getFunctions());
        }
    }

    protected abstract class EntryBase implements LootPoolEntry {
        @Override
        public int getWeight(float luck) {
            // Paper start - Configurable LootPool luck formula
            // SEE: https://luckformula.emc.gs for details and data
            if (LootPoolSingletonContainer.this.lastLuck != null && LootPoolSingletonContainer.this.lastLuck == luck) {
                return lastWeight;
            }
            // This is vanilla
            float qualityModifer = (float) LootPoolSingletonContainer.this.quality * luck;
            double baseWeight = (LootPoolSingletonContainer.this.weight + qualityModifer);
            if (io.papermc.paper.configuration.GlobalConfiguration.get().misc.useAlternativeLuckFormula) {
                // Random boost to avoid losing precision in the final int cast on return
                final int weightBoost = 100;
                baseWeight *= weightBoost;
                // If we have vanilla 1, bump that down to 0 so nothing is is impacted
                // vanilla 3 = 300, 200 basis = impact 2%
                // =($B2*(($B2-100)/100/100))
                double impacted = baseWeight * ((baseWeight - weightBoost) / weightBoost / 100);
                // =($B$7/100)
                float luckModifier = Math.min(100, luck * 10) / 100;
                // =B2 - (C2 *($B$7/100))
                baseWeight = Math.ceil(baseWeight - (impacted * luckModifier));
            }
            LootPoolSingletonContainer.this.lastLuck = luck;
            LootPoolSingletonContainer.this.lastWeight = (int) Math.max(Math.floor(baseWeight), 0);
            return lastWeight;
            // Paper end - Configurable LootPool luck formula
        }
    }

    @FunctionalInterface
    protected interface EntryConstructor {
        LootPoolSingletonContainer build(int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions);
    }
}
