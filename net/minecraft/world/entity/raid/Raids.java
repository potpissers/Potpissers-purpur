package net.minecraft.world.entity.raid;

import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

public class Raids extends SavedData {
    private static final String RAID_FILE_ID = "raids";
    public final Map<java.util.UUID, Integer> playerCooldowns = Maps.newHashMap(); // Purpur - Raid cooldown setting
    public final Map<Integer, Raid> raidMap = Maps.newHashMap();
    private final ServerLevel level;
    private int nextAvailableID;
    private int tick;

    public static SavedData.Factory<Raids> factory(ServerLevel level) {
        return new SavedData.Factory<>(() -> new Raids(level), (compoundTag, provider) -> load(level, compoundTag), DataFixTypes.SAVED_DATA_RAIDS);
    }

    public Raids(ServerLevel level) {
        this.level = level;
        this.nextAvailableID = 1;
        this.setDirty();
    }

    public Raid get(int id) {
        return this.raidMap.get(id);
    }

    public void tick() {
        this.tick++;
        // Purpur start - Raid cooldown setting
        if (level.purpurConfig.raidCooldownSeconds != 0 && this.tick % 20 == 0) {
            com.google.common.collect.ImmutableMap.copyOf(playerCooldowns).forEach((uuid, i) -> {
                if (i < 1) {
                    playerCooldowns.remove(uuid);
                } else {
                    playerCooldowns.put(uuid, i - 1);
                }
            });
        }
        // Purpur end - Raid cooldown setting
        Iterator<Raid> iterator = this.raidMap.values().iterator();

        while (iterator.hasNext()) {
            Raid raid = iterator.next();
            if (this.level.getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS)) {
                raid.stop();
            }

            if (raid.isStopped()) {
                iterator.remove();
                this.setDirty();
            } else {
                raid.tick();
            }
        }

        if (this.tick % 200 == 0) {
            this.setDirty();
        }

        DebugPackets.sendRaids(this.level, this.raidMap.values());
    }

    public static boolean canJoinRaid(Raider raider, Raid raid) {
        return raider != null
            && raid != null
            && raid.getLevel() != null
            && raider.isAlive()
            && raider.canJoinRaid()
            && raider.getNoActionTime() <= 2400
            && raider.level().dimensionType() == raid.getLevel().dimensionType();
    }

    @Nullable
    public Raid createOrExtendRaid(ServerPlayer player, BlockPos pos) {
        if (player.isSpectator()) {
            return null;
        } else if (this.level.getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS)) {
            return null;
        } else {
            DimensionType dimensionType = player.level().dimensionType();
            if (!dimensionType.hasRaids()) {
                return null;
            } else {
                List<PoiRecord> list = this.level
                    .getPoiManager()
                    .getInRange(holder -> holder.is(PoiTypeTags.VILLAGE), pos, 64, PoiManager.Occupancy.IS_OCCUPIED)
                    .toList();
                int i = 0;
                Vec3 vec3 = Vec3.ZERO;

                for (PoiRecord poiRecord : list) {
                    BlockPos pos1 = poiRecord.getPos();
                    vec3 = vec3.add(pos1.getX(), pos1.getY(), pos1.getZ());
                    i++;
                }

                BlockPos blockPos;
                if (i > 0) {
                    vec3 = vec3.scale(1.0 / i);
                    blockPos = BlockPos.containing(vec3);
                } else {
                    blockPos = pos;
                }

                Raid raid = this.getOrCreateRaid(player.serverLevel(), blockPos);
                /* CraftBukkit - moved down
                if (!raid.isStarted() && !this.raidMap.containsKey(raid.getId())) {
                    this.raidMap.put(raid.getId(), raid);
                }
                */

                if (!raid.isStarted() || (raid.isInProgress() && raid.getRaidOmenLevel() < raid.getMaxRaidOmenLevel())) { // CraftBukkit - fixed a bug with raid: players could add up Bad Omen level even when the raid had finished
                    if (level.purpurConfig.raidCooldownSeconds != 0 && playerCooldowns.containsKey(player.getUUID())) return null; // Purpur - Raid cooldown setting
                    // CraftBukkit start
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.callRaidTriggerEvent(raid, player)) {
                        player.removeEffect(net.minecraft.world.effect.MobEffects.RAID_OMEN);
                        return null;
                    }
                    if (level.purpurConfig.raidCooldownSeconds != 0) playerCooldowns.put(player.getUUID(), level.purpurConfig.raidCooldownSeconds); // Purpur - Raid cooldown setting

                    if (!raid.isStarted() && !this.raidMap.containsKey(raid.getId())) {
                        this.raidMap.put(raid.getId(), raid);
                    }
                    // CraftBukkit end
                    raid.absorbRaidOmen(player);
                }

                this.setDirty();
                return raid;
            }
        }
    }

    private Raid getOrCreateRaid(ServerLevel serverLevel, BlockPos pos) {
        Raid raidAt = serverLevel.getRaidAt(pos);
        return raidAt != null ? raidAt : new Raid(this.getUniqueId(), serverLevel, pos);
    }

    public static Raids load(ServerLevel level, CompoundTag tag) {
        Raids raids = new Raids(level);
        raids.nextAvailableID = tag.getInt("NextAvailableID");
        raids.tick = tag.getInt("Tick");
        ListTag list = tag.getList("Raids", 10);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag compound = list.getCompound(i);
            Raid raid = new Raid(level, compound);
            raids.raidMap.put(raid.getId(), raid);
        }

        return raids;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("NextAvailableID", this.nextAvailableID);
        tag.putInt("Tick", this.tick);
        ListTag listTag = new ListTag();

        for (Raid raid : this.raidMap.values()) {
            CompoundTag compoundTag = new CompoundTag();
            raid.save(compoundTag);
            listTag.add(compoundTag);
        }

        tag.put("Raids", listTag);
        return tag;
    }

    public static String getFileId(Holder<DimensionType> dimensionTypeHolder) {
        return dimensionTypeHolder.is(BuiltinDimensionTypes.END) ? "raids_end" : "raids";
    }

    private int getUniqueId() {
        return ++this.nextAvailableID;
    }

    @Nullable
    public Raid getNearbyRaid(BlockPos pos, int distance) {
        Raid raid = null;
        double d = distance;

        for (Raid raid1 : this.raidMap.values()) {
            double d1 = raid1.getCenter().distSqr(pos);
            if (raid1.isActive() && d1 < d) {
                raid = raid1;
                d = d1;
            }
        }

        return raid;
    }
}
