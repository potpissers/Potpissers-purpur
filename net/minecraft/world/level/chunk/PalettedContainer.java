package net.minecraft.world.level.chunk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.stream.LongStream;
import javax.annotation.Nullable;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.util.BitStorage;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ThreadingDetector;
import net.minecraft.util.ZeroBitStorage;

public class PalettedContainer<T> implements PaletteResize<T>, PalettedContainerRO<T> {
    private static final int MIN_PALETTE_BITS = 0;
    private final PaletteResize<T> dummyPaletteResize = (bits, objectAdded) -> 0;
    public final IdMap<T> registry;
    private final T @org.jetbrains.annotations.Nullable [] presetValues; // Paper - Anti-Xray - Add preset values
    public volatile PalettedContainer.Data<T> data; // Paper - optimise collisions - public
    private final PalettedContainer.Strategy strategy;
    //private final ThreadingDetector threadingDetector = new ThreadingDetector("PalettedContainer"); // Paper - unused

    public void acquire() {
        // this.threadingDetector.checkAndLock(); // Paper - disable this - use proper synchronization
    }

    public void release() {
        // this.threadingDetector.checkAndUnlock(); // Paper - disable this - use proper synchronization
    }

    // Paper start - Anti-Xray - Add preset values
    @Deprecated @io.papermc.paper.annotation.DoNotUse
    public static <T> Codec<PalettedContainer<T>> codecRW(IdMap<T> registry, Codec<T> codec, PalettedContainer.Strategy strategy, T value) {
        return PalettedContainer.codecRW(registry, codec, strategy, value, null);
    }
    public static <T> Codec<PalettedContainer<T>> codecRW(IdMap<T> registry, Codec<T> codec, PalettedContainer.Strategy strategy, T value, T @org.jetbrains.annotations.Nullable [] presetValues) {
        PalettedContainerRO.Unpacker<T, PalettedContainer<T>> unpacker = (idListx, paletteProviderx, serialized) -> {
            return unpack(idListx, paletteProviderx, serialized, value, presetValues);
        };
        // Paper end
        return codec(registry, codec, strategy, value, unpacker);
    }

    public static <T> Codec<PalettedContainerRO<T>> codecRO(IdMap<T> registry, Codec<T> codec, PalettedContainer.Strategy strategy, T value) {
        PalettedContainerRO.Unpacker<T, PalettedContainerRO<T>> unpacker = (registry1, strategy1, packedData) -> unpack(registry1, strategy1, packedData, value, null) // Paper - Anti-Xray - Add preset values
            .map(container -> (PalettedContainerRO<T>)container);
        return codec(registry, codec, strategy, value, unpacker);
    }

    private static <T, C extends PalettedContainerRO<T>> Codec<C> codec(
        IdMap<T> registry, Codec<T> codec, PalettedContainer.Strategy strategy, T value, PalettedContainerRO.Unpacker<T, C> unpacker
    ) {
        return RecordCodecBuilder.<PalettedContainerRO.PackedData>create(
                instance -> instance.group(
                        codec.mapResult(ExtraCodecs.orElsePartial(value)).listOf().fieldOf("palette").forGetter(PalettedContainerRO.PackedData::paletteEntries),
                        Codec.LONG_STREAM.lenientOptionalFieldOf("data").forGetter(PalettedContainerRO.PackedData::storage)
                    )
                    .apply(instance, PalettedContainerRO.PackedData::new)
            )
            .comapFlatMap(
                packedData -> unpacker.read(registry, strategy, (PalettedContainerRO.PackedData<T>)packedData), container -> container.pack(registry, strategy)
            );
    }

    // Paper start - optimise palette reads
    private void updateData(final PalettedContainer.Data<T> data) {
        if (data != null) {
            ((ca.spottedleaf.moonrise.patches.fast_palette.FastPaletteData<T>)(Object)data).moonrise$setPalette(
                ((ca.spottedleaf.moonrise.patches.fast_palette.FastPalette<T>)data.palette).moonrise$getRawPalette((ca.spottedleaf.moonrise.patches.fast_palette.FastPaletteData<T>)(Object)data)
            );
        }
    }

    private T readPaletteSlow(final PalettedContainer.Data<T> data, final int paletteIdx) {
        return data.palette.valueFor(paletteIdx);
    }

    private T readPalette(final PalettedContainer.Data<T> data, final int paletteIdx) {
        final T[] palette = ((ca.spottedleaf.moonrise.patches.fast_palette.FastPaletteData<T>)(Object)data).moonrise$getPalette();
        if (palette == null) {
            return this.readPaletteSlow(data, paletteIdx);
        }

        final T ret = palette[paletteIdx];
        if (ret == null) {
            throw new IllegalArgumentException("Palette index out of bounds");
        }
        return ret;
    }
    // Paper end - optimise palette reads

    // Paper start - Anti-Xray - Add preset values
    @Deprecated @io.papermc.paper.annotation.DoNotUse
    public PalettedContainer(IdMap<T> registry, PalettedContainer.Strategy strategy, PalettedContainer.Configuration<T> configuration, BitStorage storage, List<T> values) {
        this(registry, strategy, configuration, storage, values, null, null);
    }
    public PalettedContainer(
        IdMap<T> registry, PalettedContainer.Strategy strategy, PalettedContainer.Configuration<T> configuration, BitStorage storage, List<T> values, T defaultValue, T @org.jetbrains.annotations.Nullable [] presetValues
    ) {
        this.presetValues = presetValues;
        this.registry = registry;
        this.strategy = strategy;
        this.data = new PalettedContainer.Data<>(configuration, storage, configuration.factory().create(configuration.bits(), registry, this, values));
        if (presetValues != null && (configuration.factory() == PalettedContainer.Strategy.SINGLE_VALUE_PALETTE_FACTORY ? this.data.palette.valueFor(0) != defaultValue : configuration.factory() != PalettedContainer.Strategy.GLOBAL_PALETTE_FACTORY)) {
            // In 1.18 Mojang unfortunately removed code that already handled possible resize operations on read from disk for us
            // We readd this here but in a smarter way than it was before
            int maxSize = 1 << configuration.bits();

            for (T presetValue : presetValues) {
                if (this.data.palette.getSize() >= maxSize) {
                    java.util.Set<T> allValues = new java.util.HashSet<>(values);
                    allValues.addAll(Arrays.asList(presetValues));
                    int newBits = Mth.ceillog2(allValues.size());

                    if (newBits > configuration.bits()) {
                        this.onResize(newBits, null);
                    }

                    break;
                }

                this.data.palette.idFor(presetValue);
            }
        }
        // Paper end
        this.updateData(this.data); // Paper - optimise palette reads
    }

    // Paper start - Anti-Xray - Add preset values
    private PalettedContainer(IdMap<T> registry, PalettedContainer.Strategy strategy, PalettedContainer.Data<T> data, T @org.jetbrains.annotations.Nullable [] presetValues) {
        this.presetValues = presetValues;
        // Paper end - Anti-Xray
        this.registry = registry;
        this.strategy = strategy;
        this.data = data;
        this.updateData(this.data); // Paper - optimise palette reads
    }

    private PalettedContainer(PalettedContainer<T> other, T @org.jetbrains.annotations.Nullable [] presetValues) { // Paper - Anti-Xray - Add preset values
        this.presetValues = presetValues; // Paper - Anti-Xray - Add preset values
        this.registry = other.registry;
        this.strategy = other.strategy;
        this.data = other.data.copy(this);
    }

    // Paper start - Anti-Xray - Add preset values
    @Deprecated @io.papermc.paper.annotation.DoNotUse
    public PalettedContainer(IdMap<T> registry, T palette, PalettedContainer.Strategy strategy) {
        this(registry, palette, strategy, null);
    }
    public PalettedContainer(IdMap<T> registry, T palette, PalettedContainer.Strategy strategy, T @org.jetbrains.annotations.Nullable [] presetValues) {
        this.presetValues = presetValues;
        // Paper end - Anti-Xray
        this.strategy = strategy;
        this.registry = registry;
        this.data = this.createOrReuseData(null, 0);
        this.data.palette.idFor(palette);
        this.updateData(this.data); // Paper - optimise palette reads
    }

    private PalettedContainer.Data<T> createOrReuseData(@Nullable PalettedContainer.Data<T> data, int id) {
        PalettedContainer.Configuration<T> configuration = this.strategy.getConfiguration(this.registry, id);
        return data != null && configuration.equals(data.configuration()) ? data : configuration.createData(this.registry, this, this.strategy.size());
    }

    @Override
    public synchronized int onResize(int bits, T objectAdded) { // Paper - synchronize
        PalettedContainer.Data<T> data = this.data;
        // Paper start - Anti-Xray - Add preset values
        if (this.presetValues != null && objectAdded != null && data.configuration().factory() == PalettedContainer.Strategy.SINGLE_VALUE_PALETTE_FACTORY) {
            int duplicates = 0;
            List<T> presetValues = Arrays.asList(this.presetValues);
            duplicates += presetValues.contains(objectAdded) ? 1 : 0;
            duplicates += presetValues.contains(data.palette.valueFor(0)) ? 1 : 0;
            bits = Mth.ceillog2((1 << this.strategy.calculateBitsForSerialization(this.registry, 1 << bits)) + presetValues.size() - duplicates);
        }
        // Paper end - Anti-Xray
        PalettedContainer.Data<T> data1 = this.createOrReuseData(data, bits);
        data1.copyFrom(data.palette, data.storage);
        this.data = data1;
        // Paper start - Anti-Xray
        this.addPresetValues();
        this.updateData(this.data); // Paper - optimise palette reads
        return objectAdded == null ? -1 : data1.palette.idFor(objectAdded);
    }
    private void addPresetValues() {
        if (this.presetValues != null && this.data.configuration().factory() != PalettedContainer.Strategy.GLOBAL_PALETTE_FACTORY) {
            for (T presetValue : this.presetValues) {
                this.data.palette.idFor(presetValue);
            }
        }
    }
    // Paper end - Anti-Xray

    public synchronized T getAndSet(int x, int y, int z, T state) { // Paper - synchronize
        this.acquire();

        Object var5;
        try {
            var5 = this.getAndSet(this.strategy.getIndex(x, y, z), state);
        } finally {
            this.release();
        }

        return (T)var5;
    }

    public T getAndSetUnchecked(int x, int y, int z, T state) {
        return this.getAndSet(this.strategy.getIndex(x, y, z), state);
    }

    private T getAndSet(int index, T state) {
        // Paper start - optimise palette reads
        final int paletteIdx = this.data.palette.idFor(state);
        final PalettedContainer.Data<T> data = this.data;
        final int prev = data.storage.getAndSet(index, paletteIdx);
        return this.readPalette(data, prev);
        // Paper end - optimise palette reads
    }

    public synchronized void set(int x, int y, int z, T state) { // Paper - synchronize
        this.acquire();

        try {
            this.set(this.strategy.getIndex(x, y, z), state);
        } finally {
            this.release();
        }
    }

    private void set(int index, T state) {
        int i = this.data.palette.idFor(state);
        this.data.storage.set(index, i);
    }

    @Override
    public T get(int x, int y, int z) {
        return this.get(this.strategy.getIndex(x, y, z));
    }

    public T get(int index) { // Paper - public
        // Paper start - optimise palette reads
        final PalettedContainer.Data<T> data = this.data;
        return this.readPalette(data, data.storage.get(index));
        // Paper end - optimise palette reads
    }

    @Override
    public void getAll(Consumer<T> consumer) {
        Palette<T> palette = this.data.palette();
        IntSet set = new IntArraySet();
        this.data.storage.getAll(set::add);
        set.forEach(id -> consumer.accept(palette.valueFor(id)));
    }

    public synchronized void read(FriendlyByteBuf buffer) { // Paper - synchronize
        this.acquire();

        try {
            int _byte = buffer.readByte();
            PalettedContainer.Data<T> data = this.createOrReuseData(this.data, _byte);
            data.palette.read(buffer);
            buffer.readLongArray(data.storage.getRaw());
            this.data = data;
            this.addPresetValues(); // Paper - Anti-Xray - Add preset values (inefficient, but this isn't used by the server)
            this.updateData(this.data); // Paper - optimise palette reads
        } finally {
            this.release();
        }
    }

    // Paper start - Anti-Xray; Add chunk packet info
    @Override
    @Deprecated @io.papermc.paper.annotation.DoNotUse
    public void write(FriendlyByteBuf buffer) {
        this.write(buffer, null, 0);
    }
    @Override
    public synchronized void write(FriendlyByteBuf buffer, @Nullable io.papermc.paper.antixray.ChunkPacketInfo<T> chunkPacketInfo, int chunkSectionIndex) { // Paper - Synchronize
        this.acquire();

        try {
            this.data.write(buffer, chunkPacketInfo, chunkSectionIndex);
            if (chunkPacketInfo != null) {
                chunkPacketInfo.setPresetValues(chunkSectionIndex, this.presetValues);
            }
            // Paper end - Anti-Xray
        } finally {
            this.release();
        }
    }

    private static <T> DataResult<PalettedContainer<T>> unpack(
        IdMap<T> registry, PalettedContainer.Strategy strategy, PalettedContainerRO.PackedData<T> packedData, T defaultValue, T @org.jetbrains.annotations.Nullable [] presetValues // Paper - Anti-Xray - Add preset values
    ) {
        List<T> list = packedData.paletteEntries();
        int size = strategy.size();
        int i = strategy.calculateBitsForSerialization(registry, list.size());
        PalettedContainer.Configuration<T> configuration = strategy.getConfiguration(registry, i);
        BitStorage bitStorage;
        if (i == 0) {
            bitStorage = new ZeroBitStorage(size);
        } else {
            Optional<LongStream> optional = packedData.storage();
            if (optional.isEmpty()) {
                return DataResult.error(() -> "Missing values for non-zero storage");
            }

            long[] longs = optional.get().toArray();

            try {
                if (configuration.factory() == PalettedContainer.Strategy.GLOBAL_PALETTE_FACTORY) {
                    Palette<T> palette = new HashMapPalette<>(registry, i, (bits, objectAdded) -> 0, list);
                    SimpleBitStorage simpleBitStorage = new SimpleBitStorage(i, size, longs);
                    int[] ints = new int[size];
                    simpleBitStorage.unpack(ints);
                    swapPalette(ints, id -> registry.getId(palette.valueFor(id)));
                    bitStorage = new SimpleBitStorage(configuration.bits(), size, ints);
                } else {
                    bitStorage = new SimpleBitStorage(configuration.bits(), size, longs);
                }
            } catch (SimpleBitStorage.InitializationException var13) {
                return DataResult.error(() -> "Failed to read PalettedContainer: " + var13.getMessage());
            }
        }

        return DataResult.success(new PalettedContainer<>(registry, strategy, configuration, bitStorage, list, defaultValue, presetValues)); // Paper - Anti-Xray - Add preset values
    }

    @Override
    public synchronized PalettedContainerRO.PackedData<T> pack(IdMap<T> registry, PalettedContainer.Strategy strategy) { // Paper - synchronize
        this.acquire();

        PalettedContainerRO.PackedData var12;
        try {
            HashMapPalette<T> hashMapPalette = new HashMapPalette<>(registry, this.data.storage.getBits(), this.dummyPaletteResize);
            int size = strategy.size();
            int[] ints = new int[size];
            this.data.storage.unpack(ints);
            swapPalette(ints, id -> hashMapPalette.idFor(this.data.palette.valueFor(id)));
            int i = strategy.calculateBitsForSerialization(registry, hashMapPalette.getSize());
            Optional<LongStream> optional;
            if (i != 0) {
                SimpleBitStorage simpleBitStorage = new SimpleBitStorage(i, size, ints);
                optional = Optional.of(Arrays.stream(simpleBitStorage.getRaw()));
            } else {
                optional = Optional.empty();
            }

            var12 = new PalettedContainerRO.PackedData<>(hashMapPalette.getEntries(), optional);
        } finally {
            this.release();
        }

        return var12;
    }

    private static <T> void swapPalette(int[] bits, IntUnaryOperator operator) {
        int i = -1;
        int i1 = -1;

        for (int i2 = 0; i2 < bits.length; i2++) {
            int i3 = bits[i2];
            if (i3 != i) {
                i = i3;
                i1 = operator.applyAsInt(i3);
            }

            bits[i2] = i1;
        }
    }

    @Override
    public int getSerializedSize() {
        return this.data.getSerializedSize();
    }

    @Override
    public boolean maybeHas(Predicate<T> predicate) {
        return this.data.palette.maybeHas(predicate);
    }

    @Override
    public PalettedContainer<T> copy() {
        return new PalettedContainer<>(this, this.presetValues); // Paper - Anti-Xray - Add preset values
    }

    @Override
    public PalettedContainer<T> recreate() {
        return new PalettedContainer<>(this.registry, this.data.palette.valueFor(0), this.strategy, this.presetValues); // Paper - Anti-Xray - Add preset values
    }

    @Override
    public void count(PalettedContainer.CountConsumer<T> countConsumer) {
        if (this.data.palette.getSize() == 1) {
            countConsumer.accept(this.data.palette.valueFor(0), this.data.storage.getSize());
        } else {
            Int2IntOpenHashMap map = new Int2IntOpenHashMap();
            this.data.storage.getAll(id -> map.addTo(id, 1));
            map.int2IntEntrySet().forEach(idEntry -> countConsumer.accept(this.data.palette.valueFor(idEntry.getIntKey()), idEntry.getIntValue()));
        }
    }

    record Configuration<T>(Palette.Factory factory, int bits) {
        public PalettedContainer.Data<T> createData(IdMap<T> registry, PaletteResize<T> paletteResize, int size) {
            BitStorage bitStorage = (BitStorage)(this.bits == 0 ? new ZeroBitStorage(size) : new SimpleBitStorage(this.bits, size));
            Palette<T> palette = this.factory.create(this.bits, registry, paletteResize, List.of());
            return new PalettedContainer.Data<>(this, bitStorage, palette);
        }
    }

    @FunctionalInterface
    public interface CountConsumer<T> {
        void accept(T state, int count);
    }

    // Paper start - optimise palette reads
    public static final class Data<T> implements ca.spottedleaf.moonrise.patches.fast_palette.FastPaletteData<T> {

        private final PalettedContainer.Configuration<T> configuration;
        private final BitStorage storage;
        private final Palette<T> palette;

        private T[] moonrise$palette;

        public Data(final PalettedContainer.Configuration<T> configuration, final BitStorage storage, final Palette<T> palette) {
            this.configuration = configuration;
            this.storage = storage;
            this.palette = palette;
        }

        public PalettedContainer.Configuration<T> configuration() {
            return this.configuration;
        }

        public BitStorage storage() {
            return this.storage;
        }

        public Palette<T> palette() {
            return this.palette;
        }

        @Override
        public final T[] moonrise$getPalette() {
            return this.moonrise$palette;
        }

        @Override
        public final void moonrise$setPalette(final T[] palette) {
            this.moonrise$palette = palette;
        }
        // Paper end - optimise palette reads

        public void copyFrom(Palette<T> palette, BitStorage bitStorage) {
            for (int i = 0; i < bitStorage.getSize(); i++) {
                T object = palette.valueFor(bitStorage.get(i));
                this.storage.set(i, this.palette.idFor(object));
            }
        }

        public int getSerializedSize() {
            return 1 + this.palette.getSerializedSize() + VarInt.getByteSize(this.storage.getRaw().length) + this.storage.getRaw().length * 8;
        }

        // Paper start - Anti-Xray - Add chunk packet info
        public void write(FriendlyByteBuf buffer, @Nullable io.papermc.paper.antixray.ChunkPacketInfo<T> chunkPacketInfo, int chunkSectionIndex) {
            buffer.writeByte(this.storage.getBits());
            this.palette.write(buffer);
            if (chunkPacketInfo != null) {
                chunkPacketInfo.setBits(chunkSectionIndex, this.configuration.bits());
                chunkPacketInfo.setPalette(chunkSectionIndex, this.palette);
                chunkPacketInfo.setIndex(chunkSectionIndex, buffer.writerIndex() + VarInt.getByteSize(this.storage.getRaw().length));
            }
            // Paper end
            buffer.writeLongArray(this.storage.getRaw());
        }

        public PalettedContainer.Data<T> copy(PaletteResize<T> resizeHandler) {
            return new PalettedContainer.Data<>(this.configuration, this.storage.copy(), this.palette.copy(resizeHandler));
        }
    }

    public abstract static class Strategy {
        public static final Palette.Factory SINGLE_VALUE_PALETTE_FACTORY = SingleValuePalette::create;
        public static final Palette.Factory LINEAR_PALETTE_FACTORY = LinearPalette::create;
        public static final Palette.Factory HASHMAP_PALETTE_FACTORY = HashMapPalette::create;
        static final Palette.Factory GLOBAL_PALETTE_FACTORY = GlobalPalette::create;
        public static final PalettedContainer.Strategy SECTION_STATES = new PalettedContainer.Strategy(4) {
            @Override
            public <A> PalettedContainer.Configuration<A> getConfiguration(IdMap<A> registry, int size) {
                return switch (size) {
                    case 0 -> new PalettedContainer.Configuration(SINGLE_VALUE_PALETTE_FACTORY, size);
                    case 1, 2, 3, 4 -> new PalettedContainer.Configuration(LINEAR_PALETTE_FACTORY, 4);
                    case 5, 6, 7, 8 -> new PalettedContainer.Configuration(HASHMAP_PALETTE_FACTORY, size);
                    default -> new PalettedContainer.Configuration(PalettedContainer.Strategy.GLOBAL_PALETTE_FACTORY, Mth.ceillog2(registry.size()));
                };
            }
        };
        public static final PalettedContainer.Strategy SECTION_BIOMES = new PalettedContainer.Strategy(2) {
            @Override
            public <A> PalettedContainer.Configuration<A> getConfiguration(IdMap<A> registry, int size) {
                return switch (size) {
                    case 0 -> new PalettedContainer.Configuration(SINGLE_VALUE_PALETTE_FACTORY, size);
                    case 1, 2, 3 -> new PalettedContainer.Configuration(LINEAR_PALETTE_FACTORY, size);
                    default -> new PalettedContainer.Configuration(PalettedContainer.Strategy.GLOBAL_PALETTE_FACTORY, Mth.ceillog2(registry.size()));
                };
            }
        };
        private final int sizeBits;

        Strategy(int sizeBits) {
            this.sizeBits = sizeBits;
        }

        public int size() {
            return 1 << this.sizeBits * 3;
        }

        public int getIndex(int x, int y, int z) {
            return (y << this.sizeBits | z) << this.sizeBits | x;
        }

        public abstract <A> PalettedContainer.Configuration<A> getConfiguration(IdMap<A> registry, int size);

        <A> int calculateBitsForSerialization(IdMap<A> registry, int size) {
            int i = Mth.ceillog2(size);
            PalettedContainer.Configuration<A> configuration = this.getConfiguration(registry, i);
            return configuration.factory() == GLOBAL_PALETTE_FACTORY ? i : configuration.bits();
        }
    }
}
