package net.minecraft.world.item;

import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.component.UseCooldown;

public class ItemCooldowns {
    public final Map<ResourceLocation, ItemCooldowns.CooldownInstance> cooldowns = Maps.newHashMap();
    public int tickCount;

    public boolean isOnCooldown(ItemStack stack) {
        return this.getCooldownPercent(stack, 0.0F) > 0.0F;
    }

    public float getCooldownPercent(ItemStack stack, float partialTick) {
        ResourceLocation cooldownGroup = this.getCooldownGroup(stack);
        ItemCooldowns.CooldownInstance cooldownInstance = this.cooldowns.get(cooldownGroup);
        if (cooldownInstance != null) {
            float f = cooldownInstance.endTime - cooldownInstance.startTime;
            float f1 = cooldownInstance.endTime - (this.tickCount + partialTick);
            return Mth.clamp(f1 / f, 0.0F, 1.0F);
        } else {
            return 0.0F;
        }
    }

    public void tick() {
        this.tickCount++;
        if (!this.cooldowns.isEmpty()) {
            Iterator<Entry<ResourceLocation, ItemCooldowns.CooldownInstance>> iterator = this.cooldowns.entrySet().iterator();

            while (iterator.hasNext()) {
                Entry<ResourceLocation, ItemCooldowns.CooldownInstance> entry = iterator.next();
                if (entry.getValue().endTime <= this.tickCount) {
                    iterator.remove();
                    this.onCooldownEnded(entry.getKey());
                }
            }
        }
    }

    public ResourceLocation getCooldownGroup(ItemStack stack) {
        UseCooldown useCooldown = stack.get(DataComponents.USE_COOLDOWN);
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return useCooldown == null ? key : useCooldown.cooldownGroup().orElse(key);
    }

    public void addCooldown(ItemStack stack, int cooldown) {
        this.addCooldown(this.getCooldownGroup(stack), cooldown);
    }

    public void addCooldown(ResourceLocation group, int cooldown) {
        // Paper start - Item cooldown events
        this.addCooldown(group, cooldown, true);
    }

    public void addCooldown(ResourceLocation group, int cooldown, boolean callEvent) {
        // Event called in server override
        // Paper end - Item cooldown events
        this.cooldowns.put(group, new ItemCooldowns.CooldownInstance(this.tickCount, this.tickCount + cooldown));
        this.onCooldownStarted(group, cooldown);
    }

    public void removeCooldown(ResourceLocation group) {
        this.cooldowns.remove(group);
        this.onCooldownEnded(group);
    }

    protected void onCooldownStarted(ResourceLocation group, int cooldown) {
    }

    protected void onCooldownEnded(ResourceLocation group) {
    }

    public record CooldownInstance(int startTime, int endTime) {
    }
}
