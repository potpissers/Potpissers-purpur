package net.minecraft.network.chat;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface ChatDecorator {
    ChatDecorator PLAIN = (player, message) -> message;

    Component decorate(@Nullable ServerPlayer player, Component message);
}
