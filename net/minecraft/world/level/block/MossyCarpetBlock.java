package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MossyCarpetBlock extends Block implements BonemealableBlock {
    public static final MapCodec<MossyCarpetBlock> CODEC = simpleCodec(MossyCarpetBlock::new);
    public static final BooleanProperty BASE = BlockStateProperties.BOTTOM;
    private static final EnumProperty<WallSide> NORTH = BlockStateProperties.NORTH_WALL;
    private static final EnumProperty<WallSide> EAST = BlockStateProperties.EAST_WALL;
    private static final EnumProperty<WallSide> SOUTH = BlockStateProperties.SOUTH_WALL;
    private static final EnumProperty<WallSide> WEST = BlockStateProperties.WEST_WALL;
    private static final Map<Direction, EnumProperty<WallSide>> PROPERTY_BY_DIRECTION = ImmutableMap.copyOf(
        Util.make(Maps.newEnumMap(Direction.class), map -> {
            map.put(Direction.NORTH, NORTH);
            map.put(Direction.EAST, EAST);
            map.put(Direction.SOUTH, SOUTH);
            map.put(Direction.WEST, WEST);
        })
    );
    private static final float AABB_OFFSET = 1.0F;
    private static final VoxelShape DOWN_AABB = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);
    private static final VoxelShape WEST_AABB = Block.box(0.0, 0.0, 0.0, 1.0, 16.0, 16.0);
    private static final VoxelShape EAST_AABB = Block.box(15.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape NORTH_AABB = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 1.0);
    private static final VoxelShape SOUTH_AABB = Block.box(0.0, 0.0, 15.0, 16.0, 16.0, 16.0);
    private static final int SHORT_HEIGHT = 10;
    private static final VoxelShape WEST_SHORT_AABB = Block.box(0.0, 0.0, 0.0, 1.0, 10.0, 16.0);
    private static final VoxelShape EAST_SHORT_AABB = Block.box(15.0, 0.0, 0.0, 16.0, 10.0, 16.0);
    private static final VoxelShape NORTH_SHORT_AABB = Block.box(0.0, 0.0, 0.0, 16.0, 10.0, 1.0);
    private static final VoxelShape SOUTH_SHORT_AABB = Block.box(0.0, 0.0, 15.0, 16.0, 10.0, 16.0);
    private final Map<BlockState, VoxelShape> shapesCache;

    @Override
    public MapCodec<MossyCarpetBlock> codec() {
        return CODEC;
    }

    public MossyCarpetBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(BASE, Boolean.valueOf(true))
                .setValue(NORTH, WallSide.NONE)
                .setValue(EAST, WallSide.NONE)
                .setValue(SOUTH, WallSide.NONE)
                .setValue(WEST, WallSide.NONE)
        );
        this.shapesCache = ImmutableMap.copyOf(
            this.stateDefinition.getPossibleStates().stream().collect(Collectors.toMap(Function.identity(), MossyCarpetBlock::calculateShape))
        );
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return Shapes.empty();
    }

    private static VoxelShape calculateShape(BlockState state) {
        VoxelShape voxelShape = Shapes.empty();
        if (state.getValue(BASE)) {
            voxelShape = DOWN_AABB;
        }
        voxelShape = switch ((WallSide)state.getValue(NORTH)) {
            case NONE -> voxelShape;
            case LOW -> Shapes.or(voxelShape, NORTH_SHORT_AABB);
            case TALL -> Shapes.or(voxelShape, NORTH_AABB);
        };

        voxelShape = switch ((WallSide)state.getValue(SOUTH)) {
            case NONE -> voxelShape;
            case LOW -> Shapes.or(voxelShape, SOUTH_SHORT_AABB);
            case TALL -> Shapes.or(voxelShape, SOUTH_AABB);
        };

        voxelShape = switch ((WallSide)state.getValue(EAST)) {
            case NONE -> voxelShape;
            case LOW -> Shapes.or(voxelShape, EAST_SHORT_AABB);
            case TALL -> Shapes.or(voxelShape, EAST_AABB);
        };

        voxelShape = switch ((WallSide)state.getValue(WEST)) {
            case NONE -> voxelShape;
            case LOW -> Shapes.or(voxelShape, WEST_SHORT_AABB);
            case TALL -> Shapes.or(voxelShape, WEST_AABB);
        };
        return voxelShape.isEmpty() ? Shapes.block() : voxelShape;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapesCache.get(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(BASE) ? DOWN_AABB : Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos.below());
        return state.getValue(BASE) ? !blockState.isAir() : blockState.is(this) && blockState.getValue(BASE);
    }

    private static boolean hasFaces(BlockState state) {
        if (state.getValue(BASE)) {
            return true;
        } else {
            for (EnumProperty<WallSide> enumProperty : PROPERTY_BY_DIRECTION.values()) {
                if (state.getValue(enumProperty) != WallSide.NONE) {
                    return true;
                }
            }

            return false;
        }
    }

    private static boolean canSupportAtFace(BlockGetter level, BlockPos pos, Direction direction) {
        return direction != Direction.UP && MultifaceBlock.canAttachTo(level, pos, direction);
    }

    private static BlockState getUpdatedState(BlockState state, BlockGetter level, BlockPos pos, boolean tip) {
        BlockState blockState = null;
        BlockState blockState1 = null;
        tip |= state.getValue(BASE);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            EnumProperty<WallSide> propertyForFace = getPropertyForFace(direction);
            WallSide wallSide = canSupportAtFace(level, pos, direction) ? (tip ? WallSide.LOW : state.getValue(propertyForFace)) : WallSide.NONE;
            if (wallSide == WallSide.LOW) {
                if (blockState == null) {
                    blockState = level.getBlockState(pos.above());
                }

                if (blockState.is(Blocks.PALE_MOSS_CARPET) && blockState.getValue(propertyForFace) != WallSide.NONE && !blockState.getValue(BASE)) {
                    wallSide = WallSide.TALL;
                }

                if (!state.getValue(BASE)) {
                    if (blockState1 == null) {
                        blockState1 = level.getBlockState(pos.below());
                    }

                    if (blockState1.is(Blocks.PALE_MOSS_CARPET) && blockState1.getValue(propertyForFace) == WallSide.NONE) {
                        wallSide = WallSide.NONE;
                    }
                }
            }

            state = state.setValue(propertyForFace, wallSide);
        }

        return state;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return getUpdatedState(this.defaultBlockState(), context.getLevel(), context.getClickedPos(), true);
    }

    public static void placeAt(LevelAccessor level, BlockPos pos, RandomSource random, int flags) {
        BlockState blockState = Blocks.PALE_MOSS_CARPET.defaultBlockState();
        BlockState updatedState = getUpdatedState(blockState, level, pos, true);
        level.setBlock(pos, updatedState, 3);
        BlockState blockState1 = createTopperWithSideChance(level, pos, random::nextBoolean);
        if (!blockState1.isAir()) {
            level.setBlock(pos.above(), blockState1, flags);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide) {
            RandomSource random = level.getRandom();
            BlockState blockState = createTopperWithSideChance(level, pos, random::nextBoolean);
            if (!blockState.isAir()) {
                level.setBlock(pos.above(), blockState, 3);
            }
        }
    }

    private static BlockState createTopperWithSideChance(BlockGetter level, BlockPos pos, BooleanSupplier placeSide) {
        BlockPos blockPos = pos.above();
        BlockState blockState = level.getBlockState(blockPos);
        boolean isPaleMossCarpet = blockState.is(Blocks.PALE_MOSS_CARPET);
        if ((!isPaleMossCarpet || !blockState.getValue(BASE)) && (isPaleMossCarpet || blockState.canBeReplaced())) {
            BlockState blockState1 = Blocks.PALE_MOSS_CARPET.defaultBlockState().setValue(BASE, Boolean.valueOf(false));
            BlockState updatedState = getUpdatedState(blockState1, level, pos.above(), true);

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                EnumProperty<WallSide> propertyForFace = getPropertyForFace(direction);
                if (updatedState.getValue(propertyForFace) != WallSide.NONE && !placeSide.getAsBoolean()) {
                    updatedState = updatedState.setValue(propertyForFace, WallSide.NONE);
                }
            }

            return hasFaces(updatedState) && updatedState != blockState ? updatedState : Blocks.AIR.defaultBlockState();
        } else {
            return Blocks.AIR.defaultBlockState();
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
        if (!state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            BlockState updatedState = getUpdatedState(state, level, pos, false);
            return !hasFaces(updatedState) ? Blocks.AIR.defaultBlockState() : updatedState;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BASE, NORTH, EAST, SOUTH, WEST);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_180 -> (BlockState)state.setValue(NORTH, state.getValue(SOUTH))
                .setValue(EAST, state.getValue(WEST))
                .setValue(SOUTH, state.getValue(NORTH))
                .setValue(WEST, state.getValue(EAST));
            case COUNTERCLOCKWISE_90 -> (BlockState)state.setValue(NORTH, state.getValue(EAST))
                .setValue(EAST, state.getValue(SOUTH))
                .setValue(SOUTH, state.getValue(WEST))
                .setValue(WEST, state.getValue(NORTH));
            case CLOCKWISE_90 -> (BlockState)state.setValue(NORTH, state.getValue(WEST))
                .setValue(EAST, state.getValue(NORTH))
                .setValue(SOUTH, state.getValue(EAST))
                .setValue(WEST, state.getValue(SOUTH));
            default -> state;
        };
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> (BlockState)state.setValue(NORTH, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK -> (BlockState)state.setValue(EAST, state.getValue(WEST)).setValue(WEST, state.getValue(EAST));
            default -> super.mirror(state, mirror);
        };
    }

    @Nullable
    public static EnumProperty<WallSide> getPropertyForFace(Direction direction) {
        return PROPERTY_BY_DIRECTION.get(direction);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(BASE) && !createTopperWithSideChance(level, pos, () -> true).isAir();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockState blockState = createTopperWithSideChance(level, pos, () -> true);
        if (!blockState.isAir()) {
            level.setBlock(pos.above(), blockState, 3);
        }
    }
}
