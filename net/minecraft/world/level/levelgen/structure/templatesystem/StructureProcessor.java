package net.minecraft.world.level.levelgen.structure.templatesystem;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;

public abstract class StructureProcessor {
    @Nullable
    public StructureTemplate.StructureBlockInfo processBlock(
        LevelReader level,
        BlockPos offset,
        BlockPos pos,
        StructureTemplate.StructureBlockInfo blockInfo,
        StructureTemplate.StructureBlockInfo relativeBlockInfo,
        StructurePlaceSettings settings
    ) {
        return relativeBlockInfo;
    }

    protected abstract StructureProcessorType<?> getType();

    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
        ServerLevelAccessor serverLevel,
        BlockPos offset,
        BlockPos pos,
        List<StructureTemplate.StructureBlockInfo> originalBlockInfos,
        List<StructureTemplate.StructureBlockInfo> processedBlockInfos,
        StructurePlaceSettings settings
    ) {
        return processedBlockInfos;
    }
}
