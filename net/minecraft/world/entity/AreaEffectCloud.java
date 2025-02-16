package net.minecraft.world.entity;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import org.slf4j.Logger;

public class AreaEffectCloud extends Entity implements TraceableEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TIME_BETWEEN_APPLICATIONS = 5;
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(AreaEffectCloud.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_WAITING = SynchedEntityData.defineId(AreaEffectCloud.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<ParticleOptions> DATA_PARTICLE = SynchedEntityData.defineId(AreaEffectCloud.class, EntityDataSerializers.PARTICLE);
    private static final float MAX_RADIUS = 32.0F;
    private static final float MINIMAL_RADIUS = 0.5F;
    private static final float DEFAULT_RADIUS = 3.0F;
    public static final float DEFAULT_WIDTH = 6.0F;
    public static final float HEIGHT = 0.5F;
    public PotionContents potionContents = PotionContents.EMPTY;
    private final Map<Entity, Integer> victims = Maps.newHashMap();
    private int duration = 600;
    public int waitTime = 20;
    public int reapplicationDelay = 20;
    public int durationOnUse;
    public float radiusOnUse;
    public float radiusPerTick;
    @Nullable
    private LivingEntity owner;
    @Nullable
    public UUID ownerUUID;

    public AreaEffectCloud(EntityType<? extends AreaEffectCloud> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    public AreaEffectCloud(Level level, double x, double y, double z) {
        this(EntityType.AREA_EFFECT_CLOUD, level);
        this.setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 3.0F);
        builder.define(DATA_WAITING, false);
        builder.define(DATA_PARTICLE, ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, -1));
    }

    public void setRadius(float radius) {
        if (!this.level().isClientSide) {
            this.getEntityData().set(DATA_RADIUS, Mth.clamp(radius, 0.0F, 32.0F));
        }
    }

    @Override
    public void refreshDimensions() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        super.refreshDimensions();
        this.setPos(x, y, z);
    }

    public float getRadius() {
        return this.getEntityData().get(DATA_RADIUS);
    }

    public void setPotionContents(PotionContents potionContents) {
        this.potionContents = potionContents;
        this.updateColor();
    }

    public void updateColor() {
        ParticleOptions particleOptions = this.entityData.get(DATA_PARTICLE);
        if (particleOptions instanceof ColorParticleOption colorParticleOption) {
            int i = this.potionContents.equals(PotionContents.EMPTY) ? 0 : this.potionContents.getColor();
            this.entityData.set(DATA_PARTICLE, ColorParticleOption.create(colorParticleOption.getType(), ARGB.opaque(i)));
        }
    }

    public void addEffect(MobEffectInstance effectInstance) {
        this.setPotionContents(this.potionContents.withEffectAdded(effectInstance));
    }

    public ParticleOptions getParticle() {
        return this.getEntityData().get(DATA_PARTICLE);
    }

    public void setParticle(ParticleOptions particleOption) {
        this.getEntityData().set(DATA_PARTICLE, particleOption);
    }

    protected void setWaiting(boolean waiting) {
        this.getEntityData().set(DATA_WAITING, waiting);
    }

    public boolean isWaiting() {
        return this.getEntityData().get(DATA_WAITING);
    }

    public int getDuration() {
        return this.duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel serverLevel) {
            this.serverTick(serverLevel);
        } else {
            this.clientTick();
        }
    }

    private void clientTick() {
        boolean isWaiting = this.isWaiting();
        float radius = this.getRadius();
        if (!isWaiting || !this.random.nextBoolean()) {
            ParticleOptions particle = this.getParticle();
            int i;
            float f;
            if (isWaiting) {
                i = 2;
                f = 0.2F;
            } else {
                i = Mth.ceil((float) Math.PI * radius * radius);
                f = radius;
            }

            for (int i1 = 0; i1 < i; i1++) {
                float f1 = this.random.nextFloat() * (float) (Math.PI * 2);
                float f2 = Mth.sqrt(this.random.nextFloat()) * f;
                double d = this.getX() + Mth.cos(f1) * f2;
                double y = this.getY();
                double d1 = this.getZ() + Mth.sin(f1) * f2;
                if (particle.getType() == ParticleTypes.ENTITY_EFFECT) {
                    if (isWaiting && this.random.nextBoolean()) {
                        this.level().addAlwaysVisibleParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, -1), d, y, d1, 0.0, 0.0, 0.0);
                    } else {
                        this.level().addAlwaysVisibleParticle(particle, d, y, d1, 0.0, 0.0, 0.0);
                    }
                } else if (isWaiting) {
                    this.level().addAlwaysVisibleParticle(particle, d, y, d1, 0.0, 0.0, 0.0);
                } else {
                    this.level()
                        .addAlwaysVisibleParticle(particle, d, y, d1, (0.5 - this.random.nextDouble()) * 0.15, 0.01F, (0.5 - this.random.nextDouble()) * 0.15);
                }
            }
        }
    }

    private void serverTick(ServerLevel level) {
        if (this.tickCount >= this.waitTime + this.duration) {
            this.discard();
        } else {
            boolean isWaiting = this.isWaiting();
            boolean flag = this.tickCount < this.waitTime;
            if (isWaiting != flag) {
                this.setWaiting(flag);
            }

            if (!flag) {
                float radius = this.getRadius();
                if (this.radiusPerTick != 0.0F) {
                    radius += this.radiusPerTick;
                    if (radius < 0.5F) {
                        this.discard();
                        return;
                    }

                    this.setRadius(radius);
                }

                if (this.tickCount % 5 == 0) {
                    this.victims.entrySet().removeIf(victim -> this.tickCount >= victim.getValue());
                    if (!this.potionContents.hasEffects()) {
                        this.victims.clear();
                    } else {
                        List<MobEffectInstance> list = Lists.newArrayList();
                        if (this.potionContents.potion().isPresent()) {
                            for (MobEffectInstance mobEffectInstance : this.potionContents.potion().get().value().getEffects()) {
                                list.add(
                                    new MobEffectInstance(
                                        mobEffectInstance.getEffect(),
                                        mobEffectInstance.mapDuration(duration -> duration / 4),
                                        mobEffectInstance.getAmplifier(),
                                        mobEffectInstance.isAmbient(),
                                        mobEffectInstance.isVisible()
                                    )
                                );
                            }
                        }

                        list.addAll(this.potionContents.customEffects());
                        List<LivingEntity> entitiesOfClass = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox());
                        if (!entitiesOfClass.isEmpty()) {
                            for (LivingEntity livingEntity : entitiesOfClass) {
                                if (!this.victims.containsKey(livingEntity)
                                    && livingEntity.isAffectedByPotions()
                                    && !list.stream().noneMatch(livingEntity::canBeAffected)) {
                                    double d = livingEntity.getX() - this.getX();
                                    double d1 = livingEntity.getZ() - this.getZ();
                                    double d2 = d * d + d1 * d1;
                                    if (d2 <= radius * radius) {
                                        this.victims.put(livingEntity, this.tickCount + this.reapplicationDelay);

                                        for (MobEffectInstance mobEffectInstance1 : list) {
                                            if (mobEffectInstance1.getEffect().value().isInstantenous()) {
                                                mobEffectInstance1.getEffect()
                                                    .value()
                                                    .applyInstantenousEffect(level, this, this.getOwner(), livingEntity, mobEffectInstance1.getAmplifier(), 0.5);
                                            } else {
                                                livingEntity.addEffect(new MobEffectInstance(mobEffectInstance1), this);
                                            }
                                        }

                                        if (this.radiusOnUse != 0.0F) {
                                            radius += this.radiusOnUse;
                                            if (radius < 0.5F) {
                                                this.discard();
                                                return;
                                            }

                                            this.setRadius(radius);
                                        }

                                        if (this.durationOnUse != 0) {
                                            this.duration = this.duration + this.durationOnUse;
                                            if (this.duration <= 0) {
                                                this.discard();
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public float getRadiusOnUse() {
        return this.radiusOnUse;
    }

    public void setRadiusOnUse(float radiusOnUse) {
        this.radiusOnUse = radiusOnUse;
    }

    public float getRadiusPerTick() {
        return this.radiusPerTick;
    }

    public void setRadiusPerTick(float radiusPerTick) {
        this.radiusPerTick = radiusPerTick;
    }

    public int getDurationOnUse() {
        return this.durationOnUse;
    }

    public void setDurationOnUse(int durationOnUse) {
        this.durationOnUse = durationOnUse;
    }

    public int getWaitTime() {
        return this.waitTime;
    }

    public void setWaitTime(int waitTime) {
        this.waitTime = waitTime;
    }

    public void setOwner(@Nullable LivingEntity owner) {
        this.owner = owner;
        this.ownerUUID = owner == null ? null : owner.getUUID();
    }

    @Nullable
    @Override
    public LivingEntity getOwner() {
        if (this.owner != null && !this.owner.isRemoved()) {
            return this.owner;
        } else {
            if (this.ownerUUID != null && this.level() instanceof ServerLevel serverLevel) {
                this.owner = serverLevel.getEntity(this.ownerUUID) instanceof LivingEntity livingEntity ? livingEntity : null;
            }

            return this.owner;
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        this.tickCount = compound.getInt("Age");
        this.duration = compound.getInt("Duration");
        this.waitTime = compound.getInt("WaitTime");
        this.reapplicationDelay = compound.getInt("ReapplicationDelay");
        this.durationOnUse = compound.getInt("DurationOnUse");
        this.radiusOnUse = compound.getFloat("RadiusOnUse");
        this.radiusPerTick = compound.getFloat("RadiusPerTick");
        this.setRadius(compound.getFloat("Radius"));
        if (compound.hasUUID("Owner")) {
            this.ownerUUID = compound.getUUID("Owner");
        }

        RegistryOps<Tag> registryOps = this.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        if (compound.contains("Particle", 10)) {
            ParticleTypes.CODEC
                .parse(registryOps, compound.get("Particle"))
                .resultOrPartial(string -> LOGGER.warn("Failed to parse area effect cloud particle options: '{}'", string))
                .ifPresent(this::setParticle);
        }

        if (compound.contains("potion_contents")) {
            PotionContents.CODEC
                .parse(registryOps, compound.get("potion_contents"))
                .resultOrPartial(string -> LOGGER.warn("Failed to parse area effect cloud potions: '{}'", string))
                .ifPresent(this::setPotionContents);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putInt("Age", this.tickCount);
        compound.putInt("Duration", this.duration);
        compound.putInt("WaitTime", this.waitTime);
        compound.putInt("ReapplicationDelay", this.reapplicationDelay);
        compound.putInt("DurationOnUse", this.durationOnUse);
        compound.putFloat("RadiusOnUse", this.radiusOnUse);
        compound.putFloat("RadiusPerTick", this.radiusPerTick);
        compound.putFloat("Radius", this.getRadius());
        RegistryOps<Tag> registryOps = this.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        compound.put("Particle", ParticleTypes.CODEC.encodeStart(registryOps, this.getParticle()).getOrThrow());
        if (this.ownerUUID != null) {
            compound.putUUID("Owner", this.ownerUUID);
        }

        if (!this.potionContents.equals(PotionContents.EMPTY)) {
            Tag tag = PotionContents.CODEC.encodeStart(registryOps, this.potionContents).getOrThrow();
            compound.put("potion_contents", tag);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_RADIUS.equals(key)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(key);
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(this.getRadius() * 2.0F, 0.5F);
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }
}
