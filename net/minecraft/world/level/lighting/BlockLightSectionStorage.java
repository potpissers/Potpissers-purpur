package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

public class BlockLightSectionStorage extends LayerLightSectionStorage<BlockLightSectionStorage.BlockDataLayerStorageMap> {
    protected BlockLightSectionStorage(LightChunkGetter chunkSource) {
        super(LightLayer.BLOCK, chunkSource, new BlockLightSectionStorage.BlockDataLayerStorageMap(new Long2ObjectOpenHashMap<>()));
    }

    @Override
    protected int getLightValue(long levelPos) {
        long packedSectionPos = SectionPos.blockToSection(levelPos);
        DataLayer dataLayer = this.getDataLayer(packedSectionPos, false);
        return dataLayer == null
            ? 0
            : dataLayer.get(
                SectionPos.sectionRelative(BlockPos.getX(levelPos)),
                SectionPos.sectionRelative(BlockPos.getY(levelPos)),
                SectionPos.sectionRelative(BlockPos.getZ(levelPos))
            );
    }

    protected static final class BlockDataLayerStorageMap extends DataLayerStorageMap<BlockLightSectionStorage.BlockDataLayerStorageMap> {
        public BlockDataLayerStorageMap(Long2ObjectOpenHashMap<DataLayer> map) {
            super(map);
        }

        @Override
        public BlockLightSectionStorage.BlockDataLayerStorageMap copy() {
            return new BlockLightSectionStorage.BlockDataLayerStorageMap(this.map.clone());
        }
    }
}
