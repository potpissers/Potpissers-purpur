package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ConduitBlockEntity extends BlockEntity {
    private static final int BLOCK_REFRESH_RATE = 2;
    private static final int EFFECT_DURATION = 13;
    private static final float ROTATION_SPEED = -0.0375F;
    private static final int MIN_ACTIVE_SIZE = 16;
    private static final int MIN_KILL_SIZE = 42;
    private static final int KILL_RANGE = 8;
    private static final Block[] VALID_BLOCKS = new Block[]{Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN, Blocks.DARK_PRISMARINE};
    public int tickCount;
    private float activeRotation;
    private boolean isActive;
    private boolean isHunting;
    public final List<BlockPos> effectBlocks = Lists.newArrayList();
    @Nullable
    public LivingEntity destroyTarget;
    @Nullable
    public UUID destroyTargetUUID;
    private long nextAmbientSoundActivation;

    public ConduitBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.CONDUIT, pos, blockState);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("Target")) {
            this.destroyTargetUUID = tag.getUUID("Target");
        } else {
            this.destroyTargetUUID = null;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.destroyTarget != null) {
            tag.putUUID("Target", this.destroyTarget.getUUID());
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ConduitBlockEntity blockEntity) {
        blockEntity.tickCount++;
        long gameTime = level.getGameTime();
        List<BlockPos> list = blockEntity.effectBlocks;
        if (gameTime % 40L == 0L) {
            blockEntity.isActive = updateShape(level, pos, list);
            updateHunting(blockEntity, list);
        }

        updateClientTarget(level, pos, blockEntity);
        animationTick(level, pos, list, blockEntity.destroyTarget, blockEntity.tickCount);
        if (blockEntity.isActive()) {
            blockEntity.activeRotation++;
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ConduitBlockEntity blockEntity) {
        blockEntity.tickCount++;
        long gameTime = level.getGameTime();
        List<BlockPos> list = blockEntity.effectBlocks;
        if (gameTime % 40L == 0L) {
            boolean flag = updateShape(level, pos, list);
            if (flag != blockEntity.isActive) {
                SoundEvent soundEvent = flag ? SoundEvents.CONDUIT_ACTIVATE : SoundEvents.CONDUIT_DEACTIVATE;
                level.playSound(null, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
            }

            blockEntity.isActive = flag;
            updateHunting(blockEntity, list);
            if (flag) {
                applyEffects(level, pos, list);
                updateDestroyTarget(level, pos, state, list, blockEntity);
            }
        }

        if (blockEntity.isActive()) {
            if (gameTime % 80L == 0L) {
                level.playSound(null, pos, SoundEvents.CONDUIT_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.0F);
            }

            if (gameTime > blockEntity.nextAmbientSoundActivation) {
                blockEntity.nextAmbientSoundActivation = gameTime + 60L + level.getRandom().nextInt(40);
                level.playSound(null, pos, SoundEvents.CONDUIT_AMBIENT_SHORT, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
    }

    private static void updateHunting(ConduitBlockEntity blockEntity, List<BlockPos> positions) {
        blockEntity.setHunting(positions.size() >= 42);
    }

    private static boolean updateShape(Level level, BlockPos pos, List<BlockPos> positions) {
        positions.clear();

        for (int i = -1; i <= 1; i++) {
            for (int i1 = -1; i1 <= 1; i1++) {
                for (int i2 = -1; i2 <= 1; i2++) {
                    BlockPos blockPos = pos.offset(i, i1, i2);
                    if (!level.isWaterAt(blockPos)) {
                        return false;
                    }
                }
            }
        }

        for (int i = -2; i <= 2; i++) {
            for (int i1 = -2; i1 <= 2; i1++) {
                for (int i2x = -2; i2x <= 2; i2x++) {
                    int abs = Math.abs(i);
                    int abs1 = Math.abs(i1);
                    int abs2 = Math.abs(i2x);
                    if ((abs > 1 || abs1 > 1 || abs2 > 1)
                        && (i == 0 && (abs1 == 2 || abs2 == 2) || i1 == 0 && (abs == 2 || abs2 == 2) || i2x == 0 && (abs == 2 || abs1 == 2))) {
                        BlockPos blockPos1 = pos.offset(i, i1, i2x);
                        BlockState blockState = level.getBlockState(blockPos1);

                        for (Block block : level.purpurConfig.conduitBlocks) { // Purpur - Conduit behavior configuration
                            if (blockState.is(block)) {
                                positions.add(blockPos1);
                            }
                        }
                    }
                }
            }
        }

        return positions.size() >= 16;
    }

    private static void applyEffects(Level level, BlockPos pos, List<BlockPos> positions) {
        // CraftBukkit start
        ConduitBlockEntity.applyEffects(level, pos, ConduitBlockEntity.getRange(positions, level)); // Purpur - Conduit behavior configuration
    }

    public static int getRange(List<BlockPos> positions, Level level) { // Purpur - Conduit behavior configuration
        // CraftBukkit end
        int size = positions.size();
        int i = size / 7 * level.purpurConfig.conduitDistance; // Purpur - Conduit behavior configuration
        // CraftBukkit start
        return i;
    }

    private static void applyEffects(Level level, BlockPos pos, int i) { // i = effect range in blocks
        // CraftBukkit end
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        AABB aabb = new AABB(x, y, z, x + 1, y + 1, z + 1).inflate(i).expandTowards(0.0, level.getHeight(), 0.0);
        List<Player> entitiesOfClass = level.getEntitiesOfClass(Player.class, aabb);
        if (!entitiesOfClass.isEmpty()) {
            for (Player player : entitiesOfClass) {
                if (pos.closerThan(player.blockPosition(), i) && player.isInWaterOrRain()) {
                    player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 260, 0, true, true), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.CONDUIT); // CraftBukkit
                }
            }
        }
    }

    private static void updateDestroyTarget(Level level, BlockPos pos, BlockState state, List<BlockPos> positions, ConduitBlockEntity blockEntity) {
        // CraftBukkit start - add "damageTarget" boolean
        ConduitBlockEntity.updateDestroyTarget(level, pos, state, positions, blockEntity, true);
    }

    public static void updateDestroyTarget(Level level, BlockPos pos, BlockState state, List<BlockPos> positions, ConduitBlockEntity blockEntity, boolean damageTarget) {
        // CraftBukkit end
        LivingEntity livingEntity = blockEntity.destroyTarget;
        int size = positions.size();
        if (size < 42) {
            blockEntity.destroyTarget = null;
        } else if (blockEntity.destroyTarget == null && blockEntity.destroyTargetUUID != null) {
            blockEntity.destroyTarget = findDestroyTarget(level, pos, blockEntity.destroyTargetUUID);
            blockEntity.destroyTargetUUID = null;
        } else if (blockEntity.destroyTarget == null) {
            List<LivingEntity> entitiesOfClass = level.getEntitiesOfClass(
                LivingEntity.class, getDestroyRangeAABB(pos, level), collidedEntity -> collidedEntity instanceof Enemy && collidedEntity.isInWaterOrRain() // Purpur - Conduit behavior configuration
            );
            if (!entitiesOfClass.isEmpty()) {
                blockEntity.destroyTarget = entitiesOfClass.get(level.random.nextInt(entitiesOfClass.size()));
            }
        } else if (!blockEntity.destroyTarget.isAlive() || !pos.closerThan(blockEntity.destroyTarget.blockPosition(), level.purpurConfig.conduitDamageDistance)) { // Purpur - Conduit behavior configuration
            blockEntity.destroyTarget = null;
        }

        if (damageTarget && blockEntity.destroyTarget != null) { // CraftBukkit
            if (blockEntity.destroyTarget.hurtServer((net.minecraft.server.level.ServerLevel) level, level.damageSources().magic().directBlock(level, pos), level.purpurConfig.conduitDamageAmount)) // CraftBukkit // Purpur - Conduit behavior configuration
            level.playSound(
                null,
                blockEntity.destroyTarget.getX(),
                blockEntity.destroyTarget.getY(),
                blockEntity.destroyTarget.getZ(),
                SoundEvents.CONDUIT_ATTACK_TARGET,
                SoundSource.BLOCKS,
                1.0F,
                1.0F
            );
        }

        if (livingEntity != blockEntity.destroyTarget) {
            level.sendBlockUpdated(pos, state, state, 2);
        }
    }

    private static void updateClientTarget(Level level, BlockPos pos, ConduitBlockEntity blockEntity) {
        if (blockEntity.destroyTargetUUID == null) {
            blockEntity.destroyTarget = null;
        } else if (blockEntity.destroyTarget == null || !blockEntity.destroyTarget.getUUID().equals(blockEntity.destroyTargetUUID)) {
            blockEntity.destroyTarget = findDestroyTarget(level, pos, blockEntity.destroyTargetUUID);
            if (blockEntity.destroyTarget == null) {
                blockEntity.destroyTargetUUID = null;
            }
        }
    }

    public static AABB getDestroyRangeAABB(BlockPos pos) {
        // Purpur start - Conduit behavior configuration
        return getDestroyRangeAABB(pos, null);
    }

    private static AABB getDestroyRangeAABB(BlockPos pos, Level level) {
        // Purpur end - Conduit behavior configuration
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        return new AABB(x, y, z, x + 1, y + 1, z + 1).inflate(level == null ? 8.0 : level.purpurConfig.conduitDamageDistance); // Purpur - Conduit behavior configuration
    }

    @Nullable
    private static LivingEntity findDestroyTarget(Level level, BlockPos pos, UUID targetId) {
        List<LivingEntity> entitiesOfClass = level.getEntitiesOfClass(
            LivingEntity.class, getDestroyRangeAABB(pos, level), collidedEntity -> collidedEntity.getUUID().equals(targetId) // Purpur - Conduit behavior configuration
        );
        return entitiesOfClass.size() == 1 ? entitiesOfClass.get(0) : null;
    }

    private static void animationTick(Level level, BlockPos pos, List<BlockPos> positions, @Nullable Entity entity, int tickCount) {
        RandomSource randomSource = level.random;
        double d = Mth.sin((tickCount + 35) * 0.1F) / 2.0F + 0.5F;
        d = (d * d + d) * 0.3F;
        Vec3 vec3 = new Vec3(pos.getX() + 0.5, pos.getY() + 1.5 + d, pos.getZ() + 0.5);

        for (BlockPos blockPos : positions) {
            if (randomSource.nextInt(50) == 0) {
                BlockPos blockPos1 = blockPos.subtract(pos);
                float f = -0.5F + randomSource.nextFloat() + blockPos1.getX();
                float f1 = -2.0F + randomSource.nextFloat() + blockPos1.getY();
                float f2 = -0.5F + randomSource.nextFloat() + blockPos1.getZ();
                level.addParticle(ParticleTypes.NAUTILUS, vec3.x, vec3.y, vec3.z, f, f1, f2);
            }
        }

        if (entity != null) {
            Vec3 vec31 = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ());
            float f3 = (-0.5F + randomSource.nextFloat()) * (3.0F + entity.getBbWidth());
            float f4 = -1.0F + randomSource.nextFloat() * entity.getBbHeight();
            float f = (-0.5F + randomSource.nextFloat()) * (3.0F + entity.getBbWidth());
            Vec3 vec32 = new Vec3(f3, f4, f);
            level.addParticle(ParticleTypes.NAUTILUS, vec31.x, vec31.y, vec31.z, vec32.x, vec32.y, vec32.z);
        }
    }

    public boolean isActive() {
        return this.isActive;
    }

    public boolean isHunting() {
        return this.isHunting;
    }

    private void setHunting(boolean isHunting) {
        this.isHunting = isHunting;
    }

    public float getActiveRotation(float partialTick) {
        return (this.activeRotation + partialTick) * -0.0375F;
    }
}
