package net.minecraft.commands;

import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.network.chat.PlayerChatMessage;

public interface CommandSigningContext {
    CommandSigningContext ANONYMOUS = new CommandSigningContext() {
        @Nullable
        @Override
        public PlayerChatMessage getArgument(String name) {
            return null;
        }
    };

    @Nullable
    PlayerChatMessage getArgument(String name);

    public record SignedArguments(Map<String, PlayerChatMessage> arguments) implements CommandSigningContext {
        @Nullable
        @Override
        public PlayerChatMessage getArgument(String name) {
            return this.arguments.get(name);
        }
    }
}
