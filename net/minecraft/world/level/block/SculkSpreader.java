package net.minecraft.world.level.block;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public class SculkSpreader {
    public static final int MAX_GROWTH_RATE_RADIUS = 24;
    public static final int MAX_CHARGE = 1000;
    public static final float MAX_DECAY_FACTOR = 0.5F;
    private static final int MAX_CURSORS = 32;
    public static final int SHRIEKER_PLACEMENT_RATE = 11;
    public static final int MAX_CURSOR_DISTANCE = 1024;
    final boolean isWorldGeneration;
    private final TagKey<Block> replaceableBlocks;
    private final int growthSpawnCost;
    private final int noGrowthRadius;
    private final int chargeDecayRate;
    private final int additionalDecayRate;
    private List<SculkSpreader.ChargeCursor> cursors = new ArrayList<>();
    private static final Logger LOGGER = LogUtils.getLogger();

    public SculkSpreader(
        boolean isWorldGeneration, TagKey<Block> replaceableBlocks, int growthSpawnCoat, int noGrowthRadius, int chargeDecayRate, int additionalDecayRate
    ) {
        this.isWorldGeneration = isWorldGeneration;
        this.replaceableBlocks = replaceableBlocks;
        this.growthSpawnCost = growthSpawnCoat;
        this.noGrowthRadius = noGrowthRadius;
        this.chargeDecayRate = chargeDecayRate;
        this.additionalDecayRate = additionalDecayRate;
    }

    public static SculkSpreader createLevelSpreader() {
        return new SculkSpreader(false, BlockTags.SCULK_REPLACEABLE, 10, 4, 10, 5);
    }

    public static SculkSpreader createWorldGenSpreader() {
        return new SculkSpreader(true, BlockTags.SCULK_REPLACEABLE_WORLD_GEN, 50, 1, 5, 10);
    }

    public TagKey<Block> replaceableBlocks() {
        return this.replaceableBlocks;
    }

    public int growthSpawnCost() {
        return this.growthSpawnCost;
    }

    public int noGrowthRadius() {
        return this.noGrowthRadius;
    }

    public int chargeDecayRate() {
        return this.chargeDecayRate;
    }

    public int additionalDecayRate() {
        return this.additionalDecayRate;
    }

    public boolean isWorldGeneration() {
        return this.isWorldGeneration;
    }

    @VisibleForTesting
    public List<SculkSpreader.ChargeCursor> getCursors() {
        return this.cursors;
    }

    public void clear() {
        this.cursors.clear();
    }

    public void load(CompoundTag tag) {
        if (tag.contains("cursors", 9)) {
            this.cursors.clear();
            List<SculkSpreader.ChargeCursor> list = SculkSpreader.ChargeCursor.CODEC
                .listOf()
                .parse(new Dynamic<>(NbtOps.INSTANCE, tag.getList("cursors", 10)))
                .resultOrPartial(LOGGER::error)
                .orElseGet(ArrayList::new);
            int min = Math.min(list.size(), 32);

            for (int i = 0; i < min; i++) {
                this.addCursor(list.get(i));
            }
        }
    }

    public void save(CompoundTag tag) {
        SculkSpreader.ChargeCursor.CODEC
            .listOf()
            .encodeStart(NbtOps.INSTANCE, this.cursors)
            .resultOrPartial(LOGGER::error)
            .ifPresent(cursorsTag -> tag.put("cursors", cursorsTag));
    }

    public void addCursors(BlockPos pos, int charge) {
        while (charge > 0) {
            int min = Math.min(charge, 1000);
            this.addCursor(new SculkSpreader.ChargeCursor(pos, min));
            charge -= min;
        }
    }

    private void addCursor(SculkSpreader.ChargeCursor cursor) {
        if (this.cursors.size() < 32) {
            this.cursors.add(cursor);
        }
    }

    public void updateCursors(LevelAccessor level, BlockPos pos, RandomSource random, boolean shouldConvertBlocks) {
        if (!this.cursors.isEmpty()) {
            List<SculkSpreader.ChargeCursor> list = new ArrayList<>();
            Map<BlockPos, SculkSpreader.ChargeCursor> map = new HashMap<>();
            Object2IntMap<BlockPos> map1 = new Object2IntOpenHashMap<>();

            for (SculkSpreader.ChargeCursor chargeCursor : this.cursors) {
                if (!chargeCursor.isPosUnreasonable(pos)) {
                    chargeCursor.update(level, pos, random, this, shouldConvertBlocks);
                    if (chargeCursor.charge <= 0) {
                        level.levelEvent(3006, chargeCursor.getPos(), 0);
                    } else {
                        BlockPos pos1 = chargeCursor.getPos();
                        map1.computeInt(pos1, (cursorPos, charge) -> (charge == null ? 0 : charge) + chargeCursor.charge);
                        SculkSpreader.ChargeCursor chargeCursor1 = map.get(pos1);
                        if (chargeCursor1 == null) {
                            map.put(pos1, chargeCursor);
                            list.add(chargeCursor);
                        } else if (!this.isWorldGeneration() && chargeCursor.charge + chargeCursor1.charge <= 1000) {
                            chargeCursor1.mergeWith(chargeCursor);
                        } else {
                            list.add(chargeCursor);
                            if (chargeCursor.charge < chargeCursor1.charge) {
                                map.put(pos1, chargeCursor);
                            }
                        }
                    }
                }
            }

            for (Entry<BlockPos> entry : map1.object2IntEntrySet()) {
                BlockPos pos1 = entry.getKey();
                int intValue = entry.getIntValue();
                SculkSpreader.ChargeCursor chargeCursor2 = map.get(pos1);
                Collection<Direction> collection = chargeCursor2 == null ? null : chargeCursor2.getFacingData();
                if (intValue > 0 && collection != null) {
                    int i = (int)(Math.log1p(intValue) / 2.3F) + 1;
                    int i1 = (i << 6) + MultifaceBlock.pack(collection);
                    level.levelEvent(3006, pos1, i1);
                }
            }

            this.cursors = list;
        }
    }

    public static class ChargeCursor {
        private static final ObjectArrayList<Vec3i> NON_CORNER_NEIGHBOURS = Util.make(
            new ObjectArrayList<>(18),
            list -> BlockPos.betweenClosedStream(new BlockPos(-1, -1, -1), new BlockPos(1, 1, 1))
                .filter(
                    candidatePos -> (candidatePos.getX() == 0 || candidatePos.getY() == 0 || candidatePos.getZ() == 0) && !candidatePos.equals(BlockPos.ZERO)
                )
                .map(BlockPos::immutable)
                .forEach(list::add)
        );
        public static final int MAX_CURSOR_DECAY_DELAY = 1;
        private BlockPos pos;
        int charge;
        private int updateDelay;
        private int decayDelay;
        @Nullable
        private Set<Direction> facings;
        private static final Codec<Set<Direction>> DIRECTION_SET = Direction.CODEC
            .listOf()
            .xmap(directions -> Sets.newEnumSet(directions, Direction.class), Lists::newArrayList);
        public static final Codec<SculkSpreader.ChargeCursor> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    BlockPos.CODEC.fieldOf("pos").forGetter(SculkSpreader.ChargeCursor::getPos),
                    Codec.intRange(0, 1000).fieldOf("charge").orElse(0).forGetter(SculkSpreader.ChargeCursor::getCharge),
                    Codec.intRange(0, 1).fieldOf("decay_delay").orElse(1).forGetter(SculkSpreader.ChargeCursor::getDecayDelay),
                    Codec.intRange(0, Integer.MAX_VALUE).fieldOf("update_delay").orElse(0).forGetter(cursor -> cursor.updateDelay),
                    DIRECTION_SET.lenientOptionalFieldOf("facings").forGetter(cursor -> Optional.ofNullable(cursor.getFacingData()))
                )
                .apply(instance, SculkSpreader.ChargeCursor::new)
        );

        private ChargeCursor(BlockPos pos, int charge, int decayDelay, int updateDelay, Optional<Set<Direction>> facings) {
            this.pos = pos;
            this.charge = charge;
            this.decayDelay = decayDelay;
            this.updateDelay = updateDelay;
            this.facings = facings.orElse(null);
        }

        public ChargeCursor(BlockPos pos, int charge) {
            this(pos, charge, 1, 0, Optional.empty());
        }

        public BlockPos getPos() {
            return this.pos;
        }

        boolean isPosUnreasonable(BlockPos pos) {
            return this.pos.distChessboard(pos) > 1024;
        }

        public int getCharge() {
            return this.charge;
        }

        public int getDecayDelay() {
            return this.decayDelay;
        }

        @Nullable
        public Set<Direction> getFacingData() {
            return this.facings;
        }

        private boolean shouldUpdate(LevelAccessor level, BlockPos pos, boolean isWorldGeneration) {
            return this.charge > 0 && (isWorldGeneration || level instanceof ServerLevel serverLevel && serverLevel.shouldTickBlocksAt(pos));
        }

        public void update(LevelAccessor level, BlockPos pos, RandomSource random, SculkSpreader spreader, boolean shouldConvertBlocks) {
            if (this.shouldUpdate(level, pos, spreader.isWorldGeneration)) {
                if (this.updateDelay > 0) {
                    this.updateDelay--;
                } else {
                    BlockState blockState = level.getBlockState(this.pos);
                    SculkBehaviour blockBehaviour = getBlockBehaviour(blockState);
                    if (shouldConvertBlocks && blockBehaviour.attemptSpreadVein(level, this.pos, blockState, this.facings, spreader.isWorldGeneration())) {
                        if (blockBehaviour.canChangeBlockStateOnSpread()) {
                            blockState = level.getBlockState(this.pos);
                            blockBehaviour = getBlockBehaviour(blockState);
                        }

                        level.playSound(null, this.pos, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.BLOCKS, 1.0F, 1.0F);
                    }

                    this.charge = blockBehaviour.attemptUseCharge(this, level, pos, random, spreader, shouldConvertBlocks);
                    if (this.charge <= 0) {
                        blockBehaviour.onDischarged(level, blockState, this.pos, random);
                    } else {
                        BlockPos validMovementPos = getValidMovementPos(level, this.pos, random);
                        if (validMovementPos != null) {
                            blockBehaviour.onDischarged(level, blockState, this.pos, random);
                            this.pos = validMovementPos.immutable();
                            if (spreader.isWorldGeneration() && !this.pos.closerThan(new Vec3i(pos.getX(), this.pos.getY(), pos.getZ()), 15.0)) {
                                this.charge = 0;
                                return;
                            }

                            blockState = level.getBlockState(validMovementPos);
                        }

                        if (blockState.getBlock() instanceof SculkBehaviour) {
                            this.facings = MultifaceBlock.availableFaces(blockState);
                        }

                        this.decayDelay = blockBehaviour.updateDecayDelay(this.decayDelay);
                        this.updateDelay = blockBehaviour.getSculkSpreadDelay();
                    }
                }
            }
        }

        void mergeWith(SculkSpreader.ChargeCursor cursor) {
            this.charge = this.charge + cursor.charge;
            cursor.charge = 0;
            this.updateDelay = Math.min(this.updateDelay, cursor.updateDelay);
        }

        private static SculkBehaviour getBlockBehaviour(BlockState state) {
            return state.getBlock() instanceof SculkBehaviour sculkBehaviour ? sculkBehaviour : SculkBehaviour.DEFAULT;
        }

        private static List<Vec3i> getRandomizedNonCornerNeighbourOffsets(RandomSource random) {
            return Util.shuffledCopy(NON_CORNER_NEIGHBOURS, random);
        }

        @Nullable
        private static BlockPos getValidMovementPos(LevelAccessor level, BlockPos pos, RandomSource random) {
            BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();
            BlockPos.MutableBlockPos mutableBlockPos1 = pos.mutable();

            for (Vec3i vec3i : getRandomizedNonCornerNeighbourOffsets(random)) {
                mutableBlockPos1.setWithOffset(pos, vec3i);
                BlockState blockState = level.getBlockState(mutableBlockPos1);
                if (blockState.getBlock() instanceof SculkBehaviour && isMovementUnobstructed(level, pos, mutableBlockPos1)) {
                    mutableBlockPos.set(mutableBlockPos1);
                    if (SculkVeinBlock.hasSubstrateAccess(level, blockState, mutableBlockPos1)) {
                        break;
                    }
                }
            }

            return mutableBlockPos.equals(pos) ? null : mutableBlockPos;
        }

        private static boolean isMovementUnobstructed(LevelAccessor level, BlockPos fromPos, BlockPos toPos) {
            if (fromPos.distManhattan(toPos) == 1) {
                return true;
            } else {
                BlockPos blockPos = toPos.subtract(fromPos);
                Direction direction = Direction.fromAxisAndDirection(
                    Direction.Axis.X, blockPos.getX() < 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE
                );
                Direction direction1 = Direction.fromAxisAndDirection(
                    Direction.Axis.Y, blockPos.getY() < 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE
                );
                Direction direction2 = Direction.fromAxisAndDirection(
                    Direction.Axis.Z, blockPos.getZ() < 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE
                );
                if (blockPos.getX() == 0) {
                    return isUnobstructed(level, fromPos, direction1) || isUnobstructed(level, fromPos, direction2);
                } else {
                    return blockPos.getY() == 0
                        ? isUnobstructed(level, fromPos, direction) || isUnobstructed(level, fromPos, direction2)
                        : isUnobstructed(level, fromPos, direction) || isUnobstructed(level, fromPos, direction1);
                }
            }
        }

        private static boolean isUnobstructed(LevelAccessor level, BlockPos pos, Direction direction) {
            BlockPos blockPos = pos.relative(direction);
            return !level.getBlockState(blockPos).isFaceSturdy(level, blockPos, direction.getOpposite());
        }
    }
}
