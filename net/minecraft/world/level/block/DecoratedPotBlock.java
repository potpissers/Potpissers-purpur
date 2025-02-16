package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DecoratedPotBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<DecoratedPotBlock> CODEC = simpleCodec(DecoratedPotBlock::new);
    public static final ResourceLocation SHERDS_DYNAMIC_DROP_ID = ResourceLocation.withDefaultNamespace("sherds");
    private static final VoxelShape BOUNDING_BOX = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);
    private static final EnumProperty<Direction> HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty CRACKED = BlockStateProperties.CRACKED;
    private static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    @Override
    public MapCodec<DecoratedPotBlock> codec() {
        return CODEC;
    }

    protected DecoratedPotBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(HORIZONTAL_FACING, Direction.NORTH)
                .setValue(WATERLOGGED, Boolean.valueOf(false))
                .setValue(CRACKED, Boolean.valueOf(false))
        );
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random
    ) {
        if (state.getValue(WATERLOGGED)) {
            scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState()
            .setValue(HORIZONTAL_FACING, context.getHorizontalDirection())
            .setValue(WATERLOGGED, Boolean.valueOf(fluidState.getType() == Fluids.WATER))
            .setValue(CRACKED, Boolean.valueOf(false));
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (level.getBlockEntity(pos) instanceof DecoratedPotBlockEntity decoratedPotBlockEntity) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            } else {
                ItemStack theItem = decoratedPotBlockEntity.getTheItem();
                if (!stack.isEmpty()
                    && (theItem.isEmpty() || ItemStack.isSameItemSameComponents(theItem, stack) && theItem.getCount() < theItem.getMaxStackSize())) {
                    decoratedPotBlockEntity.wobble(DecoratedPotBlockEntity.WobbleStyle.POSITIVE);
                    player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
                    ItemStack itemStack = stack.consumeAndReturn(1, player);
                    float f;
                    if (decoratedPotBlockEntity.isEmpty()) {
                        decoratedPotBlockEntity.setTheItem(itemStack);
                        f = (float)itemStack.getCount() / itemStack.getMaxStackSize();
                    } else {
                        theItem.grow(1);
                        f = (float)theItem.getCount() / theItem.getMaxStackSize();
                    }

                    level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 1.0F, 0.7F + 0.5F * f);
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.DUST_PLUME, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 7, 0.0, 0.0, 0.0, 0.0);
                    }

                    decoratedPotBlockEntity.setChanged();
                    level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
                    return InteractionResult.SUCCESS;
                } else {
                    return InteractionResult.TRY_WITH_EMPTY_HAND;
                }
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof DecoratedPotBlockEntity decoratedPotBlockEntity) {
            level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT_FAIL, SoundSource.BLOCKS, 1.0F, 1.0F);
            decoratedPotBlockEntity.wobble(DecoratedPotBlockEntity.WobbleStyle.NEGATIVE);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BOUNDING_BOX;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING, WATERLOGGED, CRACKED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DecoratedPotBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        Containers.dropContentsOnDestroy(state, newState, level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof DecoratedPotBlockEntity decoratedPotBlockEntity) {
            params.withDynamicDrop(SHERDS_DYNAMIC_DROP_ID, output -> {
                for (Item item : decoratedPotBlockEntity.getDecorations().ordered()) {
                    output.accept(item.getDefaultInstance());
                }
            });
        }

        return super.getDrops(state, params);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        ItemStack mainHandItem = player.getMainHandItem();
        BlockState blockState = state;
        if (mainHandItem.is(ItemTags.BREAKS_DECORATED_POTS) && !EnchantmentHelper.hasTag(mainHandItem, EnchantmentTags.PREVENTS_DECORATED_POT_SHATTERING)) {
            blockState = state.setValue(CRACKED, Boolean.valueOf(true));
            level.setBlock(pos, blockState, 4);
        }

        return super.playerWillDestroy(level, pos, blockState, player);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected SoundType getSoundType(BlockState state) {
        return state.getValue(CRACKED) ? SoundType.DECORATED_POT_CRACKED : SoundType.DECORATED_POT;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        PotDecorations potDecorations = stack.getOrDefault(DataComponents.POT_DECORATIONS, PotDecorations.EMPTY);
        if (!potDecorations.equals(PotDecorations.EMPTY)) {
            tooltipComponents.add(CommonComponents.EMPTY);
            Stream.of(potDecorations.front(), potDecorations.left(), potDecorations.right(), potDecorations.back())
                .forEach(
                    optional -> tooltipComponents.add(new ItemStack(optional.orElse(Items.BRICK), 1).getHoverName().plainCopy().withStyle(ChatFormatting.GRAY))
                );
        }
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        BlockPos blockPos = hit.getBlockPos();
        if (level instanceof ServerLevel serverLevel && projectile.mayInteract(serverLevel, blockPos) && projectile.mayBreak(serverLevel)) {
            level.setBlock(blockPos, state.setValue(CRACKED, Boolean.valueOf(true)), 4);
            level.destroyBlock(blockPos, true, projectile);
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        if (level.getBlockEntity(pos) instanceof DecoratedPotBlockEntity decoratedPotBlockEntity) {
            PotDecorations decorations = decoratedPotBlockEntity.getDecorations();
            return DecoratedPotBlockEntity.createDecoratedPotItem(decorations);
        } else {
            return super.getCloneItemStack(level, pos, state, includeData);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(HORIZONTAL_FACING, rotation.rotate(state.getValue(HORIZONTAL_FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(HORIZONTAL_FACING)));
    }
}
