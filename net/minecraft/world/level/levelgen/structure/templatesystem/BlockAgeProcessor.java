package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

public class BlockAgeProcessor extends StructureProcessor {
    public static final MapCodec<BlockAgeProcessor> CODEC = Codec.FLOAT
        .fieldOf("mossiness")
        .xmap(BlockAgeProcessor::new, blockAgeProcessor -> blockAgeProcessor.mossiness);
    private static final float PROBABILITY_OF_REPLACING_FULL_BLOCK = 0.5F;
    private static final float PROBABILITY_OF_REPLACING_STAIRS = 0.5F;
    private static final float PROBABILITY_OF_REPLACING_OBSIDIAN = 0.15F;
    private static final BlockState[] NON_MOSSY_REPLACEMENTS = new BlockState[]{
        Blocks.STONE_SLAB.defaultBlockState(), Blocks.STONE_BRICK_SLAB.defaultBlockState()
    };
    private final float mossiness;

    public BlockAgeProcessor(float mossiness) {
        this.mossiness = mossiness;
    }

    @Nullable
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(
        LevelReader level,
        BlockPos offset,
        BlockPos pos,
        StructureTemplate.StructureBlockInfo blockInfo,
        StructureTemplate.StructureBlockInfo relativeBlockInfo,
        StructurePlaceSettings settings
    ) {
        RandomSource random = settings.getRandom(relativeBlockInfo.pos());
        BlockState blockState = relativeBlockInfo.state();
        BlockPos blockPos = relativeBlockInfo.pos();
        BlockState blockState1 = null;
        if (blockState.is(Blocks.STONE_BRICKS) || blockState.is(Blocks.STONE) || blockState.is(Blocks.CHISELED_STONE_BRICKS)) {
            blockState1 = this.maybeReplaceFullStoneBlock(random);
        } else if (blockState.is(BlockTags.STAIRS)) {
            blockState1 = this.maybeReplaceStairs(random, relativeBlockInfo.state());
        } else if (blockState.is(BlockTags.SLABS)) {
            blockState1 = this.maybeReplaceSlab(random);
        } else if (blockState.is(BlockTags.WALLS)) {
            blockState1 = this.maybeReplaceWall(random);
        } else if (blockState.is(Blocks.OBSIDIAN)) {
            blockState1 = this.maybeReplaceObsidian(random);
        }

        return blockState1 != null ? new StructureTemplate.StructureBlockInfo(blockPos, blockState1, relativeBlockInfo.nbt()) : relativeBlockInfo;
    }

    @Nullable
    private BlockState maybeReplaceFullStoneBlock(RandomSource random) {
        if (random.nextFloat() >= 0.5F) {
            return null;
        } else {
            BlockState[] blockStates = new BlockState[]{
                Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), getRandomFacingStairs(random, Blocks.STONE_BRICK_STAIRS)
            };
            BlockState[] blockStates1 = new BlockState[]{
                Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), getRandomFacingStairs(random, Blocks.MOSSY_STONE_BRICK_STAIRS)
            };
            return this.getRandomBlock(random, blockStates, blockStates1);
        }
    }

    @Nullable
    private BlockState maybeReplaceStairs(RandomSource random, BlockState state) {
        Direction direction = state.getValue(StairBlock.FACING);
        Half half = state.getValue(StairBlock.HALF);
        if (random.nextFloat() >= 0.5F) {
            return null;
        } else {
            BlockState[] blockStates = new BlockState[]{
                Blocks.MOSSY_STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, direction).setValue(StairBlock.HALF, half),
                Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState()
            };
            return this.getRandomBlock(random, NON_MOSSY_REPLACEMENTS, blockStates);
        }
    }

    @Nullable
    private BlockState maybeReplaceSlab(RandomSource random) {
        return random.nextFloat() < this.mossiness ? Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState() : null;
    }

    @Nullable
    private BlockState maybeReplaceWall(RandomSource random) {
        return random.nextFloat() < this.mossiness ? Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState() : null;
    }

    @Nullable
    private BlockState maybeReplaceObsidian(RandomSource random) {
        return random.nextFloat() < 0.15F ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : null;
    }

    private static BlockState getRandomFacingStairs(RandomSource random, Block stairsBlock) {
        return stairsBlock.defaultBlockState()
            .setValue(StairBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random))
            .setValue(StairBlock.HALF, Util.getRandom(Half.values(), random));
    }

    private BlockState getRandomBlock(RandomSource random, BlockState[] normalStates, BlockState[] mossyStates) {
        return random.nextFloat() < this.mossiness ? getRandomBlock(random, mossyStates) : getRandomBlock(random, normalStates);
    }

    private static BlockState getRandomBlock(RandomSource random, BlockState[] states) {
        return states[random.nextInt(states.length)];
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.BLOCK_AGE;
    }
}
