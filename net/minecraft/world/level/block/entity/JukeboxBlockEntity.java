package net.minecraft.world.level.block.entity;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ContainerSingleItem;

public class JukeboxBlockEntity extends BlockEntity implements ContainerSingleItem.BlockContainerSingleItem {

    // CraftBukkit start - add fields and methods
    public java.util.List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = MAX_STACK;
    public boolean opened;

    @Override
    public java.util.List<net.minecraft.world.item.ItemStack> getContents() {
        return java.util.Collections.singletonList(this.item);
    }

    @Override
    public void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.add(player);
    }

    @Override
    public void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.remove(player);
    }

    @Override
    public java.util.List<org.bukkit.entity.HumanEntity> getViewers() {
        return this.transaction;
    }

    @Override
    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }

    @Override
    public org.bukkit.Location getLocation() {
        if (this.level == null) return null;
        return io.papermc.paper.util.MCUtil.toLocation(this.level, this.worldPosition);
    }
    // CraftBukkit end

    public static final String SONG_ITEM_TAG_ID = "RecordItem";
    public static final String TICKS_SINCE_SONG_STARTED_TAG_ID = "ticks_since_song_started";
    private ItemStack item = ItemStack.EMPTY;
    private final JukeboxSongPlayer jukeboxSongPlayer = new JukeboxSongPlayer(this::onSongChanged, this.getBlockPos());

    public JukeboxBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.JUKEBOX, pos, blockState);
    }

    public JukeboxSongPlayer getSongPlayer() {
        return this.jukeboxSongPlayer;
    }

    public void onSongChanged() {
        this.level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        this.setChanged();
    }

    private void notifyItemChangedInJukebox(boolean hasRecord) {
        if (this.level != null && this.level.getBlockState(this.getBlockPos()) == this.getBlockState()) {
            this.level.setBlock(this.getBlockPos(), this.getBlockState().setValue(JukeboxBlock.HAS_RECORD, Boolean.valueOf(hasRecord)), 2);
            this.level.gameEvent(GameEvent.BLOCK_CHANGE, this.getBlockPos(), GameEvent.Context.of(this.getBlockState()));
        }
    }

    public void popOutTheItem() {
        if (this.level != null && !this.level.isClientSide) {
            BlockPos blockPos = this.getBlockPos();
            ItemStack theItem = this.getTheItem();
            if (!theItem.isEmpty()) {
                this.removeTheItem();
                Vec3 vec3 = Vec3.atLowerCornerWithOffset(blockPos, 0.5, 1.01, 0.5).offsetRandom(this.level.random, 0.7F);
                ItemStack itemStack = theItem.copy();
                ItemEntity itemEntity = new ItemEntity(this.level, vec3.x(), vec3.y(), vec3.z(), itemStack);
                itemEntity.setDefaultPickUpDelay();
                this.level.addFreshEntity(itemEntity);
            }
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, JukeboxBlockEntity jukebox) {
        jukebox.jukeboxSongPlayer.tick(level, state);
    }

    public int getComparatorOutput() {
        return JukeboxSong.fromStack(this.level.registryAccess(), this.item).map(Holder::value).map(JukeboxSong::comparatorOutput).orElse(0);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("RecordItem", 10)) {
            this.item = ItemStack.parse(registries, tag.getCompound("RecordItem")).orElse(ItemStack.EMPTY);
        } else {
            if (!this.item.isEmpty()) {
                this.jukeboxSongPlayer.stop(this.level, this.getBlockState());
            }

            this.item = ItemStack.EMPTY;
        }

        if (tag.contains("ticks_since_song_started", 4)) {
            JukeboxSong.fromStack(registries, this.item)
                .ifPresent(holder -> this.jukeboxSongPlayer.setSongWithoutPlaying((Holder<JukeboxSong>)holder, tag.getLong("ticks_since_song_started")));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.getTheItem().isEmpty()) {
            tag.put("RecordItem", this.getTheItem().save(registries));
        }

        if (this.jukeboxSongPlayer.getSong() != null) {
            tag.putLong("ticks_since_song_started", this.jukeboxSongPlayer.getTicksSinceSongStarted());
        }
    }

    @Override
    public ItemStack getTheItem() {
        return this.item;
    }

    @Override
    public ItemStack splitTheItem(int amount) {
        ItemStack itemStack = this.item;
        this.setTheItem(ItemStack.EMPTY);
        return itemStack;
    }

    @Override
    public void setTheItem(ItemStack item) {
        this.item = item;
        boolean flag = !this.item.isEmpty();
        Optional<Holder<JukeboxSong>> optional = JukeboxSong.fromStack(this.level.registryAccess(), this.item);
        this.notifyItemChangedInJukebox(flag);
        if (flag && optional.isPresent()) {
            this.jukeboxSongPlayer.play(this.level, optional.get());
        } else {
            this.jukeboxSongPlayer.stop(this.level, this.getBlockState());
        }
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack; // CraftBukkit
    }

    @Override
    public BlockEntity getContainerBlockEntity() {
        return this;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.has(DataComponents.JUKEBOX_PLAYABLE) && this.getItem(slot).isEmpty();
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return target.hasAnyMatching(ItemStack::isEmpty);
    }

    @VisibleForTesting
    public void setSongItemWithoutPlaying(ItemStack stack, final long ticksSinceSongStarted) { // CraftBukkit - passed ticks since song started
        this.item = stack;
        this.jukeboxSongPlayer.song = null; // CraftBukkit - reset
        JukeboxSong.fromStack(this.level != null ? this.level.registryAccess() : org.bukkit.craftbukkit.CraftRegistry.getMinecraftRegistry(), stack) // Paper - fallback to other RegistryAccess if no level
            .ifPresent(holder -> this.jukeboxSongPlayer.setSongWithoutPlaying((Holder<JukeboxSong>)holder, ticksSinceSongStarted)); // CraftBukkit - passed ticks since song started
        // CraftBukkit start - add null check for level
        if (this.level != null) {
            this.level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        }
        // CraftBukkit end
        this.setChanged();
    }

    @VisibleForTesting
    public void tryForcePlaySong() {
        JukeboxSong.fromStack(this.level.registryAccess(), this.getTheItem())
            .ifPresent(holder -> this.jukeboxSongPlayer.play(this.level, (Holder<JukeboxSong>)holder));
    }
}
