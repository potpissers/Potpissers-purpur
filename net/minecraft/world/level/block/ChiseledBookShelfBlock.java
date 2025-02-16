package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class ChiseledBookShelfBlock extends BaseEntityBlock {
    public static final MapCodec<ChiseledBookShelfBlock> CODEC = simpleCodec(ChiseledBookShelfBlock::new);
    private static final int MAX_BOOKS_IN_STORAGE = 6;
    public static final int BOOKS_PER_ROW = 3;
    public static final List<BooleanProperty> SLOT_OCCUPIED_PROPERTIES = List.of(
        BlockStateProperties.CHISELED_BOOKSHELF_SLOT_0_OCCUPIED,
        BlockStateProperties.CHISELED_BOOKSHELF_SLOT_1_OCCUPIED,
        BlockStateProperties.CHISELED_BOOKSHELF_SLOT_2_OCCUPIED,
        BlockStateProperties.CHISELED_BOOKSHELF_SLOT_3_OCCUPIED,
        BlockStateProperties.CHISELED_BOOKSHELF_SLOT_4_OCCUPIED,
        BlockStateProperties.CHISELED_BOOKSHELF_SLOT_5_OCCUPIED
    );

    @Override
    public MapCodec<ChiseledBookShelfBlock> codec() {
        return CODEC;
    }

    public ChiseledBookShelfBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState blockState = this.stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);

        for (BooleanProperty booleanProperty : SLOT_OCCUPIED_PROPERTIES) {
            blockState = blockState.setValue(booleanProperty, Boolean.valueOf(false));
        }

        this.registerDefaultState(blockState);
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity chiseledBookShelfBlockEntity) {
            if (!stack.is(ItemTags.BOOKSHELF_BOOKS)) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            } else {
                OptionalInt hitSlot = this.getHitSlot(hitResult, state);
                if (hitSlot.isEmpty()) {
                    return InteractionResult.PASS;
                } else if (state.getValue(SLOT_OCCUPIED_PROPERTIES.get(hitSlot.getAsInt()))) {
                    return InteractionResult.TRY_WITH_EMPTY_HAND;
                } else {
                    addBook(level, pos, player, chiseledBookShelfBlockEntity, stack, hitSlot.getAsInt());
                    return InteractionResult.SUCCESS;
                }
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity chiseledBookShelfBlockEntity) {
            OptionalInt hitSlot = this.getHitSlot(hitResult, state);
            if (hitSlot.isEmpty()) {
                return InteractionResult.PASS;
            } else if (!state.getValue(SLOT_OCCUPIED_PROPERTIES.get(hitSlot.getAsInt()))) {
                return InteractionResult.CONSUME;
            } else {
                removeBook(level, pos, player, chiseledBookShelfBlockEntity, hitSlot.getAsInt());
                return InteractionResult.SUCCESS;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    private OptionalInt getHitSlot(BlockHitResult hitReselt, BlockState state) {
        return getRelativeHitCoordinatesForBlockFace(hitReselt, state.getValue(HorizontalDirectionalBlock.FACING)).map(vec2 -> {
            int i = vec2.y >= 0.5F ? 0 : 1;
            int section = getSection(vec2.x);
            return OptionalInt.of(section + i * 3);
        }).orElseGet(OptionalInt::empty);
    }

    private static Optional<Vec2> getRelativeHitCoordinatesForBlockFace(BlockHitResult hitResult, Direction face) {
        Direction direction = hitResult.getDirection();
        if (face != direction) {
            return Optional.empty();
        } else {
            BlockPos blockPos = hitResult.getBlockPos().relative(direction);
            Vec3 vec3 = hitResult.getLocation().subtract(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            double x = vec3.x();
            double y = vec3.y();
            double z = vec3.z();

            return switch (direction) {
                case NORTH -> Optional.of(new Vec2((float)(1.0 - x), (float)y));
                case SOUTH -> Optional.of(new Vec2((float)x, (float)y));
                case WEST -> Optional.of(new Vec2((float)z, (float)y));
                case EAST -> Optional.of(new Vec2((float)(1.0 - z), (float)y));
                case DOWN, UP -> Optional.empty();
            };
        }
    }

    private static int getSection(float x) {
        float f = 0.0625F;
        float f1 = 0.375F;
        if (x < 0.375F) {
            return 0;
        } else {
            float f2 = 0.6875F;
            return x < 0.6875F ? 1 : 2;
        }
    }

    private static void addBook(Level level, BlockPos pos, Player player, ChiseledBookShelfBlockEntity blockEntity, ItemStack bookStack, int slot) {
        if (!level.isClientSide) {
            player.awardStat(Stats.ITEM_USED.get(bookStack.getItem()));
            SoundEvent soundEvent = bookStack.is(Items.ENCHANTED_BOOK)
                ? SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED
                : SoundEvents.CHISELED_BOOKSHELF_INSERT;
            blockEntity.setItem(slot, bookStack.consumeAndReturn(1, player));
            level.playSound(null, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private static void removeBook(Level level, BlockPos pos, Player player, ChiseledBookShelfBlockEntity blockEntity, int slot) {
        if (!level.isClientSide) {
            ItemStack itemStack = blockEntity.removeItem(slot, 1);
            SoundEvent soundEvent = itemStack.is(Items.ENCHANTED_BOOK)
                ? SoundEvents.CHISELED_BOOKSHELF_PICKUP_ENCHANTED
                : SoundEvents.CHISELED_BOOKSHELF_PICKUP;
            level.playSound(null, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!player.getInventory().add(itemStack)) {
                player.drop(itemStack, false);
            }

            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChiseledBookShelfBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING);
        SLOT_OCCUPIED_PROPERTIES.forEach(property -> builder.add(property));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            boolean flag;
            if (level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity chiseledBookShelfBlockEntity && !chiseledBookShelfBlockEntity.isEmpty()) {
                for (int i = 0; i < 6; i++) {
                    ItemStack item = chiseledBookShelfBlockEntity.getItem(i);
                    if (!item.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), item);
                    }
                }

                chiseledBookShelfBlockEntity.clearContent();
                flag = true;
            } else {
                flag = false;
            }

            super.onRemove(state, level, pos, newState, movedByPiston);
            if (flag) {
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return 0;
        } else {
            return level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity chiseledBookShelfBlockEntity
                ? chiseledBookShelfBlockEntity.getLastInteractedSlot() + 1
                : 0;
        }
    }
}
