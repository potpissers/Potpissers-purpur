package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.DaylightDetectorBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DaylightDetectorBlock extends BaseEntityBlock {
    public static final MapCodec<DaylightDetectorBlock> CODEC = simpleCodec(DaylightDetectorBlock::new);
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final BooleanProperty INVERTED = BlockStateProperties.INVERTED;
    protected static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 6.0, 16.0);

    @Override
    public MapCodec<DaylightDetectorBlock> codec() {
        return CODEC;
    }

    public DaylightDetectorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWER, Integer.valueOf(0)).setValue(INVERTED, Boolean.valueOf(false)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState blockState, BlockGetter blockAccess, BlockPos pos, Direction side) {
        return blockState.getValue(POWER);
    }

    private static void updateSignalStrength(BlockState state, Level level, BlockPos pos) {
        int i = level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken();
        float sunAngle = level.getSunAngle(1.0F);
        boolean invertedValue = state.getValue(INVERTED);
        if (invertedValue) {
            i = 15 - i;
        } else if (i > 0) {
            float f = sunAngle < (float) Math.PI ? 0.0F : (float) (Math.PI * 2);
            sunAngle += (f - sunAngle) * 0.2F;
            i = Math.round(i * Mth.cos(sunAngle));
        }

        i = Mth.clamp(i, 0, 15);
        if (state.getValue(POWER) != i) {
            i = org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(level, pos, state.getValue(DaylightDetectorBlock.POWER), i).getNewCurrent(); // CraftBukkit - Call BlockRedstoneEvent
            level.setBlock(pos, state.setValue(POWER, Integer.valueOf(i)), 3);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.mayBuild()) {
            return super.useWithoutItem(state, level, pos, player, hitResult);
        } else {
            if (!level.isClientSide) {
                BlockState blockState = state.cycle(INVERTED);
                level.setBlock(pos, blockState, 2);
                level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, blockState));
                updateSignalStrength(blockState, level, pos);
            }

            return InteractionResult.SUCCESS;
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DaylightDetectorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return !level.isClientSide && level.dimensionType().hasSkyLight()
            ? createTickerHelper(blockEntityType, BlockEntityType.DAYLIGHT_DETECTOR, DaylightDetectorBlock::tickEntity)
            : null;
    }

    private static void tickEntity(Level level, BlockPos pos, BlockState state, DaylightDetectorBlockEntity blockEntity) {
        if (level.getGameTime() % 20L == 0L) {
            updateSignalStrength(state, level, pos);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER, INVERTED);
    }
}
