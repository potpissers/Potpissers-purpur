package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.IdMapper;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
// CraftBukkit start
import org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer;
import org.bukkit.craftbukkit.persistence.CraftPersistentDataTypeRegistry;
// CraftBukkit end

public class StructureTemplate {
    public static final String PALETTE_TAG = "palette";
    public static final String PALETTE_LIST_TAG = "palettes";
    public static final String ENTITIES_TAG = "entities";
    public static final String BLOCKS_TAG = "blocks";
    public static final String BLOCK_TAG_POS = "pos";
    public static final String BLOCK_TAG_STATE = "state";
    public static final String BLOCK_TAG_NBT = "nbt";
    public static final String ENTITY_TAG_POS = "pos";
    public static final String ENTITY_TAG_BLOCKPOS = "blockPos";
    public static final String ENTITY_TAG_NBT = "nbt";
    public static final String SIZE_TAG = "size";
    public final List<StructureTemplate.Palette> palettes = Lists.newArrayList();
    public final List<StructureTemplate.StructureEntityInfo> entityInfoList = Lists.newArrayList();
    private Vec3i size = Vec3i.ZERO;
    private String author = "?";
    // CraftBukkit start - data containers
    private static final CraftPersistentDataTypeRegistry DATA_TYPE_REGISTRY = new CraftPersistentDataTypeRegistry();
    public CraftPersistentDataContainer persistentDataContainer = new CraftPersistentDataContainer(StructureTemplate.DATA_TYPE_REGISTRY);
    // CraftBukkit end

    public Vec3i getSize() {
        return this.size;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getAuthor() {
        return this.author;
    }

    public void fillFromWorld(Level level, BlockPos pos, Vec3i size, boolean withEntities, @Nullable Block toIgnore) {
        if (size.getX() >= 1 && size.getY() >= 1 && size.getZ() >= 1) {
            BlockPos blockPos = pos.offset(size).offset(-1, -1, -1);
            List<StructureTemplate.StructureBlockInfo> list = Lists.newArrayList();
            List<StructureTemplate.StructureBlockInfo> list1 = Lists.newArrayList();
            List<StructureTemplate.StructureBlockInfo> list2 = Lists.newArrayList();
            BlockPos blockPos1 = new BlockPos(
                Math.min(pos.getX(), blockPos.getX()), Math.min(pos.getY(), blockPos.getY()), Math.min(pos.getZ(), blockPos.getZ())
            );
            BlockPos blockPos2 = new BlockPos(
                Math.max(pos.getX(), blockPos.getX()), Math.max(pos.getY(), blockPos.getY()), Math.max(pos.getZ(), blockPos.getZ())
            );
            this.size = size;

            for (BlockPos blockPos3 : BlockPos.betweenClosed(blockPos1, blockPos2)) {
                BlockPos blockPos4 = blockPos3.subtract(blockPos1);
                BlockState blockState = level.getBlockState(blockPos3);
                if (toIgnore == null || !blockState.is(toIgnore)) {
                    BlockEntity blockEntity = level.getBlockEntity(blockPos3);
                    StructureTemplate.StructureBlockInfo structureBlockInfo;
                    if (blockEntity != null) {
                        structureBlockInfo = new StructureTemplate.StructureBlockInfo(blockPos4, blockState, blockEntity.saveWithId(level.registryAccess()));
                    } else {
                        structureBlockInfo = new StructureTemplate.StructureBlockInfo(blockPos4, blockState, null);
                    }

                    addToLists(structureBlockInfo, list, list1, list2);
                }
            }

            List<StructureTemplate.StructureBlockInfo> list3 = buildInfoList(list, list1, list2);
            this.palettes.clear();
            this.palettes.add(new StructureTemplate.Palette(list3));
            if (withEntities) {
                this.fillEntityList(level, blockPos1, blockPos2);
            } else {
                this.entityInfoList.clear();
            }
        }
    }

    private static void addToLists(
        StructureTemplate.StructureBlockInfo blockInfo,
        List<StructureTemplate.StructureBlockInfo> normalBlocks,
        List<StructureTemplate.StructureBlockInfo> blocksWithNbt,
        List<StructureTemplate.StructureBlockInfo> blocksWithSpecialShape
    ) {
        if (blockInfo.nbt != null) {
            blocksWithNbt.add(blockInfo);
        } else if (!blockInfo.state.getBlock().hasDynamicShape() && blockInfo.state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            normalBlocks.add(blockInfo);
        } else {
            blocksWithSpecialShape.add(blockInfo);
        }
    }

    private static List<StructureTemplate.StructureBlockInfo> buildInfoList(
        List<StructureTemplate.StructureBlockInfo> normalBlocks,
        List<StructureTemplate.StructureBlockInfo> blocksWithNbt,
        List<StructureTemplate.StructureBlockInfo> blocksWithSpecialShape
    ) {
        Comparator<StructureTemplate.StructureBlockInfo> comparator = Comparator.<StructureTemplate.StructureBlockInfo>comparingInt(
                structureBlockInfo -> structureBlockInfo.pos.getY()
            )
            .thenComparingInt(structureBlockInfo -> structureBlockInfo.pos.getX())
            .thenComparingInt(structureBlockInfo -> structureBlockInfo.pos.getZ());
        normalBlocks.sort(comparator);
        blocksWithSpecialShape.sort(comparator);
        blocksWithNbt.sort(comparator);
        List<StructureTemplate.StructureBlockInfo> list = Lists.newArrayList();
        list.addAll(normalBlocks);
        list.addAll(blocksWithSpecialShape);
        list.addAll(blocksWithNbt);
        return list;
    }

    private void fillEntityList(Level level, BlockPos startPos, BlockPos endPos) {
        List<Entity> entitiesOfClass = level.getEntitiesOfClass(
            Entity.class, AABB.encapsulatingFullBlocks(startPos, endPos), entity1 -> !(entity1 instanceof Player)
        );
        this.entityInfoList.clear();

        for (Entity entity : entitiesOfClass) {
            Vec3 vec3 = new Vec3(entity.getX() - startPos.getX(), entity.getY() - startPos.getY(), entity.getZ() - startPos.getZ());
            CompoundTag compoundTag = new CompoundTag();
            entity.save(compoundTag);
            BlockPos blockPos;
            if (entity instanceof Painting) {
                blockPos = ((Painting)entity).getPos().subtract(startPos);
            } else {
                blockPos = BlockPos.containing(vec3);
            }

            this.entityInfoList.add(new StructureTemplate.StructureEntityInfo(vec3, blockPos, compoundTag.copy()));
        }
    }

    public List<StructureTemplate.StructureBlockInfo> filterBlocks(BlockPos pos, StructurePlaceSettings settings, Block block) {
        return this.filterBlocks(pos, settings, block, true);
    }

    public List<StructureTemplate.JigsawBlockInfo> getJigsaws(BlockPos pos, Rotation rotation) {
        if (this.palettes.isEmpty()) {
            return new ArrayList<>();
        } else {
            StructurePlaceSettings structurePlaceSettings = new StructurePlaceSettings().setRotation(rotation);
            List<StructureTemplate.JigsawBlockInfo> list = structurePlaceSettings.getRandomPalette(this.palettes, pos).jigsaws();
            List<StructureTemplate.JigsawBlockInfo> list1 = new ArrayList<>(list.size());

            for (StructureTemplate.JigsawBlockInfo jigsawBlockInfo : list) {
                StructureTemplate.StructureBlockInfo structureBlockInfo = jigsawBlockInfo.info;
                list1.add(
                    jigsawBlockInfo.withInfo(
                        new StructureTemplate.StructureBlockInfo(
                            calculateRelativePosition(structurePlaceSettings, structureBlockInfo.pos()).offset(pos),
                            structureBlockInfo.state.rotate(structurePlaceSettings.getRotation()),
                            structureBlockInfo.nbt
                        )
                    )
                );
            }

            return list1;
        }
    }

    public ObjectArrayList<StructureTemplate.StructureBlockInfo> filterBlocks(
        BlockPos pos, StructurePlaceSettings settings, Block block, boolean relativePosition
    ) {
        ObjectArrayList<StructureTemplate.StructureBlockInfo> list = new ObjectArrayList<>();
        BoundingBox boundingBox = settings.getBoundingBox();
        if (this.palettes.isEmpty()) {
            return list;
        } else {
            for (StructureTemplate.StructureBlockInfo structureBlockInfo : settings.getRandomPalette(this.palettes, pos).blocks(block)) {
                BlockPos blockPos = relativePosition ? calculateRelativePosition(settings, structureBlockInfo.pos).offset(pos) : structureBlockInfo.pos;
                if (boundingBox == null || boundingBox.isInside(blockPos)) {
                    list.add(
                        new StructureTemplate.StructureBlockInfo(blockPos, structureBlockInfo.state.rotate(settings.getRotation()), structureBlockInfo.nbt)
                    );
                }
            }

            return list;
        }
    }

    public BlockPos calculateConnectedPosition(StructurePlaceSettings decorator, BlockPos start, StructurePlaceSettings settings, BlockPos end) {
        BlockPos blockPos = calculateRelativePosition(decorator, start);
        BlockPos blockPos1 = calculateRelativePosition(settings, end);
        return blockPos.subtract(blockPos1);
    }

    public static BlockPos calculateRelativePosition(StructurePlaceSettings decorator, BlockPos pos) {
        return transform(pos, decorator.getMirror(), decorator.getRotation(), decorator.getRotationPivot());
    }

    public boolean placeInWorld(ServerLevelAccessor serverLevel, BlockPos offset, BlockPos pos, StructurePlaceSettings settings, RandomSource random, int flags) {
        if (this.palettes.isEmpty()) {
            return false;
        } else {
            // CraftBukkit start
            // We only want the TransformerGeneratorAccess at certain locations because in here are many "block update" calls that shouldn't be transformed
            ServerLevelAccessor wrappedAccess = serverLevel;
            org.bukkit.craftbukkit.util.CraftStructureTransformer structureTransformer = null;
            if (wrappedAccess instanceof org.bukkit.craftbukkit.util.TransformerGeneratorAccess transformerAccess) {
                serverLevel = transformerAccess.getHandle();
                structureTransformer = transformerAccess.getStructureTransformer();
                // The structureTransformer is not needed if we can not transform blocks therefore we can save a little bit of performance doing this
                if (structureTransformer != null && !structureTransformer.canTransformBlocks()) {
                    structureTransformer = null;
                }
            }
            // CraftBukkit end
            List<StructureTemplate.StructureBlockInfo> list = settings.getRandomPalette(this.palettes, offset).blocks();
            if ((!list.isEmpty() || !settings.isIgnoreEntities() && !this.entityInfoList.isEmpty())
                && this.size.getX() >= 1
                && this.size.getY() >= 1
                && this.size.getZ() >= 1) {
                BoundingBox boundingBox = settings.getBoundingBox();
                List<BlockPos> list1 = Lists.newArrayListWithCapacity(settings.shouldApplyWaterlogging() ? list.size() : 0);
                List<BlockPos> list2 = Lists.newArrayListWithCapacity(settings.shouldApplyWaterlogging() ? list.size() : 0);
                List<Pair<BlockPos, CompoundTag>> list3 = Lists.newArrayListWithCapacity(list.size());
                int i = Integer.MAX_VALUE;
                int i1 = Integer.MAX_VALUE;
                int i2 = Integer.MAX_VALUE;
                int i3 = Integer.MIN_VALUE;
                int i4 = Integer.MIN_VALUE;
                int i5 = Integer.MIN_VALUE;

                for (StructureTemplate.StructureBlockInfo structureBlockInfo : processBlockInfos(serverLevel, offset, pos, settings, list)) {
                    BlockPos blockPos = structureBlockInfo.pos;
                    if (boundingBox == null || boundingBox.isInside(blockPos)) {
                        FluidState fluidState = settings.shouldApplyWaterlogging() ? serverLevel.getFluidState(blockPos) : null;
                        BlockState blockState = structureBlockInfo.state.mirror(settings.getMirror()).rotate(settings.getRotation());
                        if (structureBlockInfo.nbt != null) {
                            BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos);
                            // Paper start - Fix NBT pieces overriding a block entity during worldgen deadlock
                            if (!(serverLevel instanceof net.minecraft.world.level.WorldGenLevel)) {
                                Clearable.tryClear(blockEntity);
                            }
                            // Paper end - Fix NBT pieces overriding a block entity during worldgen deadlock
                            serverLevel.setBlock(blockPos, Blocks.BARRIER.defaultBlockState(), 20);
                        }

                        // CraftBukkit start
                        if (structureTransformer != null) {
                            org.bukkit.craftbukkit.block.CraftBlockState craftBlockState = (org.bukkit.craftbukkit.block.CraftBlockState) org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(serverLevel, blockPos, blockState, null);
                            if (structureBlockInfo.nbt != null && craftBlockState instanceof org.bukkit.craftbukkit.block.CraftBlockEntityState<?> entityState) {
                                entityState.loadData(structureBlockInfo.nbt);
                                if (craftBlockState instanceof org.bukkit.craftbukkit.block.CraftLootable<?> craftLootable) {
                                    craftLootable.setSeed(random.nextLong());
                                }
                            }
                            craftBlockState = structureTransformer.transformCraftState(craftBlockState);
                            blockState = craftBlockState.getHandle();
                            structureBlockInfo = new StructureTemplate.StructureBlockInfo(blockPos, blockState, (craftBlockState instanceof org.bukkit.craftbukkit.block.CraftBlockEntityState<?> craftBlockEntityState ? craftBlockEntityState.getSnapshotNBT() : null));
                        }
                        // CraftBukkit end

                        if (serverLevel.setBlock(blockPos, blockState, flags)) {
                            i = Math.min(i, blockPos.getX());
                            i1 = Math.min(i1, blockPos.getY());
                            i2 = Math.min(i2, blockPos.getZ());
                            i3 = Math.max(i3, blockPos.getX());
                            i4 = Math.max(i4, blockPos.getY());
                            i5 = Math.max(i5, blockPos.getZ());
                            list3.add(Pair.of(blockPos, structureBlockInfo.nbt));
                            if (structureBlockInfo.nbt != null) {
                                BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos);
                                if (blockEntity != null) {
                                    if (structureTransformer == null && blockEntity instanceof RandomizableContainer) { // CraftBukkit - only process if don't have a transformer access (Was already set above) - SPIGOT-7520: Use structureTransformer as check, so that it is the same as above
                                        structureBlockInfo.nbt.putLong("LootTableSeed", random.nextLong());
                                    }

                                    blockEntity.loadWithComponents(structureBlockInfo.nbt, serverLevel.registryAccess());
                                }
                            }

                            if (fluidState != null) {
                                if (blockState.getFluidState().isSource()) {
                                    list2.add(blockPos);
                                } else if (blockState.getBlock() instanceof LiquidBlockContainer) {
                                    ((LiquidBlockContainer)blockState.getBlock()).placeLiquid(serverLevel, blockPos, blockState, fluidState);
                                    if (!fluidState.isSource()) {
                                        list1.add(blockPos);
                                    }
                                }
                            }
                        }
                    }
                }

                boolean flag = true;
                Direction[] directions = new Direction[]{Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

                while (flag && !list1.isEmpty()) {
                    flag = false;
                    Iterator<BlockPos> iterator = list1.iterator();

                    while (iterator.hasNext()) {
                        BlockPos blockPos1 = iterator.next();
                        FluidState fluidState1 = serverLevel.getFluidState(blockPos1);

                        for (int i6 = 0; i6 < directions.length && !fluidState1.isSource(); i6++) {
                            BlockPos blockPos2 = blockPos1.relative(directions[i6]);
                            FluidState fluidState2 = serverLevel.getFluidState(blockPos2);
                            if (fluidState2.isSource() && !list2.contains(blockPos2)) {
                                fluidState1 = fluidState2;
                            }
                        }

                        if (fluidState1.isSource()) {
                            BlockState blockState1 = serverLevel.getBlockState(blockPos1);
                            Block block = blockState1.getBlock();
                            if (block instanceof LiquidBlockContainer) {
                                ((LiquidBlockContainer)block).placeLiquid(serverLevel, blockPos1, blockState1, fluidState1);
                                flag = true;
                                iterator.remove();
                            }
                        }
                    }
                }

                if (i <= i3) {
                    if (!settings.getKnownShape()) {
                        DiscreteVoxelShape discreteVoxelShape = new BitSetDiscreteVoxelShape(i3 - i + 1, i4 - i1 + 1, i5 - i2 + 1);
                        int i7 = i;
                        int i8 = i1;
                        int i6x = i2;

                        for (Pair<BlockPos, CompoundTag> pair : list3) {
                            BlockPos blockPos3 = pair.getFirst();
                            discreteVoxelShape.fill(blockPos3.getX() - i7, blockPos3.getY() - i8, blockPos3.getZ() - i6x);
                        }

                        updateShapeAtEdge(serverLevel, flags, discreteVoxelShape, i7, i8, i6x);
                    }

                    for (Pair<BlockPos, CompoundTag> pair1 : list3) {
                        BlockPos blockPos4 = pair1.getFirst();
                        if (!settings.getKnownShape()) {
                            BlockState blockState1 = serverLevel.getBlockState(blockPos4);
                            BlockState blockState2 = Block.updateFromNeighbourShapes(blockState1, serverLevel, blockPos4);
                            if (blockState1 != blockState2) {
                                serverLevel.setBlock(blockPos4, blockState2, flags & -2 | 16);
                            }

                            serverLevel.blockUpdated(blockPos4, blockState2.getBlock());
                        }

                        if (pair1.getSecond() != null) {
                            BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos4);
                            if (blockEntity != null) {
                                // Paper start - Fix NBT pieces overriding a block entity during worldgen deadlock
                                if (!(serverLevel instanceof net.minecraft.world.level.WorldGenLevel)) {
                                    blockEntity.setChanged();
                                }
                                // Paper end - Fix NBT pieces overriding a block entity during worldgen deadlock
                            }
                        }
                    }
                }

                if (!settings.isIgnoreEntities()) {
                    this.placeEntities(
                        wrappedAccess, // CraftBukkit
                        offset,
                        settings.getMirror(),
                        settings.getRotation(),
                        settings.getRotationPivot(),
                        boundingBox,
                        settings.shouldFinalizeEntities()
                    );
                }

                return true;
            } else {
                return false;
            }
        }
    }

    public static void updateShapeAtEdge(LevelAccessor level, int flags, DiscreteVoxelShape shape, BlockPos pos) {
        updateShapeAtEdge(level, flags, shape, pos.getX(), pos.getY(), pos.getZ());
    }

    public static void updateShapeAtEdge(LevelAccessor level, int flags, DiscreteVoxelShape shape, int x, int y, int z) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos mutableBlockPos1 = new BlockPos.MutableBlockPos();
        shape.forAllFaces(
            (direction, faceX, faceY, faceZ) -> {
                mutableBlockPos.set(x + faceX, y + faceY, z + faceZ);
                mutableBlockPos1.setWithOffset(mutableBlockPos, direction);
                BlockState blockState = level.getBlockState(mutableBlockPos);
                BlockState blockState1 = level.getBlockState(mutableBlockPos1);
                BlockState blockState2 = blockState.updateShape(level, level, mutableBlockPos, direction, mutableBlockPos1, blockState1, level.getRandom());
                if (blockState != blockState2) {
                    level.setBlock(mutableBlockPos, blockState2, flags & -2);
                }

                BlockState blockState3 = blockState1.updateShape(
                    level, level, mutableBlockPos1, direction.getOpposite(), mutableBlockPos, blockState2, level.getRandom()
                );
                if (blockState1 != blockState3) {
                    level.setBlock(mutableBlockPos1, blockState3, flags & -2);
                }
            }
        );
    }

    public static List<StructureTemplate.StructureBlockInfo> processBlockInfos(
        ServerLevelAccessor serverLevel, BlockPos offset, BlockPos pos, StructurePlaceSettings settings, List<StructureTemplate.StructureBlockInfo> blockInfos
    ) {
        List<StructureTemplate.StructureBlockInfo> list = new ArrayList<>();
        List<StructureTemplate.StructureBlockInfo> list1 = new ArrayList<>();

        for (StructureTemplate.StructureBlockInfo structureBlockInfo : blockInfos) {
            BlockPos blockPos = calculateRelativePosition(settings, structureBlockInfo.pos).offset(offset);
            StructureTemplate.StructureBlockInfo structureBlockInfo1 = new StructureTemplate.StructureBlockInfo(
                blockPos, structureBlockInfo.state, structureBlockInfo.nbt != null ? structureBlockInfo.nbt.copy() : null
            );
            Iterator<StructureProcessor> iterator = settings.getProcessors().iterator();

            while (structureBlockInfo1 != null && iterator.hasNext()) {
                structureBlockInfo1 = iterator.next().processBlock(serverLevel, offset, pos, structureBlockInfo, structureBlockInfo1, settings);
            }

            if (structureBlockInfo1 != null) {
                list1.add(structureBlockInfo1);
                list.add(structureBlockInfo);
            }
        }

        for (StructureProcessor structureProcessor : settings.getProcessors()) {
            list1 = structureProcessor.finalizeProcessing(serverLevel, offset, pos, list, list1, settings);
        }

        return list1;
    }

    private void placeEntities(
        ServerLevelAccessor serverLevel,
        BlockPos pos,
        Mirror mirror,
        Rotation rotation,
        BlockPos offset,
        @Nullable BoundingBox boundingBox,
        boolean withEntities
    ) {
        for (StructureTemplate.StructureEntityInfo structureEntityInfo : this.entityInfoList) {
            BlockPos blockPos = transform(structureEntityInfo.blockPos, mirror, rotation, offset).offset(pos);
            if (boundingBox == null || boundingBox.isInside(blockPos)) {
                CompoundTag compoundTag = structureEntityInfo.nbt.copy();
                Vec3 vec3 = transform(structureEntityInfo.pos, mirror, rotation, offset);
                Vec3 vec31 = vec3.add(pos.getX(), pos.getY(), pos.getZ());
                ListTag listTag = new ListTag();
                listTag.add(DoubleTag.valueOf(vec31.x));
                listTag.add(DoubleTag.valueOf(vec31.y));
                listTag.add(DoubleTag.valueOf(vec31.z));
                compoundTag.put("Pos", listTag);
                compoundTag.remove("UUID");
                createEntityIgnoreException(serverLevel, compoundTag)
                    .ifPresent(
                        entity -> {
                            float f = entity.rotate(rotation);
                            f += entity.mirror(mirror) - entity.getYRot();
                            entity.moveTo(vec31.x, vec31.y, vec31.z, f, entity.getXRot());
                            if (withEntities && entity instanceof Mob) {
                                ((Mob)entity)
                                    .finalizeSpawn(
                                        serverLevel, serverLevel.getCurrentDifficultyAt(BlockPos.containing(vec31)), EntitySpawnReason.STRUCTURE, null
                                    );
                            }

                            serverLevel.addFreshEntityWithPassengers(entity);
                        }
                    );
            }
        }
    }

    private static Optional<Entity> createEntityIgnoreException(ServerLevelAccessor level, CompoundTag tag) {
        // CraftBukkit start
        // try {
        return EntityType.create(tag, level.getLevel(), EntitySpawnReason.STRUCTURE, true); // Paper - Don't fire sync event during generation
        // } catch (Exception var3) {
        //     return Optional.empty();
        // }
        // CraftBukkit end
    }

    public Vec3i getSize(Rotation rotation) {
        switch (rotation) {
            case COUNTERCLOCKWISE_90:
            case CLOCKWISE_90:
                return new Vec3i(this.size.getZ(), this.size.getY(), this.size.getX());
            default:
                return this.size;
        }
    }

    public static BlockPos transform(BlockPos targetPos, Mirror mirror, Rotation rotation, BlockPos offset) {
        int x = targetPos.getX();
        int y = targetPos.getY();
        int z = targetPos.getZ();
        boolean flag = true;
        switch (mirror) {
            case LEFT_RIGHT:
                z = -z;
                break;
            case FRONT_BACK:
                x = -x;
                break;
            default:
                flag = false;
        }

        int x1 = offset.getX();
        int z1 = offset.getZ();
        switch (rotation) {
            case COUNTERCLOCKWISE_90:
                return new BlockPos(x1 - z1 + z, y, x1 + z1 - x);
            case CLOCKWISE_90:
                return new BlockPos(x1 + z1 - z, y, z1 - x1 + x);
            case CLOCKWISE_180:
                return new BlockPos(x1 + x1 - x, y, z1 + z1 - z);
            default:
                return flag ? new BlockPos(x, y, z) : targetPos;
        }
    }

    public static Vec3 transform(Vec3 target, Mirror mirror, Rotation rotation, BlockPos centerOffset) {
        double d = target.x;
        double d1 = target.y;
        double d2 = target.z;
        boolean flag = true;
        switch (mirror) {
            case LEFT_RIGHT:
                d2 = 1.0 - d2;
                break;
            case FRONT_BACK:
                d = 1.0 - d;
                break;
            default:
                flag = false;
        }

        int x = centerOffset.getX();
        int z = centerOffset.getZ();
        switch (rotation) {
            case COUNTERCLOCKWISE_90:
                return new Vec3(x - z + d2, d1, x + z + 1 - d);
            case CLOCKWISE_90:
                return new Vec3(x + z + 1 - d2, d1, z - x + d);
            case CLOCKWISE_180:
                return new Vec3(x + x + 1 - d, d1, z + z + 1 - d2);
            default:
                return flag ? new Vec3(d, d1, d2) : target;
        }
    }

    public BlockPos getZeroPositionWithTransform(BlockPos targetPos, Mirror mirror, Rotation rotation) {
        return getZeroPositionWithTransform(targetPos, mirror, rotation, this.getSize().getX(), this.getSize().getZ());
    }

    public static BlockPos getZeroPositionWithTransform(BlockPos pos, Mirror mirror, Rotation rotation, int sizeX, int sizeZ) {
        sizeX--;
        sizeZ--;
        int i = mirror == Mirror.FRONT_BACK ? sizeX : 0;
        int i1 = mirror == Mirror.LEFT_RIGHT ? sizeZ : 0;
        BlockPos blockPos = pos;
        switch (rotation) {
            case COUNTERCLOCKWISE_90:
                blockPos = pos.offset(i1, 0, sizeX - i);
                break;
            case CLOCKWISE_90:
                blockPos = pos.offset(sizeZ - i1, 0, i);
                break;
            case CLOCKWISE_180:
                blockPos = pos.offset(sizeX - i, 0, sizeZ - i1);
                break;
            case NONE:
                blockPos = pos.offset(i, 0, i1);
        }

        return blockPos;
    }

    public BoundingBox getBoundingBox(StructurePlaceSettings settings, BlockPos startPos) {
        return this.getBoundingBox(startPos, settings.getRotation(), settings.getRotationPivot(), settings.getMirror());
    }

    public BoundingBox getBoundingBox(BlockPos startPos, Rotation rotation, BlockPos pivotPos, Mirror mirror) {
        return getBoundingBox(startPos, rotation, pivotPos, mirror, this.size);
    }

    @VisibleForTesting
    protected static BoundingBox getBoundingBox(BlockPos startPos, Rotation rotation, BlockPos pivotPos, Mirror mirror, Vec3i size) {
        Vec3i vec3i = size.offset(-1, -1, -1);
        BlockPos blockPos = transform(BlockPos.ZERO, mirror, rotation, pivotPos);
        BlockPos blockPos1 = transform(BlockPos.ZERO.offset(vec3i), mirror, rotation, pivotPos);
        return BoundingBox.fromCorners(blockPos, blockPos1).move(startPos);
    }

    public CompoundTag save(CompoundTag tag) {
        if (this.palettes.isEmpty()) {
            tag.put("blocks", new ListTag());
            tag.put("palette", new ListTag());
        } else {
            List<StructureTemplate.SimplePalette> list = Lists.newArrayList();
            StructureTemplate.SimplePalette simplePalette = new StructureTemplate.SimplePalette();
            list.add(simplePalette);

            for (int i = 1; i < this.palettes.size(); i++) {
                list.add(new StructureTemplate.SimplePalette());
            }

            ListTag listTag = new ListTag();
            List<StructureTemplate.StructureBlockInfo> list1 = this.palettes.get(0).blocks();

            for (int i1 = 0; i1 < list1.size(); i1++) {
                StructureTemplate.StructureBlockInfo structureBlockInfo = list1.get(i1);
                CompoundTag compoundTag = new CompoundTag();
                compoundTag.put("pos", this.newIntegerList(structureBlockInfo.pos.getX(), structureBlockInfo.pos.getY(), structureBlockInfo.pos.getZ()));
                int i2 = simplePalette.idFor(structureBlockInfo.state);
                compoundTag.putInt("state", i2);
                if (structureBlockInfo.nbt != null) {
                    compoundTag.put("nbt", structureBlockInfo.nbt);
                }

                listTag.add(compoundTag);

                for (int i3 = 1; i3 < this.palettes.size(); i3++) {
                    StructureTemplate.SimplePalette simplePalette1 = list.get(i3);
                    simplePalette1.addMapping(this.palettes.get(i3).blocks().get(i1).state, i2);
                }
            }

            tag.put("blocks", listTag);
            if (list.size() == 1) {
                ListTag listTag1 = new ListTag();

                for (BlockState blockState : simplePalette) {
                    listTag1.add(NbtUtils.writeBlockState(blockState));
                }

                tag.put("palette", listTag1);
            } else {
                ListTag listTag1 = new ListTag();

                for (StructureTemplate.SimplePalette simplePalette2 : list) {
                    ListTag listTag2 = new ListTag();

                    for (BlockState blockState1 : simplePalette2) {
                        listTag2.add(NbtUtils.writeBlockState(blockState1));
                    }

                    listTag1.add(listTag2);
                }

                tag.put("palettes", listTag1);
            }
        }

        ListTag listTag3 = new ListTag();

        for (StructureTemplate.StructureEntityInfo structureEntityInfo : this.entityInfoList) {
            CompoundTag compoundTag1 = new CompoundTag();
            compoundTag1.put("pos", this.newDoubleList(structureEntityInfo.pos.x, structureEntityInfo.pos.y, structureEntityInfo.pos.z));
            compoundTag1.put(
                "blockPos", this.newIntegerList(structureEntityInfo.blockPos.getX(), structureEntityInfo.blockPos.getY(), structureEntityInfo.blockPos.getZ())
            );
            if (structureEntityInfo.nbt != null) {
                compoundTag1.put("nbt", structureEntityInfo.nbt);
            }

            listTag3.add(compoundTag1);
        }

        tag.put("entities", listTag3);
        tag.put("size", this.newIntegerList(this.size.getX(), this.size.getY(), this.size.getZ()));
        // CraftBukkit start - PDC
        if (!this.persistentDataContainer.isEmpty()) {
            tag.put("BukkitValues", this.persistentDataContainer.toTagCompound());
        }
        // CraftBukkit end
        return NbtUtils.addCurrentDataVersion(tag);
    }

    public void load(HolderGetter<Block> blockGetter, CompoundTag tag) {
        this.palettes.clear();
        this.entityInfoList.clear();
        ListTag list = tag.getList("size", 3);
        this.size = new Vec3i(list.getInt(0), list.getInt(1), list.getInt(2));
        ListTag list1 = tag.getList("blocks", 10);
        if (tag.contains("palettes", 9)) {
            ListTag list2 = tag.getList("palettes", 9);

            for (int i = 0; i < list2.size(); i++) {
                this.loadPalette(blockGetter, list2.getList(i), list1);
            }
        } else {
            this.loadPalette(blockGetter, tag.getList("palette", 10), list1);
        }

        ListTag list2 = tag.getList("entities", 10);

        for (int i = 0; i < list2.size(); i++) {
            CompoundTag compound = list2.getCompound(i);
            ListTag list3 = compound.getList("pos", 6);
            Vec3 vec3 = new Vec3(list3.getDouble(0), list3.getDouble(1), list3.getDouble(2));
            ListTag list4 = compound.getList("blockPos", 3);
            BlockPos blockPos = new BlockPos(list4.getInt(0), list4.getInt(1), list4.getInt(2));
            if (compound.contains("nbt")) {
                CompoundTag compound1 = compound.getCompound("nbt");
                this.entityInfoList.add(new StructureTemplate.StructureEntityInfo(vec3, blockPos, compound1));
            }
        }

        // CraftBukkit start - PDC
        if (tag.get("BukkitValues") instanceof CompoundTag compoundTag) {
            this.persistentDataContainer.putAll(compoundTag);
        }
        // CraftBukkit end
    }

    private void loadPalette(HolderGetter<Block> blockGetter, ListTag paletteTag, ListTag blocksTag) {
        StructureTemplate.SimplePalette simplePalette = new StructureTemplate.SimplePalette();

        for (int i = 0; i < paletteTag.size(); i++) {
            simplePalette.addMapping(NbtUtils.readBlockState(blockGetter, paletteTag.getCompound(i)), i);
        }

        List<StructureTemplate.StructureBlockInfo> list = Lists.newArrayList();
        List<StructureTemplate.StructureBlockInfo> list1 = Lists.newArrayList();
        List<StructureTemplate.StructureBlockInfo> list2 = Lists.newArrayList();

        for (int i1 = 0; i1 < blocksTag.size(); i1++) {
            CompoundTag compound = blocksTag.getCompound(i1);
            ListTag list3 = compound.getList("pos", 3);
            BlockPos blockPos = new BlockPos(list3.getInt(0), list3.getInt(1), list3.getInt(2));
            BlockState blockState = simplePalette.stateFor(compound.getInt("state"));
            CompoundTag compound1;
            if (compound.contains("nbt")) {
                compound1 = compound.getCompound("nbt");
            } else {
                compound1 = null;
            }

            StructureTemplate.StructureBlockInfo structureBlockInfo = new StructureTemplate.StructureBlockInfo(blockPos, blockState, compound1);
            addToLists(structureBlockInfo, list, list1, list2);
        }

        List<StructureTemplate.StructureBlockInfo> list4 = buildInfoList(list, list1, list2);
        this.palettes.add(new StructureTemplate.Palette(list4));
    }

    private ListTag newIntegerList(int... values) {
        ListTag listTag = new ListTag();

        for (int i : values) {
            listTag.add(IntTag.valueOf(i));
        }

        return listTag;
    }

    private ListTag newDoubleList(double... values) {
        ListTag listTag = new ListTag();

        for (double d : values) {
            listTag.add(DoubleTag.valueOf(d));
        }

        return listTag;
    }

    public static JigsawBlockEntity.JointType getJointType(CompoundTag tag, BlockState state) {
        return JigsawBlockEntity.JointType.CODEC
            .byName(
                tag.getString("joint"),
                () -> JigsawBlock.getFrontFacing(state).getAxis().isHorizontal() ? JigsawBlockEntity.JointType.ALIGNED : JigsawBlockEntity.JointType.ROLLABLE
            );
    }

    public record JigsawBlockInfo(
        StructureTemplate.StructureBlockInfo info,
        JigsawBlockEntity.JointType jointType,
        ResourceLocation name,
        ResourceLocation pool,
        ResourceLocation target,
        int placementPriority,
        int selectionPriority
    ) {
        public static StructureTemplate.JigsawBlockInfo of(StructureTemplate.StructureBlockInfo structureBlockInfo) {
            CompoundTag compoundTag = Objects.requireNonNull(structureBlockInfo.nbt(), () -> structureBlockInfo + " nbt was null");
            return new StructureTemplate.JigsawBlockInfo(
                structureBlockInfo,
                StructureTemplate.getJointType(compoundTag, structureBlockInfo.state()),
                ResourceLocation.parse(compoundTag.getString("name")),
                ResourceLocation.parse(compoundTag.getString("pool")),
                ResourceLocation.parse(compoundTag.getString("target")),
                compoundTag.getInt("placement_priority"),
                compoundTag.getInt("selection_priority")
            );
        }

        @Override
        public String toString() {
            return String.format(
                Locale.ROOT,
                "<JigsawBlockInfo | %s | %s | name: %s | pool: %s | target: %s | placement: %d | selection: %d | %s>",
                this.info.pos,
                this.info.state,
                this.name,
                this.pool,
                this.target,
                this.placementPriority,
                this.selectionPriority,
                this.info.nbt
            );
        }

        public StructureTemplate.JigsawBlockInfo withInfo(StructureTemplate.StructureBlockInfo info) {
            return new StructureTemplate.JigsawBlockInfo(
                info, this.jointType, this.name, this.pool, this.target, this.placementPriority, this.selectionPriority
            );
        }
    }

    public static final class Palette {
        private final List<StructureTemplate.StructureBlockInfo> blocks;
        private final Map<Block, List<StructureTemplate.StructureBlockInfo>> cache = Maps.newConcurrentMap(); // Paper - Fix CME due to this collection being shared across threads
        @Nullable
        private List<StructureTemplate.JigsawBlockInfo> cachedJigsaws;

        Palette(List<StructureTemplate.StructureBlockInfo> blocks) {
            this.blocks = blocks;
        }

        public List<StructureTemplate.JigsawBlockInfo> jigsaws() {
            if (this.cachedJigsaws == null) {
                this.cachedJigsaws = this.blocks(Blocks.JIGSAW).stream().map(StructureTemplate.JigsawBlockInfo::of).toList();
            }

            return this.cachedJigsaws;
        }

        public List<StructureTemplate.StructureBlockInfo> blocks() {
            return this.blocks;
        }

        public List<StructureTemplate.StructureBlockInfo> blocks(Block block) {
            return this.cache
                .computeIfAbsent(
                    block, block1 -> this.blocks.stream().filter(structureBlockInfo -> structureBlockInfo.state.is(block1)).collect(Collectors.toList())
                );
        }
    }

    static class SimplePalette implements Iterable<BlockState> {
        public static final BlockState DEFAULT_BLOCK_STATE = Blocks.AIR.defaultBlockState();
        private final IdMapper<BlockState> ids = new IdMapper<>(16);
        private int lastId;

        public int idFor(BlockState state) {
            int id = this.ids.getId(state);
            if (id == -1) {
                id = this.lastId++;
                this.ids.addMapping(state, id);
            }

            return id;
        }

        @Nullable
        public BlockState stateFor(int id) {
            BlockState blockState = this.ids.byId(id);
            return blockState == null ? DEFAULT_BLOCK_STATE : blockState;
        }

        @Override
        public Iterator<BlockState> iterator() {
            return this.ids.iterator();
        }

        public void addMapping(BlockState state, int id) {
            this.ids.addMapping(state, id);
        }
    }

    public record StructureBlockInfo(BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "<StructureBlockInfo | %s | %s | %s>", this.pos, this.state, this.nbt);
        }
    }

    public static class StructureEntityInfo {
        public final Vec3 pos;
        public final BlockPos blockPos;
        public final CompoundTag nbt;

        public StructureEntityInfo(Vec3 pos, BlockPos blockPos, CompoundTag nbt) {
            this.pos = pos;
            this.blockPos = blockPos;
            this.nbt = nbt;
        }
    }
}
