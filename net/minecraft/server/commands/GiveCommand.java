package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public class GiveCommand {
    public static final int MAX_ALLOWED_ITEMSTACKS = 100;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("give")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("targets", EntityArgument.players())
                        .then(
                            Commands.argument("item", ItemArgument.item(context))
                                .executes(
                                    context1 -> giveItem(
                                        context1.getSource(), ItemArgument.getItem(context1, "item"), EntityArgument.getPlayers(context1, "targets"), 1
                                    )
                                )
                                .then(
                                    Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(
                                            context1 -> giveItem(
                                                context1.getSource(),
                                                ItemArgument.getItem(context1, "item"),
                                                EntityArgument.getPlayers(context1, "targets"),
                                                IntegerArgumentType.getInteger(context1, "count")
                                            )
                                        )
                                )
                        )
                )
        );
    }

    private static int giveItem(CommandSourceStack source, ItemInput item, Collection<ServerPlayer> targets, int count) throws CommandSyntaxException {
        ItemStack itemStack = item.createItemStack(1, false);
        final Component displayName = itemStack.getDisplayName(); // Paper - get display name early
        int maxStackSize = itemStack.getMaxStackSize();
        int i = maxStackSize * 100;
        if (count > i) {
            source.sendFailure(Component.translatable("commands.give.failed.toomanyitems", i, itemStack.getDisplayName()));
            return 0;
        } else {
            for (ServerPlayer serverPlayer : targets) {
                int i1 = count;

                while (i1 > 0) {
                    int min = Math.min(maxStackSize, i1);
                    i1 -= min;
                    ItemStack itemStack1 = item.createItemStack(min, false);
                    boolean flag = serverPlayer.getInventory().add(itemStack1);
                    if (org.purpurmc.purpur.PurpurConfig.disableGiveCommandDrops) continue; // Purpur - add config option for toggling give command dropping
                    if (flag && itemStack1.isEmpty()) {
                        ItemEntity itemEntity = serverPlayer.drop(itemStack, false, false, false); // CraftBukkit - SPIGOT-2942: Add boolean to call event
                        if (itemEntity != null) {
                            itemEntity.makeFakeItem();
                        }

                        serverPlayer.level()
                            .playSound(
                                null,
                                serverPlayer.getX(),
                                serverPlayer.getY(),
                                serverPlayer.getZ(),
                                SoundEvents.ITEM_PICKUP,
                                SoundSource.PLAYERS,
                                0.2F,
                                ((serverPlayer.getRandom().nextFloat() - serverPlayer.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F
                            );
                        serverPlayer.containerMenu.broadcastChanges();
                    } else {
                        ItemEntity itemEntity = serverPlayer.drop(itemStack1, false);
                        if (itemEntity != null) {
                            itemEntity.setNoPickUpDelay();
                            itemEntity.setTarget(serverPlayer.getUUID());
                        }
                    }
                }
            }

            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable("commands.give.success.single", count, displayName, targets.iterator().next().getDisplayName()), // Paper - use cached display name
                    true
                );
            } else {
                source.sendSuccess(() -> Component.translatable("commands.give.success.single", count, displayName, targets.size()), true); // Paper - use cached display name
            }

            return targets.size();
        }
    }
}
