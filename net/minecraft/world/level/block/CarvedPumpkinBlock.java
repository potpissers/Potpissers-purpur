package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class CarvedPumpkinBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CarvedPumpkinBlock> CODEC = simpleCodec(CarvedPumpkinBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    @Nullable
    private BlockPattern snowGolemBase;
    @Nullable
    private BlockPattern snowGolemFull;
    @Nullable
    private BlockPattern ironGolemBase;
    @Nullable
    private BlockPattern ironGolemFull;
    private static final Predicate<BlockState> PUMPKINS_PREDICATE = state -> state != null
        && (state.is(Blocks.CARVED_PUMPKIN) || state.is(Blocks.JACK_O_LANTERN));

    @Override
    public MapCodec<? extends CarvedPumpkinBlock> codec() {
        return CODEC;
    }

    protected CarvedPumpkinBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!oldState.is(state.getBlock())) {
            this.trySpawnGolem(level, pos);
        }
    }

    public boolean canSpawnGolem(LevelReader level, BlockPos pos) {
        return this.getOrCreateSnowGolemBase().find(level, pos) != null || this.getOrCreateIronGolemBase().find(level, pos) != null;
    }

    private void trySpawnGolem(Level level, BlockPos pos) {
        BlockPattern.BlockPatternMatch blockPatternMatch = this.getOrCreateSnowGolemFull().find(level, pos);
        if (blockPatternMatch != null) {
            SnowGolem snowGolem = EntityType.SNOW_GOLEM.create(level, EntitySpawnReason.TRIGGERED);
            if (snowGolem != null) {
                spawnGolemInWorld(level, blockPatternMatch, snowGolem, blockPatternMatch.getBlock(0, 2, 0).getPos(), this.placer); // Purpur - Summoner API
            }
        } else {
            BlockPattern.BlockPatternMatch blockPatternMatch1 = this.getOrCreateIronGolemFull().find(level, pos);
            if (blockPatternMatch1 != null) {
                IronGolem ironGolem = EntityType.IRON_GOLEM.create(level, EntitySpawnReason.TRIGGERED);
                if (ironGolem != null) {
                    ironGolem.setPlayerCreated(true);
                    spawnGolemInWorld(level, blockPatternMatch1, ironGolem, blockPatternMatch1.getBlock(1, 2, 0).getPos(), this.placer); // Purpur - Summoner API
                }
            }
        }
    }

    private static void spawnGolemInWorld(Level level, BlockPattern.BlockPatternMatch patternMatch, Entity golem, BlockPos pos) {
        // Purpur start - Summoner API
        spawnGolemInWorld(level, patternMatch, golem, pos, null);
    }
    private static void spawnGolemInWorld(Level level, BlockPattern.BlockPatternMatch patternMatch, Entity golem, BlockPos pos, net.minecraft.world.entity.LivingEntity placer) {
        if (golem instanceof SnowGolem snowGolem) {
            snowGolem.setSummoner(placer == null ? null : placer.getUUID());
        } else if (golem instanceof IronGolem ironGolem) {
            ironGolem.setSummoner(placer == null ? null : placer.getUUID());
        }
        // Purpur end - Summoner API
        // clearPatternBlocks(level, patternMatch); // CraftBukkit - moved down
        golem.moveTo(pos.getX() + 0.5, pos.getY() + 0.05, pos.getZ() + 0.5, 0.0F, 0.0F);
        if (!level.addFreshEntity(golem, (golem.getType() == EntityType.SNOW_GOLEM) ? org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BUILD_SNOWMAN : org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BUILD_IRONGOLEM)) {
            return;
        }
        clearPatternBlocks(level, patternMatch); // CraftBukkit - from above
        // CraftBukkit end

        for (ServerPlayer serverPlayer : level.getEntitiesOfClass(ServerPlayer.class, golem.getBoundingBox().inflate(5.0))) {
            CriteriaTriggers.SUMMONED_ENTITY.trigger(serverPlayer, golem);
        }

        updatePatternBlocks(level, patternMatch);
    }

    public static void clearPatternBlocks(Level level, BlockPattern.BlockPatternMatch patternMatch) {
        for (int i = 0; i < patternMatch.getWidth(); i++) {
            for (int i1 = 0; i1 < patternMatch.getHeight(); i1++) {
                BlockInWorld block = patternMatch.getBlock(i, i1, 0);
                level.setBlock(block.getPos(), Blocks.AIR.defaultBlockState(), 2);
                level.levelEvent(2001, block.getPos(), Block.getId(block.getState()));
            }
        }
    }

    public static void updatePatternBlocks(Level level, BlockPattern.BlockPatternMatch patternMatch) {
        for (int i = 0; i < patternMatch.getWidth(); i++) {
            for (int i1 = 0; i1 < patternMatch.getHeight(); i1++) {
                BlockInWorld block = patternMatch.getBlock(i, i1, 0);
                level.blockUpdated(block.getPos(), Blocks.AIR);
            }
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    private BlockPattern getOrCreateSnowGolemBase() {
        if (this.snowGolemBase == null) {
            this.snowGolemBase = BlockPatternBuilder.start()
                .aisle(" ", "#", "#")
                .where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.SNOW_BLOCK)))
                .build();
        }

        return this.snowGolemBase;
    }

    private BlockPattern getOrCreateSnowGolemFull() {
        if (this.snowGolemFull == null) {
            this.snowGolemFull = BlockPatternBuilder.start()
                .aisle("^", "#", "#")
                .where('^', BlockInWorld.hasState(PUMPKINS_PREDICATE))
                .where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.SNOW_BLOCK)))
                .build();
        }

        return this.snowGolemFull;
    }

    private BlockPattern getOrCreateIronGolemBase() {
        if (this.ironGolemBase == null) {
            this.ironGolemBase = BlockPatternBuilder.start()
                .aisle("~ ~", "###", "~#~")
                .where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.IRON_BLOCK)))
                .where('~', block -> block.getState().isAir())
                .build();
        }

        return this.ironGolemBase;
    }

    private BlockPattern getOrCreateIronGolemFull() {
        if (this.ironGolemFull == null) {
            this.ironGolemFull = BlockPatternBuilder.start()
                .aisle("~^~", "###", "~#~")
                .where('^', BlockInWorld.hasState(PUMPKINS_PREDICATE))
                .where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.IRON_BLOCK)))
                .where('~', block -> block.getState().isAir())
                .build();
        }

        return this.ironGolemFull;
    }
}
