package net.minecraft.world.item;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.event.block.BlockCanBuildEvent;
// CraftBukkit end

public class BlockItem extends Item {
    @Deprecated
    private final Block block;

    public BlockItem(Block block, Item.Properties properties) {
        super(properties);
        this.block = block;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult interactionResult = this.place(new BlockPlaceContext(context));
        return !interactionResult.consumesAction() && context.getItemInHand().has(DataComponents.CONSUMABLE)
            ? super.use(context.getLevel(), context.getPlayer(), context.getHand())
            : interactionResult;
    }

    public InteractionResult place(BlockPlaceContext context) {
        if (!this.getBlock().isEnabled(context.getLevel().enabledFeatures())) {
            return InteractionResult.FAIL;
        } else if (!context.canPlace()) {
            return InteractionResult.FAIL;
        } else {
            BlockPlaceContext blockPlaceContext = this.updatePlacementContext(context);
            if (blockPlaceContext == null) {
                return InteractionResult.FAIL;
            } else {
                BlockState placementState = this.getPlacementState(blockPlaceContext);
                // CraftBukkit start - special case for handling block placement with water lilies and snow buckets
                org.bukkit.block.BlockState bukkitState = null;
                if (this instanceof PlaceOnWaterBlockItem || this instanceof SolidBucketItem) {
                    bukkitState = org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(blockPlaceContext.getLevel(), blockPlaceContext.getClickedPos());
                }
                final org.bukkit.block.BlockState oldBukkitState = bukkitState != null ? bukkitState : org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(blockPlaceContext.getLevel(), blockPlaceContext.getClickedPos()); // Paper - Reset placed block on exception
                // CraftBukkit end

                if (placementState == null) {
                    return InteractionResult.FAIL;
                } else if (!this.placeBlock(blockPlaceContext, placementState)) {
                    return InteractionResult.FAIL;
                } else {
                    BlockPos clickedPos = blockPlaceContext.getClickedPos();
                    Level level = blockPlaceContext.getLevel();
                    Player player = blockPlaceContext.getPlayer();
                    ItemStack itemInHand = blockPlaceContext.getItemInHand();
                    BlockState blockState = level.getBlockState(clickedPos);
                    if (blockState.is(placementState.getBlock())) {
                        blockState = this.updateBlockStateFromTag(clickedPos, level, itemInHand, blockState);
                        // Paper start - Reset placed block on exception
                        try {
                        this.updateCustomBlockEntityTag(clickedPos, level, player, itemInHand, blockState);
                        updateBlockEntityComponents(level, clickedPos, itemInHand);
                        } catch (Exception ex) {
                            oldBukkitState.update(true, false);
                            if (player instanceof ServerPlayer serverPlayer) {
                                org.apache.logging.log4j.LogManager.getLogger().error("Player {} tried placing invalid block", player.getScoreboardName(), ex);
                                serverPlayer.getBukkitEntity().kickPlayer("Packet processing error");
                                return InteractionResult.FAIL;
                            }
                            throw ex; // Rethrow exception if not placed by a player
                        }
                        // Paper end - Reset placed block on exception
                        blockState.getBlock().setPlacedBy(level, clickedPos, blockState, player, itemInHand);
                        // CraftBukkit start
                        if (bukkitState != null) {
                            org.bukkit.event.block.BlockPlaceEvent placeEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockPlaceEvent((net.minecraft.server.level.ServerLevel) level, player, blockPlaceContext.getHand(), bukkitState, clickedPos.getX(), clickedPos.getY(), clickedPos.getZ());
                            if (placeEvent != null && (placeEvent.isCancelled() || !placeEvent.canBuild())) {
                                bukkitState.update(true, false);

                                // Paper - if the event is called here, the inventory should be updated
                                player.containerMenu.sendAllDataToRemote(); // SPIGOT-4541
                                return InteractionResult.FAIL;
                            }
                        }
                        // CraftBukkit end
                        if (player instanceof ServerPlayer) {
                            CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer)player, clickedPos, itemInHand);
                        }
                    }

                    SoundType soundType = blockState.getSoundType();
                    if (player == null) // Paper - Fix block place logic; reintroduce this for the dispenser (i.e the shulker)
                    level.playSound(
                        player,
                        clickedPos,
                        this.getPlaceSound(blockState),
                        SoundSource.BLOCKS,
                        (soundType.getVolume() + 1.0F) / 2.0F,
                        soundType.getPitch() * 0.8F
                    );
                    level.gameEvent(GameEvent.BLOCK_PLACE, clickedPos, GameEvent.Context.of(player, blockState));
                    itemInHand.consume(1, player);
                    return InteractionResult.SUCCESS;
                }
            }
        }
    }

    protected SoundEvent getPlaceSound(BlockState state) {
        return state.getSoundType().getPlaceSound();
    }

    @Nullable
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        return context;
    }

    private static void updateBlockEntityComponents(Level level, BlockPos poa, ItemStack stack) {
        BlockEntity blockEntity = level.getBlockEntity(poa);
        if (blockEntity != null) {
            blockEntity.applyComponentsFromItemStack(stack);
            blockEntity.setChanged();
        }
    }

    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state) {
        // Purpur start - Persistent BlockEntity Lore and DisplayName
        boolean handled = updateCustomBlockEntityTag(level, player, pos, stack);
        if (level.purpurConfig.persistentTileEntityLore) {
            BlockEntity blockEntity1 = level.getBlockEntity(pos);
            if (blockEntity1 != null) {
                blockEntity1.setPersistentLore(stack.getOrDefault(DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY));
            }
        }
        return handled;
        // Purpur end - Persistent BlockEntity Lore and DisplayName
    }

    @Nullable
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState stateForPlacement = this.getBlock().getStateForPlacement(context);
        return stateForPlacement != null && this.canPlace(context, stateForPlacement) ? stateForPlacement : null;
    }

    private BlockState updateBlockStateFromTag(BlockPos pos, Level level, ItemStack stack, BlockState state) {
        BlockItemStateProperties blockItemStateProperties = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        if (blockItemStateProperties.isEmpty()) {
            return state;
        } else {
            BlockState blockState = blockItemStateProperties.apply(state);
            if (blockState != state) {
                level.setBlock(pos, blockState, 2);
            }

            return blockState;
        }
    }

    protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        Player player = context.getPlayer();
        CollisionContext collisionContext = player == null ? CollisionContext.empty() : CollisionContext.of(player);
        // CraftBukkit start
        Level world = context.getLevel(); // Paper - Cancel hit for vanished players
        boolean canBuild = (!this.mustSurvive() || state.canSurvive(world, context.getClickedPos())) && world.checkEntityCollision(state, player, collisionContext, context.getClickedPos(), true); // Paper - Cancel hit for vanished players
        org.bukkit.entity.Player bukkitPlayer = (context.getPlayer() instanceof ServerPlayer) ? (org.bukkit.entity.Player) context.getPlayer().getBukkitEntity() : null;

        BlockCanBuildEvent event = new BlockCanBuildEvent(CraftBlock.at(world, context.getClickedPos()), bukkitPlayer, CraftBlockData.fromData(state), canBuild, org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(context.getHand())); // Paper - Expose hand in BlockCanBuildEvent
        world.getCraftServer().getPluginManager().callEvent(event);

        return event.isBuildable();
        // CraftBukkit end
    }

    protected boolean mustSurvive() {
        return true;
    }

    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return context.getLevel().setBlock(context.getClickedPos(), state, 11);
    }

    public static boolean updateCustomBlockEntityTag(Level level, @Nullable Player player, BlockPos pos, ItemStack stack) {
        if (level.isClientSide) {
            return false;
        } else {
            CustomData customData = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY);
            if (!customData.isEmpty()) {
                BlockEntityType<?> blockEntityType = customData.parseEntityType(level.registryAccess(), Registries.BLOCK_ENTITY_TYPE);
                if (blockEntityType == null) {
                    return false;
                }

                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    BlockEntityType<?> type = blockEntity.getType();
                    if (type != blockEntityType) {
                        return false;
                    }

                    if (!type.onlyOpCanSetNbt() || player != null && (player.canUseGameMasterBlocks() || (player.getAbilities().instabuild && player.getBukkitEntity().hasPermission("minecraft.nbt.place")))) { // Spigot - add permission
                        if (!(level.purpurConfig.silkTouchEnabled && blockEntity instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity && player.getBukkitEntity().hasPermission("purpur.drop.spawners"))) // Purpur - Silk touch spawners
                        return customData.loadInto(blockEntity, level.registryAccess());
                    }

                    return false;
                }
            }

            return false;
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        this.getBlock().appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    @Override
    public boolean shouldPrintOpWarning(ItemStack player, @Nullable Player player1) {
        if (player1 != null && player1.getPermissionLevel() >= 2) {
            CustomData customData = player.get(DataComponents.BLOCK_ENTITY_DATA);
            if (customData != null) {
                BlockEntityType<?> blockEntityType = customData.parseEntityType(player1.level().registryAccess(), Registries.BLOCK_ENTITY_TYPE);
                return blockEntityType != null && blockEntityType.onlyOpCanSetNbt();
            }
        }

        return false;
    }

    public Block getBlock() {
        return this.block;
    }

    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
        blockToItemMap.put(this.getBlock(), item);
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return !(this.getBlock() instanceof ShulkerBoxBlock);
    }

    @Override
    public void onDestroyed(ItemEntity itemEntity) {
        ItemContainerContents itemContainerContents = itemEntity.getItem().set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (itemContainerContents != null) {
            if (itemEntity.level().purpurConfig.shulkerBoxItemDropContentsWhenDestroyed && this.getBlock() instanceof ShulkerBoxBlock) // Purpur - option to disable shulker box items from dropping contents when destroyed
            ItemUtils.onContainerDestroyed(itemEntity, itemContainerContents.nonEmptyItemsCopy());
        }
    }

    public static void setBlockEntityData(ItemStack stack, BlockEntityType<?> blockEntityType, CompoundTag blockEntityData) {
        blockEntityData.remove("id");
        if (blockEntityData.isEmpty()) {
            stack.remove(DataComponents.BLOCK_ENTITY_DATA);
        } else {
            BlockEntity.addEntityType(blockEntityData, blockEntityType);
            stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(blockEntityData));
        }
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.getBlock().requiredFeatures();
    }
}
