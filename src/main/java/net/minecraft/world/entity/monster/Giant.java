package net.minecraft.world.entity.monster;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

public class Giant extends Monster {
    public Giant(EntityType<? extends Giant> type, Level world) {
        super(type, world);
    }

    // Purpur start
    @Override
    public boolean isRidable() {
        return level().purpurConfig.giantRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.giantRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.giantControllable;
    }

    @Override
    protected void registerGoals() {
        if (level().purpurConfig.giantHaveAI) {
            this.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.FloatGoal(this));
            this.goalSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this));
            this.goalSelector.addGoal(7, new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal(this, 1.0D));
            this.goalSelector.addGoal(8, new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(this, net.minecraft.world.entity.player.Player.class, 16.0F));
            this.goalSelector.addGoal(8, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(this));
            this.goalSelector.addGoal(5, new net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal(this, 1.0D));
            if (level().purpurConfig.giantHaveHostileAI) {
                this.goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.0D, false));
                this.targetSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this));
                this.targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(this).setAlertOthers(ZombifiedPiglin.class));
                this.targetSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.player.Player.class, true));
                this.targetSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.npc.Villager.class, false));
                this.targetSelector.addGoal(4, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.animal.IronGolem.class, true));
                this.targetSelector.addGoal(5, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.animal.Turtle.class, true));
            }
        }
    }

    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.giantTakeDamageFromWater;
    }

    @Override
    protected void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.giantMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.giantScale);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(this.level().purpurConfig.giantMovementSpeed);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(this.level().purpurConfig.giantAttackDamage);
    }

    // Purpur end

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 100.0).add(Attributes.MOVEMENT_SPEED, 0.5).add(Attributes.ATTACK_DAMAGE, 50.0);
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor world, net.minecraft.world.DifficultyInstance difficulty, net.minecraft.world.entity.MobSpawnType spawnReason, @javax.annotation.Nullable net.minecraft.world.entity.SpawnGroupData entityData) {
        net.minecraft.world.entity.SpawnGroupData groupData = super.finalizeSpawn(world, difficulty, spawnReason, entityData);
        if (groupData == null) {
            populateDefaultEquipmentSlots(this.random, difficulty);
            populateDefaultEquipmentEnchantments(world, this.random, difficulty);
        }
        return groupData;
    }

    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, net.minecraft.world.DifficultyInstance difficulty) {
        super.populateDefaultEquipmentSlots(this.random, difficulty);
        // TODO make configurable
        if (random.nextFloat() < (level().getDifficulty() == net.minecraft.world.Difficulty.HARD ? 0.1F : 0.05F)) {
            this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
        }
    }

    @Override
    public float getJumpPower() {
        // make giants jump as high as everything else relative to their size
        // 1.0 makes bottom of feet about as high as their waist when they jump
        return level().purpurConfig.giantJumpHeight;
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader world) {
        return super.getWalkTargetValue(pos, world); // Purpur - fix light requirements for natural spawns
    }
}
