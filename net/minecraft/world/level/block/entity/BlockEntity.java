package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public abstract class BlockEntity {
    static boolean ignoreBlockEntityUpdates; // Paper - Perf: Optimize Hoppers
    // CraftBukkit start - data containers
    private static final org.bukkit.craftbukkit.persistence.CraftPersistentDataTypeRegistry DATA_TYPE_REGISTRY = new org.bukkit.craftbukkit.persistence.CraftPersistentDataTypeRegistry();
    public org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer persistentDataContainer;
    // CraftBukkit end
    private static final Logger LOGGER = LogUtils.getLogger();
    private final BlockEntityType<?> type;
    @Nullable
    protected Level level;
    protected final BlockPos worldPosition;
    protected boolean remove;
    private BlockState blockState;
    private DataComponentMap components = DataComponentMap.EMPTY;

    public BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        this.type = type;
        this.worldPosition = pos.immutable();
        this.validateBlockState(blockState);
        this.blockState = blockState;
        this.persistentDataContainer = new org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer(DATA_TYPE_REGISTRY); // Paper - always init
    }

    private void validateBlockState(BlockState state) {
        if (!this.isValidBlockState(state)) {
            throw new IllegalStateException("Invalid block entity " + this.getNameForReporting() + " state at " + this.worldPosition + ", got " + state);
        }
    }

    public boolean isValidBlockState(BlockState state) {
        return this.type.isValid(state);
    }

    public static BlockPos getPosFromTag(CompoundTag tag) {
        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    @Nullable
    public Level getLevel() {
        return this.level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public boolean hasLevel() {
        return this.level != null;
    }

    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        // Paper start - read persistent data container
        this.persistentDataContainer.clear(); // Paper - clear instead of init

        net.minecraft.nbt.Tag persistentDataTag = tag.get("PublicBukkitValues");
        if (persistentDataTag instanceof CompoundTag) {
            this.persistentDataContainer.putAll((CompoundTag) persistentDataTag);
        }
        // Paper end - read persistent data container

        // Purpur start - Persistent BlockEntity Lore and DisplayName
        if (tag.contains("Purpur.persistentLore")) {
            net.minecraft.world.item.component.ItemLore.CODEC.decode(net.minecraft.nbt.NbtOps.INSTANCE, tag.getCompound("Purpur.persistentLore")).result()
                .ifPresent(tag1 -> this.persistentLore = tag1.getFirst());
        }
        // Purpur end - Persistent BlockEntity Lore and DisplayName

    }

    public final void loadWithComponents(CompoundTag tag, HolderLookup.Provider registries) {
        this.loadAdditional(tag, registries);
        BlockEntity.ComponentHelper.COMPONENTS_CODEC
            .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
            .resultOrPartial(string -> LOGGER.warn("Failed to load components: {}", string))
            .ifPresent(components -> this.components = components);
    }

    public final void loadCustomOnly(CompoundTag tag, HolderLookup.Provider registries) {
        this.loadAdditional(tag, registries);
    }

    // Purpur start - Persistent BlockEntity Lore and DisplayName
    protected void saveAdditional(CompoundTag nbt) {
        if (this.persistentLore != null) {
            net.minecraft.world.item.component.ItemLore.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, this.persistentLore).result()
                .ifPresent(tag -> nbt.put("Purpur.persistentLore", tag));
        }
    }
    // Purpur end - Persistent BlockEntity Lore and DisplayName

    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    }

    public final CompoundTag saveWithFullMetadata(HolderLookup.Provider registries) {
        CompoundTag compoundTag = this.saveWithoutMetadata(registries);
        this.saveMetadata(compoundTag);
        return compoundTag;
    }

    public final CompoundTag saveWithId(HolderLookup.Provider registries) {
        CompoundTag compoundTag = this.saveWithoutMetadata(registries);
        this.saveId(compoundTag);
        return compoundTag;
    }

    public final CompoundTag saveWithoutMetadata(HolderLookup.Provider registries) {
        CompoundTag compoundTag = new CompoundTag();
        this.saveAdditional(compoundTag, registries);
        BlockEntity.ComponentHelper.COMPONENTS_CODEC
            .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this.components)
            .resultOrPartial(string -> LOGGER.warn("Failed to save components: {}", string))
            .ifPresent(tag -> compoundTag.merge((CompoundTag)tag));
        // CraftBukkit start - store container
        if (this.persistentDataContainer != null && !this.persistentDataContainer.isEmpty()) {
            compoundTag.put("PublicBukkitValues", this.persistentDataContainer.toTagCompound());
        }
        // CraftBukkit end
        return compoundTag;
    }

    public final CompoundTag saveCustomOnly(HolderLookup.Provider registries) {
        CompoundTag compoundTag = new CompoundTag();
        this.saveAdditional(compoundTag, registries);
        // Paper start - store PDC here as well
        if (this.persistentDataContainer != null && !this.persistentDataContainer.isEmpty()) {
            compoundTag.put("PublicBukkitValues", this.persistentDataContainer.toTagCompound());
        }
        // Paper end
        return compoundTag;
    }

    public final CompoundTag saveCustomAndMetadata(HolderLookup.Provider registries) {
        CompoundTag compoundTag = this.saveCustomOnly(registries);
        this.saveMetadata(compoundTag);
        return compoundTag;
    }

    public void saveId(CompoundTag tag) {
        ResourceLocation key = BlockEntityType.getKey(this.getType());
        if (key == null) {
            throw new RuntimeException(this.getClass() + " is missing a mapping! This is a bug!");
        } else {
            tag.putString("id", key.toString());
        }
    }

    public static void addEntityType(CompoundTag tag, BlockEntityType<?> entityType) {
        tag.putString("id", BlockEntityType.getKey(entityType).toString());
    }

    private void saveMetadata(CompoundTag tag) {
        this.saveId(tag);
        tag.putInt("x", this.worldPosition.getX());
        tag.putInt("y", this.worldPosition.getY());
        tag.putInt("z", this.worldPosition.getZ());
    }

    @Nullable
    public static BlockEntity loadStatic(BlockPos pos, BlockState state, CompoundTag tag, HolderLookup.Provider registries) {
        String string = tag.getString("id");
        ResourceLocation resourceLocation = ResourceLocation.tryParse(string);
        if (resourceLocation == null) {
            LOGGER.error("Block entity has invalid type: {}", string);
            return null;
        } else {
            return BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(resourceLocation).map(blockEntityType -> {
                try {
                    return blockEntityType.create(pos, state);
                } catch (Throwable var5x) {
                    LOGGER.error("Failed to create block entity {}", string, var5x);
                    return null;
                }
            }).map(blockEntity -> {
                try {
                    blockEntity.loadWithComponents(tag, registries);
                    return (BlockEntity)blockEntity;
                } catch (Throwable var5x) {
                    LOGGER.error("Failed to load data for block entity {}", string, var5x);
                    return null;
                }
            }).orElseGet(() -> {
                LOGGER.warn("Skipping BlockEntity with id {}", string);
                return null;
            });
        }
    }

    public void setChanged() {
        if (this.level != null) {
            if (ignoreBlockEntityUpdates) return; // Paper - Perf: Optimize Hoppers
            setChanged(this.level, this.worldPosition, this.blockState);
        }
    }

    protected static void setChanged(Level level, BlockPos pos, BlockState state) {
        level.blockEntityChanged(pos);
        if (!state.isAir()) {
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    public BlockPos getBlockPos() {
        return this.worldPosition;
    }

    public BlockState getBlockState() {
        return this.blockState;
    }

    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return null;
    }

    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return new CompoundTag();
    }

    public boolean isRemoved() {
        return this.remove;
    }

    public void setRemoved() {
        this.remove = true;
    }

    public void clearRemoved() {
        this.remove = false;
    }

    public boolean triggerEvent(int id, int type) {
        return false;
    }

    public void fillCrashReportCategory(CrashReportCategory reportCategory) {
        reportCategory.setDetail("Name", this::getNameForReporting);
        if (this.level != null) {
            // Paper start - Prevent block entity and entity crashes
            BlockState block = this.getBlockState();
            if (block != null) {
                CrashReportCategory.populateBlockDetails(reportCategory, this.level, this.worldPosition, block);
            }
            // Paper end - Prevent block entity and entity crashes
            CrashReportCategory.populateBlockDetails(reportCategory, this.level, this.worldPosition, this.level.getBlockState(this.worldPosition));
        }
    }

    private String getNameForReporting() {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(this.getType()) + " // " + this.getClass().getCanonicalName();
    }

    public BlockEntityType<?> getType() {
        return this.type;
    }

    @Deprecated
    public void setBlockState(BlockState blockState) {
        this.validateBlockState(blockState);
        this.blockState = blockState;
    }

    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
    }

    public final void applyComponentsFromItemStack(ItemStack stack) {
        this.applyComponents(stack.getPrototype(), stack.getComponentsPatch());
    }

    public final void applyComponents(DataComponentMap components, DataComponentPatch patch) {
        // CraftBukkit start
        this.applyComponentsSet(components, patch);
    }

    public final Set<DataComponentType<?>> applyComponentsSet(DataComponentMap components, DataComponentPatch patch) {
        // CraftBukkit end
        final Set<DataComponentType<?>> set = new HashSet<>();
        set.add(DataComponents.BLOCK_ENTITY_DATA);
        set.add(DataComponents.BLOCK_STATE);
        final DataComponentMap dataComponentMap = PatchedDataComponentMap.fromPatch(components, patch);
        this.applyImplicitComponents(new BlockEntity.DataComponentInput() {
            @Nullable
            @Override
            public <T> T get(DataComponentType<T> component) {
                set.add(component);
                return dataComponentMap.get(component);
            }

            @Override
            public <T> T getOrDefault(DataComponentType<? extends T> component, T defaultValue) {
                set.add(component);
                return dataComponentMap.getOrDefault(component, defaultValue);
            }
        });
        DataComponentPatch dataComponentPatch = patch.forget(set::contains);
        this.components = dataComponentPatch.split().added();
        // CraftBukkit start
        set.remove(DataComponents.BLOCK_ENTITY_DATA); // Remove as never actually added by applyImplicitComponents
        return set;
        // CraftBukkit end
    }

    protected void collectImplicitComponents(DataComponentMap.Builder components) {
    }

    @Deprecated
    public void removeComponentsFromTag(CompoundTag tag) {
    }

    public final DataComponentMap collectComponents() {
        DataComponentMap.Builder builder = DataComponentMap.builder();
        builder.addAll(this.components);
        this.collectImplicitComponents(builder);
        return builder.build();
    }

    public DataComponentMap components() {
        return this.components;
    }

    public void setComponents(DataComponentMap components) {
        this.components = components;
    }

    @Nullable
    public static Component parseCustomNameSafe(String customName, HolderLookup.Provider registries) {
        try {
            return Component.Serializer.fromJson(customName, registries);
        } catch (Exception var3) {
            LOGGER.warn("Failed to parse custom name from string '{}', discarding", customName, var3);
            return null;
        }
    }

    // CraftBukkit start - add method
    public org.bukkit.inventory.InventoryHolder getOwner() {
        // Paper start
        return getOwner(true);
    }
    public org.bukkit.inventory.InventoryHolder getOwner(boolean useSnapshot) {
        // Paper end
        if (this.level == null) return null;
        org.bukkit.block.Block block = this.level.getWorld().getBlockAt(this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ());
        // if (block.getType() == org.bukkit.Material.AIR) return null; // Paper - actually get the tile entity if it still exists
        org.bukkit.block.BlockState state = block.getState(useSnapshot); // Paper
        return state instanceof final org.bukkit.inventory.InventoryHolder inventoryHolder ? inventoryHolder : null;
    }
    // CraftBukkit end

    // Paper start - Sanitize sent data
    public CompoundTag sanitizeSentNbt(CompoundTag tag) {
        tag.remove("PublicBukkitValues");

        return tag;
    }
    // Paper end - Sanitize sent data


    static class ComponentHelper {
        public static final Codec<DataComponentMap> COMPONENTS_CODEC = DataComponentMap.CODEC.optionalFieldOf("components", DataComponentMap.EMPTY).codec();

        private ComponentHelper() {
        }
    }

    protected interface DataComponentInput {
        @Nullable
        <T> T get(DataComponentType<T> component);

        <T> T getOrDefault(DataComponentType<? extends T> component, T defaultValue);
    }
    // Purpur start - Persistent BlockEntity Lore and DisplayName
    @Nullable
    private net.minecraft.world.item.component.ItemLore persistentLore = null;

    public void setPersistentLore(net.minecraft.world.item.component.ItemLore lore) {
        this.persistentLore = lore;
    }

    public @org.jetbrains.annotations.Nullable net.minecraft.world.item.component.ItemLore getPersistentLore() {
        return this.persistentLore;
    }
    // Purpur end - Persistent BlockEntity Lore and DisplayName
}
