package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;

public abstract class SpreadingSnowyDirtBlock extends SnowyDirtBlock {
    protected SpreadingSnowyDirtBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    private static boolean canBeGrass(BlockState state, LevelReader levelReader, BlockPos pos) {
        BlockPos blockPos = pos.above();
        BlockState blockState = levelReader.getBlockState(blockPos);
        if (blockState.is(Blocks.SNOW) && blockState.getValue(SnowLayerBlock.LAYERS) == 1) {
            return true;
        } else if (blockState.getFluidState().getAmount() == 8) {
            return false;
        } else {
            int lightBlockInto = LightEngine.getLightBlockInto(state, blockState, Direction.UP, blockState.getLightBlock());
            return lightBlockInto < 15;
        }
    }

    @Override
    protected abstract MapCodec<? extends SpreadingSnowyDirtBlock> codec();

    private static boolean canPropagate(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockPos = pos.above();
        return canBeGrass(state, level, pos) && !level.getFluidState(blockPos).is(FluidTags.WATER);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (this instanceof GrassBlock && level.paperConfig().tickRates.grassSpread != 1 && (level.paperConfig().tickRates.grassSpread < 1 || (net.minecraft.server.MinecraftServer.currentTick + pos.hashCode()) % level.paperConfig().tickRates.grassSpread != 0)) { return; } // Paper - Configurable random tick rates for blocks
        if (!canBeGrass(state, level, pos)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(level, pos, Blocks.DIRT.defaultBlockState()).isCancelled()) {
                return;
            }
            // CraftBukkit end
            level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
        } else {
            if (level.getMaxLocalRawBrightness(pos.above()) >= 9) {
                BlockState blockState = this.defaultBlockState();

                for (int i = 0; i < 4; i++) {
                    BlockPos blockPos = pos.offset(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);
                    if (level.getBlockState(blockPos).is(Blocks.DIRT) && canPropagate(blockState, level, blockPos)) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, pos, blockPos, blockState.setValue(SNOWY, Boolean.valueOf(isSnowySetting(level.getBlockState(blockPos.above()))))); // CraftBukkit
                    }
                }
            }
        }
    }
}
