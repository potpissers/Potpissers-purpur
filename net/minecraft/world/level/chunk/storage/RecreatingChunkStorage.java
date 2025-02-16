package net.minecraft.world.level.chunk.storage;

import com.mojang.datafixers.DataFixer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.io.FileUtils;

public class RecreatingChunkStorage extends ChunkStorage {
    private final IOWorker writeWorker;
    private final Path writeFolder;

    public RecreatingChunkStorage(RegionStorageInfo info, Path folder, RegionStorageInfo writeInfo, Path writeFolder, DataFixer fixerUpper, boolean sync) {
        super(info, folder, fixerUpper, sync);
        this.writeFolder = writeFolder;
        this.writeWorker = new IOWorker(writeInfo, writeFolder, sync);
    }

    @Override
    public CompletableFuture<Void> write(ChunkPos pos, Supplier<CompoundTag> tagSupplier) {
        this.handleLegacyStructureIndex(pos);
        return this.writeWorker.store(pos, tagSupplier);
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
