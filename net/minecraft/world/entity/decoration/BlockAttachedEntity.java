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
    private int checkInterval;
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
            if (this.checkInterval++ == 100) {
                this.checkInterval = 0;
                if (!this.isRemoved() && !this.survives()) {
                    this.discard();
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
            this.kill(serverLevel);
            this.dropItem(serverLevel, null);
        }
    }

    @Override
    public void push(double x, double y, double z) {
        if (this.level() instanceof ServerLevel serverLevel && !this.isRemoved() && x * x + y * y + z * z > 0.0) {
            this.kill(serverLevel);
            this.dropItem(serverLevel, null);
        }
    }

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
