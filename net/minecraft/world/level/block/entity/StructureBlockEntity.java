package net.minecraft.world.level.block.entity;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ResourceLocationException;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class StructureBlockEntity extends BlockEntity {
    private static final int SCAN_CORNER_BLOCKS_RANGE = 5;
    public static final int MAX_OFFSET_PER_AXIS = 48;
    public static final int MAX_SIZE_PER_AXIS = 48;
    public static final String AUTHOR_TAG = "author";
    @Nullable
    private ResourceLocation structureName;
    public String author = "";
    public String metaData = "";
    public BlockPos structurePos = new BlockPos(0, 1, 0);
    public Vec3i structureSize = Vec3i.ZERO;
    public Mirror mirror = Mirror.NONE;
    public Rotation rotation = Rotation.NONE;
    public StructureMode mode;
    public boolean ignoreEntities = true;
    private boolean powered;
    public boolean showAir;
    public boolean showBoundingBox = true;
    public float integrity = 1.0F;
    public long seed;

    public StructureBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.STRUCTURE_BLOCK, pos, blockState);
        this.mode = blockState.getValue(StructureBlock.MODE);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("name", this.getStructureName());
        tag.putString("author", this.author);
        tag.putString("metadata", this.metaData);
        tag.putInt("posX", this.structurePos.getX());
        tag.putInt("posY", this.structurePos.getY());
        tag.putInt("posZ", this.structurePos.getZ());
        tag.putInt("sizeX", this.structureSize.getX());
        tag.putInt("sizeY", this.structureSize.getY());
        tag.putInt("sizeZ", this.structureSize.getZ());
        tag.putString("rotation", this.rotation.toString());
        tag.putString("mirror", this.mirror.toString());
        tag.putString("mode", this.mode.toString());
        tag.putBoolean("ignoreEntities", this.ignoreEntities);
        tag.putBoolean("powered", this.powered);
        tag.putBoolean("showair", this.showAir);
        tag.putBoolean("showboundingbox", this.showBoundingBox);
        tag.putFloat("integrity", this.integrity);
        tag.putLong("seed", this.seed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.setStructureName(tag.getString("name"));
        this.author = tag.getString("author");
        this.metaData = tag.getString("metadata");
        int i = Mth.clamp(tag.getInt("posX"), -48, 48);
        int i1 = Mth.clamp(tag.getInt("posY"), -48, 48);
        int i2 = Mth.clamp(tag.getInt("posZ"), -48, 48);
        this.structurePos = new BlockPos(i, i1, i2);
        int i3 = Mth.clamp(tag.getInt("sizeX"), 0, 48);
        int i4 = Mth.clamp(tag.getInt("sizeY"), 0, 48);
        int i5 = Mth.clamp(tag.getInt("sizeZ"), 0, 48);
        this.structureSize = new Vec3i(i3, i4, i5);

        try {
            this.rotation = Rotation.valueOf(tag.getString("rotation"));
        } catch (IllegalArgumentException var12) {
            this.rotation = Rotation.NONE;
        }

        try {
            this.mirror = Mirror.valueOf(tag.getString("mirror"));
        } catch (IllegalArgumentException var11) {
            this.mirror = Mirror.NONE;
        }

        try {
            this.mode = StructureMode.valueOf(tag.getString("mode"));
        } catch (IllegalArgumentException var10) {
            this.mode = StructureMode.DATA;
        }

        this.ignoreEntities = tag.getBoolean("ignoreEntities");
        this.powered = tag.getBoolean("powered");
        this.showAir = tag.getBoolean("showair");
        this.showBoundingBox = tag.getBoolean("showboundingbox");
        if (tag.contains("integrity")) {
            this.integrity = tag.getFloat("integrity");
        } else {
            this.integrity = 1.0F;
        }

        this.seed = tag.getLong("seed");
        this.updateBlockState();
    }

    private void updateBlockState() {
        if (this.level != null) {
            BlockPos blockPos = this.getBlockPos();
            BlockState blockState = this.level.getBlockState(blockPos);
            if (blockState.is(Blocks.STRUCTURE_BLOCK)) {
                this.level.setBlock(blockPos, blockState.setValue(StructureBlock.MODE, this.mode), 2);
            }
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

    public boolean usedBy(Player player) {
        if (!player.canUseGameMasterBlocks()) {
            return false;
        } else {
            if (player.getCommandSenderWorld().isClientSide) {
                player.openStructureBlock(this);
            }

            return true;
        }
    }

    public String getStructureName() {
        return this.structureName == null ? "" : this.structureName.toString();
    }

    public boolean hasStructureName() {
        return this.structureName != null;
    }

    public void setStructureName(@Nullable String structureName) {
        this.setStructureName(StringUtil.isNullOrEmpty(structureName) ? null : ResourceLocation.tryParse(structureName));
    }

    public void setStructureName(@Nullable ResourceLocation structureName) {
        this.structureName = structureName;
    }

    public void createdBy(LivingEntity author) {
        this.author = author.getName().getString();
    }

    public BlockPos getStructurePos() {
        return this.structurePos;
    }

    public void setStructurePos(BlockPos structurePos) {
        this.structurePos = structurePos;
    }

    public Vec3i getStructureSize() {
        return this.structureSize;
    }

    public void setStructureSize(Vec3i structureSize) {
        this.structureSize = structureSize;
    }

    public Mirror getMirror() {
        return this.mirror;
    }

    public void setMirror(Mirror mirror) {
        this.mirror = mirror;
    }

    public Rotation getRotation() {
        return this.rotation;
    }

    public void setRotation(Rotation rotation) {
        this.rotation = rotation;
    }

    public String getMetaData() {
        return this.metaData;
    }

    public void setMetaData(String metaData) {
        this.metaData = metaData;
    }

    public StructureMode getMode() {
        return this.mode;
    }

    public void setMode(StructureMode mode) {
        this.mode = mode;
        BlockState blockState = this.level.getBlockState(this.getBlockPos());
        if (blockState.is(Blocks.STRUCTURE_BLOCK)) {
            this.level.setBlock(this.getBlockPos(), blockState.setValue(StructureBlock.MODE, mode), 2);
        }
    }

    public boolean isIgnoreEntities() {
        return this.ignoreEntities;
    }

    public void setIgnoreEntities(boolean ignoreEntities) {
        this.ignoreEntities = ignoreEntities;
    }

    public float getIntegrity() {
        return this.integrity;
    }

    public void setIntegrity(float integrity) {
        this.integrity = integrity;
    }

    public long getSeed() {
        return this.seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public boolean detectSize() {
        if (this.mode != StructureMode.SAVE) {
            return false;
        } else {
            BlockPos blockPos = this.getBlockPos();
            int i = 80;
            BlockPos blockPos1 = new BlockPos(blockPos.getX() - 80, this.level.getMinY(), blockPos.getZ() - 80);
            BlockPos blockPos2 = new BlockPos(blockPos.getX() + 80, this.level.getMaxY(), blockPos.getZ() + 80);
            Stream<BlockPos> relatedCorners = this.getRelatedCorners(blockPos1, blockPos2);
            return calculateEnclosingBoundingBox(blockPos, relatedCorners)
                .filter(
                    boundingBox -> {
                        int i1 = boundingBox.maxX() - boundingBox.minX();
                        int i2 = boundingBox.maxY() - boundingBox.minY();
                        int i3 = boundingBox.maxZ() - boundingBox.minZ();
                        if (i1 > 1 && i2 > 1 && i3 > 1) {
                            this.structurePos = new BlockPos(
                                boundingBox.minX() - blockPos.getX() + 1, boundingBox.minY() - blockPos.getY() + 1, boundingBox.minZ() - blockPos.getZ() + 1
                            );
                            this.structureSize = new Vec3i(i1 - 1, i2 - 1, i3 - 1);
                            this.setChanged();
                            BlockState blockState = this.level.getBlockState(blockPos);
                            this.level.sendBlockUpdated(blockPos, blockState, blockState, 3);
                            return true;
                        } else {
                            return false;
                        }
                    }
                )
                .isPresent();
        }
    }

    private Stream<BlockPos> getRelatedCorners(BlockPos minPos, BlockPos maxPos) {
        return BlockPos.betweenClosedStream(minPos, maxPos)
            .filter(pos -> this.level.getBlockState(pos).is(Blocks.STRUCTURE_BLOCK))
            .map(this.level::getBlockEntity)
            .filter(blockEntity -> blockEntity instanceof StructureBlockEntity)
            .map(blockEntity -> (StructureBlockEntity)blockEntity)
            .filter(blockEntity -> blockEntity.mode == StructureMode.CORNER && Objects.equals(this.structureName, blockEntity.structureName))
            .map(BlockEntity::getBlockPos);
    }

    private static Optional<BoundingBox> calculateEnclosingBoundingBox(BlockPos pos, Stream<BlockPos> relatedCorners) {
        Iterator<BlockPos> iterator = relatedCorners.iterator();
        if (!iterator.hasNext()) {
            return Optional.empty();
        } else {
            BlockPos blockPos = iterator.next();
            BoundingBox boundingBox = new BoundingBox(blockPos);
            if (iterator.hasNext()) {
                iterator.forEachRemaining(boundingBox::encapsulate);
            } else {
                boundingBox.encapsulate(pos);
            }

            return Optional.of(boundingBox);
        }
    }

    public boolean saveStructure() {
        return this.mode == StructureMode.SAVE && this.saveStructure(true);
    }

    public boolean saveStructure(boolean writeToDisk) {
        if (this.structureName == null) {
            return false;
        } else {
            BlockPos blockPos = this.getBlockPos().offset(this.structurePos);
            ServerLevel serverLevel = (ServerLevel)this.level;
            StructureTemplateManager structureManager = serverLevel.getStructureManager();

            StructureTemplate structureTemplate;
            try {
                structureTemplate = structureManager.getOrCreate(this.structureName);
            } catch (ResourceLocationException var8) {
                return false;
            }

            structureTemplate.fillFromWorld(this.level, blockPos, this.structureSize, !this.ignoreEntities, Blocks.STRUCTURE_VOID);
            structureTemplate.setAuthor(this.author);
            if (writeToDisk) {
                try {
                    return structureManager.save(this.structureName);
                } catch (ResourceLocationException var7) {
                    return false;
                }
            } else {
                return true;
            }
        }
    }

    public static RandomSource createRandom(long seed) {
        return seed == 0L ? RandomSource.create(Util.getMillis()) : RandomSource.create(seed);
    }

    public boolean placeStructureIfSameSize(ServerLevel level) {
        if (this.mode == StructureMode.LOAD && this.structureName != null) {
            StructureTemplate structureTemplate = level.getStructureManager().get(this.structureName).orElse(null);
            if (structureTemplate == null) {
                return false;
            } else if (structureTemplate.getSize().equals(this.structureSize)) {
                this.placeStructure(level, structureTemplate);
                return true;
            } else {
                this.loadStructureInfo(structureTemplate);
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean loadStructureInfo(ServerLevel level) {
        StructureTemplate structureTemplate = this.getStructureTemplate(level);
        if (structureTemplate == null) {
            return false;
        } else {
            this.loadStructureInfo(structureTemplate);
            return true;
        }
    }

    private void loadStructureInfo(StructureTemplate structureTemplate) {
        this.author = !StringUtil.isNullOrEmpty(structureTemplate.getAuthor()) ? structureTemplate.getAuthor() : "";
        this.structureSize = structureTemplate.getSize();
        this.setChanged();
    }

    public void placeStructure(ServerLevel level) {
        StructureTemplate structureTemplate = this.getStructureTemplate(level);
        if (structureTemplate != null) {
            this.placeStructure(level, structureTemplate);
        }
    }

    @Nullable
    private StructureTemplate getStructureTemplate(ServerLevel level) {
        return this.structureName == null ? null : level.getStructureManager().get(this.structureName).orElse(null);
    }

    private void placeStructure(ServerLevel level, StructureTemplate structureTemplate) {
        this.loadStructureInfo(structureTemplate);
        StructurePlaceSettings structurePlaceSettings = new StructurePlaceSettings()
            .setMirror(this.mirror)
            .setRotation(this.rotation)
            .setIgnoreEntities(this.ignoreEntities);
        if (this.integrity < 1.0F) {
            structurePlaceSettings.clearProcessors()
                .addProcessor(new BlockRotProcessor(Mth.clamp(this.integrity, 0.0F, 1.0F)))
                .setRandom(createRandom(this.seed));
        }

        BlockPos blockPos = this.getBlockPos().offset(this.structurePos);
        structureTemplate.placeInWorld(level, blockPos, blockPos, structurePlaceSettings, createRandom(this.seed), 2);
    }

    public void unloadStructure() {
        if (this.structureName != null) {
            ServerLevel serverLevel = (ServerLevel)this.level;
            StructureTemplateManager structureManager = serverLevel.getStructureManager();
            structureManager.remove(this.structureName);
        }
    }

    public boolean isStructureLoadable() {
        if (this.mode == StructureMode.LOAD && !this.level.isClientSide && this.structureName != null) {
            ServerLevel serverLevel = (ServerLevel)this.level;
            StructureTemplateManager structureManager = serverLevel.getStructureManager();

            try {
                return structureManager.get(this.structureName).isPresent();
            } catch (ResourceLocationException var4) {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean isPowered() {
        return this.powered;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    public boolean getShowAir() {
        return this.showAir;
    }

    public void setShowAir(boolean showAir) {
        this.showAir = showAir;
    }

    public boolean getShowBoundingBox() {
        return this.showBoundingBox;
    }

    public void setShowBoundingBox(boolean showBoundingBox) {
        this.showBoundingBox = showBoundingBox;
    }

    public static enum UpdateType {
        UPDATE_DATA,
        SAVE_AREA,
        LOAD_AREA,
        SCAN_AREA;
    }
}
