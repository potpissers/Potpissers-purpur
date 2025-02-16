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

    public float armorstandStepHeight = 0.0F;
    private void armorstandSettings() {
        armorstandStepHeight = (float) getDouble("gameplay-mechanics.armorstand.step-height", armorstandStepHeight);
    }

    public boolean disableDropsOnCrammingDeath = false;
    public boolean milkCuresBadOmen = true;
    private void miscGameplayMechanicsSettings() {
        disableDropsOnCrammingDeath = getBoolean("gameplay-mechanics.disable-drops-on-cramming-death", disableDropsOnCrammingDeath);
        milkCuresBadOmen = getBoolean("gameplay-mechanics.milk-cures-bad-omen", milkCuresBadOmen);
    }

    public double minecartMaxSpeed = 0.4D;
    public boolean minecartPlaceAnywhere = false;
    public boolean minecartControllable = false;
    public float minecartControllableStepHeight = 1.0F;
    public double minecartControllableHopBoost = 0.5D;
    public boolean minecartControllableFallDamage = true;
    public double minecartControllableBaseSpeed = 0.1D;
    public Map<Block, Double> minecartControllableBlockSpeeds = new HashMap<>();
    private void minecartSettings() {
        if (PurpurConfig.version < 12) {
            boolean oldBool = getBoolean("gameplay-mechanics.controllable-minecarts.place-anywhere", minecartPlaceAnywhere);
            set("gameplay-mechanics.controllable-minecarts.place-anywhere", null);
            set("gameplay-mechanics.minecart.place-anywhere", oldBool);
            oldBool = getBoolean("gameplay-mechanics.controllable-minecarts.enabled", minecartControllable);
            set("gameplay-mechanics.controllable-minecarts.enabled", null);
            set("gameplay-mechanics.minecart.controllable.enabled", oldBool);
            double oldDouble = getDouble("gameplay-mechanics.controllable-minecarts.step-height", minecartControllableStepHeight);
            set("gameplay-mechanics.controllable-minecarts.step-height", null);
            set("gameplay-mechanics.minecart.controllable.step-height", oldDouble);
            oldDouble = getDouble("gameplay-mechanics.controllable-minecarts.hop-boost", minecartControllableHopBoost);
            set("gameplay-mechanics.controllable-minecarts.hop-boost", null);
            set("gameplay-mechanics.minecart.controllable.hop-boost", oldDouble);
            oldBool = getBoolean("gameplay-mechanics.controllable-minecarts.fall-damage", minecartControllableFallDamage);
            set("gameplay-mechanics.controllable-minecarts.fall-damage", null);
            set("gameplay-mechanics.minecart.controllable.fall-damage", oldBool);
            oldDouble = getDouble("gameplay-mechanics.controllable-minecarts.base-speed", minecartControllableBaseSpeed);
            set("gameplay-mechanics.controllable-minecarts.base-speed", null);
            set("gameplay-mechanics.minecart.controllable.base-speed", oldDouble);
            ConfigurationSection section = getConfigurationSection("gameplay-mechanics.controllable-minecarts.block-speed");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    if ("grass-block".equals(key)) key = "grass_block"; // oopsie
                    oldDouble = section.getDouble(key, minecartControllableBaseSpeed);
                    set("gameplay-mechanics.controllable-minecarts.block-speed." + key, null);
                    set("gameplay-mechanics.minecart.controllable.block-speed." + key, oldDouble);
                }
                set("gameplay-mechanics.controllable-minecarts.block-speed", null);
            }
            set("gameplay-mechanics.controllable-minecarts", null);
        }

        minecartMaxSpeed = getDouble("gameplay-mechanics.minecart.max-speed", minecartMaxSpeed);
        minecartPlaceAnywhere = getBoolean("gameplay-mechanics.minecart.place-anywhere", minecartPlaceAnywhere);
        minecartControllable = getBoolean("gameplay-mechanics.minecart.controllable.enabled", minecartControllable);
        minecartControllableStepHeight = (float) getDouble("gameplay-mechanics.minecart.controllable.step-height", minecartControllableStepHeight);
        minecartControllableHopBoost = getDouble("gameplay-mechanics.minecart.controllable.hop-boost", minecartControllableHopBoost);
        minecartControllableFallDamage = getBoolean("gameplay-mechanics.minecart.controllable.fall-damage", minecartControllableFallDamage);
        minecartControllableBaseSpeed = getDouble("gameplay-mechanics.minecart.controllable.base-speed", minecartControllableBaseSpeed);
        ConfigurationSection section = getConfigurationSection("gameplay-mechanics.minecart.controllable.block-speed");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(key));
                if (block != Blocks.AIR) {
                    minecartControllableBlockSpeeds.put(block, section.getDouble(key, minecartControllableBaseSpeed));
                }
            }
        } else {
            set("gameplay-mechanics.minecart.controllable.block-speed.grass_block", 0.3D);
            set("gameplay-mechanics.minecart.controllable.block-speed.stone", 0.5D);
        }
    }

    public boolean idleTimeoutKick = true;
    public boolean idleTimeoutTickNearbyEntities = true;
    public boolean idleTimeoutCountAsSleeping = false;
    public boolean idleTimeoutUpdateTabList = false;
    public boolean idleTimeoutTargetPlayer = true;
    private void playerSettings() {
        if (PurpurConfig.version < 19) {
            boolean oldVal = getBoolean("gameplay-mechanics.player.idle-timeout.mods-target", idleTimeoutTargetPlayer);
            set("gameplay-mechanics.player.idle-timeout.mods-target", null);
            set("gameplay-mechanics.player.idle-timeout.mobs-target", oldVal);
        }
        idleTimeoutKick = System.getenv("PURPUR_FORCE_IDLE_KICK") == null ? getBoolean("gameplay-mechanics.player.idle-timeout.kick-if-idle", idleTimeoutKick) : Boolean.parseBoolean(System.getenv("PURPUR_FORCE_IDLE_KICK"));
        idleTimeoutTickNearbyEntities = getBoolean("gameplay-mechanics.player.idle-timeout.tick-nearby-entities", idleTimeoutTickNearbyEntities);
        idleTimeoutCountAsSleeping = getBoolean("gameplay-mechanics.player.idle-timeout.count-as-sleeping", idleTimeoutCountAsSleeping);
        idleTimeoutUpdateTabList = getBoolean("gameplay-mechanics.player.idle-timeout.update-tab-list", idleTimeoutUpdateTabList);
        idleTimeoutTargetPlayer = getBoolean("gameplay-mechanics.player.idle-timeout.mobs-target", idleTimeoutTargetPlayer);
    }

    public boolean silkTouchEnabled = false;
    public String silkTouchSpawnerName = "<reset><white>Monster Spawner";
    public List<String> silkTouchSpawnerLore = new ArrayList<>();
    public List<Item> silkTouchTools = new ArrayList<>();
    public int minimumSilkTouchSpawnerRequire = 1;
    private void silkTouchSettings() {
        if (PurpurConfig.version < 21) {
            String oldName = getString("gameplay-mechanics.silk-touch.spawner-name", silkTouchSpawnerName);
            set("gameplay-mechanics.silk-touch.spawner-name", "<reset>" + ChatColor.toMM(oldName.replace("{mob}", "<mob>")));
            List<String> list = new ArrayList<>();
            getList("gameplay-mechanics.silk-touch.spawner-lore", List.of("Spawns a <mob>"))
                    .forEach(line -> list.add("<reset>" + ChatColor.toMM(line.toString().replace("{mob}", "<mob>"))));
            set("gameplay-mechanics.silk-touch.spawner-lore", list);
        }
        silkTouchEnabled = getBoolean("gameplay-mechanics.silk-touch.enabled", silkTouchEnabled);
        silkTouchSpawnerName = getString("gameplay-mechanics.silk-touch.spawner-name", silkTouchSpawnerName);
        minimumSilkTouchSpawnerRequire = getInt("gameplay-mechanics.silk-touch.minimal-level", minimumSilkTouchSpawnerRequire);
        silkTouchSpawnerLore.clear();
        getList("gameplay-mechanics.silk-touch.spawner-lore", List.of("Spawns a <mob>"))
                .forEach(line -> silkTouchSpawnerLore.add(line.toString()));
        silkTouchTools.clear();
        getList("gameplay-mechanics.silk-touch.tools", List.of(
                "minecraft:iron_pickaxe",
                "minecraft:golden_pickaxe",
                "minecraft:diamond_pickaxe",
                "minecraft:netherite_pickaxe"
        )).forEach(key -> {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(key.toString()));
            if (item != Items.AIR) silkTouchTools.add(item);
        });
    }

    public boolean farmlandGetsMoistFromBelow = false;
    private void farmlandSettings() {
        farmlandGetsMoistFromBelow = getBoolean("blocks.farmland.gets-moist-from-below", farmlandGetsMoistFromBelow);
    }

    public boolean turtleEggsBreakFromExpOrbs = false;
    public boolean turtleEggsBreakFromItems = false;
    public boolean turtleEggsBreakFromMinecarts = false;
    private void turtleEggSettings() {
        turtleEggsBreakFromExpOrbs = getBoolean("blocks.turtle_egg.break-from-exp-orbs", turtleEggsBreakFromExpOrbs);
        turtleEggsBreakFromItems = getBoolean("blocks.turtle_egg.break-from-items", turtleEggsBreakFromItems);
        turtleEggsBreakFromMinecarts = getBoolean("blocks.turtle_egg.break-from-minecarts", turtleEggsBreakFromMinecarts);
    }

    public boolean babiesAreRidable = true;
    public boolean untamedTamablesAreRidable = true;
    public boolean useNightVisionWhenRiding = false;
    public boolean useDismountsUnderwaterTag = true;
    private void ridableSettings() {
        babiesAreRidable = getBoolean("ridable-settings.babies-are-ridable", babiesAreRidable);
        untamedTamablesAreRidable = getBoolean("ridable-settings.untamed-tamables-are-ridable", untamedTamablesAreRidable);
        useNightVisionWhenRiding = getBoolean("ridable-settings.use-night-vision", useNightVisionWhenRiding);
        useDismountsUnderwaterTag = getBoolean("ridable-settings.use-dismounts-underwater-tag", useDismountsUnderwaterTag);
    }

    public boolean allayRidable = false;
    public boolean allayRidableInWater = true;
    public boolean allayControllable = true;
    public double allayMaxHealth = 20.0D;
    public double allayScale = 1.0D;
    private void allaySettings() {
        allayRidable = getBoolean("mobs.allay.ridable", allayRidable);
        allayRidableInWater = getBoolean("mobs.allay.ridable-in-water", allayRidableInWater);
        allayControllable = getBoolean("mobs.allay.controllable", allayControllable);
        allayMaxHealth = getDouble("mobs.allay.attributes.max_health", allayMaxHealth);
        allayScale = Mth.clamp(getDouble("mobs.allay.attributes.scale", allayScale), 0.0625D, 16.0D);
    }

    public boolean armadilloRidable = false;
    public boolean armadilloRidableInWater = true;
    public boolean armadilloControllable = true;
    public double armadilloMaxHealth = 12.0D;
    public double armadilloScale = 1.0D;
    private void armadilloSettings() {
        armadilloRidable = getBoolean("mobs.armadillo.ridable", armadilloRidable);
        armadilloRidableInWater = getBoolean("mobs.armadillo.ridable-in-water", armadilloRidableInWater);
        armadilloControllable = getBoolean("mobs.armadillo.controllable", armadilloControllable);
        armadilloMaxHealth = getDouble("mobs.armadillo.attributes.max_health", armadilloMaxHealth);
        armadilloScale = Mth.clamp(getDouble("mobs.armadillo.attributes.scale", armadilloScale), 0.0625D, 16.0D);
    }

    public boolean axolotlRidable = false;
    public boolean axolotlControllable = true;
    public double axolotlMaxHealth = 14.0D;
    public double axolotlScale = 1.0D;
    private void axolotlSettings() {
        axolotlRidable = getBoolean("mobs.axolotl.ridable", axolotlRidable);
        axolotlControllable = getBoolean("mobs.axolotl.controllable", axolotlControllable);
        axolotlMaxHealth = getDouble("mobs.axolotl.attributes.max_health", axolotlMaxHealth);
        axolotlScale = Mth.clamp(getDouble("mobs.axolotl.attributes.scale", axolotlScale), 0.0625D, 16.0D);
    }

    public boolean batRidable = false;
    public boolean batRidableInWater = true;
    public boolean batControllable = true;
    public double batMaxY = 320D;
    public double batMaxHealth = 6.0D;
    public double batScale = 1.0D;
    public double batFollowRange = 16.0D;
    public double batKnockbackResistance = 0.0D;
    public double batMovementSpeed = 0.6D;
    public double batFlyingSpeed = 0.6D;
    public double batArmor = 0.0D;
    public double batArmorToughness = 0.0D;
    public double batAttackKnockback = 0.0D;
    private void batSettings() {
        batRidable = getBoolean("mobs.bat.ridable", batRidable);
        batRidableInWater = getBoolean("mobs.bat.ridable-in-water", batRidableInWater);
        batControllable = getBoolean("mobs.bat.controllable", batControllable);
        batMaxY = getDouble("mobs.bat.ridable-max-y", batMaxY);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.bat.attributes.max-health", batMaxHealth);
            set("mobs.bat.attributes.max-health", null);
            set("mobs.bat.attributes.max_health", oldValue);
        }
        batMaxHealth = getDouble("mobs.bat.attributes.max_health", batMaxHealth);
        batScale = Mth.clamp(getDouble("mobs.bat.attributes.scale", batScale), 0.0625D, 16.0D);
        batFollowRange = getDouble("mobs.bat.attributes.follow_range", batFollowRange);
        batKnockbackResistance = getDouble("mobs.bat.attributes.knockback_resistance", batKnockbackResistance);
        batMovementSpeed = getDouble("mobs.bat.attributes.movement_speed", batMovementSpeed);
        batFlyingSpeed = getDouble("mobs.bat.attributes.flying_speed", batFlyingSpeed);
        batArmor = getDouble("mobs.bat.attributes.armor", batArmor);
        batArmorToughness = getDouble("mobs.bat.attributes.armor_toughness", batArmorToughness);
        batAttackKnockback = getDouble("mobs.bat.attributes.attack_knockback", batAttackKnockback);
    }

    public boolean beeRidable = false;
    public boolean beeRidableInWater = true;
    public boolean beeControllable = true;
    public double beeMaxY = 320D;
    public double beeMaxHealth = 10.0D;
    public double beeScale = 1.0D;
    private void beeSettings() {
        beeRidable = getBoolean("mobs.bee.ridable", beeRidable);
        beeRidableInWater = getBoolean("mobs.bee.ridable-in-water", beeRidableInWater);
        beeControllable = getBoolean("mobs.bee.controllable", beeControllable);
        beeMaxY = getDouble("mobs.bee.ridable-max-y", beeMaxY);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.bee.attributes.max-health", beeMaxHealth);
            set("mobs.bee.attributes.max-health", null);
            set("mobs.bee.attributes.max_health", oldValue);
        }
        beeMaxHealth = getDouble("mobs.bee.attributes.max_health", beeMaxHealth);
        beeScale = Mth.clamp(getDouble("mobs.bee.attributes.scale", beeScale), 0.0625D, 16.0D);
    }

    public boolean blazeRidable = false;
    public boolean blazeRidableInWater = true;
    public boolean blazeControllable = true;
    public double blazeMaxY = 320D;
    public double blazeMaxHealth = 20.0D;
    public double blazeScale = 1.0D;
    private void blazeSettings() {
        blazeRidable = getBoolean("mobs.blaze.ridable", blazeRidable);
        blazeRidableInWater = getBoolean("mobs.blaze.ridable-in-water", blazeRidableInWater);
        blazeControllable = getBoolean("mobs.blaze.controllable", blazeControllable);
        blazeMaxY = getDouble("mobs.blaze.ridable-max-y", blazeMaxY);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.blaze.attributes.max-health", blazeMaxHealth);
            set("mobs.blaze.attributes.max-health", null);
            set("mobs.blaze.attributes.max_health", oldValue);
        }
        blazeMaxHealth = getDouble("mobs.blaze.attributes.max_health", blazeMaxHealth);
        blazeScale = Mth.clamp(getDouble("mobs.blaze.attributes.scale", blazeScale), 0.0625D, 16.0D);
    }

    public boolean boggedRidable = false;
    public boolean boggedRidableInWater = true;
    public boolean boggedControllable = true;
    public double boggedMaxHealth = 16.0D;
    public double boggedScale = 1.0D;
    private void boggedSettings() {
        boggedRidable = getBoolean("mobs.bogged.ridable", boggedRidable);
        boggedRidableInWater = getBoolean("mobs.bogged.ridable-in-water", boggedRidableInWater);
        boggedControllable = getBoolean("mobs.bogged.controllable", boggedControllable);
        boggedMaxHealth = getDouble("mobs.bogged.attributes.max_health", boggedMaxHealth);
        boggedScale = Mth.clamp(getDouble("mobs.bogged.attributes.scale", boggedScale), 0.0625D, 16.0D);
    }

    public boolean camelRidableInWater = false;
    public double camelMaxHealthMin = 32.0D;
    public double camelMaxHealthMax = 32.0D;
    public double camelJumpStrengthMin = 0.42D;
    public double camelJumpStrengthMax = 0.42D;
    public double camelMovementSpeedMin = 0.09D;
    public double camelMovementSpeedMax = 0.09D;
    private void camelSettings() {
        camelRidableInWater = getBoolean("mobs.camel.ridable-in-water", camelRidableInWater);
        camelMaxHealthMin = getDouble("mobs.camel.attributes.max_health.min", camelMaxHealthMin);
        camelMaxHealthMax = getDouble("mobs.camel.attributes.max_health.max", camelMaxHealthMax);
        camelJumpStrengthMin = getDouble("mobs.camel.attributes.jump_strength.min", camelJumpStrengthMin);
        camelJumpStrengthMax = getDouble("mobs.camel.attributes.jump_strength.max", camelJumpStrengthMax);
        camelMovementSpeedMin = getDouble("mobs.camel.attributes.movement_speed.min", camelMovementSpeedMin);
        camelMovementSpeedMax = getDouble("mobs.camel.attributes.movement_speed.max", camelMovementSpeedMax);
    }

    public boolean catRidable = false;
    public boolean catRidableInWater = true;
    public boolean catControllable = true;
    public double catMaxHealth = 10.0D;
    public double catScale = 1.0D;
    public int catSpawnDelay = 1200;
    public int catSpawnSwampHutScanRange = 16;
    public int catSpawnVillageScanRange = 48;
    private void catSettings() {
        catRidable = getBoolean("mobs.cat.ridable", catRidable);
        catRidableInWater = getBoolean("mobs.cat.ridable-in-water", catRidableInWater);
        catControllable = getBoolean("mobs.cat.controllable", catControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.cat.attributes.max-health", catMaxHealth);
            set("mobs.cat.attributes.max-health", null);
            set("mobs.cat.attributes.max_health", oldValue);
        }
        catMaxHealth = getDouble("mobs.cat.attributes.max_health", catMaxHealth);
        catScale = Mth.clamp(getDouble("mobs.cat.attributes.scale", catScale), 0.0625D, 16.0D);
        catSpawnDelay = getInt("mobs.cat.spawn-delay", catSpawnDelay);
        catSpawnSwampHutScanRange = getInt("mobs.cat.scan-range-for-other-cats.swamp-hut", catSpawnSwampHutScanRange);
        catSpawnVillageScanRange = getInt("mobs.cat.scan-range-for-other-cats.village", catSpawnVillageScanRange);
    }

    public boolean caveSpiderRidable = false;
    public boolean caveSpiderRidableInWater = true;
    public boolean caveSpiderControllable = true;
    public double caveSpiderMaxHealth = 12.0D;
    public double caveSpiderScale = 1.0D;
    private void caveSpiderSettings() {
        caveSpiderRidable = getBoolean("mobs.cave_spider.ridable", caveSpiderRidable);
        caveSpiderRidableInWater = getBoolean("mobs.cave_spider.ridable-in-water", caveSpiderRidableInWater);
        caveSpiderControllable = getBoolean("mobs.cave_spider.controllable", caveSpiderControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.cave_spider.attributes.max-health", caveSpiderMaxHealth);
            set("mobs.cave_spider.attributes.max-health", null);
            set("mobs.cave_spider.attributes.max_health", oldValue);
        }
        caveSpiderMaxHealth = getDouble("mobs.cave_spider.attributes.max_health", caveSpiderMaxHealth);
        caveSpiderScale = Mth.clamp(getDouble("mobs.cave_spider.attributes.scale", caveSpiderScale), 0.0625D, 16.0D);
    }

    public boolean chickenRidable = false;
    public boolean chickenRidableInWater = false;
    public boolean chickenControllable = true;
    public double chickenMaxHealth = 4.0D;
    public double chickenScale = 1.0D;
    public boolean chickenRetaliate = false;
    private void chickenSettings() {
        chickenRidable = getBoolean("mobs.chicken.ridable", chickenRidable);
        chickenRidableInWater = getBoolean("mobs.chicken.ridable-in-water", chickenRidableInWater);
        chickenControllable = getBoolean("mobs.chicken.controllable", chickenControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.chicken.attributes.max-health", chickenMaxHealth);
            set("mobs.chicken.attributes.max-health", null);
            set("mobs.chicken.attributes.max_health", oldValue);
        }
        chickenMaxHealth = getDouble("mobs.chicken.attributes.max_health", chickenMaxHealth);
        chickenScale = Mth.clamp(getDouble("mobs.chicken.attributes.scale", chickenScale), 0.0625D, 16.0D);
        chickenRetaliate = getBoolean("mobs.chicken.retaliate", chickenRetaliate);
    }

    public boolean codRidable = false;
    public boolean codControllable = true;
    public double codMaxHealth = 3.0D;
    public double codScale = 1.0D;
    private void codSettings() {
        codRidable = getBoolean("mobs.cod.ridable", codRidable);
        codControllable = getBoolean("mobs.cod.controllable", codControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.cod.attributes.max-health", codMaxHealth);
            set("mobs.cod.attributes.max-health", null);
            set("mobs.cod.attributes.max_health", oldValue);
        }
        codMaxHealth = getDouble("mobs.cod.attributes.max_health", codMaxHealth);
        codScale = Mth.clamp(getDouble("mobs.cod.attributes.scale", codScale), 0.0625D, 16.0D);
    }

    public boolean cowRidable = false;
    public boolean cowRidableInWater = true;
    public boolean cowControllable = true;
    public double cowMaxHealth = 10.0D;
    public double cowScale = 1.0D;
    public int cowFeedMushrooms = 0;
    private void cowSettings() {
        cowRidable = getBoolean("mobs.cow.ridable", cowRidable);
        cowRidableInWater = getBoolean("mobs.cow.ridable-in-water", cowRidableInWater);
        cowControllable = getBoolean("mobs.cow.controllable", cowControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.cow.attributes.max-health", cowMaxHealth);
            set("mobs.cow.attributes.max-health", null);
            set("mobs.cow.attributes.max_health", oldValue);
        }
        cowMaxHealth = getDouble("mobs.cow.attributes.max_health", cowMaxHealth);
        cowScale = Mth.clamp(getDouble("mobs.cow.attributes.scale", cowScale), 0.0625D, 16.0D);
        cowFeedMushrooms = getInt("mobs.cow.feed-mushrooms-for-mooshroom", cowFeedMushrooms);
    }

    public boolean creeperRidable = false;
    public boolean creeperRidableInWater = true;
    public boolean creeperControllable = true;
    public double creeperMaxHealth = 20.0D;
    public double creeperScale = 1.0D;
    public double creeperChargedChance = 0.0D;
    private void creeperSettings() {
        creeperRidable = getBoolean("mobs.creeper.ridable", creeperRidable);
        creeperRidableInWater = getBoolean("mobs.creeper.ridable-in-water", creeperRidableInWater);
        creeperControllable = getBoolean("mobs.creeper.controllable", creeperControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.creeper.attributes.max-health", creeperMaxHealth);
            set("mobs.creeper.attributes.max-health", null);
            set("mobs.creeper.attributes.max_health", oldValue);
        }
        creeperMaxHealth = getDouble("mobs.creeper.attributes.max_health", creeperMaxHealth);
        creeperScale = Mth.clamp(getDouble("mobs.creeper.attributes.scale", creeperScale), 0.0625D, 16.0D);
        creeperChargedChance = getDouble("mobs.creeper.naturally-charged-chance", creeperChargedChance);
    }

    public boolean dolphinRidable = false;
    public boolean dolphinControllable = true;
    public int dolphinSpitCooldown = 20;
    public float dolphinSpitSpeed = 1.0F;
    public float dolphinSpitDamage = 2.0F;
    public double dolphinMaxHealth = 10.0D;
    public double dolphinScale = 1.0D;
    private void dolphinSettings() {
        dolphinRidable = getBoolean("mobs.dolphin.ridable", dolphinRidable);
        dolphinControllable = getBoolean("mobs.dolphin.controllable", dolphinControllable);
        dolphinSpitCooldown = getInt("mobs.dolphin.spit.cooldown", dolphinSpitCooldown);
        dolphinSpitSpeed = (float) getDouble("mobs.dolphin.spit.speed", dolphinSpitSpeed);
        dolphinSpitDamage = (float) getDouble("mobs.dolphin.spit.damage", dolphinSpitDamage);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.dolphin.attributes.max-health", dolphinMaxHealth);
            set("mobs.dolphin.attributes.max-health", null);
            set("mobs.dolphin.attributes.max_health", oldValue);
        }
        dolphinMaxHealth = getDouble("mobs.dolphin.attributes.max_health", dolphinMaxHealth);
        dolphinScale = Mth.clamp(getDouble("mobs.dolphin.attributes.scale", dolphinScale), 0.0625D, 16.0D);
    }

    public boolean donkeyRidableInWater = false;
    public double donkeyMaxHealthMin = 15.0D;
    public double donkeyMaxHealthMax = 30.0D;
    public double donkeyJumpStrengthMin = 0.5D;
    public double donkeyJumpStrengthMax = 0.5D;
    public double donkeyMovementSpeedMin = 0.175D;
    public double donkeyMovementSpeedMax = 0.175D;
    private void donkeySettings() {
        donkeyRidableInWater = getBoolean("mobs.donkey.ridable-in-water", donkeyRidableInWater);
        if (PurpurConfig.version < 10) {
            double oldMin = getDouble("mobs.donkey.attributes.max-health.min", donkeyMaxHealthMin);
            double oldMax = getDouble("mobs.donkey.attributes.max-health.max", donkeyMaxHealthMax);
            set("mobs.donkey.attributes.max-health", null);
            set("mobs.donkey.attributes.max_health.min", oldMin);
            set("mobs.donkey.attributes.max_health.max", oldMax);
        }
        donkeyMaxHealthMin = getDouble("mobs.donkey.attributes.max_health.min", donkeyMaxHealthMin);
        donkeyMaxHealthMax = getDouble("mobs.donkey.attributes.max_health.max", donkeyMaxHealthMax);
        donkeyJumpStrengthMin = getDouble("mobs.donkey.attributes.jump_strength.min", donkeyJumpStrengthMin);
        donkeyJumpStrengthMax = getDouble("mobs.donkey.attributes.jump_strength.max", donkeyJumpStrengthMax);
        donkeyMovementSpeedMin = getDouble("mobs.donkey.attributes.movement_speed.min", donkeyMovementSpeedMin);
        donkeyMovementSpeedMax = getDouble("mobs.donkey.attributes.movement_speed.max", donkeyMovementSpeedMax);
    }

    public boolean drownedRidable = false;
    public boolean drownedRidableInWater = true;
    public boolean drownedControllable = true;
    public double drownedMaxHealth = 20.0D;
    public double drownedScale = 1.0D;
    public double drownedSpawnReinforcements = 0.1D;
    private void drownedSettings() {
        drownedRidable = getBoolean("mobs.drowned.ridable", drownedRidable);
        drownedRidableInWater = getBoolean("mobs.drowned.ridable-in-water", drownedRidableInWater);
        drownedControllable = getBoolean("mobs.drowned.controllable", drownedControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.drowned.attributes.max-health", drownedMaxHealth);
            set("mobs.drowned.attributes.max-health", null);
            set("mobs.drowned.attributes.max_health", oldValue);
        }
        drownedMaxHealth = getDouble("mobs.drowned.attributes.max_health", drownedMaxHealth);
        drownedScale = Mth.clamp(getDouble("mobs.drowned.attributes.scale", drownedScale), 0.0625D, 16.0D);
        drownedSpawnReinforcements = getDouble("mobs.drowned.attributes.spawn_reinforcements", drownedSpawnReinforcements);
    }

    public boolean elderGuardianRidable = false;
    public boolean elderGuardianControllable = true;
    public double elderGuardianMaxHealth = 80.0D;
    public double elderGuardianScale = 1.0D;
    private void elderGuardianSettings() {
        elderGuardianRidable = getBoolean("mobs.elder_guardian.ridable", elderGuardianRidable);
        elderGuardianControllable = getBoolean("mobs.elder_guardian.controllable", elderGuardianControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.elder_guardian.attributes.max-health", elderGuardianMaxHealth);
            set("mobs.elder_guardian.attributes.max-health", null);
            set("mobs.elder_guardian.attributes.max_health", oldValue);
        }
        elderGuardianMaxHealth = getDouble("mobs.elder_guardian.attributes.max_health", elderGuardianMaxHealth);
        elderGuardianScale = Mth.clamp(getDouble("mobs.elder_guardian.attributes.scale", elderGuardianScale), 0.0625D, 16.0D);
    }

    public boolean enderDragonRidable = false;
    public boolean enderDragonRidableInWater = true;
    public boolean enderDragonControllable = true;
    public double enderDragonMaxY = 320D;
    public double enderDragonMaxHealth = 200.0D;
    public boolean enderDragonAlwaysDropsFullExp = false;
    private void enderDragonSettings() {
        enderDragonRidable = getBoolean("mobs.ender_dragon.ridable", enderDragonRidable);
        enderDragonRidableInWater = getBoolean("mobs.ender_dragon.ridable-in-water", enderDragonRidableInWater);
        enderDragonControllable = getBoolean("mobs.ender_dragon.controllable", enderDragonControllable);
        enderDragonMaxY = getDouble("mobs.ender_dragon.ridable-max-y", enderDragonMaxY);
        if (PurpurConfig.version < 8) {
            double oldValue = getDouble("mobs.ender_dragon.max-health", enderDragonMaxHealth);
            set("mobs.ender_dragon.max-health", null);
            set("mobs.ender_dragon.attributes.max_health", oldValue);
        } else if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.ender_dragon.attributes.max-health", enderDragonMaxHealth);
            set("mobs.ender_dragon.attributes.max-health", null);
            set("mobs.ender_dragon.attributes.max_health", oldValue);
        }
        enderDragonMaxHealth = getDouble("mobs.ender_dragon.attributes.max_health", enderDragonMaxHealth);
        enderDragonAlwaysDropsFullExp = getBoolean("mobs.ender_dragon.always-drop-full-exp", enderDragonAlwaysDropsFullExp);
    }

    public boolean endermanRidable = false;
    public boolean endermanRidableInWater = true;
    public boolean endermanControllable = true;
    public double endermanMaxHealth = 40.0D;
    public double endermanScale = 1.0D;
    private void endermanSettings() {
        endermanRidable = getBoolean("mobs.enderman.ridable", endermanRidable);
        endermanRidableInWater = getBoolean("mobs.enderman.ridable-in-water", endermanRidableInWater);
        endermanControllable = getBoolean("mobs.enderman.controllable", endermanControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.enderman.attributes.max-health", endermanMaxHealth);
            set("mobs.enderman.attributes.max-health", null);
            set("mobs.enderman.attributes.max_health", oldValue);
        }
        endermanMaxHealth = getDouble("mobs.enderman.attributes.max_health", endermanMaxHealth);
        endermanScale = Mth.clamp(getDouble("mobs.enderman.attributes.scale", endermanScale), 0.0625D, 16.0D);
    }

    public boolean endermiteRidable = false;
    public boolean endermiteRidableInWater = true;
    public boolean endermiteControllable = true;
    public double endermiteMaxHealth = 8.0D;
    public double endermiteScale = 1.0D;
    private void endermiteSettings() {
        endermiteRidable = getBoolean("mobs.endermite.ridable", endermiteRidable);
        endermiteRidableInWater = getBoolean("mobs.endermite.ridable-in-water", endermiteRidableInWater);
        endermiteControllable = getBoolean("mobs.endermite.controllable", endermiteControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.endermite.attributes.max-health", endermiteMaxHealth);
            set("mobs.endermite.attributes.max-health", null);
            set("mobs.endermite.attributes.max_health", oldValue);
        }
        endermiteMaxHealth = getDouble("mobs.endermite.attributes.max_health", endermiteMaxHealth);
        endermiteScale = Mth.clamp(getDouble("mobs.endermite.attributes.scale", endermiteScale), 0.0625D, 16.0D);
    }

    public boolean evokerRidable = false;
    public boolean evokerRidableInWater = true;
    public boolean evokerControllable = true;
    public double evokerMaxHealth = 24.0D;
    public double evokerScale = 1.0D;
    private void evokerSettings() {
        evokerRidable = getBoolean("mobs.evoker.ridable", evokerRidable);
        evokerRidableInWater = getBoolean("mobs.evoker.ridable-in-water", evokerRidableInWater);
        evokerControllable = getBoolean("mobs.evoker.controllable", evokerControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.evoker.attributes.max-health", evokerMaxHealth);
            set("mobs.evoker.attributes.max-health", null);
            set("mobs.evoker.attributes.max_health", oldValue);
        }
        evokerMaxHealth = getDouble("mobs.evoker.attributes.max_health", evokerMaxHealth);
        evokerScale = Mth.clamp(getDouble("mobs.evoker.attributes.scale", evokerScale), 0.0625D, 16.0D);
    }

    public boolean foxRidable = false;
    public boolean foxRidableInWater = true;
    public boolean foxControllable = true;
    public double foxMaxHealth = 10.0D;
    public double foxScale = 1.0D;
    public boolean foxTypeChangesWithTulips = false;
    private void foxSettings() {
        foxRidable = getBoolean("mobs.fox.ridable", foxRidable);
        foxRidableInWater = getBoolean("mobs.fox.ridable-in-water", foxRidableInWater);
        foxControllable = getBoolean("mobs.fox.controllable", foxControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.fox.attributes.max-health", foxMaxHealth);
            set("mobs.fox.attributes.max-health", null);
            set("mobs.fox.attributes.max_health", oldValue);
        }
        foxMaxHealth = getDouble("mobs.fox.attributes.max_health", foxMaxHealth);
        foxScale = Mth.clamp(getDouble("mobs.fox.attributes.scale", foxScale), 0.0625D, 16.0D);
        foxTypeChangesWithTulips = getBoolean("mobs.fox.tulips-change-type", foxTypeChangesWithTulips);
    }

    public boolean frogRidable = false;
    public boolean frogRidableInWater = true;
    public boolean frogControllable = true;
    public float frogRidableJumpHeight = 0.65F;
    private void frogSettings() {
        frogRidable = getBoolean("mobs.frog.ridable", frogRidable);
        frogRidableInWater = getBoolean("mobs.frog.ridable-in-water", frogRidableInWater);
        frogControllable = getBoolean("mobs.frog.controllable", frogControllable);
        frogRidableJumpHeight = (float) getDouble("mobs.frog.ridable-jump-height", frogRidableJumpHeight);
    }

    public boolean ghastRidable = false;
    public boolean ghastRidableInWater = true;
    public boolean ghastControllable = true;
    public double ghastMaxY = 320D;
    public double ghastMaxHealth = 10.0D;
    public double ghastScale = 1.0D;
    private void ghastSettings() {
        ghastRidable = getBoolean("mobs.ghast.ridable", ghastRidable);
        ghastRidableInWater = getBoolean("mobs.ghast.ridable-in-water", ghastRidableInWater);
        ghastControllable = getBoolean("mobs.ghast.controllable", ghastControllable);
        ghastMaxY = getDouble("mobs.ghast.ridable-max-y", ghastMaxY);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.ghast.attributes.max-health", ghastMaxHealth);
            set("mobs.ghast.attributes.max-health", null);
            set("mobs.ghast.attributes.max_health", oldValue);
        }
        ghastMaxHealth = getDouble("mobs.ghast.attributes.max_health", ghastMaxHealth);
        ghastScale = Mth.clamp(getDouble("mobs.ghast.attributes.scale", ghastScale), 0.0625D, 16.0D);
    }

    public boolean giantRidable = false;
    public boolean giantRidableInWater = true;
    public boolean giantControllable = true;
    public double giantMovementSpeed = 0.5D;
    public double giantAttackDamage = 50.0D;
    public double giantMaxHealth = 100.0D;
    public double giantScale = 1.0D;
    public float giantStepHeight = 2.0F;
    public float giantJumpHeight = 1.0F;
    public boolean giantHaveAI = false;
    public boolean giantHaveHostileAI = false;
    private void giantSettings() {
        giantRidable = getBoolean("mobs.giant.ridable", giantRidable);
        giantRidableInWater = getBoolean("mobs.giant.ridable-in-water", giantRidableInWater);
        giantControllable = getBoolean("mobs.giant.controllable", giantControllable);
        giantMovementSpeed = getDouble("mobs.giant.movement-speed", giantMovementSpeed);
        giantAttackDamage = getDouble("mobs.giant.attack-damage", giantAttackDamage);
        if (PurpurConfig.version < 8) {
            double oldValue = getDouble("mobs.giant.max-health", giantMaxHealth);
            set("mobs.giant.max-health", null);
            set("mobs.giant.attributes.max_health", oldValue);
        } else if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.giant.attributes.max-health", giantMaxHealth);
            set("mobs.giant.attributes.max-health", null);
            set("mobs.giant.attributes.max_health", oldValue);
        }
        giantMaxHealth = getDouble("mobs.giant.attributes.max_health", giantMaxHealth);
        giantScale = Mth.clamp(getDouble("mobs.giant.attributes.scale", giantScale), 0.0625D, 16.0D);
        giantStepHeight = (float) getDouble("mobs.giant.step-height", giantStepHeight);
        giantJumpHeight = (float) getDouble("mobs.giant.jump-height", giantJumpHeight);
        giantHaveAI = getBoolean("mobs.giant.have-ai", giantHaveAI);
        giantHaveHostileAI = getBoolean("mobs.giant.have-hostile-ai", giantHaveHostileAI);
    }

    public boolean glowSquidRidable = false;
    public boolean glowSquidControllable = true;
    public double glowSquidMaxHealth = 10.0D;
    public double glowSquidScale = 1.0D;
    private void glowSquidSettings() {
        glowSquidRidable = getBoolean("mobs.glow_squid.ridable", glowSquidRidable);
        glowSquidControllable = getBoolean("mobs.glow_squid.controllable", glowSquidControllable);
        glowSquidMaxHealth = getDouble("mobs.glow_squid.attributes.max_health", glowSquidMaxHealth);
        glowSquidScale = Mth.clamp(getDouble("mobs.glow_squid.attributes.scale", glowSquidScale), 0.0625D, 16.0D);
    }

    public boolean goatRidable = false;
    public boolean goatRidableInWater = true;
    public boolean goatControllable = true;
    public double goatMaxHealth = 10.0D;
    public double goatScale = 1.0D;
    private void goatSettings() {
        goatRidable = getBoolean("mobs.goat.ridable", goatRidable);
        goatRidableInWater = getBoolean("mobs.goat.ridable-in-water", goatRidableInWater);
        goatControllable = getBoolean("mobs.goat.controllable", goatControllable);
        goatMaxHealth = getDouble("mobs.goat.attributes.max_health", goatMaxHealth);
        goatScale = Mth.clamp(getDouble("mobs.goat.attributes.scale", goatScale), 0.0625D, 16.0D);
    }

    public boolean guardianRidable = false;
    public boolean guardianControllable = true;
    public double guardianMaxHealth = 30.0D;
    public double guardianScale = 1.0D;
    private void guardianSettings() {
        guardianRidable = getBoolean("mobs.guardian.ridable", guardianRidable);
        guardianControllable = getBoolean("mobs.guardian.controllable", guardianControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.guardian.attributes.max-health", guardianMaxHealth);
            set("mobs.guardian.attributes.max-health", null);
            set("mobs.guardian.attributes.max_health", oldValue);
        }
        guardianMaxHealth = getDouble("mobs.guardian.attributes.max_health", guardianMaxHealth);
        guardianScale = Mth.clamp(getDouble("mobs.guardian.attributes.scale", guardianScale), 0.0625D, 16.0D);
    }

    public boolean hoglinRidable = false;
    public boolean hoglinRidableInWater = true;
    public boolean hoglinControllable = true;
    public double hoglinMaxHealth = 40.0D;
    public double hoglinScale = 1.0D;
    private void hoglinSettings() {
        hoglinRidable = getBoolean("mobs.hoglin.ridable", hoglinRidable);
        hoglinRidableInWater = getBoolean("mobs.hoglin.ridable-in-water", hoglinRidableInWater);
        hoglinControllable = getBoolean("mobs.hoglin.controllable", hoglinControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.hoglin.attributes.max-health", hoglinMaxHealth);
            set("mobs.hoglin.attributes.max-health", null);
            set("mobs.hoglin.attributes.max_health", oldValue);
        }
        hoglinMaxHealth = getDouble("mobs.hoglin.attributes.max_health", hoglinMaxHealth);
        hoglinScale = Mth.clamp(getDouble("mobs.hoglin.attributes.scale", hoglinScale), 0.0625D, 16.0D);
    }

    public boolean horseRidableInWater = false;
    public double horseMaxHealthMin = 15.0D;
    public double horseMaxHealthMax = 30.0D;
    public double horseJumpStrengthMin = 0.4D;
    public double horseJumpStrengthMax = 1.0D;
    public double horseMovementSpeedMin = 0.1125D;
    public double horseMovementSpeedMax = 0.3375D;
    private void horseSettings() {
        horseRidableInWater = getBoolean("mobs.horse.ridable-in-water", horseRidableInWater);
        if (PurpurConfig.version < 10) {
            double oldMin = getDouble("mobs.horse.attributes.max-health.min", horseMaxHealthMin);
            double oldMax = getDouble("mobs.horse.attributes.max-health.max", horseMaxHealthMax);
            set("mobs.horse.attributes.max-health", null);
            set("mobs.horse.attributes.max_health.min", oldMin);
            set("mobs.horse.attributes.max_health.max", oldMax);
        }
        horseMaxHealthMin = getDouble("mobs.horse.attributes.max_health.min", horseMaxHealthMin);
        horseMaxHealthMax = getDouble("mobs.horse.attributes.max_health.max", horseMaxHealthMax);
        horseJumpStrengthMin = getDouble("mobs.horse.attributes.jump_strength.min", horseJumpStrengthMin);
        horseJumpStrengthMax = getDouble("mobs.horse.attributes.jump_strength.max", horseJumpStrengthMax);
        horseMovementSpeedMin = getDouble("mobs.horse.attributes.movement_speed.min", horseMovementSpeedMin);
        horseMovementSpeedMax = getDouble("mobs.horse.attributes.movement_speed.max", horseMovementSpeedMax);
    }

    public boolean huskRidable = false;
    public boolean huskRidableInWater = true;
    public boolean huskControllable = true;
    public double huskMaxHealth = 20.0D;
    public double huskScale = 1.0D;
    public double huskSpawnReinforcements = 0.1D;
    private void huskSettings() {
        huskRidable = getBoolean("mobs.husk.ridable", huskRidable);
        huskRidableInWater = getBoolean("mobs.husk.ridable-in-water", huskRidableInWater);
        huskControllable = getBoolean("mobs.husk.controllable", huskControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.husk.attributes.max-health", huskMaxHealth);
            set("mobs.husk.attributes.max-health", null);
            set("mobs.husk.attributes.max_health", oldValue);
        }
        huskMaxHealth = getDouble("mobs.husk.attributes.max_health", huskMaxHealth);
        huskScale = Mth.clamp(getDouble("mobs.husk.attributes.scale", huskScale), 0.0625D, 16.0D);
        huskSpawnReinforcements = getDouble("mobs.husk.attributes.spawn_reinforcements", huskSpawnReinforcements);
    }

    public boolean illusionerRidable = false;
    public boolean illusionerRidableInWater = true;
    public boolean illusionerControllable = true;
    public double illusionerMovementSpeed = 0.5D;
    public double illusionerFollowRange = 18.0D;
    public double illusionerMaxHealth = 32.0D;
    public double illusionerScale = 1.0D;
    private void illusionerSettings() {
        illusionerRidable = getBoolean("mobs.illusioner.ridable", illusionerRidable);
        illusionerRidableInWater = getBoolean("mobs.illusioner.ridable-in-water", illusionerRidableInWater);
        illusionerControllable = getBoolean("mobs.illusioner.controllable", illusionerControllable);
        illusionerMovementSpeed = getDouble("mobs.illusioner.movement-speed", illusionerMovementSpeed);
        illusionerFollowRange = getDouble("mobs.illusioner.follow-range", illusionerFollowRange);
        if (PurpurConfig.version < 8) {
            double oldValue = getDouble("mobs.illusioner.max-health", illusionerMaxHealth);
            set("mobs.illusioner.max-health", null);
            set("mobs.illusioner.attributes.max_health", oldValue);
        } else if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.illusioner.attributes.max-health", illusionerMaxHealth);
            set("mobs.illusioner.attributes.max-health", null);
            set("mobs.illusioner.attributes.max_health", oldValue);
        }
        illusionerMaxHealth = getDouble("mobs.illusioner.attributes.max_health", illusionerMaxHealth);
        illusionerScale = Mth.clamp(getDouble("mobs.illusioner.attributes.scale", illusionerScale), 0.0625D, 16.0D);
    }

    public boolean ironGolemRidable = false;
    public boolean ironGolemRidableInWater = true;
    public boolean ironGolemControllable = true;
    public boolean ironGolemCanSwim = false;
    public double ironGolemMaxHealth = 100.0D;
    public double ironGolemScale = 1.0D;
    private void ironGolemSettings() {
        ironGolemRidable = getBoolean("mobs.iron_golem.ridable", ironGolemRidable);
        ironGolemRidableInWater = getBoolean("mobs.iron_golem.ridable-in-water", ironGolemRidableInWater);
        ironGolemControllable = getBoolean("mobs.iron_golem.controllable", ironGolemControllable);
        ironGolemCanSwim = getBoolean("mobs.iron_golem.can-swim", ironGolemCanSwim);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.iron_golem.attributes.max-health", ironGolemMaxHealth);
            set("mobs.iron_golem.attributes.max-health", null);
            set("mobs.iron_golem.attributes.max_health", oldValue);
        }
        ironGolemMaxHealth = getDouble("mobs.iron_golem.attributes.max_health", ironGolemMaxHealth);
        ironGolemScale = Mth.clamp(getDouble("mobs.iron_golem.attributes.scale", ironGolemScale), 0.0625D, 16.0D);
    }

    public boolean llamaRidable = false;
    public boolean llamaRidableInWater = false;
    public boolean llamaControllable = true;
    public double llamaMaxHealthMin = 15.0D;
    public double llamaMaxHealthMax = 30.0D;
    public double llamaJumpStrengthMin = 0.5D;
    public double llamaJumpStrengthMax = 0.5D;
    public double llamaMovementSpeedMin = 0.175D;
    public double llamaMovementSpeedMax = 0.175D;
    private void llamaSettings() {
        llamaRidable = getBoolean("mobs.llama.ridable", llamaRidable);
        llamaRidableInWater = getBoolean("mobs.llama.ridable-in-water", llamaRidableInWater);
        llamaControllable = getBoolean("mobs.llama.controllable", llamaControllable);
        if (PurpurConfig.version < 10) {
            double oldMin = getDouble("mobs.llama.attributes.max-health.min", llamaMaxHealthMin);
            double oldMax = getDouble("mobs.llama.attributes.max-health.max", llamaMaxHealthMax);
            set("mobs.llama.attributes.max-health", null);
            set("mobs.llama.attributes.max_health.min", oldMin);
            set("mobs.llama.attributes.max_health.max", oldMax);
        }
        llamaMaxHealthMin = getDouble("mobs.llama.attributes.max_health.min", llamaMaxHealthMin);
        llamaMaxHealthMax = getDouble("mobs.llama.attributes.max_health.max", llamaMaxHealthMax);
        llamaJumpStrengthMin = getDouble("mobs.llama.attributes.jump_strength.min", llamaJumpStrengthMin);
        llamaJumpStrengthMax = getDouble("mobs.llama.attributes.jump_strength.max", llamaJumpStrengthMax);
        llamaMovementSpeedMin = getDouble("mobs.llama.attributes.movement_speed.min", llamaMovementSpeedMin);
        llamaMovementSpeedMax = getDouble("mobs.llama.attributes.movement_speed.max", llamaMovementSpeedMax);
    }

    public boolean magmaCubeRidable = false;
    public boolean magmaCubeRidableInWater = true;
    public boolean magmaCubeControllable = true;
    public String magmaCubeMaxHealth = "size * size";
    public String magmaCubeAttackDamage = "size";
    public Map<Integer, Double> magmaCubeMaxHealthCache = new HashMap<>();
    public Map<Integer, Double> magmaCubeAttackDamageCache = new HashMap<>();
    private void magmaCubeSettings() {
        magmaCubeRidable = getBoolean("mobs.magma_cube.ridable", magmaCubeRidable);
        magmaCubeRidableInWater = getBoolean("mobs.magma_cube.ridable-in-water", magmaCubeRidableInWater);
        magmaCubeControllable = getBoolean("mobs.magma_cube.controllable", magmaCubeControllable);
        if (PurpurConfig.version < 10) {
            String oldValue = getString("mobs.magma_cube.attributes.max-health", magmaCubeMaxHealth);
            set("mobs.magma_cube.attributes.max-health", null);
            set("mobs.magma_cube.attributes.max_health", oldValue);
        }
        magmaCubeMaxHealth = getString("mobs.magma_cube.attributes.max_health", magmaCubeMaxHealth);
        magmaCubeAttackDamage = getString("mobs.magma_cube.attributes.attack_damage", magmaCubeAttackDamage);
        magmaCubeMaxHealthCache.clear();
        magmaCubeAttackDamageCache.clear();
    }

    public boolean mooshroomRidable = false;
    public boolean mooshroomRidableInWater = true;
    public boolean mooshroomControllable = true;
    public double mooshroomMaxHealth = 10.0D;
    public double mooshroomScale = 1.0D;
    private void mooshroomSettings() {
        mooshroomRidable = getBoolean("mobs.mooshroom.ridable", mooshroomRidable);
        mooshroomRidableInWater = getBoolean("mobs.mooshroom.ridable-in-water", mooshroomRidableInWater);
        mooshroomControllable = getBoolean("mobs.mooshroom.controllable", mooshroomControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.mooshroom.attributes.max-health", mooshroomMaxHealth);
            set("mobs.mooshroom.attributes.max-health", null);
            set("mobs.mooshroom.attributes.max_health", oldValue);
        }
        mooshroomMaxHealth = getDouble("mobs.mooshroom.attributes.max_health", mooshroomMaxHealth);
        mooshroomScale = Mth.clamp(getDouble("mobs.mooshroom.attributes.scale", mooshroomScale), 0.0625D, 16.0D);
    }

    public boolean muleRidableInWater = false;
    public double muleMaxHealthMin = 15.0D;
    public double muleMaxHealthMax = 30.0D;
    public double muleJumpStrengthMin = 0.5D;
    public double muleJumpStrengthMax = 0.5D;
    public double muleMovementSpeedMin = 0.175D;
    public double muleMovementSpeedMax = 0.175D;
    private void muleSettings() {
        muleRidableInWater = getBoolean("mobs.mule.ridable-in-water", muleRidableInWater);
        if (PurpurConfig.version < 10) {
            double oldMin = getDouble("mobs.mule.attributes.max-health.min", muleMaxHealthMin);
            double oldMax = getDouble("mobs.mule.attributes.max-health.max", muleMaxHealthMax);
            set("mobs.mule.attributes.max-health", null);
            set("mobs.mule.attributes.max_health.min", oldMin);
            set("mobs.mule.attributes.max_health.max", oldMax);
        }
        muleMaxHealthMin = getDouble("mobs.mule.attributes.max_health.min", muleMaxHealthMin);
        muleMaxHealthMax = getDouble("mobs.mule.attributes.max_health.max", muleMaxHealthMax);
        muleJumpStrengthMin = getDouble("mobs.mule.attributes.jump_strength.min", muleJumpStrengthMin);
        muleJumpStrengthMax = getDouble("mobs.mule.attributes.jump_strength.max", muleJumpStrengthMax);
        muleMovementSpeedMin = getDouble("mobs.mule.attributes.movement_speed.min", muleMovementSpeedMin);
        muleMovementSpeedMax = getDouble("mobs.mule.attributes.movement_speed.max", muleMovementSpeedMax);
    }

    public boolean ocelotRidable = false;
    public boolean ocelotRidableInWater = true;
    public boolean ocelotControllable = true;
    public double ocelotMaxHealth = 10.0D;
    public double ocelotScale = 1.0D;
    private void ocelotSettings() {
        ocelotRidable = getBoolean("mobs.ocelot.ridable", ocelotRidable);
        ocelotRidableInWater = getBoolean("mobs.ocelot.ridable-in-water", ocelotRidableInWater);
        ocelotControllable = getBoolean("mobs.ocelot.controllable", ocelotControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.ocelot.attributes.max-health", ocelotMaxHealth);
            set("mobs.ocelot.attributes.max-health", null);
            set("mobs.ocelot.attributes.max_health", oldValue);
        }
        ocelotMaxHealth = getDouble("mobs.ocelot.attributes.max_health", ocelotMaxHealth);
        ocelotScale = Mth.clamp(getDouble("mobs.ocelot.attributes.scale", ocelotScale), 0.0625D, 16.0D);
    }

    public boolean pandaRidable = false;
    public boolean pandaRidableInWater = true;
    public boolean pandaControllable = true;
    public double pandaMaxHealth = 20.0D;
    public double pandaScale = 1.0D;
    private void pandaSettings() {
        pandaRidable = getBoolean("mobs.panda.ridable", pandaRidable);
        pandaRidableInWater = getBoolean("mobs.panda.ridable-in-water", pandaRidableInWater);
        pandaControllable = getBoolean("mobs.panda.controllable", pandaControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.panda.attributes.max-health", pandaMaxHealth);
            set("mobs.panda.attributes.max-health", null);
            set("mobs.panda.attributes.max_health", oldValue);
        }
        pandaMaxHealth = getDouble("mobs.panda.attributes.max_health", pandaMaxHealth);
        pandaScale = Mth.clamp(getDouble("mobs.panda.attributes.scale", pandaScale), 0.0625D, 16.0D);
    }

    public boolean parrotRidable = false;
    public boolean parrotRidableInWater = true;
    public boolean parrotControllable = true;
    public double parrotMaxY = 320D;
    public double parrotMaxHealth = 6.0D;
    public double parrotScale = 1.0D;
    private void parrotSettings() {
        parrotRidable = getBoolean("mobs.parrot.ridable", parrotRidable);
        parrotRidableInWater = getBoolean("mobs.parrot.ridable-in-water", parrotRidableInWater);
        parrotControllable = getBoolean("mobs.parrot.controllable", parrotControllable);
        parrotMaxY = getDouble("mobs.parrot.ridable-max-y", parrotMaxY);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.parrot.attributes.max-health", parrotMaxHealth);
            set("mobs.parrot.attributes.max-health", null);
            set("mobs.parrot.attributes.max_health", oldValue);
        }
        parrotMaxHealth = getDouble("mobs.parrot.attributes.max_health", parrotMaxHealth);
        parrotScale = Mth.clamp(getDouble("mobs.parrot.attributes.scale", parrotScale), 0.0625D, 16.0D);
    }

    public boolean phantomRidable = false;
    public boolean phantomRidableInWater = true;
    public boolean phantomControllable = true;
    public double phantomMaxY = 320D;
    public float phantomFlameDamage = 1.0F;
    public int phantomFlameFireTime = 8;
    public boolean phantomAllowGriefing = false;
    public String phantomMaxHealth = "20.0";
    public String phantomAttackDamage = "6 + size";
    public Map<Integer, Double> phantomMaxHealthCache = new HashMap<>();
    public Map<Integer, Double> phantomAttackDamageCache = new HashMap<>();
    private void phantomSettings() {
        phantomRidable = getBoolean("mobs.phantom.ridable", phantomRidable);
        phantomRidableInWater = getBoolean("mobs.phantom.ridable-in-water", phantomRidableInWater);
        phantomControllable = getBoolean("mobs.phantom.controllable", phantomControllable);
        phantomMaxY = getDouble("mobs.phantom.ridable-max-y", phantomMaxY);
        phantomFlameDamage = (float) getDouble("mobs.phantom.flames.damage", phantomFlameDamage);
        phantomFlameFireTime = getInt("mobs.phantom.flames.fire-time", phantomFlameFireTime);
        phantomAllowGriefing = getBoolean("mobs.phantom.allow-griefing", phantomAllowGriefing);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.phantom.attributes.max-health", Double.parseDouble(phantomMaxHealth));
            set("mobs.phantom.attributes.max-health", null);
            set("mobs.phantom.attributes.max_health", String.valueOf(oldValue));
        }
        if (PurpurConfig.version < 25) {
            double oldValue = getDouble("mobs.phantom.attributes.max_health", Double.parseDouble(phantomMaxHealth));
            set("mobs.phantom.attributes.max_health", String.valueOf(oldValue));
        }
        phantomMaxHealth = getString("mobs.phantom.attributes.max_health", phantomMaxHealth);
        phantomAttackDamage = getString("mobs.phantom.attributes.attack_damage", phantomAttackDamage);
        phantomMaxHealthCache.clear();
        phantomAttackDamageCache.clear();
    }

    public boolean pigRidable = false;
    public boolean pigRidableInWater = false;
    public boolean pigControllable = true;
    public double pigMaxHealth = 10.0D;
    public double pigScale = 1.0D;
    public boolean pigGiveSaddleBack = false;
    private void pigSettings() {
        pigRidable = getBoolean("mobs.pig.ridable", pigRidable);
        pigRidableInWater = getBoolean("mobs.pig.ridable-in-water", pigRidableInWater);
        pigControllable = getBoolean("mobs.pig.controllable", pigControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.pig.attributes.max-health", pigMaxHealth);
            set("mobs.pig.attributes.max-health", null);
            set("mobs.pig.attributes.max_health", oldValue);
        }
        pigMaxHealth = getDouble("mobs.pig.attributes.max_health", pigMaxHealth);
        pigScale = Mth.clamp(getDouble("mobs.pig.attributes.scale", pigScale), 0.0625D, 16.0D);
        pigGiveSaddleBack = getBoolean("mobs.pig.give-saddle-back", pigGiveSaddleBack);
    }

    public boolean piglinRidable = false;
    public boolean piglinRidableInWater = true;
    public boolean piglinControllable = true;
    public double piglinMaxHealth = 16.0D;
    public double piglinScale = 1.0D;
    private void piglinSettings() {
        piglinRidable = getBoolean("mobs.piglin.ridable", piglinRidable);
        piglinRidableInWater = getBoolean("mobs.piglin.ridable-in-water", piglinRidableInWater);
        piglinControllable = getBoolean("mobs.piglin.controllable", piglinControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.piglin.attributes.max-health", piglinMaxHealth);
            set("mobs.piglin.attributes.max-health", null);
            set("mobs.piglin.attributes.max_health", oldValue);
        }
        piglinMaxHealth = getDouble("mobs.piglin.attributes.max_health", piglinMaxHealth);
        piglinScale = Mth.clamp(getDouble("mobs.piglin.attributes.scale", piglinScale), 0.0625D, 16.0D);
    }

    public boolean piglinBruteRidable = false;
    public boolean piglinBruteRidableInWater = true;
    public boolean piglinBruteControllable = true;
    public double piglinBruteMaxHealth = 50.0D;
    public double piglinBruteScale = 1.0D;
    private void piglinBruteSettings() {
        piglinBruteRidable = getBoolean("mobs.piglin_brute.ridable", piglinBruteRidable);
        piglinBruteRidableInWater = getBoolean("mobs.piglin_brute.ridable-in-water", piglinBruteRidableInWater);
        piglinBruteControllable = getBoolean("mobs.piglin_brute.controllable", piglinBruteControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.piglin_brute.attributes.max-health", piglinBruteMaxHealth);
            set("mobs.piglin_brute.attributes.max-health", null);
            set("mobs.piglin_brute.attributes.max_health", oldValue);
        }
        piglinBruteMaxHealth = getDouble("mobs.piglin_brute.attributes.max_health", piglinBruteMaxHealth);
        piglinBruteScale = Mth.clamp(getDouble("mobs.piglin_brute.attributes.scale", piglinBruteScale), 0.0625D, 16.0D);
    }

    public boolean pillagerRidable = false;
    public boolean pillagerRidableInWater = true;
    public boolean pillagerControllable = true;
    public double pillagerMaxHealth = 24.0D;
    public double pillagerScale = 1.0D;
    private void pillagerSettings() {
        pillagerRidable = getBoolean("mobs.pillager.ridable", pillagerRidable);
        pillagerRidableInWater = getBoolean("mobs.pillager.ridable-in-water", pillagerRidableInWater);
        pillagerControllable = getBoolean("mobs.pillager.controllable", pillagerControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.pillager.attributes.max-health", pillagerMaxHealth);
            set("mobs.pillager.attributes.max-health", null);
            set("mobs.pillager.attributes.max_health", oldValue);
        }
        pillagerMaxHealth = getDouble("mobs.pillager.attributes.max_health", pillagerMaxHealth);
        pillagerScale = Mth.clamp(getDouble("mobs.pillager.attributes.scale", pillagerScale), 0.0625D, 16.0D);
    }

    public boolean polarBearRidable = false;
    public boolean polarBearRidableInWater = true;
    public boolean polarBearControllable = true;
    public double polarBearMaxHealth = 30.0D;
    public double polarBearScale = 1.0D;
    public String polarBearBreedableItemString = "";
    public Item polarBearBreedableItem = null;
    private void polarBearSettings() {
        polarBearRidable = getBoolean("mobs.polar_bear.ridable", polarBearRidable);
        polarBearRidableInWater = getBoolean("mobs.polar_bear.ridable-in-water", polarBearRidableInWater);
        polarBearControllable = getBoolean("mobs.polar_bear.controllable", polarBearControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.polar_bear.attributes.max-health", polarBearMaxHealth);
            set("mobs.polar_bear.attributes.max-health", null);
            set("mobs.polar_bear.attributes.max_health", oldValue);
        }
        polarBearMaxHealth = getDouble("mobs.polar_bear.attributes.max_health", polarBearMaxHealth);
        polarBearScale = Mth.clamp(getDouble("mobs.polar_bear.attributes.scale", polarBearScale), 0.0625D, 16.0D);
        polarBearBreedableItemString = getString("mobs.polar_bear.breedable-item", polarBearBreedableItemString);
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(polarBearBreedableItemString));
        if (item != Items.AIR) polarBearBreedableItem = item;
    }

    public boolean pufferfishRidable = false;
    public boolean pufferfishControllable = true;
    public double pufferfishMaxHealth = 3.0D;
    public double pufferfishScale = 1.0D;
    private void pufferfishSettings() {
        pufferfishRidable = getBoolean("mobs.pufferfish.ridable", pufferfishRidable);
        pufferfishControllable = getBoolean("mobs.pufferfish.controllable", pufferfishControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.pufferfish.attributes.max-health", pufferfishMaxHealth);
            set("mobs.pufferfish.attributes.max-health", null);
            set("mobs.pufferfish.attributes.max_health", oldValue);
        }
        pufferfishMaxHealth = getDouble("mobs.pufferfish.attributes.max_health", pufferfishMaxHealth);
        pufferfishScale = Mth.clamp(getDouble("mobs.pufferfish.attributes.scale", pufferfishScale), 0.0625D, 16.0D);
    }

    public boolean rabbitRidable = false;
    public boolean rabbitRidableInWater = true;
    public boolean rabbitControllable = true;
    public double rabbitMaxHealth = 3.0D;
    public double rabbitScale = 1.0D;
    public double rabbitNaturalToast = 0.0D;
    public double rabbitNaturalKiller = 0.0D;
    private void rabbitSettings() {
        rabbitRidable = getBoolean("mobs.rabbit.ridable", rabbitRidable);
        rabbitRidableInWater = getBoolean("mobs.rabbit.ridable-in-water", rabbitRidableInWater);
        rabbitControllable = getBoolean("mobs.rabbit.controllable", rabbitControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.rabbit.attributes.max-health", rabbitMaxHealth);
            set("mobs.rabbit.attributes.max-health", null);
            set("mobs.rabbit.attributes.max_health", oldValue);
        }
        rabbitMaxHealth = getDouble("mobs.rabbit.attributes.max_health", rabbitMaxHealth);
        rabbitScale = Mth.clamp(getDouble("mobs.rabbit.attributes.scale", rabbitScale), 0.0625D, 16.0D);
        rabbitNaturalToast = getDouble("mobs.rabbit.spawn-toast-chance", rabbitNaturalToast);
        rabbitNaturalKiller = getDouble("mobs.rabbit.spawn-killer-rabbit-chance", rabbitNaturalKiller);
    }

    public boolean ravagerRidable = false;
    public boolean ravagerRidableInWater = false;
    public boolean ravagerControllable = true;
    public double ravagerMaxHealth = 100.0D;
    public double ravagerScale = 1.0D;
    private void ravagerSettings() {
        ravagerRidable = getBoolean("mobs.ravager.ridable", ravagerRidable);
        ravagerRidableInWater = getBoolean("mobs.ravager.ridable-in-water", ravagerRidableInWater);
        ravagerControllable = getBoolean("mobs.ravager.controllable", ravagerControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.ravager.attributes.max-health", ravagerMaxHealth);
            set("mobs.ravager.attributes.max-health", null);
            set("mobs.ravager.attributes.max_health", oldValue);
        }
        ravagerMaxHealth = getDouble("mobs.ravager.attributes.max_health", ravagerMaxHealth);
        ravagerScale = Mth.clamp(getDouble("mobs.ravager.attributes.scale", ravagerScale), 0.0625D, 16.0D);
    }

    public boolean salmonRidable = false;
    public boolean salmonControllable = true;
    public double salmonMaxHealth = 3.0D;
    public double salmonScale = 1.0D;
    private void salmonSettings() {
        salmonRidable = getBoolean("mobs.salmon.ridable", salmonRidable);
        salmonControllable = getBoolean("mobs.salmon.controllable", salmonControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.salmon.attributes.max-health", salmonMaxHealth);
            set("mobs.salmon.attributes.max-health", null);
            set("mobs.salmon.attributes.max_health", oldValue);
        }
        salmonMaxHealth = getDouble("mobs.salmon.attributes.max_health", salmonMaxHealth);
        salmonScale = Mth.clamp(getDouble("mobs.salmon.attributes.scale", salmonScale), 0.0625D, 16.0D);
    }

    public boolean sheepRidable = false;
    public boolean sheepRidableInWater = true;
    public boolean sheepControllable = true;
    public double sheepMaxHealth = 8.0D;
    public double sheepScale = 1.0D;
    private void sheepSettings() {
        sheepRidable = getBoolean("mobs.sheep.ridable", sheepRidable);
        sheepRidableInWater = getBoolean("mobs.sheep.ridable-in-water", sheepRidableInWater);
        sheepControllable = getBoolean("mobs.sheep.controllable", sheepControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.sheep.attributes.max-health", sheepMaxHealth);
            set("mobs.sheep.attributes.max-health", null);
            set("mobs.sheep.attributes.max_health", oldValue);
        }
        sheepMaxHealth = getDouble("mobs.sheep.attributes.max_health", sheepMaxHealth);
        sheepScale = Mth.clamp(getDouble("mobs.sheep.attributes.scale", sheepScale), 0.0625D, 16.0D);
    }

    public boolean shulkerRidable = false;
    public boolean shulkerRidableInWater = true;
    public boolean shulkerControllable = true;
    public double shulkerMaxHealth = 30.0D;
    public double shulkerScale = 1.0D;
    private void shulkerSettings() {
        shulkerRidable = getBoolean("mobs.shulker.ridable", shulkerRidable);
        shulkerRidableInWater = getBoolean("mobs.shulker.ridable-in-water", shulkerRidableInWater);
        shulkerControllable = getBoolean("mobs.shulker.controllable", shulkerControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.shulker.attributes.max-health", shulkerMaxHealth);
            set("mobs.shulker.attributes.max-health", null);
            set("mobs.shulker.attributes.max_health", oldValue);
        }
        shulkerMaxHealth = getDouble("mobs.shulker.attributes.max_health", shulkerMaxHealth);
        shulkerScale = Mth.clamp(getDouble("mobs.shulker.attributes.scale", shulkerScale), 0.0625D, Shulker.MAX_SCALE);
    }

    public boolean silverfishRidable = false;
    public boolean silverfishRidableInWater = true;
    public boolean silverfishControllable = true;
    public double silverfishMaxHealth = 8.0D;
    public double silverfishScale = 1.0D;
    public double silverfishMovementSpeed = 0.25D;
    public double silverfishAttackDamage = 1.0D;
    private void silverfishSettings() {
        silverfishRidable = getBoolean("mobs.silverfish.ridable", silverfishRidable);
        silverfishRidableInWater = getBoolean("mobs.silverfish.ridable-in-water", silverfishRidableInWater);
        silverfishControllable = getBoolean("mobs.silverfish.controllable", silverfishControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.silverfish.attributes.max-health", silverfishMaxHealth);
            set("mobs.silverfish.attributes.max-health", null);
            set("mobs.silverfish.attributes.max_health", oldValue);
        }
        silverfishMaxHealth = getDouble("mobs.silverfish.attributes.max_health", silverfishMaxHealth);
        silverfishScale = Mth.clamp(getDouble("mobs.silverfish.attributes.scale", silverfishScale), 0.0625D, 16.0D);
        silverfishMovementSpeed = getDouble("mobs.silverfish.attributes.movement_speed", silverfishMovementSpeed);
        silverfishAttackDamage = getDouble("mobs.silverfish.attributes.attack_damage", silverfishAttackDamage);
    }

    public boolean skeletonRidable = false;
    public boolean skeletonRidableInWater = true;
    public boolean skeletonControllable = true;
    public double skeletonMaxHealth = 20.0D;
    public double skeletonScale = 1.0D;
    private void skeletonSettings() {
        skeletonRidable = getBoolean("mobs.skeleton.ridable", skeletonRidable);
        skeletonRidableInWater = getBoolean("mobs.skeleton.ridable-in-water", skeletonRidableInWater);
        skeletonControllable = getBoolean("mobs.skeleton.controllable", skeletonControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.skeleton.attributes.max-health", skeletonMaxHealth);
            set("mobs.skeleton.attributes.max-health", null);
            set("mobs.skeleton.attributes.max_health", oldValue);
        }
        skeletonMaxHealth = getDouble("mobs.skeleton.attributes.max_health", skeletonMaxHealth);
        skeletonScale = Mth.clamp(getDouble("mobs.skeleton.attributes.scale", skeletonScale), 0.0625D, 16.0D);
    }

    public boolean skeletonHorseRidable = false;
    public boolean skeletonHorseRidableInWater = true;
    public boolean skeletonHorseCanSwim = false;
    public double skeletonHorseMaxHealthMin = 15.0D;
    public double skeletonHorseMaxHealthMax = 15.0D;
    public double skeletonHorseJumpStrengthMin = 0.4D;
    public double skeletonHorseJumpStrengthMax = 1.0D;
    public double skeletonHorseMovementSpeedMin = 0.2D;
    public double skeletonHorseMovementSpeedMax = 0.2D;
    private void skeletonHorseSettings() {
        skeletonHorseRidable = getBoolean("mobs.skeleton_horse.ridable", skeletonHorseRidable);
        skeletonHorseRidableInWater = getBoolean("mobs.skeleton_horse.ridable-in-water", skeletonHorseRidableInWater);
        skeletonHorseCanSwim = getBoolean("mobs.skeleton_horse.can-swim", skeletonHorseCanSwim);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.skeleton_horse.attributes.max-health", skeletonHorseMaxHealthMin);
            set("mobs.skeleton_horse.attributes.max-health", null);
            set("mobs.skeleton_horse.attributes.max_health.min", oldValue);
            set("mobs.skeleton_horse.attributes.max_health.max", oldValue);
        }
        skeletonHorseMaxHealthMin = getDouble("mobs.skeleton_horse.attributes.max_health.min", skeletonHorseMaxHealthMin);
        skeletonHorseMaxHealthMax = getDouble("mobs.skeleton_horse.attributes.max_health.max", skeletonHorseMaxHealthMax);
        skeletonHorseJumpStrengthMin = getDouble("mobs.skeleton_horse.attributes.jump_strength.min", skeletonHorseJumpStrengthMin);
        skeletonHorseJumpStrengthMax = getDouble("mobs.skeleton_horse.attributes.jump_strength.max", skeletonHorseJumpStrengthMax);
        skeletonHorseMovementSpeedMin = getDouble("mobs.skeleton_horse.attributes.movement_speed.min", skeletonHorseMovementSpeedMin);
        skeletonHorseMovementSpeedMax = getDouble("mobs.skeleton_horse.attributes.movement_speed.max", skeletonHorseMovementSpeedMax);
    }

    public boolean slimeRidable = false;
    public boolean slimeRidableInWater = true;
    public boolean slimeControllable = true;
    public String slimeMaxHealth = "size * size";
    public String slimeAttackDamage = "size";
    public Map<Integer, Double> slimeMaxHealthCache = new HashMap<>();
    public Map<Integer, Double> slimeAttackDamageCache = new HashMap<>();
    private void slimeSettings() {
        slimeRidable = getBoolean("mobs.slime.ridable", slimeRidable);
        slimeRidableInWater = getBoolean("mobs.slime.ridable-in-water", slimeRidableInWater);
        slimeControllable = getBoolean("mobs.slime.controllable", slimeControllable);
        if (PurpurConfig.version < 10) {
            String oldValue = getString("mobs.slime.attributes.max-health", slimeMaxHealth);
            set("mobs.slime.attributes.max-health", null);
            set("mobs.slime.attributes.max_health", oldValue);
        }
        slimeMaxHealth = getString("mobs.slime.attributes.max_health", slimeMaxHealth);
        slimeAttackDamage = getString("mobs.slime.attributes.attack_damage", slimeAttackDamage);
        slimeMaxHealthCache.clear();
        slimeAttackDamageCache.clear();
    }

    public boolean snowGolemRidable = false;
    public boolean snowGolemRidableInWater = true;
    public boolean snowGolemControllable = true;
    public boolean snowGolemLeaveTrailWhenRidden = false;
    public double snowGolemMaxHealth = 4.0D;
    public double snowGolemScale = 1.0D;
    public boolean snowGolemPutPumpkinBack = false;
    private void snowGolemSettings() {
        snowGolemRidable = getBoolean("mobs.snow_golem.ridable", snowGolemRidable);
        snowGolemRidableInWater = getBoolean("mobs.snow_golem.ridable-in-water", snowGolemRidableInWater);
        snowGolemControllable = getBoolean("mobs.snow_golem.controllable", snowGolemControllable);
        snowGolemLeaveTrailWhenRidden = getBoolean("mobs.snow_golem.leave-trail-when-ridden", snowGolemLeaveTrailWhenRidden);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.snow_golem.attributes.max-health", snowGolemMaxHealth);
            set("mobs.snow_golem.attributes.max-health", null);
            set("mobs.snow_golem.attributes.max_health", oldValue);
        }
        snowGolemMaxHealth = getDouble("mobs.snow_golem.attributes.max_health", snowGolemMaxHealth);
        snowGolemScale = Mth.clamp(getDouble("mobs.snow_golem.attributes.scale", snowGolemScale), 0.0625D, 16.0D);
        snowGolemPutPumpkinBack = getBoolean("mobs.snow_golem.pumpkin-can-be-added-back", snowGolemPutPumpkinBack);
    }

    public boolean snifferRidable = false;
    public boolean snifferRidableInWater = true;
    public boolean snifferControllable = true;
    public double snifferMaxHealth = 14.0D;
    public double snifferScale = 1.0D;
    private void snifferSettings() {
        snifferRidable = getBoolean("mobs.sniffer.ridable", snifferRidable);
        snifferRidableInWater = getBoolean("mobs.sniffer.ridable-in-water", snifferRidableInWater);
        snifferControllable = getBoolean("mobs.sniffer.controllable", snifferControllable);
        snifferMaxHealth = getDouble("mobs.sniffer.attributes.max_health", snifferMaxHealth);
        snifferScale = Mth.clamp(getDouble("mobs.sniffer.attributes.scale", snifferScale), 0.0625D, 16.0D);
    }

    public boolean squidRidable = false;
    public boolean squidControllable = true;
    public double squidMaxHealth = 10.0D;
    public double squidScale = 1.0D;
    private void squidSettings() {
        squidRidable = getBoolean("mobs.squid.ridable", squidRidable);
        squidControllable = getBoolean("mobs.squid.controllable", squidControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.squid.attributes.max-health", squidMaxHealth);
            set("mobs.squid.attributes.max-health", null);
            set("mobs.squid.attributes.max_health", oldValue);
        }
        squidMaxHealth = getDouble("mobs.squid.attributes.max_health", squidMaxHealth);
        squidScale = Mth.clamp(getDouble("mobs.squid.attributes.scale", squidScale), 0.0625D, 16.0D);
    }

    public boolean spiderRidable = false;
    public boolean spiderRidableInWater = false;
    public boolean spiderControllable = true;
    public double spiderMaxHealth = 16.0D;
    public double spiderScale = 1.0D;
    private void spiderSettings() {
        spiderRidable = getBoolean("mobs.spider.ridable", spiderRidable);
        spiderRidableInWater = getBoolean("mobs.spider.ridable-in-water", spiderRidableInWater);
        spiderControllable = getBoolean("mobs.spider.controllable", spiderControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.spider.attributes.max-health", spiderMaxHealth);
            set("mobs.spider.attributes.max-health", null);
            set("mobs.spider.attributes.max_health", oldValue);
        }
        spiderMaxHealth = getDouble("mobs.spider.attributes.max_health", spiderMaxHealth);
        spiderScale = Mth.clamp(getDouble("mobs.spider.attributes.scale", spiderScale), 0.0625D, 16.0D);
    }

    public boolean strayRidable = false;
    public boolean strayRidableInWater = true;
    public boolean strayControllable = true;
    public double strayMaxHealth = 20.0D;
    public double strayScale = 1.0D;
    private void straySettings() {
        strayRidable = getBoolean("mobs.stray.ridable", strayRidable);
        strayRidableInWater = getBoolean("mobs.stray.ridable-in-water", strayRidableInWater);
        strayControllable = getBoolean("mobs.stray.controllable", strayControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.stray.attributes.max-health", strayMaxHealth);
            set("mobs.stray.attributes.max-health", null);
            set("mobs.stray.attributes.max_health", oldValue);
        }
        strayMaxHealth = getDouble("mobs.stray.attributes.max_health", strayMaxHealth);
        strayScale = Mth.clamp(getDouble("mobs.stray.attributes.scale", strayScale), 0.0625D, 16.0D);
    }

    public boolean striderRidable = false;
    public boolean striderRidableInWater = false;
    public boolean striderControllable = true;
    public double striderMaxHealth = 20.0D;
    public double striderScale = 1.0D;
    private void striderSettings() {
        striderRidable = getBoolean("mobs.strider.ridable", striderRidable);
        striderRidableInWater = getBoolean("mobs.strider.ridable-in-water", striderRidableInWater);
        striderControllable = getBoolean("mobs.strider.controllable", striderControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.strider.attributes.max-health", striderMaxHealth);
            set("mobs.strider.attributes.max-health", null);
            set("mobs.strider.attributes.max_health", oldValue);
        }
        striderMaxHealth = getDouble("mobs.strider.attributes.max_health", striderMaxHealth);
        striderScale = Mth.clamp(getDouble("mobs.strider.attributes.scale", striderScale), 0.0625D, 16.0D);
    }

    public boolean tadpoleRidable = false;
    public boolean tadpoleRidableInWater = true;
    public boolean tadpoleControllable = true;
    private void tadpoleSettings() {
        tadpoleRidable = getBoolean("mobs.tadpole.ridable", tadpoleRidable);
        tadpoleRidableInWater = getBoolean("mobs.tadpole.ridable-in-water", tadpoleRidableInWater);
        tadpoleControllable = getBoolean("mobs.tadpole.controllable", tadpoleControllable);
    }

    public boolean traderLlamaRidable = false;
    public boolean traderLlamaRidableInWater = false;
    public boolean traderLlamaControllable = true;
    public double traderLlamaMaxHealthMin = 15.0D;
    public double traderLlamaMaxHealthMax = 30.0D;
    public double traderLlamaJumpStrengthMin = 0.5D;
    public double traderLlamaJumpStrengthMax = 0.5D;
    public double traderLlamaMovementSpeedMin = 0.175D;
    public double traderLlamaMovementSpeedMax = 0.175D;
    private void traderLlamaSettings() {
        traderLlamaRidable = getBoolean("mobs.trader_llama.ridable", traderLlamaRidable);
        traderLlamaRidableInWater = getBoolean("mobs.trader_llama.ridable-in-water", traderLlamaRidableInWater);
        traderLlamaControllable = getBoolean("mobs.trader_llama.controllable", traderLlamaControllable);
        if (PurpurConfig.version < 10) {
            double oldMin = getDouble("mobs.trader_llama.attributes.max-health.min", traderLlamaMaxHealthMin);
            double oldMax = getDouble("mobs.trader_llama.attributes.max-health.max", traderLlamaMaxHealthMax);
            set("mobs.trader_llama.attributes.max-health", null);
            set("mobs.trader_llama.attributes.max_health.min", oldMin);
            set("mobs.trader_llama.attributes.max_health.max", oldMax);
        }
        traderLlamaMaxHealthMin = getDouble("mobs.trader_llama.attributes.max_health.min", traderLlamaMaxHealthMin);
        traderLlamaMaxHealthMax = getDouble("mobs.trader_llama.attributes.max_health.max", traderLlamaMaxHealthMax);
        traderLlamaJumpStrengthMin = getDouble("mobs.trader_llama.attributes.jump_strength.min", traderLlamaJumpStrengthMin);
        traderLlamaJumpStrengthMax = getDouble("mobs.trader_llama.attributes.jump_strength.max", traderLlamaJumpStrengthMax);
        traderLlamaMovementSpeedMin = getDouble("mobs.trader_llama.attributes.movement_speed.min", traderLlamaMovementSpeedMin);
        traderLlamaMovementSpeedMax = getDouble("mobs.trader_llama.attributes.movement_speed.max", traderLlamaMovementSpeedMax);
    }

    public boolean tropicalFishRidable = false;
    public boolean tropicalFishControllable = true;
    public double tropicalFishMaxHealth = 3.0D;
    public double tropicalFishScale = 1.0D;
    private void tropicalFishSettings() {
        tropicalFishRidable = getBoolean("mobs.tropical_fish.ridable", tropicalFishRidable);
        tropicalFishControllable = getBoolean("mobs.tropical_fish.controllable", tropicalFishControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.tropical_fish.attributes.max-health", tropicalFishMaxHealth);
            set("mobs.tropical_fish.attributes.max-health", null);
            set("mobs.tropical_fish.attributes.max_health", oldValue);
        }
        tropicalFishMaxHealth = getDouble("mobs.tropical_fish.attributes.max_health", tropicalFishMaxHealth);
        tropicalFishScale = Mth.clamp(getDouble("mobs.tropical_fish.attributes.scale", tropicalFishScale), 0.0625D, 16.0D);
    }

    public boolean turtleRidable = false;
    public boolean turtleRidableInWater = true;
    public boolean turtleControllable = true;
    public double turtleMaxHealth = 30.0D;
    public double turtleScale = 1.0D;
    private void turtleSettings() {
        turtleRidable = getBoolean("mobs.turtle.ridable", turtleRidable);
        turtleRidableInWater = getBoolean("mobs.turtle.ridable-in-water", turtleRidableInWater);
        turtleControllable = getBoolean("mobs.turtle.controllable", turtleControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.turtle.attributes.max-health", turtleMaxHealth);
            set("mobs.turtle.attributes.max-health", null);
            set("mobs.turtle.attributes.max_health", oldValue);
        }
        turtleMaxHealth = getDouble("mobs.turtle.attributes.max_health", turtleMaxHealth);
        turtleScale = Mth.clamp(getDouble("mobs.turtle.attributes.scale", turtleScale), 0.0625D, 16.0D);
    }

    public boolean vexRidable = false;
    public boolean vexRidableInWater = true;
    public boolean vexControllable = true;
    public double vexMaxY = 320D;
    public double vexMaxHealth = 14.0D;
    public double vexScale = 1.0D;
    private void vexSettings() {
        vexRidable = getBoolean("mobs.vex.ridable", vexRidable);
        vexRidableInWater = getBoolean("mobs.vex.ridable-in-water", vexRidableInWater);
        vexControllable = getBoolean("mobs.vex.controllable", vexControllable);
        vexMaxY = getDouble("mobs.vex.ridable-max-y", vexMaxY);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.vex.attributes.max-health", vexMaxHealth);
            set("mobs.vex.attributes.max-health", null);
            set("mobs.vex.attributes.max_health", oldValue);
        }
        vexMaxHealth = getDouble("mobs.vex.attributes.max_health", vexMaxHealth);
        vexScale = Mth.clamp(getDouble("mobs.vex.attributes.scale", vexScale), 0.0625D, 16.0D);
    }

    public boolean villagerRidable = false;
    public boolean villagerRidableInWater = true;
    public boolean villagerControllable = true;
    public double villagerMaxHealth = 20.0D;
    public double villagerScale = 1.0D;
    private void villagerSettings() {
        villagerRidable = getBoolean("mobs.villager.ridable", villagerRidable);
        villagerRidableInWater = getBoolean("mobs.villager.ridable-in-water", villagerRidableInWater);
        villagerControllable = getBoolean("mobs.villager.controllable", villagerControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.villager.attributes.max-health", villagerMaxHealth);
            set("mobs.villager.attributes.max-health", null);
            set("mobs.villager.attributes.max_health", oldValue);
        }
        villagerMaxHealth = getDouble("mobs.villager.attributes.max_health", villagerMaxHealth);
        villagerScale = Mth.clamp(getDouble("mobs.villager.attributes.scale", villagerScale), 0.0625D, 16.0D);
    }

    public boolean vindicatorRidable = false;
    public boolean vindicatorRidableInWater = true;
    public boolean vindicatorControllable = true;
    public double vindicatorMaxHealth = 24.0D;
    public double vindicatorScale = 1.0D;
    private void vindicatorSettings() {
        vindicatorRidable = getBoolean("mobs.vindicator.ridable", vindicatorRidable);
        vindicatorRidableInWater = getBoolean("mobs.vindicator.ridable-in-water", vindicatorRidableInWater);
        vindicatorControllable = getBoolean("mobs.vindicator.controllable", vindicatorControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.vindicator.attributes.max-health", vindicatorMaxHealth);
            set("mobs.vindicator.attributes.max-health", null);
            set("mobs.vindicator.attributes.max_health", oldValue);
        }
        vindicatorMaxHealth = getDouble("mobs.vindicator.attributes.max_health", vindicatorMaxHealth);
        vindicatorScale = Mth.clamp(getDouble("mobs.vindicator.attributes.scale", vindicatorScale), 0.0625D, 16.0D);
    }

    public boolean wanderingTraderRidable = false;
    public boolean wanderingTraderRidableInWater = true;
    public boolean wanderingTraderControllable = true;
    public double wanderingTraderMaxHealth = 20.0D;
    public double wanderingTraderScale = 1.0D;
    private void wanderingTraderSettings() {
        wanderingTraderRidable = getBoolean("mobs.wandering_trader.ridable", wanderingTraderRidable);
        wanderingTraderRidableInWater = getBoolean("mobs.wandering_trader.ridable-in-water", wanderingTraderRidableInWater);
        wanderingTraderControllable = getBoolean("mobs.wandering_trader.controllable", wanderingTraderControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.wandering_trader.attributes.max-health", wanderingTraderMaxHealth);
            set("mobs.wandering_trader.attributes.max-health", null);
            set("mobs.wandering_trader.attributes.max_health", oldValue);
        }
        wanderingTraderMaxHealth = getDouble("mobs.wandering_trader.attributes.max_health", wanderingTraderMaxHealth);
        wanderingTraderScale = Mth.clamp(getDouble("mobs.wandering_trader.attributes.scale", wanderingTraderScale), 0.0625D, 16.0D);
    }

    public boolean wardenRidable = false;
    public boolean wardenRidableInWater = true;
    public boolean wardenControllable = true;
    private void wardenSettings() {
        wardenRidable = getBoolean("mobs.warden.ridable", wardenRidable);
        wardenRidableInWater = getBoolean("mobs.warden.ridable-in-water", wardenRidableInWater);
        wardenControllable = getBoolean("mobs.warden.controllable", wardenControllable);
    }

    public boolean witchRidable = false;
    public boolean witchRidableInWater = true;
    public boolean witchControllable = true;
    public double witchMaxHealth = 26.0D;
    public double witchScale = 1.0D;
    private void witchSettings() {
        witchRidable = getBoolean("mobs.witch.ridable", witchRidable);
        witchRidableInWater = getBoolean("mobs.witch.ridable-in-water", witchRidableInWater);
        witchControllable = getBoolean("mobs.witch.controllable", witchControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.witch.attributes.max-health", witchMaxHealth);
            set("mobs.witch.attributes.max-health", null);
            set("mobs.witch.attributes.max_health", oldValue);
        }
        witchMaxHealth = getDouble("mobs.witch.attributes.max_health", witchMaxHealth);
        witchScale = Mth.clamp(getDouble("mobs.witch.attributes.scale", witchScale), 0.0625D, 16.0D);
    }

    public boolean witherRidable = false;
    public boolean witherRidableInWater = true;
    public boolean witherControllable = true;
    public double witherMaxY = 320D;
    public double witherMaxHealth = 300.0D;
    public double witherScale = 1.0D;
    private void witherSettings() {
        witherRidable = getBoolean("mobs.wither.ridable", witherRidable);
        witherRidableInWater = getBoolean("mobs.wither.ridable-in-water", witherRidableInWater);
        witherControllable = getBoolean("mobs.wither.controllable", witherControllable);
        witherMaxY = getDouble("mobs.wither.ridable-max-y", witherMaxY);
        if (PurpurConfig.version < 8) {
            double oldValue = getDouble("mobs.wither.max-health", witherMaxHealth);
            set("mobs.wither.max_health", null);
            set("mobs.wither.attributes.max-health", oldValue);
        } else if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.wither.attributes.max-health", witherMaxHealth);
            set("mobs.wither.attributes.max-health", null);
            set("mobs.wither.attributes.max_health", oldValue);
        }
        witherMaxHealth = getDouble("mobs.wither.attributes.max_health", witherMaxHealth);
        witherScale = Mth.clamp(getDouble("mobs.wither.attributes.scale", witherScale), 0.0625D, 16.0D);
    }

    public boolean witherSkeletonRidable = false;
    public boolean witherSkeletonRidableInWater = true;
    public boolean witherSkeletonControllable = true;
    public double witherSkeletonMaxHealth = 20.0D;
    public double witherSkeletonScale = 1.0D;
    private void witherSkeletonSettings() {
        witherSkeletonRidable = getBoolean("mobs.wither_skeleton.ridable", witherSkeletonRidable);
        witherSkeletonRidableInWater = getBoolean("mobs.wither_skeleton.ridable-in-water", witherSkeletonRidableInWater);
        witherSkeletonControllable = getBoolean("mobs.wither_skeleton.controllable", witherSkeletonControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.wither_skeleton.attributes.max-health", witherSkeletonMaxHealth);
            set("mobs.wither_skeleton.attributes.max-health", null);
            set("mobs.wither_skeleton.attributes.max_health", oldValue);
        }
        witherSkeletonMaxHealth = getDouble("mobs.wither_skeleton.attributes.max_health", witherSkeletonMaxHealth);
        witherSkeletonScale = Mth.clamp(getDouble("mobs.wither_skeleton.attributes.scale", witherSkeletonScale), 0.0625D, 16.0D);
    }

    public boolean wolfRidable = false;
    public boolean wolfRidableInWater = true;
    public boolean wolfControllable = true;
    public double wolfMaxHealth = 8.0D;
    public double wolfScale = 1.0D;
    private void wolfSettings() {
        wolfRidable = getBoolean("mobs.wolf.ridable", wolfRidable);
        wolfRidableInWater = getBoolean("mobs.wolf.ridable-in-water", wolfRidableInWater);
        wolfControllable = getBoolean("mobs.wolf.controllable", wolfControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.wolf.attributes.max-health", wolfMaxHealth);
            set("mobs.wolf.attributes.max-health", null);
            set("mobs.wolf.attributes.max_health", oldValue);
        }
        wolfMaxHealth = getDouble("mobs.wolf.attributes.max_health", wolfMaxHealth);
        wolfScale = Mth.clamp(getDouble("mobs.wolf.attributes.scale", wolfScale), 0.0625D, 16.0D);
    }

    public boolean zoglinRidable = false;
    public boolean zoglinRidableInWater = true;
    public boolean zoglinControllable = true;
    public double zoglinMaxHealth = 40.0D;
    public double zoglinScale = 1.0D;
    private void zoglinSettings() {
        zoglinRidable = getBoolean("mobs.zoglin.ridable", zoglinRidable);
        zoglinRidableInWater = getBoolean("mobs.zoglin.ridable-in-water", zoglinRidableInWater);
        zoglinControllable = getBoolean("mobs.zoglin.controllable", zoglinControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.zoglin.attributes.max-health", zoglinMaxHealth);
            set("mobs.zoglin.attributes.max-health", null);
            set("mobs.zoglin.attributes.max_health", oldValue);
        }
        zoglinMaxHealth = getDouble("mobs.zoglin.attributes.max_health", zoglinMaxHealth);
        zoglinScale = Mth.clamp(getDouble("mobs.zoglin.attributes.scale", zoglinScale), 0.0625D, 16.0D);
    }

    public boolean zombieRidable = false;
    public boolean zombieRidableInWater = true;
    public boolean zombieControllable = true;
    public double zombieMaxHealth = 20.0D;
    public double zombieScale = 1.0D;
    public double zombieSpawnReinforcements = 0.1D;
    private void zombieSettings() {
        zombieRidable = getBoolean("mobs.zombie.ridable", zombieRidable);
        zombieRidableInWater = getBoolean("mobs.zombie.ridable-in-water", zombieRidableInWater);
        zombieControllable = getBoolean("mobs.zombie.controllable", zombieControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.zombie.attributes.max-health", zombieMaxHealth);
            set("mobs.zombie.attributes.max-health", null);
            set("mobs.zombie.attributes.max_health", oldValue);
        }
        zombieMaxHealth = getDouble("mobs.zombie.attributes.max_health", zombieMaxHealth);
        zombieScale = Mth.clamp(getDouble("mobs.zombie.attributes.scale", zombieScale), 0.0625D, 16.0D);
        zombieSpawnReinforcements = getDouble("mobs.zombie.attributes.spawn_reinforcements", zombieSpawnReinforcements);
    }

    public boolean zombieHorseRidable = false;
    public boolean zombieHorseRidableInWater = false;
    public boolean zombieHorseCanSwim = false;
    public double zombieHorseMaxHealthMin = 15.0D;
    public double zombieHorseMaxHealthMax = 15.0D;
    public double zombieHorseJumpStrengthMin = 0.4D;
    public double zombieHorseJumpStrengthMax = 1.0D;
    public double zombieHorseMovementSpeedMin = 0.2D;
    public double zombieHorseMovementSpeedMax = 0.2D;
    public double zombieHorseSpawnChance = 0.0D;
    private void zombieHorseSettings() {
        zombieHorseRidable = getBoolean("mobs.zombie_horse.ridable", zombieHorseRidable);
        zombieHorseRidableInWater = getBoolean("mobs.zombie_horse.ridable-in-water", zombieHorseRidableInWater);
        zombieHorseCanSwim = getBoolean("mobs.zombie_horse.can-swim", zombieHorseCanSwim);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.zombie_horse.attributes.max-health", zombieHorseMaxHealthMin);
            set("mobs.zombie_horse.attributes.max-health", null);
            set("mobs.zombie_horse.attributes.max_health.min", oldValue);
            set("mobs.zombie_horse.attributes.max_health.max", oldValue);
        }
        zombieHorseMaxHealthMin = getDouble("mobs.zombie_horse.attributes.max_health.min", zombieHorseMaxHealthMin);
        zombieHorseMaxHealthMax = getDouble("mobs.zombie_horse.attributes.max_health.max", zombieHorseMaxHealthMax);
        zombieHorseJumpStrengthMin = getDouble("mobs.zombie_horse.attributes.jump_strength.min", zombieHorseJumpStrengthMin);
        zombieHorseJumpStrengthMax = getDouble("mobs.zombie_horse.attributes.jump_strength.max", zombieHorseJumpStrengthMax);
        zombieHorseMovementSpeedMin = getDouble("mobs.zombie_horse.attributes.movement_speed.min", zombieHorseMovementSpeedMin);
        zombieHorseMovementSpeedMax = getDouble("mobs.zombie_horse.attributes.movement_speed.max", zombieHorseMovementSpeedMax);
        zombieHorseSpawnChance = getDouble("mobs.zombie_horse.spawn-chance", zombieHorseSpawnChance);
    }

    public boolean zombieVillagerRidable = false;
    public boolean zombieVillagerRidableInWater = true;
    public boolean zombieVillagerControllable = true;
    public double zombieVillagerMaxHealth = 20.0D;
    public double zombieVillagerScale = 1.0D;
    public double zombieVillagerSpawnReinforcements = 0.1D;
    private void zombieVillagerSettings() {
        zombieVillagerRidable = getBoolean("mobs.zombie_villager.ridable", zombieVillagerRidable);
        zombieVillagerRidableInWater = getBoolean("mobs.zombie_villager.ridable-in-water", zombieVillagerRidableInWater);
        zombieVillagerControllable = getBoolean("mobs.zombie_villager.controllable", zombieVillagerControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.zombie_villager.attributes.max-health", zombieVillagerMaxHealth);
            set("mobs.zombie_villager.attributes.max-health", null);
            set("mobs.zombie_villager.attributes.max_health", oldValue);
        }
        zombieVillagerMaxHealth = getDouble("mobs.zombie_villager.attributes.max_health", zombieVillagerMaxHealth);
        zombieVillagerScale = Mth.clamp(getDouble("mobs.zombie_villager.attributes.scale", zombieVillagerScale), 0.0625D, 16.0D);
        zombieVillagerSpawnReinforcements = getDouble("mobs.zombie_villager.attributes.spawn_reinforcements", zombieVillagerSpawnReinforcements);
    }

    public boolean zombifiedPiglinRidable = false;
    public boolean zombifiedPiglinRidableInWater = true;
    public boolean zombifiedPiglinControllable = true;
    public double zombifiedPiglinMaxHealth = 20.0D;
    public double zombifiedPiglinScale = 1.0D;
    public double zombifiedPiglinSpawnReinforcements = 0.0D;
    private void zombifiedPiglinSettings() {
        zombifiedPiglinRidable = getBoolean("mobs.zombified_piglin.ridable", zombifiedPiglinRidable);
        zombifiedPiglinRidableInWater = getBoolean("mobs.zombified_piglin.ridable-in-water", zombifiedPiglinRidableInWater);
        zombifiedPiglinControllable = getBoolean("mobs.zombified_piglin.controllable", zombifiedPiglinControllable);
        if (PurpurConfig.version < 10) {
            double oldValue = getDouble("mobs.zombified_piglin.attributes.max-health", zombifiedPiglinMaxHealth);
            set("mobs.zombified_piglin.attributes.max-health", null);
            set("mobs.zombified_piglin.attributes.max_health", oldValue);
        }
        zombifiedPiglinMaxHealth = getDouble("mobs.zombified_piglin.attributes.max_health", zombifiedPiglinMaxHealth);
        zombifiedPiglinScale = Mth.clamp(getDouble("mobs.zombified_piglin.attributes.scale", zombifiedPiglinScale), 0.0625D, 16.0D);
        zombifiedPiglinSpawnReinforcements = getDouble("mobs.zombified_piglin.attributes.spawn_reinforcements", zombifiedPiglinSpawnReinforcements);
    }
}
