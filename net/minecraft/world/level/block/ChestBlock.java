package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.floats.Float2FloatFunction;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ChestBlock extends AbstractChestBlock<ChestBlockEntity> implements SimpleWaterloggedBlock {
    public static final MapCodec<ChestBlock> CODEC = simpleCodec(properties -> new ChestBlock(() -> BlockEntityType.CHEST, properties));
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<ChestType> TYPE = BlockStateProperties.CHEST_TYPE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final int EVENT_SET_OPEN_COUNT = 1;
    protected static final int AABB_OFFSET = 1;
    protected static final int AABB_HEIGHT = 14;
    protected static final VoxelShape NORTH_AABB = Block.box(1.0, 0.0, 0.0, 15.0, 14.0, 15.0);
    protected static final VoxelShape SOUTH_AABB = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 16.0);
    protected static final VoxelShape WEST_AABB = Block.box(0.0, 0.0, 1.0, 15.0, 14.0, 15.0);
    protected static final VoxelShape EAST_AABB = Block.box(1.0, 0.0, 1.0, 16.0, 14.0, 15.0);
    protected static final VoxelShape AABB = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0);
    private static final DoubleBlockCombiner.Combiner<ChestBlockEntity, Optional<Container>> CHEST_COMBINER = new DoubleBlockCombiner.Combiner<ChestBlockEntity, Optional<Container>>() {
        @Override
        public Optional<Container> acceptDouble(ChestBlockEntity first, ChestBlockEntity second) {
            return Optional.of(new CompoundContainer(first, second));
        }

        @Override
        public Optional<Container> acceptSingle(ChestBlockEntity single) {
            return Optional.of(single);
        }

        @Override
        public Optional<Container> acceptNone() {
            return Optional.empty();
        }
    };
    public static DoubleBlockCombiner.Combiner<ChestBlockEntity, Optional<MenuProvider>> MENU_PROVIDER_COMBINER = new DoubleBlockCombiner.Combiner<ChestBlockEntity, Optional<MenuProvider>>() {
        @Override
        public Optional<MenuProvider> acceptDouble(final ChestBlockEntity first, final ChestBlockEntity second) {
            final Container container = new CompoundContainer(first, second);
            return Optional.of(DoubleInventory.wrap(new MenuProvider() { // CraftBukkit - wrap for identification
                @Nullable
                @Override
                public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
                    if (first.canOpen(player) && second.canOpen(player)) {
                        first.unpackLootTable(playerInventory.player);
                        second.unpackLootTable(playerInventory.player);
                        return ChestMenu.sixRows(containerId, playerInventory, container);
                    } else {
                        return null;
                    }
                }

                @Override
                public Component getDisplayName() {
                    if (first.hasCustomName()) {
                        return first.getDisplayName();
                    } else {
                        return (Component)(second.hasCustomName() ? second.getDisplayName() : Component.translatable("container.chestDouble"));
                    }
                }
            }, (CompoundContainer) container)); // CraftBukkit - wrap for identification
        }

        @Override
        public Optional<MenuProvider> acceptSingle(ChestBlockEntity single) {
            return Optional.of(single);
        }

        @Override
        public Optional<MenuProvider> acceptNone() {
            return Optional.empty();
        }
    };

    // CraftBukkit start
    public static class DoubleInventory implements MenuProvider {

        private final MenuProvider delegate;
        public final CompoundContainer container; // expose to api

        private DoubleInventory(MenuProvider delegate, CompoundContainer container) {
            this.delegate = delegate;
            this.container = container;
        }

        public static DoubleInventory wrap(MenuProvider delegate, CompoundContainer container) {
            return new DoubleInventory(delegate, container);
        }

        @Nullable
        @Override
        public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
            return this.delegate.createMenu(syncId, playerInventory, player);
        }

        @Override
        public Component getDisplayName() {
            return this.delegate.getDisplayName();
        }
    }
    // CraftBukkit end

    @Override
    public MapCodec<? extends ChestBlock> codec() {
        return CODEC;
    }

    protected ChestBlock(Supplier<BlockEntityType<? extends ChestBlockEntity>> blockEntityType, BlockBehaviour.Properties properties) {
        super(properties, blockEntityType);
        this.registerDefaultState(
            this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TYPE, ChestType.SINGLE).setValue(WATERLOGGED, Boolean.valueOf(false))
        );
    }

    public static DoubleBlockCombiner.BlockType getBlockType(BlockState state) {
        ChestType chestType = state.getValue(TYPE);
        if (chestType == ChestType.SINGLE) {
            return DoubleBlockCombiner.BlockType.SINGLE;
        } else {
            return chestType == ChestType.RIGHT ? DoubleBlockCombiner.BlockType.FIRST : DoubleBlockCombiner.BlockType.SECOND;
        }
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

        if (neighborState.is(this) && direction.getAxis().isHorizontal()) {
            ChestType chestType = neighborState.getValue(TYPE);
            if (state.getValue(TYPE) == ChestType.SINGLE
                && chestType != ChestType.SINGLE
                && state.getValue(FACING) == neighborState.getValue(FACING)
                && getConnectedDirection(neighborState) == direction.getOpposite()) {
                return state.setValue(TYPE, chestType.getOpposite());
            }
        } else if (getConnectedDirection(state) == direction) {
            return state.setValue(TYPE, ChestType.SINGLE);
        }

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(TYPE) == ChestType.SINGLE) {
            return AABB;
        } else {
            switch (getConnectedDirection(state)) {
                case NORTH:
                default:
                    return NORTH_AABB;
                case SOUTH:
                    return SOUTH_AABB;
                case WEST:
                    return WEST_AABB;
                case EAST:
                    return EAST_AABB;
            }
        }
    }

    public static Direction getConnectedDirection(BlockState state) {
        Direction direction = state.getValue(FACING);
        return state.getValue(TYPE) == ChestType.LEFT ? direction.getClockWise() : direction.getCounterClockWise();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        ChestType chestType = ChestType.SINGLE;
        Direction opposite = context.getHorizontalDirection().getOpposite();
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        boolean isSecondaryUseActive = context.isSecondaryUseActive();
        Direction clickedFace = context.getClickedFace();
        if (clickedFace.getAxis().isHorizontal() && isSecondaryUseActive) {
            Direction direction = this.candidatePartnerFacing(context, clickedFace.getOpposite());
            if (direction != null && direction.getAxis() != clickedFace.getAxis()) {
                opposite = direction;
                chestType = direction.getCounterClockWise() == clickedFace.getOpposite() ? ChestType.RIGHT : ChestType.LEFT;
            }
        }

        if (chestType == ChestType.SINGLE && !isSecondaryUseActive) {
            if (opposite == this.candidatePartnerFacing(context, opposite.getClockWise())) {
                chestType = ChestType.LEFT;
            } else if (opposite == this.candidatePartnerFacing(context, opposite.getCounterClockWise())) {
                chestType = ChestType.RIGHT;
            }
        }

        return this.defaultBlockState()
            .setValue(FACING, opposite)
            .setValue(TYPE, chestType)
            .setValue(WATERLOGGED, Boolean.valueOf(fluidState.getType() == Fluids.WATER));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Nullable
    private Direction candidatePartnerFacing(BlockPlaceContext context, Direction direction) {
        BlockState blockState = context.getLevel().getBlockState(context.getClickedPos().relative(direction));
        return blockState.is(this) && blockState.getValue(TYPE) == ChestType.SINGLE ? blockState.getValue(FACING) : null;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        Containers.dropContentsOnDestroy(state, newState, level, pos);
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel) {
            MenuProvider menuProvider = this.getMenuProvider(state, level, pos);
            if (menuProvider != null && player.openMenu(menuProvider).isPresent()) { // Paper - Fix InventoryOpenEvent cancellation
                player.awardStat(this.getOpenChestStat());
                PiglinAi.angerNearbyPiglins(serverLevel, player, true);
            }
        }

        return InteractionResult.SUCCESS;
    }

    protected Stat<ResourceLocation> getOpenChestStat() {
        return Stats.CUSTOM.get(Stats.OPEN_CHEST);
    }

    public BlockEntityType<? extends ChestBlockEntity> blockEntityType() {
        return this.blockEntityType.get();
    }

    @Nullable
    public static Container getContainer(ChestBlock chest, BlockState state, Level level, BlockPos pos, boolean override) {
        return chest.combine(state, level, pos, override).apply(CHEST_COMBINER).orElse(null);
    }

    @Override
    public DoubleBlockCombiner.NeighborCombineResult<? extends ChestBlockEntity> combine(BlockState state, Level level, BlockPos pos, boolean override) {
        BiPredicate<LevelAccessor, BlockPos> biPredicate;
        if (override) {
            biPredicate = (levelAccessor, blockPos) -> false;
        } else {
            biPredicate = ChestBlock::isChestBlockedAt;
        }

        return DoubleBlockCombiner.combineWithNeigbour(
            this.blockEntityType.get(), ChestBlock::getBlockType, ChestBlock::getConnectedDirection, FACING, state, level, pos, biPredicate
        );
    }

    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
    // CraftBukkit start
        return this.getMenuProvider(state, level, pos, false);
    }

    @Nullable
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos, boolean ignoreObstructions) {
        return this.combine(state, level, pos, ignoreObstructions).apply(MENU_PROVIDER_COMBINER).orElse(null);
    // CraftBukkit end
    }

    public static DoubleBlockCombiner.Combiner<ChestBlockEntity, Float2FloatFunction> opennessCombiner(final LidBlockEntity lid) {
        return new DoubleBlockCombiner.Combiner<ChestBlockEntity, Float2FloatFunction>() {
            @Override
            public Float2FloatFunction acceptDouble(ChestBlockEntity first, ChestBlockEntity second) {
                return partialTicks -> Math.max(first.getOpenNess(partialTicks), second.getOpenNess(partialTicks));
            }

            @Override
            public Float2FloatFunction acceptSingle(ChestBlockEntity single) {
                return single::getOpenNess;
            }

            @Override
            public Float2FloatFunction acceptNone() {
                return lid::getOpenNess;
            }
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChestBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide ? createTickerHelper(blockEntityType, this.blockEntityType(), ChestBlockEntity::lidAnimateTick) : null;
    }

    public static boolean isChestBlockedAt(LevelAccessor level, BlockPos pos) {
        return isBlockedChestByBlock(level, pos) || isCatSittingOnChest(level, pos);
    }

    public static boolean isBlockedChestByBlock(BlockGetter level, BlockPos pos) {
        if (level instanceof Level level1 && level1.purpurConfig.chestOpenWithBlockOnTop) return false; // Purpur - Option for chests to open even with a solid block on top
        BlockPos blockPos = pos.above();
        return level.getBlockState(blockPos).isRedstoneConductor(level, blockPos);
    }

    private static boolean isCatSittingOnChest(LevelAccessor level, BlockPos pos) {
        // Paper start - Option to disable chest cat detection
        if (level.getMinecraftWorld().paperConfig().entities.behavior.disableChestCatDetection) {
            return false;
        }
        // Paper end - Option to disable chest cat detection
        List<Cat> entitiesOfClass = level.getEntitiesOfClass(
            Cat.class, new AABB(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1)
        );
        if (!entitiesOfClass.isEmpty()) {
            for (Cat cat : entitiesOfClass) {
                if (cat.isInSittingPose()) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState blockState, Level level, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromContainer(getContainer(this, blockState, level, pos, false));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TYPE, WATERLOGGED);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ChestBlockEntity) {
            ((ChestBlockEntity)blockEntity).recheckOpen();
        }
    }
}
