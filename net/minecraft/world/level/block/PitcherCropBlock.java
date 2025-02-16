package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PitcherCropBlock extends DoublePlantBlock implements BonemealableBlock {
    public static final MapCodec<PitcherCropBlock> CODEC = simpleCodec(PitcherCropBlock::new);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_4;
    public static final int MAX_AGE = 4;
    private static final int DOUBLE_PLANT_AGE_INTERSECTION = 3;
    private static final int BONEMEAL_INCREASE = 1;
    private static final VoxelShape FULL_UPPER_SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 15.0, 13.0);
    private static final VoxelShape FULL_LOWER_SHAPE = Block.box(3.0, -1.0, 3.0, 13.0, 16.0, 13.0);
    private static final VoxelShape COLLISION_SHAPE_BULB = Block.box(5.0, -1.0, 5.0, 11.0, 3.0, 11.0);
    private static final VoxelShape COLLISION_SHAPE_CROP = Block.box(3.0, -1.0, 3.0, 13.0, 5.0, 13.0);
    private static final VoxelShape[] UPPER_SHAPE_BY_AGE = new VoxelShape[]{Block.box(3.0, 0.0, 3.0, 13.0, 11.0, 13.0), FULL_UPPER_SHAPE};
    private static final VoxelShape[] LOWER_SHAPE_BY_AGE = new VoxelShape[]{
        COLLISION_SHAPE_BULB, Block.box(3.0, -1.0, 3.0, 13.0, 14.0, 13.0), FULL_LOWER_SHAPE, FULL_LOWER_SHAPE, FULL_LOWER_SHAPE
    };

    @Override
    public MapCodec<PitcherCropBlock> codec() {
        return CODEC;
    }

    public PitcherCropBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER
            ? UPPER_SHAPE_BY_AGE[Math.min(Math.abs(4 - (state.getValue(AGE) + 1)), UPPER_SHAPE_BY_AGE.length - 1)]
            : LOWER_SHAPE_BY_AGE[state.getValue(AGE)];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(AGE) == 0) {
            return COLLISION_SHAPE_BULB;
        } else {
            return state.getValue(HALF) == DoubleBlockHalf.LOWER ? COLLISION_SHAPE_CROP : super.getCollisionShape(state, level, pos, context);
        }
    }

    @Override
    public BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random
    ) {
        if (isDouble(state.getValue(AGE))) {
            return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        } else {
            return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return (!isLower(state) || sufficientLight(level, pos)) && super.canSurvive(state, level, pos);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.FARMLAND);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level instanceof ServerLevel serverLevel && entity instanceof Ravager && serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            serverLevel.destroyBlock(pos, true, entity);
        }

        super.entityInside(state, level, pos, entity);
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        return false;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER && !this.isMaxAge(state);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        float growthSpeed = CropBlock.getGrowthSpeed(this, level, pos);
        boolean flag = random.nextInt((int)(25.0F / growthSpeed) + 1) == 0;
        if (flag) {
            this.grow(level, state, pos, 1);
        }
    }

    private void grow(ServerLevel level, BlockState state, BlockPos pos, int ageIncrement) {
        int min = Math.min(state.getValue(AGE) + ageIncrement, 4);
        if (this.canGrow(level, pos, state, min)) {
            BlockState blockState = state.setValue(AGE, Integer.valueOf(min));
            level.setBlock(pos, blockState, 2);
            if (isDouble(min)) {
                level.setBlock(pos.above(), blockState.setValue(HALF, DoubleBlockHalf.UPPER), 3);
            }
        }
    }

    private static boolean canGrowInto(LevelReader level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        return blockState.isAir() || blockState.is(Blocks.PITCHER_CROP);
    }

    private static boolean sufficientLight(LevelReader level, BlockPos pos) {
        return CropBlock.hasSufficientLight(level, pos);
    }

    private static boolean isLower(BlockState state) {
        return state.is(Blocks.PITCHER_CROP) && state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    private static boolean isDouble(int age) {
        return age >= 3;
    }

    private boolean canGrow(LevelReader reader, BlockPos pos, BlockState state, int age) {
        return !this.isMaxAge(state) && sufficientLight(reader, pos) && (!isDouble(age) || canGrowInto(reader, pos.above()));
    }

    private boolean isMaxAge(BlockState state) {
        return state.getValue(AGE) >= 4;
    }

    @Nullable
    private PitcherCropBlock.PosAndState getLowerHalf(LevelReader level, BlockPos pos, BlockState state) {
        if (isLower(state)) {
            return new PitcherCropBlock.PosAndState(pos, state);
        } else {
            BlockPos blockPos = pos.below();
            BlockState blockState = level.getBlockState(blockPos);
            return isLower(blockState) ? new PitcherCropBlock.PosAndState(blockPos, blockState) : null;
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        PitcherCropBlock.PosAndState lowerHalf = this.getLowerHalf(level, pos, state);
        return lowerHalf != null && this.canGrow(level, lowerHalf.pos, lowerHalf.state, lowerHalf.state.getValue(AGE) + 1);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        PitcherCropBlock.PosAndState lowerHalf = this.getLowerHalf(level, pos, state);
        if (lowerHalf != null) {
            this.grow(level, lowerHalf.state, lowerHalf.pos, 1);
        }
    }

    record PosAndState(BlockPos pos, BlockState state) {
    }
}
