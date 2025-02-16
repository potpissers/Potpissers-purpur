package net.minecraft.server.level;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class ServerPlayerGameMode {
    private static final Logger LOGGER = LogUtils.getLogger();
    protected ServerLevel level;
    protected final ServerPlayer player;
    private GameType gameModeForPlayer = GameType.DEFAULT_MODE;
    @Nullable
    private GameType previousGameModeForPlayer;
    private boolean isDestroyingBlock;
    private int destroyProgressStart;
    private BlockPos destroyPos = BlockPos.ZERO;
    private int gameTicks;
    private boolean hasDelayedDestroy;
    private BlockPos delayedDestroyPos = BlockPos.ZERO;
    private int delayedTickStart;
    private int lastSentState = -1;

    public ServerPlayerGameMode(ServerPlayer player) {
        this.player = player;
        this.level = player.serverLevel();
    }

    public boolean changeGameModeForPlayer(GameType gameModeForPlayer) {
        if (gameModeForPlayer == this.gameModeForPlayer) {
            return false;
        } else {
            this.setGameModeForPlayer(gameModeForPlayer, this.previousGameModeForPlayer);
            this.player.onUpdateAbilities();
            this.player
                .server
                .getPlayerList()
                .broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE, this.player));
            this.level.updateSleepingPlayerList();
            if (gameModeForPlayer == GameType.CREATIVE) {
                this.player.resetCurrentImpulseContext();
            }

            return true;
        }
    }

    protected void setGameModeForPlayer(GameType gameModeForPlayer, @Nullable GameType previousGameModeForPlayer) {
        this.previousGameModeForPlayer = previousGameModeForPlayer;
        this.gameModeForPlayer = gameModeForPlayer;
        gameModeForPlayer.updatePlayerAbilities(this.player.getAbilities());
    }

    public GameType getGameModeForPlayer() {
        return this.gameModeForPlayer;
    }

    @Nullable
    public GameType getPreviousGameModeForPlayer() {
        return this.previousGameModeForPlayer;
    }

    public boolean isSurvival() {
        return this.gameModeForPlayer.isSurvival();
    }

    public boolean isCreative() {
        return this.gameModeForPlayer.isCreative();
    }

    public void tick() {
        this.gameTicks++;
        if (this.hasDelayedDestroy) {
            BlockState blockState = this.level.getBlockState(this.delayedDestroyPos);
            if (blockState.isAir()) {
                this.hasDelayedDestroy = false;
            } else {
                float f = this.incrementDestroyProgress(blockState, this.delayedDestroyPos, this.delayedTickStart);
                if (f >= 1.0F) {
                    this.hasDelayedDestroy = false;
                    this.destroyBlock(this.delayedDestroyPos);
                }
            }
        } else if (this.isDestroyingBlock) {
            BlockState blockState = this.level.getBlockState(this.destroyPos);
            if (blockState.isAir()) {
                this.level.destroyBlockProgress(this.player.getId(), this.destroyPos, -1);
                this.lastSentState = -1;
                this.isDestroyingBlock = false;
            } else {
                this.incrementDestroyProgress(blockState, this.destroyPos, this.destroyProgressStart);
            }
        }
    }

    private float incrementDestroyProgress(BlockState state, BlockPos pos, int startTick) {
        int i = this.gameTicks - startTick;
        float f = state.getDestroyProgress(this.player, this.player.level(), pos) * (i + 1);
        int i1 = (int)(f * 10.0F);
        if (i1 != this.lastSentState) {
            this.level.destroyBlockProgress(this.player.getId(), pos, i1);
            this.lastSentState = i1;
        }

        return f;
    }

    private void debugLogging(BlockPos pos, boolean terminate, int sequence, String message) {
    }

    public void handleBlockBreakAction(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction face, int maxBuildHeight, int sequence) {
        if (!this.player.canInteractWithBlock(pos, 1.0)) {
            this.debugLogging(pos, false, sequence, "too far");
        } else if (pos.getY() > maxBuildHeight) {
            this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
            this.debugLogging(pos, false, sequence, "too high");
        } else {
            if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
                if (!this.level.mayInteract(this.player, pos)) {
                    this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
                    this.debugLogging(pos, false, sequence, "may not interact");
                    return;
                }

                if (this.isCreative()) {
                    this.destroyAndAck(pos, sequence, "creative destroy");
                    return;
                }

                if (this.player.blockActionRestricted(this.level, pos, this.gameModeForPlayer)) {
                    this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
                    this.debugLogging(pos, false, sequence, "block action restricted");
                    return;
                }

                this.destroyProgressStart = this.gameTicks;
                float f = 1.0F;
                BlockState blockState = this.level.getBlockState(pos);
                if (!blockState.isAir()) {
                    EnchantmentHelper.onHitBlock(
                        this.level,
                        this.player.getMainHandItem(),
                        this.player,
                        this.player,
                        EquipmentSlot.MAINHAND,
                        Vec3.atCenterOf(pos),
                        blockState,
                        item -> this.player.onEquippedItemBroken(item, EquipmentSlot.MAINHAND)
                    );
                    blockState.attack(this.level, pos, this.player);
                    f = blockState.getDestroyProgress(this.player, this.player.level(), pos);
                }

                if (!blockState.isAir() && f >= 1.0F) {
                    this.destroyAndAck(pos, sequence, "insta mine");
                } else {
                    if (this.isDestroyingBlock) {
                        this.player.connection.send(new ClientboundBlockUpdatePacket(this.destroyPos, this.level.getBlockState(this.destroyPos)));
                        this.debugLogging(pos, false, sequence, "abort destroying since another started (client insta mine, server disagreed)");
                    }

                    this.isDestroyingBlock = true;
                    this.destroyPos = pos.immutable();
                    int i = (int)(f * 10.0F);
                    this.level.destroyBlockProgress(this.player.getId(), pos, i);
                    this.debugLogging(pos, true, sequence, "actual start of destroying");
                    this.lastSentState = i;
                }
            } else if (action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
                if (pos.equals(this.destroyPos)) {
                    int i1 = this.gameTicks - this.destroyProgressStart;
                    BlockState blockStatex = this.level.getBlockState(pos);
                    if (!blockStatex.isAir()) {
                        float f1 = blockStatex.getDestroyProgress(this.player, this.player.level(), pos) * (i1 + 1);
                        if (f1 >= 0.7F) {
                            this.isDestroyingBlock = false;
                            this.level.destroyBlockProgress(this.player.getId(), pos, -1);
                            this.destroyAndAck(pos, sequence, "destroyed");
                            return;
                        }

                        if (!this.hasDelayedDestroy) {
                            this.isDestroyingBlock = false;
                            this.hasDelayedDestroy = true;
                            this.delayedDestroyPos = pos;
                            this.delayedTickStart = this.destroyProgressStart;
                        }
                    }
                }

                this.debugLogging(pos, true, sequence, "stopped destroying");
            } else if (action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
                this.isDestroyingBlock = false;
                if (!Objects.equals(this.destroyPos, pos)) {
                    LOGGER.warn("Mismatch in destroy block pos: {} {}", this.destroyPos, pos);
                    this.level.destroyBlockProgress(this.player.getId(), this.destroyPos, -1);
                    this.debugLogging(pos, true, sequence, "aborted mismatched destroying");
                }

                this.level.destroyBlockProgress(this.player.getId(), pos, -1);
                this.debugLogging(pos, true, sequence, "aborted destroying");
            }
        }
    }

    public void destroyAndAck(BlockPos pos, int sequence, String message) {
        if (this.destroyBlock(pos)) {
            this.debugLogging(pos, true, sequence, message);
        } else {
            this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
            this.debugLogging(pos, false, sequence, message);
        }
    }

    public boolean destroyBlock(BlockPos pos) {
        BlockState blockState = this.level.getBlockState(pos);
        if (!this.player.getMainHandItem().getItem().canAttackBlock(blockState, this.level, pos, this.player)) {
            return false;
        } else {
            BlockEntity blockEntity = this.level.getBlockEntity(pos);
            Block block = blockState.getBlock();
            if (block instanceof GameMasterBlock && !this.player.canUseGameMasterBlocks()) {
                this.level.sendBlockUpdated(pos, blockState, blockState, 3);
                return false;
            } else if (this.player.blockActionRestricted(this.level, pos, this.gameModeForPlayer)) {
                return false;
            } else {
                BlockState blockState1 = block.playerWillDestroy(this.level, pos, blockState, this.player);
                boolean flag = this.level.removeBlock(pos, false);
                if (flag) {
                    block.destroy(this.level, pos, blockState1);
                }

                if (this.isCreative()) {
                    return true;
                } else {
                    ItemStack mainHandItem = this.player.getMainHandItem();
                    ItemStack itemStack = mainHandItem.copy();
                    boolean hasCorrectToolForDrops = this.player.hasCorrectToolForDrops(blockState1);
                    mainHandItem.mineBlock(this.level, blockState1, pos, this.player);
                    if (flag && hasCorrectToolForDrops) {
                        block.playerDestroy(this.level, this.player, pos, blockState1, blockEntity, itemStack);
                    }

                    return true;
                }
            }
        }
    }

    public InteractionResult useItem(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand) {
        if (this.gameModeForPlayer == GameType.SPECTATOR) {
            return InteractionResult.PASS;
        } else if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.PASS;
        } else {
            int count = stack.getCount();
            int damageValue = stack.getDamageValue();
            InteractionResult interactionResult = stack.use(level, player, hand);
            ItemStack itemStack;
            if (interactionResult instanceof InteractionResult.Success success) {
                itemStack = Objects.requireNonNullElse(success.heldItemTransformedTo(), player.getItemInHand(hand));
            } else {
                itemStack = player.getItemInHand(hand);
            }

            if (itemStack == stack && itemStack.getCount() == count && itemStack.getUseDuration(player) <= 0 && itemStack.getDamageValue() == damageValue) {
                return interactionResult;
            } else if (interactionResult instanceof InteractionResult.Fail && itemStack.getUseDuration(player) > 0 && !player.isUsingItem()) {
                return interactionResult;
            } else {
                if (stack != itemStack) {
                    player.setItemInHand(hand, itemStack);
                }

                if (itemStack.isEmpty()) {
                    player.setItemInHand(hand, ItemStack.EMPTY);
                }

                if (!player.isUsingItem()) {
                    player.inventoryMenu.sendAllDataToRemote();
                }

                return interactionResult;
            }
        }
    }

    public InteractionResult useItemOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hitResult) {
        BlockPos blockPos = hitResult.getBlockPos();
        BlockState blockState = level.getBlockState(blockPos);
        if (!blockState.getBlock().isEnabled(level.enabledFeatures())) {
            return InteractionResult.FAIL;
        } else if (this.gameModeForPlayer == GameType.SPECTATOR) {
            MenuProvider menuProvider = blockState.getMenuProvider(level, blockPos);
            if (menuProvider != null) {
                player.openMenu(menuProvider);
                return InteractionResult.CONSUME;
            } else {
                return InteractionResult.PASS;
            }
        } else {
            boolean flag = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
            boolean flag1 = player.isSecondaryUseActive() && flag;
            ItemStack itemStack = stack.copy();
            if (!flag1) {
                InteractionResult interactionResult = blockState.useItemOn(player.getItemInHand(hand), level, player, hand, hitResult);
                if (interactionResult.consumesAction()) {
                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, blockPos, itemStack);
                    return interactionResult;
                }

                if (interactionResult instanceof InteractionResult.TryEmptyHandInteraction && hand == InteractionHand.MAIN_HAND) {
                    InteractionResult interactionResult1 = blockState.useWithoutItem(level, player, hitResult);
                    if (interactionResult1.consumesAction()) {
                        CriteriaTriggers.DEFAULT_BLOCK_USE.trigger(player, blockPos);
                        return interactionResult1;
                    }
                }
            }

            if (!stack.isEmpty() && !player.getCooldowns().isOnCooldown(stack)) {
                UseOnContext useOnContext = new UseOnContext(player, hand, hitResult);
                InteractionResult interactionResult1;
                if (this.isCreative()) {
                    int count = stack.getCount();
                    interactionResult1 = stack.useOn(useOnContext);
                    stack.setCount(count);
                } else {
                    interactionResult1 = stack.useOn(useOnContext);
                }

                if (interactionResult1.consumesAction()) {
                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, blockPos, itemStack);
                }

                return interactionResult1;
            } else {
                return InteractionResult.PASS;
            }
        }
    }

    public void setLevel(ServerLevel serverLevel) {
        this.level = serverLevel;
    }
}
