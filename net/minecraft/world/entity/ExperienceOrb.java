package net.minecraft.world.entity;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddExperienceOrbPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ExperienceOrb extends Entity {
    private static final int LIFETIME = 6000;
    private static final int ENTITY_SCAN_PERIOD = 20;
    private static final int MAX_FOLLOW_DIST = 8;
    private static final int ORB_GROUPS_PER_AREA = 40;
    private static final double ORB_MERGE_DISTANCE = 0.5;
    private int age;
    private int health = 5;
    public int value;
    public int count = 1;
    private Player followingPlayer;

    public ExperienceOrb(Level level, double x, double y, double z, int value) {
        this(EntityType.EXPERIENCE_ORB, level);
        this.setPos(x, y, z);
        this.setYRot((float)(this.random.nextDouble() * 360.0));
        this.setDeltaMovement(
            (this.random.nextDouble() * 0.2F - 0.1F) * 2.0, this.random.nextDouble() * 0.2 * 2.0, (this.random.nextDouble() * 0.2F - 0.1F) * 2.0
        );
        this.value = value;
    }

    public ExperienceOrb(EntityType<? extends ExperienceOrb> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected double getDefaultGravity() {
        return 0.03;
    }

    @Override
    public void tick() {
        super.tick();
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        if (this.isEyeInFluid(FluidTags.WATER)) {
            this.setUnderwaterMovement();
        } else {
            this.applyGravity();
        }

        if (this.level().getFluidState(this.blockPosition()).is(FluidTags.LAVA)) {
            this.setDeltaMovement((this.random.nextFloat() - this.random.nextFloat()) * 0.2F, 0.2F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
        }

        if (!this.level().noCollision(this.getBoundingBox())) {
            this.moveTowardsClosestSpace(this.getX(), (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0, this.getZ());
        }

        if (this.tickCount % 20 == 1) {
            this.scanForEntities();
        }

        if (this.followingPlayer != null && (this.followingPlayer.isSpectator() || this.followingPlayer.isDeadOrDying())) {
            this.followingPlayer = null;
        }

        if (this.followingPlayer != null) {
            Vec3 vec3 = new Vec3(
                this.followingPlayer.getX() - this.getX(),
                this.followingPlayer.getY() + this.followingPlayer.getEyeHeight() / 2.0 - this.getY(),
                this.followingPlayer.getZ() - this.getZ()
            );
            double d = vec3.lengthSqr();
            if (d < 64.0) {
                double d1 = 1.0 - Math.sqrt(d) / 8.0;
                this.setDeltaMovement(this.getDeltaMovement().add(vec3.normalize().scale(d1 * d1 * 0.1)));
            }
        }

        double d2 = this.getDeltaMovement().y;
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.applyEffectsFromBlocks();
        float f = 0.98F;
        if (this.onGround()) {
            f = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.98F;
        }

        this.setDeltaMovement(this.getDeltaMovement().multiply(f, 0.98, f));
        if (this.onGround()) {
            this.setDeltaMovement(new Vec3(this.getDeltaMovement().x, -d2 * 0.4, this.getDeltaMovement().z));
        }

        this.age++;
        if (this.age >= 6000) {
            this.discard();
        }
    }

    @Override
    public BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.999999F);
    }

    private void scanForEntities() {
        if (this.followingPlayer == null || this.followingPlayer.distanceToSqr(this) > 64.0) {
            this.followingPlayer = this.level().getNearestPlayer(this, 8.0);
        }

        if (this.level() instanceof ServerLevel) {
            for (ExperienceOrb experienceOrb : this.level()
                .getEntities(EntityTypeTest.forClass(ExperienceOrb.class), this.getBoundingBox().inflate(0.5), this::canMerge)) {
                this.merge(experienceOrb);
            }
        }
    }

    public static void award(ServerLevel level, Vec3 pos, int amount) {
        while (amount > 0) {
            int experienceValue = getExperienceValue(amount);
            amount -= experienceValue;
            if (!tryMergeToExisting(level, pos, experienceValue)) {
                level.addFreshEntity(new ExperienceOrb(level, pos.x(), pos.y(), pos.z(), experienceValue));
            }
        }
    }

    private static boolean tryMergeToExisting(ServerLevel level, Vec3 pos, int amount) {
        AABB aabb = AABB.ofSize(pos, 1.0, 1.0, 1.0);
        int randomInt = level.getRandom().nextInt(40);
        List<ExperienceOrb> entities = level.getEntities(EntityTypeTest.forClass(ExperienceOrb.class), aabb, orb -> canMerge(orb, randomInt, amount));
        if (!entities.isEmpty()) {
            ExperienceOrb experienceOrb = entities.get(0);
            experienceOrb.count++;
            experienceOrb.age = 0;
            return true;
        } else {
            return false;
        }
    }

    private boolean canMerge(ExperienceOrb orb) {
        return orb != this && canMerge(orb, this.getId(), this.value);
    }

    private static boolean canMerge(ExperienceOrb orb, int amount, int other) {
        return !orb.isRemoved() && (orb.getId() - amount) % 40 == 0 && orb.value == other;
    }

    private void merge(ExperienceOrb orb) {
        this.count = this.count + orb.count;
        this.age = Math.min(this.age, orb.age);
        orb.discard();
    }

    private void setUnderwaterMovement() {
        Vec3 deltaMovement = this.getDeltaMovement();
        this.setDeltaMovement(deltaMovement.x * 0.99F, Math.min(deltaMovement.y + 5.0E-4F, 0.06F), deltaMovement.z * 0.99F);
    }

    @Override
    protected void doWaterSplashEffect() {
    }

    @Override
    public final boolean hurtClient(DamageSource damageSource) {
        return !this.isInvulnerableToBase(damageSource);
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableToBase(damageSource)) {
            return false;
        } else {
            this.markHurt();
            this.health = (int)(this.health - amount);
            if (this.health <= 0) {
                this.discard();
            }

            return true;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        compound.putShort("Health", (short)this.health);
        compound.putShort("Age", (short)this.age);
        compound.putShort("Value", (short)this.value);
        compound.putInt("Count", this.count);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        this.health = compound.getShort("Health");
        this.age = compound.getShort("Age");
        this.value = compound.getShort("Value");
        this.count = Math.max(compound.getInt("Count"), 1);
    }

    @Override
    public void playerTouch(Player entity) {
        if (entity instanceof ServerPlayer serverPlayer) {
            if (entity.takeXpDelay == 0) {
                entity.takeXpDelay = 2;
                entity.take(this, 1);
                int i = this.repairPlayerItems(serverPlayer, this.value);
                if (i > 0) {
                    entity.giveExperiencePoints(i);
                }

                this.count--;
                if (this.count == 0) {
                    this.discard();
                }
            }
        }
    }

    private int repairPlayerItems(ServerPlayer player, int value) {
        Optional<EnchantedItemInUse> randomItemWith = EnchantmentHelper.getRandomItemWith(
            EnchantmentEffectComponents.REPAIR_WITH_XP, player, ItemStack::isDamaged
        );
        if (randomItemWith.isPresent()) {
            ItemStack itemStack = randomItemWith.get().itemStack();
            int i = EnchantmentHelper.modifyDurabilityToRepairFromXp(player.serverLevel(), itemStack, value);
            int min = Math.min(i, itemStack.getDamageValue());
            itemStack.setDamageValue(itemStack.getDamageValue() - min);
            if (min > 0) {
                int i1 = value - min * value / i;
                if (i1 > 0) {
                    return this.repairPlayerItems(player, i1);
                }
            }

            return 0;
        } else {
            return value;
        }
    }

    public int getValue() {
        return this.value;
    }

    public int getIcon() {
        if (this.value >= 2477) {
            return 10;
        } else if (this.value >= 1237) {
            return 9;
        } else if (this.value >= 617) {
            return 8;
        } else if (this.value >= 307) {
            return 7;
        } else if (this.value >= 149) {
            return 6;
        } else if (this.value >= 73) {
            return 5;
        } else if (this.value >= 37) {
            return 4;
        } else if (this.value >= 17) {
            return 3;
        } else if (this.value >= 7) {
            return 2;
        } else {
            return this.value >= 3 ? 1 : 0;
        }
    }

    public static int getExperienceValue(int expValue) {
        if (expValue >= 2477) {
            return 2477;
        } else if (expValue >= 1237) {
            return 1237;
        } else if (expValue >= 617) {
            return 617;
        } else if (expValue >= 307) {
            return 307;
        } else if (expValue >= 149) {
            return 149;
        } else if (expValue >= 73) {
            return 73;
        } else if (expValue >= 37) {
            return 37;
        } else if (expValue >= 17) {
            return 17;
        } else if (expValue >= 7) {
            return 7;
        } else {
            return expValue >= 3 ? 3 : 1;
        }
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddExperienceOrbPacket(this, entity);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.AMBIENT;
    }
}
