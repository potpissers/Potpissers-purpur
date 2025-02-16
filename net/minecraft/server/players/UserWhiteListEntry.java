package net.minecraft.server.players;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.util.UUID;

public class UserWhiteListEntry extends StoredUserEntry<GameProfile> {
    public UserWhiteListEntry(GameProfile user) {
        super(user);
    }

    public UserWhiteListEntry(JsonObject user) {
        super(createGameProfile(user));
    }

    @Override
    protected void serialize(JsonObject data) {
        if (this.getUser() != null) {
            data.addProperty("uuid", this.getUser().getId() == null ? "" : this.getUser().getId().toString());
            data.addProperty("name", this.getUser().getName());
        }
    }

    private static GameProfile createGameProfile(JsonObject json) {
        if (json.has("uuid") && json.has("name")) {
            String asString = json.get("uuid").getAsString();

            UUID uuid;
            try {
                uuid = UUID.fromString(asString);
            } catch (Throwable var4) {
                return null;
            }

            return new GameProfile(uuid, json.get("name").getAsString());
        } else {
            return null;
        }
    }
}
