package net.minecraft.world.level.material;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public abstract class LavaFluid extends FlowingFluid {
    public static final float MIN_LEVEL_CUTOFF = 0.44444445F;

    @Override
    public Fluid getFlowing() {
        return Fluids.FLOWING_LAVA;
    }

    @Override
    public Fluid getSource() {
        return Fluids.LAVA;
    }

    @Override
    public Item getBucket() {
        return Items.LAVA_BUCKET;
    }

    @Override
    public void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
        BlockPos blockPos = pos.above();
        if (level.getBlockState(blockPos).isAir() && !level.getBlockState(blockPos).isSolidRender()) {
            if (random.nextInt(100) == 0) {
                double d = pos.getX() + random.nextDouble();
                double d1 = pos.getY() + 1.0;
                double d2 = pos.getZ() + random.nextDouble();
                level.addParticle(ParticleTypes.LAVA, d, d1, d2, 0.0, 0.0, 0.0);
                level.playLocalSound(
                    d, d1, d2, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false
                );
            }

            if (random.nextInt(200) == 0) {
                level.playLocalSound(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    SoundEvents.LAVA_AMBIENT,
                    SoundSource.BLOCKS,
                    0.2F + random.nextFloat() * 0.2F,
                    0.9F + random.nextFloat() * 0.15F,
                    false
                );
            }
        }
    }

    @Override
    public void randomTick(ServerLevel level, BlockPos pos, FluidState state, RandomSource random) {
        if (level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
            int randomInt = random.nextInt(3);
            if (randomInt > 0) {
                BlockPos blockPos = pos;

                for (int i = 0; i < randomInt; i++) {
                    blockPos = blockPos.offset(random.nextInt(3) - 1, 1, random.nextInt(3) - 1);
                    if (!level.isLoaded(blockPos)) {
                        return;
                    }

                    BlockState blockState = level.getBlockState(blockPos);
                    if (blockState.isAir()) {
                        if (this.hasFlammableNeighbours(level, blockPos)) {
                            // CraftBukkit start - Prevent lava putting something on fire
                            if (level.getBlockState(blockPos).getBlock() != Blocks.FIRE) {
                                if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(level, blockPos, pos).isCancelled()) {
                                    continue;
                                }
                            }
                            // CraftBukkit end
                            level.setBlockAndUpdate(blockPos, BaseFireBlock.getState(level, blockPos));
                            return;
                        }
                    } else if (blockState.blocksMotion()) {
                        return;
                    }
                }
            } else {
                for (int i1 = 0; i1 < 3; i1++) {
                    BlockPos blockPos1 = pos.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
                    if (!level.isLoaded(blockPos1)) {
                        return;
                    }

                    if (level.isEmptyBlock(blockPos1.above()) && this.isFlammable(level, blockPos1)) {
                        // CraftBukkit start - Prevent lava putting something on fire
                        BlockPos up = blockPos1.above();
                        if (level.getBlockState(up).getBlock() != Blocks.FIRE) {
                            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(level, up, pos).isCancelled()) {
                                continue;
                            }
                        }
                        // CraftBukkit end
                        level.setBlockAndUpdate(blockPos1.above(), BaseFireBlock.getState(level, blockPos1));
                    }
                }
            }
        }
    }

    private boolean hasFlammableNeighbours(LevelReader level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (this.isFlammable(level, pos.relative(direction))) {
                return true;
            }
        }

        return false;
    }

    private boolean isFlammable(LevelReader level, BlockPos pos) {
        return (!level.isInsideBuildHeight(pos.getY()) || level.hasChunkAt(pos)) && level.getBlockState(pos).ignitedByLava();
    }

    @Nullable
    @Override
    public ParticleOptions getDripParticle() {
        return ParticleTypes.DRIPPING_LAVA;
    }

    @Override
    protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        this.fizz(level, pos);
    }

    @Override
    public int getSlopeFindDistance(LevelReader level) {
        return level.dimensionType().ultraWarm() ? 4 : 2;
    }

    @Override
    public BlockState createLegacyBlock(FluidState state) {
        return Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, Integer.valueOf(getLegacyLevel(state)));
    }

    @Override
    public boolean isSame(Fluid fluid) {
        return fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA;
    }

    @Override
    public int getDropOff(LevelReader level) {
        return level.dimensionType().ultraWarm() ? 1 : 2;
    }

    @Override
    public boolean canBeReplacedWith(FluidState fluidState, BlockGetter blockReader, BlockPos pos, Fluid fluid, Direction direction) {
        return fluidState.getHeight(blockReader, pos) >= 0.44444445F && fluid.is(FluidTags.WATER);
    }

    @Override
    public int getTickDelay(LevelReader level) {
        return level.dimensionType().ultraWarm() ? level.getWorldBorder().world.purpurConfig.lavaSpeedNether : level.getWorldBorder().world.purpurConfig.lavaSpeedNotNether; // Purpur - Make lava flow speed configurable
    }

    @Override
    public int getSpreadDelay(Level level, BlockPos pos, FluidState currentState, FluidState newState) {
        int tickDelay = this.getTickDelay(level);
        if (!currentState.isEmpty()
            && !newState.isEmpty()
            && !currentState.getValue(FALLING)
            && !newState.getValue(FALLING)
            && newState.getHeight(level, pos) > currentState.getHeight(level, pos)
            && level.getRandom().nextInt(4) != 0) {
            tickDelay *= 4;
        }

        return tickDelay;
    }

    private void fizz(LevelAccessor level, BlockPos pos) {
        level.levelEvent(1501, pos, 0);
    }

    // Purpur start - Implement infinite liquids
    @Override
    protected int getRequiredSources(Level level) {
        return level.purpurConfig.lavaInfiniteRequiredSources;
    }
    // Purpur end - Implement infinite liquids

    @Override
    protected boolean canConvertToSource(ServerLevel level) {
        return level.getGameRules().getBoolean(GameRules.RULE_LAVA_SOURCE_CONVERSION);
    }

    @Override
    protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState blockState, Direction direction, FluidState fluidState) {
        if (direction == Direction.DOWN) {
            FluidState fluidState1 = level.getFluidState(pos);
            if (this.is(FluidTags.LAVA) && fluidState1.is(FluidTags.WATER)) {
                if (blockState.getBlock() instanceof LiquidBlock) {
                    // CraftBukkit start
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(level.getMinecraftWorld(), pos, Blocks.STONE.defaultBlockState(), 3)) {
                        return;
                    }
                    // CraftBukkit end
                }

                this.fizz(level, pos);
                return;
            }
        }

        super.spreadTo(level, pos, blockState, direction, fluidState);
    }

    @Override
    protected boolean isRandomlyTicking() {
        return true;
    }

    @Override
    protected float getExplosionResistance() {
        return Blocks.LAVA.getExplosionResistance(); // Paper - Get explosion resistance from actual block
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL_LAVA);
    }

    public static class Flowing extends LavaFluid {
        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }

    public static class Source extends LavaFluid {
        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }
}
