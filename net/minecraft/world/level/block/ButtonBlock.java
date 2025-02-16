package net.minecraft.world.level.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ButtonBlock extends FaceAttachedHorizontalDirectionalBlock {
    public static final MapCodec<ButtonBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                BlockSetType.CODEC.fieldOf("block_set_type").forGetter(buttonBlock -> buttonBlock.type),
                Codec.intRange(1, 1024).fieldOf("ticks_to_stay_pressed").forGetter(buttonBlock -> buttonBlock.ticksToStayPressed),
                propertiesCodec()
            )
            .apply(instance, ButtonBlock::new)
    );
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final int PRESSED_DEPTH = 1;
    private static final int UNPRESSED_DEPTH = 2;
    protected static final int HALF_AABB_HEIGHT = 2;
    protected static final int HALF_AABB_WIDTH = 3;
    protected static final VoxelShape CEILING_AABB_X = Block.box(6.0, 14.0, 5.0, 10.0, 16.0, 11.0);
    protected static final VoxelShape CEILING_AABB_Z = Block.box(5.0, 14.0, 6.0, 11.0, 16.0, 10.0);
    protected static final VoxelShape FLOOR_AABB_X = Block.box(6.0, 0.0, 5.0, 10.0, 2.0, 11.0);
    protected static final VoxelShape FLOOR_AABB_Z = Block.box(5.0, 0.0, 6.0, 11.0, 2.0, 10.0);
    protected static final VoxelShape NORTH_AABB = Block.box(5.0, 6.0, 14.0, 11.0, 10.0, 16.0);
    protected static final VoxelShape SOUTH_AABB = Block.box(5.0, 6.0, 0.0, 11.0, 10.0, 2.0);
    protected static final VoxelShape WEST_AABB = Block.box(14.0, 6.0, 5.0, 16.0, 10.0, 11.0);
    protected static final VoxelShape EAST_AABB = Block.box(0.0, 6.0, 5.0, 2.0, 10.0, 11.0);
    protected static final VoxelShape PRESSED_CEILING_AABB_X = Block.box(6.0, 15.0, 5.0, 10.0, 16.0, 11.0);
    protected static final VoxelShape PRESSED_CEILING_AABB_Z = Block.box(5.0, 15.0, 6.0, 11.0, 16.0, 10.0);
    protected static final VoxelShape PRESSED_FLOOR_AABB_X = Block.box(6.0, 0.0, 5.0, 10.0, 1.0, 11.0);
    protected static final VoxelShape PRESSED_FLOOR_AABB_Z = Block.box(5.0, 0.0, 6.0, 11.0, 1.0, 10.0);
    protected static final VoxelShape PRESSED_NORTH_AABB = Block.box(5.0, 6.0, 15.0, 11.0, 10.0, 16.0);
    protected static final VoxelShape PRESSED_SOUTH_AABB = Block.box(5.0, 6.0, 0.0, 11.0, 10.0, 1.0);
    protected static final VoxelShape PRESSED_WEST_AABB = Block.box(15.0, 6.0, 5.0, 16.0, 10.0, 11.0);
    protected static final VoxelShape PRESSED_EAST_AABB = Block.box(0.0, 6.0, 5.0, 1.0, 10.0, 11.0);
    private final BlockSetType type;
    private final int ticksToStayPressed;

    @Override
    public MapCodec<ButtonBlock> codec() {
        return CODEC;
    }

    protected ButtonBlock(BlockSetType type, int ticksToStayPressed, BlockBehaviour.Properties properties) {
        super(properties.sound(type.soundType()));
        this.type = type;
        this.registerDefaultState(
            this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, Boolean.valueOf(false)).setValue(FACE, AttachFace.WALL)
        );
        this.ticksToStayPressed = ticksToStayPressed;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction direction = state.getValue(FACING);
        boolean poweredValue = state.getValue(POWERED);
        switch ((AttachFace)state.getValue(FACE)) {
            case FLOOR:
                if (direction.getAxis() == Direction.Axis.X) {
                    return poweredValue ? PRESSED_FLOOR_AABB_X : FLOOR_AABB_X;
                }

                return poweredValue ? PRESSED_FLOOR_AABB_Z : FLOOR_AABB_Z;
            case WALL:
                return switch (direction) {
                    case EAST -> poweredValue ? PRESSED_EAST_AABB : EAST_AABB;
                    case WEST -> poweredValue ? PRESSED_WEST_AABB : WEST_AABB;
                    case SOUTH -> poweredValue ? PRESSED_SOUTH_AABB : SOUTH_AABB;
                    case NORTH, UP, DOWN -> poweredValue ? PRESSED_NORTH_AABB : NORTH_AABB;
                };
            case CEILING:
            default:
                if (direction.getAxis() == Direction.Axis.X) {
                    return poweredValue ? PRESSED_CEILING_AABB_X : CEILING_AABB_X;
                } else {
                    return poweredValue ? PRESSED_CEILING_AABB_Z : CEILING_AABB_Z;
                }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (state.getValue(POWERED)) {
            return InteractionResult.CONSUME;
        } else {
            this.press(state, level, pos, player);
            return InteractionResult.SUCCESS;
        }
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
        if (explosion.canTriggerBlocks() && !state.getValue(POWERED)) {
            this.press(state, level, pos, null);
        }

        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
    }

    public void press(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        level.setBlock(pos, state.setValue(POWERED, Boolean.valueOf(true)), 3);
        this.updateNeighbours(state, level, pos);
        level.scheduleTick(pos, this, this.ticksToStayPressed);
        this.playSound(player, level, pos, true);
        level.gameEvent(player, GameEvent.BLOCK_ACTIVATE, pos);
    }

    protected void playSound(@Nullable Player player, LevelAccessor level, BlockPos pos, boolean hitByArrow) {
        level.playSound(hitByArrow ? player : null, pos, this.getSound(hitByArrow), SoundSource.BLOCKS);
    }

    protected SoundEvent getSound(boolean isOn) {
        return isOn ? this.type.buttonClickOn() : this.type.buttonClickOff();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && !state.is(newState.getBlock())) {
            if (state.getValue(POWERED)) {
                this.updateNeighbours(state, level, pos);
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
        return blockState.getValue(POWERED) && getConnectedDirection(blockState) == side ? 15 : 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            this.checkPressed(state, level, pos);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && this.type.canButtonBeActivatedByArrows() && !state.getValue(POWERED)) {
            this.checkPressed(state, level, pos);
        }
    }

    protected void checkPressed(BlockState state, Level level, BlockPos pos) {
        AbstractArrow abstractArrow = this.type.canButtonBeActivatedByArrows()
            ? level.getEntitiesOfClass(AbstractArrow.class, state.getShape(level, pos).bounds().move(pos)).stream().findFirst().orElse(null)
            : null;
        boolean flag = abstractArrow != null;
        boolean poweredValue = state.getValue(POWERED);
        if (flag != poweredValue) {
            level.setBlock(pos, state.setValue(POWERED, Boolean.valueOf(flag)), 3);
            this.updateNeighbours(state, level, pos);
            this.playSound(null, level, pos, flag);
            level.gameEvent(abstractArrow, flag ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos);
        }

        if (flag) {
            level.scheduleTick(new BlockPos(pos), this, this.ticksToStayPressed);
        }
    }

    private void updateNeighbours(BlockState state, Level level, BlockPos pos) {
        Direction opposite = getConnectedDirection(state).getOpposite();
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(
            level, opposite, opposite.getAxis().isHorizontal() ? Direction.UP : state.getValue(FACING)
        );
        level.updateNeighborsAt(pos, this, orientation);
        level.updateNeighborsAt(pos.relative(opposite), this, orientation);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, FACE);
    }
}
