package net.minecraft.world.level.storage;

import java.util.UUID;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.timers.TimerQueue;

public class DerivedLevelData implements ServerLevelData {
    private final WorldData worldData;
    private final ServerLevelData wrapped;

    public DerivedLevelData(WorldData worldData, ServerLevelData wrapped) {
        this.worldData = worldData;
        this.wrapped = wrapped;
    }

    @Override
    public BlockPos getSpawnPos() {
        return this.wrapped.getSpawnPos();
    }

    @Override
    public float getSpawnAngle() {
        return this.wrapped.getSpawnAngle();
    }

    @Override
    public long getGameTime() {
        return this.wrapped.getGameTime();
    }

    @Override
    public long getDayTime() {
        return this.wrapped.getDayTime();
    }

    @Override
    public String getLevelName() {
        return this.worldData.getLevelName();
    }

    @Override
    public int getClearWeatherTime() {
        return this.wrapped.getClearWeatherTime();
    }

    @Override
    public void setClearWeatherTime(int time) {
    }

    @Override
    public boolean isThundering() {
        return this.wrapped.isThundering();
    }

    @Override
    public int getThunderTime() {
        return this.wrapped.getThunderTime();
    }

    @Override
    public boolean isRaining() {
        return this.wrapped.isRaining();
    }

    @Override
    public int getRainTime() {
        return this.wrapped.getRainTime();
    }

    @Override
    public GameType getGameType() {
        return this.worldData.getGameType();
    }

    @Override
    public void setGameTime(long time) {
    }

    @Override
    public void setDayTime(long time) {
    }

    @Override
    public void setSpawn(BlockPos spawnPoint, float angle) {
    }

    @Override
    public void setThundering(boolean thundering) {
    }

    @Override
    public void setThunderTime(int time) {
    }

    @Override
    public void setRaining(boolean isRaining) {
    }

    @Override
    public void setRainTime(int time) {
    }

    @Override
    public void setGameType(GameType type) {
    }

    @Override
    public boolean isHardcore() {
        return this.worldData.isHardcore();
    }

    @Override
    public boolean isAllowCommands() {
        return this.worldData.isAllowCommands();
    }

    @Override
    public boolean isInitialized() {
        return this.wrapped.isInitialized();
    }

    @Override
    public void setInitialized(boolean initialized) {
    }

    @Override
    public GameRules getGameRules() {
        return this.worldData.getGameRules();
    }

    @Override
    public WorldBorder.Settings getWorldBorder() {
        return this.wrapped.getWorldBorder();
    }

    @Override
    public void setWorldBorder(WorldBorder.Settings serializer) {
    }

    @Override
    public Difficulty getDifficulty() {
        return this.worldData.getDifficulty();
    }

    @Override
    public boolean isDifficultyLocked() {
        return this.worldData.isDifficultyLocked();
    }

    @Override
    public TimerQueue<MinecraftServer> getScheduledEvents() {
        return this.wrapped.getScheduledEvents();
    }

    @Override
    public int getWanderingTraderSpawnDelay() {
        return 0;
    }

    @Override
    public void setWanderingTraderSpawnDelay(int delay) {
    }

    @Override
    public int getWanderingTraderSpawnChance() {
        return 0;
    }

    @Override
    public void setWanderingTraderSpawnChance(int chance) {
    }

    @Override
    public UUID getWanderingTraderId() {
        return null;
    }

    @Override
    public void setWanderingTraderId(UUID id) {
    }

    @Override
    public void fillCrashReportCategory(CrashReportCategory crashReportCategory, LevelHeightAccessor level) {
        crashReportCategory.setDetail("Derived", true);
        this.wrapped.fillCrashReportCategory(crashReportCategory, level);
    }
}
