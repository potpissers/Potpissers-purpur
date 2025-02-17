package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SculkShriekerBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<SculkShriekerBlock> CODEC = simpleCodec(SculkShriekerBlock::new);
    public static final BooleanProperty SHRIEKING = BlockStateProperties.SHRIEKING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty CAN_SUMMON = BlockStateProperties.CAN_SUMMON;
    protected static final VoxelShape COLLIDER = Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);
    public static final double TOP_Y = COLLIDER.max(Direction.Axis.Y);

    @Override
    public MapCodec<SculkShriekerBlock> codec() {
        return CODEC;
    }

    public SculkShriekerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(SHRIEKING, Boolean.valueOf(false))
                .setValue(WATERLOGGED, Boolean.valueOf(false))
                .setValue(CAN_SUMMON, Boolean.valueOf(false))
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHRIEKING);
        builder.add(WATERLOGGED);
        builder.add(CAN_SUMMON);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level instanceof ServerLevel serverLevel) {
            ServerPlayer serverPlayer = SculkShriekerBlockEntity.tryGetPlayer(entity);
            if (serverPlayer != null) {
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent(serverPlayer, org.bukkit.event.block.Action.PHYSICAL, pos, null, null, null).isCancelled()) return; // CraftBukkit
                serverLevel.getBlockEntity(pos, BlockEntityType.SCULK_SHRIEKER).ifPresent(sculkShrieker -> sculkShrieker.tryShriek(serverLevel, serverPlayer));
            }
        }

        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && state.getValue(SHRIEKING) && !state.is(newState.getBlock())) {
            serverLevel.getBlockEntity(pos, BlockEntityType.SCULK_SHRIEKER).ifPresent(sculkShrieker -> sculkShrieker.tryRespond(serverLevel));
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(SHRIEKING)) {
            level.setBlock(pos, state.setValue(SHRIEKING, Boolean.valueOf(false)), 3);
            level.getBlockEntity(pos, BlockEntityType.SCULK_SHRIEKER).ifPresent(sculkShrieker -> sculkShrieker.tryRespond(level));
        }
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLIDER;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return COLLIDER;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SculkShriekerBlockEntity(pos, state);
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

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
            .setValue(WATERLOGGED, Boolean.valueOf(context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER)).setValue(SculkShriekerBlock.CAN_SUMMON, context.getLevel().purpurConfig.sculkShriekerCanSummonDefault); // Purpur - Config for sculk shrieker can_summon state
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack stack, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, stack, dropExperience);
        // CraftBukkit start - Delegate to getExpDrop
    }

    @Override
    public int getExpDrop(BlockState state, ServerLevel level, BlockPos pos, ItemStack stack, boolean dropExperience) {
        if (dropExperience) {
            return this.tryDropExperience(level, pos, stack, ConstantInt.of(5));
        }
        return 0;
        // CraftBukkit end
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return !level.isClientSide
            ? BaseEntityBlock.createTickerHelper(
                blockEntityType,
                BlockEntityType.SCULK_SHRIEKER,
                (level1, pos, state1, blockEntity) -> VibrationSystem.Ticker.tick(level1, blockEntity.getVibrationData(), blockEntity.getVibrationUser())
            )
            : null;
    }
}
