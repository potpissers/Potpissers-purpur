package net.minecraft.world.level.storage;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public class CommandStorage {
    private static final String ID_PREFIX = "command_storage_";
    private final Map<String, CommandStorage.Container> namespaces = Maps.newHashMap();
    private final DimensionDataStorage storage;

    public CommandStorage(DimensionDataStorage storage) {
        this.storage = storage;
    }

    private CommandStorage.Container newStorage(String namespace) {
        CommandStorage.Container container = new CommandStorage.Container();
        this.namespaces.put(namespace, container);
        return container;
    }

    private SavedData.Factory<CommandStorage.Container> factory(String namespace) {
        return new SavedData.Factory<>(
            () -> this.newStorage(namespace), (compoundTag, provider) -> this.newStorage(namespace).load(compoundTag), DataFixTypes.SAVED_DATA_COMMAND_STORAGE
        );
    }

    public CompoundTag get(ResourceLocation id) {
        String namespace = id.getNamespace();
        CommandStorage.Container container = this.storage.get(this.factory(namespace), createId(namespace));
        return container != null ? container.get(id.getPath()) : new CompoundTag();
    }

    public void set(ResourceLocation id, CompoundTag nbt) {
        String namespace = id.getNamespace();
        this.storage.computeIfAbsent(this.factory(namespace), createId(namespace)).put(id.getPath(), nbt);
    }

    public Stream<ResourceLocation> keys() {
        return this.namespaces.entrySet().stream().flatMap(entry -> entry.getValue().getKeys(entry.getKey()));
    }

    private static String createId(String namespace) {
        return "command_storage_" + namespace;
    }

    static class Container extends SavedData {
        private static final String TAG_CONTENTS = "contents";
        private final Map<String, CompoundTag> storage = Maps.newHashMap();

        CommandStorage.Container load(CompoundTag compoundTag) {
            CompoundTag compound = compoundTag.getCompound("contents");

            for (String string : compound.getAllKeys()) {
                this.storage.put(string, compound.getCompound(string));
            }

            return this;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            CompoundTag compoundTag = new CompoundTag();
            this.storage.forEach((key, storageCompoundTag) -> compoundTag.put(key, storageCompoundTag.copy()));
            tag.put("contents", compoundTag);
            return tag;
        }

        public CompoundTag get(String id) {
            CompoundTag compoundTag = this.storage.get(id);
            return compoundTag != null ? compoundTag : new CompoundTag();
        }

        public void put(String id, CompoundTag nbt) {
            if (nbt.isEmpty()) {
                this.storage.remove(id);
            } else {
                this.storage.put(id, nbt);
            }

            this.setDirty();
        }

        public Stream<ResourceLocation> getKeys(String namespace) {
            return this.storage.keySet().stream().map(key -> ResourceLocation.fromNamespaceAndPath(namespace, key));
        }
    }
}
