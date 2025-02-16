package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class SetBlockCommand {
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.setblock.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("setblock")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(
                            Commands.argument("block", BlockStateArgument.block(context))
                                .executes(
                                    context1 -> setBlock(
                                        context1.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context1, "pos"),
                                        BlockStateArgument.getBlock(context1, "block"),
                                        SetBlockCommand.Mode.REPLACE,
                                        null
                                    )
                                )
                                .then(
                                    Commands.literal("destroy")
                                        .executes(
                                            context1 -> setBlock(
                                                context1.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context1, "pos"),
                                                BlockStateArgument.getBlock(context1, "block"),
                                                SetBlockCommand.Mode.DESTROY,
                                                null
                                            )
                                        )
                                )
                                .then(
                                    Commands.literal("keep")
                                        .executes(
                                            context1 -> setBlock(
                                                context1.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context1, "pos"),
                                                BlockStateArgument.getBlock(context1, "block"),
                                                SetBlockCommand.Mode.REPLACE,
                                                pos -> pos.getLevel().isEmptyBlock(pos.getPos())
                                            )
                                        )
                                )
                                .then(
                                    Commands.literal("replace")
                                        .executes(
                                            context1 -> setBlock(
                                                context1.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context1, "pos"),
                                                BlockStateArgument.getBlock(context1, "block"),
                                                SetBlockCommand.Mode.REPLACE,
                                                null
                                            )
                                        )
                                )
                        )
                )
        );
    }

    private static int setBlock(
        CommandSourceStack source, BlockPos pos, BlockInput state, SetBlockCommand.Mode mode, @Nullable Predicate<BlockInWorld> predicate
    ) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        if (predicate != null && !predicate.test(new BlockInWorld(level, pos, true))) {
            throw ERROR_FAILED.create();
        } else {
            boolean flag;
            if (mode == SetBlockCommand.Mode.DESTROY) {
                level.destroyBlock(pos, true);
                flag = !state.getState().isAir() || !level.getBlockState(pos).isAir();
            } else {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                Clearable.tryClear(blockEntity);
                flag = true;
            }

            if (flag && !state.place(level, pos, 2)) {
                throw ERROR_FAILED.create();
            } else {
                level.blockUpdated(pos, state.getState().getBlock());
                source.sendSuccess(() -> Component.translatable("commands.setblock.success", pos.getX(), pos.getY(), pos.getZ()), true);
                return 1;
            }
        }
    }

    public interface Filter {
        @Nullable
        BlockInput filter(BoundingBox boundingBox, BlockPos pos, BlockInput blockInput, ServerLevel level);
    }

    public static enum Mode {
        REPLACE,
        DESTROY;
    }
}
