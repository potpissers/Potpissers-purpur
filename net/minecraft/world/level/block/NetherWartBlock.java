package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class NetherWartBlock extends BushBlock implements BonemealableBlock { // Purpur - bonemealable netherwart
    public static final MapCodec<NetherWartBlock> CODEC = simpleCodec(NetherWartBlock::new);
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    private static final VoxelShape[] SHAPE_BY_AGE = new VoxelShape[]{
        Block.box(0.0, 0.0, 0.0, 16.0, 5.0, 16.0),
        Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0),
        Block.box(0.0, 0.0, 0.0, 16.0, 11.0, 16.0),
        Block.box(0.0, 0.0, 0.0, 16.0, 14.0, 16.0)
    };

    @Override
    public MapCodec<NetherWartBlock> codec() {
        return CODEC;
    }

    protected NetherWartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, Integer.valueOf(0)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_BY_AGE[state.getValue(AGE)];
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.SOUL_SAND);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < 3;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int ageValue = state.getValue(AGE);
        if (ageValue < 3 && random.nextFloat() < (level.spigotConfig.wartModifier / (100.0f * 10))) { // Spigot - SPIGOT-7159: Better modifier resolution
            state = state.setValue(AGE, Integer.valueOf(ageValue + 1));
            org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, pos, state, 2); // CraftBukkit
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(Items.NETHER_WART);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    // Purpur start - Ability for hoe to replant nether warts
    @Override
    public void playerDestroy(net.minecraft.world.level.Level world, net.minecraft.world.entity.player.Player player, BlockPos pos, BlockState state, @javax.annotation.Nullable net.minecraft.world.level.block.entity.BlockEntity blockEntity, ItemStack itemInHand, boolean includeDrops, boolean dropExp) {
        if (world.purpurConfig.hoeReplantsNetherWarts && itemInHand.getItem() instanceof net.minecraft.world.item.HoeItem) {
            super.playerDestroyAndReplant(world, player, pos, state, blockEntity, itemInHand, Items.NETHER_WART);
        } else {
            super.playerDestroy(world, player, pos, state, blockEntity, itemInHand, includeDrops, dropExp);
        }
    }
    // Purpur end - Ability for hoe to replant nether warts

    // Purpur start - bonemealable netherwart
    @Override
    public boolean isValidBonemealTarget(final net.minecraft.world.level.LevelReader world, final BlockPos pos, final BlockState state) {
        return ((net.minecraft.world.level.Level) world).purpurConfig.netherWartAffectedByBonemeal && state.getValue(NetherWartBlock.AGE) < 3;
    }

    @Override
    public boolean isBonemealSuccess(net.minecraft.world.level.Level world, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel world, RandomSource random, BlockPos pos, BlockState state) {
        int i = Math.min(3, state.getValue(NetherWartBlock.AGE) + 1);
        state = state.setValue(NetherWartBlock.AGE, i);
        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(world, pos, state, 2); // CraftBukkit
    }
    // Purpur end - bonemealable netherwart
}
