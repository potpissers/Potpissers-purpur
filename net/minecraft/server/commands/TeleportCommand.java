package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class TeleportCommand {
    private static final SimpleCommandExceptionType INVALID_POSITION = new SimpleCommandExceptionType(
        Component.translatable("commands.teleport.invalidPosition")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> literalCommandNode = dispatcher.register(
            Commands.literal("teleport")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("location", Vec3Argument.vec3())
                        .executes(
                            context -> teleportToPos(
                                context.getSource(),
                                Collections.singleton(context.getSource().getEntityOrException()),
                                context.getSource().getLevel(),
                                Vec3Argument.getCoordinates(context, "location"),
                                null,
                                null
                            )
                        )
                )
                .then(
                    Commands.argument("destination", EntityArgument.entity())
                        .executes(
                            context -> teleportToEntity(
                                context.getSource(),
                                Collections.singleton(context.getSource().getEntityOrException()),
                                EntityArgument.getEntity(context, "destination")
                            )
                        )
                )
                .then(
                    Commands.argument("targets", EntityArgument.entities())
                        .then(
                            Commands.argument("location", Vec3Argument.vec3())
                                .executes(
                                    context -> teleportToPos(
                                        context.getSource(),
                                        EntityArgument.getEntities(context, "targets"),
                                        context.getSource().getLevel(),
                                        Vec3Argument.getCoordinates(context, "location"),
                                        null,
                                        null
                                    )
                                )
                                .then(
                                    Commands.argument("rotation", RotationArgument.rotation())
                                        .executes(
                                            context -> teleportToPos(
                                                context.getSource(),
                                                EntityArgument.getEntities(context, "targets"),
                                                context.getSource().getLevel(),
                                                Vec3Argument.getCoordinates(context, "location"),
                                                RotationArgument.getRotation(context, "rotation"),
                                                null
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
                                                            context -> teleportToPos(
                                                                context.getSource(),
                                                                EntityArgument.getEntities(context, "targets"),
                                                                context.getSource().getLevel(),
                                                                Vec3Argument.getCoordinates(context, "location"),
                                                                null,
                                                                new LookAt.LookAtEntity(
                                                                    EntityArgument.getEntity(context, "facingEntity"), EntityAnchorArgument.Anchor.FEET
                                                                )
                                                            )
                                                        )
                                                        .then(
                                                            Commands.argument("facingAnchor", EntityAnchorArgument.anchor())
                                                                .executes(
                                                                    context -> teleportToPos(
                                                                        context.getSource(),
                                                                        EntityArgument.getEntities(context, "targets"),
                                                                        context.getSource().getLevel(),
                                                                        Vec3Argument.getCoordinates(context, "location"),
                                                                        null,
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
                                                    context -> teleportToPos(
                                                        context.getSource(),
                                                        EntityArgument.getEntities(context, "targets"),
                                                        context.getSource().getLevel(),
                                                        Vec3Argument.getCoordinates(context, "location"),
                                                        null,
                                                        new LookAt.LookAtPosition(Vec3Argument.getVec3(context, "facingLocation"))
                                                    )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.argument("destination", EntityArgument.entity())
                                .executes(
                                    context -> teleportToEntity(
                                        context.getSource(), EntityArgument.getEntities(context, "targets"), EntityArgument.getEntity(context, "destination")
                                    )
                                )
                        )
                )
        );
        dispatcher.register(Commands.literal("tp").requires(source -> source.hasPermission(2)).redirect(literalCommandNode));
    }

    private static int teleportToEntity(CommandSourceStack source, Collection<? extends Entity> targets, Entity destination) throws CommandSyntaxException {
        for (Entity entity : targets) {
            performTeleport(
                source,
                entity,
                (ServerLevel)destination.level(),
                destination.getX(),
                destination.getY(),
                destination.getZ(),
                EnumSet.noneOf(Relative.class),
                destination.getYRot(),
                destination.getXRot(),
                null
            );
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.teleport.success.entity.single", targets.iterator().next().getDisplayName(), destination.getDisplayName()
                ),
                true
            );
        } else {
            source.sendSuccess(() -> Component.translatable("commands.teleport.success.entity.multiple", targets.size(), destination.getDisplayName()), true);
        }

        return targets.size();
    }

    private static int teleportToPos(
        CommandSourceStack source,
        Collection<? extends Entity> targets,
        ServerLevel level,
        Coordinates position,
        @Nullable Coordinates rotation,
        @Nullable LookAt lookAt
    ) throws CommandSyntaxException {
        Vec3 position1 = position.getPosition(source);
        Vec2 vec2 = rotation == null ? null : rotation.getRotation(source);

        for (Entity entity : targets) {
            Set<Relative> relatives = getRelatives(position, rotation, entity.level().dimension() == level.dimension());
            if (vec2 == null) {
                performTeleport(source, entity, level, position1.x, position1.y, position1.z, relatives, entity.getYRot(), entity.getXRot(), lookAt);
            } else {
                performTeleport(source, entity, level, position1.x, position1.y, position1.z, relatives, vec2.y, vec2.x, lookAt);
            }
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.teleport.success.location.single",
                    targets.iterator().next().getDisplayName(),
                    formatDouble(position1.x),
                    formatDouble(position1.y),
                    formatDouble(position1.z)
                ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.teleport.success.location.multiple",
                    targets.size(),
                    formatDouble(position1.x),
                    formatDouble(position1.y),
                    formatDouble(position1.z)
                ),
                true
            );
        }

        return targets.size();
    }

    private static Set<Relative> getRelatives(Coordinates position, @Nullable Coordinates rotation, boolean absolute) {
        Set<Relative> set = EnumSet.noneOf(Relative.class);
        if (position.isXRelative()) {
            set.add(Relative.DELTA_X);
            if (absolute) {
                set.add(Relative.X);
            }
        }

        if (position.isYRelative()) {
            set.add(Relative.DELTA_Y);
            if (absolute) {
                set.add(Relative.Y);
            }
        }

        if (position.isZRelative()) {
            set.add(Relative.DELTA_Z);
            if (absolute) {
                set.add(Relative.Z);
            }
        }

        if (rotation == null || rotation.isXRelative()) {
            set.add(Relative.X_ROT);
        }

        if (rotation == null || rotation.isYRelative()) {
            set.add(Relative.Y_ROT);
        }

        return set;
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%f", value);
    }

    private static void performTeleport(
        CommandSourceStack source,
        Entity target,
        ServerLevel level,
        double x,
        double y,
        double z,
        Set<Relative> relatives,
        float yRot,
        float xRot,
        @Nullable LookAt lookAt
    ) throws CommandSyntaxException {
        BlockPos blockPos = BlockPos.containing(x, y, z);
        if (!Level.isInSpawnableBounds(blockPos)) {
            throw INVALID_POSITION.create();
        } else {
            double d = relatives.contains(Relative.X) ? x - target.getX() : x;
            double d1 = relatives.contains(Relative.Y) ? y - target.getY() : y;
            double d2 = relatives.contains(Relative.Z) ? z - target.getZ() : z;
            float f = relatives.contains(Relative.Y_ROT) ? yRot - target.getYRot() : yRot;
            float f1 = relatives.contains(Relative.X_ROT) ? xRot - target.getXRot() : xRot;
            float f2 = Mth.wrapDegrees(f);
            float f3 = Mth.wrapDegrees(f1);
            if (target.teleportTo(level, d, d1, d2, relatives, f2, f3, true)) {
                if (lookAt != null) {
                    lookAt.perform(source, target);
                }

                if (!(target instanceof LivingEntity livingEntity && livingEntity.isFallFlying())) {
                    target.setDeltaMovement(target.getDeltaMovement().multiply(1.0, 0.0, 1.0));
                    target.setOnGround(true);
                }

                if (target instanceof PathfinderMob pathfinderMob) {
                    pathfinderMob.getNavigation().stop();
                }
            }
        }
    }
}
