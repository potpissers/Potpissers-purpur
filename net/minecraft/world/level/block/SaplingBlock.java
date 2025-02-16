package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SaplingBlock extends BushBlock implements BonemealableBlock {
    public static final MapCodec<SaplingBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(TreeGrower.CODEC.fieldOf("tree").forGetter(saplingBlock -> saplingBlock.treeGrower), propertiesCodec())
            .apply(instance, SaplingBlock::new)
    );
    public static final IntegerProperty STAGE = BlockStateProperties.STAGE;
    protected static final float AABB_OFFSET = 6.0F;
    protected static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 12.0, 14.0);
    protected final TreeGrower treeGrower;
    public static org.bukkit.TreeType treeType; // CraftBukkit

    @Override
    public MapCodec<? extends SaplingBlock> codec() {
        return CODEC;
    }

    protected SaplingBlock(TreeGrower treeGrower, BlockBehaviour.Properties properties) {
        super(properties);
        this.treeGrower = treeGrower;
        this.registerDefaultState(this.stateDefinition.any().setValue(STAGE, Integer.valueOf(0)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getMaxLocalRawBrightness(pos.above()) >= 9 && random.nextFloat() < (level.spigotConfig.saplingModifier / (100.0f * 7))) { // Spigot - SPIGOT-7159: Better modifier resolution
            this.advanceTree(level, pos, state, random);
        }
    }

    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.cycle(STAGE), 4);
        } else {
            // CraftBukkit start
            if (level.captureTreeGeneration) {
                this.treeGrower.growTree(level, level.getChunkSource().getGenerator(), pos, state, random);
            } else {
                level.captureTreeGeneration = true;
                this.treeGrower.growTree(level, level.getChunkSource().getGenerator(), pos, state, random);
                level.captureTreeGeneration = false;
                if (!level.capturedBlockStates.isEmpty()) {
                    org.bukkit.TreeType treeType = SaplingBlock.treeType;
                    SaplingBlock.treeType = null;
                    org.bukkit.Location location = org.bukkit.craftbukkit.util.CraftLocation.toBukkit(pos, level.getWorld());
                    java.util.List<org.bukkit.block.BlockState> blocks = new java.util.ArrayList<>(level.capturedBlockStates.values());
                    level.capturedBlockStates.clear();
                    org.bukkit.event.world.StructureGrowEvent event = null;
                    if (treeType != null) {
                        event = new org.bukkit.event.world.StructureGrowEvent(location, treeType, false, null, blocks);
                        org.bukkit.Bukkit.getPluginManager().callEvent(event);
                    }
                    if (event == null || !event.isCancelled()) {
                        for (org.bukkit.block.BlockState blockstate : blocks) {
                            org.bukkit.craftbukkit.block.CapturedBlockState.setBlockState(blockstate);
                            level.checkCapturedTreeStateForObserverNotify(pos, (org.bukkit.craftbukkit.block.CraftBlockState) blockstate); // Paper - notify observers even if grow failed
                        }
                    }
                }
            }
            // CraftBukkit end
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return level.random.nextFloat() < 0.45;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        this.advanceTree(level, pos, state, random);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }
}
