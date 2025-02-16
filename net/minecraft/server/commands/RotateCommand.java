package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;

public class RotateCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("rotate")
                .requires(commandSourceStack -> commandSourceStack.hasPermission(2))
                .then(
                    Commands.argument("target", EntityArgument.entity())
                        .then(
                            Commands.argument("rotation", RotationArgument.rotation())
                                .executes(
                                    context -> rotate(
                                        context.getSource(), EntityArgument.getEntity(context, "target"), RotationArgument.getRotation(context, "rotation")
                                    )
                                )
                        )
                        .then(
                            Commands.literal("facing")
                                .then(
                                    Commands.literal("entity")
                                        .then(
                                            Commands.argument("facingEntity", EntityArgument.entity())
                                                .executes(
                                                    context -> rotate(
                                                        context.getSource(),
                                                        EntityArgument.getEntity(context, "target"),
                                                        new LookAt.LookAtEntity(
                                                            EntityArgument.getEntity(context, "facingEntity"), EntityAnchorArgument.Anchor.FEET
                                                        )
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("facingAnchor", EntityAnchorArgument.anchor())
                                                        .executes(
                                                            context -> rotate(
                                                                context.getSource(),
                                                                EntityArgument.getEntity(context, "target"),
                                                                new LookAt.LookAtEntity(
                                                                    EntityArgument.getEntity(context, "facingEntity"),
                                                                    EntityAnchorArgument.getAnchor(context, "facingAnchor")
                                                                )
                                                            )
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.argument("facingLocation", Vec3Argument.vec3())
                                        .executes(
                                            commandContext -> rotate(
                                                commandContext.getSource(),
                                                EntityArgument.getEntity(commandContext, "target"),
                                                new LookAt.LookAtPosition(Vec3Argument.getVec3(commandContext, "facingLocation"))
                                            )
                                        )
                                )
                        )
                )
        );
    }

    private static int rotate(CommandSourceStack source, Entity entity, Coordinates coordinates) {
        Vec2 rotation = coordinates.getRotation(source);
        entity.forceSetRotation(rotation.y, rotation.x);
        source.sendSuccess(() -> Component.translatable("commands.rotate.success", entity.getDisplayName()), true);
        return 1;
    }

    private static int rotate(CommandSourceStack source, Entity entity, LookAt lookAt) {
        lookAt.perform(source, entity);
        source.sendSuccess(() -> Component.translatable("commands.rotate.success", entity.getDisplayName()), true);
        return 1;
    }
}
