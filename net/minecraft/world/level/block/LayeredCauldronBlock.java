package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public class LayeredCauldronBlock extends AbstractCauldronBlock {
    public static final MapCodec<LayeredCauldronBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Biome.Precipitation.CODEC.fieldOf("precipitation").forGetter(layeredCauldronBlock -> layeredCauldronBlock.precipitationType),
                CauldronInteraction.CODEC.fieldOf("interactions").forGetter(layeredCauldronBlock -> layeredCauldronBlock.interactions),
                propertiesCodec()
            )
            .apply(instance, LayeredCauldronBlock::new)
    );
    public static final int MIN_FILL_LEVEL = 1;
    public static final int MAX_FILL_LEVEL = 3;
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL_CAULDRON;
    private static final int BASE_CONTENT_HEIGHT = 6;
    private static final double HEIGHT_PER_LEVEL = 3.0;
    private final Biome.Precipitation precipitationType;

    @Override
    public MapCodec<LayeredCauldronBlock> codec() {
        return CODEC;
    }

    public LayeredCauldronBlock(Biome.Precipitation precipitationType, CauldronInteraction.InteractionMap interactions, BlockBehaviour.Properties properties) {
        super(properties, interactions);
        this.precipitationType = precipitationType;
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, Integer.valueOf(1)));
    }

    @Override
    public boolean isFull(BlockState state) {
        return state.getValue(LEVEL) == 3;
    }

    @Override
    protected boolean canReceiveStalactiteDrip(Fluid fluid) {
        return fluid == Fluids.WATER && this.precipitationType == Biome.Precipitation.RAIN;
    }

    @Override
    protected double getContentHeight(BlockState state) {
        return (6.0 + state.getValue(LEVEL).intValue() * 3.0) / 16.0;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level instanceof ServerLevel serverLevel && entity.isOnFire() && this.isEntityInsideContent(state, pos, entity)) {
            entity.clearFire();
            if (entity.mayInteract(serverLevel, pos)) {
                this.handleEntityOnFireInside(state, level, pos);
            }
        }
    }

    private void handleEntityOnFireInside(BlockState state, Level level, BlockPos pos) {
        if (this.precipitationType == Biome.Precipitation.SNOW) {
            lowerFillLevel(Blocks.WATER_CAULDRON.defaultBlockState().setValue(LEVEL, state.getValue(LEVEL)), level, pos);
        } else {
            lowerFillLevel(state, level, pos);
        }
    }

    public static void lowerFillLevel(BlockState state, Level level, BlockPos pos) {
        int i = state.getValue(LEVEL) - 1;
        BlockState blockState = i == 0 ? Blocks.CAULDRON.defaultBlockState() : state.setValue(LEVEL, Integer.valueOf(i));
        level.setBlockAndUpdate(pos, blockState);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(blockState));
    }

    @Override
    public void handlePrecipitation(BlockState state, Level level, BlockPos pos, Biome.Precipitation precipitation) {
        if (CauldronBlock.shouldHandlePrecipitation(level, precipitation) && state.getValue(LEVEL) != 3 && precipitation == this.precipitationType) {
            BlockState blockState = state.cycle(LEVEL);
            level.setBlockAndUpdate(pos, blockState);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(blockState));
        }
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(LEVEL);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    protected void receiveStalactiteDrip(BlockState state, Level level, BlockPos pos, Fluid fluid) {
        if (!this.isFull(state)) {
            BlockState blockState = state.setValue(LEVEL, Integer.valueOf(state.getValue(LEVEL) + 1));
            level.setBlockAndUpdate(pos, blockState);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(blockState));
            level.levelEvent(1047, pos, 0);
        }
    }
}
