package net.minecraft.world.item;

import com.google.common.collect.ImmutableMap.Builder;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class AxeItem extends DiggerItem {
    protected static final Map<Block, Block> STRIPPABLES = new Builder<Block, Block>()
        .put(Blocks.OAK_WOOD, Blocks.STRIPPED_OAK_WOOD)
        .put(Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG)
        .put(Blocks.DARK_OAK_WOOD, Blocks.STRIPPED_DARK_OAK_WOOD)
        .put(Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG)
        .put(Blocks.ACACIA_WOOD, Blocks.STRIPPED_ACACIA_WOOD)
        .put(Blocks.ACACIA_LOG, Blocks.STRIPPED_ACACIA_LOG)
        .put(Blocks.CHERRY_WOOD, Blocks.STRIPPED_CHERRY_WOOD)
        .put(Blocks.CHERRY_LOG, Blocks.STRIPPED_CHERRY_LOG)
        .put(Blocks.BIRCH_WOOD, Blocks.STRIPPED_BIRCH_WOOD)
        .put(Blocks.BIRCH_LOG, Blocks.STRIPPED_BIRCH_LOG)
        .put(Blocks.JUNGLE_WOOD, Blocks.STRIPPED_JUNGLE_WOOD)
        .put(Blocks.JUNGLE_LOG, Blocks.STRIPPED_JUNGLE_LOG)
        .put(Blocks.SPRUCE_WOOD, Blocks.STRIPPED_SPRUCE_WOOD)
        .put(Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG)
        .put(Blocks.WARPED_STEM, Blocks.STRIPPED_WARPED_STEM)
        .put(Blocks.WARPED_HYPHAE, Blocks.STRIPPED_WARPED_HYPHAE)
        .put(Blocks.CRIMSON_STEM, Blocks.STRIPPED_CRIMSON_STEM)
        .put(Blocks.CRIMSON_HYPHAE, Blocks.STRIPPED_CRIMSON_HYPHAE)
        .put(Blocks.MANGROVE_WOOD, Blocks.STRIPPED_MANGROVE_WOOD)
        .put(Blocks.MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_LOG)
        .put(Blocks.BAMBOO_BLOCK, Blocks.STRIPPED_BAMBOO_BLOCK)
        .build();

    public AxeItem(Tier material, Item.Properties settings) {
        super(material, BlockTags.MINEABLE_WITH_AXE, settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos blockPos = context.getClickedPos();
        Player player = context.getPlayer();
        if (playerHasShieldUseIntent(context)) {
            return InteractionResult.PASS;
        } else {
            Optional<org.purpurmc.purpur.tool.Actionable> optional = this.evaluateActionable(level, blockPos, player, level.getBlockState(blockPos)); // Purpur
            if (optional.isEmpty()) {
                return InteractionResult.PASS;
            } else {
                org.purpurmc.purpur.tool.Actionable actionable = optional.get(); // Purpur
                BlockState state = actionable.into().withPropertiesOf(level.getBlockState(blockPos)); // Purpur
                ItemStack itemStack = context.getItemInHand();
                // Paper start - EntityChangeBlockEvent
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(player, blockPos, state)) { // Purpur
                    return InteractionResult.PASS;
                }
                // Paper end
                if (player instanceof ServerPlayer) {
                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger((ServerPlayer)player, blockPos, itemStack);
                }

                // Purpur start
                level.setBlock(blockPos, state, 11);
                actionable.drops().forEach((drop, chance) -> {
                    if (level.random.nextDouble() < chance) {
                        Block.popResourceFromFace(level, blockPos, context.getClickedFace(), new ItemStack(drop));
                    }
                });
                level.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(player, state));
                // Purpur end
                if (player != null) {
                    itemStack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));
                }

                return InteractionResult.SUCCESS; // Purpur - force arm swing
            }
        }
    }

    private static boolean playerHasShieldUseIntent(UseOnContext context) {
        Player player = context.getPlayer();
        return context.getHand().equals(InteractionHand.MAIN_HAND) && player.getOffhandItem().is(Items.SHIELD) && !player.isSecondaryUseActive();
    }

    private Optional<org.purpurmc.purpur.tool.Actionable> evaluateActionable(Level world, BlockPos pos, @Nullable Player player, BlockState state) { // Purpur
        Optional<org.purpurmc.purpur.tool.Actionable> optional = Optional.ofNullable(world.purpurConfig.axeStrippables.get(state.getBlock())); // Purpur
        if (optional.isPresent()) {
            world.playSound(STRIPPABLES.containsKey(state.getBlock()) ? player : null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F); // Purpur - force sound
            return optional;
        } else {
            Optional<org.purpurmc.purpur.tool.Actionable> optional2 = Optional.ofNullable(world.purpurConfig.axeWeatherables.get(state.getBlock())); // Purpur
            if (optional2.isPresent()) {
                world.playSound(WeatheringCopper.getPrevious(state).isPresent() ? player : null, pos, SoundEvents.AXE_SCRAPE, SoundSource.BLOCKS, 1.0F, 1.0F); // Purpur - force sound
                world.levelEvent(player, 3005, pos, 0);
                return optional2;
            } else {
                // Purpur start
                Optional<org.purpurmc.purpur.tool.Actionable> optional3 = Optional.ofNullable(world.purpurConfig.axeWaxables.get(state.getBlock()));
                //    .map(block -> block.withPropertiesOf(state));
                // Purpur end
                if (optional3.isPresent()) {
                    world.playSound(HoneycombItem.WAX_OFF_BY_BLOCK.get().containsKey(state.getBlock()) ? player : null, pos, SoundEvents.AXE_WAX_OFF, SoundSource.BLOCKS, 1.0F, 1.0F); // Purpur - force sound
                    world.levelEvent(player, 3004, pos, 0);
                    return optional3;
                } else {
                    return Optional.empty();
                }
            }
        }
    }

    private Optional<BlockState> getStripped(BlockState state) {
        return Optional.ofNullable(STRIPPABLES.get(state.getBlock()))
            .map(block -> block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS)));
    }
}
