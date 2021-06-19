package net.minecraft.world.level.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.slf4j.Logger;

public class PlayerDataStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final File playerDir;
    protected final DataFixer fixerUpper;
    private static final DateTimeFormatter FORMATTER = FileNameDateFormatter.create();

    public PlayerDataStorage(LevelStorageSource.LevelStorageAccess levelStorageAccess, DataFixer fixerUpper) {
        this.fixerUpper = fixerUpper;
        this.playerDir = levelStorageAccess.getLevelPath(LevelResource.PLAYER_DATA_DIR).toFile();
        this.playerDir.mkdirs();
    }

    public void save(Player player) {
        if (org.spigotmc.SpigotConfig.disablePlayerDataSaving) return; // Spigot
        try {
            CompoundTag compoundTag = player.saveWithoutId(new CompoundTag());
            Path path = this.playerDir.toPath();
            Path path1 = Files.createTempFile(path, player.getStringUUID() + "-", ".dat");
            NbtIo.writeCompressed(compoundTag, path1);
            Path path2 = path.resolve(player.getStringUUID() + ".dat");
            Path path3 = path.resolve(player.getStringUUID() + ".dat_old");
            Util.safeReplaceFile(path2, path1, path3);
        } catch (Exception var7) {
            LOGGER.warn("Failed to save player data for {}", player.getScoreboardName(), var7); // Paper - Print exception
        }
    }

    private void backup(String name, String stringUuid, String suffix) { // CraftBukkit
        Path path = this.playerDir.toPath();
        Path path1 = path.resolve(stringUuid + suffix); // CraftBukkit
        Path path2 = path.resolve(stringUuid + "_corrupted_" + LocalDateTime.now().format(FORMATTER) + suffix); // CraftBukkit
        if (Files.isRegularFile(path1)) {
            try {
                Files.copy(path1, path2, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (Exception var7) {
                LOGGER.warn("Failed to copy the player.dat file for {}", name, var7); // CraftBukkit
            }
        }
    }

    private Optional<CompoundTag> load(String name, String stringUuid, String suffix) { // CraftBukkit
        File file = new File(this.playerDir, stringUuid + suffix); // CraftBukkit
        // Spigot start
        boolean usingWrongFile = false;
        if (org.bukkit.Bukkit.getOnlineMode() && !file.exists()) { // Paper - Check online mode first
            file = new File(this.playerDir, java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString() + suffix);
            if (file.exists()) {
                usingWrongFile = true;
                LOGGER.warn("Using offline mode UUID file for player {} as it is the only copy we can find.", name);
            }
        }
        // Spigot end
        if (file.exists() && file.isFile()) {
            try {
                // Spigot start
                Optional<CompoundTag> optional = Optional.of(NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap()));
                if (usingWrongFile) {
                    file.renameTo(new File(file.getPath() + ".offline-read"));
                }
                return optional;
                // Spigot end
            } catch (Exception var5) {
                LOGGER.warn("Failed to load player data for {}", name); // CraftBukkit
            }
        }

        return Optional.empty();
    }

    public Optional<CompoundTag> load(Player player) {
        // CraftBukkit start
        return this.load(player.getName().getString(), player.getStringUUID()).map((tag) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                CraftPlayer craftPlayer = serverPlayer.getBukkitEntity();
                // Only update first played if it is older than the one we have
                long modified = new File(this.playerDir, player.getStringUUID() + ".dat").lastModified();
                if (modified < craftPlayer.getFirstPlayed()) {
                    craftPlayer.setFirstPlayed(modified);
                }
            }

            player.load(tag); // From below
            return tag;
        });
    }

    public Optional<CompoundTag> load(String name, String uuid) {
        // CraftBukkit end
        Optional<CompoundTag> optional = this.load(name, uuid, ".dat"); // CraftBukkit
        if (optional.isEmpty()) {
            this.backup(name, uuid, ".dat"); // CraftBukkit
        }

        return optional.or(() -> this.load(name, uuid, ".dat_old")).map(compoundTag -> { // CraftBukkit
            int dataVersion = NbtUtils.getDataVersion(compoundTag, -1);
            compoundTag = ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.PLAYER, compoundTag, dataVersion, net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion()); // Paper - rewrite data conversion system
            // player.load(compoundTag); // CraftBukkit - handled above
            return compoundTag;
        });
    }

    // CraftBukkit start
    public File getPlayerDir() {
        return this.playerDir;
    }
    // CraftBukkit end
}
