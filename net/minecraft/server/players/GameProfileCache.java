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

    public GameProfileCache(GameProfileRepository profileRepository, File file) {
        this.profileRepository = profileRepository;
        this.file = file;
        Lists.reverse(this.load()).forEach(this::safeAdd);
    }

    private void safeAdd(GameProfileCache.GameProfileInfo profile) {
        GameProfile profile1 = profile.getProfile();
        profile.setLastAccess(this.getNextOperation());
        this.profilesByName.put(profile1.getName().toLowerCase(Locale.ROOT), profile);
        this.profilesByUUID.put(profile1.getId(), profile);
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
        return usesAuthentication;
    }

    public void add(GameProfile gameProfile) {
        Calendar instance = Calendar.getInstance();
        instance.setTime(new Date());
        instance.add(2, 1);
        Date time = instance.getTime();
        GameProfileCache.GameProfileInfo gameProfileInfo = new GameProfileCache.GameProfileInfo(gameProfile, time);
        this.safeAdd(gameProfileInfo);
        this.save();
    }

    private long getNextOperation() {
        return this.operationCount.incrementAndGet();
    }

    public Optional<GameProfile> get(String name) {
        String string = name.toLowerCase(Locale.ROOT);
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
        } else {
            optional = lookupGameProfile(this.profileRepository, string);
            if (optional.isPresent()) {
                this.add(optional.get());
                flag = false;
            }
        }

        if (flag) {
            this.save();
        }

        return optional;
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
                        () -> this.get(name), Util.backgroundExecutor().forName("getProfile")
                    )
                    .whenCompleteAsync((gameProfile, exception) -> this.requests.remove(name), this.executor);
                this.requests.put(name, completableFuture1);
                return completableFuture1;
            }
        }
    }

    public Optional<GameProfile> get(UUID uuid) {
        GameProfileCache.GameProfileInfo gameProfileInfo = this.profilesByUUID.get(uuid);
        if (gameProfileInfo == null) {
            return Optional.empty();
        } else {
            gameProfileInfo.setLastAccess(this.getNextOperation());
            return Optional.of(gameProfileInfo.getProfile());
        }
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
        } catch (JsonParseException | IOException var8) {
            LOGGER.warn("Failed to load profile cache {}", this.file, var8);
        }

        return list;
    }

    public void save() {
        JsonArray jsonArray = new JsonArray();
        DateFormat dateFormat = createDateFormat();
        this.getTopMRUProfiles(1000).forEach(info -> jsonArray.add(writeGameProfile(info, dateFormat)));
        String string = this.gson.toJson((JsonElement)jsonArray);

        try (Writer writer = Files.newWriter(this.file, StandardCharsets.UTF_8)) {
            writer.write(string);
        } catch (IOException var9) {
        }
    }

    private Stream<GameProfileCache.GameProfileInfo> getTopMRUProfiles(int limit) {
        return ImmutableList.copyOf(this.profilesByUUID.values())
            .stream()
            .sorted(Comparator.comparing(GameProfileCache.GameProfileInfo::getLastAccess).reversed())
            .limit(limit);
    }

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
