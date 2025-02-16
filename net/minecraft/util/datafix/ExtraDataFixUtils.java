package net.minecraft.util.datafix;

import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.RewriteResult;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.View;
import com.mojang.datafixers.functions.PointFreeRule;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.BitSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

public class ExtraDataFixUtils {
    public static Dynamic<?> fixBlockPos(Dynamic<?> data) {
        Optional<Number> optional = data.get("X").asNumber().result();
        Optional<Number> optional1 = data.get("Y").asNumber().result();
        Optional<Number> optional2 = data.get("Z").asNumber().result();
        return !optional.isEmpty() && !optional1.isEmpty() && !optional2.isEmpty()
            ? data.createIntList(IntStream.of(optional.get().intValue(), optional1.get().intValue(), optional2.get().intValue()))
            : data;
    }

    public static <T, R> Typed<R> cast(Type<R> type, Typed<T> data) {
        return new Typed<>(type, data.getOps(), (R)data.getValue());
    }

    public static Type<?> patchSubType(Type<?> type, Type<?> oldSubType, Type<?> newSubType) {
        return type.all(typePatcher(oldSubType, newSubType), true, false).view().newType();
    }

    private static <A, B> TypeRewriteRule typePatcher(Type<A> oldType, Type<B> newType) {
        RewriteResult<A, B> rewriteResult = RewriteResult.create(View.create("Patcher", oldType, newType, dynamicOps -> object -> {
            throw new UnsupportedOperationException();
        }), new BitSet());
        return TypeRewriteRule.everywhere(TypeRewriteRule.ifSame(oldType, rewriteResult), PointFreeRule.nop(), true, true);
    }

    @SafeVarargs
    public static <T> Function<Typed<?>, Typed<?>> chainAllFilters(Function<Typed<?>, Typed<?>>... filters) {
        return typed -> {
            for (Function<Typed<?>, Typed<?>> function : filters) {
                typed = function.apply(typed);
            }

            return typed;
        };
    }

    public static Dynamic<?> blockState(String blockId, Map<String, String> properties) {
        Dynamic<Tag> dynamic = new Dynamic<>(NbtOps.INSTANCE, new CompoundTag());
        Dynamic<Tag> dynamic1 = dynamic.set("Name", dynamic.createString(blockId));
        if (!properties.isEmpty()) {
            dynamic1 = dynamic1.set(
                "Properties",
                dynamic.createMap(
                    properties.entrySet()
                        .stream()
                        .collect(Collectors.toMap(entry -> dynamic.createString(entry.getKey()), entry -> dynamic.createString(entry.getValue())))
                )
            );
        }

        return dynamic1;
    }

    public static Dynamic<?> blockState(String blockId) {
        return blockState(blockId, Map.of());
    }

    public static Dynamic<?> fixStringField(Dynamic<?> data, String fieldName, UnaryOperator<String> fixer) {
        return data.update(fieldName, dynamic -> DataFixUtils.orElse(dynamic.asString().map(fixer).map(data::createString).result(), dynamic));
    }
}
