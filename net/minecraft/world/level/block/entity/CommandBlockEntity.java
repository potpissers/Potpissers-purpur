package net.minecraft.world.level.block.entity;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class CommandBlockEntity extends BlockEntity {
    private boolean powered;
    private boolean auto;
    private boolean conditionMet;
    private final BaseCommandBlock commandBlock = new BaseCommandBlock() {
        @Override
        public void setCommand(String command) {
            super.setCommand(command);
            CommandBlockEntity.this.setChanged();
        }

        @Override
        public ServerLevel getLevel() {
            return (ServerLevel)CommandBlockEntity.this.level;
        }

        @Override
        public void onUpdated() {
            BlockState blockState = CommandBlockEntity.this.level.getBlockState(CommandBlockEntity.this.worldPosition);
            this.getLevel().sendBlockUpdated(CommandBlockEntity.this.worldPosition, blockState, blockState, 3);
        }

        @Override
        public Vec3 getPosition() {
            return Vec3.atCenterOf(CommandBlockEntity.this.worldPosition);
        }

        @Override
        public CommandSourceStack createCommandSourceStack() {
            Direction direction = CommandBlockEntity.this.getBlockState().getValue(CommandBlock.FACING);
            return new CommandSourceStack(
                this,
                Vec3.atCenterOf(CommandBlockEntity.this.worldPosition),
                new Vec2(0.0F, direction.toYRot()),
                this.getLevel(),
                2,
                this.getName().getString(),
                this.getName(),
                this.getLevel().getServer(),
                null
            );
        }

        @Override
        public boolean isValid() {
            return !CommandBlockEntity.this.isRemoved();
        }
    };

    public CommandBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.COMMAND_BLOCK, pos, blockState);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.commandBlock.save(tag, registries);
        tag.putBoolean("powered", this.isPowered());
        tag.putBoolean("conditionMet", this.wasConditionMet());
        tag.putBoolean("auto", this.isAutomatic());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.commandBlock.load(tag, registries);
        this.powered = tag.getBoolean("powered");
        this.conditionMet = tag.getBoolean("conditionMet");
        this.setAutomatic(tag.getBoolean("auto"));
    }

    public BaseCommandBlock getCommandBlock() {
        return this.commandBlock;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    public boolean isPowered() {
        return this.powered;
    }

    public boolean isAutomatic() {
        return this.auto;
    }

    public void setAutomatic(boolean auto) {
        boolean flag = this.auto;
        this.auto = auto;
        if (!flag && auto && !this.powered && this.level != null && this.getMode() != CommandBlockEntity.Mode.SEQUENCE) {
            this.scheduleTick();
        }
    }

    public void onModeSwitch() {
        CommandBlockEntity.Mode mode = this.getMode();
        if (mode == CommandBlockEntity.Mode.AUTO && (this.powered || this.auto) && this.level != null) {
            this.scheduleTick();
        }
    }

    private void scheduleTick() {
        Block block = this.getBlockState().getBlock();
        if (block instanceof CommandBlock) {
            this.markConditionMet();
            this.level.scheduleTick(this.worldPosition, block, 1);
        }
    }

    public boolean wasConditionMet() {
        return this.conditionMet;
    }

    public boolean markConditionMet() {
        this.conditionMet = true;
        if (this.isConditional()) {
            BlockPos blockPos = this.worldPosition.relative(this.level.getBlockState(this.worldPosition).getValue(CommandBlock.FACING).getOpposite());
            if (this.level.getBlockState(blockPos).getBlock() instanceof CommandBlock) {
                BlockEntity blockEntity = this.level.getBlockEntity(blockPos);
                this.conditionMet = blockEntity instanceof CommandBlockEntity && ((CommandBlockEntity)blockEntity).getCommandBlock().getSuccessCount() > 0;
            } else {
                this.conditionMet = false;
            }
        }

        return this.conditionMet;
    }

    public CommandBlockEntity.Mode getMode() {
        BlockState blockState = this.getBlockState();
        if (blockState.is(Blocks.COMMAND_BLOCK)) {
            return CommandBlockEntity.Mode.REDSTONE;
        } else if (blockState.is(Blocks.REPEATING_COMMAND_BLOCK)) {
            return CommandBlockEntity.Mode.AUTO;
        } else {
            return blockState.is(Blocks.CHAIN_COMMAND_BLOCK) ? CommandBlockEntity.Mode.SEQUENCE : CommandBlockEntity.Mode.REDSTONE;
        }
    }

    public boolean isConditional() {
        BlockState blockState = this.level.getBlockState(this.getBlockPos());
        return blockState.getBlock() instanceof CommandBlock && blockState.getValue(CommandBlock.CONDITIONAL);
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        this.commandBlock.setCustomName(componentInput.get(DataComponents.CUSTOM_NAME));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CUSTOM_NAME, this.commandBlock.getCustomName());
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("CustomName");
        tag.remove("conditionMet");
        tag.remove("powered");
    }

    public static enum Mode {
        SEQUENCE,
        AUTO,
        REDSTONE;
    }
}
