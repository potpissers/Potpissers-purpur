package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class CrossCollisionBlock extends Block implements SimpleWaterloggedBlock {
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    protected static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION
        .entrySet()
        .stream()
        .filter(direction -> direction.getKey().getAxis().isHorizontal())
        .collect(Util.toMap());
    protected final VoxelShape[] collisionShapeByIndex;
    protected final VoxelShape[] shapeByIndex;
    private final Object2IntMap<BlockState> stateToIndex = new Object2IntOpenHashMap<>();

    protected CrossCollisionBlock(
        float nodeWidth, float extensionWidth, float nodeHeight, float extensionHeight, float collisionHeight, BlockBehaviour.Properties properties
    ) {
        super(properties);
        this.collisionShapeByIndex = this.makeShapes(nodeWidth, extensionWidth, collisionHeight, 0.0F, collisionHeight);
        this.shapeByIndex = this.makeShapes(nodeWidth, extensionWidth, nodeHeight, 0.0F, extensionHeight);

        for (BlockState blockState : this.stateDefinition.getPossibleStates()) {
            this.getAABBIndex(blockState);
        }
    }

    @Override
    protected abstract MapCodec<? extends CrossCollisionBlock> codec();

    protected VoxelShape[] makeShapes(float nodeWidth, float extensionWidth, float nodeHeight, float extensionBottom, float extensionHeight) {
        float f = 8.0F - nodeWidth;
        float f1 = 8.0F + nodeWidth;
        float f2 = 8.0F - extensionWidth;
        float f3 = 8.0F + extensionWidth;
        VoxelShape voxelShape = Block.box(f, 0.0, f, f1, nodeHeight, f1);
        VoxelShape voxelShape1 = Block.box(f2, extensionBottom, 0.0, f3, extensionHeight, f3);
        VoxelShape voxelShape2 = Block.box(f2, extensionBottom, f2, f3, extensionHeight, 16.0);
        VoxelShape voxelShape3 = Block.box(0.0, extensionBottom, f2, f3, extensionHeight, f3);
        VoxelShape voxelShape4 = Block.box(f2, extensionBottom, f2, 16.0, extensionHeight, f3);
        VoxelShape voxelShape5 = Shapes.or(voxelShape1, voxelShape4);
        VoxelShape voxelShape6 = Shapes.or(voxelShape2, voxelShape3);
        VoxelShape[] voxelShapes = new VoxelShape[]{
            Shapes.empty(),
            voxelShape2,
            voxelShape3,
            voxelShape6,
            voxelShape1,
            Shapes.or(voxelShape2, voxelShape1),
            Shapes.or(voxelShape3, voxelShape1),
            Shapes.or(voxelShape6, voxelShape1),
            voxelShape4,
            Shapes.or(voxelShape2, voxelShape4),
            Shapes.or(voxelShape3, voxelShape4),
            Shapes.or(voxelShape6, voxelShape4),
            voxelShape5,
            Shapes.or(voxelShape2, voxelShape5),
            Shapes.or(voxelShape3, voxelShape5),
            Shapes.or(voxelShape6, voxelShape5)
        };

        for (int i = 0; i < 16; i++) {
            voxelShapes[i] = Shapes.or(voxelShape, voxelShapes[i]);
        }

        return voxelShapes;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return !state.getValue(WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapeByIndex[this.getAABBIndex(state)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.collisionShapeByIndex[this.getAABBIndex(state)];
    }

    private static int indexFor(Direction facing) {
        return 1 << facing.get2DDataValue();
    }

    protected int getAABBIndex(BlockState state) {
        return this.stateToIndex.computeIntIfAbsent(state, missingState -> {
            int i = 0;
            if (missingState.getValue(NORTH)) {
                i |= indexFor(Direction.NORTH);
            }

            if (missingState.getValue(EAST)) {
                i |= indexFor(Direction.EAST);
            }

            if (missingState.getValue(SOUTH)) {
                i |= indexFor(Direction.SOUTH);
            }

            if (missingState.getValue(WEST)) {
                i |= indexFor(Direction.WEST);
            }

            return i;
        });
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        switch (rot) {
            case CLOCKWISE_180:
                return state.setValue(NORTH, state.getValue(SOUTH))
                    .setValue(EAST, state.getValue(WEST))
                    .setValue(SOUTH, state.getValue(NORTH))
                    .setValue(WEST, state.getValue(EAST));
            case COUNTERCLOCKWISE_90:
                return state.setValue(NORTH, state.getValue(EAST))
                    .setValue(EAST, state.getValue(SOUTH))
                    .setValue(SOUTH, state.getValue(WEST))
                    .setValue(WEST, state.getValue(NORTH));
            case CLOCKWISE_90:
                return state.setValue(NORTH, state.getValue(WEST))
                    .setValue(EAST, state.getValue(NORTH))
                    .setValue(SOUTH, state.getValue(EAST))
                    .setValue(WEST, state.getValue(SOUTH));
            default:
                return state;
        }
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        switch (mirror) {
            case LEFT_RIGHT:
                return state.setValue(NORTH, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK:
                return state.setValue(EAST, state.getValue(WEST)).setValue(WEST, state.getValue(EAST));
            default:
                return super.mirror(state, mirror);
        }
    }
}
