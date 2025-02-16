package net.minecraft.world.item;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;

public class MobBucketItem extends BucketItem {
    private static final MapCodec<TropicalFish.Variant> VARIANT_FIELD_CODEC = TropicalFish.Variant.CODEC.fieldOf("BucketVariantTag");
    private final EntityType<? extends Mob> type;
    private final SoundEvent emptySound;

    public MobBucketItem(EntityType<? extends Mob> type, Fluid content, SoundEvent emptySound, Item.Properties properties) {
        super(content, properties);
        this.type = type;
        this.emptySound = emptySound;
    }

    @Override
    public void checkExtraContent(@Nullable Player player, Level level, ItemStack containerStack, BlockPos pos) {
        if (level instanceof ServerLevel) {
            this.spawn((ServerLevel)level, containerStack, pos);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
        }
    }

    @Override
    protected void playEmptySound(@Nullable Player player, LevelAccessor level, BlockPos pos) {
        level.playSound(player, pos, this.emptySound, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    private void spawn(ServerLevel serverLevel, ItemStack bucketedMobStack, BlockPos pos) {
        Mob mob = this.type
            .create(serverLevel, EntityType.createDefaultStackConfig(serverLevel, bucketedMobStack, null), pos, EntitySpawnReason.BUCKET, true, false);
        if (mob instanceof Bucketable bucketable) {
            CustomData customData = bucketedMobStack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY);
            bucketable.loadFromBucketTag(customData.copyTag());
            bucketable.setFromBucket(true);
        }

        if (mob != null) {
            serverLevel.addFreshEntityWithPassengers(mob);
            mob.playAmbientSound();
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (this.type == EntityType.TROPICAL_FISH) {
            CustomData customData = stack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY);
            if (customData.isEmpty()) {
                return;
            }

            Optional<TropicalFish.Variant> optional = customData.read(VARIANT_FIELD_CODEC).result();
            if (optional.isPresent()) {
                TropicalFish.Variant variant = optional.get();
                ChatFormatting[] chatFormattings = new ChatFormatting[]{ChatFormatting.ITALIC, ChatFormatting.GRAY};
                String string = "color.minecraft." + variant.baseColor();
                String string1 = "color.minecraft." + variant.patternColor();
                int index = TropicalFish.COMMON_VARIANTS.indexOf(variant);
                if (index != -1) {
                    tooltipComponents.add(Component.translatable(TropicalFish.getPredefinedName(index)).withStyle(chatFormattings));
                    return;
                }

                tooltipComponents.add(variant.pattern().displayName().plainCopy().withStyle(chatFormattings));
                MutableComponent mutableComponent = Component.translatable(string);
                if (!string.equals(string1)) {
                    mutableComponent.append(", ").append(Component.translatable(string1));
                }

                mutableComponent.withStyle(chatFormattings);
                tooltipComponents.add(mutableComponent);
            }
        }
    }
}
