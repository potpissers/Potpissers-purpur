package net.minecraft.world.entity.projectile;

import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class ThrownPotion extends ThrowableItemProjectile {
    public static final double SPLASH_RANGE = 4.0;
    private static final double SPLASH_RANGE_SQ = 16.0;
    public static final Predicate<LivingEntity> WATER_SENSITIVE_OR_ON_FIRE = entity -> entity.isSensitiveToWater() || entity.isOnFire();

    public ThrownPotion(EntityType<? extends ThrownPotion> entityType, Level level) {
        super(entityType, level);
    }

    public ThrownPotion(Level level, LivingEntity owner, ItemStack item) {
        super(EntityType.POTION, owner, level, item);
    }

    public ThrownPotion(Level level, double x, double y, double z, ItemStack item) {
        super(EntityType.POTION, x, y, z, level, item);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SPLASH_POTION;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            ItemStack item = this.getItem();
            Direction direction = result.getDirection();
            BlockPos blockPos = result.getBlockPos();
            BlockPos blockPos1 = blockPos.relative(direction);
            PotionContents potionContents = item.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            if (potionContents.is(Potions.WATER)) {
                this.dowseFire(blockPos1);
                this.dowseFire(blockPos1.relative(direction.getOpposite()));

                for (Direction direction1 : Direction.Plane.HORIZONTAL) {
                    this.dowseFire(blockPos1.relative(direction1));
                }
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        // Paper start - More projectile API
        this.splash(result);
    }
    public void splash(@Nullable HitResult result) {
        // Paper end - More projectile API
        if (this.level() instanceof ServerLevel serverLevel) {
            ItemStack item = this.getItem();
            PotionContents potionContents = item.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            boolean showParticles = true; // Paper - Fix potions splash events
            if (potionContents.is(Potions.WATER)) {
                showParticles = this.applyWater(serverLevel, result); // Paper - Fix potions splash events
            } else if (true || potionContents.hasEffects()) { // CraftBukkit - Call event even if no effects to apply
                if (this.isLingering()) {
                    showParticles = this.makeAreaOfEffectCloud(potionContents, result); // CraftBukkit - Pass MovingObjectPosition // Paper
                } else {
                    showParticles = this.applySplash(
                        serverLevel, potionContents.getAllEffects(), result != null && result.getType() == HitResult.Type.ENTITY ? ((EntityHitResult)result).getEntity() : null, result // CraftBukkit - Pass MovingObjectPosition // Paper - More projectile API
                    );
                }
            }

            if (showParticles) { // Paper - Fix potions splash events
            int i = potionContents.potion().isPresent() && potionContents.potion().get().value().hasInstantEffects() ? 2007 : 2002;
            serverLevel.levelEvent(i, this.blockPosition(), potionContents.getColor());
            } // Paper - Fix potions splash events
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }
    }

    private static final Predicate<net.minecraft.world.entity.LivingEntity> APPLY_WATER_GET_ENTITIES_PREDICATE = ThrownPotion.WATER_SENSITIVE_OR_ON_FIRE.or(Axolotl.class::isInstance); // Paper - Fix potions splash events

    private boolean applyWater(ServerLevel level, @Nullable HitResult result) { // Paper - Fix potions splash events
        AABB aabb = this.getBoundingBox().inflate(4.0, 2.0, 4.0);

        // Paper start - Fix potions splash events
        java.util.Map<org.bukkit.entity.LivingEntity, Double> affected = new java.util.HashMap<>();
        java.util.Set<org.bukkit.entity.LivingEntity> rehydrate = new java.util.HashSet<>();
        java.util.Set<org.bukkit.entity.LivingEntity> extinguish = new java.util.HashSet<>();
        for (LivingEntity livingEntity : this.level().getEntitiesOfClass(LivingEntity.class, aabb, APPLY_WATER_GET_ENTITIES_PREDICATE)) {
            if (livingEntity instanceof Axolotl axolotl) {
                rehydrate.add(((org.bukkit.entity.Axolotl) axolotl.getBukkitEntity()));
            }
            // Paper end - Fix potions splash events
            double d = this.distanceToSqr(livingEntity);
            if (d < 16.0) {
                if (livingEntity.isSensitiveToWater()) {
                    affected.put(livingEntity.getBukkitLivingEntity(), 1.0);
                    // livingEntity.hurtServer(level, this.damageSources().indirectMagic(this, this.getOwner()), 1.0F);
                }

                if (livingEntity.isOnFire() && livingEntity.isAlive()) {
                    extinguish.add(livingEntity.getBukkitLivingEntity());
                    // livingEntity.extinguishFire();
                }
            }
        }

        io.papermc.paper.event.entity.WaterBottleSplashEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callWaterBottleSplashEvent(
            this, result, affected, rehydrate, extinguish
        );
        if (!event.isCancelled()) {
            for (org.bukkit.entity.LivingEntity affectedEntity : event.getToDamage()) {
                ((org.bukkit.craftbukkit.entity.CraftLivingEntity) affectedEntity).getHandle().hurtServer(level, this.damageSources().indirectMagic(this, this.getOwner()), 1.0F);
            }
            for (org.bukkit.entity.LivingEntity toExtinguish : event.getToExtinguish()) {
                ((org.bukkit.craftbukkit.entity.CraftLivingEntity) toExtinguish).getHandle().extinguishFire();
            }
            for (org.bukkit.entity.LivingEntity toRehydrate : event.getToRehydrate()) {
                if (((org.bukkit.craftbukkit.entity.CraftLivingEntity) toRehydrate).getHandle() instanceof Axolotl axolotl) {
                    axolotl.rehydrate();
                }
            }
            // Paper end - Fix potions splash events
        }
        return !event.isCancelled(); // Paper - Fix potions splash events
    }

    private boolean applySplash(ServerLevel level, Iterable<MobEffectInstance> effects, @Nullable Entity entity, @Nullable HitResult result) { // CraftBukkit - Pass MovingObjectPosition // Paper - Fix potions splash events & More projectile API
        AABB aabb = this.getBoundingBox().inflate(4.0, 2.0, 4.0);
        List<LivingEntity> entitiesOfClass = level.getEntitiesOfClass(LivingEntity.class, aabb);
        java.util.Map<org.bukkit.entity.LivingEntity, Double> affected = new java.util.HashMap<>(); // CraftBukkit
        if (!entitiesOfClass.isEmpty()) {
            Entity effectSource = this.getEffectSource();

            for (LivingEntity livingEntity : entitiesOfClass) {
                if (livingEntity.isAffectedByPotions()) {
                    double d = this.distanceToSqr(livingEntity);
                    if (d < 16.0) {
                        double d1;
                        // Paper - diff on change, used when calling the splash event for water splash potions
                        if (livingEntity == entity) {
                            d1 = 1.0;
                        } else {
                            d1 = 1.0 - Math.sqrt(d) / 4.0;
                        }

                        affected.put(livingEntity.getBukkitLivingEntity(), d1);
                    }
                }
            }
        }
        org.bukkit.event.entity.PotionSplashEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPotionSplashEvent(this, result, affected);
        if (!event.isCancelled() && entitiesOfClass != null && !entitiesOfClass.isEmpty()) { // do not process effects if there are no effects to process
            Entity effectSource = this.getEffectSource();
            for (org.bukkit.entity.LivingEntity victim : event.getAffectedEntities()) {
                if (!(victim instanceof org.bukkit.craftbukkit.entity.CraftLivingEntity craftLivingEntity)) {
                    continue;
                }
                net.minecraft.world.entity.LivingEntity livingEntity = craftLivingEntity.getHandle();
                double d1 = event.getIntensity(victim);
                // CraftBukkit end
                for (MobEffectInstance mobEffectInstance : effects) {
                    Holder<MobEffect> effect = mobEffectInstance.getEffect();
                    // CraftBukkit start - Abide by PVP settings - for players only!
                    if (!this.level().pvpMode && this.getOwner() instanceof ServerPlayer && livingEntity instanceof ServerPlayer && livingEntity != this.getOwner()) {
                        MobEffect mobEffect = effect.value();
                        if (mobEffect == net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN || mobEffect == net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN || mobEffect == net.minecraft.world.effect.MobEffects.HARM || mobEffect == net.minecraft.world.effect.MobEffects.BLINDNESS
                                || mobEffect == net.minecraft.world.effect.MobEffects.HUNGER || mobEffect == net.minecraft.world.effect.MobEffects.WEAKNESS || mobEffect == net.minecraft.world.effect.MobEffects.POISON) {
                            continue;
                        }
                    }
                    // CraftBukkit end
                    if (effect.value().isInstantenous()) {
                        effect.value().applyInstantenousEffect(level, this, this.getOwner(), livingEntity, mobEffectInstance.getAmplifier(), d1);
                    } else {
                        int i = mobEffectInstance.mapDuration(i1 -> (int)(d1 * i1 + 0.5));
                        MobEffectInstance mobEffectInstance1 = new MobEffectInstance(
                            effect, i, mobEffectInstance.getAmplifier(), mobEffectInstance.isAmbient(), mobEffectInstance.isVisible()
                        );
                        if (!mobEffectInstance1.endsWithin(20)) {
                            livingEntity.addEffect(mobEffectInstance1, effectSource, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.POTION_SPLASH); // CraftBukkit
                        }
                    }
                }
            }
        }
        return !event.isCancelled(); // Paper - Fix potions splash events
    }

    private boolean makeAreaOfEffectCloud(PotionContents potionContents, @Nullable HitResult result) { // CraftBukkit - Pass MovingObjectPosition // Paper - More projectile API
        AreaEffectCloud areaEffectCloud = new AreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
        if (this.getOwner() instanceof LivingEntity livingEntity) {
            areaEffectCloud.setOwner(livingEntity);
        }

        areaEffectCloud.setRadius(3.0F);
        areaEffectCloud.setRadiusOnUse(-0.5F);
        areaEffectCloud.setWaitTime(10);
        areaEffectCloud.setRadiusPerTick(-areaEffectCloud.getRadius() / areaEffectCloud.getDuration());
        areaEffectCloud.setPotionContents(potionContents);
        boolean noEffects = potionContents.hasEffects(); // Paper - Fix potions splash events
        // CraftBukkit start
        org.bukkit.event.entity.LingeringPotionSplashEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callLingeringPotionSplashEvent(this, result, areaEffectCloud);
        if (!(event.isCancelled() || areaEffectCloud.isRemoved() || (!event.allowsEmptyCreation() && (noEffects && !areaEffectCloud.potionContents.hasEffects())))) { // Paper - don't spawn area effect cloud if the effects were empty and not changed during the event handling
            this.level().addFreshEntity(areaEffectCloud);
        } else {
            areaEffectCloud.discard(null); // CraftBukkit - add Bukkit remove cause
        }
        // CraftBukkit end
        return !event.isCancelled(); // Paper - Fix potions splash events
    }

    public boolean isLingering() {
        return this.getItem().is(Items.LINGERING_POTION);
    }

    private void dowseFire(BlockPos pos) {
        BlockState blockState = this.level().getBlockState(pos);
        if (blockState.is(BlockTags.FIRE)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this, pos, blockState.getFluidState().createLegacyBlock())) { // Paper - fix wrong block state
                this.level().destroyBlock(pos, false, this);
            }
            // CraftBukkit end
        } else if (AbstractCandleBlock.isLit(blockState)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this, pos, blockState.setValue(AbstractCandleBlock.LIT, false))) {
                AbstractCandleBlock.extinguish(null, blockState, this.level(), pos);
            }
            // CraftBukkit end
        } else if (CampfireBlock.isLitCampfire(blockState)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this, pos, blockState.setValue(CampfireBlock.LIT, false))) {
                this.level().levelEvent(null, 1009, pos, 0);
                CampfireBlock.dowse(this.getOwner(), this.level(), pos, blockState);
                this.level().setBlockAndUpdate(pos, blockState.setValue(CampfireBlock.LIT, false));
            }
            // CraftBukkit end
        }
    }

    @Override
    public DoubleDoubleImmutablePair calculateHorizontalHurtKnockbackDirection(LivingEntity entity, DamageSource damageSource) {
        double d = entity.position().x - this.position().x;
        double d1 = entity.position().z - this.position().z;
        return DoubleDoubleImmutablePair.of(d, d1);
    }
}
