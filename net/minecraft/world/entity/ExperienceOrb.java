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

// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerExpCooldownChangeEvent;
// CraftBukkit end

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
    // Paper start
    @javax.annotation.Nullable
    public java.util.UUID sourceEntityId;
    @javax.annotation.Nullable
    public java.util.UUID triggerEntityId;
    public org.bukkit.entity.ExperienceOrb.SpawnReason spawnReason = org.bukkit.entity.ExperienceOrb.SpawnReason.UNKNOWN;

    private void loadPaperNBT(CompoundTag tag) {
        if (!tag.contains("Paper.ExpData", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag comp = tag.getCompound("Paper.ExpData");
        if (comp.hasUUID("source")) {
            this.sourceEntityId = comp.getUUID("source");
        }
        if (comp.hasUUID("trigger")) {
            this.triggerEntityId = comp.getUUID("trigger");
        }
        if (comp.contains("reason")) {
            String reason = comp.getString("reason");
            try {
                this.spawnReason = org.bukkit.entity.ExperienceOrb.SpawnReason.valueOf(reason);
            } catch (Exception e) {
                this.level().getCraftServer().getLogger().warning("Invalid spawnReason set for experience orb: " + e.getMessage() + " - " + reason);
            }
        }
    }
    private void savePaperNBT(CompoundTag tag) {
        CompoundTag comp = new CompoundTag();
        if (this.sourceEntityId != null) {
            comp.putUUID("source", this.sourceEntityId);
        }
        if (this.triggerEntityId != null) {
            comp.putUUID("trigger", triggerEntityId);
        }
        if (this.spawnReason != null && this.spawnReason != org.bukkit.entity.ExperienceOrb.SpawnReason.UNKNOWN) {
            comp.putString("reason", this.spawnReason.name());
        }
        tag.put("Paper.ExpData", comp);
    }

    @io.papermc.paper.annotation.DoNotUse
    @Deprecated
    public ExperienceOrb(Level level, double x, double y, double z, int value) {
        this(level, x, y, z, value, null, null);
    }

    public ExperienceOrb(Level level, double x, double y, double z, int value, @javax.annotation.Nullable org.bukkit.entity.ExperienceOrb.SpawnReason reason, @javax.annotation.Nullable Entity triggerId) {
        this(level, x, y, z, value, reason, triggerId, null);
    }

    public ExperienceOrb(Level level, double x, double y, double z, int value, @javax.annotation.Nullable org.bukkit.entity.ExperienceOrb.SpawnReason reason, @javax.annotation.Nullable Entity triggerId, @javax.annotation.Nullable Entity sourceId) {
        this(EntityType.EXPERIENCE_ORB, level);
        this.sourceEntityId = sourceId != null ? sourceId.getUUID() : null;
        this.triggerEntityId = triggerId != null ? triggerId.getUUID() : null;
        this.spawnReason = reason != null ? reason : org.bukkit.entity.ExperienceOrb.SpawnReason.UNKNOWN;
        // Paper end
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
        Player prevTarget = this.followingPlayer;// CraftBukkit - store old target
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

        // CraftBukkit start
        boolean cancelled = false;
        if (this.followingPlayer != prevTarget) {
            EntityTargetLivingEntityEvent event = CraftEventFactory.callEntityTargetLivingEvent(this, this.followingPlayer, (this.followingPlayer != null) ? EntityTargetEvent.TargetReason.CLOSEST_PLAYER : EntityTargetEvent.TargetReason.FORGOT_TARGET);
            LivingEntity target = (event.getTarget() == null) ? null : ((org.bukkit.craftbukkit.entity.CraftLivingEntity) event.getTarget()).getHandle();
            cancelled = event.isCancelled();

            if (cancelled) {
                this.followingPlayer = prevTarget;
            } else {
                this.followingPlayer = (target instanceof Player) ? (Player) target : null;
            }
        }

        if (this.followingPlayer != null && !cancelled) {
            // CraftBukkit end
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
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
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
        // Paper start - add reasons for orbs
        award(level, pos, amount, null, null, null);
    }
    public static void award(ServerLevel level, Vec3 pos, int amount, org.bukkit.entity.ExperienceOrb.SpawnReason reason, Entity triggerId) {
        award(level, pos, amount, reason, triggerId, null);
    }
    public static void award(ServerLevel level, Vec3 pos, int amount, org.bukkit.entity.ExperienceOrb.SpawnReason reason, Entity triggerId, Entity sourceId) {
        // Paper end - add reasons for orbs
        while (amount > 0) {
            int experienceValue = getExperienceValue(amount);
            amount -= experienceValue;
            if (!tryMergeToExisting(level, pos, experienceValue)) {
                level.addFreshEntity(new ExperienceOrb(level, pos.x(), pos.y(), pos.z(), experienceValue, reason, triggerId, sourceId)); // Paper - add reason
            }
        }
    }

    private static boolean tryMergeToExisting(ServerLevel level, Vec3 pos, int amount) {
        // Paper - TODO some other event for this kind of merge
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
        // Paper start - call orb merge event
        if (!new com.destroystokyo.paper.event.entity.ExperienceOrbMergeEvent((org.bukkit.entity.ExperienceOrb) this.getBukkitEntity(), (org.bukkit.entity.ExperienceOrb) orb.getBukkitEntity()).callEvent()) {
            return;
        }
        // Paper end - call orb merge event
        this.count = this.count + orb.count;
        this.age = Math.min(this.age, orb.age);
        orb.discard(EntityRemoveEvent.Cause.MERGE); // CraftBukkit - add Bukkit remove cause
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
                this.discard(EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
            }

            return true;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        compound.putShort("Health", (short)this.health);
        compound.putShort("Age", (short)this.age);
        compound.putInt("Value", this.value); // Paper - save as Integer
        compound.putInt("Count", this.count);
        this.savePaperNBT(compound); // Paper
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        this.health = compound.getShort("Health");
        this.age = compound.getShort("Age");
        this.value = compound.getInt("Value"); // Paper - load as Integer
        this.count = Math.max(compound.getInt("Count"), 1);
        this.loadPaperNBT(compound); // Paper
    }

    @Override
    public void playerTouch(Player entity) {
        if (entity instanceof ServerPlayer serverPlayer) {
            if (entity.takeXpDelay == 0 && new com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent(serverPlayer.getBukkitEntity(), (org.bukkit.entity.ExperienceOrb) this.getBukkitEntity()).callEvent()) { // Paper - PlayerPickupExperienceEvent
                entity.takeXpDelay = CraftEventFactory.callPlayerXpCooldownEvent(entity, this.level().purpurConfig.playerExpPickupDelay, PlayerExpCooldownChangeEvent.ChangeReason.PICKUP_ORB).getNewCooldown(); // CraftBukkit - entityhuman.takeXpDelay = 2; // Purpur - Configurable player pickup exp delay
                entity.take(this, 1);
                int i = this.repairPlayerItems(serverPlayer, this.value);
                if (i > 0) {
                    entity.giveExperiencePoints(CraftEventFactory.callPlayerExpChangeEvent(entity, this).getAmount()); // CraftBukkit - this.value -> event.getAmount() // Paper - supply experience orb object
                }

                this.count--;
                if (this.count == 0) {
                    this.discard(EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
                }
            }
        }
    }

    private int repairPlayerItems(ServerPlayer player, int value) {
        Optional<EnchantedItemInUse> randomItemWith = level().purpurConfig.useBetterMending ? EnchantmentHelper.getMostDamagedItemWith(EnchantmentEffectComponents.REPAIR_WITH_XP, player) : EnchantmentHelper.getRandomItemWith( // Purpur - Add option to mend the most damaged equipment first
            EnchantmentEffectComponents.REPAIR_WITH_XP, player, ItemStack::isDamaged
        );
        if (randomItemWith.isPresent()) {
            ItemStack itemStack = randomItemWith.get().itemStack();
            int i = EnchantmentHelper.modifyDurabilityToRepairFromXp(player.serverLevel(), itemStack, value);
            int min = Math.min(i, itemStack.getDamageValue());
            // CraftBukkit start
            // Paper start - mending event
            final int consumedExperience = min > 0 ? min * value / i : 0;
            org.bukkit.event.player.PlayerItemMendEvent event = CraftEventFactory.callPlayerItemMendEvent(player, this, itemStack, randomItemWith.get().inSlot(), min, consumedExperience);
            // Paper end - mending event
            min = event.getRepairAmount();
            if (event.isCancelled()) {
                return value;
            }
            // CraftBukkit end
            itemStack.setDamageValue(itemStack.getDamageValue() - min);
            if (min > 0) {
                int i1 = value - min * value / i; // Paper - diff on change - expand PlayerMendEvents
                if (i1 > 0) {
                    // this.value = l; // CraftBukkit - update exp value of orb for PlayerItemMendEvent calls // Paper - the value field should not be mutated here because it doesn't take "count" into account
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
        // CraftBukkit start
        if (expValue > 162670129) return expValue - 100000;
        if (expValue > 81335063) return 81335063;
        if (expValue > 40667527) return 40667527;
        if (expValue > 20333759) return 20333759;
        if (expValue > 10166857) return 10166857;
        if (expValue > 5083423) return 5083423;
        if (expValue > 2541701) return 2541701;
        if (expValue > 1270849) return 1270849;
        if (expValue > 635413) return 635413;
        if (expValue > 317701) return 317701;
        if (expValue > 158849) return 158849;
        if (expValue > 79423) return 79423;
        if (expValue > 39709) return 39709;
        if (expValue > 19853) return 19853;
        if (expValue > 9923) return 9923;
        if (expValue > 4957) return 4957;
        // CraftBukkit end
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
