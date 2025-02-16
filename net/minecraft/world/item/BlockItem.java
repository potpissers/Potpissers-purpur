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
                        this.updateCustomBlockEntityTag(clickedPos, level, player, itemInHand, blockState);
                        updateBlockEntityComponents(level, clickedPos, itemInHand);
                        blockState.getBlock().setPlacedBy(level, clickedPos, blockState, player, itemInHand);
                        if (player instanceof ServerPlayer) {
                            CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer)player, clickedPos, itemInHand);
                        }
                    }

                    SoundType soundType = blockState.getSoundType();
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
        return updateCustomBlockEntityTag(level, player, pos, stack);
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
        return (!this.mustSurvive() || state.canSurvive(context.getLevel(), context.getClickedPos()))
            && context.getLevel().isUnobstructed(state, context.getClickedPos(), collisionContext);
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

                    if (!type.onlyOpCanSetNbt() || player != null && player.canUseGameMasterBlocks()) {
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
