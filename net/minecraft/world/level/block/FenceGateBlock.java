package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FenceGateBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<FenceGateBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(WoodType.CODEC.fieldOf("wood_type").forGetter(fenceGateBlock -> fenceGateBlock.type), propertiesCodec())
            .apply(instance, FenceGateBlock::new)
    );
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty IN_WALL = BlockStateProperties.IN_WALL;
    protected static final VoxelShape Z_SHAPE = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    protected static final VoxelShape X_SHAPE = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);
    protected static final VoxelShape Z_SHAPE_LOW = Block.box(0.0, 0.0, 6.0, 16.0, 13.0, 10.0);
    protected static final VoxelShape X_SHAPE_LOW = Block.box(6.0, 0.0, 0.0, 10.0, 13.0, 16.0);
    protected static final VoxelShape Z_COLLISION_SHAPE = Block.box(0.0, 0.0, 6.0, 16.0, 24.0, 10.0);
    protected static final VoxelShape X_COLLISION_SHAPE = Block.box(6.0, 0.0, 0.0, 10.0, 24.0, 16.0);
    protected static final VoxelShape Z_SUPPORT_SHAPE = Block.box(0.0, 5.0, 6.0, 16.0, 24.0, 10.0);
    protected static final VoxelShape X_SUPPORT_SHAPE = Block.box(6.0, 5.0, 0.0, 10.0, 24.0, 16.0);
    protected static final VoxelShape Z_OCCLUSION_SHAPE = Shapes.or(Block.box(0.0, 5.0, 7.0, 2.0, 16.0, 9.0), Block.box(14.0, 5.0, 7.0, 16.0, 16.0, 9.0));
    protected static final VoxelShape X_OCCLUSION_SHAPE = Shapes.or(Block.box(7.0, 5.0, 0.0, 9.0, 16.0, 2.0), Block.box(7.0, 5.0, 14.0, 9.0, 16.0, 16.0));
    protected static final VoxelShape Z_OCCLUSION_SHAPE_LOW = Shapes.or(Block.box(0.0, 2.0, 7.0, 2.0, 13.0, 9.0), Block.box(14.0, 2.0, 7.0, 16.0, 13.0, 9.0));
    protected static final VoxelShape X_OCCLUSION_SHAPE_LOW = Shapes.or(Block.box(7.0, 2.0, 0.0, 9.0, 13.0, 2.0), Block.box(7.0, 2.0, 14.0, 9.0, 13.0, 16.0));
    private final WoodType type;

    @Override
    public MapCodec<FenceGateBlock> codec() {
        return CODEC;
    }

    public FenceGateBlock(WoodType type, BlockBehaviour.Properties properties) {
        super(properties.sound(type.soundType()));
        this.type = type;
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(OPEN, Boolean.valueOf(false))
                .setValue(POWERED, Boolean.valueOf(false))
                .setValue(IN_WALL, Boolean.valueOf(false))
        );
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(IN_WALL)) {
            return state.getValue(FACING).getAxis() == Direction.Axis.X ? X_SHAPE_LOW : Z_SHAPE_LOW;
        } else {
            return state.getValue(FACING).getAxis() == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
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
        Direction.Axis axis = direction.getAxis();
        if (state.getValue(FACING).getClockWise().getAxis() != axis) {
            return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        } else {
            boolean flag = this.isWall(neighborState) || this.isWall(level.getBlockState(pos.relative(direction.getOpposite())));
            return state.setValue(IN_WALL, Boolean.valueOf(flag));
        }
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getValue(OPEN)) {
            return Shapes.empty();
        } else {
            return state.getValue(FACING).getAxis() == Direction.Axis.Z ? Z_SUPPORT_SHAPE : X_SUPPORT_SHAPE;
        }
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(OPEN)) {
            return Shapes.empty();
        } else {
            return state.getValue(FACING).getAxis() == Direction.Axis.Z ? Z_COLLISION_SHAPE : X_COLLISION_SHAPE;
        }
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        if (state.getValue(IN_WALL)) {
            return state.getValue(FACING).getAxis() == Direction.Axis.X ? X_OCCLUSION_SHAPE_LOW : Z_OCCLUSION_SHAPE_LOW;
        } else {
            return state.getValue(FACING).getAxis() == Direction.Axis.X ? X_OCCLUSION_SHAPE : Z_OCCLUSION_SHAPE;
        }
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        switch (pathComputationType) {
            case LAND:
                return state.getValue(OPEN);
            case WATER:
                return false;
            case AIR:
                return state.getValue(OPEN);
            default:
                return false;
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        boolean hasNeighborSignal = level.hasNeighborSignal(clickedPos);
        Direction horizontalDirection = context.getHorizontalDirection();
        Direction.Axis axis = horizontalDirection.getAxis();
        boolean flag = axis == Direction.Axis.Z && (this.isWall(level.getBlockState(clickedPos.west())) || this.isWall(level.getBlockState(clickedPos.east())))
            || axis == Direction.Axis.X && (this.isWall(level.getBlockState(clickedPos.north())) || this.isWall(level.getBlockState(clickedPos.south())));
        return this.defaultBlockState()
            .setValue(FACING, horizontalDirection)
            .setValue(OPEN, Boolean.valueOf(hasNeighborSignal))
            .setValue(POWERED, Boolean.valueOf(hasNeighborSignal))
            .setValue(IN_WALL, Boolean.valueOf(flag));
    }

    private boolean isWall(BlockState state) {
        return state.is(BlockTags.WALLS);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (state.getValue(OPEN)) {
            state = state.setValue(OPEN, Boolean.valueOf(false));
            level.setBlock(pos, state, 10);
        } else {
            Direction direction = player.getDirection();
            if (state.getValue(FACING) == direction.getOpposite()) {
                state = state.setValue(FACING, direction);
            }

            state = state.setValue(OPEN, Boolean.valueOf(true));
            level.setBlock(pos, state, 10);
        }

        boolean openValue = state.getValue(OPEN);
        level.playSound(
            player,
            pos,
            openValue ? this.type.fenceGateOpen() : this.type.fenceGateClose(),
            SoundSource.BLOCKS,
            1.0F,
            level.getRandom().nextFloat() * 0.1F + 0.9F
        );
        level.gameEvent(player, openValue ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
        if (explosion.canTriggerBlocks() && !state.getValue(POWERED)) {
            boolean openValue = state.getValue(OPEN);
            level.setBlockAndUpdate(pos, state.setValue(OPEN, Boolean.valueOf(!openValue)));
            level.playSound(
                null,
                pos,
                openValue ? this.type.fenceGateClose() : this.type.fenceGateOpen(),
                SoundSource.BLOCKS,
                1.0F,
                level.getRandom().nextFloat() * 0.1F + 0.9F
            );
            level.gameEvent(openValue ? GameEvent.BLOCK_CLOSE : GameEvent.BLOCK_OPEN, pos, GameEvent.Context.of(state));
        }

        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide) {
            boolean hasNeighborSignal = level.hasNeighborSignal(pos);
            // CraftBukkit start
            boolean oldPowered = state.getValue(FenceGateBlock.POWERED);
            if (oldPowered != hasNeighborSignal) {
                int newPower = hasNeighborSignal ? 15 : 0;
                int oldPower = oldPowered ? 15 : 0;
                org.bukkit.block.Block bukkitBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
                org.bukkit.event.block.BlockRedstoneEvent eventRedstone = new org.bukkit.event.block.BlockRedstoneEvent(bukkitBlock, oldPower, newPower);
                level.getCraftServer().getPluginManager().callEvent(eventRedstone);
                hasNeighborSignal = eventRedstone.getNewCurrent() > 0;
            }
            // CraftBukkit end
            if (state.getValue(POWERED) != hasNeighborSignal) {
                level.setBlock(pos, state.setValue(POWERED, Boolean.valueOf(hasNeighborSignal)).setValue(OPEN, Boolean.valueOf(hasNeighborSignal)), 2);
                if (state.getValue(OPEN) != hasNeighborSignal) {
                    level.playSound(
                        null,
                        pos,
                        hasNeighborSignal ? this.type.fenceGateOpen() : this.type.fenceGateClose(),
                        SoundSource.BLOCKS,
                        1.0F,
                        level.getRandom().nextFloat() * 0.1F + 0.9F
                    );
                    level.gameEvent(null, hasNeighborSignal ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
                }
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, POWERED, IN_WALL);
    }

    public static boolean connectsToDirection(BlockState state, Direction direction) {
        return state.getValue(FACING).getAxis() == direction.getClockWise().getAxis();
    }
}
