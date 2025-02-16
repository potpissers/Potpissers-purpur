package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public class LongJumpMidJump extends Behavior<Mob> {
    public static final int TIME_OUT_DURATION = 100;
    private final UniformInt timeBetweenLongJumps;
    private final SoundEvent landingSound;

    public LongJumpMidJump(UniformInt timeBetweenLongJumps, SoundEvent landingSound) {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.LONG_JUMP_MID_JUMP, MemoryStatus.VALUE_PRESENT), 100);
        this.timeBetweenLongJumps = timeBetweenLongJumps;
        this.landingSound = landingSound;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Mob entity, long gameTime) {
        return !entity.onGround();
    }

    @Override
    protected void start(ServerLevel level, Mob entity, long gameTime) {
        entity.setDiscardFriction(true);
        entity.setPose(Pose.LONG_JUMPING);
    }

    @Override
    protected void stop(ServerLevel level, Mob entity, long gameTime) {
        if (entity.onGround()) {
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.1F, 1.0, 0.1F));
            level.playSound(null, entity, this.landingSound, SoundSource.NEUTRAL, 2.0F, 1.0F);
        }

        entity.setDiscardFriction(false);
        entity.setPose(Pose.STANDING);
        entity.getBrain().eraseMemory(MemoryModuleType.LONG_JUMP_MID_JUMP);
        entity.getBrain().setMemory(MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, this.timeBetweenLongJumps.sample(level.random));
    }
}
