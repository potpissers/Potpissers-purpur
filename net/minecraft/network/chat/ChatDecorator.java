package net.minecraft.network.chat;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface ChatDecorator {
    ChatDecorator PLAIN = (player, message) -> java.util.concurrent.CompletableFuture.completedFuture(message); // Paper - adventure; support async chat decoration events

    @io.papermc.paper.annotation.DoNotUse @Deprecated // Paper - adventure; support chat decoration events (callers should use the overload with CommandSourceStack)
    java.util.concurrent.CompletableFuture<Component> decorate(@Nullable ServerPlayer player, Component message); // Paper - adventure; support async chat decoration events

    // Paper start - adventure; support async chat decoration events
    default java.util.concurrent.CompletableFuture<Component> decorate(@Nullable ServerPlayer sender, @Nullable net.minecraft.commands.CommandSourceStack commandSourceStack, Component message) {
        throw new UnsupportedOperationException("Must override this implementation");
    }
    // Paper end - adventure; support async chat decoration events
}
