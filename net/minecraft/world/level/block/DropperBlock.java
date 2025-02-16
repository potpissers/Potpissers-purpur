package net.minecraft.world.level.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public class DropperBlock extends DispenserBlock {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<DropperBlock> CODEC = simpleCodec(DropperBlock::new);
    private static final DispenseItemBehavior DISPENSE_BEHAVIOUR = new DefaultDispenseItemBehavior();

    @Override
    public MapCodec<DropperBlock> codec() {
        return CODEC;
    }

    public DropperBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected DispenseItemBehavior getDispenseMethod(Level level, ItemStack item) {
        return DISPENSE_BEHAVIOUR;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DropperBlockEntity(pos, state);
    }

    @Override
    protected void dispenseFrom(ServerLevel level, BlockState state, BlockPos pos) {
        DispenserBlockEntity dispenserBlockEntity = level.getBlockEntity(pos, BlockEntityType.DROPPER).orElse(null);
        if (dispenserBlockEntity == null) {
            LOGGER.warn("Ignoring dispensing attempt for Dropper without matching block entity at {}", pos);
        } else {
            BlockSource blockSource = new BlockSource(level, pos, state, dispenserBlockEntity);
            int randomSlot = dispenserBlockEntity.getRandomSlot(level.random);
            if (randomSlot < 0) {
                level.levelEvent(1001, pos, 0);
            } else {
                ItemStack item = dispenserBlockEntity.getItem(randomSlot);
                if (!item.isEmpty()) {
                    Direction direction = level.getBlockState(pos).getValue(FACING);
                    Container containerAt = HopperBlockEntity.getContainerAt(level, pos.relative(direction));
                    ItemStack itemStack;
                    if (containerAt == null) {
                        itemStack = DISPENSE_BEHAVIOUR.dispense(blockSource, item);
                    } else {
                        itemStack = HopperBlockEntity.addItem(dispenserBlockEntity, containerAt, item.copyWithCount(1), direction.getOpposite());
                        if (itemStack.isEmpty()) {
                            itemStack = item.copy();
                            itemStack.shrink(1);
                        } else {
                            itemStack = item.copy();
                        }
                    }

                    dispenserBlockEntity.setItem(randomSlot, itemStack);
                }
            }
        }
    }
}
