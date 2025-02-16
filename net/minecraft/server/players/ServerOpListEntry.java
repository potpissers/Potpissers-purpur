package net.minecraft.server.players;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import javax.annotation.Nullable;

public class ServerOpListEntry extends StoredUserEntry<GameProfile> {
    private final int level;
    private final boolean bypassesPlayerLimit;

    public ServerOpListEntry(GameProfile user, int level, boolean bypassesPlayerLimit) {
        super(user);
        this.level = level;
        this.bypassesPlayerLimit = bypassesPlayerLimit;
    }

    public ServerOpListEntry(JsonObject user) {
        super(createGameProfile(user));
        this.level = user.has("level") ? user.get("level").getAsInt() : 0;
        this.bypassesPlayerLimit = user.has("bypassesPlayerLimit") && user.get("bypassesPlayerLimit").getAsBoolean();
    }

    public int getLevel() {
        return this.level;
    }

    public boolean getBypassesPlayerLimit() {
        return this.bypassesPlayerLimit;
    }

    @Override
    protected void serialize(JsonObject data) {
        if (this.getUser() != null) {
            data.addProperty("uuid", this.getUser().getId().toString());
            data.addProperty("name", this.getUser().getName());
            data.addProperty("level", this.level);
            data.addProperty("bypassesPlayerLimit", this.bypassesPlayerLimit);
        }
    }

    @Nullable
    private static GameProfile createGameProfile(JsonObject profileData) {
        if (profileData.has("uuid") && profileData.has("name")) {
            String asString = profileData.get("uuid").getAsString();

            UUID uuid;
            try {
                uuid = UUID.fromString(asString);
            } catch (Throwable var4) {
                return null;
            }

            return new GameProfile(uuid, profileData.get("name").getAsString());
        } else {
            return null;
        }
    }
}
