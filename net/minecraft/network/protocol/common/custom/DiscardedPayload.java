package net.minecraft.network.protocol.common.custom;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record DiscardedPayload(ResourceLocation id, io.netty.buffer.ByteBuf data) implements CustomPacketPayload { // CraftBukkit - store data
    public static <T extends FriendlyByteBuf> StreamCodec<T, DiscardedPayload> codec(ResourceLocation id, int maxSize) {
        return CustomPacketPayload.codec((value, output) -> {
            output.writeBytes(value.data); // CraftBukkit - serialize
        }, buffer -> {
            int i = buffer.readableBytes();
            if (i >= 0 && i <= maxSize) {
                return new DiscardedPayload(id, buffer.readBytes(i)); // CraftBukkit
            } else {
                throw new IllegalArgumentException("Payload may not be larger than " + maxSize + " bytes");
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<DiscardedPayload> type() {
        return new CustomPacketPayload.Type<>(this.id);
    }
}
