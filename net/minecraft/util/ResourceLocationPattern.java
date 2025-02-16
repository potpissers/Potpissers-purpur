package net.minecraft.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

public class ResourceLocationPattern {
    public static final Codec<ResourceLocationPattern> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ExtraCodecs.PATTERN.optionalFieldOf("namespace").forGetter(resourceLocationPattern -> resourceLocationPattern.namespacePattern),
                ExtraCodecs.PATTERN.optionalFieldOf("path").forGetter(resourceLocationPattern -> resourceLocationPattern.pathPattern)
            )
            .apply(instance, ResourceLocationPattern::new)
    );
    private final Optional<Pattern> namespacePattern;
    private final Predicate<String> namespacePredicate;
    private final Optional<Pattern> pathPattern;
    private final Predicate<String> pathPredicate;
    private final Predicate<ResourceLocation> locationPredicate;

    private ResourceLocationPattern(Optional<Pattern> namespacePattern, Optional<Pattern> pathPattern) {
        this.namespacePattern = namespacePattern;
        this.namespacePredicate = namespacePattern.map(Pattern::asPredicate).orElse(namespace -> true);
        this.pathPattern = pathPattern;
        this.pathPredicate = pathPattern.map(Pattern::asPredicate).orElse(path -> true);
        this.locationPredicate = location -> this.namespacePredicate.test(location.getNamespace()) && this.pathPredicate.test(location.getPath());
    }

    public Predicate<String> namespacePredicate() {
        return this.namespacePredicate;
    }

    public Predicate<String> pathPredicate() {
        return this.pathPredicate;
    }

    public Predicate<ResourceLocation> locationPredicate() {
        return this.locationPredicate;
    }
}
