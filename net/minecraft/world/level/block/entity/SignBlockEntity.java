package net.minecraft.world.level.block.entity;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.FilteredText;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class SignBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TEXT_LINE_WIDTH = 90;
    private static final int TEXT_LINE_HEIGHT = 10;
    @Nullable
    public UUID playerWhoMayEdit;
    private SignText frontText = this.createDefaultSignText();
    private SignText backText = this.createDefaultSignText();
    private boolean isWaxed;

    public SignBlockEntity(BlockPos pos, BlockState blockState) {
        this(BlockEntityType.SIGN, pos, blockState);
    }

    public SignBlockEntity(BlockEntityType type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    protected SignText createDefaultSignText() {
        return new SignText();
    }

    public boolean isFacingFrontText(Player player) {
        // Paper start - More Sign Block API
        return this.isFacingFrontText(player.getX(), player.getZ());
    }
    public boolean isFacingFrontText(double x, double z) {
        // Paper end - More Sign Block API
        if (this.getBlockState().getBlock() instanceof SignBlock signBlock) {
            Vec3 signHitboxCenterPosition = signBlock.getSignHitboxCenterPosition(this.getBlockState());
            double d = x - (this.getBlockPos().getX() + signHitboxCenterPosition.x); // Paper - More Sign Block API
            double d1 = z - (this.getBlockPos().getZ() + signHitboxCenterPosition.z); // Paper - More Sign Block AP
            float yRotationDegrees = signBlock.getYRotationDegrees(this.getBlockState());
            float f = (float)(Mth.atan2(d1, d) * 180.0F / (float)Math.PI) - 90.0F;
            return Mth.degreesDifferenceAbs(yRotationDegrees, f) <= 90.0F;
        } else {
            return false;
        }
    }

    public SignText getText(boolean isFrontText) {
        return isFrontText ? this.frontText : this.backText;
    }

    public SignText getFrontText() {
        return this.frontText;
    }

    public SignText getBackText() {
        return this.backText;
    }

    public int getTextLineHeight() {
        return 10;
    }

    public int getMaxTextLineWidth() {
        return 90;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        DynamicOps<Tag> dynamicOps = registries.createSerializationContext(NbtOps.INSTANCE);
        SignText.DIRECT_CODEC
            .encodeStart(dynamicOps, this.frontText)
            .resultOrPartial(LOGGER::error)
            .ifPresent(frontTextTag -> tag.put("front_text", frontTextTag));
        SignText.DIRECT_CODEC.encodeStart(dynamicOps, this.backText).resultOrPartial(LOGGER::error).ifPresent(backTextTag -> tag.put("back_text", backTextTag));
        tag.putBoolean("is_waxed", this.isWaxed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        DynamicOps<Tag> dynamicOps = registries.createSerializationContext(NbtOps.INSTANCE);
        if (tag.contains("front_text")) {
            SignText.DIRECT_CODEC
                .parse(dynamicOps, tag.getCompound("front_text"))
                .resultOrPartial(LOGGER::error)
                .ifPresent(signText -> this.frontText = this.loadLines(signText));
        }

        if (tag.contains("back_text")) {
            SignText.DIRECT_CODEC
                .parse(dynamicOps, tag.getCompound("back_text"))
                .resultOrPartial(LOGGER::error)
                .ifPresent(signText -> this.backText = this.loadLines(signText));
        }

        this.isWaxed = tag.getBoolean("is_waxed");
    }

    private SignText loadLines(SignText text) {
        for (int i = 0; i < 4; i++) {
            Component component = this.loadLine(text.getMessage(i, false));
            Component component1 = this.loadLine(text.getMessage(i, true));
            text = text.setMessage(i, component, component1);
        }

        return text;
    }

    private Component loadLine(Component lineText) {
        if (this.level instanceof ServerLevel serverLevel) {
            try {
                return ComponentUtils.updateForEntity(createCommandSourceStack(null, serverLevel, this.worldPosition), lineText, null, 0);
            } catch (CommandSyntaxException var4) {
            }
        }

        return lineText;
    }

    public void updateSignText(Player player, boolean isFrontText, List<FilteredText> filteredText) {
        if (!this.isWaxed() && player.getUUID().equals(this.getPlayerWhoMayEdit()) && this.level != null) {
            this.updateText(signText -> this.setMessages(player, filteredText, signText, isFrontText), isFrontText); // CraftBukkit
            this.setAllowedPlayerEditor(null);
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        } else {
            LOGGER.warn("Player {} just tried to change non-editable sign", player.getName().getString());
            if (player.distanceToSqr(this.getBlockPos().getX(), this.getBlockPos().getY(), this.getBlockPos().getZ()) < Mth.square(32)) // Paper - Don't send far away sign update
            ((net.minecraft.server.level.ServerPlayer) player).connection.send(this.getUpdatePacket()); // CraftBukkit
        }
    }

    public boolean updateText(UnaryOperator<SignText> updater, boolean isFrontText) {
        SignText text = this.getText(isFrontText);
        return this.setText(updater.apply(text), isFrontText);
    }

    // Purpur start - Signs allow color codes
    private Component translateColors(org.bukkit.entity.Player player, String line, Style style) {
        if (level.purpurConfig.signAllowColors) {
            if (player.hasPermission("purpur.sign.color")) line = line.replaceAll("(?i)&([0-9a-fr])", "\u00a7$1");
            if (player.hasPermission("purpur.sign.style")) line = line.replaceAll("(?i)&([l-or])", "\u00a7$1");
            if (player.hasPermission("purpur.sign.magic")) line = line.replaceAll("(?i)&([kr])", "\u00a7$1");

            return io.papermc.paper.adventure.PaperAdventure.asVanilla(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(line));
        } else {
            return Component.literal(line).setStyle(style);
        }
    }
    // Purpur end - Signs allow color codes

    private SignText setMessages(Player player, List<FilteredText> filteredText, SignText text, boolean front) { // CraftBukkit
        SignText originalText = text; // CraftBukkit
        for (int i = 0; i < filteredText.size(); i++) {
            FilteredText filteredText1 = filteredText.get(i);
            Style style = text.getMessage(i, player.isTextFilteringEnabled()).getStyle();

            org.bukkit.entity.Player craftPlayer =  (org.bukkit.craftbukkit.entity.CraftPlayer) player.getBukkitEntity(); // Purpur - Signs allow color codes
            if (player.isTextFilteringEnabled()) {
                text = text.setMessage(i, translateColors(craftPlayer, net.minecraft.util.StringUtil.filterText(filteredText1.filteredOrEmpty()), style)); // Paper - filter sign text to chat only // Purpur - Signs allow color codes
            } else {
                text = text.setMessage(
                    i, translateColors(craftPlayer, net.minecraft.util.StringUtil.filterText(filteredText1.raw()), style), translateColors(craftPlayer, net.minecraft.util.StringUtil.filterText(filteredText1.filteredOrEmpty()), style) // Paper - filter sign text to chat only // Purpur - Signs allow color codes
                );
            }
        }

        // CraftBukkit start
        org.bukkit.entity.Player apiPlayer = ((net.minecraft.server.level.ServerPlayer) player).getBukkitEntity();
        List<net.kyori.adventure.text.Component> lines = new java.util.ArrayList<>(); // Paper - adventure

        for (int i = 0; i < filteredText.size(); ++i) {
            lines.add(io.papermc.paper.adventure.PaperAdventure.asAdventure(text.getMessage(i, player.isTextFilteringEnabled()))); // Paper - Adventure
        }

        org.bukkit.event.block.SignChangeEvent event = new org.bukkit.event.block.SignChangeEvent(org.bukkit.craftbukkit.block.CraftBlock.at(this.level, this.worldPosition), apiPlayer, new java.util.ArrayList<>(lines), (front) ? org.bukkit.block.sign.Side.FRONT : org.bukkit.block.sign.Side.BACK); // Paper - Adventure
        if (!event.callEvent()) {
            return originalText;
        }

        Component[] components = org.bukkit.craftbukkit.block.CraftSign.sanitizeLines(event.lines()); // Paper - Adventure
        for (int i = 0; i < components.length; i++) {
            if (!java.util.Objects.equals(lines.get(i), event.line(i))) { // Paper - Adventure
                text = text.setMessage(i, components[i]);
            }
        }
        // CraftBukkit end

        return text;
    }

    public boolean setText(SignText text, boolean isFrontText) {
        return isFrontText ? this.setFrontText(text) : this.setBackText(text);
    }

    private boolean setBackText(SignText text) {
        if (text != this.backText) {
            this.backText = text;
            this.markUpdated();
            return true;
        } else {
            return false;
        }
    }

    private boolean setFrontText(SignText text) {
        if (text != this.frontText) {
            this.frontText = text;
            this.markUpdated();
            return true;
        } else {
            return false;
        }
    }

    public boolean canExecuteClickCommands(boolean isFrontText, Player player) {
        return this.isWaxed() && this.getText(isFrontText).hasAnyClickCommands(player);
    }

    public boolean executeClickCommandsIfPresent(Player player, Level level, BlockPos pos, boolean frontText) {
        boolean flag = false;

        for (Component component : this.getText(frontText).getMessages(player.isTextFilteringEnabled())) {
            Style style = component.getStyle();
            ClickEvent clickEvent = style.getClickEvent();
            if (clickEvent != null && clickEvent.getAction() == ClickEvent.Action.RUN_COMMAND) {
                // Paper start - Fix commands from signs not firing command events
                String command = clickEvent.getValue().startsWith("/") ? clickEvent.getValue() : "/" + clickEvent.getValue();
                if (org.spigotmc.SpigotConfig.logCommands)  {
                    LOGGER.info("{} issued server command: {}", player.getScoreboardName(), command);
                }
                io.papermc.paper.event.player.PlayerSignCommandPreprocessEvent event = new io.papermc.paper.event.player.PlayerSignCommandPreprocessEvent(
                    (org.bukkit.entity.Player) player.getBukkitEntity(),
                    command,
                    new org.bukkit.craftbukkit.util.LazyPlayerSet(player.getServer()),
                    (org.bukkit.block.Sign) org.bukkit.craftbukkit.block.CraftBlock.at(this.level, this.worldPosition).getState(),
                    frontText ? org.bukkit.block.sign.Side.FRONT : org.bukkit.block.sign.Side.BACK
                );
                if (!event.callEvent()) {
                    return false;
                }
                player.getServer().getCommands().performPrefixedCommand(this.createCommandSourceStack(((org.bukkit.craftbukkit.entity.CraftPlayer) event.getPlayer()).getHandle(), level, pos), event.getMessage());
                // Paper end - Fix commands from signs not firing command events
                flag = true;
            }
        }

        return flag;
    }

    // CraftBukkit start
    private final CommandSource commandSource = new CommandSource() {

        @Override
        public void sendSystemMessage(Component message) {}

        @Override
        public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack commandSourceStack) {
            return commandSourceStack.getEntity() != null ? commandSourceStack.getEntity().getBukkitEntity() : new org.bukkit.craftbukkit.command.CraftBlockCommandSender(commandSourceStack, SignBlockEntity.this);
        }

        @Override
        public boolean acceptsSuccess() {
            return false;
        }

        @Override
        public boolean acceptsFailure() {
            return false;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    };

    private CommandSourceStack createCommandSourceStack(@Nullable Player player, Level level, BlockPos pos) {
        // CraftBukkit end
        String string = player == null ? "Sign" : player.getName().getString();
        Component component = (Component)(player == null ? Component.literal("Sign") : player.getDisplayName());

        // Paper start - Fix commands from signs not firing command events
        CommandSource commandSource = level.paperConfig().misc.showSignClickCommandFailureMsgsToPlayer ? new io.papermc.paper.commands.DelegatingCommandSource(this.commandSource) {
            @Override
            public void sendSystemMessage(Component message) {
                if (player instanceof final net.minecraft.server.level.ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(message);
                }
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }
        } : this.commandSource;
        // Paper end - Fix commands from signs not firing command events
        // CraftBukkit - this
        return new CommandSourceStack(commandSource, Vec3.atCenterOf(pos), Vec2.ZERO, (ServerLevel)level, 2, string, component, level.getServer(), player); // Paper - Fix commands from signs not firing command events
    }

    // Purpur start - Signs allow color codes
    public ClientboundBlockEntityDataPacket getTranslatedUpdatePacket(boolean filtered, boolean front) {
        final CompoundTag nbt = new CompoundTag();
        this.saveAdditional(nbt, this.getLevel().registryAccess());
        final Component[] lines = front ? frontText.getMessages(filtered) : backText.getMessages(filtered);
        final String side = front ? "front_text" : "back_text";
        for (int i = 0; i < 4; i++) {
            final var component = io.papermc.paper.adventure.PaperAdventure.asAdventure(lines[i]);
            final String line = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().serialize(component);
            final var text = net.kyori.adventure.text.Component.text(line);
            final String json = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(text);
            if (!nbt.contains(side)) nbt.put(side, new CompoundTag());
            final CompoundTag sideNbt = nbt.getCompound(side);
            if (!sideNbt.contains("messages")) sideNbt.put("messages", new net.minecraft.nbt.ListTag());
            final net.minecraft.nbt.ListTag messagesNbt = sideNbt.getList("messages", Tag.TAG_STRING);
            messagesNbt.set(i, net.minecraft.nbt.StringTag.valueOf(json));
        }
        nbt.putString("PurpurEditor", "true");
        return ClientboundBlockEntityDataPacket.create(this, (blockEntity, registryAccess) -> nbt);
    }
    // Purpur end - Signs allow color codes

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public void setAllowedPlayerEditor(@Nullable UUID playWhoMayEdit) {
        this.playerWhoMayEdit = playWhoMayEdit;
    }

    @Nullable
    public UUID getPlayerWhoMayEdit() {
        // CraftBukkit start - unnecessary sign ticking removed, so do this lazily
        if (this.level != null && this.playerWhoMayEdit != null) {
            this.clearInvalidPlayerWhoMayEdit(this, this.level, this.playerWhoMayEdit);
        }
        // CraftBukkit end
        return this.playerWhoMayEdit;
    }

    private void markUpdated() {
        this.setChanged();
        if (this.level != null) this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3); // CraftBukkit - skip notify if world is null (SPIGOT-5122)
    }

    public boolean isWaxed() {
        return this.isWaxed;
    }

    public boolean setWaxed(boolean isWaxed) {
        if (this.isWaxed != isWaxed) {
            this.isWaxed = isWaxed;
            this.markUpdated();
            return true;
        } else {
            return false;
        }
    }

    public boolean playerIsTooFarAwayToEdit(UUID uuid) {
        Player playerByUuid = this.level.getPlayerByUUID(uuid);
        return playerByUuid == null || !playerByUuid.canInteractWithBlock(this.getBlockPos(), 4.0);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SignBlockEntity sign) {
        UUID playerWhoMayEdit = sign.getPlayerWhoMayEdit();
        if (playerWhoMayEdit != null) {
            sign.clearInvalidPlayerWhoMayEdit(sign, level, playerWhoMayEdit);
        }
    }

    private void clearInvalidPlayerWhoMayEdit(SignBlockEntity sign, Level level, UUID uuid) {
        if (sign.playerIsTooFarAwayToEdit(uuid)) {
            sign.setAllowedPlayerEditor(null);
        }
    }

    public SoundEvent getSignInteractionFailedSoundEvent() {
        return SoundEvents.WAXED_SIGN_INTERACT_FAIL;
    }
}
