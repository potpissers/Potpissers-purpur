package net.minecraft.world.item;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class MapItem extends Item {
    public static final int IMAGE_WIDTH = 128;
    public static final int IMAGE_HEIGHT = 128;

    public MapItem(Item.Properties properties) {
        super(properties);
    }

    public static ItemStack create(Level level, int levelX, int levelZ, byte scale, boolean trackingPosition, boolean unlimitedTracking) {
        ItemStack itemStack = new ItemStack(Items.FILLED_MAP);
        MapId mapId = createNewSavedData(level, levelX, levelZ, scale, trackingPosition, unlimitedTracking, level.dimension());
        itemStack.set(DataComponents.MAP_ID, mapId);
        return itemStack;
    }

    @Nullable
    public static MapItemSavedData getSavedData(@Nullable MapId mapId, Level level) {
        return mapId == null ? null : level.getMapData(mapId);
    }

    @Nullable
    public static MapItemSavedData getSavedData(ItemStack stack, Level level) {
        MapId mapId = stack.get(DataComponents.MAP_ID);
        return getSavedData(mapId, level);
    }

    public static MapId createNewSavedData(
        Level level, int x, int z, int scale, boolean trackingPosition, boolean unlimitedTracking, ResourceKey<Level> dimension
    ) {
        MapItemSavedData mapItemSavedData = MapItemSavedData.createFresh(x, z, (byte)scale, trackingPosition, unlimitedTracking, dimension);
        MapId freeMapId = level.getFreeMapId();
        level.setMapData(freeMapId, mapItemSavedData);
        return freeMapId;
    }

    public void update(Level level, Entity viewer, MapItemSavedData data) {
        if (level.dimension() == data.dimension && viewer instanceof Player) {
            int i = 1 << data.scale;
            int i1 = data.centerX;
            int i2 = data.centerZ;
            int i3 = Mth.floor(viewer.getX() - i1) / i + 64;
            int i4 = Mth.floor(viewer.getZ() - i2) / i + 64;
            int i5 = 128 / i;
            if (level.dimensionType().hasCeiling()) {
                i5 /= 2;
            }

            MapItemSavedData.HoldingPlayer holdingPlayer = data.getHoldingPlayer((Player)viewer);
            holdingPlayer.step++;
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos mutableBlockPos1 = new BlockPos.MutableBlockPos();
            boolean flag = false;

            for (int i6 = i3 - i5 + 1; i6 < i3 + i5; i6++) {
                if ((i6 & 15) == (holdingPlayer.step & 15) || flag) {
                    flag = false;
                    double d = 0.0;

                    for (int i7 = i4 - i5 - 1; i7 < i4 + i5; i7++) {
                        if (i6 >= 0 && i7 >= -1 && i6 < 128 && i7 < 128) {
                            int i8 = Mth.square(i6 - i3) + Mth.square(i7 - i4);
                            boolean flag1 = i8 > (i5 - 2) * (i5 - 2);
                            int i9 = (i1 / i + i6 - 64) * i;
                            int i10 = (i2 / i + i7 - 64) * i;
                            Multiset<MapColor> multiset = LinkedHashMultiset.create();
                            LevelChunk chunk = level.getChunkIfLoaded(SectionPos.blockToSectionCoord(i9), SectionPos.blockToSectionCoord(i10)); // Paper - Maps shouldn't load chunks
                            if (chunk != null && !chunk.isEmpty()) { // Paper - Maps shouldn't load chunks
                                int i11 = 0;
                                double d1 = 0.0;
                                if (level.dimensionType().hasCeiling()) {
                                    int i12 = i9 + i10 * 231871;
                                    i12 = i12 * i12 * 31287121 + i12 * 11;
                                    if ((i12 >> 20 & 1) == 0) {
                                        multiset.add(Blocks.DIRT.defaultBlockState().getMapColor(level, BlockPos.ZERO), 10);
                                    } else {
                                        multiset.add(Blocks.STONE.defaultBlockState().getMapColor(level, BlockPos.ZERO), 100);
                                    }

                                    d1 = 100.0;
                                } else {
                                    for (int i12 = 0; i12 < i; i12++) {
                                        for (int i13 = 0; i13 < i; i13++) {
                                            mutableBlockPos.set(i9 + i12, 0, i10 + i13);
                                            int i14 = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, mutableBlockPos.getX(), mutableBlockPos.getZ()) + 1;
                                            BlockState blockState;
                                            if (i14 <= level.getMinY()) {
                                                blockState = Blocks.BEDROCK.defaultBlockState();
                                            } else {
                                                do {
                                                    mutableBlockPos.setY(--i14);
                                                    blockState = chunk.getBlockState(mutableBlockPos);
                                                } while (blockState.getMapColor(level, mutableBlockPos) == MapColor.NONE && i14 > level.getMinY());

                                                if (i14 > level.getMinY() && !blockState.getFluidState().isEmpty()) {
                                                    int i15 = i14 - 1;
                                                    mutableBlockPos1.set(mutableBlockPos);

                                                    BlockState blockState1;
                                                    do {
                                                        mutableBlockPos1.setY(i15--);
                                                        blockState1 = chunk.getBlockState(mutableBlockPos1);
                                                        i11++;
                                                    } while (i15 > level.getMinY() && !blockState1.getFluidState().isEmpty());

                                                    blockState = this.getCorrectStateForFluidBlock(level, blockState, mutableBlockPos);
                                                }
                                            }

                                            data.checkBanners(level, mutableBlockPos.getX(), mutableBlockPos.getZ());
                                            d1 += (double)i14 / (i * i);
                                            multiset.add(blockState.getMapColor(level, mutableBlockPos));
                                        }
                                    }
                                }

                                i11 /= i * i;
                                MapColor mapColor = Iterables.getFirst(Multisets.copyHighestCountFirst(multiset), MapColor.NONE);
                                MapColor.Brightness brightness;
                                if (mapColor == MapColor.WATER) {
                                    double d2 = i11 * 0.1 + (i6 + i7 & 1) * 0.2;
                                    if (d2 < 0.5) {
                                        brightness = MapColor.Brightness.HIGH;
                                    } else if (d2 > 0.9) {
                                        brightness = MapColor.Brightness.LOW;
                                    } else {
                                        brightness = MapColor.Brightness.NORMAL;
                                    }
                                } else {
                                    double d2 = (d1 - d) * 4.0 / (i + 4) + ((i6 + i7 & 1) - 0.5) * 0.4;
                                    if (d2 > 0.6) {
                                        brightness = MapColor.Brightness.HIGH;
                                    } else if (d2 < -0.6) {
                                        brightness = MapColor.Brightness.LOW;
                                    } else {
                                        brightness = MapColor.Brightness.NORMAL;
                                    }
                                }

                                d = d1;
                                if (i7 >= 0 && i8 < i5 * i5 && (!flag1 || (i6 + i7 & 1) != 0)) {
                                    flag |= data.updateColor(i6, i7, mapColor.getPackedId(brightness));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private BlockState getCorrectStateForFluidBlock(Level level, BlockState state, BlockPos pos) {
        FluidState fluidState = state.getFluidState();
        return !fluidState.isEmpty() && !state.isFaceSturdy(level, pos, Direction.UP) ? fluidState.createLegacyBlock() : state;
    }

    private static boolean isBiomeWatery(boolean[] wateryMap, int xSample, int zSample) {
        return wateryMap[zSample * 128 + xSample];
    }

    public static void renderBiomePreviewMap(ServerLevel serverLevel, ItemStack stack) {
        MapItemSavedData savedData = getSavedData(stack, serverLevel);
        if (savedData != null) {
            savedData.isExplorerMap = true; // Purpur - Explorer Map API
            if (serverLevel.dimension() == savedData.dimension) {
                int i = 1 << savedData.scale;
                int i1 = savedData.centerX;
                int i2 = savedData.centerZ;
                boolean[] flags = new boolean[16384];
                int i3 = i1 / i - 64;
                int i4 = i2 / i - 64;
                BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

                for (int i5 = 0; i5 < 128; i5++) {
                    for (int i6 = 0; i6 < 128; i6++) {
                        Holder<Biome> biome = serverLevel.getUncachedNoiseBiome(net.minecraft.core.QuartPos.fromBlock((i3 + i6) * i), net.minecraft.core.QuartPos.fromBlock(0), net.minecraft.core.QuartPos.fromBlock((i4 + i5) * i)); // Paper - Perf: Use seed based lookup for treasure maps
                        flags[i5 * 128 + i6] = biome.is(BiomeTags.WATER_ON_MAP_OUTLINES);
                    }
                }

                for (int i5 = 1; i5 < 127; i5++) {
                    for (int i6 = 1; i6 < 127; i6++) {
                        int i7 = 0;

                        for (int i8 = -1; i8 < 2; i8++) {
                            for (int i9 = -1; i9 < 2; i9++) {
                                if ((i8 != 0 || i9 != 0) && isBiomeWatery(flags, i5 + i8, i6 + i9)) {
                                    i7++;
                                }
                            }
                        }

                        MapColor.Brightness brightness = MapColor.Brightness.LOWEST;
                        MapColor mapColor = MapColor.NONE;
                        if (isBiomeWatery(flags, i5, i6)) {
                            mapColor = MapColor.COLOR_ORANGE;
                            if (i7 > 7 && i6 % 2 == 0) {
                                switch ((i5 + (int)(Mth.sin(i6 + 0.0F) * 7.0F)) / 8 % 5) {
                                    case 0:
                                    case 4:
                                        brightness = MapColor.Brightness.LOW;
                                        break;
                                    case 1:
                                    case 3:
                                        brightness = MapColor.Brightness.NORMAL;
                                        break;
                                    case 2:
                                        brightness = MapColor.Brightness.HIGH;
                                }
                            } else if (i7 > 7) {
                                mapColor = MapColor.NONE;
                            } else if (i7 > 5) {
                                brightness = MapColor.Brightness.NORMAL;
                            } else if (i7 > 3) {
                                brightness = MapColor.Brightness.LOW;
                            } else if (i7 > 1) {
                                brightness = MapColor.Brightness.LOW;
                            }
                        } else if (i7 > 0) {
                            mapColor = MapColor.COLOR_BROWN;
                            if (i7 > 3) {
                                brightness = MapColor.Brightness.NORMAL;
                            } else {
                                brightness = MapColor.Brightness.LOWEST;
                            }
                        }

                        if (mapColor != MapColor.NONE) {
                            savedData.setColor(i5, i6, mapColor.getPackedId(brightness));
                        }
                    }
                }
            }
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int itemSlot, boolean isSelected) {
        if (!level.isClientSide) {
            MapItemSavedData savedData = getSavedData(stack, level);
            if (savedData != null) {
                if (entity instanceof Player player) {
                    savedData.tickCarriedBy(player, stack);
                }

                if (!savedData.locked && (isSelected || entity instanceof Player && ((Player)entity).getOffhandItem() == stack)) {
                    this.update(level, entity, savedData);
                }
            }
        }
    }

    @Override
    public void onCraftedPostProcess(ItemStack stack, Level level) {
        MapPostProcessing mapPostProcessing = stack.remove(DataComponents.MAP_POST_PROCESSING);
        if (mapPostProcessing != null) {
            switch (mapPostProcessing) {
                case LOCK:
                    lockMap(level, stack);
                    break;
                case SCALE:
                    scaleMap(stack, level);
            }
        }
    }

    private static void scaleMap(ItemStack stack, Level level) {
        MapItemSavedData savedData = getSavedData(stack, level);
        if (savedData != null) {
            MapId freeMapId = level.getFreeMapId();
            level.setMapData(freeMapId, savedData.scaled());
            stack.set(DataComponents.MAP_ID, freeMapId);
        }
    }

    public static void lockMap(Level level, ItemStack stack) {
        MapItemSavedData savedData = getSavedData(stack, level);
        if (savedData != null) {
            MapId freeMapId = level.getFreeMapId();
            MapItemSavedData mapItemSavedData = savedData.locked();
            level.setMapData(freeMapId, mapItemSavedData);
            stack.set(DataComponents.MAP_ID, freeMapId);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        MapId mapId = stack.get(DataComponents.MAP_ID);
        MapItemSavedData mapItemSavedData = mapId != null ? context.mapData(mapId) : null;
        MapPostProcessing mapPostProcessing = stack.get(DataComponents.MAP_POST_PROCESSING);
        if (mapItemSavedData != null && (mapItemSavedData.locked || mapPostProcessing == MapPostProcessing.LOCK)) {
            tooltipComponents.add(Component.translatable("filled_map.locked", mapId.id()).withStyle(ChatFormatting.GRAY));
        }

        if (tooltipFlag.isAdvanced()) {
            if (mapItemSavedData != null) {
                if (mapPostProcessing == null) {
                    tooltipComponents.add(getTooltipForId(mapId));
                }

                int i = mapPostProcessing == MapPostProcessing.SCALE ? 1 : 0;
                int min = Math.min(mapItemSavedData.scale + i, 4);
                tooltipComponents.add(Component.translatable("filled_map.scale", 1 << min).withStyle(ChatFormatting.GRAY));
                tooltipComponents.add(Component.translatable("filled_map.level", min, 4).withStyle(ChatFormatting.GRAY));
            } else {
                tooltipComponents.add(Component.translatable("filled_map.unknown").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    public static Component getTooltipForId(MapId mapId) {
        return Component.translatable("filled_map.id", mapId.id()).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockState blockState = context.getLevel().getBlockState(context.getClickedPos());
        if (blockState.is(BlockTags.BANNERS)) {
            if (!context.getLevel().isClientSide) {
                MapItemSavedData savedData = getSavedData(context.getItemInHand(), context.getLevel());
                if (savedData != null && !savedData.toggleBanner(context.getLevel(), context.getClickedPos())) {
                    return InteractionResult.FAIL;
                }
            }

            return InteractionResult.SUCCESS;
        } else {
            return super.useOn(context);
        }
    }
}
