package net.minecraft.network.chat.contents;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public record BlockDataSource(String posPattern, @Nullable Coordinates compiledPos) implements DataSource {
    public static final MapCodec<BlockDataSource> SUB_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(Codec.STRING.fieldOf("block").forGetter(BlockDataSource::posPattern)).apply(instance, BlockDataSource::new)
    );
    public static final DataSource.Type<BlockDataSource> TYPE = new DataSource.Type<>(SUB_CODEC, "block");

    public BlockDataSource(String posPattern) {
        this(posPattern, compilePos(posPattern));
    }

    @Nullable
    private static Coordinates compilePos(String posPattern) {
        try {
            return BlockPosArgument.blockPos().parse(new StringReader(posPattern));
        } catch (CommandSyntaxException var2) {
            return null;
        }
    }

    @Override
    public Stream<CompoundTag> getData(CommandSourceStack source) {
        if (this.compiledPos != null) {
            ServerLevel level = source.getLevel();
            BlockPos blockPos = this.compiledPos.getBlockPos(source);
            if (level.isLoaded(blockPos)) {
                BlockEntity blockEntity = level.getBlockEntity(blockPos);
                if (blockEntity != null) {
                    return Stream.of(blockEntity.saveWithFullMetadata(source.registryAccess()));
                }
            }
        }

        return Stream.empty();
    }

    @Override
    public DataSource.Type<?> type() {
        return TYPE;
    }

    @Override
    public String toString() {
        return "block=" + this.posPattern;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof BlockDataSource blockDataSource && this.posPattern.equals(blockDataSource.posPattern);
    }

    @Override
    public int hashCode() {
        return this.posPattern.hashCode();
    }
}
