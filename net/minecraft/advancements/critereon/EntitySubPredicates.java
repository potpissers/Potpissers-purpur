package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.CatVariant;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.FrogVariant;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Salmon;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.WolfVariant;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.horse.Variant;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.phys.Vec3;

public class EntitySubPredicates {
    public static final MapCodec<LightningBoltPredicate> LIGHTNING = register("lightning", LightningBoltPredicate.CODEC);
    public static final MapCodec<FishingHookPredicate> FISHING_HOOK = register("fishing_hook", FishingHookPredicate.CODEC);
    public static final MapCodec<PlayerPredicate> PLAYER = register("player", PlayerPredicate.CODEC);
    public static final MapCodec<SlimePredicate> SLIME = register("slime", SlimePredicate.CODEC);
    public static final MapCodec<RaiderPredicate> RAIDER = register("raider", RaiderPredicate.CODEC);
    public static final MapCodec<SheepPredicate> SHEEP = register("sheep", SheepPredicate.CODEC);
    public static final EntitySubPredicates.EntityVariantPredicateType<Axolotl.Variant> AXOLOTL = register(
        "axolotl",
        EntitySubPredicates.EntityVariantPredicateType.create(
            Axolotl.Variant.CODEC, entity -> entity instanceof Axolotl axolotl ? Optional.of(axolotl.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityVariantPredicateType<Fox.Variant> FOX = register(
        "fox",
        EntitySubPredicates.EntityVariantPredicateType.create(
            Fox.Variant.CODEC, entity -> entity instanceof Fox fox ? Optional.of(fox.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityVariantPredicateType<MushroomCow.Variant> MOOSHROOM = register(
        "mooshroom",
        EntitySubPredicates.EntityVariantPredicateType.create(
            MushroomCow.Variant.CODEC, entity -> entity instanceof MushroomCow mushroomCow ? Optional.of(mushroomCow.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityVariantPredicateType<Rabbit.Variant> RABBIT = register(
        "rabbit",
        EntitySubPredicates.EntityVariantPredicateType.create(
            Rabbit.Variant.CODEC, entity -> entity instanceof Rabbit rabbit ? Optional.of(rabbit.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityVariantPredicateType<Variant> HORSE = register(
        "horse",
        EntitySubPredicates.EntityVariantPredicateType.create(
            Variant.CODEC, entity -> entity instanceof Horse horse ? Optional.of(horse.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityVariantPredicateType<Llama.Variant> LLAMA = register(
        "llama",
        EntitySubPredicates.EntityVariantPredicateType.create(
            Llama.Variant.CODEC, entity -> entity instanceof Llama llama ? Optional.of(llama.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityVariantPredicateType<VillagerType> VILLAGER = register(
        "villager",
        EntitySubPredicates.EntityVariantPredicateType.create(
            BuiltInRegistries.VILLAGER_TYPE.byNameCodec(),
            entity -> entity instanceof VillagerDataHolder villagerDataHolder ? Optional.of(villagerDataHolder.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityVariantPredicateType<Parrot.Variant> PARROT = register(
        "parrot",
        EntitySubPredicates.EntityVariantPredicateType.create(
            Parrot.Variant.CODEC, entity -> entity instanceof Parrot parrot ? Optional.of(parrot.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityVariantPredicateType<Salmon.Variant> SALMON = register(
        "salmon",
        EntitySubPredicates.EntityVariantPredicateType.create(
            Salmon.Variant.CODEC, entity -> entity instanceof Salmon salmon ? Optional.of(salmon.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityVariantPredicateType<TropicalFish.Pattern> TROPICAL_FISH = register(
        "tropical_fish",
        EntitySubPredicates.EntityVariantPredicateType.create(
            TropicalFish.Pattern.CODEC, entity -> entity instanceof TropicalFish tropicalFish ? Optional.of(tropicalFish.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityHolderVariantPredicateType<PaintingVariant> PAINTING = register(
        "painting",
        EntitySubPredicates.EntityHolderVariantPredicateType.create(
            Registries.PAINTING_VARIANT, entity -> entity instanceof Painting painting ? Optional.of(painting.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityHolderVariantPredicateType<CatVariant> CAT = register(
        "cat",
        EntitySubPredicates.EntityHolderVariantPredicateType.create(
            Registries.CAT_VARIANT, entity -> entity instanceof Cat cat ? Optional.of(cat.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityHolderVariantPredicateType<FrogVariant> FROG = register(
        "frog",
        EntitySubPredicates.EntityHolderVariantPredicateType.create(
            Registries.FROG_VARIANT, entity -> entity instanceof Frog frog ? Optional.of(frog.getVariant()) : Optional.empty()
        )
    );
    public static final EntitySubPredicates.EntityHolderVariantPredicateType<WolfVariant> WOLF = register(
        "wolf",
        EntitySubPredicates.EntityHolderVariantPredicateType.create(
            Registries.WOLF_VARIANT, entity -> entity instanceof Wolf wolf ? Optional.of(wolf.getVariant()) : Optional.empty()
        )
    );

    private static <T extends EntitySubPredicate> MapCodec<T> register(String name, MapCodec<T> codec) {
        return Registry.register(BuiltInRegistries.ENTITY_SUB_PREDICATE_TYPE, name, codec);
    }

    private static <V> EntitySubPredicates.EntityVariantPredicateType<V> register(String name, EntitySubPredicates.EntityVariantPredicateType<V> predicateType) {
        Registry.register(BuiltInRegistries.ENTITY_SUB_PREDICATE_TYPE, name, predicateType.codec);
        return predicateType;
    }

    private static <V> EntitySubPredicates.EntityHolderVariantPredicateType<V> register(
        String name, EntitySubPredicates.EntityHolderVariantPredicateType<V> predicateType
    ) {
        Registry.register(BuiltInRegistries.ENTITY_SUB_PREDICATE_TYPE, name, predicateType.codec);
        return predicateType;
    }

    public static MapCodec<? extends EntitySubPredicate> bootstrap(Registry<MapCodec<? extends EntitySubPredicate>> registry) {
        return LIGHTNING;
    }

    public static EntitySubPredicate catVariant(Holder<CatVariant> catVariant) {
        return CAT.createPredicate(HolderSet.direct(catVariant));
    }

    public static EntitySubPredicate frogVariant(Holder<FrogVariant> frogVariant) {
        return FROG.createPredicate(HolderSet.direct(frogVariant));
    }

    public static EntitySubPredicate wolfVariant(HolderSet<WolfVariant> wolfVariant) {
        return WOLF.createPredicate(wolfVariant);
    }

    public static class EntityHolderVariantPredicateType<V> {
        final MapCodec<EntitySubPredicates.EntityHolderVariantPredicateType<V>.Instance> codec;
        final Function<Entity, Optional<Holder<V>>> getter;

        public static <V> EntitySubPredicates.EntityHolderVariantPredicateType<V> create(
            ResourceKey<? extends Registry<V>> registryKey, Function<Entity, Optional<Holder<V>>> getter
        ) {
            return new EntitySubPredicates.EntityHolderVariantPredicateType<>(registryKey, getter);
        }

        public EntityHolderVariantPredicateType(ResourceKey<? extends Registry<V>> registryKey, Function<Entity, Optional<Holder<V>>> getter) {
            this.getter = getter;
            this.codec = RecordCodecBuilder.mapCodec(
                instance -> instance.group(RegistryCodecs.homogeneousList(registryKey).fieldOf("variant").forGetter(instance1 -> instance1.variants))
                    .apply(instance, holderSet -> new EntitySubPredicates.EntityHolderVariantPredicateType.Instance(holderSet))
            );
        }

        public EntitySubPredicate createPredicate(HolderSet<V> variants) {
            return new EntitySubPredicates.EntityHolderVariantPredicateType.Instance(variants);
        }

        class Instance implements EntitySubPredicate {
            final HolderSet<V> variants;

            Instance(final HolderSet<V> variants) {
                this.variants = variants;
            }

            @Override
            public MapCodec<EntitySubPredicates.EntityHolderVariantPredicateType<V>.Instance> codec() {
                return EntityHolderVariantPredicateType.this.codec;
            }

            @Override
            public boolean matches(Entity entity, ServerLevel level, @Nullable Vec3 position) {
                return EntityHolderVariantPredicateType.this.getter.apply(entity).filter(this.variants::contains).isPresent();
            }
        }
    }

    public static class EntityVariantPredicateType<V> {
        final MapCodec<EntitySubPredicates.EntityVariantPredicateType<V>.Instance> codec;
        final Function<Entity, Optional<V>> getter;

        public static <V> EntitySubPredicates.EntityVariantPredicateType<V> create(Registry<V> variantRegistry, Function<Entity, Optional<V>> getter) {
            return new EntitySubPredicates.EntityVariantPredicateType<>(variantRegistry.byNameCodec(), getter);
        }

        public static <V> EntitySubPredicates.EntityVariantPredicateType<V> create(Codec<V> codec, Function<Entity, Optional<V>> getter) {
            return new EntitySubPredicates.EntityVariantPredicateType<>(codec, getter);
        }

        public EntityVariantPredicateType(Codec<V> codec, Function<Entity, Optional<V>> getter) {
            this.getter = getter;
            this.codec = RecordCodecBuilder.mapCodec(
                instance -> instance.group(codec.fieldOf("variant").forGetter(instance1 -> instance1.variant))
                    .apply(instance, object -> new EntitySubPredicates.EntityVariantPredicateType.Instance(object))
            );
        }

        public EntitySubPredicate createPredicate(V variant) {
            return new EntitySubPredicates.EntityVariantPredicateType.Instance(variant);
        }

        class Instance implements EntitySubPredicate {
            final V variant;

            Instance(final V variant) {
                this.variant = variant;
            }

            @Override
            public MapCodec<EntitySubPredicates.EntityVariantPredicateType<V>.Instance> codec() {
                return EntityVariantPredicateType.this.codec;
            }

            @Override
            public boolean matches(Entity entity, ServerLevel level, @Nullable Vec3 position) {
                return EntityVariantPredicateType.this.getter.apply(entity).filter(this.variant::equals).isPresent();
            }
        }
    }
}
