package net.minecraft.world.entity.decoration;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;

public class ItemFrame extends HangingEntity {
    public static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(ItemFrame.class, EntityDataSerializers.ITEM_STACK);
    public static final EntityDataAccessor<Integer> DATA_ROTATION = SynchedEntityData.defineId(ItemFrame.class, EntityDataSerializers.INT);
    public static final int NUM_ROTATIONS = 8;
    private static final float DEPTH = 0.0625F;
    private static final float WIDTH = 0.75F;
    private static final float HEIGHT = 0.75F;
    public float dropChance = 1.0F;
    public boolean fixed;
    public @Nullable MapId cachedMapId; // Paper - Perf: Cache map ids on item frames

    public ItemFrame(EntityType<? extends ItemFrame> entityType, Level level) {
        super(entityType, level);
    }

    public ItemFrame(Level level, BlockPos pos, Direction facingDirection) {
        this(EntityType.ITEM_FRAME, level, pos, facingDirection);
    }

    public ItemFrame(EntityType<? extends ItemFrame> entityType, Level level, BlockPos pos, Direction direction) {
        super(entityType, level, pos);
        this.setDirection(direction);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM, ItemStack.EMPTY);
        builder.define(DATA_ROTATION, 0);
    }

    @Override
    public void setDirection(Direction facingDirection) {
        Validate.notNull(facingDirection);
        this.direction = facingDirection;
        if (facingDirection.getAxis().isHorizontal()) {
            this.setXRot(0.0F);
            this.setYRot(this.direction.get2DDataValue() * 90);
        } else {
            this.setXRot(-90 * facingDirection.getAxisDirection().getStep());
            this.setYRot(0.0F);
        }

        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
        this.recalculateBoundingBox();
    }

    @Override
    protected AABB calculateBoundingBox(BlockPos pos, Direction direction) {
        // CraftBukkit start - break out BB calc into own method
        return ItemFrame.calculateBoundingBoxStatic(pos, direction);
    }

    public static AABB calculateBoundingBoxStatic(BlockPos pos, Direction direction) {
        // CraftBukkit end
        float f = 0.46875F;
        Vec3 vec3 = Vec3.atCenterOf(pos).relative(direction, -0.46875);
        Direction.Axis axis = direction.getAxis();
        double d = axis == Direction.Axis.X ? 0.0625 : 0.75;
        double d1 = axis == Direction.Axis.Y ? 0.0625 : 0.75;
        double d2 = axis == Direction.Axis.Z ? 0.0625 : 0.75;
        return AABB.ofSize(vec3, d, d1, d2);
    }

    @Override
    public boolean survives() {
        if (this.fixed) {
            return true;
        } else if (!this.level().noCollision(this)) {
            return false;
        } else {
            BlockState blockState = this.level().getBlockState(this.pos.relative(this.direction.getOpposite()));
            return (blockState.isSolid() || this.direction.getAxis().isHorizontal() && DiodeBlock.isDiode(blockState))
                && this.level().getEntities(this, this.getBoundingBox(), HANGING_ENTITY).isEmpty();
        }
    }

    @Override
    public void move(MoverType type, Vec3 pos) {
        if (!this.fixed) {
            super.move(type, pos);
        }
    }

    @Override
    public void push(double x, double y, double z, @Nullable Entity pushingEntity) { // Paper - add push source entity param
        if (!this.fixed) {
            super.push(x, y, z, pushingEntity); // Paper - add push source entity param
        }
    }

    @Override
    public void kill(ServerLevel level) {
        this.removeFramedMap(this.getItem());
        super.kill(level);
    }

    private boolean shouldDamageDropItem(DamageSource damageSource) {
        return !damageSource.is(DamageTypeTags.IS_EXPLOSION) && !this.getItem().isEmpty();
    }

    private static boolean canHurtWhenFixed(DamageSource damageSource) {
        return damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || damageSource.isCreativePlayer();
    }

    @Override
    public boolean hurtClient(DamageSource damageSource) {
        return (!this.fixed || canHurtWhenFixed(damageSource)) && !this.isInvulnerableToBase(damageSource);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (!this.fixed) {
            if (this.isInvulnerableToBase(damageSource)) {
                return false;
            } else if (this.shouldDamageDropItem(damageSource)) {
                // CraftBukkit start - fire EntityDamageEvent
                if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, damageSource, amount, false) || this.isRemoved()) {
                    return true;
                }
                // CraftBukkit end
                // Paper start - Add PlayerItemFrameChangeEvent
                if (damageSource.getEntity() instanceof Player player) {
                    var event = new io.papermc.paper.event.player.PlayerItemFrameChangeEvent((org.bukkit.entity.Player) player.getBukkitEntity(), (org.bukkit.entity.ItemFrame) this.getBukkitEntity(), this.getItem().asBukkitCopy(), io.papermc.paper.event.player.PlayerItemFrameChangeEvent.ItemFrameChangeAction.REMOVE);
                    if (!event.callEvent()) return true; // return true here because you aren't cancelling the damage, just the change
                    this.setItem(ItemStack.fromBukkitCopy(event.getItemStack()), false);
                }
                // Paper end - Add PlayerItemFrameChangeEvent
                this.dropItem(level, damageSource.getEntity(), false);
                this.gameEvent(GameEvent.BLOCK_CHANGE, damageSource.getEntity());
                this.playSound(this.getRemoveItemSound(), 1.0F, 1.0F);
                return true;
            } else {
                return super.hurtServer(level, damageSource, amount);
            }
        } else {
            return canHurtWhenFixed(damageSource) && super.hurtServer(level, damageSource, amount);
        }
    }

    public SoundEvent getRemoveItemSound() {
        return SoundEvents.ITEM_FRAME_REMOVE_ITEM;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d = 16.0;
        d *= 64.0 * getViewScale();
        return distance < d * d;
    }

    @Override
    public void dropItem(ServerLevel level, @Nullable Entity entity) {
        this.playSound(this.getBreakSound(), 1.0F, 1.0F);
        this.dropItem(level, entity, true);
        this.gameEvent(GameEvent.BLOCK_CHANGE, entity);
    }

    public SoundEvent getBreakSound() {
        return SoundEvents.ITEM_FRAME_BREAK;
    }

    @Override
    public void playPlacementSound() {
        this.playSound(this.getPlaceSound(), 1.0F, 1.0F);
    }

    public SoundEvent getPlaceSound() {
        return SoundEvents.ITEM_FRAME_PLACE;
    }

    private void dropItem(ServerLevel level, @Nullable Entity entity, boolean dropItem) {
        if (!this.fixed) {
            ItemStack item = this.getItem();
            this.setItem(ItemStack.EMPTY);
            if (!level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                if (entity == null) {
                    this.removeFramedMap(item);
                }
            } else if (entity instanceof Player player && player.hasInfiniteMaterials()) {
                this.removeFramedMap(item);
            } else {
                if (dropItem) {
                    // Purpur start - Apply display names from item forms of entities to entities and vice versa
                    final ItemStack itemFrame = this.getFrameItemStack();
                    if (!level.purpurConfig.persistentDroppableEntityDisplayNames) itemFrame.set(DataComponents.CUSTOM_NAME, null);
                    this.spawnAtLocation(level, itemFrame);
                    // Purpur end - Apply display names from item forms of entities to entities and vice versa
                }

                if (!item.isEmpty()) {
                    item = item.copy();
                    this.removeFramedMap(item);
                    if (this.random.nextFloat() < this.dropChance) {
                        this.spawnAtLocation(level, item);
                    }
                }
            }
        }
    }

    private void removeFramedMap(ItemStack stack) {
        MapId framedMapId = this.getFramedMapId(stack);
        if (framedMapId != null) {
            MapItemSavedData savedData = MapItem.getSavedData(framedMapId, this.level());
            if (savedData != null) {
                savedData.removedFromFrame(this.pos, this.getId());
            }
        }

        stack.setEntityRepresentation(null);
    }

    public ItemStack getItem() {
        return this.getEntityData().get(DATA_ITEM);
    }

    // Paper start - Fix MC-123848 (spawn item frame drops above block)
    @Nullable
    @Override
    public net.minecraft.world.entity.item.ItemEntity spawnAtLocation(ServerLevel serverLevel, ItemStack stack) {
        return this.spawnAtLocation(serverLevel, stack, this.getDirection() == Direction.DOWN ? -0.6F : 0.0F);
    }
    // Paper end

    @Nullable
    public MapId getFramedMapId(ItemStack stack) {
        return stack.get(DataComponents.MAP_ID);
    }

    public boolean hasFramedMap() {
        return this.getItem().has(DataComponents.MAP_ID);
    }

    public void setItem(ItemStack stack) {
        this.setItem(stack, true);
    }

    public void setItem(ItemStack stack, boolean updateNeighbours) {
        // CraftBukkit start
        this.setItem(stack, updateNeighbours, true);
    }

    public void setItem(ItemStack stack, boolean updateNeighbours, boolean playSound) {
        // CraftBukkit end
        if (!stack.isEmpty()) {
            stack = stack.copyWithCount(1);
        }

        this.onItemChanged(stack);
        this.getEntityData().set(DATA_ITEM, stack);
        if (!stack.isEmpty() && updateNeighbours && playSound) { // CraftBukkit // Paper - only play sound when update flag is set
            this.playSound(this.getAddItemSound(), 1.0F, 1.0F);
        }

        if (updateNeighbours && this.pos != null) {
            this.level().updateNeighbourForOutputSignal(this.pos, Blocks.AIR);
        }
    }

    public SoundEvent getAddItemSound() {
        return SoundEvents.ITEM_FRAME_ADD_ITEM;
    }

    @Override
    public SlotAccess getSlot(int slot) {
        return slot == 0 ? SlotAccess.of(this::getItem, this::setItem) : super.getSlot(slot);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (key.equals(DATA_ITEM)) {
            this.onItemChanged(this.getItem());
        }
    }

    private void onItemChanged(ItemStack item) {
        this.cachedMapId = item.getComponents().get(net.minecraft.core.component.DataComponents.MAP_ID); // Paper - Perf: Cache map ids on item frames
        if (!item.isEmpty() && item.getFrame() != this) {
            item.setEntityRepresentation(this);
        }

        this.recalculateBoundingBox();
    }

    public int getRotation() {
        return this.getEntityData().get(DATA_ROTATION);
    }

    public void setRotation(int rotation) {
        this.setRotation(rotation, true);
    }

    private void setRotation(int rotation, boolean updateNeighbours) {
        this.getEntityData().set(DATA_ROTATION, rotation % 8);
        if (updateNeighbours && this.pos != null) {
            this.level().updateNeighbourForOutputSignal(this.pos, Blocks.AIR);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (!this.getItem().isEmpty()) {
            compound.put("Item", this.getItem().save(this.registryAccess()));
            compound.putByte("ItemRotation", (byte)this.getRotation());
            compound.putFloat("ItemDropChance", this.dropChance);
        }

        compound.putByte("Facing", (byte)this.direction.get3DDataValue());
        compound.putBoolean("Invisible", this.isInvisible());
        compound.putBoolean("Fixed", this.fixed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        ItemStack itemStack;
        if (compound.contains("Item", 10)) {
            CompoundTag compound1 = compound.getCompound("Item");
            itemStack = ItemStack.parse(this.registryAccess(), compound1).orElse(ItemStack.EMPTY);
        } else {
            itemStack = ItemStack.EMPTY;
        }

        ItemStack item = this.getItem();
        if (!item.isEmpty() && !ItemStack.matches(itemStack, item)) {
            this.removeFramedMap(item);
        }

        this.setItem(itemStack, false);
        if (!itemStack.isEmpty()) {
            this.setRotation(compound.getByte("ItemRotation"), false);
            if (compound.contains("ItemDropChance", 99)) {
                this.dropChance = compound.getFloat("ItemDropChance");
            }
        }

        this.setDirection(Direction.from3DDataValue(compound.getByte("Facing")));
        this.setInvisible(compound.getBoolean("Invisible"));
        this.fixed = compound.getBoolean("Fixed");
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        boolean flag = !this.getItem().isEmpty();
        boolean flag1 = !itemInHand.isEmpty();
        if (this.fixed) {
            return InteractionResult.PASS;
        } else if (!player.level().isClientSide) {
            if (!flag) {
                if (flag1 && !this.isRemoved()) {
                    MapItemSavedData savedData = MapItem.getSavedData(itemInHand, this.level());
                    if (savedData != null && savedData.isTrackedCountOverLimit(256)) {
                        return InteractionResult.FAIL;
                    } else {
                        // Paper start - Add PlayerItemFrameChangeEvent
                        io.papermc.paper.event.player.PlayerItemFrameChangeEvent event = new io.papermc.paper.event.player.PlayerItemFrameChangeEvent((org.bukkit.entity.Player) player.getBukkitEntity(), (org.bukkit.entity.ItemFrame) this.getBukkitEntity(), itemInHand.asBukkitCopy(), io.papermc.paper.event.player.PlayerItemFrameChangeEvent.ItemFrameChangeAction.PLACE);
                        if (!event.callEvent()) {
                            return InteractionResult.FAIL;
                        }
                        this.setItem(ItemStack.fromBukkitCopy(event.getItemStack()));
                        // Paper end - Add PlayerItemFrameChangeEvent
                        this.gameEvent(GameEvent.BLOCK_CHANGE, player);
                        itemInHand.consume(1, player);
                        return InteractionResult.SUCCESS;
                    }
                } else {
                    return InteractionResult.PASS;
                }
            } else {
                // Paper start - Add PlayerItemFrameChangeEvent
                io.papermc.paper.event.player.PlayerItemFrameChangeEvent event = new io.papermc.paper.event.player.PlayerItemFrameChangeEvent((org.bukkit.entity.Player) player.getBukkitEntity(), (org.bukkit.entity.ItemFrame) this.getBukkitEntity(), this.getItem().asBukkitCopy(), io.papermc.paper.event.player.PlayerItemFrameChangeEvent.ItemFrameChangeAction.ROTATE);
                if (!event.callEvent()) {
                    return InteractionResult.FAIL;
                }
                setItem(ItemStack.fromBukkitCopy(event.getItemStack()), false, false);
                // Paper end - Add PlayerItemFrameChangeEvent
                this.playSound(this.getRotateItemSound(), 1.0F, 1.0F);
                this.setRotation(this.getRotation() + 1);
                this.gameEvent(GameEvent.BLOCK_CHANGE, player);
                return InteractionResult.SUCCESS;
            }
        } else {
            return (InteractionResult)(!flag && !flag1 ? InteractionResult.PASS : InteractionResult.SUCCESS);
        }
    }

    public SoundEvent getRotateItemSound() {
        return SoundEvents.ITEM_FRAME_ROTATE_ITEM;
    }

    public int getAnalogOutput() {
        return this.getItem().isEmpty() ? 0 : this.getRotation() % 8 + 1;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, this.direction.get3DDataValue(), this.getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.setDirection(Direction.from3DDataValue(packet.getData()));
    }

    @Override
    public ItemStack getPickResult() {
        ItemStack item = this.getItem();
        return item.isEmpty() ? this.getFrameItemStack() : item.copy();
    }

    protected ItemStack getFrameItemStack() {
        return new ItemStack(Items.ITEM_FRAME);
    }

    @Override
    public float getVisualRotationYInDegrees() {
        Direction direction = this.getDirection();
        int i = direction.getAxis().isVertical() ? 90 * direction.getAxisDirection().getStep() : 0;
        return Mth.wrapDegrees(180 + direction.get2DDataValue() * 90 + this.getRotation() * 45 + i);
    }
}
