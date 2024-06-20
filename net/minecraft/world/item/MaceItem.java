package net.minecraft.world.item;

import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class MaceItem extends Item {
    private static final int DEFAULT_ATTACK_DAMAGE = 5;
    private static final float DEFAULT_ATTACK_SPEED = -3.4F;
    public static final float SMASH_ATTACK_FALL_THRESHOLD = 1.5F;
    private static final float SMASH_ATTACK_HEAVY_THRESHOLD = 5.0F;
    public static final float SMASH_ATTACK_KNOCKBACK_RADIUS = 3.5F;
    private static final float SMASH_ATTACK_KNOCKBACK_POWER = 0.7F;

    public MaceItem(Item.Properties properties) {
        super(properties);
    }

    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
            .add(
                Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 5.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND
            )
            .add(
                Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, 0.0F, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND
            )
            .build();
    }

    public static Tool createToolProperties() {
        return new Tool(List.of(), 1.0F, 2);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (canSmashAttack(attacker)) {
            ServerLevel serverLevel = (ServerLevel)attacker.level();
            attacker.setDeltaMovement(attacker.getDeltaMovement().with(Direction.Axis.Y, 0.01F));
            if (attacker instanceof ServerPlayer serverPlayer) {
                serverPlayer.currentImpulseImpactPos = this.calculateImpactPosition(serverPlayer);
                serverPlayer.setIgnoreFallDamageFromCurrentImpulse(true);
                serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
            }

            if (target.onGround()) {
                if (attacker instanceof ServerPlayer serverPlayer) {
                    serverPlayer.setSpawnExtraParticlesOnFall(true);
                }

                SoundEvent soundEvent = attacker.fallDistance > 5.0F ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND;
                serverLevel.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), soundEvent, attacker.getSoundSource(), 1.0F, 1.0F);
            } else {
                serverLevel.playSound(
                    null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.MACE_SMASH_AIR, attacker.getSoundSource(), 1.0F, 1.0F
                );
            }

            knockback(serverLevel, attacker, target);
        }

        return true;
    }

    private Vec3 calculateImpactPosition(ServerPlayer player) {
        return player.isIgnoringFallDamageFromCurrentImpulse()
                && player.currentImpulseImpactPos != null
                && player.currentImpulseImpactPos.y <= player.position().y
            ? player.currentImpulseImpactPos
            : player.position();
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        if (canSmashAttack(attacker)) {
            attacker.resetFallDistance();
        }
    }

    @Override
    public float getAttackDamageBonus(Entity target, float damage, DamageSource damageSource) {
        if (damageSource.getDirectEntity() instanceof LivingEntity livingEntity) {
            if (!canSmashAttack(livingEntity)) {
                return 0.0F;
            } else {
                float f = 3.0F;
                float f1 = 8.0F;
                float f2 = livingEntity.fallDistance;
                float f3;
                if (f2 <= 3.0F) {
                    f3 = 4.0F * f2;
                } else if (f2 <= 8.0F) {
                    f3 = 12.0F + 2.0F * (f2 - 3.0F);
                } else {
                    f3 = 22.0F + f2 - 8.0F;
                }

                return livingEntity.level() instanceof ServerLevel serverLevel
                    ? f3 + EnchantmentHelper.modifyFallBasedDamage(serverLevel, livingEntity.getWeaponItem(), target, damageSource, 0.0F) * f2
                    : f3;
            }
        } else {
            return 0.0F;
        }
    }

    private static void knockback(Level level, Entity attacker, Entity target) {
        level.levelEvent(2013, target.getOnPos(), 750);
        level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3.5), knockbackPredicate(attacker, target)).forEach(livingEntity -> {
            Vec3 vec3 = livingEntity.position().subtract(target.position());
            double knockbackPower = getKnockbackPower(attacker, livingEntity, vec3);
            Vec3 vec31 = vec3.normalize().scale(knockbackPower);
            if (knockbackPower > 0.0) {
                livingEntity.push(vec31.x, 0.7F, vec31.z);
                if (livingEntity instanceof ServerPlayer serverPlayer) {
                    serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
                }
            }
        });
    }

    private static Predicate<LivingEntity> knockbackPredicate(Entity attacker, Entity target) {
        return livingEntity -> {
            boolean flag = !livingEntity.isSpectator();
            boolean flag1 = livingEntity != attacker && livingEntity != target;
            boolean flag2 = !attacker.isAlliedTo(livingEntity);
            boolean flag3 = !(
                livingEntity instanceof TamableAnimal tamableAnimal && tamableAnimal.isTame() && attacker.getUUID().equals(tamableAnimal.getOwnerUUID())
            );
            boolean flag4 = !(livingEntity instanceof ArmorStand armorStand && armorStand.isMarker());
            boolean flag5 = target.distanceToSqr(livingEntity) <= Math.pow(3.5, 2.0);
            return flag && flag1 && flag2 && flag3 && flag4 && flag5;
        };
    }

    private static double getKnockbackPower(Entity attacker, LivingEntity entity, Vec3 offset) {
        return (3.5 - offset.length()) * 0.7F * (attacker.fallDistance > 5.0F ? 2 : 1) * (1.0 - entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
    }

    public static boolean canSmashAttack(LivingEntity entity) {
        return entity.fallDistance > 1.5F && !entity.isFallFlying();
    }

    @Nullable
    @Override
    public DamageSource getDamageSource(LivingEntity entity) {
        return canSmashAttack(entity) ? entity.damageSources().mace(entity) : super.getDamageSource(entity);
    }
}
