package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MultifaceBlock extends Block implements SimpleWaterloggedBlock {
    public static final MapCodec<MultifaceBlock> CODEC = simpleCodec(MultifaceBlock::new);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final float AABB_OFFSET = 1.0F;
    private static final VoxelShape UP_AABB = Block.box(0.0, 15.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape DOWN_AABB = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);
    private static final VoxelShape WEST_AABB = Block.box(0.0, 0.0, 0.0, 1.0, 16.0, 16.0);
    private static final VoxelShape EAST_AABB = Block.box(15.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape NORTH_AABB = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 1.0);
    private static final VoxelShape SOUTH_AABB = Block.box(0.0, 0.0, 15.0, 16.0, 16.0, 16.0);
    private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final Map<Direction, VoxelShape> SHAPE_BY_DIRECTION = Util.make(Maps.newEnumMap(Direction.class), map -> {
        map.put(Direction.NORTH, NORTH_AABB);
        map.put(Direction.EAST, EAST_AABB);
        map.put(Direction.SOUTH, SOUTH_AABB);
        map.put(Direction.WEST, WEST_AABB);
        map.put(Direction.UP, UP_AABB);
        map.put(Direction.DOWN, DOWN_AABB);
    });
    protected static final Direction[] DIRECTIONS = Direction.values();
    private final ImmutableMap<BlockState, VoxelShape> shapesCache;
    private final boolean canRotate;
    private final boolean canMirrorX;
    private final boolean canMirrorZ;

    @Override
    protected MapCodec<? extends MultifaceBlock> codec() {
        return CODEC;
    }

    public MultifaceBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(getDefaultMultifaceState(this.stateDefinition));
        this.shapesCache = this.getShapeForEachState(MultifaceBlock::calculateMultifaceShape);
        this.canRotate = Direction.Plane.HORIZONTAL.stream().allMatch(this::isFaceSupported);
        this.canMirrorX = Direction.Plane.HORIZONTAL.stream().filter(Direction.Axis.X).filter(this::isFaceSupported).count() % 2L == 0L;
        this.canMirrorZ = Direction.Plane.HORIZONTAL.stream().filter(Direction.Axis.Z).filter(this::isFaceSupported).count() % 2L == 0L;
    }

    public static Set<Direction> availableFaces(BlockState state) {
        if (!(state.getBlock() instanceof MultifaceBlock)) {
            return Set.of();
        } else {
            Set<Direction> set = EnumSet.noneOf(Direction.class);

            for (Direction direction : Direction.values()) {
                if (hasFace(state, direction)) {
                    set.add(direction);
                }
            }

            return set;
        }
    }

    public static Set<Direction> unpack(byte packedDirections) {
        Set<Direction> set = EnumSet.noneOf(Direction.class);

        for (Direction direction : Direction.values()) {
            if ((packedDirections & (byte)(1 << direction.ordinal())) > 0) {
                set.add(direction);
            }
        }

        return set;
    }

    public static byte pack(Collection<Direction> directions) {
        byte b = 0;

        for (Direction direction : directions) {
            b = (byte)(b | 1 << direction.ordinal());
        }

        return b;
    }

    protected boolean isFaceSupported(Direction face) {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        for (Direction direction : DIRECTIONS) {
            if (this.isFaceSupported(direction)) {
                builder.add(getFaceProperty(direction));
            }
        }

        builder.add(WATERLOGGED);
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

        if (!hasAnyFace(state)) {
            return this.getFluidState(state).createLegacyBlock();
        } else {
            return hasFace(state, direction) && !canAttachTo(level, direction, neighborPos, neighborState)
                ? removeFace(state, getFaceProperty(direction))
                : state;
        }
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapesCache.get(state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        boolean flag = false;

        for (Direction direction : DIRECTIONS) {
            if (hasFace(state, direction)) {
                if (!canAttachTo(level, pos, direction)) {
                    return false;
                }

                flag = true;
            }
        }

        return flag;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        return !useContext.getItemInHand().is(this.asItem()) || hasAnyVacantFace(state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState blockState = level.getBlockState(clickedPos);
        return Arrays.stream(context.getNearestLookingDirections())
            .map(lookingDirection -> this.getStateForPlacement(blockState, level, clickedPos, lookingDirection))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    public boolean isValidStateForPlacement(BlockGetter level, BlockState state, BlockPos pos, Direction direction) {
        if (this.isFaceSupported(direction) && (!state.is(this) || !hasFace(state, direction))) {
            BlockPos blockPos = pos.relative(direction);
            return canAttachTo(level, direction, blockPos, level.getBlockState(blockPos));
        } else {
            return false;
        }
    }

    @Nullable
    public BlockState getStateForPlacement(BlockState currentState, BlockGetter level, BlockPos pos, Direction lookingDirection) {
        if (!this.isValidStateForPlacement(level, currentState, pos, lookingDirection)) {
            return null;
        } else {
            BlockState blockState;
            if (currentState.is(this)) {
                blockState = currentState;
            } else if (currentState.getFluidState().isSourceOfType(Fluids.WATER)) {
                blockState = this.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, Boolean.valueOf(true));
            } else {
                blockState = this.defaultBlockState();
            }

            return blockState.setValue(getFaceProperty(lookingDirection), Boolean.valueOf(true));
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return !this.canRotate ? state : this.mapDirections(state, rotation::rotate);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.FRONT_BACK && !this.canMirrorX) {
            return state;
        } else {
            return mirror == Mirror.LEFT_RIGHT && !this.canMirrorZ ? state : this.mapDirections(state, mirror::mirror);
        }
    }

    private BlockState mapDirections(BlockState state, Function<Direction, Direction> directionalFunction) {
        BlockState blockState = state;

        for (Direction direction : DIRECTIONS) {
            if (this.isFaceSupported(direction)) {
                blockState = blockState.setValue(getFaceProperty(directionalFunction.apply(direction)), state.getValue(getFaceProperty(direction)));
            }
        }

        return blockState;
    }

    public static boolean hasFace(BlockState state, Direction direction) {
        BooleanProperty faceProperty = getFaceProperty(direction);
        return state.getValueOrElse(faceProperty, Boolean.valueOf(false));
    }

    public static boolean canAttachTo(BlockGetter level, BlockPos pos, Direction direction) {
        BlockPos blockPos = pos.relative(direction);
        BlockState blockState = level.getBlockState(blockPos);
        return canAttachTo(level, direction, blockPos, blockState);
    }

    public static boolean canAttachTo(BlockGetter level, Direction direction, BlockPos pos, BlockState state) {
        return Block.isFaceFull(state.getBlockSupportShape(level, pos), direction.getOpposite())
            || Block.isFaceFull(state.getCollisionShape(level, pos), direction.getOpposite());
    }

    private static BlockState removeFace(BlockState state, BooleanProperty faceProp) {
        BlockState blockState = state.setValue(faceProp, Boolean.valueOf(false));
        return hasAnyFace(blockState) ? blockState : Blocks.AIR.defaultBlockState();
    }

    public static BooleanProperty getFaceProperty(Direction direction) {
        return PROPERTY_BY_DIRECTION.get(direction);
    }

    private static BlockState getDefaultMultifaceState(StateDefinition<Block, BlockState> stateDefinition) {
        BlockState blockState = stateDefinition.any().setValue(WATERLOGGED, Boolean.valueOf(false));

        for (BooleanProperty booleanProperty : PROPERTY_BY_DIRECTION.values()) {
            blockState = blockState.trySetValue(booleanProperty, Boolean.valueOf(false));
        }

        return blockState;
    }

    private static VoxelShape calculateMultifaceShape(BlockState state) {
        VoxelShape voxelShape = Shapes.empty();

        for (Direction direction : DIRECTIONS) {
            if (hasFace(state, direction)) {
                voxelShape = Shapes.or(voxelShape, SHAPE_BY_DIRECTION.get(direction));
            }
        }

        return voxelShape.isEmpty() ? Shapes.block() : voxelShape;
    }

    protected static boolean hasAnyFace(BlockState state) {
        for (Direction direction : DIRECTIONS) {
            if (hasFace(state, direction)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasAnyVacantFace(BlockState state) {
        for (Direction direction : DIRECTIONS) {
            if (!hasFace(state, direction)) {
                return true;
            }
        }

        return false;
    }
}
