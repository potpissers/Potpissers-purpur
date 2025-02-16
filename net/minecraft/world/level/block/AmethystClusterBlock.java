package net.minecraft.world.level.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AmethystClusterBlock extends AmethystBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<AmethystClusterBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.FLOAT.fieldOf("height").forGetter(amethystClusterBlock -> amethystClusterBlock.height),
                Codec.FLOAT.fieldOf("aabb_offset").forGetter(amethystClusterBlock -> amethystClusterBlock.aabbOffset),
                propertiesCodec()
            )
            .apply(instance, AmethystClusterBlock::new)
    );
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    private final float height;
    private final float aabbOffset;
    protected final VoxelShape northAabb;
    protected final VoxelShape southAabb;
    protected final VoxelShape eastAabb;
    protected final VoxelShape westAabb;
    protected final VoxelShape upAabb;
    protected final VoxelShape downAabb;

    @Override
    public MapCodec<AmethystClusterBlock> codec() {
        return CODEC;
    }

    public AmethystClusterBlock(float height, float aabbOffset, BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(WATERLOGGED, Boolean.valueOf(false)).setValue(FACING, Direction.UP));
        this.upAabb = Block.box(aabbOffset, 0.0, aabbOffset, 16.0F - aabbOffset, height, 16.0F - aabbOffset);
        this.downAabb = Block.box(aabbOffset, 16.0F - height, aabbOffset, 16.0F - aabbOffset, 16.0, 16.0F - aabbOffset);
        this.northAabb = Block.box(aabbOffset, aabbOffset, 16.0F - height, 16.0F - aabbOffset, 16.0F - aabbOffset, 16.0);
        this.southAabb = Block.box(aabbOffset, aabbOffset, 0.0, 16.0F - aabbOffset, 16.0F - aabbOffset, height);
        this.eastAabb = Block.box(0.0, aabbOffset, aabbOffset, height, 16.0F - aabbOffset, 16.0F - aabbOffset);
        this.westAabb = Block.box(16.0F - height, aabbOffset, aabbOffset, 16.0, 16.0F - aabbOffset, 16.0F - aabbOffset);
        this.height = height;
        this.aabbOffset = aabbOffset;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction direction = state.getValue(FACING);
        switch (direction) {
            case NORTH:
                return this.northAabb;
            case SOUTH:
                return this.southAabb;
            case EAST:
                return this.eastAabb;
            case WEST:
                return this.westAabb;
            case DOWN:
                return this.downAabb;
            case UP:
            default:
                return this.upAabb;
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction direction = state.getValue(FACING);
        BlockPos blockPos = pos.relative(direction.getOpposite());
        return level.getBlockState(blockPos).isFaceSturdy(level, blockPos, direction);
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

        return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelAccessor level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        return this.defaultBlockState()
            .setValue(WATERLOGGED, Boolean.valueOf(level.getFluidState(clickedPos).getType() == Fluids.WATER))
            .setValue(FACING, context.getClickedFace());
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
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED, FACING);
    }
}
