package net.minecraft.world.damagesource;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class CombatTracker {
    public static final int RESET_DAMAGE_STATUS_TIME = 100;
    public static final int RESET_COMBAT_STATUS_TIME = 300;
    private static final Style INTENTIONAL_GAME_DESIGN_STYLE = Style.EMPTY
        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://bugs.mojang.com/browse/MCPE-28723"))
        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("MCPE-28723")));
    private final List<CombatEntry> entries = Lists.newArrayList();
    private final LivingEntity mob;
    private int lastDamageTime;
    private int combatStartTime;
    private int combatEndTime;
    private boolean inCombat;
    private boolean takingDamage;

    public CombatTracker(LivingEntity mob) {
        this.mob = mob;
    }

    public void recordDamage(DamageSource source, float damage) {
        this.recheckStatus();
        FallLocation currentFallLocation = FallLocation.getCurrentFallLocation(this.mob);
        CombatEntry combatEntry = new CombatEntry(source, damage, currentFallLocation, this.mob.fallDistance);
        this.entries.add(combatEntry);
        this.lastDamageTime = this.mob.tickCount;
        this.takingDamage = true;
        if (!this.inCombat && this.mob.isAlive() && shouldEnterCombat(source)) {
            this.inCombat = true;
            this.combatStartTime = this.mob.tickCount;
            this.combatEndTime = this.combatStartTime;
            this.mob.onEnterCombat();
        }
    }

    private static boolean shouldEnterCombat(DamageSource source) {
        return source.getEntity() instanceof LivingEntity;
    }

    private Component getMessageForAssistedFall(Entity entity, Component entityDisplayName, String hasWeaponTranslationKey, String noWeaponTranslationKey) {
        ItemStack itemStack = entity instanceof LivingEntity livingEntity ? livingEntity.getMainHandItem() : ItemStack.EMPTY;
        return !itemStack.isEmpty() && (org.purpurmc.purpur.PurpurConfig.playerDeathsAlwaysShowItem || itemStack.has(DataComponents.CUSTOM_NAME)) // Purpur - always show item in player death messages
            ? Component.translatable(hasWeaponTranslationKey, this.mob.getDisplayName(), entityDisplayName, itemStack.getDisplayName())
            : Component.translatable(noWeaponTranslationKey, this.mob.getDisplayName(), entityDisplayName);
    }

    private Component getFallMessage(CombatEntry combatEntry, @Nullable Entity entity) {
        DamageSource damageSource = combatEntry.source();
        if (!damageSource.is(DamageTypeTags.IS_FALL) && !damageSource.is(DamageTypeTags.ALWAYS_MOST_SIGNIFICANT_FALL)) {
            Component displayName = getDisplayName(entity);
            Entity entity1 = damageSource.getEntity();
            Component displayName1 = getDisplayName(entity1);
            if (displayName1 != null && !displayName1.equals(displayName)) {
                return this.getMessageForAssistedFall(entity1, displayName1, "death.fell.assist.item", "death.fell.assist");
            } else {
                return (Component)(displayName != null
                    ? this.getMessageForAssistedFall(entity, displayName, "death.fell.finish.item", "death.fell.finish")
                    : Component.translatable("death.fell.killer", this.mob.getDisplayName()));
            }
        } else {
            FallLocation fallLocation = Objects.requireNonNullElse(combatEntry.fallLocation(), FallLocation.GENERIC);
            return Component.translatable(fallLocation.languageKey(), this.mob.getDisplayName());
        }
    }

    @Nullable
    private static Component getDisplayName(@Nullable Entity entity) {
        return entity == null ? null : entity.getDisplayName();
    }

    public Component getDeathMessage() {
        if (this.entries.isEmpty()) {
            return Component.translatable("death.attack.generic", this.mob.getDisplayName());
        } else {
            CombatEntry combatEntry = this.entries.get(this.entries.size() - 1);
            DamageSource damageSource = combatEntry.source();
            CombatEntry mostSignificantFall = this.getMostSignificantFall();
            DeathMessageType deathMessageType = damageSource.type().deathMessageType();
            if (deathMessageType == DeathMessageType.FALL_VARIANTS && mostSignificantFall != null) {
                return this.getFallMessage(mostSignificantFall, damageSource.getEntity());
            } else if (deathMessageType == DeathMessageType.INTENTIONAL_GAME_DESIGN) {
                String string = "death.attack." + damageSource.getMsgId();
                Component component = ComponentUtils.wrapInSquareBrackets(Component.translatable(string + ".link")).withStyle(INTENTIONAL_GAME_DESIGN_STYLE);
                return Component.translatable(string + ".message", this.mob.getDisplayName(), component);
            } else {
                // Purpur start - Dont run with scissors!
                if (damageSource.isScissors()) {
                    return damageSource.getLocalizedDeathMessage(org.purpurmc.purpur.PurpurConfig.deathMsgRunWithScissors, this.mob);
                // Purpur start - Stonecutter damage
                } else if (damageSource.isStonecutter()) {
                    return damageSource.getLocalizedDeathMessage(org.purpurmc.purpur.PurpurConfig.deathMsgStonecutter, this.mob);
                // Purpur end - Stonecutter damage
                }
                // Purpur end - Dont run with scissors!
                return damageSource.getLocalizedDeathMessage(this.mob);
            }
        }
    }

    @Nullable
    private CombatEntry getMostSignificantFall() {
        CombatEntry combatEntry = null;
        CombatEntry combatEntry1 = null;
        float f = 0.0F;
        float f1 = 0.0F;

        for (int i = 0; i < this.entries.size(); i++) {
            CombatEntry combatEntry2 = this.entries.get(i);
            CombatEntry combatEntry3 = i > 0 ? this.entries.get(i - 1) : null;
            DamageSource damageSource = combatEntry2.source();
            boolean isAlwaysMostSignificantFall = damageSource.is(DamageTypeTags.ALWAYS_MOST_SIGNIFICANT_FALL);
            float f2 = isAlwaysMostSignificantFall ? Float.MAX_VALUE : combatEntry2.fallDistance();
            if ((damageSource.is(DamageTypeTags.IS_FALL) || isAlwaysMostSignificantFall) && f2 > 0.0F && (combatEntry == null || f2 > f1)) {
                if (i > 0) {
                    combatEntry = combatEntry3;
                } else {
                    combatEntry = combatEntry2;
                }

                f1 = f2;
            }

            if (combatEntry2.fallLocation() != null && (combatEntry1 == null || combatEntry2.damage() > f)) {
                combatEntry1 = combatEntry2;
                f = combatEntry2.damage();
            }
        }

        if (f1 > 5.0F && combatEntry != null) {
            return combatEntry;
        } else {
            return f > 5.0F && combatEntry1 != null ? combatEntry1 : null;
        }
    }

    public int getCombatDuration() {
        return this.inCombat ? this.mob.tickCount - this.combatStartTime : this.combatEndTime - this.combatStartTime;
    }

    public void recheckStatus() {
        int i = this.inCombat ? 300 : 100;
        if (this.takingDamage && (!this.mob.isAlive() || this.mob.tickCount - this.lastDamageTime > i)) {
            boolean flag = this.inCombat;
            this.takingDamage = false;
            this.inCombat = false;
            this.combatEndTime = this.mob.tickCount;
            if (flag) {
                this.mob.onLeaveCombat();
            }

            this.entries.clear();
        }
    }
}
