package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class ParticleLeavesBlock extends LeavesBlock {
    public static final MapCodec<ParticleLeavesBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("chance").forGetter(block -> block.chance),
                ParticleTypes.CODEC.fieldOf("particle").forGetter(block -> block.particle),
                propertiesCodec()
            )
            .apply(instance, ParticleLeavesBlock::new)
    );
    private final ParticleOptions particle;
    private final int chance;

    @Override
    public MapCodec<ParticleLeavesBlock> codec() {
        return CODEC;
    }

    public ParticleLeavesBlock(int chance, ParticleOptions particle, BlockBehaviour.Properties properties) {
        super(properties);
        this.chance = chance;
        this.particle = particle;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (random.nextInt(this.chance) == 0) {
            BlockPos blockPos = pos.below();
            BlockState blockState = level.getBlockState(blockPos);
            if (!isFaceFull(blockState.getCollisionShape(level, blockPos), Direction.UP)) {
                ParticleUtils.spawnParticleBelow(level, pos, random, this.particle);
            }
        }
    }
}
