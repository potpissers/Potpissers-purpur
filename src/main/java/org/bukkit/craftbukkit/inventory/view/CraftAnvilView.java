package org.bukkit.craftbukkit.inventory.view;

import net.minecraft.world.inventory.AnvilMenu;
import org.bukkit.craftbukkit.inventory.CraftInventoryAnvil;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.view.AnvilView;
import org.jetbrains.annotations.Nullable;

public class CraftAnvilView extends CraftInventoryView<AnvilMenu> implements AnvilView {

    public CraftAnvilView(final HumanEntity player, final Inventory viewing, final AnvilMenu container) {
        super(player, viewing, container);
    }

    @Nullable
    @Override
    public String getRenameText() {
        return this.container.itemName;
    }

    @Override
    public int getRepairItemCountCost() {
        return this.container.repairItemCountCost;
    }

    @Override
    public int getRepairCost() {
        return this.container.getCost();
    }

    @Override
    public int getMaximumRepairCost() {
        return this.container.maximumRepairCost;
    }

    @Override
    public void setRepairItemCountCost(final int cost) {
        this.container.repairItemCountCost = cost;
    }

    @Override
    public void setRepairCost(final int cost) {
        this.container.cost.set(cost);
    }

    @Override
    public void setMaximumRepairCost(final int cost) {
        this.container.maximumRepairCost = cost;
    }

    // Paper start
    @Override
    public boolean bypassesEnchantmentLevelRestriction() {
        return this.container.bypassEnchantmentLevelRestriction;
    }

    @Override
    public void bypassEnchantmentLevelRestriction(final boolean bypassEnchantmentLevelRestriction) {
        this.container.bypassEnchantmentLevelRestriction = bypassEnchantmentLevelRestriction;
    }
    // Paper end

    public void updateFromLegacy(CraftInventoryAnvil legacy) {
        if (legacy.isRepairCostSet()) {
            this.setRepairCost(legacy.getRepairCost());
        }

        if (legacy.isRepairCostAmountSet()) {
            this.setRepairItemCountCost(legacy.getRepairCostAmount());
        }

        if (legacy.isMaximumRepairCostSet()) {
            this.setMaximumRepairCost(legacy.getMaximumRepairCost());
        }
    }

    // Purpur start - Anvil API
    @Override
    public boolean canBypassCost() {
        return this.container.bypassCost;
    }

    @Override
    public void setBypassCost(boolean bypassCost) {
        this.container.bypassCost = bypassCost;
    }

    @Override
    public boolean canDoUnsafeEnchants() {
        return this.container.canDoUnsafeEnchants;
    }

    @Override
    public void setDoUnsafeEnchants(boolean canDoUnsafeEnchants) {
        this.container.canDoUnsafeEnchants = canDoUnsafeEnchants;
    }
    // Purpur end - Anvil API
}
