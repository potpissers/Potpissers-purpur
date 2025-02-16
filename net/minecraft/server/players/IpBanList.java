package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.io.File;
import java.net.SocketAddress;
import javax.annotation.Nullable;

public class IpBanList extends StoredUserList<String, IpBanListEntry> {
    public IpBanList(File file) {
        super(file);
    }

    @Override
    protected StoredUserEntry<String> createEntry(JsonObject entryData) {
        return new IpBanListEntry(entryData);
    }

    public boolean isBanned(SocketAddress address) {
        String ipFromAddress = this.getIpFromAddress(address);
        return this.contains(ipFromAddress);
    }

    public boolean isBanned(String address) {
        return this.contains(address);
    }

    @Nullable
    public IpBanListEntry get(SocketAddress address) {
        String ipFromAddress = this.getIpFromAddress(address);
        return this.get(ipFromAddress);
    }

    private String getIpFromAddress(SocketAddress address) {
        String string = address.toString();
        if (string.contains("/")) {
            string = string.substring(string.indexOf(47) + 1);
        }

        if (string.contains(":")) {
            string = string.substring(0, string.indexOf(58));
        }

        return string;
    }
}
