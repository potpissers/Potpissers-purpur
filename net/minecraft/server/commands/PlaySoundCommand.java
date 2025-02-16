package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class PlaySoundCommand {
    private static final SimpleCommandExceptionType ERROR_TOO_FAR = new SimpleCommandExceptionType(Component.translatable("commands.playsound.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> requiredArgumentBuilder = Commands.argument("sound", ResourceLocationArgument.id())
            .suggests(SuggestionProviders.AVAILABLE_SOUNDS)
            .executes(
                commandContext -> playSound(
                    commandContext.getSource(),
                    getCallingPlayerAsCollection(commandContext.getSource().getPlayer()),
                    ResourceLocationArgument.getId(commandContext, "sound"),
                    SoundSource.MASTER,
                    commandContext.getSource().getPosition(),
                    1.0F,
                    1.0F,
                    0.0F
                )
            );

        for (SoundSource soundSource : SoundSource.values()) {
            requiredArgumentBuilder.then(source(soundSource));
        }

        dispatcher.register(Commands.literal("playsound").requires(commandSourceStack -> commandSourceStack.hasPermission(2)).then(requiredArgumentBuilder));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> source(SoundSource category) {
        return Commands.literal(category.getName())
            .executes(
                context -> playSound(
                    context.getSource(),
                    getCallingPlayerAsCollection(context.getSource().getPlayer()),
                    ResourceLocationArgument.getId(context, "sound"),
                    category,
                    context.getSource().getPosition(),
                    1.0F,
                    1.0F,
                    0.0F
                )
            )
            .then(
                Commands.argument("targets", EntityArgument.players())
                    .executes(
                        context -> playSound(
                            context.getSource(),
                            EntityArgument.getPlayers(context, "targets"),
                            ResourceLocationArgument.getId(context, "sound"),
                            category,
                            context.getSource().getPosition(),
                            1.0F,
                            1.0F,
                            0.0F
                        )
                    )
                    .then(
                        Commands.argument("pos", Vec3Argument.vec3())
                            .executes(
                                context -> playSound(
                                    context.getSource(),
                                    EntityArgument.getPlayers(context, "targets"),
                                    ResourceLocationArgument.getId(context, "sound"),
                                    category,
                                    Vec3Argument.getVec3(context, "pos"),
                                    1.0F,
                                    1.0F,
                                    0.0F
                                )
                            )
                            .then(
                                Commands.argument("volume", FloatArgumentType.floatArg(0.0F))
                                    .executes(
                                        context -> playSound(
                                            context.getSource(),
                                            EntityArgument.getPlayers(context, "targets"),
                                            ResourceLocationArgument.getId(context, "sound"),
                                            category,
                                            Vec3Argument.getVec3(context, "pos"),
                                            context.getArgument("volume", Float.class),
                                            1.0F,
                                            0.0F
                                        )
                                    )
                                    .then(
                                        Commands.argument("pitch", FloatArgumentType.floatArg(0.0F, 2.0F))
                                            .executes(
                                                commandContext -> playSound(
                                                    commandContext.getSource(),
                                                    EntityArgument.getPlayers(commandContext, "targets"),
                                                    ResourceLocationArgument.getId(commandContext, "sound"),
                                                    category,
                                                    Vec3Argument.getVec3(commandContext, "pos"),
                                                    commandContext.getArgument("volume", Float.class),
                                                    commandContext.getArgument("pitch", Float.class),
                                                    0.0F
                                                )
                                            )
                                            .then(
                                                Commands.argument("minVolume", FloatArgumentType.floatArg(0.0F, 1.0F))
                                                    .executes(
                                                        commandContext -> playSound(
                                                            commandContext.getSource(),
                                                            EntityArgument.getPlayers(commandContext, "targets"),
                                                            ResourceLocationArgument.getId(commandContext, "sound"),
                                                            category,
                                                            Vec3Argument.getVec3(commandContext, "pos"),
                                                            commandContext.getArgument("volume", Float.class),
                                                            commandContext.getArgument("pitch", Float.class),
                                                            commandContext.getArgument("minVolume", Float.class)
                                                        )
                                                    )
                                            )
                                    )
                            )
                    )
            );
    }

    private static Collection<ServerPlayer> getCallingPlayerAsCollection(@Nullable ServerPlayer player) {
        return player != null ? List.of(player) : List.of();
    }

    private static int playSound(
        CommandSourceStack source,
        Collection<ServerPlayer> targets,
        ResourceLocation sound,
        SoundSource category,
        Vec3 pos,
        float volume,
        float pitch,
        float minVolume
    ) throws CommandSyntaxException {
        Holder<SoundEvent> holder = Holder.direct(SoundEvent.createVariableRangeEvent(sound));
        double d = Mth.square(holder.value().getRange(volume));
        int i = 0;
        long randomLong = source.getLevel().getRandom().nextLong();

        for (ServerPlayer serverPlayer : targets) {
            double d1 = pos.x - serverPlayer.getX();
            double d2 = pos.y - serverPlayer.getY();
            double d3 = pos.z - serverPlayer.getZ();
            double d4 = d1 * d1 + d2 * d2 + d3 * d3;
            Vec3 vec3 = pos;
            float f = volume;
            if (d4 > d) {
                if (minVolume <= 0.0F) {
                    continue;
                }

                double squareRoot = Math.sqrt(d4);
                vec3 = new Vec3(
                    serverPlayer.getX() + d1 / squareRoot * 2.0, serverPlayer.getY() + d2 / squareRoot * 2.0, serverPlayer.getZ() + d3 / squareRoot * 2.0
                );
                f = minVolume;
            }

            serverPlayer.connection.send(new ClientboundSoundPacket(holder, category, vec3.x(), vec3.y(), vec3.z(), f, pitch, randomLong));
            i++;
        }

        if (i == 0) {
            throw ERROR_TOO_FAR.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable(
                        "commands.playsound.success.single", Component.translationArg(sound), targets.iterator().next().getDisplayName()
                    ),
                    true
                );
            } else {
                source.sendSuccess(() -> Component.translatable("commands.playsound.success.multiple", Component.translationArg(sound), targets.size()), true);
            }

            return i;
        }
    }
}
