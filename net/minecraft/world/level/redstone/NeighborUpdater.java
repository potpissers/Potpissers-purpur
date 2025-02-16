package net.minecraft.world.level.redstone;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public interface NeighborUpdater {
    Direction[] UPDATE_ORDER = new Direction[]{Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH};

    void shapeUpdate(Direction direction, BlockState state, BlockPos pos, BlockPos neighborPos, int flags, int recursionLevel);

    void neighborChanged(BlockPos pos, Block neighborBlock, @Nullable Orientation orientation);

    void neighborChanged(BlockState state, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston);

    default void updateNeighborsAtExceptFromFacing(BlockPos pos, Block block, @Nullable Direction facing, @Nullable Orientation orientation) {
        for (Direction direction : UPDATE_ORDER) {
            if (direction != facing) {
                this.neighborChanged(pos.relative(direction), block, null);
            }
        }
    }

    static void executeShapeUpdate(
        LevelAccessor level, Direction direction, BlockPos pos, BlockPos neighborPos, BlockState neighborState, int flags, int recursionLeft
    ) {
        BlockState blockState = level.getBlockState(pos);
        if ((flags & 128) == 0 || !blockState.is(Blocks.REDSTONE_WIRE)) {
            BlockState blockState1 = blockState.updateShape(level, level, pos, direction, neighborPos, neighborState, level.getRandom());
            Block.updateOrDestroy(blockState, blockState1, level, pos, flags, recursionLeft);
        }
    }

    static void executeUpdate(Level level, BlockState state, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        // Paper start - Add source block to BlockPhysicsEvent
        executeUpdate(level, state, pos, neighborBlock, orientation, movedByPiston, pos);
    }

    static void executeUpdate(Level level, BlockState state, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston, BlockPos sourcePos) {
        // Paper end - Add source block to BlockPhysicsEvent
        try {
            // CraftBukkit start
            org.bukkit.craftbukkit.CraftWorld cworld = level.getWorld();
            if (cworld != null) {
                org.bukkit.event.block.BlockPhysicsEvent event = new org.bukkit.event.block.BlockPhysicsEvent(org.bukkit.craftbukkit.block.CraftBlock.at(level, pos), org.bukkit.craftbukkit.block.data.CraftBlockData.fromData(state), org.bukkit.craftbukkit.block.CraftBlock.at(level, sourcePos)); // Paper - Add source block to BlockPhysicsEvent
                level.getCraftServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return;
                }
            }
            // CraftBukkit end
            state.handleNeighborChanged(level, pos, neighborBlock, orientation, movedByPiston);
            // Spigot start
        } catch (StackOverflowError ex) {
            level.lastPhysicsProblem = new BlockPos(pos);
            // Spigot end
        } catch (Throwable var9) {
            CrashReport crashReport = CrashReport.forThrowable(var9, "Exception while updating neighbours");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Block being updated");
            crashReportCategory.setDetail(
                "Source block type",
                () -> {
                    try {
                        return String.format(
                            Locale.ROOT,
                            "ID #%s (%s // %s)",
                            BuiltInRegistries.BLOCK.getKey(neighborBlock),
                            neighborBlock.getDescriptionId(),
                            neighborBlock.getClass().getCanonicalName()
                        );
                    } catch (Throwable var2x) {
                        return "ID #" + BuiltInRegistries.BLOCK.getKey(neighborBlock);
                    }
                }
            );
            CrashReportCategory.populateBlockDetails(crashReportCategory, level, pos, state);
            throw new ReportedException(crashReport);
        }
    }
}
