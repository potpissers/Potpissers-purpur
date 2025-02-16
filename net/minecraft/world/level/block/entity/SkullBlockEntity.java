package net.minecraft.world.level.block.entity;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.logging.LogUtils;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Services;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public class SkullBlockEntity extends BlockEntity {
    private static final String TAG_PROFILE = "profile";
    private static final String TAG_NOTE_BLOCK_SOUND = "note_block_sound";
    private static final String TAG_CUSTOM_NAME = "custom_name";
    private static final Logger LOGGER = LogUtils.getLogger();
    @Nullable
    private static Executor mainThreadExecutor;
    @Nullable
    private static LoadingCache<String, CompletableFuture<Optional<GameProfile>>> profileCacheByName;
    @Nullable
    private static LoadingCache<UUID, CompletableFuture<Optional<GameProfile>>> profileCacheById;
    public static final Executor CHECKED_MAIN_THREAD_EXECUTOR = runnable -> {
        Executor executor = mainThreadExecutor;
        if (executor != null) {
            executor.execute(runnable);
        }
    };
    @Nullable
    public ResolvableProfile owner;
    @Nullable
    public ResourceLocation noteBlockSound;
    private int animationTickCount;
    private boolean isAnimating;
    @Nullable
    private Component customName;

    public SkullBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.SKULL, pos, blockState);
    }

    public static void setup(final Services services, Executor mainThreadExecutor) {
        SkullBlockEntity.mainThreadExecutor = mainThreadExecutor;
        final BooleanSupplier booleanSupplier = () -> profileCacheById == null;
        profileCacheByName = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10L))
            .maximumSize(256L)
            .build(new CacheLoader<String, CompletableFuture<Optional<GameProfile>>>() {
                @Override
                public CompletableFuture<Optional<GameProfile>> load(String username) {
                    return SkullBlockEntity.fetchProfileByName(username, services);
                }
            });
        profileCacheById = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10L))
            .maximumSize(256L)
            .build(new CacheLoader<UUID, CompletableFuture<Optional<GameProfile>>>() {
                @Override
                public CompletableFuture<Optional<GameProfile>> load(UUID id) {
                    return SkullBlockEntity.fetchProfileById(id, services, booleanSupplier);
                }
            });
    }

    static CompletableFuture<Optional<GameProfile>> fetchProfileByName(String name, Services services) {
        return services.profileCache()
            .getAsync(name)
            .thenCompose(
                optional -> {
                    LoadingCache<UUID, CompletableFuture<Optional<GameProfile>>> loadingCache = profileCacheById;
                    return loadingCache != null && !optional.isEmpty()
                        ? loadingCache.getUnchecked(optional.get().getId()).thenApply(optional1 -> optional1.or(() -> optional))
                        : CompletableFuture.completedFuture(Optional.empty());
                }
            );
    }

    static CompletableFuture<Optional<GameProfile>> fetchProfileById(UUID id, Services services, BooleanSupplier cacheUninitialized) {
        return CompletableFuture.supplyAsync(() -> {
            if (cacheUninitialized.getAsBoolean()) {
                return Optional.empty();
            } else {
                ProfileResult profileResult = services.sessionService().fetchProfile(id, true);
                return Optional.ofNullable(profileResult).map(ProfileResult::profile);
            }
        }, Util.backgroundExecutor().forName("fetchProfile"));
    }

    public static void clear() {
        mainThreadExecutor = null;
        profileCacheByName = null;
        profileCacheById = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.owner != null) {
            tag.put("profile", ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, this.owner).getOrThrow());
        }

        if (this.noteBlockSound != null) {
            tag.putString("note_block_sound", this.noteBlockSound.toString());
        }

        if (this.customName != null) {
            tag.putString("custom_name", Component.Serializer.toJson(this.customName, registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("profile")) {
            ResolvableProfile.CODEC
                .parse(NbtOps.INSTANCE, tag.get("profile"))
                .resultOrPartial(string -> LOGGER.error("Failed to load profile from player head: {}", string))
                .ifPresent(this::setOwner);
        }

        if (tag.contains("note_block_sound", 8)) {
            this.noteBlockSound = ResourceLocation.tryParse(tag.getString("note_block_sound"));
        }

        if (tag.contains("custom_name", 8)) {
            this.customName = parseCustomNameSafe(tag.getString("custom_name"), registries);
        } else {
            this.customName = null;
        }
    }

    public static void animation(Level level, BlockPos pos, BlockState state, SkullBlockEntity blockEntity) {
        if (state.hasProperty(SkullBlock.POWERED) && state.getValue(SkullBlock.POWERED)) {
            blockEntity.isAnimating = true;
            blockEntity.animationTickCount++;
        } else {
            blockEntity.isAnimating = false;
        }
    }

    public float getAnimation(float partialTick) {
        return this.isAnimating ? this.animationTickCount + partialTick : this.animationTickCount;
    }

    @Nullable
    public ResolvableProfile getOwnerProfile() {
        return this.owner;
    }

    @Nullable
    public ResourceLocation getNoteBlockSound() {
        return this.noteBlockSound;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public void setOwner(@Nullable ResolvableProfile owner) {
        synchronized (this) {
            this.owner = owner;
        }

        this.updateOwnerProfile();
    }

    private void updateOwnerProfile() {
        if (this.owner != null && !this.owner.isResolved()) {
            this.owner.resolve().thenAcceptAsync(resolvableProfile -> {
                this.owner = resolvableProfile;
                this.setChanged();
            }, CHECKED_MAIN_THREAD_EXECUTOR);
        } else {
            this.setChanged();
        }
    }

    public static CompletableFuture<Optional<GameProfile>> fetchGameProfile(String profileName) {
        LoadingCache<String, CompletableFuture<Optional<GameProfile>>> loadingCache = profileCacheByName;
        return loadingCache != null && StringUtil.isValidPlayerName(profileName)
            ? loadingCache.getUnchecked(profileName)
            : CompletableFuture.completedFuture(Optional.empty());
    }

    public static CompletableFuture<Optional<GameProfile>> fetchGameProfile(UUID profileUuid) {
        LoadingCache<UUID, CompletableFuture<Optional<GameProfile>>> loadingCache = profileCacheById;
        return loadingCache != null ? loadingCache.getUnchecked(profileUuid) : CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        this.setOwner(componentInput.get(DataComponents.PROFILE));
        this.noteBlockSound = componentInput.get(DataComponents.NOTE_BLOCK_SOUND);
        this.customName = componentInput.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.PROFILE, this.owner);
        components.set(DataComponents.NOTE_BLOCK_SOUND, this.noteBlockSound);
        components.set(DataComponents.CUSTOM_NAME, this.customName);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("profile");
        tag.remove("note_block_sound");
        tag.remove("custom_name");
    }
}
