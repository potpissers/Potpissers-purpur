package net.minecraft.world.food;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;

public class FoodData {
    public int foodLevel = 20;
    public float saturationLevel = 5.0F;
    public float exhaustionLevel;
    private int tickTimer;
    // CraftBukkit start
    public int saturatedRegenRate = 80; //10; CamwenPurpur
    public int unsaturatedRegenRate = 80;
    public int starvationRate = 80;
    // CraftBukkit end

    private void add(int foodLevel, float saturationLevel) {
        this.foodLevel = Mth.clamp(foodLevel + this.foodLevel, 0, 20);
        this.saturationLevel = Mth.clamp(saturationLevel + this.saturationLevel, 0.0F, (float)this.foodLevel);
    }

    public void eat(int foodLevelModifier, float saturationLevelModifier) {
        this.add(foodLevelModifier, FoodConstants.saturationByModifier(foodLevelModifier, saturationLevelModifier));
    }

    public void eat(FoodProperties foodProperties) {
        this.add(foodProperties.nutrition(), foodProperties.saturation());
    }

    // CraftBukkit start
    public void eat(FoodProperties foodProperties, net.minecraft.world.item.ItemStack stack, ServerPlayer serverPlayer) {
        int oldFoodLevel = this.foodLevel;
        org.bukkit.event.entity.FoodLevelChangeEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callFoodLevelChangeEvent(serverPlayer, foodProperties.nutrition() + oldFoodLevel, stack);
        if (!event.isCancelled()) {
            if (serverPlayer.level().purpurConfig.playerBurpWhenFull && event.getFoodLevel() == 20 && oldFoodLevel < 20) serverPlayer.burpDelay = serverPlayer.level().purpurConfig.playerBurpDelay; // Purpur - Burp after eating food fills hunger bar completely
            this.add(event.getFoodLevel() - oldFoodLevel, foodProperties.saturation());
        }
        serverPlayer.getBukkitEntity().sendHealthUpdate();
    }
    // CraftBukkit end

    public void tick(ServerPlayer player) {
        ServerLevel serverLevel = player.serverLevel();
        Difficulty difficulty = serverLevel.getDifficulty();
        if (this.exhaustionLevel > 4.0F) {
            this.exhaustionLevel -= 4.0F;
            if (this.saturationLevel > 0.0F) {
                this.saturationLevel = Math.max(this.saturationLevel - 1.0F, 0.0F);
            } else if (difficulty != Difficulty.PEACEFUL) {
                // CraftBukkit start
                org.bukkit.event.entity.FoodLevelChangeEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callFoodLevelChangeEvent(player, Math.max(this.foodLevel - 1, 0));

                if (!event.isCancelled()) {
                    this.foodLevel = event.getFoodLevel();
                }

                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(player.getBukkitEntity().getScaledHealth(), this.foodLevel, this.saturationLevel));
                // CraftBukkit end
            }
        }

        boolean _boolean = serverLevel.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
        if (false && _boolean && this.saturationLevel > 0.0F && player.isHurt() && this.foodLevel >= 20) { // CamwenPurpur
            this.tickTimer++;
            if (this.tickTimer >= this.saturatedRegenRate) { // CraftBukkit
                float min = Math.min(this.saturationLevel, 6.0F);
                player.heal(min / 6.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.SATIATED, true); // CraftBukkit - added RegainReason // Paper - This is fast regen
                // this.addExhaustion(min); CraftBukkit - EntityExhaustionEvent
                player.causeFoodExhaustion(min, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.REGEN); // CraftBukkit - EntityExhaustionEvent
                this.tickTimer = 0;
            }
        } else if (_boolean && this.foodLevel >= 18 && player.isHurt()) {
            this.tickTimer++;
            if (this.tickTimer >= this.unsaturatedRegenRate) { // CraftBukkit - add regen rate manipulation
                player.heal(1.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.SATIATED); // CraftBukkit - added RegainReason
                // this.addExhaustion(6.0F); CraftBukkit - EntityExhaustionEvent
                player.causeFoodExhaustion(player.level().spigotConfig.regenExhaustion, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.REGEN); // CraftBukkit - EntityExhaustionEvent // Spigot - Change to use configurable value
                this.tickTimer = 0;
            }
        } else if (this.foodLevel <= 0) {
            this.tickTimer++;
            if (this.tickTimer >= this.starvationRate) { // CraftBukkit - add regen rate manipulation
                if (player.getHealth() > 10.0F || difficulty == Difficulty.HARD || player.getHealth() > 1.0F && difficulty == Difficulty.NORMAL) {
                    player.hurtServer(serverLevel, player.damageSources().starve(), player.level().purpurConfig.hungerStarvationDamage); // Purpur - Configurable hunger starvation damage
                }

                this.tickTimer = 0;
            }
        } else {
            this.tickTimer = 0;
        }
    }

    public void readAdditionalSaveData(CompoundTag compoundTag) {
        if (compoundTag.contains("foodLevel", 99)) {
            this.foodLevel = compoundTag.getInt("foodLevel");
            this.tickTimer = compoundTag.getInt("foodTickTimer");
            this.saturationLevel = compoundTag.getFloat("foodSaturationLevel");
            this.exhaustionLevel = compoundTag.getFloat("foodExhaustionLevel");
        }
    }

    public void addAdditionalSaveData(CompoundTag compoundTag) {
        compoundTag.putInt("foodLevel", this.foodLevel);
        compoundTag.putInt("foodTickTimer", this.tickTimer);
        compoundTag.putFloat("foodSaturationLevel", this.saturationLevel);
        compoundTag.putFloat("foodExhaustionLevel", this.exhaustionLevel);
    }

    public int getFoodLevel() {
        return this.foodLevel;
    }

    public boolean needsFood() {
        return this.foodLevel < 20;
    }

    public void addExhaustion(float exhaustion) {
        this.exhaustionLevel = Math.min(this.exhaustionLevel + exhaustion, 40.0F);
    }

    public float getSaturationLevel() {
        return this.saturationLevel;
    }

    public void setFoodLevel(int foodLevel) {
        this.foodLevel = foodLevel;
    }

    public void setSaturation(float saturationLevel) {
        this.saturationLevel = saturationLevel;
    }
}
