package net.minecraft.world.entity.animal.horse;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public abstract class AbstractChestedHorse extends AbstractHorse {
    private static final EntityDataAccessor<Boolean> DATA_ID_CHEST = SynchedEntityData.defineId(AbstractChestedHorse.class, EntityDataSerializers.BOOLEAN);
    private final EntityDimensions babyDimensions;

    protected AbstractChestedHorse(EntityType<? extends AbstractChestedHorse> entityType, Level level) {
        super(entityType, level);
        this.canGallop = false;
        this.babyDimensions = entityType.getDimensions()
            .withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, entityType.getHeight() - 0.15625F, 0.0F))
            .scale(0.5F);
    }

    @Override
    protected void randomizeAttributes(RandomSource random) {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(generateMaxHealth(random::nextInt));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ID_CHEST, false);
    }

    public static AttributeSupplier.Builder createBaseChestedHorseAttributes() {
        return createBaseHorseAttributes().add(Attributes.MOVEMENT_SPEED, 0.175F).add(Attributes.JUMP_STRENGTH, 0.5);
    }

    public boolean hasChest() {
        return this.entityData.get(DATA_ID_CHEST);
    }

    public void setChest(boolean chested) {
        this.entityData.set(DATA_ID_CHEST, chested);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? this.babyDimensions : super.getDefaultDimensions(pose);
    }

    @Override
    protected void dropEquipment(ServerLevel level) {
        super.dropEquipment(level);
        if (this.hasChest()) {
            this.spawnAtLocation(level, Blocks.CHEST);
            // Paper start - moved to post death logic
        }
    }
    protected void postDeathDropItems(org.bukkit.event.entity.EntityDeathEvent event) {
        if (this.hasChest() && !event.isCancelled()) {
            // Paper end - moved to post death logic
            this.setChest(false);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("ChestedHorse", this.hasChest());
        if (this.hasChest()) {
            ListTag listTag = new ListTag();

            for (int i = 1; i < this.inventory.getContainerSize(); i++) {
                ItemStack item = this.inventory.getItem(i);
                if (!item.isEmpty()) {
                    CompoundTag compoundTag = new CompoundTag();
                    compoundTag.putByte("Slot", (byte)(i - 1));
                    listTag.add(item.save(this.registryAccess(), compoundTag));
                }
            }

            compound.put("Items", listTag);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setChest(compound.getBoolean("ChestedHorse"));
        this.createInventory();
        if (this.hasChest()) {
            ListTag list = compound.getList("Items", 10);

            for (int i = 0; i < list.size(); i++) {
                CompoundTag compound1 = list.getCompound(i);
                int i1 = compound1.getByte("Slot") & 255;
                if (i1 < this.inventory.getContainerSize() - 1) {
                    this.inventory.setItem(i1 + 1, ItemStack.parse(this.registryAccess(), compound1).orElse(ItemStack.EMPTY));
                }
            }
        }

        this.syncSaddleToClients();
    }

    @Override
    public SlotAccess getSlot(int slot) {
        return slot == 499 ? new SlotAccess() {
            @Override
            public ItemStack get() {
                return AbstractChestedHorse.this.hasChest() ? new ItemStack(Items.CHEST) : ItemStack.EMPTY;
            }

            @Override
            public boolean set(ItemStack carried) {
                if (carried.isEmpty()) {
                    if (AbstractChestedHorse.this.hasChest()) {
                        AbstractChestedHorse.this.setChest(false);
                        AbstractChestedHorse.this.createInventory();
                    }

                    return true;
                } else if (carried.is(Items.CHEST)) {
                    if (!AbstractChestedHorse.this.hasChest()) {
                        AbstractChestedHorse.this.setChest(true);
                        AbstractChestedHorse.this.createInventory();
                    }

                    return true;
                } else {
                    return false;
                }
            }
        } : super.getSlot(slot);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        boolean flag = !this.isBaby() && this.isTamed() && player.isSecondaryUseActive();
        if (!this.isVehicle() && !flag) {
            ItemStack itemInHand = player.getItemInHand(hand);
            if (!itemInHand.isEmpty()) {
                if (this.isFood(itemInHand)) {
                    return this.fedFood(player, itemInHand);
                }

                if (!this.isTamed()) {
                    this.makeMad();
                    return InteractionResult.SUCCESS;
                }

                if (!this.hasChest() && itemInHand.is(Items.CHEST)) {
                    this.equipChest(player, itemInHand);
                    return InteractionResult.SUCCESS;
                }
            }

            return super.mobInteract(player, hand);
        } else {
            return super.mobInteract(player, hand);
        }
    }

    private void equipChest(Player player, ItemStack chestStack) {
        this.setChest(true);
        this.playChestEquipsSound();
        chestStack.consume(1, player);
        this.createInventory();
    }

    protected void playChestEquipsSound() {
        this.playSound(SoundEvents.DONKEY_CHEST, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
    }

    @Override
    public int getInventoryColumns() {
        return this.hasChest() ? 5 : 0;
    }
}
