package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CandleBlock extends AbstractCandleBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<CandleBlock> CODEC = simpleCodec(CandleBlock::new);
    public static final int MIN_CANDLES = 1;
    public static final int MAX_CANDLES = 4;
    public static final IntegerProperty CANDLES = BlockStateProperties.CANDLES;
    public static final BooleanProperty LIT = AbstractCandleBlock.LIT;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final ToIntFunction<BlockState> LIGHT_EMISSION = state -> state.getValue(LIT) ? 3 * state.getValue(CANDLES) : 0;
    private static final Int2ObjectMap<List<Vec3>> PARTICLE_OFFSETS = Util.make(() -> {
        Int2ObjectMap<List<Vec3>> map = new Int2ObjectOpenHashMap<>();
        map.defaultReturnValue(ImmutableList.of());
        map.put(1, ImmutableList.of(new Vec3(0.5, 0.5, 0.5)));
        map.put(2, ImmutableList.of(new Vec3(0.375, 0.44, 0.5), new Vec3(0.625, 0.5, 0.44)));
        map.put(3, ImmutableList.of(new Vec3(0.5, 0.313, 0.625), new Vec3(0.375, 0.44, 0.5), new Vec3(0.56, 0.5, 0.44)));
        map.put(4, ImmutableList.of(new Vec3(0.44, 0.313, 0.56), new Vec3(0.625, 0.44, 0.56), new Vec3(0.375, 0.44, 0.375), new Vec3(0.56, 0.5, 0.375)));
        return Int2ObjectMaps.unmodifiable(map);
    });
    private static final VoxelShape ONE_AABB = Block.box(7.0, 0.0, 7.0, 9.0, 6.0, 9.0);
    private static final VoxelShape TWO_AABB = Block.box(5.0, 0.0, 6.0, 11.0, 6.0, 9.0);
    private static final VoxelShape THREE_AABB = Block.box(5.0, 0.0, 6.0, 10.0, 6.0, 11.0);
    private static final VoxelShape FOUR_AABB = Block.box(5.0, 0.0, 5.0, 11.0, 6.0, 10.0);

    @Override
    public MapCodec<CandleBlock> codec() {
        return CODEC;
    }

    public CandleBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(CANDLES, Integer.valueOf(1))
                .setValue(LIT, Boolean.valueOf(false))
                .setValue(WATERLOGGED, Boolean.valueOf(false))
        );
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (stack.isEmpty() && player.getAbilities().mayBuild && state.getValue(LIT)) {
            extinguish(player, state, level, pos);
            return InteractionResult.SUCCESS;
        } else {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        return !useContext.isSecondaryUseActive() && useContext.getItemInHand().getItem() == this.asItem() && state.getValue(CANDLES) < 4
            || super.canBeReplaced(state, useContext);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockState = context.getLevel().getBlockState(context.getClickedPos());
        if (blockState.is(this)) {
            return blockState.cycle(CANDLES);
        } else {
            FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
            boolean flag = fluidState.getType() == Fluids.WATER;
            return super.getStateForPlacement(context).setValue(WATERLOGGED, Boolean.valueOf(flag));
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
        if (state.getValue(WATERLOGGED)) {
            scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        switch (state.getValue(CANDLES)) {
            case 1:
            default:
                return ONE_AABB;
            case 2:
                return TWO_AABB;
            case 3:
                return THREE_AABB;
            case 4:
                return FOUR_AABB;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CANDLES, LIT, WATERLOGGED);
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        if (!state.getValue(WATERLOGGED) && fluidState.getType() == Fluids.WATER) {
            BlockState blockState = state.setValue(WATERLOGGED, Boolean.valueOf(true));
            if (state.getValue(LIT)) {
                extinguish(null, blockState, level, pos);
            } else {
                level.setBlock(pos, blockState, 3);
            }

            level.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level));
            return true;
        } else {
            return false;
        }
    }

    public static boolean canLight(BlockState state) {
        return state.is(BlockTags.CANDLES, state1 -> state1.hasProperty(LIT) && state1.hasProperty(WATERLOGGED))
            && !state.getValue(LIT)
            && !state.getValue(WATERLOGGED);
    }

    @Override
    protected Iterable<Vec3> getParticleOffsets(BlockState state) {
        return PARTICLE_OFFSETS.get(state.getValue(CANDLES).intValue());
    }

    @Override
    protected boolean canBeLit(BlockState state) {
        return !state.getValue(WATERLOGGED) && super.canBeLit(state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }
}
