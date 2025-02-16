package net.minecraft.world.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

public class AdventureModePredicate {
    private static final Codec<AdventureModePredicate> SIMPLE_CODEC = BlockPredicate.CODEC
        .flatComapMap(
            blockPredicate -> new AdventureModePredicate(List.of(blockPredicate), true), adventureModePredicate -> DataResult.error(() -> "Cannot encode")
        );
    private static final Codec<AdventureModePredicate> FULL_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ExtraCodecs.nonEmptyList(BlockPredicate.CODEC.listOf())
                    .fieldOf("predicates")
                    .forGetter(adventureModePredicate -> adventureModePredicate.predicates),
                Codec.BOOL.optionalFieldOf("show_in_tooltip", Boolean.valueOf(true)).forGetter(AdventureModePredicate::showInTooltip)
            )
            .apply(instance, AdventureModePredicate::new)
    );
    public static final Codec<AdventureModePredicate> CODEC = Codec.withAlternative(FULL_CODEC, SIMPLE_CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, AdventureModePredicate> STREAM_CODEC = StreamCodec.composite(
        BlockPredicate.STREAM_CODEC.apply(ByteBufCodecs.list()),
        adventureModePredicate -> adventureModePredicate.predicates,
        ByteBufCodecs.BOOL,
        AdventureModePredicate::showInTooltip,
        AdventureModePredicate::new
    );
    public static final Component CAN_BREAK_HEADER = Component.translatable("item.canBreak").withStyle(ChatFormatting.GRAY);
    public static final Component CAN_PLACE_HEADER = Component.translatable("item.canPlace").withStyle(ChatFormatting.GRAY);
    private static final Component UNKNOWN_USE = Component.translatable("item.canUse.unknown").withStyle(ChatFormatting.GRAY);
    private final List<BlockPredicate> predicates;
    private final boolean showInTooltip;
    @Nullable
    private List<Component> cachedTooltip;
    @Nullable
    private BlockInWorld lastCheckedBlock;
    private boolean lastResult;
    private boolean checksBlockEntity;

    public AdventureModePredicate(List<BlockPredicate> predicates, boolean showInTooltip) {
        this.predicates = predicates;
        this.showInTooltip = showInTooltip;
    }

    private static boolean areSameBlocks(BlockInWorld first, @Nullable BlockInWorld second, boolean checkNbt) {
        if (second == null || first.getState() != second.getState()) {
            return false;
        } else if (!checkNbt) {
            return true;
        } else if (first.getEntity() == null && second.getEntity() == null) {
            return true;
        } else if (first.getEntity() != null && second.getEntity() != null) {
            RegistryAccess registryAccess = first.getLevel().registryAccess();
            return Objects.equals(first.getEntity().saveWithId(registryAccess), second.getEntity().saveWithId(registryAccess));
        } else {
            return false;
        }
    }

    public boolean test(BlockInWorld block) {
        if (areSameBlocks(block, this.lastCheckedBlock, this.checksBlockEntity)) {
            return this.lastResult;
        } else {
            this.lastCheckedBlock = block;
            this.checksBlockEntity = false;

            for (BlockPredicate blockPredicate : this.predicates) {
                if (blockPredicate.matches(block)) {
                    this.checksBlockEntity = this.checksBlockEntity | blockPredicate.requiresNbt();
                    this.lastResult = true;
                    return true;
                }
            }

            this.lastResult = false;
            return false;
        }
    }

    private List<Component> tooltip() {
        if (this.cachedTooltip == null) {
            this.cachedTooltip = computeTooltip(this.predicates);
        }

        return this.cachedTooltip;
    }

    public void addToTooltip(Consumer<Component> tooltipAdder) {
        this.tooltip().forEach(tooltipAdder);
    }

    public AdventureModePredicate withTooltip(boolean showInTooltip) {
        return new AdventureModePredicate(this.predicates, showInTooltip);
    }

    private static List<Component> computeTooltip(List<BlockPredicate> predicates) {
        for (BlockPredicate blockPredicate : predicates) {
            if (blockPredicate.blocks().isEmpty()) {
                return List.of(UNKNOWN_USE);
            }
        }

        return predicates.stream()
            .flatMap(blockPredicate1 -> blockPredicate1.blocks().orElseThrow().stream())
            .distinct()
            .map(holder -> holder.value().getName().withStyle(ChatFormatting.DARK_GRAY))
            .toList();
    }

    public boolean showInTooltip() {
        return this.showInTooltip;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof AdventureModePredicate adventureModePredicate
                && this.predicates.equals(adventureModePredicate.predicates)
                && this.showInTooltip == adventureModePredicate.showInTooltip;
    }

    @Override
    public int hashCode() {
        return this.predicates.hashCode() * 31 + (this.showInTooltip ? 1 : 0);
    }

    @Override
    public String toString() {
        return "AdventureModePredicate{predicates=" + this.predicates + ", showInTooltip=" + this.showInTooltip + "}";
    }
}
