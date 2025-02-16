package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class PipeBlock extends Block {
    private static final Direction[] DIRECTIONS = Direction.values();
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = ImmutableMap.copyOf(Util.make(Maps.newEnumMap(Direction.class), map -> {
        map.put(Direction.NORTH, NORTH);
        map.put(Direction.EAST, EAST);
        map.put(Direction.SOUTH, SOUTH);
        map.put(Direction.WEST, WEST);
        map.put(Direction.UP, UP);
        map.put(Direction.DOWN, DOWN);
    }));
    protected final VoxelShape[] shapeByIndex;

    protected PipeBlock(float apothem, BlockBehaviour.Properties properties) {
        super(properties);
        this.shapeByIndex = this.makeShapes(apothem);
    }

    @Override
    protected abstract MapCodec<? extends PipeBlock> codec();

    private VoxelShape[] makeShapes(float apothem) {
        float f = 0.5F - apothem;
        float f1 = 0.5F + apothem;
        VoxelShape voxelShape = Block.box(f * 16.0F, f * 16.0F, f * 16.0F, f1 * 16.0F, f1 * 16.0F, f1 * 16.0F);
        VoxelShape[] voxelShapes = new VoxelShape[DIRECTIONS.length];

        for (int i = 0; i < DIRECTIONS.length; i++) {
            Direction direction = DIRECTIONS[i];
            voxelShapes[i] = Shapes.box(
                0.5 + Math.min((double)(-apothem), direction.getStepX() * 0.5),
                0.5 + Math.min((double)(-apothem), direction.getStepY() * 0.5),
                0.5 + Math.min((double)(-apothem), direction.getStepZ() * 0.5),
                0.5 + Math.max((double)apothem, direction.getStepX() * 0.5),
                0.5 + Math.max((double)apothem, direction.getStepY() * 0.5),
                0.5 + Math.max((double)apothem, direction.getStepZ() * 0.5)
            );
        }

        VoxelShape[] voxelShapes1 = new VoxelShape[64];

        for (int i1 = 0; i1 < 64; i1++) {
            VoxelShape voxelShape1 = voxelShape;

            for (int i2 = 0; i2 < DIRECTIONS.length; i2++) {
                if ((i1 & 1 << i2) != 0) {
                    voxelShape1 = Shapes.or(voxelShape1, voxelShapes[i2]);
                }
            }

            voxelShapes1[i1] = voxelShape1;
        }

        return voxelShapes1;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return false;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapeByIndex[this.getAABBIndex(state)];
    }

    protected int getAABBIndex(BlockState state) {
        int i = 0;

        for (int i1 = 0; i1 < DIRECTIONS.length; i1++) {
            if (state.getValue(PROPERTY_BY_DIRECTION.get(DIRECTIONS[i1]))) {
                i |= 1 << i1;
            }
        }

        return i;
    }
}
