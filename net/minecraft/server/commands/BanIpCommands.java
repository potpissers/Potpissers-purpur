package net.minecraft.server.commands;

import com.google.common.net.InetAddresses;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanList;
import net.minecraft.server.players.IpBanListEntry;

public class BanIpCommands {
    private static final SimpleCommandExceptionType ERROR_INVALID_IP = new SimpleCommandExceptionType(Component.translatable("commands.banip.invalid"));
    private static final SimpleCommandExceptionType ERROR_ALREADY_BANNED = new SimpleCommandExceptionType(Component.translatable("commands.banip.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ban-ip")
                .requires(source -> source.hasPermission(3))
                .then(
                    Commands.argument("target", StringArgumentType.word())
                        .executes(context -> banIpOrName(context.getSource(), StringArgumentType.getString(context, "target"), null))
                        .then(
                            Commands.argument("reason", MessageArgument.message())
                                .executes(
                                    context -> banIpOrName(
                                        context.getSource(), StringArgumentType.getString(context, "target"), MessageArgument.getMessage(context, "reason")
                                    )
                                )
                        )
                )
        );
    }

    private static int banIpOrName(CommandSourceStack source, String username, @Nullable Component reason) throws CommandSyntaxException {
        if (InetAddresses.isInetAddress(username)) {
            return banIp(source, username, reason);
        } else {
            ServerPlayer playerByName = source.getServer().getPlayerList().getPlayerByName(username);
            if (playerByName != null) {
                return banIp(source, playerByName.getIpAddress(), reason);
            } else {
                throw ERROR_INVALID_IP.create();
            }
        }
    }

    private static int banIp(CommandSourceStack source, String ip, @Nullable Component reason) throws CommandSyntaxException {
        IpBanList ipBans = source.getServer().getPlayerList().getIpBans();
        if (ipBans.isBanned(ip)) {
            throw ERROR_ALREADY_BANNED.create();
        } else {
            List<ServerPlayer> playersWithAddress = source.getServer().getPlayerList().getPlayersWithAddress(ip);
            IpBanListEntry ipBanListEntry = new IpBanListEntry(ip, null, source.getTextName(), null, reason == null ? null : reason.getString());
            ipBans.add(ipBanListEntry);
            source.sendSuccess(() -> Component.translatable("commands.banip.success", ip, ipBanListEntry.getReason()), true);
            if (!playersWithAddress.isEmpty()) {
                source.sendSuccess(
                    () -> Component.translatable("commands.banip.info", playersWithAddress.size(), EntitySelector.joinNames(playersWithAddress)), true
                );
            }

            for (ServerPlayer serverPlayer : playersWithAddress) {
                serverPlayer.connection.disconnect(Component.translatable("multiplayer.disconnect.ip_banned"));
            }

            return playersWithAddress.size();
        }
    }
}
