package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Column;
import net.minecraft.world.level.levelgen.feature.configurations.UnderwaterMagmaConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class UnderwaterMagmaFeature extends Feature<UnderwaterMagmaConfiguration> {
    public UnderwaterMagmaFeature(Codec<UnderwaterMagmaConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<UnderwaterMagmaConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        UnderwaterMagmaConfiguration underwaterMagmaConfiguration = context.config();
        RandomSource randomSource = context.random();
        OptionalInt floorY = getFloorY(worldGenLevel, blockPos, underwaterMagmaConfiguration);
        if (floorY.isEmpty()) {
            return false;
        } else {
            BlockPos blockPos1 = blockPos.atY(floorY.getAsInt());
            Vec3i vec3i = new Vec3i(
                underwaterMagmaConfiguration.placementRadiusAroundFloor,
                underwaterMagmaConfiguration.placementRadiusAroundFloor,
                underwaterMagmaConfiguration.placementRadiusAroundFloor
            );
            BoundingBox boundingBox = BoundingBox.fromCorners(blockPos1.subtract(vec3i), blockPos1.offset(vec3i));
            return BlockPos.betweenClosedStream(boundingBox)
                    .filter(blockPos2 -> randomSource.nextFloat() < underwaterMagmaConfiguration.placementProbabilityPerValidPosition)
                    .filter(blockPos2 -> this.isValidPlacement(worldGenLevel, blockPos2))
                    .mapToInt(blockPos2 -> {
                        worldGenLevel.setBlock(blockPos2, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                        return 1;
                    })
                    .sum()
                > 0;
        }
    }

    private static OptionalInt getFloorY(WorldGenLevel level, BlockPos pos, UnderwaterMagmaConfiguration config) {
        Predicate<BlockState> predicate = blockState -> blockState.is(Blocks.WATER);
        Predicate<BlockState> predicate1 = blockState -> !blockState.is(Blocks.WATER);
        Optional<Column> optional = Column.scan(level, pos, config.floorSearchRange, predicate, predicate1);
        return optional.map(Column::getFloor).orElseGet(OptionalInt::empty);
    }

    private boolean isValidPlacement(WorldGenLevel level, BlockPos pos) {
        if (!this.isWaterOrAir(level, pos) && !this.isWaterOrAir(level, pos.below())) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (this.isWaterOrAir(level, pos.relative(direction))) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private boolean isWaterOrAir(LevelAccessor level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        return blockState.is(Blocks.WATER) || blockState.isAir();
    }
}
