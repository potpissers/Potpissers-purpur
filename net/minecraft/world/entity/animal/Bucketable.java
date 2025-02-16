package net.minecraft.world.entity.animal;

import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public interface Bucketable {
    boolean fromBucket();

    void setFromBucket(boolean fromBucket);

    void saveToBucketTag(ItemStack stack);

    void loadFromBucketTag(CompoundTag tag);

    ItemStack getBucketItemStack();

    SoundEvent getPickupSound();

    @Deprecated
    static void saveDefaultDataToBucketTag(Mob mob, ItemStack bucket) {
        bucket.set(DataComponents.CUSTOM_NAME, mob.getCustomName());
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, bucket, compoundTag -> {
            if (mob.isNoAi()) {
                compoundTag.putBoolean("NoAI", mob.isNoAi());
            }

            if (mob.isSilent()) {
                compoundTag.putBoolean("Silent", mob.isSilent());
            }

            if (mob.isNoGravity()) {
                compoundTag.putBoolean("NoGravity", mob.isNoGravity());
            }

            if (mob.hasGlowingTag()) {
                compoundTag.putBoolean("Glowing", mob.hasGlowingTag());
            }

            if (mob.isInvulnerable()) {
                compoundTag.putBoolean("Invulnerable", mob.isInvulnerable());
            }

            compoundTag.putFloat("Health", mob.getHealth());
        });
    }

    @Deprecated
    static void loadDefaultDataFromBucketTag(Mob mob, CompoundTag tag) {
        if (tag.contains("NoAI")) {
            mob.setNoAi(tag.getBoolean("NoAI"));
        }

        if (tag.contains("Silent")) {
            mob.setSilent(tag.getBoolean("Silent"));
        }

        if (tag.contains("NoGravity")) {
            mob.setNoGravity(tag.getBoolean("NoGravity"));
        }

        if (tag.contains("Glowing")) {
            mob.setGlowingTag(tag.getBoolean("Glowing"));
        }

        if (tag.contains("Invulnerable")) {
            mob.setInvulnerable(tag.getBoolean("Invulnerable"));
        }

        if (tag.contains("Health", 99)) {
            mob.setHealth(tag.getFloat("Health"));
        }
    }

    static <T extends LivingEntity & Bucketable> Optional<InteractionResult> bucketMobPickup(Player player, InteractionHand hand, T entity) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.getItem() == Items.WATER_BUCKET && entity.isAlive()) {
            entity.playSound(entity.getPickupSound(), 1.0F, 1.0F);
            ItemStack bucketItemStack = entity.getBucketItemStack();
            entity.saveToBucketTag(bucketItemStack);
            ItemStack itemStack = ItemUtils.createFilledResult(itemInHand, player, bucketItemStack, false);
            player.setItemInHand(hand, itemStack);
            Level level = entity.level();
            if (!level.isClientSide) {
                CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayer)player, bucketItemStack);
            }

            entity.discard();
            return Optional.of(InteractionResult.SUCCESS);
        } else {
            return Optional.empty();
        }
    }
}
