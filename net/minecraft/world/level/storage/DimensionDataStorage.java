package net.minecraft.world.level.storage;

import com.google.common.collect.Iterables;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.FastBufferedInputStream;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public class DimensionDataStorage implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    public final Map<String, Optional<SavedData>> cache = new HashMap<>();
    private final DataFixer fixerUpper;
    private final HolderLookup.Provider registries;
    private final Path dataFolder;
    private CompletableFuture<?> pendingWriteFuture = CompletableFuture.completedFuture(null);

    public DimensionDataStorage(Path dataFolder, DataFixer fixerUpper, HolderLookup.Provider registries) {
        this.fixerUpper = fixerUpper;
        this.dataFolder = dataFolder;
        this.registries = registries;
    }

    private Path getDataFile(String filename) {
        return this.dataFolder.resolve(filename + ".dat");
    }

    public <T extends SavedData> T computeIfAbsent(SavedData.Factory<T> factory, String name) {
        T savedData = this.get(factory, name);
        if (savedData != null) {
            return savedData;
        } else {
            T savedData1 = (T)factory.constructor().get();
            this.set(name, savedData1);
            return savedData1;
        }
    }

    @Nullable
    public <T extends SavedData> T get(SavedData.Factory<T> factory, String name) {
        Optional<SavedData> optional = this.cache.get(name);
        if (optional == null) {
            optional = Optional.ofNullable(this.readSavedData(factory.deserializer(), factory.type(), name));
            this.cache.put(name, optional);
        }

        return (T)optional.orElse(null);
    }

    @Nullable
    private <T extends SavedData> T readSavedData(BiFunction<CompoundTag, HolderLookup.Provider, T> reader, DataFixTypes dataFixType, String filename) {
        try {
            Path dataFile = this.getDataFile(filename);
            if (Files.exists(dataFile)) {
                CompoundTag tagFromDisk = this.readTagFromDisk(filename, dataFixType, SharedConstants.getCurrentVersion().getDataVersion().getVersion());
                return reader.apply(tagFromDisk.getCompound("data"), this.registries);
            }
        } catch (Exception var6) {
            LOGGER.error("Error loading saved data: {}", filename, var6);
        }

        return null;
    }

    public void set(String name, SavedData savedData) {
        this.cache.put(name, Optional.of(savedData));
        savedData.setDirty();
    }

    public CompoundTag readTagFromDisk(String filename, DataFixTypes dataFixType, int version) throws IOException {
        CompoundTag var8;
        try (
            InputStream inputStream = Files.newInputStream(this.getDataFile(filename));
            PushbackInputStream pushbackInputStream = new PushbackInputStream(new FastBufferedInputStream(inputStream), 2);
        ) {
            CompoundTag compressed;
            if (this.isGzip(pushbackInputStream)) {
                compressed = NbtIo.readCompressed(pushbackInputStream, NbtAccounter.unlimitedHeap());
            } else {
                try (DataInputStream dataInputStream = new DataInputStream(pushbackInputStream)) {
                    compressed = NbtIo.read(dataInputStream);
                }
            }

            int dataVersion = NbtUtils.getDataVersion(compressed, 1343);
            var8 = dataFixType.update(this.fixerUpper, compressed, dataVersion, version);
        }

        return var8;
    }

    private boolean isGzip(PushbackInputStream inputStream) throws IOException {
        byte[] bytes = new byte[2];
        boolean flag = false;
        int i = inputStream.read(bytes, 0, 2);
        if (i == 2) {
            int i1 = (bytes[1] & 255) << 8 | bytes[0] & 255;
            if (i1 == 35615) {
                flag = true;
            }
        }

        if (i != 0) {
            inputStream.unread(bytes, 0, i);
        }

        return flag;
    }

    public CompletableFuture<?> scheduleSave() {
        Map<Path, CompoundTag> map = this.collectDirtyTagsToSave();
        if (map.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        } else {
            int i = Util.maxAllowedExecutorThreads();
            int size = map.size();
            if (false && size > i) { // Paper - Separate dimension data IO pool; just throw them into the fixed pool queue
                this.pendingWriteFuture = this.pendingWriteFuture.thenCompose(object -> {
                    List<CompletableFuture<?>> list = new ArrayList<>(i);
                    int i1 = Mth.positiveCeilDiv(size, i);

                    for (List<Entry<Path, CompoundTag>> list1 : Iterables.partition(map.entrySet(), i1)) {
                        list.add(CompletableFuture.runAsync(() -> {
                            for (Entry<Path, CompoundTag> entry : list1) {
                                tryWrite(entry.getKey(), entry.getValue());
                            }
                        }, Util.ioPool()));
                    }

                    return CompletableFuture.allOf(list.toArray(CompletableFuture[]::new));
                });
            } else {
                this.pendingWriteFuture = this.pendingWriteFuture
                    .thenCompose(
                        object -> CompletableFuture.allOf(
                            map.entrySet()
                                .stream()
                                .map(entry -> CompletableFuture.runAsync(() -> tryWrite(entry.getKey(), entry.getValue()), Util.DIMENSION_DATA_IO_POOL)) // Paper - Separate dimension data IO pool
                                .toArray(CompletableFuture[]::new)
                        )
                    );
            }

            return this.pendingWriteFuture;
        }
    }

    private Map<Path, CompoundTag> collectDirtyTagsToSave() {
        Map<Path, CompoundTag> map = new Object2ObjectArrayMap<>();
        this.cache
            .forEach(
                (string, optional) -> optional.filter(SavedData::isDirty)
                    .ifPresent(savedData -> map.put(this.getDataFile(string), savedData.save(this.registries)))
            );
        return map;
    }

    private static void tryWrite(Path path, CompoundTag tag) {
        try {
            NbtIo.writeCompressed(tag, path);
        } catch (IOException var3) {
            LOGGER.error("Could not save data to {}", path.getFileName(), var3);
        }
    }

    public void saveAndJoin() {
        this.scheduleSave().join();
    }

    @Override
    public void close() {
        this.saveAndJoin();
    }
}
