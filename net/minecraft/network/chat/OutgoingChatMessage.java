package net.minecraft.network.chat;

import net.minecraft.server.level.ServerPlayer;

public interface OutgoingChatMessage {
    Component content();

    void sendToPlayer(ServerPlayer player, boolean filtered, ChatType.Bound boundType);

    static OutgoingChatMessage create(PlayerChatMessage message) {
        return (OutgoingChatMessage)(message.isSystem()
            ? new OutgoingChatMessage.Disguised(message.decoratedContent())
            : new OutgoingChatMessage.Player(message));
    }

    public record Disguised(@Override Component content) implements OutgoingChatMessage {
        @Override
        public void sendToPlayer(ServerPlayer player, boolean filtered, ChatType.Bound boundType) {
            player.connection.sendDisguisedChatMessage(this.content, boundType);
        }
    }

    public record Player(PlayerChatMessage message) implements OutgoingChatMessage {
        @Override
        public Component content() {
            return this.message.decoratedContent();
        }

        @Override
        public void sendToPlayer(ServerPlayer player, boolean filtered, ChatType.Bound boundType) {
            PlayerChatMessage playerChatMessage = this.message.filter(filtered);
            if (!playerChatMessage.isFullyFiltered()) {
                player.connection.sendPlayerChatMessage(playerChatMessage, boundType);
            }
        }
    }
}
