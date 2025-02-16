package net.minecraft.server;

import com.mojang.logging.LogUtils;
import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.SuppressForbidden;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;

@SuppressForbidden(
    reason = "System.out setup"
)
public class Bootstrap {
    public static final PrintStream STDOUT = System.out;
    private static volatile boolean isBootstrapped;
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final AtomicLong bootstrapDuration = new AtomicLong(-1L);

    public static void bootStrap() {
        if (!isBootstrapped) {
            isBootstrapped = true;
            Instant instant = Instant.now();
            io.papermc.paper.plugin.entrypoint.LaunchEntryPointHandler.enterBootstrappers(); // Paper - Entrypoint for bootstrapping
            if (BuiltInRegistries.REGISTRY.keySet().isEmpty()) {
                throw new IllegalStateException("Unable to load registries");
            } else {
                FireBlock.bootStrap();
                ComposterBlock.bootStrap();
                if (EntityType.getKey(EntityType.PLAYER) == null) {
                    throw new IllegalStateException("Failed loading EntityTypes");
                } else {
                    EntitySelectorOptions.bootStrap();
                    DispenseItemBehavior.bootStrap();
                    CauldronInteraction.bootStrap();
                    // Paper start
                    BuiltInRegistries.bootStrap(() -> {
                        io.papermc.paper.world.worldgen.OptionallyFlatBedrockConditionSource.bootstrap(); // Paper - Flat bedrock generator settings
                    });
                    // Paper end
                    CreativeModeTabs.validate();
                    wrapStreams();
                    bootstrapDuration.set(Duration.between(instant, Instant.now()).toMillis());
                }
                // CraftBukkit start
                // TODO Check what of this is needed, maybe report it to Mojira. if deemed relevant, move to the respective classes
                //  Used in CraftLegacy
                net.minecraft.util.datafix.fixes.BlockStateData.register(1008, "{Name:'minecraft:oak_sign',Properties:{rotation:'0'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'0'}}");
                net.minecraft.util.datafix.fixes.BlockStateData.register(1009, "{Name:'minecraft:oak_sign',Properties:{rotation:'1'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'1'}}");
                net.minecraft.util.datafix.fixes.BlockStateData.register(1010, "{Name:'minecraft:oak_sign',Properties:{rotation:'2'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'2'}}");
                net.minecraft.util.datafix.fixes.BlockStateData.register(1011, "{Name:'minecraft:oak_sign',Properties:{rotation:'3'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'3'}}");
                net.minecraft.util.datafix.fixes.BlockStateData.register(1012, "{Name:'minecraft:oak_sign',Properties:{rotation:'4'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'4'}}");
                net.minecraft.util.datafix.fixes.BlockStateData.register(1013, "{Name:'minecraft:oak_sign',Properties:{rotation:'5'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'5'}}");
                net.minecraft.util.datafix.fixes.BlockStateData.register(1014, "{Name:'minecraft:oak_sign',Properties:{rotation:'6'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'6'}}");
                net.minecraft.util.datafix.fixes.BlockStateData.register(1015, "{Name:'minecraft:oak_sign',Properties:{rotation:'7'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'7'}}");
                net.minecraft.util.datafix.fixes.BlockStateData.register(1016, "{Name:'minecraft:oak_sign',Properties:{rotation:'8'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'8'}}");
                net.minecraft.util.datafix.fixes.BlockStateData.register(1017, "{Name:'minecraft:oak_sign',Properties:{rotation:'9'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'9'}}");
                net.minecraft.util.datafix.fixes.BlockStateData.register(1018, "{Name:'minecraft:oak_sign',Properties:{rotation:'10'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'10'}}");
                net.minecraft.util.datafix.fixes.BlockStateData.register(1019, "{Name:'minecraft:oak_sign',Properties:{rotation:'11'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'11'}}");
                net.minecraft.util.datafix.fixes.BlockStateData.register(1020, "{Name:'minecraft:oak_sign',Properties:{rotation:'12'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'12'}}");
                net.minecraft.util.datafix.fixes.BlockStateData.register(1021, "{Name:'minecraft:oak_sign',Properties:{rotation:'13'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'13'}}");
                net.minecraft.util.datafix.fixes.BlockStateData.register(1022, "{Name:'minecraft:oak_sign',Properties:{rotation:'14'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'14'}}");
                net.minecraft.util.datafix.fixes.BlockStateData.register(1023, "{Name:'minecraft:oak_sign',Properties:{rotation:'15'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'15'}}");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(323, "minecraft:oak_sign");

                net.minecraft.util.datafix.fixes.BlockStateData.register(1440, "{Name:'minecraft:portal',Properties:{axis:'x'}}", "{Name:'minecraft:portal',Properties:{axis:'x'}}");

                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(409, "minecraft:prismarine_shard");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(410, "minecraft:prismarine_crystals");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(411, "minecraft:rabbit");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(412, "minecraft:cooked_rabbit");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(413, "minecraft:rabbit_stew");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(414, "minecraft:rabbit_foot");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(415, "minecraft:rabbit_hide");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(416, "minecraft:armor_stand");

                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(423, "minecraft:mutton");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(424, "minecraft:cooked_mutton");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(425, "minecraft:banner");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(426, "minecraft:end_crystal");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(427, "minecraft:spruce_door");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(428, "minecraft:birch_door");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(429, "minecraft:jungle_door");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(430, "minecraft:acacia_door");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(431, "minecraft:dark_oak_door");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(432, "minecraft:chorus_fruit");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(433, "minecraft:chorus_fruit_popped");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(434, "minecraft:beetroot");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(435, "minecraft:beetroot_seeds");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(436, "minecraft:beetroot_soup");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(437, "minecraft:dragon_breath");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(438, "minecraft:splash_potion");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(439, "minecraft:spectral_arrow");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(440, "minecraft:tipped_arrow");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(441, "minecraft:lingering_potion");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(442, "minecraft:shield");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(443, "minecraft:elytra");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(444, "minecraft:spruce_boat");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(445, "minecraft:birch_boat");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(446, "minecraft:jungle_boat");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(447, "minecraft:acacia_boat");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(448, "minecraft:dark_oak_boat");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(449, "minecraft:totem_of_undying");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(450, "minecraft:shulker_shell");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(452, "minecraft:iron_nugget");
                net.minecraft.util.datafix.fixes.ItemIdFix.ITEM_NAMES.put(453, "minecraft:knowledge_book");

                net.minecraft.util.datafix.fixes.ItemSpawnEggFix.ID_TO_ENTITY[23] = "Arrow";
                // CraftBukkit end
            }
        }
    }

    private static <T> void checkTranslations(Iterable<T> objects, Function<T, String> objectToKeyFunction, Set<String> translationSet) {
        Language instance = Language.getInstance();
        objects.forEach(object -> {
            String string = objectToKeyFunction.apply((T)object);
            if (!instance.has(string)) {
                translationSet.add(string);
            }
        });
    }

    private static void checkGameruleTranslations(final Set<String> translations) {
        final Language instance = Language.getInstance();
        GameRules gameRules = new GameRules(FeatureFlags.REGISTRY.allFlags());
        gameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                if (!instance.has(key.getDescriptionId())) {
                    translations.add(key.getId());
                }
            }
        });
    }

    public static Set<String> getMissingTranslations() {
        Set<String> set = new TreeSet<>();
        checkTranslations(BuiltInRegistries.ATTRIBUTE, Attribute::getDescriptionId, set);
        checkTranslations(BuiltInRegistries.ENTITY_TYPE, EntityType::getDescriptionId, set);
        checkTranslations(BuiltInRegistries.MOB_EFFECT, MobEffect::getDescriptionId, set);
        checkTranslations(BuiltInRegistries.ITEM, Item::getDescriptionId, set);
        checkTranslations(BuiltInRegistries.BLOCK, BlockBehaviour::getDescriptionId, set);
        checkTranslations(BuiltInRegistries.CUSTOM_STAT, resourceLocation -> "stat." + resourceLocation.toString().replace(':', '.'), set);
        checkGameruleTranslations(set);
        return set;
    }

    public static void checkBootstrapCalled(Supplier<String> callSite) {
        if (!isBootstrapped) {
            throw createBootstrapException(callSite);
        }
    }

    private static RuntimeException createBootstrapException(Supplier<String> callSite) {
        try {
            String string = callSite.get();
            return new IllegalArgumentException("Not bootstrapped (called from " + string + ")");
        } catch (Exception var3) {
            RuntimeException runtimeException = new IllegalArgumentException("Not bootstrapped (failed to resolve location)");
            runtimeException.addSuppressed(var3);
            return runtimeException;
        }
    }

    public static void validate() {
        checkBootstrapCalled(() -> "validate");
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            getMissingTranslations().forEach(string -> LOGGER.error("Missing translations: {}", string));
            Commands.validate();
        }

        DefaultAttributes.validate();
    }

    private static void wrapStreams() {
        if (LOGGER.isDebugEnabled()) {
            System.setErr(new DebugLoggedPrintStream("STDERR", System.err));
            System.setOut(new DebugLoggedPrintStream("STDOUT", STDOUT));
        } else {
            System.setErr(new LoggedPrintStream("STDERR", System.err));
            System.setOut(new LoggedPrintStream("STDOUT", STDOUT));
        }
    }

    public static void realStdoutPrintln(String message) {
        STDOUT.println(message);
    }
}
