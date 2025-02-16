package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WallSignBlock extends SignBlock {
    public static final MapCodec<WallSignBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(WoodType.CODEC.fieldOf("wood_type").forGetter(SignBlock::type), propertiesCodec()).apply(instance, WallSignBlock::new)
    );
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    protected static final float AABB_THICKNESS = 2.0F;
    protected static final float AABB_BOTTOM = 4.5F;
    protected static final float AABB_TOP = 12.5F;
    private static final Map<Direction, VoxelShape> AABBS = Maps.newEnumMap(
        ImmutableMap.of(
            Direction.NORTH,
            Block.box(0.0, 4.5, 14.0, 16.0, 12.5, 16.0),
            Direction.SOUTH,
            Block.box(0.0, 4.5, 0.0, 16.0, 12.5, 2.0),
            Direction.EAST,
            Block.box(0.0, 4.5, 0.0, 2.0, 12.5, 16.0),
            Direction.WEST,
            Block.box(14.0, 4.5, 0.0, 16.0, 12.5, 16.0)
        )
    );

    @Override
    public MapCodec<WallSignBlock> codec() {
        return CODEC;
    }

    public WallSignBlock(WoodType type, BlockBehaviour.Properties properties) {
        super(type, properties.sound(type.soundType()));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, Boolean.valueOf(false)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AABBS.get(state.getValue(FACING));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())).isSolid();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockState = this.defaultBlockState();
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        LevelReader level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Direction[] nearestLookingDirections = context.getNearestLookingDirections();

        for (Direction direction : nearestLookingDirections) {
            if (direction.getAxis().isHorizontal()) {
                Direction opposite = direction.getOpposite();
                blockState = blockState.setValue(FACING, opposite);
                if (blockState.canSurvive(level, clickedPos)) {
                    return blockState.setValue(WATERLOGGED, Boolean.valueOf(fluidState.getType() == Fluids.WATER));
                }
            }
        }

        return null;
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

    @Override
    public float getYRotationDegrees(BlockState state) {
        return state.getValue(FACING).toYRot();
    }

    @Override
    public Vec3 getSignHitboxCenterPosition(BlockState state) {
        VoxelShape voxelShape = AABBS.get(state.getValue(FACING));
        return voxelShape.bounds().getCenter();
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
        builder.add(FACING, WATERLOGGED);
    }
}
