package net.minecraft.world.level.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

public class NetherPortalBlock extends Block implements Portal {
    public static final MapCodec<NetherPortalBlock> CODEC = simpleCodec(NetherPortalBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private static final Logger LOGGER = LogUtils.getLogger();
    protected static final int AABB_OFFSET = 2;
    protected static final VoxelShape X_AXIS_AABB = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    protected static final VoxelShape Z_AXIS_AABB = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    @Override
    public MapCodec<NetherPortalBlock> codec() {
        return CODEC;
    }

    public NetherPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        switch ((Direction.Axis)state.getValue(AXIS)) {
            case Z:
                return Z_AXIS_AABB;
            case X:
            default:
                return X_AXIS_AABB;
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.dimensionType().natural()
            && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
            && random.nextInt(2000) < level.getDifficulty().getId()) {
            while (level.getBlockState(pos).is(this)) {
                pos = pos.below();
            }

            if (level.getBlockState(pos).isValidSpawn(level, pos, EntityType.ZOMBIFIED_PIGLIN)) {
                Entity entity = EntityType.ZOMBIFIED_PIGLIN.spawn(level, pos.above(), EntitySpawnReason.STRUCTURE);
                if (entity != null) {
                    entity.setPortalCooldown();
                    Entity vehicle = entity.getVehicle();
                    if (vehicle != null) {
                        vehicle.setPortalCooldown();
                    }
                }
            }
        }
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random
    ) {
        Direction.Axis axis = direction.getAxis();
        Direction.Axis axis1 = state.getValue(AXIS);
        boolean flag = axis1 != axis && axis.isHorizontal();
        return !flag && !neighborState.is(this) && !PortalShape.findAnyShape(level, pos, axis1).isComplete()
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity.canUsePortal(false)) {
            entity.setAsInsidePortal(this, pos);
        }
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return entity instanceof Player player
            ? Math.max(
                0,
                level.getGameRules()
                    .getInt(
                        player.getAbilities().invulnerable
                            ? GameRules.RULE_PLAYERS_NETHER_PORTAL_CREATIVE_DELAY
                            : GameRules.RULE_PLAYERS_NETHER_PORTAL_DEFAULT_DELAY
                    )
            )
            : 0;
    }

    @Nullable
    @Override
    public TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        ResourceKey<Level> resourceKey = level.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER;
        ServerLevel level1 = level.getServer().getLevel(resourceKey);
        if (level1 == null) {
            return null;
        } else {
            boolean flag = level1.dimension() == Level.NETHER;
            WorldBorder worldBorder = level1.getWorldBorder();
            double teleportationScale = DimensionType.getTeleportationScale(level.dimensionType(), level1.dimensionType());
            BlockPos blockPos = worldBorder.clampToBounds(entity.getX() * teleportationScale, entity.getY(), entity.getZ() * teleportationScale);
            return this.getExitPortal(level1, entity, pos, blockPos, flag, worldBorder);
        }
    }

    @Nullable
    private TeleportTransition getExitPortal(ServerLevel level, Entity entity, BlockPos pos, BlockPos exitPos, boolean isNether, WorldBorder worldBorder) {
        Optional<BlockPos> optional = level.getPortalForcer().findClosestPortalPosition(exitPos, isNether, worldBorder);
        BlockUtil.FoundRectangle largestRectangleAround;
        TeleportTransition.PostTeleportTransition postTeleportTransition;
        if (optional.isPresent()) {
            BlockPos blockPos = optional.get();
            BlockState blockState = level.getBlockState(blockPos);
            largestRectangleAround = BlockUtil.getLargestRectangleAround(
                blockPos,
                blockState.getValue(BlockStateProperties.HORIZONTAL_AXIS),
                21,
                Direction.Axis.Y,
                21,
                blockPos1 -> level.getBlockState(blockPos1) == blockState
            );
            postTeleportTransition = TeleportTransition.PLAY_PORTAL_SOUND.then(entity1 -> entity1.placePortalTicket(blockPos));
        } else {
            Direction.Axis axis = entity.level().getBlockState(pos).getOptionalValue(AXIS).orElse(Direction.Axis.X);
            Optional<BlockUtil.FoundRectangle> optional1 = level.getPortalForcer().createPortal(exitPos, axis);
            if (optional1.isEmpty()) {
                LOGGER.error("Unable to create a portal, likely target out of worldborder");
                return null;
            }

            largestRectangleAround = optional1.get();
            postTeleportTransition = TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET);
        }

        return getDimensionTransitionFromExit(entity, pos, largestRectangleAround, level, postTeleportTransition);
    }

    private static TeleportTransition getDimensionTransitionFromExit(
        Entity entity, BlockPos pos, BlockUtil.FoundRectangle rectangle, ServerLevel level, TeleportTransition.PostTeleportTransition postTeleportTransition
    ) {
        BlockState blockState = entity.level().getBlockState(pos);
        Direction.Axis axis;
        Vec3 relativePortalPosition;
        if (blockState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            axis = blockState.getValue(BlockStateProperties.HORIZONTAL_AXIS);
            BlockUtil.FoundRectangle largestRectangleAround = BlockUtil.getLargestRectangleAround(
                pos, axis, 21, Direction.Axis.Y, 21, blockPos -> entity.level().getBlockState(blockPos) == blockState
            );
            relativePortalPosition = entity.getRelativePortalPosition(axis, largestRectangleAround);
        } else {
            axis = Direction.Axis.X;
            relativePortalPosition = new Vec3(0.5, 0.0, 0.0);
        }

        return createDimensionTransition(level, rectangle, axis, relativePortalPosition, entity, postTeleportTransition);
    }

    private static TeleportTransition createDimensionTransition(
        ServerLevel level,
        BlockUtil.FoundRectangle rectangle,
        Direction.Axis axis,
        Vec3 offset,
        Entity entity,
        TeleportTransition.PostTeleportTransition postTeleportTransition
    ) {
        BlockPos blockPos = rectangle.minCorner;
        BlockState blockState = level.getBlockState(blockPos);
        Direction.Axis axis1 = blockState.getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS).orElse(Direction.Axis.X);
        double d = rectangle.axis1Size;
        double d1 = rectangle.axis2Size;
        EntityDimensions dimensions = entity.getDimensions(entity.getPose());
        int i = axis == axis1 ? 0 : 90;
        double d2 = dimensions.width() / 2.0 + (d - dimensions.width()) * offset.x();
        double d3 = (d1 - dimensions.height()) * offset.y();
        double d4 = 0.5 + offset.z();
        boolean flag = axis1 == Direction.Axis.X;
        Vec3 vec3 = new Vec3(blockPos.getX() + (flag ? d2 : d4), blockPos.getY() + d3, blockPos.getZ() + (flag ? d4 : d2));
        Vec3 vec31 = PortalShape.findCollisionFreePosition(vec3, level, entity, dimensions);
        return new TeleportTransition(level, vec31, Vec3.ZERO, i, 0.0F, Relative.union(Relative.DELTA, Relative.ROTATION), postTeleportTransition);
    }

    @Override
    public Portal.Transition getLocalTransition() {
        return Portal.Transition.CONFUSION;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(100) == 0) {
            level.playLocalSound(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                SoundEvents.PORTAL_AMBIENT,
                SoundSource.BLOCKS,
                0.5F,
                random.nextFloat() * 0.4F + 0.8F,
                false
            );
        }

        for (int i = 0; i < 4; i++) {
            double d = pos.getX() + random.nextDouble();
            double d1 = pos.getY() + random.nextDouble();
            double d2 = pos.getZ() + random.nextDouble();
            double d3 = (random.nextFloat() - 0.5) * 0.5;
            double d4 = (random.nextFloat() - 0.5) * 0.5;
            double d5 = (random.nextFloat() - 0.5) * 0.5;
            int i1 = random.nextInt(2) * 2 - 1;
            if (!level.getBlockState(pos.west()).is(this) && !level.getBlockState(pos.east()).is(this)) {
                d = pos.getX() + 0.5 + 0.25 * i1;
                d3 = random.nextFloat() * 2.0F * i1;
            } else {
                d2 = pos.getZ() + 0.5 + 0.25 * i1;
                d5 = random.nextFloat() * 2.0F * i1;
            }

            level.addParticle(ParticleTypes.PORTAL, d, d1, d2, d3, d4, d5);
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        switch (rot) {
            case COUNTERCLOCKWISE_90:
            case CLOCKWISE_90:
                switch ((Direction.Axis)state.getValue(AXIS)) {
                    case Z:
                        return state.setValue(AXIS, Direction.Axis.X);
                    case X:
                        return state.setValue(AXIS, Direction.Axis.Z);
                    default:
                        return state;
                }
            default:
                return state;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }
}
