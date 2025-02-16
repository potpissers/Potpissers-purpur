package net.minecraft.server.bossevents;

import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class CustomBossEvents {
    private final Map<ResourceLocation, CustomBossEvent> events = Maps.newHashMap();

    @Nullable
    public CustomBossEvent get(ResourceLocation id) {
        return this.events.get(id);
    }

    public CustomBossEvent create(ResourceLocation id, Component name) {
        CustomBossEvent customBossEvent = new CustomBossEvent(id, name);
        this.events.put(id, customBossEvent);
        return customBossEvent;
    }

    public void remove(CustomBossEvent bossbar) {
        this.events.remove(bossbar.getTextId());
    }

    public Collection<ResourceLocation> getIds() {
        return this.events.keySet();
    }

    public Collection<CustomBossEvent> getEvents() {
        return this.events.values();
    }

    public CompoundTag save(HolderLookup.Provider levelRegistry) {
        CompoundTag compoundTag = new CompoundTag();

        for (CustomBossEvent customBossEvent : this.events.values()) {
            compoundTag.put(customBossEvent.getTextId().toString(), customBossEvent.save(levelRegistry));
        }

        return compoundTag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider levelRegistry) {
        for (String string : tag.getAllKeys()) {
            ResourceLocation resourceLocation = ResourceLocation.parse(string);
            this.events.put(resourceLocation, CustomBossEvent.load(tag.getCompound(string), resourceLocation, levelRegistry));
        }
    }

    public void onPlayerConnect(ServerPlayer player) {
        for (CustomBossEvent customBossEvent : this.events.values()) {
            customBossEvent.onPlayerConnect(player);
        }
    }

    public void onPlayerDisconnect(ServerPlayer player) {
        for (CustomBossEvent customBossEvent : this.events.values()) {
            customBossEvent.onPlayerDisconnect(player);
        }
    }
}
