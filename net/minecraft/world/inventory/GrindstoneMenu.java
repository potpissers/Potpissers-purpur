package net.minecraft.world.inventory;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class GrindstoneMenu extends AbstractContainerMenu {
    // CraftBukkit start
    private org.bukkit.craftbukkit.inventory.CraftInventoryView bukkitEntity = null;
    private org.bukkit.entity.Player player;

    @Override
    public org.bukkit.craftbukkit.inventory.CraftInventoryView getBukkitView() {
        if (this.bukkitEntity != null) {
            return this.bukkitEntity;
        }

        org.bukkit.craftbukkit.inventory.CraftInventoryGrindstone inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryGrindstone(this.repairSlots, this.resultSlots);
        this.bukkitEntity = new org.bukkit.craftbukkit.inventory.CraftInventoryView(this.player, inventory, this);
        return this.bukkitEntity;
    }
    // CraftBukkit end
    public static final int MAX_NAME_LENGTH = 35;
    public static final int INPUT_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private static final int INV_SLOT_START = 3;
    private static final int INV_SLOT_END = 30;
    private static final int USE_ROW_SLOT_START = 30;
    private static final int USE_ROW_SLOT_END = 39;
    private final Container resultSlots; // Paper - Add missing InventoryHolders - move down
    final Container repairSlots; // Paper - Add missing InventoryHolders - move down
    private final ContainerLevelAccess access;

    public GrindstoneMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public GrindstoneMenu(int containerId, Inventory playerInventory, final ContainerLevelAccess access) {
        super(MenuType.GRINDSTONE, containerId);
        // Paper start - Add missing InventoryHolders
        this.resultSlots = new ResultContainer(this.createBlockHolder(access)); // Paper - Add missing InventoryHolders
        this.repairSlots = new SimpleContainer(this.createBlockHolder(access), 2) { // Paper - Add missing InventoryHolders
            @Override
            public void setChanged() {
                super.setChanged();
                GrindstoneMenu.this.slotsChanged(this);
            }
            // CraftBukkit start
            @Override
            public org.bukkit.Location getLocation() {
                return access.getLocation();
            }
            // CraftBukkit end
        };
        // Paper end - Add missing InventoryHolders
        this.access = access;
        this.addSlot(new Slot(this.repairSlots, 0, 49, 19) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.isDamageableItem() || EnchantmentHelper.hasAnyEnchantments(stack);
            }
        });
        this.addSlot(new Slot(this.repairSlots, 1, 49, 40) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.isDamageableItem() || EnchantmentHelper.hasAnyEnchantments(stack);
            }
        });
        this.addSlot(new Slot(this.resultSlots, 2, 129, 34) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                access.execute((level, blockPos) -> {
                    ItemStack itemstack = activeQuickItem == null ? stack : activeQuickItem; // Purpur - Grindstone API
                    if (level instanceof ServerLevel) {
                        // Paper start - Fire BlockExpEvent on grindstone use
                        org.bukkit.event.block.BlockExpEvent event = new org.bukkit.event.block.BlockExpEvent(org.bukkit.craftbukkit.block.CraftBlock.at(level, blockPos), this.getExperienceAmount(level));
                        event.callEvent();
                        org.purpurmc.purpur.event.inventory.GrindstoneTakeResultEvent grindstoneTakeResultEvent = new org.purpurmc.purpur.event.inventory.GrindstoneTakeResultEvent(player.getBukkitEntity(), getBukkitView(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemstack), event.getExpToDrop()); grindstoneTakeResultEvent.callEvent(); // Purpur - Grindstone API
                        ExperienceOrb.award((ServerLevel) level, Vec3.atCenterOf(blockPos), grindstoneTakeResultEvent.getExperienceAmount(), org.bukkit.entity.ExperienceOrb.SpawnReason.GRINDSTONE, player); // Purpur - Grindstone API
                        // Paper end - Fire BlockExpEvent on grindstone use
                    }

                    level.levelEvent(1042, blockPos, 0);
                });
                GrindstoneMenu.this.repairSlots.setItem(0, ItemStack.EMPTY);
                GrindstoneMenu.this.repairSlots.setItem(1, ItemStack.EMPTY);
            }

            private int getExperienceAmount(Level level) {
                int i = 0;
                i += this.getExperienceFromItem(GrindstoneMenu.this.repairSlots.getItem(0));
                i += this.getExperienceFromItem(GrindstoneMenu.this.repairSlots.getItem(1));
                if (i > 0) {
                    int i1 = (int)Math.ceil(i / 2.0);
                    return i1 + level.random.nextInt(i1);
                } else {
                    return 0;
                }
            }

            private int getExperienceFromItem(ItemStack stack) {
                int i = 0;
                ItemEnchantments enchantmentsForCrafting = EnchantmentHelper.getEnchantmentsForCrafting(stack);

                for (Entry<Holder<Enchantment>> entry : enchantmentsForCrafting.entrySet()) {
                    Holder<Enchantment> holder = entry.getKey();
                    int intValue = entry.getIntValue();
                    if (!org.purpurmc.purpur.PurpurConfig.grindstoneIgnoredEnchants.contains(holder.value())) { // Purpur - Config for grindstones
                        i += holder.value().getMinCost(intValue);
                    }
                }

                return i;
            }
        });
        this.addStandardInventorySlots(playerInventory, 8, 84);
        this.player = (org.bukkit.entity.Player) playerInventory.player.getBukkitEntity(); // CraftBukkit
    }

    @Override
    public void slotsChanged(Container inventory) {
        super.slotsChanged(inventory);
        if (inventory == this.repairSlots) {
            this.createResult();
            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareResultEvent(this, RESULT_SLOT); // Paper - Add PrepareResultEvent
        }
    }

    private void createResult() {
        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareGrindstoneEvent(this.getBukkitView(), this.computeResult(this.repairSlots.getItem(0), this.repairSlots.getItem(1))); // CraftBukkit
        this.sendAllDataToRemote(); // CraftBukkit - SPIGOT-6686: Always send completed inventory to stay in sync with client
        this.broadcastChanges();
    }

    private ItemStack computeResult(ItemStack inputItem, ItemStack additionalItem) {
        boolean flag = !inputItem.isEmpty() || !additionalItem.isEmpty();
        if (!flag) {
            return ItemStack.EMPTY;
        } else if (inputItem.getCount() <= 1 && additionalItem.getCount() <= 1) {
            boolean flag1 = !inputItem.isEmpty() && !additionalItem.isEmpty();
            if (!flag1) {
                ItemStack itemStack = !inputItem.isEmpty() ? inputItem : additionalItem;
                return !EnchantmentHelper.hasAnyEnchantments(itemStack) ? ItemStack.EMPTY : this.removeNonCursesFrom(itemStack.copy());
            } else {
                return this.mergeItems(inputItem, additionalItem);
            }
        } else {
            return ItemStack.EMPTY;
        }
    }

    private ItemStack mergeItems(ItemStack inputItem, ItemStack additionalItem) {
        if (!inputItem.is(additionalItem.getItem())) {
            return ItemStack.EMPTY;
        } else {
            int max = Math.max(inputItem.getMaxDamage(), additionalItem.getMaxDamage());
            int i = inputItem.getMaxDamage() - inputItem.getDamageValue();
            int i1 = additionalItem.getMaxDamage() - additionalItem.getDamageValue();
            int i2 = i + i1 + max * 5 / 100;
            int i3 = 1;
            if (!inputItem.isDamageableItem()) {
                if (inputItem.getMaxStackSize() < 2 || !ItemStack.matches(inputItem, additionalItem)) {
                    return ItemStack.EMPTY;
                }

                i3 = 2;
            }

            ItemStack itemStack = inputItem.copyWithCount(i3);
            if (itemStack.isDamageableItem()) {
                itemStack.set(DataComponents.MAX_DAMAGE, max);
                itemStack.setDamageValue(Math.max(max - i2, 0));
            }

            this.mergeEnchantsFrom(itemStack, additionalItem);
            return this.removeNonCursesFrom(itemStack);
        }
    }

    private void mergeEnchantsFrom(ItemStack inputItem, ItemStack additionalItem) {
        EnchantmentHelper.updateEnchantments(inputItem, mutable -> {
            ItemEnchantments enchantmentsForCrafting = EnchantmentHelper.getEnchantmentsForCrafting(additionalItem);

            for (Entry<Holder<Enchantment>> entry : enchantmentsForCrafting.entrySet()) {
                Holder<Enchantment> holder = entry.getKey();
                if (!org.purpurmc.purpur.PurpurConfig.grindstoneIgnoredEnchants.contains(holder.value()) || mutable.getLevel(holder) == 0) { // Purpur - Config for grindstones
                    mutable.upgrade(holder, entry.getIntValue());
                }
            }
        });
    }

    // Purpur start - Config for grindstones
    private java.util.List<net.minecraft.core.component.DataComponentType<?>> GRINDSTONE_REMOVE_ATTRIBUTES_REMOVAL_LIST = java.util.List.of(
        // DataComponents.MAX_STACK_SIZE,
        // DataComponents.DAMAGE,
        // DataComponents.BLOCK_STATE,
        DataComponents.CUSTOM_DATA,
        // DataComponents.MAX_DAMAGE,
        // DataComponents.UNBREAKABLE,
        // DataComponents.CUSTOM_NAME,
        // DataComponents.ITEM_NAME,
        // DataComponents.LORE,
        // DataComponents.RARITY,
        // DataComponents.ENCHANTMENTS,
        // DataComponents.CAN_PLACE_ON,
        // DataComponents.CAN_BREAK,
        DataComponents.ATTRIBUTE_MODIFIERS,
        DataComponents.CUSTOM_MODEL_DATA,
        // DataComponents.HIDE_ADDITIONAL_TOOLTIP,
        // DataComponents.HIDE_TOOLTIP,
        // DataComponents.REPAIR_COST,
        // DataComponents.CREATIVE_SLOT_LOCK,
        // DataComponents.ENCHANTMENT_GLINT_OVERRIDE,
        // DataComponents.INTANGIBLE_PROJECTILE,
        // DataComponents.FOOD,
        // DataComponents.FIRE_RESISTANT,
        // DataComponents.TOOL,
        // DataComponents.STORED_ENCHANTMENTS,
        DataComponents.DYED_COLOR,
        // DataComponents.MAP_COLOR,
        // DataComponents.MAP_ID,
        // DataComponents.MAP_DECORATIONS,
        // DataComponents.MAP_POST_PROCESSING,
        // DataComponents.CHARGED_PROJECTILES,
        // DataComponents.BUNDLE_CONTENTS,
        // DataComponents.POTION_CONTENTS,
        DataComponents.SUSPICIOUS_STEW_EFFECTS
        // DataComponents.WRITABLE_BOOK_CONTENT,
        // DataComponents.WRITTEN_BOOK_CONTENT,
        // DataComponents.TRIM,
        // DataComponents.DEBUG_STICK_STATE,
        // DataComponents.ENTITY_DATA,
        // DataComponents.BUCKET_ENTITY_DATA,
        // DataComponents.BLOCK_ENTITY_DATA,
        // DataComponents.INSTRUMENT,
        // DataComponents.OMINOUS_BOTTLE_AMPLIFIER,
        // DataComponents.RECIPES,
        // DataComponents.LODESTONE_TRACKER,
        // DataComponents.FIREWORK_EXPLOSION,
        // DataComponents.FIREWORKS,
        // DataComponents.PROFILE,
        // DataComponents.NOTE_BLOCK_SOUND,
        // DataComponents.BANNER_PATTERNS,
        // DataComponents.BASE_COLOR,
        // DataComponents.POT_DECORATIONS,
        // DataComponents.CONTAINER,
        // DataComponents.BEES,
        // DataComponents.LOCK,
        // DataComponents.CONTAINER_LOOT,
    );
    // Purpur end - Config for grindstones
    private ItemStack removeNonCursesFrom(ItemStack item) {
        ItemEnchantments itemEnchantments = EnchantmentHelper.updateEnchantments(item, mutable -> mutable.removeIf(holder -> !org.purpurmc.purpur.PurpurConfig.grindstoneIgnoredEnchants.contains(holder.value()))); // Purpur - Config for grindstones
        if (item.is(Items.ENCHANTED_BOOK) && itemEnchantments.isEmpty()) {
            item = item.transmuteCopy(Items.BOOK);
        }

        int i = 0;

        for (int i1 = 0; i1 < itemEnchantments.size(); i1++) {
            i = AnvilMenu.calculateIncreasedRepairCost(i);
        }

        item.set(DataComponents.REPAIR_COST, i);

        // Purpur start - Config for grindstones
        net.minecraft.core.component.DataComponentPatch.Builder builder = net.minecraft.core.component.DataComponentPatch.builder();
        if (org.purpurmc.purpur.PurpurConfig.grindstoneRemoveAttributes) {
            item.getComponents().forEach(typedDataComponent -> {
                if (GRINDSTONE_REMOVE_ATTRIBUTES_REMOVAL_LIST.contains(typedDataComponent.type())) {
                    builder.remove(typedDataComponent.type());
                }
            });
        }
        if (org.purpurmc.purpur.PurpurConfig.grindstoneRemoveDisplay) {
            builder.remove(DataComponents.CUSTOM_NAME);
            builder.remove(DataComponents.LORE);
        }
        item.applyComponents(builder.build());
        // Purpur end - Config for grindstones

        return item;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, blockPos) -> this.clearContainer(player, this.repairSlots));
    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, player, Blocks.GRINDSTONE);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            itemStack = item.copy();
            ItemStack item1 = this.repairSlots.getItem(0);
            ItemStack item2 = this.repairSlots.getItem(1);
            if (index == 2) {
                if (!this.moveItemStackTo(item, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(item, itemStack);
            } else if (index != 0 && index != 1) {
                if (!item1.isEmpty() && !item2.isEmpty()) {
                    if (index >= 3 && index < 30) {
                        if (!this.moveItemStackTo(item, 30, 39, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (index >= 30 && index < 39 && !this.moveItemStackTo(item, 3, 30, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(item, 0, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(item, 3, 39, false)) {
                return ItemStack.EMPTY;
            }

            if (item.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (item.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }

            this.activeQuickItem = itemStack; // Purpur - Grindstone API
            slot.onTake(player, item);
            this.activeQuickItem = null; // Purpur - Grindstone API
        }

        return itemStack;
    }
}
