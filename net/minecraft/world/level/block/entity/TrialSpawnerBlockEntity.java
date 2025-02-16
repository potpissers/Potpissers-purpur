package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.entity.trialspawner.PlayerDetector;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.slf4j.Logger;

public class TrialSpawnerBlockEntity extends BlockEntity implements Spawner, TrialSpawner.StateAccessor {
    private static final Logger LOGGER = LogUtils.getLogger();
    public TrialSpawner trialSpawner;

    public TrialSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.TRIAL_SPAWNER, pos, state);
        PlayerDetector playerDetector = PlayerDetector.NO_CREATIVE_PLAYERS;
        PlayerDetector.EntitySelector entitySelector = PlayerDetector.EntitySelector.SELECT_FROM_LEVEL;
        this.trialSpawner = new TrialSpawner(this, playerDetector, entitySelector);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.trialSpawner
            .codec()
            .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
            .resultOrPartial(LOGGER::error)
            .ifPresent(trialSpawner -> this.trialSpawner = trialSpawner);
        if (this.level != null) {
            this.markUpdated();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.trialSpawner
            .codec()
            .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this.trialSpawner)
            .ifSuccess(tag1 -> tag.merge((CompoundTag)tag1))
            .ifError(error -> LOGGER.warn("Failed to encode TrialSpawner {}", error.message()));
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.trialSpawner.getData().getUpdateTag(this.getBlockState().getValue(TrialSpawnerBlock.STATE));
    }

    @Override
    public void setEntityId(EntityType<?> entityType, RandomSource random) {
        if (this.level == null) {
            Util.logAndPauseIfInIde("Expected non-null level");
        } else {
            this.trialSpawner.overrideEntityToSpawn(entityType, this.level);
            this.setChanged();
        }
    }

    public TrialSpawner getTrialSpawner() {
        return this.trialSpawner;
    }

    @Override
    public TrialSpawnerState getState() {
        return !this.getBlockState().hasProperty(BlockStateProperties.TRIAL_SPAWNER_STATE)
            ? TrialSpawnerState.INACTIVE
            : this.getBlockState().getValue(BlockStateProperties.TRIAL_SPAWNER_STATE);
    }

    @Override
    public void setState(Level level, TrialSpawnerState state) {
        this.setChanged();
        level.setBlockAndUpdate(this.worldPosition, this.getBlockState().setValue(BlockStateProperties.TRIAL_SPAWNER_STATE, state));
    }

    @Override
    public void markUpdated() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }
}
