package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TurtleEggBlock extends Block {
    public static final MapCodec<TurtleEggBlock> CODEC = simpleCodec(TurtleEggBlock::new);
    public static final int MAX_HATCH_LEVEL = 2;
    public static final int MIN_EGGS = 1;
    public static final int MAX_EGGS = 4;
    private static final VoxelShape ONE_EGG_AABB = Block.box(3.0, 0.0, 3.0, 12.0, 7.0, 12.0);
    private static final VoxelShape MULTIPLE_EGGS_AABB = Block.box(1.0, 0.0, 1.0, 15.0, 7.0, 15.0);
    public static final IntegerProperty HATCH = BlockStateProperties.HATCH;
    public static final IntegerProperty EGGS = BlockStateProperties.EGGS;

    @Override
    public MapCodec<TurtleEggBlock> codec() {
        return CODEC;
    }

    public TurtleEggBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(HATCH, Integer.valueOf(0)).setValue(EGGS, Integer.valueOf(1)));
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!entity.isSteppingCarefully()) {
            this.destroyEgg(level, state, pos, entity, 100);
        }

        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if (!(entity instanceof Zombie)) {
            this.destroyEgg(level, state, pos, entity, 3);
        }

        super.fallOn(level, state, pos, entity, fallDistance);
    }

    private void destroyEgg(Level level, BlockState state, BlockPos pos, Entity entity, int chance) {
        if (state.is(Blocks.TURTLE_EGG)
            && level instanceof ServerLevel serverLevel
            && this.canDestroyEgg(serverLevel, entity)
            && level.random.nextInt(chance) == 0) {
            // CraftBukkit start - Step on eggs
            org.bukkit.event.Cancellable cancellable;
            if (entity instanceof Player) {
                cancellable = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent((Player) entity, org.bukkit.event.block.Action.PHYSICAL, pos, null, null, null);
            } else {
                cancellable = new org.bukkit.event.entity.EntityInteractEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos));
                level.getCraftServer().getPluginManager().callEvent((org.bukkit.event.entity.EntityInteractEvent) cancellable);
            }

            if (cancellable.isCancelled()) {
                return;
            }
            // CraftBukkit end
            this.decreaseEggs(serverLevel, pos, state);
        }
    }

    public void decreaseEggs(Level level, BlockPos pos, BlockState state) {
        level.playSound(null, pos, SoundEvents.TURTLE_EGG_BREAK, SoundSource.BLOCKS, 0.7F, 0.9F + level.random.nextFloat() * 0.2F);
        int eggsValue = state.getValue(EGGS);
        if (eggsValue <= 1) {
            level.destroyBlock(pos, false);
        } else {
            level.setBlock(pos, state.setValue(EGGS, Integer.valueOf(eggsValue - 1)), 2);
            level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(state));
            level.levelEvent(2001, pos, Block.getId(state));
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (this.shouldUpdateHatchLevel(level) && onSand(level, pos)) {
            int hatchValue = state.getValue(HATCH);
            if (hatchValue < 2) {
                // CraftBukkit start - Call BlockGrowEvent
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, pos, state.setValue(TurtleEggBlock.HATCH, hatchValue + 1), 2)) {
                    return;
                }
                // CraftBukkit end
                level.playSound(null, pos, SoundEvents.TURTLE_EGG_CRACK, SoundSource.BLOCKS, 0.7F, 0.9F + random.nextFloat() * 0.2F);
                // level.setBlock(pos, state.setValue(HATCH, Integer.valueOf(hatchValue + 1)), 2); // CraftBukkit - handled above
                level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
            } else {
                // CraftBukkit start - Call BlockFadeEvent
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(level, pos, Blocks.AIR.defaultBlockState()).isCancelled()) {
                    return;
                }
                // CraftBukkit end
                level.playSound(null, pos, SoundEvents.TURTLE_EGG_HATCH, SoundSource.BLOCKS, 0.7F, 0.9F + random.nextFloat() * 0.2F);
                level.removeBlock(pos, false);
                level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(state));

                for (int i = 0; i < state.getValue(EGGS); i++) {
                    level.levelEvent(2001, pos, Block.getId(state));
                    Turtle turtle = EntityType.TURTLE.create(level, EntitySpawnReason.BREEDING);
                    if (turtle != null) {
                        turtle.setAge(-24000);
                        turtle.setHomePos(pos);
                        turtle.moveTo(pos.getX() + 0.3 + i * 0.2, pos.getY(), pos.getZ() + 0.3, 0.0F, 0.0F);
                        level.addFreshEntity(turtle, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.EGG); // CraftBukkit
                    }
                }
            }
        }
    }

    public static boolean onSand(BlockGetter level, BlockPos pos) {
        return isSand(level, pos.below());
    }

    public static boolean isSand(BlockGetter reader, BlockPos pos) {
        return reader.getBlockState(pos).is(BlockTags.SAND);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (onSand(level, pos) && !level.isClientSide) {
            level.levelEvent(2012, pos, 15);
        }
    }

    private boolean shouldUpdateHatchLevel(Level level) {
        float timeOfDay = level.getTimeOfDay(1.0F);
        return timeOfDay < 0.69 && timeOfDay > 0.65 || level.random.nextInt(level.purpurConfig.turtleEggsRandomTickCrackChance) == 0; // Purpur - Turtle eggs random tick crack chance
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity te, ItemStack stack, boolean includeDrops, boolean dropExp) { // Paper - fix drops not preventing stats/food exhaustion
        super.playerDestroy(level, player, pos, state, te, stack, includeDrops, dropExp); // Paper - fix drops not preventing stats/food exhaustion
        this.decreaseEggs(level, pos, state);
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        return !useContext.isSecondaryUseActive() && useContext.getItemInHand().is(this.asItem()) && state.getValue(EGGS) < 4
            || super.canBeReplaced(state, useContext);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockState = context.getLevel().getBlockState(context.getClickedPos());
        return blockState.is(this)
            ? blockState.setValue(EGGS, Integer.valueOf(Math.min(4, blockState.getValue(EGGS) + 1)))
            : super.getStateForPlacement(context);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(EGGS) > 1 ? MULTIPLE_EGGS_AABB : ONE_EGG_AABB;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HATCH, EGGS);
    }

    private boolean canDestroyEgg(ServerLevel level, Entity entity) {
        // Purpur start - Add turtle egg block options
        if (entity instanceof Turtle || entity instanceof Bat) {
            return false;
        }
        if (level.purpurConfig.turtleEggsBreakFromExpOrbs && entity instanceof net.minecraft.world.entity.ExperienceOrb) {
            return true;
        }
        if (level.purpurConfig.turtleEggsBreakFromItems && entity instanceof net.minecraft.world.entity.item.ItemEntity) {
            return true;
        }
        if (level.purpurConfig.turtleEggsBreakFromMinecarts && entity instanceof net.minecraft.world.entity.vehicle.AbstractMinecart) {
            return true;
        }
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        // Purpur start - Option to disable turtle egg trampling with feather falling
        if (level.purpurConfig.turtleEggsTramplingFeatherFalling) {
            java.util.Iterator<ItemStack> armor = ((LivingEntity) entity).getArmorSlots().iterator();
            return !armor.hasNext() || net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.FEATHER_FALLING, armor.next()) < (int) entity.fallDistance;
        }
        // Purpur end - Option to disable turtle egg trampling with feather falling
        if (entity instanceof Player) return true;

        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        // Purpur end - Add turtle egg block options
    }
}
