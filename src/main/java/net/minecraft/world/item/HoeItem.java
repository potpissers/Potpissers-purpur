package net.minecraft.world.item;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class HoeItem extends DiggerItem {
    protected static final Map<Block, Pair<Predicate<UseOnContext>, Consumer<UseOnContext>>> TILLABLES = Maps.newHashMap(
        ImmutableMap.of(
            Blocks.GRASS_BLOCK,
            Pair.of(HoeItem::onlyIfAirAbove, changeIntoState(Blocks.FARMLAND.defaultBlockState())),
            Blocks.DIRT_PATH,
            Pair.of(HoeItem::onlyIfAirAbove, changeIntoState(Blocks.FARMLAND.defaultBlockState())),
            Blocks.DIRT,
            Pair.of(HoeItem::onlyIfAirAbove, changeIntoState(Blocks.FARMLAND.defaultBlockState())),
            Blocks.COARSE_DIRT,
            Pair.of(HoeItem::onlyIfAirAbove, changeIntoState(Blocks.DIRT.defaultBlockState())),
            Blocks.ROOTED_DIRT,
            Pair.of(useOnContext -> true, changeIntoStateAndDropItem(Blocks.DIRT.defaultBlockState(), Items.HANGING_ROOTS))
        )
    );

    public HoeItem(Tier material, Item.Properties settings) {
        super(material, BlockTags.MINEABLE_WITH_HOE, settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos blockPos = context.getClickedPos();
        // Purpur start
        Block clickedBlock = level.getBlockState(blockPos).getBlock();
        var tillable = level.purpurConfig.hoeTillables.get(clickedBlock);
        if (tillable == null) { return InteractionResult.PASS; } else {
            Predicate<UseOnContext> predicate = tillable.condition().predicate();
            Consumer<UseOnContext> consumer = (ctx) -> {
                level.setBlock(blockPos, tillable.into().defaultBlockState(), 11);
                tillable.drops().forEach((drop, chance) -> {
                    if (level.random.nextDouble() < chance) {
                        Block.popResourceFromFace(level, blockPos, ctx.getClickedFace(), new ItemStack(drop));
                    }
                });
            };
            // Purpur end
            if (predicate.test(context)) {
                Player player = context.getPlayer();
                if (!TILLABLES.containsKey(clickedBlock)) level.playSound(null, blockPos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F); // Purpur - force sound
                if (!level.isClientSide) {
                    consumer.accept(context);
                    if (player != null) {
                        context.getItemInHand().hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));
                    }
                }

                return InteractionResult.SUCCESS; // Purpur - force arm swing
            } else {
                return InteractionResult.PASS;
            }
        }
    }

    public static Consumer<UseOnContext> changeIntoState(BlockState result) {
        return context -> {
            context.getLevel().setBlock(context.getClickedPos(), result, 11);
            context.getLevel().gameEvent(GameEvent.BLOCK_CHANGE, context.getClickedPos(), GameEvent.Context.of(context.getPlayer(), result));
        };
    }

    public static Consumer<UseOnContext> changeIntoStateAndDropItem(BlockState result, ItemLike droppedItem) {
        return context -> {
            context.getLevel().setBlock(context.getClickedPos(), result, 11);
            context.getLevel().gameEvent(GameEvent.BLOCK_CHANGE, context.getClickedPos(), GameEvent.Context.of(context.getPlayer(), result));
            Block.popResourceFromFace(context.getLevel(), context.getClickedPos(), context.getClickedFace(), new ItemStack(droppedItem));
        };
    }

    public static boolean onlyIfAirAbove(UseOnContext context) {
        return context.getClickedFace() != Direction.DOWN && context.getLevel().getBlockState(context.getClickedPos().above()).isAir();
    }
}
