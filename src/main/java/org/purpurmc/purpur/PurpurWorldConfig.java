package org.purpurmc.purpur;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Tilt;
import org.purpurmc.purpur.tool.Flattenable;
import org.purpurmc.purpur.tool.Strippable;
import org.purpurmc.purpur.tool.Tillable;
import org.purpurmc.purpur.tool.Waxable;
import org.purpurmc.purpur.tool.Weatherable;
import org.apache.commons.lang.BooleanUtils;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Level;
import static org.purpurmc.purpur.PurpurConfig.log;

@SuppressWarnings("unused")
public class PurpurWorldConfig {

    private final String worldName;
    private final World.Environment environment;

    public PurpurWorldConfig(String worldName, World.Environment environment) {
        this.worldName = worldName;
        this.environment = environment;
        init();
    }

    public void init() {
        log("-------- World Settings For [" + worldName + "] --------");
        PurpurConfig.readConfig(PurpurWorldConfig.class, this);
    }

    private void set(String path, Object val) {
        PurpurConfig.config.addDefault("world-settings.default." + path, val);
        PurpurConfig.config.set("world-settings.default." + path, val);
        if (PurpurConfig.config.get("world-settings." + worldName + "." + path) != null) {
            PurpurConfig.config.addDefault("world-settings." + worldName + "." + path, val);
            PurpurConfig.config.set("world-settings." + worldName + "." + path, val);
        }
    }

    private ConfigurationSection getConfigurationSection(String path) {
        ConfigurationSection section = PurpurConfig.config.getConfigurationSection("world-settings." + worldName + "." + path);
        return section != null ? section : PurpurConfig.config.getConfigurationSection("world-settings.default." + path);
    }

    private String getString(String path, String def) {
        PurpurConfig.config.addDefault("world-settings.default." + path, def);
        return PurpurConfig.config.getString("world-settings." + worldName + "." + path, PurpurConfig.config.getString("world-settings.default." + path));
    }

    private boolean getBoolean(String path, boolean def) {
        PurpurConfig.config.addDefault("world-settings.default." + path, def);
        return PurpurConfig.config.getBoolean("world-settings." + worldName + "." + path, PurpurConfig.config.getBoolean("world-settings.default." + path));
    }

    private double getDouble(String path, double def) {
        PurpurConfig.config.addDefault("world-settings.default." + path, def);
        return PurpurConfig.config.getDouble("world-settings." + worldName + "." + path, PurpurConfig.config.getDouble("world-settings.default." + path));
    }

    private int getInt(String path, int def) {
        PurpurConfig.config.addDefault("world-settings.default." + path, def);
        return PurpurConfig.config.getInt("world-settings." + worldName + "." + path, PurpurConfig.config.getInt("world-settings.default." + path));
    }

    private <T> List<?> getList(String path, T def) {
        PurpurConfig.config.addDefault("world-settings.default." + path, def);
        return PurpurConfig.config.getList("world-settings." + worldName + "." + path, PurpurConfig.config.getList("world-settings.default." + path));
    }

    private Map<String, Object> getMap(String path, Map<String, Object> def) {
        final Map<String, Object> fallback = PurpurConfig.getMap("world-settings.default." + path, def);
        final Map<String, Object> value = PurpurConfig.getMap("world-settings." + worldName + "." + path, null);
        return value.isEmpty() ? fallback : value;
    }
}
