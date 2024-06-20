package net.minecraft.world.item;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class TridentItem extends Item implements ProjectileItem {
    public static final int THROW_THRESHOLD_TIME = 10;
    public static final float BASE_DAMAGE = 8.0F;
    public static final float PROJECTILE_SHOOT_POWER = 2.5F;

    public TridentItem(Item.Properties properties) {
        super(properties);
    }

    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
            .add(
                Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 8.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND
            )
            .add(
                Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, 0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND // CombatRevert
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
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (entity instanceof Player player) {
            int i = this.getUseDuration(stack, entity) - timeLeft;
            if (i < 10) {
                return false;
            } else {
                float tridentSpinAttackStrength = EnchantmentHelper.getTridentSpinAttackStrength(stack, player);
                if (false && tridentSpinAttackStrength > 0.0F && !player.isInWaterOrRain()) { // CamwenPurpur
                    return false;
                } else if (stack.nextDamageWillBreak()) {
                    return false;
                } else {
                    Holder<SoundEvent> holder = EnchantmentHelper.pickHighestLevel(stack, EnchantmentEffectComponents.TRIDENT_SOUND)
                        .orElse(SoundEvents.TRIDENT_THROW);
                    player.awardStat(Stats.ITEM_USED.get(this));
                    if (level instanceof ServerLevel serverLevel) {
                        // stack.hurtWithoutBreaking(1, player); // CraftBukkit - moved down
                        if (tridentSpinAttackStrength == 0.0F) {
                            Projectile.Delayed<ThrownTrident> tridentDelayed = Projectile.spawnProjectileFromRotationDelayed( // Paper - PlayerLaunchProjectileEvent
                                ThrownTrident::new, serverLevel, stack, player, 0.0F, 2.5F, (float) serverLevel.purpurConfig.tridentProjectileOffset  // Purpur - Projectile offset config
                            );
                            // Paper start - PlayerLaunchProjectileEvent
                            com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent event = new com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent((org.bukkit.entity.Player) player.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(stack), (org.bukkit.entity.Projectile) tridentDelayed.projectile().getBukkitEntity());
                            if (!event.callEvent() || !tridentDelayed.attemptSpawn()) {
                                // CraftBukkit start
                                // Paper end - PlayerLaunchProjectileEvent
                                player.containerMenu.sendAllDataToRemote();
                                return false;
                            }
                            ThrownTrident thrownTrident = tridentDelayed.projectile(); // Paper - PlayerLaunchProjectileEvent

                            thrownTrident.setActualEnchantments(stack.getEnchantments()); // Purpur - Add an option to fix MC-3304 projectile looting

                            if (event.shouldConsume()) stack.hurtWithoutBreaking(1, player); // Paper - PlayerLaunchProjectileEvent
                            thrownTrident.pickupItemStack = stack.copy(); // SPIGOT-4511 update since damage call moved
                            // CraftBukkit end
                            if (player.hasInfiniteMaterials()) {
                                thrownTrident.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                            } else if (event.shouldConsume()) { // Paper - PlayerLaunchProjectileEvent
                                player.getInventory().removeItem(stack);
                            }

                            level.playSound(null, thrownTrident, holder.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                            return true;
                            // CraftBukkit start - SPIGOT-5458 also need in this branch :(
                        } else {
                            stack.hurtWithoutBreaking(1, player);
                            // CraftBukkit end
                        }
                    }

                    if (tridentSpinAttackStrength > 0.0F) {
                        float yRot = player.getYRot();
                        float xRot = player.getXRot();
                        float f = -Mth.sin(yRot * (float) (Math.PI / 180.0)) * Mth.cos(xRot * (float) (Math.PI / 180.0));
                        float f1 = -Mth.sin(xRot * (float) (Math.PI / 180.0));
                        float f2 = Mth.cos(yRot * (float) (Math.PI / 180.0)) * Mth.cos(xRot * (float) (Math.PI / 180.0));
                        float squareRoot = Mth.sqrt(f * f + f1 * f1 + f2 * f2);
                        f *= tridentSpinAttackStrength / squareRoot;
                        f1 *= tridentSpinAttackStrength / squareRoot;
                        f2 *= tridentSpinAttackStrength / squareRoot;
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerRiptideEvent(player, stack, f, f1, f2); // CraftBukkit

                        // Purpur start - Implement elytra settings
                        List<EquipmentSlot> list = EquipmentSlot.VALUES.stream().filter((enumitemslot) -> LivingEntity.canGlideUsing(entity.getItemBySlot(enumitemslot), enumitemslot)).toList();
                        if (!list.isEmpty()) {
                            EquipmentSlot enumitemslot = net.minecraft.Util.getRandom(list, entity.random);
                            ItemStack glideItem = entity.getItemBySlot(enumitemslot);
                            if (glideItem.has(net.minecraft.core.component.DataComponents.GLIDER) && level.purpurConfig.elytraDamagePerTridentBoost > 0) {
                                glideItem.hurtAndBreak(level.purpurConfig.elytraDamagePerTridentBoost, entity, enumitemslot);
                            }
                        }
                        // Purpur end - Implement elytra settings

                        player.push(f, f1, f2);
                        player.startAutoSpinAttack(20, 8.0F, stack);
                        if (player.onGround()) {
                            float f3 = 1.1999999F;
                            player.move(MoverType.SELF, new Vec3(0.0, 1.1999999F, 0.0));
                        }

                        level.playSound(null, player, holder.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                        return true;
                    } else {
                        return false;
                    }
                }
            }
        } else {
            return false;
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.nextDamageWillBreak()) {
            return InteractionResult.FAIL;
            // CamwenPurpur start
        // } else if (EnchantmentHelper.getTridentSpinAttackStrength(itemInHand, player) > 0.0F && !player.isInWaterOrRain()) {
        //     return InteractionResult.FAIL;
            // CamwenPurpur end
        } else {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        ThrownTrident thrownTrident = new ThrownTrident(level, pos.x(), pos.y(), pos.z(), stack.copyWithCount(1));
        thrownTrident.pickup = AbstractArrow.Pickup.ALLOWED;
        return thrownTrident;
    }
}
