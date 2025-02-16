package net.minecraft.world.level.levelgen.structure;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.util.function.Function;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;

public abstract class TemplateStructurePiece extends StructurePiece {
    private static final Logger LOGGER = LogUtils.getLogger();
    protected final String templateName;
    protected StructureTemplate template;
    protected StructurePlaceSettings placeSettings;
    protected BlockPos templatePosition;

    public TemplateStructurePiece(
        StructurePieceType type,
        int genDepth,
        StructureTemplateManager structureTemplateManager,
        ResourceLocation location,
        String templateName,
        StructurePlaceSettings placeSettings,
        BlockPos templatePosition
    ) {
        super(type, genDepth, structureTemplateManager.getOrCreate(location).getBoundingBox(placeSettings, templatePosition));
        this.setOrientation(Direction.NORTH);
        this.templateName = templateName;
        this.templatePosition = templatePosition;
        this.template = structureTemplateManager.getOrCreate(location);
        this.placeSettings = placeSettings;
    }

    public TemplateStructurePiece(
        StructurePieceType type,
        CompoundTag tag,
        StructureTemplateManager structureTemplateManager,
        Function<ResourceLocation, StructurePlaceSettings> placeSettingsFactory
    ) {
        super(type, tag);
        this.setOrientation(Direction.NORTH);
        this.templateName = tag.getString("Template");
        this.templatePosition = new BlockPos(tag.getInt("TPX"), tag.getInt("TPY"), tag.getInt("TPZ"));
        ResourceLocation resourceLocation = this.makeTemplateLocation();
        this.template = structureTemplateManager.getOrCreate(resourceLocation);
        this.placeSettings = placeSettingsFactory.apply(resourceLocation);
        this.boundingBox = this.template.getBoundingBox(this.placeSettings, this.templatePosition);
    }

    protected ResourceLocation makeTemplateLocation() {
        return ResourceLocation.parse(this.templateName);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("TPX", this.templatePosition.getX());
        tag.putInt("TPY", this.templatePosition.getY());
        tag.putInt("TPZ", this.templatePosition.getZ());
        tag.putString("Template", this.templateName);
    }

    @Override
    public void postProcess(
        WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos
    ) {
        this.placeSettings.setBoundingBox(box);
        this.boundingBox = this.template.getBoundingBox(this.placeSettings, this.templatePosition);
        if (this.template.placeInWorld(level, this.templatePosition, pos, this.placeSettings, random, 2)) {
            for (StructureTemplate.StructureBlockInfo structureBlockInfo : this.template
                .filterBlocks(this.templatePosition, this.placeSettings, Blocks.STRUCTURE_BLOCK)) {
                if (structureBlockInfo.nbt() != null) {
                    StructureMode structureMode = StructureMode.valueOf(structureBlockInfo.nbt().getString("mode"));
                    if (structureMode == StructureMode.DATA) {
                        this.handleDataMarker(structureBlockInfo.nbt().getString("metadata"), structureBlockInfo.pos(), level, random, box);
                    }
                }
            }

            for (StructureTemplate.StructureBlockInfo structureBlockInfo1 : this.template
                .filterBlocks(this.templatePosition, this.placeSettings, Blocks.JIGSAW)) {
                if (structureBlockInfo1.nbt() != null) {
                    String string = structureBlockInfo1.nbt().getString("final_state");
                    BlockState blockState = Blocks.AIR.defaultBlockState();

                    try {
                        blockState = BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK), string, true).blockState();
                    } catch (CommandSyntaxException var15) {
                        LOGGER.error("Error while parsing blockstate {} in jigsaw block @ {}", string, structureBlockInfo1.pos());
                    }

                    level.setBlock(structureBlockInfo1.pos(), blockState, 3);
                }
            }
        }
    }

    protected abstract void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box);

    @Deprecated
    @Override
    public void move(int x, int y, int z) {
        super.move(x, y, z);
        this.templatePosition = this.templatePosition.offset(x, y, z);
    }

    @Override
    public Rotation getRotation() {
        return this.placeSettings.getRotation();
    }

    public StructureTemplate template() {
        return this.template;
    }

    public BlockPos templatePosition() {
        return this.templatePosition;
    }

    public StructurePlaceSettings placeSettings() {
        return this.placeSettings;
    }
}
