package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AttachedStemBlock extends BushBlock {
    public static final MapCodec<AttachedStemBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                ResourceKey.codec(Registries.BLOCK).fieldOf("fruit").forGetter(attachedStemBlock -> attachedStemBlock.fruit),
                ResourceKey.codec(Registries.BLOCK).fieldOf("stem").forGetter(attachedStemBlock -> attachedStemBlock.stem),
                ResourceKey.codec(Registries.ITEM).fieldOf("seed").forGetter(attachedStemBlock -> attachedStemBlock.seed),
                propertiesCodec()
            )
            .apply(instance, AttachedStemBlock::new)
    );
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    protected static final float AABB_OFFSET = 2.0F;
    private static final Map<Direction, VoxelShape> AABBS = Maps.newEnumMap(
        ImmutableMap.of(
            Direction.SOUTH,
            Block.box(6.0, 0.0, 6.0, 10.0, 10.0, 16.0),
            Direction.WEST,
            Block.box(0.0, 0.0, 6.0, 10.0, 10.0, 10.0),
            Direction.NORTH,
            Block.box(6.0, 0.0, 0.0, 10.0, 10.0, 10.0),
            Direction.EAST,
            Block.box(6.0, 0.0, 6.0, 16.0, 10.0, 10.0)
        )
    );
    private final ResourceKey<Block> fruit;
    private final ResourceKey<Block> stem;
    private final ResourceKey<Item> seed;

    @Override
    public MapCodec<AttachedStemBlock> codec() {
        return CODEC;
    }

    protected AttachedStemBlock(ResourceKey<Block> stem, ResourceKey<Block> fruit, ResourceKey<Item> seed, BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
        this.stem = stem;
        this.fruit = fruit;
        this.seed = seed;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AABBS.get(state.getValue(FACING));
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
        if (!neighborState.is(this.fruit) && direction == state.getValue(FACING)) {
            Optional<Block> optional = level.registryAccess().lookupOrThrow(Registries.BLOCK).getOptional(this.stem);
            if (optional.isPresent()) {
                return optional.get().defaultBlockState().trySetValue(StemBlock.AGE, Integer.valueOf(7));
            }
        }

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.FARMLAND);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(DataFixUtils.orElse(level.registryAccess().lookupOrThrow(Registries.ITEM).getOptional(this.seed), this));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
