package net.minecraft.world.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class EyeOfEnder extends Entity implements ItemSupplier {
    private static final float MIN_CAMERA_DISTANCE_SQUARED = 12.25F;
    private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK = SynchedEntityData.defineId(EyeOfEnder.class, EntityDataSerializers.ITEM_STACK);
    public double tx;
    public double ty;
    public double tz;
    public int life;
    public boolean surviveAfterDeath;

    public EyeOfEnder(EntityType<? extends EyeOfEnder> entityType, Level level) {
        super(entityType, level);
    }

    public EyeOfEnder(Level level, double x, double y, double z) {
        this(EntityType.EYE_OF_ENDER, level);
        this.setPos(x, y, z);
    }

    public void setItem(ItemStack stack) {
        if (stack.isEmpty()) {
            this.getEntityData().set(DATA_ITEM_STACK, this.getDefaultItem());
        } else {
            this.getEntityData().set(DATA_ITEM_STACK, stack.copyWithCount(1));
        }
    }

    @Override
    public ItemStack getItem() {
        return this.getEntityData().get(DATA_ITEM_STACK);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM_STACK, this.getDefaultItem());
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        if (this.tickCount < 2 && distance < 12.25) {
            return false;
        } else {
            double d = this.getBoundingBox().getSize() * 4.0;
            if (Double.isNaN(d)) {
                d = 4.0;
            }

            d *= 64.0;
            return distance < d * d;
        }
    }

    public void signalTo(BlockPos pos) {
        double d = pos.getX();
        int y = pos.getY();
        double d1 = pos.getZ();
        double d2 = d - this.getX();
        double d3 = d1 - this.getZ();
        double squareRoot = Math.sqrt(d2 * d2 + d3 * d3);
        if (squareRoot > 12.0) {
            this.tx = this.getX() + d2 / squareRoot * 12.0;
            this.tz = this.getZ() + d3 / squareRoot * 12.0;
            this.ty = this.getY() + 8.0;
        } else {
            this.tx = d;
            this.ty = y;
            this.tz = d1;
        }

        this.life = 0;
        this.surviveAfterDeath = this.random.nextInt(5) > 0;
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        this.setDeltaMovement(x, y, z);
        if (this.xRotO == 0.0F && this.yRotO == 0.0F) {
            double squareRoot = Math.sqrt(x * x + z * z);
            this.setYRot((float)(Mth.atan2(x, z) * 180.0F / (float)Math.PI));
            this.setXRot((float)(Mth.atan2(y, squareRoot) * 180.0F / (float)Math.PI));
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();
        }
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 deltaMovement = this.getDeltaMovement();
        double d = this.getX() + deltaMovement.x;
        double d1 = this.getY() + deltaMovement.y;
        double d2 = this.getZ() + deltaMovement.z;
        double d3 = deltaMovement.horizontalDistance();
        this.setXRot(Projectile.lerpRotation(this.xRotO, (float)(Mth.atan2(deltaMovement.y, d3) * 180.0F / (float)Math.PI)));
        this.setYRot(Projectile.lerpRotation(this.yRotO, (float)(Mth.atan2(deltaMovement.x, deltaMovement.z) * 180.0F / (float)Math.PI)));
        if (!this.level().isClientSide) {
            double d4 = this.tx - d;
            double d5 = this.tz - d2;
            float f = (float)Math.sqrt(d4 * d4 + d5 * d5);
            float f1 = (float)Mth.atan2(d5, d4);
            double d6 = Mth.lerp(0.0025, d3, (double)f);
            double d7 = deltaMovement.y;
            if (f < 1.0F) {
                d6 *= 0.8;
                d7 *= 0.8;
            }

            int i = this.getY() < this.ty ? 1 : -1;
            deltaMovement = new Vec3(Math.cos(f1) * d6, d7 + (i - d7) * 0.015F, Math.sin(f1) * d6);
            this.setDeltaMovement(deltaMovement);
        }

        float f2 = 0.25F;
        if (this.isInWater()) {
            for (int i1 = 0; i1 < 4; i1++) {
                this.level()
                    .addParticle(
                        ParticleTypes.BUBBLE,
                        d - deltaMovement.x * 0.25,
                        d1 - deltaMovement.y * 0.25,
                        d2 - deltaMovement.z * 0.25,
                        deltaMovement.x,
                        deltaMovement.y,
                        deltaMovement.z
                    );
            }
        } else {
            this.level()
                .addParticle(
                    ParticleTypes.PORTAL,
                    d - deltaMovement.x * 0.25 + this.random.nextDouble() * 0.6 - 0.3,
                    d1 - deltaMovement.y * 0.25 - 0.5,
                    d2 - deltaMovement.z * 0.25 + this.random.nextDouble() * 0.6 - 0.3,
                    deltaMovement.x,
                    deltaMovement.y,
                    deltaMovement.z
                );
        }

        if (!this.level().isClientSide) {
            this.setPos(d, d1, d2);
            this.life++;
            if (this.life > 80 && !this.level().isClientSide) {
                this.playSound(SoundEvents.ENDER_EYE_DEATH, 1.0F, 1.0F);
                this.discard();
                if (this.surviveAfterDeath) {
                    this.level().addFreshEntity(new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), this.getItem()));
                } else {
                    this.level().levelEvent(2003, this.blockPosition(), 0);
                }
            }
        } else {
            this.setPosRaw(d, d1, d2);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        compound.put("Item", this.getItem().save(this.registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        if (compound.contains("Item", 10)) {
            this.setItem(ItemStack.parse(this.registryAccess(), compound.getCompound("Item")).orElse(this.getDefaultItem()));
        } else {
            this.setItem(this.getDefaultItem());
        }
    }

    private ItemStack getDefaultItem() {
        return new ItemStack(Items.ENDER_EYE);
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }
}
