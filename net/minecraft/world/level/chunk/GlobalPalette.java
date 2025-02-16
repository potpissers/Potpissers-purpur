package net.minecraft.world.level.chunk;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;

public class GlobalPalette<T> implements Palette<T> {
    private final IdMap<T> registry;

    public GlobalPalette(IdMap<T> registry) {
        this.registry = registry;
    }

    public static <A> Palette<A> create(int bits, IdMap<A> registry, PaletteResize<A> resizeHandler, List<A> values) {
        return new GlobalPalette<>(registry);
    }

    @Override
    public int idFor(T state) {
        int id = this.registry.getId(state);
        return id == -1 ? 0 : id;
    }

    @Override
    public boolean maybeHas(Predicate<T> filter) {
        return true;
    }

    @Override
    public T valueFor(int id) {
        T object = this.registry.byId(id);
        if (object == null) {
            throw new MissingPaletteEntryException(id);
        } else {
            return object;
        }
    }

    @Override
    public void read(FriendlyByteBuf buffer) {
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
    }

    @Override
    public int getSerializedSize() {
        return 0;
    }

    @Override
    public int getSize() {
        return this.registry.size();
    }

    @Override
    public Palette<T> copy(PaletteResize<T> resizeHandler) {
        return this;
    }
}
