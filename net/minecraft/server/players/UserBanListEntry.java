package net.minecraft.server.players;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.util.Date;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;

public class UserBanListEntry extends BanListEntry<GameProfile> {
    public UserBanListEntry(@Nullable GameProfile user) {
        this(user, null, null, null, null);
    }

    public UserBanListEntry(@Nullable GameProfile profile, @Nullable Date created, @Nullable String source, @Nullable Date expires, @Nullable String reason) {
        super(profile, created, source, expires, reason);
    }

    public UserBanListEntry(JsonObject entryData) {
        super(createGameProfile(entryData), entryData);
    }

    @Override
    protected void serialize(JsonObject data) {
        if (this.getUser() != null) {
            data.addProperty("uuid", this.getUser().getId().toString());
            data.addProperty("name", this.getUser().getName());
            super.serialize(data);
        }
    }

    @Override
    public Component getDisplayName() {
        GameProfile gameProfile = this.getUser();
        return gameProfile != null ? Component.literal(gameProfile.getName()) : Component.translatable("commands.banlist.entry.unknown");
    }

    @Nullable
    private static GameProfile createGameProfile(JsonObject json) {
        // Spigot start
        // this whole method has to be reworked to account for the fact Bukkit only accepts UUID bans and gives no way for usernames to be stored!
        UUID uuid = null;
        String name = null;
        if (json.has("uuid")) {
            String asString = json.get("uuid").getAsString();

            try {
                uuid = UUID.fromString(asString);
            } catch (Throwable var4) {
            }

        }
        if (json.has("name")) {
            name = json.get("name").getAsString();
        }
        if (uuid != null || name != null) {
            return new GameProfile(uuid, name);
        } else {
            return null;
        }
        // Spigot end
    }
}
