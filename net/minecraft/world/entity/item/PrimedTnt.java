package net.minecraft.world.entity.item;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.portal.TeleportTransition;

// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
// CraftBukkit end

public class PrimedTnt extends Entity implements TraceableEntity {
    private static final EntityDataAccessor<Integer> DATA_FUSE_ID = SynchedEntityData.defineId(PrimedTnt.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockState> DATA_BLOCK_STATE_ID = SynchedEntityData.defineId(PrimedTnt.class, EntityDataSerializers.BLOCK_STATE);
    private static final int DEFAULT_FUSE_TIME = 80;
    private static final float DEFAULT_EXPLOSION_POWER = 4.0F;
    private static final String TAG_BLOCK_STATE = "block_state";
    public static final String TAG_FUSE = "fuse";
    private static final String TAG_EXPLOSION_POWER = "explosion_power";
    private static final ExplosionDamageCalculator USED_PORTAL_DAMAGE_CALCULATOR = new ExplosionDamageCalculator() {
        @Override
        public boolean shouldBlockExplode(Explosion explosion, BlockGetter reader, BlockPos pos, BlockState state, float power) {
            return !state.is(Blocks.NETHER_PORTAL) && super.shouldBlockExplode(explosion, reader, pos, state, power);
        }

        @Override
        public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter reader, BlockPos pos, BlockState state, FluidState fluid) {
            return state.is(Blocks.NETHER_PORTAL) ? Optional.empty() : super.getBlockExplosionResistance(explosion, reader, pos, state, fluid);
        }
    };
    @Nullable
    public LivingEntity owner;
    private boolean usedPortal;
    public float explosionPower = 4.0F;
    public boolean isIncendiary = false; // CraftBukkit - add field

    public PrimedTnt(EntityType<? extends PrimedTnt> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
    }

    public PrimedTnt(Level level, double x, double y, double z, @Nullable LivingEntity owner) {
        this(EntityType.TNT, level);
        this.setPos(x, y, z);
        double d = this.random.nextDouble() * (float) (Math.PI * 2); // Paper - Don't use level random in entity constructors
        this.setDeltaMovement(-Math.sin(d) * 0.02, 0.2F, -Math.cos(d) * 0.02);
        this.setFuse(80);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.owner = owner;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FUSE_ID, 80);
        builder.define(DATA_BLOCK_STATE_ID, Blocks.TNT.defaultBlockState());
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
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
        if (this.level().spigotConfig.maxTntTicksPerTick > 0 && ++this.level().spigotConfig.currentPrimedTnt > this.level().spigotConfig.maxTntTicksPerTick) { return; } // Spigot
        this.handlePortal();
        this.applyGravity();
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.applyEffectsFromBlocks();
        // Paper start - Configurable TNT height nerf
        if (this.level().paperConfig().fixes.tntEntityHeightNerf.test(v -> this.getY() > v)) {
            this.discard(EntityRemoveEvent.Cause.OUT_OF_WORLD);
            return;
        }
        // Paper end - Configurable TNT height nerf
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.7, -0.5, 0.7));
        }

        int i = this.getFuse() - 1;
        this.setFuse(i);
        if (i <= 0) {
            // CraftBukkit start - Need to reverse the order of the explosion and the entity death so we have a location for the event
            //this.discard();
            if (!this.level().isClientSide) {
                this.explode();
            }
            this.discard(EntityRemoveEvent.Cause.EXPLODE); // CraftBukkit - add Bukkit remove cause
            // CraftBukkit end
        } else {
            this.updateInWaterStateAndDoFluidPushing();
            if (this.level().isClientSide) {
                this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 0.0, 0.0, 0.0);
            }
        }
        // Paper start - Option to prevent TNT from moving in water
        if (!this.isRemoved() && this.wasTouchingWater && this.level().paperConfig().fixes.preventTntFromMovingInWater) {
            /*
             * Author: Jedediah Smith <jedediah@silencegreys.com>
             */
            // Send position and velocity updates to nearby players on every tick while the TNT is in water.
            // This does pretty well at keeping their clients in sync with the server.
            net.minecraft.server.level.ChunkMap.TrackedEntity ete = ((net.minecraft.server.level.ServerLevel) this.level()).getChunkSource().chunkMap.entityMap.get(this.getId());
            if (ete != null) {
                net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket velocityPacket = new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(this);
                net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket positionPacket = net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket.teleport(this.getId(), net.minecraft.world.entity.PositionMoveRotation.of(this), java.util.Set.of(), this.onGround);

                ete.seenBy.stream()
                    .filter(viewer -> (viewer.getPlayer().getX() - this.getX()) * (viewer.getPlayer().getY() - this.getY()) * (viewer.getPlayer().getZ() - this.getZ()) < 16 * 16)
                    .forEach(viewer -> {
                        viewer.send(velocityPacket);
                        viewer.send(positionPacket);
                    });
            }
        }
        // Paper end - Option to prevent TNT from moving in water
    }

    private void explode() {
        // CraftBukkit start
        ExplosionPrimeEvent event = CraftEventFactory.callExplosionPrimeEvent((org.bukkit.entity.Explosive) this.getBukkitEntity());
        if (event.isCancelled()) {
            return;
        }
        // CraftBukkit end
        this.level()
            .explode(
                this,
                Explosion.getDefaultDamageSource(this.level(), this),
                this.usedPortal ? USED_PORTAL_DAMAGE_CALCULATOR : null,
                this.getX(),
                this.getY(0.0625),
                this.getZ(),
                event.getRadius(), // CraftBukkit
                event.getFire(), // CraftBukkit
                Level.ExplosionInteraction.TNT
            );
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putShort("fuse", (short)this.getFuse());
        compound.put("block_state", NbtUtils.writeBlockState(this.getBlockState()));
        if (this.explosionPower != 4.0F) {
            compound.putFloat("explosion_power", this.explosionPower);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        this.setFuse(compound.getShort("fuse"));
        if (compound.contains("block_state", 10)) {
            this.setBlockState(NbtUtils.readBlockState(this.level().holderLookup(Registries.BLOCK), compound.getCompound("block_state")));
        }

        if (compound.contains("explosion_power", 99)) {
            this.explosionPower = Mth.clamp(compound.getFloat("explosion_power"), 0.0F, 128.0F);
        }
    }

    @Nullable
    @Override
    public LivingEntity getOwner() {
        return this.owner;
    }

    @Override
    public void restoreFrom(Entity entity) {
        super.restoreFrom(entity);
        if (entity instanceof PrimedTnt primedTnt) {
            this.owner = primedTnt.owner;
        }
    }

    public void setFuse(int life) {
        this.entityData.set(DATA_FUSE_ID, life);
    }

    public int getFuse() {
        return this.entityData.get(DATA_FUSE_ID);
    }

    public void setBlockState(BlockState blockState) {
        this.entityData.set(DATA_BLOCK_STATE_ID, blockState);
    }

    public BlockState getBlockState() {
        return this.entityData.get(DATA_BLOCK_STATE_ID);
    }

    private void setUsedPortal(boolean usedPortal) {
        this.usedPortal = usedPortal;
    }

    @Nullable
    @Override
    public Entity teleport(TeleportTransition teleportTransition) {
        Entity entity = super.teleport(teleportTransition);
        if (entity instanceof PrimedTnt primedTnt) {
            primedTnt.setUsedPortal(true);
        }

        return entity;
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }

    // Paper start - Option to prevent TNT from moving in water
    @Override
    public boolean isPushedByFluid() {
        return !this.level().paperConfig().fixes.preventTntFromMovingInWater && super.isPushedByFluid();
    }
    // Paper end - Option to prevent TNT from moving in water

    // Purpur start - Shears can defuse TNT
    @Override
    public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        Level world = this.level();

        if (world instanceof ServerLevel serverWorld && level().purpurConfig.shearsCanDefuseTnt) {
            final net.minecraft.world.item.ItemStack inHand = player.getItemInHand(hand);

            if (!inHand.is(net.minecraft.world.item.Items.SHEARS) || !player.getBukkitEntity().hasPermission("purpur.tnt.defuse") ||
                    serverWorld.random.nextFloat() > serverWorld.purpurConfig.shearsCanDefuseTntChance) return net.minecraft.world.InteractionResult.PASS;

            net.minecraft.world.entity.item.ItemEntity tntItem = new net.minecraft.world.entity.item.ItemEntity(serverWorld, getX(), getY(), getZ(),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TNT));
            tntItem.setPickUpDelay(10);

            inHand.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            serverWorld.addFreshEntity(tntItem, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);

            this.playSound(net.minecraft.sounds.SoundEvents.SHEEP_SHEAR);

            this.kill(serverWorld);
            return net.minecraft.world.InteractionResult.SUCCESS;
        }

        return super.interact(player, hand);
    }
    // Purpur end - Shears can defuse TNT
}
