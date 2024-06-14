package net.minecraft.world.level.chunk.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;

public class SimpleRegionStorage implements AutoCloseable {
    public final IOWorker worker; // Paper - public
    private final DataFixer fixerUpper;
    private final DataFixTypes dataFixType;

    public SimpleRegionStorage(RegionStorageInfo info, Path folder, DataFixer fixerUpper, boolean sync, DataFixTypes dataFixType) {
        this.fixerUpper = fixerUpper;
        this.dataFixType = dataFixType;
        this.worker = new IOWorker(info, folder, sync);
    }

    public CompletableFuture<Optional<CompoundTag>> read(ChunkPos chunkPos) {
        return this.worker.loadAsync(chunkPos);
    }

    public CompletableFuture<Void> write(ChunkPos chunkPos, @Nullable CompoundTag data) {
        return this.worker.store(chunkPos, data);
    }

    // Paper start - rewrite data conversion system
    private ca.spottedleaf.dataconverter.minecraft.datatypes.MCDataType getDataConverterType() {
        if (this.dataFixType == DataFixTypes.ENTITY_CHUNK) {
            return ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.ENTITY_CHUNK;
        } else if (this.dataFixType == DataFixTypes.POI_CHUNK) {
            return ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.POI_CHUNK;
        } else {
            throw new UnsupportedOperationException("For " + this.dataFixType.name());
        }
    }
    // Paper end - rewrite data conversion system

    public CompoundTag upgradeChunkTag(CompoundTag tag, int version) {
        // Paper start - rewrite data conversion system
        final int dataVer = NbtUtils.getDataVersion(tag, version);
        return ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(this.getDataConverterType(), tag, dataVer, net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        // Paper end - rewrite data conversion system
    }

    public Dynamic<Tag> upgradeChunkTag(Dynamic<Tag> tag, int version) {
        // Paper start - rewrite data conversion system
        final CompoundTag converted = ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(this.getDataConverterType(), (CompoundTag)tag.getValue(), version, net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        return new Dynamic<>(tag.getOps(), converted);
        // Paper end - rewrite data conversion system
    }

    public CompletableFuture<Void> synchronize(boolean flushStorage) {
        return this.worker.synchronize(flushStorage);
    }

    @Override
    public void close() throws IOException {
        this.worker.close();
    }

    public RegionStorageInfo storageInfo() {
        return this.worker.storageInfo();
    }
}
