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

    // CraftBukkit start
    public CompoundTag upgradeChunkTag(
        ResourceKey<net.minecraft.world.level.dimension.LevelStem> levelKey,
        Supplier<DimensionDataStorage> storage,
        CompoundTag chunkData,
        Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> chunkGeneratorKey,
        ChunkPos pos,
        @Nullable net.minecraft.world.level.LevelAccessor levelAccessor
        // CraftBukkit end
    ) {
        int version = getVersion(chunkData);
        if (version == SharedConstants.getCurrentVersion().getDataVersion().getVersion()) {
            return chunkData;
        } else {
            try {
                // CraftBukkit start
                if (version < 1466) {
                    CompoundTag level = chunkData.getCompound("Level");
                    if (level.getBoolean("TerrainPopulated") && !level.getBoolean("LightPopulated")) {
                        // Light is purged updating to 1.14+. We need to set light populated to true so the converter recognizes the chunk as being "full"
                        level.putBoolean("LightPopulated", true);
                    }
                }
                // CraftBukkit end
                if (version < 1493) {
                    chunkData = DataFixTypes.CHUNK.update(this.fixerUpper, chunkData, version, 1493);
                    if (chunkData.getCompound("Level").getBoolean("hasLegacyStructureData")) {
                        LegacyStructureDataHandler legacyStructureHandler = this.getLegacyStructureHandler(levelKey, storage);
                        chunkData = legacyStructureHandler.updateFromLegacy(chunkData);
                    }
                }

                // Spigot start - SPIGOT-6806: Quick and dirty way to prevent below zero generation in old chunks, by setting the status to heightmap instead of empty
                boolean stopBelowZero = false;
                boolean belowZeroGenerationInExistingChunks = (levelAccessor != null) ? ((net.minecraft.server.level.ServerLevel) levelAccessor).spigotConfig.belowZeroGenerationInExistingChunks : org.spigotmc.SpigotConfig.belowZeroGenerationInExistingChunks;

                if (version <= 2730 && !belowZeroGenerationInExistingChunks) {
                    stopBelowZero = "full".equals(chunkData.getCompound("Level").getString("Status"));
                }
                // Spigot end

                injectDatafixingContext(chunkData, levelKey, chunkGeneratorKey);
                chunkData = DataFixTypes.CHUNK.updateToCurrentVersion(this.fixerUpper, chunkData, Math.max(1493, version));
                // Spigot start
                if (stopBelowZero) {
                    chunkData.putString("Status", net.minecraft.core.registries.BuiltInRegistries.CHUNK_STATUS.getKey(net.minecraft.world.level.chunk.status.ChunkStatus.SPAWN).toString());
                }
                // Spigot end
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

    private LegacyStructureDataHandler getLegacyStructureHandler(ResourceKey<net.minecraft.world.level.dimension.LevelStem> level, Supplier<DimensionDataStorage> storage) { // CraftBukkit
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
        CompoundTag chunkData, ResourceKey<net.minecraft.world.level.dimension.LevelStem> levelKey, Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> chunkGeneratorKey // CraftBukkit
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
        // Paper start - guard against possible chunk pos desync
        final Supplier<CompoundTag> guardedPosCheck = () -> {
            CompoundTag nbt = tagSupplier.get();
            if (nbt != null && !pos.equals(SerializableChunkData.getChunkCoordinate(nbt))) {
                final String world = (ChunkStorage.this instanceof net.minecraft.server.level.ChunkMap) ? ((net.minecraft.server.level.ChunkMap) ChunkStorage.this).level.getWorld().getName() : null;
                throw new IllegalArgumentException("Chunk coordinate and serialized data do not have matching coordinates, trying to serialize coordinate " + pos
                    + " but compound says coordinate is " + SerializableChunkData.getChunkCoordinate(nbt) + (world == null ? " for an unknown world" : (" for world: " + world)));
            }
            return nbt;
        };
        // Paper end - guard against possible chunk pos desync
        this.handleLegacyStructureIndex(pos);
        return this.worker.store(pos, guardedPosCheck); // Paper - guard against possible chunk pos desync
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
