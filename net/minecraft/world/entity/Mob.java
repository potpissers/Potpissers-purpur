package net.minecraft.world.entity;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensing;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;

public abstract class Mob extends LivingEntity implements EquipmentUser, Leashable, Targeting {
    private static final EntityDataAccessor<Byte> DATA_MOB_FLAGS_ID = SynchedEntityData.defineId(Mob.class, EntityDataSerializers.BYTE);
    private static final int MOB_FLAG_NO_AI = 1;
    private static final int MOB_FLAG_LEFTHANDED = 2;
    private static final int MOB_FLAG_AGGRESSIVE = 4;
    protected static final int PICKUP_REACH = 1;
    private static final Vec3i ITEM_PICKUP_REACH = new Vec3i(1, 0, 1);
    private static final List<EquipmentSlot> EQUIPMENT_POPULATION_ORDER = List.of(
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    );
    public static final float MAX_WEARING_ARMOR_CHANCE = 0.15F;
    public static final float MAX_PICKUP_LOOT_CHANCE = 0.55F;
    public static final float MAX_ENCHANTED_ARMOR_CHANCE = 0.5F;
    public static final float MAX_ENCHANTED_WEAPON_CHANCE = 0.25F;
    public static final float DEFAULT_EQUIPMENT_DROP_CHANCE = 0.085F;
    public static final float PRESERVE_ITEM_DROP_CHANCE_THRESHOLD = 1.0F;
    public static final int PRESERVE_ITEM_DROP_CHANCE = 2;
    public static final int UPDATE_GOAL_SELECTOR_EVERY_N_TICKS = 2;
    private static final double DEFAULT_ATTACK_REACH = Math.sqrt(2.04F) - 0.6F;
    protected static final ResourceLocation RANDOM_SPAWN_BONUS_ID = ResourceLocation.withDefaultNamespace("random_spawn_bonus");
    public int ambientSoundTime;
    protected int xpReward;
    protected LookControl lookControl;
    protected MoveControl moveControl;
    protected JumpControl jumpControl;
    private final BodyRotationControl bodyRotationControl;
    protected PathNavigation navigation;
    protected final GoalSelector goalSelector;
    protected final GoalSelector targetSelector;
    @Nullable
    private LivingEntity target;
    private final Sensing sensing;
    private final NonNullList<ItemStack> handItems = NonNullList.withSize(2, ItemStack.EMPTY);
    protected final float[] handDropChances = new float[2];
    private final NonNullList<ItemStack> armorItems = NonNullList.withSize(4, ItemStack.EMPTY);
    protected final float[] armorDropChances = new float[4];
    private ItemStack bodyArmorItem = ItemStack.EMPTY;
    protected float bodyArmorDropChance;
    private boolean canPickUpLoot;
    private boolean persistenceRequired;
    private final Map<PathType, Float> pathfindingMalus = Maps.newEnumMap(PathType.class);
    private Optional<ResourceKey<LootTable>> lootTable = Optional.empty();
    private long lootTableSeed;
    @Nullable
    private Leashable.LeashData leashData;
    private BlockPos restrictCenter = BlockPos.ZERO;
    private float restrictRadius = -1.0F;

    protected Mob(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
        this.goalSelector = new GoalSelector();
        this.targetSelector = new GoalSelector();
        this.lookControl = new LookControl(this);
        this.moveControl = new MoveControl(this);
        this.jumpControl = new JumpControl(this);
        this.bodyRotationControl = this.createBodyControl();
        this.navigation = this.createNavigation(level);
        this.sensing = new Sensing(this);
        Arrays.fill(this.armorDropChances, 0.085F);
        Arrays.fill(this.handDropChances, 0.085F);
        this.bodyArmorDropChance = 0.085F;
        if (level instanceof ServerLevel) {
            this.registerGoals();
        }
    }

    protected void registerGoals() {
    }

    public static AttributeSupplier.Builder createMobAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.FOLLOW_RANGE, 16.0);
    }

    protected PathNavigation createNavigation(Level level) {
        return new GroundPathNavigation(this, level);
    }

    protected boolean shouldPassengersInheritMalus() {
        return false;
    }

    public float getPathfindingMalus(PathType pathType) {
        Mob mob1;
        if (this.getControlledVehicle() instanceof Mob mob && mob.shouldPassengersInheritMalus()) {
            mob1 = mob;
        } else {
            mob1 = this;
        }

        Float _float = mob1.pathfindingMalus.get(pathType);
        return _float == null ? pathType.getMalus() : _float;
    }

    public void setPathfindingMalus(PathType pathType, float malus) {
        this.pathfindingMalus.put(pathType, malus);
    }

    public void onPathfindingStart() {
    }

    public void onPathfindingDone() {
    }

    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this);
    }

    public LookControl getLookControl() {
        return this.lookControl;
    }

    public MoveControl getMoveControl() {
        return this.getControlledVehicle() instanceof Mob mob ? mob.getMoveControl() : this.moveControl;
    }

    public JumpControl getJumpControl() {
        return this.jumpControl;
    }

    public PathNavigation getNavigation() {
        return this.getControlledVehicle() instanceof Mob mob ? mob.getNavigation() : this.navigation;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity firstPassenger = this.getFirstPassenger();
        return !this.isNoAi() && firstPassenger instanceof Mob mob && firstPassenger.canControlVehicle() ? mob : null;
    }

    public Sensing getSensing() {
        return this.sensing;
    }

    @Nullable
    @Override
    public LivingEntity getTarget() {
        return this.target;
    }

    @Nullable
    protected final LivingEntity getTargetFromBrain() {
        return this.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    public void setTarget(@Nullable LivingEntity target) {
        this.target = target;
    }

    @Override
    public boolean canAttackType(EntityType<?> type) {
        return type != EntityType.GHAST;
    }

    public boolean canFireProjectileWeapon(ProjectileWeaponItem projectileWeapon) {
        return false;
    }

    public void ate() {
        this.gameEvent(GameEvent.EAT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MOB_FLAGS_ID, (byte)0);
    }

    public int getAmbientSoundInterval() {
        return 80;
    }

    public void playAmbientSound() {
        this.makeSound(this.getAmbientSound());
    }

    @Override
    public void baseTick() {
        super.baseTick();
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("mobBaseTick");
        if (this.isAlive() && this.random.nextInt(1000) < this.ambientSoundTime++) {
            this.resetAmbientSoundTime();
            this.playAmbientSound();
        }

        profilerFiller.pop();
    }

    @Override
    protected void playHurtSound(DamageSource source) {
        this.resetAmbientSoundTime();
        super.playHurtSound(source);
    }

    private void resetAmbientSoundTime() {
        this.ambientSoundTime = -this.getAmbientSoundInterval();
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        if (this.xpReward > 0) {
            int i = this.xpReward;

            for (int i1 = 0; i1 < this.armorItems.size(); i1++) {
                if (!this.armorItems.get(i1).isEmpty() && this.armorDropChances[i1] <= 1.0F) {
                    i += 1 + this.random.nextInt(3);
                }
            }

            for (int i1x = 0; i1x < this.handItems.size(); i1x++) {
                if (!this.handItems.get(i1x).isEmpty() && this.handDropChances[i1x] <= 1.0F) {
                    i += 1 + this.random.nextInt(3);
                }
            }

            if (!this.bodyArmorItem.isEmpty() && this.bodyArmorDropChance <= 1.0F) {
                i += 1 + this.random.nextInt(3);
            }

            return i;
        } else {
            return this.xpReward;
        }
    }

    public void spawnAnim() {
        if (this.level().isClientSide) {
            this.makePoofParticles();
        } else {
            this.level().broadcastEntityEvent(this, (byte)20);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 20) {
            this.spawnAnim();
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount % 5 == 0) {
            this.updateControlFlags();
        }
    }

    protected void updateControlFlags() {
        boolean flag = !(this.getControllingPassenger() instanceof Mob);
        boolean flag1 = !(this.getVehicle() instanceof AbstractBoat);
        this.goalSelector.setControlFlag(Goal.Flag.MOVE, flag);
        this.goalSelector.setControlFlag(Goal.Flag.JUMP, flag && flag1);
        this.goalSelector.setControlFlag(Goal.Flag.LOOK, flag);
    }

    @Override
    protected float tickHeadTurn(float yRot, float animStep) {
        this.bodyRotationControl.clientTick();
        return animStep;
    }

    @Nullable
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("CanPickUpLoot", this.canPickUpLoot());
        compound.putBoolean("PersistenceRequired", this.persistenceRequired);
        ListTag listTag = new ListTag();

        for (ItemStack itemStack : this.armorItems) {
            if (!itemStack.isEmpty()) {
                listTag.add(itemStack.save(this.registryAccess()));
            } else {
                listTag.add(new CompoundTag());
            }
        }

        compound.put("ArmorItems", listTag);
        ListTag listTag1 = new ListTag();

        for (float f : this.armorDropChances) {
            listTag1.add(FloatTag.valueOf(f));
        }

        compound.put("ArmorDropChances", listTag1);
        ListTag listTag2 = new ListTag();

        for (ItemStack itemStack1 : this.handItems) {
            if (!itemStack1.isEmpty()) {
                listTag2.add(itemStack1.save(this.registryAccess()));
            } else {
                listTag2.add(new CompoundTag());
            }
        }

        compound.put("HandItems", listTag2);
        ListTag listTag3 = new ListTag();

        for (float f1 : this.handDropChances) {
            listTag3.add(FloatTag.valueOf(f1));
        }

        compound.put("HandDropChances", listTag3);
        if (!this.bodyArmorItem.isEmpty()) {
            compound.put("body_armor_item", this.bodyArmorItem.save(this.registryAccess()));
            compound.putFloat("body_armor_drop_chance", this.bodyArmorDropChance);
        }

        this.writeLeashData(compound, this.leashData);
        compound.putBoolean("LeftHanded", this.isLeftHanded());
        if (this.lootTable.isPresent()) {
            compound.putString("DeathLootTable", this.lootTable.get().location().toString());
        }

        if (this.lootTableSeed != 0L) {
            compound.putLong("DeathLootTableSeed", this.lootTableSeed);
        }

        if (this.isNoAi()) {
            compound.putBoolean("NoAI", this.isNoAi());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setCanPickUpLoot(compound.getBoolean("CanPickUpLoot"));
        this.persistenceRequired = compound.getBoolean("PersistenceRequired");
        if (compound.contains("ArmorItems", 9)) {
            ListTag list = compound.getList("ArmorItems", 10);

            for (int i = 0; i < this.armorItems.size(); i++) {
                CompoundTag compound1 = list.getCompound(i);
                this.armorItems.set(i, ItemStack.parseOptional(this.registryAccess(), compound1));
            }
        } else {
            this.armorItems.replaceAll(itemStack -> ItemStack.EMPTY);
        }

        if (compound.contains("ArmorDropChances", 9)) {
            ListTag list = compound.getList("ArmorDropChances", 5);

            for (int i = 0; i < list.size(); i++) {
                this.armorDropChances[i] = list.getFloat(i);
            }
        } else {
            Arrays.fill(this.armorDropChances, 0.0F);
        }

        if (compound.contains("HandItems", 9)) {
            ListTag list = compound.getList("HandItems", 10);

            for (int i = 0; i < this.handItems.size(); i++) {
                CompoundTag compound1 = list.getCompound(i);
                this.handItems.set(i, ItemStack.parseOptional(this.registryAccess(), compound1));
            }
        } else {
            this.handItems.replaceAll(itemStack -> ItemStack.EMPTY);
        }

        if (compound.contains("HandDropChances", 9)) {
            ListTag list = compound.getList("HandDropChances", 5);

            for (int i = 0; i < list.size(); i++) {
                this.handDropChances[i] = list.getFloat(i);
            }
        } else {
            Arrays.fill(this.handDropChances, 0.0F);
        }

        if (compound.contains("body_armor_item", 10)) {
            this.bodyArmorItem = ItemStack.parse(this.registryAccess(), compound.getCompound("body_armor_item")).orElse(ItemStack.EMPTY);
            this.bodyArmorDropChance = compound.getFloat("body_armor_drop_chance");
        } else {
            this.bodyArmorItem = ItemStack.EMPTY;
        }

        this.readLeashData(compound);
        this.setLeftHanded(compound.getBoolean("LeftHanded"));
        if (compound.contains("DeathLootTable", 8)) {
            this.lootTable = Optional.of(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(compound.getString("DeathLootTable"))));
        } else {
            this.lootTable = Optional.empty();
        }

        this.lootTableSeed = compound.getLong("DeathLootTableSeed");
        this.setNoAi(compound.getBoolean("NoAI"));
    }

    @Override
    protected void dropFromLootTable(ServerLevel level, DamageSource damageSource, boolean playerKill) {
        super.dropFromLootTable(level, damageSource, playerKill);
        this.lootTable = Optional.empty();
    }

    @Override
    public final Optional<ResourceKey<LootTable>> getLootTable() {
        return this.lootTable.isPresent() ? this.lootTable : super.getLootTable();
    }

    @Override
    public long getLootTableSeed() {
        return this.lootTableSeed;
    }

    public void setZza(float amount) {
        this.zza = amount;
    }

    public void setYya(float amount) {
        this.yya = amount;
    }

    public void setXxa(float amount) {
        this.xxa = amount;
    }

    @Override
    public void setSpeed(float speed) {
        super.setSpeed(speed);
        this.setZza(speed);
    }

    public void stopInPlace() {
        this.getNavigation().stop();
        this.setXxa(0.0F);
        this.setYya(0.0F);
        this.setSpeed(0.0F);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("looting");
        if (this.level() instanceof ServerLevel serverLevel
            && this.canPickUpLoot()
            && this.isAlive()
            && !this.dead
            && serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            Vec3i pickupReach = this.getPickupReach();

            for (ItemEntity itemEntity : this.level()
                .getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(pickupReach.getX(), pickupReach.getY(), pickupReach.getZ()))) {
                if (!itemEntity.isRemoved()
                    && !itemEntity.getItem().isEmpty()
                    && !itemEntity.hasPickUpDelay()
                    && this.wantsToPickUp(serverLevel, itemEntity.getItem())) {
                    this.pickUpItem(serverLevel, itemEntity);
                }
            }
        }

        profilerFiller.pop();
    }

    protected Vec3i getPickupReach() {
        return ITEM_PICKUP_REACH;
    }

    protected void pickUpItem(ServerLevel level, ItemEntity entity) {
        ItemStack item = entity.getItem();
        ItemStack itemStack = this.equipItemIfPossible(level, item.copy());
        if (!itemStack.isEmpty()) {
            this.onItemPickup(entity);
            this.take(entity, itemStack.getCount());
            item.shrink(itemStack.getCount());
            if (item.isEmpty()) {
                entity.discard();
            }
        }
    }

    public ItemStack equipItemIfPossible(ServerLevel level, ItemStack stack) {
        EquipmentSlot equipmentSlotForItem = this.getEquipmentSlotForItem(stack);
        ItemStack itemBySlot = this.getItemBySlot(equipmentSlotForItem);
        boolean canReplaceCurrentItem = this.canReplaceCurrentItem(stack, itemBySlot, equipmentSlotForItem);
        if (equipmentSlotForItem.isArmor() && !canReplaceCurrentItem) {
            equipmentSlotForItem = EquipmentSlot.MAINHAND;
            itemBySlot = this.getItemBySlot(equipmentSlotForItem);
            canReplaceCurrentItem = itemBySlot.isEmpty();
        }

        if (canReplaceCurrentItem && this.canHoldItem(stack)) {
            double d = this.getEquipmentDropChance(equipmentSlotForItem);
            if (!itemBySlot.isEmpty() && Math.max(this.random.nextFloat() - 0.1F, 0.0F) < d) {
                this.spawnAtLocation(level, itemBySlot);
            }

            ItemStack itemStack = equipmentSlotForItem.limit(stack);
            this.setItemSlotAndDropWhenKilled(equipmentSlotForItem, itemStack);
            return itemStack;
        } else {
            return ItemStack.EMPTY;
        }
    }

    protected void setItemSlotAndDropWhenKilled(EquipmentSlot slot, ItemStack stack) {
        this.setItemSlot(slot, stack);
        this.setGuaranteedDrop(slot);
        this.persistenceRequired = true;
    }

    public void setGuaranteedDrop(EquipmentSlot slot) {
        switch (slot.getType()) {
            case HAND:
                this.handDropChances[slot.getIndex()] = 2.0F;
                break;
            case HUMANOID_ARMOR:
                this.armorDropChances[slot.getIndex()] = 2.0F;
                break;
            case ANIMAL_ARMOR:
                this.bodyArmorDropChance = 2.0F;
        }
    }

    protected boolean canReplaceCurrentItem(ItemStack newItem, ItemStack currentItem, EquipmentSlot slot) {
        if (currentItem.isEmpty()) {
            return true;
        } else {
            return slot.isArmor()
                ? this.compareArmor(newItem, currentItem, slot)
                : slot == EquipmentSlot.MAINHAND && this.compareWeapons(newItem, currentItem, slot);
        }
    }

    private boolean compareArmor(ItemStack newItem, ItemStack currentItem, EquipmentSlot slot) {
        if (EnchantmentHelper.has(currentItem, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
            return false;
        } else {
            double approximateAttributeWith = this.getApproximateAttributeWith(newItem, Attributes.ARMOR, slot);
            double approximateAttributeWith1 = this.getApproximateAttributeWith(currentItem, Attributes.ARMOR, slot);
            double approximateAttributeWith2 = this.getApproximateAttributeWith(newItem, Attributes.ARMOR_TOUGHNESS, slot);
            double approximateAttributeWith3 = this.getApproximateAttributeWith(currentItem, Attributes.ARMOR_TOUGHNESS, slot);
            if (approximateAttributeWith != approximateAttributeWith1) {
                return approximateAttributeWith > approximateAttributeWith1;
            } else {
                return approximateAttributeWith2 != approximateAttributeWith3
                    ? approximateAttributeWith2 > approximateAttributeWith3
                    : this.canReplaceEqualItem(newItem, currentItem);
            }
        }
    }

    private boolean compareWeapons(ItemStack newItem, ItemStack currentItem, EquipmentSlot slot) {
        TagKey<Item> preferredWeaponType = this.getPreferredWeaponType();
        if (preferredWeaponType != null) {
            if (currentItem.is(preferredWeaponType) && !newItem.is(preferredWeaponType)) {
                return false;
            }

            if (!currentItem.is(preferredWeaponType) && newItem.is(preferredWeaponType)) {
                return true;
            }
        }

        double approximateAttributeWith = this.getApproximateAttributeWith(newItem, Attributes.ATTACK_DAMAGE, slot);
        double approximateAttributeWith1 = this.getApproximateAttributeWith(currentItem, Attributes.ATTACK_DAMAGE, slot);
        return approximateAttributeWith != approximateAttributeWith1
            ? approximateAttributeWith > approximateAttributeWith1
            : this.canReplaceEqualItem(newItem, currentItem);
    }

    private double getApproximateAttributeWith(ItemStack item, Holder<Attribute> attribute, EquipmentSlot slot) {
        double d = this.getAttributes().hasAttribute(attribute) ? this.getAttributeBaseValue(attribute) : 0.0;
        ItemAttributeModifiers itemAttributeModifiers = item.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return itemAttributeModifiers.compute(d, slot);
    }

    public boolean canReplaceEqualItem(ItemStack candidate, ItemStack existing) {
        Set<Entry<Holder<Enchantment>>> set = existing.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet();
        Set<Entry<Holder<Enchantment>>> set1 = candidate.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet();
        if (set1.size() != set.size()) {
            return set1.size() > set.size();
        } else {
            int damageValue = candidate.getDamageValue();
            int damageValue1 = existing.getDamageValue();
            return damageValue != damageValue1
                ? damageValue < damageValue1
                : candidate.has(DataComponents.CUSTOM_NAME) && !existing.has(DataComponents.CUSTOM_NAME);
        }
    }

    public boolean canHoldItem(ItemStack stack) {
        return true;
    }

    public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
        return this.canHoldItem(stack);
    }

    @Nullable
    public TagKey<Item> getPreferredWeaponType() {
        return null;
    }

    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }

    public boolean requiresCustomPersistence() {
        return this.isPassenger();
    }

    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void checkDespawn() {
        if (this.level().getDifficulty() == Difficulty.PEACEFUL && this.shouldDespawnInPeaceful()) {
            this.discard();
        } else if (!this.isPersistenceRequired() && !this.requiresCustomPersistence()) {
            Entity nearestPlayer = this.level().getNearestPlayer(this, -1.0);
            if (nearestPlayer != null) {
                double d = nearestPlayer.distanceToSqr(this);
                int despawnDistance = this.getType().getCategory().getDespawnDistance();
                int i = despawnDistance * despawnDistance;
                if (d > i && this.removeWhenFarAway(d)) {
                    this.discard();
                }

                int noDespawnDistance = this.getType().getCategory().getNoDespawnDistance();
                int i1 = noDespawnDistance * noDespawnDistance;
                if (this.noActionTime > 600 && this.random.nextInt(800) == 0 && d > i1 && this.removeWhenFarAway(d)) {
                    this.discard();
                } else if (d < i1) {
                    this.noActionTime = 0;
                }
            }
        } else {
            this.noActionTime = 0;
        }
    }

    @Override
    protected final void serverAiStep() {
        this.noActionTime++;
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("sensing");
        this.sensing.tick();
        profilerFiller.pop();
        int i = this.tickCount + this.getId();
        if (i % 2 != 0 && this.tickCount > 1) {
            profilerFiller.push("targetSelector");
            this.targetSelector.tickRunningGoals(false);
            profilerFiller.pop();
            profilerFiller.push("goalSelector");
            this.goalSelector.tickRunningGoals(false);
            profilerFiller.pop();
        } else {
            profilerFiller.push("targetSelector");
            this.targetSelector.tick();
            profilerFiller.pop();
            profilerFiller.push("goalSelector");
            this.goalSelector.tick();
            profilerFiller.pop();
        }

        profilerFiller.push("navigation");
        this.navigation.tick();
        profilerFiller.pop();
        profilerFiller.push("mob tick");
        this.customServerAiStep((ServerLevel)this.level());
        profilerFiller.pop();
        profilerFiller.push("controls");
        profilerFiller.push("move");
        this.moveControl.tick();
        profilerFiller.popPush("look");
        this.lookControl.tick();
        profilerFiller.popPush("jump");
        this.jumpControl.tick();
        profilerFiller.pop();
        profilerFiller.pop();
        this.sendDebugPackets();
    }

    protected void sendDebugPackets() {
        DebugPackets.sendGoalSelector(this.level(), this, this.goalSelector);
    }

    protected void customServerAiStep(ServerLevel level) {
    }

    public int getMaxHeadXRot() {
        return 40;
    }

    public int getMaxHeadYRot() {
        return 75;
    }

    protected void clampHeadRotationToBody() {
        float f = this.getMaxHeadYRot();
        float yHeadRot = this.getYHeadRot();
        float f1 = Mth.wrapDegrees(this.yBodyRot - yHeadRot);
        float f2 = Mth.clamp(Mth.wrapDegrees(this.yBodyRot - yHeadRot), -f, f);
        float f3 = yHeadRot + f1 - f2;
        this.setYHeadRot(f3);
    }

    public int getHeadRotSpeed() {
        return 10;
    }

    public void lookAt(Entity entity, float maxYRotIncrease, float maxXRotIncrease) {
        double d = entity.getX() - this.getX();
        double d1 = entity.getZ() - this.getZ();
        double d2;
        if (entity instanceof LivingEntity livingEntity) {
            d2 = livingEntity.getEyeY() - this.getEyeY();
        } else {
            d2 = (entity.getBoundingBox().minY + entity.getBoundingBox().maxY) / 2.0 - this.getEyeY();
        }

        double squareRoot = Math.sqrt(d * d + d1 * d1);
        float f = (float)(Mth.atan2(d1, d) * 180.0F / (float)Math.PI) - 90.0F;
        float f1 = (float)(-(Mth.atan2(d2, squareRoot) * 180.0F / (float)Math.PI));
        this.setXRot(this.rotlerp(this.getXRot(), f1, maxXRotIncrease));
        this.setYRot(this.rotlerp(this.getYRot(), f, maxYRotIncrease));
    }

    private float rotlerp(float angle, float targetAngle, float maxIncrease) {
        float f = Mth.wrapDegrees(targetAngle - angle);
        if (f > maxIncrease) {
            f = maxIncrease;
        }

        if (f < -maxIncrease) {
            f = -maxIncrease;
        }

        return angle + f;
    }

    public static boolean checkMobSpawnRules(
        EntityType<? extends Mob> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        BlockPos blockPos = pos.below();
        return EntitySpawnReason.isSpawner(spawnReason) || level.getBlockState(blockPos).isValidSpawn(level, blockPos, entityType);
    }

    public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason spawnReason) {
        return true;
    }

    public boolean checkSpawnObstruction(LevelReader level) {
        return !level.containsAnyLiquid(this.getBoundingBox()) && level.isUnobstructed(this);
    }

    public int getMaxSpawnClusterSize() {
        return 4;
    }

    public boolean isMaxGroupSizeReached(int size) {
        return false;
    }

    @Override
    public int getMaxFallDistance() {
        if (this.getTarget() == null) {
            return this.getComfortableFallDistance(0.0F);
        } else {
            int i = (int)(this.getHealth() - this.getMaxHealth() * 0.33F);
            i -= (3 - this.level().getDifficulty().getId()) * 4;
            if (i < 0) {
                i = 0;
            }

            return this.getComfortableFallDistance(i);
        }
    }

    @Override
    public Iterable<ItemStack> getHandSlots() {
        return this.handItems;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return this.armorItems;
    }

    public ItemStack getBodyArmorItem() {
        return this.bodyArmorItem;
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return slot != EquipmentSlot.BODY;
    }

    public boolean isWearingBodyArmor() {
        return !this.getItemBySlot(EquipmentSlot.BODY).isEmpty();
    }

    public void setBodyArmorItem(ItemStack stack) {
        this.setItemSlotAndDropWhenKilled(EquipmentSlot.BODY, stack);
    }

    @Override
    public Iterable<ItemStack> getArmorAndBodyArmorSlots() {
        return (Iterable<ItemStack>)(this.bodyArmorItem.isEmpty() ? this.armorItems : Iterables.concat(this.armorItems, List.of(this.bodyArmorItem)));
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot.getType()) {
            case HAND -> (ItemStack)this.handItems.get(slot.getIndex());
            case HUMANOID_ARMOR -> (ItemStack)this.armorItems.get(slot.getIndex());
            case ANIMAL_ARMOR -> this.bodyArmorItem;
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        this.verifyEquippedItem(stack);
        switch (slot.getType()) {
            case HAND:
                this.onEquipItem(slot, this.handItems.set(slot.getIndex(), stack), stack);
                break;
            case HUMANOID_ARMOR:
                this.onEquipItem(slot, this.armorItems.set(slot.getIndex(), stack), stack);
                break;
            case ANIMAL_ARMOR:
                ItemStack itemStack = this.bodyArmorItem;
                this.bodyArmorItem = stack;
                this.onEquipItem(slot, itemStack, stack);
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);

        for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
            ItemStack itemBySlot = this.getItemBySlot(equipmentSlot);
            float equipmentDropChance = this.getEquipmentDropChance(equipmentSlot);
            if (equipmentDropChance != 0.0F) {
                boolean flag = equipmentDropChance > 1.0F;
                Entity var11 = damageSource.getEntity();
                if (var11 instanceof LivingEntity) {
                    LivingEntity livingEntity = (LivingEntity)var11;
                    if (this.level() instanceof ServerLevel serverLevel) {
                        equipmentDropChance = EnchantmentHelper.processEquipmentDropChance(serverLevel, livingEntity, damageSource, equipmentDropChance);
                    }
                }

                if (!itemBySlot.isEmpty()
                    && !EnchantmentHelper.has(itemBySlot, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)
                    && (recentlyHit || flag)
                    && this.random.nextFloat() < equipmentDropChance) {
                    if (!flag && itemBySlot.isDamageableItem()) {
                        itemBySlot.setDamageValue(
                            itemBySlot.getMaxDamage() - this.random.nextInt(1 + this.random.nextInt(Math.max(itemBySlot.getMaxDamage() - 3, 1)))
                        );
                    }

                    this.spawnAtLocation(level, itemBySlot);
                    this.setItemSlot(equipmentSlot, ItemStack.EMPTY);
                }
            }
        }
    }

    protected float getEquipmentDropChance(EquipmentSlot slot) {
        return switch (slot.getType()) {
            case HAND -> this.handDropChances[slot.getIndex()];
            case HUMANOID_ARMOR -> this.armorDropChances[slot.getIndex()];
            case ANIMAL_ARMOR -> this.bodyArmorDropChance;
        };
    }

    public void dropPreservedEquipment(ServerLevel level) {
        this.dropPreservedEquipment(level, itemStack -> true);
    }

    public Set<EquipmentSlot> dropPreservedEquipment(ServerLevel level, Predicate<ItemStack> filter) {
        Set<EquipmentSlot> set = new HashSet<>();

        for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
            ItemStack itemBySlot = this.getItemBySlot(equipmentSlot);
            if (!itemBySlot.isEmpty()) {
                if (!filter.test(itemBySlot)) {
                    set.add(equipmentSlot);
                } else {
                    double d = this.getEquipmentDropChance(equipmentSlot);
                    if (d > 1.0) {
                        this.setItemSlot(equipmentSlot, ItemStack.EMPTY);
                        this.spawnAtLocation(level, itemBySlot);
                    }
                }
            }
        }

        return set;
    }

    private LootParams createEquipmentParams(ServerLevel level) {
        return new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, this.position())
            .withParameter(LootContextParams.THIS_ENTITY, this)
            .create(LootContextParamSets.EQUIPMENT);
    }

    public void equip(EquipmentTable equipmentTable) {
        this.equip(equipmentTable.lootTable(), equipmentTable.slotDropChances());
    }

    public void equip(ResourceKey<LootTable> equipmentLootTable, Map<EquipmentSlot, Float> slotDropChances) {
        if (this.level() instanceof ServerLevel serverLevel) {
            this.equip(equipmentLootTable, this.createEquipmentParams(serverLevel), slotDropChances);
        }
    }

    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        if (random.nextFloat() < 0.15F * difficulty.getSpecialMultiplier()) {
            int randomInt = random.nextInt(2);
            float f = this.level().getDifficulty() == Difficulty.HARD ? 0.1F : 0.25F;
            if (random.nextFloat() < 0.095F) {
                randomInt++;
            }

            if (random.nextFloat() < 0.095F) {
                randomInt++;
            }

            if (random.nextFloat() < 0.095F) {
                randomInt++;
            }

            boolean flag = true;

            for (EquipmentSlot equipmentSlot : EQUIPMENT_POPULATION_ORDER) {
                ItemStack itemBySlot = this.getItemBySlot(equipmentSlot);
                if (!flag && random.nextFloat() < f) {
                    break;
                }

                flag = false;
                if (itemBySlot.isEmpty()) {
                    Item equipmentForSlot = getEquipmentForSlot(equipmentSlot, randomInt);
                    if (equipmentForSlot != null) {
                        this.setItemSlot(equipmentSlot, new ItemStack(equipmentForSlot));
                    }
                }
            }
        }
    }

    @Nullable
    public static Item getEquipmentForSlot(EquipmentSlot slot, int chance) {
        switch (slot) {
            case HEAD:
                if (chance == 0) {
                    return Items.LEATHER_HELMET;
                } else if (chance == 1) {
                    return Items.GOLDEN_HELMET;
                } else if (chance == 2) {
                    return Items.CHAINMAIL_HELMET;
                } else if (chance == 3) {
                    return Items.IRON_HELMET;
                } else if (chance == 4) {
                    return Items.DIAMOND_HELMET;
                }
            case CHEST:
                if (chance == 0) {
                    return Items.LEATHER_CHESTPLATE;
                } else if (chance == 1) {
                    return Items.GOLDEN_CHESTPLATE;
                } else if (chance == 2) {
                    return Items.CHAINMAIL_CHESTPLATE;
                } else if (chance == 3) {
                    return Items.IRON_CHESTPLATE;
                } else if (chance == 4) {
                    return Items.DIAMOND_CHESTPLATE;
                }
            case LEGS:
                if (chance == 0) {
                    return Items.LEATHER_LEGGINGS;
                } else if (chance == 1) {
                    return Items.GOLDEN_LEGGINGS;
                } else if (chance == 2) {
                    return Items.CHAINMAIL_LEGGINGS;
                } else if (chance == 3) {
                    return Items.IRON_LEGGINGS;
                } else if (chance == 4) {
                    return Items.DIAMOND_LEGGINGS;
                }
            case FEET:
                if (chance == 0) {
                    return Items.LEATHER_BOOTS;
                } else if (chance == 1) {
                    return Items.GOLDEN_BOOTS;
                } else if (chance == 2) {
                    return Items.CHAINMAIL_BOOTS;
                } else if (chance == 3) {
                    return Items.IRON_BOOTS;
                } else if (chance == 4) {
                    return Items.DIAMOND_BOOTS;
                }
            default:
                return null;
        }
    }

    protected void populateDefaultEquipmentEnchantments(ServerLevelAccessor level, RandomSource random, DifficultyInstance difficulty) {
        this.enchantSpawnedWeapon(level, random, difficulty);

        for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
            if (equipmentSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                this.enchantSpawnedArmor(level, random, equipmentSlot, difficulty);
            }
        }
    }

    protected void enchantSpawnedWeapon(ServerLevelAccessor level, RandomSource random, DifficultyInstance difficulty) {
        this.enchantSpawnedEquipment(level, EquipmentSlot.MAINHAND, random, 0.25F, difficulty);
    }

    protected void enchantSpawnedArmor(ServerLevelAccessor level, RandomSource random, EquipmentSlot slot, DifficultyInstance difficulty) {
        this.enchantSpawnedEquipment(level, slot, random, 0.5F, difficulty);
    }

    private void enchantSpawnedEquipment(ServerLevelAccessor level, EquipmentSlot slot, RandomSource random, float enchantChance, DifficultyInstance difficulty) {
        ItemStack itemBySlot = this.getItemBySlot(slot);
        if (!itemBySlot.isEmpty() && random.nextFloat() < enchantChance * difficulty.getSpecialMultiplier()) {
            EnchantmentHelper.enchantItemFromProvider(itemBySlot, level.registryAccess(), VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT, difficulty, random);
            this.setItemSlot(slot, itemBySlot);
        }
    }

    @Nullable
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        RandomSource random = level.getRandom();
        AttributeInstance attributeInstance = Objects.requireNonNull(this.getAttribute(Attributes.FOLLOW_RANGE));
        if (!attributeInstance.hasModifier(RANDOM_SPAWN_BONUS_ID)) {
            attributeInstance.addPermanentModifier(
                new AttributeModifier(RANDOM_SPAWN_BONUS_ID, random.triangle(0.0, 0.11485000000000001), AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
            );
        }

        this.setLeftHanded(random.nextFloat() < 0.05F);
        return spawnGroupData;
    }

    public void setPersistenceRequired() {
        this.persistenceRequired = true;
    }

    @Override
    public void setDropChance(EquipmentSlot slot, float chance) {
        switch (slot.getType()) {
            case HAND:
                this.handDropChances[slot.getIndex()] = chance;
                break;
            case HUMANOID_ARMOR:
                this.armorDropChances[slot.getIndex()] = chance;
                break;
            case ANIMAL_ARMOR:
                this.bodyArmorDropChance = chance;
        }
    }

    @Override
    public boolean canPickUpLoot() {
        return this.canPickUpLoot;
    }

    public void setCanPickUpLoot(boolean canPickUpLoot) {
        this.canPickUpLoot = canPickUpLoot;
    }

    @Override
    protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
        return this.canPickUpLoot();
    }

    public boolean isPersistenceRequired() {
        return this.persistenceRequired;
    }

    @Override
    public final InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.isAlive()) {
            return InteractionResult.PASS;
        } else {
            InteractionResult interactionResult = this.checkAndHandleImportantInteractions(player, hand);
            if (interactionResult.consumesAction()) {
                this.gameEvent(GameEvent.ENTITY_INTERACT, player);
                return interactionResult;
            } else {
                InteractionResult interactionResult1 = super.interact(player, hand);
                if (interactionResult1 != InteractionResult.PASS) {
                    return interactionResult1;
                } else {
                    interactionResult = this.mobInteract(player, hand);
                    if (interactionResult.consumesAction()) {
                        this.gameEvent(GameEvent.ENTITY_INTERACT, player);
                        return interactionResult;
                    } else {
                        return InteractionResult.PASS;
                    }
                }
            }
        }
    }

    private InteractionResult checkAndHandleImportantInteractions(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.is(Items.NAME_TAG)) {
            InteractionResult interactionResult = itemInHand.interactLivingEntity(player, this, hand);
            if (interactionResult.consumesAction()) {
                return interactionResult;
            }
        }

        if (itemInHand.getItem() instanceof SpawnEggItem) {
            if (this.level() instanceof ServerLevel) {
                SpawnEggItem spawnEggItem = (SpawnEggItem)itemInHand.getItem();
                Optional<Mob> optional = spawnEggItem.spawnOffspringFromSpawnEgg(
                    player, this, (EntityType<? extends Mob>)this.getType(), (ServerLevel)this.level(), this.position(), itemInHand
                );
                optional.ifPresent(mob -> this.onOffspringSpawnedFromEgg(player, mob));
                if (optional.isEmpty()) {
                    return InteractionResult.PASS;
                }
            }

            return InteractionResult.SUCCESS_SERVER;
        } else {
            return InteractionResult.PASS;
        }
    }

    protected void onOffspringSpawnedFromEgg(Player player, Mob child) {
    }

    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    public boolean isWithinRestriction() {
        return this.isWithinRestriction(this.blockPosition());
    }

    public boolean isWithinRestriction(BlockPos pos) {
        return this.restrictRadius == -1.0F || this.restrictCenter.distSqr(pos) < this.restrictRadius * this.restrictRadius;
    }

    public void restrictTo(BlockPos pos, int distance) {
        this.restrictCenter = pos;
        this.restrictRadius = distance;
    }

    public BlockPos getRestrictCenter() {
        return this.restrictCenter;
    }

    public float getRestrictRadius() {
        return this.restrictRadius;
    }

    public void clearRestriction() {
        this.restrictRadius = -1.0F;
    }

    public boolean hasRestriction() {
        return this.restrictRadius != -1.0F;
    }

    @Nullable
    public <T extends Mob> T convertTo(
        EntityType<T> entityType, ConversionParams conversionParams, EntitySpawnReason spawnReason, ConversionParams.AfterConversion<T> afterConversion
    ) {
        if (this.isRemoved()) {
            return null;
        } else {
            T mob = (T)entityType.create(this.level(), spawnReason);
            if (mob == null) {
                return null;
            } else {
                conversionParams.type().convert(this, mob, conversionParams);
                afterConversion.finalizeConversion(mob);
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.addFreshEntity(mob);
                }

                if (conversionParams.type().shouldDiscardAfterConversion()) {
                    this.discard();
                }

                return mob;
            }
        }
    }

    @Nullable
    public <T extends Mob> T convertTo(EntityType<T> entityType, ConversionParams coversionParams, ConversionParams.AfterConversion<T> afterConversion) {
        return this.convertTo(entityType, coversionParams, EntitySpawnReason.CONVERSION, afterConversion);
    }

    @Nullable
    @Override
    public Leashable.LeashData getLeashData() {
        return this.leashData;
    }

    @Override
    public void setLeashData(@Nullable Leashable.LeashData leashData) {
        this.leashData = leashData;
    }

    @Override
    public void onLeashRemoved() {
        if (this.getLeashData() == null) {
            this.clearRestriction();
        }
    }

    @Override
    public void leashTooFarBehaviour() {
        Leashable.super.leashTooFarBehaviour();
        this.goalSelector.disableControlFlag(Goal.Flag.MOVE);
    }

    @Override
    public boolean canBeLeashed() {
        return !(this instanceof Enemy);
    }

    @Override
    public boolean startRiding(Entity entity, boolean force) {
        boolean flag = super.startRiding(entity, force);
        if (flag && this.isLeashed()) {
            this.dropLeash();
        }

        return flag;
    }

    @Override
    public boolean isEffectiveAi() {
        return super.isEffectiveAi() && !this.isNoAi();
    }

    public void setNoAi(boolean noAi) {
        byte b = this.entityData.get(DATA_MOB_FLAGS_ID);
        this.entityData.set(DATA_MOB_FLAGS_ID, noAi ? (byte)(b | 1) : (byte)(b & -2));
    }

    public void setLeftHanded(boolean leftHanded) {
        byte b = this.entityData.get(DATA_MOB_FLAGS_ID);
        this.entityData.set(DATA_MOB_FLAGS_ID, leftHanded ? (byte)(b | 2) : (byte)(b & -3));
    }

    public void setAggressive(boolean aggressive) {
        byte b = this.entityData.get(DATA_MOB_FLAGS_ID);
        this.entityData.set(DATA_MOB_FLAGS_ID, aggressive ? (byte)(b | 4) : (byte)(b & -5));
    }

    public boolean isNoAi() {
        return (this.entityData.get(DATA_MOB_FLAGS_ID) & 1) != 0;
    }

    public boolean isLeftHanded() {
        return (this.entityData.get(DATA_MOB_FLAGS_ID) & 2) != 0;
    }

    public boolean isAggressive() {
        return (this.entityData.get(DATA_MOB_FLAGS_ID) & 4) != 0;
    }

    public void setBaby(boolean baby) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return this.isLeftHanded() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }

    public boolean isWithinMeleeAttackRange(LivingEntity entity) {
        return this.getAttackBoundingBox().intersects(entity.getHitbox());
    }

    protected AABB getAttackBoundingBox() {
        Entity vehicle = this.getVehicle();
        AABB aabb;
        if (vehicle != null) {
            AABB boundingBox = vehicle.getBoundingBox();
            AABB boundingBox1 = this.getBoundingBox();
            aabb = new AABB(
                Math.min(boundingBox1.minX, boundingBox.minX),
                boundingBox1.minY,
                Math.min(boundingBox1.minZ, boundingBox.minZ),
                Math.max(boundingBox1.maxX, boundingBox.maxX),
                boundingBox1.maxY,
                Math.max(boundingBox1.maxZ, boundingBox.maxZ)
            );
        } else {
            aabb = this.getBoundingBox();
        }

        return aabb.inflate(DEFAULT_ATTACK_REACH, 0.0, DEFAULT_ATTACK_REACH);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity source) {
        float f = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        ItemStack weaponItem = this.getWeaponItem();
        DamageSource damageSource = Optional.ofNullable(weaponItem.getItem().getDamageSource(this)).orElse(this.damageSources().mobAttack(this));
        f = EnchantmentHelper.modifyDamage(level, weaponItem, source, damageSource, f);
        f += weaponItem.getItem().getAttackDamageBonus(source, f, damageSource);
        boolean flag = source.hurtServer(level, damageSource, f);
        if (flag) {
            float knockback = this.getKnockback(source, damageSource);
            if (knockback > 0.0F && source instanceof LivingEntity livingEntity) {
                livingEntity.knockback(
                    knockback * 0.5F, Mth.sin(this.getYRot() * (float) (Math.PI / 180.0)), -Mth.cos(this.getYRot() * (float) (Math.PI / 180.0))
                );
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, 1.0, 0.6));
            }

            if (source instanceof LivingEntity livingEntity) {
                weaponItem.hurtEnemy(livingEntity, this);
            }

            EnchantmentHelper.doPostAttackEffects(level, source, damageSource);
            this.setLastHurtMob(source);
            this.playAttackSound();
        }

        return flag;
    }

    protected void playAttackSound() {
    }

    protected boolean isSunBurnTick() {
        if (this.level().isDay() && !this.level().isClientSide) {
            float lightLevelDependentMagicValue = this.getLightLevelDependentMagicValue();
            BlockPos blockPos = BlockPos.containing(this.getX(), this.getEyeY(), this.getZ());
            boolean flag = this.isInWaterRainOrBubble() || this.isInPowderSnow || this.wasInPowderSnow;
            if (lightLevelDependentMagicValue > 0.5F
                && this.random.nextFloat() * 30.0F < (lightLevelDependentMagicValue - 0.4F) * 2.0F
                && !flag
                && this.level().canSeeSky(blockPos)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void jumpInLiquid(TagKey<Fluid> fluidTag) {
        if (this.getNavigation().canFloat()) {
            super.jumpInLiquid(fluidTag);
        } else {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.3, 0.0));
        }
    }

    @VisibleForTesting
    public void removeFreeWill() {
        this.removeAllGoals(goal -> true);
        this.getBrain().removeAllBehaviors();
    }

    public void removeAllGoals(Predicate<Goal> filter) {
        this.goalSelector.removeAllGoals(filter);
    }

    @Override
    protected void removeAfterChangingDimensions() {
        super.removeAfterChangingDimensions();
        this.getAllSlots().forEach(itemStack -> {
            if (!itemStack.isEmpty()) {
                itemStack.setCount(0);
            }
        });
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        SpawnEggItem spawnEggItem = SpawnEggItem.byId(this.getType());
        return spawnEggItem == null ? null : new ItemStack(spawnEggItem);
    }

    @Override
    protected void onAttributeUpdated(Holder<Attribute> attribute) {
        super.onAttributeUpdated(attribute);
        if (attribute.is(Attributes.FOLLOW_RANGE) || attribute.is(Attributes.TEMPT_RANGE)) {
            this.getNavigation().updatePathfinderMaxVisitedNodes();
        }
    }

    @VisibleForTesting
    public float[] getHandDropChances() {
        return this.handDropChances;
    }

    @VisibleForTesting
    public float[] getArmorDropChances() {
        return this.armorDropChances;
    }
}
