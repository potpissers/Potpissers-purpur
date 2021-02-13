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
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.dimension.end.EndDragonFight;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
// CraftBukkit end

public class EndCrystal extends Entity {

    private static final EntityDataAccessor<Optional<BlockPos>> DATA_BEAM_TARGET = SynchedEntityData.defineId(EndCrystal.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Boolean> DATA_SHOW_BOTTOM = SynchedEntityData.defineId(EndCrystal.class, EntityDataSerializers.BOOLEAN);
    public int time;
    public boolean generatedByDragonFight = false; // Paper - Fix invulnerable end crystals
    // Purpur start
    private net.minecraft.world.entity.monster.Phantom targetPhantom;
    private int phantomBeamTicks = 0;
    private int phantomDamageCooldown = 0;
    private int idleCooldown = 0;
    // Purpur end

    public EndCrystal(EntityType<? extends EndCrystal> type, Level world) {
        super(type, world);
        this.blocksBuilding = true;
        this.time = this.random.nextInt(100000);
    }

    public EndCrystal(Level world, double x, double y, double z) {
        this(EntityType.END_CRYSTAL, world);
        this.setPos(x, y, z);
    }

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

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(EndCrystal.DATA_BEAM_TARGET, Optional.empty());
        builder.define(EndCrystal.DATA_SHOW_BOTTOM, true);
    }

    @Override
    public void tick() {
        ++this.time;
        this.checkInsideBlocks();
        this.handlePortal();
        if (this.level() instanceof ServerLevel) {
            BlockPos blockposition = this.blockPosition();

            if (((ServerLevel) this.level()).getDragonFight() != null && this.level().getBlockState(blockposition).isAir()) {
                // CraftBukkit start
                if (!CraftEventFactory.callBlockIgniteEvent(this.level(), blockposition, this).isCancelled()) {
                    this.level().setBlockAndUpdate(blockposition, BaseFireBlock.getState(this.level(), blockposition));
                }
                // CraftBukkit end
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
        }

    // Purpur start
        if (level().purpurConfig.phantomAttackedByCrystalRadius <= 0 || --idleCooldown > 0) {
            return; // on cooldown
        }

        if (targetPhantom == null) {
            for (net.minecraft.world.entity.monster.Phantom phantom : level().getEntitiesOfClass(net.minecraft.world.entity.monster.Phantom.class, getBoundingBox().inflate(level().purpurConfig.phantomAttackedByCrystalRadius))) {
                if (phantom.hasLineOfSight(this)) {
                    attackPhantom(phantom);
                    break;
                }
            }
        } else {
            setBeamTarget(new BlockPos(targetPhantom).offset(0, -2, 0));
            if (--phantomBeamTicks > 0 && targetPhantom.isAlive()) {
                phantomDamageCooldown--;
                if (targetPhantom.hasLineOfSight(this)) {
                    if (phantomDamageCooldown <= 0) {
                        phantomDamageCooldown = 20;
                        targetPhantom.hurt(targetPhantom.damageSources().indirectMagic(this, this), level().purpurConfig.phantomAttackedByCrystalDamage);
                    }
                } else {
                    forgetPhantom(); // no longer in sight
                }
            } else {
                forgetPhantom(); // attacked long enough
            }
        }
    }

    private void attackPhantom(net.minecraft.world.entity.monster.Phantom phantom) {
        phantomDamageCooldown = 0;
        phantomBeamTicks = 60;
        targetPhantom = phantom;
    }

    private void forgetPhantom() {
        targetPhantom = null;
        setBeamTarget(null);
        phantomBeamTicks = 0;
        phantomDamageCooldown = 0;
        idleCooldown = 60;
    // Purpur end
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        if (this.getBeamTarget() != null) {
            nbt.put("beam_target", NbtUtils.writeBlockPos(this.getBeamTarget()));
        }

        nbt.putBoolean("ShowBottom", this.showsBottom());
        if (this.generatedByDragonFight) nbt.putBoolean("Paper.GeneratedByDragonFight", this.generatedByDragonFight); // Paper - Fix invulnerable end crystals
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        NbtUtils.readBlockPos(nbt, "beam_target").ifPresent(this::setBeamTarget);
        if (nbt.contains("ShowBottom", 1)) {
            this.setShowBottom(nbt.getBoolean("ShowBottom"));
        }
        if (nbt.contains("Paper.GeneratedByDragonFight", 1)) this.generatedByDragonFight = nbt.getBoolean("Paper.GeneratedByDragonFight"); // Paper - Fix invulnerable end crystals

    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else if (source.getEntity() instanceof EnderDragon) {
            return false;
        } else {
            if (!this.isRemoved() && !this.level().isClientSide) {
                // CraftBukkit start - All non-living entities need this
                if (CraftEventFactory.handleNonLivingEntityDamageEvent(this, source, amount, false)) {
                    return false;
                }
                // CraftBukkit end
                if (!source.is(DamageTypeTags.IS_EXPLOSION)) {
                    if (shouldExplode()) {// Purpur
                    DamageSource damagesource1 = source.getEntity() != null ? this.damageSources().explosion(this, source.getEntity()) : null;

                    // CraftBukkit start
                    ExplosionPrimeEvent event = CraftEventFactory.callExplosionPrimeEvent(this, getExplosionPower(), hasExplosionFire()); // Purpur
                    if (event.isCancelled()) {
                        return false;
                    }

                    this.remove(Entity.RemovalReason.KILLED, EntityRemoveEvent.Cause.EXPLODE); // CraftBukkit - add Bukkit remove cause
                    this.level().explode(this, damagesource1, (ExplosionDamageCalculator) null, this.getX(), this.getY(), this.getZ(), event.getRadius(), event.getFire(), getExplosionEffect()); // Purpur
                    } else this.unsetRemoved(); // Purpur
                } else {
                    this.remove(Entity.RemovalReason.KILLED, EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
                }
                // CraftBukkit end

                this.onDestroyedBy(source);
            }

            return true;
        }
    }

    @Override
    public void kill() {
        this.onDestroyedBy(this.damageSources().generic());
        super.kill();
    }

    private void onDestroyedBy(DamageSource source) {
        if (this.level() instanceof ServerLevel) {
            EndDragonFight enderdragonbattle = ((ServerLevel) this.level()).getDragonFight();

            if (enderdragonbattle != null) {
                enderdragonbattle.onCrystalDestroyed(this, source);
            }
        }

    }

    public void setBeamTarget(@Nullable BlockPos beamTarget) {
        this.getEntityData().set(EndCrystal.DATA_BEAM_TARGET, Optional.ofNullable(beamTarget));
    }

    @Nullable
    public BlockPos getBeamTarget() {
        return (BlockPos) ((Optional) this.getEntityData().get(EndCrystal.DATA_BEAM_TARGET)).orElse((Object) null);
    }

    public void setShowBottom(boolean showBottom) {
        this.getEntityData().set(EndCrystal.DATA_SHOW_BOTTOM, showBottom);
    }

    public boolean showsBottom() {
        return (Boolean) this.getEntityData().get(EndCrystal.DATA_SHOW_BOTTOM);
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
