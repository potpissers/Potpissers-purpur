package net.minecraft.world.level.chunk.storage;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

public class RegionFile implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SECTOR_BYTES = 4096;
    @VisibleForTesting
    protected static final int SECTOR_INTS = 1024;
    private static final int CHUNK_HEADER_SIZE = 5;
    private static final int HEADER_OFFSET = 0;
    private static final ByteBuffer PADDING_BUFFER = ByteBuffer.allocateDirect(1);
    private static final String EXTERNAL_FILE_EXTENSION = ".mcc";
    private static final int EXTERNAL_STREAM_FLAG = 128;
    private static final int EXTERNAL_CHUNK_THRESHOLD = 256;
    private static final int CHUNK_NOT_PRESENT = 0;
    final RegionStorageInfo info;
    private final Path path;
    private final FileChannel file;
    private final Path externalFileDir;
    final RegionFileVersion version;
    private final ByteBuffer header = ByteBuffer.allocateDirect(8192);
    private final IntBuffer offsets;
    private final IntBuffer timestamps;
    @VisibleForTesting
    protected final RegionBitmap usedSectors = new RegionBitmap();

    public RegionFile(RegionStorageInfo info, Path path, Path externalFileDir, boolean sync) throws IOException {
        this(info, path, externalFileDir, RegionFileVersion.getCompressionFormat(), sync); // Paper - Configurable region compression format
    }

    public RegionFile(RegionStorageInfo info, Path path, Path externalFileDir, RegionFileVersion version, boolean sync) throws IOException {
        this.info = info;
        this.path = path;
        this.version = version;
        this.initOversizedState(); // Paper
        if (!Files.isDirectory(externalFileDir)) {
            throw new IllegalArgumentException("Expected directory, got " + externalFileDir.toAbsolutePath());
        } else {
            this.externalFileDir = externalFileDir;
            this.offsets = this.header.asIntBuffer();
            this.offsets.limit(1024);
            this.header.position(4096);
            this.timestamps = this.header.asIntBuffer();
            if (sync) {
                this.file = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
            } else {
                this.file = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            }

            this.usedSectors.force(0, 2);
            this.header.position(0);
            int i = this.file.read(this.header, 0L);
            if (i != -1) {
                if (i != 8192) {
                    LOGGER.warn("Region file {} has truncated header: {}", path, i);
                }

                long size = Files.size(path);

                for (int i1 = 0; i1 < 1024; i1++) {
                    int i2 = this.offsets.get(i1);
                    if (i2 != 0) {
                        int sectorNumber = getSectorNumber(i2);
                        int numSectors = getNumSectors(i2);
                        // Spigot start
                        if (numSectors == 255) {
                            // We're maxed out, so we need to read the proper length from the section
                            ByteBuffer realLen = ByteBuffer.allocate(4);
                            this.file.read(realLen, sectorNumber * 4096);
                            numSectors = (realLen.getInt(0) + 4) / 4096 + 1;
                        }
                        // Spigot end
                        if (sectorNumber < 2) {
                            LOGGER.warn("Region file {} has invalid sector at index: {}; sector {} overlaps with header", path, i1, sectorNumber);
                            this.offsets.put(i1, 0);
                        } else if (numSectors == 0) {
                            LOGGER.warn("Region file {} has an invalid sector at index: {}; size has to be > 0", path, i1);
                            this.offsets.put(i1, 0);
                        } else if (sectorNumber * 4096L > size) {
                            LOGGER.warn("Region file {} has an invalid sector at index: {}; sector {} is out of bounds", path, i1, sectorNumber);
                            this.offsets.put(i1, 0);
                        } else {
                            this.usedSectors.force(sectorNumber, numSectors);
                        }
                    }
                }
            }
        }
    }

    public Path getPath() {
        return this.path;
    }

    private Path getExternalChunkPath(ChunkPos chunkPos) {
        String string = "c." + chunkPos.x + "." + chunkPos.z + ".mcc";
        return this.externalFileDir.resolve(string);
    }

    @Nullable
    public synchronized DataInputStream getChunkDataInputStream(ChunkPos chunkPos) throws IOException {
        int offset = this.getOffset(chunkPos);
        if (offset == 0) {
            return null;
        } else {
            int sectorNumber = getSectorNumber(offset);
            int numSectors = getNumSectors(offset);
            // Spigot start
            if (numSectors == 255) {
                ByteBuffer realLen = ByteBuffer.allocate(4);
                this.file.read(realLen, sectorNumber * 4096);
                numSectors = (realLen.getInt(0) + 4) / 4096 + 1;
            }
            // Spigot end
            int i = numSectors * 4096;
            ByteBuffer byteBuffer = ByteBuffer.allocate(i);
            this.file.read(byteBuffer, sectorNumber * 4096);
            byteBuffer.flip();
            if (byteBuffer.remaining() < 5) {
                LOGGER.error("Chunk {} header is truncated: expected {} but read {}", chunkPos, i, byteBuffer.remaining());
                return null;
            } else {
                int _int = byteBuffer.getInt();
                byte b = byteBuffer.get();
                if (_int == 0) {
                    LOGGER.warn("Chunk {} is allocated, but stream is missing", chunkPos);
                    return null;
                } else {
                    int i1 = _int - 1;
                    if (isExternalStreamChunk(b)) {
                        if (i1 != 0) {
                            LOGGER.warn("Chunk has both internal and external streams");
                        }

                        return this.createExternalChunkInputStream(chunkPos, getExternalChunkVersion(b));
                    } else if (i1 > byteBuffer.remaining()) {
                        LOGGER.error("Chunk {} stream is truncated: expected {} but read {}", chunkPos, i1, byteBuffer.remaining());
                        return null;
                    } else if (i1 < 0) {
                        LOGGER.error("Declared size {} of chunk {} is negative", _int, chunkPos);
                        return null;
                    } else {
                        JvmProfiler.INSTANCE.onRegionFileRead(this.info, chunkPos, this.version, i1);
                        return this.createChunkInputStream(chunkPos, b, createStream(byteBuffer, i1));
                    }
                }
            }
        }
    }

    private static int getTimestamp() {
        return (int)(Util.getEpochMillis() / 1000L);
    }

    private static boolean isExternalStreamChunk(byte versionByte) {
        return (versionByte & 128) != 0;
    }

    private static byte getExternalChunkVersion(byte versionByte) {
        return (byte)(versionByte & -129);
    }

    @Nullable
    private DataInputStream createChunkInputStream(ChunkPos chunkPos, byte versionByte, InputStream inputStream) throws IOException {
        RegionFileVersion regionFileVersion = RegionFileVersion.fromId(versionByte);
        if (regionFileVersion == RegionFileVersion.VERSION_CUSTOM) {
            String utf = new DataInputStream(inputStream).readUTF();
            ResourceLocation resourceLocation = ResourceLocation.tryParse(utf);
            if (resourceLocation != null) {
                LOGGER.error("Unrecognized custom compression {}", resourceLocation);
                return null;
            } else {
                LOGGER.error("Invalid custom compression id {}", utf);
                return null;
            }
        } else if (regionFileVersion == null) {
            LOGGER.error("Chunk {} has invalid chunk stream version {}", chunkPos, versionByte);
            return null;
        } else {
            return new DataInputStream(regionFileVersion.wrap(inputStream));
        }
    }

    @Nullable
    private DataInputStream createExternalChunkInputStream(ChunkPos chunkPos, byte versionByte) throws IOException {
        Path externalChunkPath = this.getExternalChunkPath(chunkPos);
        if (!Files.isRegularFile(externalChunkPath)) {
            LOGGER.error("External chunk path {} is not file", externalChunkPath);
            return null;
        } else {
            return this.createChunkInputStream(chunkPos, versionByte, Files.newInputStream(externalChunkPath));
        }
    }

    private static ByteArrayInputStream createStream(ByteBuffer sourceBuffer, int length) {
        return new ByteArrayInputStream(sourceBuffer.array(), sourceBuffer.position(), length);
    }

    private int packSectorOffset(int sectorOffset, int sectorCount) {
        return sectorOffset << 8 | sectorCount;
    }

    private static int getNumSectors(int packedSectorOffset) {
        return packedSectorOffset & 0xFF;
    }

    private static int getSectorNumber(int packedSectorOffset) {
        return packedSectorOffset >> 8 & 16777215;
    }

    private static int sizeToSectors(int size) {
        return (size + 4096 - 1) / 4096;
    }

    public boolean doesChunkExist(ChunkPos chunkPos) {
        int offset = this.getOffset(chunkPos);
        if (offset == 0) {
            return false;
        } else {
            int sectorNumber = getSectorNumber(offset);
            int numSectors = getNumSectors(offset);
            ByteBuffer byteBuffer = ByteBuffer.allocate(5);

            try {
                this.file.read(byteBuffer, sectorNumber * 4096);
                byteBuffer.flip();
                if (byteBuffer.remaining() != 5) {
                    return false;
                } else {
                    int _int = byteBuffer.getInt();
                    byte b = byteBuffer.get();
                    if (isExternalStreamChunk(b)) {
                        if (!RegionFileVersion.isValidVersion(getExternalChunkVersion(b))) {
                            return false;
                        }

                        if (!Files.isRegularFile(this.getExternalChunkPath(chunkPos))) {
                            return false;
                        }
                    } else {
                        if (!RegionFileVersion.isValidVersion(b)) {
                            return false;
                        }

                        if (_int == 0) {
                            return false;
                        }

                        int i = _int - 1;
                        if (i < 0 || i > 4096 * numSectors) {
                            return false;
                        }
                    }

                    return true;
                }
            } catch (IOException var9) {
                com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(var9); // Paper - ServerExceptionEvent
                return false;
            }
        }
    }

    public DataOutputStream getChunkDataOutputStream(ChunkPos chunkPos) throws IOException {
        return new DataOutputStream(this.version.wrap(new RegionFile.ChunkBuffer(chunkPos)));
    }

    public void flush() throws IOException {
        this.file.force(true);
    }

    public void clear(ChunkPos chunkPos) throws IOException {
        int offsetIndex = getOffsetIndex(chunkPos);
        int i = this.offsets.get(offsetIndex);
        if (i != 0) {
            this.offsets.put(offsetIndex, 0);
            this.timestamps.put(offsetIndex, getTimestamp());
            this.writeHeader();
            Files.deleteIfExists(this.getExternalChunkPath(chunkPos));
            this.usedSectors.free(getSectorNumber(i), getNumSectors(i));
        }
    }

    protected synchronized void write(ChunkPos chunkPos, ByteBuffer chunkData) throws IOException {
        int offsetIndex = getOffsetIndex(chunkPos);
        int i = this.offsets.get(offsetIndex);
        int sectorNumber = getSectorNumber(i);
        int numSectors = getNumSectors(i);
        int i1 = chunkData.remaining();
        int i2 = sizeToSectors(i1);
        int i3;
        RegionFile.CommitOp commitOp;
        if (i2 >= 256) {
            Path externalChunkPath = this.getExternalChunkPath(chunkPos);
            LOGGER.warn("Saving oversized chunk {} ({} bytes} to external file {}", chunkPos, i1, externalChunkPath);
            i2 = 1;
            i3 = this.usedSectors.allocate(i2);
            commitOp = this.writeToExternalFile(externalChunkPath, chunkData);
            ByteBuffer byteBuffer = this.createExternalStub();
            this.file.write(byteBuffer, i3 * 4096);
        } else {
            i3 = this.usedSectors.allocate(i2);
            commitOp = () -> Files.deleteIfExists(this.getExternalChunkPath(chunkPos));
            this.file.write(chunkData, i3 * 4096);
        }

        this.offsets.put(offsetIndex, this.packSectorOffset(i3, i2));
        this.timestamps.put(offsetIndex, getTimestamp());
        this.writeHeader();
        commitOp.run();
        if (sectorNumber != 0) {
            this.usedSectors.free(sectorNumber, numSectors);
        }
    }

    private ByteBuffer createExternalStub() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(5);
        byteBuffer.putInt(1);
        byteBuffer.put((byte)(this.version.getId() | 128));
        byteBuffer.flip();
        return byteBuffer;
    }

    private RegionFile.CommitOp writeToExternalFile(Path externalChunkFile, ByteBuffer chunkData) throws IOException {
        Path path = Files.createTempFile(this.externalFileDir, "tmp", null);

        try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            chunkData.position(5);
            fileChannel.write(chunkData);
            // Paper start - ServerExceptionEvent
        } catch (Throwable throwable) {
            com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(throwable);
            throw throwable;
            // Paper end - ServerExceptionEvent
        }

        return () -> Files.move(path, externalChunkFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private void writeHeader() throws IOException {
        this.header.position(0);
        this.file.write(this.header, 0L);
    }

    private int getOffset(ChunkPos chunkPos) {
        return this.offsets.get(getOffsetIndex(chunkPos));
    }

    public boolean hasChunk(ChunkPos chunkPos) {
        return this.getOffset(chunkPos) != 0;
    }

    private static int getOffsetIndex(ChunkPos chunkPos) {
        return chunkPos.getRegionLocalX() + chunkPos.getRegionLocalZ() * 32;
    }

    @Override
    public void close() throws IOException {
        try {
            this.padToFullSector();
        } finally {
            try {
                this.file.force(true);
            } finally {
                this.file.close();
            }
        }
    }

    private void padToFullSector() throws IOException {
        int i = (int)this.file.size();
        int i1 = sizeToSectors(i) * 4096;
        if (i != i1) {
            ByteBuffer byteBuffer = PADDING_BUFFER.duplicate();
            byteBuffer.position(0);
            this.file.write(byteBuffer, i1 - 1);
        }
    }

    class ChunkBuffer extends ByteArrayOutputStream {
        private final ChunkPos pos;

        public ChunkBuffer(final ChunkPos pos) {
            super(8096);
            super.write(0);
            super.write(0);
            super.write(0);
            super.write(0);
            super.write(RegionFile.this.version.getId());
            this.pos = pos;
        }

        @Override
        public void close() throws IOException {
            ByteBuffer byteBuffer = ByteBuffer.wrap(this.buf, 0, this.count);
            int i = this.count - 5 + 1;
            JvmProfiler.INSTANCE.onRegionFileWrite(RegionFile.this.info, this.pos, RegionFile.this.version, i);
            byteBuffer.putInt(0, i);
            RegionFile.this.write(this.pos, byteBuffer);
        }
    }

    interface CommitOp {
        void run() throws IOException;
    }

    // Paper start
    private final byte[] oversized = new byte[1024];
    private int oversizedCount;

    private synchronized void initOversizedState() throws IOException {
        Path metaFile = getOversizedMetaFile();
        if (Files.exists(metaFile)) {
            final byte[] read = java.nio.file.Files.readAllBytes(metaFile);
            System.arraycopy(read, 0, oversized, 0, oversized.length);
            for (byte temp : oversized) {
                oversizedCount += temp;
            }
        }
    }

    private static int getChunkIndex(int x, int z) {
        return (x & 31) + (z & 31) * 32;
    }

    synchronized boolean isOversized(int x, int z) {
        return this.oversized[getChunkIndex(x, z)] == 1;
    }

    synchronized void setOversized(int x, int z, boolean oversized) throws IOException {
        final int offset = getChunkIndex(x, z);
        boolean previous = this.oversized[offset] == 1;
        this.oversized[offset] = (byte) (oversized ? 1 : 0);
        if (!previous && oversized) {
            oversizedCount++;
        } else if (!oversized && previous) {
            oversizedCount--;
        }
        if (previous && !oversized) {
            Path oversizedFile = getOversizedFile(x, z);
            if (Files.exists(oversizedFile)) {
                Files.delete(oversizedFile);
            }
        }
        if (oversizedCount > 0) {
            if (previous != oversized) {
                writeOversizedMeta();
            }
        } else if (previous) {
            Path oversizedMetaFile = getOversizedMetaFile();
            if (Files.exists(oversizedMetaFile)) {
                Files.delete(oversizedMetaFile);
            }
        }
    }

    private void writeOversizedMeta() throws IOException {
        java.nio.file.Files.write(getOversizedMetaFile(), oversized);
    }

    private Path getOversizedMetaFile() {
        return this.path.getParent().resolve(this.path.getFileName().toString().replaceAll("\\.mca$", "") + ".oversized.nbt");
    }

    private Path getOversizedFile(int x, int z) {
        return this.path.getParent().resolve(this.path.getFileName().toString().replaceAll("\\.mca$", "") + "_oversized_" + x + "_" + z + ".nbt");
    }

    synchronized net.minecraft.nbt.CompoundTag getOversizedData(int x, int z) throws IOException {
        Path file = getOversizedFile(x, z);
        try (DataInputStream out = new DataInputStream(new java.io.BufferedInputStream(new java.util.zip.InflaterInputStream(Files.newInputStream(file))))) {
            return net.minecraft.nbt.NbtIo.read((java.io.DataInput) out);
        }

    }
    // Paper end
}
