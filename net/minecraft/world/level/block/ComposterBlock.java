package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ComposterBlock extends Block implements WorldlyContainerHolder {
    public static final MapCodec<ComposterBlock> CODEC = simpleCodec(ComposterBlock::new);
    public static final int READY = 8;
    public static final int MIN_LEVEL = 0;
    public static final int MAX_LEVEL = 7;
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL_COMPOSTER;
    public static final Object2FloatMap<ItemLike> COMPOSTABLES = new Object2FloatOpenHashMap<>();
    private static final int AABB_SIDE_THICKNESS = 2;
    private static final VoxelShape OUTER_SHAPE = Shapes.block();
    private static final VoxelShape[] SHAPES = Util.make(new VoxelShape[9], shapes -> {
        for (int i = 0; i < 8; i++) {
            shapes[i] = Shapes.join(OUTER_SHAPE, Block.box(2.0, Math.max(2, 1 + i * 2), 2.0, 14.0, 16.0, 14.0), BooleanOp.ONLY_FIRST);
        }

        shapes[8] = shapes[7];
    });

    @Override
    public MapCodec<ComposterBlock> codec() {
        return CODEC;
    }

    public static void bootStrap() {
        COMPOSTABLES.defaultReturnValue(-1.0F);
        float f = 0.3F;
        float f1 = 0.5F;
        float f2 = 0.65F;
        float f3 = 0.85F;
        float f4 = 1.0F;
        add(0.3F, Items.JUNGLE_LEAVES);
        add(0.3F, Items.OAK_LEAVES);
        add(0.3F, Items.SPRUCE_LEAVES);
        add(0.3F, Items.DARK_OAK_LEAVES);
        add(0.3F, Items.PALE_OAK_LEAVES);
        add(0.3F, Items.ACACIA_LEAVES);
        add(0.3F, Items.CHERRY_LEAVES);
        add(0.3F, Items.BIRCH_LEAVES);
        add(0.3F, Items.AZALEA_LEAVES);
        add(0.3F, Items.MANGROVE_LEAVES);
        add(0.3F, Items.OAK_SAPLING);
        add(0.3F, Items.SPRUCE_SAPLING);
        add(0.3F, Items.BIRCH_SAPLING);
        add(0.3F, Items.JUNGLE_SAPLING);
        add(0.3F, Items.ACACIA_SAPLING);
        add(0.3F, Items.CHERRY_SAPLING);
        add(0.3F, Items.DARK_OAK_SAPLING);
        add(0.3F, Items.PALE_OAK_SAPLING);
        add(0.3F, Items.MANGROVE_PROPAGULE);
        add(0.3F, Items.BEETROOT_SEEDS);
        add(0.3F, Items.DRIED_KELP);
        add(0.3F, Items.SHORT_GRASS);
        add(0.3F, Items.KELP);
        add(0.3F, Items.MELON_SEEDS);
        add(0.3F, Items.PUMPKIN_SEEDS);
        add(0.3F, Items.SEAGRASS);
        add(0.3F, Items.SWEET_BERRIES);
        add(0.3F, Items.GLOW_BERRIES);
        add(0.3F, Items.WHEAT_SEEDS);
        add(0.3F, Items.MOSS_CARPET);
        add(0.3F, Items.PALE_MOSS_CARPET);
        add(0.3F, Items.PALE_HANGING_MOSS);
        add(0.3F, Items.PINK_PETALS);
        add(0.3F, Items.SMALL_DRIPLEAF);
        add(0.3F, Items.HANGING_ROOTS);
        add(0.3F, Items.MANGROVE_ROOTS);
        add(0.3F, Items.TORCHFLOWER_SEEDS);
        add(0.3F, Items.PITCHER_POD);
        add(0.5F, Items.DRIED_KELP_BLOCK);
        add(0.5F, Items.TALL_GRASS);
        add(0.5F, Items.FLOWERING_AZALEA_LEAVES);
        add(0.5F, Items.CACTUS);
        add(0.5F, Items.SUGAR_CANE);
        add(0.5F, Items.VINE);
        add(0.5F, Items.NETHER_SPROUTS);
        add(0.5F, Items.WEEPING_VINES);
        add(0.5F, Items.TWISTING_VINES);
        add(0.5F, Items.MELON_SLICE);
        add(0.5F, Items.GLOW_LICHEN);
        add(0.65F, Items.SEA_PICKLE);
        add(0.65F, Items.LILY_PAD);
        add(0.65F, Items.PUMPKIN);
        add(0.65F, Items.CARVED_PUMPKIN);
        add(0.65F, Items.MELON);
        add(0.65F, Items.APPLE);
        add(0.65F, Items.BEETROOT);
        add(0.65F, Items.CARROT);
        add(0.65F, Items.COCOA_BEANS);
        add(0.65F, Items.POTATO);
        add(0.65F, Items.WHEAT);
        add(0.65F, Items.BROWN_MUSHROOM);
        add(0.65F, Items.RED_MUSHROOM);
        add(0.65F, Items.MUSHROOM_STEM);
        add(0.65F, Items.CRIMSON_FUNGUS);
        add(0.65F, Items.WARPED_FUNGUS);
        add(0.65F, Items.NETHER_WART);
        add(0.65F, Items.CRIMSON_ROOTS);
        add(0.65F, Items.WARPED_ROOTS);
        add(0.65F, Items.SHROOMLIGHT);
        add(0.65F, Items.DANDELION);
        add(0.65F, Items.POPPY);
        add(0.65F, Items.BLUE_ORCHID);
        add(0.65F, Items.ALLIUM);
        add(0.65F, Items.AZURE_BLUET);
        add(0.65F, Items.RED_TULIP);
        add(0.65F, Items.ORANGE_TULIP);
        add(0.65F, Items.WHITE_TULIP);
        add(0.65F, Items.PINK_TULIP);
        add(0.65F, Items.OXEYE_DAISY);
        add(0.65F, Items.CORNFLOWER);
        add(0.65F, Items.LILY_OF_THE_VALLEY);
        add(0.65F, Items.WITHER_ROSE);
        add(0.65F, Items.OPEN_EYEBLOSSOM);
        add(0.65F, Items.CLOSED_EYEBLOSSOM);
        add(0.65F, Items.FERN);
        add(0.65F, Items.SUNFLOWER);
        add(0.65F, Items.LILAC);
        add(0.65F, Items.ROSE_BUSH);
        add(0.65F, Items.PEONY);
        add(0.65F, Items.LARGE_FERN);
        add(0.65F, Items.SPORE_BLOSSOM);
        add(0.65F, Items.AZALEA);
        add(0.65F, Items.MOSS_BLOCK);
        add(0.65F, Items.PALE_MOSS_BLOCK);
        add(0.65F, Items.BIG_DRIPLEAF);
        add(0.85F, Items.HAY_BLOCK);
        add(0.85F, Items.BROWN_MUSHROOM_BLOCK);
        add(0.85F, Items.RED_MUSHROOM_BLOCK);
        add(0.85F, Items.NETHER_WART_BLOCK);
        add(0.85F, Items.WARPED_WART_BLOCK);
        add(0.85F, Items.FLOWERING_AZALEA);
        add(0.85F, Items.BREAD);
        add(0.85F, Items.BAKED_POTATO);
        add(0.85F, Items.COOKIE);
        add(0.85F, Items.TORCHFLOWER);
        add(0.85F, Items.PITCHER_PLANT);
        add(1.0F, Items.CAKE);
        add(1.0F, Items.PUMPKIN_PIE);
    }

    private static void add(float chance, ItemLike item) {
        COMPOSTABLES.put(item.asItem(), chance);
    }

    public ComposterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, Integer.valueOf(0)));
    }

    public static void handleFill(Level level, BlockPos pos, boolean success) {
        BlockState blockState = level.getBlockState(pos);
        level.playLocalSound(pos, success ? SoundEvents.COMPOSTER_FILL_SUCCESS : SoundEvents.COMPOSTER_FILL, SoundSource.BLOCKS, 1.0F, 1.0F, false);
        double d = blockState.getShape(level, pos).max(Direction.Axis.Y, 0.5, 0.5) + 0.03125;
        double d1 = 0.13125F;
        double d2 = 0.7375F;
        RandomSource random = level.getRandom();

        for (int i = 0; i < 10; i++) {
            double d3 = random.nextGaussian() * 0.02;
            double d4 = random.nextGaussian() * 0.02;
            double d5 = random.nextGaussian() * 0.02;
            level.addParticle(
                ParticleTypes.COMPOSTER,
                pos.getX() + 0.13125F + 0.7375F * random.nextFloat(),
                pos.getY() + d + random.nextFloat() * (1.0 - d),
                pos.getZ() + 0.13125F + 0.7375F * random.nextFloat(),
                d3,
                d4,
                d5
            );
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LEVEL)];
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return OUTER_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[0];
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (state.getValue(LEVEL) == 7) {
            level.scheduleTick(pos, state.getBlock(), 20);
        }
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        int levelValue = state.getValue(LEVEL);
        if (levelValue < 8 && COMPOSTABLES.containsKey(stack.getItem())) {
            // Purpur start - sneak to bulk process composter
            BlockState newState = process(levelValue, player, state, level, pos, stack);
            if (newState == null) {
                return InteractionResult.PASS;
            }
            if (level.purpurConfig.composterBulkProcess && player.isShiftKeyDown() && newState != state) {
                BlockState oldState;
                int oldCount, newCount, oldLevel, newLevel;
                do {
                    oldState = newState;
                    oldCount = stack.getCount();
                    oldLevel = oldState.getValue(ComposterBlock.LEVEL);
                    newState = process(oldLevel, player, oldState, level, pos, stack);
                    if (newState == null) {
                        return InteractionResult.PASS;
                    }
                    newCount = stack.getCount();
                    newLevel = newState.getValue(ComposterBlock.LEVEL);
                } while (newCount > 0 && (newCount != oldCount || newLevel != oldLevel || newState != oldState));
            }
            // Purpur end - Sneak to bulk process composter

            return InteractionResult.SUCCESS;
        } else {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
    }

    // Purpur start - sneak to bulk process composter
    private static @Nullable BlockState process(int levelValue, Player player, BlockState state, Level level, BlockPos pos, ItemStack stack) {
        if (levelValue < 7 && !level.isClientSide) {
            BlockState iblockdata1 = ComposterBlock.addItem(player, state, level, pos, stack);
            // Paper start - handle cancelled events
            if (iblockdata1 == null) {
                return null;
            }
            // Paper end

            level.levelEvent(1500, pos, state != iblockdata1 ? 1 : 0);
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            stack.consume(1, player);
            return iblockdata1;
        }
        return state;
    }
    // Purpur end - Sneak to bulk process composter

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        int levelValue = state.getValue(LEVEL);
        if (levelValue == 8) {
            extractProduce(player, state, level, pos);
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    public static BlockState insertItem(Entity entity, BlockState state, ServerLevel level, ItemStack stack, BlockPos pos) {
        int levelValue = state.getValue(LEVEL);
        if (levelValue < 7 && COMPOSTABLES.containsKey(stack.getItem())) {
            // CraftBukkit start
            double rand = level.getRandom().nextDouble();
            BlockState blockState = null; // Paper
            if (false && (state == blockState || !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, pos, blockState))) { // Paper - move event call into addItem
                return state;
            }
            blockState = ComposterBlock.addItem(entity, state, level, pos, stack, rand);
            // Paper start - handle cancelled events
            if (blockState == null) {
                return state;
            }
            // Paper end
            // CraftBukkit end
            stack.shrink(1);
            return blockState;
        } else {
            return state;
        }
    }

    public static BlockState extractProduce(Entity entity, BlockState state, Level level, BlockPos pos) {
        // CraftBukkit start
        if (entity != null && !(entity instanceof Player)) {
            BlockState emptyState = ComposterBlock.empty(entity, state, org.bukkit.craftbukkit.util.DummyGeneratorAccess.INSTANCE, pos);
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, pos, emptyState)) {
                return state;
            }
        }
        // CraftBukkit end
        if (!level.isClientSide) {
            Vec3 vec3 = Vec3.atLowerCornerWithOffset(pos, 0.5, 1.01, 0.5).offsetRandom(level.random, 0.7F);
            ItemEntity itemEntity = new ItemEntity(level, vec3.x(), vec3.y(), vec3.z(), new ItemStack(Items.BONE_MEAL));
            itemEntity.setDefaultPickUpDelay();
            level.addFreshEntity(itemEntity);
        }

        BlockState blockState = empty(entity, state, level, pos);
        level.playSound(null, pos, SoundEvents.COMPOSTER_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        return blockState;
    }

    static BlockState empty(@Nullable Entity entity, BlockState state, LevelAccessor level, BlockPos pos) {
        BlockState blockState = state.setValue(LEVEL, Integer.valueOf(0));
        level.setBlock(pos, blockState, 3);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(entity, blockState));
        return blockState;
    }

    @Nullable // Paper
    static BlockState addItem(@Nullable Entity entity, BlockState state, LevelAccessor level, BlockPos pos, ItemStack stack) {
        // CraftBukkit start
        return ComposterBlock.addItem(entity, state, level, pos, stack, level.getRandom().nextDouble());
    }
    @Nullable // Paper - make it nullable
    static BlockState addItem(@Nullable Entity entity, BlockState state, LevelAccessor level, BlockPos pos, ItemStack stack, double rand) {
        int levelValue = state.getValue(LEVEL);
        float _float = COMPOSTABLES.getFloat(stack.getItem());
        // Paper start - Add CompostItemEvent and EntityCompostItemEvent
        boolean willRaiseLevel = !((levelValue != 0 || _float <= 0.0F) && rand >= (double) _float);
        final io.papermc.paper.event.block.CompostItemEvent event;
        if (entity == null) {
            event = new io.papermc.paper.event.block.CompostItemEvent(org.bukkit.craftbukkit.block.CraftBlock.at(level, pos), stack.getBukkitStack(), willRaiseLevel);
        } else {
            event = new io.papermc.paper.event.entity.EntityCompostItemEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos), stack.getBukkitStack(), willRaiseLevel);
        }
        if (!event.callEvent()) { // check for cancellation of entity event (non entity event can't be cancelled cause of hoppers)
            return null;
        }
        willRaiseLevel = event.willRaiseLevel();

        if (!willRaiseLevel) {
            // Paper end - Add CompostItemEvent and EntityCompostItemEvent
            return state;
        } else {
            int i = levelValue + 1;
            BlockState blockState = state.setValue(LEVEL, Integer.valueOf(i));
            // Paper start - move the EntityChangeBlockEvent here to avoid conflict later for the compost events
            if (entity != null && !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, pos, blockState)) {
                return null;
            }
            // Paper end
            level.setBlock(pos, blockState, 3);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(entity, blockState));
            if (i == 7) {
                level.scheduleTick(pos, state.getBlock(), 20);
            }

            return blockState;
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LEVEL) == 7) {
            level.setBlock(pos, state.cycle(LEVEL), 3);
            level.playSound(null, pos, SoundEvents.COMPOSTER_READY, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState blockState, Level level, BlockPos pos) {
        return blockState.getValue(LEVEL);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public WorldlyContainer getContainer(BlockState state, LevelAccessor level, BlockPos pos) {
        int levelValue = state.getValue(LEVEL);
        if (levelValue == 8) {
            return new ComposterBlock.OutputContainer(state, level, pos, new ItemStack(Items.BONE_MEAL));
        } else {
            return (WorldlyContainer)(levelValue < 7 ? new ComposterBlock.InputContainer(state, level, pos) : new ComposterBlock.EmptyContainer(level, pos)); // CraftBukkit - empty generatoraccess, blockposition
        }
    }

    public static class EmptyContainer extends SimpleContainer implements WorldlyContainer {
        public EmptyContainer(LevelAccessor levelAccessor, BlockPos blockPos) { // CraftBukkit
            super(0);
            this.bukkitOwner = new org.bukkit.craftbukkit.inventory.CraftBlockInventoryHolder(levelAccessor, blockPos, this); // CraftBukkit
        }

        @Override
        public int[] getSlotsForFace(Direction side) {
            return new int[0];
        }

        @Override
        public boolean canPlaceItemThroughFace(int index, ItemStack itemStack, @Nullable Direction direction) {
            return false;
        }

        @Override
        public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
            return false;
        }
    }

    public static class InputContainer extends SimpleContainer implements WorldlyContainer {
        private final BlockState state;
        private final LevelAccessor level;
        private final BlockPos pos;
        private boolean changed;

        public InputContainer(BlockState state, LevelAccessor level, BlockPos pos) {
            super(1);
            this.bukkitOwner = new org.bukkit.craftbukkit.inventory.CraftBlockInventoryHolder(level, pos, this); // CraftBukkit
            this.state = state;
            this.level = level;
            this.pos = pos;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int[] getSlotsForFace(Direction side) {
            return side == Direction.UP ? new int[]{0} : new int[0];
        }

        @Override
        public boolean canPlaceItemThroughFace(int index, ItemStack itemStack, @Nullable Direction direction) {
            return !this.changed && direction == Direction.UP && ComposterBlock.COMPOSTABLES.containsKey(itemStack.getItem());
        }

        @Override
        public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
            return false;
        }

        @Override
        public void setChanged() {
            ItemStack item = this.getItem(0);
            if (!item.isEmpty()) {
                this.changed = true;
                BlockState blockState = ComposterBlock.addItem(null, this.state, this.level, this.pos, item);
                // Paper start - Add CompostItemEvent and EntityCompostItemEvent
                if (blockState == null) {
                    return;
                }
                // Paper end - Add CompostItemEvent and EntityCompostItemEvent
                this.level.levelEvent(1500, this.pos, blockState != this.state ? 1 : 0);
                this.removeItemNoUpdate(0);
            }
        }
    }

    public static class OutputContainer extends SimpleContainer implements WorldlyContainer {
        private final BlockState state;
        private final LevelAccessor level;
        private final BlockPos pos;
        private boolean changed;

        public OutputContainer(BlockState state, LevelAccessor level, BlockPos pos, ItemStack stack) {
            super(stack);
            this.bukkitOwner = new org.bukkit.craftbukkit.inventory.CraftBlockInventoryHolder(level, pos, this); // Paper
            this.state = state;
            this.level = level;
            this.pos = pos;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int[] getSlotsForFace(Direction side) {
            return side == Direction.DOWN ? new int[]{0} : new int[0];
        }

        @Override
        public boolean canPlaceItemThroughFace(int index, ItemStack itemStack, @Nullable Direction direction) {
            return false;
        }

        @Override
        public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
            return !this.changed && direction == Direction.DOWN && stack.is(Items.BONE_MEAL);
        }

        @Override
        public void setChanged() {
            // CraftBukkit start - allow putting items back (eg cancelled InventoryMoveItemEvent)
            if (this.isEmpty()) {
            ComposterBlock.empty(null, this.state, this.level, this.pos);
            this.changed = true;
            } else {
                this.level.setBlock(this.pos, this.state, 3);
                this.changed = false;
            }
            // CraftBukkit end
        }
    }
}
