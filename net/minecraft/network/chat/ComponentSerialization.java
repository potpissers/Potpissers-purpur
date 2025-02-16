package net.minecraft.network.chat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.MapEncoder;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.ScoreContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.StringRepresentable;

public class ComponentSerialization {
    public static final Codec<Component> CODEC = Codec.recursive("Component", ComponentSerialization::createCodec);
    public static final StreamCodec<RegistryFriendlyByteBuf, Component> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> OPTIONAL_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs::optional);
    public static final StreamCodec<RegistryFriendlyByteBuf, Component> TRUSTED_STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistriesTrusted(CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> TRUSTED_OPTIONAL_STREAM_CODEC = TRUSTED_STREAM_CODEC.apply(
        ByteBufCodecs::optional
    );
    public static final StreamCodec<ByteBuf, Component> TRUSTED_CONTEXT_FREE_STREAM_CODEC = ByteBufCodecs.fromCodecTrusted(CODEC);
    public static final Codec<Component> FLAT_CODEC = flatCodec(Integer.MAX_VALUE);

    public static Codec<Component> flatCodec(int maxSize) {
        final Codec<String> codec = Codec.string(0, maxSize);
        return new Codec<Component>() {
            @Override
            public <T> DataResult<Pair<Component, T>> decode(DynamicOps<T> ops, T input) {
                DynamicOps<JsonElement> jsonOps = asJsonOps(ops);
                return codec.decode(ops, input).flatMap(pair -> {
                    try {
                        JsonElement jsonElement = JsonParser.parseString(pair.getFirst());
                        return ComponentSerialization.CODEC.parse(jsonOps, jsonElement).map(component -> Pair.of(component, (T)pair.getSecond()));
                    } catch (JsonParseException var3x) {
                        return DataResult.error(var3x::getMessage);
                    }
                });
            }

            @Override
            public <T> DataResult<T> encode(Component input, DynamicOps<T> ops, T value) {
                DynamicOps<JsonElement> jsonOps = asJsonOps(ops);
                return ComponentSerialization.CODEC.encodeStart(jsonOps, input).flatMap(jsonElement -> {
                    try {
                        return codec.encodeStart(ops, GsonHelper.toStableString(jsonElement));
                    } catch (IllegalArgumentException var4x) {
                        return DataResult.error(var4x::getMessage);
                    }
                });
            }

            private static <T> DynamicOps<JsonElement> asJsonOps(DynamicOps<T> ops) {
                return (DynamicOps<JsonElement>)(ops instanceof RegistryOps<T> registryOps ? registryOps.withParent(JsonOps.INSTANCE) : JsonOps.INSTANCE);
            }
        };
    }

    private static MutableComponent createFromList(List<Component> components) {
        MutableComponent mutableComponent = components.get(0).copy();

        for (int i = 1; i < components.size(); i++) {
            mutableComponent.append(components.get(i));
        }

        return mutableComponent;
    }

    public static <T extends StringRepresentable, E> MapCodec<E> createLegacyComponentMatcher(
        T[] types, Function<T, MapCodec<? extends E>> codecGetter, Function<E, T> typeGetter, String typeFieldName
    ) {
        MapCodec<E> mapCodec = new ComponentSerialization.FuzzyCodec<>(
            Stream.<T>of(types).map(codecGetter).toList(), object -> codecGetter.apply(typeGetter.apply(object))
        );
        Codec<T> codec = StringRepresentable.fromValues((Supplier<T[]>)(() -> types));
        MapCodec<E> mapCodec1 = codec.dispatchMap(typeFieldName, typeGetter, codecGetter);
        MapCodec<E> mapCodec2 = new ComponentSerialization.StrictEither<>(typeFieldName, mapCodec1, mapCodec);
        return ExtraCodecs.orCompressed(mapCodec2, mapCodec1);
    }

    private static Codec<Component> createCodec(Codec<Component> codec) {
        ComponentContents.Type<?>[] types = new ComponentContents.Type[]{
            PlainTextContents.TYPE, TranslatableContents.TYPE, KeybindContents.TYPE, ScoreContents.TYPE, SelectorContents.TYPE, NbtContents.TYPE
        };
        MapCodec<ComponentContents> mapCodec = createLegacyComponentMatcher(types, ComponentContents.Type::codec, ComponentContents::type, "type");
        Codec<Component> codec1 = RecordCodecBuilder.create(
            instance -> instance.group(
                    mapCodec.forGetter(Component::getContents),
                    ExtraCodecs.nonEmptyList(codec.listOf()).optionalFieldOf("extra", List.of()).forGetter(Component::getSiblings),
                    Style.Serializer.MAP_CODEC.forGetter(Component::getStyle)
                )
                .apply(instance, MutableComponent::new)
        );
        return Codec.either(Codec.either(Codec.STRING, ExtraCodecs.nonEmptyList(codec.listOf())), codec1)
            .xmap(
                either -> either.map(either1 -> either1.map(Component::literal, ComponentSerialization::createFromList), component -> (Component)component),
                component -> {
                    String string = component.tryCollapseToString();
                    return string != null ? Either.left(Either.left(string)) : Either.right(component);
                }
            );
    }

    static class FuzzyCodec<T> extends MapCodec<T> {
        private final List<MapCodec<? extends T>> codecs;
        private final Function<T, MapEncoder<? extends T>> encoderGetter;

        public FuzzyCodec(List<MapCodec<? extends T>> codecs, Function<T, MapEncoder<? extends T>> encoderGetter) {
            this.codecs = codecs;
            this.encoderGetter = encoderGetter;
        }

        @Override
        public <S> DataResult<T> decode(DynamicOps<S> ops, MapLike<S> input) {
            for (MapDecoder<? extends T> mapDecoder : this.codecs) {
                DataResult<? extends T> dataResult = mapDecoder.decode(ops, input);
                if (dataResult.result().isPresent()) {
                    return (DataResult<T>)dataResult;
                }
            }

            return DataResult.error(() -> "No matching codec found");
        }

        @Override
        public <S> RecordBuilder<S> encode(T input, DynamicOps<S> ops, RecordBuilder<S> prefix) {
            MapEncoder<T> mapEncoder = (MapEncoder<T>)this.encoderGetter.apply(input);
            return mapEncoder.encode(input, ops, prefix);
        }

        @Override
        public <S> Stream<S> keys(DynamicOps<S> ops) {
            return this.codecs.stream().flatMap(mapCodec -> mapCodec.keys(ops)).distinct();
        }

        @Override
        public String toString() {
            return "FuzzyCodec[" + this.codecs + "]";
        }
    }

    static class StrictEither<T> extends MapCodec<T> {
        private final String typeFieldName;
        private final MapCodec<T> typed;
        private final MapCodec<T> fuzzy;

        public StrictEither(String typeFieldName, MapCodec<T> typed, MapCodec<T> fuzzy) {
            this.typeFieldName = typeFieldName;
            this.typed = typed;
            this.fuzzy = fuzzy;
        }

        @Override
        public <O> DataResult<T> decode(DynamicOps<O> ops, MapLike<O> input) {
            return input.get(this.typeFieldName) != null ? this.typed.decode(ops, input) : this.fuzzy.decode(ops, input);
        }

        @Override
        public <O> RecordBuilder<O> encode(T input, DynamicOps<O> ops, RecordBuilder<O> prefix) {
            return this.fuzzy.encode(input, ops, prefix);
        }

        @Override
        public <T1> Stream<T1> keys(DynamicOps<T1> ops) {
            return Stream.concat(this.typed.keys(ops), this.fuzzy.keys(ops)).distinct();
        }
    }
}
