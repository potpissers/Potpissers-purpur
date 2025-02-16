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

public class RegionFileStorage implements AutoCloseable, ca.spottedleaf.moonrise.patches.chunk_system.io.ChunkSystemRegionFileStorage { // Paper - rewrite chunk system
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger(); // Paper
    public static final String ANVIL_EXTENSION = ".mca";
    private static final int MAX_CACHE_SIZE = 256;
    public final Long2ObjectLinkedOpenHashMap<RegionFile> regionCache = new Long2ObjectLinkedOpenHashMap<>();
    private final RegionStorageInfo info;
    private final Path folder;
    private final boolean sync;

    // Paper start - recalculate region file headers
    private final boolean isChunkData;

    public static boolean isChunkDataFolder(Path path) {
        return path.toFile().getName().equalsIgnoreCase("region");
    }

    @Nullable
    public static ChunkPos getRegionFileCoordinates(Path file) {
        String fileName = file.getFileName().toString();
        if (!fileName.startsWith("r.") || !fileName.endsWith(".mca")) {
            return null;
        }

        String[] split = fileName.split("\\.");

        if (split.length != 4) {
            return null;
        }

        try {
            int x = Integer.parseInt(split[1]);
            int z = Integer.parseInt(split[2]);

            return new ChunkPos(x << 5, z << 5);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    // Paper end
    // Paper start - rewrite chunk system
    private static final int REGION_SHIFT = 5;
    private static final int MAX_NON_EXISTING_CACHE = 1024 * 4;
    private final it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet nonExistingRegionFiles = new it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet();
    private static String getRegionFileName(final int chunkX, final int chunkZ) {
        return "r." + (chunkX >> REGION_SHIFT) + "." + (chunkZ >> REGION_SHIFT) + ".mca";
    }

    private boolean doesRegionFilePossiblyExist(final long position) {
        synchronized (this.nonExistingRegionFiles) {
            if (this.nonExistingRegionFiles.contains(position)) {
                this.nonExistingRegionFiles.addAndMoveToFirst(position);
                return false;
            }
            return true;
        }
    }

    private void createRegionFile(final long position) {
        synchronized (this.nonExistingRegionFiles) {
            this.nonExistingRegionFiles.remove(position);
        }
    }

    private void markNonExisting(final long position) {
        synchronized (this.nonExistingRegionFiles) {
            if (this.nonExistingRegionFiles.addAndMoveToFirst(position)) {
                while (this.nonExistingRegionFiles.size() >= MAX_NON_EXISTING_CACHE) {
                    this.nonExistingRegionFiles.removeLastLong();
                }
            }
        }
    }

    @Override
    public final boolean moonrise$doesRegionFileNotExistNoIO(final int chunkX, final int chunkZ) {
        return !this.doesRegionFilePossiblyExist(ChunkPos.asLong(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT));
    }

    @Override
    public synchronized final RegionFile moonrise$getRegionFileIfLoaded(final int chunkX, final int chunkZ) {
        return this.regionCache.getAndMoveToFirst(ChunkPos.asLong(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT));
    }

    @Override
    public synchronized final RegionFile moonrise$getRegionFileIfExists(final int chunkX, final int chunkZ) throws IOException {
        final long key = ChunkPos.asLong(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT);

        RegionFile ret = this.regionCache.getAndMoveToFirst(key);
        if (ret != null) {
            return ret;
        }

        if (!this.doesRegionFilePossiblyExist(key)) {
            return null;
        }

        if (this.regionCache.size() >= io.papermc.paper.configuration.GlobalConfiguration.get().misc.regionFileCacheSize) { // Paper
            this.regionCache.removeLast().close();
        }

        final Path regionPath = this.folder.resolve(getRegionFileName(chunkX, chunkZ));

        if (!java.nio.file.Files.exists(regionPath)) {
            this.markNonExisting(key);
            return null;
        }

        this.createRegionFile(key);

        FileUtil.createDirectoriesSafe(this.folder);

        ret = new RegionFile(this.info, regionPath, this.folder, this.sync);

        this.regionCache.putAndMoveToFirst(key, ret);

        return ret;
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData moonrise$startWrite(
        final int chunkX, final int chunkZ, final CompoundTag compound
    ) throws IOException {
        if (compound == null) {
            return new ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData(
                compound, ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData.WriteResult.DELETE,
                null, null
            );
        }

        final ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        final RegionFile regionFile = this.getRegionFile(pos);

        // note: not required to keep regionfile loaded after this call, as the write param takes a regionfile as input
        // (and, the regionfile parameter is unused for writing until the write call)
        final ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData writeData = ((ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemRegionFile)regionFile).moonrise$startWrite(compound, pos);

        try { // Paper - implement RegionFileSizeException
        try {
            NbtIo.write(compound, writeData.output());
        } finally {
            writeData.output().close();
        }
        // Paper start - implement RegionFileSizeException
        } catch (final RegionFileSizeException ex) {
            // note: it's OK if close() is called, as close() here will not issue a write to the RegionFile
            // see startWrite
            final int maxSize = RegionFile.MAX_CHUNK_SIZE / (1024 * 1024);
            LOGGER.error("Chunk at (" + chunkX + "," + chunkZ + ") in regionfile '" + regionFile.getPath().toString() + "' exceeds max size of " + maxSize + "MiB, it has been deleted from disk.");
            return new ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData(
                compound, ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData.WriteResult.DELETE,
                null, null
            );
        }
        // Paper end - implement RegionFileSizeException

        return writeData;
    }

    @Override
    public final void moonrise$finishWrite(
        final int chunkX, final int chunkZ, final ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData writeData
    ) throws IOException {
        final ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (writeData.result() == ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData.WriteResult.DELETE) {
            final RegionFile regionFile = this.moonrise$getRegionFileIfExists(chunkX, chunkZ);
            if (regionFile != null) {
                regionFile.clear(pos);
            } // else: didn't exist

            return;
        }

        writeData.write().run(this.getRegionFile(pos));
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData moonrise$readData(
        final int chunkX, final int chunkZ
    ) throws IOException {
        final RegionFile regionFile = this.moonrise$getRegionFileIfExists(chunkX, chunkZ);

        final DataInputStream input = regionFile == null ? null : regionFile.getChunkDataInputStream(new ChunkPos(chunkX, chunkZ));

        if (input == null) {
            return new ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData(
                ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData.ReadResult.NO_DATA, null, null
            );
        }

        final ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData ret = new ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData(
            ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData.ReadResult.HAS_DATA, input, null
        );

        if (!(input instanceof ca.spottedleaf.moonrise.patches.chunk_system.util.stream.ExternalChunkStreamMarker)) {
            // internal stream, which is fully read
            return ret;
        }

        final CompoundTag syncRead = this.moonrise$finishRead(chunkX, chunkZ, ret);

        if (syncRead == null) {
            // need to try again
            return this.moonrise$readData(chunkX, chunkZ);
        }

        return new ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData(
            ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData.ReadResult.SYNC_READ, null, syncRead
        );
    }

    // if the return value is null, then the caller needs to re-try with a new call to readData()
    @Override
    public final CompoundTag moonrise$finishRead(
        final int chunkX, final int chunkZ, final ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData readData
    ) throws IOException {
        try {
            return NbtIo.read(readData.input());
        } finally {
            readData.input().close();
        }
    }
    // Paper end - rewrite chunk system
    // Paper start - rewrite chunk system
    public RegionFile getRegionFile(ChunkPos chunkcoordintpair) throws IOException {
        return this.getRegionFile(chunkcoordintpair, false);
    }
    // Paper end - rewrite chunk system

    protected RegionFileStorage(RegionStorageInfo info, Path folder, boolean sync) { // Paper - protected
        this.folder = folder;
        this.sync = sync;
        this.info = info;
        this.isChunkData = isChunkDataFolder(this.folder); // Paper - recalculate region file headers
    }

    @org.jetbrains.annotations.Contract("_, false -> !null") @Nullable private RegionFile getRegionFile(ChunkPos chunkPos, boolean existingOnly) throws IOException { // CraftBukkit
        // Paper start - rewrite chunk system
        if (existingOnly) {
            return this.moonrise$getRegionFileIfExists(chunkPos.x, chunkPos.z);
        }
        synchronized (this) {
            final long key = ChunkPos.asLong(chunkPos.x >> REGION_SHIFT, chunkPos.z >> REGION_SHIFT);

            RegionFile ret = this.regionCache.getAndMoveToFirst(key);
            if (ret != null) {
                return ret;
            }

            if (this.regionCache.size() >= io.papermc.paper.configuration.GlobalConfiguration.get().misc.regionFileCacheSize) { // Paper
                this.regionCache.removeLast().close();
            }

            final Path regionPath = this.folder.resolve(getRegionFileName(chunkPos.x, chunkPos.z));

            this.createRegionFile(key);

            FileUtil.createDirectoriesSafe(this.folder);

            ret = new RegionFile(this.info, regionPath, this.folder, this.sync);

            this.regionCache.putAndMoveToFirst(key, ret);

            return ret;
        }
        // Paper end - rewrite chunk system
    }

    // Paper start
    private static void printOversizedLog(String msg, Path file, int x, int z) {
        org.apache.logging.log4j.LogManager.getLogger().fatal(msg + " (" + file.toString().replaceAll(".+[\\\\/]", "") + " - " + x + "," + z + ") Go clean it up to remove this message. /minecraft:tp " + (x<<4)+" 128 "+(z<<4) + " - DO NOT REPORT THIS TO PAPER - You may ask for help on Discord, but do not file an issue. These error messages can not be removed.");
    }

    private static CompoundTag readOversizedChunk(RegionFile regionfile, ChunkPos chunkCoordinate) throws IOException {
        synchronized (regionfile) {
            try (DataInputStream datainputstream = regionfile.getChunkDataInputStream(chunkCoordinate)) {
                CompoundTag oversizedData = regionfile.getOversizedData(chunkCoordinate.x, chunkCoordinate.z);
                CompoundTag chunk = NbtIo.read(datainputstream);
                if (oversizedData == null) {
                    return chunk;
                }
                CompoundTag oversizedLevel = oversizedData.getCompound("Level");

                mergeChunkList(chunk.getCompound("Level"), oversizedLevel, "Entities", "Entities");
                mergeChunkList(chunk.getCompound("Level"), oversizedLevel, "TileEntities", "TileEntities");

                return chunk;
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                throw throwable;
            }
        }
    }

    private static void mergeChunkList(CompoundTag level, CompoundTag oversizedLevel, String key, String oversizedKey) {
        net.minecraft.nbt.ListTag levelList = level.getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND);
        net.minecraft.nbt.ListTag oversizedList = oversizedLevel.getList(oversizedKey, net.minecraft.nbt.Tag.TAG_COMPOUND);

        if (!oversizedList.isEmpty()) {
            levelList.addAll(oversizedList);
            level.put(key, levelList);
        }
    }
    // Paper end

    @Nullable
    public CompoundTag read(ChunkPos chunkPos) throws IOException {
        // CraftBukkit start - SPIGOT-5680: There's no good reason to preemptively create files on read, save that for writing
        RegionFile regionFile = this.getRegionFile(chunkPos, true);
        if (regionFile == null) {
            return null;
        }
        // CraftBukkit end
        // Paper start
        if (regionFile.isOversized(chunkPos.x, chunkPos.z)) {
            printOversizedLog("Loading Oversized Chunk!", regionFile.getPath(), chunkPos.x, chunkPos.z);
            return readOversizedChunk(regionFile, chunkPos);
        }
        // Paper end

        CompoundTag var4;
        try (DataInputStream chunkDataInputStream = regionFile.getChunkDataInputStream(chunkPos)) {
            if (chunkDataInputStream == null) {
                return null;
            }

            var4 = NbtIo.read(chunkDataInputStream);
            // Paper start - recover from corrupt regionfile header
            if (this.isChunkData) {
                ChunkPos headerChunkPos = SerializableChunkData.getChunkCoordinate(var4);
                if (!headerChunkPos.equals(chunkPos)) {
                    net.minecraft.server.MinecraftServer.LOGGER.error("Attempting to read chunk data at " + chunkPos + " but got chunk data for " + headerChunkPos + " instead! Attempting regionfile recalculation for regionfile " + regionFile.getPath().toAbsolutePath());
                    if (regionFile.recalculateHeader()) {
                        return this.read(chunkPos);
                    }
                    net.minecraft.server.MinecraftServer.LOGGER.error("Can't recalculate regionfile header, regenerating chunk " + chunkPos + " for " + regionFile.getPath().toAbsolutePath());
                    return null;
                }
            }
            // Paper end - recover from corrupt regionfile header
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

    public void write(ChunkPos chunkPos, @Nullable CompoundTag chunkData) throws IOException { // Paper - rewrite chunk system - public
        RegionFile regionFile = this.getRegionFile(chunkPos, chunkData == null); // CraftBukkit // Paper - rewrite chunk system
        // Paper start - rewrite chunk system
        if (regionFile == null) {
            // if the RegionFile doesn't exist, no point in deleting from it
            return;
        }
        // Paper end - rewrite chunk system
        if (chunkData == null) {
            regionFile.clear(chunkPos);
        } else {
            DataOutputStream chunkDataOutputStream = regionFile.getChunkDataOutputStream(chunkPos); // Paper - Only write if successful
            try { // Paper - Only write if successful
                NbtIo.write(chunkData, chunkDataOutputStream);
                regionFile.setOversized(chunkPos.x, chunkPos.z, false); // Paper - We don't do this anymore, mojang stores differently, but clear old meta flag if it exists to get rid of our own meta file once last oversized is gone
                // Paper start - don't write garbage data to disk if writing serialization fails
                chunkDataOutputStream.close();
            } catch (final RegionFileSizeException ex) {
                regionFile.clear(chunkPos);
                final int maxSize = RegionFile.MAX_CHUNK_SIZE / (1024 * 1024);
                LOGGER.error("Chunk at (" + chunkPos.x + "," + chunkPos.z + ") in regionfile '" + regionFile.getPath().toString() + "' exceeds max size of " + maxSize + "MiB, it has been deleted from disk.");
                // Paper end - don't write garbage data to disk if writing serialization fails
            }
        }
    }

    @Override
    public void close() throws IOException {
        // Paper start - rewrite chunk system
        synchronized (this) {
            final ExceptionCollector<IOException> exceptionCollector = new ExceptionCollector<>();
            for (final RegionFile regionFile : this.regionCache.values()) {
                try {
                    regionFile.close();
                } catch (final IOException ex) {
                    exceptionCollector.add(ex);
                }
            }
            exceptionCollector.throwIfPresent();
        }
        // Paper end - rewrite chunk system
    }

    public void flush() throws IOException {
        // Paper start - rewrite chunk system
        synchronized (this) {
            final ExceptionCollector<IOException> exceptionCollector = new ExceptionCollector<>();
            for (final RegionFile regionFile : this.regionCache.values()) {
                try {
                    regionFile.flush();
                } catch (final IOException ex) {
                    exceptionCollector.add(ex);
                }
            }

            exceptionCollector.throwIfPresent();
        }
        // Paper end - rewrite chunk system
    }

    public RegionStorageInfo info() {
        return this.info;
    }

    // Paper start - don't write garbage data to disk if writing serialization fails
    public static final class RegionFileSizeException extends RuntimeException {

        public RegionFileSizeException(final String message) {
            super(message);
        }
    }
    // Paper end - don't write garbage data to disk if writing serialization fails
}
