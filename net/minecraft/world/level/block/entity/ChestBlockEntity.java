package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public class ChestBlockEntity extends RandomizableContainerBlockEntity implements LidBlockEntity {
    private static final int EVENT_SET_OPEN_COUNT = 1;
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            ChestBlockEntity.playSound(level, pos, state, SoundEvents.CHEST_OPEN);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            ChestBlockEntity.playSound(level, pos, state, SoundEvents.CHEST_CLOSE);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int count, int openCount) {
            ChestBlockEntity.this.signalOpenCount(level, pos, state, count, openCount);
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            if (!(player.containerMenu instanceof ChestMenu)) {
                return false;
            } else {
                Container container = ((ChestMenu)player.containerMenu).getContainer();
                return container == ChestBlockEntity.this
                    || container instanceof CompoundContainer && ((CompoundContainer)container).contains(ChestBlockEntity.this);
            }
        }
    };
    private final ChestLidController chestLidController = new ChestLidController();

    protected ChestBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public ChestBlockEntity(BlockPos pos, BlockState blockState) {
        this(BlockEntityType.CHEST, pos, blockState);
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.chest");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, this.items, registries);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, this.items, registries);
        }
    }

    public static void lidAnimateTick(Level level, BlockPos pos, BlockState state, ChestBlockEntity blockEntity) {
        blockEntity.chestLidController.tickLid();
    }

    static void playSound(Level level, BlockPos pos, BlockState state, SoundEvent sound) {
        ChestType chestType = state.getValue(ChestBlock.TYPE);
        if (chestType != ChestType.LEFT) {
            double d = pos.getX() + 0.5;
            double d1 = pos.getY() + 0.5;
            double d2 = pos.getZ() + 0.5;
            if (chestType == ChestType.RIGHT) {
                Direction connectedDirection = ChestBlock.getConnectedDirection(state);
                d += connectedDirection.getStepX() * 0.5;
                d2 += connectedDirection.getStepZ() * 0.5;
            }

            level.playSound(null, d, d1, d2, sound, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
        }
    }

    @Override
    public boolean triggerEvent(int id, int type) {
        if (id == 1) {
            this.chestLidController.shouldBeOpen(type > 0);
            return true;
        } else {
            return super.triggerEvent(id, type);
        }
    }

    @Override
    public void startOpen(Player player) {
        if (!this.remove && !player.isSpectator()) {
            this.openersCounter.incrementOpeners(player, this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!this.remove && !player.isSpectator()) {
            this.openersCounter.decrementOpeners(player, this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public float getOpenNess(float partialTicks) {
        return this.chestLidController.getOpenness(partialTicks);
    }

    public static int getOpenCount(BlockGetter level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        if (blockState.hasBlockEntity()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ChestBlockEntity) {
                return ((ChestBlockEntity)blockEntity).openersCounter.getOpenerCount();
            }
        }

        return 0;
    }

    public static void swapContents(ChestBlockEntity chest, ChestBlockEntity otherChest) {
        NonNullList<ItemStack> items = chest.getItems();
        chest.setItems(otherChest.getItems());
        otherChest.setItems(items);
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory player) {
        return ChestMenu.threeRows(id, player, this);
    }

    public void recheckOpen() {
        if (!this.remove) {
            this.openersCounter.recheckOpeners(this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    protected void signalOpenCount(Level level, BlockPos pos, BlockState state, int eventId, int eventParam) {
        Block block = state.getBlock();
        level.blockEvent(pos, block, 1, eventParam);
    }
}
