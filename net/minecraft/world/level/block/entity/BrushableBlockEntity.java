package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class BrushableBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOOT_TABLE_TAG = "LootTable";
    private static final String LOOT_TABLE_SEED_TAG = "LootTableSeed";
    private static final String HIT_DIRECTION_TAG = "hit_direction";
    private static final String ITEM_TAG = "item";
    private static final int BRUSH_COOLDOWN_TICKS = 10;
    private static final int BRUSH_RESET_TICKS = 40;
    private static final int REQUIRED_BRUSHES_TO_BREAK = 10;
    private int brushCount;
    private long brushCountResetsAtTick;
    private long coolDownEndsAtTick;
    public ItemStack item = ItemStack.EMPTY;
    @Nullable
    private Direction hitDirection;
    @Nullable
    public ResourceKey<LootTable> lootTable;
    public long lootTableSeed;

    public BrushableBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.BRUSHABLE_BLOCK, pos, blockState);
    }

    public boolean brush(long startTick, ServerLevel level, Player player, Direction hitDirection, ItemStack stack) {
        if (this.hitDirection == null) {
            this.hitDirection = hitDirection;
        }

        this.brushCountResetsAtTick = startTick + 40L;
        if (startTick < this.coolDownEndsAtTick) {
            return false;
        } else {
            this.coolDownEndsAtTick = startTick + 10L;
            this.unpackLootTable(level, player, stack);
            int completionState = this.getCompletionState();
            if (++this.brushCount >= 10) {
                this.brushingCompleted(level, player, stack);
                return true;
            } else {
                level.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), 2);
                int completionState1 = this.getCompletionState();
                if (completionState != completionState1) {
                    BlockState blockState = this.getBlockState();
                    BlockState blockState1 = blockState.setValue(BlockStateProperties.DUSTED, Integer.valueOf(completionState1));
                    level.setBlock(this.getBlockPos(), blockState1, 3);
                }

                return false;
            }
        }
    }

    private void unpackLootTable(ServerLevel level, Player player, ItemStack stack) {
        if (this.lootTable != null) {
            LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(this.lootTable);
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.GENERATE_LOOT.trigger(serverPlayer, this.lootTable);
            }

            LootParams lootParams = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(this.worldPosition))
                .withLuck(player.getLuck())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withParameter(LootContextParams.TOOL, stack)
                .create(LootContextParamSets.ARCHAEOLOGY);
            ObjectArrayList<ItemStack> randomItems = lootTable.getRandomItems(lootParams, this.lootTableSeed);

            this.item = switch (randomItems.size()) {
                case 0 -> ItemStack.EMPTY;
                case 1 -> (ItemStack)randomItems.getFirst();
                default -> {
                    LOGGER.warn("Expected max 1 loot from loot table {}, but got {}", this.lootTable.location(), randomItems.size());
                    yield randomItems.getFirst();
                }
            };
            this.lootTable = null;
            this.setChanged();
        }
    }

    private void brushingCompleted(ServerLevel level, Player player, ItemStack stack) {
        this.dropContent(level, player, stack);
        BlockState blockState = this.getBlockState();
        level.levelEvent(3008, this.getBlockPos(), Block.getId(blockState));
        Block turnsInto;
        if (this.getBlockState().getBlock() instanceof BrushableBlock brushableBlock) {
            turnsInto = brushableBlock.getTurnsInto();
        } else {
            turnsInto = Blocks.AIR;
        }

        level.setBlock(this.worldPosition, turnsInto.defaultBlockState(), 3);
    }

    private void dropContent(ServerLevel level, Player player, ItemStack stack) {
        this.unpackLootTable(level, player, stack);
        if (!this.item.isEmpty()) {
            double d = EntityType.ITEM.getWidth();
            double d1 = 1.0 - d;
            double d2 = d / 2.0;
            Direction direction = Objects.requireNonNullElse(this.hitDirection, Direction.UP);
            BlockPos blockPos = this.worldPosition.relative(direction, 1);
            double d3 = blockPos.getX() + 0.5 * d1 + d2;
            double d4 = blockPos.getY() + 0.5 + EntityType.ITEM.getHeight() / 2.0F;
            double d5 = blockPos.getZ() + 0.5 * d1 + d2;
            ItemEntity itemEntity = new ItemEntity(level, d3, d4, d5, this.item.split(level.random.nextInt(21) + 10));
            itemEntity.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(itemEntity);
            this.item = ItemStack.EMPTY;
        }
    }

    public void checkReset(ServerLevel level) {
        if (this.brushCount != 0 && level.getGameTime() >= this.brushCountResetsAtTick) {
            int completionState = this.getCompletionState();
            this.brushCount = Math.max(0, this.brushCount - 2);
            int completionState1 = this.getCompletionState();
            if (completionState != completionState1) {
                level.setBlock(this.getBlockPos(), this.getBlockState().setValue(BlockStateProperties.DUSTED, Integer.valueOf(completionState1)), 3);
            }

            int i = 4;
            this.brushCountResetsAtTick = level.getGameTime() + 4L;
        }

        if (this.brushCount == 0) {
            this.hitDirection = null;
            this.brushCountResetsAtTick = 0L;
            this.coolDownEndsAtTick = 0L;
        } else {
            level.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), 2);
        }
    }

    private boolean tryLoadLootTable(CompoundTag tag) {
        if (tag.contains("LootTable", 8)) {
            this.lootTable = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(tag.getString("LootTable")));
            this.lootTableSeed = tag.getLong("LootTableSeed");
            return true;
        } else {
            return false;
        }
    }

    private boolean trySaveLootTable(CompoundTag tag) {
        if (this.lootTable == null) {
            return false;
        } else {
            tag.putString("LootTable", this.lootTable.location().toString());
            if (this.lootTableSeed != 0L) {
                tag.putLong("LootTableSeed", this.lootTableSeed);
            }

            return true;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag compoundTag = super.getUpdateTag(registries);
        if (this.hitDirection != null) {
            compoundTag.putInt("hit_direction", this.hitDirection.ordinal());
        }

        if (!this.item.isEmpty()) {
            compoundTag.put("item", this.item.save(registries));
        }

        return compoundTag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!this.tryLoadLootTable(tag) && tag.contains("item")) {
            this.item = ItemStack.parse(registries, tag.getCompound("item")).orElse(ItemStack.EMPTY);
        } else {
            this.item = ItemStack.EMPTY;
        }

        if (tag.contains("hit_direction")) {
            this.hitDirection = Direction.values()[tag.getInt("hit_direction")];
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.trySaveLootTable(tag) && !this.item.isEmpty()) {
            tag.put("item", this.item.save(registries));
        }
    }

    public void setLootTable(ResourceKey<LootTable> lootTable, long seed) {
        this.lootTable = lootTable;
        this.lootTableSeed = seed;
    }

    private int getCompletionState() {
        if (this.brushCount == 0) {
            return 0;
        } else if (this.brushCount < 3) {
            return 1;
        } else {
            return this.brushCount < 6 ? 2 : 3;
        }
    }

    @Nullable
    public Direction getHitDirection() {
        return this.hitDirection;
    }

    public ItemStack getItem() {
        return this.item;
    }
}
