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
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (level instanceof ServerLevel serverLevel && entity.isOnFire() && this.isEntityInsideContent(state, pos, entity)) {
            // CraftBukkit start - moved down
            // entity.clearFire();
            if ((entity instanceof net.minecraft.world.entity.player.Player || serverLevel.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) && entity.mayInteract(serverLevel, pos)) { // Paper - Fixes MC-248588
                if (this.handleEntityOnFireInside(state, level, pos, entity)) { // Paper - fix powdered snow cauldron extinguishing entities
                    entity.clearFire();
                }
            // CraftBukkit end
            }
        }
    }

    // CraftBukkit start
    private boolean handleEntityOnFireInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (this.precipitationType == Biome.Precipitation.SNOW) {
            return LayeredCauldronBlock.lowerFillLevel((BlockState) Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, (Integer) state.getValue(LayeredCauldronBlock.LEVEL)), level, pos, entity, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.EXTINGUISH); // CraftBukkit
        } else {
            return LayeredCauldronBlock.lowerFillLevel(state, level, pos, entity, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.EXTINGUISH); // CraftBukkit
        }
    }

    public static void lowerFillLevel(BlockState state, Level level, BlockPos pos) {
        // CraftBukkit start
        LayeredCauldronBlock.lowerFillLevel(state, level, pos, null, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.UNKNOWN);
    }
    public static boolean lowerFillLevel(BlockState state, Level level, BlockPos pos, Entity entity, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason reason) {
        int i = (Integer) state.getValue(LayeredCauldronBlock.LEVEL) - 1;
        BlockState iblockdata1 = i == 0 ? Blocks.CAULDRON.defaultBlockState() : (BlockState) state.setValue(LayeredCauldronBlock.LEVEL, i);

        return LayeredCauldronBlock.changeLevel(level, pos, iblockdata1, entity, reason);
     }

    // CraftBukkit start
    // Paper start - Call CauldronLevelChangeEvent
    public static boolean changeLevel(Level level, BlockPos pos, BlockState newBlock, @javax.annotation.Nullable Entity entity, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason reason) { // Paper - entity is nullable
        return changeLevel(level, pos, newBlock, entity, reason, true);
    }

    public static boolean changeLevel(Level level, BlockPos pos, BlockState newBlock, @javax.annotation.Nullable Entity entity, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason reason, boolean sendGameEvent) { // Paper - entity is nullable
        // Paper end - Call CauldronLevelChangeEvent
        org.bukkit.craftbukkit.block.CraftBlockState newState = org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(level, pos);
        newState.setData(newBlock);

        org.bukkit.event.block.CauldronLevelChangeEvent event = new org.bukkit.event.block.CauldronLevelChangeEvent(
                org.bukkit.craftbukkit.block.CraftBlock.at(level, pos),
                (entity == null) ? null : entity.getBukkitEntity(), reason, newState
        );
        if (!event.callEvent()) {
            return false;
        }
        newState.update(true);
        if (sendGameEvent) level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(newBlock)); // Paper - Call CauldronLevelChangeEvent
        return true;
    }
    // CraftBukkit end

    @Override
    public void handlePrecipitation(BlockState state, Level level, BlockPos pos, Biome.Precipitation precipitation) {
        if (CauldronBlock.shouldHandlePrecipitation(level, precipitation) && state.getValue(LEVEL) != 3 && precipitation == this.precipitationType) {
            BlockState blockState = state.cycle(LEVEL);
            LayeredCauldronBlock.changeLevel(level, pos, blockState, null, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL); // CraftBukkit
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
            // CraftBukkit start
            if (!LayeredCauldronBlock.changeLevel(level, pos, blockState, null, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL)) {
                return;
            }
            // CraftBukkit end
            level.levelEvent(1047, pos, 0);
        }
    }
}
