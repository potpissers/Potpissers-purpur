package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AnvilBlock extends FallingBlock {
    public static final MapCodec<AnvilBlock> CODEC = simpleCodec(AnvilBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape BASE = Block.box(2.0, 0.0, 2.0, 14.0, 4.0, 14.0);
    private static final VoxelShape X_LEG1 = Block.box(3.0, 4.0, 4.0, 13.0, 5.0, 12.0);
    private static final VoxelShape X_LEG2 = Block.box(4.0, 5.0, 6.0, 12.0, 10.0, 10.0);
    private static final VoxelShape X_TOP = Block.box(0.0, 10.0, 3.0, 16.0, 16.0, 13.0);
    private static final VoxelShape Z_LEG1 = Block.box(4.0, 4.0, 3.0, 12.0, 5.0, 13.0);
    private static final VoxelShape Z_LEG2 = Block.box(6.0, 5.0, 4.0, 10.0, 10.0, 12.0);
    private static final VoxelShape Z_TOP = Block.box(3.0, 10.0, 0.0, 13.0, 16.0, 16.0);
    private static final VoxelShape X_AXIS_AABB = Shapes.or(BASE, X_LEG1, X_LEG2, X_TOP);
    private static final VoxelShape Z_AXIS_AABB = Shapes.or(BASE, Z_LEG1, Z_LEG2, Z_TOP);
    private static final Component CONTAINER_TITLE = Component.translatable("container.repair");
    private static final float FALL_DAMAGE_PER_DISTANCE = 2.0F;
    private static final int FALL_DAMAGE_MAX = 40;

    @Override
    public MapCodec<AnvilBlock> codec() {
        return CODEC;
    }

    public AnvilBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getClockWise());
    }

    // Purpur start - Anvil repair/damage options
    @Override
    protected net.minecraft.world.InteractionResult useItemOn(final net.minecraft.world.item.ItemStack stack, final BlockState state, final Level world, final BlockPos pos, final Player player, final net.minecraft.world.InteractionHand hand, final BlockHitResult hit) {
        if (world.purpurConfig.anvilRepairIngotsAmount > 0 && stack.is(net.minecraft.world.item.Items.IRON_INGOT)) {
            if (stack.getCount() < world.purpurConfig.anvilRepairIngotsAmount) {
                // not enough iron ingots, play "error" sound and consume
                world.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_HIT, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                return net.minecraft.world.InteractionResult.CONSUME;
            }
            if (state.is(Blocks.DAMAGED_ANVIL)) {
                world.setBlock(pos, Blocks.CHIPPED_ANVIL.defaultBlockState().setValue(FACING, state.getValue(FACING)), 3);
            } else if (state.is(Blocks.CHIPPED_ANVIL)) {
                world.setBlock(pos, Blocks.ANVIL.defaultBlockState().setValue(FACING, state.getValue(FACING)), 3);
            } else if (state.is(Blocks.ANVIL)) {
                // anvil is already fully repaired, play "error" sound and consume
                world.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_HIT, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                return net.minecraft.world.InteractionResult.CONSUME;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(world.purpurConfig.anvilRepairIngotsAmount);
            }
            world.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        if (world.purpurConfig.anvilDamageObsidianAmount > 0 && stack.is(net.minecraft.world.item.Items.OBSIDIAN)) {
            if (stack.getCount() < world.purpurConfig.anvilDamageObsidianAmount) {
                // not enough obsidian, play "error" sound and consume
                world.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_HIT, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                return net.minecraft.world.InteractionResult.CONSUME;
            }
            if (state.is(Blocks.DAMAGED_ANVIL)) {
                world.destroyBlock(pos, false);
            } else if (state.is(Blocks.CHIPPED_ANVIL)) {
                world.setBlock(pos, Blocks.DAMAGED_ANVIL.defaultBlockState().setValue(FACING, state.getValue(FACING)), 3);
            } else if (state.is(Blocks.ANVIL)) {
                world.setBlock(pos, Blocks.CHIPPED_ANVIL.defaultBlockState().setValue(FACING, state.getValue(FACING)), 3);
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(world.purpurConfig.anvilDamageObsidianAmount);
            }
            world.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        return net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND;
    }
    // Purpur end - Anvil repair/damage options

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            if (player.openMenu(state.getMenuProvider(level, pos)).isPresent()) { // Paper - Fix InventoryOpenEvent cancellation
            player.awardStat(Stats.INTERACT_WITH_ANVIL);
            } // Paper - Fix InventoryOpenEvent cancellation
        }

        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
            (containerId, inventory, player) -> new AnvilMenu(containerId, inventory, ContainerLevelAccess.create(level, pos)), CONTAINER_TITLE
        );
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction direction = state.getValue(FACING);
        return direction.getAxis() == Direction.Axis.X ? X_AXIS_AABB : Z_AXIS_AABB;
    }

    @Override
    protected void falling(FallingBlockEntity fallingEntity) {
        fallingEntity.setHurtsEntities(2.0F, 40);
    }

    @Override
    public void onLand(Level level, BlockPos pos, BlockState state, BlockState replaceableState, FallingBlockEntity fallingBlock) {
        if (!fallingBlock.isSilent()) {
            level.levelEvent(1031, pos, 0);
        }
    }

    @Override
    public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity fallingBlock) {
        if (!fallingBlock.isSilent()) {
            level.levelEvent(1029, pos, 0);
        }
    }

    @Override
    public DamageSource getFallDamageSource(Entity entity) {
        return entity.damageSources().anvil(entity);
    }

    @Nullable
    public static BlockState damage(BlockState state) {
        if (state.is(Blocks.ANVIL)) {
            return Blocks.CHIPPED_ANVIL.defaultBlockState().setValue(FACING, state.getValue(FACING));
        } else {
            return state.is(Blocks.CHIPPED_ANVIL) ? Blocks.DAMAGED_ANVIL.defaultBlockState().setValue(FACING, state.getValue(FACING)) : null;
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public int getDustColor(BlockState state, BlockGetter reader, BlockPos pos) {
        return state.getMapColor(reader, pos).col;
    }
}
