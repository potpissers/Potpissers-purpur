package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SlabBlock extends Block implements SimpleWaterloggedBlock {
    public static final MapCodec<SlabBlock> CODEC = simpleCodec(SlabBlock::new);
    public static final EnumProperty<SlabType> TYPE = BlockStateProperties.SLAB_TYPE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    protected static final VoxelShape BOTTOM_AABB = Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);
    protected static final VoxelShape TOP_AABB = Block.box(0.0, 8.0, 0.0, 16.0, 16.0, 16.0);

    @Override
    public MapCodec<? extends SlabBlock> codec() {
        return CODEC;
    }

    public SlabBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(TYPE, SlabType.BOTTOM).setValue(WATERLOGGED, Boolean.valueOf(false)));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return state.getValue(TYPE) != SlabType.DOUBLE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TYPE, WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        SlabType slabType = state.getValue(TYPE);
        switch (slabType) {
            case DOUBLE:
                return Shapes.block();
            case TOP:
                return TOP_AABB;
            default:
                return BOTTOM_AABB;
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos clickedPos = context.getClickedPos();
        BlockState blockState = context.getLevel().getBlockState(clickedPos);
        if (blockState.is(this)) {
            return blockState.setValue(TYPE, SlabType.DOUBLE).setValue(WATERLOGGED, Boolean.valueOf(false));
        } else {
            FluidState fluidState = context.getLevel().getFluidState(clickedPos);
            BlockState blockState1 = this.defaultBlockState()
                .setValue(TYPE, SlabType.BOTTOM)
                .setValue(WATERLOGGED, Boolean.valueOf(fluidState.getType() == Fluids.WATER));
            Direction clickedFace = context.getClickedFace();
            return clickedFace != Direction.DOWN && (clickedFace == Direction.UP || !(context.getClickLocation().y - clickedPos.getY() > 0.5))
                ? blockState1
                : blockState1.setValue(TYPE, SlabType.TOP);
        }
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        ItemStack itemInHand = useContext.getItemInHand();
        SlabType slabType = state.getValue(TYPE);
        if (slabType == SlabType.DOUBLE || !itemInHand.is(this.asItem())) {
            return false;
        } else if (useContext.replacingClickedOnBlock()) {
            boolean flag = useContext.getClickLocation().y - useContext.getClickedPos().getY() > 0.5;
            Direction clickedFace = useContext.getClickedFace();
            return slabType == SlabType.BOTTOM
                ? clickedFace == Direction.UP || flag && clickedFace.getAxis().isHorizontal()
                : clickedFace == Direction.DOWN || !flag && clickedFace.getAxis().isHorizontal();
        } else {
            return true;
        }
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        return state.getValue(TYPE) != SlabType.DOUBLE && SimpleWaterloggedBlock.super.placeLiquid(level, pos, state, fluidState);
    }

    @Override
    public boolean canPlaceLiquid(@Nullable Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return state.getValue(TYPE) != SlabType.DOUBLE && SimpleWaterloggedBlock.super.canPlaceLiquid(player, level, pos, state, fluid);
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

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        switch (pathComputationType) {
            case LAND:
                return false;
            case WATER:
                return state.getFluidState().is(FluidTags.WATER);
            case AIR:
                return false;
            default:
                return false;
        }
    }

    // Purpur start - Break individual slabs when sneaking
    public boolean halfBreak(BlockState state, BlockPos pos, net.minecraft.server.level.ServerPlayer player) {
        if (state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE) {
            return false;
        }
        net.minecraft.world.phys.HitResult result = player.getRayTrace(16, net.minecraft.world.level.ClipContext.Fluid.NONE);
        if (result.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return false;
        }
        double hitY = result.getLocation().y();
        int blockY = org.bukkit.util.NumberConversions.floor(hitY);
        player.level().setBlock(pos, state.setValue(SlabBlock.TYPE, (hitY - blockY > 0.5 || blockY - pos.getY() == 1) ? SlabType.BOTTOM : SlabType.TOP), 3);
        if (!player.getAbilities().instabuild) {
            net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(player.level(), pos.getX(), pos.getY(), pos.getZ(), new ItemStack(asItem()));
            item.setDefaultPickUpDelay();
            player.level().addFreshEntity(item);
        }
        return true;
    }
    // Purpur end - Break individual slabs when sneaking
}
