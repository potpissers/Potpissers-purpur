package net.minecraft.world.entity.vehicle;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class MinecartFurnace extends AbstractMinecart {
    private static final EntityDataAccessor<Boolean> DATA_ID_FUEL = SynchedEntityData.defineId(MinecartFurnace.class, EntityDataSerializers.BOOLEAN);
    private static final int FUEL_TICKS_PER_ITEM = 3600;
    private static final int MAX_FUEL_TICKS = 32000;
    private int fuel;
    public Vec3 push = Vec3.ZERO;

    public MinecartFurnace(EntityType<? extends MinecartFurnace> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public boolean isFurnace() {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ID_FUEL, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            if (this.fuel > 0) {
                this.fuel--;
            }

            if (this.fuel <= 0) {
                this.push = Vec3.ZERO;
            }

            this.setHasFuel(this.fuel > 0);
        }

        if (this.hasFuel() && this.random.nextInt(4) == 0) {
            this.level().addParticle(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.8, this.getZ(), 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected double getMaxSpeed(ServerLevel level) {
        return this.isInWater() ? super.getMaxSpeed(level) * 0.75 : super.getMaxSpeed(level) * 0.5;
    }

    @Override
    protected Item getDropItem() {
        return Items.FURNACE_MINECART;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.FURNACE_MINECART);
    }

    @Override
    protected Vec3 applyNaturalSlowdown(Vec3 speed) {
        Vec3 vec3;
        if (this.push.lengthSqr() > 1.0E-7) {
            this.push = this.calculateNewPushAlong(speed);
            vec3 = speed.multiply(0.8, 0.0, 0.8).add(this.push);
            if (this.isInWater()) {
                vec3 = vec3.scale(0.1);
            }
        } else {
            vec3 = speed.multiply(0.98, 0.0, 0.98);
        }

        return super.applyNaturalSlowdown(vec3);
    }

    private Vec3 calculateNewPushAlong(Vec3 speed) {
        double d = 1.0E-4;
        double d1 = 0.001;
        return this.push.horizontalDistanceSqr() > 1.0E-4 && speed.horizontalDistanceSqr() > 0.001
            ? this.push.projectedOn(speed).normalize().scale(this.push.length())
            : this.push;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.is(ItemTags.FURNACE_MINECART_FUEL) && this.fuel + 3600 <= 32000) {
            itemInHand.consume(1, player);
            this.fuel += 3600;
        }

        if (this.fuel > 0) {
            this.push = this.position().subtract(player.position()).horizontal();
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putDouble("PushX", this.push.x);
        compound.putDouble("PushZ", this.push.z);
        compound.putShort("Fuel", (short)this.fuel);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        double _double = compound.getDouble("PushX");
        double _double1 = compound.getDouble("PushZ");
        this.push = new Vec3(_double, 0.0, _double1);
        this.fuel = compound.getShort("Fuel");
    }

    protected boolean hasFuel() {
        return this.entityData.get(DATA_ID_FUEL);
    }

    protected void setHasFuel(boolean hasFuel) {
        this.entityData.set(DATA_ID_FUEL, hasFuel);
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.NORTH).setValue(FurnaceBlock.LIT, Boolean.valueOf(this.hasFuel()));
    }
}
