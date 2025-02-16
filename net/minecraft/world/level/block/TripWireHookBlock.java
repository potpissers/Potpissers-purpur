package net.minecraft.world.level.block;

import com.google.common.base.MoreObjects;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TripWireHookBlock extends Block {
    public static final MapCodec<TripWireHookBlock> CODEC = simpleCodec(TripWireHookBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty ATTACHED = BlockStateProperties.ATTACHED;
    protected static final int WIRE_DIST_MIN = 1;
    protected static final int WIRE_DIST_MAX = 42;
    private static final int RECHECK_PERIOD = 10;
    protected static final int AABB_OFFSET = 3;
    protected static final VoxelShape NORTH_AABB = Block.box(5.0, 0.0, 10.0, 11.0, 10.0, 16.0);
    protected static final VoxelShape SOUTH_AABB = Block.box(5.0, 0.0, 0.0, 11.0, 10.0, 6.0);
    protected static final VoxelShape WEST_AABB = Block.box(10.0, 0.0, 5.0, 16.0, 10.0, 11.0);
    protected static final VoxelShape EAST_AABB = Block.box(0.0, 0.0, 5.0, 6.0, 10.0, 11.0);

    @Override
    public MapCodec<TripWireHookBlock> codec() {
        return CODEC;
    }

    public TripWireHookBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, Boolean.valueOf(false)).setValue(ATTACHED, Boolean.valueOf(false))
        );
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        switch ((Direction)state.getValue(FACING)) {
            case EAST:
            default:
                return EAST_AABB;
            case WEST:
                return WEST_AABB;
            case SOUTH:
                return SOUTH_AABB;
            case NORTH:
                return NORTH_AABB;
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction direction = state.getValue(FACING);
        BlockPos blockPos = pos.relative(direction.getOpposite());
        BlockState blockState = level.getBlockState(blockPos);
        return direction.getAxis().isHorizontal() && blockState.isFaceSturdy(level, blockPos, direction);
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
        return direction.getOpposite() == state.getValue(FACING) && !state.canSurvive(level, pos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockState = this.defaultBlockState().setValue(POWERED, Boolean.valueOf(false)).setValue(ATTACHED, Boolean.valueOf(false));
        LevelReader level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Direction[] nearestLookingDirections = context.getNearestLookingDirections();

        for (Direction direction : nearestLookingDirections) {
            if (direction.getAxis().isHorizontal()) {
                Direction opposite = direction.getOpposite();
                blockState = blockState.setValue(FACING, opposite);
                if (blockState.canSurvive(level, clickedPos)) {
                    return blockState;
                }
            }
        }

        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        calculateState(level, pos, state, false, false, -1, null);
    }

    public static void calculateState(
        Level level, BlockPos pos, BlockState hookState, boolean attaching, boolean shouldNotifyNeighbours, int searchRange, @Nullable BlockState state
    ) {
        Optional<Direction> optionalValue = hookState.getOptionalValue(FACING);
        if (optionalValue.isPresent()) {
            Direction direction = optionalValue.get();
            boolean flag = hookState.getOptionalValue(ATTACHED).orElse(false);
            boolean flag1 = hookState.getOptionalValue(POWERED).orElse(false); // Paper - diff on change, for event below
            Block block = hookState.getBlock();
            boolean flag2 = !attaching;
            boolean flag3 = false; // Paper - diff on change, for event below
            int i = 0;
            BlockState[] blockStates = new BlockState[42];

            for (int i1 = 1; i1 < 42; i1++) {
                BlockPos blockPos = pos.relative(direction, i1);
                BlockState blockState = level.getBlockState(blockPos);
                if (blockState.is(Blocks.TRIPWIRE_HOOK)) {
                    if (blockState.getValue(FACING) == direction.getOpposite()) {
                        i = i1;
                    }
                    break;
                }

                if (!blockState.is(Blocks.TRIPWIRE) && i1 != searchRange) {
                    blockStates[i1] = null;
                    flag2 = false;
                } else {
                    if (i1 == searchRange) {
                        blockState = MoreObjects.firstNonNull(state, blockState);
                    }

                    boolean flag4 = !blockState.getValue(TripWireBlock.DISARMED);
                    boolean poweredValue = blockState.getValue(TripWireBlock.POWERED);
                    flag3 |= flag4 && poweredValue;
                    blockStates[i1] = blockState;
                    if (i1 == searchRange) {
                        level.scheduleTick(pos, block, 10);
                        flag2 &= flag4;
                    }
                }
            }

            flag2 &= i > 1;
            flag3 &= flag2;
            BlockState blockState1 = block.defaultBlockState().trySetValue(ATTACHED, Boolean.valueOf(flag2)).trySetValue(POWERED, Boolean.valueOf(flag3));
            boolean cancelledEmitterHook = false, cancelledReceiverHook = false; // Paper - Call BlockRedstoneEvent
            boolean wasPowered = flag1, willBePowered = flag3; // Paper - OBFHELPER
            if (i > 0) {
                BlockPos blockPosx = pos.relative(direction, i);
                // Paper start - Call BlockRedstoneEvent
                if (wasPowered != willBePowered) {
                    int newCurrent = willBePowered ? 15 : 0;
                    org.bukkit.event.block.BlockRedstoneEvent event = new org.bukkit.event.block.BlockRedstoneEvent(
                        org.bukkit.craftbukkit.block.CraftBlock.at(level, blockPosx), wasPowered ? 15 : 0, newCurrent
                    );
                    event.callEvent();
                    cancelledReceiverHook = event.getNewCurrent() != newCurrent;
                }
                if (!cancelledReceiverHook) { // always trigger two events even when the first hook current change is cancelled
                // Paper end - Call BlockRedstoneEvent
                Direction opposite = direction.getOpposite();
                level.setBlock(blockPosx, blockState1.setValue(FACING, opposite), 3);
                notifyNeighbors(block, level, blockPosx, opposite);
                emitState(level, blockPosx, flag2, flag3, flag, flag1);
                } // Paper - Call BlockRedstoneEvent
            }
            // Paper start - Call BlockRedstoneEvent
            if (wasPowered != willBePowered) {
                int newCurrent = willBePowered ? 15 : 0;
                org.bukkit.event.block.BlockRedstoneEvent event = new org.bukkit.event.block.BlockRedstoneEvent(
                    org.bukkit.craftbukkit.block.CraftBlock.at(level, pos), wasPowered ? 15 : 0, newCurrent
                );
                event.callEvent();
                cancelledEmitterHook = event.getNewCurrent() != newCurrent;
            }
            // Paper end - Call BlockRedstoneEvent

            if (!cancelledEmitterHook) { // Paper - Call BlockRedstoneEvent
            emitState(level, pos, flag2, flag3, flag, flag1);
            if (!attaching) {
                if (io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.skipTripwireHookPlacementValidation || level.getBlockState(pos).is(Blocks.TRIPWIRE_HOOK)) // Paper - Validate tripwire hook placement before update
                level.setBlock(pos, blockState1.setValue(FACING, direction), 3);
                if (shouldNotifyNeighbours) {
                    notifyNeighbors(block, level, pos, direction);
                }
            }
            } // Paper - Call BlockRedstoneEvent

            if (flag != flag2) {
                for (int i2 = 1; i2 < i; i2++) {
                    BlockPos blockPos1 = pos.relative(direction, i2);
                    BlockState blockState2 = blockStates[i2];
                    if (blockState2 != null) {
                        BlockState blockState3 = level.getBlockState(blockPos1);
                        if (blockState3.is(Blocks.TRIPWIRE) || blockState3.is(Blocks.TRIPWIRE_HOOK)) {
                            level.setBlock(blockPos1, blockState2.trySetValue(ATTACHED, Boolean.valueOf(flag2)), 3);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        calculateState(level, pos, state, false, true, -1, null);
    }

    private static void emitState(Level level, BlockPos pos, boolean attached, boolean powered, boolean wasAttached, boolean wasPowered) {
        if (powered && !wasPowered) {
            level.playSound(null, pos, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 0.4F, 0.6F);
            level.gameEvent(null, GameEvent.BLOCK_ACTIVATE, pos);
        } else if (!powered && wasPowered) {
            level.playSound(null, pos, SoundEvents.TRIPWIRE_CLICK_OFF, SoundSource.BLOCKS, 0.4F, 0.5F);
            level.gameEvent(null, GameEvent.BLOCK_DEACTIVATE, pos);
        } else if (attached && !wasAttached) {
            level.playSound(null, pos, SoundEvents.TRIPWIRE_ATTACH, SoundSource.BLOCKS, 0.4F, 0.7F);
            level.gameEvent(null, GameEvent.BLOCK_ATTACH, pos);
        } else if (!attached && wasAttached) {
            level.playSound(null, pos, SoundEvents.TRIPWIRE_DETACH, SoundSource.BLOCKS, 0.4F, 1.2F / (level.random.nextFloat() * 0.2F + 0.9F));
            level.gameEvent(null, GameEvent.BLOCK_DETACH, pos);
        }
    }

    private static void notifyNeighbors(Block block, Level level, BlockPos pos, Direction direction) {
        Direction opposite = direction.getOpposite();
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, opposite, Direction.UP);
        level.updateNeighborsAt(pos, block, orientation);
        level.updateNeighborsAt(pos.relative(opposite), block, orientation);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && !state.is(newState.getBlock())) {
            boolean attachedValue = state.getValue(ATTACHED);
            boolean poweredValue = state.getValue(POWERED);
            if (attachedValue || poweredValue) {
                calculateState(level, pos, state, true, false, -1, null);
            }

            if (poweredValue) {
                notifyNeighbors(this, level, pos, state.getValue(FACING));
            }

            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    protected int getSignal(BlockState blockState, BlockGetter blockAccess, BlockPos pos, Direction side) {
        return blockState.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState blockState, BlockGetter blockAccess, BlockPos pos, Direction side) {
        if (!blockState.getValue(POWERED)) {
            return 0;
        } else {
            return blockState.getValue(FACING) == side ? 15 : 0;
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
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
        builder.add(FACING, POWERED, ATTACHED);
    }
}
