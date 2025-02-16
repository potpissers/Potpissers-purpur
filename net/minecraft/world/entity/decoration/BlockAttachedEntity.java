package net.minecraft.world.entity.decoration;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public abstract class BlockAttachedEntity extends Entity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private int checkInterval; { this.checkInterval = this.getId() % this.level().spigotConfig.hangingTickFrequency; } // Paper - Perf: offset item frame ticking
    protected BlockPos pos;

    protected BlockAttachedEntity(EntityType<? extends BlockAttachedEntity> entityType, Level level) {
        super(entityType, level);
    }

    protected BlockAttachedEntity(EntityType<? extends BlockAttachedEntity> entityType, Level level, BlockPos pos) {
        this(entityType, level);
        this.pos = pos;
    }

    protected abstract void recalculateBoundingBox();

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel serverLevel) {
            this.checkBelowWorld();
            if (this.checkInterval++ == this.level().spigotConfig.hangingTickFrequency) { // Spigot
                this.checkInterval = 0;
                if (!this.isRemoved() && !this.survives()) {
                    // CraftBukkit start - fire break events
                    final org.bukkit.event.hanging.HangingBreakEvent.RemoveCause cause;
                    if (!this.level().getBlockState(this.blockPosition()).isAir()) {
                        // TODO: This feels insufficient to catch 100% of suffocation cases
                        cause = org.bukkit.event.hanging.HangingBreakEvent.RemoveCause.OBSTRUCTION;
                    } else {
                        cause = org.bukkit.event.hanging.HangingBreakEvent.RemoveCause.PHYSICS;
                    }

                    org.bukkit.event.hanging.HangingBreakEvent event = new org.bukkit.event.hanging.HangingBreakEvent((org.bukkit.entity.Hanging) this.getBukkitEntity(), cause);
                    this.level().getCraftServer().getPluginManager().callEvent(event);

                    if (this.isRemoved() || event.isCancelled()) {
                        return;
                    }
                    // CraftBukkit end
                    this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DROP); // CraftBukkit - add Bukkit remove cause
                    this.dropItem(serverLevel, null);
                }
            }
        }
    }

    public abstract boolean survives();

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean skipAttackInteraction(Entity entity) {
        return entity instanceof Player player
            && (!this.level().mayInteract(player, this.pos) || this.hurtOrSimulate(this.damageSources().playerAttack(player), 0.0F));
    }

    @Override
    public boolean hurtClient(DamageSource damageSource) {
        return !this.isInvulnerableToBase(damageSource);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableToBase(damageSource)) {
            return false;
        } else if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && damageSource.getEntity() instanceof Mob) {
            return false;
        } else {
            if (!this.isRemoved()) {
                // CraftBukkit start - fire break events
                Entity damager = (!damageSource.isDirect() && damageSource.getEntity() != null) ? damageSource.getEntity() : damageSource.getDirectEntity(); // Paper - fix DamageSource API
                org.bukkit.event.hanging.HangingBreakEvent event;
                if (damager != null) {
                    event = new org.bukkit.event.hanging.HangingBreakByEntityEvent((org.bukkit.entity.Hanging) this.getBukkitEntity(), damager.getBukkitEntity(), damageSource.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) ? org.bukkit.event.hanging.HangingBreakEvent.RemoveCause.EXPLOSION : org.bukkit.event.hanging.HangingBreakEvent.RemoveCause.ENTITY);
                } else {
                    event = new org.bukkit.event.hanging.HangingBreakEvent((org.bukkit.entity.Hanging) this.getBukkitEntity(), damageSource.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) ? org.bukkit.event.hanging.HangingBreakEvent.RemoveCause.EXPLOSION : org.bukkit.event.hanging.HangingBreakEvent.RemoveCause.DEFAULT);
                }

                this.level().getCraftServer().getPluginManager().callEvent(event);

                if (this.isRemoved() || event.isCancelled()) {
                    return true;
                }
                // CraftBukkit end
                this.kill(level);
                this.markHurt();
                this.dropItem(level, damageSource.getEntity());
            }

            return true;
        }
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return !explosion.shouldAffectBlocklikeEntities() || super.ignoreExplosion(explosion);
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        if (this.level() instanceof ServerLevel serverLevel && !this.isRemoved() && movement.lengthSqr() > 0.0) {
            // CraftBukkit start - fire break events
            // TODO - Does this need its own cause? Seems to only be triggered by pistons
            org.bukkit.event.hanging.HangingBreakEvent event = new org.bukkit.event.hanging.HangingBreakEvent((org.bukkit.entity.Hanging) this.getBukkitEntity(), org.bukkit.event.hanging.HangingBreakEvent.RemoveCause.PHYSICS);
            this.level().getCraftServer().getPluginManager().callEvent(event);

            if (this.isRemoved() || event.isCancelled()) {
                return;
            }
            // CraftBukkit end
            this.kill(serverLevel);
            this.dropItem(serverLevel, null);
        }
    }

    @Override
    public void push(double x, double y, double z, @Nullable Entity pushingEntity) { // Paper - override correct overload
        if (false && this.level() instanceof ServerLevel serverLevel && !this.isRemoved() && x * x + y * y + z * z > 0.0) { // CraftBukkit - not needed
            this.kill(serverLevel);
            this.dropItem(serverLevel, null);
        }
    }

    // CraftBukkit start - selectively save tile position
    @Override
    public void addAdditionalSaveData(CompoundTag nbt, boolean includeAll) {
        if (includeAll) {
            this.addAdditionalSaveData(nbt);
        }
    }
    // CraftBukkit end

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        BlockPos pos = this.getPos();
        tag.putInt("TileX", pos.getX());
        tag.putInt("TileY", pos.getY());
        tag.putInt("TileZ", pos.getZ());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        BlockPos blockPos = new BlockPos(tag.getInt("TileX"), tag.getInt("TileY"), tag.getInt("TileZ"));
        if (!blockPos.closerThan(this.blockPosition(), 16.0)) {
            LOGGER.error("Block-attached entity at invalid position: {}", blockPos);
        } else {
            this.pos = blockPos;
        }
    }

    public abstract void dropItem(ServerLevel level, @Nullable Entity entity);

    @Override
    protected boolean repositionEntityAfterLoad() {
        return false;
    }

    @Override
    public void setPos(double x, double y, double z) {
        this.pos = BlockPos.containing(x, y, z);
        this.recalculateBoundingBox();
        this.hasImpulse = true;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
    }

    @Override
    public void refreshDimensions() {
    }
}
