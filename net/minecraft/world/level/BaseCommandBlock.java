package net.minecraft.world.level;

import java.text.SimpleDateFormat;
import java.util.Date;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public abstract class BaseCommandBlock implements CommandSource {
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private static final Component DEFAULT_NAME = Component.literal("@");
    private long lastExecution = -1L;
    private boolean updateLastExecution = true;
    private int successCount;
    private boolean trackOutput = true;
    @Nullable
    private Component lastOutput;
    private String command = "";
    @Nullable
    private Component customName;
    // CraftBukkit start
    @Override
    public abstract org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper);
    // CraftBukkit end


    public int getSuccessCount() {
        return this.successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public Component getLastOutput() {
        return this.lastOutput == null ? CommonComponents.EMPTY : this.lastOutput;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider levelRegistry) {
        tag.putString("Command", this.command);
        tag.putInt("SuccessCount", this.successCount);
        if (this.customName != null) {
            tag.putString("CustomName", Component.Serializer.toJson(this.customName, levelRegistry));
        }

        tag.putBoolean("TrackOutput", this.trackOutput);
        if (this.lastOutput != null && this.trackOutput) {
            tag.putString("LastOutput", Component.Serializer.toJson(this.lastOutput, levelRegistry));
        }

        tag.putBoolean("UpdateLastExecution", this.updateLastExecution);
        if (this.updateLastExecution && this.lastExecution > 0L) {
            tag.putLong("LastExecution", this.lastExecution);
        }

        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider levelRegistry) {
        this.command = tag.getString("Command");
        this.successCount = tag.getInt("SuccessCount");
        if (tag.contains("CustomName", 8)) {
            this.setCustomName(BlockEntity.parseCustomNameSafe(tag.getString("CustomName"), levelRegistry));
        } else {
            this.setCustomName(null);
        }

        if (tag.contains("TrackOutput", 1)) {
            this.trackOutput = tag.getBoolean("TrackOutput");
        }

        if (tag.contains("LastOutput", 8) && this.trackOutput) {
            try {
                this.lastOutput = Component.Serializer.fromJson(tag.getString("LastOutput"), levelRegistry);
            } catch (Throwable var4) {
                this.lastOutput = Component.literal(var4.getMessage());
            }
        } else {
            this.lastOutput = null;
        }

        if (tag.contains("UpdateLastExecution")) {
            this.updateLastExecution = tag.getBoolean("UpdateLastExecution");
        }

        if (this.updateLastExecution && tag.contains("LastExecution")) {
            this.lastExecution = tag.getLong("LastExecution");
        } else {
            this.lastExecution = -1L;
        }
    }

    public void setCommand(String command) {
        this.command = command;
        this.successCount = 0;
    }

    public String getCommand() {
        return this.command;
    }

    public boolean performCommand(Level level) {
        if (level.isClientSide || level.getGameTime() == this.lastExecution) {
            return false;
        } else if ("Searge".equalsIgnoreCase(this.command)) {
            this.lastOutput = Component.literal("#itzlipofutzli");
            this.successCount = 1;
            return true;
        } else {
            this.successCount = 0;
            MinecraftServer server = this.getLevel().getServer();
            if (server.isCommandBlockEnabled() && !StringUtil.isNullOrEmpty(this.command)) {
                try {
                    this.lastOutput = null;
                    CommandSourceStack commandSourceStack = this.createCommandSourceStack().withCallback((success, result) -> {
                        if (success) {
                            this.successCount++;
                        }
                    });
                    server.getCommands().dispatchServerCommand(commandSourceStack, this.command); // CraftBukkit
                } catch (Throwable var6) {
                    CrashReport crashReport = CrashReport.forThrowable(var6, "Executing command block");
                    CrashReportCategory crashReportCategory = crashReport.addCategory("Command to be executed");
                    crashReportCategory.setDetail("Command", this::getCommand);
                    crashReportCategory.setDetail("Name", () -> this.getName().getString());
                    throw new ReportedException(crashReport);
                }
            }

            if (this.updateLastExecution) {
                this.lastExecution = level.getGameTime();
            } else {
                this.lastExecution = -1L;
            }

            return true;
        }
    }

    public Component getName() {
        return this.customName != null ? this.customName : DEFAULT_NAME;
    }

    @Nullable
    public Component getCustomName() {
        return this.customName;
    }

    public void setCustomName(@Nullable Component customName) {
        this.customName = customName;
    }

    @Override
    public void sendSystemMessage(Component component) {
        if (this.trackOutput) {
            org.spigotmc.AsyncCatcher.catchOp("sendSystemMessage to a command block"); // Paper - Don't broadcast messages to command blocks
            this.lastOutput = Component.literal("[" + TIME_FORMAT.format(new Date()) + "] ").append(component);
            this.onUpdated();
        }
    }

    public abstract ServerLevel getLevel();

    public abstract void onUpdated();

    public void setLastOutput(@Nullable Component lastOutputMessage) {
        this.lastOutput = lastOutputMessage;
    }

    public void setTrackOutput(boolean shouldTrackOutput) {
        this.trackOutput = shouldTrackOutput;
    }

    public boolean isTrackOutput() {
        return this.trackOutput;
    }

    public InteractionResult usedBy(Player player) {
        if (!player.canUseGameMasterBlocks() && (!player.isCreative() || !player.getBukkitEntity().hasPermission("minecraft.commandblock"))) { // Paper - command block permission
            return InteractionResult.PASS;
        } else {
            if (player.getCommandSenderWorld().isClientSide) {
                player.openMinecartCommandBlock(this);
            }

            return InteractionResult.SUCCESS;
        }
    }

    public abstract Vec3 getPosition();

    public abstract CommandSourceStack createCommandSourceStack();

    @Override
    public boolean acceptsSuccess() {
        return this.getLevel().getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK) && this.trackOutput;
    }

    @Override
    public boolean acceptsFailure() {
        return this.trackOutput;
    }

    @Override
    public boolean shouldInformAdmins() {
        return this.getLevel().getGameRules().getBoolean(GameRules.RULE_COMMANDBLOCKOUTPUT);
    }

    public abstract boolean isValid();
}
