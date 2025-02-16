package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class CloneCommands {
    private static final SimpleCommandExceptionType ERROR_OVERLAP = new SimpleCommandExceptionType(Component.translatable("commands.clone.overlap"));
    private static final Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE = new Dynamic2CommandExceptionType(
        (maxBlocks, specifiedBlocks) -> Component.translatableEscape("commands.clone.toobig", maxBlocks, specifiedBlocks)
    );
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.clone.failed"));
    public static final Predicate<BlockInWorld> FILTER_AIR = blockInWorld -> !blockInWorld.getState().isAir();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("clone")
                .requires(source -> source.hasPermission(2))
                .then(beginEndDestinationAndModeSuffix(context, context1 -> context1.getSource().getLevel()))
                .then(
                    Commands.literal("from")
                        .then(
                            Commands.argument("sourceDimension", DimensionArgument.dimension())
                                .then(beginEndDestinationAndModeSuffix(context, context1 -> DimensionArgument.getDimension(context1, "sourceDimension")))
                        )
                )
        );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> beginEndDestinationAndModeSuffix(
        CommandBuildContext buildContext, CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, ServerLevel> levelGetter
    ) {
        return Commands.argument("begin", BlockPosArgument.blockPos())
            .then(
                Commands.argument("end", BlockPosArgument.blockPos())
                    .then(destinationAndModeSuffix(buildContext, levelGetter, context -> context.getSource().getLevel()))
                    .then(
                        Commands.literal("to")
                            .then(
                                Commands.argument("targetDimension", DimensionArgument.dimension())
                                    .then(
                                        destinationAndModeSuffix(
                                            buildContext, levelGetter, context -> DimensionArgument.getDimension(context, "targetDimension")
                                        )
                                    )
                            )
                    )
            );
    }

    private static CloneCommands.DimensionAndPosition getLoadedDimensionAndPosition(CommandContext<CommandSourceStack> context, ServerLevel level, String name) throws CommandSyntaxException {
        BlockPos loadedBlockPos = BlockPosArgument.getLoadedBlockPos(context, level, name);
        return new CloneCommands.DimensionAndPosition(level, loadedBlockPos);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> destinationAndModeSuffix(
        CommandBuildContext buildContext,
        CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, ServerLevel> sourceLevelGetter,
        CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, ServerLevel> destinationLevelGetter
    ) {
        CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> commandFunction = context -> getLoadedDimensionAndPosition(
            context, sourceLevelGetter.apply(context), "begin"
        );
        CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> commandFunction1 = context -> getLoadedDimensionAndPosition(
            context, sourceLevelGetter.apply(context), "end"
        );
        CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> commandFunction2 = context -> getLoadedDimensionAndPosition(
            context, destinationLevelGetter.apply(context), "destination"
        );
        return Commands.argument("destination", BlockPosArgument.blockPos())
            .executes(
                context -> clone(
                    context.getSource(),
                    commandFunction.apply(context),
                    commandFunction1.apply(context),
                    commandFunction2.apply(context),
                    blockInWorld -> true,
                    CloneCommands.Mode.NORMAL
                )
            )
            .then(
                wrapWithCloneMode(
                    commandFunction,
                    commandFunction1,
                    commandFunction2,
                    context -> blockInWorld -> true,
                    Commands.literal("replace")
                        .executes(
                            context -> clone(
                                context.getSource(),
                                commandFunction.apply(context),
                                commandFunction1.apply(context),
                                commandFunction2.apply(context),
                                blockInWorld -> true,
                                CloneCommands.Mode.NORMAL
                            )
                        )
                )
            )
            .then(
                wrapWithCloneMode(
                    commandFunction,
                    commandFunction1,
                    commandFunction2,
                    context -> FILTER_AIR,
                    Commands.literal("masked")
                        .executes(
                            context -> clone(
                                context.getSource(),
                                commandFunction.apply(context),
                                commandFunction1.apply(context),
                                commandFunction2.apply(context),
                                FILTER_AIR,
                                CloneCommands.Mode.NORMAL
                            )
                        )
                )
            )
            .then(
                Commands.literal("filtered")
                    .then(
                        wrapWithCloneMode(
                            commandFunction,
                            commandFunction1,
                            commandFunction2,
                            context -> BlockPredicateArgument.getBlockPredicate(context, "filter"),
                            Commands.argument("filter", BlockPredicateArgument.blockPredicate(buildContext))
                                .executes(
                                    context -> clone(
                                        context.getSource(),
                                        commandFunction.apply(context),
                                        commandFunction1.apply(context),
                                        commandFunction2.apply(context),
                                        BlockPredicateArgument.getBlockPredicate(context, "filter"),
                                        CloneCommands.Mode.NORMAL
                                    )
                                )
                        )
                    )
            );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> wrapWithCloneMode(
        CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> beginGetter,
        CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> endGetter,
        CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> targetGetter,
        CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, Predicate<BlockInWorld>> filterGetter,
        ArgumentBuilder<CommandSourceStack, ?> argumentBuilder
    ) {
        return argumentBuilder.then(
                Commands.literal("force")
                    .executes(
                        context -> clone(
                            context.getSource(),
                            beginGetter.apply(context),
                            endGetter.apply(context),
                            targetGetter.apply(context),
                            filterGetter.apply(context),
                            CloneCommands.Mode.FORCE
                        )
                    )
            )
            .then(
                Commands.literal("move")
                    .executes(
                        context -> clone(
                            context.getSource(),
                            beginGetter.apply(context),
                            endGetter.apply(context),
                            targetGetter.apply(context),
                            filterGetter.apply(context),
                            CloneCommands.Mode.MOVE
                        )
                    )
            )
            .then(
                Commands.literal("normal")
                    .executes(
                        context -> clone(
                            context.getSource(),
                            beginGetter.apply(context),
                            endGetter.apply(context),
                            targetGetter.apply(context),
                            filterGetter.apply(context),
                            CloneCommands.Mode.NORMAL
                        )
                    )
            );
    }

    private static int clone(
        CommandSourceStack source,
        CloneCommands.DimensionAndPosition begin,
        CloneCommands.DimensionAndPosition end,
        CloneCommands.DimensionAndPosition target,
        Predicate<BlockInWorld> filter,
        CloneCommands.Mode mode
    ) throws CommandSyntaxException {
        BlockPos blockPos = begin.position();
        BlockPos blockPos1 = end.position();
        BoundingBox boundingBox = BoundingBox.fromCorners(blockPos, blockPos1);
        BlockPos blockPos2 = target.position();
        BlockPos blockPos3 = blockPos2.offset(boundingBox.getLength());
        BoundingBox boundingBox1 = BoundingBox.fromCorners(blockPos2, blockPos3);
        ServerLevel serverLevel = begin.dimension();
        ServerLevel serverLevel1 = target.dimension();
        if (!mode.canOverlap() && serverLevel == serverLevel1 && boundingBox1.intersects(boundingBox)) {
            throw ERROR_OVERLAP.create();
        } else {
            int i = boundingBox.getXSpan() * boundingBox.getYSpan() * boundingBox.getZSpan();
            int _int = source.getLevel().getGameRules().getInt(GameRules.RULE_COMMAND_MODIFICATION_BLOCK_LIMIT);
            if (i > _int) {
                throw ERROR_AREA_TOO_LARGE.create(_int, i);
            } else if (serverLevel.hasChunksAt(blockPos, blockPos1) && serverLevel1.hasChunksAt(blockPos2, blockPos3)) {
                List<CloneCommands.CloneBlockInfo> list = Lists.newArrayList();
                List<CloneCommands.CloneBlockInfo> list1 = Lists.newArrayList();
                List<CloneCommands.CloneBlockInfo> list2 = Lists.newArrayList();
                Deque<BlockPos> list3 = Lists.newLinkedList();
                BlockPos blockPos4 = new BlockPos(
                    boundingBox1.minX() - boundingBox.minX(), boundingBox1.minY() - boundingBox.minY(), boundingBox1.minZ() - boundingBox.minZ()
                );

                for (int z = boundingBox.minZ(); z <= boundingBox.maxZ(); z++) {
                    for (int y = boundingBox.minY(); y <= boundingBox.maxY(); y++) {
                        for (int x = boundingBox.minX(); x <= boundingBox.maxX(); x++) {
                            BlockPos blockPos5 = new BlockPos(x, y, z);
                            BlockPos blockPos6 = blockPos5.offset(blockPos4);
                            BlockInWorld blockInWorld = new BlockInWorld(serverLevel, blockPos5, false);
                            BlockState state = blockInWorld.getState();
                            if (filter.test(blockInWorld)) {
                                BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos5);
                                if (blockEntity != null) {
                                    CloneCommands.CloneBlockEntityInfo cloneBlockEntityInfo = new CloneCommands.CloneBlockEntityInfo(
                                        blockEntity.saveCustomOnly(source.registryAccess()), blockEntity.components()
                                    );
                                    list1.add(new CloneCommands.CloneBlockInfo(blockPos6, state, cloneBlockEntityInfo));
                                    list3.addLast(blockPos5);
                                } else if (!state.isSolidRender() && !state.isCollisionShapeFullBlock(serverLevel, blockPos5)) {
                                    list2.add(new CloneCommands.CloneBlockInfo(blockPos6, state, null));
                                    list3.addFirst(blockPos5);
                                } else {
                                    list.add(new CloneCommands.CloneBlockInfo(blockPos6, state, null));
                                    list3.addLast(blockPos5);
                                }
                            }
                        }
                    }
                }

                if (mode == CloneCommands.Mode.MOVE) {
                    for (BlockPos blockPos7 : list3) {
                        BlockEntity blockEntity1 = serverLevel.getBlockEntity(blockPos7);
                        Clearable.tryClear(blockEntity1);
                        serverLevel.setBlock(blockPos7, Blocks.BARRIER.defaultBlockState(), 2);
                    }

                    for (BlockPos blockPos7 : list3) {
                        serverLevel.setBlock(blockPos7, Blocks.AIR.defaultBlockState(), 3);
                    }
                }

                List<CloneCommands.CloneBlockInfo> list4 = Lists.newArrayList();
                list4.addAll(list);
                list4.addAll(list1);
                list4.addAll(list2);
                List<CloneCommands.CloneBlockInfo> list5 = Lists.reverse(list4);

                for (CloneCommands.CloneBlockInfo cloneBlockInfo : list5) {
                    BlockEntity blockEntity2 = serverLevel1.getBlockEntity(cloneBlockInfo.pos);
                    Clearable.tryClear(blockEntity2);
                    serverLevel1.setBlock(cloneBlockInfo.pos, Blocks.BARRIER.defaultBlockState(), 2);
                }

                int xx = 0;

                for (CloneCommands.CloneBlockInfo cloneBlockInfo1 : list4) {
                    if (serverLevel1.setBlock(cloneBlockInfo1.pos, cloneBlockInfo1.state, 2)) {
                        xx++;
                    }
                }

                for (CloneCommands.CloneBlockInfo cloneBlockInfo1x : list1) {
                    BlockEntity blockEntity3 = serverLevel1.getBlockEntity(cloneBlockInfo1x.pos);
                    if (cloneBlockInfo1x.blockEntityInfo != null && blockEntity3 != null) {
                        blockEntity3.loadCustomOnly(cloneBlockInfo1x.blockEntityInfo.tag, serverLevel1.registryAccess());
                        blockEntity3.setComponents(cloneBlockInfo1x.blockEntityInfo.components);
                        blockEntity3.setChanged();
                    }

                    serverLevel1.setBlock(cloneBlockInfo1x.pos, cloneBlockInfo1x.state, 2);
                }

                for (CloneCommands.CloneBlockInfo cloneBlockInfo1x : list5) {
                    serverLevel1.blockUpdated(cloneBlockInfo1x.pos, cloneBlockInfo1x.state.getBlock());
                }

                serverLevel1.getBlockTicks().copyAreaFrom(serverLevel.getBlockTicks(), boundingBox, blockPos4);
                if (xx == 0) {
                    throw ERROR_FAILED.create();
                } else {
                    int i1 = xx;
                    source.sendSuccess(() -> Component.translatable("commands.clone.success", i1), true);
                    return xx;
                }
            } else {
                throw BlockPosArgument.ERROR_NOT_LOADED.create();
            }
        }
    }

    record CloneBlockEntityInfo(CompoundTag tag, DataComponentMap components) {
    }

    record CloneBlockInfo(BlockPos pos, BlockState state, @Nullable CloneCommands.CloneBlockEntityInfo blockEntityInfo) {
    }

    @FunctionalInterface
    interface CommandFunction<T, R> {
        R apply(T input) throws CommandSyntaxException;
    }

    record DimensionAndPosition(ServerLevel dimension, BlockPos position) {
    }

    static enum Mode {
        FORCE(true),
        MOVE(true),
        NORMAL(false);

        private final boolean canOverlap;

        private Mode(final boolean canOverlap) {
            this.canOverlap = canOverlap;
        }

        public boolean canOverlap() {
            return this.canOverlap;
        }
    }
}
