package net.minecraft.world.level;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicLike;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import org.slf4j.Logger;

public class GameRules {
    // Paper start - allow disabling gamerule limits
    private static final boolean DISABLE_LIMITS = Boolean.getBoolean("paper.disableGameRuleLimits");

    private static int limit(final int limit, final int unlimited) {
        return DISABLE_LIMITS ? unlimited : limit;
    }
    // Paper end - allow disabling gamerule limits

    public static final int DEFAULT_RANDOM_TICK_SPEED = 3;
    static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<GameRules.Key<?>, GameRules.Type<?>> GAME_RULE_TYPES = Maps.newTreeMap(Comparator.comparing(entry -> entry.id));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DOFIRETICK = register(
        "doFireTick", GameRules.Category.UPDATES, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_MOBGRIEFING = register(
        "mobGriefing", GameRules.Category.MOBS, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_KEEPINVENTORY = register(
        "keepInventory", GameRules.Category.PLAYER, GameRules.BooleanValue.create(false)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DOMOBSPAWNING = register(
        "doMobSpawning", GameRules.Category.SPAWNING, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DOMOBLOOT = register(
        "doMobLoot", GameRules.Category.DROPS, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_PROJECTILESCANBREAKBLOCKS = register(
        "projectilesCanBreakBlocks", GameRules.Category.DROPS, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DOBLOCKDROPS = register(
        "doTileDrops", GameRules.Category.DROPS, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DOENTITYDROPS = register(
        "doEntityDrops", GameRules.Category.DROPS, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_COMMANDBLOCKOUTPUT = register(
        "commandBlockOutput", GameRules.Category.CHAT, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_NATURAL_REGENERATION = register(
        "naturalRegeneration", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DAYLIGHT = register(
        "doDaylightCycle", GameRules.Category.UPDATES, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_LOGADMINCOMMANDS = register(
        "logAdminCommands", GameRules.Category.CHAT, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_SHOWDEATHMESSAGES = register(
        "showDeathMessages", GameRules.Category.CHAT, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.IntegerValue> RULE_RANDOMTICKING = register(
        "randomTickSpeed", GameRules.Category.UPDATES, GameRules.IntegerValue.create(3)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_SENDCOMMANDFEEDBACK = register(
        "sendCommandFeedback", GameRules.Category.CHAT, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_REDUCEDDEBUGINFO = register(
        "reducedDebugInfo", GameRules.Category.MISC, GameRules.BooleanValue.create(false, (level, value) -> { // Paper - rename param to match changes
            byte b = (byte)(value.get() ? 22 : 23);

            for (ServerPlayer serverPlayer : level.players()) {
                serverPlayer.connection.send(new ClientboundEntityEventPacket(serverPlayer, b));
            }
        })
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_SPECTATORSGENERATECHUNKS = register(
        "spectatorsGenerateChunks", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.IntegerValue> RULE_SPAWN_RADIUS = register(
        "spawnRadius", GameRules.Category.PLAYER, GameRules.IntegerValue.create(10)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DISABLE_PLAYER_MOVEMENT_CHECK = register(
        "disablePlayerMovementCheck", GameRules.Category.PLAYER, GameRules.BooleanValue.create(false)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DISABLE_ELYTRA_MOVEMENT_CHECK = register(
        "disableElytraMovementCheck", GameRules.Category.PLAYER, GameRules.BooleanValue.create(false)
    );
    public static final GameRules.Key<GameRules.IntegerValue> RULE_MAX_ENTITY_CRAMMING = register(
        "maxEntityCramming", GameRules.Category.MOBS, GameRules.IntegerValue.create(24)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_WEATHER_CYCLE = register(
        "doWeatherCycle", GameRules.Category.UPDATES, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_LIMITED_CRAFTING = register(
        "doLimitedCrafting", GameRules.Category.PLAYER, GameRules.BooleanValue.create(false, (level, value) -> { // Paper - rename param to match changes
            for (ServerPlayer serverPlayer : level.players()) {
                serverPlayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.LIMITED_CRAFTING, value.get() ? 1.0F : 0.0F));
            }
        })
    );
    public static final GameRules.Key<GameRules.IntegerValue> RULE_MAX_COMMAND_CHAIN_LENGTH = register(
        "maxCommandChainLength", GameRules.Category.MISC, GameRules.IntegerValue.create(65536)
    );
    public static final GameRules.Key<GameRules.IntegerValue> RULE_MAX_COMMAND_FORK_COUNT = register(
        "maxCommandForkCount", GameRules.Category.MISC, GameRules.IntegerValue.create(65536)
    );
    public static final GameRules.Key<GameRules.IntegerValue> RULE_COMMAND_MODIFICATION_BLOCK_LIMIT = register(
        "commandModificationBlockLimit", GameRules.Category.MISC, GameRules.IntegerValue.create(32768)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_ANNOUNCE_ADVANCEMENTS = register(
        "announceAdvancements", GameRules.Category.CHAT, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DISABLE_RAIDS = register(
        "disableRaids", GameRules.Category.MOBS, GameRules.BooleanValue.create(false)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DOINSOMNIA = register(
        "doInsomnia", GameRules.Category.SPAWNING, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DO_IMMEDIATE_RESPAWN = register(
        "doImmediateRespawn", GameRules.Category.PLAYER, GameRules.BooleanValue.create(false, (level, value) -> { // Paper - rename param to match changes
            for (ServerPlayer serverPlayer : level.players()) {
                serverPlayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.IMMEDIATE_RESPAWN, value.get() ? 1.0F : 0.0F));
            }
        })
    );
    public static final GameRules.Key<GameRules.IntegerValue> RULE_PLAYERS_NETHER_PORTAL_DEFAULT_DELAY = register(
        "playersNetherPortalDefaultDelay", GameRules.Category.PLAYER, GameRules.IntegerValue.create(80)
    );
    public static final GameRules.Key<GameRules.IntegerValue> RULE_PLAYERS_NETHER_PORTAL_CREATIVE_DELAY = register(
        "playersNetherPortalCreativeDelay", GameRules.Category.PLAYER, GameRules.IntegerValue.create(0)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DROWNING_DAMAGE = register(
        "drowningDamage", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_FALL_DAMAGE = register(
        "fallDamage", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_FIRE_DAMAGE = register(
        "fireDamage", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_FREEZE_DAMAGE = register(
        "freezeDamage", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DO_PATROL_SPAWNING = register(
        "doPatrolSpawning", GameRules.Category.SPAWNING, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DO_TRADER_SPAWNING = register(
        "doTraderSpawning", GameRules.Category.SPAWNING, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DO_WARDEN_SPAWNING = register(
        "doWardenSpawning", GameRules.Category.SPAWNING, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_FORGIVE_DEAD_PLAYERS = register(
        "forgiveDeadPlayers", GameRules.Category.MOBS, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_UNIVERSAL_ANGER = register(
        "universalAnger", GameRules.Category.MOBS, GameRules.BooleanValue.create(false)
    );
    public static final GameRules.Key<GameRules.IntegerValue> RULE_PLAYERS_SLEEPING_PERCENTAGE = register(
        "playersSleepingPercentage", GameRules.Category.PLAYER, GameRules.IntegerValue.create(100)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_BLOCK_EXPLOSION_DROP_DECAY = register(
        "blockExplosionDropDecay", GameRules.Category.DROPS, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_MOB_EXPLOSION_DROP_DECAY = register(
        "mobExplosionDropDecay", GameRules.Category.DROPS, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_TNT_EXPLOSION_DROP_DECAY = register(
        "tntExplosionDropDecay", GameRules.Category.DROPS, GameRules.BooleanValue.create(false)
    );
    public static final GameRules.Key<GameRules.IntegerValue> RULE_SNOW_ACCUMULATION_HEIGHT = register(
        "snowAccumulationHeight", GameRules.Category.UPDATES, GameRules.IntegerValue.create(1)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_WATER_SOURCE_CONVERSION = register(
        "waterSourceConversion", GameRules.Category.UPDATES, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_LAVA_SOURCE_CONVERSION = register(
        "lavaSourceConversion", GameRules.Category.UPDATES, GameRules.BooleanValue.create(false)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_GLOBAL_SOUND_EVENTS = register(
        "globalSoundEvents", GameRules.Category.MISC, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DO_VINES_SPREAD = register(
        "doVinesSpread", GameRules.Category.UPDATES, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.BooleanValue> RULE_ENDER_PEARLS_VANISH_ON_DEATH = register(
        "enderPearlsVanishOnDeath", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true)
    );
    public static final GameRules.Key<GameRules.IntegerValue> RULE_MINECART_MAX_SPEED = register(
        "minecartMaxSpeed",
        GameRules.Category.MISC,
        GameRules.IntegerValue.create(8, 1, limit(1000, Integer.MAX_VALUE), FeatureFlagSet.of(FeatureFlags.MINECART_IMPROVEMENTS), (server, value) -> {}) // Paper - allow disabling gamerule limits
    );
    public static final GameRules.Key<GameRules.IntegerValue> RULE_SPAWN_CHUNK_RADIUS = register(
        "spawnChunkRadius", GameRules.Category.MISC, GameRules.IntegerValue.create(2, 0, limit(32, Integer.MAX_VALUE), FeatureFlagSet.of(), (level, value) -> { // Paper - allow disabling gamerule limits - also, rename param
            ServerLevel serverLevel = level; // CraftBukkit - per-world
            serverLevel.setDefaultSpawnPos(serverLevel.getSharedSpawnPos(), serverLevel.getSharedSpawnAngle());
        })
    );
    private final Map<GameRules.Key<?>, GameRules.Value<?>> rules;
    private final FeatureFlagSet enabledFeatures;
    private final GameRules.Value<?>[] gameruleArray; // Paper - Perf: Use array for gamerule storage

    private static <T extends GameRules.Value<T>> GameRules.Key<T> register(String name, GameRules.Category category, GameRules.Type<T> type) {
        GameRules.Key<T> key = new GameRules.Key<>(name, category);
        GameRules.Type<?> type1 = GAME_RULE_TYPES.put(key, type);
        if (type1 != null) {
            throw new IllegalStateException("Duplicate game rule registration for " + name);
        } else {
            return key;
        }
    }

    public GameRules(FeatureFlagSet enabledFeatures, DynamicLike<?> tag) {
        this(enabledFeatures);
        this.loadFromTag(tag);
    }

    public GameRules(FeatureFlagSet enabledFeatures) {
        this(availableRules(enabledFeatures).collect(ImmutableMap.toImmutableMap(Entry::getKey, entry -> entry.getValue().createRule())), enabledFeatures);
    }

    private static Stream<Entry<GameRules.Key<?>, GameRules.Type<?>>> availableRules(FeatureFlagSet enabledFeatures) {
        return GAME_RULE_TYPES.entrySet().stream().filter(entry -> entry.getValue().requiredFeatures.isSubsetOf(enabledFeatures));
    }

    private GameRules(Map<GameRules.Key<?>, GameRules.Value<?>> rules, FeatureFlagSet enabledFeatures) {
        this.rules = rules;
        this.enabledFeatures = enabledFeatures;

        // Paper start - Perf: Use array for gamerule storage
        int arraySize = GameRules.Key.lastGameRuleIndex + 1;
        GameRules.Value<?>[] values = new GameRules.Value[arraySize];

        for (Entry<GameRules.Key<?>, GameRules.Value<?>> entry : rules.entrySet()) {
            values[entry.getKey().gameRuleIndex] = entry.getValue();
        }

        this.gameruleArray = values;
        // Paper end - Perf: Use array for gamerule storage
    }

    public <T extends GameRules.Value<T>> T getRule(GameRules.Key<T> key) {
        T value = key == null ? null : (T) this.gameruleArray[key.gameRuleIndex]; // Paper - Perf: Use array for gamerule storage
        if (value == null) {
            throw new IllegalArgumentException("Tried to access invalid game rule");
        } else {
            return value;
        }
    }

    public CompoundTag createTag() {
        CompoundTag compoundTag = new CompoundTag();
        this.rules.forEach((key, value) -> compoundTag.putString(key.id, value.serialize()));
        return compoundTag;
    }

    private void loadFromTag(DynamicLike<?> dynamic) {
        this.rules.forEach((key, value) -> dynamic.get(key.id).asString().ifSuccess(value::deserialize));
    }

    public GameRules copy(FeatureFlagSet enabledFeatures) {
        return new GameRules(
            availableRules(enabledFeatures)
                .collect(
                    ImmutableMap.toImmutableMap(
                        Entry::getKey, entry -> this.rules.containsKey(entry.getKey()) ? this.rules.get(entry.getKey()) : entry.getValue().createRule()
                    )
                ),
            enabledFeatures
        );
    }

    public void visitGameRuleTypes(GameRules.GameRuleTypeVisitor visitor) {
        GAME_RULE_TYPES.forEach((key, type) -> this.callVisitorCap(visitor, (GameRules.Key<?>)key, (GameRules.Type<?>)type));
    }

    private <T extends GameRules.Value<T>> void callVisitorCap(GameRules.GameRuleTypeVisitor visitor, GameRules.Key<?> key, GameRules.Type<?> type) {
        if (type.requiredFeatures.isSubsetOf(this.enabledFeatures)) {
            visitor.visit((GameRules.Key<T>) key, (GameRules.Type<T>) type);
            ((GameRules.Type<T>) type).callVisitor(visitor, (GameRules.Key<T>) key);
        }
    }

    public void assignFrom(GameRules rules, @Nullable ServerLevel level) { // CraftBukkit - per-world
        rules.rules.keySet().forEach(key -> this.assignCap((GameRules.Key<?>)key, rules, level)); // CraftBukkit - per-world
    }

    private <T extends GameRules.Value<T>> void assignCap(GameRules.Key<T> key, GameRules rules, @Nullable ServerLevel level) { // CraftBukkit - per-world
        T rule = rules.getRule(key);
        this.<T>getRule(key).setFrom(rule, level); // CraftBukkit - per-world
    }

    public boolean getBoolean(GameRules.Key<GameRules.BooleanValue> key) {
        return this.getRule(key).get();
    }

    public int getInt(GameRules.Key<GameRules.IntegerValue> key) {
        return this.getRule(key).get();
    }

    public static class BooleanValue extends GameRules.Value<GameRules.BooleanValue> {
        private boolean value;

        static GameRules.Type<GameRules.BooleanValue> create(boolean defaultValue, BiConsumer<ServerLevel, GameRules.BooleanValue> changeListener) { // CraftBukkit - per-world
            return new GameRules.Type<>(
                BoolArgumentType::bool,
                type -> new GameRules.BooleanValue(type, defaultValue),
                changeListener,
                GameRules.GameRuleTypeVisitor::visitBoolean,
                FeatureFlagSet.of()
            );
        }

        static GameRules.Type<GameRules.BooleanValue> create(boolean defaultValue) {
            return create(defaultValue, (key, value) -> {});
        }

        public BooleanValue(GameRules.Type<GameRules.BooleanValue> type, boolean value) {
            super(type);
            this.value = value;
        }

        @Override
        // Paper start - Add WorldGameRuleChangeEvent
        protected void updateFromArgument(CommandContext<CommandSourceStack> context, String paramName, GameRules.Key<BooleanValue> gameRuleKey) {
            io.papermc.paper.event.world.WorldGameRuleChangeEvent event = new io.papermc.paper.event.world.WorldGameRuleChangeEvent(context.getSource().getBukkitWorld(), context.getSource().getBukkitSender(), (org.bukkit.GameRule<Boolean>) org.bukkit.GameRule.getByName(gameRuleKey.toString()), String.valueOf(BoolArgumentType.getBool(context, paramName)));
            if (!event.callEvent()) return;
            this.value = Boolean.parseBoolean(event.getValue());
        // Paper end - Add WorldGameRuleChangeEvent
        }

        public boolean get() {
            return this.value;
        }

        public void set(boolean value, @Nullable ServerLevel level) { // CraftBukkit - per-world
            this.value = value;
            this.onChanged(level); // CraftBukkit - per-world
        }

        @Override
        public String serialize() {
            return Boolean.toString(this.value);
        }

        @Override
        public void deserialize(String value) { // PAIL - protected->public
            this.value = Boolean.parseBoolean(value);
        }

        @Override
        public int getCommandResult() {
            return this.value ? 1 : 0;
        }

        @Override
        protected GameRules.BooleanValue getSelf() {
            return this;
        }

        @Override
        protected GameRules.BooleanValue copy() {
            return new GameRules.BooleanValue(this.type, this.value);
        }

        @Override
        public void setFrom(GameRules.BooleanValue value, @Nullable ServerLevel level) { // CraftBukkit - per-world
            this.value = value.value;
            this.onChanged(level); // CraftBukkit - per-world
        }
    }

    public static enum Category {
        PLAYER("gamerule.category.player"),
        MOBS("gamerule.category.mobs"),
        SPAWNING("gamerule.category.spawning"),
        DROPS("gamerule.category.drops"),
        UPDATES("gamerule.category.updates"),
        CHAT("gamerule.category.chat"),
        MISC("gamerule.category.misc");

        private final String descriptionId;

        private Category(final String descriptionId) {
            this.descriptionId = descriptionId;
        }

        public String getDescriptionId() {
            return this.descriptionId;
        }
    }

    public interface GameRuleTypeVisitor {
        default <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
        }

        default void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
        }

        default void visitInteger(GameRules.Key<GameRules.IntegerValue> key, GameRules.Type<GameRules.IntegerValue> type) {
        }
    }

    public static class IntegerValue extends GameRules.Value<GameRules.IntegerValue> {
        private int value;

        private static GameRules.Type<GameRules.IntegerValue> create(int defaultValue, BiConsumer<ServerLevel, GameRules.IntegerValue> changeListener) { // CraftBukkit - per-world
            return new GameRules.Type<>(
                IntegerArgumentType::integer,
                type -> new GameRules.IntegerValue(type, defaultValue),
                changeListener,
                GameRules.GameRuleTypeVisitor::visitInteger,
                FeatureFlagSet.of()
            );
        }

        static GameRules.Type<GameRules.IntegerValue> create(
            int defaultValue, int min, int max, FeatureFlagSet requiredFeatures, BiConsumer<ServerLevel, GameRules.IntegerValue> changeListener // CraftBukkit - per-world
        ) {
            return new GameRules.Type<>(
                () -> IntegerArgumentType.integer(min, max),
                type -> new GameRules.IntegerValue(type, defaultValue),
                changeListener,
                GameRules.GameRuleTypeVisitor::visitInteger,
                requiredFeatures
            );
        }

        static GameRules.Type<GameRules.IntegerValue> create(int defaultValue) {
            return create(defaultValue, (minecraftServer, integerValue) -> {});
        }

        public IntegerValue(GameRules.Type<GameRules.IntegerValue> type, int value) {
            super(type);
            this.value = value;
        }

        @Override
        // Paper start - Add WorldGameRuleChangeEvent
        protected void updateFromArgument(CommandContext<CommandSourceStack> context, String paramName, GameRules.Key<IntegerValue> gameRuleKey) {
            io.papermc.paper.event.world.WorldGameRuleChangeEvent event = new io.papermc.paper.event.world.WorldGameRuleChangeEvent(context.getSource().getBukkitWorld(), context.getSource().getBukkitSender(), (org.bukkit.GameRule<Integer>) org.bukkit.GameRule.getByName(gameRuleKey.toString()), String.valueOf(IntegerArgumentType.getInteger(context, paramName)));
            if (!event.callEvent()) return;
            this.value = Integer.parseInt(event.getValue());
        // Paper end - Add WorldGameRuleChangeEvent
        }

        public int get() {
            return this.value;
        }

        public void set(int value, @Nullable ServerLevel level) { // CraftBukkit - per-world
            this.value = value;
            this.onChanged(level); // CraftBukkit - per-world
        }

        @Override
        public String serialize() {
            return Integer.toString(this.value);
        }

        @Override
        public void deserialize(String value) { // PAIL - protected->public
            this.value = safeParse(value);
        }

        public boolean tryDeserialize(String name) {
            try {
                StringReader stringReader = new StringReader(name);
                this.value = (Integer)this.type.argument.get().parse(stringReader);
                return !stringReader.canRead();
            } catch (CommandSyntaxException var3) {
                return false;
            }
        }

        private static int safeParse(String strValue) {
            if (!strValue.isEmpty()) {
                try {
                    return Integer.parseInt(strValue);
                } catch (NumberFormatException var2) {
                    GameRules.LOGGER.warn("Failed to parse integer {}", strValue);
                }
            }

            return 0;
        }

        @Override
        public int getCommandResult() {
            return this.value;
        }

        @Override
        protected GameRules.IntegerValue getSelf() {
            return this;
        }

        @Override
        protected GameRules.IntegerValue copy() {
            return new GameRules.IntegerValue(this.type, this.value);
        }

        @Override
        public void setFrom(GameRules.IntegerValue value, @Nullable ServerLevel level) { // CraftBukkit - per-world
            this.value = value.value;
            this.onChanged(level); // CraftBukkit - per-world
        }
    }

    public static final class Key<T extends GameRules.Value<T>> {
        // Paper start - Perf: Use array for gamerule storage
        public static int lastGameRuleIndex = 0;
        public final int gameRuleIndex = lastGameRuleIndex++;
        // Paper end - Perf: Use array for gamerule storage
        final String id;
        private final GameRules.Category category;

        public Key(String id, GameRules.Category category) {
            this.id = id;
            this.category = category;
        }

        @Override
        public String toString() {
            return this.id;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof GameRules.Key && ((GameRules.Key)other).id.equals(this.id);
        }

        @Override
        public int hashCode() {
            return this.id.hashCode();
        }

        public String getId() {
            return this.id;
        }

        public String getDescriptionId() {
            return "gamerule." + this.id;
        }

        public GameRules.Category getCategory() {
            return this.category;
        }
    }

    public static class Type<T extends GameRules.Value<T>> {
        final Supplier<ArgumentType<?>> argument;
        private final Function<GameRules.Type<T>, T> constructor;
        final BiConsumer<ServerLevel, T> callback; // CraftBukkit - per-world
        private final GameRules.VisitorCaller<T> visitorCaller;
        final FeatureFlagSet requiredFeatures;

        Type(
            Supplier<ArgumentType<?>> argument,
            Function<GameRules.Type<T>, T> constructor,
            BiConsumer<ServerLevel, T> callback, // CraftBukkit - per-world
            GameRules.VisitorCaller<T> visitorCaller,
            FeatureFlagSet requiredFeature
        ) {
            this.argument = argument;
            this.constructor = constructor;
            this.callback = callback;
            this.visitorCaller = visitorCaller;
            this.requiredFeatures = requiredFeature;
        }

        public RequiredArgumentBuilder<CommandSourceStack, ?> createArgument(String name) {
            return Commands.argument(name, this.argument.get());
        }

        public T createRule() {
            return this.constructor.apply(this);
        }

        public void callVisitor(GameRules.GameRuleTypeVisitor visitor, GameRules.Key<T> key) {
            this.visitorCaller.call(visitor, key, this);
        }

        public FeatureFlagSet requiredFeatures() {
            return this.requiredFeatures;
        }
    }

    public abstract static class Value<T extends GameRules.Value<T>> {
        protected final GameRules.Type<T> type;

        public Value(GameRules.Type<T> type) {
            this.type = type;
        }

        protected abstract void updateFromArgument(CommandContext<CommandSourceStack> context, String paramName, GameRules.Key<T> gameRuleKey); // Paper - Add WorldGameRuleChangeEvent

        public void setFromArgument(CommandContext<CommandSourceStack> context, String paramName, GameRules.Key<T> gameRuleKey) { // Paper - Add WorldGameRuleChangeEvent
            this.updateFromArgument(context, paramName, gameRuleKey); // Paper - Add WorldGameRuleChangeEvent
            this.onChanged(context.getSource().getLevel()); // CraftBukkit - per-world
        }

        public void onChanged(@Nullable ServerLevel level) { // CraftBukkit - per-world
            if (level != null) { // CraftBukkit - per-world
                this.type.callback.accept(level, this.getSelf()); // CraftBukkit - per-world
            }
        }

        public abstract void deserialize(String value); // PAIL - private->public

        public abstract String serialize();

        @Override
        public String toString() {
            return this.serialize();
        }

        public abstract int getCommandResult();

        protected abstract T getSelf();

        protected abstract T copy();

        public abstract void setFrom(T value, @Nullable ServerLevel level); // CraftBukkit - per-world
    }

    interface VisitorCaller<T extends GameRules.Value<T>> {
        void call(GameRules.GameRuleTypeVisitor visitor, GameRules.Key<T> key, GameRules.Type<T> type);
    }
}
