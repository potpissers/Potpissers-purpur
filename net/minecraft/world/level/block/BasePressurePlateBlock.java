package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class BasePressurePlateBlock extends Block {
    protected static final VoxelShape PRESSED_AABB = Block.box(1.0, 0.0, 1.0, 15.0, 0.5, 15.0);
    protected static final VoxelShape AABB = Block.box(1.0, 0.0, 1.0, 15.0, 1.0, 15.0);
    protected static final AABB TOUCH_AABB = new AABB(0.0625, 0.0, 0.0625, 0.9375, 0.25, 0.9375);
    protected final BlockSetType type;

    protected BasePressurePlateBlock(BlockBehaviour.Properties properties, BlockSetType type) {
        super(properties.sound(type.soundType()));
        this.type = type;
    }

    @Override
    protected abstract MapCodec<? extends BasePressurePlateBlock> codec();

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getSignalForState(state) > 0 ? PRESSED_AABB : AABB;
    }

    protected int getPressedTime() {
        return 20;
    }

    @Override
    public boolean isPossibleToRespawnInThis(BlockState state) {
        return true;
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
        return direction == Direction.DOWN && !state.canSurvive(level, pos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockPos = pos.below();
        return canSupportRigidBlock(level, blockPos) || canSupportCenter(level, blockPos, Direction.UP);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int signalForState = this.getSignalForState(state);
        if (signalForState > 0) {
            this.checkPressed(null, level, pos, state, signalForState);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide) {
            int signalForState = this.getSignalForState(state);
            if (signalForState == 0) {
                this.checkPressed(entity, level, pos, state, signalForState);
            }
        }
    }

    private void checkPressed(@Nullable Entity entity, Level level, BlockPos pos, BlockState state, int currentSignal) {
        int signalStrength = this.getSignalStrength(level, pos);
        boolean flag = currentSignal > 0;
        boolean flag1 = signalStrength > 0;
        if (currentSignal != signalStrength) {
            BlockState blockState = this.setSignalForState(state, signalStrength);
            level.setBlock(pos, blockState, 2);
            this.updateNeighbours(level, pos);
            level.setBlocksDirty(pos, state, blockState);
        }

        if (!flag1 && flag) {
            level.playSound(null, pos, this.type.pressurePlateClickOff(), SoundSource.BLOCKS);
            level.gameEvent(entity, GameEvent.BLOCK_DEACTIVATE, pos);
        } else if (flag1 && !flag) {
            level.playSound(null, pos, this.type.pressurePlateClickOn(), SoundSource.BLOCKS);
            level.gameEvent(entity, GameEvent.BLOCK_ACTIVATE, pos);
        }

        if (flag1) {
            level.scheduleTick(new BlockPos(pos), this, this.getPressedTime());
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && !state.is(newState.getBlock())) {
            if (this.getSignalForState(state) > 0) {
                this.updateNeighbours(level, pos);
            }

            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    protected void updateNeighbours(Level level, BlockPos pos) {
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.below(), this);
    }

    @Override
    protected int getSignal(BlockState blockState, BlockGetter blockAccess, BlockPos pos, Direction side) {
        return this.getSignalForState(blockState);
    }

    @Override
    protected int getDirectSignal(BlockState blockState, BlockGetter blockAccess, BlockPos pos, Direction side) {
        return side == Direction.UP ? this.getSignalForState(blockState) : 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    protected static int getEntityCount(Level level, AABB box, Class<? extends Entity> entityClass) {
        return level.getEntitiesOfClass(entityClass, box, EntitySelector.NO_SPECTATORS.and(entity -> !entity.isIgnoringBlockTriggers())).size();
    }

    protected abstract int getSignalStrength(Level level, BlockPos pos);

    protected abstract int getSignalForState(BlockState state);

    protected abstract BlockState setSignalForState(BlockState state, int signal);
}
