package net.minecraft.world.entity.item;

import com.mojang.logging.LogUtils;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class FallingBlockEntity extends Entity {
    private static final Logger LOGGER = LogUtils.getLogger();
    public BlockState blockState = Blocks.SAND.defaultBlockState();
    public int time;
    public boolean dropItem = true;
    public boolean cancelDrop;
    public boolean hurtEntities;
    public int fallDamageMax = 40;
    public float fallDamagePerDistance;
    @Nullable
    public CompoundTag blockData;
    public boolean forceTickAfterTeleportToDuplicate;
    protected static final EntityDataAccessor<BlockPos> DATA_START_POS = SynchedEntityData.defineId(FallingBlockEntity.class, EntityDataSerializers.BLOCK_POS);
    public boolean autoExpire = true; // Paper - Expand FallingBlock API

    public FallingBlockEntity(EntityType<? extends FallingBlockEntity> entityType, Level level) {
        super(entityType, level);
    }

    public FallingBlockEntity(Level level, double x, double y, double z, BlockState state) {
        this(EntityType.FALLING_BLOCK, level);
        this.blockState = state;
        this.blocksBuilding = true;
        this.setPos(x, y, z);
        this.setDeltaMovement(Vec3.ZERO);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.setStartPos(this.blockPosition());
    }

    public static FallingBlockEntity fall(Level level, BlockPos pos, BlockState blockState) {
        FallingBlockEntity fallingBlockEntity = new FallingBlockEntity(
            level,
            pos.getX() + 0.5,
            pos.getY(),
            pos.getZ() + 0.5,
            blockState.hasProperty(BlockStateProperties.WATERLOGGED)
                ? blockState.setValue(BlockStateProperties.WATERLOGGED, Boolean.valueOf(false))
                : blockState
        );
        if (!CraftEventFactory.callEntityChangeBlockEvent(fallingBlockEntity, pos, blockState.getFluidState().createLegacyBlock())) return fallingBlockEntity; // CraftBukkit
        level.setBlock(pos, blockState.getFluidState().createLegacyBlock(), 3);
        level.addFreshEntity(fallingBlockEntity);
        return fallingBlockEntity;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (!this.isInvulnerableToBase(damageSource)) {
            this.markHurt();
        }

        return false;
    }

    public void setStartPos(BlockPos startPos) {
        this.entityData.set(DATA_START_POS, startPos);
    }

    public BlockPos getStartPos() {
        return this.entityData.get(DATA_START_POS);
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_START_POS, BlockPos.ZERO);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04;
    }

    @Override
    public void tick() {
        if (this.blockState.isAir()) {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else {
            Block block = this.blockState.getBlock();
            this.time++;
            this.applyGravity();
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.applyEffectsFromBlocks();
            // Paper start - Configurable falling blocks height nerf
            if (this.level().paperConfig().fixes.fallingBlockHeightNerf.test(v -> this.getY() > v)) {
                if (this.dropItem && this.level() instanceof final ServerLevel serverLevel && serverLevel.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                    this.spawnAtLocation(serverLevel, block);
                }
                this.discard(EntityRemoveEvent.Cause.OUT_OF_WORLD);
                return;
            }
            // Paper end - Configurable falling blocks height nerf
            this.handlePortal();
            if (this.level() instanceof ServerLevel serverLevel && (this.isAlive() || this.forceTickAfterTeleportToDuplicate)) {
                BlockPos blockPos = this.blockPosition();
                boolean flag = this.blockState.getBlock() instanceof ConcretePowderBlock;
                boolean flag1 = flag && this.level().getFluidState(blockPos).is(FluidTags.WATER);
                double d = this.getDeltaMovement().lengthSqr();
                if (flag && d > 1.0) {
                    BlockHitResult blockHitResult = this.level()
                        .clip(
                            new ClipContext(
                                new Vec3(this.xo, this.yo, this.zo), this.position(), ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, this
                            )
                        );
                    if (blockHitResult.getType() != HitResult.Type.MISS && this.level().getFluidState(blockHitResult.getBlockPos()).is(FluidTags.WATER)) {
                        blockPos = blockHitResult.getBlockPos();
                        flag1 = true;
                    }
                }

                if (!this.onGround() && !flag1) {
                    if ((this.time > 100 && autoExpire) && (blockPos.getY() <= this.level().getMinY() || blockPos.getY() > this.level().getMaxY()) || (this.time > 600 && autoExpire)) { // Paper - Expand FallingBlock API
                        if (this.dropItem && serverLevel.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                            this.spawnAtLocation(serverLevel, block);
                        }

                        this.discard(EntityRemoveEvent.Cause.DROP); // CraftBukkit - add Bukkit remove cause
                    }
                } else {
                    BlockState blockState = this.level().getBlockState(blockPos);
                    this.setDeltaMovement(this.getDeltaMovement().multiply(0.7, -0.5, 0.7));
                    if (!blockState.is(Blocks.MOVING_PISTON)) {
                        if (!this.cancelDrop) {
                            boolean canBeReplaced = blockState.canBeReplaced(
                                new DirectionalPlaceContext(this.level(), blockPos, Direction.DOWN, ItemStack.EMPTY, Direction.UP)
                            );
                            boolean flag2 = FallingBlock.isFree(this.level().getBlockState(blockPos.below())) && (!flag || !flag1);
                            boolean flag3 = this.blockState.canSurvive(this.level(), blockPos) && !flag2;
                            if (canBeReplaced && flag3) {
                                if (this.blockState.hasProperty(BlockStateProperties.WATERLOGGED)
                                    && this.level().getFluidState(blockPos).getType() == Fluids.WATER) {
                                    this.blockState = this.blockState.setValue(BlockStateProperties.WATERLOGGED, Boolean.valueOf(true));
                                }

                                // CraftBukkit start
                                if (!CraftEventFactory.callEntityChangeBlockEvent(this, blockPos, this.blockState)) {
                                    this.discard(EntityRemoveEvent.Cause.DESPAWN); // SPIGOT-6586 called before the event in previous versions
                                    return;
                                }
                                // CraftBukkit end
                                if (this.level().setBlock(blockPos, this.blockState, 3)) {
                                    ((ServerLevel)this.level())
                                        .getChunkSource()
                                        .chunkMap
                                        .broadcast(this, new ClientboundBlockUpdatePacket(blockPos, this.level().getBlockState(blockPos)));
                                    this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                                    if (block instanceof Fallable) {
                                        ((Fallable)block).onLand(this.level(), blockPos, this.blockState, blockState, this);
                                    }

                                    if (this.blockData != null && this.blockState.hasBlockEntity()) {
                                        BlockEntity blockEntity = this.level().getBlockEntity(blockPos);
                                        if (blockEntity != null) {
                                            CompoundTag compoundTag = blockEntity.saveWithoutMetadata(this.level().registryAccess());

                                            for (String string : this.blockData.getAllKeys()) {
                                                compoundTag.put(string, this.blockData.get(string).copy());
                                            }

                                            try {
                                                blockEntity.loadWithComponents(compoundTag, this.level().registryAccess());
                                            } catch (Exception var16) {
                                                LOGGER.error("Failed to load block entity from falling block", (Throwable)var16);
                                            }

                                            blockEntity.setChanged();
                                        }
                                    }
                                } else if (this.dropItem && serverLevel.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                                    this.discard(EntityRemoveEvent.Cause.DROP); // CraftBukkit - add Bukkit remove cause
                                    this.callOnBrokenAfterFall(block, blockPos);
                                    this.spawnAtLocation(serverLevel, block);
                                }
                            } else {
                                this.discard(EntityRemoveEvent.Cause.DROP); // CraftBukkit - add Bukkit remove cause
                                if (this.dropItem && serverLevel.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                                    this.callOnBrokenAfterFall(block, blockPos);
                                    this.spawnAtLocation(serverLevel, block);
                                }
                            }
                        } else {
                            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                            this.callOnBrokenAfterFall(block, blockPos);
                        }
                    }
                }
            }

            this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
        }
    }

    public void callOnBrokenAfterFall(Block block, BlockPos pos) {
        if (block instanceof Fallable) {
            ((Fallable)block).onBrokenAfterFall(this.level(), pos, this);
        }
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (!this.hurtEntities) {
            return false;
        } else {
            int ceil = Mth.ceil(fallDistance - 1.0F);
            if (ceil < 0) {
                return false;
            } else {
                Predicate<Entity> predicate = EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(EntitySelector.LIVING_ENTITY_STILL_ALIVE);
                DamageSource damageSource = this.blockState.getBlock() instanceof Fallable fallable
                    ? fallable.getFallDamageSource(this)
                    : this.damageSources().fallingBlock(this);
                float f = Math.min(Mth.floor(ceil * this.fallDamagePerDistance), this.fallDamageMax);
                this.level().getEntities(this, this.getBoundingBox(), predicate).forEach(entity -> entity.hurt(damageSource, f));
                boolean isAnvil = this.blockState.is(BlockTags.ANVIL);
                if (isAnvil && f > 0.0F && this.random.nextFloat() < 0.05F + ceil * 0.05F) {
                    BlockState blockState = AnvilBlock.damage(this.blockState);
                    if (blockState == null) {
                        this.cancelDrop = true;
                    } else {
                        this.blockState = blockState;
                    }
                }

                return false;
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.put("BlockState", NbtUtils.writeBlockState(this.blockState));
        compound.putInt("Time", this.time);
        compound.putBoolean("DropItem", this.dropItem);
        compound.putBoolean("HurtEntities", this.hurtEntities);
        compound.putFloat("FallHurtAmount", this.fallDamagePerDistance);
        compound.putInt("FallHurtMax", this.fallDamageMax);
        if (this.blockData != null) {
            compound.put("TileEntityData", this.blockData);
        }

        compound.putBoolean("CancelDrop", this.cancelDrop);
        if (!this.autoExpire) compound.putBoolean("Paper.AutoExpire", false); // Paper - Expand FallingBlock API
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        this.blockState = NbtUtils.readBlockState(this.level().holderLookup(Registries.BLOCK), compound.getCompound("BlockState"));
        this.time = compound.getInt("Time");
        if (compound.contains("HurtEntities", 99)) {
            this.hurtEntities = compound.getBoolean("HurtEntities");
            this.fallDamagePerDistance = compound.getFloat("FallHurtAmount");
            this.fallDamageMax = compound.getInt("FallHurtMax");
        } else if (this.blockState.is(BlockTags.ANVIL)) {
            this.hurtEntities = true;
        }

        if (compound.contains("DropItem", 99)) {
            this.dropItem = compound.getBoolean("DropItem");
        }

        if (compound.contains("TileEntityData", 10) && !(this.level().paperConfig().entities.spawning.filterBadTileEntityNbtFromFallingBlocks && this.blockState.getBlock() instanceof net.minecraft.world.level.block.GameMasterBlock)) { // Paper - Filter bad block entity nbt data from falling blocks
            this.blockData = compound.getCompound("TileEntityData").copy();
        }

        this.cancelDrop = compound.getBoolean("CancelDrop");
        if (this.blockState.isAir()) {
            this.blockState = Blocks.SAND.defaultBlockState();
        }

        // Paper start - Expand FallingBlock API
        if (compound.contains("Paper.AutoExpire")) {
            this.autoExpire = compound.getBoolean("Paper.AutoExpire");
        }
        // Paper end - Expand FallingBlock API
    }

    public void setHurtsEntities(float fallDamagePerDistance, int fallDamageMax) {
        this.hurtEntities = true;
        this.fallDamagePerDistance = fallDamagePerDistance;
        this.fallDamageMax = fallDamageMax;
    }

    public void disableDrop() {
        this.cancelDrop = true;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public void fillCrashReportCategory(CrashReportCategory category) {
        super.fillCrashReportCategory(category);
        category.setDetail("Immitating BlockState", this.blockState.toString());
    }

    public BlockState getBlockState() {
        return this.blockState;
    }

    @Override
    protected Component getTypeName() {
        return Component.translatable("entity.minecraft.falling_block_type", this.blockState.getBlock().getName());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity, Block.getId(this.getBlockState()));
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.blockState = Block.stateById(packet.getData());
        this.blocksBuilding = true;
        double x = packet.getX();
        double y = packet.getY();
        double z = packet.getZ();
        this.setPos(x, y, z);
        this.setStartPos(this.blockPosition());
    }

    @Nullable
    @Override
    public Entity teleport(TeleportTransition teleportTransition) {
        ResourceKey<Level> resourceKey = teleportTransition.newLevel().dimension();
        ResourceKey<Level> resourceKey1 = this.level().dimension();
        boolean flag = (resourceKey1 == Level.END || resourceKey == Level.END) && resourceKey1 != resourceKey;
        Entity entity = super.teleport(teleportTransition);
        this.forceTickAfterTeleportToDuplicate = entity != null && flag && io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.allowUnsafeEndPortalTeleportation; // Paper
        return entity;
    }
}
