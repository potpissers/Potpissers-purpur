package net.minecraft.world.item.component;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.MapEncoder;
import com.mojang.serialization.MapLike;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

public final class CustomData {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final CustomData EMPTY = new CustomData(new CompoundTag());
    private static final String TYPE_TAG = "id";
    // Paper start - Item serialization as json
    public static ThreadLocal<Boolean> SERIALIZE_CUSTOM_AS_SNBT = ThreadLocal.withInitial(() -> false);
    public static final Codec<CustomData> CODEC = Codec.either(CompoundTag.CODEC, TagParser.AS_CODEC)
        .xmap(com.mojang.datafixers.util.Either::unwrap, data -> { // Both will be used for deserialization, but we decide which one to use for serialization
            if (!SERIALIZE_CUSTOM_AS_SNBT.get()) {
                return com.mojang.datafixers.util.Either.left(data); // First codec
            } else {
                return com.mojang.datafixers.util.Either.right(data); // Second codec
            }
        })
        // Paper end - Item serialization as json
        .xmap(CustomData::new, customData -> customData.tag);
    public static final Codec<CustomData> CODEC_WITH_ID = CODEC.validate(
        data -> data.getUnsafe().contains("id", 8) ? DataResult.success(data) : DataResult.error(() -> "Missing id for entity in: " + data)
    );
    @Deprecated
    public static final StreamCodec<ByteBuf, CustomData> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(CustomData::new, data -> data.tag);
    private final CompoundTag tag;

    private CustomData(CompoundTag tag) {
        this.tag = tag;
    }

    public static CustomData of(CompoundTag tag) {
        return new CustomData(tag.copy());
    }

    public static Predicate<ItemStack> itemMatcher(DataComponentType<CustomData> componentType, CompoundTag tag) {
        return stack -> {
            CustomData customData = stack.getOrDefault(componentType, EMPTY);
            return customData.matchedBy(tag);
        };
    }

    public boolean matchedBy(CompoundTag tag) {
        return NbtUtils.compareNbt(tag, this.tag, true);
    }

    public static void update(DataComponentType<CustomData> componentType, ItemStack stack, Consumer<CompoundTag> updater) {
        CustomData customData = stack.getOrDefault(componentType, EMPTY).update(updater);
        if (customData.tag.isEmpty()) {
            stack.remove(componentType);
        } else {
            stack.set(componentType, customData);
        }
    }

    public static void set(DataComponentType<CustomData> componentType, ItemStack stack, CompoundTag tag) {
        if (!tag.isEmpty()) {
            stack.set(componentType, of(tag));
        } else {
            stack.remove(componentType);
        }
    }

    public CustomData update(Consumer<CompoundTag> updater) {
        CompoundTag compoundTag = this.tag.copy();
        updater.accept(compoundTag);
        return new CustomData(compoundTag);
    }

    @Nullable
    public ResourceLocation parseEntityId() {
        return !this.tag.contains("id", 8) ? null : ResourceLocation.tryParse(this.tag.getString("id"));
    }

    @Nullable
    public <T> T parseEntityType(HolderLookup.Provider registries, ResourceKey<? extends Registry<T>> registryKey) {
        ResourceLocation resourceLocation = this.parseEntityId();
        return resourceLocation == null
            ? null
            : registries.lookup(registryKey)
                .flatMap(registryLookup -> registryLookup.get(ResourceKey.create(registryKey, resourceLocation)))
                .map(Holder::value)
                .orElse(null);
    }

    public void loadInto(Entity entity) {
        CompoundTag compoundTag = entity.saveWithoutId(new CompoundTag());
        UUID uuid = entity.getUUID();
        compoundTag.merge(this.tag);
        entity.load(compoundTag);
        entity.setUUID(uuid);
    }

    public boolean loadInto(BlockEntity blockEntity, HolderLookup.Provider levelRegistry) {
        CompoundTag compoundTag = blockEntity.saveCustomOnly(levelRegistry);
        CompoundTag compoundTag1 = compoundTag.copy();
        compoundTag.merge(this.tag);
        if (!compoundTag.equals(compoundTag1)) {
            try {
                blockEntity.loadCustomOnly(compoundTag, levelRegistry);
                blockEntity.setChanged();
                return true;
            } catch (Exception var8) {
                LOGGER.warn("Failed to apply custom data to block entity at {}", blockEntity.getBlockPos(), var8);

                try {
                    blockEntity.loadCustomOnly(compoundTag1, levelRegistry);
                } catch (Exception var7) {
                    LOGGER.warn("Failed to rollback block entity at {} after failure", blockEntity.getBlockPos(), var7);
                }
            }
        }

        return false;
    }

    public <T> DataResult<CustomData> update(DynamicOps<Tag> ops, MapEncoder<T> encoder, T value) {
        return encoder.encode(value, ops, ops.mapBuilder()).build(this.tag).map(tag -> new CustomData((CompoundTag)tag));
    }

    public <T> DataResult<T> read(MapDecoder<T> decoder) {
        return this.read(NbtOps.INSTANCE, decoder);
    }

    public <T> DataResult<T> read(DynamicOps<Tag> ops, MapDecoder<T> decoder) {
        MapLike<Tag> mapLike = ops.getMap(this.tag).getOrThrow();
        return decoder.decode(ops, mapLike);
    }

    public int size() {
        return this.tag.size();
    }

    public boolean isEmpty() {
        return this.tag.isEmpty();
    }

    public CompoundTag copyTag() {
        return this.tag.copy();
    }

    public boolean contains(String key) {
        return this.tag.contains(key);
    }

    @Override
    public boolean equals(Object other) {
        return other == this || other instanceof CustomData customData && this.tag.equals(customData.tag);
    }

    @Override
    public int hashCode() {
        return this.tag.hashCode();
    }

    @Override
    public String toString() {
        return this.tag.toString();
    }

    @Deprecated
    public CompoundTag getUnsafe() {
        return this.tag;
    }
}
