package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CeilingHangingSignBlock extends SignBlock {
    public static final MapCodec<CeilingHangingSignBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(WoodType.CODEC.fieldOf("wood_type").forGetter(SignBlock::type), propertiesCodec())
            .apply(instance, CeilingHangingSignBlock::new)
    );
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
    public static final BooleanProperty ATTACHED = BlockStateProperties.ATTACHED;
    protected static final float AABB_OFFSET = 5.0F;
    protected static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);
    private static final Map<Integer, VoxelShape> AABBS = Maps.newHashMap(
        ImmutableMap.of(
            0,
            Block.box(1.0, 0.0, 7.0, 15.0, 10.0, 9.0),
            4,
            Block.box(7.0, 0.0, 1.0, 9.0, 10.0, 15.0),
            8,
            Block.box(1.0, 0.0, 7.0, 15.0, 10.0, 9.0),
            12,
            Block.box(7.0, 0.0, 1.0, 9.0, 10.0, 15.0)
        )
    );

    @Override
    public MapCodec<CeilingHangingSignBlock> codec() {
        return CODEC;
    }

    public CeilingHangingSignBlock(WoodType type, BlockBehaviour.Properties properties) {
        super(type, properties.sound(type.hangingSignSoundType()));
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(ROTATION, Integer.valueOf(0))
                .setValue(ATTACHED, Boolean.valueOf(false))
                .setValue(WATERLOGGED, Boolean.valueOf(false))
        );
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        return (InteractionResult)(level.getBlockEntity(pos) instanceof SignBlockEntity signBlockEntity
                && this.shouldTryToChainAnotherHangingSign(player, hitResult, signBlockEntity, stack)
            ? InteractionResult.PASS
            : super.useItemOn(stack, state, level, pos, player, hand, hitResult));
    }

    private boolean shouldTryToChainAnotherHangingSign(Player player, BlockHitResult hitResult, SignBlockEntity sign, ItemStack stack) {
        return !sign.canExecuteClickCommands(sign.isFacingFrontText(player), player)
            && stack.getItem() instanceof HangingSignItem
            && hitResult.getDirection().equals(Direction.DOWN);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.above()).isFaceSturdy(level, pos.above(), Direction.DOWN, SupportType.CENTER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        FluidState fluidState = level.getFluidState(context.getClickedPos());
        BlockPos blockPos = context.getClickedPos().above();
        BlockState blockState = level.getBlockState(blockPos);
        boolean isAllHangingSigns = blockState.is(BlockTags.ALL_HANGING_SIGNS);
        Direction direction = Direction.fromYRot(context.getRotation());
        boolean flag = !Block.isFaceFull(blockState.getCollisionShape(level, blockPos), Direction.DOWN) || context.isSecondaryUseActive();
        if (isAllHangingSigns && !context.isSecondaryUseActive()) {
            if (blockState.hasProperty(WallHangingSignBlock.FACING)) {
                Direction direction1 = blockState.getValue(WallHangingSignBlock.FACING);
                if (direction1.getAxis().test(direction)) {
                    flag = false;
                }
            } else if (blockState.hasProperty(ROTATION)) {
                Optional<Direction> optional = RotationSegment.convertToDirection(blockState.getValue(ROTATION));
                if (optional.isPresent() && optional.get().getAxis().test(direction)) {
                    flag = false;
                }
            }
        }

        int i = !flag ? RotationSegment.convertToSegment(direction.getOpposite()) : RotationSegment.convertToSegment(context.getRotation() + 180.0F);
        return this.defaultBlockState()
            .setValue(ATTACHED, Boolean.valueOf(flag))
            .setValue(ROTATION, Integer.valueOf(i))
            .setValue(WATERLOGGED, Boolean.valueOf(fluidState.getType() == Fluids.WATER));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape voxelShape = AABBS.get(state.getValue(ROTATION));
        return voxelShape == null ? SHAPE : voxelShape;
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return this.getShape(state, level, pos, CollisionContext.empty());
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
        return direction == Direction.UP && !this.canSurvive(state, level, pos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public float getYRotationDegrees(BlockState state) {
        return RotationSegment.convertToDegrees(state.getValue(ROTATION));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ROTATION, Integer.valueOf(rotation.rotate(state.getValue(ROTATION), 16)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(ROTATION, Integer.valueOf(mirror.mirror(state.getValue(ROTATION), 16)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION, ATTACHED, WATERLOGGED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HangingSignBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return null; // CraftBukkit - remove unnecessary sign ticking
    }
}
