package net.minecraft.world.entity.projectile.windcharge;

import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.phys.Vec3;

public class WindCharge extends AbstractWindCharge {
    private static final ExplosionDamageCalculator EXPLOSION_DAMAGE_CALCULATOR = new SimpleExplosionDamageCalculator(
        true, false, Optional.of(1.22F), BuiltInRegistries.BLOCK.get(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity())
    );
    private static final float RADIUS = 1.2F;
    private static final float MIN_CAMERA_DISTANCE_SQUARED = Mth.square(3.5F);
    private int noDeflectTicks = 5;

    public WindCharge(EntityType<? extends AbstractWindCharge> entityType, Level level) {
        super(entityType, level);
    }

    public WindCharge(Player player, Level level, double x, double y, double z) {
        super(EntityType.WIND_CHARGE, level, player, x, y, z);
    }

    public WindCharge(Level level, double x, double y, double z, Vec3 movement) {
        super(EntityType.WIND_CHARGE, x, y, z, movement, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.noDeflectTicks > 0) {
            this.noDeflectTicks--;
        }
    }

    @Override
    public boolean deflect(ProjectileDeflection deflection, @Nullable Entity entity, @Nullable Entity owner, boolean deflectedByPlayer) {
        return this.noDeflectTicks <= 0 && super.deflect(deflection, entity, owner, deflectedByPlayer);
    }

    @Override
    protected void explode(Vec3 pos) {
        this.level()
            .explode(
                this,
                null,
                EXPLOSION_DAMAGE_CALCULATOR,
                pos.x(),
                pos.y(),
                pos.z(),
                1.2F,
                false,
                Level.ExplosionInteraction.TRIGGER,
                ParticleTypes.GUST_EMITTER_SMALL,
                ParticleTypes.GUST_EMITTER_LARGE,
                SoundEvents.WIND_CHARGE_BURST
            );
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return (this.tickCount >= 2 || !(distance < MIN_CAMERA_DISTANCE_SQUARED)) && super.shouldRenderAtSqrDistance(distance);
    }
}
