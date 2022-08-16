package net.minecraft.world.item;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.Level;

public class SuspiciousStewItem extends Item {

    public static final int DEFAULT_DURATION = 160;

    public SuspiciousStewItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        super.appendHoverText(stack, context, tooltip, type);
        if (type.isCreative()) {
            List<MobEffectInstance> list1 = new ArrayList();
            SuspiciousStewEffects suspicioussteweffects = (SuspiciousStewEffects) stack.getOrDefault(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY);
            Iterator iterator = suspicioussteweffects.effects().iterator();

            while (iterator.hasNext()) {
                SuspiciousStewEffects.Entry suspicioussteweffects_a = (SuspiciousStewEffects.Entry) iterator.next();

                list1.add(suspicioussteweffects_a.createEffectInstance());
            }

            Objects.requireNonNull(tooltip);
            PotionContents.addPotionTooltip(list1, tooltip::add, 1.0F, context.tickRate());
        }

    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        SuspiciousStewEffects suspicioussteweffects = (SuspiciousStewEffects) stack.getOrDefault(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY);
        Iterator iterator = suspicioussteweffects.effects().iterator();

        while (iterator.hasNext()) {
            SuspiciousStewEffects.Entry suspicioussteweffects_a = (SuspiciousStewEffects.Entry) iterator.next();

            user.addEffect(suspicioussteweffects_a.createEffectInstance(), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.FOOD); // Paper - Add missing effect cause
        }

        return super.finishUsingItem(stack, world, user);
    }

    // CraftBukkit start
    public void cancelUsingItem(net.minecraft.server.level.ServerPlayer entityplayer, ItemStack itemstack) {
        SuspiciousStewEffects suspicioussteweffects = (SuspiciousStewEffects) itemstack.getOrDefault(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY);

        for (SuspiciousStewEffects.Entry suspicioussteweffects_a : suspicioussteweffects.effects()) {
            entityplayer.connection.send(new net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket(entityplayer.getId(), suspicioussteweffects_a.effect()));
        }
        entityplayer.server.getPlayerList().sendActivePlayerEffects(entityplayer);
    }
    // CraftBukkit end
}
