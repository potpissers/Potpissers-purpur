package net.minecraft.world.entity.animal;

import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SuspiciousEffectHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class MushroomCow extends Cow implements Shearable, VariantHolder<MushroomCow.Variant> {
    private static final EntityDataAccessor<String> DATA_TYPE = SynchedEntityData.defineId(MushroomCow.class, EntityDataSerializers.STRING);
    private static final int MUTATE_CHANCE = 1024;
    private static final String TAG_STEW_EFFECTS = "stew_effects";
    @Nullable
    public SuspiciousStewEffects stewEffects;
    @Nullable
    private UUID lastLightningBoltUUID;

    public MushroomCow(EntityType<? extends MushroomCow> entityType, Level level) {
        super(entityType, level);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.mooshroomRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.mooshroomRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.mooshroomControllable;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.mooshroomMaxHealth);
    }
    // Purpur end - Configurable entity base attributes

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getBlockState(pos.below()).is(Blocks.MYCELIUM) ? 10.0F : level.getPathfindingCostFromLightLevels(pos);
    }

    public static boolean checkMushroomSpawnRules(
        EntityType<MushroomCow> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return level.getBlockState(pos.below()).is(BlockTags.MOOSHROOMS_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
        UUID uuid = lightning.getUUID();
        if (!uuid.equals(this.lastLightningBoltUUID)) {
            this.setVariant(this.getVariant() == MushroomCow.Variant.RED ? MushroomCow.Variant.BROWN : MushroomCow.Variant.RED);
            this.lastLightningBoltUUID = uuid;
            this.playSound(SoundEvents.MOOSHROOM_CONVERT, 2.0F, 1.0F);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TYPE, MushroomCow.Variant.RED.type);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.is(Items.BOWL) && !this.isBaby()) {
            boolean flag = false;
            ItemStack itemStack;
            if (this.stewEffects != null) {
                flag = true;
                itemStack = new ItemStack(Items.SUSPICIOUS_STEW);
                itemStack.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, this.stewEffects);
                this.stewEffects = null;
            } else {
                itemStack = new ItemStack(Items.MUSHROOM_STEW);
            }

            ItemStack itemStack1 = ItemUtils.createFilledResult(itemInHand, player, itemStack, false);
            player.setItemInHand(hand, itemStack1);
            SoundEvent soundEvent;
            if (flag) {
                soundEvent = SoundEvents.MOOSHROOM_MILK_SUSPICIOUSLY;
            } else {
                soundEvent = SoundEvents.MOOSHROOM_MILK;
            }

            this.playSound(soundEvent, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        } else if (itemInHand.is(Items.SHEARS) && this.readyForShearing()) {
            if (this.level() instanceof ServerLevel serverLevel) {
                // CraftBukkit start
                // Paper start - custom shear drops
                java.util.List<ItemStack> drops = this.generateDefaultDrops(serverLevel, itemInHand);
                org.bukkit.event.player.PlayerShearEntityEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.handlePlayerShearEntityEvent(player, this, itemInHand, hand, drops);
                if (event != null) {
                    if (event.isCancelled()) return tryRide(player, hand); // Purpur - Ridables
                    drops = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getDrops());
                    // Paper end - custom shear drops
                }
                // CraftBukkit end
                this.shear(serverLevel, SoundSource.PLAYERS, itemInHand, drops); // Paper - custom shear drops
                this.gameEvent(GameEvent.SHEAR, player);
                itemInHand.hurtAndBreak(1, player, getSlotForHand(hand));
            }

            return InteractionResult.SUCCESS;
        } else if (this.getVariant() == MushroomCow.Variant.BROWN) {
            Optional<SuspiciousStewEffects> effectsFromItemStack = this.getEffectsFromItemStack(itemInHand);
            if (effectsFromItemStack.isEmpty()) {
                return super.mobInteract(player, hand);
            } else {
                if (this.stewEffects != null) {
                    for (int i = 0; i < 2; i++) {
                        this.level()
                            .addParticle(
                                ParticleTypes.SMOKE,
                                this.getX() + this.random.nextDouble() / 2.0,
                                this.getY(0.5),
                                this.getZ() + this.random.nextDouble() / 2.0,
                                0.0,
                                this.random.nextDouble() / 5.0,
                                0.0
                            );
                    }
                } else {
                    itemInHand.consume(1, player);

                    for (int i = 0; i < 4; i++) {
                        this.level()
                            .addParticle(
                                ParticleTypes.EFFECT,
                                this.getX() + this.random.nextDouble() / 2.0,
                                this.getY(0.5),
                                this.getZ() + this.random.nextDouble() / 2.0,
                                0.0,
                                this.random.nextDouble() / 5.0,
                                0.0
                            );
                    }

                    this.stewEffects = effectsFromItemStack.get();
                    this.playSound(SoundEvents.MOOSHROOM_EAT, 2.0F, 1.0F);
                }

                return InteractionResult.SUCCESS;
            }
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    public void shear(ServerLevel level, SoundSource soundSource, ItemStack shears) {
        // Paper start - custom shear drops
        this.shear(level, soundSource, shears, this.generateDefaultDrops(level, shears));
    }

    @Override
    public java.util.List<ItemStack> generateDefaultDrops(final ServerLevel serverLevel, final ItemStack shears) {
        final java.util.List<ItemStack> drops = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
        this.dropFromShearingLootTable(serverLevel, BuiltInLootTables.SHEAR_MOOSHROOM, shears, (ignored, stack) -> {
            for (int i = 0; i < stack.getCount(); ++i) drops.add(stack.copyWithCount(1));
        });
        return drops;
    }

    @Override
    public void shear(ServerLevel level, SoundSource soundSource, ItemStack shears, java.util.List<ItemStack> drops) {
        // Paper end
        level.playSound(null, this, SoundEvents.MOOSHROOM_SHEAR, soundSource, 1.0F, 1.0F);
        this.convertTo(EntityType.COW, ConversionParams.single(this, false, false), mob -> {
            level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(0.5), this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            // Purpur start - Fix cow rotation when shearing mooshroom
            mob.copyPosition(this);
            mob.yBodyRot = this.yBodyRot;
            mob.setYHeadRot(this.getYHeadRot());
            mob.yRotO = this.yRotO;
            mob.xRotO = this.xRotO;
            // Purpur end - Fix cow rotation when shearing mooshroom
            // Paper start - custom shear drops; moved drop generation to separate method
            drops.forEach(drop -> {
                this.spawnAtLocation(level, new ItemEntity(this.level(), this.getX(), this.getY(1.0), this.getZ(), drop));
            });
            // Paper end - custom shear drops
        }, org.bukkit.event.entity.EntityTransformEvent.TransformReason.SHEARED, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SHEARED); // CraftBukkit
    }

    @Override
    public boolean readyForShearing() {
        return this.isAlive() && !this.isBaby();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putString("Type", this.getVariant().getSerializedName());
        if (this.stewEffects != null) {
            SuspiciousStewEffects.CODEC.encodeStart(NbtOps.INSTANCE, this.stewEffects).ifSuccess(tag -> compound.put("stew_effects", tag));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setVariant(MushroomCow.Variant.byName(compound.getString("Type")));
        if (compound.contains("stew_effects", 9)) {
            SuspiciousStewEffects.CODEC
                .parse(NbtOps.INSTANCE, compound.get("stew_effects"))
                .ifSuccess(suspiciousStewEffects -> this.stewEffects = suspiciousStewEffects);
        }
    }

    private Optional<SuspiciousStewEffects> getEffectsFromItemStack(ItemStack stack) {
        SuspiciousEffectHolder suspiciousEffectHolder = SuspiciousEffectHolder.tryGet(stack.getItem());
        return suspiciousEffectHolder != null ? Optional.of(suspiciousEffectHolder.getSuspiciousEffects()) : Optional.empty();
    }

    @Override
    public void setVariant(MushroomCow.Variant variant) {
        this.entityData.set(DATA_TYPE, variant.type);
    }

    @Override
    public MushroomCow.Variant getVariant() {
        return MushroomCow.Variant.byName(this.entityData.get(DATA_TYPE));
    }

    @Nullable
    @Override
    public MushroomCow getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        MushroomCow mushroomCow = EntityType.MOOSHROOM.create(level, EntitySpawnReason.BREEDING);
        if (mushroomCow != null) {
            mushroomCow.setVariant(this.getOffspringVariant((MushroomCow)otherParent));
        }

        return mushroomCow;
    }

    private MushroomCow.Variant getOffspringVariant(MushroomCow partner) {
        MushroomCow.Variant variant = this.getVariant();
        MushroomCow.Variant variant1 = partner.getVariant();
        MushroomCow.Variant variant2;
        if (variant == variant1 && this.random.nextInt(1024) == 0) {
            variant2 = variant == MushroomCow.Variant.BROWN ? MushroomCow.Variant.RED : MushroomCow.Variant.BROWN;
        } else {
            variant2 = this.random.nextBoolean() ? variant : variant1;
        }

        return variant2;
    }

    public static enum Variant implements StringRepresentable {
        RED("red", Blocks.RED_MUSHROOM.defaultBlockState()),
        BROWN("brown", Blocks.BROWN_MUSHROOM.defaultBlockState());

        public static final StringRepresentable.EnumCodec<MushroomCow.Variant> CODEC = StringRepresentable.fromEnum(MushroomCow.Variant::values);
        final String type;
        private final BlockState blockState;

        private Variant(final String type, final BlockState blockState) {
            this.type = type;
            this.blockState = blockState;
        }

        public BlockState getBlockState() {
            return this.blockState;
        }

        @Override
        public String getSerializedName() {
            return this.type;
        }

        static MushroomCow.Variant byName(String name) {
            return CODEC.byName(name, RED);
        }
    }
}
