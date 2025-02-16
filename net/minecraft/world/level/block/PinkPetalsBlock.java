package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.function.BiFunction;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PinkPetalsBlock extends BushBlock implements BonemealableBlock {
    public static final MapCodec<PinkPetalsBlock> CODEC = simpleCodec(PinkPetalsBlock::new);
    public static final int MIN_FLOWERS = 1;
    public static final int MAX_FLOWERS = 4;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty AMOUNT = BlockStateProperties.FLOWER_AMOUNT;
    private static final BiFunction<Direction, Integer, VoxelShape> SHAPE_BY_PROPERTIES = Util.memoize(
        (direction, integer) -> {
            VoxelShape[] voxelShapes = new VoxelShape[]{
                Block.box(8.0, 0.0, 8.0, 16.0, 3.0, 16.0),
                Block.box(8.0, 0.0, 0.0, 16.0, 3.0, 8.0),
                Block.box(0.0, 0.0, 0.0, 8.0, 3.0, 8.0),
                Block.box(0.0, 0.0, 8.0, 8.0, 3.0, 16.0)
            };
            VoxelShape voxelShape = Shapes.empty();

            for (int i = 0; i < integer; i++) {
                int i1 = Math.floorMod(i - direction.get2DDataValue(), 4);
                voxelShape = Shapes.or(voxelShape, voxelShapes[i1]);
            }

            return voxelShape.singleEncompassing();
        }
    );

    @Override
    public MapCodec<PinkPetalsBlock> codec() {
        return CODEC;
    }

    protected PinkPetalsBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(AMOUNT, Integer.valueOf(1)));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        return !useContext.isSecondaryUseActive() && useContext.getItemInHand().is(this.asItem()) && state.getValue(AMOUNT) < 4
            || super.canBeReplaced(state, useContext);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_BY_PROPERTIES.apply(state.getValue(FACING), state.getValue(AMOUNT));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockState = context.getLevel().getBlockState(context.getClickedPos());
        return blockState.is(this)
            ? blockState.setValue(AMOUNT, Integer.valueOf(Math.min(4, blockState.getValue(AMOUNT) + 1)))
            : this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, AMOUNT);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        int amountValue = state.getValue(AMOUNT);
        if (amountValue < 4) {
            level.setBlock(pos, state.setValue(AMOUNT, Integer.valueOf(amountValue + 1)), 2);
        } else {
            popResource(level, pos, new ItemStack(this));
        }
    }
}
