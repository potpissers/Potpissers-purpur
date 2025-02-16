package net.minecraft.world.level.storage.loot;

import com.google.common.collect.Sets;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class LootContext {
    private final LootParams params;
    private final RandomSource random;
    private final HolderGetter.Provider lootDataResolver;
    private final Set<LootContext.VisitedEntry<?>> visitedElements = Sets.newLinkedHashSet();

    LootContext(LootParams params, RandomSource random, HolderGetter.Provider lootDataResolver) {
        this.params = params;
        this.random = random;
        this.lootDataResolver = lootDataResolver;
    }

    public boolean hasParameter(ContextKey<?> parameter) {
        return this.params.contextMap().has(parameter);
    }

    public <T> T getParameter(ContextKey<T> parameter) {
        return this.params.contextMap().getOrThrow(parameter);
    }

    @Nullable
    public <T> T getOptionalParameter(ContextKey<T> parameter) {
        return this.params.contextMap().getOptional(parameter);
    }

    public void addDynamicDrops(ResourceLocation name, Consumer<ItemStack> consumer) {
        this.params.addDynamicDrops(name, consumer);
    }

    public boolean hasVisitedElement(LootContext.VisitedEntry<?> element) {
        return this.visitedElements.contains(element);
    }

    public boolean pushVisitedElement(LootContext.VisitedEntry<?> element) {
        return this.visitedElements.add(element);
    }

    public void popVisitedElement(LootContext.VisitedEntry<?> element) {
        this.visitedElements.remove(element);
    }

    public HolderGetter.Provider getResolver() {
        return this.lootDataResolver;
    }

    public RandomSource getRandom() {
        return this.random;
    }

    public float getLuck() {
        return this.params.getLuck();
    }

    public ServerLevel getLevel() {
        return this.params.getLevel();
    }

    public static LootContext.VisitedEntry<LootTable> createVisitedEntry(LootTable lootTable) {
        return new LootContext.VisitedEntry<>(LootDataType.TABLE, lootTable);
    }

    public static LootContext.VisitedEntry<LootItemCondition> createVisitedEntry(LootItemCondition predicate) {
        return new LootContext.VisitedEntry<>(LootDataType.PREDICATE, predicate);
    }

    public static LootContext.VisitedEntry<LootItemFunction> createVisitedEntry(LootItemFunction modifier) {
        return new LootContext.VisitedEntry<>(LootDataType.MODIFIER, modifier);
    }

    public static class Builder {
        private final LootParams params;
        @Nullable
        private RandomSource random;

        public Builder(LootParams params) {
            this.params = params;
        }

        public LootContext.Builder withOptionalRandomSeed(long seed) {
            if (seed != 0L) {
                this.random = RandomSource.create(seed);
            }

            return this;
        }

        public LootContext.Builder withOptionalRandomSource(RandomSource random) {
            this.random = random;
            return this;
        }

        public ServerLevel getLevel() {
            return this.params.getLevel();
        }

        public LootContext create(Optional<ResourceLocation> sequence) {
            ServerLevel level = this.getLevel();
            MinecraftServer server = level.getServer();
            RandomSource randomSource = Optional.ofNullable(this.random).or(() -> sequence.map(level::getRandomSequence)).orElseGet(level::getRandom);
            return new LootContext(this.params, randomSource, server.reloadableRegistries().lookup());
        }
    }

    public static enum EntityTarget implements StringRepresentable {
        THIS("this", LootContextParams.THIS_ENTITY),
        ATTACKER("attacker", LootContextParams.ATTACKING_ENTITY),
        DIRECT_ATTACKER("direct_attacker", LootContextParams.DIRECT_ATTACKING_ENTITY),
        ATTACKING_PLAYER("attacking_player", LootContextParams.LAST_DAMAGE_PLAYER);

        public static final StringRepresentable.EnumCodec<LootContext.EntityTarget> CODEC = StringRepresentable.fromEnum(LootContext.EntityTarget::values);
        private final String name;
        private final ContextKey<? extends Entity> param;

        private EntityTarget(final String name, final ContextKey<? extends Entity> param) {
            this.name = name;
            this.param = param;
        }

        public ContextKey<? extends Entity> getParam() {
            return this.param;
        }

        public static LootContext.EntityTarget getByName(String name) {
            LootContext.EntityTarget entityTarget = CODEC.byName(name);
            if (entityTarget != null) {
                return entityTarget;
            } else {
                throw new IllegalArgumentException("Invalid entity target " + name);
            }
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public record VisitedEntry<T>(LootDataType<T> type, T value) {
    }
}
