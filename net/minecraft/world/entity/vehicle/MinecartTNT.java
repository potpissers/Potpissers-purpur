package net.minecraft.world.entity.vehicle;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class MinecartTNT extends AbstractMinecart {
    private static final byte EVENT_PRIME = 10;
    private static final String TAG_EXPLOSION_POWER = "explosion_power";
    private static final String TAG_EXPLOSION_SPEED_FACTOR = "explosion_speed_factor";
    private static final String TAG_FUSE = "fuse";
    private static final float DEFAULT_EXPLOSION_POWER_BASE = 4.0F;
    private static final float DEFAULT_EXPLOSION_SPEED_FACTOR = 1.0F;
    private int fuse = -1;
    private float explosionPowerBase = 4.0F;
    private float explosionSpeedFactor = 1.0F;

    public MinecartTNT(EntityType<? extends MinecartTNT> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return Blocks.TNT.defaultBlockState();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.fuse > 0) {
            this.fuse--;
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 0.0, 0.0, 0.0);
        } else if (this.fuse == 0) {
            this.explode(this.getDeltaMovement().horizontalDistanceSqr());
        }

        if (this.horizontalCollision) {
            double d = this.getDeltaMovement().horizontalDistanceSqr();
            if (d >= 0.01F) {
                this.explode(d);
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (damageSource.getDirectEntity() instanceof Projectile projectile && projectile.isOnFire()) {
            DamageSource damageSource1 = this.damageSources().explosion(this, damageSource.getEntity());
            this.explode(damageSource1, projectile.getDeltaMovement().lengthSqr());
        }

        return super.hurtServer(level, damageSource, amount);
    }

    @Override
    public void destroy(ServerLevel level, DamageSource damageSource) {
        double d = this.getDeltaMovement().horizontalDistanceSqr();
        if (!damageSourceIgnitesTnt(damageSource) && !(d >= 0.01F)) {
            this.destroy(level, this.getDropItem());
        } else {
            if (this.fuse < 0) {
                this.primeFuse();
                this.fuse = this.random.nextInt(20) + this.random.nextInt(20);
            }
        }
    }

    @Override
    protected Item getDropItem() {
        return Items.TNT_MINECART;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.TNT_MINECART);
    }

    protected void explode(double radiusModifier) {
        this.explode(null, radiusModifier);
    }

    protected void explode(@Nullable DamageSource damageSource, double radiusModifier) {
        if (this.level() instanceof ServerLevel serverLevel) {
            double min = Math.min(Math.sqrt(radiusModifier), 5.0);
            serverLevel.explode(
                this,
                damageSource,
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                (float)(this.explosionPowerBase + this.explosionSpeedFactor * this.random.nextDouble() * 1.5 * min),
                false,
                Level.ExplosionInteraction.TNT
            );
            this.discard();
        }
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (fallDistance >= 3.0F) {
            float f = fallDistance / 10.0F;
            this.explode(f * f);
        }

        return super.causeFallDamage(fallDistance, multiplier, source);
    }

    @Override
    public void activateMinecart(int x, int y, int z, boolean receivingPower) {
        if (receivingPower && this.fuse < 0) {
            this.primeFuse();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 10) {
            this.primeFuse();
        } else {
            super.handleEntityEvent(id);
        }
    }

    public void primeFuse() {
        this.fuse = 80;
        if (!this.level().isClientSide) {
            this.level().broadcastEntityEvent(this, (byte)10);
            if (!this.isSilent()) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
    }

    public int getFuse() {
        return this.fuse;
    }

    public boolean isPrimed() {
        return this.fuse > -1;
    }

    @Override
    public float getBlockExplosionResistance(
        Explosion explosion, BlockGetter level, BlockPos pos, BlockState blockState, FluidState fluidState, float explosionPower
    ) {
        return !this.isPrimed() || !blockState.is(BlockTags.RAILS) && !level.getBlockState(pos.above()).is(BlockTags.RAILS)
            ? super.getBlockExplosionResistance(explosion, level, pos, blockState, fluidState, explosionPower)
            : 0.0F;
    }

    @Override
    public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState blockState, float explosionPower) {
        return (!this.isPrimed() || !blockState.is(BlockTags.RAILS) && !level.getBlockState(pos.above()).is(BlockTags.RAILS))
            && super.shouldBlockExplode(explosion, level, pos, blockState, explosionPower);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("fuse", 99)) {
            this.fuse = compound.getInt("fuse");
        }

        if (compound.contains("explosion_power", 99)) {
            this.explosionPowerBase = Mth.clamp(compound.getFloat("explosion_power"), 0.0F, 128.0F);
        }

        if (compound.contains("explosion_speed_factor", 99)) {
            this.explosionSpeedFactor = Mth.clamp(compound.getFloat("explosion_speed_factor"), 0.0F, 128.0F);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("fuse", this.fuse);
        if (this.explosionPowerBase != 4.0F) {
            compound.putFloat("explosion_power", this.explosionPowerBase);
        }

        if (this.explosionSpeedFactor != 1.0F) {
            compound.putFloat("explosion_speed_factor", this.explosionSpeedFactor);
        }
    }

    @Override
    boolean shouldSourceDestroy(DamageSource source) {
        return damageSourceIgnitesTnt(source);
    }

    private static boolean damageSourceIgnitesTnt(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION);
    }
}
