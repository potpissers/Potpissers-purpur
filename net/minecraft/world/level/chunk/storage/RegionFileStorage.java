package net.minecraft.world.level.chunk.storage;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.util.ExceptionCollector;
import net.minecraft.world.level.ChunkPos;

public final class RegionFileStorage implements AutoCloseable {
    public static final String ANVIL_EXTENSION = ".mca";
    private static final int MAX_CACHE_SIZE = 256;
    public final Long2ObjectLinkedOpenHashMap<RegionFile> regionCache = new Long2ObjectLinkedOpenHashMap<>();
    private final RegionStorageInfo info;
    private final Path folder;
    private final boolean sync;

    RegionFileStorage(RegionStorageInfo info, Path folder, boolean sync) {
        this.folder = folder;
        this.sync = sync;
        this.info = info;
    }

    @org.jetbrains.annotations.Contract("_, false -> !null") @Nullable private RegionFile getRegionFile(ChunkPos chunkPos, boolean existingOnly) throws IOException { // CraftBukkit
        long packedChunkPos = ChunkPos.asLong(chunkPos.getRegionX(), chunkPos.getRegionZ());
        RegionFile regionFile = this.regionCache.getAndMoveToFirst(packedChunkPos);
        if (regionFile != null) {
            return regionFile;
        } else {
            if (this.regionCache.size() >= io.papermc.paper.configuration.GlobalConfiguration.get().misc.regionFileCacheSize) { // Paper - Sanitise RegionFileCache and make configurable
                this.regionCache.removeLast().close();
            }

            FileUtil.createDirectoriesSafe(this.folder);
            Path path = this.folder.resolve("r." + chunkPos.getRegionX() + "." + chunkPos.getRegionZ() + ".mca");
            if (existingOnly && !java.nio.file.Files.exists(path)) return null; // CraftBukkit
            RegionFile regionFile1 = new RegionFile(this.info, path, this.folder, this.sync);
            this.regionCache.putAndMoveToFirst(packedChunkPos, regionFile1);
            return regionFile1;
        }
    }

    @Nullable
    public CompoundTag read(ChunkPos chunkPos) throws IOException {
        // CraftBukkit start - SPIGOT-5680: There's no good reason to preemptively create files on read, save that for writing
        RegionFile regionFile = this.getRegionFile(chunkPos, true);
        if (regionFile == null) {
            return null;
        }
        // CraftBukkit end

        CompoundTag var4;
        try (DataInputStream chunkDataInputStream = regionFile.getChunkDataInputStream(chunkPos)) {
            if (chunkDataInputStream == null) {
                return null;
            }

            var4 = NbtIo.read(chunkDataInputStream);
        }

        return var4;
    }

    public void scanChunk(ChunkPos chunkPos, StreamTagVisitor visitor) throws IOException {
        // CraftBukkit start - SPIGOT-5680: There's no good reason to preemptively create files on read, save that for writing
        RegionFile regionFile = this.getRegionFile(chunkPos, true);
        if (regionFile == null) {
            return;
        }
        // CraftBukkit end

        try (DataInputStream chunkDataInputStream = regionFile.getChunkDataInputStream(chunkPos)) {
            if (chunkDataInputStream != null) {
                NbtIo.parse(chunkDataInputStream, visitor, NbtAccounter.unlimitedHeap());
            }
        }
    }

    protected void write(ChunkPos chunkPos, @Nullable CompoundTag chunkData) throws IOException {
        RegionFile regionFile = this.getRegionFile(chunkPos, false); // CraftBukkit
        if (chunkData == null) {
            regionFile.clear(chunkPos);
        } else {
            try (DataOutputStream chunkDataOutputStream = regionFile.getChunkDataOutputStream(chunkPos)) {
                NbtIo.write(chunkData, chunkDataOutputStream);
            }
        }
    }

    @Override
    public void close() throws IOException {
        ExceptionCollector<IOException> exceptionCollector = new ExceptionCollector<>();

        for (RegionFile regionFile : this.regionCache.values()) {
            try {
                regionFile.close();
            } catch (IOException var5) {
                exceptionCollector.add(var5);
            }
        }

        exceptionCollector.throwIfPresent();
    }

    public void flush() throws IOException {
        for (RegionFile regionFile : this.regionCache.values()) {
            regionFile.flush();
        }
    }

    public RegionStorageInfo info() {
        return this.info;
    }
}
