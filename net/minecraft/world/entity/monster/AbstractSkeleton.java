package net.minecraft.world.entity.monster;

import java.time.LocalDate;
import java.time.temporal.ChronoField;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RestrictSunGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public abstract class AbstractSkeleton extends Monster implements RangedAttackMob {
    private static final int HARD_ATTACK_INTERVAL = 20;
    private static final int NORMAL_ATTACK_INTERVAL = 40;
    private final RangedBowAttackGoal<AbstractSkeleton> bowGoal = new RangedBowAttackGoal<>(this, 1.0, 20, 15.0F);
    private final MeleeAttackGoal meleeGoal = new MeleeAttackGoal(this, 1.2, false) {
        @Override
        public void stop() {
            super.stop();
            AbstractSkeleton.this.setAggressive(false);
        }

        @Override
        public void start() {
            super.start();
            AbstractSkeleton.this.setAggressive(true);
        }
    };
    private boolean shouldBurnInDay = true; // Paper - shouldBurnInDay API

    protected AbstractSkeleton(EntityType<? extends AbstractSkeleton> entityType, Level level) {
        super(entityType, level);
        this.reassessWeaponGoal();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new RestrictSunGoal(this));
        this.goalSelector.addGoal(3, new FleeSunGoal(this, 1.0));
        this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Wolf.class, 6.0F, 1.0, 1.2));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Turtle.class, 10, true, false, Turtle.BABY_ON_LAND_SELECTOR));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    // Paper start - shouldBurnInDay API
    public boolean shouldBurnInDay() {
        return this.shouldBurnInDay;
    }

    public void setShouldBurnInDay(boolean shouldBurnInDay) {
        this.shouldBurnInDay = shouldBurnInDay;
    }
    // Paper end - shouldBurnInDay API

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        this.playSound(this.getStepSound(), 0.15F, 1.0F);
    }

    abstract SoundEvent getStepSound();

    @Override
    public void aiStep() {
        boolean isSunBurnTick = this.shouldBurnInDay && this.isSunBurnTick(); // Paper - shouldBurnInDay API
        if (isSunBurnTick) {
            ItemStack itemBySlot = this.getItemBySlot(EquipmentSlot.HEAD);
            if (!itemBySlot.isEmpty()) {
                if (itemBySlot.isDamageableItem()) {
                    Item item = itemBySlot.getItem();
                    itemBySlot.setDamageValue(itemBySlot.getDamageValue() + this.random.nextInt(2));
                    if (itemBySlot.getDamageValue() >= itemBySlot.getMaxDamage()) {
                        this.onEquippedItemBroken(item, EquipmentSlot.HEAD);
                        this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    }
                }

                isSunBurnTick = false;
            }

            if (isSunBurnTick) {
                this.igniteForSeconds(8.0F);
            }
        }

        super.aiStep();
    }

    @Override
    public void rideTick() {
        super.rideTick();
        if (this.getControlledVehicle() instanceof PathfinderMob pathfinderMob) {
            this.yBodyRot = pathfinderMob.yBodyRot;
        }
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        super.populateDefaultEquipmentSlots(random, difficulty);
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        spawnGroupData = super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
        RandomSource random = level.getRandom();
        this.populateDefaultEquipmentSlots(random, difficulty);
        this.populateDefaultEquipmentEnchantments(level, random, difficulty);
        this.reassessWeaponGoal();
        this.setCanPickUpLoot(this.level().paperConfig().entities.behavior.mobsCanAlwaysPickUpLoot.skeletons || random.nextFloat() < 0.55F * difficulty.getSpecialMultiplier()); // Paper - Add world settings for mobs picking up loot
        if (this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            if (net.minecraft.world.entity.ambient.Bat.isHalloweenSeason(level.getMinecraftWorld()) && this.random.nextFloat() < this.level().purpurConfig.chanceHeadHalloweenOnEntity) { // Purpur - Halloween options and optimizations
                this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(random.nextFloat() < 0.1F ? Blocks.JACK_O_LANTERN : Blocks.CARVED_PUMPKIN));
                this.armorDropChances[EquipmentSlot.HEAD.getIndex()] = 0.0F;
            }
        }

        return spawnGroupData;
    }

    public void reassessWeaponGoal() {
        if (this.level() != null && !this.level().isClientSide) {
            this.goalSelector.removeGoal(this.meleeGoal);
            this.goalSelector.removeGoal(this.bowGoal);
            ItemStack itemInHand = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
            if (itemInHand.is(Items.BOW)) {
                int hardAttackInterval = this.getHardAttackInterval();
                if (this.level().getDifficulty() != Difficulty.HARD) {
                    hardAttackInterval = this.getAttackInterval();
                }

                this.bowGoal.setMinAttackInterval(hardAttackInterval);
                this.goalSelector.addGoal(4, this.bowGoal);
            } else {
                this.goalSelector.addGoal(4, this.meleeGoal);
            }
        }
    }

    protected int getHardAttackInterval() {
        return 20;
    }

    protected int getAttackInterval() {
        return 40;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        ItemStack itemInHand = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        ItemStack projectile = this.getProjectile(itemInHand);
        AbstractArrow arrow = this.getArrow(projectile, distanceFactor, itemInHand);
        double d = target.getX() - this.getX();
        double d1 = target.getY(0.3333333333333333) - arrow.getY();
        double d2 = target.getZ() - this.getZ();
        double squareRoot = Math.sqrt(d * d + d2 * d2);
        if (this.level() instanceof ServerLevel serverLevel) {
            // CraftBukkit start
            org.bukkit.event.entity.EntityShootBowEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityShootBowEvent(this, this.getMainHandItem(), arrow.getPickupItem(), arrow, net.minecraft.world.InteractionHand.MAIN_HAND, 0.8F, true); // Paper - improve entity shhot bow event - add arrow stack to event
            if (event.isCancelled()) {
                event.getProjectile().remove();
                return;
            }

            if (event.getProjectile() == arrow.getBukkitEntity()) {
            // CraftBukkit end
            Projectile.spawnProjectileUsingShoot(
                arrow, serverLevel, projectile, d, d1 + squareRoot * 0.2F, d2, 1.6F, serverLevel.purpurConfig.skeletonBowAccuracyMap.getOrDefault(serverLevel.getDifficulty().getId(), (float) (14 - serverLevel.getDifficulty().getId() * 4))  // Purpur - skeleton bow accuracy option
            );
            } // CraftBukkit
        }

        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    protected AbstractArrow getArrow(ItemStack arrow, float velocity, @Nullable ItemStack weapon) {
        return ProjectileUtil.getMobArrow(this, arrow, velocity, weapon);
    }

    @Override
    public boolean canFireProjectileWeapon(ProjectileWeaponItem projectileWeapon) {
        return projectileWeapon == Items.BOW;
    }

    @Override
    public TagKey<Item> getPreferredWeaponType() {
        return ItemTags.SKELETON_PREFERRED_WEAPONS;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.reassessWeaponGoal();
        // Paper start - shouldBurnInDay API
        if (compound.contains("Paper.ShouldBurnInDay")) {
            this.shouldBurnInDay = compound.getBoolean("Paper.ShouldBurnInDay");
        }
        // Paper end - shouldBurnInDay API
    }
    // Paper start - shouldBurnInDay API
    @Override
    public void addAdditionalSaveData(final net.minecraft.nbt.CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("Paper.ShouldBurnInDay", this.shouldBurnInDay);
    }
    // Paper end - shouldBurnInDay API

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack, boolean silent) { // Paper - Fix silent equipment change
        super.setItemSlot(slot, stack, silent); // Paper - Fix silent equipment change
        if (!this.level().isClientSide) {
            this.reassessWeaponGoal();
        }
    }

    public boolean isShaking() {
        return this.isFullyFrozen();
    }
}
