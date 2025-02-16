package net.minecraft.world.item.alchemy;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ConsumableListener;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

public record PotionContents(Optional<Holder<Potion>> potion, Optional<Integer> customColor, List<MobEffectInstance> customEffects, Optional<String> customName)
    implements ConsumableListener {
    public static final PotionContents EMPTY = new PotionContents(Optional.empty(), Optional.empty(), List.of(), Optional.empty());
    private static final Component NO_EFFECT = Component.translatable("effect.none").withStyle(ChatFormatting.GRAY);
    public static final int BASE_POTION_COLOR = -13083194;
    private static final Codec<PotionContents> FULL_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                Potion.CODEC.optionalFieldOf("potion").forGetter(PotionContents::potion),
                Codec.INT.optionalFieldOf("custom_color").forGetter(PotionContents::customColor),
                MobEffectInstance.CODEC.listOf().optionalFieldOf("custom_effects", List.of()).forGetter(PotionContents::customEffects),
                Codec.STRING.optionalFieldOf("custom_name").forGetter(PotionContents::customName)
            )
            .apply(instance, PotionContents::new)
    );
    public static final Codec<PotionContents> CODEC = Codec.withAlternative(FULL_CODEC, Potion.CODEC, PotionContents::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, PotionContents> STREAM_CODEC = StreamCodec.composite(
        Potion.STREAM_CODEC.apply(ByteBufCodecs::optional),
        PotionContents::potion,
        ByteBufCodecs.INT.apply(ByteBufCodecs::optional),
        PotionContents::customColor,
        MobEffectInstance.STREAM_CODEC.apply(ByteBufCodecs.list()),
        PotionContents::customEffects,
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs::optional),
        PotionContents::customName,
        PotionContents::new
    );

    public PotionContents(Holder<Potion> potion) {
        this(Optional.of(potion), Optional.empty(), List.of(), Optional.empty());
    }

    public static ItemStack createItemStack(Item item, Holder<Potion> potion) {
        ItemStack itemStack = new ItemStack(item);
        itemStack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        return itemStack;
    }

    public boolean is(Holder<Potion> potion) {
        return this.potion.isPresent() && this.potion.get().is(potion) && this.customEffects.isEmpty();
    }

    public Iterable<MobEffectInstance> getAllEffects() {
        if (this.potion.isEmpty()) {
            return this.customEffects;
        } else {
            return (Iterable<MobEffectInstance>)(this.customEffects.isEmpty()
                ? this.potion.get().value().getEffects()
                : Iterables.concat(this.potion.get().value().getEffects(), this.customEffects));
        }
    }

    public void forEachEffect(Consumer<MobEffectInstance> action) {
        if (this.potion.isPresent()) {
            for (MobEffectInstance mobEffectInstance : this.potion.get().value().getEffects()) {
                action.accept(new MobEffectInstance(mobEffectInstance));
            }
        }

        for (MobEffectInstance mobEffectInstance : this.customEffects) {
            action.accept(new MobEffectInstance(mobEffectInstance));
        }
    }

    public PotionContents withPotion(Holder<Potion> potion) {
        return new PotionContents(Optional.of(potion), this.customColor, this.customEffects, this.customName);
    }

    public PotionContents withEffectAdded(MobEffectInstance effect) {
        return new PotionContents(this.potion, this.customColor, Util.copyAndAdd(this.customEffects, effect), this.customName);
    }

    public int getColor() {
        return this.getColorOr(-13083194);
    }

    public int getColorOr(int defaultValue) {
        return this.customColor.isPresent() ? this.customColor.get() : getColorOptional(this.getAllEffects()).orElse(defaultValue);
    }

    public Component getName(String name) {
        String string = this.customName.or(() -> this.potion.map(potion -> potion.value().name())).orElse("empty");
        return Component.translatable(name + string);
    }

    public static OptionalInt getColorOptional(Iterable<MobEffectInstance> effects) {
        int i = 0;
        int i1 = 0;
        int i2 = 0;
        int i3 = 0;

        for (MobEffectInstance mobEffectInstance : effects) {
            if (mobEffectInstance.isVisible()) {
                int color = mobEffectInstance.getEffect().value().getColor();
                int i4 = mobEffectInstance.getAmplifier() + 1;
                i += i4 * ARGB.red(color);
                i1 += i4 * ARGB.green(color);
                i2 += i4 * ARGB.blue(color);
                i3 += i4;
            }
        }

        return i3 == 0 ? OptionalInt.empty() : OptionalInt.of(ARGB.color(i / i3, i1 / i3, i2 / i3));
    }

    public boolean hasEffects() {
        return !this.customEffects.isEmpty() || this.potion.isPresent() && !this.potion.get().value().getEffects().isEmpty();
    }

    public List<MobEffectInstance> customEffects() {
        return Lists.transform(this.customEffects, MobEffectInstance::new);
    }

    public void addPotionTooltip(Consumer<Component> tooltipAdder, float durationFactor, float ticksPerSecond) {
        addPotionTooltip(this.getAllEffects(), tooltipAdder, durationFactor, ticksPerSecond);
    }

    public void applyToLivingEntity(LivingEntity entity) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            Player player1 = entity instanceof Player player ? player : null;
            this.forEachEffect(mobEffectInstance -> {
                if (mobEffectInstance.getEffect().value().isInstantenous()) {
                    mobEffectInstance.getEffect().value().applyInstantenousEffect(serverLevel, player1, player1, entity, mobEffectInstance.getAmplifier(), 1.0);
                } else {
                    entity.addEffect(mobEffectInstance);
                }
            });
        }
    }

    public static void addPotionTooltip(Iterable<MobEffectInstance> effects, Consumer<Component> tooltipAdder, float durationFactor, float ticksPerSecond) {
        List<Pair<Holder<Attribute>, AttributeModifier>> list = Lists.newArrayList();
        boolean flag = true;

        for (MobEffectInstance mobEffectInstance : effects) {
            flag = false;
            MutableComponent mutableComponent = Component.translatable(mobEffectInstance.getDescriptionId());
            Holder<MobEffect> effect = mobEffectInstance.getEffect();
            effect.value().createModifiers(mobEffectInstance.getAmplifier(), (attribute, modifier) -> list.add(new Pair<>(attribute, modifier)));
            if (mobEffectInstance.getAmplifier() > 0) {
                mutableComponent = Component.translatable(
                    "potion.withAmplifier", mutableComponent, Component.translatable("potion.potency." + mobEffectInstance.getAmplifier())
                );
            }

            if (!mobEffectInstance.endsWithin(20)) {
                mutableComponent = Component.translatable(
                    "potion.withDuration", mutableComponent, MobEffectUtil.formatDuration(mobEffectInstance, durationFactor, ticksPerSecond)
                );
            }

            tooltipAdder.accept(mutableComponent.withStyle(effect.value().getCategory().getTooltipFormatting()));
        }

        if (flag) {
            tooltipAdder.accept(NO_EFFECT);
        }

        if (!list.isEmpty()) {
            tooltipAdder.accept(CommonComponents.EMPTY);
            tooltipAdder.accept(Component.translatable("potion.whenDrank").withStyle(ChatFormatting.DARK_PURPLE));

            for (Pair<Holder<Attribute>, AttributeModifier> pair : list) {
                AttributeModifier attributeModifier = pair.getSecond();
                double amount = attributeModifier.amount();
                double d;
                if (attributeModifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                    && attributeModifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                    d = attributeModifier.amount();
                } else {
                    d = attributeModifier.amount() * 100.0;
                }

                if (amount > 0.0) {
                    tooltipAdder.accept(
                        Component.translatable(
                                "attribute.modifier.plus." + attributeModifier.operation().id(),
                                ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d),
                                Component.translatable(pair.getFirst().value().getDescriptionId())
                            )
                            .withStyle(ChatFormatting.BLUE)
                    );
                } else if (amount < 0.0) {
                    d *= -1.0;
                    tooltipAdder.accept(
                        Component.translatable(
                                "attribute.modifier.take." + attributeModifier.operation().id(),
                                ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d),
                                Component.translatable(pair.getFirst().value().getDescriptionId())
                            )
                            .withStyle(ChatFormatting.RED)
                    );
                }
            }
        }
    }

    @Override
    public void onConsume(Level level, LivingEntity entity, ItemStack stack, Consumable consumable) {
        this.applyToLivingEntity(entity);
    }
}
