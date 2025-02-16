package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.slf4j.Logger;

public class BeehiveBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_FLOWER_POS = "flower_pos";
    private static final String BEES = "bees";
    static final List<String> IGNORED_BEE_TAGS = Arrays.asList(
        "Air",
        "ArmorDropChances",
        "ArmorItems",
        "Brain",
        "CanPickUpLoot",
        "DeathTime",
        "FallDistance",
        "FallFlying",
        "Fire",
        "HandDropChances",
        "HandItems",
        "HurtByTimestamp",
        "HurtTime",
        "LeftHanded",
        "Motion",
        "NoGravity",
        "OnGround",
        "PortalCooldown",
        "Pos",
        "Rotation",
        "SleepingX",
        "SleepingY",
        "SleepingZ",
        "CannotEnterHiveTicks",
        "TicksSincePollination",
        "CropsGrownSincePollination",
        "hive_pos",
        "Passengers",
        "leash",
        "UUID"
    );
    public static final int MAX_OCCUPANTS = 3;
    private static final int MIN_TICKS_BEFORE_REENTERING_HIVE = 400;
    private static final int MIN_OCCUPATION_TICKS_NECTAR = 2400;
    public static final int MIN_OCCUPATION_TICKS_NECTARLESS = 600;
    private final List<BeehiveBlockEntity.BeeData> stored = Lists.newArrayList();
    @Nullable
    private BlockPos savedFlowerPos;

    public BeehiveBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.BEEHIVE, pos, blockState);
    }

    @Override
    public void setChanged() {
        if (this.isFireNearby()) {
            this.emptyAllLivingFromHive(null, this.level.getBlockState(this.getBlockPos()), BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        }

        super.setChanged();
    }

    public boolean isFireNearby() {
        if (this.level == null) {
            return false;
        } else {
            for (BlockPos blockPos : BlockPos.betweenClosed(this.worldPosition.offset(-1, -1, -1), this.worldPosition.offset(1, 1, 1))) {
                if (this.level.getBlockState(blockPos).getBlock() instanceof FireBlock) {
                    return true;
                }
            }

            return false;
        }
    }

    public boolean isEmpty() {
        return this.stored.isEmpty();
    }

    public boolean isFull() {
        return this.stored.size() == 3;
    }

    public void emptyAllLivingFromHive(@Nullable Player player, BlockState state, BeehiveBlockEntity.BeeReleaseStatus releaseStatus) {
        List<Entity> list = this.releaseAllOccupants(state, releaseStatus);
        if (player != null) {
            for (Entity entity : list) {
                if (entity instanceof Bee) {
                    Bee bee = (Bee)entity;
                    if (player.position().distanceToSqr(entity.position()) <= 16.0) {
                        if (!this.isSedated()) {
                            bee.setTarget(player);
                        } else {
                            bee.setStayOutOfHiveCountdown(400);
                        }
                    }
                }
            }
        }
    }

    private List<Entity> releaseAllOccupants(BlockState state, BeehiveBlockEntity.BeeReleaseStatus releaseStatus) {
        List<Entity> list = Lists.newArrayList();
        this.stored.removeIf(data -> releaseOccupant(this.level, this.worldPosition, state, data.toOccupant(), list, releaseStatus, this.savedFlowerPos));
        if (!list.isEmpty()) {
            super.setChanged();
        }

        return list;
    }

    @VisibleForDebug
    public int getOccupantCount() {
        return this.stored.size();
    }

    public static int getHoneyLevel(BlockState state) {
        return state.getValue(BeehiveBlock.HONEY_LEVEL);
    }

    @VisibleForDebug
    public boolean isSedated() {
        return CampfireBlock.isSmokeyPos(this.level, this.getBlockPos());
    }

    public void addOccupant(Bee bee) {
        if (this.stored.size() < 3) {
            bee.stopRiding();
            bee.ejectPassengers();
            bee.dropLeash();
            this.storeBee(BeehiveBlockEntity.Occupant.of(bee));
            if (this.level != null) {
                if (bee.hasSavedFlowerPos() && (!this.hasSavedFlowerPos() || this.level.random.nextBoolean())) {
                    this.savedFlowerPos = bee.getSavedFlowerPos();
                }

                BlockPos blockPos = this.getBlockPos();
                this.level
                    .playSound(
                        null,
                        (double)blockPos.getX(),
                        (double)blockPos.getY(),
                        (double)blockPos.getZ(),
                        SoundEvents.BEEHIVE_ENTER,
                        SoundSource.BLOCKS,
                        1.0F,
                        1.0F
                    );
                this.level.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(bee, this.getBlockState()));
            }

            bee.discard();
            super.setChanged();
        }
    }

    public void storeBee(BeehiveBlockEntity.Occupant occupant) {
        this.stored.add(new BeehiveBlockEntity.BeeData(occupant));
    }

    private static boolean releaseOccupant(
        Level level,
        BlockPos pos,
        BlockState state,
        BeehiveBlockEntity.Occupant occupant,
        @Nullable List<Entity> storedInHives,
        BeehiveBlockEntity.BeeReleaseStatus releaseStatus,
        @Nullable BlockPos storedFlowerPos
    ) {
        if (Bee.isNightOrRaining(level) && releaseStatus != BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY) {
            return false;
        } else {
            Direction direction = state.getValue(BeehiveBlock.FACING);
            BlockPos blockPos = pos.relative(direction);
            boolean flag = !level.getBlockState(blockPos).getCollisionShape(level, blockPos).isEmpty();
            if (flag && releaseStatus != BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY) {
                return false;
            } else {
                Entity entity = occupant.createEntity(level, pos);
                if (entity != null) {
                    if (entity instanceof Bee bee) {
                        if (storedFlowerPos != null && !bee.hasSavedFlowerPos() && level.random.nextFloat() < 0.9F) {
                            bee.setSavedFlowerPos(storedFlowerPos);
                        }

                        if (releaseStatus == BeehiveBlockEntity.BeeReleaseStatus.HONEY_DELIVERED) {
                            bee.dropOffNectar();
                            if (state.is(BlockTags.BEEHIVES, blockStateBase -> blockStateBase.hasProperty(BeehiveBlock.HONEY_LEVEL))) {
                                int honeyLevel = getHoneyLevel(state);
                                if (honeyLevel < 5) {
                                    int i = level.random.nextInt(100) == 0 ? 2 : 1;
                                    if (honeyLevel + i > 5) {
                                        i--;
                                    }

                                    level.setBlockAndUpdate(pos, state.setValue(BeehiveBlock.HONEY_LEVEL, Integer.valueOf(honeyLevel + i)));
                                }
                            }
                        }

                        if (storedInHives != null) {
                            storedInHives.add(bee);
                        }

                        float bbWidth = entity.getBbWidth();
                        double d = flag ? 0.0 : 0.55 + bbWidth / 2.0F;
                        double d1 = pos.getX() + 0.5 + d * direction.getStepX();
                        double d2 = pos.getY() + 0.5 - entity.getBbHeight() / 2.0F;
                        double d3 = pos.getZ() + 0.5 + d * direction.getStepZ();
                        entity.moveTo(d1, d2, d3, entity.getYRot(), entity.getXRot());
                    }

                    level.playSound(null, pos, SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(entity, level.getBlockState(pos)));
                    return level.addFreshEntity(entity);
                } else {
                    return false;
                }
            }
        }
    }

    private boolean hasSavedFlowerPos() {
        return this.savedFlowerPos != null;
    }

    private static void tickOccupants(Level level, BlockPos pos, BlockState state, List<BeehiveBlockEntity.BeeData> data, @Nullable BlockPos savedFlowerPos) {
        boolean flag = false;
        Iterator<BeehiveBlockEntity.BeeData> iterator = data.iterator();

        while (iterator.hasNext()) {
            BeehiveBlockEntity.BeeData beeData = iterator.next();
            if (beeData.tick()) {
                BeehiveBlockEntity.BeeReleaseStatus beeReleaseStatus = beeData.hasNectar()
                    ? BeehiveBlockEntity.BeeReleaseStatus.HONEY_DELIVERED
                    : BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED;
                if (releaseOccupant(level, pos, state, beeData.toOccupant(), null, beeReleaseStatus, savedFlowerPos)) {
                    flag = true;
                    iterator.remove();
                }
            }
        }

        if (flag) {
            setChanged(level, pos, state);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BeehiveBlockEntity beehive) {
        tickOccupants(level, pos, state, beehive.stored, beehive.savedFlowerPos);
        if (!beehive.stored.isEmpty() && level.getRandom().nextDouble() < 0.005) {
            double d = pos.getX() + 0.5;
            double d1 = pos.getY();
            double d2 = pos.getZ() + 0.5;
            level.playSound(null, d, d1, d2, SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        DebugPackets.sendHiveInfo(level, pos, state, beehive);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.stored.clear();
        if (tag.contains("bees")) {
            BeehiveBlockEntity.Occupant.LIST_CODEC
                .parse(NbtOps.INSTANCE, tag.get("bees"))
                .resultOrPartial(string -> LOGGER.error("Failed to parse bees: '{}'", string))
                .ifPresent(bees -> bees.forEach(this::storeBee));
        }

        this.savedFlowerPos = NbtUtils.readBlockPos(tag, "flower_pos").orElse(null);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("bees", BeehiveBlockEntity.Occupant.LIST_CODEC.encodeStart(NbtOps.INSTANCE, this.getBees()).getOrThrow());
        if (this.hasSavedFlowerPos()) {
            tag.put("flower_pos", NbtUtils.writeBlockPos(this.savedFlowerPos));
        }
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        this.stored.clear();
        List<BeehiveBlockEntity.Occupant> list = componentInput.getOrDefault(DataComponents.BEES, List.of());
        list.forEach(this::storeBee);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.BEES, this.getBees());
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("bees");
    }

    private List<BeehiveBlockEntity.Occupant> getBees() {
        return this.stored.stream().map(BeehiveBlockEntity.BeeData::toOccupant).toList();
    }

    static class BeeData {
        private final BeehiveBlockEntity.Occupant occupant;
        private int ticksInHive;

        BeeData(BeehiveBlockEntity.Occupant occupant) {
            this.occupant = occupant;
            this.ticksInHive = occupant.ticksInHive();
        }

        public boolean tick() {
            return this.ticksInHive++ > this.occupant.minTicksInHive;
        }

        public BeehiveBlockEntity.Occupant toOccupant() {
            return new BeehiveBlockEntity.Occupant(this.occupant.entityData, this.ticksInHive, this.occupant.minTicksInHive);
        }

        public boolean hasNectar() {
            return this.occupant.entityData.getUnsafe().getBoolean("HasNectar");
        }
    }

    public static enum BeeReleaseStatus {
        HONEY_DELIVERED,
        BEE_RELEASED,
        EMERGENCY;
    }

    public record Occupant(CustomData entityData, int ticksInHive, int minTicksInHive) {
        public static final Codec<BeehiveBlockEntity.Occupant> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    CustomData.CODEC.optionalFieldOf("entity_data", CustomData.EMPTY).forGetter(BeehiveBlockEntity.Occupant::entityData),
                    Codec.INT.fieldOf("ticks_in_hive").forGetter(BeehiveBlockEntity.Occupant::ticksInHive),
                    Codec.INT.fieldOf("min_ticks_in_hive").forGetter(BeehiveBlockEntity.Occupant::minTicksInHive)
                )
                .apply(instance, BeehiveBlockEntity.Occupant::new)
        );
        public static final Codec<List<BeehiveBlockEntity.Occupant>> LIST_CODEC = CODEC.listOf();
        public static final StreamCodec<ByteBuf, BeehiveBlockEntity.Occupant> STREAM_CODEC = StreamCodec.composite(
            CustomData.STREAM_CODEC,
            BeehiveBlockEntity.Occupant::entityData,
            ByteBufCodecs.VAR_INT,
            BeehiveBlockEntity.Occupant::ticksInHive,
            ByteBufCodecs.VAR_INT,
            BeehiveBlockEntity.Occupant::minTicksInHive,
            BeehiveBlockEntity.Occupant::new
        );

        public static BeehiveBlockEntity.Occupant of(Entity entity) {
            CompoundTag compoundTag = new CompoundTag();
            entity.save(compoundTag);
            BeehiveBlockEntity.IGNORED_BEE_TAGS.forEach(compoundTag::remove);
            boolean _boolean = compoundTag.getBoolean("HasNectar");
            return new BeehiveBlockEntity.Occupant(CustomData.of(compoundTag), 0, _boolean ? 2400 : 600);
        }

        public static BeehiveBlockEntity.Occupant create(int ticksInHive) {
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.BEE).toString());
            return new BeehiveBlockEntity.Occupant(CustomData.of(compoundTag), ticksInHive, 600);
        }

        @Nullable
        public Entity createEntity(Level level, BlockPos pos) {
            CompoundTag compoundTag = this.entityData.copyTag();
            BeehiveBlockEntity.IGNORED_BEE_TAGS.forEach(compoundTag::remove);
            Entity entity = EntityType.loadEntityRecursive(compoundTag, level, EntitySpawnReason.LOAD, entity1 -> entity1);
            if (entity != null && entity.getType().is(EntityTypeTags.BEEHIVE_INHABITORS)) {
                entity.setNoGravity(true);
                if (entity instanceof Bee bee) {
                    bee.setHivePos(pos);
                    setBeeReleaseData(this.ticksInHive, bee);
                }

                return entity;
            } else {
                return null;
            }
        }

        private static void setBeeReleaseData(int ticksInHive, Bee bee) {
            int age = bee.getAge();
            if (age < 0) {
                bee.setAge(Math.min(0, age + ticksInHive));
            } else if (age > 0) {
                bee.setAge(Math.max(0, age - ticksInHive));
            }

            bee.setInLoveTime(Math.max(0, bee.getInLoveTime() - ticksInHive));
        }
    }
}
