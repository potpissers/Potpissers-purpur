package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;

public class DataPackCommand {
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_PACK = new DynamicCommandExceptionType(
        pack -> Component.translatableEscape("commands.datapack.unknown", pack)
    );
    private static final DynamicCommandExceptionType ERROR_PACK_ALREADY_ENABLED = new DynamicCommandExceptionType(
        pack -> Component.translatableEscape("commands.datapack.enable.failed", pack)
    );
    private static final DynamicCommandExceptionType ERROR_PACK_ALREADY_DISABLED = new DynamicCommandExceptionType(
        pack -> Component.translatableEscape("commands.datapack.disable.failed", pack)
    );
    private static final DynamicCommandExceptionType ERROR_CANNOT_DISABLE_FEATURE = new DynamicCommandExceptionType(
        object -> Component.translatableEscape("commands.datapack.disable.failed.feature", object)
    );
    private static final Dynamic2CommandExceptionType ERROR_PACK_FEATURES_NOT_ENABLED = new Dynamic2CommandExceptionType(
        (object, object1) -> Component.translatableEscape("commands.datapack.enable.failed.no_flags", object, object1)
    );
    private static final SuggestionProvider<CommandSourceStack> SELECTED_PACKS = (commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggest(
        commandContext.getSource().getServer().getPackRepository().getSelectedIds().stream().map(StringArgumentType::escapeIfRequired), suggestionsBuilder
    );
    private static final SuggestionProvider<CommandSourceStack> UNSELECTED_PACKS = (commandContext, suggestionsBuilder) -> {
        PackRepository packRepository = commandContext.getSource().getServer().getPackRepository();
        Collection<String> selectedIds = packRepository.getSelectedIds();
        FeatureFlagSet featureFlagSet = commandContext.getSource().enabledFeatures();
        return SharedSuggestionProvider.suggest(
            packRepository.getAvailablePacks()
                .stream()
                .filter(pack -> pack.getRequestedFeatures().isSubsetOf(featureFlagSet))
                .map(Pack::getId)
                .filter(string -> !selectedIds.contains(string))
                .map(StringArgumentType::escapeIfRequired),
            suggestionsBuilder
        );
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("datapack")
                .requires(commandSourceStack -> commandSourceStack.hasPermission(2))
                .then(
                    Commands.literal("enable")
                        .then(
                            Commands.argument("name", StringArgumentType.string())
                                .suggests(UNSELECTED_PACKS)
                                .executes(
                                    context -> enablePack(
                                        context.getSource(),
                                        getPack(context, "name", true),
                                        (enabledPacks, packToEnable) -> packToEnable.getDefaultPosition()
                                            .insert(enabledPacks, packToEnable, Pack::selectionConfig, false)
                                    )
                                )
                                .then(
                                    Commands.literal("after")
                                        .then(
                                            Commands.argument("existing", StringArgumentType.string())
                                                .suggests(SELECTED_PACKS)
                                                .executes(
                                                    context -> enablePack(
                                                        context.getSource(),
                                                        getPack(context, "name", true),
                                                        (enabledPacks, packToEnable) -> enabledPacks.add(
                                                            enabledPacks.indexOf(getPack(context, "existing", false)) + 1, packToEnable
                                                        )
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("before")
                                        .then(
                                            Commands.argument("existing", StringArgumentType.string())
                                                .suggests(SELECTED_PACKS)
                                                .executes(
                                                    context -> enablePack(
                                                        context.getSource(),
                                                        getPack(context, "name", true),
                                                        (enabledPacks, packToEnable) -> enabledPacks.add(
                                                            enabledPacks.indexOf(getPack(context, "existing", false)), packToEnable
                                                        )
                                                    )
                                                )
                                        )
                                )
                                .then(Commands.literal("last").executes(context -> enablePack(context.getSource(), getPack(context, "name", true), List::add)))
                                .then(
                                    Commands.literal("first")
                                        .executes(
                                            context -> enablePack(
                                                context.getSource(),
                                                getPack(context, "name", true),
                                                (enabledPacks, packToEnable) -> enabledPacks.add(0, packToEnable)
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("disable")
                        .then(
                            Commands.argument("name", StringArgumentType.string())
                                .suggests(SELECTED_PACKS)
                                .executes(context -> disablePack(context.getSource(), getPack(context, "name", false)))
                        )
                )
                .then(
                    Commands.literal("list")
                        .executes(context -> listPacks(context.getSource()))
                        .then(Commands.literal("available").executes(context -> listAvailablePacks(context.getSource())))
                        .then(Commands.literal("enabled").executes(context -> listEnabledPacks(context.getSource())))
                )
        );
    }

    private static int enablePack(CommandSourceStack source, Pack pack, DataPackCommand.Inserter priorityCallback) throws CommandSyntaxException {
        PackRepository packRepository = source.getServer().getPackRepository();
        List<Pack> list = Lists.newArrayList(packRepository.getSelectedPacks());
        priorityCallback.apply(list, pack);
        source.sendSuccess(() -> Component.translatable("commands.datapack.modify.enable", pack.getChatLink(true)), true);
        ReloadCommand.reloadPacks(list.stream().map(Pack::getId).collect(Collectors.toList()), source);
        return list.size();
    }

    private static int disablePack(CommandSourceStack source, Pack pack) {
        PackRepository packRepository = source.getServer().getPackRepository();
        List<Pack> list = Lists.newArrayList(packRepository.getSelectedPacks());
        list.remove(pack);
        source.sendSuccess(() -> Component.translatable("commands.datapack.modify.disable", pack.getChatLink(true)), true);
        ReloadCommand.reloadPacks(list.stream().map(Pack::getId).collect(Collectors.toList()), source);
        return list.size();
    }

    private static int listPacks(CommandSourceStack source) {
        return listEnabledPacks(source) + listAvailablePacks(source);
    }

    private static int listAvailablePacks(CommandSourceStack source) {
        PackRepository packRepository = source.getServer().getPackRepository();
        packRepository.reload();
        Collection<Pack> selectedPacks = packRepository.getSelectedPacks();
        Collection<Pack> availablePacks = packRepository.getAvailablePacks();
        FeatureFlagSet featureFlagSet = source.enabledFeatures();
        List<Pack> list = availablePacks.stream()
            .filter(pack -> !selectedPacks.contains(pack) && pack.getRequestedFeatures().isSubsetOf(featureFlagSet))
            .toList();
        if (list.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.datapack.list.available.none"), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.datapack.list.available.success", list.size(), ComponentUtils.formatList(list, pack -> pack.getChatLink(false))
                ),
                false
            );
        }

        return list.size();
    }

    private static int listEnabledPacks(CommandSourceStack source) {
        PackRepository packRepository = source.getServer().getPackRepository();
        packRepository.reload();
        Collection<? extends Pack> selectedPacks = packRepository.getSelectedPacks();
        if (selectedPacks.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.datapack.list.enabled.none"), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.datapack.list.enabled.success", selectedPacks.size(), ComponentUtils.formatList(selectedPacks, pack -> pack.getChatLink(true))
                ),
                false
            );
        }

        return selectedPacks.size();
    }

    private static Pack getPack(CommandContext<CommandSourceStack> context, String name, boolean enabling) throws CommandSyntaxException {
        String string = StringArgumentType.getString(context, name);
        PackRepository packRepository = context.getSource().getServer().getPackRepository();
        Pack pack = packRepository.getPack(string);
        if (pack == null) {
            throw ERROR_UNKNOWN_PACK.create(string);
        } else {
            boolean flag = packRepository.getSelectedPacks().contains(pack);
            if (enabling && flag) {
                throw ERROR_PACK_ALREADY_ENABLED.create(string);
            } else if (!enabling && !flag) {
                throw ERROR_PACK_ALREADY_DISABLED.create(string);
            } else {
                FeatureFlagSet featureFlagSet = context.getSource().enabledFeatures();
                FeatureFlagSet requestedFeatures = pack.getRequestedFeatures();
                if (!enabling && !requestedFeatures.isEmpty() && pack.getPackSource() == PackSource.FEATURE) {
                    throw ERROR_CANNOT_DISABLE_FEATURE.create(string);
                } else if (!requestedFeatures.isSubsetOf(featureFlagSet)) {
                    throw ERROR_PACK_FEATURES_NOT_ENABLED.create(string, FeatureFlags.printMissingFlags(featureFlagSet, requestedFeatures));
                } else {
                    return pack;
                }
            }
        }
    }

    interface Inserter {
        void apply(List<Pack> currentPacks, Pack pack) throws CommandSyntaxException;
    }
}
