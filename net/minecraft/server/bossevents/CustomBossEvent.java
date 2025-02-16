package net.minecraft.server.bossevents;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;

public class CustomBossEvent extends ServerBossEvent {
    private final ResourceLocation id;
    private final Set<UUID> players = Sets.newHashSet();
    private int value;
    private int max = 100;

    public CustomBossEvent(ResourceLocation id, Component name) {
        super(name, BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
        this.id = id;
        this.setProgress(0.0F);
    }

    public ResourceLocation getTextId() {
        return this.id;
    }

    @Override
    public void addPlayer(ServerPlayer player) {
        super.addPlayer(player);
        this.players.add(player.getUUID());
    }

    public void addOfflinePlayer(UUID player) {
        this.players.add(player);
    }

    @Override
    public void removePlayer(ServerPlayer player) {
        super.removePlayer(player);
        this.players.remove(player.getUUID());
    }

    @Override
    public void removeAllPlayers() {
        super.removeAllPlayers();
        this.players.clear();
    }

    public int getValue() {
        return this.value;
    }

    public int getMax() {
        return this.max;
    }

    public void setValue(int value) {
        this.value = value;
        this.setProgress(Mth.clamp((float)value / this.max, 0.0F, 1.0F));
    }

    public void setMax(int max) {
        this.max = max;
        this.setProgress(Mth.clamp((float)this.value / max, 0.0F, 1.0F));
    }

    public final Component getDisplayName() {
        return ComponentUtils.wrapInSquareBrackets(this.getName())
            .withStyle(
                style -> style.withColor(this.getColor().getFormatting())
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(this.getTextId().toString())))
                    .withInsertion(this.getTextId().toString())
            );
    }

    public boolean setPlayers(Collection<ServerPlayer> serverPlayerList) {
        Set<UUID> set = Sets.newHashSet();
        Set<ServerPlayer> set1 = Sets.newHashSet();

        for (UUID uuid : this.players) {
            boolean flag = false;

            for (ServerPlayer serverPlayer : serverPlayerList) {
                if (serverPlayer.getUUID().equals(uuid)) {
                    flag = true;
                    break;
                }
            }

            if (!flag) {
                set.add(uuid);
            }
        }

        for (ServerPlayer serverPlayer1 : serverPlayerList) {
            boolean flag = false;

            for (UUID uuid1 : this.players) {
                if (serverPlayer1.getUUID().equals(uuid1)) {
                    flag = true;
                    break;
                }
            }

            if (!flag) {
                set1.add(serverPlayer1);
            }
        }

        for (UUID uuid : set) {
            for (ServerPlayer serverPlayer2 : this.getPlayers()) {
                if (serverPlayer2.getUUID().equals(uuid)) {
                    this.removePlayer(serverPlayer2);
                    break;
                }
            }

            this.players.remove(uuid);
        }

        for (ServerPlayer serverPlayer1 : set1) {
            this.addPlayer(serverPlayer1);
        }

        return !set.isEmpty() || !set1.isEmpty();
    }

    public CompoundTag save(HolderLookup.Provider levelRegistry) {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("Name", Component.Serializer.toJson(this.name, levelRegistry));
        compoundTag.putBoolean("Visible", this.isVisible());
        compoundTag.putInt("Value", this.value);
        compoundTag.putInt("Max", this.max);
        compoundTag.putString("Color", this.getColor().getName());
        compoundTag.putString("Overlay", this.getOverlay().getName());
        compoundTag.putBoolean("DarkenScreen", this.shouldDarkenScreen());
        compoundTag.putBoolean("PlayBossMusic", this.shouldPlayBossMusic());
        compoundTag.putBoolean("CreateWorldFog", this.shouldCreateWorldFog());
        ListTag listTag = new ListTag();

        for (UUID uuid : this.players) {
            listTag.add(NbtUtils.createUUID(uuid));
        }

        compoundTag.put("Players", listTag);
        return compoundTag;
    }

    public static CustomBossEvent load(CompoundTag tag, ResourceLocation id, HolderLookup.Provider levelRegistry) {
        CustomBossEvent customBossEvent = new CustomBossEvent(id, Component.Serializer.fromJson(tag.getString("Name"), levelRegistry));
        customBossEvent.setVisible(tag.getBoolean("Visible"));
        customBossEvent.setValue(tag.getInt("Value"));
        customBossEvent.setMax(tag.getInt("Max"));
        customBossEvent.setColor(BossEvent.BossBarColor.byName(tag.getString("Color")));
        customBossEvent.setOverlay(BossEvent.BossBarOverlay.byName(tag.getString("Overlay")));
        customBossEvent.setDarkenScreen(tag.getBoolean("DarkenScreen"));
        customBossEvent.setPlayBossMusic(tag.getBoolean("PlayBossMusic"));
        customBossEvent.setCreateWorldFog(tag.getBoolean("CreateWorldFog"));

        for (Tag tag1 : tag.getList("Players", 11)) {
            customBossEvent.addOfflinePlayer(NbtUtils.loadUUID(tag1));
        }

        return customBossEvent;
    }

    public void onPlayerConnect(ServerPlayer player) {
        if (this.players.contains(player.getUUID())) {
            this.addPlayer(player);
        }
    }

    public void onPlayerDisconnect(ServerPlayer player) {
        super.removePlayer(player);
    }
}
