package net.minecraft.world.item;

import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class BucketItem extends Item implements DispensibleContainerItem {
    private static @Nullable ItemStack itemLeftInHandAfterPlayerBucketEmptyEvent = null; // Paper - Fix PlayerBucketEmptyEvent result itemstack
    public final Fluid content;

    public BucketItem(Fluid content, Item.Properties properties) {
        super(properties);
        this.content = content;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        BlockHitResult playerPovHitResult = getPlayerPOVHitResult(
            level, player, this.content == Fluids.EMPTY ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE
        );
        if (playerPovHitResult.getType() == HitResult.Type.MISS) {
            return InteractionResult.PASS;
        } else if (playerPovHitResult.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        } else {
            BlockPos blockPos = playerPovHitResult.getBlockPos();
            Direction direction = playerPovHitResult.getDirection();
            BlockPos blockPos1 = blockPos.relative(direction);
            if (!level.mayInteract(player, blockPos) || !player.mayUseItemAt(blockPos1, direction, itemInHand)) {
                return InteractionResult.FAIL;
            } else if (this.content == Fluids.EMPTY) {
                BlockState blockState = level.getBlockState(blockPos);
                if (blockState.getBlock() instanceof BucketPickup bucketPickup) {
                    // CraftBukkit start
                    ItemStack dummyFluid = bucketPickup.pickupBlock(player, org.bukkit.craftbukkit.util.DummyGeneratorAccess.INSTANCE, blockPos, blockState);
                    if (dummyFluid.isEmpty()) return InteractionResult.FAIL; // Don't fire event if the bucket won't be filled.
                    org.bukkit.event.player.PlayerBucketFillEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerBucketFillEvent((net.minecraft.server.level.ServerLevel) level, player, blockPos, blockPos, playerPovHitResult.getDirection(), itemInHand, dummyFluid.getItem(), hand);

                    if (event.isCancelled()) {
                        // ((ServerPlayer) user).connection.send(new ClientboundBlockUpdatePacket(level, blockPos)); // SPIGOT-5163 (see PlayerInteractManager) // Paper - Don't resend blocks
                        ((ServerPlayer) player).getBukkitEntity().updateInventory(); // SPIGOT-4541
                        return InteractionResult.FAIL;
                    }
                    // CraftBukkit end
                    ItemStack itemStack = bucketPickup.pickupBlock(player, level, blockPos, blockState);
                    if (!itemStack.isEmpty()) {
                        player.awardStat(Stats.ITEM_USED.get(this));
                        bucketPickup.getPickupSound().ifPresent(soundEvent -> player.playSound(soundEvent, 1.0F, 1.0F));
                        level.gameEvent(player, GameEvent.FLUID_PICKUP, blockPos);
                        ItemStack itemStack1 = ItemUtils.createFilledResult(itemInHand, player, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItemStack())); // CraftBukkit
                        if (!level.isClientSide) {
                            CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayer)player, itemStack);
                        }

                        return InteractionResult.SUCCESS.heldItemTransformedTo(itemStack1);
                    }
                }

                return InteractionResult.FAIL;
            } else {
                BlockState blockState = level.getBlockState(blockPos);
                BlockPos blockPos2 = blockState.getBlock() instanceof LiquidBlockContainer && this.content == Fluids.WATER ? blockPos : blockPos1;
                if (this.emptyContents(player, level, blockPos2, playerPovHitResult, playerPovHitResult.getDirection(), blockPos, itemInHand, hand)) { // CraftBukkit
                    this.checkExtraContent(player, level, itemInHand, blockPos2);
                    if (player instanceof ServerPlayer) {
                        CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer)player, blockPos2, itemInHand);
                    }

                    player.awardStat(Stats.ITEM_USED.get(this));
                    ItemStack itemStack = ItemUtils.createFilledResult(itemInHand, player, getEmptySuccessItem(itemInHand, player));
                    return InteractionResult.SUCCESS.heldItemTransformedTo(itemStack);
                } else {
                    return InteractionResult.FAIL;
                }
            }
        }
    }

    public static ItemStack getEmptySuccessItem(ItemStack bucketStack, Player player) {
        // Paper start - Fix PlayerBucketEmptyEvent result itemstack
        if (itemLeftInHandAfterPlayerBucketEmptyEvent != null) {
            ItemStack itemInHand = itemLeftInHandAfterPlayerBucketEmptyEvent;
            itemLeftInHandAfterPlayerBucketEmptyEvent = null;
            return itemInHand;
        }
        // Paper end - Fix PlayerBucketEmptyEvent result itemstack
        return !player.hasInfiniteMaterials() ? new ItemStack(Items.BUCKET) : bucketStack;
    }

    @Override
    public void checkExtraContent(@Nullable Player player, Level level, ItemStack containerStack, BlockPos pos) {
    }

    @Override
    public boolean emptyContents(@Nullable Player player, Level level, BlockPos pos, @Nullable BlockHitResult result) {
        // CraftBukkit start
        return this.emptyContents(player, level, pos, result, null, null, null, InteractionHand.MAIN_HAND);
    }

    public boolean emptyContents(@Nullable Player player, Level level, BlockPos pos, @Nullable BlockHitResult result, Direction enumdirection, BlockPos clicked, ItemStack itemstack, InteractionHand enumhand) {
        // CraftBukkit end
        if (!(this.content instanceof FlowingFluid flowingFluid)) {
            return false;
        } else {
            BlockState blockState = level.getBlockState(pos);
            Block block = blockState.getBlock();
            boolean canBeReplaced = blockState.canBeReplaced(this.content);
            boolean flag = blockState.isAir()
                || canBeReplaced
                || block instanceof LiquidBlockContainer liquidBlockContainer
                    && liquidBlockContainer.canPlaceLiquid(player, level, pos, blockState, this.content);
            // CraftBukkit start
            if (flag && player != null) {
                org.bukkit.event.player.PlayerBucketEmptyEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerBucketEmptyEvent((net.minecraft.server.level.ServerLevel) level, player, pos, clicked, enumdirection, itemstack, enumhand);
                if (event.isCancelled()) {
                    // ((ServerPlayer) player).connection.send(new ClientboundBlockUpdatePacket(level, pos)); // SPIGOT-4238: needed when looking through entity // Paper - Don't resend blocks
                    ((ServerPlayer) player).getBukkitEntity().updateInventory(); // SPIGOT-4541
                    return false;
                }
                itemLeftInHandAfterPlayerBucketEmptyEvent = event.getItemStack() != null ? event.getItemStack().equals(org.bukkit.craftbukkit.inventory.CraftItemStack.asNewCraftStack(net.minecraft.world.item.Items.BUCKET)) ? null : org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItemStack()) : ItemStack.EMPTY; // Paper - Fix PlayerBucketEmptyEvent result itemstack
            }
            // CraftBukkit end
            if (!flag) {
                return result != null && this.emptyContents(player, level, result.getBlockPos().relative(result.getDirection()), null, enumdirection, clicked, itemstack, enumhand); // CraftBukkit
            } else if ((level.dimensionType().ultraWarm() || (level.isTheEnd() && !org.purpurmc.purpur.PurpurConfig.allowWaterPlacementInTheEnd)) && this.content.is(FluidTags.WATER)) { // Purpur - Add allow water in end world option
                int x = pos.getX();
                int y = pos.getY();
                int z = pos.getZ();
                level.playSound(
                    player, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 2.6F + (level.random.nextFloat() - level.random.nextFloat()) * 0.8F
                );

                for (int i = 0; i < 8; i++) {
                    ((net.minecraft.server.level.ServerLevel) level).sendParticlesSource(null, ParticleTypes.LARGE_SMOKE, true, false, x + Math.random(), y + Math.random(), z + Math.random(), 1, 0.0D, 0.0D, 0.0D, 0.0D); // Purpur - Add allow water in end world option
                }

                return true;
            } else if (block instanceof LiquidBlockContainer liquidBlockContainerx && this.content == Fluids.WATER) {
                liquidBlockContainerx.placeLiquid(level, pos, blockState, flowingFluid.getSource(false));
                this.playEmptySound(player, level, pos);
                return true;
            } else {
                if (!level.isClientSide && canBeReplaced && !blockState.liquid()) {
                    level.destroyBlock(pos, true);
                }

                if (!level.setBlock(pos, this.content.defaultFluidState().createLegacyBlock(), 11) && !blockState.getFluidState().isSource()) {
                    return false;
                } else {
                    this.playEmptySound(player, level, pos);
                    return true;
                }
            }
        }
    }

    protected void playEmptySound(@Nullable Player player, LevelAccessor level, BlockPos pos) {
        SoundEvent soundEvent = this.content.is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;
        level.playSound(player, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
    }
}
