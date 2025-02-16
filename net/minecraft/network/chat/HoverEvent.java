package net.minecraft.network.chat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class HoverEvent {
    public static final Codec<HoverEvent> CODEC = Codec.withAlternative(
            HoverEvent.TypedHoverEvent.CODEC.codec(), HoverEvent.TypedHoverEvent.LEGACY_CODEC.codec()
        )
        .xmap(HoverEvent::new, hoverEvent -> hoverEvent.event);
    private final HoverEvent.TypedHoverEvent<?> event;

    public <T> HoverEvent(HoverEvent.Action<T> action, T value) {
        this(new HoverEvent.TypedHoverEvent<>(action, value));
    }

    private HoverEvent(HoverEvent.TypedHoverEvent<?> event) {
        this.event = event;
    }

    public HoverEvent.Action<?> getAction() {
        return this.event.action;
    }

    @Nullable
    public <T> T getValue(HoverEvent.Action<T> actionType) {
        return this.event.action == actionType ? actionType.cast(this.event.value) : null;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other != null && this.getClass() == other.getClass() && ((HoverEvent)other).event.equals(this.event);
    }

    @Override
    public String toString() {
        return this.event.toString();
    }

    @Override
    public int hashCode() {
        return this.event.hashCode();
    }

    public static class Action<T> implements StringRepresentable {
        public static final HoverEvent.Action<Component> SHOW_TEXT = new HoverEvent.Action<>(
            "show_text", true, ComponentSerialization.CODEC, (name, ops) -> DataResult.success(name)
        );
        public static final HoverEvent.Action<HoverEvent.ItemStackInfo> SHOW_ITEM = new HoverEvent.Action<>(
            "show_item", true, HoverEvent.ItemStackInfo.CODEC, HoverEvent.ItemStackInfo::legacyCreate
        );
        public static final HoverEvent.Action<HoverEvent.EntityTooltipInfo> SHOW_ENTITY = new HoverEvent.Action<>(
            "show_entity", true, HoverEvent.EntityTooltipInfo.CODEC, HoverEvent.EntityTooltipInfo::legacyCreate
        );
        public static final Codec<HoverEvent.Action<?>> UNSAFE_CODEC = StringRepresentable.fromValues(
            () -> new HoverEvent.Action[]{SHOW_TEXT, SHOW_ITEM, SHOW_ENTITY}
        );
        public static final Codec<HoverEvent.Action<?>> CODEC = UNSAFE_CODEC.validate(HoverEvent.Action::filterForSerialization);
        private final String name;
        private final boolean allowFromServer;
        final MapCodec<HoverEvent.TypedHoverEvent<T>> codec;
        final MapCodec<HoverEvent.TypedHoverEvent<T>> legacyCodec;

        public Action(String name, boolean allowFromServer, Codec<T> codec, final HoverEvent.LegacyConverter<T> legacyConverter) {
            this.name = name;
            this.allowFromServer = allowFromServer;
            this.codec = codec.xmap(object -> new HoverEvent.TypedHoverEvent<>(this, (T)object), typedHoverEvent -> typedHoverEvent.value).fieldOf("contents");
            this.legacyCodec = (new Codec<HoverEvent.TypedHoverEvent<T>>() {
                @Override
                public <D> DataResult<Pair<HoverEvent.TypedHoverEvent<T>, D>> decode(DynamicOps<D> dynamicOps, D object) {
                    return ComponentSerialization.CODEC.decode(dynamicOps, object).flatMap(pair -> {
                        DataResult<T> dataResult;
                        if (dynamicOps instanceof RegistryOps<D> registryOps) {
                            dataResult = legacyConverter.parse(pair.getFirst(), registryOps);
                        } else {
                            dataResult = legacyConverter.parse(pair.getFirst(), null);
                        }

                        return dataResult.map(object1 -> Pair.of(new HoverEvent.TypedHoverEvent<>(Action.this, (T)object1), (D)pair.getSecond()));
                    });
                }

                @Override
                public <D> DataResult<D> encode(HoverEvent.TypedHoverEvent<T> typedHoverEvent, DynamicOps<D> dynamicOps, D object) {
                    return DataResult.error(() -> "Can't encode in legacy format");
                }
            }).fieldOf("value");
        }

        public boolean isAllowedFromServer() {
            return this.allowFromServer;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        T cast(Object parameter) {
            return (T)parameter;
        }

        @Override
        public String toString() {
            return "<action " + this.name + ">";
        }

        private static DataResult<HoverEvent.Action<?>> filterForSerialization(@Nullable HoverEvent.Action<?> action) {
            if (action == null) {
                return DataResult.error(() -> "Unknown action");
            } else {
                return !action.isAllowedFromServer() ? DataResult.error(() -> "Action not allowed: " + action) : DataResult.success(action, Lifecycle.stable());
            }
        }
    }

    public static class EntityTooltipInfo {
        public static final Codec<HoverEvent.EntityTooltipInfo> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("type").forGetter(info -> info.type),
                    UUIDUtil.LENIENT_CODEC.fieldOf("id").forGetter(info -> info.id),
                    ComponentSerialization.CODEC.lenientOptionalFieldOf("name").forGetter(info -> info.name)
                )
                .apply(instance, HoverEvent.EntityTooltipInfo::new)
        );
        public final EntityType<?> type;
        public final UUID id;
        public final Optional<Component> name;
        @Nullable
        private List<Component> linesCache;

        public EntityTooltipInfo(EntityType<?> type, UUID id, @Nullable Component name) {
            this(type, id, Optional.ofNullable(name));
        }

        public EntityTooltipInfo(EntityType<?> type, UUID id, Optional<Component> name) {
            this.type = type;
            this.id = id;
            this.name = name;
        }

        public static DataResult<HoverEvent.EntityTooltipInfo> legacyCreate(Component name, @Nullable RegistryOps<?> ops) {
            try {
                CompoundTag compoundTag = TagParser.parseTag(name.getString());
                DynamicOps<JsonElement> dynamicOps = (DynamicOps<JsonElement>)(ops != null ? ops.withParent(JsonOps.INSTANCE) : JsonOps.INSTANCE);
                DataResult<Component> dataResult = ComponentSerialization.CODEC.parse(dynamicOps, JsonParser.parseString(compoundTag.getString("name")));
                EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(ResourceLocation.parse(compoundTag.getString("type")));
                UUID uuid = UUID.fromString(compoundTag.getString("id"));
                return dataResult.map(component -> new HoverEvent.EntityTooltipInfo(entityType, uuid, component));
            } catch (Exception var7) {
                return DataResult.error(() -> "Failed to parse tooltip: " + var7.getMessage());
            }
        }

        public List<Component> getTooltipLines() {
            if (this.linesCache == null) {
                this.linesCache = new ArrayList<>();
                this.name.ifPresent(this.linesCache::add);
                this.linesCache.add(Component.translatable("gui.entity_tooltip.type", this.type.getDescription()));
                this.linesCache.add(Component.literal(this.id.toString()));
            }

            return this.linesCache;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            } else if (other != null && this.getClass() == other.getClass()) {
                HoverEvent.EntityTooltipInfo entityTooltipInfo = (HoverEvent.EntityTooltipInfo)other;
                return this.type.equals(entityTooltipInfo.type) && this.id.equals(entityTooltipInfo.id) && this.name.equals(entityTooltipInfo.name);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            int hashCode = this.type.hashCode();
            hashCode = 31 * hashCode + this.id.hashCode();
            return 31 * hashCode + this.name.hashCode();
        }
    }

    public static class ItemStackInfo {
        public static final Codec<HoverEvent.ItemStackInfo> FULL_CODEC = ItemStack.CODEC
            .xmap(HoverEvent.ItemStackInfo::new, HoverEvent.ItemStackInfo::getItemStack);
        private static final Codec<HoverEvent.ItemStackInfo> SIMPLE_CODEC = ItemStack.SIMPLE_ITEM_CODEC
            .xmap(HoverEvent.ItemStackInfo::new, HoverEvent.ItemStackInfo::getItemStack);
        public static final Codec<HoverEvent.ItemStackInfo> CODEC = Codec.withAlternative(FULL_CODEC, SIMPLE_CODEC);
        private final Holder<Item> item;
        private final int count;
        private final DataComponentPatch components;
        @Nullable
        private ItemStack itemStack;

        ItemStackInfo(Holder<Item> item, int count, DataComponentPatch components) {
            this.item = item;
            this.count = count;
            this.components = components;
        }

        public ItemStackInfo(ItemStack stack) {
            this(stack.getItemHolder(), stack.getCount(), stack.getComponentsPatch());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            } else if (other != null && this.getClass() == other.getClass()) {
                HoverEvent.ItemStackInfo itemStackInfo = (HoverEvent.ItemStackInfo)other;
                return this.count == itemStackInfo.count && this.item.equals(itemStackInfo.item) && this.components.equals(itemStackInfo.components);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            int hashCode = this.item.hashCode();
            hashCode = 31 * hashCode + this.count;
            return 31 * hashCode + this.components.hashCode();
        }

        public ItemStack getItemStack() {
            if (this.itemStack == null) {
                this.itemStack = new ItemStack(this.item, this.count, this.components);
            }

            return this.itemStack;
        }

        private static DataResult<HoverEvent.ItemStackInfo> legacyCreate(Component name, @Nullable RegistryOps<?> ops) {
            try {
                CompoundTag compoundTag = TagParser.parseTag(name.getString());
                DynamicOps<Tag> dynamicOps = (DynamicOps<Tag>)(ops != null ? ops.withParent(NbtOps.INSTANCE) : NbtOps.INSTANCE);
                return ItemStack.CODEC.parse(dynamicOps, compoundTag).map(HoverEvent.ItemStackInfo::new);
            } catch (CommandSyntaxException var4) {
                return DataResult.error(() -> "Failed to parse item tag: " + var4.getMessage());
            }
        }
    }

    public interface LegacyConverter<T> {
        DataResult<T> parse(Component name, @Nullable RegistryOps<?> ops);
    }

    record TypedHoverEvent<T>(HoverEvent.Action<T> action, T value) {
        public static final MapCodec<HoverEvent.TypedHoverEvent<?>> CODEC = HoverEvent.Action.CODEC
            .dispatchMap("action", HoverEvent.TypedHoverEvent::action, hoverEvent -> hoverEvent.codec);
        public static final MapCodec<HoverEvent.TypedHoverEvent<?>> LEGACY_CODEC = HoverEvent.Action.CODEC
            .dispatchMap("action", HoverEvent.TypedHoverEvent::action, hoverEvent -> hoverEvent.legacyCodec);
    }
}
