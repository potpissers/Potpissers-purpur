package net.minecraft.world.entity.decoration;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class LeashFenceKnotEntity extends BlockAttachedEntity {
    public static final double OFFSET_Y = 0.375;

    public LeashFenceKnotEntity(EntityType<? extends LeashFenceKnotEntity> entityType, Level level) {
        super(entityType, level);
    }

    public LeashFenceKnotEntity(Level level, BlockPos pos) {
        super(EntityType.LEASH_KNOT, level, pos);
        this.setPos(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void recalculateBoundingBox() {
        this.setPosRaw(this.pos.getX() + 0.5, this.pos.getY() + 0.375, this.pos.getZ() + 0.5);
        double d = this.getType().getWidth() / 2.0;
        double d1 = this.getType().getHeight();
        this.setBoundingBox(new AABB(this.getX() - d, this.getY(), this.getZ() - d, this.getX() + d, this.getY() + d1, this.getZ() + d));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 1024.0;
    }

    @Override
    public void dropItem(ServerLevel level, @Nullable Entity entity) {
        this.playSound(SoundEvents.LEASH_KNOT_BREAK, 1.0F, 1.0F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            boolean flag = false;
            List<Leashable> list = LeadItem.leashableInArea(this.level(), this.getPos(), leashable2 -> {
                Entity leashHolder = leashable2.getLeashHolder();
                return leashHolder == player || leashHolder == this;
            });

            for (Leashable leashable : list) {
                if (leashable.getLeashHolder() == player) {
                    // CraftBukkit start
                    if (leashable instanceof Entity leashed) {
                        if (org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerLeashEntityEvent(leashed, this, player, hand).isCancelled()) {
                            ((net.minecraft.server.level.ServerPlayer) player).connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket(leashed, leashable.getLeashHolder()));
                            flag = true; // Also set true when the event is cancelled otherwise it tries to unleash the entities
                            continue;
                        }
                    }
                    // CraftBukkit end
                    leashable.setLeashedTo(this, true);
                    flag = true;
                }
            }

            boolean flag1 = false;
            if (!flag) {
                // CraftBukkit start - Move below
                // this.discard();
                boolean die = true;
                // CraftBukkit end
                if (true || player.getAbilities().instabuild) { // CraftBukkit - Process for non-creative as well
                    for (Leashable leashable1 : list) {
                        if (leashable1.isLeashed() && leashable1.getLeashHolder() == this) {
                            // CraftBukkit start
                            boolean dropLeash = !player.hasInfiniteMaterials();
                            if (leashable1 instanceof Entity leashed) {
                                // Paper start - Expand EntityUnleashEvent
                                org.bukkit.event.player.PlayerUnleashEntityEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerUnleashEntityEvent(leashed, player, hand, dropLeash);
                                dropLeash = event.isDropLeash();
                                if (event.isCancelled()) {
                                    // Paper end - Expand EntityUnleashEvent
                                    die = false;
                                    continue;
                                }
                            }
                            if (!dropLeash) { // Paper - Expand EntityUnleashEvent
                                leashable1.removeLeash();
                            } else {
                                leashable1.dropLeash();
                            }
                            // CraftBukkit end
                            flag1 = true;
                        }
                    }
                    // CraftBukkit start
                    if (die) {
                        this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DROP); // CraftBukkit - add Bukkit remove cause
                    }
                    // CraftBukkit end
                }
            }

            if (flag || flag1) {
                this.gameEvent(GameEvent.BLOCK_ATTACH, player);
            }

            return InteractionResult.SUCCESS;
        }
    }

    @Override
    public boolean survives() {
        return this.level().getBlockState(this.pos).is(BlockTags.FENCES);
    }

    public static LeashFenceKnotEntity getOrCreateKnot(Level level, BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        for (LeashFenceKnotEntity leashFenceKnotEntity : level.getEntitiesOfClass(
            LeashFenceKnotEntity.class, new AABB(x - 1.0, y - 1.0, z - 1.0, x + 1.0, y + 1.0, z + 1.0)
        )) {
            if (leashFenceKnotEntity.getPos().equals(pos)) {
                return leashFenceKnotEntity;
            }
        }

        LeashFenceKnotEntity leashFenceKnotEntity1 = new LeashFenceKnotEntity(level, pos);
        level.addFreshEntity(leashFenceKnotEntity1);
        return leashFenceKnotEntity1;
    }

    public void playPlacementSound() {
        this.playSound(SoundEvents.LEASH_KNOT_PLACE, 1.0F, 1.0F);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, 0, this.getPos());
    }

    @Override
    public Vec3 getRopeHoldPosition(float partialTicks) {
        return this.getPosition(partialTicks).add(0.0, 0.2, 0.0);
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.LEAD);
    }
}
