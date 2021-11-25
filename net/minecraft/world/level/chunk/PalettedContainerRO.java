package net.minecraft.world.level.chunk;

import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.LongStream;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;

public interface PalettedContainerRO<T> {
    T get(int x, int y, int z);

    void getAll(Consumer<T> consumer);

    // Paper start - Anti-Xray - Add chunk packet info
    @Deprecated @io.papermc.paper.annotation.DoNotUse void write(FriendlyByteBuf buffer);
    void write(FriendlyByteBuf buffer, @javax.annotation.Nullable io.papermc.paper.antixray.ChunkPacketInfo<T> chunkPacketInfo, int chunkSectionIndex);
    // Paper end

    int getSerializedSize();

    boolean maybeHas(Predicate<T> filter);

    void count(PalettedContainer.CountConsumer<T> countConsumer);

    PalettedContainer<T> copy();

    PalettedContainer<T> recreate();

    PalettedContainerRO.PackedData<T> pack(IdMap<T> registry, PalettedContainer.Strategy strategy);

    public record PackedData<T>(List<T> paletteEntries, Optional<LongStream> storage) {
    }

    public interface Unpacker<T, C extends PalettedContainerRO<T>> {
        DataResult<C> read(IdMap<T> registry, PalettedContainer.Strategy strategy, PalettedContainerRO.PackedData<T> packedData);
    }
}
