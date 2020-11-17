package net.minecraft.world.entity.decoration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class Painting extends HangingEntity implements VariantHolder<Holder<PaintingVariant>> {
    private static final EntityDataAccessor<Holder<PaintingVariant>> DATA_PAINTING_VARIANT_ID = SynchedEntityData.defineId(
        Painting.class, EntityDataSerializers.PAINTING_VARIANT
    );
    public static final MapCodec<Holder<PaintingVariant>> VARIANT_MAP_CODEC = PaintingVariant.CODEC.fieldOf("variant");
    public static final Codec<Holder<PaintingVariant>> VARIANT_CODEC = VARIANT_MAP_CODEC.codec();
    public static final float DEPTH = 0.0625F;

    public Painting(EntityType<? extends Painting> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PAINTING_VARIANT_ID, this.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT).getAny().orElseThrow());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_PAINTING_VARIANT_ID.equals(key)) {
            this.recalculateBoundingBox();
        }
    }

    @Override
    public void setVariant(Holder<PaintingVariant> variant) {
        this.entityData.set(DATA_PAINTING_VARIANT_ID, variant);
    }

    @Override
    public Holder<PaintingVariant> getVariant() {
        return this.entityData.get(DATA_PAINTING_VARIANT_ID);
    }

    public static Optional<Painting> create(Level level, BlockPos pos, Direction direction) {
        Painting painting = new Painting(level, pos);
        List<Holder<PaintingVariant>> list = new ArrayList<>();
        level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT).getTagOrEmpty(PaintingVariantTags.PLACEABLE).forEach(list::add);
        if (list.isEmpty()) {
            return Optional.empty();
        } else {
            painting.setDirection(direction);
            list.removeIf(holder -> {
                painting.setVariant((Holder<PaintingVariant>)holder);
                return !painting.survives();
            });
            if (list.isEmpty()) {
                return Optional.empty();
            } else {
                int i = list.stream().mapToInt(Painting::variantArea).max().orElse(0);
                list.removeIf(holder -> variantArea((Holder<PaintingVariant>)holder) < i);
                Optional<Holder<PaintingVariant>> randomSafe = Util.getRandomSafe(list, painting.random);
                if (randomSafe.isEmpty()) {
                    return Optional.empty();
                } else {
                    painting.setVariant(randomSafe.get());
                    painting.setDirection(direction);
                    return Optional.of(painting);
                }
            }
        }
    }

    private static int variantArea(Holder<PaintingVariant> variant) {
        return variant.value().area();
    }

    private Painting(Level level, BlockPos pos) {
        super(EntityType.PAINTING, level, pos);
    }

    public Painting(Level level, BlockPos pos, Direction direction, Holder<PaintingVariant> variant) {
        this(level, pos);
        this.setVariant(variant);
        this.setDirection(direction);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        VARIANT_CODEC.encodeStart(this.registryAccess().createSerializationContext(NbtOps.INSTANCE), this.getVariant())
            .ifSuccess(tag -> compound.merge((CompoundTag)tag));
        compound.putByte("facing", (byte)this.direction.get2DDataValue());
        super.addAdditionalSaveData(compound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        VARIANT_CODEC.parse(this.registryAccess().createSerializationContext(NbtOps.INSTANCE), compound).ifSuccess(this::setVariant);
        this.direction = Direction.from2DDataValue(compound.getByte("facing"));
        super.readAdditionalSaveData(compound);
        this.setDirection(this.direction);
    }

    @Override
    protected AABB calculateBoundingBox(BlockPos pos, Direction direction) {
        // CraftBukkit start
        PaintingVariant variant = (PaintingVariant) this.getVariant().value();
        return Painting.calculateBoundingBoxStatic(pos, direction, variant.width(), variant.height());
    }

    public static AABB calculateBoundingBoxStatic(BlockPos pos, Direction direction, int width, int height) {
        // CraftBukkit end
        float f = 0.46875F;
        Vec3 vec3 = Vec3.atCenterOf(pos).relative(direction, -0.46875);
        // CraftBukkit start
        double d = Painting.offsetForPaintingSize(width);
        double d1 = Painting.offsetForPaintingSize(height);
        // CraftBukkit end
        Direction counterClockWise = direction.getCounterClockWise();
        Vec3 vec31 = vec3.relative(counterClockWise, d).relative(Direction.UP, d1);
        Direction.Axis axis = direction.getAxis();
        // CraftBukkit start
        double d2 = axis == Direction.Axis.X ? 0.0625 : width;
        double d3 = height;
        double d4 = axis == Direction.Axis.Z ? 0.0625 : width;
        // CraftBukkit end
        return AABB.ofSize(vec31, d2, d3, d4);
    }

    private static double offsetForPaintingSize(int size) { // CraftBukkit - static
        return size % 2 == 0 ? 0.5 : 0.0;
    }

    @Override
    public void dropItem(ServerLevel level, @Nullable Entity entity) {
        if (level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            this.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
            if (!(entity instanceof Player player && player.hasInfiniteMaterials())) {
                // Purpur start - Apply display names from item forms of entities to entities and vice versa
                final ItemStack painting = new ItemStack(Items.PAINTING);
                if (!this.level().purpurConfig.persistentDroppableEntityDisplayNames) painting.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, null);
                this.spawnAtLocation(level, painting);
                // Purpur end - Apply display names from item forms of entities to entities and vice versa
            }
        }
    }

    @Override
    public void playPlacementSound() {
        this.playSound(SoundEvents.PAINTING_PLACE, 1.0F, 1.0F);
    }

    @Override
    public void moveTo(double x, double y, double z, float yaw, float pitch) {
        this.setPos(x, y, z);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.setPos(x, y, z);
    }

    @Override
    public Vec3 trackingPosition() {
        return Vec3.atLowerCornerOf(this.pos);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, this.direction.get3DDataValue(), this.getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.setDirection(Direction.from3DDataValue(packet.getData()));
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.PAINTING);
    }
}
