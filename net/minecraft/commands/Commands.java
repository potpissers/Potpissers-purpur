package net.minecraft.commands;

import com.google.common.collect.Maps;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.gametest.framework.TestCommand;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.AdvancementCommands;
import net.minecraft.server.commands.AttributeCommand;
import net.minecraft.server.commands.BanIpCommands;
import net.minecraft.server.commands.BanListCommands;
import net.minecraft.server.commands.BanPlayerCommands;
import net.minecraft.server.commands.BossBarCommands;
import net.minecraft.server.commands.ClearInventoryCommands;
import net.minecraft.server.commands.CloneCommands;
import net.minecraft.server.commands.DamageCommand;
import net.minecraft.server.commands.DataPackCommand;
import net.minecraft.server.commands.DeOpCommands;
import net.minecraft.server.commands.DebugCommand;
import net.minecraft.server.commands.DebugConfigCommand;
import net.minecraft.server.commands.DebugMobSpawningCommand;
import net.minecraft.server.commands.DebugPathCommand;
import net.minecraft.server.commands.DefaultGameModeCommands;
import net.minecraft.server.commands.DifficultyCommand;
import net.minecraft.server.commands.EffectCommands;
import net.minecraft.server.commands.EmoteCommands;
import net.minecraft.server.commands.EnchantCommand;
import net.minecraft.server.commands.ExecuteCommand;
import net.minecraft.server.commands.ExperienceCommand;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.commands.FillCommand;
import net.minecraft.server.commands.ForceLoadCommand;
import net.minecraft.server.commands.FunctionCommand;
import net.minecraft.server.commands.GameModeCommand;
import net.minecraft.server.commands.GameRuleCommand;
import net.minecraft.server.commands.GiveCommand;
import net.minecraft.server.commands.HelpCommand;
import net.minecraft.server.commands.ItemCommands;
import net.minecraft.server.commands.JfrCommand;
import net.minecraft.server.commands.KickCommand;
import net.minecraft.server.commands.KillCommand;
import net.minecraft.server.commands.ListPlayersCommand;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.server.commands.LootCommand;
import net.minecraft.server.commands.MsgCommand;
import net.minecraft.server.commands.OpCommand;
import net.minecraft.server.commands.PardonCommand;
import net.minecraft.server.commands.PardonIpCommand;
import net.minecraft.server.commands.ParticleCommand;
import net.minecraft.server.commands.PerfCommand;
import net.minecraft.server.commands.PlaceCommand;
import net.minecraft.server.commands.PlaySoundCommand;
import net.minecraft.server.commands.PublishCommand;
import net.minecraft.server.commands.RaidCommand;
import net.minecraft.server.commands.RandomCommand;
import net.minecraft.server.commands.RecipeCommand;
import net.minecraft.server.commands.ReloadCommand;
import net.minecraft.server.commands.ReturnCommand;
import net.minecraft.server.commands.RideCommand;
import net.minecraft.server.commands.RotateCommand;
import net.minecraft.server.commands.SaveAllCommand;
import net.minecraft.server.commands.SaveOffCommand;
import net.minecraft.server.commands.SaveOnCommand;
import net.minecraft.server.commands.SayCommand;
import net.minecraft.server.commands.ScheduleCommand;
import net.minecraft.server.commands.ScoreboardCommand;
import net.minecraft.server.commands.SeedCommand;
import net.minecraft.server.commands.ServerPackCommand;
import net.minecraft.server.commands.SetBlockCommand;
import net.minecraft.server.commands.SetPlayerIdleTimeoutCommand;
import net.minecraft.server.commands.SetSpawnCommand;
import net.minecraft.server.commands.SetWorldSpawnCommand;
import net.minecraft.server.commands.SpawnArmorTrimsCommand;
import net.minecraft.server.commands.SpectateCommand;
import net.minecraft.server.commands.SpreadPlayersCommand;
import net.minecraft.server.commands.StopCommand;
import net.minecraft.server.commands.StopSoundCommand;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.server.commands.TagCommand;
import net.minecraft.server.commands.TeamCommand;
import net.minecraft.server.commands.TeamMsgCommand;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.commands.TellRawCommand;
import net.minecraft.server.commands.TickCommand;
import net.minecraft.server.commands.TimeCommand;
import net.minecraft.server.commands.TitleCommand;
import net.minecraft.server.commands.TransferCommand;
import net.minecraft.server.commands.TriggerCommand;
import net.minecraft.server.commands.WardenSpawnTrackerCommand;
import net.minecraft.server.commands.WeatherCommand;
import net.minecraft.server.commands.WhitelistCommand;
import net.minecraft.server.commands.WorldBorderCommand;
import net.minecraft.server.commands.data.DataCommands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameRules;
import org.slf4j.Logger;

public class Commands {
    private static final ThreadLocal<ExecutionContext<CommandSourceStack>> CURRENT_EXECUTION_CONTEXT = new ThreadLocal<>();
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int LEVEL_ALL = 0;
    public static final int LEVEL_MODERATORS = 1;
    public static final int LEVEL_GAMEMASTERS = 2;
    public static final int LEVEL_ADMINS = 3;
    public static final int LEVEL_OWNERS = 4;
    private final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

    public Commands(Commands.CommandSelection selection, CommandBuildContext context) {
        AdvancementCommands.register(this.dispatcher);
        AttributeCommand.register(this.dispatcher, context);
        ExecuteCommand.register(this.dispatcher, context);
        BossBarCommands.register(this.dispatcher, context);
        ClearInventoryCommands.register(this.dispatcher, context);
        CloneCommands.register(this.dispatcher, context);
        DamageCommand.register(this.dispatcher, context);
        DataCommands.register(this.dispatcher);
        DataPackCommand.register(this.dispatcher);
        DebugCommand.register(this.dispatcher);
        DefaultGameModeCommands.register(this.dispatcher);
        DifficultyCommand.register(this.dispatcher);
        EffectCommands.register(this.dispatcher, context);
        EmoteCommands.register(this.dispatcher);
        EnchantCommand.register(this.dispatcher, context);
        ExperienceCommand.register(this.dispatcher);
        FillCommand.register(this.dispatcher, context);
        FillBiomeCommand.register(this.dispatcher, context);
        ForceLoadCommand.register(this.dispatcher);
        FunctionCommand.register(this.dispatcher);
        GameModeCommand.register(this.dispatcher);
        GameRuleCommand.register(this.dispatcher, context);
        GiveCommand.register(this.dispatcher, context);
        HelpCommand.register(this.dispatcher);
        ItemCommands.register(this.dispatcher, context);
        KickCommand.register(this.dispatcher);
        KillCommand.register(this.dispatcher);
        ListPlayersCommand.register(this.dispatcher);
        LocateCommand.register(this.dispatcher, context);
        LootCommand.register(this.dispatcher, context);
        MsgCommand.register(this.dispatcher);
        ParticleCommand.register(this.dispatcher, context);
        PlaceCommand.register(this.dispatcher);
        PlaySoundCommand.register(this.dispatcher);
        RandomCommand.register(this.dispatcher);
        ReloadCommand.register(this.dispatcher);
        RecipeCommand.register(this.dispatcher);
        ReturnCommand.register(this.dispatcher);
        RideCommand.register(this.dispatcher);
        RotateCommand.register(this.dispatcher);
        SayCommand.register(this.dispatcher);
        ScheduleCommand.register(this.dispatcher);
        ScoreboardCommand.register(this.dispatcher, context);
        SeedCommand.register(this.dispatcher, selection != Commands.CommandSelection.INTEGRATED);
        SetBlockCommand.register(this.dispatcher, context);
        SetSpawnCommand.register(this.dispatcher);
        SetWorldSpawnCommand.register(this.dispatcher);
        SpectateCommand.register(this.dispatcher);
        SpreadPlayersCommand.register(this.dispatcher);
        StopSoundCommand.register(this.dispatcher);
        SummonCommand.register(this.dispatcher, context);
        TagCommand.register(this.dispatcher);
        TeamCommand.register(this.dispatcher, context);
        TeamMsgCommand.register(this.dispatcher);
        TeleportCommand.register(this.dispatcher);
        TellRawCommand.register(this.dispatcher, context);
        TickCommand.register(this.dispatcher);
        TimeCommand.register(this.dispatcher);
        TitleCommand.register(this.dispatcher, context);
        TriggerCommand.register(this.dispatcher);
        WeatherCommand.register(this.dispatcher);
        WorldBorderCommand.register(this.dispatcher);
        if (JvmProfiler.INSTANCE.isAvailable()) {
            JfrCommand.register(this.dispatcher);
        }

        if (org.purpurmc.purpur.PurpurConfig.registerMinecraftDebugCommands || SharedConstants.IS_RUNNING_IN_IDE) { // Purpur - register minecraft debug commands
            if (!org.purpurmc.purpur.PurpurConfig.registerMinecraftDebugCommands) TestCommand.register(this.dispatcher); // Purpur - register minecraft debug commands
            RaidCommand.register(this.dispatcher, context);
            DebugPathCommand.register(this.dispatcher);
            DebugMobSpawningCommand.register(this.dispatcher);
            WardenSpawnTrackerCommand.register(this.dispatcher);
            SpawnArmorTrimsCommand.register(this.dispatcher);
            ServerPackCommand.register(this.dispatcher);
            if (selection.includeDedicated) {
                DebugConfigCommand.register(this.dispatcher);
            }
        }

        if (selection.includeDedicated) {
            BanIpCommands.register(this.dispatcher);
            BanListCommands.register(this.dispatcher);
            BanPlayerCommands.register(this.dispatcher);
            DeOpCommands.register(this.dispatcher);
            OpCommand.register(this.dispatcher);
            PardonCommand.register(this.dispatcher);
            PardonIpCommand.register(this.dispatcher);
            PerfCommand.register(this.dispatcher);
            SaveAllCommand.register(this.dispatcher);
            SaveOffCommand.register(this.dispatcher);
            SaveOnCommand.register(this.dispatcher);
            SetPlayerIdleTimeoutCommand.register(this.dispatcher);
            StopCommand.register(this.dispatcher);
            TransferCommand.register(this.dispatcher);
            WhitelistCommand.register(this.dispatcher);
            org.purpurmc.purpur.command.CreditsCommand.register(this.dispatcher); // Purpur - Add credits command
            org.purpurmc.purpur.command.DemoCommand.register(this.dispatcher); // Purpur - Add demo command
            org.purpurmc.purpur.command.PingCommand.register(this.dispatcher); // Purpur - Add ping command
            org.purpurmc.purpur.command.UptimeCommand.register(this.dispatcher); // Purpur - Add uptime command
            org.purpurmc.purpur.command.TPSBarCommand.register(this.dispatcher); // Purpur - Implement TPSBar
            org.purpurmc.purpur.command.CompassCommand.register(this.dispatcher); // Purpur - Add compass command
            org.purpurmc.purpur.command.RamBarCommand.register(this.dispatcher); // Purpur - Add rambar command
            org.purpurmc.purpur.command.RamCommand.register(this.dispatcher); // Purpur - Add ram command
        }

        if (selection.includeIntegrated) {
            PublishCommand.register(this.dispatcher);
        }

        // Paper start - Vanilla command permission fixes
        for (final CommandNode<CommandSourceStack> node : this.dispatcher.getRoot().getChildren()) {
            if (node.getRequirement() == com.mojang.brigadier.builder.ArgumentBuilder.<CommandSourceStack>defaultRequirement()) {
                node.requirement = stack -> stack.source == CommandSource.NULL || stack.getBukkitSender().hasPermission(org.bukkit.craftbukkit.command.VanillaCommandWrapper.getPermission(node));
            }
        }
        // Paper end - Vanilla command permission fixes
        // Paper start - Brigadier Command API
        // Create legacy minecraft namespace commands
        for (final CommandNode<CommandSourceStack> node : new java.util.ArrayList<>(this.dispatcher.getRoot().getChildren())) {
            // The brigadier dispatcher is not able to resolve nested redirects.
            // E.g. registering the alias minecraft:tp cannot redirect to tp, as tp itself redirects to teleport.
            // Instead, target the first none redirecting node.
            CommandNode<CommandSourceStack> flattenedAliasTarget = node;
            while (flattenedAliasTarget.getRedirect() != null) flattenedAliasTarget = flattenedAliasTarget.getRedirect();

            this.dispatcher.register(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.<CommandSourceStack>literal("minecraft:" + node.getName())
                    .executes(flattenedAliasTarget.getCommand())
                    .requires(flattenedAliasTarget.getRequirement())
                    .redirect(flattenedAliasTarget)
            );
        }
        // Paper end - Brigadier Command API
        this.dispatcher.setConsumer(ExecutionCommandSource.resultConsumer());
    }

    public static <S> ParseResults<S> mapSource(ParseResults<S> parseResults, UnaryOperator<S> mapper) {
        CommandContextBuilder<S> context = parseResults.getContext();
        CommandContextBuilder<S> commandContextBuilder = context.withSource(mapper.apply(context.getSource()));
        return new ParseResults<>(commandContextBuilder, parseResults.getReader(), parseResults.getExceptions());
    }

    // CraftBukkit start
    public void dispatchServerCommand(CommandSourceStack sender, String command) {
        com.google.common.base.Joiner joiner = com.google.common.base.Joiner.on(" ");
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        org.bukkit.event.server.ServerCommandEvent event = new org.bukkit.event.server.ServerCommandEvent(sender.getBukkitSender(), command);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        command = event.getCommand();

        String[] args = command.split(" ");
        if (args.length == 0) return; // Paper - empty commands shall not be dispatched

        // Paper - Fix permission levels for command blocks

        // Handle vanilla commands; // Paper - handled in CommandNode/CommandDispatcher

        String newCommand = joiner.join(args);
        this.performPrefixedCommand(sender, newCommand, newCommand);
    }
    // CraftBukkit end

    public void performPrefixedCommand(CommandSourceStack source, String command) {
        // CraftBukkit start
        this.performPrefixedCommand(source, command, command);
    }

    public void performPrefixedCommand(CommandSourceStack source, String command, String label) {
        command = command.startsWith("/") ? command.substring(1) : command;
        this.performCommand(this.dispatcher.parse(command, source), command, label);
        // CraftBukkit end
    }

    public void performCommand(ParseResults<CommandSourceStack> parseResults, String command) {
        // CraftBukkit start
        this.performCommand(parseResults, command, command);
    }

    public void performCommand(ParseResults<CommandSourceStack> parseResults, String command, String label) {
        // CraftBukkit end
        // Paper start
        this.performCommand(parseResults, command, label, false);
    }
    public void performCommand(ParseResults<CommandSourceStack> parseResults, String command, String label, boolean throwCommandError) {
        // Paper end
        CommandSourceStack commandSourceStack = parseResults.getContext().getSource();
        Profiler.get().push(() -> "/" + command);
        ContextChain contextChain = this.finishParsing(parseResults, command, commandSourceStack, label); // CraftBukkit // Paper - Add UnknownCommandEvent

        try {
            if (contextChain != null) {
                executeCommandInContext(
                    commandSourceStack,
                    executionContext -> ExecutionContext.queueInitialCommandExecution(
                        executionContext, command, contextChain, commandSourceStack, CommandResultCallback.EMPTY
                    )
                );
            }
        } catch (Exception var12) {
            if (throwCommandError) throw var12; // Paper
            MutableComponent mutableComponent = Component.literal(var12.getMessage() == null ? var12.getClass().getName() : var12.getMessage());
            LOGGER.error("Command exception: /{}", command, var12); // Paper - always show execution exception in console log
            if (commandSourceStack.getServer().isDebugging() || LOGGER.isDebugEnabled()) { // Paper - Debugging
                StackTraceElement[] stackTrace = var12.getStackTrace();

                for (int i = 0; i < Math.min(stackTrace.length, 3); i++) {
                    mutableComponent.append("\n\n")
                        .append(stackTrace[i].getMethodName())
                        .append("\n ")
                        .append(stackTrace[i].getFileName())
                        .append(":")
                        .append(String.valueOf(stackTrace[i].getLineNumber()));
                }
            }

            commandSourceStack.sendFailure(
                Component.translatable("command.failed")
                    .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, mutableComponent)))
            );
            if (SharedConstants.IS_RUNNING_IN_IDE) {
                commandSourceStack.sendFailure(Component.literal(Util.describeError(var12)));
                LOGGER.error("'/{}' threw an exception", command, var12);
            }
        } finally {
            Profiler.get().pop();
        }
    }

    @Nullable
    private ContextChain<CommandSourceStack> finishParsing(ParseResults<CommandSourceStack> parseResults, String command, CommandSourceStack source, String label) { // CraftBukkit // Paper - Add UnknownCommandEvent
        try {
            validateParseResults(parseResults);
            return ContextChain.tryFlatten(parseResults.getContext().build(command))
                .orElseThrow(() -> CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parseResults.getReader()));
        } catch (CommandSyntaxException var7) {
            // Paper start - Add UnknownCommandEvent
            final net.kyori.adventure.text.TextComponent.Builder builder = net.kyori.adventure.text.Component.text();
            // source.sendFailure(ComponentUtils.fromMessage(var7.getRawMessage()));
            builder.color(net.kyori.adventure.text.format.NamedTextColor.RED).append(io.papermc.paper.command.brigadier.MessageComponentSerializer.message().deserialize(var7.getRawMessage()));
            // Paper end - Add UnknownCommandEvent
            if (var7.getInput() != null && var7.getCursor() >= 0) {
                int min = Math.min(var7.getInput().length(), var7.getCursor());
                MutableComponent mutableComponent = Component.empty()
                    .withStyle(ChatFormatting.GRAY)
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/" + label))); // CraftBukkit // Paper
                if (min > 10) {
                    mutableComponent.append(CommonComponents.ELLIPSIS);
                }

                mutableComponent.append(var7.getInput().substring(Math.max(0, min - 10), min));
                if (min < var7.getInput().length()) {
                    Component component = Component.literal(var7.getInput().substring(min)).withStyle(ChatFormatting.RED, ChatFormatting.UNDERLINE);
                    mutableComponent.append(component);
                }

                mutableComponent.append(Component.translatable("command.context.here").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
                // Paper start - Add UnknownCommandEvent
                // source.sendFailure(mutableComponent);
                builder
                    .append(net.kyori.adventure.text.Component.newline())
                    .append(io.papermc.paper.adventure.PaperAdventure.asAdventure(mutableComponent));
            }
            org.bukkit.event.command.UnknownCommandEvent event = new org.bukkit.event.command.UnknownCommandEvent(source.getBukkitSender(), command, org.spigotmc.SpigotConfig.unknownCommandMessage.isEmpty() ? null : builder.build());
            org.bukkit.Bukkit.getServer().getPluginManager().callEvent(event);
            if (event.message() != null) {
                source.sendFailure(io.papermc.paper.adventure.PaperAdventure.asVanilla(event.message()), false);
                // Paper end - Add UnknownCommandEvent
            }

            return null;
        }
    }

    public static void executeCommandInContext(CommandSourceStack source, Consumer<ExecutionContext<CommandSourceStack>> contextConsumer) {
        MinecraftServer server = source.getServer();
        ExecutionContext<CommandSourceStack> executionContext = CURRENT_EXECUTION_CONTEXT.get();
        boolean flag = executionContext == null;
        if (flag) {
            int max = Math.max(1, server.getGameRules().getInt(GameRules.RULE_MAX_COMMAND_CHAIN_LENGTH));
            int _int = server.getGameRules().getInt(GameRules.RULE_MAX_COMMAND_FORK_COUNT);

            try (ExecutionContext<CommandSourceStack> executionContext1 = new ExecutionContext<>(max, _int, Profiler.get())) {
                CURRENT_EXECUTION_CONTEXT.set(executionContext1);
                contextConsumer.accept(executionContext1);
                executionContext1.runCommandQueue();
            } finally {
                CURRENT_EXECUTION_CONTEXT.set(null);
            }
        } else {
            contextConsumer.accept(executionContext);
        }
    }

    public void sendCommands(ServerPlayer player) {
        // Paper start - Send empty commands if tab completion is disabled
        if (org.spigotmc.SpigotConfig.tabComplete < 0) {
            player.connection.send(new ClientboundCommandsPacket(new RootCommandNode<>()));
            return;
        }
        // Paper end - Send empty commands if tab completion is disabled
        // CraftBukkit start
        // Register Vanilla commands into builtRoot as before
        // Paper start - Perf: Async command map building
        // Copy root children to avoid concurrent modification during building
        final java.util.Collection<CommandNode<CommandSourceStack>> commandNodes = new java.util.ArrayList<>(this.dispatcher.getRoot().getChildren());
        COMMAND_SENDING_POOL.execute(() -> this.sendAsync(player, commandNodes));
    }

    // Fixed pool, but with discard policy
    public static final java.util.concurrent.ExecutorService COMMAND_SENDING_POOL = new java.util.concurrent.ThreadPoolExecutor(
        2, 2, 0, java.util.concurrent.TimeUnit.MILLISECONDS,
        new java.util.concurrent.LinkedBlockingQueue<>(),
        new com.google.common.util.concurrent.ThreadFactoryBuilder()
            .setNameFormat("Paper Async Command Builder Thread Pool - %1$d")
            .setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(net.minecraft.server.MinecraftServer.LOGGER))
            .build(),
        new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy()
    );

    private void sendAsync(ServerPlayer player, java.util.Collection<CommandNode<CommandSourceStack>> dispatcherRootChildren) {
        // Paper end - Perf: Async command map building
        Map<CommandNode<CommandSourceStack>, CommandNode<SharedSuggestionProvider>> map = Maps.newIdentityHashMap(); // Use identity to prevent aliasing issues
        RootCommandNode<SharedSuggestionProvider> rootCommandNode = new RootCommandNode<>();
        map.put(this.dispatcher.getRoot(), rootCommandNode);
        this.fillUsableCommands(dispatcherRootChildren, rootCommandNode, player.createCommandSourceStack(), map); // Paper - Perf: Async command map building; pass copy of children

        java.util.Collection<String> bukkit = new java.util.LinkedHashSet<>();
        for (CommandNode node : rootCommandNode.getChildren()) {
            bukkit.add(node.getName());
        }
        // Paper start - Perf: Async command map building
        new com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent<CommandSourceStack>(player.getBukkitEntity(), (RootCommandNode) rootCommandNode, false).callEvent(); // Paper - Brigadier API
        net.minecraft.server.MinecraftServer.getServer().execute(() -> {
           runSync(player, bukkit, rootCommandNode);
        });
    }

    private void runSync(ServerPlayer player, java.util.Collection<String> bukkit, RootCommandNode<SharedSuggestionProvider> rootCommandNode) {
        // Paper end - Perf: Async command map building
        new com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent<CommandSourceStack>(player.getBukkitEntity(), (RootCommandNode) rootCommandNode, true).callEvent(); // Paper - Brigadier API
        if (org.bukkit.event.player.PlayerCommandSendEvent.getHandlerList().getRegisteredListeners().length > 0) { // Purpur - Skip events if there's no listeners
        org.bukkit.event.player.PlayerCommandSendEvent event = new org.bukkit.event.player.PlayerCommandSendEvent(player.getBukkitEntity(), new java.util.LinkedHashSet<>(bukkit));
        event.getPlayer().getServer().getPluginManager().callEvent(event);

        // Remove labels that were removed during the event
        for (String orig : bukkit) {
            if (!event.getCommands().contains(orig)) {
                rootCommandNode.removeCommand(orig);
            }
        }
        // CraftBukkit end
        } // Purpur - Skip events if there's no listeners

        player.connection.send(new ClientboundCommandsPacket(rootCommandNode));
    }

    private void fillUsableCommands(
        java.util.Collection<CommandNode<CommandSourceStack>> children, // Paper - Perf: Async command map building; pass copy of children
        CommandNode<SharedSuggestionProvider> rootSuggestion,
        CommandSourceStack source,
        Map<CommandNode<CommandSourceStack>, CommandNode<SharedSuggestionProvider>> commandNodeToSuggestionNode
    ) {
        commandNodeToSuggestionNode.keySet().removeIf((node) -> !org.spigotmc.SpigotConfig.sendNamespaced && node.getName().contains(":")); // Paper - Remove namedspaced from result nodes to prevent redirect trimming ~ see comment below
        for (CommandNode<CommandSourceStack> commandNode : children) { // Paper - Perf: Async command map building; pass copy of children
            // Paper start - Brigadier API
            if (commandNode.clientNode != null) {
                commandNode = commandNode.clientNode;
            }
            // Paper end - Brigadier API
            if (!org.spigotmc.SpigotConfig.sendNamespaced && commandNode.getName().contains(":")) continue; // Spigot
            if (commandNode.canUse(source)) {
                ArgumentBuilder<SharedSuggestionProvider, ?> argumentBuilder = (ArgumentBuilder) commandNode.createBuilder();
                // Paper start
                /*
                Because of how commands can be yeeted right left and center due to bad bukkit practices
                we need to be able to ensure that ALL commands are registered (even redirects).

                What this will do is IF the redirect seems to be "dead" it will create a builder and essentially populate (flatten)
                all the children from the dead redirect to the node.

                So, if minecraft:msg redirects to msg but the original msg node has been overriden minecraft:msg will now act as msg and will explicilty inherit its children.

                The only way to fix this is to either:
                - Send EVERYTHING flattened, don't use redirects
                - Don't allow command nodes to be deleted
                - Do this :)
                 */

                // Is there an invalid command redirect?
                if (argumentBuilder.getRedirect() != null && commandNodeToSuggestionNode.get(argumentBuilder.getRedirect()) == null) {
                    // Create the argument builder with the same values as the specified node, but with a different literal and populated children

                    CommandNode<SharedSuggestionProvider> redirect = argumentBuilder.getRedirect();
                    // Diff copied from LiteralCommand#createBuilder
                    final com.mojang.brigadier.builder.LiteralArgumentBuilder<SharedSuggestionProvider> builder = com.mojang.brigadier.builder.LiteralArgumentBuilder.literal(commandNode.getName());
                    builder.requires(redirect.getRequirement());
                    // builder.forward(redirect.getRedirect(), redirect.getRedirectModifier(), redirect.isFork()); We don't want to migrate the forward, since it's invalid.
                    if (redirect.getCommand() != null) {
                        builder.executes(redirect.getCommand());
                    }
                    // Diff copied from LiteralCommand#createBuilder
                    for (CommandNode<SharedSuggestionProvider> child : redirect.getChildren()) {
                        builder.then(child);
                    }

                    argumentBuilder = builder;
                }
                // Paper end
                argumentBuilder.requires(suggestions -> true);
                if (argumentBuilder.getCommand() != null) {
                    // Paper start - fix suggestions due to falsely equal nodes
                    // Always create a new instance
                    //noinspection Convert2Lambda
                    argumentBuilder.executes(new com.mojang.brigadier.Command<>() {
                        @Override
                        public int run(com.mojang.brigadier.context.CommandContext<SharedSuggestionProvider> commandContext) {
                            return 0;
                        }
                    });
                    // Paper end - fix suggestions due to falsely equal nodes
                }

                if (argumentBuilder instanceof RequiredArgumentBuilder) {
                    RequiredArgumentBuilder<SharedSuggestionProvider, ?> requiredArgumentBuilder = (RequiredArgumentBuilder<SharedSuggestionProvider, ?>)argumentBuilder;
                    if (requiredArgumentBuilder.getSuggestionsProvider() != null) {
                        requiredArgumentBuilder.suggests(SuggestionProviders.safelySwap(requiredArgumentBuilder.getSuggestionsProvider()));
                    }
                }

                if (argumentBuilder.getRedirect() != null) {
                    argumentBuilder.redirect(commandNodeToSuggestionNode.get(argumentBuilder.getRedirect()));
                }

                CommandNode<SharedSuggestionProvider> commandNode1 = argumentBuilder.build();
                commandNodeToSuggestionNode.put(commandNode, commandNode1);
                rootSuggestion.addChild(commandNode1);
                if (!commandNode.getChildren().isEmpty()) {
                    this.fillUsableCommands(commandNode.getChildren(), commandNode1, source, commandNodeToSuggestionNode); // Paper - Perf: Async command map building; pass copy of children
                }
            }
        }
    }

    public static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    public static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    public static Predicate<String> createValidator(Commands.ParseFunction parser) {
        return contents -> {
            try {
                parser.parse(new StringReader(contents));
                return true;
            } catch (CommandSyntaxException var3) {
                return false;
            }
        };
    }

    public CommandDispatcher<CommandSourceStack> getDispatcher() {
        return this.dispatcher;
    }

    public static <S> void validateParseResults(ParseResults<S> parseResults) throws CommandSyntaxException {
        CommandSyntaxException parseException = getParseException(parseResults);
        if (parseException != null) {
            throw parseException;
        }
    }

    @Nullable
    public static <S> CommandSyntaxException getParseException(ParseResults<S> result) {
        if (!result.getReader().canRead()) {
            return null;
        } else if (result.getExceptions().size() == 1) {
            return result.getExceptions().values().iterator().next();
        } else {
            return result.getContext().getRange().isEmpty()
                ? CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(result.getReader())
                : CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(result.getReader());
        }
    }

    public static CommandBuildContext createValidationContext(final HolderLookup.Provider provider) {
        return new CommandBuildContext() {
            @Override
            public FeatureFlagSet enabledFeatures() {
                return FeatureFlags.REGISTRY.allFlags();
            }

            @Override
            public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
                return provider.listRegistryKeys();
            }

            @Override
            public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey) {
                return provider.lookup(registryKey).map(this::createLookup);
            }

            private <T> HolderLookup.RegistryLookup.Delegate<T> createLookup(final HolderLookup.RegistryLookup<T> registryLookup) {
                return new HolderLookup.RegistryLookup.Delegate<T>() {
                    @Override
                    public HolderLookup.RegistryLookup<T> parent() {
                        return registryLookup;
                    }

                    @Override
                    public Optional<HolderSet.Named<T>> get(TagKey<T> tagKey) {
                        return Optional.of(this.getOrThrow(tagKey));
                    }

                    @Override
                    public HolderSet.Named<T> getOrThrow(TagKey<T> tagKey) {
                        Optional<HolderSet.Named<T>> optional = this.parent().get(tagKey);
                        return optional.orElseGet(() -> HolderSet.emptyNamed(this.parent(), tagKey));
                    }
                };
            }
        };
    }

    public static void validate() {
        CommandBuildContext commandBuildContext = createValidationContext(VanillaRegistries.createLookup());
        CommandDispatcher<CommandSourceStack> dispatcher = new Commands(Commands.CommandSelection.ALL, commandBuildContext).getDispatcher();
        RootCommandNode<CommandSourceStack> root = dispatcher.getRoot();
        dispatcher.findAmbiguities(
            (commandNode, commandNode1, commandNode2, collection) -> LOGGER.warn(
                "Ambiguity between arguments {} and {} with inputs: {}", dispatcher.getPath(commandNode1), dispatcher.getPath(commandNode2), collection
            )
        );
        Set<ArgumentType<?>> set = ArgumentUtils.findUsedArgumentTypes(root);
        Set<ArgumentType<?>> set1 = set.stream()
            .filter(argumentType -> !ArgumentTypeInfos.isClassRecognized(argumentType.getClass()))
            .collect(Collectors.toSet());
        if (!set1.isEmpty()) {
            LOGGER.warn(
                "Missing type registration for following arguments:\n {}",
                set1.stream().map(argumentType -> "\t" + argumentType).collect(Collectors.joining(",\n"))
            );
            throw new IllegalStateException("Unregistered argument types");
        }
    }

    public static enum CommandSelection {
        ALL(true, true),
        DEDICATED(false, true),
        INTEGRATED(true, false);

        final boolean includeIntegrated;
        final boolean includeDedicated;

        private CommandSelection(final boolean includeIntegrated, final boolean includeDedicated) {
            this.includeIntegrated = includeIntegrated;
            this.includeDedicated = includeDedicated;
        }
    }

    @FunctionalInterface
    public interface ParseFunction {
        void parse(StringReader input) throws CommandSyntaxException;
    }
}
