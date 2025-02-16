package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public class RideCommand {
    private static final DynamicCommandExceptionType ERROR_NOT_RIDING = new DynamicCommandExceptionType(
        target -> Component.translatableEscape("commands.ride.not_riding", target)
    );
    private static final Dynamic2CommandExceptionType ERROR_ALREADY_RIDING = new Dynamic2CommandExceptionType(
        (target, vehicle) -> Component.translatableEscape("commands.ride.already_riding", target, vehicle)
    );
    private static final Dynamic2CommandExceptionType ERROR_MOUNT_FAILED = new Dynamic2CommandExceptionType(
        (target, vehicle) -> Component.translatableEscape("commands.ride.mount.failure.generic", target, vehicle)
    );
    private static final SimpleCommandExceptionType ERROR_MOUNTING_PLAYER = new SimpleCommandExceptionType(
        Component.translatable("commands.ride.mount.failure.cant_ride_players")
    );
    private static final SimpleCommandExceptionType ERROR_MOUNTING_LOOP = new SimpleCommandExceptionType(
        Component.translatable("commands.ride.mount.failure.loop")
    );
    private static final SimpleCommandExceptionType ERROR_WRONG_DIMENSION = new SimpleCommandExceptionType(
        Component.translatable("commands.ride.mount.failure.wrong_dimension")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ride")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("target", EntityArgument.entity())
                        .then(
                            Commands.literal("mount")
                                .then(
                                    Commands.argument("vehicle", EntityArgument.entity())
                                        .executes(
                                            context -> mount(
                                                context.getSource(), EntityArgument.getEntity(context, "target"), EntityArgument.getEntity(context, "vehicle")
                                            )
                                        )
                                )
                        )
                        .then(Commands.literal("dismount").executes(context -> dismount(context.getSource(), EntityArgument.getEntity(context, "target"))))
                )
        );
    }

    private static int mount(CommandSourceStack source, Entity target, Entity vehicle) throws CommandSyntaxException {
        Entity vehicle1 = target.getVehicle();
        if (vehicle1 != null) {
            throw ERROR_ALREADY_RIDING.create(target.getDisplayName(), vehicle1.getDisplayName());
        } else if (vehicle.getType() == EntityType.PLAYER) {
            throw ERROR_MOUNTING_PLAYER.create();
        } else if (target.getSelfAndPassengers().anyMatch(passenger -> passenger == vehicle)) {
            throw ERROR_MOUNTING_LOOP.create();
        } else if (target.level() != vehicle.level()) {
            throw ERROR_WRONG_DIMENSION.create();
        } else if (!target.startRiding(vehicle, true)) {
            throw ERROR_MOUNT_FAILED.create(target.getDisplayName(), vehicle.getDisplayName());
        } else {
            source.sendSuccess(() -> Component.translatable("commands.ride.mount.success", target.getDisplayName(), vehicle.getDisplayName()), true);
            return 1;
        }
    }

    private static int dismount(CommandSourceStack source, Entity target) throws CommandSyntaxException {
        Entity vehicle = target.getVehicle();
        if (vehicle == null) {
            throw ERROR_NOT_RIDING.create(target.getDisplayName());
        } else {
            target.stopRiding();
            source.sendSuccess(() -> Component.translatable("commands.ride.dismount.success", target.getDisplayName(), vehicle.getDisplayName()), true);
            return 1;
        }
    }
}
