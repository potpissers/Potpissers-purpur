package net.minecraft.world.entity.boss.enderdragon;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.dimension.end.EndDragonFight;

public class EndCrystal extends Entity {
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_BEAM_TARGET = SynchedEntityData.defineId(
        EndCrystal.class, EntityDataSerializers.OPTIONAL_BLOCK_POS
    );
    private static final EntityDataAccessor<Boolean> DATA_SHOW_BOTTOM = SynchedEntityData.defineId(EndCrystal.class, EntityDataSerializers.BOOLEAN);
    public int time;
    public boolean generatedByDragonFight = false; // Paper - Fix invulnerable end crystals

    public EndCrystal(EntityType<? extends EndCrystal> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
        this.time = this.random.nextInt(100000);
    }

    public EndCrystal(Level level, double x, double y, double z) {
        this(EntityType.END_CRYSTAL, level);
        this.setPos(x, y, z);
    }

    // Purpur start - End crystal explosion options
    public boolean shouldExplode() {
        return showsBottom() ? level().purpurConfig.basedEndCrystalExplode : level().purpurConfig.baselessEndCrystalExplode;
    }

    public float getExplosionPower() {
        return (float) (showsBottom() ? level().purpurConfig.basedEndCrystalExplosionPower : level().purpurConfig.baselessEndCrystalExplosionPower);
    }

    public boolean hasExplosionFire() {
        return showsBottom() ? level().purpurConfig.basedEndCrystalExplosionFire : level().purpurConfig.baselessEndCrystalExplosionFire;
    }

    public Level.ExplosionInteraction getExplosionEffect() {
        return showsBottom() ? level().purpurConfig.basedEndCrystalExplosionEffect : level().purpurConfig.baselessEndCrystalExplosionEffect;
    }
    // Purpur end - End crystal explosion options

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BEAM_TARGET, Optional.empty());
        builder.define(DATA_SHOW_BOTTOM, true);
    }

    @Override
    public void tick() {
        this.time++;
        this.applyEffectsFromBlocks();
        this.handlePortal();
        if (this.level() instanceof ServerLevel) {
            BlockPos blockPos = this.blockPosition();
            if (((ServerLevel)this.level()).getDragonFight() != null && this.level().getBlockState(blockPos).isAir()) {
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(this.level(), blockPos, this).isCancelled()) { // Paper
                this.level().setBlockAndUpdate(blockPos, BaseFireBlock.getState(this.level(), blockPos));
                } // Paper
            }
        }

        // Paper start - Fix invulnerable end crystals
        if (this.level().paperConfig().unsupportedSettings.fixInvulnerableEndCrystalExploit && this.generatedByDragonFight && this.isInvulnerable()) {
            if (!java.util.Objects.equals(((ServerLevel) this.level()).uuid, this.getOriginWorld())
                || ((ServerLevel) this.level()).getDragonFight() == null
                || ((ServerLevel) this.level()).getDragonFight().respawnStage == null
                || ((ServerLevel) this.level()).getDragonFight().respawnStage.ordinal() > net.minecraft.world.level.dimension.end.DragonRespawnAnimation.SUMMONING_DRAGON.ordinal()) {
                this.setInvulnerable(false);
                this.setBeamTarget(null);
            }
        }
        // Paper end - Fix invulnerable end crystals
        if (this.level().purpurConfig.endCrystalCramming > 0 && this.level().getEntitiesOfClass(EndCrystal.class, getBoundingBox()).size() > this.level().purpurConfig.endCrystalCramming) this.hurt(this.damageSources().cramming(), 6.0F); // Purpur - End Crystal Cramming

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        if (this.getBeamTarget() != null) {
            compound.put("beam_target", NbtUtils.writeBlockPos(this.getBeamTarget()));
        }

        compound.putBoolean("ShowBottom", this.showsBottom());
        if (this.generatedByDragonFight) compound.putBoolean("Paper.GeneratedByDragonFight", this.generatedByDragonFight); // Paper - Fix invulnerable end crystals
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        NbtUtils.readBlockPos(compound, "beam_target").ifPresent(this::setBeamTarget);
        if (compound.contains("ShowBottom", 1)) {
            this.setShowBottom(compound.getBoolean("ShowBottom"));
        }
        if (compound.contains("Paper.GeneratedByDragonFight", 1)) this.generatedByDragonFight = compound.getBoolean("Paper.GeneratedByDragonFight"); // Paper - Fix invulnerable end crystals
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public final boolean hurtClient(DamageSource damageSource) {
        return !this.isInvulnerableToBase(damageSource) && !(damageSource.getEntity() instanceof EnderDragon);
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableToBase(damageSource)) {
            return false;
        } else if (damageSource.getEntity() instanceof EnderDragon) {
            return false;
        } else {
            if (!this.isRemoved()) {
                // CraftBukkit start - All non-living entities need this
                if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, damageSource, amount, false)) {
                    return false;
                }
                // CraftBukkit end
                if (!damageSource.is(DamageTypeTags.IS_EXPLOSION)) {
                    if (shouldExplode()) {// Purpur - End crystal explosion options
                    DamageSource damageSource1 = damageSource.getEntity() != null ? this.damageSources().explosion(this, damageSource.getEntity()) : null;
                    // CraftBukkit start
                    org.bukkit.event.entity.ExplosionPrimeEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callExplosionPrimeEvent(this, getExplosionPower(), hasExplosionFire()); // Purpur - End crystal explosion options
                    if (event.isCancelled()) {
                        return false;
                    }

                    this.remove(Entity.RemovalReason.KILLED, org.bukkit.event.entity.EntityRemoveEvent.Cause.EXPLODE); // Paper - add Bukkit remove cause
                    level.explode(this, damageSource1, null, this.getX(), this.getY(), this.getZ(), event.getRadius(), event.getFire(), getExplosionEffect()); // Purpur - End crystal explosion options
                    } else this.unsetRemoved(); // Purpur - End crystal explosion options
                } else {
                    this.remove(Entity.RemovalReason.KILLED, org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH); // Paper - add Bukkit remove cause
                    // CraftBukkit end
                }

                this.onDestroyedBy(level, damageSource);
            }

            return true;
        }
    }

    @Override
    public void kill(ServerLevel level) {
        this.onDestroyedBy(level, this.damageSources().generic());
        super.kill(level);
    }

    private void onDestroyedBy(ServerLevel level, DamageSource damageSource) {
        EndDragonFight dragonFight = level.getDragonFight();
        if (dragonFight != null) {
            dragonFight.onCrystalDestroyed(this, damageSource);
        }
    }

    public void setBeamTarget(@Nullable BlockPos beamTarget) {
        this.getEntityData().set(DATA_BEAM_TARGET, Optional.ofNullable(beamTarget));
    }

    @Nullable
    public BlockPos getBeamTarget() {
        return this.getEntityData().get(DATA_BEAM_TARGET).orElse(null);
    }

    public void setShowBottom(boolean showBottom) {
        this.getEntityData().set(DATA_SHOW_BOTTOM, showBottom);
    }

    public boolean showsBottom() {
        return this.getEntityData().get(DATA_SHOW_BOTTOM);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return super.shouldRenderAtSqrDistance(distance) || this.getBeamTarget() != null;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.END_CRYSTAL);
    }
}
