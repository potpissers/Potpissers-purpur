package net.minecraft.world.item;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public class InstrumentItem extends Item {
    private final TagKey<Instrument> instruments;

    public InstrumentItem(TagKey<Instrument> instruments, Item.Properties properties) {
        super(properties);
        this.instruments = instruments;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        HolderLookup.Provider provider = context.registries();
        if (provider != null) {
            Optional<Holder<Instrument>> instrument = this.getInstrument(stack, provider);
            if (instrument.isPresent()) {
                MutableComponent mutableComponent = instrument.get().value().description().copy();
                ComponentUtils.mergeStyles(mutableComponent, Style.EMPTY.withColor(ChatFormatting.GRAY));
                tooltipComponents.add(mutableComponent);
            }
        }
    }

    public static ItemStack create(Item item, Holder<Instrument> instrument) {
        ItemStack itemStack = new ItemStack(item);
        itemStack.set(DataComponents.INSTRUMENT, instrument);
        return itemStack;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        Optional<? extends Holder<Instrument>> instrument = this.getInstrument(itemInHand, player.registryAccess());
        if (instrument.isPresent()) {
            Instrument instrument1 = instrument.get().value();
            player.startUsingItem(hand);
            play(level, player, instrument1);
            player.getCooldowns().addCooldown(itemInHand, Mth.floor(instrument1.useDuration() * 20.0F));
            player.awardStat(Stats.ITEM_USED.get(this));
            return InteractionResult.CONSUME;
        } else {
            return InteractionResult.FAIL;
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        Optional<Holder<Instrument>> instrument = this.getInstrument(stack, entity.registryAccess());
        return instrument.<Integer>map(holder -> Mth.floor(holder.value().useDuration() * 20.0F)).orElse(0);
    }

    private Optional<Holder<Instrument>> getInstrument(ItemStack stack, HolderLookup.Provider registries) {
        Holder<Instrument> holder = stack.get(DataComponents.INSTRUMENT);
        if (holder != null) {
            return Optional.of(holder);
        } else {
            Optional<HolderSet.Named<Instrument>> optional = registries.lookupOrThrow(Registries.INSTRUMENT).get(this.instruments);
            if (optional.isPresent()) {
                Iterator<Holder<Instrument>> iterator = optional.get().iterator();
                if (iterator.hasNext()) {
                    return Optional.of(iterator.next());
                }
            }

            return Optional.empty();
        }
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.TOOT_HORN;
    }

    private static void play(Level level, Player player, Instrument instrument) {
        SoundEvent soundEvent = instrument.soundEvent().value();
        float f = instrument.range() / 16.0F;
        level.playSound(player, player, soundEvent, SoundSource.RECORDS, f, 1.0F);
        level.gameEvent(GameEvent.INSTRUMENT_PLAY, player.position(), GameEvent.Context.of(player));
    }
}
