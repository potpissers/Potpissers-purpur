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
        if (this.getBlockState().getBlock() instanceof SignBlock signBlock) {
            Vec3 signHitboxCenterPosition = signBlock.getSignHitboxCenterPosition(this.getBlockState());
            double d = player.getX() - (this.getBlockPos().getX() + signHitboxCenterPosition.x);
            double d1 = player.getZ() - (this.getBlockPos().getZ() + signHitboxCenterPosition.z);
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
            this.updateText(signText -> this.setMessages(player, filteredText, signText), isFrontText);
            this.setAllowedPlayerEditor(null);
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        } else {
            LOGGER.warn("Player {} just tried to change non-editable sign", player.getName().getString());
        }
    }

    public boolean updateText(UnaryOperator<SignText> updater, boolean isFrontText) {
        SignText text = this.getText(isFrontText);
        return this.setText(updater.apply(text), isFrontText);
    }

    private SignText setMessages(Player player, List<FilteredText> filteredText, SignText text) {
        for (int i = 0; i < filteredText.size(); i++) {
            FilteredText filteredText1 = filteredText.get(i);
            Style style = text.getMessage(i, player.isTextFilteringEnabled()).getStyle();
            if (player.isTextFilteringEnabled()) {
                text = text.setMessage(i, Component.literal(filteredText1.filteredOrEmpty()).setStyle(style));
            } else {
                text = text.setMessage(
                    i, Component.literal(filteredText1.raw()).setStyle(style), Component.literal(filteredText1.filteredOrEmpty()).setStyle(style)
                );
            }
        }

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
                player.getServer().getCommands().performPrefixedCommand(createCommandSourceStack(player, level, pos), clickEvent.getValue());
                flag = true;
            }
        }

        return flag;
    }

    private static CommandSourceStack createCommandSourceStack(@Nullable Player player, Level level, BlockPos pos) {
        String string = player == null ? "Sign" : player.getName().getString();
        Component component = (Component)(player == null ? Component.literal("Sign") : player.getDisplayName());
        return new CommandSourceStack(CommandSource.NULL, Vec3.atCenterOf(pos), Vec2.ZERO, (ServerLevel)level, 2, string, component, level.getServer(), player);
    }

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
        return this.playerWhoMayEdit;
    }

    private void markUpdated() {
        this.setChanged();
        this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
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
