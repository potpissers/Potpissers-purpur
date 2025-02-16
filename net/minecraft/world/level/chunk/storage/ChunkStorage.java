package net.minecraft.world.level.chunk.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.MapCodec;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.LegacyStructureDataHandler;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class ChunkStorage implements AutoCloseable {
    public static final int LAST_MONOLYTH_STRUCTURE_DATA_VERSION = 1493;
    private final IOWorker worker;
    protected final DataFixer fixerUpper;
    @Nullable
    private volatile LegacyStructureDataHandler legacyStructureHandler;

    public ChunkStorage(RegionStorageInfo info, Path folder, DataFixer fixerUpper, boolean sync) {
        this.fixerUpper = fixerUpper;
        this.worker = new IOWorker(info, folder, sync);
    }

    public boolean isOldChunkAround(ChunkPos pos, int radius) {
        return this.worker.isOldChunkAround(pos, radius);
    }

    public CompoundTag upgradeChunkTag(
        ResourceKey<Level> levelKey,
        Supplier<DimensionDataStorage> storage,
        CompoundTag chunkData,
        Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> chunkGeneratorKey
    ) {
        int version = getVersion(chunkData);
        if (version == SharedConstants.getCurrentVersion().getDataVersion().getVersion()) {
            return chunkData;
        } else {
            try {
                if (version < 1493) {
                    chunkData = DataFixTypes.CHUNK.update(this.fixerUpper, chunkData, version, 1493);
                    if (chunkData.getCompound("Level").getBoolean("hasLegacyStructureData")) {
                        LegacyStructureDataHandler legacyStructureHandler = this.getLegacyStructureHandler(levelKey, storage);
                        chunkData = legacyStructureHandler.updateFromLegacy(chunkData);
                    }
                }

                injectDatafixingContext(chunkData, levelKey, chunkGeneratorKey);
                chunkData = DataFixTypes.CHUNK.updateToCurrentVersion(this.fixerUpper, chunkData, Math.max(1493, version));
                removeDatafixingContext(chunkData);
                NbtUtils.addCurrentDataVersion(chunkData);
                return chunkData;
            } catch (Exception var9) {
                CrashReport crashReport = CrashReport.forThrowable(var9, "Updated chunk");
                CrashReportCategory crashReportCategory = crashReport.addCategory("Updated chunk details");
                crashReportCategory.setDetail("Data version", version);
                throw new ReportedException(crashReport);
            }
        }
    }

    private LegacyStructureDataHandler getLegacyStructureHandler(ResourceKey<Level> level, Supplier<DimensionDataStorage> storage) {
        LegacyStructureDataHandler legacyStructureDataHandler = this.legacyStructureHandler;
        if (legacyStructureDataHandler == null) {
            synchronized (this) {
                legacyStructureDataHandler = this.legacyStructureHandler;
                if (legacyStructureDataHandler == null) {
                    this.legacyStructureHandler = legacyStructureDataHandler = LegacyStructureDataHandler.getLegacyStructureHandler(level, storage.get());
                }
            }
        }

        return legacyStructureDataHandler;
    }

    public static void injectDatafixingContext(
        CompoundTag chunkData, ResourceKey<Level> levelKey, Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> chunkGeneratorKey
    ) {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("dimension", levelKey.location().toString());
        chunkGeneratorKey.ifPresent(generator -> compoundTag.putString("generator", generator.location().toString()));
        chunkData.put("__context", compoundTag);
    }

    private static void removeDatafixingContext(CompoundTag tag) {
        tag.remove("__context");
    }

    public static int getVersion(CompoundTag chunkData) {
        return NbtUtils.getDataVersion(chunkData, -1);
    }

    public CompletableFuture<Optional<CompoundTag>> read(ChunkPos chunkPos) {
        return this.worker.loadAsync(chunkPos);
    }

    public CompletableFuture<Void> write(ChunkPos pos, Supplier<CompoundTag> tagSupplier) {
        this.handleLegacyStructureIndex(pos);
        return this.worker.store(pos, tagSupplier);
    }

    protected void handleLegacyStructureIndex(ChunkPos chunkPos) {
        if (this.legacyStructureHandler != null) {
            this.legacyStructureHandler.removeIndex(chunkPos.toLong());
        }
    }

    public void flushWorker() {
        this.worker.synchronize(true).join();
    }

    @Override
    public void close() throws IOException {
        this.worker.close();
    }

    public ChunkScanAccess chunkScanner() {
        return this.worker;
    }

    protected RegionStorageInfo storageInfo() {
        return this.worker.storageInfo();
    }
}
