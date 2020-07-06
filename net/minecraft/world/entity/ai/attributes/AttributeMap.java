package net.minecraft.world.entity.ai.attributes;

import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class AttributeMap {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<Holder<Attribute>, AttributeInstance> attributes = new Object2ObjectOpenHashMap<>();
    private final Set<AttributeInstance> attributesToSync = new ObjectOpenHashSet<>();
    private final Set<AttributeInstance> attributesToUpdate = new ObjectOpenHashSet<>();
    private final AttributeSupplier supplier;
    private final net.minecraft.world.entity.LivingEntity entity; // Purpur - Ridables

    public AttributeMap(AttributeSupplier supplier) {
        // Purpur start - Ridables
        this(supplier, null);
    }
    public AttributeMap(AttributeSupplier defaultAttributes, net.minecraft.world.entity.LivingEntity entity) {
        this.entity = entity;
        // Purpur end - Ridables
        this.supplier = defaultAttributes;
    }

    private void onAttributeModified(AttributeInstance instance) {
        this.attributesToUpdate.add(instance);
        if (instance.getAttribute().value().isClientSyncable() && (entity == null || entity.shouldSendAttribute(instance.getAttribute().value()))) { // Purpur - Ridables
            this.attributesToSync.add(instance);
        }
    }

    public Set<AttributeInstance> getAttributesToSync() {
        return this.attributesToSync;
    }

    public Set<AttributeInstance> getAttributesToUpdate() {
        return this.attributesToUpdate;
    }

    public Collection<AttributeInstance> getSyncableAttributes() {
        return this.attributes.values().stream().filter(instance -> instance.getAttribute().value().isClientSyncable() && (entity == null || entity.shouldSendAttribute(instance.getAttribute().value()))).collect(Collectors.toList()); // Purpur - Ridables
    }

    @Nullable
    public AttributeInstance getInstance(Holder<Attribute> attribute) {
        return this.attributes.computeIfAbsent(attribute, holder -> this.supplier.createInstance(this::onAttributeModified, (Holder<Attribute>)holder));
    }

    public boolean hasAttribute(Holder<Attribute> attribute) {
        return this.attributes.get(attribute) != null || this.supplier.hasAttribute(attribute);
    }

    public boolean hasModifier(Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getModifier(id) != null : this.supplier.hasModifier(attribute, id);
    }

    public double getValue(Holder<Attribute> attribute) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getValue() : this.supplier.getValue(attribute);
    }

    public double getBaseValue(Holder<Attribute> attribute) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getBaseValue() : this.supplier.getBaseValue(attribute);
    }

    public double getModifierValue(Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getModifier(id).amount() : this.supplier.getModifierValue(attribute, id);
    }

    public void addTransientAttributeModifiers(Multimap<Holder<Attribute>, AttributeModifier> modifiers) {
        modifiers.forEach((attribute, modifier) -> {
            AttributeInstance instance = this.getInstance((Holder<Attribute>)attribute);
            if (instance != null) {
                instance.removeModifier(modifier.id());
                instance.addTransientModifier(modifier);
            }
        });
    }

    public void removeAttributeModifiers(Multimap<Holder<Attribute>, AttributeModifier> modifiers) {
        modifiers.asMap().forEach((holder, collection) -> {
            AttributeInstance attributeInstance = this.attributes.get(holder);
            if (attributeInstance != null) {
                collection.forEach(attributeModifier -> attributeInstance.removeModifier(attributeModifier.id()));
            }
        });
    }

    public void assignAllValues(AttributeMap map) {
        map.attributes.values().forEach(attribute -> {
            AttributeInstance instance = this.getInstance(attribute.getAttribute());
            if (instance != null) {
                instance.replaceFrom(attribute);
            }
        });
    }

    public void assignBaseValues(AttributeMap map) {
        map.attributes.values().forEach(attribute -> {
            AttributeInstance instance = this.getInstance(attribute.getAttribute());
            if (instance != null) {
                instance.setBaseValue(attribute.getBaseValue());
            }
        });
    }

    public void assignPermanentModifiers(AttributeMap map) {
        map.attributes.values().forEach(attribute -> {
            AttributeInstance instance = this.getInstance(attribute.getAttribute());
            if (instance != null) {
                instance.addPermanentModifiers(attribute.getPermanentModifiers());
            }
        });
    }

    public boolean resetBaseValue(Holder<Attribute> attribute) {
        if (!this.supplier.hasAttribute(attribute)) {
            return false;
        } else {
            AttributeInstance attributeInstance = this.attributes.get(attribute);
            if (attributeInstance != null) {
                attributeInstance.setBaseValue(this.supplier.getBaseValue(attribute));
            }

            return true;
        }
    }

    public ListTag save() {
        ListTag listTag = new ListTag();

        for (AttributeInstance attributeInstance : this.attributes.values()) {
            listTag.add(attributeInstance.save());
        }

        return listTag;
    }

    public void load(ListTag nbt) {
        for (int i = 0; i < nbt.size(); i++) {
            CompoundTag compound = nbt.getCompound(i);
            String string = compound.getString("id");
            ResourceLocation resourceLocation = ResourceLocation.tryParse(string);
            if (resourceLocation != null) {
                Util.ifElse(BuiltInRegistries.ATTRIBUTE.get(resourceLocation), reference -> {
                    AttributeInstance instance = this.getInstance(reference);
                    if (instance != null) {
                        instance.load(compound);
                    }
                }, () -> LOGGER.warn("Ignoring unknown attribute '{}'", resourceLocation));
            } else {
                LOGGER.warn("Ignoring malformed attribute '{}'", string);
            }
        }
    }

    // Paper - start - living entity allow attribute registration
    public void registerAttribute(Holder<Attribute> attributeBase) {
        AttributeInstance attributeModifiable = new AttributeInstance(attributeBase, AttributeInstance::getAttribute);
        attributes.put(attributeBase, attributeModifiable);
    }
    // Paper - end - living entity allow attribute registration

}
