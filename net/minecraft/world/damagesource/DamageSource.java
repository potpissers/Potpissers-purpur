package net.minecraft.world.damagesource;

import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class DamageSource {
    private final Holder<DamageType> type;
    @Nullable
    private final Entity causingEntity;
    @Nullable
    private final Entity directEntity;
    @Nullable
    private final Vec3 damageSourcePosition;
    // CraftBukkit start
    @Nullable
    private org.bukkit.block.Block directBlock; // The block that caused the damage. damageSourcePosition is not used for all block damages
    @Nullable
    private org.bukkit.block.BlockState directBlockState; // The block state of the block relevant to this damage source
    private boolean sweep = false;
    private boolean melting = false;
    private boolean poison = false;
    private boolean scissors = false; // Purpur - Dont run with scissors!
    private boolean stonecutter = false; // Purpur - Stonecutter damage
    @Nullable
    private Entity customEventDamager = null; // This field is a helper for when causing entity damage is not set by vanilla // Paper - fix DamageSource API

    public DamageSource sweep() {
        this.sweep = true;
        return this;
    }

    public boolean isSweep() {
        return this.sweep;
    }

    public DamageSource melting() {
        this.melting = true;
        return this;
    }

    public boolean isMelting() {
        return this.melting;
    }

    public DamageSource poison() {
        this.poison = true;
        return this;
    }

    public boolean isPoison() {
        return this.poison;
    }

    // Purpur start - Dont run with scissors!
    public DamageSource scissors() {
        this.scissors = true;
        return this;
    }

    public boolean isScissors() {
        return this.scissors;
    }
    // Purpur end - Dont run with scissors!
    // Purpur start -  - Stonecutter damage
    public DamageSource stonecutter() {
        this.stonecutter = true;
        return this;
    }

    public boolean isStonecutter() {
        return this.stonecutter;
    }
    // Purpur end - Stonecutter damage

    // Paper start - fix DamageSource API
    @Nullable
    public Entity getCustomEventDamager() {
        return (this.customEventDamager != null) ? this.customEventDamager : this.directEntity;
    }

    public DamageSource customEventDamager(Entity entity) {
        if (this.directEntity != null) {
            throw new IllegalStateException("Cannot set custom event damager when direct entity is already set (report a bug to Paper)");
        }
        DamageSource damageSource = this.cloneInstance();
        damageSource.customEventDamager = entity;
        // Paper end - fix DamageSource API
        return damageSource;
    }

    @Nullable
    public org.bukkit.block.Block getDirectBlock() {
        return this.directBlock;
    }

    public DamageSource directBlock(@Nullable net.minecraft.world.level.Level world, @Nullable net.minecraft.core.BlockPos blockPosition) {
        if (blockPosition == null || world == null) {
            return this;
        }
        return this.directBlock(org.bukkit.craftbukkit.block.CraftBlock.at(world, blockPosition));
    }

    public DamageSource directBlock(@Nullable org.bukkit.block.Block block) {
        if (block == null) {
            return this;
        }
        // Cloning the instance lets us return unique instances of DamageSource without affecting constants defined in DamageSources
        DamageSource damageSource = this.cloneInstance();
        damageSource.directBlock = block;
        return damageSource;
    }

    @Nullable
    public org.bukkit.block.BlockState getDirectBlockState() {
        return this.directBlockState;
    }

    public DamageSource directBlockState(@Nullable org.bukkit.block.BlockState blockState) {
        if (blockState == null) {
            return this;
        }
        // Cloning the instance lets us return unique instances of DamageSource without affecting constants defined in DamageSources
        DamageSource damageSource = this.cloneInstance();
        damageSource.directBlockState = blockState;
        return damageSource;
    }

    private DamageSource cloneInstance() {
        DamageSource damageSource = new DamageSource(this.type, this.directEntity, this.causingEntity, this.damageSourcePosition);
        damageSource.directBlock = this.getDirectBlock();
        damageSource.directBlockState = this.getDirectBlockState();
        damageSource.sweep = this.isSweep();
        damageSource.poison = this.isPoison();
        damageSource.melting = this.isMelting();
        damageSource.scissors = this.isScissors(); // Purpur - Dont run with scissors!
        damageSource.stonecutter = this.isStonecutter(); // Purpur - Stonecutter damage
        return damageSource;
    }
    // CraftBukkit end

    @Override
    public String toString() {
        return "DamageSource (" + this.type().msgId() + ")";
    }

    public float getFoodExhaustion() {
        return this.type().exhaustion();
    }

    public boolean isDirect() {
        return this.causingEntity == this.directEntity;
    }

    public DamageSource(Holder<DamageType> type, @Nullable Entity directEntity, @Nullable Entity causingEntity, @Nullable Vec3 damageSourcePosition) {
        this.type = type;
        this.causingEntity = causingEntity;
        this.directEntity = directEntity;
        this.damageSourcePosition = damageSourcePosition;
    }

    public DamageSource(Holder<DamageType> type, @Nullable Entity directEntity, @Nullable Entity causingEntity) {
        this(type, directEntity, causingEntity, null);
    }

    public DamageSource(Holder<DamageType> type, Vec3 damageSourcePosition) {
        this(type, null, null, damageSourcePosition);
    }

    public DamageSource(Holder<DamageType> type, @Nullable Entity entity) {
        this(type, entity, entity);
    }

    public DamageSource(Holder<DamageType> type) {
        this(type, null, null, null);
    }

    @Nullable
    public Entity getDirectEntity() {
        return this.directEntity;
    }

    @Nullable
    public Entity getEntity() {
        return this.causingEntity;
    }

    @Nullable
    public ItemStack getWeaponItem() {
        return this.directEntity != null ? this.directEntity.getWeaponItem() : null;
    }

    public Component getLocalizedDeathMessage(LivingEntity livingEntity) {
        String string = "death.attack." + this.type().msgId();
        if (this.causingEntity == null && this.directEntity == null) {
            LivingEntity killCredit = livingEntity.getKillCredit();
            String string1 = string + ".player";
            return killCredit != null
                ? Component.translatable(string1, livingEntity.getDisplayName(), killCredit.getDisplayName())
                : Component.translatable(string, livingEntity.getDisplayName());
        } else {
            Component component = this.causingEntity == null ? this.directEntity.getDisplayName() : this.causingEntity.getDisplayName();
            ItemStack itemStack = this.causingEntity instanceof LivingEntity livingEntity1 ? livingEntity1.getMainHandItem() : ItemStack.EMPTY;
            return !itemStack.isEmpty() && (org.purpurmc.purpur.PurpurConfig.playerDeathsAlwaysShowItem || itemStack.has(DataComponents.CUSTOM_NAME)) // Purpur - always show item in player death messages
                ? Component.translatable(string + ".item", livingEntity.getDisplayName(), component, itemStack.getDisplayName())
                : Component.translatable(string, livingEntity.getDisplayName(), component);
        }
    }

    // Purpur start - Component related conveniences
    public Component getLocalizedDeathMessage(String str, LivingEntity entity) {
        net.kyori.adventure.text.Component name = io.papermc.paper.adventure.PaperAdventure.asAdventure(entity.getDisplayName());
        net.kyori.adventure.text.minimessage.tag.resolver.TagResolver template = net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("player", name);
        net.kyori.adventure.text.Component component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(str, template);
        return io.papermc.paper.adventure.PaperAdventure.asVanilla(component);
    }
    // Purpur end - Component related conveniences

    public String getMsgId() {
        return this.type().msgId();
    }

    public boolean scalesWithDifficulty() {
        return switch (this.type().scaling()) {
            case NEVER -> false;
            case WHEN_CAUSED_BY_LIVING_NON_PLAYER -> this.causingEntity instanceof LivingEntity && !(this.causingEntity instanceof Player);
            case ALWAYS -> true;
        };
    }

    public boolean isCreativePlayer() {
        return this.getEntity() instanceof Player player && player.getAbilities().instabuild;
    }

    @Nullable
    public Vec3 getSourcePosition() {
        if (this.damageSourcePosition != null) {
            return this.damageSourcePosition;
        } else {
            return this.directEntity != null ? this.directEntity.position() : null;
        }
    }

    @Nullable
    public Vec3 sourcePositionRaw() {
        return this.damageSourcePosition;
    }

    public boolean is(TagKey<DamageType> damageTypeKey) {
        return this.type.is(damageTypeKey);
    }

    public boolean is(ResourceKey<DamageType> damageTypeKey) {
        return this.type.is(damageTypeKey);
    }

    public DamageType type() {
        return this.type.value();
    }

    public Holder<DamageType> typeHolder() {
        return this.type;
    }

    // Paper start - add critical damage API
    private boolean critical;
    public boolean isCritical() {
        return this.critical;
    }
    public DamageSource critical() {
        return this.critical(true);
    }
    public DamageSource critical(boolean critical) {
        this.critical = critical;
        return this;
    }
    // Paper end - add critical damage API
}
