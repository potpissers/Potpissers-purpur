package net.minecraft.stats;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

public class ServerStatsCounter extends StatsCounter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final MinecraftServer server;
    private final File file;
    private final Set<Stat<?>> dirty = Sets.newHashSet();

    public ServerStatsCounter(MinecraftServer server, File file) {
        this.server = server;
        this.file = file;
        if (file.isFile()) {
            try {
                this.parseLocal(server.getFixerUpper(), FileUtils.readFileToString(file));
            } catch (IOException var4) {
                LOGGER.error("Couldn't read statistics file {}", file, var4);
            } catch (JsonParseException var5) {
                LOGGER.error("Couldn't parse statistics file {}", file, var5);
            }
        }
        // Paper start - Moved after stat fetching for player state file
        // Moves the loading after vanilla loading, so it overrides the values.
        // Disables saving any forced stats, so it stays at the same value (without enabling disableStatSaving)
        // Fixes stat initialization to not cause a NullPointerException
        // Spigot start
        for (Map.Entry<ResourceLocation, Integer> entry : org.spigotmc.SpigotConfig.forcedStats.entrySet()) {
            Stat<ResourceLocation> wrapper = Stats.CUSTOM.get(java.util.Objects.requireNonNull(BuiltInRegistries.CUSTOM_STAT.getValue(entry.getKey()))); // Paper - ensured by SpigotConfig#stats
            this.stats.put(wrapper, entry.getValue().intValue());
        }
        // Spigot end
        // Paper end - Moved after stat fetching for player state file
    }

    public void save() {
        if (org.spigotmc.SpigotConfig.disableStatSaving) return; // Spigot
        try {
            FileUtils.writeStringToFile(this.file, this.toJson());
        } catch (IOException var2) {
            LOGGER.error("Couldn't save stats", (Throwable)var2);
        }
    }

    @Override
    public void setValue(Player player, Stat<?> stat, int i) {
        if (org.spigotmc.SpigotConfig.disableStatSaving) return; // Spigot
        if (stat.getType() == Stats.CUSTOM && stat.getValue() instanceof final ResourceLocation resourceLocation && org.spigotmc.SpigotConfig.forcedStats.get(resourceLocation) != null) return; // Paper - disable saving forced stats
        super.setValue(player, stat, i);
        this.dirty.add(stat);
    }

    private Set<Stat<?>> getDirty() {
        Set<Stat<?>> set = Sets.newHashSet(this.dirty);
        this.dirty.clear();
        return set;
    }

    public void parseLocal(DataFixer fixerUpper, String json) {
        try {
            try (JsonReader jsonReader = new JsonReader(new StringReader(json))) {
                jsonReader.setLenient(false);
                JsonElement jsonElement = Streams.parse(jsonReader);
                if (!jsonElement.isJsonNull()) {
                    CompoundTag compoundTag = fromJson(jsonElement.getAsJsonObject());
                    compoundTag = DataFixTypes.STATS.updateToCurrentVersion(fixerUpper, compoundTag, NbtUtils.getDataVersion(compoundTag, 1343));
                    if (!compoundTag.contains("stats", 10)) {
                        return;
                    }

                    CompoundTag compound = compoundTag.getCompound("stats");

                    for (String string : compound.getAllKeys()) {
                        if (compound.contains(string, 10)) {
                            Util.ifElse(
                                BuiltInRegistries.STAT_TYPE.getOptional(ResourceLocation.parse(string)),
                                type -> {
                                    CompoundTag compound1 = compound.getCompound(string);

                                    for (String string1 : compound1.getAllKeys()) {
                                        if (compound1.contains(string1, 99)) {
                                            Util.ifElse(
                                                this.getStat(type, string1),
                                                stat -> this.stats.put(stat, compound1.getInt(string1)),
                                                () -> LOGGER.warn("Invalid statistic in {}: Don't know what {} is", this.file, string1)
                                            );
                                        } else {
                                            LOGGER.warn(
                                                "Invalid statistic value in {}: Don't know what {} is for key {}", this.file, compound1.get(string1), string1
                                            );
                                        }
                                    }
                                },
                                () -> LOGGER.warn("Invalid statistic type in {}: Don't know what {} is", this.file, string)
                            );
                        }
                    }

                    return;
                }

                LOGGER.error("Unable to parse Stat data from {}", this.file);
            }
        } catch (IOException | JsonParseException var11) {
            LOGGER.error("Unable to parse Stat data from {}", this.file, var11);
        }
    }

    private <T> Optional<Stat<T>> getStat(StatType<T> type, String location) {
        return Optional.ofNullable(ResourceLocation.tryParse(location)).flatMap(type.getRegistry()::getOptional).map(type::get);
    }

    private static CompoundTag fromJson(JsonObject json) {
        CompoundTag compoundTag = new CompoundTag();

        for (Entry<String, JsonElement> entry : json.entrySet()) {
            JsonElement jsonElement = entry.getValue();
            if (jsonElement.isJsonObject()) {
                compoundTag.put(entry.getKey(), fromJson(jsonElement.getAsJsonObject()));
            } else if (jsonElement.isJsonPrimitive()) {
                JsonPrimitive asJsonPrimitive = jsonElement.getAsJsonPrimitive();
                if (asJsonPrimitive.isNumber()) {
                    compoundTag.putInt(entry.getKey(), asJsonPrimitive.getAsInt());
                }
            }
        }

        return compoundTag;
    }

    protected String toJson() {
        Map<StatType<?>, JsonObject> map = Maps.newHashMap();

        for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Stat<?>> entry : this.stats.object2IntEntrySet()) {
            Stat<?> stat = entry.getKey();
            map.computeIfAbsent(stat.getType(), type -> new JsonObject()).addProperty(getKey(stat).toString(), entry.getIntValue());
        }

        JsonObject jsonObject = new JsonObject();

        for (Entry<StatType<?>, JsonObject> entry1 : map.entrySet()) {
            jsonObject.add(BuiltInRegistries.STAT_TYPE.getKey(entry1.getKey()).toString(), entry1.getValue());
        }

        JsonObject jsonObject1 = new JsonObject();
        jsonObject1.add("stats", jsonObject);
        jsonObject1.addProperty("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        return jsonObject1.toString();
    }

    private static <T> ResourceLocation getKey(Stat<T> stat) {
        return stat.getType().getRegistry().getKey(stat.getValue());
    }

    public void markAllDirty() {
        this.dirty.addAll(this.stats.keySet());
    }

    public void sendStats(ServerPlayer player) {
        Object2IntMap<Stat<?>> map = new Object2IntOpenHashMap<>();

        for (Stat<?> stat : this.getDirty()) {
            map.put(stat, this.getValue(stat));
        }

        player.connection.send(new ClientboundAwardStatsPacket(map));
    }
}
