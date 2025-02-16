package net.minecraft.server.packs.repository;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FeatureFlagsMetadataSection;
import net.minecraft.server.packs.OverlayMetadataSection;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.flag.FeatureFlagSet;
import org.slf4j.Logger;

public class Pack {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackLocationInfo location;
    public final Pack.ResourcesSupplier resources;
    private final Pack.Metadata metadata;
    private final PackSelectionConfig selectionConfig;

    @Nullable
    public static Pack readMetaAndCreate(PackLocationInfo location, Pack.ResourcesSupplier resources, PackType packType, PackSelectionConfig selectionConfig) {
        int packVersion = SharedConstants.getCurrentVersion().getPackVersion(packType);
        Pack.Metadata packMetadata = readPackMetadata(location, resources, packVersion);
        return packMetadata != null ? new Pack(location, resources, packMetadata, selectionConfig) : null;
    }

    public Pack(PackLocationInfo location, Pack.ResourcesSupplier resources, Pack.Metadata metadata, PackSelectionConfig selectionConfig) {
        this.location = location;
        this.resources = resources;
        this.metadata = metadata;
        this.selectionConfig = selectionConfig;
    }

    @Nullable
    public static Pack.Metadata readPackMetadata(PackLocationInfo location, Pack.ResourcesSupplier resources, int version) {
        try {
            Pack.Metadata var11;
            try (PackResources packResources = resources.openPrimary(location)) {
                PackMetadataSection packMetadataSection = packResources.getMetadataSection(PackMetadataSection.TYPE);
                if (packMetadataSection == null) {
                    LOGGER.warn("Missing metadata in pack {}", location.id());
                    return null;
                }

                FeatureFlagsMetadataSection featureFlagsMetadataSection = packResources.getMetadataSection(FeatureFlagsMetadataSection.TYPE);
                FeatureFlagSet featureFlagSet = featureFlagsMetadataSection != null ? featureFlagsMetadataSection.flags() : FeatureFlagSet.of();
                InclusiveRange<Integer> declaredPackVersions = getDeclaredPackVersions(location.id(), packMetadataSection);
                PackCompatibility packCompatibility = PackCompatibility.forVersion(declaredPackVersions, version);
                OverlayMetadataSection overlayMetadataSection = packResources.getMetadataSection(OverlayMetadataSection.TYPE);
                List<String> list = overlayMetadataSection != null ? overlayMetadataSection.overlaysForVersion(version) : List.of();
                var11 = new Pack.Metadata(packMetadataSection.description(), packCompatibility, featureFlagSet, list);
            }

            return var11;
        } catch (Exception var14) {
            LOGGER.warn("Failed to read pack {} metadata", location.id(), var14);
            return null;
        }
    }

    private static InclusiveRange<Integer> getDeclaredPackVersions(String id, PackMetadataSection metadata) {
        int packFormat = metadata.packFormat();
        if (metadata.supportedFormats().isEmpty()) {
            return new InclusiveRange<>(packFormat);
        } else {
            InclusiveRange<Integer> inclusiveRange = metadata.supportedFormats().get();
            if (!inclusiveRange.isValueInRange(packFormat)) {
                LOGGER.warn(
                    "Pack {} declared support for versions {} but declared main format is {}, defaulting to {}", id, inclusiveRange, packFormat, packFormat
                );
                return new InclusiveRange<>(packFormat);
            } else {
                return inclusiveRange;
            }
        }
    }

    public PackLocationInfo location() {
        return this.location;
    }

    public Component getTitle() {
        return this.location.title();
    }

    public Component getDescription() {
        return this.metadata.description();
    }

    public Component getChatLink(boolean green) {
        return this.location.createChatLink(green, this.metadata.description);
    }

    public PackCompatibility getCompatibility() {
        return this.metadata.compatibility();
    }

    public FeatureFlagSet getRequestedFeatures() {
        return this.metadata.requestedFeatures();
    }

    public PackResources open() {
        return this.resources.openFull(this.location, this.metadata);
    }

    public String getId() {
        return this.location.id();
    }

    public PackSelectionConfig selectionConfig() {
        return this.selectionConfig;
    }

    public boolean isRequired() {
        return this.selectionConfig.required();
    }

    public boolean isFixedPosition() {
        return this.selectionConfig.fixedPosition();
    }

    public Pack.Position getDefaultPosition() {
        return this.selectionConfig.defaultPosition();
    }

    public PackSource getPackSource() {
        return this.location.source();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Pack pack && this.location.equals(pack.location);
    }

    @Override
    public int hashCode() {
        return this.location.hashCode();
    }

    public record Metadata(Component description, PackCompatibility compatibility, FeatureFlagSet requestedFeatures, List<String> overlays) {
    }

    public static enum Position {
        TOP,
        BOTTOM;

        public <T> int insert(List<T> list, T element, Function<T, PackSelectionConfig> packFactory, boolean flipPosition) {
            Pack.Position position = flipPosition ? this.opposite() : this;
            if (position == BOTTOM) {
                int i;
                for (i = 0; i < list.size(); i++) {
                    PackSelectionConfig packSelectionConfig = packFactory.apply(list.get(i));
                    if (!packSelectionConfig.fixedPosition() || packSelectionConfig.defaultPosition() != this) {
                        break;
                    }
                }

                list.add(i, element);
                return i;
            } else {
                int i;
                for (i = list.size() - 1; i >= 0; i--) {
                    PackSelectionConfig packSelectionConfig = packFactory.apply(list.get(i));
                    if (!packSelectionConfig.fixedPosition() || packSelectionConfig.defaultPosition() != this) {
                        break;
                    }
                }

                list.add(i + 1, element);
                return i + 1;
            }
        }

        public Pack.Position opposite() {
            return this == TOP ? BOTTOM : TOP;
        }
    }

    public interface ResourcesSupplier {
        PackResources openPrimary(PackLocationInfo location);

        PackResources openFull(PackLocationInfo location, Pack.Metadata metadata);
    }
}
