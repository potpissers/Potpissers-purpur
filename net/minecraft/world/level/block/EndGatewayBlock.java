package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

public class EndGatewayBlock extends BaseEntityBlock implements Portal {
    public static final MapCodec<EndGatewayBlock> CODEC = simpleCodec(EndGatewayBlock::new);

    @Override
    public MapCodec<EndGatewayBlock> codec() {
        return CODEC;
    }

    protected EndGatewayBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TheEndGatewayBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(
            blockEntityType,
            BlockEntityType.END_GATEWAY,
            level.isClientSide ? TheEndGatewayBlockEntity::beamAnimationTick : TheEndGatewayBlockEntity::portalTick
        );
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof TheEndGatewayBlockEntity) {
            int particleAmount = ((TheEndGatewayBlockEntity)blockEntity).getParticleAmount();

            for (int i = 0; i < particleAmount; i++) {
                double d = pos.getX() + random.nextDouble();
                double d1 = pos.getY() + random.nextDouble();
                double d2 = pos.getZ() + random.nextDouble();
                double d3 = (random.nextDouble() - 0.5) * 0.5;
                double d4 = (random.nextDouble() - 0.5) * 0.5;
                double d5 = (random.nextDouble() - 0.5) * 0.5;
                int i1 = random.nextInt(2) * 2 - 1;
                if (random.nextBoolean()) {
                    d2 = pos.getZ() + 0.5 + 0.25 * i1;
                    d5 = random.nextFloat() * 2.0F * i1;
                } else {
                    d = pos.getX() + 0.5 + 0.25 * i1;
                    d3 = random.nextFloat() * 2.0F * i1;
                }

                level.addParticle(ParticleTypes.PORTAL, d, d1, d2, d3, d4, d5);
            }
        }
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
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (entity.canUsePortal(false)
            && !level.isClientSide
            && level.getBlockEntity(pos) instanceof TheEndGatewayBlockEntity theEndGatewayBlockEntity
            && !theEndGatewayBlockEntity.isCoolingDown()) {
            // Paper start - call EntityPortalEnterEvent
            org.bukkit.event.entity.EntityPortalEnterEvent event = new org.bukkit.event.entity.EntityPortalEnterEvent(entity.getBukkitEntity(), new org.bukkit.Location(level.getWorld(), pos.getX(), pos.getY(), pos.getZ()), org.bukkit.PortalType.END_GATEWAY); // Paper - add portal type
            if (!event.callEvent()) return;
            // Paper end - call EntityPortalEnterEvent
            // Purpur start - Add EntityTeleportHinderedEvent
            if (level.purpurConfig.imposeTeleportRestrictionsOnGateways && (entity.isVehicle() || entity.isPassenger())) {
                if (!new org.purpurmc.purpur.event.entity.EntityTeleportHinderedEvent(entity.getBukkitEntity(), entity.isPassenger() ? org.purpurmc.purpur.event.entity.EntityTeleportHinderedEvent.Reason.IS_PASSENGER : org.purpurmc.purpur.event.entity.EntityTeleportHinderedEvent.Reason.IS_VEHICLE, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.END_GATEWAY).callEvent()) {
                    return;
                }
            }
            // Purpur end - Add EntityTeleportHinderedEvent
            entity.setAsInsidePortal(this, pos);
            TheEndGatewayBlockEntity.triggerCooldown(level, pos, state, theEndGatewayBlockEntity);
        }
    }

    @Nullable
    @Override
    public TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TheEndGatewayBlockEntity theEndGatewayBlockEntity) {
            Vec3 portalPosition = theEndGatewayBlockEntity.getPortalPosition(level, pos);
            if (portalPosition == null) {
                return null;
            } else {
                return entity instanceof ThrownEnderpearl
                    ? new TeleportTransition(level, portalPosition, Vec3.ZERO, 0.0F, 0.0F, Set.of(), TeleportTransition.PLACE_PORTAL_TICKET, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.END_GATEWAY) // CraftBukkit
                    : new TeleportTransition(
                        level, portalPosition, Vec3.ZERO, 0.0F, 0.0F, Relative.union(Relative.DELTA, Relative.ROTATION), TeleportTransition.PLACE_PORTAL_TICKET, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.END_GATEWAY // CraftBukkit
                    );
            }
        } else {
            return null;
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
