package net.minecraft.world.item;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SpawnEggItem extends Item {
    private static final Map<EntityType<? extends Mob>, SpawnEggItem> BY_ID = Maps.newIdentityHashMap();
    private final EntityType<?> defaultType;

    public SpawnEggItem(EntityType<? extends Mob> defaultType, Item.Properties properties) {
        super(properties);
        this.defaultType = defaultType;
        BY_ID.put(defaultType, this);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            ItemStack itemInHand = context.getItemInHand();
            BlockPos clickedPos = context.getClickedPos();
            Direction clickedFace = context.getClickedFace();
            BlockState blockState = level.getBlockState(clickedPos);
            if (level.getBlockEntity(clickedPos) instanceof Spawner spawner) {
                if (level.paperConfig().entities.spawning.disableMobSpawnerSpawnEggTransformation) return InteractionResult.FAIL; // Paper - Allow disabling mob spawner spawn egg transformation
                EntityType<?> type = this.getType(level.registryAccess(), itemInHand);
                // Purpur start - PlayerSetSpawnerTypeWithEggEvent
                if (spawner instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity) {
                    org.bukkit.block.Block bukkitBlock = level.getWorld().getBlockAt(clickedPos.getX(), clickedPos.getY(), clickedPos.getZ());
                    org.purpurmc.purpur.event.PlayerSetSpawnerTypeWithEggEvent event = new org.purpurmc.purpur.event.PlayerSetSpawnerTypeWithEggEvent((org.bukkit.entity.Player) context.getPlayer().getBukkitEntity(), bukkitBlock, (org.bukkit.block.CreatureSpawner) bukkitBlock.getState(), org.bukkit.entity.EntityType.fromName(type.getName()));
                    if (!event.callEvent()) {
                        return InteractionResult.FAIL;
                    }
                    type = EntityType.getFromBukkitType(event.getEntityType());
                } else if (spawner instanceof net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity) {
                    org.bukkit.block.Block bukkitBlock = level.getWorld().getBlockAt(clickedPos.getX(), clickedPos.getY(), clickedPos.getZ());
                    org.purpurmc.purpur.event.PlayerSetTrialSpawnerTypeWithEggEvent event = new org.purpurmc.purpur.event.PlayerSetTrialSpawnerTypeWithEggEvent((org.bukkit.entity.Player) context.getPlayer().getBukkitEntity(), bukkitBlock, (org.bukkit.block.TrialSpawner) bukkitBlock.getState(), org.bukkit.entity.EntityType.fromName(type.getName()));
                    if (!event.callEvent()) {
                        return InteractionResult.FAIL;
                    }
                    type = EntityType.getFromBukkitType(event.getEntityType());
                }
                // Purpur end - PlayerSetSpawnerTypeWithEggEvent
                spawner.setEntityId(type, level.getRandom());
                level.sendBlockUpdated(clickedPos, blockState, blockState, 3);
                level.gameEvent(context.getPlayer(), GameEvent.BLOCK_CHANGE, clickedPos);
                itemInHand.shrink(1);
                return InteractionResult.SUCCESS;
            } else {
                BlockPos blockPos;
                if (blockState.getCollisionShape(level, clickedPos).isEmpty()) {
                    blockPos = clickedPos;
                } else {
                    blockPos = clickedPos.relative(clickedFace);
                }

                EntityType<?> type = this.getType(level.registryAccess(), itemInHand);
                if (type.spawn(
                        (ServerLevel)level,
                        itemInHand,
                        context.getPlayer(),
                        blockPos,
                        EntitySpawnReason.SPAWN_ITEM_USE,
                        true,
                        !Objects.equals(clickedPos, blockPos) && clickedFace == Direction.UP
                    )
                    != null) {
                    itemInHand.shrink(1);
                    level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, clickedPos);
                }

                return InteractionResult.SUCCESS;
            }
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        BlockHitResult playerPovHitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (playerPovHitResult.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        } else if (level instanceof ServerLevel serverLevel) {
            BlockPos blockPos = playerPovHitResult.getBlockPos();
            if (!(level.getBlockState(blockPos).getBlock() instanceof LiquidBlock)) {
                return InteractionResult.PASS;
            } else if (level.mayInteract(player, blockPos) && player.mayUseItemAt(blockPos, playerPovHitResult.getDirection(), itemInHand)) {
                EntityType<?> type = this.getType(serverLevel.registryAccess(), itemInHand);
                Entity entity = type.spawn(serverLevel, itemInHand, player, blockPos, EntitySpawnReason.SPAWN_ITEM_USE, false, false);
                if (entity == null) {
                    return InteractionResult.PASS;
                } else {
                    itemInHand.consume(1, player);
                    player.awardStat(Stats.ITEM_USED.get(this));
                    level.gameEvent(player, GameEvent.ENTITY_PLACE, entity.position());
                    return InteractionResult.SUCCESS;
                }
            } else {
                return InteractionResult.FAIL;
            }
        } else {
            return InteractionResult.SUCCESS;
        }
    }

    public boolean spawnsEntity(HolderLookup.Provider registries, ItemStack stack, EntityType<?> entityType) {
        return Objects.equals(this.getType(registries, stack), entityType);
    }

    @Nullable
    public static SpawnEggItem byId(@Nullable EntityType<?> type) {
        return BY_ID.get(type);
    }

    public static Iterable<SpawnEggItem> eggs() {
        return Iterables.unmodifiableIterable(BY_ID.values());
    }

    public EntityType<?> getType(HolderLookup.Provider registries, ItemStack provider) {
        CustomData customData = provider.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY);
        if (!customData.isEmpty()) {
            EntityType<?> entityType = customData.parseEntityType(registries, Registries.ENTITY_TYPE);
            if (entityType != null) {
                return entityType;
            }
        }

        return this.defaultType;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.defaultType.requiredFeatures();
    }

    public Optional<Mob> spawnOffspringFromSpawnEgg(
        Player player, Mob mob, EntityType<? extends Mob> entityType, ServerLevel serverLevel, Vec3 pos, ItemStack stack
    ) {
        if (!this.spawnsEntity(serverLevel.registryAccess(), stack, entityType)) {
            return Optional.empty();
        } else {
            Mob breedOffspring;
            if (mob instanceof AgeableMob) {
                breedOffspring = ((AgeableMob)mob).getBreedOffspring(serverLevel, (AgeableMob)mob);
            } else {
                breedOffspring = entityType.create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
            }

            if (breedOffspring == null) {
                return Optional.empty();
            } else {
                breedOffspring.setBaby(true);
                if (!breedOffspring.isBaby()) {
                    return Optional.empty();
                } else {
                    breedOffspring.moveTo(pos.x(), pos.y(), pos.z(), 0.0F, 0.0F);
                    serverLevel.addFreshEntityWithPassengers(breedOffspring, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER_EGG); // CraftBukkit
                    breedOffspring.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
                    stack.consume(1, player);
                    return Optional.of(breedOffspring);
                }
            }
        }
    }

    @Override
    public boolean shouldPrintOpWarning(ItemStack stack, @Nullable Player player) {
        if (player != null && player.getPermissionLevel() >= 2) {
            CustomData customData = stack.get(DataComponents.ENTITY_DATA);
            if (customData != null) {
                EntityType<?> entityType = customData.parseEntityType(player.level().registryAccess(), Registries.ENTITY_TYPE);
                return entityType != null && entityType.onlyOpCanSetNbt();
            }
        }

        return false;
    }
}
