package net.minecraft.gametest.framework;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class GameTestHelper {
    private final GameTestInfo testInfo;
    private boolean finalCheckAdded;

    public GameTestHelper(GameTestInfo testInfo) {
        this.testInfo = testInfo;
    }

    public ServerLevel getLevel() {
        return this.testInfo.getLevel();
    }

    public BlockState getBlockState(BlockPos pos) {
        return this.getLevel().getBlockState(this.absolutePos(pos));
    }

    public <T extends BlockEntity> T getBlockEntity(BlockPos pos) {
        BlockEntity blockEntity = this.getLevel().getBlockEntity(this.absolutePos(pos));
        if (blockEntity == null) {
            throw new GameTestAssertPosException("Missing block entity", this.absolutePos(pos), pos, this.testInfo.getTick());
        } else {
            return (T)blockEntity;
        }
    }

    public void killAllEntities() {
        this.killAllEntitiesOfClass(Entity.class);
    }

    public void killAllEntitiesOfClass(Class entityClass) {
        AABB bounds = this.getBounds();
        List<Entity> entitiesOfClass = this.getLevel().getEntitiesOfClass(entityClass, bounds.inflate(1.0), entity -> !(entity instanceof Player));
        entitiesOfClass.forEach(entity -> entity.kill(this.getLevel()));
    }

    public ItemEntity spawnItem(Item item, Vec3 pos) {
        ServerLevel level = this.getLevel();
        Vec3 vec3 = this.absoluteVec(pos);
        ItemEntity itemEntity = new ItemEntity(level, vec3.x, vec3.y, vec3.z, new ItemStack(item, 1));
        itemEntity.setDeltaMovement(0.0, 0.0, 0.0);
        level.addFreshEntity(itemEntity);
        return itemEntity;
    }

    public ItemEntity spawnItem(Item item, float x, float y, float z) {
        return this.spawnItem(item, new Vec3(x, y, z));
    }

    public ItemEntity spawnItem(Item item, BlockPos pos) {
        return this.spawnItem(item, pos.getX(), pos.getY(), pos.getZ());
    }

    public <E extends Entity> E spawn(EntityType<E> type, BlockPos pos) {
        return this.spawn(type, Vec3.atBottomCenterOf(pos));
    }

    public <E extends Entity> E spawn(EntityType<E> type, Vec3 pos) {
        ServerLevel level = this.getLevel();
        E entity = type.create(level, EntitySpawnReason.STRUCTURE);
        if (entity == null) {
            throw new NullPointerException("Failed to create entity " + type.builtInRegistryHolder().key().location());
        } else {
            if (entity instanceof Mob mob) {
                mob.setPersistenceRequired();
            }

            Vec3 vec3 = this.absoluteVec(pos);
            entity.moveTo(vec3.x, vec3.y, vec3.z, entity.getYRot(), entity.getXRot());
            level.addFreshEntity(entity);
            return entity;
        }
    }

    public void hurt(Entity entity, DamageSource damageSource, float amount) {
        entity.hurtServer(this.getLevel(), damageSource, amount);
    }

    public void kill(Entity entity) {
        entity.kill(this.getLevel());
    }

    public <E extends Entity> E findOneEntity(EntityType<E> type) {
        return this.findClosestEntity(type, 0, 0, 0, 2.147483647E9);
    }

    public <E extends Entity> E findClosestEntity(EntityType<E> type, int x, int y, int z, double radius) {
        List<E> list = this.findEntities(type, x, y, z, radius);
        if (list.isEmpty()) {
            throw new GameTestAssertException("Expected " + type.toShortString() + " to exist around " + x + "," + y + "," + z);
        } else if (list.size() > 1) {
            throw new GameTestAssertException(
                "Expected only one " + type.toShortString() + " to exist around " + x + "," + y + "," + z + ", but found " + list.size()
            );
        } else {
            Vec3 vec3 = this.absoluteVec(new Vec3(x, y, z));
            list.sort((entity, entity1) -> {
                double d = entity.position().distanceTo(vec3);
                double d1 = entity1.position().distanceTo(vec3);
                return Double.compare(d, d1);
            });
            return list.get(0);
        }
    }

    public <E extends Entity> List<E> findEntities(EntityType<E> type, int x, int y, int z, double radius) {
        return this.findEntities(type, Vec3.atBottomCenterOf(new BlockPos(x, y, z)), radius);
    }

    public <E extends Entity> List<E> findEntities(EntityType<E> type, Vec3 pos, double radius) {
        ServerLevel level = this.getLevel();
        Vec3 vec3 = this.absoluteVec(pos);
        AABB structureBounds = this.testInfo.getStructureBounds();
        AABB aabb = new AABB(vec3.add(-radius, -radius, -radius), vec3.add(radius, radius, radius));
        return level.getEntities(type, structureBounds, entity -> entity.getBoundingBox().intersects(aabb) && entity.isAlive());
    }

    public <E extends Entity> E spawn(EntityType<E> type, int x, int y, int z) {
        return this.spawn(type, new BlockPos(x, y, z));
    }

    public <E extends Entity> E spawn(EntityType<E> type, float x, float y, float z) {
        return this.spawn(type, new Vec3(x, y, z));
    }

    public <E extends Mob> E spawnWithNoFreeWill(EntityType<E> type, BlockPos pos) {
        E mob = (E)this.spawn(type, pos);
        mob.removeFreeWill();
        return mob;
    }

    public <E extends Mob> E spawnWithNoFreeWill(EntityType<E> type, int x, int y, int z) {
        return this.spawnWithNoFreeWill(type, new BlockPos(x, y, z));
    }

    public <E extends Mob> E spawnWithNoFreeWill(EntityType<E> type, Vec3 pos) {
        E mob = (E)this.spawn(type, pos);
        mob.removeFreeWill();
        return mob;
    }

    public <E extends Mob> E spawnWithNoFreeWill(EntityType<E> type, float x, float y, float z) {
        return this.spawnWithNoFreeWill(type, new Vec3(x, y, z));
    }

    public void moveTo(Mob mob, float x, float y, float z) {
        Vec3 vec3 = this.absoluteVec(new Vec3(x, y, z));
        mob.moveTo(vec3.x, vec3.y, vec3.z, mob.getYRot(), mob.getXRot());
    }

    public GameTestSequence walkTo(Mob mob, BlockPos pos, float speed) {
        return this.startSequence().thenExecuteAfter(2, () -> {
            Path path = mob.getNavigation().createPath(this.absolutePos(pos), 0);
            mob.getNavigation().moveTo(path, (double)speed);
        });
    }

    public void pressButton(int x, int y, int z) {
        this.pressButton(new BlockPos(x, y, z));
    }

    public void pressButton(BlockPos pos) {
        this.assertBlockState(pos, state -> state.is(BlockTags.BUTTONS), () -> "Expected button");
        BlockPos blockPos = this.absolutePos(pos);
        BlockState blockState = this.getLevel().getBlockState(blockPos);
        ButtonBlock buttonBlock = (ButtonBlock)blockState.getBlock();
        buttonBlock.press(blockState, this.getLevel(), blockPos, null);
    }

    public void useBlock(BlockPos pos) {
        this.useBlock(pos, this.makeMockPlayer(GameType.CREATIVE));
    }

    public void useBlock(BlockPos pos, Player player) {
        BlockPos blockPos = this.absolutePos(pos);
        this.useBlock(pos, player, new BlockHitResult(Vec3.atCenterOf(blockPos), Direction.NORTH, blockPos, true));
    }

    public void useBlock(BlockPos pos, Player player, BlockHitResult result) {
        BlockPos blockPos = this.absolutePos(pos);
        BlockState blockState = this.getLevel().getBlockState(blockPos);
        InteractionHand interactionHand = InteractionHand.MAIN_HAND;
        InteractionResult interactionResult = blockState.useItemOn(player.getItemInHand(interactionHand), this.getLevel(), player, interactionHand, result);
        if (!interactionResult.consumesAction()) {
            if (!(interactionResult instanceof InteractionResult.TryEmptyHandInteraction)
                || !blockState.useWithoutItem(this.getLevel(), player, result).consumesAction()) {
                UseOnContext useOnContext = new UseOnContext(player, interactionHand, result);
                player.getItemInHand(interactionHand).useOn(useOnContext);
            }
        }
    }

    public LivingEntity makeAboutToDrown(LivingEntity entity) {
        entity.setAirSupply(0);
        entity.setHealth(0.25F);
        return entity;
    }

    public LivingEntity withLowHealth(LivingEntity entity) {
        entity.setHealth(0.25F);
        return entity;
    }

    public Player makeMockPlayer(final GameType gameType) {
        return new Player(this.getLevel(), BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "test-mock-player")) {
            @Override
            public boolean isSpectator() {
                return gameType == GameType.SPECTATOR;
            }

            @Override
            public boolean isCreative() {
                return gameType.isCreative();
            }

            public void setAfk(final boolean afk) {} // Purpur - AFK API

            @Override
            public boolean isLocalPlayer() {
                return true;
            }
        };
    }

    @Deprecated(
        forRemoval = true
    )
    public ServerPlayer makeMockServerPlayerInLevel() {
        CommonListenerCookie commonListenerCookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer serverPlayer = new ServerPlayer(
            this.getLevel().getServer(), this.getLevel(), commonListenerCookie.gameProfile(), commonListenerCookie.clientInformation()
        ) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        this.getLevel().getServer().getPlayerList().placeNewPlayer(connection, serverPlayer, commonListenerCookie);
        return serverPlayer;
    }

    public void pullLever(int x, int y, int z) {
        this.pullLever(new BlockPos(x, y, z));
    }

    public void pullLever(BlockPos pos) {
        this.assertBlockPresent(Blocks.LEVER, pos);
        BlockPos blockPos = this.absolutePos(pos);
        BlockState blockState = this.getLevel().getBlockState(blockPos);
        LeverBlock leverBlock = (LeverBlock)blockState.getBlock();
        leverBlock.pull(blockState, this.getLevel(), blockPos, null);
    }

    public void pulseRedstone(BlockPos pos, long delay) {
        this.setBlock(pos, Blocks.REDSTONE_BLOCK);
        this.runAfterDelay(delay, () -> this.setBlock(pos, Blocks.AIR));
    }

    public void destroyBlock(BlockPos pos) {
        this.getLevel().destroyBlock(this.absolutePos(pos), false, null);
    }

    public void setBlock(int x, int y, int z, Block block) {
        this.setBlock(new BlockPos(x, y, z), block);
    }

    public void setBlock(int x, int y, int z, BlockState state) {
        this.setBlock(new BlockPos(x, y, z), state);
    }

    public void setBlock(BlockPos pos, Block block) {
        this.setBlock(pos, block.defaultBlockState());
    }

    public void setBlock(BlockPos pos, BlockState state) {
        this.getLevel().setBlock(this.absolutePos(pos), state, 3);
    }

    public void setNight() {
        this.setDayTime(13000);
    }

    public void setDayTime(int time) {
        this.getLevel().setDayTime(time);
    }

    public void assertBlockPresent(Block block, int x, int y, int z) {
        this.assertBlockPresent(block, new BlockPos(x, y, z));
    }

    public void assertBlockPresent(Block block, BlockPos pos) {
        BlockState blockState = this.getBlockState(pos);
        this.assertBlock(
            pos, block1 -> blockState.is(block), "Expected " + block.getName().getString() + ", got " + blockState.getBlock().getName().getString()
        );
    }

    public void assertBlockNotPresent(Block block, int x, int y, int z) {
        this.assertBlockNotPresent(block, new BlockPos(x, y, z));
    }

    public void assertBlockNotPresent(Block block, BlockPos pos) {
        this.assertBlock(pos, block1 -> !this.getBlockState(pos).is(block), "Did not expect " + block.getName().getString());
    }

    public void succeedWhenBlockPresent(Block block, int x, int y, int z) {
        this.succeedWhenBlockPresent(block, new BlockPos(x, y, z));
    }

    public void succeedWhenBlockPresent(Block block, BlockPos pos) {
        this.succeedWhen(() -> this.assertBlockPresent(block, pos));
    }

    public void assertBlock(BlockPos pos, Predicate<Block> predicate, String exceptionMessage) {
        this.assertBlock(pos, predicate, () -> exceptionMessage);
    }

    public void assertBlock(BlockPos pos, Predicate<Block> predicate, Supplier<String> exceptionMessage) {
        this.assertBlockState(pos, state -> predicate.test(state.getBlock()), exceptionMessage);
    }

    public <T extends Comparable<T>> void assertBlockProperty(BlockPos pos, Property<T> property, T value) {
        BlockState blockState = this.getBlockState(pos);
        boolean hasProperty = blockState.hasProperty(property);
        if (!hasProperty || !blockState.<T>getValue(property).equals(value)) {
            String string = hasProperty ? "was " + blockState.getValue(property) : "property " + property.getName() + " is missing";
            String string1 = String.format(Locale.ROOT, "Expected property %s to be %s, %s", property.getName(), value, string);
            throw new GameTestAssertPosException(string1, this.absolutePos(pos), pos, this.testInfo.getTick());
        }
    }

    public <T extends Comparable<T>> void assertBlockProperty(BlockPos pos, Property<T> property, Predicate<T> predicate, String exceptionMessage) {
        this.assertBlockState(pos, state -> {
            if (!state.hasProperty(property)) {
                return false;
            } else {
                T value = state.getValue(property);
                return predicate.test(value);
            }
        }, () -> exceptionMessage);
    }

    public void assertBlockState(BlockPos pos, Predicate<BlockState> predicate, Supplier<String> exceptionMessage) {
        BlockState blockState = this.getBlockState(pos);
        if (!predicate.test(blockState)) {
            throw new GameTestAssertPosException(exceptionMessage.get(), this.absolutePos(pos), pos, this.testInfo.getTick());
        }
    }

    public <T extends BlockEntity> void assertBlockEntityData(BlockPos pos, Predicate<T> predicate, Supplier<String> exceptionMessage) {
        T blockEntity = this.getBlockEntity(pos);
        if (!predicate.test(blockEntity)) {
            throw new GameTestAssertPosException(exceptionMessage.get(), this.absolutePos(pos), pos, this.testInfo.getTick());
        }
    }

    public void assertRedstoneSignal(BlockPos pos, Direction direction, IntPredicate signalStrengthPredicate, Supplier<String> exceptionMessage) {
        BlockPos blockPos = this.absolutePos(pos);
        ServerLevel level = this.getLevel();
        BlockState blockState = level.getBlockState(blockPos);
        int signal = blockState.getSignal(level, blockPos, direction);
        if (!signalStrengthPredicate.test(signal)) {
            throw new GameTestAssertPosException(exceptionMessage.get(), blockPos, pos, this.testInfo.getTick());
        }
    }

    public void assertEntityPresent(EntityType<?> type) {
        List<? extends Entity> entities = this.getLevel().getEntities(type, this.getBounds(), Entity::isAlive);
        if (entities.isEmpty()) {
            throw new GameTestAssertException("Expected " + type.toShortString() + " to exist");
        }
    }

    public void assertEntityPresent(EntityType<?> type, int x, int y, int z) {
        this.assertEntityPresent(type, new BlockPos(x, y, z));
    }

    public void assertEntityPresent(EntityType<?> type, BlockPos pos) {
        BlockPos blockPos = this.absolutePos(pos);
        List<? extends Entity> entities = this.getLevel().getEntities(type, new AABB(blockPos), Entity::isAlive);
        if (entities.isEmpty()) {
            throw new GameTestAssertPosException("Expected " + type.toShortString(), blockPos, pos, this.testInfo.getTick());
        }
    }

    public void assertEntityPresent(EntityType<?> type, AABB box) {
        AABB aabb = this.absoluteAABB(box);
        List<? extends Entity> entities = this.getLevel().getEntities(type, aabb, Entity::isAlive);
        if (entities.isEmpty()) {
            throw new GameTestAssertPosException(
                "Expected " + type.toShortString(), BlockPos.containing(aabb.getCenter()), BlockPos.containing(box.getCenter()), this.testInfo.getTick()
            );
        }
    }

    public void assertEntitiesPresent(EntityType<?> entityType, int count) {
        List<? extends Entity> entities = this.getLevel().getEntities(entityType, this.getBounds(), Entity::isAlive);
        if (entities.size() != count) {
            throw new GameTestAssertException("Expected " + count + " of type " + entityType.toShortString() + " to exist, found " + entities.size());
        }
    }

    public void assertEntitiesPresent(EntityType<?> entityType, BlockPos pos, int count, double radius) {
        BlockPos blockPos = this.absolutePos(pos);
        List<? extends Entity> entities = this.getEntities((EntityType<? extends Entity>)entityType, pos, radius);
        if (entities.size() != count) {
            throw new GameTestAssertPosException(
                "Expected " + count + " entities of type " + entityType.toShortString() + ", actual number of entities found=" + entities.size(),
                blockPos,
                pos,
                this.testInfo.getTick()
            );
        }
    }

    public void assertEntityPresent(EntityType<?> type, BlockPos pos, double expansionAmount) {
        List<? extends Entity> entities = this.getEntities((EntityType<? extends Entity>)type, pos, expansionAmount);
        if (entities.isEmpty()) {
            BlockPos blockPos = this.absolutePos(pos);
            throw new GameTestAssertPosException("Expected " + type.toShortString(), blockPos, pos, this.testInfo.getTick());
        }
    }

    public <T extends Entity> List<T> getEntities(EntityType<T> entityType, BlockPos pos, double radius) {
        BlockPos blockPos = this.absolutePos(pos);
        return this.getLevel().getEntities(entityType, new AABB(blockPos).inflate(radius), Entity::isAlive);
    }

    public <T extends Entity> List<T> getEntities(EntityType<T> entityType) {
        return this.getLevel().getEntities(entityType, this.getBounds(), Entity::isAlive);
    }

    public void assertEntityInstancePresent(Entity entity, int x, int y, int z) {
        this.assertEntityInstancePresent(entity, new BlockPos(x, y, z));
    }

    public void assertEntityInstancePresent(Entity entity, BlockPos pos) {
        BlockPos blockPos = this.absolutePos(pos);
        List<? extends Entity> entities = this.getLevel().getEntities(entity.getType(), new AABB(blockPos), Entity::isAlive);
        entities.stream()
            .filter(entity1 -> entity1 == entity)
            .findFirst()
            .orElseThrow(() -> new GameTestAssertPosException("Expected " + entity.getType().toShortString(), blockPos, pos, this.testInfo.getTick()));
    }

    public void assertItemEntityCountIs(Item item, BlockPos pos, double expansionAmount, int count) {
        BlockPos blockPos = this.absolutePos(pos);
        List<ItemEntity> entities = this.getLevel().getEntities(EntityType.ITEM, new AABB(blockPos).inflate(expansionAmount), Entity::isAlive);
        int i = 0;

        for (ItemEntity itemEntity : entities) {
            ItemStack item1 = itemEntity.getItem();
            if (item1.is(item)) {
                i += item1.getCount();
            }
        }

        if (i != count) {
            throw new GameTestAssertPosException(
                "Expected " + count + " " + item.getName().getString() + " items to exist (found " + i + ")", blockPos, pos, this.testInfo.getTick()
            );
        }
    }

    public void assertItemEntityPresent(Item item, BlockPos pos, double expansionAmount) {
        BlockPos blockPos = this.absolutePos(pos);

        for (Entity entity : this.getLevel().getEntities(EntityType.ITEM, new AABB(blockPos).inflate(expansionAmount), Entity::isAlive)) {
            ItemEntity itemEntity = (ItemEntity)entity;
            if (itemEntity.getItem().getItem().equals(item)) {
                return;
            }
        }

        throw new GameTestAssertPosException("Expected " + item.getName().getString() + " item", blockPos, pos, this.testInfo.getTick());
    }

    public void assertItemEntityNotPresent(Item item, BlockPos pos, double radius) {
        BlockPos blockPos = this.absolutePos(pos);

        for (Entity entity : this.getLevel().getEntities(EntityType.ITEM, new AABB(blockPos).inflate(radius), Entity::isAlive)) {
            ItemEntity itemEntity = (ItemEntity)entity;
            if (itemEntity.getItem().getItem().equals(item)) {
                throw new GameTestAssertPosException("Did not expect " + item.getName().getString() + " item", blockPos, pos, this.testInfo.getTick());
            }
        }
    }

    public void assertItemEntityPresent(Item item) {
        for (Entity entity : this.getLevel().getEntities(EntityType.ITEM, this.getBounds(), Entity::isAlive)) {
            ItemEntity itemEntity = (ItemEntity)entity;
            if (itemEntity.getItem().getItem().equals(item)) {
                return;
            }
        }

        throw new GameTestAssertException("Expected " + item.getName().getString() + " item");
    }

    public void assertItemEntityNotPresent(Item item) {
        for (Entity entity : this.getLevel().getEntities(EntityType.ITEM, this.getBounds(), Entity::isAlive)) {
            ItemEntity itemEntity = (ItemEntity)entity;
            if (itemEntity.getItem().getItem().equals(item)) {
                throw new GameTestAssertException("Did not expect " + item.getName().getString() + " item");
            }
        }
    }

    public void assertEntityNotPresent(EntityType<?> type) {
        List<? extends Entity> entities = this.getLevel().getEntities(type, this.getBounds(), Entity::isAlive);
        if (!entities.isEmpty()) {
            throw new GameTestAssertException("Did not expect " + type.toShortString() + " to exist");
        }
    }

    public void assertEntityNotPresent(EntityType<?> type, int x, int y, int z) {
        this.assertEntityNotPresent(type, new BlockPos(x, y, z));
    }

    public void assertEntityNotPresent(EntityType<?> type, BlockPos pos) {
        BlockPos blockPos = this.absolutePos(pos);
        List<? extends Entity> entities = this.getLevel().getEntities(type, new AABB(blockPos), Entity::isAlive);
        if (!entities.isEmpty()) {
            throw new GameTestAssertPosException("Did not expect " + type.toShortString(), blockPos, pos, this.testInfo.getTick());
        }
    }

    public void assertEntityNotPresent(EntityType<?> type, AABB box) {
        AABB aabb = this.absoluteAABB(box);
        List<? extends Entity> entities = this.getLevel().getEntities(type, aabb, Entity::isAlive);
        if (!entities.isEmpty()) {
            throw new GameTestAssertPosException(
                "Did not expect " + type.toShortString(), BlockPos.containing(aabb.getCenter()), BlockPos.containing(box.getCenter()), this.testInfo.getTick()
            );
        }
    }

    public void assertEntityTouching(EntityType<?> type, double x, double y, double z) {
        Vec3 vec3 = new Vec3(x, y, z);
        Vec3 vec31 = this.absoluteVec(vec3);
        Predicate<? super Entity> predicate = entity -> entity.getBoundingBox().intersects(vec31, vec31);
        List<? extends Entity> entities = this.getLevel().getEntities(type, this.getBounds(), predicate);
        if (entities.isEmpty()) {
            throw new GameTestAssertException("Expected " + type.toShortString() + " to touch " + vec31 + " (relative " + vec3 + ")");
        }
    }

    public void assertEntityNotTouching(EntityType<?> type, double x, double y, double z) {
        Vec3 vec3 = new Vec3(x, y, z);
        Vec3 vec31 = this.absoluteVec(vec3);
        Predicate<? super Entity> predicate = entity -> !entity.getBoundingBox().intersects(vec31, vec31);
        List<? extends Entity> entities = this.getLevel().getEntities(type, this.getBounds(), predicate);
        if (entities.isEmpty()) {
            throw new GameTestAssertException("Did not expect " + type.toShortString() + " to touch " + vec31 + " (relative " + vec3 + ")");
        }
    }

    public <E extends Entity, T> void assertEntityData(BlockPos pos, EntityType<E> type, Predicate<E> predicate) {
        BlockPos blockPos = this.absolutePos(pos);
        List<E> entities = this.getLevel().getEntities(type, new AABB(blockPos), Entity::isAlive);
        if (entities.isEmpty()) {
            throw new GameTestAssertPosException("Expected " + type.toShortString(), blockPos, pos, this.testInfo.getTick());
        } else {
            for (E entity : entities) {
                if (!predicate.test(entity)) {
                    throw new GameTestAssertException("Test failed for entity " + entity);
                }
            }
        }
    }

    public <E extends Entity, T> void assertEntityData(BlockPos pos, EntityType<E> type, Function<? super E, T> entityDataGetter, @Nullable T testEntityData) {
        BlockPos blockPos = this.absolutePos(pos);
        List<E> entities = this.getLevel().getEntities(type, new AABB(blockPos), Entity::isAlive);
        if (entities.isEmpty()) {
            throw new GameTestAssertPosException("Expected " + type.toShortString(), blockPos, pos, this.testInfo.getTick());
        } else {
            for (E entity : entities) {
                T object = entityDataGetter.apply(entity);
                if (!Objects.equals(object, testEntityData)) {
                    throw new GameTestAssertException("Expected entity data to be: " + testEntityData + ", but was: " + object);
                }
            }
        }
    }

    public <E extends LivingEntity> void assertEntityIsHolding(BlockPos pos, EntityType<E> entityType, Item item) {
        BlockPos blockPos = this.absolutePos(pos);
        List<E> entities = this.getLevel().getEntities(entityType, new AABB(blockPos), Entity::isAlive);
        if (entities.isEmpty()) {
            throw new GameTestAssertPosException("Expected entity of type: " + entityType, blockPos, pos, this.getTick());
        } else {
            for (E livingEntity : entities) {
                if (livingEntity.isHolding(item)) {
                    return;
                }
            }

            throw new GameTestAssertPosException("Entity should be holding: " + item, blockPos, pos, this.getTick());
        }
    }

    public <E extends Entity & InventoryCarrier> void assertEntityInventoryContains(BlockPos pos, EntityType<E> entityType, Item item) {
        BlockPos blockPos = this.absolutePos(pos);
        List<E> entities = this.getLevel().getEntities(entityType, new AABB(blockPos), object -> object.isAlive());
        if (entities.isEmpty()) {
            throw new GameTestAssertPosException("Expected " + entityType.toShortString() + " to exist", blockPos, pos, this.getTick());
        } else {
            for (E entity : entities) {
                if (entity.getInventory().hasAnyMatching(stack -> stack.is(item))) {
                    return;
                }
            }

            throw new GameTestAssertPosException("Entity inventory should contain: " + item, blockPos, pos, this.getTick());
        }
    }

    public void assertContainerEmpty(BlockPos pos) {
        BlockPos blockPos = this.absolutePos(pos);
        BlockEntity blockEntity = this.getLevel().getBlockEntity(blockPos);
        if (blockEntity instanceof BaseContainerBlockEntity && !((BaseContainerBlockEntity)blockEntity).isEmpty()) {
            throw new GameTestAssertException("Container should be empty");
        }
    }

    public void assertContainerContains(BlockPos pos, Item item) {
        BlockPos blockPos = this.absolutePos(pos);
        BlockEntity blockEntity = this.getLevel().getBlockEntity(blockPos);
        if (!(blockEntity instanceof BaseContainerBlockEntity)) {
            ResourceLocation resourceLocation = blockEntity != null ? BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()) : null;
            throw new GameTestAssertException("Expected a container at " + pos + ", found " + resourceLocation);
        } else if (((BaseContainerBlockEntity)blockEntity).countItem(item) != 1) {
            throw new GameTestAssertException("Container should contain: " + item);
        }
    }

    public void assertSameBlockStates(BoundingBox boundingBox, BlockPos pos) {
        BlockPos.betweenClosedStream(boundingBox).forEach(blockPos -> {
            BlockPos blockPos1 = pos.offset(blockPos.getX() - boundingBox.minX(), blockPos.getY() - boundingBox.minY(), blockPos.getZ() - boundingBox.minZ());
            this.assertSameBlockState(blockPos, blockPos1);
        });
    }

    public void assertSameBlockState(BlockPos testPos, BlockPos comparisonPos) {
        BlockState blockState = this.getBlockState(testPos);
        BlockState blockState1 = this.getBlockState(comparisonPos);
        if (blockState != blockState1) {
            this.fail("Incorrect state. Expected " + blockState1 + ", got " + blockState, testPos);
        }
    }

    public void assertAtTickTimeContainerContains(long tickTime, BlockPos pos, Item item) {
        this.runAtTickTime(tickTime, () -> this.assertContainerContains(pos, item));
    }

    public void assertAtTickTimeContainerEmpty(long tickTime, BlockPos pos) {
        this.runAtTickTime(tickTime, () -> this.assertContainerEmpty(pos));
    }

    public <E extends Entity, T> void succeedWhenEntityData(BlockPos pos, EntityType<E> type, Function<E, T> entityDataGetter, T testEntityData) {
        this.succeedWhen(() -> this.assertEntityData(pos, type, entityDataGetter, testEntityData));
    }

    public void assertEntityPosition(Entity entity, AABB box, String exceptionMessage) {
        if (!box.contains(this.relativeVec(entity.position()))) {
            this.fail(exceptionMessage);
        }
    }

    public <E extends Entity> void assertEntityProperty(E entity, Predicate<E> predicate, String name) {
        if (!predicate.test(entity)) {
            throw new GameTestAssertException("Entity " + entity + " failed " + name + " test");
        }
    }

    public <E extends Entity, T> void assertEntityProperty(E entity, Function<E, T> entityPropertyGetter, String valueName, T testEntityProperty) {
        T object = entityPropertyGetter.apply(entity);
        if (!object.equals(testEntityProperty)) {
            throw new GameTestAssertException("Entity " + entity + " value " + valueName + "=" + object + " is not equal to expected " + testEntityProperty);
        }
    }

    public void assertLivingEntityHasMobEffect(LivingEntity entity, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance effect1 = entity.getEffect(effect);
        if (effect1 == null || effect1.getAmplifier() != amplifier) {
            int i = amplifier + 1;
            throw new GameTestAssertException("Entity " + entity + " failed has " + effect.value().getDescriptionId() + " x " + i + " test");
        }
    }

    public void succeedWhenEntityPresent(EntityType<?> type, int x, int y, int z) {
        this.succeedWhenEntityPresent(type, new BlockPos(x, y, z));
    }

    public void succeedWhenEntityPresent(EntityType<?> type, BlockPos pos) {
        this.succeedWhen(() -> this.assertEntityPresent(type, pos));
    }

    public void succeedWhenEntityNotPresent(EntityType<?> type, int x, int y, int z) {
        this.succeedWhenEntityNotPresent(type, new BlockPos(x, y, z));
    }

    public void succeedWhenEntityNotPresent(EntityType<?> type, BlockPos pos) {
        this.succeedWhen(() -> this.assertEntityNotPresent(type, pos));
    }

    public void succeed() {
        this.testInfo.succeed();
    }

    private void ensureSingleFinalCheck() {
        if (this.finalCheckAdded) {
            throw new IllegalStateException("This test already has final clause");
        } else {
            this.finalCheckAdded = true;
        }
    }

    public void succeedIf(Runnable criterion) {
        this.ensureSingleFinalCheck();
        this.testInfo.createSequence().thenWaitUntil(0L, criterion).thenSucceed();
    }

    public void succeedWhen(Runnable criterion) {
        this.ensureSingleFinalCheck();
        this.testInfo.createSequence().thenWaitUntil(criterion).thenSucceed();
    }

    public void succeedOnTickWhen(int tick, Runnable criterion) {
        this.ensureSingleFinalCheck();
        this.testInfo.createSequence().thenWaitUntil(tick, criterion).thenSucceed();
    }

    public void runAtTickTime(long tickTime, Runnable task) {
        this.testInfo.setRunAtTickTime(tickTime, task);
    }

    public void runAfterDelay(long delay, Runnable task) {
        this.runAtTickTime(this.testInfo.getTick() + delay, task);
    }

    public void randomTick(BlockPos pos) {
        BlockPos blockPos = this.absolutePos(pos);
        ServerLevel level = this.getLevel();
        level.getBlockState(blockPos).randomTick(level, blockPos, level.random);
    }

    public void tickPrecipitation(BlockPos pos) {
        BlockPos blockPos = this.absolutePos(pos);
        ServerLevel level = this.getLevel();
        level.tickPrecipitation(blockPos);
    }

    public void tickPrecipitation() {
        AABB relativeBounds = this.getRelativeBounds();
        int i = (int)Math.floor(relativeBounds.maxX);
        int i1 = (int)Math.floor(relativeBounds.maxZ);
        int i2 = (int)Math.floor(relativeBounds.maxY);

        for (int i3 = (int)Math.floor(relativeBounds.minX); i3 < i; i3++) {
            for (int i4 = (int)Math.floor(relativeBounds.minZ); i4 < i1; i4++) {
                this.tickPrecipitation(new BlockPos(i3, i2, i4));
            }
        }
    }

    public int getHeight(Heightmap.Types heightmapType, int x, int z) {
        BlockPos blockPos = this.absolutePos(new BlockPos(x, 0, z));
        return this.relativePos(this.getLevel().getHeightmapPos(heightmapType, blockPos)).getY();
    }

    public void fail(String exceptionMessage, BlockPos pos) {
        throw new GameTestAssertPosException(exceptionMessage, this.absolutePos(pos), pos, this.getTick());
    }

    public void fail(String exceptionMessage, Entity entity) {
        throw new GameTestAssertPosException(exceptionMessage, entity.blockPosition(), this.relativePos(entity.blockPosition()), this.getTick());
    }

    public void fail(String exceptionMessage) {
        throw new GameTestAssertException(exceptionMessage);
    }

    public void failIf(Runnable criterion) {
        this.testInfo.createSequence().thenWaitUntil(criterion).thenFail(() -> new GameTestAssertException("Fail conditions met"));
    }

    public void failIfEver(Runnable criterion) {
        LongStream.range(this.testInfo.getTick(), this.testInfo.getTimeoutTicks()).forEach(l -> this.testInfo.setRunAtTickTime(l, criterion::run));
    }

    public GameTestSequence startSequence() {
        return this.testInfo.createSequence();
    }

    public BlockPos absolutePos(BlockPos pos) {
        BlockPos testOrigin = this.testInfo.getTestOrigin();
        BlockPos blockPos = testOrigin.offset(pos);
        return StructureTemplate.transform(blockPos, Mirror.NONE, this.testInfo.getRotation(), testOrigin);
    }

    public BlockPos relativePos(BlockPos pos) {
        BlockPos testOrigin = this.testInfo.getTestOrigin();
        Rotation rotated = this.testInfo.getRotation().getRotated(Rotation.CLOCKWISE_180);
        BlockPos blockPos = StructureTemplate.transform(pos, Mirror.NONE, rotated, testOrigin);
        return blockPos.subtract(testOrigin);
    }

    public AABB absoluteAABB(AABB aabb) {
        Vec3 vec3 = this.absoluteVec(aabb.getMinPosition());
        Vec3 vec31 = this.absoluteVec(aabb.getMaxPosition());
        return new AABB(vec3, vec31);
    }

    public AABB relativeAABB(AABB aabb) {
        Vec3 vec3 = this.relativeVec(aabb.getMinPosition());
        Vec3 vec31 = this.relativeVec(aabb.getMaxPosition());
        return new AABB(vec3, vec31);
    }

    public Vec3 absoluteVec(Vec3 relativeVec3) {
        Vec3 vec3 = Vec3.atLowerCornerOf(this.testInfo.getTestOrigin());
        return StructureTemplate.transform(vec3.add(relativeVec3), Mirror.NONE, this.testInfo.getRotation(), this.testInfo.getTestOrigin());
    }

    public Vec3 relativeVec(Vec3 absoluteVec3) {
        Vec3 vec3 = Vec3.atLowerCornerOf(this.testInfo.getTestOrigin());
        return StructureTemplate.transform(absoluteVec3.subtract(vec3), Mirror.NONE, this.testInfo.getRotation(), this.testInfo.getTestOrigin());
    }

    public Rotation getTestRotation() {
        return this.testInfo.getRotation();
    }

    public void assertTrue(boolean condition, String failureMessage) {
        if (!condition) {
            throw new GameTestAssertException(failureMessage);
        }
    }

    public <N> void assertValueEqual(N actual, N expected, String valueName) {
        if (!actual.equals(expected)) {
            throw new GameTestAssertException("Expected " + valueName + " to be " + expected + ", but was " + actual);
        }
    }

    public void assertFalse(boolean condition, String failureMessage) {
        if (condition) {
            throw new GameTestAssertException(failureMessage);
        }
    }

    public long getTick() {
        return this.testInfo.getTick();
    }

    public AABB getBounds() {
        return this.testInfo.getStructureBounds();
    }

    private AABB getRelativeBounds() {
        AABB structureBounds = this.testInfo.getStructureBounds();
        Rotation rotation = this.testInfo.getRotation();
        switch (rotation) {
            case COUNTERCLOCKWISE_90:
            case CLOCKWISE_90:
                return new AABB(0.0, 0.0, 0.0, structureBounds.getZsize(), structureBounds.getYsize(), structureBounds.getXsize());
            default:
                return new AABB(0.0, 0.0, 0.0, structureBounds.getXsize(), structureBounds.getYsize(), structureBounds.getZsize());
        }
    }

    public void forEveryBlockInStructure(Consumer<BlockPos> consumer) {
        AABB aabb = this.getRelativeBounds().contract(1.0, 1.0, 1.0);
        BlockPos.MutableBlockPos.betweenClosedStream(aabb).forEach(consumer);
    }

    public void onEachTick(Runnable task) {
        LongStream.range(this.testInfo.getTick(), this.testInfo.getTimeoutTicks()).forEach(l -> this.testInfo.setRunAtTickTime(l, task::run));
    }

    public void placeAt(Player player, ItemStack stack, BlockPos pos, Direction direction) {
        BlockPos blockPos = this.absolutePos(pos.relative(direction));
        BlockHitResult blockHitResult = new BlockHitResult(Vec3.atCenterOf(blockPos), direction, blockPos, false);
        UseOnContext useOnContext = new UseOnContext(player, InteractionHand.MAIN_HAND, blockHitResult);
        stack.useOn(useOnContext);
    }

    public void setBiome(ResourceKey<Biome> biome) {
        AABB bounds = this.getBounds();
        BlockPos blockPos = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos blockPos1 = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        Either<Integer, CommandSyntaxException> either = FillBiomeCommand.fill(
            this.getLevel(), blockPos, blockPos1, this.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(biome)
        );
        if (either.right().isPresent()) {
            this.fail("Failed to set biome for test");
        }
    }
}
