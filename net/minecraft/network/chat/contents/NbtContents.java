package net.minecraft.network.chat.contents;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

public class NbtContents implements ComponentContents {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<NbtContents> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.STRING.fieldOf("nbt").forGetter(NbtContents::getNbtPath),
                Codec.BOOL.lenientOptionalFieldOf("interpret", Boolean.valueOf(false)).forGetter(NbtContents::isInterpreting),
                ComponentSerialization.CODEC.lenientOptionalFieldOf("separator").forGetter(NbtContents::getSeparator),
                DataSource.CODEC.forGetter(NbtContents::getDataSource)
            )
            .apply(instance, NbtContents::new)
    );
    public static final ComponentContents.Type<NbtContents> TYPE = new ComponentContents.Type<>(CODEC, "nbt");
    private final boolean interpreting;
    private final Optional<Component> separator;
    private final String nbtPathPattern;
    private final DataSource dataSource;
    @Nullable
    protected final NbtPathArgument.NbtPath compiledNbtPath;

    public NbtContents(String nbtPathPattern, boolean interpreting, Optional<Component> separator, DataSource dataSource) {
        this(nbtPathPattern, compileNbtPath(nbtPathPattern), interpreting, separator, dataSource);
    }

    private NbtContents(
        String nbtPathPattern, @Nullable NbtPathArgument.NbtPath compiledNbtPath, boolean interpreting, Optional<Component> separator, DataSource dataSource
    ) {
        this.nbtPathPattern = nbtPathPattern;
        this.compiledNbtPath = compiledNbtPath;
        this.interpreting = interpreting;
        this.separator = separator;
        this.dataSource = dataSource;
    }

    @Nullable
    private static NbtPathArgument.NbtPath compileNbtPath(String nbtPathPattern) {
        try {
            return new NbtPathArgument().parse(new StringReader(nbtPathPattern));
        } catch (CommandSyntaxException var2) {
            return null;
        }
    }

    public String getNbtPath() {
        return this.nbtPathPattern;
    }

    public boolean isInterpreting() {
        return this.interpreting;
    }

    public Optional<Component> getSeparator() {
        return this.separator;
    }

    public DataSource getDataSource() {
        return this.dataSource;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof NbtContents nbtContents
                && this.dataSource.equals(nbtContents.dataSource)
                && this.separator.equals(nbtContents.separator)
                && this.interpreting == nbtContents.interpreting
                && this.nbtPathPattern.equals(nbtContents.nbtPathPattern);
    }

    @Override
    public int hashCode() {
        int i = this.interpreting ? 1 : 0;
        i = 31 * i + this.separator.hashCode();
        i = 31 * i + this.nbtPathPattern.hashCode();
        return 31 * i + this.dataSource.hashCode();
    }

    @Override
    public String toString() {
        return "nbt{" + this.dataSource + ", interpreting=" + this.interpreting + ", separator=" + this.separator + "}";
    }

    @Override
    public MutableComponent resolve(@Nullable CommandSourceStack nbtPathPattern, @Nullable Entity entity, int recursionDepth) throws CommandSyntaxException {
        if (nbtPathPattern != null && this.compiledNbtPath != null) {
            Stream<String> stream = this.dataSource.getData(nbtPathPattern).flatMap(tag -> {
                try {
                    return this.compiledNbtPath.get(tag).stream();
                } catch (CommandSyntaxException var3x) {
                    return Stream.empty();
                }
            }).map(Tag::getAsString);
            if (this.interpreting) {
                Component component = DataFixUtils.orElse(
                    ComponentUtils.updateSeparatorForEntity(nbtPathPattern, this.separator, entity, recursionDepth), ComponentUtils.DEFAULT_NO_STYLE_SEPARATOR // Paper - validate separator
                );
                return stream.flatMap(text -> {
                    try {
                        MutableComponent mutableComponent = Component.Serializer.fromJson(text, nbtPathPattern.registryAccess());
                        return Stream.of(ComponentUtils.updateForEntity(nbtPathPattern, mutableComponent, entity, recursionDepth));
                    } catch (Exception var5x) {
                        LOGGER.warn("Failed to parse component: {}", text, var5x);
                        return Stream.of();
                    }
                }).reduce((mutableComponent, component1) -> mutableComponent.append(component).append(component1)).orElseGet(Component::empty);
            } else {
                return ComponentUtils.updateSeparatorForEntity(nbtPathPattern, this.separator, entity, recursionDepth) // Paper - validate separator
                    .map(
                        mutableComponent -> stream.map(Component::literal)
                            .reduce((mutableComponent1, otherMutableComponent) -> mutableComponent1.append(mutableComponent).append(otherMutableComponent))
                            .orElseGet(Component::empty)
                    )
                    .orElseGet(() -> Component.literal(stream.collect(Collectors.joining(", "))));
            }
        } else {
            return Component.empty();
        }
    }

    @Override
    public ComponentContents.Type<?> type() {
        return TYPE;
    }
}
