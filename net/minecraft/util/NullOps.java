package net.minecraft.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.RecordBuilder.AbstractUniversalBuilder;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class NullOps implements DynamicOps<Unit> {
    public static final NullOps INSTANCE = new NullOps();

    private NullOps() {
    }

    @Override
    public <U> U convertTo(DynamicOps<U> ops, Unit unit) {
        return ops.empty();
    }

    @Override
    public Unit empty() {
        return Unit.INSTANCE;
    }

    @Override
    public Unit emptyMap() {
        return Unit.INSTANCE;
    }

    @Override
    public Unit emptyList() {
        return Unit.INSTANCE;
    }

    @Override
    public Unit createNumeric(Number value) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit createByte(byte value) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit createShort(short value) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit createInt(int value) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit createLong(long value) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit createFloat(float value) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit createDouble(double value) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit createBoolean(boolean value) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit createString(String value) {
        return Unit.INSTANCE;
    }

    @Override
    public DataResult<Number> getNumberValue(Unit input) {
        return DataResult.error(() -> "Not a number");
    }

    @Override
    public DataResult<Boolean> getBooleanValue(Unit input) {
        return DataResult.error(() -> "Not a boolean");
    }

    @Override
    public DataResult<String> getStringValue(Unit input) {
        return DataResult.error(() -> "Not a string");
    }

    @Override
    public DataResult<Unit> mergeToList(Unit list, Unit value) {
        return DataResult.success(Unit.INSTANCE);
    }

    @Override
    public DataResult<Unit> mergeToList(Unit list, List<Unit> values) {
        return DataResult.success(Unit.INSTANCE);
    }

    @Override
    public DataResult<Unit> mergeToMap(Unit map, Unit key, Unit value) {
        return DataResult.success(Unit.INSTANCE);
    }

    @Override
    public DataResult<Unit> mergeToMap(Unit map, Map<Unit, Unit> values) {
        return DataResult.success(Unit.INSTANCE);
    }

    @Override
    public DataResult<Unit> mergeToMap(Unit map, MapLike<Unit> values) {
        return DataResult.success(Unit.INSTANCE);
    }

    @Override
    public DataResult<Stream<Pair<Unit, Unit>>> getMapValues(Unit input) {
        return DataResult.error(() -> "Not a map");
    }

    @Override
    public DataResult<Consumer<BiConsumer<Unit, Unit>>> getMapEntries(Unit input) {
        return DataResult.error(() -> "Not a map");
    }

    @Override
    public DataResult<MapLike<Unit>> getMap(Unit input) {
        return DataResult.error(() -> "Not a map");
    }

    @Override
    public DataResult<Stream<Unit>> getStream(Unit input) {
        return DataResult.error(() -> "Not a list");
    }

    @Override
    public DataResult<Consumer<Consumer<Unit>>> getList(Unit input) {
        return DataResult.error(() -> "Not a list");
    }

    @Override
    public DataResult<ByteBuffer> getByteBuffer(Unit input) {
        return DataResult.error(() -> "Not a byte list");
    }

    @Override
    public DataResult<IntStream> getIntStream(Unit input) {
        return DataResult.error(() -> "Not an int list");
    }

    @Override
    public DataResult<LongStream> getLongStream(Unit input) {
        return DataResult.error(() -> "Not a long list");
    }

    @Override
    public Unit createMap(Stream<Pair<Unit, Unit>> map) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit createMap(Map<Unit, Unit> map) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit createList(Stream<Unit> input) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit createByteList(ByteBuffer input) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit createIntList(IntStream input) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit createLongList(LongStream input) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit remove(Unit input, String key) {
        return input;
    }

    @Override
    public RecordBuilder<Unit> mapBuilder() {
        return new NullOps.NullMapBuilder(this);
    }

    @Override
    public String toString() {
        return "Null";
    }

    static final class NullMapBuilder extends AbstractUniversalBuilder<Unit, Unit> {
        public NullMapBuilder(DynamicOps<Unit> ops) {
            super(ops);
        }

        @Override
        protected Unit initBuilder() {
            return Unit.INSTANCE;
        }

        @Override
        protected Unit append(Unit unit, Unit unit1, Unit unit2) {
            return unit2;
        }

        @Override
        protected DataResult<Unit> build(Unit unit, Unit unit1) {
            return DataResult.success(unit1);
        }
    }
}
