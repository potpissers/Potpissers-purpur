package net.minecraft.world.entity.decoration;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ArmorStand extends LivingEntity {
    public static final int WOBBLE_TIME = 5;
    private static final boolean ENABLE_ARMS = true;
    public static final Rotations DEFAULT_HEAD_POSE = new Rotations(0.0F, 0.0F, 0.0F);
    public static final Rotations DEFAULT_BODY_POSE = new Rotations(0.0F, 0.0F, 0.0F);
    public static final Rotations DEFAULT_LEFT_ARM_POSE = new Rotations(-10.0F, 0.0F, -10.0F);
    public static final Rotations DEFAULT_RIGHT_ARM_POSE = new Rotations(-15.0F, 0.0F, 10.0F);
    public static final Rotations DEFAULT_LEFT_LEG_POSE = new Rotations(-1.0F, 0.0F, -1.0F);
    public static final Rotations DEFAULT_RIGHT_LEG_POSE = new Rotations(1.0F, 0.0F, 1.0F);
    private static final EntityDimensions MARKER_DIMENSIONS = EntityDimensions.fixed(0.0F, 0.0F);
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.ARMOR_STAND.getDimensions().scale(0.5F).withEyeHeight(0.9875F);
    private static final double FEET_OFFSET = 0.1;
    private static final double CHEST_OFFSET = 0.9;
    private static final double LEGS_OFFSET = 0.4;
    private static final double HEAD_OFFSET = 1.6;
    public static final int DISABLE_TAKING_OFFSET = 8;
    public static final int DISABLE_PUTTING_OFFSET = 16;
    public static final int CLIENT_FLAG_SMALL = 1;
    public static final int CLIENT_FLAG_SHOW_ARMS = 4;
    public static final int CLIENT_FLAG_NO_BASEPLATE = 8;
    public static final int CLIENT_FLAG_MARKER = 16;
    public static final EntityDataAccessor<Byte> DATA_CLIENT_FLAGS = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.BYTE);
    public static final EntityDataAccessor<Rotations> DATA_HEAD_POSE = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_BODY_POSE = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_LEFT_ARM_POSE = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_RIGHT_ARM_POSE = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_LEFT_LEG_POSE = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_RIGHT_LEG_POSE = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
    private static final Predicate<Entity> RIDABLE_MINECARTS = entity -> entity instanceof AbstractMinecart abstractMinecart && abstractMinecart.isRideable();
    private final NonNullList<ItemStack> handItems = NonNullList.withSize(2, ItemStack.EMPTY);
    private final NonNullList<ItemStack> armorItems = NonNullList.withSize(4, ItemStack.EMPTY);
    private boolean invisible;
    public long lastHit;
    public int disabledSlots;
    public Rotations headPose = DEFAULT_HEAD_POSE;
    public Rotations bodyPose = DEFAULT_BODY_POSE;
    public Rotations leftArmPose = DEFAULT_LEFT_ARM_POSE;
    public Rotations rightArmPose = DEFAULT_RIGHT_ARM_POSE;
    public Rotations leftLegPose = DEFAULT_LEFT_LEG_POSE;
    public Rotations rightLegPose = DEFAULT_RIGHT_LEG_POSE;
    public boolean canMove = true; // Paper
    // Paper start - Allow ArmorStands not to tick
    public boolean canTick = true;
    public boolean canTickSetByAPI = false;
    private boolean noTickPoseDirty = false;
    private boolean noTickEquipmentDirty = false;
    // Paper end - Allow ArmorStands not to tick
    public boolean canMovementTick = true; // Purpur - Movement options for armor stands

    public ArmorStand(EntityType<? extends ArmorStand> entityType, Level level) {
        super(entityType, level);
        if (level != null) this.canTick = level.paperConfig().entities.armorStands.tick; // Paper - Allow ArmorStands not to tick
        if (level != null) this.canMovementTick = level.purpurConfig.armorstandMovement; // Purpur - Movement options for armor stands
        this.setShowArms(level != null && level.purpurConfig.armorstandPlaceWithArms); // Purpur - Config to show Armor Stand arms on spawn
    }

    public ArmorStand(Level level, double x, double y, double z) {
        this(EntityType.ARMOR_STAND, level);
        this.setPos(x, y, z);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createLivingAttributes().add(Attributes.STEP_HEIGHT, 0.0);
    }

    // CraftBukkit start - SPIGOT-3607, SPIGOT-3637
    @Override
    public float getBukkitYaw() {
        return this.getYRot();
    }
    // CraftBukkit end

    @Override
    public void refreshDimensions() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        super.refreshDimensions();
        this.setPos(x, y, z);
    }

    private boolean hasPhysics() {
        return !this.isMarker() && !this.isNoGravity();
    }

    @Override
    public boolean isEffectiveAi() {
        return super.isEffectiveAi() && this.hasPhysics();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CLIENT_FLAGS, (byte)0);
        builder.define(DATA_HEAD_POSE, DEFAULT_HEAD_POSE);
        builder.define(DATA_BODY_POSE, DEFAULT_BODY_POSE);
        builder.define(DATA_LEFT_ARM_POSE, DEFAULT_LEFT_ARM_POSE);
        builder.define(DATA_RIGHT_ARM_POSE, DEFAULT_RIGHT_ARM_POSE);
        builder.define(DATA_LEFT_LEG_POSE, DEFAULT_LEFT_LEG_POSE);
        builder.define(DATA_RIGHT_LEG_POSE, DEFAULT_RIGHT_LEG_POSE);
    }

    @Override
    public Iterable<ItemStack> getHandSlots() {
        return this.handItems;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return this.armorItems;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        switch (slot.getType()) {
            case HAND:
                return this.handItems.get(slot.getIndex());
            case HUMANOID_ARMOR:
                return this.armorItems.get(slot.getIndex());
            default:
                return ItemStack.EMPTY;
        }
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return slot != EquipmentSlot.BODY && !this.isDisabled(slot);
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        // CraftBukkit start
        this.setItemSlot(slot, stack, false);
    }

    @Override
    public void setItemSlot(net.minecraft.world.entity.EquipmentSlot slot, ItemStack stack, boolean silent) {
        // CraftBukkit end
        this.verifyEquippedItem(stack);
        switch (slot.getType()) {
            case HAND:
                this.onEquipItem(slot, this.handItems.set(slot.getIndex(), stack), stack, silent); // CraftBukkit
                break;
            case HUMANOID_ARMOR:
                this.onEquipItem(slot, this.armorItems.set(slot.getIndex(), stack), stack, silent); // CraftBukkit
        }
        this.noTickEquipmentDirty = true; // Paper - Allow ArmorStands not to tick; Still update equipment
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        ListTag listTag = new ListTag();

        for (ItemStack itemStack : this.armorItems) {
            listTag.add(itemStack.saveOptional(this.registryAccess()));
        }

        compound.put("ArmorItems", listTag);
        ListTag listTag1 = new ListTag();

        for (ItemStack itemStack1 : this.handItems) {
            listTag1.add(itemStack1.saveOptional(this.registryAccess()));
        }

        compound.put("HandItems", listTag1);
        compound.putBoolean("Invisible", this.isInvisible());
        compound.putBoolean("Small", this.isSmall());
        compound.putBoolean("ShowArms", this.showArms());
        compound.putInt("DisabledSlots", this.disabledSlots);
        compound.putBoolean("NoBasePlate", !this.showBasePlate());
        if (this.isMarker()) {
            compound.putBoolean("Marker", this.isMarker());
        }

        compound.put("Pose", this.writePose());
        if (this.canTickSetByAPI) compound.putBoolean("Paper.CanTickOverride", this.canTick); // Paper - Allow ArmorStands not to tick
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("ArmorItems", 9)) {
            ListTag list = compound.getList("ArmorItems", 10);

            for (int i = 0; i < this.armorItems.size(); i++) {
                CompoundTag compound1 = list.getCompound(i);
                this.armorItems.set(i, ItemStack.parseOptional(this.registryAccess(), compound1));
            }
        }

        if (compound.contains("HandItems", 9)) {
            ListTag list = compound.getList("HandItems", 10);

            for (int i = 0; i < this.handItems.size(); i++) {
                CompoundTag compound1 = list.getCompound(i);
                this.handItems.set(i, ItemStack.parseOptional(this.registryAccess(), compound1));
            }
        }

        this.setInvisible(compound.getBoolean("Invisible"));
        this.setSmall(compound.getBoolean("Small"));
        this.setShowArms(compound.getBoolean("ShowArms"));
        this.disabledSlots = compound.getInt("DisabledSlots");
        this.setNoBasePlate(compound.getBoolean("NoBasePlate"));
        this.setMarker(compound.getBoolean("Marker"));
        this.noPhysics = !this.hasPhysics();
        // Paper start - Allow ArmorStands not to tick
        if (compound.contains("Paper.CanTickOverride")) {
            this.canTick = compound.getBoolean("Paper.CanTickOverride");
            this.canTickSetByAPI = true;
        }
        // Paper end - Allow ArmorStands not to tick
        CompoundTag compound2 = compound.getCompound("Pose");
        this.readPose(compound2);
    }

    private void readPose(CompoundTag compound) {
        ListTag list = compound.getList("Head", 5);
        this.setHeadPose(list.isEmpty() ? DEFAULT_HEAD_POSE : new Rotations(list));
        ListTag list1 = compound.getList("Body", 5);
        this.setBodyPose(list1.isEmpty() ? DEFAULT_BODY_POSE : new Rotations(list1));
        ListTag list2 = compound.getList("LeftArm", 5);
        this.setLeftArmPose(list2.isEmpty() ? DEFAULT_LEFT_ARM_POSE : new Rotations(list2));
        ListTag list3 = compound.getList("RightArm", 5);
        this.setRightArmPose(list3.isEmpty() ? DEFAULT_RIGHT_ARM_POSE : new Rotations(list3));
        ListTag list4 = compound.getList("LeftLeg", 5);
        this.setLeftLegPose(list4.isEmpty() ? DEFAULT_LEFT_LEG_POSE : new Rotations(list4));
        ListTag list5 = compound.getList("RightLeg", 5);
        this.setRightLegPose(list5.isEmpty() ? DEFAULT_RIGHT_LEG_POSE : new Rotations(list5));
    }

    private CompoundTag writePose() {
        CompoundTag compoundTag = new CompoundTag();
        if (!DEFAULT_HEAD_POSE.equals(this.headPose)) {
            compoundTag.put("Head", this.headPose.save());
        }

        if (!DEFAULT_BODY_POSE.equals(this.bodyPose)) {
            compoundTag.put("Body", this.bodyPose.save());
        }

        if (!DEFAULT_LEFT_ARM_POSE.equals(this.leftArmPose)) {
            compoundTag.put("LeftArm", this.leftArmPose.save());
        }

        if (!DEFAULT_RIGHT_ARM_POSE.equals(this.rightArmPose)) {
            compoundTag.put("RightArm", this.rightArmPose.save());
        }

        if (!DEFAULT_LEFT_LEG_POSE.equals(this.leftLegPose)) {
            compoundTag.put("LeftLeg", this.leftLegPose.save());
        }

        if (!DEFAULT_RIGHT_LEG_POSE.equals(this.rightLegPose)) {
            compoundTag.put("RightLeg", this.rightLegPose.save());
        }

        return compoundTag;
    }

    @Override
    public boolean isCollidable(boolean ignoreClimbing) { // Paper - Climbing should not bypass cramming gamerule
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    protected void pushEntities() {
        if (!this.level().paperConfig().entities.armorStands.doCollisionEntityLookups) return; // Paper - Option to prevent armor stands from doing entity lookups
        for (Entity entity : this.level().getEntitiesOfClass(AbstractMinecart.class, this.getBoundingBox(), RIDABLE_MINECARTS)) { // Paper - optimise collisions
            if (this.distanceToSqr(entity) <= 0.2) {
                entity.push(this);
            }
        }
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 vec, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (this.isMarker() || itemInHand.is(Items.NAME_TAG)) {
            return InteractionResult.PASS;
        } else if (player.isSpectator()) {
            return InteractionResult.SUCCESS;
        } else if (player.level().isClientSide) {
            return InteractionResult.SUCCESS_SERVER;
        } else {
            EquipmentSlot equipmentSlotForItem = this.getEquipmentSlotForItem(itemInHand);
            if (itemInHand.isEmpty()) {
                EquipmentSlot clickedSlot = this.getClickedSlot(vec);
                EquipmentSlot equipmentSlot = this.isDisabled(clickedSlot) ? equipmentSlotForItem : clickedSlot;
                if (this.hasItemInSlot(equipmentSlot) && this.swapItem(player, equipmentSlot, itemInHand, hand)) {
                    return InteractionResult.SUCCESS_SERVER;
                }
            } else {
                if (this.isDisabled(equipmentSlotForItem)) {
                    return InteractionResult.FAIL;
                }

                if (equipmentSlotForItem.getType() == EquipmentSlot.Type.HAND && !this.showArms()) {
                    return InteractionResult.FAIL;
                }

                if (this.swapItem(player, equipmentSlotForItem, itemInHand, hand)) {
                    return InteractionResult.SUCCESS_SERVER;
                }
            }

            return InteractionResult.PASS;
        }
    }

    private EquipmentSlot getClickedSlot(Vec3 vector) {
        EquipmentSlot equipmentSlot = EquipmentSlot.MAINHAND;
        boolean isSmall = this.isSmall();
        double d = vector.y / (this.getScale() * this.getAgeScale());
        EquipmentSlot equipmentSlot1 = EquipmentSlot.FEET;
        if (d >= 0.1 && d < 0.1 + (isSmall ? 0.8 : 0.45) && this.hasItemInSlot(equipmentSlot1)) {
            equipmentSlot = EquipmentSlot.FEET;
        } else if (d >= 0.9 + (isSmall ? 0.3 : 0.0) && d < 0.9 + (isSmall ? 1.0 : 0.7) && this.hasItemInSlot(EquipmentSlot.CHEST)) {
            equipmentSlot = EquipmentSlot.CHEST;
        } else if (d >= 0.4 && d < 0.4 + (isSmall ? 1.0 : 0.8) && this.hasItemInSlot(EquipmentSlot.LEGS)) {
            equipmentSlot = EquipmentSlot.LEGS;
        } else if (d >= 1.6 && this.hasItemInSlot(EquipmentSlot.HEAD)) {
            equipmentSlot = EquipmentSlot.HEAD;
        } else if (!this.hasItemInSlot(EquipmentSlot.MAINHAND) && this.hasItemInSlot(EquipmentSlot.OFFHAND)) {
            equipmentSlot = EquipmentSlot.OFFHAND;
        }

        return equipmentSlot;
    }

    public boolean isDisabled(EquipmentSlot slot) {
        return (this.disabledSlots & 1 << slot.getFilterBit(0)) != 0 || slot.getType() == EquipmentSlot.Type.HAND && !this.showArms();
    }

    private boolean swapItem(Player player, EquipmentSlot slot, ItemStack stack, InteractionHand hand) {
        ItemStack itemBySlot = this.getItemBySlot(slot);
        if (!itemBySlot.isEmpty() && (this.disabledSlots & 1 << slot.getFilterBit(8)) != 0) {
            return false;
        } else if (itemBySlot.isEmpty() && (this.disabledSlots & 1 << slot.getFilterBit(16)) != 0) {
            return false;
            // CraftBukkit start
        } else {
            org.bukkit.inventory.ItemStack armorStandItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemBySlot);
            org.bukkit.inventory.ItemStack playerHeldItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(stack);

            org.bukkit.entity.Player player1 = (org.bukkit.entity.Player) player.getBukkitEntity();
            org.bukkit.entity.ArmorStand self = (org.bukkit.entity.ArmorStand) this.getBukkitEntity();

            org.bukkit.inventory.EquipmentSlot slot1 = org.bukkit.craftbukkit.CraftEquipmentSlot.getSlot(slot);
            org.bukkit.inventory.EquipmentSlot hand1 = org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(hand);
            org.bukkit.event.player.PlayerArmorStandManipulateEvent armorStandManipulateEvent = new org.bukkit.event.player.PlayerArmorStandManipulateEvent(player1, self, playerHeldItem, armorStandItem, slot1, hand1);
            this.level().getCraftServer().getPluginManager().callEvent(armorStandManipulateEvent);

            if (armorStandManipulateEvent.isCancelled()) {
                return true;
            }

        if (player.hasInfiniteMaterials() && itemBySlot.isEmpty() && !stack.isEmpty()) {
            // CraftBukkit end
            this.setItemSlot(slot, stack.copyWithCount(1));
            return true;
        } else if (stack.isEmpty() || stack.getCount() <= 1) {
            this.setItemSlot(slot, stack);
            player.setItemInHand(hand, itemBySlot);
            return true;
        } else if (!itemBySlot.isEmpty()) {
            return false;
        } else {
            this.setItemSlot(slot, stack.split(1));
            return true;
        }
        } // CraftBukkit
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isRemoved()) {
            return false;
        } else if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && damageSource.getEntity() instanceof Mob) {
            return false;
        } else if (damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, damageSource, amount)) {
                return false;
            }
            this.kill(level, damageSource); // CraftBukkit
            // CraftBukkit end
            return false;
        } else if (this.isInvulnerableTo(level, damageSource) /*|| this.invisible*/ || this.isMarker()) { // CraftBukkit
            return false;
        } else if (damageSource.is(DamageTypeTags.IS_EXPLOSION)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, damageSource, amount, true, this.invisible)) {
                return false;
            }
            // CraftBukkit end
            // Paper start - avoid duplicate event call
            org.bukkit.event.entity.EntityDeathEvent event = this.brokenByAnything(level, damageSource);
            if (!event.isCancelled()) this.kill(level, damageSource, false); // CraftBukkit
            // Paper end
            return false;
        } else if (damageSource.is(DamageTypeTags.IGNITES_ARMOR_STANDS)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, damageSource, amount, true, this.invisible)) {
                return false;
            }
            // CraftBukkit end
            if (this.isOnFire()) {
                this.causeDamage(level, damageSource, 0.15F);
            } else {
                this.igniteForSeconds(5.0F);
            }

            return false;
        } else if (damageSource.is(DamageTypeTags.BURNS_ARMOR_STANDS) && this.getHealth() > 0.5F) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, damageSource, amount, true, this.invisible)) {
                return false;
            }
            // CraftBukkit end
            this.causeDamage(level, damageSource, 4.0F);
            return false;
        } else {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, damageSource, amount, true, this.invisible)) {
                return false;
            }
            // CraftBukkit end
            boolean isCanBreakArmorStand = damageSource.is(DamageTypeTags.CAN_BREAK_ARMOR_STAND);
            boolean isAlwaysKillsArmorStands = damageSource.is(DamageTypeTags.ALWAYS_KILLS_ARMOR_STANDS);
            if (!isCanBreakArmorStand && !isAlwaysKillsArmorStands) {
                return false;
            } else if (damageSource.getEntity() instanceof Player player && !player.getAbilities().mayBuild) {
                return false;
            } else if (damageSource.isCreativePlayer()) {
                this.playBrokenSound();
                this.showBreakingParticles();
                this.kill(level, damageSource); // CraftBukkit
                return true;
            } else {
                long gameTime = level.getGameTime();
                if (gameTime - this.lastHit > 5L && !isAlwaysKillsArmorStands) {
                    level.broadcastEntityEvent(this, (byte)32);
                    this.gameEvent(GameEvent.ENTITY_DAMAGE, damageSource.getEntity());
                    this.lastHit = gameTime;
                } else {
                    org.bukkit.event.entity.EntityDeathEvent event = this.brokenByPlayer(level, damageSource); // Paper
                    this.showBreakingParticles();
                    if (!event.isCancelled()) this.kill(level, damageSource, false); // Paper - we still need to kill to follow vanilla logic (emit the game event etc...)
                }

                return true;
            }
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 32) {
            if (this.level().isClientSide) {
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.ARMOR_STAND_HIT, this.getSoundSource(), 0.3F, 1.0F, false);
                this.lastHit = this.level().getGameTime();
            }
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d = this.getBoundingBox().getSize() * 4.0;
        if (Double.isNaN(d) || d == 0.0) {
            d = 4.0;
        }

        d *= 64.0;
        return distance < d * d;
    }

    private void showBreakingParticles() {
        if (this.level() instanceof ServerLevel) {
            ((ServerLevel)this.level())
                .sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()),
                    this.getX(),
                    this.getY(0.6666666666666666),
                    this.getZ(),
                    10,
                    this.getBbWidth() / 4.0F,
                    this.getBbHeight() / 4.0F,
                    this.getBbWidth() / 4.0F,
                    0.05
                );
        }
    }

    private void causeDamage(ServerLevel level, DamageSource damageSource, float damageAmount) {
        float health = this.getHealth();
        health -= damageAmount;
        if (health <= 0.5F) {
            this.brokenByAnything(level, damageSource);
            // Paper start - avoid duplicate event call
            org.bukkit.event.entity.EntityDeathEvent event = this.brokenByAnything(level, damageSource);
            if (!event.isCancelled()) this.kill(level, damageSource, false); // CraftBukkit
            // Paper end
        } else {
            this.setHealth(health);
            this.gameEvent(GameEvent.ENTITY_DAMAGE, damageSource.getEntity());
        }
    }

    private org.bukkit.event.entity.EntityDeathEvent brokenByPlayer(ServerLevel level, DamageSource damageSource) { // Paper
        ItemStack itemStack = new ItemStack(Items.ARMOR_STAND);
        itemStack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
        this.drops.add(new DefaultDrop(itemStack, stack -> Block.popResource(this.level(), this.blockPosition(), stack))); // CraftBukkit - add to drops // Paper - Restore vanilla drops behavior
        return this.brokenByAnything(level, damageSource); // Paper
    }

    private org.bukkit.event.entity.EntityDeathEvent brokenByAnything(ServerLevel level, DamageSource damageSource) { // Paper
        this.playBrokenSound();
        // this.dropAllDeathLoot(level, damageSource); // CraftBukkit - moved down

        for (int i = 0; i < this.handItems.size(); i++) {
            ItemStack itemStack = this.handItems.get(i);
            if (!itemStack.isEmpty()) {
                this.drops.add(new DefaultDrop(itemStack, stack -> Block.popResource(this.level(), this.blockPosition().above(), stack))); // CraftBukkit - add to drops // Paper - Restore vanilla drops behavior; mirror so we can destroy it later - though this call site was safe & spawn drops correctly
                this.handItems.set(i, ItemStack.EMPTY);
            }
        }

        for (int ix = 0; ix < this.armorItems.size(); ix++) {
            ItemStack itemStack = this.armorItems.get(ix);
            if (!itemStack.isEmpty()) {
                this.drops.add(new DefaultDrop(itemStack, stack -> Block.popResource(this.level(), this.blockPosition().above(), stack))); // CraftBukkit - add to drops // Paper - Restore vanilla drops behavior; mirror so we can destroy it later - though this call site was safe & spawn drops correctly
                this.armorItems.set(ix, ItemStack.EMPTY);
            }
        }
        return this.dropAllDeathLoot(level, damageSource); // CraftBukkit - moved from above // Paper
    }

    private void playBrokenSound() {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ARMOR_STAND_BREAK, this.getSoundSource(), 1.0F, 1.0F);
    }

    @Override
    protected float tickHeadTurn(float yRot, float animStep) {
        this.yBodyRotO = this.yRotO;
        this.yBodyRot = this.getYRot();
        return 0.0F;
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.hasPhysics()) {
            super.travel(travelVector);
        }
    }

    @Override
    public void setYBodyRot(float offset) {
        this.yBodyRotO = this.yRotO = offset;
        this.yHeadRotO = this.yHeadRot = offset;
    }

    @Override
    public void setYHeadRot(float rotation) {
        this.yBodyRotO = this.yRotO = rotation;
        this.yHeadRotO = this.yHeadRot = rotation;
    }

    @Override
    public void tick() {
        maxUpStep = level().purpurConfig.armorstandStepHeight; // Purpur - Add option to set armorstand step height
        // Paper start - Allow ArmorStands not to tick
        if (!this.canTick) {
            if (this.noTickPoseDirty) {
                this.noTickPoseDirty = false;
                this.updatePose();
            }

            if (this.noTickEquipmentDirty) {
                this.noTickEquipmentDirty = false;
                this.detectEquipmentUpdatesPublic();
            }

            return;
        }
        // Paper end - Allow ArmorStands not to tick
        super.tick();
        // Paper start - Allow ArmorStands not to tick
        this.updatePose();
    }

    public void updatePose() {
        // Paper end - Allow ArmorStands not to tick
        Rotations rotations = this.entityData.get(DATA_HEAD_POSE);
        if (!this.headPose.equals(rotations)) {
            this.setHeadPose(rotations);
        }

        Rotations rotations1 = this.entityData.get(DATA_BODY_POSE);
        if (!this.bodyPose.equals(rotations1)) {
            this.setBodyPose(rotations1);
        }

        Rotations rotations2 = this.entityData.get(DATA_LEFT_ARM_POSE);
        if (!this.leftArmPose.equals(rotations2)) {
            this.setLeftArmPose(rotations2);
        }

        Rotations rotations3 = this.entityData.get(DATA_RIGHT_ARM_POSE);
        if (!this.rightArmPose.equals(rotations3)) {
            this.setRightArmPose(rotations3);
        }

        Rotations rotations4 = this.entityData.get(DATA_LEFT_LEG_POSE);
        if (!this.leftLegPose.equals(rotations4)) {
            this.setLeftLegPose(rotations4);
        }

        Rotations rotations5 = this.entityData.get(DATA_RIGHT_LEG_POSE);
        if (!this.rightLegPose.equals(rotations5)) {
            this.setRightLegPose(rotations5);
        }
    }

    @Override
    protected void updateInvisibilityStatus() {
        this.setInvisible(this.invisible);
    }

    @Override
    public void setInvisible(boolean invisible) {
        this.invisible = invisible;
        super.setInvisible(invisible);
    }

    @Override
    public boolean isBaby() {
        return this.isSmall();
    }

    // CraftBukkit start
     @Override
    public boolean shouldDropExperience() {
        return true; // MC-157395, SPIGOT-5193 even baby (small) armor stands should drop
    }
    // CraftBukkit end

    @Override
    public void kill(ServerLevel level) {
        // CraftBukkit start - pass DamageSource for kill
        this.kill(level, null);
    }

    public void kill(ServerLevel level, @Nullable DamageSource damageSource) {
        // Paper start - make cancellable
        this.kill(level, damageSource, true);
    }

    public void kill(ServerLevel level, @Nullable DamageSource damageSource, boolean callEvent) {
        if (callEvent) {
            org.bukkit.event.entity.EntityDeathEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityDeathEvent(this, (damageSource == null ? this.damageSources().genericKill() : damageSource), this.drops); // CraftBukkit - call event
            if (event.isCancelled()) return;
        }
        // Paper end
        this.remove(Entity.RemovalReason.KILLED, org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
        // CraftBukkit end
        this.gameEvent(GameEvent.ENTITY_DIE);
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return !explosion.shouldAffectBlocklikeEntities() || this.isInvisible();
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return this.isMarker() ? PushReaction.IGNORE : super.getPistonPushReaction();
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return this.isMarker();
    }

    public void setSmall(boolean small) {
        this.entityData.set(DATA_CLIENT_FLAGS, this.setBit(this.entityData.get(DATA_CLIENT_FLAGS), 1, small));
    }

    public boolean isSmall() {
        return (this.entityData.get(DATA_CLIENT_FLAGS) & 1) != 0;
    }

    public void setShowArms(boolean showArms) {
        this.entityData.set(DATA_CLIENT_FLAGS, this.setBit(this.entityData.get(DATA_CLIENT_FLAGS), 4, showArms));
    }

    public boolean showArms() {
        return (this.entityData.get(DATA_CLIENT_FLAGS) & 4) != 0;
    }

    public void setNoBasePlate(boolean noBasePlate) {
        this.entityData.set(DATA_CLIENT_FLAGS, this.setBit(this.entityData.get(DATA_CLIENT_FLAGS), 8, noBasePlate));
    }

    public boolean showBasePlate() {
        return (this.entityData.get(DATA_CLIENT_FLAGS) & 8) == 0;
    }

    public void setMarker(boolean marker) {
        this.entityData.set(DATA_CLIENT_FLAGS, this.setBit(this.entityData.get(DATA_CLIENT_FLAGS), 16, marker));
    }

    public boolean isMarker() {
        return (this.entityData.get(DATA_CLIENT_FLAGS) & 16) != 0;
    }

    private byte setBit(byte oldBit, int offset, boolean value) {
        if (value) {
            oldBit = (byte)(oldBit | offset);
        } else {
            oldBit = (byte)(oldBit & ~offset);
        }

        return oldBit;
    }

    public void setHeadPose(Rotations headPose) {
        this.headPose = headPose;
        this.entityData.set(DATA_HEAD_POSE, headPose);
        this.noTickPoseDirty = true; // Paper - Allow updates when not ticking
    }

    public void setBodyPose(Rotations bodyPose) {
        this.bodyPose = bodyPose;
        this.entityData.set(DATA_BODY_POSE, bodyPose);
        this.noTickPoseDirty = true; // Paper - Allow updates when not ticking
    }

    public void setLeftArmPose(Rotations leftArmPose) {
        this.leftArmPose = leftArmPose;
        this.entityData.set(DATA_LEFT_ARM_POSE, leftArmPose);
        this.noTickPoseDirty = true; // Paper - Allow updates when not ticking
    }

    public void setRightArmPose(Rotations rightArmPose) {
        this.rightArmPose = rightArmPose;
        this.entityData.set(DATA_RIGHT_ARM_POSE, rightArmPose);
        this.noTickPoseDirty = true; // Paper - Allow updates when not ticking
    }

    public void setLeftLegPose(Rotations leftLegPose) {
        this.leftLegPose = leftLegPose;
        this.entityData.set(DATA_LEFT_LEG_POSE, leftLegPose);
        this.noTickPoseDirty = true; // Paper - Allow updates when not ticking
    }

    public void setRightLegPose(Rotations rightLegPose) {
        this.rightLegPose = rightLegPose;
        this.entityData.set(DATA_RIGHT_LEG_POSE, rightLegPose);
        this.noTickPoseDirty = true; // Paper - Allow updates when not ticking
    }

    public Rotations getHeadPose() {
        return this.headPose;
    }

    public Rotations getBodyPose() {
        return this.bodyPose;
    }

    public Rotations getLeftArmPose() {
        return this.leftArmPose;
    }

    public Rotations getRightArmPose() {
        return this.rightArmPose;
    }

    public Rotations getLeftLegPose() {
        return this.leftLegPose;
    }

    public Rotations getRightLegPose() {
        return this.rightLegPose;
    }

    @Override
    public boolean isPickable() {
        return super.isPickable() && !this.isMarker();
    }

    @Override
    public boolean skipAttackInteraction(Entity entity) {
        return entity instanceof Player && !this.level().mayInteract((Player)entity, this.blockPosition());
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public LivingEntity.Fallsounds getFallSounds() {
        return new LivingEntity.Fallsounds(SoundEvents.ARMOR_STAND_FALL, SoundEvents.ARMOR_STAND_FALL);
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ARMOR_STAND_HIT;
    }

    @Nullable
    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.ARMOR_STAND_BREAK;
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_CLIENT_FLAGS.equals(key)) {
            this.refreshDimensions();
            this.blocksBuilding = !this.isMarker();
        }

        super.onSyncedDataUpdated(key);
    }

    @Override
    public boolean attackable() {
        return false;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.getDimensionsMarker(this.isMarker());
    }

    private EntityDimensions getDimensionsMarker(boolean isMarker) {
        if (isMarker) {
            return MARKER_DIMENSIONS;
        } else {
            return this.isBaby() ? BABY_DIMENSIONS : this.getType().getDimensions();
        }
    }

    @Override
    public Vec3 getLightProbePosition(float partialTicks) {
        if (this.isMarker()) {
            AABB aabb = this.getDimensionsMarker(false).makeBoundingBox(this.position());
            BlockPos blockPos = this.blockPosition();
            int i = Integer.MIN_VALUE;

            for (BlockPos blockPos1 : BlockPos.betweenClosed(
                BlockPos.containing(aabb.minX, aabb.minY, aabb.minZ), BlockPos.containing(aabb.maxX, aabb.maxY, aabb.maxZ)
            )) {
                int max = Math.max(this.level().getBrightness(LightLayer.BLOCK, blockPos1), this.level().getBrightness(LightLayer.SKY, blockPos1));
                if (max == 15) {
                    return Vec3.atCenterOf(blockPos1);
                }

                if (max > i) {
                    i = max;
                    blockPos = blockPos1.immutable();
                }
            }

            return Vec3.atCenterOf(blockPos);
        } else {
            return super.getLightProbePosition(partialTicks);
        }
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.ARMOR_STAND);
    }

    @Override
    public boolean canBeSeenByAnyone() {
        return !this.isInvisible() && !this.isMarker();
    }

    // Paper start
    @Override
    public void move(net.minecraft.world.entity.MoverType type, Vec3 movement) {
        if (this.canMove) {
            super.move(type, movement);
        }
    }
    // Paper end

    // Purpur start - Movement options for armor stands
    @Override
    public void updateInWaterStateAndDoWaterCurrentPushing() {
        if (this.level().purpurConfig.armorstandWaterMovement &&
            (this.level().purpurConfig.armorstandWaterFence || !(level().getBlockState(blockPosition().below()).getBlock() instanceof net.minecraft.world.level.block.FenceBlock)))
            super.updateInWaterStateAndDoWaterCurrentPushing();
    }

    @Override
    public void aiStep() {
        if (this.canMovementTick && this.canMove) super.aiStep();
    }
    // Purpur end - Movement options for armor stands
}
