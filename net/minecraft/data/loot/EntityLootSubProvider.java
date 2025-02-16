package net.minecraft.data.loot;

import com.google.common.collect.Maps;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import net.minecraft.advancements.critereon.DamageSourcePredicate;
import net.minecraft.advancements.critereon.EnchantmentPredicate;
import net.minecraft.advancements.critereon.EntityEquipmentPredicate;
import net.minecraft.advancements.critereon.EntityFlagsPredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.EntitySubPredicates;
import net.minecraft.advancements.critereon.ItemEnchantmentsPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.ItemSubPredicates;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SheepPredicate;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.FrogVariant;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.AlternativesEntry;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.predicates.DamageSourceCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;

public abstract class EntityLootSubProvider implements LootTableSubProvider {
    protected final HolderLookup.Provider registries;
    private final FeatureFlagSet allowed;
    private final FeatureFlagSet required;
    private final Map<EntityType<?>, Map<ResourceKey<LootTable>, LootTable.Builder>> map = Maps.newHashMap();

    protected final AnyOfCondition.Builder shouldSmeltLoot() {
        HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return AnyOfCondition.anyOf(
            LootItemEntityPropertyCondition.hasProperties(
                LootContext.EntityTarget.THIS, EntityPredicate.Builder.entity().flags(EntityFlagsPredicate.Builder.flags().setOnFire(true))
            ),
            LootItemEntityPropertyCondition.hasProperties(
                LootContext.EntityTarget.DIRECT_ATTACKER,
                EntityPredicate.Builder.entity()
                    .equipment(
                        EntityEquipmentPredicate.Builder.equipment()
                            .mainhand(
                                ItemPredicate.Builder.item()
                                    .withSubPredicate(
                                        ItemSubPredicates.ENCHANTMENTS,
                                        ItemEnchantmentsPredicate.enchantments(
                                            List.of(new EnchantmentPredicate(registryLookup.getOrThrow(EnchantmentTags.SMELTS_LOOT), MinMaxBounds.Ints.ANY))
                                        )
                                    )
                            )
                    )
            )
        );
    }

    protected EntityLootSubProvider(FeatureFlagSet required, HolderLookup.Provider registries) {
        this(required, required, registries);
    }

    protected EntityLootSubProvider(FeatureFlagSet allowed, FeatureFlagSet required, HolderLookup.Provider registries) {
        this.allowed = allowed;
        this.required = required;
        this.registries = registries;
    }

    public static LootPool.Builder createSheepDispatchPool(Map<DyeColor, ResourceKey<LootTable>> lootTables) {
        AlternativesEntry.Builder builder = AlternativesEntry.alternatives();

        for (Entry<DyeColor, ResourceKey<LootTable>> entry : lootTables.entrySet()) {
            builder = builder.otherwise(
                NestedLootTable.lootTableReference(entry.getValue())
                    .when(
                        LootItemEntityPropertyCondition.hasProperties(
                            LootContext.EntityTarget.THIS, EntityPredicate.Builder.entity().subPredicate(SheepPredicate.hasWool(entry.getKey()))
                        )
                    )
            );
        }

        return LootPool.lootPool().add(builder);
    }

    public abstract void generate();

    @Override
    public void generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> output) {
        this.generate();
        Set<ResourceKey<LootTable>> set = new HashSet<>();
        BuiltInRegistries.ENTITY_TYPE
            .listElements()
            .forEach(
                reference -> {
                    EntityType<?> entityType = reference.value();
                    if (entityType.isEnabled(this.allowed)) {
                        Optional<ResourceKey<LootTable>> defaultLootTable = entityType.getDefaultLootTable();
                        if (defaultLootTable.isPresent()) {
                            Map<ResourceKey<LootTable>, LootTable.Builder> map = this.map.remove(entityType);
                            if (entityType.isEnabled(this.required) && (map == null || !map.containsKey(defaultLootTable.get()))) {
                                throw new IllegalStateException(
                                    String.format(Locale.ROOT, "Missing loottable '%s' for '%s'", defaultLootTable.get(), reference.key().location())
                                );
                            }

                            if (map != null) {
                                map.forEach(
                                    (resourceKey, builder) -> {
                                        if (!set.add((ResourceKey<LootTable>)resourceKey)) {
                                            throw new IllegalStateException(
                                                String.format(Locale.ROOT, "Duplicate loottable '%s' for '%s'", resourceKey, reference.key().location())
                                            );
                                        } else {
                                            output.accept((ResourceKey<LootTable>)resourceKey, builder);
                                        }
                                    }
                                );
                            }
                        } else {
                            Map<ResourceKey<LootTable>, LootTable.Builder> mapx = this.map.remove(entityType);
                            if (mapx != null) {
                                throw new IllegalStateException(
                                    String.format(
                                        Locale.ROOT,
                                        "Weird loottables '%s' for '%s', not a LivingEntity so should not have loot",
                                        mapx.keySet().stream().map(resourceKey -> resourceKey.location().toString()).collect(Collectors.joining(",")),
                                        reference.key().location()
                                    )
                                );
                            }
                        }
                    }
                }
            );
        if (!this.map.isEmpty()) {
            throw new IllegalStateException("Created loot tables for entities not supported by datapack: " + this.map.keySet());
        }
    }

    protected LootItemCondition.Builder killedByFrog(HolderGetter<EntityType<?>> entityTypeRegistry) {
        return DamageSourceCondition.hasDamageSource(
            DamageSourcePredicate.Builder.damageType().source(EntityPredicate.Builder.entity().of(entityTypeRegistry, EntityType.FROG))
        );
    }

    protected LootItemCondition.Builder killedByFrogVariant(HolderGetter<EntityType<?>> entityTypeRegistry, ResourceKey<FrogVariant> frogVariant) {
        return DamageSourceCondition.hasDamageSource(
            DamageSourcePredicate.Builder.damageType()
                .source(
                    EntityPredicate.Builder.entity()
                        .of(entityTypeRegistry, EntityType.FROG)
                        .subPredicate(EntitySubPredicates.frogVariant(BuiltInRegistries.FROG_VARIANT.getOrThrow(frogVariant)))
                )
        );
    }

    protected void add(EntityType<?> entityType, LootTable.Builder builder) {
        this.add(
            entityType, entityType.getDefaultLootTable().orElseThrow(() -> new IllegalStateException("Entity " + entityType + " has no loot table")), builder
        );
    }

    protected void add(EntityType<?> entityType, ResourceKey<LootTable> defaultLootTable, LootTable.Builder builder) {
        this.map.computeIfAbsent(entityType, entityType1 -> new HashMap<>()).put(defaultLootTable, builder);
    }
}
