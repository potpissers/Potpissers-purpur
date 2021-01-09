package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class EndPortalBlock extends BaseEntityBlock implements Portal {
    public static final MapCodec<EndPortalBlock> CODEC = simpleCodec(EndPortalBlock::new);
    protected static final VoxelShape SHAPE = Block.box(0.0, 6.0, 0.0, 16.0, 12.0, 16.0);

    @Override
    public MapCodec<EndPortalBlock> codec() {
        return CODEC;
    }

    protected EndPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TheEndPortalBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getEntityInsideCollisionShape(BlockState state, Level level, BlockPos pos) {
        return state.getShape(level, pos);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (entity.canUsePortal(false)) {
            // Purpur start - Add EntityTeleportHinderedEvent
            if (level.purpurConfig.imposeTeleportRestrictionsOnEndPortals && (entity.isVehicle() || entity.isPassenger())) {
                if (!new org.purpurmc.purpur.event.entity.EntityTeleportHinderedEvent(entity.getBukkitEntity(), entity.isPassenger() ? org.purpurmc.purpur.event.entity.EntityTeleportHinderedEvent.Reason.IS_PASSENGER : org.purpurmc.purpur.event.entity.EntityTeleportHinderedEvent.Reason.IS_VEHICLE, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.END_PORTAL).callEvent()) {
                    return;
                }
            }
            // Purpur end - Add EntityTeleportHinderedEvent
            // CraftBukkit start - Entity in portal
            org.bukkit.event.entity.EntityPortalEnterEvent event = new org.bukkit.event.entity.EntityPortalEnterEvent(entity.getBukkitEntity(), new org.bukkit.Location(level.getWorld(), pos.getX(), pos.getY(), pos.getZ()), org.bukkit.PortalType.ENDER); // Paper - add portal type
            level.getCraftServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) return; // Paper - make cancellable
            // CraftBukkit end
            if (!level.isClientSide && level.dimension() == Level.END && entity instanceof ServerPlayer serverPlayer && !serverPlayer.seenCredits) {
                if (level.paperConfig().misc.disableEndCredits) {serverPlayer.seenCredits = true; return;} // Paper - Option to disable end credits
                serverPlayer.showEndCredits();
            } else {
                entity.setAsInsidePortal(this, pos);
            }
        }
    }

    @Override
    public TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        ResourceKey<Level> resourceKey = level.getTypeKey() == net.minecraft.world.level.dimension.LevelStem.END ? Level.OVERWORLD : Level.END; // CraftBukkit - SPIGOT-6152: send back to main overworld in custom ends
        ServerLevel level1 = level.getServer().getLevel(resourceKey);
        if (level1 == null) {
            return null;
        } else {
            boolean flag = resourceKey == Level.END;
            BlockPos blockPos = flag ? ServerLevel.END_SPAWN_POINT : level1.getSharedSpawnPos();
            Vec3 bottomCenter = blockPos.getBottomCenter();
            float f;
            Set<Relative> set;
            if (flag) {
                EndPlatformFeature.createEndPlatform(level1, BlockPos.containing(bottomCenter).below(), true, entity); // CraftBukkit
                f = Direction.WEST.toYRot();
                set = Relative.union(Relative.DELTA, Set.of(Relative.X_ROT));
                if (entity instanceof ServerPlayer) {
                    bottomCenter = bottomCenter.subtract(0.0, 1.0, 0.0);
                }
            } else {
                f = 0.0F;
                set = Relative.union(Relative.DELTA, Relative.ROTATION);
                if (entity instanceof ServerPlayer serverPlayer) {
                    return serverPlayer.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING, org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.END_PORTAL); // CraftBukkit
                }

                bottomCenter = entity.adjustSpawnLocation(level1, blockPos).getBottomCenter();
            }

            // CraftBukkit start
            org.bukkit.craftbukkit.event.CraftPortalEvent event = entity.callPortalEvent(entity, org.bukkit.craftbukkit.util.CraftLocation.toBukkit(bottomCenter, level1.getWorld(), f, entity.getXRot()), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.END_PORTAL, 0, 0);
            if (event == null) {
                return null;
            }
            org.bukkit.Location to = event.getTo();

            return new TeleportTransition(((org.bukkit.craftbukkit.CraftWorld) to.getWorld()).getHandle(), org.bukkit.craftbukkit.util.CraftLocation.toVec3D(to), entity.getDeltaMovement(), to.getYaw(), to.getPitch(), set, TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.END_PORTAL);
            // CraftBukkit end
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double d = pos.getX() + random.nextDouble();
        double d1 = pos.getY() + 0.8;
        double d2 = pos.getZ() + random.nextDouble();
        level.addParticle(ParticleTypes.SMOKE, d, d1, d2, 0.0, 0.0, 0.0);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
