package net.minecraft.world.entity.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class MinecartSpawner extends AbstractMinecart {
    private final BaseSpawner spawner = new BaseSpawner() {
        @Override
        public void broadcastEvent(Level level, BlockPos pos, int eventId) {
            level.broadcastEntityEvent(MinecartSpawner.this, (byte)eventId);
        }
    };
    private final Runnable ticker;

    public MinecartSpawner(EntityType<? extends MinecartSpawner> entityType, Level level) {
        super(entityType, level);
        this.ticker = this.createTicker(level);
    }

    @Override
    protected Item getDropItem() {
        return Items.MINECART;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.MINECART);
    }

    private Runnable createTicker(Level level) {
        return level instanceof ServerLevel
            ? () -> this.spawner.serverTick((ServerLevel)level, this.blockPosition())
            : () -> this.spawner.clientTick(level, this.blockPosition());
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return Blocks.SPAWNER.defaultBlockState();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.spawner.load(this.level(), this.blockPosition(), compound);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        this.spawner.save(compound);
    }

    @Override
    public void handleEntityEvent(byte id) {
        this.spawner.onEventTriggered(this.level(), id);
    }

    @Override
    public void tick() {
        super.tick();
        this.ticker.run();
    }

    public BaseSpawner getSpawner() {
        return this.spawner;
    }
}
