package net.minecraft.world.scores;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.NumberFormatTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.slf4j.Logger;

public class ScoreboardSaveData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String FILE_ID = "scoreboard";
    private final Scoreboard scoreboard;

    public ScoreboardSaveData(Scoreboard scoreboard) {
        this.scoreboard = scoreboard;
    }

    public ScoreboardSaveData load(CompoundTag tag, HolderLookup.Provider levelRegistry) {
        this.loadObjectives(tag.getList("Objectives", 10), levelRegistry);
        this.scoreboard.loadPlayerScores(tag.getList("PlayerScores", 10), levelRegistry);
        if (tag.contains("DisplaySlots", 10)) {
            this.loadDisplaySlots(tag.getCompound("DisplaySlots"));
        }

        if (tag.contains("Teams", 9)) {
            this.loadTeams(tag.getList("Teams", 10), levelRegistry);
        }

        return this;
    }

    private void loadTeams(ListTag tag, HolderLookup.Provider levelRegistry) {
        for (int i = 0; i < tag.size(); i++) {
            CompoundTag compound = tag.getCompound(i);
            String string = compound.getString("Name");
            PlayerTeam playerTeam = this.scoreboard.addPlayerTeam(string);
            Component component = Component.Serializer.fromJson(compound.getString("DisplayName"), levelRegistry);
            if (component != null) {
                playerTeam.setDisplayName(component);
            }

            if (compound.contains("TeamColor", 8)) {
                playerTeam.setColor(ChatFormatting.getByName(compound.getString("TeamColor")));
            }

            if (compound.contains("AllowFriendlyFire", 99)) {
                playerTeam.setAllowFriendlyFire(compound.getBoolean("AllowFriendlyFire"));
            }

            if (compound.contains("SeeFriendlyInvisibles", 99)) {
                playerTeam.setSeeFriendlyInvisibles(compound.getBoolean("SeeFriendlyInvisibles"));
            }

            if (compound.contains("MemberNamePrefix", 8)) {
                Component component1 = Component.Serializer.fromJson(compound.getString("MemberNamePrefix"), levelRegistry);
                if (component1 != null) {
                    playerTeam.setPlayerPrefix(component1);
                }
            }

            if (compound.contains("MemberNameSuffix", 8)) {
                Component component1 = Component.Serializer.fromJson(compound.getString("MemberNameSuffix"), levelRegistry);
                if (component1 != null) {
                    playerTeam.setPlayerSuffix(component1);
                }
            }

            if (compound.contains("NameTagVisibility", 8)) {
                Team.Visibility visibility = Team.Visibility.byName(compound.getString("NameTagVisibility"));
                if (visibility != null) {
                    playerTeam.setNameTagVisibility(visibility);
                }
            }

            if (compound.contains("DeathMessageVisibility", 8)) {
                Team.Visibility visibility = Team.Visibility.byName(compound.getString("DeathMessageVisibility"));
                if (visibility != null) {
                    playerTeam.setDeathMessageVisibility(visibility);
                }
            }

            if (compound.contains("CollisionRule", 8)) {
                Team.CollisionRule collisionRule = Team.CollisionRule.byName(compound.getString("CollisionRule"));
                if (collisionRule != null) {
                    playerTeam.setCollisionRule(collisionRule);
                }
            }

            this.loadTeamPlayers(playerTeam, compound.getList("Players", 8));
        }
    }

    private void loadTeamPlayers(PlayerTeam playerTeam, ListTag tagList) {
        for (int i = 0; i < tagList.size(); i++) {
            this.scoreboard.addPlayerToTeam(tagList.getString(i), playerTeam);
        }
    }

    private void loadDisplaySlots(CompoundTag compound) {
        for (String string : compound.getAllKeys()) {
            DisplaySlot displaySlot = DisplaySlot.CODEC.byName(string);
            if (displaySlot != null) {
                String string1 = compound.getString(string);
                Objective objective = this.scoreboard.getObjective(string1);
                this.scoreboard.setDisplayObjective(displaySlot, objective);
            }
        }
    }

    private void loadObjectives(ListTag tag, HolderLookup.Provider levelRegistry) {
        for (int i = 0; i < tag.size(); i++) {
            CompoundTag compound = tag.getCompound(i);
            String string = compound.getString("CriteriaName");
            ObjectiveCriteria objectiveCriteria = ObjectiveCriteria.byName(string).orElseGet(() -> {
                LOGGER.warn("Unknown scoreboard criteria {}, replacing with {}", string, ObjectiveCriteria.DUMMY.getName());
                return ObjectiveCriteria.DUMMY;
            });
            String string1 = compound.getString("Name");
            Component component = Component.Serializer.fromJson(compound.getString("DisplayName"), levelRegistry);
            ObjectiveCriteria.RenderType renderType = ObjectiveCriteria.RenderType.byId(compound.getString("RenderType"));
            boolean _boolean = compound.getBoolean("display_auto_update");
            NumberFormat numberFormat = NumberFormatTypes.CODEC
                .parse(levelRegistry.createSerializationContext(NbtOps.INSTANCE), compound.get("format"))
                .result()
                .orElse(null);
            this.scoreboard.addObjective(string1, objectiveCriteria, component, renderType, _boolean, numberFormat);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Objectives", this.saveObjectives(registries));
        tag.put("PlayerScores", this.scoreboard.savePlayerScores(registries));
        tag.put("Teams", this.saveTeams(registries));
        this.saveDisplaySlots(tag);
        return tag;
    }

    private ListTag saveTeams(HolderLookup.Provider levelRegistry) {
        ListTag listTag = new ListTag();

        for (PlayerTeam playerTeam : this.scoreboard.getPlayerTeams()) {
            if (!io.papermc.paper.configuration.GlobalConfiguration.get().scoreboards.saveEmptyScoreboardTeams && playerTeam.getPlayers().isEmpty()) continue; // Paper - Don't save empty scoreboard teams to scoreboard.dat
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.putString("Name", playerTeam.getName());
            compoundTag.putString("DisplayName", Component.Serializer.toJson(playerTeam.getDisplayName(), levelRegistry));
            if (playerTeam.getColor().getId() >= 0) {
                compoundTag.putString("TeamColor", playerTeam.getColor().getName());
            }

            compoundTag.putBoolean("AllowFriendlyFire", playerTeam.isAllowFriendlyFire());
            compoundTag.putBoolean("SeeFriendlyInvisibles", playerTeam.canSeeFriendlyInvisibles());
            compoundTag.putString("MemberNamePrefix", Component.Serializer.toJson(playerTeam.getPlayerPrefix(), levelRegistry));
            compoundTag.putString("MemberNameSuffix", Component.Serializer.toJson(playerTeam.getPlayerSuffix(), levelRegistry));
            compoundTag.putString("NameTagVisibility", playerTeam.getNameTagVisibility().name);
            compoundTag.putString("DeathMessageVisibility", playerTeam.getDeathMessageVisibility().name);
            compoundTag.putString("CollisionRule", playerTeam.getCollisionRule().name);
            ListTag listTag1 = new ListTag();

            for (String string : playerTeam.getPlayers()) {
                listTag1.add(StringTag.valueOf(string));
            }

            compoundTag.put("Players", listTag1);
            listTag.add(compoundTag);
        }

        return listTag;
    }

    private void saveDisplaySlots(CompoundTag compound) {
        CompoundTag compoundTag = new CompoundTag();

        for (DisplaySlot displaySlot : DisplaySlot.values()) {
            Objective displayObjective = this.scoreboard.getDisplayObjective(displaySlot);
            if (displayObjective != null) {
                compoundTag.putString(displaySlot.getSerializedName(), displayObjective.getName());
            }
        }

        if (!compoundTag.isEmpty()) {
            compound.put("DisplaySlots", compoundTag);
        }
    }

    private ListTag saveObjectives(HolderLookup.Provider levelRegistry) {
        ListTag listTag = new ListTag();

        for (Objective objective : this.scoreboard.getObjectives()) {
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.putString("Name", objective.getName());
            compoundTag.putString("CriteriaName", objective.getCriteria().getName());
            compoundTag.putString("DisplayName", Component.Serializer.toJson(objective.getDisplayName(), levelRegistry));
            compoundTag.putString("RenderType", objective.getRenderType().getId());
            compoundTag.putBoolean("display_auto_update", objective.displayAutoUpdate());
            NumberFormat numberFormat = objective.numberFormat();
            if (numberFormat != null) {
                NumberFormatTypes.CODEC
                    .encodeStart(levelRegistry.createSerializationContext(NbtOps.INSTANCE), numberFormat)
                    .ifSuccess(tag -> compoundTag.put("format", tag));
            }

            listTag.add(compoundTag);
        }

        return listTag;
    }
}
