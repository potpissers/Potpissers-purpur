package net.minecraft.world.level.timers;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;

public class FunctionTagCallback implements TimerCallback<MinecraftServer> {
    final ResourceLocation tagId;

    public FunctionTagCallback(ResourceLocation tagId) {
        this.tagId = tagId;
    }

    @Override
    public void handle(MinecraftServer obj, TimerQueue<MinecraftServer> manager, long gameTime) {
        ServerFunctionManager functions = obj.getFunctions();

        for (CommandFunction<CommandSourceStack> commandFunction : functions.getTag(this.tagId)) {
            functions.execute(commandFunction, functions.getGameLoopSender());
        }
    }

    public static class Serializer extends TimerCallback.Serializer<MinecraftServer, FunctionTagCallback> {
        public Serializer() {
            super(ResourceLocation.withDefaultNamespace("function_tag"), FunctionTagCallback.class);
        }

        @Override
        public void serialize(CompoundTag tag, FunctionTagCallback callback) {
            tag.putString("Name", callback.tagId.toString());
        }

        @Override
        public FunctionTagCallback deserialize(CompoundTag tag) {
            ResourceLocation resourceLocation = ResourceLocation.parse(tag.getString("Name"));
            return new FunctionTagCallback(resourceLocation);
        }
    }
}
