package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.redstone.Orientation;

public class SpongeBlock extends Block {
    public static final MapCodec<SpongeBlock> CODEC = simpleCodec(SpongeBlock::new);
    public static final int MAX_DEPTH = 6;
    public static final int MAX_COUNT = 64;
    private static final Direction[] ALL_DIRECTIONS = Direction.values();

    @Override
    public MapCodec<SpongeBlock> codec() {
        return CODEC;
    }

    protected SpongeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!oldState.is(state.getBlock())) {
            this.tryAbsorbWater(level, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        this.tryAbsorbWater(level, pos);
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
    }

    protected void tryAbsorbWater(Level level, BlockPos pos) {
        if (this.removeWaterBreadthFirstSearch(level, pos)) {
            level.setBlock(pos, Blocks.WET_SPONGE.defaultBlockState(), 2);
            level.playSound(null, pos, SoundEvents.SPONGE_ABSORB, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private boolean removeWaterBreadthFirstSearch(Level level, BlockPos pos) {
        org.bukkit.craftbukkit.util.BlockStateListPopulator blockList = new org.bukkit.craftbukkit.util.BlockStateListPopulator(level); // CraftBukkit - Use BlockStateListPopulator
        BlockPos.breadthFirstTraversal(
                pos,
                level.purpurConfig.spongeAbsorptionRadius, // Purpur - Configurable sponge absorption
                level.purpurConfig.spongeAbsorptionArea, // Purpur - Configurable sponge absorption
                (validPos, queueAdder) -> {
                    for (Direction direction : ALL_DIRECTIONS) {
                        queueAdder.accept(validPos.relative(direction));
                    }
                },
                blockPos -> {
                    if (blockPos.equals(pos)) {
                        return BlockPos.TraversalNodeStatus.ACCEPT;
                    } else {
                        // CraftBukkit start
                        BlockState blockState = blockList.getBlockState(blockPos);
                        FluidState fluidState = blockList.getFluidState(blockPos);
                        // CraftBukkit end
                        if (!fluidState.is(FluidTags.WATER) && (!level.purpurConfig.spongeAbsorbsLava || !fluidState.is(FluidTags.LAVA)) && (!level.purpurConfig.spongeAbsorbsWaterFromMud || !blockState.is(Blocks.MUD))) { // Purpur - Option for sponges to work on lava and mud
                            return BlockPos.TraversalNodeStatus.SKIP;
                        } else if (blockState.getBlock() instanceof BucketPickup bucketPickup
                            && !bucketPickup.pickupBlock(null, blockList, blockPos, blockState).isEmpty()) { // CraftBukkit
                            return BlockPos.TraversalNodeStatus.ACCEPT;
                        } else {
                            if (blockState.getBlock() instanceof LiquidBlock) {
                                blockList.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3); // CraftBukkit
                            // Purpur start - Option for sponges to work on lava and mud
                            } else if (blockState.is(Blocks.MUD)) {
                                blockList.setBlock(blockPos, Blocks.CLAY.defaultBlockState(), 3);
                            // Purpur end - Option for sponges to work on lava and mud
                            } else {
                                if (!blockState.is(Blocks.KELP)
                                    && !blockState.is(Blocks.KELP_PLANT)
                                    && !blockState.is(Blocks.SEAGRASS)
                                    && !blockState.is(Blocks.TALL_SEAGRASS)) {
                                    return BlockPos.TraversalNodeStatus.SKIP;
                                }

                                // CraftBukkit start
                                // BlockEntity blockEntity = blockState.hasBlockEntity() ? level.getBlockEntity(blockPos) : null;
                                // dropResources(blockState, level, blockPos, blockEntity);
                                // level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3);
                                blockList.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3);
                                // CraftBukkit end
                            }

                            return BlockPos.TraversalNodeStatus.ACCEPT;
                        }
                    }
                }
            );
        // CraftBukkit start
        java.util.List<org.bukkit.craftbukkit.block.CraftBlockState> blocks = blockList.getList(); // Is a clone
        if (!blocks.isEmpty()) {
            final org.bukkit.block.Block sponge = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);

            org.bukkit.event.block.SpongeAbsorbEvent event = new org.bukkit.event.block.SpongeAbsorbEvent(sponge, (java.util.List<org.bukkit.block.BlockState>) (java.util.List) blocks);
            if (!event.callEvent()) {
                return false;
            }

            for (org.bukkit.craftbukkit.block.CraftBlockState block : blocks) {
                BlockPos blockPos = block.getPosition();
                BlockState state = level.getBlockState(blockPos);
                FluidState fluid = level.getFluidState(blockPos);

                if (fluid.is(FluidTags.WATER)) {
                    if (state.getBlock() instanceof BucketPickup bucketPickup && !bucketPickup.pickupBlock(null, blockList, blockPos, state).isEmpty()) {
                        // NOP
                    } else if (state.getBlock() instanceof LiquidBlock) {
                        // NOP
                    } else if (state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT) || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)) {
                        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(blockPos) : null;

                        // Paper start - Fix SpongeAbsortEvent handling
                        if (block.getHandle().isAir()) {
                        dropResources(state, level, blockPos, blockEntity);
                        }
                        // Paper end - Fix SpongeAbsortEvent handling
                    }
                }
                level.setBlock(blockPos, block.getHandle(), block.getFlag());
            }

            return true;
        }
        return false;
        // CraftBukkit end
    }
}
