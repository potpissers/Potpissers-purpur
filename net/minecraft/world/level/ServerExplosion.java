package net.minecraft.world.level;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// CraftBukkit start
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.Location;
import org.bukkit.event.block.BlockExplodeEvent;
// CraftBukkit end

public class ServerExplosion implements Explosion {
    private static final ExplosionDamageCalculator EXPLOSION_DAMAGE_CALCULATOR = new ExplosionDamageCalculator();
    private static final int MAX_DROPS_PER_COMBINED_STACK = 16;
    private static final float LARGE_EXPLOSION_RADIUS = 2.0F;
    private final boolean fire;
    private final Explosion.BlockInteraction blockInteraction;
    private final ServerLevel level;
    private final Vec3 center;
    @Nullable
    private final Entity source;
    private final float radius;
    private final DamageSource damageSource;
    private final ExplosionDamageCalculator damageCalculator;
    private final Map<Player, Vec3> hitPlayers = new HashMap<>();
    // CraftBukkit - add field
    public boolean wasCanceled = false;
    public float yield;
    // CraftBukkit end
    public boolean excludeSourceFromDamage = true; // Paper - Allow explosions to damage source

    public ServerExplosion(
        ServerLevel level,
        @Nullable Entity source,
        @Nullable DamageSource damageSource,
        @Nullable ExplosionDamageCalculator damageCalculator,
        Vec3 center,
        float radius,
        boolean fire,
        Explosion.BlockInteraction blockInteraction
    ) {
        this.level = level;
        this.source = source;
        this.radius = (float) Math.max(radius, 0.0); // CraftBukkit - clamp bad values
        this.center = center;
        this.fire = fire;
        this.blockInteraction = blockInteraction;
        this.damageSource = damageSource == null ? level.damageSources().explosion(this) : damageSource;
        this.damageCalculator = damageCalculator == null ? this.makeDamageCalculator(source) : damageCalculator;
        this.yield = this.blockInteraction == Explosion.BlockInteraction.DESTROY_WITH_DECAY ? 1.0F / this.radius : 1.0F; // CraftBukkit
    }

    private ExplosionDamageCalculator makeDamageCalculator(@Nullable Entity entity) {
        return (ExplosionDamageCalculator)(entity == null ? EXPLOSION_DAMAGE_CALCULATOR : new EntityBasedExplosionDamageCalculator(entity));
    }

    public static float getSeenPercent(Vec3 explosionVector, Entity entity) {
        AABB boundingBox = entity.getBoundingBox();
        double d = 1.0 / ((boundingBox.maxX - boundingBox.minX) * 2.0 + 1.0);
        double d1 = 1.0 / ((boundingBox.maxY - boundingBox.minY) * 2.0 + 1.0);
        double d2 = 1.0 / ((boundingBox.maxZ - boundingBox.minZ) * 2.0 + 1.0);
        double d3 = (1.0 - Math.floor(1.0 / d) * d) / 2.0;
        double d4 = (1.0 - Math.floor(1.0 / d2) * d2) / 2.0;
        if (!(d < 0.0) && !(d1 < 0.0) && !(d2 < 0.0)) {
            int i = 0;
            int i1 = 0;

            for (double d5 = 0.0; d5 <= 1.0; d5 += d) {
                for (double d6 = 0.0; d6 <= 1.0; d6 += d1) {
                    for (double d7 = 0.0; d7 <= 1.0; d7 += d2) {
                        double d8 = Mth.lerp(d5, boundingBox.minX, boundingBox.maxX);
                        double d9 = Mth.lerp(d6, boundingBox.minY, boundingBox.maxY);
                        double d10 = Mth.lerp(d7, boundingBox.minZ, boundingBox.maxZ);
                        Vec3 vec3 = new Vec3(d8 + d3, d9, d10 + d4);
                        if (entity.level().clip(new ClipContext(vec3, explosionVector, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getType()
                            == HitResult.Type.MISS) {
                            i++;
                        }

                        i1++;
                    }
                }
            }

            return (float)i / i1;
        } else {
            return 0.0F;
        }
    }

    @Override
    public float radius() {
        return this.radius;
    }

    @Override
    public Vec3 center() {
        return this.center;
    }

    private List<BlockPos> calculateExplodedPositions() {
        Set<BlockPos> set = new HashSet<>();
        int i = 16;

        for (int i1 = 0; i1 < 16; i1++) {
            for (int i2 = 0; i2 < 16; i2++) {
                for (int i3 = 0; i3 < 16; i3++) {
                    if (i1 == 0 || i1 == 15 || i2 == 0 || i2 == 15 || i3 == 0 || i3 == 15) {
                        double d = i1 / 15.0F * 2.0F - 1.0F;
                        double d1 = i2 / 15.0F * 2.0F - 1.0F;
                        double d2 = i3 / 15.0F * 2.0F - 1.0F;
                        double squareRoot = Math.sqrt(d * d + d1 * d1 + d2 * d2);
                        d /= squareRoot;
                        d1 /= squareRoot;
                        d2 /= squareRoot;
                        float f = this.radius * (0.7F + this.level.random.nextFloat() * 0.6F);
                        double d3 = this.center.x;
                        double d4 = this.center.y;
                        double d5 = this.center.z;

                        for (float f1 = 0.3F; f > 0.0F; f -= 0.22500001F) {
                            BlockPos blockPos = BlockPos.containing(d3, d4, d5);
                            BlockState blockState = this.level.getBlockState(blockPos);
                            if (!blockState.isDestroyable()) continue; // Paper - Protect Bedrock and End Portal/Frames from being destroyed
                            FluidState fluidState = blockState.getFluidState(); // Paper - Perf: Optimize call to getFluid for explosions
                            if (!this.level.isInWorldBounds(blockPos)) {
                                break;
                            }

                            Optional<Float> blockExplosionResistance = this.damageCalculator
                                .getBlockExplosionResistance(this, this.level, blockPos, blockState, fluidState);
                            if (blockExplosionResistance.isPresent()) {
                                f -= (blockExplosionResistance.get() + 0.3F) * 0.3F;
                            }

                            if (f > 0.0F && this.damageCalculator.shouldBlockExplode(this, this.level, blockPos, blockState, f)) {
                                set.add(blockPos);
                                // Paper start - prevent headless pistons from forming
                                if (!io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.allowHeadlessPistons && blockState.is(Blocks.MOVING_PISTON)) {
                                    net.minecraft.world.level.block.entity.BlockEntity extension = this.level.getBlockEntity(blockPos);
                                    if (extension instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity blockEntity && blockEntity.isSourcePiston()) {
                                        net.minecraft.core.Direction direction = blockState.getValue(net.minecraft.world.level.block.piston.PistonHeadBlock.FACING);
                                        set.add(blockPos.relative(direction.getOpposite()));
                                    }
                                }
                                // Paper end - prevent headless pistons from forming
                            }

                            d3 += d * 0.3F;
                            d4 += d1 * 0.3F;
                            d5 += d2 * 0.3F;
                        }
                    }
                }
            }
        }

        return new ObjectArrayList<>(set);
    }

    private void hurtEntities() {
        float f = this.radius * 2.0F;
        int floor = Mth.floor(this.center.x - f - 1.0);
        int floor1 = Mth.floor(this.center.x + f + 1.0);
        int floor2 = Mth.floor(this.center.y - f - 1.0);
        int floor3 = Mth.floor(this.center.y + f + 1.0);
        int floor4 = Mth.floor(this.center.z - f - 1.0);
        int floor5 = Mth.floor(this.center.z + f + 1.0);
        List <Entity> list = this.level.getEntities(excludeSourceFromDamage ? this.source : null, new AABB(floor, floor2, floor4, floor1, floor3, floor5), entity -> entity.isAlive() && !entity.isSpectator()); // Paper - Fix lag from explosions processing dead entities, Allow explosions to damage source
        for (Entity entity : list) { // Paper - used in loop
            if (!entity.ignoreExplosion(this)) {
                double d = Math.sqrt(entity.distanceToSqr(this.center)) / f;
                if (d <= 1.0) {
                    double d1 = entity.getX() - this.center.x;
                    double d2 = (entity instanceof PrimedTnt ? entity.getY() : entity.getEyeY()) - this.center.y;
                    double d3 = entity.getZ() - this.center.z;
                    double squareRoot = Math.sqrt(d1 * d1 + d2 * d2 + d3 * d3);
                    if (squareRoot != 0.0) {
                        d1 /= squareRoot;
                        d2 /= squareRoot;
                        d3 /= squareRoot;
                        boolean shouldDamageEntity = this.damageCalculator.shouldDamageEntity(this, entity);
                        float knockbackMultiplier = this.damageCalculator.getKnockbackMultiplier(entity);
                        float f1 = !shouldDamageEntity && knockbackMultiplier == 0.0F ? 0.0F : this.getBlockDensity(this.center, entity); // Paper - Optimize explosions
                        if (shouldDamageEntity) {
                            // CraftBukkit start

                            // Special case ender dragon only give knockback if no damage is cancelled
                            // Thinks to note:
                            // - Setting a velocity to a EnderDragonPart is ignored (and therefore not needed)
                            // - Damaging EnderDragonPart while forward the damage to EnderDragon
                            // - Damaging EntityEnderDragon does nothing
                            // - EnderDragon hitbock always covers the other parts and is therefore always present
                            if (entity instanceof EnderDragonPart) {
                                continue;
                            }

                            entity.lastDamageCancelled = false;

                            if (entity instanceof EnderDragon) {
                                for (EnderDragonPart dragonPart : ((EnderDragon) entity).getSubEntities()) {
                                    // Calculate damage separately for each EntityComplexPart
                                    if (list.contains(dragonPart)) {
                                        dragonPart.hurtServer(this.level, this.damageSource, this.damageCalculator.getEntityDamageAmount(this, entity, f1));
                                    }
                                }
                            } else {
                                entity.hurtServer(this.level, this.damageSource, this.damageCalculator.getEntityDamageAmount(this, entity, f1));
                            }

                            if (entity.lastDamageCancelled) { // SPIGOT-5339, SPIGOT-6252, SPIGOT-6777: Skip entity if damage event was cancelled
                                continue;
                            }
                            // CraftBukkit end
                        }

                        double d4 = (1.0 - d) * f1 * knockbackMultiplier;
                        double d5;
                        if (entity instanceof LivingEntity livingEntity) {
                            d5 = entity instanceof Player && this.level.paperConfig().environment.disableExplosionKnockback ? 0 : d4 * (1.0 - livingEntity.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)); // Paper
                        } else {
                            d5 = d4;
                        }

                        d1 *= d5;
                        d2 *= d5;
                        d3 *= d5;
                        Vec3 vec3 = new Vec3(d1, d2, d3);
                        // CraftBukkit start - Call EntityKnockbackEvent
                        if (entity instanceof LivingEntity) {
                            // Paper start - knockback events
                            io.papermc.paper.event.entity.EntityKnockbackEvent event = CraftEventFactory.callEntityKnockbackEvent((org.bukkit.craftbukkit.entity.CraftLivingEntity) entity.getBukkitEntity(), this.source, this.damageSource.getEntity() != null ? this.damageSource.getEntity() : this.source, io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.EXPLOSION, d5, vec3);
                            vec3 = event.isCancelled() ? Vec3.ZERO : org.bukkit.craftbukkit.util.CraftVector.toNMS(event.getKnockback());
                            // Paper end - knockback events
                        }
                        // CraftBukkit end
                        entity.push(vec3);
                        if (entity instanceof Player) {
                            Player player = (Player)entity;
                            if (!player.isSpectator() && (!player.isCreative() || !player.getAbilities().flying) && !level.paperConfig().environment.disableExplosionKnockback) { // Paper - Option to disable explosion knockback
                                this.hitPlayers.put(player, vec3);
                            }
                        }

                        entity.onExplosionHit(this.source);
                    }
                }
            }
        }
    }

    private void interactWithBlocks(List<BlockPos> blocks) {
        List<ServerExplosion.StackCollector> list = new ArrayList<>();
        Util.shuffle(blocks, this.level.random);

        // CraftBukkit start
        org.bukkit.World bworld = this.level.getWorld();
        Location location = CraftLocation.toBukkit(this.center, bworld);

        List<org.bukkit.block.Block> blockList = new ObjectArrayList<>();
        for (int i1 = blocks.size() - 1; i1 >= 0; i1--) {
            BlockPos cpos = blocks.get(i1);
            org.bukkit.block.Block bblock = bworld.getBlockAt(cpos.getX(), cpos.getY(), cpos.getZ());
            if (!bblock.getType().isAir()) {
                blockList.add(bblock);
            }
        }

        List<org.bukkit.block.Block> bukkitBlocks;

        if (this.source != null) {
            EntityExplodeEvent event = CraftEventFactory.callEntityExplodeEvent(this.source, blockList, this.yield, this.getBlockInteraction());
            this.wasCanceled = event.isCancelled();
            bukkitBlocks = event.blockList();
            this.yield = event.getYield();
        } else {
            org.bukkit.block.Block block = location.getBlock();
            org.bukkit.block.BlockState blockState = (this.damageSource.getDirectBlockState() != null) ? this.damageSource.getDirectBlockState() : block.getState();
            BlockExplodeEvent event = CraftEventFactory.callBlockExplodeEvent(block, blockState, blockList, this.yield, this.getBlockInteraction());
            this.wasCanceled = event.isCancelled();
            bukkitBlocks = event.blockList();
            this.yield = event.getYield();
        }

        blocks.clear();

        for (org.bukkit.block.Block bblock : bukkitBlocks) {
            BlockPos coords = new BlockPos(bblock.getX(), bblock.getY(), bblock.getZ());
            blocks.add(coords);
        }

        if (this.wasCanceled) {
            return;
        }
        // CraftBukkit end

        for (BlockPos blockPos : blocks) {
            // CraftBukkit start - TNTPrimeEvent
            BlockState iblockdata = this.level.getBlockState(blockPos);
            Block block = iblockdata.getBlock();
            if (block instanceof net.minecraft.world.level.block.TntBlock) {
                Entity sourceEntity = this.source == null ? null : this.source;
                BlockPos sourceBlock = sourceEntity == null ? BlockPos.containing(this.center) : null;
                if (!CraftEventFactory.callTNTPrimeEvent(this.level, blockPos, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.EXPLOSION, sourceEntity, sourceBlock)) {
                    this.level.sendBlockUpdated(blockPos, Blocks.AIR.defaultBlockState(), iblockdata, 3); // Update the block on the client
                    continue;
                }
            }
            // CraftBukkit end

            this.level
                .getBlockState(blockPos)
                .onExplosionHit(this.level, blockPos, this, (itemStack, blockPos1) -> addOrAppendStack(list, itemStack, blockPos1));
        }

        for (ServerExplosion.StackCollector stackCollector : list) {
            Block.popResource(this.level, stackCollector.pos, stackCollector.stack);
        }
    }

    private void createFire(List<BlockPos> blocks) {
        for (BlockPos blockPos : blocks) {
            if (this.level.random.nextInt(3) == 0 && this.level.getBlockState(blockPos).isAir() && this.level.getBlockState(blockPos.below()).isSolidRender()) {
                // CraftBukkit start - Ignition by explosion
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(this.level, blockPos, this).isCancelled()) {
                    this.level.setBlockAndUpdate(blockPos, BaseFireBlock.getState(this.level, blockPos));
                }
                // CraftBukkit end
            }
        }
    }

    public void explode() {
        // CraftBukkit start
        if (this.radius < 0.1F) {
            return;
        }
        // CraftBukkit end
        this.level.gameEvent(this.source, GameEvent.EXPLODE, this.center);
        List<BlockPos> list = this.calculateExplodedPositions();
        this.hurtEntities();
        if (this.interactsWithBlocks()) {
            ProfilerFiller profilerFiller = Profiler.get();
            profilerFiller.push("explosion_blocks");
            this.interactWithBlocks(list);
            profilerFiller.pop();
        }

        if (this.fire) {
            this.createFire(list);
        }
    }

    private static void addOrAppendStack(List<ServerExplosion.StackCollector> stackCollectors, ItemStack stack, BlockPos pos) {
        if (stack.isEmpty()) return; // CraftBukkit - SPIGOT-5425
        for (ServerExplosion.StackCollector stackCollector : stackCollectors) {
            stackCollector.tryMerge(stack);
            if (stack.isEmpty()) {
                return;
            }
        }

        stackCollectors.add(new ServerExplosion.StackCollector(pos, stack));
    }

    private boolean interactsWithBlocks() {
        return this.blockInteraction != Explosion.BlockInteraction.KEEP;
    }

    public Map<Player, Vec3> getHitPlayers() {
        return this.hitPlayers;
    }

    @Override
    public ServerLevel level() {
        return this.level;
    }

    @Nullable
    @Override
    public LivingEntity getIndirectSourceEntity() {
        return Explosion.getIndirectSourceEntity(this.source);
    }

    @Nullable
    @Override
    public Entity getDirectSourceEntity() {
        return this.source;
    }

    public DamageSource getDamageSource() {
        return this.damageSource;
    }

    @Override
    public Explosion.BlockInteraction getBlockInteraction() {
        return this.blockInteraction;
    }

    @Override
    public boolean canTriggerBlocks() {
        return this.blockInteraction == Explosion.BlockInteraction.TRIGGER_BLOCK
            && (
                this.source == null
                    || this.source.getType() != EntityType.BREEZE_WIND_CHARGE
                    || this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
            );
    }

    @Override
    public boolean shouldAffectBlocklikeEntities() {
        boolean _boolean = this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        boolean flag = this.source == null || !this.source.isInWater();
        boolean flag1 = this.source == null || this.source.getType() != EntityType.BREEZE_WIND_CHARGE && this.source.getType() != EntityType.WIND_CHARGE;
        return _boolean ? flag && flag1 : this.blockInteraction.shouldAffectBlocklikeEntities() && flag && flag1;
    }

    public boolean isSmall() {
        return this.radius < 2.0F || !this.interactsWithBlocks();
    }

    static class StackCollector {
        final BlockPos pos;
        ItemStack stack;

        StackCollector(BlockPos pos, ItemStack stack) {
            this.pos = pos;
            this.stack = stack;
        }

        public void tryMerge(ItemStack stack) {
            if (ItemEntity.areMergable(this.stack, stack)) {
                this.stack = ItemEntity.merge(this.stack, stack, 16);
            }
        }
    }


    // Paper start - Optimize explosions
    private float getBlockDensity(Vec3 vec3d, Entity entity) {
        if (!this.level.paperConfig().environment.optimizeExplosions) {
            return getSeenPercent(vec3d, entity);
        }
        CacheKey key = new CacheKey(this, entity.getBoundingBox());
        Float blockDensity = this.level.explosionDensityCache.get(key);
        if (blockDensity == null) {
            blockDensity = getSeenPercent(vec3d, entity);
            this.level.explosionDensityCache.put(key, blockDensity);
        }

        return blockDensity;
    }

    static class CacheKey {
        private final Level world;
        private final double posX, posY, posZ;
        private final double minX, minY, minZ;
        private final double maxX, maxY, maxZ;

        public CacheKey(Explosion explosion, AABB aabb) {
            this.world = explosion.level();
            this.posX = explosion.center().x;
            this.posY = explosion.center().y;
            this.posZ = explosion.center().z;
            this.minX = aabb.minX;
            this.minY = aabb.minY;
            this.minZ = aabb.minZ;
            this.maxX = aabb.maxX;
            this.maxY = aabb.maxY;
            this.maxZ = aabb.maxZ;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CacheKey cacheKey = (CacheKey) o;

            if (Double.compare(cacheKey.posX, posX) != 0) return false;
            if (Double.compare(cacheKey.posY, posY) != 0) return false;
            if (Double.compare(cacheKey.posZ, posZ) != 0) return false;
            if (Double.compare(cacheKey.minX, minX) != 0) return false;
            if (Double.compare(cacheKey.minY, minY) != 0) return false;
            if (Double.compare(cacheKey.minZ, minZ) != 0) return false;
            if (Double.compare(cacheKey.maxX, maxX) != 0) return false;
            if (Double.compare(cacheKey.maxY, maxY) != 0) return false;
            if (Double.compare(cacheKey.maxZ, maxZ) != 0) return false;
            return world.equals(cacheKey.world);
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            result = world.hashCode();
            temp = Double.doubleToLongBits(posX);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(posY);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(posZ);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(minX);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(minY);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(minZ);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(maxX);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(maxY);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(maxZ);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }
    }
    // Paper end
}
