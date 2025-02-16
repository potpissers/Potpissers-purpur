package net.minecraft.world.item;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

public class LeadItem extends Item {
    public LeadItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState blockState = level.getBlockState(clickedPos);
        if (blockState.is(BlockTags.FENCES)) {
            Player player = context.getPlayer();
            if (!level.isClientSide && player != null) {
                return bindPlayerMobs(player, level, clickedPos);
            }
        }

        return InteractionResult.PASS;
    }

    public static InteractionResult bindPlayerMobs(Player player, Level level, BlockPos pos) {
        LeashFenceKnotEntity leashFenceKnotEntity = null;
        List<Leashable> list = leashableInArea(level, pos, leashable1 -> leashable1.getLeashHolder() == player);

        for (Leashable leashable : list) {
            if (leashFenceKnotEntity == null) {
                leashFenceKnotEntity = LeashFenceKnotEntity.getOrCreateKnot(level, pos);
                leashFenceKnotEntity.playPlacementSound();
            }

            leashable.setLeashedTo(leashFenceKnotEntity, true);
        }

        if (!list.isEmpty()) {
            level.gameEvent(GameEvent.BLOCK_ATTACH, pos, GameEvent.Context.of(player));
            return InteractionResult.SUCCESS_SERVER;
        } else {
            return InteractionResult.PASS;
        }
    }

    public static List<Leashable> leashableInArea(Level level, BlockPos pos, Predicate<Leashable> predicate) {
        double d = 7.0;
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        AABB aabb = new AABB(x - 7.0, y - 7.0, z - 7.0, x + 7.0, y + 7.0, z + 7.0);
        return level.getEntitiesOfClass(Entity.class, aabb, entity -> entity instanceof Leashable leashable && predicate.test(leashable))
            .stream()
            .map(Leashable.class::cast)
            .toList();
    }
}
