package net.minecraft.world.level.saveddata.maps;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public class MapItemSavedData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAP_SIZE = 128;
    private static final int HALF_MAP_SIZE = 64;
    public static final int MAX_SCALE = 4;
    public static final int TRACKED_DECORATION_LIMIT = 256;
    private static final String FRAME_PREFIX = "frame-";
    public final int centerX;
    public final int centerZ;
    public final ResourceKey<Level> dimension;
    private final boolean trackingPosition;
    private final boolean unlimitedTracking;
    public final byte scale;
    public byte[] colors = new byte[16384];
    public final boolean locked;
    private final List<MapItemSavedData.HoldingPlayer> carriedBy = Lists.newArrayList();
    private final Map<Player, MapItemSavedData.HoldingPlayer> carriedByPlayers = Maps.newHashMap();
    private final Map<String, MapBanner> bannerMarkers = Maps.newHashMap();
    final Map<String, MapDecoration> decorations = Maps.newLinkedHashMap();
    private final Map<String, MapFrame> frameMarkers = Maps.newHashMap();
    private int trackedDecorationCount;

    public static SavedData.Factory<MapItemSavedData> factory() {
        return new SavedData.Factory<>(() -> {
            throw new IllegalStateException("Should never create an empty map saved data");
        }, MapItemSavedData::load, DataFixTypes.SAVED_DATA_MAP_DATA);
    }

    private MapItemSavedData(int x, int z, byte scale, boolean trackingPosition, boolean unlimitedTracking, boolean locked, ResourceKey<Level> dimension) {
        this.scale = scale;
        this.centerX = x;
        this.centerZ = z;
        this.dimension = dimension;
        this.trackingPosition = trackingPosition;
        this.unlimitedTracking = unlimitedTracking;
        this.locked = locked;
    }

    public static MapItemSavedData createFresh(
        double x, double z, byte scale, boolean trackingPosition, boolean unlimitedTracking, ResourceKey<Level> dimension
    ) {
        int i = 128 * (1 << scale);
        int floor = Mth.floor((x + 64.0) / i);
        int floor1 = Mth.floor((z + 64.0) / i);
        int i1 = floor * i + i / 2 - 64;
        int i2 = floor1 * i + i / 2 - 64;
        return new MapItemSavedData(i1, i2, scale, trackingPosition, unlimitedTracking, false, dimension);
    }

    public static MapItemSavedData createForClient(byte scale, boolean locked, ResourceKey<Level> dimension) {
        return new MapItemSavedData(0, 0, scale, false, false, locked, dimension);
    }

    public static MapItemSavedData load(CompoundTag tag, HolderLookup.Provider levelRegistry) {
        ResourceKey<Level> resourceKey = DimensionType.parseLegacy(new Dynamic<>(NbtOps.INSTANCE, tag.get("dimension")))
            .resultOrPartial(LOGGER::error)
            .orElseThrow(() -> new IllegalArgumentException("Invalid map dimension: " + tag.get("dimension")));
        int _int = tag.getInt("xCenter");
        int _int1 = tag.getInt("zCenter");
        byte b = (byte)Mth.clamp(tag.getByte("scale"), 0, 4);
        boolean flag = !tag.contains("trackingPosition", 1) || tag.getBoolean("trackingPosition");
        boolean _boolean = tag.getBoolean("unlimitedTracking");
        boolean _boolean1 = tag.getBoolean("locked");
        MapItemSavedData mapItemSavedData = new MapItemSavedData(_int, _int1, b, flag, _boolean, _boolean1, resourceKey);
        byte[] byteArray = tag.getByteArray("colors");
        if (byteArray.length == 16384) {
            mapItemSavedData.colors = byteArray;
        }

        RegistryOps<Tag> registryOps = levelRegistry.createSerializationContext(NbtOps.INSTANCE);

        for (MapBanner mapBanner : MapBanner.LIST_CODEC
            .parse(registryOps, tag.get("banners"))
            .resultOrPartial(string -> LOGGER.warn("Failed to parse map banner: '{}'", string))
            .orElse(List.of())) {
            mapItemSavedData.bannerMarkers.put(mapBanner.getId(), mapBanner);
            mapItemSavedData.addDecoration(
                mapBanner.getDecoration(), null, mapBanner.getId(), mapBanner.pos().getX(), mapBanner.pos().getZ(), 180.0, mapBanner.name().orElse(null)
            );
        }

        ListTag list1 = tag.getList("frames", 10);

        for (int i = 0; i < list1.size(); i++) {
            MapFrame mapFrame = MapFrame.load(list1.getCompound(i));
            if (mapFrame != null) {
                mapItemSavedData.frameMarkers.put(mapFrame.getId(), mapFrame);
                mapItemSavedData.addDecoration(
                    MapDecorationTypes.FRAME,
                    null,
                    getFrameKey(mapFrame.getEntityId()),
                    mapFrame.getPos().getX(),
                    mapFrame.getPos().getZ(),
                    mapFrame.getRotation(),
                    null
                );
            }
        }

        return mapItemSavedData;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ResourceLocation.CODEC
            .encodeStart(NbtOps.INSTANCE, this.dimension.location())
            .resultOrPartial(LOGGER::error)
            .ifPresent(dimension -> tag.put("dimension", dimension));
        tag.putInt("xCenter", this.centerX);
        tag.putInt("zCenter", this.centerZ);
        tag.putByte("scale", this.scale);
        tag.putByteArray("colors", this.colors);
        tag.putBoolean("trackingPosition", this.trackingPosition);
        tag.putBoolean("unlimitedTracking", this.unlimitedTracking);
        tag.putBoolean("locked", this.locked);
        RegistryOps<Tag> registryOps = registries.createSerializationContext(NbtOps.INSTANCE);
        tag.put("banners", MapBanner.LIST_CODEC.encodeStart(registryOps, List.copyOf(this.bannerMarkers.values())).getOrThrow());
        ListTag listTag = new ListTag();

        for (MapFrame mapFrame : this.frameMarkers.values()) {
            listTag.add(mapFrame.save());
        }

        tag.put("frames", listTag);
        return tag;
    }

    public MapItemSavedData locked() {
        MapItemSavedData mapItemSavedData = new MapItemSavedData(
            this.centerX, this.centerZ, this.scale, this.trackingPosition, this.unlimitedTracking, true, this.dimension
        );
        mapItemSavedData.bannerMarkers.putAll(this.bannerMarkers);
        mapItemSavedData.decorations.putAll(this.decorations);
        mapItemSavedData.trackedDecorationCount = this.trackedDecorationCount;
        System.arraycopy(this.colors, 0, mapItemSavedData.colors, 0, this.colors.length);
        return mapItemSavedData;
    }

    public MapItemSavedData scaled() {
        return createFresh(this.centerX, this.centerZ, (byte)Mth.clamp(this.scale + 1, 0, 4), this.trackingPosition, this.unlimitedTracking, this.dimension);
    }

    private static Predicate<ItemStack> mapMatcher(ItemStack stack) {
        MapId mapId = stack.get(DataComponents.MAP_ID);
        return itemStack -> itemStack == stack || itemStack.is(stack.getItem()) && Objects.equals(mapId, itemStack.get(DataComponents.MAP_ID));
    }

    public void tickCarriedBy(Player player, ItemStack mapStack) {
        if (!this.carriedByPlayers.containsKey(player)) {
            MapItemSavedData.HoldingPlayer holdingPlayer = new MapItemSavedData.HoldingPlayer(player);
            this.carriedByPlayers.put(player, holdingPlayer);
            this.carriedBy.add(holdingPlayer);
        }

        Predicate<ItemStack> predicate = mapMatcher(mapStack);
        if (!player.getInventory().contains(predicate)) {
            this.removeDecoration(player.getName().getString());
        }

        for (int i = 0; i < this.carriedBy.size(); i++) {
            MapItemSavedData.HoldingPlayer holdingPlayer1 = this.carriedBy.get(i);
            Player player1 = holdingPlayer1.player;
            String string = player1.getName().getString();
            if (!player1.isRemoved() && (player1.getInventory().contains(predicate) || mapStack.isFramed())) {
                if (!mapStack.isFramed() && player1.level().dimension() == this.dimension && this.trackingPosition) {
                    this.addDecoration(MapDecorationTypes.PLAYER, player1.level(), string, player1.getX(), player1.getZ(), player1.getYRot(), null);
                }
            } else {
                this.carriedByPlayers.remove(player1);
                this.carriedBy.remove(holdingPlayer1);
                this.removeDecoration(string);
            }

            if (!player1.equals(player) && hasMapInvisibilityItemEquipped(player1)) {
                this.removeDecoration(string);
            }
        }

        if (mapStack.isFramed() && this.trackingPosition) {
            ItemFrame frame = mapStack.getFrame();
            BlockPos pos = frame.getPos();
            MapFrame mapFrame = this.frameMarkers.get(MapFrame.frameId(pos));
            if (mapFrame != null && frame.getId() != mapFrame.getEntityId() && this.frameMarkers.containsKey(mapFrame.getId())) {
                this.removeDecoration(getFrameKey(mapFrame.getEntityId()));
            }

            MapFrame mapFrame1 = new MapFrame(pos, frame.getDirection().get2DDataValue() * 90, frame.getId());
            this.addDecoration(
                MapDecorationTypes.FRAME, player.level(), getFrameKey(frame.getId()), pos.getX(), pos.getZ(), frame.getDirection().get2DDataValue() * 90, null
            );
            this.frameMarkers.put(mapFrame1.getId(), mapFrame1);
        }

        MapDecorations mapDecorations = mapStack.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY);
        if (!this.decorations.keySet().containsAll(mapDecorations.decorations().keySet())) {
            mapDecorations.decorations().forEach((string1, entry) -> {
                if (!this.decorations.containsKey(string1)) {
                    this.addDecoration(entry.type(), player.level(), string1, entry.x(), entry.z(), entry.rotation(), null);
                }
            });
        }
    }

    private static boolean hasMapInvisibilityItemEquipped(Player player) {
        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            if (equipmentSlot != EquipmentSlot.MAINHAND
                && equipmentSlot != EquipmentSlot.OFFHAND
                && player.getItemBySlot(equipmentSlot).is(ItemTags.MAP_INVISIBILITY_EQUIPMENT)) {
                return true;
            }
        }

        return false;
    }

    private void removeDecoration(String identifier) {
        MapDecoration mapDecoration = this.decorations.remove(identifier);
        if (mapDecoration != null && mapDecoration.type().value().trackCount()) {
            this.trackedDecorationCount--;
        }

        this.setDecorationsDirty();
    }

    public static void addTargetDecoration(ItemStack stack, BlockPos pos, String type, Holder<MapDecorationType> mapDecorationType) {
        MapDecorations.Entry entry = new MapDecorations.Entry(mapDecorationType, pos.getX(), pos.getZ(), 180.0F);
        stack.update(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY, decorations -> decorations.withDecoration(type, entry));
        if (mapDecorationType.value().hasMapColor()) {
            stack.set(DataComponents.MAP_COLOR, new MapItemColor(mapDecorationType.value().mapColor()));
        }
    }

    private void addDecoration(
        Holder<MapDecorationType> decorationType, @Nullable LevelAccessor level, String id, double x, double z, double yRot, @Nullable Component displayName
    ) {
        int i = 1 << this.scale;
        float f = (float)(x - this.centerX) / i;
        float f1 = (float)(z - this.centerZ) / i;
        MapItemSavedData.MapDecorationLocation mapDecorationLocation = this.calculateDecorationLocationAndType(decorationType, level, yRot, f, f1);
        if (mapDecorationLocation == null) {
            this.removeDecoration(id);
        } else {
            MapDecoration mapDecoration = new MapDecoration(
                mapDecorationLocation.type(),
                mapDecorationLocation.x(),
                mapDecorationLocation.y(),
                mapDecorationLocation.rot(),
                Optional.ofNullable(displayName)
            );
            MapDecoration mapDecoration1 = this.decorations.put(id, mapDecoration);
            if (!mapDecoration.equals(mapDecoration1)) {
                if (mapDecoration1 != null && mapDecoration1.type().value().trackCount()) {
                    this.trackedDecorationCount--;
                }

                if (mapDecorationLocation.type().value().trackCount()) {
                    this.trackedDecorationCount++;
                }

                this.setDecorationsDirty();
            }
        }
    }

    @Nullable
    private MapItemSavedData.MapDecorationLocation calculateDecorationLocationAndType(
        Holder<MapDecorationType> decorationType, @Nullable LevelAccessor level, double yRot, float x, float z
    ) {
        byte b = clampMapCoordinate(x);
        byte b1 = clampMapCoordinate(z);
        if (decorationType.is(MapDecorationTypes.PLAYER)) {
            Pair<Holder<MapDecorationType>, Byte> pair = this.playerDecorationTypeAndRotation(decorationType, level, yRot, x, z);
            return pair == null ? null : new MapItemSavedData.MapDecorationLocation(pair.getFirst(), b, b1, pair.getSecond());
        } else {
            return !isInsideMap(x, z) && !this.unlimitedTracking
                ? null
                : new MapItemSavedData.MapDecorationLocation(decorationType, b, b1, this.calculateRotation(level, yRot));
        }
    }

    @Nullable
    private Pair<Holder<MapDecorationType>, Byte> playerDecorationTypeAndRotation(
        Holder<MapDecorationType> decorationType, @Nullable LevelAccessor level, double yRot, float x, float z
    ) {
        if (isInsideMap(x, z)) {
            return Pair.of(decorationType, this.calculateRotation(level, yRot));
        } else {
            Holder<MapDecorationType> holder = this.decorationTypeForPlayerOutsideMap(x, z);
            return holder == null ? null : Pair.of(holder, (byte)0);
        }
    }

    private byte calculateRotation(@Nullable LevelAccessor level, double yRot) {
        if (this.dimension == Level.NETHER && level != null) {
            int i = (int)(level.getLevelData().getDayTime() / 10L);
            return (byte)(i * i * 34187121 + i * 121 >> 15 & 15);
        } else {
            double d = yRot < 0.0 ? yRot - 8.0 : yRot + 8.0;
            return (byte)(d * 16.0 / 360.0);
        }
    }

    private static boolean isInsideMap(float x, float z) {
        int i = 63;
        return x >= -63.0F && z >= -63.0F && x <= 63.0F && z <= 63.0F;
    }

    @Nullable
    private Holder<MapDecorationType> decorationTypeForPlayerOutsideMap(float x, float z) {
        int i = 320;
        boolean flag = Math.abs(x) < 320.0F && Math.abs(z) < 320.0F;
        if (flag) {
            return MapDecorationTypes.PLAYER_OFF_MAP;
        } else {
            return this.unlimitedTracking ? MapDecorationTypes.PLAYER_OFF_LIMITS : null;
        }
    }

    private static byte clampMapCoordinate(float coord) {
        int i = 63;
        if (coord <= -63.0F) {
            return -128;
        } else {
            return coord >= 63.0F ? 127 : (byte)(coord * 2.0F + 0.5);
        }
    }

    @Nullable
    public Packet<?> getUpdatePacket(MapId mapId, Player player) {
        MapItemSavedData.HoldingPlayer holdingPlayer = this.carriedByPlayers.get(player);
        return holdingPlayer == null ? null : holdingPlayer.nextUpdatePacket(mapId);
    }

    private void setColorsDirty(int x, int z) {
        this.setDirty();

        for (MapItemSavedData.HoldingPlayer holdingPlayer : this.carriedBy) {
            holdingPlayer.markColorsDirty(x, z);
        }
    }

    private void setDecorationsDirty() {
        this.setDirty();
        this.carriedBy.forEach(MapItemSavedData.HoldingPlayer::markDecorationsDirty);
    }

    public MapItemSavedData.HoldingPlayer getHoldingPlayer(Player player) {
        MapItemSavedData.HoldingPlayer holdingPlayer = this.carriedByPlayers.get(player);
        if (holdingPlayer == null) {
            holdingPlayer = new MapItemSavedData.HoldingPlayer(player);
            this.carriedByPlayers.put(player, holdingPlayer);
            this.carriedBy.add(holdingPlayer);
        }

        return holdingPlayer;
    }

    public boolean toggleBanner(LevelAccessor accessor, BlockPos pos) {
        double d = pos.getX() + 0.5;
        double d1 = pos.getZ() + 0.5;
        int i = 1 << this.scale;
        double d2 = (d - this.centerX) / i;
        double d3 = (d1 - this.centerZ) / i;
        int i1 = 63;
        if (d2 >= -63.0 && d3 >= -63.0 && d2 <= 63.0 && d3 <= 63.0) {
            MapBanner mapBanner = MapBanner.fromWorld(accessor, pos);
            if (mapBanner == null) {
                return false;
            }

            if (this.bannerMarkers.remove(mapBanner.getId(), mapBanner)) {
                this.removeDecoration(mapBanner.getId());
                return true;
            }

            if (!this.isTrackedCountOverLimit(256)) {
                this.bannerMarkers.put(mapBanner.getId(), mapBanner);
                this.addDecoration(mapBanner.getDecoration(), accessor, mapBanner.getId(), d, d1, 180.0, mapBanner.name().orElse(null));
                return true;
            }
        }

        return false;
    }

    public void checkBanners(BlockGetter reader, int x, int z) {
        Iterator<MapBanner> iterator = this.bannerMarkers.values().iterator();

        while (iterator.hasNext()) {
            MapBanner mapBanner = iterator.next();
            if (mapBanner.pos().getX() == x && mapBanner.pos().getZ() == z) {
                MapBanner mapBanner1 = MapBanner.fromWorld(reader, mapBanner.pos());
                if (!mapBanner.equals(mapBanner1)) {
                    iterator.remove();
                    this.removeDecoration(mapBanner.getId());
                }
            }
        }
    }

    public Collection<MapBanner> getBanners() {
        return this.bannerMarkers.values();
    }

    public void removedFromFrame(BlockPos pos, int entityId) {
        this.removeDecoration(getFrameKey(entityId));
        this.frameMarkers.remove(MapFrame.frameId(pos));
        this.setDirty();
    }

    public boolean updateColor(int x, int z, byte color) {
        byte b = this.colors[x + z * 128];
        if (b != color) {
            this.setColor(x, z, color);
            return true;
        } else {
            return false;
        }
    }

    public void setColor(int x, int z, byte color) {
        this.colors[x + z * 128] = color;
        this.setColorsDirty(x, z);
    }

    public boolean isExplorationMap() {
        for (MapDecoration mapDecoration : this.decorations.values()) {
            if (mapDecoration.type().value().explorationMapElement()) {
                return true;
            }
        }

        return false;
    }

    public void addClientSideDecorations(List<MapDecoration> decorations) {
        this.decorations.clear();
        this.trackedDecorationCount = 0;

        for (int i = 0; i < decorations.size(); i++) {
            MapDecoration mapDecoration = decorations.get(i);
            this.decorations.put("icon-" + i, mapDecoration);
            if (mapDecoration.type().value().trackCount()) {
                this.trackedDecorationCount++;
            }
        }
    }

    public Iterable<MapDecoration> getDecorations() {
        return this.decorations.values();
    }

    public boolean isTrackedCountOverLimit(int trackedCount) {
        return this.trackedDecorationCount >= trackedCount;
    }

    private static String getFrameKey(int entityId) {
        return "frame-" + entityId;
    }

    public class HoldingPlayer {
        public final Player player;
        private boolean dirtyData = true;
        private int minDirtyX;
        private int minDirtyY;
        private int maxDirtyX = 127;
        private int maxDirtyY = 127;
        private boolean dirtyDecorations = true;
        private int tick;
        public int step;

        HoldingPlayer(final Player player) {
            this.player = player;
        }

        private MapItemSavedData.MapPatch createPatch() {
            int i = this.minDirtyX;
            int i1 = this.minDirtyY;
            int i2 = this.maxDirtyX + 1 - this.minDirtyX;
            int i3 = this.maxDirtyY + 1 - this.minDirtyY;
            byte[] bytes = new byte[i2 * i3];

            for (int i4 = 0; i4 < i2; i4++) {
                for (int i5 = 0; i5 < i3; i5++) {
                    bytes[i4 + i5 * i2] = MapItemSavedData.this.colors[i + i4 + (i1 + i5) * 128];
                }
            }

            return new MapItemSavedData.MapPatch(i, i1, i2, i3, bytes);
        }

        @Nullable
        Packet<?> nextUpdatePacket(MapId mapId) {
            MapItemSavedData.MapPatch mapPatch;
            if (this.dirtyData) {
                this.dirtyData = false;
                mapPatch = this.createPatch();
            } else {
                mapPatch = null;
            }

            Collection<MapDecoration> collection;
            if (this.dirtyDecorations && this.tick++ % 5 == 0) {
                this.dirtyDecorations = false;
                collection = MapItemSavedData.this.decorations.values();
            } else {
                collection = null;
            }

            return collection == null && mapPatch == null
                ? null
                : new ClientboundMapItemDataPacket(mapId, MapItemSavedData.this.scale, MapItemSavedData.this.locked, collection, mapPatch);
        }

        void markColorsDirty(int x, int z) {
            if (this.dirtyData) {
                this.minDirtyX = Math.min(this.minDirtyX, x);
                this.minDirtyY = Math.min(this.minDirtyY, z);
                this.maxDirtyX = Math.max(this.maxDirtyX, x);
                this.maxDirtyY = Math.max(this.maxDirtyY, z);
            } else {
                this.dirtyData = true;
                this.minDirtyX = x;
                this.minDirtyY = z;
                this.maxDirtyX = x;
                this.maxDirtyY = z;
            }
        }

        private void markDecorationsDirty() {
            this.dirtyDecorations = true;
        }
    }

    record MapDecorationLocation(Holder<MapDecorationType> type, byte x, byte y, byte rot) {
    }

    public record MapPatch(int startX, int startY, int width, int height, byte[] mapColors) {
        public static final StreamCodec<ByteBuf, Optional<MapItemSavedData.MapPatch>> STREAM_CODEC = StreamCodec.of(
            MapItemSavedData.MapPatch::write, MapItemSavedData.MapPatch::read
        );

        private static void write(ByteBuf buffer, Optional<MapItemSavedData.MapPatch> mapPatch) {
            if (mapPatch.isPresent()) {
                MapItemSavedData.MapPatch mapPatch1 = mapPatch.get();
                buffer.writeByte(mapPatch1.width);
                buffer.writeByte(mapPatch1.height);
                buffer.writeByte(mapPatch1.startX);
                buffer.writeByte(mapPatch1.startY);
                FriendlyByteBuf.writeByteArray(buffer, mapPatch1.mapColors);
            } else {
                buffer.writeByte(0);
            }
        }

        private static Optional<MapItemSavedData.MapPatch> read(ByteBuf buffer) {
            int unsignedByte = buffer.readUnsignedByte();
            if (unsignedByte > 0) {
                int unsignedByte1 = buffer.readUnsignedByte();
                int unsignedByte2 = buffer.readUnsignedByte();
                int unsignedByte3 = buffer.readUnsignedByte();
                byte[] byteArray = FriendlyByteBuf.readByteArray(buffer);
                return Optional.of(new MapItemSavedData.MapPatch(unsignedByte2, unsignedByte3, unsignedByte, unsignedByte1, byteArray));
            } else {
                return Optional.empty();
            }
        }

        public void applyToMap(MapItemSavedData savedData) {
            for (int i = 0; i < this.width; i++) {
                for (int i1 = 0; i1 < this.height; i1++) {
                    savedData.setColor(this.startX + i, this.startY + i1, this.mapColors[i + i1 * this.width]);
                }
            }
        }
    }
}
