package net.minecraft.world.level.chunk;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import org.apache.commons.lang3.Validate;

public class LinearPalette<T> implements Palette<T>, ca.spottedleaf.moonrise.patches.fast_palette.FastPalette<T> { // Paper - optimise palette reads
    private final IdMap<T> registry;
    private final T[] values;
    private final PaletteResize<T> resizeHandler;
    private final int bits;
    private int size;

    // Paper start - optimise palette reads
    @Override
    public final T[] moonrise$getRawPalette(final ca.spottedleaf.moonrise.patches.fast_palette.FastPaletteData<T> container) {
        return this.values;
    }
    // Paper end - optimise palette reads

    private LinearPalette(IdMap<T> registry, int bits, PaletteResize<T> resizeHandler, List<T> values) {
        this.registry = registry;
        this.values = (T[])(new Object[1 << bits]);
        this.bits = bits;
        this.resizeHandler = resizeHandler;
        Validate.isTrue(values.size() <= this.values.length, "Can't initialize LinearPalette of size %d with %d entries", this.values.length, values.size());

        for (int i = 0; i < values.size(); i++) {
            this.values[i] = values.get(i);
        }

        this.size = values.size();
    }

    private LinearPalette(IdMap<T> registry, T[] values, PaletteResize<T> resizeHandler, int bits, int size) {
        this.registry = registry;
        this.values = values;
        this.resizeHandler = resizeHandler;
        this.bits = bits;
        this.size = size;
    }

    public static <A> Palette<A> create(int bits, IdMap<A> registry, PaletteResize<A> resizeHandler, List<A> values) {
        return new LinearPalette<>(registry, bits, resizeHandler, values);
    }

    @Override
    public int idFor(T state) {
        for (int i = 0; i < this.size; i++) {
            if (this.values[i] == state) {
                return i;
            }
        }

        int ix = this.size;
        if (ix < this.values.length) {
            this.values[ix] = state;
            this.size++;
            return ix;
        } else {
            return this.resizeHandler.onResize(this.bits + 1, state);
        }
    }

    @Override
    public boolean maybeHas(Predicate<T> filter) {
        for (int i = 0; i < this.size; i++) {
            if (filter.test(this.values[i])) {
                return true;
            }
        }

        return false;
    }

    @Override
    public T valueFor(int id) {
        if (id >= 0 && id < this.size) {
            return this.values[id];
        } else {
            throw new MissingPaletteEntryException(id);
        }
    }

    @Override
    public void read(FriendlyByteBuf buffer) {
        this.size = buffer.readVarInt();

        for (int i = 0; i < this.size; i++) {
            this.values[i] = this.registry.byIdOrThrow(buffer.readVarInt());
        }
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.size);

        for (int i = 0; i < this.size; i++) {
            buffer.writeVarInt(this.registry.getId(this.values[i]));
        }
    }

    @Override
    public int getSerializedSize() {
        int byteSize = VarInt.getByteSize(this.getSize());

        for (int i = 0; i < this.getSize(); i++) {
            byteSize += VarInt.getByteSize(this.registry.getId(this.values[i]));
        }

        return byteSize;
    }

    @Override
    public int getSize() {
        return this.size;
    }

    @Override
    public Palette<T> copy(PaletteResize<T> resizeHandler) {
        return new LinearPalette<>(this.registry, (T[])((Object[])this.values.clone()), resizeHandler, this.bits, this.size);
    }
}
