package net.minecraft.world.level.timers;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;

public class FunctionCallback implements TimerCallback<MinecraftServer> {
    final ResourceLocation functionId;

    public FunctionCallback(ResourceLocation functionId) {
        this.functionId = functionId;
    }

    @Override
    public void handle(MinecraftServer obj, TimerQueue<MinecraftServer> manager, long gameTime) {
        ServerFunctionManager functions = obj.getFunctions();
        functions.get(this.functionId)
            .ifPresent(commandFunction -> functions.execute((CommandFunction<CommandSourceStack>)commandFunction, functions.getGameLoopSender()));
    }

    public static class Serializer extends TimerCallback.Serializer<MinecraftServer, FunctionCallback> {
        public Serializer() {
            super(ResourceLocation.withDefaultNamespace("function"), FunctionCallback.class);
        }

        @Override
        public void serialize(CompoundTag tag, FunctionCallback callback) {
            tag.putString("Name", callback.functionId.toString());
        }

        @Override
        public FunctionCallback deserialize(CompoundTag tag) {
            ResourceLocation resourceLocation = ResourceLocation.parse(tag.getString("Name"));
            return new FunctionCallback(resourceLocation);
        }
    }
}
