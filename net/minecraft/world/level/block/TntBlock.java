package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

public class TntBlock extends Block {
    public static final MapCodec<TntBlock> CODEC = simpleCodec(TntBlock::new);
    public static final BooleanProperty UNSTABLE = BlockStateProperties.UNSTABLE;

    @Override
    public MapCodec<TntBlock> codec() {
        return CODEC;
    }

    public TntBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(UNSTABLE, Boolean.valueOf(false)));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!oldState.is(state.getBlock())) {
            if (level.hasNeighborSignal(pos) && org.bukkit.craftbukkit.event.CraftEventFactory.callTNTPrimeEvent(level, pos, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.REDSTONE, null, null)) { // CraftBukkit - TNTPrimeEvent
                // Paper start - TNTPrimeEvent
                org.bukkit.block.Block tntBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
                if (!new com.destroystokyo.paper.event.block.TNTPrimeEvent(tntBlock, com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.REDSTONE, null).callEvent()) {
                    return;
                }
                // Paper end - TNTPrimeEvent
                explode(level, pos);
                level.removeBlock(pos, false);
            }
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.hasNeighborSignal(pos) && org.bukkit.craftbukkit.event.CraftEventFactory.callTNTPrimeEvent(level, pos, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.REDSTONE, null, null)) { // CraftBukkit - TNTPrimeEvent
            // Paper start - TNTPrimeEvent
            org.bukkit.block.Block tntBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
            if (!new com.destroystokyo.paper.event.block.TNTPrimeEvent(tntBlock, com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.REDSTONE, null).callEvent()) {
                return;
            }
            // Paper end - TNTPrimeEvent
            explode(level, pos);
            level.removeBlock(pos, false);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative() && state.getValue(UNSTABLE) && org.bukkit.craftbukkit.event.CraftEventFactory.callTNTPrimeEvent(level, pos, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.BLOCK_BREAK, player, null)) { // CraftBukkit - TNTPrimeEvent
            explode(level, pos);
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void wasExploded(ServerLevel level, BlockPos pos, Explosion explosion) {
        // Paper start - TNTPrimeEvent
        org.bukkit.block.Block tntBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
        org.bukkit.entity.Entity source = explosion.getDirectSourceEntity() != null ? explosion.getDirectSourceEntity().getBukkitEntity() : null;
        if (!new com.destroystokyo.paper.event.block.TNTPrimeEvent(tntBlock, com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.EXPLOSION, source).callEvent()) {
            return;
        }
        // Paper end - TNTPrimeEvent
        PrimedTnt primedTnt = new PrimedTnt(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, explosion.getIndirectSourceEntity());
        int fuse = primedTnt.getFuse();
        primedTnt.setFuse((short)(level.random.nextInt(fuse / 4) + fuse / 8));
        level.addFreshEntity(primedTnt);
    }

    public static void explode(Level level, BlockPos pos) {
        explode(level, pos, null);
    }

    private static void explode(Level level, BlockPos pos, @Nullable LivingEntity entity) {
        if (!level.isClientSide) {
            PrimedTnt primedTnt = new PrimedTnt(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, entity);
            level.addFreshEntity(primedTnt);
            level.playSound(null, primedTnt.getX(), primedTnt.getY(), primedTnt.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(entity, GameEvent.PRIME_FUSE, pos);
        }
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (!stack.is(Items.FLINT_AND_STEEL) && !stack.is(Items.FIRE_CHARGE)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        } else {
            // CraftBukkit start - TNTPrimeEvent
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callTNTPrimeEvent(level, pos, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.PLAYER, player, null)) {
                return InteractionResult.CONSUME;
            }
            // CraftBukkit end
            // Paper start - TNTPrimeEvent
            org.bukkit.block.Block tntBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
            if (!new com.destroystokyo.paper.event.block.TNTPrimeEvent(tntBlock, com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.ITEM, player.getBukkitEntity()).callEvent()) {
                return InteractionResult.FAIL;
            }
            // Paper end - TNTPrimeEvent
            explode(level, pos, player);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
            Item item = stack.getItem();
            if (stack.is(Items.FLINT_AND_STEEL)) {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            } else {
                stack.consume(1, player);
            }

            player.awardStat(Stats.ITEM_USED.get(item));
            return InteractionResult.SUCCESS;
        }
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (level instanceof ServerLevel serverLevel) {
            BlockPos blockPos = hit.getBlockPos();
            Entity owner = projectile.getOwner();
            if (projectile.isOnFire() && projectile.mayInteract(serverLevel, blockPos)) {
                // CraftBukkit start
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(projectile, blockPos, state.getFluidState().createLegacyBlock()) || !org.bukkit.craftbukkit.event.CraftEventFactory.callTNTPrimeEvent(level, blockPos, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.PROJECTILE, projectile, null)) { // Paper - fix wrong block state
                    return;
                }
                // CraftBukkit end
                // Paper start - TNTPrimeEvent
                org.bukkit.block.Block tntBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, blockPos);
                if (!new com.destroystokyo.paper.event.block.TNTPrimeEvent(tntBlock, com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.PROJECTILE, projectile.getBukkitEntity()).callEvent()) {
                    return;
                }
                // Paper end - TNTPrimeEvent
                explode(level, blockPos, owner instanceof LivingEntity ? (LivingEntity)owner : null);
                level.removeBlock(blockPos, false);
            }
        }
    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UNSTABLE);
    }
}
