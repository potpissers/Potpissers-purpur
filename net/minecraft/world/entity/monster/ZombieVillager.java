package net.minecraft.world.entity.monster;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public class ZombieVillager extends Zombie implements VillagerDataHolder {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final EntityDataAccessor<Boolean> DATA_CONVERTING_ID = SynchedEntityData.defineId(ZombieVillager.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<VillagerData> DATA_VILLAGER_DATA = SynchedEntityData.defineId(
        ZombieVillager.class, EntityDataSerializers.VILLAGER_DATA
    );
    private static final int VILLAGER_CONVERSION_WAIT_MIN = 3600;
    private static final int VILLAGER_CONVERSION_WAIT_MAX = 6000;
    private static final int MAX_SPECIAL_BLOCKS_COUNT = 14;
    private static final int SPECIAL_BLOCK_RADIUS = 4;
    public int villagerConversionTime;
    @Nullable
    public UUID conversionStarter;
    @Nullable
    private Tag gossips;
    @Nullable
    private MerchantOffers tradeOffers;
    private int villagerXp;
    private int lastTick = net.minecraft.server.MinecraftServer.currentTick; // CraftBukkit - add field

    public ZombieVillager(EntityType<? extends ZombieVillager> entityType, Level level) {
        super(entityType, level);
        BuiltInRegistries.VILLAGER_PROFESSION
            .getRandom(this.random)
            .ifPresent(profession -> this.setVillagerData(this.getVillagerData().setProfession(profession.value())));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CONVERTING_ID, false);
        builder.define(DATA_VILLAGER_DATA, new VillagerData(VillagerType.PLAINS, VillagerProfession.NONE, 1));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        VillagerData.CODEC
            .encodeStart(NbtOps.INSTANCE, this.getVillagerData())
            .resultOrPartial(LOGGER::error)
            .ifPresent(tag -> compound.put("VillagerData", tag));
        if (this.tradeOffers != null) {
            compound.put(
                "Offers", MerchantOffers.CODEC.encodeStart(this.registryAccess().createSerializationContext(NbtOps.INSTANCE), this.tradeOffers).getOrThrow()
            );
        }

        if (this.gossips != null) {
            compound.put("Gossips", this.gossips);
        }

        compound.putInt("ConversionTime", this.isConverting() ? this.villagerConversionTime : -1);
        if (this.conversionStarter != null) {
            compound.putUUID("ConversionPlayer", this.conversionStarter);
        }

        compound.putInt("Xp", this.villagerXp);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("VillagerData", 10)) {
            DataResult<VillagerData> dataResult = VillagerData.CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, compound.get("VillagerData")));
            dataResult.resultOrPartial(LOGGER::error).ifPresent(this::setVillagerData);
        }

        if (compound.contains("Offers")) {
            MerchantOffers.CODEC
                .parse(this.registryAccess().createSerializationContext(NbtOps.INSTANCE), compound.get("Offers"))
                .resultOrPartial(Util.prefix("Failed to load offers: ", LOGGER::warn))
                .ifPresent(tradeOffers -> this.tradeOffers = tradeOffers);
        }

        if (compound.contains("Gossips", 9)) {
            this.gossips = compound.getList("Gossips", 10);
        }

        if (compound.contains("ConversionTime", 99) && compound.getInt("ConversionTime") > -1) {
            this.startConverting(compound.hasUUID("ConversionPlayer") ? compound.getUUID("ConversionPlayer") : null, compound.getInt("ConversionTime"));
        }

        if (compound.contains("Xp", 3)) {
            this.villagerXp = compound.getInt("Xp");
        }
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.isAlive() && this.isConverting()) {
            int conversionProgress = this.getConversionProgress();
            this.villagerConversionTime -= conversionProgress;
            if (this.villagerConversionTime <= 0) {
                this.finishConversion((ServerLevel)this.level());
            }
        }

        super.tick();
        this.lastTick = net.minecraft.server.MinecraftServer.currentTick; // CraftBukkit
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.is(Items.GOLDEN_APPLE)) {
            if (this.hasEffect(MobEffects.WEAKNESS) && level().purpurConfig.zombieVillagerCureEnabled) { // Purpur - Add option to disable zombie villagers cure
                itemInHand.consume(1, player);
                if (!this.level().isClientSide) {
                    this.startConverting(player.getUUID(), this.random.nextInt(level().purpurConfig.zombieVillagerCuringTimeMax - level().purpurConfig.zombieVillagerCuringTimeMin + 1) + level().purpurConfig.zombieVillagerCuringTimeMin); // Purpur - Customizable Zombie Villager curing times
                }

                return InteractionResult.SUCCESS_SERVER;
            } else {
                return InteractionResult.CONSUME;
            }
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return !this.isConverting() && this.villagerXp == 0;
    }

    public boolean isConverting() {
        return this.getEntityData().get(DATA_CONVERTING_ID);
    }

    public void startConverting(@Nullable UUID conversionStarter, int villagerConversionTime) {
    // Paper start - missing entity behaviour api - converting without entity event
        this.startConverting(conversionStarter, villagerConversionTime, true);
    }

    public void startConverting(@Nullable UUID conversionStarter, int villagerConversionTime, boolean broadcastEntityEvent) {
    // Paper end - missing entity behaviour api - converting without entity event
        this.conversionStarter = conversionStarter;
        this.villagerConversionTime = villagerConversionTime;
        this.getEntityData().set(DATA_CONVERTING_ID, true);
        // CraftBukkit start
        this.removeEffect(MobEffects.WEAKNESS, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.CONVERSION);
        this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, villagerConversionTime, Math.min(this.level().getDifficulty().getId() - 1, 0)), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.CONVERSION);
        // CraftBukkit end
        if (broadcastEntityEvent) this.level().broadcastEntityEvent(this, (byte)16); // Paper - missing entity behaviour api - converting without entity event
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 16) {
            if (!this.isSilent()) {
                this.level()
                    .playLocalSound(
                        this.getX(),
                        this.getEyeY(),
                        this.getZ(),
                        SoundEvents.ZOMBIE_VILLAGER_CURE,
                        this.getSoundSource(),
                        1.0F + this.random.nextFloat(),
                        this.random.nextFloat() * 0.7F + 0.3F,
                        false
                    );
            }
        } else {
            super.handleEntityEvent(id);
        }
    }

    private void finishConversion(ServerLevel serverLevel) {
        Villager converted = this.convertTo( // CraftBukkit
            EntityType.VILLAGER,
            ConversionParams.single(this, false, false),
            villager -> {
                for (EquipmentSlot equipmentSlot : this.dropPreservedEquipment(
                    serverLevel, itemStack -> !EnchantmentHelper.has(itemStack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)
                )) {
                    SlotAccess slot = villager.getSlot(equipmentSlot.getIndex() + 300);
                    slot.set(this.getItemBySlot(equipmentSlot));
                }
                this.forceDrops = false; // CraftBukkit

                villager.setVillagerData(this.getVillagerData());
                if (this.gossips != null) {
                    villager.setGossips(this.gossips);
                }

                if (this.tradeOffers != null) {
                    villager.setOffers(this.tradeOffers.copy());
                }

                villager.setVillagerXp(this.villagerXp);
                villager.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(villager.blockPosition()), EntitySpawnReason.CONVERSION, null);
                villager.refreshBrain(serverLevel);
                if (this.conversionStarter != null) {
                    Player playerByUuid = serverLevel.getGlobalPlayerByUUID(this.conversionStarter); // Paper - check global player list where appropriate
                    if (playerByUuid instanceof ServerPlayer) {
                        CriteriaTriggers.CURED_ZOMBIE_VILLAGER.trigger((ServerPlayer)playerByUuid, this, villager);
                        serverLevel.onReputationEvent(ReputationEventType.ZOMBIE_VILLAGER_CURED, playerByUuid, villager);
                    }
                }

                villager.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.CONVERSION); // CraftBukkit
                if (!this.isSilent()) {
                    serverLevel.levelEvent(null, 1027, this.blockPosition(), 0);
                }
                // CraftBukkit start
            }, org.bukkit.event.entity.EntityTransformEvent.TransformReason.CURED, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CURED // CraftBukkit
        );
        if (converted == null) {
            ((org.bukkit.entity.ZombieVillager) this.getBukkitEntity()).setConversionTime(-1); // SPIGOT-5208: End conversion to stop event spam
        }
        // CraftBukkit end
    }

    @VisibleForTesting
    public void setVillagerConversionTime(int villagerConversionTime) {
        this.villagerConversionTime = villagerConversionTime;
    }

    private int getConversionProgress() {
        int i = 1;
        if (this.random.nextFloat() < 0.01F) {
            int i1 = 0;
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (int i2 = (int)this.getX() - 4; i2 < (int)this.getX() + 4 && i1 < 14; i2++) {
                for (int i3 = (int)this.getY() - 4; i3 < (int)this.getY() + 4 && i1 < 14; i3++) {
                    for (int i4 = (int)this.getZ() - 4; i4 < (int)this.getZ() + 4 && i1 < 14; i4++) {
                        BlockState blockState = this.level().getBlockState(mutableBlockPos.set(i2, i3, i4));
                        if (blockState.is(Blocks.IRON_BARS) || blockState.getBlock() instanceof BedBlock) {
                            if (this.random.nextFloat() < 0.3F) {
                                i++;
                            }

                            i1++;
                        }
                    }
                }
            }
        }

        return i;
    }

    @Override
    public float getVoicePitch() {
        return this.isBaby()
            ? (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 2.0F
            : (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_VILLAGER_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ZOMBIE_VILLAGER_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_VILLAGER_DEATH;
    }

    @Override
    public SoundEvent getStepSound() {
        return SoundEvents.ZOMBIE_VILLAGER_STEP;
    }

    @Override
    protected ItemStack getSkull() {
        return ItemStack.EMPTY;
    }

    public void setTradeOffers(MerchantOffers tradeOffers) {
        this.tradeOffers = tradeOffers;
    }

    public void setGossips(Tag gossips) {
        this.gossips = gossips;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        this.setVillagerData(this.getVillagerData().setType(VillagerType.byBiome(level.getBiome(this.blockPosition()))));
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    @Override
    public void setVillagerData(VillagerData data) {
        VillagerData villagerData = this.getVillagerData();
        if (villagerData.getProfession() != data.getProfession()) {
            this.tradeOffers = null;
        }

        this.entityData.set(DATA_VILLAGER_DATA, data);
    }

    @Override
    public VillagerData getVillagerData() {
        return this.entityData.get(DATA_VILLAGER_DATA);
    }

    public int getVillagerXp() {
        return this.villagerXp;
    }

    public void setVillagerXp(int villagerXp) {
        this.villagerXp = villagerXp;
    }
}
