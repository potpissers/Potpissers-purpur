package net.minecraft.server.players;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringUtil;
import org.slf4j.Logger;

public class GameProfileCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int GAMEPROFILES_MRU_LIMIT = 1000;
    private static final int GAMEPROFILES_EXPIRATION_MONTHS = 1;
    private static boolean usesAuthentication;
    private final Map<String, GameProfileCache.GameProfileInfo> profilesByName = Maps.newConcurrentMap();
    private final Map<UUID, GameProfileCache.GameProfileInfo> profilesByUUID = Maps.newConcurrentMap();
    private final Map<String, CompletableFuture<Optional<GameProfile>>> requests = Maps.newConcurrentMap();
    private final GameProfileRepository profileRepository;
    private final Gson gson = new GsonBuilder().create();
    private final File file;
    private final AtomicLong operationCount = new AtomicLong();
    @Nullable
    private Executor executor;
    // Paper start - Fix GameProfileCache concurrency
    protected final java.util.concurrent.locks.ReentrantLock stateLock = new java.util.concurrent.locks.ReentrantLock();
    protected final java.util.concurrent.locks.ReentrantLock lookupLock = new java.util.concurrent.locks.ReentrantLock();
    // Paper end - Fix GameProfileCache concurrency

    public GameProfileCache(GameProfileRepository profileRepository, File file) {
        this.profileRepository = profileRepository;
        this.file = file;
        Lists.reverse(this.load()).forEach(this::safeAdd);
    }

    private void safeAdd(GameProfileCache.GameProfileInfo profile) {
        try { this.stateLock.lock(); // Paper - Fix GameProfileCache concurrency
        GameProfile profile1 = profile.getProfile();
        profile.setLastAccess(this.getNextOperation());
        this.profilesByName.put(profile1.getName().toLowerCase(Locale.ROOT), profile);
        this.profilesByUUID.put(profile1.getId(), profile);
        } finally { this.stateLock.unlock(); } // Paper - Fix GameProfileCache concurrency
    }

    private static Optional<GameProfile> lookupGameProfile(GameProfileRepository profileRepo, String name) {
        if (!StringUtil.isValidPlayerName(name)) {
            return createUnknownProfile(name);
        } else {
            final AtomicReference<GameProfile> atomicReference = new AtomicReference<>();
            ProfileLookupCallback profileLookupCallback = new ProfileLookupCallback() {
                @Override
                public void onProfileLookupSucceeded(GameProfile profile) {
                    atomicReference.set(profile);
                }

                @Override
                public void onProfileLookupFailed(String profileName, Exception exception) {
                    atomicReference.set(null);
                }
            };
            if (!org.apache.commons.lang3.StringUtils.isBlank(name) // Paper - Don't lookup a profile with a blank name
                && io.papermc.paper.configuration.GlobalConfiguration.get().proxies.isProxyOnlineMode()) // Paper - Add setting for proxy online mode status
            profileRepo.findProfilesByNames(new String[]{name}, profileLookupCallback);
            GameProfile gameProfile = atomicReference.get();
            return gameProfile != null ? Optional.of(gameProfile) : createUnknownProfile(name);
        }
    }

    private static Optional<GameProfile> createUnknownProfile(String profileName) {
        return usesAuthentication() ? Optional.empty() : Optional.of(UUIDUtil.createOfflineProfile(profileName));
    }

    public static void setUsesAuthentication(boolean onlineMode) {
        usesAuthentication = onlineMode;
    }

    private static boolean usesAuthentication() {
        return io.papermc.paper.configuration.GlobalConfiguration.get().proxies.isProxyOnlineMode(); // Paper - Add setting for proxy online mode status
    }

    public void add(GameProfile gameProfile) {
        Calendar instance = Calendar.getInstance();
        instance.setTime(new Date());
        instance.add(2, 1);
        Date time = instance.getTime();
        GameProfileCache.GameProfileInfo gameProfileInfo = new GameProfileCache.GameProfileInfo(gameProfile, time);
        this.safeAdd(gameProfileInfo);
        if (!org.spigotmc.SpigotConfig.saveUserCacheOnStopOnly) this.save(true); // Spigot - skip saving if disabled // Paper - Perf: Async GameProfileCache saving
    }

    private long getNextOperation() {
        return this.operationCount.incrementAndGet();
    }

    // Paper start
    public @Nullable GameProfile getProfileIfCached(String name) {
        try { this.stateLock.lock(); // Paper - Fix GameProfileCache concurrency
        GameProfileCache.GameProfileInfo entry = this.profilesByName.get(name.toLowerCase(Locale.ROOT));
        if (entry == null) {
            return null;
        }
        entry.setLastAccess(this.getNextOperation());
        return entry.getProfile();
        } finally { this.stateLock.unlock(); } // Paper - Fix GameProfileCache concurrency
    }
    // Paper end

    public Optional<GameProfile> get(String name) {
        String string = name.toLowerCase(Locale.ROOT);
        boolean stateLocked = true; try { this.stateLock.lock(); // Paper - Fix GameProfileCache concurrency
        GameProfileCache.GameProfileInfo gameProfileInfo = this.profilesByName.get(string);
        boolean flag = false;
        if (gameProfileInfo != null && new Date().getTime() >= gameProfileInfo.expirationDate.getTime()) {
            this.profilesByUUID.remove(gameProfileInfo.getProfile().getId());
            this.profilesByName.remove(gameProfileInfo.getProfile().getName().toLowerCase(Locale.ROOT));
            flag = true;
            gameProfileInfo = null;
        }

        Optional<GameProfile> optional;
        if (gameProfileInfo != null) {
            gameProfileInfo.setLastAccess(this.getNextOperation());
            optional = Optional.of(gameProfileInfo.getProfile());
            stateLocked = false; this.stateLock.unlock(); // Paper - Fix GameProfileCache concurrency
        } else {
            stateLocked = false; this.stateLock.unlock(); // Paper - Fix GameProfileCache concurrency
            try { this.lookupLock.lock(); // Paper - Fix GameProfileCache concurrency
            optional = lookupGameProfile(this.profileRepository, name); // CraftBukkit - use correct case for offline players
            } finally { this.lookupLock.unlock(); } // Paper - Fix GameProfileCache concurrency
            if (optional.isPresent()) {
                this.add(optional.get());
                flag = false;
            }
        }

        if (flag && !org.spigotmc.SpigotConfig.saveUserCacheOnStopOnly) { // Spigot - skip saving if disabled
            this.save(true); // Paper - Perf: Async GameProfileCache saving
        }

        return optional;
        } finally { if (stateLocked) {  this.stateLock.unlock(); } } // Paper - Fix GameProfileCache concurrency
    }

    public CompletableFuture<Optional<GameProfile>> getAsync(String name) {
        if (this.executor == null) {
            throw new IllegalStateException("No executor");
        } else {
            CompletableFuture<Optional<GameProfile>> completableFuture = this.requests.get(name);
            if (completableFuture != null) {
                return completableFuture;
            } else {
                CompletableFuture<Optional<GameProfile>> completableFuture1 = CompletableFuture.<Optional<GameProfile>>supplyAsync(
                        () -> this.get(name), Util.PROFILE_EXECUTOR // Paper - don't submit BLOCKING PROFILE LOOKUPS to the world gen thread
                    )
                    .whenCompleteAsync((gameProfile, exception) -> this.requests.remove(name), this.executor);
                this.requests.put(name, completableFuture1);
                return completableFuture1;
            }
        }
    }

    public Optional<GameProfile> get(UUID uuid) {
        try { this.stateLock.lock(); // Paper - Fix GameProfileCache concurrency
        GameProfileCache.GameProfileInfo gameProfileInfo = this.profilesByUUID.get(uuid);
        if (gameProfileInfo == null) {
            return Optional.empty();
        } else {
            gameProfileInfo.setLastAccess(this.getNextOperation());
            return Optional.of(gameProfileInfo.getProfile());
        }
        } finally { this.stateLock.unlock(); } // Paper - Fix GameProfileCache concurrency
    }

    public void setExecutor(Executor exectutor) {
        this.executor = exectutor;
    }

    public void clearExecutor() {
        this.executor = null;
    }

    private static DateFormat createDateFormat() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
    }

    public List<GameProfileCache.GameProfileInfo> load() {
        List<GameProfileCache.GameProfileInfo> list = Lists.newArrayList();

        try {
            Object var9;
            try (Reader reader = Files.newReader(this.file, StandardCharsets.UTF_8)) {
                JsonArray jsonArray = this.gson.fromJson(reader, JsonArray.class);
                if (jsonArray != null) {
                    DateFormat dateFormat = createDateFormat();
                    jsonArray.forEach(json -> readGameProfile(json, dateFormat).ifPresent(list::add));
                    return list;
                }

                var9 = list;
            }

            return (List<GameProfileCache.GameProfileInfo>)var9;
        } catch (FileNotFoundException var7) {
        // Spigot start
        } catch (com.google.gson.JsonSyntaxException | NullPointerException ex) {
            LOGGER.warn( "Usercache.json is corrupted or has bad formatting. Deleting it to prevent further issues." );
            this.file.delete();
        // Spigot end
        } catch (JsonParseException | IOException var8) {
            LOGGER.warn("Failed to load profile cache {}", this.file, var8);
        }

        return list;
    }

    public void save(boolean asyncSave) { // Paper - Perf: Async GameProfileCache saving
        JsonArray jsonArray = new JsonArray();
        DateFormat dateFormat = createDateFormat();
        this.listTopMRUProfiles(org.spigotmc.SpigotConfig.userCacheCap).forEach((info) -> jsonArray.add(writeGameProfile(info, dateFormat))); // Spigot // Paper - Fix GameProfileCache concurrency
        String string = this.gson.toJson((JsonElement)jsonArray);
        Runnable save = () -> { // Paper - Perf: Async GameProfileCache saving

        try (Writer writer = Files.newWriter(this.file, StandardCharsets.UTF_8)) {
            writer.write(string);
        } catch (IOException var9) {
        }
        // Paper start - Perf: Async GameProfileCache saving
        };
        if (asyncSave) {
            io.papermc.paper.util.MCUtil.scheduleAsyncTask(save);
        } else {
            save.run();
        }
        // Paper end - Perf: Async GameProfileCache saving
    }

    private Stream<GameProfileCache.GameProfileInfo> getTopMRUProfiles(int limit) {
       // Paper start - Fix GameProfileCache concurrency
       return this.listTopMRUProfiles(limit).stream();
    }

    private List<GameProfileCache.GameProfileInfo> listTopMRUProfiles(int limit) {
        try {
            this.stateLock.lock();
            return this.profilesByUUID.values()
                .stream()
                .sorted(Comparator.comparing(GameProfileCache.GameProfileInfo::getLastAccess).reversed())
                .limit(limit)
                .toList();
        } finally {
            this.stateLock.unlock();
        }
    }
    // Paper end - Fix GameProfileCache concurrency

    private static JsonElement writeGameProfile(GameProfileCache.GameProfileInfo profileInfo, DateFormat dateFormat) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("name", profileInfo.getProfile().getName());
        jsonObject.addProperty("uuid", profileInfo.getProfile().getId().toString());
        jsonObject.addProperty("expiresOn", dateFormat.format(profileInfo.getExpirationDate()));
        return jsonObject;
    }

    private static Optional<GameProfileCache.GameProfileInfo> readGameProfile(JsonElement json, DateFormat dateFormat) {
        if (json.isJsonObject()) {
            JsonObject asJsonObject = json.getAsJsonObject();
            JsonElement jsonElement = asJsonObject.get("name");
            JsonElement jsonElement1 = asJsonObject.get("uuid");
            JsonElement jsonElement2 = asJsonObject.get("expiresOn");
            if (jsonElement != null && jsonElement1 != null) {
                String asString = jsonElement1.getAsString();
                String asString1 = jsonElement.getAsString();
                Date date = null;
                if (jsonElement2 != null) {
                    try {
                        date = dateFormat.parse(jsonElement2.getAsString());
                    } catch (ParseException var12) {
                    }
                }

                if (asString1 != null && asString != null && date != null) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(asString);
                    } catch (Throwable var11) {
                        return Optional.empty();
                    }

                    return Optional.of(new GameProfileCache.GameProfileInfo(new GameProfile(uuid, asString1), date));
                } else {
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    static class GameProfileInfo {
        private final GameProfile profile;
        final Date expirationDate;
        private volatile long lastAccess;

        GameProfileInfo(GameProfile profile, Date expirationDate) {
            this.profile = profile;
            this.expirationDate = expirationDate;
        }

        public GameProfile getProfile() {
            return this.profile;
        }

        public Date getExpirationDate() {
            return this.expirationDate;
        }

        public void setLastAccess(long lastAccess) {
            this.lastAccess = lastAccess;
        }

        public long getLastAccess() {
            return this.lastAccess;
        }
    }
}
