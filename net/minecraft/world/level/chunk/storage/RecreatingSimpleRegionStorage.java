package net.minecraft.world.level.chunk.storage;

import com.mojang.datafixers.DataFixer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.io.FileUtils;

public class RecreatingSimpleRegionStorage extends SimpleRegionStorage {
    private final IOWorker writeWorker;
    private final Path writeFolder;

    public RecreatingSimpleRegionStorage(
        RegionStorageInfo info, Path folder, RegionStorageInfo writeInfo, Path writeFolder, DataFixer fixerUpper, boolean sync, DataFixTypes dataFixType
    ) {
        super(info, folder, fixerUpper, sync, dataFixType);
        this.writeFolder = writeFolder;
        this.writeWorker = new IOWorker(writeInfo, writeFolder, sync);
    }

    @Override
    public CompletableFuture<Void> write(ChunkPos chunkPos, @Nullable CompoundTag data) {
        return this.writeWorker.store(chunkPos, data);
    }

    @Override
    public void close() throws IOException {
        super.close();
        this.writeWorker.close();
        if (this.writeFolder.toFile().exists()) {
            FileUtils.deleteDirectory(this.writeFolder.toFile());
        }
    }
}
