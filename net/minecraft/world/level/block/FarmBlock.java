package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FarmBlock extends Block {
    public static final MapCodec<FarmBlock> CODEC = simpleCodec(FarmBlock::new);
    public static final IntegerProperty MOISTURE = BlockStateProperties.MOISTURE;
    protected static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 15.0, 16.0);
    public static final int MAX_MOISTURE = 7;

    @Override
    public MapCodec<FarmBlock> codec() {
        return CODEC;
    }

    protected FarmBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(MOISTURE, Integer.valueOf(0)));
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random
    ) {
        if (direction == Direction.UP && !state.canSurvive(level, pos)) {
            scheduledTickAccess.scheduleTick(pos, this, 1);
        }

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos.above());
        return !blockState.isSolid() || blockState.getBlock() instanceof FenceGateBlock || blockState.getBlock() instanceof MovingPistonBlock;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return !this.defaultBlockState().canSurvive(context.getLevel(), context.getClickedPos())
            ? Blocks.DIRT.defaultBlockState()
            : super.getStateForPlacement(context);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            turnToDirt(null, state, level, pos);
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int moistureValue = state.getValue(MOISTURE);
        if (moistureValue > 0 && level.paperConfig().tickRates.wetFarmland != 1 && (level.paperConfig().tickRates.wetFarmland < 1 || (net.minecraft.server.MinecraftServer.currentTick + pos.hashCode()) % level.paperConfig().tickRates.wetFarmland != 0)) { return; } // Paper - Configurable random tick rates for blocks
        if (moistureValue == 0 && level.paperConfig().tickRates.dryFarmland != 1 && (level.paperConfig().tickRates.dryFarmland < 1 || (net.minecraft.server.MinecraftServer.currentTick + pos.hashCode()) % level.paperConfig().tickRates.dryFarmland != 0)) { return; } // Paper - Configurable random tick rates for blocks
        if (!isNearWater(level, pos) && !level.isRainingAt(pos.above())) {
            if (moistureValue > 0) {
                org.bukkit.craftbukkit.event.CraftEventFactory.handleMoistureChangeEvent(level, pos, state.setValue(FarmBlock.MOISTURE, moistureValue - 1), 2); // CraftBukkit
            } else if (!shouldMaintainFarmland(level, pos)) {
                turnToDirt(null, state, level, pos);
            }
        } else if (moistureValue < 7) {
            org.bukkit.craftbukkit.event.CraftEventFactory.handleMoistureChangeEvent(level, pos, state.setValue(FarmBlock.MOISTURE, 7), 2); // CraftBukkit
        }
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        super.fallOn(level, state, pos, entity, fallDistance); // CraftBukkit - moved here as game rules / events shouldn't affect fall damage.
        if (level instanceof ServerLevel serverLevel
            && (serverLevel.purpurConfig.farmlandTrampleHeight >= 0D ? fallDistance >= serverLevel.purpurConfig.farmlandTrampleHeight : level.random.nextFloat() < fallDistance - 0.5F) // // Purpur - Configurable farmland trample height
            && entity instanceof LivingEntity
            && (entity instanceof Player || serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))
            && entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight() > 0.512F) {
                // CraftBukkit start - Interact soil
                org.bukkit.event.Cancellable cancellable;
                if (entity instanceof Player) {
                    cancellable = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent((Player) entity, org.bukkit.event.block.Action.PHYSICAL, pos, null, null, null);
                } else {
                    cancellable = new org.bukkit.event.entity.EntityInteractEvent(entity.getBukkitEntity(), level.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()));
                    level.getCraftServer().getPluginManager().callEvent((org.bukkit.event.entity.EntityInteractEvent) cancellable);
                }

                if (cancellable.isCancelled()) {
                    return;
                }

                if (level.purpurConfig.farmlandTramplingDisabled) return; // Purpur - Farmland trampling changes
                if (level.purpurConfig.farmlandTramplingOnlyPlayers && !(entity instanceof Player)) return; // Purpur - Farmland trampling changes
                
                // Purpur start - Ability to re-add farmland mechanics from Alpha
                if (level.purpurConfig.farmlandAlpha) {
                    Block block = level.getBlockState(pos.below()).getBlock();
                    if (block instanceof FenceBlock || block instanceof WallBlock) {
                        return;
                    }
                }
                // Purpur end - Ability to re-add farmland mechanics from Alpha

                // Purpur start - Farmland trampling changes
                if (level.purpurConfig.farmlandTramplingFeatherFalling) {
                    java.util.Iterator<net.minecraft.world.item.ItemStack> armor = ((LivingEntity) entity).getArmorSlots().iterator();
                    if (armor.hasNext() && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.FEATHER_FALLING, armor.next()) >= (int) entity.fallDistance) {
                        return;
                    }
                }
                // Purpur end - Farmland trampling changes

                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, pos, Blocks.DIRT.defaultBlockState())) {
                    return;
                }
                // CraftBukkit end
            turnToDirt(entity, state, level, pos);
        }

        // super.fallOn(level, state, pos, entity, fallDistance); // CraftBukkit - moved up
    }

    public static void turnToDirt(@Nullable Entity entity, BlockState state, Level level, BlockPos pos) {
        // CraftBukkit start
        if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(level, pos, Blocks.DIRT.defaultBlockState()).isCancelled()) {
            return;
        }
        // CraftBukkit end
        BlockState blockState = pushEntitiesUp(state, Blocks.DIRT.defaultBlockState(), level, pos);
        level.setBlockAndUpdate(pos, blockState);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(entity, blockState));
    }

    private static boolean shouldMaintainFarmland(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos.above()).is(BlockTags.MAINTAINS_FARMLAND);
    }

    private static boolean isNearWater(LevelReader level, BlockPos pos) {
        // Paper start - Perf: remove abstract block iteration
        int xOff = pos.getX();
        int yOff = pos.getY();
        int zOff = pos.getZ();
        for (int dz = -4; dz <= 4; ++dz) {
            int z = dz + zOff;
            for (int dx = -4; dx <= 4; ++dx) {
                int x = xOff + dx;
                for (int dy = 0; dy <= 1; ++dy) {
                    int y = dy + yOff;
                    net.minecraft.world.level.chunk.LevelChunk chunk = (net.minecraft.world.level.chunk.LevelChunk)level.getChunk(x >> 4, z >> 4);
                    net.minecraft.world.level.material.FluidState fluid = chunk.getBlockStateFinal(x, y, z).getFluidState();
                    if (fluid.is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }

        return ((ServerLevel) level).purpurConfig.farmlandGetsMoistFromBelow && level.getFluidState(pos.relative(Direction.DOWN)).is(FluidTags.WATER); // Purpur - Allow soil to moisten from water directly under it
        // Paper end - Perf: remove abstract block iteration
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MOISTURE);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}
