package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import org.slf4j.Logger;

public class SculkSensorBlockEntity extends BlockEntity implements GameEventListener.Provider<VibrationSystem.Listener>, VibrationSystem {
    private static final Logger LOGGER = LogUtils.getLogger();
    private VibrationSystem.Data vibrationData;
    private final VibrationSystem.Listener vibrationListener;
    private final VibrationSystem.User vibrationUser = this.createVibrationUser();
    public int lastVibrationFrequency;

    protected SculkSensorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.vibrationData = new VibrationSystem.Data();
        this.vibrationListener = new VibrationSystem.Listener(this);
    }

    public SculkSensorBlockEntity(BlockPos pos, BlockState blockState) {
        this(BlockEntityType.SCULK_SENSOR, pos, blockState);
    }

    public VibrationSystem.User createVibrationUser() {
        return new SculkSensorBlockEntity.VibrationUser(this.getBlockPos());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.lastVibrationFrequency = tag.getInt("last_vibration_frequency");
        RegistryOps<Tag> registryOps = registries.createSerializationContext(NbtOps.INSTANCE);
        if (tag.contains("listener", 10)) {
            VibrationSystem.Data.CODEC
                .parse(registryOps, tag.getCompound("listener"))
                .resultOrPartial(string -> LOGGER.error("Failed to parse vibration listener for Sculk Sensor: '{}'", string))
                .ifPresent(data -> this.vibrationData = data);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("last_vibration_frequency", this.lastVibrationFrequency);
        RegistryOps<Tag> registryOps = registries.createSerializationContext(NbtOps.INSTANCE);
        VibrationSystem.Data.CODEC
            .encodeStart(registryOps, this.vibrationData)
            .resultOrPartial(string -> LOGGER.error("Failed to encode vibration listener for Sculk Sensor: '{}'", string))
            .ifPresent(tag1 -> tag.put("listener", tag1));
    }

    @Override
    public VibrationSystem.Data getVibrationData() {
        return this.vibrationData;
    }

    @Override
    public VibrationSystem.User getVibrationUser() {
        return this.vibrationUser;
    }

    public int getLastVibrationFrequency() {
        return this.lastVibrationFrequency;
    }

    public void setLastVibrationFrequency(int lastVibrationFrequency) {
        this.lastVibrationFrequency = lastVibrationFrequency;
    }

    @Override
    public VibrationSystem.Listener getListener() {
        return this.vibrationListener;
    }

    protected class VibrationUser implements VibrationSystem.User {
        public static final int LISTENER_RANGE = 8;
        protected final BlockPos blockPos;
        private final PositionSource positionSource;

        public VibrationUser(final BlockPos pos) {
            this.blockPos = pos;
            this.positionSource = new BlockPositionSource(pos);
        }

        @Override
        public int getListenerRadius() {
            return 8;
        }

        @Override
        public PositionSource getPositionSource() {
            return this.positionSource;
        }

        @Override
        public boolean canTriggerAvoidVibration() {
            return true;
        }

        @Override
        public boolean canReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent, @Nullable GameEvent.Context context) {
            return (!pos.equals(this.blockPos) || !gameEvent.is(GameEvent.BLOCK_DESTROY) && !gameEvent.is(GameEvent.BLOCK_PLACE))
                && SculkSensorBlock.canActivate(SculkSensorBlockEntity.this.getBlockState());
        }

        @Override
        public void onReceiveVibration(
            ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent, @Nullable Entity entity, @Nullable Entity playerEntity, float distance
        ) {
            BlockState blockState = SculkSensorBlockEntity.this.getBlockState();
            if (SculkSensorBlock.canActivate(blockState)) {
                SculkSensorBlockEntity.this.setLastVibrationFrequency(VibrationSystem.getGameEventFrequency(gameEvent));
                int redstoneStrengthForDistance = VibrationSystem.getRedstoneStrengthForDistance(distance, this.getListenerRadius());
                if (blockState.getBlock() instanceof SculkSensorBlock sculkSensorBlock) {
                    sculkSensorBlock.activate(
                        entity, level, this.blockPos, blockState, redstoneStrengthForDistance, SculkSensorBlockEntity.this.getLastVibrationFrequency()
                    );
                }
            }
        }

        @Override
        public void onDataChanged() {
            SculkSensorBlockEntity.this.setChanged();
        }

        @Override
        public boolean requiresAdjacentChunksToBeTicking() {
            return true;
        }
    }
}
