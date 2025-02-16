package net.minecraft.world.item;

import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class ServerItemCooldowns extends ItemCooldowns {
    private final ServerPlayer player;

    public ServerItemCooldowns(ServerPlayer player) {
        this.player = player;
    }

    // Paper start - Add PlayerItemCooldownEvent
    private int getCurrentCooldown(final ResourceLocation groupId) {
        final net.minecraft.world.item.ItemCooldowns.CooldownInstance cooldownInstance = this.cooldowns.get(groupId);
        if (cooldownInstance == null) {
            return 0;
        }
        return Math.max(0, cooldownInstance.endTime() - this.tickCount);
    }

    @Override
    public void addCooldown(ItemStack item, int duration) {
        final ResourceLocation cooldownGroup = this.getCooldownGroup(item);
        final io.papermc.paper.event.player.PlayerItemCooldownEvent event = new io.papermc.paper.event.player.PlayerItemCooldownEvent(
            this.player.getBukkitEntity(),
            org.bukkit.craftbukkit.inventory.CraftItemType.minecraftToBukkit(item.getItem()),
            org.bukkit.craftbukkit.util.CraftNamespacedKey.fromMinecraft(cooldownGroup),
            duration
        );
        if (event.callEvent()) {
            this.addCooldown(cooldownGroup, event.getCooldown(), false);
        } else {
            this.player.connection.send(new ClientboundCooldownPacket(cooldownGroup, this.getCurrentCooldown(cooldownGroup)));
        }
    }

    @Override
    public void addCooldown(ResourceLocation groupId, int duration, boolean callEvent) {
        if (callEvent) {
            final io.papermc.paper.event.player.PlayerItemGroupCooldownEvent event = new io.papermc.paper.event.player.PlayerItemGroupCooldownEvent(
                this.player.getBukkitEntity(),
                org.bukkit.craftbukkit.util.CraftNamespacedKey.fromMinecraft(groupId),
                duration
            );
            if (!event.callEvent()) {
                this.player.connection.send(new ClientboundCooldownPacket(groupId, this.getCurrentCooldown(groupId)));
                return;
            }

            duration = event.getCooldown();
        }
        super.addCooldown(groupId, duration, false);
    }
    // Paper end - Add PlayerItemCooldownEvent

    @Override
    protected void onCooldownStarted(ResourceLocation group, int cooldown) {
        super.onCooldownStarted(group, cooldown);
        this.player.connection.send(new ClientboundCooldownPacket(group, cooldown));
    }

    @Override
    protected void onCooldownEnded(ResourceLocation group) {
        super.onCooldownEnded(group);
        this.player.connection.send(new ClientboundCooldownPacket(group, 0));
    }
}
