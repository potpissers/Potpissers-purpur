package net.minecraft.server.players;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.io.File;
import java.util.Objects;

public class ServerOpList extends StoredUserList<GameProfile, ServerOpListEntry> {
    public ServerOpList(File file) {
        super(file);
    }

    @Override
    protected StoredUserEntry<GameProfile> createEntry(JsonObject entryData) {
        return new ServerOpListEntry(entryData);
    }

    @Override
    public String[] getUserList() {
        return this.getEntries().stream().map(StoredUserEntry::getUser).filter(Objects::nonNull).map(GameProfile::getName).toArray(String[]::new);
    }

    public boolean canBypassPlayerLimit(GameProfile profile) {
        ServerOpListEntry serverOpListEntry = this.get(profile);
        return serverOpListEntry != null && serverOpListEntry.getBypassesPlayerLimit();
    }

    @Override
    protected String getKeyForUser(GameProfile obj) {
        return obj.getId().toString();
    }
}
