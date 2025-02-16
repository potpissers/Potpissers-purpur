package net.minecraft.stats;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.inventory.RecipeBookType;

public final class RecipeBookSettings {
    public static final StreamCodec<FriendlyByteBuf, RecipeBookSettings> STREAM_CODEC = StreamCodec.ofMember(
        RecipeBookSettings::write, RecipeBookSettings::read
    );
    private static final Map<RecipeBookType, Pair<String, String>> TAG_FIELDS = ImmutableMap.of(
        RecipeBookType.CRAFTING,
        Pair.of("isGuiOpen", "isFilteringCraftable"),
        RecipeBookType.FURNACE,
        Pair.of("isFurnaceGuiOpen", "isFurnaceFilteringCraftable"),
        RecipeBookType.BLAST_FURNACE,
        Pair.of("isBlastingFurnaceGuiOpen", "isBlastingFurnaceFilteringCraftable"),
        RecipeBookType.SMOKER,
        Pair.of("isSmokerGuiOpen", "isSmokerFilteringCraftable")
    );
    private final Map<RecipeBookType, RecipeBookSettings.TypeSettings> states;

    private RecipeBookSettings(Map<RecipeBookType, RecipeBookSettings.TypeSettings> states) {
        this.states = states;
    }

    public RecipeBookSettings() {
        this(new EnumMap<>(RecipeBookType.class));
    }

    private RecipeBookSettings.TypeSettings getSettings(RecipeBookType type) {
        return this.states.getOrDefault(type, RecipeBookSettings.TypeSettings.DEFAULT);
    }

    private void updateSettings(RecipeBookType type, UnaryOperator<RecipeBookSettings.TypeSettings> updater) {
        this.states.compute(type, (recipeBookType, typeSettings) -> {
            if (typeSettings == null) {
                typeSettings = RecipeBookSettings.TypeSettings.DEFAULT;
            }

            typeSettings = updater.apply(typeSettings);
            if (typeSettings.equals(RecipeBookSettings.TypeSettings.DEFAULT)) {
                typeSettings = null;
            }

            return typeSettings;
        });
    }

    public boolean isOpen(RecipeBookType bookType) {
        return this.getSettings(bookType).open;
    }

    public void setOpen(RecipeBookType bookType, boolean _open) {
        this.updateSettings(bookType, settings -> settings.setOpen(_open));
    }

    public boolean isFiltering(RecipeBookType bookType) {
        return this.getSettings(bookType).filtering;
    }

    public void setFiltering(RecipeBookType bookType, boolean filtering) {
        this.updateSettings(bookType, settings -> settings.setFiltering(filtering));
    }

    private static RecipeBookSettings read(FriendlyByteBuf buffer) {
        Map<RecipeBookType, RecipeBookSettings.TypeSettings> map = new EnumMap<>(RecipeBookType.class);

        for (RecipeBookType recipeBookType : RecipeBookType.values()) {
            boolean _boolean = buffer.readBoolean();
            boolean _boolean1 = buffer.readBoolean();
            if (_boolean || _boolean1) {
                map.put(recipeBookType, new RecipeBookSettings.TypeSettings(_boolean, _boolean1));
            }
        }

        return new RecipeBookSettings(map);
    }

    private void write(FriendlyByteBuf buffer) {
        for (RecipeBookType recipeBookType : RecipeBookType.values()) {
            RecipeBookSettings.TypeSettings typeSettings = this.states.getOrDefault(recipeBookType, RecipeBookSettings.TypeSettings.DEFAULT);
            buffer.writeBoolean(typeSettings.open);
            buffer.writeBoolean(typeSettings.filtering);
        }
    }

    public static RecipeBookSettings read(CompoundTag tag) {
        Map<RecipeBookType, RecipeBookSettings.TypeSettings> map = new EnumMap<>(RecipeBookType.class);
        TAG_FIELDS.forEach((type, settings) -> {
            boolean _boolean = tag.getBoolean(settings.getFirst());
            boolean _boolean1 = tag.getBoolean(settings.getSecond());
            if (_boolean || _boolean1) {
                map.put(type, new RecipeBookSettings.TypeSettings(_boolean, _boolean1));
            }
        });
        return new RecipeBookSettings(map);
    }

    public void write(CompoundTag tag) {
        TAG_FIELDS.forEach((type, settings) -> {
            RecipeBookSettings.TypeSettings typeSettings = this.states.getOrDefault(type, RecipeBookSettings.TypeSettings.DEFAULT);
            tag.putBoolean(settings.getFirst(), typeSettings.open);
            tag.putBoolean(settings.getSecond(), typeSettings.filtering);
        });
    }

    public RecipeBookSettings copy() {
        return new RecipeBookSettings(new EnumMap<>(this.states));
    }

    public void replaceFrom(RecipeBookSettings other) {
        this.states.clear();
        this.states.putAll(other.states);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof RecipeBookSettings && this.states.equals(((RecipeBookSettings)other).states);
    }

    @Override
    public int hashCode() {
        return this.states.hashCode();
    }

    record TypeSettings(boolean open, boolean filtering) {
        public static final RecipeBookSettings.TypeSettings DEFAULT = new RecipeBookSettings.TypeSettings(false, false);

        @Override
        public String toString() {
            return "[open=" + this.open + ", filtering=" + this.filtering + "]";
        }

        public RecipeBookSettings.TypeSettings setOpen(boolean _open) {
            return new RecipeBookSettings.TypeSettings(_open, this.filtering);
        }

        public RecipeBookSettings.TypeSettings setFiltering(boolean filtering) {
            return new RecipeBookSettings.TypeSettings(this.open, filtering);
        }
    }
}
