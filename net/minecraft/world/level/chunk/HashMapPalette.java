package net.minecraft.world.level.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;

public class HashMapPalette<T> implements Palette<T> {
    private final IdMap<T> registry;
    private final CrudeIncrementalIntIdentityHashBiMap<T> values;
    private final PaletteResize<T> resizeHandler;
    private final int bits;

    public HashMapPalette(IdMap<T> registry, int bits, PaletteResize<T> resizeHandler, List<T> values) {
        this(registry, bits, resizeHandler);
        values.forEach(this.values::add);
    }

    public HashMapPalette(IdMap<T> registry, int bits, PaletteResize<T> resizeHandler) {
        this(registry, bits, resizeHandler, CrudeIncrementalIntIdentityHashBiMap.create((1 << bits) + 1)); // Paper - Perf: Avoid unnecessary resize operation in CrudeIncrementalIntIdentityHashBiMap
    }

    private HashMapPalette(IdMap<T> registry, int bits, PaletteResize<T> resizeHandler, CrudeIncrementalIntIdentityHashBiMap<T> values) {
        this.registry = registry;
        this.bits = bits;
        this.resizeHandler = resizeHandler;
        this.values = values;
    }

    public static <A> Palette<A> create(int bits, IdMap<A> registry, PaletteResize<A> resizeHandler, List<A> values) {
        return new HashMapPalette<>(registry, bits, resizeHandler, values);
    }

    @Override
    public int idFor(T state) {
        int id = this.values.getId(state);
        if (id == -1) {
            // Paper start - Perf: Avoid unnecessary resize operation in CrudeIncrementalIntIdentityHashBiMap and optimize
            // We use size() instead of the result from add(K)
            // This avoids adding another object unnecessarily
            // Without this change, + 2 would be required in the constructor
            if (this.values.size() >= 1 << this.bits) {
                id = this.resizeHandler.onResize(this.bits + 1, state);
            } else {
                id = this.values.add(state);
            }
            // Paper end - Perf: Avoid unnecessary resize operation in CrudeIncrementalIntIdentityHashBiMap and optimize
        }

        return id;
    }

    @Override
    public boolean maybeHas(Predicate<T> filter) {
        for (int i = 0; i < this.getSize(); i++) {
            if (filter.test(this.values.byId(i))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public T valueFor(int id) {
        T object = this.values.byId(id);
        if (object == null) {
            throw new MissingPaletteEntryException(id);
        } else {
            return object;
        }
    }

    @Override
    public void read(FriendlyByteBuf buffer) {
        this.values.clear();
        int varInt = buffer.readVarInt();

        for (int i = 0; i < varInt; i++) {
            this.values.add(this.registry.byIdOrThrow(buffer.readVarInt()));
        }
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        int size = this.getSize();
        buffer.writeVarInt(size);

        for (int i = 0; i < size; i++) {
            buffer.writeVarInt(this.registry.getId(this.values.byId(i)));
        }
    }

    @Override
    public int getSerializedSize() {
        int byteSize = VarInt.getByteSize(this.getSize());

        for (int i = 0; i < this.getSize(); i++) {
            byteSize += VarInt.getByteSize(this.registry.getId(this.values.byId(i)));
        }

        return byteSize;
    }

    public List<T> getEntries() {
        ArrayList<T> list = new ArrayList<>();
        this.values.iterator().forEachRemaining(list::add);
        return list;
    }

    @Override
    public int getSize() {
        return this.values.size();
    }

    @Override
    public Palette<T> copy(PaletteResize<T> resizeHandler) {
        return new HashMapPalette<>(this.registry, this.bits, resizeHandler, this.values.copy());
    }
}
