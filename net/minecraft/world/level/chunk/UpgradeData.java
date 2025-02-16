package net.minecraft.world.level.chunk;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction8;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.SavedTick;
import org.slf4j.Logger;

public class UpgradeData {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final UpgradeData EMPTY = new UpgradeData(EmptyBlockGetter.INSTANCE);
    private static final String TAG_INDICES = "Indices";
    private static final Direction8[] DIRECTIONS = Direction8.values();
    private final EnumSet<Direction8> sides = EnumSet.noneOf(Direction8.class);
    private final List<SavedTick<Block>> neighborBlockTicks = Lists.newArrayList();
    private final List<SavedTick<Fluid>> neighborFluidTicks = Lists.newArrayList();
    private final int[][] index;
    static final Map<Block, UpgradeData.BlockFixer> MAP = new IdentityHashMap<>();
    static final Set<UpgradeData.BlockFixer> CHUNKY_FIXERS = Sets.newHashSet();

    private UpgradeData(LevelHeightAccessor level) {
        this.index = new int[level.getSectionsCount()][];
    }

    public UpgradeData(CompoundTag tag, LevelHeightAccessor level) {
        this(level);
        if (tag.contains("Indices", 10)) {
            CompoundTag compound = tag.getCompound("Indices");

            for (int i = 0; i < this.index.length; i++) {
                String string = String.valueOf(i);
                if (compound.contains(string, 11)) {
                    this.index[i] = compound.getIntArray(string);
                }
            }
        }

        int _int = tag.getInt("Sides");

        for (Direction8 direction8 : Direction8.values()) {
            if ((_int & 1 << direction8.ordinal()) != 0) {
                this.sides.add(direction8);
            }
        }

        loadTicks(
            tag,
            "neighbor_block_ticks",
            id -> BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(id)).or(() -> Optional.of(Blocks.AIR)),
            this.neighborBlockTicks
        );
        loadTicks(
            tag,
            "neighbor_fluid_ticks",
            id -> BuiltInRegistries.FLUID.getOptional(ResourceLocation.tryParse(id)).or(() -> Optional.of(Fluids.EMPTY)),
            this.neighborFluidTicks
        );
    }

    private UpgradeData(UpgradeData other) {
        this.sides.addAll(other.sides);
        this.neighborBlockTicks.addAll(other.neighborBlockTicks);
        this.neighborFluidTicks.addAll(other.neighborFluidTicks);
        this.index = new int[other.index.length][];

        for (int i = 0; i < other.index.length; i++) {
            int[] ints = other.index[i];
            this.index[i] = ints != null ? IntArrays.copy(ints) : null;
        }
    }

    private static <T> void loadTicks(CompoundTag tag, String identifier, Function<String, Optional<T>> valueFunction, List<SavedTick<T>> ticks) {
        if (tag.contains(identifier, 9)) {
            for (Tag tag1 : tag.getList(identifier, 10)) {
                SavedTick.loadTick((CompoundTag)tag1, valueFunction).ifPresent(ticks::add);
            }
        }
    }

    public void upgrade(LevelChunk chunk) {
        this.upgradeInside(chunk);

        for (Direction8 direction8 : DIRECTIONS) {
            upgradeSides(chunk, direction8);
        }

        Level level = chunk.getLevel();
        this.neighborBlockTicks.forEach(blockTicker -> {
            Block block = blockTicker.type() == Blocks.AIR ? level.getBlockState(blockTicker.pos()).getBlock() : blockTicker.type();
            level.scheduleTick(blockTicker.pos(), block, blockTicker.delay(), blockTicker.priority());
        });
        this.neighborFluidTicks.forEach(fluidTicker -> {
            Fluid fluid = fluidTicker.type() == Fluids.EMPTY ? level.getFluidState(fluidTicker.pos()).getType() : fluidTicker.type();
            level.scheduleTick(fluidTicker.pos(), fluid, fluidTicker.delay(), fluidTicker.priority());
        });
        CHUNKY_FIXERS.forEach(fixers -> fixers.processChunk(level));
    }

    private static void upgradeSides(LevelChunk chunk, Direction8 side) {
        Level level = chunk.getLevel();
        if (chunk.getUpgradeData().sides.remove(side)) {
            Set<Direction> directions = side.getDirections();
            int i = 0;
            int i1 = 15;
            boolean flag = directions.contains(Direction.EAST);
            boolean flag1 = directions.contains(Direction.WEST);
            boolean flag2 = directions.contains(Direction.SOUTH);
            boolean flag3 = directions.contains(Direction.NORTH);
            boolean flag4 = directions.size() == 1;
            ChunkPos pos = chunk.getPos();
            int i2 = pos.getMinBlockX() + (!flag4 || !flag3 && !flag2 ? (flag1 ? 0 : 15) : 1);
            int i3 = pos.getMinBlockX() + (!flag4 || !flag3 && !flag2 ? (flag1 ? 0 : 15) : 14);
            int i4 = pos.getMinBlockZ() + (!flag4 || !flag && !flag1 ? (flag3 ? 0 : 15) : 1);
            int i5 = pos.getMinBlockZ() + (!flag4 || !flag && !flag1 ? (flag3 ? 0 : 15) : 14);
            Direction[] directions1 = Direction.values();
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (BlockPos blockPos : BlockPos.betweenClosed(i2, level.getMinY(), i4, i3, level.getMaxY(), i5)) {
                BlockState blockState = level.getBlockState(blockPos);
                BlockState blockState1 = blockState;

                for (Direction direction : directions1) {
                    mutableBlockPos.setWithOffset(blockPos, direction);
                    blockState1 = updateState(blockState1, direction, level, blockPos, mutableBlockPos);
                }

                Block.updateOrDestroy(blockState, blockState1, level, blockPos, 18);
            }
        }
    }

    private static BlockState updateState(BlockState state, Direction direction, LevelAccessor level, BlockPos pos, BlockPos offsetPos) {
        return MAP.getOrDefault(state.getBlock(), UpgradeData.BlockFixers.DEFAULT)
            .updateShape(state, direction, level.getBlockState(offsetPos), level, pos, offsetPos);
    }

    private void upgradeInside(LevelChunk chunk) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos mutableBlockPos1 = new BlockPos.MutableBlockPos();
        ChunkPos pos = chunk.getPos();
        LevelAccessor level = chunk.getLevel();

        for (int i = 0; i < this.index.length; i++) {
            LevelChunkSection section = chunk.getSection(i);
            int[] ints = this.index[i];
            this.index[i] = null;
            if (ints != null && ints.length > 0) {
                Direction[] directions = Direction.values();
                PalettedContainer<BlockState> states = section.getStates();
                int sectionYFromSectionIndex = chunk.getSectionYFromSectionIndex(i);
                int blockPosCoord = SectionPos.sectionToBlockCoord(sectionYFromSectionIndex);

                for (int i1 : ints) {
                    int i2 = i1 & 15;
                    int i3 = i1 >> 8 & 15;
                    int i4 = i1 >> 4 & 15;
                    mutableBlockPos.set(pos.getMinBlockX() + i2, blockPosCoord + i3, pos.getMinBlockZ() + i4);
                    BlockState blockState = states.get(i1);
                    BlockState blockState1 = blockState;

                    for (Direction direction : directions) {
                        mutableBlockPos1.setWithOffset(mutableBlockPos, direction);
                        if (SectionPos.blockToSectionCoord(mutableBlockPos.getX()) == pos.x && SectionPos.blockToSectionCoord(mutableBlockPos.getZ()) == pos.z) {
                            blockState1 = updateState(blockState1, direction, level, mutableBlockPos, mutableBlockPos1);
                        }
                    }

                    Block.updateOrDestroy(blockState, blockState1, level, mutableBlockPos, 18);
                }
            }
        }

        for (int ix = 0; ix < this.index.length; ix++) {
            if (this.index[ix] != null) {
                LOGGER.warn("Discarding update data for section {} for chunk ({} {})", level.getSectionYFromSectionIndex(ix), pos.x, pos.z);
            }

            this.index[ix] = null;
        }
    }

    public boolean isEmpty() {
        for (int[] ints : this.index) {
            if (ints != null) {
                return false;
            }
        }

        return this.sides.isEmpty();
    }

    public CompoundTag write() {
        CompoundTag compoundTag = new CompoundTag();
        CompoundTag compoundTag1 = new CompoundTag();

        for (int i = 0; i < this.index.length; i++) {
            String string = String.valueOf(i);
            if (this.index[i] != null && this.index[i].length != 0) {
                compoundTag1.putIntArray(string, this.index[i]);
            }
        }

        if (!compoundTag1.isEmpty()) {
            compoundTag.put("Indices", compoundTag1);
        }

        int ix = 0;

        for (Direction8 direction8 : this.sides) {
            ix |= 1 << direction8.ordinal();
        }

        compoundTag.putByte("Sides", (byte)ix);
        if (!this.neighborBlockTicks.isEmpty()) {
            ListTag listTag = new ListTag();
            this.neighborBlockTicks.forEach(blockTicker -> listTag.add(blockTicker.save(block -> BuiltInRegistries.BLOCK.getKey(block).toString())));
            compoundTag.put("neighbor_block_ticks", listTag);
        }

        if (!this.neighborFluidTicks.isEmpty()) {
            ListTag listTag = new ListTag();
            this.neighborFluidTicks.forEach(fluidTicker -> listTag.add(fluidTicker.save(fluid -> BuiltInRegistries.FLUID.getKey(fluid).toString())));
            compoundTag.put("neighbor_fluid_ticks", listTag);
        }

        return compoundTag;
    }

    public UpgradeData copy() {
        return this == EMPTY ? EMPTY : new UpgradeData(this);
    }

    public interface BlockFixer {
        BlockState updateShape(BlockState state, Direction direction, BlockState offsetState, LevelAccessor level, BlockPos pos, BlockPos offsetPos);

        default void processChunk(LevelAccessor level) {
        }
    }

    static enum BlockFixers implements UpgradeData.BlockFixer {
        BLACKLIST(
            Blocks.OBSERVER,
            Blocks.NETHER_PORTAL,
            Blocks.WHITE_CONCRETE_POWDER,
            Blocks.ORANGE_CONCRETE_POWDER,
            Blocks.MAGENTA_CONCRETE_POWDER,
            Blocks.LIGHT_BLUE_CONCRETE_POWDER,
            Blocks.YELLOW_CONCRETE_POWDER,
            Blocks.LIME_CONCRETE_POWDER,
            Blocks.PINK_CONCRETE_POWDER,
            Blocks.GRAY_CONCRETE_POWDER,
            Blocks.LIGHT_GRAY_CONCRETE_POWDER,
            Blocks.CYAN_CONCRETE_POWDER,
            Blocks.PURPLE_CONCRETE_POWDER,
            Blocks.BLUE_CONCRETE_POWDER,
            Blocks.BROWN_CONCRETE_POWDER,
            Blocks.GREEN_CONCRETE_POWDER,
            Blocks.RED_CONCRETE_POWDER,
            Blocks.BLACK_CONCRETE_POWDER,
            Blocks.ANVIL,
            Blocks.CHIPPED_ANVIL,
            Blocks.DAMAGED_ANVIL,
            Blocks.DRAGON_EGG,
            Blocks.GRAVEL,
            Blocks.SAND,
            Blocks.RED_SAND,
            Blocks.OAK_SIGN,
            Blocks.SPRUCE_SIGN,
            Blocks.BIRCH_SIGN,
            Blocks.ACACIA_SIGN,
            Blocks.CHERRY_SIGN,
            Blocks.JUNGLE_SIGN,
            Blocks.DARK_OAK_SIGN,
            Blocks.PALE_OAK_SIGN,
            Blocks.OAK_WALL_SIGN,
            Blocks.SPRUCE_WALL_SIGN,
            Blocks.BIRCH_WALL_SIGN,
            Blocks.ACACIA_WALL_SIGN,
            Blocks.JUNGLE_WALL_SIGN,
            Blocks.DARK_OAK_WALL_SIGN,
            Blocks.PALE_OAK_WALL_SIGN,
            Blocks.OAK_HANGING_SIGN,
            Blocks.SPRUCE_HANGING_SIGN,
            Blocks.BIRCH_HANGING_SIGN,
            Blocks.ACACIA_HANGING_SIGN,
            Blocks.JUNGLE_HANGING_SIGN,
            Blocks.DARK_OAK_HANGING_SIGN,
            Blocks.PALE_OAK_HANGING_SIGN,
            Blocks.OAK_WALL_HANGING_SIGN,
            Blocks.SPRUCE_WALL_HANGING_SIGN,
            Blocks.BIRCH_WALL_HANGING_SIGN,
            Blocks.ACACIA_WALL_HANGING_SIGN,
            Blocks.JUNGLE_WALL_HANGING_SIGN,
            Blocks.DARK_OAK_WALL_HANGING_SIGN,
            Blocks.PALE_OAK_WALL_HANGING_SIGN
        ) {
            @Override
            public BlockState updateShape(BlockState state, Direction direction, BlockState offsetState, LevelAccessor level, BlockPos pos, BlockPos offsetPos) {
                return state;
            }
        },
        DEFAULT {
            @Override
            public BlockState updateShape(BlockState state, Direction direction, BlockState offsetState, LevelAccessor level, BlockPos pos, BlockPos offsetPos) {
                return state.updateShape(level, level, pos, direction, offsetPos, level.getBlockState(offsetPos), level.getRandom());
            }
        },
        CHEST(Blocks.CHEST, Blocks.TRAPPED_CHEST) {
            @Override
            public BlockState updateShape(BlockState state, Direction direction, BlockState offsetState, LevelAccessor level, BlockPos pos, BlockPos offsetPos) {
                if (offsetState.is(state.getBlock())
                    && direction.getAxis().isHorizontal()
                    && state.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                    && offsetState.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
                    Direction direction1 = state.getValue(ChestBlock.FACING);
                    if (direction.getAxis() != direction1.getAxis() && direction1 == offsetState.getValue(ChestBlock.FACING)) {
                        ChestType chestType = direction == direction1.getClockWise() ? ChestType.LEFT : ChestType.RIGHT;
                        level.setBlock(offsetPos, offsetState.setValue(ChestBlock.TYPE, chestType.getOpposite()), 18);
                        if (direction1 == Direction.NORTH || direction1 == Direction.EAST) {
                            BlockEntity blockEntity = level.getBlockEntity(pos);
                            BlockEntity blockEntity1 = level.getBlockEntity(offsetPos);
                            if (blockEntity instanceof ChestBlockEntity && blockEntity1 instanceof ChestBlockEntity) {
                                ChestBlockEntity.swapContents((ChestBlockEntity)blockEntity, (ChestBlockEntity)blockEntity1);
                            }
                        }

                        return state.setValue(ChestBlock.TYPE, chestType);
                    }
                }

                return state;
            }
        },
        LEAVES(
            true,
            Blocks.ACACIA_LEAVES,
            Blocks.CHERRY_LEAVES,
            Blocks.BIRCH_LEAVES,
            Blocks.PALE_OAK_LEAVES,
            Blocks.DARK_OAK_LEAVES,
            Blocks.JUNGLE_LEAVES,
            Blocks.OAK_LEAVES,
            Blocks.SPRUCE_LEAVES
        ) {
            private final ThreadLocal<List<ObjectSet<BlockPos>>> queue = ThreadLocal.withInitial(() -> Lists.newArrayListWithCapacity(7));

            @Override
            public BlockState updateShape(BlockState state, Direction direction, BlockState offsetState, LevelAccessor level, BlockPos pos, BlockPos offsetPos) {
                BlockState blockState = state.updateShape(level, level, pos, direction, offsetPos, level.getBlockState(offsetPos), level.getRandom());
                if (state != blockState) {
                    int distanceValue = blockState.getValue(BlockStateProperties.DISTANCE);
                    List<ObjectSet<BlockPos>> list = this.queue.get();
                    if (list.isEmpty()) {
                        for (int i = 0; i < 7; i++) {
                            list.add(new ObjectOpenHashSet<>());
                        }
                    }

                    list.get(distanceValue).add(pos.immutable());
                }

                return state;
            }

            @Override
            public void processChunk(LevelAccessor level) {
                BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
                List<ObjectSet<BlockPos>> list = this.queue.get();

                for (int i = 2; i < list.size(); i++) {
                    int i1 = i - 1;
                    ObjectSet<BlockPos> set = list.get(i1);
                    ObjectSet<BlockPos> set1 = list.get(i);

                    for (BlockPos blockPos : set) {
                        BlockState blockState = level.getBlockState(blockPos);
                        if (blockState.getValue(BlockStateProperties.DISTANCE) >= i1) {
                            level.setBlock(blockPos, blockState.setValue(BlockStateProperties.DISTANCE, Integer.valueOf(i1)), 18);
                            if (i != 7) {
                                for (Direction direction : DIRECTIONS) {
                                    mutableBlockPos.setWithOffset(blockPos, direction);
                                    BlockState blockState1 = level.getBlockState(mutableBlockPos);
                                    if (blockState1.hasProperty(BlockStateProperties.DISTANCE) && blockState.getValue(BlockStateProperties.DISTANCE) > i) {
                                        set1.add(mutableBlockPos.immutable());
                                    }
                                }
                            }
                        }
                    }
                }

                list.clear();
            }
        },
        STEM_BLOCK(Blocks.MELON_STEM, Blocks.PUMPKIN_STEM) {
            @Override
            public BlockState updateShape(BlockState state, Direction direction, BlockState offsetState, LevelAccessor level, BlockPos pos, BlockPos offsetPos) {
                if (state.getValue(StemBlock.AGE) == 7) {
                    Block block = state.is(Blocks.PUMPKIN_STEM) ? Blocks.PUMPKIN : Blocks.MELON;
                    if (offsetState.is(block)) {
                        return (state.is(Blocks.PUMPKIN_STEM) ? Blocks.ATTACHED_PUMPKIN_STEM : Blocks.ATTACHED_MELON_STEM)
                            .defaultBlockState()
                            .setValue(HorizontalDirectionalBlock.FACING, direction);
                    }
                }

                return state;
            }
        };

        public static final Direction[] DIRECTIONS = Direction.values();

        BlockFixers(final Block... blocks) {
            this(false, blocks);
        }

        BlockFixers(final boolean chunkyFixer, final Block... blocks) {
            for (Block block : blocks) {
                UpgradeData.MAP.put(block, this);
            }

            if (chunkyFixer) {
                UpgradeData.CHUNKY_FIXERS.add(this);
            }
        }
    }
}
