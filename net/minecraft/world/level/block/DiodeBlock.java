package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;

public abstract class DiodeBlock extends HorizontalDirectionalBlock {
    protected static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    protected DiodeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected abstract MapCodec<? extends DiodeBlock> codec();

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockPos = pos.below();
        return this.canSurviveOn(level, blockPos, level.getBlockState(blockPos));
    }

    protected boolean canSurviveOn(LevelReader level, BlockPos pos, BlockState state) {
        return state.isFaceSturdy(level, pos, Direction.UP, SupportType.RIGID);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!this.isLocked(level, pos, state)) {
            boolean poweredValue = state.getValue(POWERED);
            boolean shouldTurnOn = this.shouldTurnOn(level, pos, state);
            if (poweredValue && !shouldTurnOn) {
                level.setBlock(pos, state.setValue(POWERED, Boolean.valueOf(false)), 2);
            } else if (!poweredValue) {
                level.setBlock(pos, state.setValue(POWERED, Boolean.valueOf(true)), 2);
                if (!shouldTurnOn) {
                    level.scheduleTick(pos, this, this.getDelay(state), TickPriority.VERY_HIGH);
                }
            }
        }
    }

    @Override
    protected int getDirectSignal(BlockState blockState, BlockGetter blockAccess, BlockPos pos, Direction side) {
        return blockState.getSignal(blockAccess, pos, side);
    }

    @Override
    protected int getSignal(BlockState blockState, BlockGetter blockAccess, BlockPos pos, Direction side) {
        if (!blockState.getValue(POWERED)) {
            return 0;
        } else {
            return blockState.getValue(FACING) == side ? this.getOutputSignal(blockAccess, pos, blockState) : 0;
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (state.canSurvive(level, pos)) {
            this.checkTickOnNeighbor(level, pos, state);
        } else {
            BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            dropResources(state, level, pos, blockEntity);
            level.removeBlock(pos, false);

            for (Direction direction : Direction.values()) {
                level.updateNeighborsAt(pos.relative(direction), this);
            }
        }
    }

    protected void checkTickOnNeighbor(Level level, BlockPos pos, BlockState state) {
        if (!this.isLocked(level, pos, state)) {
            boolean poweredValue = state.getValue(POWERED);
            boolean shouldTurnOn = this.shouldTurnOn(level, pos, state);
            if (poweredValue != shouldTurnOn && !level.getBlockTicks().willTickThisTick(pos, this)) {
                TickPriority tickPriority = TickPriority.HIGH;
                if (this.shouldPrioritize(level, pos, state)) {
                    tickPriority = TickPriority.EXTREMELY_HIGH;
                } else if (poweredValue) {
                    tickPriority = TickPriority.VERY_HIGH;
                }

                level.scheduleTick(pos, this, this.getDelay(state), tickPriority);
            }
        }
    }

    public boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
        return false;
    }

    protected boolean shouldTurnOn(Level level, BlockPos pos, BlockState state) {
        return this.getInputSignal(level, pos, state) > 0;
    }

    protected int getInputSignal(Level level, BlockPos pos, BlockState state) {
        Direction direction = state.getValue(FACING);
        BlockPos blockPos = pos.relative(direction);
        int signal = level.getSignal(blockPos, direction);
        if (signal >= 15) {
            return signal;
        } else {
            BlockState blockState = level.getBlockState(blockPos);
            return Math.max(signal, blockState.is(Blocks.REDSTONE_WIRE) ? blockState.getValue(RedStoneWireBlock.POWER) : 0);
        }
    }

    protected int getAlternateSignal(SignalGetter level, BlockPos pos, BlockState state) {
        Direction direction = state.getValue(FACING);
        Direction clockWise = direction.getClockWise();
        Direction counterClockWise = direction.getCounterClockWise();
        boolean flag = this.sideInputDiodesOnly();
        return Math.max(
            level.getControlInputSignal(pos.relative(clockWise), clockWise, flag),
            level.getControlInputSignal(pos.relative(counterClockWise), counterClockWise, flag)
        );
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (this.shouldTurnOn(level, pos, state)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        this.updateNeighborsInFront(level, pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && !state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, isMoving);
            this.updateNeighborsInFront(level, pos, state);
        }
    }

    protected void updateNeighborsInFront(Level level, BlockPos pos, BlockState state) {
        Direction direction = state.getValue(FACING);
        BlockPos blockPos = pos.relative(direction.getOpposite());
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, direction.getOpposite(), Direction.UP);
        level.neighborChanged(blockPos, this, orientation);
        level.updateNeighborsAtExceptFromFacing(blockPos, this, direction, orientation);
    }

    protected boolean sideInputDiodesOnly() {
        return false;
    }

    protected int getOutputSignal(BlockGetter level, BlockPos pos, BlockState state) {
        return 15;
    }

    public static boolean isDiode(BlockState state) {
        return state.getBlock() instanceof DiodeBlock;
    }

    public boolean shouldPrioritize(BlockGetter level, BlockPos pos, BlockState state) {
        Direction opposite = state.getValue(FACING).getOpposite();
        BlockState blockState = level.getBlockState(pos.relative(opposite));
        return isDiode(blockState) && blockState.getValue(FACING) != opposite;
    }

    protected abstract int getDelay(BlockState state);
}
