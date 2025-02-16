package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;

public class SimpleBlockFeature extends Feature<SimpleBlockConfiguration> {
    public SimpleBlockFeature(Codec<SimpleBlockConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<SimpleBlockConfiguration> context) {
        SimpleBlockConfiguration simpleBlockConfiguration = context.config();
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        BlockState state = simpleBlockConfiguration.toPlace().getState(context.random(), blockPos);
        if (state.canSurvive(worldGenLevel, blockPos)) {
            if (state.getBlock() instanceof DoublePlantBlock) {
                if (!worldGenLevel.isEmptyBlock(blockPos.above())) {
                    return false;
                }

                DoublePlantBlock.placeAt(worldGenLevel, state, blockPos, 2);
            } else if (state.getBlock() instanceof MossyCarpetBlock) {
                MossyCarpetBlock.placeAt(worldGenLevel, blockPos, worldGenLevel.getRandom(), 2);
            } else {
                worldGenLevel.setBlock(blockPos, state, 2);
            }

            if (simpleBlockConfiguration.scheduleTick()) {
                worldGenLevel.scheduleTick(blockPos, worldGenLevel.getBlockState(blockPos).getBlock(), 1);
            }

            return true;
        } else {
            return false;
        }
    }
}
