package net.minecraft.world.level.storage;

import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.CrashReportCategory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.timers.TimerQueue;

public interface ServerLevelData extends WritableLevelData {
    String getLevelName();

    void setThundering(boolean thundering);

    int getRainTime();

    void setRainTime(int time);

    void setThunderTime(int time);

    int getThunderTime();

    @Override
    default void fillCrashReportCategory(CrashReportCategory crashReportCategory, LevelHeightAccessor level) {
        WritableLevelData.super.fillCrashReportCategory(crashReportCategory, level);
        crashReportCategory.setDetail("Level name", this::getLevelName);
        crashReportCategory.setDetail(
            "Level game mode",
            () -> String.format(
                Locale.ROOT,
                "Game mode: %s (ID %d). Hardcore: %b. Commands: %b",
                this.getGameType().getName(),
                this.getGameType().getId(),
                this.isHardcore(),
                this.isAllowCommands()
            )
        );
        crashReportCategory.setDetail(
            "Level weather",
            () -> String.format(
                Locale.ROOT,
                "Rain time: %d (now: %b), thunder time: %d (now: %b)",
                this.getRainTime(),
                this.isRaining(),
                this.getThunderTime(),
                this.isThundering()
            )
        );
    }

    int getClearWeatherTime();

    void setClearWeatherTime(int time);

    int getWanderingTraderSpawnDelay();

    void setWanderingTraderSpawnDelay(int delay);

    int getWanderingTraderSpawnChance();

    void setWanderingTraderSpawnChance(int chance);

    @Nullable
    UUID getWanderingTraderId();

    void setWanderingTraderId(UUID id);

    GameType getGameType();

    void setWorldBorder(WorldBorder.Settings serializer);

    WorldBorder.Settings getWorldBorder();

    boolean isInitialized();

    void setInitialized(boolean initialized);

    boolean isAllowCommands();

    void setGameType(GameType type);

    TimerQueue<MinecraftServer> getScheduledEvents();

    void setGameTime(long time);

    void setDayTime(long time);

    GameRules getGameRules();
}
