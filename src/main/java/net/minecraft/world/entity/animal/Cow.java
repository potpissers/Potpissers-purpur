package net.minecraft.world.entity.animal;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.player.PlayerBucketFillEvent;
// CraftBukkit end

public class Cow extends Animal {

    private static final EntityDimensions BABY_DIMENSIONS = EntityType.COW.getDimensions().scale(0.5F).withEyeHeight(0.665F);

    public Cow(EntityType<? extends Cow> type, Level world) {
        super(type, world);
    }

    // Purpur start
    @Override
    public boolean isRidable() {
        return level().purpurConfig.cowRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.cowRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.cowControllable;
    }

    @Override
    public void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.cowMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.cowScale);
    }
    // Purpur end

    @Override
    public int getPurpurBreedTime() {
        return this.level().purpurConfig.cowBreedingTicks;
    }

    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.cowTakeDamageFromWater;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur
        this.goalSelector.addGoal(1, new PanicGoal(this, 2.0D));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.25D, (itemstack) -> {
            return level().purpurConfig.cowFeedMushrooms > 0 && (itemstack.is(net.minecraft.world.level.block.Blocks.RED_MUSHROOM.asItem()) || itemstack.is(net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM.asItem())) || itemstack.is(ItemTags.COW_FOOD); // Purpur
        }, false));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.25D));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.COW_FOOD);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0D).add(Attributes.MOVEMENT_SPEED, 0.20000000298023224D);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.COW_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.COW_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.COW_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.COW_STEP, 0.15F, 1.0F);
    }

    @Override
    public float getSoundVolume() {
        return 0.4F;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (getRider() != null) return InteractionResult.PASS; // Purpur
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.is(Items.BUCKET) && !this.isBaby()) {
            // CraftBukkit start - Got milk?
            PlayerBucketFillEvent event = CraftEventFactory.callPlayerBucketFillEvent((ServerLevel) player.level(), player, this.blockPosition(), this.blockPosition(), null, itemstack, Items.MILK_BUCKET, hand);

            if (event.isCancelled()) {
                player.containerMenu.sendAllDataToRemote(); // Paper - Fix inventory desync
                return tryRide(player, hand); // Purpur
            }
            // CraftBukkit end

            player.playSound(SoundEvents.COW_MILK, 1.0F, 1.0F);
            ItemStack itemstack1 = ItemUtils.createFilledResult(itemstack, player, CraftItemStack.asNMSCopy(event.getItemStack())); // CraftBukkit

            player.setItemInHand(hand, itemstack1);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        // Purpur start - feed mushroom to change to mooshroom
        } else if (level().purpurConfig.cowFeedMushrooms > 0 && this.getType() != EntityType.MOOSHROOM && isMushroom(itemstack)) {
            return this.feedMushroom(player, itemstack);
        // Purpur end
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Nullable
    @Override
    public Cow getBreedOffspring(ServerLevel world, AgeableMob entity) {
        return (Cow) EntityType.COW.create(world);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? Cow.BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    // Purpur start - feed mushroom to change to mooshroom
    private int redMushroomsFed = 0;
    private int brownMushroomsFed = 0;

    private boolean isMushroom(ItemStack stack) {
        return stack.getItem() == net.minecraft.world.level.block.Blocks.RED_MUSHROOM.asItem() || stack.getItem() == net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM.asItem();
    }

    private int incrementFeedCount(ItemStack stack) {
        if (stack.getItem() == net.minecraft.world.level.block.Blocks.RED_MUSHROOM.asItem()) {
            return ++redMushroomsFed;
        } else {
            return ++brownMushroomsFed;
        }
    }

    private InteractionResult feedMushroom(Player player, ItemStack stack) {
        level().broadcastEntityEvent(this, (byte) 18); // hearts
        playSound(SoundEvents.COW_MILK, 1.0F, 1.0F);
        if (incrementFeedCount(stack) < level().purpurConfig.cowFeedMushrooms) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return InteractionResult.CONSUME; // require 5 mushrooms to transform (prevents mushroom duping)
        }
        MushroomCow mooshroom = EntityType.MOOSHROOM.create(level());
        if (mooshroom == null) {
            return InteractionResult.PASS;
        }
        if (stack.getItem() == net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM.asItem()) {
            mooshroom.setVariant(MushroomCow.MushroomType.BROWN);
        } else {
            mooshroom.setVariant(MushroomCow.MushroomType.RED);
        }
        mooshroom.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
        mooshroom.setHealth(this.getHealth());
        mooshroom.setAge(getAge());
        mooshroom.copyPosition(this);
        mooshroom.setYBodyRot(this.yBodyRot);
        mooshroom.setYHeadRot(this.getYHeadRot());
        mooshroom.yRotO = this.yRotO;
        mooshroom.xRotO = this.xRotO;
        if (this.hasCustomName()) {
            mooshroom.setCustomName(this.getCustomName());
        }
        if (CraftEventFactory.callEntityTransformEvent(this, mooshroom, org.bukkit.event.entity.EntityTransformEvent.TransformReason.INFECTION).isCancelled()) {
            return InteractionResult.PASS;
        }
        this.level().addFreshEntity(mooshroom);
        this.remove(RemovalReason.DISCARDED, org.bukkit.event.entity.EntityRemoveEvent.Cause.DISCARD);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        for (int i = 0; i < 15; ++i) {
            ((ServerLevel) level()).sendParticles(((ServerLevel) level()).players(), null, net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                    getX() + random.nextFloat(), getY() + (random.nextFloat() * 2), getZ() + random.nextFloat(), 1,
                    random.nextGaussian() * 0.05D, random.nextGaussian() * 0.05D, random.nextGaussian() * 0.05D, 0, true);
        }
        return InteractionResult.SUCCESS;
    }
    // Purpur end
}
